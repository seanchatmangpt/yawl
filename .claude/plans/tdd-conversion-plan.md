# Chicago TDD Conversion Plan for Observability Tests

## Overview
Convert 4 observability test files from London TDD style (using Mockito mocks) to Chicago TDD style (using real implementations and testing actual behavior).

## Files to Convert

### 1. SLOAlertManagerTest.java
- **Current**: Uses mocked `AndonCord` with 18 verify() calls
- **Mock Usage**:
  - `@Mock private AndonCord andonCord;`
  - `verify(andonCord, atLeast(1)).pull(...)`
  - `verify(andonCord, times(1)).acknowledge(...)`
  - `verify(andonCord, never()).release(...)`

### 2. SLOTrackerTest.java
- **Current**: Uses mocked `AndonCord` with 2 verify() calls
- **Mock Usage**:
  - `@Mock private AndonCord andonCord;`
  - `verify(andonCord, atLeast(1)).triggerCriticalAlert(...)`
  - `verify(andonCord, atLeast(1)).triggerWarning(...)`

### 3. SLOPredictiveAnalyticsTest.java
- **Current**: Uses mocked `AndonCord` with 2 verify() calls
- **Mock Usage**:
  - `@Mock private AndonCord andonCord;`
  - `verify(andonCord, atLeast(1)).triggerCriticalAlert(...)`
  - `verify(andonCord, atLeast(1)).triggerWarning(...)`

### 4. Missing File: ObservabilityMetricsTest.java
- **Status**: File does not exist, will need to be created

### 5. Missing File: ServiceHealthCheckTest.java
- **Status**: File does not exist, will need to be created

## Chicago TDD Conversion Strategy

### Key Principles
1. **Remove all mocking** - Replace mocks with real implementations
2. **Test behavior, not interactions** - Verify outcomes using assertions
3. **Use in-memory implementations** - For testable behavior
4. **Implement real alert routing** - Instead of verifying mock calls
5. **Create real metrics storage** - Instead of mocked metrics

### Real Implementations Needed

#### 1. TestAndonCord Implementation
Create a real `TestAndonCord` class that:
- Extends/inherits from `AndonCord` or implements test-specific behavior
- Captures all alert calls for verification
- Provides methods to check alert history
- Implements the same interface as production `AndonCord`
- Tracks alert counts by severity
- Stores alert history for assertions

#### 2. Test Metrics Registry
Use `SimpleMeterRegistry` for real metrics:
- Already used in existing tests
- Verify actual metric registration
- Check metric values after operations
- Count metric increments

#### 3. Real Alert Processing
For `SLOAlertManagerTest`:
- Create real alert routing logic
- Test actual alert creation and processing
- Verify alert state transitions (→ ACKNOWLEDGED → RESOLVED)
- Test alert suppression and deduplication logic
- Test real alert routing to channels

### Conversion Steps

#### Phase 1: Remove Mock Dependencies
1. Remove `@Mock`, `@InjectMocks`, `@MockBean` annotations
2. Remove `when().thenReturn()` patterns
3. Remove all `verify()` calls
4. Create real implementations in `@BeforeEach`

#### Phase 2: Implement Test Doubles
1. Create `TestAndonCord` class for testing alert behavior
2. Create real alert storage mechanisms
3. Implement real alert routing logic
4. Add helper methods for verification

#### Phase 3: Convert Assertions
1. Replace verify calls with state-based assertions
2. Check actual alert history in the test double
3. Verify metric values through the registry
4. Assert on actual outcomes instead of interactions

#### Phase 4: Enhance Coverage
1. Add edge case tests
2. Test concurrent scenarios with real threading
3. Verify error handling paths
4. Test metric registration and updates

## Specific Changes for Each File

### SLOAlertManagerTest.java
- **Remove**: 18 verify() calls
- **Replace**: Mocked `AndonCord` with `TestAndonCord`
- **Add**: Real alert processing verification
- **Test**: Alert creation, routing, acknowledgment, resolution
- **Verify**: Alert counts by severity, state transitions

### SLOTrackerTest.java
- **Remove**: 2 verify() calls for alert triggering
- **Replace**: Mocked `AndonCord` with real implementation
- **Test**: Actual alert generation based on violations
- **Verify**: Alert creation through test double
- **Check**: Alert counts and types

### SLOPredictiveAnalyticsTest.java
- **Remove**: 2 verify() calls for prediction alerts
- **Replace**: Mocked `AndonCord` with real implementation
- **Test**: Real breach prediction and alert generation
- **Verify**: Alert generation based on predictions
- **Check**: Model accuracy and forecasting

### New Files to Create
1. **ObservabilityMetricsTest.java**
   - Test metric registration
   - Test metric collection
   - Test metric reporting
   - Use real meter registry

2. **ServiceHealthCheckTest.java**
   - Test service health status
   - Test failure detection
   - Test recovery mechanisms
   - Test health check endpoints

## Test Double Implementation

```java
public class TestAndonCord extends AndonCord {
    private final List<Alert> alertHistory = new CopyOnWriteArrayList<>();
    private final Map<Severity, Integer> alertCounts = new ConcurrentHashMap<>();

    // Override methods to capture calls
    @Override
    public Alert pull(Severity severity, String alertName, Map<String, Object> context) {
        Alert alert = super.pull(severity, alertName, context);
        alertHistory.add(alert);
        alertCounts.merge(severity, 1, Integer::sum);
        return alert;
    }

    // Verification methods
    public int getAlertCount(Severity severity) {
        return alertCounts.getOrDefault(severity, 0);
    }

    public int getTotalAlerts() {
        return alertHistory.size();
    }

    public List<Alert> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }
}
```

## Expected Outcomes

1. **Cleaner Tests** - No mock dependencies or verify calls
2. **Real Behavior** - Tests verify actual functionality
3. **Better Coverage** - Test real integration points
4. **Faster Tests** - No mock setup/teardown overhead
5. **More Reliable** - Test what users actually experience

## Success Criteria
- All tests pass after conversion
- No Mockito annotations or verify() calls remain
- Tests use real implementations where possible
- Test coverage maintained or improved
- Tests compile and run successfully