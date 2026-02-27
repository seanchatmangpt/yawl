# Type Marshalling Between Java and Scripting Languages

How YAWL automatically converts data between Java and Python/JavaScript, and why Python and JavaScript require different marshalling strategies.

## The Core Problem: Language Impedance Mismatch

Each language has different type systems:

| Type | Java | Python | JavaScript |
|------|------|--------|-----------|
| Text | `String` | `str` | `string` |
| Integer | `int`/`long` | `int` | `number` (64-bit float!) |
| Floating-point | `double` | `float` | `number` |
| Map/Dictionary | `Map<K,V>` | `dict` | `object` |
| Array/List | `List<T>` | `list` | `array` |
| Boolean | `boolean` | `bool` | `boolean` |
| Null | `null` | `None` | `null` / `undefined` |

GraalVM Polyglot bridges these via the **Value type**, which can represent any language's value. Marshalling converts Values ↔ Java types.

## The Universal Type: GraalVM Value

```java
// When you eval Python:
Object pythonResult = engine.eval("{'name': 'Alice'}");
// Result is actually: org.graalvm.polyglot.Value

// When you eval JavaScript:
Object jsResult = engine.eval("[1, 2, 3]");
// Result is also: org.graalvm.polyglot.Value

// Polyglot Value can represent:
// - Python dict, list, tuple, str, int, None, ...
// - JS object, array, string, number, null, undefined, ...
// - WASM memory, values, functions, ...
```

**Polyglot Value is the bridge**: It knows what language created it and can convert to Java.

## Python vs JavaScript: The Critical Difference

### Python uses `hasHashEntries()` for dicts

Python dictionaries are distinct from objects. In Polyglot, they're detected via `hasHashEntries()`:

```java
Value pythonDict = engine.eval("{'name': 'Alice', 'age': 30}");

// Correct way (uses TypeMarshaller):
if (pythonDict.hasHashEntries()) {  // ← Python dict check
    String name = pythonDict.getHashValue("name").asString();
    int age = pythonDict.getHashValue("age").asInt();
}

// WRONG: hasMembers() returns false for Python dicts
if (pythonDict.hasMembers()) {  // ❌ Returns false for dicts!
    String name = pythonDict.getMember("name").asString();  // ❌ Will fail
}
```

### JavaScript uses `hasMembers()` for objects

JavaScript objects are accessed via `hasMembers()`:

```java
Value jsObject = engine.eval("{ name: 'Alice', age: 30 }");

// Correct way (uses JsTypeMarshaller):
if (jsObject.hasMembers()) {  // ← JS object check
    String name = jsObject.getMember("name").asString();
    int age = jsObject.getMember("age").asInt();
}

// WRONG: hasHashEntries() returns false for JS objects
if (jsObject.hasHashEntries()) {  // ❌ Returns false for JS objects!
    String name = jsObject.getHashValue("name").asString();  // ❌ Will fail
}
```

### JavaScript arrays use `hasArrayElements()`

Both Python and JS use `hasArrayElements()` for lists/arrays:

```java
// Python list
Value pythonList = engine.eval("[1, 2, 3]");
if (pythonList.hasArrayElements()) {
    long len = pythonList.getArraySize();
    Value first = pythonList.getArrayElement(0);
}

// JavaScript array
Value jsArray = engine.eval("[1, 2, 3]");
if (jsArray.hasArrayElements()) {
    long len = jsArray.getArraySize();
    Value first = jsArray.getArrayElement(0);
}
```

## YAWL TypeMarshaller Implementations

### Python: TypeMarshaller

```java
// org.yawlfoundation.yawl.graalpy.TypeMarshaller

public static Map<String, Object> toMap(Value pythonDict) {
    // Uses hasHashEntries() — correct for Python dicts
    Map<String, Object> result = new HashMap<>();
    for (String key : pythonDict.getHashKeys()) {
        Value value = pythonDict.getHashValue(key);
        result.put(key, toJava(value));
    }
    return result;
}

public static List<Object> toList(Value pythonList) {
    // Uses hasArrayElements() — works for both Python and JS
    List<Object> result = new ArrayList<>();
    for (long i = 0; i < pythonList.getArraySize(); i++) {
        result.add(toJava(pythonList.getArrayElement(i)));
    }
    return result;
}
```

### JavaScript: JsTypeMarshaller

```java
// org.yawlfoundation.yawl.graaljs.JsTypeMarshaller

public static Map<String, Object> toMap(Value jsObject) {
    // Uses hasMembers() — correct for JS objects
    Map<String, Object> result = new HashMap<>();
    for (String key : jsObject.getMemberKeys()) {
        Value value = jsObject.getMember(key);
        result.put(key, toJava(value));
    }
    return result;
}

public static List<Object> toList(Value jsArray) {
    // Uses hasArrayElements() — works for both
    List<Object> result = new ArrayList<>();
    for (long i = 0; i < jsArray.getArraySize(); i++) {
        result.add(toJava(jsArray.getArrayElement(i)));
    }
    return result;
}
```

**Key difference**: Python uses `getHashValue()`, JavaScript uses `getMember()`.

