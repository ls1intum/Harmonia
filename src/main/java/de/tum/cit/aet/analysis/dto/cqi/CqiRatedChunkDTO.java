package de.tum.cit.aet.analysis.dto.cqi;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;

/**
 * Input for CQI calculation: rated commit chunk (without token usage metadata).
 *
 * @param chunk  the original commit chunk
 * @param rating the LLM effort rating result
 */
public record CqiRatedChunkDTO(CommitChunkDTO chunk, EffortRatingDTO rating) {
}
