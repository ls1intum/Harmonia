package de.tum.cit.aet.analysis.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Maps an unrecognised git email to a known student within an exercise.
 * Used for manual orphan-commit resolution in the UI.
 * When {@code isDismissed} is true the email is dismissed (no student assignment).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "exercise_email_mappings")
public class ExerciseEmailMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "git_email", nullable = false)
    private String gitEmail;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "is_dismissed", nullable = false)
    private Boolean isDismissed = false;

    public ExerciseEmailMapping(Long exerciseId, String gitEmail, Long studentId, String studentName) {
        this.exerciseId = exerciseId;
        this.gitEmail = gitEmail;
        this.studentId = studentId;
        this.studentName = studentName;
        this.isDismissed = false;
    }

    public ExerciseEmailMapping(Long exerciseId, String gitEmail, boolean isDismissed) {
        this.exerciseId = exerciseId;
        this.gitEmail = gitEmail;
        this.isDismissed = isDismissed;
        this.studentId = null;
        this.studentName = null;
    }
}
