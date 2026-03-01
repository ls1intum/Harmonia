package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export row for team-level data.
 *
 * @param teamName                team display name
 * @param shortName               team short name
 * @param tutor                   assigned tutor name
 * @param repositoryUrl           repository URL (credentials stripped)
 * @param submissionCount         number of submissions
 * @param analysisStatus          current analysis status
 * @param cqi                     final CQI score
 * @param cqiEffortBalance        effort balance component score
 * @param cqiLocBalance           LoC balance component score
 * @param cqiTemporalSpread       temporal spread component score
 * @param cqiOwnershipSpread      ownership spread component score
 * @param cqiPairProgramming      pair programming component score
 * @param cqiPairProgrammingStatus pair programming status
 * @param isSuspicious            whether the team is flagged as suspicious
 * @param llmTotalTokens          total LLM tokens used for this team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamExportRow(
        String teamName,
        String shortName,
        String tutor,
        String repositoryUrl,
        Integer submissionCount,
        String analysisStatus,
        Double cqi,
        Double cqiEffortBalance,
        Double cqiLocBalance,
        Double cqiTemporalSpread,
        Double cqiOwnershipSpread,
        Double cqiPairProgramming,
        String cqiPairProgrammingStatus,
        Boolean isSuspicious,
        Long llmTotalTokens) {
}
