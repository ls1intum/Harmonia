package de.tum.cit.aet.analysis.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entity representing the persistent state of an analysis job for an exercise.
 * Primary key is the exerciseId since each exercise can only have one active
 * analysis.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "analysis_status")
public class AnalysisStatus {

    @Id
    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private AnalysisState state = AnalysisState.IDLE;

    @Column(name = "total_teams")
    private int totalTeams;

    @Column(name = "processed_teams")
    private int processedTeams;

    @Column(name = "current_team_name")
    private String currentTeamName;

    @Column(name = "current_stage")
    private String currentStage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public AnalysisStatus(Long exerciseId) {
        this.exerciseId = exerciseId;
        this.state = AnalysisState.IDLE;
    }

    /**
     * Reset the status to IDLE state, clearing all progress data.
     */
    public void reset() {
        this.state = AnalysisState.IDLE;
        this.totalTeams = 0;
        this.processedTeams = 0;
        this.currentTeamName = null;
        this.currentStage = null;
        this.startedAt = null;
        this.lastUpdatedAt = Instant.now();
        this.errorMessage = null;
    }
}
