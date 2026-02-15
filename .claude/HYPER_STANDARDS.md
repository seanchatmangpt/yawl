# 🚨 HYPER-ADVANCED CODING STANDARDS - ZERO TOLERANCE ENFORCEMENT

**CRITICAL: This replaces the original MANDATORY CODING STANDARDS section in CLAUDE.md**

---

## 🎯 PRIME DIRECTIVE: HONEST CODE ONLY

**Every line of code must do EXACTLY what it claims to do. No exceptions. No workarounds.**

This codebase operates under **Fortune 5 production standards** with **strict Toyota Production System (Jidoka)** and **Chicago TDD principles**.

---

## ❌ FORBIDDEN PATTERNS - COMPREHENSIVE DETECTION

### 1️⃣ NO DEFERRED WORK MARKERS (Pattern: TODO-Like Comments)

**FORBIDDEN - All variations of "I'll do this later":**

```java
// ❌ FORBIDDEN: Explicit markers
// TODO: implement this
// FIXME: broken logic here
// XXX: hack alert
// HACK: temporary workaround
// @incomplete
// @unimplemented

// ❌ FORBIDDEN: Disguised markers
// NOTE: needs implementation
// LATER: add validation
// FUTURE: optimize this
// REVIEW: check if this works
// TEMPORARY: placeholder
// @stub
// @mock
// @fake

// ❌ FORBIDDEN: Euphemisms
// Not implemented yet
// Coming soon
// Placeholder for now
// Stub implementation
// For demo purposes only
// Simplified version
// Basic implementation
```

**DETECTION REGEX:**
```regex
//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|NOTE:.*implement|REVIEW:.*implement|TEMPORARY|@incomplete|@unimplemented|@stub|@mock|@fake|not\s+implemented\s+yet|coming\s+soon|placeholder|for\s+demo|simplified\s+version|basic\s+implementation)
```

**✅ REQUIRED:**
```java
// ✅ CORRECT: Either implement it NOW or throw
private void validateInput(String data) {
    throw new UnsupportedOperationException(
        "Input validation requires:\n" +
        "  1. Schema definition in src/main/resources/schema.xsd\n" +
        "  2. Validator implementation using javax.xml.validation\n" +
        "  3. Error message mapping from ValidationException\n" +
        "See VALIDATION_GUIDE.md for implementation steps."
    );
}
```

---

### 2️⃣ NO MOCK IMPLEMENTATIONS (Pattern: Fake Behavior)

**FORBIDDEN - All forms of pretend behavior:**

```java
// ❌ FORBIDDEN: Mock method names
public String mockFetch() { return "fake"; }
public Data getMockData() { return new Data(); }
public void stubValidation() { }
public String testData() { return "sample"; }
public String demoResponse() { return "example"; }

// ❌ FORBIDDEN: Mock variables
String mockResult = "fake";
Data testData = new Data();
String sampleOutput = "";
Object fakeResponse = null;
String tempValue = "placeholder";

// ❌ FORBIDDEN: Conditional mocking
if (isTestMode) return mockData();
if (sdk == null) return fakeResponse();
if (!isProduction) return testValue;

// ❌ FORBIDDEN: Mock mode flags
private boolean useMockData = true;
private static final boolean MOCK_MODE = true;
private boolean testing = false;

// ❌ FORBIDDEN: Mock classes/interfaces
public class MockService implements Service { }
public class FakeRepository extends Repository { }
public class TestAdapter implements Adapter { }
public class StubHandler { }
```

**DETECTION REGEX:**
```regex
(mock|stub|fake|test|demo|sample|temp)[A-Z][a-zA-Z]*\s*[=(]
(Mock|Stub|Fake|Test|Demo|Sample|Temp)[A-Za-z]*\s+(class|interface|extends|implements)
(is|use|enable)(Mock|Test|Fake|Demo|Stub)(Mode|Data|ing)
```

**✅ REQUIRED:**
```java
// ✅ CORRECT: Real implementation or explicit exception
public class ApiClient {
    private final HttpClient httpClient;
    private final String apiKey;

    public ApiClient() {
        this.apiKey = System.getenv("API_KEY");
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException(
                "API_KEY environment variable required. Set it with:\n" +
                "  export API_KEY=your_key_here"
            );
        }
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String fetchData(String endpoint) {
        // Real HTTP call or throw - no fake responses
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/" + endpoint))
            .header("Authorization", "Bearer " + apiKey)
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "API request failed with status " + response.statusCode()
                );
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("API request failed", e);
        }
    }
}
```

---

