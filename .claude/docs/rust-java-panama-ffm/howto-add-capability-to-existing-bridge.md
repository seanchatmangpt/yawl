# How to Add a Capability to an Existing Rust–Java Bridge

**Goal**: Wire a new Rust function through all three layers (FFM binding, Bridge, Service) and add a capability test, starting from an existing working bridge.

**When to use this guide**: You have a working YAWL data-modelling bridge (all 3 layers + capability system in place). A new Rust function is available and you need to export it as a capability. This guide ensures zero ambiguity about what to change and where.

**Prerequisites**:
- Existing working bridge module with `Capability.java`, `DataModellingBridge.java`, `DataModellingServiceImpl.java`, and test infrastructure in place.
- Rust function already compiled and exported in the shared library with a C signature.
- Familiarity with JUnit 5, Java 21+ Foreign Function & Memory API (Panama FFM), and the module's architecture.

---

## Overview: 7 Changes Across 3 Layers

Adding one new capability (`EXPORT_TO_AVRO_SCHEMA` as an example) requires:

1. **Capability enum** (`Capability.java`) — add enum value, update TOTAL
2. **Layer 1: FFM binding** (`data_modelling_ffi_h.java`) — add MethodHandle and invoker
3. **Layer 2: Bridge** (`DataModellingBridge.java`) — add @MapsToCapability bridge method
4. **Layer 3 Service interface** (`DataModellingService.java`) — add public method signature
5. **Layer 3 Service implementation** (`DataModellingServiceImpl.java`) — add @MapsToCapability impl
6. **Test** (`*CapabilityTest.java`) — add @CapabilityTest test method
7. **Build verification** — compile, test registry, run capability tests

This is a linear workflow with no feedback loops if done correctly. Each layer depends on the previous layer being complete.

---

## Step 1: Understand Your Rust Function

Your Rust function is already exported with a C signature. For this example, assume:

```c
// data_modelling_ffi.h (pre-built)
DmResult dm_export_avro_schema(const char* odcs_json);
```

This function:
- Takes a C string (pointer + implicit null termination)
- Returns `DmResult` struct: `{ data: *mut c_char, error: *mut c_char }` (16 bytes)
- On success: `data` = JSON string, `error` = NULL
- On failure: `error` = error message, `data` = NULL
- Caller must call `dm_string_free()` on both pointers when done

The Bridge layer will handle all Arena allocation, null-termination, and pointer cleanup. You just need to wire the signatures.

---

## Step 2: Add the Enum Value

Open `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/Capability.java`.

Locate the group containing similar capabilities. For export operations, find **Group D — Schema Format Export**.

Add your capability to the enum in alphabetical order within the group. Update `TOTAL` to reflect the new count.

**Before:**
```java
// org/yawlfoundation/yawl/datamodelling/Capability.java
    // Group D — Schema Format Export (5)
    EXPORT_TO_AVRO,
    EXPORT_TO_JSON_SCHEMA,
    EXPORT_TO_PROTOBUF,
    EXPORT_TO_CADS,
    EXPORT_TO_ODPS,

    // ... other groups ...

    public static final int TOTAL = 42;
```

**After:**
```java
// org/yawlfoundation/yawl/datamodelling/Capability.java
    // Group D — Schema Format Export (6)
    EXPORT_TO_AVRO,
    EXPORT_TO_AVRO_SCHEMA,
    EXPORT_TO_JSON_SCHEMA,
    EXPORT_TO_PROTOBUF,
    EXPORT_TO_CADS,
    EXPORT_TO_ODPS,

    // ... other groups ...

    public static final int TOTAL = 43;
```

**Verification command:**
```bash
mvn test -pl yawl-data-modelling -Dtest=DataModellingCapabilityRegistryTest#totalConstant_matchesEnumLength
```

This test must pass before proceeding. If it fails with "expected 43 but was 42", you forgot to update TOTAL.

---

## Step 3: Add Layer 1 Binding in FFM Header

Open `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java`.

This is generated code, but you must add the MethodHandle and invoker manually.

**Step 3a: Add MethodHandle field**

Locate the MethodHandle declarations in Group D (around line 154). Add your field:

```java
// org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java
    // Group D
    public static final MethodHandle MH$dm_export_to_avro           = mhResult("dm_export_to_avro",           ADDRESS);
    public static final MethodHandle MH$dm_export_to_avro_schema    = mhResult("dm_export_to_avro_schema",    ADDRESS);
    public static final MethodHandle MH$dm_export_to_json_schema    = mhResult("dm_export_to_json_schema",    ADDRESS);
```

Note:
- Field name: `MH$dm_export_to_avro_schema` (matches C function name)
- `mhResult(...)` creates a FunctionDescriptor returning `DmResult`
- `ADDRESS` parameter = one C string input (the ODCS JSON)
- If multiple parameters: `mhResult("...", ADDRESS, ADDRESS)` for two strings, etc.

