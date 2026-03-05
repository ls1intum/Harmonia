package de.tum.cit.aet.analysis.service.cqi;

import de.tum.cit.aet.analysis.domain.CqiWeightConfiguration;
import de.tum.cit.aet.analysis.repository.CqiWeightConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqiWeightServiceTest {

    @Mock
    private CqiWeightConfigurationRepository weightConfigRepository;

    private CQIConfig cqiConfig;
    private CqiWeightService service;

    @BeforeEach
    void setUp() {
        cqiConfig = new CQIConfig();
        service = new CqiWeightService(weightConfigRepository, cqiConfig);
    }

    @Test
    void getWeightsForExercise_dbEntryExists_returnsDbWeights() {
        CqiWeightConfiguration config = new CqiWeightConfiguration(1L, 0.4, 0.3, 0.2, 0.1);
        when(weightConfigRepository.findByExerciseId(1L)).thenReturn(Optional.of(config));

        CQIConfig.Weights result = service.getWeightsForExercise(1L);

        assertEquals(0.4, result.getEffort());
        assertEquals(0.3, result.getLoc());
        assertEquals(0.2, result.getTemporal());
        assertEquals(0.1, result.getOwnership());
    }

    @Test
    void getWeightsForExercise_nullExerciseId_returnsDefaults() {
        CQIConfig.Weights result = service.getWeightsForExercise(null);

        assertEquals(0.55, result.getEffort());
        assertEquals(0.25, result.getLoc());
        assertEquals(0.05, result.getTemporal());
        assertEquals(0.15, result.getOwnership());
        verifyNoInteractions(weightConfigRepository);
    }

    @Test
    void getWeightsForExercise_noDbEntry_returnsDefaults() {
        when(weightConfigRepository.findByExerciseId(99L)).thenReturn(Optional.empty());

        CQIConfig.Weights result = service.getWeightsForExercise(99L);

        assertEquals(0.55, result.getEffort());
        assertEquals(0.25, result.getLoc());
        assertEquals(0.05, result.getTemporal());
        assertEquals(0.15, result.getOwnership());
    }

    @Test
    void getWeightsForExercise_dbEntry_mapsAllFieldsCorrectly() {
        CqiWeightConfiguration config = new CqiWeightConfiguration(5L, 0.1, 0.2, 0.3, 0.4);
        when(weightConfigRepository.findByExerciseId(5L)).thenReturn(Optional.of(config));

        CQIConfig.Weights result = service.getWeightsForExercise(5L);

        assertEquals(config.getEffortWeight(), result.getEffort());
        assertEquals(config.getLocWeight(), result.getLoc());
        assertEquals(config.getTemporalWeight(), result.getTemporal());
        assertEquals(config.getOwnershipWeight(), result.getOwnership());
    }
}
