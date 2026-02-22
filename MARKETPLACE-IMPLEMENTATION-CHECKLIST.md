# Data Marketplace Implementation Checklist

**Project**: Data Marketplace MVP (4-week sprint)
**Target Completion**: 4 weeks from start
**Updated**: 2026-02-21

---

## Week 1: Schema & Anonymization (30% effort)

### Day 1-2: Schema Definition
- [ ] Create `config/metrics-schema.yaml` from template
- [ ] Define skill_execution_metric fields (12 fields)
- [ ] Define case_execution_metric fields (10 fields)
- [ ] Define anonymization rules (4 rules)
- [ ] Define retention policy (7d/90d/2y)
- [ ] Add version history section
- [ ] Code review + approval

**Acceptance**: YAML parses, all required fields present, no TODOs

### Day 2-3: Anonymization Pipeline
- [ ] Create `AnonymizationPipeline.java` interface
- [ ] Create `AnonymizationRule.java` sealed class (3 rule types)
- [ ] Implement `HashRule` (sha256, deterministic option)
- [ ] Implement `MaskRule` (last octet masking)
- [ ] Implement `RemoveRule` (field deletion)
- [ ] Create `AnonymizationRuleLoader.java` (YAML parsing)
- [ ] Create `PiiDetector.java` (validation)

**Acceptance**: Code compiles, interfaces complete, no mock/stub/TODO

### Day 3-4: Unit Tests
- [ ] Test: User ID hashing (non-deterministic, different each call)
- [ ] Test: Org ID hashing (deterministic, same output)
- [ ] Test: Case ID hashing (non-deterministic)
- [ ] Test: IP masking (last octet masked)
- [ ] Test: PII detection (regex scanning)
- [ ] Test: Rule application order
- [ ] Test: Empty/null field handling
- [ ] Test: Error propagation

**Acceptance**: All tests GREEN, >90% coverage of AnonymizationPipeline

### Day 4-5: Integration Tests
- [ ] Test: Anonymize raw skill metric
- [ ] Test: Anonymize raw case metric
- [ ] Test: Bulk anonymization (100 metrics)
- [ ] Test: No PII in output (regex scan)
- [ ] Test: Determinism verification (same input → same hash)

**Acceptance**: All tests GREEN, <100ms per metric, no data loss

### Day 5: Code Review & Docs
- [ ] Code review (2 reviewers)
- [ ] JavaDoc for public classes
- [ ] README in marketplace/ directory
- [ ] Design doc crosslink

**Week 1 Success Criteria**:
- ✓ AnonymizationPipeline fully implemented
- ✓ All unit tests GREEN (>90% coverage)
- ✓ Integration tests GREEN
- ✓ No TODOs, mocks, or stubs
- ✓ Code reviewed + documented

---

## Week 2: Collection & Integration (35% effort)

### Day 1-2: Metrics Collector Interface
- [ ] Create `MetricsCollector.java` interface
- [ ] Create `SkillMetricEvent.java` record
- [ ] Create `CaseMetricEvent.java` record
- [ ] Create `MetricsCollectorImpl.java`
- [ ] Implement `collectSkillMetrics()` (async)
- [ ] Implement `collectCaseMetrics()` (async)
- [ ] Add Micrometer counters

**Acceptance**: Compiles, no mock, async confirmed, counters integrated

### Day 2-3: Entity & Repository Layer
- [ ] Create `SkillMetricRaw.java` (JPA entity)
- [ ] Create `SkillMetricAnonymized.java` (JPA entity)
- [ ] Create `CaseMetricRaw.java` (JPA entity)
- [ ] Create `CaseMetricAnonymized.java` (JPA entity)
- [ ] Create `SkillMetricsRepository.java` interface
- [ ] Create `CaseMetricsRepository.java` interface
- [ ] Implement save/query methods

**Acceptance**: Entities map to schema, repositories complete

### Day 3: MCP Server Hook Integration
- [ ] Locate `YawlMcpServer.executeTool()` method
- [ ] Add `@Autowired private MetricsCollector` field
- [ ] Wrap with try/catch for metrics
- [ ] Collect on success: duration, tokens, success=true
- [ ] Collect on error: duration, error_type, success=false
- [ ] Test: Tool execution not blocked
- [ ] Test: Metrics collected asynchronously

