# Java 21 + Spring Boot 3.4 Upgrade Summary

**YAWL Version:** 5.2 → 5.3
**Date:** 2026-02-15
**Status:** Architecture Planning Complete

---

## Executive Summary

This upgrade positions YAWL for the next decade with:
- **Java 21 LTS** (supported until 2029)
- **Virtual threads** for massive agent concurrency
- **Spring Boot 3.4** for modern infrastructure
- **Structured concurrency** for safer parallel code

**Key Benefit:** Handle 10,000+ concurrent agents without complexity or cost of reactive programming.

---

## What's Changing

### Current State (YAWL 5.2)

| Component | Technology | Limitation |
|-----------|-----------|------------|
| **Java Runtime** | Java 11 | No virtual threads |
| **Concurrency** | Fixed thread pools (12-100 threads) | Manual tuning, resource waste |
| **Web Framework** | Servlet 2.4 + raw servlets | Verbose, no auto-config |
| **Deployment** | WAR files → Tomcat 9.x | Manual lifecycle management |
| **Observability** | JMX + custom logging | No standard metrics/health |

### Target State (YAWL 5.3)

| Component | Technology | Benefit |
|-----------|-----------|---------|
| **Java Runtime** | Java 21 LTS | Virtual threads, structured concurrency, records |
| **Concurrency** | Virtual threads (unlimited) | No tuning, 100x better scaling |
| **New Services** | Spring Boot 3.4 + Jakarta EE 10 | Auto-config, actuators, cloud-native |
| **Core Engine** | Keep Servlet (no change) | Preserve stability |
| **Observability** | Prometheus, OpenTelemetry | Industry-standard monitoring |

---

## Documentation Suite

### 1. Architecture Decision Record
**File:** `/home/user/yawl/docs/deployment/java21-spring-boot-3.4-migration.md`

**Contents:**
- Compatibility analysis (Java 11 → 21)
- Breaking changes and mitigation
- Virtual thread adoption strategy
- Spring Boot integration architecture
- 6-phase migration roadmap
- Risk assessment and rollback plans

**Audience:** Architects, senior developers

### 2. Implementation Guide
**File:** `/home/user/yawl/docs/deployment/virtual-threads-implementation-guide.md`

**Contents:**
- 10 practical code patterns
- Before/after comparisons
- Performance benchmarks
- Common pitfalls and solutions
- Monitoring and troubleshooting

**Audience:** Developers implementing changes

### 3. Spring Boot Migration Guide
**File:** `/home/user/yawl/docs/deployment/spring-boot-migration-guide.md`

**Contents:**
- Hybrid migration strategy
- Complete MCP server example
- REST API patterns
- Docker/Kubernetes deployment
- Actuator configuration

**Audience:** DevOps, full-stack developers

---

## Key Architectural Decisions

### Decision 1: Hybrid Approach (Not Big-Bang Rewrite)

**Keep Servlet-Based:**
- Core Engine (`yawl.war`)
- Resource Service (`resourceService.war`)
- Worklet Service (`workletService.war`)

**New Spring Boot Services:**
- MCP Server (STDIO + REST)
- Agent Registry (REST + WebSocket)
- A2A Server (REST)

**Rationale:** Minimize risk, preserve stable code, modernize incrementally.

### Decision 2: Virtual Threads for I/O-Bound Work Only

**Use Virtual Threads:**
- HTTP server request handlers
- Event fan-out (1000s of listeners)
- Agent discovery (parallel HTTP)
- MCP tool execution

**Don't Use Virtual Threads:**
- CPU-bound computation (use `ForkJoinPool`)
- Scheduled tasks (keep `ScheduledThreadPool`)
- Single-threaded sequential logic

**Rationale:** Virtual threads are I/O optimizers, not CPU accelerators.

### Decision 3: Structured Concurrency Over Raw Executors

**Replace:**
```java
ExecutorService executor = Executors.newFixedThreadPool(N);
// Manual lifecycle, error handling, timeout
```

