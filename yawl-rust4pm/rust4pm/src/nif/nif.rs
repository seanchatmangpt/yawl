//! Process Mining Bridge NIF - Complete wrapper around process_mining library
//!
//! Library docs: https://docs.rs/process_mining/latest/process_mining/

use rustler::{atoms, Atom, Encoder, Env, NifResult, Term};
use std::collections::{HashMap, HashSet};
use std::sync::Mutex;
use std::path::Path;
use uuid::Uuid;
use lazy_static::lazy_static;
use serde_json::Value;

use rustler::atoms::ok;

// Import the process_mining library
use process_mining::{
    EventLog, OCEL, IndexLinkedOCEL, SlimLinkedOCEL, LinkedOCELAccess,
    EventLogActivityProjection, Importable,
};

atoms! {
    ok, error, conformance_score, fitness, produced_tokens, consumed_tokens,
    missing_tokens, remaining_tokens, trace_count, event_count, activity_count,
}

// Registry item enum
#[derive(Debug)]
pub enum RegistryItem {
    EventLog(EventLog),
    OCEL(OCEL),
    IndexLinkedOCEL(IndexLinkedOCEL),
    SlimLinkedOCEL(SlimLinkedOCEL),
    DfgJson(String),
    PetriNetJson(String),
}

impl Clone for RegistryItem {
    fn clone(&self) -> Self {
        match self {
            RegistryItem::EventLog(log) => RegistryItem::EventLog(log.clone()),
            RegistryItem::OCEL(ocel) => RegistryItem::OCEL(ocel.clone()),
            RegistryItem::IndexLinkedOCEL(locel) => RegistryItem::IndexLinkedOCEL(locel.clone()),
            RegistryItem::SlimLinkedOCEL(locel) => RegistryItem::SlimLinkedOCEL(locel.clone()),
            RegistryItem::DfgJson(s) => RegistryItem::DfgJson(s.clone()),
            RegistryItem::PetriNetJson(s) => RegistryItem::PetriNetJson(s.clone()),
        }
    }
}

lazy_static! {
    static ref REGISTRY: Mutex<HashMap<String, RegistryItem>> = Mutex::new(HashMap::new());
}

fn store_item(item: RegistryItem) -> String {
    let id = Uuid::new_v4().to_string();
    REGISTRY.lock().unwrap().insert(id.clone(), item);
    id
}

// Benchmark functions
#[rustler::nif]
pub fn nop(env: Env<'_>) -> NifResult<Term<'_>> { Ok(ok().encode(env)) }

#[rustler::nif]
pub fn int_passthrough(env: Env<'_>, n: i64) -> NifResult<Term<'_>> {
    Ok((ok(), n).encode(env))
}

#[rustler::nif]
pub fn atom_passthrough(env: Env<'_>, atom: Atom) -> NifResult<Term<'_>> {
    Ok((ok(), atom).encode(env))
}

#[rustler::nif]
pub fn echo_json<'a>(env: Env<'a>, json: String) -> NifResult<Term<'a>> {
    Ok((ok(), json).encode(env))
}

#[rustler::nif]
pub fn echo_term<'a>(env: Env<'a>, term: Term<'a>) -> NifResult<Term<'a>> {
    Ok((ok(), term).encode(env))
}

#[rustler::nif]
pub fn echo_binary<'a>(env: Env<'a>, binary: Vec<u8>) -> NifResult<Term<'a>> {
    Ok((ok(), binary).encode(env))
}

#[rustler::nif]
pub fn echo_ocel_event<'a>(env: Env<'a>, event: String) -> NifResult<Term<'a>> {
    Ok((ok(), event).encode(env))
}

#[rustler::nif]
pub fn large_list_transfer<'a>(env: Env<'a>, list: Vec<i64>) -> NifResult<Term<'a>> {
    Ok((ok(), list).encode(env))
}

// Benchmark functions that were missing
#[rustler::nif]
pub fn small_list_passthrough<'a>(env: Env<'a>, list: Vec<Atom>) -> NifResult<Term<'a>> {
    Ok((ok(), list).encode(env))
}

#[rustler::nif]
pub fn tuple_passthrough<'a>(env: Env<'a>, tuple: (Atom, Atom, Atom, Atom, Atom)) -> NifResult<Term<'a>> {
    Ok((ok(), tuple).encode(env))
}

// OCEL operations using process_mining library
#[rustler::nif]
pub fn import_ocel_json_path(path: String) -> NifResult<String> {
    let ocel = OCEL::import_from_path(Path::new(&path))
        .map_err(|e| rustler::Error::Term(Box::new(format!("Failed to import OCEL: {}", e))))?;
    Ok(store_item(RegistryItem::OCEL(ocel)))
}

