# Reference: C Façade Patterns

**Diátaxis quadrant: Reference** — Look up C façade idioms and their Java counterparts.

---

## Pattern Catalogue

### CF-01: Lifecycle (Create / Destroy)

**When to use:** Any C++ object that must be allocated on the heap and owned by the caller.

```c
/* Header */
typedef struct Engine Engine;   /* opaque */

Engine* engine_create(const char* config_path, EngineStatus* status);
void    engine_destroy(Engine* e);  /* null-safe, never fails */
```

```cpp
/* Implementation */
struct Engine {
    std::unique_ptr<mylib::Engine> impl;
};

extern "C" Engine* engine_create(const char* config_path, EngineStatus* status) {
    set_ok(status);
    try {
        auto* e = new Engine();
        e->impl = std::make_unique<mylib::Engine>(config_path);
        return e;
    } catch (const std::exception& ex) { translate(ex, status); return nullptr; }
      catch (...) { translate_unknown(status); return nullptr; }
}

extern "C" void engine_destroy(Engine* e) {
    try { delete e; } catch (...) {}
}
```

Java pattern: store handle in `AutoCloseable`, call `destroy` in `close()`.

---

### CF-02: Status Out-Parameter

**When to use:** Whenever a C function can fail. Pass `status` as last argument.

```c
typedef struct {
    int  code;         /* 0 = ok, negative = error */
    char message[512]; /* human-readable, UTF-8, null-terminated */
} EngineStatus;

#define ENGINE_OK            0
#define ENGINE_ERR          -1
#define ENGINE_INVALID_ARG  -2
#define ENGINE_TIMEOUT      -10
```

```cpp
/* Pattern: clear → guard → work → catch */
extern "C" void engine_do_thing(Engine* e, int param, EngineStatus* status) {
    if (status) { status->code = ENGINE_OK; status->message[0] = '\0'; }
    if (!e)     { set_null_error(status); return; }
    try {
        e->impl->doThing(param);
    } catch (const std::exception& ex) { translate(ex, status); }
      catch (...) { translate_unknown(status); }
}
```

Java pattern: allocate status in `Arena.ofConfined()`, fill with zeros, call function, check code at offset 0.

---

### CF-03: Result Iterator

**When to use:** C++ function produces a sequence of strings (query results, log lines,
records). Avoids allocating one huge buffer.

```c
typedef struct Result Result;   /* opaque */

Result*     engine_exec(Engine* e, const char* query, EngineStatus* status);
int         result_has_next(const Result* r);    /* 0 = empty, 1 = more */
const char* result_next(Result* r);              /* returns pointer into r */
int64_t     result_row_count(const Result* r);   /* total rows (may be estimate) */
void        result_destroy(Result* r);           /* mandatory; do not skip */
```

```cpp
struct Result {
    std::vector<std::string> lines;
    size_t pos = 0;
};

extern "C" int result_has_next(const Result* r) {
    if (!r) return 0;
    return r->pos < r->lines.size() ? 1 : 0;
}

extern "C" const char* result_next(Result* r) {
    if (!r || r->pos >= r->lines.size()) return nullptr;
    return r->lines[r->pos++].c_str();   /* pointer valid until destroy */
}
```

Java pattern:
```java
MemorySegment result = null;
try {
    result = ffi.exec(query).result();
    StringBuilder sb = new StringBuilder();
    while (ffi.hasNext(result)) {
        String line = ffi.next(result);
        if (line != null) sb.append(line).append('\n');
    }
    return sb.toString();
} finally {
    if (result != null) ffi.destroy(result);
}
```

---

### CF-04: Opaque Counter / Statistics

**When to use:** Reading metrics without exposing internal struct layout.

```c
int64_t engine_query_count(const Engine* e, EngineStatus* status);
int64_t engine_cache_hits(const Engine* e, EngineStatus* status);
double  engine_avg_latency_us(const Engine* e, EngineStatus* status);
```

No struct needed — Java calls each function independently. Values are returned
as primitives; errors go into status.

---

### CF-05: Boolean Check

**When to use:** Querying state (`is_loaded`, `is_connected`, `is_open`).

```c
/* Returns 1 if true, 0 if false, -1 on error (check status) */
int engine_is_ready(const Engine* e, EngineStatus* status);
```

```java
// FunctionDescriptor: JAVA_INT return, ADDRESS Engine*, ADDRESS status
int raw = (int) mhIsReady.invokeExact(handle, status);
checkStatus(status, "engine_is_ready");
boolean ready = raw == 1;
```

---

### CF-06: String Input

**When to use:** Passing a Java `String` to a C function. C function reads but does not store the string.

