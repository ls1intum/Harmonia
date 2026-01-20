package de.tum.cit.aet.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "harmonia.ai")
@Data
public class AiProperties {

    private boolean enabled = true;
    private CommitClassifier commitClassifier = new CommitClassifier();
    private AnomalyDetector anomalyDetector = new AnomalyDetector();
    private FileClustering fileClustering = new FileClustering();

    @Data
    public static class CommitClassifier {
        private boolean enabled = true;
        private double confidenceThreshold = 0.7;
        private String modelName; // Will be auto-detected if null
    }

    @Data
    public static class AnomalyDetector {
        private boolean enabled = true;
        private double confidenceThreshold = 0.7;
        private String modelName; // Will be auto-detected if null
    }

    @Data
    public static class FileClustering {
        private boolean enabled = false;
    }
}
