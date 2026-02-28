# YAWL v6.0.0 DSPy Module

[![Java](https://img.shields.io/badge/Java-25+-blue.svg)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-green.svg)](https://maven.apache.org/)
[![DSPy](https://img.shields.io/badge/DSPy-2.5-orange.svg)](https://dspy.ai/)
[![License](https://img.shields.io/badge/License-LGPLv3-yellow.svg)](https://www.gnu.org/licenses/lgpl-3.0.en.html)

The YAWL DSPy module provides a Java API for integrating DSPy (Declarative Self-Improving Language Models) into YAWL workflows. This module enables LLM-powered workflow optimization, intelligent worklet selection, anomaly forensics, and runtime adaptation using declarative programming patterns.

## 🚀 Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-dspy</artifactId>
    <version>6.0.0</version>
</dependency>
```

### Basic Usage

```java
// Initialize the Python bridge
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .contextPoolSize(4)
    .build();
PythonDspyBridge dspy = new PythonDspyBridge(engine);

// Create a DSPy program
DspyProgram sentimentProgram = DspyProgram.builder()
    .name("sentiment-analyzer")
    .source("""
        import dspy
        class SentimentAnalyzer(dspy.Module):
            def __init__(self):
                self.classify = dspy.ChainOfThought("text -> sentiment")
            def forward(self, text):
                return self.classify(text=text)
        """)
    .build();

// Execute the program
Map<String, Object> inputs = Map.of("text", "YAWL is fantastic!");
DspyExecutionResult result = dspy.execute(sentimentProgram, inputs);

// Access results
Map<String, Object> output = result.output();
String sentiment = (String) output.get("sentiment");
DspyExecutionMetrics metrics = result.metrics();
```

## 📚 Documentation Structure

This documentation follows the [Diátaxis framework](https://diataxis.fr/), organizing content into four distinct modes:

| Mode | Purpose | Audience |
|------|---------|----------|
| **[Tutorials](docs/tutorials/)** | Learning-oriented | Beginners |
| **[How-to Guides](docs/how-to/)** | Problem-oriented | Practitioners |
| **[Reference](docs/reference/)** | Information-oriented | Users |
| **[Explanation](docs/explanation/)** | Understanding-oriented | Architects |

### Quick Navigation

- **New to YAWL DSPy?** Start with the [Getting Started Tutorial](docs/tutorials/getting-started.md)
- **Have a specific problem?** Browse [How-to Guides](docs/how-to/) for step-by-step solutions
- **Looking for API details?** Check the [Reference Documentation](docs/reference/) for complete API specs
- **Want to understand the architecture?** Read the [Explanation](docs/explanation/) section for design decisions

## 🎯 Key Features

### Core DSPy Integration
- **🔄 Automatic Compilation Caching**: Compiled DSPy programs are cached in memory (LRU, max 100 entries)
- **⚡ Concurrent Execution**: Thread-safe bridge with context pooling for parallel execution
- **📊 Comprehensive Metrics**: Compilation time, execution time, token counts, quality scores
- **🔍 Anomaly Forensics**: Root cause analysis for workflow anomalies using DSPy MultiChainComparison
- **🎯 Worklet Selection**: ML-optimized worklet selection with BootstrapFewShot
- **🤖 Runtime Adaptation**: Autonomous workflow adaptation using DSPy ReAct agents

### Integration Capabilities
- **🌐 MCP Tools**: Integration with Model Context Protocol for LLM access
- **🔄 A2A Skills**: Autonomous Agent to Agent integration skills
- **📦 Program Registry**: Persistent program storage and management
- **🔧 GEPA Optimizer**: Gradient estimation for prompt architecture optimization

## 📦 Package Structure

### Core Packages
- **`org.yawlfoundation.yawl.dspy`** - Main DSPy execution API
- **`org.yawlfoundation.yawl.dspy.forensics`** - Anomaly root cause analysis
- **`org.yawlfoundation.yawl.dspy.worklets`** - Worklet selection and routing
- **`org.yawlfoundation.yawl.dspy.adaptation`** - Runtime workflow adaptation
- **`org.yawlfoundation.yawl.dspy.resources`** - Resource prediction and routing
- **`org.yawlfoundation.yawl.dspy.learning`** - Learning and training components
- **`org.yawlfoundation.yawl.dspy.mcp`** - Model Context Protocol integration
- **`org.yawlfoundation.yawl.dspy.a2a`** - Autonomous Agent to Agent skills
- **`org.yawlfoundation.yawl.dspy.persistence`** - Program registry and persistence
- **`org.yawlfoundation.yawl.dspy.validation`** - Perfect workflow validation

## 🎯 Use Cases

### 1. Intelligent Worklet Selection
```java
WorkletSelectionContext context = new WorkletSelectionContext(
    "Review",
    Map.of("urgency", "high", "complexity", "medium"),
    List.of("StandardTrack", "FastTrack", "ExpertTrack"),
    List.of("case_123: FastTrack", "case_124: StandardTrack")
);

WorkletSelection selection = dspy.selectWorklet(context);
```

### 2. Anomaly Forensics
```java
AnomalyContext anomaly = new AnomalyContext(
    "task_processing_latency",
    5000L,  // persisted duration
    3.2,    // deviation factor
    List.of(145L, 152L, 148L, 160L, 210L, 475L, 510L, 495L),
    8       // concurrent cases
);

ForensicsReport report = dspy.runForensics(anomaly);
```

### 3. Runtime Adaptation
```java
WorkflowAdaptationContext adaptation = new WorkflowAdaptationContext(
    "case_125", "spec_workflow_1", 0.85,
    List.of("Review", "Approve", "Execute"),
    List.of("Review", "Execute"), 12, 45L,
    List.of("agent-1", "agent-2"),
    "task_completed", Map.of("task_id", "Review")
);

AdaptationAction action = dspy.executeReActAgent(adaptation);
```

## 🔧 Configuration

### GEPA Optimizer Configuration
Create `gepa-optimization.toml`:
```toml
[optimization]
target = "balanced"
auto_mode = "medium"
max_iterations = 100
convergence_threshold = 0.001

[behavioral]
footprint_validation = true
perfect_agreement_required = true
behavioral_weight = 0.7

[performance]
execution_time_weight = 0.4
resource_utilization_weight = 0.3
throughput_weight = 0.3
```

### Integration Configuration
Create `dspy-integration.toml`:
```toml
[mcp]
enabled = true
port = 8080
max_concurrent_requests = 10

[a2a]
enabled = true
skills_auto_register = true

[programs]
registry_path = "programs.json"
cache_size = 100
```

## 📊 Performance & Metrics

### Execution Metrics
```java
DspyExecutionResult result = dspy.execute(program, inputs);
DspyExecutionMetrics metrics = result.metrics();

System.out.println("Total time: " + metrics.totalTimeMs() + "ms");
System.out.println("Cache hit: " + metrics.cacheHit());
System.out.println("Total tokens: " + metrics.totalTokens());
```

### Performance Characteristics
- **First Execution**: ~500-1000ms (compilation + execution)
- **Cached Execution**: ~50-200ms (execution only)
- **Memory Usage**: ~2-5MB per DSPy program instance
- **Thread Safety**: All public methods are thread-safe
- **Concurrent Support**: Multiple programs can execute simultaneously

## 🌐 MCP Tools Integration

### Available Tools
1. **`dspy_execute_program`** - Execute a saved DSPy program
2. **`dspy_list_programs`** - List all available programs
3. **`dspy_get_program_info`** - Get detailed program information
4. **`dspy_reload_program`** - Hot-reload a program from disk

### Example Usage
```json
{
  "tool": "dspy_execute_program",
  "arguments": {
    "program": "worklet_selector",
    "inputs": {
      "context": "Task: Review, Case: {urgency: high}"
    }
  }
}
```

## 🤖 A2A Skills Integration

### Available Skills
1. **`dspy_worklet_selector`** - ML-optimized worklet selection
2. **`dspy_resource_router`** - Predictive resource allocation
3. **`dspy_anomaly_forensics`** - Root cause analysis
4. **`dspy_runtime_adaptation`** - Autonomous workflow adaptation

### Example Usage
```java
List<A2ASkill> skills = DspyA2ASkill.createAll(registry);
SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
    .parameter("context", "Task: Review, Case: {urgency: high}")
    .build();
SkillResult result = skills.get(0).execute(request);
```

## 🔍 Error Handling

### Python Exceptions
```java
try {
    DspyExecutionResult result = dspy.execute(program, inputs);
} catch (PythonException e) {
    System.err.println("DSPy execution failed:");
    System.err.println("Message: " + e.getMessage());
    System.err.println("Traceback: " + e.getTraceback());
}
```

### Common Error Patterns
1. **Compilation Errors**: Invalid DSPy program syntax
2. **Runtime Errors**: Missing input parameters or invalid data types
3. **Resource Errors**: Insufficient Python context memory
4. **Timeout Errors**: Program execution exceeded time limits

## 🧪 Examples

### Java Examples
- **SentimentAnalysisExample.java** - Basic DSPy program execution
- **WorkletSelectionExample.java** - Intelligent worklet selection
- **AnomalyForensicsExample.java** - Root cause analysis

### Python Examples
- **SentimentAnalysisExample.py** - DSPy program templates
- **ResourceRoutingExample.py** - Resource prediction programs

## 📄 Examples

See the [examples/](examples/) directory for complete working examples.

## 📚 Documentation

### API Reference
- **[Javadoc](javadoc/)** - Complete API documentation
- **[API Usage Examples](docs/API_Usage_Examples.md)** - Comprehensive usage examples
- **[MCP/A2A Integration](docs/MCP_A2A_Integration.md)** - Integration guide

### External Resources
- **[DSPy Documentation](https://dspy.ai/)** - DSPy framework documentation
- **[GraalPy Documentation](https://www.graalvm.org/)** - GraalPy Python integration
- **[YAWL Foundation](https://yawl.sourceforge.net/)** - YAWL workflow engine

## 🔧 Requirements

- **Java 25+** (with records, sealed classes, pattern matching)
- **Python 3.11+** (via GraalPy)
- **YAWL Engine 6.0.0+**
- **DSPy 2.5+**
- **Maven 3.8+**

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Add comprehensive tests
4. Update documentation
5. Submit a pull request

### Development Guidelines

- Follow Java 25 conventions
- Include Javadoc for all public APIs
- Write comprehensive unit and integration tests
- Use SLF4J for logging
- Follow YAWL coding standards

## 📄 License

This project is licensed under the GNU Lesser General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Projects

- **[YAWL Engine](../yawl-engine/)** - Core YAWL workflow engine
- **[YAWL Integration](../yawl-integration/)** - A2A and MCP integration
- **[YAWL Observability](../yawl-observability/)** - Monitoring and metrics
- **[GEPA Optimizer](../gepa-optimizer/)** - Gradient estimation for prompt architecture

---

**Author**: YAWL Foundation  
**Version**: 6.0.0  
**Since**: 6.0.0  
**License**: LGPL v3.0
