# Data Marketplace Architecture & Integration Guide

**Version**: 1.0
**Date**: 2026-02-21
**For**: YAWL v6.0.0 MCP/A2A Integration
**Status**: Design Reference for Implementation

---

## Overview

The Data Marketplace is a privacy-first metrics aggregation system that collects anonymized execution data from YAWL skills (MCP tools) and cases (workflows), enabling benchmarking and performance comparison without exposing personally identifiable information.

**Architecture principle**: Anonymize at source, aggregate in database, expose only aggregates via API.

---

## System Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ YAWL Core Engine + MCP/A2A                                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────┐         ┌──────────────────┐                 │
│  │ YawlMcpServer    │         │ YawlA2AServer    │                 │
│  │ (MCP tool host)  │         │ (A2A skill host) │                 │
│  └────────┬─────────┘         └────────┬─────────┘                 │
│           │                           │                             │
│           └───────────┬───────────────┘                             │
│                       │ (hook calls)                                │
│         ┌─────────────▼──────────────┐                             │
│         │ MetricsCollectionHook      │                             │
│         │ • onToolCompletion()       │                             │
│         │ • onCaseCompletion()       │                             │
│         └─────────────┬──────────────┘                             │
│                       │ (async fire-and-forget)                    │
│         ┌─────────────▼──────────────────────┐                     │
│         │ MetricsCollector Interface         │                     │
│         │ • collectSkillMetrics()            │                     │
│         │ • collectCaseMetrics()             │                     │
│         │ • enqueueForAnonymization()        │                     │
│         └─────────────┬──────────────────────┘                     │
│                       │ (virtual thread task)                      │
│         ┌─────────────▼──────────────────────────┐                 │
│         │ AnonymizationPipeline                  │                 │
│         │ • applyRules()                         │                 │
│         │ • hashUserId()                         │                 │
│         │ • hashOrgId()                          │                 │
│         │ • updateMetadata()                     │                 │
│         └─────────────┬──────────────────────────┘                 │
│                       │                                             │
│         ┌─────────────▼──────────────────────────┐                 │
│         │ Database Layer (PostgreSQL + TSDb)     │                 │
│         │ Tables:                                │                 │
│         │ • skill_metrics_raw (7d retention)    │                 │
│         │ • skill_metrics_anonymized (90d)      │                 │
│         │ • case_metrics_raw (7d)               │                 │
│         │ • case_metrics_anonymized (90d)       │                 │
│         │ Views:                                │                 │
│         │ • skill_benchmarks_hourly            │                 │
│         │ • skill_benchmarks_industry          │                 │
│         │ • case_benchmarks_hourly             │                 │
│         └─────────────┬──────────────────────────┘                 │
│                       │                                             │
│         ┌─────────────▼──────────────────────────┐                 │
│         │ BenchmarkQueryService (Cached)         │                 │
│         │ • getSkillBenchmark()                 │                 │
│         │ • getIndustryPercentiles()            │                 │
│         │ • compareCases()                      │                 │
│         └─────────────┬──────────────────────────┘                 │
│                       │ (REST responses)                           │
│         ┌─────────────▼──────────────────────────┐                 │
│         │ REST API (BenchmarkController)         │                 │
│         │ GET /api/v1/benchmarks/skills/:id     │                 │
│         │ GET /api/v1/benchmarks/cases/:type    │                 │
│         │ GET /api/v1/benchmarks/export         │                 │
│         │ GET /api/v1/benchmarks/leaderboard    │                 │
│         │ GET /api/v1/benchmarks/trends         │                 │
│         └──────────────────────────────────────────┘               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Integration Points

### 1. MCP Tool Completion Hook

**Location**: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer.executeTool()`

**Hook implementation**:

```java
@Component
public class McpToolCompletionHook {
    @Autowired
    private MetricsCollector metricsCollector;

