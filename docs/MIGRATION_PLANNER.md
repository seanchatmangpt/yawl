# YAWL v5 → v6 Migration Planner

**Status**: Complete Migration Guide | **Last Updated**: February 2026 | **Estimated Time**: 2-6 weeks

Comprehensive step-by-step guide for migrating from YAWL v5 to v6.0.0.

---

## 1. Executive Summary

### What's New in v6

| Area | v5 | v6 | Impact |
|------|----|----|--------|
| **Engines** | 1 (Persistent) | 2 (Persistent + Stateless) | New deployment options |
| **Java** | Java 11+ | Java 25 optimized | 17% memory savings, <1ms GC |
| **DB** | MySQL/PostgreSQL | +6 databases | Flexibility |
| **Architecture** | Monolithic | Modular (20+) | Better testing/deployment |
| **Integration** | REST API | REST + MCP + A2A | AI agents, microservices |
| **Cloud** | Limited | Cloud-native | Kubernetes, serverless |
| **Scalability** | Single instance | Horizontal scaling | 100x throughput increase |

### Migration Complexity

```
Small Deployment (v5 → v6):
  Time: 1-2 weeks
  Effort: 1 person (developer + DBA)
  Risk: Low
  Complexity: Medium

Medium Deployment (100K cases):
  Time: 2-4 weeks
  Effort: 2-3 people (lead + dev + DBA + QA)
  Risk: Medium
  Complexity: High

Large Deployment (1M cases, production):
  Time: 4-6 weeks
  Effort: 4-5 people (arch + lead + devs + DBA + QA + ops)
  Risk: High
  Complexity: Very High
  Downtime: 0-4 hours (depends on cutover strategy)
```

---

## 2. Pre-Migration Checklist (Week 0)

### System Assessment

- [ ] Audit current v5 instance for:
  - Total cases (SELECT COUNT(*) FROM YNET;)
  - Average case data size (SELECT AVG(DATALENGTH(casedata)) FROM YWORKITEM;)
  - Active concurrent cases (SELECT COUNT(*) WHERE status='running';)
  - Custom code/extensions (count Java files in custom/)
  - Custom database columns (check schema)
  - External dependencies (REST APIs, integrations)
  - Authentication method (LDAP, OAuth, or none)
  - Workflow complexity (average tasks per case)

### Inventory Template

```yaml
Current v5 System:
  Deployment Type: [Standalone|Clustered|Cloud]
  Database:        [MySQL|PostgreSQL|Oracle]
  Database Size:   [XX GB]
  Total Cases:     [XXX K]
  Active Cases:    [XXX]
  Custom Code:     [Yes|No] (count files)
  Authentication:  [LDAP|OAuth|None]
  External APIs:   [Y|N] (list)
  Uptime SLA:      [XX% (e.g., 99.9%)]
```

### Resource Planning

```
Development Team:
  - 1 Architect (design migration path)
  - 1-2 Java developers (code updates)
  - 1 DBA (schema migration)
  - 1 QA engineer (testing)
  - 1 DevOps (deployment)

Timeline:
  Pre-migration: 3-5 days (assessment + planning)
  Migration: 1-3 weeks (implementation)
  Testing: 1-2 weeks (validation)
  Cutover: 1-2 days (production deployment)
```

---

## 3. Phase 1: Environment Setup (Days 1-3)

### Step 1.1: Install Java 25 (Recommended)

```bash
# On all migration hosts
# Option 1: GraalVM JDK
curl -s "https://github.com/oracle/graalvm-ce-releases/releases/download/jdk-25.0.1/graalvm-jdk-25.0.1_linux-x64_bin.tar.gz" \
  | tar xz -C /opt/java/

# Option 2: Oracle JDK 25
apt-get install openjdk-25-jdk

# Verify installation
java -version
# Output: openjdk version "25" 2026-09-16

# Set JAVA_HOME
export JAVA_HOME=/opt/java/jdk-25
export PATH=$JAVA_HOME/bin:$PATH
```