```c
void engine_set_option(Engine* e, const char* key, const char* value,
                       EngineStatus* status);
```

```java
try (Arena call = Arena.ofConfined()) {
    MemorySegment keySeg   = call.allocateFrom(key,   StandardCharsets.UTF_8);
    MemorySegment valueSeg = call.allocateFrom(value, StandardCharsets.UTF_8);
    MemorySegment status   = call.allocate(STATUS_LAYOUT);
    status.fill((byte) 0);

    mhSetOption.invokeExact(handle, keySeg, valueSeg, status);
    checkStatus(status, "engine_set_option");
}
```

---

### CF-07: String Output (Fixed Buffer)

**When to use:** C function writes a string into a caller-provided buffer.

```c
/* Writes null-terminated UTF-8 into buf[0..buf_len-1].
 * Returns number of bytes written (not counting null terminator).
 * If buf is too small, writes truncated string and returns buf_len. */
int64_t engine_get_version_string(char* buf, int64_t buf_len, EngineStatus* status);
```

```java
try (Arena call = Arena.ofConfined()) {
    int bufLen = 256;
    MemorySegment buf    = call.allocate(bufLen, 1);
    MemorySegment status = call.allocate(STATUS_LAYOUT);
    buf.fill((byte) 0);
    status.fill((byte) 0);

    long written = (long) mhGetVersionString.invokeExact(buf, (long) bufLen, status);
    checkStatus(status, "engine_get_version_string");

    return buf.getString(0, StandardCharsets.UTF_8);
}
```

---

### CF-08: Array Input

**When to use:** Passing a Java `int[]` or `long[]` to C.

```c
void engine_set_thread_affinity(Engine* e, const int* cpu_ids, int count,
                                EngineStatus* status);
```

```java
int[] cpus = {0, 1, 2, 3};
try (Arena call = Arena.ofConfined()) {
    MemorySegment arr = call.allocate(
        (long) cpus.length * ValueLayout.JAVA_INT.byteSize(),
        ValueLayout.JAVA_INT.byteAlignment()
    );
    MemorySegment.copy(MemorySegment.ofArray(cpus), 0, arr, 0,
                       (long) cpus.length * ValueLayout.JAVA_INT.byteSize());

    MemorySegment status = call.allocate(STATUS_LAYOUT);
    status.fill((byte) 0);

    mhSetThreadAffinity.invokeExact(handle, arr, cpus.length, status);
    checkStatus(status, "engine_set_thread_affinity");
}
```

---

### CF-09: Struct Output (Read Multiple Fields)

**When to use:** C function fills a struct with multiple output values.

```c
typedef struct {
    int64_t triple_count;
    int64_t cache_size_bytes;
    double  avg_query_ms;
} EngineStats;

void engine_get_stats(const Engine* e, EngineStats* out, EngineStatus* status);
```

```java
// Java MemoryLayout mirrors EngineStats exactly
StructLayout STATS_LAYOUT = MemoryLayout.structLayout(
    ValueLayout.JAVA_LONG.withName("triple_count"),
    ValueLayout.JAVA_LONG.withName("cache_size_bytes"),
    ValueLayout.JAVA_DOUBLE.withName("avg_query_ms")
).withName("EngineStats");

try (Arena call = Arena.ofConfined()) {
    MemorySegment stats  = call.allocate(STATS_LAYOUT);
    MemorySegment status = call.allocate(STATUS_LAYOUT);
    stats.fill((byte) 0);
    status.fill((byte) 0);

    mhGetStats.invokeExact(handle, stats, status);
    checkStatus(status, "engine_get_stats");

    long   triples = stats.get(ValueLayout.JAVA_LONG,   0);
    long   cache   = stats.get(ValueLayout.JAVA_LONG,   8);
    double avgMs   = stats.get(ValueLayout.JAVA_DOUBLE, 16);
}
```

---

## Anti-Patterns (Never Do These)

| Anti-pattern | Problem |
|-------------|---------|
| C function returns `std::string` | Not representable as C ABI |
| C function throws `std::exception` | Process crash if it crosses `extern "C"` |
| C function retains `const char*` after return | Use-after-free when Arena closes |
| C struct definition in `.h` file | Java must track layout; breaks on any change |
| Multiple return values without struct or out-params | C has no tuples; can't represent |
| `malloc`-allocated string returned to Java | Java cannot call `free()` on it; leak |

---

## See Also

- [Reference: Type Mapping](type-mapping.md)
- [Reference: FunctionDescriptor Patterns](functiondescriptor-patterns.md)
- [How-to: Write a C Façade](../how-to/write-c-facade.md)
- [How-to: Handle C++ Exceptions](../how-to/handle-cpp-exceptions.md)
