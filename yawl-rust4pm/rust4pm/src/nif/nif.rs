//! Erlang NIF bindings for YAWL Process Mining Bridge
//!
//! This module provides NIF (Native Interfaced Function) bindings for Erlang
//! to access the process_mining crate functionality from RWTH Aachen.
//!
//! ## Architecture
//!
//! This is the BEAM ↔ Rust boundary of the Three-Domain Native Bridge Pattern.
//! The Rust NIF is loaded by process_mining_bridge.erl.

use rustler::{
    atoms,
    resource::ResourceArc,
    Encoder, Env, NifResult, Term,
};
use std::sync::Mutex;

// Import from process_mining crate with renamed symbols to avoid collisions
use process_mining::{
    discovery::case_centric::dfg::discover_dfg as pm_discover_dfg,
    core::event_data::case_centric::{XESEditableAttribute, constants::ACTIVITY_NAME},
    Exportable, Importable,
};

// Define atoms for responses
atoms! {
    ok,
    error,
}

/// Resource wrapper for EventLog
pub struct EventLogResource {
    pub log: Mutex<process_mining::EventLog>,
}

/// Resource wrapper for PetriNet
pub struct PetriNetResource {
    pub net: Mutex<process_mining::PetriNet>,
}

/// Resource wrapper for OCEL (Object-Centric Event Log)
pub struct OcelResource {
    pub ocel: Mutex<process_mining::OCEL>,
}

// ============================================================================
// XES Import/Export
// ============================================================================

/// Import a XES event log from a file path
///
/// Returns `{ok, Handle}` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn import_xes(env: Env<'_>, path: String) -> NifResult<Term<'_>> {
    match process_mining::EventLog::import_from_path(&path) {
        Ok(log) => {
            let resource = ResourceArc::new(EventLogResource {
                log: Mutex::new(log),
            });
            Ok((ok(), resource).encode(env))
        }
        Err(e) => Ok((error(), format!("Import failed: {}", e)).encode(env)),
    }
}

/// Export an EventLog to a XES file
///
/// Returns `ok` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn export_xes(
    env: Env<'_>,
    log_resource: ResourceArc<EventLogResource>,
    path: String,
) -> NifResult<Term<'_>> {
    let log = log_resource.log.lock().unwrap();
    match log.export_to_path(&path) {
        Ok(()) => Ok(ok().encode(env)),
        Err(e) => Ok((error(), format!("Export failed: {}", e)).encode(env)),
    }
}

// ============================================================================
// OCEL Import/Export
// ============================================================================

/// Import an OCEL (Object-Centric Event Log) from JSON file
///
/// Returns `{ok, Handle}` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn import_ocel_json(env: Env<'_>, path: String) -> NifResult<Term<'_>> {
    match process_mining::OCEL::import_from_path(&path) {
        Ok(ocel) => {
            let resource = ResourceArc::new(OcelResource {
                ocel: Mutex::new(ocel),
            });
            Ok((ok(), resource).encode(env))
        }
        Err(e) => Ok((error(), format!("OCEL import failed: {}", e)).encode(env)),
    }
}

/// Export an OCEL to a JSON file
///
/// Returns `ok` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn export_ocel_json(
    env: Env<'_>,
    ocel_resource: ResourceArc<OcelResource>,
    path: String,
) -> NifResult<Term<'_>> {
    let ocel = ocel_resource.ocel.lock().unwrap();
    match ocel.export_to_path(&path) {
        Ok(()) => Ok(ok().encode(env)),
        Err(e) => Ok((error(), format!("OCEL export failed: {}", e)).encode(env)),
    }
}

// ============================================================================
// Process Discovery
// ============================================================================

/// Discover a Directly-Follows Graph (DFG) from an EventLog
///
/// Returns `{ok, DfgJson}` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn discover_dfg(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>> {
    let log = log_resource.log.lock().unwrap();
    let dfg = pm_discover_dfg(&log);

    // Serialize DFG to JSON
    let dfg_json = serde_json::to_string(&dfg)
        .unwrap_or_else(|e| format!("{{\"error\": \"{}\"}}", e));

    Ok((ok(), dfg_json).encode(env))
}

