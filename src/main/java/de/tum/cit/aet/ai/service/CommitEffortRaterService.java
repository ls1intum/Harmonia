package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.ai.dto.LlmTokenUsageDTO;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;

/**
 * Rates the effort of a commit chunk using an LLM.
 * Analyses diff content to determine effort, complexity, novelty and commit type.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommitEffortRaterService {

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.7;
    private static final int MAX_DIFF_CHARS = 10_000;

    private static final String EFFORT_RATING_PROMPT = """
            Analyze the following git commit chunk and rate the EFFORT required to implement it.

            Context:
            - Commit Message: %s
            - Files Changed: %s
            - Lines Added: %d
            - Lines Deleted: %d

            Diff Content:
            %s

            Task:
            Rate the following aspects on a scale of 1-10:
            1. effort_score: How much work did this take? (1=Trivial typo, 10=Massive architectural change)
            2. complexity: How technically complex is the change? (patterns, algorithms, logic)
            3. novelty: Is this original work? (1=Copy-paste/Generated, 10=Highly original)

            Also classify the type of change (FEATURE, BUG_FIX, TEST, REFACTOR, TRIVIAL).
            Provide a VERY SHORT reasoning (max 10 words).

            Return ONLY a valid JSON object matching this structure (no markdown, no explanation):
            {"effortScore": 5.0, "complexity": 5.0, "novelty": 5.0, "type": "FEATURE", "confidence": 0.9, "reasoning": "Short reason."}
            """;

    /**
     * Combined result containing effort rating plus token usage.
     *
     * @param rating     parsed effort rating
     * @param tokenUsage usage metadata for this LLM call
     */
    public record RatingWithUsage(EffortRatingDTO rating, LlmTokenUsageDTO tokenUsage) {
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Rates a commit chunk and returns only the rating.
     *
     * @param chunk the commit chunk to analyse
     * @return effort rating
     * @throws InterruptedException if the analysis is cancelled
     */
    public EffortRatingDTO rateChunk(CommitChunkDTO chunk) throws InterruptedException {
        return rateChunkWithUsage(chunk).rating();
    }

    /**
     * Rates a commit chunk and returns both rating and token usage metadata.
     *
     * @param chunk the commit chunk to analyse
     * @return rating plus token usage
     * @throws InterruptedException if the analysis is cancelled
     */
    public RatingWithUsage rateChunkWithUsage(CommitChunkDTO chunk) throws InterruptedException {
        AiProperties.CommitClassifier config = aiProperties.getCommitClassifier();

        // 1) Check cancellation before doing any work
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Analysis cancelled before rating chunk");
        }

        // 2) Return disabled rating when AI is off
        if (!aiProperties.isEnabled() || config == null || !config.isEnabled()) {
            return new RatingWithUsage(EffortRatingDTO.disabled(),
                    LlmTokenUsageDTO.unavailable(resolveModel()));
        }

        try {
            // 3) Build prompt and call LLM
            String promptText = buildPrompt(chunk);

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Analysis cancelled before LLM call");
            }

            ChatResponse chatResponse = chatClient.prompt()
                    .user(promptText)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(config.getModelName())
                            .build())
                    .call()
                    .chatResponse();

            LlmTokenUsageDTO tokenUsage = extractTokenUsage(chatResponse);

            // 4) Parse response
            String rawResponse = extractResponseContent(chatResponse);
            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("Empty LLM response for commit {}", chunk.commitSha());
                return new RatingWithUsage(EffortRatingDTO.trivial("Empty AI response"), tokenUsage);
            }

            EffortRatingDTO rating = parseJsonWithRepair(rawResponse, chunk.commitSha());

            if (rating.confidence() < LOW_CONFIDENCE_THRESHOLD) {
                log.warn("Low confidence ({}) for commit {}", rating.confidence(), chunk.commitSha());
            }

            return new RatingWithUsage(rating, tokenUsage);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("Failed to rate commit {}: {}", chunk.commitSha(), e.getMessage());
            return new RatingWithUsage(
                    EffortRatingDTO.trivial("Error during AI analysis: " + e.getMessage()),
                    LlmTokenUsageDTO.unavailable(resolveModel()));
        }
    }

    // ── Prompt building ──────────────────────────────────────────────────

    private String buildPrompt(CommitChunkDTO chunk) {
        return EFFORT_RATING_PROMPT.formatted(
                chunk.commitMessage(),
                String.join(", ", chunk.files()),
                chunk.linesAdded(),
                chunk.linesDeleted(),
                truncateDiff(chunk.diffContent()));
    }

    // ── Response extraction ──────────────────────────────────────────────

    private String extractResponseContent(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        Generation result = chatResponse.getResult();
        if (result == null || result.getOutput() == null) {
            return null;
        }
        return result.getOutput().getText();
    }

    private LlmTokenUsageDTO extractTokenUsage(ChatResponse chatResponse) {
        String model = resolveModel();
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return LlmTokenUsageDTO.unavailable(model);
        }

        String providerModel = chatResponse.getMetadata().getModel();
        if (providerModel != null && !providerModel.isBlank()) {
            model = providerModel;
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return LlmTokenUsageDTO.unavailable(model);
        }

        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : promptTokens + completionTokens;

        boolean hasNonEmptyNativeUsage = false;
        if (usage.getNativeUsage() instanceof java.util.Map<?, ?> nativeMap) {
            hasNonEmptyNativeUsage = !nativeMap.isEmpty();
        } else if (usage.getNativeUsage() != null) {
            hasNonEmptyNativeUsage = true;
        }
        boolean usageAvailable = !(usage instanceof EmptyUsage)
                && (hasNonEmptyNativeUsage || promptTokens > 0 || completionTokens > 0 || totalTokens > 0);

        return new LlmTokenUsageDTO(model, promptTokens, completionTokens, totalTokens, usageAvailable);
    }

    private String resolveModel() {
        if (aiProperties == null || aiProperties.getCommitClassifier() == null) {
            return "unknown";
        }
        String configured = aiProperties.getCommitClassifier().getModelName();
        return (configured != null && !configured.isBlank()) ? configured : "unknown";
    }

    // ── JSON parsing with repair ─────────────────────────────────────────

    /**
     * Parses LLM JSON response, attempting repair if the string is truncated.
     */
    private EffortRatingDTO parseJsonWithRepair(String json, String commitSha) {
        // 1) Strip markdown code fences if present
        json = json.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        // 2) Try direct parse
        try {
            return parseJson(json);
        } catch (Exception e) {
            log.debug("Initial parse failed for commit {}, attempting repair", commitSha);
        }

        // 3) Attempt repair for truncated JSON
        String repairedJson = repairTruncatedJson(json);
        try {
            return parseJson(repairedJson);
        } catch (Exception e) {
            log.warn("JSON repair failed for commit {}, using default rating", commitSha);
            return EffortRatingDTO.trivial("Truncated AI response");
        }
    }

    /**
     * Closes open quotes, brackets and braces in a truncated JSON string.
     */
    private String repairTruncatedJson(String json) {
        StringBuilder repaired = new StringBuilder(json);
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }

        if (inString) repaired.append("\"");
        while (openBrackets-- > 0) repaired.append("]");
        while (openBraces-- > 0) repaired.append("}");

        return repaired.toString();
    }

    private EffortRatingDTO parseJson(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(json, EffortRatingDTO.class);
    }

    private String truncateDiff(String diff) {
        if (diff == null) {
            return "";
        }
        if (diff.length() > MAX_DIFF_CHARS) {
            return diff.substring(0, MAX_DIFF_CHARS) + "\n... (truncated)";
        }
        return diff;
    }
}
