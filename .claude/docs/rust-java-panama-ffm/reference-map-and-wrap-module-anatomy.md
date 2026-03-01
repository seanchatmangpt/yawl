# Reference: Map-and-Wrap Module Anatomy — JSON-Based FFM Bridge Structure

**Purpose**: Canonical directory layout, file naming conventions, and mandatory code elements for any JSON-based Rust→Java Panama FFM bridge module in YAWL.

**How to use this document**: Alongside a tutorial or how-to guide, not instead of it. When building a new module (e.g., `yawl-graph`, `yawl-workflow-trace`), use this reference to verify your file structure and naming match the pattern established in `yawl-data-modelling`.

---

## 1. Module Directory Tree

Every file listed below is mandatory. The tree structure is exact; deviations require justification in architecture review.

```
yawl-{name}/
├── pom.xml                          # Maven module descriptor
├── src/
│   ├── main/java/org/yawlfoundation/yawl/{name}/
│   │   ├── generated/               # LAYER 1 — Hand-written Panama FFM bindings
│   │   │   ├── {name_snake}_ffi_h.java      # Library loading, MethodHandles, struct layouts, CbC sizeof probes
│   │   │   ├── {Name}Result_h.java          # VarHandle accessors for {Name}Result struct (data + error)
│   │   │   ├── {Name}VoidResult_h.java      # VarHandle accessor for void-result struct (error-only)
│   │   │   └── package-info.java            # "LAYER 1 — Hand-written Panama FFM bindings"
│   │   ├── bridge/                  # LAYER 2 — Type-safe bridge (internal, no public API)
│   │   │   ├── {Name}Bridge.java    # Arena.ofShared lifetime; per-call Arena.ofConfined; @MapsToCapability on each method
│   │   │   └── package-info.java    # "LAYER 2 — Typed Java bridge (no Panama types leaked)"
│   │   ├── api/                     # LAYER 3 — Public service API (pure Java, no Panama types)
│   │   │   ├── {Name}Service.java   # Interface: typed domain methods, AutoCloseable
│   │   │   ├── {Name}ServiceImpl.java  # Implementation: @MapsToCapability on each override, Json.encode/decode
│   │   │   ├── Json.java            # encode(Object)/decode(String,Class<T>) via Jackson
│   │   │   └── package-info.java    # "LAYER 3 — Pure Java service API"
│   │   ├── model/                   # Domain model records and enums (immutable, no Panama types)
│   │   │   ├── *.java               # Record types only; @JsonAutoDetect or @JsonProperty on fields
│   │   │   └── package-info.java    # "Domain model records (immutable)"
│   │   ├── {Name}Capability.java                # Enum: one value per Rust function; TOTAL constant
│   │   ├── MapsTo{Name}Capability.java         # @Retention(RUNTIME) @Target(METHOD) @Repeatable annotation
│   │   ├── {Name}CapabilityTest.java           # Container annotation for @Repeatable {Name}CapabilityTest[]
│   │   ├── {Name}CapabilityTests.java          # Plural container for multi-capability test classes
│   │   ├── {Name}CapabilityRegistry.java       # Reflection scanner; assertComplete(); startup gate
│   │   ├── {Name}Module.java                   # Static factory: assertComplete() then create service
│   │   └── {Name}Exception.java                # Unchecked RuntimeException for all bridge errors
│   └── test/java/org/yawlfoundation/yawl/{name}/
│       ├── {Name}CapabilityRegistryTest.java       # 3 tests: enum drift, unmapped, over-mapped
│       ├── bridge/{Name}BridgeTest.java            # Layer 2 structural tests (assumeTrue, library presence)
│       ├── {CapsGroup}CapabilityTest.java          # Tests per capability group (assumeTrue, @CapabilityTest)
│       ├── {Name}IntegrationTest.java              # Multi-capability pipeline tests
│       └── test/{Name}TestFixtures.java            # Static fixture constants
└── src/test/resources/org/yawlfoundation/yawl/{name}/test/fixtures/
    └── *.yaml, *.sql, *.json                       # Test input/output fixtures
```

---

## 2. Naming Conventions

Apply the naming convention row that matches your module's abbreviation.

