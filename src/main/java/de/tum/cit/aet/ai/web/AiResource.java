package de.tum.cit.aet.ai.web;

import de.tum.cit.aet.ai.dto.AnomalyDetectionRequestDTO;
import de.tum.cit.aet.ai.dto.AnomalyReportDTO;
import de.tum.cit.aet.ai.dto.CommitClassificationDTO;
import de.tum.cit.aet.ai.dto.CommitClassificationRequestDTO;
import de.tum.cit.aet.ai.service.AnomalyDetectorService;
import de.tum.cit.aet.ai.service.CommitClassifierService;
import de.tum.cit.aet.core.config.AiProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST controller for AI-related endpoints.
 */
@RestController
@RequestMapping("api/ai/")
@Slf4j
@Profile("!openapi")
public class AiResource {

    private final ChatClient chatClient;
    private final CommitClassifierService commitClassifierService;
    private final AnomalyDetectorService anomalyDetectorService;
    private final AiProperties aiProperties;
    private final WebClient webClient;

    public AiResource(ChatClient chatClient, CommitClassifierService commitClassifierService,
            AnomalyDetectorService anomalyDetectorService, AiProperties aiProperties,
            @Value("${spring.ai.openai.base-url:http://localhost:1234}") String llmBaseUrl) {
        this.chatClient = chatClient;
        this.commitClassifierService = commitClassifierService;
        this.anomalyDetectorService = anomalyDetectorService;
        this.aiProperties = aiProperties;
        this.webClient = WebClient.builder().baseUrl(llmBaseUrl).build();
    }

    /**
     * Get list of available LLM models from the configured LLM server.
     *
     * @return list of model IDs
     */
    @GetMapping("models")
    public ResponseEntity<?> getAvailableModels() {
        log.info("Fetching available models from LLM server");
        try {
            String response = webClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to fetch models from LLM server: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", "LLM server not reachable", "details", e.getMessage()));
        }
    }

    /**
     * Get the currently selected model.
     *
     * @return current model name
     */
    @GetMapping("model")
    public ResponseEntity<?> getCurrentModel() {
        String currentModel = aiProperties.getCommitClassifier().getModelName();
        return ResponseEntity.ok(Map.of("model", currentModel));
    }

    /**
     * Set the active LLM model for analysis.
     *
     * @param request containing the model ID to use
     * @return confirmation of the change
     */
    @PostMapping("model")
    public ResponseEntity<?> setActiveModel(@RequestBody Map<String, String> request) {
        String modelId = request.get("model");
        if (modelId == null || modelId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Model ID is required"));
        }

        setModel(modelId);

        return ResponseEntity.ok(Map.of(
                "message", "Model updated successfully",
                "model", modelId));
    }

    private void setModel(String modelId) {
        log.info("Setting active AI model to: {}", modelId);
        aiProperties.getCommitClassifier().setModelName(modelId);
        aiProperties.getAnomalyDetector().setModelName(modelId);
    }

    /**
     * Auto-detects available models on startup and sets the first one as default
     * if no model is currently configured.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void autoDetectModel() {
        if (aiProperties.getCommitClassifier().getModelName() != null) {
            log.info("Model already configured: {}", aiProperties.getCommitClassifier().getModelName());
            return;
        }

        log.info("No AI model configured. Attempting auto-discovery...");
        try {
            String response = webClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
                com.fasterxml.jackson.databind.JsonNode data = root.get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    String firstModelId = data.get(0).get("id").asText();
                    log.info("Auto-discovered model: {}", firstModelId);
                    setModel(firstModelId);
                } else {
                    log.warn("No models found in LM Studio response.");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto-detect AI models on startup (LM Studio might be down): {}", e.getMessage());
        }
    }

    /**
     * Example endpoint to generate a story based on the provided message. (Must be
     * deleted or changed later)
     * Streams the response as a text event stream.
     *
     * @param message The input message to generate the story from.
     * @return The generated story content.
     */
    @GetMapping(value = "generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String storyWithStream(@RequestParam(defaultValue = "Tell a story in less than 100 words") String message) {
        log.info("Received story generation request with message: {}", message);
        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * Test endpoint for commit classification.
     *
     * @param sha     commit SHA
     * @param message commit message
     * @param files   comma-separated file paths
     * @param diff    optional: actual code changes
     * @return classification result
     */
    @GetMapping("classify-commit")
    public CommitClassificationDTO classifyCommit(
            @RequestParam(defaultValue = "abc123") String sha,
            @RequestParam(defaultValue = "Add user authentication") String message,
            @RequestParam(defaultValue = "src/auth/AuthController.java,src/auth/AuthService.java") String files,
            @RequestParam(required = false) String diff) {
        log.info("Test classification request for commit: {}", sha);
        List<String> filePaths = List.of(files.split(","));
        CommitClassificationRequestDTO request = new CommitClassificationRequestDTO(sha, message, filePaths, diff);
        return commitClassifierService.classify(request);
    }

    /**
     * Test endpoint for anomaly detection.
     *
     * @param scenario test scenario (late-dump, solo, inactive, good)
     * @return anomaly report
     */
    @GetMapping("detect-anomalies")
    public AnomalyReportDTO detectAnomalies(@RequestParam(defaultValue = "late-dump") String scenario) {
        log.info("Test anomaly detection request: {}", scenario);

        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();
        List<AnomalyDetectionRequestDTO.CommitSummary> commits;

        switch (scenario) {
            case "solo" -> {
                // SOLO_DEVELOPMENT: Alice does 90% of work
                commits = List.of(
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(3), 100),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(6), 80),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(10), 120),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(14), 90),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(18), 110),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(22), 100),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(25), 80),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(27), 95),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusDays(1), 70),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", end.minusHours(2), 25));
            }
            case "inactive" -> {
                // INACTIVE_PERIOD: 16-day gap in middle
                commits = List.of(
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(2), 100),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(4), 80),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(6), 90),
                        // 16-day gap here (53% of 30 days)
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(22), 100),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(25), 110),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(28), 95),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusDays(1), 70));
            }
            case "good" -> {
                // GOOD: Balanced, spread out, both contributing
                commits = List.of(
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(2), 80),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(5), 70),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(8), 90),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(12), 85),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(16), 75),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(20), 80),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(24), 70),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", start.plusDays(27), 65),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusDays(1), 75));
            }
            default -> {
                // LATE_DUMP + SOLO_DEVELOPMENT: Most work by Alice at deadline
                commits = List.of(
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(3), 50),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(8), 40),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", start.plusDays(15), 30),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusDays(2), 200),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusDays(1), 150),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusHours(10), 180),
                        new AnomalyDetectionRequestDTO.CommitSummary("Alice", end.minusHours(5), 120),
                        new AnomalyDetectionRequestDTO.CommitSummary("Bob", end.minusHours(3), 30));
            }
        }

        AnomalyDetectionRequestDTO request = new AnomalyDetectionRequestDTO("team-" + scenario, commits, start, end);
        return anomalyDetectorService.detect(request);
    }
}
