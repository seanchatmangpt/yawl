# How-To: Debug Panama FFM Layout Mismatches in the rust4pm Bridge

**Branch**: `claude/rust-java-panama-ffm-xLRSX`

**Audience**: AI coding agents, platform engineers debugging Rust-to-Java FFM (Foreign Function and Memory) bindings in the rust4pm bridge.

**Goal**: Rapidly identify and fix layout mismatches between Rust struct sizes and Java `StructLayout` declarations at JVM startup.

---

## Problem Overview

Panama FFM requires Java to declare `StructLayout` objects that exactly match the binary memory layout of Rust C structs. Mismatches manifest in four ways:

1. **AssertionError on JVM startup** — `assertLayout()` detects sizeof mismatch
2. **Garbage data reads** — Wrong data types in fields or string encoding
3. **ClassCastException on field access** — Wrong `ValueLayout` for field type
4. **Illegal memory access** — Usually lifetime issues, not layout

This guide covers diagnostics and fixes for each.

---

## Symptom 1: AssertionError at JVM Startup (sizeof Mismatch)

### What You See

```
AssertionError: Layout mismatch for OcelEventC: Rust sizeof=32 but Java StructLayout.byteSize()=24
	at rust4pm_h.<clinit>():280
```

The JVM starts and immediately crashes before any code runs. You can't even call the native library.

### Root Cause

The Java `StructLayout` declaration does **not** match the actual Rust struct size. This is caught by the static initializer calling `rust4pm_sizeof_ocel_event_c()` and comparing it to `layout.byteSize()`.

### How to Diagnose

**Step 1: Read the error message exactly.**

Extract two numbers:
- **Rust sizeof**: 32 (from native `sizeof(OcelEventC)`)
- **Java byteSize()**: 24 (from your hand-written layout)

**Step 2: Check the Rust struct definition.**

Open `/home/user/yawl/rust/rust4pm/src/lib.rs` and find the struct:

```rust
#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelEventC {
    pub event_id:     *const c_char,      // 8 bytes (pointer)
    pub event_type:   *const c_char,      // 8 bytes (pointer)
    pub timestamp_ms: i64,                // 8 bytes (signed 64-bit)
    pub attr_count:   usize,              // 8 bytes (size_t = u64 on 64-bit)
}
```

**Total Rust size**: 8 + 8 + 8 + 8 = 32 bytes. No padding because all fields are naturally aligned.

**Step 3: Check the Java StructLayout.**

Open `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`:

```java
// WRONG: Missing attr_count field
public static final StructLayout OCEL_EVENT_C_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("event_id"),
        ADDRESS.withName("event_type"),
        JAVA_LONG.withName("timestamp_ms")
        // BUG: attr_count field is missing!
    ).withName("OcelEventC");
```

**Calculated Java size**: 8 + 8 + 8 = 24 bytes.

**Step 4: Verify field count and order.**

Count fields in Rust struct: 4 fields
Count fields in Java layout: 3 fields

Mismatch found. The `attr_count` field is missing from the Java layout.

### How to Fix

**Add the missing field to the Java layout:**

```java
// CORRECT
public static final StructLayout OCEL_EVENT_C_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("event_id"),
        ADDRESS.withName("event_type"),
        JAVA_LONG.withName("timestamp_ms"),
        JAVA_LONG.withName("attr_count")    // size_t = u64 on 64-bit → JAVA_LONG
    ).withName("OcelEventC");
```

Also add the VarHandle:

```java
public static final VarHandle OCEL_EVENT_C_ATTR_COUNT =
    OCEL_EVENT_C_LAYOUT.varHandle(groupElement("attr_count"));
```

**Re-run JVM startup**: The assertion should now pass.

---

## Symptom 2: Garbage String Reads

### What You See

```java
MemorySegment eventIdSeg = ...;
String id = eventIdSeg.getString(0);  // Reads as: "\u00A7Ô\u00D3\u00C4..."
```

You expect "e1" but get scrambled characters. The bytes are there, but the encoding is wrong.

### Root Cause

Two issues:

1. **String encoding**: `getString(0)` defaults to your JVM's platform encoding (usually not UTF-8). Rust always emits UTF-8.
2. **Pointer dereferencing**: If the field is a `*const c_char` pointer, you must first load the pointer as an `ADDRESS`, then reinterpret it as a memory segment, then read the string.

### How to Diagnose

**Step 1: Verify the Rust struct field.**

In `/home/user/yawl/rust/rust4pm/src/lib.rs`:

