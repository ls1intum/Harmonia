package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotals;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.ai.service.ContributionFairnessService.FairnessReportWithUsage;
import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.dto.RepositoryAnalysisResultDTO;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for email-mapping application logic in {@link RequestService}.
 */
@ExtendWith(MockitoExtension.class)
class RequestServiceApplyMappingsTest {

    @Mock private AnalyzedChunkRepository analyzedChunkRepository;
    @Mock private ExerciseEmailMappingRepository emailMappingRepository;
    @Mock private TeamParticipationRepository teamParticipationRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ContributionFairnessService fairnessService;
    @Mock private ExerciseTemplateAuthorRepository templateAuthorRepository;
    @Mock private GitContributionAnalysisService gitContributionAnalysisService;

    @Captor private ArgumentCaptor<List<AnalyzedChunk>> chunksCaptor;

    private RequestService service;

    @BeforeEach
    void setUp() {
        service = new RequestService(
                null, null, null, fairnessService, null,
                null, teamParticipationRepository, null, studentRepository,
                analyzedChunkRepository, templateAuthorRepository, emailMappingRepository,
                gitContributionAnalysisService, null, null, null, null);
    }

    // ── Unit tests for applyExistingEmailMappings ──────────────────────────

    @Test
    void applyMappings_matchingExternalChunk_becomesNonExternalWithCorrectName() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team A");

