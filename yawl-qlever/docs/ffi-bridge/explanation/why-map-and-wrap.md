# Why the Map-and-Wrap Pattern

**Diátaxis quadrant: Explanation** — Concepts and reasoning, not steps.

---

## The Problem

Java's Panama FFM API can call any native function directly. The temptation is to
call C++ functions straight from Java — but that breaks immediately in practice:

- **C++ has name mangling.** `Index::query(std::string)` becomes
  `_ZN5Index5queryB5cxx11St6stringE` or similar (ABI-defined, compiler-specific).
  You cannot predict or hard-code this.
- **C++ throws exceptions across the FFI boundary.** An unhandled
  `std::runtime_error` crossing from C++ into Java via FFM produces
  undefined behaviour. Java sees a corrupt stack or a SIGSEGV.
- **C++ types do not exist in C.** `std::string`, `std::vector`,
  `std::unique_ptr`, virtual dispatch tables — none of these are representable
  as `FunctionDescriptor` value layouts.
- **C++ ABIs are unstable.** Changing the compiler version, flags, or even
  adding a virtual method reorders the vtable and silently breaks all callers.

These problems are not bugs to fix. They are fundamental properties of C++.

---

## The Insight: Hourglass Through C

C has a stable, well-defined ABI that every language knows. Functions with
`extern "C"` linkage:

- Have **predictable names** (no mangling).
- Take and return **primitive types** and **opaque pointers** (both expressible
  as Panama `ValueLayout`).
- **Never throw** (C has no exceptions; exceptions from C++ must be caught
  before returning from any `extern "C"` function).

The Map-and-Wrap pattern exploits this by inserting a thin C façade between the
C++ core and Java:

```
Java (rich, safe API)        ← the "wrap" layer
       ↕ Panama FFM
C façade (extern "C")        ← the "waist" of the hourglass
       ↕ C++ calls
C++ engine (complex core)    ← the "map" layer
```

The hourglass shape is deliberate: many Java callers and many C++ internals
share **one narrow stable interface**.

---

## What "Map" Means

**Mapping** is the work done inside `qlever_ffi.cpp` (or your equivalent file):

- Convert a `const char*` SPARQL string into a `std::string` for the C++ parser.
- Convert a C `QleverMediaType` enum value into a C++ serialisation call.
- Convert a `QLeverResult*` opaque pointer into accesses on the internal
  `std::vector<std::string>` it points to.

The C layer *maps* between the C world (pointers, ints, char arrays) and the
C++ world (objects, templates, RAII, exceptions).

---

## What "Wrap" Means

**Wrapping** is the work done in `QLeverFfiBindings.java` and
`QLeverEmbeddedSparqlEngine.java`:

- Convert a Java `String` into a `MemorySegment` (C string) using an `Arena`.
- Convert a C enum integer into a `QLeverMediaType` Java enum `.ordinal()`.
- Convert a returned `MemorySegment` opaque pointer into a meaningful
  `QLeverStatus` record.
- Convert a `Throwable` from `MethodHandle.invokeExact()` into a
  `QLeverFfiException`.

The Java layer *wraps* raw FFM machinery in idiomatic Java.

---

## Why Not Just jextract the C++ Headers?

`jextract` works on C headers. If you run it on a C++ header it can sometimes
produce bindings for POD types and `extern "C"` functions, but:

1. Templates, namespaces, vtables, and `std::` types are not supported.
2. Name-mangled symbols cannot be looked up by `SymbolLookup`.
3. jextract produces no exception safety.

The C façade *makes C++ look like C* so that jextract and `Linker` work
correctly. This is the only reliable approach.

---

## Opaque Pointers and Encapsulation

The C façade exposes handles like `QLeverIndex*` and `QLeverResult*` as
**opaque pointers** — the Java side never knows the size or layout of the
struct, only its address.

This is intentional:

- The C++ implementation can change freely (swap `std::unique_ptr` for a
  custom allocator, add fields, change alignment) without recompiling Java.
- Java never directly reads or writes native struct fields, so there is no
  `MemoryLayout` to keep in sync with the C++ struct definition.
- Ownership is explicit: whoever calls `qlever_index_create()` owns the
  pointer and must call `qlever_index_destroy()`. Java `AutoCloseable` enforces
  this at the source level.

---

## ABI Stability

A C façade with only:

- Opaque pointer types
- Primitive parameters (`int`, `size_t`, `char*`)
- Status-code error returns

...can remain stable across C++ compiler upgrades, library refactors, and
addition of new internal features — as long as the *semantics* of each function
are preserved. This is the same principle as `libc`.

Contrast with a direct Java ↔ C++ bridge: any change to the C++ class layout
(adding a field, changing `virtual` dispatch) silently breaks all callers.

---

## See Also

- [Tutorial: Bridge Your First C++ Library](../tutorial/bridge-first-cpp-library.md)
- [Reference: Type Mapping](../reference/type-mapping.md)
- [Explanation: Memory Ownership](memory-ownership.md)
