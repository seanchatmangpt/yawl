# YAWL Coding Standards - Fortune 5 Production Quality

## Philosophy

This codebase follows **Toyota Production System** (Jidoka - stop the line) and **Chicago School TDD** (test real systems, not mocks) principles.

## The Five Commandments

### 1. NO MOCKS
```java
// ❌ FORBIDDEN
if (!sdkAvailable) {
    return mockResponse();
}

// ✅ REQUIRED
if (!sdkAvailable) {
    throw new IllegalStateException("SDK required. Add dependency to classpath.");
}
```

### 2. NO STUBS
```java
// ❌ FORBIDDEN
public String fetchData() {
    return ""; // stub
}

// ✅ REQUIRED
public String fetchData() {
    throw new UnsupportedOperationException(
        "API integration required. Implement fetchData() with real HTTP client."
    );
}
```

### 3. NO TODOs
```java
// ❌ FORBIDDEN
// TODO: implement validation

// ✅ REQUIRED - Either implement NOW or:
throw new UnsupportedOperationException("Validation not implemented");
```

### 4. NO FALLBACKS
```java
// ❌ FORBIDDEN
try {
    realImplementation();
} catch (Exception e) {
    return fakeData(); // Silent degradation
}

// ✅ REQUIRED
try {
    realImplementation();
} catch (Exception e) {
    throw new RuntimeException("Implementation failed: " + e.getMessage(), e);
}
```

### 5. NO LIES
```java
// ❌ FORBIDDEN
public boolean startWorkflow(String id) {
    System.out.println("Starting workflow...");
    return true; // Didn't actually start anything
}

// ✅ REQUIRED
public boolean startWorkflow(String id) {
    WorkflowEngine engine = getEngine(); // Real engine or throw
    return engine.start(id); // Real operation
}
```

## Enforcement

### Automated Validation
```bash
# Check code quality
./.claude/validate-no-mocks.sh

# Install pre-commit hook
ln -s ../../.claude/pre-commit-hook .git/hooks/pre-commit
```

### Manual Review Checklist
- [ ] No `mock`, `stub`, `fake` in method/class names
- [ ] No `TODO`, `FIXME`, `XXX`, `HACK` comments
- [ ] No empty returns (`return "";`) without clear purpose
- [ ] All missing dependencies cause startup failures
- [ ] All unimplemented features throw exceptions with clear messages
- [ ] No silent error swallowing
- [ ] No dual-mode behavior (real vs mock)

## When You Can't Implement

If you genuinely cannot implement a feature right now:

```java
/**
 * Processes advanced analytics.
 *
 * @throws UnsupportedOperationException always - analytics engine integration required
 */
public AnalyticsResult processAnalytics(Data data) {
    throw new UnsupportedOperationException(
        "Analytics engine integration required. To implement:\n" +
        "  1. Add analytics-engine-sdk dependency (v2.0+)\n" +
        "  2. Configure ANALYTICS_API_KEY environment variable\n" +
        "  3. Implement AnalyticsClient initialization\n" +
        "  4. Add error handling for network failures\n" +
        "See: https://docs.analytics-engine.com/java-sdk"
    );
}
```

## Testing Standards

### ✅ CORRECT - Chicago TDD
```java
@Test
public void testWorkflowStartsInRealEngine() {
    YEngine engine = new YEngine(); // Real engine
    engine.initialize();

    String caseId = engine.launchCase(specId, data);

    assertNotNull(caseId); // Real case ID from real engine
    assertTrue(engine.getCaseState(caseId).isRunning());
}
```

### ❌ FORBIDDEN - London TDD with Mocks
```java
@Test
public void testWorkflowStarts() {
    YEngine mockEngine = Mockito.mock(YEngine.class);
    when(mockEngine.launchCase(any(), any())).thenReturn("case-123");

    // Testing against fake behavior!
}
```

## Why This Matters

**For AI Assistants:**
- Mock code confuses AI about what's real vs fake
- Future AI can't distinguish working code from placeholders
- Wasted time debugging fake implementations

**For Engineers:**
- No surprises in production
- Clear error messages guide implementation
- Honest status of system capabilities

**For Business:**
- No hidden technical debt
- Predictable system behavior
- Fortune 5 reliability standards

## Questions?

**Q: What if I need to test without external dependencies?**
**A:** Use real test doubles (in-memory databases, local servers) not mocks. Example: H2 instead of mocking SQL, embedded Tomcat instead of mocking HTTP.

**Q: What about integration tests that are slow?**
**A:** Make them fast with proper test infrastructure. Don't fake them.

**Q: What if SDK is optional?**
**A:** Document it clearly and fail fast:
```java
public OptionalService() {
    if (!isAvailable()) {
        throw new IllegalStateException(
            "Optional SDK not installed. Install with: npm install optional-sdk"
        );
    }
}
```

**Q: Can I use mocks in tests?**
**A:** Only for true unit tests of isolated logic. Integration tests must use real systems.

---

**Remember:** When in doubt, FAIL FAST with a clear message.
Better to crash honestly than lie successfully.
