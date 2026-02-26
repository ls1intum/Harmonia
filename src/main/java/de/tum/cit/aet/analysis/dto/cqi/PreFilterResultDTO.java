package de.tum.cit.aet.analysis.dto.cqi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.ai.dto.CommitChunkDTO;

import java.util.List;

/**
 * Result of the pre-filtering process.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreFilterResultDTO(
        List<CommitChunkDTO> chunksToAnalyze,
        List<PreFilteredCommitDTO> filteredChunks,
        FilterSummaryDTO summary) {
}
