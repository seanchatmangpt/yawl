//! data-modelling-ffi: C FFI bridge for data-modelling-sdk v2.3.0.
//!
//! # ABI Contract
//! All functions accept UTF-8 C strings (null-terminated) and return either:
//! - `DmResult { data: *mut c_char, error: *mut c_char }` — exactly one is non-null
//! - `DmVoidResult { error: *mut c_char }` — non-null means error
//!
//! Callers MUST call `dm_string_free` on every non-null pointer returned.
//! Null pointers passed for optional args are accepted; required args return error.

use std::ffi::{CStr, CString, c_char};

/// Result type for operations returning data.
/// Exactly one of `data` or `error` is non-null.
#[repr(C)]
pub struct DmResult {
    pub data: *mut c_char,
    pub error: *mut c_char,
}

/// Result type for void operations (validation etc).
/// `error` is null on success, non-null on failure.
#[repr(C)]
pub struct DmVoidResult {
    pub error: *mut c_char,
}

impl DmResult {
    fn ok(s: String) -> Self {
        DmResult {
            data: CString::new(s).unwrap_or_else(|_| CString::new("encoding error").unwrap()).into_raw(),
            error: std::ptr::null_mut(),
        }
    }
    fn err(msg: String) -> Self {
        DmResult {
            data: std::ptr::null_mut(),
            error: CString::new(msg).unwrap_or_else(|_| CString::new("error encoding error").unwrap()).into_raw(),
        }
    }
}

impl DmVoidResult {
    fn ok() -> Self { DmVoidResult { error: std::ptr::null_mut() } }
    fn err(msg: String) -> Self {
        DmVoidResult {
            error: CString::new(msg).unwrap_or_else(|_| CString::new("error encoding error").unwrap()).into_raw(),
        }
    }
}

/// Free a string returned by any dm_* function.
/// MUST be called on every non-null pointer returned from any dm_* function.
#[no_mangle]
pub extern "C" fn dm_string_free(ptr: *mut c_char) {
    if !ptr.is_null() {
        unsafe { drop(CString::from_raw(ptr)); }
    }
}

// ── CbC size/offset probes ──────────────────────────────────────────────────
// Java Layer 1 calls these at JVM startup to verify its StructLayout matches.

#[no_mangle]
pub extern "C" fn dm_sizeof_dm_result() -> usize { std::mem::size_of::<DmResult>() }

#[no_mangle]
pub extern "C" fn dm_sizeof_dm_void_result() -> usize { std::mem::size_of::<DmVoidResult>() }

#[no_mangle]
pub extern "C" fn dm_offsetof_dm_result_data() -> usize {
    unsafe {
        let base = std::ptr::null::<DmResult>();
        std::ptr::addr_of!((*base).data) as usize - (base as usize)
    }
}

#[no_mangle]
pub extern "C" fn dm_offsetof_dm_result_error() -> usize {
    unsafe {
        let base = std::ptr::null::<DmResult>();
        std::ptr::addr_of!((*base).error) as usize - (base as usize)
    }
}

#[no_mangle]
pub extern "C" fn dm_offsetof_dm_void_result_error() -> usize { 0usize }

// ── Helper ──────────────────────────────────────────────────────────────────

fn cstr_to_str<'a>(ptr: *const c_char, field: &'static str) -> Result<&'a str, String> {
    if ptr.is_null() {
        return Err(format!("{} must not be null", field));
    }
    unsafe { CStr::from_ptr(ptr) }.to_str().map_err(|e| format!("{} is not valid UTF-8: {}", field, e))
}

fn cstr_to_str_opt<'a>(ptr: *const c_char) -> Option<&'a str> {
    if ptr.is_null() { return None; }
    unsafe { CStr::from_ptr(ptr) }.to_str().ok()
}

