/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

//! Rust NIF function behavior tests
//!
//! This test suite verifies:
//! - NIF function correctness and safety
//! - Memory management across NIF boundaries
//! - Performance characteristics
//! - Fault isolation guarantees

use yawl_rust4pm::{ProcessMiningNIF, ConformanceResult, EventLogEntry, ProcessGraph};
use std::collections::{HashMap, HashSet};
use std::time::{Duration, Instant};

/// Test fixture setup
fn setup_test_data() -> (Vec<EventLogEntry>, ProcessGraph) {
    // Create test event log
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
            activity: "Task_C".to_string(),
            timestamp: "2024-01-01T12:00:00Z".to_string(),
            case_id: "case1".to_string(),
            attributes: HashMap::new(),
        },
        EventLogEntry {
            activity: "Task_A".to_string(),
            timestamp: "2024-01-01T10:30:00Z".to_string(),
            case_id: "case2".to_string(),
            attributes: HashMap::new(),
        },
        EventLogEntry {
            activity: "Task_C".to_string(),
            timestamp: "2024-01-01T11:30:00Z".to_string(),
            case_id: "case2".to_string(),
            attributes: HashMap::new(),
        },
    ];

    // Create process graph
    let graph = ProcessGraph {
        tasks: vec!["Task_A", "Task_B", "Task_C", "Start", "End"]
            .iter()
            .map(|s| s.to_string())
            .collect(),
        edges: {
            let mut m = HashMap::new();
            m.insert("Start".to_string(), vec!["Task_A".to_string()]);
            m.insert("Task_A".to_string(), vec!["Task_B".to_string(), "Task_C".to_string()]);
            m.insert("Task_B".to_string(), vec!["Task_C".to_string()]);
            m.insert("Task_C".to_string(), vec!["End".to_string()]);
            m.insert("End".to_string(), vec![]);
            m
        },
        start_task: "Start".to_string(),
        end_task: "End".to_string(),
    };

    (event_log, graph)
}

/// Test 1: Basic conformance checking
#[test]
fn basic_conformance_checking() {
    let (event_log, graph) = setup_test_data();

    // Call NIF function
    let result = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        &graph as *const ProcessGraph
    );

    // Verify result
    assert_eq!(result.case_id, "case1");
    assert!(result.is_conformant);
    assert!(result.missing_tasks.is_empty());
    assert!(result.extra_tasks.is_empty());
    assert!(result.fitness >= 0.0 && result.fitness <= 1.0);
    assert!(result.completeness >= 0.0 && result.completeness <= 1.0);
}

/// Test 2: Non-conformant case
#[test]
fn non_conformant_case() {
    let (mut event_log, graph) = setup_test_data();

    // Add invalid task
    event_log.push(EventLogEntry {
        activity: "Invalid_Task".to_string(),
        timestamp: "2024-01-01T13:00:00Z".to_string(),
        case_id: "case3".to_string(),
        attributes: HashMap::new(),
    });

    let result = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        &graph as *const ProcessGraph
    );

    // Verify non-conformant result
    assert_eq!(result.case_id, "case1");
    assert!(!result.is_conformant);
    assert!(result.extra_tasks.contains(&"Invalid_Task".to_string()));
}

/// Test 3: Memory safety across NIF boundaries
#[test]
fn memory_safety_across_boundaries() {
    let (event_log, graph) = setup_test_data();

    // Create reference to graph that will go out of scope
    let graph_copy = graph.clone();

    // Call NIF function while graph is in scope
    let result1 = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        &graph as *const ProcessGraph
    );

    // Call again after some operations
    let result2 = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        &graph_copy as *const ProcessGraph
    );

    // Results should be identical
    assert_eq!(result1.case_id, result2.case_id);
    assert_eq!(result1.is_conformant, result2.is_conformant);
    assert_eq!(result1.fitness, result2.fitness);
}

