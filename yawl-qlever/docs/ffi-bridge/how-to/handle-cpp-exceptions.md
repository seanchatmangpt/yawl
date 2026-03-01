# How-to: Handle C++ Exceptions in the C Façade

**Diátaxis quadrant: How-to** — Task-oriented. You need every C++ exception translated safely.

---

## Goal

Ensure no C++ exception propagates out of any `extern "C"` function, and that
callers receive meaningful error codes and messages via `MyStatus*`.

---

## The Lippincott Pattern

The canonical approach is a **centralised translator function** (named after the
C++ Core Guidelines contributor Sara Chipps, popularised as the
"Lippincott function"):

```cpp
// my_lib_ffi.cpp — put this near the top, before any extern "C" functions

static void translate(const std::exception& e, MyStatus* s) {
    if (!s) return;

    // Map known exception types to distinct codes
    if      (const auto* p = dynamic_cast<const std::invalid_argument*>(&e))
        s->code = MY_STATUS_INVALID_ARG;
    else if (const auto* p = dynamic_cast<const std::out_of_range*>(&e))
        s->code = MY_STATUS_OUT_OF_RANGE;
    else if (const auto* p = dynamic_cast<const std::bad_alloc*>(&e))
        s->code = MY_STATUS_BAD_ALLOC;
    else if (const auto* p = dynamic_cast<MyLib::ParseError*>(&e))
        s->code = MY_STATUS_PARSE_ERROR;
    else
        s->code = MY_STATUS_ERROR;     /* catch-all */

    std::snprintf(s->message, sizeof(s->message), "%s", e.what());
}

static void translate_unknown(MyStatus* s) {
    if (!s) return;
    s->code = MY_STATUS_ERROR;
    std::strncpy(s->message, "unknown non-std exception", sizeof(s->message));
    s->message[sizeof(s->message) - 1] = '\0';
}
```

Call it in every function:

```cpp
extern "C" void my_thing_do_work(MyThing* t, int param, MyStatus* status) {
    if (status) { status->code = MY_STATUS_OK; status->message[0] = '\0'; }
    if (!t) { /* null guard */ return; }
    try {
        t->impl.doWork(param);
    } catch (const std::exception& e) {
        translate(e, status);
    } catch (...) {
        translate_unknown(status);
    }
}
```

The `catch (...)` clause is **mandatory**. C++ libraries can throw types not
derived from `std::exception`. If an unhandled exception propagates across the
`extern "C"` boundary into Java code, the C++ runtime calls `std::terminate()`
and the JVM crashes with no recovery.

---

## When the Function Returns a Value

If the function normally returns a value, return a safe sentinel on error:

```cpp
extern "C" int64_t my_thing_get_count(const MyThing* t, MyStatus* status) {
    if (status) { status->code = MY_STATUS_OK; status->message[0] = '\0'; }
    if (!t) {
        if (status) {
            status->code = MY_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null handle", sizeof(status->message));
        }
        return 0;    // sentinel
    }
    try {
        return t->impl.count();
    } catch (const std::exception& e) {
        translate(e, status);
        return 0;    // sentinel
    } catch (...) {
        translate_unknown(status);
        return 0;
    }
}
```

Sentinel conventions:
- Integer: return `0` or `-1` (document which).
- Pointer: return `nullptr`.
- Boolean: return `0` (false).

---

## When the Function Returns a Pointer That May Be Null

```cpp
extern "C" MyResult* my_thing_exec(MyThing* t, const char* query, MyStatus* status) {
    if (status) { status->code = MY_STATUS_OK; status->message[0] = '\0'; }
    auto* result = new MyResult();  // allocate before try
    if (!t || !query) {
        if (status) {
            status->code = MY_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null argument", sizeof(status->message));
        }
        result->statusCode = 400;
        return result;              // return non-null, but error code set
    }
    try {
        result->data = t->impl.execute(query);
        result->statusCode = 200;
    } catch (const std::exception& e) {
        translate(e, status);
        result->statusCode = 500;
    } catch (...) {
        translate_unknown(status);
        result->statusCode = 500;
    }
    return result;
}
```

Always return a non-null `MyResult*` so the caller can safely call
`my_result_destroy(result)` in a `finally` block without a null check. Encode
the error in `status`.

---

## Wrapping Library-Specific Exception Types

If your C++ library has its own exception hierarchy, add cases to `translate()`:

```cpp
// For a library with a deep exception hierarchy:
#include "mylib/exceptions.hpp"

static void translate(const std::exception& e, MyStatus* s) {
    if (!s) return;

    // Library-specific types (check before std:: types)
    if (dynamic_cast<const mylib::NetworkError*>(&e))
        s->code = MY_STATUS_NETWORK;
    else if (dynamic_cast<const mylib::AuthError*>(&e))
        s->code = MY_STATUS_AUTH;
    else if (dynamic_cast<const mylib::TimeoutError*>(&e))
        s->code = MY_STATUS_TIMEOUT;

    // Standard library types
    else if (dynamic_cast<const std::invalid_argument*>(&e))
        s->code = MY_STATUS_INVALID_ARG;
    else if (dynamic_cast<const std::bad_alloc*>(&e))
        s->code = MY_STATUS_BAD_ALLOC;
    else
        s->code = MY_STATUS_ERROR;

    std::snprintf(s->message, sizeof(s->message), "[%s] %s",
        typeid(e).name(), e.what());
}
```

Including `typeid(e).name()` in the message aids debugging when a new
exception type appears that is not yet explicitly handled.

---

## On the Java Side: Mapping Status Codes

In `MyFfiBindings.java`:

```java
private static void checkStatus(MemorySegment status, String fn) {
    int code = status.get(ValueLayout.JAVA_INT, 0);
    if (code == 0) return;   // MY_STATUS_OK

    String msg = status.getString(4, StandardCharsets.UTF_8);

    throw switch (code) {
        case -2 -> new MyFfiException.InvalidArgument(fn + ": " + msg);
        case -3 -> new MyFfiException.OutOfRange(fn + ": " + msg);
        case -4 -> new MyFfiException.BadAlloc(fn + ": " + msg);
        default -> new MyFfiException(fn + " error " + code + ": " + msg);
    };
}
```

Maintain a constant class or enum to avoid magic numbers:

```java
public final class MyStatus {
    public static final int OK            =  0;
    public static final int ERROR         = -1;
    public static final int INVALID_ARG   = -2;
    public static final int OUT_OF_RANGE  = -3;
    public static final int BAD_ALLOC     = -4;
    private MyStatus() {}
}
```

---

## Checklist

- [ ] Every `extern "C"` function has `try { } catch (const std::exception&) { } catch (...) { }`
- [ ] `catch (...)` is always present — never omitted
- [ ] All functions clear `status` at entry with `set_ok(status)`
- [ ] All functions null-guard pointer parameters before use
- [ ] `*_destroy` functions catch all exceptions silently (no status parameter)
- [ ] Java `checkStatus()` covers all status codes defined in the C header
- [ ] Status codes are named constants, not magic numbers, on both sides

---

## See Also

- [How-to: Write a C Façade](write-c-facade.md)
- [Reference: C Façade Patterns](../reference/c-facade-patterns.md)
- [Tutorial: Bridge Your First C++ Library](../tutorial/bridge-first-cpp-library.md)
