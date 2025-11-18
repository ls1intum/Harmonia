package de.tum.cit.aet;

import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HarmoniaApplicationTests {

    @Autowired
    private ArtemisClientService artemisClientService;

    @Autowired
    private RepositoryFetchingService repositoryFetchingService;

    @Test
    void testRepositoryCloningAndPulling() {
        List<TeamRepositoryDTO> teamRepositories = repositoryFetchingService.fetchAndCloneRepositories();
        System.out.println(teamRepositories);
    }

    @Test
    void testRepositoryFetching() {
        List<ParticipationDTO> teamRepositories = artemisClientService.fetchParticipations();
        for (ParticipationDTO participationDTO : teamRepositories) {
            System.out.println(participationDTO);
        }
    }

    @Test
    void contextLoads() {
    }

}
