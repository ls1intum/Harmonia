package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.Tutor;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TutorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciseDataCleanupServiceTest {

    @Mock private TeamParticipationRepository teamParticipationRepository;
    @Mock private TeamRepositoryRepository teamRepositoryRepository;
    @Mock private AnalyzedChunkRepository analyzedChunkRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TutorRepository tutorRepository;

    @InjectMocks
    private ExerciseDataCleanupService service;

    @Test
    void clearDatabase_deletesOrphanedTutors() {
        Long exerciseId = 1L;

        Tutor tutor = new Tutor(100L, "tutor1", "Tutor One", "tutor@uni.de");
        UUID tutorId = UUID.randomUUID();
        tutor.setTutorId(tutorId);

        TeamParticipation participation = new TeamParticipation(
                10L, 1L, tutor, "Team A", "team-a", "https://repo", 5);

        when(teamParticipationRepository.findAllByExerciseId(exerciseId))
                .thenReturn(List.of(participation));

        service.clearDatabaseForExercise(exerciseId);

        // Verify child entities are deleted
        verify(teamRepositoryRepository).deleteAllByTeamParticipation(participation);
        verify(analyzedChunkRepository).deleteAllByParticipation(participation);
        verify(studentRepository).deleteAllByTeam(participation);
        verify(teamParticipationRepository).deleteAllByExerciseId(exerciseId);

        // Verify orphaned tutors are cleaned up
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(tutorRepository).deleteOrphanedByIds(idsCaptor.capture());
        assertTrue(idsCaptor.getValue().contains(tutorId));
    }

    @Test
    void clearDatabase_noParticipations_skipsAllDeletions() {
        Long exerciseId = 1L;

        when(teamParticipationRepository.findAllByExerciseId(exerciseId))
                .thenReturn(List.of());

        service.clearDatabaseForExercise(exerciseId);

        verify(tutorRepository, never()).deleteOrphanedByIds(any());
        verify(teamRepositoryRepository, never()).deleteAllByTeamParticipation(any());
        verify(studentRepository, never()).deleteAllByTeam(any());
    }

    @Test
    void clearDatabase_participationWithoutTutor_skipsOrphanDeletion() {
        Long exerciseId = 1L;

        TeamParticipation participation = new TeamParticipation(
                10L, 1L, null, "Team A", "team-a", "https://repo", 5);

        when(teamParticipationRepository.findAllByExerciseId(exerciseId))
                .thenReturn(List.of(participation));

        service.clearDatabaseForExercise(exerciseId);

        // Tutor IDs list is empty, so deleteOrphanedByIds should not be called
        verify(tutorRepository, never()).deleteOrphanedByIds(any());

        // Other deletions should still happen
        verify(teamParticipationRepository).deleteAllByExerciseId(exerciseId);
    }

    @Test
    void clearDatabase_sharedTutor_deduplicatesIds() {
        Long exerciseId = 1L;

        Tutor tutor = new Tutor(100L, "tutor1", "Tutor One", "tutor@uni.de");
        UUID tutorId = UUID.randomUUID();
        tutor.setTutorId(tutorId);

        TeamParticipation p1 = new TeamParticipation(10L, 1L, tutor, "Team A", "team-a", "https://repo-a", 5);
        TeamParticipation p2 = new TeamParticipation(11L, 1L, tutor, "Team B", "team-b", "https://repo-b", 5);

        when(teamParticipationRepository.findAllByExerciseId(exerciseId))
                .thenReturn(List.of(p1, p2));

        service.clearDatabaseForExercise(exerciseId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(tutorRepository).deleteOrphanedByIds(idsCaptor.capture());
        assertEquals(1, idsCaptor.getValue().size());
        assertTrue(idsCaptor.getValue().contains(tutorId));
    }
}