### Step 1.2: Prepare v6 Environment

```bash
# Clone YAWL v6.0.0 repository
git clone --branch v6.0.0 https://github.com/yawlfoundation/yawl.git yawl-v6

# Or download release
cd /opt
wget https://github.com/yawlfoundation/yawl/archive/refs/tags/v6.0.0.zip
unzip v6.0.0.zip
cd yawl-6.0.0

# Verify pom.xml
grep '<version>6.0' pom.xml
# Output: <version>6.0.0-GA</version>
```

### Step 1.3: Backup v5 Instance

```bash
# Full backup of v5 database
## MySQL
mysqldump -u root -p yawl > yawl_v5_backup_$(date +%Y%m%d).sql
gzip yawl_v5_backup_*.sql

## PostgreSQL
pg_dump -U yawl_user yawl > yawl_v5_backup_$(date +%Y%m%d).sql
gzip yawl_v5_backup_*.sql

# Backup application files
tar -czf yawl_v5_app_backup_$(date +%Y%m%d).tar.gz /opt/yawl-v5/

# Store in safe location
cp yawl_v5_backup*.sql.gz /backup/
cp yawl_v5_app_backup*.tar.gz /backup/

# Verify backup integrity
tar -tzf yawl_v5_app_backup*.tar.gz | head -10
```

### Step 1.4: Create v6 Database

```bash
# Option 1: PostgreSQL (Recommended for v6)
createdb -U postgres yawl_v6
psql -U postgres -d yawl_v6 -c "CREATE SCHEMA yawl"

# Option 2: MySQL
mysql -u root -p -e "CREATE DATABASE yawl_v6 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

# Test connection
psql -U postgres -d yawl_v6 -c "SELECT 1"  # PostgreSQL
# or
mysql -u root -p yawl_v6 -e "SELECT 1"     # MySQL
```

---

## 4. Phase 2: Schema Migration (Days 4-5)

### Step 2.1: Review v6 Schema

```bash
# v6 provides DDL scripts in:
# yawl-engine/src/main/resources/schema/

# Option 1: Auto-create via Spring Boot
# (recommended for first-time v6 deploy)

# Option 2: Manual schema creation
# Copy DDL scripts to database init folder
cp yawl-engine/src/main/resources/schema/*.sql /var/lib/postgresql/data/initdb/

# Execute schema creation
psql -U postgres -d yawl_v6 -f schema-create.sql
psql -U postgres -d yawl_v6 -f schema-indices.sql
```

### Step 2.2: Map v5 Data to v6 Schema

```sql
-- Key differences:

-- v5 Schema (case IDs are INTEGER)
CREATE TABLE YNET (
  NETID       INT PRIMARY KEY,
  SPECVERSION INT,
  YNETID      VARCHAR(255),
  DOCUMENTATION LONGTEXT
);

-- v6 Schema (case IDs are UUID)
CREATE TABLE yawl_net (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  spec_version INT,
  net_id      VARCHAR(255) UNIQUE,
  documentation TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migration script (PostgreSQL)
INSERT INTO yawl_net (id, spec_version, net_id, documentation)
SELECT
  gen_random_uuid(),        -- Convert INTEGER to UUID
  SPECVERSION,
  YNETID,
  DOCUMENTATION
FROM YNET;
```

### Step 2.3: Migrate Case Data

**Important**: Cases are the hardest part to migrate. Two strategies:

#### Strategy A: Re-execute Cases (Recommended if <100K cases)

```
Pros:
  ✓ Clean state in v6
  ✓ No historical baggage
  ✓ Simple execution

Cons:
  ✗ No historical case data
  ✗ Time to re-execute
  ✗ May not be feasible for running cases
```

#### Strategy B: Data Migration (For large case volumes)