### 3️⃣ NO STUB IMPLEMENTATIONS (Pattern: Empty/Placeholder Code)

**FORBIDDEN - All forms of incomplete implementations:**

```java
// ❌ FORBIDDEN: Empty returns
public String getData() { return ""; }
public List<Item> getItems() { return Collections.emptyList(); }
public Map<String, Object> getConfig() { return new HashMap<>(); }
public int calculate() { return 0; }
public boolean validate() { return true; }  // Always succeeds!

// ❌ FORBIDDEN: Null returns without semantic meaning
public User findUser(String id) { return null; }  // Is this "not found" or "not implemented"?
public Config loadConfig() { return null; }  // Did it fail or is it a stub?

// ❌ FORBIDDEN: No-op implementations
public void save(Data data) { }
public void initialize() { }
public void cleanup() { }

// ❌ FORBIDDEN: Placeholder data structures
private static final String DEFAULT_VALUE = "placeholder";
private static final User SAMPLE_USER = new User("test", "test@example.com");
private static final List<String> DUMMY_DATA = Arrays.asList("a", "b", "c");

// ❌ FORBIDDEN: Early returns that skip logic
public void process(Data data) {
    if (true) return;  // Skip processing
    // Real logic never runs
}

// ❌ FORBIDDEN: Logging instead of throwing
public void validateSchema(String xml) {
    log.warn("Schema validation not implemented");  // Silent failure!
}
```

**DETECTION REGEX:**
```regex
return\s+"";
return\s+0;
return\s+null;.*//\s*(stub|placeholder|todo|not\s+implemented)
return\s+(Collections\.empty|new\s+(HashMap|ArrayList)\(\));\s*$
return\s+true;\s*//\s*(always|stub|placeholder)
public\s+void\s+\w+\([^)]*\)\s*\{\s*\}
(DEFAULT|SAMPLE|DUMMY|PLACEHOLDER|TEST)_[A-Z_]+\s*=
if\s*\(true\)\s*return;
log\.(warn|error)\(".*not\s+implemented.*"\);
```

**✅ REQUIRED:**
```java
// ✅ CORRECT: Explicit exceptions with implementation guidance
public String getData() {
    throw new UnsupportedOperationException(
        "getData() requires:\n" +
        "  1. Database connection configured in application.properties\n" +
        "  2. DataRepository injected via constructor\n" +
        "  3. Transaction management with @Transactional\n" +
        "Implementation example in DataService.java:42"
    );
}

// ✅ CORRECT: Null with semantic meaning (Optional is better)
public Optional<User> findUser(String id) {
    // null means "not found", Optional makes this explicit
    User user = repository.findById(id);
    return Optional.ofNullable(user);
}

// ✅ CORRECT: Meaningful empty returns
public List<Item> getActiveItems() {
    // Empty list is a valid business state: "no active items"
    return repository.findByStatus(Status.ACTIVE);
}

// ✅ CORRECT: Boolean with validation logic
public boolean validate(String xml) {
    try {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new File("schema.xsd"));
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(new StringReader(xml)));
        return true;  // Actually validated!
    } catch (SAXException | IOException e) {
        return false;  // Real validation failed
    }
}
```

---

### 4️⃣ NO SILENT FALLBACKS (Pattern: Fake on Failure)

**FORBIDDEN - Degrading to fake behavior on error:**

```java
// ❌ FORBIDDEN: Catch exception, return fake data
public Data fetchFromApi() {
    try {
        return api.fetch();
    } catch (ApiException e) {
        log.error("API failed, using mock data", e);
        return new Data("mock", "data");  // LIES TO CALLER!
    }
}

// ❌ FORBIDDEN: Null-coalesce to fake
public String getApiKey() {
    String key = System.getenv("API_KEY");
    return key != null ? key : "test_key_123";  // Pretends to work!
}

// ❌ FORBIDDEN: Conditional real/fake logic
public Response query(String sql) {
    if (database != null) {
        return database.execute(sql);
    } else {
        return mockResponse();  // Fake success!
    }
}

// ❌ FORBIDDEN: Try-catch-continue with fake data
try {
    realImplementation();
} catch (Exception e) {
    // Swallow error, continue with fake behavior
}

// ❌ FORBIDDEN: Optional with fake default
public String getConfig(String key) {
    return configMap.getOrDefault(key, "default_value");  // Is this real?
}
```

