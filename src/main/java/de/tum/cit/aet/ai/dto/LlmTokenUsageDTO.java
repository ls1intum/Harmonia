package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Token usage for a single LLM call.
 *
 * @param model           model identifier reported by provider/config
 * @param promptTokens    input tokens
 * @param completionTokens output tokens
 * @param totalTokens     total tokens (input + output)
 * @param usageAvailable  true when provider returned usage metadata
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LlmTokenUsageDTO(
        String model,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        boolean usageAvailable) {

    /**
     * Creates an unavailable usage value (no provider usage metadata).
     *
     * @param model model identifier
     * @return usage with zero token counts and usageAvailable=false
     */
    public static LlmTokenUsageDTO unavailable(String model) {
        return new LlmTokenUsageDTO(model, 0, 0, 0, false);
    }
}
