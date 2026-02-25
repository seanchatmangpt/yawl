# YAWL v6.0.0 Quick Reference Card

## Emergency Commands (Production Down)

```bash
# 1. Check if Tomcat is running
pgrep -f catalina.startup.Bootstrap

# 2. See real-time logs
tail -f $CATALINA_HOME/logs/catalina.out

# 3. Start Tomcat
systemctl start yawl-app

# 4. Verify application health
bash scripts/health-check.sh

# 5. Rollback if deployment caused failure
sudo bash scripts/rollback.sh
```

---

## Pre-Deployment (Run in Order)

```bash
# 1. Build and test (must pass)
ant clean compile && ant unitTest
# Expected: BUILD SUCCESSFUL, all tests pass

# 2. Create backup (required)
sudo bash scripts/backup-before-deploy.sh --full --compress
# Expected: Backup directory created with database.sql

# 3. Deploy to staging
cp build/lib/yawl.war /staging/tomcat/webapps/

# 4. Run health checks
bash scripts/health-check.sh --wait-for-startup
# Expected: All checks passing

# 5. Smoke tests
java -cp build/lib/*:. org.yawlfoundation.yawl.test.SmokeTest
```

---

## Production Deployment (30-45 minutes)

```bash
# PHASE 1: Backup & Stop (5 min)
sudo bash scripts/backup-before-deploy.sh --verify-only
systemctl stop yawl-app

# PHASE 2: Deploy (5 min)
cp build/lib/yawl*.war $CATALINA_HOME/webapps/
rm -rf $CATALINA_HOME/webapps/yawl $CATALINA_HOME/webapps/yawl-web

# PHASE 3: Start & Verify (10 min)
systemctl start yawl-app
bash scripts/health-check.sh --wait-for-startup
curl http://localhost:8080/yawl/api/cases

# PHASE 4: Monitor (10 min)
tail -f $CATALINA_HOME/logs/catalina.out
# Watch for errors - stop if critical issues

# PHASE 5: Announce & Handoff
# Mark deployment complete
# Notify stakeholders
```

---

## Rollback (5-10 minutes)

```bash
# Automatic rollback
sudo bash scripts/rollback.sh

# Verify success
bash scripts/health-check.sh

# If automatic fails, manual:
# 1. Stop services: systemctl stop yawl-app
# 2. Restore WAR: cp /backup/yawl-[DATE]/*.war $CATALINA_HOME/webapps/
# 3. Restore config: cp /backup/yawl-[DATE]/properties/* build/properties/
# 4. Restore DB: psql yawl < /backup/yawl-[DATE]/database.sql
# 5. Start: systemctl start yawl-app
# 6. Verify: bash scripts/health-check.sh
```

---

## Common Issues & Quick Fixes

| Issue | Command | Expected Result |
|-------|---------|-----------------|
| **Tomcat not starting** | `systemctl restart yawl-app && sleep 30 && bash scripts/health-check.sh` | Checks pass |
| **404 Not Found** | `cp build/lib/yawl.war $CATALINA_HOME/webapps/ && systemctl restart yawl-app` | API responds |
| **Database connection fail** | `systemctl start postgresql && sleep 5 && systemctl restart yawl-app` | DB accessible |
| **OutOfMemory** | Edit `$CATALINA_HOME/bin/setenv.sh`: `-Xmx2048m` | App runs without OOM |
| **Slow response** | `psql yawl -c "CREATE INDEX ...;"` + restart | < 2s response |
| **High CPU** | `jstat -gc <pid> 1000` | Monitor GC patterns |

---

## Health Check Status

### Green (All OK)
```bash
bash scripts/health-check.sh
# Output: Status: HEALTHY (100% passed)
# Action: Safe to use
```

### Yellow (Warnings)
```bash
# Output: Status: DEGRADED (minor issues)
# Action: Monitor for 15 minutes, rollback if worsening
```

### Red (Critical)
```bash
# Output: Status: UNHEALTHY
# Action: Execute rollback immediately
sudo bash scripts/rollback.sh
```

---

## File Locations Quick Reference

