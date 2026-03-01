package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Collection of rated chunks with aggregated token usage totals.
 *
 * @param ratedChunks rated chunks
 * @param tokenTotals aggregated token usage across all rating calls
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RatedChunksWithUsageDTO(List<RatedChunkDTO> ratedChunks, LlmTokenTotalsDTO tokenTotals) {
}
