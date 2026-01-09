package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisting analysis status.
 */
@Repository
public interface AnalysisStatusRepository extends JpaRepository<AnalysisStatus, Long> {
}