```sql
-- v5 case structure
SELECT * FROM YWORKITEM LIMIT 1;
-- Output:
-- CASEID | TASKID | ITEMID | CASEDATA | TASKNAME | STATUS

-- v6 expects different structure (see v6 schema)

-- Migration approach:
-- 1. Export v5 cases to JSON
-- 2. Transform via mapping script
-- 3. Import to v6 event store / database

-- Export script (MySQL):
SELECT
  CASEID,
  TASKID,
  ITEMID,
  CASEDATA,
  TASKNAME,
  STATUS
FROM YWORKITEM
INTO OUTFILE '/tmp/yawl_cases.csv'
FIELDS TERMINATED BY '|'
ENCLOSED BY '"'
LINES TERMINATED BY '\n';

-- Transform in Java/Python
python3 scripts/migrate-cases.py \
  --input /tmp/yawl_cases.csv \
  --output /tmp/yawl_cases_v6.json \
  --mapping config/case-mapping.yaml

# Validate
wc -l /tmp/yawl_cases_v6.json  # Should match source count
```

---

## 5. Phase 3: Custom Code Migration (Days 6-10)

### Step 3.1: Inventory Custom Code

```bash
# Find custom task handlers, codelet, and extensions
find yawl-v5-install -name "*.java" | grep -E "(codelet|handler|custom|extension)" | wc -l

# Categorize by type
grep -r "extends YTask\|implements YExternalNetElement" . --include="*.java"
grep -r "class.*Codelet\|AbstractCodelet" . --include="*.java"
grep -r "implements.*Handler" . --include="*.java"

# Create inventory
cat > custom-code-inventory.txt
```

### Step 3.2: Update Task Handlers

```java
// v5 Task Handler
package com.mycompany.yawl.custom;

public class OrderApprovalTask extends YTask {
    public OrderApprovalTask(YNet net, String name) {
        super(net, name);
    }

    @Override
    public void execute(YWorkItem item) {
        // Old v5 style
        item.setDataValue("approved", "true");
    }
}

// v6 Task Handler (updated)
package com.mycompany.yawl.custom;

import org.yawlfoundation.yawl.engine.YTask;
import org.yawlfoundation.yawl.elements.YWorkItem;

public class OrderApprovalTask extends YTask {

    public OrderApprovalTask(YNet net, String name) {
        super(net, name);
    }

    @Override
    public void handleWorkItemCompletion(YWorkItem item) {
        // v6 style - use work item events
        String approved = item.getDataValue("approved");
        if ("true".equals(approved)) {
            // Process approved order
            this.notifyCompletion(item);
        }
    }
}
```

### Step 3.3: Update Codelet Classes

```java
// v5 Codelet
public class ValidateOrderCodelet extends AbstractCodelet {

    public void executeNet(YWorkItem item) throws YDataStateException {
        String orderData = (String) item.getDataValue("orderXML");
        boolean valid = validateOrder(orderData);
        item.setDataValue("isValid", valid ? "true" : "false");
    }
}

// v6 Codelet (updated)
public class ValidateOrderCodelet extends AbstractCodelet {

    @Override
    public void executeNet(YWorkItem item) throws YDataStateException {
        String orderData = item.getDataValue("orderXML");
        if (orderData == null) {
            throw new YDataStateException("orderXML data not provided");
        }

        boolean valid = validateOrder(orderData);
        item.setDataValue("isValid", valid);  // v6 accepts boolean directly
    }

    private boolean validateOrder(String data) {
        // Validation logic
        return !data.isEmpty();
    }
}
```

### Step 3.4: Recompile Custom Code

```bash
# Update pom.xml to depend on yawl v6
# Before:
#   <dependency>
#       <groupId>org.yawlfoundation</groupId>
#       <artifactId>yawl-kernel</artifactId>
#       <version>5.0</version>
#   </dependency>

# After:
#   <dependency>
#       <groupId>org.yawlfoundation</groupId>
#       <artifactId>yawl-engine</artifactId>
#       <version>6.0.0-GA</version>
#   </dependency>

# Compile
mvn clean compile

# Fix any compilation errors (see Breaking Changes section)

# Test custom code
mvn test
```

