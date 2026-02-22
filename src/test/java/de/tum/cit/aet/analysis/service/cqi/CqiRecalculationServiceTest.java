package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentWeightsDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqiRecalculationServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private ExerciseEmailMappingRepository emailMappingRepository;
    @Mock private TeamParticipationRepository teamParticipationRepository;
    @Mock private CQICalculatorService cqiCalculatorService;

    private CqiRecalculationService service;

    @BeforeEach
    void setUp() {
        service = new CqiRecalculationService(
                studentRepository, emailMappingRepository,
                teamParticipationRepository, cqiCalculatorService);
    }

    @Test
    void recalculateFromChunks_updatesOrphanCountFromExternalChunks() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team A");
        participation.setExerciseId(42L);
        participation.setOrphanCommitCount(5); // stale pre-mapping value

        Student alice = new Student(1L, "alice", "Alice", "alice@uni.de", participation, 10, 300, 100, 400);
        Student bob = new Student(2L, "bob", "Bob", "bob@uni.de", participation, 10, 300, 100, 400);
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice, bob));
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of());

        CQIResultDTO cqiResult = new CQIResultDTO(
                80.0,
                new ComponentScoresDTO(90.0, 85.0, 37.0, 50.0, null, null),
                new ComponentWeightsDTO(0.55, 0.25, 0.05, 0.15),
                List.of(), 80.0, 1.0, FilterSummaryDTO.empty());
        when(cqiCalculatorService.calculate(anyList(), eq(2), any(), any(), any(), anyString()))
                .thenReturn(cqiResult);

        // 2 non-external chunks, 1 external chunk
        AnalyzedChunk teamChunk1 = makeChunk("alice@uni.de", false);
        AnalyzedChunk teamChunk2 = makeChunk("bob@uni.de", false);
        AnalyzedChunk externalChunk = makeChunk("orphan@gmail.com", true);

        service.recalculateFromChunks(participation, List.of(teamChunk1, teamChunk2, externalChunk));

        // Orphan count should be 1 (the external chunk), not the stale 5
        assertEquals(1, participation.getOrphanCommitCount());
        verify(teamParticipationRepository).save(participation);
    }

    @Test
    void recalculateFromChunks_setsZeroOrphanCountWhenNoExternalChunks() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team B");
        participation.setExerciseId(42L);
        participation.setOrphanCommitCount(3); // stale value

        Student alice = new Student(1L, "alice", "Alice", "alice@uni.de", participation, 10, 300, 100, 400);
        Student bob = new Student(2L, "bob", "Bob", "bob@uni.de", participation, 10, 300, 100, 400);
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice, bob));
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of());

        CQIResultDTO cqiResult = new CQIResultDTO(
                80.0,
                new ComponentScoresDTO(90.0, 85.0, 37.0, 50.0, null, null),
                new ComponentWeightsDTO(0.55, 0.25, 0.05, 0.15),
                List.of(), 80.0, 1.0, FilterSummaryDTO.empty());
        when(cqiCalculatorService.calculate(anyList(), eq(2), any(), any(), any(), anyString()))
                .thenReturn(cqiResult);

        // All chunks are non-external (mappings were applied)
        AnalyzedChunk chunk1 = makeChunk("alice@uni.de", false);
        AnalyzedChunk chunk2 = makeChunk("bob@uni.de", false);

        service.recalculateFromChunks(participation, List.of(chunk1, chunk2));

        assertEquals(0, participation.getOrphanCommitCount());
    }

    @Test
    void recalculateFromChunks_updatesCqiComponentScores() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team C");
        participation.setExerciseId(42L);
        // Stale pre-mapping values
        participation.setCqi(72.0);
        participation.setCqiTemporalSpread(69.0);

        Student alice = new Student(1L, "alice", "Alice", "alice@uni.de", participation, 10, 300, 100, 400);
        Student bob = new Student(2L, "bob", "Bob", "bob@uni.de", participation, 10, 300, 100, 400);
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice, bob));
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of());

        // After remapping, CQI calculator returns corrected temporal spread of 37
        CQIResultDTO cqiResult = new CQIResultDTO(
                85.0,
                new ComponentScoresDTO(98.0, 97.0, 37.0, 50.0, null, null),
                new ComponentWeightsDTO(0.55, 0.25, 0.05, 0.15),
                List.of(), 85.0, 1.0, FilterSummaryDTO.empty());
        when(cqiCalculatorService.calculate(anyList(), eq(2), any(), any(), any(), anyString()))
                .thenReturn(cqiResult);

        AnalyzedChunk chunk = makeChunk("alice@uni.de", false);

        service.recalculateFromChunks(participation, List.of(chunk));

        // Temporal spread should be updated to 37 (not stale 69)
        assertEquals(37.0, participation.getCqiTemporalSpread());
        assertEquals(98.0, participation.getCqiEffortBalance());
        assertEquals(97.0, participation.getCqiLocBalance());
    }

    @Test
    void recalculateFromChunks_usesEmailMappingsForAuthorResolution() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team D");
        participation.setExerciseId(42L);

        Student alice = new Student(1L, "alice", "Alice", "alice@uni.de", participation, 10, 300, 100, 400);
        Student bob = new Student(2L, "bob", "Bob", "bob@uni.de", participation, 10, 300, 100, 400);
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice, bob));

        // Email mapping maps orphan email to Alice
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(42L, "alice-personal@gmail.com", 1L, "Alice");
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of(mapping));

        CQIResultDTO cqiResult = new CQIResultDTO(
                80.0,
                new ComponentScoresDTO(90.0, 85.0, 60.0, 50.0, null, null),
                new ComponentWeightsDTO(0.55, 0.25, 0.05, 0.15),
                List.of(), 80.0, 1.0, FilterSummaryDTO.empty());
        when(cqiCalculatorService.calculate(anyList(), eq(2), any(), any(), any(), anyString()))
                .thenReturn(cqiResult);

        // Chunk with mapped email (non-external after mapping was applied)
        AnalyzedChunk chunk = makeChunk("alice-personal@gmail.com", false);

        service.recalculateFromChunks(participation, List.of(chunk));

        // Should complete without error and save
        verify(teamParticipationRepository).save(participation);
        assertNotNull(participation.getCqi());
    }

    @Test
    void recalculateFromChunks_emptyChunksSetsZeroCqi() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team E");
        participation.setExerciseId(42L);
        participation.setCqi(85.0); // stale value

        Student alice = new Student(1L, "alice", "Alice", "alice@uni.de", participation, 10, 300, 100, 400);
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice));

        // All chunks are external → no team chunks
        AnalyzedChunk externalChunk = makeChunk("orphan@gmail.com", true);

        service.recalculateFromChunks(participation, List.of(externalChunk));

        assertEquals(0.0, participation.getCqi());
        assertEquals(1, participation.getOrphanCommitCount());
    }

    private AnalyzedChunk makeChunk(String email, boolean external) {
        AnalyzedChunk chunk = new AnalyzedChunk();
        chunk.setAuthorEmail(email);
        chunk.setIsExternalContributor(external);
        chunk.setCommitShas("abc123");
        chunk.setClassification("FEATURE");
        chunk.setEffortScore(5.0);
        chunk.setComplexity(3.0);
        chunk.setNovelty(4.0);
        chunk.setConfidence(0.9);
        chunk.setTimestamp(LocalDateTime.of(2025, 6, 15, 10, 0));
        chunk.setLinesChanged(50);
        return chunk;
    }
}
