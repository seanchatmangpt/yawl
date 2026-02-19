# E2E MCP/A2A Workflow Test Suite

This directory contains end-to-end test scripts for validating YAWL's MCP (Model Context Protocol) and A2A (Agent-to-Agent) protocol integrations.

## Overview

The E2E test suite provides comprehensive testing of:
- MCP protocol integration with YAWL workflow engine
- A2A protocol integration with YAWL workflow engine
- Cross-protocol workflows
- Authentication and error handling

## File Structure

```
scripts/test-e2e-mcp-a2a/
├── lib/                           # Shared test libraries
│   ├── workflow-common.sh         # Common workflow functions
│   └── workflow-assertions.sh     # Test assertion functions
├── specs/                         # Test specifications
│   └── minimal-test.xml          # Minimal workflow for testing
├── test-mcp-workflow.sh          # MCP workflow test
├── test-a2a-workflow.sh          # A2A workflow test
├── run-e2e-suite.sh              # Main test orchestrator
└── README.md                     # This file
```

## Prerequisites

1. **Running Servers**:
   - MCP Server on localhost:9090
   - A2A Server on localhost:8081
   - YAWL Engine on localhost:8080

2. **Dependencies**:
   - curl (HTTP requests)
   - nc (netcat for TCP connections)
   - jq (JSON processing)
   - Python3 (for XML to JSON conversion)

## Running Tests

### Quick Start

```bash
# Run all tests
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --all

# Run only MCP tests
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --mcp

# Run only A2A tests
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --a2a
```

### Advanced Options

```bash
# Run tests concurrently (faster)
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --all --concurrent

# Generate JSON reports
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --all --json

# Clean previous results
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --force-clean

# Enable verbose logging
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --all --verbose
```

### Individual Test Suites

```bash
# Run MCP workflow tests directly
bash scripts/test-e2e-mcp-a2a/test-mcp-workflow.sh --verbose

# Run A2A workflow tests directly
bash scripts/test-e2e-mcp-a2a/test-a2a-workflow.sh --verbose
```

## Test Coverage

### MCP Tests (7 test functions)
1. **Protocol Initialization** - MCP connection setup
2. **Specification Upload** - Upload workflow via MCP
3. **Case Status Check** - Monitor case execution
4. **Work Items Retrieval** - Get work items for case
5. **Workflow Completion** - Verify case completion
6. **Error Handling** - Test error responses
7. **Resource Access** - Test MCP resources

### A2A Tests (8 test functions)
1. **Agent Card Discovery** - Discover agent capabilities
2. **Skills Registration** - Validate skill registration
3. **Workflow Launch** - Launch workflow via A2A
4. **Case Query** - Query case status
5. **Workflow Monitoring** - Monitor progress
6. **Authentication** - Test authentication mechanisms
7. **Message Patterns** - Validate message formats
8. **Error Responses** - Test error handling

## Output

### Text Reports
- Console output with colored indicators
- Detailed test results in terminal

### JSON Reports
- Saved to `docs/v6/latest/e2e-reports/`
- Individual reports for each test suite
- Combined summary report
- Latest symlinks for quick access

### Report Structure
```
docs/v6/latest/e2e-reports/
├── latest-mcp.json            # Latest MCP results
├── latest-a2a.json            # Latest A2A results
├── latest-combined.json       # Combined results
├── E2E_TEST_SUMMARY.md        # Text summary
└── latest.md                  # Symlink to latest summary
```

## Test Configuration

### Environment Variables
```bash
# Server URLs
export YAWL_ENGINE_URL=http://localhost:8080
export MCP_SERVER_URL=http://localhost:9090
export A2A_SERVER_URL=http://localhost:8081

# Test timeouts
export TEST_TIMEOUT=120

# Verbosity
export VERBOSE=1
```

### Test Specification
The test uses `minimal-test.xml` which contains:
- Simple workflow with one task
- Straight-through execution
- No complex control flows
- Minimal data requirements

## Troubleshooting

### Common Issues

1. **Servers not running**
   - Check if MCP server is on port 9090
   - Check if A2A server is on port 8081
   - Start servers using respective scripts

2. **Port conflicts**
   - Change ports in environment variables
   - Kill conflicting processes:
     ```bash
     lsof -ti:9090 | xargs kill -9  # MCP
     lsof -ti:8081 | xargs kill -9  # A2A
     ```

3. **Test timeouts**
   - Increase `TEST_TIMEOUT` variable
   - Check network connectivity
   - Verify server responsiveness

4. **JSON parsing errors**
   - Ensure `jq` is installed
   - Check Python3 availability
   - Verify XML file format

### Debug Mode

```bash
# Enable verbose logging
export VERBOSE=1

# Clean and run tests
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --force-clean --verbose
```

## Integration

### CI/CD Integration

The JSON output format makes it easy to integrate with CI/CD pipelines:

```bash
#!/bin/bash
# Example CI script
bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh --json

if [[ $? -eq 0 ]]; then
    echo "All tests passed!"
    exit 0
else
    echo "Tests failed!"
    exit 1
fi
```

### Monitoring

Set up monitoring for test reports:

```bash
# Check latest test status
cat docs/v6/latest/e2e-reports/latest.md

# Check MCP results
cat docs/v6/latest/e2e-reports/latest-mcp.json | jq '.summary'

# Check A2A results
cat docs/v6/latest/e2e-reports/latest-a2a.json | jq '.summary'
```

## Contributing

### Adding New Tests

1. Create test function in appropriate script
2. Use assertion functions from `workflow-assertions.sh`
3. Add test to appropriate test array
4. Update documentation with new test coverage

### Test Best Practices

- Use descriptive test names
- Follow existing assertion patterns
- Include error handling
- Log important steps
- Clean up after tests
- Use environment variables for configuration

## License

This test suite is part of the YAWL project and follows the same license terms.