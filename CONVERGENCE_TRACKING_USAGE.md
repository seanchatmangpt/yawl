# YAWL Self-Play Convergence Tracking - Usage Guide

## Overview

This document describes how to use the ConvergenceTracker system for YAWL self-play convergence analysis. The system provides comprehensive trend analysis, plateau detection, and actionable insights to optimize self-play performance.

## Architecture

### Core Components

1. **ConvergenceTracker** - Main class that tracks simulation history and performs analysis
2. **ConvergenceAnalysis** - Result of convergence analysis with status, alerts, and recommendations
3. **ConvergenceAlert** - Structured alerts for convergence issues
4. **TrendAnalysis** - Statistical trend analysis of fitness scores over time
5. **PlateauDetection** - Detection of convergence plateaus
6. **TrackingSummary** - Summary of tracking history statistics

### Key Features

- **Historical tracking** of fitness scores across multiple simulation runs
- **Statistical analysis** using linear regression and correlation
- **Plateau detection** with configurable thresholds
- **Divergence detection** with early warning alerts
- **Trend analysis** with correlation strength indicators
- **Actionable recommendations** based on analysis results

## Usage Examples

### Basic Usage

```java
// Create convergence tracker with custom configuration
ConvergenceTracker tracker = new ConvergenceTracker.Builder()
    .maxHistorySize(20)
    .plateauThreshold(0.01)
    .divergenceThreshold(0.1)
    .plateauMinSamples(3)
    .trendWindow(10)
    .build();

// Record each simulation run
V7SimulationReport report = orchestrator.runLoop();
ConvergenceAnalysis analysis = tracker.recordSimulation(report);

// Analyze results
if (analysis.hasProblems()) {
    for (ConvergenceAlert alert : analysis.getAlerts()) {
        System.out.println("Alert: " + alert.getType() + " - " + alert.getMessage());
    }
}
```

### Advanced Configuration

```java
// Configure for different sensitivity levels
ConvergenceTracker sensitiveTracker = new ConvergenceTracker.Builder()
    .maxHistorySize(50)        // Store more history
    .plateauThreshold(0.005)   // More sensitive plateau detection
    .divergenceThreshold(0.05) // Lower divergence threshold
    .plateauMinSamples(5)      // Require more samples for plateau
    .trendWindow(20)           // Longer trend window
    .build();
```

### Integration with YAWL Self-Play

```java
public class SelfPlayWithConvergenceTracking {
    private final ConvergenceTracker tracker;
    private final List<SelfPlaySession> sessionHistory;

    public ConvergenceAnalysis runTrackingSessions(int maxSessions) {
        for (int session = 1; session <= maxSessions; session++) {
            // Run self-play session
            V7SelfPlayOrchestrator orchestrator = createOrchestrator(session);
            V7SimulationReport report = orchestrator.runLoop();

            // Record and analyze
            ConvergenceAnalysis analysis = tracker.recordSimulation(report);

            // Handle alerts
            handleAlerts(analysis, session);

            // Early termination check
            if (shouldTerminate(analysis, session)) {
                break;
            }
        }

        return tracker.analyzeConvergence();
    }
}
```

## Analysis Results

### Convergence Status

The system identifies the following convergence statuses:

- **INSUFFICIENT_DATA** - Not enough runs for analysis
- **NORMAL_PROGRESS** - Converging normally
- **APPROACHING_CONVERGENCE** - Near convergence threshold
- **CONVERGED** - Convergence threshold achieved
- **PLATEAUED** - No improvement for multiple runs
- **DIVERGING** - Performance degrading

### Alert Types

The system generates alerts for:

1. **DIVERGENCE** - Fitness scores are declining
2. **PLATEAU** - No improvement detected
3. **SLOW_PROGRESS** - Improvement rate is very low
4. **OSCILLATION** - Scores fluctuating without direction

Each alert has severity levels: LOW, MEDIUM, HIGH

### Trend Analysis

The trend analysis provides:

- **Linear regression slope** indicating direction and rate
- **Correlation coefficient** showing trend strength
- **Trend direction**: IMPROVING, STABLE, or DECLINING

### Plateau Detection

Plateau detection identifies:

- **Detection status** - Whether a plateau is present
- **Range** - Variation in fitness scores
- **Sample size** - Number of runs analyzed
- **Plateau level** - NONE, MARGINALLY_STABLE, SLIGHTLY_STABLE, STABLE, VERY_STABLE

## Actionable Insights

### When Convergence is Achieved

```java
if (analysis.isConverged()) {
    System.out.println("Success! Convergence achieved at fitness: " +
                       analysis.getCurrentFitness());

    // Save final configuration
    saveFinalConfiguration();

    // Run validation tests
    runValidationTests();
}
```

### Handling Plateaus

