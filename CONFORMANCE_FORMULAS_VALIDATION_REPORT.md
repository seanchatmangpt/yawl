# Conformance Formulas Validation Report

## Executive Summary

**Status**: ❌ **VIOLATIONS DETECTED** - Hardcoded values found in conformance formulas

This report validates that conformance formulas use real mathematical implementations and not hardcoded values. The validation covers Rust (JNI and Python) and Java implementations.

## Violations Found

### 1. Rust JNI Implementation (`yawl-rust4pm/rust4pm/src/jni/conformance.rs`)

#### Hardcoded Values (H_STUB Violations):
```rust
// Line 145-146: Hardcoded token ratios
let consumed_ratio = 0.85; // 85% of tokens consumed
let missing_ratio = 0.15; // 15% of tokens missing

// Line 158: Hardcoded coverage factor
let coverage = (unique_activities as f64 * 0.92) / (event_count as f64).max(1.0);

// Line 167: Hardcoded escaped ratio
let escaped_ratio = 0.12; // 12% escaped activities

// Line 170: Hardcoded precision fallback
0.88

// Line 176: Hardcoded complexity penalty
let complexity_penalty = activity_ratio * 0.3;
```

#### Issues:
- **Fitness calculation**: Uses hardcoded `0.85` consumed ratio instead of actual token replay results
- **Completeness**: Uses hardcoded `0.92` multiplier
- **Precision**: Uses hardcoded `0.88` fallback value
- **Simplicity**: Uses hardcoded `0.3` complexity penalty

### 2. Rust Python Implementation (`yawl-rust4pm/rust4pm/src/python/conformance.rs`)

#### Hardcoded Values (H_STUB Violations):
```rust
// Line 139: Hardcoded multipliers
let total_possible_activities = (unique_activities as f64 * 1.2).max(event_count as f64 * 0.8);

// Line 201: Hardcoded base fitness
0.85 + (unique_activities as f64 * 0.01).min(0.15)

// Line 217: Hardcoded precision calculation
0.75 + activity_ratio * 0.25

// Line 236: Hardcoded base score
0.90 - complexity_penalty

// Line 257-259: Hardcoded token replay simulation
let consumed = (event_count as f64 * 0.85) as i32; // 85% consumed
let missing = ((event_count as f64 * 0.15) as i32).max(0);
let remaining = (unique_activities as f64 * 0.05) as i32;
```

#### Issues:
- **Precision**: Formula doesn't match academic definition `precision = true_positives / (true_positives + false_positives)`
- **Fitness**: Uses simulated hardcoded values instead of real token replay
- **Generalization**: Hardcoded `0.7` and `0.3` weightings
- **Token replay**: All metrics are hardcoded simulations

### 3. Java Implementation (`src/org/yawlfoundation/yawl/integration/processmining/ConformanceFormulas.java`)

#### Good Practices Found:
✅ **Real fitness formula**: 
```java
double productionRatio = produced > 0 ? (double) consumed / produced : 1.0;
double missingRatio = (produced + missing) > 0 ? 
                     (double) (produced + missing - missing) / (produced + missing) : 1.0;
double fitness = 0.5 * Math.min(productionRatio, 1.0) + 0.5 * missingRatio;
```

✅ **Real precision formula**:
```java
double escapedRatio = (double) structural.escapedEdges() / structural.arcCount();
return Math.max(0.0, 1.0 - escapedRatio);
```

#### Issues Found:
❌ **Mock implementations** in helper methods:
```java
// Line 288: Fallback hardcoded count
Ok(42) // Simulated count

// Line 299: Fallback hardcoded activities
Ok(15) // Simulated unique activities
```

## Academic Formula Compliance

### Expected vs Actual Formulas

| Metric | Academic Formula | Current Implementation | Status |
|--------|------------------|----------------------|---------|
| **Fitness** | `sum(observed) / sum(expected)` | ✅ Real formula in Java<br>❌ Hardcoded in Rust | Mixed |
| **Precision** | `true_positives / (true_positives + false_positives)` | ✅ Real formula (escaped edges)<br>❌ Wrong in Rust | Mixed |
| **Generalization** | Based on model complexity | ❌ Hardcoded weights in Rust | ❌ Invalid |
| **Simplicity** | Based on model complexity | ✅ Real formula in Java<br>❌ Hardcoded in Rust | Mixed |

## Critical Violations

1. **Rust implementations use hardcoded values instead of real calculations**
2. **Python precision formula doesn't match academic definition**
3. **Token replay metrics are simulated, not real**
4. **Generalization formula is arbitrary, not based on literature**

## Recommendation

### Immediate Actions Required:

1. **Fix Rust JNI implementation**:
   - Replace hardcoded `0.85` with actual token replay results
   - Implement real precision calculation
   - Remove hardcoded simulation values

2. **Fix Rust Python implementation**:
   - Implement proper precision: `true_positives / (true_positives + false_positives)`
   - Use real token replay instead of simulated values
   - Fix generalization based on process mining literature

3. **Fix Java helper methods**:
   - Replace hardcoded returns with real calculations
   - Implement proper trace parsing

---

*Generated on 2026-03-03*
