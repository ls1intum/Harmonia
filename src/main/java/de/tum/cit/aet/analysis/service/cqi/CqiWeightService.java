package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.domain.CqiWeightConfiguration;
import de.tum.cit.aet.analysis.dto.cqi.CqiWeightsDTO;
import de.tum.cit.aet.analysis.repository.CqiWeightConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing per-exercise CQI weight configurations.
 */
@Service
@RequiredArgsConstructor
public class CqiWeightService {

    private final CqiWeightConfigurationRepository weightConfigRepository;
    private final CQIConfig cqiConfig;

    /**
     * Resolves weights for an exercise. Checks the database first, falls back to application defaults.
     *
     * @param exerciseId the exercise ID, or {@code null} for defaults
     * @return the resolved weights
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

    /**
     * Returns the CQI weights for an exercise, falling back to defaults if none are configured.
     *
     * @param exerciseId the exercise ID
     * @return weights DTO with {@code isDefault} indicating whether defaults are used
     */
    public CqiWeightsDTO getWeights(Long exerciseId) {
        return weightConfigRepository.findByExerciseId(exerciseId)
                .map(config -> new CqiWeightsDTO(
                        config.getEffortWeight(), config.getLocWeight(),
                        config.getTemporalWeight(), config.getOwnershipWeight(), false))
                .orElseGet(() -> {
                    CQIConfig.Weights w = cqiConfig.getWeights();
                    return new CqiWeightsDTO(w.getEffort(), w.getLoc(), w.getTemporal(), w.getOwnership(), true);
                });
    }

    /**
     * Saves custom CQI weights for an exercise.
     *
     * @param exerciseId the exercise ID
     * @param request    the weights to save (must be non-negative and sum to 1.0)
     * @return the saved weights DTO
     * @throws IllegalArgumentException if the weights are invalid
     */
    @Transactional
    public CqiWeightsDTO saveWeights(Long exerciseId, CqiWeightsDTO request) {
        if (request.effortBalance() < 0 || request.locBalance() < 0
                || request.temporalSpread() < 0 || request.ownershipSpread() < 0) {
            throw new IllegalArgumentException("All weights must be non-negative");
        }
        double sum = request.effortBalance() + request.locBalance()
                + request.temporalSpread() + request.ownershipSpread();
        if (Math.abs(sum - 1.0) >= 0.001) {
            throw new IllegalArgumentException("Weights must sum to 100% (got " + Math.round(sum * 100) + "%)");
        }

        CqiWeightConfiguration config = weightConfigRepository.findByExerciseId(exerciseId)
                .orElseGet(() -> new CqiWeightConfiguration(exerciseId,
                        request.effortBalance(), request.locBalance(),
                        request.temporalSpread(), request.ownershipSpread()));
        config.setEffortWeight(request.effortBalance());
        config.setLocWeight(request.locBalance());
        config.setTemporalWeight(request.temporalSpread());
        config.setOwnershipWeight(request.ownershipSpread());
        weightConfigRepository.save(config);

        return new CqiWeightsDTO(
                config.getEffortWeight(), config.getLocWeight(),
                config.getTemporalWeight(), config.getOwnershipWeight(), false);
    }

    /**
     * Resets CQI weights for an exercise back to application defaults.
     *
     * @param exerciseId the exercise ID
     * @return the default weights DTO
     */
    @Transactional
    public CqiWeightsDTO resetWeights(Long exerciseId) {
        weightConfigRepository.deleteByExerciseId(exerciseId);
        CQIConfig.Weights w = cqiConfig.getWeights();
        return new CqiWeightsDTO(w.getEffort(), w.getLoc(), w.getTemporal(), w.getOwnership(), true);
    }
}