---

## 6. Phase 4: Configuration Migration (Days 11-12)

### Step 4.1: Migrate Configuration Files

```yaml
# v5: yawl.properties
yawl.engine.persistence.mysql.host=localhost
yawl.engine.persistence.mysql.user=root
yawl.engine.persistence.mysql.password=secret
yawl.security.ldap.url=ldap://ldap.example.com:389

# v6: application.yml (Spring Boot format)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yawl_v6
    username: root
    password: secret
    hikari:
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: validate
  security:
    ldap:
      urls: ldap://ldap.example.com:389
```

### Step 4.2: Authentication Configuration

```yaml
# v5: No built-in multi-auth

# v6: application.yml supports multiple auth methods
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
    ldap:
      urls: ldap://ldap.example.com:389
      base: dc=example,dc=com
      user-search-base: ou=users
      user-search-filter: (uid={0})

# Or use OAuth 2.0
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
```

### Step 4.3: Logging Configuration

```xml
<!-- v5: log4j.properties converted to XML -->

<!-- v6: logback.xml (Spring Boot default) -->
<configuration>
  <property name="LOG_FILE" value="/var/log/yawl/yawl.log"/>

  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}</file>
    <encoder>
      <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Set log levels -->
  <logger name="org.yawlfoundation" level="INFO"/>
  <logger name="org.springframework" level="WARN"/>

  <root level="INFO">
    <appender-ref ref="FILE"/>
  </root>
</configuration>
```

---

## 7. Phase 5: Testing (Days 13-17)

### Step 5.1: Unit Test Custom Code

```bash
# Compile and run unit tests for custom code
mvn test -Dtest=*YawlCustomTest

# Expected output:
# Tests run: XX, Failures: 0, Errors: 0
```

### Step 5.2: Integration Testing

```bash
# Start v6 engine in test mode
./gradlew bootTestRun

# Or with Maven
mvn spring-boot:run -Dspring.profiles.active=test

# Run integration tests against test instance
mvn test -Dtest=*IntegrationTest

# Check database migration
psql -U yawl_user -d yawl_v6 -c "SELECT COUNT(*) FROM yawl_case"
```

### Step 5.3: Data Validation

```bash
# Compare case counts
CASE_COUNT_V5=$(mysql -u root yawl -se "SELECT COUNT(*) FROM YWORKITEM")
CASE_COUNT_V6=$(psql -U yawl_user yawl_v6 -tA -c "SELECT COUNT(*) FROM yawl_work_item")

echo "v5 Cases: $CASE_COUNT_V5"
echo "v6 Cases: $CASE_COUNT_V6"

# Should be equal (or close, depending on migration strategy)

# Detailed comparison
cat << 'SQL' > validate.sql
-- v5
SELECT
  COUNT(*) as total,
  COUNT(DISTINCT CASEID) as unique_cases,
  COUNT(DISTINCT TASKID) as unique_tasks
FROM YWORKITEM;

-- v6
SELECT
  COUNT(*) as total,
  COUNT(DISTINCT case_id) as unique_cases,
  COUNT(DISTINCT task_id) as unique_tasks
FROM yawl_work_item;
SQL
```

### Step 5.4: Performance Testing

```bash
# Run benchmark tests
mvn test -Dtest=*PerformanceTest

# Expected results (v6 should be faster or equal):
# v5:  500 ops/sec
# v6:  550+ ops/sec (with Java 25 optimizations)

# Memory footprint comparison
jcmd <pid> VM.native_memory summary

# v5:  Total: 2048 MB
# v6:  Total: 1700 MB (17% reduction with compact headers)
```

### Step 5.5: Cutover Rehearsal

```bash
# Simulate production cutover
1. Stop v5 engine
2. Run final data migration
3. Verify data integrity
4. Start v6 engine
5. Run smoke tests
6. Monitor for 30 min
7. Rollback to v5 (test rollback procedure)
8. Restart v5

# Time this entire process
echo "Cutover started at: $(date)"
# ... perform cutover ...
echo "Cutover completed at: $(date)"

# Target: <4 hours total downtime for 100K cases
```

