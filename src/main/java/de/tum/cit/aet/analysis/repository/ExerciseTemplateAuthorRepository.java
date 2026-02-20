package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExerciseTemplateAuthorRepository extends JpaRepository<ExerciseTemplateAuthor, UUID> {

    Optional<ExerciseTemplateAuthor> findByExerciseId(Long exerciseId);

    void deleteByExerciseId(Long exerciseId);
}
