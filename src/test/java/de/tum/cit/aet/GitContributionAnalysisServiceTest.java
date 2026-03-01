package de.tum.cit.aet;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.service.GitContributionAnalysisService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.dataProcessing.service.RequestService;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.artemis.ArtemisClientService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@Tag("integration")
@SpringBootTest
class GitContributionAnalysisServiceTest {

    private static final Logger log = LoggerFactory.getLogger(GitContributionAnalysisServiceTest.class);

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private RequestService requestService;

    private final GitContributionAnalysisService gitContributionAnalysisService = new GitContributionAnalysisService();

    @Test
    void testFetchingAndAnalyzing() {
        TestCredentialsLoader loader = new TestCredentialsLoader();
        if (!loader.isAvailable()) {
            log.info("Skipping test: {}", loader.getSkipMessage());
            return;
        }

        log.info("Starting VCS Access Log Test...");

        // 1. Authenticate
        String jwtToken = artemisClientService.authenticate(
                loader.getServerUrl(), loader.getUsername(), loader.getPassword());
        log.info("Authentication successful. JWT: {}...", jwtToken.substring(0, Math.min(jwtToken.length(), 10)));

        // 2. Fetch and Clone using credentials DTO
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        List<TeamRepositoryDTO> repos = requestService.fetchAndCloneRepositories(credentials, 18806L);

        // 3. Analyze contributions
        Map<Long, AuthorContributionDTO> map = gitContributionAnalysisService.processAllRepositories(repos);
        for (Long studentId : map.keySet()) {
            log.info("Processed student ID: {}", studentId);
            AuthorContributionDTO contributions = map.get(studentId);
            log.info("Added = {}, Deleted = {}", contributions.linesAdded(), contributions.linesDeleted());
        }
    }

    @Test
    void testSaving() {
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

        // 2. Fetch, Analyze and Save
        ArtemisCredentials credentials = loader.getCredentials(jwtToken);
        requestService.fetchAnalyzeAndSaveRepositories(credentials, 18806L);
    }

    @Test
    void testDatabase() {
        testSaving();

        // 3. Fetch from database
        List<ClientResponseDTO> test = requestService.getAllRepositoryData();
        log.info("Fetched {} entries from database.", test.size());
    }
}
