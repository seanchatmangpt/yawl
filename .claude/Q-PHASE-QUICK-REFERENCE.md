# Q Phase (Invariants) - Quick Reference

**What**: Verify code invariants (real_impl, ¬mock, ¬lie, ¬silent_fallback)
**Where**: `.claude/sparql/invariants-q-phase.sparql`
**When**: After H phase (guards) passes, before consolidation
**Why**: Fortune 5 production standard - "honest code only"

---

## The 4 Invariants

### Q1: real_impl ∨ throw
**Principle**: Every method either implements real logic OR throws UnsupportedOperationException.

**Violations Detected**:
- Empty bodies: `public void foo() { }`
- Stub returns: `return "";` or `return 0;` or `return null;`
- Wrong exception type: `throw new Exception()` (not UnsupportedOperationException)

**Fix**:
```java
// ❌ WRONG (empty stub)
public void initialize() { }

// ✅ RIGHT (real implementation)
public void initialize() {
    database.connect();
    cache.refresh();
}

// ✅ RIGHT (honest exception)
public void initialize() {
    throw new UnsupportedOperationException(
        "Requires database configuration. See DATABASE.md:42"
    );
}
```

---

### Q2: ¬mock (No Mock Implementations)
**Principle**: No mock, stub, fake, test, demo, sample, or temporary implementations in production code.

**Violations Detected**:
- Mock methods: `mockFetch()`, `getFakeData()`, `testResponse()`
- Mock classes: `MockService`, `FakeRepository`, `TestAdapter`
- Mock variables: `String mockData`, `Object dummyUser`, `boolean useMockData`
- Mock mode flags: `MOCK_MODE`, `isTestMode`

**Fix**:
```java
// ❌ WRONG (mock implementation)
public String getMockData() {
    return "fake data";
}

// ✅ RIGHT (real implementation)
public String getData() {
    HttpResponse response = client.fetch("https://api.example.com/data");
    return response.body();
}

// ✅ RIGHT (if unimplemented)
public String getData() {
    throw new UnsupportedOperationException(
        "Requires API client setup. See API_SETUP.md"
    );
}
```

**Note**: Mock classes are allowed ONLY in `src/test/` directories.

---

### Q3: ¬silent_fallback (No Silent Exception Handling)
**Principle**: Exceptions are propagated or logged with re-throw, never silently caught and faked.

**Violations Detected**:
- Return fake data on error: `catch(IOException e) { return mockData(); }`
- Empty catch: `catch(Exception e) { }`
- Log and ignore: `catch(IOException e) { log.warn("failed"); }`
- Default fallback: `getOrDefault(key, "fake_default")`
- Ternary with fake: `database != null ? database.query() : fakeQuery()`

**Fix**:
```java
// ❌ WRONG (silent fallback)
public Data fetchFromApi() {
    try {
        return api.fetch();
    } catch (ApiException e) {
        log.error("API failed", e);  // Silently continues!
        return new Data("fake", "data");  // LIES to caller
    }
}

// ✅ RIGHT (fail fast)
public Data fetchFromApi() {
    try {
        return api.fetch();
    } catch (ApiException e) {
        throw new RuntimeException(
            "API fetch failed - check API_KEY and network connectivity",
            e  // Include original exception
        );
    }
}

// ✅ RIGHT (explicit exception)
public Data fetchFromApi() {
    if (api == null) {
        throw new IllegalStateException(
            "API client not initialized. Call initialize() first."
        );
    }
    return api.fetch();  // Let any exception propagate
}
```

**Allowed Patterns**:
- `throw new RuntimeException(..., e)` - Re-throw as different type
- `catch(...) { throw e; }` - Re-throw same exception
- `catch(...) { log.error(...); throw ...; }` - Log AND throw

---

### Q4: ¬lie (Code Matches Documentation)
**Principle**: Method behavior matches its name, documentation, and return type.

**Violations Detected**:
- Claims validation but doesn't: Javadoc says "validates", body is empty
- Claims persistence but doesn't: Javadoc says "saves", no database call
- Claims exception handling but doesn't: Javadoc says "throws X", no throw
- Claims transformation but doesn't: Javadoc says "converts", returns original
- Method name implies work but doesn't: `fetch()` returns empty/null

**Fix**:
```java
// ❌ WRONG (lie - claims validation)
/**
 * Validates the user input against schema.
 * @param data User data
 * @return true if valid, false otherwise
 */
public boolean validate(String data) {
    return new ArrayList<>();  // LIES! No validation!
}

// ✅ RIGHT (real validation)
/**
 * Validates the user input against schema.
 * @param data User data
 * @return true if valid, false otherwise
 */
public boolean validate(String data) {
    try {
        SchemaFactory factory = SchemaFactory.newInstance(
            XMLConstants.W3C_XML_SCHEMA_NS_URI
        );
        Schema schema = factory.newSchema(new File("schema.xsd"));
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(new StringReader(data)));
        return true;  // Actually validated!
    } catch (SAXException | IOException e) {
        return false;  // Real validation failed
    }
}

// ✅ RIGHT (honest if unimplemented)
/**
 * Validates the user input against schema.
 * @throws UnsupportedOperationException - Not yet implemented
 */
public boolean validate(String data) {
    throw new UnsupportedOperationException(
        "Validation requires:\n" +
        "  1. XSD schema file at schema/user-schema.xsd\n" +
        "  2. javax.xml.validation imported\n" +
        "  3. Error handler setup for detailed feedback\n" +
        "See validation-example.java:42 for reference implementation"
    );
}
```

