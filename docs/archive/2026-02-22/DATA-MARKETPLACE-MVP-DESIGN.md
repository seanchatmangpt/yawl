# Data Marketplace MVP (4-Week Implementation Plan)

**Version**: 1.0
**Date**: 2026-02-21
**Effort**: 4 weeks, 2 engineers, ~$50K cost
**Status**: Design Phase

---

## Executive Summary

Design a privacy-first Data Marketplace MVP that collects anonymized execution metrics from skills (MCP tools) and cases (workflow executions), providing benchmarking and comparison data without exposing personally identifiable information (PII).

**Core insight**: Skills generate execution data. Anonymize from ingestion. Aggregate into benchmarks. Enable strategic decisions ("Is my skill faster than peers? How does my case throughput compare?") without leaking proprietary data.

**Key success criteria**:
- 10K+ anonymized skill executions ingested in Week 4
- 100% PII removal (verified in tests)
- Benchmarks queryable in <500ms
- Industry median comparisons (top 25%, median, bottom 25%)
- Zero central authority (federated-ready in Phase 2)

---

## 1. Architecture Overview

### 1.1 System Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ YAWL Skill Execution & Case Processing                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────┐          ┌──────────────────┐         │
│  │ MCP Tool Call   │          │ A2A Message      │         │
│  │ (skill exec)    │          │ (case exec)      │         │
│  └────────┬────────┘          └────────┬─────────┘         │
│           │                           │                     │
│           └───────────┬───────────────┘                     │
│                       │                                     │
│           ┌───────────▼──────────┐                          │
│           │ MetricsCollector     │                          │
│           │ (hook-based)         │                          │
│           │ • skill_execution    │                          │
│           │ • case_execution     │                          │
│           └───────────┬──────────┘                          │
│                       │                                     │
│           ┌───────────▼──────────────────┐                  │
│           │ AnonymizationPipeline        │                  │
│           │ • remove PII (names, emails) │                  │
│           │ • hash org IDs               │                  │
│           │ • anonymize user names       │                  │
│           │ • mask IP addresses          │                  │
│           └───────────┬──────────────────┘                  │
│                       │                                     │
│           ┌───────────▼──────────────────────┐              │
│           │ Metrics Storage (PostgreSQL+TSDb) │             │
│           │ Tables:                          │             │
│           │ • skill_metrics_raw              │             │
│           │ • case_metrics_raw               │             │
│           │ • anonymization_metadata         │             │
│           └───────────┬──────────────────────┘              │
│                       │                                     │
│           ┌───────────▼──────────────────────┐              │
│           │ BenchmarkAggregator (SQL Views)  │              │
│           │ • hourly aggregates              │              │
│           │ • percentile calculations        │              │
│           │ • industry median rollups        │              │
│           └───────────┬──────────────────────┘              │
│                       │                                     │
│           ┌───────────▼──────────────────────┐              │
│           │ BenchmarkQueryAPI (REST)         │              │
│           │ GET /api/v1/benchmarks/skills    │              │
│           │ GET /api/v1/benchmarks/cases     │              │
│           │ GET /api/v1/industry/percentiles │              │
│           │ POST /api/v1/metrics/export      │              │
│           └───────────────────────────────────┘              │
│                       │                                     │
│           ┌───────────▼──────────────────────┐              │
│           │ Dashboard (Read-Only)            │              │
│           │ • Skill benchmarks               │              │
│           │ • Case performance               │              │
│           │ • Industry comparisons           │              │
│           │ • Trend analysis                 │              │
│           └────────────────────────────────────┘             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Data Flow (4 Stages)

```
Stage 1: Collection
  Skill execution → MetricsCollector → Raw event (skill_name, duration, success, resources)
  Case execution  → MetricsCollector → Raw event (case_type, throughput, cycle_time, errors)

Stage 2: Anonymization
  Raw event + [user_name, org_id, email, ip_address, ...]
    ↓
  Anonymization rules (regex, hashing, masking)
    ↓
  Anonymized event (user_hash, org_hash, anon_ip) + metadata (rule_applied, timestamp)

Stage 3: Aggregation
  Anonymized events (10K+)
    ↓
  BenchmarkAggregator (SQL views)
    ↓
  Hourly aggregates:
    • skill_type → avg_duration, p50, p95, p99, success_rate
    • case_type → throughput, cycle_time, error_rate

Stage 4: Query
  Client request: "Benchmarks for 'code_review' skill"
    ↓
  BenchmarkQueryAPI
    ↓
  SQL views (aggregated data only, no raw records exposed)
    ↓
  JSON response:
    {
      "skill": "code_review",
      "avg_duration_ms": 1245,
      "p50_duration_ms": 1000,
      "p95_duration_ms": 2500,
      "p99_duration_ms": 5000,
      "success_rate": 0.98,
      "n_samples": 1247,
      "industry_percentile": "top_25%"
    }
```

---

## 2. Core Components

### 2.1 Metrics Schema (YAML-Backed, Immutable)

**File**: `config/metrics-schema.yaml`

