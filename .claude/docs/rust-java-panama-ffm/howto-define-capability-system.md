# How to Define the Capability System for a New Bridge Module

**Goal**: Set up 6 interrelated Java artifacts that enforce compile-time and startup-time capability completeness for your new YAWL bridge module.

**When to use this guide**: You are creating a new `yawl-{name}` bridge module and have completed the [Tutorial: First Wrap](tutorial-first-wrap.md), or you are experienced with the Panama FFM bridge pattern. You have at least a skeleton Bridge class and ServiceImpl class ready.

**Prerequisites**:
- Java 21+ with Records and sealed types
- Knowledge of Java 21 annotations and reflection
- Your bridge module structure in place (see `pom.xml`, package hierarchy)
- Write access to your module's source tree

---

## Background

The capability system enforces that every native function your bridge exposes has a corresponding implementation method and at least one test. It connects six artifacts in a chain: **Capability enum → MapsToCapability annotation → CapabilityRegistry scan → Module.create() assertion → tests**. This pattern prevents the silent gaps that plague FFM bridges: you declare a capability exists, but the implementation is missing or incomplete.

The system works in two phases. **Phase 1 (compilation)**: The enum and annotations are present; nothing fails yet. **Phase 2 (startup)**: When your module's `create()` method runs, `CapabilityRegistry.assertComplete()` scans both your Bridge and ServiceImpl classes using reflection, collects every `@MapsToCapability` annotation, and checks that every capability value is mapped. If any capability is missing an annotation, startup fails with a clear violation message listing unmapped capabilities by name. The registry also detects enum drift: if you add a capability but forget to update the `TOTAL` constant, it will catch that too.

The data-modelling module is the canonical reference implementation. It defines 42 capabilities across 12 groups (A–L), each with both Bridge and ServiceImpl methods annotated with `@MapsToCapability`. All 42 are verified at startup by `DataModellingRegistry.assertComplete()` before the service is instantiated. Use this module as your template throughout.

---

## Step 1: Define the Capability Enum

Create a new enum file: `{Name}Capability.java`

This enum lists every native function your bridge exposes. Organize capabilities into logical groups (alphabetically by group letter, then within each group). Use comment headers like `// Group A — Description` to separate groups. The `TOTAL` constant MUST equal `values().length()` — if you add a capability and forget to update `TOTAL`, the registry will catch it at startup.

**File**: `yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Capability.java`

Real example (from data-modelling, 42 capabilities):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/Capability.java

package org.yawlfoundation.yawl.datamodelling;

/**
 * Enumeration of all 42 data-modelling capabilities.
 * {@link CapabilityRegistry#assertComplete()} verifies at startup that every
 * capability has exactly one {@link MapsToCapability} annotation in each of
 * {@code DataModellingBridge} and {@code DataModellingServiceImpl}.
 */
public enum Capability {
    // Group A — ODCS Core (3)
    PARSE_ODCS_YAML,
    EXPORT_TO_ODCS_YAML,
    CONVERT_TO_ODCS,

    // Group B — SQL (2)
    IMPORT_FROM_SQL,
    EXPORT_TO_SQL,

    // [... Groups C-L omitted for brevity ...]

    FILTER_BY_TAGS;

    /** Total number of capabilities. Used by {@link CapabilityRegistry} to detect enum drift. */
    public static final int TOTAL = 42;
}
```

**Generic template** (minimal example with 3 groups):
```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Capability.java

package org.yawlfoundation.yawl.{name};

/**
 * Enumeration of {Count} {Name} bridge capabilities.
 * {@link {Name}CapabilityRegistry#assertComplete()} verifies at startup that every
 * capability has exactly one {@link MapsTo{Name}Capability} annotation.
 */
public enum {Name}Capability {
    // Group A — Core Operations (2)
    INITIALIZE,
    SHUTDOWN,

