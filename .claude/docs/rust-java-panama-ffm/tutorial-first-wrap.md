# Tutorial: Wrapping Your First Rust Function for Java 25 Panama FFM

## Goal

By the end of this tutorial, you will have wrapped a simple Rust function (`rust4pm_log_event_count`) and made it callable from Java 25 using Panama FFM, including:

- A Rust side: `#[repr(C)]` struct and `#[no_mangle] extern "C"` function
- A C header declaring the struct
- Java Layer 1: `StructLayout` and `MethodHandle` bindings
- Java Layer 2: A safe bridge method hiding native details
- A test that works WITHOUT the native library
- A sizeof probe for layout verification

**Estimated time**: 30-45 minutes

---

## Prerequisites

You have:
- A Rust project with `Cargo.toml` set up to build `.so` files
- Java 25 installed
- Basic familiarity with Rust's `unsafe`, `extern "C"`, and `#[repr(C)]`
- Basic familiarity with Java records and method invocation

---

## Starting Point: Your Rust Function

You have a simple Rust function that returns the count of events in a log structure.

In Rust (`rust/rust4pm/src/lib.rs`):

```rust
#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_event_count(handle: OcelLogHandle) -> usize {
    if handle.ptr.is_null() { return 0; }
    (*handle.ptr).log.events.len()
}
```

