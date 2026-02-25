# Data Marketplace Integration Quick Start

**Time to integrate**: ~30 minutes
**Effort**: 2 engineers (1 to MCP, 1 to A2A)
**No breaking changes**: All changes backward compatible

---

## 1. Add Metrics Collector Hook to MCP Server

**File**: `yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`

**Change**: Wrap `executeTool()` to capture metrics.

```java
// Add this field
@Autowired
private MetricsCollector metricsCollector;  // NEW: injected by Spring

// Modify executeTool() method
public Object executeTool(String toolName, Map<String, Object> params) {
    long startNs = System.nanoTime();
    try {
        // Execute tool (existing code)
        Object result = toolRegistry.execute(toolName, params);

        // NEW: Record success metric
        metricsCollector.collectSkillMetrics(
            SkillMetricEvent.builder()
                .skillId(toolName)
                .skillName(toolDescription(toolName))
                .durationMs((int) (System.nanoTime() - startNs) / 1_000_000)
                .success(true)
                .userId(getCurrentUserId())
                .organizationId(getCurrentOrgId())
                .requestSource("mcp_client")
                .region(detectRegion())
                .build()
        );

        return result;

    } catch (Exception e) {
        // NEW: Record failure metric
        metricsCollector.collectSkillMetrics(
            SkillMetricEvent.builder()
                .skillId(toolName)
                .skillName(toolDescription(toolName))
                .durationMs((int) (System.nanoTime() - startNs) / 1_000_000)
                .success(false)
                .errorType(e.getClass().getSimpleName())
                .userId(getCurrentUserId())
                .organizationId(getCurrentOrgId())
                .requestSource("mcp_client")
                .region(detectRegion())
                .build()
        );

        throw e;
    }
}

// Helper methods
private String getCurrentUserId() {
    // Extract from JWT context (ScopedValue, SecurityContext, etc.)
    return JwtContextHolder.getSubject();
}

private String getCurrentOrgId() {
    return JwtContextHolder.getClaim("org");
}

private String detectRegion() {
    // From request header, config, or cloud metadata service
    return "us-west-2";  // Example
}

private String toolDescription(String toolName) {
    return toolRegistry.getTool(toolName).getDescription();
}
```

**Integration points** (using existing YAWL patterns):
- `JwtContextHolder.getSubject()` → User ID (from MCP auth token)
- `JwtContextHolder.getClaim("org")` → Org ID (from JWT custom claim)
- `toolRegistry.execute()` → Existing tool execution (no change)
- `System.nanoTime()` → Precise timing

**Testing**:

```java
@Test
void test_mcp_tool_metric_collected_on_success() {
    // Arrange
    MeterRegistry metrics = new SimpleMeterRegistry();
    MetricsCollector collector = new MetricsCollector(metrics);
    YawlMcpServer server = new YawlMcpServer(collector);

    // Act
    Object result = server.executeTool("openai-gpt4", Map.of("prompt", "test"));

    // Assert
    Counter counter = metrics.find("marketplace.skill.collected")
        .tags("skill_id", "openai-gpt4", "success", "true")
        .counter();

    assertThat(counter.count()).isEqualTo(1);
}
```

---

## 2. Add Metrics Collector Hook to A2A Server

**File**: `yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`

**Change**: Call metrics collector when case completes.

