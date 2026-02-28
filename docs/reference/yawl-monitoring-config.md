# YAWL Monitoring Configuration Reference

Complete configuration options for distributed tracing, metrics, structured logging, and alerting.

---

## OpenTelemetry Tracing

### Basic Configuration

```yaml
otel:
  # Enable OpenTelemetry
  # Default: true
  enabled: true

  # Service name (appears in traces)
  # Default: "yawl-engine"
  service-name: yawl-engine

  # Service version
  # Default: "6.0.0"
  service-version: 6.0.0

  # Service namespace
  # Default: "org.yawlfoundation"
  service-namespace: org.yawlfoundation

  # Environment
  # Default: "production"
  environment: production

  # Resource attributes
  resource-attributes:
    deployment.environment: production
    service.name: yawl-engine
    service.version: 6.0.0
```

### Tracing Configuration

```yaml
otel.sdk.trace:
  # Enable trace SDK
  # Default: true
  enabled: true

  # Tracer provider type
  # Options: jaeger, otlp, jaeger-thrift
  # Default: jaeger
  exporter: jaeger

  # Sampling configuration
  # Sampler type
  # Options: parentbased_always_on, parentbased_always_off, parentbased_traceidratio
  # Default: parentbased_always_on
  sampler: parentbased_always_on

  # Sampling ratio (0.0 - 1.0)
  # Default: 1.0 (100% sampling)
  sample-rate: 1.0

  # Batch processing
  batch:
    # Batch size
    # Default: 512
    size: 512

    # Schedule delay (milliseconds)
    # Default: 5000
    scheduled-delay: 5000

    # Export timeout (milliseconds)
    # Default: 30000
    export-timeout: 30000

    # Max queue size
    # Default: 2048
    max-queue-size: 2048

  # Span processor type
  # Options: batch, simple, jaeger
  # Default: batch
  processor: batch

  # Span name limits
  attr:
    # Max attribute value length
    # Default: 128
    max-value-length: 128

    # Number of attributes per span
    # Default: 128
    max-num-attributes: 128

    # Number of events per span
    # Default: 128
    max-num-events: 128

    # Number of links per span
    # Default: 128
    max-num-links: 128
```

### Jaeger Exporter

```yaml
otel.exporter.jaeger:
  # Enable Jaeger exporter
  # Default: true
  enabled: true

  # Protocol
  # Options: thrift, grpc, http
  # Default: thrift
  protocol: thrift

  # Agent configuration (Thrift)
  agent:
    # Agent host
    # Default: localhost
    host: localhost

    # Agent port (Thrift UDP)
    # Default: 6831
    port: 6831

  # Collector configuration (HTTP)
  collector:
    # Collector endpoint
    # Default: http://localhost:14268/api/traces
    endpoint: http://localhost:14268/api/traces

  # gRPC configuration
  grpc:
    # gRPC endpoint
    # Default: http://localhost:14250
    endpoint: http://localhost:14250

  # Jaeger agent tags
  tags:
    jaeger.service.name: yawl-engine
    jaeger.deployment: production

  # Use agent vs collector
  # Default: true (use agent)
  use-agent: true
```

### OTLP Exporter

```yaml
otel.exporter.otlp:
  # Enable OTLP exporter
  # Default: false
  enabled: false

  # OTLP endpoint
  # Default: http://localhost:4317 (gRPC)
  endpoint: http://localhost:4317

  # Protocol
  # Options: grpc, http/protobuf
  # Default: grpc
  protocol: grpc

  # HTTP endpoint (if using http/protobuf)
  http-endpoint: http://localhost:4318/v1/traces

  # Request timeout (milliseconds)
  # Default: 10000
  timeout: 10000

  # Headers to include in requests
  headers:
    Authorization: "Bearer ${OTLP_API_TOKEN}"

  # Compression
  # Options: gzip, none
  # Default: gzip
  compression: gzip

  # Retry configuration
  retry:
    # Enable retries
    enabled: true

    # Initial backoff (milliseconds)
    initial-backoff: 1000

    # Max backoff (milliseconds)
    max-backoff: 30000

    # Backoff multiplier
    multiplier: 1.5

    # Max attempts
    max-attempts: 5
```

---

## Metrics Configuration

### Micrometer Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info

  metrics:
    # Export to Prometheus
    export:
      prometheus:
        enabled: true

        # Scrape endpoint path
        # Default: /actuator/prometheus
        endpoint: /actuator/prometheus

    # Meter filters
    tags:
      application: yawl-engine
      environment: production

    # Common tags
    tags:
      app: yawl
      version: 6.0.0

yawl:
  observability:
    metrics:
      # Enable metrics collection
      # Default: true
      enabled: true

      # Case metrics
      case-metrics:
        # Enable case metrics
        # Default: true
        enabled: true

        # Metrics to track
        track:
          # Active cases
          active-cases: true

          # Completed cases
          completed-cases: true

          # Failed cases
          failed-cases: true

          # Case duration
          case-duration: true

          # Cases by status
          cases-by-status: true

      # Work item metrics
      work-item-metrics:
        # Enable work item metrics
        # Default: true
        enabled: true

        # Track
        track:
          # Active work items
          active-work-items: true

          # Work item duration
          work-item-duration: true

          # Work items by status
          work-items-by-status: true

      # Task metrics
      task-metrics:
        # Enable task metrics
        # Default: true
        enabled: true

        # Track
        track:
          # Task execution count
          task-executions: true

          # Task duration (percentiles)
          task-duration: true

          # Task failure rate
          task-failures: true

      # Engine metrics
      engine-metrics:
        # Enable engine metrics
        # Default: true
        enabled: true

        # Track
        track:
          # Uptime
          uptime: true

          # Thread count
          thread-count: true

          # Memory usage
          memory-usage: true

          # Queue depth
          queue-depth: true

    # Metric retention
    retention:
      # How long to keep metrics in memory
      # Default: 1 hour
      duration: 1h

      # Step size for histogram buckets
      # Default: 1 minute
      step: 1m
