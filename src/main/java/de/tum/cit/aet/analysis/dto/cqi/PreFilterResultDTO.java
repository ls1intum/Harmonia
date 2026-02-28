package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;

import java.util.List;

/**
 * Result of the pre-filtering process.
 *
 * @param chunksToAnalyze chunks that passed filtering and will be sent to AI
 * @param filteredChunks  chunks that were filtered out with their reasons
 * @param summary         aggregated filter statistics
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreFilterResultDTO(
        List<CommitChunkDTO> chunksToAnalyze,
        List<PreFilteredCommitDTO> filteredChunks,
        FilterSummaryDTO summary) {
}
