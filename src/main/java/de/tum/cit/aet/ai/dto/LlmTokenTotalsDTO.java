package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated LLM token usage counters.
 *
 * @param llmCalls         number of LLM calls
 * @param callsWithUsage   number of calls with provider usage metadata
 * @param promptTokens     total input tokens
 * @param completionTokens total output tokens
 * @param totalTokens      total tokens
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LlmTokenTotalsDTO(
        long llmCalls,
        long callsWithUsage,
        long promptTokens,
        long completionTokens,
        long totalTokens) {

    /**
     * Creates empty token counters.
     *
     * @return empty totals
     */
    public static LlmTokenTotalsDTO empty() {
        return new LlmTokenTotalsDTO(0, 0, 0, 0, 0);
    }

    /**
     * Adds one usage sample to this aggregate and returns the new aggregate.
     *
     * @param usage usage sample from one LLM call
     * @return updated totals
     */
    public LlmTokenTotalsDTO addUsage(LlmTokenUsageDTO usage) {
        if (usage == null) {
            return this;
        }

        return new LlmTokenTotalsDTO(
                llmCalls + 1,
                callsWithUsage + (usage.usageAvailable() ? 1 : 0),
                promptTokens + usage.promptTokens(),
                completionTokens + usage.completionTokens(),
                totalTokens + usage.totalTokens());
    }

    /**
     * Merges another aggregate into this one and returns the result.
     *
     * @param other other aggregate
     * @return merged totals
     */
    public LlmTokenTotalsDTO merge(LlmTokenTotalsDTO other) {
        if (other == null) {
            return this;
        }

        return new LlmTokenTotalsDTO(
                llmCalls + other.llmCalls(),
                callsWithUsage + other.callsWithUsage(),
                promptTokens + other.promptTokens(),
                completionTokens + other.completionTokens(),
                totalTokens + other.totalTokens());
    }
}
