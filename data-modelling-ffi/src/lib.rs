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
use uuid::Uuid;

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
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::ODCSImporter::new();
    match importer.import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => match serde_json::to_string(&result) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_odcs_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let exports = data_modelling_sdk::ODCSExporter::export_model(&model, None, "odcs");
    match serde_json::to_string(&exports) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_convert_to_odcs(json: *const c_char, source_format: *const c_char) -> DmResult {
    let input = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let fmt = cstr_to_str_opt(source_format);
    match data_modelling_sdk::convert_to_odcs(input, fmt) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

// ── Group B: SQL (2) ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_from_sql(sql: *const c_char, dialect: *const c_char) -> DmResult {
    let sql_str = match cstr_to_str(sql, "sql") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let dialect_str = match cstr_to_str(dialect, "dialect") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let importer = data_modelling_sdk::SQLImporter::new(dialect_str);
    match importer.parse(sql_str) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => match serde_json::to_string(&result) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_sql(json: *const c_char, dialect: *const c_char) -> DmResult {
    let json_str = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let dialect_str = cstr_to_str_opt(dialect);
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(json_str) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let sql = data_modelling_sdk::SQLExporter::export_model(&model, None, dialect_str);
    DmResult::ok(sql)
}

// ── Group C: Schema Format Import (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_from_avro(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::AvroImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => match serde_json::to_string(&result) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_json_schema(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::JSONSchemaImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => match serde_json::to_string(&result) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_protobuf(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::ProtobufImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => match serde_json::to_string(&result) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_cads(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::import::CADSImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(asset) => match serde_json::to_string(&asset) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_odps(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::import::ODPSImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(product) => match serde_json::to_string(&product) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

// ── Group D: Schema Format Export (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_export_to_avro(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let avro_value = data_modelling_sdk::AvroExporter::export_model(&model, None);
    match serde_json::to_string(&avro_value) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_json_schema(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let schema_value = data_modelling_sdk::JSONSchemaExporter::export_model(&model, None);
    match serde_json::to_string(&schema_value) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_protobuf(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let proto = data_modelling_sdk::ProtobufExporter::export_model(&model, None);
    DmResult::ok(proto)
}

#[no_mangle]
pub extern "C" fn dm_export_to_cads(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let asset: data_modelling_sdk::models::CADSAsset = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    DmResult::ok(data_modelling_sdk::export::CADSExporter::export_asset(&asset))
}

#[no_mangle]
pub extern "C" fn dm_export_to_odps(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let product: data_modelling_sdk::models::ODPSDataProduct = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    DmResult::ok(data_modelling_sdk::export::ODPSExporter::export_product(&product))
}

// ── Group E: ODPS Validation (1) ────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_validate_odps(yaml: *const c_char) -> DmVoidResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmVoidResult::err(e), Ok(v) => v };
    // ODPSImporter validates on import when odps-validation feature is enabled
    match data_modelling_sdk::import::ODPSImporter::new().import(s) {
        Err(e) => DmVoidResult::err(format!("{}", e)),
        Ok(_) => DmVoidResult::ok(),
    }
}

// ── Group F: BPMN (2) ───────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_bpmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::import::bpmn::BPMNImporter::new();
    match importer.import(s, Uuid::nil(), None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(model) => match serde_json::to_string(&model) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_bpmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::export::bpmn::BPMNExporter::new().export(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

// ── Group G: DMN (2) ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_dmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::import::dmn::DMNImporter::new();
    match importer.import(s, Uuid::nil(), None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(model) => match serde_json::to_string(&model) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_dmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::export::dmn::DMNExporter::new().export(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

// ── Group H: OpenAPI (4) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_openapi_spec(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::import::openapi::OpenAPIImporter::new();
    match importer.import(s, Uuid::nil(), None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(model) => match serde_json::to_string(&model) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_openapi_spec(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let importer = data_modelling_sdk::import::openapi::OpenAPIImporter::new();
    let source_fmt = importer.detect_format(s);
    match data_modelling_sdk::export::openapi::OpenAPIExporter::new().export(s, source_fmt, None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

#[no_mangle]
pub extern "C" fn dm_convert_openapi_to_odcs(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::convert_to_odcs(s, Some("openapi")) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

#[no_mangle]
pub extern "C" fn dm_analyze_openapi_conversion(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let converter = data_modelling_sdk::convert::OpenAPIToODCSConverter::new();
    match converter.analyze_conversion(s, "") {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(report) => match serde_json::to_string(&report) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

// ── Group I: DataFlow (1) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_migrate_dataflow_to_domain(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::convert::migrate_dataflow_to_domain(s, None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(domain) => match serde_json::to_string(&domain) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

// ── Group J: Sketch (8) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_parse_sketch_yaml(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::SketchImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(sketch) => match serde_json::to_string(&sketch) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_parse_sketch_index_yaml(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::SketchImporter::new().import_index(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(index) => match serde_json::to_string(&index) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_to_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let sketch: data_modelling_sdk::Sketch = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    match data_modelling_sdk::SketchExporter::new().export(&sketch) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(yaml) => DmResult::ok(yaml),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_index_to_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let index: data_modelling_sdk::SketchIndex = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    match data_modelling_sdk::SketchExporter::new().export_index(&index) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(yaml) => DmResult::ok(yaml),
    }
}

#[no_mangle]
pub extern "C" fn dm_create_sketch(name: *const c_char, sketch_type: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let type_str = match cstr_to_str(sketch_type, "sketch_type") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let desc_str = cstr_to_str_opt(description).unwrap_or("");
    let stype: data_modelling_sdk::SketchType = match serde_json::from_str(&format!("\"{}\"", type_str.to_lowercase())) {
        Err(e) => return DmResult::err(format!("invalid sketch_type '{}': {}", type_str, e)),
        Ok(v) => v,
    };
    let mut sketch = data_modelling_sdk::Sketch::new(0, name_str, "");
    sketch.sketch_type = stype;
    if !desc_str.is_empty() {
        sketch.description = Some(desc_str.to_string());
    }
    match serde_json::to_string(&sketch) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_create_sketch_index(_name: *const c_char) -> DmResult {
    let index = data_modelling_sdk::SketchIndex::new();
    match serde_json::to_string(&index) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_add_sketch_to_index(index_json: *const c_char, sketch_json: *const c_char) -> DmResult {
    let idx_s = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let sk_s = match cstr_to_str(sketch_json, "sketch_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut index: data_modelling_sdk::SketchIndex = match serde_json::from_str(idx_s) {
        Err(e) => return DmResult::err(format!("deserialize index error: {}", e)),
        Ok(v) => v,
    };
    let sketch: data_modelling_sdk::Sketch = match serde_json::from_str(sk_s) {
        Err(e) => return DmResult::err(format!("deserialize sketch error: {}", e)),
        Ok(v) => v,
    };
    index.add_sketch(&sketch, format!("{}.sketch.yaml", sketch.title));
    match serde_json::to_string(&index) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_search_sketches(index_json: *const c_char, query: *const c_char) -> DmResult {
    let idx_s = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let q = match cstr_to_str(query, "query") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let index: data_modelling_sdk::SketchIndex = match serde_json::from_str(idx_s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let q_lower = q.to_lowercase();
    let matches: Vec<&data_modelling_sdk::SketchIndexEntry> = index.sketches.iter()
        .filter(|e| e.title.to_lowercase().contains(&q_lower) || e.file.to_lowercase().contains(&q_lower))
        .collect();
    match serde_json::to_string(&matches) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

// ── Group K: Domain (4) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_create_domain(name: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let desc_str = cstr_to_str_opt(description);
    let mut domain = data_modelling_sdk::models::domain::Domain::new(name_str.to_string());
    if let Some(desc) = desc_str {
        if !desc.is_empty() {
            domain.description = Some(desc.to_string());
        }
    }
    match serde_json::to_string(&domain) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_add_system_to_domain(domain_json: *const c_char, system_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let sys_s = match cstr_to_str(system_json, "system_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut domain: data_modelling_sdk::models::domain::Domain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    let system: data_modelling_sdk::models::domain::System = match serde_json::from_str(sys_s) {
        Err(e) => return DmResult::err(format!("deserialize system error: {}", e)),
        Ok(v) => v,
    };
    domain.add_system(system);
    match serde_json::to_string(&domain) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_add_cads_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let node_s = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut domain: data_modelling_sdk::models::domain::Domain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    let node: data_modelling_sdk::models::domain::CADSNode = match serde_json::from_str(node_s) {
        Err(e) => return DmResult::err(format!("deserialize node error: {}", e)),
        Ok(v) => v,
    };
    domain.add_cads_node(node);
    match serde_json::to_string(&domain) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_add_odcs_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let node_s = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut domain: data_modelling_sdk::models::domain::Domain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    let node: data_modelling_sdk::models::domain::ODCSNode = match serde_json::from_str(node_s) {
        Err(e) => return DmResult::err(format!("deserialize node error: {}", e)),
        Ok(v) => v,
    };
    domain.add_odcs_node(node);
    match serde_json::to_string(&domain) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

// ── Group L: Filter (5) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let o = match cstr_to_str(owner, "owner") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let nodes = model.filter_nodes_by_owner(o);
    match serde_json::to_string(&nodes) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let o = match cstr_to_str(owner, "owner") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let rels = model.filter_relationships_by_owner(o);
    match serde_json::to_string(&rels) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let it = match cstr_to_str(infra_type, "infra_type") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let itype: data_modelling_sdk::models::enums::InfrastructureType =
        match serde_json::from_str(&format!("\"{}\"", it)) {
            Err(e) => return DmResult::err(format!("invalid infra_type '{}': {}", it, e)),
            Ok(v) => v,
        };
    let nodes = model.filter_nodes_by_infrastructure_type(itype);
    match serde_json::to_string(&nodes) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let it = match cstr_to_str(infra_type, "infra_type") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let itype: data_modelling_sdk::models::enums::InfrastructureType =
        match serde_json::from_str(&format!("\"{}\"", it)) {
            Err(e) => return DmResult::err(format!("invalid infra_type '{}': {}", it, e)),
            Ok(v) => v,
        };
    let rels = model.filter_relationships_by_infrastructure_type(itype);
    match serde_json::to_string(&rels) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}

#[no_mangle]
pub extern "C" fn dm_filter_by_tags(json: *const c_char, tags_json: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let t = match cstr_to_str(tags_json, "tags_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let model: data_modelling_sdk::DataModel = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let tags: Vec<String> = match serde_json::from_str(t) {
        Err(e) => return DmResult::err(format!("deserialize tags error: {}", e)),
        Ok(v) => v,
    };
    let mut all_tables: Vec<&data_modelling_sdk::Table> = Vec::new();
    let mut all_rels: Vec<&data_modelling_sdk::Relationship> = Vec::new();
    for tag in &tags {
        let (tables, rels) = model.filter_by_tags(tag);
        for t in tables { if !all_tables.contains(&t) { all_tables.push(t); } }
        for r in rels { if !all_rels.contains(&r) { all_rels.push(r); } }
    }
    let result = serde_json::json!({ "tables": all_tables, "relationships": all_rels });
    match serde_json::to_string(&result) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(json) => DmResult::ok(json),
    }
}
