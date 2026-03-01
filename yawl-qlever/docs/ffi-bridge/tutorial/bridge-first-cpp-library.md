# Tutorial: Bridge Your First C++ Library to Java

**Diátaxis quadrant: Tutorial** — Learn by doing. Follow every step.

By the end of this tutorial you will have a working five-layer FFI bridge
that wraps a simple C++ counter class and exposes it as a Java
`AutoCloseable`. The steps are identical to how the QLever SPARQL engine
is bridged; once you finish this, you can replicate the pattern for any
C++ library.

**Prerequisites**

- `g++` or `clang++` installed
- Java 25 with Panama FFM (`--enable-preview`)
- `jextract` 21+ on your `PATH`
- A YAWL checkout so Maven can compile the Java side

---

## Step 0 — Understand the Five Layers

Before writing a line of code, hold this picture in your head:

```
Layer 5 │ YourEngine.java        │ User-facing Java API (AutoCloseable)
Layer 4 │ YourFfiBindings.java   │ Safe Java wrapper (Arena, MethodHandle)
Layer 3 │ your_lib_h.java        │ jextract-generated raw FFI
────────┼───────────────────────┼─────────────────────────────────────
Layer 2 │ your_lib.cpp           │ C++ implementation (try/catch, map)
Layer 1 │ your_lib.h             │ C façade (extern "C", opaque pointers)
Layer 0 │ YourCppLib             │ The actual C++ library you are wrapping
```

The hourglass waist is Layer 1: a flat C header.

---

## Step 1 — Write the C Façade Header

Create `src/main/native/counter_ffi.h`.

This file is the **only interface** between Java and C++. Everything goes
through it.

```c
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Error reporting ─────────────────────────────────────────────────── */

#define COUNTER_STATUS_OK              0
#define COUNTER_STATUS_ERROR          -1
#define COUNTER_STATUS_INVALID_ARG    -2
#define COUNTER_STATUS_OVERFLOW       -3

typedef struct {
    int    code;
    char   message[256];
} CounterStatus;

static inline int counter_status_ok(const CounterStatus* s) {
    return s && s->code == COUNTER_STATUS_OK;
}

/* ── Opaque handle ───────────────────────────────────────────────────── */

/* Forward-declared; Java never sees the definition. */
typedef struct Counter Counter;

/* ── Lifecycle ───────────────────────────────────────────────────────── */

Counter*    counter_create(int64_t initial_value, CounterStatus* status);
void        counter_destroy(Counter* c);

/* ── Operations ──────────────────────────────────────────────────────── */

void        counter_increment(Counter* c, int64_t by, CounterStatus* status);
void        counter_decrement(Counter* c, int64_t by, CounterStatus* status);
int64_t     counter_value(const Counter* c, CounterStatus* status);
void        counter_reset(Counter* c, CounterStatus* status);

#ifdef __cplusplus
} /* extern "C" */
#endif
```

**Rules to follow every time:**

1. All functions inside `extern "C" { }`.
2. Return types: only primitives (`int`, `int64_t`, `size_t`) or opaque
   pointers (`SomeStruct*`). No `std::` types, no references.
3. Status as an out-parameter `CounterStatus* status`. The function fills it;
   callers check `status.code` afterwards.
4. The struct `Counter` is forward-declared only — never defined in the
   header. Java receives only the address, never the layout.

---

## Step 2 — Implement the C Façade in C++

Create `src/main/native/counter_ffi.cpp`.

