# Actor Validation System for YAWL v6.0.0

## Overview

The Actor Validation System provides comprehensive validation for actor-based code in YAWL, detecting memory leaks, deadlocks, and performance issues. It integrates seamlessly with the existing MCP/A2A infrastructure and follows YAWL's GODSPEED methodology.

## Architecture

### Core Components

1. **ActorValidator** - Main validation service
   - Memory leak detection (H_ACTOR_LEAK)
   - Deadlock detection (H_ACTOR_DEADLOCK)
   - Performance monitoring
   - Integration with MCP and A2A protocols

2. **ActorValidationService** - MCP integration
   - MCP tools for actor validation
   - Resource management
   - Health monitoring

3. **ActorValidationMetrics** - Metrics tracking
   - Validation history
   - Performance metrics
   - Health status

4. **ActorMcpIntegration** - Full MCP integration
   - Tool registration
   - Resource provision
   - Event handling

## Integration Points

### 1. MCP Server Integration

The actor validation system integrates with the YAWL MCP server through the `ActorMcpIntegration` class:

```java
// Integrated into YawlMcpServer.java
ActorMcpIntegration actorIntegration = new ActorMcpIntegration(this, observabilityService);
actorIntegration.initialize();
```

### 2. Maven Plugin

The `ActorValidationPlugin` provides Maven integration:

```xml
<plugin>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-ggen-plugin</artifactId>
    <version>6.0.0-GA</version>
    <configuration>
        <validationPhase>true</validationPhase>
        <actorValidationEnabled>true</actorValidationEnabled>
    </configuration>
    <executions>
        <execution>
            <id>actor-validation</id>
            <phase>validate</phase>
            <goals>
                <goal>validate-actors</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 3. Build Script

The `build-with-actor-validation.sh` script provides a complete build process:

```bash
./scripts/build-with-actor-validation.sh
```

## MCP Tools

### 1. Memory Leak Detection

```bash
# Check for memory leaks
curl -X POST "http://localhost:8080/mcp/actor_validate_memory" \
  -H "Content-Type: application/json" \
  -d '{"case_id": "case-123", "threshold_mb": 100}'
```

### 2. Deadlock Detection

```bash
# Check for deadlock potential
curl -X POST "http://localhost:8080/mcp/actor_validate_deadlock" \
  -H "Content-Type: application/json" \
  -d '{"case_id": "case-123"}'
```

### 3. Performance Monitoring

```bash
# Get performance metrics
curl -X POST "http://localhost:8080/mcp/actor_performance_metrics" \
  -H "Content-Type: application/json" \
  -d '{"case_id": "case-123", "time_range_minutes": 60}'
```

### 4. Health Checks

```bash
# Get health summary
curl -X POST "http://localhost:8080/mcp/actor_health_summary" \
  -H "Content-Type: application/json" \
  -d '{"case_id": "case-123"}'
```

## MCP Resources

### 1. Actor Validation Results

```bash
curl "http://localhost:8080/resources/actor_validation"
```

### 2. Actor Health Status

```bash
curl "http://localhost:8080/resources/actor_health"
```

### 3. Performance Metrics

```bash
curl "http://localhost:8080/resources/actor_performance"
```

## Configuration

### Environment Variables

```bash
# Enable/disable actor validation
export ACTOR_VALIDATION_ENABLED=true

# Set validation threshold (MB)
export ACTOR_MEMORY_THRESHOLD=100

# Set validation interval (seconds)
export ACTOR_VALIDATION_INTERVAL=30

# Enable verbose logging
export ACTOR_VALIDATION_VERBOSE=true
```

### Configuration File

```toml
# config/actor-validation.toml
[validation]
enabled = true
interval_seconds = 30
memory_threshold_mb = 100

[mcp]
enabled = true
port = 8080
host = "localhost"