    /**
     * Called after any MCP tool execution (success or failure).
     * Enqueues metric collection asynchronously.
     */
    public void onToolCompletion(McpToolCompletionEvent event) {
        // Fire-and-forget: don't block MCP response
        metricsCollector.collectSkillMetrics(
            SkillMetricEvent.builder()
                .skillId(event.toolName())
                .skillName(event.toolDescription())
                .durationMs((int) event.duration().toMillis())
                .success(event.success())
                .errorType(event.errorType().orElse(null))
                .inputTokens(event.inputTokens())
                .outputTokens(event.outputTokens())
                .userId(extractUserId(event))      // From JWT context
                .organizationId(extractOrgId(event)) // From JWT context
                .requestSource("mcp_client")
                .region(detectRegion(event))
                .build()
        );
    }
}
```

**Event timing**:
- Latency: <10ms (async enqueueing, non-blocking)
- Collection: Persisted within 100ms
- Anonymization: Batched every 6 hours

**Data field mapping**:

| MCP Event | Metric Field | Notes |
|-----------|--------------|-------|
| `toolName` | `skill_id` | Tool identifier in MCP registry |
| `duration` | `duration_ms` | Wall-clock time |
| `isSuccess` | `success` | true/false |
| `exception.class` | `error_type` | Java class name |
| `inputTokenCount` | `input_tokens` | LLM tokens (if applicable) |
| `outputTokenCount` | `output_tokens` | LLM tokens produced |
| JWT subject | `user_id` | Extracted from MCP auth token |
| JWT org claim | `organization_id` | Custom claim in JWT |
| Request IP | `region` | Geo-lookup (optional) |

---

### 2. A2A Case Completion Hook

**Location**: `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer.handleCaseCompletion()`

**Hook implementation**:

```java
@Component
public class A2ACaseCompletionHook {
    @Autowired
    private MetricsCollector metricsCollector;

    /**
     * Called when a case transitions to completion state.
     * Records full case lifecycle metrics.
     */
    public void onCaseCompletion(CaseCompletionMessage msg) {
        metricsCollector.collectCaseMetrics(
            CaseMetricEvent.builder()
                .caseId(msg.getCaseId())
                .caseType(msg.getCaseType())
                .caseStartedAt(msg.getCreatedAt())
                .caseCompletedAt(Instant.now())
                .cycleTimeSeconds(calculateCycleTime(msg))
                .workItemsCompleted(msg.getCompletedCount())
                .workItemsFailed(msg.getFailedCount())
                .errorRate(calculateErrorRate(msg))
                .throughputCasesPerHour(calculateThroughput(msg))
                .organizationId(extractOrgId(msg))
                .assignedUser(extractAssignedUser(msg))
                .priority(msg.getPriority())
                .build()
        );
    }

    private int calculateCycleTime(CaseCompletionMessage msg) {
        return (int) Duration.between(
            msg.getCreatedAt(),
            Instant.now()
        ).toSeconds();
    }

    private double calculateErrorRate(CaseCompletionMessage msg) {
        int total = msg.getCompletedCount() + msg.getFailedCount();
        return total > 0
            ? (double) msg.getFailedCount() / total
            : 0.0;
    }
}
```

**Event mapping**:

| A2A Event | Metric Field | Notes |
|-----------|--------------|-------|
| `caseId` | `case_id` | YAWL case UUID |
| `processDefinition` | `case_type` | Workflow type name |
| `createdAt` | `case_started_at` | Case creation timestamp |
| `completionTime` | `case_completed_at` | Completion timestamp |
| `taskCount` | `work_items_completed` | Successfully completed tasks |
| `failedTaskCount` | `work_items_failed` | Failed or errored tasks |
| `orgContext` | `organization_id` | From A2A context |
| `assignedUser` | `assigned_user` | Last assigned user |

---

### 3. Data Flow: Raw → Anonymized → Aggregated

```
Timeline: T=0 to T=+6 hours

T+0:00  Skill/case execution
        ↓
T+0:00  MetricsCollectionHook.onCompletion()
        ├─ Enqueue metric to async queue
        └─ Return immediately (non-blocking)

T+0:01  MetricsCollector.collectSkillMetrics()
        ├─ Write raw metric to DB (with PII)
        ├─ Insert into skill_metrics_raw table
        └─ Increment collection counter

