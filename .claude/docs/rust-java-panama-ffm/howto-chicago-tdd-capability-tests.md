---
title: "How-to Guide: Chicago TDD for Bridge Capability Tests"
goal: Write test coverage for FFM bridge modules that skips gracefully without the native library and tracks capability coverage
audience: Engineers adding tests to existing working bridge modules
prerequisites: |
  - Completed the tutorial: howto-define-capability-system.md
  - Working FFM bridge module (DataModellingModule or equivalent)
  - JUnit 5 (Jupiter) installed
  - Familiarity with Java 21+ records and assertions
---

# Chicago TDD for Bridge Capability Tests

## Goal

Learn to write test coverage for an FFM bridge module that automatically skips when the native library is absent, meets Chicago TDD standards (no mocks, real integrations), and tracks which capabilities are covered by tests.

## When to Use This Guide

- **You are writing** capability tests for a working bridge module
- **You need tests to** automatically skip in CI when the native binary is unavailable
- **You want to verify** that all bridge capabilities have test coverage
- **You follow** Chicago TDD (real integrations only, no mocking)

## Prerequisites

1. Read howto-define-capability-system.md (capability annotations and registry)
2. Working FFM bridge module with native library loading
3. Java 21+ with JUnit 5 (Jupiter)
4. Test fixtures (real YAML, SQL, JSON files under `src/test/resources/`)

---

## Background

### Why Bridge Tests Are Different

Traditional unit tests exercise Java code with pure functions and mocked dependencies. Bridge tests are fundamentally different because they cross a boundary into native code (Rust, C, etc.). Three implications:

1. **Native library dependency**: Tests must skip gracefully if the compiled `.so`/`.dll` is absent (e.g., in CI where it's not built). A failed assumption via `assumeTrue()` is not a failure; it signals "skip this test."

2. **Cannot mock the bridge**: Chicago TDD forbids mocking. You cannot stub a native function call — the entire point is testing through to real Rust code. If the library is absent, the test must skip, not mock.

3. **Thread safety across boundaries**: Native code may have different concurrency guarantees. Virtual-thread tests verify the bridge handles concurrent calls without corruption (race conditions, memory issues).

### Chicago TDD: Real Integrations, No Mocks

Chicago TDD (Detroit School) emphasizes testing through real implementations. For bridge modules:

- **Test the full pipeline**: YAML → parse → WorkspaceModel → export → SQL (all layers, no mocks)
- **Structural assertions**: Assert that output contains expected content (`assertTrue(sql.contains("CREATE TABLE"))`) not just existence (`assertNotNull(sql)`)
- **Exception contract**: Test that invalid input throws the declared exception type (e.g., `DataModellingException`)
- **Virtual-thread safety**: Concurrent tests prove thread safety without introducing locks that would break virtual-thread benefits

### How `assumeTrue()` Enables CI Execution Without the Native Binary

When a test class calls `assumeTrue(library.isPresent(), ...)` in `@BeforeAll`, all test methods in that class are skipped if the library is absent. JUnit reports this as "0 executions, N assumptions violated" — a clean success (exit code 0).

Why not `@DisabledIf`? Because `@DisabledIf` permanently disables tests in any build. With `assumeTrue()`, the test runs as soon as the library becomes available (e.g., when you build the native component locally).

---

## Section 1: Test Class Skeleton

Every capability test class follows this structure:

```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/SqlCapabilityTest.java

package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.SqlDialect;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

@Tag("capability")                          // enables: mvn test -Dgroups=capability
@DisplayName("SQL generation capabilities")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // allows @BeforeAll/@AfterAll on non-static methods
class SqlCapabilityTest {

    private DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(
            data_modelling_ffi_h.LIBRARY.isPresent(),
            "Skipping: native library not available. " +
            "Build with: bash scripts/build-data-modelling-ffi.sh"
        );
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    // Test methods go here...
}
```

### Annotation Explained

| Annotation | Purpose | Notes |
|-----------|---------|-------|
| `@Tag("capability")` | Allows selective test execution | Run with: `mvn test -Dgroups=capability` |
| `@TestInstance(Lifecycle.PER_CLASS)` | Enables non-static `@BeforeAll`/`@AfterAll` | Required when you store instance state (like `service`) |
| `@BeforeAll` inside `@BeforeAll` | Runs once before any test in the class | If `assumeTrue()` fails, **all tests in the class skip**. This is what you want: fail fast. |
| `assumeTrue(condition, message)` | Skip test if condition is false | Much better than `@DisabledIf` — tests run again when the condition changes. |
| `@AfterAll` | Cleanup runs once after all tests | Close resources here; guaranteed to run even if a test fails. |

**Critical:** Use `@BeforeAll assumeTrue(...)` not `@BeforeEach assumeTrue(...)`. With `@BeforeEach`, each test shows "0 executions, 1 assumption violated" — confusing in CI. With `@BeforeAll`, the entire class skips cleanly.

---

## Section 2: Writing Capability-Tagged Tests

Every test method must declare which capability it covers:

```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/SqlCapabilityTest.java

@Test
@CapabilityTest(EXPORT_TO_SQL)
@DisplayName("PostgreSQL export contains CREATE TABLE statement")
void postgresDialect_containsCreateTable() throws Exception {
    // Arrange: Parse real SQL fixture
    WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);

    // Act: Export back to SQL
    String sql = service.exportToSql(ws, SqlDialect.POSTGRESQL);

    // Assert: Check structural properties (no mocks, real content)
    assertNotNull(sql, "Exported SQL must not be null");
    assertFalse(sql.isBlank(), "Exported SQL must not be blank");
    assertTrue(sql.toUpperCase().contains("CREATE TABLE"),
        "PostgreSQL export must contain CREATE TABLE statement");
}
```

### Structural vs. Vacuous Assertions

This table shows which assertions count toward Chicago TDD standards:

| Good (Structural) | Bad (Vacuous) | Why |
|---|---|---|
| `assertFalse(result.isBlank())` | `assertNotNull(result)` | `isBlank()` tests content, not existence |
| `assertEquals(2, ws.tables().size())` | `assertTrue(ws.tables() != null)` | Cardinality is structural, null-check is vacuous |
| `assertTrue(result.contains("CREATE TABLE"))` | `assertDoesNotThrow(() -> service.method())` | Content assertion is structural; "doesn't throw" adds no information |
| `assertEquals("orders", table.name())` | `assertTrue(true)` | Exact match is structural; tautology is vacuous |
| `assertThrows(DataModellingException.class, () -> ...)` | `assertNotNull(service)` | Exception contract is structural; service existence is vacuous |

**Rule**: Every assertion must verify a property of the output, not just that it exists.

### Multi-Capability Integration Tests

Some tests verify multiple capabilities in a pipeline. Use `@CapabilityTest` multiple times:

```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/SqlCapabilityTest.java

@Test
@CapabilityTest(IMPORT_FROM_SQL)
@CapabilityTest(EXPORT_TO_SQL)
@CapabilityTest(EXPORT_TO_AVRO)
@DisplayName("pipeline: SQL → Workspace → Avro round-trip preserves schema")
void pipeline_sqlToWorkspaceToAvro_schemasConsistent() throws Exception {
    // Step 1: Parse SQL (IMPORT_FROM_SQL)
    WorkspaceModel ws1 = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
    assertEquals(2, ws1.tables().size());

    // Step 2: Export to Avro (EXPORT_TO_AVRO)
    String avro = service.exportToAvro(ws1);
    assertFalse(avro.isBlank());

    // Step 3: Cross-check: Avro field count should match ODCS column count
    int odcsColumns = ws1.tables().stream()
        .mapToInt(t -> t.columns().size()).sum();
    var avroSchema = parseAvroSchema(avro);
    assertEquals(odcsColumns, avroSchema.fields().size(),
        "Avro field count must match total ODCS column count");
}
```

The `@CapabilityTest` annotation is `@Repeatable`, so you can attach it multiple times. The capability registry verifies that every capability has at least one test.

---

## Section 3: Test Fixtures

Test fixtures are real data files (YAML, SQL, JSON) loaded once at class initialization:

```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/test/DataModellingTestFixtures.java

package org.yawlfoundation.yawl.datamodelling.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared test fixture loader for all data-modelling capability tests.
 * Resources are loaded from the classpath relative to this class's package:
 * {@code org/yawlfoundation/yawl/datamodelling/test/fixtures/}.
 */
public final class DataModellingTestFixtures {

    private DataModellingTestFixtures() {}

    /** Load a fixture file from the fixtures/ directory. */
    public static String load(String filename) {
        try (InputStream in = DataModellingTestFixtures.class
                .getResourceAsStream("fixtures/" + filename)) {
            if (in == null) {
                throw new RuntimeException("Missing test fixture: fixtures/" + filename);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load fixture: " + filename, e);
        }
    }

    // Group A — ODCS
    public static final String ODCS_YAML        = load("odcs-orders.yaml");

    // Group B — SQL
    public static final String SQL_POSTGRES      = load("orders-postgres.sql");
    public static final String SQL_SQLITE        = load("orders-sqlite.sql");

    // Group C — Avro Schema
    public static final String AVRO_SCHEMA       = load("orders.avsc");
}
```

### Fixture Directory Structure

```
src/test/resources/
  org/yawlfoundation/yawl/datamodelling/test/fixtures/
    odcs-orders.yaml
    orders-postgres.sql
    orders-sqlite.sql
    orders.avsc
    orders-decision.dmn
    orders-api.yaml
```

### Why Static Final Constants

- **Loaded once at class init**: Expensive I/O happens once, not per test
- **Fail loudly if missing**: `RuntimeException` at class init reveals missing fixtures immediately (not in test output)
- **Reusable across tests**: Reference `SQL_POSTGRES` in multiple test methods; constant promotes DRY principle

Never use inline string literals for fixtures:

```java
// BAD: Inline YAML breaks on whitespace changes
void test_parseYaml() throws Exception {
    var ws = service.parseOdcsYaml("tables:\n  - name: orders\n    columns: [...]");
}

// GOOD: Use fixture constant
void test_parseYaml() throws Exception {
    var ws = service.parseOdcsYaml(ODCS_YAML);
}
```

---

## Section 4: Virtual-Thread Concurrency Tests

Bridge modules must prove thread safety. Use Java 21+ virtual threads:

```java
// yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/DataModellingIntegrationTest.java

package org.yawlfoundation.yawl.datamodelling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Test
@CapabilityTest(Capability.PARSE_ODCS_YAML)
@DisplayName("parseOdcsYaml is safe under concurrent virtual-thread calls")
void parseOdcsYaml_concurrent100Threads_noCorruption() throws Exception {
    assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
        "Skipping: native library not available");

    final int threadCount = 100;
    var latch = new CountDownLatch(threadCount);
    var errors = new AtomicInteger(0);
    var tableCountMismatches = new AtomicInteger(0);
    List<Thread> threads = new ArrayList<>(threadCount);

    try (var svc = DataModellingModule.create()) {
        IntStream.range(0, threadCount).forEach(i -> {
            var t = Thread.ofVirtual().start(() -> {
                try {
                    var ws = svc.parseOdcsYaml(ODCS_YAML);
                    if (ws.tables().size() != 2) {
                        tableCountMismatches.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            threads.add(t);
        });

        latch.await();
        assertEquals(0, errors.get(), "Unexpected errors in virtual threads");
        assertEquals(0, tableCountMismatches.get(),
            "Table count mismatch detected across threads");
    }
}
```

### Why Virtual Threads

- **100 threads is cheap**: Virtual threads cost ~100 bytes per thread vs. ~1 MB for platform threads
- **Tests thread safety**: If the bridge has data races or unsafe pointer access, concurrent calls reveal them
- **Real-world workload**: Production YAWL engines spawn thousands of virtual threads; tests must verify safety at that scale

### Key Assertions

- `errors.get() == 0`: No uncaught exceptions (race condition symptom)
- `tableCountMismatches.get() == 0`: Output is consistent across threads (memory corruption symptom)

---

## Section 5: The Capability Registry Test

The registry test verifies coverage without requiring the native library. It runs in every CI build:

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
        // Validates @MapsToCapability annotation coverage
        // Works even when native library is absent
        assertDoesNotThrow(CapabilityRegistry::assertComplete);
    }

    @Test
    void capability_totalMatchesEnumCount() {
        assertEquals(Capability.TOTAL, Capability.values().length,
            "Capability.TOTAL must equal the number of enum constants. " +
            "Update TOTAL when adding new capabilities.");
    }

    @Test
    void capability_hasExpectedGroups() {
        // Spot-check that all groups A-L are present
        assertNotNull(Capability.PARSE_ODCS_YAML,        "Group A");
        assertNotNull(Capability.IMPORT_FROM_SQL,         "Group B");
        assertNotNull(Capability.EXPORT_TO_AVRO,          "Group C");
        assertNotNull(Capability.VALIDATE_ODPS,           "Group D");
        // ... more groups
    }
}
```

**Important**: The registry test requires zero native library dependencies. It verifies:

1. **Annotation coverage**: Every method in the bridge has `@MapsToCapability` (registry completeness)
2. **Enum consistency**: `Capability.TOTAL` matches the actual enum size (detects silent enum drift)
3. **Group presence**: All expected capability groups exist (no missing groups)

Run this test to ensure test coverage is complete:

```bash
mvn test -pl yawl-data-modelling -Dtest=CapabilityRegistryTest
```

---

## Section 6: Running the Tests

### Full Capability Test Suite (Requires Native Library)

```bash
# Run all capability tests (skips if library absent)
mvn test -pl yawl-data-modelling -Dgroups=capability

