package de.tum.cit.aet.export.dto;

public record TeamExportRow(
        String teamName,
        String tutor,
        Integer submissionCount,
        String analysisStatus,
        Double cqi,
        Double cqiEffortBalance,
        Double cqiLocBalance,
        Double cqiTemporalSpread,
        Double cqiOwnershipSpread,
        Boolean isSuspicious,
        Long llmTotalTokens) {
}
