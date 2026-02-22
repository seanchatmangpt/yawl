# Data Marketplace MVP - Design Summary

**Version**: 1.0
**Created**: 2026-02-21
**Status**: Ready for Implementation
**Effort**: 4 weeks, 2 engineers, ~$50K

---

## Executive Summary

A privacy-first **Data Marketplace MVP** that collects anonymized execution metrics from YAWL skills (MCP tools) and cases (workflows), enabling industry benchmarking and performance comparison without exposing personally identifiable information.

**Core hypothesis**: Skills generate execution data. Anonymize at ingestion. Aggregate into benchmarks. Enable data-driven decisions ("Is my skill faster than peers? How does my throughput compare?") without leaking proprietary data.

**Success in 4 weeks**:
- ✓ 10K+ anonymized skill/case executions ingested
- ✓ 100% PII removal verified in tests
- ✓ Benchmarks queryable in <500ms
- ✓ Industry median comparisons available
- ✓ REST API for benchmark access

---

## What We've Designed

### 1. **DATA-MARKETPLACE-MVP-DESIGN.md** (85 KB, comprehensive)

**Complete 4-week implementation roadmap**:

| Week | Focus | Deliverables |
|------|-------|--------------|
| 1 | Schema + Anonymization | metrics-schema.yaml, AnonymizationPipeline, test suite |
| 2 | Collection + Integration | MetricsCollector, A2A/MCP hooks, database migration |
| 3 | Aggregation + Queries | BenchmarkAggregator, REST API, performance tuning |
| 4 | Export + Dashboard + E2E | CSV export, dashboard UI, end-to-end tests |

**Key sections**:
- System architecture (4-stage data flow diagram)
- Core components (Metrics Schema, Database Schema, Code outline)
- Week-by-week breakdown with code examples
- Data privacy & security (PII removal, salt rotation, compliance)
- Success criteria & validation checklist
- Risk mitigation strategies

**Use this to**: Guide development, validate design decisions, track progress

---

### 2. **config/metrics-schema.yaml** (15 KB, YAML specification)

**Immutable metric definitions** (version 1.0):

```yaml
skill_execution_metric:
  fields:
    - skill_id (string, anonymized=false)
    - skill_name (string)
    - execution_timestamp (timestamp)
    - duration_ms (integer)
    - success (boolean)
    - error_type (string, nullable)
    - input_tokens, output_tokens (integer, for LLM usage)
    - memory_used_mb (float)
    - user_id (string, ANONYMIZE ME → hash)
    - organization_id (string, ANONYMIZE ME → deterministic hash)
    - request_source (enum: mcp_client, a2a_agent, direct_api)
    - region (string, optional)

case_execution_metric:
  fields:
    - case_id (string, ANONYMIZE ME → hash)
    - case_type (string)
    - cycle_time_seconds (integer)
    - work_items_completed, work_items_failed (integer)
    - error_rate (float, 0-1)
    - organization_id (string, ANONYMIZE ME → deterministic hash)
    - assigned_user (string, ANONYMIZE ME → hash)

anonymization_rules:
  - hash_user_id (non-deterministic: breaks linkability)
  - hash_organization_id (deterministic: enables org benchmarking)
  - hash_case_id (non-deterministic)
  - mask_ip_address (last octet masked)

retention_policy:
  raw_metrics: 7 days (audit trail)
  anonymized_metrics: 90 days (benchmarks)
  aggregated_metrics: 2 years (history)
```

**Use this to**: Define database schema, validate PII removal, guide implementation

---

### 3. **docs/integration/DATA-MARKETPLACE-ARCHITECTURE.md** (14 KB, detailed architecture)

**Technical design for integration with YAWL**:

- Component diagram (collection → anonymization → aggregation → query)
- Integration points:
  - **MCP**: `YawlMcpServer.executeTool()` hook
  - **A2A**: `YawlA2AServer.handleCaseCompletion()` hook
- Data flow timeline (T=0 to T=+90 days)
- Database schema with partitioning strategy
- REST API specification (5 endpoints)
- Performance targets (SLA: <500ms queries, 100/sec collection)
- Security & privacy (auth, encryption, retention)
- Monitoring & observability (Micrometer metrics, alerts)
- Testing strategy (unit, integration, E2E)
- Deployment checklist

