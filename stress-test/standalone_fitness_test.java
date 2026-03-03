import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Standalone fitness measurement system
// This demonstrates the system without requiring Maven dependencies

class FitnessMetrics {
    private final String sessionId;
    private final Instant startTime;
    private final Map<String, CodeQualityMetrics> codeQualityMetrics;
    private final Map<String, PerformanceMetrics> performanceMetrics;
    private final Map<String, IntegrationMetrics> integrationMetrics;
    private final Map<String, SelfPlayMetrics> selfPlayMetrics;
    private final Map<String, Object> metadata;
    private Double aggregatedScore;
    private FitnessScore breakdown;
    private volatile boolean dirty = true;

    public FitnessMetrics(String sessionId) {
        this.sessionId = sessionId;
        this.startTime = Instant.now();
        this.codeQualityMetrics = new ConcurrentHashMap<>();
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.integrationMetrics = new ConcurrentHashMap<>();
        this.selfPlayMetrics = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();
    }

    // Code Quality Metrics
    public void recordCodeComplexity(double score, String description) {
        codeQualityMetrics.put("complexity", new CodeQualityMetrics(score, description));
        markDirty();
    }

    public void recordTestCoverage(double score, String description) {
        codeQualityMetrics.put("coverage", new CodeQualityMetrics(score, description));
        markDirty();
    }

    public void recordMaintainability(double score, String description) {
        codeQualityMetrics.put("maintainability", new CodeQualityMetrics(score, description));
        markDirty();
    }

    // Performance Metrics
    public void recordLatency(long value, String unit, String description) {
        performanceMetrics.put("latency", new PerformanceMetrics(value, unit, description));
        markDirty();
    }

    public void recordThroughput(long value, String unit, String description) {
        performanceMetrics.put("throughput", new PerformanceMetrics(value, unit, description));
        markDirty();
    }

    public void recordMemoryUsage(long value, String unit, String description) {
        performanceMetrics.put("memory", new PerformanceMetrics(value, unit, description));
        markDirty();
    }

    public void recordCpuUsage(double value, String unit, String description) {
        performanceMetrics.put("cpu", new PerformanceMetrics((long)value, unit, description));
        markDirty();
    }

    // Integration Metrics
    public void recordMcpHealth(double score, String description) {
        integrationMetrics.put("mcp_health", new IntegrationMetrics(score, description));
        markDirty();
    }

    public void recordA2aHealth(double score, String description) {
        integrationMetrics.put("a2a_health", new IntegrationMetrics(score, description));
        markDirty();
    }

    public void recordApiAvailability(double score, String description) {
        integrationMetrics.put("api_availability", new IntegrationMetrics(score, description));
        markDirty();
    }

    // Self-Play Metrics
    public void recordConvergenceSpeed(double rounds, String description) {
        selfPlayMetrics.put("convergence_speed", new SelfPlayMetrics(rounds, description));
        markDirty();
    }

    public void recordProposalQuality(double score, String description) {
        selfPlayMetrics.put("proposal_quality", new SelfPlayMetrics(score, description));
        markDirty();
    }

    public void recordConsensusRate(double score, String description) {
        selfPlayMetrics.put("consensus_rate", new SelfPlayMetrics(score, description));
        markDirty();
    }

    public void recordKnowledgeGain(double score, String description) {
        selfPlayMetrics.put("knowledge_gain", new SelfPlayMetrics(score, description));
        markDirty();
    }

