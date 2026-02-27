# Tutorial 1: Getting Started with GraalPy

## Goal

Execute a Python script from within a YAWL workflow task handler, computing a case risk score and returning the result to Java. By the end, you'll have a working `PythonExecutionEngine` embedded in your YAWL code.

## Prerequisites

- **GraalVM JDK 24.1 or later** with Python language support
  ```bash
  sdk install java 24.1.0-graal
  gu install python
  ```
- **Maven** 3.8+
- **Familiarity** with Java, Maven dependency management, and basic Python
- A YAWL workflow module with a task completion handler

## Steps

### Step 1: Add Maven Dependency

Add yawl-graalpy to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalpy</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

Run Maven to download and cache the dependency:

```bash
mvn clean install -U
```

### Step 2: Create a PythonExecutionEngine

In your task handler class, instantiate the Python engine using the builder pattern:

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

public class RiskAssessmentHandler {

    public void assessRisk(YWorkItem item) {
        // Create the engine with a pool of 4 contexts
        PythonExecutionEngine engine = PythonExecutionEngine.builder()
            .poolSize(4)
            .sandboxConfig(PythonSandboxConfig.strict())
            .build();

        try {
            // Use the engine here (see next steps)
        } finally {
            engine.close();  // Always close to release resources
        }
    }
}
```

The `strict()` sandbox prevents Python code from accessing the file system or network, making it safe for untrusted scripts.

### Step 3: Evaluate a Simple Expression

Add a simple Python expression evaluation to your engine:

```java
try {
    String greeting = engine.evalToString("'Hello, ' + 'YAWL!'");
    System.out.println(greeting);  // Output: Hello, YAWL!
} finally {
    engine.close();
}
```

The `evalToString()` method evaluates a Python expression and returns the result as a `String`. Python's string objects are automatically converted to Java `String`.

### Step 4: Run a Multi-Line Python Script

Define and call a Python function within your engine:

```java
try {
    // Define a Python function that calculates risk
    String script = """
        def calculate_risk(case_age_days, priority_level):
            base_risk = 0.3 + (case_age_days / 365.0) * 0.5
            priority_multiplier = 1.0 if priority_level == 'low' else 1.5 if priority_level == 'medium' else 2.0
            return base_risk * priority_multiplier
        """;

    engine.eval(script);  // Load the function into Python context

    // Now call the function
    double riskScore = engine.evalToDouble(
        "calculate_risk(" + item.getAge() + ", '" + item.getPriority() + "')"
    );

    System.out.println("Risk Score: " + riskScore);
    item.setRiskScore(riskScore);
} finally {
    engine.close();
}
```

The `eval()` method loads Python code but returns nothing. Use `evalToDouble()` to get numeric results. Python's `float` type is automatically converted to Java `double`.

### Step 5: Get a Python Dictionary as a Java Map

Python dictionaries are common for returning structured data. Retrieve them as Java `Map`:

```java
try {
    // Define a Python function that returns analysis as a dict
    String analysisScript = """
        def analyze_case(case_id, case_age):
            return {
                'case_id': case_id,
                'age_days': case_age,
                'status': 'high_priority' if case_age > 30 else 'normal',
                'escalation_required': case_age > 60
            }
        """;

    engine.eval(analysisScript);

    // Call the function and get the result as a Map
    Map<String, Object> analysis = engine.evalToMap(
        "analyze_case('" + item.getId() + "', " + item.getAge() + ")"
    );

    String status = (String) analysis.get("status");
    Boolean escalationRequired = (Boolean) analysis.get("escalation_required");

    System.out.println("Status: " + status);
    System.out.println("Escalation: " + escalationRequired);

    item.setStatus(status);
    item.setEscalationRequired(escalationRequired);
} finally {
    engine.close();
}
```

Python dicts use `hasHashEntries()` internally. The `evalToMap()` method handles this marshalling automatically.

### Step 6: Get a Python List as a Java List

Python lists become Java `List<Object>`. Retrieve them as follows:

```java
try {
    String listScript = """
        def get_recommendations(risk_score):
            if risk_score > 0.8:
                return ['escalate', 'notify_manager', 'hold_payment']
            elif risk_score > 0.5:
                return ['review', 'request_info']
            else:
                return ['approve', 'process']
        """;

    engine.eval(listScript);

    List<Object> recommendations = engine.evalToList(
        "get_recommendations(" + riskScore + ")"
    );

    System.out.println("Recommendations: " + recommendations);
    item.setRecommendations((List<String>) (List<?>) recommendations);
} finally {
    engine.close();
}
```

The `evalToList()` method returns a `List<Object>`. Cast elements to the appropriate Java type.

### Step 7: Handle Python Exceptions

Wrap engine calls in try/catch to handle Python runtime errors:

```java
try {
    String badScript = "result = 1 / 0";  // Division by zero
    engine.eval(badScript);
} catch (PythonException e) {
    // Log the error and handle gracefully
    System.err.println("Python error: " + e.getMessage());
    // e.getMessage() includes the Python traceback
    item.setError("Risk calculation failed: " + e.getMessage());
} finally {
    engine.close();
}
```

`PythonException` is raised when Python code fails. The exception message includes the Python traceback, making debugging easier.

### Step 8: Use Try-With-Resources for Automatic Cleanup

Simplify resource management by using try-with-resources (since `PythonExecutionEngine` is `AutoCloseable`):

```java
public void assessRisk(YWorkItem item) {
    try (PythonExecutionEngine engine = PythonExecutionEngine.builder()
            .poolSize(4)
            .sandboxConfig(PythonSandboxConfig.strict())
            .build()) {

        String script = """
            def comprehensive_analysis(case_data):
                return {
                    'risk': 0.65,
                    'actions': ['review', 'approve'],
                    'confidence': 0.92
                }
            """;

        engine.eval(script);

        Map<String, Object> result = engine.evalToMap(
            "comprehensive_analysis('" + item.getId() + "')"
        );

        double risk = ((Number) result.get("risk")).doubleValue();
        item.setRiskScore(risk);
        item.setStatus("analyzed");

    } catch (PythonException e) {
        System.err.println("Analysis failed: " + e.getMessage());
        item.setStatus("analysis_failed");
    }
}
```

The try-with-resources statement automatically calls `engine.close()` when the block exits, even if an exception occurs.

### Step 9: Complete Example in a YAWL Task Handler

Here's a realistic example in a YWorkItemCompleted handler:

```java
import org.yawl.engine.domain.YWorkItem;
import org.yawl.engine.interfce.WorkItemCompleteListener;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;
import org.yawlfoundation.yawl.graalpy.PythonException;