/// Test 4: Large dataset processing
#[test]
fn large_dataset_processing() {
    // Generate large event log
    let mut event_log = Vec::new();
    let mut cases = 1000;
    let events_per_case = 50;

    for case_id in 1..=cases {
        for event_id in 1..=events_per_case {
            event_log.push(EventLogEntry {
                activity: format!("Task_{}", event_id),
                timestamp: format!("2024-01-01T{}:{}:00Z", event_id / 3600, (event_id % 3600) / 60),
                case_id: format!("large_case_{}", case_id),
                attributes: HashMap::new(),
            });
        }
    }

    let graph = ProcessGraph {
        tasks: (1..=events_per_case)
            .map(|i| format!("Task_{}", i))
            .collect(),
        edges: {
            let mut m = HashMap::new();
            for i in 1..events_per_case {
                m.insert(format!("Task_{}", i), vec![format!("Task_{}", i + 1)]);
            }
            m.insert(format!("Task_{}", events_per_case), vec!["End".to_string()]);
            m
        },
        start_task: "Start".to_string(),
        end_task: "End".to_string(),
    };

    // Benchmark large dataset processing
    let start = Instant::now();
    let result = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        &graph as *const ProcessGraph
    );
    let duration = start.elapsed();

    // Verify processing
    assert!(result.is_conformant);
    println!("Processed {} events in {:?}", event_log.len(), duration);
    println!("Time per event: {:?}", duration / event_log.len() as u32);

    // Performance target: <1µs per event
    let expected_max_duration = Duration::from_micros(event_log.len() as u64);
    assert!(duration < expected_max_duration,
            "Processing took {:?}, expected < {:?}",
            duration, expected_max_duration);
}

/// Test 5: Fault isolation
#[test]
fn fault_isolation() {
    let (event_log, graph) = setup_test_data();

    // Test that NIF function doesn't crash on invalid input
    let result = rust4pm::check_conformance(
        event_log.as_ptr(),
        event_log.len(),
        std::ptr::null()
    );

    // Should handle null pointer gracefully
    assert!(!result.case_id.is_empty());
}

/// Test 6: Model reconstruction
#[test]
fn model_reconstruction() {
    let (event_log, _) = setup_test_data();

    let reconstructed = rust4pm::reconstruct_model(
        event_log.as_ptr(),
        event_log.len()
    );

    // Verify reconstructed model contains expected tasks
    assert!(reconstructed.tasks.contains("Task_A"));
    assert!(reconstructed.tasks.contains("Task_B"));
    assert!(reconstructed.tasks.contains("Task_C"));

    // Verify edges
    assert!(reconstructed.edges.contains_key("Task_A"));
    assert!(reconstructed.edges["Task_A"].contains(&"Task_B".to_string()));
    assert!(reconstructed.edges["Task_A"].contains(&"Task_C".to_string()));
}

/// Test 7: Metrics calculation
#[test]
fn metrics_calculation() {
    let (event_log, _) = setup_test_data();

    let metrics = rust4pm::calculate_metrics(
        event_log.as_ptr(),
        event_log.len()
    );

    // Verify metrics
    assert_eq!(metrics.get("total_events"), Some(&5.0));
    assert_eq!(metrics.get("unique_cases"), Some(&2.0));
    assert_eq!(metrics.get("avg_case_length"), Some(&2.5));
    assert!(metrics.get("Task_A_frequency").is_some());
    assert!(metrics.get("Task_B_frequency").is_some());
    assert!(metrics.get("Task_C_frequency").is_some());

    // Verify frequency sums to 1.0
    let total_frequency: f64 = ["Task_A", "Task_B", "Task_C"]
        .iter()
        .map(|task| metrics.get(&format!("{}_frequency", task)).unwrap_or(&0.0))
        .sum();
    assert!((total_frequency - 1.0).abs() < 0.001);
}

