package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus;

import java.util.List;

/**
 * Response DTO sent to the client containing a team's full analysis results,
 * including CQI, student contributions, commit history, and LLM usage.
 *
 * @param tutor            the assigned tutor name
 * @param teamId           the Artemis team ID
 * @param teamName         the team display name
 * @param submissionCount  number of submissions
 * @param students         list of student contribution summaries
 * @param cqi              final CQI score
 * @param isSuspicious     whether the team is flagged as suspicious
 * @param analysisStatus   current analysis status
 * @param cqiDetails       detailed CQI breakdown with component scores
 * @param analysisHistory  list of analyzed chunks from AI analysis
 * @param orphanCommits    list of commits that could not be attributed
 * @param llmTokenTotals   aggregated LLM token usage
 * @param orphanCommitCount number of orphan commits
 * @param isFailed         whether the analysis failed for this team
 * @param isReviewed       whether the team has been marked as reviewed
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
                TeamAnalysisStatus analysisStatus,
                CQIResultDTO cqiDetails,
                List<AnalyzedChunkDTO> analysisHistory,
                List<OrphanCommitDTO> orphanCommits,
                LlmTokenTotalsDTO llmTokenTotals,
                Integer orphanCommitCount,
                Boolean isFailed,
                Boolean isReviewed) {

        /**
         * Constructor for backward compatibility without isReviewed.
         */
        public ClientResponseDTO(
                        String tutor,
                        Long teamId,
                        String teamName,
                        Integer submissionCount,
                        List<StudentAnalysisDTO> students,
                        Double cqi,
                        Boolean isSuspicious,
                        TeamAnalysisStatus analysisStatus,
                        CQIResultDTO cqiDetails,
                        List<AnalyzedChunkDTO> analysisHistory,
                        List<OrphanCommitDTO> orphanCommits,
                        LlmTokenTotalsDTO llmTokenTotals,
                        Integer orphanCommitCount,
                        Boolean isFailed) {
                this(tutor, teamId, teamName, submissionCount, students, cqi, isSuspicious, analysisStatus,
                                cqiDetails, analysisHistory, orphanCommits, llmTokenTotals, orphanCommitCount, isFailed, null);
        }

        /**
         * Constructor for backward compatibility without orphanCommitCount, isFailed, or isReviewed.
         */
        public ClientResponseDTO(
                        String tutor,
                        Long teamId,
                        String teamName,
                        Integer submissionCount,
                        List<StudentAnalysisDTO> students,
                        Double cqi,
                        Boolean isSuspicious,
                        TeamAnalysisStatus analysisStatus,
                        CQIResultDTO cqiDetails,
                        List<AnalyzedChunkDTO> analysisHistory,
                        List<OrphanCommitDTO> orphanCommits,
                        LlmTokenTotalsDTO llmTokenTotals) {
                this(tutor, teamId, teamName, submissionCount, students, cqi, isSuspicious, analysisStatus,
                                cqiDetails, analysisHistory, orphanCommits, llmTokenTotals, null, null, null);
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
                        TeamAnalysisStatus analysisStatus,
                        CQIResultDTO cqiDetails,
                        List<AnalyzedChunkDTO> analysisHistory,
                        List<OrphanCommitDTO> orphanCommits) {
                this(tutor, teamId, teamName, submissionCount, students, cqi, isSuspicious, analysisStatus,
                                cqiDetails, analysisHistory, orphanCommits, null, null, null, null);
        }
}
