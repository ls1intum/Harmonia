package de.tum.cit.aet.repositoryProcessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "participations")
public class TeamParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "teamParticipation_id", nullable = false)
    private UUID teamParticipationId;

    @Column(name = "artemis_participation_id", nullable = false)
    private Long participation;

    @Column(name = "artemis_team_id")
    private Long team;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @ManyToOne
    @JoinColumn(name = "tutor_id")
    private Tutor tutor;

    @Column(name = "name")
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "repository_url", nullable = false)
    private String repositoryUrl;

    @Column(name = "submission_count")
    private Integer submissionCount;

    @Column(name = "cqi")
    private Double cqi;

    @Column(name = "is_suspicious")
    private Boolean isSuspicious;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status")
    private AnalysisStatus analysisStatus;

    // CQI component fields for persistence
    @Column(name = "cqi_effort_balance")
    private Double cqiEffortBalance;

    @Column(name = "cqi_loc_balance")
    private Double cqiLocBalance;

    @Column(name = "cqi_temporal_spread")
    private Double cqiTemporalSpread;

    @Column(name = "cqi_ownership_spread")
    private Double cqiOwnershipSpread;

    @Column(name = "cqi_pair_programming")
    private Double cqiPairProgramming;

    @Column(name = "cqi_pair_programming_status")
    private String cqiPairProgrammingStatus;

    @Column(name = "cqi_base_score")
    private Double cqiBaseScore;

    @Column(name = "cqi_penalty_multiplier")
    private Double cqiPenaltyMultiplier;

    @Column(name = "cqi_penalties", columnDefinition = "TEXT")
    private String cqiPenalties;

    @Column(name = "llm_calls")
    private Long llmCalls;

    @Column(name = "llm_calls_with_usage")
    private Long llmCallsWithUsage;

    @Column(name = "llm_prompt_tokens")
    private Long llmPromptTokens;

    @Column(name = "llm_completion_tokens")
    private Long llmCompletionTokens;

    @Column(name = "llm_total_tokens")
    private Long llmTotalTokens;

    public TeamParticipation(Long participation, Long team, Tutor tutor, String name, String shortName,
            String repositoryUrl, Integer submissionCount) {
        this.participation = participation;
        this.team = team;
        this.tutor = tutor;
        this.name = name;
        this.shortName = shortName;
        this.repositoryUrl = repositoryUrl;
        this.submissionCount = submissionCount;
    }

    public TeamParticipation(Long participation, Long team, Tutor tutor, String name, String shortName,
            String repositoryUrl, Integer submissionCount, Double cqi, Boolean isSuspicious) {
        this.participation = participation;
        this.team = team;
        this.tutor = tutor;
        this.name = name;
        this.shortName = shortName;
        this.repositoryUrl = repositoryUrl;
        this.submissionCount = submissionCount;
        this.cqi = cqi;
        this.isSuspicious = isSuspicious;
    }
}
