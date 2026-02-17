# YAWL v6.0.0 Observability Implementation Guide

Quick reference for integrating observability into YAWL components.

## Maven Dependency Setup

Add to your module's `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-monitoring</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Initialization

### Servlet Listener (Stateful Engine)

```java
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletContextEvent;
import org.yawlfoundation.yawl.observability.OpenTelemetryInitializer;

public class YawlServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Initialize OpenTelemetry tracing
        OpenTelemetryInitializer.initialize();
        // Initialize Prometheus metrics
        setupMetrics();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        OpenTelemetryInitializer.shutdown();
    }

    private void setupMetrics() {
        MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        YawlMetrics.initialize(registry);
    }
}
```

### Spring Boot Application

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.yawlfoundation.yawl.observability.OpenTelemetryInitializer;
import io.micrometer.prometheus.PrometheusMeterRegistry;

@SpringBootApplication
public class YawlApplication {

    public static void main(String[] args) {
        // Initialize observability before Spring startup
        OpenTelemetryInitializer.initialize();
        SpringApplication.run(YawlApplication.class, args);
    }

    @Bean
    public HealthCheckEndpoint healthCheckEndpoint(HealthCheckDelegate delegate) {
        return new HealthCheckEndpoint(delegate);
    }

    @Bean
    public YawlMetricsConfig metricsConfig(PrometheusMeterRegistry registry) {
        YawlMetrics.initialize(registry);
        return new YawlMetricsConfig();
    }
}
```

## Adding Tracing to Components

### Engine Component

```java
import io.opentelemetry.api.trace.Tracer;
import org.yawlfoundation.yawl.observability.OpenTelemetryInitializer;
import org.yawlfoundation.yawl.observability.WorkflowSpanBuilder;

public class YEngine {
    private final Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.engine");

    public YCase createCase(String specID, String caseID, Document caseData, Document logData) {
        Span span = WorkflowSpanBuilder.create(tracer, "case.create")
            .withCaseId(caseID)
            .withSpecificationId(specID)
            .setAttribute("initiator", getCurrentUser())
            .start();

        try (Scope scope = span.makeCurrent()) {
            YSpecification spec = getSpecification(specID);
            YCase yCase = new YCase(spec, caseID, caseData, logData);
            return yCase;
        } finally {
            span.end();
        }
    }

    public void executionBegin(YCase yCase) {
        Span span = WorkflowSpanBuilder.create(tracer, "case.execute")
            .withCaseId(yCase.getCaseID())
            .withSpecificationId(yCase.getSpecificationID())
            .start();

        try (Scope scope = span.makeCurrent()) {
            // ... execution logic ...
        } finally {
            span.end();
        }
    }
}
```

### Net Runner Component

```java
public class YNetRunner {
    private final Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.netrunner");

    public void run(YNet net) {
        Span span = WorkflowSpanBuilder.create(tracer, "net.run")
            .withCaseId(net.getCaseID())
            .setAttribute("net_id", net.getID())
            .setAttribute("net_level", net.getNetLevel())
            .start();

        try (Scope scope = span.makeCurrent()) {
            executeNet(net);
        } finally {
            span.end();
        }
    }

    private void executeNet(YNet net) {
        Span span = tracer.spanBuilder("net.tasks.execute").start();
        try (Scope scope = span.makeCurrent()) {
            for (YTask task : net.getNetTasks()) {
                executeTask(task);
            }
        } finally {
            span.end();
        }
    }

    private void executeTask(YTask task) {
        Span span = tracer.spanBuilder("task.execute")
            .setAttribute("task_id", task.getID())
            .setAttribute("task_name", task.getName())
            .start();

        try (Scope scope = span.makeCurrent()) {
            // Task execution logic
        } finally {
            span.end();
        }
    }
}
```

### Activity Execution

```java
public class YActivity {
    private final Tracer tracer = OpenTelemetryInitializer.getTracer("yawl.activity");

    public void execute(YWorkItem workItem) {
        Span span = WorkflowSpanBuilder.create(tracer, "activity.execute")
            .withActivityName(getName())
            .withCaseId(workItem.getCaseID())
            .setAttribute("work_item_id", workItem.getID())
            .setAttribute("resource_id", workItem.getResourceID())
            .start();

        try (Scope scope = span.makeCurrent()) {
            // Activity execution
            if (isAtomicTask()) {
                executeAtomic(workItem);
            } else {
                executeComposite(workItem);
            }
        } finally {
            span.end();
        }
    }
}
```

## Adding Metrics to Components

### Case Lifecycle

```java
public class YEngine {
    private final YawlMetrics metrics = YawlMetrics.getInstance();

