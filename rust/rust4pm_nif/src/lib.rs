//! rust4pm_nif - Erlang NIF wrapper for rust4pm process mining
//!
//! Architecture:
//!   Java -> Erlang (via libei) -> NIF (this crate) -> Process Mining algorithms
//!
//! This NIF provides process mining capabilities:
//! - OCEL2 JSON parsing
//! - DFG (Directly-Follows Graph) discovery
//! - Token replay conformance checking

use rustler::{Encoder, Env, NifResult, Term, Binary, ResourceArc};
use std::collections::HashMap;
use std::sync::RwLock;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use regex::Regex;

mod atoms {
    rustler::atoms! {
        ok,
        error
    }
}

// ── OCEL2 Types ─────────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ocel2Event {
    id: String,
    #[serde(rename = "type")]
    event_type: String,
    time: String,
    #[serde(default)]
    attributes: Vec<Ocel2Attr>,
    #[serde(default)]
    relationships: Vec<Ocel2Rel>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ocel2Object {
    id: String,
    #[serde(rename = "type")]
    object_type: String,
    #[serde(default)]
    attributes: Vec<Ocel2Attr>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ocel2Attr {
    name: String,
    value: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ocel2Rel {
    #[serde(rename = "objectId")]
    object_id: String,
    qualifier: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ocel2Log {
    #[serde(rename = "objectTypes", default)]
    object_types: Vec<serde_json::Value>,
    #[serde(rename = "eventTypes", default)]
    event_types: Vec<serde_json::Value>,
    #[serde(default)]
    objects: Vec<Ocel2Object>,
    #[serde(default)]
    events: Vec<Ocel2Event>,
}

// ── Resource Types ───────────────────────────────────────────────────────────

struct OcelLogResource {
    log: RwLock<Ocel2Log>,
}

fn parse_timestamp_ms(iso: &str) -> i64 {
    iso.parse::<DateTime<Utc>>()
        .map(|dt| dt.timestamp_millis())
        .unwrap_or(0)
}

// ── NIF Functions ────────────────────────────────────────────────────────────

/// Parse OCEL2 JSON and return a handle
#[rustler::nif]
fn parse_ocel2_json<'a>(env: Env<'a>, json_binary: Binary<'a>) -> NifResult<Term<'a>> {
    let json_bytes = json_binary.as_slice();

    match serde_json::from_slice::<Ocel2Log>(json_bytes) {
        Ok(log) => {
            let resource = ResourceArc::new(OcelLogResource {
                log: RwLock::new(log),
            });
            Ok((atoms::ok(), resource).encode(env))
        }
        Err(e) => {
            Ok((atoms::error(), format!("Parse error: {}", e)).encode(env))
        }
    }
}

/// Get event count from OCEL log
#[rustler::nif]
fn log_event_count(env: Env<'_>, handle: ResourceArc<OcelLogResource>) -> NifResult<Term<'_>> {
    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;
    Ok((atoms::ok(), log.events.len()).encode(env))
}

/// Get object count from OCEL log
#[rustler::nif]
fn log_object_count(env: Env<'_>, handle: ResourceArc<OcelLogResource>) -> NifResult<Term<'_>> {
    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;
    Ok((atoms::ok(), log.objects.len()).encode(env))
}

/// Get event type statistics
#[rustler::nif]
fn log_event_type_stats(env: Env<'_>, handle: ResourceArc<OcelLogResource>) -> NifResult<Term<'_>> {
    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;

    let mut type_counts: HashMap<String, usize> = HashMap::new();
    for event in &log.events {
        *type_counts.entry(event.event_type.clone()).or_insert(0) += 1;
    }

    let stats: Vec<serde_json::Value> = type_counts
        .iter()
        .map(|(k, &v)| serde_json::json!({"type": k, "count": v}))
        .collect();

    Ok((atoms::ok(), serde_json::to_string(&stats).unwrap_or_default()).encode(env))
}

/// Get object type statistics
#[rustler::nif]
fn log_object_type_stats(env: Env<'_>, handle: ResourceArc<OcelLogResource>) -> NifResult<Term<'_>> {
    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;

    let mut type_counts: HashMap<String, usize> = HashMap::new();
    for obj in &log.objects {
        *type_counts.entry(obj.object_type.clone()).or_insert(0) += 1;
    }

    let stats: Vec<serde_json::Value> = type_counts
        .iter()
        .map(|(k, &v)| serde_json::json!({"type": k, "count": v}))
        .collect();

    Ok((atoms::ok(), serde_json::to_string(&stats).unwrap_or_default()).encode(env))
}

// ── DFG Discovery ────────────────────────────────────────────────────────────

#[derive(Serialize)]
struct DfgNode {
    id: String,
    label: String,
    count: u64,
}

#[derive(Serialize)]
struct DfgEdge {
    source: String,
    target: String,
    count: u64,
}

#[derive(Serialize)]
struct DfgGraph {
    nodes: Vec<DfgNode>,
    edges: Vec<DfgEdge>,
}

/// Discover Directly-Follows Graph from OCEL log
#[rustler::nif]
fn discover_dfg(env: Env<'_>, handle: ResourceArc<OcelLogResource>) -> NifResult<Term<'_>> {
    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;

    let mut activity_count: HashMap<String, u64> = HashMap::new();
    let mut follows_count: HashMap<(String, String), u64> = HashMap::new();
    let mut obj_last_event: HashMap<String, String> = HashMap::new();

    // Sort events by timestamp
    let mut events_sorted = log.events.clone();
    events_sorted.sort_by(|a, b| a.time.cmp(&b.time));

    // Build DFG by tracking object lifecycles
    for ev in &events_sorted {
        *activity_count.entry(ev.event_type.clone()).or_insert(0) += 1;

        for rel in &ev.relationships {
            if let Some(prev) = obj_last_event.get(&rel.object_id) {
                let key = (prev.clone(), ev.event_type.clone());
                *follows_count.entry(key).or_insert(0) += 1;
            }
            obj_last_event.insert(rel.object_id.clone(), ev.event_type.clone());
        }
    }

    // Build output graph
    let nodes: Vec<DfgNode> = activity_count
        .iter()
        .map(|(act, &cnt)| DfgNode {
            id: act.clone(),
            label: act.clone(),
            count: cnt,
        })
        .collect();

    let edges: Vec<DfgEdge> = follows_count
        .iter()
        .map(|((src, tgt), &cnt)| DfgEdge {
            source: src.clone(),
            target: tgt.clone(),
            count: cnt,
        })
        .collect();

    let dfg = DfgGraph { nodes, edges };

    match serde_json::to_string(&dfg) {
        Ok(json) => Ok((atoms::ok(), json).encode(env)),
        Err(e) => Ok((atoms::error(), format!("JSON error: {}", e)).encode(env)),
    }
}

// ── Token Replay Conformance ─────────────────────────────────────────────────

struct PetriNet {
    places: HashMap<String, String>,
    transitions: HashMap<String, String>,
    arcs_from_place: HashMap<String, Vec<String>>,
    arcs_from_trans: HashMap<String, Vec<String>>,
    initial_places: Vec<String>,
    final_places: Vec<String>,
}

/// Extract content between XML tags without using look-around regex
fn extract_tag_content(xml: &str, tag: &str) -> Vec<(String, String)> {
    let mut results = Vec::new();
    let open_tag_start = format!("<{}", tag);
    let close_tag = format!("</{}>", tag);

    let mut pos = 0;
    while pos < xml.len() {
        // Find opening tag
        if let Some(start) = xml[pos..].find(&open_tag_start) {
            let tag_start = pos + start;
            // Find end of opening tag (>)
            if let Some(tag_end_open) = xml[tag_start..].find('>') {
                let id_start = tag_start + tag_end_open + 1;

                // Extract id attribute
                let tag_content = &xml[tag_start..tag_start + tag_end_open + 1];
                let id = if let Some(id_match) = tag_content.split("id=\"").nth(1) {
                    if let Some(end_quote) = id_match.find('"') {
                        id_match[..end_quote].to_string()
                    } else {
                        continue;
                    }
                } else {
                    continue;
                };

                // Find closing tag
                if let Some(end_pos) = xml[id_start..].find(&close_tag) {
                    let content = &xml[id_start..id_start + end_pos];
                    results.push((id, content.to_string()));
                    pos = id_start + end_pos + close_tag.len();
                } else {
                    break;
                }
            } else {
                break;
            }
        } else {
            break;
        }
    }
    results
}

fn parse_pnml(pnml: &str) -> Result<PetriNet, String> {
    let mut net = PetriNet {
        places: HashMap::new(),
        transitions: HashMap::new(),
        arcs_from_place: HashMap::new(),
        arcs_from_trans: HashMap::new(),
        initial_places: Vec::new(),
        final_places: Vec::new(),
    };

    // Use simple regex for arc extraction (no look-around needed)
    let arc_re = Regex::new(r#"<arc\s[^>]*source="([^"]+)"[^>]*target="([^"]+)""#)
        .map_err(|e| e.to_string())?;

    let name_re = Regex::new(r#"<text>([^<]*)</text>"#)
        .map_err(|e| e.to_string())?;

    // Extract places using simple string parsing
    for (id, content) in extract_tag_content(pnml, "place") {
        let label = name_re.captures(&content)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().trim().to_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| id.clone());

        // Check for initial marking
        if content.contains("<initialMarking>") {
            net.initial_places.push(id.clone());
        }

        net.places.insert(id, label);
    }

    // Extract transitions using simple string parsing
    for (id, content) in extract_tag_content(pnml, "transition") {
        let label = name_re.captures(&content)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().trim().to_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| id.clone());

        net.transitions.insert(id.clone(), label);
    }

    // Extract arcs
    for cap in arc_re.captures_iter(pnml) {
        let src = cap[1].to_string();
        let tgt = cap[2].to_string();
        if net.places.contains_key(&src) {
            net.arcs_from_place.entry(src).or_default().push(tgt);
        } else if net.transitions.contains_key(&src) {
            net.arcs_from_trans.entry(src).or_default().push(tgt);
        }
    }

    // Identify final places (no outgoing arcs and not initial)
    for place_id in net.places.keys() {
        if !net.initial_places.contains(place_id) && !net.arcs_from_place.contains_key(place_id) {
            net.final_places.push(place_id.clone());
        }
    }

    if net.places.is_empty() {
        return Err("PNML parse error: no places found".to_string());
    }

    Ok(net)
}

fn token_replay(net: &PetriNet, trace: &[String]) -> (u64, u64, u64, u64) {
    let mut marking: HashMap<String, u64> = HashMap::new();
    for p in &net.initial_places {
        marking.insert(p.clone(), 1);
    }

    let mut produced = net.initial_places.len() as u64;
    let mut consumed = 0u64;
    let mut missing = 0u64;
    let mut remaining = 0u64;

    for activity in trace {
        let trans_id = net.transitions.iter()
            .find(|(_, label)| *label == activity)
            .map(|(id, _)| id.clone());

        if let Some(tid) = trans_id {
            let input_places: Vec<String> = net.arcs_from_place.iter()
                .filter(|(_, targets)| targets.contains(&tid))
                .map(|(src, _)| src.clone())
                .collect();

            for p in &input_places {
                let tokens = *marking.get(p).unwrap_or(&0);
                if tokens > 0 {
                    *marking.entry(p.clone()).or_insert(0) -= 1;
                    consumed += 1;
                } else {
                    missing += 1;
                    consumed += 1;
                }
            }

            if let Some(outputs) = net.arcs_from_trans.get(&tid) {
                for p in outputs {
                    *marking.entry(p.clone()).or_insert(0) += 1;
                    produced += 1;
                }
            }
        }
    }

    for (place_id, &count) in &marking {
        if !net.final_places.contains(place_id) {
            remaining += count;
        }
    }

    (produced, consumed, missing, remaining)
}

/// Check conformance using token replay
#[rustler::nif]
fn check_conformance<'a>(
    env: Env<'a>,
    handle: ResourceArc<OcelLogResource>,
    petri_net_pnml: Binary<'a>,
) -> NifResult<Term<'a>> {
    let pnml_str = String::from_utf8_lossy(petri_net_pnml.as_slice()).into_owned();

    let log = handle.log.read().map_err(|_| rustler::Error::Term(Box::new("Lock error")))?;

    let net = match parse_pnml(&pnml_str) {
        Ok(n) => n,
        Err(e) => return Ok((atoms::error(), e).encode(env)),
    };

    // Build object traces
    let mut obj_traces: HashMap<String, Vec<String>> = HashMap::new();
    let mut events_sorted = log.events.clone();
    events_sorted.sort_by(|a, b| a.time.cmp(&b.time));

    for ev in &events_sorted {
        for rel in &ev.relationships {
            obj_traces.entry(rel.object_id.clone())
                .or_default()
                .push(ev.event_type.clone());
        }
    }

    if obj_traces.is_empty() {
        let result = serde_json::json!({
            "fitness": 1.0,
            "precision": 1.0
        });
        return Ok((atoms::ok(), result.to_string()).encode(env));
    }

    let (total_produced, total_consumed, total_missing, total_remaining) =
        obj_traces.values().fold((0u64, 0u64, 0u64, 0u64), |acc, trace| {
            let (p, c, m, r) = token_replay(&net, trace);
            (acc.0 + p, acc.1 + c, acc.2 + m, acc.3 + r)
        });

    let fitness = if total_consumed + total_missing == 0 {
        1.0
    } else {
        (1.0 - (total_missing as f64 / (total_consumed + total_missing) as f64)).clamp(0.0, 1.0)
    };

    let precision = if total_produced + total_remaining == 0 {
        1.0
    } else {
        (1.0 - (total_remaining as f64 / (total_produced + total_remaining) as f64)).clamp(0.0, 1.0)
    };

    let result = serde_json::json!({
        "fitness": fitness,
        "precision": precision,
        "produced": total_produced,
        "consumed": total_consumed,
        "missing": total_missing,
        "remaining": total_remaining
    });

    Ok((atoms::ok(), result.to_string()).encode(env))
}

// ── Utility Functions ───────────────────────────────────────────────────────

/// Health check
#[rustler::nif]
fn ping(env: Env<'_>) -> NifResult<Term<'_>> {
    Ok((atoms::ok(), "pong").encode(env))
}

/// Get NIF version info
#[rustler::nif]
fn nif_version(env: Env<'_>) -> NifResult<Term<'_>> {
    Ok((atoms::ok(), env!("CARGO_PKG_VERSION")).encode(env))
}

// ── NIF Initialization ───────────────────────────────────────────────────────

fn load(env: Env<'_>, _term: Term<'_>) -> bool {
    rustler::resource!(OcelLogResource, env);
    true
}

rustler::init!("rust4pm_nif", [
    parse_ocel2_json,
    log_event_count,
    log_object_count,
    log_event_type_stats,
    log_object_type_stats,
    discover_dfg,
    check_conformance,
    ping,
    nif_version
], load = load);
