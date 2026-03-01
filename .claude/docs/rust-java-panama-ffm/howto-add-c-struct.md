# How-To: Add a New C Struct Binding to the rust4pm Bridge

**Goal**: Add a new C struct (e.g., `OcelObjectC`) to the rust4pm Panama FFM bridge, enabling Java code to safely read and write struct fields across the Rust FFI boundary.

**Audience**: AI coding agents (Claude) adding struct bindings to an existing Panama FFM bridge.

**Scope**: Rust-to-Java 25 Panama Foreign Function & Memory API (FFM) struct binding, assuming familiarity with C ABI, memory layout, and VarHandle mechanics.

**Prerequisites**:
- Rust struct defined with `#[repr(C)]` in `rust/rust4pm/src/lib.rs`
- C header declarations in `rust/rust4pm/rust4pm.h`
- Understanding of 64-bit pointer sizes (ADDRESS = 8 bytes, JAVA_LONG = 8 bytes)
- JDK 25+ with Foreign Function & Memory API enabled
- Successful build of rust4pm Rust library (`bash scripts/build-rust4pm.sh`)

---

## 1. Prerequisites Checklist

Before proceeding, verify all prerequisites are met:

- [ ] Rust library (`librust4pm.so` or `.dll`) builds successfully
  ```bash
  cd rust/rust4pm && cargo build --release
  ```
- [ ] New C struct is defined with `#[repr(C)]` in `lib.rs` (no padding surprises)
- [ ] C header in `rust4pm.h` includes struct declaration
- [ ] sizeof probe function declared in header (e.g., `rust4pm_sizeof_ocel_object_c()`)
- [ ] No unsafe assumptions about field alignment or padding
- [ ] You can run `mvn test -pl yawl-rust4pm` without errors

**Pitfall**: If the Rust library is not built, Java's `SymbolLookup.libraryLookup()` will silently fail, and all methods will throw `UnsupportedOperationException` at call time rather than at struct-binding time.

---

## 2. Step: Add `#[repr(C)]` Struct in lib.rs with Field Annotations

**File**: `/home/user/yawl/rust/rust4pm/src/lib.rs`

Define the struct with `#[repr(C)]` to fix the memory layout to C ABI.

**Example from codebase** (OcelObjectC):
```rust
#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelObjectC {
    pub object_id:   *const c_char,
    pub object_type: *const c_char,
}
```

**What each annotation does**:
- `#[repr(C)]`: Rust promises layout matches C struct (no reordering, no padding coalescing)
- `#[derive(Clone, Copy)]`: Allows Copy semantics (essential for pointer-carrying structs)

**Field order matters**: In the Rust struct, list fields in the EXACT order they appear in the C header. The struct layout algorithm assigns byte offsets sequentially.

**Common pitfall — Hidden padding**: Rust may insert padding between fields to align them. For example:
```rust
// BAD: May have padding between u8 and i64
#[repr(C)]
pub struct BadLayout {
    pub flag: u8,       // offset 0, 1 byte
    // 7 bytes padding here (alignment to i64)
    pub count: i64,     // offset 8, 8 bytes
}

// GOOD: Ordered for natural alignment
#[repr(C)]
pub struct GoodLayout {
    pub count: i64,     // offset 0, 8 bytes
    pub flag: u8,       // offset 8, 1 byte
    // 7 bytes padding (trailing)
}
```

Use `std::mem::size_of::<YourStruct>()` at compile time to verify total size.

---

## 3. Step: Add OcelObjectsResult with Owned/Borrowed Distinction

**File**: `/home/user/yawl/rust/rust4pm/src/lib.rs`

For result types that return arrays or strings, distinguish between:
- **Owned**: Allocated by Rust, must be freed by caller (use `*mut`)
- **Borrowed**: Points into Rust-owned storage, freed when parent is freed (use `*const`)

**Example from codebase** (OcelObjectsResult):
```rust
#[repr(C)]
pub struct OcelObjectsResult {
    objects: *const OcelObjectC,  // BORROWED from OcelLogInternal
    count:   usize,               // Length of array
    error:   *mut c_char,         // OWNED, caller must free via rust4pm_error_free()
}
```

