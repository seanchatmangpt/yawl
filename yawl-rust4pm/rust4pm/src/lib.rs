/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

//! Rust NIF library for process mining - provides high-performance
//! conformance checking and trace analysis functions.

use std::collections::{HashMap, HashSet};
use std::time::{Duration, Instant};

/// Process mining NIF API - exposed to Erlang
#[derive(Debug, Clone)]
pub struct ProcessMiningNIF {
    cache: HashMap<String, ProcessGraph>,
}

/// Process graph structure for conformance checking
#[derive(Debug, Clone)]
pub struct ProcessGraph {
    tasks: HashSet<String>,
    edges: HashMap<String, Vec<String>>,
    start_task: String,
    end_task: String,
}

/// Event log entry
#[derive(Debug, Clone)]
pub struct EventLogEntry {
    pub activity: String,
    pub timestamp: String,
    pub case_id: String,
    pub attributes: HashMap<String, String>,
}

/// Conformance result
#[derive(Debug)]
pub struct ConformanceResult {
    pub case_id: String,
    pub is_conformant: bool,
    pub missing_tasks: Vec<String>,
    pub extra_tasks: Vec<String>,
    pub fitness: f64,
    pub completeness: f64,
}

#[cfg(target_os = "linux")]
pub const RUST4PM_VERSION: &str = "1.0.0";
#[cfg(target_os = "macos")]
pub const RUST4PM_VERSION: &str = "1.0.0";
#[cfg(target_os = "windows")]
pub const RUST4PM_VERSION: &str = "1.0.0";

/// Initialize NIF module
#[no_mangle]
pub extern "C" fn rust4pm_init() -> bool {
    true
}

/// Check process conformance
#[no_mangle]
pub extern "C" fn check_conformance(
    event_log_ptr: *const EventLogEntry,
    event_log_len: usize,
    graph_ptr: *const ProcessGraph,
) -> ConformanceResult {
    let event_log = unsafe {
        std::slice::from_raw_parts(event_log_ptr, event_log_len)
    };

    let graph = unsafe {
        &*graph_ptr
    };

    check_conformance_rust(event_log, graph)
}

/// Create process graph from specification
#[no_mangle]
pub extern "C" fn create_process_graph(
    tasks_ptr: *const *const u8,
    tasks_len: usize,
    edges_ptr: *const *const u8,
    edges_len: usize,
    start_task_ptr: *const u8,
    end_task_ptr: *const u8,
) -> ProcessGraph {
    let tasks = unsafe {
        let slice = std::slice::from_raw_parts(tasks_ptr, tasks_len);
        slice.iter()
            .map(|&ptr| std::ffi::CStr::from_ptr(ptr).to_string_lossy().into_owned())
            .collect()
    };

    let edges = unsafe {
        let slice = std::slice::from_raw_parts(edges_ptr, edges_len);
        let mut edge_map: HashMap<String, Vec<String>> = HashMap::new();

        for ptr in slice {
            let edge_str = std::ffi::CStr::from_ptr(ptr).to_string_lossy();
            let parts: Vec<&str> = edge_str.split(',').collect();
            if parts.len() == 2 {
                let from = parts[0].to_string();
                let to = parts[1].to_string();
                edge_map.entry(from.clone()).or_insert_with(Vec::new).push(to);
            }
        }

        edge_map
    };

    let start_task = unsafe {
        std::ffi::CStr::from_ptr(start_task_ptr).to_string_lossy().into_owned()
    };

    let end_task = unsafe {
        std::ffi::CStr::from_ptr(end_task_ptr).to_string_lossy().into_owned()
    };

    ProcessGraph {
        tasks: tasks.into_iter().collect(),
        edges,
        start_task,
        end_task,
    }
}

/// Reconstruct process model from event log
#[no_mangle]
pub extern "C" fn reconstruct_model(
    event_log_ptr: *const EventLogEntry,
    event_log_len: usize,
) -> ProcessGraph {
    let event_log = unsafe {
        std::slice::from_raw_parts(event_log_ptr, event_log_len)
    };

    reconstruct_model_rust(event_log)
}

/// Calculate process metrics
#[no_mangle]
pub extern "C" fn calculate_metrics(
    event_log_ptr: *const EventLogEntry,
    event_log_len: usize,
) -> HashMap<String, f64> {
    let event_log = unsafe {
        std::slice::from_raw_parts(event_log_ptr, event_log_len)
    };

    calculate_metrics_rust(event_log)
}