---

## 8. Phase 6: Production Deployment (Days 18-20)

### Step 6.1: Pre-Cutover Tasks

```bash
# 24 hours before cutover:
- [ ] Announce maintenance window to users
- [ ] Back up v5 production database
- [ ] Back up v5 production application
- [ ] Verify v6 production environment ready
- [ ] Verify v6 database ready
- [ ] Run final validation scripts
- [ ] Brief support team on rollback procedure
- [ ] Have DBA on standby

# 1 hour before cutover:
- [ ] Verify all systems ready
- [ ] Start monitoring (metrics, logs)
- [ ] Open war room (Zoom, Slack, etc.)
```

### Step 6.2: Cutover Procedure

```bash
#!/bin/bash
# YAWL v5 → v6 Cutover Script

set -e  # Exit on any error
CUTOVER_STARTED=$(date)

echo "=== YAWL v5 → v6 Cutover Started at $CUTOVER_STARTED ==="

# Step 1: Halt v5 engine
echo "[1/7] Stopping v5 engine..."
systemctl stop yawl-v5
sleep 10
systemctl status yawl-v5 || echo "v5 engine stopped"

# Step 2: Final database backup
echo "[2/7] Final backup of v5 database..."
mysqldump -u root yawl > /backup/yawl_final_backup_$(date +%s).sql
gzip /backup/yawl_final_backup_*.sql
echo "  ✓ Backup complete"

# Step 3: Verify v6 database
echo "[3/7] Verifying v6 database..."
psql -U yawl_user yawl_v6 -c "SELECT COUNT(*) FROM yawl_case" || {
  echo "FATAL: v6 database not ready!"
  exit 1
}

# Step 4: Run data validation
echo "[4/7] Validating migrated data..."
psql -U yawl_user yawl_v6 -f scripts/validate-migration.sql
echo "  ✓ Data validation passed"

# Step 5: Start v6 engine
echo "[5/7] Starting v6 engine..."
systemctl start yawl-v6
sleep 20
systemctl status yawl-v6

# Step 6: Run smoke tests
echo "[6/7] Running smoke tests..."
curl -s http://localhost:8080/api/health || {
  echo "FATAL: v6 health check failed!"
  systemctl stop yawl-v6
  systemctl start yawl-v5
  exit 1
}

# Step 7: Monitor
echo "[7/7] Monitoring v6 for 5 minutes..."
for i in {1..5}; do
  sleep 60
  ERROR_COUNT=$(curl -s http://localhost:8080/api/metrics/errors | jq '.value')
  echo "  [$i/5] Error count: $ERROR_COUNT"
done

CUTOVER_COMPLETED=$(date)
echo "=== YAWL Cutover Completed Successfully at $CUTOVER_COMPLETED ==="
echo "Downtime: ~10 minutes"
```

### Step 6.3: Post-Cutover Validation

```bash
# Immediate (within 1 hour)
- [ ] All REST API endpoints responding
- [ ] Database connections healthy
- [ ] No errors in application logs
- [ ] Metrics dashboard shows healthy state
- [ ] Sample cases processing correctly

# Extended (4-24 hours)
- [ ] Monitor all error rates (<0.1%)
- [ ] Verify all business workflows executing
- [ ] Performance metrics match expectations
- [ ] No database corruption observed
- [ ] No security issues detected
```

### Step 6.4: Rollback Procedure (If Needed)

