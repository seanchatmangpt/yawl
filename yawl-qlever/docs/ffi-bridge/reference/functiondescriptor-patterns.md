# Reference: FunctionDescriptor Patterns

**Diátaxis quadrant: Reference** — Look up specific patterns.

---

## What FunctionDescriptor Does

`FunctionDescriptor` describes the C-level type signature of a native function.
The `Linker` uses it to generate a `MethodHandle` that marshals arguments and
return values between JVM types and native calling conventions automatically.

Every native function you call needs exactly one `FunctionDescriptor`.

---

## Complete Pattern Catalogue

### P-01: No Arguments, No Return

```c
void engine_reset(void);
```

```java
FunctionDescriptor.ofVoid()
```

```java
mhReset.invokeExact();
```

---

### P-02: No Arguments, Primitive Return

```c
int64_t engine_version(void);
```

```java
FunctionDescriptor.of(ValueLayout.JAVA_LONG)
```

```java
long version = (long) mhVersion.invokeExact();
```

---

### P-03: Single Pointer Argument, No Return

```c
void thing_destroy(MyThing* t);
```

```java
FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
```

```java
mhDestroy.invokeExact(handle);   // handle: MemorySegment
```

---

### P-04: Opaque Pointer Create (Constructor Pattern)

```c
MyThing* thing_create(int64_t initial, MyStatus* status);
```

```java
FunctionDescriptor.of(
    ValueLayout.ADDRESS,      // MyThing* return
    ValueLayout.JAVA_LONG,    // int64_t initial
    ValueLayout.ADDRESS       // MyStatus* status
)
```

```java
MemorySegment handle = (MemorySegment) mhCreate.invokeExact(
    42L,       // initial
    status     // MemorySegment for MyStatus
);
```

---

### P-05: Pointer + C String Argument

```c
void thing_set_name(MyThing* t, const char* name, MyStatus* status);
```

```java
FunctionDescriptor.ofVoid(
    ValueLayout.ADDRESS,   // MyThing*
    ValueLayout.ADDRESS,   // const char*
    ValueLayout.ADDRESS    // MyStatus*
)
```

```java
try (Arena call = Arena.ofConfined()) {
    MemorySegment nameStr = call.allocateFrom(name, StandardCharsets.UTF_8);
    mhSetName.invokeExact(handle, nameStr, status);
}
```

---

### P-06: Integer Enum Argument

```c
typedef enum { FMT_JSON = 0, FMT_CSV = 1 } OutputFormat;
MyResult* thing_exec(MyThing* t, const char* query, OutputFormat fmt, MyStatus* s);
```

```java
FunctionDescriptor.of(
    ValueLayout.ADDRESS,    // MyResult* return
    ValueLayout.ADDRESS,    // MyThing*
    ValueLayout.ADDRESS,    // const char*
    ValueLayout.JAVA_INT,   // OutputFormat enum (= int)
    ValueLayout.ADDRESS     // MyStatus*
)
```

```java
MemorySegment result = (MemorySegment) mhExec.invokeExact(
    handle,
    querySeg,
    OutputFormat.JSON.nativeValue,  // int
    status
);
```

---

### P-07: Returns Primitive, No Error Param

```c
int result_has_next(const MyResult* r);
```

```java
FunctionDescriptor.of(
    ValueLayout.JAVA_INT,   // int return
    ValueLayout.ADDRESS     // const MyResult*
)
```

```java
boolean hasNext = (int) mhHasNext.invokeExact(result) != 0;
```

---

### P-08: Returns C String Pointer

```c
const char* result_next(MyResult* r, MyStatus* status);
```

```java
FunctionDescriptor.of(
    ValueLayout.ADDRESS,   // const char* return
    ValueLayout.ADDRESS,   // MyResult*
    ValueLayout.ADDRESS    // MyStatus*
)
```

```java
MemorySegment ptr = (MemorySegment) mhNext.invokeExact(result, status);
if (!ptr.equals(MemorySegment.NULL)) {
    MemorySegment bounded = ptr.reinterpret(MAX_LINE_BYTES, sharedArena, null);
    String line = bounded.getString(0, StandardCharsets.UTF_8);
}
```

---

### P-09: Struct Out-Parameter (Fills Struct on Return)

```c
void thing_get_stats(const MyThing* t, MyStats* out, MyStatus* status);
// fills: out->count, out->total_bytes, out->elapsed_ms
```