/// Clean up memory
#[no_mangle]
pub extern "C" fn cleanup() {
    // Rust's memory management handles cleanup automatically
}

/// Rust implementation of conformance checking
fn check_conformance_rust(
    event_log: &[EventLogEntry],
    graph: &ProcessGraph,
) -> ConformanceResult {
    let case_id = if let Some(first_event) = event_log.first() {
        first_event.case_id.clone()
    } else {
        "empty_case".to_string()
    };

    // Extract tasks from event log
    let log_tasks: HashSet<String> = event_log
        .iter()
        .map(|e| e.activity.clone())
        .collect();

    // Find missing and extra tasks
    let missing_tasks: Vec<String> = graph.tasks
        .difference(&log_tasks)
        .cloned()
        .collect();

    let extra_tasks: Vec<String> = log_tasks
        .difference(&graph.tasks)
        .cloned()
        .collect();

    // Calculate fitness (how well the log fits the model)
    let fitness = calculate_fitness(event_log, graph);

    // Calculate completeness (how well the model fits the log)
    let completeness = calculate_completeness(event_log, graph);

    ConformanceResult {
        case_id,
        is_conformant: missing_tasks.is_empty() && extra_tasks.is_empty(),
        missing_tasks,
        extra_tasks,
        fitness,
        completeness,
    }
}

/// Rust implementation of model reconstruction
fn reconstruct_model_rust(event_log: &[EventLogEntry]) -> ProcessGraph {
    // Get unique tasks
    let tasks: HashSet<String> = event_log
        .iter()
        .map(|e| e.activity.clone())
        .collect();

    // Reconstruct edges from consecutive tasks
    let mut edges: HashMap<String, Vec<String>> = HashMap::new();
    let mut case_tasks: HashMap<String, Vec<String>> = HashMap::new();

    // Group tasks by case
    for event in event_log {
        case_tasks
            .entry(event.case_id.clone())
            .or_insert_with(Vec::new)
            .push(event.activity.clone());
    }

    // Build edges from sequences
    for tasks_in_case in case_tasks.values() {
        for i in 0..tasks_in_case.len() - 1 {
            let from = tasks_in_case[i].clone();
            let to = tasks_in_case[i + 1].clone();
            edges.entry(from).or_insert_with(Vec::new).push(to);
        }
    }

    // Determine start and end tasks
    let start_task = tasks_in_cases
        .first()
        .and_then(|t| t.first())
        .cloned()
        .unwrap_or_else(|| "Start".to_string());

    let end_task = tasks_in_cases
        .iter()
        .filter_map(|t| t.last())
        .next()
        .cloned()
        .unwrap_or_else(|| "End".to_string());

    ProcessGraph {
        tasks,
        edges,
        start_task,
        end_task,
    }
}

/// Calculate fitness metric
fn calculate_fitness(event_log: &[EventLogEntry], graph: &ProcessGraph) -> f64 {
    let mut valid_transitions = 0;
    let mut total_transitions = 0;

    // Group events by case
    let mut case_events: HashMap<String, Vec<EventLogEntry>> = HashMap::new();
    for event in event_log {
        case_events
            .entry(event.case_id.clone())
            .or_insert_with(Vec::new)
            .push(event.clone());
    }

    // Check transitions within each case
    for events in case_events.values() {
        for i in 0..events.len() - 1 {
            let from = &events[i].activity;
            let to = &events[i + 1].activity;

            if graph.edges.contains_key(from) &&
               graph.edges[from].contains(to) {
                valid_transitions += 1;
            }
            total_transitions += 1;
        }
    }

    if total_transitions == 0 {
        0.0
    } else {
        valid_transitions as f64 / total_transitions as f64
    }
}

/// Calculate completeness metric
fn calculate_completeness(event_log: &[EventLogEntry], graph: &ProcessGraph) -> f64 {
    // Simple completeness calculation
    let log_tasks: HashSet<String> = event_log
        .iter()
        .map(|e| e.activity.clone())
        .collect();

    if graph.tasks.is_empty() {
        0.0
    } else {
        let covered = graph.tasks.intersection(&log_tasks).count();
        covered as f64 / graph.tasks.len() as f64
    }
}

