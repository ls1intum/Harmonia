package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.service.AnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.*;
import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.cqi.CqiRecalculationService;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.dto.LlmTokenUsageDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.ai.dto.FairnessReportWithUsageDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrator service for the main analysis pipeline.
 * Acts as a facade that coordinates the three analysis phases:
 *
 * <ol>
 *   <li><b>Repository download</b> — clones team repos from Artemis via
 *       {@link GitOperationsService}</li>
 *   <li><b>Git analysis</b> — extracts commit metrics (lines, authors) via
 *       {@link AnalysisService} / {@link GitContributionAnalysisService}</li>
 *   <li><b>AI analysis</b> — computes CQI scores via
 *       {@link ContributionFairnessService}</li>
 * </ol>
 *
 * <p>Also provides query methods for persisted results and task lifecycle
 * management (start / stop / status).</p>
 */
@Service
@Slf4j
public class RequestService {

    private final ArtemisClientService artemisClientService;
    private final GitOperationsService gitOperationsService;
    private final AnalysisService analysisService;
    private final de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator balanceCalculator;
    private final ContributionFairnessService fairnessService;
    private final AnalysisStateService analysisStateService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final CommitChunkerService commitChunkerService;
    private final TransactionTemplate transactionTemplate;

    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;
    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final CqiRecalculationService cqiRecalculationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Active download/git-analysis executors by exerciseId (for cancellation). */
    private final Map<Long, ExecutorService> activeExecutors = new ConcurrentHashMap<>();

    /** Main stream-analysis threads by exerciseId (for interrupt-based cancellation). */
    private final Map<Long, Thread> runningStreamTasks = new ConcurrentHashMap<>();

    /** Running Future tasks by exerciseId (used by RequestResource). */
    private final Map<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();

    public RequestService(
            ArtemisClientService artemisClientService,
            GitOperationsService gitOperationsService,
            AnalysisService analysisService,
            de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator balanceCalculator,
            ContributionFairnessService fairnessService,
            AnalysisStateService analysisStateService,
            TeamRepositoryRepository teamRepositoryRepository,
            TeamParticipationRepository teamParticipationRepository,
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository,
            ExerciseTemplateAuthorRepository templateAuthorRepository,
            ExerciseEmailMappingRepository emailMappingRepository,
            GitContributionAnalysisService gitContributionAnalysisService,
            CommitPreFilterService commitPreFilterService,
            CQICalculatorService cqiCalculatorService,
            CommitChunkerService commitChunkerService,
            TransactionTemplate transactionTemplate,
            CqiRecalculationService cqiRecalculationService) {
        this.artemisClientService = artemisClientService;
        this.gitOperationsService = gitOperationsService;
        this.analysisService = analysisService;
        this.balanceCalculator = balanceCalculator;
        this.fairnessService = fairnessService;
        this.analysisStateService = analysisStateService;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.templateAuthorRepository = templateAuthorRepository;
        this.emailMappingRepository = emailMappingRepository;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.commitPreFilterService = commitPreFilterService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.commitChunkerService = commitChunkerService;
        this.transactionTemplate = transactionTemplate;
        this.cqiRecalculationService = cqiRecalculationService;
    }

    // =====================================================================
    //  Synchronous analysis pipeline
    // =====================================================================