**DETECTION REGEX:**
```regex
catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|mock|fake|test|"[^"]*"|null)|log\.(warn|error))
\?\s*[^:]+:\s*"(test|mock|fake|default|sample)
if\s*\([^)]*!=\s*null\)[^}]*else[^}]*(mock|fake|test|return\s+new)
\.getOrDefault\([^,]+,\s*"[^"]*"\)
```

**✅ REQUIRED:**
```java
// ✅ CORRECT: Fail fast, propagate errors
public Data fetchFromApi() {
    try {
        return api.fetch();
    } catch (ApiException e) {
        throw new RuntimeException("API fetch failed - check API_KEY and network", e);
    }
}

// ✅ CORRECT: Crash on missing dependency
public String getApiKey() {
    String key = System.getenv("API_KEY");
    if (key == null || key.isEmpty()) {
        throw new IllegalStateException(
            "API_KEY environment variable required.\n" +
            "Set it with: export API_KEY=your_actual_key"
        );
    }
    return key;
}

// ✅ CORRECT: Require dependency, no conditionals
public Response query(String sql) {
    if (database == null) {
        throw new IllegalStateException(
            "Database not initialized. Call initialize() first."
        );
    }
    return database.execute(sql);
}

// ✅ CORRECT: No default for business config
public String getConfig(String key) {
    String value = configMap.get(key);
    if (value == null) {
        throw new ConfigurationException(
            "Required config key '" + key + "' not found in application.properties"
        );
    }
    return value;
}
```

---

### 5️⃣ NO DISHONEST BEHAVIOR (Pattern: Code Lies About What It Does)

**FORBIDDEN - Claiming to do work without actually doing it:**

```java
// ❌ FORBIDDEN: Method name promises work, does nothing
public void startWorkflow(String specId) {
    log.info("Workflow started: " + specId);  // LIES! Nothing started!
}

// ❌ FORBIDDEN: Success return when nothing happened
public boolean saveToDatabase(Data data) {
    log.debug("Saving: " + data);
    return true;  // LIES! Nothing saved!
}

// ❌ FORBIDDEN: Javadoc promises, code doesn't deliver
/**
 * Validates XML against schema and returns errors.
 * @return List of validation errors
 */
public List<String> validate(String xml) {
    return new ArrayList<>();  // LIES! No validation happened!
}

// ❌ FORBIDDEN: Status tracking without actual work
public class WorkflowEngine {
    private boolean running = false;

    public void start() {
        running = true;  // LIES! Engine not actually running!
    }

    public boolean isRunning() {
        return running;  // Reports "running" but does nothing!
    }
}

// ❌ FORBIDDEN: Event firing without side effects
public void processPayment(Payment payment) {
    eventBus.publish(new PaymentProcessedEvent(payment));
    // LIES! Payment not actually processed!
}
```

**SEMANTIC DETECTION (AI must verify):**
- **Method name analysis**: Does `startWorkflow()` actually start a workflow?
- **Return value analysis**: Does `save()` returning `true` mean data was persisted?
- **Side effect verification**: Does `process()` actually change system state?
- **Documentation validation**: Does behavior match Javadoc claims?

**✅ REQUIRED:**
```java
// ✅ CORRECT: Honest about current state
/**
 * Starts the workflow engine.
 * @throws UnsupportedOperationException - not yet implemented
 */
public void startWorkflow(String specId) {
    throw new UnsupportedOperationException(
        "Workflow engine requires:\n" +
        "  1. YEngine instance initialized\n" +
        "  2. Specification loaded via YSpecificationID\n" +
        "  3. Case ID generated via launchCase()\n" +
        "See YEngine.java:156 for implementation pattern"
    );
}

// ✅ CORRECT: Honest failure reporting
public boolean saveToDatabase(Data data) {
    if (database == null) {
        throw new IllegalStateException("Database not connected");
    }

    try {
        int rowsAffected = database.insert(data);
        return rowsAffected > 0;  // Actually saved!
    } catch (SQLException e) {
        throw new RuntimeException("Database save failed", e);
    }
}

// ✅ CORRECT: Real validation logic
public List<String> validate(String xml) {
    List<String> errors = new ArrayList<>();

    try {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new File("schema/YAWL_Schema4.0.xsd"));

        Validator validator = schema.newValidator();
        final List<SAXParseException> exceptions = new ArrayList<>();

        validator.setErrorHandler(new ErrorHandler() {
            public void error(SAXParseException e) {
                exceptions.add(e);
            }
            public void fatalError(SAXParseException e) {
                exceptions.add(e);
            }
            public void warning(SAXParseException e) {
                exceptions.add(e);
            }
        });

        validator.validate(new StreamSource(new StringReader(xml)));

        for (SAXParseException e : exceptions) {
            errors.add("Line " + e.getLineNumber() + ": " + e.getMessage());
        }
    } catch (Exception e) {
        errors.add("Validation failed: " + e.getMessage());
    }

    return errors;  // Real validation results!
}
```