**Key distinctions**:
- `*const OcelObjectC`: Borrowed pointer (do not free separately)
- `*mut c_char`: Owned (must be freed)
- `usize`: This is `size_t` in C — maps to JAVA_LONG in Java

**Common pitfall — Double-free**: If you mark a borrowed pointer as owned (use `*mut` instead of `*const`), the caller will try to `free()` memory still owned by Rust, causing a crash. Always use `*const` for borrowed data.

---

## 4. Step: Add sizeof Probe Function in Rust

**File**: `/home/user/yawl/rust/rust4pm/src/lib.rs`

At the bottom of the file (around line 557+), add a `#[no_mangle]` sizeof probe:

```rust
#[no_mangle]
pub extern "C" fn rust4pm_sizeof_ocel_object_c() -> usize {
    std::mem::size_of::<OcelObjectC>()
}
```

**Why this matters**: Java will call this function at JVM startup and assert that its hand-written `StructLayout.byteSize()` matches the actual Rust size. If they diverge, an `AssertionError` is thrown immediately — not a silent memory corruption at read time.

**Naming convention**: `rust4pm_sizeof_<struct_name_snake_case>`

**Common pitfall — Unused probe**: If you forget to add the probe, the Java side will fail with "symbol not found" at JVM startup. If you name it incorrectly, the linker can't find it.

---

## 5. Step: Add C Header Declarations in rust4pm.h

**File**: `/home/user/yawl/rust/rust4pm/rust4pm.h`

Add the struct typedef and function declaration.

**Example from codebase** (OcelObjectC):
```c
typedef struct {
    const char *object_id;
    const char *object_type;
} OcelObjectC;

typedef struct {
    const OcelObjectC *objects;
    size_t             count;
    char              *error;
} OcelObjectsResult;

/* ... later in the file ... */

size_t rust4pm_sizeof_ocel_object_c(void);
```

**Field order must match Rust exactly**:
- C: `const char *object_id, const char *object_type`
- Rust: `pub object_id: *const c_char, pub object_type: *const c_char`

**Common pitfall — Type mismatch**:
- C: `size_t` (platform-specific, typically u64 on 64-bit)
- Rust: `usize` (also platform-specific)
- Java: `JAVA_LONG` (always 64-bit)
- This mapping is correct and safe.

---

## 6. Step: Calculate Expected StructLayout Byte Size

**File**: (mental calculation before writing Java code)

For 64-bit systems (typical deployment):
- **ADDRESS** (pointer): 8 bytes
- **JAVA_LONG** (i64/u64/size_t): 8 bytes
- **JAVA_DOUBLE** (f64): 8 bytes
- **JAVA_INT** (i32/u32): 4 bytes
- **JAVA_BYTE** (i8/u8): 1 byte

**Example calculation for OcelObjectC**:
```
OcelObjectC {
  object_id:   *const c_char  →  ADDRESS  →  8 bytes
  object_type: *const c_char  →  ADDRESS  →  8 bytes
}
Total = 16 bytes
```

**Example calculation for OcelObjectsResult**:
```
OcelObjectsResult {
  objects: *const OcelObjectC  →  ADDRESS  →  8 bytes
  count:   usize               →  JAVA_LONG  →  8 bytes
  error:   *mut c_char         →  ADDRESS  →  8 bytes
}
Total = 24 bytes
```

**Common pitfall — Off-by-one byte**: A struct with padding will have a larger total size than the sum of field sizes. Use the Rust `std::mem::size_of()` value as the ground truth.

---

## 7. Step: Add StructLayout in rust4pm_h.java with Correct Field Ordering

**File**: `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`

Define the struct layout as a static final constant, listing fields in Rust order:

**Example from codebase** (OcelObjectC):
```java
/**
 * OcelObjectC: { object_id(*)(8), object_type(*)(8) } — 16 bytes.
 * Strings BORROWED from OcelLogInternal CStrings.
 */
public static final StructLayout OCEL_OBJECT_C_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("object_id"),
        ADDRESS.withName("object_type")
    ).withName("OcelObjectC");
```

