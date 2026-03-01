package de.tum.cit.aet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Artemis LMS integration ({@code artemis.*}).
 * Binds connection credentials, exercise context, and repository settings.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "artemis")
public class ArtemisConfig {

    private String baseUrl;
    private String username;
    private String password;
    private String jwtToken;
    private Long exerciseId;
    private String gitRepoPath;
    private Integer numThreads;
}