**Acceptance**: MCP tests GREEN, hook non-blocking, metrics in DB

### Day 4: A2A Server Hook Integration
- [ ] Locate `YawlA2AServer.handleCaseCompletion()` method
- [ ] Add `@Autowired private MetricsCollector` field
- [ ] Call collector.collectCaseMetrics() (non-blocking)
- [ ] Collect: case_id, case_type, cycle_time, work items, error rate
- [ ] Test: Case completion not blocked
- [ ] Test: Metrics collected asynchronously

**Acceptance**: A2A tests GREEN, hook non-blocking, metrics in DB

### Day 5: Database Migration & Verification
- [ ] Create `V001__init_marketplace_schema.sql` (Flyway)
  - [ ] skill_metrics_raw table (9 columns + audit)
  - [ ] skill_metrics_anonymized table
  - [ ] case_metrics_raw table (8 columns + audit)
  - [ ] case_metrics_anonymized table
  - [ ] Indexes on skill_id, case_type, timestamps
- [ ] Create `V002__create_benchmark_views.sql`
  - [ ] skill_benchmarks_hourly (percentiles)
  - [ ] case_benchmarks_hourly (percentiles)
- [ ] Run migration against test DB
- [ ] Verify schema matches YAML

**Acceptance**: Migration runs clean, schema correct, views queryable

### Day 5: Integration Tests
- [ ] Test: Collect 100 skill metrics (should reach DB)
- [ ] Test: Collect 100 case metrics
- [ ] Test: Concurrent collection (100 threads × 100 events)
- [ ] Test: No data loss (<1%)
- [ ] Test: Performance (<100ms per collection)
- [ ] Test: Raw metrics have all PII fields

**Acceptance**: 10K+ records in DB by end of day, IT GREEN

### Day 6: Async Anonymization Scheduler
- [ ] Create `AnonymizationScheduler.java` Spring component
- [ ] Schedule batch run every 6 hours
- [ ] Query unanonymized raw records
- [ ] Apply anonymization pipeline
- [ ] Move to anonymized tables
- [ ] Mark raw as anonymized=true
- [ ] Test: Scheduler runs (manual trigger + scheduled)
- [ ] Test: Anon metrics have hashes, no PII

**Acceptance**: Anonymization runs, no PII in output, determinism verified

**Week 2 Success Criteria**:
- ✓ 10K+ skill executions collected & stored
- ✓ MCP/A2A hooks integrated & tested
- ✓ Anonymization pipeline scheduled
- ✓ Anonymized tables populated (after 6h or manual run)
- ✓ Integration tests GREEN
- ✓ No data loss (<1%)

---

## Week 3: Aggregation & Queries (25% effort)

### Day 1: Benchmark Query Service
- [ ] Create `BenchmarkQueryService.java` Spring service
- [ ] Implement `getSkillBenchmark()` (PERCENTILE_CONT queries)
  - [ ] p50, p95, p99, avg, success_rate
  - [ ] Count samples
- [ ] Implement `getCaseBenchmark()` (similar aggregates)
- [ ] Implement `getIndustryPercentiles()` (NTILE quartiles)
- [ ] Add error handling + logging
- [ ] No mocks, real SQL queries

**Acceptance**: Compiles, queries execute, <500ms latency

### Day 1-2: Caching Layer
- [ ] Create `BenchmarkQueryCache.java` component
- [ ] Add Redis cache (optional, fallback to in-memory)
- [ ] Cache duration: 1 hour (TTL)
- [ ] Cache keys: `skill:<skillId>`, `case:<caseType>`
- [ ] Implement cache invalidation (optional)
- [ ] Test: Cache hit rate >80%

**Acceptance**: Cache reduces query latency by 50%+

### Day 2-3: REST API Controller
- [ ] Create `BenchmarkController.java`
- [ ] Endpoint: `GET /api/v1/benchmarks/skills/{skillId}`
  - [ ] Response: SkillBenchmarkResponse record
  - [ ] Status: 200 OK or 404 Not Found
- [ ] Endpoint: `GET /api/v1/benchmarks/cases/{caseType}`
  - [ ] Response: CaseBenchmarkResponse record
