package de.tum.cit.aet.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.export.dto.ExportFormat;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private TeamParticipationRepository teamParticipationRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AnalyzedChunkRepository analyzedChunkRepository;

    @Mock
    private TeamRepositoryRepository teamRepositoryRepository;

    private ExportService exportService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        exportService = new ExportService(teamParticipationRepository, studentRepository,
                analyzedChunkRepository, teamRepositoryRepository);
    }

    @Test
    void exportData_includeTeams_populatesTeamRows() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("teams"));

        JsonNode root = mapper.readTree(result);
        JsonNode team = root.get("teams").get(0);
        assertEquals("Team1", team.get("teamName").asText());
        assertEquals(0.85, team.get("cqi").asDouble(), 0.001);
        assertEquals("DONE", team.get("analysisStatus").asText());
        assertFalse(team.get("isSuspicious").asBoolean());
        assertNull(root.get("students"));
        assertNull(root.get("chunks"));
        assertNull(root.get("commits"));
        verifyNoInteractions(studentRepository);
        verifyNoInteractions(analyzedChunkRepository);
        verifyNoInteractions(teamRepositoryRepository);
    }

    @Test
    void exportData_includeStudents_populatesStudentRows() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        Student student = new Student(1L, "alice01", "Alice", "alice@test.com", tp, 10, 200, 50, 250);
        when(studentRepository.findAllByTeam(tp)).thenReturn(List.of(student));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("students"));

        JsonNode root = mapper.readTree(result);
        JsonNode s = root.get("students").get(0);
        assertEquals("Team1", s.get("teamName").asText());
        assertEquals("Alice", s.get("studentName").asText());
        assertEquals("alice01", s.get("login").asText());
        assertEquals("alice@test.com", s.get("email").asText());
        assertEquals(10, s.get("commitCount").asInt());
        verify(studentRepository).findAllByTeam(tp);
    }

    @Test
    void exportData_includeChunks_populatesChunkRows() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        AnalyzedChunk chunk = new AnalyzedChunk();
        chunk.setParticipation(tp);
        chunk.setAuthorName("Alice");
        chunk.setAuthorEmail("alice@test.com");
        chunk.setClassification("FEATURE");
        chunk.setEffortScore(0.9);
        chunk.setComplexity(0.8);
        chunk.setNovelty(0.7);
        chunk.setConfidence(0.95);
        chunk.setReasoning("Good work");
        chunk.setCommitShas("abc123");
        chunk.setTimestamp(LocalDateTime.of(2025, 1, 15, 10, 30));
        chunk.setLinesChanged(42);
        chunk.setLlmModel("gpt-4");
        chunk.setLlmUsageAvailable(true);
        when(analyzedChunkRepository.findByParticipation(tp)).thenReturn(List.of(chunk));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("chunks"));

        JsonNode root = mapper.readTree(result);
        JsonNode c = root.get("chunks").get(0);
        assertEquals("Team1", c.get("teamName").asText());
        assertEquals("FEATURE", c.get("classification").asText());
        assertEquals("alice@test.com", c.get("authorEmail").asText());
        assertEquals(0.9, c.get("effortScore").asDouble(), 0.001);
        assertEquals("gpt-4", c.get("llmModel").asText());
        assertTrue(c.get("llmUsageAvailable").asBoolean());
        verify(analyzedChunkRepository).findByParticipation(tp);
    }

    @Test
    void exportData_includeCommits_populatesCommitRows() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        TeamRepository teamRepo = new TeamRepository();
        teamRepo.setTeamParticipation(tp);
        VCSLog log = new VCSLog();
        log.setCommitHash("abc123");
        log.setEmail("alice@test.com");
        teamRepo.setVcsLogs(List.of(log));
        when(teamRepositoryRepository.findByTeamParticipation(tp)).thenReturn(Optional.of(teamRepo));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("commits"));

        JsonNode root = mapper.readTree(result);
        JsonNode commit = root.get("commits").get(0);
        assertEquals("Team1", commit.get("teamName").asText());
        assertEquals("abc123", commit.get("commitHash").asText());
        assertEquals("alice@test.com", commit.get("authorEmail").asText());
        verify(teamRepositoryRepository).findByTeamParticipation(tp);
    }

    @Test
    void exportData_allIncludes_populatesAllRows() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        Student student = new Student(1L, "alice01", "Alice", "alice@test.com", tp, 10, 200, 50, 250);
        when(studentRepository.findAllByTeam(tp)).thenReturn(List.of(student));

        AnalyzedChunk chunk = new AnalyzedChunk();
        chunk.setAuthorName("Alice");
        chunk.setClassification("FEATURE");
        when(analyzedChunkRepository.findByParticipation(tp)).thenReturn(List.of(chunk));

        TeamRepository teamRepo = new TeamRepository();
        teamRepo.setTeamParticipation(tp);
        VCSLog log = new VCSLog();
        log.setCommitHash("abc123");
        log.setEmail("alice@test.com");
        teamRepo.setVcsLogs(List.of(log));
        when(teamRepositoryRepository.findByTeamParticipation(tp)).thenReturn(Optional.of(teamRepo));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON,
                Set.of("teams", "students", "chunks", "commits"));

        JsonNode root = mapper.readTree(result);
        assertFalse(root.get("teams").isNull());
        assertEquals(1, root.get("teams").size());
        assertFalse(root.get("students").isNull());
        assertEquals(1, root.get("students").size());
        assertFalse(root.get("chunks").isNull());
        assertEquals(1, root.get("chunks").size());
        assertFalse(root.get("commits").isNull());
        assertEquals(1, root.get("commits").size());
    }

    @Test
    void exportData_excelFormat_returnsNonEmptyBytes() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        byte[] result = exportService.exportData(1L, ExportFormat.EXCEL, Set.of("teams"));

        assertTrue(result.length > 0);
    }

    @Test
    void exportData_noParticipations_returnsEmptyExport() throws IOException {
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of());

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("teams"));

        JsonNode root = mapper.readTree(result);
        assertTrue(root.get("teams").isArray());
        assertEquals(0, root.get("teams").size());
    }

    @Test
    void exportData_sanitizesRepositoryUrl() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        tp.setRepositoryUrl("https://user:secret@git.example.com/repo.git");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("teams"));

        JsonNode root = mapper.readTree(result);
        String exportedUrl = root.get("teams").get(0).get("repositoryUrl").asText();
        assertFalse(exportedUrl.contains("user"), "URL should not contain username");
        assertFalse(exportedUrl.contains("secret"), "URL should not contain password");
        assertTrue(exportedUrl.contains("git.example.com/repo.git"));
    }

    @Test
    void exportData_sanitizesUrlWithoutCredentials() throws IOException {
        TeamParticipation tp = createParticipation("Team1");
        tp.setRepositoryUrl("https://git.example.com/repo.git");
        when(teamParticipationRepository.findAllByExerciseId(1L)).thenReturn(List.of(tp));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("teams"));

        JsonNode root = mapper.readTree(result);
        assertEquals("https://git.example.com/repo.git",
                root.get("teams").get(0).get("repositoryUrl").asText());
    }

    private TeamParticipation createParticipation(String name) {
        TeamParticipation tp = new TeamParticipation();
        tp.setName(name);
        tp.setExerciseId(1L);
        tp.setSubmissionCount(5);
        tp.setCqi(0.85);
        tp.setAnalysisStatus(AnalysisStatus.DONE);
        tp.setIsSuspicious(false);
        tp.setLlmTotalTokens(1000L);
        return tp;
    }
}
