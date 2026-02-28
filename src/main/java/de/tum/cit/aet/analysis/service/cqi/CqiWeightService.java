package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.repository.CqiWeightConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CqiWeightService {

    private final CqiWeightConfigurationRepository weightConfigRepository;
    private final CQIConfig cqiConfig;

    /**
     * Resolve weights for an exercise. Checks DB first, falls back to application.yml defaults.
     *
     * @param exerciseId the exercise ID to resolve weights for, or null for defaults
     * @return the resolved weights configuration
     */
    public CQIConfig.Weights getWeightsForExercise(Long exerciseId) {
        if (exerciseId == null) {
            return cqiConfig.getWeights();
        }
        return weightConfigRepository.findByExerciseId(exerciseId)
                .map(config -> {
                    CQIConfig.Weights w = new CQIConfig.Weights();
                    w.setEffort(config.getEffortWeight());
                    w.setLoc(config.getLocWeight());
                    w.setTemporal(config.getTemporalWeight());
                    w.setOwnership(config.getOwnershipWeight());
                    return w;
                })
                .orElse(cqiConfig.getWeights());
    }
}
