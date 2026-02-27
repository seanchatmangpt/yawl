# Tutorial 2: Getting Started with GraalJS

## Goal

Evaluate workflow business rules written in JavaScript from a YAWL task handler, making real-time routing decisions based on case data. By the end, you'll have a working `JavaScriptExecutionEngine` that powers your workflow's decision logic.

## Prerequisites

- **GraalVM JDK 24.1 or later** with JavaScript language support
  ```bash
  sdk install java 24.1.0-graal
  gu install js
  ```
- **Maven** 3.8+
- **Familiarity** with Java, Maven, and JavaScript (ES2022)
- A YAWL workflow module with a task completion or decision handler

## Steps

### Step 1: Add Maven Dependency

Add yawl-graaljs to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graaljs</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

Run Maven to resolve dependencies:

```bash
mvn clean install -U
```

### Step 2: Create a JavaScriptExecutionEngine

In your workflow handler class, build the engine using the fluent builder:

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

public class WorkflowRoutingHandler {

    public void routeCase(YWorkItem item) {
        // Build engine with sandbox enabled
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
            .sandboxed(true)
            .contextPoolSize(4)
            .build();

        try {
            // Use the engine here (see next steps)
        } finally {
            engine.close();  // Always close to release resources
        }
    }
}
```

The `sandboxed(true)` setting restricts file I/O and network access, protecting your engine from malicious scripts.

### Step 3: Evaluate a String Expression

Start with a simple JavaScript expression to verify the engine works:

```java
try {
    String greeting = engine.evalToString("'Hello from GraalJS ' + 42");
    System.out.println(greeting);  // Output: Hello from GraalJS 42
} finally {
    engine.close();
}
```

The `evalToString()` method evaluates a JavaScript expression and converts the result to a Java `String`. JavaScript string concatenation works as expected.

### Step 4: Evaluate a Business Rule

Define a routing rule that evaluates case properties:

```java
try {
    // Load case data variables
    String caseAmount = item.getDataVariable("amount");
    String caseAge = item.getDataVariable("caseAge");

    // Evaluate routing rule
    String rule = """
        function routeCase(amount, caseAge) {
            if (amount > 10000 && caseAge > 30) {
                return 'escalate_manager';
            } else if (amount > 5000) {
                return 'standard_review';
            } else {
                return 'auto_approve';
            }
        }
        routeCase(AMOUNT, CASE_AGE);
        """.replace("AMOUNT", caseAmount).replace("CASE_AGE", caseAge);

    String decision = engine.evalToString(rule);
    System.out.println("Routing decision: " + decision);
    item.setDataVariable("route", decision);
} finally {
    engine.close();
}
```

The rule function inspects case properties and returns a routing decision. JavaScript's dynamic typing makes it easy to write flexible rules.

### Step 5: Load a JavaScript Rules File

For reusable rules, load them from a file instead of embedding them as strings:

```java
try {
    // Load rules once (typically in a static initializer or startup)
    Path rulesFile = Paths.get("src/main/resources/rules/routing.js");
    Object rulesLoaded = engine.evalScript(rulesFile);
    System.out.println("Rules loaded: " + rulesLoaded);

    // Now call functions defined in the rules file
    String decision = engine.evalToString(
        "determineRoute(" + item.getDataVariable("amount") + ", "
        + item.getDataVariable("priority") + ")"
    );

    item.setDataVariable("route", decision);
} finally {
    engine.close();
}
```

The `evalScript(Path)` method loads and evaluates a JavaScript file. Functions and variables defined in the file remain available for subsequent `eval()` calls within the same engine instance.

### Step 6: Get a JavaScript Object as a Java Map

JavaScript objects become Java `Map<String, Object>`. Retrieve and work with them:

```java
try {
    String rule = """
        function analyzeCase(caseId, amount, priority) {
            return {
                decision: amount > 5000 ? 'escalate' : 'approve',
                score: Math.random() * 100,
                recommended_reviewer: priority === 'high' ? 'senior_analyst' : 'analyst',
                requires_approval: amount > 10000
            };
        }
        analyzeCase(CASE_ID, AMOUNT, PRIORITY);
        """.replace("CASE_ID", "'" + item.getId() + "'")
          .replace("AMOUNT", item.getDataVariable("amount"))
          .replace("PRIORITY", "'" + item.getDataVariable("priority") + "'");

    Map<String, Object> analysis = engine.evalToMap(rule);

    String decision = (String) analysis.get("decision");
    Double score = ((Number) analysis.get("score")).doubleValue();
    String reviewer = (String) analysis.get("recommended_reviewer");
    Boolean requiresApproval = (Boolean) analysis.get("requires_approval");

    System.out.println("Decision: " + decision);
    System.out.println("Score: " + score);
    System.out.println("Reviewer: " + reviewer);

    item.setDataVariable("decision", decision);
    item.setDataVariable("score", String.valueOf(score));
    item.setDataVariable("reviewer", reviewer);
} finally {
    engine.close();
}
```

JavaScript objects use `hasMembers()` internally. The `evalToMap()` method automatically marshals them to Java `Map`. Note that JavaScript numbers map to `Number` in Java, so use `doubleValue()` or `intValue()` to extract typed values.

### Step 7: Get a JavaScript Array as a Java List

JavaScript arrays become Java `List<Object>`. Handle them as follows:

```java
try {
    String rule = """
        function getEscalationPath(riskLevel) {
            if (riskLevel > 80) {
                return ['notify_ceo', 'escalate_legal', 'freeze_account'];
            } else if (riskLevel > 50) {
                return ['notify_manager', 'request_docs'];
            } else {
                return ['send_email', 'update_status'];
            }
        }
        getEscalationPath(RISK);
        """.replace("RISK", item.getDataVariable("riskScore"));

    List<Object> actions = engine.evalToList(rule);

    System.out.println("Escalation actions: " + actions);
    for (Object action : actions) {
        System.out.println("  - " + action);
    }

    item.setDataVariable("escalation_actions",
        String.join(", ", actions.stream().map(Object::toString).toList())
    );
} finally {
    engine.close();
}
```

The `evalToList()` method returns `List<Object>`. Elements are typically strings or numbers, which can be cast or converted as needed.

### Step 8: Invoke a Named JavaScript Function

For better code organization, define functions in your script and call them by name:

```java
try {
    // Load the rules file (once per engine instance)
    engine.evalScript(Paths.get("src/main/resources/rules/routing.js"));

    // Call a named function from the loaded rules
    String decision = (String) engine.invokeJsFunction(
        "evaluateRiskAndRoute",
        item.getId(),
        Double.parseDouble(item.getDataVariable("amount")),
        item.getDataVariable("priority")
    );

    item.setDataVariable("route", decision);
} finally {
    engine.close();
}
```

The `invokeJsFunction(functionName, args...)` method is cleaner than building JavaScript expression strings. Arguments are automatically marshalled to JavaScript types.

### Step 9: Warm Up All Pool Contexts

For optimal performance in high-throughput scenarios, pre-load rules into all contexts at startup:

```java
public class WorkflowRoutingHandler {
    private JavaScriptExecutionEngine engine;

