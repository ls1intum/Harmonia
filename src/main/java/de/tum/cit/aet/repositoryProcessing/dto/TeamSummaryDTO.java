package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.repositoryProcessing.domain.TeamAnalysisStatus;

import java.util.List;

/**
 * Lean DTO for the Teams list page.
 * Excludes heavy fields: analysisHistory, orphanCommits, codeQualityScore, gitLogOutput.
 *
 * @param teamId           the Artemis team ID
 * @param teamName         the team display name
 * @param shortName        the team short name (optional, for pair programming attendance fallback)
 * @param tutor            the assigned tutor name
 * @param analysisStatus   current analysis status
 * @param cqi              final CQI score
 * @param isSuspicious     whether the team is flagged as suspicious
 * @param students         list of lean student summaries
 * @param cqiDetails       detailed CQI breakdown with component scores
 * @param llmTokenTotals   aggregated LLM token usage
 * @param orphanCommitCount number of orphan commits
 * @param isFailed         whether the analysis failed for this team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamSummaryDTO(
        Long teamId,
        String teamName,
        String shortName,
        String tutor,
        TeamAnalysisStatus analysisStatus,
        Double cqi,
        Boolean isSuspicious,
        List<StudentSummaryDTO> students,
        CQIResultDTO cqiDetails,
        LlmTokenTotalsDTO llmTokenTotals,
        Integer orphanCommitCount,
        Boolean isFailed
) {
    /**
     * Create a summary from a full ClientResponseDTO.
     */
    public static TeamSummaryDTO fromClientResponse(ClientResponseDTO dto) {
        List<StudentSummaryDTO> studentSummaries = dto.students() != null
                ? dto.students().stream().map(StudentSummaryDTO::from).toList()
                : null;

        return new TeamSummaryDTO(
                dto.teamId(),
                dto.teamName(),
                dto.shortName(),
                dto.tutor(),
                dto.analysisStatus(),
                dto.cqi(),
                dto.isSuspicious(),
                studentSummaries,
                dto.cqiDetails(),
                dto.llmTokenTotals(),
                dto.orphanCommitCount(),
                dto.isFailed());
    }
}
