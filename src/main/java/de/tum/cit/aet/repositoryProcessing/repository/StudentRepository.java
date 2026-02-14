package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    List<Student> findAllByTeam(TeamParticipation participation);

    /**
     * Delete all students for a given team participation.
     *
     * @param participation the team participation to delete students for
     */
    void deleteAllByTeam(TeamParticipation participation);
}
