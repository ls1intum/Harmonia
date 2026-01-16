package de.tum.cit.aet.core.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for OpenAPI generation profile.
 * Provides mock beans to allow application startup without external dependencies.
 */
@Configuration
@Profile("openapi")
public class OpenApiConfiguration {

    /**
     * Provides a dummy ChatModel bean for OpenAPI generation.
     *
     * @return A mock ChatModel that throws UnsupportedOperationException when used
     */
    @Bean
    public ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException("ChatModel is not available in openapi profile");
            }
        };
    }

    /**
     * Provides a dummy ChatClient bean for OpenAPI generation.
     * This allows the application to start without requiring actual AI model configuration.
     *
     * @param chatModel The mock ChatModel to use
     * @return A ChatClient instance built with the mock ChatModel
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