- [ ] Endpoint: `GET /api/v1/benchmarks/skills/{skillId}/industry`
  - [ ] Response: IndustryPercentileResponse (top/median/bottom 25%)
- [ ] Endpoint: `GET /api/v1/benchmarks/export`
  - [ ] Query params: skillId, caseType, daysBack
  - [ ] Response: CSV file (download)
- [ ] Endpoint: `GET /api/v1/benchmarks/leaderboard`
  - [ ] Return top 10 skills (fastest)
- [ ] Add error handling (404, 500)
- [ ] Add logging (requests, latencies)

**Acceptance**: Endpoints respond correctly, <500ms latency with cache

### Day 3-4: Response Records & DTOs
- [ ] Create `SkillBenchmarkResponse.java` record
- [ ] Create `CaseBenchmarkResponse.java` record
- [ ] Create `IndustryPercentileResponse.java` record with nested quartiles
- [ ] Create `SkillLeaderboardResponse.java` record
- [ ] All records immutable, no setters
- [ ] Add JSON serialization annotations

**Acceptance**: Records serialize to JSON correctly

### Day 4-5: Integration Tests
- [ ] Test: Query skill benchmark (10K metrics in DB)
- [ ] Test: Percentile accuracy (p50, p95, p99 within 1% error)
- [ ] Test: Industry percentiles calculation
- [ ] Test: CSV export format
- [ ] Test: Leaderboard ranking
- [ ] Test: 50 concurrent queries
- [ ] Test: Cache hit rate verification
- [ ] Test: Error cases (404, empty DB)

**Acceptance**: All benchmark queries accurate, <500ms, cache >80% hits

### Day 5-6: Performance Tuning
- [ ] Analyze query plans (EXPLAIN)
- [ ] Add indexes if needed (execution_timestamp, skill_id, case_type)
- [ ] Enable ANALYZE on tables
- [ ] Test: Repeat 50 concurrent queries
- [ ] Measure: Latency, cache hits, CPU usage
- [ ] Document: Query optimization notes

**Acceptance**: <500ms p95 latency, >80% cache hit rate

**Week 3 Success Criteria**:
- ✓ BenchmarkQueryService implemented
- ✓ REST API endpoints working
- ✓ <500ms query latency (SLA met)
- ✓ Caching layer >80% hit rate
- ✓ Integration tests GREEN
- ✓ Performance verified

---

## Week 4: Export, Dashboard & E2E (10% effort)

### Day 1: CSV Export Implementation
- [ ] Implement `MetricsExporter.java` component
- [ ] Method: `exportMetrics(skillId, caseType, daysBack)` → byte[]
- [ ] CSV format: header row + data rows
- [ ] Columns: skill_id, avg_duration_ms, p50, p95, p99, success_rate, samples, date
- [ ] Test: Export produces valid CSV (parseable)
- [ ] Test: No PII in export

**Acceptance**: CSV exports correctly, no PII

### Day 1-2: Dashboard UI (Basic)
- [ ] Create `src/main/resources/static/dashboard.html`
- [ ] Add: Skill benchmark table (top 10)
- [ ] Add: Case benchmark table
- [ ] Add: Industry percentile charts (sparklines or text)
- [ ] Make read-only (no mutations)
- [ ] Add: Export button (triggers CSV download)
- [ ] Add: Simple search by skill name

**Acceptance**: Dashboard displays benchmarks, no errors

### Day 2-3: E2E Test (Full Data Flow)
- [ ] Create `DataMarketplaceE2ETest.java`
- [ ] Step 1: Generate & collect 10K skill metrics
  - [ ] Loop 7 days, 1428 metrics/day
  - [ ] Mix success/failure
  - [ ] Vary duration
- [ ] Step 2: Trigger anonymization (or wait 6h)
  - [ ] Verify anonymization scheduled
  - [ ] Check anon table has records
  - [ ] Verify no PII in anon table (regex scan)
- [ ] Step 3: Query benchmarks
  - [ ] GET /api/v1/benchmarks/skills/test-skill
  - [ ] Verify nSamples >= 10K
  - [ ] Verify p50 < p95 < p99
  - [ ] Verify successRate > 0.97
  - [ ] Time query: <500ms
- [ ] Step 4: Query industry percentiles
  - [ ] GET /api/v1/benchmarks/skills/test-skill/industry
  - [ ] Verify top25 < median < bottom25
