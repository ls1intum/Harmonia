package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.service.AnalysisService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.*;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.analysis.service.AnalysisStateService;

@Service
@Slf4j
public class RequestService {

    private final RepositoryFetchingService repositoryFetchingService;
    private final AnalysisService analysisService;
    private final de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator balanceCalculator;
    private final ContributionFairnessService fairnessService;
    private final AnalysisStateService analysisStateService;

    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RequestService(
            RepositoryFetchingService repositoryFetchingService,
            AnalysisService analysisService,
            de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator balanceCalculator,
            ContributionFairnessService fairnessService,
            AnalysisStateService analysisStateService,
            TeamRepositoryRepository teamRepositoryRepository,
            TeamParticipationRepository teamParticipationRepository,
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository) {
        this.repositoryFetchingService = repositoryFetchingService;
        this.analysisService = analysisService;
        this.balanceCalculator = balanceCalculator;
        this.fairnessService = fairnessService;
        this.analysisStateService = analysisStateService;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
    }

    /**
     * Fetches, analyzes, and saves repository data using the provided Artemis
     * credentials.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     */
    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId) {
        // Fetch and clone repositories
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);

        // Analyze contributions
        Map<Long, AuthorContributionDTO> contributionData = getContributionData(repositories);

        // Save results to the database
        saveResults(repositories, contributionData);
    }

    /**
     * Fetches and clones all repositories from Artemis using dynamic credentials.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        log.info("RequestService: Initiating repository fetch and clone process");
        return repositoryFetchingService.fetchAndCloneRepositories(credentials, exerciseId);
    }

    /**
     * Analyzes contributions for the given list of repositories.
     *
     * @param repositories List of TeamRepositoryDTO to be analyzed
     * @return Map of Participant ID to an array of contribution metrics (e.g.,
     *         lines added, lines deleted)
     */
    public Map<Long, AuthorContributionDTO> getContributionData(List<TeamRepositoryDTO> repositories) {
        return analysisService.analyzeContributions(repositories);
    }

    /**
     * Saves the fetched repository information into the database.
     *
     * @param repositories     List of TeamRepositoryDTO to be saved
     * @param contributionData Map of Participant ID to an array of contribution
     *                         metrics (e.g., lines added, lines deleted)
     */
    public void saveResults(List<TeamRepositoryDTO> repositories, Map<Long, AuthorContributionDTO> contributionData) {
        // TODO: Implement a better strategy for updating existing records instead of
        // deleting all data
        // Clear existing data in database tables. We assume a full refresh of all data
        // is intended, effectively treating the run as idempotent
        clearDatabase();

        for (TeamRepositoryDTO repo : repositories) {
            saveSingleResult(repo, contributionData);
        }
    }

    /**
     * Clears all data from the database tables.
     */
    public void clearDatabase() {
        // Must delete child entities first due to foreign key constraints
        analyzedChunkRepository.deleteAll();
        teamRepositoryRepository.deleteAll();
        studentRepository.deleteAll();
        teamParticipationRepository.deleteAll();
        tutorRepository.deleteAll();
    }

    /**
     * Saves a single repository analysis result to the database.
     *
     * @param repo             Repository data to save
     * @param contributionData Contribution data by student ID
     * @return Client response DTO with calculated metrics
     */
    public ClientResponseDTO saveSingleResult(TeamRepositoryDTO repo,
            Map<Long, AuthorContributionDTO> contributionData) {
        // Save tutor
        ParticipantDTO tut = repo.participation().team().owner();
        Tutor tutor = null;
        if (tut == null) {
            log.warn("No tutor found for team: {}", repo.participation().team().name());
        } else {
            tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
            tutorRepository.save(tutor);
        }

        // Save team participation
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                team.shortName(), participation.repositoryUri(), participation.submissionCount());
        teamParticipationRepository.save(teamParticipation);

        // Save students with contributions
        List<Student> students = new ArrayList<>();
        List<StudentAnalysisDTO> studentAnalysisDTOS = new ArrayList<>();

        for (ParticipantDTO student : repo.participation().team().students()) {
            AuthorContributionDTO contributionDTO = contributionData.get(student.id());

            // Handle the case where a student made no contributions (e.g., if they were
            // registered but never committed)
            if (contributionDTO == null) {
                contributionDTO = new AuthorContributionDTO(0, 0, 0);
            }

            students.add(new Student(student.id(), student.login(), student.name(), student.email(), teamParticipation,
                    contributionDTO.commitCount(), contributionDTO.linesAdded(), contributionDTO.linesDeleted(),
                    contributionDTO.linesAdded() + contributionDTO.linesDeleted()));

            studentAnalysisDTOS.add(new StudentAnalysisDTO(
                    student.name(),
                    contributionDTO.commitCount(),
                    contributionDTO.linesAdded(),
                    contributionDTO.linesDeleted(),
                    contributionDTO.linesAdded() + contributionDTO.linesDeleted()));
        }
        studentRepository.saveAll(students);

        // Save team repository
        TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(),
                repo.error());

        // Process VCS logs
        List<VCSLog> vcsLogs = repo.vcsLogs().stream().map(log -> new VCSLog(teamRepo, log.commitHash(), log.email()))
                .toList();

        // Save the TeamRepository (through cascade, VCSLogs will also be saved)
        teamRepo.setVcsLogs(vcsLogs);
        teamRepositoryRepository.save(teamRepo);

        log.info("Processed repository for team: {}", team.name());

        // Calculate CQI using effort-based fairness analysis
        Double cqi = null;
        boolean isSuspicious = false;
        List<AnalyzedChunkDTO> analysisHistory = null;

        try {
            // Try effort-based fairness analysis first
            FairnessReportDTO fairnessReport = fairnessService.analyzeFairness(repo);
            if (fairnessReport.balanceScore() > 0 || !fairnessReport.authorDetails().isEmpty()) {
                cqi = fairnessReport.balanceScore();
                isSuspicious = fairnessReport.requiresManualReview();
                analysisHistory = fairnessReport.analyzedChunks();
                log.debug("Fairness analysis complete for team {}: score={}, suspicious={}, chunks={}",
                        team.name(), cqi, isSuspicious, analysisHistory != null ? analysisHistory.size() : 0);

                // Persist analyzed chunks to database
                if (analysisHistory != null && !analysisHistory.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, analysisHistory);
                }
            }
        } catch (Exception e) {
            log.warn("Fairness analysis failed for team {}, falling back to balance calculator: {}",
                    team.name(), e.getMessage());
        }

        // Fallback to simple balance calculator if fairness analysis failed or returned
        // 0
        if (cqi == null || cqi == 0.0) {
            Map<String, Integer> commitCounts = new HashMap<>();
            students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
            if (!commitCounts.isEmpty()) {
                cqi = balanceCalculator.calculate(commitCounts);
            }
        }

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.team().id(),
                participation.team().name(),
                participation.submissionCount(),
                studentAnalysisDTOS,
                cqi,
                isSuspicious,
                analysisHistory);
    }

    /**
     * Fetches, analyzes, and saves repositories with streaming updates.
     * Tracks progress via AnalysisStateService and skips already-analyzed teams.
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   Exercise ID to fetch repositories for
     * @param eventEmitter Consumer to emit progress events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
            java.util.function.Consumer<Object> eventEmitter) {
        try {
            List<ParticipationDTO> participations = repositoryFetchingService.fetchParticipations(credentials,
                    exerciseId);

            // Filter participations with repositories
            List<ParticipationDTO> validParticipations = participations.stream()
                    .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                    .toList();

            // Filter out already-analyzed teams
            List<ParticipationDTO> teamsToAnalyze = validParticipations.stream()
                    .filter(p -> !isTeamAlreadyAnalyzed(p.id()))
                    .toList();

            int totalToProcess = teamsToAnalyze.size();
            log.info("Found {} teams total, {} need analysis", validParticipations.size(), totalToProcess);

            // Start tracking in state service
            analysisStateService.startAnalysis(exerciseId, totalToProcess);

            // Emit total count
            eventEmitter.accept(Map.of("type", "START", "total", totalToProcess));

            // Thread-safe counter for progress
            java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // Process repositories in parallel
            teamsToAnalyze.parallelStream().forEach(participation -> {
                // Check if analysis was cancelled
                if (!analysisStateService.isRunning(exerciseId)) {
                    return;
                }

                String teamName = participation.team() != null ? participation.team().name() : "Unknown";

                try {
                    // Update state: downloading
                    analysisStateService.updateProgress(exerciseId, teamName, "DOWNLOADING", processedCount.get());

                    // Clone
                    TeamRepositoryDTO repo = repositoryFetchingService.cloneTeamRepository(participation, credentials,
                            exerciseId);

                    // Update state: analyzing
                    analysisStateService.updateProgress(exerciseId, teamName, "ANALYZING", processedCount.get());

                    // Analyze
                    Map<Long, AuthorContributionDTO> contributions = analysisService.analyzeRepository(repo);

                    // Save
                    ClientResponseDTO dto = saveSingleResult(repo, contributions);

                    int currentProcessed = processedCount.incrementAndGet();

                    // Update state with new progress count
                    analysisStateService.updateProgress(exerciseId, teamName, "DONE", currentProcessed);

                    // Emit result (synchronized to ensure thread safety)
                    synchronized (eventEmitter) {
                        eventEmitter.accept(Map.of("type", "UPDATE", "data", dto));
                    }
                } catch (Exception e) {
                    log.error("Error processing participation {} (team: {})", participation.id(), teamName, e);
                    processedCount.incrementAndGet();
                    // Continue with other teams, don't fail entire analysis
                }
            });

            // Complete the analysis
            analysisStateService.completeAnalysis(exerciseId);
            eventEmitter.accept(Map.of("type", "DONE"));

        } catch (Exception e) {
            log.error("Analysis failed for exercise {}", exerciseId, e);
            analysisStateService.failAnalysis(exerciseId, e.getMessage());
            eventEmitter.accept(Map.of("type", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * Check if a team has already been analyzed (has data in the database).
     */
    private boolean isTeamAlreadyAnalyzed(Long participationId) {
        return teamParticipationRepository.existsByParticipation(participationId);
    }

    /**
     * Retrieves all stored repository data from the database and assembles it into
     * ClientResponseDTOs.
     *
     * @return List of ClientResponseDTO containing the assembled data
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        log.info("RequestService: Initiating data extraction from database");

        // Fetch all TeamParticipation records
        List<TeamParticipation> participations = teamParticipationRepository.findAll();

        // Map and assemble the data into ClientResponseDTOs
        List<ClientResponseDTO> responseDTOs = participations.stream()
                .map(participation -> {
                    List<Student> students = studentRepository.findAllByTeam(participation);

                    Tutor tutor = participation.getTutor();

                    List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream()
                            .map(student -> (new StudentAnalysisDTO(
                                    student.getName(),
                                    student.getCommitCount(),
                                    student.getLinesAdded(),
                                    student.getLinesDeleted(),
                                    student.getLinesChanged())))
                            .toList();

                    // Calculate CQI (Balance score only for now)
                    Double cqi = null;
                    Map<String, Integer> commitCounts = new HashMap<>();
                    students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));

                    if (!commitCounts.isEmpty()) {
                        double balanceScore = balanceCalculator.calculate(commitCounts);
                        cqi = balanceScore; // 100% balance for now
                    }

                    return new ClientResponseDTO(
                            tutor != null ? tutor.getName() : "Unassigned",
                            participation.getTeam(),
                            participation.getName(),
                            participation.getSubmissionCount(),
                            studentAnalysisDTOS,
                            cqi,
                            false,
                            loadAnalyzedChunks(participation));
                })
                .toList();

        log.info("RequestService: Extracted {} team participation records from the database.", responseDTOs.size());
        return responseDTOs;
    }

    /**
     * Saves analyzed chunks to the database for a given participation.
     */
    private void saveAnalyzedChunks(TeamParticipation participation, List<AnalyzedChunkDTO> chunks) {
        try {
            List<AnalyzedChunk> entities = chunks.stream()
                    .map(dto -> new AnalyzedChunk(
                            participation,
                            dto.id(),
                            dto.authorEmail(),
                            dto.authorName(),
                            dto.classification(),
                            dto.effortScore(),
                            dto.reasoning(),
                            String.join(",", dto.commitShas()),
                            serializeCommitMessages(dto.commitMessages()),
                            dto.timestamp(),
                            dto.linesChanged(),
                            dto.isBundled(),
                            dto.chunkIndex(),
                            dto.totalChunks(),
                            dto.isError(),
                            dto.errorMessage()))
                    .toList();
            analyzedChunkRepository.saveAll(entities);
            log.debug("Saved {} analyzed chunks for team {}", entities.size(), participation.getName());
        } catch (Exception e) {
            log.warn("Failed to save analyzed chunks for team {}: {}", participation.getName(), e.getMessage());
        }
    }

    /**
     * Loads analyzed chunks from the database for a given participation.
     */
    private List<AnalyzedChunkDTO> loadAnalyzedChunks(TeamParticipation participation) {
        try {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            if (chunks.isEmpty()) {
                return null;
            }
            return chunks.stream()
                    .map(chunk -> new AnalyzedChunkDTO(
                            chunk.getChunkIdentifier(),
                            chunk.getAuthorEmail(),
                            chunk.getAuthorName(),
                            chunk.getClassification(),
                            chunk.getEffortScore() != null ? chunk.getEffortScore() : 0.0,
                            chunk.getReasoning(),
                            List.of(chunk.getCommitShas().split(",")),
                            parseCommitMessages(chunk.getCommitMessages()),
                            chunk.getTimestamp(),
                            chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0,
                            Boolean.TRUE.equals(chunk.getIsBundled()),
                            chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                            chunk.getTotalChunks() != null ? chunk.getTotalChunks() : 1,
                            Boolean.TRUE.equals(chunk.getIsError()),
                            chunk.getErrorMessage()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load analyzed chunks for team {}: {}", participation.getName(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseCommitMessages(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializeCommitMessages(List<String> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            return "[]";
        }
    }
}
