# YAWL Resilience4j Operations Guide

## Overview

YAWL v5.2 includes **platform-level resilience** powered by Resilience4j. All teams get production-grade fault tolerance **by default** without writing boilerplate code.

This guide covers:
- Resilience pattern usage
- Threshold tuning
- Monitoring and alerting
- Troubleshooting
- Performance optimization

---

## Architecture

### Resilience Patterns

YAWL implements five resilience patterns:

1. **Circuit Breakers** - Prevent cascade failures
2. **Retries** - Handle transient errors with exponential backoff
3. **Rate Limiters** - Control request rates
4. **Bulkheads** - Isolate concurrent operations
5. **Time Limiters** - Enforce operation timeouts

### Integration Points

Resilience is applied at four integration boundaries:

- **Engine Service** - InterfaceA/InterfaceB calls
- **External Services** - HTTP/REST calls
- **MCP Integration** - Model Context Protocol operations
- **A2A Integration** - Agent-to-Agent communication

---

## Usage Patterns

### 1. Resilient Engine Calls

All YAWL engine operations automatically use circuit breakers and retries:

```java
import org.yawlfoundation.yawl.resilience.decorator.ResilientYawlEngineAdapter;

// Create resilient adapter (drop-in replacement)
ResilientYawlEngineAdapter adapter = ResilientYawlEngineAdapter.fromEnvironment();

// All operations now have built-in resilience
adapter.connect();
String caseId = adapter.launchCase("OrderProcessing", caseData);
List<WorkItemRecord> items = adapter.getWorkItems();
```

**Resilience Applied:**
- Circuit breaker: `engineService`
- Retry: `default` (3 attempts, exponential backoff)
- Failure threshold: 60%
- Slow call threshold: 2 seconds

### 2. External Service Calls

For HTTP/REST calls to external services:

```java
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;

YawlResilienceProvider resilience = YawlResilienceProvider.getInstance();

// Execute with full resilience stack
Response response = resilience.executeExternalCall(() ->
    httpClient.post(url, data)
);
```

**Resilience Applied:**
- Circuit breaker: `externalService`
- Retry: `default` (3 attempts)
- Failure threshold: 50%
- Slow call threshold: 5 seconds
- Open state duration: 60 seconds

### 3. Multi-Agent Fan-Out

For concurrent agent invocations:

```java
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;
import java.util.concurrent.CompletableFuture;

YawlResilienceProvider resilience = YawlResilienceProvider.getInstance();

// Execute with rate limiting and bulkhead
CompletableFuture<List<Result>> results = resilience.executeMultiAgentFanout(() ->
    agents.parallelStream()
        .map(Agent::execute)
        .collect(toList())
);
```

**Resilience Applied:**
- Rate limiter: `multiAgentFanout` (50 calls/sec)
- Bulkhead: `default` (25 concurrent calls)
- Retry: `default`
- Prevents thundering herd

### 4. MCP/A2A Integration

For protocol integration operations:

```java
// MCP calls
McpResponse mcpResult = resilience.executeMcpCall(() ->
    mcpClient.sendRequest(request)
);

// A2A calls
A2AResponse a2aResult = resilience.executeA2aCall(() ->
    a2aClient.executeTask(task)
);
```

---

## Threshold Tuning

### Circuit Breaker Tuning

#### Key Parameters

| Parameter | Default | Description | Tuning Guide |
|-----------|---------|-------------|--------------|
| `failureRateThreshold` | 50% | Open circuit when failure rate exceeds | Lower for critical services (30-40%), higher for flaky services (60-70%) |
| `slowCallRateThreshold` | 50% | Open circuit when slow call rate exceeds | Adjust based on SLA requirements |
| `slowCallDurationThreshold` | 3s | Defines a slow call | Set to 2x expected latency |
| `minimumNumberOfCalls` | 10 | Min calls before calculating rates | Increase for high-traffic (50-100), decrease for low-traffic (5) |
| `waitDurationInOpenState` | 30s | How long circuit stays open | Increase for persistent failures (60s+), decrease for transient issues (10-20s) |

#### Tuning by Service Type

**Fast Internal Services** (InterfaceB/A):
```yaml
engineService:
  failureRateThreshold: 60
  slowCallDurationThreshold: 2s
  waitDurationInOpenState: 20s
```

**External APIs** (HTTP/REST):
```yaml
externalService:
  failureRateThreshold: 50
  slowCallDurationThreshold: 5s
  waitDurationInOpenState: 60s
  minimumNumberOfCalls: 20
```

