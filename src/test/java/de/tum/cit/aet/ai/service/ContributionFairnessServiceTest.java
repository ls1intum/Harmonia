package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.*;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionFairnessServiceTest {

    @Mock
    private CommitChunkerService commitChunkerService;

    @Mock
    private CommitEffortRaterService commitEffortRaterService;

    @Mock
    private GitContributionAnalysisService gitContributionAnalysisService;

    @InjectMocks
    private ContributionFairnessService fairnessService;

    private TeamRepositoryDTO dummyRepo;
    private CommitChunkDTO chunkA, chunkB;
    private EffortRatingDTO ratingHigh, ratingLow;

    @BeforeEach
    void setUp() {
        TeamDTO teamDTO = new TeamDTO(null, "Team 1", "T1", null, null);
        ParticipationDTO participationDTO = new ParticipationDTO(teamDTO, null, null, null);

        List<VCSLogDTO> logs = List.of(
                new VCSLogDTO("student1@tum.de", null, "hash1"),
                new VCSLogDTO("student2@tum.de", null, "hash2"));

        dummyRepo = new TeamRepositoryDTO(participationDTO, logs, "/tmp/repo", true, null);

        chunkA = new CommitChunkDTO(
                "hash1", 1L, "student1@tum.de", "feat", LocalDateTime.now(),
                List.of(), "diff", 100, 0, 0, 1, false, List.of());
        chunkB = new CommitChunkDTO(
                "hash2", 2L, "student2@tum.de", "fix", LocalDateTime.now(),
                List.of(), "diff", 10, 0, 0, 1, false, List.of());

        ratingHigh = new EffortRatingDTO(8.0, 5.0, 5.0, CommitLabel.FEATURE, 0.9, "Good work", false, null);
        ratingLow = new EffortRatingDTO(2.0, 2.0, 2.0, CommitLabel.TRIVIAL, 0.9, "Trivial", false, null);
    }

    @Test
    void testAnalyzeFairness_unevenDistribution() {
        when(commitChunkerService.processRepository(anyString(), any())).thenReturn(List.of(chunkA, chunkB));
        when(commitEffortRaterService.rateChunk(chunkA)).thenReturn(ratingHigh); // Weighted ~8.0
        when(commitEffortRaterService.rateChunk(chunkB)).thenReturn(ratingLow); // Weighted ~2.0

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        assertNotNull(report);
        assertEquals("Team 1", report.teamId());

        // High effort share for student 1 (~80%), Low for student 2 (~20%)
        // Should trigger UNEVEN_DISTRIBUTION flag
        assertTrue(report.flags().contains(FairnessFlag.UNEVEN_DISTRIBUTION));
        assertTrue(report.requiresManualReview());

        assertEquals(2, report.authorDetails().size());
    }

    @Test
    void testAnalyzeFairness_balanced() {
        when(commitChunkerService.processRepository(anyString(), any())).thenReturn(List.of(chunkA, chunkB));
        when(commitEffortRaterService.rateChunk(chunkA)).thenReturn(ratingHigh);
        when(commitEffortRaterService.rateChunk(chunkB)).thenReturn(ratingHigh); // Both high effort

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        // Balanced (~50/50)
        assertTrue(report.flags().isEmpty());
        assertFalse(report.requiresManualReview());
        assertEquals(100.0, report.balanceScore(), 1.0);
    }

    @Test
    void testAnalyzeFairness_soloContributor() {
        when(commitChunkerService.processRepository(anyString(), any())).thenReturn(List.of(chunkA));
        when(commitEffortRaterService.rateChunk(chunkA)).thenReturn(ratingHigh);

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        // 100% share for student 1
        assertTrue(report.flags().contains(FairnessFlag.SOLO_CONTRIBUTOR));
        assertEquals(0.0, report.balanceScore());
    }
}
