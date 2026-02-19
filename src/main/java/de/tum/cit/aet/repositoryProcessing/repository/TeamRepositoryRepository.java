package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.TeamRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepositoryRepository extends JpaRepository<TeamRepository, UUID> {

    /**
     * Delete all team repositories for a given participation.
     *
     * @param participation the team participation to delete repositories for
     */
    void deleteAllByTeamParticipation(TeamParticipation participation);

    /**
     * Finds the repository metadata for a given team participation.
     *
     * @param participation the team participation
     * @return the repository entity, if available
     */
    Optional<TeamRepository> findByTeamParticipation(TeamParticipation participation);
}
