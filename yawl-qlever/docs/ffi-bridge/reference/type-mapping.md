# Reference: C ↔ Java Type Mapping

**Diátaxis quadrant: Reference** — Look up, don't read top-to-bottom.

---

## Primitive Types

| C type | Bits | Java `ValueLayout` | Java carrier type |
|--------|------|--------------------|-------------------|
| `char` | 8 | `ValueLayout.JAVA_BYTE` | `byte` |
| `unsigned char` | 8 | `ValueLayout.JAVA_BYTE` | `byte` (reinterpret as unsigned with `& 0xFF`) |
| `short` | 16 | `ValueLayout.JAVA_SHORT` | `short` |
| `unsigned short` | 16 | `ValueLayout.JAVA_SHORT` | `short` (reinterpret) |
| `int` | 32 | `ValueLayout.JAVA_INT` | `int` |
| `unsigned int` | 32 | `ValueLayout.JAVA_INT` | `int` (reinterpret) |
| `long` (LP64 Linux/macOS) | 64 | `ValueLayout.JAVA_LONG` | `long` |
| `long` (LLP64 Windows) | 32 | `ValueLayout.JAVA_INT` | `int` |
| `long long` | 64 | `ValueLayout.JAVA_LONG` | `long` |
| `int8_t` | 8 | `ValueLayout.JAVA_BYTE` | `byte` |
| `int16_t` | 16 | `ValueLayout.JAVA_SHORT` | `short` |
| `int32_t` | 32 | `ValueLayout.JAVA_INT` | `int` |
| `int64_t` | 64 | `ValueLayout.JAVA_LONG` | `long` |
| `uint8_t` | 8 | `ValueLayout.JAVA_BYTE` | `byte` |
| `uint32_t` | 32 | `ValueLayout.JAVA_INT` | `int` |
| `uint64_t` | 64 | `ValueLayout.JAVA_LONG` | `long` |
| `size_t` (64-bit) | 64 | `ValueLayout.JAVA_LONG` | `long` |
| `size_t` (32-bit) | 32 | `ValueLayout.JAVA_INT` | `int` |
| `ptrdiff_t` | ptr-size | `ValueLayout.JAVA_LONG` / `JAVA_INT` | matches pointer size |
| `float` | 32 | `ValueLayout.JAVA_FLOAT` | `float` |
| `double` | 64 | `ValueLayout.JAVA_DOUBLE` | `double` |
| `_Bool` / `bool` | 8 | `ValueLayout.JAVA_BYTE` | `byte` (0=false, 1=true) |

> **Platform note:** Always prefer `int8_t`/`int64_t` over `char`/`long` in
> your C façade header. Fixed-width types have the same size on all platforms
> and eliminate cross-platform mapping ambiguity.

---

## Pointer Types

| C type | Java `ValueLayout` | Java carrier | Notes |
|--------|-------------------|--------------|-------|
| Any `T*` | `ValueLayout.ADDRESS` | `MemorySegment` | Covers all pointer types |
| `const T*` | `ValueLayout.ADDRESS` | `MemorySegment` | Same layout; `const` is a C concept only |
| `const char*` | `ValueLayout.ADDRESS` | `MemorySegment` | Use `seg.getString(0, charset)` to read |
| `void*` | `ValueLayout.ADDRESS` | `MemorySegment` | Generic pointer |
| `char**` | `ValueLayout.ADDRESS` | `MemorySegment` | Pointer to pointer; dereference manually |
| `NULL` | `MemorySegment.NULL` | — | Constant for null check |

```java
// Null check for returned pointer:
MemorySegment result = (MemorySegment) mhCreate.invokeExact(...);
if (result == null || result.equals(MemorySegment.NULL)) {
    throw new MyFfiException("create returned null");
}
```

---

## Enum Types

C `enum` compiles to `int` (32-bit signed) on all platforms.

```c
typedef enum { MEDIA_JSON = 0, MEDIA_TSV = 1, MEDIA_CSV = 2 } MediaType;
```

| C type | Java `ValueLayout` | Carrier |
|--------|--------------------|---------|
| `enum Foo` | `ValueLayout.JAVA_INT` | `int` |

Java pattern:

