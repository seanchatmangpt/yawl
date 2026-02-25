# YAWL v6 Release Roadmap

**Document Version:** 1.0
**Created:** 2026-02-18
**Status:** DRAFT
**Target GA:** 8 weeks from initiation

---

## Executive Summary

This roadmap transforms the v5.2 assessment findings into an actionable 8-week release plan for YAWL v6. The plan prioritizes **critical blockers** (YEngine singleton, in-memory sessions, database versioning) while building on existing strengths (MCP integration, resilience patterns, health checks).

**Current State:**
- Health Score: 100/100 (GREEN)
- MCP Integration: 37 classes, STDIO transport
- A2A Integration: 13 classes, port 8081
- SPIFFE/SPIRE: Implemented but not activated
- Resilience Patterns: CircuitBreaker, RetryPolicy (custom implementation)

**Target State:**
- Java 25 runtime with virtual threads
- Spring Boot 3.x foundation
- Externalized state (Redis sessions)
- OpenTelemetry observability
- Zero-trust identity (SPIFFE/SPIRE active)
- Production-ready multi-cloud deployment

---

## Phase 1: Immediate Next Steps (Weeks 1-2)

**Theme:** Foundation Layer - Eliminate Critical Blockers

### 1.1 Java 25 Upgrade (CRITICAL PATH)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Update `pom.xml` Java version to 25 | engineer | 1 day | `mvn compile` succeeds with JDK 25 | JDK 25 installed |
| Enable compact object headers | engineer | 1 day | `-XX:+UseCompactObjectHeaders` verified in tests | JDK 25 |
| Migrate thread pools to virtual threads | engineer | 3 days | All `Executors.newFixedThreadPool()` replaced | JDK 25 |
| Test ThreadLocal usage for virtual thread safety | tester | 2 days | All tests pass, no thread leakage | Migration complete |
| Update CI/CD pipelines for JDK 25 | integrator | 1 day | GitHub Actions uses JDK 25 | pom.xml updated |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/pom.xml` - Java version property
- `/Users/sac/cre/vendors/yawl/.github/workflows/ci.yml` - CI configuration
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/util/NamedThreadFactory.java` - Thread factory

**Success Gate:**
- [ ] All 295 tests pass on JDK 25
- [ ] Virtual thread migration verified via JFR
- [ ] Memory footprint reduced by 5-10%

### 1.2 Database Versioning with Flyway (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Extract DDL from Hibernate mappings | architect | 2 days | All 73 .hbm.xml files converted to Flyway migrations | None |
| Add Flyway dependency to pom.xml | engineer | 1 day | Flyway 10.x integrated | None |
| Create initial migration (V1__baseline.sql) | engineer | 1 day | Baseline migration creates all tables | DDL extracted |
| Configure Flyway for multi-database support | engineer | 1 day | PostgreSQL, MySQL, H2 profiles work | Migration files created |
| Write migration tests | tester | 2 days | Tests verify schema on clean and migrate | Flyway configured |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/main/resources/db/migration/` - New directory
- `/Users/sac/cre/vendors/yawl/pom.xml` - Flyway dependency
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/resources/*.hbm.xml` - Source mappings

**Success Gate:**
- [ ] Clean database initializes via Flyway
- [ ] Existing databases migrate without data loss
- [ ] Schema version tracked in `flyway_schema_history` table

### 1.3 Dependency Security Updates (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Upgrade PostgreSQL driver to 42.7.x | engineer | 1 day | No security vulnerabilities in Trivy scan | None |
| Upgrade MySQL driver to 8.x | engineer | 1 day | Connection tests pass | None |
| Upgrade SLF4J to 2.0.x | engineer | 1 day | Logging works, no bridge conflicts | None |
| Run OWASP dependency check | validator | 1 day | Zero HIGH/CRITICAL vulnerabilities | Upgrades complete |
| Update Docker base images | integrator | 1 day | Trivy scan clean | Dependencies updated |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/pom.xml` - Dependency versions
- `/Users/sac/cre/vendors/yawl/docker/Dockerfile` - Base image

**Success Gate:**
- [ ] Trivy scan: 0 HIGH/CRITICAL vulnerabilities
- [ ] OWASP dependency check: 0 vulnerabilities > MEDIUM
- [ ] All database connectivity tests pass

### 1.4 OpenTelemetry Integration (MEDIUM PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Add OTel Java agent to Docker image | integrator | 1 day | Agent downloads and configures | None |
| Configure OTLP exporter | integrator | 1 day | Traces export to collector | Agent added |
| Add correlation IDs to logs | engineer | 2 days | Trace context in all log messages | OTel configured |
| Create Grafana dashboards | architect | 2 days | Dashboards for engine metrics | OTel collecting |
| Write OTel integration tests | tester | 2 days | Verify trace propagation | Dashboards created |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/docker/Dockerfile` - OTel agent
- `/Users/sac/cre/vendors/yawl/src/main/resources/otel-collector.yaml` - Collector config
- `/Users/sac/cre/vendors/yawl/docker-compose-otel.yml` - OTel stack