```cpp
#include "counter_ffi.h"
#include <stdexcept>
#include <string>
#include <cstring>
#include <cstdio>
#include <climits>

/* ── The real C++ class ──────────────────────────────────────────────── */

namespace mylib {

class Counter {
public:
    explicit Counter(int64_t initial) : value_(initial) {}

    void increment(int64_t by) {
        if (by < 0) throw std::invalid_argument("increment by must be >= 0");
        if (value_ > INT64_MAX - by) throw std::overflow_error("counter overflow");
        value_ += by;
    }

    void decrement(int64_t by) {
        if (by < 0) throw std::invalid_argument("decrement by must be >= 0");
        if (value_ < INT64_MIN + by) throw std::overflow_error("counter underflow");
        value_ -= by;
    }

    int64_t value() const { return value_; }
    void    reset()        { value_ = 0; }

private:
    int64_t value_;
};

} // namespace mylib

/* ── C opaque struct (wraps the real class) ──────────────────────────── */

struct Counter {
    mylib::Counter impl;
    explicit Counter(int64_t v) : impl(v) {}
};

/* ── Centralised exception translation (Lippincott Pattern) ──────────── */

static void set_ok(CounterStatus* s) {
    if (!s) return;
    s->code = COUNTER_STATUS_OK;
    s->message[0] = '\0';
}

static void translate(const std::exception& e, CounterStatus* s) {
    if (!s) return;
    if (dynamic_cast<const std::invalid_argument*>(&e)) {
        s->code = COUNTER_STATUS_INVALID_ARG;
    } else if (dynamic_cast<const std::overflow_error*>(&e)) {
        s->code = COUNTER_STATUS_OVERFLOW;
    } else {
        s->code = COUNTER_STATUS_ERROR;
    }
    std::snprintf(s->message, sizeof(s->message), "%s", e.what());
}

/* ── C function implementations ──────────────────────────────────────── */

extern "C" {

Counter* counter_create(int64_t initial_value, CounterStatus* status) {
    set_ok(status);
    try {
        return new Counter(initial_value);
    } catch (const std::exception& e) {
        translate(e, status);
        return nullptr;
    } catch (...) {
        if (status) {
            status->code = COUNTER_STATUS_ERROR;
            std::strncpy(status->message, "unknown error", sizeof(status->message));
        }
        return nullptr;
    }
}

void counter_destroy(Counter* c) {
    delete c;   /* null-safe: delete nullptr is a no-op */
}

void counter_increment(Counter* c, int64_t by, CounterStatus* status) {
    set_ok(status);
    if (!c) {
        if (status) {
            status->code = COUNTER_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null counter", sizeof(status->message));
        }
        return;
    }
    try {
        c->impl.increment(by);
    } catch (const std::exception& e) {
        translate(e, status);
    } catch (...) {
        if (status) {
            status->code = COUNTER_STATUS_ERROR;
            std::strncpy(status->message, "unknown error", sizeof(status->message));
        }
    }
}

void counter_decrement(Counter* c, int64_t by, CounterStatus* status) {
    set_ok(status);
    if (!c) {
        if (status) {
            status->code = COUNTER_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null counter", sizeof(status->message));
        }
        return;
    }
    try {
        c->impl.decrement(by);
    } catch (const std::exception& e) {
        translate(e, status);
    } catch (...) {
        if (status) {
            status->code = COUNTER_STATUS_ERROR;
            std::strncpy(status->message, "unknown error", sizeof(status->message));
        }
    }
}

int64_t counter_value(const Counter* c, CounterStatus* status) {
    set_ok(status);
    if (!c) {
        if (status) {
            status->code = COUNTER_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null counter", sizeof(status->message));
        }
        return 0;
    }
    try {
        return c->impl.value();
    } catch (const std::exception& e) {
        translate(e, status);
        return 0;
    }
}

void counter_reset(Counter* c, CounterStatus* status) {
    set_ok(status);
    if (!c) return;
    try {
        c->impl.reset();
    } catch (const std::exception& e) {
        translate(e, status);
    }
}

} // extern "C"
```

**Key patterns to always include:**

- `extern "C" { ... }` wrapping all implementations.
- Every function: `set_ok(status)` at the top, `catch (const std::exception&)`
  + `catch (...)` at the bottom. No exception may escape an `extern "C"` function.
- Null-pointer guard on every pointer parameter before use.
- `delete c` in `*_destroy` (null-safe).

---

## Step 3 — Build the Shared Library

```bash
g++ -std=c++20 -O2 -fPIC -shared \
    -o libcounter_ffi.so \
    src/main/native/counter_ffi.cpp
```

Verify the symbols are exported:

```bash
nm -D libcounter_ffi.so | grep ' T ' | grep counter_
# Should print: counter_create, counter_destroy, counter_increment, ...
```

Names must appear **without** C++ mangling (i.e., exactly `counter_create`,
not `_ZN7counter_createB5...`).

---

## Step 4 — Generate Java Bindings with jextract

```bash
jextract \
  --output src/main/java \
  --target-package org.yawlfoundation.yawl.counter.ffi \
  --library counter_ffi \
  src/main/native/counter_ffi.h
```

This produces `counter_ffi_h.java` in your target package. Open it and find:

```java
// Generated by jextract — DO NOT EDIT
public final class counter_ffi_h {

    public static final FunctionDescriptor counter_create$FUNC =
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,    // Counter* (return)
            ValueLayout.JAVA_LONG,  // int64_t initial_value
            ValueLayout.ADDRESS     // CounterStatus* status
        );

    public static MethodHandle counter_create() { ... }

    // ... counter_destroy, counter_increment, counter_value, counter_reset
}
```

You will reference these `FunctionDescriptor` constants and `MethodHandle`
factories in Layer 4.

> **If jextract is not available:** Write `counter_ffi_h.java` by hand using
> the Type Mapping reference. The QLever codebase does this for
> `qlever_ffi_h.java`.

---

## Step 5 — Write the Safe Java Wrapper (Layer 4)

Create `src/main/java/org/yawlfoundation/yawl/counter/CounterFfiBindings.java`:

```java
package org.yawlfoundation.yawl.counter;

import org.yawlfoundation.yawl.counter.ffi.counter_ffi_h;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Layer 4: safe Java wrapper over the raw counter_ffi.h FFI.
 * Converts Java types ↔ native memory. Never throws raw Throwable.
 */
public final class CounterFfiBindings implements AutoCloseable {

    private static final String LIBRARY = "counter_ffi";

    static {
        try {
            System.loadLibrary(LIBRARY);
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Cannot load " + LIBRARY + ": " + e.getMessage(), e);
        }
    }

    private final Arena          arena;
    private final MethodHandle   mhCreate;
    private final MethodHandle   mhDestroy;
    private final MethodHandle   mhIncrement;
    private final MethodHandle   mhDecrement;
    private final MethodHandle   mhValue;
    private final MethodHandle   mhReset;

    public CounterFfiBindings() {
        this.arena       = Arena.ofShared();
        this.mhCreate    = counter_ffi_h.counter_create();
        this.mhDestroy   = counter_ffi_h.counter_destroy();
        this.mhIncrement = counter_ffi_h.counter_increment();
        this.mhDecrement = counter_ffi_h.counter_decrement();
        this.mhValue     = counter_ffi_h.counter_value();
        this.mhReset     = counter_ffi_h.counter_reset();
    }

    /** Returns native handle, or NULL segment on error. */
    public MemorySegment create(long initialValue) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment status = allocStatus(call);
            MemorySegment handle = (MemorySegment)
                    mhCreate.invokeExact(initialValue, status);
            checkStatus(status, "counter_create");
            return handle;
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to create counter", t);
        }
    }

    public void destroy(MemorySegment handle) {
        if (handle == null || handle.equals(MemorySegment.NULL)) return;
        try {
            mhDestroy.invokeExact(handle);
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to destroy counter", t);
        }
    }

    public void increment(MemorySegment handle, long by) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment status = allocStatus(call);
            mhIncrement.invokeExact(handle, by, status);
            checkStatus(status, "counter_increment");
        } catch (CounterFfiException e) {
            throw e;
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to increment counter", t);
        }
    }

    public void decrement(MemorySegment handle, long by) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment status = allocStatus(call);
            mhDecrement.invokeExact(handle, by, status);
            checkStatus(status, "counter_decrement");
        } catch (CounterFfiException e) {
            throw e;
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to decrement counter", t);
        }
    }

    public long value(MemorySegment handle) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment status = allocStatus(call);
            long v = (long) mhValue.invokeExact(handle, status);
            checkStatus(status, "counter_value");
            return v;
        } catch (CounterFfiException e) {
            throw e;
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to read counter value", t);
        }
    }

    public void reset(MemorySegment handle) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment status = allocStatus(call);
            mhReset.invokeExact(handle, status);
            checkStatus(status, "counter_reset");
        } catch (CounterFfiException e) {
            throw e;
        } catch (Throwable t) {
            throw new CounterFfiException("Failed to reset counter", t);
        }
    }

    @Override
    public void close() {
        arena.close();
    }

    /* ── Private helpers ────────────────────────────────────────────── */

    /** Allocate a zeroed CounterStatus struct in the call arena. */
    private static MemorySegment allocStatus(Arena arena) {
        // CounterStatus: int code (4 bytes) + char message[256] = 260 bytes total
        // Align to 4 bytes.
        MemorySegment s = arena.allocate(260, 4);
        s.fill((byte) 0);
        return s;
    }

    /** Read the int code field at offset 0. Throw if non-zero. */
    private static void checkStatus(MemorySegment status, String fn) {
        int code = status.get(ValueLayout.JAVA_INT, 0);
        if (code != 0) {
            // message starts at offset 4
            String msg = status.getString(4, StandardCharsets.UTF_8);
            throw new CounterFfiException(fn + " failed (code=" + code + "): " + msg);
        }
    }
}
```

