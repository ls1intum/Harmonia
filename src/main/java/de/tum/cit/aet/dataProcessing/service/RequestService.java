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
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import de.tum.cit.aet.ai.dto.LlmTokenUsage;
import de.tum.cit.aet.ai.dto.LlmTokenTotals;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.ai.service.ContributionFairnessService.FairnessReportWithUsage;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIPenaltyDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track active executors by exerciseId for cancellation (download/git phase)
    private final Map<Long, ExecutorService> activeExecutors = new ConcurrentHashMap<>();

    // Track running stream analysis tasks by exerciseId for cancellation
    private final Map<Long, Thread> runningStreamTasks = new ConcurrentHashMap<>();

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
            CommitChunkerService commitChunkerService,
            TransactionTemplate transactionTemplate) {
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
        this.transactionTemplate = transactionTemplate;
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
        List<ClientResponseDTO> results = saveResults(repositories, contributionData, exerciseId);

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
     * @param exerciseId       The Artemis exercise ID for this analysis
     * @return List of ClientResponseDTO with saved results
     */
    public List<ClientResponseDTO> saveResults(List<TeamRepositoryDTO> repositories,
                                               Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        // Note: We no longer clear the database here since we have exercise-specific
        // filtering. Users can manually clear data using the "Clear Data" button which
        // calls clearDatabaseForExercise(exerciseId).

        // Process and save each repository result
        List<ClientResponseDTO> results = new ArrayList<>();
        LlmTokenTotals runTokenTotals = LlmTokenTotals.empty();
        for (TeamRepositoryDTO repo : repositories) {
            ClientResponseWithUsage resultWithUsage = saveSingleResultWithUsage(repo, contributionData, exerciseId);
            if (resultWithUsage.response() != null) {
                results.add(resultWithUsage.response());
            }
            runTokenTotals = runTokenTotals.merge(resultWithUsage.tokenTotals());
        }
        logTotalUsage("sync", exerciseId, results.size(), runTokenTotals);
        return results;
    }

    /**
     * Stops a running analysis by interrupting its executor and stream thread.
     * This should be called when the user cancels an analysis.
     *
     * @param exerciseId the exercise ID to stop analysis for
     */
    public void stopAnalysis(Long exerciseId) {
        log.info("Stopping analysis for exercise {}", exerciseId);

        // Stop the download/git analysis executor
        ExecutorService executor = activeExecutors.remove(exerciseId);
        if (executor != null) {
            log.info("Shutting down executor for exercise {}", exerciseId);
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Executor for exercise {} did not terminate within 2 seconds", exerciseId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while stopping executor for exercise {}", exerciseId);
            }
        }

        // Interrupt the main stream analysis thread (this stops AI analysis phase)
        Thread streamThread = runningStreamTasks.remove(exerciseId);
        if (streamThread != null && streamThread.isAlive()) {
            log.info("Interrupting stream analysis thread for exercise {}", exerciseId);
            streamThread.interrupt();
        }
    }

    /**
     * Clears all data for a specific exercise from the database tables.
     * This is the public API that uses @Transactional for external calls.
     *
     * @param exerciseId the Artemis exercise ID to clear data for
     */
    @Transactional
    public void clearDatabaseForExercise(Long exerciseId) {
        clearDatabaseForExerciseInternal(exerciseId);
    }

    /**
     * Internal method to clear database - called within transactionTemplate.
     */
    private void clearDatabaseForExerciseInternal(Long exerciseId) {
        log.info("Clearing database for exercise {}", exerciseId);

        // Find all participations for this exercise
        var participations = teamParticipationRepository.findAllByExerciseId(exerciseId);

        if (participations.isEmpty()) {
            log.info("No participations found for exercise {}", exerciseId);
            return;
        }

        // Delete child entities first due to foreign key constraints
        for (var participation : participations) {
            // Delete team repository for this participation (references participation)
            teamRepositoryRepository.deleteAllByTeamParticipation(participation);
            // Delete analyzed chunks for this participation
            analyzedChunkRepository.deleteAllByParticipation(participation);
            // Delete students for this participation
            studentRepository.deleteAllByTeam(participation);
        }

        // Delete the participations themselves
        teamParticipationRepository.deleteAllByExerciseId(exerciseId);

        log.info("Cleared {} participations for exercise {}", participations.size(), exerciseId);
    }

    /**
     * Clears all data from the database tables (legacy method - use
     * clearDatabaseForExercise instead).
     */
    @Deprecated
    public void clearDatabase() {
        // Must delete child entities first due to foreign key constraints
        analyzedChunkRepository.deleteAll();
        teamRepositoryRepository.deleteAll();
        studentRepository.deleteAll();
        teamParticipationRepository.deleteAll();
        tutorRepository.deleteAll();
    }

    /**
     * Saves git analysis results (commits, lines of code) WITHOUT AI/CQI analysis.
     * This is Phase 2 of the phased analysis flow.
     *
     * @param repo             Repository data to save
     * @param contributionData Contribution data by student ID
     * @param exerciseId       The Artemis exercise ID for this analysis
     * @return Client response DTO with git metrics (no CQI)
     */
    public ClientResponseDTO saveGitAnalysisResult(TeamRepositoryDTO repo,
                                                   Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        // Step 1: Save tutor information
        Tutor tutor = ensureTutor(repo.participation().team());

        // Step 2: Save team participation
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                        team.shortName(), participation.repositoryUri(), participation.submissionCount()));

        teamParticipation.setExerciseId(exerciseId);
        teamParticipation.setTutor(tutor);
        teamParticipation.setSubmissionCount(participation.submissionCount());
        teamParticipation.setAnalysisStatus(AnalysisStatus.GIT_DONE);
        persistTeamTokenTotals(teamParticipation, LlmTokenTotals.empty());
        teamParticipationRepository.save(teamParticipation);

        // Step 3: Save students with their contribution metrics
        List<Student> students = new ArrayList<>();
        List<StudentAnalysisDTO> studentAnalysisDTOS = new ArrayList<>();

        // Delete existing students for this team (avoid duplicates)
        studentRepository.deleteAllByTeam(teamParticipation);

        for (ParticipantDTO student : repo.participation().team().students()) {
            AuthorContributionDTO contributionDTO = contributionData.get(student.id());

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

        // Step 5: Calculate git-only CQI components (no AI needed)
        CQIResultDTO gitCqiDetails = null;
        try {
            // Get commit chunks for git-based metrics
            Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
            List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(), commitToAuthor);

            // Apply pre-filter to remove trivial commits
            CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);

            // Calculate git-only components (locBalance, temporalSpread, ownershipSpread, pairProgramming)
            var gitComponents = cqiCalculatorService.calculateGitOnlyComponents(
                    filterResult.chunksToAnalyze(),
                    students.size(),
                    null,  // projectStart - will be determined from commits
                    null,  // projectEnd - will be determined from commits
                    team.name()  // teamName for pair programming calculation
            );

            // Store git-based components
            if (gitComponents != null) {
                teamParticipation.setCqiLocBalance(gitComponents.locBalance());
                teamParticipation.setCqiTemporalSpread(gitComponents.temporalSpread());
                teamParticipation.setCqiOwnershipSpread(gitComponents.ownershipSpread());
                if (gitComponents.pairProgramming() != null) {
                    teamParticipation.setCqiPairProgramming(gitComponents.pairProgramming());
                }
                teamParticipationRepository.save(teamParticipation);

                // Create partial CQI details with git-only components
                gitCqiDetails = CQIResultDTO.gitOnly(cqiCalculatorService.buildWeightsDTO(), gitComponents, filterResult.summary());

                log.debug("Git-only metrics for team {}: LoC={}, Temporal={}, Ownership={}, PairProgramming={}",
                        team.name(),
                        gitComponents.locBalance(),
                        gitComponents.temporalSpread(),
                        gitComponents.ownershipSpread(),
                        gitComponents.pairProgramming() != null ? String.format("%.1f", gitComponents.pairProgramming()) : "N/A");
            }
        } catch (Exception e) {
            log.warn("Failed to calculate git-only metrics for team {}: {}", team.name(), e.getMessage());
        }

        log.info("Git analysis complete for team: {} (commits={}, students={})",
                team.name(), participation.submissionCount(), students.size());

        // Return DTO with git metrics and git-only CQI components
        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.team().id(),
                participation.team().name(),
                participation.submissionCount(),
                studentAnalysisDTOS,
                null,  // CQI - will be calculated in Phase 3
                null,  // isSuspicious - will be calculated in Phase 3
                AnalysisStatus.GIT_DONE,
                gitCqiDetails,  // Partial CQI details with git-only components
                null,  // analysisHistory - will be populated in Phase 3
                null, // orphanCommits
                readTeamTokenTotals(teamParticipation));
    }

    /**
     * Performs AI analysis (CQI calculation) for a team.
     * This is Phase 3 of the phased analysis flow.
     *
     * @param repo       Repository data
     * @param exerciseId The Artemis exercise ID
     * @return Client response DTO with CQI and AI metrics
     */
    public ClientResponseDTO saveAIAnalysisResult(TeamRepositoryDTO repo, Long exerciseId) {
        return saveAIAnalysisResultWithUsage(repo, exerciseId).response();
    }

    private ClientResponseWithUsage saveAIAnalysisResultWithUsage(TeamRepositoryDTO repo, Long exerciseId) {
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElseThrow(() -> new IllegalStateException("Team participation not found for AI analysis"));

        // Set status to AI_ANALYZING
        teamParticipation.setAnalysisStatus(AnalysisStatus.AI_ANALYZING);
        teamParticipationRepository.save(teamParticipation);

        List<Student> students = studentRepository.findAllByTeam(teamParticipation);

        // Calculate CQI using effort-based fairness analysis
        Double cqi = null;
        boolean isSuspicious = false;
        List<AnalyzedChunkDTO> analysisHistory = null;
        List<OrphanCommitDTO> orphanCommits = null;
        CQIResultDTO cqiDetails = null;
        LlmTokenTotals teamTokenTotals = LlmTokenTotals.empty();

        // Step 1: Detect orphan commits
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

        // Step 2: Try effort-based fairness analysis (primary method)
        boolean fairnessAnalysisSucceeded = false;
        try {
            FairnessReportWithUsage fairnessResult = fairnessService.analyzeFairnessWithUsage(repo);
            FairnessReportDTO fairnessReport = fairnessResult.report();
            teamTokenTotals = fairnessResult.tokenTotals();
            boolean isErrorReport = fairnessReport.flags() != null &&
                    fairnessReport.flags().contains(de.tum.cit.aet.ai.domain.FairnessFlag.ANALYSIS_ERROR);
            if (!isErrorReport) {
                cqi = fairnessReport.balanceScore();
                isSuspicious = fairnessReport.requiresManualReview();
                analysisHistory = fairnessReport.analyzedChunks();
                cqiDetails = fairnessReport.cqiResult();
                fairnessAnalysisSucceeded = true;
                log.debug("Fairness analysis complete for team {}: score={}, suspicious={}",
                        team.name(), cqi, isSuspicious);

                if (analysisHistory != null && !analysisHistory.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, analysisHistory);
                }
            } else {
                log.warn("Fairness analysis returned error for team {}", team.name());
            }
        } catch (Exception e) {
            log.warn("Fairness analysis failed for team {}, falling back: {}", team.name(), e.getMessage());
        }

        // Step 3: Fallback to CQI calculator if fairness analysis failed
        if (!fairnessAnalysisSucceeded) {
            try {
                if (repo.localPath() != null) {
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(),
                            commitToAuthor);

                    CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);

                    CQIResultDTO cqiResult = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(),
                            students.size(),
                            filterResult.summary());

                    cqi = cqiResult.cqi();
                    cqiDetails = cqiResult;
                    log.debug("Fallback CQI for team {}: {}", team.name(), cqi);
                }
            } catch (Exception e) {
                log.warn("Fallback CQI calculation failed for team {}: {}", team.name(), e.getMessage());
            }

            // Step 4: Last resort - simple commit-count based balance
            if (cqi == null || cqi == 0.0) {
                Map<String, Integer> commitCounts = new HashMap<>();
                students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                if (!commitCounts.isEmpty()) {
                    cqi = balanceCalculator.calculate(commitCounts);
                }
            }
        }

        // Step 5: Update TeamParticipation with CQI
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(isSuspicious);

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

        persistTeamTokenTotals(teamParticipation, teamTokenTotals);
        teamParticipation.setAnalysisStatus(AnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        // Build student DTOs from persisted data
        List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Tutor tutor = teamParticipation.getTutor();

        log.info("AI analysis complete for team: {} (CQI={})", team.name(), cqi);

        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        participation.team().id(),
                        participation.team().name(),
                        participation.submissionCount(),
                        studentAnalysisDTOS,
                        cqi,
                        isSuspicious,
                        AnalysisStatus.DONE,
                        cqiDetails,
                        analysisHistory,
                        orphanCommits,
                        teamTokenTotals),
                teamTokenTotals);
    }

    /**
     * Saves a single repository analysis result to the database.
     * LEGACY METHOD - kept for backwards compatibility.
     * For new code, use saveGitAnalysisResult + saveAIAnalysisResult.
     *
     * @param repo             Repository data to save
     * @param contributionData Contribution data by student ID
     * @param exerciseId       The Artemis exercise ID for this analysis
     * @return Client response DTO with calculated metrics
     */
    public ClientResponseDTO saveSingleResult(TeamRepositoryDTO repo,
                                              Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        return saveSingleResultWithUsage(repo, contributionData, exerciseId).response();
    }

    private ClientResponseWithUsage saveSingleResultWithUsage(TeamRepositoryDTO repo,
                                                              Map<Long, AuthorContributionDTO> contributionData, Long exerciseId) {
        // Step 1: Save tutor information
        Tutor tutor = ensureTutor(repo.participation().team());

        // Step 2: Save team participation (CQI and isSuspicious will be updated later)
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();

        // Find existing participation to update
        TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                        team.shortName(), participation.repositoryUri(), participation.submissionCount()));

        // Update fields - keep status as ANALYZING until CQI is calculated
        teamParticipation.setExerciseId(exerciseId);
        teamParticipation.setTutor(tutor);
        teamParticipation.setSubmissionCount(participation.submissionCount());
        teamParticipation.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.ANALYZING);
        persistTeamTokenTotals(teamParticipation, LlmTokenTotals.empty());
        teamParticipationRepository.save(teamParticipation);

        // Step 3: Save students with their contribution metrics
        List<Student> students = new ArrayList<>();
        List<StudentAnalysisDTO> studentAnalysisDTOS = new ArrayList<>();

        // Delete existing students for this team (avoid duplicates from pending state)
        studentRepository.deleteAllByTeam(teamParticipation);

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
        LlmTokenTotals teamTokenTotals = LlmTokenTotals.empty();

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
        boolean fairnessAnalysisSucceeded = false;
        try {
            FairnessReportWithUsage fairnessResult = fairnessService.analyzeFairnessWithUsage(repo);
            FairnessReportDTO fairnessReport = fairnessResult.report();
            teamTokenTotals = fairnessResult.tokenTotals();
            // Check if this is an error report (has ANALYSIS_ERROR flag) or a valid
            // analysis
            boolean isErrorReport = fairnessReport.flags() != null &&
                    fairnessReport.flags().contains(de.tum.cit.aet.ai.domain.FairnessFlag.ANALYSIS_ERROR);
            if (!isErrorReport) {
                // Valid analysis result (even if balanceScore is 0)
                cqi = fairnessReport.balanceScore();
                isSuspicious = fairnessReport.requiresManualReview();
                analysisHistory = fairnessReport.analyzedChunks();
                cqiDetails = fairnessReport.cqiResult();
                fairnessAnalysisSucceeded = true;
                log.debug("Fairness analysis complete for team {}: score={}, suspicious={}, chunks={}",
                        team.name(), cqi, isSuspicious, analysisHistory != null ? analysisHistory.size() : 0);

                // Persist analyzed chunks to database
                if (analysisHistory != null && !analysisHistory.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, analysisHistory);
                }
            } else {
                log.warn("Fairness analysis returned error for team {}", team.name());
            }
        } catch (Exception e) {
            log.warn("Fairness analysis failed for team {}, falling back to balance calculator: {}",
                    team.name(), e.getMessage());
        }

        // Step 5c: Fallback to CQI calculator with pre-filtered commits if fairness
        // analysis failed
        if (!fairnessAnalysisSucceeded) {
            try {
                // Try pre-filtered LoC-based CQI calculation
                if (repo.localPath() != null) {
                    Map<String, Long> commitToAuthor = gitContributionAnalysisService.buildCommitToAuthorMap(repo);
                    List<CommitChunkDTO> allChunks = commitChunkerService.processRepository(repo.localPath(),
                            commitToAuthor);

                    // Apply pre-filter to remove trivial commits
                    CommitPreFilterService.PreFilterResult filterResult = commitPreFilterService.preFilter(allChunks);

                    log.info("Pre-filter for team {}: {} of {} commits will be analyzed",
                            team.name(), filterResult.chunksToAnalyze().size(), allChunks.size());

                    // Calculate CQI using fallback (LoC-based, no LLM)
                    CQIResultDTO cqiResult = cqiCalculatorService.calculateFallback(
                            filterResult.chunksToAnalyze(),
                            students.size(),
                            filterResult.summary());

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

        // Step 6b: NOW set status to DONE - after all data is populated
        // This prevents race conditions where status=DONE but CQI is still null
        persistTeamTokenTotals(teamParticipation, teamTokenTotals);
        teamParticipation.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.DONE);
        teamParticipationRepository.save(teamParticipation);

        // Step 7: Return the assembled client response DTO
        return new ClientResponseWithUsage(
                new ClientResponseDTO(
                        tutor != null ? tutor.getName() : "Unassigned",
                        participation.team().id(),
                        participation.team().name(),
                        participation.submissionCount(),
                        studentAnalysisDTOS,
                        cqi,
                        isSuspicious,
                        teamParticipation.getAnalysisStatus(),
                        cqiDetails,
                        analysisHistory,
                        orphanCommits,
                        teamTokenTotals),
                teamTokenTotals);
    }

    /**
     * Fetches, analyzes, and saves repositories with streaming updates.
     * Uses a 3-phase approach:
     * - Phase 1: Download all repositories (parallel)
     * - Phase 2: Git analysis (commits, lines of code) with immediate updates
     * - Phase 3: AI analysis (CQI calculation) after all git analysis is complete
     *
     * @param credentials  Artemis credentials
     * @param exerciseId   Exercise ID to fetch repositories for
     * @param eventEmitter Consumer to emit progress events
     */
    public void fetchAnalyzeAndSaveRepositoriesStream(ArtemisCredentials credentials, Long exerciseId,
                                                      java.util.function.Consumer<Object> eventEmitter) {
        // Track this thread for cancellation
        runningStreamTasks.put(exerciseId, Thread.currentThread());

        ExecutorService executor = null;
        try {
            // Step 0: Clear all existing data for this exercise to ensure clean state
            log.info("Clearing existing data for exercise {} before starting analysis", exerciseId);
            transactionTemplate.executeWithoutResult(status -> {
                clearDatabaseForExerciseInternal(exerciseId);
            });

            // Step 1: Fetch all participations from Artemis
            List<ParticipationDTO> participations = repositoryFetchingService.fetchParticipations(credentials,
                    exerciseId);

            // Step 2: Filter participations with valid repositories
            List<ParticipationDTO> validParticipations = participations.stream()
                    .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                    .toList();

            int totalToProcess = validParticipations.size();
            log.info("Found {} teams to analyze", totalToProcess);

            // Start fresh analysis state
            analysisStateService.startAnalysis(exerciseId, totalToProcess);

            // Emit total count
            eventEmitter.accept(Map.of("type", "START", "total", validParticipations.size()));

            // Step 3: Persist PENDING state to database immediately
            initializePendingTeams(validParticipations, exerciseId, false);

            // Emit all teams as PENDING
            for (ParticipationDTO participation : validParticipations) {
                if (participation.team() != null) {
                    TeamDTO team = participation.team();

                    Map<String, Object> pendingTeam = new HashMap<>();
                    pendingTeam.put("teamId", team.id());
                    pendingTeam.put("teamName", team.name());
                    pendingTeam.put("shortName", team.shortName());
                    pendingTeam.put("repositoryUri", participation.repositoryUri());
                    pendingTeam.put("submissionCount", participation.submissionCount());
                    pendingTeam.put("tutor", team.owner() != null ? team.owner().name() : "Unassigned");

                    List<Map<String, Object>> students = new java.util.ArrayList<>();
                    if (team.students() != null) {
                        for (ParticipantDTO student : team.students()) {
                            Map<String, Object> studentData = new HashMap<>();
                            studentData.put("name", student.name());
                            studentData.put("login", student.login());
                            students.add(studentData);
                        }
                    }
                    pendingTeam.put("students", students);
                    pendingTeam.put("cqi", null);
                    pendingTeam.put("isSuspicious", null);
                    pendingTeam.put("analysisStatus", "PENDING");

                    eventEmitter.accept(Map.of("type", "INIT", "data", pendingTeam));
                }
            }

            if (validParticipations.isEmpty()) {
                log.info("No teams to analyze for exercise {}", exerciseId);
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
                return;
            }

            // ============================================================
            // PHASE 1+2: DOWNLOAD AND GIT ANALYSIS IN PARALLEL
            // Each team is analyzed immediately after download
            // ============================================================
            log.info("Phase 1+2: Downloading and analyzing {} repositories in parallel", validParticipations.size());

            // Emit phase change event
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "GIT_ANALYSIS", "total", validParticipations.size()));

            Map<Long, TeamRepositoryDTO> clonedRepos = new java.util.concurrent.ConcurrentHashMap<>();

            int threadCount = Math.max(1, Math.min(validParticipations.size(),
                    Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);
            activeExecutors.put(exerciseId, executor);

            CountDownLatch downloadAndAnalyzeLatch = new CountDownLatch(validParticipations.size());
            java.util.concurrent.atomic.AtomicInteger gitAnalyzedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            for (ParticipationDTO participation : validParticipations) {
                executor.submit(() -> {
                    try {
                        if (!analysisStateService.isRunning(exerciseId)) {
                            return;
                        }

                        String teamName = participation.team() != null ? participation.team().name() : "Unknown";

                        // Step 1: Download
                        analysisStateService.updateProgress(exerciseId, teamName, "DOWNLOADING", gitAnalyzedCount.get());

                        TeamRepositoryDTO repo = repositoryFetchingService.cloneTeamRepository(
                                participation, credentials, exerciseId);

                        if (repo == null) {
                            log.warn("Failed to clone repository for team {}", teamName);
                            markTeamAsFailed(participation, exerciseId);
                            synchronized (eventEmitter) {
                                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
                            }
                            return;
                        }

                        clonedRepos.put(participation.id(), repo);

                        // Step 2: Git Analysis immediately after download
                        analysisStateService.updateProgress(exerciseId, teamName, "GIT_ANALYZING", gitAnalyzedCount.get());
                        synchronized (eventEmitter) {
                            eventEmitter.accept(Map.of(
                                    "type", "GIT_ANALYZING",
                                    "teamId", participation.team().id(),
                                    "teamName", teamName));
                        }

                        // Analyze git contributions (commits, lines of code)
                        Map<Long, AuthorContributionDTO> contributions = analysisService.analyzeRepository(repo);

                        // Save git analysis results (no CQI yet)
                        final TeamRepositoryDTO finalRepo = repo;
                        ClientResponseDTO gitDto = transactionTemplate
                                .execute(status -> saveGitAnalysisResult(finalRepo, contributions, exerciseId));

                        int currentProcessed = gitAnalyzedCount.incrementAndGet();
                        analysisStateService.updateProgress(exerciseId, teamName, "GIT_DONE", currentProcessed);

                        // Emit git analysis complete - client can now show commits and lines
                        synchronized (eventEmitter) {
                            eventEmitter.accept(Map.of("type", "GIT_UPDATE", "data", gitDto));
                        }

                        log.debug("Download + Git analysis {}/{}: {}", currentProcessed, validParticipations.size(), teamName);

                    } catch (Exception e) {
                        // Check if this was an interrupt (analysis cancelled) vs a real error
                        boolean isInterrupt = Thread.currentThread().isInterrupted() ||
                                e.getCause() instanceof InterruptedException ||
                                (e.getCause() != null && e.getCause().getCause() instanceof java.nio.channels.ClosedByInterruptException);

                        if (isInterrupt) {
                            log.info("Download/analysis interrupted for team {} (analysis likely cancelled)",
                                    participation.team() != null ? participation.team().name() : participation.id());
                            // Don't mark as failed - just leave as PENDING or mark as CANCELLED
                        } else {
                            log.error("Failed to download/analyze repo for team {}",
                                    participation.team() != null ? participation.team().name() : participation.id(), e);
                            markTeamAsFailed(participation, exerciseId);
                            synchronized (eventEmitter) {
                                eventEmitter.accept(Map.of("type", "ERROR_TEAM", "teamId", participation.team().id()));
                            }
                        }
                        gitAnalyzedCount.incrementAndGet();
                    } finally {
                        downloadAndAnalyzeLatch.countDown();
                    }
                });
            }

            try {
                downloadAndAnalyzeLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Download+Analysis phase interrupted");
            }

            executor.shutdown();

            log.info("Phase 1+2 complete: Downloaded and analyzed {} of {} repositories", clonedRepos.size(),
                    validParticipations.size());

            // Emit git phase complete
            eventEmitter.accept(Map.of("type", "GIT_DONE", "processed", gitAnalyzedCount.get()));

            if (!analysisStateService.isRunning(exerciseId)) {
                log.info("Analysis cancelled after git analysis phase");
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", gitAnalyzedCount.get(),
                        "total", totalToProcess));
                return;
            }

            // ============================================================
            // PHASE 3: AI ANALYSIS - Calculate CQI scores
            // This runs AFTER all git analysis is complete
            // ============================================================
            log.info("Phase 3: AI analysis (CQI calculation) for {} teams", clonedRepos.size());

            // Emit phase change event
            eventEmitter.accept(Map.of("type", "PHASE", "phase", "AI_ANALYSIS", "total", clonedRepos.size()));

            java.util.concurrent.atomic.AtomicInteger aiAnalyzedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            LlmTokenTotals runTokenTotals = LlmTokenTotals.empty();

            for (ParticipationDTO participation : validParticipations) {
                // Check for cancellation (both state-based and interrupt-based)
                if (!analysisStateService.isRunning(exerciseId) || Thread.currentThread().isInterrupted()) {
                    log.info("Analysis cancelled during AI analysis phase");
                    break;
                }

                TeamRepositoryDTO repo = clonedRepos.get(participation.id());
                String teamName = participation.team() != null ? participation.team().name() : "Unknown";

                if (repo == null) {
                    aiAnalyzedCount.incrementAndGet();
                    continue;
                }

                try {
                    // Notify frontend this team's AI analysis is starting
                    analysisStateService.updateProgress(exerciseId, teamName, "AI_ANALYZING", aiAnalyzedCount.get());
                    synchronized (eventEmitter) {
                        eventEmitter.accept(Map.of(
                                "type", "AI_ANALYZING",
                                "teamId", participation.team().id(),
                                "teamName", teamName));
                    }

                    // Perform AI analysis (CQI calculation)
                    final TeamRepositoryDTO finalRepo = repo;
                    ClientResponseWithUsage aiResult = transactionTemplate
                            .execute(status -> saveAIAnalysisResultWithUsage(finalRepo, exerciseId));
                    ClientResponseDTO aiDto = aiResult != null ? aiResult.response() : null;
                    if (aiResult != null) {
                        runTokenTotals = runTokenTotals.merge(aiResult.tokenTotals());
                    }

                    int currentProcessed = aiAnalyzedCount.incrementAndGet();
                    analysisStateService.updateProgress(exerciseId, teamName, "DONE", currentProcessed);

                    // Emit AI analysis complete - client can now show CQI
                    synchronized (eventEmitter) {
                        eventEmitter.accept(Map.of("type", "AI_UPDATE", "data", aiDto));
                    }

                    log.debug("AI analysis {}/{}: {} (CQI={})", currentProcessed, clonedRepos.size(),
                            teamName, aiDto != null ? aiDto.cqi() : "N/A");

                } catch (Exception e) {
                    log.error("Error in AI analysis for team {}", teamName, e);
                    // Don't fail the team - git data is still valid
                    aiAnalyzedCount.incrementAndGet();
                    analysisStateService.updateProgress(exerciseId, teamName, "AI_ERROR", aiAnalyzedCount.get());
                    synchronized (eventEmitter) {
                        eventEmitter.accept(Map.of(
                                "type", "AI_ERROR",
                                "teamId", participation.team().id(),
                                "teamName", teamName,
                                "error", e.getMessage()));
                    }
                }
            }

            log.info("Phase 3 complete: AI analysis done for {} teams", aiAnalyzedCount.get());
            logTotalUsage("stream", exerciseId, aiAnalyzedCount.get(), runTokenTotals);

            // Complete analysis
            if (analysisStateService.isRunning(exerciseId)) {
                analysisStateService.completeAnalysis(exerciseId);
                eventEmitter.accept(Map.of("type", "DONE"));
            } else {
                markPendingTeamsAsCancelled(exerciseId);
                eventEmitter.accept(Map.of("type", "CANCELLED",
                        "processed", aiAnalyzedCount.get(),
                        "total", totalToProcess));
            }

        } catch (Exception e) {
            log.error("Analysis failed for exercise {}", exerciseId, e);
            analysisStateService.failAnalysis(exerciseId, e.getMessage());
            eventEmitter.accept(Map.of("type", "ERROR", "message", e.getMessage()));
        } finally {
            // Remove from tracking maps
            activeExecutors.remove(exerciseId);
            runningStreamTasks.remove(exerciseId);

            // Shutdown executor if still running
            if (executor != null && !executor.isShutdown()) {
                log.info("Shutting down executor for exercise {}", exerciseId);
                executor.shutdownNow();
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

    private void logTotalUsage(String scope, Long exerciseId, int analyzedTeams, LlmTokenTotals tokenTotals) {
        log.info(
                "LLM_USAGE total scope={} exerciseId={} analyzedTeams={} llmCalls={} callsWithUsage={} promptTokens={} completionTokens={} totalTokens={}",
                scope,
                exerciseId,
                analyzedTeams,
                tokenTotals.llmCalls(),
                tokenTotals.callsWithUsage(),
                tokenTotals.promptTokens(),
                tokenTotals.completionTokens(),
                tokenTotals.totalTokens());
    }

    private void persistTeamTokenTotals(TeamParticipation teamParticipation, LlmTokenTotals tokenTotals) {
        if (tokenTotals == null) {
            teamParticipation.setLlmCalls(null);
            teamParticipation.setLlmCallsWithUsage(null);
            teamParticipation.setLlmPromptTokens(null);
            teamParticipation.setLlmCompletionTokens(null);
            teamParticipation.setLlmTotalTokens(null);
            return;
        }

        teamParticipation.setLlmCalls(tokenTotals.llmCalls());
        teamParticipation.setLlmCallsWithUsage(tokenTotals.callsWithUsage());
        teamParticipation.setLlmPromptTokens(tokenTotals.promptTokens());
        teamParticipation.setLlmCompletionTokens(tokenTotals.completionTokens());
        teamParticipation.setLlmTotalTokens(tokenTotals.totalTokens());
    }

    private LlmTokenTotals readTeamTokenTotals(TeamParticipation teamParticipation) {
        if (teamParticipation.getLlmCalls() == null
                && teamParticipation.getLlmCallsWithUsage() == null
                && teamParticipation.getLlmPromptTokens() == null
                && teamParticipation.getLlmCompletionTokens() == null
                && teamParticipation.getLlmTotalTokens() == null) {
            return null;
        }

        long promptTokens = teamParticipation.getLlmPromptTokens() != null ? teamParticipation.getLlmPromptTokens() : 0L;
        long completionTokens = teamParticipation.getLlmCompletionTokens() != null
                ? teamParticipation.getLlmCompletionTokens()
                : 0L;
        long totalTokens = teamParticipation.getLlmTotalTokens() != null
                ? teamParticipation.getLlmTotalTokens()
                : promptTokens + completionTokens;

        return new LlmTokenTotals(
                teamParticipation.getLlmCalls() != null ? teamParticipation.getLlmCalls() : 0L,
                teamParticipation.getLlmCallsWithUsage() != null ? teamParticipation.getLlmCallsWithUsage() : 0L,
                promptTokens,
                completionTokens,
                totalTokens);
    }

    private record ClientResponseWithUsage(ClientResponseDTO response, LlmTokenTotals tokenTotals) {
    }

    /**
     * Check if a team has been fully analyzed (has valid CQI calculated).
     * A team is considered "analyzed" only if it has a CQI value > 0.
     * CQI = 0 indicates a failed or incomplete analysis.
     */
    private boolean isTeamAlreadyAnalyzed(Long participationId) {
        var participation = teamParticipationRepository.findByParticipation(participationId);
        if (participation.isEmpty()) {
            return false;
        }
        Double cqi = participation.get().getCqi();
        return cqi != null && cqi > 0;
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
                            participation.getAnalysisStatus(),
                            cqiDetails,
                            loadAnalyzedChunks(participation),
                            null, // Orphan commits are not persisted, only shown during live analysis
                            readTeamTokenTotals(participation));
                })
                .toList();

        log.info("RequestService: Extracted {} team participation records from the database.", responseDTOs.size());
        return responseDTOs;
    }

    /**
     * Step 1: Gets all teams for a specific exercise from the database.
     * This allows checking for existing data before triggering a new analysis.
     *
     * @param exerciseId The Artemis exercise ID
     * @return List of ClientResponseDTO for the exercise, empty if no data exists
     */
    public List<ClientResponseDTO> getTeamsByExerciseId(Long exerciseId) {
        log.info("RequestService: Fetching teams for exercise ID: {}", exerciseId);

        // Step 1a: Fetch all TeamParticipation records for this exercise
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);

        if (participations.isEmpty()) {
            log.info("No teams found in database for exercise ID: {}", exerciseId);
            return new ArrayList<>();
        }

        // Step 1b: Map all participations to DTOs
        // DONE teams will have full data, PENDING/ANALYZING will have null CQI
        // The status field tells the frontend what state each team is in
        List<ClientResponseDTO> responseDTOs = participations.stream()
                .map(this::mapParticipationToClientResponse)
                .toList();

        long doneCount = participations.stream()
                .filter(p -> p.getAnalysisStatus() == de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.DONE)
                .count();

        log.info("RequestService: Found {} teams ({} analyzed) for exercise ID: {}",
                responseDTOs.size(), doneCount, exerciseId);
        return responseDTOs;
    }

    /**
     * Step 2: Checks if analyzed data exists for a specific exercise.
     * Returns true if at least one team has a CQI value calculated.
     *
     * @param exerciseId The Artemis exercise ID
     * @return true if analyzed data exists, false otherwise
     */
    public boolean hasAnalyzedDataForExercise(Long exerciseId) {
        boolean hasData = teamParticipationRepository.existsByExerciseIdAndCqiIsNotNull(exerciseId);
        log.debug("Exercise {} has analyzed data: {}", exerciseId, hasData);
        return hasData;
    }

    /**
     * Helper method to map a TeamParticipation entity to ClientResponseDTO.
     * Reduces code duplication between getAllRepositoryData and
     * getTeamsByExerciseId.
     */
    private ClientResponseDTO mapParticipationToClientResponse(TeamParticipation participation) {
        // Step 1: Fetch students for this participation
        List<Student> students = studentRepository.findAllByTeam(participation);
        Tutor tutor = participation.getTutor();

        // Step 2: Map students to StudentAnalysisDTOs
        List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream()
                .map(student -> new StudentAnalysisDTO(
                        student.getName(),
                        student.getCommitCount(),
                        student.getLinesAdded(),
                        student.getLinesDeleted(),
                        student.getLinesChanged()))
                .toList();

        // Step 3: Get persisted CQI and isSuspicious values
        Double cqi = participation.getCqi();
        Boolean isSuspicious = participation.getIsSuspicious() != null ? participation.getIsSuspicious() : false;

        // Step 4: Fallback - recalculate if CQI is null (legacy data)
        if (cqi == null) {
            Map<String, Integer> commitCounts = new HashMap<>();
            students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
            if (!commitCounts.isEmpty()) {
                cqi = balanceCalculator.calculate(commitCounts);
            }
        }

        // Step 5: Reconstruct CQI details from persisted components
        CQIResultDTO cqiDetails = reconstructCqiDetails(participation);

        // Step 6: Assemble final response DTO
        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.getTeam(),
                participation.getName(),
                participation.getSubmissionCount(),
                studentAnalysisDTOS,
                cqi,
                isSuspicious,
                participation.getAnalysisStatus(),
                cqiDetails,
                loadAnalyzedChunks(participation),
                null, // Orphan commits are not persisted
                readTeamTokenTotals(participation));
    }

    /**
     * Saves analyzed chunks to the database for a given participation.
     */
    private void saveAnalyzedChunks(TeamParticipation participation, List<AnalyzedChunkDTO> chunks) {
        try {
            List<AnalyzedChunk> entities = chunks.stream()
                    .map(dto -> {
                        LlmTokenUsage usage = dto.llmTokenUsage();
                        return new AnalyzedChunk(
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
                                dto.errorMessage(),
                                dto.isExternalContributor(),
                                usage != null ? usage.model() : null,
                                usage != null ? usage.promptTokens() : null,
                                usage != null ? usage.completionTokens() : null,
                                usage != null ? usage.totalTokens() : null,
                                usage != null ? usage.usageAvailable() : null);
                    })
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
                            chunk.getErrorMessage(),
                            Boolean.TRUE.equals(chunk.getIsExternalContributor()),
                            new LlmTokenUsage(
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
                participation.getCqiOwnershipSpread() != null ? participation.getCqiOwnershipSpread() : 0.0,
                participation.getCqiPairProgramming());

        // Reconstruct penalties
        List<CQIPenaltyDTO> penalties = deserializePenalties(participation.getCqiPenalties());

        // Build the full CQI result
        return new CQIResultDTO(
                participation.getCqi() != null ? participation.getCqi() : 0.0,
                components,
                cqiCalculatorService.buildWeightsDTO(),
                penalties,
                participation.getCqiBaseScore() != null ? participation.getCqiBaseScore() : 0.0,
                participation.getCqiPenaltyMultiplier() != null ? participation.getCqiPenaltyMultiplier() : 1.0,
                null // FilterSummary is not persisted - only available during live analysis
        );
    }

    /**
     * Helper to ensure a tutor exists in the database.
     */
    private Tutor ensureTutor(TeamDTO team) {
        if (team.owner() != null) {
            ParticipantDTO tut = team.owner();
            Tutor tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
            return tutorRepository.save(tutor);
        }
        return null;
    }

    /**
     * Initializes all teams with PENDING status in the database.
     */
    private void initializePendingTeams(List<ParticipationDTO> participations, Long exerciseId, boolean isResume) {
        log.info("RequestService: Initializing {} teams with PENDING status (resume={})", participations.size(),
                isResume);
        for (ParticipationDTO participation : participations) {
            if (participation.team() == null) {
                continue;
            }

            // Check if exists
            var existing = teamParticipationRepository.findByParticipation(participation.id());

            TeamParticipation teamParticipation;
            if (existing.isPresent()) {
                teamParticipation = existing.get();

                // PRESERVE already-analyzed teams with valid CQI data (CQI > 0) - don't reset
                // them
                // CQI of 0 indicates incomplete/failed analysis, so we should re-analyze
                if (teamParticipation.getCqi() != null && teamParticipation.getCqi() > 0 &&
                        teamParticipation
                                .getAnalysisStatus() == de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.DONE) {
                    log.debug("Preserving already-analyzed team {} with CQI {}",
                            participation.team().name(), teamParticipation.getCqi());
                    continue; // Skip - this team is already done
                }

                // If NOT resuming and not fully analyzed, reset status to PENDING
                if (!isResume) {
                    teamParticipation
                            .setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.PENDING);
                    // Also ensure tutor is up to date
                    teamParticipation.setTutor(ensureTutor(participation.team()));
                    teamParticipationRepository.save(teamParticipation);
                } else if (teamParticipation.getAnalysisStatus() == null) {
                    // Fallback for missing status
                    teamParticipation
                            .setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.PENDING);
                    teamParticipationRepository.save(teamParticipation);
                }
            } else {
                // Create new pending team
                Tutor tutor = ensureTutor(participation.team());
                teamParticipation = new TeamParticipation(
                        participation.id(),
                        participation.team().id(),
                        tutor,
                        participation.team().name(),
                        participation.team().shortName(),
                        participation.repositoryUri(),
                        participation.submissionCount());
                teamParticipation.setExerciseId(exerciseId);
                teamParticipation.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.PENDING);
                teamParticipationRepository.save(teamParticipation);

                // Also save students with basic info (no commit counts yet)
                // This ensures student names are visible for pending teams after page refresh
                if (participation.team().students() != null) {
                    for (ParticipantDTO student : participation.team().students()) {
                        Student studentEntity = new Student(
                                student.id(),
                                student.login(),
                                student.name(),
                                student.email(),
                                teamParticipation,
                                0, 0, 0, 0); // No commit data yet
                        studentRepository.save(studentEntity);
                    }
                }
            }
        }
    }

    /**
     * Marks a team as failed in the database.
     */
    private void markTeamAsFailed(ParticipationDTO participation, Long exerciseId) {
        try {
            TeamDTO team = participation.team();
            Tutor tutor = ensureTutor(team);

            TeamParticipation teamParticipation = teamParticipationRepository.findByParticipation(participation.id())
                    .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                            team.shortName(), participation.repositoryUri(), participation.submissionCount()));

            teamParticipation.setExerciseId(exerciseId);
            teamParticipation.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.ERROR);
            teamParticipationRepository.save(teamParticipation);
        } catch (Exception e) {
            log.error("Failed to mark team {} as failed", participation.team().name(), e);
        }
    }

    /**
     * Marks all pending teams as CANCELLED when analysis is cancelled.
     */
    private void markPendingTeamsAsCancelled(Long exerciseId) {
        try {
            List<TeamParticipation> pendingTeams = teamParticipationRepository
                    .findAllByExerciseIdAndAnalysisStatus(exerciseId,
                            de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.PENDING);

            for (TeamParticipation team : pendingTeams) {
                team.setAnalysisStatus(de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus.CANCELLED);
                teamParticipationRepository.save(team);
            }

            log.info("Marked {} pending teams as CANCELLED for exercise {}", pendingTeams.size(), exerciseId);
        } catch (Exception e) {
            log.error("Failed to mark pending teams as cancelled for exercise {}", exerciseId, e);
        }
    }
}