- [ ] Step 5: Export CSV
  - [ ] GET /api/v1/benchmarks/export?skillId=test-skill
  - [ ] Verify CSV format
  - [ ] Verify no PII
- [ ] Step 6: Verify PII removal
  - [ ] Query raw table: should have user_id
  - [ ] Query anon table: should have user_id_hash, not user_id
  - [ ] Run regex scan: 0 PII patterns found

**Acceptance**: E2E test GREEN, all checks pass, <500ms latencies

### Day 3-4: Additional Tests
- [ ] Test: Skill comparison (multiple skills)
- [ ] Test: Trend analysis (7-day hourly)
- [ ] Test: Leaderboard ranking
- [ ] Test: Error cases (404, malformed requests)
- [ ] Test: Concurrent requests (50 users)

**Acceptance**: All tests GREEN

### Day 4-5: Documentation
- [ ] Create deployment guide
- [ ] Document: Database setup (migrations)
- [ ] Document: Environment variables (`ANONYMIZATION_SALT`)
- [ ] Document: API endpoints (curl examples)
- [ ] Document: Monitoring setup (Micrometer, alerts)
- [ ] Document: Troubleshooting (common issues)
- [ ] Create README in marketplace/ module

**Acceptance**: Documentation complete, accurate, step-by-step

### Day 5-6: Final Validation
- [ ] Run full test suite (all weeks)
  - [ ] Week 1: Anonymization tests GREEN
  - [ ] Week 2: Collection IT GREEN
  - [ ] Week 3: Query IT GREEN
  - [ ] Week 4: E2E test GREEN
- [ ] Coverage: >80% (check with Jacoco)
- [ ] Static analysis: SpotBugs, PMD clean
- [ ] Build: `mvn clean verify` GREEN
- [ ] Security: No hardcoded secrets, env vars only
- [ ] Performance: <500ms queries verified

**Acceptance**: All tests GREEN, coverage >80%, static analysis clean

### Day 6: Code Review & Merge
- [ ] Code review (2+ reviewers)
- [ ] Address feedback
- [ ] Merge to main branch
- [ ] Tag release: `marketplace-mvp-v1.0.0`
- [ ] Create release notes

**Week 4 Success Criteria**:
- ✓ CSV export working
- ✓ Dashboard UI displays benchmarks
- ✓ E2E test GREEN (full data flow verified)
- ✓ 10K+ records in system, 100% PII removal
- ✓ All tests GREEN (>80% coverage)
- ✓ Documentation complete
- ✓ Deployment ready

---

## Cross-Week Checkpoints

### End of Week 1
- [ ] Schema defined (metrics-schema.yaml)
- [ ] Anonymization pipeline implemented & tested
- [ ] Code reviewed, ready for Week 2

### End of Week 2
- [ ] 10K+ metrics collected in DB
- [ ] MCP/A2A hooks integrated & tested
- [ ] Anonymization running every 6 hours
- [ ] Anonymized tables populated, no PII

### End of Week 3
- [ ] REST API responding (<500ms SLA)
- [ ] Caching >80% hit rate
- [ ] Benchmark accuracy verified
- [ ] Load test: 50 concurrent queries passing

### End of Week 4
- [ ] E2E test passing (10K records, full flow)
- [ ] CSV export working
- [ ] Dashboard UI live
- [ ] Documentation complete
- [ ] All tests GREEN (>80% coverage)
- [ ] Ready for production deployment

---

## Quality Gates (Must Pass Before Merge)

### Code Quality
- [ ] All tests GREEN
- [ ] No failing assertions
- [ ] >80% code coverage (Jacoco)
- [ ] SpotBugs: 0 high-severity bugs
- [ ] PMD: 0 code smells
- [ ] Checkstyle: All rules passed

### Functionality
- [ ] Metrics collected successfully
- [ ] Anonymization removes all PII
- [ ] Queries return correct results
- [ ] API endpoints respond correctly
- [ ] CSV export produces valid format

### Performance
- [ ] Collection latency: <100ms (async)
- [ ] Query latency: <500ms p95 (with cache)
- [ ] Concurrent collection: 100/sec sustained
- [ ] Cache hit rate: >80%
- [ ] Database handles 10K+ records without slowdown

