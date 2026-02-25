# YAWL v6.0.0 Recovery Procedures - Complete Guide

## Overview

This document outlines the complete rollback and recovery system for YAWL v6.0.0. The system consists of:

1. **Documentation**: Comprehensive guides for deployment and recovery
2. **Automation**: Production-ready scripts for backup, rollback, and health checking
3. **Procedures**: Step-by-step instructions for all scenarios

**Key Metrics**:
- Recovery Time Objective (RTO): 5-10 minutes
- Recovery Point Objective (RPO): < 5 minutes
- Expected downtime: 10-15 minutes for standard rollback

---

## Directory Structure

```
yawl/
├── docs/
│   ├── ROLLBACK-PROCEDURES.md         # Complete rollback guide (14 KB)
│   ├── DEPLOYMENT-CHECKLIST.md        # Pre/during/post-deployment steps (13 KB)
│   ├── TROUBLESHOOTING-GUIDE.md       # Common issues and solutions (19 KB)
│   ├── QUICK-REFERENCE.md             # One-page cheat sheet (7 KB)
│   └── RECOVERY-PROCEDURES-README.md  # This file
│
└── scripts/
    ├── backup-before-deploy.sh        # Pre-deployment backup automation (20 KB)
    ├── rollback.sh                    # Automated rollback execution (25 KB)
    └── health-check.sh                # Comprehensive health validation (19 KB)
```

---

## When to Use Each Document

### DEPLOYMENT-CHECKLIST.md
**Use when**: Planning or executing a production deployment

**Contains**:
- Pre-deployment validation checklist (code, staging, infrastructure)
- Step-by-step deployment phases (5 phases × 30-45 min total)
- Post-deployment validation and monitoring
- Escalation procedures if deployment fails

**Key Sections**:
- Phase 1: Pre-Deployment (1-2 hours before)
- Phase 2: Backup and Shutdown
- Phase 3: Deploy New Version
- Phase 4: Validation
- Phase 5: Monitoring

### ROLLBACK-PROCEDURES.md
**Use when**: Need to revert to previous version (emergency or planned)

**Contains**:
- Quick Rollback (5 minutes) - fastest path
- Extended Rollback (10-15 minutes) - with data integrity checks
- 6 specific scenarios with root causes and solutions
- Post-rollback analysis procedures

**Key Sections**:
- Quick Rollback (§1-6)
- Extended Rollback (§1-6)
- Known Scenarios (Scenario 1-6)
- Monitoring during rollback
- Post-rollback analysis

### TROUBLESHOOTING-GUIDE.md
**Use when**: Application has issues and you need to debug

**Contains**:
- 10 common issues with diagnosis and resolution
- Log file locations and analysis commands
- Performance tuning checklist
- Emergency contact procedures

**Key Sections**:
- Issue 1: Tomcat Not Running
- Issue 2: WAR Not Deployed (404)
- Issue 3: Jakarta EE Incompatibility
- Issue 4: Database Connection Failures
- Issue 5: Data Corruption
- Issue 6: Security Vulnerabilities
- Issue 7: Slow Performance
- Issue 8: Memory Issues
- Issue 9: Container Startup Failed
- Issue 10: REST API Slow

### QUICK-REFERENCE.md
**Use when**: You need quick answers during incident

**Contains**:
- One-page summary of critical commands
- Common issues and fixes (table format)
- Health check status interpretation
- File location reference
- Database commands

**Best for**: Printing as desk reference or showing during standups

---

## When to Use Each Script

### backup-before-deploy.sh
**When to run**: Before every production deployment

**Usage**:
```bash
# Standard backup (database only)
sudo bash scripts/backup-before-deploy.sh

# Full backup with compression
sudo bash scripts/backup-before-deploy.sh --full --compress

# Custom retention
sudo bash scripts/backup-before-deploy.sh --retention-days 7
```

**Output**:
- Creates `/backup/yawl/yawl-YYYYMMDD-HHMMSS/` directory
- Contains: database dump, configurations, WAR files, manifest
- Typical size: 500MB - 2GB (depending on --full flag)

**Typical Duration**: 5-10 minutes

**Exit Codes**:
- 0: Success
- 1: Warning (backup created but some checks failed)
- 2: Critical failure (backup not created)

### rollback.sh
**When to run**: When deployment failed and needs reverting

