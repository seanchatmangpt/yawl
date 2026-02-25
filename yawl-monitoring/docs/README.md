# YAWL Monitoring Module

## Overview

The YAWL Monitoring module provides comprehensive observability and monitoring infrastructure for the YAWL workflow engine. It integrates OpenTelemetry for distributed tracing, Micrometer for metrics collection, and structured logging for production monitoring.

## Key Components

### 1. Metrics Collection (`YawlMetrics.java`)

**Purpose**: Production metrics tracking with Micrometer registry integration

**Key Metrics**:
- **Counters**:
  - `yawl.case.created`: Total workflow cases created
  - `yawl.case.completed`: Successfully completed cases
  - `yawl.case.failed`: Failed cases
  - `yawl.task.executed`: Total task executions
  - `yawl.task.failed`: Task execution failures

- **Gauges** (Real-time values):
  - `yawl.case.active`: Active case count
  - `yawl.queue.depth`: Engine work queue depth
  - `yawl.threadpool.active`: Active worker threads

- **Timers** (Performance tracking):
  - `yawl.case.duration`: Case execution time (50th, 95th, 99th percentiles)
  - `yawl.task.duration`: Task execution time
  - `yawl.engine.latency`: Engine request latency

**Usage**:
```java
// Initialize singleton
YawlMetrics.initialize(meterRegistry);

// Track case lifecycle
YawlMetrics.getInstance().incrementCaseCreated();
YawlMetrics.getInstance().incrementCaseCompleted();

// Measure performance
Timer.Sample caseTimer = YawlMetrics.getInstance().startCaseExecutionTimer();
// ... execute case ...
YawlMetrics.getInstance().recordCaseExecutionTime(caseTimer);
```

### 2. Distributed Tracing (`DistributedTracer.java`)

**Purpose**: End-to-end tracing across workflow boundaries and autonomous agents

**Features**:
- Auto-generates unique trace IDs for case lifecycle
- Propagates trace context across task boundaries
- Correlates events across multiple agents
- Thread-safe with MDC integration

**Trace Types**:
- **Case spans**: Entire workflow lifecycle tracking
- **Work item spans**: Individual task execution within cases
- **Task spans**: Agent-specific task execution
- **Agent action spans**: Autonomous agent operations

**Usage**:
```java
DistributedTracer tracer = new DistributedTracer(openTelemetry);

// Start case trace
try (DistributedTracer.TraceSpan span = tracer.startCaseSpan(caseId, specId)) {
    span.addEvent("case_started", "priority", "high");
    // ... execute workflow ...
    span.endWithSuccess();
}

// Work item tracing with parent context
try (DistributedTracer.TraceSpan span = tracer.startWorkItemSpan(
        caseId, workItemId, "ReviewTask", parentTraceId)) {
    // ... execute task ...
}
```

### 3. Workflow Optimization (`WorkflowOptimizer.java`)

**Purpose**: Autonomous detection of inefficient execution patterns

**Detected Patterns**:
- **High variability tasks**: Resource contention detection
- **Slow repeated tasks**: Caching opportunities
- **Sequential opportunities**: Parallelization candidates

**Optimization Types**:
- `PARALLELIZE`: Enable AND-split for independent paths
- `CACHE`: Cache task outputs for repeated invocations
- `ROUTE`: Route to faster agent alternatives
- `BATCH`: Batch similar tasks for bulk processing

**Usage**:
```java
WorkflowOptimizer optimizer = new WorkflowOptimizer(meterRegistry);

// Record task execution for analysis
optimizer.recordTaskExecution("spec-123", "ReviewTask", 1500);

// Get optimization suggestions
List<WorkflowOptimizer.Optimization> suggestions =
    optimizer.getActiveSuggestions();

// Register listener for auto-applicable optimizations
optimizer.onOptimization(opt -> {
    if (opt.canAutoApply) {
        applyOptimization(opt);
    }
});
```

### 4. Health Monitoring (`HealthCheckEndpoint.java`)

**Purpose**: Runtime health monitoring and alerting

**Features**:
- Engine health status monitoring
- Queue depth alerts
- Performance threshold monitoring
- Auto-remediation logging

### 5. Anomaly Detection (`AnomalyDetector.java`)

**Purpose**: Statistical anomaly detection in workflow patterns

**Capabilities**:
- Execution time variance analysis
- Error rate pattern detection
- Resource utilization anomalies

### 6. Bottleneck Detection (`BottleneckDetector.java`)

**Purpose**: Performance bottleneck identification

