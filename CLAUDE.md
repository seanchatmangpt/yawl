# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# YAWL - Yet Another Workflow Language

Java-based BPM/Workflow engine with formal foundations. Version 5.2.

---

# 🚨 HYPER-ADVANCED CODING STANDARDS - ZERO TOLERANCE ENFORCEMENT

## 🎯 PRIME DIRECTIVE: IMPLEMENT REAL, WORKING FEATURES

**Every line of code must do EXACTLY what it claims to do. No exceptions. No workarounds.**

This codebase operates under **Fortune 5 production standards** with **strict Toyota Production System (Jidoka)** and **Chicago TDD principles**.

### 📋 IMPLEMENTATION HIERARCHY (Preference Order):

1. **✅ IMPLEMENT THE REAL FEATURE** (95% of cases)
   - Connect to actual services (YAWL Engine, databases, APIs)
   - Use real dependencies (InterfaceB_EnvironmentBasedClient, HttpClient, etc.)
   - Handle errors properly with real exception handling
   - **This is the DEFAULT** - Always try this first!

2. **⚠️ THROW WITH IMPLEMENTATION GUIDE** (5% of cases - rare)
   - ONLY when dependencies are genuinely missing/unavailable
   - Must include detailed implementation roadmap
   - Must reference specific files and line numbers
   - Example: External config file doesn't exist yet

3. **❌ NEVER ACCEPTABLE** (0% of cases)
   - TODO comments ("will implement later")
   - Mock/stub implementations that return fake data
   - Placeholder constants that pretend to be real
   - Silent failures or fallbacks to fake behavior

**Example of Fortune 5 Implementation:**
```java
// ✅ CORRECT: Real YAWL Engine integration
private String launchWorkflow(String specId, String caseData) throws IOException {
    InterfaceB_EnvironmentBasedClient client =
        new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");
    String session = client.connect(username, password);
    YSpecificationID spec = new YSpecificationID(specId, "0.1", "0.1");
    String caseId = client.launchCase(spec, caseData, session);
    return caseId; // Real case ID from engine!
}
```

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

**✅ REQUIRED - IMPLEMENT REAL FEATURES:**
```java
// ✅ BEST: Implement the REAL feature NOW with real dependencies
private void validateInput(String data) {
    try {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new File("src/main/resources/schema.xsd"));
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(new StringReader(data)));
    } catch (SAXException | IOException e) {
        throw new ValidationException("Input validation failed: " + e.getMessage(), e);
    }
}

// ✅ ACCEPTABLE ONLY if dependencies are truly unavailable:
// Throw UnsupportedOperationException with detailed implementation guide
private void validateInputAlt(String data) {
    throw new UnsupportedOperationException(
        "Input validation requires external schema file that doesn't exist yet.\n" +
        "To implement:\n" +
        "  1. Create schema definition in src/main/resources/schema.xsd\n" +
        "  2. Use javax.xml.validation.SchemaFactory\n" +
        "  3. Catch SAXException for validation errors\n" +
        "See VALIDATION_GUIDE.md for schema format."
    );
}
```

**CRITICAL: Fortune 5 = IMPLEMENT REAL FEATURES**
- **DEFAULT**: Implement the real feature with real integrations
- **ONLY IF BLOCKED**: Throw UnsupportedOperationException with implementation roadmap
- **NEVER**: Write TODO comments, mock/stub implementations, or fake data

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

// ❌ FORBIDDEN: Placeholder/fake data structures
private static final String DEFAULT_VALUE = "placeholder";
private static final User SAMPLE_USER = new User("test", "test@example.com");
private static final List<String> DUMMY_DATA = Arrays.asList("a", "b", "c");
private static final String MOCK_API_KEY = "test_key_123";
private static final String FAKE_ENDPOINT = "http://example.com/fake";

// ✅ ALLOWED: Real service configuration constants
private static final String ZHIPU_AI_MODEL_GLM_4_6 = "glm-4.6";  // Real AI model ID
private static final String YAWL_INTERFACE_B_PATH = "/ib";  // Real YAWL endpoint
private static final int DEFAULT_TIMEOUT_SECONDS = 30;  // Real configuration value
private static final String XML_SCHEMA_NAMESPACE = XMLConstants.W3C_XML_SCHEMA_NS_URI;

