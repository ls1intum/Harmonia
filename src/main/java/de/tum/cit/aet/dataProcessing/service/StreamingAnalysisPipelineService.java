package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.AnalysisResultPersistenceService;
import de.tum.cit.aet.analysis.service.AnalysisTaskManager;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.channels.ClosedByInterruptException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Orchestrates the streaming and synchronous analysis pipelines.
 *
 * <p>The pipeline consists of three phases:</p>
 * <ol>
 *   <li><b>Download</b> — clone team repositories from Artemis</li>
 *   <li><b>Git analysis</b> — extract commit metrics (lines, authors)</li>
 *   <li><b>AI analysis</b> — compute CQI scores (or simple CQI in SIMPLE mode)</li>
 * </ol>
 */
@Service
@Slf4j
public class StreamingAnalysisPipelineService {

    private final ArtemisClientService artemisClientService;
    private final GitOperationsService gitOperationsService;
    private final AnalysisStateService analysisStateService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final TransactionTemplate transactionTemplate;
    private final TeamParticipationRepository teamParticipationRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;

    private final AnalysisTaskManager analysisTaskManager;
    private final ExerciseTeamLifecycleService exerciseDataCleanupService;
    private final AnalysisResultPersistenceService persistenceService;

    public StreamingAnalysisPipelineService(
            ArtemisClientService artemisClientService,
            GitOperationsService gitOperationsService,
            AnalysisStateService analysisStateService,
            GitContributionAnalysisService gitContributionAnalysisService,
            TransactionTemplate transactionTemplate,
            TeamParticipationRepository teamParticipationRepository,
            ExerciseTemplateAuthorRepository templateAuthorRepository,
            AnalysisTaskManager analysisTaskManager,
            ExerciseTeamLifecycleService exerciseDataCleanupService,
            AnalysisResultPersistenceService persistenceService) {
        this.artemisClientService = artemisClientService;
        this.gitOperationsService = gitOperationsService;
        this.analysisStateService = analysisStateService;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.transactionTemplate = transactionTemplate;
        this.teamParticipationRepository = teamParticipationRepository;
        this.templateAuthorRepository = templateAuthorRepository;
        this.analysisTaskManager = analysisTaskManager;
        this.exerciseDataCleanupService = exerciseDataCleanupService;
        this.persistenceService = persistenceService;
    }

    // =====================================================================
    //  Synchronous analysis pipeline
    // =====================================================================