```rust
#[repr(C)]
pub struct OcelEventC {
    pub event_id:     *const c_char,      // pointer to UTF-8 C string
    ...
}
```

The field is `*const c_char`, which is a **pointer**. In Java FFM, this is `ADDRESS`.

**Step 2: Check the Java read code.**

```java
// WRONG
MemorySegment eventId = resultSeg.byteOffsetByteBuffer(0, 8);
String id = eventId.getString(0);  // Wrong: uses platform encoding, assumes direct buffer
```

**Step 3: Check the Java encoding.**

Ask: "Is `StandardCharsets.UTF_8` passed to `getString()`?"

Answer: No. Using platform default, which is likely UTF-8 on Linux but might not be elsewhere.

### How to Fix

**For pointer fields, load and dereference before reading:**

```java
// CORRECT: Load the pointer address
MemorySegment resultSeg = ...;  // Points to OcelEventC struct
long eventIdPtr = (long) OCEL_EVENT_C_EVENT_ID.get(resultSeg, 0L);

// Reinterpret the pointer as a readable memory segment
MemorySegment eventIdSeg = MemorySegment.ofAddress(eventIdPtr, Long.MAX_VALUE);

// Read string with explicit UTF-8 encoding
String id = eventIdSeg.getString(0, StandardCharsets.UTF_8);
```

Alternatively, if the pointer is already in a segment:

```java
// Load the pointer value from the struct field
MemorySegment eventIdPtr = (MemorySegment) OCEL_EVENT_C_EVENT_ID.get(resultSeg, 0L);

// Reinterpret to make it readable (Rust strings are lifetime-managed)
String id = eventIdPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
```

**Always specify `StandardCharsets.UTF_8`:**

```java
import java.nio.charset.StandardCharsets;

// ...

String id = eventIdSeg.getString(0, StandardCharsets.UTF_8);
```

**Test with a known string:**

If Rust emits `"o1"` (2 bytes UTF-8), you should read exactly those bytes.

---

## Symptom 3: ClassCastException on VarHandle.get()

### What You See

```
ClassCastException: Cannot cast [C to java.lang.Long
    at java.base/java.lang.invoke.VarHandle.get():0
```

You're trying to read a field, and the VarHandle says the value type is wrong. Usually happens when you use `ADDRESS` for a non-pointer field or vice versa.

### Root Cause

The VarHandle's `ValueLayout` (e.g., `ADDRESS`, `JAVA_LONG`, `JAVA_DOUBLE`) must **exactly match** the Rust field type:

| Rust Type | C Type | FFM ValueLayout | Java Type |
|-----------|--------|-----------------|-----------|
| `*const T` / `*mut T` | pointer | `ADDRESS` | `MemorySegment` or `long` |
| `u64` / `i64` | `int64_t` | `JAVA_LONG` | `long` |
| `usize` | `size_t` (u64 on 64-bit) | `JAVA_LONG` | `long` |
| `f64` | `double` | `JAVA_DOUBLE` | `double` |
| `u32` / `i32` | `int32_t` | `JAVA_INT` | `int` |
| `u8` / `i8` | `char` / `signed char` | `JAVA_BYTE` | `byte` |

### How to Diagnose

**Step 1: Read the error message.**

The exception tells you what type the VarHandle expected vs. what you gave it. Example:

```
ClassCastException: Cannot cast [C to java.lang.Long
```

This suggests the VarHandle is expecting `long`, but you're passing something else (possibly a `char[]` or array type).

**Step 2: Find the VarHandle definition.**

Search in `rust4pm_h.java` for the VarHandle name:

```java
public static final VarHandle OCEL_EVENT_C_TIMESTAMP_MS =
    OCEL_EVENT_C_LAYOUT.varHandle(groupElement("timestamp_ms"));
```

**Step 3: Trace back to the StructLayout field.**

```java
public static final StructLayout OCEL_EVENT_C_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("event_id"),
        ADDRESS.withName("event_type"),
        JAVA_LONG.withName("timestamp_ms"),  // <-- this field uses JAVA_LONG
        JAVA_LONG.withName("attr_count")
    ).withName("OcelEventC");
```

The layout says `timestamp_ms` is `JAVA_LONG` (8 bytes, signed 64-bit integer).

**Step 4: Check the Rust struct.**

```rust
#[repr(C)]
pub struct OcelEventC {
    pub event_id:     *const c_char,
    pub event_type:   *const c_char,
    pub timestamp_ms: i64,                // i64 = JAVA_LONG ✓
    pub attr_count:   usize,              // usize = JAVA_LONG on 64-bit ✓
}
```