| Element | Pattern | yawl-data-modelling Example | yawl-rust4pm Example | yawl-graph Example |
|---------|---------|---------------------------|----------------------|-------------------|
| Maven artifact | `yawl-{name}` | `yawl-data-modelling` | `yawl-rust4pm` | `yawl-graph` |
| Java root package | `org.yawlfoundation.yawl.{name}` | `org.yawlfoundation.yawl.datamodelling` | `org.yawlfoundation.yawl.rust4pm` | `org.yawlfoundation.yawl.graph` |
| Library name (C) | `lib{name_snake}_ffi.so` | `libdata_modelling_ffi.so` | `librust4pm_ffi.so` | `libgraph_ffi.so` |
| FFM class (Layer 1) | `{name_snake}_ffi_h` | `data_modelling_ffi_h` | `rust4pm_h` | `graph_ffi_h` |
| Result struct accessor | `{Name}Result_h` | `DmResult_h` | `ParseResult_h` | `GraphResult_h` |
| Void result accessor | `{Name}VoidResult_h` | `DmVoidResult_h` | `ParseVoidResult_h` | `GraphVoidResult_h` |
| Bridge class (Layer 2) | `{Name}Bridge` | `DataModellingBridge` | `Rust4pmBridge` | `GraphBridge` |
| Service interface (Layer 3) | `{Name}Service` | `DataModellingService` | `Rust4pmService` | `GraphService` |
| Service impl (Layer 3) | `{Name}ServiceImpl` | `DataModellingServiceImpl` | `Rust4pmServiceImpl` | `GraphServiceImpl` |
| JSON helper | `Json` (singular, always) | `Json` | `Json` | `Json` |
| Capability enum | `{Name}Capability` | `DataModellingCapability` (or `Capability` if unambiguous) | `Rust4pmCapability` | `GraphCapability` |
| Impl annotation | `MapsTo{Name}Capability` | `MapsToDataModellingCapability` (or `MapsToCapability`) | `MapsToRust4pmCapability` | `MapsToGraphCapability` |
| Test annotation | `{Name}CapabilityTest` | `DataModellingCapabilityTest` (or `CapabilityTest`) | `Rust4pmCapabilityTest` | `GraphCapabilityTest` |
| Registry | `{Name}CapabilityRegistry` | `DataModellingCapabilityRegistry` | `Rust4pmCapabilityRegistry` | `GraphCapabilityRegistry` |
| Module factory | `{Name}Module` | `DataModellingModule` | `Rust4pmModule` | `GraphModule` |
| Exception base | `{Name}Exception` | `DataModellingException` | `ParseException` | `GraphException` |
| System property | `{name_snake}_ffi.library.path` | `data_modelling_ffi.library.path` | `rust4pm_ffi.library.path` | `graph_ffi.library.path` |
| C function prefix | `{abbrev}_` | `dm_` | `r4pm_` | `gr_` |

**Rule**: If your module name is short (4-6 chars), you may abbreviate it in class names (e.g., `Dm` for DataModelling, `Gr` for Graph). If long (>8 chars), use the full name or a common abbreviation from the team.

---

## 3. Maven pom.xml — Mandatory Configuration

Extract from `/home/user/yawl/yawl-data-modelling/pom.xml`:

```xml
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-GA</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>yawl-{name}</artifactId>
    <packaging>jar</packaging>
    <name>YAWL {Name} — Native FFM Bridge</name>
    <description>
        Java 25 Panama FFM bridge to lib{name_snake}_ffi.so.
        Three-layer architecture: Layer 1 (FFM stubs), Layer 2 (typed bridge),
        Layer 3 (domain service API).
    </description>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
                <executions>
                    <execution>
                        <id>default-testCompile</id>
                        <configuration>
                            <compilerArgs>
                                <arg>--enable-preview</arg>
                            </compilerArgs>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>--enable-preview --enable-native-access=ALL-UNNAMED</argLine>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Critical flags**:
- `maven-compiler-plugin`: `--enable-preview` (both main and test compilation)
- `maven-surefire-plugin`: `--enable-preview --enable-native-access=ALL-UNNAMED` (no other args needed)
- Do not add `-XX:+UseCompactObjectHeaders` here — it's set globally in parent pom

---

## 4. Layer 1: {name_snake}_ffi_h.java Mandatory Elements

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java`

Every element in the table below must exist in your Layer 1 class.