**Success Gate:**
- [ ] Traces visible in Jaeger/Grafana Tempo
- [ ] Metrics exported to Prometheus
- [ ] Log correlation IDs present

---

## Phase 2: Beta Milestone (Weeks 3-4)

**Theme:** State Externalization - Enable Horizontal Scaling

### 2.1 YEngine Singleton Refactor (CRITICAL BLOCKER)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Design stateless YEngine interface | architect | 2 days | Interface A/B contracts preserved | Phase 1 complete |
| Extract YNetRunnerRepository to Spring Bean | engineer | 3 days | Repository works as singleton bean | Design approved |
| Extract YWorkItemRepository to Spring Bean | engineer | 2 days | Repository works as singleton bean | YNetRunner done |
| Extract YSpecificationTable to Spring Bean | engineer | 2 days | Repository works as singleton bean | YWorkItem done |
| Refactor YEngine as facade over beans | engineer | 3 days | All existing tests pass | All extractions done |
| Add comprehensive integration tests | tester | 3 days | 90%+ coverage on new code | Refactor complete |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java` - Main refactor target
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YNetRunnerRepository.java` - New bean
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YWorkItemRepository.java` - New bean

**Success Gate:**
- [ ] YEngine is pure facade (no state)
- [ ] All repositories injectable
- [ ] Interface A/B contracts unchanged
- [ ] All 295+ tests pass

### 2.2 Redis Session Clustering (CRITICAL BLOCKER)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Add Spring Session Redis dependency | engineer | 1 day | Dependency resolves | Phase 2.1 started |
| Create YSessionRepository interface | architect | 1 day | Interface matches existing YSessionCache | None |
| Implement RedisYSessionRepository | engineer | 3 days | Sessions persist to Redis | Interface defined |
| Configure session serialization | engineer | 1 day | YSession serializes correctly | Implementation done |
| Write session failover tests | tester | 2 days | Session survives Redis restart | Serialization done |
| Add Redis to Docker Compose | integrator | 1 day | Redis container in stack | Tests passing |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/authentication/YSessionCache.java` - Current implementation
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/authentication/YSessionRepository.java` - New interface
- `/Users/sac/cre/vendors/yawl/docker-compose.yml` - Redis service

**Success Gate:**
- [ ] Multiple engine instances share sessions
- [ ] Session survives engine restart
- [ ] Redis failover tested

### 2.3 Spring Boot Foundation (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Create Spring Boot starter module | architect | 1 day | Module compiles | None |
| Migrate Hibernate configuration to Spring Data JPA | engineer | 3 days | All entities work via Spring Data | Starter created |
| Add Spring Boot Actuator | engineer | 1 day | `/actuator/health` endpoint works | Foundation done |
| Configure Spring Boot properties | engineer | 1 day | Externalized configuration works | Actuator added |
| Migrate health checks to Actuator | engineer | 2 days | Custom checks integrate with Actuator | Actuator working |
| Create Spring Boot integration tests | tester | 2 days | All tests pass in Spring context | Migration complete |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/yawl-springboot/` - New module
- `/Users/sac/cre/vendors/yawl/src/main/resources/application.yml` - Spring config
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/autonomous/observability/HealthCheck.java` - Migrate to Actuator

**Success Gate:**
- [ ] Spring Boot application starts
- [ ] Actuator endpoints respond
- [ ] All existing functionality preserved

### 2.4 MCP HTTP Transport (MEDIUM PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Add Spring AI MCP dependency | engineer | 1 day | Dependency resolves | Spring Boot done |
| Implement HTTP transport provider | engineer | 2 days | MCP server accepts HTTP connections | Dependency added |
| Add SSE transport support | engineer | 2 days | SSE streaming works | HTTP done |
| Update MCP client configuration | engineer | 1 day | Clients connect via HTTP | Transport done |
| Write MCP transport tests | tester | 2 days | Both STDIO and HTTP work | Implementation done |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java` - Add HTTP transport
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/mcp/transport/` - New transport implementations

**Success Gate:**
- [ ] MCP tools accessible via HTTP
- [ ] SSE streaming functional
- [ ] Backward compatibility with STDIO

---

## Phase 3: RC Milestone (Weeks 5-6)

**Theme:** Zero Trust and Resilience - Production Hardening

### 3.1 SPIFFE/SPIRE Activation (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Create SPIRE server Kubernetes manifests | integrator | 2 days | SPIRE server deployed | Phase 2 complete |
| Create SPIRE agent DaemonSet | integrator | 1 day | Agents on all nodes | Server deployed |
| Integrate SpiffeWorkloadIdentity with Spring Security | engineer | 3 days | X.509 SVID authentication works | Agents running |
| Configure workload registration | integrator | 1 day | YAWL workloads registered | Integration done |
| Write SPIFFE integration tests | tester | 2 days | mTLS verified between services | Registration done |
| Document SPIRE deployment | architect | 1 day | Deployment guide complete | Tests passing |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/spiffe/` - Existing implementations
- `/Users/sac/cre/vendors/yawl/k8s/spire/` - New Kubernetes manifests
- `/Users/sac/cre/vendors/yawl/docker-compose.spiffe.yml` - Existing SPIFFE compose

