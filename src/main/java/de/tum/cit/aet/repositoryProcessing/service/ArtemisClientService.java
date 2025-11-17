package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Service responsible for communicating with the Artemis API.
 * Handles authentication and fetching of participation data.
 */
@Service
@Slf4j
public class ArtemisClientService {

    private final ArtemisConfig artemisConfig;
    private final RestClient restClient;

    @Autowired
    public ArtemisClientService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
        restClient = RestClient.builder()
                .baseUrl(artemisConfig.getBaseUrl())
                .defaultHeader("Cookie", "jwt=" + artemisConfig.getJwtToken())
                .build();
    }

    /**
     * Fetches all participations for the configured exercise from Artemis.
     *
     * @return List of participation DTOs containing team and repository information
     */
    public List<ParticipationDTO> fetchParticipations() {
        log.info("Fetching participations for exercise ID: {}", artemisConfig.getExerciseId());

        String uri = String.format("/exercise/exercises/%d/participations?withLatestResults=false",
                artemisConfig.getExerciseId());

        try {
            List<ParticipationDTO> participations = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            log.info("Successfully fetched {} participations",
                    participations != null ? participations.size() : 0);

            return participations;

        } catch (Exception e) {
            log.error("Error fetching participations from Artemis", e);
            throw new RuntimeException("Failed to fetch participations from Artemis", e);
        }
    }
}