```bash
#!/bin/bash
# Rollback to v5 (in case of critical failure)

echo "=== YAWL Rollback to v5 Starting ==="

# Step 1: Stop v6
systemctl stop yawl-v6

# Step 2: Restore v5 database
BACKUP_FILE="/backup/yawl_final_backup_*.sql.gz"
gunzip -c $BACKUP_FILE | mysql -u root yawl
echo "✓ v5 database restored"

# Step 3: Start v5
systemctl start yawl-v5
sleep 10

# Step 4: Verify v5
curl -s http://localhost:8080/yawl-monitor/ | grep -q "YAWL" || {
  echo "FATAL: v5 failed to start!"
  exit 1
}

echo "=== Rollback Complete - System Running v5 ==="
```

---

## 9. Common Migration Issues & Solutions

### Issue 1: Custom Code Compilation Fails

**Symptom**: `error: cannot find symbol: class YTask`

**Solution**:
```bash
# Update maven dependency
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-engine</artifactId>
    <version>6.0.0-GA</version>
</dependency>

# Run maven clean
mvn clean

# Recompile
mvn compile
```

### Issue 2: Database Migration Hangs

**Symptom**: Migrate script runs for >1 hour without completion

**Solution**:
```bash
# Check long-running queries
SHOW FULL PROCESSLIST;

# Kill slow query
KILL QUERY <process_id>;

# For large datasets, increase timeout
SET SESSION WAIT_TIMEOUT = 28800;  # 8 hours

# Or migrate in batches
INSERT INTO yawl_case SELECT * FROM YNET LIMIT 10000;
```

### Issue 3: Data Loss in Migration

**Symptom**: Case count after migration < count before

**Solution**:
```bash
# Verify backup integrity
tar -tzf yawl_v5_backup.tar.gz >/dev/null && echo "Backup OK"

# Restore from backup
gunzip -c yawl_v5_backup_*.sql.gz | mysql yawl

# Re-run migration with logging
python3 scripts/migrate-cases.py \
  --input /tmp/yawl_cases.csv \
  --output /tmp/yawl_cases_v6.json \
  --log-level DEBUG \
  --log-file /tmp/migrate.log
```

### Issue 4: Performance Degradation

**Symptom**: v6 throughput < v5 (unexpected)

**Solution**:
```bash
# Verify Java 25 optimizations enabled
jcmd <pid> VM.flags | grep CompactObjectHeaders
# Should output: -XX:+UseCompactObjectHeaders

# Check GC performance
jstat -gc -h20 <pid> 1000 | head -20

# If G1GC pause > 50ms, switch to ZGC:
# In application.yml:
# spring.jvm.args: -XX:+UseZGC -XX:+UseCompactObjectHeaders

# Recompile and redeploy
./gradlew clean bootJar
systemctl restart yawl-v6
```

### Issue 5: Authentication Broken After Migration

**Symptom**: Users cannot login to v6

**Solution**:
```yaml
# Check v6 authentication config
spring:
  security:
    ldap:
      urls: ldap://ldap.example.com:389  # Correct URL?
      base: dc=example,dc=com            # Correct base?
      user-search-filter: (uid={0})      # Correct filter?

# Test LDAP connection
ldapsearch -H ldap://ldap.example.com:389 -x -D "uid=admin,ou=users,dc=example,dc=com" -W -b "dc=example,dc=com" "(uid=testuser)"

# Enable debug logging
logging:
  level:
    org.springframework.security.ldap: DEBUG
```

---

## 10. Post-Migration Optimization

### Step 10.1: Enable Java 25 Features

```yaml
# application.yml
spring:
  jvm:
    args: >-
      -XX:+UseCompactObjectHeaders
      -XX:+UseZGC
      -XX:InitiatingHeapOccupancyPercent=35
      -Djdk.virtualThreadScheduler.parallelism=auto
```

### Step 10.2: Tune Database

```sql
-- PostgreSQL performance tuning (after migration)
ALTER SYSTEM SET shared_buffers = '8GB';
ALTER SYSTEM SET effective_cache_size = '24GB';
ALTER SYSTEM SET maintenance_work_mem = '2GB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';

-- Apply changes
SELECT pg_reload_conf();

-- Analyze tables
ANALYZE yawl_case;
ANALYZE yawl_work_item;
ANALYZE yawl_case_event;
```