```yaml
---
schema_version: "1.0"
date_published: "2026-02-21"

# ========== SKILL EXECUTION METRICS ==========
skill_execution_metric:
  description: "Execution metrics for a single MCP tool invocation"
  fields:
    skill_id:
      type: "string"
      description: "Unique skill identifier (e.g., 'openai-gpt4')"
      anonymized: false
      pii_risk: "low"

    skill_name:
      type: "string"
      description: "Human-readable skill name"
      anonymized: false
      pii_risk: "low"

    execution_timestamp:
      type: "timestamp"
      description: "Execution start time (UTC, microsecond precision)"
      anonymized: false
      pii_risk: "none"

    duration_ms:
      type: "integer"
      description: "Execution duration in milliseconds"
      min: 0
      max: 3600000
      anonymized: false
      pii_risk: "none"

    success:
      type: "boolean"
      description: "Did execution succeed?"
      anonymized: false
      pii_risk: "none"

    error_type:
      type: "string"
      description: "Exception class if failed (e.g., TimeoutException)"
      nullable: true
      anonymized: false
      pii_risk: "low"

    input_tokens:
      type: "integer"
      description: "Tokens consumed (if LLM-based)"
      nullable: true
      min: 0
      anonymized: false
      pii_risk: "none"

    output_tokens:
      type: "integer"
      description: "Tokens produced (if LLM-based)"
      nullable: true
      min: 0
      anonymized: false
      pii_risk: "none"

    memory_used_mb:
      type: "float"
      description: "Peak memory used during execution"
      nullable: true
      min: 0
      anonymized: false
      pii_risk: "none"

    user_id:
      type: "string"
      description: "User who triggered execution (ANONYMIZE ME)"
      anonymized: true
      pii_risk: "high"
      anonymization: "sha256_hash"

    organization_id:
      type: "string"
      description: "Organization/tenant context (ANONYMIZE ME)"
      anonymized: true
      pii_risk: "high"
      anonymization: "deterministic_hash"

    request_source:
      type: "string"
      description: "Source of request: 'mcp_client', 'a2a_agent', 'direct_api'"
      enum: ["mcp_client", "a2a_agent", "direct_api"]
      anonymized: false
      pii_risk: "low"

    region:
      type: "string"
      description: "Geographic region (e.g., 'us-west-2')"
      anonymized: false
      pii_risk: "none"

# ========== CASE EXECUTION METRICS ==========
case_execution_metric:
  description: "Execution metrics for a single YAWL case"
  fields:
    case_id:
      type: "string"
      description: "Unique case identifier (ANONYMIZE ME)"
      anonymized: true
      pii_risk: "high"
      anonymization: "sha256_hash"

    case_type:
      type: "string"
      description: "Workflow type (e.g., 'order_processing', 'approval')"
      anonymized: false
      pii_risk: "low"

    case_started_at:
      type: "timestamp"
      description: "Case creation time"
      anonymized: false
      pii_risk: "none"

    case_completed_at:
      type: "timestamp"
      description: "Case completion time (nullable if still running)"
      nullable: true
      anonymized: false
      pii_risk: "none"

    cycle_time_seconds:
      type: "integer"
      description: "Total case duration (completed_at - started_at)"
      nullable: true
      min: 0
      anonymized: false
      pii_risk: "none"

    work_items_completed:
      type: "integer"
      description: "Number of work items executed"
      min: 0
      anonymized: false
      pii_risk: "none"

    work_items_failed:
      type: "integer"
      description: "Number of work items that failed"
      min: 0
      anonymized: false
      pii_risk: "none"

    error_rate:
      type: "float"
      description: "Fraction of work items that failed (0.0 to 1.0)"
      min: 0.0
      max: 1.0
      anonymized: false
      pii_risk: "none"

    throughput_cases_per_hour:
      type: "float"
      description: "Cases completed per hour (aggregate metric)"
      min: 0.0
      anonymized: false
      pii_risk: "none"

    organization_id:
      type: "string"
      description: "Organization/tenant context (ANONYMIZE ME)"
      anonymized: true
      pii_risk: "high"
      anonymization: "deterministic_hash"

    assigned_user:
      type: "string"
      description: "User assigned work item (ANONYMIZE ME)"
      nullable: true
      anonymized: true
      pii_risk: "high"
      anonymization: "sha256_hash"

# ========== ANONYMIZATION RULES ==========
anonymization_rules:
  - rule_name: "hash_user_id"
    pattern: "^[a-z0-9-]{16,}$"  # User UUIDs
    field_name: "user_id"
    algorithm: "sha256"
    salt: "${ANONYMIZATION_SALT}"
    description: "Hash user IDs to break linkability"

  - rule_name: "hash_organization_id"
    pattern: "^[a-z0-9-]{16,}$"  # Org UUIDs
    field_name: "organization_id"
    algorithm: "sha256"
    salt: "${ANONYMIZATION_SALT}"
    deterministic: true
    description: "Deterministic hash of org IDs (same org → same hash)"

  - rule_name: "hash_case_id"
    pattern: "^[A-Z0-9-]{20,}$"  # Case IDs
    field_name: "case_id"
    algorithm: "sha256"
    salt: "${ANONYMIZATION_SALT}"
    description: "Hash case IDs to prevent case linking"

  - rule_name: "hash_assigned_user"
    pattern: "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    field_name: "assigned_user"
    algorithm: "sha256"
    salt: "${ANONYMIZATION_SALT}"
    description: "Hash email-like assigned_user fields"

  - rule_name: "mask_ip_address"
    pattern: "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$"
    field_name: "request_ip"
    algorithm: "mask_last_octet"
    description: "Mask last octet of IP address"

# ========== METRICS RETENTION POLICY ==========
retention_policy:
  raw_metrics:
    duration_days: 7
    reason: "Anonymization audit trail"

  anonymized_metrics:
    duration_days: 90
    reason: "Benchmark aggregation and trend analysis"

  aggregated_metrics:
    duration_days: 730  # 2 years
    reason: "Historical benchmarks and industry trends"
```

### 2.2 Database Schema (PostgreSQL + TimescaleDB)

**Tables** (Version 1.0):

