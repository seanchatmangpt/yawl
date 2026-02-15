# YAWL Spring Boot Actuator

## Overview

YAWL v5.2 includes production-ready Spring Boot Actuator integration for cloud-native health checks, readiness probes, and metrics collection. This makes YAWL deployment-ready for Kubernetes (GKE, EKS, AKS), Google Cloud Run, and other cloud platforms.

## Features

### Health Indicators

1. **YEngineHealthIndicator** - Engine state and capacity
   - Engine status (dormant, initializing, running, terminating)
   - Active workflow cases
   - Work item queue depth
   - Engine load and capacity metrics

2. **YDatabaseHealthIndicator** - Database connectivity
   - Connection availability
   - Query execution time
   - Connection pool statistics
   - Database version information

3. **YExternalServicesHealthIndicator** - External service connectivity
   - A2A agent availability
   - MCP service health
   - Service response times
   - Failure rate tracking

4. **YLivenessHealthIndicator** - Process liveness
   - Deadlock detection
   - Process uptime
   - Critical failure detection

5. **YReadinessHealthIndicator** - Traffic readiness
   - Initialization completion
   - Database availability
   - Overload detection

### Metrics Collection

1. **YWorkflowMetrics** - Workflow execution metrics
   - Cases launched/completed/cancelled/failed
   - Work items enabled/started/completed/failed
   - Execution time histograms
   - Active instance counts

2. **YAgentPerformanceMetrics** - Agent performance
   - A2A agent invocations and success rates
   - MCP service invocations and success rates
   - Agent response time histograms
   - Failure tracking by error type

3. **YResourceMetrics** - JVM resource utilization
   - Heap and non-heap memory usage
   - Thread counts and states
   - CPU availability
   - Memory usage percentages

## Endpoints

### Health Endpoints

| Endpoint | Purpose | Kubernetes Use |
|----------|---------|----------------|
| `/actuator/health` | Overall system health | Startup probe |
| `/actuator/health/liveness` | Process liveness | Liveness probe |
| `/actuator/health/readiness` | Traffic readiness | Readiness probe |

### Metrics Endpoints

| Endpoint | Format | Purpose |
|----------|--------|---------|
| `/actuator/metrics` | JSON | List all metrics |
| `/actuator/metrics/{name}` | JSON | Get specific metric |
| `/actuator/prometheus` | Prometheus | Prometheus scraping |

## Quick Start

### 1. Add Dependencies

The actuator dependencies are already included in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2. Configure Application

Create `src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

### 3. Build Application

```bash
mvn clean package
```

### 4. Run Application

```bash
java -jar target/yawl-5.2.jar
```

### 5. Test Health Endpoints

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Docker Deployment

### Build Image

```bash
docker build -t yawl-engine:5.2 .
```

### Run Container

```bash
docker run -p 8080:8080 \
  -e ENVIRONMENT=production \
  --health-cmd="curl -f http://localhost:8080/actuator/health/liveness || exit 1" \
  --health-interval=30s \
  --health-timeout=5s \
  --health-retries=3 \
  yawl-engine:5.2
```

### Check Health

```bash
docker ps  # Shows health status
curl http://localhost:8080/actuator/health
```

## Kubernetes Deployment

See [KUBERNETES_INTEGRATION.md](KUBERNETES_INTEGRATION.md) for complete Kubernetes deployment guide.

**Quick deployment:**

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

**Check health:**

```bash
kubectl get pods
kubectl port-forward svc/yawl-engine 8080:80
curl http://localhost:8080/actuator/health
```

## Google Cloud Run Deployment

### Deploy

```bash
gcloud run deploy yawl-engine \
  --image gcr.io/project-id/yawl-engine:5.2 \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 8080 \
  --memory 2Gi \
  --cpu 2 \
  --max-instances 10 \
  --min-instances 1
```

### Configure Health Checks

Cloud Run automatically uses:
- Startup probe: `/actuator/health/readiness`
- Liveness probe: `/actuator/health/liveness`

## Monitoring Integration

### Prometheus

**Scrape Configuration:**

```yaml
scrape_configs:
  - job_name: 'yawl-engine'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['yawl-engine:8080']
```

### Grafana

Import dashboard template from `grafana-dashboard.json`.

**Key Visualizations:**
- Active workflow cases (gauge)
- Case completion rate (graph)
- Agent response times (heatmap)
- JVM memory usage (graph)
- Database performance (graph)

### Datadog

```yaml
# datadog-values.yaml
datadog:
  apiKey: <YOUR_API_KEY>
  prometheusScrape:
    enabled: true
    serviceEndpoints: true
```

### New Relic

Add to `application.yml`:

```yaml
management:
  metrics:
    export:
      newrelic:
        api-key: ${NEW_RELIC_API_KEY}
        account-id: ${NEW_RELIC_ACCOUNT_ID}
```

## Custom Metrics

### Recording Workflow Events

```java
import org.yawlfoundation.yawl.engine.actuator.metrics.YWorkflowMetrics;

public class MyWorkflowService {

    @Autowired
    private YWorkflowMetrics metrics;

    public void launchCase() {
        // Launch workflow case
        metrics.recordCaseLaunched();
    }

    public void completeCase(long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        metrics.recordCaseCompleted(executionTime);
    }
}
```

### Recording Agent Events

```java
import org.yawlfoundation.yawl.engine.actuator.metrics.YAgentPerformanceMetrics;

public class AgentInvoker {

    @Autowired
    private YAgentPerformanceMetrics metrics;