```

### Prometheus Configuration

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true

        # Include histogram percentiles
        # Default: true
        percentiles-histogram: true

        # Histogram buckets (seconds)
        # Default: predefined buckets
        histogram-buckets:
          - 0.001
          - 0.005
          - 0.01
          - 0.05
          - 0.1
          - 0.5
          - 1.0
          - 5.0
          - 10.0
```

---

## Structured Logging

### Log4j 2 Configuration

```yaml
logging:
  level:
    root: INFO
    org.yawlfoundation.yawl: DEBUG
    org.yawlfoundation.yawl.engine: DEBUG
    io.opentelemetry: WARN

  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"

yawl:
  observability:
    logging:
      # Enable structured logging
      # Default: true
      enabled: true

      # Log format
      # Options: json, text
      # Default: json
      format: json

      # JSON layout configuration
      json:
        # Include trace ID
        # Default: true
        include-trace-id: true

        # Include span ID
        # Default: true
        include-span-id: true

        # Include MDC (mapped diagnostic context)
        # Default: true
        include-mdc: true

        # Pretty print JSON
        # Default: false
        pretty: false

        # Additional fields to include
        additional-fields:
          service_name: yawl-engine
          environment: production

      # Async logging
      async:
        # Enable async logging
        # Default: true
        enabled: true

        # Queue size
        # Default: 1024
        queue-size: 1024

        # Discard oldest events if queue full
        # Default: true
        discard-oldest: true

      # File rolling policy
      file:
        # File path
        # Default: logs/yawl.log
        path: logs/yawl.log

        # Max file size before rolling
        # Default: 100MB
        max-size: 100MB

        # Max backups to keep
        # Default: 10
        max-backups: 10

        # Compress rolled files
        # Default: true
        compress: true
```

---

## Health Check Configuration

```yaml
management:
  endpoint:
    health:
      # Show detailed health info
      # Default: when-authorized
      show-details: when-authorized

      # Include health indicators
      enabled: true

  endpoints:
    web:
      exposure:
        include: health,info

  health:
    # Liveness probe
    livenessState:
      enabled: true

    # Readiness probe
    readinessState:
      enabled: true

yawl:
  observability:
    health:
      # Include engine health
      # Default: true
      engine: true

      # Include database health
      # Default: true
      database: true

      # Include cache health
      # Default: true
      cache: true
```

---

## Complete Example

```yaml
# application-monitoring-production.yaml

otel:
  enabled: true
  service-name: yawl-engine
  service-version: 6.0.0
  environment: production

otel.sdk.trace:
  enabled: true
  exporter: otlp
  sampler: parentbased_traceidratio
  sample-rate: 0.1  # 10% sampling in production
  batch:
    size: 512
    scheduled-delay: 5000

otel.exporter.otlp:
  enabled: true
  endpoint: http://otel-collector:4317
  protocol: grpc
  headers:
    Authorization: "Bearer ${OTLP_API_TOKEN}"

management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: yawl-engine
      environment: production

logging:
  level:
    root: INFO
    org.yawlfoundation.yawl: DEBUG
    io.opentelemetry: WARN

yawl:
  observability:
    metrics:
      enabled: true
      case-metrics:
        enabled: true
      work-item-metrics:
        enabled: true
    logging:
      enabled: true
      format: json
      json:
        include-trace-id: true
        include-span-id: true
```

---

## API Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health check |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/info` | Application info |

---

## Common Patterns

### Development (100% Tracing)

```yaml
otel.sdk.trace:
  exporter: jaeger
  sample-rate: 1.0  # 100%
  sampler: parentbased_always_on

otel.exporter.jaeger:
  enabled: true
  agent:
    host: localhost
    port: 6831
```

### Production (10% Tracing)

```yaml
otel.sdk.trace:
  exporter: otlp
  sample-rate: 0.1  # 10%
  sampler: parentbased_traceidratio

otel.exporter.otlp:
  enabled: true
  endpoint: http://otel-collector:4317
  protocol: grpc
```

### High-Volume (1% Tracing)

```yaml
otel.sdk.trace:
  sampler: parentbased_traceidratio
  sample-rate: 0.01  # 1%
  batch:
    size: 1024
    scheduled-delay: 10000  # 10 seconds
```

---

## Troubleshooting

### Traces Not Appearing

1. Verify exporter is enabled
2. Check endpoint connectivity
3. Verify sampling rate > 0
4. Check logs for export errors

### High Memory Usage

1. Reduce sampling rate
2. Reduce batch size
3. Enable compression
4. Add metric export timeout

### Missing Metrics

1. Verify management.endpoints.web.exposure includes prometheus
2. Check `/actuator/prometheus` endpoint
3. Verify metrics.enabled: true

---

## See Also

- [How-To: Set Up Distributed Tracing](../how-to/yawl-monitoring-tracing.md)
- [Tutorial: Getting Started](../tutorials/yawl-monitoring-getting-started.md)
- [Architecture: Observability Design](../explanation/yawl-monitoring-architecture.md)
