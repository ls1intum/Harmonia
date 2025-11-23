package de.tum.cit.aet;

import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class DynamicAuthTest {

    // TODO: Enter your credentials here to run the dynamic auth test manually
    private static final String SERVER_URL = "https://artemis-test2.artemis.cit.tum.de";
    private static final String USERNAME = "REPLACE_WITH_USERNAME";
    private static final String PASSWORD = "REPLACE_WITH_PASSWORD";

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private RepositoryFetchingService repositoryFetchingService;

    @Test
    void testDynamicAuthenticationAndCloning() {
        if (USERNAME.equals("REPLACE_WITH_USERNAME")) {
            System.out.println("Skipping testDynamicAuthenticationAndCloning: Credentials not set.");
            return;
        }

        System.out.println("Starting Dynamic Auth Test...");

        // 1. Authenticate
        String jwtToken = artemisClientService.authenticate(SERVER_URL, USERNAME, PASSWORD);
        System.out.println("Authentication successful. JWT: " + jwtToken.substring(0, Math.min(jwtToken.length(), 10)) + "...");

        // 2. Fetch and Clone using the token (and fallback credentials)
        List<TeamRepositoryDTO> teamRepositories = repositoryFetchingService.fetchAndCloneRepositories(SERVER_URL, jwtToken, USERNAME, PASSWORD);

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
