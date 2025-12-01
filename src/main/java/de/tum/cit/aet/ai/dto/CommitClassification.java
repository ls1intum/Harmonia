package de.tum.cit.aet.ai.dto;

public record CommitClassification(
    CommitLabel label,
    double confidence,
    String reasoning
) {
    public enum CommitLabel {
        FEATURE,
        BUG_FIX,
        TEST,
        REFACTOR,
        TRIVIAL
    }
}
