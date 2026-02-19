# YAWL Resilience Quick Start

## 5-Minute Setup

Get production-grade fault tolerance in 5 minutes.

### Step 1: Dependencies (Already Included)

YAWL v6.0.0 ships with Resilience4j. No additional dependencies needed.

```xml
<!-- Already in pom.xml -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

### Step 2: Enable Resilience (Default: ON)

Resilience is **enabled by default**. To customize, create:

`/home/user/yawl/config/resilience/resilience4j.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      engineService:
        failureRateThreshold: 60
        slowCallDurationThreshold: 2s
```

### Step 3: Use Resilient Adapters

Replace standard adapters with resilient versions:

```java
// Before
import org.yawlfoundation.yawl.integration.a2a.YawlEngineAdapter;
YawlEngineAdapter adapter = new YawlEngineAdapter(url, user, pass);

// After (ONE LINE CHANGE)
import org.yawlfoundation.yawl.resilience.decorator.ResilientYawlEngineAdapter;
ResilientYawlEngineAdapter adapter = ResilientYawlEngineAdapter.fromEnvironment();
```

**That's it!** You now have:
- Circuit breakers
- Retries with exponential backoff
- Rate limiting
- Bulkhead isolation
- Full metrics

### Step 4: Monitor (Optional)

View metrics:
```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
```

View health:
```bash
curl http://localhost:8080/actuator/health
```

---

## Common Patterns

### Pattern 1: Resilient Engine Calls

```java
import org.yawlfoundation.yawl.resilience.decorator.ResilientYawlEngineAdapter;

ResilientYawlEngineAdapter adapter = ResilientYawlEngineAdapter.fromEnvironment();
adapter.connect();

// All operations now have resilience
String caseId = adapter.launchCase("OrderWorkflow", caseData);
List<WorkItemRecord> items = adapter.getWorkItems();
adapter.completeTask(caseId, "ApprovalTask", outputData);
```

### Pattern 2: External API Calls

```java
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;

YawlResilienceProvider resilience = YawlResilienceProvider.getInstance();

// HTTP call with circuit breaker + retry
Response response = resilience.executeExternalCall(() ->
    httpClient.post("https://api.example.com/endpoint", payload)
);
```

### Pattern 3: Multi-Agent Operations

```java
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;
import java.util.concurrent.CompletableFuture;

YawlResilienceProvider resilience = YawlResilienceProvider.getInstance();

// Fan-out with rate limiting + bulkhead
CompletableFuture<List<Result>> results = resilience.executeMultiAgentFanout(() ->
    agents.parallelStream()
        .map(Agent::execute)
        .collect(Collectors.toList())
);
```

### Pattern 4: Retry-Only

```java
// Just retries, no circuit breaker
String result = resilience.executeWithRetry(() ->
    transientService.call()
);
```

### Pattern 5: Rate Limit-Only

```java
// Just rate limiting
String result = resilience.executeWithRateLimit(() ->
    externalApi.query(params)
);
```

---

## Configuration Examples

### Conservative (Prod)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      engineService:
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        minimumNumberOfCalls: 20

  retry:
    instances:
      default:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2.0
```

### Aggressive (Dev)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      engineService:
        failureRateThreshold: 80
        waitDurationInOpenState: 10s
        minimumNumberOfCalls: 3

  retry:
    instances:
      default:
        maxAttempts: 5
        waitDuration: 200ms
```

---

## Testing

### Unit Test Example

```java
import org.junit.Test;
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@Test
public void testRetryWithRecovery() throws Exception {
    YawlResilienceProvider provider = YawlResilienceProvider.getInstance();
    AtomicInteger attempts = new AtomicInteger(0);

    String result = provider.executeEngineCall(() -> {
        if (attempts.incrementAndGet() < 3) {
            throw new IOException("Transient error");
        }
        return "success";
    });

    assertEquals("success", result);
    assertEquals(3, attempts.get());  // Retried twice
}
```

---

## Troubleshooting

### Problem: Circuit opens immediately

**Cause**: Not enough calls to calculate failure rate

**Fix**: Lower `minimumNumberOfCalls`
```yaml
minimumNumberOfCalls: 5  # from 10
```

### Problem: Too many retries

**Cause**: Aggressive retry configuration

**Fix**: Reduce retry attempts
```yaml
maxAttempts: 2  # from 3
```

### Problem: Rate limiter rejecting valid requests

**Cause**: Limit too low for traffic volume

**Fix**: Increase rate limit
```yaml
limitForPeriod: 200  # from 100
```

---

## Next Steps

1. **Read the Operations Guide**: [RESILIENCE_OPERATIONS_GUIDE.md](RESILIENCE_OPERATIONS_GUIDE.md)
2. **Set up monitoring**: Import Prometheus alerts from `config/resilience/prometheus-alerts.yml`
3. **Tune thresholds**: Adjust based on your service SLAs
4. **Add custom patterns**: Create domain-specific circuit breakers

---

## Cheat Sheet

| Use Case | Method | Patterns Applied |
|----------|--------|------------------|
| YAWL engine call | `executeEngineCall()` | Circuit breaker + Retry |
| External API | `executeExternalCall()` | Circuit breaker + Retry + Timeout |
| MCP operation | `executeMcpCall()` | Circuit breaker + Retry |
| A2A operation | `executeA2aCall()` | Circuit breaker + Retry |
| Multi-agent | `executeMultiAgentFanout()` | Rate limiter + Bulkhead + Retry |
| Retry only | `executeWithRetry()` | Retry |
| Rate limit only | `executeWithRateLimit()` | Rate limiter |
| Bulkhead only | `executeWithBulkhead()` | Bulkhead |

---

## Support

- **Documentation**: `/home/user/yawl/docs/resilience/`
- **Examples**: `/home/user/yawl/test/org/yawlfoundation/yawl/resilience/`
- **Configuration**: `/home/user/yawl/config/resilience/resilience4j.yml`

**Version**: 5.2
**Last Updated**: 2026-02-15
