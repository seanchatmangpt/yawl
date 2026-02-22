# Pattern Matching Issues - Detailed Analysis

**Date**: 2026-02-16

---

## Issue Summary

| # | File | Line | Type | Severity | Status |
|---|------|------|------|----------|--------|
| 1 | YWorkItem.java | 440 | Non-exhaustive switch | CRITICAL | NEEDS FIX |
| 2 | YWorkItem.java | 572 | Non-exhaustive switch | CRITICAL | NEEDS FIX |
| 3 | YEngine.java | 1474 | Scope violation | CRITICAL | NEEDS FIX |
| 4 | InterfaceB_EnvironmentBasedClient.java | 1241+ | Preview API | HIGH | SEPARATE ISSUE |

---

## ISSUE #1: YWorkItem.java - Non-Exhaustive Switch (Line 440)

### File Location
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java:440-447
```

### Error Message
```
error: the switch expression does not cover all possible input values
    YWorkItemTimer timer = switch (_timerParameters.getTimerType()) {
                           ^
```

### Current Code
```java
// Line 440-447
YWorkItemTimer timer = switch (_timerParameters.getTimerType()) {
    case Expiry -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getDate(), (pmgr != null));
    case Duration -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getWorkDayDuration(), (pmgr != null));
    case Interval -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getTicks(), _timerParameters.getTimeUnit(), (pmgr != null));
};
```

### Enum Definition
```java
// From YTimerParameters.java
public enum TimerType { Duration, Expiry, Interval, LateBound, Nil }
```

### Root Cause
The switch expression only covers 3 of 5 enum values:
- Duration ✅
- Expiry ✅
- Interval ✅
- LateBound ❌ NOT COVERED
- Nil ❌ NOT COVERED

Since this is a switch expression (not a statement), Java requires all possible input values to be covered.

### Why It Matters
The `LateBound` and `Nil` timer types might legitimately be passed to this method. Not handling them causes a compilation error.

### Fix Options

#### Option 1: Add cases for LateBound and Nil
```java
YWorkItemTimer timer = switch (_timerParameters.getTimerType()) {
    case Expiry -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getDate(), (pmgr != null));
    case Duration -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getWorkDayDuration(), (pmgr != null));
    case Interval -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getTicks(), _timerParameters.getTimeUnit(), (pmgr != null));
    case LateBound -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getVariable(), (pmgr != null));  // hypothetical
    case Nil -> null;  // or appropriate handling
};
```

#### Option 2: Add default case
```java
YWorkItemTimer timer = switch (_timerParameters.getTimerType()) {
    case Expiry -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getDate(), (pmgr != null));
    case Duration -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getWorkDayDuration(), (pmgr != null));
    case Interval -> new YWorkItemTimer(_workItemID.toString(),
            _timerParameters.getTicks(), _timerParameters.getTimeUnit(), (pmgr != null));
    default -> throw new UnsupportedOperationException(
            "Timer type " + _timerParameters.getTimerType() + " not yet supported");
};
```

#### Option 3: Revert to switch statement
```java
YWorkItemTimer timer = null;
switch (_timerParameters.getTimerType()) {
    case Expiry:
        timer = new YWorkItemTimer(_workItemID.toString(),
                _timerParameters.getDate(), (pmgr != null));
        break;
    case Duration:
        timer = new YWorkItemTimer(_workItemID.toString(),
                _timerParameters.getWorkDayDuration(), (pmgr != null));
        break;
    case Interval:
        timer = new YWorkItemTimer(_workItemID.toString(),
                _timerParameters.getTicks(), _timerParameters.getTimeUnit(), (pmgr != null));
        break;
    // LateBound and Nil just remain as null
}
```

### Recommendation
**Use Option 2** (throw exception). This makes the code explicit about unsupported cases and will catch configuration errors at runtime.

---

## ISSUE #2: YWorkItem.java - Non-Exhaustive Switch (Line 572)

### File Location
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java:572-576
```

### Error Message
```
error: the switch expression does not cover all possible input values
    YWorkItemStatus completionStatus = switch (completionFlag) {
                                       ^
```

