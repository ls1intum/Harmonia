package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.CqiWeightConfiguration;
import de.tum.cit.aet.analysis.repository.CqiWeightConfigurationRepository;
import de.tum.cit.aet.analysis.service.cqi.CQIConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercises/{exerciseId}/cqi-weights")
@Slf4j
@RequiredArgsConstructor
public class CqiWeightResource {

    private final CqiWeightConfigurationRepository weightConfigRepository;
    private final CQIConfig cqiConfig;

    public record CqiWeightsDTO(
            double effortBalance,
            double locBalance,
            double temporalSpread,
            double ownershipSpread,
            boolean isDefault
    ) {}

    @GetMapping
    public ResponseEntity<CqiWeightsDTO> getWeights(@PathVariable Long exerciseId) {
        return weightConfigRepository.findByExerciseId(exerciseId)
                .map(config -> ResponseEntity.ok(new CqiWeightsDTO(
                        config.getEffortWeight(), config.getLocWeight(),
                        config.getTemporalWeight(), config.getOwnershipWeight(), false)))
                .orElseGet(() -> {
                    CQIConfig.Weights w = cqiConfig.getWeights();
                    return ResponseEntity.ok(new CqiWeightsDTO(
                            w.getEffort(), w.getLoc(), w.getTemporal(), w.getOwnership(), true));
                });
    }

    @PutMapping
    @Transactional
    public ResponseEntity<CqiWeightsDTO> saveWeights(
            @PathVariable Long exerciseId,
            @RequestBody CqiWeightsDTO request) {

        double sum = request.effortBalance() + request.locBalance()
                + request.temporalSpread() + request.ownershipSpread();
        if (Math.abs(sum - 1.0) >= 0.001) {
            return ResponseEntity.badRequest().build();
        }
        if (request.effortBalance() < 0 || request.locBalance() < 0
                || request.temporalSpread() < 0 || request.ownershipSpread() < 0) {
            return ResponseEntity.badRequest().build();
        }

        CqiWeightConfiguration config = weightConfigRepository.findByExerciseId(exerciseId)
                .orElse(new CqiWeightConfiguration());
        config.setExerciseId(exerciseId);
        config.setEffortWeight(request.effortBalance());
        config.setLocWeight(request.locBalance());
        config.setTemporalWeight(request.temporalSpread());
        config.setOwnershipWeight(request.ownershipSpread());
        weightConfigRepository.save(config);

        log.info("Saved CQI weights for exercise {}: effort={}, loc={}, temporal={}, ownership={}",
                exerciseId, config.getEffortWeight(), config.getLocWeight(),
                config.getTemporalWeight(), config.getOwnershipWeight());

        return ResponseEntity.ok(new CqiWeightsDTO(
                config.getEffortWeight(), config.getLocWeight(),
                config.getTemporalWeight(), config.getOwnershipWeight(), false));
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<CqiWeightsDTO> resetWeights(@PathVariable Long exerciseId) {
        weightConfigRepository.deleteByExerciseId(exerciseId);
        CQIConfig.Weights w = cqiConfig.getWeights();

        log.info("Reset CQI weights to defaults for exercise {}", exerciseId);

        return ResponseEntity.ok(new CqiWeightsDTO(
                w.getEffort(), w.getLoc(), w.getTemporal(), w.getOwnership(), true));
    }
}
