package de.tum.cit.aet.repositoryProcessing.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Artemis API tutorial group session response.
 *
 * @param id        the unique session identifier
 * @param start     the session start time
 * @param end       the session end time
 * @param location  the session location (optional)
 * @param cancelled whether the session was cancelled
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TutorialGroupSessionDTO(
        Long id,
        @JsonProperty("start") OffsetDateTime start,
        @JsonProperty("end") OffsetDateTime end,
        String location,
        boolean cancelled
) {
}
