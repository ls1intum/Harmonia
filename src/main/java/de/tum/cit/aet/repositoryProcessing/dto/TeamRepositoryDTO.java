package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a team's repository information and analysis results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamRepositoryDTO(
        String teamName,
        String repositoryUri,
        String localPath,
        Integer commitCount,
        Boolean isCloned,
        String error
) {
    /**
     * Creates a builder for TeamRepositoryDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for immutable TeamRepositoryDTO records.
     */
    public static class Builder {
        private String teamName;
        private String repositoryUri;
        private String localPath;
        private Integer commitCount;
        private Boolean isCloned;
        private String error;

        public Builder teamName(String teamName) {
            this.teamName = teamName;
            return this;
        }

        public Builder repositoryUri(String repositoryUri) {
            this.repositoryUri = repositoryUri;
            return this;
        }

        public Builder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }

        public Builder commitCount(Integer commitCount) {
            this.commitCount = commitCount;
            return this;
        }

        public Builder isCloned(Boolean isCloned) {
            this.isCloned = isCloned;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public TeamRepositoryDTO build() {
            return new TeamRepositoryDTO(teamName, repositoryUri, localPath, commitCount, isCloned, error);
        }
    }

    /**
     * Creates a copy with updated commitCount.
     */
    public TeamRepositoryDTO withCommitCount(Integer commitCount) {
        return new TeamRepositoryDTO(teamName, repositoryUri, localPath, commitCount, isCloned, error);
    }

    /**
     * Creates a copy with updated error.
     */
    public TeamRepositoryDTO withError(String error) {
        return new TeamRepositoryDTO(teamName, repositoryUri, localPath, commitCount, isCloned, error);
    }
}