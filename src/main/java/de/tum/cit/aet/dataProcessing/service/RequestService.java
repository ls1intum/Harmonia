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
     * Fetches and clones all repositories from Artemis.
     *
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories() {
        log.info("RequestService: Initiating repository fetch and clone process");
        return repositoryFetchingService.fetchAndCloneRepositories();
    }
}
