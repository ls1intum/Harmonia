package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitClassification;
import de.tum.cit.aet.ai.dto.CommitClassificationRequest;
import de.tum.cit.aet.core.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommitClassifierService {

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
    public CommitClassification classify(CommitClassificationRequest request) {
        if (!aiProperties.isEnabled() || !aiProperties.getCommitClassifier().isEnabled()) {
            log.debug("Commit classifier is disabled, returning TRIVIAL classification");
            return new CommitClassification(CommitClassification.CommitLabel.TRIVIAL, 0.0, "AI disabled");
        }

        log.info("Classifying commit: {}", request.sha());

        String prompt = buildPrompt(request);
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        CommitClassification result = parseResponse(response);

        if (result.confidence() < aiProperties.getCommitClassifier().getConfidenceThreshold()) {
            log.debug("Low confidence ({}) for commit {}, treating as TRIVIAL",
                    result.confidence(), request.sha());
            return new CommitClassification(CommitClassification.CommitLabel.TRIVIAL,
                    result.confidence(), "Low confidence: " + result.reasoning());
        }

        return result;
    }

    private String buildPrompt(CommitClassificationRequest request) {
        String basePrompt = String.format("""
                You are a commit classifier. Analyze this commit and classify it into ONE category:

                - FEATURE: New functionality or significant enhancement
                - BUG_FIX: Fixes a bug or error
                - TEST: Adds or modifies tests only
                - REFACTOR: Code restructuring without behavior change
                - TRIVIAL: Formatting, comments, docs, whitespace, typos

                Commit SHA: %s
                Message: %s
                Files changed: %s
                """,
                request.sha(),
                request.message(),
                String.join(", ", request.filePaths()));

        if (request.diffContent() != null && !request.diffContent().isBlank()) {
            basePrompt += "\n\nActual code changes:\n" + request.diffContent();
            basePrompt += "\n\nIMPORTANT: Base your classification mostly on the actual code changes above, not only the commit message. ";
            basePrompt += "If the code changes don't match the commit message, classify based on what the code actually does. ";
            basePrompt += "Misleading commit messages should result in TRIVIAL classification with low confidence.";
        }

        basePrompt += "\n\nRespond ONLY with valid JSON in this exact format:\n";
        basePrompt += "{\"label\": \"FEATURE\", \"confidence\": 0.95, \"reasoning\": \"brief explanation\"}\n\n";
        basePrompt += "Valid labels: FEATURE, BUG_FIX, TEST, REFACTOR, TRIVIAL";

        return basePrompt;
    }

    private CommitClassification parseResponse(String response) {
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

            CommitClassification.CommitLabel commitLabel = CommitClassification.CommitLabel.valueOf(label);
            return new CommitClassification(commitLabel, confidence, reasoning);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", response, e);
            return new CommitClassification(CommitClassification.CommitLabel.TRIVIAL, 0.0,
                    "Parse error: " + e.getMessage());
        }
    }
}
