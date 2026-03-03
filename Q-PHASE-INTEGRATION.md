# Q-Phase Integration Plan

## Current Status

✅ **H-GUARDS phase** is fully implemented and GREEN  
✅ **Q-INVARIANTS-DESIGN.md** exists with full specifications  
✅ **q-phase-invariants.sh** MVP hook exists (current implementation)  
✅ **q-validate.sh** script created (new alternative)  
✅ **dx.sh integration** exists and runs Q phase after H phase  

## Q Phase Validation Overview

The Q phase enforces 4 semantic invariants on generated Java code:

### Q1: real_impl ∨ throw
- Every method must either have real implementation OR throw `UnsupportedOperationException`
- Detects: Empty methods (`{}`), one-liner stubs (`return ""`), etc.
- Script support: `validate_q1_real_impl_or_throw()` in q-validate.sh

### Q2: ¬mock  
- No mock, stub, fake, or demo objects in generated code
- Detects: Class names like `MockService`, `StubValidator`
- Script support: `validate_q2_no_mock_objects()` in q-validate.sh

### Q3: ¬silent_fallback
- Catch blocks must propagate exceptions, log meaningfully, or provide real alternatives
- Detects: `catch { return Collections.emptyList(); }` patterns
- Script support: `validate_q3_no_silent_fallback()` in q-validate.sh

### Q4: ¬lie (semantic)
- Method behavior must match documented contract
- MVP: Basic checks only; future: Full SHACL pipeline
- Script support: `validate_q4_no_lies()` in q-validate.sh

## Available Scripts

### 1. MVP Hook (Current)
**Location**: `.claude/hooks/q-phase-invariants.sh`
**Usage**: 
```bash
bash .claude/hooks/q-phase-invariants.sh --emit-dir ./emit
```
**Integration**: Used by dx.sh in Q phase
**Status**: Active, regex-based detection

### 2. Enhanced Script (New)
**Location**: `q-validate.sh`
**Usage**: 
```bash
bash q-validate.sh
```
**Integration**: Can be used standalone or with dx.sh
**Status**: New implementation with enhanced regex patterns

## Integration Options

### Option 1: Use Current Implementation (Recommended)
No changes needed. dx.sh already uses the MVP hook successfully.

**Pros**:
- Already integrated in dx.sh
- Well-tested in production
- Minimal changes

**Cons**:
- Limited to regex-based detection
- Q4 (semantic) not fully implemented

### Option 2: Replace with Enhanced Script
Update dx.sh to use q-validate.sh instead of the hook.

**Pros**:
- Enhanced regex patterns
- Better error messages
- Consistent interface

**Cons**:
- Need to update dx.sh
- New script needs testing

### Option 3: Dual Implementation (Recommended for transition)
Keep both scripts, use enhanced script optionally.

**Usage**:
```bash
# Use enhanced version for development
bash q-validate.sh --verbose

# Use current version in CI
bash .claude/hooks/q-phase-invariants.sh
```

## Test Scenarios

### Test 1: Empty Methods (Q1 Violation)
```java
// VIOLATION
public void process() { }

// CORRECT  
public void process() {
    throw new UnsupportedOperationException("process() requires real implementation");
}
```

### Test 2: Mock Classes (Q2 Violation)
```java
// VIOLATION
public class MockDataService implements DataService { }

// CORRECT
public class DataService implements DataService { }
```

### Test 3: Silent Fallback (Q3 Violation)
```java
// VIOLATION
try {
    return engine.fetchCase(caseId);
} catch (Exception e) {
    return Collections.emptyList();  // silent fallback
}

// CORRECT
try {
    return engine.fetchCase(caseId);
} catch (Exception e) {
    throw new WorkflowException("Failed to fetch case", e);
}
```

### Test 4: Documentation Mismatch (Q4 Violation)
```java
// VIOLATION
/** @return never null */
public String getData() {
    return null;  // lies about contract
}

// CORRECT
/** @return never null */
public String getData() {
    return Objects.requireNonNull(data, "Data must not be null");
}
```

## Exit Codes

| Exit Code | Meaning | Action |
|-----------|---------|--------|
| 0 | GREEN | Proceed to Ω phase |
| 1 | Transient error | Retry |
| 2 | Violations found | Fix and re-run |

## Receipt Format

Both scripts generate the same receipt format:

```json
{
  "phase": "invariants",
  "timestamp": "2026-03-01T10:30:00Z",
  "code_directory": "emit",
  "java_files_scanned": 42,
  "total_violations": 3,
  "invariant_results": {
    "Q1_real_impl_or_throw": "FAIL (2 violations)",
    "Q2_no_mock_objects": "FAIL (1 violations)", 
    "Q3_no_silent_fallback": "PASS (0 violations)",
    "Q4_no_lies": "PASS (0 violations)"
  },
  "status": "RED",
  "message": "Invariant violations detected",
  "exit_code": 2
}
```

## Next Steps

1. **Test q-validate.sh**: Run against existing codebase
2. **Validation**: Compare results with current MVP hook
3. **Decision**: Choose integration option based on testing
4. **Implementation**: Update dx.sh if switching to enhanced script
5. **Documentation**: Update team guides with new options

## Migration Path

### Phase 1: Testing
- Run both scripts on generated code
- Compare violation detection rates
- Performance benchmarking

### Phase 2: Integration  
- Update dx.sh to use enhanced script
- Keep MVP hook as fallback

### Phase 3: Enhancement
- Implement Q4 (semantic) with SHACL
- Add more sophisticated pattern detection