```sql
-- ========== SKILL METRICS TABLE ==========
CREATE TABLE IF NOT EXISTS skill_metrics_raw (
    id BIGSERIAL PRIMARY KEY,

    -- Identifiers
    skill_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,

    -- Timing
    execution_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),

    -- Status
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),

    -- Resources
    input_tokens INTEGER,
    output_tokens INTEGER,
    memory_used_mb FLOAT,

    -- Context (PRE-ANONYMIZATION)
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    request_source VARCHAR(50) NOT NULL,
    region VARCHAR(50),

    -- Anonymization metadata
    anonymized BOOLEAN DEFAULT FALSE,
    anonymization_timestamp TIMESTAMP WITH TIME ZONE,
    anonymization_rules_applied TEXT[],

    -- Indexes for fast queries
    CONSTRAINT fk_org_id CHECK (organization_id ~ '^[a-z0-9-]{16,}$'),
    CONSTRAINT fk_user_id CHECK (user_id ~ '^[a-z0-9-]{16,}$')
) PARTITION BY RANGE (execution_timestamp);

-- Create partitions (daily)
CREATE TABLE skill_metrics_raw_2026_02_21 PARTITION OF skill_metrics_raw
    FOR VALUES FROM ('2026-02-21') TO ('2026-02-22');

-- Indexes
CREATE INDEX idx_skill_metrics_skill_id ON skill_metrics_raw(skill_id);
CREATE INDEX idx_skill_metrics_timestamp ON skill_metrics_raw(execution_timestamp);
CREATE INDEX idx_skill_metrics_org_id ON skill_metrics_raw(organization_id);
CREATE INDEX idx_skill_metrics_user_id ON skill_metrics_raw(user_id);

-- ========== ANONYMIZED SKILL METRICS TABLE ==========
CREATE TABLE IF NOT EXISTS skill_metrics_anonymized (
    id BIGSERIAL PRIMARY KEY,
    raw_id BIGINT NOT NULL UNIQUE,

    -- Anonymized identifiers
    skill_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    user_id_hash VARCHAR(64) NOT NULL,
    organization_id_hash VARCHAR(64) NOT NULL,

    -- Metrics (unchanged from raw)
    execution_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),
    input_tokens INTEGER,
    output_tokens INTEGER,
    memory_used_mb FLOAT,

    -- Context
    request_source VARCHAR(50) NOT NULL,
    region VARCHAR(50),

    -- Anonymization audit
    anonymization_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    anonymization_rules_applied TEXT[] NOT NULL,

    FOREIGN KEY (raw_id) REFERENCES skill_metrics_raw(id) ON DELETE CASCADE
) PARTITION BY RANGE (execution_timestamp);

CREATE TABLE skill_metrics_anonymized_2026_02_21 PARTITION OF skill_metrics_anonymized
    FOR VALUES FROM ('2026-02-21') TO ('2026-02-22');

CREATE INDEX idx_skill_metrics_anon_skill ON skill_metrics_anonymized(skill_id);
CREATE INDEX idx_skill_metrics_anon_timestamp ON skill_metrics_anonymized(execution_timestamp);
CREATE INDEX idx_skill_metrics_anon_org_hash ON skill_metrics_anonymized(organization_id_hash);

-- ========== CASE METRICS TABLE ==========
CREATE TABLE IF NOT EXISTS case_metrics_raw (
    id BIGSERIAL PRIMARY KEY,

    -- Identifiers
    case_id VARCHAR(255) NOT NULL,
    case_type VARCHAR(255) NOT NULL,

    -- Timing
    case_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    case_completed_at TIMESTAMP WITH TIME ZONE,
    cycle_time_seconds INTEGER,

    -- Execution stats
    work_items_completed INTEGER DEFAULT 0,
    work_items_failed INTEGER DEFAULT 0,
    error_rate FLOAT DEFAULT 0.0,
    throughput_cases_per_hour FLOAT,

    -- Context (PRE-ANONYMIZATION)
    organization_id VARCHAR(255) NOT NULL,
    assigned_user VARCHAR(255),

    -- Anonymization metadata
    anonymized BOOLEAN DEFAULT FALSE,
    anonymization_timestamp TIMESTAMP WITH TIME ZONE,
    anonymization_rules_applied TEXT[],

    CONSTRAINT fk_case_id CHECK (case_id ~ '^[A-Z0-9-]{20,}$'),
    CONSTRAINT fk_error_rate CHECK (error_rate >= 0.0 AND error_rate <= 1.0)
) PARTITION BY RANGE (case_started_at);

CREATE TABLE case_metrics_raw_2026_02_21 PARTITION OF case_metrics_raw
    FOR VALUES FROM ('2026-02-21') TO ('2026-02-22');

CREATE INDEX idx_case_metrics_case_type ON case_metrics_raw(case_type);
CREATE INDEX idx_case_metrics_started_at ON case_metrics_raw(case_started_at);
CREATE INDEX idx_case_metrics_org_id ON case_metrics_raw(organization_id);

-- ========== ANONYMIZED CASE METRICS TABLE ==========
CREATE TABLE IF NOT EXISTS case_metrics_anonymized (
    id BIGSERIAL PRIMARY KEY,
    raw_id BIGINT NOT NULL UNIQUE,

    -- Anonymized identifiers
    case_id_hash VARCHAR(64) NOT NULL,
    case_type VARCHAR(255) NOT NULL,
    organization_id_hash VARCHAR(64) NOT NULL,
    assigned_user_hash VARCHAR(64),

    -- Metrics (unchanged from raw)
    case_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    case_completed_at TIMESTAMP WITH TIME ZONE,
    cycle_time_seconds INTEGER,
    work_items_completed INTEGER,
    work_items_failed INTEGER,
    error_rate FLOAT,
    throughput_cases_per_hour FLOAT,

    -- Anonymization audit
    anonymization_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    anonymization_rules_applied TEXT[] NOT NULL,

    FOREIGN KEY (raw_id) REFERENCES case_metrics_raw(id) ON DELETE CASCADE
) PARTITION BY RANGE (case_started_at);

CREATE TABLE case_metrics_anonymized_2026_02_21 PARTITION OF case_metrics_anonymized
    FOR VALUES FROM ('2026-02-21') TO ('2026-02-22');

CREATE INDEX idx_case_metrics_anon_type ON case_metrics_anonymized(case_type);
CREATE INDEX idx_case_metrics_anon_started_at ON case_metrics_anonymized(case_started_at);
CREATE INDEX idx_case_metrics_anon_org_hash ON case_metrics_anonymized(organization_id_hash);

-- ========== BENCHMARK AGGREGATES (VIEWS) ==========
-- Aggregated hourly metrics (queryable)
CREATE VIEW skill_benchmarks_hourly AS
SELECT
    skill_id,
    skill_name,
    DATE_TRUNC('hour', execution_timestamp) AS hour,
    COUNT(*) AS n_samples,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_duration_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_duration_ms,
    AVG(duration_ms) AS avg_duration_ms,
    SUM(CASE WHEN success THEN 1 ELSE 0 END)::FLOAT / COUNT(*) AS success_rate,
    region
FROM skill_metrics_anonymized
WHERE execution_timestamp >= NOW() - INTERVAL '90 days'
GROUP BY skill_id, skill_name, DATE_TRUNC('hour', execution_timestamp), region;

-- Industry percentile calculation
CREATE VIEW skill_benchmarks_industry AS
SELECT
    skill_id,
    skill_name,
    COUNT(*) AS n_samples,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_duration_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_duration_ms,
    AVG(duration_ms) AS avg_duration_ms,
    SUM(CASE WHEN success THEN 1 ELSE 0 END)::FLOAT / COUNT(*) AS success_rate,
    NTILE(4) OVER (ORDER BY AVG(duration_ms)) AS quartile  -- Top 25%, median, bottom 25%
FROM skill_metrics_anonymized
WHERE execution_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY skill_id, skill_name;

-- Case benchmarks
CREATE VIEW case_benchmarks_hourly AS
SELECT
    case_type,
    DATE_TRUNC('hour', case_started_at) AS hour,
    COUNT(*) AS n_cases,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p50_cycle_time_s,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p95_cycle_time_s,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p99_cycle_time_s,
    AVG(cycle_time_seconds) AS avg_cycle_time_s,
    AVG(error_rate) AS avg_error_rate,
    AVG(throughput_cases_per_hour) AS avg_throughput
FROM case_metrics_anonymized
WHERE case_started_at >= NOW() - INTERVAL '90 days'
  AND case_completed_at IS NOT NULL
GROUP BY case_type, DATE_TRUNC('hour', case_started_at);
```

---

## 3. Implementation Components (Week-by-Week Breakdown)

### Week 1: Metrics Schema + Anonymization Rules

**Deliverables**:
1. `metrics-schema.yaml` (config file, immutable)
2. `AnonymizationPipeline` (interface + rules engine)
3. `AnonymizationRule` (sealed class for different rule types)
4. Tests (rule validation, deterministic hashing)

**Key files**:
- `src/org/yawlfoundation/yawl/integration/marketplace/config/metrics-schema.yaml`
- `src/org/yawlfoundation/yawl/integration/marketplace/anonymization/AnonymizationPipeline.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/anonymization/AnonymizationRule.java`
- `src/test/org/yawlfoundation/yawl/integration/marketplace/AnonymizationPipelineTest.java`

**Code outline**:

