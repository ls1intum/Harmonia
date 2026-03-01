package de.tum.cit.aet.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Configuration for Spring AI chat clients.
 * Active when the {@code openapi} profile is not enabled.
 * Selects the first available {@link ChatModel} to build the application-wide {@link ChatClient}.
 */
@Slf4j
@Configuration
@Profile("!openapi")
public class SpringAIConfiguration {

    /**
     * Creates a {@link ChatClient} from the first available {@link ChatModel}.
     *
     * @param chatModels the list of auto-detected chat models
     * @return a configured {@link ChatClient}, or {@code null} if no models are available
     */
    @Bean
    public ChatClient chatClient(List<ChatModel> chatModels) {
        if (chatModels == null || chatModels.isEmpty()) {
            return null;
        }

        ChatModel chatModel = chatModels.getFirst();
        log.info("Using Chat Model: {}", chatModel.getDefaultOptions().getModel());

        return ChatClient.builder(chatModel).build();
    }
}