    public WorkflowRoutingHandler() {
        this.engine = JavaScriptExecutionEngine.builder()
            .sandboxed(true)
            .contextPoolSize(4)
            .build();

        // Warm up all contexts with rules (happens once at startup)
        try {
            engine.evalScriptInAllContexts(Paths.get("src/main/resources/rules/routing.js"));
            System.out.println("Rules pre-loaded in all contexts");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rules", e);
        }
    }

    public void routeCase(YWorkItem item) {
        try {
            String decision = (String) engine.invokeJsFunction(
                "evaluateRiskAndRoute",
                item.getId(),
                Double.parseDouble(item.getDataVariable("amount")),
                item.getDataVariable("priority")
            );

            item.setDataVariable("route", decision);
        } catch (JavaScriptException e) {
            System.err.println("Routing failed: " + e.getMessage());
            item.setDataVariable("route", "MANUAL_REVIEW");
        }
    }

    public void shutdown() {
        engine.close();
    }
}
```

The `evalScriptInAllContexts(Path)` method loads the rules file into every pooled context, eliminating the overhead of loading rules on first use.

### Step 10: Handle JavaScript Exceptions

Wrap engine calls in try/catch to handle JavaScript runtime errors:

```java
try {
    String badRule = "throw new Error('Invalid rule');";
    engine.eval(badRule);
} catch (JavaScriptException e) {
    // Log and handle the error
    System.err.println("JavaScript error: " + e.getMessage());
    item.setDataVariable("route", "ERROR");
} finally {
    engine.close();
}
```

`JavaScriptException` is raised when JavaScript code fails. The exception message includes the JavaScript error message and stack trace.

### Step 11: Evaluate Complex Objects and Return JSON

For returning complex structured data, evaluate and parse JSON directly:

```java
try {
    String complexRule = """
        function buildApprovalPackage(caseId, amount, riskScore) {
            return {
                caseId: caseId,
                decision: amount > 5000 ? 'ESCALATE' : 'APPROVE',
                riskAssessment: {
                    score: riskScore,
                    level: riskScore > 70 ? 'HIGH' : riskScore > 40 ? 'MEDIUM' : 'LOW',
                    timestamp: new Date().toISOString()
                },
                actions: ['notify_supervisor', 'create_audit_log'],
                confidence: 0.95
            };
        }
        buildApprovalPackage(CASE_ID, AMOUNT, RISK);
        """.replace("CASE_ID", "'" + item.getId() + "'")
          .replace("AMOUNT", item.getDataVariable("amount"))
          .replace("RISK", item.getDataVariable("riskScore"));

    Map<String, Object> pkg = engine.evalToMap(complexRule);

    String decision = (String) pkg.get("decision");
    Map<String, Object> riskAssessment = (Map<String, Object>) pkg.get("riskAssessment");
    String riskLevel = (String) riskAssessment.get("level");
    List<Object> actions = (List<Object>) pkg.get("actions");

    System.out.println("Decision: " + decision);
    System.out.println("Risk Level: " + riskLevel);
    System.out.println("Actions: " + actions);

    item.setDataVariable("decision", decision);
    item.setDataVariable("risk_level", riskLevel);
} finally {
    engine.close();
}
```

Nested objects in JavaScript become nested `Map` and `List` structures in Java. Cast cautiously and use proper null checks in production.

### Step 12: Complete Example in a YAWL Task Handler

Here's a realistic workflow routing handler:

```java
import org.yawl.engine.domain.YWorkItem;
import org.yawl.engine.interfce.WorkItemCompleteListener;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptException;

