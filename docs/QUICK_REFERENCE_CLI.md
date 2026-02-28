# YAWL CLI Quick Reference — All Commands at a Glance

**TL;DR**: Copy-paste commands for every common development task.

---

## Build System: dx.sh

The main build script (`scripts/dx.sh`) orchestrates Maven with smart change detection, test sharding, and Java 25 optimization.

### Build Commands (Most Common)

```bash
# Fast compile (just code, no tests) — ~60 seconds
bash scripts/dx.sh compile

# Compile + run tests for changed modules — ~2-5 minutes
bash scripts/dx.sh

# Compile + run ALL tests (pre-commit) — ~10-15 minutes
bash scripts/dx.sh all

# Build specific module(s) only
bash scripts/dx.sh -pl yawl-engine
bash scripts/dx.sh -pl yawl-engine,yawl-webapps

# Compile only (skip tests) — ~30 seconds
mvn clean compile
```

### Build Phases

```bash
# PHASE 1: Compile only (fail fast on syntax errors)
bash scripts/dx.sh compile

# PHASE 2: Compile + Unit tests (< 5 min)
bash scripts/dx.sh  # default behavior

# PHASE 3: Compile + All tests (integration, perf)
bash scripts/dx.sh all

# PHASE 4: Package (create JARs/WARs)
mvn clean package -DskipTests
```

### Advanced Build Options

```bash
# Enable test impact graph (skip tests for unaffected code)
DX_IMPACT=1 bash scripts/dx.sh

# Enable bytecode warm cache (faster recompiles)
DX_WARM_CACHE=1 bash scripts/dx.sh

# Run fast feedback-tier tests only (smoke tests < 5 sec)
DX_FEEDBACK=1 bash scripts/dx.sh

# Offline mode (no network, use local .m2 cache)
DX_OFFLINE=1 bash scripts/dx.sh

# Stateless test execution with H2 snapshots
DX_STATELESS=1 bash scripts/dx.sh

# Fail fast at specific test tier (1=unit, 2=integration, 3=system)
TEP_FAIL_FAST_TIER=2 bash scripts/dx.sh

# Combine multiple options
DX_IMPACT=1 DX_WARM_CACHE=1 bash scripts/dx.sh all
```

### Environment Variables

```bash
# Force specific Java version (must be Java 25+)
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64

# Enable remote cache (CI/CD)
export CLAUDE_CODE_REMOTE=true

# Offline mode
export DX_OFFLINE=1
```

---

## Maven Commands (Raw)

If you prefer Maven directly (slower, more verbose):

```bash
# Compile all modules
mvn clean compile

# Compile + test changed modules
mvn clean test

# Compile + test all modules
mvn clean verify

# Package (create JARs)
mvn clean package -DskipTests

# Package specific module
mvn clean package -pl yawl-engine -DskipTests

# Package with all dependencies (transitively)
mvn clean package -am -pl yawl-webapps

# Run only fast unit tests
mvn test -Dgroups="fast"

# Run integration tests only
mvn verify -Dgroups="integration"

# Run static analysis (PMD, SpotBugs)
mvn clean verify -P analysis

# Deploy to local repository
mvn clean install
```

---

## Testing Commands

### Quick Test Runs

```bash
# Run fast tests only (unit tests, < 5 min)
bash scripts/dx.sh --feedback

# Run single test class
mvn test -Dtest=YEngineTest

# Run single test method
mvn test -Dtest=YEngineTest#testWorkItemCheckout

# Run tests matching pattern
mvn test -Dtest=YEngine*

# Run tests and stop at first failure
mvn test -X  # Verbose mode

# Run tests with coverage report
mvn clean test jacoco:report
# Report at: target/site/jacoco/index.html
```

### Test Sharding & Parallelization

```bash
# Run tests sharded by module (parallel, fast)
bash scripts/run-all-shards.sh

# Run specific shard
bash scripts/run-shard.sh 1 4  # Run shard 1 of 4

# Run integration test shards
bash scripts/run-integration-shards.sh

# Run MCP validation tests
bash scripts/run-mcp-validation-tests.sh

# Run stress tests for performance
bash scripts/run_stress_tests.sh
```

### Test Analysis

