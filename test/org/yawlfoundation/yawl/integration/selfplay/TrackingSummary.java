package org.yawlfoundation.yawl.integration.selfplay;

/**
 * Summary of convergence tracking history.
 *
 * <p>Provides key statistics about the tracking history for quick analysis:
 * <ul>
 *   <li>Total number of simulations recorded</li>
 *   <li>Current, maximum, minimum, and average fitness scores</li>
 *   <li>Range of fitness scores observed</li>
 * </ul>
 */
public final class TrackingSummary {

    private final int totalSimulations;
    private final double currentFitness;
    private final double maxFitness;
    private final double minFitness;
    private final double averageFitness;

    /**
     * Creates a tracking summary.
     *
     * @param totalSimulations the total number of simulations recorded
     * @param currentFitness the most recent fitness score
     * @param maxFitness the highest fitness score observed
     * @param minFitness the lowest fitness score observed
     * @param averageFitness the average fitness score across all simulations
     */
    public TrackingSummary(
        int totalSimulations,
        double currentFitness,
        double maxFitness,
        double minFitness,
        double averageFitness
    ) {
        if (totalSimulations < 0) {
            throw new IllegalArgumentException("Total simulations cannot be negative");
        }
        if (totalSimulations > 0) {
            if (currentFitness < 0.0 || currentFitness > 1.0) {
                throw new IllegalArgumentException("Current fitness must be between 0.0 and 1.0");
            }
            if (maxFitness < 0.0 || maxFitness > 1.0) {
                throw new IllegalArgumentException("Max fitness must be between 0.0 and 1.0");
            }
            if (minFitness < 0.0 || minFitness > 1.0) {
                throw new IllegalArgumentException("Min fitness must be between 0.0 and 1.0");
            }
            if (averageFitness < 0.0 || averageFitness > 1.0) {
                throw new IllegalArgumentException("Average fitness must be between 0.0 and 1.0");
            }
        }

        this.totalSimulations = totalSimulations;
        this.currentFitness = currentFitness;
        this.maxFitness = maxFitness;
        this.minFitness = minFitness;
        this.averageFitness = averageFitness;
    }

    /**
     * Returns the total number of simulations recorded.
     */
    public int getTotalSimulations() {
        return totalSimulations;
    }

    /**
     * Returns the most recent fitness score.
     */
    public double getCurrentFitness() {
        return currentFitness;
    }

    /**
     * Returns the highest fitness score observed.
     */
    public double getMaxFitness() {
        return maxFitness;
    }

    /**
     * Returns the lowest fitness score observed.
     */
    public double getMinFitness() {
        return minFitness;
    }

    /**
     * Returns the average fitness score across all simulations.
     */
    public double getAverageFitness() {
        return averageFitness;
    }

    /**
     * Returns the range of fitness scores (max - min).
     */
    public double getFitnessRange() {
        return maxFitness - minFitness;
    }

    /**
     * Returns whether any simulations have been recorded.
     */
    public boolean hasData() {
        return totalSimulations > 0;
    }

    /**
     * Returns whether the current fitness is at a record high.
     */
    public boolean isCurrentRecordHigh() {
        return currentFitness == maxFitness;
    }

    /**
     * Returns whether the current fitness is at a record low.
     */
    public boolean isCurrentRecordLow() {
        return currentFitness == minFitness;
    }

    /**
     * Returns the improvement from worst to best.
     */
    public double getTotalImprovement() {
        return maxFitness - minFitness;
    }

    /**
     * Returns the progress toward an ideal fitness of 1.0.
     */
    public double getProgressTowardIdeal() {
        if (totalSimulations == 0) return 0.0;
        return averageFitness; // Since ideal is 1.0, average is the progress
    }

    /**
     * Returns a human-readable summary of the tracking data.
     */
    public String getSummary() {
        if (!hasData()) {
            return "No tracking data available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Tracking Summary ===\n");
        sb.append("Total Simulations: ").append(totalSimulations).append("\n");
        sb.append("Current Fitness: ").append(String.format("%.4f", currentFitness)).append("\n");
        sb.append("Max Fitness: ").append(String.format("%.4f", maxFitness)).append("\n");
        sb.append("Min Fitness: ").append(String.format("%.4f", minFitness)).append("\n");
        sb.append("Average Fitness: ").append(String.format("%.4f", averageFitness)).append("\n");
        sb.append("Fitness Range: ").append(String.format(".4f", getFitnessRange())).append("\n");
        sb.append("Total Improvement: ").append(String.format(".4f", getTotalImprovement())).append("\n");
        sb.append("Progress Toward Ideal: ").append(String.format(".1f%%", getProgressTowardIdeal() * 100)).append("\n");

        if (isCurrentRecordHigh()) {
            sb.append("Status: Current fitness is a record high!\n");
        } else if (isCurrentRecordLow()) {
            sb.append("Status: Current fitness is a record low.\n");
        } else {
            sb.append("Status: Normal progress\n");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingSummary that = (TrackingSummary) o;
        return totalSimulations == that.totalSimulations &&
                Double.compare(that.currentFitness, currentFitness) == 0 &&
                Double.compare(that.maxFitness, maxFitness) == 0 &&
                Double.compare(that.minFitness, minFitness) == 0 &&
                Double.compare(that.averageFitness, averageFitness) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalSimulations, currentFitness, maxFitness, minFitness, averageFitness);
    }

    @Override
    public String toString() {
        return String.format(
            "TrackingSummary{simulations=%d, current=%.4f, max=%.4f, min=%.4f, avg=%.4f}",
            totalSimulations, currentFitness, maxFitness, minFitness, averageFitness
        );
    }
}