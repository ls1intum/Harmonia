package de.tum.cit.aet.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO holding Artemis authentication credentials.
 * Used to pass credentials through the application layers cleanly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtemisCredentials(
        String serverUrl,
        String jwtToken,
        String username,
        String password
) {
    /**
     * Checks if the credentials contain valid authentication data.
     *
     * @return true if serverUrl and jwtToken are present
     */
    public boolean isValid() {
        return serverUrl != null && !serverUrl.isBlank()
                && jwtToken != null && !jwtToken.isBlank();
    }

    /**
     * Checks if username/password credentials are available for Git operations.
     *
     * @return true if both username and password are present
     */
    public boolean hasGitCredentials() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