// ❌ FORBIDDEN: Logging instead of throwing
public void validateSchema(String xml) {
    log.warn("Schema validation not implemented");  // Silent failure!
}
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
```

**✅ REQUIRED:**
```java
// ✅ CORRECT: Honest about current state
public void startWorkflow(String specId) {
    throw new UnsupportedOperationException(
        "Workflow engine requires:\n" +
        "  1. YEngine instance initialized\n" +
        "  2. Specification loaded via YSpecificationID\n" +
        "  3. Case ID generated via launchCase()\n" +
        "See YEngine.java:156 for implementation pattern"
    );
}

// ✅ CORRECT: Real validation logic
public List<String> validate(String xml) {
    List<String> errors = new ArrayList<>();
    try {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new File("schema/YAWL_Schema4.0.xsd"));
        Validator validator = schema.newValidator();
        // ... actual validation logic
        return errors;  // Real validation results!
    } catch (Exception e) {
        errors.add("Validation failed: " + e.getMessage());
        return errors;
    }
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

## 🔍 AUTOMATED VALIDATION

**Post-write validation hook active:** `.claude/hooks/hyper-validate.sh`

Checks for 14 violation patterns after every Write/Edit:
- TODO-like markers (TODO, FIXME, XXX, HACK, LATER, etc.)
- Mock/stub method names
- Mock class names
- Mock mode flags
- Empty string returns
- NULL returns with stub comments
- No-op method bodies
- Placeholder constants
- Silent fallback patterns
- Conditional mock behavior
- Suspicious getOrDefault() calls
- Early returns that skip logic
- Log.warn() instead of throw
- Mock framework imports in src/

**Violations = BLOCKED** (exit code 2, feedback shown to AI)

See `.claude/settings.json` for hook configuration.

---

## ✅ SUMMARY: The Five Commandments

1. **NO DEFERRED WORK** - No TODO/FIXME/XXX/HACK or any disguised variants
2. **NO MOCKS** - No mock/stub/fake/test/demo/sample behavior
3. **NO STUBS** - No empty returns, no-op methods, or placeholder data
4. **NO FALLBACKS** - No silent degradation to fake behavior on errors
5. **NO LIES** - Code behavior must match its name, docs, and promises

**Violation of ANY commandment = IMMEDIATE REJECTION**

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

**AI Assistants: By writing code in this repository, you affirm:**
- ✅ I have scanned my code for all forbidden patterns
- ✅ I have verified method behavior matches documentation
- ✅ I have chosen real implementation OR explicit exception
- ✅ I have NOT used any disguised TODO/mock/stub variants
- ✅ I understand violations will be caught and blocked

**This is not negotiable. This is Fortune 5 production code.**

*See `.claude/HYPER_STANDARDS.md` for comprehensive examples and edge cases.*

---

## Environment Requirements

**This project runs in Docker/DevContainer environments.**
- Use docker-compose for full YAWL stack
- DevContainer setup available in `.devcontainer/`

## Build Commands

```bash
# Build all WARs (default target)
ant -f build/build.xml buildWebApps

# Full build (all release material)
ant -f build/build.xml buildAll

# Compile only
ant -f build/build.xml compile

# Build standalone JAR
ant -f build/build.xml build_Standalone

# Clean build artifacts
ant -f build/build.xml clean

# Generate Javadoc
ant -f build/build.xml javadoc

# Run unit tests
ant -f build/build.xml unitTest
```

## Docker/DevContainer Setup

### Option 1: VS Code DevContainer (Recommended)
```bash
# Open in VS Code with DevContainer
# VSCode will prompt to "Reopen in Container"
# This automatically sets up Java 21 and Ant

# Then use the build script:
./.claude/build.sh all
```

See `.devcontainer/devcontainer.json` for configuration.

### Option 2: Docker Compose (Full Stack)
```bash
# Start YAWL dev environment with PostgreSQL
docker-compose up -d

# Enter the development container
docker-compose exec yawl-dev bash

# Inside container, use build script:
./.claude/build.sh all
```

This includes:
- YAWL development environment (Java 21, Ant)
- PostgreSQL database (pre-configured)
- Tomcat ready for deployment
- Port forwarding (8080, 8081)