```java
// AnonymizationRule.java (sealed class)
public sealed class AnonymizationRule {

    sealed interface Rule permits HashRule, MaskRule, RemoveRule {}

    record HashRule(
        String ruleName,
        String fieldName,
        String algorithm,  // "sha256" | "md5" | "blake2b"
        String salt,
        boolean deterministic
    ) implements Rule {}

    record MaskRule(
        String ruleName,
        String fieldName,
        String pattern,
        String maskType    // "last_octet" | "all_digits"
    ) implements Rule {}

    record RemoveRule(
        String ruleName,
        String fieldName
    ) implements Rule {}
}

// AnonymizationPipeline.java
@Service
public class AnonymizationPipeline {

    private final SkillMetricsRepository skillRepo;
    private final CaseMetricsRepository caseRepo;
    private final AnonymizationRuleLoader ruleLoader;

    /**
     * Process raw skill metrics and apply anonymization rules.
     */
    public void anonymizeSkillMetrics(SkillMetricRaw raw)
        throws AnonymizationException {

        List<AnonymizationRule> rules = ruleLoader.load();

        for (var rule : rules) {
            switch (rule) {
                case HashRule hr when hr.fieldName().equals("user_id") -> {
                    String hashed = hashValue(raw.userId(), hr.salt(), hr.deterministic());
                    raw.setUserIdHash(hashed);
                }
                case MaskRule mr -> {
                    String masked = maskValue(raw.getField(mr.fieldName()), mr.maskType());
                    raw.setField(mr.fieldName() + "_masked", masked);
                }
                case RemoveRule rr -> {
                    raw.removeField(rr.fieldName());
                }
                default -> {}
            }
        }

        // Save anonymized record
        skillRepo.saveAnonymized(raw);
    }

    private String hashValue(String value, String salt, boolean deterministic) {
        if (deterministic) {
            // Same input → Same output (for org grouping)
            return sha256(value + salt);
        } else {
            // Same input → Different output (breaks linkability)
            return sha256(value + salt + UUID.randomUUID());
        }
    }
}
```

**Tests** (TDD/Chicago style):
```java
@Test
void test_anonymize_user_id_removes_linkability() {
    SkillMetricRaw raw = testData.createRawMetric()
        .withUserId("user-12345");

    AnonymizationPipeline pipeline = new AnonymizationPipeline();
    SkillMetricAnonymized anon = pipeline.anonymizeSkillMetrics(raw);

    // User IDs should be hashed, not equal to original
    assertNotEquals(raw.userId(), anon.userIdHash());

    // Hash should be deterministic within session (for same rule set)
    SkillMetricRaw raw2 = testData.createRawMetric()
        .withUserId("user-12345");
    SkillMetricAnonymized anon2 = pipeline.anonymizeSkillMetrics(raw2);

    assertEquals(anon.userIdHash(), anon2.userIdHash());
}

@Test
void test_anonymize_org_id_preserves_determinism() {
    SkillMetricRaw raw1 = testData.createRawMetric()
        .withOrgId("org-99999");
    SkillMetricRaw raw2 = testData.createRawMetric()
        .withOrgId("org-99999");

    // Same org → Same hash (for benchmarking by org)
    SkillMetricAnonymized anon1 = pipeline.anonymizeSkillMetrics(raw1);
    SkillMetricAnonymized anon2 = pipeline.anonymizeSkillMetrics(raw2);

    assertEquals(anon1.organizationIdHash(), anon2.organizationIdHash());
}

@Test
void test_pii_removal_verified() {
    SkillMetricRaw raw = testData.createRawMetric()
        .withUserEmail("alice@company.com")
        .withPhoneNumber("555-1234");

    SkillMetricAnonymized anon = pipeline.anonymizeSkillMetrics(raw);

    // PII fields should not exist or be hashed
    assertNull(anon.userEmail());
    assertNull(anon.phoneNumber());
}
```

**Success criteria**:
- 100% of PII rules applied (verified by test scanning)
- Deterministic hashing for org grouping
- Non-deterministic hashing for user linking prevention
- <1% false positives in rule detection

---

### Week 2: Collection Pipeline + Hook Integration

**Deliverables**:
1. `MetricsCollector` interface + implementations
2. A2A hook integration (post-task completion)
3. MCP hook integration (post-tool invocation)
4. Database migration (create tables from schema)
5. Integration tests (real data flow)

**Key files**:
- `src/org/yawlfoundation/yawl/integration/marketplace/collection/MetricsCollector.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/collection/SkillMetricsCollector.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/collection/CaseMetricsCollector.java`
- `src/db/marketplace/V001__init_marketplace_schema.sql`
- `src/test/org/yawlfoundation/yawl/integration/marketplace/MetricsCollectionIT.java`

**Code outline**:

```java
// MetricsCollector.java (interface)
public interface MetricsCollector {

    void collectSkillMetrics(SkillMetricEvent event)
        throws MetricsCollectionException;

    void collectCaseMetrics(CaseMetricEvent event)
        throws MetricsCollectionException;
}

// SkillMetricsCollector.java
@Service
public class SkillMetricsCollector implements MetricsCollector {

    private final SkillMetricsRepository skillRepo;
    private final AnonymizationPipeline anonPipeline;
    private final MeterRegistry metrics;

    @Override
    public void collectSkillMetrics(SkillMetricEvent event) {
        try {
            // Create raw record
            SkillMetricRaw raw = SkillMetricRaw.fromEvent(event);
            skillRepo.saveRaw(raw);

            // Enqueue for async anonymization
            asyncAnonymizer.queue(raw.id());

            // Record metric
            metrics.counter("marketplace.skill.collected").increment();

        } catch (Exception e) {
            metrics.counter("marketplace.skill.collection.error").increment();
            throw new MetricsCollectionException("Failed to collect skill metrics", e);
        }
    }
}

// A2A hook integration
@Component
public class A2AMetricsHook {

    @Autowired
    private MetricsCollector metricsCollector;

    /**
     * Called post-task completion via A2A protocol.
     * Records case metrics asynchronously.
     */
    public void onTaskCompletion(TaskCompletionEvent event) {
        CaseMetricEvent caseMetric = CaseMetricEvent.builder()
            .caseId(event.caseId())
            .caseType(event.caseType())
            .cycleTimeSeconds(event.cycleTime().toSeconds())
            .workItemsCompleted(event.completedCount())
            .workItemsFailed(event.failedCount())
            .errorRate(calculateErrorRate(event))
            .organizationId(event.orgId())
            .assignedUser(event.userId())
            .build();

        // Fire-and-forget to avoid blocking A2A
        metricsCollector.collectCaseMetrics(caseMetric);
    }
}

// MCP hook integration
@Component
public class McpMetricsHook {

    @Autowired
    private MetricsCollector metricsCollector;

    /**
     * Called after MCP tool invocation.
     * Records skill execution metrics.
     */
    public void onToolCompletion(McpToolCompletionEvent event) {
        SkillMetricEvent skillMetric = SkillMetricEvent.builder()
            .skillId(event.toolName())
            .skillName(event.toolDescription())
            .durationMs((int) event.duration().toMillis())
            .success(event.isSuccess())
            .errorType(event.errorType().orElse(null))
            .inputTokens(event.inputTokens())
            .outputTokens(event.outputTokens())
            .userId(event.callerId())
            .organizationId(event.orgId())
            .requestSource("mcp_client")
            .region(event.region())
            .build();

        metricsCollector.collectSkillMetrics(skillMetric);
    }
}
```

