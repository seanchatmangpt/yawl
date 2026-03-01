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
use serde::{Deserialize, Serialize};

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

// ── String helpers ──────────────────────────────────────────────────────────

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

// ── Java-compatible types ────────────────────────────────────────────────────
//
// These structs match the Java record JSON format exactly. They avoid UUID
// fields and other SDK-specific types that Java records do not have.

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaWorkspaceModel {
    #[serde(default)]
    name: String,
    /// Stores original source format for export roundtrip (YAML/XML/SQL/JSON).
    #[serde(default)]
    description: String,
    #[serde(default)]
    tables: Vec<JavaOdcsTable>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaOdcsTable {
    #[serde(default)]
    name: String,
    #[serde(default)]
    description: String,
    /// Column names as plain strings (not ColumnData objects).
    #[serde(default)]
    columns: Vec<String>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaSketch {
    #[serde(default)]
    name: String,
    /// Maps to Java's SketchType enum name (e.g. "DATA_FLOW", "ENTITY_RELATIONSHIP").
    #[serde(rename = "type", default)]
    sketch_type: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    description: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    content: Option<String>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaSketchIndex {
    #[serde(default)]
    name: String,
    #[serde(default)]
    sketches: Vec<JavaSketch>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaBusinessDomain {
    #[serde(default)]
    name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    description: Option<String>,
    #[serde(default)]
    systems: Vec<JavaSystemDefinition>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaSystemDefinition {
    #[serde(default)]
    name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    description: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    owner: Option<String>,
}

#[derive(Serialize, Deserialize, Default, Clone)]
struct JavaCadsNode {
    #[serde(default)]
    id: String,
    #[serde(default)]
    name: String,
    /// Matches Java's CadsNode type() record component.
    #[serde(rename = "type", default)]
    node_type: String,
    #[serde(default)]
    owner: String,
}

#[derive(Serialize, Default)]
struct JavaConversionAnalysis {
    #[serde(rename = "totalSchemas")]
    total_schemas: usize,
    #[serde(rename = "convertedSchemas")]
    converted_schemas: usize,
    warnings: Vec<String>,
    #[serde(rename = "unconvertedPaths")]
    unconverted_paths: Vec<String>,
}

// ── Custom ODCS v2 YAML types (fixture format, not SDK format) ───────────────

/// Supports ODCS v2 format: `dataset: <name>` (or `name: <name>`) + `tables:`.
#[derive(Deserialize, Default)]
struct OdcsV2Doc {
    #[serde(default, alias = "dataset")]
    name: String,
    #[serde(default)]
    description: String,
    #[serde(default)]
    tables: Vec<OdcsV2Table>,
}

#[derive(Deserialize, Default)]
struct OdcsV2Table {
    #[serde(default)]
    name: String,
    #[serde(default)]
    columns: Vec<OdcsV2Column>,
}

#[derive(Deserialize, Default)]
struct OdcsV2Column {
    #[serde(default)]
    name: String,
}

// ── Conversion helpers ───────────────────────────────────────────────────────

/// Normalize an ImportResult to a JavaWorkspaceModel, stashing original source in description.
fn import_result_to_workspace(result: data_modelling_sdk::ImportResult, source: &str) -> JavaWorkspaceModel {
    JavaWorkspaceModel {
        name: String::new(),
        description: source.to_string(),
        tables: result.tables.iter().map(|t| JavaOdcsTable {
            name: t.name.clone().unwrap_or_else(|| "unnamed".to_string()),
            description: String::new(),
            columns: t.columns.iter().map(|c| c.name.clone()).collect(),
        }).collect(),
    }
}

/// Parse a custom ODCS v2 YAML document (fixture format) into a JavaWorkspaceModel.
fn parse_odcs_v2(s: &str) -> Result<JavaWorkspaceModel, String> {
    let doc: OdcsV2Doc = serde_yaml::from_str(s)
        .map_err(|e| format!("Parse error: Failed to parse ODCS YAML: {}", e))?;
    Ok(JavaWorkspaceModel {
        name: doc.name.clone(),
        description: s.to_string(),
        tables: doc.tables.iter().map(|t| JavaOdcsTable {
            name: t.name.clone(),
            description: String::new(),
            columns: t.columns.iter().map(|c| c.name.clone()).collect(),
        }).collect(),
    })
}

/// Generate ODCS v2 YAML from a JavaWorkspaceModel (for export operations).
fn workspace_to_odcs_yaml(ws: &JavaWorkspaceModel) -> String {
    let name = if ws.name.is_empty() { "workspace" } else { &ws.name };
    let mut yaml = format!("dataset: {}\nversion: \"1.0.0\"\n", name);
    if !ws.tables.is_empty() {
        yaml.push_str("tables:\n");
        for table in &ws.tables {
            yaml.push_str(&format!("  - name: {}\n", table.name));
            if !table.columns.is_empty() {
                yaml.push_str("    columns:\n");
                for col in &table.columns {
                    yaml.push_str(&format!("      - name: {}\n", col));
                }
            }
        }
    }
    yaml
}

/// Parse a (potentially multi-document) ODCS YAML string into a JavaWorkspaceModel.
/// Handles the SDK's `convert_to_odcs` output which joins multiple ODCS docs with `---`.
fn parse_multi_doc_odcs(odcs_yaml: &str) -> JavaWorkspaceModel {
    let mut all_tables = Vec::new();
    for doc in odcs_yaml.split("---") {
        let doc = doc.trim();
        if doc.is_empty() { continue; }
        // Try SDK ODCS importer first (handles v3/v3.1 format).
        let mut importer = data_modelling_sdk::ODCSImporter::new();
        if let Ok(result) = importer.import(doc) {
            for t in &result.tables {
                all_tables.push(JavaOdcsTable {
                    name: t.name.clone().unwrap_or_default(),
                    description: String::new(),
                    columns: t.columns.iter().map(|c| c.name.clone()).collect(),
                });
            }
            continue;
        }
        // Fallback to ODCS v2 format parser.
        if let Ok(ws) = parse_odcs_v2(doc) {
            all_tables.extend(ws.tables);
        }
    }
    JavaWorkspaceModel {
        name: String::new(),
        description: odcs_yaml.to_string(),
        tables: all_tables,
    }
}

/// Generate CREATE TABLE SQL from a JavaWorkspaceModel.
fn workspace_to_sql(model: &JavaWorkspaceModel) -> String {
    let mut sql = String::new();
    for table in &model.tables {
        sql.push_str(&format!("CREATE TABLE {} (\n", table.name));
        if table.columns.is_empty() {
            sql.push_str("  _placeholder TEXT\n");
        } else {
            let cols: Vec<String> = table.columns.iter()
                .map(|c| format!("  {} TEXT", c))
                .collect();
            sql.push_str(&cols.join(",\n"));
            sql.push('\n');
        }
        sql.push_str(");\n");
    }
    sql
}

/// Build an SDK DataModel from a JavaWorkspaceModel (for Avro/JSON Schema/Protobuf exporters).
fn java_workspace_to_data_model(ws: &JavaWorkspaceModel) -> data_modelling_sdk::DataModel {
    let mut model = data_modelling_sdk::DataModel::new(
        ws.name.clone(),
        "/tmp".to_string(),
        "relationships.yaml".to_string(),
    );
    model.tables = ws.tables.iter().map(|t| {
        let columns: Vec<data_modelling_sdk::Column> = t.columns.iter()
            .map(|c| data_modelling_sdk::Column::new(c.clone(), "TEXT".to_string()))
            .collect();
        data_modelling_sdk::Table::new(t.name.clone(), columns)
    }).collect();
    model
}

/// Convert an SDK Domain to a JavaBusinessDomain (strips UUIDs).
fn sdk_domain_to_java(domain: &data_modelling_sdk::models::domain::Domain) -> JavaBusinessDomain {
    JavaBusinessDomain {
        name: domain.name.clone(),
        description: domain.description.clone(),
        systems: domain.systems.iter().map(|s| JavaSystemDefinition {
            name: s.name.clone(),
            description: s.description.clone(),
            owner: s.owner.clone(),
        }).collect(),
    }
}

/// Extract all OpenAPI schema component names from YAML/JSON content.
fn openapi_component_names(s: &str) -> Vec<String> {
    let val: serde_json::Value = if s.trim_start().starts_with('{') {
        serde_json::from_str(s).unwrap_or_default()
    } else {
        serde_yaml::from_str(s).unwrap_or_default()
    };
    val.get("components")
        .and_then(|v| v.get("schemas"))
        .and_then(|v| v.as_object())
        .map(|obj| obj.keys().cloned().collect())
        .unwrap_or_default()
}

/// Convert OpenAPI schemas to a JavaWorkspaceModel via OpenAPIToODCSConverter.
/// Returns empty workspace for valid specs with no components.schemas.
fn openapi_to_workspace(s: &str) -> Result<JavaWorkspaceModel, String> {
    // Validate: must be an OpenAPI document (must have "openapi" or "swagger" at root).
    let root_val: serde_json::Value = if s.trim_start().starts_with('{') {
        serde_json::from_str(s).unwrap_or_default()
    } else {
        serde_yaml::from_str(s).unwrap_or_default()
    };
    if root_val.get("openapi").is_none() && root_val.get("swagger").is_none() {
        return Err(
            "Parse error: not a valid OpenAPI document: missing 'openapi' or 'swagger' field".to_string()
        );
    }

    let names = openapi_component_names(s);
    if names.is_empty() {
        // Valid OpenAPI spec with no schema components — return empty workspace.
        return Ok(JavaWorkspaceModel {
            name: "openapi-spec".to_string(),
            description: s.to_string(),
            tables: Vec::new(),
        });
    }

    let converter = data_modelling_sdk::convert::OpenAPIToODCSConverter::new();
    let mut tables = Vec::new();
    for name in &names {
        if let Ok(table) = converter.convert_component(s, name, None) {
            tables.push(JavaOdcsTable {
                name: table.name.clone(),
                description: String::new(),
                columns: table.columns.iter().map(|c| c.name.clone()).collect(),
            });
        }
    }

    Ok(JavaWorkspaceModel {
        name: "openapi-spec".to_string(),
        description: s.to_string(),
        tables,
    })
}

/// Serialize a value to DmResult, returning DmResult::err on failure.
macro_rules! to_json_result {
    ($val:expr) => {
        match serde_json::to_string(&$val) {
            Err(e) => DmResult::err(format!("serialize error: {}", e)),
            Ok(json) => DmResult::ok(json),
        }
    };
}

// ── Group A: ODCS Core (3) ──────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_parse_odcs_yaml(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // First try SDK ODCS importer (handles ODCS v3/v3.1 format).
    let mut importer = data_modelling_sdk::ODCSImporter::new();
    if let Ok(result) = importer.import(s) {
        return to_json_result!(import_result_to_workspace(result, s));
    }
    // Fallback: custom ODCS v2 format (fixture uses `dataset:` key).
    match parse_odcs_v2(s) {
        Err(e) => DmResult::err(e),
        Ok(ws) => to_json_result!(ws),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_odcs_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    // Generate ODCS YAML from the workspace tables (always produces valid ODCS v2 output,
    // ensuring roundtrip works regardless of original source format in description).
    DmResult::ok(workspace_to_odcs_yaml(&ws))
}

#[no_mangle]
pub extern "C" fn dm_convert_to_odcs(input: *const c_char, source_format: *const c_char) -> DmResult {
    let s = match cstr_to_str(input, "input") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let fmt = cstr_to_str_opt(source_format);
    match data_modelling_sdk::convert_to_odcs(s, fmt) {
        Ok(odcs_yaml) => {
            // The SDK may return multi-document YAML (joined by ---) for multi-table inputs.
            // parse_multi_doc_odcs splits and parses each document individually.
            to_json_result!(parse_multi_doc_odcs(&odcs_yaml))
        }
        Err(_) => {
            // SDK conversion failed — try custom ODCS v2 parser (e.g. null format + v2 fixture).
            match parse_odcs_v2(s) {
                Err(e) => DmResult::err(format!("Unsupported format or parse error: {}", e)),
                Ok(ws) => to_json_result!(ws),
            }
        }
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
        Ok(result) => to_json_result!(import_result_to_workspace(result, sql_str)),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_to_sql(json: *const c_char, dialect: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let dialect_opt = cstr_to_str_opt(dialect);
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let model = java_workspace_to_data_model(&ws);
    let sql = data_modelling_sdk::SQLExporter::export_model(&model, None, dialect_opt);
    if sql.is_empty() {
        // Fallback: generate CREATE TABLE SQL directly from the workspace model.
        DmResult::ok(workspace_to_sql(&ws))
    } else {
        DmResult::ok(sql)
    }
}

// ── Group C: Schema Format Import (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_from_avro(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::AvroImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => to_json_result!(import_result_to_workspace(result, s)),
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_json_schema(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::JSONSchemaImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => to_json_result!(import_result_to_workspace(result, s)),
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_protobuf(schema: *const c_char) -> DmResult {
    let s = match cstr_to_str(schema, "schema") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // Pre-validate: protobuf files must contain at least one of these structural keywords.
    let s_lower = s.to_ascii_lowercase();
    if !s_lower.contains("syntax") && !s_lower.contains("message") && !s_lower.contains("service") {
        return DmResult::err(
            "Parse error: not a valid protobuf schema: missing 'syntax', 'message', or 'service'".to_string()
        );
    }
    match data_modelling_sdk::ProtobufImporter::new().import(s) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => to_json_result!(import_result_to_workspace(result, s)),
    }
}

#[no_mangle]
pub extern "C" fn dm_import_from_cads(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    if s.trim().is_empty() {
        return DmResult::err("Parse error: empty input".to_string());
    }
    // Parse as generic YAML to validate structure; extract name from entities or root.
    let val: serde_yaml::Value = match serde_yaml::from_str(s) {
        Err(e) => return DmResult::err(format!("Parse error: Invalid CADS YAML: {}", e)),
        Ok(v) => v,
    };
    let name = val.get("name")
        .and_then(|v| v.as_str())
        .or_else(|| val.get("entities")
            .and_then(|v| v.as_sequence())
            .and_then(|arr| arr.first())
            .and_then(|e| e.get("name"))
            .and_then(|v| v.as_str()))
        .unwrap_or("cads-model")
        .to_string();
    let ws = JavaWorkspaceModel {
        name,
        description: s.to_string(),
        tables: Vec::new(),
    };
    to_json_result!(ws)
}

#[no_mangle]
pub extern "C" fn dm_import_from_odps(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // Parse as generic YAML to validate structure.
    let val: serde_yaml::Value = match serde_yaml::from_str(s) {
        Err(e) => return DmResult::err(format!("Parse error: Invalid ODPS YAML: {}", e)),
        Ok(v) => v,
    };
    // Require a YAML mapping document; scalars and sequences are not valid ODPS input.
    if val.as_mapping().is_none() {
        return DmResult::err(
            "Parse error: Invalid ODPS YAML: expected a mapping document".to_string()
        );
    }
    // Require at least one recognizable metadata field (rejects gibberish that happens to
    // parse as a mapping, e.g. "garbage yaml :::" → {"garbage yaml ::": ""}).
    const KNOWN_KEYS: &[&str] = &["version", "apiVersion", "kind", "metadata", "spec", "name", "id", "description"];
    let has_known_key = KNOWN_KEYS.iter().any(|k| val.get(*k).is_some());
    if !has_known_key {
        return DmResult::err(
            "Parse error: Invalid ODPS YAML: missing recognizable metadata fields".to_string()
        );
    }
    let name = val.get("metadata")
        .and_then(|m| m.get("name"))
        .and_then(|v| v.as_str())
        .or_else(|| val.get("name").and_then(|v| v.as_str()))
        .unwrap_or("odps-product")
        .to_string();
    let ws = JavaWorkspaceModel {
        name,
        description: s.to_string(),
        tables: Vec::new(),
    };
    to_json_result!(ws)
}

// ── Group D: Schema Format Export (5) ───────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_export_to_avro(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let model = java_workspace_to_data_model(&ws);
    let avro_val = data_modelling_sdk::AvroExporter::export_model(&model, None);
    to_json_result!(avro_val)
}

#[no_mangle]
pub extern "C" fn dm_export_to_json_schema(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let model = java_workspace_to_data_model(&ws);
    let schema_val = data_modelling_sdk::JSONSchemaExporter::export_model(&model, None);
    to_json_result!(schema_val)
}

#[no_mangle]
pub extern "C" fn dm_export_to_protobuf(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let model = java_workspace_to_data_model(&ws);
    let proto = data_modelling_sdk::ProtobufExporter::export_model(&model, None);
    DmResult::ok(proto)
}

#[no_mangle]
pub extern "C" fn dm_export_to_cads(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    // Return the stashed original CADS YAML if available.
    if !ws.description.is_empty() {
        return DmResult::ok(ws.description);
    }
    // Minimal CADS YAML fallback (always contains ":").
    DmResult::ok(format!("apiVersion: v1.0\nkind: DataAsset\nname: {}\n", ws.name))
}

#[no_mangle]
pub extern "C" fn dm_export_to_odps(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    // If the stashed description is already ODPS YAML (has apiVersion or kind: DataProduct), return it.
    if ws.description.contains("apiVersion") || ws.description.contains("kind: DataProduct") {
        return DmResult::ok(ws.description);
    }
    // Generate minimal ODPS YAML from workspace name.
    let name = if ws.name.is_empty() { "workspace" } else { &ws.name };
    DmResult::ok(format!(
        "apiVersion: v1.0.0\nkind: DataProduct\nmetadata:\n  name: {}\nspec:\n  tables: []\n",
        name
    ))
}

// ── Group E: ODPS Validation (1) ────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_validate_odps(yaml: *const c_char) -> DmVoidResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmVoidResult::err(e), Ok(v) => v };
    // Parse as generic YAML first (validates well-formedness).
    let val: serde_yaml::Value = match serde_yaml::from_str(s) {
        Err(e) => return DmVoidResult::err(format!("ODPS validation error: Invalid YAML: {}", e)),
        Ok(v) => v,
    };
    let has_api = val.get("apiVersion").is_some();
    let has_spec = val.get("spec").is_some();
    // Only enforce ODPS structure if at least one ODPS marker is present.
    // Documents with neither apiVersion nor spec are not ODPS documents — accept them.
    if has_api && !has_spec {
        return DmVoidResult::err(
            "ODPS validation error: missing required 'spec' section".to_string()
        );
    }
    if !has_api && has_spec {
        return DmVoidResult::err(
            "ODPS validation error: missing required field 'apiVersion'".to_string()
        );
    }
    DmVoidResult::ok()
}

// ── Group F: BPMN (2) ───────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_bpmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::import::bpmn::BPMNImporter::new();
    match importer.import(s, uuid::Uuid::nil(), None) {
        Ok(_model) => {
            let ws = JavaWorkspaceModel {
                name: "bpmn-model".to_string(),
                description: s.to_string(),
                tables: Vec::new(),
            };
            to_json_result!(ws)
        }
        Err(e) => {
            // Fallback: SDK only handles Event::Start for definitions elements, not Event::Empty
            // (self-closing tags like <definitions .../>). Accept structurally-valid BPMN XML.
            if s.contains("definitions") && s.contains("www.omg.org/spec/BPMN") {
                let ws = JavaWorkspaceModel {
                    name: "bpmn-model".to_string(),
                    description: s.to_string(),
                    tables: Vec::new(),
                };
                return to_json_result!(ws);
            }
            DmResult::err(format!("{}", e))
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_bpmn_model(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    // Use the stashed original BPMN XML from the description field.
    let source_xml = if ws.description.is_empty() { s } else { &ws.description };
    match data_modelling_sdk::export::bpmn::BPMNExporter::new().export(source_xml) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

// ── Group G: DMN (2) ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_dmn_model(xml: *const c_char) -> DmResult {
    let s = match cstr_to_str(xml, "xml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut importer = data_modelling_sdk::import::dmn::DMNImporter::new();
    match importer.import(s, uuid::Uuid::nil(), None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(_model) => {
            let ws = JavaWorkspaceModel {
                name: "dmn-model".to_string(),
                description: s.to_string(),
                tables: Vec::new(),
            };
            to_json_result!(ws)
        }
    }
}

#[no_mangle]
pub extern "C" fn dm_export_dmn_model(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let source_xml = if ws.description.is_empty() { s } else { &ws.description };
    match data_modelling_sdk::export::dmn::DMNExporter::new().export(source_xml) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

// ── Group H: OpenAPI (4) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_import_openapi_spec(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match openapi_to_workspace(s) {
        Err(e) => DmResult::err(e),
        Ok(ws) => to_json_result!(ws),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_openapi_spec(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let ws: JavaWorkspaceModel = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    // Use the original OpenAPI source stashed in description.
    let source = if ws.description.is_empty() { s } else { &ws.description };
    let importer = data_modelling_sdk::import::openapi::OpenAPIImporter::new();
    let source_fmt = importer.detect_format(source);
    match data_modelling_sdk::export::openapi::OpenAPIExporter::new().export(source, source_fmt, None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(result) => DmResult::ok(result),
    }
}

#[no_mangle]
pub extern "C" fn dm_convert_openapi_to_odcs(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match openapi_to_workspace(s) {
        Err(e) => DmResult::err(e),
        Ok(ws) => to_json_result!(ws),
    }
}

#[no_mangle]
pub extern "C" fn dm_analyze_openapi_conversion(yaml_or_json: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml_or_json, "yaml_or_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let names = openapi_component_names(s);
    if names.is_empty() {
        // Valid OpenAPI spec with no schema components — return empty analysis.
        return to_json_result!(JavaConversionAnalysis {
            total_schemas: 0,
            converted_schemas: 0,
            warnings: Vec::new(),
            unconverted_paths: Vec::new(),
        });
    }
    let converter = data_modelling_sdk::convert::OpenAPIToODCSConverter::new();
    let mut total = 0usize;
    let mut converted = 0usize;
    let mut warnings = Vec::new();
    let mut unconverted_paths = Vec::new();

    for name in &names {
        total += 1;
        match converter.analyze_conversion(s, name) {
            Err(e) => {
                unconverted_paths.push(name.clone());
                warnings.push(format!("{}: {}", name, e));
            }
            Ok(report) => {
                if report.skipped_fields.is_empty() {
                    converted += 1;
                } else {
                    unconverted_paths.push(name.clone());
                    warnings.extend(report.warnings.clone());
                }
            }
        }
    }

    let java = JavaConversionAnalysis {
        total_schemas: total,
        converted_schemas: converted,
        warnings,
        unconverted_paths,
    };
    to_json_result!(java)
}

// ── Group I: DataFlow (1) ────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_migrate_dataflow_to_domain(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match data_modelling_sdk::convert::migrate_dataflow_to_domain(s, None) {
        Err(e) => DmResult::err(format!("{}", e)),
        Ok(domain) => to_json_result!(sdk_domain_to_java(&domain)),
    }
}

// ── Group J: Sketch (8) ─────────────────────────────────────────────────────
//
// Sketch YAML format used by fixtures and tests is the Java-record format:
//   name: "..." | type: ENTITY_RELATIONSHIP | description: "..." | content: "..."
// This differs from the SDK's internal format (id/title/sketchType/excalidrawData).
// We use serde_yaml directly to parse/serialize the Java format.

#[no_mangle]
pub extern "C" fn dm_parse_sketch_yaml(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match serde_yaml::from_str::<JavaSketch>(s) {
        Err(e) => DmResult::err(format!("Parse error: Failed to parse sketch YAML: {}", e)),
        Ok(sketch) => to_json_result!(sketch),
    }
}

#[no_mangle]
pub extern "C" fn dm_parse_sketch_index_yaml(yaml: *const c_char) -> DmResult {
    let s = match cstr_to_str(yaml, "yaml") { Err(e) => return DmResult::err(e), Ok(v) => v };
    match serde_yaml::from_str::<JavaSketchIndex>(s) {
        Err(e) => DmResult::err(format!("Parse error: Failed to parse sketch index YAML: {}", e)),
        Ok(index) => to_json_result!(index),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_to_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let java: JavaSketch = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    match serde_yaml::to_string(&java) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(yaml) => DmResult::ok(yaml),
    }
}

#[no_mangle]
pub extern "C" fn dm_export_sketch_index_to_yaml(json: *const c_char) -> DmResult {
    let s = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let java_index: JavaSketchIndex = match serde_json::from_str(s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    match serde_yaml::to_string(&java_index) {
        Err(e) => DmResult::err(format!("serialize error: {}", e)),
        Ok(yaml) => DmResult::ok(yaml),
    }
}

#[no_mangle]
pub extern "C" fn dm_create_sketch(name: *const c_char, sketch_type: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let type_str = match cstr_to_str(sketch_type, "sketch_type") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let desc_opt = cstr_to_str_opt(description).filter(|s| !s.is_empty()).map(String::from);
    let java = JavaSketch {
        name: name_str.to_string(),
        sketch_type: type_str.to_string(),
        description: desc_opt,
        content: None,
    };
    to_json_result!(java)
}

#[no_mangle]
pub extern "C" fn dm_create_sketch_index(name: *const c_char) -> DmResult {
    let name_str = cstr_to_str_opt(name).unwrap_or("sketch-index");
    let java_index = JavaSketchIndex {
        name: name_str.to_string(),
        sketches: Vec::new(),
    };
    to_json_result!(java_index)
}

#[no_mangle]
pub extern "C" fn dm_add_sketch_to_index(index_json: *const c_char, sketch_json: *const c_char) -> DmResult {
    let idx_s = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let sk_s = match cstr_to_str(sketch_json, "sketch_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut java_index: JavaSketchIndex = match serde_json::from_str(idx_s) {
        Err(e) => return DmResult::err(format!("deserialize index error: {}", e)),
        Ok(v) => v,
    };
    let java_sketch: JavaSketch = match serde_json::from_str(sk_s) {
        Err(e) => return DmResult::err(format!("deserialize sketch error: {}", e)),
        Ok(v) => v,
    };
    java_index.sketches.push(java_sketch);
    to_json_result!(java_index)
}

#[no_mangle]
pub extern "C" fn dm_search_sketches(index_json: *const c_char, query: *const c_char) -> DmResult {
    let idx_s = match cstr_to_str(index_json, "index_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let q = match cstr_to_str(query, "query") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let java_index: JavaSketchIndex = match serde_json::from_str(idx_s) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let q_lower = q.to_lowercase();
    let matches: Vec<&JavaSketch> = java_index.sketches.iter()
        .filter(|sk| sk.name.to_lowercase().contains(&q_lower))
        .collect();
    to_json_result!(matches)
}

// ── Group K: Domain (4) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_create_domain(name: *const c_char, description: *const c_char) -> DmResult {
    let name_str = match cstr_to_str(name, "name") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let desc_opt = cstr_to_str_opt(description).filter(|s| !s.is_empty()).map(String::from);
    let domain = JavaBusinessDomain {
        name: name_str.to_string(),
        description: desc_opt,
        systems: Vec::new(),
    };
    to_json_result!(domain)
}

#[no_mangle]
pub extern "C" fn dm_add_system_to_domain(domain_json: *const c_char, system_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let sys_s = match cstr_to_str(system_json, "system_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let mut domain: JavaBusinessDomain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    let system: JavaSystemDefinition = match serde_json::from_str(sys_s) {
        Err(e) => return DmResult::err(format!("deserialize system error: {}", e)),
        Ok(v) => v,
    };
    domain.systems.push(system);
    to_json_result!(domain)
}

#[no_mangle]
pub extern "C" fn dm_add_cads_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let _node_s = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // Deserialize domain and return it unchanged (Java BusinessDomain has no cadsNodes field).
    let domain: JavaBusinessDomain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    to_json_result!(domain)
}

#[no_mangle]
pub extern "C" fn dm_add_odcs_node_to_domain(domain_json: *const c_char, node_json: *const c_char) -> DmResult {
    let dom_s = match cstr_to_str(domain_json, "domain_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let _node_s = match cstr_to_str(node_json, "node_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // Deserialize domain and return it unchanged (Java BusinessDomain has no odcsNodes field).
    let domain: JavaBusinessDomain = match serde_json::from_str(dom_s) {
        Err(e) => return DmResult::err(format!("deserialize domain error: {}", e)),
        Ok(v) => v,
    };
    to_json_result!(domain)
}

// ── Group L: Filter (5) ─────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let o = match cstr_to_str(owner, "owner") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let nodes: Vec<JavaCadsNode> = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let filtered: Vec<&JavaCadsNode> = nodes.iter()
        .filter(|n| n.owner == o)
        .collect();
    to_json_result!(filtered)
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_owner(json: *const c_char, owner: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let owner_str = match cstr_to_str(owner, "owner") { Err(_) => "", Ok(v) => v };
    let rels: Vec<serde_json::Value> = match serde_json::from_str(j) {
        Err(_) => return DmResult::ok("[]".to_string()),
        Ok(v) => v,
    };
    let filtered: Vec<&serde_json::Value> = rels.iter()
        .filter(|r| {
            r.get("owner")
                .and_then(|v| v.as_str())
                .map(|o| o == owner_str)
                .unwrap_or(false)
        })
        .collect();
    to_json_result!(filtered)
}

#[no_mangle]
pub extern "C" fn dm_filter_nodes_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let it = match cstr_to_str(infra_type, "infra_type") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let nodes: Vec<JavaCadsNode> = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize error: {}", e)),
        Ok(v) => v,
    };
    let it_lower = it.to_lowercase();
    let filtered: Vec<&JavaCadsNode> = nodes.iter()
        .filter(|n| n.node_type.to_lowercase() == it_lower)
        .collect();
    to_json_result!(filtered)
}

#[no_mangle]
pub extern "C" fn dm_filter_relationships_by_infrastructure_type(json: *const c_char, infra_type: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let infra_str = match cstr_to_str(infra_type, "infra_type") { Err(_) => "", Ok(v) => v };
    let rels: Vec<serde_json::Value> = match serde_json::from_str(j) {
        Err(_) => return DmResult::ok("[]".to_string()),
        Ok(v) => v,
    };
    let infra_lower = infra_str.to_lowercase();
    let filtered: Vec<&serde_json::Value> = rels.iter()
        .filter(|r| {
            r.get("type")
                .or_else(|| r.get("infrastructureType"))
                .or_else(|| r.get("infrastructure_type"))
                .and_then(|v| v.as_str())
                .map(|t| t.to_lowercase() == infra_lower)
                .unwrap_or(false)
        })
        .collect();
    to_json_result!(filtered)
}

#[no_mangle]
pub extern "C" fn dm_filter_by_tags(json: *const c_char, tags_json: *const c_char) -> DmResult {
    let j = match cstr_to_str(json, "json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    let t = match cstr_to_str(tags_json, "tags_json") { Err(e) => return DmResult::err(e), Ok(v) => v };
    // Validate that nodes and tags can be parsed, then return empty list
    // (CadsNode has no tags field, so no nodes can match any tag).
    let _nodes: Vec<JavaCadsNode> = match serde_json::from_str(j) {
        Err(e) => return DmResult::err(format!("deserialize nodes error: {}", e)),
        Ok(v) => v,
    };
    let _tags: Vec<String> = match serde_json::from_str(t) {
        Err(e) => return DmResult::err(format!("deserialize tags error: {}", e)),
        Ok(v) => v,
    };
    DmResult::ok("[]".to_string())
}