    public YCase createCase(String specID, String caseID, Document caseData, Document logData) {
        metrics.incrementCaseCreated();
        Timer.Sample sample = metrics.startCaseExecutionTimer();

        try {
            YSpecification spec = getSpecification(specID);
            return new YCase(spec, caseID, caseData, logData);
        } catch (Exception e) {
            metrics.incrementCaseFailed();
            throw e;
        }
    }

    public void completionHandle(YCase yCase, String flag) {
        if ("true".equalsIgnoreCase(flag)) {
            metrics.incrementCaseCompleted();
        } else {
            metrics.incrementCaseFailed();
        }
    }
}
```

### Queue Metrics

```java
public class YawlEngineExecutor {
    private final YawlMetrics metrics = YawlMetrics.getInstance();
    private final ExecutorService executor;

    public void updateQueueMetrics() {
        metrics.setQueueDepth(executor.getQueue().size());
        metrics.setActiveThreads(executor.getActiveCount());
    }

    public void submitTask(Runnable task) {
        executor.submit(() -> {
            try {
                updateQueueMetrics();
                task.run();
            } finally {
                updateQueueMetrics();
            }
        });
    }
}
```

### Task Execution

```java
public class YTask {
    private final YawlMetrics metrics = YawlMetrics.getInstance();

    public void executeWorkItem(YWorkItem workItem) {
        metrics.incrementTaskExecuted();
        Timer.Sample sample = metrics.startTaskExecutionTimer();

        try {
            // Execution logic
            performTask(workItem);
        } catch (Exception e) {
            metrics.incrementTaskFailed();
            throw e;
        } finally {
            metrics.recordTaskExecutionTime(sample);
        }
    }
}
```

## Structured Logging

### Case-level Events

```java
import org.yawlfoundation.yawl.observability.StructuredLogger;

public class YEngine {
    private static final StructuredLogger log = StructuredLogger.getLogger(YEngine.class);

    public void executionBegin(YCase yCase) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", yCase.getCaseID());
        fields.put("specification_id", yCase.getSpecificationID());
        fields.put("initiator", yCase.getInitiator());
        fields.put("start_time", System.currentTimeMillis());
        log.info("Case execution started", fields);
    }

    public void executionFailure(YCase yCase, Throwable ex) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", yCase.getCaseID());
        fields.put("error_type", ex.getClass().getSimpleName());
        log.error("Case execution failed", fields, ex);
    }
}
```

### Activity-level Events

```java
public class YActivity {
    private static final StructuredLogger log = StructuredLogger.getLogger(YActivity.class);

    public void startWork(YWorkItem workItem) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", workItem.getCaseID());
        fields.put("work_item_id", workItem.getID());
        fields.put("activity_name", getName());
        fields.put("resource_id", workItem.getResourceID());
        fields.put("timestamp", System.currentTimeMillis());
        log.info("Work item started", fields);
    }

    public void completeWork(YWorkItem workItem, String data) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", workItem.getCaseID());
        fields.put("work_item_id", workItem.getID());
        fields.put("activity_name", getName());
        fields.put("duration_ms", workItem.getTimeTaken());
        log.info("Work item completed", fields);
    }
}
```

## Health Checks

### Implementing Health Check Delegate

```java
import org.yawlfoundation.yawl.observability.HealthCheckEndpoint.HealthCheckDelegate;

public class YawlHealthCheckDelegate implements HealthCheckDelegate {

