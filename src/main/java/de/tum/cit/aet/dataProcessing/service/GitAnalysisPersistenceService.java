package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.analysis.service.cqi.AttendanceDataProvider;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persists Phase-2 (git analysis) results and handles team failure detection.
 */
@Service
@Slf4j
public class GitAnalysisPersistenceService {

    private final AnalysisStateService analysisStateService;
    private final CQICalculatorService cqiCalculatorService;
    private final ExerciseDataCleanupService cleanupService;
    private final CqiPersistenceHelper cqiPersistenceHelper;
    private final AttendanceDataProvider pairProgrammingService;
    private final AnalysisQueryService queryService;

    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;

    public GitAnalysisPersistenceService(
            AnalysisStateService analysisStateService,
            CQICalculatorService cqiCalculatorService,
            ExerciseDataCleanupService cleanupService,
            CqiPersistenceHelper cqiPersistenceHelper,
            AttendanceDataProvider pairProgrammingService,
            AnalysisQueryService queryService,
            TeamParticipationRepository teamParticipationRepository,
            StudentRepository studentRepository,
            TeamRepositoryRepository teamRepositoryRepository) {
        this.analysisStateService = analysisStateService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.cleanupService = cleanupService;
        this.cqiPersistenceHelper = cqiPersistenceHelper;
        this.pairProgrammingService = pairProgrammingService;
        this.queryService = queryService;
        this.teamParticipationRepository = teamParticipationRepository;
        this.studentRepository = studentRepository;
        this.teamRepositoryRepository = teamRepositoryRepository;
    }

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
        cqiPersistenceHelper.persistTeamTokenTotals(teamParticipation, LlmTokenTotalsDTO.empty());
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
                    team.id(), team.name(), participation.submissionCount(),
                    studentDtos, 0.0, false, TeamAnalysisStatus.DONE,
                    null, null, null, null, 0, true);
        }

        CQIResultDTO gitCqiDetails = cqiPersistenceHelper.calculateGitOnlyCqi(repo, teamParticipation, team, students);

        CQIResultDTO finalDetails = gitCqiDetails;
        if (mode == AnalysisMode.SIMPLE && gitCqiDetails != null) {
            finalDetails = cqiCalculatorService.renormalizeWithoutEffort(gitCqiDetails);
        }

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                team.id(), team.name(), participation.submissionCount(),
                studentDtos,
                null,
                null,
                TeamAnalysisStatus.GIT_DONE,
                finalDetails,
                null,
                null,
                queryService.readTeamTokenTotals(teamParticipation),
                null,
                null);
    }

    /**
     * Checks whether a team should be skipped during the AI or simple analysis phase.
     *
     * @param tp the team participation to check
     * @return {@code true} if the team is marked as failed and should be skipped
     */
    public boolean shouldSkipTeam(TeamParticipation tp) {
        return shouldSkipAnalysis(tp);
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

    private boolean shouldSkipAnalysis(TeamParticipation tp) {
        return Boolean.TRUE.equals(tp.getIsFailed());
    }
}