**Use this to**: Integrate with MCP/A2A, optimize queries, deploy to production

---

### 4. **docs/integration/MARKETPLACE-INTEGRATION-QUICKSTART.md** (10 KB, step-by-step)

**Quick start guide for integration** (30-minute task):

1. Add metrics hook to MCP server (code example)
2. Add metrics hook to A2A server (code example)
3. Create `MetricsCollectorImpl` Spring component
4. Database setup (Flyway migration SQL)
5. Query API controller (REST endpoints)
6. Verification checklist (curl commands)
7. Common issues & fixes
8. Monitoring setup

**Use this to**: Integrate hooks quickly, verify setup, debug issues

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────┐
│ YAWL Skill Execution (MCP) / Case Execution (A2A)           │
└────────────────┬────────────────────────────────────────────┘
                 │
         ┌───────▼────────┐
         │ MetricsCollector│ (async, non-blocking)
         └───────┬────────┘
                 │
    ┌────────────▼────────────┐
    │ AnonymizationPipeline   │ (batch every 6h)
    │ • hash user_id          │
    │ • hash org_id           │
    │ • remove PII            │
    └────────────┬────────────┘
                 │
    ┌────────────▼──────────────────┐
    │ PostgreSQL + TimescaleDB       │
    │ • skill_metrics_raw (7d)       │
    │ • skill_metrics_anonymized (90d)
    │ • Views: benchmarks            │
    └────────────┬──────────────────┘
                 │
    ┌────────────▼──────────────────┐
    │ BenchmarkQueryService (Cached) │
    │ <500ms SLA                     │
    └────────────┬──────────────────┘
                 │
    ┌────────────▼──────────────────┐
    │ REST API                       │
    │ GET /api/v1/benchmarks/skills/:id
    │ GET /api/v1/benchmarks/cases/:type
    │ GET /api/v1/benchmarks/export │
    └────────────────────────────────┘
