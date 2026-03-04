//! Process Mining Bridge NIF - Complete wrapper around process_mining library
//!
//! Library docs: https://docs.rs/process_mining/latest/process_mining/

use rustler::{atoms, Encoder, Env, NifResult, Term};
use std::collections::{HashMap, HashSet};
use std::sync::Mutex;
use std::path::Path;
use uuid::Uuid;
use lazy_static::lazy_static;
use serde_json::Value;

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
pub fn atom_passthrough<'a>(env: Env<'a>, atom: rustler::types::Atom) -> NifResult<Term<'a>> {
    Ok((ok(), atom).encode(env))
}

#[rustler::nif]
pub fn small_list_passthrough<'a>(env: Env<'a>, list: Vec<rustler::types::Atom>) -> NifResult<Term<'a>> {
    Ok((ok(), list).encode(env))
}

#[rustler::nif]
pub fn tuple_passthrough<'a>(env: Env<'a>, t: (rustler::types::Atom, rustler::types::Atom, rustler::types::Atom)) -> NifResult<Term<'a>> {
    Ok((ok(), t).encode(env))
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
pub fn discover_dfg(id: String) -> NifResult<String> {
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

// Petri Net Discovery
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

    let mut pc = 0;
    for (a, b) in &causal {
        let pid = format!("p_{}", pc);
        pc += 1;
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
    ])?)
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

fn load(_env: Env<'_>, _term: Term<'_>) -> bool { true }

rustler::init!("process_mining_bridge", [
    nop, int_passthrough, atom_passthrough, small_list_passthrough, tuple_passthrough,
    echo_json, echo_term, echo_binary, echo_ocel_event, large_list_transfer,
    import_ocel_json_path, import_xes_path, num_events, num_objects,
    index_link_ocel, slim_link_ocel, ocel_type_stats,
    discover_dfg, discover_petri_net, token_replay,
    registry_get_type, registry_free, registry_list,
], load = load);
