package de.tum.cit.aet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "artemis")
@Getter
@Setter
public class ArtemisConfig {

    private Long exerciseId;
    private String gitRepoPath;
    private Integer numThreads;
}
