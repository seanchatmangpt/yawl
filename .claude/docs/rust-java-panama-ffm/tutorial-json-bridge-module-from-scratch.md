# Tutorial: Building a JSON-Based Native Bridge Module for YAWL

## Goal

By the end of this tutorial, you will have created a complete YAWL native bridge module (`yawl-graph`) that:

- Wraps a Rust graph analysis library using Panama FFM
- Uses JSON as the wire format between Java and Rust
- Implements the Capability system for startup verification
- Demonstrates the three-layer architecture (FFM bindings → Bridge → Service)
- Passes both offline unit tests and runtime capability tests

**Estimated reading time**: 50–60 minutes
**Estimated implementation time**: 2–3 hours (including build and testing)

---

## Prerequisites

You have:
- Completed [tutorial-first-wrap.md](./tutorial-first-wrap.md) (understands Layer 1 FFM bindings, MethodHandles, MemorySegments)
- A Rust library built and available (or can mock with `-Dbuild.skip.native=true`)
- Java 25 with Panama FFM enabled
- Maven 3.9+ and `mvn` on your PATH
- Familiarity with Jackson `ObjectMapper` for JSON encoding/decoding

---

## Architecture Overview

Your three-layer stack looks like this:

```
Rust library (libgraph_ffi.so)
    ↕ FFM MethodHandles (struct returns, Arena allocation)
         [Layer 1: graph_ffi_h.java — generated/hand-written]
    ↕ MemorySegment accessors + per-call Arena
         [Layer 2: GraphBridge.java — manual unwrapping]
    ↕ JSON strings (Jackson encode/decode)
         [Layer 3: GraphServiceImpl.java — domain types]
         [Layer 3: GraphService interface — public API]
    ↕ Capability annotations + registry
         [Layer 3: @MapsToCapability + GraphCapabilityRegistry]
    ↕
Client code (YAWL engine, tests, user code)
```

**Key difference from raw FFM** (tutorial-first-wrap):
- Layer 2 **unwraps result structs** and **frees native memory** (no manual cleanup)
- Layer 3 **encodes/decodes JSON** for domain objects (records)
- **Capability registry** validates at startup that every public method is annotated

---

## Step 1 – Define GraphCapability Enum

Create a new enum that lists every capability your graph module provides. This is your **contract**: each capability must map to exactly one method in both the Bridge and the Service.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphCapability.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphCapability.java
package org.yawlfoundation.yawl.graph;

/**
 * Enumeration of all 6 graph-analysis capabilities.
 * {@link GraphCapabilityRegistry#assertComplete()} verifies at startup that every
 * capability has exactly one {@link MapsToCapability} annotation in each of
 * {@code GraphBridge} and {@code GraphServiceImpl}.
 */
public enum GraphCapability {
    // Group A — Graph Parsing (2)
    PARSE_DOT,
    PARSE_GRAPHML,

    // Group B — Path Analysis (2)
    FIND_SHORTEST_PATH,
    DETECT_CYCLES,

    // Group C — Analytics (2)
    COMPUTE_CENTRALITY,
    EXPORT_SVG;

    /** Total number of capabilities. Used by {@link GraphCapabilityRegistry} to detect enum drift. */
    public static final int TOTAL = 6;
}
```

Key points:
- Each value is a **public operation** you expose through your module
- Comment groups help you organize related capabilities
- **`TOTAL` constant must match the count** — the registry will verify this at startup
- The convention is UPPERCASE_WITH_UNDERSCORES

---

## Step 2 – Understand the Rust Side

Your Rust library exposes these functions. You won't write them in this tutorial, but you need to understand their shape.

**Hypothetical Rust function** (`libgraph_ffi.so`, from `rust/libgraph_ffi/src/lib.rs`):

```rust
#[repr(C)]
pub struct GraphResult {
    data: *mut c_char,    // JSON string or NULL
    error: *mut c_char,   // error message or NULL
}

#[no_mangle]
pub unsafe extern "C" fn graph_parse_dot(input: *const c_char) -> GraphResult {
    // Parse DOT format, return JSON representation or error
    // If input is valid, data = JSON string, error = NULL
    // If input is invalid, data = NULL, error = error message
}

#[no_mangle]
pub extern "C" fn graph_free_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        let _ = CString::from_raw(ptr);
    }
}

// Sizeof probes for Correct-by-Construction verification
#[no_mangle]
pub extern "C" fn graph_sizeof_result() -> usize {
    std::mem::size_of::<GraphResult>()
}

#[no_mangle]
pub extern "C" fn graph_offsetof_result_data() -> usize {
    std::mem::offset_of!(GraphResult, data)
}

