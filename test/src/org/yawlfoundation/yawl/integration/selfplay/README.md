# Self-Play Test Orchestrator for YAWL

The Self-Play Test Orchestrator provides automated testing capabilities for YAWL workflows through a complete cycle: XML generation → Validation → Upload → Execution → Verification.

## Features

- **Automated Workflow Generation**: Generate YAWL workflow XML using AI (Z.ai) or templates
- **XML Validation**: Validate workflow specifications against YAWL Schema 4.0
- **Multi-Iteration Testing**: Run multiple test iterations with configurable parameters
- **Metrics Collection**: Capture detailed timing and performance metrics
- **Execution Verification**: Verify workflow execution outcomes and work item completion
- **Comprehensive Reporting**: Generate JSON reports with detailed test results

## Architecture

```
SelfPlayOrchestrator (main orchestrator)
├── SelfPlayConfig (configuration management)
├── ZaiWorkflowGenerator (AI integration)
└── SelfPlayOrchestratorTest (unit tests)
```

## Quick Start

### Standalone Execution

```bash
# Run with default settings
java org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator

# Custom configuration
java org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator \
    --engine-url http://localhost:8080/yawl \
    --iterations 5 \
    --use-zai
```

### Maven Test

```bash
# Run tests
mvn test -Dtest=SelfPlayOrchestratorTest

# Run specific test
mvn test -Dtest=SelfPlayOrchestratorTest#testXmlGeneration
```

## Configuration

### Environment Variables

```bash
# YAWL Engine configuration
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=admin

# Test configuration
export YAWL_ITERATIONS=5
export YAWL_TIMEOUT_MS=30000
export YAWL_USE_ZAI=true

# Output directory
export YAWL_OUTPUT_DIR=./test-results
```

### Command Line Arguments

```bash
java org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator \
    --engine-url <url>     # YAWL engine URL
    --username <user>     # Username for authentication
    --password <pass>     # Password for authentication
    --iterations <n>      # Number of test iterations
    --timeout <ms>         # Timeout in milliseconds
    --use-zai             # Enable Z.ai integration
    --output-dir <path>   # Output directory for reports
```

### Configuration Files

Create a `selfplay.properties` file:

```properties
engine.url=http://localhost:8080/yawl
username=admin
password=admin
iterations=5
timeout.ms=30000
use.zai=true
output.dir=./test-results
```

## Workflow Generation

### Using Z.ai Integration

Enable Z.ai integration by setting `--use-zai` or `YAWL_USE_ZAI=true`:

```bash
java org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator --use-zai
```

The orchestrator will use Z.ai to generate workflow XML. When Z.ai is unavailable, it automatically falls back to template-based generation.

### Template Generation

When Z.ai is disabled, the orchestrator generates simple sequence workflows:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="sequenceWorkflow_...">
    <metaData>
      <title>Sequence Test X</title>
      <description>A simple sequential workflow</description>
    </metaData>
    <decomposition id="mainFlow" xsi:type="NetFactsType" isRootNet="true">
      <!-- Sequence of 2-3 tasks -->
    </decomposition>
  </specification>
</specificationSet>
```

## Test Execution Cycle

Each test iteration follows this cycle:

1. **XML Generation**: Generate workflow XML using Z.ai or templates
2. **Validation**: Validate XML against YAWL Schema
3. **Upload**: Upload specification to YAWL engine
4. **Execution**: Launch and execute workflow
5. **Verification**: Verify work item completion and outcomes

## Metrics Collection

The orchestrator captures metrics at each stage:

```json
{
  "metrics": {
    "iteration.0.generation.duration": 1200,
    "iteration.0.validation.duration": 500,
    "iteration.0.upload.duration": 3000,
    "iteration.0.execution.duration": 15000,
    "iteration.0.verification.duration": 1000,
    "total.duration": 20700
  }
}
```

## Reports

Generated reports include:

- `self-play-report.json`: Comprehensive test results in JSON format
- Console output with progress tracking
- Individual iteration results with success/failure status

## Error Handling

The orchestrator includes comprehensive error handling:

- **Connection Errors**: Graceful handling of engine connection failures
- **Timeout Management**: Configurable timeouts for long-running operations
- **XML Validation**: Detailed validation error reporting
- **Retry Logic**: Automatic fallback when Z.ai is unavailable
- **Exception Recovery**: Continue with other iterations if one fails

## Advanced Usage

### Custom Workflow Templates

Extend the orchestrator to support custom workflow patterns:

```java
public String generateParallelWorkflow(int iterationNumber) {
    // Generate XML with parallel split/synchronization patterns
}
```

### Custom Validation

Add additional validation rules:

```java
private boolean validateWorkflows(String xmlSpec) {
    // Custom validation logic
    return true;
}
```

### Custom Metrics

Extend metrics collection:

```java
metrics.put("custom.metric.value", customValue);
```

## Performance Considerations

- **Virtual Threads**: Uses Java 25 virtual threads for concurrent operations
- **Parallel Execution**: Multi-threaded test execution
- **Connection Pooling**: Reusable HTTP client connections
- **Memory Management**: Proper resource cleanup and shutdown

## Integration with CI/CD

### Maven Integration

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>3.1.2</version>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Jenkins Pipeline

```groovy
pipeline {
  agent any
  stages {
    stage('Self-Play Test') {
      steps {
        sh 'mvn test -Dtest=SelfPlayOrchestrator'
      }
    }
  }
}
```

## Troubleshooting

### Common Issues

1. **Engine Connection**
   - Verify YAWL engine is running
   - Check URL and credentials
   - Test with manual InterfaceB client

2. **XML Validation Errors**
   - Check XML format compliance
   - Verify namespace declarations
   - Review schema validation logs

3. **Z.ai Integration**
   - Verify API key is set
   - Check network connectivity
   - Review API rate limits

4. **Timeout Issues**
   - Increase timeout value
   - Check engine performance
   - Review workflow complexity

### Debug Mode

Enable debug logging:

```java
System.setProperty("java.util.logging.config.file", "logging.properties");
```

## Contributing

### Adding New Features

1. Create new test cases in `SelfPlayOrchestratorTest`
2. Implement the feature in the main class
3. Add documentation
4. Run comprehensive tests

### Code Style

- Follow Java 25 conventions
- Use virtual threads for I/O operations
- Include comprehensive error handling
- Add proper logging

## License

This component is part of the YAWL Foundation project and is distributed under the GNU Lesser General Public License.

## Support

For issues and feature requests:
- Create an issue on the YAWL GitHub repository
- Check the main YAWL documentation
- Contact the YAWL Foundation team