package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.TeamRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TeamRepositoryRepository extends JpaRepository<TeamRepository, UUID> {
    TeamRepository findByTeamParticipation(TeamParticipation teamParticipation);
}
