package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;

/**
 * A commit that was filtered out with the reason.
 *
 * @param chunk   the original commit chunk that was filtered
 * @param reason  the filter reason category
 * @param details human-readable explanation of why the commit was filtered
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreFilteredCommitDTO(
        CommitChunkDTO chunk,
        FilterReason reason,
        String details) {
}
