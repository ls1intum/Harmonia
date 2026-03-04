package de.tum.cit.aet.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.dto.FairnessReportWithUsageDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.ai.dto.LlmTokenUsageDTO;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentWeightsDTO;
import de.tum.cit.aet.analysis.dto.cqi.PreFilterResultDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator;
import de.tum.cit.aet.analysis.service.cqi.CqiRecalculationService;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.dataProcessing.service.ExerciseTeamLifecycleService;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TutorRepository;
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
 * Persists Phase-2 (git analysis) and Phase-3 (AI analysis) results.
 * Handles CQI fallback chain, chunk saving, email mapping application,
 * and token usage tracking.
 */
@Service
@Slf4j
public class AnalysisResultPersistenceService {

    private final ContributionBalanceCalculator balanceCalculator;
    private final ContributionFairnessService fairnessService;
    private final AnalysisStateService analysisStateService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final CommitChunkerService commitChunkerService;
    private final CqiRecalculationService cqiRecalculationService;
    private final PairProgrammingService pairProgrammingService;

    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;
    private final ExerciseEmailMappingRepository emailMappingRepository;

    private final ExerciseTeamLifecycleService cleanupService;
    private final AnalysisQueryService queryService;
    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ClientResponseWithUsage(ClientResponseDTO response, LlmTokenTotalsDTO tokenTotals) {}

    public AnalysisResultPersistenceService(
            ContributionBalanceCalculator balanceCalculator,
            ContributionFairnessService fairnessService,
            AnalysisStateService analysisStateService,
            GitContributionAnalysisService gitContributionAnalysisService,
            CommitPreFilterService commitPreFilterService,
            CQICalculatorService cqiCalculatorService,
            CommitChunkerService commitChunkerService,
            CqiRecalculationService cqiRecalculationService,
            PairProgrammingService pairProgrammingService,
            TeamRepositoryRepository teamRepositoryRepository,
            TeamParticipationRepository teamParticipationRepository,
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository,
            ExerciseTemplateAuthorRepository templateAuthorRepository,
            ExerciseEmailMappingRepository emailMappingRepository,
            ExerciseTeamLifecycleService cleanupService,
            AnalysisQueryService queryService,
            TransactionTemplate transactionTemplate) {
        this.balanceCalculator = balanceCalculator;
        this.fairnessService = fairnessService;
        this.analysisStateService = analysisStateService;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.commitPreFilterService = commitPreFilterService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.commitChunkerService = commitChunkerService;
        this.cqiRecalculationService = cqiRecalculationService;
        this.pairProgrammingService = pairProgrammingService;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.templateAuthorRepository = templateAuthorRepository;
        this.emailMappingRepository = emailMappingRepository;
        this.cleanupService = cleanupService;
        this.queryService = queryService;
        this.transactionTemplate = transactionTemplate;
    }

    // =====================================================================
    //  Phase 2: Git analysis persistence
    // =====================================================================

