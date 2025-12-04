package de.tum.cit.aet.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitClassificationRequestDTO(
    String sha,
    String message,
    List<String> filePaths,
    String diffContent
) {
    public CommitClassificationRequestDTO(String sha, String message, List<String> filePaths) {
        this(sha, message, filePaths, null);
    }
}
