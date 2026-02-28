package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.repositoryProcessing.domain.AnalysisStatus;

import java.util.List;

/**
 * Lean DTO for the Teams list page.
 * Excludes heavy fields: analysisHistory, orphanCommits, codeQualityScore, gitLogOutput.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamSummaryDTO(
        Long teamId,
        String teamName,
        String tutor,
        AnalysisStatus analysisStatus,
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
