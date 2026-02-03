package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.service.AnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
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
import java.util.concurrent.*;

import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIPenaltyDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;

@Service
@Slf4j
public class RequestService {

    private final RepositoryFetchingService repositoryFetchingService;
    private final AnalysisService analysisService;
    private final de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator balanceCalculator;
    private final ContributionFairnessService fairnessService;
    private final AnalysisStateService analysisStateService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final CommitChunkerService commitChunkerService;

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
            AnalyzedChunkRepository analyzedChunkRepository,
            GitContributionAnalysisService gitContributionAnalysisService,
            CommitPreFilterService commitPreFilterService,
            CQICalculatorService cqiCalculatorService,
            CommitChunkerService commitChunkerService) {
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
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.commitPreFilterService = commitPreFilterService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.commitChunkerService = commitChunkerService;
    }

    /**
     * Fetches, analyzes, and saves repository data using the provided Artemis
     * credentials.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     */
    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId) {
        fetchAnalyzeAndSaveRepositories(credentials, exerciseId, Integer.MAX_VALUE);
    }

    /**
     * Fetches, analyzes, and saves repository data with a limit on the number of
     * teams.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     * @param maxTeams    Maximum number of teams to analyze (use Integer.MAX_VALUE
     *                    for all)
     * @return List of ClientResponseDTO with analysis results
     */
    public List<ClientResponseDTO> fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId,
                                                                   int maxTeams) {
        log.info("=== Starting Analysis Pipeline ===");
        log.info("Exercise ID: {}", exerciseId);
        log.info("Max teams to analyze: {}", maxTeams == Integer.MAX_VALUE ? "ALL" : maxTeams);

        // Step 1: Fetch and clone repositories from Artemis
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);
        log.info("Fetched {} repositories from Artemis", repositories.size());

        // Step 2: Limit to maxTeams if specified
        if (repositories.size() > maxTeams) {
            log.info("Limiting analysis to first {} teams (out of {})", maxTeams, repositories.size());
            repositories = repositories.subList(0, maxTeams);
        }

        // Step 3: Analyze contributions for each repository
        log.info("Analyzing contributions for {} teams...", repositories.size());
        Map<Long, AuthorContributionDTO> contributionData = getContributionData(repositories);

        // Step 4: Save results to the database
        log.info("Saving results to database...");
        List<ClientResponseDTO> results = saveResults(repositories, contributionData);

        log.info("=== Analysis Pipeline Complete ===");
        log.info("Total teams analyzed: {}", results.size());

        return results;
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
     * @return List of ClientResponseDTO with saved results
     */
    public List<ClientResponseDTO> saveResults(List<TeamRepositoryDTO> repositories, Map<Long, AuthorContributionDTO> contributionData) {
        // Step 1: Clear existing data in database (full refresh approach)
        // TODO: Implement a better strategy for updating existing records instead of deleting all data
        clearDatabase();

        // Step 2: Process and save each repository result
        List<ClientResponseDTO> results = new ArrayList<>();
        for (TeamRepositoryDTO repo : repositories) {
            ClientResponseDTO result = saveSingleResult(repo, contributionData);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
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
        // Step 1: Save tutor information
        ParticipantDTO tut = repo.participation().team().owner();
        Tutor tutor = null;
        if (tut == null) {
            log.warn("No tutor found for team: {}", repo.participation().team().name());
        } else {
            tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
            tutorRepository.save(tutor);
        }

        // Step 2: Save team participation (CQI and isSuspicious will be updated later)
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                team.shortName(), participation.repositoryUri(), participation.submissionCount());
        teamParticipationRepository.save(teamParticipation);

        // Step 3: Save students with their contribution metrics
        List<Student> students = new ArrayList<>();
        List<StudentAnalysisDTO> studentAnalysisDTOS = new ArrayList<>();

        for (ParticipantDTO student : repo.participation().team().students()) {
            AuthorContributionDTO contributionDTO = contributionData.get(student.id());

            // Handle the case where a student made no contributions
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

        // Step 4: Save team repository with VCS logs
        TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(),
                repo.error());

        List<VCSLog> vcsLogs = repo.vcsLogs().stream().map(log -> new VCSLog(teamRepo, log.commitHash(), log.email()))
                .toList();

        teamRepo.setVcsLogs(vcsLogs);
        teamRepositoryRepository.save(teamRepo);

        log.info("Processed repository for team: {}", team.name());

        // Step 5: Calculate CQI using effort-based fairness analysis
        Double cqi = null;
        boolean isSuspicious = false;
        List<AnalyzedChunkDTO> analysisHistory = null;
        List<OrphanCommitDTO> orphanCommits = null;

        // Step 5a: Detect orphan commits first
        try {
            RepositoryAnalysisResultDTO analysisResult = gitContributionAnalysisService
                    .analyzeRepositoryWithOrphans(repo);
            orphanCommits = analysisResult.orphanCommits();
            if (orphanCommits != null && !orphanCommits.isEmpty()) {
                log.info("Found {} orphan commits for team {}", orphanCommits.size(), team.name());
            }
        } catch (Exception e) {
            log.warn("Failed to detect orphan commits for team {}: {}", team.name(), e.getMessage());
        }

        CQIResultDTO cqiDetails = null;

        // Step 5b: Try effort-based fairness analysis (primary method)
        try {
            FairnessReportDTO fairnessReport = fairnessService.analyzeFairness(repo);
            if (fairnessReport.balanceScore() > 0 || !fairnessReport.authorDetails().isEmpty()) {
                cqi = fairnessReport.balanceScore();
                isSuspicious = fairnessReport.requiresManualReview();
                analysisHistory = fairnessReport.analyzedChunks();
                cqiDetails = fairnessReport.cqiResult();
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

        // Step 5c: Fallback to CQI calculator with pre-filtered commits if fairness analysis failed
        if (cqi == null || cqi == 0.0) {
            try {
                // Try pre-filtered LoC-based CQI calculation
                if (repo.localPath() != null) {
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);

                    // Apply pre-filter to remove trivial commits
                    CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);

                    log.info("Pre-filter for team {}: {} of {} commits will be analyzed",
                            team.name(), filterResult.chunksToAnalyze().size(), allChunks.size());

                    // Calculate CQI using fallback (LoC-based, no LLM)
                    CQIResultDTO cqiResult = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(),
                            students.size(),
                            filterResult.summary()
                    );

                    cqi = cqiResult.cqi();
                    cqiDetails = cqiResult;
                    log.debug("Fallback CQI for team {}: {}", team.name(), cqi);
                }
            } catch (Exception e) {
                log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
            }

            // Step 5d: Last resort - simple commit-count based balance
            if (cqi == null || cqi == 0.0) {
                Map<String, Integer> commitCounts = new HashMap<>();
                students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                if (!commitCounts.isEmpty()) {
                    cqi = balanceCalculator.calculate(commitCounts);
                }
            }
        }

        // Step 6: Update TeamParticipation with CQI and isSuspicious flag
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(isSuspicious);

        // Step 6a: Persist CQI components for later reconstruction
        if (cqiDetails != null && cqiDetails.components() != null) {
            teamParticipation.setCqiEffortBalance(cqiDetails.components().effortBalance());
            teamParticipation.setCqiLocBalance(cqiDetails.components().locBalance());
            teamParticipation.setCqiTemporalSpread(cqiDetails.components().temporalSpread());
            teamParticipation.setCqiOwnershipSpread(cqiDetails.components().ownershipSpread());
            teamParticipation.setCqiBaseScore(cqiDetails.baseScore());
            teamParticipation.setCqiPenaltyMultiplier(cqiDetails.penaltyMultiplier());
            if (cqiDetails.penalties() != null) {
                teamParticipation.setCqiPenalties(serializePenalties(cqiDetails.penalties()));
            }
        }

        teamParticipationRepository.save(teamParticipation);

        // Step 7: Return the assembled client response DTO
        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.team().id(),
                participation.team().name(),
                participation.submissionCount(),
                studentAnalysisDTOS,
                cqi,
                isSuspicious,
                cqiDetails,
                analysisHistory,
                orphanCommits);
    }

    /**
     * Fetches, analyzes, and saves repositories with streaming updates.
     * Tracks progress via AnalysisStateService and skips already-analyzed teams.
     * Uses interruptible ExecutorService instead of parallelStream for proper cancellation.
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   Exercise ID to fetch repositories for
     * @param eventEmitter Consumer to emit progress events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                      java.util.function.Consumer<Object> eventEmitter) {
        ExecutorService executor = null;
        try {
            // Step 1: Fetch all participations from Artemis
            List<ParticipationDTO> participations = repositoryFetchingService.fetchParticipations(credentials,
                    exerciseId);

            // Step 2: Filter participations with valid repositories
            List<ParticipationDTO> validParticipations = participations.stream()
                    .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                    .toList();

            // Step 3: Filter out already-analyzed teams
            List<ParticipationDTO> teamsToAnalyze = validParticipations.stream()
                    .filter(p -> !isTeamAlreadyAnalyzed(p.id()))
                    .toList();

            int totalToProcess = teamsToAnalyze.size();
            log.info("Found {} teams total, {} need analysis", validParticipations.size(), totalToProcess);

            // Get current status to check if resuming
            AnalysisStatus currentStatus = analysisStateService.getStatus(exerciseId);
            int alreadyProcessed = 0;

            if (currentStatus.getState() == AnalysisState.PAUSED) {
                // Resuming: use existing total and processed counts
                alreadyProcessed = currentStatus.getProcessedTeams();
                log.info("Resuming analysis: {} teams already processed, {} remaining",
                        alreadyProcessed, totalToProcess - alreadyProcessed);
            } else {
                // Starting fresh
                analysisStateService.startAnalysis(exerciseId, totalToProcess);
            }

            // Emit total count
            eventEmitter.accept(Map.of("type", "START", "total", totalToProcess,
                    "alreadyProcessed", alreadyProcessed));

            // Thread-safe counter for progress
            java.util.concurrent.atomic.AtomicInteger processedCount =
                    new java.util.concurrent.atomic.AtomicInteger(alreadyProcessed);

            // Use ExecutorService with thread pool for interruptible parallel processing
            int threadCount = Math.min(teamsToAnalyze.size(),
                    Runtime.getRuntime().availableProcessors());
            executor = Executors.newFixedThreadPool(threadCount);

            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch completionLatch = new CountDownLatch(teamsToAnalyze.size());

            // Submit tasks for each team
            for (ParticipationDTO participation : teamsToAnalyze) {
                Future<?> future = executor.submit(() -> {
                    try {
                        // Check if analysis was cancelled/paused (check more frequently)
                        if (!analysisStateService.isRunning(exerciseId)) {
                            log.debug("Analysis stopped, skipping team {}",
                                    participation.team() != null ? participation.team().name() : "Unknown");
                            return;
                        }

                        // Check if already processed (for resume scenario)
                        if (isTeamAlreadyAnalyzed(participation.id())) {
                            log.debug("Team {} already analyzed, skipping", participation.id());
                            processedCount.incrementAndGet();
                            return;
                        }

                        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

                        // Check interruption status
                        if (Thread.currentThread().isInterrupted()) {
                            log.debug("Thread interrupted, stopping processing");
                            return;
                        }

                        try {
                            // Update state: downloading
                            analysisStateService.updateProgress(exerciseId, teamName, "DOWNLOADING",
                                    processedCount.get());

                            // Check again before expensive operations
                            if (!analysisStateService.isRunning(exerciseId) ||
                                    Thread.currentThread().isInterrupted()) {
                                return;
                            }

                            // Clone
                            TeamRepositoryDTO repo = repositoryFetchingService.cloneTeamRepository(
                                    participation, credentials, exerciseId);

                            // Update state: analyzing
                            analysisStateService.updateProgress(exerciseId, teamName, "ANALYZING",
                                    processedCount.get());

                            // Check again
                            if (!analysisStateService.isRunning(exerciseId) ||
                                    Thread.currentThread().isInterrupted()) {
                                return;
                            }

                            // Analyze
                            Map<Long, AuthorContributionDTO> contributions =
                                    analysisService.analyzeRepository(repo);

                            // Check again before saving
                            if (!analysisStateService.isRunning(exerciseId) ||
                                    Thread.currentThread().isInterrupted()) {
                                return;
                            }

                            // Save (persistent storage)
                            ClientResponseDTO dto = saveSingleResult(repo, contributions);

                            int currentProcessed = processedCount.incrementAndGet();

                            // Update state with new progress count
                            analysisStateService.updateProgress(exerciseId, teamName, "DONE", currentProcessed);

                            // Emit result (synchronized to ensure thread safety)
                            synchronized (eventEmitter) {
                                eventEmitter.accept(Map.of("type", "UPDATE", "data", dto));
                            }
                        } catch (Exception e) {
                            log.error("Error processing participation {} (team: {})",
                                    participation.id(), teamName, e);
                            processedCount.incrementAndGet();
                            // Continue with other teams, don't fail entire analysis
                        } finally {
                            completionLatch.countDown();
                        }
                    } catch (Exception e) {
                        log.error("Unexpected error in processing thread for participation {}",
                                participation.id(), e);
                        completionLatch.countDown();
                    }
                });
                futures.add(future);
            }

            // Wait for all tasks to complete or be cancelled
            try {
                completionLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Waiting for completion interrupted");
            }

            // Check if we should complete or if analysis was paused
            if (analysisStateService.isRunning(exerciseId)) {
                // Complete the analysis
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
            } else {
                // Analysis was paused/cancelled
                log.info("Analysis paused/cancelled for exercise {}", exerciseId);
                eventEmitter.accept(Map.of("type", "PAUSED",
                        "processed", processedCount.get(),
                        "total", totalToProcess));
            }

        } catch (Exception e) {
            log.error("Analysis failed for exercise {}", exerciseId, e);
            analysisStateService.failAnalysis(exerciseId, e.getMessage());
            eventEmitter.accept(Map.of("type", "ERROR", "message", e.getMessage()));
        } finally {
            // Shutdown executor and interrupt any remaining tasks
            if (executor != null) {
                log.info("Shutting down executor for exercise {}", exerciseId);
                executor.shutdownNow(); // Interrupts all running tasks
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("Executor did not terminate within 5 seconds");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for executor shutdown");
                }
            }
        }
    }

    /**
     * Check if a team has been fully analyzed (has CQI calculated).
     * A team is considered "analyzed" only if it has a CQI value,
     * not just if the participation record exists.
     */
    private boolean isTeamAlreadyAnalyzed(Long participationId) {
        return teamParticipationRepository.existsByParticipationAndCqiIsNotNull(participationId);
    }

    /**
     * Retrieves all stored repository data from the database and assembles it into
     * ClientResponseDTOs.
     *
     * @return List of ClientResponseDTO containing the assembled data
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        log.info("RequestService: Initiating data extraction from database");

        // Step 1: Fetch all TeamParticipation records from database
        List<TeamParticipation> participations = teamParticipationRepository.findAll();

        // Step 2: Map and assemble the data into ClientResponseDTOs
        List<ClientResponseDTO> responseDTOs = participations.stream()
                .map(participation -> {
                    // Step 2a: Fetch students for this participation
                    List<Student> students = studentRepository.findAllByTeam(participation);

                    Tutor tutor = participation.getTutor();

                    // Step 2b: Map students to StudentAnalysisDTOs
                    List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream()
                            .map(student -> (new StudentAnalysisDTO(
                                    student.getName(),
                                    student.getCommitCount(),
                                    student.getLinesAdded(),
                                    student.getLinesDeleted(),
                                    student.getLinesChanged())))
                            .toList();

                    // Step 2c: Get persisted CQI and isSuspicious values
                    Double cqi = participation.getCqi();
                    Boolean isSuspicious = participation.getIsSuspicious() != null ? participation.getIsSuspicious()
                            : false;

                    // Step 2d: Fallback - recalculate if CQI is null (legacy data)
                    if (cqi == null) {
                        Map<String, Integer> commitCounts = new HashMap<>();
                        students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                        if (!commitCounts.isEmpty()) {
                            cqi = balanceCalculator.calculate(commitCounts);
                        }
                    }

                    // Step 2e: Reconstruct CQI details from persisted components
                    CQIResultDTO cqiDetails = reconstructCqiDetails(participation);

                    // Step 2f: Assemble final response DTO
                    return new ClientResponseDTO(
                            tutor != null ? tutor.getName() : "Unassigned",
                            participation.getTeam(),
                            participation.getName(),
                            participation.getSubmissionCount(),
                            studentAnalysisDTOS,
                            cqi,
                            isSuspicious,
                            cqiDetails,
                            loadAnalyzedChunks(participation),
                            null); // Orphan commits are not persisted, only shown during live analysis
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
                            dto.complexity(),
                            dto.novelty(),
                            dto.confidence(),
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
                            chunk.getComplexity() != null ? chunk.getComplexity() : 0.0,
                            chunk.getNovelty() != null ? chunk.getNovelty() : 0.0,
                            chunk.getConfidence() != null ? chunk.getConfidence() : 0.0,
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

    /**
     * Serializes a list of CQI penalties to JSON for database storage.
     */
    private String serializePenalties(List<CQIPenaltyDTO> penalties) {
        try {
            return objectMapper.writeValueAsString(penalties);
        } catch (Exception e) {
            log.warn("Failed to serialize CQI penalties: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Deserializes a JSON string to a list of CQI penalties.
     */
    private List<CQIPenaltyDTO> deserializePenalties(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CQIPenaltyDTO.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize CQI penalties: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reconstructs a CQIResultDTO from persisted TeamParticipation fields.
     * Returns null if no CQI components were stored.
     */
    private CQIResultDTO reconstructCqiDetails(TeamParticipation participation) {
        // Check if we have stored CQI component data
        if (participation.getCqiEffortBalance() == null && participation.getCqiLocBalance() == null
                && participation.getCqiTemporalSpread() == null && participation.getCqiOwnershipSpread() == null) {
            return null;
        }

        // Reconstruct components
        ComponentScoresDTO components = new ComponentScoresDTO(
                participation.getCqiEffortBalance() != null ? participation.getCqiEffortBalance() : 0.0,
                participation.getCqiLocBalance() != null ? participation.getCqiLocBalance() : 0.0,
                participation.getCqiTemporalSpread() != null ? participation.getCqiTemporalSpread() : 0.0,
                participation.getCqiOwnershipSpread() != null ? participation.getCqiOwnershipSpread() : 0.0
        );

        // Reconstruct penalties
        List<CQIPenaltyDTO> penalties = deserializePenalties(participation.getCqiPenalties());

        // Build the full CQI result
        return new CQIResultDTO(
                participation.getCqi() != null ? participation.getCqi() : 0.0,
                components,
                penalties,
                participation.getCqiBaseScore() != null ? participation.getCqiBaseScore() : 0.0,
                participation.getCqiPenaltyMultiplier() != null ? participation.getCqiPenaltyMultiplier() : 1.0,
                null  // FilterSummary is not persisted - only available during live analysis
        );
    }
}