**Step 3b: Add static invoker method**

Locate the invoker methods in Group D (around line 266). Add your invoker:

```java
// org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java
    public static MemorySegment dm_export_to_avro_schema(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_avro_schema.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }
```

**Exact pattern**:
- Method is `public static`
- First parameter is `SegmentAllocator alloc` (provided by Bridge)
- Remaining parameters are `MemorySegment` (one per C argument)
- Call `requireLibrary()` first
- Invoke the MethodHandle with `alloc` + all MemorySegment args
- Cast result to `MemorySegment`
- Wrap any `Throwable` in `AssertionError` (indicates programming error, not user error)
- Return the `MemorySegment` containing `DmResult`

**Important**: If the library is NOT loaded (development without compiled Rust), the MethodHandle field will be `null`. Initialize it explicitly in the `else` branch of the library loading static block if needed, but the invoker's `requireLibrary()` call ensures a clear error message.

---

## Step 4: Add Bridge Method (Layer 2)

Open `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/bridge/DataModellingBridge.java`.

Locate Group D (around line 270). Add your method:

```java
// org/yawlfoundation/yawl/datamodelling/bridge/DataModellingBridge.java
    /**
     * Export ODCS JSON to Apache Avro schema (with schema-level metadata).
     *
     * @param json the ODCS JSON representation
     * @return Avro schema string with metadata
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_AVRO_SCHEMA)
    public String exportToAvroSchema(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_avro_schema(call, cstrCall(call, json)));
        }
    }
```

**Key points**:

| Element | Rule | Example |
|---------|------|---------|
| **Annotation** | Required: `@MapsToCapability(Capability.EXPORT_TO_AVRO_SCHEMA)` | Must match the enum value exactly |
| **Visibility** | Package-private (not `public`) | Bridge is internal Layer 2; Service is the public API |
| **Arena** | Use `Arena.ofConfined()` per-call; scope with try-with-resources | Automatically freed when block exits |
| **String encoding** | `cstrCall(call, string)` = allocate UTF-8 C string from call arena | Returns `MemorySegment` |
| **Error handling** | `unwrapResult()` extracts data or throws `DataModellingException` | Frees pointers internally |
| **Return type** | `String` (no MemorySegment, no Arena visible to caller) | Pure Java API |

**Pattern for multi-parameter functions**:

If your Rust function takes two C strings (e.g., source and target):
```java
@MapsToCapability(Capability.CONVERT_SOME_FORMAT)
public String convertFormat(String source, String target) {
    try (Arena call = Arena.ofConfined()) {
        return unwrapResult(data_modelling_ffi_h.dm_convert_format(
            call, cstrCall(call, source), cstrCall(call, target)));
    }
}
```

**Do NOT**:
- Use `Arena.ofShared()` here (that's for Bridge's lifetime, already declared as a field)
- Leak Arena references to caller
- Forget `@MapsToCapability` (registry will fail at startup)

---

## Step 5: Add Service Methods (Layer 3)

Open `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/DataModellingService.java`.

Add the interface method signature in the correct group comment section:

```java
// org/yawlfoundation/yawl/datamodelling/api/DataModellingService.java
    // Group D — Schema Format Export
    String exportToAvro(WorkspaceModel model);
    String exportToAvroSchema(WorkspaceModel model);
    String exportToJsonSchema(WorkspaceModel model);
    String exportToProtobuf(WorkspaceModel model);
    String exportToCads(WorkspaceModel model);
    String exportToOdps(WorkspaceModel model);
```

**Service interface rules**:
- Public method (part of the public API)
- Takes domain types (`WorkspaceModel`, not `String`)
- Returns domain types or throws checked/unchecked exceptions
- No annotations on interface (only on impl)
- Ordered alphabetically within group

Now open `/home/user/yawl/yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/DataModellingServiceImpl.java`.

Add the implementation in the corresponding group (around line 92):

```java
// org/yawlfoundation/yawl/datamodelling/api/DataModellingServiceImpl.java
    // Group D
    @MapsToCapability(EXPORT_TO_AVRO)
    @Override public String exportToAvro(WorkspaceModel model) {
        return bridge.exportToAvro(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_AVRO_SCHEMA)
    @Override public String exportToAvroSchema(WorkspaceModel model) {
        return bridge.exportToAvroSchema(Json.encode(model));
    }

    @MapsToCapability(EXPORT_TO_JSON_SCHEMA)
    @Override public String exportToJsonSchema(WorkspaceModel model) {
        return bridge.exportToJsonSchema(Json.encode(model));
    }
```

**Service implementation rules**:

| Element | Rule | Example |
|---------|------|---------|
| **@MapsToCapability** | Mandatory (registry scans both Bridge + ServiceImpl) | Must match enum exactly |
| **@Override** | Always present | Implements interface method |
| **Input encoding** | `Json.encode(domainType)` for complex types | Produces String JSON |
| **Bridge call** | Call matching Bridge method | `bridge.exportToAvroSchema(...)` |
| **Output decoding** | For output, depends on return type | Return `String` directly; if `List<T>`, use `decodeList(json, ref)` |
| **Exception handling** | Propagate domain exceptions naturally | Bridge throws `DataModellingException`, Service re-throws |

**Pattern for input/output mismatch**:

If Bridge returns JSON but Service returns a domain type:
```java
@MapsToCapability(PARSE_SKETCH_YAML)
@Override public Sketch parseSketchYaml(String yaml) {
    return decode(bridge.parseSketchYaml(yaml), Sketch.class);
}
```

If Service accepts a domain type and returns JSON:
```java
@MapsToCapability(EXPORT_SKETCH_TO_YAML)
@Override public String exportSketchToYaml(Sketch sketch) {
    return bridge.exportSketchToYaml(Json.encode(sketch));
}
```

---

## Step 6: Add Capability Test

Open `/home/user/yawl/yawl-data-modelling/src/test/java/org/yawlfoundation/yawl/datamodelling/SchemaExportCapabilityTest.java` (the test class for Group D export operations).

Add test methods after the last EXPORT_TO_ODPS test (around line 190):

```java
// org/yawlfoundation/yawl/datamodelling/SchemaExportCapabilityTest.java

    // EXPORT_TO_AVRO_SCHEMA
    @Test
    @CapabilityTest(EXPORT_TO_AVRO_SCHEMA)
    @DisplayName("EXPORT_TO_AVRO_SCHEMA: WorkspaceModel → non-blank output")
    void exportToAvroSchema_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToAvroSchema(ws);
        assertNotNull(out, "exportToAvroSchema must return non-null output");
        assertFalse(out.isBlank(), "Avro schema export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_AVRO_SCHEMA)
    @DisplayName("EXPORT_TO_AVRO_SCHEMA: output contains 'schema' or 'record' marker")
    void exportToAvroSchema_output_containsSchemaMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToAvroSchema(ws);
        assertTrue(
            out.toLowerCase().contains("schema") || out.contains("record"),
            "Avro schema output must contain 'schema' or 'record' keyword"
        );
    }
```

**Test rules**:

| Element | Rule | Example |
|---------|------|---------|
| **@CapabilityTest(EXPORT_TO_AVRO_SCHEMA)** | Required marker (testability gate) | Maps test to capability |
| **@Test** | JUnit 5 test annotation | Standard test marker |
| **Test method name** | `capability_input_outcome()` pattern | Describe what you're testing, not how |
| **Assertions** | Structural, not just null checks | `assertFalse(output.isBlank())` is stronger than `assertNotNull()` |
| **Fixtures** | Use constants from `DataModellingTestFixtures` | `ODCS_YAML`, `ODCS_TWO_TABLE_YAML`, etc. |
| **Setup/teardown** | Already in class `@BeforeAll`/`@AfterAll` | Uses `assumeTrue(LIBRARY.isPresent())` to skip if no native lib |

**Add at least 2 tests per capability**:
1. **Positive basic test** — valid input → non-null, non-blank output
2. **Format validation** — output contains expected markers (e.g., "record" for Avro, "properties" for JSON Schema)

Optional third test (if applicable):
3. **Roundtrip** — export → re-import → not null (validates stability)

Use test fixtures from `DataModellingTestFixtures` (e.g., `ODCS_YAML`). If your test needs a specific fixture, ask teammates or create one in the TestFixtures class.

---

## Step 7: Build and Verify

Run these commands in order:

### 7a: Compile

```bash
mvn test-compile -pl yawl-data-modelling
```

This catches syntax errors in the files you edited. If successful, all three layers are structurally correct.

### 7b: Registry test (no native library required)

```bash
mvn test -pl yawl-data-modelling \
  -Dtest=DataModellingCapabilityRegistryTest
```

This verifies:
- Every capability in `Capability` enum has exactly one `@MapsToCapability` in Bridge
- Every capability in `Capability` enum has exactly one `@MapsToCapability` in ServiceImpl
- TOTAL constant matches enum length
- Annotation values match capability names

**If it fails**:
- Missing annotation: add `@MapsToCapability(EXPORT_TO_AVRO_SCHEMA)` to both Bridge and ServiceImpl methods
- Mismatched annotation: ensure both use `Capability.EXPORT_TO_AVRO_SCHEMA` (not a typo)
- TOTAL mismatch: update `Capability.TOTAL` to 43

