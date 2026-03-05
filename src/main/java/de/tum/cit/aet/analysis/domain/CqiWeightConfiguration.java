package de.tum.cit.aet.analysis.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cqi_weight_configurations")
public class CqiWeightConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exercise_id", nullable = false, unique = true)
    private Long exerciseId;

    @Column(name = "effort_weight", nullable = false)
    private double effortWeight = 0.55;

    @Column(name = "loc_weight", nullable = false)
    private double locWeight = 0.25;

    @Column(name = "temporal_weight", nullable = false)
    private double temporalWeight = 0.05;

    @Column(name = "ownership_weight", nullable = false)
    private double ownershipWeight = 0.15;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CqiWeightConfiguration(Long exerciseId, double effortWeight, double locWeight, double temporalWeight, double ownershipWeight) {
        this.exerciseId = exerciseId;
        this.effortWeight = effortWeight;
        this.locWeight = locWeight;
        this.temporalWeight = temporalWeight;
        this.ownershipWeight = ownershipWeight;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