```java
public enum MediaType {
    JSON(0), TSV(1), CSV(2);
    public final int nativeValue;
    MediaType(int v) { this.nativeValue = v; }

    public static MediaType fromNative(int v) {
        return Arrays.stream(values())
            .filter(e -> e.nativeValue == v)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown MediaType: " + v));
    }
}

// Passing to native:
mhExec.invokeExact(handle, MediaType.JSON.nativeValue, ...);
```

---

## String Passing Patterns

### Java String → C `const char*` (input)

```java
try (Arena call = Arena.ofConfined()) {
    MemorySegment cstr = call.allocateFrom(javaString, StandardCharsets.UTF_8);
    // cstr is null-terminated UTF-8
    mhFn.invokeExact(cstr);
}
// cstr freed automatically
```

### C `const char*` → Java String (output, C function retains pointer)

```java
MemorySegment ptr = (MemorySegment) mhGetName.invokeExact(handle);
if (ptr.equals(MemorySegment.NULL)) return null;
// Reinterpret with a maximum length; use shared arena
MemorySegment bounded = ptr.reinterpret(MAX_NAME_BYTES, sharedArena, null);
return bounded.getString(0, StandardCharsets.UTF_8);
```

### C function fills `char buf[N]` out-param

```java
try (Arena call = Arena.ofConfined()) {
    MemorySegment buf = call.allocate(N, 1);  // N bytes, byte-aligned
    buf.fill((byte) 0);
    mhFillName.invokeExact(handle, buf, (long) N);
    return buf.getString(0, StandardCharsets.UTF_8);
}
```

---

## Struct Types

| Scenario | Java representation |
|----------|---------------------|
| Opaque pointer (`MyThing*`) | `ValueLayout.ADDRESS` + `MemorySegment` |
| Passed-by-pointer struct (`MyStatus*`) | `ValueLayout.ADDRESS` + arena-allocated `MemorySegment` |
| Returned-by-value struct (rare in C) | `StructLayout` + `MemorySegment` (pass address to hold it) |

For struct field access see [How-to: Map C++ Structs](../how-to/map-cpp-structs.md).

---

## FunctionDescriptor Quick Reference

```java
// void fn(void)
FunctionDescriptor.ofVoid()

// void fn(int a, long b)
FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)

// int fn(const char* s, int n)
FunctionDescriptor.of(ValueLayout.JAVA_INT,
    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)

// MyThing* fn(int64_t init, MyStatus* s)
FunctionDescriptor.of(ValueLayout.ADDRESS,
    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)

// const char* fn(const MyThing* t)
FunctionDescriptor.of(ValueLayout.ADDRESS,   // return const char*
    ValueLayout.ADDRESS)                     // arg const MyThing*
```

Rule: **return layout first**, then argument layouts left-to-right.
`ofVoid(...)` takes only argument layouts.

---

## Memory Layout Sizes

| `ValueLayout` | Bytes |
|--------------|-------|
| `JAVA_BYTE` | 1 |
| `JAVA_SHORT` | 2 |
| `JAVA_INT` | 4 |
| `JAVA_LONG` | 8 |
| `JAVA_FLOAT` | 4 |
| `JAVA_DOUBLE` | 8 |
| `ADDRESS` | 4 or 8 (platform pointer size) |

Check at runtime: `ValueLayout.ADDRESS.byteSize()` returns `8` on 64-bit JVM.

---

## Common Gotchas

| Mistake | Consequence | Fix |
|---------|------------|-----|
| Mapping `long` (32-bit Windows) as `JAVA_LONG` | Reads extra bytes | Use `int32_t` in the C header |
| Using `JAVA_INT` for `size_t` on 64-bit | Truncates values >2³¹ | Use `JAVA_LONG` for `size_t` |
| Forgetting null terminator in manual allocation | Garbage after string | `allocateFrom(s, charset)` adds it; manual `allocate(len+1)` does not |
| Passing `MemorySegment.NULL` where non-null required | C segfault | Guard with `if (seg.equals(MemorySegment.NULL))` |
| Wrong argument order in `FunctionDescriptor.of()` | `WrongMethodTypeException` | Return type first, then args left-to-right |

---

## See Also

- [Reference: FunctionDescriptor Patterns](functiondescriptor-patterns.md)
- [Reference: C Façade Patterns](c-facade-patterns.md)
- [How-to: Map C++ Structs](../how-to/map-cpp-structs.md)
