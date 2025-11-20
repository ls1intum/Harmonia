package de.tum.cit.aet.core.exceptions;

/**
 * Custom exception for errors occurring during communication with the Artemis API.
 * This class provides a more specific way to catch and handle external service failures
 * related to the Artemis client functionality.
 */
public class ArtemisConnectionException extends RuntimeException {

    /**
     * Constructs a new ArtemisConnectionException with the specified detail message.
     *
     * @param message the detail message.
     */
    public ArtemisConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new ArtemisClientException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     */
    public ArtemisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