# Run a single capability test class
mvn test -pl yawl-data-modelling -Dtest=SqlCapabilityTest

# Run with verbose output (shows skipped tests)
mvn test -pl yawl-data-modelling -Dgroups=capability -X
```

### Registry Test Only (No Native Library Required)

```bash
# Verify capability coverage (runs in CI)
mvn test -pl yawl-data-modelling -Dtest=CapabilityRegistryTest
```

### All Tests (Mixed: Registry + Capability, Capability Skips if Library Absent)

```bash
# Run all tests in the module
mvn test -pl yawl-data-modelling

# CI output:
# CapabilityRegistryTest ...................... PASSED
# SqlCapabilityTest ........................... SKIPPED (0 executions, N assumptions violated)
# OdcsCapabilityTest .......................... SKIPPED (0 executions, N assumptions violated)
# ...
# Tests: 3 passed, 0 failed, N skipped — exit code 0 (SUCCESS)
```

### With Native Library Present

```bash
# Build the native library first
bash scripts/build-data-modelling-ffi.sh

# Now capability tests run
mvn test -pl yawl-data-modelling -Dgroups=capability

# Output:
# SqlCapabilityTest ........................... PASSED
# OdcsCapabilityTest .......................... PASSED
# ...
```

---

## Section 7: Pre-Completion Checklist

Before declaring tests complete, verify all items:

- [ ] Every test method has `@CapabilityTest(CAPABILITY_VALUE)` annotation
- [ ] `@BeforeAll` contains `assumeTrue(LIBRARY.isPresent(), ...)` (not `@BeforeEach`)
- [ ] No `assertTrue(true)`, no `assertNotNull(result)` as the only assertion
- [ ] No mocks, no stubs — all service calls go through real bridge (via `DataModellingModule.create()`)
- [ ] Fixtures are static final constants loaded once, not inline string literals
- [ ] `@AfterAll` closes the service in a try-finally or `.close()` pattern
- [ ] `CapabilityRegistryTest` has all 3 mandatory tests (assertComplete, totalMatches, expectedGroups)
- [ ] `mvn test -pl yawl-data-modelling` passes (registry tests pass, capability tests skip without library)
- [ ] `mvn test -pl yawl-data-modelling -Dgroups=capability` runs (with library present)
- [ ] Concurrent virtual-thread tests exist for thread-safety validation

---

## Common Mistakes

### `@BeforeEach assumeTrue(...)`

```java
// BAD: Each test shows "0 executions, 1 assumption violated"
@BeforeEach
void setup() {
    assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(), ...);
    service = DataModellingModule.create();
}

