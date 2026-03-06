# Petgraph Integration API Reference

Complete documentation of the PetriNetGraph class and related types.

## PetriNetGraph

High-performance directed graph implementation via JNI bindings to Rust petgraph.

### Class Declaration

```java
public class PetriNetGraph implements AutoCloseable
```

### Constructors

#### `PetriNetGraph()`

Create a new empty directed graph.

**Signature**:
```java
public PetriNetGraph()
```

**Returns**: New graph instance with zero nodes and edges.

**Throws**: `UnsatisfiedLinkError` if the native library (`petgraph_jni`) cannot be loaded.

**Example**:
```java
PetriNetGraph graph = new PetriNetGraph();
```

---

### Methods

#### `addNode`

Add a node to the graph with arbitrary JSON data.

**Signature**:
```java
public int addNode(String id, JsonNode data)
```

**Parameters**:
- `id` (String): Unique identifier for the node. Used for logging and debugging.
- `data` (JsonNode): JSON object containing node properties (label, count, custom fields).

**Returns**: Node index (non-negative integer) assigned by the Rust graph.

**Throws**:
- `IllegalStateException`: If the graph is closed.

**Example**:
```java
ObjectMapper mapper = new ObjectMapper();
int node = graph.addNode("p1", mapper.createObjectNode()
    .put("label", "Place P1")
    .put("marking", 1));
```

---

#### `addEdge`

Add a directed edge from one node to another.

**Signature**:
```java
public int addEdge(int fromIdx, int toIdx, JsonNode data)
```

**Parameters**:
- `fromIdx` (int): Source node index (returned from previous `addNode` calls).
- `toIdx` (int): Target node index.
- `data` (JsonNode): JSON object with edge properties (weight, count).

**Returns**: Edge index (non-negative integer).

**Throws**:
- `IllegalStateException`: If the graph is closed.
- `IndexOutOfBoundsException`: If node indices are invalid.

**Example**:
```java
int edge = graph.addEdge(0, 1, mapper.createObjectNode()
    .put("weight", 5)
    .put("label", "transition"));
```

---

#### `nodeCount`

Get the total number of nodes in the graph.

**Signature**:
```java
public int nodeCount()
```

**Returns**: Number of nodes (≥ 0).

**Throws**: `IllegalStateException` if the graph is closed.

**Example**:
```java
int n = graph.nodeCount();
System.out.println("Graph has " + n + " nodes");
```

---

#### `edgeCount`

Get the total number of edges in the graph.

**Signature**:
```java
public int edgeCount()
```

**Returns**: Number of edges (≥ 0).

**Throws**: `IllegalStateException` if the graph is closed.

**Example**:
```java
int e = graph.edgeCount();
System.out.println("Graph has " + e + " edges");
```

---

#### `hasPath`

Check if a directed path exists from source to target node.

**Signature**:
```java
public boolean hasPath(int fromIdx, int toIdx)
```

**Parameters**:
- `fromIdx` (int): Source node index.
- `toIdx` (int): Target node index.

**Returns**: `true` if at least one directed path exists from `fromIdx` to `toIdx`, `false` otherwise.

**Algorithm**: Depth-first search (DFS) on the Rust side.

**Time Complexity**: O(V + E) worst case, but typically much faster if path is found early.

**Throws**: `IllegalStateException` if the graph is closed.

**Example**:
```java
if (graph.hasPath(startNode, endNode)) {
    System.out.println("Path found");
}
```

---

#### `successors`

Get all direct successors (out-neighbors) of a node.

**Signature**:
```java
public List<Integer> successors(int nodeIdx)
```

**Parameters**:
- `nodeIdx` (int): Source node index.

**Returns**: Unmodifiable list of successor node indices. Empty list if no successors.

**Throws**: `IllegalStateException` if the graph is closed.

**Example**:
```java
List<Integer> next = graph.successors(currentNode);
for (int succ : next) {
    System.out.println("Successor: " + succ);
}
```

---

#### `toJson`

Serialize the entire graph to JSON format.

**Signature**:
```java
public JsonNode toJson()
```

**Returns**: JSON object with structure:
```json
{
  "nodes": [
    { "index": 0, "data": { ... } },
    { "index": 1, "data": { ... } }
  ],
  "edges": [
    { "from": 0, "to": 1, "data": { ... } }
  ]
}
```

**Throws**: `IllegalStateException` if the graph is closed.

**Example**:
```java
JsonNode json = graph.toJson();
String pretty = json.toPrettyString();
System.out.println(pretty);
```

---

#### `isClosed`

Check if the graph has been closed.

**Signature**:
```java
public boolean isClosed()
```

**Returns**: `true` if `close()` has been called, `false` otherwise.

