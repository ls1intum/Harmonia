package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;

/**
 * A commit that was filtered out with the reason.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreFilteredCommitDTO(
        CommitChunkDTO chunk,
        FilterReason reason,
        String details) {
}
