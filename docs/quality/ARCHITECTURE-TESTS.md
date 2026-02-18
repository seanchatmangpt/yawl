# YAWL v6.0.0 Architecture Tests (ArchUnit)

## Overview

YAWL uses **ArchUnit** to enforce architectural invariants at compile time. These tests prevent architectural erosion by validating layer boundaries, naming conventions, and dependency rules.

**Test Location**: `test/org/yawlfoundation/yawl/quality/architecture/`

## Test Classes

| Class | Purpose |
|-------|---------|
| `YawlLayerArchitectureTest.java` | Layer isolation and dependency direction |
| `YawlCycleDetectionTest.java` | Package cycle detection |
| `YawlPackageBoundaryTest.java` | Naming conventions and visibility rules |

## Running Architecture Tests

```bash
# Run all ArchUnit tests
mvn test -Dtest="org.yawlfoundation.yawl.quality.architecture.*"

# Run specific test class
mvn test -Dtest="YawlLayerArchitectureTest"

# Run with verbose output
mvn test -Dtest="YawlLayerArchitectureTest" -X
```

---

## Layer Model

YAWL follows a strict layered architecture:

```
integration  (top-most: MCP, A2A, external consumers)
    |
engine       (stateful workflow execution, persistence)
    |
stateless    (in-memory workflow execution, no persistence)
    |
elements     (Petri net element model: tasks, conditions, nets)
    |
authentication  (session, JWT, CSRF)
    |
util / schema / exceptions / logging  (infrastructure, no domain deps)
```

**Rule**: Lower layers must never import from upper layers. Dependencies flow downward only.

---

## ArchUnit Rules (8 Rules)

### 1. Layer Isolation Rule

**File**: `YawlLayerArchitectureTest.java`
**Rule Name**: `layerIsolationRule`

Enforces that each layer may only be accessed by layers above it.

```java
@ArchTest
public static final ArchRule layerIsolationRule = layeredArchitecture()
    .consideringAllDependencies()
    .layer("Infrastructure").definedBy(UTIL_PKG, SCHEMA_PKG, EXCEPTIONS_PKG, LOGGING_PKG)
    .layer("Authentication").definedBy(AUTH_PKG, SECURITY_PKG)
    .layer("Unmarshal").definedBy(UNMARSHAL_PKG)
    .layer("Elements").definedBy(ELEMENTS_PKG)
    .layer("Stateless").definedBy(STATELESS_PKG)
    .layer("Engine").definedBy(ENGINE_PKG)
    .layer("Integration").definedBy(INTEGRATION_PKG)

    .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers(
        "Authentication", "Unmarshal", "Elements", "Stateless", "Engine", "Integration")
    .whereLayer("Authentication").mayOnlyBeAccessedByLayers("Engine", "Integration")
    .whereLayer("Unmarshal").mayOnlyBeAccessedByLayers("Elements", "Stateless", "Engine", "Integration")
    .whereLayer("Elements").mayOnlyBeAccessedByLayers("Stateless", "Engine", "Integration")
    .whereLayer("Stateless").mayOnlyBeAccessedByLayers("Engine", "Integration")
    .whereLayer("Engine").mayOnlyBeAccessedByLayers("Integration")
    .whereLayer("Integration").mayNotBeAccessedByAnyLayer();
```

**Violation Example**:
```
Architecture violation: Layer 'Elements' depends on layer 'Engine'
  at org.yawlfoundation.yawl.elements.YTask.getYEngine(YTask.java:45)
```

**Fix**: Move the dependency upward or use dependency injection.

---

### 2. Elements Must Not Depend on Engine

**File**: `YawlLayerArchitectureTest.java`
**Rule Names**: `elementsMustNotDependOnEngine`, `elementsMustNotDependOnIntegration`

The `elements` package contains the Petri net domain model. It must remain independent of the engine and integration layers.