/// Discover a Petri Net using Alpha+++ algorithm
///
/// Returns `{ok, PetriNetHandle}` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn discover_alpha(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>> {
    use process_mining::discovery::case_centric::alphappp::full::alphappp_discover_petri_net;
    use process_mining::core::event_data::case_centric::utils::activity_projection::log_to_activity_projection;

    let log = log_resource.log.lock().unwrap();

    // Create activity projection for Alpha+++
    let projection = log_to_activity_projection(&log);

    // Run Alpha+++ discovery with default config
    let petri_net = alphappp_discover_petri_net(&projection, Default::default());

    let resource = ResourceArc::new(PetriNetResource {
        net: Mutex::new(petri_net),
    });

    Ok((ok(), resource).encode(env))
}

/// Export a PetriNet to PNML format
///
/// Returns `{ok, PnmlString}` on success, `{error, Reason}` on failure.
#[rustler::nif]
pub fn export_pnml(
    env: Env<'_>,
    net_resource: ResourceArc<PetriNetResource>,
) -> NifResult<Term<'_>> {
    let net = net_resource.net.lock().unwrap();

    match net.export_to_bytes(".pnml") {
        Ok(bytes) => {
            // Convert bytes to string (PNML is XML/text)
            match String::from_utf8(bytes) {
                Ok(pnml) => Ok((ok(), pnml).encode(env)),
                Err(e) => Ok((error(), format!("PNML encoding error: {}", e)).encode(env)),
            }
        }
        Err(e) => Ok((error(), format!("PNML export failed: {}", e)).encode(env)),
    }
}

// ============================================================================
// Event Log Statistics
// ============================================================================

/// Get statistics about an EventLog
///
/// Returns a proplist with:
/// - `{traces, N}`: Number of traces (cases)
/// - `{events, N}`: Total number of events
/// - `{activities, N}`: Number of unique activities
/// - `{avg_events_per_trace, F}`: Average events per trace
#[rustler::nif]
pub fn event_log_stats(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>> {
    use process_mining::core::event_data::case_centric::AttributeValue;

    let log = log_resource.log.lock().unwrap();

    let num_traces = log.traces.len();
    let num_events: usize = log.traces.iter().map(|t| t.events.len()).sum();

    // Count unique activities using the correct API
    let mut activities = std::collections::HashSet::new();
    for trace in &log.traces {
        for event in &trace.events {
            if let Some(attr) = event.attributes.get_by_key(ACTIVITY_NAME) {
                if let AttributeValue::String(s) = &attr.value {
                    activities.insert(s.clone());
                }
            }
        }
    }
    let num_activities = activities.len();

    let avg_events = if num_traces > 0 {
        num_events as f64 / num_traces as f64
    } else {
        0.0
    };

    // Return as a proplist-style tuple for easier Erlang consumption
    Ok((
        ok(),
        (
            ("traces", num_traces),
            ("events", num_events),
            ("activities", num_activities),
            ("avg_events_per_trace", avg_events),
        ),
    )
        .encode(env))
}

// ============================================================================
// Resource Management
// ============================================================================

/// Free an EventLog resource handle
#[rustler::nif]
pub fn free_event_log(env: Env<'_>, _resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>> {
    Ok(ok().encode(env))
}

/// Free a PetriNet resource handle
#[rustler::nif]
pub fn free_petri_net(env: Env<'_>, _resource: ResourceArc<PetriNetResource>) -> NifResult<Term<'_>> {
    Ok(ok().encode(env))
}

/// Free an OCEL resource handle
#[rustler::nif]
pub fn free_ocel(env: Env<'_>, _resource: ResourceArc<OcelResource>) -> NifResult<Term<'_>> {
    Ok(ok().encode(env))
}

// ============================================================================
// NIF Module Registration
// ============================================================================

fn load(env: Env<'_>, _term: Term<'_>) -> bool {
    rustler::resource!(EventLogResource, env);
    rustler::resource!(PetriNetResource, env);
    rustler::resource!(OcelResource, env);
    true
}

rustler::init!("process_mining_bridge", [
    import_xes,
    export_xes,
    import_ocel_json,
    export_ocel_json,
    discover_dfg,
    discover_alpha,
    export_pnml,
    event_log_stats,
    free_event_log,
    free_petri_net,
    free_ocel,
], load = load);
