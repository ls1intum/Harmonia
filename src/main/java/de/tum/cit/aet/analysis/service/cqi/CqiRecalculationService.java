package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentWeightsDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.ai.dto.*;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Recalculates CQI from already-persisted AnalyzedChunk data.
 * Shared by {@code EmailMappingResource} (manual assign/unassign)
 * and {@code RequestService} (auto-apply after re-analysis).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CqiRecalculationService {

    private final StudentRepository studentRepository;
    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final CQICalculatorService cqiCalculatorService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Recalculates CQI from already-persisted AnalyzedChunk data.
     * Only non-external chunks are included in the CQI calculation.
     * Also updates the orphan commit count on the participation.
     *
     * @param participation the team participation to update
     * @param allChunks     all analyzed chunks (including external ones)
     */
    public void recalculateFromChunks(TeamParticipation participation,
            List<AnalyzedChunk> allChunks) {

        List<Student> students = studentRepository.findAllByTeam(participation);
        int teamSize = students.size();

        // Collect non-external chunks for CQI
        List<AnalyzedChunk> teamChunks = allChunks.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsExternalContributor()))
                .toList();

        // Count remaining external chunks as orphans
        long orphanCount = allChunks.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsExternalContributor()))
                .count();
        participation.setOrphanCommitCount((int) orphanCount);

        if (teamChunks.isEmpty() || teamSize <= 1) {
            participation.setCqi(0.0);
            teamParticipationRepository.save(participation);
            return;
        }

        // Build email -> studentId map from students + mappings
        Map<String, Long> emailToStudentId = new HashMap<>();
        for (Student s : students) {
            if (s.getEmail() != null && s.getId() != null) {
                emailToStudentId.put(s.getEmail().toLowerCase(Locale.ROOT), s.getId());
            }
        }
        List<ExerciseEmailMapping> mappings = emailMappingRepository
                .findAllByExerciseId(participation.getExerciseId());
        for (ExerciseEmailMapping m : mappings) {
            emailToStudentId.put(m.getGitEmail().toLowerCase(Locale.ROOT), m.getStudentId());
        }

        // Reconstruct RatedChunks from DB data
        List<CQICalculatorService.RatedChunk> ratedChunks = teamChunks.stream()
                .map(chunk -> {
                    String chunkEmail = chunk.getAuthorEmail() != null
                            ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : "";
                    Long authorId = emailToStudentId.getOrDefault(chunkEmail, -1L);

                    CommitChunkDTO chunkDTO = CommitChunkDTO.single(
                            chunk.getCommitShas() != null ? chunk.getCommitShas().split(",")[0] : "",
                            authorId,
                            chunk.getAuthorEmail(),
                            "", // commit message not needed for CQI
                            chunk.getTimestamp(),
                            List.of(), // files not needed
                            "", // diff not needed
                            chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0,
                            0);

                    CommitLabel label;
                    try {
                        label = CommitLabel.valueOf(chunk.getClassification());
                    } catch (Exception e) {
                        label = CommitLabel.TRIVIAL;
                    }

                    EffortRatingDTO rating = new EffortRatingDTO(
                            chunk.getEffortScore() != null ? chunk.getEffortScore() : 0.0,
                            chunk.getComplexity() != null ? chunk.getComplexity() : 0.0,
                            chunk.getNovelty() != null ? chunk.getNovelty() : 0.0,
                            label,
                            chunk.getConfidence() != null ? chunk.getConfidence() : 0.0,
                            chunk.getReasoning(),
                            Boolean.TRUE.equals(chunk.getIsError()),
                            chunk.getErrorMessage());

                    return new CQICalculatorService.RatedChunk(chunkDTO, rating);
                })
                .toList();

        // Calculate date range
        LocalDateTime projectStart = teamChunks.stream()
                .map(AnalyzedChunk::getTimestamp)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusDays(30));

        LocalDateTime projectEnd = teamChunks.stream()
                .map(AnalyzedChunk::getTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        CQIResultDTO cqiResult = cqiCalculatorService.calculate(
                ratedChunks, teamSize, projectStart, projectEnd,
                FilterSummaryDTO.empty(), participation.getName());

        // Preserve ownership spread from original analysis because we don't
        // have the file list for recalculation (chunks are rebuilt without files).
        // Recompute the CQI using the original ownership so the final score is
        // consistent with the displayed component scores.
        Double originalOwnership = participation.getCqiOwnershipSpread();
        double finalCqi = cqiResult.cqi();
        double finalBaseScore = cqiResult.baseScore();

        if (originalOwnership != null && cqiResult.components() != null) {
            ComponentWeightsDTO weights = cqiCalculatorService.buildWeightsDTO();
            finalBaseScore = cqiResult.components().weightedSum(
                    weights.effortBalance(), weights.locBalance(),
                    weights.temporalSpread(), weights.ownershipSpread())
                    // Replace the incorrect ownership contribution with the original
                    - weights.ownershipSpread() * cqiResult.components().ownershipSpread()
                    + weights.ownershipSpread() * originalOwnership;
            finalCqi = Math.max(0, Math.min(100,
                    finalBaseScore * cqiResult.penaltyMultiplier()));
        }

        participation.setCqi(finalCqi);
        if (cqiResult.components() != null) {
            participation.setCqiEffortBalance(cqiResult.components().effortBalance());
            participation.setCqiLocBalance(cqiResult.components().locBalance());
            participation.setCqiTemporalSpread(cqiResult.components().temporalSpread());
            if (originalOwnership != null) {
                participation.setCqiOwnershipSpread(originalOwnership);
            }
            participation.setCqiBaseScore(finalBaseScore);
            participation.setCqiPenaltyMultiplier(cqiResult.penaltyMultiplier());
            if (cqiResult.penalties() != null) {
                participation.setCqiPenalties(serializePenalties(cqiResult.penalties()));
            }
        }
        teamParticipationRepository.save(participation);

        log.info("Recalculated CQI for team {}: {} (from {} team chunks, {} orphan)",
                participation.getName(), finalCqi, teamChunks.size(), orphanCount);
    }

    /**
     * Distributes a linesChanged delta into linesAdded/linesDeleted
     * using the student's existing ratio.
     *
     * @param student    the student to update
     * @param deltaLines total line delta to distribute
     * @param add        true to add, false to subtract
     */
    public static void applyLinesSplit(Student student, int deltaLines, boolean add) {
        int oldAdded = student.getLinesAdded() != null ? student.getLinesAdded() : 0;
        int oldDeleted = student.getLinesDeleted() != null ? student.getLinesDeleted() : 0;
        int oldTotal = oldAdded + oldDeleted;

        int deltaAdded;
        int deltaDeleted;
        if (oldTotal > 0) {
            deltaAdded = (int) Math.round(deltaLines * ((double) oldAdded / oldTotal));
            deltaDeleted = deltaLines - deltaAdded;
        } else {
            deltaAdded = deltaLines;
            deltaDeleted = 0;
        }

        if (add) {
            student.setLinesAdded(oldAdded + deltaAdded);
            student.setLinesDeleted(oldDeleted + deltaDeleted);
        } else {
            student.setLinesAdded(Math.max(0, oldAdded - deltaAdded));
            student.setLinesDeleted(Math.max(0, oldDeleted - deltaDeleted));
        }
    }

    String serializePenalties(List<?> penalties) {
        try {
            return OBJECT_MAPPER.writeValueAsString(penalties);
        } catch (Exception e) {
            return "[]";
        }
    }
}
