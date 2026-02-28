# YAWL v6.0.0 Production Deployment Checklist

**Date**: __________  
**Deployment Engineer**: __________  
**Approval**: __________  
**Go/No-Go Decision**: __________

---

## Pre-Deployment Phase (48 hours before)

### System Requirements Verification

- [ ] **Java 25 Installation**
  - Version check: `java -version`
  - Required: 25.0.0 or later
  - Command: `java -version 2>&1 | grep "25"`
  - Expected output: `openjdk version "25" ...`

- [ ] **Disk Space**
  - Minimum: 10GB free space
  - Recommended: 50GB for data growth
  - Check: `df -h / | awk 'NR==2 {print $4}'`
  - Result: __________GB available

- [ ] **Memory (RAM)**
  - Minimum: 8GB total
  - Recommended: 16GB+ for production
  - JVM allocation: 4GB heap minimum
  - Check: `free -h | awk 'NR==2'`
  - Result: __________GB available

- [ ] **Network Connectivity**
  - Database host reachable: `nc -zv db.example.com 5432`
  - Registry accessible: `curl -I registry.example.com`
  - NTP synchronized: `timedatectl`
  - Time offset: __________ms

- [ ] **Container Runtime (if Docker)**
  - Docker installed: `docker --version`
  - Version: __________
  - Docker daemon running: `systemctl status docker`
  - Docker socket writable: `ls -l /var/run/docker.sock`

- [ ] **Kubernetes Cluster (if K8s)**
  - Cluster accessible: `kubectl cluster-info`
  - Nodes healthy: `kubectl get nodes`
  - Storage classes available: `kubectl get storageclass`
  - Node count: __________
  - Available capacity: __________CPU, __________GB RAM

### Database Validation

- [ ] **PostgreSQL Installation**
  - Version: 12+
  - Check: `psql --version`
  - Result: __________

- [ ] **Database Created**
  - Database name: yawl_prod
  - Owner: yawl_user
  - Check: `psql -U postgres -lqt | grep yawl_prod`
  - Result: ✓ Exists / ✗ Missing

- [ ] **User Permissions**
  - User created: `yawl_user`
  - Password set (not blank)
  - Password entropy: __________bits (minimum 128)
  - Test connection: `psql -U yawl_user -d yawl_prod -c "SELECT 1"`
  - Result: ✓ Connected / ✗ Failed

- [ ] **Extensions Installed**
  - pgcrypto: `SELECT * FROM pg_extension WHERE extname='pgcrypto'`
  - uuid-ossp: `SELECT * FROM pg_extension WHERE extname='uuid-ossp'`
  - Both present: ✓ Yes / ✗ No

- [ ] **SSL Configuration (if required)**
  - SSL enabled: `grep "ssl = on" /etc/postgresql/*/main/postgresql.conf`
  - Certificate path: __________
  - Key path: __________
  - Certificate valid until: __________

- [ ] **Backup System Tested**
  - Backup directory created: /var/backups/yawl
  - Cron job scheduled: `crontab -l | grep backup-yawl`
  - Test backup successful: ✓ Yes / ✗ No
  - Restore tested: ✓ Yes / ✗ No

### JVM Configuration Validation

- [ ] **JAVA_HOME Set**
  - Command: `echo $JAVA_HOME`
  - Result: __________
  - Path exists: `ls -d $JAVA_HOME`

- [ ] **JVM Flags Prepared**
  - Config file location: __________
  - Initial heap (-Xms): __________
  - Maximum heap (-Xmx): __________
  - GC algorithm: Shenandoah / ZGC / Other: __________
  - Flags reviewed: ✓ Yes / ✗ No

- [ ] **GC Logging Enabled**
  - GC log directory: /app/logs
  - Permissions (writable by app user): ✓ Yes / ✗ No
  - Log rotation configured: ✓ Yes / ✗ No

- [ ] **Memory Calculation**
  - Total system RAM: __________GB
  - JVM heap allocation: __________GB
  - Ratio (heap/total): __________% (max 75%)
  - OS buffer cache reserved: __________GB

### Networking & Security

- [ ] **Port Availability**
  - Port 8080 (HTTP): `netstat -tuln | grep 8080` → ✗ In use / ✓ Available
  - Port 8081 (MCP): `netstat -tuln | grep 8081` → ✗ In use / ✓ Available
  - Port 9090 (Metrics): `netstat -tuln | grep 9090` → ✗ In use / ✓ Available
  - Port 5432 (PostgreSQL): `netstat -tuln | grep 5432` → ✗ In use / ✓ Available

- [ ] **Firewall Rules**
  - Firewall enabled: `ufw status` → Active / Inactive
  - Rules loaded: `ufw show added`
  - Rules reviewed: ✓ Yes / ✗ No
  - Allow list complete: ✓ Yes / ✗ No

