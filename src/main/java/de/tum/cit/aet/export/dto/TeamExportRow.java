package de.tum.cit.aet.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