The Rust field `timestamp_ms: i64` matches `JAVA_LONG`.

**Step 5: Check how you're reading it.**

```java
// WRONG: Reading a pointer as if it's a long
long ts = (long) OCEL_EVENT_C_EVENT_ID.get(resultSeg, 0L);  // BUG: using wrong VarHandle
```

vs.

```java
// CORRECT: Reading a long with the correct VarHandle
long ts = (long) OCEL_EVENT_C_TIMESTAMP_MS.get(resultSeg, 0L);
```

### How to Fix

**Match the VarHandle to the field type:**

| Wrong | Correct |
|-------|---------|
| `(long) OCEL_EVENT_C_EVENT_ID.get(...)` (reading pointer as long) | `(MemorySegment) OCEL_EVENT_C_EVENT_ID.get(...)` or reinterpret |
| `(double) OCEL_EVENT_C_TIMESTAMP_MS.get(...)` (reading long as double) | `(long) OCEL_EVENT_C_TIMESTAMP_MS.get(...)` |
| `(int) OCEL_EVENT_C_ATTR_COUNT.get(...)` (reading u64 as int) | `(long) OCEL_EVENT_C_ATTR_COUNT.get(...)` |

**Example: Correct field reading from a result struct:**

```java
// Load the ParseResult struct
MemorySegment parseResult = rust4pm_h.rust4pm_parse_ocel2_json(
    SegmentAllocator.nativeAllocator(), jsonSeg, jsonLen);

// Read the nested handle.ptr field (two levels deep)
MemorySegment handlePtr = (MemorySegment) rust4pm_h.PARSE_RESULT_HANDLE_PTR.get(parseResult, 0L);

// Read the error field
MemorySegment errorPtr = (MemorySegment) rust4pm_h.PARSE_RESULT_ERROR.get(parseResult, 0L);

// Check for error (null = success)
if (MemorySegment.NULL.equals(errorPtr)) {
    // Success: use handlePtr
} else {
    // Failure: read error string
    String errorMsg = errorPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
}
```

---

## Symptom 4: Nested Struct Field Access (Struct-in-Struct)

### What You See

You have a struct containing another struct (e.g., `ParseResult` contains `OcelLogHandle`), and accessing the nested field fails or returns garbage.

```
VarHandle path: groupElement("handle") then groupElement("ptr")
But getting: "No such path element" error
```

### Root Cause

Nested struct access requires:

1. The nested struct must be **embedded** in the parent layout (not a pointer)
2. The VarHandle path must use **chained `groupElement()` calls**
3. The field order in Java must match the Rust struct exactly

### How to Diagnose

**Step 1: Check the Rust struct.**

In `/home/user/yawl/rust/rust4pm/src/lib.rs`:

```rust
#[repr(C)]
pub struct ParseResult {
    handle: OcelLogHandle,      // <-- embedded struct (not a pointer)
    error:  *mut c_char,        // <-- pointer to error string
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelLogHandle {
    ptr: *mut OcelLogInternal,  // <-- pointer to opaque handle
}
```

`ParseResult` contains an **embedded** `OcelLogHandle`, not a pointer to it.

**Step 2: Check the C header.**

In `/home/user/yawl/rust/rust4pm/rust4pm.h`:

```c
typedef struct { void *ptr; } OcelLogHandle;

typedef struct {
    OcelLogHandle handle;     // <-- embedded struct
    char *error;
} ParseResult;
```

Same: embedded, not a pointer.

**Step 3: Check the Java layout.**

```java
public static final StructLayout OCEL_LOG_HANDLE_LAYOUT =
    MemoryLayout.structLayout(ADDRESS.withName("ptr"))
                .withName("OcelLogHandle");

public static final StructLayout PARSE_RESULT_LAYOUT =
    MemoryLayout.structLayout(
        OCEL_LOG_HANDLE_LAYOUT.withName("handle"),  // <-- embedded layout
        ADDRESS.withName("error")
    ).withName("ParseResult");
```

The Java layout correctly embeds `OCEL_LOG_HANDLE_LAYOUT` (not a reference to it).

**Step 4: Check the VarHandle path.**

```java
public static final VarHandle PARSE_RESULT_HANDLE_PTR =
    PARSE_RESULT_LAYOUT.varHandle(
        groupElement("handle"),   // Navigate to nested "handle" struct
        groupElement("ptr")       // Then to "ptr" field inside it
    );
```