**Critical Dependencies**:
```yaml
critical:
  failureRateThreshold: 30
  slowCallDurationThreshold: 1s
  waitDurationInOpenState: 10s
  permittedNumberOfCallsInHalfOpenState: 10
```

### Retry Tuning

#### Key Parameters

| Parameter | Default | Description | Tuning Guide |
|-----------|---------|-------------|--------------|
| `maxAttempts` | 3 | Maximum retry attempts | 2-3 for idempotent ops, 1 for non-idempotent |
| `waitDuration` | 500ms | Base wait between retries | Match service recovery time |
| `exponentialBackoffMultiplier` | 2.0 | Backoff multiplier | 1.5-2.0 for aggressive, 2.0-3.0 for conservative |
| `randomizationFactor` | 0.5 | Jitter randomization | 0.3-0.7 (prevents thundering herd) |

#### Retry Strategies

**Aggressive** (Quick recovery):
```yaml
aggressive:
  maxAttempts: 5
  waitDuration: 200ms
  exponentialBackoffMultiplier: 1.5
```

**Conservative** (Expensive operations):
```yaml
conservative:
  maxAttempts: 2
  waitDuration: 1s
  exponentialBackoffMultiplier: 3.0
```

**Custom Backoff Calculation**:
- Attempt 1: 500ms
- Attempt 2: 500ms × 2.0 × (0.5-1.5 jitter) = 500-1500ms
- Attempt 3: 1000ms × 2.0 × (0.5-1.5 jitter) = 1000-3000ms
- Max wait: 10s (capped by `exponentialMaxWaitDuration`)

### Rate Limiter Tuning

#### Key Parameters

| Parameter | Default | Description | Tuning Guide |
|-----------|---------|-------------|--------------|
| `limitForPeriod` | 100 | Max calls per period | Set to 80% of service capacity |
| `limitRefreshPeriod` | 1s | Period duration | 1s for fine-grained control, 60s for hourly limits |
| `timeoutDuration` | 500ms | Max wait for permission | Match client timeout requirements |

#### Rate Limiting Strategies

**External API** (respecting upstream limits):
```yaml
externalApi:
  limitForPeriod: 100  # API allows 100/min
  limitRefreshPeriod: 60s
  timeoutDuration: 2s
```

**Multi-Agent Fan-Out** (prevent overwhelm):
```yaml
multiAgentFanout:
  limitForPeriod: 50
  limitRefreshPeriod: 1s
  timeoutDuration: 1s
```

**Burst Traffic** (allow spikes):
```yaml
burstTraffic:
  limitForPeriod: 200
  limitRefreshPeriod: 1s
  timeoutDuration: 100ms
```

### Bulkhead Tuning

#### Key Parameters

| Parameter | Default | Description | Tuning Guide |
|-----------|---------|-------------|--------------|
| `maxConcurrentCalls` | 25 | Max concurrent operations | Set to thread pool size / number of bulkheads |
| `maxWaitDuration` | 500ms | Max wait for available slot | Match client timeout |

#### Bulkhead Strategies

**High Concurrency** (workflow engine):
```yaml
highConcurrency:
  maxConcurrentCalls: 100
  maxWaitDuration: 1s
```

**Resource Protection** (database/file I/O):
```yaml
lowConcurrency:
  maxConcurrentCalls: 5
  maxWaitDuration: 2s
```

**Thread Pool Bulkhead** (async operations):
```yaml
asyncBulkhead:
  maxThreadPoolSize: 20
  coreThreadPoolSize: 10
  queueCapacity: 100
```

---

## Monitoring and Alerting

### Metrics Endpoints

**Prometheus Metrics**:
```bash
curl http://localhost:8080/actuator/prometheus | grep resilience4j
```

**Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

**Resilience4j Events**:
```bash
curl http://localhost:8080/actuator/resilience4j/events
```

### Key Metrics

#### Circuit Breaker Metrics

```promql
# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="engineService"}

# Failure rate
resilience4j_circuitbreaker_failure_rate{name="engineService"}

# Slow call rate
resilience4j_circuitbreaker_slow_call_rate{name="engineService"}

# Not permitted calls (circuit open)
resilience4j_circuitbreaker_not_permitted_calls_total{name="engineService"}
```

#### Retry Metrics

```promql
# Successful calls without retry
resilience4j_retry_calls_total{name="default",kind="successful_without_retry"}

# Successful calls with retry
resilience4j_retry_calls_total{name="default",kind="successful_with_retry"}

# Failed calls after retries
resilience4j_retry_calls_total{name="default",kind="failed_with_retry"}
```

#### Rate Limiter Metrics

