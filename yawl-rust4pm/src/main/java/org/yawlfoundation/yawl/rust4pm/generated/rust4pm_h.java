package org.yawlfoundation.yawl.rust4pm.generated;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * GENERATED — do not edit by hand.
 * Regenerate: bash scripts/jextract-generate.sh
 * Source: rust/rust4pm/rust4pm.h
 *
 * <p>Layer 1: raw Panama FFM bindings to librust4pm.so.
 * One static method per C function. Not for direct use — call via Rust4pmBridge.
 *
 * <p>Library loading: checks system property {@code rust4pm.library.path} first,
 * then {@code ./target/release/librust4pm.so}. If not found, all method calls
 * throw {@link UnsupportedOperationException}.
 *
 * <p><b>Correct-by-construction layout verification</b>: When the library is
 * present, the static initialiser calls {@code rust4pm_sizeof_*} probes and
 * asserts each {@link StructLayout#byteSize()} matches. Any divergence between
 * the hand-written layouts and the actual Rust struct sizes throws
 * {@link AssertionError} at JVM startup — never silently at call time.
 */
public final class rust4pm_h {

    public static final String LIB_PATH_PROP = "rust4pm.library.path";

    static final Linker LINKER    = Linker.nativeLinker();
    static final Arena  LIB_ARENA = Arena.ofShared();
    public static final Optional<SymbolLookup> LIBRARY;

    static {
        String libName = System.getProperty(
            LIB_PATH_PROP,
            Path.of(System.getProperty("user.dir"), "target", "release",
                    System.mapLibraryName("rust4pm")).toString());
        Optional<SymbolLookup> lookup = Optional.empty();
        try {
            lookup = Optional.of(SymbolLookup.libraryLookup(libName, LIB_ARENA));
        } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
            // Library absent — all methods throw UnsupportedOperationException
        }
        LIBRARY = lookup;
    }

    // ── Struct layouts ──────────────────────────────────────────────────────
    // Byte sizes annotated with Rust sizeof for review correctness.
    // Verified at runtime via CbC sizeof assertions below.

    /** OcelLogHandle: { ptr: *mut OcelLogInternal } — 8 bytes on 64-bit */
    public static final StructLayout OCEL_LOG_HANDLE_LAYOUT =
        MemoryLayout.structLayout(ADDRESS.withName("ptr"))
                    .withName("OcelLogHandle");

    /** ParseResult: { handle: OcelLogHandle(8), error: *mut c_char(8) } — 16 bytes */
    public static final StructLayout PARSE_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            OCEL_LOG_HANDLE_LAYOUT.withName("handle"),
            ADDRESS.withName("error")
        ).withName("ParseResult");

    /**
     * OcelEventC: { event_id(*)(8), event_type(*)(8), timestamp_ms(i64)(8), attr_count(usize)(8) }
     * Total: 32 bytes on 64-bit.
     */
    public static final StructLayout OCEL_EVENT_C_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("event_id"),
            ADDRESS.withName("event_type"),
            JAVA_LONG.withName("timestamp_ms"),
            JAVA_LONG.withName("attr_count")    // size_t = u64 on 64-bit
        ).withName("OcelEventC");

    /**
     * OcelEventsResult: { events(*)(8), count(usize)(8), error(*)(8) } — 24 bytes.
     * Events pointer BORROWED from OcelLogInternal — do not free separately.
     */
    public static final StructLayout OCEL_EVENTS_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("events"),
            JAVA_LONG.withName("count"),         // size_t → JAVA_LONG
            ADDRESS.withName("error")
        ).withName("OcelEventsResult");

    /**
     * OcelObjectC: { object_id(*)(8), object_type(*)(8) } — 16 bytes.
     * Strings BORROWED from OcelLogInternal CStrings.
     */
    public static final StructLayout OCEL_OBJECT_C_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("object_id"),
            ADDRESS.withName("object_type")
        ).withName("OcelObjectC");

    /**
     * OcelObjectsResult: { objects(*)(8), count(usize)(8), error(*)(8) } — 24 bytes.
     * Objects pointer BORROWED from OcelLogInternal — do not free separately.
     */
    public static final StructLayout OCEL_OBJECTS_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("objects"),
            JAVA_LONG.withName("count"),         // size_t → JAVA_LONG
            ADDRESS.withName("error")
        ).withName("OcelObjectsResult");

    /** DfgResultC: { json(*mut c_char)(8), error(*mut c_char)(8) } — 16 bytes */
    public static final StructLayout DFG_RESULT_C_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("json"),
            ADDRESS.withName("error")
        ).withName("DfgResultC");

    /** ConformanceResultC: { fitness(f64)(8), precision(f64)(8), error(*)(8) } — 24 bytes */
    public static final StructLayout CONFORMANCE_RESULT_C_LAYOUT =
        MemoryLayout.structLayout(
            JAVA_DOUBLE.withName("fitness"),
            JAVA_DOUBLE.withName("precision"),
            ADDRESS.withName("error")
        ).withName("ConformanceResultC");

    // ── VarHandles — ParseResult ────────────────────────────────────────────

    public static final VarHandle PARSE_RESULT_HANDLE_PTR =
        PARSE_RESULT_LAYOUT.varHandle(groupElement("handle"), groupElement("ptr"));
    public static final VarHandle PARSE_RESULT_ERROR =
        PARSE_RESULT_LAYOUT.varHandle(groupElement("error"));

    // ── VarHandles — OcelEventC ─────────────────────────────────────────────

    public static final VarHandle OCEL_EVENT_C_EVENT_ID =
        OCEL_EVENT_C_LAYOUT.varHandle(groupElement("event_id"));
    public static final VarHandle OCEL_EVENT_C_EVENT_TYPE =
        OCEL_EVENT_C_LAYOUT.varHandle(groupElement("event_type"));
    public static final VarHandle OCEL_EVENT_C_TIMESTAMP_MS =
        OCEL_EVENT_C_LAYOUT.varHandle(groupElement("timestamp_ms"));
    public static final VarHandle OCEL_EVENT_C_ATTR_COUNT =
        OCEL_EVENT_C_LAYOUT.varHandle(groupElement("attr_count"));

    // ── VarHandles — OcelEventsResult ──────────────────────────────────────

    public static final VarHandle OCEL_EVENTS_RESULT_EVENTS =
        OCEL_EVENTS_RESULT_LAYOUT.varHandle(groupElement("events"));
    public static final VarHandle OCEL_EVENTS_RESULT_COUNT =
        OCEL_EVENTS_RESULT_LAYOUT.varHandle(groupElement("count"));
    public static final VarHandle OCEL_EVENTS_RESULT_ERROR =
        OCEL_EVENTS_RESULT_LAYOUT.varHandle(groupElement("error"));

    // ── VarHandles — OcelObjectC ────────────────────────────────────────────

    public static final VarHandle OCEL_OBJECT_C_OBJECT_ID =
        OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_id"));
    public static final VarHandle OCEL_OBJECT_C_OBJECT_TYPE =
        OCEL_OBJECT_C_LAYOUT.varHandle(groupElement("object_type"));

    // ── VarHandles — OcelObjectsResult ─────────────────────────────────────

    public static final VarHandle OCEL_OBJECTS_RESULT_OBJECTS =
        OCEL_OBJECTS_RESULT_LAYOUT.varHandle(groupElement("objects"));
    public static final VarHandle OCEL_OBJECTS_RESULT_COUNT =
        OCEL_OBJECTS_RESULT_LAYOUT.varHandle(groupElement("count"));
    public static final VarHandle OCEL_OBJECTS_RESULT_ERROR =
        OCEL_OBJECTS_RESULT_LAYOUT.varHandle(groupElement("error"));

    // ── VarHandles — DfgResultC ─────────────────────────────────────────────

    public static final VarHandle DFG_RESULT_JSON =
        DFG_RESULT_C_LAYOUT.varHandle(groupElement("json"));
    public static final VarHandle DFG_RESULT_ERROR =
        DFG_RESULT_C_LAYOUT.varHandle(groupElement("error"));

    // ── VarHandles — ConformanceResultC ────────────────────────────────────

    public static final VarHandle CONFORMANCE_FITNESS =
        CONFORMANCE_RESULT_C_LAYOUT.varHandle(groupElement("fitness"));
    public static final VarHandle CONFORMANCE_PRECISION =
        CONFORMANCE_RESULT_C_LAYOUT.varHandle(groupElement("precision"));
    public static final VarHandle CONFORMANCE_ERROR =
        CONFORMANCE_RESULT_C_LAYOUT.varHandle(groupElement("error"));

    // ── MethodHandle cache ──────────────────────────────────────────────────

    static final MethodHandle MH$parse_ocel2_json;
    static final MethodHandle MH$log_event_count;
    static final MethodHandle MH$log_get_events;
    static final MethodHandle MH$log_object_count;
    static final MethodHandle MH$log_get_objects;
    static final MethodHandle MH$discover_dfg;
    static final MethodHandle MH$check_conformance;
    static final MethodHandle MH$log_free;
    static final MethodHandle MH$events_free;
    static final MethodHandle MH$objects_free;
    static final MethodHandle MH$dfg_free;
    static final MethodHandle MH$error_free;
    // sizeof probes
    static final MethodHandle MH$sizeof_ocel_log_handle;
    static final MethodHandle MH$sizeof_parse_result;
    static final MethodHandle MH$sizeof_ocel_event_c;
    static final MethodHandle MH$sizeof_ocel_events_result;
    static final MethodHandle MH$sizeof_ocel_object_c;
    static final MethodHandle MH$sizeof_ocel_objects_result;
    static final MethodHandle MH$sizeof_dfg_result_c;
    static final MethodHandle MH$sizeof_conformance_result_c;

    static {
        if (LIBRARY.isPresent()) {
            SymbolLookup lib = LIBRARY.get();
            MH$parse_ocel2_json = LINKER.downcallHandle(
                lib.find("rust4pm_parse_ocel2_json").orElseThrow(),
                FunctionDescriptor.of(PARSE_RESULT_LAYOUT, ADDRESS, JAVA_LONG));
            MH$log_event_count = LINKER.downcallHandle(
                lib.find("rust4pm_log_event_count").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS),
                Linker.Option.critical(true));
            MH$log_get_events = LINKER.downcallHandle(
                lib.find("rust4pm_log_get_events").orElseThrow(),
                FunctionDescriptor.of(OCEL_EVENTS_RESULT_LAYOUT, ADDRESS));
            MH$log_object_count = LINKER.downcallHandle(
                lib.find("rust4pm_log_object_count").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS),
                Linker.Option.critical(true));
            MH$log_get_objects = LINKER.downcallHandle(
                lib.find("rust4pm_log_get_objects").orElseThrow(),
                FunctionDescriptor.of(OCEL_OBJECTS_RESULT_LAYOUT, ADDRESS));
            MH$discover_dfg = LINKER.downcallHandle(
                lib.find("rust4pm_discover_dfg").orElseThrow(),
                FunctionDescriptor.of(DFG_RESULT_C_LAYOUT, ADDRESS));
            MH$check_conformance = LINKER.downcallHandle(
                lib.find("rust4pm_check_conformance").orElseThrow(),
                FunctionDescriptor.of(CONFORMANCE_RESULT_C_LAYOUT, ADDRESS, ADDRESS, JAVA_LONG));
            MH$log_free = LINKER.downcallHandle(
                lib.find("rust4pm_log_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS),
                Linker.Option.critical(true));
            MH$events_free = LINKER.downcallHandle(
                lib.find("rust4pm_events_free").orElseThrow(),
                FunctionDescriptor.ofVoid(OCEL_EVENTS_RESULT_LAYOUT));
            MH$objects_free = LINKER.downcallHandle(
                lib.find("rust4pm_objects_free").orElseThrow(),
                FunctionDescriptor.ofVoid(OCEL_OBJECTS_RESULT_LAYOUT));
            MH$dfg_free = LINKER.downcallHandle(
                lib.find("rust4pm_dfg_free").orElseThrow(),
                FunctionDescriptor.ofVoid(DFG_RESULT_C_LAYOUT));
            MH$error_free = LINKER.downcallHandle(
                lib.find("rust4pm_error_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS),
                Linker.Option.critical(true));
            // sizeof probes
            MH$sizeof_ocel_log_handle = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_ocel_log_handle").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_parse_result = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_parse_result").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_ocel_event_c = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_ocel_event_c").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_ocel_events_result = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_ocel_events_result").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_ocel_object_c = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_ocel_object_c").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_ocel_objects_result = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_ocel_objects_result").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_dfg_result_c = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_dfg_result_c").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));
            MH$sizeof_conformance_result_c = LINKER.downcallHandle(
                lib.find("rust4pm_sizeof_conformance_result_c").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG), Linker.Option.critical(true));

            // ── Correct-by-construction layout assertions ───────────────────
            // Verify every hand-written StructLayout byte size against the
            // actual Rust sizeof at JVM startup. Mismatch = AssertionError here,
            // not a silent wrong-data read deep in production.
            assertLayout("OcelLogHandle",       OCEL_LOG_HANDLE_LAYOUT,       MH$sizeof_ocel_log_handle);
            assertLayout("ParseResult",         PARSE_RESULT_LAYOUT,          MH$sizeof_parse_result);
            assertLayout("OcelEventC",          OCEL_EVENT_C_LAYOUT,          MH$sizeof_ocel_event_c);
            assertLayout("OcelEventsResult",    OCEL_EVENTS_RESULT_LAYOUT,    MH$sizeof_ocel_events_result);
            assertLayout("OcelObjectC",         OCEL_OBJECT_C_LAYOUT,         MH$sizeof_ocel_object_c);
            assertLayout("OcelObjectsResult",   OCEL_OBJECTS_RESULT_LAYOUT,   MH$sizeof_ocel_objects_result);
            assertLayout("DfgResultC",          DFG_RESULT_C_LAYOUT,          MH$sizeof_dfg_result_c);
            assertLayout("ConformanceResultC",  CONFORMANCE_RESULT_C_LAYOUT,  MH$sizeof_conformance_result_c);
        } else {
            MH$parse_ocel2_json       = null;
            MH$log_event_count        = null;
            MH$log_get_events         = null;
            MH$log_object_count       = null;
            MH$log_get_objects        = null;
            MH$discover_dfg           = null;
            MH$check_conformance      = null;
            MH$log_free               = null;
            MH$events_free            = null;
            MH$objects_free           = null;
            MH$dfg_free               = null;
            MH$error_free             = null;
            MH$sizeof_ocel_log_handle    = null;
            MH$sizeof_parse_result       = null;
            MH$sizeof_ocel_event_c       = null;
            MH$sizeof_ocel_events_result = null;
            MH$sizeof_ocel_object_c      = null;
            MH$sizeof_ocel_objects_result= null;
            MH$sizeof_dfg_result_c       = null;
            MH$sizeof_conformance_result_c = null;
        }
    }

    private static void assertLayout(String name, StructLayout layout, MethodHandle sizeofMH) {
        try {
            long rustSize = (long) sizeofMH.invokeExact();
            long javaSize = layout.byteSize();
            if (rustSize != javaSize) {
                throw new AssertionError(
                    "Layout mismatch for " + name + ": Rust sizeof=" + rustSize +
                    " but Java StructLayout.byteSize()=" + javaSize +
                    ". Regenerate rust4pm_h.java from rust4pm.h.");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            throw new AssertionError("sizeof probe failed for " + name, t);
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public static MemorySegment rust4pm_parse_ocel2_json(
            SegmentAllocator allocator, MemorySegment json, long jsonLen) {
        requireLibrary();
        try {
            return (MemorySegment) MH$parse_ocel2_json.invokeExact(allocator, json, jsonLen);
        } catch (Throwable t) { throw new AssertionError("rust4pm_parse_ocel2_json failed", t); }
    }

    public static long rust4pm_log_event_count(MemorySegment handlePtr) {
        requireLibrary();
        try {
            return (long) MH$log_event_count.invokeExact(handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_log_event_count failed", t); }
    }

    public static MemorySegment rust4pm_log_get_events(
            SegmentAllocator allocator, MemorySegment handlePtr) {
        requireLibrary();
        try {
            return (MemorySegment) MH$log_get_events.invokeExact(allocator, handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_log_get_events failed", t); }
    }

    public static long rust4pm_log_object_count(MemorySegment handlePtr) {
        requireLibrary();
        try {
            return (long) MH$log_object_count.invokeExact(handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_log_object_count failed", t); }
    }

    public static MemorySegment rust4pm_log_get_objects(
            SegmentAllocator allocator, MemorySegment handlePtr) {
        requireLibrary();
        try {
            return (MemorySegment) MH$log_get_objects.invokeExact(allocator, handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_log_get_objects failed", t); }
    }

    public static MemorySegment rust4pm_discover_dfg(
            SegmentAllocator allocator, MemorySegment handlePtr) {
        requireLibrary();
        try {
            return (MemorySegment) MH$discover_dfg.invokeExact(allocator, handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_discover_dfg failed", t); }
    }

    public static MemorySegment rust4pm_check_conformance(
            SegmentAllocator allocator, MemorySegment handlePtr,
            MemorySegment pnmlSeg, long pnmlLen) {
        requireLibrary();
        try {
            return (MemorySegment) MH$check_conformance.invokeExact(
                allocator, handlePtr, pnmlSeg, pnmlLen);
        } catch (Throwable t) { throw new AssertionError("rust4pm_check_conformance failed", t); }
    }

    public static void rust4pm_log_free(MemorySegment handlePtr) {
        if (MH$log_free == null) return;
        try {
            MH$log_free.invokeExact(handlePtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_log_free failed", t); }
    }

    public static void rust4pm_dfg_free(MemorySegment resultSeg) {
        if (MH$dfg_free == null) return;
        try {
            MH$dfg_free.invokeExact(resultSeg);
        } catch (Throwable t) { throw new AssertionError("rust4pm_dfg_free failed", t); }
    }

    public static void rust4pm_error_free(MemorySegment errorPtr) {
        if (MH$error_free == null || MemorySegment.NULL.equals(errorPtr)) return;
        try {
            MH$error_free.invokeExact(errorPtr);
        } catch (Throwable t) { throw new AssertionError("rust4pm_error_free failed", t); }
    }

    static void requireLibrary() {
        if (LIBRARY.isEmpty()) {
            throw new UnsupportedOperationException(
                "rust4pm native library not found.\n" +
                "Build:  bash scripts/build-rust4pm.sh\n" +
                "Set:    -D" + LIB_PATH_PROP + "=/path/to/librust4pm.so\n" +
                "Or put: librust4pm.so at ./target/release/librust4pm.so");
        }
    }

    private rust4pm_h() {}
}