```bash
# Tomcat
$CATALINA_HOME              /opt/apache-tomcat-10.1.13
$CATALINA_HOME/webapps      Deployed applications (WAR files)
$CATALINA_HOME/conf         Configuration (server.xml, web.xml)
$CATALINA_HOME/logs         Log files (catalina.out)

# YAWL
~/yawl/build/lib/yawl.war   Compiled application
~/yawl/build/properties     Configuration files (hibernate.properties)
~/yawl/scripts              Deployment/rollback scripts

# Database
/var/lib/postgresql         PostgreSQL data files
/backup/yawl                Backup directory (automatic)

# Logs
$CATALINA_HOME/logs/catalina.out    Main application log
/var/log/postgresql                 Database logs
/var/log/syslog                      System events
```

---

## Database Commands

```bash
# Connect to YAWL database
psql -U postgres yawl

# List workflows
SELECT COUNT(*) FROM workflow_case;

# Find running cases
SELECT * FROM workflow_case WHERE status = 'Active';

# Check database size
SELECT pg_size_pretty(pg_database_size('yawl'));

# Backup database
pg_dump -U postgres yawl > backup.sql

# Restore database
psql -U postgres yawl < backup.sql

# Check PostgreSQL status
systemctl status postgresql
```

---

## Monitoring Commands

```bash
# Real-time log monitoring
tail -f $CATALINA_HOME/logs/catalina.out

# Search for errors
grep -i error $CATALINA_HOME/logs/catalina.out | tail -20

# Java process info
ps aux | grep catalina

# JVM memory stats
jstat -gc -h10 <pid> 500

# Network connections
netstat -tlnp | grep 8080

# Disk usage
df -h /

# CPU and memory
top -u tomcat

# Database connections
psql yawl -c "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"
```

---

## Deployment Readiness Checklist (5 min)

- [ ] Build passes: `ant unitTest` ✓
- [ ] Backup exists: `ls /backup/yawl/yawl-*/ | head -1` ✓
- [ ] Current state OK: `bash scripts/health-check.sh` ✓
- [ ] Change approved: Link to approval ticket ✓
- [ ] Team ready: All personnel online ✓
- [ ] Status page prepared: Can update in 30 seconds ✓

---

## Critical Phone Numbers & Escalation

| Role | On-Call | Status |
|------|---------|--------|
| DevOps Lead | See PagerDuty | [Status] |
| Database Admin | See PagerDuty | [Status] |
| Incident Commander | See PagerDuty | [Status] |

---

## Important Links

- **Rollback Procedures**: `/home/user/yawl/docs/ROLLBACK-PROCEDURES.md`
- **Deployment Checklist**: `/home/user/yawl/docs/DEPLOYMENT-CHECKLIST.md`
- **Troubleshooting Guide**: `/home/user/yawl/docs/TROUBLESHOOTING-GUIDE.md`
- **GitHub Releases**: https://github.com/yawl/yawl/releases
- **JIRA Board**: [Your JIRA URL]
- **Status Page**: [Your Status Page URL]

---

## Post-Deployment Tasks

1. **Monitor for 20 minutes**
   - Watch: `tail -f $CATALINA_HOME/logs/catalina.out`
   - Check: No ERROR messages appearing

2. **Alert if issues**
   - Immediate: Slack #deployments
   - Escalate: Page on-call engineer if critical

3. **Document deployment**
   - Note: Deployment time, any issues, resolution
   - File: Incident ticket if rollback occurred

4. **Update status page**
   - Mark: Deployment complete
   - Message: "v5.2 deployed, stable"

5. **Team debrief (24 hours)**
   - Review: What went well, what could improve
   - Document: Lessons learned
   - Update: Procedures if needed

---

## Environment Variables (if using custom setup)

```bash
export CATALINA_HOME=/opt/apache-tomcat-10.1.13
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$CATALINA_HOME/bin:$PATH

# Verify
echo $CATALINA_HOME
java -version
```

---

## Version Information

| Component | Version | Status |
|-----------|---------|--------|
| YAWL | 5.2 | Current |
| Tomcat | 10.1.13 | Required |
| Java | 11+ | Required |
| PostgreSQL | 12+ | Recommended |

---

**Last Updated**: 2026-02-16
**Document Owner**: DevOps Team
**Questions?** Contact: devops@yawl-team.org

