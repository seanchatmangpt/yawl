# YAWL v6.0.0 Shell Test Suite

## Overview

YAWL uses a comprehensive shell-based black-box testing framework for integration and end-to-end testing. These tests validate the system from an external perspective without knowledge of internal implementation.

**Test Location**: `test/shell/`
**Framework Location**: `scripts/shell-test/`

## Philosophy

**No Lies, No Mocks, No Stubs**

Shell tests verify real system behavior:
- Real HTTP requests to running services
- Real process lifecycle management
- Real file system operations
- Real protocol interactions

---

## Running Shell Tests

### Run All Phases

```bash
# Run all 8 phases
./scripts/shell-test/runner.sh

# Run with verbose output
./scripts/shell-test/runner.sh -v

# Stop on first failure
./scripts/shell-test/runner.sh --stop-on-failure
```

### Run Quick Tests (Phases 1-3)

```bash
# Quick validation (schema, stubs, build)
./scripts/shell-test/runner.sh -q
```

### Run Specific Phase

```bash
# Run single phase
./scripts/shell-test/runner.sh -p 04

# Run engine lifecycle tests only
./scripts/shell-test/runner.sh -p 04 -v
```

### Custom Report Directory

```bash
./scripts/shell-test/runner.sh --report-dir ./custom-reports
```

---

## Test Phases (8 Phases)

### Phase 01: Schema Validation

**Directory**: `test/shell/01-schema-validation/`
**Purpose**: Validate all XML specifications against XSD schemas

**What It Tests**:
- YAWL specification files against `YAWL_Schema4.0.xsd`
- Well-formedness of all XML files
- Schema compliance for workflow definitions

**Key Assertions**:
```bash
assert_xmllint_valid "$spec_file" "$schema_file"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 01
```

---

### Phase 02: Stub Detection

**Directory**: `test/shell/02-stub-detection/`
**Purpose**: Scan source code for forbidden patterns

**Forbidden Patterns**:
- `UnsupportedOperationException` (except explicitly allowed)
- `TODO`, `FIXME`, `XXX`, `HACK` comments
- `mock`, `stub`, `fake` keywords in production code
- Empty method bodies
- Placeholder implementations

**Key Assertions**:
```bash
assert_no_pattern_in_source "TODO"
assert_no_pattern_in_source "FIXME"
assert_no_pattern_in_source "throw new UnsupportedOperationException"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 02
```

---

### Phase 03: Build Verification

**Directory**: `test/shell/03-build-verification/`
**Purpose**: Verify successful compilation and artifact generation

**What It Tests**:
- `mvn compile` succeeds
- Expected JAR/WAR artifacts exist
- No compilation warnings (configurable)
- Build outputs are valid

**Key Assertions**:
```bash
assert_file_exists "$BUILD_DIR/yawl-engine.jar"
assert_build_success
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 03
```

---

### Phase 04: Engine Lifecycle

**Directory**: `test/shell/04-engine-lifecycle/`
**Purpose**: Test YAWL engine startup, health, and shutdown

**What It Tests**:
- Engine starts successfully
- Health endpoint returns 200
- Specification listing works
- Clean shutdown without errors

**Configuration**:
```bash
ENGINE_PORT="${ENGINE_PORT:-8080}"
ENGINE_STARTUP_TIMEOUT="${ENGINE_STARTUP_TIMEOUT:-60}"
```

**Key Assertions**:
```bash
wait_for_port "localhost" "$ENGINE_PORT" 60
assert_http_ok "http://localhost:8080/health"
assert_response_contains "$ENGINE_URL/specs" "YSpecification"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 04
```

---

### Phase 05: A2A Protocol

**Directory**: `test/shell/05-a2a-protocol/`
**Purpose**: Test Agent-to-Agent protocol implementation

**What It Tests**:
- Agent card endpoint availability
- JSON-RPC 2.0 protocol compliance
- Task management operations
- Message handling

**Configuration**:
```bash
A2A_PORT="${A2A_PORT:-8082}"
A2A_STARTUP_TIMEOUT="${A2A_STARTUP_TIMEOUT:-30}"
```

**Key Assertions**:
```bash
assert_http_ok "$A2A_URL/.well-known/agent.json"
assert_json_has_field "$response" "capabilities"
assert_json_array_not_empty "$response" "capabilities"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 05
```

---