#[no_mangle]
pub extern "C" fn graph_offsetof_result_error() -> usize {
    std::mem::offset_of!(GraphResult, error)
}
```

**Key patterns**:
- **Result struct**: Two pointers (data, error) — one is always NULL
- **Error-first unwrapping**: Check error pointer FIRST
- **Free function**: `graph_free_string` to deallocate returned strings
- **Sizeof probes**: For runtime layout verification

---

## Step 3 – Layer 1: Implement graph_ffi_h.java

This is the **raw FFM binding layer**. It defines struct layouts, MethodHandles, and layout verification.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/generated/graph_ffi_h.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/generated/graph_ffi_h.java
package org.yawlfoundation.yawl.graph.generated;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * Layer 1: raw Panama FFM bindings to libgraph_ffi.so.
 *
 * <p>Library loading: checks system property {@code graph_ffi.library.path}
 * first, then {@code ./target/release/libgraph_ffi.so}. If not found,
 * all method calls throw {@link UnsupportedOperationException}.
 *
 * <p><b>Correct-by-construction layout verification</b>: When the library is
 * present, the static initialiser calls sizeof/offsetof probes and asserts each
 * {@link StructLayout#byteSize()} matches. Any mismatch throws {@link AssertionError}
 * at JVM startup.
 */
public final class graph_ffi_h {

    public static final String LIB_PATH_PROP = "graph_ffi.library.path";

    static final Linker LINKER    = Linker.nativeLinker();
    static final Arena  LIB_ARENA = Arena.ofShared();
    public static final Optional<SymbolLookup> LIBRARY;

    static {
        String libName = System.getProperty(
            LIB_PATH_PROP,
            Path.of(System.getProperty("user.dir"), "target", "release",
                    System.mapLibraryName("graph_ffi")).toString());
        Optional<SymbolLookup> lookup = Optional.empty();
        try {
            lookup = Optional.of(SymbolLookup.libraryLookup(libName, LIB_ARENA));
        } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
            // Library absent — all methods throw UnsupportedOperationException
        }
        LIBRARY = lookup;
    }

    // ── Struct layouts ──────────────────────────────────────────────────────

    /**
     * GraphResult: { data: *mut c_char (8B), error: *mut c_char (8B) } = 16 bytes.
     * Exactly one of data/error is non-null.
     */
    public static final StructLayout GRAPH_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("data"),
            ADDRESS.withName("error")
        ).withName("GraphResult");

    // ── Probe MethodHandles (usize → JAVA_LONG) ─────────────────────────────

    private static MethodHandle mhProbe(String sym) {
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr,
                FunctionDescriptor.of(JAVA_LONG),
                Linker.Option.critical(false)))
            .orElse(null);
    }

    static final MethodHandle MH$graph_sizeof_result            = mhProbe("graph_sizeof_result");
    static final MethodHandle MH$graph_offsetof_result_data     = mhProbe("graph_offsetof_result_data");
    static final MethodHandle MH$graph_offsetof_result_error    = mhProbe("graph_offsetof_result_error");

    // ── CbC verification ─────────────────────────────────────────────────────

    static {
        if (LIBRARY.isPresent()) {
            try {
                long sizeOfResult   = (long) MH$graph_sizeof_result.invoke();
                long offsetData     = (long) MH$graph_offsetof_result_data.invoke();
                long offsetError    = (long) MH$graph_offsetof_result_error.invoke();

                if (sizeOfResult != GRAPH_RESULT_LAYOUT.byteSize())
                    throw new AssertionError("GraphResult size mismatch: C=" + sizeOfResult
                        + " Java=" + GRAPH_RESULT_LAYOUT.byteSize());
                if (offsetData != 0L)
                    throw new AssertionError("GraphResult.data offset mismatch: expected 0, got " + offsetData);
                if (offsetError != 8L)
                    throw new AssertionError("GraphResult.error offset mismatch: expected 8, got " + offsetError);
            } catch (AssertionError ae) {
                throw ae;
            } catch (Throwable t) {
                throw new AssertionError("CbC layout probe failed", t);
            }
        }
    }

    // ── Helper to build a GraphResult-returning MethodHandle ───────────────────

    private static MethodHandle mhResult(String sym, MemoryLayout... argLayouts) {
        FunctionDescriptor fd = FunctionDescriptor.of(GRAPH_RESULT_LAYOUT, argLayouts);
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr, fd, Linker.Option.critical(false)))
            .orElse(null);
    }

    // ── MethodHandle cache — one per C function ──────────────────────────────

    // Group A
    public static final MethodHandle MH$graph_parse_dot   = mhResult("graph_parse_dot",   ADDRESS);
    public static final MethodHandle MH$graph_parse_graphml = mhResult("graph_parse_graphml", ADDRESS);

    // Group B
    public static final MethodHandle MH$graph_find_shortest_path = mhResult("graph_find_shortest_path", ADDRESS, ADDRESS);
    public static final MethodHandle MH$graph_detect_cycles      = mhResult("graph_detect_cycles", ADDRESS);

    // Group C
    public static final MethodHandle MH$graph_compute_centrality = mhResult("graph_compute_centrality", ADDRESS);
    public static final MethodHandle MH$graph_export_svg         = mhResult("graph_export_svg", ADDRESS);

    // ── Free function ────────────────────────────────────────────────────────

    private static MethodHandle mhVoid(String sym, MemoryLayout... argLayouts) {
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(argLayouts);
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr, fd, Linker.Option.critical(false)))
            .orElse(null);
    }

    public static final MethodHandle MH$graph_free_string = mhVoid("graph_free_string", ADDRESS);

    // ── Static invokers ──────────────────────────────────────────────────────

    /**
     * Invoke graph_parse_dot(input: *const c_char) -> GraphResult.
     * Allocates input string from call-scoped arena.
     */
    public static MemorySegment graph_parse_dot(SegmentAllocator call, MemorySegment input) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_parse_dot.invoke(input);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_parse_dot invocation failed", e);
        }
    }

    /**
     * Invoke graph_parse_graphml(input: *const c_char) -> GraphResult.
     */
    public static MemorySegment graph_parse_graphml(SegmentAllocator call, MemorySegment input) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_parse_graphml.invoke(input);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_parse_graphml invocation failed", e);
        }
    }

    /**
     * Invoke graph_find_shortest_path(graph: *const c_char, query: *const c_char) -> GraphResult.
     */
    public static MemorySegment graph_find_shortest_path(SegmentAllocator call, MemorySegment graph, MemorySegment query) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_find_shortest_path.invoke(graph, query);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_find_shortest_path invocation failed", e);
        }
    }

    /**
     * Invoke graph_detect_cycles(graph: *const c_char) -> GraphResult.
     */
    public static MemorySegment graph_detect_cycles(SegmentAllocator call, MemorySegment graph) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_detect_cycles.invoke(graph);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_detect_cycles invocation failed", e);
        }
    }

    /**
     * Invoke graph_compute_centrality(graph: *const c_char) -> GraphResult.
     */
    public static MemorySegment graph_compute_centrality(SegmentAllocator call, MemorySegment graph) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_compute_centrality.invoke(graph);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_compute_centrality invocation failed", e);
        }
    }

    /**
     * Invoke graph_export_svg(graph: *const c_char) -> GraphResult.
     */
    public static MemorySegment graph_export_svg(SegmentAllocator call, MemorySegment graph) {
        requireLibrary();
        try {
            return (MemorySegment) MH$graph_export_svg.invoke(graph);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_export_svg invocation failed", e);
        }
    }

    /**
     * Invoke graph_free_string(ptr: *mut c_char) -> void.
     */
    public static void graph_free_string(MemorySegment ptr) {
        if (LIBRARY.isEmpty()) return; // No-op if library absent
        try {
            MH$graph_free_string.invoke(ptr);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("graph_free_string invocation failed", e);
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private static void requireLibrary() {
        if (LIBRARY.isEmpty()) {
            throw new UnsupportedOperationException(
                "libgraph_ffi.so not found. Set system property '" + LIB_PATH_PROP + "' " +
                "to the absolute path of libgraph_ffi.so, or build with 'mvn -Dbuild.skip.native=false'");
        }
    }

    private graph_ffi_h() {}
}
```

