package de.tum.cit.aet.analysis.service.cqi;

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
 *       effort: 0.40
 *       loc: 0.25
 *       temporal: 0.20
 *       ownership: 0.15
 *     thresholds:
 *       solo-development: 0.85
 *       severe-imbalance: 0.70
 *       high-trivial: 0.50
 *       low-confidence: 0.40
 *       late-work: 0.50
 *     penalties:
 *       solo-development: 0.25
 *       severe-imbalance: 0.70
 *       high-trivial: 0.85
 *       low-confidence: 0.90
 *       late-work: 0.85
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "harmonia.cqi")
public class CQIConfig {

    private Weights weights = new Weights();
    private Thresholds thresholds = new Thresholds();
    private Penalties penalties = new Penalties();
    private Filter filter = new Filter();

    // Getters and setters
    public Weights getWeights() {
        return weights;
    }

    public void setWeights(Weights weights) {
        this.weights = weights;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Penalties getPenalties() {
        return penalties;
    }

    public void setPenalties(Penalties penalties) {
        this.penalties = penalties;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * Component weights for CQI formula.
     */
    public static class Weights {
        private double effort = 0.40;
        private double loc = 0.25;
        private double temporal = 0.20;
        private double ownership = 0.15;

        public double getEffort() {
            return effort;
        }

        public void setEffort(double effort) {
            this.effort = effort;
        }

        public double getLoc() {
            return loc;
        }

        public void setLoc(double loc) {
            this.loc = loc;
        }

        public double getTemporal() {
            return temporal;
        }

        public void setTemporal(double temporal) {
            this.temporal = temporal;
        }

        public double getOwnership() {
            return ownership;
        }

        public void setOwnership(double ownership) {
            this.ownership = ownership;
        }

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
     * Thresholds for triggering penalties.
     */
    public static class Thresholds {
        private double soloDevelopment = 0.85;
        private double severeImbalance = 0.70;
        private double highTrivial = 0.50;
        private double lowConfidence = 0.40;
        private double lateWork = 0.50;

        public double getSoloDevelopment() {
            return soloDevelopment;
        }

        public void setSoloDevelopment(double soloDevelopment) {
            this.soloDevelopment = soloDevelopment;
        }

        public double getSevereImbalance() {
            return severeImbalance;
        }

        public void setSevereImbalance(double severeImbalance) {
            this.severeImbalance = severeImbalance;
        }

        public double getHighTrivial() {
            return highTrivial;
        }

        public void setHighTrivial(double highTrivial) {
            this.highTrivial = highTrivial;
        }

        public double getLowConfidence() {
            return lowConfidence;
        }

        public void setLowConfidence(double lowConfidence) {
            this.lowConfidence = lowConfidence;
        }

        public double getLateWork() {
            return lateWork;
        }

        public void setLateWork(double lateWork) {
            this.lateWork = lateWork;
        }
    }

    /**
     * Penalty multipliers when thresholds are exceeded.
     */
    public static class Penalties {
        private double soloDevelopment = 0.25;
        private double severeImbalance = 0.70;
        private double highTrivial = 0.85;
        private double lowConfidence = 0.90;
        private double lateWork = 0.85;

        public double getSoloDevelopment() {
            return soloDevelopment;
        }

        public void setSoloDevelopment(double soloDevelopment) {
            this.soloDevelopment = soloDevelopment;
        }

        public double getSevereImbalance() {
            return severeImbalance;
        }

        public void setSevereImbalance(double severeImbalance) {
            this.severeImbalance = severeImbalance;
        }

        public double getHighTrivial() {
            return highTrivial;
        }

        public void setHighTrivial(double highTrivial) {
            this.highTrivial = highTrivial;
        }

        public double getLowConfidence() {
            return lowConfidence;
        }

        public void setLowConfidence(double lowConfidence) {
            this.lowConfidence = lowConfidence;
        }

        public double getLateWork() {
            return lateWork;
        }

        public void setLateWork(double lateWork) {
            this.lateWork = lateWork;
        }
    }

    /**
     * Configuration for commit filtering.
     */
    public static class Filter {
        private double lowNoveltyThreshold = 3.0;
        private double lowEffortThreshold = 2.0;
        private double copyPasteNoveltyThreshold = 2.0;
        private double copyPasteComplexityThreshold = 3.0;
        private int copyPasteLocThreshold = 100;
        private int trivialLocThreshold = 5;
        private double copyPasteWeightReduction = 0.1;

        public double getLowNoveltyThreshold() {
            return lowNoveltyThreshold;
        }

        public void setLowNoveltyThreshold(double lowNoveltyThreshold) {
            this.lowNoveltyThreshold = lowNoveltyThreshold;
        }

        public double getLowEffortThreshold() {
            return lowEffortThreshold;
        }

        public void setLowEffortThreshold(double lowEffortThreshold) {
            this.lowEffortThreshold = lowEffortThreshold;
        }

        public double getCopyPasteNoveltyThreshold() {
            return copyPasteNoveltyThreshold;
        }

        public void setCopyPasteNoveltyThreshold(double copyPasteNoveltyThreshold) {
            this.copyPasteNoveltyThreshold = copyPasteNoveltyThreshold;
        }

        public double getCopyPasteComplexityThreshold() {
            return copyPasteComplexityThreshold;
        }

        public void setCopyPasteComplexityThreshold(double copyPasteComplexityThreshold) {
            this.copyPasteComplexityThreshold = copyPasteComplexityThreshold;
        }

        public int getCopyPasteLocThreshold() {
            return copyPasteLocThreshold;
        }

        public void setCopyPasteLocThreshold(int copyPasteLocThreshold) {
            this.copyPasteLocThreshold = copyPasteLocThreshold;
        }

        public int getTrivialLocThreshold() {
            return trivialLocThreshold;
        }

        public void setTrivialLocThreshold(int trivialLocThreshold) {
            this.trivialLocThreshold = trivialLocThreshold;
        }

        public double getCopyPasteWeightReduction() {
            return copyPasteWeightReduction;
        }

        public void setCopyPasteWeightReduction(double copyPasteWeightReduction) {
            this.copyPasteWeightReduction = copyPasteWeightReduction;
        }
    }
}
