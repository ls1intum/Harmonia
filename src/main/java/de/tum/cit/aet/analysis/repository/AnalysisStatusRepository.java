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
     *
     * @param state the state to filter by
     * @return a list of analysis statuses with the given state
     */
    List<AnalysisStatus> findByState(AnalysisState state);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE AnalysisStatus s SET s.processedTeams = :processed, s.currentStage = :stage, s.lastUpdatedAt = CURRENT_TIMESTAMP WHERE s.exerciseId = :exerciseId AND s.state = 'RUNNING'")
    void updateProgress(Long exerciseId, String stage, int processed);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE AnalysisStatus s SET s.currentTeamName = :teamName, s.processedTeams = :processed, s.currentStage = :stage, s.lastUpdatedAt = CURRENT_TIMESTAMP WHERE s.exerciseId = :exerciseId AND s.state = 'RUNNING'")
    void updateProgressAndName(Long exerciseId, String teamName, String stage, int processed);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE AnalysisStatus s SET s.currentTeamName = CASE WHEN s.currentTeamName = :teamNameToCheck THEN NULL ELSE s.currentTeamName END, s.processedTeams = :processed, s.currentStage = :stage, s.lastUpdatedAt = CURRENT_TIMESTAMP WHERE s.exerciseId = :exerciseId AND s.state = 'RUNNING'")
    void updateProgressAndClearNameIfMatching(Long exerciseId, String teamNameToCheck, String stage, int processed);
}