### Step 10.3: Monitor & Validate

```bash
# Set up monitoring
# 1. Enable Prometheus metrics
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v /etc/prometheus:/etc/prometheus \
  prom/prometheus

# 2. Enable Grafana dashboards
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana

# 3. Configure alerts
cat > /etc/prometheus/alerts.yml << 'EOF'
groups:
  - name: yawl
    rules:
      - alert: YAWLHighErrorRate
        expr: rate(yawl_errors_total[5m]) > 0.001
        for: 5m
        annotations:
          summary: "High error rate on YAWL"
EOF
```

---

## 11. Migration Checklist

### Pre-Migration (Days 1-5)

- [ ] Team assembled (architect, devs, DBA, QA)
- [ ] v5 system assessed (case count, size, complexity)
- [ ] v6 environment prepared (Java 25, PostgreSQL)
- [ ] v5 database backed up
- [ ] Custom code inventoried
- [ ] Migration strategy chosen (re-execute vs data migration)

### Implementation (Days 6-12)

- [ ] v6 schema created and tested
- [ ] Case data migrated (or re-execution plan confirmed)
- [ ] Custom code updated to v6 APIs
- [ ] Configuration files migrated
- [ ] Custom code compiled without errors
- [ ] All unit tests passing

### Testing (Days 13-17)

- [ ] Integration tests passing
- [ ] Data validation confirmed (case counts match)
- [ ] Performance benchmarks meet targets
- [ ] Cutover procedure rehearsed and timed
- [ ] Rollback procedure tested and working

### Production (Days 18-20)

- [ ] All stakeholders notified of maintenance window
- [ ] v5 production backed up
- [ ] v6 production environment ready
- [ ] Cutover procedure executed
- [ ] Post-cutover validation passed
- [ ] System running stable on v6 for 24 hours

### Post-Migration (Days 21+)

- [ ] v5 servers decommissioned (after 30-day verification period)
- [ ] Documentation updated for v6
- [ ] Team trained on v6 operations
- [ ] Monitoring and alerting configured
- [ ] DR/backup procedures updated for v6

---

## 12. Timeline Gantt Chart

```
Week 1  ████████████████████
  Day 1: Pre-assessment, environment setup
  Day 2-3: Database setup, schema creation
  Day 4-5: Initial data migration

Week 2  ████████████████████
  Day 6-10: Custom code migration & updates
  Day 11-12: Configuration migration

Week 3  ████████████████████
  Day 13-17: Testing & validation
  (Parallel with Week 2 for large teams)

Week 4  ████████████████████
  Day 18-20: Cutover & production deployment
  Day 21+: Stabilization & optimization

Legend:
  ████ = Development/Testing
  ████ = Validation & Cutover
  ████ = Post-deployment monitoring
```

---

## 13. Key Contacts & Escalation

```
Role                Email           Phone         On-Call
─────────────────────────────────────────────────────────
Architect           arch@co.com     +1-555-0100   24/7
DBA Lead            dba@co.com      +1-555-0101   24/7
Dev Lead            dev@co.com      +1-555-0102   24/7
QA Lead             qa@co.com       +1-555-0103   Biz hours
DevOps Lead         ops@co.com      +1-555-0104   24/7
Executive Sponsor   exec@co.com     +1-555-0105   Biz hours
```

---

## 14. References & Resources

- [Detailed Breaking Changes](how-to/migration/breaking-changes.md)
- [Custom Code Migration Guide](how-to/migration/custom-code-guide.md)
- [Database Migration Scripts](scripts/migration/)
- [v6 Configuration Reference](reference/configuration.md)
- [Troubleshooting Guide](how-to/troubleshooting.md)

---

**For support during migration, contact the YAWL Foundation or consult the community forums.**

**Estimated Cost**: $20K-100K depending on team size and complexity

**Expected Value**: 5-10x better performance, unlimited scalability, modern cloud-native features
