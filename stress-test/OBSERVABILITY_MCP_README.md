# YAWL Observability MCP Server Implementation

This implementation provides a comprehensive MCP (Model Context Protocol) server for YAWL observability integration, featuring metrics export, trace correlation, health monitoring, and workflow tracking.

## Overview

The YAWL Observability MCP Server enables:

- **Metrics Export**: Real-time YAWL engine metrics via MCP protocol
- **Trace Correlation**: Distributed tracing with workflow ID correlation
- **Health Monitoring**: Comprehensive health check endpoints
- **Workflow Tracking**: Live workflow monitoring and state tracking
- **OTel Integration**: OpenTelemetry SDK integration for production observability

## Architecture

### Core Components

1. **ObservabilityMcpServer** - Full-featured implementation with OpenTelemetry
2. **SimpleObservabilityMcpServer** - Simplified version for standalone operation
3. **YawlMetrics** - Micrometer-based metrics collection
4. **OpenTelemetryInitializer** - OTel SDK setup with OTLP export
5. **ObservabilityMcpDemo** - Interactive demo and testing interface

### Key Features

#### MCP Protocol Support
- Full MCP 2024-11-05 protocol implementation
- Tool registration and execution
- JSON-RPC style communication
- HTTP-based transport

#### Metrics Integration
- Case metrics (created, completed, failed, active)
- Task execution metrics
- Queue depth monitoring
- Thread pool tracking
- Custom metric registration

#### Trace Correlation
- Workflow ID to trace correlation
- Span management for active workflows
- Start time and duration tracking
- Task completion counting

#### Health Monitoring
- Memory usage tracking
- Active span limits
- Thread health monitoring
- System uptime reporting
- HTTP health endpoints

## Quick Start

### Running the Demo

```bash
# Start the observability server
java -cp target/classes org.yawlfoundation.yawl.observability.mcp.SimpleObservabilityDemo

# Or run the basic example
java -cp target/classes org.yawlfoundation.yawl.observability.mcp.ObservabilityMcpExample
```

### Basic Usage

```java
// Create and start server
SimpleObservabilityMcpServer server = new SimpleObservabilityMcpServer(8082, "yawl-observability");
server.start();

// Monitor a workflow
server.monitorWorkflow("case-123", 300000);

// Access metrics
long activeCases = yawlMetrics.getActiveCaseCount();
int queueDepth = yawlMetrics.getQueueDepth();
```

## HTTP Endpoints

### Root Endpoint
```
GET /
```
Returns server information and status.

### Metrics Endpoint
```
GET /metrics
```
Returns current YAWL metrics:
- cases_active: Number of active cases
- queue_depth: Engine queue depth
- active_threads: Active worker threads
- server_uptime: Server uptime in milliseconds
- active_spans: Number of active workflow spans

### Health Endpoint
```
GET /health
```
Returns health status with:
- status: "healthy" or "unhealthy"
- timestamp: Current timestamp
- uptime_ms: Server uptime
- reason: Health failure reason (if unhealthy)

### Traces Endpoint
```
GET /traces
```
Returns all active traces with workflow correlation.

### Workflows Endpoint
```
GET /workflows
```
Returns active workflow states and progress.

### MCP Protocol Endpoint
```
POST /mcp
```
Handles MCP protocol requests (initialize, tools/list, tools/call, ping).

## MCP Tools

### get_metrics
Get current YAWL metrics via MCP tool.

```json
{
  "method": "tools/call",
  "params": {
    "name": "get_metrics",
    "arguments": {}
  }
}
```

### correlate_trace
Correlate a trace with a workflow ID.

```json
{
  "method": "tools/call",
  "params": {
    "name": "correlate_trace",
    "arguments": {
      "workflowId": "case-123"
    }
  }
}
```

### monitor_workflow
Start monitoring a workflow.

```json
{
  "method": "tools/call",
  "params": {
    "name": "monitor_workflow",
    "arguments": {
      "workflowId": "case-123",
      "maxDuration": 300000
    }
  }
}
```

### health_check
Get detailed health status.

```json
{
  "method": "tools/call",
  "params": {
    "name": "health_check",
    "arguments": {}
  }
}
```

## Configuration

### OpenTelemetry Configuration

Configure via system properties:

```bash
# OTLP endpoint for trace export
-Dotel.exporter.otlp.endpoint=http://localhost:4317

# Service name
-Dotel.service.name=yawl-engine

# Service version
-Dotel.service.version=6.0.0

# Additional resource attributes
-Dotel.resource.attributes=region=us-west-2,environment=production
```

### Server Configuration

```java
// Custom port and name
SimpleObservabilityMcpServer server = new SimpleObservabilityMcpServer(9090, "custom-observability");
```

## Dependencies

### Required
- Jackson (JSON processing)
- SLF4J (logging)
- YAWL Engine (core workflow engine)
- Micrometer (metrics collection)

### Optional
- OpenTelemetry SDK (for distributed tracing)
- OTLP exporters (for trace export)
- JUnit 5 (for testing)

## Testing

Run the test suite:

```bash
mvn test -Dtest=McpServerTest
```

### Test Coverage
- Basic server creation and startup
- Metrics collection and reporting
- Health check functionality
- OpenTelemetry integration
- Error handling scenarios

## Production Considerations

### Performance
- Uses virtual threads for concurrent request handling
- Efficient metric collection with Micrometer
- Minimal memory overhead for span tracking
- Configurable timeouts and limits

### Reliability
- Graceful shutdown with resource cleanup
- Comprehensive error handling
- Health monitoring with configurable thresholds
- Connection pooling for external dependencies

### Security
- HTTP transport with standard security practices
- No sensitive data logging
- Configurable access controls
- Input validation for all requests

## Integration Examples

### With YAWL Engine
```java
// Initialize YAWL engine with observability
YawlMetrics.initialize(meterRegistry);

// Create MCP server for external access
ObservabilityMcpServer mcpServer = new ObservabilityMcpServer(8082, "yawl-engine");
mcpServer.start();

// Register workflow events for tracing
WorkflowSpan span = mcpServer.monitorWorkflow(workflowId, timeout);
```

### With Prometheus
```java
// Configure Micrometer to export to Prometheus
MeterRegistry registry = new PrometheusMeterRegistry();
YawlMetrics.initialize(registry);

// Access metrics via /metrics endpoint
curl http://localhost:8082/metrics
```

### With Jaeger
```bash
# Export traces to Jaeger via OTLP
-Dotel.exporter.otlp.endpoint=http://localhost:4317
```

## Troubleshooting

### Common Issues

1. **Port already in use**
   - Check for existing processes on the configured port
   - Use a different port number

2. **OpenTelemetry initialization fails**
   - Verify OTLP endpoint is accessible
   - Check network connectivity
   - Verify authentication credentials

3. **Metrics not updating**
   - Ensure YawlMetrics is properly initialized
   - Check metric registry configuration
   - Verify workflow events are being recorded

### Debug Mode

Enable debug logging:

```bash
-Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## Contributing

### Code Standards
- Follow YAWL coding conventions
- Implement comprehensive error handling
- Include unit tests for new features
- Update documentation for API changes

### Testing Requirements
- All new features must include tests
- Integration tests for MCP protocol
- Performance tests for high-load scenarios
- Error handling tests for edge cases

## License

This implementation is part of the YAWL 6.0.0 project and follows the same license terms.