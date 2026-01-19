package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.util.DtoUtils;
import java.time.LocalDateTime;

/**
 * Represents a commit that could not be attributed to any registered student
 * due to email mismatch between git commit and Artemis registration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrphanCommitDTO(
        String commitHash,
        String authorEmail,
        String authorName,
        String message,
        LocalDateTime timestamp,
        int linesAdded,
        int linesDeleted) {
    public int linesChanged() {
        return DtoUtils.calculateLinesChanged(linesAdded, linesDeleted);
    }
}