    /**
     * Persists git analysis results for a single team repository (full mode).
     *
     * @param repo             the cloned repository with VCS logs
     * @param contributionData per-student contribution statistics
     * @param exerciseId       the exercise being analyzed
     * @return client-facing response DTO, or {@code null} if the analysis was cancelled
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        return saveGitAnalysisResult(repo, contributionData, exerciseId, null);
    }

    /**
     * Persists git analysis results for a single team repository.
     *
     * @param repo             the cloned repository with VCS logs
     * @param contributionData per-student contribution statistics
     * @param exerciseId       the exercise being analyzed
     * @param mode             analysis mode ({@code SIMPLE} or {@code FULL}), may be {@code null}
     * @return client-facing response DTO, or {@code null} if the analysis was cancelled
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData,
                                                    Long exerciseId, AnalysisMode mode) {
        if (!analysisStateService.isRunning(exerciseId)) {
            return null;
        }

        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        Tutor tutor = cleanupService.ensureTutor(team);

        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                        team.shortName(), participation.repositoryUri(), participation.submissionCount()));

        teamParticipation.setExerciseId(exerciseId);
        teamParticipation.setTutor(tutor);
        teamParticipation.setSubmissionCount(participation.submissionCount());
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.GIT_DONE);
        persistTeamTokenTotals(teamParticipation, LlmTokenTotalsDTO.empty());
        teamParticipationRepository.save(teamParticipation);

        List<Student> students = new ArrayList<>();
        List<StudentAnalysisDTO> studentDtos = new ArrayList<>();
        studentRepository.deleteAllByTeam(teamParticipation);

        for (ParticipantDTO student : team.students()) {
            AuthorContributionDTO contrib = contributionData.getOrDefault(student.id(),
                    new AuthorContributionDTO(0, 0, 0));

            students.add(new Student(student.id(), student.login(), student.name(), student.email(), teamParticipation,
                    contrib.commitCount(), contrib.linesAdded(), contrib.linesDeleted(),
                    contrib.linesAdded() + contrib.linesDeleted()));

            studentDtos.add(new StudentAnalysisDTO(student.name(), contrib.commitCount(),
                    contrib.linesAdded(), contrib.linesDeleted(),
                    contrib.linesAdded() + contrib.linesDeleted()));
        }
        studentRepository.saveAll(students);

        TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(),
                repo.error());
        List<VCSLog> vcsLogs = repo.vcsLogs().stream()
                .map(vcsLog -> new VCSLog(teamRepo, vcsLog.commitHash(), vcsLog.email()))
                .toList();
        teamRepo.setVcsLogs(vcsLogs);
        teamRepositoryRepository.save(teamRepo);

        boolean hasFailed = checkAndMarkFailed(teamParticipation, students);
        if (hasFailed) {
            return new ClientResponseDTO(
                    tutor != null ? tutor.getName() : "Unassigned",
                    team.id(), participation.id(), team.name(), team.shortName(),
                    participation.submissionCount(),
                    studentDtos, 0.0, false, TeamAnalysisStatus.DONE,
                    null, null, null, null, 0, true, null);
        }

        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        CQIResultDTO finalDetails = gitCqiDetails;
        if (mode == AnalysisMode.SIMPLE && gitCqiDetails != null) {
            finalDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails, exerciseId);
        }

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), participation.id(), team.name(), team.shortName(),
                participation.submissionCount(),
                studentDtos,
                null,
                null,
                TeamAnalysisStatus.GIT_DONE,
                finalDetails,
                null,
                null,
                queryService.readTeamTokenTotals(teamParticipation),
                null,
                null,
                null);
    }

    // =====================================================================
    //  Phase 3: AI analysis persistence
    // =====================================================================

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

        Double cqi = null;
        boolean isSuspicious = false;
        List<AnalyzedChunkDTO> analysisHistory = null;
        List<OrphanCommitDTO> orphanCommits = null;
        CQIResultDTO cqiDetails = null;
        LlmTokenTotalsDTO teamTokenTotals = LlmTokenTotalsDTO.empty();

        // 1) Detect orphan commits
        try {
            RepositoryAnalysisResultDTO analysisResult = gitContributionAnalysisService
                    .analyzeRepositoryWithOrphans(repo, templateAuthorEmail);
            orphanCommits = analysisResult.orphanCommits();
        } catch (Exception e) {
            log.warn("Failed to detect orphan commits for team {}: {}", team.name(), e.getMessage());
        }

        // 2) Try effort-based fairness analysis (primary method)
        boolean fairnessSucceeded = false;
        try {
            FairnessReportWithUsageDTO fairnessResult = fairnessService.analyzeFairnessWithUsage(
                    repo, templateAuthorEmail);
            FairnessReportDTO report = fairnessResult.report();
            teamTokenTotals = fairnessResult.tokenTotals();

            if (!report.error()) {
                cqi = report.balanceScore();
                analysisHistory = report.analyzedChunks();
                cqiDetails = report.cqiResult();
                fairnessSucceeded = true;

                if (analysisHistory != null && !analysisHistory.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, analysisHistory);
                }
            } else {
                log.warn("Fairness analysis returned error for team {}", team.name());
            }
        } catch (Exception e) {
            log.warn("Fairness analysis failed for team {}, falling back: {}", team.name(), e.getMessage());
        }

        // 3) Fallback: CQI calculator with pre-filtered commits
        if (!fairnessSucceeded) {
            cqi = calculateFallbackCqi(repo, team, students);
            if (cqi != null) {
                try {
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
                    PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
                    cqiDetails = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(), students.size(), filterResult.summary(),
                            team.name(), team.shortName(), exerciseId);
                    cqi = cqiDetails.cqi();
                } catch (Exception e) {
                    log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
                }
            }

            // 4) Last resort: simple commit-count balance
            if (cqi == null || cqi == 0.0) {
                Map<String, Integer> commitCounts = new HashMap<>();
                students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                if (!commitCounts.isEmpty()) {
                    cqi = balanceCalculator.calculate(commitCounts);
                }
            }
        }

        // 5) Persist CQI and component scores
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(isSuspicious);
        teamParticipation.setOrphanCommitCount(orphanCommits != null ? orphanCommits.size() : 0);
        persistCqiComponents(teamParticipation, cqiDetails);
        persistTeamTokenTotals(teamParticipation, teamTokenTotals);
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        // 6) Apply existing email mappings (may recalculate CQI)
        if (analysisHistory != null && !analysisHistory.isEmpty()) {
            applyExistingEmailMappings(teamParticipation, exerciseId);
        }

        // 7) Build response from post-mapping state
        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();
        Double finalCqi = teamParticipation.getCqi() != null ? teamParticipation.getCqi() : cqi;
        CQIResultDTO finalCqiDetails = queryService.reconstructCqiDetails(teamParticipation, AnalysisMode.FULL);
        if (finalCqiDetails == null) {
            finalCqiDetails = cqiDetails;
        }

        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        team.id(), participation.id(), team.name(), team.shortName(),
                        participation.submissionCount(),
                        studentDtos, finalCqi, isSuspicious, TeamAnalysisStatus.DONE,
                        finalCqiDetails, analysisHistory, orphanCommits,
                        teamTokenTotals, teamParticipation.getOrphanCommitCount(), null, null),
                teamTokenTotals);
    }

    // =====================================================================
    //  Single team AI re-analysis
    // =====================================================================

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

    // =====================================================================
    //  SIMPLE mode CQI computation
    // =====================================================================

    /**
     * Calculates a git-only CQI (without effort balance) for SIMPLE mode and persists the result.
     *
     * @param participation the Artemis participation DTO
     * @param repo          the cloned repository
     * @param exerciseId    the exercise being analyzed
     * @return client-facing response DTO with the simple CQI
     */
    public ClientResponseDTO calculateAndPersistSimpleCqi(ParticipationDTO participation,
                                                           TeamRepositoryDTO repo, Long exerciseId) {
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for simple CQI"));

        List<Student> students = studentRepository.findAllByTeam(teamParticipation);

        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        Double cqi = null;
        CQIResultDTO simpleCqiDetails = gitCqiDetails;
        if (gitCqiDetails != null && gitCqiDetails.components() != null) {
            ComponentWeightsDTO weights = cqiCalculatorService.buildWeightsDTO();
            double wLoc = weights.locBalance();
            double wTemporal = weights.temporalSpread();
            double wOwnership = weights.ownershipSpread();
            double divisor = wLoc + wTemporal + wOwnership;

            if (divisor > 0) {
                double locScore = gitCqiDetails.components().locBalance();
                double temporalScore = gitCqiDetails.components().temporalSpread();
                double ownershipScore = gitCqiDetails.components().ownershipSpread();

                double rawCqi = (wLoc * locScore + wTemporal * temporalScore + wOwnership * ownershipScore) / divisor;
                cqi = (double) Math.max(0, Math.min(100, Math.round(rawCqi)));
            }

            simpleCqiDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails, exerciseId);
        }

