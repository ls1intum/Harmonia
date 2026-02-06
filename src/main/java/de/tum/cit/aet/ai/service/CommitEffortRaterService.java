package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Service for rating the effort of a commit using an LLM.
 * Analyzes commit chunks to determine effort, complexity, and novelty.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommitEffortRaterService {

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

    /**
     * Rates the effort of a specific commit chunk.
     *
     * @param chunk The commit chunk to analyze
     * @return An EffortRatingDTO containing the scores
     * @throws InterruptedException if the thread is interrupted (analysis cancelled)
     */
    public EffortRatingDTO rateChunk(CommitChunkDTO chunk) throws InterruptedException {
        // Check if cancelled before starting
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Analysis cancelled before rating chunk");
        }

        if (!aiProperties.isEnabled() || !aiProperties.getCommitClassifier().isEnabled()) {
            return EffortRatingDTO.disabled();
        }

        try {
            // Build prompt manually to avoid PromptTemplate issues with curly braces in
            // diff
            String promptText = """
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
                    """
                    .formatted(
                            chunk.commitMessage(),
                            String.join(", ", chunk.files()),
                            chunk.linesAdded(),
                            chunk.linesDeleted(),
                            truncateDiff(chunk.diffContent()));

            log.debug("Sending rating request for chunk {} of commit {}", chunk.chunkIndex(), chunk.commitSha());

            // Check again before the potentially slow LLM call
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Analysis cancelled before LLM call");
            }

            // Get raw response instead of using entity() to handle truncated JSON
            String rawResponse = chatClient.prompt()
                    .user(promptText)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(aiProperties.getCommitClassifier().getModelName())
                            .build())
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("Received empty response from LLM for commit {}", chunk.commitSha());
                return EffortRatingDTO.trivial("Empty AI response");
            }

            // Try to parse, with repair if needed
            EffortRatingDTO rating = parseJsonWithRepair(rawResponse, chunk.commitSha());

            // Treat low confidence as trivial or flagged (here we just log)
            if (rating.confidence() < DEFAULT_CONFIDENCE_THRESHOLD) {
                log.warn("Low confidence ({}) rating for commit {}: {}",
                        rating.confidence(), chunk.commitSha(), rating.reasoning());
            }

            return rating;

        } catch (InterruptedException e) {
            // Re-throw InterruptedException to allow cancellation
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("Error rating commit chunk {}: {}", chunk.commitSha(), e.getMessage());
            return EffortRatingDTO.trivial("Error during AI analysis: " + e.getMessage());
        }
    }

    /**
     * Parses JSON response from LLM with repair logic for truncated strings.
     * If the JSON is truncated (missing closing quotes/braces), attempts to repair
     * it.
     */
    private EffortRatingDTO parseJsonWithRepair(String json, String commitSha) {
        // Clean up markdown code fences if present
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

        // Try parsing as-is first
        try {
            return parseJson(json);
        } catch (Exception e) {
            log.debug("Initial parse failed for commit {}, attempting repair: {}", commitSha, e.getMessage());
        }

        // Attempt repair for truncated JSON
        String repairedJson = repairTruncatedJson(json);
        try {
            EffortRatingDTO rating = parseJson(repairedJson);
            log.info("Successfully repaired truncated JSON for commit {}", commitSha);
            return rating;
        } catch (Exception e) {
            log.warn("JSON repair failed for commit {}, using default rating. Original: {}",
                    commitSha, json.substring(0, Math.min(100, json.length())));
            return EffortRatingDTO.trivial("Truncated AI response");
        }
    }

    /**
     * Attempts to repair a truncated JSON string by closing open quotes and braces.
     */
    private String repairTruncatedJson(String json) {
        StringBuilder repaired = new StringBuilder(json);

        // Count open/close braces and quotes
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
                if (c == '{') {
                    openBraces++;
                } else if (c == '}') {
                    openBraces--;
                } else if (c == '[') {
                    openBrackets++;
                } else if (c == ']') {
                    openBrackets--;
                }
            }
        }

        // Close open string if needed
        if (inString) {
            repaired.append("\"");
        }

        // Close open brackets and braces
        while (openBrackets > 0) {
            repaired.append("]");
            openBrackets--;
        }
        while (openBraces > 0) {
            repaired.append("}");
            openBraces--;
        }

        return repaired.toString();
    }

    private EffortRatingDTO parseJson(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(json, EffortRatingDTO.class);
    }

    /**
     * Truncates diff if it's too long to fit in context (safety mechanism).
     * The ChunkerService should already handle this, but double safety is good.
     */
    private String truncateDiff(String diff) {
        if (diff == null) {
            return "";
        }
        int maxLength = 10000; // conservative character limit
        if (diff.length() > maxLength) {
            return diff.substring(0, maxLength) + "\n... (truncated)";
        }
        return diff;
    }
}