```java
@ArchTest
public static final ArchRule elementsMustNotDependOnEngine =
    noClasses().that().resideInAPackage(ELEMENTS_PKG)
        .should().dependOnClassesThat()
            .resideInAPackage(ENGINE_PKG)
        .as("Elements package must not depend on Engine package.");
```

**Why**: Elements are pure domain objects. Engine depends on elements, not the reverse.

---

### 3. Infrastructure Must Not Depend on Domain

**File**: `YawlLayerArchitectureTest.java`
**Rule Names**: `utilMustNotDependOnEngine`, `schemaPackageMustNotDependOnEngine`, `exceptionsMustNotDependOnEngine`

Infrastructure packages (`util`, `schema`, `exceptions`, `logging`) must not import domain packages.

```java
@ArchTest
public static final ArchRule utilMustNotDependOnEngine =
    noClasses().that().resideInAPackage(UTIL_PKG)
        .should().dependOnClassesThat()
            .resideInAnyPackage(ENGINE_PKG, ELEMENTS_PKG, STATELESS_PKG, INTEGRATION_PKG)
        .as("Util package must not depend on domain packages.");
```

**Why**: Infrastructure is reusable across projects. Domain coupling prevents reuse.

---

### 4. Stateless Must Not Depend on Stateful Persistence

**File**: `YawlLayerArchitectureTest.java`
**Rule Names**: `statelessMustNotDependOnEnginePersistence`, `statelessMustNotDependOnYEngine`

The stateless engine must never import persistence classes from the stateful engine.

```java
@ArchTest
public static final ArchRule statelessMustNotDependOnEnginePersistence =
    noClasses().that().resideInAPackage(STATELESS_PKG)
        .should().dependOnClassesThat()
            .haveSimpleNameContaining("Persistence")
            .and().resideInAPackage(ENGINE_PKG)
        .as("Stateless engine must not import persistence classes from the stateful engine.");
```

**Why**: Stateless operation is an invariant. Adding persistence breaks isolation guarantees.

---

### 5. No Cyclic Package Dependencies

**File**: `YawlLayerArchitectureTest.java`, `YawlCycleDetectionTest.java`
**Rule Name**: `noCyclicPackageDependencies`, `noTopLevelPackageCycles`

No package cycles allowed within `org.yawlfoundation.yawl.*`.

```java
@ArchTest
public static final ArchRule noCyclicPackageDependencies =
    slices().matching("org.yawlfoundation.yawl.(*)..")
        .should().beFreeOfCycles()
        .as("Top-level YAWL packages must be free of cyclic dependencies.");
```

**Violation Example**:
```
Cycle detected:
  org.yawlfoundation.yawl.engine -> org.yawlfoundation.yawl.elements
  org.yawlfoundation.yawl.elements -> org.yawlfoundation.yawl.engine
```

**Fix**: Extract shared code to a lower layer or use interfaces with dependency injection.

---

### 6. Security Isolation from UI

**File**: `YawlLayerArchitectureTest.java`
**Rule Name**: `swingMustNotAccessSecurityInternals`

Swing worklist UI must not directly access security internals.

```java
@ArchTest
public static final ArchRule swingMustNotAccessSecurityInternals =
    noClasses().that().resideInAPackage(SWING_PKG)
        .should().dependOnClassesThat()
            .resideInAPackage(SECURITY_PKG)
        .as("Swing worklist UI must not access security internals.");
```

**Why**: Authentication must be handled through the engine layer, not bypassed.

---

### 7. Authentication Must Not Depend on Persistence

**File**: `YawlLayerArchitectureTest.java`
**Rule Names**: `authMustNotDependOnEnginePersistence`, `integrationMustNotInstantiateSessionsDirectly`

Authentication and integration layers must not directly access persistence managers.

```java
@ArchTest
public static final ArchRule authMustNotDependOnEnginePersistence =
    noClasses().that().resideInAPackage(AUTH_PKG)
        .should().dependOnClassesThat()
            .haveNameMatching(".*YPersistenceManager.*")
        .as("Authentication must not depend on YPersistenceManager.");
```

