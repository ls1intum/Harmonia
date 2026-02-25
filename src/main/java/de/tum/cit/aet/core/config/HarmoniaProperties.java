package de.tum.cit.aet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Root configuration properties for the Harmonia application ({@code harmonia.*}).
 * Binds user credentials, CORS settings, and project definitions from the application config.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "harmonia")
public class HarmoniaProperties {

    private String user;
    private String password;
    private Cors cors = new Cors();
    private List<Project> projects;

    /**
     * CORS-specific properties.
     */
    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins;
    }

    /**
     * Represents a single project/course configuration entry.
     */
    @Getter
    @Setter
    public static class Project {
        private String id;
        private String courseName;
        private String semester;
        private Long exerciseId;
        private String gitRepoPath;
    }
}