// Output: Test_1 ............ 0 executions, 1 assumption violated
//         Test_2 ............ 0 executions, 1 assumption violated
//         Confusing in CI logs — looks like test file parsing error
```

**Fix**: Use `@BeforeAll` instead. All tests skip cleanly if the assumption fails.

### Inline Fixture Strings

```java
// BAD: Test breaks if YAML format changes slightly
void test_parseOdcs() throws Exception {
    var ws = service.parseOdcsYaml("tables:\n  - name: orders");
}

// GOOD: Use constant fixture loaded once
void test_parseOdcs() throws Exception {
    var ws = service.parseOdcsYaml(ODCS_YAML);
}
```

### Vacuous Assertions

```java
// BAD: Only checks existence, not content
void test_generateSql() throws Exception {
    String sql = service.generateSql(ws);
    assertNotNull(sql);  // ← vacuous, add more assertions
}

// GOOD: Check structural properties
void test_generateSql() throws Exception {
    String sql = service.generateSql(ws);
    assertFalse(sql.isBlank());
    assertTrue(sql.toUpperCase().contains("CREATE TABLE"));
    assertTrue(sql.contains("orders"));
}
```

### `@Disabled` Instead of `assumeTrue()`

```java
// BAD: Permanently disables test, no way to re-enable without code change
@Test
@Disabled("Native library not available")
void test_parseOdcs() throws Exception { ... }

