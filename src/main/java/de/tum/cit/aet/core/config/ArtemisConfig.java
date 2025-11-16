package de.tum.cit.aet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class ArtemisConfig {

    @Value("${artemis.baseUrl}")
    private String baseUrl;

    @Value("${artemis.username}")
    private String username;

    @Value("${artemis.password}")
    private String password;

    @Value("${artemis.jwtToken}")
    private String jwtToken;

    @Value("${artemis.exerciseId}")
    private Long exerciseId;

    @Value("${artemis.gitRepoPath}")
    private String gitRepoPath;

    @Value("${artemis.numThreads}")
    private Integer numThreads;
}