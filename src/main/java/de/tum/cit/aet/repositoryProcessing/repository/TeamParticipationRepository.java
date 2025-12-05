package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, UUID> {
}