#[rustler::nif]
pub fn import_xes_path(path: String) -> NifResult<String> {
    let log = EventLog::import_from_path(Path::new(&path))
        .map_err(|e| rustler::Error::Term(Box::new(format!("Failed to import XES: {}", e))))?;
    Ok(store_item(RegistryItem::EventLog(log)))
}

#[rustler::nif]
pub fn num_events(id: String) -> NifResult<usize> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::OCEL(ocel) => Ok(ocel.events.len()),
        RegistryItem::IndexLinkedOCEL(locel) => Ok(locel.get_num_evs()),
        RegistryItem::SlimLinkedOCEL(locel) => Ok(locel.get_num_evs()),
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    }
}

#[rustler::nif]
pub fn num_objects(id: String) -> NifResult<usize> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::OCEL(ocel) => Ok(ocel.objects.len()),
        RegistryItem::IndexLinkedOCEL(locel) => Ok(locel.get_num_obs()),
        RegistryItem::SlimLinkedOCEL(locel) => Ok(locel.get_num_obs()),
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    }
}

#[rustler::nif]
pub fn index_link_ocel(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::OCEL(ocel) => {
            let linked = IndexLinkedOCEL::from_ocel(ocel.clone());
            drop(registry);
            Ok(store_item(RegistryItem::IndexLinkedOCEL(linked)))
        }
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    }
}

#[rustler::nif]
pub fn slim_link_ocel(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::OCEL(ocel) => {
            let linked = SlimLinkedOCEL::from_ocel(ocel.clone());
            drop(registry);
            Ok(store_item(RegistryItem::SlimLinkedOCEL(linked)))
        }
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    }
}

#[rustler::nif]
pub fn ocel_type_stats(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::OCEL(ocel) => {
            let mut event_types: HashMap<String, usize> = HashMap::new();
            let mut obj_types: HashMap<String, usize> = HashMap::new();
            for e in &ocel.events {
                *event_types.entry(e.activity.clone()).or_insert(0) += 1;
            }
            for o in &ocel.objects {
                *obj_types.entry(o.type_name.clone()).or_insert(0) += 1;
            }
            Ok(serde_json::json!({"event_type_counts": event_types, "object_type_counts": obj_types}).to_string())
        }
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    }
}

// DFG Discovery
#[rustler::nif]
pub fn discover_dfg_ocel(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    let ocel = match item {
        RegistryItem::OCEL(ocel) => ocel,
        _ => return Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    };

    let mut traces: HashMap<String, Vec<_>> = HashMap::new();
    for e in &ocel.events {
        for o in &e.omap {
            traces.entry(o.object_id.clone()).or_default().push(e);
        }
    }

    let mut edges: HashMap<(String, String), usize> = HashMap::new();
    let mut freq: HashMap<String, usize> = HashMap::new();
    let mut starts: HashMap<String, usize> = HashMap::new();
    let mut ends: HashMap<String, usize> = HashMap::new();

    for (_, mut evs) in traces {
        if evs.is_empty() { continue; }
        evs.sort_by(|a, b| a.timestamp.cmp(&b.timestamp));
        let acts: Vec<_> = evs.iter().map(|e| e.activity.clone()).collect();
        if !acts.is_empty() {
            *starts.entry(acts[0].clone()).or_insert(0) += 1;
            *ends.entry(acts[acts.len()-1].clone()).or_insert(0) += 1;
        }
        for a in &acts { *freq.entry(a.clone()).or_insert(0) += 1; }
        for w in acts.windows(2) {
            *edges.entry((w[0].clone(), w[1].clone())).or_insert(0) += 1;
        }
    }

    let nodes: Vec<_> = freq.iter().map(|(id, &f)| serde_json::json!({"id": id, "frequency": f})).collect();
    let e: Vec<_> = edges.iter().map(|((s,t), &f)| serde_json::json!({"source": s, "target": t, "frequency": f})).collect();
    Ok(serde_json::json!({"nodes": nodes, "edges": e, "start_activities": starts, "end_activities": ends}).to_string())
}

// DFG computation
#[rustler::nif]
pub fn compute_dfg(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    match item {
        RegistryItem::EventLog(log) => {
            // Simple DFG implementation for EventLog
            let mut edges: HashMap<(String, String), usize> = HashMap::new();
            let mut freq: HashMap<String, usize> = HashMap::new();

            for trace in &log.traces {
                let activities: Vec<&str> = trace.split("->").collect();
                for window in activities.windows(2) {
                    let a1 = window[0].trim().to_string();
                    let a2 = window[1].trim().to_string();
                    *edges.entry((a1.clone(), a2.clone())).or_insert(0) += 1;
                }
                for a in activities {
                    *freq.entry(a.to_string()).or_insert(0) += 1;
                }
            }

            let nodes: Vec<_> = freq.iter().map(|(id, &f)| serde_json::json!({"id": id, "frequency": f})).collect();
            let edge_list: Vec<_> = edges.iter().map(|((s,t), &f)| serde_json::json!({"source": s, "target": t, "frequency": f})).collect();

            Ok(serde_json::json!({"nodes": nodes, "edges": edge_list}).to_string())
        },
        _ => Err(rustler::Error::Term(Box::new("Not an EventLog"))),
    }
}

