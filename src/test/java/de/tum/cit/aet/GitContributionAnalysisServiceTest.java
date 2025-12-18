package de.tum.cit.aet;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class GitContributionAnalysisServiceTest {

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private RequestService requestService;

    private final GitContributionAnalysisService gitContributionAnalysisService = new GitContributionAnalysisService();

    @Test
    void testFetchingAndAnalyzing() {
        TestCredentialsLoader loader = new TestCredentialsLoader();
        if (!loader.isAvailable()) {
            System.out.println("Skipping test: " + loader.getSkipMessage());
            return;
        }

        System.out.println("Starting VCS Access Log Test...");

        // 1. Authenticate
        String jwtToken = artemisClientService.authenticate(
                loader.getServerUrl(), loader.getUsername(), loader.getPassword());
        System.out.println("Authentication successful. JWT: " + jwtToken.substring(0, Math.min(jwtToken.length(), 10)) + "...");

        // 2. Fetch and Clone using credentials DTO
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        List<TeamRepositoryDTO> repos = requestService.fetchAndCloneRepositories(credentials, 18806L);

        // 3. Analyze contributions
        Map<Long, AuthorContributionDTO> map = gitContributionAnalysisService.processAllRepositories(repos);
        for (Long studentId : map.keySet()) {
            System.out.println("Processed student ID: " + studentId);
            AuthorContributionDTO contributions = map.get(studentId);
            System.out.println("Added = " + contributions.linesAdded() + ", Deleted = " + contributions.linesDeleted());
        }
    }

    @Test
    void testSaving() {
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

        // 2. Fetch, Analyze and Save
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        requestService.fetchAnalyzeAndSaveRepositories(credentials, 18806L);
    }

    @Test
    void testDatabase() {
        testSaving();

        // 3. Fetch from database
        List<ClientResponseDTO> test = requestService.getAllRepositoryData();
        System.out.println("Fetched " + test.size() + " entries from database.");
    }
}