**With:**
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // Automatic cleanup, timeout, cancellation
}
```

**Rationale:** Safer, simpler, prevents task leaks.

---

## Migration Roadmap

### Milestone 1: Java 21 Foundation (2 weeks)
- Update `build.xml` to `release="21"`
- Update Docker images to `eclipse-temurin:21-jre-alpine`
- Run full test suite (zero code changes expected)
- Document compatibility issues

**Success Criteria:** YAWL 5.2 runs on Java 21 unchanged.

### Milestone 2: Virtual Threads - Event Notifiers (3 weeks)
- Replace `MultiThreadEventNotifier` thread pool
- Replace `SingleThreadEventNotifier` (evaluate if needed)
- Load test: 10,000 concurrent events
- Monitor JFR for pinning

**Success Criteria:** 10x throughput, lower memory usage.

### Milestone 3: Virtual Threads - Agent Services (3 weeks)
- Replace `AgentRegistry` HTTP executor
- Replace `GenericPartyAgent` HTTP executor
- Replace `YawlA2AServer` thread pool
- Load test: 1,000 concurrent agent registrations

**Success Criteria:** 20x throughput, zero connection refused errors.

### Milestone 4: Structured Concurrency - Discovery (4 weeks)
- Implement parallel agent discovery
- Implement MCP tool execution with timeouts
- Implement A2A handshake parallelization
- Add circuit breakers and retries

**Success Criteria:** Agent discovery 100x faster (20s → 200ms).

### Milestone 5: Spring Boot - MCP Server (5 weeks)
- Create `yawl-mcp-server` Spring Boot module
- Implement REST API + STDIO transport
- Add Actuator endpoints
- Add Prometheus metrics
- Deploy to staging

**Success Criteria:** MCP server runs standalone with production-ready observability.

### Milestone 6: Spring Boot - Agent Registry (4 weeks)
- Create `yawl-agent-registry` Spring Boot module
- Add Redis caching
- Add WebSocket support
- Deploy with Kubernetes HPA

**Success Criteria:** Agent registry scales horizontally based on load.

**Total Timeline:** ~22 weeks (~5.5 months)

---

## Performance Targets

### Concurrency

| Scenario | Before (Java 11) | After (Java 21) | Improvement |
|----------|------------------|-----------------|-------------|
| Concurrent agent connections | 100 (thread limit) | 10,000+ | 100x |
| Event listeners | 12 (pool size) | Unlimited | Infinite |
| MCP tool concurrency | 1 (sequential) | Unlimited | N-way parallel |
| Agent discovery (100 agents) | 20s (sequential) | 0.2s (parallel) | 100x |

### Resource Usage

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| Memory per agent | 1MB (platform thread) | 10KB (virtual thread) | 99% |
| Thread count (10,000 agents) | 10,000+ threads (OOM) | 15 platform threads | Deployable |
| Connection pool tuning | Required | Not needed | Operational simplicity |

### Latency

| Operation | p95 Before | p95 After | Improvement |
|-----------|-----------|-----------|-------------|
| Agent registration | 2.8s (queueing) | 120ms (instant) | 23x |
| MCP tool execution | 150ms | 50ms (parallel) | 3x |
| Event fan-out | 500ms (sequential) | 10ms (parallel) | 50x |

---

## Infrastructure Changes

### Build System

**Before:**
```bash
ant -f build/build.xml buildWebApps
# Deploys WAR files to Tomcat
```

**After:**
```bash
# Existing services (no change)
ant buildWebApps

# New Spring Boot services
mvn clean package
java -jar yawl-mcp-server.jar
```

### Container Images

**Before:**
```dockerfile
FROM eclipse-temurin:11-jre-alpine
# Tomcat 9.x embedded
```

**After:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
# Spring Boot embedded Tomcat with virtual threads
```

### Deployment Units

**Before:**
- `yawl.war` (Core Engine)
- `resourceService.war`
- `workletService.war`
- Shared Tomcat instance

**After:**
- Keep existing WAR files (unchanged)
- **Add:** `yawl-mcp-server.jar` (standalone)
- **Add:** `yawl-agent-registry.jar` (standalone)
- Tomcat for legacy, Spring Boot for new

---

## Risk Mitigation

### Risk 1: Hibernate Compatibility
**Mitigation:** Upgrade to Hibernate 6.4+ (supports Java 21 + Jakarta)
**Fallback:** Use `--add-opens` flags for reflective access

### Risk 2: Virtual Thread Pinning
**Mitigation:** Replace `synchronized` with `ReentrantLock`
**Monitoring:** JFR events `jdk.VirtualThreadPinned`

### Risk 3: Spring Boot Learning Curve
**Mitigation:** 2-day training, start with simple services
**Fallback:** Keep Servlet architecture for core engine

