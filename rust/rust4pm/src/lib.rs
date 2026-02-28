use std::collections::HashMap;
use std::ffi::CString;
use std::os::raw::{c_char, c_double};
use std::ptr;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

// ── Internal OCEL2 types ────────────────────────────────────────────────────

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

// ── Internal log handle ─────────────────────────────────────────────────────

struct OcelLogInternal {
    log: Ocel2Log,
    #[allow(dead_code)]
    event_id_strings: Vec<CString>,
    #[allow(dead_code)]
    event_type_strings: Vec<CString>,
    event_cs: Vec<OcelEventC>,
}

impl OcelLogInternal {
    fn new(log: Ocel2Log) -> Self {
        let mut event_id_strings   = Vec::with_capacity(log.events.len());
        let mut event_type_strings = Vec::with_capacity(log.events.len());
        let mut event_cs           = Vec::with_capacity(log.events.len());

        for ev in &log.events {
            let id_cs   = CString::new(ev.id.as_str()).unwrap_or_else(|_| CString::new("").unwrap());
            let type_cs = CString::new(ev.event_type.as_str()).unwrap_or_else(|_| CString::new("").unwrap());
            let ts_ms   = parse_timestamp_ms(&ev.time);
            event_cs.push(OcelEventC {
                event_id:     id_cs.as_ptr(),
                event_type:   type_cs.as_ptr(),
                timestamp_ms: ts_ms,
                attr_count:   ev.attributes.len(),
            });
            event_id_strings.push(id_cs);
            event_type_strings.push(type_cs);
        }

        OcelLogInternal { log, event_id_strings, event_type_strings, event_cs }
    }
}

fn parse_timestamp_ms(iso: &str) -> i64 {
    iso.parse::<DateTime<Utc>>()
        .map(|dt| dt.timestamp_millis())
        .unwrap_or(0)
}

// ── C ABI types ─────────────────────────────────────────────────────────────

#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelLogHandle {
    ptr: *mut OcelLogInternal,
}

