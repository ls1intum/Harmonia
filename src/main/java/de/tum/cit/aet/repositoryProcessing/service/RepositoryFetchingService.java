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
     * Fetches all team repositories from Artemis and clones/pulls them locally using dynamic credentials.
     *
     * @param serverUrl The Artemis server URL
     * @param jwtToken  The JWT token
     * @param username  The username (optional, for fallback)
     * @param password  The password (optional, for fallback)
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(String serverUrl, String jwtToken, String username, String password) {
        log.info("Starting repository fetching process (Dynamic Auth)");

        // Step 1: Fetch participations from Artemis
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(serverUrl, jwtToken);

        // Step 2: Filter participations with repositories and clone them
        List<TeamRepositoryDTO> teamRepositories = participations.stream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> cloneTeamRepository(p, serverUrl, jwtToken, username, password))
                .toList();

        log.info("Completed repository fetching. Total repositories: {}", teamRepositories.size());
        return teamRepositories;
    }

    private TeamRepositoryDTO cloneTeamRepository(ParticipationDTO participation, String serverUrl, String jwtToken, String username, String password) {
        String teamName = participation.team() != null
                ? participation.team().name()
                : "Unknown Team";
        String repositoryUri = participation.repositoryUri();

        TeamRepositoryDTOBuilder builder = TeamRepositoryDTO.builder()
                .participation(participation);

        try {
            String localPath;
            if (username != null && password != null) {
                // Instructors always use username/password as they cannot generate VCS tokens for student repos
                localPath = gitOperationsService.cloneOrPullRepository(repositoryUri, teamName, username, password);
            } else {
                throw new RuntimeException("No credentials provided for cloning. Username and password are required for instructors.");
            }

            builder.localPath(localPath)
                    .isCloned(true);

            log.info("Successfully processed repository for team: {} (Dynamic Auth)", teamName);

        } catch (Exception e) {
            log.error("Failed to clone repository for team: {} (Dynamic Auth)", teamName, e);
            builder.isCloned(false)
                    .error(e.getMessage());
        }

        return builder.build();
    }
}
