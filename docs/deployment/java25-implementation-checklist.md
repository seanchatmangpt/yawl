# YAWL Java 25 Implementation Checklist

**Project:** YAWL v5.2 Java 25 Upgrade
**Date:** 2026-02-15
**Status:** READY FOR IMPLEMENTATION

---

## Phase 1: Environment Preparation (Day 1-2)

### Java 25 Installation

- [ ] **Install Java 25 on development machines**
  ```bash
  sdk install java 25-tem
  java --version  # Verify: openjdk 25
  ```

- [ ] **Install Java 25 on CI/CD servers**
  ```bash
  # GitHub Actions: uses: actions/setup-java@v4 with java-version: 25
  ```

- [ ] **Install Java 25 in Docker build environment**
  ```bash
  docker pull eclipse-temurin:25-jdk
  docker pull eclipse-temurin:25-jre-alpine
  ```

- [ ] **Update JAVA_HOME environment variables**
  ```bash
  export JAVA_HOME=/path/to/jdk-25
  export PATH=$JAVA_HOME/bin:$PATH
  ```

### Tool Verification

- [ ] **Maven 3.9.6+ installed**
  ```bash
  mvn --version  # Should show Maven 3.9.6 or higher
  ```

- [ ] **Ant 1.10.14+ installed**
  ```bash
  ant -version  # Should show Apache Ant 1.10.14 or higher
  ```

- [ ] **Docker 24.0+ installed**
  ```bash
  docker --version  # Should show 24.0 or higher
  ```

---

## Phase 2: Configuration Updates (Day 2-3)

### Maven Configuration

- [ ] **Update pom.xml compiler properties**
  - File: `/home/user/yawl/pom.xml`
  - Change line 18: `<maven.compiler.source>21</maven.compiler.source>` → `25`
  - Change line 19: `<maven.compiler.target>21</maven.compiler.target>` → `25`
  - Add line 20: `<maven.compiler.release>25</maven.compiler.release>`

- [ ] **Update maven-compiler-plugin configuration**
  - File: `/home/user/yawl/pom.xml`
  - Change line 394: `<source>21</source>` → `<source>25</source>`
  - Change line 395: `<target>21</target>` → `<target>25</target>`
  - Add: `<release>25</release>`
  - Add: `<arg>--enable-preview</arg>` to compilerArgs

- [ ] **Update maven-compiler-plugin version**
  - File: `/home/user/yawl/pom.xml`
  - Change line 392: `<version>3.11.0</version>` → `<version>3.13.0</version>`

- [ ] **Update maven-surefire-plugin for preview features**
  - File: `/home/user/yawl/pom.xml`
  - Add: `<argLine>--enable-preview</argLine>` to configuration

### Ant Configuration

- [ ] **Update build.xml Java version**
  - File: `/home/user/yawl/build/build.xml`
  - Uncomment and update lines 3011-3012:
    ```xml
    <property name="ant.build.javac.source" value="25"/>
    <property name="ant.build.javac.target" value="25"/>
    ```

- [ ] **Add release property**
  - File: `/home/user/yawl/build/build.xml`
  - Add: `<property name="ant.build.javac.release" value="25"/>`

- [ ] **Update javac tasks with preview support**
  - File: `/home/user/yawl/build/build.xml`
  - Add `--enable-preview` to javac compilerarg in all compile targets

### Dockerfile Updates

- [ ] **Update main Dockerfile**
  - File: `/home/user/yawl/Dockerfile`
  - Line 4: `FROM eclipse-temurin:21-jre-alpine` → `eclipse-temurin:25-jre-alpine`
  - Add to JAVA_OPTS: `--enable-preview`

- [ ] **Update development Dockerfile**
  - File: `/home/user/yawl/Dockerfile.dev`
  - Line 1: `FROM eclipse-temurin:21-jdk` → `eclipse-temurin:25-jdk`

- [ ] **Update build Dockerfile**
  - File: `/home/user/yawl/Dockerfile.build`
  - Line 4: `FROM eclipse-temurin:17-jdk AS builder` → `eclipse-temurin:25-jdk`
  - Line 28: `FROM eclipse-temurin:17-jre AS runtime` → `eclipse-temurin:25-jre`