// Backward compatibility alias
#[rustler::nif]
pub fn discover_dfg(id: String) -> NifResult<String> {
    compute_dfg(id)
}

// Petri Net Discovery
#[rustler::nif]
pub fn discover_alpha(id: String) -> NifResult<String> {
    // For now, reuse discover_petri_net
    discover_petri_net(id)
}

#[rustler::nif]
pub fn discover_petri_net(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;
    let ocel = match item {
        RegistryItem::OCEL(ocel) => ocel,
        _ => return Err(rustler::Error::Term(Box::new("Not an OCEL"))),
    };

    let mut traces: HashMap<String, Vec<_>> = HashMap::new();
    for e in &ocel.events {
        for o in &e.omap {
            traces.entry(o.object_id.clone()).or_default().push(e);
        }
    }

    let mut activities: HashSet<String> = HashSet::new();
    let mut trcs: Vec<Vec<String>> = Vec::new();
    for (_, mut evs) in traces {
        if evs.is_empty() { continue; }
        evs.sort_by(|a, b| a.timestamp.cmp(&b.timestamp));
        let t: Vec<_> = evs.iter().map(|e| e.activity.clone()).collect();
        for a in &t { activities.insert(a.clone()); }
        trcs.push(t);
    }

    let mut df: HashSet<(String, String)> = HashSet::new();
    for t in &trcs {
        for w in t.windows(2) { df.insert((w[0].clone(), w[1].clone())); }
    }

    let mut causal: HashSet<(String, String)> = HashSet::new();
    for (a, b) in &df {
        if !df.contains(&(b.clone(), a.clone())) { causal.insert((a.clone(), b.clone())); }
    }

    let mut starts: HashSet<String> = HashSet::new();
    let mut ends: HashSet<String> = HashSet::new();
    for t in &trcs {
        if !t.is_empty() {
            starts.insert(t[0].clone());
            ends.insert(t[t.len()-1].clone());
        }
    }

    let mut places = vec![serde_json::json!({"id": "p_start", "is_start": true, "initial_marking": 1})];
    let mut transitions: Vec<_> = activities.iter().map(|a| serde_json::json!({"id": format!("t_{}", a), "name": a})).collect();
    let mut arcs: Vec<_> = starts.iter().map(|a| serde_json::json!({"source": "p_start", "target": format!("t_{}", a)})).collect();

    places.push(serde_json::json!({"id": "p_end", "is_end": true, "initial_marking": 0}));
    for a in &ends {
        arcs.push(serde_json::json!({"source": format!("t_{}", a), "target": "p_end"}));
    }

    for (pc, (a, b)) in causal.iter().enumerate() {
        let pid = format!("p_{}", pc);
        places.push(serde_json::json!({"id": pid}));
        arcs.push(serde_json::json!({"source": format!("t_{}", a), "target": pid}));
        arcs.push(serde_json::json!({"source": pid, "target": format!("t_{}", b)}));
    }

    let pn_id = store_item(RegistryItem::PetriNetJson(serde_json::json!({"places": places, "transitions": transitions, "arcs": arcs}).to_string()));
    drop(registry);
    Ok(pn_id)
}