```bash
# Analyze test timing (which tests are slowest)
bash scripts/analyze-test-times.sh

# Generate test impact graph (shows test dependencies)
bash scripts/build-test-impact-graph.sh

# Predict test execution time
bash scripts/predict-test-times.sh

# Run feedback-tier tests (fast smoke tests)
bash scripts/run-feedback-tests.sh
```

---

## Deployment & Packaging

### Build Docker Images

```bash
# Build stateless engine Docker image
mvn clean package -P docker -pl yawl-stateless -DskipTests

# Build webapps (servlet container) image
mvn clean package -P docker -pl yawl-webapps -DskipTests

# Build and tag
docker build -t myregistry/yawl-engine:v1.0 -f Dockerfile.stateless .
docker push myregistry/yawl-engine:v1.0
```

### Deploy with Docker Compose

```bash
# Start full stack (engine, Kafka, PostgreSQL)
docker-compose -f docker-compose.dev.yml up -d

# Start just engine + H2 (minimal)
docker-compose -f docker-compose.minimal.yml up -d

# View logs
docker-compose logs -f yawl-engine

# Stop all services
docker-compose down
```

### Deploy with Kubernetes

```bash
# Create namespace
kubectl create namespace yawl

# Deploy stateless engine
kubectl apply -f docs/deployment/k8s/yawl-stateless-deployment.yaml -n yawl

# Deploy with Kafka
kubectl apply -f docs/deployment/k8s/kafka-statefulset.yaml -n yawl

# Scale deployment
kubectl scale deployment yawl-engine --replicas=5 -n yawl

# Watch rollout
kubectl rollout status deployment/yawl-engine -n yawl

# View logs
kubectl logs -f deployment/yawl-engine -n yawl
```

### Run Local (Development)

```bash
# Run stateful engine (dev mode)
java -jar yawl-webapps/target/yawl-webapps.jar \
  --server.port=8080 \
  --spring.datasource.url=jdbc:h2:./yawl-data

# Run stateless engine
java -jar yawl-stateless/target/yawl-stateless.jar \
  --server.port=8080 \
  --yawl.event-store.url=kafka://localhost:9092

# Run desktop control panel (for editing workflows)
java -jar yawl-control-panel/target/yawl-control-panel.jar
```

---

## Code Quality & Analysis

### Static Analysis

```bash
# Run all static analysis (PMD, SpotBugs, SonarQube)
mvn clean verify -P analysis

# Run PMD only (code quality)
mvn pmd:pmd

# Run SpotBugs (find bugs)
mvn spotbugs:check

# Run SonarQube scan
mvn clean sonar:sonar -Dsonar.host.url=http://localhost:9000

# Code coverage report
mvn clean test jacoco:report
# Open: target/site/jacoco/index.html
```

### Build Metrics

```bash
# Analyze build timing (which modules are slowest)
bash scripts/analyze-build-timings.sh

# Collect build metrics
bash scripts/collect-build-metrics.sh

# Generate performance report
bash scripts/generate-performance-report.sh

# Monitor build performance in real-time
bash scripts/monitor-build-performance.sh
```

### Semantic Analysis

```bash
# Build semantic cache (for faster incremental builds)
bash scripts/build-semantic-cache.sh

# Detect semantic changes (ignore formatting)
bash scripts/detect-semantic-changes.sh

# Compute semantic hash (change detection)
bash scripts/compute-semantic-hash.sh

# Compare ASTs (differences between versions)
bash scripts/ast-differ.sh <file1> <file2>
```

---

## Performance & Benchmarking

### Benchmark Commands

```bash
# Run all benchmarks
bash scripts/benchmark-integration-tests.sh

# Run performance tests
bash scripts/run_stress_tests.sh

# Generate performance analysis
bash scripts/generate-performance-report.sh

# Collect metrics during build
bash scripts/collect-metrics.sh

# Profile a specific workload
java -jar yawl-benchmark/target/yawl-benchmark.jar \
  --workload workflow-execution \
  --cases 10000 \
  --concurrency 50
```

### Performance Tuning

```bash
# Establish baseline metrics
bash scripts/establish-baselines.sh

# Enable GraalVM compilation
mvn clean package -P graalvm -DskipTests

# Build ahead-of-time (AOT) binaries
bash scripts/aot/build-aot-image.sh

# Generate Class Data Sharing (CDS) archives
bash scripts/generate-cds-archives.sh

# Use warm cache for recompiles
DX_WARM_CACHE=1 bash scripts/dx.sh

# Manage warm cache
bash scripts/manage-warm-cache.sh
```

