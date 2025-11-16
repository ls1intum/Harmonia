package de.tum.cit.aet;

import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HarmoniaApplicationTests {

    @Autowired
    private GitOperationsService gitOperationsService;

    @Autowired
    private ArtemisClientService artemisClientService;

    @Test
    void testRepositoryCloning() {
        List<ParticipationDTO> participations = artemisClientService.fetchParticipations();

        List<TeamRepositoryDTO> teamRepositories = participations.stream()
                .filter(p -> p.repositoryUri() != null && !p.repositoryUri().isEmpty())
                .map(gitOperationsService::cloneTeamRepository)
                .toList();

        System.out.println(teamRepositories);
    }

    @Test
    void contextLoads() {
    }

}