### Phase 06: MCP Protocol

**Directory**: `test/shell/06-mcp-protocol/`
**Purpose**: Test Model Context Protocol implementation

**What It Tests**:
- MCP protocol handshake
- Tool listing and discovery
- Tool execution
- Resource access

**Configuration**:
```bash
MCP_PORT="${MCP_PORT:-3000}"
MCP_TRANSPORT="${MCP_TRANSPORT:-HTTP}"
```

**Key Assertions**:
```bash
assert_http_ok "$MCP_URL/tools"
assert_json_has_field "$response" "tools"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 06
```

---

### Phase 07: Workflow Patterns

**Directory**: `test/shell/07-workflow-patterns/`
**Purpose**: Test YAWL workflow pattern implementations

**What It Tests**:
- Load specification
- Start case
- Get work items
- Checkout/complete work items
- Case completion verification

**Configuration**:
```bash
ENGINE_PORT="${ENGINE_PORT:-8080}"
ENGINE_USER="${ENGINE_USER:-admin}"
ENGINE_PASS="${ENGINE_PASS:-YAWL}"
```

**Key Assertions**:
```bash
assert_http_ok "$ENGINE_URL/specs/load?file=$spec_file"
assert_json_has_field "$response" "caseID"
assert_json_array_not_empty "$workitems" "workItems"
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 07
```

---

### Phase 08: Integration Report

**Directory**: `test/shell/08-integration/`
**Purpose**: Generate comprehensive test reports

**What It Does**:
- Collects all test results
- Generates JUnit XML for CI/CD
- Generates Markdown summary for humans
- Generates JSON metrics for dashboards

**Output Files**:
```
reports/
├── junit.xml          # JUnit XML format
├── summary.md         # Human-readable summary
├── metrics.json       # Dashboard metrics
└── test-results/      # Detailed results per phase
```

**Run Command**:
```bash
./scripts/shell-test/runner.sh -p 08
```

---

## Assertion Library

The shell test framework provides a comprehensive assertion library.

### Basic Assertions

```bash
# Equality
assert_equals "expected" "actual" "Values should match"

# Non-empty
assert_not_empty "$value" "Value should not be empty"

# Boolean
assert_true "Condition should be true"
assert_false "Condition should be false"
```

### HTTP Assertions

```bash
# HTTP status code
assert_http_ok "$url" "Endpoint should return 200"
assert_http_code "404" "$url" "Endpoint should return 404"

# Response content
assert_response_contains "$url" "pattern" "Response should contain pattern"
```

### JSON Assertions

```bash
# Field existence
assert_json_has_field "$json" "fieldName" "JSON should have field"

# Field value
assert_json_field "$json" "fieldName" "expectedValue" "Field should match"

# Array length
assert_json_array_length "$json" "arrayField" ">=" "1" "Array should have items"
assert_json_array_not_empty "$json" "arrayField" "Array should not be empty"
```

### File Assertions

```bash
# File/Directory existence
assert_file_exists "$filepath" "File should exist"
assert_dir_exists "$dirpath" "Directory should exist"
```

### Process Assertions

```bash
# Process state
assert_process_running "$pid" "Process should be running"
assert_process_stopped "$pid" "Process should be stopped"

# Port availability
assert_port_open "localhost" "8080" "Port should be open"
assert_port_closed "localhost" "8080" "Port should be closed"
```

### Wait Utilities

```bash
# Wait for port
wait_for_port "localhost" "8080" 30 "Waiting for engine"

# Wait for HTTP endpoint
wait_for_http "http://localhost:8080/health" 30 "Waiting for health endpoint"
```

---

## Test File Structure

```
test/shell/
├── 01-schema-validation/
│   └── run.sh              # Phase 1 test script
├── 02-stub-detection/
│   └── run.sh              # Phase 2 test script
├── 03-build-verification/
│   └── run.sh              # Phase 3 test script
├── 04-engine-lifecycle/
│   └── run.sh              # Phase 4 test script
├── 05-a2a-protocol/
│   └── run.sh              # Phase 5 test script
├── 06-mcp-protocol/
│   └── run.sh              # Phase 6 test script
├── 07-workflow-patterns/
│   └── run.sh              # Phase 7 test script
└── 08-integration/
    └── run.sh              # Phase 8 test script

scripts/shell-test/
├── runner.sh               # Main test runner
├── assert.sh               # Assertion library
├── http-client.sh          # HTTP utilities
├── process-manager.sh      # Process management
└── report.sh               # Report generation
```

