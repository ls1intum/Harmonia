package de.tum.cit.aet.ai.dto;

/**
 * Token usage for a single LLM call.
 *
 * @param model           model identifier reported by provider/config
 * @param promptTokens    input tokens
 * @param completionTokens output tokens
 * @param totalTokens     total tokens (input + output)
 * @param usageAvailable  true when provider returned usage metadata
 */
public record LlmTokenUsage(
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
    public static LlmTokenUsage unavailable(String model) {
        return new LlmTokenUsage(model, 0, 0, 0, false);
    }
}
