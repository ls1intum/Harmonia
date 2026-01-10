package de.tum.cit.aet.ai.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommitChunkDTO.
 */
class CommitChunkDTOTest {

    @Test
    void testSingleFactory() {
        LocalDateTime now = LocalDateTime.now();
        List<String> files = List.of("src/Main.java", "src/Utils.java");

        CommitChunkDTO chunk = CommitChunkDTO.single(
                "abc123",
                1L,
                "student@test.com",
                "Add feature",
                now,
                files,
                "diff content",
                50,
                10);

        assertEquals("abc123", chunk.commitSha());
        assertEquals(1L, chunk.authorId());
        assertEquals("student@test.com", chunk.authorEmail());
        assertEquals("Add feature", chunk.commitMessage());
        assertEquals(now, chunk.timestamp());
        assertEquals(files, chunk.files());
        assertEquals("diff content", chunk.diffContent());
        assertEquals(50, chunk.linesAdded());
        assertEquals(10, chunk.linesDeleted());
        assertEquals(0, chunk.chunkIndex());
        assertEquals(1, chunk.totalChunks());
        assertFalse(chunk.isBundled());
        assertTrue(chunk.bundledCommits().isEmpty());
    }

    @Test
    void testTotalLinesChanged() {
        CommitChunkDTO chunk = CommitChunkDTO.single(
                "abc123", 1L, "test@test.com", "msg",
                LocalDateTime.now(), List.of(), "", 100, 50);

        assertEquals(150, chunk.totalLinesChanged());
    }

    @Test
    void testChunkedCommit() {
        LocalDateTime now = LocalDateTime.now();

        CommitChunkDTO chunk = new CommitChunkDTO(
                "abc123",
                1L,
                "test@test.com",
                "Large commit",
                now,
                List.of("file1.java"),
                "diff",
                200,
                100,
                2, // chunkIndex
                5, // totalChunks
                false,
                List.of());

        assertEquals(2, chunk.chunkIndex());
        assertEquals(5, chunk.totalChunks());
    }

    @Test
    void testBundledCommit() {
        LocalDateTime now = LocalDateTime.now();
        List<String> bundledShas = List.of("sha1", "sha2", "sha3");

        CommitChunkDTO chunk = new CommitChunkDTO(
                "sha1",
                1L,
                "test@test.com",
                "msg1 | msg2 | msg3",
                now,
                List.of("file.java"),
                "combined diff",
                30,
                10,
                0,
                1,
                true, // isBundled
                bundledShas);

        assertTrue(chunk.isBundled());
        assertEquals(3, chunk.bundledCommits().size());
        assertEquals("sha1", chunk.bundledCommits().get(0));
    }
}
