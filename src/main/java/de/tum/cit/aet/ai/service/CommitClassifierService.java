package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitClassificationDTO;
import de.tum.cit.aet.ai.dto.CommitClassificationRequestDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!openapi")
public class CommitClassifierService {

    /**
     * Base prompt template for commit classification.
     * Placeholders: sha, message, filePaths
     */
    private static final String COMMIT_CLASSIFICATION_PROMPT = """
            You are a commit classifier. Analyze this commit and classify it into ONE category:

            - FEATURE: New functionality or significant enhancement
            - BUG_FIX: Fixes a bug or error
            - TEST: Adds or modifies tests only
            - REFACTOR: Code restructuring without behavior change
            - TRIVIAL: Formatting, comments, docs, whitespace, typos

            Commit SHA: %s
            Message: %s
            Files changed: %s
            """;

    /**
     * Additional prompt when diff content is provided.
     */
    private static final String DIFF_ANALYSIS_PROMPT = """

            Actual code changes:
            %s

            IMPORTANT: Base your classification mostly on the actual code changes above, not only the commit message. \
            If the code changes don't match the commit message, classify based on what the code actually does. \
            Misleading commit messages should result in TRIVIAL classification with low confidence.""";

    /**
     * Response format instruction.
     */
    private static final String RESPONSE_FORMAT_PROMPT = """

            Respond ONLY with valid JSON in this exact format:
            {"label": "FEATURE", "confidence": 0.95, "reasoning": "brief explanation"}

            Valid labels: FEATURE, BUG_FIX, TEST, REFACTOR, TRIVIAL""";

    private final ChatClient chatClient;

    private final AiProperties aiProperties;

    /**
     * Constructor for CommitClassifierService.
     *
     * @param chatClient the chat client for AI interactions
     * @param aiProperties the AI configuration properties
     */
    public CommitClassifierService(ChatClient chatClient, AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
    }

    /**
     * Classifies a commit based on its message and file paths.
     *
     * @param request the commit classification request
     * @return the commit classification result
     */
    public CommitClassificationDTO classify(CommitClassificationRequestDTO request) {
        if (!aiProperties.isEnabled() || !aiProperties.getCommitClassifier().isEnabled()) {
            log.debug("Commit classifier is disabled, returning TRIVIAL classification");
            return new CommitClassificationDTO(CommitLabel.TRIVIAL, 0.0, "AI disabled");
        }

        log.info("Classifying commit: {}", request.sha());

        String prompt = buildPrompt(request);
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        CommitClassificationDTO result = parseResponse(response);

        if (result.confidence() < aiProperties.getCommitClassifier().getConfidenceThreshold()) {
            log.debug("Low confidence ({}) for commit {}, treating as TRIVIAL",
                    result.confidence(), request.sha());
            return new CommitClassificationDTO(CommitLabel.TRIVIAL,
                    result.confidence(), "Low confidence: " + result.reasoning());
        }

        return result;
    }

    private String buildPrompt(CommitClassificationRequestDTO request) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(String.format(COMMIT_CLASSIFICATION_PROMPT,
                request.sha(),
                request.message(),
                String.join(", ", request.filePaths())));

        if (request.diffContent() != null && !request.diffContent().isBlank()) {
            prompt.append(String.format(DIFF_ANALYSIS_PROMPT, request.diffContent()));
        }

        prompt.append(RESPONSE_FORMAT_PROMPT);

        return prompt.toString();
    }

    private CommitClassificationDTO parseResponse(String response) {
        try {
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            int labelStart = cleaned.indexOf("\"label\": \"") + 10;
            int labelEnd = cleaned.indexOf("\"", labelStart);
            String label = cleaned.substring(labelStart, labelEnd);

            int confStart = cleaned.indexOf("\"confidence\": ") + 15;
            int confEnd = cleaned.indexOf(",", confStart);
            if (confEnd == -1) {
                confEnd = cleaned.indexOf("}", confStart);
            }
            double confidence = Double.parseDouble(cleaned.substring(confStart, confEnd).trim());

            int reasonStart = cleaned.indexOf("\"reasoning\": \"") + 14;
            int reasonEnd = cleaned.lastIndexOf("\"");
            String reasoning = cleaned.substring(reasonStart, reasonEnd);

            CommitLabel commitLabel = CommitLabel.valueOf(label);
            return new CommitClassificationDTO(commitLabel, confidence, reasoning);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", response, e);
            return new CommitClassificationDTO(CommitLabel.TRIVIAL, 0.0,
                    "Parse error: " + e.getMessage());
        }
    }
}