**Integration test**:

```java
@SpringBootTest
@Import(MarketplaceTestConfig.class)
class MetricsCollectionIT {

    @Autowired
    private MetricsCollector collector;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DELETE FROM skill_metrics_raw");
        jdbc.execute("DELETE FROM case_metrics_raw");
    }

    @Test
    @Transactional
    void test_collect_skill_metrics_stores_raw_record() {
        SkillMetricEvent event = testData.createSkillEvent()
            .withSkillId("openai-gpt4")
            .withDuration(Duration.ofMillis(1500))
            .withSuccess(true);

        collector.collectSkillMetrics(event);

        // Verify raw record exists
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM skill_metrics_raw WHERE skill_id = 'openai-gpt4'");

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("duration_ms", 1500)
            .containsEntry("success", true);
    }

    @Test
    void test_collect_case_metrics_fires_async() {
        CaseMetricEvent event = testData.createCaseEvent()
            .withCaseType("order_processing")
            .withCycleTime(Duration.ofSeconds(3600));

        collector.collectCaseMetrics(event);

        // Allow async processing
        Thread.sleep(100);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM case_metrics_raw WHERE case_type = 'order_processing'");

        assertThat(rows).hasSize(1);
    }

    @Test
    void test_100_concurrent_collections_succeed() throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(10);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            futures.add(exec.submit(() -> {
                SkillMetricEvent event = testData.createSkillEvent();
                collector.collectSkillMetrics(event);
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get();
        }

        List<Map<String, Object>> all = jdbc.queryForList(
            "SELECT COUNT(*) as cnt FROM skill_metrics_raw");

        assertThat((Number) all.get(0).get("cnt")).isEqualTo(100);
    }
}
```

**Success criteria**:
- 1000+ skill executions collected per day
- 10K+ case metrics ingested by week-end
- <1% data loss (async queueing)
- Sub-100ms collection latency (non-blocking)
- Database handles 100 concurrent collections

---

### Week 3: Benchmark Aggregation + Query API

**Deliverables**:
1. `BenchmarkAggregator` (SQL views + computed aggregates)
2. `BenchmarkQueryService` (query logic)
3. REST API endpoints (`/api/v1/benchmarks/*`)
4. Query performance tuning (indexes, partitioning)
5. Integration tests (query accuracy)

**Key files**:
- `src/org/yawlfoundation/yawl/integration/marketplace/aggregation/BenchmarkAggregator.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/query/BenchmarkQueryService.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/api/BenchmarkController.java`
- `src/test/org/yawlfoundation/yawl/integration/marketplace/BenchmarkAggregationIT.java`

**Code outline**:

```java
// BenchmarkQueryService.java
@Service
public class BenchmarkQueryService {

    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;

    /**
     * Query benchmarks for a specific skill.
     * Returns aggregated metrics (p50, p95, p99, avg, success rate).
     */
    public SkillBenchmarkResponse getSkillBenchmark(String skillId) {
        return cacheManager.get("skill:" + skillId, () -> {
            String sql = """
                SELECT
                    skill_id,
                    skill_name,
                    COUNT(*) AS n_samples,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_duration_ms,
                    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms,
                    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_duration_ms,
                    AVG(duration_ms) AS avg_duration_ms,
                    SUM(CASE WHEN success THEN 1 ELSE 0 END)::FLOAT / COUNT(*) AS success_rate
                FROM skill_metrics_anonymized
                WHERE skill_id = ?
                  AND execution_timestamp >= NOW() - INTERVAL '30 days'
                GROUP BY skill_id, skill_name
                """;

            return jdbc.queryForObject(sql, (rs, rowNum) ->
                SkillBenchmarkResponse.builder()
                    .skillId(rs.getString("skill_id"))
                    .skillName(rs.getString("skill_name"))
                    .nSamples(rs.getLong("n_samples"))
                    .p50DurationMs(rs.getInt("p50_duration_ms"))
                    .p95DurationMs(rs.getInt("p95_duration_ms"))
                    .p99DurationMs(rs.getInt("p99_duration_ms"))
                    .avgDurationMs(rs.getDouble("avg_duration_ms"))
                    .successRate(rs.getDouble("success_rate"))
                    .build(),
                skillId);
        });
    }

    /**
     * Get industry percentile comparison.
     * Shows: top 25%, median, bottom 25% for skill duration.
     */
    public IndustryPercentileResponse getIndustryPercentiles(String skillId) {
        String sql = """
            SELECT
                quartile,
                MIN(avg_duration_ms) AS min_duration_ms,
                MAX(avg_duration_ms) AS max_duration_ms,
                COUNT(*) AS org_count
            FROM (
                SELECT
                    skill_id,
                    organization_id_hash,
                    AVG(duration_ms) AS avg_duration_ms,
                    NTILE(4) OVER (
                        PARTITION BY skill_id
                        ORDER BY AVG(duration_ms)
                    ) AS quartile
                FROM skill_metrics_anonymized
                WHERE skill_id = ?
                  AND execution_timestamp >= NOW() - INTERVAL '30 days'
                GROUP BY skill_id, organization_id_hash
            )
            GROUP BY quartile
            ORDER BY quartile
            """;

        List<PercentileRow> rows = jdbc.query(sql, (rs, rowNum) ->
            new PercentileRow(
                rs.getInt("quartile"),
                rs.getDouble("min_duration_ms"),
                rs.getDouble("max_duration_ms"),
                rs.getInt("org_count")
            ),
            skillId);

        return IndustryPercentileResponse.fromRows(rows);
    }

    /**
     * Compare skill performance across organizations.
     */
    public SkillComparisonResponse compareSkills(String... skillIds) {
        String placeholders = String.join(",",
            Collections.nCopies(skillIds.length, "?"));

        String sql = String.format("""
            SELECT
                skill_id,
                skill_name,
                COUNT(*) AS n_samples,
                ROUND(AVG(duration_ms)::NUMERIC, 2) AS avg_duration_ms,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms
            FROM skill_metrics_anonymized
            WHERE skill_id IN (%s)
              AND execution_timestamp >= NOW() - INTERVAL '7 days'
            GROUP BY skill_id, skill_name
            ORDER BY avg_duration_ms ASC
            """, placeholders);

        List<SkillComparisonRow> rows = jdbc.query(sql, (rs, rowNum) ->
            new SkillComparisonRow(
                rs.getString("skill_id"),
                rs.getString("skill_name"),
                rs.getLong("n_samples"),
                rs.getDouble("avg_duration_ms"),
                rs.getDouble("p95_duration_ms")
            ),
            (Object[]) skillIds);

        return new SkillComparisonResponse(rows);
    }
}

// BenchmarkController.java
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

    @GetMapping("/skills/{skillId}/industry")
    public ResponseEntity<IndustryPercentileResponse> getIndustryPercentiles(
        @PathVariable String skillId) {

        IndustryPercentileResponse response = queryService.getIndustryPercentiles(skillId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseType}")
    public ResponseEntity<CaseBenchmarkResponse> getCaseBenchmark(
        @PathVariable String caseType) {

        String sql = """
            SELECT
                case_type,
                COUNT(*) AS n_cases,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p50_cycle_time_s,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p95_cycle_time_s,
                PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY cycle_time_seconds) AS p99_cycle_time_s,
                AVG(cycle_time_seconds) AS avg_cycle_time_s,
                AVG(error_rate) AS avg_error_rate
            FROM case_metrics_anonymized
            WHERE case_type = ?
              AND case_started_at >= NOW() - INTERVAL '30 days'
              AND case_completed_at IS NOT NULL
            GROUP BY case_type
            """;

        // Execute and map...
    }
}
```