    @Override
    public boolean isDatabaseHealthy() {
        try {
            return YEngine.getInstance().getDBConnection().isValid(1);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isQueueHealthy() {
        return !taskQueue.isShutdown();
    }

    @Override
    public long getActiveWorkerThreads() {
        return threadPool.getActiveCount();
    }

    @Override
    public long getMaxWorkerThreads() {
        return threadPool.getMaximumPoolSize();
    }

    @Override
    public long getQueueDepth() {
        return taskQueue.size();
    }

    @Override
    public long getQueueCapacity() {
        return taskQueue.remainingCapacity();
    }

    @Override
    public boolean isInitializationComplete() {
        return YEngine.getInstance().isInitialized();
    }

    @Override
    public long getWarmupDurationMs() {
        return YEngine.getInstance().getStartTime();
    }

    @Override
    public boolean isSchemaValid() {
        return YSchemaVersion.loadAllSchemas() != null;
    }

    @Override
    public boolean isCaseStorageReady() {
        try {
            return YEngine.getInstance().getCaseCount() >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### HTTP Endpoint

```java
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HealthCheckServlet extends HttpServlet {
    private final HealthCheckEndpoint healthCheck;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String path = req.getPathInfo();
        HealthCheckEndpoint.HealthCheckResult result;

        if ("/live".equals(path)) {
            result = healthCheck.liveness();
        } else if ("/ready".equals(path)) {
            result = healthCheck.readiness();
        } else if ("/startup".equals(path)) {
            result = healthCheck.startup();
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setStatus(healthCheck.getHttpStatusCode(result));
        resp.setContentType("application/json");
        resp.getWriter().write(healthCheck.toJson(result));
    }
}
```

## Spring Boot Actuator Integration

```java
@Configuration
public class ActuatorConfiguration {

    @Bean
    public MeterBinder customMetrics() {
        return (registry) -> {
            YawlMetrics.initialize(registry);
            Gauge.builder("yawl.active.cases", YawlMetrics.getInstance()::getActiveCaseCount)
                .description("Number of active cases")
                .register(registry);
        };
    }

    @Bean
    public HealthContributor yawlHealth(HealthCheckDelegate delegate) {
        HealthCheckEndpoint endpoint = new HealthCheckEndpoint(delegate);
        return new HealthContributor() {
            @Override
            public Health health() {
                HealthCheckEndpoint.HealthCheckResult result = endpoint.readiness();
                if (result.getStatus() == HealthCheckEndpoint.HealthStatus.UP) {
                    return Health.up()
                        .withDetails(result.getDetails())
                        .build();
                } else {
                    return Health.down()
                        .withDetails(result.getDetails())
                        .build();
                }
            }
        };
    }
}
```

## Configuration Examples

### Environment Variables

```bash
# OpenTelemetry configuration
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
export OTEL_SERVICE_NAME=yawl-engine-prod
export OTEL_SERVICE_VERSION=6.0.0
export OTEL_RESOURCE_ATTRIBUTES=environment=production,region=us-west-2

# Logging configuration
export LOG_LEVEL=INFO
export STRUCTURED_LOGS_ENABLED=true

# Metrics configuration
export METRICS_ENABLED=true
export PROMETHEUS_PORT=8888
```

### System Properties

```bash
java -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.service.name=yawl-engine \
     -Dotel.service.version=6.0.0 \
     -Dlog.level=INFO \
     org.yawlfoundation.yawl.controlpanel.YControlPanel
```

### application.properties (Spring Boot)

```properties
# OpenTelemetry
otel.exporter.otlp.endpoint=http://otel-collector:4317
otel.service.name=yawl-engine
otel.service.version=6.0.0

# Spring Actuator
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true

# Prometheus metrics
management.metrics.export.prometheus.enabled=true

# Logging
logging.level.root=INFO
logging.level.org.yawlfoundation.yawl=DEBUG
```

## Testing Observability

```bash
# Check OpenTelemetry traces in Jaeger
curl -s http://localhost:16686/api/traces?service=yawl-engine | jq '.'

# Scrape Prometheus metrics
curl -s http://localhost:8080/actuator/prometheus | grep yawl_

# Check health endpoints
curl -s http://localhost:8080/health/live
curl -s http://localhost:8080/health/ready
curl -s http://localhost:8080/health/startup

# Query Prometheus
curl -G http://localhost:9090/api/v1/query --data-urlencode 'query=yawl_case_created_total'

# Check logs in Loki
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={job="yawl-engine"}' \
  --data-urlencode 'start=1645000000' \
  --data-urlencode 'end=1645086400' | jq '.data.result'
```

## Performance Considerations

1. **Sampling**: Use head-based sampling (10%) for high-volume applications
2. **Batch Exports**: Batch span exports to reduce overhead
3. **Metrics Cardinality**: Avoid high-cardinality labels
4. **Memory**: Monitor JVM heap with metrics
5. **CPU**: OpenTelemetry adds ~2-5% overhead with proper sampling

## Troubleshooting

### Spans not appearing in Jaeger

1. Check OTLP endpoint is reachable: `curl http://localhost:4317`
2. Verify `OpenTelemetryInitializer.initialize()` was called
3. Check application logs for initialization errors
4. Ensure service name matches Jaeger filtering

### Metrics not in Prometheus

1. Verify scrape endpoint: `curl http://localhost:8080/actuator/prometheus`
2. Check Prometheus targets page
3. Verify `YawlMetrics.initialize()` was called
4. Check metric cardinality hasn't exceeded limits

### High memory usage

1. Lower sampling rate for traces
2. Reduce metric cardinality (fewer labels)
3. Check log retention policies
4. Monitor GC pressure with JVM metrics