| Element | Type | Purpose | Example Pattern |
|---------|------|---------|-----------------|
| `LIBRARY` | `Optional<SymbolLookup>` | Library presence check | `Optional.ofNullable(tryLoad())` |
| `LIB_ARENA` | `Arena` | Library-lifetime arena (shared) | `Arena.ofShared()` or `null` |
| `LINKER` | `Linker` | FFM linker instance | `Linker.nativeLinker()` |
| `{Name}Result` layout | `StructLayout` | 16-byte result struct (data + error pointers) | `structLayout(ADDRESS, ADDRESS)` |
| `{Name}VoidResult` layout | `StructLayout` | 8-byte void-result struct (error pointer only) | `structLayout(ADDRESS)` |
| Probe MethodHandles | `static final MethodHandle` | sizeof/offsetof functions from Rust | `mhProbe("dm_sizeof_dm_result")` |
| CbC static block | static initializer | Verify struct sizes at JVM startup | See section 4.1 below |
| `mhResult(name, args...)` | static method | Create MethodHandle for {Name}Result-returning C function | Downcall with FD = `of({Name}Result_h, argLayouts)` |
| `mhVoidResult(name, args...)` | static method | Create MethodHandle for {Name}VoidResult-returning C function | Same as above but FD = `of({Name}VoidResult_h, ...)` |
| `mhVoid(name, args...)` | static method | Create MethodHandle for void C function | FD = `ofVoid(argLayouts)` |
| `requireLibrary()` | static method | Throw if library absent | Throws `UnsupportedOperationException` with build path |
| Per-function MethodHandle | `public static final MethodHandle MH$dm_*` | One per C function exported to Java | e.g., `MH$dm_parse_odcs_yaml` |

### 4.1 CbC Sizeof Probe Pattern

```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java
static {
    if (LIBRARY.isPresent()) {
        try {
            long sizeOfDmResult     = (long) MH$dm_sizeof_dm_result.invoke();
            long sizeOfDmVoidResult = (long) MH$dm_sizeof_dm_void_result.invoke();
            long offsetData         = (long) MH$dm_offsetof_dm_result_data.invoke();
            long offsetError        = (long) MH$dm_offsetof_dm_result_error.invoke();

            if (sizeOfDmResult != DM_RESULT_LAYOUT.byteSize())
                throw new AssertionError("DmResult size mismatch: C=" + sizeOfDmResult
                    + " Java=" + DM_RESULT_LAYOUT.byteSize());
            if (sizeOfDmVoidResult != DM_VOID_RESULT_LAYOUT.byteSize())
                throw new AssertionError("DmVoidResult size mismatch: C=" + sizeOfDmVoidResult
                    + " Java=" + DM_VOID_RESULT_LAYOUT.byteSize());
            if (offsetData != 0L)
                throw new AssertionError("DmResult.data offset mismatch: expected 0, got " + offsetData);
            if (offsetError != 8L)
                throw new AssertionError("DmResult.error offset mismatch: expected 8, got " + offsetError);
        } catch (AssertionError ae) {
            throw ae;
        } catch (Throwable t) {
            throw new AssertionError("CbC layout probe failed", t);
        }
    }
}
```

---

