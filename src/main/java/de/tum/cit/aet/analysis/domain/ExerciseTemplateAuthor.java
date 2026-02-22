package de.tum.cit.aet.analysis.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Stores the detected or manually configured template author email for an exercise.
 * All commits from this author are excluded from CQI calculation and orphan counts.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "exercise_template_authors")
public class ExerciseTemplateAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exercise_id", nullable = false, unique = true)
    private Long exerciseId;

    @Column(name = "template_email", nullable = false)
    private String templateEmail;

    @Column(name = "auto_detected")
    private Boolean autoDetected;

    public ExerciseTemplateAuthor(Long exerciseId, String templateEmail, Boolean autoDetected) {
        this.exerciseId = exerciseId;
        this.templateEmail = templateEmail;
        this.autoDetected = autoDetected;
    }
}
