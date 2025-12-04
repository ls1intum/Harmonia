package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request for anomaly detection analysis.
 *
 * @param teamId team identifier
 * @param commits list of commit summaries
 * @param assignmentStart assignment start date
 * @param assignmentEnd assignment end date (deadline)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnomalyDetectionRequestDTO(
    String teamId,
    List<CommitSummary> commits,
    LocalDateTime assignmentStart,
    LocalDateTime assignmentEnd
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitSummary(String author, LocalDateTime timestamp, int linesChanged) {}
}