### 7c: Capability tests (requires compiled native library)

```bash
mvn test -pl yawl-data-modelling \
  -Dgroups=capability \
  -Dtest=SchemaExportCapabilityTest
```

This runs your new test methods (and all other capability tests in that class) against the native library.

**If library is absent**: Tests are skipped (via `assumeTrue(LIBRARY.isPresent())`), which is OK for CI without native build artifacts.

**If tests fail**:
- Check Bridge method logic (ensure Arena is closed properly)
- Check FFM invoker (ensure MethodHandle parameters match C signature)
- Check test assertions (may be too strict for actual output)

### 7d: Full build (optional, pre-commit gate)

```bash
mvn clean verify -pl yawl-data-modelling
```

Runs compile, unit tests, capability tests, and static analysis. Run this before committing.

---

## Common Mistakes

### 1. Added to ServiceImpl but forgot Bridge
**Error**: `CapabilityRegistryException: capability EXPORT_TO_AVRO_SCHEMA unmapped in Bridge`
**Fix**: Add `@MapsToCapability(EXPORT_TO_AVRO_SCHEMA)` method to Bridge class

### 2. Added to Bridge but forgot ServiceImpl
**Error**: `CapabilityRegistryException: capability EXPORT_TO_AVRO_SCHEMA unmapped in ServiceImpl`
**Fix**: Add `@MapsToCapability(EXPORT_TO_AVRO_SCHEMA)` method to ServiceImpl class

### 3. Updated Capability enum but forgot TOTAL
**Error**: `DataModellingCapabilityRegistryTest.totalConstant_matchesEnumLength() expected 43 but was 42`
**Fix**: Update `public static final int TOTAL = 43;` in Capability.java

### 4. Wrong parameter types in FFM invoker
**Error**: `AssertionError: native call failed: invokeExact() type mismatch: (SegmentAllocator, MemorySegment) but was (SegmentAllocator, MemorySegment, MemorySegment)`
**Fix**: Check FFM invoker signature matches C function. If C function takes 2 parameters: `mhResult("...", ADDRESS, ADDRESS)` + invoker with 2 MemorySegment args

### 5. Used Arena.ofShared() in per-call allocation
**Error**: Memory leak or `SegmentAllocationException` (arena closed unexpectedly)
**Fix**: Use `Arena.ofConfined()` scoped with try-with-resources in Bridge method

### 6. Forgot @MapsToCapability on Bridge method
**Error**: `CapabilityRegistryException: capability EXPORT_TO_AVRO_SCHEMA unmapped in Bridge` (or no error if registry scan is loose, but tests will fail mysteriously)
**Fix**: Add `@MapsToCapability(Capability.EXPORT_TO_AVRO_SCHEMA)` to the method

### 7. Annotation parameter is a string, not an enum constant
**Example error**: `@MapsToCapability("EXPORT_TO_AVRO_SCHEMA")` (wrong — annotation value parameter expects `Capability` enum, not String)
**Fix**: Use `@MapsToCapability(Capability.EXPORT_TO_AVRO_SCHEMA)` or with static import `@MapsToCapability(EXPORT_TO_AVRO_SCHEMA)`

---

## See Also

- **Tutorial**: `.claude/docs/rust-java-panama-ffm/tutorial-understand-the-three-layers.md` — deep dive into Layer 1/2/3 design
- **FFM patterns**: `.claude/rules/java25/modern-java.md` — Panama FFM best practices
- **Chicago TDD**: `.claude/rules/chicago-tdd.md` — how to write structural capability tests
- **Capability system**: Architecture documentation in the bridge module's package-info.java

---

## Quick Checklist

- [ ] Added `EXPORT_TO_AVRO_SCHEMA` to `Capability` enum (alphabetical within group)
- [ ] Updated `Capability.TOTAL` from 42 to 43
- [ ] Added `MH$dm_export_to_avro_schema` MethodHandle in FFM
- [ ] Added `dm_export_to_avro_schema()` invoker in FFM
- [ ] Added `exportToAvroSchema(String json)` method to Bridge with `@MapsToCapability`
- [ ] Added `exportToAvroSchema(WorkspaceModel model)` to Service interface
- [ ] Added `exportToAvroSchema(WorkspaceModel model)` impl to ServiceImpl with `@MapsToCapability`
- [ ] Added 2+ test methods with `@CapabilityTest(EXPORT_TO_AVRO_SCHEMA)` to test class
- [ ] `mvn test-compile -pl yawl-data-modelling` passes
- [ ] `mvn test -pl yawl-data-modelling -Dtest=DataModellingCapabilityRegistryTest` passes
- [ ] `mvn test -pl yawl-data-modelling -Dgroups=capability -Dtest=SchemaExportCapabilityTest` passes (or skipped if no library)
