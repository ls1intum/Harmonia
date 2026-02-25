package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus;

import java.util.List;

/**
 * Response DTO sent to the client containing a team's full analysis results,
 * including CQI, student contributions, commit history, and LLM usage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponseDTO(
                String tutor,
                Long teamId,
                String teamName,
                Integer submissionCount,
                List<StudentAnalysisDTO> students,
                Double cqi,
                Boolean isSuspicious,
                AnalysisStatus analysisStatus,
                CQIResultDTO cqiDetails,
                List<AnalyzedChunkDTO> analysisHistory,
                List<OrphanCommitDTO> orphanCommits,
                LlmTokenTotalsDTO llmTokenTotals,
                Integer orphanCommitCount) {

        /**
         * Constructor for backward compatibility without orphanCommitCount.
         */
        public ClientResponseDTO(
                        String tutor,
                        Long teamId,
                        String teamName,
                        Integer submissionCount,
                        List<StudentAnalysisDTO> students,
                        Double cqi,
                        Boolean isSuspicious,
                        AnalysisStatus analysisStatus,
                        CQIResultDTO cqiDetails,
                        List<AnalyzedChunkDTO> analysisHistory,
                        List<OrphanCommitDTO> orphanCommits,
                        LlmTokenTotalsDTO llmTokenTotals) {
                this(tutor, teamId, teamName, submissionCount, students, cqi, isSuspicious, analysisStatus,
                                cqiDetails, analysisHistory, orphanCommits, llmTokenTotals, null);
        }

        /**
         * Constructor for backward compatibility without llmTokenTotals.
         */
        public ClientResponseDTO(
                        String tutor,
                        Long teamId,
                        String teamName,
                        Integer submissionCount,
                        List<StudentAnalysisDTO> students,
                        Double cqi,
                        Boolean isSuspicious,
                        AnalysisStatus analysisStatus,
                        CQIResultDTO cqiDetails,
                        List<AnalyzedChunkDTO> analysisHistory,
                        List<OrphanCommitDTO> orphanCommits) {
                this(tutor, teamId, teamName, submissionCount, students, cqi, isSuspicious, analysisStatus,
                                cqiDetails, analysisHistory, orphanCommits, null, null);
        }
}