**Success Gate:**
- [ ] Workloads receive SVIDs automatically
- [ ] mTLS between all services
- [ ] Certificate rotation tested

### 3.2 Resilience4j Migration (MEDIUM PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Add Resilience4j dependencies | engineer | 1 day | Dependencies resolve | Phase 2 complete |
| Migrate CircuitBreaker to Resilience4j | engineer | 2 days | Same behavior, metrics added | Dependencies added |
| Migrate RetryPolicy to Resilience4j | engineer | 2 days | Same behavior, metrics added | CircuitBreaker done |
| Configure Resilience4j metrics | engineer | 1 day | Metrics exported to Prometheus | Migration complete |
| Remove custom resilience classes | engineer | 1 day | No dead code | Metrics working |
| Write resilience tests | tester | 2 days | All resilience scenarios tested | Migration complete |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/autonomous/resilience/CircuitBreaker.java` - Replace
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/autonomous/resilience/RetryPolicy.java` - Replace
- `/Users/sac/cre/vendors/yawl/src/main/resources/resilience4j.yml` - Configuration

**Success Gate:**
- [ ] All resilience patterns use Resilience4j
- [ ] Metrics visible in Grafana
- [ ] No custom resilience code remains

### 3.3 Kubernetes Production Manifests (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Create production namespace and RBAC | integrator | 1 day | Secure RBAC configured | None |
| Create deployment with HPA | integrator | 2 days | Auto-scaling works | RBAC done |
| Create pod disruption budgets | integrator | 1 day | Graceful maintenance | Deployment done |
| Create network policies | integrator | 1 day | Network segmentation | PDB done |
| Create pod security standards | integrator | 1 day | Restricted profile enforced | Network policies |
| Create Helm chart | integrator | 2 days | One-command deployment | All manifests |
| Write deployment validation tests | tester | 2 days | All scenarios tested | Helm chart done |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/k8s/production/` - New production manifests
- `/Users/sac/cre/vendors/yawl/helm/yawl/` - Helm chart

**Success Gate:**
- [ ] HPA scales based on CPU/memory
- [ ] Rolling updates zero-downtime
- [ ] Network isolation verified

### 3.4 API Gateway Integration (MEDIUM PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Create Kong/nginx ingress configuration | integrator | 2 days | Gateway routes traffic | Phase 2 complete |
| Configure rate limiting | integrator | 1 day | Rate limits enforced | Gateway deployed |
| Configure JWT validation | engineer | 2 days | JWT auth at gateway | Rate limiting done |
| Add API versioning | architect | 1 day | v6 API versioned | JWT working |
| Write gateway tests | tester | 2 days | All scenarios tested | Configuration done |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/k8s/ingress/` - Ingress configurations
- `/Users/sac/cre/vendors/yawl/docs/reference/` - API documentation

**Success Gate:**
- [ ] Rate limiting protects backend
- [ ] JWT validation at edge
- [ ] API versioning functional

---

## Phase 4: GA Release (Weeks 7-8)

