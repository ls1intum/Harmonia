package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A commit chunk paired with its LLM effort rating and token usage.
 *
 * @param chunk      the original commit chunk
 * @param rating     LLM effort rating result
 * @param tokenUsage token usage metadata for this rating call
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RatedChunkDTO(CommitChunkDTO chunk, EffortRatingDTO rating, LlmTokenUsageDTO tokenUsage) {
}
