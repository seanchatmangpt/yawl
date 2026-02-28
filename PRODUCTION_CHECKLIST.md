# YAWL v6.0.0 Production Deployment Checklist

**Date:** 2026-02-28  
**Environment:** Production  
**Go/No-Go Decision Point:** Final sign-off required before proceeding

---

## Pre-Deployment (24 hours before)

### Code Readiness
- [ ] **Code Review Complete**
  - All pull requests merged to main
  - No TODOs or FIXMEs in src/
  - Test: `grep -r "TODO\|FIXME" src/ | wc -l` should be 0

- [ ] **Tests Passing**
  - All unit tests pass
  - All integration tests pass
  - Test Coverage >80% for critical paths
  - Command: `mvn clean verify -P prod`
  - Expected: `BUILD SUCCESS, Tests run: XXX, Failures: 0`

- [ ] **Build Successful**
  - Clean build with no errors
  - WAR files generated: yawl.war, resourceService.war, workletService.war
  - Command: `mvn clean package -DskipTests -P prod`

- [ ] **Static Analysis Passed**
  - No critical code quality issues
  - No security vulnerabilities (OWASP Top 10)
  - Command: `mvn clean verify -P analysis`

### System Requirements
- [ ] **Java Version Correct**
  - Version: Java 17+ (optimized for 25)
  - Command: `java -version`
  - Expected output: `openjdk version "25"` or compatible

- [ ] **Memory Available**
  - Minimum: 8 GB free RAM
  - Recommended: 16 GB for production
  - Command: `free -h | grep Mem`
  - Expected: `Mem ... Avail 8G+`

- [ ] **Disk Space Available**
  - Minimum: 20 GB free
  - For full deployment: 50+ GB recommended
  - Command: `df -h / | tail -1`

- [ ] **Network Connectivity**
  - Can reach database server
  - Can reach monitoring endpoints
  - Command: `ping $DB_HOST && ping $MONITORING_HOST`

- [ ] **Time Synchronization**
  - NTP configured and synced
  - Command: `timedatectl` or `ntpq -p`
  - Time skew <1 second

### Infrastructure Preparation
- [ ] **Database Ready**
  - PostgreSQL 13+ running
  - Database created: yawl
  - User configured with appropriate permissions
  - Command: `psql -h $DB_HOST -U $DB_USER -d yawl -c "\dt"`
  - Expected: Tables listed successfully

- [ ] **Database Backup Created**
  - Full backup completed
  - Backup verified (can restore)
  - Location documented: `/backup/yawl-$(date +%Y%m%d)/`
  - Size: >100MB
  - Command: `ls -lh /backup/yawl-*/`

- [ ] **Redis Ready (if used)**
  - Redis 6.0+ running
  - Test command: `redis-cli -h $REDIS_HOST ping`
  - Expected: PONG

- [ ] **Load Balancer Configured**
  - Health check endpoint: `/actuator/health/ready`
  - Timeout: 10 seconds
  - Healthy threshold: 2 consecutive checks
  - Unhealthy threshold: 3 consecutive checks

- [ ] **SSL Certificates Valid**
  - Certificate installed
  - Not expired: `openssl x509 -in cert.pem -noout -dates`
  - Domain matches: `openssl x509 -in cert.pem -noout -subject`

- [ ] **Monitoring Stack Ready**
  - Prometheus: Running and accepting metrics
  - Grafana: Dashboards created and validated
  - AlertManager: Configured for notification channels
  - Test: `curl -s http://prometheus:9090/api/v1/targets | jq '.data.activeTargets | length'`

- [ ] **Log Aggregation Ready**
  - Log forwarding configured (ELK, Splunk, CloudWatch)
  - Test: `curl -H "Content-Type: application/json" -X POST http://logstash:5000/test`

- [ ] **Backup Solution Tested**
  - Full backup + restore cycle completed
  - Restore time measured: <15 minutes expected
  - Backup retention policy documented

---

## Configuration Validation (4 hours before)

### Environment Variables
- [ ] **Database Configuration**
  - `DB_TYPE`: postgres
  - `DB_HOST`: prod-db.example.com (resolvable)
  - `DB_PORT`: 5432
  - `DB_NAME`: yawl
  - `DB_USER`: set from secrets manager
  - `DB_PASSWORD`: set from secrets manager (not hardcoded)