**Benchmark response types**:

```java
record SkillBenchmarkResponse(
    String skillId,
    String skillName,
    long nSamples,
    int p50DurationMs,
    int p95DurationMs,
    int p99DurationMs,
    double avgDurationMs,
    double successRate
) {}

record IndustryPercentileResponse(
    String skillId,
    PercentileQuartile top25,     // Best performers
    PercentileQuartile median,    // Middle 50%
    PercentileQuartile bottom25   // Slowest
) {
    record PercentileQuartile(
        int minDurationMs,
        int maxDurationMs,
        int orgCount  // Organizations in this quartile
    ) {}
}

record CaseBenchmarkResponse(
    String caseType,
    long nCases,
    int p50CycleTimeS,
    int p95CycleTimeS,
    int p99CycleTimeS,
    double avgCycleTimeS,
    double avgErrorRate
) {}
```

**Integration test**:

```java
@SpringBootTest
@Import(MarketplaceTestConfig.class)
class BenchmarkAggregationIT {

    @Autowired
    private BenchmarkQueryService queryService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MetricsCollector collector;

    @Test
    void test_skill_benchmark_aggregates_samples_correctly() {
        // Insert 100 skill metrics
        for (int i = 0; i < 100; i++) {
            SkillMetricRaw raw = testData.createRawMetric()
                .withSkillId("code-review")
                .withDuration(1000 + (i % 500))  // 1000-1500ms
                .withSuccess(true);

            skillRepo.saveAnonymized(raw);
        }

        SkillBenchmarkResponse benchmark = queryService.getSkillBenchmark("code-review");

        assertThat(benchmark.nSamples()).isEqualTo(100);
        assertThat(benchmark.p50DurationMs()).isBetween(1200, 1300);  // Median
        assertThat(benchmark.p95DurationMs()).isBetween(1400, 1500);  // High end
        assertThat(benchmark.successRate()).isEqualTo(1.0);
    }

    @Test
    void test_industry_percentile_calculation() {
        // Create 40 orgs with different performance profiles
        for (int org = 0; org < 40; org++) {
            String orgHash = String.format("org-%03d", org);

            // Fast orgs (top 25%): 500-800ms
            if (org < 10) {
                for (int i = 0; i < 50; i++) {
                    insertMetric("skill-1", orgHash, 500 + org * 30);
                }
            }
            // Medium orgs (50%): 1000-1500ms
            else if (org < 30) {
                for (int i = 0; i < 50; i++) {
                    insertMetric("skill-1", orgHash, 1000 + org * 30);
                }
            }
            // Slow orgs (bottom 25%): 2000-3000ms
            else {
                for (int i = 0; i < 50; i++) {
                    insertMetric("skill-1", orgHash, 2000 + org * 100);
                }
            }
        }

        IndustryPercentileResponse percentiles =
            queryService.getIndustryPercentiles("skill-1");

        assertThat(percentiles.top25().minDurationMs()).isLessThan(800);
        assertThat(percentiles.median().minDurationMs()).isBetween(1000, 1500);
        assertThat(percentiles.bottom25().minDurationMs()).isGreaterThan(2000);
    }

    @Test
    void test_benchmark_query_performance_sub_500ms() {
        // Warm up cache
        queryService.getSkillBenchmark("slow-skill");

        long startNs = System.nanoTime();
        SkillBenchmarkResponse response = queryService.getSkillBenchmark("slow-skill");
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertThat(elapsedMs).isLessThan(500);  // SLA: <500ms
    }
}
```

**Success criteria**:
- Queries return <500ms (with caching)
- Percentile calculations accurate within 1%
- Support 50+ concurrent benchmark queries
- Cache hit rate >80%

---

### Week 4: Query API + Dashboard + Export

**Deliverables**:
1. REST API complete (see Week 3, expand)
2. Metrics export endpoint (CSV)
3. Read-only dashboard (basic web UI)
4. E2E tests (full data flow)
5. Documentation + deployment guide

**Key files**:
- `src/org/yawlfoundation/yawl/integration/marketplace/api/BenchmarkController.java`
- `src/org/yawlfoundation/yawl/integration/marketplace/export/MetricsExporter.java`
- `src/ui/marketplace-dashboard.html`
- `src/test/org/yawlfoundation/yawl/integration/marketplace/E2ETest.java`

**Additional API endpoints**:

```java
// Export metrics as CSV
@GetMapping("/export")
public ResponseEntity<Resource> exportMetrics(
    @RequestParam(required = false) String skillId,
    @RequestParam(required = false) String caseType,
    @RequestParam(defaultValue = "30") int daysBack) {

    byte[] csvData = exporter.exportMetrics(skillId, caseType, daysBack);

    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=benchmarks.csv")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new ByteArrayResource(csvData));
}

// Leaderboard (top performers)
@GetMapping("/leaderboard/skills")
public ResponseEntity<SkillLeaderboardResponse> getSkillLeaderboard(
    @RequestParam(defaultValue = "10") int limit) {

    List<SkillRankRow> rows = jdbc.query("""
        SELECT
            RANK() OVER (ORDER BY AVG(duration_ms) ASC) AS rank,
            skill_id,
            skill_name,
            COUNT(*) AS n_samples,
            ROUND(AVG(duration_ms)::NUMERIC, 0) AS avg_duration_ms,
            SUM(CASE WHEN success THEN 1 ELSE 0 END)::FLOAT / COUNT(*) AS success_rate
        FROM skill_metrics_anonymized
        WHERE execution_timestamp >= NOW() - INTERVAL '7 days'
        GROUP BY skill_id, skill_name
        ORDER BY avg_duration_ms ASC
        LIMIT ?
        """, ps -> ps.setInt(1, limit));

    return ResponseEntity.ok(new SkillLeaderboardResponse(rows));
}

// Trend analysis (skill performance over time)
@GetMapping("/trends/skills/{skillId}")
public ResponseEntity<SkillTrendResponse> getSkillTrend(
    @PathVariable String skillId,
    @RequestParam(defaultValue = "7") int daysBack) {

    List<TrendPoint> points = jdbc.query("""
        SELECT
            DATE_TRUNC('hour', execution_timestamp) AS hour,
            ROUND(AVG(duration_ms)::NUMERIC, 2) AS avg_duration_ms,
            COUNT(*) AS n_samples
        FROM skill_metrics_anonymized
        WHERE skill_id = ?
          AND execution_timestamp >= NOW() - INTERVAL ? ||' days'
        GROUP BY DATE_TRUNC('hour', execution_timestamp)
        ORDER BY hour ASC
        """,
        new Object[]{skillId, daysBack},
        (rs, rowNum) -> new TrendPoint(
            rs.getTimestamp("hour"),
            rs.getDouble("avg_duration_ms"),
            rs.getInt("n_samples")
        ));

    return ResponseEntity.ok(new SkillTrendResponse(skillId, points));
}
```

