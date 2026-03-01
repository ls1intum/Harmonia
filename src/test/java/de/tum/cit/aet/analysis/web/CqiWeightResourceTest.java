package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.CqiWeightConfiguration;
import de.tum.cit.aet.analysis.repository.CqiWeightConfigurationRepository;
import de.tum.cit.aet.analysis.service.cqi.CQIConfig;
import de.tum.cit.aet.analysis.web.CqiWeightResource.CqiWeightsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqiWeightResourceTest {

    @Mock
    private CqiWeightConfigurationRepository weightConfigRepository;

    @Mock
    private CQIConfig cqiConfig;

    @InjectMocks
    private CqiWeightResource resource;

    private CQIConfig.Weights defaultWeights() {
        CQIConfig.Weights w = new CQIConfig.Weights();
        w.setEffort(0.55);
        w.setLoc(0.25);
        w.setTemporal(0.05);
        w.setOwnership(0.15);
        return w;
    }

    // --- GET ---

    @Test
    void getWeights_customConfigExists_returnsCustomWithIsDefaultFalse() {
        CqiWeightConfiguration config = new CqiWeightConfiguration(1L, 0.4, 0.3, 0.2, 0.1);
        when(weightConfigRepository.findByExerciseId(1L)).thenReturn(Optional.of(config));

        ResponseEntity<CqiWeightsDTO> response = resource.getWeights(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CqiWeightsDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(0.4, body.effortBalance());
        assertEquals(0.3, body.locBalance());
        assertEquals(0.2, body.temporalSpread());
        assertEquals(0.1, body.ownershipSpread());
        assertFalse(body.isDefault());
    }

    @Test
    void getWeights_noConfigExists_returnsDefaultsWithIsDefaultTrue() {
        when(weightConfigRepository.findByExerciseId(1L)).thenReturn(Optional.empty());
        when(cqiConfig.getWeights()).thenReturn(defaultWeights());

        ResponseEntity<CqiWeightsDTO> response = resource.getWeights(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CqiWeightsDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(0.55, body.effortBalance());
        assertEquals(0.25, body.locBalance());
        assertEquals(0.05, body.temporalSpread());
        assertEquals(0.15, body.ownershipSpread());
        assertTrue(body.isDefault());
    }

    // --- PUT ---

    @Test
    void saveWeights_validWeights_savesAndReturnsWithIsDefaultFalse() {
        CqiWeightsDTO request = new CqiWeightsDTO(0.4, 0.3, 0.2, 0.1, false);
        when(weightConfigRepository.findByExerciseId(1L)).thenReturn(Optional.empty());
        when(weightConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = resource.saveWeights(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CqiWeightsDTO body = (CqiWeightsDTO) response.getBody();
        assertNotNull(body);
        assertEquals(0.4, body.effortBalance());
        assertEquals(0.3, body.locBalance());
        assertEquals(0.2, body.temporalSpread());
        assertEquals(0.1, body.ownershipSpread());
        assertFalse(body.isDefault());
        verify(weightConfigRepository).save(any());
    }

    @Test
    void saveWeights_negativeWeight_returns400() {
        CqiWeightsDTO request = new CqiWeightsDTO(-0.1, 0.5, 0.3, 0.3, false);

        ResponseEntity<?> response = resource.saveWeights(1L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("All weights must be non-negative", response.getBody());
        verify(weightConfigRepository, never()).save(any());
    }

    @Test
    void saveWeights_weightsDontSumToOne_returns400() {
        CqiWeightsDTO request = new CqiWeightsDTO(0.5, 0.3, 0.2, 0.2, false);

        ResponseEntity<?> response = resource.saveWeights(1L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String body = (String) response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Weights must sum to 100%"));
        verify(weightConfigRepository, never()).save(any());
    }

    @Test
    void saveWeights_noExistingConfig_createsNewEntity() {
        CqiWeightsDTO request = new CqiWeightsDTO(0.4, 0.3, 0.2, 0.1, false);
        when(weightConfigRepository.findByExerciseId(42L)).thenReturn(Optional.empty());
        when(weightConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        resource.saveWeights(42L, request);

        ArgumentCaptor<CqiWeightConfiguration> captor = ArgumentCaptor.forClass(CqiWeightConfiguration.class);
        verify(weightConfigRepository).save(captor.capture());
        CqiWeightConfiguration saved = captor.getValue();
        assertEquals(42L, saved.getExerciseId());
        assertEquals(0.4, saved.getEffortWeight());
    }

    @Test
    void saveWeights_existingConfig_updatesEntity() {
        CqiWeightConfiguration existing = new CqiWeightConfiguration(1L, 0.55, 0.25, 0.05, 0.15);
        when(weightConfigRepository.findByExerciseId(1L)).thenReturn(Optional.of(existing));
        when(weightConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqiWeightsDTO request = new CqiWeightsDTO(0.4, 0.3, 0.2, 0.1, false);
        resource.saveWeights(1L, request);

        verify(weightConfigRepository).save(existing);
        assertEquals(0.4, existing.getEffortWeight());
        assertEquals(0.3, existing.getLocWeight());
        assertEquals(0.2, existing.getTemporalWeight());
        assertEquals(0.1, existing.getOwnershipWeight());
    }

    // --- DELETE ---

    @Test
    void resetWeights_deletesConfigAndReturnsDefaults() {
        when(cqiConfig.getWeights()).thenReturn(defaultWeights());

        ResponseEntity<CqiWeightsDTO> response = resource.resetWeights(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(weightConfigRepository).deleteByExerciseId(1L);
        CqiWeightsDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(0.55, body.effortBalance());
        assertTrue(body.isDefault());
    }

    @Test
    void resetWeights_noConfigExisted_stillReturnsDefaults() {
        when(cqiConfig.getWeights()).thenReturn(defaultWeights());

        ResponseEntity<CqiWeightsDTO> response = resource.resetWeights(99L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(weightConfigRepository).deleteByExerciseId(99L);
        assertTrue(response.getBody().isDefault());
    }
}
