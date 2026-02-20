package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciseEmailMappingRepository extends JpaRepository<ExerciseEmailMapping, UUID> {

    List<ExerciseEmailMapping> findAllByExerciseId(Long exerciseId);

    void deleteById(UUID id);
}
