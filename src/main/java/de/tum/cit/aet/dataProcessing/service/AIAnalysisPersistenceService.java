package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.dto.FairnessReportWithUsageDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.ai.dto.LlmTokenUsageDTO;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator;
import de.tum.cit.aet.analysis.service.cqi.CqiRecalculationService;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists Phase-3 (AI analysis) results including fairness analysis,
 * chunk saving, email mapping application, and single-team re-analysis.
 */
@Service
@Slf4j
public class AIAnalysisPersistenceService {

    private final ContributionFairnessService fairnessService;
    private final ContributionBalanceCalculator balanceCalculator;
    private final CqiPersistenceHelper cqiPersistenceHelper;
    private final CqiRecalculationService cqiRecalculationService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final AnalysisQueryService queryService;

    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;
    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;

    private final TransactionTemplate transactionTemplate;

    public record ClientResponseWithUsage(ClientResponseDTO response, LlmTokenTotalsDTO tokenTotals) {}

    public AIAnalysisPersistenceService(
            ContributionFairnessService fairnessService,
            ContributionBalanceCalculator balanceCalculator,
            CqiPersistenceHelper cqiPersistenceHelper,
            CqiRecalculationService cqiRecalculationService,
            GitContributionAnalysisService gitContributionAnalysisService,
            AnalysisQueryService queryService,
            TeamParticipationRepository teamParticipationRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository,
            ExerciseTemplateAuthorRepository templateAuthorRepository,
            ExerciseEmailMappingRepository emailMappingRepository,
            TeamRepositoryRepository teamRepositoryRepository,
            TransactionTemplate transactionTemplate) {
        this.fairnessService = fairnessService;
        this.balanceCalculator = balanceCalculator;
        this.cqiPersistenceHelper = cqiPersistenceHelper;
        this.cqiRecalculationService = cqiRecalculationService;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.queryService = queryService;
        this.teamParticipationRepository = teamParticipationRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.templateAuthorRepository = templateAuthorRepository;
        this.emailMappingRepository = emailMappingRepository;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Persists AI analysis results for a team and returns the client response.
     *
     * @param repo       the cloned repository with VCS logs
     * @param exerciseId the exercise being analyzed
     * @return client-facing response DTO
     */
    public ClientResponseDTO saveAIAnalysisResult(TeamRepositoryDTO repo, Long exerciseId) {
        return saveAIAnalysisResultWithUsage(repo, exerciseId).response();
    }

    /**
     * Runs the full AI analysis pipeline (orphan detection, fairness analysis,
     * CQI fallback chain) and persists the results together with LLM token usage.
     *
     * @param repo       the cloned repository with VCS logs
     * @param exerciseId the exercise being analyzed
     * @return response DTO bundled with aggregated token usage
     */
    public ClientResponseWithUsage saveAIAnalysisResultWithUsage(TeamRepositoryDTO repo, Long exerciseId) {
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for AI analysis"));

        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.AI_ANALYZING);
        teamParticipationRepository.save(teamParticipation);

        List<Student> students = studentRepository.findAllByTeam(teamParticipation);
        String templateAuthorEmail = templateAuthorRepository.findByExerciseId(exerciseId)
                .map(ExerciseTemplateAuthor::getTemplateEmail)
                .orElse(null);

        List<OrphanCommitDTO> orphanCommits = detectOrphanCommits(repo, templateAuthorEmail, team.name());
        CqiComputationResult cqiResult = computeCqiWithFallback(
                repo, templateAuthorEmail, teamParticipation, team, students);

        persistAnalysisState(teamParticipation, cqiResult, orphanCommits);

        if (cqiResult.analysisHistory() != null && !cqiResult.analysisHistory().isEmpty()) {
            applyExistingEmailMappings(teamParticipation, exerciseId);
        }

        return buildAIAnalysisResponse(teamParticipation, participation, team, students,
                cqiResult, orphanCommits, exerciseId);
    }

