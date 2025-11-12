package de.tum.cit.aet.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("api/story/")
public class HarmoniaTestResource {

    private static final Logger log = LoggerFactory.getLogger(HarmoniaTestResource.class);

    private final ChatClient chatClient;

    public HarmoniaTestResource(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

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
