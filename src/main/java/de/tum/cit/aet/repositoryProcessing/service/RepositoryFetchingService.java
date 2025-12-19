package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.HarmoniaProperties;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTOBuilder;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
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
    private final HarmoniaProperties harmoniaProperties;

    @Autowired
    public RepositoryFetchingService(ArtemisClientService artemisClientService, GitOperationsService gitOperationsService, HarmoniaProperties harmoniaProperties) {
        this.artemisClientService = artemisClientService;
        this.gitOperationsService = gitOperationsService;
        this.harmoniaProperties = harmoniaProperties;
    }

    /**
     * Fetches all team repositories from Artemis and clones/pulls them locally.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        log.info("Starting repository fetching process");

        // Step 1: Fetch participations from Artemis
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);

        // Step 2: Filter participations with repositories and clone them in parallel
        List<TeamRepositoryDTO> teamRepositories = participations.parallelStream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> cloneTeamRepository(p, credentials, exerciseId))
                .toList();

        log.info("Completed repository fetching. Total repositories: {}", teamRepositories.size());
        return teamRepositories;
    }


    /**
     * Fetches participations from Artemis.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID
     * @return List of ParticipationDTO
     */
    public List<ParticipationDTO> fetchParticipations(ArtemisCredentials credentials, Long exerciseId) {
        return artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), exerciseId);
    }

    /**
     * Clones a team repository from Artemis.
     *
     * @param participation Participation data
     * @param credentials Artemis credentials
     * @param exerciseId Exercise ID
     * @return Team repository DTO with clone information
     */
    public TeamRepositoryDTO cloneTeamRepository(ParticipationDTO participation, ArtemisCredentials credentials, Long exerciseId) {
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

            // Find gitRepoPath for this exercise
            String gitRepoPath = harmoniaProperties.getProjects().stream()
                    .filter(p -> p.getExerciseId().equals(exerciseId))
                    .findFirst()
                    .map(HarmoniaProperties.Project::getGitRepoPath)
                    .orElse("Projects/repos");

            String localPath = gitOperationsService.cloneOrPullRepository(
                    repositoryUri, teamName, credentials.username(), credentials.password(), gitRepoPath);

            List<VCSLogDTO> vcsLogs = fetchVCSAccessLog(credentials, participation.id());

            builder.localPath(localPath)
                    .isCloned(true)
                    .vcsLogs(vcsLogs);

            log.info("Successfully processed repository for team: {}", teamName);

        } catch (Exception e) {
            log.error("Failed to fetch repository for team: {}", teamName, e);
            builder.isCloned(false)
                    .error(e.getMessage())
                    .vcsLogs(List.of()); // Empty list instead of null
        }

        return builder.build();
    }

    /**
     * Fetches the VCS access log for a specific participation from Artemis.
     *
     * @param credentials     The Artemis credentials
     * @param participationId The ID of the participation
     * @return List of VCSLogDTO containing VCS access log entries
     */
    private List<VCSLogDTO> fetchVCSAccessLog(ArtemisCredentials credentials, Long participationId) {
        List<VCSLogDTO> vcsLogs = artemisClientService.fetchVCSAccessLog(
                credentials.serverUrl(), credentials.jwtToken(), participationId);

        log.info("Fetched {} VCS access log entries", vcsLogs.size());
        return vcsLogs;
    }
}