    /**
     * Fetches, analyzes, and saves repositories synchronously with a team limit.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  the exercise id
     * @param maxTeams    maximum number of teams to process
     * @return list of client response DTOs
     */
    public List<ClientResponseDTO> fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId,
                                                                    int maxTeams) {
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);
        if (repositories.size() > maxTeams) {
            repositories = repositories.subList(0, maxTeams);
        }

        Map<Long, AuthorContributionDTO> contributionData = gitContributionAnalysisService.processAllRepositories(repositories);

        List<ClientResponseDTO> results = new ArrayList<>();
        LlmTokenTotalsDTO runTokenTotals = LlmTokenTotalsDTO.empty();
        for (TeamRepositoryDTO repo : repositories) {
            ClientResponseDTO gitResult = persistenceService.saveGitAnalysisResult(repo, contributionData, exerciseId);
            if (gitResult == null) {
                continue;
            }
            AnalysisResultPersistenceService.ClientResponseWithUsage aiResult =
                    persistenceService.saveAIAnalysisResultWithUsage(repo, exerciseId);
            if (aiResult.response() != null) {
                results.add(aiResult.response());
            }
            runTokenTotals = runTokenTotals.merge(aiResult.tokenTotals());
        }

        log.info("LLM_USAGE scope=sync exerciseId={} teams={} llmCalls={} promptTokens={} completionTokens={} totalTokens={}",
                exerciseId, results.size(),
                runTokenTotals.llmCalls(), runTokenTotals.promptTokens(),
                runTokenTotals.completionTokens(), runTokenTotals.totalTokens());
        return results;
    }

    /**
     * Fetches team participations from Artemis and clones their repositories.
     *
     * @param credentials Artemis credentials
     * @param exerciseId  the exercise id
     * @return list of cloned team repository DTOs
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);

        return participations.parallelStream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> gitOperationsService.cloneAndFetchLogs(p, credentials, exerciseId))
                .toList();
    }

    // =====================================================================
    //  Streaming analysis pipeline
    // =====================================================================

    /**
     * Runs the full streaming analysis pipeline (download, git analysis, AI/simple CQI),
     * emitting SSE status updates through the event consumer as teams are processed.
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   the exercise id
     * @param mode         analysis mode ({@code FULL} or {@code SIMPLE})
     * @param eventEmitter consumer that receives SSE status events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                       AnalysisMode mode,
                                                       Consumer<Object> eventEmitter) {
        analysisTaskManager.registerStreamThread(exerciseId, Thread.currentThread());

        ExecutorService executor = null;
        try {
            // 1) Clear existing data for a clean slate
            transactionTemplate.executeWithoutResult(status ->
                    exerciseDataCleanupService.clearDatabaseForExercise(exerciseId));

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
            exerciseDataCleanupService.initializePendingTeams(validParticipations, exerciseId, false);
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
            analysisTaskManager.registerExecutor(exerciseId, executor);

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
                exerciseDataCleanupService.markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", downloadedCount.get(), "total", totalToProcess));
                return;
            }

            // 5) Phase 2 — Git Analysis
            int clonedCount = clonedRepos.size();
            log.info("Phase 2: Git-analyzing {} repositories", clonedCount);
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "GIT_ANALYSIS", "total", clonedCount));

            CountDownLatch analysisLatch = new CountDownLatch(clonedCount);
            AtomicInteger gitAnalyzedCount = new AtomicInteger(0);

            for (ParticipationDTO participation : validParticipations) {
                TeamRepositoryDTO repo = clonedRepos.get(participation.id());
                if (repo == null) {
                    continue;
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
                exerciseDataCleanupService.markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", gitAnalyzedCount.get(), "total", totalToProcess));
                return;
            }

            // 6) Detect template author
            detectTemplateAuthor(exerciseId, clonedRepos, eventEmitter);

            // 7) Phase 3 — CQI Computation
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

                    TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id()).orElse(null);
                    if (tp != null && persistenceService.shouldSkipTeam(tp)) {
                        aiAnalyzedCount.incrementAndGet();
                        continue;
                    }

                    runTokenTotals = processAIAnalysis(participation, repo, exerciseId,
                            aiAnalyzedCount, clonedRepos.size(), runTokenTotals, eventEmitter);
                }

                log.info("Phase 3 complete: AI analysis done for {} teams", aiAnalyzedCount.get());
                log.info("LLM_USAGE scope=stream exerciseId={} teams={} llmCalls={} promptTokens={} completionTokens={} totalTokens={}",
                        exerciseId, aiAnalyzedCount.get(),
                        runTokenTotals.llmCalls(), runTokenTotals.promptTokens(),
                        runTokenTotals.completionTokens(), runTokenTotals.totalTokens());

                if (analysisStateService.isRunning(exerciseId)) {
                    analysisStateService.completeAnalysis(exerciseId);
                    eventEmitter.accept(Map.of("type", "DONE"));
                } else {
                    exerciseDataCleanupService.markPendingTeamsAsCancelled(exerciseId);
                    eventEmitter.accept(Map.of("type", "CANCELLED",
                            "processed", aiAnalyzedCount.get(), "total", totalToProcess));
                }
            }

        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.info("Analysis cancelled (executor shut down) for exerciseId={}", exerciseId);
            exerciseDataCleanupService.markPendingTeamsAsCancelled(exerciseId);
            analysisStateService.cancelAnalysis(exerciseId);
            eventEmitter.accept(Map.of("type", "CANCELLED"));
        } catch (Exception e) {
            log.error("Analysis failed for exerciseId={}", exerciseId, e);
            analysisStateService.failAnalysis(exerciseId, e.getMessage());
            eventEmitter.accept(Map.of("type", "ERROR", "message", e.getMessage()));
        } finally {
            analysisTaskManager.unregisterExecutor(exerciseId);
            analysisTaskManager.unregisterStreamThread(exerciseId);
            shutdownExecutorQuietly(executor);
        }
    }

    // =====================================================================
    //  Streaming pipeline helpers
    // =====================================================================

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
            exerciseDataCleanupService.markTeamAsFailed(participation, exerciseId);
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
                .execute(status -> persistenceService.saveGitAnalysisResult(finalRepo, contributions, exerciseId, finalMode));

        if (gitDto == null) {
            return;
        }

        int current = gitAnalyzedCount.incrementAndGet();

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
            exerciseDataCleanupService.markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
        }
        counter.incrementAndGet();
    }

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
            exerciseDataCleanupService.markTeamAsFailed(participation, exerciseId);
            synchronized (eventEmitter) {
                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
            }
        }
        counter.incrementAndGet();
    }

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
            AnalysisResultPersistenceService.ClientResponseWithUsage aiResult = transactionTemplate
                    .execute(status -> persistenceService.saveAIAnalysisResultWithUsage(finalRepo, exerciseId));
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

    private void finalizeSimpleMode(List<ParticipationDTO> validParticipations,
                                     Map<Long, TeamRepositoryDTO> clonedRepos,
                                     Long exerciseId,
                                     Consumer<Object> eventEmitter) {
        AtomicInteger processedCount = new AtomicInteger(0);
        int total = validParticipations.size();

        for (ParticipationDTO participation : validParticipations) {
            if (!analysisStateService.isRunning(exerciseId) || Thread.currentThread().isInterrupted()) {
                break;
            }

            TeamRepositoryDTO repo = clonedRepos.get(participation.id());
            if (repo == null) {
                processedCount.incrementAndGet();
                continue;
            }

            TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id()).orElse(null);
            if (tp != null && persistenceService.shouldSkipTeam(tp)) {
                processedCount.incrementAndGet();
                continue;
            }

            String teamName = participation.team() != null ? participation.team().name() : "Unknown";
            try {
                ClientResponseDTO result = transactionTemplate.execute(status ->
                        persistenceService.calculateAndPersistSimpleCqi(participation, repo, exerciseId));

                int current = processedCount.incrementAndGet();
                analysisStateService.updateProgress(exerciseId, teamName, "DONE", current);
                emitStatusUpdate(eventEmitter, teamName, "DONE", current, total);

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

        if (analysisStateService.isRunning(exerciseId)) {
            analysisStateService.completeAnalysis(exerciseId);
            eventEmitter.accept(Map.of("type", "DONE"));
        } else {
            exerciseDataCleanupService.markPendingTeamsAsCancelled(exerciseId);
            eventEmitter.accept(Map.of("type", "CANCELLED",
                    "processed", processedCount.get(), "total", total));
        }
    }

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

            Optional<String> unanimousAuthor = rootAuthorCounts.entrySet().stream()
                    .filter(e -> e.getValue() == totalRepos)
                    .map(Map.Entry::getKey)
                    .findFirst();

            if (unanimousAuthor.isPresent() && totalRepos > 0) {
                String email = unanimousAuthor.get();
                templateAuthorRepository.save(new ExerciseTemplateAuthor(exerciseId, email, true));
                synchronized (eventEmitter) {
                    eventEmitter.accept(Map.of("type", "TEMPLATE_AUTHOR",
                            "email", email, "autoDetected", true));
                }
            } else if (!rootAuthorCounts.isEmpty()) {
                synchronized (eventEmitter) {
                    eventEmitter.accept(Map.of("type", "TEMPLATE_AUTHOR_AMBIGUOUS",
                            "candidates", new ArrayList<>(rootAuthorCounts.keySet())));
                }
            }
        } catch (Exception e) {
            log.warn("Template author detection failed for exerciseId={}: {}", exerciseId, e.getMessage());
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