    // Metadata
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
        markDirty();
    }

    // Calculate aggregated score
    public synchronized FitnessScore calculateAggregatedScore() {
        if (breakdown != null && !isDirty()) {
            return breakdown;
        }

        double codeQualityScore = calculateDimensionScore(codeQualityMetrics, 0.3);
        double performanceScore = calculateDimensionScore(performanceMetrics, 0.25);
        double integrationScore = calculateDimensionScore(integrationMetrics, 0.25);
        double selfPlayScore = calculateDimensionScore(selfPlayMetrics, 0.2);

        this.aggregatedScore = codeQualityScore * 0.3 +
                               performanceScore * 0.25 +
                               integrationScore * 0.25 +
                               selfPlayScore * 0.2;

        this.breakdown = new FitnessScore(
            aggregatedScore,
            new FitnessDimensionScore("code_quality", codeQualityScore, codeQualityMetrics),
            new FitnessDimensionScore("performance", performanceScore, performanceMetrics),
            new FitnessDimensionScore("integration", integrationScore, integrationMetrics),
            new FitnessDimensionScore("self_play", selfPlayScore, selfPlayMetrics)
        );

        clean();
        return breakdown;
    }

    private double calculateDimensionScore(Map<String, ?> metrics, double weight) {
        if (metrics.isEmpty()) return 0.0;

        double sum = 0.0;
        int count = 0;

        for (Object metric : metrics.values()) {
            double value = 0.0;
            if (metric instanceof CodeQualityMetrics) {
                value = ((CodeQualityMetrics) metric).getScore();
            } else if (metric instanceof PerformanceMetrics) {
                value = normalizePerformanceMetric((PerformanceMetrics) metric);
            } else if (metric instanceof IntegrationMetrics) {
                value = ((IntegrationMetrics) metric).getScore();
            } else if (metric instanceof SelfPlayMetrics) {
                value = ((SelfPlayMetrics) metric).getScore();
            }
            sum += value;
            count++;
        }

        return count > 0 ? sum / count : 0.0;
    }

    private double normalizePerformanceMetric(PerformanceMetrics metric) {
        String description = metric.getDescription().toLowerCase();

        if (description.contains("latency") || description.contains("response")) {
            long value = metric.getValue();
            return Math.max(0, 1.0 - Math.min(value, 5000) / 5000.0);
        } else if (description.contains("throughput") || description.contains("req") || description.contains("per second")) {
            long value = metric.getValue();
            return Math.min(1.0, value / 10000.0);
        } else if (description.contains("memory")) {
            long value = metric.getValue();
            return Math.max(0, 1.0 - Math.min(value, 8192) / 8192.0);
        } else if (description.contains("cpu")) {
            return Math.max(0, 1.0 - Math.min(metric.getValue(), 100) / 100.0);
        } else {
            return Math.max(0, Math.min(1.0, metric.getValue() / 100.0));
        }
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public Instant getStartTime() { return startTime; }
    public Duration getDuration() { return Duration.between(startTime, Instant.now()); }
    public Map<String, CodeQualityMetrics> getCodeQualityMetrics() { return Collections.unmodifiableMap(codeQualityMetrics); }
    public Map<String, PerformanceMetrics> getPerformanceMetrics() { return Collections.unmodifiableMap(performanceMetrics); }
    public Map<String, IntegrationMetrics> getIntegrationMetrics() { return Collections.unmodifiableMap(integrationMetrics); }
    public Map<String, SelfPlayMetrics> getSelfPlayMetrics() { return Collections.unmodifiableMap(selfPlayMetrics); }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Double getAggregatedScore() { return aggregatedScore; }

    // Dirty flag
    private boolean isDirty() { return dirty; }
    private void clean() { this.dirty = false; }
    private void markDirty() { this.dirty = true; this.aggregatedScore = null; this.breakdown = null; }
}

// Metric classes
record CodeQualityMetrics(double score, String description) {
    public CodeQualityMetrics {
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
    }
}

record PerformanceMetrics(long value, String unit, String description) {
    public PerformanceMetrics {
        if (value < 0) throw new IllegalArgumentException("Value cannot be negative");
    }
}

record IntegrationMetrics(double score, String description) {
    public IntegrationMetrics {
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
    }
}

record SelfPlayMetrics(double score, String description) {
    public SelfPlayMetrics {
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
    }
}

