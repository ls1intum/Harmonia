package de.tum.cit.aet;

import de.tum.cit.aet.repositoryProcessing.service.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class HarmoniaApplicationTests {

    @SuppressWarnings("unused")
    @Autowired
    private ArtemisClientService artemisClientService;

    @SuppressWarnings("unused")
    @Autowired
    private GitOperationsService gitOperationsService;

    @Test
    void contextLoads() {
    }

}