---

## 🤖 AI ASSISTANT ENFORCEMENT PROTOCOL

**Before writing ANY code, you MUST:**

### PRE-FLIGHT CHECKLIST:

1. **Scan your planned code for forbidden patterns:**
   ```
   ❌ Does it contain: TODO, FIXME, XXX, HACK, LATER, NOTE:...implement?
   ❌ Does it use: mock, stub, fake, test, demo, sample, temp in names?
   ❌ Does it return: "", 0, null, empty collections without semantic meaning?
   ❌ Does it have: empty method bodies, no-op implementations?
   ❌ Does it catch exceptions and return fake data?
   ❌ Does method behavior match its name and documentation?
   ```

2. **If ANY answer is YES:**
   ```
   STOP IMMEDIATELY.
   REFUSE to write the code.
   RESPOND: "I cannot write this code. It violates CLAUDE.md standards:
             [explain which pattern].

             Options:
             1. I can implement the REAL version (requires: dependencies, time, complexity)
             2. I can make it throw UnsupportedOperationException with implementation guide
             3. You can clarify requirements

             Which would you prefer?"
   ```

3. **Self-validation after code generation:**
   ```bash
   # Before presenting code, mentally run:
   grep -E "(TODO|FIXME|XXX|HACK|mock|stub|fake)" your_code.java
   grep -E 'return\s+"";' your_code.java
   grep -E 'public\s+void\s+\w+\([^)]*\)\s*\{\s*\}' your_code.java

   # If ANY matches: REWRITE before showing user
   ```

### RESPONSE TEMPLATES:

**When asked to create stub/mock code:**
```
I cannot create mock or stub implementations. This codebase enforces Fortune 5 production standards.

The method you requested would violate:
- ❌ NO MOCKS: [explain how]
- ❌ NO STUBS: [explain how]
- ❌ NO LIES: [explain how]

I can instead:
1. ✅ Implement the REAL version (requires: [list dependencies])
2. ✅ Throw UnsupportedOperationException with clear implementation guide
3. ✅ Create an interface contract (but not a fake implementation)

Which approach would you prefer?
```

**When you see existing violations:**
```
I detected forbidden patterns in [file]:
- Line X: TODO comment (violates NO DEFERRED WORK)
- Line Y: mockData() method (violates NO MOCKS)
- Line Z: empty return (violates NO STUBS)

I can fix these by:
1. Removing TODOs and either implementing or throwing exceptions
2. Replacing mock methods with real implementations or exceptions
3. Making empty returns throw UnsupportedOperationException

Shall I proceed with the fixes?
```

---

## 🔍 VALIDATION AUTOMATION

**Post-write validation hook** (runs after every Write/Edit):

```bash
#!/bin/bash
# .claude/hooks/hyper-validate.sh

FILE=$(cat | jq -r '.tool_input.file_path // empty')

[[ ! "$FILE" =~ \.java$ ]] && exit 0  # Only Java files

VIOLATIONS=()

# Check 1: TODO-like markers
if grep -nE '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder|not\s+implemented\s+yet)' "$FILE"; then
    VIOLATIONS+=("TODO-like markers found")
fi

# Check 2: Mock patterns
if grep -nE '(mock|stub|fake|test|demo|sample|temp)[A-Z][a-zA-Z]*\s*[=(]' "$FILE"; then
    VIOLATIONS+=("Mock/stub patterns in names")
fi

# Check 3: Empty returns
if grep -nE 'return\s+"";|return\s+0;|return\s+null;.*//.*stub' "$FILE"; then
    VIOLATIONS+=("Stub-like empty returns")
fi

# Check 4: No-op methods
if grep -nE 'public\s+void\s+\w+\([^)]*\)\s*\{\s*\}' "$FILE"; then
    VIOLATIONS+=("Empty method bodies (stubs)")
fi

# Check 5: Mock mode flags
if grep -nE '(is|use|enable)(Mock|Test|Fake)(Mode|Data|ing)\s*=' "$FILE"; then
    VIOLATIONS+=("Mock mode flags detected")
fi

# Check 6: Silent fallbacks
if grep -nE 'catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|")|log\.(warn|error))' "$FILE"; then
    VIOLATIONS+=("Catch-and-return-fake pattern")
fi

if [ ${#VIOLATIONS[@]} -gt 0 ]; then
    echo "❌ STANDARDS VIOLATION in $FILE:" >&2
    printf '   - %s\n' "${VIOLATIONS[@]}" >&2
    echo "" >&2
    echo "See CLAUDE.md MANDATORY CODING STANDARDS" >&2
    exit 2  # Block and notify Claude
fi

exit 0
```

