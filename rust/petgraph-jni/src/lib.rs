//! petgraph-jni — Java bindings for petgraph directed graphs
//!
//! Provides JNI (Java Native Interface) bindings to petgraph's DiGraph implementation.
//! This enables Java applications to leverage Rust's high-performance graph algorithms
//! for process mining and workflow analysis.
//!
//! # Architecture
//!
//! 1. **Graph Storage**: Rust-side DiGraph<NodeData, EdgeData>
//! 2. **Handle Management**: Opaque long pointer passed to Java
//! 3. **Operation Bridge**: JNI methods for graph construction and querying
//! 4. **Data Transfer**: JSON serialization for node/edge weights

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject};
use jni::sys::{jlong, jint, jboolean, jdouble};
use petgraph::graph::{DiGraph, NodeIndex, EdgeIndex};
use petgraph::algo::{dijkstra, DfsSpace};
use petgraph::visit::Dfs;
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

/// A wrapper around a petgraph DiGraph with thread-safe ownership
/// Nodes and edges carry JSON data for maximum flexibility
struct GraphHandle {
    graph: Arc<Mutex<DiGraph<Value, Value>>>,
    node_map: Arc<Mutex<HashMap<String, NodeIndex>>>,
    edge_count: Arc<Mutex<usize>>,
}

impl GraphHandle {
    fn new() -> Self {
        GraphHandle {
            graph: Arc::new(Mutex::new(DiGraph::new())),
            node_map: Arc::new(Mutex::new(HashMap::new())),
            edge_count: Arc::new(Mutex::new(0)),
        }
    }

    fn from_pointer(ptr: jlong) -> Arc<Mutex<GraphHandle>> {
        unsafe { Arc::from_raw(ptr as *const Mutex<GraphHandle>) }
    }

    fn to_pointer(&self) -> jlong {
        Arc::into_raw(Arc::new(Mutex::new(self.clone()))) as jlong
    }
}

impl Clone for GraphHandle {
    fn clone(&self) -> Self {
        GraphHandle {
            graph: Arc::clone(&self.graph),
            node_map: Arc::clone(&self.node_map),
            edge_count: Arc::clone(&self.edge_count),
        }
    }
}

/// Create a new directed graph handle
/// Returns a long pointer to the graph that must be passed to other operations
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_createGraph(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let handle = GraphHandle::new();
    Arc::into_raw(Arc::new(Mutex::new(handle))) as jlong
}

/// Add a node to the graph with JSON data
/// Returns the node index (positive integer)
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_addNode(
    env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
    node_id: JString,
    node_data: JString,
) -> jint {
    let id: String = env
        .get_string(&node_id)
        .ok()
        .and_then(|s| s.into_string().ok())
        .unwrap_or_default();

    let data_json: String = env
        .get_string(&node_data)
        .ok()
        .and_then(|s| s.into_string().ok())
        .unwrap_or_else(|_| "{}".to_string());

    let handle = GraphHandle::from_pointer(graph_ptr);
    let mut locked = handle.lock().unwrap();

    let data: Value = serde_json::from_str(&data_json)
        .unwrap_or_else(|_| Value::Object(Default::default()));

    let mut graph = locked.graph.lock().unwrap();
    let node_idx = graph.add_node(data);

    let mut node_map = locked.node_map.lock().unwrap();
    node_map.insert(id, node_idx);

    node_idx.index() as jint
}

/// Add an edge (arc) between two nodes
/// Returns the edge index
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_addEdge(
    env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
    from_idx: jint,
    to_idx: jint,
    edge_data: JString,
) -> jint {
    let data_json: String = env
        .get_string(&edge_data)
        .ok()
        .and_then(|s| s.into_string().ok())
        .unwrap_or_else(|_| "{}".to_string());

    let handle = GraphHandle::from_pointer(graph_ptr);
    let mut locked = handle.lock().unwrap();

    let data: Value = serde_json::from_str(&data_json)
        .unwrap_or_else(|_| Value::Object(Default::default()));

    let from = NodeIndex::new(from_idx as usize);
    let to = NodeIndex::new(to_idx as usize);

    let mut graph = locked.graph.lock().unwrap();
    let edge_idx = graph.add_edge(from, to, data);

    let mut edge_count = locked.edge_count.lock().unwrap();
    *edge_count += 1;

    edge_idx.index() as jint
}

/// Get the number of nodes in the graph
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_nodeCount(
    _env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
) -> jint {
    let handle = GraphHandle::from_pointer(graph_ptr);
    let locked = handle.lock().unwrap();
    let graph = locked.graph.lock().unwrap();
    graph.node_count() as jint
}

/// Get the number of edges in the graph
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_edgeCount(
    _env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
) -> jint {
    let handle = GraphHandle::from_pointer(graph_ptr);
    let locked = handle.lock().unwrap();
    let edge_count = locked.edge_count.lock().unwrap();
    *edge_count as jint
}

/// Check if a path exists from source to target using DFS
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_hasPath(
    _env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
    from_idx: jint,
    to_idx: jint,
) -> jboolean {
    let handle = GraphHandle::from_pointer(graph_ptr);
    let locked = handle.lock().unwrap();
    let graph = locked.graph.lock().unwrap();

    let from = NodeIndex::new(from_idx as usize);
    let to = NodeIndex::new(to_idx as usize);

    let mut dfs = Dfs::new(&*graph, from);
    while let Some(nx) = dfs.next(&*graph) {
        if nx == to {
            return 1; // true
        }
    }
    0 // false
}

/// Get all successors (out-neighbors) of a node
/// Returns a JSON array of node indices
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_successors(
    env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
    node_idx: jint,
) -> JString {
    let handle = GraphHandle::from_pointer(graph_ptr);
    let locked = handle.lock().unwrap();
    let graph = locked.graph.lock().unwrap();

    let node = NodeIndex::new(node_idx as usize);
    let successors: Vec<usize> = graph
        .neighbors(node)
        .map(|n| n.index())
        .collect();

    let json = serde_json::to_string(&successors).unwrap_or_else(|_| "[]".to_string());
    env.new_string(&json).unwrap()
}

/// Serialize the graph to JSON for persistence
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_toJson(
    env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
) -> JString {
    let handle = GraphHandle::from_pointer(graph_ptr);
    let locked = handle.lock().unwrap();
    let graph = locked.graph.lock().unwrap();

    let nodes: Vec<Value> = graph
        .node_indices()
        .map(|idx| {
            json!({
                "index": idx.index(),
                "data": &graph[idx]
            })
        })
        .collect();

    let edges: Vec<Value> = graph
        .edge_indices()
        .map(|idx| {
            let (from, to) = graph.edge_endpoints(idx).unwrap();
            json!({
                "from": from.index(),
                "to": to.index(),
                "data": &graph[idx]
            })
        })
        .collect();

    let result = json!({
        "nodes": nodes,
        "edges": edges
    });

    let json_str = serde_json::to_string(&result).unwrap_or_else(|_| "{}".to_string());
    env.new_string(&json_str).unwrap()
}

/// Destroy the graph and free memory
#[no_mangle]
pub extern "system" fn Java_org_yawlfoundation_yawl_rust4pm_petgraph_PetriNetGraph_destroy(
    _env: JNIEnv,
    _class: JClass,
    graph_ptr: jlong,
) {
    if graph_ptr != 0 {
        unsafe {
            let _ = Arc::from_raw(graph_ptr as *const Mutex<GraphHandle>);
        }
    }
}
