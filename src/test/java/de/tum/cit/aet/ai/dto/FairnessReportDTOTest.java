package de.tum.cit.aet.ai.dto;

import de.tum.cit.aet.ai.domain.FairnessFlag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FairnessReportDTO.
 */
class FairnessReportDTOTest {

    @Test
    void testBalancedReport() {
        FairnessReportDTO report = new FairnessReportDTO(
                "team-123",
                85.0,
                Map.of(1L, 100.0, 2L, 95.0),
                Map.of(1L, 0.51, 2L, 0.49),
                List.of(),
                false,
                List.of(
                        new FairnessReportDTO.AuthorDetailDTO(
                                1L, "alice@test.com", 100.0, 0.51, 10, 12, 8.3,
                                Map.of(CommitLabel.FEATURE, 5, CommitLabel.BUG_FIX, 3)),
                        new FairnessReportDTO.AuthorDetailDTO(
                                2L, "bob@test.com", 95.0, 0.49, 9, 11, 8.6,
                                Map.of(CommitLabel.FEATURE, 4, CommitLabel.TEST, 4))),
                new FairnessReportDTO.AnalysisMetadataDTO(19, 23, 2, 0.92, 1, 1500),
                List.of());

        assertEquals("team-123", report.teamId());
        assertEquals(85.0, report.balanceScore());
        assertFalse(report.requiresManualReview());
        assertTrue(report.flags().isEmpty());
        assertEquals(2, report.authorDetails().size());
    }

    @Test
    void testUnbalancedReport() {
        FairnessReportDTO report = new FairnessReportDTO(
                "team-456",
                35.0,
                Map.of(1L, 180.0, 2L, 20.0),
                Map.of(1L, 0.90, 2L, 0.10),
                List.of(FairnessFlag.UNEVEN_DISTRIBUTION, FairnessFlag.SOLO_CONTRIBUTOR),
                true,
                List.of(),
                new FairnessReportDTO.AnalysisMetadataDTO(15, 18, 0, 0.85, 2, 2000),
                List.of());

        assertEquals(35.0, report.balanceScore());
        assertTrue(report.requiresManualReview());
        assertEquals(2, report.flags().size());
        assertTrue(report.flags().contains(FairnessFlag.UNEVEN_DISTRIBUTION));
        assertTrue(report.flags().contains(FairnessFlag.SOLO_CONTRIBUTOR));
    }

    @Test
    void testErrorReport() {
        FairnessReportDTO report = FairnessReportDTO.error("team-error", "Repository not found");

        assertEquals("team-error", report.teamId());
        assertEquals(0.0, report.balanceScore());
        assertTrue(report.requiresManualReview());
        assertEquals(1, report.flags().size());
        assertEquals(FairnessFlag.ANALYSIS_ERROR, report.flags().get(0));
        assertTrue(report.authorDetails().isEmpty());
    }

    @Test
    void testAuthorDetailDTO() {
        FairnessReportDTO.AuthorDetailDTO detail = new FairnessReportDTO.AuthorDetailDTO(
                42L,
                "developer@test.com",
                150.5,
                0.65,
                20,
                25,
                6.02,
                Map.of(
                        CommitLabel.FEATURE, 10,
                        CommitLabel.BUG_FIX, 5,
                        CommitLabel.TEST, 3,
                        CommitLabel.REFACTOR, 2));

        assertEquals(42L, detail.authorId());
        assertEquals("developer@test.com", detail.authorEmail());
        assertEquals(150.5, detail.totalEffort());
        assertEquals(0.65, detail.effortShare());
        assertEquals(20, detail.commitCount());
        assertEquals(25, detail.chunkCount());
        assertEquals(6.02, detail.averageEffortPerChunk());
        assertEquals(4, detail.commitsByType().size());
    }

    @Test
    void testAnalysisMetadataDTO() {
        FairnessReportDTO.AnalysisMetadataDTO metadata = new FairnessReportDTO.AnalysisMetadataDTO(
                50, // totalCommits
                65, // totalChunks
                5, // bundledCommitGroups
                0.88, // averageConfidence
                3, // lowConfidenceRatings
                3500 // analysisTimeMs
        );

        assertEquals(50, metadata.totalCommits());
        assertEquals(65, metadata.totalChunks());
        assertEquals(5, metadata.bundledCommitGroups());
        assertEquals(0.88, metadata.averageConfidence());
        assertEquals(3, metadata.lowConfidenceRatings());
        assertEquals(3500, metadata.analysisTimeMs());
    }

    @Test
    void testAllFairnessFlags() {
        // Verify all flags can be used
        for (FairnessFlag flag : FairnessFlag.values()) {
            FairnessReportDTO report = new FairnessReportDTO(
                    "test", 50.0, Map.of(), Map.of(),
                    List.of(flag), true, List.of(),
                    new FairnessReportDTO.AnalysisMetadataDTO(0, 0, 0, 0, 0, 0),
                    List.of());
            assertTrue(report.flags().contains(flag));
        }
    }
}