    /** Detects orphan commits (unassigned, non-template) in the repository. */
    private List<OrphanCommitDTO> detectOrphanCommits(
            TeamRepositoryDTO repo, String templateAuthorEmail, String teamName) {
        try {
            RepositoryAnalysisResultDTO analysisResult = gitContributionAnalysisService
                    .analyzeRepositoryWithOrphans(repo, templateAuthorEmail);
            List<OrphanCommitDTO> orphans = analysisResult.orphanCommits();
            if (orphans != null && !orphans.isEmpty()) {
                log.info("Found {} orphan commits for team {}", orphans.size(), teamName);
            }
            return orphans;
        } catch (Exception e) {
            log.warn("Failed to detect orphan commits for team {}: {}", teamName, e.getMessage());
            return null;
        }
    }

    /** Aggregated CQI computation result from fairness analysis or fallback chain. */
    private record CqiComputationResult(
            Double cqi,
            List<AnalyzedChunkDTO> analysisHistory,
            CQIResultDTO cqiDetails,
            LlmTokenTotalsDTO tokenTotals
    ) {
    }

    /**
     * Computes CQI using a 3-tier fallback chain:
     * 1) Effort-based fairness analysis (LLM)
     * 2) CQI calculator with pre-filtered commits
     * 3) Simple commit-count balance
     */
    private CqiComputationResult computeCqiWithFallback(
            TeamRepositoryDTO repo, String templateAuthorEmail,
            TeamParticipation teamParticipation, TeamDTO team, List<Student> students) {

        // Tier 1: Effort-based fairness analysis
        try {
            FairnessReportWithUsageDTO fairnessResult = fairnessService.analyzeFairnessWithUsage(
                    repo, templateAuthorEmail);
            FairnessReportDTO report = fairnessResult.report();

            if (!report.error()) {
                List<AnalyzedChunkDTO> history = report.analyzedChunks();
                if (history != null && !history.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, history);
                }
                return new CqiComputationResult(
                        report.balanceScore(), history, report.cqiResult(),
                        fairnessResult.tokenTotals());
            }
            log.warn("Fairness analysis returned error for team {}", team.name());
        } catch (Exception e) {
            log.warn("Fairness analysis failed for team {}, falling back: {}", team.name(), e.getMessage());
        }

        // Tier 2: CQI calculator with pre-filtered commits
        CQIResultDTO cqiDetails = cqiPersistenceHelper.calculateFallbackCqi(repo, team, students);
        Double cqi = cqiDetails != null ? cqiDetails.cqi() : null;

        // Tier 3: Simple commit-count balance
        if (cqi == null || cqi == 0.0) {
            Map<String, Integer> commitCounts = new HashMap<>();
            students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
            if (!commitCounts.isEmpty()) {
                cqi = balanceCalculator.calculate(commitCounts);
            }
        }