### Current Code
```java
// Line 572-576
YWorkItemStatus completionStatus = switch (completionFlag) {
    case Normal -> statusComplete;
    case Force -> statusForcedComplete;
    case Fail -> statusFailed;
};
```

### Enum Definition
```java
// From WorkItemCompletion.java
public enum WorkItemCompletion {
    Normal(0),                    // a vanilla successful completion
    Force(1),                     // a forced, but successful, completion
    Fail(2),                      // a failed, and unsuccessful, completion
    Invalid(-1);
}
```

### Root Cause
The switch expression only covers 3 of 4 enum values:
- Normal ✅
- Force ✅
- Fail ✅
- Invalid ❌ NOT COVERED

The `Invalid` case is used as a sentinel value for invalid completions.

### Fix Options

#### Option 1: Add case for Invalid
```java
YWorkItemStatus completionStatus = switch (completionFlag) {
    case Normal -> statusComplete;
    case Force -> statusForcedComplete;
    case Fail -> statusFailed;
    case Invalid -> throw new IllegalArgumentException(
            "Cannot set completion status to Invalid");
};
```

#### Option 2: Add default case
```java
YWorkItemStatus completionStatus = switch (completionFlag) {
    case Normal -> statusComplete;
    case Force -> statusForcedComplete;
    case Fail -> statusFailed;
    default -> throw new IllegalArgumentException(
            "Unknown completion flag: " + completionFlag);
};
```

#### Option 3: Revert to switch statement
```java
YWorkItemStatus completionStatus;
switch (completionFlag) {
    case Normal:
        completionStatus = statusComplete;
        break;
    case Force:
        completionStatus = statusForcedComplete;
        break;
    case Fail:
        completionStatus = statusFailed;
        break;
    default:
        completionStatus = null;  // or handle Invalid differently
}
```

### Recommendation
**Use Option 1** (explicit Invalid case). This makes the Intent clear: attempting to complete a work item with Invalid status is a programming error.

---

## ISSUE #3: YEngine.java - Pattern Variable Scope Violation (Line 1474)

### File Location
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java:1449-1474
```

### Error Message
```
error: cannot find symbol
    if (netRunner != null) announceEvents(netRunner.getCaseID());
        ^
  symbol:   variable netRunner
  location: class YEngine
```

### Current Code
```java
// Line 1449-1474 (simplified)
startedItem = switch (workItem.getStatus()) {
    case statusEnabled -> {
        YNetRunner netRunner = getNetRunner(workItem.getCaseID());
        yield startEnabledWorkItem(netRunner, workItem, client);
    }
    case statusFired -> {
        YNetRunner netRunner = getNetRunner(workItem.getCaseID().getParent());
        yield startFiredWorkItem(netRunner, workItem, client);
    }
    case statusDeadlocked -> workItem;
    default -> { 
        rollbackTransaction();
        throw new YStateException(String.format(
                "Item [%s]: status [%s] does not permit starting.",
                 workItem.getIDString(), workItem.getStatus()));
    }
};

// PROBLEM: netRunner is NOT in scope here!
if (netRunner != null) announceEvents(netRunner.getCaseID());
```

### Root Cause
Pattern variables declared inside switch case blocks are **scoped to that block only**. Once the switch expression completes, all pattern variables from the switch are out of scope.

The `netRunner` variable is:
- Declared in the statusEnabled case block
- Declared again in the statusFired case block
- OUT OF SCOPE in both blocks after the yield
- DEFINITELY OUT OF SCOPE after the switch completes

Using `netRunner` at line 1474 is a compilation error.

### Why This Happened
When converting old switch statements to expressions with yield, variables need to be declared differently if they're used outside the switch.

### Fix Options

#### Option 1: Move declaration outside (RECOMMENDED)
```java
YNetRunner netRunner = null;  // declare outside switch
YWorkItem startedItem;

if (workItem != null && workItem.triggerMatchesStatus(_status)) {
    startedItem = switch (workItem.getStatus()) {
        case statusEnabled -> {
            netRunner = getNetRunner(workItem.getCaseID());
            yield startEnabledWorkItem(netRunner, workItem, client);
        }
        case statusFired -> {
            netRunner = getNetRunner(workItem.getCaseID().getParent());
            yield startFiredWorkItem(netRunner, workItem, client);
        }
        case statusDeadlocked -> workItem;
        default -> {
            rollbackTransaction();
            throw new YStateException(String.format(
                    "Item [%s]: status [%s] does not permit starting.",
                     workItem.getIDString(), workItem.getStatus()));
        }
    };
} else {
    rollbackTransaction();
    throw new YStateException("Cannot start null work item.");
}

