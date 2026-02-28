package org.yawlfoundation.yawl.benchmark.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Manages performance baseline storage and retrieval.
 */
public final class BaselineManager {

    private final Path baselinePath;
    private final ObjectMapper mapper;

    public BaselineManager(Path baselinePath) {
        this.baselinePath = baselinePath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * Load baseline from JSON file.
     */
    public Optional<BaselineData> loadBaseline() throws IOException {
        if (!baselinePath.toFile().exists()) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(baselinePath.toFile(), BaselineData.class));
    }

    /**
     * Save baseline to JSON file.
     */
    public void saveBaseline(BaselineData baseline) throws IOException {
        baselinePath.getParent().toFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(baselinePath.toFile(), baseline);
    }

    /**
     * Get threshold for a specific benchmark.
     */
    public double getThreshold(BaselineData baseline, String benchmarkKey) {
        // Check for override first
        if (baseline.getThresholds() != null
            && baseline.getThresholds().getOverrides() != null
            && baseline.getThresholds().getOverrides().containsKey(benchmarkKey)) {
            return baseline.getThresholds().getOverrides().get(benchmarkKey).getRegression();
        }
        // Fall back to default
        if (baseline.getThresholds() != null && baseline.getThresholds().getDefaultThreshold() != null) {
            return baseline.getThresholds().getDefaultThreshold().getRegression();
        }
        return 0.20; // 20% default
    }

    /**
     * Compare current results against baseline.
     */
    public RegressionReport compare(Map<String, Double> current, BaselineData baseline) {
        List<Regression> regressions = new ArrayList<>();
        List<Improvement> improvements = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (Map.Entry<String, BenchmarkEntry> entry : baseline.getBenchmarks().entrySet()) {
            String key = entry.getKey();
            double baselineValue = entry.getValue().getBaseline();

            if (!current.containsKey(key)) {
                continue;
            }

            double currentValue = current.get(key);
            double percentChange = (currentValue - baselineValue) / baselineValue;
            double threshold = getThreshold(baseline, key);

            if (percentChange > threshold) {
                regressions.add(new Regression(key, percentChange, threshold));
            } else if (percentChange < -baseline.getThresholds().getDefaultThreshold().getImprovement()) {
                improvements.add(new Improvement(key, -percentChange));
            } else {
                unchanged.add(key);
            }
        }

        return new RegressionReport(regressions, improvements, unchanged);
    }

    // Inner classes for data structures
    public record BaselineData(
        String version,
        Instant generated,
        String gitSha,
        String javaVersion,
        String jmhVersion,
        Map<String, BenchmarkEntry> benchmarks,
        Thresholds thresholds
    ) {
        public Map<String, BenchmarkEntry> getBenchmarks() { return benchmarks; }
        public Thresholds getThresholds() { return thresholds; }
    }

    public record BenchmarkEntry(
        String metric,
        String unit,
        double baseline,
        double errorMargin,
        int samples,
        Instant lastUpdated
    ) {
        public double getBaseline() { return baseline; }
    }

    public record Thresholds(
        DefaultThreshold defaultThreshold,
        Map<String, ThresholdOverride> overrides
    ) {
        public DefaultThreshold getDefaultThreshold() { return defaultThreshold; }
        public Map<String, ThresholdOverride> getOverrides() { return overrides; }
    }

    public record DefaultThreshold(double regression, double improvement) {
        public double getRegression() { return regression; }
        public double getImprovement() { return improvement; }
    }

    public record ThresholdOverride(double regression) {
        public double getRegression() { return regression; }
    }

    public record Regression(String benchmark, double percentChange, double threshold) {}

    public record Improvement(String benchmark, double percentImprovement) {}

    public record RegressionReport(
        List<Regression> regressions,
        List<Improvement> improvements,
        List<String> unchanged
    ) {
        public boolean hasRegressions() { return !regressions.isEmpty(); }
    }
}