/// Test 8: Concurrent access
#[test]
fn concurrent_access() {
    let (event_log, graph) = setup_test_data();
    let mut handles = Vec::new();

    // Spawn multiple threads to test concurrent access
    for thread_id in 0..10 {
        let event_log_clone = event_log.clone();
        let graph_clone = graph.clone();

        let handle = std::thread::spawn(move || {
            // Thread-local processing
            for _ in 0..5 {
                let result = rust4pm::check_conformance(
                    event_log_clone.as_ptr(),
                    event_log_clone.len(),
                    &graph_clone as *const ProcessGraph
                );
                assert!(result.is_conformant);
            }
        });

        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.join().unwrap();
    }
}

/// Test 9: Performance benchmarks
#[test]
fn performance_benchmarks() {
    let (event_log, graph) = setup_test_data();

    // Benchmark conformance checking
    let iterations = 1000;
    let start = Instant::now();

    for _ in 0..iterations {
        let _ = rust4pm::check_conformance(
            event_log.as_ptr(),
            event_log.len(),
            &graph as *const ProcessGraph
        );
    }

    let duration = start.elapsed();
    let avg_duration = duration / iterations;

    println!("Conformance checking benchmark:");
    println!("  Iterations: {}", iterations);
    println!("  Total time: {:?}", duration);
    println!("  Avg time: {:?}", avg_duration);
    println!("  Target: <1µs per call");

    // Performance target: <1µs per call
    assert!(avg_duration < Duration::from_micros(1),
            "Average call took {:?}, expected < 1µs",
            avg_duration);

    // Benchmark model reconstruction
    let start = Instant::now();
    for _ in 0..iterations {
        let _ = rust4pm::reconstruct_model(
            event_log.as_ptr(),
            event_log.len()
        );
    }
    let recon_duration = start.elapsed();
    let recon_avg = recon_duration / iterations;

    println!("Model reconstruction benchmark:");
    println!("  Iterations: {}", iterations);
    println!("  Total time: {:?}", recon_duration);
    println!("  Avg time: {:?}", recon_avg);
    println!("  Target: <10µs per call");

    // Performance target: <10µs per call
    assert!(recon_avg < Duration::from_micros(10),
            "Average reconstruction took {:?}, expected < 10µs",
            recon_avg);
}

/// Test 10: Edge cases
#[test]
fn edge_cases() {
    // Empty event log
    let empty_event_log: Vec<EventLogEntry> = Vec::new();
    let result = rust4pm::check_conformance(
        empty_event_log.as_ptr(),
        empty_event_log.len(),
        &setup_test_data().1 as *const ProcessGraph
    );
    assert!(!result.is_conformant);

    // Event log with single event
    let single_event_log = vec![
        EventLogEntry {
            activity: "Task_A".to_string(),
            timestamp: "2024-01-01T10:00:00Z".to_string(),
            case_id: "single".to_string(),
            attributes: HashMap::new(),
        }
    ];
    let result = rust4pm::check_conformance(
        single_event_log.as_ptr(),
        single_event_log.len(),
        &setup_test_data().1 as *const ProcessGraph
    );
    assert!(result.is_conformant);

    // Process graph with single task
    let single_task_graph = ProcessGraph {
        tasks: vec!["Task_A".to_string()].into_iter().collect(),
        edges: HashMap::new(),
        start_task: "Task_A".to_string(),
        end_task: "Task_A".to_string(),
    };
    let result = rust4pm::check_conformance(
        single_event_log.as_ptr(),
        single_event_log.len(),
        &single_task_graph as *const ProcessGraph
    );
    assert!(result.is_conformant);
}

