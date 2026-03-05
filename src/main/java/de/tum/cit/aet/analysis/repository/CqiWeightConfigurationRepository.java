package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.CqiWeightConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CqiWeightConfigurationRepository extends JpaRepository<CqiWeightConfiguration, UUID> {

    Optional<CqiWeightConfiguration> findByExerciseId(Long exerciseId);

    void deleteByExerciseId(Long exerciseId);
}
