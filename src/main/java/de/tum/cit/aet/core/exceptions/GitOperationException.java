package de.tum.cit.aet.core.exceptions;

/**
 * Custom exception for errors occurring during Git operations.
 * This class provides a more specific way to catch and handle failures
 * related to cloning, pulling, or other Git functionality.
 */
public class GitOperationException extends RuntimeException {

    /**
     * Constructs a new GitOperationException with the specified detail message.
     *
     * @param message the detail message.
     */
    public GitOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a new GitOperationException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     */
    public GitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
