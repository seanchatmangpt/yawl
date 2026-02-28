# FootprintScorer

The `FootprintScorer` class calculates behavioral footprint agreement scores between reference and generated workflows.

## Overview

A behavioral footprint represents the control-flow relationships in a workflow across three dimensions:

1. **Direct Succession**: Task A is immediately followed by Task B
2. **Concurrency**: Task A and Task B can execute in parallel
3. **Exclusivity**: Task A and Task B are mutually exclusive

## Scoring Methodology

The scorer uses Jaccard similarity for each footprint dimension:
```
Jaccard(A, B) = |A ∩ B| / |A ∪ B|
```

The final score is the macro-average across all three dimensions, resulting in a value between 0.0 and 1.0 where:
- 1.0 = Perfect behavioral conformance
- 0.0 = No behavioral conformance

## Usage

```java
// Create footprints
BehavioralFootprint reference = new BehavioralFootprint(
    Set.of(List.of("A", "B"), List.of("B", "C")),  // Direct succession
    Set.of(List.of("A", "C")),                    // Concurrency
    Set.of(List.of("B", "D"))                     // Exclusivity
);

BehavioralFootprint generated = new BehavioralFootprint(
    Set.of(List.of("A", "B"), List.of("B", "X")),
    Set.of(List.of("A", "C")),
    Set.of(List.of("B", "Y"))
);

// Calculate agreement score
FootprintScorer scorer = new FootprintScorer();
double agreement = scorer.scoreFootprint(reference, generated);

// Perfect generation: agreement == 1.0
if (agreement == 1.0) {
    System.out.println("Perfect behavioral conformance!");
}
```

## Key Features

- **Immutable**: `BehavioralFootprint` is thread-safe
- **Flexible**: Handles any workflow pattern
- **Accurate**: Uses mathematically sound Jaccard similarity
- **Null-safe**: Proper validation of input parameters

## Perfect Workflow Validation

For perfect workflow validation, use this scorer to verify that:
1. All direct succession relationships match exactly
2. All concurrency relationships match exactly
3. All exclusivity relationships match exactly

A score of 1.0 indicates perfect behavioral conformance.