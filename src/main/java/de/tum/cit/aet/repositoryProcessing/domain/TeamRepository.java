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

}