record FitnessScore(
    double total,
    FitnessDimensionScore codeQuality,
    FitnessDimensionScore performance,
    FitnessDimensionScore integration,
    FitnessDimensionScore selfPlay
) {
    public FitnessScore {
        if (total < 0.0 || total > 1.0) throw new IllegalArgumentException("Total score must be between 0.0 and 1.0");
    }

    public String getPerformanceLevel() {
        if (total >= 0.8) return "Excellent";
        if (total >= 0.6) return "Good";
        if (total >= 0.4) return "Acceptable";
        return "Poor";
    }

    public FitnessDimensionScore getWeakestDimension() {
        List<FitnessDimensionScore> dimensions = List.of(codeQuality, performance, integration, selfPlay);
        return dimensions.stream().min(FitnessDimensionScore::compareTo).orElseThrow();
    }

    public FitnessDimensionScore getStrongestDimension() {
        List<FitnessDimensionScore> dimensions = List.of(codeQuality, performance, integration, selfPlay);
        return dimensions.stream().max(FitnessDimensionScore::compareTo).orElseThrow();
    }

    public int countExcellentDimensions() {
        return (int) List.of(codeQuality, performance, integration, selfPlay).stream()
            .filter(dim -> dim.score() >= 0.8)
            .count();
    }

    public int countPoorDimensions() {
        return (int) List.of(codeQuality, performance, integration, selfPlay).stream()
            .filter(dim -> dim.score() < 0.4)
            .count();
    }

    public boolean allDimensionsGood() {
        return List.of(codeQuality, performance, integration, selfPlay).stream()
            .allMatch(dim -> dim.score() >= 0.6);
    }

    public boolean hasSignificantImbalance() {
        double maxScore = Math.max(Math.max(codeQuality.score(), performance.score()),
                                  Math.max(integration.score(), selfPlay.score()));
        double minScore = Math.min(Math.min(codeQuality.score(), performance.score()),
                                  Math.min(integration.score(), selfPlay.score()));
        return (maxScore - minScore) > 0.4;
    }

    public String getSummary() {
        return String.format("=== Fitness Assessment Summary ===\n" +
                           "Total Score: %.2f (%s)\n" +
                           "Code Quality: %.2f (%s)\n" +
                           "Performance: %.2f (%s)\n" +
                           "Integration: %.2f (%s)\n" +
                           "Self-Play: %.2f (%s)\n\n" +
                           "--- Analysis ---\n" +
                           "Excellent Dimensions: %d/4\n" +
                           "Poor Dimensions: %d/4\n" +
                           "All Dimensions Good: %s\n" +
                           "Significant Imbalance: %s\n",
                           total, getPerformanceLevel(),
                           codeQuality.score(), codeQuality.level(),
                           performance.score(), performance.level(),
                           integration.score(), integration.level(),
                           selfPlay.score(), selfPlay.level(),
                           countExcellentDimensions(), countPoorDimensions(),
                           allDimensionsGood(), hasSignificantImbalance());
    }
}

record FitnessDimensionScore(String dimension, double score, Map<String, ?> metrics) {
    public FitnessDimensionScore {
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
    }

    public String level() {
        if (score >= 0.8) return "Excellent";
        if (score >= 0.6) return "Good";
        if (score >= 0.4) return "Acceptable";
        return "Poor";
    }

    public int getMetricCount() {
        return metrics != null ? metrics.size() : 0;
    }

    @Override
    public int compareTo(FitnessDimensionScore other) {
        return Double.compare(this.score, other.score);
    }
}

