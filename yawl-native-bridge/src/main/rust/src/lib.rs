//! Rust4pm Process Mining Library
//!
//! This is the Rust component of YAWL v7's Three-Domain Native Bridge Pattern.
//! It runs as a NIF (Native Implemented Function) in the BEAM domain, providing
//! process mining algorithms with memory safety and performance.
//!
//! Key properties:
//! - Memory-safe Rust implementation
//! - OCEL parsing and validation
//! - Process mining algorithms (DFG, alpha++, token replay)
//! - Conformance checking
//! - UUID-based object handles for persistence

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};
use std::ptr;
use std::sync::RwLock;
use uuid::Uuid;

// Module structure
pub mod nif_bindings;
pub mod ocel;
pub mod mining;
pub mod conformance;

use nif_bindings::*;
use ocel::*;
use mining::*;
use conformance::*;

// Global state with thread-safe storage
static RUST4PM_STATE: RwLock<Option<Rust4pmState>> = RwLock::new(None);

/// Rust4pm state structure
pub struct Rust4pmState {
    pub logs: std::collections::HashMap<Uuid, OcelLog>,
    pub slim_logs: std::collections::HashMap<Uuid, SlimOcel>,
    pub petri_nets: std::collections::HashMap<Uuid, PetriNet>,
}

/// Initialize rust4pm state
#[no_mangle]
pub extern "C" fn rust4pm_init() -> c_int {
    let state = Rust4pmState {
        logs: std::collections::HashMap::new(),
        slim_logs: std::collections::HashMap::new(),
        petri_nets: std::collections::HashMap::new(),
    };

    let mut global_state = RUST4PM_STATE.write().unwrap();
    *global_state = Some(state);

    0 // success
}

/// Parse OCEL2 JSON string into a native OcelLog
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
/// The caller must ensure that json_ptr is not null.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_import_ocel_json_path(
    json_ptr: *const c_char,
) -> ParseResult {
    let json_path = match CStr::from_ptr(json_ptr).to_str() {
        Ok(s) => s,
        Err(e) => return ParseResult::error(format!("Invalid UTF-8: {}", e)),
    };

    // Read file contents
    let json_content = match std::fs::read_to_string(json_path) {
        Ok(content) => content,
        Err(e) => return ParseResult::error(format!("Failed to read file: {}", e)),
    };

    // Parse OCEL JSON
    let log = match OcelLog::from_json(&json_content) {
        Ok(log) => log,
        Err(e) => return ParseResult::error(format!("OCEL parsing failed: {}", e)),
    };

    // Generate UUID and store in global state
    let uuid = Uuid::new_v4();
    let mut state = RUST4PM_STATE.write().unwrap();
    if let Some(s) = state.as_mut() {
        s.logs.insert(uuid, log);
    }

    ParseResult::new(uuid)
}

/// Create slim OCEL representation from existing OCEL
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_slim_link(
    uuid_ptr: *const u8,
    uuid_len: usize,
) -> SlimLinkResult {
    // Convert UUID bytes to Uuid
    let uuid_bytes = std::slice::from_raw_parts(uuid_ptr, uuid_len);
    let uuid = match Uuid::from_slice(uuid_bytes) {
        Ok(u) => u,
        Err(e) => return SlimLinkResult::error(format!("Invalid UUID: {}", e)),
    };

    // Get original log from global state
    let mut state = RUST4PM_STATE.write().unwrap();
    let log = match state.as_mut().and_then(|s| s.logs.get_mut(&uuid)) {
        Some(log) => log,
        None => return SlimLinkResult::error("OcelId not found"),
    };

    // Create slim OCEL
    let slim = match SlimOcel::from_ocel(log) {
        Ok(slim) => slim,
        Err(e) => return SlimLinkResult::error(format!("Slim OCEL creation failed: {}", e)),
    };

    // Generate new UUID for slim OCEL
    let slim_uuid = Uuid::new_v4();
    if let Some(s) = state.as_mut() {
        s.slim_logs.insert(slim_uuid, slim);
    }

    SlimLinkResult::new(slim_uuid)
}