public class CaseRiskEvaluator implements WorkItemCompleteListener {

    @Override
    public void workItemCompleted(YWorkItem item) {
        try (PythonExecutionEngine engine = PythonExecutionEngine.builder()
                .poolSize(4)
                .sandboxConfig(PythonSandboxConfig.strict())
                .build()) {

            // Load the Python analysis module
            String analysisModule = """
                import math

                def evaluate_case_risk(case_age_days, priority, amount):
                    '''Evaluate case risk using domain expertise.'''
                    # Risk increases with age
                    age_risk = min(case_age_days / 365.0, 1.0)

                    # Priority multiplier
                    priority_weights = {'low': 1.0, 'medium': 1.5, 'high': 2.5}
                    priority_mult = priority_weights.get(priority, 1.0)

                    # Amount factor
                    amount_risk = math.log(amount + 1) / 10.0

                    # Combined risk score [0.0, 1.0]
                    total_risk = min(age_risk + amount_risk, 1.0) * priority_mult
                    return min(total_risk, 1.0)

                def get_decision(risk_score):
                    '''Return action based on risk score.'''
                    if risk_score > 0.7:
                        return 'ESCALATE'
                    elif risk_score > 0.4:
                        return 'REVIEW'
                    else:
                        return 'APPROVE'
                """;

            engine.eval(analysisModule);

            // Extract case properties
            String caseAge = item.getDataVariable("caseAge");
            String priority = item.getDataVariable("priority");
            String amount = item.getDataVariable("amount");

            // Evaluate risk
            double riskScore = engine.evalToDouble(
                String.format("evaluate_case_risk(%s, '%s', %s)",
                    caseAge, priority, amount)
            );

            // Get decision
            String decision = engine.evalToString(
                String.format("get_decision(%f)", riskScore)
            );

            // Update work item with results
            item.setDataVariable("riskScore", String.valueOf(riskScore));
            item.setDataVariable("decision", decision);

            System.out.println("Case " + item.getId()
                + ": Risk=" + riskScore + ", Decision=" + decision);

        } catch (PythonException e) {
            System.err.println("Risk evaluation failed for case "
                + item.getId() + ": " + e.getMessage());
            item.setDataVariable("decision", "MANUAL_REVIEW");
        }
    }
}
```

---

## What You Built

You've created a **Python-in-Java evaluator** for YAWL workflow task handlers. Your `RiskAssessmentHandler` can now:

1. Define Python functions directly in your handler
2. Evaluate Python expressions and get results back as Java objects
3. Pass case data to Python for analysis
4. Handle Python errors gracefully
5. Automatically clean up resources via try-with-resources

The engine uses context pooling under the hood, so repeated calls reuse Python interpreter instances, keeping latency low in high-throughput workflows.

**Next steps:**
- Read [Getting Started with GraalJS](02-graaljs-getting-started.md) to combine JavaScript rules with Python analytics
- Explore the How-To guides for loading Python pip packages via virtual environments
- Consult the [GraalPy API Reference](../../reference/polyglot-graalpy-api.md) for advanced features like bytecode caching and custom marshallers