**Detection Types**:
- Task queue backlogs
- Resource contention
- Execution time outliers

## Integration Points

### Engine Integration
- Integrated with YAWL engine for automatic metrics collection
- Hooks into work item lifecycle for tracing
- Metrics published through Micrometer registry

### OpenTelemetry Integration
- Compatible with OpenTelemetry SDK 1.x
- Supports OTLP exporters for trace/metric collection
- Semantic conventions for workflow domain

### External Monitoring Systems
- **Prometheus**: Metrics exposure via Micrometer
- **Jaeger**: Trace visualization
- **Grafana**: Dashboard integration
- **ELK Stack**: Log aggregation

## Dependencies

### YAWL Module Dependencies
- `yawl-engine`: Core workflow engine integration
- `yawl-stateless`: Stateless engine support

### External Dependencies
- **OpenTelemetry**: Distributed tracing SDK
- **Micrometer**: Metrics collection
- **SLF4J + Log4j 2**: Structured logging
- **Spring Boot Actuator**: Optional monitoring endpoints

## Configuration

### Basic Metrics Configuration
```java
// Configure Prometheus meter registry
new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    .configure(YawlMetrics.getInstance());

// Enable distributed tracing
OpenTelemetry openTelemetry = OpenTelemetrySdk.initialize();
DistributedTracer tracer = new DistributedTracer(openTelemetry);
```

### Structured Logging Configuration
```xml
<!-- Log4j 2 configuration for JSON output -->
<Configuration status="WARN">
    <Appenders>
        <Json name="Structured" />
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="Structured" />
        </Root>
    </Loggers>
</Configuration>
```

## Usage Examples

### Complete Monitoring Setup
```java
// 1. Initialize observability infrastructure
MeterRegistry registry = new PrometheusMeterRegistry();
YawlMetrics.initialize(registry);
DistributedTracer tracer = new DistributedTracer(openTelemetry);
WorkflowOptimizer optimizer = new WorkflowOptimizer(registry);

// 2. Monitor workflow execution
public void executeWorkflow(String specId, String caseId) {
    try (DistributedTracer.TraceSpan span = tracer.startCaseSpan(caseId, specId)) {
        span.setAttribute("yawl.spec.id", specId);

        // Track case creation
        YawlMetrics.getInstance().incrementCaseCreated();

        // Execute workflow steps with timing
        executeWorkflowSteps(specId, caseId);

        span.endWithSuccess();
    }
}

// 3. Record task execution
public void executeTask(String caseId, String taskId, long durationMs) {
    YawlMetrics.getInstance().incrementTaskExecuted();
    YawlMetrics.getInstance().recordTaskDuration(durationMs);

    // Analyze for optimizations
    WorkflowOptimizer optimizer = WorkflowOptimizer.getInstance();
    optimizer.recordTaskExecution(currentSpec, taskId, durationMs);
}
```

### Health Monitoring
```java
// Check engine health
HealthCheckEndpoint health = new HealthCheckEndpoint();
HealthCheckEndpoint.HealthCheckResult result = health.checkEngineHealth();

if (result.getStatus() == HealthStatus.UNHEALTHY) {
    // Trigger alert or auto-remediation
    health.triggerRemediation(result.getIssues());
}
```

## Best Practices

1. **Metrics Collection**: Always track case lifecycle events for SLA monitoring
2. **Tracing**: Use distributed tracing for cross-service workflow debugging
3. **Optimization**: Regularly review workflow optimizer suggestions
4. **Alerting**: Configure meaningful thresholds for production monitoring
5. **Performance**: Use percentiles (50th, 95th, 99th) for response times

## Monitoring Data Flow

```
Workflow Engine
    ↓
YawlMetrics (Counters/Gauges/Timers)
    ↓
DistributedTracer (Span creation/propagation)
    ↓
WorkflowOptimizer (Pattern analysis)
    ↓
HealthCheckEndpoint (Status monitoring)
    ↓
OpenTelemetry Exporter → Monitoring Systems
```

## Performance Considerations

- Metrics collection is asynchronous and non-blocking
- Tracing spans have minimal overhead when sampling is disabled
- Optimizer analysis runs on statistical sampling (10th execution)
- Health checks are periodic with configurable intervals

## Monitoring Best Practices

1. **Start with business metrics**: Case completion rates, error rates
2. **Add technical metrics**: Queue depths, response times, thread counts
3. **Use distributed tracing for debugging**: Complex workflows with multiple agents
4. **Set up alerts**: Critical thresholds for SLA compliance
5. **Regular optimization reviews**: Apply safe auto-optimizations first