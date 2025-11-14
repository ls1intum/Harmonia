package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.repositoryProcessing.domain.TeamRepository;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamRepositoryDTO(UUID teamRepositoryDTO) {

    /**
     * @return The teamRepositoryDTO from the teamRepository
     */
    public static TeamRepositoryDTO getFromEntity(TeamRepository teamRepository) {
        if (teamRepository == null) {
            return null;
        }

        return new TeamRepositoryDTO(
                teamRepository.getTeamRepositoryId()
        );
    }
}
