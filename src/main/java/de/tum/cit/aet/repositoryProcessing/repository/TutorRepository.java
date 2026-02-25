package de.tum.cit.aet.repositoryProcessing.repository;

import de.tum.cit.aet.repositoryProcessing.domain.Tutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Tutor t WHERE t.tutorId IN :ids AND NOT EXISTS (SELECT 1 FROM TeamParticipation p WHERE p.tutor = t)")
    void deleteOrphanedByIds(Collection<UUID> ids);
}