**Step-by-step**:
1. Comment with byte sizes and borrowing info
2. `MemoryLayout.structLayout(...)` — Creates a struct descriptor
3. For each field in order:
   - Choose layout type (`ADDRESS`, `JAVA_LONG`, `JAVA_DOUBLE`, etc.)
   - Chain `.withName("fieldName")`
4. Chain `.withName("StructName")` at the end

**Common pitfall — Wrong field order**: If you list fields out of order, the VarHandle indices will be wrong, and reads/writes will hit the wrong bytes. Always follow Rust struct field order exactly.

**Common pitfall — Wrong layout type**:
- Pointer in Rust (`*const T`, `*mut T`) → `ADDRESS` in Java
- `i64`, `u64`, `size_t` in Rust → `JAVA_LONG` in Java
- `f64` in Rust → `JAVA_DOUBLE` in Java
- `i32`, `u32` in Rust → `JAVA_INT` in Java

---

## 8. Step: Add VarHandles for Each Field

**File**: `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`

After the StructLayout definitions, add VarHandles to safely read/write each field:

**Example from codebase** (OcelObjectC):
```java
// ── VarHandles — OcelObjectC ────────────────────────────────────

public static final VarHandle OCEL_OBJECT_C_OBJECT_ID =
    OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_id"));
public static final VarHandle OCEL_OBJECT_C_OBJECT_TYPE =
    OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_type"));
```

**Naming convention**: `<STRUCT_UPPERCASE>_<FIELD_UPPERCASE>`

**Usage** (reading a field from a MemorySegment):
```java
// Read object_id pointer from segment at offset 0
MemorySegment idPtr = (MemorySegment) OCEL_OBJECT_C_OBJECT_ID.get(
    SegmentScope.auto(), segment, 0L);
```

**Common pitfall — Wrong groupElement name**: The name passed to `groupElement()` must EXACTLY match the field name in the StructLayout (case-sensitive). Typos result in runtime `IllegalArgumentException`.

**Common pitfall — Field ordering in varHandle**: The order of VarHandle declarations doesn't matter, but the field name must match the StructLayout exactly.

---

## 9. Step: Add MethodHandle and assertLayout() Call

**File**: `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`

In the static MethodHandle cache section (around line 200+), declare a MethodHandle for the sizeof probe:

```java
static final MethodHandle MH$sizeof_ocel_object_c;
```

In the static initializer block (inside `if (LIBRARY.isPresent())`), add the downcall:

```java
MH$sizeof_ocel_object_c = LINKER.downcallHandle(
    lib.find("rust4pm_sizeof_ocel_object_c").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG),
    Linker.Option.critical(true));
```

Then add the assertLayout call:

```java
assertLayout("OcelObjectC", OCEL_OBJECT_C_LAYOUT, MH$sizeof_ocel_object_c);
```

And in the else branch (library absent), set to null:

```java
MH$sizeof_ocel_object_c = null;
```

**Common pitfall — Function name typo**: If the symbol name `"rust4pm_sizeof_ocel_object_c"` doesn't match the Rust `#[no_mangle]` function, the linker throws `UnsatisfiedLinkError` at JVM startup.

**Common pitfall — Wrong return type**: The FunctionDescriptor must be `FunctionDescriptor.of(JAVA_LONG)` for a sizeof probe (returns size_t/usize).

**Common pitfall — Missing assertLayout call**: Without this, size mismatches won't be detected until a struct field is accessed at runtime, potentially causing memory corruption.

---

## 10. Step: Add Static Method Wrapper

**File**: `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`

In the public API section (around line 390+), add a wrapper method for any exported function that uses your struct:

**Example from codebase** (rust4pm_log_get_objects):
```java
public static MemorySegment rust4pm_log_get_objects(
        SegmentAllocator allocator, MemorySegment handlePtr) {
    requireLibrary();
    try {
        return (MemorySegment) MH$log_get_objects.invokeExact(allocator, handlePtr);
    } catch (Throwable t) { throw new AssertionError("rust4pm_log_get_objects failed", t); }
}
```