T+0:06  [Every 6 hours]
        AnonymizationScheduler.runAnonymization()
        ├─ Query: SELECT * FROM skill_metrics_raw WHERE anonymized=false
        ├─ For each raw metric:
        │   ├─ Apply anonymization rules (hash user_id, org_id, etc.)
        │   ├─ Insert into skill_metrics_anonymized
        │   └─ Mark raw as anonymized=true
        └─ Commit transaction

T+0:12  [Hourly]
        BenchmarkAggregator.aggregateHourly()
        ├─ Query anonymized metrics from past hour
        ├─ Calculate percentiles (p50, p95, p99)
        ├─ Calculate averages and success rates
        └─ Insert into benchmark_views (materialized)

T+7:00  [7 days later]
        DataRetentionEnforcer.enforcePolicy()
        ├─ Query: SELECT * FROM skill_metrics_raw WHERE created_at < 7d ago
        ├─ DELETE raw metrics (no longer needed)
        └─ Keep anonymized for 90 days total

T+90:00 [90 days]
        DataRetentionEnforcer.enforcePolicy()
        ├─ Archive anonymized metrics to cold storage (optional)
        ├─ DELETE anonymized metrics
        └─ Keep aggregated benchmarks forever
```

---

## Database Schema Integration

### Table Partitioning Strategy

All raw and anonymized tables are partitioned by date (daily):

```sql
-- Skill metrics (raw)
CREATE TABLE skill_metrics_raw (
    id BIGSERIAL,
    execution_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    -- ... other columns ...
) PARTITION BY RANGE (execution_timestamp);

CREATE TABLE skill_metrics_raw_2026_02_21
    PARTITION OF skill_metrics_raw
    FOR VALUES FROM ('2026-02-21') TO ('2026-02-22');

-- Partition creation automated via:
-- DataRetentionEnforcer.createNextPartition() [daily at 00:00 UTC]
```

**Benefits**:
- Fast deletion (entire partition dropped in 1ms)
- Query optimization (only relevant partition scanned)
- Parallel bulk operations
- No locking on active partitions

### Index Strategy

```sql
-- Skill metrics indexes
CREATE INDEX idx_skill_metrics_skill_id ON skill_metrics_raw(skill_id);
CREATE INDEX idx_skill_metrics_timestamp ON skill_metrics_raw(execution_timestamp);
CREATE INDEX idx_skill_metrics_org_id ON skill_metrics_raw(organization_id);

-- Case metrics indexes
CREATE INDEX idx_case_metrics_case_type ON case_metrics_raw(case_type);
CREATE INDEX idx_case_metrics_timestamp ON case_metrics_raw(case_started_at);
CREATE INDEX idx_case_metrics_org_id ON case_metrics_raw(organization_id);

-- Anonymized (for queries)
CREATE INDEX idx_skill_metrics_anon_skill ON skill_metrics_anonymized(skill_id);
CREATE INDEX idx_skill_metrics_anon_timestamp ON skill_metrics_anonymized(execution_timestamp);
```

---

## API Specification

### Benchmark Query Endpoints

**1. Get Skill Benchmark**

```
GET /api/v1/benchmarks/skills/{skillId}

Response:
{
  "skillId": "openai-gpt4-turbo",
  "skillName": "OpenAI GPT-4 Turbo",
  "nSamples": 12847,
  "p50DurationMs": 1234,
  "p95DurationMs": 2500,
  "p99DurationMs": 5000,
  "avgDurationMs": 1456.2,
  "successRate": 0.98,
  "queryTimeMs": 123
}
```

**2. Get Industry Percentiles**

```
GET /api/v1/benchmarks/skills/{skillId}/industry

Response:
{
  "skillId": "openai-gpt4-turbo",
  "top25Percent": {
    "minDurationMs": 500,
    "maxDurationMs": 800,
    "orgCount": 45,
    "percentile": "top_25%"
  },
  "median": {
    "minDurationMs": 1000,
    "maxDurationMs": 1500,
    "orgCount": 90,
    "percentile": "median_50%"
  },
  "bottom25Percent": {
    "minDurationMs": 2000,
    "maxDurationMs": 3500,
    "orgCount": 45,
    "percentile": "bottom_25%"
  }
}
```

**3. Compare Skills**

```
GET /api/v1/benchmarks/skills/compare?skills=skill1,skill2,skill3

