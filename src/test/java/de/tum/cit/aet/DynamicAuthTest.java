package de.tum.cit.aet;

import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class DynamicAuthTest {

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private GitOperationsService gitOperationsService;

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

        // 2. Fetch participations and clone repositories
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations(
                credentials.serverUrl(), credentials.jwtToken(), 18806L);
        List<TeamRepositoryDTO> teamRepositories = participations.parallelStream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(p -> gitOperationsService.cloneAndFetchLogs(p, credentials, 18806L))
                .toList();

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
