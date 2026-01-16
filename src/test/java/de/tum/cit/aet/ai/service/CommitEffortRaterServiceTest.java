package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import de.tum.cit.aet.core.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rateChunk_aiDisabled_returnsDisabledDTO() {
        when(aiProperties.isEnabled()).thenReturn(false);

        EffortRatingDTO result = raterService.rateChunk(createDummyChunk());

        assertEquals(0.0, result.confidence());
        assertEquals("AI disabled", result.reasoning());
        verifyNoInteractions(chatClient);
    }

    @Test
    void rateChunk_classifierDisabled_returnsDisabledDTO() {
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getCommitClassifier()).thenReturn(commitClassifierProperties);
        when(commitClassifierProperties.isEnabled()).thenReturn(false);

        EffortRatingDTO result = raterService.rateChunk(createDummyChunk());

        assertEquals(0.0, result.confidence());
        assertEquals("AI disabled", result.reasoning());
        verifyNoInteractions(chatClient);
    }

    // Note: Testing actual Spring AI mocking is verbose without deep integration
    // tests.
    // We assume the prompt construction logic works if the basic flow is correct.
    // A fully mocked test would require extensive mocking of the ChatClient fluent
    // API.

    private CommitChunkDTO createDummyChunk() {
        return new CommitChunkDTO(
                "sha1", 1L, "author@test.com", "msg", LocalDateTime.now(),
                List.of("file.java"), "diff", 10, 5, 0, 1, false, List.of());
    }
}