**Pattern**:
1. Call `requireLibrary()` to throw early if library is absent
2. Invoke the MethodHandle with `.invokeExact(args)`
3. Cast result to expected type
4. Catch all `Throwable` and wrap in `AssertionError` with function name

**Common pitfall — Missing requireLibrary()**: If you skip this, a null MethodHandle (library absent) will throw `NullPointerException` with a confusing stack trace instead of a clear error message.

**Common pitfall — Wrong argument order**: The order of arguments in `invokeExact()` must match the FunctionDescriptor definition exactly (library/allocator first if using Panama allocation).

---

## 11. Step: Write a Library-Absent Test for the New Binding

**File**: Create or update `/home/user/yawl/yawl-rust4pm/src/test/java/org/yawlfoundation/yawl/rust4pm/LibraryAbsentTest.java`

This test verifies that the binding fails gracefully when the library is not found:

```java
package org.yawlfoundation.yawl.rust4pm;

import org.junit.jupiter.api.Test;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

public class Rust4pmLibraryAbsentTest {

    @Test
    void ocelObjectCLayoutIsValid() {
        // Even without library, the StructLayout itself should be valid
        assertNotNull(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
        assertEquals(16, rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize());
    }

    @Test
    void varHandlesAreAccessible() {
        // VarHandles should exist regardless of library presence
        assertNotNull(rust4pm_h.OCEL_OBJECT_C_OBJECT_ID);
        assertNotNull(rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE);
    }

    @Test
    void methodHandleIsNullWhenLibraryAbsent() {
        if (rust4pm_h.LIBRARY.isEmpty()) {
            // Library not present; method handles should be null
            assertNull(Rust4pmLibraryAbsentTest.getSizeofHandle());
        } else {
            // Library present; this test is N/A
            assertTrue(rust4pm_h.LIBRARY.isPresent());
        }
    }

    @Test
    void callingSizeofThrowsUnsupportedWhenLibraryAbsent() {
        if (rust4pm_h.LIBRARY.isEmpty()) {
            assertThrows(UnsupportedOperationException.class, () -> {
                rust4pm_h.rust4pm_log_get_objects(
                    Arena.ofAuto().scope(),
                    MemorySegment.NULL);
            });
        }
    }
}
```

**What to test**:
- StructLayout exists and has correct byte size
- VarHandles exist and are accessible
- MethodHandle is null when library absent
- Calling the function throws `UnsupportedOperationException` with helpful message

**Common pitfall — Assuming library is present**: Tests may pass on your dev machine but fail in CI if the library isn't built. Always test the library-absent path explicitly.

---

## 12. Step: Verify with mvn test -pl yawl-rust4pm

**Command**:
```bash
cd /home/user/yawl
mvn test -pl yawl-rust4pm
```

**Success criteria**:
- All tests pass
- No `AssertionError` about layout mismatches
- No `UnsatisfiedLinkError` for the sizeof probe
- Output includes:
  ```
  BUILD SUCCESS
  ```

**Troubleshooting**:

| Error | Cause | Fix |
|-------|-------|-----|
| `UnsatisfiedLinkError: rust4pm_sizeof_ocel_object_c` | Sizeof probe not exported from Rust | Add `#[no_mangle] pub extern "C" fn ...` to lib.rs |
| `AssertionError: Layout mismatch for OcelObjectC` | StructLayout byte size != Rust sizeof | Recalculate field sizes; check for padding |
| `IllegalArgumentException: groupElement("field")` | Field name typo in VarHandle | Match the exact name in StructLayout |
| `NullPointerException in rust4pm_log_get_objects` | Library not found; missing `requireLibrary()` call | Add `requireLibrary()` at start of wrapper method |
| `NoSuchFieldError: OCEL_OBJECT_C_LAYOUT` | StructLayout constant not public | Change to `public static final` |

---

## Common Pitfalls Summary

