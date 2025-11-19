package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/requestResource/")
@Slf4j
public class RequestResource {

    private final RequestService requestService;

    @Autowired
    public RequestResource(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * GET endpoint to fetch and clone all repositories.
     * Triggers the fetching of participations from Artemis and clones/pulls all repositories.
     *
     * @return ResponseEntity containing the list of TeamRepositoryDTO
     */
    @GetMapping("fetchAndCloneRepositories")
    public ResponseEntity<List<TeamRepositoryDTO>> fetchAndCloneRepositories() {
        log.info("GET request received: fetchAndCloneRepositories");
        List<TeamRepositoryDTO> repositories = requestService.fetchAndCloneRepositories();
        log.info("Successfully fetched and cloned {} repositories", repositories.size());
        return ResponseEntity.ok(repositories);
    }
}