---

## Integration & Connectivity

### MCP (Model Context Protocol)

```bash
# Run MCP server integration tests
bash scripts/run-mcp-validation-tests.sh

# Test MCP endpoint
curl -X GET http://localhost:8080/mcp/health

# Check MCP service status
curl -s http://localhost:8080/actuator/metrics/mcp.requests | jq .
```

### A2A (Agent-to-Agent)

```bash
# Start MCP/A2A server
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app.jar

# Test agent endpoint
curl -X POST http://localhost:8080/a2a/invoke \
  -H "Content-Type: application/json" \
  -d '{"agent":"workflow-scheduler","action":"launch"}'

# Monitor agent traffic
curl -s http://localhost:8080/actuator/metrics/a2a.messages | jq .
```

### Event Streaming

```bash
# Start Kafka for event streaming
docker run -d --name kafka \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  bitnami/kafka:latest

# Check Kafka topics
kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor events
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic yawl-events --from-beginning

# Clear event log
kafka-topics.sh --delete --topic yawl-events \
  --bootstrap-server localhost:9092
```

---

## Monitoring & Observability

### Health Checks

```bash
# Check engine readiness
curl http://localhost:8080/actuator/health/readiness

# Check engine liveness
curl http://localhost:8080/actuator/health/liveness

# Full health report
curl http://localhost:8080/actuator/health

# Check specific component
curl http://localhost:8080/actuator/health/db

# Check dependencies (circuit breakers, etc.)
curl http://localhost:8080/actuator/env | jq .
```

### Metrics & Monitoring

```bash
# List all available metrics
curl http://localhost:8080/actuator/metrics

# Get specific metric (e.g., JVM memory)
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq .

# Export Prometheus format
curl http://localhost:8080/actuator/prometheus

# OpenTelemetry trace export
curl http://localhost:4318/v1/traces

# Collect metrics report
bash scripts/collect-metrics.sh
```

### Logging

```bash
# View application logs (docker)
docker-compose logs -f yawl-engine

# View logs from Kubernetes
kubectl logs -f deployment/yawl-engine -n yawl

# Stream logs with grep
kubectl logs -f deployment/yawl-engine -n yawl | grep ERROR

# Tail local log file
tail -f logs/application.log

# Filter by level
tail -f logs/application.log | grep -i "ERROR\|WARN"

# JSON logging export
curl http://localhost:8080/actuator/loggers | jq .
```

---

## Troubleshooting Commands

### Quick Diagnostics

```bash
# Check all components up
bash scripts/dx.sh compile  # Verifies build environment

# Test database connectivity
mvn test -Dtest=DatabaseConnectionTest

# Test REST API
curl -X POST http://localhost:8080/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Test Kafka connectivity
kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Test authentication
curl -X GET http://localhost:8080/actuator/health \
  -H "Authorization: Bearer <token>"
```

### Build Troubleshooting

```bash
# Clear build cache
rm -rf target/ ~/.m2/repository/org/yawlfoundation/

# Check Java version
java -version  # Should be 25+

# Verify Maven installation
mvn -version

# Check network connectivity
mvn dependency:tree

# Rebuild without cache
mvn clean compile -X  # Verbose output
```

### Runtime Troubleshooting

```bash
# Check running processes
ps aux | grep java

# Kill hung Java process
pkill -f "java.*yawl"

# Monitor resource usage
jps -l  # List Java processes

# Thread dump (for debugging hangs)
jstack <pid> > thread-dump.txt

# Heap dump (for memory issues)
jmap -dump:live,format=b,file=heap.bin <pid>
```

---

## CI/CD & Automation

### Git Hooks

```bash
# Enable pre-commit hooks
git config core.hooksPath scripts/git-hooks

# Run pre-commit checks manually
bash scripts/git-hooks/pre-commit

# Run pre-push checks
bash scripts/git-hooks/pre-push
```

### Continuous Integration

```bash
# Run full CI pipeline locally
DX_OFFLINE=0 bash scripts/dx.sh all

# Run with remote cache
CLAUDE_CODE_REMOTE=true bash scripts/dx.sh all

# Deploy parallelization
bash scripts/deploy-parallelization.sh

# Generate validation report
bash scripts/generate-validation-report.sh
```

### Release & Deployment