        persistCqiComponents(teamParticipation, gitCqiDetails);
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(false);
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();
        CQIResultDTO finalDetails = simpleCqiDetails != null ? simpleCqiDetails : queryService.reconstructCqiDetails(teamParticipation, AnalysisMode.SIMPLE);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), participation.id(), team.name(), team.shortName(),
                participation.submissionCount(),
                studentDtos, cqi, false, TeamAnalysisStatus.DONE,
                finalDetails, null, null, null, null, null, null);
    }

    // =====================================================================
    //  Review status
    // =====================================================================

    /**
     * Toggles the review status of a team participation.
     *
     * @param exerciseId the exercise id
     * @param teamId     the Artemis team id
     * @return updated client response DTO
     * @throws IllegalArgumentException if the team is not found
     */
    @Transactional
    public ClientResponseDTO toggleReviewStatus(Long exerciseId, Long teamId) {
        TeamParticipation participation = teamParticipationRepository.findByExerciseIdAndTeam(exerciseId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        Boolean current = participation.getIsReviewed();
        participation.setIsReviewed(!Boolean.TRUE.equals(current));
        teamParticipationRepository.save(participation);
        return queryService.mapParticipationToClientResponse(participation);
    }

    // =====================================================================
    //  Persistence helpers
    // =====================================================================

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
                                serializeCommitMessages(dto.commitMessages()),
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
                cqiRecalculationService.recalculateFromChunks(participation, chunks);
            }
        } catch (Exception e) {
            log.warn("Failed to apply existing email mappings for team {}: {}",
                    participation.getName(), e.getMessage());
        }
    }

    /**
     * Writes aggregated LLM token usage totals onto the team participation entity.
     *
     * @param tp     the team participation to update
     * @param totals the token totals, or {@code null} to clear
     */
    public void persistTeamTokenTotals(TeamParticipation tp, LlmTokenTotalsDTO totals) {
        if (totals == null) {
            tp.setLlmCalls(null);
            tp.setLlmCallsWithUsage(null);
            tp.setLlmPromptTokens(null);
            tp.setLlmCompletionTokens(null);
            tp.setLlmTotalTokens(null);
            return;
        }
        tp.setLlmCalls(totals.llmCalls());
        tp.setLlmCallsWithUsage(totals.callsWithUsage());
        tp.setLlmPromptTokens(totals.promptTokens());
        tp.setLlmCompletionTokens(totals.completionTokens());
        tp.setLlmTotalTokens(totals.totalTokens());
    }

    /**
     * Serializes a list of commit messages to a JSON string.
     *
     * @param messages the commit messages
     * @return JSON array string, or {@code "[]"} on error
     */
    public String serializeCommitMessages(List<String> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Serializes a weekly effort distribution to a JSON string.
     *
     * @param weeklyDistribution the weekly effort values
     * @return JSON array string, or {@code null} if empty or on error
     */
    public String serializeWeeklyDistribution(List<Double> weeklyDistribution) {
        try {
            if (weeklyDistribution == null || weeklyDistribution.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(weeklyDistribution);
        } catch (Exception e) {
            log.warn("Failed to serialize weekly distribution: {}", e.getMessage());
            return null;
        }
    }

    // =====================================================================
    //  Internal helpers
    // =====================================================================

    private CQIResultDTO calculateGitOnlyCqi(TeamRepositoryDTO repo, TeamParticipation teamParticipation,
                                              TeamDTO team, List<Student> students) {
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);

            ComponentScoresDTO gitComponents = cqiCalculatorService.calculateGitOnlyComponents(
                    filterResult.chunksToAnalyze(), students.size(), null, null, team.name(), team.shortName());

            if (gitComponents != null) {
                teamParticipation.setCqiLocBalance(gitComponents.locBalance());
                teamParticipation.setCqiTemporalSpread(gitComponents.temporalSpread());
                teamParticipation.setCqiOwnershipSpread(gitComponents.ownershipSpread());
                teamParticipation.setCqiPairProgramming(gitComponents.pairProgramming());
                teamParticipation.setCqiPairProgrammingStatus(
                        gitComponents.pairProgrammingStatus() != null ? gitComponents.pairProgrammingStatus().name() : null);
                teamParticipationRepository.save(teamParticipation);

                return CQIResultDTO.gitOnly(cqiCalculatorService.buildWeightsDTO(), gitComponents, filterResult.summary());
            }
        } catch (Exception e) {
            log.warn("Failed to calculate git-only metrics for team {}: {}", team.name(), e.getMessage());
        }
        return null;
    }

    private Double calculateFallbackCqi(TeamRepositoryDTO repo, TeamDTO team, List<Student> students) {
        if (repo.localPath() == null) {
            return null;
        }
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
            CQIResultDTO result = cqiCalculatorService.calculateFallback(
                    filterResult.chunksToAnalyze(), students.size(), filterResult.summary(),
                    team.name(), team.shortName(), null);
            return result.cqi();
        } catch (Exception e) {
            log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
            return null;
        }
    }

    private void persistCqiComponents(TeamParticipation teamParticipation, CQIResultDTO cqiDetails) {
        if (cqiDetails == null || cqiDetails.components() == null) {
            return;
        }
        teamParticipation.setCqiEffortBalance(cqiDetails.components().effortBalance());
        teamParticipation.setCqiLocBalance(cqiDetails.components().locBalance());
        teamParticipation.setCqiTemporalSpread(cqiDetails.components().temporalSpread());
        teamParticipation.setCqiOwnershipSpread(cqiDetails.components().ownershipSpread());
        teamParticipation.setCqiBaseScore(cqiDetails.baseScore());
    }

    private boolean checkAndMarkFailed(TeamParticipation tp, List<Student> students) {
        boolean hasFailed = false;

        boolean hasFailedStudent = students.stream()
                .anyMatch(s -> s.getCommitCount() != null && s.getCommitCount() < 10);
        if (hasFailedStudent) {
            hasFailed = true;
        }

        if (!hasFailed) {
            String teamName = tp.getName();
            if (pairProgrammingService.hasAttendanceData()
                    && pairProgrammingService.hasTeamAttendance(teamName)
                    && !pairProgrammingService.isPairedMandatorySessions(teamName)) {
                hasFailed = true;
            }
        }

        if (hasFailed) {
            tp.setIsFailed(true);
            tp.setCqi(0.0);
            tp.setIsSuspicious(false);
            tp.setAnalysisStatus(TeamAnalysisStatus.DONE);
            tp.setCqiEffortBalance(null);
            tp.setCqiLocBalance(null);
            tp.setCqiTemporalSpread(null);
            tp.setCqiOwnershipSpread(null);
            tp.setCqiPairProgramming(null);
            tp.setCqiPairProgrammingStatus(null);
            tp.setCqiBaseScore(null);
            teamParticipationRepository.save(tp);
        }

        return hasFailed;
    }

    /**
     * Checks whether a team should be skipped during the AI or simple analysis phase.
     *
     * @param tp the team participation to check
     * @return {@code true} if the team is marked as failed and should be skipped
     */
    public boolean shouldSkipTeam(TeamParticipation tp) {
        return Boolean.TRUE.equals(tp.getIsFailed());
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
