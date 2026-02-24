use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::sync::OnceLock;
use chrono::{DateTime, Utc};

/// Parsed event log (case-centric XES format)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EventLog {
    pub traces: Vec<Trace>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Trace {
    pub case_id: String,
    pub events: Vec<Event>,
    pub attributes: HashMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Event {
    pub activity: String,
    pub timestamp: Option<DateTime<Utc>>,
    pub attributes: HashMap<String, String>,
}

/// Petri Net representation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PetriNet {
    pub places: Vec<Place>,
    pub transitions: Vec<Transition>,
    pub arcs: Vec<Arc>,
    pub initial_marking: HashMap<String, u32>,
    pub final_markings: Vec<HashMap<String, u32>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct Place {
    pub id: String,
    pub label: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct Transition {
    pub id: String,
    pub label: String,
    pub is_invisible: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Arc {
    pub source: String,        // place or transition id
    pub target: String,        // place or transition id
    pub multiplicity: u32,
}

/// Token-based replay result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReplayResult {
    pub fitness: f64,
    pub produced: u64,
    pub consumed: u64,
    pub missing: u64,
    pub remaining: u64,
    pub deviating_traces: Vec<String>,
}

/// DFG (Directly-Follows Graph) node
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DfgNode {
    pub id: String,
    pub label: String,
    pub count: u64,
}

/// DFG edge
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DfgEdge {
    pub source: String,
    pub target: String,
    pub count: u64,
}

/// DFG result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DfgResult {
    pub nodes: Vec<DfgNode>,
    pub edges: Vec<DfgEdge>,
    pub start_activities: HashMap<String, u64>,
    pub end_activities: HashMap<String, u64>,
}

/// Performance statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceStats {
    pub trace_count: usize,
    pub total_events: usize,
    pub avg_trace_length: f64,
    pub min_trace_length: usize,
    pub max_trace_length: usize,
    pub avg_flow_time_ms: f64,
    pub throughput_per_hour: f64,
    pub activity_stats: HashMap<String, ActivityStats>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivityStats {
    pub count: usize,
    pub avg_duration_ms: f64,
    pub min_duration_ms: f64,
    pub max_duration_ms: f64,
}

// Statically compiled regex patterns — compiled once per process via OnceLock.
// (?s) enables dotall mode so `.` matches newlines (required for multi-line XES/PNML).
fn xes_trace_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"(?s)<trace>(.*?)</trace>"#).unwrap())
}
fn xes_event_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"(?s)<event>(.*?)</event>"#).unwrap())
}
fn xes_attr_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"<string key="([^"]+)" value="([^"]*)"/>"#).unwrap())
}
fn xes_date_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"<date key="([^"]+)" value="([^"]*)"/>"#).unwrap())
}
fn pnml_place_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"(?s)<place id="([^"]+)".*?<name>.*?<text>([^<]*)</text>"#).unwrap())
}
fn pnml_trans_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"(?s)<transition id="([^"]+)".*?<name>.*?<text>([^<]*)</text>"#).unwrap())
}
fn pnml_arc_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"<arc.*?source="([^"]+)".*?target="([^"]+)""#).unwrap())
}
fn pnml_initial_pattern() -> &'static Regex {
    static PAT: OnceLock<Regex> = OnceLock::new();
    PAT.get_or_init(|| Regex::new(r#"<initialMarking><text>([^<]*)</text>"#).unwrap())
}

/// Parse XES format (simplified XML-based event log)
pub fn parse_xes(xes_str: &str) -> Result<EventLog, String> {
    let trace_pattern = xes_trace_pattern();
    let event_pattern = xes_event_pattern();
    let attr_pattern = xes_attr_pattern();
    let date_pattern = xes_date_pattern();

    let mut traces = Vec::new();

    for trace_cap in trace_pattern.captures_iter(xes_str) {
        let trace_content = &trace_cap[1];
        let mut case_id = String::from("case-unknown");
        let mut events = Vec::new();
        let mut trace_attrs = HashMap::new();

        // Extract case ID and trace attributes
        for attr_cap in attr_pattern.captures_iter(trace_content) {
            let key = &attr_cap[1];
            let value = &attr_cap[2];
            if key == "concept:name" {
                case_id = value.to_string();
            }
            trace_attrs.insert(key.to_string(), value.to_string());
        }

        // Extract events from trace
        for event_cap in event_pattern.captures_iter(trace_content) {
            let event_content = &event_cap[1];
            let mut activity = String::from("unknown");
            let mut timestamp = None;
            let mut event_attrs = HashMap::new();

            // Parse event attributes
            for attr_cap in attr_pattern.captures_iter(event_content) {
                let key = &attr_cap[1];
                let value = &attr_cap[2];
                if key == "concept:name" {
                    activity = value.to_string();
                }
                event_attrs.insert(key.to_string(), value.to_string());
            }

            // Parse timestamp
            for date_cap in date_pattern.captures_iter(event_content) {
                let key = &date_cap[1];
                let value = &date_cap[2];
                if key == "time:timestamp" {
                    if let Ok(ts) = DateTime::parse_from_rfc3339(value) {
                        timestamp = Some(ts.with_timezone(&Utc));
                    }
                }
            }

            events.push(Event {
                activity,
                timestamp,
                attributes: event_attrs,
            });
        }

        traces.push(Trace {
            case_id,
            events,
            attributes: trace_attrs,
        });
    }

    if traces.is_empty() {
        return Err("No traces found in XES log".to_string());
    }

    Ok(EventLog { traces })
}

/// Parse PNML format (simplified Petri net in XML)
pub fn parse_pnml(pnml_str: &str) -> Result<PetriNet, String> {
    let place_pattern = pnml_place_pattern();
    let trans_pattern = pnml_trans_pattern();
    let arc_pattern = pnml_arc_pattern();
    let initial_pattern = pnml_initial_pattern();

    let mut places = Vec::new();
    let mut transitions = Vec::new();
    let mut arcs = Vec::new();
    let mut initial_marking = HashMap::new();
    let final_markings = vec![HashMap::new()];

    // Parse places
    for cap in place_pattern.captures_iter(pnml_str) {
        let id = &cap[1];
        let label = &cap[2];
        places.push(Place {
            id: id.to_string(),
            label: label.to_string(),
        });
        initial_marking.insert(id.to_string(), 0);
    }

    // Parse transitions
    for cap in trans_pattern.captures_iter(pnml_str) {
        let id = &cap[1];
        let label = &cap[2];
        transitions.push(Transition {
            id: id.to_string(),
            label: label.to_string(),
            is_invisible: label.is_empty() || label == "tau",
        });
    }

    // Parse arcs
    for cap in arc_pattern.captures_iter(pnml_str) {
        let source = &cap[1];
        let target = &cap[2];
        arcs.push(Arc {
            source: source.to_string(),
            target: target.to_string(),
            multiplicity: 1,
        });
    }

    // Parse initial marking
    for cap in initial_pattern.captures_iter(pnml_str) {
        let marking_str = &cap[1];
        if !marking_str.is_empty() {
            for place_id in marking_str.split(',') {
                let id = place_id.trim();
                *initial_marking.entry(id.to_string()).or_insert(0) += 1;
            }
        }
    }

    if transitions.is_empty() {
        return Err("No transitions found in PNML".to_string());
    }

    Ok(PetriNet {
        places,
        transitions,
        arcs,
        initial_marking,
        final_markings,
    })
}

/// Token-based replay conformance checking
pub fn token_based_replay(net: &PetriNet, log: &EventLog) -> Result<ReplayResult, String> {
    let mut total_produced = 0u64;
    let mut total_consumed = 0u64;
    let mut total_missing = 0u64;
    let mut total_remaining = 0u64;
    let mut deviating_traces = Vec::new();

    // Replay each trace through the net
    for trace in &log.traces {
        let (produced, consumed, missing, remaining) =
            replay_trace(net, trace).map_err(|e| format!("Replay error for {}: {}", trace.case_id, e))?;

        total_produced += produced;
        total_consumed += consumed;
        total_missing += missing;
        total_remaining += remaining;

        if missing > 0 || remaining > 0 {
            deviating_traces.push(trace.case_id.clone());
        }
    }

    // Compute fitness metric (4-counter metric)
    let fitness = if total_produced + total_consumed > 0 {
        let produced_fitness = if total_produced > 0 {
            1.0 - (total_missing as f64 / total_produced as f64)
        } else {
            1.0
        };
        let consumed_fitness = if total_consumed > 0 {
            1.0 - (total_remaining as f64 / total_consumed as f64)
        } else {
            1.0
        };
        (produced_fitness + consumed_fitness) / 2.0
    } else {
        0.0
    };

    Ok(ReplayResult {
        fitness: fitness.max(0.0).min(1.0),
        produced: total_produced,
        consumed: total_consumed,
        missing: total_missing,
        remaining: total_remaining,
        deviating_traces,
    })
}

/// Replay a single trace through the Petri net
fn replay_trace(net: &PetriNet, trace: &Trace) -> Result<(u64, u64, u64, u64), String> {
    let mut marking = net.initial_marking.clone();
    let mut produced = 0u64;
    let mut consumed = 0u64;
    let mut missing = 0u64;
    let mut remaining = 0u64;

    // For each event in the trace
    for event in &trace.events {
        // Find transitions matching this activity
        let matching_trans: Vec<_> = net
            .transitions
            .iter()
            .filter(|t| t.label == event.activity && !t.is_invisible)
            .collect();

        if matching_trans.is_empty() {
            // Activity not in net
            missing += 1;
            continue;
        }

        let trans = matching_trans[0];

        // Consume tokens from input places
        for arc in &net.arcs {
            if arc.target == trans.id {
                let place_id = &arc.source;
                let available = marking.get(place_id).copied().unwrap_or(0);
                if available >= arc.multiplicity {
                    consumed += arc.multiplicity as u64;
                    *marking.entry(place_id.clone()).or_insert(0) -= arc.multiplicity;
                } else {
                    remaining += arc.multiplicity as u64 - available as u64;
                }
            }
        }

        // Produce tokens to output places
        for arc in &net.arcs {
            if arc.source == trans.id {
                let place_id = &arc.target;
                produced += arc.multiplicity as u64;
                *marking.entry(place_id.clone()).or_insert(0) += arc.multiplicity;
            }
        }
    }

    Ok((produced, consumed, missing, remaining))
}

/// Discover a Directly-Follows Graph from the event log
pub fn discover_dfg(log: &EventLog) -> DfgResult {
    let mut df_relations: HashMap<(String, String), u64> = HashMap::new();
    let mut activity_counts: HashMap<String, u64> = HashMap::new();
    let mut start_activities: HashMap<String, u64> = HashMap::new();
    let mut end_activities: HashMap<String, u64> = HashMap::new();

    for trace in &log.traces {
        if trace.events.is_empty() {
            continue;
        }

        // Count start activity
        *start_activities
            .entry(trace.events[0].activity.clone())
            .or_insert(0) += 1;

        // Count end activity
        *end_activities
            .entry(trace.events[trace.events.len() - 1].activity.clone())
            .or_insert(0) += 1;

        // Count activities and directly-follows relations
        for event in &trace.events {
            *activity_counts
                .entry(event.activity.clone())
                .or_insert(0) += 1;
        }

        for i in 0..trace.events.len() - 1 {
            let source = &trace.events[i].activity;
            let target = &trace.events[i + 1].activity;
            *df_relations
                .entry((source.clone(), target.clone()))
                .or_insert(0) += 1;
        }
    }

    let nodes = activity_counts
        .into_iter()
        .map(|(activity, count)| DfgNode {
            id: activity.clone(),
            label: activity,
            count,
        })
        .collect();

    let edges = df_relations
        .into_iter()
        .map(|((source, target), count)| DfgEdge {
            source,
            target,
            count,
        })
        .collect();

    DfgResult {
        nodes,
        edges,
        start_activities,
        end_activities,
    }
}

/// Alpha++ Petri net discovery (simplified)
pub fn discover_alpha_ppp(log: &EventLog) -> Result<PetriNet, String> {
    // Simplified Alpha++ implementation
    // Real implementation would use causality, concurrency analysis
    let dfg = discover_dfg(log);

    let mut places = Vec::new();
    let mut transitions = Vec::new();
    let mut arcs = Vec::new();

    // Create a transition for each unique activity
    let mut activity_set = HashSet::new();
    for node in &dfg.nodes {
        if !activity_set.contains(&node.id) {
            activity_set.insert(node.id.clone());
            transitions.push(Transition {
                id: format!("t_{}", node.id),
                label: node.id.clone(),
                is_invisible: false,
            });
        }
    }

    // Create source and sink places
    places.push(Place {
        id: "p_source".to_string(),
        label: "source".to_string(),
    });
    places.push(Place {
        id: "p_sink".to_string(),
        label: "sink".to_string(),
    });

    // Connect source to start activities
    for (start_activity, _) in &dfg.start_activities {
        arcs.push(Arc {
            source: "p_source".to_string(),
            target: format!("t_{}", start_activity),
            multiplicity: 1,
        });
    }

    // Connect end activities to sink
    for (end_activity, _) in &dfg.end_activities {
        arcs.push(Arc {
            source: format!("t_{}", end_activity),
            target: "p_sink".to_string(),
            multiplicity: 1,
        });
    }

    // Create intermediate places and arcs from edges
    for (i, edge) in dfg.edges.iter().enumerate() {
        let place_id = format!("p_{}", i);
        places.push(Place {
            id: place_id.clone(),
            label: format!("{}->{}", edge.source, edge.target),
        });

        arcs.push(Arc {
            source: format!("t_{}", edge.source),
            target: place_id.clone(),
            multiplicity: 1,
        });

        arcs.push(Arc {
            source: place_id,
            target: format!("t_{}", edge.target),
            multiplicity: 1,
        });
    }

    let mut initial_marking = HashMap::new();
    initial_marking.insert("p_source".to_string(), 1);

    let mut final_marking = HashMap::new();
    final_marking.insert("p_sink".to_string(), 1);

    Ok(PetriNet {
        places,
        transitions,
        arcs,
        initial_marking,
        final_markings: vec![final_marking],
    })
}

/// Export Petri net to PNML format
pub fn export_pnml(net: &PetriNet) -> String {
    let mut pnml = String::from(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<pnml xmlns="http://www.pnml.org/version-2009-05-13/grammar/pnml">
  <net id="net" type="http://www.pnml.org/version-2009-05-13/grammar/pnmlcoremodel">
"#,
    );

    // Add places
    for place in &net.places {
        pnml.push_str(&format!(
            r#"    <place id="{}">
      <name>
        <text>{}</text>
      </name>
"#,
            place.id, place.label
        ));

        // Add initial marking if present
        if let Some(tokens) = net.initial_marking.get(&place.id) {
            if *tokens > 0 {
                pnml.push_str(&format!("      <initialMarking><text>{}</text></initialMarking>\n", tokens));
            }
        }

        pnml.push_str("    </place>\n");
    }

    // Add transitions
    for trans in &net.transitions {
        pnml.push_str(&format!(
            r#"    <transition id="{}">
      <name>
        <text>{}</text>
      </name>
    </transition>
"#,
            trans.id, trans.label
        ));
    }

    // Add arcs
    for arc in &net.arcs {
        pnml.push_str(&format!(
            r#"    <arc id="arc_{}_to_{}" source="{}" target="{}">
      <inscription><text>{}</text></inscription>
    </arc>
"#,
            arc.source, arc.target, arc.source, arc.target, arc.multiplicity
        ));
    }

    pnml.push_str("  </net>\n</pnml>\n");
    pnml
}

/// Compute performance statistics from event log
pub fn compute_performance_stats(log: &EventLog) -> PerformanceStats {
    // activity_counts: occurrence count per activity name
    let mut activity_counts: HashMap<String, usize> = HashMap::new();
    // activity_durations: inter-event waiting times attributed to each activity.
    // Duration for activity[i] = timestamp[i+1] - timestamp[i] within the same trace.
    // This gives the sojourn time approximation when only single lifecycle events exist.
    let mut activity_durations: HashMap<String, Vec<i64>> = HashMap::new();
    let mut total_flow_times = Vec::new();

    for trace in &log.traces {
        if trace.events.is_empty() {
            continue;
        }

        // Count activity occurrences
        for event in &trace.events {
            *activity_counts.entry(event.activity.clone()).or_insert(0) += 1;
        }

        // Compute sojourn time per activity: time from this event to the next event in the trace.
        for i in 0..trace.events.len().saturating_sub(1) {
            let curr = &trace.events[i];
            let next = &trace.events[i + 1];
            if let (Some(curr_ts), Some(next_ts)) = (curr.timestamp, next.timestamp) {
                let dur_ms = (next_ts - curr_ts).num_milliseconds();
                if dur_ms >= 0 {
                    activity_durations
                        .entry(curr.activity.clone())
                        .or_insert_with(Vec::new)
                        .push(dur_ms);
                }
            }
        }

        // Compute trace flow time (first to last event)
        let first_time = trace.events[0].timestamp;
        let last_time = trace.events[trace.events.len() - 1].timestamp;

        if let (Some(start), Some(end)) = (first_time, last_time) {
            let duration_ms = (end - start).num_milliseconds();
            if duration_ms >= 0 {
                total_flow_times.push(duration_ms as f64);
            }
        }
    }

    // Compute activity-level statistics
    let mut activity_stats = HashMap::new();
    for (activity, count) in &activity_counts {
        let durations = activity_durations.get(activity.as_str());

        let (avg_duration, min_duration, max_duration) = match durations {
            Some(d) if !d.is_empty() => {
                let sum: i64 = d.iter().sum();
                let avg = sum as f64 / d.len() as f64;
                let min = *d.iter().min().unwrap() as f64;
                let max = *d.iter().max().unwrap() as f64;
                (avg, min, max)
            }
            _ => (0.0, 0.0, 0.0),
        };

        activity_stats.insert(
            activity.clone(),
            ActivityStats {
                count: *count,
                avg_duration_ms: avg_duration,
                min_duration_ms: min_duration,
                max_duration_ms: max_duration,
            },
        );
    }

    let trace_count = log.traces.len();
    let total_events: usize = log.traces.iter().map(|t| t.events.len()).sum();
    let avg_trace_length = if trace_count > 0 {
        total_events as f64 / trace_count as f64
    } else {
        0.0
    };

    let min_trace_length = log.traces.iter().map(|t| t.events.len()).min().unwrap_or(0);
    let max_trace_length = log.traces.iter().map(|t| t.events.len()).max().unwrap_or(0);

    let avg_flow_time_ms = if !total_flow_times.is_empty() {
        let sum: f64 = total_flow_times.iter().sum();
        sum / total_flow_times.len() as f64
    } else {
        0.0
    };

    let throughput_per_hour = if avg_flow_time_ms > 0.0 {
        3_600_000.0 / avg_flow_time_ms
    } else {
        0.0
    };

    PerformanceStats {
        trace_count,
        total_events,
        avg_trace_length,
        min_trace_length,
        max_trace_length,
        avg_flow_time_ms,
        throughput_per_hour,
        activity_stats,
    }
}

/// Convert event log to OCEL 2.0 format
pub fn convert_to_ocel(log: &EventLog) -> serde_json::Value {
    use serde_json::json;

    let mut objects = Vec::new();
    let mut events = Vec::new();

    // Create case objects
    for trace in &log.traces {
        objects.push(json!({
            "ocel:oid": trace.case_id,
            "ocel:type": "case",
            "ocel:ovmap": {}
        }));
    }

    // Create workflow events
    let mut event_counter = 0u64;
    for trace in &log.traces {
        for event in &trace.events {
            let timestamp = event
                .timestamp
                .map(|ts| ts.to_rfc3339())
                .unwrap_or_else(|| "1970-01-01T00:00:00Z".to_string());

            let mut event_obj = json!({
                "ocel:eid": format!("event-{}", event_counter),
                "ocel:activity": event.activity,
                "ocel:timestamp": timestamp,
                "ocel:omap": [trace.case_id.clone()],
                "ocel:vmap": {}
            });

            // Add custom attributes if present
            for (key, value) in &event.attributes {
                if !key.starts_with("concept:") && !key.starts_with("time:") {
                    if let Some(obj) = event_obj.get_mut("ocel:vmap") {
                        if let Some(map) = obj.as_object_mut() {
                            map.insert(key.clone(), json!(value));
                        }
                    }
                }
            }

            events.push(event_obj);
            event_counter += 1;
        }
    }

    json!({
        "ocel:objectTypes": [
            {
                "ocel:name": "case",
                "ocel:attributeNames": []
            }
        ],
        "ocel:eventTypes": [
            {
                "ocel:name": "event",
                "ocel:attributeNames": ["activity", "timestamp"]
            }
        ],
        "ocel:objects": objects,
        "ocel:events": events,
        "ocel:globalLog": {
            "ocel:attributes": [],
            "ocel:version": "2.0"
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_xes_basic() {
        let xes = r#"<?xml version="1.0"?>
<log>
  <trace>
    <string key="concept:name" value="case1"/>
    <event>
      <string key="concept:name" value="A"/>
    </event>
  </trace>
</log>"#;
        let log = parse_xes(xes).expect("Parse failed");
        assert_eq!(log.traces.len(), 1);
        assert_eq!(log.traces[0].events.len(), 1);
    }
}