#[repr(C)]
pub struct ParseResult {
    handle: OcelLogHandle,
    error:  *mut c_char,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct OcelEventC {
    pub event_id:     *const c_char,
    pub event_type:   *const c_char,
    pub timestamp_ms: i64,
    pub attr_count:   usize,
}

#[repr(C)]
pub struct OcelEventsResult {
    events: *const OcelEventC,
    count:  usize,
    error:  *mut c_char,
}

#[repr(C)]
pub struct DfgResultC {
    json:  *mut c_char,
    error: *mut c_char,
}

#[repr(C)]
pub struct ConformanceResultC {
    fitness:   c_double,
    precision: c_double,
    error:     *mut c_char,
}

// ── DFG serialization types ──────────────────────────────────────────────────

#[derive(Serialize)]
struct DfgNodeJson {
    id:    String,
    label: String,
    count: u64,
}

#[derive(Serialize)]
struct DfgEdgeJson {
    source: String,
    target: String,
    count:  u64,
}

#[derive(Serialize)]
struct DfgJson {
    nodes: Vec<DfgNodeJson>,
    edges: Vec<DfgEdgeJson>,
}

// ── Error helper ─────────────────────────────────────────────────────────────

fn make_error(msg: &str) -> *mut c_char {
    CString::new(msg)
        .unwrap_or_else(|_| CString::new("unknown error").unwrap())
        .into_raw()
}

// ── Exported C functions ──────────────────────────────────────────────────────

#[no_mangle]
pub unsafe extern "C" fn rust4pm_parse_ocel2_json(
    json:     *const c_char,
    json_len: usize,
) -> ParseResult {
    let bytes = std::slice::from_raw_parts(json as *const u8, json_len);
    match serde_json::from_slice::<Ocel2Log>(bytes) {
        Ok(log) => {
            let internal = Box::new(OcelLogInternal::new(log));
            ParseResult {
                handle: OcelLogHandle { ptr: Box::into_raw(internal) },
                error:  ptr::null_mut(),
            }
        }
        Err(e) => ParseResult {
            handle: OcelLogHandle { ptr: ptr::null_mut() },
            error:  make_error(&e.to_string()),
        },
    }
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_event_count(handle: OcelLogHandle) -> usize {
    if handle.ptr.is_null() { return 0; }
    (*handle.ptr).log.events.len()
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_get_events(handle: OcelLogHandle) -> OcelEventsResult {
    if handle.ptr.is_null() {
        return OcelEventsResult { events: ptr::null(), count: 0, error: make_error("null handle") };
    }
    let internal = &*handle.ptr;
    OcelEventsResult {
        events: internal.event_cs.as_ptr(),
        count:  internal.event_cs.len(),
        error:  ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_discover_dfg(handle: OcelLogHandle) -> DfgResultC {
    if handle.ptr.is_null() {
        return DfgResultC { json: ptr::null_mut(), error: make_error("null handle") };
    }
    let log = &(*handle.ptr).log;

    let mut activity_count: HashMap<String, u64> = HashMap::new();
    let mut follows_count: HashMap<(String, String), u64> = HashMap::new();
    let mut obj_last_event: HashMap<String, String> = HashMap::new();

    let mut events_sorted = log.events.clone();
    events_sorted.sort_by(|a, b| a.time.cmp(&b.time));

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

    let nodes: Vec<DfgNodeJson> = activity_count
        .iter()
        .map(|(act, &cnt)| DfgNodeJson { id: act.clone(), label: act.clone(), count: cnt })
        .collect();

    let edges: Vec<DfgEdgeJson> = follows_count
        .iter()
        .map(|((src, tgt), &cnt)| DfgEdgeJson { source: src.clone(), target: tgt.clone(), count: cnt })
        .collect();

    let dfg = DfgJson { nodes, edges };
    match serde_json::to_string(&dfg) {
        Ok(json_str) => DfgResultC {
            json:  CString::new(json_str).unwrap().into_raw(),
            error: ptr::null_mut(),
        },
        Err(e) => DfgResultC {
            json:  ptr::null_mut(),
            error: make_error(&e.to_string()),
        },
    }
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_check_conformance(
    log_handle:      OcelLogHandle,
    petri_net_pnml:  *const c_char,
    pn_len:          usize,
) -> ConformanceResultC {
    if log_handle.ptr.is_null() {
        return ConformanceResultC { fitness: 0.0, precision: 0.0, error: make_error("null handle") };
    }
    let pnml_bytes = std::slice::from_raw_parts(petri_net_pnml as *const u8, pn_len);
    let pnml_str = match std::str::from_utf8(pnml_bytes) {
        Ok(s) => s,
        Err(e) => return ConformanceResultC { fitness: 0.0, precision: 0.0, error: make_error(&e.to_string()) },
    };
    let log = &(*log_handle.ptr).log;
    match compute_conformance(log, pnml_str) {
        Ok((fitness, precision)) => ConformanceResultC { fitness, precision, error: ptr::null_mut() },
        Err(msg) => ConformanceResultC { fitness: 0.0, precision: 0.0, error: make_error(&msg) },
    }
}

fn compute_conformance(log: &Ocel2Log, pnml_str: &str) -> Result<(f64, f64), String> {
    let net = parse_pnml(pnml_str)?;
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
        return Ok((1.0, 1.0));
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

    Ok((fitness, precision))
}

// ── PNML parser ───────────────────────────────────────────────────────────────

struct PetriNet {
    places: HashMap<String, String>,
    transitions: HashMap<String, String>,
    arcs_from_place: HashMap<String, Vec<String>>,
    arcs_from_trans: HashMap<String, Vec<String>>,
    initial_places: Vec<String>,
    final_places: Vec<String>,
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

    let place_re = regex::Regex::new(
        r#"<place\s+id="([^"]+)"[^/]*>(?:[^<]|<(?!/?place))*?</place>"#
    ).map_err(|e| e.to_string())?;

    let trans_re = regex::Regex::new(
        r#"<transition\s+id="([^"]+)"[^/]*>(?:[^<]|<(?!/?transition))*?</transition>"#
    ).map_err(|e| e.to_string())?;

    let name_re = regex::Regex::new(
        r#"<name>.*?<text>([^<]*)</text>.*?</name>"#
    ).map_err(|e| e.to_string())?;

    let arc_re = regex::Regex::new(
        r#"<arc\s[^>]*source="([^"]+)"[^>]*target="([^"]+)""#
    ).map_err(|e| e.to_string())?;

    let init_re = regex::Regex::new(
        r#"<place\s+id="([^"]+)"(?:[^<]|<(?!initialMarking|/place))*<initialMarking"#
    ).map_err(|e| e.to_string())?;

    for cap in place_re.captures_iter(pnml) {
        let id      = cap[1].to_string();
        let segment = cap.get(0).map(|m| m.as_str()).unwrap_or("");
        let label   = name_re.captures(segment)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_else(|| id.clone());
        net.places.insert(id, label);
    }

    for cap in trans_re.captures_iter(pnml) {
        let id      = cap[1].to_string();
        let segment = cap.get(0).map(|m| m.as_str()).unwrap_or("");
        let label   = name_re.captures(segment)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
            .unwrap_or_else(|| id.clone());
        net.transitions.insert(id.clone(), label);
    }

    for cap in arc_re.captures_iter(pnml) {
        let src = cap[1].to_string();
        let tgt = cap[2].to_string();
        if net.places.contains_key(&src) {
            net.arcs_from_place.entry(src).or_default().push(tgt);
        } else if net.transitions.contains_key(&src) {
            net.arcs_from_trans.entry(src).or_default().push(tgt);
        }
    }

    for cap in init_re.captures_iter(pnml) {
        net.initial_places.push(cap[1].to_string());
    }

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

    let mut produced  = net.initial_places.len() as u64;
    let mut consumed  = 0u64;
    let mut missing   = 0u64;
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
                    missing  += 1;
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

// ── Free functions ────────────────────────────────────────────────────────────

#[no_mangle]
pub unsafe extern "C" fn rust4pm_log_free(handle: OcelLogHandle) {
    if !handle.ptr.is_null() {
        drop(Box::from_raw(handle.ptr));
    }
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_events_free(_result: OcelEventsResult) {
    // Events pointer is BORROWED from OcelLogInternal — do not free it.
    // The OcelEventsResult itself is stack-allocated on the C side.
    // error is null in the success path (set only on failure, but failure path
    // returns early without heap-allocating the events array).
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_dfg_free(result: DfgResultC) {
    if !result.json.is_null()  { drop(CString::from_raw(result.json)); }
    if !result.error.is_null() { drop(CString::from_raw(result.error)); }
}

#[no_mangle]
pub unsafe extern "C" fn rust4pm_error_free(error: *mut c_char) {
    if !error.is_null() {
        drop(CString::from_raw(error));
    }
}

// ── Unit tests ────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CStr;

    const SAMPLE_OCEL2: &str = r#"{
        "objectTypes": [{"name":"Order","attributes":[]}],
        "eventTypes": [{"name":"place","attributes":[]}],
        "objects": [{"id":"o1","type":"Order","attributes":[]}],
        "events": [
            {"id":"e1","type":"place","time":"2024-01-01T10:00:00Z","attributes":[],"relationships":[{"objectId":"o1","qualifier":""}]},
            {"id":"e2","type":"ship","time":"2024-01-02T10:00:00Z","attributes":[],"relationships":[{"objectId":"o1","qualifier":""}]}
        ]
    }"#;

    #[test]
    fn parse_valid_ocel2() {
        let json_bytes = SAMPLE_OCEL2.as_bytes();
        let result = unsafe {
            rust4pm_parse_ocel2_json(json_bytes.as_ptr() as *const c_char, json_bytes.len())
        };
        assert!(result.error.is_null(), "Expected no error");
        assert!(!result.handle.ptr.is_null());
        let count = unsafe { rust4pm_log_event_count(result.handle) };
        assert_eq!(count, 2);
        unsafe { rust4pm_log_free(result.handle); }
    }

    #[test]
    fn parse_invalid_json_returns_error() {
        let bad = b"not-valid-json";
        let result = unsafe {
            rust4pm_parse_ocel2_json(bad.as_ptr() as *const c_char, bad.len())
        };
        assert!(!result.error.is_null());
        assert!(result.handle.ptr.is_null());
        unsafe { rust4pm_error_free(result.error); }
    }

    #[test]
    fn discover_dfg_produces_valid_json() {
        let json_bytes = SAMPLE_OCEL2.as_bytes();
        let parse_result = unsafe {
            rust4pm_parse_ocel2_json(json_bytes.as_ptr() as *const c_char, json_bytes.len())
        };
        assert!(parse_result.error.is_null());
        let dfg_result = unsafe { rust4pm_discover_dfg(parse_result.handle) };
        assert!(dfg_result.error.is_null());
        let json_str = unsafe { CStr::from_ptr(dfg_result.json).to_str().unwrap() };
        assert!(json_str.contains("nodes"));
        assert!(json_str.contains("edges"));
        unsafe {
            rust4pm_dfg_free(dfg_result);
            rust4pm_log_free(parse_result.handle);
        }
    }

    #[test]
    fn get_events_returns_correct_count() {
        let json_bytes = SAMPLE_OCEL2.as_bytes();
        let parse_result = unsafe {
            rust4pm_parse_ocel2_json(json_bytes.as_ptr() as *const c_char, json_bytes.len())
        };
        assert!(parse_result.error.is_null());
        let events_result = unsafe { rust4pm_log_get_events(parse_result.handle) };
        assert!(events_result.error.is_null());
        assert_eq!(events_result.count, 2);
        unsafe { rust4pm_log_free(parse_result.handle); }
    }
}