```java
if (analysis.getPlateau().detected()) {
    System.out.println("Plateau detected. Strategies to break it:");
    System.out.println("1. Introduce new proposal services");
    System.out.println("2. Adjust fitness evaluation weights");
    System.out.println("3. Increase agent diversity");

    // Implement plateau-breaking strategy
    implementNewStrategies();
}
```

### Handling Divergence

```java
for (ConvergenceAlert alert : analysis.getAlerts()) {
    if (alert.getType() == ConvergenceAlert.AlertType.DIVERGENCE) {
        System.out.println("Divergence detected! Immediate action needed:");
        System.out.println("- Check data sources");
        System.out.println("- Validate fitness calculations");
        System.out.println("- Review acceptance criteria");

        // Implement recovery strategy
        implementRecoveryStrategy();
    }
}
```

## Configuration Parameters

### Builder Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxHistorySize` | 50 | Maximum number of simulations to track |
| `plateauThreshold` | 0.001 | Maximum range for plateau detection |
| `divergenceThreshold` | 0.1 | Performance degradation threshold |
| `plateauMinSamples` | 5 | Minimum runs for plateau detection |
| `trendWindow` | 10 | Number of runs for trend analysis |

### Recommendation Guidelines

- **Conservative settings**: Use smaller thresholds for sensitive environments
- **Fast optimization**: Use larger trend windows for long-term analysis
- **High-frequency runs**: Reduce history size to manage memory
- **Stable environments**: Increase plateau detection sensitivity

## Best Practices

### 1. Continuous Monitoring

```java
// Track after every simulation
ConvergenceAnalysis latestAnalysis = tracker.recordSimulation(latestReport);

// Check for critical issues
if (latestAnalysis.hasProblems()) {
    alertTeam(latestAnalysis.getAlerts());
}
```

### 2. Historical Analysis

```java
// Compare current performance with history
TrackingSummary summary = tracker.getSummary();
System.out.println("Current fitness: " + summary.getCurrentFitness());
System.out.println("Historical max: " + summary.getMaxFitness());
System.out.println("Improvement trend: " + calculateImprovementTrend());
```

### 3. Alert Handling

```java
// Prioritize alerts by severity
latestAnalysis.getAlerts().stream()
    .sorted((a1, a2) -> a2.getSeverity().compareTo(a1.getSeverity()))
    .forEach(alert -> {
        if (alert.getSeverity() == ConvergenceAlert.AlertSeverity.HIGH) {
            handleCriticalAlert(alert);
        } else {
            handleNonCriticalAlert(alert);
        }
    });
```

### 4. Integration with CI/CD

```java
// Include convergence analysis in build process
ConvergenceAnalysis analysis = tracker.analyzeConvergence();

if (analysis.isConverged()) {
    System.out.println("Build passed: Convergence achieved");
    return BuildStatus.PASSED;
} else if (analysis.getStatus() == ConvergenceTracker.ConvergenceStatus.PLATEAUED) {
    System.out.println("Build warning: Convergence plateau detected");
    return BuildStatus.WARNING;
} else {
    System.out.println("Build failed: No convergence");
    return BuildStatus.FAILED;
}
```

## Troubleshooting

### Common Issues

1. **Insufficient Data**
   - Symptom: Status is always INSUFFICIENT_DATA
   - Fix: Run more simulations or reduce min samples

2. **False Positives**
   - Symptom: Too many alerts for normal behavior
   - Fix: Adjust thresholds or increase trend window

3. **Missed Convergence**
   - Symptom: System doesn't detect convergence
   - Fix: Check fitness threshold configuration

### Performance Considerations

- Memory usage scales with maxHistorySize
- Analysis time increases with trend window size
- Plateau detection is O(n) for sample size

## File Locations

All convergence tracking classes are in:
```
/Users/sac/yawl/test/org/yawlfoundation/yawl/integration/selfplay/
├── ConvergenceTracker.java
├── ConvergenceAnalysis.java
├── ConvergenceAlert.java
├── TrendAnalysis.java
├── PlateauDetection.java
└── TrackingSummary.java
```

Integration example:
```
/Users/sac/yawl/test/org/yawlfoundation/yawl/integration/selfplay/
└── SelfPlayWithConvergenceTracking.java
```

## Testing

Run the comprehensive test:
```bash
cd /Users/sac/yawl
java -cp test/org/yawlfoundation/yawl/integration/selfplay ConvergenceTrackerTest
```

This will demonstrate:
- Multiple convergence scenarios
- Alert generation and handling
- Trend analysis examples
- Plateau detection
- Actionable recommendations

## Conclusion

The ConvergenceTracker system provides comprehensive analysis of YAWL self-play convergence with actionable insights. By following this guide, you can effectively monitor and optimize your self-play performance.