package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    // We can reuse the classifier's configuration for now, or add a specific one
    // later
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

    private static final String RATING_PROMPT = """
            Analyze the following git commit chunk and rate the EFFORT required to implement it.

            Context:
            - Commit Message: {message}
            - Files Changed: {files}
            - Lines Added: {added}
            - Lines Deleted: {deleted}

            Diff Content:
            {diff}

            Task:
            Rate the following aspects on a scale of 1-10:
            1. effort_score: How much work did this take? (1=Trivial typo, 10=Massive architectural change)
            2. complexity: How technically complex is the change? (patterns, algorithms, logic)
            3. novelty: Is this original work? (1=Copy-paste/Generated, 10=Highly original)

            Also classify the type of change (FEATURE, BUG_FIX, TEST, REFACTOR, TRIVIAL).
            Provide a short reasoning (max 1 sentence).

            Return the result strictly as a valid JSON object matching this structure:
            {
              "effortScore": 5.0,
              "complexity": 5.0,
              "novelty": 5.0,
              "type": "FEATURE",
              "confidence": 0.9,
              "reasoning": "Standard controller implementation."
            }
            """;

    /**
     * Rates the effort of a specific commit chunk.
     *
     * @param chunk The commit chunk to analyze
     * @return An EffortRatingDTO containing the scores
     */
    public EffortRatingDTO rateChunk(CommitChunkDTO chunk) {
        if (!aiProperties.isEnabled() || !aiProperties.getCommitClassifier().isEnabled()) {
            return EffortRatingDTO.disabled();
        }

        try {
            PromptTemplate promptTemplate = new PromptTemplate(RATING_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of(
                    "message", chunk.commitMessage(),
                    "files", String.join(", ", chunk.files()),
                    "added", chunk.linesAdded(),
                    "deleted", chunk.linesDeleted(),
                    "diff", truncateDiff(chunk.diffContent())));

            log.debug("Sending rating request for chunk {} of commit {}", chunk.chunkIndex(), chunk.commitSha());

            EffortRatingDTO rating = chatClient.prompt(prompt)
                    .call()
                    .entity(new ParameterizedTypeReference<EffortRatingDTO>() {
                    });

            if (rating == null) {
                log.warn("Received null rating from LLM for commit {}", chunk.commitSha());
                return EffortRatingDTO.trivial("Failed to parse AI response");
            }

            // Treat low confidence as trivial or flagged (here we just log)
            if (rating.confidence() < DEFAULT_CONFIDENCE_THRESHOLD) {
                log.warn("Low confidence ({}) rating for commit {}: {}",
                        rating.confidence(), chunk.commitSha(), rating.reasoning());
            }

            return rating;

        } catch (Exception e) {
            log.error("Error rating commit chunk {}: {}", chunk.commitSha(), e.getMessage());
            return EffortRatingDTO.trivial("Error during AI analysis: " + e.getMessage());
        }
    }

    /**
     * Truncates diff if it's too long to fit in context (safety mechanism).
     * The ChunkerService should already handle this, but double safety is good.
     */
    private String truncateDiff(String diff) {
        if (diff == null)
            return "";
        int maxLength = 10000; // conservative character limit
        if (diff.length() > maxLength) {
            return diff.substring(0, maxLength) + "\n... (truncated)";
        }
        return diff;
    }
}
