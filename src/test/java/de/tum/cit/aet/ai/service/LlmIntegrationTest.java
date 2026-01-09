package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.CommitChunkDTO;
import de.tum.cit.aet.ai.dto.EffortRatingDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CommitEffortRaterService that actually calls the LLM.
 * These tests are disabled by default and only run when
 * LLM_INTEGRATION_TESTS=true.
 *
 * To run: LLM_INTEGRATION_TESTS=true ./gradlew test --tests
 * "*LlmIntegrationTest*"
 *
 * Prerequisites:
 * - LM Studio running on localhost:1234
 * - application-local.yml configured with spring.ai.openai.base-url:
 * http://localhost:1234
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "LLM_INTEGRATION_TESTS", matches = "true")
class LlmIntegrationTest {

    @Autowired
    private CommitEffortRaterService effortRaterService;

    @Test
    void testRateFeatureCommit() {
        // Given: A realistic feature commit with Java code
        CommitChunkDTO chunk = new CommitChunkDTO(
                "abc123def",
                1L,
                "developer@example.com",
                "Add user authentication service",
                LocalDateTime.now(),
                List.of("src/main/java/auth/AuthService.java"),
                """
                        +package auth;
                        +
                        +import org.springframework.stereotype.Service;
                        +
                        +@Service
                        +public class AuthService {
                        +    public boolean authenticate(String username, String password) {
                        +        // TODO: implement real authentication
                        +        return username.equals("admin") && password.equals("secret");
                        +    }
                        +}
                        """,
                12,
                0,
                0,
                1,
                false,
                List.of());

        // When: We rate the chunk
        EffortRatingDTO rating = effortRaterService.rateChunk(chunk);

        // Then: We get a valid rating with reasonable values
        assertNotNull(rating, "Rating should not be null");
        assertNotNull(rating.type(), "Type should not be null");
        assertTrue(rating.effortScore() >= 1 && rating.effortScore() <= 10,
                "Effort score should be between 1-10, got: " + rating.effortScore());
        assertTrue(rating.complexity() >= 1 && rating.complexity() <= 10,
                "Complexity should be between 1-10, got: " + rating.complexity());
        assertNotNull(rating.reasoning(), "Reasoning should not be null");

        System.out.println("=== LLM Rating Result ===");
        System.out.println("Type: " + rating.type());
        System.out.println("Effort: " + rating.effortScore());
        System.out.println("Complexity: " + rating.complexity());
        System.out.println("Novelty: " + rating.novelty());
        System.out.println("Confidence: " + rating.confidence());
        System.out.println("Reasoning: " + rating.reasoning());
    }

    @Test
    void testRateTrivialCommit() {
        // Given: A trivial typo fix
        CommitChunkDTO chunk = new CommitChunkDTO(
                "xyz789",
                2L,
                "intern@example.com",
                "Fix typo in README",
                LocalDateTime.now(),
                List.of("README.md"),
                """
                        -# Welcome to the Projectt
                        +# Welcome to the Project
                        """,
                1,
                1,
                0,
                1,
                false,
                List.of());

        // When
        EffortRatingDTO rating = effortRaterService.rateChunk(chunk);

        // Then: Should be classified as trivial or low effort
        assertNotNull(rating);
        assertTrue(rating.effortScore() <= 3,
                "Trivial typo fix should have low effort score, got: " + rating.effortScore());

        System.out.println("=== Trivial Commit Rating ===");
        System.out.println("Type: " + rating.type());
        System.out.println("Effort: " + rating.effortScore());
        System.out.println("Reasoning: " + rating.reasoning());
    }

    @Test
    void testRateCommitWithCurlyBraces() {
        // Given: A commit with lots of curly braces (the bug we fixed!)
        CommitChunkDTO chunk = new CommitChunkDTO(
                "curly123",
                1L,
                "dev@example.com",
                "Add JSON parsing with complex structures",
                LocalDateTime.now(),
                List.of("Parser.java"),
                """
                        +public Map<String, Object> parse(String json) {
                        +    Map<String, Object> result = new HashMap<>();
                        +    if (json.startsWith("{") && json.endsWith("}")) {
                        +        // Handle nested objects like {"key": {"nested": "value"}}
                        +        result.put("data", parseNested(json));
                        +    }
                        +    return result;
                        +}
                        """,
                8,
                0,
                0,
                1,
                false,
                List.of());

        // When: This should NOT throw "template string is not valid"
        EffortRatingDTO rating = effortRaterService.rateChunk(chunk);

        // Then
        assertNotNull(rating, "Should handle curly braces in diff content");
        assertFalse(rating.reasoning().contains("template string"),
                "Should not have template parsing error");

        System.out.println("=== Curly Brace Test ===");
        System.out.println("Type: " + rating.type());
        System.out.println("Reasoning: " + rating.reasoning());
    }
}
