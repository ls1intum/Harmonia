package de.tum.cit.aet.ai.service;

import de.tum.cit.aet.ai.dto.*;
import de.tum.cit.aet.analysis.dto.CommitMappingResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.*;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.analysis.service.cqi.CommitPreFilterService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamDTO;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipantDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionFairnessServiceTest {

    @Mock
    private CommitChunkerService commitChunkerService;

    @Mock
    private CommitEffortRaterService commitEffortRaterService;

    @Mock
    private CommitPreFilterService commitPreFilterService;

    @Mock
    private CQICalculatorService cqiCalculatorService;

    @Mock
    private GitContributionAnalysisService gitContributionAnalysisService;

    @InjectMocks
    private ContributionFairnessService fairnessService;

    private TeamRepositoryDTO dummyRepo;
    private CommitChunkDTO chunkA, chunkB;
    private EffortRatingDTO ratingHigh, ratingLow;

    @BeforeEach
    void setUp() {
        // Create students for the team
        List<ParticipantDTO> students = List.of(
                new ParticipantDTO(1L, "student1", "Student One", "student1@tum.de"),
                new ParticipantDTO(2L, "student2", "Student Two", "student2@tum.de")
        );

        TeamDTO teamDTO = new TeamDTO(1L, "Team 1", "T1", students, null);
        ParticipationDTO participationDTO = new ParticipationDTO(teamDTO, null, null, null);

        List<VCSLogDTO> logs = List.of(
                new VCSLogDTO("student1@tum.de", null, "hash1"),
                new VCSLogDTO("student2@tum.de", null, "hash2"));

        dummyRepo = new TeamRepositoryDTO(participationDTO, logs, "/tmp/repo", true, null);

        // Mock mapCommitToAuthor to return hash1 -> student 1, hash2 -> student 2
        CommitMappingResultDTO commitMapping = new CommitMappingResultDTO(
                Map.of("hash1", 1L, "hash2", 2L),
                Map.of(),
                Map.of("hash1", "student1@tum.de", "hash2", "student2@tum.de"));
        when(gitContributionAnalysisService.mapCommitToAuthor(any(TeamRepositoryDTO.class),
                nullable(String.class)))
                .thenReturn(commitMapping);

        chunkA = new CommitChunkDTO(
                "hash1", 1L, "student1@tum.de", "feat", LocalDateTime.now(),
                List.of(), "diff", 100, 0, 0, 1, false, List.of(),
                null, null, null);
        chunkB = new CommitChunkDTO(
                "hash2", 2L, "student2@tum.de", "fix", LocalDateTime.now(),
                List.of(), "diff", 10, 0, 0, 1, false, List.of(),
                null, null, null);

        ratingHigh = new EffortRatingDTO(8.0, 5.0, 5.0, CommitLabel.FEATURE, 0.9, "Good work", false, null);
        ratingLow = new EffortRatingDTO(2.0, 2.0, 2.0, CommitLabel.TRIVIAL, 0.9, "Trivial", false, null);
    }

    private void setupDefaultMocks(List<CommitChunkDTO> chunks) {
        // Mock pre-filter to return all chunks
        FilterSummaryDTO filterSummary = new FilterSummaryDTO(
                chunks.size(), chunks.size(), 0, 0, 0, 0, 0, 0, 0, 0);
        PreFilterResultDTO preFilterResult =
                new PreFilterResultDTO(chunks, List.of(), filterSummary);
        when(commitPreFilterService.preFilter(any())).thenReturn(preFilterResult);
    }

    @Test
    void testAnalyzeFairness_unevenDistribution() throws InterruptedException {
        List<CommitChunkDTO> chunks = List.of(chunkA, chunkB);
        when(commitChunkerService.processRepository(anyString(), anyMap(), anyMap())).thenReturn(chunks);
        when(commitEffortRaterService.rateChunkWithUsage(chunkA)).thenReturn(
                ratingWithUsage(ratingHigh, LlmTokenUsageDTO.unavailable("test-model")));
        when(commitEffortRaterService.rateChunkWithUsage(chunkB)).thenReturn(
                ratingWithUsage(ratingLow, LlmTokenUsageDTO.unavailable("test-model")));
        setupDefaultMocks(chunks);

        // Mock CQI result with uneven distribution
        CQIResultDTO cqiResult = new CQIResultDTO(
                35.0,
                new ComponentScoresDTO(35.0, 40.0, 80.0, 50.0, null, null, null),
                null,
                50.0,
                null
        );
        when(cqiCalculatorService.calculate(any(), anyInt(), any(), any(), any(), anyString(), anyString())).thenReturn(cqiResult);

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        assertNotNull(report);
        assertEquals("Team 1", report.teamId());
        assertFalse(report.error());
        assertEquals(2, report.authorDetails().size());
    }

    @Test
    void testAnalyzeFairness_balanced() throws InterruptedException {
        List<CommitChunkDTO> chunks = List.of(chunkA, chunkB);
        when(commitChunkerService.processRepository(anyString(), anyMap(), anyMap())).thenReturn(chunks);
        when(commitEffortRaterService.rateChunkWithUsage(chunkA)).thenReturn(
                ratingWithUsage(ratingHigh, LlmTokenUsageDTO.unavailable("test-model")));
        when(commitEffortRaterService.rateChunkWithUsage(chunkB)).thenReturn(
                ratingWithUsage(ratingHigh, LlmTokenUsageDTO.unavailable("test-model")));
        setupDefaultMocks(chunks);

        // Mock CQI result with balanced scores
        CQIResultDTO cqiResult = new CQIResultDTO(
                95.0,
                new ComponentScoresDTO(95.0, 90.0, 85.0, 80.0, null, null, null),
                null,
                95.0,
                null
        );
        when(cqiCalculatorService.calculate(any(), anyInt(), any(), any(), any(), anyString(), anyString())).thenReturn(cqiResult);

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        assertFalse(report.error());
        assertEquals(95.0, report.balanceScore(), 1.0);
    }

    @Test
    void testAnalyzeFairness_soloContributor() throws InterruptedException {
        List<CommitChunkDTO> chunks = List.of(chunkA);
        when(commitChunkerService.processRepository(anyString(), anyMap(), anyMap())).thenReturn(chunks);
        when(commitEffortRaterService.rateChunkWithUsage(chunkA)).thenReturn(
                ratingWithUsage(ratingHigh, LlmTokenUsageDTO.unavailable("test-model")));
        setupDefaultMocks(chunks);

        // Mock CQI result with solo contributor
        CQIResultDTO cqiResult = new CQIResultDTO(
                0.0,
                new ComponentScoresDTO(0.0, 0.0, 50.0, 0.0, null, null, null),
                null,
                50.0,
                null
        );
        when(cqiCalculatorService.calculate(any(), anyInt(), any(), any(), any(), anyString(), anyString())).thenReturn(cqiResult);

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        assertFalse(report.error());
        assertEquals(0.0, report.balanceScore());
    }

    @Test
    void testAnalyzeFairnessWithUsage_aggregatesTeamTokens() throws InterruptedException {
        List<CommitChunkDTO> chunks = List.of(chunkA, chunkB);
        when(commitChunkerService.processRepository(anyString(), anyMap(), anyMap())).thenReturn(chunks);
        when(commitEffortRaterService.rateChunkWithUsage(chunkA)).thenReturn(
                ratingWithUsage(ratingHigh, new LlmTokenUsageDTO("model-a", 100, 20, 120, true)));
        when(commitEffortRaterService.rateChunkWithUsage(chunkB)).thenReturn(
                ratingWithUsage(ratingLow, LlmTokenUsageDTO.unavailable("model-a")));
        setupDefaultMocks(chunks);

        CQIResultDTO cqiResult = new CQIResultDTO(
                70.0,
                new ComponentScoresDTO(70.0, 70.0, 70.0, 70.0, null, null, null),
                null,
                70.0,
                null
        );
        when(cqiCalculatorService.calculate(any(), anyInt(), any(), any(), any(), anyString(), anyString())).thenReturn(cqiResult);

        FairnessReportWithUsageDTO reportWithUsage = fairnessService.analyzeFairnessWithUsage(dummyRepo);

        assertEquals(2, reportWithUsage.tokenTotals().llmCalls());
        assertEquals(1, reportWithUsage.tokenTotals().callsWithUsage());
        assertEquals(100, reportWithUsage.tokenTotals().promptTokens());
        assertEquals(20, reportWithUsage.tokenTotals().completionTokens());
        assertEquals(120, reportWithUsage.tokenTotals().totalTokens());
        assertNotNull(reportWithUsage.report());
        assertNotNull(reportWithUsage.report().analyzedChunks());
        assertEquals(2, reportWithUsage.report().analyzedChunks().size());
        assertNotNull(reportWithUsage.report().analyzedChunks().get(0).llmTokenUsage());
        assertEquals(120, reportWithUsage.report().analyzedChunks().get(0).llmTokenUsage().totalTokens());
        assertFalse(reportWithUsage.report().analyzedChunks().get(1).llmTokenUsage().usageAvailable());
    }

    @Test
    void testAnalyzeFairness_noCommitsReturnsError() {
        // Clear the setUp stub and return empty mapping instead
        reset(gitContributionAnalysisService);
        CommitMappingResultDTO emptyMapping = new CommitMappingResultDTO(
                Map.of(), Map.of(), Map.of());
        when(gitContributionAnalysisService.mapCommitToAuthor(any(TeamRepositoryDTO.class),
                nullable(String.class)))
                .thenReturn(emptyMapping);

        FairnessReportDTO report = fairnessService.analyzeFairness(dummyRepo);

        assertNotNull(report);
        assertEquals("Team 1", report.teamId());
        assertTrue(report.error(), "Should be marked as error when no team member commits found");
        assertEquals(0.0, report.balanceScore());
    }

    private CommitEffortRaterService.RatingWithUsage ratingWithUsage(EffortRatingDTO rating, LlmTokenUsageDTO usage) {
        return new CommitEffortRaterService.RatingWithUsage(rating, usage);
    }
}