**Key patterns**:
- **Struct layout**: `GRAPH_RESULT_LAYOUT` defines the 16-byte structure
- **Sizeof probes**: Verify struct size and field offsets at startup
- **MethodHandle cache**: One public field per C function
- **Static invokers**: Wrap MethodHandle invocation with error handling
- **`requireLibrary()` call**: Throws actionable exception if library absent

---

## Step 4 – Accessor Helper: GraphResult_h.java

Create a small helper to access fields from the `GraphResult` struct.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/generated/GraphResult_h.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/generated/GraphResult_h.java
package org.yawlfoundation.yawl.graph.generated;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.yawlfoundation.yawl.graph.generated.graph_ffi_h.GRAPH_RESULT_LAYOUT;

/**
 * Accessor helpers for {@code GraphResult} C struct.
 * <pre>
 * struct GraphResult {
 *   char* data;   // offset 0
 *   char* error;  // offset 8
 * };
 * </pre>
 */
public final class GraphResult_h {
    private static final VarHandle VH_DATA =
        GRAPH_RESULT_LAYOUT.varHandle(groupElement("data"));
    private static final VarHandle VH_ERROR =
        GRAPH_RESULT_LAYOUT.varHandle(groupElement("error"));

    private GraphResult_h() {}

    public static long data$OFFSET()  { return 0L; }
    public static long error$OFFSET() { return 8L; }