**Theme:** Validation and Documentation - Production Ready

### 4.1 Performance Benchmarking (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Create JMeter test plans | perf-bench | 2 days | Comprehensive load tests | Phase 3 complete |
| Benchmark virtual thread performance | perf-bench | 2 days | 10x improvement vs platform threads | Test plans done |
| Benchmark horizontal scaling | perf-bench | 2 days | Linear scaling to 10 nodes | Virtual threads done |
| Create performance baseline document | architect | 1 day | Documented baselines | Benchmarks done |
| Tune JVM for production | perf-bench | 1 day | Optimal GC settings | Baseline established |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/tests/performance/` - JMeter tests
- `/Users/sac/cre/vendors/yawl/docs/deployment/performance-tuning.md` - New document

**Success Gate:**
- [ ] 10,000 concurrent cases supported
- [ ] P99 latency < 100ms
- [ ] Linear scaling verified

### 4.2 Chaos Engineering (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Deploy Chaos Mesh | integrator | 1 day | Chaos experiments run | Phase 3 complete |
| Create pod kill experiments | tester | 1 day | Engine survives pod loss | Chaos Mesh deployed |
| Create network partition experiments | tester | 1 day | System degrades gracefully | Pod kill done |
| Create resource exhaustion experiments | tester | 1 day | System recovers | Network partition done |
| Document resilience runbook | architect | 1 day | Runbook complete | All experiments done |
| Run full chaos day | tester | 1 day | All experiments pass | Runbook done |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/k8s/chaos/` - Chaos experiments
- `/Users/sac/cre/vendors/yawl/docs/operations/resilience-runbook.md` - New runbook

**Success Gate:**
- [ ] All chaos experiments pass
- [ ] Runbook documents recovery procedures
- [ ] MTTR documented

### 4.3 Security Audit (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Run full Trivy scan | prod-val | 1 day | Zero HIGH/CRITICAL | Phase 3 complete |
| Run OWASP ZAP DAST | prod-val | 1 day | Zero HIGH findings | Trivy clean |
| Review RBAC configurations | prod-val | 1 day | Least privilege verified | ZAP clean |
| Review network policies | prod-val | 1 day | Segmentation verified | RBAC reviewed |
| Create security documentation | architect | 1 day | Security guide complete | All reviews done |
| External penetration test | prod-val | 2 days | No critical findings | Documentation done |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/docs/security/` - Security documentation
- `/Users/sac/cre/vendors/yawl/.claude/SECURITY-CHECKLIST-JAVA25.md` - Existing checklist

**Success Gate:**
- [ ] All security scans clean
- [ ] Penetration test passed
- [ ] Security documentation complete

### 4.4 Documentation and Release (HIGH PRIORITY)

| Task | Owner | Duration | Success Criteria | Dependencies |
|------|-------|----------|------------------|--------------|
| Update all API documentation | architect | 2 days | OpenAPI specs current | All features complete |
| Create migration guide from v5.x | architect | 2 days | Step-by-step guide | API docs done |
| Create deployment guide | architect | 1 day | Multi-cloud instructions | Migration guide done |
| Update README and CLAUDE.md | architect | 1 day | Documentation current | All guides done |
| Create release notes | architect | 1 day | Comprehensive changelog | All documentation |
| Tag v6.0.0 release | integrator | 1 day | Git tag and GitHub release | All complete |

**Key Files:**
- `/Users/sac/cre/vendors/yawl/docs/README.md` - Main documentation
- `/Users/sac/cre/vendors/yawl/docs/API-REFERENCE.md` - API documentation
- `/Users/sac/cre/vendors/yawl/docs/deployment/migration-v5-to-v6.md` - New migration guide

**Success Gate:**
- [ ] All documentation reviewed and current
- [ ] Migration guide tested
- [ ] Release tagged and published

---

## Critical Path Summary

```
Week 1-2: Java 25 + Flyway + Dependencies + OTel
    |
    v
Week 3-4: YEngine Refactor + Redis Sessions + Spring Boot + MCP HTTP
    |
    v
Week 5-6: SPIFFE/SPIRE + Resilience4j + K8s Production + API Gateway
    |
    v
