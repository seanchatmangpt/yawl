# Getting Started with YAWL DSPy

## What You'll Learn

In this 15-minute tutorial, you'll learn how to:
- Set up YAWL DSPy in your project
- Execute your first DSPy program from Java
- Optimize workflow decisions using GEPA
- Validate results with behavioral footprints

## Prerequisites

Before starting, ensure you have:

| Requirement | Version | How to Verify |
|-------------|---------|---------------|
| Java JDK | 25+ | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Python | 3.11+ | `python3 --version` |

## Step 1: Add Dependency

Add YAWL DSPy to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-dspy</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

## Step 2: Create a DSPy Program

Create a file `worklet_selector.py`:

```python
import dspy

class WorkletSelectionSignature(dspy.Signature):
    """Select the most appropriate worklet for a workflow context."""
    workflow_description: str = dspy.InputField(desc="Natural language description")
    case_context: str = dspy.InputField(desc="Additional case context")

    worklet_id: str = dspy.OutputField(desc="ID of the selected worklet")
    rationale: str = dspy.OutputField(desc="Why this worklet was selected")
    confidence: float = dspy.OutputField(desc="Confidence score 0-1")


class WorkletSelector(dspy.Module):
    def __init__(self):
        super().__init__()
        self.predict = dspy.ChainOfThought(WorkletSelectionSignature)

    def forward(self, workflow_description: str, case_context: str = ""):
        return self.predict(
            workflow_description=workflow_description,
            case_context=case_context
        )
```

## Step 3: Execute from Java

Create a Java class to execute your DSPy program:

```java
package com.example;

import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;

import java.util.Map;

public class WorkletSelectorExample {

    public static void main(String[] args) {
        // Create the bridge
        PythonDspyBridge bridge = new PythonDspyBridge();

        // Load the program
        DspyProgram program = bridge.loadProgram(
            "worklet_selector",
            "path/to/worklet_selector.py",
            "WorkletSelector"
        );

        // Execute with inputs
        Map<String, Object> inputs = Map.of(
            "workflow_description", "Process customer order with priority shipping",
            "case_context", "Customer is VIP, order value > $500"
        );

        DspyExecutionResult result = program.execute(inputs);

        // Print results
        System.out.println("Worklet ID: " + result.output().get("worklet_id"));
        System.out.println("Rationale: " + result.output().get("rationale"));
        System.out.println("Confidence: " + result.output().get("confidence"));
        System.out.println("Execution time: " + result.metrics().executionTimeMs() + "ms");

        // Cleanup
        bridge.shutdown();
    }
}
```

## Step 4: Optimize with GEPA

GEPA (Gradient Estimation for Prompt Architecture) optimizes your DSPy programs:

```java
import org.yawlfoundation.yawl.dspy.persistence.GepaProgramEnhancer;
import org.yawlfoundation.yawl.dspy.persistence.GepaOptimizationResult;

// Create enhancer
GepaProgramEnhancer enhancer = new GepaProgramEnhancer(bridge);

// Optimize with behavioral target
GepaOptimizationResult result = enhancer.enhanceWithGEPA(
    program,
    GepaOptimizationResult.OptimizationTarget.BEHAVIORAL,
    trainingExamples  // List of historical workflow examples
);

// Check optimization quality
if (result.footprintAgreement() >= 0.95) {
    System.out.println("Optimization successful!");
    System.out.println("Score: " + result.score());
}
```

## Step 5: Validate with Footprints

Validate that generated workflows match expected behavior:

```java
import org.yawlfoundation.yawl.dspy.validation.PerfectWorkflowValidator;

PerfectWorkflowValidator validator = new PerfectWorkflowValidator(true);

ValidationResult validation = validator.validatePerfectWorkflow(
    generatedWorkflow,   // The YNet produced by DSPy
    referenceWorkflow,   // The expected YNet
    GepaOptimizationResult.OptimizationTarget.BEHAVIORAL
);

if (validation.isPerfectGeneration()) {
    System.out.println("Perfect workflow generated!");
} else {
    System.out.println("Issues: " + validation.errors());
}
```

## What You've Learned

You've successfully:
- ✅ Added YAWL DSPy to your project
- ✅ Created a DSPy program for worklet selection
- ✅ Executed the program from Java
- ✅ Optimized with GEPA
- ✅ Validated results with behavioral footprints

## Next Steps

- **[Optimize Performance](../how-to/optimize-performance.md)** - Learn performance tuning
- **[Create MCP Tools](../how-to/create-mcp-tools.md)** - Expose DSPy via MCP
- **[Understand GEPA](../explanation/gepa-architecture.md)** - Deep dive into optimization

## Troubleshooting

### "PythonException: Module not found"

Ensure Python path includes your DSPy modules:
```java
System.setProperty("python.path", "/path/to/your/modules");
```

### "GraalPy context not available"

Add GraalPy dependency:
```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.0</version>
</dependency>
```

### "DSPy not found"

Install DSPy in your Python environment:
```bash
pip install dspy-ai
```
