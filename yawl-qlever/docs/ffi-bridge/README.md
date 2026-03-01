# FFI Bridge: Map and Wrap Pattern

Diátaxis documentation for the YAWL Map-and-Wrap pattern — how to bridge a
C++ library to Java using Panama FFM through a stable C façade.

**The pattern is the same every time.** Replicate it for any C++ library by
following the guides below.

---

## Five-Layer Architecture

```
Layer 5 │ YourEngine.java          User-facing AutoCloseable Java API
Layer 4 │ YourFfiBindings.java     Safe Java wrapper (Arena, MethodHandle)
Layer 3 │ your_lib_ffi_h.java      jextract-generated raw FFI descriptors
────────┼──────────────────────────────────────────────────────────────
Layer 2 │ your_lib_ffi.cpp         C++ try/catch, exception translation
Layer 1 │ your_lib_ffi.h           Stable C façade (extern "C", opaque ptrs)
Layer 0 │ YourCppLib               The C++ library being wrapped
```

The QLever SPARQL engine (`QLeverEmbeddedSparqlEngine`) is the canonical
implementation. Read its source alongside these docs.

---

## Learning Path

### New to this pattern? Start here:

1. **[Explanation: Why Map and Wrap?](explanation/why-map-and-wrap.md)**
   — Understand why the pattern exists and why alternatives fail.

2. **[Tutorial: Bridge Your First C++ Library](tutorial/bridge-first-cpp-library.md)**
   — Complete walkthrough, counter example, all five layers.

### Doing a specific task:

- **[How-to: Write a C Façade](how-to/write-c-facade.md)**
  — Step-by-step: header + cpp file for any C++ class.

- **[How-to: Handle C++ Exceptions](how-to/handle-cpp-exceptions.md)**
  — The Lippincott pattern; never let exceptions cross `extern "C"`.

- **[How-to: Map C++ Structs to Java MemoryLayout](how-to/map-cpp-structs.md)**
  — When Java needs to read struct fields directly.

- **[How-to: Manage Arena Memory](how-to/manage-arena-memory.md)**
  — Per-call vs. shared arenas; all six allocation patterns.

- **[How-to: Generate Java Bindings with jextract](how-to/generate-java-bindings.md)**
  — Running jextract; writing bindings by hand when unavailable.

### Need exact syntax:

- **[Reference: Type Mapping](reference/type-mapping.md)**
  — C type → `ValueLayout` → Java carrier type. Full table.

- **[Reference: FunctionDescriptor Patterns](reference/functiondescriptor-patterns.md)**
  — 12 patterns covering every C function shape.

- **[Reference: C Façade Patterns](reference/c-facade-patterns.md)**
  — 9 façade patterns: lifecycle, iterator, boolean, string I/O, arrays, structs.

### Deeper understanding:

- **[Explanation: Memory Ownership](explanation/memory-ownership.md)**
  — Two heaps, four ownership rules, lifecycle diagram, failure table.

---

## Quick Checklist for a New Bridge

```
C side:
  [ ] qlever_ffi.h   — only extern "C", opaque pointers, primitive types
  [ ] qlever_ffi.cpp — every function has clear/guard/try/catch(std::exception)/catch(...)
  [ ] nm -D libfoo.so | grep ' T '  — symbols appear without mangling

Java side:
  [ ] foo_ffi_h.java       — one FunctionDescriptor per function; cached MethodHandles
  [ ] FooFfiBindings.java  — Arena.ofConfined() per call; Arena.ofShared() for object lifetime
  [ ] FooEngine.java       — AutoCloseable; destroy + close in close()
  [ ] Unit test            — create/use/close with no native crash
```

---

## QLever as the Reference Implementation

| Bridge file | Layer | What to learn from it |
|------------|-------|----------------------|
| `yawl-qlever/src/main/native/qlever_ffi.h` | 1 | Status type, opaque pointers, extern "C" |
| `yawl-qlever/src/main/native/qlever_ffi.cpp` | 2 | Lippincott pattern, iterator impl |
| `src/org/yawlfoundation/yawl/qlever/ffi/qlever_ffi_h.java` | 3 | FunctionDescriptor per function |
| `src/org/yawlfoundation/yawl/qlever/QLeverFfiBindings.java` | 4 | Arena patterns, MethodHandle caching |
| `src/org/yawlfoundation/yawl/qlever/QLeverEmbeddedSparqlEngine.java` | 5 | AutoCloseable, null guards, result drain |