// GOOD: Conditionally skips, auto-runs when library is available
@Test
void test_parseOdcs() throws Exception {
    assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(), ...);
    ...
}
```

### Expensive Initialization Per Test

```java
// BAD: Each test creates a new service (expensive)
@BeforeEach
void setup() {
    service = DataModellingModule.create();
}

// GOOD: Create service once per class
@BeforeAll
void setup() {
    assumeTrue(...);
    service = DataModellingModule.create();
}
```

### Missing Exception Contract Tests

```java
// BAD: No test for invalid input
void test_parseOdcs_valid() throws Exception {
    var ws = service.parseOdcsYaml(ODCS_YAML);
    assertNotNull(ws);
}

// GOOD: Test both valid and invalid paths
void test_parseOdcs_valid() throws Exception {
    var ws = service.parseOdcsYaml(ODCS_YAML);
    assertNotNull(ws);
}

void test_parseOdcs_invalidYaml_throwsDataModellingException() {
    assertThrows(DataModellingException.class,
        () -> service.parseOdcsYaml("not: valid: yaml:::"),
        "Parsing invalid YAML must throw DataModellingException");
}
```

---

## See Also

- **Tutorial**: howto-define-capability-system.md (capability annotations, @MapsToCapability, registry)
- **Testing Rules**: `.claude/rules/testing/chicago-tdd.md` (JUnit 5, coverage targets, anti-patterns)
- **Java 25 Conventions**: `.claude/rules/java25/modern-java.md` (virtual threads, records, sealed classes)
- **Bridge Architecture**: How the FFM Panama API boundary works (see JavaDoc on DataModellingBridge)

---

## Summary

1. **Test class skeleton**: `@Tag("capability")`, `@TestInstance(PER_CLASS)`, `@BeforeAll assumeTrue(...)`, `@AfterAll close()`
2. **Each test method**: Single `@CapabilityTest(CAPABILITY)` annotation, real service call, structural assertions
3. **Fixtures**: Static final constants loaded once, fail loudly if missing, never inline strings
4. **Concurrency**: Virtual-thread tests verify thread safety without mocking the bridge
5. **Registry test**: Validates coverage without requiring the native library (runs in every CI build)
6. **CI behavior**: Capability tests skip when library is absent (exit code 0); registry tests always pass
7. **Verification**: Check the pre-completion checklist before submitting

---

**Document Version**: 1.0 | **Last Updated**: 2026-03-01 | **Framework**: JUnit 5 (Jupiter), Java 21+, Panama FFM