**Why**: Auth is a cross-cutting concern independent of persistence strategy.

---

### 8. Naming and Visibility Conventions

**File**: `YawlPackageBoundaryTest.java`
**Rule Names**: Multiple (see below)

Enforces naming conventions and visibility rules:

```java
// Exception classes must end with "Exception"
@ArchTest
public static final ArchRule exceptionClassesMustEndWithException =
    classes().that().areAssignableTo(Exception.class)
        .and().resideInAPackage("org.yawlfoundation.yawl..")
        .should().haveSimpleNameEndingWith("Exception");

// Password fields must be private
@ArchTest
public static final ArchRule securityCredentialFieldsMustBePrivate =
    fields().that().haveNameContaining("password")
        .and().resideInAPackage("org.yawlfoundation.yawl..")
        .should().bePrivate();

// No System.exit() in library code
@ArchTest
public static final ArchRule noSystemExitInEnginePackage =
    noClasses().that().resideInAPackage("org.yawlfoundation.yawl.engine..")
        .should().callMethod(System.class, "exit", int.class);

// No raw Thread creation in integration layer
@ArchTest
public static final ArchRule noRawThreadCreationInIntegrationPackage =
    noClasses().that().resideInAPackage("org.yawlfoundation.yawl.integration..")
        .should().dependOnClassesThat()
            .areAssignableTo(Thread.class)
            .and().haveSimpleName("Thread");
```

---

## Adding New Architecture Rules

### Template for Layer Rule

```java
@ArchTest
public static final ArchRule myNewLayerRule =
    noClasses().that().resideInAPackage("org.yawlfoundation.yawl.myPackage..")
        .should().dependOnClassesThat()
            .resideInAPackage("org.yawlfoundation.yawl.forbiddenPackage..")
        .as("MyPackage must not depend on ForbiddenPackage because [reason].");
```

### Template for Naming Rule

```java
@ArchTest
public static final ArchRule myNamingRule =
    classes().that().resideInAPackage("org.yawlfoundation.yawl.myPackage..")
        .and().arePublic()
        .should().haveSimpleNameStartingWith("MyPrefix")
        .as("Public classes in myPackage must start with 'MyPrefix'.");
```

### Template for Cycle Detection

```java
@ArchTest
public static final ArchRule noCyclesInMyPackage =
    slices().matching("org.yawlfoundation.yawl.myPackage.(*)..")
        .should().beFreeOfCycles()
        .allowEmptyShould(true)
        .as("MyPackage sub-packages must not form cycles.");
```

---

## CI/CD Integration

Architecture tests run as part of the standard test suite:

```yaml
# GitHub Actions
- name: Run Architecture Tests
  run: mvn test -Dtest="org.yawlfoundation.yawl.quality.architecture.*"
```

**Quality Gate**: Architecture violations fail the build. No exceptions.

---

## Troubleshooting

### Common Violations

| Violation | Cause | Fix |
|-----------|-------|-----|
| Layer access violation | Lower layer imports upper layer | Move import upward or use DI |
| Cycle detected | Mutual package imports | Extract shared code to lower layer |
| Naming violation | Missing naming convention | Rename class/field |
| System.exit() | Direct JVM termination | Use exception or callback |

### Suppressing Violations (Last Resort)

```java
@ArchTest
public static final ArchRule ruleWithAllowance =
    noClasses().that()
        .resideInAPackage("org.yawlfoundation.yawl.exception..")
        .should().dependOnClassesThat()
            .resideInAnyPackage("org.yawlfoundation.yawl.engine..")
        .allowEmptyShould(true)  // Allow if no matches
        .as("Description...");
```

---

## References

- **Source Code**: `test/org/yawlfoundation/yawl/quality/architecture/`
- **ArchUnit Documentation**: https://www.archunit.org/userguide/html/000_Index.html
- **YAWL Architecture**: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
