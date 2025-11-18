package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTOBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for fetching repository information from Artemis
 * and coordinating the cloning/pulling of repositories.
 */
@Service
@Slf4j
public class RepositoryFetchingService {

    private final ArtemisClientService artemisClientService;
    private final GitOperationsService gitOperationsService;

    @Autowired
    public RepositoryFetchingService(ArtemisClientService artemisClientService, GitOperationsService gitOperationsService) {
        this.artemisClientService = artemisClientService;
        this.gitOperationsService = gitOperationsService;
    }

    /**
     * Fetches all team repositories from Artemis and clones/pulls them locally.
     *
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories() {
        log.info("Starting repository fetching process");

        // Step 1: Fetch participations from Artemis
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations();

        // Step 2: Filter participations with repositories and clone them
        List<TeamRepositoryDTO> teamRepositories = participations.stream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(this::cloneTeamRepository)
                .toList();

        log.info("Completed repository fetching. Total repositories: {}", teamRepositories.size());
        return teamRepositories;
    }

    /**
     * Clones a single team repository and creates a TeamRepositoryDTO.
     *
     * @param participation The participation containing repository information
     * @return TeamRepositoryDTO with clone status and information
     */
    private TeamRepositoryDTO cloneTeamRepository(ParticipationDTO participation) {
        String teamName = participation.team() != null
                ? participation.team().name()
                : "Unknown Team";
        String repositoryUri = participation.repositoryUri();

        TeamRepositoryDTOBuilder builder = TeamRepositoryDTO.builder()
                .participation(participation);
        try {
            String localPath = gitOperationsService.cloneOrPullRepository(repositoryUri, teamName);

            builder.localPath(localPath)
                    .isCloned(true);

            log.info("Successfully processed repository for team: {}", teamName);

        } catch (Exception e) {
            log.error("Failed to clone repository for team: {}", teamName, e);
            builder.isCloned(false)
                    .error(e.getMessage());
        }

        return builder.build();
    }
}
