package de.tum.cit.aet;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class DynamicAuthTest {

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private RepositoryFetchingService repositoryFetchingService;

    @Test
    void testDynamicAuthenticationAndCloning() {
        TestCredentialsLoader loader = new TestCredentialsLoader();
        if (!loader.isAvailable()) {
            System.out.println("Skipping test: " + loader.getSkipMessage());
            return;
        }

        System.out.println("Starting Dynamic Auth Test...");

        // 1. Authenticate
        String jwtToken = artemisClientService.authenticate(
                loader.getServerUrl(), loader.getUsername(), loader.getPassword());
        System.out.println("Authentication successful. JWT: " + jwtToken.substring(0, Math.min(jwtToken.length(), 10)) + "...");

        // 2. Fetch and Clone using credentials DTO
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        List<TeamRepositoryDTO> teamRepositories = repositoryFetchingService.fetchAndCloneRepositories(credentials, 18806L);

        System.out.println("Fetched " + teamRepositories.size() + " repositories.");
        teamRepositories.forEach(repo -> {
            System.out.println("Team: " + (repo.participation().team() != null ? repo.participation().team().name() : "Unknown"));
            System.out.println("Cloned: " + repo.isCloned());
            System.out.println("Path: " + repo.localPath());
            if (!repo.isCloned()) {
                System.out.println("Error: " + repo.error());
            }
            System.out.println("---");
        });
    }
}