- [ ] **Update containerization Dockerfiles**
  - File: `/home/user/yawl/containerization/Dockerfile.base`
    - Line 10: `FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine` with default ARG JAVA_VERSION=25
    - Line 54: `FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine`

  - File: `/home/user/yawl/containerization/Dockerfile.engine`
    - Line 8: `FROM eclipse-temurin:17-jdk AS builder` → `eclipse-temurin:25-jdk`
    - Line 41: `FROM eclipse-temurin:17-jre AS runtime` → `eclipse-temurin:25-jre`

  - File: `/home/user/yawl/containerization/Dockerfile.resourceService`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

  - File: `/home/user/yawl/containerization/Dockerfile.workletService`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

  - File: `/home/user/yawl/containerization/Dockerfile.monitorService`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

  - File: `/home/user/yawl/containerization/Dockerfile.costService`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

  - File: `/home/user/yawl/containerization/Dockerfile.schedulingService`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

  - File: `/home/user/yawl/containerization/Dockerfile.balancer`
    - Update all eclipse-temurin:17 → eclipse-temurin:25

- [ ] **Update Oracle Cloud Dockerfiles**
  - File: `/home/user/yawl/ci-cd/oracle-cloud/Dockerfile.engine`
    - Update all eclipse-temurin references to version 25

  - File: `/home/user/yawl/ci-cd/oracle-cloud/Dockerfile.resource`
    - Update all eclipse-temurin references to version 25

  - File: `/home/user/yawl/ci-cd/oracle-cloud/Dockerfile.worklet`
    - Update all eclipse-temurin references to version 25

### CI/CD Configuration

- [ ] **Update GitHub Actions workflow**
  - File: `/home/user/yawl/.github/workflows/unit-tests.yml`
  - Add Java 25 to test matrix:
    ```yaml
    strategy:
      matrix:
        java-version: [21, 25]
    ```
  - Add preview feature support:
    ```yaml
    env:
      MAVEN_OPTS: "--enable-preview"
    ```

- [ ] **Create Java version compatibility test**
  - Add test to verify both Java 21 and Java 25 work

---

## Phase 3: Virtual Thread Migration (Day 3-5)

### High Priority Thread Pool Replacements

- [ ] **MultiThreadEventNotifier.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/stateless/engine/MultiThreadEventNotifier.java`
  - Line 16: Replace `Executors.newFixedThreadPool(12)` with `Executors.newVirtualThreadPerTaskExecutor()`
  - Remove: `private static final int THREAD_POOL_SIZE = 12;`
  - Test: Event fan-out with 10,000 listeners

- [ ] **ObserverGatewayController.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/ObserverGatewayController.java`
  - Line 47: Remove `private static final int THREADPOOL_SIZE = Runtime.getRuntime().availableProcessors();`
  - Line 54: Replace `Executors.newFixedThreadPool(THREADPOOL_SIZE)` with `Executors.newVirtualThreadPerTaskExecutor()`
  - Test: Parallel gateway notifications

- [ ] **YawlA2AServer.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`
  - Line 120: Replace `Executors.newFixedThreadPool(4)` with `Executors.newVirtualThreadPerTaskExecutor()`
  - Line 141: Replace `httpServer.setExecutor(executorService)` to use virtual threads
  - Test: 1000 concurrent A2A agent connections

- [ ] **AgentRegistry.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/registry/AgentRegistry.java`
  - Replace fixed thread pool with virtual thread executor
  - Test: Unlimited concurrent agent registrations

### Medium Priority Thread Pool Replacements

- [ ] **InterfaceB_EngineBasedClient.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EngineBasedClient.java`
  - Review for thread pool usage
  - Replace with virtual threads if applicable

- [ ] **InterfaceB_EnvironmentBasedServer.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EnvironmentBasedServer.java`
  - Review for thread pool usage
  - Replace with virtual threads if applicable

- [ ] **EventLogger.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/logging/YEventLogger.java`
  - Replace sequential logging with parallel virtual thread logging

- [ ] **WorkItemCache.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/resourcing/datastore/WorkItemCache.java`
  - Review for synchronized blocks with I/O
  - Replace with ReentrantLock if pinning detected

### Structured Concurrency Implementation

- [ ] **Create ParallelAgentDiscovery.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/ParallelAgentDiscovery.java`
  - Implement parallel agent discovery using StructuredTaskScope
  - Add timeout protection (5 seconds default)
  - Test: 100 agents discovered in < 1 second

- [ ] **Create StructuredMcpToolExecutor.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/StructuredMcpToolExecutor.java`
  - Implement MCP tool execution with timeouts
  - Add automatic cancellation on timeout
  - Test: 30-second timeout enforcement

