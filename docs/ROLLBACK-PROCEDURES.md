# Rollback Procedures for YAWL v5.2

## Executive Summary

This document outlines critical procedures for rolling back a YAWL v5.2 deployment. All procedures prioritize minimal downtime and data integrity. Target recovery time objective (RTO) is 5-10 minutes for standard rollbacks.

## Pre-Deployment Checklist

Before deploying any version to production, execute:

```bash
scripts/backup-before-deploy.sh
```

This creates a timestamped backup containing:
- Previous WAR deployments
- Hibernate/database configuration
- Application properties
- Database schema and data snapshot

## Quick Rollback (5 minutes)

Use this procedure for immediate rollback when deployment causes critical failures.

### 1. Stop Application Server

**Tomcat 10.1+** (Jakarta EE)
```bash
$CATALINA_HOME/bin/shutdown.sh -force
# Wait for graceful shutdown (up to 30 seconds)
sleep 5
pkill -9 java  # Force kill if needed
```

**Jetty**
```bash
systemctl stop jetty
# Verify
systemctl status jetty
```

**Docker Container**
```bash
docker stop yawl-container
docker rm yawl-container
```

### 2. Restore Previous WAR Files

```bash
cd $CATALINA_HOME/webapps

# Remove current deployment
rm -rf yawl yawl.war
rm -rf yawl-web yawl-web.war
rm -rf yawl-stateless yawl-stateless.war

# Restore previous version
BACKUP_DATE=$(date -d '1 day ago' +%Y%m%d)
cp /backup/yawl-${BACKUP_DATE}-*/yawl*.war .

# Verify restoration
ls -lah yawl*.war
```

### 3. Restore Configuration Files

```bash
# Restore Hibernate configuration
cp /backup/yawl-${BACKUP_DATE}-*/properties/hibernate.properties build/properties/

# Restore database configuration
cp /backup/yawl-${BACKUP_DATE}-*/properties/database.properties build/properties/

# Restore application properties
cp /backup/yawl-${BACKUP_DATE}-*/properties/application.properties build/properties/

# Verify critical properties exist
grep -E "hibernate.dialect|jdbc.url|jdbc.user" build/properties/hibernate.properties
```

### 4. Database Rollback (If Schema Changed)

**PostgreSQL Rollback**
```bash
# Stop application to prevent connections
systemctl stop yawl-app

# List available backups
ls -lah /backup/yawl-*/database.sql

# Restore from backup (choose appropriate date)
BACKUP_DATE="20260215-093022"
psql yawl < /backup/yawl-${BACKUP_DATE}/database.sql

# Verify restoration
psql yawl -c "SELECT COUNT(*) FROM workflow_case;"

# Restart application
systemctl start yawl-app
```

**MySQL Rollback**
```bash
mysql -u yawl -p yawl < /backup/yawl-${BACKUP_DATE}/database.sql
```

**H2 Database Rollback**
```bash
# Stop application first
systemctl stop yawl-app

# Restore H2 database files
rm -rf data/yawl.db* data/yawl.h2.db
cp /backup/yawl-${BACKUP_DATE}/yawl.h2.db data/

# Restart application
systemctl start yawl-app
```

### 5. Restart Application Server

**Tomcat**
```bash
$CATALINA_HOME/bin/startup.sh

# Monitor startup (should be ready in 30-60 seconds)
tail -f $CATALINA_HOME/logs/catalina.out
```

**Jetty**
```bash
systemctl start jetty
systemctl status jetty
```

### 6. Verify Rollback Success

```bash
# Check logs for errors
tail -50 $CATALINA_HOME/logs/catalina.out | grep -i error

# Test REST API endpoint
curl -s http://localhost:8080/yawl/ib | head -20

# Test stateless engine
curl -s http://localhost:8080/yawl-stateless/health

# Check database connectivity
curl -s http://localhost:8080/yawl/api/cases | jq '.cases | length'

# Monitor memory usage
jps -l | grep Catalina
```

## Extended Rollback Procedure (10-15 minutes)

Use for complex deployments or when database schema migration is involved.

### 1. Pre-Rollback Diagnostics

```bash
# Capture current state for investigation
DIAG_DIR="/var/log/yawl-rollback-diagnostics-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DIAG_DIR"

# Capture running processes
ps aux | grep java > "$DIAG_DIR/processes.log"

# Capture error logs
cp $CATALINA_HOME/logs/*.log "$DIAG_DIR/"

# Capture database state
pg_dump yawl > "$DIAG_DIR/failed-database.sql"
```

### 2. Initiate Graceful Shutdown

```bash
# Send SIGTERM to allow transaction completion
kill -15 $(pgrep -f "org.apache.catalina.startup.Bootstrap")

# Monitor shutdown (up to 60 seconds)
for i in {1..12}; do
    if ! pgrep -f "org.apache.catalina.startup.Bootstrap" > /dev/null; then
        echo "Shutdown complete"
        break
    fi
    echo "Waiting for graceful shutdown... ($i/12)"
    sleep 5
done

# Force kill if still running
if pgrep -f "org.apache.catalina.startup.Bootstrap" > /dev/null; then
    echo "Forcing shutdown..."
    pkill -9 java
fi
```