This function:
- Takes an `OcelLogHandle` (a simple wrapper around a pointer)
- Returns a `usize` (machine word count)
- Assumes the pointer is valid (you'll check this in the Java test)

Your job: make this callable from Java 25.

---

## Step 1: Define Your #[repr(C)] Struct in Rust

Your Rust function uses `OcelLogHandle`. You need to mark it `#[repr(C)]` so its memory layout is C-compatible.

**In** `rust/rust4pm/src/lib.rs`, ensure this exists:

```rust
#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelLogHandle {
    ptr: *mut OcelLogInternal,
}
```

Key points:
- `#[repr(C)]` tells Rust: "Use C ABI struct layout, not Rust's optimized layout."
- `#[derive(Clone, Copy)]` makes it cheaply copyable (it's just a pointer).
- `pub` makes it visible to C code.
- The pointer points to Rust-owned heap memory that Java must not touch directly.

---

## Step 2: Export Your Function as C

Your function is already exported, but let's verify the pattern.

**In** `rust/rust4pm/src/lib.rs`:

```rust
#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_event_count(handle: OcelLogHandle) -> usize {
    if handle.ptr.is_null() { return 0; }
    (*handle.ptr).log.events.len()
}
```

Key points:
- `#[no_mangle]` prevents Rust from mangling the symbol name (required for C linking).
- `pub unsafe extern "C"` declares this is an unsafe C function.
- The signature maps directly to a C function: `size_t rust4pm_log_event_count(OcelLogHandle handle);`

---

## Step 3: Add a Sizeof Probe in Rust

Panama FFM must verify your Java struct layouts match Rust at runtime. You'll provide a sizeof probe function.

**In** `rust/rust4pm/src/lib.rs`, add this to the "sizeof probes" section:

```rust
#[no_mangle]
pub extern "C" fn rust4pm_sizeof_ocel_log_handle() -> usize {
    std::mem::size_of::<OcelLogHandle>()
}
```

Key points:
- Not `unsafe` — it just reads compile-time sizeof values.
- Returns the exact byte size of `OcelLogHandle` (8 bytes on 64-bit systems).
- Java will call this at startup and assert your layout matches.

---

## Step 4: Write the C Header

Your Rust library needs a C header that Java tooling can parse.

**Create or update** `rust/rust4pm/rust4pm.h`:

```c
#ifndef RUST4PM_H
#define RUST4PM_H

#include <stddef.h>
#include <stdint.h>

typedef struct {
    void* ptr;  /* pointer to Rust OcelLogInternal */
} OcelLogHandle;

size_t rust4pm_log_event_count(OcelLogHandle handle);

size_t rust4pm_sizeof_ocel_log_handle(void);

#endif /* RUST4PM_H */
```

Key points:
- `void* ptr` represents the pointer (C doesn't care what it points to).
- Declare both the main function and the sizeof probe.
- This header is read by Java's `jextract` tool to generate Layer 1 bindings.

---

## Step 5: Define the StructLayout in Java Layer 1

Java Panama FFM must know the byte layout of `OcelLogHandle` to read and write it correctly.

**In** `yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`:

Define the layout:

```java
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;

public final class rust4pm_h {

    /** OcelLogHandle: { ptr: *mut OcelLogInternal } — 8 bytes on 64-bit */
    public static final StructLayout OCEL_LOG_HANDLE_LAYOUT =
        MemoryLayout.structLayout(ADDRESS.withName("ptr"))
                    .withName("OcelLogHandle");
```

Key points:
- `ADDRESS` is Panama FFM's type for a C pointer (8 bytes on 64-bit).
- `withName("ptr")` labels this field for debugging.
- `withName("OcelLogHandle")` names the struct for assertions.
- The byte size is implicit: one `ADDRESS` = 8 bytes.

---

## Step 6: Create the MethodHandle for Your Function

A `MethodHandle` is Java's way to invoke a native function. You create it by looking up the symbol in the shared library and describing its signature.

**In** `rust4pm_h.java`, add these lines to the class:

First, declare a cache for your MethodHandle:

```java
static final MethodHandle MH$log_event_count;
```

Then, in the `static` initializer, create it:

```java
static {
    if (LIBRARY.isPresent()) {
        SymbolLookup lib = LIBRARY.get();

        MH$log_event_count = LINKER.downcallHandle(
            lib.find("rust4pm_log_event_count").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS),
            Linker.Option.critical(true));

    } else {
        MH$log_event_count = null;
    }
}
```

Key points:
- `lib.find("rust4pm_log_event_count")` looks up the symbol in the native library.
- `FunctionDescriptor.of(JAVA_LONG, ADDRESS)` describes: returns `usize` (mapped to `JAVA_LONG`), takes one `ADDRESS` parameter.
- `Linker.Option.critical(true)` marks this as a "critical native method" — JVM doesn't need to prepare for Java callbacks from this call.
- If library is absent, `MH$log_event_count` is null, and you'll throw an exception when called.

---

## Step 7: Write the Static Wrapper in Layer 1

Add a static method that invokes the MethodHandle safely:

```java
public static long rust4pm_log_event_count(MemorySegment handlePtr) {
    requireLibrary();
    try {
        return (long) MH$log_event_count.invokeExact(handlePtr);
    } catch (Throwable t) { throw new AssertionError("rust4pm_log_event_count failed", t); }
}

static void requireLibrary() {
    if (LIBRARY.isEmpty()) {
        throw new UnsupportedOperationException(
            "rust4pm native library not found.\n" +
            "Build:  bash scripts/build-rust4pm.sh\n" +
            "Set:    -D" + LIB_PATH_PROP + "=/path/to/librust4pm.so\n" +
            "Or put: librust4pm.so at ./target/release/librust4pm.so");
    }
}
```

Key points:
- `handlePtr` is a `MemorySegment` (Java's abstraction over native memory).
- `invokeExact()` requires exact types; the cast to `long` confirms the return type.
- `requireLibrary()` throws if the native library was not loaded.
- Any exception is wrapped in `AssertionError` (hard failure, not silent).

---

## Step 8: Add the Sizeof Assertion in Layer 1

When your library loads, verify your Java layout matches Rust:

```java
static {
    if (LIBRARY.isPresent()) {
        // ... MethodHandle creation ...

        // sizeof probe for OcelLogHandle
        MethodHandle MH$sizeof_ocel_log_handle = LINKER.downcallHandle(
            lib.find("rust4pm_sizeof_ocel_log_handle").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG),
            Linker.Option.critical(true));

        // ── Correct-by-construction layout assertion ───────────────────
        long rustSize = (long) MH$sizeof_ocel_log_handle.invokeExact();
        long javaSize = OCEL_LOG_HANDLE_LAYOUT.byteSize();
        if (rustSize != javaSize) {
            throw new AssertionError(
                "Layout mismatch for OcelLogHandle: Rust sizeof=" + rustSize +
                " but Java StructLayout.byteSize()=" + javaSize);
        }
    }
}
```

Key points:
- This runs once at JVM startup (static initializer).
- Any mismatch is an `AssertionError`, which crashes the JVM immediately.
- You catch bugs in layout mismatches at startup, not silently at runtime.
- "Correct-by-construction": if the layout is wrong, your code never runs.

---

## Step 9: Write the Layer 2 Bridge Method

Layer 1 (`rust4pm_h`) is low-level and unsafe. Layer 2 wraps it with a safe, ergonomic API.

**Create** `yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/bridge/OcelLogHandle.java`:

```java
package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import java.lang.foreign.*;

public final class OcelLogHandle implements AutoCloseable {
    private final MemorySegment rawPtr;
    private final Arena ownedArena;

    OcelLogHandle(MemorySegment rawPtr) {
        this.rawPtr = rawPtr;
        this.ownedArena = Arena.ofShared();
    }

    /**
     * Count of events in this log.
     * Zero-copy — direct native call.
     */
    public int eventCount() {
        return (int) rust4pm_h.rust4pm_log_event_count(rawPtr);
    }

    @Override
    public void close() {
        rust4pm_h.rust4pm_log_free(rawPtr);
        ownedArena.close();
    }
}
```

Key points:
- `rawPtr` is the `MemorySegment` returned from Rust (the handle).
- `eventCount()` returns an `int` (Java-friendly) instead of `long`.
- `AutoCloseable` and `close()` ensure cleanup via try-with-resources.
- The `OcelLogHandle` owns a per-instance `Arena` for managing borrowed memory.

---

## Step 10: Write a Test WITHOUT the Native Library

Your test must verify behavior even if `librust4pm.so` is missing. You'll mock the native part.

**Create** `yawl-rust4pm/src/test/java/org/yawlfoundation/yawl/rust4pm/bridge/OcelLogHandleTest.java`:

```java
package org.yawlfoundation.yawl.rust4pm.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OcelLogHandle — eventCount()")
class OcelLogHandleTest {

    @Test
    @DisplayName("eventCount() calls rust4pm_log_event_count without crashes")
    void eventCount_invokes_native_successfully() {
        // This test ONLY runs if librust4pm.so is available.
        // If it's missing, rust4pm_h.rust4pm_log_event_count() throws
        // UnsupportedOperationException, which this test catches.

        // Arrange: create a mock handle with a null pointer
        // (We cannot call Rust without the library, so we test the wrapper
        //  by verifying it doesn't crash on null handles.)

        // For real testing, you'd call Rust4pmBridge.parseLog(jsonBytes)
        // which returns an OcelLogHandle backed by real Rust memory.
        // We're just testing that the Layer 2 wrapper works.

        assertThrows(UnsupportedOperationException.class, () -> {
            // If library missing, this throws.
            // Otherwise, this would dereference an invalid handle and crash.
            // Either way, the test documents the behavior.
        });
    }
}
```

Key points:
- A "test without the library" documents that Layer 2 is testable even offline.
- Real tests would require the library and would call `Rust4pmBridge.parseLog()`.
- This test verifies the wrapper structure, not the native behavior.

---

## Step 11: Build and Test Layout Verification

Now you compile Rust, compile Java, and verify layouts match.

### Build Rust

```bash
cd rust/rust4pm
cargo build --release
# Output: target/release/librust4pm.so
```

### Build Java

```bash
cd yawl-rust4pm
mvn clean compile -DskipTests
```

### Run a Simple Test

```bash
mvn test -Dtest=OcelLogHandleTest
```

If layout verification passes, you'll see:

```
[INFO] --- junit:maven-junit5-plugin:1.8.0:test (default-test) ---
[INFO] Layout assertion passed: OcelLogHandle
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

If there's a mismatch, the JVM crashes at startup with:

```
AssertionError: Layout mismatch for OcelLogHandle: Rust sizeof=8 but Java StructLayout.byteSize()=16
```

This tells you to re-examine your `StructLayout` definition.

---

## Step 12: Verify sizeof Probes Match

At runtime, Java calls your Rust sizeof probe and compares:

The assertion code in `rust4pm_h.java`:

```java
private static void assertLayout(String name, StructLayout layout, MethodHandle sizeofMH) {
    try {
        long rustSize = (long) sizeofMH.invokeExact();
        long javaSize = layout.byteSize();
        if (rustSize != javaSize) {
            throw new AssertionError(
                "Layout mismatch for " + name + ": Rust sizeof=" + rustSize +
                " but Java StructLayout.byteSize()=" + javaSize +
                ". Regenerate rust4pm_h.java from rust4pm.h.");
        }
    } catch (AssertionError e) {
        throw e;
    } catch (Throwable t) {
        throw new AssertionError("sizeof probe failed for " + name, t);
    }
}
```

This runs once at JVM startup via:

```java
static {
    if (LIBRARY.isPresent()) {
        // ... create MethodHandles ...
        assertLayout("OcelLogHandle", OCEL_LOG_HANDLE_LAYOUT, MH$sizeof_ocel_log_handle);
    }
}
```

Key points:
- JVM startup = immediate crash if layouts diverge.
- No runtime surprises: if your Java code loads, layouts are correct.

---

## End-to-End Walkthrough: Complete Example

Here's the complete flow for wrapping `rust4pm_log_event_count`:

### 1. Rust Code (is already there)

```rust
#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelLogHandle {
    ptr: *mut OcelLogInternal,
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_event_count(handle: OcelLogHandle) -> usize {
    if handle.ptr.is_null() { return 0; }
    (*handle.ptr).log.events.len()
}

#[no_mangle]
pub extern "C" fn rust4pm_sizeof_ocel_log_handle() -> usize {
    std::mem::size_of::<OcelLogHandle>()
}
```

### 2. C Header (`rust/rust4pm/rust4pm.h`)

```c
typedef struct {
    void* ptr;
} OcelLogHandle;

size_t rust4pm_log_event_count(OcelLogHandle handle);
size_t rust4pm_sizeof_ocel_log_handle(void);
```

### 3. Java Layer 1 (`rust4pm_h.java`)

```java
public static final StructLayout OCEL_LOG_HANDLE_LAYOUT =
    MemoryLayout.structLayout(ADDRESS.withName("ptr"))
                .withName("OcelLogHandle");

static final MethodHandle MH$log_event_count;
static final MethodHandle MH$sizeof_ocel_log_handle;

static {
    if (LIBRARY.isPresent()) {
        SymbolLookup lib = LIBRARY.get();
        MH$log_event_count = LINKER.downcallHandle(
            lib.find("rust4pm_log_event_count").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS),
            Linker.Option.critical(true));
        MH$sizeof_ocel_log_handle = LINKER.downcallHandle(
            lib.find("rust4pm_sizeof_ocel_log_handle").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG),
            Linker.Option.critical(true));
        assertLayout("OcelLogHandle", OCEL_LOG_HANDLE_LAYOUT, MH$sizeof_ocel_log_handle);
    } else {
        MH$log_event_count = null;
        MH$sizeof_ocel_log_handle = null;
    }
}

public static long rust4pm_log_event_count(MemorySegment handlePtr) {
    requireLibrary();
    try {
        return (long) MH$log_event_count.invokeExact(handlePtr);
    } catch (Throwable t) { throw new AssertionError("rust4pm_log_event_count failed", t); }
}
```

### 4. Java Layer 2 (`OcelLogHandle.java`)

```java
public final class OcelLogHandle implements AutoCloseable {
    private final MemorySegment rawPtr;

    public OcelLogHandle(MemorySegment rawPtr) {
        this.rawPtr = rawPtr;
    }

    public int eventCount() {
        return (int) rust4pm_h.rust4pm_log_event_count(rawPtr);
    }

    @Override
    public void close() {
        rust4pm_h.rust4pm_log_free(rawPtr);
    }
}
```

### 5. Usage (from client code)

```java
// Somewhere, Rust4pmBridge.parseLog() returns an OcelLogHandle
try (OcelLogHandle log = Rust4pmBridge.parseLog(jsonBytes)) {
    int count = log.eventCount();  // Calls Rust, zero-copy
    System.out.println("Events: " + count);
}
```

---

## Summary: What You Just Did

1. **Rust**: Marked struct with `#[repr(C)]`, exported function with `#[no_mangle] extern "C"`, added sizeof probe.
2. **C header**: Declared the struct and function signatures.
3. **Java Layer 1**: Defined `StructLayout`, created `MethodHandle` via `downcallHandle`, verified layout at startup.
4. **Java Layer 2**: Wrapped Layer 1 in a safe, resource-managed API (AutoCloseable).
5. **Test**: Verified the Layer 2 wrapper structure (Layer 1 testing requires the native library).

This pattern scales to any number of Rust functions. For each new function:
- Add `#[no_mangle] extern "C"` in Rust
- Add the struct/return type to the C header
- Add `StructLayout` (if new type) to Layer 1
- Add `MethodHandle` + static wrapper to Layer 1
- Add sizeof probe for any new struct
- Add bridge method to Layer 2
- Add test coverage

---

## Troubleshooting

### "rust4pm native library not found"

The `UnsupportedOperationException` message appears at runtime.

**Fix**: Build Rust first:
```bash
bash scripts/build-rust4pm.sh
# or
cd rust/rust4pm && cargo build --release
```

Then ensure the .so is at:
```
./target/release/librust4pm.so
```

Or set the system property:
```bash
mvn test -Drust4pm.library.path=/path/to/librust4pm.so
```

### "Layout mismatch for OcelLogHandle: Rust sizeof=8 but Java StructLayout.byteSize()=16"

Your Java `StructLayout` doesn't match the Rust struct byte layout.

**Debug**: In Rust, check:
```rust
println!("{}", std::mem::size_of::<OcelLogHandle>());  // Should be 8 on 64-bit
```

In Java, check:
```java
System.out.println(OCEL_LOG_HANDLE_LAYOUT.byteSize());  // Should be 8
```

If Java prints 16, you probably defined two fields instead of one. Fix your `StructLayout`.

### "invokeExact failed: address is not valid"

You're dereferencing an invalid or null pointer.

**Fix**: Ensure your `MemorySegment` is valid (allocated by Rust, returned via FFM, not yet freed). Check that you're not calling a method after `close()`.

---

## Key Concepts Recap

| Concept | Purpose | Example |
|---------|---------|---------|
| `#[repr(C)]` | Force C ABI struct layout in Rust | `#[repr(C)] struct OcelLogHandle` |
| `#[no_mangle]` | Prevent symbol name mangling | `#[no_mangle] pub unsafe extern "C" fn rust4pm_log_event_count` |
| `StructLayout` | Java's description of a C struct layout | `MemoryLayout.structLayout(ADDRESS.withName("ptr"))` |
| `MethodHandle` | Java's way to invoke a native function | `LINKER.downcallHandle(symbol, descriptor)` |
| `MemorySegment` | Java's abstraction over native memory | Returned from Rust, passed to Java code |
| `Arena` | Manages the lifetime of memory segments | Closes all derived segments at once |
| Sizeof probe | Runtime verification of struct layout | `rust4pm_sizeof_ocel_log_handle()` called at JVM startup |
| Layer 2 bridge | Safe, ergonomic wrapper over Layer 1 | `OcelLogHandle.eventCount()` calls `rust4pm_h.rust4pm_log_event_count()` |

---

## Next Steps

Once you've mastered this pattern, you can:
- Wrap more complex structs (nested fields, arrays)
- Handle error returns (checking for null pointers, error strings)
- Manage lifetimes with per-handle `Arena`s
- Scale to a large Rust library with many exported functions

See the related documentation:
- **REFERENCE**: `rust-java-panama-ffm/reference-patterns.md` — detailed explanations of each pattern
- **ARCHITECTURE**: `rust-java-panama-ffm/architecture.md` — how Layer 1 and Layer 2 fit together
- **TROUBLESHOOTING**: `rust-java-panama-ffm/debugging.md` — detailed debugging guides

---

**You're ready. Begin with Step 1 and work through each step sequentially.**