---

## Running Q Phase Validation

### Via CLI
```bash
# Run Q phase verification
yawl godspeed verify --verbose

# Run specific invariant check
yawl godspeed verify --invariant Q_REAL_IMPL_OR_THROW

# Save detailed report
yawl godspeed verify --report json > invariant-report.json
```

### Direct SPARQL
```bash
# Using RDF4J or Jena:
# 1. Generate RDF from Java AST: yawl ggen validate --rdf-output
# 2. Execute SPARQL queries against RDF graph
# 3. Parse results → invariant-receipt.json
```

### Full GODSPEED Circuit
```bash
# Run all phases including Q
yawl godspeed full --verbose

# Output:
# Ψ (Discover)   ✓
# Λ (Compile)    ✓
# H (Guards)     ✓
# Q (Invariants) ✓ (or RED with violations)
```

---

## Interpreting Violation Reports

### Sample Invariant Receipt (JSON)
```json
{
  "phase": "invariants",
  "timestamp": "2026-02-22T14:30:00Z",
  "files_scanned": 42,
  "violations": [
    {
      "invariant": "Q_REAL_IMPL_OR_THROW",
      "severity": "FAIL",
      "file": "src/main/java/YWorkItem.java",
      "line": 427,
      "method": "setState",
      "issue": "Empty method body (stub). Must implement or throw.",
      "fix_guidance": "Either implement real state validation logic or throw UnsupportedOperationException"
    },
    {
      "invariant": "Q_NO_MOCK",
      "severity": "FAIL",
      "file": "src/main/java/MockDataService.java",
      "line": 12,
      "class_name": "MockDataService",
      "issue": "Mock class detected in production code",
      "fix_guidance": "Delete mock class or implement real DataService"
    }
  ],
  "summary": {
    "total_violations": 2,
    "by_invariant": {
      "Q_REAL_IMPL_OR_THROW": 1,
      "Q_NO_MOCK": 1,
      "Q_NO_SILENT_FALLBACK": 0,
      "Q_NO_LIE": 0
    }
  },
  "status": "RED"
}
```

### Interpreting Exit Codes
- `0` = GREEN (all invariants pass, ready for consolidation)
- `1` = Error (validation tool failure, retry)
- `2` = RED (violations found, must fix code)

---

## Common Fixes Checklist

### Q1 (real_impl ∨ throw)
- [ ] Method body not empty (has real logic)
- [ ] OR throws UnsupportedOperationException
- [ ] Exception message includes implementation guide
- [ ] No stub returns (empty string, 0, null, Collections.empty)

### Q2 (¬mock)
- [ ] No mock/stub/fake/test/demo/sample in method names
- [ ] No mock/stub/fake/test/demo/sample in class names
- [ ] No mock mode flags (useMockData, MOCK_MODE, etc)
- [ ] All names describe real purpose

### Q3 (¬silent_fallback)
- [ ] No catch blocks that return fake data
- [ ] No empty catch blocks
- [ ] All catches either re-throw or throw new exception
- [ ] If logging, must also throw
- [ ] No getOrDefault with fake values

### Q4 (¬lie)
- [ ] Javadoc claims match actual implementation
- [ ] Method name matches behavior
- [ ] Exception handling matches @throws declarations
- [ ] Return value semantics match documentation
- [ ] No magical "always succeeds" behavior

---

## SPARQL Query Structure

All Q phase queries follow this pattern:

```sparql
PREFIX code: <http://yawl.org/code#>

SELECT ?violation ?method_name ?line ?severity
WHERE {
  # 1. FIND: Methods matching pattern
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line .

  # 2. FILTER: Condition for violation
  FILTER(REGEX(?body, 'empty_pattern'))

  # 3. EXCLUDE: False positives (allowed cases)
  FILTER(!REGEX(?body, 'UnsupportedOperationException'))

  # 4. BIND: Violation details
  BIND("Q_INVARIANT_NAME" AS ?violation)
  BIND("FAIL" AS ?severity)
}
ORDER BY ?method_name ?line
```

---

## When Q Phase Fails

### 1. Review Report
```bash
yawl godspeed verify --report json > report.json
cat report.json | jq '.violations[]'
```

### 2. Understand Violation Type
- Q1: Empty/stub? → Implement or throw
- Q2: Mock name? → Rename or delete
- Q3: Silent catch? → Add throw or propagate
- Q4: Behavior mismatch? → Fix code or docs

