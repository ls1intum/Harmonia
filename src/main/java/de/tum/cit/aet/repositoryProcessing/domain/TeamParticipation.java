package de.tum.cit.aet.repositoryProcessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
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
}