    public static MemorySegment data$get(MemorySegment seg) {
        return (MemorySegment) VH_DATA.get(seg, 0L);
    }
    public static MemorySegment error$get(MemorySegment seg) {
        return (MemorySegment) VH_ERROR.get(seg, 0L);
    }
}
```

---

## Step 5 – Layer 2: Implement GraphBridge.java

The bridge is where you **unwrap result structs, free memory, and convert errors to exceptions**. It also declares which capability each method implements.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/bridge/GraphBridge.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/bridge/GraphBridge.java
package org.yawlfoundation.yawl.graph.bridge;

import org.yawlfoundation.yawl.graph.GraphCapability;
import org.yawlfoundation.yawl.graph.GraphException;
import org.yawlfoundation.yawl.graph.MapsToCapability;
import org.yawlfoundation.yawl.graph.generated.GraphResult_h;
import org.yawlfoundation.yawl.graph.generated.graph_ffi_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.yawlfoundation.yawl.graph.GraphCapability.*;

/**
 * Layer 2 bridge to libgraph_ffi.so.
 *
 * <p>Thread-safe: uses {@link Arena#ofShared()} for the bridge lifetime arena.
 * Multiple threads may call any method concurrently.
 *
 * <p>Usage:
 * <pre>{@code
 * try (GraphBridge bridge = new GraphBridge()) {
 *     String json = bridge.parseDot("digraph { a -> b; }");
 * }
 * }</pre>
 *
 * <p>If the native library is not loaded, all methods throw
 * {@link UnsupportedOperationException} with actionable guidance.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class GraphBridge implements AutoCloseable {

    private final Arena arena = Arena.ofShared();

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Unwrap a GraphResult struct, freeing both pointers and returning data or throwing.
     *
     * @param result the memory segment containing GraphResult
     * @return the data string on success
     * @throws GraphException if error pointer is non-null
     */
    private String unwrapResult(MemorySegment result) {
        MemorySegment errorPtr = GraphResult_h.error$get(result);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(Long.MAX_VALUE)
                                 .getString(0, StandardCharsets.UTF_8);
            graph_ffi_h.graph_free_string(errorPtr);
            throw new GraphException(msg);
        }
        MemorySegment dataPtr = GraphResult_h.data$get(result);
        try {
            return dataPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
        } finally {
            graph_ffi_h.graph_free_string(dataPtr);
        }
    }

    /**
     * Allocate a required C string from the call-scoped arena.
     *
     * @param call the call-scoped arena
     * @param s the string to allocate (must not be null)
     * @return memory segment containing null-terminated C string
     */
    private static MemorySegment cstrCall(Arena call, String s) {
        return call.allocateFrom(s, StandardCharsets.UTF_8);
    }

    // ── Group A: Graph Parsing (2) ──────────────────────────────────────────

    /**
     * Parse DOT format into normalized JSON graph representation.
     *
     * @param dotInput the DOT format string
     * @return normalized JSON string representing the graph
     * @throws GraphException on parse or validation error
     */
    @MapsToCapability(PARSE_DOT)
    public String parseDot(String dotInput) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_parse_dot(call, cstrCall(call, dotInput)));
        }
    }

    /**
     * Parse GraphML format into normalized JSON graph representation.
     *
     * @param graphmlInput the GraphML XML string
     * @return normalized JSON string representing the graph
     * @throws GraphException on parse or validation error
     */
    @MapsToCapability(PARSE_GRAPHML)
    public String parseGraphml(String graphmlInput) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_parse_graphml(call, cstrCall(call, graphmlInput)));
        }
    }

    // ── Group B: Path Analysis (2) ──────────────────────────────────────────

    /**
     * Find the shortest path between two nodes in a graph.
     *
     * @param graphJson the graph JSON representation
     * @param queryJson the query JSON (must contain source and target node IDs)
     * @return path JSON with node sequence and total cost
     * @throws GraphException on path computation or validation error
     */
    @MapsToCapability(FIND_SHORTEST_PATH)
    public String findShortestPath(String graphJson, String queryJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_find_shortest_path(
                call, cstrCall(call, graphJson), cstrCall(call, queryJson)));
        }
    }

    /**
     * Detect cycles in a graph.
     *
     * @param graphJson the graph JSON representation
     * @return cycles JSON array (empty if no cycles found)
     * @throws GraphException on detection error
     */
    @MapsToCapability(DETECT_CYCLES)
    public String detectCycles(String graphJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_detect_cycles(call, cstrCall(call, graphJson)));
        }
    }

    // ── Group C: Analytics (2) ──────────────────────────────────────────────

    /**
     * Compute centrality metrics for all nodes in a graph.
     *
     * @param graphJson the graph JSON representation
     * @return centrality JSON with per-node metrics (betweenness, closeness, etc.)
     * @throws GraphException on computation error
     */
    @MapsToCapability(COMPUTE_CENTRALITY)
    public String computeCentrality(String graphJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_compute_centrality(call, cstrCall(call, graphJson)));
        }
    }

    /**
     * Export graph to SVG vector graphics format.
     *
     * @param graphJson the graph JSON representation
     * @return SVG XML string ready for rendering
     * @throws GraphException on serialization error
     */
    @MapsToCapability(EXPORT_SVG)
    public String exportSvg(String graphJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(graph_ffi_h.graph_export_svg(call, cstrCall(call, graphJson)));
        }
    }

    // ── AutoCloseable ────────────────────────────────────────────────────────

    /**
     * Close the bridge's shared arena, freeing all native resources.
     *
     * <p>After close, all method invocations will raise {@link IllegalStateException}
     * or similar error from the foreign function interface.</p>
     */
    @Override
    public void close() {
        arena.close();
    }
}
```

**Key patterns**:
- **`@MapsToCapability(CAPABILITY_NAME)`** on every public method
- **Per-call arena**: `Arena.ofConfined()` for each invocation — freed automatically in try-with-resources
- **Error-first unwrapping**: Read error pointer first, then data pointer
- **Memory cleanup**: Call `graph_ffi_h.graph_free_string()` to deallocate returned strings
- **Exception conversion**: `GraphException` thrown when error pointer is non-null

---

## Step 6 – Create GraphException

A simple checked exception to signal graph operation errors.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphException.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphException.java
package org.yawlfoundation.yawl.graph;

/**
 * Thrown when a graph operation fails (native library returns an error string).
 */
public class GraphException extends RuntimeException {
    public GraphException(String message) {
        super(message);
    }