### 3. Fix Code
- Implement real logic OR
- Throw UnsupportedOperationException with guide OR
- Update documentation to match behavior

### 4. Re-run Verification
```bash
yawl godspeed verify --verbose
```

### 5. Proceed to Consolidation
Once GREEN, safe to commit.

---

## Examples by Invariant

### Q1 Violation Example
```java
// ❌ VIOLATION
public List<String> getErrors() {
    return Collections.emptyList();
}

// ✅ FIX #1 (Real implementation)
public List<String> getErrors() {
    try {
        validator.validate(input);
        return validator.getErrors();
    } catch (ValidationException e) {
        return List.of(e.getMessage());
    }
}

// ✅ FIX #2 (Honest exception)
public List<String> getErrors() {
    throw new UnsupportedOperationException(
        "Error reporting requires validation engine initialization. " +
        "See ValidationEngine.md for setup steps."
    );
}
```

### Q2 Violation Example
```java
// ❌ VIOLATION
public class FakeAuthService implements AuthService {
    @Override
    public boolean authenticate(String user, String pass) {
        return true;  // Always succeeds!
    }
}

// ✅ FIX #1 (Real implementation)
public class JwtAuthService implements AuthService {
    @Override
    public boolean authenticate(String user, String pass) {
        String hashedInput = hashPassword(pass);
        String storedHash = database.getPasswordHash(user);
        return hashedInput.equals(storedHash);
    }
}

// ✅ FIX #2 (Honest exception)
public class AuthService implements AuthService {
    @Override
    public boolean authenticate(String user, String pass) {
        throw new UnsupportedOperationException(
            "Authentication requires:\n" +
            "  1. PasswordHasher (bcrypt) configured\n" +
            "  2. UserRepository initialized\n" +
            "See AuthSetup.md for implementation"
        );
    }
}
```

### Q3 Violation Example
```java
// ❌ VIOLATION (Silent fallback)
public Config loadConfig() {
    try {
        return configFile.read();
    } catch (IOException e) {
        log.warn("Config load failed, using defaults", e);
        return DEFAULT_CONFIG;  // LIES to caller!
    }
}

// ✅ FIX #1 (Fail fast with message)
public Config loadConfig() {
    try {
        return configFile.read();
    } catch (IOException e) {
        throw new ConfigurationException(
            "Failed to load config from " + configFile.getAbsolutePath() +
            ". Check file permissions and format.",
            e
        );
    }
}

// ✅ FIX #2 (No catch, let it propagate)
public Config loadConfig() throws IOException {
    return configFile.read();  // IOException propagates to caller
}
```

### Q4 Violation Example
```java
// ❌ VIOLATION (Code ≠ docs)
/**
 * Persists the workflow to the database.
 * @param workflow The workflow to save
 * @return true if saved successfully
 */
public boolean saveWorkflow(Workflow workflow) {
    log.info("Saving workflow: " + workflow.id());
    return true;  // LIES! Never actually saved!
}

// ✅ FIX #1 (Real persistence)
/**
 * Persists the workflow to the database.
 * @param workflow The workflow to save
 * @return true if saved successfully, false if already exists
 */
public boolean saveWorkflow(Workflow workflow) {
    try {
        int rowsAffected = database.insert(workflow);
        return rowsAffected > 0;  // Actually saved!
    } catch (SQLException e) {
        throw new DataAccessException("Failed to save workflow", e);
    }
}

// ✅ FIX #2 (Honest if unimplemented)
/**
 * Persists the workflow to the database.
 * @throws UnsupportedOperationException - Not yet implemented
 */
public boolean saveWorkflow(Workflow workflow) {
    throw new UnsupportedOperationException(
        "Workflow persistence requires:\n" +
        "  1. Database schema migration: db/migrations/001-workflows.sql\n" +
        "  2. WorkflowRepository implementation\n" +
        "  3. Transaction management setup\n" +
        "See WorkflowPersistence.md:78 for implementation pattern"
    );
}
```

---

## Production Checklist

Before consolidation, verify Q phase GREEN:

- [ ] Run `yawl godspeed verify --verbose`
- [ ] No violations reported
- [ ] Exit code = 0
- [ ] All methods implement real logic OR throw with guide
- [ ] No mock, stub, fake implementations
- [ ] No silent exception handling
- [ ] Code matches documentation
- [ ] Ready to commit

---

## Quick Links

- **Full SPARQL**: `.claude/sparql/invariants-q-phase.sparql`
- **CLI Guide**: `docs/GODSPEED_CLI_GUIDE.md`
- **Code Standards**: `.claude/HYPER_STANDARDS.md`
- **Phase Documentation**: `.claude/phases/PHASE-2-ARCHITECT-DELIVERABLES.md`

---

**Status**: Production Ready
**Version**: 6.0.0
**Last Updated**: 2026-02-22
