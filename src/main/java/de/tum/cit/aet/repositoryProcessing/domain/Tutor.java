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
@Table(name = "tutors")
public class Tutor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(name = "artemis_tutor_id")
    private Long id;

    @Column(name = "login")
    private String login;

    @Column(name = "name")
    private String name;

    public Tutor(Long id, String login, String name) {
        this.id = id;
        this.login = login;
        this.name = name;
    }
}
