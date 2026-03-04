package de.tum.cit.aet.pairProgramming.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.service.CommitChunkerService;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.PreFilterResultDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.dto.FullCommitMappingResultDTO;
import de.tum.cit.aet.pairProgramming.enums.PairProgrammingStatus;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.TeamRepository;
import de.tum.cit.aet.repositoryProcessing.domain.VCSLog;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles pair programming recomputation and persistence of scores/statuses.
 * Extracted from {@code RequestService} to break a circular dependency with
 * {@link PairProgrammingService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PairProgrammingRecomputeService {

    private final PairProgrammingService pairProgrammingService;
    private final PairProgrammingRecomputeTracker recomputeTracker;
    private final CQICalculatorService cqiCalculatorService;
    private final CommitPreFilterService commitPreFilterService;
    private final CommitChunkerService commitChunkerService;
    private final GitContributionAnalysisService gitContributionAnalysisService;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;
    private final StudentRepository studentRepository;

    /**
     * Recomputes pair programming metrics for an exercise asynchronously.
     *
     * @param exerciseId the exercise ID
     */
    @Async("attendanceTaskExecutor")
    public void recomputeForExerciseAsync(Long exerciseId) {
        recomputeTracker.startRecompute(exerciseId);
        try {
            int updatedTeams = recomputePairProgrammingForExercise(exerciseId);
            log.info("Async recomputed pair programming metrics for {} teams (exercise={})",
                    updatedTeams, exerciseId);
        } catch (Exception e) {
            log.error("Async pair programming recomputation failed for exercise {}", exerciseId, e);
        } finally {
            recomputeTracker.endRecompute(exerciseId);
        }
    }

    /**
     * Recomputes pair programming metrics (status + score) for every team in an exercise.
     * <p>
     * Runs in two passes so the lightweight status (PASS / FAIL / NOT_FOUND / WARNING)
     * is visible in the webapp immediately, before the slower score computation begins.
     *
     * @param exerciseId the exercise ID
     * @return number of teams updated (status or score change)
     */
    public int recomputePairProgrammingForExercise(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return 0;
        }

        // Fast pass: persist status for all teams immediately
        for (TeamParticipation participation : participations) {
            PairProgrammingStatus status = pairProgrammingService.getPairProgrammingStatus(
                    participation.getName(), participation.getShortName());
            String statusValue = status != null ? status.name() : null;
            if (!Objects.equals(participation.getCqiPairProgrammingStatus(), statusValue)) {
                participation.setCqiPairProgrammingStatus(statusValue);
                teamParticipationRepository.save(participation);
            }
        }

        // Slow pass: compute and persist scores
        int updated = 0;
        for (TeamParticipation participation : participations) {
            if (recomputeOneParticipationInNewTx(participation.getTeamParticipationId())) {
                updated++;
            }
        }

        log.info("Recomputed pair programming metrics for {}/{} teams in exerciseId={}",
                updated, participations.size(), exerciseId);
        return updated;
    }

    /**
     * Recomputes pair programming for a single participation in its own transaction.
     * Commits immediately so the result is visible and durable without waiting for
     * the full exercise recompute to finish.
     *
     * @param teamParticipationId the participation ID
     * @return true if the participation was updated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recomputeOneParticipationInNewTx(UUID teamParticipationId) {
        return teamParticipationRepository.findById(teamParticipationId)
                .map(this::recomputePairProgrammingForParticipation)
                .orElse(false);
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

    // ---- Utility methods ----

    /**
     * Parses a persisted pair programming status string back into the enum.
     *
     * @param status the status string (may be null or blank)
     * @return the parsed status, or {@code null} if unrecognized
     */
    public static PairProgrammingStatus parsePairProgrammingStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PairProgrammingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown pair programming status '{}' in persisted data, ignoring", status);
            return null;
        }
    }

    /**
     * Normalizes persisted pair-programming score values.
     * Score should remain null for NOT_FOUND and default to 0 for other statuses
     * when a numeric score is absent.
     *
     * @param score  the persisted score (may be null)
     * @param status the pair programming status
     * @return normalized score
     */
    public static Double normalizePairProgrammingScore(Double score, PairProgrammingStatus status) {
        if (score != null) {
            return score;
        }
        if (status == null || status == PairProgrammingStatus.NOT_FOUND) {
            return null;
        }
        return 0.0;
    }

    // ---- Private helpers ----

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
                filterResult.chunksToAnalyze(), students.size(), null, null, participation.getName(), participation.getShortName());

        PairProgrammingStatus nextStatus = components.pairProgrammingStatus();
        String nextStatusValue = nextStatus != null ? nextStatus.name() : null;

        // Persist status as soon as it is computed, before the score (so UI can show status even if score lags)
        boolean statusChanged = !Objects.equals(participation.getCqiPairProgrammingStatus(), nextStatusValue);
        if (statusChanged) {
            participation.setCqiPairProgrammingStatus(nextStatusValue);
            teamParticipationRepository.save(participation);
        }

        Double previousScore = participation.getCqiPairProgramming();
        Double nextScore = normalizePairProgrammingScore(components.pairProgramming(), nextStatus);
        boolean scoreChanged = !Objects.equals(previousScore, nextScore);
        if (!scoreChanged) {
            return statusChanged;
        }

        participation.setCqiPairProgramming(nextScore);
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