/// Discover declarative constraints via OC-DECLARE algorithm
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_discover_oc_declare(
    uuid_ptr: *const u8,
    uuid_len: usize,
) -> MiningResult {
    // Convert UUID bytes to Uuid
    let uuid_bytes = std::slice::from_raw_parts(uuid_ptr, uuid_len);
    let uuid = match Uuid::from_slice(uuid_bytes) {
        Ok(u) => u,
        Err(e) => return MiningResult::error(format!("Invalid UUID: {}", e)),
    };

    // Get slim OCEL from global state
    let mut state = RUST4PM_STATE.write().unwrap();
    let slim = match state.as_mut().and_then(|s| s.slim_logs.get(&uuid)) {
        Some(slim) => slim,
        None => return MiningResult::error("SlimOcelId not found"),
    };

    // Run OC-DECLARE mining
    let constraints = match mining::discover_oc_declare(slim) {
        Ok(constraints) => constraints,
        Err(e) => return MiningResult::error(format!("OC-DECLARE failed: {}", e)),
    };

    MiningResult::new(constraints)
}

/// Perform token replay with conformance checking
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_token_replay(
    ocel_uuid_ptr: *const u8,
    ocel_uuid_len: usize,
    pn_uuid_ptr: *const u8,
    pn_uuid_len: usize,
    result_ptr: *mut ConformanceReport,
) -> c_int {
    // Convert UUIDs
    let ocel_uuid = match Uuid::from_slice(std::slice::from_raw_parts(ocel_uuid_ptr, ocel_uuid_len)) {
        Ok(u) => u,
        Err(_) => return -1,
    };

    let pn_uuid = match Uuid::from_slice(std::slice::from_raw_parts(pn_uuid_ptr, pn_uuid_len)) {
        Ok(u) => u,
        Err(_) => return -1,
    };

    // Get OCEL and Petri net from global state
    let state = RUST4PM_STATE.read().unwrap();
    let log = match state.as_ref().and_then(|s| s.logs.get(&ocel_uuid)) {
        Some(log) => log,
        None => return -1,
    };

    let petri_net = match state.as_ref().and_then(|s| s.petri_nets.get(&pn_uuid)) {
        Some(net) => net,
        None => return -1,
    };

    // Perform token replay
    let report = match conformance::token_replay(log, petri_net) {
        Ok(report) => report,
        Err(_) => return -1,
    };

    // Write result to output pointer
    ptr::addr_of_mut!(*result_ptr).write(report);

    0 // success
}

/// Discover directly-follows graph
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_discover_dfg(
    uuid_ptr: *const u8,
    uuid_len: usize,
) -> MiningResult {
    // Convert UUID bytes to Uuid
    let uuid_bytes = std::slice::from_raw_parts(uuid_ptr, uuid_len);
    let uuid = match Uuid::from_slice(uuid_bytes) {
        Ok(u) => u,
        Err(e) => return MiningResult::error(format!("Invalid UUID: {}", e)),
    };

    // Get slim OCEL from global state
    let mut state = RUST4PM_STATE.write().unwrap();
    let slim = match state.as_mut().and_then(|s| s.slim_logs.get(&uuid)) {
        Some(slim) => slim,
        None => return MiningResult::error("SlimOcelId not found"),
    };

    // Discover DFG
    let dfg = match mining::discover_dfg(slim) {
        Ok(dfg) => dfg,
        Err(e) => return MiningResult::error(format!("DFG discovery failed: {}", e)),
    };

    MiningResult::new(vec![dfg])
}

/// Mine Petri net using alpha++ algorithm
///
/// # Safety
///
/// This function is unsafe because it dereferences raw pointers.
#[no_mangle]
pub unsafe extern "C" fn rust4pm_mine_alpha_plus_plus(
    uuid_ptr: *const u8,
    uuid_len: usize,
) -> MiningResult {
    // Convert UUID bytes to Uuid
    let uuid_bytes = std::slice::from_raw_parts(uuid_ptr, uuid_len);
    let uuid = match Uuid::from_slice(uuid_bytes) {
        Ok(u) => u,
        Err(e) => return MiningResult::error(format!("Invalid UUID: {}", e)),
    };

    // Get slim OCEL from global state
    let mut state = RUST4PM_STATE.write().unwrap();
    let slim = match state.as_mut().and_then(|s| s.slim_logs.get(&uuid)) {
        Some(slim) => slim,
        None => return MiningResult::error("SlimOcelId not found"),
    };

    // Mine Petri net
    let petri_net = match mining::mine_alpha_plus_plus(slim) {
        Ok(net) => net,
        Err(e) => return MiningResult::error(format!("alpha++ mining failed: {}", e)),
    };

    // Store in global state
    let net_uuid = Uuid::new_v4();
    if let Some(s) = state.as_mut() {
        s.petri_nets.insert(net_uuid, petri_net);
    }

    // Return reference UUID
    MiningResult::new(vec![net_uuid])
}

