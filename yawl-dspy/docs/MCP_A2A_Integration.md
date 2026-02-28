# MCP Tools and A2A Skills Documentation

This document provides detailed information about the Model Context Protocol (MCP) tools and Autonomous Agent to Agent (A2A) skills integration in the YAWL DSPy module.

## Table of Contents
1. [MCP Tools](#mcp-tools)
2. [A2A Skills](#a2a-skills)
3. [Configuration](#configuration)
4. [Examples](#examples)
5. [Best Practices](#best-practices)

---

## MCP Tools

### Overview

MCP tools expose YAWL DSPy programs as callable tools for LLM clients. This enables seamless integration with AI assistants and other MCP-compatible systems.

### Available Tools

#### 1. dspy_execute_program

Executes a saved DSPy program with the provided inputs.

**Request Format:**
```json
{
  "tool": "dspy_execute_program",
  "arguments": {
    "program": "program_name",
    "inputs": {
      "param1": "value1",
      "param2": "value2"
    }
  }
}
```

**Response Format:**
```json
{
  "output": {
    "result_key": "result_value"
  },
  "metrics": {
    "execution_time_ms": 150,
    "cache_hit": true
  }
}
```

**Example:**
```json
// Request
{
  "tool": "dspy_execute_program",
  "arguments": {
    "program": "sentiment-analyzer",
    "inputs": {
      "text": "This is a positive message about YAWL"
    }
  }
}

// Response
{
  "sentiment": "positive",
  "confidence": 0.92,
  "rationale": "The message expresses positive sentiment about YAWL"
}
```

#### 2. dspy_list_programs

Lists all available DSPy programs in the registry.

**Request Format:**
```json
{
  "tool": "dspy_list_programs"
}
```

**Response Format:**
```json
{
  "programs": [
    {
      "name": "sentiment-analyzer",
      "description": "Analyzes text sentiment",
      "source_hash": "abc123...",
      "created_at": "2024-01-01T00:00:00Z",
      "tags": ["nlp", "sentiment"]
    }
  ]
}
```

#### 3. dspy_get_program_info

Retrieves detailed information about a specific program.

**Request Format:**
```json
{
  "tool": "dspy_get_program_info",
  "arguments": {
    "program": "sentiment-analyzer"
  }
}
```

**Response Format:**
```json
{
  "program": {
    "name": "sentiment-analyzer",
    "description": "Analyzes text sentiment using DSPy ChainOfThought",
    "source_hash": "abc123...",
    "created_at": "2024-01-01T00:00:00Z",
    "input_schema": {
      "type": "object",
      "properties": {
        "text": {"type": "string"}
      }
    },
    "output_schema": {
      "type": "object", 
      "properties": {
        "sentiment": {"type": "string"},
        "confidence": {"type": "number"}
      }
    },
    "tags": ["nlp", "sentiment"],
    "execution_count": 42,
    "avg_execution_time_ms": 120
  }
}
```

#### 4. dspy_reload_program

Hot-reloads a program from disk (useful for development).

**Request Format:**
```json
{
  "tool": "dspy_reload_program",
  "arguments": {
    "program": "sentiment-analyzer",
    "new_source": "updated Python source code..."
  }
}
```

**Response Format:**
```json
{
  "success": true,
  "message": "Program reloaded successfully",
  "cache_cleared": true,
  "reloaded_at": "2024-01-01T00:00:00Z"
}
```

---

## A2A Skills

### Overview

A2A skills wrap DSPy programs as autonomous agent skills, enabling programmatic access between agents in the YAWL ecosystem.

### Available Skills

#### 1. dspy_worklet_selector

**Description:** ML-optimized worklet selection for YAWL workflows.

**Parameters:**
- `context` (string): Selection context including task name, case data, and available worklets

**Output:**
- `worklet_id` (string): Selected worklet identifier
- `confidence` (number): Selection confidence (0-1)
- `rationale` (string): Reasoning for the selection

**Example Usage:**
```java
// Create skill
DspyA2ASkill workletSkill = new DspyA2ASkill(
    "worklet-selector",
    "Worklet Selector",
    "Intelligently selects optimal worklet based on task and case context",
    registry
);

// Execute skill
SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
    .parameter("context", "Task: Review, Case: {urgency: high, amount: $25,000}")
    .build();

SkillResult result = workletSkill.execute(request);

// Access results
Map<String, Object> outputs = result.getOutputs();
String workletId = (String) outputs.get("worklet_id");
double confidence = (Double) outputs.get("confidence");
```

#### 2. dspy_resource_router

**Description:** Predictive resource allocation for workflow tasks.

**Parameters:**
- `task_type` (string): Type of task to execute
- `required_capabilities` (array): Required capabilities for the task
- `agent_historical_scores` (object): Historical performance scores for agents
- `current_queue_depth` (number): Current task queue depth

**Output:**
- `best_agent_id` (string): Predicted best agent
- `confidence` (number): Prediction confidence (0-1)
- `reasoning` (string): Reasoning for the prediction

#### 3. dspy_anomaly_forensics

**Description:** Root cause analysis for workflow anomalies.

**Parameters:**
- `metric_name` (string): Name of the anomalous metric
- `duration_ms` (number): Duration of the anomaly
- `deviation_factor` (number): Deviation from baseline
- `recent_samples` (array): Recent metric samples
- `concurrent_cases` (number): Number of concurrent cases

**Output:**
- `root_cause` (string): Identified root cause
- `confidence` (number): Analysis confidence (0-1)
- `evidence_chain` (array): Supporting evidence
- `recommendation` (string): Recommended action

#### 4. dspy_runtime_adaptation

**Description:** Autonomous workflow adaptation using DSPy ReAct.

**Parameters:**
- `case_id` (string): Workflow case identifier
- `spec_id` (string): Workflow specification identifier
- `bottleneck_score` (number): Current bottleneck score
- `enabled_tasks` (array): List of enabled tasks
- `busy_tasks` (array): List of busy tasks
- `queue_depth` (number): Current queue depth
- `available_agents` (array): List of available agents

**Output:**
- `action_type` (string): Type of adaptation action
- `task_id` (string): Target task (if applicable)
- `agent_id` (string): Agent ID (if applicable)
- `alternate_route` (string): Alternate route (if applicable)
- `escalation_level` (string): Escalation level (if applicable)
- `reasoning` (string): Reasoning for the action

---

## Configuration

### MCP Server Configuration

```java
// Create program registry
DspyProgramRegistry registry = new DspyProgramRegistry();

// Register programs
DspyProgram sentimentProgram = DspyProgram.builder()
    .name("sentiment-analyzer")
    .source("...")
    .build();
registry.save(sentimentProgram);

// Create MCP server
McpServer server = McpServer.builder()
    .name("yawl-dspy")
    .version("6.0.0")
    .description("DSPy program execution for YAWL workflows")
    .tools(DspyMcpTools.createAll(registry))
    .build();
```

### A2A Skills Configuration

```java
// Create skill registry
DspyProgramRegistry registry = new DspyProgramRegistry();

// Register all programs
List<DspySavedProgram> savedPrograms = registry.getAllPrograms();

// Create skills from registry
List<A2ASkill> skills = new ArrayList<>();
for (DspySavedProgram saved : savedPrograms) {
    DspyA2ASkill skill = new DspyA2ASkill(
        saved.getName(),
        "DSPy " + saved.getName(),
        saved.getDescription(),
        registry
    );
    skills.add(skill);
}

// Register skills with A2A framework
for (A2ASkill skill : skills) {
    a2aFramework.registerSkill(skill);
}
```

### Configuration File

Create `dspy-integration.toml`:

```toml
[mcp]
enabled = true
port = 8080
host = "localhost"
max_concurrent_requests = 10
request_timeout_ms = 30000

[a2a]
enabled = true
skills_auto_register = true
skill_discovery_interval = 30000

[programs]
registry_path = "programs.json"
cache_size = 100
auto_reload = false
reload_interval = 60000

[logging]
level = "INFO"
mcp_requests = true
a2a_executions = true
program_metrics = true
```

---

## Examples

### MCP Tool Example (Python)

```python
import asyncio
import mcp

async def execute_sentiment_analysis():
    # Connect to MCP server
    async with mcp.connect("ws://localhost:8080") as client:
        # Execute sentiment analysis
        result = await client.call_tool("dspy_execute_program", {
            "program": "sentiment-analyzer",
            "inputs": {
                "text": "YAWL makes workflow management easy!"
            }
        })
        
        print("Sentiment:", result["sentiment"])
        print("Confidence:", result["confidence"])

# Run the example
asyncio.run(execute_sentiment_analysis())
```

### A2A Skill Example (Java)

```java
// In autonomous agent implementation
public class WorkflowAgent implements AutonomousAgent {
    
    private final List<A2ASkill> skills;
    
    public WorkflowAgent() {
        // Initialize skills
        DspyProgramRegistry registry = new DspyProgramRegistry();
        this.skills = DspyA2ASkill.createAll(registry);
    }
    
    public void processWorkletSelection(WorkletSelectionContext context) {
        // Find worklet selector skill
        A2ASkill skill = skills.stream()
            .filter(s -> s.getId().equals("dspy_worklet_selector"))
            .findFirst()
            .orElseThrow();
        
        // Execute skill
        SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
            .parameter("context", context.toJson())
            .build();
            
        SkillResult result = skill.execute(request);
        
        // Process result
        Map<String, Object> outputs = result.getOutputs();
        String selectedWorklet = (String) outputs.get("worklet_id");
        double confidence = (Double) outputs.get("confidence");
        
        // Make decision based on confidence
        if (confidence > 0.8) {
            assignWorklet(context.caseId(), selectedWorklet);
        } else {
            escalateToHuman(context.caseId(), "Low confidence selection");
        }
    }
}
```

### Combined MCP + A2A Example

```java
// MCP server that can also be used as A2A skill provider
public class DspyMcpServer {
    
    private final McpServer mcpServer;
    private final A2AService a2aService;
    
    public DspyMcpServer(DspyProgramRegistry registry) {
        // Create MCP server
        this.mcpServer = McpServer.builder()
            .name("yawl-dspy")
            .version("6.0.0")
            .tools(DspyMcpTools.createAll(registry))
            .build();
            
        // Create A2A service
        this.a2aService = new A2AService(DspyA2ASkill.createAll(registry));
    }
    
    public void start() {
        // Start MCP server
        mcpServer.start();
        
        // Register A2A skills
        a2aService.registerAllSkills();
    }
    
    public void stop() {
        mcpServer.stop();
        a2aService.unregisterAllSkills();
    }
}
```

---

## Best Practices

### MCP Tools Best Practices

1. **Tool Naming**: Use descriptive names that indicate the program's purpose
2. **Input Validation**: Validate inputs before execution
3. **Error Handling**: Handle execution errors gracefully
4. **Caching**: Leverage the built-in program caching
5. **Monitoring**: Track execution metrics and performance
6. **Documentation**: Provide clear descriptions and examples

```java
// Example with best practices
public class RobustMcpToolHandler {
    
    private final PythonDspyBridge dspy;
    private final DspyProgramRegistry registry;
    
    public Object handleExecuteProgram(String programName, Map<String, Object> inputs) {
        try {
            // Validate inputs
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalArgumentException("Inputs cannot be null or empty");
            }
            
            // Get program
            DspyProgram program = registry.getProgram(programName);
            if (program == null) {
                throw new IllegalArgumentException("Program not found: " + programName);
            }
            
            // Execute with metrics
            DspyExecutionResult result = dspy.execute(program, inputs);
            
            // Check for errors
            if (result.output().isEmpty()) {
                logger.warn("Empty output for program: " + programName);
            }
            
            return result;
            
        } catch (PythonException e) {
            logger.error("DSPy execution failed for program " + programName, e);
            throw new McpExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }
}
```

### A2A Skills Best Practices

1. **Skill Design**: Design skills with specific, well-defined purposes
2. **Parameter Validation**: Validate skill parameters before execution
3. **Result Processing**: Process results based on confidence scores
4. **Fallback Mechanisms**: Provide fallback behavior for low confidence
5. **Skill Discovery**: Implement proper skill discovery mechanisms
6. **Performance Monitoring**: Monitor skill execution performance

```java
// Example with best practices
public class RobustA2ASkill implements A2ASkill {
    
    private final DspyProgramRegistry registry;
    private final String programName;
    private final double confidenceThreshold;
    
    @Override
    public SkillResult execute(SkillRequest request) {
        try {
            // Validate request
            if (request == null) {
                throw new IllegalArgumentException("Request cannot be null");
            }
            
            // Get required parameter
            String context = request.getParameter("context");
            if (context == null || context.isBlank()) {
                throw new IllegalArgumentException("Context parameter is required");
            }
            
            // Execute DSPy program
            DspyProgram program = registry.getProgram(programName);
            Map<String, Object> inputs = Map.of("context", context);
            DspyExecutionResult result = dspy.execute(program, inputs);
            
            // Check confidence
            Map<String, Object> output = result.output();
            Double confidence = extractConfidence(output);
            
            if (confidence != null && confidence < confidenceThreshold) {
                // Low confidence - generate fallback result
                Map<String, Object> fallback = generateFallbackResult(context);
                return new SkillResult(fallback, SkillResult.Status.WARNING);
            }
            
            return new SkillResult(output);
            
        } catch (Exception e) {
            logger.error("Skill execution failed for " + getId(), e);
            return new SkillResult(Map.of("error", e.getMessage()), 
                                 SkillResult.Status.ERROR);
        }
    }
    
    private Double extractConfidence(Map<String, Object> output) {
        // Extract confidence from output, handle various formats
        if (output.containsKey("confidence")) {
            return ((Number) output.get("confidence")).doubleValue();
        }
        if (output.containsKey("score")) {
            return ((Number) output.get("score")).doubleValue();
        }
        return null;
    }
    
    private Map<String, Object> generateFallbackResult(String context) {
        // Generate conservative fallback result
        return Map.of(
            "worklet_id", "StandardTrack",
            "confidence", 0.5,
            "rationale", "Fallback selection based on standard procedure",
            "is_fallback", true
        );
    }
}
```

### Performance Optimization

1. **Program Caching**: Always use the built-in LRU cache
2. **Context Pooling**: Reuse Python execution contexts
3. **Batch Processing**: Execute multiple related programs together
4. **Async Execution**: Use non-blocking execution where possible
5. **Resource Management**: Properly close resources when done

```java
// Performance optimization example
public class OptimizedDspyService {
    
    private final PythonDspyBridge dspy;
    private final ExecutorService executor;
    
    public void executeBatch(List<BatchExecutionTask> tasks) {
        // Submit all tasks to executor
        List<Future<DspyExecutionResult>> futures = tasks.stream()
            .map(task -> executor.submit(() -> dspy.execute(task.program(), task.inputs())))
            .collect(Collectors.toList());
            
        // Collect results
        List<DspyExecutionResult> results = new ArrayList<>();
        for (Future<DspyExecutionResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("Batch execution failed", e);
                results.add(createErrorResult(e));
            }
        }
        
        return results;
    }
}
```

---

## Troubleshooting

### Common Issues

1. **Program Not Found**: Check if the program is registered in the registry
2. **Execution Timeout**: Increase timeout or optimize program performance
3. **Memory Issues**: Reduce context pool size or clear cache
4. **Type Errors**: Ensure input/output types match the program expectations
5. **Connection Issues**: Check MCP server connection and configuration

### Debug Mode

```java
// Enable debug logging
DspyProgramRegistry registry = new DspyProgramRegistry();
registry.setDebugMode(true);

// Execute with trace
PythonDspyBridge dspy = new PythonDspyBridge(engine);
dspy.enableTracing(true);

// Monitor cache usage
Map<String, Object> cacheStats = dspy.getCacheStats();
System.out.println("Cache usage: " + cacheStats);
```

### Performance Monitoring

```java
// Monitor execution metrics
@EventListener
public void onDspyExecution(DspyExecutionEvent event) {
    DspyExecutionMetrics metrics = event.getMetrics();
    
    if (metrics.totalTimeMs() > 1000) {
        logger.warn("Slow execution: {}ms for {}", 
                   metrics.totalTimeMs(), event.getProgramName());
    }
    
    if (!metrics.cacheHit()) {
        logger.debug("Cache miss for {}", event.getProgramName());
    }
}
```

For more information, see the full API documentation in the Javadoc.