- [ ] **SSL/TLS Certificate**
  - Certificate location: __________
  - Keystore location: __________
  - Keystore password set (secure): ✓ Yes / ✗ No
  - Certificate valid from: __________
  - Certificate valid until: __________ (minimum 30 days)
  - Chain complete: ✓ Yes / ✗ No

- [ ] **Secrets Management**
  - DB password: Stored in secrets manager / Environment variable / Kubernetes secret
  - JWT secret: Generated (minimum 32 bytes)
  - API keys: ✓ All rotated / ✗ Some missing
  - Secrets encrypted at rest: ✓ Yes / ✗ No

---

## Deployment Phase (Day of deployment)

### Image/Artifact Preparation

- [ ] **Docker Image Built** (if using Docker)
  - Image name: yawl-engine:6.0.0
  - Build command executed: `docker build ...`
  - Build successful: ✓ Yes / ✗ No
  - Build time: __________seconds
  - Image size: __________MB

- [ ] **Image Verification**
  - Image exists: `docker image ls | grep yawl-engine`
  - Layers inspected: `docker image inspect yawl-engine:6.0.0`
  - Health check present: ✓ Yes / ✗ No
  - Non-root user (dev): ✓ Yes / ✗ No

- [ ] **JAR/WAR Artifacts**
  - Build completed: `mvn clean package`
  - Build status: ✓ Successful / ✗ Failed
  - Artifacts location: __________
  - Checksum verified: ✓ Yes / ✗ No

- [ ] **Registry Push** (if applicable)
  - Image pushed: `docker push registry/yawl-engine:6.0.0`
  - Push successful: ✓ Yes / ✗ No
  - Image accessible: `docker pull registry/yawl-engine:6.0.0`

### Deployment Execution

- [ ] **Configuration Applied**
  - Environment file: /etc/yawl/prod.env
  - ConfigMap created (K8s): ✓ Yes / ✗ No
  - Secrets created: ✓ Yes / ✗ No
  - Config validation passed: ✓ Yes / ✗ No

- [ ] **Container/Service Start**
  - Service started: `systemctl start yawl` OR `docker run ...` OR `kubectl apply ...`
  - Start successful: ✓ Yes / ✗ No
  - Startup time: __________seconds
  - Error logs: __________

- [ ] **Initial Health Verification**
  - HTTP endpoint responds: `curl http://localhost:8080`
  - Status code: __________
  - Liveness probe passes: ✓ Yes / ✗ No
  - Readiness probe passes: ✓ Yes / ✗ No

### Post-Deployment Validation

- [ ] **Connectivity Tests**
  - Database reachable: ✓ Yes / ✗ No
  - Query succeeds: `SELECT 1 FROM yawl.cases LIMIT 1`
  - Connection pool healthy: ✓ Yes / ✗ No
  - Connection count: __________ (should be <30)

- [ ] **API Functionality**
  - Health endpoint: `curl https://localhost:8080/actuator/health`
  - Response status: ✓ 200 UP / ✗ Other
  - Create case: `curl -X POST https://localhost:8080/api/v1/cases`
  - Case creation latency: __________ms (target: <500ms)

- [ ] **Metrics Collection**
  - Prometheus scrape endpoint: `curl https://localhost:9090/actuator/prometheus`
  - Response status: ✓ 200 / ✗ Other
  - Metrics present: ✓ Yes / ✗ No
  - Sample metrics:
    - jvm_memory_used_bytes: ✓ Present / ✗ Missing
    - http_requests_total: ✓ Present / ✗ Missing
    - jvm_gc_pause: ✓ Present / ✗ Missing

- [ ] **Observability**
  - Log aggregation working: ✓ Yes / ✗ No
  - Logs visible: `tail -f /app/logs/yawl.log`
  - Error count: __________
  - Warning count: __________
  - Latest errors: __________

- [ ] **Security Checks**
  - TLS 1.3+ enforced: `openssl s_client -connect localhost:8080 -tls1_3`
  - Certificate valid: ✓ Yes / ✗ No
  - Cipher strength: __________bits minimum
  - Non-root user running: `ps aux | grep yawl`
  - Result: ✓ Running as 'dev' / ✗ Other

- [ ] **Resource Consumption**
  - CPU usage: __________% (should be <30% idle)
  - Memory usage: __________MB / __________MB max
  - Disk I/O: __________IOPS
  - Network latency to DB: __________ms

### Performance Baseline

- [ ] **Throughput Measurement**
  - Command: `ab -n 1000 -c 10 https://localhost:8080/api/v1/cases`
  - Requests/second: __________
  - Average response time: __________ms
  - 95th percentile: __________ms
  - Target: >100 req/s

