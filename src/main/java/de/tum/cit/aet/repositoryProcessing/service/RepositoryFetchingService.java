package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
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
     * @param credentials The Artemis credentials
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials) {
        log.info("Starting repository fetching process");

        // Step 1: Fetch participations from Artemis
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken());

        // Step 2: Filter participations with repositories and clone them
        List<TeamRepositoryDTO> teamRepositories = participations.stream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> cloneTeamRepository(p, credentials))
                .toList();

        log.info("Completed repository fetching. Total repositories: {}", teamRepositories.size());
        return teamRepositories;
    }

    private TeamRepositoryDTO cloneTeamRepository(ParticipationDTO participation, ArtemisCredentials credentials) {
        String teamName = participation.team() != null
                ? participation.team().name()
                : "Unknown Team";
        String repositoryUri = participation.repositoryUri();

        TeamRepositoryDTOBuilder builder = TeamRepositoryDTO.builder()
                .participation(participation);

        try {
            if (!credentials.hasGitCredentials()) {
                throw new IllegalStateException("No credentials provided for cloning. Username and password are required.");
            }

            String localPath = gitOperationsService.cloneOrPullRepository(
                    repositoryUri, teamName, credentials.username(), credentials.password());

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
