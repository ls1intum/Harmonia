package de.tum.cit.aet.analysis.service.cqi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for CQI calculation.
 * Can be overridden in application.yml or application.properties.
 * <p>
 * Example configuration:
 * <pre>
 * harmonia:
 *   cqi:
 *     weights:
 *       effort: 0.55
 *       loc: 0.25
 *       temporal: 0.05
 *       ownership: 0.15
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "harmonia.cqi")
@Setter
@Getter
public class CQIConfig {

    private Weights weights = new Weights();
    private Filter filter = new Filter();

    /**
     * Component weights for CQI formula.
     */
    @Setter
    @Getter
    public static class Weights {
        private double effort = 0.55;
        private double loc = 0.25;
        private double temporal = 0.05;
        private double ownership = 0.15;

        /**
         * Validate that weights sum to 1.0.
         *
         * @return true if the sum of all weights equals 1.0 (within 0.001 tolerance)
         */
        public boolean isValid() {
            double sum = effort + loc + temporal + ownership;
            return Math.abs(sum - 1.0) < 0.001;
        }
    }

    /**
     * Configuration for commit filtering.
     */
    @Setter
    @Getter
    public static class Filter {
        private double lowNoveltyThreshold = 3.0;
        private double lowEffortThreshold = 2.0;
        private double copyPasteNoveltyThreshold = 2.0;
        private double copyPasteComplexityThreshold = 3.0;
        private int copyPasteLocThreshold = 100;
        private int trivialLocThreshold = 5;
        private double copyPasteWeightReduction = 0.1;

    }
}
