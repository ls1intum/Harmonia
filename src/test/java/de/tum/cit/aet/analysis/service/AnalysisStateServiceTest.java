package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import de.tum.cit.aet.analysis.repository.AnalysisStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisStateServiceTest {

    @Mock
    private AnalysisStatusRepository statusRepository;

    private AnalysisStateService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisStateService(statusRepository);
    }

    @Test
    void getStatus_newExercise_returnsIdleStatus() {
        when(statusRepository.findById(123L)).thenReturn(Optional.empty());

        AnalysisStatus status = service.getStatus(123L);

        assertEquals(123L, status.getExerciseId());
        assertEquals(AnalysisState.IDLE, status.getState());
        assertEquals(0, status.getTotalTeams());
    }

    @Test
    void getStatus_existingExercise_returnsPersistedStatus() {
        AnalysisStatus existing = new AnalysisStatus(123L);
        existing.setState(AnalysisState.DONE);
        existing.setTotalTeams(10);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(existing));

        AnalysisStatus status = service.getStatus(123L);

        assertEquals(AnalysisState.DONE, status.getState());
        assertEquals(10, status.getTotalTeams());
    }

    @Test
    void startAnalysis_idleState_transitionsToRunning() {
        AnalysisStatus idle = new AnalysisStatus(123L);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(idle));
        when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalysisStatus result = service.startAnalysis(123L, 5);

        assertEquals(AnalysisState.RUNNING, result.getState());
        assertEquals(5, result.getTotalTeams());
        assertNotNull(result.getStartedAt());
    }

    @Test
    void startAnalysis_alreadyRunning_throwsException() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));

        assertThrows(IllegalStateException.class, () -> service.startAnalysis(123L, 5));
    }

    @Test
    void updateProgress_runningState_updatesFieldsCorrectly() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));
        // Note: updateProgress uses direct query updates, not save

        AnalysisStatus result = service.updateProgress(123L, "Team A", "DOWNLOADING", 3);

        assertEquals("Team A", result.getCurrentTeamName());
        assertEquals("DOWNLOADING", result.getCurrentStage());
        assertEquals(3, result.getProcessedTeams());
    }

    @Test
    void updateProgress_notRunning_returnsCurrentStatus() {
        AnalysisStatus idle = new AnalysisStatus(123L);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(idle));

        AnalysisStatus result = service.updateProgress(123L, "Team A", "DOWNLOADING", 3);

        assertEquals(AnalysisState.IDLE, result.getState());
        verify(statusRepository, never()).save(any());
    }

    @Test
    void completeAnalysis_runningState_transitionsToDone() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        running.setCurrentTeamName("Team X");
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));
        when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalysisStatus result = service.completeAnalysis(123L);

        assertEquals(AnalysisState.DONE, result.getState());
        assertNull(result.getCurrentTeamName());
    }

    @Test
    void failAnalysis_runningState_transitionsToError() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));
        when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalysisStatus result = service.failAnalysis(123L, "Network error");

        assertEquals(AnalysisState.ERROR, result.getState());
        assertEquals("Network error", result.getErrorMessage());
    }

    @Test
    void cancelAnalysis_runningState_transitionsToCancelled() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        running.setProcessedTeams(5);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));
        when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalysisStatus result = service.cancelAnalysis(123L);

        assertEquals(AnalysisState.CANCELLED, result.getState());
        assertEquals(5, result.getProcessedTeams());
    }

    @Test
    void cancelAnalysis_notRunning_noOp() {
        AnalysisStatus idle = new AnalysisStatus(123L);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(idle));

        AnalysisStatus result = service.cancelAnalysis(123L);

        assertEquals(AnalysisState.IDLE, result.getState());
        verify(statusRepository, never()).save(any());
    }

    @Test
    void resetStatus_anyState_transitionsToIdle() {
        AnalysisStatus done = new AnalysisStatus(123L);
        done.setState(AnalysisState.DONE);
        done.setTotalTeams(10);
        done.setProcessedTeams(10);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(done));
        when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalysisStatus result = service.resetStatus(123L);

        assertEquals(AnalysisState.IDLE, result.getState());
        assertEquals(0, result.getTotalTeams());
        assertEquals(0, result.getProcessedTeams());
    }

    @Test
    void isRunning_runningState_returnsTrue() {
        AnalysisStatus running = new AnalysisStatus(123L);
        running.setState(AnalysisState.RUNNING);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(running));

        assertTrue(service.isRunning(123L));
    }

    @Test
    void isRunning_idleState_returnsFalse() {
        AnalysisStatus idle = new AnalysisStatus(123L);
        when(statusRepository.findById(123L)).thenReturn(Optional.of(idle));

        assertFalse(service.isRunning(123L));
    }
}