public class CaseRoutingHandler implements WorkItemCompleteListener {
    private JavaScriptExecutionEngine engine;

    public CaseRoutingHandler() {
        this.engine = JavaScriptExecutionEngine.builder()
            .sandboxed(true)
            .contextPoolSize(4)
            .build();

        // Pre-load routing rules into all pool contexts
        try {
            engine.evalScriptInAllContexts(
                Paths.get("src/main/resources/rules/case-routing.js")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load routing rules", e);
        }
    }

    @Override
    public void workItemCompleted(YWorkItem item) {
        try {
            // Extract case properties
            double amount = Double.parseDouble(
                item.getDataVariable("amount")
            );
            int caseAge = Integer.parseInt(
                item.getDataVariable("caseAge")
            );
            String priority = item.getDataVariable("priority");

            // Call JavaScript routing function
            Object result = engine.invokeJsFunction(
                "routeCase",
                item.getId(),
                amount,
                caseAge,
                priority
            );

            if (result instanceof Map) {
                Map<String, Object> route = (Map<String, Object>) result;
                String destination = (String) route.get("destination");
                String reason = (String) route.get("reason");

                item.setDataVariable("routed_to", destination);
                item.setDataVariable("routing_reason", reason);

                System.out.println("Case " + item.getId()
                    + " routed to: " + destination);
            } else {
                String destination = result.toString();
                item.setDataVariable("routed_to", destination);
            }

        } catch (JavaScriptException e) {
            System.err.println("Routing failed for case " + item.getId()
                + ": " + e.getMessage());
            item.setDataVariable("routed_to", "MANUAL_QUEUE");
        }
    }

    public void shutdown() {
        engine.close();
    }
}
```

And the corresponding JavaScript rules file (`src/main/resources/rules/case-routing.js`):

```javascript
function routeCase(caseId, amount, caseAge, priority) {
    // Risk calculation
    let ageRisk = Math.min(caseAge / 365, 1.0);
    let amountRisk = Math.min(Math.log(amount + 1) / 10, 1.0);
    let riskScore = ageRisk + amountRisk;

    // Priority multiplier
    let priorityMult = priority === 'high' ? 2.0
                     : priority === 'medium' ? 1.5
                     : 1.0;

    let finalRisk = Math.min(riskScore * priorityMult, 1.0);

    // Routing decision
    if (finalRisk > 0.75) {
        return {
            destination: 'ESCALATION_QUEUE',
            reason: 'High risk case (score: ' + finalRisk.toFixed(2) + ')'
        };
    } else if (amount > 50000) {
        return {
            destination: 'SENIOR_ANALYST',
            reason: 'Large amount requires senior review'
        };
    } else if (finalRisk > 0.4) {
        return {
            destination: 'STANDARD_REVIEW',
            reason: 'Medium risk, standard review'
        };
    } else {
        return {
            destination: 'AUTO_APPROVE',
            reason: 'Low risk, automatic approval'
        };
    }
}
```

---

## What You Built

You've created a **JavaScript rules engine** embedded in your YAWL workflow. Your `CaseRoutingHandler` can now:

1. Load reusable JavaScript rules from files
2. Evaluate complex business logic in JavaScript
3. Return structured decisions as Java objects
4. Handle routing errors gracefully
5. Support multiple concurrent decision threads via context pooling

JavaScript's expressive syntax makes rules easier to read and modify than Java if-else chains, while GraalVM's polyglot engine keeps everything in-process and performant.

**Next steps:**
- Read [Getting Started with GraalWasm](03-graalwasm-getting-started.md) to add high-performance analytics
- Explore How-To guides for sandboxing, testing rules, and performance profiling
- Consult the [GraalJS API Reference](../../reference/polyglot-graaljs-api.md) for advanced marshalling and promise handling
