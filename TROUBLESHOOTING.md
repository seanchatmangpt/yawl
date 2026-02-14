# YAWL Troubleshooting Guide

## Table of Contents

1. [General Troubleshooting Process](#general-troubleshooting-process)
2. [Installation Issues](#installation-issues)
3. [Service Connectivity Issues](#service-connectivity-issues)
4. [Database Issues](#database-issues)
5. [Performance Issues](#performance-issues)
6. [Workflow Execution Issues](#workflow-execution-issues)
7. [Authentication and Authorization Issues](#authentication-and-authorization-issues)
8. [Container and Orchestration Issues](#container-and-orchestration-issues)
9. [Data Issues](#data-issues)
10. [External Integration Issues](#external-integration-issues)
11. [Diagnostic Tools and Commands](#diagnostic-tools-and-commands)

## General Troubleshooting Process

### Systematic Debugging Approach

```bash
# 1. IDENTIFY THE PROBLEM
# - What is not working?
# - When did it start?
# - What changed recently?

# 2. GATHER INFORMATION
# - System logs
# - Error messages
# - Recent configurations changes
# - Resource metrics

# 3. ISOLATE THE ISSUE
# - Is it infrastructure, application, or configuration?
# - Affected services
# - Scope of impact

# 4. REPRODUCE THE PROBLEM
# - Can you replicate it consistently?
# - What are the steps to reproduce?

# 5. IMPLEMENT FIX
# - Make ONE change at a time
# - Document what you changed
# - Test the fix

# 6. VERIFY RESOLUTION
# - Issue completely resolved?
# - Any side effects?
# - Did related issues appear?

# 7. DOCUMENT AND LEARN
# - Root cause analysis
# - Prevention measures
# - Update runbooks
```

### Quick Triage Checklist

```bash
#!/bin/bash
# First steps for any issue

echo "=== YAWL Triage Checklist ==="

# 1. All services running?
echo "[1] Checking service status..."
docker-compose ps
STATUS_CHECK=$?

# 2. Database accessible?
echo "[2] Checking database..."
docker exec yawl-postgres pg_isready -U yawl
DB_CHECK=$?

# 3. Recent errors?
echo "[3] Checking recent errors..."
docker logs yawl-engine --since 1h | grep -i error | wc -l

# 4. Resource constraints?
echo "[4] Checking resources..."
docker stats --no-stream

# 5. Network connectivity?
echo "[5] Checking network..."
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/resourceService/

echo "=== Triage Complete ==="
[ $STATUS_CHECK -eq 0 ] && [ $DB_CHECK -eq 0 ] && echo "Basic health OK" || echo "Basic health FAILED"
```

## Installation Issues

### Issue: Docker Image Build Fails

**Symptoms:**
```
ERROR [builder 3/10]: failed to compute cache key: "/app/build" not found
ERROR: failed to build docker image
```

**Root Causes:**
- Missing build directory
- Incorrect Dockerfile path
- Insufficient disk space
- Docker daemon not running

**Diagnosis:**

```bash
# 1. Verify build directory exists
ls -la /home/user/yawl/build/

# 2. Check Dockerfile
cat /home/user/yawl/Dockerfile | head -20

# 3. Check disk space
df -h | grep -E "/$|/tmp"

# 4. Verify Docker is running
docker ps -a

# 5. Check build logs
docker build -f Dockerfile -t yawl:test . 2>&1 | tail -50
```

**Solutions:**

```bash
# Solution 1: Build with verbose output
docker build \
  --progress=plain \
  -f Dockerfile \
  -t yawl:5.2.0 . 2>&1 | tee build.log

# Solution 2: Clean up and retry
docker system prune -a
docker build -f Dockerfile -t yawl:5.2.0 .

# Solution 3: Free disk space
# Delete old images/containers
docker rmi $(docker images -q) 2>/dev/null
docker rm $(docker ps -aq) 2>/dev/null

# Solution 4: Use multi-stage build caching
docker build \
  --cache-from yawl:latest \
  -f Dockerfile \
  -t yawl:5.2.0 .

# Solution 5: Build with additional memory
docker build \
  --memory 4g \
  -f Dockerfile \
  -t yawl:5.2.0 .
```

### Issue: Port Already in Use

**Symptoms:**
```
Error: Bind failed: port 8080 is already allocated
docker: Error response from daemon: Ports are not available
```

**Diagnosis:**

```bash
# 1. Find process using port
lsof -i :8080
netstat -tuln | grep 8080
ss -tuln | grep 8080

# 2. Get process details
ps aux | grep 8080
```

**Solutions:**

```bash
# Solution 1: Stop conflicting container
docker ps | grep 8080
docker stop <container_id>

# Solution 2: Use different port
docker run -p 8081:8080 yawl:5.2.0

# Solution 3: Kill process using port
PROCESS_ID=$(lsof -t -i :8080)
kill -9 $PROCESS_ID

# Solution 4: Check for stuck docker daemon
docker ps     # If unresponsive, restart daemon
systemctl restart docker

# Solution 5: Remove dangling containers
docker container prune -f
```

## Service Connectivity Issues

### Issue: YAWL Engine Not Responding

**Symptoms:**
```
curl: (7) Failed to connect to localhost port 8080: Connection refused
Connection timeout
HTTP 502 Bad Gateway
```

**Diagnosis:**

```bash
# 1. Check if container is running
docker ps | grep yawl
docker logs yawl-engine | tail -50

# 2. Check startup logs
docker logs yawl-engine --since 10m | grep -i "started\|error"

# 3. Check port binding
docker port yawl-engine
netstat -tuln | grep 8080

# 4. Check container network
docker inspect yawl-engine | grep -A 20 "Networks"

# 5. Check health status
docker inspect --format='{{json .State.Health}}' yawl-engine | jq .

# 6. Try accessing from inside container
docker exec yawl-engine curl http://localhost:8080/resourceService/
```

**Solutions:**

```bash
# Solution 1: Restart container
docker-compose restart yawl

# Solution 2: Check startup script
docker exec yawl-engine cat /usr/local/tomcat/bin/startup.sh | head -20

# Solution 3: Increase startup wait time
# In docker-compose.yml:
healthcheck:
  start_period: 120s  # Increase from 60s

# Solution 4: Check JVM startup
docker logs yawl-engine | grep -A 20 "java.lang.Exception\|OutOfMemory"

# Solution 5: Check if port is truly open
docker exec yawl-engine ss -tuln | grep 8080

# Solution 6: Restart Docker daemon
sudo systemctl restart docker
docker-compose up -d

# Solution 7: View full container output
docker run --rm -it \
  -e YAWL_DB_HOST=postgres \
  --link yawl-postgres:postgres \
  yawl:5.2.0 \
  /usr/local/tomcat/bin/catalina.sh run
```

### Issue: Cannot Reach PostgreSQL from YAWL Engine

**Symptoms:**
```
ERROR: database host not found
Connection refused: postgres:5432
Caused by: java.net.UnknownHostException: postgres
```

**Diagnosis:**

```bash
# 1. Check if PostgreSQL is running
docker-compose ps | grep postgres

# 2. Test connectivity from YAWL container
docker exec yawl-engine nc -zv postgres 5432
docker exec yawl-engine nslookup postgres

# 3. Check Docker network
docker network ls
docker network inspect yawl-network

# 4. Verify environment variables
docker exec yawl-engine env | grep YAWL_DB

# 5. Test from postgres container
docker exec postgres psql -U yawl -d yawl -c "SELECT 1;"
```

**Solutions:**

```bash
# Solution 1: Use correct hostname
# Ensure docker-compose services use service name:
services:
  yawl:
    environment:
      YAWL_DB_HOST: postgres  # Not localhost or 127.0.0.1

# Solution 2: Restart network
docker network disconnect yawl-network yawl-engine
docker network connect yawl-network yawl-engine

# Solution 3: Explicit network creation
docker network create yawl-net
docker run --network yawl-net ...

# Solution 4: Use IP address if DNS fails
POSTGRES_IP=$(docker inspect -f '{{.NetworkSettings.Networks.yawl-network.IPAddress}}' yawl-postgres)
docker exec yawl-engine ping $POSTGRES_IP

# Solution 5: Check PostgreSQL is accepting connections
docker exec postgres psql -U postgres -c "SHOW max_connections;"

# Solution 6: Verify credentials
# In docker-compose.yml:
postgres:
  environment:
    POSTGRES_USER: yawl
    POSTGRES_DB: yawl
yawl:
  environment:
    YAWL_DB_USER: yawl    # Must match

# Solution 7: Enable PostgreSQL logging
docker exec postgres psql -U postgres -c \
  "ALTER SYSTEM SET log_connections = on; SELECT pg_reload_conf();"
```

### Issue: Cannot Connect to Redis Cache

**Symptoms:**
```
Redis connection refused
redis.clients.jedis.exceptions.JedisConnectionException
WRONGPASS invalid username-password pair
```

**Diagnosis:**

```bash
# 1. Check Redis status
docker ps | grep redis
docker-compose ps | grep redis

# 2. Test connectivity
docker exec yawl-engine redis-cli -h redis ping

# 3. Check Redis logs
docker logs yawl-redis

# 4. Check Redis listening port
docker exec redis netstat -tuln | grep 6379

# 5. Inspect Redis process
docker exec redis redis-cli INFO server
```

**Solutions:**

```bash
# Solution 1: Restart Redis
docker-compose restart redis

# Solution 2: Clear Redis data
docker exec redis redis-cli FLUSHALL
docker-compose restart redis

# Solution 3: Check password auth
# If Redis has password:
docker exec redis redis-cli -a your_password ping

# Solution 4: Verify connection string
# In application configuration:
spring.redis.host=redis
spring.redis.port=6379
spring.redis.password=       # Empty if no auth

# Solution 5: Check network connectivity
docker exec yawl-engine telnet redis 6379

# Solution 6: Increase Redis memory
# In docker-compose.yml:
redis:
  mem_limit: 2g
  command: redis-server --maxmemory 2gb

# Solution 7: Check firewall rules
docker exec redis iptables -L DOCKER-USER
```

## Database Issues

### Issue: Database Connection Pool Exhausted

**Symptoms:**
```
ERROR: exhausted resultset cache
HikariPool - Connection is not available
Cannot get a connection, pool error Timeout waiting for idle object
```

**Root Causes:**
- Too many concurrent connections
- Connections not being returned to pool
- Connection timeout too short
- Memory leak in connection handling

**Diagnosis:**

```bash
# 1. Check current connections
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"

# 2. Check max connections
docker exec yawl-postgres psql -U postgres -c "SHOW max_connections;"

# 3. Find long-running transactions
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT pid, usename, query, query_start FROM pg_stat_activity
   WHERE state != 'idle' ORDER BY query_start;"

# 4. Check idle connections
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT pid, usename, state, query_start FROM pg_stat_activity
   WHERE state = 'idle' AND query_start < now() - interval '30 min';"

# 5. View connection pool metrics
curl -s http://localhost:8080/metrics | grep "hikari\|connections"
```

**Solutions:**

```bash
# Solution 1: Increase max connections in PostgreSQL
docker exec yawl-postgres psql -U postgres << EOF
ALTER SYSTEM SET max_connections = 200;
SELECT pg_reload_conf();
SHOW max_connections;
EOF

# Solution 2: Terminate idle connections
docker exec yawl-postgres psql -U postgres << EOF
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE state = 'idle' AND query_start < now() - interval '10 min'
  AND pid <> pg_backend_pid();
EOF

# Solution 3: Adjust connection pool settings
# In Tomcat context.xml:
# <Resource name="jdbc/yawlDS"
#   maxActive="50"          # Increase from 20
#   maxIdle="15"
#   maxWait="30000"
#   timeBetweenEvictionRunsMillis="30000"
#   numTestsPerEvictionRun="3"
#   testOnBorrow="true"
#   testWhileIdle="true"
#   validationQuery="SELECT 1"
# />

# Solution 4: Kill specific long-running query
PID=$(docker exec yawl-postgres psql -U postgres -t -c \
  "SELECT pid FROM pg_stat_activity
   WHERE query LIKE '%SELECT%' ORDER BY query_start LIMIT 1;")
docker exec yawl-postgres psql -U postgres -c "SELECT pg_terminate_backend($PID);"

# Solution 5: Enable connection pooling at application level
# Add HikariCP configuration:
# spring.datasource.hikari.maximum-pool-size=30
# spring.datasource.hikari.minimum-idle=5
# spring.datasource.hikari.connection-timeout=20000

# Solution 6: Monitor connection usage
watch -n 5 'docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT datname, count(*) as connections FROM pg_stat_activity GROUP BY datname;"'
```

### Issue: Database Disk Space Full

**Symptoms:**
```
ERROR: could not write to relation
disk space: could not write to file
Failed to allocate XXX MB
```

**Diagnosis:**

```bash
# 1. Check available disk space
df -h /var/lib/docker

# 2. Check database size
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT pg_size_pretty(pg_database_size('yawl')) as size;"

# 3. Find large tables
docker exec yawl-postgres psql -U yawl -d yawl << EOF
SELECT schemaname, tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
LIMIT 10;
EOF

# 4. Check for unlogged tables
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT schemaname, tablename FROM pg_tables WHERE unlogged = true;"
```

**Solutions:**

```bash
# Solution 1: Expand disk
# Stop container and add storage to volume
docker-compose stop postgres

# Or expand if using cloud storage
gcloud compute disks resize yawl-disk --size 200GB

# Solution 2: Archive old cases
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- Backup old data
pg_dump -t ypcase -U yawl yawl | gzip > ypcase_backup.sql.gz

-- Delete old completed cases
DELETE FROM ypcase
WHERE status = 'COMPLETED'
  AND completed_time < now() - interval '365 days';

VACUUM FULL;
EOF

# Solution 3: Clean up logs and temporary files
docker exec yawl-postgres psql -U postgres << EOF
TRUNCATE TABLE pg_stat_statements;
VACUUM FULL ANALYZE;
EOF

# Solution 4: Check for autovacuum issues
docker exec yawl-postgres psql -U postgres -c \
  "SELECT * FROM pg_stat_all_tables WHERE last_vacuum IS NULL LIMIT 5;"

# Solution 5: Enable automatic cleanup
docker exec yawl-postgres psql -U postgres << EOF
ALTER SYSTEM SET autovacuum = on;
ALTER SYSTEM SET autovacuum_naptime = '10s';
ALTER SYSTEM SET autovacuum_vacuum_threshold = 50;
SELECT pg_reload_conf();
EOF
```

### Issue: Database Query Timeout

**Symptoms:**
```
Query execution timeout
org.postgresql.util.PSQLException: Query timeout
Query returns after 60+ seconds
```

**Diagnosis:**

```bash
# 1. Identify slow query
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "EXPLAIN ANALYZE SELECT * FROM ypcase WHERE status = 'ACTIVE';"

# 2. Check index usage
docker exec yawl-postgres psql -U yawl -d yawl << EOF
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY 1, 2, 3;
EOF

# 3. Check query plan
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM ypcase
   WHERE created_time > now() - interval '30 days' ORDER BY created_time DESC LIMIT 100;"
```

**Solutions:**

```bash
# Solution 1: Increase query timeout
# In Tomcat context.xml:
# <Resource connectionProperties="connectionTimeout=60000"/>

# Or in application config:
# spring.datasource.hikari.connection-timeout=60000

# Solution 2: Create missing indexes
docker exec yawl-postgres psql -U yawl -d yawl << EOF
CREATE INDEX idx_ypcase_status ON ypcase(status)
  WHERE status IN ('ACTIVE', 'SUSPENDED');

CREATE INDEX idx_ypcase_created_desc ON ypcase(created_time DESC);

CREATE INDEX idx_ypworkitem_allocated ON ypworkitem(allocated_to, status);

ANALYZE;
EOF

# Solution 3: Rewrite problematic query
# Before:
SELECT * FROM ypcase WHERE status = 'ACTIVE' AND data::text LIKE '%search_term%';

# After (use indexed column):
SELECT * FROM ypcase WHERE status = 'ACTIVE' AND caseid LIKE 'search_term%';

# Solution 4: Use LIMIT in queries
SELECT * FROM ypcase WHERE status = 'ACTIVE' LIMIT 100;

# Solution 5: Enable parallel query execution
docker exec yawl-postgres psql -U postgres << EOF
ALTER SYSTEM SET max_parallel_workers = 4;
ALTER SYSTEM SET max_parallel_workers_per_gather = 2;
SELECT pg_reload_conf();
EOF
```

## Performance Issues

### Issue: High CPU Usage

**Symptoms:**
```
CPU usage consistently > 80%
Application slow to respond
Load average high
```

**Diagnosis:**

```bash
# 1. Identify resource hogs
docker stats --no-stream | grep -E "yawl|CPU"

# 2. Check process CPU usage
docker exec yawl-engine ps aux | sort -k3 -rn | head -10

# 3. Find hot spots in code
# Enable profiling:
docker exec yawl-engine jps -l

# 4. Check GC overhead
docker exec yawl-engine jstat -gcutil $(pgrep -f tomcat) 1000 5

# 5. Monitor CPU per thread
docker exec yawl-engine top -b -n 1 -H -p $(pgrep -f tomcat) | head -20
```

**Solutions:**

```bash
# Solution 1: Analyze GC pauses
JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log"

# Solution 2: Optimize JVM GC
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled"

# Solution 3: Profile application
docker exec yawl-engine jmap -histo:live $(pgrep -f tomcat) | head -30

# Solution 4: Optimize database queries
# Run EXPLAIN ANALYZE on slow queries
docker exec yawl-postgres psql -U yawl -d yawl << EOF
EXPLAIN ANALYZE SELECT * FROM ypcase WHERE status = 'ACTIVE';
EOF

# Solution 5: Scale horizontally
docker-compose up -d --scale yawl=3

# Solution 6: Enable cache
# Use Redis for frequently accessed data
# Enable Hibernate second-level cache
```

### Issue: High Memory Usage (Memory Leak)

**Symptoms:**
```
Memory usage continuously increases
Java heap space error
Container restarted due to OOMKilled
```

**Diagnosis:**

```bash
# 1. Monitor memory over time
for i in {1..10}; do
  docker stats --no-stream yawl-engine
  sleep 60
done

# 2. Dump heap
docker exec yawl-engine jmap -dump:live,format=b,file=/tmp/heap.bin $(pgrep -f tomcat)

# 3. List top memory consumers
docker exec yawl-engine jmap -histo:live $(pgrep -f tomcat) | head -20

# 4. Check for string pool issues
docker exec yawl-engine jcmd $(pgrep -f tomcat) VM.string_statistics

# 5. Monitor GC frequency
docker exec yawl-engine jstat -gccause $(pgrep -f tomcat) 1000 10
```

**Solutions:**

```bash
# Solution 1: Increase heap size (temporary)
environment:
  JAVA_OPTS: "-Xms2048m -Xmx4096m"

# Solution 2: Enable profiling to find leak
docker run -d \
  -e JAVA_OPTS="-Xms1024m -Xmx2048m -javaagent:/usr/local/lib/javaagent.jar=port=8849" \
  -p 8849:8849 \
  yawl:5.2.0

# Connect YourKit profiler to port 8849

# Solution 3: Update to latest patch
docker pull yawlfoundation/yawl:latest
docker-compose up -d

# Solution 4: Archive old cases to reduce memory
docker exec yawl-postgres psql -U yawl -d yawl << EOF
DELETE FROM ypcase WHERE completed_time < now() - interval '30 days' AND status = 'COMPLETED';
VACUUM;
EOF

# Solution 5: Disable unnecessary features
# In application.properties:
spring.cache.type=redis    # Use external cache
spring.jpa.properties.hibernate.cache.use_second_level_cache=false

# Solution 6: Monitor with docker stats
watch 'docker stats --no-stream | grep yawl'
```

### Issue: Slow Response Times

**Symptoms:**
```
API responses taking > 5 seconds
Web UI slow to load
Timeouts on requests
```

**Diagnosis:**

```bash
# 1. Measure end-to-end response time
time curl -s http://localhost:8080/resourceService/ > /dev/null

# 2. Identify slow endpoints
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/resourceService/
# Where curl-format.txt contains timing metrics

# 3. Check database query time
docker exec yawl-postgres psql -U yawl -d yawl << EOF
SET log_min_duration_statement = 0;
SELECT query, mean_time FROM pg_stat_statements
ORDER BY mean_time DESC LIMIT 10;
EOF

# 4. Check application logs for bottlenecks
docker logs yawl-engine | grep -E "took|duration|ms"

# 5. Measure network latency
ping -c 5 localhost
```

**Solutions:**

```bash
# Solution 1: Enable response caching
# In application.properties:
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000

# Solution 2: Add database indexes
docker exec yawl-postgres psql -U yawl -d yawl << EOF
CREATE INDEX idx_ypcase_status_created
  ON ypcase(status, created_time DESC);

ANALYZE;
EOF

# Solution 3: Optimize API endpoints
# Pagination instead of fetching all results
curl 'http://localhost:8080/cases?page=0&size=100'

# Solution 4: Use async processing
# For long-running operations, return immediately and poll status

# Solution 5: Scale with load balancer
docker-compose up -d --scale yawl=5

# Solution 6: Enable HTTP compression
# In Tomcat server.xml:
# <Connector compression="on" compressionMinSize="1024"/>

# Solution 7: Add CDN for static assets
# Serve images, CSS, JS from CDN
```

## Workflow Execution Issues

### Issue: Cases Not Progressing

**Symptoms:**
```
Cases stuck in ACTIVE state
Work items not advancing
No errors in logs
```

**Diagnosis:**

```bash
# 1. Check case status
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT id, status, created_time, last_updated FROM ypcase
   WHERE status = 'ACTIVE' ORDER BY created_time;"

# 2. Check work items for the case
docker exec yawl-postgres psql -U yawl -d yawl << EOF
SELECT id, taskid, status, allocated_to, enabled_time, created_time
FROM ypworkitem
WHERE idcase = 'case_12345'
ORDER BY created_time DESC;
EOF

# 3. Check for errors in workflow execution
docker logs yawl-engine --since 1h | grep -i "exception\|error" | grep case_12345

# 4. Check if tasks are stuck
docker exec yawl-postgres psql -U yawl -d yawl << EOF
SELECT id, taskid, status FROM ypworkitem
WHERE status = 'ALLOCATED'
  AND enabled_time < now() - interval '24 hours'
ORDER BY enabled_time DESC;
EOF

# 5. Verify resource availability
curl -s http://localhost:8080/resourceService/resources | grep -c "resource"
```

**Solutions:**

```bash
# Solution 1: Force task completion
docker exec yawl-postgres psql -U yawl -d yawl << EOF
UPDATE ypworkitem
SET status = 'COMPLETED', completion_time = now()
WHERE id = 'workitem_123' AND status = 'ALLOCATED';
EOF

# Solution 2: Resume suspended case
curl -X POST http://localhost:8080/engine/cases/case_12345/resume \
  -H "Content-Type: application/xml"

# Solution 3: Check resource assignments
curl -s http://localhost:8080/resourceService/workqueue?userid=john.doe | \
  xmllint --format -

# Solution 4: Restart workflow net
docker-compose restart yawl

# Solution 5: Escalate task
curl -X POST http://localhost:8080/engine/workitems/workitem_123/escalate \
  -H "Content-Type: application/xml" \
  --data "<escalation><userid>supervisor</userid></escalation>"
```

### Issue: Workflow Deadlock

**Symptoms:**
```
Join conditions not met
Multiple paths waiting at split
No progress possible
```

**Diagnosis:**

```bash
# 1. Visualize workflow net state
curl -s http://localhost:8080/engine/cases/case_12345/netstate | \
  xmllint --format - > net_state.xml

# 2. Check enabled tasks
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT taskid, status FROM ypworkitem
   WHERE idcase = 'case_12345' AND status != 'COMPLETED';"

# 3. Check join conditions
curl -s http://localhost:8080/engine/workflows/wf_123 | \
  grep -A 10 "<join"

# 4. Analyze flow
docker logs yawl-engine --since 1h | grep -E "join|split|condition"
```

**Solutions:**

```bash
# Solution 1: Skip problematic join
curl -X POST http://localhost:8080/engine/workitems/workitem_123/skip \
  -H "Content-Type: application/xml"

# Solution 2: Force flow to next task
docker exec yawl-postgres psql -U yawl -d yawl << EOF
UPDATE ypworkitem
SET status = 'COMPLETED', completion_time = now()
WHERE taskid IN ('task_pending_1', 'task_pending_2');
EOF

# Solution 3: Cancel case and restart
curl -X POST http://localhost:8080/engine/cases/case_12345/cancel \
  -H "Content-Type: application/xml"

# Solution 4: Adjust workflow model
# If join condition is too strict, update specification
# (Requires workflow unload/reload)
```

## Authentication and Authorization Issues

### Issue: Login Fails

**Symptoms:**
```
Unauthorized: invalid username or password
Authentication failed
Access denied
```

**Diagnosis:**

```bash
# 1. Verify user exists
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT userid, email FROM ypresource WHERE userid = 'john.doe';"

# 2. Check password hash
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT userid, password_hash FROM ypresource WHERE userid = 'john.doe';"

# 3. Check logs for auth attempts
docker logs yawl-engine --since 1h | grep -i "authentication\|login\|auth"

# 4. Verify LDAP configuration (if used)
curl -s http://localhost:8080/resourceService/config | \
  grep -A 10 "ldap"

# 5. Check session
curl -c cookies.txt -X POST \
  -d "username=john.doe&password=test" \
  http://localhost:8080/resourceService/login
```

**Solutions:**

```bash
# Solution 1: Reset password
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- Update password hash (bcrypt)
UPDATE ypresource
SET password_hash = '\$2a\$10\$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lm'
WHERE userid = 'john.doe';
-- Password is now: 'test'
EOF

# Solution 2: Verify LDAP connectivity
docker exec yawl-engine ldapsearch -x -h ldap.example.com -b "dc=example,dc=com" "uid=john.doe"

# Solution 3: Reconfigure authentication
# Edit web.xml to switch authentication backend
# Options: Database, LDAP, SAML

# Solution 4: Clear session cache
docker exec yawl-redis redis-cli FLUSHDB

# Solution 5: Enable debug logging
docker logs yawl-engine --since 1h | grep -i "debug.*auth"
```

### Issue: Permission Denied

**Symptoms:**
```
Access denied to create workflow
User cannot access resource
Insufficient permissions
```

**Diagnosis:**

```bash
# 1. Check user roles
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM yprole WHERE userid = 'john.doe';"

# 2. Check permissions
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM yppermission WHERE userid = 'john.doe';"

# 3. Check org unit
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM yporgdata WHERE userid = 'john.doe';"

# 4. View audit log
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM ypauditlog WHERE userid = 'john.doe' ORDER BY timestamp DESC LIMIT 20;"
```

**Solutions:**

```bash
# Solution 1: Grant role to user
docker exec yawl-postgres psql -U yawl -d yawl << EOF
INSERT INTO yprole (userid, role)
VALUES ('john.doe', 'workflow_designer')
ON CONFLICT DO NOTHING;
EOF

# Solution 2: Grant specific permission
docker exec yawl-postgres psql -U yawl -d yawl << EOF
INSERT INTO yppermission (userid, permission)
VALUES ('john.doe', 'create_workflow')
ON CONFLICT DO NOTHING;
EOF

# Solution 3: Add to org unit
docker exec yawl-postgres psql -U yawl -d yawl << EOF
INSERT INTO yporgdata (userid, orgunit)
VALUES ('john.doe', 'engineering')
ON CONFLICT DO NOTHING;
EOF

# Solution 4: Check role-based access rules
# View resource allocation rules
curl -s http://localhost:8080/resourceService/allocations | \
  xmllint --format -
```

## Container and Orchestration Issues

### Issue: Pod Restart Loops

**Symptoms (Kubernetes):**
```
Pod in CrashLoopBackOff state
Container restarts every 10 seconds
Crash count constantly increasing
```

**Diagnosis:**

```bash
# 1. Check pod status
kubectl describe pod yawl-xyz -n yawl

# 2. View crash logs
kubectl logs yawl-xyz -n yawl --previous  # Previous container
kubectl logs yawl-xyz -n yawl -c yawl    # Specific container

# 3. Check events
kubectl get events -n yawl --sort-by='.lastTimestamp' | tail -20

# 4. Check resource limits
kubectl describe pod yawl-xyz -n yawl | grep -A 5 "Limits\|Requests"
```

**Solutions:**

```bash
# Solution 1: Increase startup time
kubectl patch deployment yawl -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"yawl","readinessProbe":{"initialDelaySeconds":60}}]}}}}'

# Solution 2: Increase resource limits
kubectl set resources deployment yawl \
  --limits=cpu=2,memory=2Gi \
  --requests=cpu=500m,memory=1Gi

# Solution 3: Check application logs
kubectl logs yawl-xyz -n yawl | tail -100

# Solution 4: Scale down while debugging
kubectl scale deployment yawl --replicas=1 -n yawl

# Solution 5: Check readiness probe
kubectl logs yawl-xyz -n yawl | grep -i "readiness"

# Solution 6: Manually restart pod
kubectl delete pod yawl-xyz -n yawl
```

### Issue: Persistent Volume Claim Pending

**Symptoms:**
```
PVC stuck in Pending state
Pod cannot mount volume
Storage not provisioned
```

**Diagnosis:**

```bash
# 1. Check PVC status
kubectl describe pvc -n yawl

# 2. Check storage class
kubectl get sc
kubectl describe sc standard

# 3. Check node disk space
kubectl describe nodes | grep -A 5 "Allocated resources\|DiskPressure"

# 4. Check PV events
kubectl get events -n yawl | grep -i "pvc\|storage"
```

**Solutions:**

```bash
# Solution 1: Create storage class
kubectl apply -f - << EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-ssd
EOF

# Solution 2: Create PV manually
kubectl apply -f - << EOF
apiVersion: v1
kind: PersistentVolume
metadata:
  name: yawl-pv
spec:
  capacity:
    storage: 100Gi
  accessModes:
    - ReadWriteOnce
  gcePersistentDisk:
    pdName: yawl-disk
    fsType: ext4
EOF

# Solution 3: Delete and recreate PVC
kubectl delete pvc yawl-db-pvc -n yawl
kubectl apply -f k8s/pvc.yaml

# Solution 4: Check node capacity
kubectl describe nodes | grep -E "Allocatable|Allocated"
```

## Data Issues

### Issue: Data Corruption

**Symptoms:**
```
Invalid XML data
Parsing exceptions
Constraint violations
```

**Diagnosis:**

```bash
# 1. Check data integrity
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- Check XML validity
SELECT id, data FROM ypcase WHERE data IS NOT NULL LIMIT 1;

-- Check for null constraints
SELECT * FROM ypcase WHERE id IS NULL;
EOF

# 2. Verify checksums
docker exec yawl-postgres pg_verifybackup /backups/yawl_backup_20260214

# 3. Test restore
docker exec yawl-postgres createdb yawl_test
docker exec yawl-postgres pg_restore -d yawl_test /backups/yawl_backup_20260214
```

**Solutions:**

```bash
# Solution 1: Restore from backup
docker-compose stop yawl
docker exec yawl-postgres dropdb yawl
docker exec yawl-postgres createdb yawl
docker exec yawl-postgres pg_restore -d yawl /backups/yawl_db_20260213.sql.gz

# Solution 2: Repair tables
docker exec yawl-postgres psql -U yawl -d yawl << EOF
REINDEX DATABASE yawl;
ANALYZE;
EOF

# Solution 3: Export valid data subset
docker exec yawl-postgres pg_dump -U yawl yawl \
  --exclude-table='ypcase' > yawl_clean.sql
```

### Issue: Lost or Missing Data

**Symptoms:**
```
Cases disappeared from database
Records missing after restart
Data not persisted
```

**Diagnosis:**

```bash
# 1. Check recent backups
ls -ltr /backups/yawl/

# 2. Verify volume mounts
docker inspect yawl-postgres | grep -A 20 "Mounts"

# 3. Check database size
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT pg_size_pretty(pg_database_size('yawl'));"

# 4. Check for transaction issues
docker logs yawl-engine --since 1h | grep -i "transaction\|rollback"
```

**Solutions:**

```bash
# Solution 1: Restore from backup
docker exec yawl-postgres pg_restore -d yawl /backups/yawl_db_20260213.sql.gz

# Solution 2: Check volume persistence
# Verify docker-compose has correct volume mapping:
services:
  postgres:
    volumes:
      - postgres_data:/var/lib/postgresql/data

# Check volume exists
docker volume ls | grep postgres_data
docker volume inspect postgres_data

# Solution 3: Enable write-ahead logs
docker exec yawl-postgres psql -U postgres << EOF
ALTER SYSTEM SET wal_level = replica;
SELECT pg_reload_conf();
EOF

# Solution 4: Enable transaction logging
JAVA_OPTS="${JAVA_OPTS} -Dlog4j.debug=true"
```

## External Integration Issues

### Issue: Web Service Call Fails

**Symptoms:**
```
HTTP connection timeout
SOAP fault
External service unreachable
```

**Diagnosis:**

```bash
# 1. Test external service directly
curl -I http://external-api.example.com/service

# 2. Check DNS resolution
docker exec yawl-engine nslookup external-api.example.com

# 3. Check firewall rules
docker exec yawl-engine telnet external-api.example.com 443

# 4. View request logs
docker logs yawl-engine | grep -i "external\|soap\|http"

# 5. Check proxy configuration
docker exec yawl-engine env | grep -i "proxy"
```

**Solutions:**

```bash
# Solution 1: Increase timeout
# In workflow configuration:
# <invocation timeout="30000">  <!-- 30 seconds -->

# Solution 2: Configure proxy (if needed)
# In docker-compose.yml:
environment:
  HTTP_PROXY: "http://proxy.example.com:8080"
  HTTPS_PROXY: "http://proxy.example.com:8080"

# Solution 3: Retry logic
# Implement exponential backoff in custom task

# Solution 4: Check SSL certificate
openssl s_client -connect external-api.example.com:443

# Solution 5: Whitelist external service
# Add firewall rules if on private network
docker exec yawl-engine iptables -A OUTPUT -d external-api.example.com -j ACCEPT
```

---

**Troubleshooting Guide Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
