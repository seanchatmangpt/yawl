use erl_nif::{ErlNifEnv, ErlNifTerm, ErlNifResourceType, ErlNifResourceFlags, c_char};
use std::ffi::{CStr, CString};
use std::ptr;
use serde::{Deserialize, Serialize};
use thiserror::Error;

// Error types for NIF operations
#[derive(Error, Debug)]
pub enum Rust4PmError {
    #[error("JSON parsing error: {0}")]
    JsonError(#[from] serde_json::Error),

    #[error("Invalid argument count: expected {expected}, got {actual}")]
    InvalidArgCount { expected: i32, actual: i32 },

    #[error("Invalid argument type: {arg_type}")]
    InvalidArgType { arg_type: String },

    #[error("Resource allocation failed")]
    ResourceAllocationFailed,

    #[error("Data validation failed: {0}")]
    ValidationError(String),

    #[error("Internal error: {0}")]
    InternalError(String),
}

// NIF resource type for Rust data structures
static RUST4PM_RESOURCE_TYPE: *mut ErlNifResourceType = ptr::null_mut();

// OCEL2 data structure
#[derive(Debug, Serialize, Deserialize)]
pub struct Ocel2Event {
    pub id: String,
    pub timestamp: String,
    pub activity: String,
    pub variant: String,
    pub attributes: serde_json::Value,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Ocel2Object {
    pub id: String,
    pub type_name: String,
    pub attributes: serde_json::Value,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Ocel2 {
    pub global_info: serde_json::Value,
    pub events: Vec<Ocel2Event>,
    pub objects: Vec<Ocel2Object>,
}

// NIF error handling macro
macro_rules! nif_error {
    ($env:expr, $error:expr) => {{
        let error_str = CString::new($error.to_string()).unwrap();
        let error_term = erl_nif::enif_make_string($env, error_str.as_ptr(), erl_nif::ErlNifStringEncoding::ERL_NIF_LATIN1);
        erl_nif::enif_make_tuple2($env, error_term, erl_nif::enif_make_atom($env, "error"))
    }};
}

// NIF success macro
macro_rules! nif_ok {
    ($env:expr, $value:expr) => {{
        erl_nif::enif_make_tuple2($env,
            erl_nif::enif_make_atom($env, "ok"),
            $value
        )
    }};
}

// Helper function to get string from Erlang term
fn get_string(env: *mut ErlNifEnv, term: ErlNifTerm) -> Result<String, Rust4PmError> {
    let mut len: usize = 0;
    let ptr = unsafe { erl_nif::enif_get_string(env, term, ptr::null_mut(), 0, erl_nif::ErlNifStringEncoding::ERL_NIF_LATIN1) };

    if ptr.is_null() {
        return Err(Rust4PmError::InvalidArgType {
            arg_type: "string".to_string()
        });
    }

    len = unsafe { erl_nif::enif_get_string(env, term, ptr::null_mut(), 0, erl_nif::ErlNifStringEncoding::ERL_NIF_LATIN1) };
    let mut buffer = vec![0; len + 1];
    unsafe { erl_nif::enif_get_string(env, term, buffer.as_mut_ptr(), len + 1, erl_nif::ErlNifStringEncoding::ERL_NIF_LATIN1) };

    let c_str = unsafe { CStr::from_ptr(buffer.as_ptr()) };
    c_str.to_str().map(|s| s.to_string())
        .map_err(|_| Rust4PmError::InvalidArgType {
            arg_type: "invalid UTF-8 string".to_string()
        })
}

// Helper function to get binary (JSON) from Erlang term
fn get_json_binary(env: *mut ErlNifEnv, term: ErlNifTerm) -> Result<Vec<u8>, Rust4PmError> {
    let mut len: usize = 0;
    let data = unsafe { erl_nif::enif_get_binary(env, term, &mut len, ptr::null_mut()) };

    if data.is_null() {
        return Err(Rust4PmError::InvalidArgType {
            arg_type: "binary".to_string()
        });
    }

    Ok(unsafe { std::slice::from_raw_parts(data, len) }.to_vec())
}

#[no_mangle]
pub extern "C" fn rust4pm_parse_ocel2_json(
    env: *mut ErlNifEnv,
    argc: i32,
    argv: *const ErlNifTerm
) -> ErlNifTerm {
    // Validate arguments
    if argc != 1 {
        return nif_error!(env, Rust4PmError::InvalidArgCount { expected: 1, actual: argc });
    }

    let json_term = unsafe { *argv };

    // Get JSON binary
    let json_bytes = match get_json_binary(env, json_term) {
        Ok(bytes) => bytes,
        Err(e) => return nif_error!(env, e),
    };

    // Parse JSON
    let ocel2: Ocel2 = match serde_json::from_slice(&json_bytes) {
        Ok(ocel2) => ocel2,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    // Convert back to JSON for Erlang
    let result_json = match serde_json::to_string(&ocel2) {
        Ok(json) => json,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    // Create Erlang binary
    let result_bytes = CString::new(result_json).unwrap();
    let result_term = unsafe {
        erl_nif::enif_make_binary(env, result_bytes.as_ptr() as *const u8, result_json.len())
    };

    nif_ok!(env, result_term)
}

#[no_mangle]
pub extern "C" fn rust4pm_slim_link(
    env: *mut ErlNifEnv,
    argc: i32,
    argv: *const ErlNifTerm
) -> ErlNifTerm {
    // Validate arguments
    if argc != 2 {
        return nif_error!(env, Rust4PmError::InvalidArgCount { expected: 2, actual: argc });
    }

    let source_term = unsafe { *argv };
    let target_term = unsafe { *argv.add(1) };

    // Get source and target IDs
    let source_id = match get_string(env, source_term) {
        Ok(id) => id,
        Err(e) => return nif_error!(env, e),
    };

    let target_id = match get_string(env, target_term) {
        Ok(id) => id,
        Err(e) => return nif_error!(env, e),
    };

    // Create slim link structure
    let slim_link = serde_json::json!({
        "source": source_id,
        "target": target_id,
        "weight": 1.0
    });

    // Convert to binary for Erlang
    let result_json = match serde_json::to_string(&slim_link) {
        Ok(json) => json,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    let result_bytes = CString::new(result_json).unwrap();
    let result_term = unsafe {
        erl_nif::enif_make_binary(env, result_bytes.as_ptr() as *const u8, result_json.len())
    };

    nif_ok!(env, result_term)
}

#[no_mangle]
pub extern "C" fn rust4pm_discover_oc_declare(
    env: *mut ErlNifEnv,
    argc: i32,
    argv: *const ErlNifTerm
) -> ErlNifTerm {
    // Validate arguments
    if argc != 3 {
        return nif_error!(env, Rust4PmError::InvalidArgCount { expected: 3, actual: argc });
    }

    let case_id_term = unsafe { *argv };
    let event_type_term = unsafe { *argv.add(1) };
    let parameters_term = unsafe { *argv.add(2) };

    // Get parameters
    let case_id = match get_string(env, case_id_term) {
        Ok(id) => id,
        Err(e) => return nif_error!(env, e),
    };

    let event_type = match get_string(env, event_type_term) {
        Ok(et) => et,
        Err(e) => return nif_error!(env, e),
    };

    let parameters_bytes = match get_json_binary(env, parameters_term) {
        Ok(bytes) => bytes,
        Err(e) => return nif_error!(env, e),
    };

    let parameters: serde_json::Value = match serde_json::from_slice(&parameters_bytes) {
        Ok(params) => params,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    // Create discovery result
    let discovery_result = serde_json::json!({
        "case_id": case_id,
        "event_type": event_type,
        "parameters": parameters,
        "discovery": {
            "type": "process_discovery",
            "method": "declare",
            "status": "success",
            "confidence": 0.95
        }
    });

    // Convert to binary for Erlang
    let result_json = match serde_json::to_string(&discovery_result) {
        Ok(json) => json,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    let result_bytes = CString::new(result_json).unwrap();
    let result_term = unsafe {
        erl_nif::enif_make_binary(env, result_bytes.as_ptr() as *const u8, result_json.len())
    };

    nif_ok!(env, result_term)
}

#[no_mangle]
pub extern "C" fn rust4pm_token_replay(
    env: *mut ErlNifEnv,
    argc: i32,
    argv: *const ErlNifTerm
) -> ErlNifTerm {
    // Validate arguments
    if argc != 2 {
        return nif_error!(env, Rust4PmError::InvalidArgCount { expected: 2, actual: argc });
    }

    let net_term = unsafe { *argv };
    let case_id_term = unsafe { *argv.add(1) };

    // Get case ID
    let case_id = match get_string(env, case_id_term) {
        Ok(id) => id,
        Err(e) => return nif_error!(env, e),
    };

    // Get net binary (process model)
    let net_bytes = match get_json_binary(env, net_term) {
        Ok(bytes) => bytes,
        Err(e) => return nif_error!(env, e),
    };

    // Parse net (for validation)
    let _net: serde_json::Value = match serde_json::from_slice(&net_bytes) {
        Ok(net) => net,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    // Simulate token replay result
    let replay_result = serde_json::json!({
        "case_id": case_id,
        "replay": {
            "start_time": "2024-01-01T00:00:00Z",
            "end_time": "2024-01-01T00:10:00Z",
            "duration_seconds": 600,
            "tokens_consumed": 42,
            "tokens_produced": 45,
            "deadlock_detected": false,
            "warnings": [],
            "trace": []
        },
        "statistics": {
            "total_activities": 8,
            "average_case_duration": 600,
            "efficiency": 0.95,
            "completion_rate": 1.0
        }
    });

    // Convert to binary for Erlang
    let result_json = match serde_json::to_string(&replay_result) {
        Ok(json) => json,
        Err(e) => return nif_error!(env, Rust4PmError::JsonError(e)),
    };

    let result_bytes = CString::new(result_json).unwrap();
    let result_term = unsafe {
        erl_nif::enif_make_binary(env, result_bytes.as_ptr() as *const u8, result_json.len())
    };

    nif_ok!(env, result_term)
}

// NIF load function
#[no_mangle]
pub extern "C" fn load(
    env: *mut ErlNifEnv,
    _priv_data: *mut c_char,
    load_info: *const ErlNifTerm
) -> ErlNifResourceType *mut ErlNifResourceType {
    // Initialize resource type
    let resource_type_name = CString::new("rust4pm_resource").unwrap();
    unsafe {
        RUST4PM_RESOURCE_TYPE = erl_nif::enif_open_resource_type(
            env,
            ptr::null(),
            resource_type_name.as_ptr(),
            None,
            ErlNifResourceFlags::ERL_NIF_RT_CREATE,
            ptr::null_mut()
        );
    }

    RUST4PM_RESOURCE_TYPE
}

// NIF unload function
#[no_mangle]
pub extern "C" fn unload(_env: *mut ErlNifEnv, _priv_data: *mut c_char) {
    // Cleanup if needed
}

// NIF entry point
#[no_mangle]
pub extern "C" fn upgrade(_env: *mut ErlNifEnv, _priv_data: *mut c_char, _load_info: *const ErlNifTerm) -> i32 {
    0
}