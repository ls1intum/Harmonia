package de.tum.cit.aet.ai.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example REST controller for AI-related endpoints.
 */
@RestController
@RequestMapping("api/ai/")
@Slf4j
@Profile("!openapi")
public class AiResource {

    private final ChatClient chatClient;

    public AiResource(ChatClient chatClient) {
        this.chatClient = chatClient;
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
        // 1. Get a prompt object from the chat client
        return chatClient
                .prompt()
                // 2. Set the user's input as the prompt
                .user(message)
                // 3. Stream the response from the chat client
                .call()
                // 4. Extract and return the content of the streamed response
                .content();
    }
}
