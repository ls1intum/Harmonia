package de.tum.cit.aet.repositoryProcessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "teamrepository")
public class TeamRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "team_repository_id", nullable = false)
    private UUID teamRepositoryId;

    @OneToOne
    @JoinColumn(name = "participation_id", nullable = false)
    private TeamParticipation teamParticipation;

    @Column(name = "local_path")
    private String localPath;

    @Column(name = "is_cloned")
    private Boolean isCloned;

    @Column(name = "error")
    private String error;
}