```java
// Add this field
@Autowired
private MetricsCollector metricsCollector;  // NEW

// Find where cases transition to completion
// Typically in: handleCaseCompletion(), onCaseFinished(), or similar

public void handleCaseCompletion(CaseCompletionMessage msg) {
    // Existing A2A logic...
    processCaseCompletion(msg);

    // NEW: Record case metric
    long cycleTimeSeconds = Duration.between(
        msg.getCaseCreatedAt(),
        Instant.now()
    ).toSeconds();

    metricsCollector.collectCaseMetrics(
        CaseMetricEvent.builder()
            .caseId(msg.getCaseId())
            .caseType(msg.getCaseType())
            .caseStartedAt(msg.getCaseCreatedAt())
            .caseCompletedAt(Instant.now())
            .cycleTimeSeconds((int) cycleTimeSeconds)
            .workItemsCompleted(msg.getCompletedTaskCount())
            .workItemsFailed(msg.getFailedTaskCount())
            .errorRate(calculateErrorRate(msg))
            .organizationId(getOrgIdFromContext())
            .assignedUser(msg.getLastAssignedUser())
            .priority(msg.getPriority())
            .build()
    );
}

private double calculateErrorRate(CaseCompletionMessage msg) {
    int total = msg.getCompletedTaskCount() + msg.getFailedTaskCount();
    return total > 0
        ? (double) msg.getFailedTaskCount() / total
        : 0.0;
}

private String getOrgIdFromContext() {
    return A2AContextHolder.getOrganizationId();
}
```

**Testing**:

```java
@Test
void test_a2a_case_metric_collected_on_completion() {
    // Arrange
    MetricsCollector collector = Mockito.mock(MetricsCollector.class);
    YawlA2AServer server = new YawlA2AServer(collector);

    CaseCompletionMessage msg = testData.createCaseCompletion()
        .withCaseId("ORD-123")
        .withCaseType("order_processing")
        .withCompletedTasks(5)
        .withFailedTasks(0);

    // Act
    server.handleCaseCompletion(msg);

    // Assert
    ArgumentCaptor<CaseMetricEvent> captor = ArgumentCaptor.forClass(CaseMetricEvent.class);
    verify(collector).collectCaseMetrics(captor.capture());

    CaseMetricEvent event = captor.getValue();
    assertThat(event.caseId()).isEqualTo("ORD-123");
    assertThat(event.workItemsCompleted()).isEqualTo(5);
    assertThat(event.workItemsFailed()).isEqualTo(0);
}
```

---

## 3. Create MetricsCollector Spring Component

**File**: `yawl-marketplace/src/main/java/org/yawlfoundation/yawl/integration/marketplace/collection/MetricsCollectorImpl.java`

```java
@Service
public class MetricsCollectorImpl implements MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectorImpl.class);

    @Autowired
    private SkillMetricsRepository skillRepo;

    @Autowired
    private CaseMetricsRepository caseRepo;

    @Autowired
    private ExecutorService asyncExecutor;

    @Autowired
    private MeterRegistry metrics;

    /**
     * Collect skill (MCP tool) execution metric.
     * Fire-and-forget: returns immediately, processing async.
     */
    @Override
    public void collectSkillMetrics(SkillMetricEvent event) {
        // Async processing to avoid blocking MCP response
        asyncExecutor.submit(() -> {
            try {
                SkillMetricRaw raw = SkillMetricRaw.fromEvent(event);
                skillRepo.saveRaw(raw);

                metrics.counter(
                    "marketplace.skill.collected",
                    "skill_id", event.skillId(),
                    "success", String.valueOf(event.success())
                ).increment();

                logger.debug("Skill metric collected: {}", event.skillId());

            } catch (Exception e) {
                metrics.counter("marketplace.skill.collection.error").increment();
                logger.error("Failed to collect skill metric", e);
                // Don't throw: collection failures shouldn't impact MCP
            }
        });
    }

    /**
     * Collect case execution metric.
     * Fire-and-forget: returns immediately, processing async.
     */
    @Override
    public void collectCaseMetrics(CaseMetricEvent event) {
        asyncExecutor.submit(() -> {
            try {
                CaseMetricRaw raw = CaseMetricRaw.fromEvent(event);
                caseRepo.saveRaw(raw);

                metrics.counter(
                    "marketplace.case.collected",
                    "case_type", event.caseType()
                ).increment();

                logger.debug("Case metric collected: {}", event.caseType());

            } catch (Exception e) {
                metrics.counter("marketplace.case.collection.error").increment();
                logger.error("Failed to collect case metric", e);
            }
        });
    }
}
```