```bash
# Prepare release (tag, version bump)
mvn release:prepare

# Perform release (deploy to Maven Central)
mvn release:perform

# Deploy to local Maven repository
mvn clean install

# Deploy snapshots
mvn clean deploy -P ossrh
```

---

## Cache Management

### Build Cache

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Clear local build cache
rm -rf target/

# Enable remote cache
export CLAUDE_CODE_REMOTE=true

# Manage warm cache
bash scripts/manage-warm-cache.sh

# Cache cleanup
bash scripts/cache-cleanup.sh

# Build semantic cache
bash scripts/build-semantic-cache.sh

# Validate cache
bash scripts/remote-cache-management.sh validate
```

### Test Cache

```bash
# Use H2 snapshots for stateless tests
DX_STATELESS=1 bash scripts/dx.sh

# Check test cluster status
bash scripts/check-test-shards.sh

# Pre-warm test cluster
bash scripts/establish-baselines.sh
```

---

## Utility Commands

### Help & Information

```bash
# Show dx.sh help
bash scripts/dx.sh -h

# List all available modules
grep "<module>" pom.xml | grep -v "<!--"

# Show build topology
mvn validate | grep -i "building\|success"

# Check Java 25 features
java -XX:+PrintFlagsFinal -version | grep Compact
```

### Common Workflows

#### "I made changes and want to commit"

```bash
# 1. Quick compile check
bash scripts/dx.sh compile

# 2. Run tests for changed modules
bash scripts/dx.sh

# 3. Pre-commit hook
bash scripts/git-hooks/pre-commit

# 4. Stage and commit
git add src/
git commit -m "description"
```

#### "I need to deploy to production"

```bash
# 1. Full test suite
bash scripts/dx.sh all

# 2. Build Docker image
mvn clean package -P docker -DskipTests

# 3. Push to registry
docker push myregistry/yawl-engine:v1.0

# 4. Deploy
kubectl set image deployment/yawl-engine \
  yawl=myregistry/yawl-engine:v1.0 -n yawl
```

#### "Build is slow, how do I speed it up?"

```bash
# 1. Use impact graph (skip tests for unchanged code)
DX_IMPACT=1 bash scripts/dx.sh

# 2. Enable warm cache
DX_WARM_CACHE=1 bash scripts/dx.sh

# 3. Use feedback tests (smoke tests)
DX_FEEDBACK=1 bash scripts/dx.sh

# 4. Run in parallel (default)
bash scripts/dx.sh --parallel 8

# 5. Skip tests temporarily (dev only)
mvn clean compile -DskipTests
```

---

## Command Cheat Sheet

| Task | Command |
|------|---------|
| Quick compile | `bash scripts/dx.sh compile` |
| Compile + test | `bash scripts/dx.sh` |
| Full validation | `bash scripts/dx.sh all` |
| Build Docker | `mvn clean package -P docker -DskipTests` |
| Run locally | `java -jar target/yawl-*.jar` |
| Health check | `curl http://localhost:8080/actuator/health` |
| View logs | `docker-compose logs -f` |
| Run tests | `bash scripts/dx.sh` |
| Static analysis | `mvn clean verify -P analysis` |
| Deploy to K8s | `kubectl apply -f k8s/deployment.yaml` |
| Check metrics | `curl http://localhost:8080/actuator/metrics` |
| Clear cache | `rm -rf ~/.m2/repository/ target/` |

---

## Environment Setup

### One-Time Setup

```bash
# Install Java 25
sudo apt-get install openjdk-25-jdk  # Linux
# or use Homebrew: brew install openjdk@25

# Install Maven 3.9.11+
sudo apt-get install maven  # Linux
# or: brew install maven

# Verify setup
java -version    # Should show Java 25
mvn -version     # Should show Maven 3.9.11+

# Export JAVA_HOME (optional, dx.sh detects automatically)
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
```

---

## Next Steps

1. **Start with**: `bash scripts/dx.sh compile`
2. **Make changes** to code
3. **Test locally**: `bash scripts/dx.sh`
4. **Run all tests**: `bash scripts/dx.sh all`
5. **Commit when green**: All tests pass

For detailed documentation, see:
- [Build System Documentation](../docs/build-system.md)
- [dx.sh README](scripts/README.md)
- [Maven Configuration](docs/maven-modules.md)

---

**Last updated**: February 28, 2026 | **YAWL v6.0.0**