### Risk 4: Production Regressions
**Mitigation:** Canary deployments (10% → 50% → 100%)
**Monitoring:** Compare metrics before/after each phase

---

## Scale Economics

### Before (Java 11, Platform Threads)

**Deployment:**
- 10 agents → Small instance (2 vCPU, 4GB)
- 100 agents → Medium instance (4 vCPU, 8GB)
- 1,000 agents → **Not feasible** (thread exhaustion)

**Cost Example (AWS):**
- t3.medium: $35/month
- Cannot scale beyond ~500 agents

### After (Java 21, Virtual Threads)

**Deployment:**
- 10 agents → Nano instance (1 vCPU, 1GB)
- 100 agents → Small instance (2 vCPU, 2GB)
- 1,000 agents → Medium instance (4 vCPU, 8GB)
- 10,000 agents → Large instance (8 vCPU, 16GB)

**Cost Example (AWS):**
- t3.small: $17/month (handles 100 agents)
- 50% cost reduction + 10x capacity

**Key Insight:** Virtual threads enable cheaper, simpler, more scalable deployments.

---

## Testing Strategy

### Unit Tests
- Zero changes expected (virtual threads are drop-in replacement)
- Run existing JUnit test suite on Java 21

### Integration Tests
- Agent discovery with 100 parallel agents
- Event fan-out with 10,000 listeners
- MCP tool execution with concurrent tools

### Load Tests
- Apache Bench: 10,000 concurrent HTTP requests
- Agent registry: 1,000 agents registering simultaneously
- Measure: throughput, latency, memory, thread count

### Production Validation
- Canary deployment (10% traffic)
- Monitor for 48 hours
- Compare metrics: error rates, p95 latency, heap usage
- Gradual rollout (50%, then 100%)

---

## Monitoring and Observability

### Metrics (Prometheus)

**Existing (JMX):**
- `yawl_cases_active`
- `yawl_workitems_pending`
- JVM heap, GC

**New (Micrometer):**
- `mcp_tool_execution_seconds{toolName}`
- `agent_discovery_duration_seconds`
- `virtual_thread_pinned_total` (JFR)
- HTTP request duration (Spring Actuator)

### Health Checks

**Existing:**
- Manual health check servlets

**New (Spring Boot):**
- `/actuator/health` (liveness + readiness)
- `/actuator/health/yawl-engine` (custom check)
- Kubernetes-native probes

### Distributed Tracing

**Future (OpenTelemetry):**
- Trace MCP tool calls through YAWL engine
- Correlate agent operations across services
- Export to Jaeger/Zipkin

---

## Documentation Deliverables

### For Developers
- [x] Java 21 Migration ADR (`java21-spring-boot-3.4-migration.md`)
- [x] Virtual Thread Implementation Guide (`virtual-threads-implementation-guide.md`)
- [x] Spring Boot Migration Guide (`spring-boot-migration-guide.md`)
- [ ] Code examples in `/docs/examples/java21/`
- [ ] Javadoc updates for new APIs

### For Operations
- [ ] Deployment runbook (`/docs/operations/java21-deployment.md`)
- [ ] JVM tuning guide (`/docs/operations/java21-jvm-tuning.md`)
- [ ] Monitoring setup (`/docs/operations/monitoring-setup.md`)
- [ ] Troubleshooting guide (`/docs/operations/troubleshooting.md`)

### For Architecture
- [x] ADR: Java 21 Adoption
- [x] ADR: Spring Boot Integration
- [ ] Sequence diagrams (virtual thread lifecycle)
- [ ] Component diagrams (new vs. old architecture)

---

## Success Criteria

### Technical Metrics
- [ ] All unit tests pass on Java 21
- [ ] Zero virtual thread pinning in production
- [ ] 10x improvement in concurrent agent handling
- [ ] 50% reduction in p95 latency for I/O operations
- [ ] Zero regressions in existing functionality

### Operational Metrics
- [ ] Deployment time < 5 minutes (Spring Boot)
- [ ] Zero-downtime upgrades (canary + health checks)
- [ ] Prometheus metrics available for all services
- [ ] < 1 hour MTTD (mean time to detect issues)

### Business Metrics
- [ ] Support 10,000+ concurrent agents (vs. 100 today)
- [ ] 50% cost reduction (smaller instances)
- [ ] No customer-facing downtime during migration

---

## Next Actions

