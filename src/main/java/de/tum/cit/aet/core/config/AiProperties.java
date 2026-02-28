package de.tum.cit.aet.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI features ({@code harmonia.ai.*}).
 * Controls which AI modules are enabled and their confidence thresholds.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "harmonia.ai")
public class AiProperties {

    private boolean enabled = true;
    private CommitClassifier commitClassifier = new CommitClassifier();
    private AnomalyDetector anomalyDetector = new AnomalyDetector();
    private FileClustering fileClustering = new FileClustering();

    /**
     * Settings for the commit classification AI module.
     */
    @Data
    public static class CommitClassifier {
        private boolean enabled = true;
        private double confidenceThreshold = 0.7;
        /** Will be auto-detected from available models if {@code null}. */
        private String modelName;
    }

    /**
     * Settings for the anomaly detection AI module.
     */
    @Data
    public static class AnomalyDetector {
        private boolean enabled = true;
        private double confidenceThreshold = 0.7;
        /** Will be auto-detected from available models if {@code null}. */
        private String modelName;
    }

    /**
     * Settings for the file clustering AI module.
     */
    @Data
    public static class FileClustering {
        private boolean enabled = false;
    }
}