| Pitfall | Impact | Prevention |
|---------|--------|-----------|
| Struct fields out of order in Java | Wrong byte offset for each field | Copy field order exactly from Rust |
| Wrong pointer layout type (JAVA_INT instead of ADDRESS) | Memory misalignment, crashes | Use ADDRESS for all C pointers, JAVA_LONG for size_t/usize |
| Missing sizeof probe in Rust | Silent corruption if sizes diverge | Add probe for every struct, call assertLayout() in Java |
| Owned pointer marked as `*const` (borrowed) | Memory leak | Use `*const` only for borrowed, `*mut` for owned |
| Field name typo in groupElement() | Runtime crash when accessing field | Match case-sensitive field names exactly |
| Wrong FunctionDescriptor for sizeof | Linker error at startup | Use `FunctionDescriptor.of(JAVA_LONG)` for sizeof |
| Missing requireLibrary() call | Confusing NullPointerException | Add to every public wrapper method |
| StructLayout byte size calculation off by one | Crashes on struct access | Verify with Rust `size_of::<>()` |

---

## Worked Example: Adding OcelObjectC

This is the actual binding added to the codebase. Follow this step-by-step:

### 1. Rust struct (lib.rs, lines 151-156)
```rust
#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelObjectC {
    pub object_id:   *const c_char,
    pub object_type: *const c_char,
}
```

### 2. Sizeof probe (lib.rs, lines 577-580)
```rust
#[no_mangle]
pub extern "C" fn rust4pm_sizeof_ocel_object_c() -> usize {
    std::mem::size_of::<OcelObjectC>()
}
```

### 3. C header (rust4pm.h, lines 38-41)
```c
typedef struct {
    const char *object_id;
    const char *object_type;
} OcelObjectC;
```

### 4. Java StructLayout (rust4pm_h.java, lines 95-99)
```java
public static final StructLayout OCEL_OBJECT_C_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("object_id"),
        ADDRESS.withName("object_type")
    ).withName("OcelObjectC");
```

### 5. VarHandles (rust4pm_h.java, lines 156-159)
```java
public static final VarHandle OCEL_OBJECT_C_OBJECT_ID =
    OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_id"));
public static final VarHandle OCEL_OBJECT_C_OBJECT_TYPE =
    OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_type"));
```

### 6. MethodHandle (rust4pm_h.java, lines 205-206, 266-268, 308)
```java
// Declaration
static final MethodHandle MH$sizeof_ocel_object_c;

// In if (LIBRARY.isPresent()) block
MH$sizeof_ocel_object_c = LINKER.downcallHandle(
    lib.find("rust4pm_sizeof_ocel_object_c").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));

// Assertion
assertLayout("OcelObjectC", OCEL_OBJECT_C_LAYOUT, MH$sizeof_ocel_object_c);

// In else block
MH$sizeof_ocel_object_c = null;
```

### 7. Test (library-absent verification)
```java
@Test
void ocelObjectCLayoutIsValid() {
    assertNotNull(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
    assertEquals(16, rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize());
}
```

### 8. Verify
```bash
mvn test -pl yawl-rust4pm
# Should see: BUILD SUCCESS
```

---

## References

- **Rust FFI**: `std::os::raw::c_char`, `#[repr(C)]`, `#[no_mangle]`
- **Java 25 FFM**: `MemoryLayout`, `StructLayout`, `VarHandle`, `MethodHandle`, `FunctionDescriptor`
- **Codebase files**:
  - `/home/user/yawl/rust/rust4pm/src/lib.rs` (Rust definitions)
  - `/home/user/yawl/rust/rust4pm/rust4pm.h` (C header)
  - `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java` (Java bindings)

---

## Success Criteria

You have successfully added a new C struct binding when:

1. [ ] Rust struct compiles with `#[repr(C)]` and `cargo build --release`
2. [ ] C header declares struct typedef and sizeof probe
3. [ ] Java StructLayout has correct byte size (matches `Rust::size_of<>()`)
4. [ ] All VarHandles compile without errors
5. [ ] MethodHandle for sizeof is declared and initialized
6. [ ] `assertLayout()` call is present in static initializer
7. [ ] `mvn test -pl yawl-rust4pm` passes
8. [ ] Library-absent test verifies graceful failure
9. [ ] No `UnsatisfiedLinkError`, `AssertionError`, or memory corruption

---

**Next**: If you need to add a function that takes or returns your struct, follow the [How-To: Add a New C Function Binding](./howto-add-c-function.md) guide.
