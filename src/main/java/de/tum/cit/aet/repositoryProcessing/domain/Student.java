package de.tum.cit.aet.repositoryProcessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
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

}
