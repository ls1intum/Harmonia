package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RequestService {

    private final RepositoryFetchingService repositoryFetchingService;

    @Autowired
    public RequestService(RepositoryFetchingService repositoryFetchingService) {
        this.repositoryFetchingService = repositoryFetchingService;
    }

    /**
     * Fetches and clones all repositories from Artemis using dynamic credentials.
     *
     * @param serverUrl The Artemis server URL
     * @param jwtToken  The JWT token
     * @param username  The username (optional, for fallback)
     * @param password  The password (optional, for fallback)
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(String serverUrl, String jwtToken, String username, String password) {
        log.info("RequestService: Initiating repository fetch and clone process (Dynamic Auth)");
        return repositoryFetchingService.fetchAndCloneRepositories(serverUrl, jwtToken, username, password);
    }
}
