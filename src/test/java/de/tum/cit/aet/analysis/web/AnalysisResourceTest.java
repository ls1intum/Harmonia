package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.dto.AnalysisStatusDTO;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.dataProcessing.service.AIAnalysisPersistenceService;
import de.tum.cit.aet.dataProcessing.service.AnalysisTaskManager;
import de.tum.cit.aet.dataProcessing.service.ExerciseDataCleanupService;
import de.tum.cit.aet.pairProgramming.service.PairProgrammingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisResourceTest {

    @Mock
    private AnalysisStateService stateService;

    @Mock
    private AnalysisTaskManager analysisTaskManager;

    @Mock
    private PairProgrammingService pairProgrammingService;

    @Mock
    private ExerciseDataCleanupService cleanupService;

    @Mock
    private AIAnalysisPersistenceService aiPersistenceService;

    @InjectMocks
    private AnalysisResource resource;

    @Test
    void getStatus_validExercise_returns200WithStatus() {
        AnalysisStatus status = new AnalysisStatus(123L);
        status.setState(AnalysisState.RUNNING);
        status.setTotalTeams(10);
        status.setProcessedTeams(5);
        when(stateService.getStatus(123L)).thenReturn(status);

        ResponseEntity<AnalysisStatusDTO> response = resource.getStatus(123L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RUNNING", response.getBody().state());
        assertEquals(10, response.getBody().totalTeams());
        assertEquals(5, response.getBody().processedTeams());
    }

    @Test
    void cancelAnalysis_runningAnalysis_returns200() {
        AnalysisStatus cancelled = new AnalysisStatus(123L);
        when(stateService.cancelAnalysis(123L)).thenReturn(cancelled);

        ResponseEntity<AnalysisStatusDTO> response = resource.cancelAnalysis(123L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(analysisTaskManager).stopAnalysis(123L);
        verify(stateService).cancelAnalysis(123L);
    }

    @Test
    void clearData_dbOnly_clearsOnlyDatabase() {
        ResponseEntity<String> response = resource.clearData(123L, "db", false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(analysisTaskManager).stopAnalysis(123L);  // Always stops running analysis first
        verify(pairProgrammingService).clear();  // Always clears attendance data
        verify(cleanupService).clearDatabaseForExercise(123L);
        verify(stateService).resetStatus(123L);
        verify(cleanupService, never()).clearEmailMappingsAndTemplateAuthor(any());
    }

    @Test
    void clearData_filesOnly_clearsOnlyFiles() {
        ResponseEntity<String> response = resource.clearData(123L, "files", false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(analysisTaskManager).stopAnalysis(123L);  // Always stops running analysis first
        verify(pairProgrammingService).clear();  // Always clears attendance data
        verify(cleanupService, never()).clearDatabaseForExercise(any());
        verify(stateService, never()).resetStatus(any());
        verify(cleanupService, never()).clearEmailMappingsAndTemplateAuthor(any());
    }

    @Test
    void clearData_both_clearsDatabaseAndFiles() {
        ResponseEntity<String> response = resource.clearData(123L, "both", false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(analysisTaskManager).stopAnalysis(123L);  // Always stops running analysis first
        verify(pairProgrammingService).clear();  // Always clears attendance data
        verify(cleanupService).clearDatabaseForExercise(123L);
        verify(stateService).resetStatus(123L);
        verify(cleanupService, never()).clearEmailMappingsAndTemplateAuthor(any());
    }

    @Test
    void clearData_withClearMappings_deletesEmailMappingsAndTemplateAuthor() {
        ResponseEntity<String> response = resource.clearData(123L, "both", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cleanupService).clearDatabaseForExercise(123L);
        verify(cleanupService).clearEmailMappingsAndTemplateAuthor(123L);
    }

    @Test
    void clearData_filesOnlyWithClearMappings_doesNotDeleteMappings() {
        ResponseEntity<String> response = resource.clearData(123L, "files", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cleanupService, never()).clearDatabaseForExercise(any());
        verify(cleanupService, never()).clearEmailMappingsAndTemplateAuthor(any());
    }

}