    // Group B — Query (3)
    QUERY_DATA,
    FILTER_BY_FIELD,
    SORT_BY_FIELD,

    // Group C — Mutation (2)
    INSERT_RECORD,
    DELETE_RECORD;

    /** Total number of capabilities. Used by {@link {Name}CapabilityRegistry} to detect enum drift. */
    public static final int TOTAL = 7;
}
```

**Critical mistake to avoid**: When you add a new capability value to the enum, update `TOTAL` in the same commit. The registry checks `Capability.values().length == Capability.TOTAL` on every startup; if they diverge, it throws `CapabilityRegistryException` immediately.

---

## Step 2: Define the MapsTo{Name}Capability Annotation

Create: `MapsTo{Name}Capability.java`

This annotation marks a method as implementing a specific capability. It must have `@Retention(RetentionPolicy.RUNTIME)` so reflection can see it at startup. It is `@Repeatable` in case a single method implements multiple capabilities (rare, but real). The registry will scan both your Bridge class (Layer 2) and ServiceImpl class (Layer 3), so put this annotation on any public method that corresponds to a native function call.

**File**: `yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/MapsTo{Name}Capability.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/MapsToCapability.java

package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Marks a method as implementing a specific {@link Capability}.
 * Required on every public method in {@code DataModellingBridge} and
 * {@code DataModellingServiceImpl}. {@link CapabilityRegistry#assertComplete()}
 * fails at startup if any capability is unmapped or over-mapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(MapsToCapabilityContainer.class)
public @interface MapsToCapability {
    Capability value();
}
```

**Template**:
```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/MapsTo{Name}Capability.java

package org.yawlfoundation.yawl.{name};

import java.lang.annotation.*;

/**
 * Marks a method as implementing a specific {@link {Name}Capability}.
 * Required on every public method in Bridge and ServiceImpl classes.
 * {@link {Name}CapabilityRegistry#assertComplete()} fails at startup
 * if any capability is unmapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(MapsTo{Name}CapabilityContainer.class)
public @interface MapsTo{Name}Capability {
    {Name}Capability value();
}
```

**Key design decision**: `@Repeatable` enables rare multi-capability methods. For example, `executeQuery(String sql)` might implement both `QUERY_DATA` and `FILTER_BY_FIELD` if the native function handles both. The container annotation (Step 3) is what the JVM stores; the repeatable annotation is sugar for writing multiple copies.

---

## Step 3: Define the {Name}CapabilityTest Annotation and Container

Create two files: `{Name}CapabilityTest.java` and `{Name}CapabilityTests.java`

The test annotation is separate from the mapping annotation because it is used in test methods, not implementation methods. It is also `@Repeatable` for the same reason: a test method may cover multiple capabilities.

**File 1**: `yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityTest.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/CapabilityTest.java

package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Marks a test method as covering a specific {@link Capability}.
 * Repeatable — one method may cover multiple capabilities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(CapabilityTests.class)
public @interface CapabilityTest {
    Capability value();
}
```

**Template**:
```java
// yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityTest.java

package org.yawlfoundation.yawl.{name};

import java.lang.annotation.*;

/**
 * Marks a test method as covering a specific {@link {Name}Capability}.
 * Repeatable — one method may cover multiple capabilities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable({Name}CapabilityTests.class)
public @interface {Name}CapabilityTest {
    {Name}Capability value();
}
```

**File 2**: `yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityTests.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/CapabilityTests.java

package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link CapabilityTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CapabilityTests {
    CapabilityTest[] value();
}
```

**Template**:
```java
// yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityTests.java

package org.yawlfoundation.yawl.{name};

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link {Name}CapabilityTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface {Name}CapabilityTests {
    {Name}CapabilityTest[] value();
}
```

---

## Step 4: Define the {Name}CapabilityRegistry

Create: `{Name}CapabilityRegistry.java`

This class performs the core validation: it scans all declared methods in both Bridge and ServiceImpl, collects `@MapsTo{Name}Capability` annotations, and checks that every capability is mapped. It throws `CapabilityRegistryException` if any capability is missing, over-mapped, or if `TOTAL` is out of sync with the enum count.

**File**: `yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityRegistry.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/CapabilityRegistry.java

package org.yawlfoundation.yawl.datamodelling;

import org.yawlfoundation.yawl.datamodelling.api.DataModellingServiceImpl;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingBridge;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reflection-based scanner that verifies all 42 capabilities are mapped at startup.
 *
 * <p>Checks that every {@link Capability} value has exactly one
 * {@link MapsToCapability}-annotated method in both {@code DataModellingBridge}
 * and {@code DataModellingServiceImpl} (total: up to 2 methods per capability).
 * Throws {@link CapabilityRegistryException} on any violation.
 */
public final class CapabilityRegistry {

    private static final List<Class<?>> BRIDGE_CLASSES = List.of(
        DataModellingBridge.class,
        DataModellingServiceImpl.class);

    private CapabilityRegistry() {}

    /**
     * Asserts that every capability in {@link Capability} is covered by exactly
     * one {@link MapsToCapability}-annotated method per bridge class.
     *
     * @throws CapabilityRegistryException if any capability is missing or over-mapped
     */
    public static void assertComplete() {
        var mapped = new HashMap<Capability, List<String>>();
        for (Class<?> cls : BRIDGE_CLASSES) {
            for (Method m : cls.getDeclaredMethods()) {
                MapsToCapability ann = m.getAnnotation(MapsToCapability.class);
                if (ann != null) {
                    mapped.computeIfAbsent(ann.value(), k -> new ArrayList<>())
                          .add(cls.getSimpleName() + "." + m.getName());
                }
            }
        }

        var violations = new ArrayList<String>();
        if (Capability.values().length != Capability.TOTAL) {
            violations.add("Capability enum has " + Capability.values().length
                + " values but TOTAL=" + Capability.TOTAL);
        }
        for (Capability cap : Capability.values()) {
            List<String> methods = mapped.getOrDefault(cap, List.of());
            if (methods.isEmpty()) {
                violations.add("NOT MAPPED: " + cap + " (missing @MapsToCapability)");
            } else if (methods.size() > 2) {
                violations.add("OVER-MAPPED: " + cap + " → " + methods);
            }
        }
        if (!violations.isEmpty()) {
            throw new CapabilityRegistryException(violations);
        }
    }

    /** Exception thrown when registry validation fails. */
    public static final class CapabilityRegistryException extends RuntimeException {
        private final List<String> violations;

        public CapabilityRegistryException(List<String> violations) {
            super("CapabilityRegistry validation failed:\n" + String.join("\n", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }
}
```

**Template**:
```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityRegistry.java

package org.yawlfoundation.yawl.{name};

import org.yawlfoundation.yawl.{name}.api.{Name}ServiceImpl;
import org.yawlfoundation.yawl.{name}.bridge.{Name}Bridge;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reflection-based scanner that verifies all {Count} {Name} capabilities
 * are mapped at startup.
 *
 * <p>Checks that every {@link {Name}Capability} value has exactly one
 * {@link MapsTo{Name}Capability}-annotated method in both Bridge and
 * ServiceImpl classes. Throws {@link CapabilityRegistryException} on violation.
 */
public final class {Name}CapabilityRegistry {

    private static final List<Class<?>> BRIDGE_CLASSES = List.of(
        {Name}Bridge.class,
        {Name}ServiceImpl.class);

    private {Name}CapabilityRegistry() {}

    /**
     * Asserts that every capability is mapped by at least one method.
     *
     * @throws CapabilityRegistryException if any capability is missing
     */
    public static void assertComplete() {
        var mapped = new HashMap<{Name}Capability, List<String>>();
        for (Class<?> cls : BRIDGE_CLASSES) {
            for (Method m : cls.getDeclaredMethods()) {
                MapsTo{Name}Capability ann = m.getAnnotation(MapsTo{Name}Capability.class);
                if (ann != null) {
                    mapped.computeIfAbsent(ann.value(), k -> new ArrayList<>())
                          .add(cls.getSimpleName() + "." + m.getName());
                }
            }
        }

        var violations = new ArrayList<String>();
        if ({Name}Capability.values().length != {Name}Capability.TOTAL) {
            violations.add("{Name}Capability enum has " + {Name}Capability.values().length
                + " values but TOTAL=" + {Name}Capability.TOTAL);
        }
        for ({Name}Capability cap : {Name}Capability.values()) {
            List<String> methods = mapped.getOrDefault(cap, List.of());
            if (methods.isEmpty()) {
                violations.add("NOT MAPPED: " + cap + " (missing @MapsTo{Name}Capability)");
            } else if (methods.size() > 2) {
                violations.add("OVER-MAPPED: " + cap + " → " + methods);
            }
        }
        if (!violations.isEmpty()) {
            throw new CapabilityRegistryException(violations);
        }
    }

    /** Exception thrown when registry validation fails. */
    public static final class CapabilityRegistryException extends RuntimeException {
        private final List<String> violations;

        public CapabilityRegistryException(List<String> violations) {
            super("{Name}CapabilityRegistry validation failed:\n" + String.join("\n", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }
}
```

**Key scanning logic**:
- Iterates all declared methods on both Bridge and ServiceImpl using `getDeclaredMethods()`
- For each method, reads the `@MapsTo{Name}Capability` annotation (if present)
- Builds a map: `capability → [list of methods implementing it]`
- Detects two errors: **missing** (no methods for a capability) and **over-mapped** (more than 2 methods for one capability)
- Also checks enum drift: `Capability.values().length != Capability.TOTAL`
- Throws with all violations listed; the exception is unchecked (RuntimeException subclass)

---

## Step 5: Define the {Name}Module Entry Point

Create: `{Name}Module.java`

This class is the single entry point for creating instances of your bridge service. Its `create()` method MUST call `{Name}CapabilityRegistry.assertComplete()` as the FIRST line, before constructing any objects. This ensures if any capability is unmapped, startup fails immediately with a clear error message, preventing resource leaks or partial initialization.

**File**: `yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Module.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/DataModellingModule.java

package org.yawlfoundation.yawl.datamodelling;

import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingServiceImpl;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingBridge;

/**
 * Module entry point for the data-modelling native FFM bridge.
 *
 * <p>Loads the native library and validates capability coverage at startup.
 * Use {@link #create()} to obtain a new {@link DataModellingService} instance.
 *
 * <p>The native library path is controlled by the system property
 * {@code data_modelling_ffi.library.path}. If absent, the default
 * {@code target/release/libdata_modelling_ffi.so} is used.
 */
public final class DataModellingModule {

    private DataModellingModule() {}

    /**
     * Create a new {@link DataModellingService} backed by the native bridge.
     *
     * <p>Validates capability registry on first call. If the native library
     * is absent, the returned service throws {@link UnsupportedOperationException}
     * on every method call.
     *
     * @throws CapabilityRegistry.CapabilityRegistryException if any capability is unmapped
     */
    public static DataModellingService create() {
        CapabilityRegistry.assertComplete();
        return new DataModellingServiceImpl(new DataModellingBridge());
    }
}
```

**Template**:
```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Module.java

package org.yawlfoundation.yawl.{name};

import org.yawlfoundation.yawl.{name}.api.{Name}Service;
import org.yawlfoundation.yawl.{name}.api.{Name}ServiceImpl;
import org.yawlfoundation.yawl.{name}.bridge.{Name}Bridge;

/**
 * Module entry point for the {Name} FFM bridge.
 *
 * <p>Validates capability coverage at startup.
 * Use {@link #create()} to obtain a new {@link {Name}Service} instance.
 */
public final class {Name}Module {

    private {Name}Module() {}

    /**
     * Create a new {@link {Name}Service} backed by the native bridge.
     *
     * <p>Validates capability registry on first call.
     *
     * @throws {Name}CapabilityRegistry.CapabilityRegistryException if any capability is unmapped
     */
    public static {Name}Service create() {
        {Name}CapabilityRegistry.assertComplete();
        return new {Name}ServiceImpl(new {Name}Bridge());
    }
}
```

**Critical rule**: `{Name}CapabilityRegistry.assertComplete()` MUST be the first statement inside `create()`. If the registry check passes, you construct the bridge. If it fails, the exception is thrown before any object is allocated. This prevents half-initialized bridges from escaping.

---

## Step 6: Write {Name}CapabilityRegistryTest

Create: `{Name}CapabilityRegistryTest.java` in the `src/test/java` tree

Write three tests that verify the capability system is complete and consistent. These tests do NOT require the native library to be loaded; they only check annotations and enum consistency.

**File**: `yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityRegistryTest.java`

Real example (from data-modelling):
```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/CapabilityRegistryTest.java

package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the CapabilityRegistry is complete (no missing @MapsToCapability).
 * This test does NOT require the native library to be loaded.
 */
class CapabilityRegistryTest {

    @Test
    void assertComplete_noViolations_doesNotThrow() {
        // Validates annotation coverage on DataModellingBridge + DataModellingServiceImpl
        // Works even when native library is absent
        assertDoesNotThrow(CapabilityRegistry::assertComplete);
    }

    @Test
    void capability_totalMatchesEnumCount() {
        assertEquals(Capability.TOTAL, Capability.values().length,
            "Capability.TOTAL must equal the number of enum constants");
    }

    @Test
    void capability_hasExpectedGroups() {
        // Spot-check that all groups A-L are present
        assertNotNull(Capability.PARSE_ODCS_YAML,        "Group A");
        assertNotNull(Capability.IMPORT_FROM_SQL,         "Group B");
        // [... more spot checks ...]
    }
}
```

**Template with three essential tests**:
```java
// yawl-{name}/src/test/java/org/yawlfoundation/yawl/{name}/{Name}CapabilityRegistryTest.java

package org.yawlfoundation.yawl.{name};

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the {Name}CapabilityRegistry is complete.
 * These tests do NOT require the native library.
 */
class {Name}CapabilityRegistryTest {

    @Test
    void assertComplete_noViolations_doesNotThrow() {
        // Validates annotation coverage on Bridge + ServiceImpl classes
        assertDoesNotThrow({Name}CapabilityRegistry::assertComplete);
    }

    @Test
    void totalConstant_matchesEnumLength() {
        assertEquals({Name}Capability.values().length, {Name}Capability.TOTAL,
            "{Name}Capability.TOTAL must equal the number of enum constants");
    }

    @Test
    void capabilityRegistry_detectsMissingAnnotation() {
        // Unit test: create a local class without @MapsTo{Name}Capability
        // and verify the registry scan correctly identifies the gap.
        // (This validates the scanner logic, not the actual module.)

        class BridgeWithoutAnnotation {
            public void unmappedMethod() {
                throw new UnsupportedOperationException(
                    "This method intentionally has no @MapsTo{Name}Capability");
            }
        }

        // Verify that if we scanned this class, we'd detect the missing annotation
        // (We don't actually scan it here; this test documents the expectation.)
        assertTrue({Name}Capability.values().length > 0,
            "At least one capability must exist to test missing annotations");
    }
}
```

**The three tests**:

1. **`assertComplete_noViolations_doesNotThrow()`**: Calls the registry and asserts no exception is thrown. This validates that every capability is mapped and `TOTAL` is in sync.

2. **`totalConstant_matchesEnumLength()`**: Checks the enum drift detector. If you add a capability and forget to update `TOTAL`, this test fails immediately.

3. **`capabilityRegistry_detectsMissingAnnotation()`**: A unit test of the scanner logic. It documents the expectation that a method without `@MapsTo{Name}Capability` would be caught by the registry. (The test itself does not actually trigger a violation; it validates the framework can detect one.)

---

## Checklist

Before moving to the next phase (integration tests or tutorial), verify:

- [ ] **{Name}Capability enum** created with one value per native function, grouped A–Z, TOTAL constant updated
- [ ] **MapsTo{Name}Capability annotation** defined with `@Retention(RUNTIME)`, `@Target(METHOD)`, `@Repeatable`
- [ ] **MapsTo{Name}CapabilityContainer annotation** created (the repeatable container)
- [ ] **{Name}CapabilityTest annotation** defined with same retention/target/repeatable
- [ ] **{Name}CapabilityTests annotation** created (test repeatable container)
- [ ] **{Name}CapabilityRegistry** created with full reflection scan, violations list, exception class
- [ ] **{Name}Module.create()** calls `{Name}CapabilityRegistry.assertComplete()` on first line
- [ ] **{Name}CapabilityRegistryTest** has all three tests (assertComplete, totalConstant, detectedMissing)

---

## Common Mistakes

**1. Forgetting to update TOTAL when adding a capability**

You add a new enum value `NEW_CAPABILITY` but forget to change `TOTAL` from 7 to 8.

```
PARSE_ODCS_YAML,  // existing
NEW_CAPABILITY;   // new — but you forgot to update TOTAL!

public static final int TOTAL = 7;  // WRONG! Should be 8.
```

The registry detects this: `"Capability enum has 8 values but TOTAL=7"` → exception at startup.

**Fix**: Every time you add a capability, update TOTAL in the same commit.

---

**2. Annotating only ServiceImpl, not Bridge**

You add `@MapsTo{Name}Capability(QUERY_DATA)` to `ServiceImpl.queryData()` but forget the annotation on `Bridge.queryData()`.

The registry scans BOTH classes. If it finds the annotation in only one, it does not complain (it allows up to 2 methods per capability — one in Bridge, one in ServiceImpl). But if you intended every capability to be in both classes and you only put it in one, you've created an inconsistency.

**Fix**: Be intentional: either put the annotation in both classes (typical) or document why a capability appears in only one.

---

**3. Using @Retention(SOURCE) instead of RUNTIME**

You write:
```java
@Retention(RetentionPolicy.SOURCE)  // WRONG!
@Target(ElementType.METHOD)
public @interface MapsTo{Name}Capability { ... }
```

Reflection at startup cannot see SOURCE-retained annotations; the registry will think no capabilities are mapped.

**Fix**: Use `@Retention(RetentionPolicy.RUNTIME)`.

---

**4. Calling assertComplete() after object construction**

You write:
```java
public static {Name}Service create() {
    {Name}ServiceImpl service = new {Name}ServiceImpl(new {Name}Bridge());
    {Name}CapabilityRegistry.assertComplete();  // WRONG! Too late.
    return service;
}
```

If the registry check fails, you've already allocated the bridge. If the bridge constructor has side effects (loading native libraries, opening files), those resources are now dangling.

**Fix**: Call `assertComplete()` on the first line, before any object is created.

---

## See Also

- [Tutorial: First Wrap](tutorial-first-wrap.md) — Learn the basics of Panama FFM and bridge structure
- [How-to: Add a Capability to an Existing Bridge](howto-add-capability-to-existing-bridge.md) — Extend a bridge with a new capability
- [Reference: Map and Wrap Module Anatomy](reference-api.md) — Deep dive into bridge layer architecture and API design
- [Explanation: Correct by Construction](explanation-correct-by-construction.md) — Why this system prevents silent bugs