        ExerciseEmailMapping mapping = new ExerciseEmailMapping(42L, "orphan@gmail.com", 1L, "Alice");
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of(mapping));

        AnalyzedChunk externalChunk = new AnalyzedChunk();
        externalChunk.setAuthorEmail("orphan@gmail.com");
        externalChunk.setAuthorName("Unknown");
        externalChunk.setIsExternalContributor(true);

        AnalyzedChunk normalChunk = new AnalyzedChunk();
        normalChunk.setAuthorEmail("student@uni.de");
        normalChunk.setAuthorName("Bob");
        normalChunk.setIsExternalContributor(false);

        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(List.of(externalChunk, normalChunk));

        service.applyExistingEmailMappings(participation, 42L);

        verify(analyzedChunkRepository).saveAll(any());
        assertFalse(externalChunk.getIsExternalContributor());
        assertEquals("Alice", externalChunk.getAuthorName());
        // Normal chunk must be untouched
        assertFalse(normalChunk.getIsExternalContributor());
        assertEquals("Bob", normalChunk.getAuthorName());
    }

    @Test
    void applyMappings_emailMatchIsCaseInsensitive() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team B");

        ExerciseEmailMapping mapping = new ExerciseEmailMapping(42L, "Orphan@Gmail.COM", 1L, "Alice");
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of(mapping));

        AnalyzedChunk chunk = new AnalyzedChunk();
        chunk.setAuthorEmail("orphan@gmail.com");
        chunk.setIsExternalContributor(true);

        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(List.of(chunk));

        service.applyExistingEmailMappings(participation, 42L);

        assertFalse(chunk.getIsExternalContributor());
        assertEquals("Alice", chunk.getAuthorName());
    }

    @Test
    void applyMappings_nonExternalChunkWithMatchingEmail_isNotOverwritten() {
        TeamParticipation participation = new TeamParticipation();
        participation.setName("Team C");

        ExerciseEmailMapping mapping = new ExerciseEmailMapping(42L, "student@uni.de", 1L, "Alice");
        when(emailMappingRepository.findAllByExerciseId(42L)).thenReturn(List.of(mapping));

        AnalyzedChunk chunk = new AnalyzedChunk();
        chunk.setAuthorEmail("student@uni.de");
        chunk.setAuthorName("Bob");
        chunk.setIsExternalContributor(false);

        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(List.of(chunk));

        service.applyExistingEmailMappings(participation, 42L);

        verify(analyzedChunkRepository, never()).saveAll(any());
        assertEquals("Bob", chunk.getAuthorName());
    }

    // ── Integration test: saveAIAnalysisResult calls applyExistingEmailMappings ──

    @Test
    void saveAIAnalysisResult_withExistingMapping_appliesMappingToSavedChunks() {
        Long exerciseId = 42L;

        // Build a minimal TeamRepositoryDTO
        TeamDTO teamDTO = new TeamDTO(1L, "Team Alpha", "alpha",
                List.of(new ParticipantDTO(100L, "alice", "Alice", "alice@uni.de")),
                null);
        ParticipationDTO participationDTO = new ParticipationDTO(teamDTO, 10L, "https://repo", 5);
        TeamRepositoryDTO repo = new TeamRepositoryDTO(participationDTO, List.of(), "/tmp/repo", true, null);

        // TeamParticipation must exist in DB
        TeamParticipation teamParticipation = new TeamParticipation();
        teamParticipation.setName("Team Alpha");
        teamParticipation.setExerciseId(exerciseId);
        when(teamParticipationRepository.findByParticipation(10L))
                .thenReturn(Optional.of(teamParticipation));
        when(teamParticipationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Students
        Student student = new Student(100L, "alice", "Alice", "alice@uni.de", teamParticipation, 20, 500, 50, 550);
        when(studentRepository.findAllByTeam(teamParticipation)).thenReturn(List.of(student));

        // No template author
        when(templateAuthorRepository.findByExerciseId(exerciseId)).thenReturn(Optional.empty());

        // Orphan detection returns no orphans
        when(gitContributionAnalysisService.analyzeRepositoryWithOrphans(eq(repo), isNull()))
                .thenReturn(new RepositoryAnalysisResultDTO(Map.of(), List.of()));

        // Fairness service returns one external chunk
        AnalyzedChunkDTO externalChunkDTO = new AnalyzedChunkDTO(
                "chunk-1", "orphan@gmail.com", "Unknown", "feature",
                5.0, 3.0, 4.0, 0.9, "Some reasoning",
                List.of("abc123"), List.of("add feature"), LocalDateTime.now(),
                100, false, 0, 1, false, null, true, null);

        FairnessReportDTO fairnessReport = new FairnessReportDTO(
                "Team Alpha", 75.0, Map.of(), Map.of(),
                List.of(), false, List.of(), null,
                List.of(externalChunkDTO), null);
        when(fairnessService.analyzeFairnessWithUsage(eq(repo), isNull()))
                .thenReturn(new FairnessReportWithUsage(fairnessReport, LlmTokenTotals.empty()));

        // saveAll for chunks: capture and return the same list
        when(analyzedChunkRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // An existing email mapping was created before clear+re-run
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(exerciseId, "orphan@gmail.com", 100L, "Alice");
        when(emailMappingRepository.findAllByExerciseId(exerciseId)).thenReturn(List.of(mapping));

        // findByParticipation returns the chunks that were just saved
        // (simulates DB round-trip)
        when(analyzedChunkRepository.findByParticipation(teamParticipation)).thenAnswer(inv -> {
            AnalyzedChunk chunk = new AnalyzedChunk();
            chunk.setAuthorEmail("orphan@gmail.com");
            chunk.setAuthorName("Unknown");
            chunk.setIsExternalContributor(true);
            return List.of(chunk);
        });

        // Act
        service.saveAIAnalysisResult(repo, exerciseId);

        // Assert: saveAll was called twice — once for saveAnalyzedChunks,
        // once for applyExistingEmailMappings
        verify(analyzedChunkRepository, times(2)).saveAll(chunksCaptor.capture());

        // The second saveAll should contain the updated chunk
        List<AnalyzedChunk> appliedChunks = chunksCaptor.getAllValues().get(1);
        assertEquals(1, appliedChunks.size());
        AnalyzedChunk applied = appliedChunks.get(0);
        assertFalse(applied.getIsExternalContributor(),
                "Chunk should no longer be marked as external after mapping is applied");
        assertEquals("Alice", applied.getAuthorName(),
                "Chunk author name should be updated to the mapped student name");
    }
}