## Built-in Type Conversions (Automatic)

YAWL's eval* methods automatically convert results:

| Method | Python Result | JavaScript Result | Java Type |
|--------|---|---|---|
| `evalToString` | `'hello'` → str().asString() | `'hello'` → toString() | `String` |
| `evalToDouble` | `3.14` → asDouble() | `3.14` → asDouble() | `double` |
| `evalToLong` | `42` → asLong() | `42` → asLong() | `long` |
| `evalToMap` | `{...}` → toMap() via hasHashEntries() | `{...}` → toMap() via hasMembers() | `Map<String, Object>` |
| `evalToList` | `[...]` → toList() via hasArrayElements() | `[...]` → toList() via hasArrayElements() | `List<Object>` |

## Java → Script Type Conversions

### Primitives (Automatic)

```java
// Java → Python
engine.eval("42");                  // int → Python int
engine.eval("3.14");                // double → Python float
engine.eval("true");                // boolean → Python bool
engine.eval("'hello'");             // String → Python str

// Java → JavaScript
engine.eval("42");                  // int → JS number
engine.eval("3.14");                // double → JS number
engine.eval("true");                // boolean → JS boolean
engine.eval("'hello'");             // String → JS string
```

### Collections (Automatic with eval)

```java
// Java List → Python list (automatic binding)
List<Integer> javaList = List.of(1, 2, 3);
double sum = engine.evalToDouble("""
    sum(javaList)  // javaList is auto-bound
""");

// Java Map → Python dict (NOT automatic; must pass as arg)
Map<String, Object> data = Map.of("name", "Alice");
// Option 1: Use string formatting (type-unsafe)
engine.eval("'%s'".formatted(data.get("name")));
// Option 2: Use HostAccess wrapper (type-safe)
@HostAccess.Export
class DataWrapper {
    @HostAccess.Export
    public Map<String, Object> getData() { return data; }
}
```

## Null/None/Undefined Handling

| Language | Null Representation | Java Equivalent |
|---|---|---|
| Python | `None` | `null` |
| JavaScript | `null` | `null` |
| JavaScript | `undefined` | Special Value; no Java equivalent |

```java
// Python None → Java null
Value pythonNone = engine.eval("None");
Object obj = pythonNone.isNull() ? null : pythonNone.asString();

// JS null → Java null
Value jsNull = engine.eval("null");
Object obj = jsNull.isNull() ? null : jsNull.asString();

// JS undefined → ???
Value jsUndefined = engine.eval("undefined");
boolean isUndef = jsUndefined.isNull();  // True in GraalVM!
// jsUndefined and jsNull are indistinguishable in Java
```

## Numeric Precision Issues

### Python `int` (arbitrary precision) → Java `long` (64-bit)

```python
# Python can handle huge integers
x = 2 ** 100  # 1267650600228229401496703205376

# But when converted to Java:
long javaX = engine.evalToLong("2 ** 100");
// Precision loss! Java long overflows
```

**Fix**: Keep large numbers as strings:
```java
String bigNum = engine.evalToString("str(2 ** 100)");
BigDecimal bd = new BigDecimal(bigNum);  // Exact representation
```

### JavaScript `number` (64-bit float) → Java `double`

```javascript
// JS number is IEEE 754 double
let x = 0.1 + 0.2;  // 0.30000000000000004 (classic FP issue)
```

No fix available; this is inherent to floating-point math. Round explicitly:
```java
double result = engine.evalToDouble("round(0.1 + 0.2, 2)");  // 0.3
```

## Best Practices

### 1. Use Typed eval Methods When Possible

```java
// Prefer:
String name = engine.evalToString("'Alice'");

// Over:
Object obj = engine.eval("'Alice'");
String name = (String) obj;  // Requires casting + null check
```

### 2. Know Your Marshaller

```java
// If you're writing PythonExecutionEngine code:
// Use TypeMarshaller.toMap() with hasHashEntries()

// If you're writing JavaScriptExecutionEngine code:
// Use JsTypeMarshaller.toMap() with hasMembers()
```

### 3. Pass Complex Objects via @HostAccess

```java
// Instead of converting Map → JSON → Python dict:
@HostAccess.Export
class WorkflowData {
    @HostAccess.Export
    public String getCaseID() { ... }
    
    @HostAccess.Export
    public Map<String, Object> getVariables() { ... }
}

// Pass it directly to script:
pythonEngine.eval("""
    case_id = workflow_data.get_case_id()
    variables = workflow_data.get_variables()
""");
```

### 4. Null-check Results

```java
try {
    Map<String, Object> data = engine.evalToMap("potential_none");
} catch (NullPointerException e) {
    System.err.println("Script returned None/null");
    data = Collections.emptyMap();
}
```

### 5. Preserve Precision for Financial/Scientific Data

```java
// Instead of:
double amount = engine.evalToDouble("decimal.Decimal('100.50')");  // Precision loss!

// Do:
String amountStr = engine.evalToString("str(decimal.Decimal('100.50'))");
BigDecimal amount = new BigDecimal(amountStr);  // Exact
```

