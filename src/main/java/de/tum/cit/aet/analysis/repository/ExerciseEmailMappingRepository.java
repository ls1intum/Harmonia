package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciseEmailMappingRepository extends JpaRepository<ExerciseEmailMapping, UUID> {

    List<ExerciseEmailMapping> findAllByExerciseId(Long exerciseId);

    boolean existsByExerciseIdAndGitEmail(Long exerciseId, String gitEmail);

    void deleteById(UUID id);

    @Modifying
    @Transactional
    void deleteAllByExerciseId(Long exerciseId);
}
