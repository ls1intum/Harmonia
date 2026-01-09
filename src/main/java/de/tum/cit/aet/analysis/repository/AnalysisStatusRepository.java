package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.AnalysisState;
import de.tum.cit.aet.analysis.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for persisting analysis status.
 */
@Repository
public interface AnalysisStatusRepository extends JpaRepository<AnalysisStatus, Long> {

    /**
     * Find all analyses with a given state.
     */
    List<AnalysisStatus> findByState(AnalysisState state);
}