**E2E Test**:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class DataMarketplaceE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MetricsCollector collector;

    @Test
    void test_full_data_flow_from_collection_to_query() throws Exception {
        // Step 1: Collect 10K skill metrics over 7 days
        LocalDateTime now = LocalDateTime.now();
        for (int day = 0; day < 7; day++) {
            for (int i = 0; i < 1428; i++) {  // ~10K total
                SkillMetricEvent event = testData.createSkillEvent()
                    .withSkillId("code-review")
                    .withDuration(Duration.ofMillis(500 + random.nextInt(1500)))
                    .withTimestamp(now.minusDays(7 - day))
                    .withSuccess(random.nextDouble() > 0.02);  // 98% success

                collector.collectSkillMetrics(event);
            }
        }

        // Wait for anonymization pipeline
        Thread.sleep(2000);

        // Step 2: Query benchmark
        ResponseEntity<SkillBenchmarkResponse> response = restTemplate.getForEntity(
            "/api/v1/benchmarks/skills/code-review",
            SkillBenchmarkResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SkillBenchmarkResponse benchmark = response.getBody();

        assertThat(benchmark.nSamples()).isGreaterThanOrEqualTo(10_000);
        assertThat(benchmark.successRate()).isGreaterThan(0.97);
        assertThat(benchmark.p50DurationMs()).isBetween(750, 1250);
        assertThat(benchmark.p95DurationMs()).isGreaterThan(benchmark.p50DurationMs());

        // Step 3: Query industry percentiles
        ResponseEntity<IndustryPercentileResponse> percentileResp =
            restTemplate.getForEntity(
                "/api/v1/benchmarks/skills/code-review/industry",
                IndustryPercentileResponse.class);

        assertThat(percentileResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 4: Export data as CSV
        ResponseEntity<String> exportResp = restTemplate.getForEntity(
            "/api/v1/benchmarks/export?skillId=code-review",
            String.class);

        assertThat(exportResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = exportResp.getBody();

        assertThat(csv).contains("skill_id,avg_duration_ms,p50_duration_ms");

        // Step 5: Verify PII is removed
        List<String> rawRecords = jdbc.queryForList(
            "SELECT * FROM skill_metrics_raw WHERE skill_id = 'code-review' LIMIT 1",
            String.class);

        List<String> anonRecords = jdbc.queryForList(
            "SELECT * FROM skill_metrics_anonymized WHERE skill_id = 'code-review' LIMIT 1",
            String.class);

        // Raw should have user_id, anon should have user_id_hash
        assertThat(rawRecords.get(0)).contains("user_id");
        assertThat(anonRecords.get(0)).contains("user_id_hash");
        assertThat(anonRecords.get(0)).doesNotContain("user_id");
    }
}
```

**Success criteria**:
- All endpoints tested (happy path + error cases)
- CSV export produces valid format
- 10K+ records in database by week-end
- <500ms query latency verified
- Zero PII in anonymized tables (automated checks)

---

## 4. Data Privacy & Security

### 4.1 PII Removal Verification

**Automated checks** (run in test CI/CD):

```bash
# SQL query: Detect accidentally-exposed PII patterns
SELECT COUNT(*) as pii_found
FROM skill_metrics_anonymized
WHERE
  user_id_hash ~ '^[a-f0-9-]{36}$' OR  -- UUID (not hashed)
  user_id_hash ~ '@' OR                 -- Email pattern
  organization_id_hash ~ '^[a-z0-9-]{16}$'  -- Org UUID (not hashed)
;
-- Expected: 0 rows

# Check: No raw records exposed in anonymized table
SELECT COUNT(*) as error
FROM skill_metrics_anonymized sma
LEFT JOIN skill_metrics_raw smr ON sma.raw_id = smr.id
WHERE smr.id IS NULL;
-- Expected: 0 rows (all references valid)
```

### 4.2 Anonymization Salt Rotation

**Monthly rotation** (scheduled task):

```java
@Component
public class AnonymizationSaltRotation {

    @Scheduled(cron = "0 0 0 1 * *")  // First day of month
    public void rotateAnonymizationSalt() {
        String newSalt = generateSecureRandomSalt(64);

        // Update environment
        System.setProperty("ANONYMIZATION_SALT", newSalt);

        // Log rotation (no salt value in logs)
        logger.info("Anonymization salt rotated successfully");

        // Trigger re-anonymization of raw records older than rotation point
        // (future improvement: re-hash with new salt if determinism needed)
    }
}
```

### 4.3 Data Retention Compliance

**Automated cleanup** (GDPR/CCPA compliant):

```java
@Component
public class DataRetentionEnforcer {

    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    public void enforceRetentionPolicy() {
        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(7);  // Raw metrics: 7 days

        int deleted = jdbc.update(
            "DELETE FROM skill_metrics_raw WHERE creation_timestamp < ?",
            java.sql.Timestamp.valueOf(cutoff));

        logger.info("Deleted {} raw metric records (retention policy)", deleted);

        // Keep anonymized metrics for 90 days (benchmarking)
        cutoff = LocalDateTime.now().minusDays(90);
        deleted = jdbc.update(
            "DELETE FROM skill_metrics_anonymized WHERE creation_timestamp < ?",
            java.sql.Timestamp.valueOf(cutoff));

        logger.info("Deleted {} anonymized metric records (retention policy)", deleted);
    }
}
```

---

## 5. 4-Week Roadmap

| Week | Focus | Deliverables | Success Metrics |
|------|-------|--------------|-----------------|
| **1** | Schema + Anonymization | metrics-schema.yaml, AnonymizationPipeline, tests | 100% PII rules apply, tests green |
| **2** | Collection + Integration | MetricsCollector, A2A/MCP hooks, database migration, IT | 10K records in DB, <1% loss, 100 concurrent |
| **3** | Aggregation + Queries | BenchmarkAggregator, REST API, query service, IT | <500ms queries, 50+ concurrent, cache >80% |
| **4** | Export + Dashboard + E2E | CSV export, dashboard UI, E2E test, documentation | All endpoints tested, 10K+ records, <500ms SLA met |

---

## 6. Module Placement & Maven Structure

**Create new module: `yawl-marketplace`**

```
/home/user/yawl/yawl-marketplace/
├── pom.xml                           (parent: yawl-integration)
├── src/
│   ├── main/java/org/yawlfoundation/yawl/integration/marketplace/
│   │   ├── config/
│   │   │   └── MetricsSchemaLoader.java
│   │   ├── collection/
│   │   │   ├── MetricsCollector.java
│   │   │   ├── SkillMetricsCollector.java
│   │   │   ├── CaseMetricsCollector.java
│   │   │   └── MetricsCollectionException.java
│   │   ├── anonymization/
│   │   │   ├── AnonymizationPipeline.java
│   │   │   ├── AnonymizationRule.java
│   │   │   ├── AnonymizationRuleLoader.java
│   │   │   └── PiiDetector.java
│   │   ├── aggregation/
│   │   │   ├── BenchmarkAggregator.java
│   │   │   └── AggregationScheduler.java
│   │   ├── query/
│   │   │   ├── BenchmarkQueryService.java
│   │   │   └── BenchmarkQueryCache.java
│   │   ├── api/
│   │   │   ├── BenchmarkController.java
│   │   │   ├── BenchmarkResponse.java
│   │   │   └── ExportController.java
│   │   ├── export/
│   │   │   ├── MetricsExporter.java
│   │   │   └── CsvWriter.java
│   │   ├── entity/
│   │   │   ├── SkillMetricRaw.java
│   │   │   ├── SkillMetricAnonymized.java
│   │   │   ├── CaseMetricRaw.java
│   │   │   └── CaseMetricAnonymized.java
│   │   ├── repository/
│   │   │   ├── SkillMetricsRepository.java
│   │   │   ├── CaseMetricsRepository.java
│   │   │   └── BenchmarkRepository.java
│   │   └── MarketplaceConfiguration.java
│   ├── test/java/org/yawlfoundation/yawl/integration/marketplace/
│   │   ├── AnonymizationPipelineTest.java
│   │   ├── MetricsCollectionIT.java
│   │   ├── BenchmarkAggregationIT.java
│   │   ├── BenchmarkQueryServiceIT.java
│   │   ├── BenchmarkControllerIT.java
│   │   ├── DataMarketplaceE2ETest.java
│   │   └── testdata/
│   │       └── TestDataBuilder.java
│   └── resources/
│       ├── config/metrics-schema.yaml
│       ├── db/marketplace/
│       │   ├── V001__init_marketplace_schema.sql
│       │   └── V002__create_benchmark_views.sql
│       └── application-marketplace.yaml
└── target/
```

---

## 7. Integration Points

### 7.1 A2A Integration

**Hook location**: `YawlA2AServer.handleTaskCompletion()`

```java
// Existing A2A code modified to call metrics hook
private void handleTaskCompletion(TaskCompletionMessage msg) {
    // ... existing A2A logic ...

    // NEW: Record case metrics
    a2aMetricsHook.onTaskCompletion(
        TaskCompletionEvent.builder()
            .caseId(msg.caseId())
            .caseType(msg.caseType())
            .cycleTime(msg.duration())
            .completedCount(msg.completedCount())
            .failedCount(msg.failedCount())
            .orgId(msg.organizationId())
            .userId(msg.userId())
            .build());
}
```

### 7.2 MCP Integration

**Hook location**: `YawlMcpServer.executeTool()`

```java
// Existing MCP code modified
public Object executeTool(String toolName, Map<String, Object> params) {
    long startNs = System.nanoTime();
    try {
        Object result = toolRegistry.execute(toolName, params);

        // NEW: Record success
        metricsHook.onToolCompletion(
            McpToolCompletionEvent.builder()
                .toolName(toolName)
                .success(true)
                .duration(Duration.ofNanos(System.nanoTime() - startNs))
                .callerId(getCallerId())  // From JWT context
                .orgId(getOrgId())
                .region(getRegion())
                .build());

        return result;
    } catch (Exception e) {
        // NEW: Record failure
        metricsHook.onToolCompletion(
            McpToolCompletionEvent.builder()
                .toolName(toolName)
                .success(false)
                .errorType(e.getClass().getSimpleName())
                .duration(Duration.ofNanos(System.nanoTime() - startNs))
                .callerId(getCallerId())
                .orgId(getOrgId())
                .region(getRegion())
                .build());

        throw e;
    }
}
```

---

## 8. Success Criteria & Validation

### 8.1 Quantitative Metrics

| Metric | Target | Validation |
|--------|--------|-----------|
| **Data ingestion** | 10K+ records by week 4 | Query: `SELECT COUNT(*) FROM skill_metrics_anonymized` |
| **PII removal** | 100% (0 PII in anon tables) | Automated regex scan in test CI/CD |
| **Query latency** | <500ms p95 | Load test: 50 concurrent queries |
| **Collection latency** | <100ms | Instrument: time `collector.collectSkillMetrics()` |
| **Data loss** | <1% | Compare events sent vs records stored |
| **Cache hit rate** | >80% | Micrometer: `cache.gets.hit` / `cache.gets` |
| **Concurrency** | 100 concurrent collections | Load test: 100 threads, 1000 events each |

### 8.2 Qualitative Criteria

- **Anonymization**: User cannot link anon records back to individuals
- **Schema completeness**: All execution metrics captured (no silent omissions)
- **API usability**: REST endpoints follow REST conventions (proper HTTP codes, JSON)
- **Documentation**: Deployment guide + SQL queries for analytics
- **Code quality**: 80%+ test coverage (Chicago TDD, real integrations)

---

## 9. Phase 2 (Future): Federated Marketplace

**Out of scope for MVP, but architecture enables**:

1. **Federation**: Multiple YAWL instances share benchmarks via decentralized consensus
2. **Machine learning**: Predict skill failure rates based on historical patterns
3. **Reputation scoring**: Skills get scores based on success rate + latency
4. **Cost attribution**: Link metrics to skill execution costs (LLM tokens, compute)
5. **Recommendations**: "Use Skill X—it's 40% faster than your current choice"

---

## 10. Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| **PII leak** | Automated tests scan anonymized tables for patterns (email, UUID); false positives force review |
| **High latency** | Caching (Redis), query optimization (indexes), materialized views |
| **Data loss** | Dual write (PostgreSQL + backup table), async queue with persistence |
| **Schema evolution** | Versioned schema (metrics-schema.yaml v1.0, v1.1), backward-compatible fields |
| **Regulatory change** | Data retention policy easy to adjust (see 4.3); anonymization salt rotation enabled |

---

## Conclusion

This 4-week MVP focuses on **80/20 impact**: collect anonymized execution metrics, aggregate into benchmarks, expose via REST API. Foundation enables Phase 2 federation, ML, reputation scoring, and cost attribution.

**Key design principles**:
1. **Privacy-first**: Anonymization from ingestion, not retrofit
2. **Simple schema**: Only metrics, not raw data (no workflow step sequences)
3. **Real database**: PostgreSQL + TimescaleDB, not mock
4. **No central server**: Federated-ready (Phase 2)
5. **Production-ready**: Retention policies, salt rotation, E2E tests

**Success in 4 weeks**: 10K+ anonymized records, <500ms queries, 100% PII removal verified.