        return new CqiComputationResult(cqi, null, cqiDetails, LlmTokenTotalsDTO.empty());
    }

    /** Persists CQI score, component scores, token usage, and marks the team as DONE. */
    private void persistAnalysisState(
            TeamParticipation teamParticipation, CqiComputationResult cqiResult,
            List<OrphanCommitDTO> orphanCommits) {
        teamParticipation.setCqi(cqiResult.cqi());
        teamParticipation.setIsSuspicious(false);
        teamParticipation.setOrphanCommitCount(orphanCommits != null ? orphanCommits.size() : 0);
        cqiPersistenceHelper.persistCqiComponents(teamParticipation, cqiResult.cqiDetails());
        cqiPersistenceHelper.persistTeamTokenTotals(teamParticipation, cqiResult.tokenTotals());
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);
    }

    /** Builds the client response DTO from post-mapping state. */
    private ClientResponseWithUsage buildAIAnalysisResponse(
            TeamParticipation teamParticipation, ParticipationDTO participation,
            TeamDTO team, List<Student> students, CqiComputationResult cqiResult,
            List<OrphanCommitDTO> orphanCommits, Long exerciseId) {

        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();
        Double finalCqi = teamParticipation.getCqi() != null ? teamParticipation.getCqi() : cqiResult.cqi();
        CQIResultDTO finalCqiDetails = queryService.reconstructCqiDetails(teamParticipation, AnalysisMode.FULL);
        if (finalCqiDetails == null) {
            finalCqiDetails = cqiResult.cqiDetails();
        }

        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        team.id(), team.name(), participation.submissionCount(),
                        studentDtos, finalCqi, false, TeamAnalysisStatus.DONE,
                        finalCqiDetails, cqiResult.analysisHistory(), orphanCommits,
                        cqiResult.tokenTotals(), teamParticipation.getOrphanCommitCount(), null),
                cqiResult.tokenTotals());
    }

    /**
     * Re-runs the AI analysis for a single team that already has git results persisted.
     *
     * @param exerciseId the exercise the team belongs to
     * @param teamId     the Artemis team id
     * @return the updated client response, or empty if the team/repo is not found
     */
    public Optional<ClientResponseDTO> runSingleTeamAIAnalysis(Long exerciseId, Long teamId) {
        record PreparedAnalysis(TeamRepositoryDTO repoDto) {}
        PreparedAnalysis prepared = transactionTemplate.execute(status -> {
            Optional<TeamParticipation> tpOpt = teamParticipationRepository.findByExerciseIdAndTeam(exerciseId, teamId);
            if (tpOpt.isEmpty()) {
                return null;
            }

            TeamParticipation tp = tpOpt.get();
            Optional<TeamRepository> repoOpt = teamRepositoryRepository.findByTeamParticipation(tp);
            if (repoOpt.isEmpty()) {
                return null;
            }

            TeamRepository repo = repoOpt.get();
            if (repo.getLocalPath() == null || !Files.exists(Path.of(repo.getLocalPath(), ".git"))) {
                log.warn("Local repo not found for team {} at path {}", tp.getName(), repo.getLocalPath());
                return null;
            }

            tp.setAnalysisStatus(TeamAnalysisStatus.AI_ANALYZING);
            teamParticipationRepository.save(tp);
            analyzedChunkRepository.deleteAllByParticipation(tp);

            return new PreparedAnalysis(buildTeamRepositoryDTO(tp, repo));
        });

        if (prepared == null) {
            return Optional.empty();
        }

        ClientResponseWithUsage result = saveAIAnalysisResultWithUsage(prepared.repoDto(), exerciseId);
        return Optional.ofNullable(result.response());
    }

    /**
     * Persists analyzed chunk entities for a team participation.
     *
     * @param participation the team participation that owns the chunks
     * @param chunks        the analyzed chunk DTOs to persist
     */
    public void saveAnalyzedChunks(TeamParticipation participation, List<AnalyzedChunkDTO> chunks) {
        try {
            List<AnalyzedChunk> entities = chunks.stream()
                    .map(dto -> {
                        LlmTokenUsageDTO usage = dto.llmTokenUsage();
                        return new AnalyzedChunk(
                                participation, dto.id(), dto.authorEmail(), dto.authorName(),
                                dto.classification(), dto.effortScore(), dto.complexity(),
                                dto.novelty(), dto.confidence(), dto.reasoning(),
                                String.join(",", dto.commitShas()),
                                queryService.serializeCommitMessages(dto.commitMessages()),
                                dto.timestamp(), dto.linesChanged(), dto.isBundled(),
                                dto.chunkIndex(), dto.totalChunks(), dto.isError(),
                                dto.errorMessage(), dto.isExternalContributor(),
                                usage != null ? usage.model() : null,
                                usage != null ? usage.promptTokens() : null,
                                usage != null ? usage.completionTokens() : null,
                                usage != null ? usage.totalTokens() : null,
                                usage != null ? usage.usageAvailable() : null);
                    })
                    .toList();
            analyzedChunkRepository.saveAll(entities);
        } catch (Exception e) {
            log.warn("Failed to save analyzed chunks for team {}: {}", participation.getName(), e.getMessage());
        }
    }

    /**
     * Applies all stored email mappings for the exercise to the team's analyzed chunks,
     * reassigning external-contributor chunks to known students and recalculating CQI.
     *
     * @param participation the team participation whose chunks may be remapped
     * @param exerciseId    the exercise whose email mappings should be applied
     */
    @Transactional
    public void applyExistingEmailMappings(TeamParticipation participation, Long exerciseId) {
        try {
            List<ExerciseEmailMapping> mappings = emailMappingRepository.findAllByExerciseId(exerciseId);
            if (mappings.isEmpty()) {
                return;
            }
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            Map<String, List<AnalyzedChunk>> remappedByStudent = new HashMap<>();

            for (ExerciseEmailMapping mapping : mappings) {
                String emailLower = mapping.getGitEmail().toLowerCase(java.util.Locale.ROOT);
                for (AnalyzedChunk chunk : chunks) {
                    if (Boolean.TRUE.equals(chunk.getIsExternalContributor())
                            && emailLower.equals(chunk.getAuthorEmail() != null
                                    ? chunk.getAuthorEmail().toLowerCase(java.util.Locale.ROOT) : null)) {
                        chunk.setIsExternalContributor(false);
                        chunk.setAuthorName(mapping.getStudentName());
                        remappedByStudent.computeIfAbsent(mapping.getStudentName(), k -> new ArrayList<>())
                                .add(chunk);
                    }
                }
            }

            if (!remappedByStudent.isEmpty()) {
                analyzedChunkRepository.saveAll(chunks);

                List<Student> students = studentRepository.findAllByTeam(participation);
                for (Map.Entry<String, List<AnalyzedChunk>> entry : remappedByStudent.entrySet()) {
                    String studentName = entry.getKey();
                    int deltaCommits = 0;
                    int deltaLines = 0;
                    for (AnalyzedChunk chunk : entry.getValue()) {
                        if (chunk.getCommitShas() != null && !chunk.getCommitShas().isEmpty()) {
                            deltaCommits += chunk.getCommitShas().split(",").length;
                        }
                        deltaLines += chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0;
                    }
                    if (deltaCommits > 0 || deltaLines > 0) {
                        int finalDeltaCommits = deltaCommits;
                        int finalDeltaLines = deltaLines;
                        students.stream()
                                .filter(s -> studentName.equals(s.getName()))
                                .findFirst()
                                .ifPresent(student -> {
                                    student.setCommitCount(
                                            (student.getCommitCount() != null ? student.getCommitCount() : 0)
                                                    + finalDeltaCommits);
                                    student.setLinesChanged(
                                            (student.getLinesChanged() != null ? student.getLinesChanged() : 0)
                                                    + finalDeltaLines);
                                    CqiRecalculationService.applyLinesSplit(student, finalDeltaLines, true);
                                    studentRepository.save(student);
                                });
                    }
                }

                log.info("Applied {} email mapping(s) to chunks for team {}, updated {} student(s)",
                        mappings.size(), participation.getName(), remappedByStudent.size());
                cqiRecalculationService.recalculateFromChunks(participation, chunks);
            }
        } catch (Exception e) {
            log.warn("Failed to apply existing email mappings for team {}: {}",
                    participation.getName(), e.getMessage());
        }
    }

    private TeamRepositoryDTO buildTeamRepositoryDTO(TeamParticipation tp, TeamRepository repo) {
        List<Student> students = studentRepository.findAllByTeam(tp);
        List<ParticipantDTO> studentDtos = students.stream()
                .map(s -> new ParticipantDTO(s.getId(), s.getLogin(), s.getName(), s.getEmail()))
                .toList();

        TeamDTO teamDto = new TeamDTO(tp.getTeam(), tp.getName(), tp.getShortName(), studentDtos,
                tp.getTutor() != null ? new ParticipantDTO(null, null, tp.getTutor().getName(), null) : null);

        ParticipationDTO participationDto = new ParticipationDTO(teamDto, tp.getParticipation(),
                tp.getRepositoryUrl(), tp.getSubmissionCount());

        List<VCSLogDTO> vcsLogDtos = repo.getVcsLogs() != null
                ? repo.getVcsLogs().stream()
                        .map(v -> new VCSLogDTO(v.getEmail(), null, v.getCommitHash()))
                        .toList()
                : List.of();

        return TeamRepositoryDTO.builder()
                .participation(participationDto)
                .vcsLogs(vcsLogDtos)
                .localPath(repo.getLocalPath())
                .isCloned(repo.getIsCloned())
                .error(repo.getError())
                .build();
    }
}
