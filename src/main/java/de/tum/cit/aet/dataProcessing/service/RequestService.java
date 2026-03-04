package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.ContributionBalanceCalculator;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.pairProgramming.enums.PairProgrammingStatus;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.*;
import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
import de.tum.cit.aet.analysis.dto.cqi.*;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingRecomputeService;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrator service for the main analysis pipeline.
 * Acts as a facade that coordinates the three analysis phases:
 *
 * <ol>
 *   <li><b>Repository download</b> — clones team repos from Artemis via
 *       {@link GitOperationsService}</li>
 *   <li><b>Git analysis</b> — extracts commit metrics (lines, authors) via
 *       {@link GitContributionAnalysisService}</li>
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
    private final ContributionBalanceCalculator balanceCalculator;
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
    private final PairProgrammingService pairProgrammingService;

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
            ContributionBalanceCalculator balanceCalculator,
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
            CqiRecalculationService cqiRecalculationService,
            PairProgrammingService pairProgrammingService) {
        this.artemisClientService = artemisClientService;
        this.gitOperationsService = gitOperationsService;
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
        this.pairProgrammingService = pairProgrammingService;
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
        Map<Long, AuthorContributionDTO> contributionData = gitContributionAnalysisService.processAllRepositories(repositories);

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
     * Runs the analysis pipeline with streaming progress updates via SSE.
     *
     * <p>Pipeline phases:
     * <ol>
     *   <li><b>Phase 1+2</b> — Download repos and run git analysis in parallel</li>
     *   <li><b>Template author detection</b> — Cross-repo root commit comparison</li>
     *   <li><b>Phase 3 (FULL only)</b> — AI analysis (CQI calculation) sequentially per team</li>
     *   <li><b>Phase 3 (SIMPLE only)</b> — Git-only CQI with renormalized weights</li>
     * </ol>
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   exercise ID
     * @param mode         analysis depth: SIMPLE (git-only CQI) or FULL (git + AI)
     * @param eventEmitter consumer for streaming SSE events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                       AnalysisMode mode,
                                                       Consumer<Object> eventEmitter) {
        runningStreamTasks.put(exerciseId, Thread.currentThread());

        ExecutorService executor = null;
        try {
            // 1) Clear existing data for a clean slate
            transactionTemplate.executeWithoutResult(status ->
                    clearDatabaseForExerciseInternal(exerciseId));

            // 2) Fetch team participations from Artemis and filter to those with repositories
            List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                    credentials.serverUrl(), credentials.jwtToken(), exerciseId);

            List<ParticipationDTO> validParticipations = participations.stream()
                    .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                    .toList();

            int totalToProcess = validParticipations.size();
            analysisStateService.startAnalysis(exerciseId, totalToProcess, mode);
            eventEmitter.accept(Map.of("type", "START", "total", totalToProcess, "analysisMode", mode.name()));

            // 3) Persist PENDING state for all teams and emit initial team data to the client
            initializePendingTeams(validParticipations, exerciseId, false);
            emitPendingTeams(validParticipations, eventEmitter);

            if (validParticipations.isEmpty()) {
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
                return;
            }

            // 4) Phase 1 — Download: Clone all repositories in parallel
            log.info("Phase 1: Downloading {} repositories (mode={})", totalToProcess, mode);
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "DOWNLOADING", "total", totalToProcess));

            Map<Long, TeamRepositoryDTO> clonedRepos = new ConcurrentHashMap<>();
            int threadCount = Math.max(1, Math.min(totalToProcess, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);
            activeExecutors.put(exerciseId, executor);

            CountDownLatch downloadLatch = new CountDownLatch(totalToProcess);
            AtomicInteger downloadedCount = new AtomicInteger(0);

            for (ParticipationDTO participation : validParticipations) {
                executor.submit(() -> {
                    try {
                        if (!analysisStateService.isRunning(exerciseId)) {
                            return;
                        }
                        processDownload(participation, credentials, exerciseId,
                                clonedRepos, downloadedCount, totalToProcess, eventEmitter);
                    } catch (Exception e) {
                        handleDownloadError(e, participation, exerciseId, downloadedCount, eventEmitter);
                    } finally {
                        downloadLatch.countDown();
                    }
                });
            }

            try {
                downloadLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Download phase interrupted for exerciseId={}", exerciseId);
            }

            log.info("Phase 1 complete: {} of {} repositories cloned", clonedRepos.size(), totalToProcess);

            if (!analysisStateService.isRunning(exerciseId)) {
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", downloadedCount.get(), "total", totalToProcess));
                return;
            }

            // 5) Phase 2 — Git Analysis: Analyze commits, contributions, and file ownership in parallel
            int clonedCount = clonedRepos.size();
            log.info("Phase 2: Git-analyzing {} repositories", clonedCount);
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "GIT_ANALYSIS", "total", clonedCount));

            CountDownLatch analysisLatch = new CountDownLatch(clonedCount);
            AtomicInteger gitAnalyzedCount = new AtomicInteger(0);

            for (ParticipationDTO participation : validParticipations) {
                TeamRepositoryDTO repo = clonedRepos.get(participation.id());
                if (repo == null) {
                    continue; // skip teams that failed to clone
                }
                executor.submit(() -> {
                    try {
                        if (!analysisStateService.isRunning(exerciseId)) {
                            return;
                        }
                        processGitAnalysis(participation, repo, exerciseId,
                                gitAnalyzedCount, clonedCount, eventEmitter, mode);
                    } catch (Exception e) {
                        handleGitAnalysisError(e, participation, exerciseId, gitAnalyzedCount, eventEmitter);
                    } finally {
                        analysisLatch.countDown();
                    }
                });
            }

            try {
                analysisLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Git analysis phase interrupted for exerciseId={}", exerciseId);
            }

            executor.shutdown();
            log.info("Phase 2 complete: {} of {} repositories git-analyzed", gitAnalyzedCount.get(), clonedCount);
            eventEmitter.accept(Map.of("type", "GIT_DONE", "processed", gitAnalyzedCount.get()));

            if (!analysisStateService.isRunning(exerciseId)) {
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", gitAnalyzedCount.get(), "total", totalToProcess));
                return;
            }

            // 6) Detect template author by comparing root commits across repositories
            detectTemplateAuthor(exerciseId, clonedRepos, eventEmitter);

            // 7) Phase 3 — CQI Computation: SIMPLE mode computes git-only CQI, FULL mode runs AI analysis
            if (mode == AnalysisMode.SIMPLE) {
                finalizeSimpleMode(validParticipations, clonedRepos, exerciseId, eventEmitter);
            } else {
                eventEmitter.accept(Map.of("type", "PHASE", "phase", "AI_ANALYSIS", "total", clonedRepos.size()));

                AtomicInteger aiAnalyzedCount = new AtomicInteger(0);
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

                    // Skip failed teams — already marked DONE with CQI=0 during git analysis
                    TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id()).orElse(null);
                    if (tp != null && shouldSkipAnalysis(tp)) {
                        aiAnalyzedCount.incrementAndGet();
                        continue;
                    }

                    runTokenTotals = processAIAnalysis(participation, repo, exerciseId,
                            aiAnalyzedCount, clonedRepos.size(), runTokenTotals, eventEmitter);
                }

                log.info("Phase 3 complete: AI analysis done for {} teams", aiAnalyzedCount.get());
                logTotalUsage("stream", exerciseId, aiAnalyzedCount.get(), runTokenTotals);

                // 8) Finalize analysis — mark complete or cancelled based on current state
                if (analysisStateService.isRunning(exerciseId)) {
                    analysisStateService.completeAnalysis(exerciseId);
                    eventEmitter.accept(Map.of("type", "DONE"));
                } else {
                    markPendingTeamsAsCancelled(exerciseId);
                    eventEmitter.accept(Map.of("type", "CANCELLED",
                            "processed", aiAnalyzedCount.get(), "total", totalToProcess));
                }
            }

        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Executor was shut down due to cancellation — treat as cancelled, not error
            log.info("Analysis cancelled (executor shut down) for exerciseId={}", exerciseId);
            markPendingTeamsAsCancelled(exerciseId);
            analysisStateService.cancelAnalysis(exerciseId);
            eventEmitter.accept(Map.of("type", "CANCELLED"));
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

    /**
     * Checks if an analysis task is currently running.
     *
     * @param exerciseId the exercise ID to check
     * @return true if a task is running for the given exercise
     */
    public boolean isTaskRunning(Long exerciseId) {
        Future<?> future = runningFutures.get(exerciseId);
        return future != null && !future.isDone();
    }

    /**
     * Registers a running task for tracking and cancellation.
     *
     * @param exerciseId the exercise ID
     * @param future     the future representing the running task
     */
    public void registerRunningTask(Long exerciseId, Future<?> future) {
        runningFutures.put(exerciseId, future);
    }

    /**
     * Unregisters a completed task.
     *
     * @param exerciseId the exercise ID to unregister
     */
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

    /**
     * Returns lean team summaries for an exercise (excludes analysisHistory, orphanCommits, etc.).
     *
     * @param exerciseId the exercise ID
     * @return list of team summaries
     */
    public List<TeamSummaryDTO> getTeamSummariesByExerciseId(Long exerciseId) {
        return getTeamsByExerciseId(exerciseId).stream()
                .map(TeamSummaryDTO::fromClientResponse)
                .toList();
    }

    /**
     * Returns full detail for a single team.
     *
     * @param exerciseId the exercise ID
     * @param teamId     the Artemis team ID
     * @return full team detail, or empty if not found
     */
    public Optional<ClientResponseDTO> getTeamDetail(Long exerciseId, Long teamId) {
        return teamParticipationRepository.findByExerciseIdAndTeam(exerciseId, teamId)
                .map(this::mapParticipationToClientResponse);
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
        return saveGitAnalysisResult(repo, contributionData, exerciseId, null);
    }

    /**
     * Saves git analysis results for a single team, with mode-aware weight renormalization.
     *
     * @param repo             repository data
     * @param contributionData contribution metrics by student ID
     * @param exerciseId       exercise ID
     * @param mode             analysis mode (SIMPLE sends renormalized weights, FULL sends original)
     * @return client response with git metrics, or null if cancelled
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                    Map<Long, AuthorContributionDTO> contributionData,
                                                    Long exerciseId, AnalysisMode mode) {
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
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.GIT_DONE);
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

        // 4b) Check if team has failed requirements — mark DONE with CQI=0 immediately
        boolean hasFailed = checkAndMarkFailed(teamParticipation, students);
        if (hasFailed) {
            return new ClientResponseDTO(
                    tutor != null ? tutor.getName() : "Unassigned",
                    team.id(), participation.id(), team.name(), team.shortName(), participation.submissionCount(),
                    studentDtos, 0.0, false, TeamAnalysisStatus.DONE,
                    null, null, null, null, 0, true, null);
        }

        // 5) Calculate git-only CQI components (no AI needed)
        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        // In SIMPLE mode, send renormalized weights (excluding effort balance)
        CQIResultDTO finalDetails = gitCqiDetails;
        if (mode == AnalysisMode.SIMPLE && gitCqiDetails != null) {
            finalDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails);
        }

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), participation.id(), team.name(), team.shortName(), participation.submissionCount(),
                studentDtos,
                null,  // CQI — Phase 3
                null,  // isSuspicious — Phase 3
                TeamAnalysisStatus.GIT_DONE,
                finalDetails,
                null,  // analysisHistory — Phase 3
                null,  // orphanCommits
                readTeamTokenTotals(teamParticipation),
                null,  // orphanCommitCount — Phase 3
                null,  // isFailed
                null); // isReviewed
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
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
                    PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
                    cqiDetails = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(), students.size(), filterResult.summary(), team.name(), team.shortName());
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
        CQIResultDTO finalCqiDetails = reconstructCqiDetails(teamParticipation, AnalysisMode.FULL);
        if (finalCqiDetails == null) {
            finalCqiDetails = cqiDetails;
        }

        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        team.id(), participation.id(), team.name(), team.shortName(), participation.submissionCount(),
                        studentDtos, finalCqi, isSuspicious, TeamAnalysisStatus.DONE,
                        finalCqiDetails, analysisHistory, orphanCommits,
                        teamTokenTotals, teamParticipation.getOrphanCommitCount(), null, null),
                teamTokenTotals);
    }

    // =====================================================================
    //  Streaming pipeline helpers
    // =====================================================================

    /** Emits an SSE event with current analysis progress for the ActivityLog. */
    private void emitStatusUpdate(Consumer<Object> eventEmitter, String teamName,
                                   String stage, int processed, int total) {
        Map<String, Object> statusEvent = new HashMap<>();
        statusEvent.put("type", "STATUS");
        statusEvent.put("processedTeams", processed);
        statusEvent.put("totalTeams", total);
        statusEvent.put("currentTeamName", teamName);
        statusEvent.put("currentStage", stage);
        synchronized (eventEmitter) {
            eventEmitter.accept(statusEvent);
        }
    }

    /** Downloads (clones) a single team's repository (called from parallel executor). */
    private void processDownload(ParticipationDTO participation, ArtemisCredentials credentials,
                                  Long exerciseId, Map<Long, TeamRepositoryDTO> clonedRepos,
                                  AtomicInteger downloadedCount, int total,
                                  Consumer<Object> eventEmitter) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

        analysisStateService.updateProgress(exerciseId, teamName, "DOWNLOADING", downloadedCount.get());
        emitStatusUpdate(eventEmitter, teamName, "DOWNLOADING", downloadedCount.get(), total);

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
        int current = downloadedCount.incrementAndGet();
        emitStatusUpdate(eventEmitter, teamName, "DOWNLOADED", current, total);
        log.debug("Download {}/{}: {}", current, total, teamName);
    }

    /** Git-analyzes a single team's repository (called from parallel executor). */
    private void processGitAnalysis(ParticipationDTO participation, TeamRepositoryDTO repo,
                                     Long exerciseId, AtomicInteger gitAnalyzedCount,
                                     int total, Consumer<Object> eventEmitter,
                                     AnalysisMode mode) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

        analysisStateService.updateProgress(exerciseId, teamName, "GIT_ANALYZING", gitAnalyzedCount.get());
        emitStatusUpdate(eventEmitter, teamName, "GIT_ANALYZING", gitAnalyzedCount.get(), total);
        synchronized (eventEmitter) {
            eventEmitter.accept(Map.of("type", "GIT_ANALYZING",
                    "teamId", participation.team().id(), "teamName", teamName));
        }

        Map<Long, AuthorContributionDTO> contributions = gitContributionAnalysisService.analyzeRepository(repo);

        final TeamRepositoryDTO finalRepo = repo;
        final AnalysisMode finalMode = mode;
        ClientResponseDTO gitDto = transactionTemplate
                .execute(status -> saveGitAnalysisResult(finalRepo, contributions, exerciseId, finalMode));

        if (gitDto == null) {
            return;
        }

        int current = gitAnalyzedCount.incrementAndGet();

        // If the team was marked as failed (DONE with CQI=0) during git analysis,
        // emit as AI_UPDATE so the client treats it as a completed team
        if (gitDto.analysisStatus() == TeamAnalysisStatus.DONE) {
            analysisStateService.updateProgress(exerciseId, teamName, "DONE", current);
            emitStatusUpdate(eventEmitter, teamName, "DONE", current, total);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "AI_UPDATE", "data",
                        TeamSummaryDTO.fromClientResponse(gitDto)));
            }
        } else {
            analysisStateService.updateProgress(exerciseId, teamName, "GIT_DONE", current);
            emitStatusUpdate(eventEmitter, teamName, "GIT_DONE", current, total);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "GIT_UPDATE", "data", gitDto));
            }
        }

        log.debug("Git analysis {}/{}: {}", current, total, teamName);
    }

    /** Handles errors from the download (clone) phase. */
    private void handleDownloadError(Exception e, ParticipationDTO participation, Long exerciseId,
                                      AtomicInteger counter,
                                      Consumer<Object> eventEmitter) {
        boolean isInterrupt = Thread.currentThread().isInterrupted() ||
                e.getCause() instanceof InterruptedException ||
                (e.getCause() != null && e.getCause().getCause() instanceof ClosedByInterruptException);

        if (isInterrupt) {
            log.info("Download interrupted for team {} (analysis cancelled)",
                    participation.team() != null ? participation.team().name() : participation.id());
        } else {
            log.error("Failed to download repo for team {}",
                    participation.team() != null ? participation.team().name() : participation.id(), e);
            markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
        }
        counter.incrementAndGet();
    }

    /** Handles errors from the git analysis phase. */
    private void handleGitAnalysisError(Exception e, ParticipationDTO participation, Long exerciseId,
                                         AtomicInteger counter,
                                         Consumer<Object> eventEmitter) {
        boolean isInterrupt = Thread.currentThread().isInterrupted() ||
                e.getCause() instanceof InterruptedException ||
                (e.getCause() != null && e.getCause().getCause() instanceof ClosedByInterruptException);

        if (isInterrupt) {
            log.info("Git analysis interrupted for team {} (analysis cancelled)",
                    participation.team() != null ? participation.team().name() : participation.id());
        } else {
            log.error("Failed to git-analyze repo for team {}",
                    participation.team() != null ? participation.team().name() : participation.id(), e);
            markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
        }
        counter.incrementAndGet();
    }

    /** Processes AI analysis for a single team during streaming. */
    private LlmTokenTotalsDTO processAIAnalysis(ParticipationDTO participation, TeamRepositoryDTO repo,
                                              Long exerciseId,
                                              AtomicInteger aiAnalyzedCount,
                                              int total, LlmTokenTotalsDTO runTokenTotals,
                                              Consumer<Object> eventEmitter) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

        try {
            analysisStateService.updateProgress(exerciseId, teamName, "AI_ANALYZING", aiAnalyzedCount.get());
            emitStatusUpdate(eventEmitter, teamName, "AI_ANALYZING", aiAnalyzedCount.get(), total);
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
            emitStatusUpdate(eventEmitter, teamName, "DONE", current, total);

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

    /**
     * Finalizes SIMPLE mode: computes git-only CQI for each team and marks them DONE.
     * Skips AI analysis entirely. CQI is computed from locBalance, temporalSpread, and
     * ownershipSpread with renormalized weights.
     *
     * @param validParticipations teams to process
     * @param clonedRepos         map of participation ID to cloned repo data
     * @param exerciseId          exercise ID
     * @param eventEmitter        SSE event consumer
     */
    private void finalizeSimpleMode(List<ParticipationDTO> validParticipations,
                                     Map<Long, TeamRepositoryDTO> clonedRepos,
                                     Long exerciseId,
                                     Consumer<Object> eventEmitter) {
        AtomicInteger processedCount = new AtomicInteger(0);
        int total = validParticipations.size();

        // 1) Iterate over all teams and compute git-only CQI
        for (ParticipationDTO participation : validParticipations) {
            if (!analysisStateService.isRunning(exerciseId) || Thread.currentThread().isInterrupted()) {
                break;
            }

            TeamRepositoryDTO repo = clonedRepos.get(participation.id());
            if (repo == null) {
                processedCount.incrementAndGet();
                continue;
            }

            // Skip failed teams — already marked DONE with CQI=0 during git analysis
            TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id()).orElse(null);
            if (tp != null && shouldSkipAnalysis(tp)) {
                processedCount.incrementAndGet();
                continue;
            }

            String teamName = participation.team() != null ? participation.team().name() : "Unknown";
            try {
                // 2) Calculate git-only CQI with renormalized weights and mark DONE
                ClientResponseDTO result = transactionTemplate.execute(status ->
                        calculateAndPersistSimpleCqi(participation, repo, exerciseId));

                int current = processedCount.incrementAndGet();
                analysisStateService.updateProgress(exerciseId, teamName, "DONE", current);
                emitStatusUpdate(eventEmitter, teamName, "DONE", current, total);

                // 3) Emit result as AI_UPDATE so the client treats it like a completed team
                if (result != null) {
                    synchronized (eventEmitter) {
                        eventEmitter.accept(Map.of("type", "AI_UPDATE", "data",
                                TeamSummaryDTO.fromClientResponse(result)));
                    }
                }
            } catch (Exception e) {
                log.error("Error computing simple CQI for team {}", teamName, e);
                processedCount.incrementAndGet();
            }
        }

        // 4) Finalize analysis
        if (analysisStateService.isRunning(exerciseId)) {
            analysisStateService.completeAnalysis(exerciseId);
            eventEmitter.accept(Map.of("type", "DONE"));
        } else {
            markPendingTeamsAsCancelled(exerciseId);
            eventEmitter.accept(Map.of("type", "CANCELLED",
                    "processed", processedCount.get(), "total", total));
        }
    }

    /**
     * Computes git-only CQI for SIMPLE mode: renormalize loc/temporal/ownership weights
     * (excluding effort which requires AI) and compute a weighted CQI score.
     *
     * @param participation the participation DTO
     * @param repo          the cloned repository data
     * @param exerciseId    the exercise ID
     * @return the full ClientResponseDTO with computed CQI
     */
    private ClientResponseDTO calculateAndPersistSimpleCqi(ParticipationDTO participation,
                                                            TeamRepositoryDTO repo, Long exerciseId) {
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for simple CQI"));

        List<Student> students = studentRepository.findAllByTeam(teamParticipation);

        // 1) Calculate git-only components
        CQIResultDTO gitCqiDetails = calculateGitOnlyCqi(repo, teamParticipation, team, students);

        // 2) Compute renormalized CQI from git-only components
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

            // Use renormalized weights for display
            simpleCqiDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails);
        }

        // 3) Persist components and mark DONE
        persistCqiComponents(teamParticipation, gitCqiDetails);
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(false);
        teamParticipation.setAnalysisStatus(TeamAnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        // 4) Build response
        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();
        CQIResultDTO finalDetails = simpleCqiDetails != null ? simpleCqiDetails : reconstructCqiDetails(teamParticipation, AnalysisMode.SIMPLE);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), participation.id(), team.name(), team.shortName(), participation.submissionCount(),
                studentDtos, cqi, false, TeamAnalysisStatus.DONE,
                finalDetails, null, null, null, null, null, null);
    }

    /** Emits INIT events for all pending teams. */
    private void emitPendingTeams(List<ParticipationDTO> validParticipations,
                                   Consumer<Object> eventEmitter) {
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
            pendingTeam.put("participationId", participation.id());

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
                                       Consumer<Object> eventEmitter) {
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
            Optional<String> unanimousAuthor = rootAuthorCounts.entrySet().stream()
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
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
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
                PairProgrammingStatus pairProgrammingStatus = gitComponents.pairProgrammingStatus();
                teamParticipation.setCqiPairProgrammingStatus(pairProgrammingStatus != null ? pairProgrammingStatus.name() : null);
                teamParticipation.setCqiDailyDistribution(serializeDailyDistribution(gitComponents.dailyDistribution()));
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
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildFullCommitMap(repo, null).commitToAuthor();
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);
            PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
            CQIResultDTO result = cqiCalculatorService.calculateFallback(
                    filterResult.chunksToAnalyze(), students.size(), filterResult.summary(), team.name(), team.shortName());
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
        teamParticipation.setCqiDailyDistribution(serializeDailyDistribution(cqiDetails.components().dailyDistribution()));
    }

    /**
     * Toggles the review status of a team participation.
     *
     * @param exerciseId The Artemis exercise ID
     * @param teamId     The Artemis team ID
     * @return Updated ClientResponseDTO
     */
    @Transactional
    public ClientResponseDTO toggleReviewStatus(Long exerciseId, Long teamId) {
        TeamParticipation participation = teamParticipationRepository.findByExerciseIdAndTeam(exerciseId, teamId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Team not found for exerciseId=" + exerciseId + ", teamId=" + teamId));
        participation.setIsReviewed(!Boolean.TRUE.equals(participation.getIsReviewed()));
        teamParticipationRepository.save(participation);
        return mapParticipationToClientResponse(participation);
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

        AnalysisMode mode = analysisStateService.getStatus(participation.getExerciseId()).getAnalysisMode();
        CQIResultDTO cqiDetails = reconstructCqiDetails(participation, mode);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.getTeam(), participation.getParticipation(), participation.getName(), participation.getShortName(),
                participation.getSubmissionCount(),
                studentDtos, cqi, isSuspicious,
                participation.getAnalysisStatus(),
                cqiDetails,
                loadAnalyzedChunks(participation),
                null, // Orphan commits not persisted
                readTeamTokenTotals(participation),
                participation.getOrphanCommitCount(),
                participation.getIsFailed(),
                participation.getIsReviewed());
    }

    private void initializePendingTeams(List<ParticipationDTO> participations, Long exerciseId, boolean isResume) {
        for (ParticipationDTO participation : participations) {
            if (participation.team() == null) {
                continue;
            }

            Optional<TeamParticipation> existing = teamParticipationRepository.findByParticipation(participation.id());

            if (existing.isPresent()) {
                TeamParticipation tp = existing.get();

                // Preserve already-analyzed teams with valid CQI
                if (tp.getCqi() != null && tp.getCqi() > 0 && tp.getAnalysisStatus() == TeamAnalysisStatus.DONE) {
                    continue;
                }

                if (!isResume) {
                    tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
                    tp.setTutor(ensureTutor(participation.team()));
                    teamParticipationRepository.save(tp);
                } else if (tp.getAnalysisStatus() == null) {
                    tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
                    teamParticipationRepository.save(tp);
                }
            } else {
                Tutor tutor = ensureTutor(participation.team());
                TeamParticipation tp = new TeamParticipation(
                        participation.id(), participation.team().id(), tutor,
                        participation.team().name(), participation.team().shortName(),
                        participation.repositoryUri(), participation.submissionCount());
                tp.setExerciseId(exerciseId);
                tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
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
            tp.setAnalysisStatus(TeamAnalysisStatus.ERROR);
            teamParticipationRepository.save(tp);
        } catch (Exception e) {
            log.error("Failed to mark team {} as failed", participation.team().name(), e);
        }
    }

    private void markPendingTeamsAsCancelled(Long exerciseId) {
        try {
            List<TeamParticipation> pendingTeams = teamParticipationRepository
                    .findAllByExerciseIdAndAnalysisStatus(exerciseId, TeamAnalysisStatus.PENDING);

            for (TeamParticipation team : pendingTeams) {
                team.setAnalysisStatus(TeamAnalysisStatus.CANCELLED);
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
                boolean dismissed = Boolean.TRUE.equals(mapping.getIsDismissed());
                for (AnalyzedChunk chunk : chunks) {
                    if (Boolean.TRUE.equals(chunk.getIsExternalContributor())
                            && emailLower.equals(chunk.getAuthorEmail() != null
                                    ? chunk.getAuthorEmail().toLowerCase(java.util.Locale.ROOT) : null)) {
                        chunk.setIsExternalContributor(false);
                        if (!dismissed) {
                            chunk.setAuthorName(mapping.getStudentName());
                            remappedByStudent.computeIfAbsent(mapping.getStudentName(), k -> new ArrayList<>())
                                    .add(chunk);
                        }
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

    private String serializeDailyDistribution(List<Double> dailyDistribution) {
        try {
            if (dailyDistribution == null || dailyDistribution.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(dailyDistribution);
        } catch (Exception e) {
            log.warn("Failed to serialize daily distribution: {}", e.getMessage());
            return null;
        }
    }

    private List<Double> deserializeDailyDistribution(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize daily distribution: {}", e.getMessage());
            return null;
        }
    }

    private CQIResultDTO reconstructCqiDetails(TeamParticipation participation, AnalysisMode mode) {
        if (participation.getCqiEffortBalance() == null && participation.getCqiLocBalance() == null
                && participation.getCqiTemporalSpread() == null && participation.getCqiOwnershipSpread() == null) {
            return null;
        }

        PairProgrammingStatus pairProgrammingStatus = PairProgrammingRecomputeService.parsePairProgrammingStatus(participation.getCqiPairProgrammingStatus());
        Double pairProgrammingScore = PairProgrammingRecomputeService.normalizePairProgrammingScore(
                participation.getCqiPairProgramming(),
                pairProgrammingStatus);

        ComponentScoresDTO components = new ComponentScoresDTO(
                participation.getCqiEffortBalance() != null ? participation.getCqiEffortBalance() : 0.0,
                participation.getCqiLocBalance() != null ? participation.getCqiLocBalance() : 0.0,
                participation.getCqiTemporalSpread() != null ? participation.getCqiTemporalSpread() : 0.0,
                participation.getCqiOwnershipSpread() != null ? participation.getCqiOwnershipSpread() : 0.0,
                pairProgrammingScore,
                pairProgrammingStatus,
                deserializeDailyDistribution(participation.getCqiDailyDistribution()));

        // Determine weights based on analysis mode
        ComponentWeightsDTO weights;
        if (mode == AnalysisMode.FULL) {
            weights = cqiCalculatorService.buildWeightsDTO();
        } else if (mode == AnalysisMode.SIMPLE) {
            weights = cqiCalculatorService.buildRenormalizedWeightsWithoutEffort();
        } else {
            // Null mode fallback: infer from whether effort balance was computed
            boolean hasEffortBalance = participation.getCqiEffortBalance() != null
                    && participation.getCqiEffortBalance() > 0;
            weights = hasEffortBalance
                    ? cqiCalculatorService.buildWeightsDTO()
                    : cqiCalculatorService.buildRenormalizedWeightsWithoutEffort();
        }

        return new CQIResultDTO(
                participation.getCqi() != null ? participation.getCqi() : 0.0,
                components,
                weights,
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

    // =====================================================================
    //  Per-team AI analysis
    // =====================================================================

    /**
     * Runs AI analysis for a single team on demand.
     * Requires git analysis to have been completed first (GIT_DONE or DONE status).
     *
     * @param exerciseId the exercise ID
     * @param teamId     the Artemis team ID
     * @return the updated ClientResponseDTO, or empty if team not found
     */
    public Optional<ClientResponseDTO> runSingleTeamAIAnalysis(Long exerciseId, Long teamId) {
        // Transaction 1: validate, set AI_ANALYZING status, and build DTO
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

            // Mark as AI_ANALYZING so a page refresh shows the right state
            tp.setAnalysisStatus(TeamAnalysisStatus.AI_ANALYZING);
            teamParticipationRepository.save(tp);

            // Clear previous analyzed chunks so AI re-runs cleanly
            analyzedChunkRepository.deleteAllByParticipation(tp);

            return new PreparedAnalysis(buildTeamRepositoryDTO(tp, repo));
        });

        if (prepared == null) {
            return Optional.empty();
        }

        // Transaction 2: run AI analysis (long-running, committed separately)
        ClientResponseWithUsage result = saveAIAnalysisResultWithUsage(prepared.repoDto(), exerciseId);
        return Optional.ofNullable(result.response());
    }

    /**
     * Reconstructs a TeamRepositoryDTO from persisted domain entities.
     */
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

    // =====================================================================
    //  Skip failed teams
    // =====================================================================

    /**
     * Checks whether a team has failed requirements after git analysis and persists the result.
     * A team is failed if any student has fewer than 10 commits or if pair programming
     * attendance requirements are not met. Failed teams are marked DONE with CQI=0.
     *
     * @return true if the team was marked as failed
     */
    private boolean checkAndMarkFailed(TeamParticipation tp, List<Student> students) {
        boolean hasFailed = false;

        // 1) Check if any student has < 10 commits (= "Failed" badge)
        boolean hasFailedStudent = students.stream()
                .anyMatch(s -> s.getCommitCount() != null && s.getCommitCount() < 10);
        if (hasFailedStudent) {
            hasFailed = true;
        }

        // 2) Check pair programming attendance (= "PP Failed" badge)
        if (!hasFailed) {
            if (pairProgrammingService.hasAttendanceData()
                    && pairProgrammingService.hasTeamAttendance(tp.getName(), tp.getShortName())
                    && !pairProgrammingService.isPairedMandatorySessions(tp.getName(), tp.getShortName())) {
                hasFailed = true;
            }
        }

        if (hasFailed) {
            tp.setIsFailed(true);
            tp.setCqi(0.0);
            tp.setIsSuspicious(false);
            tp.setAnalysisStatus(TeamAnalysisStatus.DONE);
            // Clear any stale CQI component fields from previous analyses
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
     * Determines whether a team should skip CQI/AI analysis.
     * Uses the persisted isFailed flag set during git analysis.
     */
    private boolean shouldSkipAnalysis(TeamParticipation tp) {
        return Boolean.TRUE.equals(tp.getIsFailed());
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
