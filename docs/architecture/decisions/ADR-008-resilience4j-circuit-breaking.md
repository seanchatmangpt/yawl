# ADR-008: Resilience4j Circuit Breaking

## Status
**ACCEPTED**

## Context

YAWL integrates with external services (databases, email servers, web services, resource services) that can fail or become slow. Without protection, these failures cascade through the system causing:

### Business Drivers

1. **System Reliability**
   - Prevent cascade failures from external service outages
   - Maintain core functionality when dependencies are degraded
   - Graceful degradation instead of complete failure

2. **Performance Protection**
   - Fail fast when services are slow
   - Prevent thread pool exhaustion
   - Avoid timeout pileups

3. **User Experience**
   - Provide fallback responses when possible
   - Clear error messages during outages
   - Automatic recovery without manual intervention

### Technical Constraints

1. **Integration Points Requiring Protection**
   - Database connections (PostgreSQL pool)
   - Resource Service HTTP calls
   - Worklet Service HTTP calls
   - External web service tasks
   - Email notification delivery

2. **Failure Modes**
   - Connection timeouts (slow network)
   - Service unavailable (500 errors)
   - Rate limiting (429 responses)
   - Connection refused (service down)

## Decision

**We will implement Resilience4j for circuit breaking with configurable thresholds and fallback mechanisms.**

### Architecture

```
+------------------------------------------------------------------+
|                     YAWL Engine                                   |
|  +------------------------------------------------------------+  |
|  |                    Service Layer                           |  |
|  |  +-------------+  +-------------+  +-------------+        |  |
|  |  |   Case      |  |   Work      |  |  Resource   |        |  |
|  |  |   Service   |  |   Item      |  |  Service    |        |  |
|  |  |             |  |   Service   |  |  Client     |        |  |
|  |  +------+------+  +------+------+  +------+------+        |  |
|  +---------+----------------+----------------+---------------+  |
|            |                |                |                  |
+------------+----------------+----------------+------------------+
             |
+------------v----------------------------------------------------+
|                     Resilience4j Layer                            |
|  +------------------------------------------------------------+  |
|  |  Circuit Breakers                                          |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |  | resourceSvc |  | workletSvc  |  | database    |         |  |
|  |  |    CB       |  |    CB       |  |    CB       |         |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |                                                            |  |
|  |  +--------------------------------------------------------+|  |
|  |  | State Machine: CLOSED -> OPEN -> HALF_OPEN -> CLOSED  ||  |
|  |  +--------------------------------------------------------+|  |
|  +------------------------------------------------------------+  |
|                                                                  |
|  +------------------------------------------------------------+  |
|  |  Retry Mechanisms                                          |  |
|  |  +-------------+  +-------------+                         |  |
|  |  | exponential |  |   fixed     |                         |  |
|  |  | backoff     |  |   delay     |                         |  |
|  |  +-------------+  +-------------+                         |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
             |
+------------v----------------------------------------------------+
|                     External Services                             |
|  +-------------+  +-------------+  +-------------+               |
|  |  Resource   |  |  Worklet    |  |  Database   |               |
|  |  Service    |  |  Service    |  |  (PostgreSQL)|              |
|  +-------------+  +-------------+  +-------------+               |
+------------------------------------------------------------------+
```

### Circuit Breaker Configuration

#### application.yml

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 2s
        minimum-number-of-calls: 5
    instances:
      resourceService:
        base-config: default
        failure-rate-threshold: 60
        wait-duration-in-open-state: 20s
      workletService:
        base-config: default
        failure-rate-threshold: 70
        wait-duration-in-open-state: 15s
      database:
        base-config: default
        failure-rate-threshold: 80
        wait-duration-in-open-state: 10s
        slow-call-duration-threshold: 5s

  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - java.net.ConnectException
          - java.net.SocketTimeoutException
    instances:
      resourceService:
        base-config: default
        max-attempts: 3
      workletService:
        base-config: default
        max-attempts: 2

  timelimiter:
    configs:
      default:
        timeout-duration: 3s
        cancel-running-future: true
    instances:
      resourceService:
        timeout-duration: 5s
      workletService:
        timeout-duration: 3s
