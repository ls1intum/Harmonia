package de.tum.cit.aet.repositoryProcessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "teamrepository")
public class TeamRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "team_repository_id", nullable = false)
    private UUID teamRepositoryId;

    @OneToOne
    @JoinColumn(name = "participation_id", nullable = false)
    private TeamParticipation teamParticipation;

    @OneToMany(mappedBy = "teamRepository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VCSLog> vcsLogs;

    @Column(name = "local_path")
    private String localPath;

    @Column(name = "is_cloned")
    private Boolean isCloned;

    @Column(name = "error")
    private String error;


    public TeamRepository(TeamParticipation teamParticipation, List<VCSLog> vcsLogs ,String localPath, Boolean isCloned, String error) {
        this.teamParticipation = teamParticipation;
        this.vcsLogs = vcsLogs;
        this.localPath = localPath;
        this.isCloned = isCloned;
        this.error = error;
    }
}