### Security & Privacy
- [ ] Zero PII in anonymized tables (regex scan)
- [ ] Deterministic hashing for org grouping
- [ ] Non-deterministic hashing for user linking
- [ ] No hardcoded secrets (env vars only)
- [ ] HTTPS/TLS enforced (if REST API exposed)

### Documentation
- [ ] README complete
- [ ] API endpoints documented (curl examples)
- [ ] Deployment guide step-by-step
- [ ] Architecture diagram clear
- [ ] Integration points documented

---

## Risk Tracking

### Risk: PII Leak
- **Monitoring**: Automated regex scan on anonymized tables (every commit)
- **Threshold**: 0 PII patterns allowed (fail fast)
- **Recovery**: Rollback + manual audit
- **Status**: [ ] Green (0 PII)

### Risk: High Query Latency
- **Monitoring**: Load test: 50 concurrent queries
- **Threshold**: <500ms p95 (SLA)
- **Recovery**: Add caching, optimize indexes
- **Status**: [ ] Green (<500ms verified)

### Risk: Data Loss During Collection
- **Monitoring**: Compare events generated vs records stored
- **Threshold**: <0.1% loss tolerance
- **Recovery**: Persistent queue + deduplication
- **Status**: [ ] Green (<0.1% loss)

### Risk: Test Coverage Insufficient
- **Monitoring**: Jacoco code coverage report
- **Threshold**: >80% required
- **Recovery**: Add tests for uncovered branches
- **Status**: [ ] Green (>80% coverage)

---

## Deployment Checklist (Production)

### Pre-Deployment
- [ ] All tests GREEN
- [ ] Code reviewed + merged
- [ ] Database migrations reviewed
- [ ] Environment variables set (`ANONYMIZATION_SALT`, Redis, PostgreSQL)
- [ ] Backups of existing data
- [ ] Rollback plan documented

### Deployment
- [ ] Run database migrations (Flyway)
- [ ] Deploy application (yawl-marketplace module)
- [ ] Verify API endpoints accessible
- [ ] Test: Create a skill metric, verify collected
- [ ] Test: Query benchmark (should show metrics)

### Post-Deployment
- [ ] Monitor: Metrics collection rate (>0/min expected)
- [ ] Monitor: Query latency (<500ms p95)
- [ ] Monitor: Error rates (<1% expected)
- [ ] Monitor: PII detector alerts (should be 0)
- [ ] Check logs for errors or warnings
- [ ] Document: Deployment timestamp, version deployed
- [ ] Notify team: Marketplace MVP live

---

## Final Verification (Week 4, Day 6)

Run these commands to confirm success:

```bash
# 1. Check database has metrics
psql -U yawl -d yawl -c \
  "SELECT COUNT(*) as raw_metrics FROM skill_metrics_raw;" \
  "SELECT COUNT(*) as anon_metrics FROM skill_metrics_anonymized;"
# Expected: 10000+ raw and anon metrics

# 2. Check anonymization worked
psql -U yawl -d yawl -c \
  "SELECT COUNT(*) as pii_count FROM skill_metrics_anonymized WHERE user_id_hash ~ '^[a-z0-9-]{36}$';"
# Expected: 0 (no raw UUIDs in anon table)

# 3. Query benchmark API
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/benchmarks/skills/openai-gpt4
# Expected: 200 OK, JSON response, <500ms latency

# 4. Run full test suite
mvn clean verify -pl yawl-marketplace
# Expected: All tests GREEN, coverage >80%

# 5. Check deployment
curl http://localhost:8080/actuator/health
# Expected: 200 OK, status=UP
```

---

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Project Lead | TBD | 2026-02-21 | [ ] |
| Tech Lead | TBD | 2026-02-21 | [ ] |
| QA Lead | TBD | 2026-02-21 | [ ] |
| DevOps | TBD | 2026-02-21 | [ ] |

---

## Summary

**Total Tasks**: ~100 line items
**Critical Path**: Week 1 (schema) → Week 2 (collection) → Week 3 (queries) → Week 4 (export + tests)
**Success Metric**: All checkboxes checked, all tests GREEN, <500ms queries

**Go/No-Go Decision**: Check this document end of Week 4. All GREEN = ready for production.

