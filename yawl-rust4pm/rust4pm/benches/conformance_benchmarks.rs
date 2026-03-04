/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with YAWL.
 * If not, see <http://www.gnu.org/licenses/>.
 */

use yawl_rust4pm::*;
use criterion::{criterion_group, criterion_main, Criterion, BatchSize};
use std::sync::{Arc, Mutex};

/// Benchmark conformance checking performance
fn bench_conformance_checking(c: &mut Criterion) {
    // Create test OCEL data
    let ocel_handle = OcelLogHandle {
        events: (0..1000)
            .map(|i| OcelEvent {
                activity: format!("Task_{}", i % 10),
                timestamp: format!("2024-01-01T{:02}:{:02}:00Z", i / 60, i % 60),
                case_id: format!("case_{}", i / 100),
                object_id: format!("obj_{}", i),
                attributes: HashMap::new(),
            })
            .collect(),
        objects: (0..100)
            .map(|i| (format!("obj_{}", i), OcelObject {
                object_type: format!("type_{}", i % 5),
                attributes: HashMap::new(),
            }))
            .collect(),
        global_trace: "global_trace".to_string(),
    };

    let ocel_resource = OcelLogResource(Arc::new(Mutex::new(ocel_handle)));
    let ocel = ResourceArc::new(ocel_resource);

    // Benchmark conformance checking
    c.bench_function("conformance_checking", |b| {
        b.iter(|| {
            // This is a placeholder benchmark
            // In a real implementation, this would do actual conformance checking
            let _ = log_event_count(ocel.clone());
            let _ = log_case_count(ocel.clone());
        })
    });
}

/// Benchmark DFG discovery performance
fn bench_dfg_discovery(c: &mut Criterion) {
    // Create test slim OCEL data
    let slim_handle = SlimOcelLogHandle {
        events: (0..1000)
            .map(|i| SlimOcelEvent {
                activity: format!("Task_{}", i % 10),
                case_id: format!("case_{}", i / 100),
                object_id: format!("obj_{}", i),
            })
            .collect(),
        object_counts: HashMap::new(),
    };

    let slim_resource = SlimOcelResource(Arc::new(Mutex::new(slim_handle)));
    let slim = ResourceArc::new(slim_resource);

    // Benchmark DFG discovery
    c.bench_function("dfg_discovery", |b| {
        b.iter(|| {
            // This is a placeholder benchmark
            // In a real implementation, this would do actual DFG discovery
            let _ = discover_dfg(slim.clone());
        })
    });
}

/// Benchmark resource management performance
fn bench_resource_management(c: &mut Criterion) {
    // Benchmark resource creation and destruction
    c.bench_function("resource_creation", |b| {
        b.iter(|| {
            let ocel_handle = OcelLogHandle {
                events: vec![],
                objects: HashMap::new(),
                global_trace: "test".to_string(),
            };
            let resource = OcelLogResource(Arc::new(Mutex::new(ocel_handle)));
            let _ = ResourceArc::new(resource);
        })
    });
}

/// Benchmark JSON serialization performance
fn bench_json_serialization(c: &mut Criterion) {
    let ocel_handle = OcelLogHandle {
        events: (0..100)
            .map(|i| OcelEvent {
                activity: format!("Task_{}", i),
                timestamp: format!("2024-01-01T{:02}:{:02}:00Z", i / 60, i % 60),
                case_id: format!("case_{}", i / 10),
                object_id: format!("obj_{}", i),
                attributes: HashMap::new(),
            })
            .collect(),
        objects: (0..10)
            .map(|i| (format!("obj_{}", i), OcelObject {
                object_type: format!("type_{}", i),
                attributes: HashMap::new(),
            }))
            .collect(),
        global_trace: "global_trace".to_string(),
    };

    // Benchmark JSON serialization
    c.bench_function("json_serialization", |b| {
        b.iter(|| {
            serde_json::to_string(&ocel_handle).unwrap()
        })
    });

    // Benchmark JSON deserialization
    let json_str = serde_json::to_string(&ocel_handle).unwrap();
    c.bench_function("json_deserialization", |b| {
        b.iter(|| {
            serde_json::from_str::<OcelLogHandle>(&json_str).unwrap()
        })
    });
}

/// Benchmark parallel access performance
fn bench_parallel_access(c: &mut Criterion) {
    // Create test data
    let ocel_handle = OcelLogHandle {
        events: (0..1000)
            .map(|i| OcelEvent {
                activity: format!("Task_{}", i % 10),
                timestamp: format!("2024-01-01T{:02}:{:02}:00Z", i / 60, i % 60),
                case_id: format!("case_{}", i / 100),
                object_id: format!("obj_{}", i),
                attributes: HashMap::new(),
            })
            .collect(),
        objects: HashMap::new(),
        global_trace: "global_trace".to_string(),
    };

    let ocel_resource = OcelLogResource(Arc::new(Mutex::new(ocel_handle)));
    let ocel = ResourceArc::new(ocel_resource);

    // Benchmark parallel access
    c.bench_function("parallel_access", |b| {
        b.iter_batched(
            || (0..10).collect::<Vec<_>>(),
            |thread_counts| {
                let handles: Vec<_> = thread_counts
                    .into_iter()
                    .map(|_| {
                        std::thread::spawn(move || {
                            for _ in 0..100 {
                                let _ = log_event_count(ocel.clone());
                                let _ = log_case_count(ocel.clone());
                            }
                        })
                    })
                    .collect();

                for handle in handles {
                    handle.join().unwrap();
                }
            },
            BatchSize::PerIteration,
        )
    });
}

criterion_group!(
    benches,
    bench_conformance_checking,
    bench_dfg_discovery,
    bench_resource_management,
    bench_json_serialization,
    bench_parallel_access
);
criterion_main!(benches);