# Configurable Conformance Thresholds

This document explains the configurable conformance thresholds feature in the YAWL Process Mining Bridge.

## Overview

All conformance thresholds that were previously hardcoded have been made configurable through the `ConformanceConfig` struct. This allows customization of conformance checking behavior while maintaining backward compatibility.

## Configuration Structure

### ConformanceConfig

The `ConformanceConfig` struct contains the following configurable parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `fitness_threshold` | f64 | 0.9 | Minimum fitness score required for conformance |
| `production_weight` | f64 | 0.5 | Weight for production fitness in weighted average |
| `missing_weight` | f64 | 0.5 | Weight for missing fitness in weighted average |
| `false_positive_factor` | f64 | 0.1 | Factor for estimating false positives |
| `complexity_factor` | f64 | 0.3 | Factor affecting token consumption ratio |
| `min_consumption_ratio` | f64 | 0.5 | Minimum token consumption ratio |

## Default Values and Reasoning

### Fitness Threshold (0.9)
- **Default**: 0.9 (90% fitness required)
- **Reasoning**: This is the industry standard for "good" conformance in process mining.
- **Range**: 0.0 to 1.0

### Production Weight (0.5)
- **Default**: 0.5 (50% weight)
- **Reasoning**: Equal weighting between production fitness and missing fitness provides balanced results.
- **Note**: Should sum to 1.0 with `missing_weight`

### Missing Weight (0.5)
- **Default**: 0.5 (50% weight)
- **Reasoning**: Equal weighting between production fitness and missing fitness.
- **Note**: Should sum to 1.0 with `production_weight`

### False Positive Factor (0.1)
- **Default**: 0.1 (10% of activities)
- **Reasoning**: Assumes 10% of activities in a model might be false positives in typical scenarios.
- **Range**: 0.0 to 1.0

### Complexity Factor (0.3)
- **Default**: 0.3 (30% reduction per unit complexity)
- **Reasoning**: Allows for significant reduction in consumed ratio as log complexity increases.
- **Range**: 0.0 to 1.0

### Minimum Consumption Ratio (0.5)
- **Default**: 0.5 (50% minimum)
- **Reasoning**: Ensures at least half of tokens are consumed regardless of complexity.
- **Range**: 0.0 to 1.0

## Usage Examples

### Java (JNI)

```java
// Use default configuration
ConformanceConfig config = new ConformanceConfig();

// Create custom configuration
ConformanceConfig config = new ConformanceConfig();
config.setFitnessThreshold(0.85);
config.setProductionWeight(0.7);
config.setMissingWeight(0.3);
config.setFalsePositiveFactor(0.05);
config.setComplexityFactor(0.2);
config.setMinConsumptionRatio(0.6);

// Use in conformance checking
ConformanceResult result = ConformanceChecker.checkConformance(eventLogHandle, pnmlXml, config);
boolean isConformant = result.isConformant(); // Uses custom threshold
```

### Python

```python
# Use default configuration
config = ConformanceConfig()

# Create custom configuration
config = ConformanceConfig(
    fitness_threshold=0.85,
    production_weight=0.7,
    missing_weight=0.3,
    false_positive_factor=0.05,
    complexity_factor=0.2,
    min_consumption_ratio=0.6
)

# Use in conformance checking
result = check_conformance(event_log, pnml, config)
is_conformant = result['is_conformant']  # Uses custom threshold
```

## Predefined Configurations

### Strict Configuration
```python
strict_config = ConformanceConfig(
    fitness_threshold=0.95,  # Only accept 95%+ fitness
    production_weight=0.7,
    missing_weight=0.3,
    false_positive_factor=0.05,
    complexity_factor=0.2,
    min_consumption_ratio=0.7
)
```

### Lenient Configuration
```python
lenient_config = ConformanceConfig(
    fitness_threshold=0.7,   # Accept 70%+ fitness
    production_weight=0.3,
    missing_weight=0.7,
    false_positive_factor=0.2,
    complexity_factor=0.4,
    min_consumption_ratio=0.3
)
```

### Production-Focused Configuration
```python
production_config = ConformanceConfig(
    fitness_threshold=0.85,
    production_weight=0.9,   # Heavily favor production
    missing_weight=0.1,
    false_positive_factor=0.15,
    complexity_factor=0.1,
    min_consumption_ratio=0.8
)
```

## Backward Compatibility

- Existing code that doesn't specify a configuration uses the default `ConformanceConfig`
- All default values match the original hardcoded values
- No breaking changes to existing APIs
- Gradual migration path for existing applications

## Benefits

1. **Flexibility**: Adapt conformance checking to different use cases
2. **Maintainability**: No need to modify core code for threshold changes
3. **Documentation**: Defaults are documented with clear reasoning
4. **Testing**: Easy to test with different threshold combinations
5. **Production Ready**: Supports various production requirements

## Migration Guide

### For Existing Applications

1. **No immediate action required** - existing code continues to work with defaults
2. **Gradual customization** - add configuration as needed for specific use cases
3. **Performance monitoring** - observe impact of threshold changes in production

### For New Applications

1. **Choose appropriate configuration** based on your use case
2. **Consider trade-offs** between strictness and leniency
3. **Monitor and adjust** as you gain experience with your data

## Testing

The implementation includes comprehensive tests that verify:
- Default values match specified defaults
- Custom configurations work correctly
- Boundary conditions are handled properly
- All weights sum appropriately
- Configuration is cloned and debugged correctly

Run tests with:
```bash
cargo test
```