// Token Replay
#[rustler::nif]
pub fn token_replay<'a>(env: Env<'a>, ocel_id: String, pn_id: String) -> NifResult<Term<'a>> {
    let registry = REGISTRY.lock().unwrap();
    let ocel = registry.get(&ocel_id).ok_or_else(|| rustler::Error::Term(Box::new("OCEL not found")))?;
    let pn = registry.get(&pn_id).ok_or_else(|| rustler::Error::Term(Box::new("PN not found")))?;

    let ocel = match ocel {
        RegistryItem::OCEL(o) => o,
        _ => return Err(rustler::Error::Term(Box::new("Not OCEL"))),
    };
    let pn_str = match pn {
        RegistryItem::PetriNetJson(s) => s,
        _ => return Err(rustler::Error::Term(Box::new("Not PN"))),
    };

    let pn_json: Value = serde_json::from_str(pn_str)
        .map_err(|e| rustler::Error::Term(Box::new(format!("PN parse error: {}", e))))?;

    let arcs = pn_json.get("arcs").and_then(|a| a.as_array()).ok_or_else(|| rustler::Error::Term(Box::new("No arcs")))?;

    let start_place = pn_json.get("places").and_then(|p| p.as_array())
        .and_then(|places| places.iter().find(|p| p.get("is_start").and_then(|v| v.as_bool()).unwrap_or(false)))
        .and_then(|p| p.get("id").and_then(|v| v.as_str()))
        .unwrap_or("p_start").to_string();

    let mut traces: HashMap<String, Vec<_>> = HashMap::new();
    for e in &ocel.events {
        for o in &e.omap { traces.entry(o.object_id.clone()).or_default().push(e); }
    }

    let mut produced: usize = 0;
    let mut consumed: usize = 0;
    let mut missing: usize = 0;
    let mut remaining: usize = 0;
    let mut total: usize = 0;

    for (_, mut evs) in traces {
        if evs.is_empty() { continue; }
        total += 1;
        evs.sort_by(|a, b| a.timestamp.cmp(&b.timestamp));

        let mut marking: HashMap<String, usize> = HashMap::new();
        marking.insert(start_place.clone(), 1);

        for e in &evs {
            let tid = format!("t_{}", e.activity);
            let inputs: Vec<_> = arcs.iter()
                .filter_map(|a| a.as_object())
                .filter(|a| a.get("target").and_then(|v| v.as_str()) == Some(&tid))
                .filter_map(|a| a.get("source").and_then(|v| v.as_str().map(|s| s.to_string())))
                .collect();
            let outputs: Vec<_> = arcs.iter()
                .filter_map(|a| a.as_object())
                .filter(|a| a.get("source").and_then(|v| v.as_str()) == Some(&tid))
                .filter_map(|a| a.get("target").and_then(|v| v.as_str().map(|s| s.to_string())))
                .collect();

            let mut can_fire = true;
            for p in &inputs {
                if marking.get(p).copied().unwrap_or(0) == 0 {
                    can_fire = false;
                    missing += 1;
                }
            }
            if can_fire {
                for p in &inputs {
                    let t = marking.entry(p.clone()).or_insert(0);
                    if *t > 0 { *t -= 1; consumed += 1; }
                }
                for p in &outputs {
                    *marking.entry(p.clone()).or_insert(0) += 1;
                    produced += 1;
                }
            }
        }
        remaining += marking.values().sum::<usize>();
    }

    let score = if produced > 0 || consumed > 0 {
        let pr = if produced > 0 { consumed as f64 / produced as f64 } else { 1.0 };
        let mr = if consumed + missing > 0 { 1.0 - missing as f64 / (consumed + missing) as f64 } else { 1.0 };
        0.5 * pr.min(1.0) + 0.5 * mr
    } else { 1.0 };

    Ok(rustler::Term::map_from_pairs(env, &[
        (atoms::conformance_score().encode(env), score.encode(env)),
        (atoms::fitness().encode(env), score.encode(env)),
        (atoms::produced_tokens().encode(env), produced.encode(env)),
        (atoms::consumed_tokens().encode(env), consumed.encode(env)),
        (atoms::missing_tokens().encode(env), missing.encode(env)),
        (atoms::remaining_tokens().encode(env), remaining.encode(env)),
        (atoms::trace_count().encode(env), total.encode(env)),
    ]))
}

// Registry management
#[rustler::nif]
pub fn registry_get_type(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id).ok_or_else(|| rustler::Error::Term(Box::new("Not found")))?;
    Ok(match item {
        RegistryItem::EventLog(_) => "EventLog",
        RegistryItem::OCEL(_) => "OCEL",
        RegistryItem::IndexLinkedOCEL(_) => "IndexLinkedOCEL",
        RegistryItem::SlimLinkedOCEL(_) => "SlimLinkedOCEL",
        RegistryItem::DfgJson(_) => "DfgJson",
        RegistryItem::PetriNetJson(_) => "PetriNetJson",
    }.to_string())
}

#[rustler::nif]
pub fn registry_free(id: String) -> NifResult<rustler::Atom> {
    REGISTRY.lock().unwrap().remove(&id).ok_or_else(|| rustler::Error::Term(Box::new("Not found")))?;
    Ok(ok())
}

#[rustler::nif]
pub fn registry_list() -> Vec<(String, String)> {
    REGISTRY.lock().unwrap().iter()
        .map(|(id, item)| (id.clone(), match item {
            RegistryItem::EventLog(_) => "EventLog",
            RegistryItem::OCEL(_) => "OCEL",
            RegistryItem::IndexLinkedOCEL(_) => "IndexLinkedOCEL",
            RegistryItem::SlimLinkedOCEL(_) => "SlimLinkedOCEL",
            RegistryItem::DfgJson(_) => "DfgJson",
            RegistryItem::PetriNetJson(_) => "PetriNetJson",
        }.to_string()))
        .collect()
}