/// Clean up rust4pm resources
#[no_mangle]
pub extern "C" fn rust4pm_cleanup() {
    let mut state = RUST4PM_STATE.write().unwrap();
    *state = None;
}

// Result structures for FFI

/// Parse result structure
#[repr(C)]
pub struct ParseResult {
    pub uuid: [u8; 16],  // UUID bytes
    pub error: *mut c_char,  // Error message or null
}

impl ParseResult {
    pub fn new(uuid: Uuid) -> Self {
        let mut bytes = [0; 16];
        bytes.copy_from_slice(&uuid.as_bytes());
        ParseResult {
            uuid: bytes,
            error: ptr::null_mut(),
        }
    }

    pub fn error(message: String) -> Self {
        let c_string = match CString::new(message) {
            Ok(s) => s,
            Err(_) => return Self::new(Uuid::nil()),
        };
        ParseResult {
            uuid: [0; 16],
            error: c_string.into_raw(),
        }
    }
}

impl Drop for ParseResult {
    fn drop(&mut self) {
        if !self.error.is_null() {
            unsafe { CString::from_raw(self.error); }
        }
    }
}

/// Slim link result structure
#[repr(C)]
pub struct SlimLinkResult {
    pub slim_uuid: [u8; 16],
    pub error: *mut c_char,
}

impl SlimLinkResult {
    pub fn new(uuid: Uuid) -> Self {
        let mut bytes = [0; 16];
        bytes.copy_from_slice(&uuid.as_bytes());
        SlimLinkResult {
            slim_uuid: bytes,
            error: ptr::null_mut(),
        }
    }

    pub fn error(message: String) -> Self {
        let c_string = match CString::new(message) {
            Ok(s) => s,
            Err(_) => return Self::new(Uuid::nil()),
        };
        SlimLinkResult {
            slim_uuid: [0; 16],
            error: c_string.into_raw(),
        }
    }
}

impl Drop for SlimLinkResult {
    fn drop(&mut self) {
        if !self.error.is_null() {
            unsafe { CString::from_raw(self.error); }
        }
    }
}

/// Mining result structure
#[repr(C)]
pub struct MiningResult {
    pub items_ptr: *mut MiningItem,
    pub items_len: usize,
    pub error: *mut c_char,
}

#[repr(C)]
pub struct MiningItem {
    pub uuid: [u8; 16],
    pub item_type: [c_char; 32],  // "constraint", "dfg", "petri_net", etc.
}

impl MiningResult {
    pub fn new(items: Vec<MiningItem>) -> Self {
        let items_len = items.len();
        let items_ptr = if items.is_empty() {
            ptr::null_mut()
        } else {
            let box_ptr = Box::into_raw(Box::new(items.into_boxed_slice()));
            box_ptr as *mut MiningItem
        };

        MiningResult {
            items_ptr,
            items_len,
            error: ptr::null_mut(),
        }
    }

    pub fn error(message: String) -> Self {
        let c_string = match CString::new(message) {
            Ok(s) => s,
            Err(_) => return Self::new(Vec::new()),
        };
        MiningResult {
            items_ptr: ptr::null_mut(),
            items_len: 0,
            error: c_string.into_raw(),
        }
    }
}

impl Drop for MiningResult {
    fn drop(&mut self) {
        if !self.items_ptr.is_null() {
            unsafe { Box::from_raw(self.items_ptr as *const [MiningItem] as *mut [MiningItem]); }
        }
        if !self.error.is_null() {
            unsafe { CString::from_raw(self.error); }
        }
    }
}