Response:
{
  "comparisons": [
    {
      "skillId": "skill1",
      "avgDurationMs": 1200,
      "p95DurationMs": 2000,
      "successRate": 0.98,
      "nSamples": 5000,
      "rank": 1
    },
    {
      "skillId": "skill2",
      "avgDurationMs": 1500,
      "p95DurationMs": 2500,
      "successRate": 0.96,
      "nSamples": 4200,
      "rank": 2
    }
  ]
}
```

**4. Case Benchmarks**

```
GET /api/v1/benchmarks/cases/{caseType}

Response:
{
  "caseType": "order_processing",
  "nCases": 3456,
  "p50CycleTimeS": 300,
  "p95CycleTimeS": 600,
  "p99CycleTimeS": 1200,
  "avgCycleTimeS": 350.5,
  "avgErrorRate": 0.02,
  "queryTimeMs": 156
}
```

**5. Export Metrics**

```
GET /api/v1/benchmarks/export?skillId=skill1&daysBack=30

Response: (CSV file)
skill_id,avg_duration_ms,p50_duration_ms,p95_duration_ms,success_rate,n_samples,date
openai-gpt4-turbo,1456.2,1234,2500,0.98,12847,2026-02-21
openai-gpt4-turbo,1478.5,1256,2600,0.97,12900,2026-02-20
...
```

---

## Performance Targets

### Query Performance (SLA)

| Query | Target | Implementation |
|-------|--------|-----------------|
| Single skill benchmark | <500ms p95 | Redis cache (1h TTL) + SQL index |
| Industry percentiles | <500ms p95 | Materialized view + cache |
| Skill comparison (5 skills) | <1s p95 | Parallel queries + connection pool |
| Leaderboard (top 10) | <300ms p95 | Pre-computed view |
| Trend (7 days, hourly) | <1s p95 | Time-series rollups |

### Collection Performance

| Operation | Target | Implementation |
|-----------|--------|-----------------|
| Skill collection | <10ms latency, non-blocking | Async queue (virtual threads) |
| Case collection | <10ms latency, non-blocking | Async queue (virtual threads) |
| Concurrent collections | 100/sec | Connection pool size=20, queue unbounded |
| Data loss | <0.1% | Persistent queue before DB write |

---

## Security & Privacy

### Authentication

All API endpoints require bearer token (JWT):

```
Authorization: Bearer <jwt-token>

Token claims:
{
  "sub": "user-uuid",
  "org": "org-uuid",
  "scope": "marketplace:read"
}
```

### Authorization

- `marketplace:read` → Can query benchmarks and export data
- `marketplace:admin` → Can manage retention policies, view raw metrics (for ops)
- No authenticated user can see raw PII (even admins)

### Encryption

- Passwords: bcrypt (Spring Security default)
- Data in transit: HTTPS/TLS 1.3+ enforced
- Data at rest: PostgreSQL encrypted columns (for sensitive fields)
- Salt storage: Secure env var (`ANONYMIZATION_SALT`), rotated monthly

---

## Monitoring & Observability

### Metrics Exported (Micrometer)

```
# Collection metrics
marketplace.skill.collected (counter)
marketplace.skill.collection.error (counter)
marketplace.skill.collection.latency (timer)

marketplace.case.collected (counter)
marketplace.case.collection.error (counter)
marketplace.case.collection.latency (timer)

# Anonymization metrics
marketplace.anonymization.records_processed (counter)
marketplace.anonymization.duration (timer)
marketplace.anonymization.rules_applied (histogram)

# Query metrics
marketplace.query.benchmarks (counter)
marketplace.query.latency (timer)
marketplace.query.cache.hits (counter)
marketplace.query.cache.misses (counter)

# Data quality
marketplace.pii_detected (counter)  # Should always be 0
marketplace.data_loss (counter)     # Should be <1%
```

### Alerts (Recommended)

```yaml
- name: HighPiiDetectionRate
  condition: marketplace.pii_detected > 0 per hour
  severity: CRITICAL
  action: Page on-call engineer

