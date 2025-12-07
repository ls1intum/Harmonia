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
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "artemis_student_id")
    private Long id;

    @Column(name = "login")
    private String login;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "commit_count")
    private Integer commitCount;

    @Column(name = "lines_added")
    private Integer linesAdded;

    @Column(name  = "lines_deleted")
    private Integer linesDeleted;

    @Column(name = "lines_changed")
    private Integer linesChanged;

    @ManyToOne
    @JoinColumn(name = "teamParticipation_id")
    private TeamParticipation team;

    public Student(Long id, String login, String name, String email, TeamParticipation teamParticipation, Integer commitCount, Integer linesAdded, Integer linesDeleted, Integer linesChanged) {
        this.id = id;
        this.login = login;
        this.name = name;
        this.email = email;
        this.team = teamParticipation;
        this.commitCount = commitCount;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.linesChanged = linesChanged;
    }
}
