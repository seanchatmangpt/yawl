# YAWL DSPy API Usage Examples

This document provides comprehensive examples for using the YAWL DSPy module.

## Table of Contents
1. [Basic Program Execution](#basic-program-execution)
2. [Worklet Selection](#worklet-selection)
3. [Anomaly Forensics](#anomaly-forensics)
4. [Runtime Adaptation](#runtime-adaptation)
5. [Resource Prediction](#resource-prediction)
6. [MCP Tools Integration](#mcp-tools-integration)
7. [A2A Skills Integration](#a2a-skills-integration)

---

## Basic Program Execution

### Creating a DSPy Program

```java
// Initialize Python execution engine
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .contextPoolSize(4)
    .enableMetrics(true)
    .build();

// Create DSPy bridge
PythonDspyBridge dspy = new PythonDspyBridge(engine);

// Define a simple DSPy program
DspyProgram program = DspyProgram.builder()
    .name("sentiment-analyzer")
    .source("""
        import dspy
        
        class SentimentAnalyzer(dspy.Module):
            def __init__(self):
                self.classify = dspy.ChainOfThought("text -> sentiment")
            
            def forward(self, text):
                return self.classify(text=text)
        """)
    .inputSchema(Map.of(
        "type", "object",
        "properties", Map.of(
            "text", Map.of("type", "string")
        )
    ))
    .outputSchema(Map.of(
        "type", "object", 
        "properties", Map.of(
            "sentiment", Map.of("type", "string"),
            "confidence", Map.of("type", "number")
        )
    ))
    .description("Analyzes text sentiment using DSPy")
    .build();
```

### Executing a Program

```java
// Execute the program
Map<String, Object> inputs = Map.of("text", "YAWL workflow engine is fantastic!");
DspyExecutionResult result = dspy.execute(program, inputs);

// Access results
Map<String, Object> output = result.output();
String sentiment = (String) output.get("sentiment");
Double confidence = (Double) output.get("confidence");

// Get execution metrics
DspyExecutionMetrics metrics = result.metrics();
System.out.println("Execution time: " + metrics.executionTimeMs() + "ms");
System.out.println("Cache hit: " + metrics.cacheHit());
System.out.println("Total tokens: " + metrics.totalTokens());
```

### Program Caching

```java
// Check if program is cached
String cacheKey = program.cacheKey();
if (dspy.getCacheStats().get("cacheSize").equals(0)) {
    System.out.println("Program not cached, will compile on first execution");
}

// Execute again - will use cached version
DspyExecutionResult cachedResult = dspy.execute(program, inputs);
System.out.println("Cache hit on second execution: " + 
    cachedResult.metrics().cacheHit());
```

---

## Worklet Selection

### Using the Built-in Worklet Selector

```java
// Create worklet selection context
WorkletSelectionContext context = new WorkletSelectionContext(
    "Review",                          // Task name
    Map.of(
        "urgency", "high",
        "complexity", "medium",
        "amount", "$25,000",
        "department", "Finance"
    ),                                  // Case data
    List.of("StandardTrack", "FastTrack", "ExpertTrack"),  // Available worklets
    List.of(
        "case_123: FastTrack",
        "case_124: StandardTrack",
        "case_125: ExpertTrack"
    )                                  // Historical selections
);

// Execute worklet selection
WorkletSelection selection = dspy.selectWorklet(context);

// Use the selection
String selectedWorklet = selection.workletId();
double confidence = selection.confidence();
String rationale = selection.rationale();

System.out.println("Selected worklet: " + selectedWorklet);
System.out.println("Confidence: " + confidence);
System.out.println("Reasoning: " + rationale);
```

### Custom Worklet Selection Program

```java
DspyProgram customSelector = DspyProgram.builder()
    .name("custom-worklet-selector")
    .source("""
        import dspy
        
        class CustomWorkletSelector(dspy.Module):
            def __init__(self):
                self.select = dspy.ChainOfThought(
                    "task, case_data, available, history -> worklet, confidence, rationale"
                )
            
            def forward(self, task, case_data, available, history):
                result = self.select(
                    task=task,
                    case_data=case_data,
                    available=available,
                    history=history
                )
                
                # Custom logic for urgent cases
                if case_data.get("urgency") == "high":
                    if "ExpertTrack" in available:
                        return {
                            "worklet_id": "ExpertTrack",
                            "confidence": 0.95,
                            "rationale": "High urgency requires expert handling"
                        }
                
                return {
                    "worklet_id": result.worklet,
                    "confidence": result.confidence,
                    "rationale": result.rationale
                }
        """)
    .build();

// Execute custom selector
Map<String, Object> inputs = Map.of(
    "task", "Review",
    "case_data", Map.of("urgency", "high", "amount", "$50,000"),
    "available", List.of("StandardTrack", "FastTrack", "ExpertTrack"),
    "history", List.of("case_123: FastTrack", "case_124: ExpertTrack")
);

DspyExecutionResult result = dspy.execute(customSelector, inputs);
Map<String, Object> output = result.output();
```

---

## Anomaly Forensics

### Running Anomaly Analysis

```java
// Create anomaly context
AnomalyContext anomaly = new AnomalyContext(
    "task_processing_latency",          // Metric name
    8000L,                              // Duration anomaly persisted (ms)
    4.5,                               // Deviation factor (450% of baseline)
    List.of(
        100L, 105L, 110L,             // Normal baseline
        450L, 520L,                    // Spike start
        850L, 920L, 880L, 910L       // Sustained spike
    ),                                  // Recent samples
    12                                 // Concurrent cases during spike
);

// Run forensics analysis
ForensicsReport report = dspy.runForensics(anomaly);

// Analyze results
String rootCause = report.rootCause();
double confidence = report.confidence();
List<String> evidenceChain = report.evidenceChain();
String recommendation = report.recommendation();

System.out.println("Root Cause: " + rootCause);
System.out.println("Confidence: " + (confidence * 100) + "%");
System.out.println("Evidence:");
evidenceChain.forEach(e -> System.out.println("  - " + e));
System.out.println("Recommendation: " + recommendation);
```

### Custom Forensics Program

```java
DspyProgram customForensics = DspyProgram.builder()
    .name("custom-forensics")
    .source("""
        import dspy
        
        class CustomForensics(dspy.Module):
            def __init__(self):
                self.analyze = dspy.ChainOfThought(
                    "metric, duration, deviation, samples, cases -> root_cause, confidence, evidence, recommendation"
                )
            
            def forward(self, metric, duration, deviation, samples, cases):
                result = self.analyze(
                    metric=metric,
                    duration=duration,
                    deviation=deviation,
                    samples=samples,
                    cases=cases
                )
                
                # Generate evidence chain
                evidence = []
                if deviation > 3.0:
                    evidence.append(f"Critical metric deviation: {deviation*100:.0f}% above baseline")
                if duration > 5000:
                    evidence.append(f"Anomaly persisted for {duration/1000:.1f} seconds")
                if cases > 10:
                    evidence.append(f"High concurrency: {cases} cases")
                
                # Generate recommendations
                if deviation > 4.0:
                    recommendation = "IMMEDIATE ACTION REQUIRED: Scale resources and investigate root cause"
                elif deviation > 2.0:
                    recommendation = "Monitor closely and prepare contingency measures"
                else:
                    recommendation = "Monitor for sustained patterns"
                
                return {
                    "root_cause": result.root_cause,
                    "confidence": result.confidence,
                    "evidence_chain": evidence,
                    "recommendation": recommendation
                }
        """)
    .build();

// Execute custom forensics
Map<String, Object> inputs = Map.of(
    "metric", "task_processing_latency",
    "duration", 8000,
    "deviation", 4.5,
    "samples", List.of(100L, 105L, 110L, 450L, 520L, 850L, 920L, 880L, 910L),
    "cases", 12
);

DspyExecutionResult result = dspy.execute(customForensics, inputs);
```

---

## Runtime Adaptation

### Executing ReAct Agent

```java
// Create adaptation context
WorkflowAdaptationContext adaptation = new WorkflowAdaptationContext(
    "case_126",                         // Case ID
    "spec_workflow_1",                  // Specification ID
    0.85,                              // Bottleneck score (0-1)
    List.of("Review", "Approve", "Execute"),  // Enabled tasks
    List.of("Review"),                 // Busy tasks
    15,                                // Queue depth
    120L,                              // Average task latency (ms)
    List.of("agent-1", "agent-2"),     // Available agents
    "bottleneck_detected",             // Event type
    Map.of(
        "task_id", "Review",
        "duration_ms", 8500,
        "queue_position", 12
    )                                  // Event payload
);

// Execute ReAct adaptation agent
AdaptationAction action = dspy.executeReActAgent(adaptation);

// Process the action
switch (action) {
    case AdaptationAction.SkipTask skip -> {
        System.out.println("Skip task: " + skip.taskId());
        System.out.println("Reasoning: " + skip.reasoning());
    }
    case AdaptationAction.AddResource add -> {
        System.out.println("Add resource: " + add.agentId() + " to task " + add.taskId());
        System.out.println("Reasoning: " + add.reasoning());
    }
    case AdaptationAction.ReRoute route -> {
        System.out.println("ReRoute task " + route.taskId() + " to " + route.alternateRoute());
        System.out.println("Reasoning: " + route.reasoning());
    }
    case AdaptationAction.EscalateCase escalate -> {
        System.out.println("Escalate case " + escalate.caseId() + " to " + escalate.escalationLevel());
        System.out.println("Reasoning: " + escalate.reasoning());
    }
}
```

### Custom Adaptation Logic

```java
DspyProgram customAdaptation = DspyProgram.builder()
    .name("custom-adaptation")
    .source("""
        import dspy
        
        class CustomAdaptation(dspy.Module):
            def __init__(self):
                self.adapt = dspy.ReAct(
                    tools=["check_status", "add_resource", "reroute", "escalate"]
                )
            
            def forward(self, case_id, spec_id, bottleneck_score, enabled_tasks, 
                       busy_tasks, queue_depth, avg_latency, available_agents):
                
                # Custom adaptation logic
                if bottleneck_score > 0.8:
                    if len(available_agents) > 0:
                        return {
                            "action_type": "ADD_RESOURCE",
                            "task_id": enabled_tasks[0] if enabled_tasks else "default",
                            "agent_id": f"agent-{case_id[:8]}",
                            "reasoning": f"Critical bottleneck ({bottleneck_score:.2f}) - add resource"
                        }
                    else:
                        return {
                            "action_type": "ESCALATE",
                            "escalation_level": "manager",
                            "reasoning": f"Critical bottleneck but no available agents"
                        }
                elif queue_depth > 10 and avg_latency > 2000:
                    return {
                        "action_type": "REROUTE",
                        "alternate_route": "expedited",
                        "reasoning": f"High queue depth ({queue_depth}) and latency ({avg_latency}ms)"
                    }
                else:
                    return {
                        "action_type": "SKIP_TASK",
                        "reasoning": f"Normal operation - no adaptation needed"
                    }
        """)
    .build();
```

---

## Resource Prediction

### Predicting Resource Allocation

```java
// Create resource prediction context
ResourcePredictionContext context = new ResourcePredictionContext(
    "Execute",                         // Task type
    List.of("Python", "DataAnalysis", "MachineLearning"),  // Required capabilities
    Map.of(
        "agent-1", 0.95,              // Historical success scores
        "agent-2", 0.88,
        "agent-3", 0.72,
        "agent-4", 0.65
    ),
    8                                 // Current queue depth
);

// Execute resource prediction
ResourcePrediction prediction = dspy.predictResourceAllocation(context);

// Use prediction
String bestAgent = prediction.agentId();
double confidence = prediction.confidence();
String reasoning = prediction.reasoning();

System.out.println("Best agent: " + bestAgent);
System.out.println("Confidence: " + confidence);
System.out.println("Reasoning: " + reasoning);
```

### Custom Resource Routing

```java
DspyProgram customRouter = DspyProgram.builder()
    .name("custom-resource-router")
    .source("""
        import dspy
        
        class CustomResourceRouter(dspy.Module):
            def __init__(self):
                self.route = dspy.ChainOfThought(
                    "task_type, capabilities, scores, queue -> best_agent, confidence, reasoning"
                )
            
            def forward(self, task_type, capabilities, scores, queue):
                result = self.route(
                    task_type=task_type,
                    capabilities=capabilities,
                    scores=scores,
                    queue=queue
                )
                
                # Custom weighting for high-priority tasks
                if task_type == "Emergency":
                    # Prefer agents with highest availability
                    best_agent = max(scores, key=scores.get)
                    confidence = scores[best_agent]
                    reasoning = f"Emergency task - assign to most capable agent"
                else:
                    # Use DSPy recommendation
                    best_agent = result.best_agent
                    confidence = result.confidence
                    reasoning = result.reasoning
                
                return {
                    "best_agent_id": best_agent,
                    "confidence": confidence,
                    "reasoning": reasoning
                }
        """)
    .build();
```

---

## MCP Tools Integration

### Setting up MCP Server

```java
// Create program registry
DspyProgramRegistry registry = new DspyProgramRegistry();

// Register some programs
DspyProgram sentimentProgram = DspyProgram.builder()
    .name("sentiment-analyzer")
    .source("import dspy
class SentimentAnalyzer(dspy.Module):
    def forward(self, text):
        return {sentiment: positive}")
    .build();
registry.save(sentimentProgram);

// Create MCP tools
List<McpServerFeatures.SyncToolSpecification> tools = DspyMcpTools.createAll(registry);

// Create MCP server
McpServer server = McpServer.builder()
    .name("yawl-dspy")
    .version("6.0.0")
    .description("DSPy program execution for YAWL workflows")
    .tools(tools)
    .build();
```

### Available MCP Tools

#### dspy_execute_program
```json
{
  "tool": "dspy_execute_program",
  "arguments": {
    "program": "sentiment-analyzer",
    "inputs": {
      "text": "This is a positive message"
    }
  }
}
```

Response:
```json
{
  "sentiment": "positive",
  "confidence": 0.95
}
```

#### dspy_list_programs
```json
{
  "tool": "dspy_list_programs"
}
```

Response:
```json
{
  "programs": [
    {
      "name": "sentiment-analyzer",
      "description": "Analyzes text sentiment",
      "source_hash": "abc123...",
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### dspy_get_program_info
```json
{
  "tool": "dspy_get_program_info",
  "arguments": {
    "program": "sentiment-analyzer"
  }
}
```

#### dspy_reload_program
```json
{
  "tool": "dspy_reload_program",
  "arguments": {
    "program": "sentiment-analyzer",
    "new_source": "updated source code..."
  }
}
```

---

## A2A Skills Integration

### Creating Skills from Registry

```java
// Create program registry
DspyProgramRegistry registry = new DspyProgramRegistry();

// Register worklet selector program
DspyProgram workletProgram = DspyProgram.builder()
    .name("worklet-selector")
    .source("""
        import dspy
        class WorkletSelector(dspy.Module):
            def forward(self, context):
                return {"worklet_id": "FastTrack", "confidence": 0.9}
        """)
    .build();
registry.save(workletProgram);

// Create A2A skills
List<A2ASkill> skills = DspyA2ASkill.createAll(registry);

// Execute a skill
SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
    .parameter("context", "Task: Review, Case: {urgency: high}")
    .build();

SkillResult result = skills.get(0).execute(request);
Map<String, Object> outputs = result.getOutputs();
```

### Custom Skill Implementation

```java
public class CustomWorkletSkill implements A2ASkill {
    private final DspyProgramRegistry registry;
    
    public CustomWorkletSkill(DspyProgramRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public String getId() {
        return "custom_worklet_selector";
    }
    
    @Override
    public String getName() {
        return "Custom Worklet Selector";
    }
    
    @Override
    public String getDescription() {
        return "Intelligent worklet selection with custom business rules";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "context", Map.of("type", "string", "description", "Selection context")
            )
        );
    }
    
    @Override
    public SkillResult execute(SkillRequest request) {
        // Execute DSPy program
        DspyProgram program = registry.getProgram("custom-worklet-selector");
        Map<String, Object> inputs = Map.of("context", request.getParameter("context"));
        
        PythonDspyBridge dspy = new PythonDspyBridge(/* engine */);
        DspyExecutionResult result = dspy.execute(program, inputs);
        
        return new SkillResult(result.output());
    }
}
```

---

## Advanced Configuration

### Custom Cache Configuration

```java
// Create custom cache with 200 entries
DspyProgramCache cache = new DspyProgramCache(200);

// Create bridge with custom cache
PythonDspyBridge dspy = new PythonDspyBridge(engine, cache);

// Monitor cache usage
Map<String, Object> stats = dspy.getCacheStats();
System.out.println("Cache size: " + stats.get("cacheSize"));
System.out.println("Cache max size: " + stats.get("cacheMaxSize"));

// Clear cache if needed
dspy.clearCache();
```

### GEPA Optimizer Configuration

```java
// Load GEPA configuration from TOML file
Path configPath = Paths.get("gepa-optimization.toml");
GEPAOptimizationConfig config = GEPAOptimizationConfig.load(configPath);

// Create optimizer with custom configuration
GEPAOptimizer optimizer = new GEPAOptimizer(config);

// Optimize a DSPy program
DspyProgram optimized = optimizer.optimize(program, trainingData);

// Use optimized program
DspyExecutionResult result = dspy.execute(optimized, inputs);
```

### Error Handling

```java
try {
    DspyExecutionResult result = dspy.execute(program, inputs);
} catch (PythonException e) {
    System.err.println("DSPy execution failed:");
    System.err.println("Message: " + e.getMessage());
    System.err.println("Traceback: " + e.getTraceback());
    System.err.println("Source: " + e.getSourceCode());
    
    // Extract error details
    String errorMessage = e.getMessage();
    if (errorMessage.contains("compilation error")) {
        System.err.println("Check DSPy program syntax");
    } else if (errorMessage.contains("runtime error")) {
        System.err.println("Check input parameters");
    }
}
```

### Performance Monitoring

```java
// Monitor execution metrics
DspyExecutionResult result = dspy.execute(program, inputs);
DspyExecutionMetrics metrics = result.metrics();

// Log performance
if (metrics.totalTimeMs() > 1000) {
    logger.warn("Slow DSPy execution: {}ms", metrics.totalTimeMs());
}

// Track cache hit rate
boolean cacheHit = metrics.cacheHit();
if (cacheHit) {
    logger.debug("Cache hit for program: {}", program.name());
} else {
    logger.debug("Cache miss for program: {}", program.name());
}
```

---

## Best Practices

1. **Program Caching**: Always use the default caching to avoid recompilation
2. **Error Handling**: Catch PythonException for DSPy-specific errors
3. **Resource Management**: Close PythonExecutionEngine when done
4. **Thread Safety**: All DSPy classes are thread-safe
5. **Input Validation**: Validate inputs before execution
6. **Monitoring**: Track execution metrics for performance tuning
7. **Configuration**: Use TOML files for complex configuration
8. **Testing**: Write unit tests for custom DSPy programs

---

## Troubleshooting

### Common Issues

1. **Compilation Errors**: Check DSPy program syntax
2. **Runtime Errors**: Verify input parameter types
3. **Memory Issues**: Reduce context pool size
4. **Timeout Errors**: Increase execution timeout if needed
5. **Cache Issues**: Clear cache and retry

### Debug Mode

```java
// Enable debug logging
engine.enableDebugMode(true);

// Execute with trace
DspyProgram program = DspyProgram.builder()
    .name("debug-program")
    .source("import dspy
class DebugModule(dspy.Module):
    def forward(self, text):
        print(f\"Debug: {text}\"); return {\"result\": text}")
    .build();

DspyExecutionResult result = dspy.execute(program, Map.of("text", "test"));
```

For more information, see the full API documentation in the Javadoc.
