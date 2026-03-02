package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.Tutor;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamDTO;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TutorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database clearing, team initialization, and team status management for exercises.
 */
@Service
@Slf4j
public class ExerciseDataCleanupService {

    private final TeamParticipationRepository teamParticipationRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;

    public ExerciseDataCleanupService(TeamParticipationRepository teamParticipationRepository,
                                      TeamRepositoryRepository teamRepositoryRepository,
                                      AnalyzedChunkRepository analyzedChunkRepository,
                                      StudentRepository studentRepository,
                                      TutorRepository tutorRepository) {
        this.teamParticipationRepository = teamParticipationRepository;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.studentRepository = studentRepository;
        this.tutorRepository = tutorRepository;
    }

    /**
     * Deletes all persisted data (participations, repos, chunks, students, tutors) for an exercise.
     *
     * @param exerciseId the exercise to clear
     */
    @Transactional
    public void clearDatabaseForExercise(Long exerciseId) {
        clearDatabaseForExerciseInternal(exerciseId);
    }

    /**
     * Internal implementation that performs the actual database cleanup for an exercise.
     *
     * @param exerciseId the exercise to clear
     */
    public void clearDatabaseForExerciseInternal(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return;
        }

        List<UUID> tutorIds = participations.stream()
                .map(TeamParticipation::getTutor)
                .filter(t -> t != null)
                .map(Tutor::getTutorId)
                .distinct()
                .toList();

        for (TeamParticipation participation : participations) {
            teamRepositoryRepository.deleteAllByTeamParticipation(participation);
            analyzedChunkRepository.deleteAllByParticipation(participation);
            studentRepository.deleteAllByTeam(participation);
        }

        teamParticipationRepository.deleteAllByExerciseId(exerciseId);

        if (!tutorIds.isEmpty()) {
            tutorRepository.deleteOrphanedByIds(tutorIds);
        }

        log.info("Cleared {} participations for exerciseId={}", participations.size(), exerciseId);
    }

    /**
     * Creates or updates team participations in PENDING status so they are ready for analysis.
     *
     * @param participations the Artemis participation DTOs to initialize
     * @param exerciseId     the exercise being analyzed
     * @param isResume       {@code true} if resuming a previously cancelled run
     */
    public void initializePendingTeams(List<ParticipationDTO> participations, Long exerciseId, boolean isResume) {
        for (ParticipationDTO participation : participations) {
            if (participation.team() == null) {
                continue;
            }

            Optional<TeamParticipation> existing = teamParticipationRepository.findByParticipation(participation.id());

            if (existing.isPresent()) {
                TeamParticipation tp = existing.get();

                if (tp.getCqi() != null && tp.getCqi() > 0 && tp.getAnalysisStatus() == TeamAnalysisStatus.DONE) {
                    continue;
                }

                if (!isResume) {
                    tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
                    tp.setTutor(ensureTutor(participation.team()));
                    teamParticipationRepository.save(tp);
                } else if (tp.getAnalysisStatus() == null) {
                    tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
                    teamParticipationRepository.save(tp);
                }
            } else {
                Tutor tutor = ensureTutor(participation.team());
                TeamParticipation tp = new TeamParticipation(
                        participation.id(), participation.team().id(), tutor,
                        participation.team().name(), participation.team().shortName(),
                        participation.repositoryUri(), participation.submissionCount());
                tp.setExerciseId(exerciseId);
                tp.setAnalysisStatus(TeamAnalysisStatus.PENDING);
                teamParticipationRepository.save(tp);

                if (participation.team().students() != null) {
                    for (ParticipantDTO student : participation.team().students()) {
                        studentRepository.save(new Student(student.id(), student.login(),
                                student.name(), student.email(), tp, 0, 0, 0, 0));
                    }
                }
            }
        }
    }

    /**
     * Marks a single team participation as ERROR (e.g. after a download or analysis failure).
     *
     * @param participation the Artemis participation DTO
     * @param exerciseId    the exercise being analyzed
     */
    public void markTeamAsFailed(ParticipationDTO participation, Long exerciseId) {
        try {
            TeamDTO team = participation.team();
            Tutor tutor = ensureTutor(team);

            TeamParticipation tp = teamParticipationRepository.findByParticipation(participation.id())
                    .orElse(new TeamParticipation(participation.id(), team.id(), tutor, team.name(),
                            team.shortName(), participation.repositoryUri(), participation.submissionCount()));

            tp.setExerciseId(exerciseId);
            tp.setAnalysisStatus(TeamAnalysisStatus.ERROR);
            teamParticipationRepository.save(tp);
        } catch (Exception e) {
            log.error("Failed to mark team {} as failed", participation.team().name(), e);
        }
    }

    /**
     * Transitions all teams still in PENDING status to CANCELLED for the given exercise.
     *
     * @param exerciseId the exercise whose pending teams should be cancelled
     */
    public void markPendingTeamsAsCancelled(Long exerciseId) {
        try {
            List<TeamParticipation> pendingTeams = teamParticipationRepository
                    .findAllByExerciseIdAndAnalysisStatus(exerciseId, TeamAnalysisStatus.PENDING);

            for (TeamParticipation team : pendingTeams) {
                team.setAnalysisStatus(TeamAnalysisStatus.CANCELLED);
                teamParticipationRepository.save(team);
            }

            if (!pendingTeams.isEmpty()) {
                log.info("Marked {} pending teams as CANCELLED for exerciseId={}", pendingTeams.size(), exerciseId);
            }
        } catch (Exception e) {
            log.error("Failed to mark pending teams as cancelled for exerciseId={}", exerciseId, e);
        }
    }

    /**
     * Persists or updates the tutor entity for a team, based on the team owner.
     *
     * @param team the team DTO containing the owner information
     * @return the persisted tutor, or {@code null} if the team has no owner
     */
    public Tutor ensureTutor(TeamDTO team) {
        if (team.owner() != null) {
            ParticipantDTO tut = team.owner();
            return tutorRepository.save(new Tutor(tut.id(), tut.login(), tut.name(), tut.email()));
        }
        return null;
    }
}