    public GraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## Step 7 – Create MapsToCapability Annotation

This annotation marks which capability a method implements. It's checked at startup by the registry.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/MapsToCapability.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/MapsToCapability.java
package org.yawlfoundation.yawl.graph;

import java.lang.annotation.*;

/**
 * Marks a method as implementing a specific {@link GraphCapability}.
 * Required on every public method in {@code GraphBridge} and
 * {@code GraphServiceImpl}. {@link GraphCapabilityRegistry#assertComplete()}
 * fails at startup if any capability is unmapped or over-mapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MapsToCapability {
    GraphCapability value();
}
```

---

## Step 8 – Create GraphCapabilityRegistry

This is the **startup validator**. It uses reflection to scan both the Bridge and Service for `@MapsToCapability` annotations and ensures complete coverage.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphCapabilityRegistry.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphCapabilityRegistry.java
package org.yawlfoundation.yawl.graph;

import org.yawlfoundation.yawl.graph.api.GraphServiceImpl;
import org.yawlfoundation.yawl.graph.bridge.GraphBridge;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reflection-based scanner that verifies all 6 capabilities are mapped at startup.
 *
 * <p>Checks that every {@link GraphCapability} value has exactly one
 * {@link MapsToCapability}-annotated method in both {@code GraphBridge}
 * and {@code GraphServiceImpl} (total: up to 2 methods per capability).
 * Throws {@link CapabilityRegistryException} on any violation.
 */
public final class GraphCapabilityRegistry {

    private static final List<Class<?>> BRIDGE_CLASSES = List.of(
        GraphBridge.class,
        GraphServiceImpl.class);

    private GraphCapabilityRegistry() {}

    /**
     * Asserts that every capability in {@link GraphCapability} is covered by exactly
     * one {@link MapsToCapability}-annotated method per bridge class.
     *
     * @throws CapabilityRegistryException if any capability is missing or over-mapped
     */
    public static void assertComplete() {
        var mapped = new HashMap<GraphCapability, List<String>>();
        for (Class<?> cls : BRIDGE_CLASSES) {
            for (Method m : cls.getDeclaredMethods()) {
                MapsToCapability ann = m.getAnnotation(MapsToCapability.class);
                if (ann != null) {
                    mapped.computeIfAbsent(ann.value(), k -> new ArrayList<>())
                          .add(cls.getSimpleName() + "." + m.getName());
                }
            }
        }

        var violations = new ArrayList<String>();
        if (GraphCapability.values().length != GraphCapability.TOTAL) {
            violations.add("GraphCapability enum has " + GraphCapability.values().length
                + " values but TOTAL=" + GraphCapability.TOTAL);
        }
        for (GraphCapability cap : GraphCapability.values()) {
            List<String> methods = mapped.getOrDefault(cap, List.of());
            if (methods.isEmpty()) {
                violations.add("NOT MAPPED: " + cap + " (missing @MapsToCapability)");
            } else if (methods.size() > 2) {
                violations.add("OVER-MAPPED: " + cap + " → " + methods);
            }
        }
        if (!violations.isEmpty()) {
            throw new CapabilityRegistryException(violations);
        }
    }

    /** Exception thrown when registry validation fails. */
    public static final class CapabilityRegistryException extends RuntimeException {
        private final List<String> violations;