// DFG computation from events
#[rustler::nif]
pub fn compute_dfg_from_events(events: Vec<String>) -> NifResult<String> {
    // Build DFG from event list
    let mut edges: HashMap<(String, String), usize> = HashMap::new();
    let mut freq: HashMap<String, usize> = HashMap::new();
    let mut starts: HashMap<String, usize> = HashMap::new();
    let mut ends: HashMap<String, usize> = HashMap::new();

    for trace in events {
        let activities: Vec<&str> = trace.split("->").collect();
        if !activities.is_empty() {
            // Start activity
            let start = activities[0].to_string();
            *starts.entry(start.clone()).or_insert(0) += 1;

            // End activity
            let end = activities[activities.len() - 1].to_string();
            *ends.entry(end.clone()).or_insert(0) += 1;
        }

        // Build edges
        for window in activities.windows(2) {
            let a1 = window[0].trim().to_string();
            let a2 = window[1].trim().to_string();
            *edges.entry((a1.clone(), a2.clone())).or_insert(0) += 1;
            *freq.entry(a1).or_insert(0) += 1;
        }
    }

    let nodes: Vec<_> = freq.iter().map(|(id, &f)| serde_json::json!({"id": id, "frequency": f})).collect();
    let edge_list: Vec<_> = edges.iter().map(|((s,t), &f)| serde_json::json!({"source": s, "target": t, "frequency": f})).collect();

    Ok(serde_json::json!({"nodes": nodes, "edges": edge_list, "start_activities": starts, "end_activities": ends}).to_string())
}

// Trace alignment to Petri net
#[rustler::nif]
pub fn align_trace(trace: Vec<String>, petri_net: String) -> NifResult<String> {
    let pn_json: Value = serde_json::from_str(&petri_net)
        .map_err(|e| rustler::Error::Term(Box::new(format!("Petri net parse error: {}", e))))?;

    let places = pn_json.get("places").and_then(|p| p.as_array()).ok_or_else(|| rustler::Error::Term(Box::new("No places")))?;
    let transitions = pn_json.get("transitions").and_then(|t| t.as_array()).ok_or_else(|| rustler::Error::Term(Box::new("No transitions")))?;
    let arcs = pn_json.get("arcs").and_then(|a| a.as_array()).ok_or_else(|| rustler::Error::Term(Box::new("No arcs")))?;

    // Find start place
    let start_place = places.iter()
        .filter_map(|p| p.as_object())
        .find(|p| p.get("is_start").and_then(|v| v.as_bool()).unwrap_or(false))
        .and_then(|p| p.get("id").and_then(|v| v.as_str()))
        .unwrap_or("p_start");

    // Create transition mapping
    let transition_map: HashMap<String, String> = transitions.iter()
        .filter_map(|t| t.as_object())
        .filter_map(|t| {
            let id = t.get("id").and_then(|v| v.as_str())?;
            let name = t.get("name").and_then(|v| v.as_str())?;
            Some((format!("t_{}", name), id.to_string()))
        })
        .collect();

    let mut alignment_log: Vec<serde_json::Value> = Vec::new();
    let mut marking: HashMap<String, usize> = HashMap::new();
    marking.insert(start_place.to_string(), 1);

    let mut produced_tokens = 0;
    let mut consumed_tokens = 0;
    let mut missing_tokens = 0;

    for (i, activity) in trace.iter().enumerate() {
        let transition_id = match transition_map.get(&format!("t_{}", activity)) {
            Some(id) => id,
            None => {
                alignment_log.push(serde_json::json!({
                    "step": i + 1,
                    "activity": activity,
                    "status": "missing",
                    "message": format!("Transition for activity '{}' not found in Petri net", activity)
                }));
                missing_tokens += 1;
                continue;
            }
        };

        // Check if transition can fire (inputs have tokens)
        let can_fire = arcs.iter()
            .filter_map(|a| a.as_object())
            .filter(|a| a.get("target").and_then(|v| v.as_str()) == Some(transition_id))
            .all(|a| {
                let source = a.get("source").and_then(|v| v.as_str()).unwrap_or("");
                marking.get(source).copied().unwrap_or(0) > 0
            });

        if can_fire {
            alignment_log.push(serde_json::json!({
                "step": i + 1,
                "activity": activity,
                "status": "fired",
                "message": format!("Transition {} fired successfully", transition_id)
            }));

            // Consume input tokens
            for a in arcs.iter()
                .filter_map(|a| a.as_object())
                .filter(|a| a.get("target").and_then(|v| v.as_str()) == Some(transition_id)) {

                let source = a.get("source").and_then(|v| v.as_str()).unwrap_or("");
                if let Some(tokens) = marking.get_mut(source) {
                    if *tokens > 0 {
                        *tokens -= 1;
                        consumed_tokens += 1;
                    }
                }
            }

            // Produce output tokens
            for a in arcs.iter()
                .filter_map(|a| a.as_object())
                .filter(|a| a.get("source").and_then(|v| v.as_str()) == Some(transition_id)) {

                let target = a.get("target").and_then(|v| v.as_str()).unwrap_or("");
                *marking.entry(target.to_string()).or_insert(0) += 1;
                produced_tokens += 1;
            }
        } else {
            alignment_log.push(serde_json::json!({
                "step": i + 1,
                "activity": activity,
                "status": "blocked",
                "message": format!("Transition {} cannot fire - missing input tokens", transition_id)
            }));
            missing_tokens += 1;
        }
    }

    let alignment = serde_json::json!({
        "trace": trace,
        "alignment_log": alignment_log,
        "metrics": {
            "produced_tokens": produced_tokens,
            "consumed_tokens": consumed_tokens,
            "missing_tokens": missing_tokens,
            "fitness": if produced_tokens > 0 {
                consumed_tokens as f64 / produced_tokens as f64
            } else {
                1.0
            }
        }
    });

    Ok(alignment.to_string())
}

