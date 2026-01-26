package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, UUID> {

    /**
     * Check if a participation exists by its Artemis participation ID.
     *
     * @param participation the Artemis participation ID
     * @return true if it exists, false otherwise
     */
    boolean existsByParticipation(Long participation);
    
    /**
     * Check if a participation has been fully analyzed (has CQI calculated).
     *
     * @param participation the Artemis participation ID
     * @return true if it exists AND has a CQI value, false otherwise
     */
    boolean existsByParticipationAndCqiIsNotNull(Long participation);
    
    /**
     * Find a participation by its Artemis participation ID.
     *
     * @param participation the Artemis participation ID
     * @return the TeamParticipation if found
     */
    Optional<TeamParticipation> findByParticipation(Long participation);
}