```promql
# Available permissions
resilience4j_ratelimiter_available_permissions{name="default"}

# Waiting threads
resilience4j_ratelimiter_waiting_threads{name="default"}
```

#### Bulkhead Metrics

```promql
# Available concurrent calls
resilience4j_bulkhead_available_concurrent_calls{name="default"}

# Max concurrent calls
resilience4j_bulkhead_max_allowed_concurrent_calls{name="default"}
```

### Alerting Rules

**Circuit Breaker Open** (Critical):
```yaml
alert: CircuitBreakerOpen
expr: resilience4j_circuitbreaker_state == 1
for: 1m
labels:
  severity: critical
annotations:
  summary: "Circuit breaker {{ $labels.name }} is OPEN"
  description: "Circuit breaker has opened due to high failure rate"
```

**High Failure Rate** (Warning):
```yaml
alert: HighCircuitBreakerFailureRate
expr: resilience4j_circuitbreaker_failure_rate > 40
for: 5m
labels:
  severity: warning
annotations:
  summary: "High failure rate for {{ $labels.name }}"
  description: "Failure rate is {{ $value }}%"
```

**Retry Exhaustion** (Warning):
```yaml
alert: HighRetryFailureRate
expr: rate(resilience4j_retry_calls_total{kind="failed_with_retry"}[5m]) > 0.1
for: 5m
labels:
  severity: warning
annotations:
  summary: "High retry failure rate for {{ $labels.name }}"
  description: "{{ $value }} retries/sec are failing"
```

**Rate Limit Saturation** (Info):
```yaml
alert: RateLimitSaturation
expr: resilience4j_ratelimiter_available_permissions == 0
for: 2m
labels:
  severity: info
annotations:
  summary: "Rate limiter {{ $labels.name }} saturated"
  description: "No available permissions, requests are being throttled"
```

**Bulkhead Saturation** (Warning):
```yaml
alert: BulkheadSaturation
expr: resilience4j_bulkhead_available_concurrent_calls == 0
for: 2m
labels:
  severity: warning
annotations:
  summary: "Bulkhead {{ $labels.name }} saturated"
  description: "All concurrent slots are in use"
```

---

## Troubleshooting

### Circuit Breaker Issues

#### Problem: Circuit opens too frequently

**Symptoms:**
- `resilience4j_circuitbreaker_state == 1` frequently
- High `not_permitted_calls` count

**Diagnosis:**
```bash
# Check failure rate
curl http://localhost:8080/actuator/circuitbreakerevents/engineService/failure

# Check slow calls
curl http://localhost:8080/actuator/circuitbreakerevents/engineService/slow
```

**Solutions:**
1. **Increase failure threshold** if service is inherently flaky:
   ```yaml
   failureRateThreshold: 70  # from 50
   ```

2. **Adjust slow call threshold** if operations are legitimately slow:
   ```yaml
   slowCallDurationThreshold: 10s  # from 3s
   ```

3. **Increase minimum calls** for high-traffic services:
   ```yaml
   minimumNumberOfCalls: 50  # from 10
   ```

#### Problem: Circuit doesn't open when it should

**Symptoms:**
- High error rates but circuit stays closed
- Cascade failures

**Diagnosis:**
```bash
# Check if minimum calls threshold is met
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.buffered.calls
```

**Solutions:**
1. **Lower minimum calls** for low-traffic services:
   ```yaml
   minimumNumberOfCalls: 5  # from 10
   ```

2. **Lower failure threshold** for critical services:
   ```yaml
   failureRateThreshold: 30  # from 50
   ```

### Retry Issues

#### Problem: Too many retries causing load

**Symptoms:**
- High retry counts
- Increased latency
- Downstream service overwhelmed

**Diagnosis:**
```bash
# Check retry metrics
curl http://localhost:8080/actuator/metrics/resilience4j.retry.calls
```

**Solutions:**
1. **Reduce max attempts**:
   ```yaml
   maxAttempts: 2  # from 3
   ```

2. **Increase backoff**:
   ```yaml
   exponentialBackoffMultiplier: 3.0  # from 2.0
   ```

3. **Add jitter** to prevent thundering herd:
   ```yaml
   randomizationFactor: 0.7  # from 0.5
   ```

#### Problem: Retries not happening

**Symptoms:**
- Low retry success rate
- Immediate failures

**Diagnosis:**
```bash
# Check if exceptions are being retried
curl http://localhost:8080/actuator/retryevents
```

**Solutions:**
1. **Add exception to retry list**:
   ```yaml
   retryExceptions:
     - java.io.IOException
     - com.custom.TransientException  # Add this
   ```

2. **Check ignore exceptions** - ensure critical exceptions aren't ignored