- [ ] **Java Configuration**
  - `JAVA_HOME`: /usr/lib/jvm/java-25-openjdk
  - `JAVA_OPTS`: Heap sizes appropriate for server
  - `-Xms4g -Xmx8g` (minimum)
  - `-XX:+UseZGC` or similar modern GC

- [ ] **Application Configuration**
  - `SPRING_PROFILES_ACTIVE`: production
  - `SPRING_APPLICATION_NAME`: yawl-engine
  - `LOGGING_LEVEL_ROOT`: INFO
  - `LOGGING_LEVEL_ORG_YAWLFOUNDATION`: DEBUG

- [ ] **Monitoring Configuration**
  - `OTEL_SERVICE_NAME`: yawl-engine
  - `OTEL_EXPORTER_OTLP_ENDPOINT`: configured
  - `PROMETHEUS_ENDPOINT`: accessible

- [ ] **Timezone Configuration**
  - `TZ`: UTC (or consistent timezone)
  - Verified: `date` command shows correct time

### Database Configuration
- [ ] **Connection Pool Configured**
  - Min connections: 5
  - Max connections: 20
  - Idle timeout: 300 seconds
  - Connection timeout: 30 seconds

- [ ] **Indexes Created**
  - Specification ID index: `CREATE INDEX idx_spec_id ON yspecification(id);`
  - Case status index: `CREATE INDEX idx_case_status ON ynetinstance(status);`
  - Work item case index: `CREATE INDEX idx_workitem_case ON yworkitem(case_id);`
  - Test: `psql ... -c "\d+"`

- [ ] **Database Statistics Updated**
  - VACUUM ANALYZE completed
  - Command: `psql ... -c "VACUUM ANALYZE;"`

- [ ] **Replication Configured (if HA)**
  - Read replicas: minimum 1
  - Replication lag <1 second
  - Test: `SELECT replication_lag FROM pg_stat_replication;`

### JVM Configuration
- [ ] **Heap Size Appropriate**
  - For 8GB server: `-Xms4g -Xmx8g`
  - For 16GB server: `-Xms8g -Xmx16g`
  - Validation: Application starts without OOM

- [ ] **GC Configuration Optimized**
  - GC algorithm: ZGC or Shenandoah
  - Max GC pause: 20-50ms
  - Full GCs expected: <10/hour

- [ ] **Virtual Threads Enabled (Java 21+)**
  - `-Djdk.virtualThreadScheduler.parallelism=200`
  - `-Djdk.virtualThreadScheduler.maxPoolSize=256`

- [ ] **Heap Dump Configuration**
  - `-XX:+HeapDumpOnOutOfMemoryError`
  - `-XX:HeapDumpPath=/app/logs/heap-dump.hprof`

### Security Configuration
- [ ] **TLS 1.3 Enabled**
  - Weak protocols disabled
  - Test: `openssl s_client -tls1_3 -connect localhost:8443`

- [ ] **No Hardcoded Secrets**
  - Source code audit for credentials
  - Command: `grep -r "password\|secret\|key" src/ | grep -v "//\|#" | wc -l`
  - Expected: 0 (or only environment references)

- [ ] **Secrets Manager Integration**
  - Database password from: AWS Secrets Manager / Vault
  - API keys from: Secrets Manager
  - JWT secret from: Secrets Manager

- [ ] **Database Encryption**
  - Database: Encrypted at rest
  - Backups: Encrypted at rest
  - Network: TLS 1.3 for connections

---

## System Readiness (2 hours before)

### Capacity Planning Validation
- [ ] **CPU Capacity**
  - Available CPU cores: ≥4
  - Expected utilization: <70% at peak
  - Headroom: >2 cores unused

- [ ] **Memory Capacity**
  - Available RAM: ≥8GB
  - Expected utilization: <75% at peak
  - Headroom: >2GB unused

- [ ] **Disk I/O Capacity**
  - Database I/O: <70% utilization
  - Write throughput: >100 MB/s available
  - Read throughput: >200 MB/s available

- [ ] **Network Capacity**
  - Database connection latency: <5ms
  - Expected throughput: >100 Mbps available
  - Bandwidth headroom: >50%

### Monitoring Stack Validation
- [ ] **Prometheus Metrics Collecting**
  - JVM metrics: jvm_memory_*, jvm_gc_*
  - Application metrics: yawl_cases_*, yawl_workitems_*
  - Database metrics: hikaricp_*, pg_*
  - Test: `curl -s http://prometheus:9090/api/v1/query?query=up | jq '.data.result | length'`
  - Expected: >5 targets