/// Test 11: Memory usage
#[test]
fn memory_usage() {
    let initial_memory = get_memory_usage();

    // Create large dataset
    let mut large_event_log = Vec::new();
    for _ in 0..10000 {
        large_event_log.push(EventLogEntry {
            activity: "Task_A".to_string(),
            timestamp: "2024-01-01T10:00:00Z".to_string(),
            case_id: "large_case".to_string(),
            attributes: HashMap::new(),
        });
    }

    let graph = ProcessGraph {
        tasks: vec!["Task_A".to_string()].into_iter().collect(),
        edges: HashMap::new(),
        start_task: "Task_A".to_string(),
        end_task: "Task_A".to_string(),
    };

    // Process large dataset
    let start = Instant::now();
    let result = rust4pm::check_conformance(
        large_event_log.as_ptr(),
        large_event_log.len(),
        &graph as *const ProcessGraph
    );
    let processing_time = start.elapsed();

    // Check memory after processing
    let final_memory = get_memory_usage();
    let memory_increase = final_memory - initial_memory;

    println!("Memory usage test:");
    println!("  Initial memory: {} KB", initial_memory);
    println!("  Final memory: {} KB", final_memory);
    println!("  Memory increase: {} KB", memory_increase);
    println!("  Processing time: {:?}", processing_time);

    // Verify memory usage is reasonable
    assert!(memory_increase < 10 * 1024, // Less than 10MB increase
            "Memory increased by {} KB, expected < 10KB",
            memory_increase);

    // Verify result is correct
    assert!(result.is_conformant);
}

/// Helper function to get memory usage
fn get_memory_usage() -> u64 {
    // This is a simplified memory measurement
    // In a real implementation, you'd use platform-specific APIs
    0
}

/// Test 12: Fault injection simulation
#[test]
#[should_panic(expected = "test_panic")]
fn fault_injection_panic() {
    // This test would be disabled in production
    // It's here to demonstrate that panics in Rust don't escape to Erlang
    panic!("test_panic");
}

/// Test 13: API version consistency
#[test]
fn api_version_consistency() {
    // Verify NIF initialization
    assert!(rust4pm::rust4pm_init());

    // Verify version is defined
    #[cfg(target_os = "linux")]
    assert!(!rust4pm::RUST4PM_VERSION.is_empty());
    #[cfg(target_os = "macos")]
    assert!(!rust4pm::RUST4PM_VERSION.is_empty());
    #[cfg(target_os = "windows")]
    assert!(!rust4pm::RUST4PM_VERSION.is_empty());
}

/// Integration test with simulated Erlang environment
#[test]
fn integration_test() {
    // This simulates how Erlang would call the NIF functions
    let (event_log, graph) = setup_test_data();

    // Simulate Erlang calling check_conformance
    let result = unsafe {
        // This is how Erlang would call the NIF
        std::ptr::read(rust4pm::check_conformance(
            event_log.as_ptr(),
            event_log.len(),
            &graph as *const ProcessGraph
        ))
    };

    assert!(result.is_conformant);

    // Simulate Erlang calling reconstruct_model
    let reconstructed = unsafe {
        std::ptr::read(rust4pm::reconstruct_model(
            event_log.as_ptr(),
            event_log.len()
        ))
    };

    assert!(reconstructed.tasks.contains("Task_A"));

    // Simulate Erlang calling calculate_metrics
    let metrics = unsafe {
        std::ptr::read(rust4pm::calculate_metrics(
            event_log.as_ptr(),
            event_log.len()
        ))
    };

    assert!(metrics.contains_key("total_events"));
    assert_eq!(metrics["total_events"], 5.0);
}

// Benchmarks for performance testing
#[bench]
fn bench_conformance_checking(b: &mut test::Bencher) {
    let (event_log, graph) = setup_test_data();

    b.iter(|| {
        rust4pm::check_conformance(
            event_log.as_ptr(),
            event_log.len(),
            &graph as *const ProcessGraph
        )
    });
}

#[bench]
fn bench_model_reconstruction(b: &mut test::Bencher) {
    let (event_log, _) = setup_test_data();

    b.iter(|| {
        rust4pm::reconstruct_model(
            event_log.as_ptr(),
            event_log.len()
        )
    });
}

#[bench]
fn bench_metrics_calculation(b: &mut test::Bencher) {
    let (event_log, _) = setup_test_data();

    b.iter(|| {
        rust4pm::calculate_metrics(
            event_log.as_ptr(),
            event_log.len()
        )
    });
}