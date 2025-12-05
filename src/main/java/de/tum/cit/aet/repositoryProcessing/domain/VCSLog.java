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
@Table(name = "vcslogs")
public class VCSLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vcs_log_id", nullable = false)
    private UUID vcsLogId;

    @ManyToOne
    @JoinColumn(name = "team_repository_id", nullable = false)
    private TeamRepository teamRepository;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "email")
    private String email;

    public VCSLog(TeamRepository teamRepository, String commitHash, String email) {
        this.teamRepository = teamRepository;
        this.commitHash = commitHash;
        this.email = email;
    }
}
