package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciseTemplateAuthorRepository extends JpaRepository<ExerciseTemplateAuthor, UUID> {

    List<ExerciseTemplateAuthor> findByExerciseId(Long exerciseId);

    @Modifying
    @Transactional
    void deleteByExerciseId(Long exerciseId);
}