```

---

## Key Design Decisions

### 1. **Privacy-First Architecture**

- **Anonymization at source**: PII removed during collection, never stored in anonymized tables
- **Deterministic org hashing**: Same org ID → same hash (enables org-level benchmarking)
- **Non-deterministic user hashing**: Same user ID → different hash (breaks user linkability)
- **No central authority**: Federated-ready (Phase 2)

### 2. **Data Retention (GDPR/CCPA Compliant)**

- **Raw metrics**: 7 days (for anonymization audit)
- **Anonymized metrics**: 90 days (for benchmarking)
- **Aggregated metrics**: 2 years (for historical comparison)
- **Automated deletion**: Scheduled daily, enforced by partitioning

### 3. **Performance at Scale**

- **Async collection**: <10ms latency, non-blocking (virtual threads)
- **Caching**: Redis 1-hour TTL, >80% hit rate target
- **SQL views**: Pre-computed percentiles, indexes optimized
- **SLA**: <500ms p95 latency (with cache), 100 concurrent queries

### 4. **Immutable Schema**

- **Version 1.0**: Skill + Case metrics, basic anonymization
- **Version 1.1**: Cost tracking (Phase 1.5)
- **Version 2.0**: ML features, federated consensus (Phase 2)
- **Backward compatibility**: New fields nullable, old queries still work

### 5. **Real Database** (Not Mocks)

- **PostgreSQL 15+** with TimescaleDB extension
- **Native partitioning** (daily) for fast deletion
- **Micrometer metrics** for production observability
- **Actual integration tests** (Chicago TDD)

---

## 4-Week Roadmap (Execution Plan)

### Week 1: Schema + Anonymization (30% effort)
**Goal**: Define metrics, implement anonymization rules, 100% test coverage

**Deliverables**:
1. `metrics-schema.yaml` (config file)
2. `AnonymizationPipeline.java` (interface + sealed rules)
3. `AnonymizationRule.java` (hash/mask/remove rules)
4. Test suite (determinism, PII removal, edge cases)

**Key milestone**: `AnonymizationPipelineTest` all green

### Week 2: Collection + Integration (35% effort)
**Goal**: Hook into MCP/A2A, collect 10K+ metrics by end of week

**Deliverables**:
1. `MetricsCollector` interface + implementations
2. MCP hook in `YawlMcpServer.executeTool()`
3. A2A hook in `YawlA2AServer.handleCaseCompletion()`
4. Database migration (Flyway V001, V002)
5. Integration tests (real data flow)

**Key milestone**: 10K+ records in `skill_metrics_raw` table

### Week 3: Aggregation + Queries (25% effort)
**Goal**: Make benchmarks queryable with <500ms latency

**Deliverables**:
1. `BenchmarkAggregator` (hourly SQL views)
2. `BenchmarkQueryService` (query logic + caching)
3. REST API controller (5 endpoints)
4. Performance tuning (indexes, cache)
5. Integration tests (query accuracy)

**Key milestone**: `GET /api/v1/benchmarks/skills/openai-gpt4` returns <500ms

### Week 4: Export + Dashboard + E2E (10% effort)
**Goal**: Full production readiness, documentation

**Deliverables**:
1. CSV export endpoint
2. Read-only dashboard UI (basic)
3. E2E test (full data flow 10K records)
4. Deployment guide + monitoring setup
5. README + integration guide

**Key milestone**: E2E test passes, all endpoints tested

---

## Success Metrics

| Metric | Target | Validation |
|--------|--------|-----------|
| **Data collected** | 10K+ records by week 4 | `SELECT COUNT(*) FROM skill_metrics_raw` |
| **PII removal** | 100% (0 PII in anon tables) | Automated regex scan in test CI/CD |
| **Query latency** | <500ms p95 | Load test: 50 concurrent queries |
| **Collection latency** | <10ms (async) | Time hook execution |
| **Data loss** | <0.1% | Compare events sent vs stored |
| **Cache hit rate** | >80% | Micrometer metrics |
| **Test coverage** | >80% | Jakarta Code Coverage |
| **Schema completeness** | 100% (no silent omissions) | Field validation tests |

---

## Code Artifacts (File Structure)

```
yawl-marketplace/ (new module)
├── pom.xml
├── src/main/java/org/yawlfoundation/yawl/integration/marketplace/
│   ├── config/
│   │   └── MetricsSchemaLoader.java
│   ├── collection/
│   │   ├── MetricsCollector.java (interface)
│   │   ├── SkillMetricsCollector.java
│   │   └── CaseMetricsCollector.java
│   ├── anonymization/
│   │   ├── AnonymizationPipeline.java
│   │   ├── AnonymizationRule.java (sealed)
│   │   └── PiiDetector.java
│   ├── aggregation/
│   │   └── BenchmarkAggregator.java
│   ├── query/
│   │   ├── BenchmarkQueryService.java
│   │   └── BenchmarkQueryCache.java
│   ├── api/
│   │   ├── BenchmarkController.java
│   │   └── BenchmarkResponse.java (records)
│   ├── entity/
│   │   ├── SkillMetricRaw.java
│   │   ├── SkillMetricAnonymized.java
│   │   └── CaseMetricRaw/Anonymized.java
│   ├── repository/
│   │   └── SkillMetricsRepository.java
│   └── MarketplaceConfiguration.java
├── src/test/java/...
│   ├── AnonymizationPipelineTest.java
│   ├── MetricsCollectionIT.java
│   ├── BenchmarkAggregationIT.java
│   ├── BenchmarkControllerIT.java
│   └── DataMarketplaceE2ETest.java
├── src/resources/
│   ├── config/metrics-schema.yaml
│   ├── db/marketplace/V001__init.sql
│   └── db/marketplace/V002__views.sql
└── target/

config/ (root)
└── metrics-schema.yaml (immutable spec)