- [ ] **GC Pause Analysis**
  - Command: `curl https://localhost:9090/actuator/metrics/jvm.gc.pause | jq`
  - Average GC pause: __________ms
  - Max GC pause: __________ms
  - Pause frequency: __________pauses/minute
  - Target: <50ms average

- [ ] **Case Creation Performance**
  - Target: <500ms per case
  - Measured: __________ms
  - Consistency check (10 samples):
    - Min: __________ms
    - Max: __________ms
    - Variance: __________ms

- [ ] **Query Performance**
  - Sample query: `SELECT * FROM yawl.cases WHERE status='ACTIVE' LIMIT 100`
  - Execution time: __________ms
  - Rows returned: __________
  - Index usage: ✓ Yes / ✗ No

---

## Sign-Off Phase

### Technical Approval

- [ ] **Operations Team**
  - Reviewed deployment procedures: ✓ Yes / ✗ No
  - Confirmed system meets requirements: ✓ Yes / ✗ No
  - Monitoring configured: ✓ Yes / ✗ No
  - Backup procedures tested: ✓ Yes / ✗ No
  - Approved by: ______________ Date: __________

- [ ] **Security Review**
  - SSL/TLS verified: ✓ Yes / ✗ No
  - Secrets manager configured: ✓ Yes / ✗ No
  - Firewall rules reviewed: ✓ Yes / ✗ No
  - Non-root user enforced: ✓ Yes / ✗ No
  - Approved by: ______________ Date: __________

- [ ] **Performance Review**
  - Baseline performance acceptable: ✓ Yes / ✗ No
  - GC pauses <50ms: ✓ Yes / ✗ No
  - Throughput >100 req/s: ✓ Yes / ✗ No
  - Database latency <100ms: ✓ Yes / ✗ No
  - Approved by: ______________ Date: __________

### Stakeholder Sign-Off

- [ ] **Deployment Manager**
  - All checks passed: ✓ Yes / ✗ No
  - Risk assessment: ✓ Low / ⚠ Medium / ✗ High
  - Rollback plan confirmed: ✓ Yes / ✗ No
  - Approved: ______________ Date: __________
  - Go/No-Go: **GO** / **NO-GO**

### Production Cutover

- [ ] **Load Balancer Configuration** (if applicable)
  - Traffic routed to new deployment: ✓ Yes / ✗ No
  - Old deployment still available: ✓ Yes / ✗ No
  - Cutover time: __________
  - Success criteria met: ✓ Yes / ✗ No

- [ ] **Canary Deployment** (if applicable)
  - 10% traffic routed to new version: ✓ Yes / ✗ No
  - Monitoring for 1 hour: ✓ Yes / ✗ No
  - Error rate <0.1%: ✓ Yes / ✗ No
  - Latency increase <5%: ✓ Yes / ✗ No
  - Proceed to 100%: ✓ Yes / ✗ No

- [ ] **Production Monitoring** (48 hours post-deployment)
  - Error rate: __________% (target: <0.01%)
  - P99 latency: __________ms (target: <1000ms)
  - GC pause incidents: __________  (target: 0)
  - Database connection errors: __________ (target: 0)
  - Memory leaks detected: ✓ Yes / ✗ No

---

## Post-Deployment Checklist (24 hours after)

- [ ] **System Stability**
  - Uptime: __________hours (target: 100%)
  - Restarts: __________ (target: 0)
  - Errors in logs: __________
  - Critical issues: ✓ None / ⚠ Some

- [ ] **Performance Verification**
  - Throughput stable: ✓ Yes / ✗ No
  - Latency stable: ✓ Yes / ✗ No
  - Resource usage stable: ✓ Yes / ✗ No
  - GC behavior expected: ✓ Yes / ✗ No

- [ ] **Backup Verification**
  - Backup executed: ✓ Yes / ✗ No
  - Backup size: __________MB
  - Restore tested: ✓ Yes / ✗ No
  - Restore time: __________seconds

- [ ] **Issue Resolution**
  - Issues found: __________ (count)
  - Issues resolved: __________ (count)
  - Remaining issues: __________
  - Next review date: __________

---

## Go/No-Go Decision

### Summary

| Area | Status | Notes |
|------|--------|-------|
| System Requirements | ✓ / ✗ | __________ |
| Database | ✓ / ✗ | __________ |
| Security | ✓ / ✗ | __________ |
| Performance | ✓ / ✗ | __________ |
| Monitoring | ✓ / ✗ | __________ |
| Backups | ✓ / ✗ | __________ |

### Final Decision

- **GO** - All checks passed, deployment successful
- **NO-GO** - Issues found, see notes below

**Issues requiring resolution**:
1. __________
2. __________
3. __________

**Mitigation plan**: __________

**Approved by**: ______________ Date: __________ Time: __________

---