## 5. Layer 2: {Name}Bridge.java Mandatory Elements

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/bridge/DataModellingBridge.java`

| Element | Required? | Pattern / Rules |
|---------|-----------|-----------------|
| `Arena arena` field | ✅ | `private final Arena arena = Arena.ofShared();` (thread-safe, shared) |
| Package-private constructor | ✅ | `{Name}Bridge()` — no args, instantiated only by Module |
| `cstrCall(Arena, String)` helper | ✅ | `call.allocateFrom(s, StandardCharsets.UTF_8)` (required) |
| `cstrOpt(Arena, String)` helper | ✅ | Returns `MemorySegment.NULL` if s is null, else calls `cstrCall` |
| `unwrapResult(MemorySegment)` | ✅ | Error-first check; read error msg; free both; throw or return data |
| `unwrapVoidResult(MemorySegment)` | ✅ | Error-only check; free error on non-null; throw if error present |
| `@MapsToCapability` on every method | ✅ | Registry scans and verifies no capability is missing |
| `close()` override | ✅ | `arena.close()` (from `AutoCloseable`) |
| `AutoCloseable` interface | ✅ | Required |
| All methods public | ✅ | Layer 3 calls them; no private helpers unless for allocation/unwrapping |

### 5.1 unwrapResult Pattern (Error-First)

```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/bridge/DataModellingBridge.java
private String unwrapResult(MemorySegment result) {
    // Step 1: Check error pointer FIRST (not data first)
    MemorySegment errorPtr = DmResult_h.error$get(result);
    if (!MemorySegment.NULL.equals(errorPtr)) {
        String msg = errorPtr.reinterpret(Long.MAX_VALUE)
                             .getString(0, StandardCharsets.UTF_8);
        data_modelling_ffi_h.dm_string_free(errorPtr);
        throw new DataModellingException(msg);
    }

    // Step 2: Read data pointer and return; free in finally
    MemorySegment dataPtr = DmResult_h.data$get(result);
    try {
        return dataPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    } finally {
        data_modelling_ffi_h.dm_string_free(dataPtr);
    }
}
```

**Key invariant**: Error pointer is checked and freed BEFORE data pointer is dereferenced. This ensures that if Rust reports an error, Java never reads garbage from the data pointer.

---

## 6. Layer 3: {Name}ServiceImpl.java Mandatory Elements

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/DataModellingServiceImpl.java`

| Element | Required? | Pattern |
|---------|-----------|---------|
| `{Name}Bridge bridge` field | ✅ | `private final DataModellingBridge bridge;` |
| Package-private constructor | ✅ | `{Name}ServiceImpl({Name}Bridge bridge)` — dependency injection |
| `@MapsToCapability` on EVERY method | ✅ | Registry verifies both Bridge AND ServiceImpl coverage |
| `Json.encode(input)` for complex inputs | ✅ | `Json.encode(workspace)` before calling bridge |
| `Json.decode(json, DomainType.class)` for results | ✅ | Bridge returns JSON string; decode to record type |
| Domain types only in public API | ✅ | No `MemorySegment`, `Arena`, `MethodHandle`, `VarHandle` in signatures |
| `close()` delegating to bridge | ✅ | `bridge.close()` (from `AutoCloseable`) |
| Private decode helper methods | ✅ | Use `Json.decode(json, type)` and `MAPPER.readValue(json, TypeReference)` for lists |

### 6.1 ServiceImpl Method Pattern

```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/DataModellingServiceImpl.java
@MapsToCapability(PARSE_ODCS_YAML)
@Override
public WorkspaceModel parseOdcsYaml(String yaml) {
    return decode(bridge.parseOdcsYaml(yaml), WorkspaceModel.class);
}

@MapsToCapability(EXPORT_TO_ODCS_YAML)
@Override
public String exportToOdcsYaml(WorkspaceModel model) {
    return bridge.exportToOdcsYaml(Json.encode(model));
}

@MapsToCapability(FILTER_NODES_BY_OWNER)
@Override
public List<CadsNode> filterNodesByOwner(List<CadsNode> nodes, String owner) {
    return decodeList(bridge.filterNodesByOwner(Json.encode(nodes), owner),
        new TypeReference<List<CadsNode>>() {});
}
```

---

## 7. Capability Enum Template

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/Capability.java`

```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Capability.java
package org.yawlfoundation.yawl.{name};

/**
 * Enumeration of all N {name} capabilities.
 * {@link {Name}CapabilityRegistry#assertComplete()} verifies at startup that every
 * capability has exactly one {@link MapsTo{Name}Capability} annotation in each of
 * {@code {Name}Bridge} and {@code {Name}ServiceImpl}.
 */
public enum {Name}Capability {
    // Group A — Core operations (X)
    OP_A_1,
    OP_A_2,
    // ... more operations ...

    // Group B — Transform operations (Y)
    OP_B_1,
    OP_B_2,

    ;  // ← CRITICAL: Semicolon MUST appear before TOTAL

    /** Total number of capabilities. Used by {@link {Name}CapabilityRegistry} to detect enum drift. */
    public static final int TOTAL = 42;  // Must equal values().length
}
```

**Rules**:
1. One enum value per Rust function you expose to Java
2. Group values by functional area (Core, Transform, Validate, etc.)
3. Semicolon (`;`) must appear after the last enum value, before any static constants
4. `TOTAL` constant MUST equal `values().length`; registry checks this at JVM startup
5. Alphabetical within each group (prefer camelCase over UPPER_SNAKE_CASE)

---

## 8. MapsToCapability Annotation

```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/MapsTo{Name}Capability.java
package org.yawlfoundation.yawl.{name};

