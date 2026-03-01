# How-to: Map C++ Structs to Java MemoryLayout

**Diátaxis quadrant: How-to** — Task-oriented. You have a C struct; you need Java code to read/write it.

---

## Goal

Write Java `MemoryLayout` and accessor code for a C struct that you defined
in your C façade header.

---

## When You Need This

You need this only when **Java must read or write individual fields of a struct
directly** — for example, reading the `code` and `message` fields of a
`CounterStatus`. You do **not** need this for opaque handles (`Counter*`) —
those are just addresses; Java never accesses their internals.

---

## Step 1 — Identify the Struct Fields

Given this C struct:

```c
typedef struct {
    int  code;           /* offset 0, 4 bytes */
    char message[512];   /* offset 4, 512 bytes */
} MyStatus;
```

Write down:

| Field | C type | Size | Alignment | Offset |
|-------|--------|------|-----------|--------|
| `code` | `int` (32-bit signed) | 4 | 4 | 0 |
| `message` | `char[512]` | 512 | 1 | 4 |

Total struct size: 4 + 512 = **516 bytes**, padded to next multiple of 4 = **516** bytes.

> **Compute alignment and offsets carefully.** C compilers insert padding bytes
> between fields so each field starts at a naturally aligned offset. Use
> `offsetof()` in a small C program if in doubt:
>
> ```c
> #include <stddef.h>
> #include <stdio.h>
> int main() {
>     printf("code:    offset=%zu\n", offsetof(MyStatus, code));
>     printf("message: offset=%zu\n", offsetof(MyStatus, message));
>     printf("sizeof:  %zu\n", sizeof(MyStatus));
> }
> ```

---

## Step 2 — Declare the MemoryLayout in Java

```java
import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.*;

public final class MyStatusLayout {

    /* Mirrors: typedef struct { int code; char message[512]; } MyStatus; */
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("code"),                   // int code
        MemoryLayout.sequenceLayout(512, ValueLayout.JAVA_BYTE)  // char message[512]
            .withName("message")
    ).withName("MyStatus");

    /* VarHandle for field access — computed once, reused forever */
    private static final VarHandle CODE    = LAYOUT.varHandle(
        PathElement.groupElement("code")
    );
    private static final VarHandle MESSAGE_BYTE = LAYOUT.varHandle(
        PathElement.groupElement("message"),
        PathElement.sequenceElement()           // index parameter needed for arrays
    );

    /* Convenience accessors */

    public static int getCode(MemorySegment seg) {
        return (int) CODE.get(seg, 0L);
    }

    public static void setCode(MemorySegment seg, int code) {
        CODE.set(seg, 0L, code);
    }

    /** Returns the message as a Java String (stops at first null byte). */
    public static String getMessage(MemorySegment seg) {
        // Offset of message field is 4 bytes into the struct
        return seg.getString(4, java.nio.charset.StandardCharsets.UTF_8);
    }
}
```

---

## Step 3 — Allocate the Struct in Native Memory

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment status = arena.allocate(MyStatusLayout.LAYOUT);

    // Zero out (important — don't trust uninitialized native memory)
    status.fill((byte) 0);

    // Pass to native function
    mhMyFunction.invokeExact(handle, status);

    // Read back
    int code = MyStatusLayout.getCode(status);
    if (code != 0) {
        String msg = MyStatusLayout.getMessage(status);
        throw new MyFfiException("native error " + code + ": " + msg);
    }
}
```

---

## Shortcut: Direct Offset Access (No VarHandle)

For simple structs with only primitive fields, computing offsets manually and
using `get/set` with explicit offsets is often simpler:

```java
// MyStatus: int code at offset 0, char[512] at offset 4
private static final long STATUS_SIZE    = 516L;
private static final long OFFSET_CODE    = 0L;
private static final long OFFSET_MESSAGE = 4L;

MemorySegment status = arena.allocate(STATUS_SIZE, /*align*/ 4);
status.fill((byte) 0);

// After native call:
int code = status.get(ValueLayout.JAVA_INT, OFFSET_CODE);
if (code != 0) {
    String msg = status.getString(OFFSET_MESSAGE, StandardCharsets.UTF_8);
    throw new MyFfiException(msg);
}
```

This is how `CounterFfiBindings.checkStatus()` works in the tutorial. Use
`MemoryLayout` and `VarHandle` when you have many fields or nested structs.

---

## Handling Arrays Inside Structs

For `char name[64]` — a fixed-length C string inside a struct:

```java
// Read: reinterpret the sub-segment at the known offset
long nameOffset = 8L;   // wherever the field starts
MemorySegment nameSeg = status.asSlice(nameOffset, 64);
String name = nameSeg.getString(0, StandardCharsets.UTF_8);
// getString stops at the first '\0' byte

// Write:
byte[] nameBytes = "hello".getBytes(StandardCharsets.UTF_8);
MemorySegment nameSeg = status.asSlice(nameOffset, 64);
nameSeg.fill((byte) 0);                    // zero the whole buffer first
nameSeg.copyFrom(MemorySegment.ofArray(nameBytes));
```

---

## Handling Enums

C `enum` compiles to an `int` (typically 32-bit). Map it as `JAVA_INT`:

```c
typedef enum { FOO_A = 0, FOO_B = 1, FOO_C = 2 } FooKind;
```

```java
public enum FooKind {
    A(0), B(1), C(2);

    public final int nativeValue;
    FooKind(int v) { this.nativeValue = v; }

    public static FooKind fromNative(int v) {
        for (FooKind k : values()) if (k.nativeValue == v) return k;
        throw new IllegalArgumentException("Unknown FooKind: " + v);
    }
}
```

Pass as `JAVA_INT` in `FunctionDescriptor`, use `.nativeValue` when calling,
use `fromNative()` when reading return values.

---

## Nested Structs

```c
typedef struct { int x; int y; }    Point;
typedef struct { Point origin; int radius; } Circle;
```

```java
StructLayout POINT_LAYOUT = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("x"),
    ValueLayout.JAVA_INT.withName("y")
).withName("Point");

StructLayout CIRCLE_LAYOUT = MemoryLayout.structLayout(
    POINT_LAYOUT.withName("origin"),
    ValueLayout.JAVA_INT.withName("radius")
).withName("Circle");

// Access: origin.x
VarHandle originX = CIRCLE_LAYOUT.varHandle(
    PathElement.groupElement("origin"),
    PathElement.groupElement("x")
);
int ox = (int) originX.get(seg, 0L);
```

---

## Common Mistakes

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Wrong offset (padding ignored) | Reads random bytes | Use `offsetof()` in C to verify |
| Layout size mismatch | Arena allocates too little → overwrite | Use `sizeof()` in C to verify |
| `getString` beyond buffer end | Exception or garbage | Use `asSlice(offset, maxLen).getString(0, ...)` |
| `VarHandle.get(seg)` missing `0L` carrier offset | `WrongMethodTypeException` | Panama 21+ requires explicit offset: `.get(seg, 0L)` |
| `MemoryLayout` field order mismatch with C | Silent wrong reads | Keep Java field order identical to C struct definition |

---

## See Also

- [Reference: Type Mapping](../reference/type-mapping.md)
- [How-to: Manage Arena Memory](manage-arena-memory.md)
- [Explanation: Memory Ownership](../explanation/memory-ownership.md)