    public void invokeA2AAgent(String agentName) {
        metrics.recordA2AAgentInvocation(agentName);

        long startTime = System.currentTimeMillis();
        try {
            // Invoke agent
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordA2AAgentSuccess(agentName, responseTime);
        } catch (Exception e) {
            metrics.recordA2AAgentFailure(agentName, e.getClass().getSimpleName());
        }
    }
}
```

## Configuration Reference

### Health Check Configuration

```yaml
management:
  endpoint:
    health:
      enabled: true
      show-details: always  # Options: never, when-authorized, always
      show-components: always
      probes:
        enabled: true
      group:
        liveness:
          include: livenessHealthIndicator
        readiness:
          include: readinessHealthIndicator,yEngineHealthIndicator
```

### Metrics Configuration

```yaml
management:
  metrics:
    tags:
      application: yawl-engine
      environment: ${ENVIRONMENT:development}
    distribution:
      percentiles-histogram:
        yawl.case.execution.time: true
        yawl.workitem.execution.time: true
```

### Security Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
        exclude: env,beans  # Exclude sensitive endpoints
  endpoint:
    health:
      show-details: when-authorized  # Require authentication for details
```

## Troubleshooting

### Health Endpoint Returns 404

**Problem:** `/actuator/health` returns 404 Not Found

**Solution:** Ensure Spring Boot Actuator is enabled:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
```

### Prometheus Endpoint Empty

**Problem:** `/actuator/prometheus` returns empty response

**Solution:** Verify Micrometer Prometheus registry:
```bash
mvn dependency:tree | grep micrometer
```

### Health Check Shows DOWN

**Problem:** Engine reports status DOWN

**Solution:** Check specific indicators:
```bash
curl http://localhost:8080/actuator/health | jq .
```

Look for error details in `components` section.

### High Memory Usage

**Problem:** JVM memory metrics show high usage

**Solution:** Adjust heap size:
```bash
java -Xmx2g -Xms1g -jar yawl-5.2.jar
```

## Performance Considerations

### Health Check Overhead

Health checks are lightweight but called frequently:
- Liveness: Every 10s (typical)
- Readiness: Every 5s (typical)

**Optimization:**
- Cache engine state for 1-5 seconds
- Use async database checks
- Limit external service checks

### Metrics Collection Overhead

Micrometer uses thread-safe counters with minimal overhead:
- Counter increment: ~10ns
- Timer recording: ~100ns
- Gauge reading: ~50ns

**Best Practices:**
- Use histograms for timing metrics
- Avoid high-cardinality tags (e.g., unique IDs)
- Set appropriate percentiles

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     YAWL Engine                          │
│                                                          │
│  ┌────────────────┐  ┌────────────────┐                │
│  │  YEngine       │  │ YPersistence   │                │
│  │                │  │  Manager       │                │
│  └───────┬────────┘  └───────┬────────┘                │
│          │                    │                          │
│          └────────┬───────────┘                          │
│                   │                                      │
│  ┌────────────────▼───────────────────────┐            │
│  │    Spring Boot Actuator Layer          │            │
│  │                                         │            │
│  │  ┌─────────────────────────────────┐  │            │
│  │  │   Health Indicators              │  │            │
│  │  │  • YEngineHealthIndicator        │  │            │
│  │  │  • YDatabaseHealthIndicator      │  │            │
│  │  │  • YExternalServicesHealthInd    │  │            │
│  │  │  • YLivenessHealthIndicator      │  │            │
│  │  │  • YReadinessHealthIndicator     │  │            │
│  │  └─────────────────────────────────┘  │            │
│  │                                         │            │
│  │  ┌─────────────────────────────────┐  │            │
│  │  │   Metrics Collectors             │  │            │
│  │  │  • YWorkflowMetrics              │  │            │
│  │  │  • YAgentPerformanceMetrics      │  │            │
│  │  │  • YResourceMetrics              │  │            │
│  │  └─────────────────────────────────┘  │            │
│  │                                         │            │
│  │  ┌─────────────────────────────────┐  │            │
│  │  │   Endpoints                      │  │            │
│  │  │  • /actuator/health              │  │            │
│  │  │  • /actuator/metrics             │  │            │
│  │  │  • /actuator/prometheus          │  │            │
│  │  └─────────────────────────────────┘  │            │
│  └─────────────────────────────────────┘  │            │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────┐
        │   Cloud Platform          │
        │  • Kubernetes             │
        │  • Cloud Run              │
        │  • Prometheus             │
        │  • Grafana                │
        └───────────────────────────┘
```

## API Reference

See JavaDoc for detailed API documentation:

```bash
mvn javadoc:javadoc
open target/site/apidocs/index.html
```

## Contributing

Health indicators and metrics are extensible. To add custom indicators:

1. Create class implementing `HealthIndicator`
2. Annotate with `@Component`
3. Inject required dependencies
4. Implement `health()` method

Example:
```java
@Component
public class MyCustomHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check custom health criteria
        return Health.up()
            .withDetail("custom", "value")
            .build();
    }
}
```

## License

YAWL is licensed under the GNU Lesser General Public License (LGPL).

## Support

- Documentation: https://yawlfoundation.github.io/yawl
- Issues: https://github.com/yawlfoundation/yawl/issues
- Discussions: https://github.com/yawlfoundation/yawl/discussions

## References

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Metrics](https://micrometer.io/docs)
- [Prometheus](https://prometheus.io/docs/)
- [Kubernetes Health Checks](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Cloud Run Health Checks](https://cloud.google.com/run/docs/configuring/healthchecks)