- [ ] **Grafana Dashboards Ready**
  - YAWL Engine dashboard created
  - JVM dashboard created
  - Database dashboard created
  - Custom alerts configured

- [ ] **Log Aggregation Working**
  - Logs flowing to central system
  - Can search for application logs
  - Test: Search for timestamp from last 5 minutes

- [ ] **Alert Channels Configured**
  - Slack integration tested
  - PagerDuty/OpsGenie integration tested
  - Email alerts tested
  - Test: `Send test alert → Verify channel receives it`

### Deployment Readiness
- [ ] **Deployment Artifacts Built**
  - Docker image built: `docker images | grep yawl-engine`
  - Image size reasonable: <1.5GB
  - Image scanned for vulnerabilities

- [ ] **Kubernetes Manifests Validated (if applicable)**
  - Syntax checked: `kubectl apply -f k8s/ --dry-run=client`
  - Resource requests/limits set appropriately
  - Health checks configured

- [ ] **Deployment Plan Documented**
  - Step-by-step procedure written
  - Rollback procedure documented
  - Estimated duration: 45-60 minutes
  - Expected downtime: 10-15 minutes

- [ ] **Team Availability Confirmed**
  - Operations team ready
  - On-call engineer standing by
  - Communication channel open (#deployments)

---

## Pre-Deployment Window (30 minutes before)

### Final Validation
- [ ] **Health Check Script Runs Successfully**
  - Command: `bash healthcheck.sh`
  - Expected: All checks PASS

- [ ] **Current System Stable**
  - No active incidents
  - Metrics baseline within expected range
  - No unusual error patterns in logs

- [ ] **Database Accessible**
  - Test connection: `psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "SELECT 1;"`
  - Response time: <100ms

- [ ] **Previous Version Working**
  - Current production is healthy
  - No performance degradation
  - Active case processing normal

### Communication Ready
- [ ] **Stakeholders Notified**
  - Engineering team: deployment starting
  - Operations team: ready for deployment
  - Support team: maintenance window announced
  - Message: Posted in deployment channel with timeline

- [ ] **Maintenance Window Announced**
  - Users notified of expected downtime
  - Duration: 10-15 minutes
  - Time window: off-peak hours
  - Impact: Workflow processing paused, new cases blocked

- [ ] **Escalation Contacts Identified**
  - On-call engineer: [Name, Phone, Slack]
  - Platform lead: [Name, Phone, Slack]
  - CTO/VP: [Name, Phone, Slack] for critical decisions

---

## GO/NO-GO DECISION

**Complete this section 10 minutes before deployment:**

### Technical Readiness
- [ ] All pre-deployment checklists: **PASS**
- [ ] Monitoring systems: **READY**
- [ ] Database: **HEALTHY**
- [ ] Backup: **VERIFIED**
- [ ] Health checks: **ALL PASS**

### Team Readiness
- [ ] Operations team: **READY**
- [ ] On-call engineer: **STANDING BY**
- [ ] Communication channel: **OPEN**
- [ ] Rollback plan: **REVIEWED AND ACCEPTED**

### Business Readiness
- [ ] Stakeholders notified: **YES**
- [ ] Maintenance window acceptable: **YES**
- [ ] Executive approval obtained: **YES**

---

## FINAL GO/NO-GO VOTE

**Deployment Lead:** _________________ **Date/Time:** _____________

**Vote:**
- [ ] **GO** - Proceed with deployment (all criteria met)
- [ ] **NO-GO** - Hold deployment (issues identified)

**If NO-GO, reason:**
```
_________________________________________________________________

_________________________________________________________________

_________________________________________________________________
```

**Approval signatures:**
- Technical Lead: _________________________ Date: _______
- Operations Manager: _________________________ Date: _______
- Platform Team Lead: _________________________ Date: _______

---

## Post-Deployment Verification (to be completed after deployment)

- [ ] All services started successfully
- [ ] Health checks all PASS
- [ ] Database migrations completed
- [ ] Monitoring data flowing
- [ ] No error spikes in logs
- [ ] Performance within baseline
- [ ] Users can create/execute workflows
- [ ] Stakeholders notified of success

**Deployment Duration:** _______ minutes (target: 45-60)
**Downtime:** _______ minutes (target: <15)
**Status:** ✓ SUCCESS / ✗ ROLLBACK

