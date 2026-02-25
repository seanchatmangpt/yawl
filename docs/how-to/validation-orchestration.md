# YAWL Validation Orchestration System Guide

## Overview

The YAWL Validation Orchestration System provides a comprehensive framework for validating A2A (Agent-to-Agent) and MCP (Model Context Protocol) compliance, chaos testing, stress testing, and integration validation. The system is designed to ensure system resilience, performance, and interoperability across all components.

## Architecture

### Core Components

1. **validate-all.sh** - Main orchestrator that coordinates all validation phases
2. **validate-chaos-stress.sh** - Chaos engineering and stress testing
3. **validate-integration.sh** - End-to-end integration testing
4. **validate-a2a-compliance.sh** - A2A protocol compliance testing
5. **validate-mcp-compliance.sh** - MCP protocol compliance testing
6. **GitHub Workflows** - CI/CD automation for continuous validation

### Validation Phases

The validation system runs through these phases in sequence:

1. **Compile** - Fast compilation check
2. **Test** - Unit and integration tests
3. **Static Analysis** - Code quality and security scanning
4. **Observatory** - Codebase metrics and documentation validation
5. **Schema Validation** - XML schema compliance checking
6. **A2A Compliance** - Agent-to-Agent protocol validation
7. **MCP Compliance** - Model Context Protocol validation
8. **Chaos & Stress** - Resilience and performance testing
9. **Integration** - End-to-end workflow validation

## Usage

### Basic Usage

```bash
# Run all validations
./scripts/validate-all.sh

# Run with JSON output
./scripts/validate-all.sh --json

# Skip A2A and MCP tests for fast compilation check
./scripts/validate-all.sh --fast

# Run only specific validation phases
./scripts/validate-all.sh --a2a
./scripts/validate-all.sh --mcp
./scripts/validate-all.sh --chaos
./scripts/validate-all.sh --integration
```

### Advanced Options

```bash
# Enable parallel execution
./scripts/validate-all.sh --parallel

# Custom metrics directory
./scripts/validate-all.sh --metrics-dir ./validation-metrics

# Run with different report format
./scripts/validate-all.sh --report-format xml

# Run chaos testing with specific parameters
./scripts/validate-all.sh --chaos --concurrent 200 --duration 600

# Run integration testing with specific workflow
./scripts/validate-all.sh --integration --workflow performance
```

## Chaos Testing

The chaos testing system simulates adverse conditions to test system resilience:

### Scenarios

- **Network Chaos**: Latency, packet loss, network partition
- **CPU Stress**: High utilization, spike simulation
- **Memory Stress**: Memory pressure, leak simulation
- **Database Chaos**: Connection pool exhaustion, slow queries
- **Application Stress**: Concurrent requests, memory leak simulation
- **Resilience**: Service restart during load, recovery after failure

### Usage

```bash
# Run all chaos tests
./scripts/validation/validate-chaos-stress.sh

# Run specific scenario
./scripts/validation/validate-chaos-stress.sh --scenario network

# Configure parameters
./scripts/validation/validate-chaos-stress.sh \
  --concurrent 100 \
  --duration 300 \
  --threshold 3 \
  --metrics-dir ./chaos-metrics
```

## Integration Testing

The integration testing validates end-to-end workflows and system interoperability:

### Test Categories

- **Basic Integration**: Engine to MCP, A2A to Engine, Cross-protocol
- **Workflow Integration**: Deployment, execution, completion
- **Data Flow**: Parameter passing, data transformation
- **Event Processing**: Publishing, subscription, consumption
- **Error Handling**: Invalid workflows, concurrent execution limits
- **Performance**: Throughput, response time under load

### Usage

```bash
# Run all integration tests
./scripts/validation/validate-integration.sh

# Run specific workflow type
./scripts/validation/validate-integration.sh --workflow basic
./scripts/validation/validate-integration.sh --workflow performance

# Configure parameters
./scripts/validation/validate-integration.sh \
  --max-duration 300 \
  --retries 3 \
  --tracing ./integration-tracing.csv
```

## A2A Compliance Testing

Validates compliance with Agent-to-Agent protocol standards:

### Tests Include

- Agent Card Discovery
- Skills Validation
- Authentication (SPIFFE/JWT/API Key)
- Message Endpoints
- Error Responses

### Usage

```bash
# Run A2A compliance
./scripts/validation/a2a/validate-a2a-compliance.sh

# With verbose output
./scripts/validation/a2a/validate-a2a-compliance.sh --verbose

# JSON output
./scripts/validation/a2a/validate-a2a-compliance.sh --json
```

## MCP Compliance Testing

Validates compliance with Model Context Protocol:

### Tests Include

- Protocol Handshake
- Tools Validation
- Resources Validation
- Prompts Validation
- Completions Validation
- Error Handling

### Usage

```bash
# Run MCP compliance
./scripts/validation/mcp/validate-mcp-compliance.sh

# With verbose output
./scripts/validation/mcp/validate-mcp-compliance.sh --verbose

# JSON output
./scripts/validation/mcp/validate-mcp-compliance.sh --json
```

## Metrics and Reporting