import java.lang.annotation.*;

/**
 * Marks a method as implementing a specific {@link {Name}Capability}.
 * Required on every public method in {@code {Name}Bridge} and
 * {@code {Name}ServiceImpl}. {@link {Name}CapabilityRegistry#assertComplete()}
 * fails at startup if any capability is unmapped or over-mapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MapsTo{Name}Capability {
    {Name}Capability value();
}
```

---

## 9. CapabilityRegistry Pattern

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/CapabilityRegistry.java`

The registry performs three checks at JVM startup:

1. **Enum drift**: `Capability.values().length == Capability.TOTAL`
2. **Coverage**: Every enum value has exactly one or two `@MapsToCapability` annotations across Bridge + ServiceImpl
3. **Over-mapping**: No capability is annotated more than twice (once per class)

Key assertion:
```java
public static void assertComplete() {
    // Scan Bridge and ServiceImpl for @MapsToCapability annotations
    // Fail immediately if enum drift or missing capabilities detected
}
```

Call this in `{Name}Module.create()` before instantiating service.

---

## 10. Json Helper Class

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/Json.java`

```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/api/Json.java
package org.yawlfoundation.yawl.{name}.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.{name}.{Name}Exception;

/**
 * Thin Jackson wrapper for JSON encode/decode in the service layer.
 */
public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static String encode(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new {Name}Exception("JSON encode failed: " + e.getMessage(), e);
        }
    }

    public static <T> T decode(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new {Name}Exception("JSON decode failed for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
```

**Configuration**: No custom Jackson configuration needed. Default `ObjectMapper` handles:
- Java 25 records via `@JsonAutoDetect`
- Optional fields via `@JsonIgnoreProperties(ignoreUnknown = true)` on records
- Null handling via `@JsonInclude(Include.NON_NULL)`

---

## 11. Module Factory

Source: `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/DataModellingModule.java`

```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Module.java
package org.yawlfoundation.yawl.{name};

import org.yawlfoundation.yawl.{name}.api.{Name}Service;
import org.yawlfoundation.yawl.{name}.api.{Name}ServiceImpl;
import org.yawlfoundation.yawl.{name}.bridge.{Name}Bridge;

/**
 * Module entry point for the {name} native FFM bridge.
 *
 * <p>Loads the native library and validates capability coverage at startup.
 * Use {@link #create()} to obtain a new {@link {Name}Service} instance.
 *
 * <p>The native library path is controlled by the system property
 * {@code {name_snake}_ffi.library.path}. If absent, the default
 * {@code target/release/lib{name_snake}_ffi.so} is used.
 */
public final class {Name}Module {

    private {Name}Module() {}

    /**
     * Create a new {@link {Name}Service} backed by the native bridge.
     *
     * <p>Validates capability registry on first call. If the native library
     * is absent, the returned service throws {@link UnsupportedOperationException}
     * on every method call.
     *
     * @throws {Name}CapabilityRegistry.CapabilityRegistryException if any capability is unmapped
     */
    public static {Name}Service create() {
        {Name}CapabilityRegistry.assertComplete();
        return new {Name}ServiceImpl(new {Name}Bridge());
    }
}
```

---

## 12. Exception Class

```java
// yawl-{name}/src/main/java/org/yawlfoundation/yawl/{name}/{Name}Exception.java
package org.yawlfoundation.yawl.{name};

/**
 * Thrown when a {name} operation fails (native library returns an error string).
 */
public class {Name}Exception extends RuntimeException {
    public {Name}Exception(String message) {
        super(message);
    }

    public {Name}Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## See Also

- **Explanation**: `/home/user/yawl/.claude/docs/rust-java-panama-ffm/explanation-json-wire-format-and-three-layer-design.md` — Why three layers, why JSON wire format, why capability completeness
- **Correct-by-Construction**: `/home/user/yawl/.claude/docs/rust-java-panama-ffm/explanation-correct-by-construction.md` — Layout invariants, lifetime invariants, sizeof probes
- **Comparison**: Struct-view pattern (zero-copy, high complexity) in `yawl-rust4pm` — use when iterating high-volume homogeneous arrays