// COMMIT POINT
commitTransaction();
if (netRunner != null) announceEvents(netRunner.getCaseID());
```

#### Option 2: Refactor to avoid post-switch usage
```java
startedItem = switch (workItem.getStatus()) {
    case statusEnabled -> {
        YNetRunner netRunner = getNetRunner(workItem.getCaseID());
        YWorkItem result = startEnabledWorkItem(netRunner, workItem, client);
        commitTransaction();
        announceEvents(netRunner.getCaseID());  // use here!
        yield result;
    }
    case statusFired -> {
        YNetRunner netRunner = getNetRunner(workItem.getCaseID().getParent());
        YWorkItem result = startFiredWorkItem(netRunner, workItem, client);
        commitTransaction();
        announceEvents(netRunner.getCaseID());  // use here!
        yield result;
    }
    case statusDeadlocked -> {
        commitTransaction();
        yield workItem;
    }
    default -> {
        rollbackTransaction();
        throw new YStateException(...);
    }
};
```

#### Option 3: Use a helper method
```java
startedItem = switch (workItem.getStatus()) {
    case statusEnabled -> handleEnabledWorkItem(workItem, client);
    case statusFired -> handleFiredWorkItem(workItem, client);
    case statusDeadlocked -> {
        commitTransaction();
        yield workItem;
    }
    default -> throw new YStateException(...);
};

private YWorkItem handleEnabledWorkItem(YWorkItem workItem, Client client) throws YPersistenceException {
    YNetRunner netRunner = getNetRunner(workItem.getCaseID());
    YWorkItem result = startEnabledWorkItem(netRunner, workItem, client);
    commitTransaction();
    announceEvents(netRunner.getCaseID());
    return result;
}

private YWorkItem handleFiredWorkItem(YWorkItem workItem, Client client) throws YPersistenceException {
    YNetRunner netRunner = getNetRunner(workItem.getCaseID().getParent());
    YWorkItem result = startFiredWorkItem(netRunner, workItem, client);
    commitTransaction();
    announceEvents(netRunner.getCaseID());
    return result;
}
```

### Recommendation
**Use Option 1** (move declaration outside). This is the minimal change that preserves the original logic while fixing the scope issue. It's also the clearest in intent: `netRunner` might be null or might be set depending on the status path taken.

---

## Pattern Matching Best Practices Violated

### 1. Non-exhaustive Switch Expressions
**Issue**: Switches that return values (expressions) must be exhaustive.
**Lesson**: When converting to switch expressions, verify ALL enum cases are handled.
**Prevention**: Add compiler flags to catch exhaustiveness errors:
```xml
<!-- In build.xml -->
<javac>
    <compilerarg value="-Werror:unchecked"/>
</javac>
```

### 2. Pattern Variable Scope
**Issue**: Pattern variables declared in switch cases have limited scope.
**Lesson**: Declare variables outside switch if they need to be used afterward.
**Prevention**: Code review checklist for pattern variable usage across scope boundaries.

---

## Verification Commands

### To verify fixes work:

```bash
# Check YWorkItem.java compiles
javac -d /tmp src/org/yawlfoundation/yawl/engine/YWorkItem.java

# Check YEngine.java compiles
javac -d /tmp src/org/yawlfoundation/yawl/engine/YEngine.java

# Full compile
cd build && ant compile

# Run tests
ant unitTest
```

---

## Summary

| Issue | Type | Fix Time | Difficulty | Risk |
|-------|------|----------|------------|------|
| #1 (YWorkItem.java:440) | Switch | <5 min | Easy | Low |
| #2 (YWorkItem.java:572) | Switch | <5 min | Easy | Low |
| #3 (YEngine.java:1474) | Scope | 10-15 min | Medium | Medium |

**Total estimated fix time**: 20-30 minutes

---

**Status**: READY FOR DEVELOPER ACTION