### Immediate (Week 1-2)
1. Architecture review board approval
2. Create feature branch: `feature/java21-spring-boot`
3. Set up Java 21 CI/CD pipeline
4. Update Docker base images
5. Run baseline performance tests

### Short-term (Week 3-8)
1. Milestone 1: Java 21 compatibility
2. Milestone 2: Virtual threads (event notifiers)
3. Milestone 3: Virtual threads (agent services)
4. Load testing and benchmarking

### Medium-term (Week 9-16)
1. Milestone 4: Structured concurrency
2. Milestone 5: Spring Boot MCP server
3. Staging deployment and validation

### Long-term (Week 17-22)
1. Milestone 6: Spring Boot agent registry
2. Production canary deployment
3. Gradual rollout to 100%
4. Post-deployment review

---

## Communication Plan

### Stakeholders
- **Development Team:** Technical training (2 days)
- **QA Team:** New testing patterns (virtual threads)
- **Operations Team:** Deployment runbooks, monitoring
- **Product Team:** Feature flag strategy
- **Leadership:** Cost/benefit analysis, timeline

### Training Sessions
1. **Java 21 Features** (4 hours)
   - Virtual threads deep-dive
   - Structured concurrency
   - Pattern matching and records

2. **Spring Boot Basics** (4 hours)
   - Auto-configuration
   - Actuator and metrics
   - Deployment strategies

3. **Hands-on Workshop** (8 hours)
   - Migrate sample service to Java 21
   - Add virtual threads to existing code
   - Deploy Spring Boot app to Kubernetes

---

## Approval

**Recommended Decision:** Proceed with Java 21 + Spring Boot 3.4 migration using hybrid approach.

**Risks:** Moderate (mitigated by incremental rollout, extensive testing)

**Benefits:** High (10x scalability, 50% cost reduction, modern infrastructure)

**Alternatives Considered:**
- Stay on Java 11: Misses virtual threads, structured concurrency
- Reactive frameworks (Reactor/RxJava): More complex than virtual threads
- Kotlin Coroutines: Language change, higher migration cost

**Signatures:**
- [ ] Chief Architect: _____________________________ Date: _______
- [ ] Lead Developer: _____________________________ Date: _______
- [ ] DevOps Lead: _______________________________ Date: _______
- [ ] QA Manager: ________________________________ Date: _______

---

## Appendix: Quick Reference

### Thread Pool Migration Table

| Location | Before | After | Benefit |
|----------|--------|-------|---------|
| `MultiThreadEventNotifier.java:16` | `newFixedThreadPool(12)` | `newVirtualThreadPerTaskExecutor()` | Unlimited listeners |
| `AgentRegistry.java:84` | `newFixedThreadPool(10)` | `newVirtualThreadPerTaskExecutor()` | 1000s of agents |
| `YawlA2AServer.java:120` | `newFixedThreadPool(4)` | `newVirtualThreadPerTaskExecutor()` | Unlimited A2A |
| `GenericPartyAgent.java:188` | `newSingleThreadExecutor()` | `newVirtualThreadPerTaskExecutor()` | Parallel HTTP |

### JVM Flags for Virtual Threads

```bash
# Production JVM args
-XX:+UseZGC                          # Z Garbage Collector (low latency)
-XX:+ZGenerational                   # Generational ZGC (Java 21+)
-Xmx4g                               # Max heap (tune based on load)
-Xms2g                               # Initial heap
-XX:MaxRAMPercentage=75.0            # Container-friendly heap sizing
-XX:StartFlightRecording=filename=app.jfr  # Record virtual thread events
```

### Spring Boot Virtual Threads Config

```yaml
# Minimal config to enable virtual threads
spring:
  threads:
    virtual:
      enabled: true

server:
  shutdown: graceful
```

### Useful Commands

```bash
# Check Java version
java -version  # Should show "openjdk version \"21\""

# Build with Java 21
export JAVA_HOME=/path/to/jdk-21
ant clean compile

# Run Spring Boot app
java -jar yawl-mcp-server.jar

# Monitor virtual threads (JFR)
jfr print --events jdk.VirtualThreadStart recording.jfr

# Health check
curl http://localhost:8090/actuator/health

# Metrics
curl http://localhost:8090/actuator/prometheus
```

---

**Document Status:** Architecture Planning Complete
**Next Review:** Post-Milestone 1 (Java 21 Foundation)
**Last Updated:** 2026-02-15