- [ ] **Update GenericPartyAgent.java**
  - File: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java`
  - Migrate to ParallelAgentDiscovery for agent discovery
  - Test: 20 seconds → 200ms for 100 agents

---

## Phase 4: Pinning Detection and Remediation (Day 5-6)

### Audit Synchronized Blocks

- [ ] **Run synchronized block audit**
  ```bash
  grep -rn "synchronized\s*(" src/ --include="*.java" > synchronized-audit.txt
  ```

- [ ] **Identify synchronized blocks with I/O**
  ```bash
  grep -rn "synchronized.*{" src/ --include="*.java" -A 20 | \
    grep -E "(read|write|fetch|query|execute|sleep)" > sync-io-audit.txt
  ```

### Fix High-Impact Pinning

- [ ] **WorkItemCache.java - Replace synchronized with ReentrantLock**
  - If applicable based on audit

- [ ] **SessionManager.java - Replace synchronized with ReentrantLock**
  - If applicable based on audit

- [ ] **ResourceCache.java - Replace synchronized with ReentrantLock**
  - If applicable based on audit

### Enable Pinning Detection

- [ ] **Add JVM flag for pinning detection**
  - File: `/home/user/yawl/Dockerfile`
  - Add to JAVA_OPTS: `-Djdk.tracePinnedThreads=short`

- [ ] **Create monitoring script**
  ```bash
  #!/bin/bash
  # File: scripts/monitor-pinning.sh
  echo "Monitoring virtual thread pinning events..."
  tail -f logs/yawl.log | grep "monitors:"
  ```

---

## Phase 5: Testing and Validation (Day 6-8)

### Unit Tests

- [ ] **Run full unit test suite with Java 25**
  ```bash
  mvn clean test -Djava.version=25
  ```

- [ ] **Run virtual thread scalability tests**
  ```bash
  mvn test -Dtest=VirtualThreadScalabilityTest
  ```

- [ ] **Run high concurrency tests**
  ```bash
  mvn test -Dtest=VirtualThreadScalabilityTest -DtaskCount=100000
  ```

### Integration Tests

- [ ] **Test event fan-out with 10,000 listeners**
  ```bash
  curl -X POST http://localhost:8080/yawl/test/event-fanout -d "listener_count=10000"
  ```

- [ ] **Test A2A server with 1,000 concurrent agents**
  ```bash
  ab -n 1000 -c 1000 http://localhost:8081/
  ```

- [ ] **Test agent discovery with 100 agents**
  ```bash
  time curl -X POST http://localhost:9090/agents/discover -d '{"agent_count": 100}'
  ```

### Load Testing

- [ ] **HTTP load test (before migration baseline)**
  ```bash
  ab -n 10000 -c 100 http://localhost:8080/yawl/ib > baseline-java21.txt
  ```

- [ ] **HTTP load test (after migration)**
  ```bash
  ab -n 10000 -c 1000 http://localhost:8080/yawl/ib > results-java25.txt
  ```

- [ ] **Compare results (expect 10x improvement)**
  ```bash
  diff baseline-java21.txt results-java25.txt
  ```

### Performance Validation

- [ ] **Verify virtual thread metrics**
  ```bash
  curl http://localhost:8080/actuator/metrics/jvm.threads.live
  # Platform threads should be < 100
  ```

- [ ] **Verify memory reduction**
  ```bash
  curl http://localhost:8080/actuator/metrics/jvm.memory.used
  # Memory should be 50-90% lower for same workload
  ```

- [ ] **Check for pinning events**
  ```bash
  grep "monitors:" logs/yawl.log | wc -l
  # Should be 0 or minimal
  ```

### Java Flight Recorder Analysis

- [ ] **Record JFR data**
  ```bash
  java -XX:StartFlightRecording=filename=yawl-java25.jfr,settings=profile \
       -jar target/yawl-5.2.jar
  ```

- [ ] **Analyze virtual thread events**
  ```bash
  jfr print --events jdk.VirtualThreadStart yawl-java25.jfr | head -20
  jfr print --events jdk.VirtualThreadPinned yawl-java25.jfr
  ```

- [ ] **Generate summary report**
  ```bash
  jfr summary yawl-java25.jfr > jfr-summary.txt
  ```

---

## Phase 6: Documentation and Training (Day 8-9)

### Documentation Updates

- [ ] **Update README.md with Java 25 requirement**
  - File: `/home/user/yawl/README.md`
  - Add: "Requires Java 25 or Java 21 LTS"

- [ ] **Update prerequisites.md**
  - File: `/home/user/yawl/docs/deployment/prerequisites.md`
  - Update Java version requirements to Java 25/21

- [ ] **Create java25-upgrade-guide.md**
  - File: `/home/user/yawl/docs/deployment/java25-upgrade-guide.md`
  - Status: ✅ COMPLETE

- [ ] **Update virtual-threads-implementation-guide.md**
  - File: `/home/user/yawl/docs/deployment/virtual-threads-implementation-guide.md`
  - Add Java 25-specific optimizations

### Code Documentation

- [ ] **Add JavaDoc for virtual thread methods**
  ```java
  /**
   * Processes events using virtual threads for unlimited concurrency.
   *
   * @apiNote This method uses {@link Executors#newVirtualThreadPerTaskExecutor()}
   *          which provides lightweight threads optimized for I/O-bound operations.
   *          Each listener is notified concurrently without blocking.
   *
   * @param listeners Set of event listeners
   * @param event Event to announce
   * @since 5.2 (Java 25)
   */
  ```

- [ ] **Add @since tags for new virtual thread code**

- [ ] **Update class-level documentation for migrated classes**

### Training Materials

- [ ] **Create virtual threads best practices guide**
  - When to use virtual threads vs platform threads
  - Avoiding pinning pitfalls
  - Structured concurrency patterns

- [ ] **Create troubleshooting guide**
  - Common issues and solutions
  - Debugging virtual thread problems
  - Performance tuning tips

---

## Phase 7: Deployment Preparation (Day 9-10)

### Staging Environment

- [ ] **Deploy to staging with Java 25**
  ```bash
  kubectl apply -f k8s/staging/yawl-java25-deployment.yaml
  ```

- [ ] **Run smoke tests**
  ```bash
  ./scripts/smoke-test.sh staging
  ```

- [ ] **Load test staging environment**
  ```bash
  ab -n 100000 -c 1000 https://staging.yawl.example.com/yawl/ib
  ```

- [ ] **Monitor for 24 hours**
  - Check error rates
  - Monitor memory usage
  - Verify no pinning events

### Production Readiness

- [ ] **Create rollback plan**
  - Document: `/home/user/yawl/docs/deployment/java25-rollback-plan.md`
  - Test rollback procedure

- [ ] **Prepare dual-version deployment**
  - Java 21 pod group (fallback)
  - Java 25 pod group (primary)

- [ ] **Update production Kubernetes manifests**
  - File: `k8s/production/yawl-deployment.yaml`
  - Update image tag to java25

- [ ] **Configure feature flags**
  - Enable virtual threads in production
  - Add kill switch for rollback

### Monitoring Setup

- [ ] **Configure Prometheus alerts**
  ```yaml
  - alert: VirtualThreadPinning
    expr: increase(yawl_virtual_thread_pinned_total[1h]) > 100
    for: 5m
    labels:
      severity: warning
  ```

- [ ] **Create Grafana dashboards**
  - Virtual thread metrics
  - Platform thread comparison
  - Memory usage trends

- [ ] **Set up log aggregation**
  - Filter for pinning events
  - Track virtual thread creation rate

---

## Phase 8: Production Deployment (Day 10-11)

### Pre-Deployment Checks

- [ ] **Verify Java 25 on production nodes**
  ```bash
  kubectl exec -it yawl-engine-0 -- java --version
  ```

- [ ] **Verify Docker images built with Java 25**
  ```bash
  docker inspect yawl-engine:5.2-java25 | grep -i java
  ```

- [ ] **Run pre-deployment tests**
  ```bash
  ./scripts/pre-deploy-checks.sh production
  ```

### Canary Deployment

- [ ] **Deploy 10% traffic to Java 25 pods**
  ```bash
  kubectl apply -f k8s/canary/yawl-java25-canary.yaml
  ```

- [ ] **Monitor canary metrics for 1 hour**
  - Error rate
  - Response time
  - Memory usage

- [ ] **Increase to 50% traffic**
  ```bash
  kubectl patch deployment yawl-engine -p '{"spec":{"replicas":5}}'
  ```

- [ ] **Monitor for 2 hours**

### Full Deployment

- [ ] **Deploy Java 25 to all pods**
  ```bash
  kubectl set image deployment/yawl-engine yawl=yawl-engine:5.2-java25
  ```

- [ ] **Verify rollout**
  ```bash
  kubectl rollout status deployment/yawl-engine
  ```

- [ ] **Run production smoke tests**
  ```bash
  ./scripts/smoke-test.sh production
  ```

### Post-Deployment Validation

- [ ] **Verify virtual threads active**
  ```bash
  curl https://prod.yawl.example.com/actuator/metrics/jvm.threads.live
  ```

- [ ] **Check performance metrics**
  - Throughput: Should be 10x higher
  - Latency: Should be lower or same
  - Memory: Should be 50-90% lower

- [ ] **Monitor for 48 hours**
  - No error rate increase
  - No memory leaks
  - No pinning events

---

## Phase 9: Optimization and Tuning (Day 12-14)

### Performance Tuning

- [ ] **Tune virtual thread scheduler parallelism**
  ```bash
  # Adjust based on CPU count
  -Djdk.virtualThreadScheduler.parallelism=64
  ```

- [ ] **Optimize database connection pool**
  - Virtual threads make small pools efficient
  - Test reducing pool size by 50%

- [ ] **Review and optimize rate limiting**
  - Identify operations that need semaphore protection
  - Add rate limiting where appropriate

### Code Optimization

- [ ] **Migrate ThreadLocal to ScopedValue**
  - Identify all ThreadLocal usage
  - Migrate to ScopedValue for virtual thread optimization

- [ ] **Adopt pattern matching**
  - Replace instanceof-cast patterns
  - Use switch pattern matching

- [ ] **Adopt record patterns**
  - Simplify data extraction from records

### Monitoring Optimization

- [ ] **Fine-tune alerts**
  - Adjust thresholds based on production data
  - Reduce false positives

- [ ] **Optimize metrics collection**
  - Add custom virtual thread metrics
  - Track key performance indicators

---

## Phase 10: Knowledge Transfer and Closure (Day 14-15)

### Team Training

- [ ] **Conduct virtual threads workshop**
  - Hands-on coding exercises
  - Best practices review
  - Q&A session

- [ ] **Document lessons learned**
  - What went well
  - Challenges faced
  - Solutions implemented

### Project Closure

- [ ] **Final performance report**
  - Before/after comparison
  - ROI analysis
  - Success metrics

- [ ] **Update project documentation**
  - Architecture diagrams
  - API documentation
  - Operations runbooks

- [ ] **Archive Java 21 artifacts**
  - Keep for rollback if needed
  - Document retention policy

- [ ] **Celebrate success!**
  - Share results with team
  - Recognize contributors
  - Plan next optimizations

---

## Success Criteria

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| **Compilation Success** | 100% with Java 25 | | ⏳ |
| **Unit Tests Pass** | 100% pass rate | | ⏳ |
| **HTTP Throughput** | 10x improvement | | ⏳ |
| **Memory Reduction** | 50-90% lower | | ⏳ |
| **Zero Pinning Events** | < 10 per day | | ⏳ |
| **No Error Rate Increase** | < 0.1% change | | ⏳ |
| **Production Uptime** | 99.9%+ | | ⏳ |

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Java 25 bugs | Low | High | Test on Java 21 fallback, keep rollback ready |
| Pinning performance degradation | Medium | Medium | Monitor with JFR, replace synchronized blocks |
| Breaking changes in dependencies | Low | Medium | Test all integrations thoroughly |
| Production deployment failure | Low | High | Canary deployment, automated rollback |
| Team unfamiliarity | Medium | Low | Training sessions, documentation |

---

## Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| 1. Environment Setup | 1-2 days | Java 25 installed everywhere |
| 2. Configuration | 1 day | All configs updated to Java 25 |
| 3. Virtual Thread Migration | 2-3 days | All thread pools migrated |
| 4. Pinning Remediation | 1 day | Zero pinning events |
| 5. Testing | 2-3 days | All tests passing, performance validated |
| 6. Documentation | 1 day | Complete upgrade guide |
| 7. Deployment Prep | 1 day | Staging validated, rollback tested |
| 8. Production Deployment | 1-2 days | Java 25 in production |
| 9. Optimization | 2-3 days | Peak performance achieved |
| 10. Closure | 1 day | Documentation complete |

**Total Timeline:** 12-15 business days

---

## Notes

- All file paths are absolute from `/home/user/yawl/`
- Backup all configuration files before making changes
- Test each change incrementally
- Keep Java 21 builds available for rollback
- Monitor production closely for first 48 hours

---

**Checklist Version:** 1.0.0
**Last Updated:** 2026-02-15
**Owner:** YAWL Architecture Team
