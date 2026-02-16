package de.tum.cit.aet.repositoryProcessing.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Artemis API tutorial group session response.
 * Represents a tutorial group session, which includes the date when pair programming tutorials occur.
 *
 * @param id The unique identifier of the session.
 * @param start The start time of the tutorial session.
 * @param end The end time of the tutorial session.
 * @param location Optional location where the session takes place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TutorialGroupSessionDTO(
        Long id,
        @JsonProperty("start") Instant start,
        @JsonProperty("end") Instant end,
        String location
) {
}
