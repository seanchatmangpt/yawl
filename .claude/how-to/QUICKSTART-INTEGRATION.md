# YAWL Integration Quick Start Guide

**30-second setup for MCP, A2A, and observability integrations**

## MCP (Model Context Protocol)

### Quick Start (3 Steps)

```bash
# 1. Build YAWL
bash scripts/dx.sh all

# 2. Configure Environment
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL

# 3. Start MCP Server
java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

### Available Tools

| Tool | Description |
|------|-------------|
| `launch_case` | Start a new workflow case |
| `query_workitems` | List available work items |
| `checkout_workitem` | Claim a work item for processing |
| `complete_workitem` | Finish a work item with data |
| `cancel_case` | Cancel a running case |
| `query_case_state` | Get current case status |

### Production Deployment

#### Docker
```dockerfile
FROM eclipse-temurin:25-jre
ENV YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
ENV YAWL_USERNAME=admin
ENV YAWL_PASSWORD=${YAWL_PASSWORD}
COPY target/yawl.jar /app/yawl.jar
EXPOSE 3000
CMD ["java", "-cp", "/app/yawl.jar", "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"]
```

#### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-mcp-server
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: mcp-server
        image: yawl-mcp-server:6.0.0
        env:
        - name: YAWL_ENGINE_URL
          value: "http://yawl-engine:8080/yawl"
        - name: YAWL_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: username
        - name: YAWL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: password
        - name: MCP_TRANSPORT
          value: "sse"
        ports:
        - containerPort: 3000
```

## A2A (Agent-to-Agent)

### Quick Setup

```bash
# Start A2A server
java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer \
  --port 8081 \
  --virtual-threads

# Connect agent
curl -N http://localhost:8081/a2a/case-updates
```

## Observability (OpenTelemetry)

### Environment Variables

```bash
# Enable OpenTelemetry (default: true)
export OTEL_ENABLED=true

# OTLP endpoint (default: http://localhost:4317)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

# Service name for trace attribution
export OTEL_SERVICE_NAME=yawl-engine

# Sampling rate (0.0-1.0, default: 1.0)
# Use 0.1 for production, 1.0 for development
export OTEL_TRACES_SAMPLER_ARG=0.1
```

### Docker Compose Setup

```yaml
services:
  yawl-engine:
    image: yawl:6.0.0
    environment:
      - OTEL_ENABLED=true
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
      - OTEL_SERVICE_NAME=yawl-engine
    ports:
      - "8080:8080"
    depends_on:
      - otel-collector

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.91.0
    volumes:
      - ./otel-config.yaml:/etc/otelcol/config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Prometheus metrics
```

### Code Integration

```java
// At application startup (before any workflow operations)
OpenTelemetryInitializer.init();

// Create workflow spans
Span caseSpan = WorkflowSpanBuilder.forCase(caseId)
    .withSpecId(specId)
    .withParentContext(parentContext)
    .start();
try (Scope scope = caseSpan.makeCurrent()) {
    // ... workflow execution ...
} finally {
    caseSpan.end();
}

// Record metrics
YawlMetrics.recordCaseLaunched(specId);
YawlMetrics.recordCaseCompleted(specId, "success", durationMs);
```

## Certificate Pinning for Z.AI

### Configuration

```java
// In ZaiHttpClient.java
private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
    "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",  // Primary
    "sha256/mK87OJ3fZtIf7ZS0Eq6/5qG3H9nM2cL8wX5dP1nO9q0="   // Backup
);
```

## Common Workflows

### MCP + Observability Integration
```bash
# 1. Start YAWL engine with observability
export OTEL_ENABLED=true
export OTEL_SERVICE_NAME=yawl-engine
bash scripts/dx.sh -pl yawl-engine

# 2. Start MCP server
export YAWL_ENGINE_URL=http://localhost:8080/yawl
java -cp target/yawl.jar org.yawlfoundation.yawl.integration.mcp.YawlMcpServer

# 3. Connect agents to A2A endpoint
curl -N http://localhost:8081/a2a/case-updates
```

## Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `yawl.case.launched` | Counter | Cases started (by spec) |
| `yawl.case.completed` | Counter | Cases finished (by spec, outcome) |
| `yawl.case.duration` | Histogram | Case execution time (ms) |
| `yawl.workitem.created` | Counter | Work items created (by task) |
| `yawl.workitem.completed` | Counter | Work items finished (by task) |
| `yawl.engine.active_cases` | Gauge | Currently running cases |

## Quick Links

- **MCP Guide**: `MCP-QUICKSTART.md`
- **Observability**: `OBSERVABILITY-QUICKSTART.md`
- **Certificate Pinning**: `CERTIFICATE_PINNING_QUICK_REF.md`

---

**Last Updated**: February 22, 2026  
**Maintainer**: YAWL Integration Team