```java
FunctionDescriptor.ofVoid(
    ValueLayout.ADDRESS,   // const MyThing*
    ValueLayout.ADDRESS,   // MyStats* out
    ValueLayout.ADDRESS    // MyStatus*
)
```

```java
try (Arena call = Arena.ofConfined()) {
    MemorySegment stats  = call.allocate(MyStatsLayout.LAYOUT);
    MemorySegment status = call.allocate(StatusLayout.LAYOUT);
    stats.fill((byte) 0);
    status.fill((byte) 0);

    mhGetStats.invokeExact(handle, stats, status);
    checkStatus(status, "thing_get_stats");

    long count = MyStatsLayout.getCount(stats);
    long bytes = MyStatsLayout.getTotalBytes(stats);
}
```

---

### P-10: Multiple Primitive Arguments

```c
void thing_configure(MyThing* t, int timeout_ms, int max_retries,
                     double threshold, MyStatus* status);
```

```java
FunctionDescriptor.ofVoid(
    ValueLayout.ADDRESS,     // MyThing*
    ValueLayout.JAVA_INT,    // int timeout_ms
    ValueLayout.JAVA_INT,    // int max_retries
    ValueLayout.JAVA_DOUBLE, // double threshold
    ValueLayout.ADDRESS      // MyStatus*
)
```

```java
mhConfigure.invokeExact(handle, 5000, 3, 0.95, status);
```

---

### P-11: Pointer to Pointer (Out-Pointer)

```c
/* Allocates *out for you; caller must free with thing_destroy(*out) */
void thing_clone(const MyThing* src, MyThing** out, MyStatus* status);
```

```java
FunctionDescriptor.ofVoid(
    ValueLayout.ADDRESS,   // const MyThing* src
    ValueLayout.ADDRESS,   // MyThing** out (pointer to pointer)
    ValueLayout.ADDRESS    // MyStatus*
)
```

```java
try (Arena call = Arena.ofConfined()) {
    /* Allocate one pointer-sized cell to hold the output address */
    MemorySegment outPtr = call.allocate(ValueLayout.ADDRESS);
    outPtr.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);  // zero it

    mhClone.invokeExact(handle, outPtr, status);
    checkStatus(status, "thing_clone");

    /* Dereference the out-pointer to get the new handle */
    MemorySegment cloned = outPtr.get(ValueLayout.ADDRESS, 0);
}
```

---

### P-12: Callback / Function Pointer

```c
typedef void (*ProgressCallback)(int percent, void* user_data);
void thing_process(MyThing* t, ProgressCallback cb, void* user_data, MyStatus* s);
```

```java
// Describe the callback type
FunctionDescriptor callbackDesc = FunctionDescriptor.ofVoid(
    ValueLayout.JAVA_INT,  // int percent
    ValueLayout.ADDRESS    // void* user_data
);

// Create an upcall stub (C→Java)
MethodHandle javaCallback = MethodHandles.lookup().findStatic(
    MyClass.class, "onProgress",
    MethodType.methodType(void.class, int.class, MemorySegment.class)
);

try (Arena callbackArena = Arena.ofConfined()) {
    MemorySegment callbackStub = Linker.nativeLinker()
        .upcallStub(javaCallback, callbackDesc, callbackArena);

    mhProcess.invokeExact(
        handle,
        callbackStub,       // passes function pointer to C
        MemorySegment.NULL, // user_data (null here)
        status
    );
}

// Java method that C will call back into:
public static void onProgress(int percent, MemorySegment userData) {
    System.out.println("Progress: " + percent + "%");
}
```

---

## Descriptor Construction Summary

| Return | Arguments | Constructor form |
|--------|-----------|-----------------|
| `void` | none | `FunctionDescriptor.ofVoid()` |
| `void` | one+ | `FunctionDescriptor.ofVoid(arg1, arg2, ...)` |
| non-void | none | `FunctionDescriptor.of(returnLayout)` |
| non-void | one+ | `FunctionDescriptor.of(returnLayout, arg1, arg2, ...)` |

The **return layout is always first** in `of(...)`, even though it is the last
thing declared in C syntax. This trips up everyone at least once.

---

## See Also

- [Reference: Type Mapping](type-mapping.md)
- [Reference: C Façade Patterns](c-facade-patterns.md)
- [How-to: Generate Java Bindings](../how-to/generate-java-bindings.md)