docs/integration/
├── DATA-MARKETPLACE-ARCHITECTURE.md
├── MARKETPLACE-INTEGRATION-QUICKSTART.md
└── ...
```

---

## Integration with Existing YAWL Code

### MCP Integration Point
```java
// In: yawl-mcp-a2a-app/src/main/java/.../YawlMcpServer.java
public Object executeTool(String toolName, Map<String, Object> params) {
    long startNs = System.nanoTime();
    try {
        Object result = toolRegistry.execute(toolName, params);
        metricsCollector.collectSkillMetrics(...);  // NEW: 3 lines
        return result;
    } catch (Exception e) {
        metricsCollector.collectSkillMetrics(...);  // NEW: 3 lines
        throw e;
    }
}
```

### A2A Integration Point
```java
// In: yawl-mcp-a2a-app/src/main/java/.../YawlA2AServer.java
public void handleCaseCompletion(CaseCompletionMessage msg) {
    processCaseCompletion(msg);
    metricsCollector.collectCaseMetrics(...);  // NEW: 1 line
}
```

**Total invasiveness**: <10 lines of code in production services

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| **PII leak** | Low | Critical | Automated tests scan anon tables for patterns; manual review on failure |
| **High query latency** | Low | Medium | Caching (Redis), query optimization (indexes), materialized views |
| **Data loss** | Low | Medium | Persistent async queue, duplicate detection |
| **Schema evolution** | Medium | Low | Versioned YAML, backward-compatible fields, migration strategy |
| **Regulatory change** | Medium | Medium | Data retention policy easy to adjust, salt rotation enabled |

---

## Phase 2+ Roadmap (Out of Scope for MVP)

1. **Federated Marketplace**: Multiple YAWL instances share benchmarks via consensus
2. **ML Models**: Failure prediction, anomaly detection, cost forecasting
3. **Skill Reputation**: Rank skills by performance, cost, reliability
4. **Cost Attribution**: Link metrics to cloud service costs
5. **Recommendations**: "Use Skill B—same features, 30% faster than yours"
6. **Real-time Streaming**: Kafka events for live dashboards
7. **Industry Reports**: Monthly benchmarking reports
8. **Multi-tenancy**: Separate benchmark pools by domain

---

## How to Use These Documents

### For Project Managers
1. Read this summary (10 min)
2. Use 4-week roadmap to track progress
3. Reference success metrics weekly

### For Architects
1. Read DATA-MARKETPLACE-MVP-DESIGN.md (30 min)
2. Review DATA-MARKETPLACE-ARCHITECTURE.md for integration (20 min)
3. Create detailed tech spec for each component

### For Engineers
1. Read MARKETPLACE-INTEGRATION-QUICKSTART.md (15 min)
2. Start with Week 1 (anonymization) + Week 2 (collection)
3. Use code examples as templates
4. Run E2E test as validation

### For QA/Testing
1. Review "Success Criteria" section (5 min)
2. Build test matrix from Week-by-week breakdown
3. Use E2E test as template for additional scenarios

### For DevOps/Security
1. Read DATA-MARKETPLACE-ARCHITECTURE.md section 9 (15 min)
2. Review retention policy + compliance
3. Set up alerts (Micrometer metrics)
4. Plan backups for raw metrics (7-day retention)

---

## Key Files & References

| File | Purpose | Size |
|------|---------|------|
| `DATA-MARKETPLACE-MVP-DESIGN.md` | Complete 4-week plan + code | 85 KB |
| `config/metrics-schema.yaml` | Metric definitions (immutable) | 15 KB |
| `docs/integration/DATA-MARKETPLACE-ARCHITECTURE.md` | Technical integration | 14 KB |
| `docs/integration/MARKETPLACE-INTEGRATION-QUICKSTART.md` | Quick start guide | 10 KB |
| This file | Executive summary | 8 KB |

---

## Next Steps

1. **Day 1**: Review this summary + get team alignment
2. **Day 2-3**: Read DATA-MARKETPLACE-MVP-DESIGN.md + architecture
3. **Day 4**: Create detailed tech spec (1-2 page per week)
4. **Day 5**: Sprint planning (Week 1 tasks + assignments)
5. **Week 1**: Implementation begins (anonymization + tests)

---

## Conclusion

This MVP is designed to be **simple, privacy-first, and production-ready**:
- ✓ **Simple**: Anonymize at source, aggregate in DB, expose via REST API
- ✓ **Privacy-first**: No PII in production, GDPR/CCPA compliant
- ✓ **Production-ready**: Real database, Chicago TDD, <500ms SLA

**Success definition**: 10K+ anonymized records, 100% PII removal, <500ms queries by week 4.

**Team**: 2 engineers, 4 weeks, ~$50K.

**Why it matters**: Enables YAWL users to make data-driven decisions ("Is my skill fast enough? How do I compare to peers?") without exposing proprietary data. Foundation for Phase 2 federated marketplace, ML models, and reputation scoring.