### Metrics Collection

The system collects comprehensive metrics:

- **Execution Time**: Phase durations
- **Test Results**: Pass/fail counts, success rates
- **Performance**: Throughput, response times
- **Resource Usage**: CPU, memory, network
- **Error Rates**: Categorized by type

### Report Formats

#### JSON Format
```json
{
  "timestamp": "2026-02-18T12:00:00Z",
  "results": {
    "total_tests": 150,
    "passed_tests": 148,
    "failed_tests": 2,
    "success_rate": 98.7
  },
  "metrics": {
    "duration_seconds": 120,
    "throughput": 2.5
  }
}
```

#### XML Format
```xml
<validation-report>
  <timestamp>2026-02-18T12:00:00Z</timestamp>
  <results>
    <total-tests>150</total-tests>
    <passed-tests>148</passed-tests>
    <failed-tests>2</failed-tests>
    <success-rate>98.7</success-rate>
  </results>
</validation-report>
```

## CI/CD Integration

### GitHub Workflows

The system includes several GitHub workflows:

1. **validation.yml** - Main validation pipeline
2. **parallel-validation.yml** - Parallel execution
3. **a2a-compliance.yml** - A2A specific validation
4. **mcp-compliance.yml** - MCP specific validation
5. **chaos-testing.yml** - Chaos engineering pipeline

### Quality Gates

The system enforces these quality gates:

- **Compilation**: Must pass without errors
- **Tests**: 100% pass rate
- **Analysis**: No critical issues
- **Compliance**: 100% protocol compliance
- **Performance**: > 1 workflow/second throughput
- **Resilience**: < 5% error rate under chaos

### Artifacts

Generated artifacts include:

- JSON/XML validation reports
- Metrics files
- Test results
- Performance charts
- Compliance certificates

## Configuration

### Environment Variables

```bash
# A2A Server Configuration
export A2A_SERVER_HOST=localhost
export A2A_SERVER_PORT=8080
export A2A_SERVER_TIMEOUT=30

# MCP Server Configuration
export MCP_SERVER_HOST=localhost
export MCP_SERVER_PORT=9090
export MCP_SERVER_TIMEOUT=30

# Test Configuration
export CONCURRENT_USERS=100
export TEST_DURATION=300
export FAILURE_THRESHOLD=5
```

### Configuration Files

Create `validation.conf` for custom settings:

```ini
[validation]
parallel_execution=true
enable_metrics=true
report_format=json

[chaos]
concurrent_users=100
duration_seconds=300
failure_threshold=5

[integration]
max_duration=300
retry_attempts=3
enable_tracing=true
```

## Troubleshooting

### Common Issues

1. **Services Not Running**
   ```bash
   # Check service status
   curl http://localhost:8080/health
   curl http://localhost:8081/health
   curl http://localhost:9090/health
   ```

2. **Permission Denied**
   ```bash
   # Make scripts executable
   chmod +x scripts/validate-all.sh
   chmod +x scripts/validation/*.sh
   ```

3. **Port Conflicts**
   ```bash
   # Check if ports are in use
   lsof -i :8080
   lsof -i :8081
   lsof -i :9090
   ```

### Debug Mode

Enable verbose logging:

```bash
# All commands accept --verbose flag
./scripts/validate-all.sh --verbose
./scripts/validation/validate-chaos-stress.sh --verbose
./scripts/validation/validate-integration.sh --verbose
```

### Log Analysis

Logs are organized by component:

```
/tmp/validation-metrics/
├── validation-metrics.json
├── chaos-metrics.json
├── integration-metrics.json
└── comprehensive-report.json
```

## Best Practices

1. **Regular Validation**
   - Run full validation suite before releases
   - Schedule automated chaos tests weekly
   - Monitor performance metrics continuously

2. **Performance Tuning**
   - Adjust concurrent user count based on system capacity
   - Set appropriate timeouts for your environment
   - Configure failure thresholds based on SLAs

3. **Monitoring**
   - Archive validation results for trend analysis
   - Set up alerts for critical failures
   - Track performance regressions

4. **Documentation**
   - Update workflows as system evolves
   - Document new compliance requirements
   - Maintain test scenarios for disaster recovery

## Extending the System

### Adding New Validation Phases

1. Create new validation script
2. Add phase to validate-all.sh
3. Update GitHub workflows
4. Add to documentation

### Custom Metrics

Extend metrics collection:

```bash
# Add custom metric
collect_metric "custom_metric" $value
```

### Custom Scenarios

Add chaos scenarios:

```bash
# In validate-chaos-stress.sh
run_custom_chaos_tests() {
    # Your custom test logic
}
```

## Support

For issues and questions:

1. Check the troubleshooting section
2. Review logs for error messages
3. Consult the YAWL documentation
4. Submit GitHub issues with detailed reproduction steps

## Contributing

To contribute to the validation system:

1. Follow existing code patterns
2. Add comprehensive tests for new features
3. Update documentation
4. Ensure backward compatibility

---

*This document is part of the YAWL Validation Orchestration System. For the latest updates, see the [GitHub repository](https://github.com/yawlfoundation/yawl).*