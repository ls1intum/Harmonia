package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitClassificationDTO(
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
