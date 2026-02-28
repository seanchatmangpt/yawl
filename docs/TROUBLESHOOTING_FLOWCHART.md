# YAWL Troubleshooting Flowchart — Diagnosis & Recovery

**TL;DR**: Follow the flowchart to diagnose and fix the most common YAWL issues in < 10 minutes.

---

## Table of Contents

1. [Start Here: Quick Diagnostics](#start-here)
2. [Flowchart 1: "Engine Won't Start"](#flowchart-1-engine-wont-start)
3. [Flowchart 2: "Workflow is Stuck"](#flowchart-2-workflow-is-stuck)
4. [Flowchart 3: "Performance is Slow"](#flowchart-3-performance-is-slow)
5. [Flowchart 4: "Build is Failing"](#flowchart-4-build-is-failing)
6. [Flowchart 5: "API Request Failed"](#flowchart-5-api-request-failed)
7. [Reference: Common Error Messages](#reference-common-error-messages)

---

## Start Here: Quick Diagnostics

**Before using any flowchart, run these checks (30 seconds):**

```bash
# 1. Is Java installed and the right version?
java -version
# Expected: OpenJDK 25 or higher

# 2. Is the engine running?
curl http://localhost:8080/actuator/health/readiness
# Expected: 200 OK with {"status":"UP"}

# 3. Is the database connected?
curl http://localhost:8080/actuator/health/db
# Expected: 200 OK with {"status":"UP"}

# 4. Can you see recent logs?
docker-compose logs --tail 20 yawl-engine  # Docker
# or
tail -20 logs/application.log  # Local JAR

# 5. Is any module misconfigured?
curl http://localhost:8080/actuator/env | jq '.propertySources[].properties | select(.* | contains("yawl"))' 2>/dev/null
```

**If all 5 checks pass:** Skip to the specific problem flowchart below.
**If any fail:** Start with the corresponding flowchart.

---

## Flowchart 1: "Engine Won't Start"

```
┌─────────────────────────────────────────────┐
│  Engine won't start / returns 503 error     │
└────────────────┬────────────────────────────┘
                 │
                 ▼
        ┌─────────────────────┐
        │ Check logs for      │
        │ startup messages    │
        └─────────┬───────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
   ┌─────────────┐    ┌──────────────────────┐
   │ Java error? │    │ Database error?      │
   │ (ClassNot   │    │ (Connection refused) │
   │  Found,     │    │                      │
   │ OutOfMemory)│    └─────────┬────────────┘
   └─────┬───────┘              │
         │                      ▼
         │           ┌──────────────────────┐
         │           │ PostgreSQL running?  │
         │           │ Connection pooling?  │
         │           └─────────┬────────────┘
         │                     │
         ▼                     ▼
   ┌──────────────────┐  ┌──────────────────┐
   │ → Fix 1.1        │  │ → Fix 1.2        │
   │                  │  │                  │
   │ JAVA ERROR       │  │ DATABASE ERROR   │
   └──────────────────┘  └──────────────────┘
```

### Fix 1.1: Java Error on Startup

**Symptoms**:
```
Exception in thread "main" java.lang.ClassNotFoundException
or
java.lang.OutOfMemoryError: Java heap space
```

**Diagnosis**:
```bash
# Check Java version
java -version  # Must be Java 25+

# Check heap size
jps -l
jinfo <pid> | grep heap
```

**Recovery**:

| Problem | Command | Time |
|---------|---------|------|
| Wrong Java version | `export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64` | <1 min |
| Out of heap | `java -Xmx4g -jar yawl-engine.jar` | <1 min |
| Classpath issue | Rebuild: `mvn clean compile` | 2-5 min |
| Missing dependencies | `mvn dependency:tree` | <1 min |

**If still broken after 5 minutes:**
```bash
# Full clean rebuild
rm -rf target/ ~/.m2/repository/org/yawlfoundation/
mvn clean compile

# Or switch to Docker
docker-compose -f docker-compose.dev.yml up -d
```

---

### Fix 1.2: Database Connection Error

**Symptoms**:
```
org.postgresql.util.PSQLException: Connection refused
or
Spring Datasource initialization failed
```

**Diagnosis**:
```bash
# Check PostgreSQL running
ps aux | grep postgres
# or
docker-compose ps postgres

# Check connection
psql -U postgres -d yawl -c "SELECT 1"

# Check network
curl -s http://localhost:5432 2>&1
```

**Recovery**:

| Problem | Command | Time |
|---------|---------|------|
| PostgreSQL not running | `docker-compose up postgres -d` | <1 min |
| Wrong host/port | Check `spring.datasource.url` in `application.properties` | <1 min |
| Connection pool exhausted | Increase `hikari.maximum-pool-size` in config | <2 min |
| Authentication failed | Verify username/password and SCRAM auth config | <2 min |
| Database doesn't exist | `createdb -U postgres yawl` | <1 min |

**If still broken after 5 minutes:**
```bash
# Reset database
docker-compose down postgres
docker volume rm yawl_postgres_data  # ⚠️ DELETES DATA
docker-compose up postgres -d

# Wait for initialization (30-60 sec)
until docker-compose exec postgres pg_isready; do sleep 1; done

# Verify
curl http://localhost:8080/actuator/health/db
```

---

## Flowchart 2: "Workflow is Stuck"

```
┌──────────────────────────────────────────┐
│  Workflow won't progress / is stuck      │
│  Case status: EXECUTING / SUSPENDED      │
└────────────┬─────────────────────────────┘
             │
             ▼
    ┌─────────────────────┐
    │ Check case status   │
    │ via REST API        │
    └────────┬────────────┘
             │
     ┌───────┴───────┐
     │               │
     ▼               ▼
┌──────────────┐  ┌──────────────────────┐
│ Status:      │  │ Status:              │
│ EXECUTING    │  │ SUSPENDED or FAILED  │
└──────┬───────┘  └────────┬─────────────┘
       │                   │
       ▼                   ▼
┌────────────────┐  ┌──────────────────┐
│ → Fix 2.1      │  │ → Fix 2.2        │
│                │  │                  │
│ DEADLOCK OR    │  │ EXCEPTION OR     │
│ LONG-RUNNING   │  │ RESOURCE ISSUE   │
│ TASK           │  │                  │
└────────────────┘  └──────────────────┘
```

### Fix 2.1: Case Executing But Not Progressing

**Symptoms**:
```
Case stuck on task for > 5 minutes
No workflow events in the log
No visible error messages
```

**Diagnosis**:
```bash
# Get case details
curl -X GET "http://localhost:8080/yawl/api/case/<caseId>?sessionHandle=<handle>"

# Check active work items
curl -X GET "http://localhost:8080/yawl/api/workitem?sessionHandle=<handle>"

# Check thread dump (might reveal deadlock)
jstack <pid> | grep -A 10 "yawl\|workflow"

# Check database locks
psql -U postgres yawl -c "SELECT * FROM pg_locks l JOIN pg_stat_activity a ON l.pid = a.pid WHERE datname = 'yawl';"
```

**Recovery**:

| Problem | Action | Time |
|---------|--------|------|
| Long-running task | Increase task timeout in specification | <2 min |
| Database deadlock | Restart engine: `docker-compose restart yawl-engine` | 1-2 min |
| Thread pool exhausted | Increase `server.tomcat.threads.max` in config | <2 min |
| Resource leak | Monitor memory: `jstat -gc <pid> 1000` | <1 min |
| Infinite loop in task | Check task implementation for loops | Debug time |

**If problem persists after 10 minutes:**
```bash
# Option 1: Suspend case (preserve state)
curl -X POST "http://localhost:8080/yawl/api/case/<caseId>/suspend?sessionHandle=<handle>"

# Option 2: Cancel case (lose state, restart)
curl -X POST "http://localhost:8080/yawl/api/case/<caseId>/cancel?sessionHandle=<handle>"

# Option 3: Restart engine (force recovery)
docker-compose restart yawl-engine
```

---

### Fix 2.2: Case Suspended or Failed

**Symptoms**:
```
Case status: SUSPENDED, FAILED
Exception visible in logs
Work item checkout failed
```

**Diagnosis**:
```bash
# Get exception details
curl -X GET "http://localhost:8080/yawl/api/case/<caseId>/log?sessionHandle=<handle>"

# Check engine logs for stack trace
docker-compose logs yawl-engine | grep -i "exception\|error" | tail -50

# Check if resource issue
curl http://localhost:8080/actuator/health

# Check if external system is down
curl -s <external_service_url>/health
```

**Recovery**:

| Exception Type | Fix | Time |
|---|---|---|
| TaskExecutionException | Check task code for bugs | Debug time |
| ResourceAllocationException | Verify resource/participant availability | <2 min |
| AuthenticationException | Check session token not expired | <1 min |
| TimeoutException | Increase timeout or optimize task | <2 min |
| DatabaseException | Check database connectivity | <5 min |
| ExternalServiceException | Check service is running, network OK | <5 min |

**If exception is in your custom code:**
```bash
# 1. Fix the bug in task implementation
# 2. Redeploy the workflow specification
# 3. Restart the case

curl -X POST "http://localhost:8080/yawl/api/specification/load?sessionHandle=<handle>" \
  -H "Content-Type: application/xml" \
  -d @updated-spec.xml
```

**If exception is in YAWL engine:**
```bash
# Contact support with details:
# 1. Exception stack trace (from logs)
# 2. YAWL version: curl http://localhost:8080/yawl/api/info
# 3. Workflow specification (YAWL XML)
# 4. Steps to reproduce
```

---

## Flowchart 3: "Performance is Slow"

```
┌────────────────────────────────────────┐
│  Requests slow (>1 second latency)     │
│  or CPU/memory high                    │
└─────────────┬──────────────────────────┘
              │
              ▼
     ┌─────────────────────┐
     │ Baseline check:     │
     │ Response time < 1s? │
     └────────┬────────────┘
              │
      ┌───────┴────────┐
      │                │
      ▼                ▼
  ┌─────────────┐  ┌──────────────────────┐
  │ YES: Normal │  │ NO: SLOW             │
  │ Performance │  │                      │
  └─────────────┘  └────────┬─────────────┘
                            │
                   ┌────────┴────────┐
                   │                 │
                   ▼                 ▼
            ┌──────────────┐  ┌──────────────┐
            │ CPU high?    │  │ Memory high? │
            │ (>80%)       │  │ (>80%)       │
            └──────┬───────┘  └──────┬───────┘
                   │                 │
                   ▼                 ▼
            ┌──────────────┐  ┌──────────────┐
            │ → Fix 3.1    │  │ → Fix 3.2    │
            │              │  │              │
            │ CPU BOUND    │  │ MEMORY BOUND │
            │ WORKLOAD     │  │ WORKLOAD     │
            └──────────────┘  └──────────────┘
```

### Fix 3.1: High CPU Usage

**Symptoms**:
```
Response time > 1 second
CPU > 80%
Thread count high (100+)
```

**Diagnosis**:
```bash
# Check thread count
jps -l
jstack <pid> | wc -l  # Count threads

# Monitor CPU
top -p <pid>  # Linux
Activity Monitor  # macOS

# Check hot spots
jprofile  # JProfiler or similar tool
# or use JFR (Java Flight Recorder)
java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
  -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
  -jar yawl-engine.jar
```

**Recovery**:

| Problem | Fix | Time |
|---------|-----|------|
| Too many concurrent cases | Reduce case load or scale horizontally | <5 min |
| Complex workflow graph | Simplify workflow or optimize conditions | Debug time |
| Inefficient database queries | Add database indexes, optimize SQL | <10 min |
| Bad algorithm in task | Profile and optimize task code | Debug time |
| Contention on shared resource | Use concurrent collections, reduce lock scope | Debug time |

**Quick fixes (< 5 minutes):**
```bash
# Increase thread pool
server.tomcat.threads.max=500

# Enable query caching
spring.cache.type=redis

# Reduce task polling frequency
yawl.task.poll-interval=5000

# Restart with optimizations
java -XX:+UseG1GC -XX:MaxGCPauseMillis=50 \
  -Xmx4g -jar yawl-engine.jar
```

---

### Fix 3.2: High Memory Usage

**Symptoms**:
```
Memory > 80% of heap
GC overhead warnings (>90% time in GC)
OutOfMemoryError errors
```

**Diagnosis**:
```bash
# Check heap usage
jstat -gc <pid> 1000  # Garbage collection stats
jstat -gcutil <pid> 1000  # GC as percentages

# Find memory leaks
jmap -histo:live <pid> | head -50  # Top object types

# Generate heap dump
jmap -dump:live,format=b,file=heap.bin <pid>
# Analyze with Eclipse MAT or jhat

# Check for growing collections
curl http://localhost:8080/actuator/metrics/jvm.memory.live | jq .
```

**Recovery**:

| Problem | Fix | Time |
|---------|-----|------|
| Heap too small | Increase: `java -Xmx8g -jar ...` | <1 min |
| Memory leak in code | Find and fix leak, rebuild | Debug time |
| Unbounded cache | Add cache size limits | <2 min |
| Too many cases in memory | Archive old cases, reduce max parallel cases | <5 min |
| Object accumulation | Implement proper object lifecycle (close, finalize) | Debug time |

**Quick fixes (< 5 minutes):**
```bash
# Increase heap size
java -Xmx8g -jar yawl-engine.jar

# Enable garbage collection tuning
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -Xmx8g -jar yawl-engine.jar

# Reduce cache sizes
spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=5m

# Clear old sessions periodically
server.servlet.session.timeout=15m
```

**If memory doesn't improve after 10 minutes:**
```bash
# Generate heap dump and analyze
jmap -dump:live,format=b,file=heap.bin <pid>

# Use Eclipse MAT or jhat to find leaks
jhat -J-Xmx4g -J-XX:+UseG1GC heap.bin

# Check application logs for warnings
docker-compose logs yawl-engine | grep -i "leak\|warning\|out of memory"
```

---

## Flowchart 4: "Build is Failing"

```
┌────────────────────────────────────────┐
│  mvn clean compile fails               │
│  Test suite fails                      │
│  Deployment build fails                │
└─────────────┬──────────────────────────┘
              │
              ▼
    ┌──────────────────────┐
    │ Check error type:    │
    │ (from Maven output)  │
    └─────────┬────────────┘
              │
      ┌───────┴────────┐
      │                │
      ▼                ▼
  ┌──────────────┐  ┌──────────────────────┐
  │ Compilation? │  │ Test/Dependency?     │
  │ (error:)     │  │ (failure:, ERROR)    │
  └──────┬───────┘  └────────┬─────────────┘
         │                   │
         ▼                   ▼
    ┌──────────────┐  ┌──────────────┐
    │ → Fix 4.1    │  │ → Fix 4.2    │
    │              │  │              │
    │ COMPILE      │  │ TEST/DEP     │
    │ ERROR        │  │ ERROR        │
    └──────────────┘  └──────────────┘
```

### Fix 4.1: Compilation Error

**Symptoms**:
```
[ERROR] COMPILATION ERROR
symbol not found / cannot find symbol
incompatible types
```

**Diagnosis**:
```bash
# Get full error output
mvn clean compile 2>&1 | tail -100

# Check what changed
git diff HEAD~1

# Rebuild specific module
mvn clean compile -pl <module>

# Show dependency tree
mvn dependency:tree
```

**Recovery**:

| Error | Fix | Time |
|-------|-----|------|
| Syntax error | Fix code: check logs for line number | <5 min |
| Symbol not found | Add missing import or check API change | <5 min |
| Incompatible types | Fix type mismatch (Java 25 generics stricter) | <5 min |
| Method not found | Check API change, update code | <5 min |
| Missing module | Add module to build: `-pl <missing>` | <1 min |

**Common fixes:**
```bash
# Update imports
mvn versions:use-latest-versions

# Fix Java 25 compatibility
mvn clean compile -source 25 -target 25

# Rebuild dependencies
mvn clean dependency:resolve

# Check for version conflicts
mvn dependency:tree | grep conflicts
```

---

### Fix 4.2: Test or Dependency Error

**Symptoms**:
```
[FAILURE] Tests run: 100, Failures: 5
BUILD FAILURE
Could not find artifact org.yawlfoundation:X
```

**Diagnosis**:
```bash
# Run tests with verbose output
mvn test -DskipTests=false -X

# Check dependency resolution
mvn dependency:tree

# Look for version conflicts
mvn dependency:tree | grep "duplicate\|conflict"

# Check network connectivity
curl -s https://repo1.maven.org/maven2 > /dev/null && echo OK
```

**Recovery**:

| Error | Fix | Time |
|-------|-----|------|
| Test failure | Read test output, debug code | Variable |
| Missing artifact | Check network, update proxy config | <5 min |
| Version conflict | Use `mvn dependency:resolve-plugins` | <5 min |
| Flaky test | Run test in isolation: `mvn test -Dtest=TestName` | <5 min |
| Timeout | Increase timeout: `mvn test -DargLine="-Dtimeout=120000"` | <1 min |

**Common fixes:**
```bash
# Clear local repository and restart
rm -rf ~/.m2/repository/org/yawlfoundation/
mvn clean compile

# Use offline mode (if cached)
mvn -o clean compile

# Skip tests for now
mvn clean compile -DskipTests

# Run failing test in isolation
mvn test -Dtest=FailingTestClassName

# Check if test is environment-dependent
mvn test -Dgroups="unit"  # Unit tests only
```

**If problem persists:**
```bash
# Check Maven settings
cat ~/.m2/settings.xml

# Check network behind proxy
mvn -X clean compile 2>&1 | grep -i "proxy\|auth\|ssl"

# Try with central repository directly
mvn clean compile -Daether.repositories.blocked=false
```

---

## Flowchart 5: "API Request Failed"

```
┌──────────────────────────────────────┐
│  REST API call returns error         │
│  401, 403, 404, 500, etc.           │
└────────────┬───────────────────────┘
             │
             ▼
    ┌─────────────────────┐
    │ Check HTTP status   │
    │ code (from response)│
    └────────┬────────────┘
             │
   ┌─────────┼──────────┬──────────┐
   │         │          │          │
   ▼         ▼          ▼          ▼
┌───────┐ ┌──────┐ ┌──────┐ ┌──────┐
│401/403│ │404   │ │500   │ │Other │
│       │ │      │ │      │ │(5xx) │
└───┬───┘ └──┬───┘ └──┬───┘ └──┬───┘
    │        │        │        │
    ▼        ▼        ▼        ▼
┌─────────┐ ┌──────┐ ┌──────┐ ┌──────┐
│→Fix 5.1 │ │→Fix  │ │→Fix  │ │→Fix  │
│         │ │5.2   │ │5.3   │ │5.4   │
│AUTH ERR │ │NOT   │ │SRV   │ │OTHER │
│         │ │FOUND │ │ERR   │ │      │
└─────────┘ └──────┘ └──────┘ └──────┘
```

### Fix 5.1: 401/403 Authentication Error

**Symptoms**:
```
HTTP 401 Unauthorized
HTTP 403 Forbidden
{"error":"Invalid session handle"}
```

**Diagnosis**:
```bash
# Check if session exists
curl -X POST http://localhost:8080/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Verify token format
curl http://localhost:8080/actuator/health \
  -H "Authorization: Bearer $TOKEN"

# Check authentication config
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("auth"))'
```

**Recovery**:

| Problem | Fix | Time |
|---------|-----|------|
| No valid session | Call `/ib/connect` first, get session handle | <1 min |
| Session expired | Reconnect with `/ib/connect` | <1 min |
| Wrong credentials | Verify username/password | <1 min |
| Token invalid | Check token format (JWT bearer token) | <1 min |
| CORS issue | Configure CORS headers in config | <2 min |

**Quick fixes:**
```bash
# 1. Get session handle
SESSION=$(curl -s -X POST http://localhost:8080/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.sessionHandle')

# 2. Use session in request
curl "http://localhost:8080/yawl/api/case?sessionHandle=$SESSION"

# 3. Verify it works
echo "Session: $SESSION"
```

---

### Fix 5.2: 404 Not Found

**Symptoms**:
```
HTTP 404 Not Found
{"error":"Path not found"}
Endpoint doesn't exist
```

**Diagnosis**:
```bash
# List available endpoints
curl http://localhost:8080/actuator

# Check engine endpoints
curl http://localhost:8080/yawl/api

# Verify path
curl -v http://localhost:8080/yawl/api/case  # -v shows request
```

**Recovery**:

| Problem | Fix | Time |
|---------|-----|------|
| Wrong path | Check API documentation, correct URL | <1 min |
| Typo in URL | Look for typos (case-sensitive) | <1 min |
| Endpoint not deployed | Check engine logs for startup errors | <2 min |
| Method not supported | Use correct HTTP verb (GET vs POST) | <1 min |
| Version mismatch | Check YAWL version: `curl http://localhost:8080/yawl/api/info` | <1 min |

**Common paths (v6.0.0):**
```bash
# Check available endpoints
curl http://localhost:8080/yawl/api/info

# Correct paths:
curl http://localhost:8080/ib/connect          # Authentication
curl http://localhost:8080/yawl/api/case       # Case management
curl http://localhost:8080/yawl/api/workitem   # Work items
curl http://localhost:8080/yawl/api/spec       # Specifications
curl http://localhost:8080/actuator/health     # Health checks
```

---

### Fix 5.3: 500 Internal Server Error

**Symptoms**:
```
HTTP 500 Internal Server Error
{"error":"An unexpected error occurred"}
Stack trace in logs
```

**Diagnosis**:
```bash
# Get detailed error from engine logs
docker-compose logs yawl-engine | grep -A 20 "500\|Exception"

# Get full response body
curl -v http://localhost:8080/yawl/api/case

# Check engine health
curl http://localhost:8080/actuator/health

# Look for resource issues
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Recovery**:

| Problem | Fix | Time |
|---------|-----|------|
| Database error | Check DB connection, logs for SQL error | <5 min |
| Null pointer exception | Check request parameters are not null | <5 min |
| Resource exhausted | Restart engine, increase resources | <5 min |
| Unhandled exception | Check engine logs for stack trace | Debug time |
| Configuration missing | Check `application.properties`, environment vars | <2 min |

**Quick recovery:**
```bash
# 1. Check logs for root cause
docker-compose logs yawl-engine | tail -100

# 2. Restart engine
docker-compose restart yawl-engine

# 3. Verify recovery
curl http://localhost:8080/actuator/health/readiness
```

---

### Fix 5.4: Other 5xx Errors (502, 503, 504)

**Symptoms**:
```
HTTP 502 Bad Gateway (upstream issue)
HTTP 503 Service Unavailable (maintenance)
HTTP 504 Gateway Timeout (slow response)
```

**Diagnosis**:
```bash
# Check if engine is running
curl http://localhost:8080/actuator/health

# Check if port is reachable
telnet localhost 8080

# Check logs for startup errors
docker-compose logs yawl-engine | head -50

# Monitor resource usage during request
watch -n1 'curl http://localhost:8080/actuator/health'
```

**Recovery**:

| Error | Fix | Time |
|-------|-----|------|
| 502 | Restart engine or check upstream | <3 min |
| 503 | Check if in maintenance mode, scale up resources | <5 min |
| 504 | Request is too slow, optimize or increase timeout | <10 min |

**Quick recovery:**
```bash
# Restart everything
docker-compose restart

# Wait for health
until curl -s http://localhost:8080/actuator/health/readiness | grep -q UP; do
  echo "Waiting for engine..."
  sleep 2
done

# Verify
curl http://localhost:8080/yawl/api/info
```

---

## Reference: Common Error Messages

### Compilation Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `cannot find symbol` | Missing import or undefined method | Add import or check API |
| `incompatible types` | Type mismatch | Fix cast or variable type |
| `method not found in class` | API change or wrong class | Check Javadoc, use correct method |
| `no applicable constructor` | Constructor arguments wrong | Check constructor signature |

### Runtime Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `ClassNotFoundException` | Missing JAR or wrong classpath | Add dependency, rebuild |
| `NullPointerException` | Null value used | Check null guards in code |
| `OutOfMemoryError` | Heap too small | Increase `-Xmx` |
| `StackOverflowError` | Infinite recursion | Fix recursive logic |
| `TimeoutException` | Operation too slow | Increase timeout or optimize |

### Connection Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `Connection refused` | Service not running | Start service (PostgreSQL, Kafka, etc.) |
| `Connection timeout` | Network unreachable | Check firewall, DNS, network |
| `SSL handshake failed` | Certificate issue | Check TLS config, certificate validity |
| `Authentication failed` | Wrong credentials | Verify username/password, tokens |

### Database Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `PSQLException` | PostgreSQL error | Check logs, verify SQL syntax |
| `DataAccessException` | Invalid SQL or schema | Check database schema, indexes |
| `HibernateException` | ORM mapping error | Verify entity annotations |
| `Deadlock detected` | Transaction conflict | Restart, optimize transaction |

---

## Emergency Recovery Procedures

### "Everything is broken, start from scratch"

```bash
# Step 1: Stop all services
docker-compose down -v  # ⚠️ DELETES ALL DATA

# Step 2: Clean build artifacts
rm -rf target/ ~/.m2/repository/org/yawlfoundation/

# Step 3: Rebuild from source
mvn clean install -DskipTests

# Step 4: Start fresh
docker-compose -f docker-compose.dev.yml up -d

# Step 5: Verify
curl http://localhost:8080/actuator/health/readiness
```

**Expected time**: 10-15 minutes

---

## When to Contact Support

If you've worked through all flowcharts and the issue persists:

1. **Gather diagnostics**:
   ```bash
   # Collect logs, config, system info
   docker-compose logs > logs.txt
   uname -a > system-info.txt
   java -version >> system-info.txt
   mvn --version >> system-info.txt
   ```

2. **Reproduce steps**: Document exact steps to reproduce the issue

3. **YAWL version**: `curl http://localhost:8080/yawl/api/info`

4. **Workflow spec**: Include YAWL XML file (if applicable)

5. **Contact**: [YAWL Support](https://yawlfoundation.org/support)

---

## Next Steps

1. **Identify your symptom** in the flowcharts above
2. **Follow the diagnosis steps** to understand the root cause
3. **Apply the fix** for your specific problem
4. **Verify recovery** with the health checks
5. **Document** what you changed for next time

**For detailed documentation, see**:
- [Troubleshooting Guide](./how-to/troubleshooting.md)
- [Architecture Guide](./architecture/)
- [API Reference](./reference/api-reference.md)

---

**Last updated**: February 28, 2026 | **YAWL v6.0.0**