// Main demo class
public class StandaloneFitnessTest {
    public static void main(String[] args) {
        System.out.println("=== YAWL Fitness Measurement System Demo ===");
        System.out.println();

        // Create fitness metrics collection
        FitnessMetrics metrics = new FitnessMetrics("demo-session-" + System.currentTimeMillis());

        // Record code quality metrics
        System.out.println("📊 Recording Code Quality Metrics...");
        metrics.recordCodeComplexity(0.75, "Average cyclomatic complexity");
        metrics.recordTestCoverage(0.92, "JUnit test coverage");
        metrics.recordMaintainability(0.68, "Code maintainability index");
        metrics.recordCodeReviewQuality(0.74, "Code review quality");
        System.out.println("✓ Recorded " + metrics.getCodeQualityMetrics().size() + " code quality metrics\n");

        // Record performance metrics
        System.out.println("⚡ Recording Performance Metrics...");
        metrics.recordLatency(145, "ms", "Average API response time");
        metrics.recordThroughput(8500, "req/s", "Requests per second");
        metrics.recordMemoryUsage(2048, "MB", "Peak memory usage");
        metrics.recordCpuUsage(25, "%", "Average CPU utilization");
        System.out.println("✓ Recorded " + metrics.getPerformanceMetrics().size() + " performance metrics\n");

        // Record integration metrics
        System.out.println("🔌 Recording Integration Metrics...");
        metrics.recordMcpHealth(0.98, "MCP server availability");
        metrics.recordA2aHealth(0.85, "A2A protocol health");
        metrics.recordApiAvailability(0.99, "API availability");
        System.out.println("✓ Recorded " + metrics.getIntegrationMetrics().size() + " integration metrics\n");

        // Record self-play metrics
        System.out.println("🎮 Recording Self-Play Metrics...");
        metrics.recordConvergenceSpeed(2.5, "Average rounds to convergence");
        metrics.recordProposalQuality(0.78, "Proposal quality");
        metrics.recordConsensusRate(0.92, "Consensus achievement rate");
        metrics.recordKnowledgeGain(0.65, "Knowledge acquisition");
        System.out.println("✓ Recorded " + metrics.getSelfPlayMetrics().size() + " self-play metrics\n");

        // Add metadata
        System.out.println("🏷️ Adding Metadata...");
        metrics.addMetadata("environment", "production");
        metrics.addMetadata("test_duration_ms", 4500);
        metrics.addMetadata("agent_count", 3);
        metrics.addMetadata("timestamp", Instant.now().toString());
        System.out.println("✓ Added metadata\n");

        // Calculate final score
        FitnessScore score = metrics.calculateAggregatedScore();

        // Display results
        System.out.println("=== Fitness Measurement Results ===");
        System.out.println();

        System.out.println("🎯 Overall Fitness Score:");
        System.out.printf("   Total: %.2f (%s)%n%n", score.total(), score.getPerformanceLevel());

        System.out.println("📈 Detailed Breakdown:");
        System.out.println("   " + score.codeQuality().getSummary());
        System.out.println("   " + score.performance().getSummary());
        System.out.println("   " + score.integration().getSummary());
        System.out.println("   " + score.selfPlay().getSummary());
        System.out.println();

        System.out.println("🔍 Performance Analysis:");
        System.out.println("   Excellent Dimensions: " + score.countExcellentDimensions() + "/4");
        System.out.println("   Poor Dimensions: " + score.countPoorDimensions() + "/4");
        System.out.println("   All Dimensions Good: " + score.allDimensionsGood());
        System.out.println("   Significant Imbalance: " + score.hasSignificantImbalance());

        if (score.hasSignificantImbalance()) {
            System.out.println("   Weakest Area: " + score.getWeakestDimension().dimension() +
                             " (" + String.format("%.2f", score.getWeakestDimension().score()) + ")");
            System.out.println("   Strongest Area: " + score.getStrongestDimension().dimension() +
                             " (" + String.format("%.2f", score.getStrongestDimension().score()) + ")");
        }
        System.out.println();

        System.out.println("📋 Detailed Summary:");
        System.out.println(score.getSummary());
        System.out.println();

        // Session info
        System.out.println("📊 Session Information:");
        System.out.println("   Session ID: " + metrics.getSessionId());
        System.out.println("   Duration: " + metrics.getDuration().toSeconds() + " seconds");
        System.out.println("   Total Metrics: " + (
            metrics.getCodeQualityMetrics().size() +
            metrics.getPerformanceMetrics().size() +
            metrics.getIntegrationMetrics().size() +
            metrics.getSelfPlayMetrics().size()
        ));
    }
}