The path is a **chain**: first `groupElement("handle")`, then `groupElement("ptr")`.

**Step 5: Calculate byte offsets to verify.**

- `PARSE_RESULT_LAYOUT.byteSize()` should be 16 (8 for handle + 8 for error pointer)
- `OCEL_LOG_HANDLE_LAYOUT.byteSize()` should be 8 (just the pointer)
- Offset of `handle` field: 0
- Offset of `ptr` inside `handle`: 0
- Offset of `error` field: 8

### How to Fix

**Ensure nested struct is embedded in the parent layout:**

```java
// WRONG: Treating nested struct as a separate pointer
public static final StructLayout PARSE_RESULT_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("handle"),          // BUG: Using ADDRESS instead of struct layout
        ADDRESS.withName("error")
    ).withName("ParseResult");
```

**CORRECT: Embed the nested struct layout:**

```java
public static final StructLayout PARSE_RESULT_LAYOUT =
    MemoryLayout.structLayout(
        OCEL_LOG_HANDLE_LAYOUT.withName("handle"),  // Embed the full struct
        ADDRESS.withName("error")
    ).withName("ParseResult");
```

**Use chained `groupElement()` for nested field access:**

```java
// WRONG: Trying to access nested field with single groupElement
VarHandle handlePtr = PARSE_RESULT_LAYOUT.varHandle(groupElement("handle"));

// CORRECT: Chain groupElement calls
VarHandle handlePtr = PARSE_RESULT_LAYOUT.varHandle(
    groupElement("handle"),
    groupElement("ptr")
);
```

**Reading a nested field:**

```java
MemorySegment parseResult = rust4pm_h.rust4pm_parse_ocel2_json(...);

// Read the nested handle.ptr field
MemorySegment ptrValue = (MemorySegment) rust4pm_h.PARSE_RESULT_HANDLE_PTR.get(parseResult, 0L);

// Use it to dereference the OcelLogInternal
if (!MemorySegment.NULL.equals(ptrValue)) {
    // ptrValue is a valid handle
}
```

---

## Quick Diagnostic Checklist

When you see a layout mismatch or data corruption, run through this checklist in order:

### Startup AssertionError

- [ ] Extract Rust sizeof and Java byteSize from error message
- [ ] Count struct fields in Rust source vs. Java layout
- [ ] Verify field order matches exactly (same sequence in both)
- [ ] Check for padding in Rust: do fields align naturally?
- [ ] Ensure all pointer fields use `ADDRESS` and all integer fields use `JAVA_LONG` / `JAVA_INT`
- [ ] Run `std::mem::size_of::<StructName>()` in Rust to confirm actual size
- [ ] Add missing fields or fix ValueLayout types in Java

### Garbage String Reads

- [ ] Verify the field is a `*const c_char` pointer (not a `[u8; N]` array)
- [ ] Load the pointer address using the correct `VarHandle`
- [ ] Reinterpret the pointer as a `MemorySegment` using `MemorySegment.ofAddress()` or `.reinterpret()`
- [ ] Call `getString(0, StandardCharsets.UTF_8)` with explicit UTF-8 encoding
- [ ] Test with a known string value (e.g., "o1", "e1")

### ClassCastException on VarHandle.get()

- [ ] Match the Rust field type to the correct FFM `ValueLayout`:
  - Pointers → `ADDRESS`
  - `i64` / `u64` / `usize` → `JAVA_LONG`
  - `f64` → `JAVA_DOUBLE`
  - `i32` / `u32` → `JAVA_INT`
- [ ] Ensure you're reading with the correct VarHandle (not mixing up field names)
- [ ] Verify cast matches the expected return type of the VarHandle

### Nested Struct Fields

- [ ] Confirm the nested struct is **embedded** in Rust (not a pointer)
- [ ] Confirm the Java layout **embeds** the nested layout (not `ADDRESS`)
- [ ] Use **chained** `groupElement()` calls in the VarHandle path
- [ ] Calculate byte offsets: offset of nested struct + offset of field within it

---

## Real-World Example: Fixing ParseResult Layout

Here's a complete example of diagnosing and fixing a ParseResult layout issue.

### Starting State (BROKEN)

**Rust struct** (correct):
```rust
#[repr(C)]
pub struct OcelLogHandle {
    ptr: *mut OcelLogInternal,
}

#[repr(C)]
pub struct ParseResult {
    handle: OcelLogHandle,
    error:  *mut c_char,
}
```