**Example**:
```java
if (!graph.isClosed()) {
    // Safe to use graph
}
```

---

#### `close`

Free memory on the Rust side and mark the graph as closed.

**Signature**:
```java
@Override
public void close()
```

**Behavior**:
- Deallocates the Rust `DiGraph` and associated data structures.
- Subsequent calls to graph methods will raise `IllegalStateException`.
- Safe to call multiple times (idempotent).

**Example** (Manual closing):
```java
PetriNetGraph graph = new PetriNetGraph();
// ... use graph ...
graph.close();
```

**Example** (Try-with-resources, recommended):
```java
try (var graph = new PetriNetGraph()) {
    // ... use graph ...
} // Automatically closed
```

---

## DirectlyFollowsGraph Extensions

### Method: `toPetriNetGraph`

Convert a DirectlyFollowsGraph to a petgraph-backed optimized structure.

**Signature**:
```java
public PetriNetGraph toPetriNetGraph()
```

**Returns**: New `PetriNetGraph` with all nodes and edges from this DFG.

**Node Data Attached**:
```json
{
  "id": "activity_id",
  "label": "Activity Label",
  "count": 150
}
```

**Edge Data Attached**:
```json
{
  "source": "activity_id",
  "target": "activity_id",
  "count": 75
}
```

**Example**:
```java
DirectlyFollowsGraph dfg = discoverDfg();
try (var graph = dfg.toPetriNetGraph()) {
    // Use optimized graph for fast queries
}
```

---

## Data Structures

### Node Data Format

Nodes carry arbitrary JSON objects. Common fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier |
| `label` | String | Human-readable name |
| `count` | Integer | Occurrence frequency |
| `type` | String | Semantic type (e.g., "activity", "place") |

### Edge Data Format

Edges carry arbitrary JSON objects. Common fields:

| Field | Type | Description |
|-------|------|-------------|
| `weight` | Number | Arc weight or frequency |
| `count` | Integer | Transition count |
| `source` | String | Source activity ID |
| `target` | String | Target activity ID |
| `label` | String | Transition label |

---

## Error Handling

### Exceptions

#### `IllegalStateException`

Thrown when attempting to use a closed graph.

```java
try (var graph = new PetriNetGraph()) {
    // ...
}
graph.nodeCount(); // Throws IllegalStateException
```

#### `IndexOutOfBoundsException`

Thrown when node indices are invalid.

```java
graph.addEdge(999, 1000, data); // May throw if indices are out of bounds
```

#### `UnsatisfiedLinkError`

Thrown during initialization if the native library cannot be loaded.

**Solution**: Ensure `libpetgraph_jni.so` (Linux), `libpetgraph_jni.dylib` (macOS), or `petgraph_jni.dll` (Windows) is in `java.library.path`.

---

## Thread Safety

- **Graph operations**: Protected by a Mutex on the Rust side. Safe for concurrent access from multiple Java threads.
- **Node/edge data**: JSON objects are cloned, not shared. Safe for concurrent modifications.
- **Closure**: Not thread-safe. Ensure only one thread calls `close()` at a time.

---

## Memory Management

- **Automatic cleanup**: Use try-with-resources to ensure proper cleanup.
- **Manual cleanup**: Call `close()` explicitly if not using try-with-resources.
- **Garbage collection**: Does NOT automatically free Rust memory. Explicit `close()` is required.

---

## Native Library Loading

The static initializer attempts to load the native library using:

```java
System.loadLibrary("petgraph_jni");
```

**Supported Platforms**:
- Linux: `libpetgraph_jni.so`
- macOS: `libpetgraph_jni.dylib`
- Windows: `petgraph_jni.dll`

**Library Path**: Must be in `java.library.path`. Typically added via Maven during build.

---

## Performance Characteristics

| Operation | Time | Space |
|-----------|------|-------|
| `addNode` | O(1) amortized | O(1) |
| `addEdge` | O(1) amortized | O(1) |
| `nodeCount` | O(1) | O(0) |
| `edgeCount` | O(1) | O(0) |
| `hasPath` | O(V + E) worst, O(1) best | O(V) |
| `successors` | O(k) where k = out-degree | O(k) |
| `toJson` | O(V + E) | O(V + E) |

---

## Limitations

- **Node/edge data**: Limited to JSON-serializable types
- **Cycle detection**: Not built-in, but can be checked via DFS
- **Graph modification**: Edges cannot be removed (append-only design)
- **Visualization**: No built-in rendering, but `toJson()` supports export

---

## See Also

- Tutorial: `petgraph-integration-tutorial.md`
- How-To Guide: `petgraph-integration-howto.md`
- Explanation: `petgraph-integration-explanation.md`
