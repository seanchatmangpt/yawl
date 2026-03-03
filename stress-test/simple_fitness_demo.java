import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Simplified fitness measurement system demonstration

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
    }

    // Performance Metrics
    public void recordLatency(long value, String unit, String description) {
        performanceMetrics.put("latency", new PerformanceMetrics(value, unit, description));
    }

    // Integration Metrics
    public void recordMcpHealth(double score, String description) {
        integrationMetrics.put("mcp_health", new IntegrationMetrics(score, description));
    }

    // Self-Play Metrics
    public void recordKnowledgeGain(double score, String description) {
        selfPlayMetrics.put("knowledge_gain", new SelfPlayMetrics(score, description));
    }

    // Metadata
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    // Calculate aggregated score
    public synchronized FitnessScore calculateAggregatedScore() {
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

        return breakdown;
    }

    private double calculateDimensionScore(Map<String, ?> metrics, double weight) {
        if (metrics.isEmpty()) return 0.0;

        double sum = 0.0;
        int count = 0;

        for (Object metric : metrics.values()) {
            double value = 0.0;
            if (metric instanceof CodeQualityMetrics) {
                value = ((CodeQualityMetrics) metric).score();
            } else if (metric instanceof PerformanceMetrics) {
                value = normalizePerformanceMetric((PerformanceMetrics) metric);
            } else if (metric instanceof IntegrationMetrics) {
                value = ((IntegrationMetrics) metric).score();
            } else if (metric instanceof SelfPlayMetrics) {
                value = ((SelfPlayMetrics) metric).score();
            }
            sum += value;
            count++;
        }

        return count > 0 ? sum / count : 0.0;
    }

    private double normalizePerformanceMetric(PerformanceMetrics metric) {
        String description = metric.description().toLowerCase();

        if (description.contains("latency") || description.contains("response")) {
            long value = metric.value();
            return Math.max(0, 1.0 - Math.min(value, 5000) / 5000.0);
        } else {
            return Math.max(0, Math.min(1.0, metric.value() / 100.0));
        }
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public Duration getDuration() { return Duration.between(startTime, Instant.now()); }
    public Map<String, CodeQualityMetrics> getCodeQualityMetrics() { return Collections.unmodifiableMap(codeQualityMetrics); }
    public Map<String, PerformanceMetrics> getPerformanceMetrics() { return Collections.unmodifiableMap(performanceMetrics); }
    public Map<String, IntegrationMetrics> getIntegrationMetrics() { return Collections.unmodifiableMap(integrationMetrics); }
    public Map<String, SelfPlayMetrics> getSelfPlayMetrics() { return Collections.unmodifiableMap(selfPlayMetrics); }
    public Double getAggregatedScore() { return aggregatedScore; }
}

// Metric records
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

record FitnessDimensionScore(String dimension, double score, Map<String, ?> metrics)
    implements Comparable<FitnessDimensionScore> {
    public FitnessDimensionScore {
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
    }

    public String level() {
        if (score >= 0.8) return "Excellent";
        if (score >= 0.6) return "Good";
        if (score >= 0.4) return "Acceptable";
        return "Poor";
    }

    @Override
    public int compareTo(FitnessDimensionScore other) {
        return Double.compare(this.score, other.score);
    }

    public String getSummary() {
        return String.format("%s: %.2f (%s)", dimension, score, level());
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

    public String getSummary() {
        return String.format("=== Fitness Assessment Summary ===\n" +
                           "Total Score: %.2f (%s)\n" +
                           "Code Quality: %.2f (%s)\n" +
                           "Performance: %.2f (%s)\n" +
                           "Integration: %.2f (%s)\n" +
                           "Self-Play: %.2f (%s)\n",
                           total, getPerformanceLevel(),
                           codeQuality.score(), codeQuality.level(),
                           performance.score(), performance.level(),
                           integration.score(), integration.level(),
                           selfPlay.score(), selfPlay.level());
    }
}

// Main demo class
class SimpleFitnessDemo {
    public static void main(String[] args) {
        System.out.println("=== Simple YAWL Fitness Measurement Demo ===");
        System.out.println();

        // Create fitness metrics collection
        FitnessMetrics metrics = new FitnessMetrics("demo-session-" + System.currentTimeMillis());

        // Record code quality metrics
        System.out.println("📊 Recording Code Quality Metrics...");
        metrics.recordCodeComplexity(0.75, "Average cyclomatic complexity");
        System.out.println("✓ Recorded " + metrics.getCodeQualityMetrics().size() + " code quality metrics\n");

        // Record performance metrics
        System.out.println("⚡ Recording Performance Metrics...");
        metrics.recordLatency(145, "ms", "Average API response time");
        System.out.println("✓ Recorded " + metrics.getPerformanceMetrics().size() + " performance metrics\n");

        // Record integration metrics
        System.out.println("🔌 Recording Integration Metrics...");
        metrics.recordMcpHealth(0.98, "MCP server availability");
        System.out.println("✓ Recorded " + metrics.getIntegrationMetrics().size() + " integration metrics\n");

        // Record self-play metrics
        System.out.println("🎮 Recording Self-Play Metrics...");
        metrics.recordKnowledgeGain(0.65, "Knowledge acquisition");
        System.out.println("✓ Recorded " + metrics.getSelfPlayMetrics().size() + " self-play metrics\n");

        // Add metadata
        System.out.println("🏷️ Adding Metadata...");
        metrics.addMetadata("environment", "production");
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

        System.out.println("📋 Detailed Summary:");
        System.out.println(score.getSummary());
        System.out.println();

        // Session info
        System.out.println("📊 Session Information:");
        System.out.println("   Session ID: " + metrics.getSessionId());
        System.out.println("   Duration: " + metrics.getDuration().toMillis() + " ms");
        System.out.println("   Total Metrics: " + (
            metrics.getCodeQualityMetrics().size() +
            metrics.getPerformanceMetrics().size() +
            metrics.getIntegrationMetrics().size() +
            metrics.getSelfPlayMetrics().size()
        ));

        System.out.println("\n=== Demo Complete ===");
    }
}