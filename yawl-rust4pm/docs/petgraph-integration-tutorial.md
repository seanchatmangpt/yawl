# Petgraph Integration Tutorial

Learn how to use petgraph-backed graphs for high-performance process mining analysis.

## What You'll Learn

- Create a directed graph from a process mining model
- Query paths and reachability efficiently
- Convert existing DFG models to the optimized petgraph backend
- Benchmark performance improvements

## Prerequisites

- Java 25+
- Maven 3.8+
- Rust toolchain (for building the JNI library)
- yawl-rust4pm module in your classpath

## Part 1: Create a Simple Graph

Start with creating an empty petgraph-backed graph and adding some nodes:

```java
import org.yawlfoundation.yawl.rust4pm.petgraph.PetriNetGraph;
import com.fasterxml.jackson.databind.ObjectMapper;

PetriNetGraph graph = new PetriNetGraph();
ObjectMapper mapper = new ObjectMapper();

// Add nodes representing activities
int nodeA = graph.addNode("a1", mapper.createObjectNode()
    .put("label", "Receive Request")
    .put("count", 150));

int nodeB = graph.addNode("a2", mapper.createObjectNode()
    .put("label", "Process")
    .put("count", 148));

int nodeC = graph.addNode("a3", mapper.createObjectNode()
    .put("label", "Send Response")
    .put("count", 148));

System.out.println("Created graph with " + graph.nodeCount() + " nodes");
```

## Part 2: Connect Nodes with Edges

Add edges representing the directly-follows relationships:

```java
// Create edges with transition counts
graph.addEdge(nodeA, nodeB, mapper.createObjectNode()
    .put("count", 148));

graph.addEdge(nodeB, nodeC, mapper.createObjectNode()
    .put("count", 148));

System.out.println("Added " + graph.edgeCount() + " edges");
```

## Part 3: Query the Graph

Check reachability and find successors:

```java
// Can we go from Receive Request to Send Response?
if (graph.hasPath(nodeA, nodeC)) {
    System.out.println("Path exists from A to C");
}

// What are the direct next activities after Process?
var nextActivities = graph.successors(nodeB);
System.out.println("Successors of B: " + nextActivities);
```

## Part 4: Convert a Discovered DFG

If you have a DirectlyFollowsGraph from process mining, convert it to petgraph format:

```java
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;

// Assume dfg is your discovered DFG
DirectlyFollowsGraph dfg = discoverDfgFromOcel2();

// Convert to optimized petgraph backend
try (var graph = dfg.toPetriNetGraph()) {
    // Now use graph for fast reachability queries
    boolean conformant = checkConformance(graph, trace);
    System.out.println("Conformance result: " + conformant);
}
```

## Part 5: Serialize and Persist

Export the graph to JSON for storage or visualization:

```java
var json = graph.toJson();

// json structure:
// {
//   "nodes": [
//     { "index": 0, "data": { "label": "...", "count": ... } },
//     ...
//   ],
//   "edges": [
//     { "from": 0, "to": 1, "data": { "count": ... } },
//     ...
//   ]
// }

System.out.println(json.toPrettyString());
```

## Summary

You've learned how to:
1. ✅ Create a petgraph-backed directed graph
2. ✅ Add nodes and edges with JSON data
3. ✅ Query paths and successors efficiently
4. ✅ Convert DFG models to the optimized format
5. ✅ Export graphs as JSON

## Next Steps

- See **How-to Guide** for common patterns (conformance checking, bottleneck analysis)
- See **Reference** for complete API documentation
- See **Explanation** for performance characteristics and architecture details
