package org.yawlfoundation.yawl.datamodelling.generated;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * Layer 1: raw Panama FFM bindings to libdata_modelling_ffi.so.
 *
 * <p>Library loading: checks system property {@code data_modelling_ffi.library.path}
 * first, then {@code ./target/release/libdata_modelling_ffi.so}. If not found,
 * all method calls throw {@link UnsupportedOperationException}.
 *
 * <p><b>Correct-by-construction layout verification</b>: When the library is
 * present, the static initialiser calls sizeof/offsetof probes and asserts each
 * {@link StructLayout#byteSize()} matches. Any mismatch throws {@link AssertionError}
 * at JVM startup.
 */
public final class data_modelling_ffi_h {

    public static final String LIB_PATH_PROP = "data_modelling_ffi.library.path";

    static final Linker LINKER    = Linker.nativeLinker();
    static final Arena  LIB_ARENA = Arena.ofShared();
    public static final Optional<SymbolLookup> LIBRARY;

    static {
        String libName = System.getProperty(
            LIB_PATH_PROP,
            Path.of(System.getProperty("user.dir"), "target", "release",
                    System.mapLibraryName("data_modelling_ffi")).toString());
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
     * DmResult: { data: *mut c_char (8B), error: *mut c_char (8B) } = 16 bytes.
     * Exactly one of data/error is non-null.
     */
    public static final StructLayout DM_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("data"),
            ADDRESS.withName("error")
        ).withName("DmResult");

    /**
     * DmVoidResult: { error: *mut c_char (8B) } = 8 bytes.
     * Null on success, non-null on failure.
     */
    public static final StructLayout DM_VOID_RESULT_LAYOUT =
        MemoryLayout.structLayout(
            ADDRESS.withName("error")
        ).withName("DmVoidResult");

    // ── Probe MethodHandles (usize → JAVA_LONG) ─────────────────────────────

    private static MethodHandle mhProbe(String sym) {
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr,
                FunctionDescriptor.of(JAVA_LONG),
                Linker.Option.isTrivial()))
            .orElse(null);
    }

    static final MethodHandle MH$dm_sizeof_dm_result            = mhProbe("dm_sizeof_dm_result");
    static final MethodHandle MH$dm_sizeof_dm_void_result       = mhProbe("dm_sizeof_dm_void_result");
    static final MethodHandle MH$dm_offsetof_dm_result_data     = mhProbe("dm_offsetof_dm_result_data");
    static final MethodHandle MH$dm_offsetof_dm_result_error    = mhProbe("dm_offsetof_dm_result_error");
    static final MethodHandle MH$dm_offsetof_dm_void_result_error = mhProbe("dm_offsetof_dm_void_result_error");

    // ── CbC verification ─────────────────────────────────────────────────────

    static {
        if (LIBRARY.isPresent()) {
            try {
                long sizeOfDmResult     = (long) MH$dm_sizeof_dm_result.invoke();
                long sizeOfDmVoidResult = (long) MH$dm_sizeof_dm_void_result.invoke();
                long offsetData         = (long) MH$dm_offsetof_dm_result_data.invoke();
                long offsetError        = (long) MH$dm_offsetof_dm_result_error.invoke();
                long offsetVoidError    = (long) MH$dm_offsetof_dm_void_result_error.invoke();

                if (sizeOfDmResult != DM_RESULT_LAYOUT.byteSize())
                    throw new AssertionError("DmResult size mismatch: C=" + sizeOfDmResult
                        + " Java=" + DM_RESULT_LAYOUT.byteSize());
                if (sizeOfDmVoidResult != DM_VOID_RESULT_LAYOUT.byteSize())
                    throw new AssertionError("DmVoidResult size mismatch: C=" + sizeOfDmVoidResult
                        + " Java=" + DM_VOID_RESULT_LAYOUT.byteSize());
                if (offsetData != 0L)
                    throw new AssertionError("DmResult.data offset mismatch: expected 0, got " + offsetData);
                if (offsetError != 8L)
                    throw new AssertionError("DmResult.error offset mismatch: expected 8, got " + offsetError);
                if (offsetVoidError != 0L)
                    throw new AssertionError("DmVoidResult.error offset mismatch: expected 0, got " + offsetVoidError);
            } catch (AssertionError ae) {
                throw ae;
            } catch (Throwable t) {
                throw new AssertionError("CbC layout probe failed", t);
            }
        }
    }

    // ── Helper to build a DmResult-returning MethodHandle ───────────────────

    private static MethodHandle mhResult(String sym, MemoryLayout... argLayouts) {
        MemoryLayout[] args = new MemoryLayout[argLayouts.length];
        System.arraycopy(argLayouts, 0, args, 0, argLayouts.length);
        FunctionDescriptor fd = FunctionDescriptor.of(DM_RESULT_LAYOUT, args);
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr, fd, Linker.Option.isTrivial()))
            .orElse(null);
    }

    private static MethodHandle mhVoidResult(String sym, MemoryLayout... argLayouts) {
        FunctionDescriptor fd = FunctionDescriptor.of(DM_VOID_RESULT_LAYOUT, argLayouts);
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr, fd, Linker.Option.isTrivial()))
            .orElse(null);
    }

    private static MethodHandle mhVoid(String sym, MemoryLayout... argLayouts) {
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(argLayouts);
        return LIBRARY.flatMap(lib -> lib.find(sym))
            .map(addr -> LINKER.downcallHandle(addr, fd, Linker.Option.isTrivial()))
            .orElse(null);
    }

    // ── MethodHandle cache — one per C function ──────────────────────────────
    // Group A
    public static final MethodHandle MH$dm_parse_odcs_yaml      = mhResult("dm_parse_odcs_yaml",      ADDRESS);
    public static final MethodHandle MH$dm_export_to_odcs_yaml  = mhResult("dm_export_to_odcs_yaml",  ADDRESS);
    public static final MethodHandle MH$dm_convert_to_odcs      = mhResult("dm_convert_to_odcs",      ADDRESS, ADDRESS);
    // Group B
    public static final MethodHandle MH$dm_import_from_sql      = mhResult("dm_import_from_sql",      ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_export_to_sql        = mhResult("dm_export_to_sql",        ADDRESS, ADDRESS);
    // Group C
    public static final MethodHandle MH$dm_import_from_avro         = mhResult("dm_import_from_avro",         ADDRESS);
    public static final MethodHandle MH$dm_import_from_json_schema  = mhResult("dm_import_from_json_schema",  ADDRESS);
    public static final MethodHandle MH$dm_import_from_protobuf     = mhResult("dm_import_from_protobuf",     ADDRESS);
    public static final MethodHandle MH$dm_import_from_cads         = mhResult("dm_import_from_cads",         ADDRESS);
    public static final MethodHandle MH$dm_import_from_odps         = mhResult("dm_import_from_odps",         ADDRESS);
    // Group D
    public static final MethodHandle MH$dm_export_to_avro           = mhResult("dm_export_to_avro",           ADDRESS);
    public static final MethodHandle MH$dm_export_to_json_schema    = mhResult("dm_export_to_json_schema",    ADDRESS);
    public static final MethodHandle MH$dm_export_to_protobuf       = mhResult("dm_export_to_protobuf",       ADDRESS);
    public static final MethodHandle MH$dm_export_to_cads           = mhResult("dm_export_to_cads",           ADDRESS);
    public static final MethodHandle MH$dm_export_to_odps           = mhResult("dm_export_to_odps",           ADDRESS);
    // Group E
    public static final MethodHandle MH$dm_validate_odps        = mhVoidResult("dm_validate_odps",    ADDRESS);
    // Group F
    public static final MethodHandle MH$dm_import_bpmn_model    = mhResult("dm_import_bpmn_model",    ADDRESS);
    public static final MethodHandle MH$dm_export_bpmn_model    = mhResult("dm_export_bpmn_model",    ADDRESS);
    // Group G
    public static final MethodHandle MH$dm_import_dmn_model     = mhResult("dm_import_dmn_model",     ADDRESS);
    public static final MethodHandle MH$dm_export_dmn_model     = mhResult("dm_export_dmn_model",     ADDRESS);
    // Group H
    public static final MethodHandle MH$dm_import_openapi_spec          = mhResult("dm_import_openapi_spec",          ADDRESS);
    public static final MethodHandle MH$dm_export_openapi_spec          = mhResult("dm_export_openapi_spec",          ADDRESS);
    public static final MethodHandle MH$dm_convert_openapi_to_odcs      = mhResult("dm_convert_openapi_to_odcs",      ADDRESS);
    public static final MethodHandle MH$dm_analyze_openapi_conversion   = mhResult("dm_analyze_openapi_conversion",   ADDRESS);
    // Group I
    public static final MethodHandle MH$dm_migrate_dataflow_to_domain   = mhResult("dm_migrate_dataflow_to_domain",   ADDRESS);
    // Group J
    public static final MethodHandle MH$dm_parse_sketch_yaml            = mhResult("dm_parse_sketch_yaml",            ADDRESS);
    public static final MethodHandle MH$dm_parse_sketch_index_yaml      = mhResult("dm_parse_sketch_index_yaml",      ADDRESS);
    public static final MethodHandle MH$dm_export_sketch_to_yaml        = mhResult("dm_export_sketch_to_yaml",        ADDRESS);
    public static final MethodHandle MH$dm_export_sketch_index_to_yaml  = mhResult("dm_export_sketch_index_to_yaml",  ADDRESS);
    public static final MethodHandle MH$dm_create_sketch                = mhResult("dm_create_sketch",                ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_create_sketch_index          = mhResult("dm_create_sketch_index",          ADDRESS);
    public static final MethodHandle MH$dm_add_sketch_to_index          = mhResult("dm_add_sketch_to_index",          ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_search_sketches              = mhResult("dm_search_sketches",              ADDRESS, ADDRESS);
    // Group K
    public static final MethodHandle MH$dm_create_domain                = mhResult("dm_create_domain",                ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_add_system_to_domain         = mhResult("dm_add_system_to_domain",         ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_add_cads_node_to_domain      = mhResult("dm_add_cads_node_to_domain",      ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_add_odcs_node_to_domain      = mhResult("dm_add_odcs_node_to_domain",      ADDRESS, ADDRESS);
    // Group L
    public static final MethodHandle MH$dm_filter_nodes_by_owner                       = mhResult("dm_filter_nodes_by_owner",                       ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_filter_relationships_by_owner               = mhResult("dm_filter_relationships_by_owner",               ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_filter_nodes_by_infrastructure_type         = mhResult("dm_filter_nodes_by_infrastructure_type",         ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_filter_relationships_by_infrastructure_type = mhResult("dm_filter_relationships_by_infrastructure_type", ADDRESS, ADDRESS);
    public static final MethodHandle MH$dm_filter_by_tags                              = mhResult("dm_filter_by_tags",                              ADDRESS, ADDRESS);
    // Memory management
    public static final MethodHandle MH$dm_string_free = mhVoid("dm_string_free", ADDRESS);

    // ── Public invoker methods ───────────────────────────────────────────────
    // Each calls the MethodHandle or throws UnsupportedOperationException if library absent.

    public static void requireLibrary() {
        if (LIBRARY.isEmpty())
            throw new UnsupportedOperationException(
                "Native library not loaded. Set -D" + LIB_PATH_PROP + "=/path/to/libdata_modelling_ffi.so");
    }

    public static MemorySegment dm_parse_odcs_yaml(SegmentAllocator alloc, MemorySegment yaml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_parse_odcs_yaml.invoke(alloc, yaml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_odcs_yaml(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_odcs_yaml.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_convert_to_odcs(SegmentAllocator alloc, MemorySegment json, MemorySegment sourceFormat) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_convert_to_odcs.invoke(alloc, json, sourceFormat); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_sql(SegmentAllocator alloc, MemorySegment sql, MemorySegment dialect) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_sql.invoke(alloc, sql, dialect); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_sql(SegmentAllocator alloc, MemorySegment json, MemorySegment dialect) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_sql.invoke(alloc, json, dialect); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_avro(SegmentAllocator alloc, MemorySegment schema) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_avro.invoke(alloc, schema); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_json_schema(SegmentAllocator alloc, MemorySegment schema) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_json_schema.invoke(alloc, schema); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_protobuf(SegmentAllocator alloc, MemorySegment schema) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_protobuf.invoke(alloc, schema); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_cads(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_cads.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_from_odps(SegmentAllocator alloc, MemorySegment yaml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_from_odps.invoke(alloc, yaml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_avro(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_avro.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_json_schema(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_json_schema.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_protobuf(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_protobuf.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_cads(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_cads.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_to_odps(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_to_odps.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_validate_odps(SegmentAllocator alloc, MemorySegment yaml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_validate_odps.invoke(alloc, yaml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_bpmn_model(SegmentAllocator alloc, MemorySegment xml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_bpmn_model.invoke(alloc, xml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_bpmn_model(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_bpmn_model.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_dmn_model(SegmentAllocator alloc, MemorySegment xml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_dmn_model.invoke(alloc, xml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_dmn_model(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_dmn_model.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_import_openapi_spec(SegmentAllocator alloc, MemorySegment yamlOrJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_import_openapi_spec.invoke(alloc, yamlOrJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_openapi_spec(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_openapi_spec.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_convert_openapi_to_odcs(SegmentAllocator alloc, MemorySegment yamlOrJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_convert_openapi_to_odcs.invoke(alloc, yamlOrJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_analyze_openapi_conversion(SegmentAllocator alloc, MemorySegment yamlOrJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_analyze_openapi_conversion.invoke(alloc, yamlOrJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_migrate_dataflow_to_domain(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_migrate_dataflow_to_domain.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_parse_sketch_yaml(SegmentAllocator alloc, MemorySegment yaml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_parse_sketch_yaml.invoke(alloc, yaml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_parse_sketch_index_yaml(SegmentAllocator alloc, MemorySegment yaml) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_parse_sketch_index_yaml.invoke(alloc, yaml); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_sketch_to_yaml(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_sketch_to_yaml.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_export_sketch_index_to_yaml(SegmentAllocator alloc, MemorySegment json) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_export_sketch_index_to_yaml.invoke(alloc, json); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_create_sketch(SegmentAllocator alloc, MemorySegment name, MemorySegment sketchType, MemorySegment description) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_create_sketch.invoke(alloc, name, sketchType, description); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_create_sketch_index(SegmentAllocator alloc, MemorySegment name) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_create_sketch_index.invoke(alloc, name); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_add_sketch_to_index(SegmentAllocator alloc, MemorySegment indexJson, MemorySegment sketchJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_add_sketch_to_index.invoke(alloc, indexJson, sketchJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_search_sketches(SegmentAllocator alloc, MemorySegment indexJson, MemorySegment query) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_search_sketches.invoke(alloc, indexJson, query); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_create_domain(SegmentAllocator alloc, MemorySegment name, MemorySegment description) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_create_domain.invoke(alloc, name, description); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_add_system_to_domain(SegmentAllocator alloc, MemorySegment domainJson, MemorySegment systemJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_add_system_to_domain.invoke(alloc, domainJson, systemJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_add_cads_node_to_domain(SegmentAllocator alloc, MemorySegment domainJson, MemorySegment nodeJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_add_cads_node_to_domain.invoke(alloc, domainJson, nodeJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_add_odcs_node_to_domain(SegmentAllocator alloc, MemorySegment domainJson, MemorySegment nodeJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_add_odcs_node_to_domain.invoke(alloc, domainJson, nodeJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_filter_nodes_by_owner(SegmentAllocator alloc, MemorySegment json, MemorySegment owner) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_filter_nodes_by_owner.invoke(alloc, json, owner); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_filter_relationships_by_owner(SegmentAllocator alloc, MemorySegment json, MemorySegment owner) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_filter_relationships_by_owner.invoke(alloc, json, owner); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_filter_nodes_by_infrastructure_type(SegmentAllocator alloc, MemorySegment json, MemorySegment infraType) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_filter_nodes_by_infrastructure_type.invoke(alloc, json, infraType); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_filter_relationships_by_infrastructure_type(SegmentAllocator alloc, MemorySegment json, MemorySegment infraType) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_filter_relationships_by_infrastructure_type.invoke(alloc, json, infraType); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static MemorySegment dm_filter_by_tags(SegmentAllocator alloc, MemorySegment json, MemorySegment tagsJson) {
        requireLibrary();
        try { return (MemorySegment) MH$dm_filter_by_tags.invoke(alloc, json, tagsJson); }
        catch (Throwable t) { throw new AssertionError("native call failed", t); }
    }

    public static void dm_string_free(MemorySegment ptr) {
        if (MH$dm_string_free == null) return; // library absent — nothing to free
        try { MH$dm_string_free.invoke(ptr); }
        catch (Throwable t) { throw new AssertionError("dm_string_free failed", t); }
    }

    private data_modelling_ffi_h() {}
}
