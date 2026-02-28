# YAWL Lint Analysis Report

**Generated**: 2026-02-28
**Compiler Version**: Java 25
**Lint Flag**: `-Xlint:all`

---

## Executive Summary

**Current State**: 105 total warnings (only first 100 shown in logs) + 7 compilation errors
**Target State**: <50 warnings, 0 errors
**Estimated Reduction**: 52%

### Warning Breakdown by Type

| Warning Type | Count | Percentage | Priority | Effort | Impact |
|--------------|-------|-----------|----------|--------|--------|
| **[deprecation]** | 64 | 61% | HIGH | Medium | High |
| **[dep-ann]** | 31 | 30% | MEDIUM | Low | Medium |
| **[unchecked]** | 8+ | 8%+ | LOW | Low | Low |
| **TOTAL** | 105+ | 100% | - | - | - |

---

## Detailed Analysis

### 1. [deprecation] Warnings (64 total) - 61%

**Root Causes**:
- Hibernate 6.6.x deprecated APIs: `Session.delete()`, `Session.save()`, `Session.saveOrUpdate()`, `Session.createQuery(String)`, `Session.load()`
- MCP Schema deprecations: `CallToolResult(String, Boolean)`, `SyncToolSpecification(Tool, BiFunction)`, `Resource(String, String, String, String, Annotations)`
- YAWL legacy APIs: `InterfaceB_EnvironmentBasedClient` methods, `checkInWorkItem(String, String, String)`
- Hibernate Query API deprecation: `createQuery(String)`, `createNativeQuery(String)`, `getNamedQuery(String)`

**Files with Most Deprecations**:
1. `YPersistenceManager.java` (8 warnings) - Uses deprecated Hibernate 6 APIs
2. `YawlToolSpecifications.java` (30+ warnings) - MCP deprecations in tool result construction
3. `HibernateEngine.java` (8 warnings) - Deprecated query APIs
4. `InterfaceB_EnvironmentBasedClient.java` (19 warnings) - Legacy interface methods

**Fix Strategy**:
- Replace `Session.delete(obj)` with `Session.remove(obj)` (Hibernate 6+)
- Replace `Session.save(obj)` with `session.persist(obj)` or `session.merge(obj)`
- Replace `Session.createQuery(String)` with `Session.createQuery(String, Class)`
- Replace `Session.load(Class, Object)` with `Session.find(Class, Object)` or `get(Class, Object)`
- Update MCP Schema constructor calls to use new signatures
- Add `@Deprecated` annotations to legacy YAWL interface methods

**Estimated Cost**: 4-6 hours per module (requires testing with Hibernate 6 semantics)

---

### 2. [dep-ann] Warnings (31 total) - 30%

**Root Cause**: Methods that are documented as deprecated (via JavaDoc `@deprecated`) but not annotated with `@Deprecated`.

**Affected Files** (31 files):
- `YAWLServiceGateway.java`: 3 missing annotations
- `YVariable.java`: 1 missing annotation
- `YSpecification.java`: 2 missing annotations
- `WorkItemRecord.java`: 4 missing annotations
- `YAtomicTask.java`: 4 missing annotations
- `InterfaceB_EnvironmentBasedClient.java`: 18+ missing annotations
- `InterfaceA_EnvironmentBasedClient.java`: 2+ missing annotations
- `EngineGatewayImpl.java`: 2 missing annotations
- Multiple MCP spec files: Various missing annotations

**Fix Strategy**:
Add `@Deprecated` annotation above `@JavaDoc` on all methods documented as deprecated:

```java
/**
 * @deprecated Use {@link #newMethod()} instead
 */
@Deprecated(since = "6.0.0", forRemoval = false)
public void oldMethod() {
    // ...
}
```

**Estimated Cost**: 1-2 hours (mostly mechanical find-and-replace with regex)

---

### 3. [unchecked] Warnings (8+ total) - 8%+

**Root Cause**: Raw type usage and unsafe generic casts (hidden by `-Xlint:all` suppression).

**Patterns Identified**:
- Raw `List`, `Map`, `Set` without type parameters
- Unchecked generic casts
- Use of untyped collections in legacy code

**Files Affected**:
- Methods returning raw `Map` instead of `Map<String, T>`
- Raw `List` instead of `List<T>`
- Unsafe casting from Object to generic types

**Fix Strategy**:
1. Identify via: `mvn compile -Xlint:unchecked`
2. Add explicit type parameters to collections
3. For unavoidable cases: Use `@SuppressWarnings("unchecked")` with documentation

**Estimated Cost**: 2-3 hours

---

## Compilation Errors (7 total)

**Errors** (from compile-output.txt):
1. `YWorkItem.java:682` - `_specID.setIdentifier(id)` - method not found
2. `YWorkItem.java:687` - `_specID.setUri(uri)` - method not found
3. `YWorkItem.java:694` - `_specID.setVersion(version)` - method not found
4. `SpecificationData.java:192` - `_specificationID.setVersion(version)` - method not found
5. `stateless/engine/YWorkItem.java:665` - `_specID.setIdentifier(id)` - method not found
6. `stateless/engine/YWorkItem.java:670` - `_specID.setUri(uri)` - method not found
7. `stateless/engine/YWorkItem.java:677` - `_specID.setVersion(version)` - method not found

**Root Cause**: `YSpecificationID` class missing setter methods. Need to verify:
- Is `YSpecificationID` immutable? (check API)
- Should use builder pattern or constructor?
- Are these methods removed in Java 25 era refactoring?