- name: HighCollectionErrorRate
  condition: marketplace.*.collection.error > 5% per hour
  severity: WARNING
  action: Create ticket, monitor

- name: LowCacheHitRate
  condition: marketplace.query.cache.hits / (hits + misses) < 0.7
  severity: INFO
  action: Increase cache size or TTL

- name: SlowBenchmarkQueries
  condition: marketplace.query.latency p95 > 500ms
  severity: WARNING
  action: Check query plan, add indexes
```

---

## Testing Strategy

### Unit Tests (Per Component)

1. **AnonymizationPipelineTest**: Rule application, determinism, PII removal
2. **MetricsCollectorTest**: Event parsing, validation, error handling
3. **BenchmarkQueryServiceTest**: Percentile accuracy, cache behavior
4. **BenchmarkControllerTest**: API response format, error codes

### Integration Tests (Cross-Component)

1. **MetricsCollectionIT**: Raw metric → DB → anonymization
2. **BenchmarkAggregationIT**: Anonymized metrics → aggregates → accuracy
3. **BenchmarkControllerIT**: API → query service → database
4. **DataPrivacyIT**: PII removal verification, determinism

### E2E Test (Full Flow)

```java
@Test
void test_full_data_flow_10k_records() {
    // 1. Generate 10K skill metrics
    for (int i = 0; i < 10_000; i++) {
        metricsCollector.collectSkillMetrics(testEvent);
    }

    // 2. Wait for anonymization (batched)
    Thread.sleep(1000);

    // 3. Query benchmark (should be <500ms)
    long start = System.currentTimeMillis();
    SkillBenchmarkResponse resp = queryService.getSkillBenchmark("test-skill");
    long elapsed = System.currentTimeMillis() - start;

    assertThat(resp.nSamples()).isEqualTo(10_000);
    assertThat(elapsed).isLessThan(500);

    // 4. Verify PII removed
    List<String> raw = jdbc.queryForList(
        "SELECT * FROM skill_metrics_raw WHERE skill_id = 'test-skill' LIMIT 1",
        String.class);
    List<String> anon = jdbc.queryForList(
        "SELECT * FROM skill_metrics_anonymized WHERE skill_id = 'test-skill' LIMIT 1",
        String.class);

    assertThat(raw.toString()).contains("user_id");
    assertThat(anon.toString()).contains("user_id_hash");
    assertThat(anon.toString()).doesNotContain("user_id");
}
```

---

## Deployment Checklist

- [ ] PostgreSQL 15+ with TimescaleDB extension installed
- [ ] `yawl-marketplace` module built and tested
- [ ] Database migrations applied (V001, V002)
- [ ] `ANONYMIZATION_SALT` environment variable set (secure)
- [ ] Redis cache configured (optional but recommended)
- [ ] MCP/A2A hooks integrated and tested
- [ ] API endpoints exposed (with auth)
- [ ] Monitoring alerts configured
- [ ] Backup strategy for raw metrics (7-day retention)
- [ ] Disaster recovery plan documented

---

## Future Enhancements (Phase 2+)

1. **Federated Benchmarking**: Multiple YAWL instances share benchmarks via consensus
2. **ML Models**: Predict failure rates, anomaly detection, resource usage forecasting
3. **Skill Reputation**: Ranking skills by performance, cost, reliability
4. **Cost Attribution**: Link metrics to cloud service costs (LLM API calls, compute)
5. **Recommendations**: "Use Skill B instead of A—same features, 30% faster"
6. **Real-time Streaming**: Kafka events for live dashboards
7. **Industry Reports**: Monthly anonymized benchmarking reports
8. **Multi-tenancy**: Separate benchmark pools by industry/domain

---

## References

- `DATA-MARKETPLACE-MVP-DESIGN.md` — Complete 4-week implementation plan
- `config/metrics-schema.yaml` — Metric definitions and anonymization rules
- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` — MCP server code
- `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer` — A2A server code
- `PostgreSQL Partitioning Guide` — Table partition strategy
- `GDPR Compliance Checklist` — Data retention and right to deletion

