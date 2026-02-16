# YAWL Test Generation Templates

Tera-based templates for generating JUnit 4/5 tests from specifications. Chicago TDD style with real integrations, not mocks.

## Templates

| Template | Purpose | Input |
|----------|---------|-------|
| `specification-test.tera` | Generate tests from YAWL XML specifications | JSON spec file |
| `exception-test.tera` | Generate comprehensive exception class tests | JSON spec file |
| `concurrency-test.tera` | Generate multi-threaded concurrent tests | JSON spec file |
| `suite.tera` | Generate JUnit 5 test suite aggregators | JSON spec file |
| `integration-test.tera` | Generate database integration tests | JSON spec file |

## Usage

### With Tera CLI

```bash
# Generate specification test
tera --template specification-test.tera \
     --spec spec.json \
     --output TestMySpecification.java

# Generate exception test
tera --template exception-test.tera \
     --spec exception.json \
     --output TestYWorkflowException.java

# Generate concurrency test
tera --template concurrency-test.tera \
     --spec concurrency.json \
     --output TestYEngineConcurrency.java

# Generate test suite
tera --template suite.tera \
     --spec suite.json \
     --output EngineTestSuite.java

# Generate integration test
tera --template integration-test.tera \
     --spec integration.json \
     --output DatabaseIntegrationTest.java
```

### With Java/Ant

```xml
<target name="generate-tests">
    <apply executable="tera" parallel="false">
        <arg value="--template"/>
        <arg value="templates/yawl-tests/specification-test.tera"/>
        <srcfile/>
        <fileset dir="test-specs" includes="*.json"/>
        <mapper type="glob" from="*.json" to="test/org/yawlfoundation/yawl/generated/*Test.java"/>
    </apply>
</target>
```

## Input JSON Schemas

### Specification Test

```json
{
  "package": "org.yawlfoundation.yawl.engine",
  "test_class": "TestMySpecification",
  "spec_file": "MySpecification.xml",
  "spec_id": "MySpec",
  "spec_uri": "MySpecification.xml",
  "root_net_id": "top",
  "junit_version": "5",
  "tasks": [
    {"id": "task-a", "name": "Task A", "type": "WebServiceGateway"},
    {"id": "task-b", "name": "Task B", "type": "MultipleInstanceExternal"}
  ],
  "input_params": [
    {"name": "requestId", "type": "xs:string"}
  ],
  "test_scenarios": [
    {"name": "BasicExecution", "description": "Tests basic specification execution"}
  ],
  "author": "YAWL Foundation",
  "version": "5.2"
}
```

### Exception Test

```json
{
  "package": "org.yawlfoundation.yawl.exceptions",
  "exception_class": "YWorkflowException",
  "parent_class": "YAWLException",
  "test_class": "TestYWorkflowException",
  "junit_version": "5",
  "constructors": [
    {"signature": "()", "params": [], "description": "Default constructor"},
    {"signature": "(String)", "params": [{"type": "String", "name": "message"}], "description": "Message constructor"},
    {"signature": "(String, Throwable)", "params": [{"type": "String", "name": "message"}, {"type": "Throwable", "name": "cause"}], "description": "Message and cause constructor"}
  ],
  "methods": [
    {"name": "getErrorCode", "return_type": "int", "description": "Returns error code"},
    {"name": "isRecoverable", "return_type": "boolean", "description": "Checks if error is recoverable"}
  ],
  "author": "YAWL Foundation",
  "version": "5.2"
}
```

### Concurrency Test

```json
{
  "package": "org.yawlfoundation.yawl.engine",
  "test_class": "TestYEngineConcurrency",
  "class_under_test": "YEngine",
  "junit_version": "5",
  "use_virtual_threads": true,
  "thread_counts": [10, 100, 1000],
  "concurrent_scenarios": [
    {
      "name": "ConcurrentCaseStart",
      "description": "Multiple threads starting cases simultaneously",
      "operation": "startCase",
      "thread_count": 100,
      "assertions": ["allCasesStart", "noExceptions", "noRaceConditions"]
    }
  ],
  "timeout_seconds": 60,
  "author": "YAWL Foundation",
  "version": "5.2"
}
```

### Suite

```json
{
  "package": "org.yawlfoundation.yawl.engine",
  "suite_name": "Engine",
  "suite_class": "EngineTestSuite",
  "test_classes": [
    {"class": "TestYEngineInit", "description": "Engine initialization tests"},
    {"class": "TestYNetRunner", "description": "Net runner tests"},
    {"class": "TestYWorkItem", "description": "Work item tests"}
  ],
  "execution_mode": "CONCURRENT",
  "fail_fast": true,
  "author": "YAWL Foundation",
  "version": "5.2"
}
```

### Integration Test

```json
{
  "package": "org.yawlfoundation.yawl.integration",
  "test_class": "DatabaseIntegrationTest",
  "class_under_test": "YEngine",
  "junit_version": "5",
  "database_type": "postgresql",
  "use_testcontainers": true,
  "test_scenarios": [
    {
      "name": "ConnectionEstablished",
      "description": "Verifies database connection works",
      "operation": "getConnection",
      "assertions": ["connectionNotNull", "connectionIsValid"]
    }
  ],
  "tables": [
    {"name": "yawl_case", "columns": ["id", "case_id", "spec_id", "status", "created_at"]}
  ],
  "cleanup_strategy": "rollback",
  "author": "YAWL Foundation",
  "version": "5.2"
}
```

## SHACL-to-JUnit Mapping

See `shacl-junit-mapping.md` for the complete specification on mapping SHACL shapes to JUnit tests.

## Generated Test Characteristics

All generated tests follow Chicago TDD (Detroit School) principles:

1. **Real Integrations**: Tests use real YAWL Engine instances, real databases
2. **No Mocks**: No mock objects, stubs, or fake implementations
3. **Comprehensive Coverage**: Tests cover happy paths, error cases, edge cases
4. **Clear Documentation**: Javadoc and @DisplayName annotations explain test purpose
5. **Proper Cleanup**: setUp/tearDown ensure clean test isolation

## JUnit Version Support

| Feature | JUnit 4 | JUnit 5 |
|---------|---------|---------|
| @DisplayName | No | Yes |
| @Nested | No | Yes |
| @Tag | @Category | Yes |
| @Timeout | @Test(timeout=) | @Timeout annotation |
| @RepeatedTest | No | Yes |
| Parameterized | Runner | Extension |

Set `junit_version` to `"4"` or `"5"` in your spec JSON.

## Template Filters

Custom Tera filters available:

| Filter | Description | Example |
|--------|-------------|---------|
| `pascal_case` | Convert to PascalCase | `"my_value" | pascal_case` -> `"MyValue"` |
| `snake_case` | Convert to snake_case | `"MyValue" | snake_case` -> `"my_value"` |
| `camel_case` | Convert to camelCase | `"my_value" | camel_case` -> `"myValue"` |

## Directory Structure

```
templates/yawl-tests/
  specification-test.tera    # XML spec -> JUnit test
  exception-test.tera        # Exception class -> comprehensive test
  concurrency-test.tera      # Concurrency scenario -> multi-threaded test
  suite.tera                 # Test class list -> JUnit 5 suite
  integration-test.tera      # Database config -> integration test
  shacl-junit-mapping.md     # SHACL constraint -> JUnit assertion mapping
  README.md                  # This file
  examples/                  # Example spec JSON files
    specification.json
    exception.json
    concurrency.json
    suite.json
    integration.json
```

## License

LGPL 3.0 - YAWL Foundation