fn load(_env: Env<'_>, _term: Term<'_>) -> bool { true }

// ============================================================================
// NIF SUFFIX WRAPPERS - Erlang expects functions with _nif suffix
// These wrappers delegate to the actual implementations
// ============================================================================

#[rustler::nif]
pub fn import_xes_nif(path: String) -> NifResult<String> {
    import_xes_path(path)
}

#[rustler::nif]
pub fn import_ocel_json_nif(path: String) -> NifResult<String> {
    import_ocel_json_path(path)
}

#[rustler::nif]
pub fn discover_dfg_nif(id: String) -> NifResult<String> {
    discover_dfg(id)
}

#[rustler::nif]
pub fn discover_alpha_nif(id: String) -> NifResult<String> {
    discover_alpha(id)
}

#[rustler::nif]
pub fn discover_petri_net_nif(id: String) -> NifResult<String> {
    discover_petri_net(id)
}

#[rustler::nif]
pub fn compute_dfg_from_events_nif(events: Vec<String>) -> NifResult<String> {
    compute_dfg_from_events(events)
}

#[rustler::nif]
pub fn token_replay_nif<'a>(env: Env<'a>, ocel_id: String, pn_id: String) -> NifResult<Term<'a>> {
    token_replay(env, ocel_id, pn_id)
}

#[rustler::nif]
pub fn registry_get_type_nif(id: String) -> NifResult<String> {
    registry_get_type(id)
}

#[rustler::nif]
pub fn registry_free_nif(id: String) -> NifResult<rustler::Atom> {
    registry_free(id)
}

#[rustler::nif]
pub fn registry_list_nif() -> Vec<(String, String)> {
    registry_list()
}

#[rustler::nif]
pub fn event_log_stats_nif(id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let num_events = ocel.events.len();
            let num_objects = ocel.objects.len();
            let num_traces: usize = ocel.events.iter()
                .flat_map(|e| e.omap.iter().map(|o| o.object_id.clone()))
                .collect::<std::collections::HashSet<_>>()
                .len();

            Ok(serde_json::json!({
                "traces": num_traces,
                "events": num_events,
                "objects": num_objects,
                "activities": ocel.events.iter().map(|e| e.activity.clone()).collect::<std::collections::HashSet<_>>().len()
            }).to_string())
        },
        RegistryItem::EventLog(log) => {
            let num_traces = log.traces.len();
            let activities: HashSet<String> = log.traces.iter()
                .flat_map(|t| t.split("->").map(|s| s.trim().to_string()))
                .collect();

            Ok(serde_json::json!({
                "traces": num_traces,
                "events": activities.len(),
                "activities": activities.len()
            }).to_string())
        },
        _ => Err(rustler::Error::Term(Box::new("Unsupported type for stats")))
    }
}

#[rustler::nif]
pub fn align_trace_nif<'a>(env: Env<'a>, trace: Vec<String>, petri_net: String, _timeout: i64) -> NifResult<Term<'a>> {
    let result = align_trace(trace, petri_net)?;
    Ok((ok(), result).encode(env))
}

#[rustler::nif]
pub fn num_events_nif(id: String) -> NifResult<usize> {
    num_events(id)
}

#[rustler::nif]
pub fn num_objects_nif(id: String) -> NifResult<usize> {
    num_objects(id)
}

#[rustler::nif]
pub fn discover_dfg_ocel_nif(id: String) -> NifResult<String> {
    discover_dfg_ocel(id)
}

// ============================================================================
// Additional NIF Functions Required by Erlang Bridge
// ============================================================================

/// Log event count - returns number of events in OCEL
#[rustler::nif]
pub fn log_event_count(ocel_id: String) -> NifResult<usize> {
    num_events(ocel_id)
}

/// Log object count - returns number of objects in OCEL
#[rustler::nif]
pub fn log_object_count(ocel_id: String) -> NifResult<usize> {
    num_objects(ocel_id)
}

