# Petgraph Integration How-To Guide

Practical recipes for common process mining tasks using petgraph-backed graphs.

## How to Check Conformance of a Trace

Use petgraph to efficiently validate whether a trace conforms to the discovered model:

```java
public boolean isTraceConformant(PetriNetGraph dfg, List<String> trace) {
    // Map activity names to node indices (you'd populate this during graph creation)
    Map<String, Integer> activityToNode = getActivityNodeMap(dfg);

    for (int i = 0; i < trace.size() - 1; i++) {
        String current = trace.get(i);
        String next = trace.get(i + 1);

        Integer fromIdx = activityToNode.get(current);
        Integer toIdx = activityToNode.get(next);

        if (fromIdx == null || toIdx == null) {
            return false; // Unknown activity
        }

        // Check if this edge exists in the DFG
        var successors = dfg.successors(fromIdx);
        if (!successors.contains(toIdx)) {
            return false; // Edge not in model
        }
    }
    return true;
}
```

## How to Find All Paths Between Two Activities

Discover all possible execution paths in your process:

```java
public List<List<Integer>> findAllPaths(PetriNetGraph graph,
                                        int from, int to) {
    List<List<Integer>> paths = new ArrayList<>();
    List<Integer> currentPath = new ArrayList<>();

    dfs(graph, from, to, currentPath, paths);

    return paths;
}

private void dfs(PetriNetGraph graph, int current, int target,
                 List<Integer> currentPath, List<List<Integer>> allPaths) {
    currentPath.add(current);

    if (current == target) {
        allPaths.add(new ArrayList<>(currentPath));
    } else {
        for (int next : graph.successors(current)) {
            if (!currentPath.contains(next)) { // Avoid cycles
                dfs(graph, next, target, currentPath, allPaths);
            }
        }
    }

    currentPath.remove(currentPath.size() - 1);
}
```

## How to Identify Bottleneck Activities

Find activities with high out-degree (many direct transitions):

```java
public List<Integer> findBottlenecks(PetriNetGraph graph) {
    List<Integer> bottlenecks = new ArrayList<>();

    for (int i = 0; i < graph.nodeCount(); i++) {
        int outDegree = graph.successors(i).size();

        // If more than 3 direct successors, it's a bottleneck
        if (outDegree > 3) {
            bottlenecks.add(i);
        }
    }

    return bottlenecks;
}
```

## How to Perform Conformance Checking with Token Replay

Token replay simulation for detailed diagnostics:

```java
public ConformanceResult replayTrace(PetriNetGraph dfg,
                                      List<String> trace,
                                      Map<String, Integer> nodeMap) {
    int maxTokens = 1;
    List<Integer> tokenPositions = new ArrayList<>();
    int currentPos = nodeMap.get(trace.get(0));
    tokenPositions.add(currentPos);

    int executed = 1;
    int total = trace.size();

    for (int i = 1; i < trace.size(); i++) {
        String activity = trace.get(i);
        int targetNode = nodeMap.get(activity);

        // Check if any token can fire the transition
        boolean found = false;
        for (Integer pos : new ArrayList<>(tokenPositions)) {
            if (dfg.successors(pos).contains(targetNode)) {
                tokenPositions.remove(pos);
                tokenPositions.add(targetNode);
                found = true;
                executed++;
                break;
            }
        }

        if (!found) {
            // Missing token - record deviation
        }
    }

    return new ConformanceResult(executed, total, (double) executed / total);
}

record ConformanceResult(int executed, int total, double fitness) {}
```

## How to Batch Convert Multiple DFGs

Process multiple discovered DFGs efficiently:

```java
public List<PetriNetGraph> convertDfgsToOptimized(List<DirectlyFollowsGraph> dfgs) {
    return dfgs.stream()
        .map(DirectlyFollowsGraph::toPetriNetGraph)
        .collect(Collectors.toList());
}

// Usage with try-with-resources for safe cleanup
public void analyzeMultipleDfgs(List<DirectlyFollowsGraph> dfgs) {
    try (var resources = dfgs.stream()
            .map(DirectlyFollowsGraph::toPetriNetGraph)
            .toList()) {

        for (var graph : resources) {
            System.out.println("Nodes: " + graph.nodeCount() +
                             ", Edges: " + graph.edgeCount());
        }
    }
}
```

## How to Export Graph for Visualization

Create a format suitable for Graphviz or other visualization tools:

```java
public String exportAsDot(PetriNetGraph graph,
                          DirectlyFollowsGraph dfg) {
    StringBuilder dot = new StringBuilder();
    dot.append("digraph dfg {\n");
    dot.append("rankdir=LR;\n");
    dot.append("node [shape=box];\n");

    // Add nodes with activity labels
    for (int i = 0; i < dfg.nodes().size(); i++) {
        var node = dfg.nodes().get(i);
        dot.append(String.format("  \"%s\" [label=\"%s\\n(%d)\"];\n",
            i, node.label(), node.count()));
    }

    // Add edges
    int nodeIdx = 0;
    for (var node : dfg.nodes()) {
        var successors = graph.successors(nodeIdx);
        for (int succ : successors) {
            dot.append(String.format("  \"%d\" -> \"%d\";\n", nodeIdx, succ));
        }
        nodeIdx++;
    }

    dot.append("}\n");
    return dot.toString();
}
```

## How to Handle Large Graphs

Best practices for memory-efficient processing:

```java
public void processLargeGraph(DirectlyFollowsGraph dfg) {
    // Create graph only when needed
    try (var graph = dfg.toPetriNetGraph()) {
        // Process in batches to avoid holding everything in memory
        for (int i = 0; i < graph.nodeCount(); i += 100) {
            processBatch(graph, i, Math.min(i + 100, graph.nodeCount()));
        }
    }
    // Graph is automatically freed when exiting try block
}

private void processBatch(PetriNetGraph graph, int start, int end) {
    for (int i = start; i < end; i++) {
        var successors = graph.successors(i);
        // Process successors
    }
}
```

## Key Patterns

### Pattern 1: Reachability Checking
Use `hasPath()` for quick connectivity checks. Returns immediately if path found.

### Pattern 2: Activity Succession
Use `successors()` to find all direct next activities. Useful for building activity adjacency matrices.

### Pattern 3: Graph Export
Use `toJson()` for persistence and visualization export. The JSON format is stable and future-proof.

### Pattern 4: Try-With-Resources
Always use try-with-resources blocks to ensure graphs are properly deallocated.

## Performance Tips

1. **Reuse graphs**: Create the petgraph once, query multiple times
2. **Batch conversions**: Convert all DFGs upfront, not per-query
3. **Check paths early**: Use `hasPath()` before expensive operations
4. **Cache successors**: Store `successors()` results if used repeatedly
5. **Close promptly**: Don't hold graphs longer than needed

## Common Mistakes to Avoid

❌ **Mistake**: Forgetting to close graphs
```java
// DON'T do this - memory leak!
var graph = dfg.toPetriNetGraph();
checkConformance(graph);
```

✅ **Correct**:
```java
// DO this - proper cleanup
try (var graph = dfg.toPetriNetGraph()) {
    checkConformance(graph);
}
```

---

For complete API details, see **Reference**.
For architecture and performance characteristics, see **Explanation**.
