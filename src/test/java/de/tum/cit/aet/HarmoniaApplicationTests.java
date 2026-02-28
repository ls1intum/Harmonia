package de.tum.cit.aet;

import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.repositoryProcessing.service.GitOperationsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
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
