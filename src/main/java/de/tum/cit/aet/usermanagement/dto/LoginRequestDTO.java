package de.tum.cit.aet.usermanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted by the user to authenticate against Artemis.
 *
 * @param username  the Artemis username
 * @param password  the Artemis password
 * @param serverUrl the Artemis server URL
 * @param courseId  the course ID to authenticate for
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequestDTO(
        @NotBlank(message = "Username must not be blank") String username,
        @NotBlank(message = "Password must not be blank") String password,
        @NotBlank(message = "Server URL must not be blank") String serverUrl,
        @NotBlank(message = "Course ID must not be blank") String courseId) {
}