    /**
     * Runs the full analysis pipeline synchronously (all phases, no streaming).
     *
     * @param credentials Artemis credentials
     * @param exerciseId  exercise ID to analyze
     */
    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId) {
        fetchAnalyzeAndSaveRepositories(credentials, exerciseId, Integer.MAX_VALUE);
    }

    /**
     * Runs the full analysis pipeline synchronously with a team limit.
     *
     * <ol>
     *   <li>Fetch and clone repositories from Artemis</li>
     *   <li>Analyze git contributions (commits, lines of code)</li>
     *   <li>Save results and compute CQI for each team</li>
     * </ol>
     *
     * @param credentials Artemis credentials
     * @param exerciseId  exercise ID to analyze
     * @param maxTeams    maximum number of teams to analyze
     * @return list of analysis results
     */
    public List<ClientResponseDTO> fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId,
                                                                    int maxTeams) {
        // 1) Fetch and clone repositories
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);
        if (repositories.size() > maxTeams) {
            repositories = repositories.subList(0, maxTeams);
        }

        // 2) Analyze git contributions
        Map<Long, AuthorContributionDTO> contributionData = analysisService.analyzeContributions(repositories);

        // 3) Save git results + run AI analysis for each team
        List<ClientResponseDTO> results = new ArrayList<>();
        LlmTokenTotalsDTO runTokenTotals = LlmTokenTotalsDTO.empty();
        for (TeamRepositoryDTO repo : repositories) {
            // Phase 2: Save git analysis
            ClientResponseDTO gitResult = saveGitAnalysisResult(repo, contributionData, exerciseId);
            if (gitResult == null) {
                continue;
            }
            // Phase 3: Run AI analysis
            ClientResponseWithUsage aiResult = saveAIAnalysisResultWithUsage(repo, exerciseId);
            if (aiResult.response() != null) {
                results.add(aiResult.response());
            }
            runTokenTotals = runTokenTotals.merge(aiResult.tokenTotals());
        }

        logTotalUsage("sync", exerciseId, results.size(), runTokenTotals);
        return results;
    }

    /**
     * Fetches participations from Artemis and clones all team repositories.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  exercise ID
     * @return list of cloned repositories with VCS logs
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        // 1) Fetch participations from Artemis
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);

        // 2) Clone repositories and fetch VCS logs in parallel
        return participations.parallelStream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> gitOperationsService.cloneAndFetchLogs(p, credentials, exerciseId))
                .toList();
    }

    // =====================================================================
    //  Streaming analysis pipeline
    // =====================================================================

    /**
     * Runs the full analysis pipeline with streaming progress updates via SSE.
     *
     * <p>Pipeline phases:
     * <ol>
     *   <li><b>Phase 1+2</b> — Download repos and run git analysis in parallel</li>
     *   <li><b>Template author detection</b> — Cross-repo root commit comparison</li>
     *   <li><b>Phase 3</b> — AI analysis (CQI calculation) sequentially per team</li>
     * </ol>
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   exercise ID
     * @param eventEmitter consumer for streaming SSE events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                       java.util.function.Consumer<Object> eventEmitter) {
        runningStreamTasks.put(exerciseId, Thread.currentThread());

        ExecutorService executor = null;
        try {
            // 0) Clear existing data for a clean slate
            transactionTemplate.executeWithoutResult(status ->
                    clearDatabaseForExerciseInternal(exerciseId));

            // 1) Fetch participations from Artemis
            List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                    credentials.serverUrl(), credentials.jwtToken(), exerciseId);

            List<ParticipationDTO> validParticipations = participations.stream()
                    .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                    .toList();

            int totalToProcess = validParticipations.size();
            analysisStateService.startAnalysis(exerciseId, totalToProcess);
            eventEmitter.accept(Map.of("type", "START", "total", totalToProcess));

            // 2) Persist PENDING state and emit initial team data
            initializePendingTeams(validParticipations, exerciseId, false);
            emitPendingTeams(validParticipations, eventEmitter);

            if (validParticipations.isEmpty()) {
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
                return;
            }

            // 3) Phase 1+2: Download and git-analyze repos in parallel
            log.info("Phase 1+2: Downloading and analyzing {} repositories", totalToProcess);
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "GIT_ANALYSIS", "total", totalToProcess));

            Map<Long, TeamRepositoryDTO> clonedRepos = new ConcurrentHashMap<>();
            int threadCount = Math.max(1, Math.min(totalToProcess, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);
            activeExecutors.put(exerciseId, executor);

            CountDownLatch downloadLatch = new CountDownLatch(totalToProcess);
            java.util.concurrent.atomic.AtomicInteger gitAnalyzedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            for (ParticipationDTO participation : validParticipations) {
                executor.submit(() -> {
                    try {
                        if (!analysisStateService.isRunning(exerciseId)) {
                            return;
                        }
                        processDownloadAndGitAnalysis(participation, credentials, exerciseId,
                                clonedRepos, gitAnalyzedCount, totalToProcess, eventEmitter);
                    } catch (Exception e) {
                        handleDownloadError(e, participation, exerciseId, gitAnalyzedCount, eventEmitter);
                    } finally {
                        downloadLatch.countDown();
                    }
                });
            }

            try {
                downloadLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Download+Analysis phase interrupted for exerciseId={}", exerciseId);
            }

            executor.shutdown();
            log.info("Phase 1+2 complete: {} of {} repositories analyzed", clonedRepos.size(), totalToProcess);
            eventEmitter.accept(Map.of("type", "GIT_DONE", "processed", gitAnalyzedCount.get()));

            if (!analysisStateService.isRunning(exerciseId)) {
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", gitAnalyzedCount.get(), "total", totalToProcess));
                return;
            }

            // 4) Template author detection (cross-repo root commit comparison)
            detectTemplateAuthor(exerciseId, clonedRepos, eventEmitter);

            // 5) Phase 3: AI analysis (CQI calculation) — sequential per team
            log.info("Phase 3: AI analysis for {} teams", clonedRepos.size());
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "AI_ANALYSIS", "total", clonedRepos.size()));

            java.util.concurrent.atomic.AtomicInteger aiAnalyzedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            LlmTokenTotalsDTO runTokenTotals = LlmTokenTotalsDTO.empty();

            for (ParticipationDTO participation : validParticipations) {
                if (!analysisStateService.isRunning(exerciseId) || Thread.currentThread().isInterrupted()) {
                    break;
                }

                TeamRepositoryDTO repo = clonedRepos.get(participation.id());
                if (repo == null) {
                    aiAnalyzedCount.incrementAndGet();
                    continue;
                }

                runTokenTotals = processAIAnalysis(participation, repo, exerciseId,
                        aiAnalyzedCount, clonedRepos.size(), runTokenTotals, eventEmitter);
            }

            log.info("Phase 3 complete: AI analysis done for {} teams", aiAnalyzedCount.get());
            logTotalUsage("stream", exerciseId, aiAnalyzedCount.get(), runTokenTotals);

            // 6) Finalize
            if (analysisStateService.isRunning(exerciseId)) {
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
            } else {
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", aiAnalyzedCount.get(), "total", totalToProcess));
            }

        } catch (Exception e) {
            log.error("Analysis failed for exerciseId={}", exerciseId, e);
            analysisStateService.failAnalysis(exerciseId, e.getMessage());
            eventEmitter.accept(Map.of("type", "ERROR", "message", e.getMessage()));
        } finally {
            activeExecutors.remove(exerciseId);
            runningStreamTasks.remove(exerciseId);
            shutdownExecutorQuietly(executor);
        }
    }

    // =====================================================================
    //  Task lifecycle
    // =====================================================================

    /**
     * Stops a running analysis by interrupting its executor and stream thread.
     *
     * @param exerciseId the exercise ID to stop
     */
    public void stopAnalysis(Long exerciseId) {
        log.info("Stopping analysis for exerciseId={}", exerciseId);

        // 1) Cancel the Future task
        Future<?> future = runningFutures.remove(exerciseId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        // 2) Shut down the download/git-analysis executor
        ExecutorService executor = activeExecutors.remove(exerciseId);
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 3) Interrupt the main stream thread (stops AI analysis phase)
        Thread streamThread = runningStreamTasks.remove(exerciseId);
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
        }
    }

    /** Checks if an analysis task is currently running. */
    public boolean isTaskRunning(Long exerciseId) {
        Future<?> future = runningFutures.get(exerciseId);
        return future != null && !future.isDone();
    }

    /** Registers a running task for tracking and cancellation. */
    public void registerRunningTask(Long exerciseId, Future<?> future) {
        runningFutures.put(exerciseId, future);
    }

    /** Unregisters a completed task. */
    public void unregisterRunningTask(Long exerciseId) {
        runningFutures.remove(exerciseId);
    }

    // =====================================================================
    //  Query methods
    // =====================================================================

    /**
     * Returns all persisted teams for a specific exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of team results (empty if no data)
     */
    public List<ClientResponseDTO> getTeamsByExerciseId(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return List.of();
        }
        return participations.stream()
                .map(this::mapParticipationToClientResponse)
                .toList();
    }

    /**
     * Returns all persisted teams across all exercises.
     *
     * @return list of all team results
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        return teamParticipationRepository.findAll().stream()
                .map(this::mapParticipationToClientResponse)
                .toList();
    }

    /**
     * Checks if analyzed data (with CQI scores) exists for an exercise.
     *
     * @param exerciseId the exercise ID
     * @return true if at least one team has a CQI value
     */
    public boolean hasAnalyzedDataForExercise(Long exerciseId) {
        return teamParticipationRepository.existsByExerciseIdAndCqiIsNotNull(exerciseId);
    }

    // =====================================================================
    //  Database clearing
    // =====================================================================

    /**
     * Clears all data for a specific exercise from the database.
     *
     * @param exerciseId the exercise ID to clear
     */
    @Transactional
    public void clearDatabaseForExercise(Long exerciseId) {
        clearDatabaseForExerciseInternal(exerciseId);
    }

    /**
     * Clears all data from the database (all exercises).
     */
    public void clearDatabase() {
        analyzedChunkRepository.deleteAll();
        teamRepositoryRepository.deleteAll();
        studentRepository.deleteAll();
        teamParticipationRepository.deleteAll();
        tutorRepository.deleteAll();
    }

    // =====================================================================
    //  Pair programming support
    // =====================================================================

    /**
     * Recomputes pair programming metrics for all teams in an exercise.
     *
     * @param exerciseId the exercise ID
     * @return number of teams updated
     */
    @Transactional
    public int recomputePairProgrammingForExercise(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (TeamParticipation participation : participations) {
            if (recomputePairProgrammingForParticipation(participation)) {
                updated++;
            }
        }
        log.info("Recomputed pair programming metrics for {}/{} teams in exerciseId={}",
                updated, participations.size(), exerciseId);
        return updated;
    }

    /**
     * Clears pair programming metrics for all teams in an exercise.
     *
     * @param exerciseId the exercise ID
     * @return number of teams cleared
     */
    @Transactional
    public int clearPairProgrammingForExercise(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (TeamParticipation participation : participations) {
            if (clearPairProgrammingFields(participation)) {
                updated++;
            }
        }
        log.info("Cleared pair programming metrics for {}/{} teams in exerciseId={}",
                updated, participations.size(), exerciseId);
        return updated;
    }

    // =====================================================================
    //  Phase 2: Git analysis persistence
    // =====================================================================

    /**
     * Saves git analysis results (commits, lines of code) for a single team.
     * This is Phase 2 of the analysis — no AI/CQI calculation yet.
     *
     * @param repo             repository data
     * @param contributionData contribution metrics by student ID
     * @param exerciseId       exercise ID
     * @return client response with git metrics, or null if cancelled
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        if (!analysisStateService.isRunning(exerciseId)) {
            return null;
        }

        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        // 1) Save tutor
        Tutor tutor = ensureTutor(team);

        // 2) Save/update team participation
        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                        team.shortName(), participation.repositoryUri(), participation.submissionCount()));

        teamParticipation.setExerciseId(exerciseId);
        teamParticipation.setTutor(tutor);
        teamParticipation.setSubmissionCount(participation.submissionCount());
        teamParticipation.setAnalysisStatus(AnalysisStatus.GIT_DONE);
        persistTeamTokenTotals(teamParticipation, LlmTokenTotalsDTO.empty());
        teamParticipationRepository.save(teamParticipation);

        // 3) Save students with contribution metrics
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

        // 4) Save team repository with VCS logs
        TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(),
                repo.error());
        List<VCSLog> vcsLogs = repo.vcsLogs().stream()
                .map(vcsLog -> new VCSLog(teamRepo, vcsLog.commitHash(), vcsLog.email()))
                .toList();
        teamRepo.setVcsLogs(vcsLogs);
        teamRepositoryRepository.save(teamRepo);

        // 5) Calculate git-only CQI components (no AI needed)
        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), team.name(), participation.submissionCount(),
                studentDtos,
                null,  // CQI — Phase 3
                null,  // isSuspicious — Phase 3
                AnalysisStatus.GIT_DONE,
                gitCqiDetails,
                null,  // analysisHistory — Phase 3
                null,  // orphanCommits
                readTeamTokenTotals(teamParticipation));
    }

    // =====================================================================
    //  Phase 3: AI analysis persistence
    // =====================================================================

    /**
     * Performs AI analysis (CQI calculation) for a single team.
     * This is Phase 3 — requires Phase 2 to have completed first.
     *
     * @param repo       repository data
     * @param exerciseId exercise ID
     * @return client response with CQI and AI metrics
     */
    public ClientResponseDTO saveAIAnalysisResult(TeamRepositoryDTO repo, Long exerciseId) {
        return saveAIAnalysisResultWithUsage(repo, exerciseId).response();
    }

    private ClientResponseWithUsage saveAIAnalysisResultWithUsage(TeamRepositoryDTO repo, Long exerciseId) {
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for AI analysis"));

        teamParticipation.setAnalysisStatus(AnalysisStatus.AI_ANALYZING);
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
            if (orphanCommits != null && !orphanCommits.isEmpty()) {
                log.info("Found {} orphan commits for team {}", orphanCommits.size(), team.name());
            }
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
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
                    CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);
                    cqiDetails = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(), students.size(), filterResult.summary());
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
        teamParticipation.setAnalysisStatus(AnalysisStatus.DONE);
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
        CQIResultDTO finalCqiDetails = reconstructCqiDetails(teamParticipation);
        if (finalCqiDetails == null) {
            finalCqiDetails = cqiDetails;
        }

        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        team.id(), team.name(), participation.submissionCount(),
                        studentDtos, finalCqi, isSuspicious, AnalysisStatus.DONE,
                        finalCqiDetails, analysisHistory, orphanCommits,
                        teamTokenTotals, teamParticipation.getOrphanCommitCount()),
                teamTokenTotals);
    }

    // =====================================================================
    //  Streaming pipeline helpers
    // =====================================================================

    /** Downloads and git-analyzes a single team (called from parallel executor). */
    private void processDownloadAndGitAnalysis(ParticipationDTO participation, ArtemisCredentials credentials,
                                                Long exerciseId, Map<Long, TeamRepositoryDTO> clonedRepos,
                                                java.util.concurrent.atomic.AtomicInteger gitAnalyzedCount,
                                                int total, java.util.function.Consumer<Object> eventEmitter) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

        // Step 1: Download
        analysisStateService.updateProgress(exerciseId, teamName, "DOWNLOADING", gitAnalyzedCount.get());
        TeamRepositoryDTO repo = gitOperationsService.cloneAndFetchLogs(participation, credentials, exerciseId);

        if (repo == null) {
            log.warn("Failed to clone repository for team {}", teamName);
            markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
            return;
        }

        clonedRepos.put(participation.id(), repo);

        // Step 2: Git analysis
        analysisStateService.updateProgress(exerciseId, teamName, "GIT_ANALYZING", gitAnalyzedCount.get());
        synchronized (eventEmitter) {
            eventEmitter.accept(Map.of("type", "GIT_ANALYZING",
                    "teamId", participation.team().id(), "teamName", teamName));
        }

        Map<Long, AuthorContributionDTO> contributions = analysisService.analyzeRepository(repo);

        final TeamRepositoryDTO finalRepo = repo;
        ClientResponseDTO gitDto = transactionTemplate
                .execute(status -> saveGitAnalysisResult(finalRepo, contributions, exerciseId));

        if (gitDto == null) {
            return;
        }

        int current = gitAnalyzedCount.incrementAndGet();
        analysisStateService.updateProgress(exerciseId, teamName, "GIT_DONE", current);

        synchronized (eventEmitter) {
            eventEmitter.accept(Map.of("type", "GIT_UPDATE", "data", gitDto));
        }

        log.debug("Git analysis {}/{}: {}", current, total, teamName);
    }

    /** Handles errors from download/git analysis. */
    private void handleDownloadError(Exception e, ParticipationDTO participation, Long exerciseId,
                                      java.util.concurrent.atomic.AtomicInteger gitAnalyzedCount,
                                      java.util.function.Consumer<Object> eventEmitter) {
        boolean isInterrupt = Thread.currentThread().isInterrupted() ||
                e.getCause() instanceof InterruptedException ||
                (e.getCause() != null && e.getCause().getCause() instanceof java.nio.channels.ClosedByInterruptException);

        if (isInterrupt) {
            log.info("Download interrupted for team {} (analysis cancelled)",
                    participation.team() != null ? participation.team().name() : participation.id());
        } else {
            log.error("Failed to download/analyze repo for team {}",
                    participation.team() != null ? participation.team().name() : participation.id(), e);
            markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
        }
        gitAnalyzedCount.incrementAndGet();
    }

    /** Processes AI analysis for a single team during streaming. */
    private LlmTokenTotalsDTO processAIAnalysis(ParticipationDTO participation, TeamRepositoryDTO repo,
                                              Long exerciseId,
                                              java.util.concurrent.atomic.AtomicInteger aiAnalyzedCount,
                                              int total, LlmTokenTotalsDTO runTokenTotals,
                                              java.util.function.Consumer<Object> eventEmitter) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

        try {
            analysisStateService.updateProgress(exerciseId, teamName, "AI_ANALYZING", aiAnalyzedCount.get());
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "AI_ANALYZING",
                        "teamId", participation.team().id(), "teamName", teamName));
            }

            final TeamRepositoryDTO finalRepo = repo;
            ClientResponseWithUsage aiResult = transactionTemplate
                    .execute(status -> saveAIAnalysisResultWithUsage(finalRepo, exerciseId));
            ClientResponseDTO aiDto = aiResult != null ? aiResult.response() : null;
            if (aiResult != null) {
                runTokenTotals = runTokenTotals.merge(aiResult.tokenTotals());
            }

            int current = aiAnalyzedCount.incrementAndGet();
            analysisStateService.updateProgress(exerciseId, teamName, "DONE", current);

            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "AI_UPDATE", "data", aiDto));
            }

            log.debug("AI analysis {}/{}: {} (CQI={})", current, total,
                    teamName, aiDto != null ? aiDto.cqi() : "N/A");

        } catch (Exception e) {
            log.error("Error in AI analysis for team {}", teamName, e);
            aiAnalyzedCount.incrementAndGet();
            analysisStateService.updateProgress(exerciseId, teamName, "AI_ERROR", aiAnalyzedCount.get());
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "AI_ERROR",
                        "teamId", participation.team().id(), "teamName", teamName,
                        "error", e.getMessage()));
            }
        }
        return runTokenTotals;
    }

    /** Emits INIT events for all pending teams. */
    private void emitPendingTeams(List<ParticipationDTO> validParticipations,
                                   java.util.function.Consumer<Object> eventEmitter) {
        for (ParticipationDTO participation : validParticipations) {
            if (participation.team() == null) {
                continue;
            }
            TeamDTO team = participation.team();

            Map<String, Object> pendingTeam = new HashMap<>();
            pendingTeam.put("teamId", team.id());
            pendingTeam.put("teamName", team.name());
            pendingTeam.put("shortName", team.shortName());
            pendingTeam.put("repositoryUri", participation.repositoryUri());
            pendingTeam.put("submissionCount", participation.submissionCount());
            pendingTeam.put("tutor", team.owner() != null ? team.owner().name() : "Unassigned");

            List<Map<String, Object>> students = new ArrayList<>();
            if (team.students() != null) {
                for (ParticipantDTO student : team.students()) {
                    students.add(Map.of("name", student.name(), "login", student.login()));
                }
            }
            pendingTeam.put("students", students);
            pendingTeam.put("cqi", null);
            pendingTeam.put("isSuspicious", null);
            pendingTeam.put("analysisStatus", "PENDING");

            eventEmitter.accept(Map.of("type", "INIT", "data", pendingTeam));
        }
    }

    /** Detects template author via cross-repo root commit comparison. */
    private void detectTemplateAuthor(Long exerciseId, Map<Long, TeamRepositoryDTO> clonedRepos,
                                       java.util.function.Consumer<Object> eventEmitter) {
        try {
            ExerciseTemplateAuthor existing = templateAuthorRepository
                    .findByExerciseId(exerciseId).orElse(null);

            if (existing != null) {
                synchronized (eventEmitter) {
                    eventEmitter.accept(Map.of(
                            "type", "TEMPLATE_AUTHOR",
                            "email", existing.getTemplateEmail(),
                            "autoDetected", existing.getAutoDetected()));
                }
                return;
            }

            // Cross-repo detection: find root commit emails across all repos
            Map<String, Integer> rootAuthorCounts = new HashMap<>();
            for (TeamRepositoryDTO repo : clonedRepos.values()) {
                if (repo.localPath() == null) {
                    continue;
                }
                Set<String> rootEmails = gitContributionAnalysisService.findRootCommitEmails(repo.localPath());
                rootEmails.forEach(email -> rootAuthorCounts.merge(email, 1, Integer::sum));
            }

            int totalRepos = (int) clonedRepos.values().stream()
                    .filter(r -> r.localPath() != null).count();

            // Email present as root author in ALL repos → auto-detect
            java.util.Optional<String> unanimousAuthor = rootAuthorCounts.entrySet().stream()
                    .filter(e -> e.getValue() == totalRepos)
                    .map(Map.Entry::getKey)
                    .findFirst();

            if (unanimousAuthor.isPresent() && totalRepos > 0) {
                String email = unanimousAuthor.get();
                templateAuthorRepository.save(new ExerciseTemplateAuthor(exerciseId, email, true));
                log.info("Auto-detected template author for exerciseId={}: {} (unanimous across {} repos)",
                        exerciseId, email, totalRepos);
                synchronized (eventEmitter) {
                    eventEmitter.accept(Map.of("type", "TEMPLATE_AUTHOR",
                            "email", email, "autoDetected", true));
                }
            } else if (!rootAuthorCounts.isEmpty()) {
                log.info("Ambiguous template author for exerciseId={}: candidates={}",
                        exerciseId, rootAuthorCounts.keySet());
                synchronized (eventEmitter) {
                    eventEmitter.accept(Map.of("type", "TEMPLATE_AUTHOR_AMBIGUOUS",
                            "candidates", new ArrayList<>(rootAuthorCounts.keySet())));
                }
            }
        } catch (Exception e) {
            log.warn("Template author detection failed for exerciseId={}: {}", exerciseId, e.getMessage());
        }
    }

    // =====================================================================
    //  Internal helpers
    // =====================================================================

    private void clearDatabaseForExerciseInternal(Long exerciseId) {
        var participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return;
        }

        List<UUID> tutorIds = participations.stream()
                .map(TeamParticipation::getTutor)
                .filter(t -> t != null)
                .map(Tutor::getTutorId)
                .distinct()
                .toList();

        for (TeamParticipation participation : participations) {
            teamRepositoryRepository.deleteAllByTeamParticipation(participation);
            analyzedChunkRepository.deleteAllByParticipation(participation);
            studentRepository.deleteAllByTeam(participation);
        }

        teamParticipationRepository.deleteAllByExerciseId(exerciseId);

        if (!tutorIds.isEmpty()) {
            tutorRepository.deleteOrphanedByIds(tutorIds);
        }

        log.info("Cleared {} participations for exerciseId={}", participations.size(), exerciseId);
    }

    /** Calculates git-only CQI components (no AI). Returns null on failure. */
    private CQIResultDTO calculateGitOnlyCqi(TeamRepositoryDTO repo, TeamParticipation teamParticipation,
                                              TeamDTO team, List<Student> students) {
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);

            var gitComponents = cqiCalculatorService.calculateGitOnlyComponents(
                    filterResult.chunksToAnalyze(), students.size(), null, null, team.name());

            if (gitComponents != null) {
                teamParticipation.setCqiLocBalance(gitComponents.locBalance());
                teamParticipation.setCqiTemporalSpread(gitComponents.temporalSpread());
                teamParticipation.setCqiOwnershipSpread(gitComponents.ownershipSpread());
                teamParticipation.setCqiPairProgramming(gitComponents.pairProgramming());
                teamParticipation.setCqiPairProgrammingStatus(gitComponents.pairProgrammingStatus());
                teamParticipationRepository.save(teamParticipation);

                return CQIResultDTO.gitOnly(cqiCalculatorService.buildWeightsDTO(), gitComponents, filterResult.summary());
            }
        } catch (Exception e) {
            log.warn("Failed to calculate git-only metrics for team {}: {}", team.name(), e.getMessage());
        }
        return null;
    }

    /** Attempts fallback CQI calculation. Returns null on failure. */
    private Double calculateFallbackCqi(TeamRepositoryDTO repo, TeamDTO team, List<Student> students) {
        if (repo.localPath() == null) {
            return null;
        }
        try {
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);
            CQIResultDTO result = cqiCalculatorService.calculateFallback(
                    filterResult.chunksToAnalyze(), students.size(), filterResult.summary());
            return result.cqi();
        } catch (Exception e) {
            log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
            return null;
        }
    }

    /** Persists CQI component scores to a participation entity. */
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

    private boolean recomputePairProgrammingForParticipation(TeamParticipation participation) {
        List<Student> students = studentRepository.findAllByTeam(participation);
        if (students.size() != 2) {
            return clearPairProgrammingFields(participation);
        }

        var teamRepositoryOptional = teamRepositoryRepository.findByTeamParticipation(participation);
        if (teamRepositoryOptional.isEmpty()) {
            return false;
        }

        TeamRepository teamRepository = teamRepositoryOptional.get();
        String localPath = teamRepository.getLocalPath();
        if (localPath == null || localPath.isBlank() || !Files.exists(Path.of(localPath, ".git"))) {
            return false;
        }

        Map<String, Long> commitToAuthor = buildCommitToAuthorMap(teamRepository, students);
        if (commitToAuthor.isEmpty()) {
            return false;
        }

        List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(localPath, commitToAuthor);
        CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);
        ComponentScoresDTO components = cqiCalculatorService.calculateGitOnlyComponents(
                filterResult.chunksToAnalyze(), students.size(), null, null, participation.getName());

        Double previousScore = participation.getCqiPairProgramming();
        String previousStatus = participation.getCqiPairProgrammingStatus();
        Double nextScore = components.pairProgramming();
        String nextStatus = components.pairProgrammingStatus();

        boolean changed = !java.util.Objects.equals(previousScore, nextScore)
                || !java.util.Objects.equals(previousStatus, nextStatus);
        if (!changed) {
            return false;
        }

        participation.setCqiPairProgramming(nextScore);
        participation.setCqiPairProgrammingStatus(nextStatus);
        teamParticipationRepository.save(participation);
        return true;
    }

    private Map<String, Long> buildCommitToAuthorMap(TeamRepository teamRepository, List<Student> students) {
        String localPath = teamRepository.getLocalPath();

        List<VCSLogDTO> vcsLogDTOs = new ArrayList<>();
        if (teamRepository.getVcsLogs() != null) {
            for (VCSLog vcsLog : teamRepository.getVcsLogs()) {
                if (vcsLog.getCommitHash() != null && vcsLog.getEmail() != null) {
                    vcsLogDTOs.add(new VCSLogDTO(vcsLog.getEmail(), null, vcsLog.getCommitHash()));
                }
            }
        }

        long syntheticId = -1L;
        List<ParticipantDTO> participantDTOs = new ArrayList<>();
        for (Student student : students) {
            if (student.getEmail() == null || student.getEmail().isBlank()) {
                continue;
            }
            Long authorId = student.getId();
            if (authorId == null) {
                authorId = syntheticId--;
            }
            participantDTOs.add(new ParticipantDTO(authorId, student.getLogin(), student.getName(), student.getEmail()));
        }

        var result = gitContributionAnalysisService.buildFullCommitMap(localPath, vcsLogDTOs, participantDTOs);
        return new HashMap<>(result.commitToAuthor());
    }

    private boolean clearPairProgrammingFields(TeamParticipation participation) {
        if (participation.getCqiPairProgramming() == null && participation.getCqiPairProgrammingStatus() == null) {
            return false;
        }
        participation.setCqiPairProgramming(null);
        participation.setCqiPairProgrammingStatus(null);
        teamParticipationRepository.save(participation);
        return true;
    }

    private ClientResponseDTO mapParticipationToClientResponse(TeamParticipation participation) {
        List<Student> students = studentRepository.findAllByTeam(participation);
        Tutor tutor = participation.getTutor();

        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Double cqi = participation.getCqi();
        Boolean isSuspicious = participation.getIsSuspicious() != null ? participation.getIsSuspicious() : false;

        // Fallback: recalculate CQI for legacy data
        if (cqi == null) {
            Map<String, Integer> commitCounts = new HashMap<>();
            students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
            if (!commitCounts.isEmpty()) {
                cqi = balanceCalculator.calculate(commitCounts);
            }
        }

        CQIResultDTO cqiDetails = reconstructCqiDetails(participation);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.getTeam(), participation.getName(),
                participation.getSubmissionCount(),
                studentDtos, cqi, isSuspicious,
                participation.getAnalysisStatus(),
                cqiDetails,
                loadAnalyzedChunks(participation),
                null, // Orphan commits not persisted
                readTeamTokenTotals(participation),
                participation.getOrphanCommitCount());
    }

    private void initializePendingTeams(List<ParticipationDTO> participations, Long exerciseId, boolean isResume) {
        for (ParticipationDTO participation : participations) {
            if (participation.team() == null) {
                continue;
            }

            var existing = teamParticipationRepository.findByParticipation(participation.id());

            if (existing.isPresent()) {
                TeamParticipation tp = existing.get();

                // Preserve already-analyzed teams with valid CQI
                if (tp.getCqi() != null && tp.getCqi() > 0 && tp.getAnalysisStatus() == AnalysisStatus.DONE) {
                    continue;
                }

                if (!isResume) {
                    tp.setAnalysisStatus(AnalysisStatus.PENDING);
                    tp.setTutor(ensureTutor(participation.team()));
                    teamParticipationRepository.save(tp);
                } else if (tp.getAnalysisStatus() == null) {
                    tp.setAnalysisStatus(AnalysisStatus.PENDING);
                    teamParticipationRepository.save(tp);
                }
            } else {
                Tutor tutor = ensureTutor(participation.team());
                TeamParticipation tp = new TeamParticipation(
                        participation.id(), participation.team().id(), tutor,
                        participation.team().name(), participation.team().shortName(),
                        participation.repositoryUri(), participation.submissionCount());
                tp.setExerciseId(exerciseId);
                tp.setAnalysisStatus(AnalysisStatus.PENDING);
                teamParticipationRepository.save(tp);

                // Save students with basic info so names are visible for pending teams
                if (participation.team().students() != null) {
                    for (ParticipantDTO student : participation.team().students()) {
                        studentRepository.save(new Student(student.id(), student.login(),
                                student.name(), student.email(), tp, 0, 0, 0, 0));
                    }
                }
            }
        }
    }

    private void markTeamAsFailed(ParticipationDTO participation, Long exerciseId) {
        try {
            TeamDTO team = participation.team();
            Tutor tutor = ensureTutor(team);

            TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id())
                    .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                            team.shortName(), participation.repositoryUri(), participation.submissionCount()));

            tp.setExerciseId(exerciseId);
            tp.setAnalysisStatus(AnalysisStatus.ERROR);
            teamParticipationRepository.save(tp);
        } catch (Exception e) {
            log.error("Failed to mark team {} as failed", participation.team().name(), e);
        }
    }

    private void markPendingTeamsAsCancelled(Long exerciseId) {
        try {
            List<TeamParticipation> pendingTeams = teamParticipationRepository
                    .findAllByExerciseIdAndAnalysisStatus(exerciseId, AnalysisStatus.PENDING);

            for (TeamParticipation team : pendingTeams) {
                team.setAnalysisStatus(AnalysisStatus.CANCELLED);
                teamParticipationRepository.save(team);
            }

            if (!pendingTeams.isEmpty()) {
                log.info("Marked {} pending teams as CANCELLED for exerciseId={}", pendingTeams.size(), exerciseId);
            }
        } catch (Exception e) {
            log.error("Failed to mark pending teams as cancelled for exerciseId={}", exerciseId, e);
        }
    }

    private Tutor ensureTutor(TeamDTO team) {
        if (team.owner() != null) {
            ParticipantDTO tut = team.owner();
            return tutorRepository.save(new Tutor(tut.id(), tut.login(), tut.name(), tut.email()));
        }
        return null;
    }

    // =====================================================================
    //  Persistence helpers
    // =====================================================================

    private void saveAnalyzedChunks(TeamParticipation participation, List<AnalyzedChunkDTO> chunks) {
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

    @Transactional
    void applyExistingEmailMappings(TeamParticipation participation, Long exerciseId) {
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

    private List<AnalyzedChunkDTO> loadAnalyzedChunks(TeamParticipation participation) {
        try {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            if (chunks.isEmpty()) {
                return null;
            }
            return chunks.stream()
                    .map(chunk -> new AnalyzedChunkDTO(
                            chunk.getChunkIdentifier(), chunk.getAuthorEmail(), chunk.getAuthorName(),
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
                            chunk.getErrorMessage(),
                            Boolean.TRUE.equals(chunk.getIsExternalContributor()),
                            new LlmTokenUsageDTO(
                                    chunk.getLlmModel() != null ? chunk.getLlmModel() : "unknown",
                                    chunk.getLlmPromptTokens() != null ? chunk.getLlmPromptTokens() : 0L,
                                    chunk.getLlmCompletionTokens() != null ? chunk.getLlmCompletionTokens() : 0L,
                                    chunk.getLlmTotalTokens() != null
                                            ? chunk.getLlmTotalTokens()
                                            : (chunk.getLlmPromptTokens() != null ? chunk.getLlmPromptTokens() : 0L)
                                            + (chunk.getLlmCompletionTokens() != null ? chunk.getLlmCompletionTokens() : 0L),
                                    Boolean.TRUE.equals(chunk.getLlmUsageAvailable()))))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load analyzed chunks for team {}: {}", participation.getName(), e.getMessage());
            return null;
        }
    }

    private CQIResultDTO reconstructCqiDetails(TeamParticipation participation) {
        if (participation.getCqiEffortBalance() == null && participation.getCqiLocBalance() == null
                && participation.getCqiTemporalSpread() == null && participation.getCqiOwnershipSpread() == null) {
            return null;
        }

        ComponentScoresDTO components = new ComponentScoresDTO(
                participation.getCqiEffortBalance() != null ? participation.getCqiEffortBalance() : 0.0,
                participation.getCqiLocBalance() != null ? participation.getCqiLocBalance() : 0.0,
                participation.getCqiTemporalSpread() != null ? participation.getCqiTemporalSpread() : 0.0,
                participation.getCqiOwnershipSpread() != null ? participation.getCqiOwnershipSpread() : 0.0,
                participation.getCqiPairProgramming(),
                participation.getCqiPairProgrammingStatus());

        return new CQIResultDTO(
                participation.getCqi() != null ? participation.getCqi() : 0.0,
                components,
                cqiCalculatorService.buildWeightsDTO(),
                participation.getCqiBaseScore() != null ? participation.getCqiBaseScore() : 0.0,
                null);
    }

    // =====================================================================
    //  LLM token tracking
    // =====================================================================

    private void logTotalUsage(String scope, Long exerciseId, int analyzedTeams, LlmTokenTotalsDTO tokenTotals) {
        log.info("LLM_USAGE scope={} exerciseId={} teams={} llmCalls={} promptTokens={} completionTokens={} totalTokens={}",
                scope, exerciseId, analyzedTeams,
                tokenTotals.llmCalls(), tokenTotals.promptTokens(),
                tokenTotals.completionTokens(), tokenTotals.totalTokens());
    }

    private void persistTeamTokenTotals(TeamParticipation tp, LlmTokenTotalsDTO totals) {
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

    private LlmTokenTotalsDTO readTeamTokenTotals(TeamParticipation tp) {
        if (tp.getLlmCalls() == null && tp.getLlmCallsWithUsage() == null
                && tp.getLlmPromptTokens() == null && tp.getLlmCompletionTokens() == null
                && tp.getLlmTotalTokens() == null) {
            return null;
        }
        long prompt = tp.getLlmPromptTokens() != null ? tp.getLlmPromptTokens() : 0L;
        long completion = tp.getLlmCompletionTokens() != null ? tp.getLlmCompletionTokens() : 0L;
        long total = tp.getLlmTotalTokens() != null ? tp.getLlmTotalTokens() : prompt + completion;
        return new LlmTokenTotalsDTO(
                tp.getLlmCalls() != null ? tp.getLlmCalls() : 0L,
                tp.getLlmCallsWithUsage() != null ? tp.getLlmCallsWithUsage() : 0L,
                prompt, completion, total);
    }

    private record ClientResponseWithUsage(ClientResponseDTO response, LlmTokenTotalsDTO tokenTotals) {
    }

    // =====================================================================
    //  JSON serialization helpers
    // =====================================================================

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

    private void shutdownExecutorQuietly(ExecutorService executor) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