// ── Group A: ODCS Core (3) ──────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_parse_odcs_yaml(yaml: *const c_char) -> DmResult {
    match cstr_to_str(yaml, "yaml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::odcs::parse_yaml(s) {
            Ok(json) => DmResult::ok(json),
            Err(e) => DmResult::err(e),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_odcs_yaml(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::odcs::export_to_yaml(s) {
            Ok(yaml) => DmResult::ok(yaml),
            Err(e) => DmResult::err(e),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_convert_to_odcs(json: *const c_char, source_format: *const c_char) -> DmResult {
    let json_str = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let fmt_str = match cstr_to_str(source_format, "source_format") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::odcs::convert_from(json_str, fmt_str) {
        Ok(result) => DmResult::ok(result),
        Err(e) => DmResult::err(e),
    }
}

// ── Group B: SQL (2) ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_from_sql(sql: *const c_char, dialect: *const c_char) -> DmResult {
    let sql_str = match cstr_to_str(sql, "sql") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let dialect_str = match cstr_to_str(dialect, "dialect") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::sql::import_from_sql(sql_str, dialect_str) {
        Ok(json) => DmResult::ok(json),
        Err(e) => DmResult::err(e),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_sql(json: *const c_char, dialect: *const c_char) -> DmResult {
    let json_str = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let dialect_str = match cstr_to_str(dialect, "dialect") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::sql::export_to_sql(json_str, dialect_str) {
        Ok(sql) => DmResult::ok(sql),
        Err(e) => DmResult::err(e),
    }
}

// ── Group C: Schema Format Import (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_from_avro(schema: *const c_char) -> DmResult {
    match cstr_to_str(schema, "schema") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::import_from_avro(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_json_schema(schema: *const c_char) -> DmResult {
    match cstr_to_str(schema, "schema") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::import_from_json_schema(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_protobuf(schema: *const c_char) -> DmResult {
    match cstr_to_str(schema, "schema") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::import_from_protobuf(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_cads(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::import_from_cads(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_odps(yaml: *const c_char) -> DmResult {
    match cstr_to_str(yaml, "yaml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::import_from_odps(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

// ── Group D: Schema Format Export (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_export_to_avro(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::export_to_avro(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_json_schema(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::export_to_json_schema(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_protobuf(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::export_to_protobuf(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_cads(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::export_to_cads(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_odps(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::schema::export_to_odps(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

// ── Group E: ODPS Validation (1) ────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_validate_odps(yaml: *const c_char) -> DmVoidResult {
    match cstr_to_str(yaml, "yaml") {
        Err(e) => DmVoidResult::err(e),
        Ok(s) => match data_modelling_sdk::validation::validate_odps(s) {
            Ok(()) => DmVoidResult::ok(),
            Err(e) => DmVoidResult::err(e),
        }
    }
}

// ── Group F: BPMN (2) ───────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_bpmn_model(xml: *const c_char) -> DmResult {
    match cstr_to_str(xml, "xml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::bpmn::import_model(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_bpmn_model(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::bpmn::export_model(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

// ── Group G: DMN (2) ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_dmn_model(xml: *const c_char) -> DmResult {
    match cstr_to_str(xml, "xml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::dmn::import_model(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_dmn_model(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::dmn::export_model(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

// ── Group H: OpenAPI (4) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_openapi_spec(yaml_or_json: *const c_char) -> DmResult {
    match cstr_to_str(yaml_or_json, "yaml_or_json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::openapi::import_spec(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_openapi_spec(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::openapi::export_spec(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_convert_openapi_to_odcs(yaml_or_json: *const c_char) -> DmResult {
    match cstr_to_str(yaml_or_json, "yaml_or_json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::openapi::convert_to_odcs(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_analyze_openapi_conversion(yaml_or_json: *const c_char) -> DmResult {
    match cstr_to_str(yaml_or_json, "yaml_or_json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::openapi::analyze_conversion(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

// ── Group I: DataFlow (1) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_migrate_dataflow_to_domain(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::dataflow::migrate_to_domain(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

// ── Group J: Sketch (8) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_parse_sketch_yaml(yaml: *const c_char) -> DmResult {
    match cstr_to_str(yaml, "yaml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::sketch::parse_yaml(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_parse_sketch_index_yaml(yaml: *const c_char) -> DmResult {
    match cstr_to_str(yaml, "yaml") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::sketch::parse_index_yaml(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_to_yaml(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::sketch::export_to_yaml(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_index_to_yaml(json: *const c_char) -> DmResult {
    match cstr_to_str(json, "json") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::sketch::export_index_to_yaml(s) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_create_sketch(name: *const c_char, sketch_type: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let type_str = match cstr_to_str(sketch_type, "sketch_type") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let desc_str = cstr_to_str_opt(description).unwrap_or("");
    match data_modelling_sdk::sketch::create(name_str, type_str, desc_str) {
        Ok(j) => DmResult::ok(j),
        Err(e) => DmResult::err(e),
    }
}

#[no_mangle]
pub extern "C" fn dm_create_sketch_index(name: *const c_char) -> DmResult {
    match cstr_to_str(name, "name") {
        Err(e) => DmResult::err(e),
        Ok(s) => match data_modelling_sdk::sketch::create_index(s) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
    }
}

#[no_mangle]
pub extern "C" fn dm_add_sketch_to_index(index_json: *const c_char, sketch_json: *const c_char) -> DmResult {
    let idx = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let sk = match cstr_to_str(sketch_json, "sketch_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::sketch::add_to_index(idx, sk) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_search_sketches(index_json: *const c_char, query: *const c_char) -> DmResult {
    let idx = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let q = match cstr_to_str(query, "query") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::sketch::search(idx, q) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

// ── Group K: Domain (4) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_create_domain(name: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let desc_str = cstr_to_str_opt(description).unwrap_or("");
    match data_modelling_sdk::domain::create(name_str, desc_str) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_add_system_to_domain(domain_json: *const c_char, system_json: *const c_char) -> DmResult {
    let dom = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let sys = match cstr_to_str(system_json, "system_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::domain::add_system(dom, sys) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_add_cads_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let node = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::domain::add_cads_node(dom, node) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_add_odcs_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let node = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::domain::add_odcs_node(dom, node) { Ok(j) => DmResult::ok(j), Err(e) => DmResult::err(e) }
}

// ── Group L: Filter (5) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let o = match cstr_to_str(owner, "owner") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::filter::nodes_by_owner(j, o) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let o = match cstr_to_str(owner, "owner") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::filter::relationships_by_owner(j, o) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let it = match cstr_to_str(infra_type, "infra_type") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::filter::nodes_by_infrastructure_type(j, it) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let it = match cstr_to_str(infra_type, "infra_type") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::filter::relationships_by_infrastructure_type(j, it) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
}

#[no_mangle]
pub extern "C" fn dm_filter_by_tags(json: *const c_char, tags_json: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    let t = match cstr_to_str(tags_json, "tags_json") { Err(e) => return DmResult::err(e), Ok(s) => s };
    match data_modelling_sdk::filter::by_tags(j, t) { Ok(r) => DmResult::ok(r), Err(e) => DmResult::err(e) }
}