### 3. Transaction Log Rollback (if applicable)

```bash
# For databases with WAL (Write-Ahead Logs)
# PostgreSQL
pg_controldata /var/lib/postgresql/data | grep -i "Redo"

# Restore from transaction-safe checkpoint
pg_restore --data-only /backup/yawl-latest/database.dump | psql yawl
```

### 4. Environment Variable Verification

```bash
# Verify JVM parameters match previous version
env | grep -E "JAVA_HOME|CATALINA_|DB_" > "$DIAG_DIR/env-vars.log"

# Restore previous JVM configuration
cp /backup/yawl-${BACKUP_DATE}/setenv.sh $CATALINA_HOME/bin/
source $CATALINA_HOME/bin/setenv.sh
```

### 5. Verify Data Integrity

```bash
# Check for orphaned records
psql yawl << EOF
SELECT COUNT(*) as orphaned_workitems
FROM work_item wi
WHERE NOT EXISTS (SELECT 1 FROM workflow_case wc WHERE wc.case_id = wi.case_id);

SELECT COUNT(*) as incomplete_tasks
FROM work_item
WHERE status = 'ERROR' OR status = 'FAILED';
EOF

# Log results for post-rollback analysis
```

### 6. Full Restart and Validation

```bash
# Start all services
systemctl start yawl-app
systemctl start yawl-web
systemctl start yawl-stateless

# Comprehensive health checks
bash scripts/health-check.sh

# Smoke test critical workflows
java -cp build/lib/*:. org.yawlfoundation.yawl.test.SmokeTest
```

## Known Rollback Scenarios

### Scenario 1: Jakarta EE Container Incompatibility

**Symptom**
```
ClassNotFoundException: jakarta.servlet.http.HttpServlet
java.lang.NoClassDefFoundError: jakarta/servlet/http/HttpServlet
```

**Root Cause**
- Deployment to Tomcat 9.x (requires javax.servlet)
- v5.2 requires Tomcat 10.1+ (jakarta.servlet)

**Resolution**
```bash
# Verify container version
$CATALINA_HOME/bin/version.sh | grep "Tomcat"

# If version < 10.0, upgrade container
wget https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.13/bin/apache-tomcat-10.1.13.tar.gz
tar -xzf apache-tomcat-10.1.13.tar.gz -C /opt/

# Reconfigure environment
export CATALINA_HOME=/opt/apache-tomcat-10.1.13
$CATALINA_HOME/bin/startup.sh

# Rollback to previous version if incompatible
bash scripts/rollback.sh --date 20260215
```

### Scenario 2: Database Connection Failures

**Symptom**
```
HikariPool - Failed to validate connection: Connection timed out
SQLException: No database connection available
```

**Root Cause**
- Incorrect JDBC URL in hibernate.properties
- Database server not running
- Network/firewall blocking connections

**Resolution**
```bash
# Verify database properties
grep "hibernate.connection" build/properties/hibernate.properties

# Check database server status
systemctl status postgresql
psql -h localhost -U yawl -d yawl -c "SELECT 1;"

# Restore previous hibernate.properties
cp /backup/yawl-${BACKUP_DATE}/properties/hibernate.properties build/properties/

# Test connection
ant test-db-connection

# Restart application
systemctl restart yawl-app
```

### Scenario 3: REST API Returns 404

**Symptom**
```
curl http://localhost:8080/yawl/api/cases
404 Not Found
```

**Root Cause**
- Jersey servlet not configured in web.xml
- Wrong servlet-mapping path
- WAR file not properly deployed

**Resolution**
```bash
# Check web.xml configuration
grep -A 5 "jersey.config.server.provider" build/web/WEB-INF/web.xml

# Verify WAR deployment
ls -lah $CATALINA_HOME/webapps/yawl/WEB-INF/web.xml

# Restore web.xml from backup
cp /backup/yawl-${BACKUP_DATE}/.../web.xml build/web/WEB-INF/

# Recompile and redeploy
ant clean compile buildWebApps
cp build/lib/yawl.war $CATALINA_HOME/webapps/

# Restart Tomcat
$CATALINA_HOME/bin/shutdown.sh
sleep 5
$CATALINA_HOME/bin/startup.sh
```

### Scenario 4: Performance Degradation After Deployment

**Symptom**
- API response times > 5 seconds
- High CPU/memory usage
- Database connection pool exhaustion

**Root Cause**
- Memory leak in new version
- Inefficient query in ORM mapping
- Connection pool misconfiguration

**Resolution**
```bash
# Monitor JVM metrics
jstat -gc -h10 <pid> 1000

# Check database connection pool
curl http://localhost:8080/yawl/admin/metrics | jq '.database.connections'

# Revert to previous version with known performance baseline
bash scripts/rollback.sh --version v5.1

# After rollback, investigate new version in staging
# Identify performance regression
# File bug report with profiling data
```

### Scenario 5: Data Corruption During Migration