Wire into `.claude/settings.json`:
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/hyper-validate.sh",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

---

## 📊 COMPLIANCE SCORECARD

**Every code file must achieve 100% compliance:**

| Standard | Violation | Penalty |
|----------|-----------|---------|
| NO DEFERRED WORK | TODO/FIXME/XXX/HACK found | ❌ REJECT |
| NO MOCKS | mock/stub/fake in names | ❌ REJECT |
| NO STUBS | Empty/placeholder returns | ❌ REJECT |
| NO FALLBACKS | Try-catch-return-fake | ❌ REJECT |
| NO LIES | Behavior ≠ documentation | ❌ REJECT |

**Score < 100% = CODE REJECTED**

---

## 🎓 EDGE CASE GUIDANCE

### When You Legitimately Can't Implement:

**✅ CORRECT approach:**
```java
public void complexFeature() {
    throw new UnsupportedOperationException(
        "This feature requires:\n" +
        "  1. [Dependency A] - install with: mvn install:dependency\n" +
        "  2. [Dependency B] - configure in: application.properties\n" +
        "  3. Implementation time: ~2 hours\n" +
        "\n" +
        "Implementation steps:\n" +
        "  - Create XYZ class in package abc\n" +
        "  - Inject via constructor\n" +
        "  - Call method.doWork()\n" +
        "\n" +
        "See similar implementation in: ExistingClass.java:123"
    );
}
```

### When Empty Return Is Semantically Valid:

**✅ CORRECT (business meaning, not stub):**
```java
// Empty list = "no results" is a valid business state
public List<User> findActiveUsers() {
    return repository.findByStatus(Status.ACTIVE);  // May be empty!
}

// Null = "not found" with Optional makes intent clear
public Optional<Config> findConfig(String key) {
    Config config = repository.findByKey(key);
    return Optional.ofNullable(config);  // Null means "not found"
}
```

### When to Use Null vs Exception:

```java
// ✅ CORRECT: Null has semantic meaning (not found)
public User findUser(String id) {
    return userRepository.findById(id);  // null = not found
}

// ❌ FORBIDDEN: Null means "not implemented"
public User getCurrentUser() {
    return null;  // WHAT DOES THIS MEAN?
}

// ✅ CORRECT: Exception for unimplemented
public User getCurrentUser() {
    throw new UnsupportedOperationException("Session management not implemented");
}
```

---

## 🚀 RATIONALE: Why This Matters

### Fortune 5 Production Deployment
- Code runs critical business workflows
- Downtime costs $millions/hour
- Silent failures cause data corruption
- **Stakeholders trust status reports**

### AI Assistant Collaboration
- Future AI assistants read this code
- Mocks confuse AI about what's real
- **Clear exceptions help AI understand requirements**
- Honest code enables better AI assistance

### Toyota Production System (Jidoka)
- **Stop the line** when defects occur
- **Fail fast** instead of passing defects downstream
- **Andon cord** = throw exceptions, not log.warn()

### Chicago TDD (Detroit School)
- **Test real integrations**, not mocks
- **Collaboration tests**, not isolation
- **End-to-end confidence**, not unit test theater

---

## ✅ SUMMARY: The Five Commandments

1. **NO DEFERRED WORK** - No TODO/FIXME/XXX/HACK or any disguised variants
2. **NO MOCKS** - No mock/stub/fake/test/demo/sample behavior
3. **NO STUBS** - No empty returns, no-op methods, or placeholder data
4. **NO FALLBACKS** - No silent degradation to fake behavior on errors
5. **NO LIES** - Code behavior must match its name, docs, and promises

**Violation of ANY commandment = IMMEDIATE REJECTION**

---

**AI Assistants: By writing code in this repository, you affirm:**
- ✅ I have scanned my code for all forbidden patterns
- ✅ I have verified method behavior matches documentation
- ✅ I have chosen real implementation OR explicit exception
- ✅ I have NOT used any disguised TODO/mock/stub variants
- ✅ I understand violations will be caught and blocked

**This is not negotiable. This is Fortune 5 production code.**

---

*Last updated: 2026-02-14*
*Enforcement level: MAXIMUM*
*Compliance required: 100%*