/// Get events from OCEL as JSON
#[rustler::nif]
pub fn log_get_events(ocel_id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let events: Vec<_> = ocel.events.iter().map(|e| serde_json::json!({
                "id": e.id,
                "activity": e.activity,
                "timestamp": e.timestamp,
                "omap": e.omap.iter().map(|o| serde_json::json!({
                    "object_id": o.object_id,
                    "type": o.type_name
                })).collect::<Vec<_>>()
            })).collect();
            Ok(serde_json::to_string(&events).unwrap_or_else(|_| "[]".to_string()))
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Get objects from OCEL as JSON
#[rustler::nif]
pub fn log_get_objects(ocel_id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let objects: Vec<_> = ocel.objects.iter().map(|o| serde_json::json!({
                "id": o.id,
                "type": o.type_name,
                "ovmap": o.ovmap
            })).collect();
            Ok(serde_json::to_string(&objects).unwrap_or_else(|_| "[]".to_string()))
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Free events handle - no-op since we use registry IDs
#[rustler::nif]
pub fn events_free(_handle: String) -> NifResult<rustler::Atom> {
    Ok(ok())
}

/// Free objects handle - no-op since we use registry IDs
#[rustler::nif]
pub fn objects_free(_handle: String) -> NifResult<rustler::Atom> {
    Ok(ok())
}

/// Calculate performance metrics from OCEL
#[rustler::nif]
pub fn calculate_performance_metrics_nif(ocel_id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            // Calculate real performance metrics from timestamps
            let mut durations: Vec<i64> = Vec::new();
            let mut traces: HashMap<String, Vec<_>> = HashMap::new();

            for e in &ocel.events {
                for o in &e.omap {
                    traces.entry(o.object_id.clone()).or_default().push(e);
                }
            }

            for (_, mut evs) in traces {
                if evs.len() >= 2 {
                    evs.sort_by(|a, b| a.timestamp.cmp(&b.timestamp));
                    let start = evs.first().unwrap().timestamp;
                    let end = evs.last().unwrap().timestamp;
                    durations.push(end - start);
                }
            }

            let avg_duration = if !durations.is_empty() {
                durations.iter().sum::<i64>() as f64 / durations.len() as f64
            } else {
                0.0
            };

            let min_duration = durations.iter().min().copied().unwrap_or(0);
            let max_duration = durations.iter().max().copied().unwrap_or(0);

            Ok(serde_json::json!({
                "avg_duration_ms": avg_duration,
                "min_duration_ms": min_duration,
                "max_duration_ms": max_duration,
                "throughput_per_hour": if avg_duration > 0.0 { 3600000.0 / avg_duration } else { 0.0 },
                "trace_count": durations.len()
            }).to_string())
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Get activity frequency from OCEL
#[rustler::nif]
pub fn get_activity_frequency_nif(ocel_id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let mut freq: HashMap<String, usize> = HashMap::new();
            for e in &ocel.events {
                *freq.entry(e.activity.clone()).or_insert(0) += 1;
            }
            Ok(serde_json::to_string(&freq).unwrap_or_else(|_| "{}".to_string()))
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Find longest traces in OCEL
#[rustler::nif]
pub fn find_longest_traces_nif(ocel_id: String, top_n: usize) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let mut traces: HashMap<String, Vec<_>> = HashMap::new();
            for e in &ocel.events {
                for o in &e.omap {
                    traces.entry(o.object_id.clone()).or_default().push(e);
                }
            }

            let mut trace_lengths: Vec<_> = traces.iter()
                .map(|(id, evs)| (id.clone(), evs.len()))
                .collect();
            trace_lengths.sort_by(|a, b| b.1.cmp(&a.1));

            let result: Vec<_> = trace_lengths.iter()
                .take(top_n)
                .map(|(id, len)| serde_json::json!({
                    "trace_id": id,
                    "event_count": len
                }))
                .collect();

            Ok(serde_json::to_string(&result).unwrap_or_else(|_| "[]".to_string()))
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Export XES - not yet implemented, throw proper error
#[rustler::nif]
pub fn export_xes_nif(_id: String, _path: String) -> NifResult<String> {
    Err(rustler::Error::Term(Box::new(
        "export_xes requires XES export implementation. Use OCEL JSON export instead."
    )))
}

/// Export OCEL JSON
#[rustler::nif]
pub fn export_ocel_json_nif(ocel_id: String, path: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", ocel_id))))?;

    match item {
        RegistryItem::OCEL(ocel) => {
            let json = serde_json::to_string(&**ocel)
                .map_err(|e| rustler::Error::Term(Box::new(format!("JSON error: {}", e))))?;
            std::fs::write(&path, &json)
                .map_err(|e| rustler::Error::Term(Box::new(format!("Write error: {}", e))))?;
            Ok(path)
        },
        _ => Err(rustler::Error::Term(Box::new("Not an OCEL")))
    }
}

/// Import OCEL XML - not yet implemented
#[rustler::nif]
pub fn import_ocel_xml_nif(_path: String) -> NifResult<String> {
    Err(rustler::Error::Term(Box::new(
        "import_ocel_xml requires OCEL XML parsing. Use JSON format instead."
    )))
}

/// Import OCEL SQLite - not yet implemented
#[rustler::nif]
pub fn import_ocel_sqlite_nif(_path: String) -> NifResult<String> {
    Err(rustler::Error::Term(Box::new(
        "import_ocel_sqlite requires SQLite OCEL support. Use JSON format instead."
    )))
}

/// Import PNML - not yet implemented
#[rustler::nif]
pub fn import_pnml_nif(_path: String) -> NifResult<String> {
    Err(rustler::Error::Term(Box::new(
        "import_pnml requires PNML parsing. Use discover_petri_net to create Petri nets from logs."
    )))
}

/// Export PNML
#[rustler::nif]
pub fn export_pnml_nif(pn_id: String) -> NifResult<String> {
    let registry = REGISTRY.lock().unwrap();
    let item = registry.get(&pn_id)
        .ok_or_else(|| rustler::Error::Term(Box::new(format!("Not found: {}", pn_id))))?;

    match item {
        RegistryItem::PetriNetJson(json) => {
            // Convert JSON to basic PNML format
            let pn: Value = serde_json::from_str(json)
                .map_err(|e| rustler::Error::Term(Box::new(format!("Parse error: {}", e))))?;

            let places = pn.get("places").and_then(|p| p.as_array()).unwrap_or(&Vec::new());
            let transitions = pn.get("transitions").and_then(|t| t.as_array()).unwrap_or(&Vec::new());
            let arcs = pn.get("arcs").and_then(|a| a.as_array()).unwrap_or(&Vec::new());

            let mut pnml = String::from("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<pnml>\n  <net>\n");

            for p in places {
                if let Some(id) = p.get("id").and_then(|v| v.as_str()) {
                    pnml.push_str(&format!("    <place id=\"{}\"/>\n", id));
                }
            }

            for t in transitions {
                if let (Some(id), Some(name)) = (t.get("id").and_then(|v| v.as_str()), t.get("name").and_then(|v| v.as_str())) {
                    pnml.push_str(&format!("    <transition id=\"{}\"><name><text>{}</text></name></transition>\n", id, name));
                }
            }

            for a in arcs {
                if let (Some(id), Some(src), Some(tgt)) = (
                    a.get("id").and_then(|v| v.as_str()),
                    a.get("source").and_then(|v| v.as_str()),
                    a.get("target").and_then(|v| v.as_str())
                ) {
                    pnml.push_str(&format!("    <arc id=\"{}\" source=\"{}\" target=\"{}\"/>\n", id, src, tgt));
                }
            }

            pnml.push_str("  </net>\n</pnml>");
            Ok(pnml)
        },
        _ => Err(rustler::Error::Term(Box::new("Not a PetriNet")))
    }
}

rustler::init!("process_mining_bridge", [
    // Benchmark functions
    nop,
    int_passthrough,
    atom_passthrough,
    echo_json,
    echo_term,
    echo_binary,
    echo_ocel_event,
    large_list_transfer,
    small_list_passthrough,
    tuple_passthrough,

    // OCEL/XES Import
    import_ocel_json_path,
    import_xes_path,
    import_ocel_json_nif,
    import_xes_nif,

    // OCEL Operations
    num_events,
    num_objects,
    index_link_ocel,
    slim_link_ocel,
    ocel_type_stats,
    num_events_nif,
    num_objects_nif,

    // DFG Discovery
    discover_dfg_ocel,
    compute_dfg,
    discover_dfg,
    discover_dfg_ocel_nif,
    discover_dfg_nif,
    compute_dfg_from_events,
    compute_dfg_from_events_nif,

    // Petri Net Discovery
    discover_alpha,
    discover_petri_net,
    discover_alpha_nif,
    discover_petri_net_nif,

    // Token Replay & Conformance
    token_replay,
    token_replay_nif,

    // Registry Management
    registry_get_type,
    registry_free,
    registry_list,
    registry_get_type_nif,
    registry_free_nif,
    registry_list_nif,

    // Event Log Stats
    event_log_stats_nif,
    log_event_count,
    log_object_count,
    log_get_events,
    log_get_objects,
    events_free,
    objects_free,

    // Performance Metrics
    calculate_performance_metrics_nif,
    get_activity_frequency_nif,
    find_longest_traces_nif,

    // Trace Alignment
    align_trace,
    align_trace_nif,

    // Export Functions
    export_xes_nif,
    export_ocel_json_nif,
    export_pnml_nif,

    // Import Functions (unsupported - throw errors)
    import_ocel_xml_nif,
    import_ocel_sqlite_nif,
    import_pnml_nif,
], load = load);
