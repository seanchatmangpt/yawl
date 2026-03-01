# How-to: Write a C Façade for a C++ Class

**Diátaxis quadrant: How-to** — Task-oriented. Assumes you know why and want to know how.

---

## Goal

Produce a `.h` + `.cpp` pair that exposes a C++ class through stable `extern "C"`
functions with opaque pointer handles, suitable for jextract and Panama FFM.

---

## Checklist Before Starting

- [ ] You have the C++ class(es) you need to expose identified.
- [ ] You know which operations callers need (constructor, destructor, methods).
- [ ] You know which C++ exceptions those methods can throw.

---

## 1 — Define the Opaque Handle

For every C++ class you expose, declare a **forward-declared C struct** in
the header. Never define the struct in the header (only in the `.cpp`).

```c
/* header: my_lib_ffi.h */
typedef struct MyThing MyThing;   /* opaque — definition is in .cpp only */
```

In the `.cpp`, define it to hold the actual C++ object:

```cpp
/* my_lib_ffi.cpp */
#include "my_lib_ffi.h"
#include "MyThing.hpp"   /* your C++ header */

struct MyThing {
    mylib::MyThing impl;           /* embed or hold a pointer */
    /* ... any bookkeeping you need */
};
```

Use embedding (`mylib::MyThing impl`) when the object is copyable/movable and
has a defined size at compile time. Use `std::unique_ptr<mylib::MyThing>` when
the object must be heap-allocated (e.g., abstract base class, large object,
forward-declared C++ type).

---

## 2 — Define the Status Type

Every C façade needs a way to report errors. Use a struct with an integer code
and a fixed-length message:

```c
#define MY_STATUS_OK            0
#define MY_STATUS_ERROR        -1
#define MY_STATUS_INVALID_ARG  -2
/* add codes for every distinct exception type you will translate */

typedef struct {
    int  code;
    char message[512];
} MyStatus;
```

`512` bytes is a safe default. Keep it power-of-two for alignment predictability.
Always put `code` first so offset 0 is the check field.

---

## 3 — Declare Lifecycle Functions

```c
#ifdef __cplusplus
extern "C" {
#endif

MyThing* my_thing_create(/* constructor args */, MyStatus* status);
void     my_thing_destroy(MyThing* t);

#ifdef __cplusplus
}
#endif
```

Rules:

- `*_create` returns a pointer. Caller owns the pointer; must call `*_destroy`.
- `*_destroy` takes only the pointer. It must be null-safe (`delete nullptr` is
  defined in C++; check before calling other methods).
- `*_destroy` must never fail. Swallow all exceptions inside it silently.

---

## 4 — Declare Method Functions

```c
#ifdef __cplusplus
extern "C" {
#endif

/* void return — result in out-param or status */
void my_thing_do_work(MyThing* t, int param, MyStatus* status);

/* value return — errors reported via status, return 0/null on error */
int64_t my_thing_get_count(const MyThing* t, MyStatus* status);

/* string return — returns C string pointer valid until next call or destroy */
const char* my_thing_get_name(const MyThing* t, MyStatus* status);

#ifdef __cplusplus
}
#endif
```

Rules:

- Non-const pointer → mutates the object.
- `const` pointer → read-only, may be called concurrently if the C++ method
  is `const` and thread-safe.
- Returned `const char*` must point to memory with a defined lifetime
  (e.g., `static` string, or memory inside the opaque struct). Never point to
  a local `std::string` variable.

---

## 5 — Implement the Exception Translator

Write one shared function in the `.cpp`. Call it at the bottom of every catch block:

```cpp
static void translate(const std::exception& e, MyStatus* s) {
    if (!s) return;
    if      (dynamic_cast<const std::invalid_argument*>(&e))
        s->code = MY_STATUS_INVALID_ARG;
    else if (dynamic_cast<const std::out_of_range*>(&e))
        s->code = MY_STATUS_OUT_OF_RANGE;
    else
        s->code = MY_STATUS_ERROR;

    std::snprintf(s->message, sizeof(s->message), "%s", e.what());
}

static void translate_unknown(MyStatus* s) {
    if (!s) return;
    s->code = MY_STATUS_ERROR;
    std::strncpy(s->message, "unknown exception", sizeof(s->message));
}
```

---

## 6 — Implement Every Function with the Same Try-Catch Shell

```cpp
void my_thing_do_work(MyThing* t, int param, MyStatus* status) {
    /* 1. Clear status */
    if (status) { status->code = MY_STATUS_OK; status->message[0] = '\0'; }

    /* 2. Null guard */
    if (!t) {
        if (status) {
            status->code = MY_STATUS_INVALID_ARG;
            std::strncpy(status->message, "null handle", sizeof(status->message));
        }
        return;
    }

    /* 3. Real work */
    try {
        t->impl.doWork(param);
    } catch (const std::exception& e) {
        translate(e, status);
    } catch (...) {
        translate_unknown(status);
    }
}
```

Never break from this shell. The three sections — clear, guard, work — are
mandatory in every function.

---

## 7 — Implement the Destroyer

```cpp
void my_thing_destroy(MyThing* t) {
    /* No status parameter — destroy must not fail. */
    try {
        delete t;
    } catch (...) {
        /* Swallow. Logging is acceptable here if you have a logger. */
    }
}
```

---

## 8 — Compile to a Shared Library

```bash
# Linux
g++ -std=c++20 -O2 -fPIC -shared \
    -o libmy_lib_ffi.so \
    my_lib_ffi.cpp \
    -lmy_cpp_lib        # link your actual C++ library here

# macOS
g++ -std=c++20 -O2 -dynamiclib \
    -o libmy_lib_ffi.dylib \
    my_lib_ffi.cpp \
    -lmy_cpp_lib
```

---

## 9 — Verify Symbol Export

```bash
# Linux
nm -D libmy_lib_ffi.so | grep ' T ' | grep my_thing_
# Each function must appear with its exact C name (no mangled suffix)

# macOS
nm -gU libmy_lib_ffi.dylib | grep my_thing_
```

If you see mangled names like `_ZN...`, a function is outside `extern "C"`.
Fix the header and recompile.

---

## Common Mistakes

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Missing `extern "C"` around implementation | Linker error: symbol not found | Wrap `.cpp` body in `extern "C" { }` |
| Returning pointer to local `std::string` | Dangling pointer / garbage read | Store string in the opaque struct |
| Exception escapes `extern "C"` function | Process crash (SIGABRT) | Add `catch (...)` to every function |
| Struct defined in header | Binary incompatibility when struct changes | Forward-declare only; define in `.cpp` |
| Null status pointer not guarded | Segfault when caller passes `NULL` | Check `if (status)` before writing to it |

---

## See Also

- [Tutorial: Bridge Your First C++ Library](../tutorial/bridge-first-cpp-library.md)
- [How-to: Handle C++ Exceptions](handle-cpp-exceptions.md)
- [Reference: Type Mapping](../reference/type-mapping.md)