```

### Implementation Patterns

#### Service Client with Circuit Breaker

```java
@Service
public class ResourceServiceClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "resourceService", fallbackMethod = "getResourcesFallback")
    @Retry(name = "resourceService")
    @TimeLimiter(name = "resourceService")
    public CompletableFuture<List<YResource>> getResources(String caseId) {
        return CompletableFuture.supplyAsync(() ->
            restClient.get()
                .uri("/resources/{caseId}", caseId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<YResource>>() {})
        );
    }

    public CompletableFuture<List<YResource>> getResourcesFallback(
            String caseId, Throwable t) {
        log.warn("Resource service unavailable for case {}, using fallback", caseId, t);
        // Return cached resources or empty list
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
```

#### Worklet Service with Fallback

```java
@Service
public class WorkletServiceClient {

    @CircuitBreaker(name = "workletService", fallbackMethod = "selectWorkletFallback")
    @Retry(name = "workletService")
    public WorkletSelection selectWorklet(String taskId, Map<String, Object> context) {
        return restClient.post()
            .uri("/worklet/select")
            .body(Map.of("taskId", taskId, "context", context))
            .retrieve()
            .body(WorkletSelection.class);
    }

    public WorkletSelection selectWorkletFallback(
            String taskId, Map<String, Object> context, Throwable t) {
        log.warn("Worklet service unavailable, using default worklet for task {}", taskId);
        // Return default worklet (original task, no substitution)
        return WorkletSelection.defaultSelection(taskId);
    }
}
```

### Circuit Breaker States

```
        +---------+
        | CLOSED  |  <-- Normal operation, requests pass through
        +----+----+
             |
             | failure rate > threshold
             v
        +---------+
        |  OPEN   |  <-- All requests fail immediately (fail-fast)
        +----+----+
             |
             | wait duration elapsed
             v
        +-----------+
        |HALF_OPEN |  <-- Limited requests allowed to test recovery
        +-----+-----+
              |
              +-- success --> CLOSED (recovered)
              |
              +-- failure --> OPEN (still degraded)
```

### Configuration Examples

#### Strict Configuration (Critical Services)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      database:
        sliding-window-size: 20        # Larger sample size
        failure-rate-threshold: 30     # Lower threshold (more sensitive)
        wait-duration-in-open-state: 60s  # Longer wait before retry
        minimum-number-of-calls: 10    # More calls before evaluation
        slow-call-duration-threshold: 1s  # Stricter slow call threshold
```

#### Lenient Configuration (Non-Critical Services)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      notificationService:
        sliding-window-size: 5         # Smaller sample size
        failure-rate-threshold: 80     # Higher threshold (less sensitive)
        wait-duration-in-open-state: 10s  # Shorter wait
        minimum-number-of-calls: 3     # Fewer calls before evaluation
```

### Fallback Mechanisms

```java
@Component
public class ResourceServiceFallback {

    private final ResourceCache resourceCache;

    public List<YResource> getResourcesFallback(String caseId, Throwable t) {
        // Strategy 1: Return cached data
        List<YResource> cached = resourceCache.get(caseId);
        if (!cached.isEmpty()) {
            log.info("Returning cached resources for case {}", caseId);
            return cached;
        }

        // Strategy 2: Return default resources
        log.warn("No cached resources, returning defaults for case {}", caseId);
        return ResourceDefaults.getDefaultResources();
    }

    public Participant allocateResourceFallback(
            String workItemId, Throwable t) {
        // Strategy 3: Round-robin allocation
        log.warn("Resource service down, using round-robin for {}", workItemId);
        return RoundRobinAllocator.allocate(workItemId);
    }
}
```

### Monitoring and Metrics

```java
@Configuration
public class Resilience4jMetricsConfig {

    @Bean
    public CircuitBreakerMetricsPublisher circuitBreakerMetricsPublisher(
            MeterRegistry meterRegistry) {
        return new CustomCircuitBreakerMetricsPublisher(meterRegistry);
    }
}
```

#### Grafana Dashboard Panels

```yaml
# Circuit Breaker State
yawl_circuit_breaker_state{instance="yawl-engine", name="resourceService"}
# Values: 0=CLOSED, 1=OPEN, 2=HALF_OPEN

# Failure Rate
yawl_circuit_breaker_failure_rate{instance="yawl-engine", name="resourceService"}

# Call Counts
yawl_circuit_breaker_calls_total{instance="yawl-engine", name="resourceService", kind="successful|failed|not_permitted|slow"}
```

### Health Endpoint

```java
@RestController
@RequestMapping("/actuator")
public class CircuitBreakerHealthEndpoint {

    private final CircuitBreakerRegistry registry;

    @GetMapping("/circuit-breakers")
    public Map<String, Object> circuitBreakerStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        registry.getAllCircuitBreakers().forEach(cb -> {
            CircuitBreakerMetrics metrics = cb.getMetrics();
            status.put(cb.getName(), Map.of(
                "state", cb.getState().name(),
                "failureRate", metrics.getFailureRate(),
                "slowCallRate", metrics.getSlowCallRate(),
                "numberOfCalls", metrics.getNumberOfCalls(),
                "numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls(),
                "numberOfFailedCalls", metrics.getNumberOfFailedCalls()
            ));
        });
        return status;
    }
}
```

## Consequences

### Positive

1. **System Resilience**
   - Prevents cascade failures
   - Fail-fast reduces resource waste
   - Automatic recovery without intervention

2. **User Experience**
   - Graceful degradation with fallbacks
   - Consistent response times (fail-fast)
   - Clear error messages during outages

3. **Observability**
   - Metrics for circuit breaker state
   - Failure rate monitoring
   - Alerting on open circuits

### Negative

1. **Configuration Complexity**
   - Tuning thresholds requires experimentation
   - Different configs for different services
   - Risk of over-sensitive circuits

2. **Fallback Design**
   - Requires thoughtful fallback strategies
   - May mask underlying issues
   - Additional code to maintain

3. **Testing Challenges**
   - Hard to test circuit breaker behavior
   - Need chaos engineering for validation
   - Simulating failures is complex

## Alternatives Considered

### Hystrix (Netflix)
**Rejected**: No longer actively maintained, Resilience4j is the successor.

**Pros:**
- Mature and battle-tested
- Rich dashboard

**Cons:**
- Maintenance mode since 2018
- Heavier dependency
- Less Spring Boot integration

### Spring Retry
**Rejected**: Only retry, no circuit breaker.

**Pros:**
- Simple retry mechanism
- Spring native

**Cons:**
- No circuit breaking
- No rate limiting
- No bulkhead

## Implementation Notes

### When to Use Circuit Breakers

| Service Type | Circuit Breaker? | Reason |
|--------------|------------------|--------|
| Database | Yes | Critical, can fail |
| Resource Service | Yes | Network dependency |
| Worklet Service | Yes | Network dependency |
| In-memory operations | No | No external dependency |
| Async notifications | Yes | External SMTP/API |

### Threshold Guidelines

| Failure Rate | Wait Duration | Use Case |
|--------------|---------------|----------|
| 30% | 60s | Critical services |
| 50% | 30s | Standard services |
| 70% | 15s | Non-critical services |

## Related ADRs

- ADR-006: OpenTelemetry Observability (circuit breaker metrics)
- ADR-007: Repository Pattern Caching (cache as fallback)
- ADR-014: Clustering and Horizontal Scaling (distributed circuit breakers)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** IN PROGRESS
**Review Date:** 2026-05-01 (3 months)

---

**Revision History:**
- 2026-02-18: Initial version approved