**Usage**:
```bash
# Automatic rollback to most recent backup
sudo bash scripts/rollback.sh

# Rollback to specific version
sudo bash scripts/rollback.sh --version v5.1

# Use specific backup directory
sudo bash scripts/rollback.sh --backup-dir /backup/yawl/yawl-20260215-093022

# Skip database restore (config only)
sudo bash scripts/rollback.sh --no-restore-db

# Force faster shutdown
sudo bash scripts/rollback.sh --force-kill
```

**Phases**:
1. Diagnostics capture (< 1 min)
2. Service shutdown (2-3 min)
3. Configuration restore (1-2 min)
4. Database restore (3-5 min)
5. Service startup (2-3 min)
6. Validation (2-3 min)

**Total Duration**: 10-15 minutes

**Exit Codes**:
- 0: Successful rollback
- 1: Rollback completed with warnings
- 2: Rollback failed (requires manual intervention)

### health-check.sh
**When to run**: After deployment or when troubleshooting

**Usage**:
```bash
# Quick health check
bash scripts/health-check.sh

# Detailed output
bash scripts/health-check.sh --verbose

# Wait for Tomcat startup before checking
bash scripts/health-check.sh --wait-for-startup

# Custom startup timeout
bash scripts/health-check.sh --wait-for-startup --startup-timeout 180
```

**Checks Performed**:
1. Tomcat process running
2. Port 8080 accessible
3. JVM memory allocation
4. REST API health endpoint
5. Cases endpoint responsive
6. PostgreSQL database connectivity
7. Hibernate configuration
8. Logging configuration
9. Tomcat logs for errors
10. Application startup messages
11. CPU and memory usage
12. Disk space
13. Web application responsive
14. Stateless engine responsive

**Exit Codes**:
- 0: All checks passed (healthy)
- 1: Some checks failed (warnings)
- 2: Critical failures (unhealthy)

**Duration**: < 1 minute

---

## Quick Decision Tree

### "Application is down!"

1. **Check if Tomcat is running**
   ```bash
   pgrep -f catalina.startup.Bootstrap
   ```

2. **If NOT running**:
   ```bash
   systemctl start yawl-app
   sleep 30
   bash scripts/health-check.sh
   ```

3. **If running but unhealthy**:
   - Is it a recent deployment? → Execute rollback
   - Is it old version? → Check logs and troubleshoot
   ```bash
   tail -50 $CATALINA_HOME/logs/catalina.out
   bash scripts/health-check.sh --verbose
   ```

4. **If rollback needed**:
   ```bash
   sudo bash scripts/rollback.sh
   bash scripts/health-check.sh --wait-for-startup
   ```

### "Should I rollback?"

**YES, rollback immediately if**:
- API returning 500 errors (> 1% of requests)
- Database connection failing completely
- Application takes > 5 seconds to respond
- OutOfMemory or other JVM crash
- Data corruption or integrity issues
- Security vulnerability discovered

**NO, investigate first if**:
- Minor warnings in logs
- Occasional 404s (check deployed files)
- Performance degradation (< 2x baseline)
- Partial feature not working (may be config)

---

## Deployment Scenarios

### Standard Deployment (Weekly Feature Release)

```
Timeline: 45 minutes
1. Pre-deployment validation (30 min) - DEPLOYMENT-CHECKLIST.md
2. Create backup (5 min) - backup-before-deploy.sh
3. Deploy and verify (10 min) - deploy scripts + health-check.sh
4. Monitor (15 min active, 24 hours passive)

If issues: Execute rollback.sh
```

### Hotfix Deployment (Production Bug)

```
Timeline: 20 minutes
1. Quick validation (5 min) - Minimal testing
2. Backup and deploy (10 min) - backup-before-deploy.sh + deploy
3. Verify (5 min) - health-check.sh
4. Monitor closely (24 hours)

If issues: Execute rollback.sh immediately
```

### Emergency Rollback (Critical Issue)

```
Timeline: 10 minutes
1. Decision (1 min) - Verify severity
2. Execute rollback (5 min) - rollback.sh
3. Verify (2 min) - health-check.sh
4. Notify (2 min) - Slack, status page
5. Investigate (post-incident) - TROUBLESHOOTING-GUIDE.md
```

---

## Backup Strategy

### Automatic Backups

**When**: Before every production deployment
**Command**: `sudo bash scripts/backup-before-deploy.sh --full --compress`
**Retention**: 30 days (configurable with --retention-days)
**Location**: `/backup/yawl/yawl-YYYYMMDD-HHMMSS/`