---

## Step 6 — Write the High-Level Java API (Layer 5)

Create `CounterEngine.java`:

```java
package org.yawlfoundation.yawl.counter;

import java.lang.foreign.MemorySegment;

/**
 * Layer 5: idiomatic Java API. Hides all FFM machinery.
 * Use this class; do not use CounterFfiBindings directly.
 */
public final class CounterEngine implements AutoCloseable {

    private final CounterFfiBindings ffi;
    private final MemorySegment      handle;

    public CounterEngine(long initialValue) {
        this.ffi    = new CounterFfiBindings();
        this.handle = ffi.create(initialValue);
        if (handle.equals(MemorySegment.NULL)) {
            ffi.close();
            throw new IllegalStateException("counter_create returned NULL");
        }
    }

    public void increment()          { ffi.increment(handle, 1); }
    public void increment(long by)   { ffi.increment(handle, by); }
    public void decrement()          { ffi.decrement(handle, 1); }
    public void decrement(long by)   { ffi.decrement(handle, by); }
    public long value()              { return ffi.value(handle); }
    public void reset()              { ffi.reset(handle); }

    @Override
    public void close() {
        ffi.destroy(handle);
        ffi.close();
    }
}
```

---

## Step 7 — Smoke Test

```java
try (CounterEngine c = new CounterEngine(0)) {
    c.increment(5);
    c.decrement(2);
    assert c.value() == 3 : "expected 3, got " + c.value();
    c.reset();
    assert c.value() == 0;
    System.out.println("Counter smoke test passed.");
}
```

Run with:

```bash
java --enable-preview \
     -Djava.library.path=/path/to/libcounter_ffi.so \
     --add-modules ALL-UNNAMED \
     org.yawlfoundation.yawl.counter.SmokeTest
```

---

## What You've Built

You now have the complete five-layer bridge:

| Layer | File | Responsibility |
|-------|------|---------------|
| 5 | `CounterEngine.java` | Idiomatic Java API |
| 4 | `CounterFfiBindings.java` | Safe type conversions, Arena, MethodHandle |
| 3 | `counter_ffi_h.java` | jextract-generated FunctionDescriptors |
| 2 | `counter_ffi.cpp` | Exception translation, C→C++ mapping |
| 1 | `counter_ffi.h` | Stable C façade contract |

The same structure scales to any C++ library. Replace `mylib::Counter` with
`QLever::Index`, `RocksDB`, `LLVM`, `OpenCV` — the layers are always the same.

---

## Next Steps

- [How-to: Write a C Façade for a C++ Class](../how-to/write-c-facade.md)
- [How-to: Map C++ Structs to Java MemoryLayout](../how-to/map-cpp-structs.md)
- [How-to: Handle C++ Exceptions](../how-to/handle-cpp-exceptions.md)
- [Reference: Type Mapping](../reference/type-mapping.md)
