package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.analysis.dto.FullCommitMappingResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.PreFilterResultDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.TeamRepository;
import de.tum.cit.aet.repositoryProcessing.domain.VCSLog;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes and clears pair programming metrics for team participations.
 */
@Service
@Slf4j
public class PairProgrammingMetricsService {

    private final TeamParticipationRepository teamParticipationRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;
    private final StudentRepository studentRepository;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final CommitPreFilterService commitPreFilterService;
    private final CQICalculatorService cqiCalculatorService;
    private final CommitChunkerService commitChunkerService;

    public PairProgrammingMetricsService(TeamParticipationRepository teamParticipationRepository,
                                         TeamRepositoryRepository teamRepositoryRepository,
                                         StudentRepository studentRepository,
                                         GitContributionAnalysisService gitContributionAnalysisService,
                                         CommitPreFilterService commitPreFilterService,
                                         CQICalculatorService cqiCalculatorService,
                                         CommitChunkerService commitChunkerService) {
        this.teamParticipationRepository = teamParticipationRepository;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.studentRepository = studentRepository;
        this.gitContributionAnalysisService = gitContributionAnalysisService;
        this.commitPreFilterService = commitPreFilterService;
        this.cqiCalculatorService = cqiCalculatorService;
        this.commitChunkerService = commitChunkerService;
    }

    /**
     * Recomputes pair programming scores for all two-person teams in the exercise
     * by re-analyzing their git repositories.
     *
     * @param exerciseId the exercise to recompute
     * @return number of teams whose scores were updated
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
     * Clears pair programming scores for all teams in the exercise.
     *
     * @param exerciseId the exercise to clear
     * @return number of teams whose scores were cleared
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

    private boolean recomputePairProgrammingForParticipation(TeamParticipation participation) {
        List<Student> students = studentRepository.findAllByTeam(participation);
        if (students.size() != 2) {
            return clearPairProgrammingFields(participation);
        }

        Optional<TeamRepository> teamRepositoryOptional = teamRepositoryRepository.findByTeamParticipation(participation);
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
        PreFilterResultDTO filterResult = commitPreFilterService.preFilter(allChunks);
        ComponentScoresDTO components = cqiCalculatorService.calculateGitOnlyComponents(
                filterResult.chunksToAnalyze(), students.size(), null, null, participation.getName());

        Double previousScore = participation.getCqiPairProgramming();
        String previousStatus = participation.getCqiPairProgrammingStatus();
        Double nextScore = components.pairProgramming();
        String nextStatus = components.pairProgrammingStatus();

        boolean changed = !Objects.equals(previousScore, nextScore)
                || !Objects.equals(previousStatus, nextStatus);
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

        FullCommitMappingResultDTO result = gitContributionAnalysisService.buildFullCommitMap(localPath, vcsLogDTOs, participantDTOs, Map.of(), null);
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
}