**Symptom**
```
DataIntegrityViolationException: Foreign key constraint violated
SELECT * returns NULL values in critical fields
```

**Root Cause**
- Migration script logic error
- Incomplete schema transformation
- Concurrent write during migration

**Resolution**
```bash
# CRITICAL: Do NOT restart application (prevents further corruption)
systemctl stop yawl-app

# Restore from backup before migration started
BACKUP_BEFORE_MIGRATION="/backup/yawl-20260214-080000"  # Before v5.2 deploy
pg_restore --data-only "$BACKUP_BEFORE_MIGRATION/database.dump" | psql yawl

# Verify data integrity
bash scripts/validate-database.sh

# Restart with previous version
bash scripts/rollback.sh --version v5.1

# Post-mortems
# 1. Review migration script for logic errors
# 2. Test migration in staging with production data copy
# 3. Add data validation hooks to migration
# 4. Implement backup-validate-restore pattern for future migrations
```

### Scenario 6: Security Vulnerability Discovered

**Symptom**
- CVE published affecting YAWL v5.2 dependencies
- Unauthorized API access due to authentication bypass

**Resolution - EMERGENCY ROLLBACK**
```bash
# Immediate action: Take production offline
systemctl stop yawl-app yawl-web

# Restore known-secure version
bash scripts/rollback.sh --version v5.1 --skip-validation

# Re-enable service
systemctl start yawl-app yawl-web

# Parallel: Build patched v5.2 version
# 1. Update vulnerable dependency
# 2. Run full test suite
# 3. Re-deploy with monitoring

# Notify stakeholders
# Incident: Rolled back to v5.1 due to CVE-2026-XXXXX
# ETA for patched v5.2: [time]
```

## Monitoring During Rollback

### Critical Metrics to Track

```bash
# Create monitoring dashboard
watch -n 1 'echo "=== CPU ==="; top -bn1 | head -5; echo "=== Memory ==="; free -h | head -3; echo "=== DB Connections ==="; curl -s http://localhost:8080/yawl/admin/metrics | jq ".database.connections"'
```

### Health Check Endpoint

```bash
#!/bin/bash
# Health check during rollback recovery

ENDPOINTS=(
    "http://localhost:8080/yawl/ib"
    "http://localhost:8080/yawl/api/cases"
    "http://localhost:8080/yawl-stateless/health"
    "http://localhost:8080/yawl-web/"
)

for endpoint in "${ENDPOINTS[@]}"; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$endpoint")
    echo "[$HTTP_CODE] $endpoint"
done
```

## Post-Rollback Analysis

### Mandatory Steps After Rollback

1. **Capture Diagnostics** (within 1 hour)
   ```bash
   tar czf /var/log/yawl-rollback-$(date +%Y%m%d-%H%M%S).tar.gz \
       $CATALINA_HOME/logs/ \
       /backup/yawl-*/database.sql \
       "$DIAG_DIR"
   ```

2. **Root Cause Analysis**
   - Review application logs for stack traces
   - Check database query logs
   - Analyze deployment changes

3. **Stakeholder Communication**
   - Time of incident
   - Duration of rollback
   - Data loss assessment (should be zero)
   - Root cause (preliminary)
   - Next steps

4. **Post-Rollback Testing**
   - Re-run all test suites
   - Load testing on rolled-back version
   - Data consistency verification

### Example RCA Template

```
INCIDENT REPORT: YAWL v5.2 Deployment Rollback
================================================

Incident: [Title]
Date/Time: 2026-02-16 14:30 UTC
Duration: 8 minutes (14:30-14:38)
Impact: Production unavailable

TIMELINE:
- 14:20: Deployment initiated
- 14:25: Application started, API returning 500 errors
- 14:28: Decision to rollback made
- 14:30: Rollback procedure started
- 14:38: v5.1 fully operational

ROOT CAUSE:
[Technical analysis]

CONTRIBUTING FACTORS:
- Missing pre-deployment smoke test
- Insufficient staging environment coverage

REMEDIATION:
- Enhanced test coverage for [feature]
- Added pre-deployment validation for [scenario]

TIMELINE FOR PERMANENT FIX:
- [Date]: Root cause fix deployed to staging
- [Date]: Comprehensive testing completed
- [Date]: Re-deployment to production

STAKEHOLDERS NOTIFIED:
- Engineering team: 14:31 UTC
- Management: 14:35 UTC
- Customers (status page): 14:40 UTC
```

## Quick Reference

| Scenario | RTO | RPO | Procedure |
|----------|-----|-----|-----------|
| API 404 error | 3 min | 0 | Quick Rollback §2 |
| Database connection fail | 5 min | 0 | Quick Rollback §4 |
| Data corruption | 10 min | varies | Extended §3-5 |
| Security vulnerability | 2 min | 0 | Scenario 6 |
| Performance degradation | 5 min | 0 | Scenario 4 |
| Schema mismatch | 8 min | 0 | Scenario 3 |

---

**Document Version**: v1.0
**Last Updated**: 2026-02-16
**Owner**: Infrastructure Team
**Contact**: devops@yawl-team.org