**Spring Configuration**:

```java
@Configuration
public class MarketplaceConfiguration {

    @Bean
    public ExecutorService asyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public MetricsCollector metricsCollector() {
        return new MetricsCollectorImpl();
    }

    // Schedule anonymization every 6 hours
    @Bean
    public AnonymizationScheduler anonymizationScheduler(
        AnonymizationPipeline pipeline,
        SkillMetricsRepository skillRepo
    ) {
        return new AnonymizationScheduler(pipeline, skillRepo);
    }
}
```

---

## 4. Database Setup (Flyway Migration)

**File**: `yawl-marketplace/src/db/migration/V001__init_marketplace_schema.sql`

```sql
-- Create skill metrics tables
CREATE TABLE skill_metrics_raw (
    id BIGSERIAL PRIMARY KEY,
    skill_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    execution_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),
    input_tokens INTEGER,
    output_tokens INTEGER,
    memory_used_mb FLOAT,
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    request_source VARCHAR(50) NOT NULL,
    region VARCHAR(50),
    anonymized BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    anonymized_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_org_id CHECK (organization_id ~ '^[a-z0-9-]{36}$'),
    CONSTRAINT fk_user_id CHECK (user_id ~ '^[a-z0-9-]{36}$')
);

CREATE TABLE skill_metrics_anonymized (
    id BIGSERIAL PRIMARY KEY,
    raw_id BIGINT NOT NULL UNIQUE REFERENCES skill_metrics_raw(id) ON DELETE CASCADE,
    skill_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    user_id_hash VARCHAR(64) NOT NULL,
    organization_id_hash VARCHAR(64) NOT NULL,
    execution_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),
    input_tokens INTEGER,
    output_tokens INTEGER,
    memory_used_mb FLOAT,
    request_source VARCHAR(50) NOT NULL,
    region VARCHAR(50),
    anonymized_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_skill_metrics_skill_id ON skill_metrics_raw(skill_id);
CREATE INDEX idx_skill_metrics_timestamp ON skill_metrics_raw(execution_timestamp DESC);
CREATE INDEX idx_skill_metrics_anon_skill ON skill_metrics_anonymized(skill_id);

-- Case metrics tables
CREATE TABLE case_metrics_raw (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(255) NOT NULL,
    case_type VARCHAR(255) NOT NULL,
    case_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    case_completed_at TIMESTAMP WITH TIME ZONE,
    cycle_time_seconds INTEGER,
    work_items_completed INTEGER DEFAULT 0,
    work_items_failed INTEGER DEFAULT 0,
    error_rate FLOAT DEFAULT 0.0,
    organization_id VARCHAR(255) NOT NULL,
    assigned_user VARCHAR(255),
    anonymized BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    anonymized_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE case_metrics_anonymized (
    id BIGSERIAL PRIMARY KEY,
    raw_id BIGINT NOT NULL UNIQUE REFERENCES case_metrics_raw(id) ON DELETE CASCADE,
    case_id_hash VARCHAR(64) NOT NULL,
    case_type VARCHAR(255) NOT NULL,
    organization_id_hash VARCHAR(64) NOT NULL,
    assigned_user_hash VARCHAR(64),
    case_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    case_completed_at TIMESTAMP WITH TIME ZONE,
    cycle_time_seconds INTEGER,
    work_items_completed INTEGER,
    work_items_failed INTEGER,
    error_rate FLOAT,
    anonymized_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_case_metrics_case_type ON case_metrics_raw(case_type);
CREATE INDEX idx_case_metrics_anon_type ON case_metrics_anonymized(case_type);
```

---

## 5. Query API Controller

**File**: `yawl-marketplace/src/main/java/org/yawlfoundation/yawl/integration/marketplace/api/BenchmarkController.java`