**Impact**: CRITICAL - Build fails, must fix before proceeding

---

## Test Timeout Analysis

### Current Configuration
- `junit.jupiter.execution.timeout.default=90 s`
- `junit.jupiter.execution.timeout.testable.method.default=180 s`
- `junit.jupiter.execution.timeout.lifecycle.method.default=180 s`

### Observed Performance
- **Last test run**: 19 seconds total (121 tests)
- **Average per test**: 156 ms
- **P99 estimate**: <5 seconds (based on typical test patterns)

### Recommendations

**Current state is CONSERVATIVE but SAFE**:
- 90s default allows for slow database setup (H2, PostgreSQL TestContainers)
- 180s method timeout provides 2x buffer for integration tests
- Virtual thread overhead minimal in Java 21+

**Optimization Opportunities**:
1. Add profile-specific timeouts:
   - `quick-test`: 30s default (for unit tests)
   - `integration-test`: 180s default (for DB/Docker tests)

2. Keep 180s for lifecycle methods (handles TestContainers startup)

3. Monitor P99 test duration and adjust after profiling

**Estimated Impact**: 5-10% faster CI/CD when using quick-test profile

---

## Prioritized Fix List

### Phase 1: Critical (Fix First)
- [ ] Fix 7 compilation errors in YSpecificationID setters
- [ ] Validate YSpecificationID API
- [ ] Run compile check

### Phase 2: High Impact (Short Effort)
- [ ] Add 31 missing `@Deprecated` annotations (1-2 hours)
- [ ] Run compile check
- [ ] Verify no new warnings introduced

### Phase 3: High Frequency (Medium Effort)
- [ ] Replace Hibernate deprecated APIs in YPersistenceManager (2-3 hours)
- [ ] Replace Hibernate deprecated APIs in HibernateEngine (2-3 hours)
- [ ] Update query APIs: `createQuery()` → `createQuery(String, Class)`

### Phase 4: Large Volume (Variable Effort)
- [ ] Fix MCP deprecations in YawlToolSpecifications (3-4 hours)
- [ ] Update `InterfaceB_EnvironmentBasedClient` deprecated methods

### Phase 5: Remaining
- [ ] Fix unchecked warnings (2-3 hours)
- [ ] Test with `-Xlint:unchecked` flag

### Phase 6: Test Optimization
- [ ] Create quick-test profile in pom.xml
- [ ] Add timeout configuration per profile
- [ ] Document timeout strategy

---

## Compiler Configuration Changes

### Current: pom.xml
```xml
<arg>-Xlint:all</arg>
```

### Recommended: pom.xml (with suppressions for unavoidable cases)
```xml
<arg>-Xlint:all</arg>
<arg>-Xlint:-serial</arg>  <!-- Suppress serial version UID warnings -->
<!-- Keep deprecation, unchecked, raw types visible -->
```

---

## Test Timeout Configuration

### Current: junit-platform.properties
```properties
junit.jupiter.execution.timeout.default=90 s
junit.jupiter.execution.timeout.testable.method.default=180 s
junit.jupiter.execution.timeout.lifecycle.method.default=180 s
```

### Recommended: Create profiles in pom.xml

**Profile: quick-test** (unit tests only)
```xml
<profiles>
  <profile>
    <id>quick-test</id>
    <properties>
      <junit.timeout.default>30 s</junit.timeout.default>
      <junit.timeout.method>60 s</junit.timeout.method>
    </properties>
  </profile>
</profiles>
```

**Profile: integration-test** (with TestContainers)
```xml
<profiles>
  <profile>
    <id>integration-test</id>
    <properties>
      <junit.timeout.default>180 s</junit.timeout.default>
      <junit.timeout.method>300 s</junit.timeout.method>
    </properties>
  </profile>
</profiles>
```

---

## Success Criteria

| Metric | Current | Target | Status |
|--------|---------|--------|--------|
| Total warnings | 105+ | <50 | FAIL |
| [deprecation] warnings | 64 | <30 | FAIL |
| [dep-ann] warnings | 31 | 0 | FAIL |
| Compilation errors | 7 | 0 | FAIL |
| Test coverage | 121 tests | 121 tests | PASS |
| Average test time | 156 ms | <500 ms | PASS |
| Build time | ~10s | <15s | TBD |

---

## Files to Modify

### High Priority
1. `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YWorkItem.java` (COMPILE ERROR)
2. `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/SpecificationData.java` (COMPILE ERROR)
3. `/home/user/yawl/src/org/yawlfoundation/yawl/stateless/engine/YWorkItem.java` (COMPILE ERROR)
4. `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java` (8 deprecation warnings)
5. `/home/user/yawl/pom.xml` (compiler config + test profiles)

### Medium Priority
6. `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java` (8 deprecation warnings)
7. `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java` (30+ warnings)
8. Various Interface files (19+ deprecation + dep-ann warnings)

### Low Priority
9. Other files with <5 warnings each

---

## Next Steps

1. **Identify YSpecificationID location and API**
   - Search for class definition
   - Verify if setter methods should exist
   - Check git history for API changes

2. **Fix compilation errors** (blocking other work)

3. **Add @Deprecated annotations** (quick win)

4. **Replace Hibernate APIs** (medium effort, high impact)

5. **Create test profiles** (document and implement)

6. **Run full build verification**

---

## References

- Hibernate 6.6.x Deprecation Guide: https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/
- Java Compiler Options: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html
- JUnit Platform: https://junit.org/junit5/docs/current/user-guide/#platform-configuration-parameters
