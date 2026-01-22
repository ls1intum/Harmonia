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

import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
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
    private final de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator pairingCalculator;
    private final ContributionFairnessService fairnessService;
    private final AnalysisStateService analysisStateService;
    private final GitContributionAnalysisService gitContributionAnalysisService;

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
            de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator pairingCalculator,
            ContributionFairnessService fairnessService,
            AnalysisStateService analysisStateService,
            TeamRepositoryRepository teamRepositoryRepository,
            TeamParticipationRepository teamParticipationRepository,
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository,
            GitContributionAnalysisService gitContributionAnalysisService) {
        this.repositoryFetchingService = repositoryFetchingService;
        this.analysisService = analysisService;
        this.balanceCalculator = balanceCalculator;
        this.pairingCalculator = pairingCalculator;
        this.fairnessService = fairnessService;
        this.analysisStateService = analysisStateService;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
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

        // Fetch and clone repositories
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);
        log.info("Fetched {} repositories from Artemis", repositories.size());

        // Limit to maxTeams
        if (repositories.size() > maxTeams) {
            log.info("Limiting analysis to first {} teams (out of {})", maxTeams, repositories.size());
            repositories = repositories.subList(0, maxTeams);
        }

        // Analyze contributions
        log.info("Analyzing contributions for {} teams...", repositories.size());
        Map<Long, AuthorContributionDTO> contributionData = getContributionData(repositories);

        // Save results to the database
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
    public List<ClientResponseDTO> saveResults(List<TeamRepositoryDTO> repositories,
            Map<Long, AuthorContributionDTO> contributionData) {
        // TODO: Implement a better strategy for updating existing records instead of
        // deleting all data
        // Clear existing data in database tables. We assume a full refresh of all data
        // is intended, effectively treating the run as idempotent
        clearDatabase();

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
        // Save tutor
        ParticipantDTO tut = repo.participation().team().owner();
        Tutor tutor = null;
        if (tut == null) {
            log.warn("No tutor found for team: {}", repo.participation().team().name());
        } else {
            tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
            tutorRepository.save(tutor);
        }

        // Save team participation (CQI and isSuspicious will be updated after
        // calculation)
        ParticipationDTO participation = repo.participation();
        TeamDTO team = participation.team();
        TeamParticipation teamParticipation = new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                team.shortName(), participation.repositoryUri(), participation.submissionCount());
        // Initial save - CQI will be updated after calculation
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

        // Calculate deterministic CQI (balance + pairing)
        Map<String, Integer> commitCounts = new HashMap<>();
        students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
        double balanceScore = commitCounts.isEmpty() ? 0.0 : balanceCalculator.calculate(commitCounts);

        List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> commitInfos = extractCommitInfo(repo);
        double pairingScore = pairingCalculator.calculate(commitInfos);

        Double cqi = (balanceScore * 0.5) + (pairingScore * 0.5);

        log.info("CQI calculation for team {}: balance={}, pairing={}, total={}",
                team.name(), balanceScore, pairingScore, cqi);

        boolean isSuspicious = false;
        List<AnalyzedChunkDTO> analysisHistory = null;
        List<OrphanCommitDTO> orphanCommits = null;

        // Detect orphan commits
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

        // Run AI fairness analysis for additional insights (optional)
        try {
            FairnessReportDTO fairnessReport = fairnessService.analyzeFairness(repo);
            if (fairnessReport != null) {
                isSuspicious = fairnessReport.requiresManualReview();
                analysisHistory = fairnessReport.analyzedChunks();
                log.debug("AI analysis complete for team {}: suspicious={}, chunks={}",
                        team.name(), isSuspicious, analysisHistory != null ? analysisHistory.size() : 0);

                if (analysisHistory != null && !analysisHistory.isEmpty()) {
                    saveAnalyzedChunks(teamParticipation, analysisHistory);
                }
            }
        } catch (Exception e) {
            log.warn("AI analysis failed for team {}: {}", team.name(), e.getMessage());
        }

        // Save CQI and isSuspicious to TeamParticipation
        teamParticipation.setCqi(cqi);
        teamParticipation.setIsSuspicious(isSuspicious);
        teamParticipationRepository.save(teamParticipation);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.team().id(),
                participation.team().name(),
                participation.submissionCount(),
                studentAnalysisDTOS,
                balanceScore,
                pairingScore,
                cqi,
                isSuspicious,
                analysisHistory,
                orphanCommits);
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

                    double balanceScore = 0.0;
                    double pairingScore = 0.0;
                    Double cqi = null;
                    Boolean isSuspicious = participation.getIsSuspicious() != null ? participation.getIsSuspicious()
                            : false;

                    // Calculate balance score from student data
                    Map<String, Integer> commitCounts = new HashMap<>();
                    students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                    balanceScore = commitCounts.isEmpty() ? 0.0 : balanceCalculator.calculate(commitCounts);
                    log.debug("Team {}: calculated balance score = {}", participation.getName(), balanceScore);

                    // Calculate pairing score from repository commit history
                    TeamRepository teamRepo = teamRepositoryRepository.findByTeamParticipation(participation);
                    if (teamRepo != null) {
                        try {
                            TeamRepositoryDTO repo = buildTeamRepositoryDTO(participation, teamRepo, students);
                            List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> commitInfos = extractCommitInfo(repo);
                            
                            if (commitInfos.isEmpty()) {
                                log.warn("Team {}: no commit info extracted from repository, pairing score = 0", participation.getName());
                                pairingScore = 0.0;
                            } else {
                                pairingScore = pairingCalculator.calculate(commitInfos);
                                log.debug("Team {}: calculated pairing score = {} from {} commits", 
                                        participation.getName(), pairingScore, commitInfos.size());
                            }
                        } catch (Exception e) {
                            log.error("Team {}: error calculating pairing score: {}", participation.getName(), e.getMessage());
                            pairingScore = 0.0;
                        }
                    } else {
                        log.warn("Team {}: no repository found, pairing score = 0", participation.getName());
                        pairingScore = 0.0;
                    }

                    cqi = (balanceScore * 0.5) + (pairingScore * 0.5);
                    log.info("Team {}: balance={}, pairing={}, CQI={}", 
                            participation.getName(), balanceScore, pairingScore, cqi);

                    return new ClientResponseDTO(
                            tutor != null ? tutor.getName() : "Unassigned",
                            participation.getTeam(),
                            participation.getName(),
                            participation.getSubmissionCount(),
                            studentAnalysisDTOS,
                            balanceScore,
                            pairingScore,
                            cqi,
                            isSuspicious,
                            loadAnalyzedChunks(participation),
                            null); // Orphan commits are not persisted, only shown during live analysis
                })
                .toList();

        log.info("RequestService: Extracted {} team participation records from the database.", responseDTOs.size());
        return responseDTOs;
    }

    /**
     * Recalculates CQI for all teams in database using current formula.
     * Useful for testing formula changes without re-fetching repositories.
     *
     * @return Number of teams updated
     */
    @org.springframework.transaction.annotation.Transactional
    public int recalculateCQIForAllTeams() {
        log.info("Recalculating CQI for all teams in database");
        List<TeamParticipation> participations = teamParticipationRepository.findAll();
        int updated = 0;

        for (TeamParticipation participation : participations) {
            try {
                TeamRepository teamRepo = teamRepositoryRepository.findByTeamParticipation(participation);
                if (teamRepo == null || teamRepo.getLocalPath() == null) {
                    log.warn("No repository found for team {}, skipping", participation.getName());
                    continue;
                }

                // Eagerly load VCS logs
                List<VCSLog> vcsLogs = teamRepo.getVcsLogs();
                if (vcsLogs == null || vcsLogs.isEmpty()) {
                    log.warn("No VCS logs for team {}, skipping", participation.getName());
                    continue;
                }

                List<Student> students = studentRepository.findAllByTeam(participation);
                TeamRepositoryDTO repo = buildTeamRepositoryDTO(participation, teamRepo, students);

                Map<String, Integer> commitCounts = new HashMap<>();
                students.forEach(s -> commitCounts.put(s.getName(), s.getCommitCount()));
                double balanceScore = commitCounts.isEmpty() ? 0.0 : balanceCalculator.calculate(commitCounts);

                List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> commitInfos = extractCommitInfo(repo);
                double pairingScore = pairingCalculator.calculate(commitInfos);

                double cqi = (balanceScore * 0.5) + (pairingScore * 0.5);

                log.info("Team {}: balance={}, pairing={}, CQI={}",
                        participation.getName(), balanceScore, pairingScore, cqi);

                participation.setCqi(cqi);
                teamParticipationRepository.save(participation);
                updated++;
            } catch (Exception e) {
                log.error("Failed to recalculate CQI for team {}: {}", participation.getName(), e.getMessage());
            }
        }

        log.info("Recalculated CQI for {} teams", updated);
        return updated;
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

    private TeamRepositoryDTO buildTeamRepositoryDTO(TeamParticipation participation, TeamRepository teamRepo,
            List<Student> students) {
        List<VCSLog> vcsLogs = teamRepo.getVcsLogs();
        List<VCSLogDTO> vcsLogDTOs = vcsLogs == null ? List.of()
                : vcsLogs.stream()
                        .map(log -> new VCSLogDTO(log.getEmail(), "WRITE", log.getCommitHash()))
                        .toList();

        List<ParticipantDTO> studentDTOs = students.stream()
                .map(s -> new ParticipantDTO(s.getId(), s.getLogin(), s.getName(), s.getEmail()))
                .toList();

        TeamDTO teamDTO = new TeamDTO(
                participation.getTeam(),
                participation.getName(),
                participation.getShortName(),
                studentDTOs,
                null // owner
        );

        ParticipationDTO participationDTO = new ParticipationDTO(
                teamDTO,
                participation.getParticipation(),
                participation.getRepositoryUrl(),
                participation.getSubmissionCount()
        );

        return new TeamRepositoryDTO(
                participationDTO,
                vcsLogDTOs,
                teamRepo.getLocalPath(),
                teamRepo.getIsCloned(),
                teamRepo.getError()
        );
    }

    /**
     * Extracts commit information from git repository for pairing analysis.
     */
    private List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> extractCommitInfo(TeamRepositoryDTO repo) {
        List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> commitInfos = new ArrayList<>();
        String localPath = repo.localPath();

        if (localPath == null || !repo.isCloned()) {
            log.warn("Repository not cloned, cannot extract commit info");
            return commitInfos;
        }

        // Map commit hash to email from VCS logs
        Map<String, String> commitToEmail = new HashMap<>();
        repo.vcsLogs().forEach(log -> commitToEmail.put(log.commitHash(), log.email()));

        try {
            java.io.File gitDir = new java.io.File(localPath, ".git");
            try (org.eclipse.jgit.lib.Repository repository = new org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(gitDir)
                    .readEnvironment()
                    .build();
                 org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {

                org.eclipse.jgit.lib.ObjectId headId = repository.resolve("HEAD");
                if (headId == null) {
                    return commitInfos;
                }

                revWalk.markStart(revWalk.parseCommit(headId));
                for (org.eclipse.jgit.revwalk.RevCommit commit : revWalk) {
                    String commitHash = commit.getName();
                    String email = commitToEmail.getOrDefault(commitHash, commit.getAuthorIdent().getEmailAddress());
                    java.time.LocalDateTime timestamp = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(commit.getCommitTime()),
                            java.time.ZoneId.systemDefault());

                    // Get modified files
                    java.util.Set<String> modifiedFiles = new java.util.HashSet<>();
                    try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repository);
                        df.setDetectRenames(true);

                        List<org.eclipse.jgit.diff.DiffEntry> diffs;
                        if (commit.getParentCount() > 0) {
                            // Compare with parent for regular commits
                            org.eclipse.jgit.revwalk.RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                            diffs = df.scan(parent, commit);
                        } else {
                            // For root commits, compare with empty tree
                            org.eclipse.jgit.lib.ObjectId emptyTree = org.eclipse.jgit.lib.ObjectId.zeroId();
                            diffs = df.scan(emptyTree, commit.getTree().getId());
                        }

                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                            modifiedFiles.add(diff.getNewPath());
                        }
                    }

                    commitInfos.add(new de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo(
                            email, timestamp, modifiedFiles));
                }
            }
        } catch (Exception e) {
            log.error("Error extracting commit info from {}: {}", localPath, e.getMessage());
        }

        // Sort by timestamp (oldest first) for proper alternation analysis
        commitInfos.sort(java.util.Comparator.comparing(de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo::getTimestamp));
        return commitInfos;
    }

    /**
     * Diagnoses CQI calculation for a specific team to help debug zero scores.
     *
     * @param teamId The Artemis Team ID to diagnose
     * @return Diagnostic report including student data, commit counts, and score calculations
     */
    public String diagnoseCQIForTeam(Long teamId) {
        StringBuilder report = new StringBuilder();
        report.append("=== CQI Diagnostic Report for Team ID: ").append(teamId).append(" ===\n\n");

        try {
            // Find team by Artemis team ID
            java.util.List<TeamParticipation> participations = teamParticipationRepository.findAll()
                    .stream()
                    .filter(p -> teamId.equals(p.getTeam()))
                    .toList();

            if (participations.isEmpty()) {
                report.append("ERROR: Team with Artemis ID ").append(teamId).append(" not found in database.\n");
                return report.toString();
            }

            TeamParticipation participation = participations.get(0);
            report.append("Team Name: ").append(participation.getName()).append("\n");
            report.append("Artemis Team ID: ").append(participation.getTeam()).append("\n");
            report.append("Current CQI: ").append(participation.getCqi()).append("\n");
            report.append("Stored as Suspicious: ").append(participation.getIsSuspicious()).append("\n\n");

            // Get students and commit counts
            List<Student> students = studentRepository.findAllByTeam(participation);
            report.append("Students Found: ").append(students.size()).append("\n");

            Map<String, Integer> commitCounts = new HashMap<>();
            for (Student student : students) {
                report.append("  - ").append(student.getName())
                        .append(" (").append(student.getLogin()).append("): ")
                        .append(student.getCommitCount()).append(" commits")
                        .append(", Lines: +").append(student.getLinesAdded())
                        .append(" -").append(student.getLinesDeleted()).append("\n");

                commitCounts.put(student.getName(), student.getCommitCount());
            }

            // Calculate balance score
            report.append("\n--- Balance Score Calculation ---\n");
            if (commitCounts.isEmpty()) {
                report.append("ERROR: No commit counts found for any students!\n");
                report.append("Balance Score: 0.0 (no data)\n");
            } else {
                double balanceScore = balanceCalculator.calculate(commitCounts);
                report.append("Commits Map: ").append(commitCounts).append("\n");
                report.append("Balance Score: ").append(balanceScore).append("\n");
            }

            // Calculate pairing score
            report.append("\n--- Pairing Score Calculation ---\n");
            TeamRepository teamRepo = teamRepositoryRepository.findByTeamParticipation(participation);

            if (teamRepo == null) {
                report.append("ERROR: No repository record found for this team.\n");
                report.append("Pairing Score: 0.0 (no repo data)\n");
            } else {
                report.append("Repository Local Path: ").append(teamRepo.getLocalPath()).append("\n");
                report.append("Is Cloned: ").append(teamRepo.getIsCloned()).append("\n");

                if (teamRepo.getLocalPath() != null) {
                    java.io.File gitDir = new java.io.File(teamRepo.getLocalPath(), ".git");
                    report.append(".git Directory Exists: ").append(gitDir.exists()).append("\n");
                }

                // Try to extract commits
                try {
                    TeamRepositoryDTO repo = buildTeamRepositoryDTO(participation, teamRepo, students);
                    List<de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo> commitInfos = extractCommitInfo(repo);

                    report.append("Commits Extracted: ").append(commitInfos.size()).append("\n");

                    if (commitInfos.isEmpty()) {
                        report.append("WARNING: No commits extracted from repository.\n");
                        report.append("Pairing Score: 0.0 (no commits)\n");
                    } else {
                        double pairingScore = pairingCalculator.calculate(commitInfos);
                        report.append("Pairing Score: ").append(pairingScore).append("\n");

                        // Show commit distribution
                        java.util.Set<String> uniqueAuthors = commitInfos.stream()
                                .map(de.tum.cit.aet.analysis.service.cqi.PairingSignalsCalculator.CommitInfo::getAuthor)
                                .collect(java.util.stream.Collectors.toSet());
                        report.append("Unique Authors in Commits: ").append(uniqueAuthors.size()).append("\n");
                        for (String author : uniqueAuthors) {
                            long count = commitInfos.stream()
                                    .filter(c -> c.getAuthor().equals(author))
                                    .count();
                            report.append("  - ").append(author).append(": ").append(count).append(" commits\n");
                        }
                    }
                } catch (Exception e) {
                    report.append("ERROR extracting commits: ").append(e.getMessage()).append("\n");
                    report.append("Pairing Score: 0.0 (extraction failed)\n");
                }
            }

            report.append("\n=== End Report ===\n");

        } catch (Exception e) {
            report.append("FATAL ERROR: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        return report.toString();
    }
}