Week 7-8: Benchmarking + Chaos + Security Audit + Documentation
```

**Critical Dependencies:**
1. Java 25 upgrade enables virtual threads (unlocks scaling)
2. YEngine refactor enables Redis sessions (unlocks horizontal scaling)
3. Spring Boot enables Actuator + Resilience4j (unlocks observability)

---

## Risk Register

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Virtual thread migration breaks ThreadLocal usage | Medium | High | Comprehensive testing, fallback to platform threads | tester |
| YEngine refactor breaks interface contracts | Low | Critical | Preserve interfaces, extensive integration tests | architect |
| Redis session serialization fails | Medium | High | Use Jackson, test all session types | engineer |
| SPIRE deployment complexity | Medium | Medium | Start with single cluster, gradual rollout | integrator |
| Spring Boot migration scope creep | High | Medium | Strict scope, Phase 2 only essentials | architect |
| Performance benchmarks miss targets | Medium | High | Early benchmarking, tuning iterations | perf-bench |

---

## Success Metrics

### Technical Metrics

| Metric | Current | Beta Target | RC Target | GA Target |
|--------|---------|-------------|-----------|-----------|
| Java Version | 11 | 25 | 25 | 25 |
| Test Coverage | 80%+ | 85%+ | 90%+ | 90%+ |
| Health Score | 100 | 100 | 100 | 100 |
| Security Vulnerabilities | Unknown | 0 HIGH | 0 HIGH | 0 HIGH |
| P99 Latency | Unknown | <200ms | <150ms | <100ms |
| Concurrent Cases | Unknown | 1,000 | 5,000 | 10,000 |
| Horizontal Scaling | No | Yes | Yes | Yes |

### Operational Metrics

| Metric | Current | Beta Target | RC Target | GA Target |
|--------|---------|-------------|-----------|-----------|
| Deployment Time | Unknown | <30min | <15min | <10min |
| Rollback Time | Unknown | <15min | <10min | <5min |
| MTTR | Unknown | <30min | <15min | <10min |
| Observability | Basic | Full | Full | Full |
| Documentation | Partial | Complete | Complete | Complete |

---

## Agent Assignment Matrix

| Agent | Primary Responsibilities | Phases |
|-------|-------------------------|--------|
| **engineer** | Code implementation, refactoring, dependency updates | All |
| **architect** | Design, documentation, migration guides | All |
| **tester** | Test writing, coverage, chaos engineering | All |
| **integrator** | CI/CD, Kubernetes, Docker, deployment | All |
| **validator** | Build verification, quality gates | All |
| **prod-val** | Security audits, production validation | 3, 4 |
| **perf-bench** | Performance testing, benchmarking | 4 |
| **reviewer** | Code review, standards compliance | All |

---

## Next Actions

**Week 1 Start (Immediate):**
1. Assign `engineer` to Java 25 upgrade (pom.xml, CI)
2. Assign `architect` to Flyway migration design
3. Assign `integrator` to dependency security updates
4. Schedule daily standups for Phase 1 blockers

**Daily Standup Agenda:**
- Blockers resolved?
- Tests passing?
- Dependencies unblocked?
- Scope creep detected?

---

## Appendix A: Key Files Reference

### Critical Engine Files
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/authentication/YSessionCache.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java`

### Interface Contracts (PRESERVE)
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceBClient.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceA/InterfaceADesign.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceX/InterfaceX_Service.java`

### Integration Points
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/spiffe/SpiffeWorkloadIdentity.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/integration/autonomous/resilience/CircuitBreaker.java`

### Build Configuration
- `/Users/sac/cre/vendors/yawl/pom.xml`
- `/Users/sac/cre/vendors/yawl/.github/workflows/ci.yml`
- `/Users/sac/cre/vendors/yawl/docker-compose.yml`

---

## Appendix B: Dependency Upgrade Matrix

| Dependency | Current | Target | Effort | Risk |
|------------|---------|--------|--------|------|
| Java | 11 | 25 | Medium | Low |
| PostgreSQL Driver | 42.2.8 | 42.7.x | Low | Low |
| MySQL Driver | 5.1.22 | 8.x | Medium | Medium |
| SLF4J | 1.7.12 | 2.0.x | Low | Low |
| Hibernate | 5.6.14 | 6.x | Medium | Medium |
| Spring Boot | None | 3.x | High | Medium |
| Flyway | None | 10.x | Medium | Low |
| Resilience4j | None | 2.x | Medium | Low |
| OpenTelemetry | None | 1.32.x | Low | Low |

---

**Document Status:** Ready for review
**Next Review:** Weekly during active development
**Approval Required:** Technical Lead, Product Owner