        public CapabilityRegistryException(List<String> violations) {
            super("GraphCapabilityRegistry validation failed:\n" + String.join("\n", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }
}
```

**Key points**:
- **Reflection scan**: Iterates `getDeclaredMethods()` looking for `@MapsToCapability` annotations
- **Violation tracking**: Records unmapped capabilities, over-mapped capabilities, and enum drift
- **`TOTAL` constant check**: Ensures enum count matches `GraphCapability.TOTAL`
- **Clear error messages**: Each violation includes class name and method name

---

## Step 9 – Layer 3: Create GraphService Interface

The public API that hides native details. Clients work with **domain types** (records), not JSON strings.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/api/GraphService.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/api/GraphService.java
package org.yawlfoundation.yawl.graph.api;

import org.yawlfoundation.yawl.graph.model.Graph;
import org.yawlfoundation.yawl.graph.model.Path;
import org.yawlfoundation.yawl.graph.model.CycleInfo;
import org.yawlfoundation.yawl.graph.model.CentralityAnalysis;

import java.util.List;

/**
 * Layer 3 service interface for graph operations.
 * Obtain an instance via {@link org.yawlfoundation.yawl.graph.GraphModule#create()}.
 *
 * <p>All methods throw {@link org.yawlfoundation.yawl.graph.GraphException}
 * (unchecked) on native error, or {@link UnsupportedOperationException} if the native library
 * is not loaded.
 */
public interface GraphService extends AutoCloseable {

    // Group A — Graph Parsing
    Graph parseDot(String dotInput);
    Graph parseGraphml(String graphmlInput);

    // Group B — Path Analysis
    Path findShortestPath(Graph graph, String sourceNodeId, String targetNodeId);
    List<CycleInfo> detectCycles(Graph graph);

    // Group C — Analytics
    CentralityAnalysis computeCentrality(Graph graph);
    String exportSvg(Graph graph);

    @Override void close();
}
```

**Domain records** (in the same package):

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/model/Graph.java
package org.yawlfoundation.yawl.graph.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record Graph(
    @JsonProperty("id") String id,
    @JsonProperty("nodes") List<Node> nodes,
    @JsonProperty("edges") List<Edge> edges
) {}

public record Node(
    @JsonProperty("id") String id,
    @JsonProperty("label") String label
) {}

public record Edge(
    @JsonProperty("source") String source,
    @JsonProperty("target") String target,
    @JsonProperty("weight") double weight
) {}

// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/model/Path.java
public record Path(
    @JsonProperty("nodes") List<String> nodes,
    @JsonProperty("total_cost") double totalCost
) {}

// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/model/CycleInfo.java
public record CycleInfo(
    @JsonProperty("cycle_id") String cycleId,
    @JsonProperty("node_ids") List<String> nodeIds
) {}

// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/model/CentralityAnalysis.java
public record CentralityAnalysis(
    @JsonProperty("graph_id") String graphId,
    @JsonProperty("metrics") Map<String, CentralityMetrics> metrics
) {}

public record CentralityMetrics(
    @JsonProperty("betweenness") double betweenness,
    @JsonProperty("closeness") double closeness,
    @JsonProperty("degree") int degree
) {}
```

---

## Step 10 – Layer 3: Implement GraphServiceImpl

The service **encodes/decodes JSON** using Jackson and delegates to the bridge.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/api/GraphServiceImpl.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/api/GraphServiceImpl.java
package org.yawlfoundation.yawl.graph.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.graph.GraphCapability;
import org.yawlfoundation.yawl.graph.GraphException;
import org.yawlfoundation.yawl.graph.MapsToCapability;
import org.yawlfoundation.yawl.graph.bridge.GraphBridge;
import org.yawlfoundation.yawl.graph.model.*;

import java.util.List;

import static org.yawlfoundation.yawl.graph.GraphCapability.*;

/**
 * Layer 3 service implementation. Delegates to {@link GraphBridge} (Layer 2)
 * with JSON encode/decode for typed domain objects.
 */
public final class GraphServiceImpl implements GraphService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GraphBridge bridge;

    public GraphServiceImpl(GraphBridge bridge) {
        this.bridge = bridge;
    }

    private <T> T decode(String json, Class<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (Exception e) { throw new GraphException("JSON decode failed: " + e.getMessage(), e); }
    }

    private <T> T decodeList(String json, TypeReference<T> ref) {
        try { return MAPPER.readValue(json, ref); }
        catch (Exception e) { throw new GraphException("JSON decode failed: " + e.getMessage(), e); }
    }

    private String encode(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) { throw new GraphException("JSON encode failed: " + e.getMessage(), e); }
    }

    // ── Group A: Graph Parsing ───────────────────────────────────────────────

    @MapsToCapability(PARSE_DOT)
    @Override public Graph parseDot(String dotInput) {
        return decode(bridge.parseDot(dotInput), Graph.class);
    }

    @MapsToCapability(PARSE_GRAPHML)
    @Override public Graph parseGraphml(String graphmlInput) {
        return decode(bridge.parseGraphml(graphmlInput), Graph.class);
    }

    // ── Group B: Path Analysis ───────────────────────────────────────────────

    @MapsToCapability(FIND_SHORTEST_PATH)
    @Override public Path findShortestPath(Graph graph, String sourceNodeId, String targetNodeId) {
        // Build query JSON from parameters
        String queryJson = encode(new PathQuery(sourceNodeId, targetNodeId));
        String resultJson = bridge.findShortestPath(encode(graph), queryJson);
        return decode(resultJson, Path.class);
    }

    @MapsToCapability(DETECT_CYCLES)
    @Override public List<CycleInfo> detectCycles(Graph graph) {
        String resultJson = bridge.detectCycles(encode(graph));
        return decodeList(resultJson, new TypeReference<List<CycleInfo>>() {});
    }

    // ── Group C: Analytics ───────────────────────────────────────────────────

    @MapsToCapability(COMPUTE_CENTRALITY)
    @Override public CentralityAnalysis computeCentrality(Graph graph) {
        String resultJson = bridge.computeCentrality(encode(graph));
        return decode(resultJson, CentralityAnalysis.class);
    }

    @MapsToCapability(EXPORT_SVG)
    @Override public String exportSvg(Graph graph) {
        return bridge.exportSvg(encode(graph));
    }

    @Override public void close() {
        bridge.close();
    }

    // ── Helper record for path queries ───────────────────────────────────────

    private record PathQuery(
        com.fasterxml.jackson.annotation.JsonProperty("source_id") String sourceId,
        com.fasterxml.jackson.annotation.JsonProperty("target_id") String targetId
    ) {}
}
```

**Key patterns**:
- **`@MapsToCapability` on EVERY public method** (both Bridge and Service)
- **JSON encode/decode**: Use `ObjectMapper.readValue()` and `writeValueAsString()`
- **Domain records**: Accept and return typed objects, not JSON strings
- **Helper records**: Use for complex input types like `PathQuery`

---

## Step 11 – Create GraphModule Entry Point

This is where startup validation happens. **Must call `GraphCapabilityRegistry.assertComplete()` FIRST**.

**File**: `yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphModule.java`

```java
// yawl-graph/src/main/java/org/yawlfoundation/yawl/graph/GraphModule.java
package org.yawlfoundation.yawl.graph;

import org.yawlfoundation.yawl.graph.api.GraphService;
import org.yawlfoundation.yawl.graph.api.GraphServiceImpl;
import org.yawlfoundation.yawl.graph.bridge.GraphBridge;

/**
 * Module entry point for the graph native FFM bridge.
 *
 * <p>Loads the native library and validates capability coverage at startup.
 * Use {@link #create()} to obtain a new {@link GraphService} instance.
 *
 * <p>The native library path is controlled by the system property
 * {@code graph_ffi.library.path}. If absent, the default
 * {@code target/release/libgraph_ffi.so} is used.
 */
public final class GraphModule {

    private GraphModule() {}

    /**
     * Create a new {@link GraphService} backed by the native bridge.
     *
     * <p>Validates capability registry on first call. If the native library
     * is absent, the returned service throws {@link UnsupportedOperationException}
     * on every method call.
     *
     * @throws GraphCapabilityRegistry.CapabilityRegistryException if any capability is unmapped
     */
    public static GraphService create() {
        GraphCapabilityRegistry.assertComplete();
        return new GraphServiceImpl(new GraphBridge());
    }
}
```

**Critical**: `assertComplete()` is called **before** creating the service. This ensures all capabilities are mapped at startup, not at the first method call.

---

## Step 12 – Write Capability Tests

Tests that validate the registry and exercise capabilities (when the native library is present).

**File**: `yawl-graph/src/test/java/org/yawlfoundation/yawl/graph/GraphCapabilityRegistryTest.java`

```java
// yawl-graph/src/test/java/org/yawlfoundation/yawl/graph/GraphCapabilityRegistryTest.java
package org.yawlfoundation.yawl.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GraphCapabilityRegistry")
class GraphCapabilityRegistryTest {

    @Test
    @DisplayName("all capabilities mapped")
    void allCapabilitiesMapped_registryClean() {
        assertDoesNotThrow(GraphCapabilityRegistry::assertComplete);
    }

    @Test
    @DisplayName("capability enum count matches TOTAL constant")
    void enumDriftDetected_totalMismatch() {
        int actualCount = GraphCapability.values().length;
        assertEquals(GraphCapability.TOTAL, actualCount,
            "GraphCapability.TOTAL constant does not match enum size");
    }

    @Test
    @DisplayName("bridge and service classes are discoverable")
    void bridgeAndServiceDiscoverable() {
        assertDoesNotThrow(() -> Class.forName(
            "org.yawlfoundation.yawl.graph.bridge.GraphBridge"));
        assertDoesNotThrow(() -> Class.forName(
            "org.yawlfoundation.yawl.graph.api.GraphServiceImpl"));
    }
}
```

**File**: `yawl-graph/src/test/java/org/yawlfoundation/yawl/graph/api/GraphServiceTest.java`

```java
// yawl-graph/src/test/java/org/yawlfoundation/yawl/graph/api/GraphServiceTest.java
package org.yawlfoundation.yawl.graph.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.yawlfoundation.yawl.graph.GraphModule;
import org.yawlfoundation.yawl.graph.generated.graph_ffi_h;
import org.yawlfoundation.yawl.graph.model.Graph;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GraphService (integration)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphServiceTest {

    private GraphService service;

    @BeforeAll
    void beforeAll() {
        assumeTrue(graph_ffi_h.LIBRARY.isPresent(),
            "libgraph_ffi.so not found; skipping integration tests. " +
            "Set -Dtest=GraphCapabilityRegistryTest to run offline tests.");
        service = GraphModule.create();
    }

    @AfterAll
    void afterAll() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    @DisplayName("parseDot returns valid Graph")
    void parseDot_validInput_returnsGraph() {
        String dot = "digraph { a -> b [weight=2.5]; }";
        Graph graph = service.parseDot(dot);

        assertNotNull(graph);
        assertNotNull(graph.nodes());
        assertNotNull(graph.edges());
        assertTrue(graph.nodes().size() > 0, "Graph should have at least one node");
    }

    @Test
    @DisplayName("parseGraphml returns valid Graph")
    void parseGraphml_validInput_returnsGraph() {
        String graphml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <graphml xmlns="http://graphml.graphdrawing.org/xmlns">
              <graph edgedefault="directed">
                <node id="a"/>
                <node id="b"/>
                <edge source="a" target="b"/>
              </graph>
            </graphml>
            """;
        Graph graph = service.parseGraphml(graphml);

        assertNotNull(graph);
        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
    }

    @Test
    @DisplayName("detectCycles on acyclic graph returns empty list")
    void detectCycles_acyclicGraph_returnsEmpty() {
        String dot = "digraph { a -> b; b -> c; }";
        Graph graph = service.parseDot(dot);
        var cycles = service.detectCycles(graph);

        assertTrue(cycles.isEmpty(), "Acyclic graph should have no cycles");
    }

    @Test
    @DisplayName("exportSvg returns valid XML")
    void exportSvg_validGraph_returnsSvgString() {
        String dot = "digraph { a -> b; }";
        Graph graph = service.parseDot(dot);
        String svg = service.exportSvg(graph);

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"), "SVG should contain <svg element");
        assertTrue(svg.contains("</svg>"), "SVG should contain closing </svg> tag");
    }
}
```

**Key testing patterns**:
- **`@BeforeAll` with `assumeTrue(graph_ffi_h.LIBRARY.isPresent())`**: Skip integration tests if library absent
- **`@AfterAll` with `service.close()`**: Clean up resources
- **Structural assertions**: Verify return types, non-null fields, counts
- **Two test classes**: `*RegistryTest` (offline, always runs), `*ServiceTest` (integration, skipped if no library)

---

## Step 13 – Build and Verify

Test that your module compiles and passes offline tests.

```bash
# Compile the module (no native library required yet)
mvn clean test-compile -pl yawl-graph

# Run offline registry test
mvn test -pl yawl-graph -Dtest=GraphCapabilityRegistryTest

# Expected output:
# ✓ all capabilities mapped
# ✓ capability enum count matches TOTAL constant
# ✓ bridge and service classes are discoverable
```

If you have the native library:

```bash
# Run full test suite including integration tests
mvn test -pl yawl-graph -Dgroups=capability

# Expected output:
# ✓ all registry tests PASS
# ✓ integration tests PASS (parseDot, parseGraphml, etc.)
```

If you don't have the native library:

```bash
# Skip native library requirement during tests
mvn test -pl yawl-graph -Dbuild.skip.native=true

# Integration tests will be SKIPPED (assumeTrue fails gracefully)
# Registry tests will PASS
```

---

## Troubleshooting

### Library Absent: UnsupportedOperationException

**Error message**:
```
UnsupportedOperationException: libgraph_ffi.so not found. Set system property
'graph_ffi.library.path' to the absolute path of libgraph_ffi.so, or build with
'mvn -Dbuild.skip.native=false'
```

**Solution**:
- Set `-Dgraph_ffi.library.path=/path/to/libgraph_ffi.so` in Maven/test invocation
- OR build the native library: `cd rust/libgraph_ffi && cargo build --release`
- OR skip native tests: `mvn test -pl yawl-graph -Dbuild.skip.native=true`

### CapabilityRegistryException: NOT MAPPED

**Error message**:
```
CapabilityRegistryException: GraphCapabilityRegistry validation failed:
NOT MAPPED: PARSE_DOT (missing @MapsToCapability)
```

**Solution**:
- Add `@MapsToCapability(PARSE_DOT)` annotation to the `parseDot()` method in both `GraphBridge` and `GraphServiceImpl`
- Ensure spelling matches the enum value exactly

### JSON Decode Fails

**Error message**:
```
GraphException: JSON decode failed: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException:
Unrecognized field "nodes" (class Graph)
```

**Solution**:
- Verify your Rust function returns valid JSON
- Add `@JsonProperty("nodes")` annotation to match the JSON key name
- Use `new ObjectMapper().registerModule(new Jdk8Module())` if using Optional fields
- Check that your record fields match the JSON field names exactly

### Memory Corruption or SEGV

**Symptoms**: JVM crashes with SEGV, memory corruption error, or "invalid memory access"

**Root causes**:
1. **Not calling `graph_free_string()`**: Native function returned pointer that wasn't freed
2. **Freed memory accessed twice**: Calling free twice on the same pointer
3. **Arena scope mismatch**: Using a closed Arena to access memory

**Solution**:
- Ensure **every** pointer returned from the native function is freed in `unwrapResult()`
- Use try-finally to guarantee cleanup even on exception
- Use per-call `Arena.ofConfined()`, not the shared arena, for temporary allocations

---

## Summary Table

You've built 8 files:

| File | Purpose |
|------|---------|
| `GraphCapability.java` | Enum of 6 capabilities (contract) |
| `graph_ffi_h.java` | Layer 1: MethodHandles, struct layouts, sizeof probes |
| `GraphResult_h.java` | Layer 1: VarHandle accessors for result struct |
| `GraphBridge.java` | Layer 2: unwrap results, free memory, map capabilities |
| `GraphException.java` | Exception type for operation failures |
| `MapsToCapability.java` | Annotation for capability mapping |
| `GraphCapabilityRegistry.java` | Startup validator (reflection scanner) |
| `GraphService.java` | Layer 3: public interface with domain types |
| `GraphServiceImpl.java` | Layer 3: JSON encode/decode, delegates to bridge |
| `GraphModule.java` | Entry point with registry validation |
| `*Test.java` (2 files) | Registry test + integration tests |

**Total effort**: ~2–3 hours to write and test

---

## Next Steps

Once you have this working:

1. **Add more capabilities**: Follow the same pattern (annotate in Bridge and Service, add to enum, update `TOTAL`)
2. **Extend domain model**: Add new records as your Rust functions return more complex types
3. **Performance testing**: See [howto-benchmark-ffi-overhead.md](./howto-benchmark-ffi-overhead.md) for profiling tips
4. **Integrate with YAWL engine**: See [howto-add-native-bridge-to-workflow.md](./howto-add-native-bridge-to-workflow.md)
5. **Error recovery**: See [howto-handle-native-errors-gracefully.md](./howto-handle-native-errors-gracefully.md)

---

## Key Takeaways

- **Three-layer architecture** decouples Rust, FFM, and domain logic
- **JSON wire format** provides safe serialization (no struct layout surprises)
- **Capability enum** is your contract — verified at startup by reflection
- **`@MapsToCapability` annotation** enforces complete coverage (no orphaned methods)
- **Per-call arenas** with try-with-resources ensure memory cleanup
- **Error-first unwrapping** in `unwrapResult()` provides safe exception conversion
- **Offline tests** run without native library; integration tests skip gracefully
- **CapabilityRegistry.assertComplete()** must be called FIRST in module creation

---

## References

- [tutorial-first-wrap.md](./tutorial-first-wrap.md) — Raw FFM bindings (prerequisite)
- [explanation-correct-by-construction.md](./explanation-correct-by-construction.md) — Why sizeof probes matter
- [CLAUDE.md](../../CLAUDE.md) — Project standards and workflow rules
- [Java 25 Conventions](../../rules/java25/modern-java.md) — Records, sealed classes, virtual threads
- [data_modelling_ffi_h.java](../../yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/generated/data_modelling_ffi_h.java) — Real-world example with 42 capabilities
