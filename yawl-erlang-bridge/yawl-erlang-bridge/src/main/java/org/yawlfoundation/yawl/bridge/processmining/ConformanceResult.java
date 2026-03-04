package org.yawlfoundation.yawl.bridge.processmining;

/**
 * Result of conformance checking between an event log and Petri net.
 */
public class ConformanceResult {

    private final double fitness;
    private final String details;

    public ConformanceResult(double fitness, String details) {
        if (fitness < 0 || fitness > 1) {
            throw new IllegalArgumentException("Fitness must be between 0 and 1");
        }
        if (details == null) {
            throw new IllegalArgumentException("Details cannot be null");
        }
        this.fitness = fitness;
        this.details = details;
    }

    public double getFitness() {
        return fitness;
    }

    public String getDetails() {
        return details;
    }

    public boolean isPerfectFit() {
        return fitness >= 0.99;
    }

    public boolean isPoorFit() {
        return fitness < 0.5;
    }

    @Override
    public String toString() {
        return String.format("ConformanceResult{fitness=%.4f, details='%s'}",
                           fitness, details.length() > 50 ?
                           details.substring(0, 50) + "..." : details);
    }
}