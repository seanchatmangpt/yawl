# How to pass Java objects to GraalJS and GraalPy scripts

## Problem

You need to pass Java objects (YAWL WorkItems, custom domain objects) to JavaScript or Python scripts and have the scripts inspect and modify them.

## Solution

GraalVM Polyglot automatically marshals primitive types and collections. For custom Java objects, use `@HostAccess` annotations to explicitly expose methods and fields.

### Automatic marshalling for built-in types

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;

JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .build();

// Java List → JavaScript array (automatic)
List<String> statuses = List.of("pending", "approved", "completed");
String result = engine.evalToString("""
    statuses.length  // statuses.length == 3
""");

// Java Map → JavaScript object (automatic)
Map<String, Object> metadata = Map.of(
    "caseId", "CASE-001",
    "created", "2024-02-27"
);

Map<String, Object> output = engine.evalToMap("""
    metadata.caseId  // "CASE-001"
    { caseId: metadata.caseId, year: 2024 }
""");

// Primitives (automatic)
engine.evalToString("""
    (42).toString()       // "42"
    (3.14).toFixed(1)     // "3.1"
    true.toString()       // "true"
""");
```

### Pass custom Java objects with @HostAccess

```java
import org.graalvm.polyglot.HostAccess;

// Define a HostAccess policy to expose your object
@HostAccess.Export
public class CaseData {
    private String caseId;
    private String status;
    private int priority;
    private Map<String, Object> variables;

    public CaseData(String caseId, String status, int priority) {
        this.caseId = caseId;
        this.status = status;
        this.priority = priority;
        this.variables = new HashMap<>();
    }

    // Only methods with @Export are visible to scripts
    @HostAccess.Export
    public String getCaseId() {
        return caseId;
    }

    @HostAccess.Export
    public String getStatus() {
        return status;
    }

    @HostAccess.Export
    public int getPriority() {
        return priority;
    }

    @HostAccess.Export
    public Map<String, Object> getVariables() {
        return variables;
    }

    @HostAccess.Export
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    // Methods without @Export are NOT visible to scripts
    public String getInternalSecret() {
        return "hidden";  // Script cannot access this
    }
}
```

### Pass object to JavaScript

```java
CaseData caseData = new CaseData("CASE-001", "in_progress", 3);
caseData.setVariable("applicant_name", "Alice");

JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .build();

String result = engine.evalToString("""
    // JavaScript receives caseData object
    const name = caseData.getVariables().applicant_name;  // "Alice"
    const id = caseData.getCaseId();                     // "CASE-001"
    const priority = caseData.getPriority();            // 3
    
    if (caseData.getPriority() > 2) {
        caseData.setVariable("urgent", true);
    }
    
    `Case ${id} is ${caseData.getStatus()}`
""");

System.out.println(result);  // "Case CASE-001 is in_progress"
System.out.println(caseData.getVariables().get("urgent"));  // true
```

### Pass object to Python

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

CaseData caseData = new CaseData("CASE-002", "completed", 1);
caseData.setVariable("loan_amount", 50000);

PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .poolSize(2)
    .build();

Map<String, Object> result = engine.evalToMap("""
    # Python receives caseData object
    case_id = case_data.get_case_id()          # "CASE-002"
    variables = case_data.get_variables()
    loan = variables.get('loan_amount')        # 50000
    
    case_data.set_variable('interest_rate', 3.5)
    
    {
        'case_id': case_id,
        'interest_rate': 3.5,
        'annual_interest': loan * 0.035
    }
""");

System.out.println(result.get("annual_interest"));  // 1750.0
```

### List and Map marshalling

```java
// Java List passed to JavaScript (automatic)
List<Integer> amounts = List.of(1000, 2000, 3000);

JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();

double total = engine.evalToDouble("""
    amounts.reduce((sum, amt) => sum + amt, 0)  // Sum all amounts
""");

// Java Map passed to JavaScript (automatic)
Map<String, Integer> scores = Map.of(
    "alice", 850,
    "bob", 720,
    "charlie", 795
);

List<Object> approved = engine.evalToList("""
    Object.entries(scores)
        .filter(([name, score]) => score >= 750)
        .map(([name, score]) => name)
    // Returns: ['alice', 'charlie']
""");
```

### Real-world example: Pass WorkItem to JavaScript rule

```java
@HostAccess.Export
public class WorkItemWrapper {
    private WorkItem workItem;

    public WorkItemWrapper(WorkItem workItem) {
        this.workItem = workItem;
    }

    @HostAccess.Export
    public String getCaseID() {
        return workItem.getCaseID();
    }

    @HostAccess.Export
    public Object getDataVariable(String name) {
        try {
            return workItem.getDataVariable(name);
        } catch (Exception e) {
            return null;
        }
    }

    @HostAccess.Export
    public void setDataVariable(String name, Object value) {
        try {
            workItem.setData(name, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

// In task handler
WorkItemWrapper wrapped = new WorkItemWrapper(workItem);

JavaScriptExecutionEngine engine = ...;  // Shared instance

Map<String, Object> decision = engine.evalToMap("""
    const amount = workItem.getDataVariable('amount');
    const creditScore = workItem.getDataVariable('creditScore');
    
    const decision = {
        approved: creditScore >= 700 && amount <= 100000,
        reason: creditScore >= 700 ? 'Credit approved' : 'Low credit score'
    };
    
    workItem.setDataVariable('routing_decision', decision);
    decision
""");
```

### Handle null and missing fields

```java
// Python script handles missing data gracefully
PythonExecutionEngine engine = PythonExecutionEngine.builder().build();

CaseData data = new CaseData("CASE-003", "pending", 2);
// Note: no variables set yet

Map<String, Object> result = engine.evalToMap("""
    variables = case_data.get_variables()
    
    # Safely access potentially missing variables
    priority = variables.get('priority', 'normal')  # 'normal' is default
    tags = variables.get('tags', [])               # [] is default
    
    {
        'has_priority': 'priority' in variables,
        'priority': priority,
        'tag_count': len(tags)
    }
""");

System.out.println(result);  // {has_priority: false, priority: "normal", tag_count: 0}
```

## Tips

- **Use @HostAccess.Export**: Explicitly annotate only the methods you want scripts to call. Unannotated methods are invisible.
- **Keep it simple**: Expose read methods and simple setters. For complex operations, do them in Java, not JavaScript.
- **No reflection**: Scripts cannot use reflection to access private fields; they see only @Export methods.
- **Collections are automatic**: List, Map, Set are marshalled automatically; no annotations needed.
- **Test access**: Write tests confirming scripts can/cannot access specific methods.

