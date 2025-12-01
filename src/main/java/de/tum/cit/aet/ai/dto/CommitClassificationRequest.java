package de.tum.cit.aet.ai.dto;

import java.util.List;

public record CommitClassificationRequest(
    String sha,
    String message,
    List<String> filePaths,
    String diffContent
) {
    public CommitClassificationRequest(String sha, String message, List<String> filePaths) {
        this(sha, message, filePaths, null);
    }
}
