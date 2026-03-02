package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.cqi.CqiRecalculationService;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailMappingServiceTest {

    @Mock
    private ExerciseEmailMappingRepository emailMappingRepository;

    @Mock
    private ExerciseTemplateAuthorRepository templateAuthorRepository;

    @Mock
    private AnalyzedChunkRepository analyzedChunkRepository;

    @Mock
    private TeamParticipationRepository teamParticipationRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CqiRecalculationService cqiRecalculationService;

    private EmailMappingService service;

    private static final Long EXERCISE_ID = 42L;
    private static final Long TEAM_ID = 10L;
    private static final UUID PARTICIPATION_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailMappingService(
                emailMappingRepository,
                templateAuthorRepository,
                analyzedChunkRepository,
                teamParticipationRepository,
                studentRepository,
                cqiRecalculationService);
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private TeamParticipation makeParticipation() {
        TeamParticipation p = new TeamParticipation(1L, TEAM_ID, null, "Team-1", "t1",
                "https://repo.example.com/t1", 5);
        p.setTeamParticipationId(PARTICIPATION_UUID);
        p.setExerciseId(EXERCISE_ID);
        return p;
    }

    private Student makeStudent(Long artemisId, String name, String email, TeamParticipation tp) {
        return new Student(artemisId, name.toLowerCase(), name, email, tp, 3, 100, 20, 120);
    }

    private AnalyzedChunk makeChunk(TeamParticipation p, String email, boolean external) {
        AnalyzedChunk c = new AnalyzedChunk();
        c.setParticipation(p);
        c.setChunkIdentifier("chunk-1");
        c.setAuthorEmail(email);
        c.setAuthorName("Author");
        c.setClassification("FEATURE");
        c.setEffortScore(5.0);
        c.setComplexity(3.0);
        c.setNovelty(4.0);
        c.setConfidence(0.9);
        c.setReasoning("reason");
        c.setCommitShas("abc123");
        c.setCommitMessages("[\"msg\"]");
        c.setTimestamp(LocalDateTime.now());
        c.setLinesChanged(50);
        c.setIsBundled(false);
        c.setChunkIndex(0);
        c.setTotalChunks(1);
        c.setIsError(false);
        c.setIsExternalContributor(external);
        return c;
    }

    // ================================================================
    //  createMapping tests
    // ================================================================

    @Test
    void createMapping_resolvesStudentIdByName() {
        TeamParticipation participation = makeParticipation();
        Student alice = makeStudent(999L, "Alice", "alice@uni.de", participation);
        Student bob = makeStudent(888L, "Bob", "bob@uni.de", participation);
        AnalyzedChunk chunk = makeChunk(participation, "orphan@gmail.com", true);

        when(teamParticipationRepository.findByExerciseIdAndTeam(EXERCISE_ID, TEAM_ID))
                .thenReturn(Optional.of(participation));
        when(studentRepository.findAllByTeam(participation))
                .thenReturn(List.of(alice, bob));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));

        EmailMappingService.CreateEmailMappingRequest request = new EmailMappingService.CreateEmailMappingRequest(
                "orphan@gmail.com", 0L, "Alice", TEAM_ID);

        service.createMapping(EXERCISE_ID, request);

        // Verify the mapping was saved with the real Artemis ID (999), not the placeholder (0)
        ArgumentCaptor<ExerciseEmailMapping> captor = ArgumentCaptor.forClass(ExerciseEmailMapping.class);
        verify(emailMappingRepository).save(captor.capture());
        assertEquals(999L, captor.getValue().getStudentId());
    }

    @Test
    void createMapping_marksExternalChunksAsNonExternal() {
        TeamParticipation participation = makeParticipation();
        Student alice = makeStudent(999L, "Alice", "alice@uni.de", participation);
        Student bob = makeStudent(888L, "Bob", "bob@uni.de", participation);
        AnalyzedChunk externalChunk = makeChunk(participation, "orphan@gmail.com", true);

        when(teamParticipationRepository.findByExerciseIdAndTeam(EXERCISE_ID, TEAM_ID))
                .thenReturn(Optional.of(participation));
        when(studentRepository.findAllByTeam(participation))
                .thenReturn(List.of(alice, bob));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(externalChunk)));

        EmailMappingService.CreateEmailMappingRequest request = new EmailMappingService.CreateEmailMappingRequest(
                "orphan@gmail.com", 0L, "Alice", TEAM_ID);

        service.createMapping(EXERCISE_ID, request);

        assertFalse(externalChunk.getIsExternalContributor());
        assertEquals("Alice", externalChunk.getAuthorName());
    }

    @Test
    void createMapping_recalculatesCqi() {
        TeamParticipation participation = makeParticipation();
        Student alice = makeStudent(999L, "Alice", "alice@uni.de", participation);
        Student bob = makeStudent(888L, "Bob", "bob@uni.de", participation);
        AnalyzedChunk chunk = makeChunk(participation, "orphan@gmail.com", true);

        when(teamParticipationRepository.findByExerciseIdAndTeam(EXERCISE_ID, TEAM_ID))
                .thenReturn(Optional.of(participation));
        when(studentRepository.findAllByTeam(participation))
                .thenReturn(List.of(alice, bob));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));

        EmailMappingService.CreateEmailMappingRequest request = new EmailMappingService.CreateEmailMappingRequest(
                "orphan@gmail.com", 0L, "Alice", TEAM_ID);

        service.createMapping(EXERCISE_ID, request);

        // CQI recalculation is delegated to the service
        verify(cqiRecalculationService).recalculateFromChunks(eq(participation), anyList());
    }

    @Test
    void createMapping_throwsIfParticipationNotFound() {
        when(teamParticipationRepository.findByExerciseIdAndTeam(EXERCISE_ID, TEAM_ID))
                .thenReturn(Optional.empty());

        EmailMappingService.CreateEmailMappingRequest request = new EmailMappingService.CreateEmailMappingRequest(
                "orphan@gmail.com", 0L, "Alice", TEAM_ID);

        assertThrows(IllegalArgumentException.class,
                () -> service.createMapping(EXERCISE_ID, request));
    }

    @Test
    void createMapping_fallsBackToRequestIdWhenNameNotFound() {
        TeamParticipation participation = makeParticipation();
        Student bob = makeStudent(888L, "Bob", "bob@uni.de", participation);
        Student charlie = makeStudent(777L, "Charlie", "charlie@uni.de", participation);
        AnalyzedChunk chunk = makeChunk(participation, "orphan@gmail.com", true);

        when(teamParticipationRepository.findByExerciseIdAndTeam(EXERCISE_ID, TEAM_ID))
                .thenReturn(Optional.of(participation));
        when(studentRepository.findAllByTeam(participation))
                .thenReturn(List.of(bob, charlie));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));

        // Student name "Unknown" does not match any student -> falls back to request ID (77)
        EmailMappingService.CreateEmailMappingRequest request = new EmailMappingService.CreateEmailMappingRequest(
                "orphan@gmail.com", 77L, "Unknown", TEAM_ID);

        service.createMapping(EXERCISE_ID, request);

        ArgumentCaptor<ExerciseEmailMapping> captor = ArgumentCaptor.forClass(ExerciseEmailMapping.class);
        verify(emailMappingRepository).save(captor.capture());
        assertEquals(77L, captor.getValue().getStudentId());
    }

    // ================================================================
    //  deleteMapping tests
    // ================================================================

    @Test
    void deleteMapping_remarksChunksAsExternalIfEmailNotKnown() {
        TeamParticipation participation = makeParticipation();
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(
                EXERCISE_ID, "orphan@gmail.com", 999L, "Alice");
        mapping.setId(UUID.randomUUID());
        AnalyzedChunk chunk = makeChunk(participation, "orphan@gmail.com", false);

        when(emailMappingRepository.findById(mapping.getId()))
                .thenReturn(Optional.of(mapping));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));
        // No students with this email, no remaining mappings -> email is unknown
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of());
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.deleteMapping(EXERCISE_ID, mapping.getId());

        assertTrue(chunk.getIsExternalContributor());
        assertEquals("orphan@gmail.com", chunk.getAuthorName());
    }

    @Test
    void deleteMapping_doesNotRemarkIfEmailStillKnown() {
        TeamParticipation participation = makeParticipation();
        Student alice = makeStudent(999L, "Alice", "orphan@gmail.com", participation);
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(
                EXERCISE_ID, "orphan@gmail.com", 999L, "Alice");
        mapping.setId(UUID.randomUUID());
        AnalyzedChunk chunk = makeChunk(participation, "orphan@gmail.com", false);

        when(emailMappingRepository.findById(mapping.getId()))
                .thenReturn(Optional.of(mapping));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));
        // Email is still known because the student has this email
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice));
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.deleteMapping(EXERCISE_ID, mapping.getId());

        // Chunk should remain non-external because the email belongs to a known student
        assertFalse(chunk.getIsExternalContributor());
    }

    @Test
    void deleteMapping_returnsEmptyIfNoChunksChanged() {
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(
                EXERCISE_ID, "orphan@gmail.com", 999L, "Alice");
        mapping.setId(UUID.randomUUID());
        TeamParticipation participation = makeParticipation();

        when(emailMappingRepository.findById(mapping.getId()))
                .thenReturn(Optional.of(mapping));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        // No chunks at all for this participation
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>());
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of());
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        Optional<ClientResponseDTO> result = service.deleteMapping(EXERCISE_ID, mapping.getId());

        assertTrue(result.isEmpty());
    }

    // ================================================================
    //  setTemplateAuthor tests
    // ================================================================

    @Test
    void setTemplateAuthor_newEmailMarksChunksAsExternal() {
        TeamParticipation participation = makeParticipation();
        AnalyzedChunk chunk = makeChunk(participation, "template@example.com", false);

        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.empty());
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of());
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        List<ClientResponseDTO> responses = service.setTemplateAuthor(EXERCISE_ID, "template@example.com");

        assertNotNull(responses);
        assertTrue(chunk.getIsExternalContributor());
    }

    @Test
    void setTemplateAuthor_changedEmail_oldKnownStudentChunksNotMarkedExternal() {
        TeamParticipation participation = makeParticipation();
        // Old template email is alice@uni.de, which is a known student email
        Student alice = makeStudent(999L, "Alice", "alice@uni.de", participation);
        AnalyzedChunk oldChunk = makeChunk(participation, "alice@uni.de", true);
        AnalyzedChunk newChunk = makeChunk(participation, "new-template@example.com", false);

        ExerciseTemplateAuthor existing = new ExerciseTemplateAuthor(
                EXERCISE_ID, "alice@uni.de", false);
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.of(existing));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(oldChunk, newChunk)));
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice));
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.setTemplateAuthor(EXERCISE_ID, "new-template@example.com");

        // Bug 3 fix: old email (alice@uni.de) is a known student -> should NOT be marked external
        assertFalse(oldChunk.getIsExternalContributor());
        // New template email should be marked external
        assertTrue(newChunk.getIsExternalContributor());
    }

    @Test
    void setTemplateAuthor_changedEmail_oldUnknownEmailStaysExternal() {
        TeamParticipation participation = makeParticipation();
        AnalyzedChunk oldChunk = makeChunk(participation, "old-unknown@example.com", true);

        ExerciseTemplateAuthor existing = new ExerciseTemplateAuthor(
                EXERCISE_ID, "old-unknown@example.com", false);
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.of(existing));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(oldChunk)));
        // No students or mappings match old-unknown@example.com
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of());
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.setTemplateAuthor(EXERCISE_ID, "new-template@example.com");

        // Old unknown email should become external (orphan)
        assertTrue(oldChunk.getIsExternalContributor());
    }

    // ================================================================
    //  deleteTemplateAuthor tests
    // ================================================================

    @Test
    void deleteTemplateAuthor_removesEntity() {
        ExerciseTemplateAuthor ta = new ExerciseTemplateAuthor(
                EXERCISE_ID, "template@example.com", false);
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.of(ta));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of());

        service.deleteTemplateAuthor(EXERCISE_ID);

        verify(templateAuthorRepository).delete(ta);
    }

    @Test
    void deleteTemplateAuthor_unmarksChunksIfOldEmailIsKnown() {
        TeamParticipation participation = makeParticipation();
        Student alice = makeStudent(999L, "Alice", "template@example.com", participation);
        // Chunk was marked external because it was the template author
        AnalyzedChunk chunk = makeChunk(participation, "template@example.com", true);

        ExerciseTemplateAuthor ta = new ExerciseTemplateAuthor(
                EXERCISE_ID, "template@example.com", false);
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.of(ta));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));
        // The old template email is a known student
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of(alice));
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.deleteTemplateAuthor(EXERCISE_ID);

        // Bug 2 fix: known email -> chunk should be unmarked (non-external)
        assertFalse(chunk.getIsExternalContributor());
        verify(analyzedChunkRepository).saveAll(anyList());
    }

    @Test
    void deleteTemplateAuthor_unknownEmailChunksStayExternal() {
        TeamParticipation participation = makeParticipation();
        AnalyzedChunk chunk = makeChunk(participation, "template@example.com", true);

        ExerciseTemplateAuthor ta = new ExerciseTemplateAuthor(
                EXERCISE_ID, "template@example.com", false);
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.of(ta));
        when(teamParticipationRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(participation));
        when(analyzedChunkRepository.findByParticipation(participation))
                .thenReturn(new ArrayList<>(List.of(chunk)));
        // No students or mappings match -> email is unknown
        when(studentRepository.findAllByTeam(participation)).thenReturn(List.of());
        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        service.deleteTemplateAuthor(EXERCISE_ID);

        // Unknown email -> chunk stays external
        assertTrue(chunk.getIsExternalContributor());
    }

    @Test
    void deleteTemplateAuthor_returnsEmptyIfNoTemplateConfigured() {
        when(templateAuthorRepository.findByExerciseId(EXERCISE_ID))
                .thenReturn(Optional.empty());

        Optional<List<ClientResponseDTO>> result = service.deleteTemplateAuthor(EXERCISE_ID);

        assertTrue(result.isEmpty());
        verify(templateAuthorRepository, never()).delete(any());
    }

    // ================================================================
    //  getAllMappings test
    // ================================================================

    @Test
    void getAllMappings_returnsMappingsAsDTOs() {
        ExerciseEmailMapping m1 = new ExerciseEmailMapping(
                EXERCISE_ID, "orphan@gmail.com", 999L, "Alice");
        m1.setId(UUID.randomUUID());
        ExerciseEmailMapping m2 = new ExerciseEmailMapping(
                EXERCISE_ID, "another@gmail.com", 888L, "Bob");
        m2.setId(UUID.randomUUID());

        when(emailMappingRepository.findAllByExerciseId(EXERCISE_ID))
                .thenReturn(List.of(m1, m2));

        List<EmailMappingService.EmailMappingDTO> result = service.getAllMappings(EXERCISE_ID);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("orphan@gmail.com", result.get(0).gitEmail());
        assertEquals(999L, result.get(0).studentId());
        assertEquals("another@gmail.com", result.get(1).gitEmail());
    }
}