[observability]
enabled = true
metrics_port = 9090
tracing_enabled = true
```

## Detection Patterns

### 1. Memory Leak Detection (H_ACTOR_LEAK)

Detects patterns that can cause memory leaks:

- Unbounded queue growth
- Resource accumulation without cleanup
- Circular references
- Unmanaged weak references

**Example Violation:**
```java
public void accumulateMessages(Actor actor) {
    while (true) {
        actor.putMessage("msg"); // Keeps growing
    }
    // No clearing mechanism
}
```

### 2. Deadlock Detection (H_ACTOR_DEADLOCK)

Detects deadlock potential:

- Circular waiting
- Nested locks with inconsistent ordering
- Unbounded blocking operations
- Resource ordering violations

**Example Violation:**
```java
public void deadlockRisk(Actor actor) {
    synchronized (actor) {
        actor.wait(); // Can cause deadlock
    }
}
```

### 3. Performance Monitoring

Monitors key performance metrics:

- Processing time
- Memory usage
- Thread states
- Lock contention

## Integration with YAWL Workflow

### 1. During Code Generation

1. Generate code with ggen
2. Run actor validation via Maven plugin
3. Fix any violations
4. Continue to Q phase (real implementation)

### 2. During Runtime

1. Actors created and initialized
2. Validator continuously monitors
3. MCP tools available for manual validation
4. Metrics available via observability

### 3. During CI/CD

1. Actor validation workflow runs on push/PR
2. Validation results uploaded as artifacts
3. PR comments created with results
4. Production gate requires passing validation

## Reports

### JSON Report

```json
{
  "phase": "actors",
  "timestamp": "2026-02-28T16:19:00Z",
  "files_scanned": 42,
  "violations": [
    {
      "pattern": "H_ACTOR_LEAK",
      "file": "src/main/java/ExampleActor.java",
      "line": 45,
      "content": "actor.putMessage(\"msg\")",
      "fix_guidance": "Implement bounded queue with periodic cleanup"
    }
  ],
  "status": "RED"
}
```

### HTML Report

Comprehensive HTML report with:
- Summary statistics
- Detailed violations
- Fix recommendations
- Performance metrics

## Best Practices

### 1. Prevention

1. Use bounded collections:
```java
private final BlockingQueue<Message> queue = new ArrayBlockingQueue<>(1000);
```

2. Implement proper cleanup:
```java
public void cleanup() {
    queue.clear();
    references.clear();
}
```

3. Use async messaging:
```java
actor.tellAsync(message, Duration.ofSeconds(5));
```

### 2. Monitoring

1. Set up monitoring alerts
2. Review metrics regularly
3. Use MCP tools for manual inspection
4. Integrate with observability dashboard

### 3. Response

1. Fix violations immediately
2. Update validation thresholds
3. Add monitoring for new patterns
4. Document successful fixes

## Troubleshooting

### Common Issues

1. **No violations detected**
   - Check that actor validation is enabled
   - Verify MCP server is running
   - Check integration configuration

2. **False positives**
   - Adjust detection thresholds
   - Update detection patterns
   - Review code structure

3. **Performance issues**
   - Check validation interval
   - Optimize monitoring
   - Use virtual threads

### Debug Commands

```bash
# Run manual validation
mvn validate -P actor-validation

# Check validation logs
tail -f target/validation-reports/actor-validation.log

# Check MCP resources
curl "http://localhost:8080/resources/actor_validation" | jq .

# Generate test report
./scripts/generate-actor-report.sh
```

## Testing

### Unit Tests

```bash
# Run actor validation tests
mvn test -Dtest=ActorValidationTest

# Run integration tests
mvn test -Dtest=ActorIntegrationTest

# Run performance tests
mvn test -Dtest=ActorPerformanceTest
```

### Test Data

Test fixtures are provided in:
- `test/resources/actor/violation-h-actor-leak.java`
- `test/resources/actor/violation-h-actor-deadlock.java`
- `test/resources/actor/clean-actor-code.java`

## Contributing

### Adding New Patterns

1. Create new SPARQL query in `rules/validation-phases/`
2. Update `ActorValidator.java`
3. Add MCP tool in `ActorValidationService.java`
4. Update test fixtures
5. Update documentation

### Performance Considerations

1. Use virtual threads for validation
2. Cache validation results
3. Implement lazy validation
4. Use efficient data structures

## License

This project is part of YAWL v6.0.0 and follows the same license terms.

## Support

For issues and questions:
- GitHub Issues: Create issue with `actor-validation` label
- Documentation: See `docs/actor-validation.md`
- CI/CD: See `.github/workflows/actor-validation.yml`