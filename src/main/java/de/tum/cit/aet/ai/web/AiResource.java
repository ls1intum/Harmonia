package de.tum.cit.aet.ai.web;

import de.tum.cit.aet.ai.dto.CommitClassification;
import de.tum.cit.aet.ai.dto.CommitClassificationRequest;
import de.tum.cit.aet.ai.service.CommitClassifierService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Example REST controller for AI-related endpoints.
 */
@RestController
@RequestMapping("api/ai/")
@Slf4j
@Profile("!openapi")
public class AiResource {

    private final ChatClient chatClient;

    private final CommitClassifierService commitClassifierService;

    public AiResource(ChatClient chatClient, CommitClassifierService commitClassifierService) {
        this.chatClient = chatClient;
        this.commitClassifierService = commitClassifierService;
    }

    /**
     * Example endpoint to generate a story based on the provided message. (Must be deleted or changed later)
     * Streams the response as a text event stream.
     *
     * @param message The input message to generate the story from.
     * @return The generated story content.
     */
    @GetMapping(value = "generate",  produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
     * @param sha commit SHA
     * @param message commit message
     * @param files comma-separated file paths
     * @param diff optional: actual code changes
     * @return classification result
     */
    @GetMapping("classify-commit")
    public CommitClassification classifyCommit(
            @RequestParam(defaultValue = "abc123") String sha,
            @RequestParam(defaultValue = "Add user authentication") String message,
            @RequestParam(defaultValue = "src/auth/AuthController.java,src/auth/AuthService.java") String files,
            @RequestParam(required = false) String diff) {
        log.info("Test classification request for commit: {}", sha);
        List<String> filePaths = List.of(files.split(","));
        CommitClassificationRequest request = new CommitClassificationRequest(sha, message, filePaths, diff);
        return commitClassifierService.classify(request);
    }
}