### What's Backed Up

✓ PostgreSQL database (schema + data)
✓ Hibernate/database configuration
✓ Application properties
✓ WAR files (if deployed)
✓ Tomcat configuration (server.xml, etc.)
✓ Build artifacts (optional with --full)
✓ Source code snapshot (optional with --full)

### Backup Verification

Each backup includes `MANIFEST.txt` with:
- Timestamp
- File inventory
- Database version
- Application version
- Deployment status

### Restore from Backup

```bash
# List available backups
ls -d /backup/yawl/yawl-*

# View backup contents
ls /backup/yawl/yawl-20260215-093022/
cat /backup/yawl/yawl-20260215-093022/MANIFEST.txt

# Restore specific files
cp /backup/yawl/yawl-20260215-093022/database-full.sql /tmp/
psql yawl < /tmp/database-full.sql
```

---

## Monitoring and Alerting

### Real-time Monitoring

```bash
# Watch logs continuously
tail -f $CATALINA_HOME/logs/catalina.out

# Monitor resource usage
top -u tomcat

# Track database queries
psql yawl -c "SELECT * FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 5;"

# Watch active connections
watch -n 5 'psql yawl -c "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"'
```

### Automated Monitoring (Recommended)

- **Application**: New Relic, Datadog, or similar APM
- **Infrastructure**: CloudWatch, Prometheus, or similar
- **Alerting**: PagerDuty, OpsGenie, or similar
- **Logs**: ELK Stack, Splunk, or similar

### Key Metrics to Monitor

| Metric | Warning | Critical | Check Script |
|--------|---------|----------|--------------|
| Response Time p95 | > 2s | > 5s | health-check.sh |
| Error Rate | > 0.5% | > 1% | Check logs |
| CPU Usage | > 70% | > 85% | top, jstat |
| Memory Usage | > 80% | > 95% | jstat |
| DB Connections | > 35/40 | > 39/40 | psql pg_stat_activity |
| Disk Space | < 2GB | < 500MB | df -h |

---

## Troubleshooting Decision Tree

### API returns 404

```
├─ Check if WAR deployed: ls $CATALINA_HOME/webapps/yawl/
├─ If missing: cp build/lib/yawl.war $CATALINA_HOME/webapps/ && restart
├─ If present: Check logs for servlet mapping errors
└─ If still 404: Rebuild WAR and redeploy
```

### Database connection failing

```
├─ Check PostgreSQL: systemctl status postgresql
├─ If not running: systemctl start postgresql
├─ If running: psql -U postgres yawl -c "SELECT 1;"
├─ If fails: Check JDBC URL in hibernate.properties
└─ If all OK but app still fails: Check firewall, connection pool
```

### High CPU or Memory

```
├─ Check process: jstat -gc <pid>
├─ If no GC: likely infinite loop - restart
├─ If constant GC: likely memory leak - increase heap
├─ If periodic spikes: normal behavior - monitor
└─ If consistently high: Consider rollback or scaling
```

### Slow response times

```
├─ Check database: psql yawl -c "SELECT * FROM pg_stat_statements ..."
├─ If slow queries: Add indexes or optimize
├─ If normal queries: Check connection pool saturation
├─ If pool OK: Check network/disk I/O
└─ If still slow: Consider rollback or investigation
```

See **TROUBLESHOOTING-GUIDE.md** for detailed procedures.

---

## Common Commands Reference

### System Status

```bash
# Tomcat status
systemctl status yawl-app
pgrep -f catalina

# Database status
systemctl status postgresql
psql -U postgres -d yawl -c "SELECT 1;"

# Full system health
bash scripts/health-check.sh --verbose
```

### Logs and Diagnostics

```bash
# View logs
tail -f $CATALINA_HOME/logs/catalina.out
tail -50 $CATALINA_HOME/logs/catalina.out | grep -i error

# Search logs
grep "keyword" $CATALINA_HOME/logs/catalina.out
grep -i exception $CATALINA_HOME/logs/*.log
```

### Backup and Recovery

```bash
# Create backup
sudo bash scripts/backup-before-deploy.sh

# List backups
ls -d /backup/yawl/yawl-*

# Rollback
sudo bash scripts/rollback.sh

# Custom rollback
sudo bash scripts/rollback.sh --version v5.1
```

### Performance Analysis