**Java layout** (BROKEN: missing nested struct):
```java
public static final StructLayout PARSE_RESULT_LAYOUT =
    MemoryLayout.structLayout(
        ADDRESS.withName("handle"),  // BUG: Should embed OcelLogHandle, not use ADDRESS
        ADDRESS.withName("error")
    ).withName("ParseResult");

public static final VarHandle PARSE_RESULT_HANDLE_PTR =
    PARSE_RESULT_LAYOUT.varHandle(groupElement("ptr"));  // BUG: Can't navigate to nested field
```

**Startup error**:
```
AssertionError: Layout mismatch for ParseResult: Rust sizeof=16 but Java StructLayout.byteSize()=16
```

Wait—sizes match! So the error must be elsewhere. Let me recalculate:

- Rust: 8 (handle.ptr pointer) + 8 (error pointer) = 16 bytes ✓
- Java: 8 (ADDRESS for handle) + 8 (ADDRESS for error) = 16 bytes ✓

The sizes happen to match **by accident**, but the layout is still wrong because:

1. `handle` should be an **embedded** struct, not just an `ADDRESS`
2. The VarHandle path is incorrect

**At runtime**, accessing the nested field fails:

```java
// Attempt to read handle.ptr
MemorySegment ptrValue = (MemorySegment) PARSE_RESULT_HANDLE_PTR.get(parseResult, 0L);
// Error: "No such path element: ptr"
```

### Fixed State

**Java layout** (CORRECT):
```java
public static final StructLayout OCEL_LOG_HANDLE_LAYOUT =
    MemoryLayout.structLayout(ADDRESS.withName("ptr"))
                .withName("OcelLogHandle");

public static final StructLayout PARSE_RESULT_LAYOUT =
    MemoryLayout.structLayout(
        OCEL_LOG_HANDLE_LAYOUT.withName("handle"),  // FIX: Embed the full struct
        ADDRESS.withName("error")
    ).withName("ParseResult");

public static final VarHandle PARSE_RESULT_HANDLE_PTR =
    PARSE_RESULT_LAYOUT.varHandle(
        groupElement("handle"),   // FIX: Navigate to nested struct
        groupElement("ptr")       // FIX: Then to field within it
    );
```

**At runtime**, nested field access now works:

```java
MemorySegment parseResult = rust4pm_h.rust4pm_parse_ocel2_json(...);
MemorySegment handlePtr = (MemorySegment) rust4pm_h.PARSE_RESULT_HANDLE_PTR.get(parseResult, 0L);
// Success: handlePtr is now valid
```

---

## Verification Commands

After fixing a layout, verify it before committing:

### Build and Test

```bash
# Rebuild the bridge
bash /home/user/yawl/scripts/build-rust4pm.sh

# Run unit tests (verify sizeof probes pass)
bash /home/user/yawl/scripts/dx.sh -pl yawl-rust4pm test
```

### Manual Verification

To manually check a struct size in Rust:

```rust
fn main() {
    println!("ParseResult: {}", std::mem::size_of::<ParseResult>());
    println!("OcelEventC: {}", std::mem::size_of::<OcelEventC>());
}
```

To manually check a layout in Java:

```java
System.out.println("ParseResult.byteSize() = " + PARSE_RESULT_LAYOUT.byteSize());
System.out.println("OcelEventC.byteSize() = " + OCEL_EVENT_C_LAYOUT.byteSize());
```

They should be equal.

---

## Summary

| Symptom | Root Cause | Fix |
|---------|------------|-----|
| AssertionError on startup | Field missing or wrong ValueLayout in Java | Add field to layout or change ValueLayout type |
| Garbage string reads | Wrong encoding or pointer not dereferenced | Use `getString(0, UTF_8)` and load pointer first |
| ClassCastException on VarHandle.get() | Wrong ValueLayout for field type | Match Rust type to correct FFM ValueLayout |
| Nested struct field access fails | Using ADDRESS instead of embedding layout | Embed struct layout in parent, use chained groupElement() |

---

## Related Files

- **Rust source**: `/home/user/yawl/rust/rust4pm/src/lib.rs`
- **C header**: `/home/user/yawl/rust/rust4pm/rust4pm.h`
- **Java bindings**: `/home/user/yawl/yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`
- **Build script**: `/home/user/yawl/scripts/build-rust4pm.sh`
- **Regeneration**: `bash scripts/jextract-generate.sh` (from Rust header)

---

**Last updated**: 2026-03-01 | **Status**: Ready for production use