### Rate Limiter Issues

#### Problem: Legitimate requests being throttled

**Symptoms:**
- High `waiting_threads` count
- Request timeouts
- `RequestNotPermitted` exceptions

**Solutions:**
1. **Increase rate limit**:
   ```yaml
   limitForPeriod: 200  # from 100
   ```

2. **Increase timeout**:
   ```yaml
   timeoutDuration: 2s  # from 500ms
   ```

3. **Analyze traffic patterns** and adjust refresh period

### Bulkhead Issues

#### Problem: Bulkhead rejections causing failures

**Symptoms:**
- `BulkheadFullException` in logs
- High `max_allowed_concurrent_calls` metric

**Solutions:**
1. **Increase concurrent calls**:
   ```yaml
   maxConcurrentCalls: 50  # from 25
   ```

2. **Use thread pool bulkhead** for async operations:
   ```yaml
   thread-pool-bulkhead:
     maxThreadPoolSize: 20
     queueCapacity: 100
   ```

3. **Add queue** for bursts

---

## Performance Optimization

### Minimizing Overhead

1. **Use TIME_BASED sliding window** for high-traffic services:
   ```yaml
   slidingWindowType: TIME_BASED
   slidingWindowSize: 10  # seconds
   ```

2. **Disable health indicators** in production if not needed:
   ```yaml
   registerHealthIndicator: false
   ```

3. **Optimize metrics collection**:
   ```yaml
   management:
     metrics:
       distribution:
         percentiles-histogram:
           resilience4j.circuitbreaker.calls: false
   ```

### Capacity Planning

**Circuit Breaker Memory**:
- COUNT_BASED: ~1KB per 100 calls
- TIME_BASED: ~5KB per window

**Bulkhead Threads**:
- Each bulkhead consumes memory: `maxThreadPoolSize × threadStackSize`
- Default: 10 threads × 1MB = 10MB per bulkhead

**Recommendations**:
- Monitor JVM heap usage
- Size thread pools to match CPU cores
- Use bulkheads to prevent resource exhaustion

---

## Environment-Specific Tuning

### Development

```yaml
# Fast feedback, loose thresholds
circuitbreaker:
  engineService:
    failureRateThreshold: 80
    waitDurationInOpenState: 10s
    minimumNumberOfCalls: 3

retry:
  default:
    maxAttempts: 2
```

### Staging

```yaml
# Match production, enable verbose logging
circuitbreaker:
  engineService:
    failureRateThreshold: 60
    waitDurationInOpenState: 20s

logging:
  level:
    io.github.resilience4j: DEBUG
```

### Production

```yaml
# Conservative thresholds, minimal logging
circuitbreaker:
  engineService:
    failureRateThreshold: 50
    waitDurationInOpenState: 30s
    minimumNumberOfCalls: 20

retry:
  default:
    maxAttempts: 3
    exponentialBackoffMultiplier: 2.0

logging:
  level:
    io.github.resilience4j: WARN
```

---

## Advanced Patterns

### Custom Circuit Breakers

```java
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;

YawlResilienceProvider provider = YawlResilienceProvider.getInstance();

// Create custom circuit breaker
CircuitBreaker customCB = provider.getResilienceConfig()
    .getCircuitBreakerRegistry()
    .circuitBreaker("customService", CircuitBreakerConfig.custom()
        .failureRateThreshold(70)
        .slowCallDurationThreshold(Duration.ofSeconds(8))
        .build());

// Use custom circuit breaker
String result = provider.executeWithCustomCircuitBreaker("customService", () ->
    customService.call()
);
```

### Fallback Strategies

```java
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

try {
    return resilience.executeEngineCall(() -> primaryService.call());
} catch (CallNotPermittedException e) {
    // Circuit is open, use fallback
    logger.warn("Circuit open, using cached data");
    return cache.get(key);
}
```

### Event Listeners

```java
CircuitBreaker cb = provider.getCircuitBreaker("engineService");

cb.getEventPublisher()
    .onStateTransition(event -> {
        logger.warn("Circuit breaker state: {} -> {}",
            event.getStateTransition().getFromState(),
            event.getStateTransition().getToState());

        // Send alert
        alertService.send("Circuit breaker state changed");
    })
    .onError(event -> {
        logger.error("Circuit breaker error: {}", event.getThrowable());
    });
```

---

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Micrometer Metrics](https://micrometer.io/docs)
- [Prometheus Alerting](https://prometheus.io/docs/alerting/latest/)

---

**Version**: 5.2
**Last Updated**: 2026-02-15
**Maintainer**: YAWL Foundation