/// Calculate process metrics
fn calculate_metrics_rust(event_log: &[EventLogEntry]) -> HashMap<String, f64> {
    let mut metrics = HashMap::new();

    if event_log.is_empty() {
        return metrics;
    }

    // Basic metrics
    metrics.insert("total_events".to_string(), event_log.len() as f64);
    metrics.insert("unique_cases".to_string(),
        event_log.iter()
            .map(|e| e.case_id.clone())
            .collect::<HashSet<_>>()
            .len() as f64);

    // Calculate average case length
    let case_lengths: HashMap<String, usize> = event_log
        .iter()
        .fold(HashMap::new(), |mut map, event| {
            *map.entry(event.case_id.clone()).or_insert(0) += 1;
            map
        });

    let avg_case_length = case_lengths.values().sum::<usize>() as f64 / case_lengths.len() as f64;
    metrics.insert("avg_case_length".to_string(), avg_case_length);

    // Calculate frequency of each task
    let task_counts: HashMap<String, usize> = event_log
        .iter()
        .map(|e| e.activity.clone())
        .fold(HashMap::new(), |mut map, task| {
            *map.entry(task).or_insert(0) += 1;
            map
        });

    let total = task_counts.values().sum::<usize>() as f64;
    for (task, count) in task_counts {
        metrics.insert(format!("{}_frequency", task), count as f64 / total);
    }

    metrics
}

/// Unit tests (compiled with test feature)
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_conformance_checking() {
        let event_log = vec![
            EventLogEntry {
                activity: "Task_A".to_string(),
                timestamp: "2024-01-01T10:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
            EventLogEntry {
                activity: "Task_B".to_string(),
                timestamp: "2024-01-01T11:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
        ];

        let graph = ProcessGraph {
            tasks: vec!["Task_A".to_string(), "Task_B".to_string()].into_iter().collect(),
            edges: {
                let mut m = HashMap::new();
                m.insert("Task_A".to_string(), vec!["Task_B".to_string()]);
                m
            },
            start_task: "Start".to_string(),
            end_task: "End".to_string(),
        };

        let result = check_conformance_rust(&event_log, &graph);

        assert!(result.is_conformant);
        assert!(result.missing_tasks.is_empty());
        assert!(result.extra_tasks.is_empty());
    }

    #[test]
    fn test_model_reconstruction() {
        let event_log = vec![
            EventLogEntry {
                activity: "Task_A".to_string(),
                timestamp: "2024-01-01T10:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
            EventLogEntry {
                activity: "Task_B".to_string(),
                timestamp: "2024-01-01T11:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
        ];

        let reconstructed = reconstruct_model_rust(&event_log);

        assert!(reconstructed.tasks.contains("Task_A"));
        assert!(reconstructed.tasks.contains("Task_B"));
        assert!(reconstructed.edges.contains_key("Task_A"));
        assert!(reconstructed.edges["Task_A"].contains(&"Task_B".to_string()));
    }

    #[test]
    fn test_metrics_calculation() {
        let event_log = vec![
            EventLogEntry {
                activity: "Task_A".to_string(),
                timestamp: "2024-01-01T10:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
            EventLogEntry {
                activity: "Task_B".to_string(),
                timestamp: "2024-01-01T11:00:00Z".to_string(),
                case_id: "case1".to_string(),
                attributes: HashMap::new(),
            },
            EventLogEntry {
                activity: "Task_A".to_string(),
                timestamp: "2024-01-01T12:00:00Z".to_string(),
                case_id: "case2".to_string(),
                attributes: HashMap::new(),
            },
        ];

        let metrics = calculate_metrics_rust(&event_log);

        assert_eq!(metrics.get("total_events"), Some(&3.0));
        assert_eq!(metrics.get("unique_cases"), Some(&2.0));
        assert_eq!(metrics.get("avg_case_length"), Some(&1.5));
        assert_eq!(metrics.get("Task_A_frequency"), Some(&2.0/3.0));
        assert_eq!(metrics.get("Task_B_frequency"), Some(&1.0/3.0));
    }

    #[test]
    fn test_empty_event_log() {
        let event_log: Vec<EventLogEntry> = vec![];
        let graph = ProcessGraph {
            tasks: HashSet::new(),
            edges: HashMap::new(),
            start_task: "Start".to_string(),
            end_task: "End".to_string(),
        };

        let result = check_conformance_rust(&event_log, &graph);

        assert_eq!(result.case_id, "empty_case");
        assert!(!result.is_conformant);
    }
}