package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.analysis.dto.OrphanCommitDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;

import java.util.List;

/**
 * DTO representing the response sent to clients.
 *
 * @param tutor           The name of the tutor associated with the team.
 * @param teamId          The unique identifier of the team.
 * @param teamName        The full name of the team.
 * @param submissionCount The number of submissions made by the team.
 * @param students        A list of StudentAnalysisDTO representing individual
 *                        student analyses within the team.
 * @param cqi             The Collaboration Quality Index score.
 * @param isSuspicious    Whether the team's collaboration pattern is flagged as
 *                        suspicious.
 * @param cqiDetails      Detailed CQI breakdown with component scores and penalties.
 * @param analysisHistory The detailed AI analysis results for each commit
 *                        chunk.
 * @param orphanCommits   Commits that couldn't be attributed to any registered
 *                        student.
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
        CQIResultDTO cqiDetails,
        List<AnalyzedChunkDTO> analysisHistory,
        List<OrphanCommitDTO> orphanCommits) {
}