```java
@RestController
@RequestMapping("/api/v1/benchmarks")
public class BenchmarkController {

    @Autowired
    private BenchmarkQueryService queryService;

    @GetMapping("/skills/{skillId}")
    public ResponseEntity<SkillBenchmarkResponse> getSkillBenchmark(
        @PathVariable String skillId) {

        try {
            SkillBenchmarkResponse response = queryService.getSkillBenchmark(skillId);
            return ResponseEntity.ok(response);
        } catch (DataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/cases/{caseType}")
    public ResponseEntity<CaseBenchmarkResponse> getCaseBenchmark(
        @PathVariable String caseType) {

        CaseBenchmarkResponse response = queryService.getCaseBenchmark(caseType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportMetrics(
        @RequestParam(required = false) String skillId,
        @RequestParam(required = false) String caseType,
        @RequestParam(defaultValue = "30") int daysBack) {

        byte[] csvData = queryService.exportMetrics(skillId, caseType, daysBack);

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=benchmarks.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new ByteArrayResource(csvData));
    }
}
```

---

## 6. Verification Checklist

After integration, verify:

```bash
# 1. Check metrics are collected
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/benchmarks/skills/openai-gpt4

# Expected response:
# {
#   "skillId": "openai-gpt4",
#   "nSamples": 100+,
#   "avgDurationMs": 1234.5,
#   ...
# }

# 2. Check database has raw metrics
psql -U yawl -d yawl -c \
  "SELECT COUNT(*) FROM skill_metrics_raw WHERE skill_id = 'openai-gpt4';"

# Expected: >0 rows

# 3. Check anonymization ran
psql -U yawl -d yawl -c \
  "SELECT COUNT(*) FROM skill_metrics_anonymized WHERE skill_id = 'openai-gpt4';"

# Expected: >0 rows (after 6 hours, or manual trigger)

# 4. Verify no PII in anonymized tables
psql -U yawl -d yawl -c \
  "SELECT COUNT(*) FROM skill_metrics_anonymized WHERE user_id_hash ~ '^[a-z0-9-]{36}$';"

# Expected: 0 (no raw UUIDs, only hashes)

# 5. Check latency
time curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/benchmarks/skills/openai-gpt4

# Expected: <500ms
```

---

## 7. Common Issues & Fixes

| Issue | Root Cause | Fix |
|-------|-----------|-----|
| **Metrics not collected** | `MetricsCollector` not autowired | Add `@Autowired` field in MCP/A2A server |
| **PII visible in DB** | Anonymization not running | Check scheduler is enabled; manually run `AnonymizationScheduler.run()` |
| **Query returns 404** | No metrics in DB yet | Wait 6+ hours for anonymization, or insert test data |
| **High query latency** | Cache disabled or indexes missing | Run `ANALYZE` on tables; check cache TTL |
| **Data loss** | Async queue lost events on restart | Enable persistent queue (PostgreSQL LISTEN/NOTIFY) |

---

## 8. Monitoring

Add these metrics to your observability stack:

```yaml
# Prometheus scrape config
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: yawl-marketplace
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

Key metrics to monitor:

```
# Alert if collection errors exceed 1%
marketplace_skill_collection_error_total / marketplace_skill_collected_total > 0.01

# Alert if query latency exceeds SLA
histogram_quantile(0.95, marketplace_query_latency_seconds) > 0.5

# Alert if PII detected (should be 0)
marketplace_pii_detected_total > 0
```

---

## Next Steps

1. **Week 1**: Deploy MCP hook + A2A hook (30 min each, parallel)
2. **Week 1-2**: Collect 10K+ metrics (runs passively)
3. **Week 2**: Deploy anonymization pipeline + database schema
4. **Week 3**: Deploy benchmark query API + caching
5. **Week 4**: Full testing + documentation

---

## Support

For questions or issues:
- See `DATA-MARKETPLACE-MVP-DESIGN.md` for full design
- See `DATA-MARKETPLACE-ARCHITECTURE.md` for detailed architecture
- See `config/metrics-schema.yaml` for metric definitions