```bash
# JVM metrics
jstat -gc -h10 <pid> 500

# Database query analysis
psql yawl << EOF
SELECT query, calls, mean_time FROM pg_stat_statements
ORDER BY mean_time DESC LIMIT 10;
EOF

# System resources
top -u tomcat
df -h /
```

---

## Deployment Checklist (30-second version)

- [ ] `ant unitTest` passes ✓
- [ ] `sudo bash scripts/backup-before-deploy.sh --full --compress` succeeds ✓
- [ ] `bash scripts/health-check.sh` shows all green ✓
- [ ] Copy WAR: `cp build/lib/*.war $CATALINA_HOME/webapps/` ✓
- [ ] Restart: `systemctl restart yawl-app` ✓
- [ ] Verify: `bash scripts/health-check.sh --wait-for-startup` ✓
- [ ] Monitor: `tail -f $CATALINA_HOME/logs/catalina.out` (5 min) ✓

---

## Support and Escalation

### Getting Help

1. **For immediate issues**: Check QUICK-REFERENCE.md
2. **For troubleshooting**: Check TROUBLESHOOTING-GUIDE.md
3. **For deployment**: Check DEPLOYMENT-CHECKLIST.md
4. **For recovery**: Check ROLLBACK-PROCEDURES.md

### Escalation Path

- **Level 1**: Check logs, health-check.sh
- **Level 2**: Execute rollback if recent deployment
- **Level 3**: Engage DBA if database issue
- **Level 4**: Engage Infrastructure if system issue
- **Level 5**: Page incident commander if multiple services affected

### Emergency Contacts

| Role | Status | Contact |
|------|--------|---------|
| On-Call Engineer | [Check PagerDuty] | [Phone] |
| DevOps Lead | [Check PagerDuty] | [Email] |
| Database Admin | [Check PagerDuty] | [Phone] |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-16 | Initial release with 4 documents and 3 scripts |

---

## Related Documentation

- **YAWL Architecture**: `/home/user/yawl/README.md`
- **Build Instructions**: `/home/user/yawl/build/README.md`
- **API Documentation**: `/home/user/yawl/docs/API-REFERENCE.md`
- **Database Schema**: `/home/user/yawl/schema/README.md`

---

## Document Maintenance

**Owner**: DevOps Team
**Last Updated**: 2026-02-16
**Review Frequency**: Quarterly
**Next Review**: 2026-05-16

**To Update**:
1. Edit appropriate document
2. Test procedures with actual deployment/rollback
3. Update version number and date
4. Notify team of changes
5. Update this README if structure changes

---

## Checklists for Print

### [PRINT THIS] Pre-Deployment Checklist

```
[ ] Code reviewed and approved
[ ] Tests passing (ant unitTest)
[ ] Build successful (ant buildAll)
[ ] Backup created (backup-before-deploy.sh)
[ ] Health check passing (health-check.sh)
[ ] Staging deployment tested
[ ] Stakeholders notified
[ ] Deployment window approved
[ ] All personnel online
[ ] Status page ready to update
[ ] Rollback plan reviewed
```

### [PRINT THIS] Deployment Phases

```
PHASE 1: Pre-Deployment (1-2 hours before)
[ ] Code review & tests
[ ] Staging validation
[ ] Infrastructure checks
[ ] Team readiness

PHASE 2: Backup (5 minutes)
[ ] Create backup
[ ] Verify backup exists
[ ] Team confirms readiness

PHASE 3: Deploy (10-15 minutes)
[ ] Stop services
[ ] Copy WAR files
[ ] Update configuration
[ ] Start services
[ ] Verify health checks

PHASE 4: Validate (10 minutes)
[ ] Test REST API
[ ] Test database
[ ] Test web UI
[ ] Check logs
[ ] Verify performance

PHASE 5: Monitor (20+ minutes)
[ ] Real-time log monitoring
[ ] Metric baselines established
[ ] No critical errors detected
[ ] Customer issues checked
```

### [PRINT THIS] Rollback Decision

```
DO NOT ROLLBACK if:
- Warnings only (no errors)
- Performance normal (< 2x baseline)
- Error rate < 1%
- Partial feature issue (may be config)

ROLLBACK IMMEDIATELY if:
- API returning 500s
- Database connection failed
- OutOfMemory or crash
- Data corruption
- Security vulnerability
- > 5 second response times
- > 5% error rate
```

---

**End of Document**

For questions or updates, contact: devops@yawl-team.org