---

## Writing New Tests

### Creating a New Phase

1. Create directory: `test/shell/09-my-feature/`
2. Create `run.sh`:

```bash
#!/bin/bash
#
# Phase 09: My Feature Tests
#
# Description of what this phase tests.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"

# Source libraries
source "$PROJECT_DIR/scripts/shell-test/assert.sh"
source "$PROJECT_DIR/scripts/shell-test/http-client.sh"

echo "==========================================="
echo "Phase 09: My Feature Tests"
echo "==========================================="

# Test 1: Feature works
echo "Test 1: Feature works correctly"
assert_equals "expected" "actual" "Feature should work"

# Test 2: Error handling
echo "Test 2: Error handling"
assert_http_code "400" "$URL/invalid" "Invalid request returns 400"

echo ""
echo "Phase 09: All tests passed"
```

3. Make executable: `chmod +x test/shell/09-my-feature/run.sh`
4. Update `runner.sh` to include phase 09

### Test Script Template

```bash
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"

source "$PROJECT_DIR/scripts/shell-test/assert.sh"

# Configuration
MY_PORT="${MY_PORT:-8080}"
MY_URL="http://localhost:$MY_PORT"

echo "==========================================="
echo "Phase XX: Test Description"
echo "==========================================="

# Setup
setup() {
    # Start services, create test data
}

# Teardown
teardown() {
    # Stop services, cleanup
}

# Tests
test_feature_a() {
    echo "Test: Feature A"
    assert_equals "expected" "actual" "Feature A should work"
}

test_feature_b() {
    echo "Test: Feature B"
    assert_http_ok "$MY_URL/feature-b" "Feature B endpoint works"
}

# Main
trap teardown EXIT
setup
test_feature_a
test_feature_b

echo ""
echo "Phase XX: All tests passed"
```

---

## CI/CD Integration

### GitHub Actions

```yaml
- name: Run Shell Tests
  run: |
    ./scripts/shell-test/runner.sh --no-color
  timeout-minutes: 30

- name: Upload Test Reports
  uses: actions/upload-artifact@v4
  with:
    name: shell-test-reports
    path: reports/
```

### Jenkins

```groovy
stage('Shell Tests') {
    steps {
        sh './scripts/shell-test/runner.sh --no-color'
    }
    post {
        always {
            junit 'reports/junit.xml'
            archiveArtifacts artifacts: 'reports/**/*'
        }
    }
}
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ENGINE_PORT` | 8080 | YAWL engine HTTP port |
| `ENGINE_USER` | admin | Engine authentication user |
| `ENGINE_PASS` | YAWL | Engine authentication password |
| `ENGINE_STARTUP_TIMEOUT` | 60 | Engine startup timeout (seconds) |
| `A2A_PORT` | 8082 | A2A protocol port |
| `A2A_STARTUP_TIMEOUT` | 30 | A2A startup timeout (seconds) |
| `MCP_PORT` | 3000 | MCP server port |
| `MCP_TRANSPORT` | HTTP | MCP transport (HTTP/STDIO) |
| `MCP_STARTUP_TIMEOUT` | 30 | MCP startup timeout (seconds) |
| `REPORT_DIR` | ./reports | Report output directory |
| `VERBOSE` | false | Enable verbose output |
| `NO_COLOR` | unset | Disable colored output |

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Port already in use | Previous test didn't clean up | Kill process on port |
| Timeout waiting for port | Service failed to start | Check service logs |
| Assertion failed | Test expectation not met | Check actual vs expected |
| Permission denied | Script not executable | `chmod +x run.sh` |

### Debug Mode

```bash
# Enable verbose output
VERBOSE=true ./scripts/shell-test/runner.sh -p 04

# Run single test with debug
bash -x test/shell/04-engine-lifecycle/run.sh
```

### Cleanup Orphaned Processes

```bash
# Kill any processes on test ports
pkill -f "yawl.*8080"
pkill -f "mcp.*3000"
```

---

## References

- **Framework Source**: `scripts/shell-test/`
- **Test Scripts**: `test/shell/`
- **Report Output**: `reports/`
- **Main Runner**: `scripts/shell-test/runner.sh`