## Test Commands

```bash
# Run all test suites (JUnit)
java -cp classes:build/3rdParty/lib/* junit.textui.TestRunner org.yawlfoundation.yawl.TestAllYAWLSuites

# Run unit tests via Ant
ant unitTest
```

Test suites: Elements, State, Engine, Exceptions, Logging, Schema, Unmarshaller, Util, Worklist, Authentication.

## Database Configuration

Edit `build/build.properties`:
- `database.type`: postgres, mysql, derby, h2, hypersonic, oracle
- `database.path`, `database.user`, `database.password`

Build target auto-configures Hibernate based on database type.

## Architecture

### Core Packages

| Package | Purpose |
|---------|---------|
| `org.yawlfoundation.yawl.engine` | Core workflow engine (YEngine.java) |
| `org.yawlfoundation.yawl.stateless` | Stateless engine variant (YStatelessEngine.java) |
| `org.yawlfoundation.yawl.elements` | YAWL net elements (tasks, conditions, flows) |
| `org.yawlfoundation.yawl.resourcing` | Human/non-human resource management |
| `org.yawlfoundation.yawl.worklet` | Dynamic workflow via Worklets |
| `org.yawlfoundation.yawl.unmarshal` | XML specification parsing |
| `org.yawlfoundation.yawl.integration.a2a` | Agent-to-Agent protocol integration |
| `org.yawlfoundation.yawl.integration.mcp` | Model Context Protocol integration |

### Services (built as WARs)

- **engine** - Core YAWL engine
- **resourceService** - Resource allocation and work queues
- **workletService** - Dynamic process adaptation
- **monitorService** - Process monitoring
- **schedulingService** - Calendar-based scheduling
- **costService** - Cost tracking
- **balancer** - Load balancing across engine instances

### Key Classes

- `YEngine.java` - Main engine implementing Interface A (design) and Interface B (client)
- `YStatelessEngine.java` - Lightweight engine without persistence
- `YNetRunner.java` - Executes workflow net instances
- `YWorkItem.java` - Unit of work in the engine
- `YSpecification.java` - Parsed workflow specification

### Interfaces

- **Interface A** - Design-time operations (upload specs, manage services)
- **Interface B** - Client/runtime operations (launch cases, complete work items)
- **Interface E** - Event notifications
- **Interface X** - Extended operations

## XML Schemas

YAWL specifications are XML documents validated against XSD schemas in `schema/`:
- `YAWL_Schema4.0.xsd` - Latest schema version
- Historical schemas (Beta3 through 3.0) for backward compatibility

## Dependencies

Third-party libraries in `build/3rdParty/lib/`:
- JDOM2 (XML processing)
- Hibernate 5 (persistence)
- Log4J 2 (logging)
- Jackson (JSON)
- Various web/JSF libraries

## File Conventions

- Source: `src/org/yawlfoundation/yawl/`
- Tests: `test/org/yawlfoundation/yawl/`
- Schemas: `schema/`
- Example specs: `exampleSpecs/`, `test/*.ywl`
- Build config: `build/build.properties`

## Build Output

All build artifacts are located in `output/`:
- `yawl-lib-5.2.jar` - Core YAWL library (3.0 MB)
- `YawlControlPanel-5.2.jar` - Control Panel executable (423 KB)

Compiled classes: `classes/`

## MCP and A2A Integration

The project includes integration with MCP (Model Context Protocol) and A2A (Agent-to-Agent):

- `src/org/yawlfoundation/yawl/integration/a2a/` - A2A server and client
- `src/org/yawlfoundation/yawl/integration/mcp/` - MCP server and client
- `run-a2a-server.sh` - Start A2A server
- `run-mcp-server.sh` - Start MCP server

See `INTEGRATION_GUIDE.md` and `INTEGRATION_README.md` for details.

## Integration with Claude Code

This project is configured to work with Claude Code in Docker environments:
- Builds with Ant (no special IDE required)
- Tests runnable with `ant unitTest`
- Clean build artifacts tracked in `.gitignore`
- Build wrapper script `./.claude/build.sh` for easy access

---

**Last Updated:** February 14, 2026
**Project Version:** 5.2
