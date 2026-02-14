package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.CommitLabel;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.core.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitEffortRaterServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiProperties.CommitClassifier commitClassifierProperties;

    private CommitEffortRaterService raterService;

    @BeforeEach
    void setUp() {
        raterService = new CommitEffortRaterService(chatClient, aiProperties);
    }

    @Test
    void rateChunk_aiDisabled_returnsDisabledDTO() throws InterruptedException {
        when(aiProperties.isEnabled()).thenReturn(false);

        EffortRatingDTO result = raterService.rateChunk(createDummyChunk());

        assertEquals(0.0, result.confidence());
        assertEquals("AI disabled", result.reasoning());
        verifyNoInteractions(chatClient);
    }

    @Test
    void rateChunk_classifierDisabled_returnsDisabledDTO() throws InterruptedException {
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getCommitClassifier()).thenReturn(commitClassifierProperties);
        when(commitClassifierProperties.isEnabled()).thenReturn(false);

        EffortRatingDTO result = raterService.rateChunk(createDummyChunk());

        assertEquals(0.0, result.confidence());
        assertEquals("AI disabled", result.reasoning());
        verifyNoInteractions(chatClient);
    }

    @Test
    void rateChunkWithUsage_usagePresent_returnsParsedRatingAndTokenUsage() throws InterruptedException {
        ChatResponse chatResponse = createChatResponse(
                """
                        {"effortScore": 6.0, "complexity": 5.0, "novelty": 4.0, "type": "FEATURE", "confidence": 0.8, "reasoning": "Solid change"}
                        """,
                "provider-model",
                new DefaultUsage(120, 30, 150, Map.of("source", "provider")));

        mockEnabledAi(chatResponse);

        CommitEffortRaterService.RatingWithUsage result = raterService.rateChunkWithUsage(createDummyChunk());

        assertEquals(CommitLabel.FEATURE, result.rating().type());
        assertEquals(0.8, result.rating().confidence());
        assertTrue(result.tokenUsage().usageAvailable());
        assertEquals(120, result.tokenUsage().promptTokens());
        assertEquals(30, result.tokenUsage().completionTokens());
        assertEquals(150, result.tokenUsage().totalTokens());
        assertEquals("provider-model", result.tokenUsage().model());
    }

    @Test
    void rateChunkWithUsage_missingUsage_marksUsageUnavailable() throws InterruptedException {
        ChatResponse chatResponse = createChatResponse(
                """
                        {"effortScore": 4.0, "complexity": 3.0, "novelty": 3.0, "type": "REFACTOR", "confidence": 0.9, "reasoning": "Refactor update"}
                        """,
                "provider-model",
                null);

        mockEnabledAi(chatResponse);

        CommitEffortRaterService.RatingWithUsage result = raterService.rateChunkWithUsage(createDummyChunk());

        assertFalse(result.tokenUsage().usageAvailable());
        assertEquals(0, result.tokenUsage().promptTokens());
        assertEquals(0, result.tokenUsage().completionTokens());
        assertEquals(0, result.tokenUsage().totalTokens());
        assertEquals("provider-model", result.tokenUsage().model());
    }

    @Test
    void rateChunkWithUsage_parseError_stillReturnsUsage() throws InterruptedException {
        ChatResponse chatResponse = createChatResponse(
                "not-json-response",
                "provider-model",
                new DefaultUsage(33, 9, 42, Map.of("source", "provider")));

        mockEnabledAi(chatResponse);

        CommitEffortRaterService.RatingWithUsage result = raterService.rateChunkWithUsage(createDummyChunk());

        assertTrue(result.tokenUsage().usageAvailable());
        assertEquals(42, result.tokenUsage().totalTokens());
        assertEquals(CommitLabel.TRIVIAL, result.rating().type());
        assertEquals("Truncated AI response", result.rating().reasoning());
    }

    private void mockEnabledAi(ChatResponse chatResponse) {
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getCommitClassifier()).thenReturn(commitClassifierProperties);
        when(commitClassifierProperties.isEnabled()).thenReturn(true);
        when(commitClassifierProperties.getModelName()).thenReturn("configured-model");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(chatResponse);
    }

    private ChatResponse createChatResponse(String content, String model, DefaultUsage usage) {
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder().model(model);
        if (usage != null) {
            metadataBuilder.usage(usage);
        }
        return new ChatResponse(List.of(generation), metadataBuilder.build());
    }

    private CommitChunkDTO createDummyChunk() {
        return new CommitChunkDTO(
                "sha1", 1L, "author@test.com", "msg", LocalDateTime.now(),
                List.of("file.java"), "diff", 10, 5, 0, 1, false, List.of(),
                null, null, null);
    }
}
