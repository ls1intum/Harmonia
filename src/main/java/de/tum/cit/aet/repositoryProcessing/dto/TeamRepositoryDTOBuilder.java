package de.tum.cit.aet.repositoryProcessing.dto;

import java.util.List;

/**
 * Builder for immutable TeamRepositoryDTO records.
 */
public class TeamRepositoryDTOBuilder {
    private ParticipationDTO participation;
    private List<VCSLogDTO> vcsLogs;
    private String localPath;
    private Boolean isCloned;
    private String error;

    public TeamRepositoryDTOBuilder participation(ParticipationDTO participation) {
        this.participation = participation;
        return this;
    }

    public TeamRepositoryDTOBuilder vcsLogs(List<VCSLogDTO> vcsLogs) {
        this.vcsLogs = vcsLogs;
        return this;
    }

    public TeamRepositoryDTOBuilder localPath(String localPath) {
        this.localPath = localPath;
        return this;
    }


    public TeamRepositoryDTOBuilder isCloned(Boolean isCloned) {
        this.isCloned = isCloned;
        return this;
    }

    public TeamRepositoryDTOBuilder error(String error) {
        this.error = error;
        return this;
    }

    public TeamRepositoryDTO build() {
        return new TeamRepositoryDTO(participation, vcsLogs, localPath, isCloned, error);
    }
}
