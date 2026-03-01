package de.tum.cit.aet;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Tag("integration")
@SpringBootTest
class DynamicAuthTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicAuthTest.class);

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private GitOperationsService gitOperationsService;

    @Test
    void testDynamicAuthenticationAndCloning() {
        TestCredentialsLoader loader = new TestCredentialsLoader();
        if (!loader.isAvailable()) {
            log.info("Skipping test: {}", loader.getSkipMessage());
            return;
        }

        log.info("Starting Dynamic Auth Test...");

        // 1. Authenticate
        String jwtToken = artemisClientService.authenticate(
                loader.getServerUrl(), loader.getUsername(), loader.getPassword());
        log.info("Authentication successful. JWT: {}...", jwtToken.substring(0, Math.min(jwtToken.length(), 10)));

        // 2. Fetch participations and clone repositories
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), 18806L);
        List<TeamRepositoryDTO> teamRepositories = participations.parallelStream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> gitOperationsService.cloneAndFetchLogs(p, credentials, 18806L))
                .toList();

        log.info("Fetched {} repositories.", teamRepositories.size());
        teamRepositories.forEach(repo -> {
            log.info("Team: {}", repo.participation().team() != null ? repo.participation().team().name() : "Unknown");
            log.info("Cloned: {}", repo.isCloned());
            log.info("Path: {}", repo.localPath());
            if (!repo.isCloned()) {
                log.info("Error: {}", repo.error());
            }
            log.info("---");
        });
    }
}
