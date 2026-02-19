package de.tum.cit.aet.export.service;

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

        assertTrue(result.length > 0);
        String json = new String(result);
        assertTrue(json.contains("Team1"));
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

        String json = new String(result);
        assertTrue(json.contains("Alice"));
        assertTrue(json.contains("alice01"));
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
        when(analyzedChunkRepository.findByParticipation(tp)).thenReturn(List.of(chunk));

        byte[] result = exportService.exportData(1L, ExportFormat.JSON, Set.of("chunks"));

        String json = new String(result);
        assertTrue(json.contains("FEATURE"));
        assertTrue(json.contains("alice@test.com"));
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

        String json = new String(result);
        assertTrue(json.contains("abc123"));
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

        String json = new String(result);
        assertTrue(json.contains("\"teams\""));
        assertTrue(json.contains("\"students\""));
        assertTrue(json.contains("\"chunks\""));
        assertTrue(json.contains("\"commits\""));
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

        String json = new String(result);
        assertTrue(json.contains("null"));
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
