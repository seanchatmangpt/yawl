# YAWL v6.0.0 Deployment Checklist

## Pre-Deployment Validation (1-2 hours before deployment)

### Code Readiness
- [ ] **Code Review**: All pull requests reviewed and approved
  - Command: Check GitHub PRs
  - Expected: All PRs have ≥1 approval

- [ ] **Unit Tests Pass**: Run comprehensive test suite
  - Command: `ant unitTest`
  - Expected: All tests pass (0 failures)
  - Time: ~5-10 minutes

- [ ] **Build Success**: Full build compiles without errors
  - Command: `ant clean && ant compile && ant buildAll`
  - Expected: BUILD SUCCESSFUL
  - Time: ~30-45 seconds

- [ ] **Static Analysis**: Code quality checks
  - Command: `ant analyze` (if configured)
  - Expected: No critical issues

- [ ] **Security Check**: Dependency vulnerabilities
  - Command: `mvn dependency-check:check` (if configured)
  - Expected: No critical CVEs

### Staging Environment Validation

- [ ] **Deploy to Staging**
  - Command: Deploy build/lib/*.war to staging Tomcat
  - Command: `bash scripts/health-check.sh --verbose --wait-for-startup`

- [ ] **Smoke Tests Pass**: Critical workflow tests
  - Command: `java -cp build/lib/*:. org.yawlfoundation.yawl.test.SmokeTest`
  - Expected: All workflows complete successfully

- [ ] **Load Test** (optional): Verify performance
  - Command: `bash scripts/load-test.sh --duration 5m --threads 10`
  - Expected: Response times < 2 seconds, error rate < 1%

- [ ] **Regression Tests**: Previous version functionality
  - Command: Manual test of critical features
  - Time: ~15-20 minutes

### Infrastructure Preparation

- [ ] **Backup Created**: Pre-deployment backup
  - Command: `sudo bash scripts/backup-before-deploy.sh --full --compress`
  - Expected: Backup directory created, size > 500MB
  - Verify: `ls -lah /backup/yawl/yawl-*/`

- [ ] **Backup Validated**: Backup integrity verified
  - Command: `tar -tzf /backup/yawl/yawl-*.tar.gz | head -10`
  - Expected: Contains database and configuration files

- [ ] **Monitoring Enabled**: Production metrics baseline
  - Check: New Relic, Datadog, or similar
  - Baseline: CPU < 20%, Memory < 40%, Response time < 1s

- [ ] **Log Aggregation Ready**: ELK, Splunk, or similar
  - Check: Logs flowing to central location
  - Verify: Can search application logs

- [ ] **Alerting Configured**: Critical incident alerts
  - Verify: On-call engineer has alerts enabled
  - Test: Send test alert to verify notification

- [ ] **Incident Channel Active**: Slack, PagerDuty, etc.
  - Action: Post message to #deployments
  - Message: "v5.2 deployment starting at HH:MM UTC"

### Change Management

- [ ] **Approval Obtained**: Deployment authorization
  - Required from: Release Manager or Engineering Lead
  - Document: Link to approval ticket

- [ ] **Change Ticket Created**: Track deployment changes
  - System: Jira, ServiceNow, etc.
  - Details: Version, deployment window, rollback plan

- [ ] **Stakeholders Notified**: Engineering, support, management
  - Notify: Slack #announcements, status page
  - Message: Deployment window, expected impact

- [ ] **Rollback Plan Reviewed**: Team understands rollback
  - Document: Link to ROLLBACK-PROCEDURES.md
  - Verify: At least 1 person can execute rollback

---

## Deployment Execution (30-45 minutes)

### Phase 1: Pre-Deployment (5 minutes)

- [ ] **Final Health Check**: Current production state
  - Command: `bash scripts/health-check.sh`
  - Expected: All checks passing
  - Stop deployment if any critical failures

- [ ] **Team Ready**: All required personnel online
  - Verify: On-call engineer, ops team, team lead
  - Communication: Confirm in deployment channel

- [ ] **Maintenance Window Announced**: Notify users
  - Duration: 15 minutes expected downtime
  - Command: Post status page update
  - Expected: "Maintenance in progress"

### Phase 2: Backup and Shutdown (10 minutes)

- [ ] **Backup Verification**: Confirm backup exists
  - Command: `ls -lah /backup/yawl/yawl-${TIMESTAMP}/`
  - Expected: Size > 100MB, contains database.sql

- [ ] **Services Shutdown**: Graceful shutdown
  - Command: `systemctl stop yawl-app yawl-web`
  - Monitor: `tail -f $CATALINA_HOME/logs/catalina.out`
  - Expected: "Shutdown complete" in logs

- [ ] **Verify Services Stopped**: No lingering processes
  - Command: `pgrep -f "catalina.startup.Bootstrap"`
  - Expected: No output (no processes running)

- [ ] **Backup Verification**: Final backup check
  - Command: `du -sh /backup/yawl/yawl-${TIMESTAMP}/ && find /backup/yawl/yawl-${TIMESTAMP}/ -type f | head -5`
  - Expected: Backup present and valid

### Phase 3: Deploy New Version (15 minutes)

- [ ] **Replace WAR Files**: Deploy new application
  - Command: `cp build/lib/yawl*.war $CATALINA_HOME/webapps/`
  - Verify: `ls -la $CATALINA_HOME/webapps/yawl*.war`

- [ ] **Remove Old Deployments**: Clean up expanded apps
  - Command: `rm -rf $CATALINA_HOME/webapps/yawl $CATALINA_HOME/webapps/yawl-web`
  - Purpose: Force Tomcat to expand fresh WAR files

- [ ] **Update Configuration** (if needed): Deploy config changes
  - Command: `cp build/properties/*.properties $CATALINA_HOME/conf/`
  - Verify: `grep "version\|app.name" $CATALINA_HOME/conf/application.properties`

- [ ] **Start Services**: Restart application
  - Command: `systemctl start yawl-app yawl-web`
  - Monitor: `tail -f $CATALINA_HOME/logs/catalina.out`
  - Expected: "Server startup complete" message

- [ ] **Startup Complete**: Services fully initialized
  - Wait: 30-60 seconds for full startup
  - Monitor: Check logs for errors
  - Expected: No ERROR or EXCEPTION messages

- [ ] **Health Check**: Verify new version is healthy
  - Command: `bash scripts/health-check.sh --verbose`
  - Expected: All checks passing
  - If fails: Execute rollback immediately (go to Phase 4)

### Phase 4: Validation (10 minutes)

- [ ] **REST API Responsive**: Basic connectivity
  - Command: `curl http://localhost:8080/yawl/ib`
  - Expected: HTTP 200 or valid HTML response

- [ ] **Database Accessible**: Data layer functional
  - Command: `curl http://localhost:8080/yawl/api/cases`
  - Expected: JSON response with cases list (may be empty)

- [ ] **Web Application Accessible**: UI functional
  - Command: `curl http://localhost:8080/yawl-web/`
  - Expected: HTML content returned

- [ ] **Critical Workflow Test**: E2E functionality
  - Action: Manually create/execute test workflow
  - Expected: Workflow completes without errors
  - Time: ~5 minutes

- [ ] **Log Monitoring**: No errors in logs
  - Command: `tail -100 $CATALINA_HOME/logs/catalina.out | grep -i "error\|exception"`
  - Expected: No output or only expected warnings

- [ ] **Performance Baseline**: Metrics within acceptable range
  - Verify: CPU < 30%, Memory < 50%, Response time < 2s
  - Monitor: New Relic, Datadog, or similar

---

## Post-Deployment (30 minutes)

### Phase 1: Announce Success (5 minutes)

- [ ] **Status Page Update**: Mark deployment complete
  - Status: "Operational"
  - Message: "Deployment of YAWL v6.0.0 complete"

- [ ] **Notify Stakeholders**: Engineering and management
  - Channel: Slack #announcements
  - Message: Version deployed, no incidents, monitoring active

- [ ] **Update Release Notes**: Document deployment
  - Include: Timestamp, deployed version, duration
  - Link: https://github.com/yawl/yawl/releases/tag/v5.2

### Phase 2: Extended Monitoring (20 minutes)

- [ ] **Active Monitoring**: Watch metrics continuously
  - Duration: 20 minutes post-deployment
  - Metrics: CPU, memory, response times, error rates
  - Alert threshold: Any spike > 2x baseline

- [ ] **Log Analysis**: Check for warnings or unusual activity
  - Command: `tail -f $CATALINA_HOME/logs/catalina.out`
  - Look for: OutOfMemoryError, connection pool exhaustion, deadlocks

- [ ] **Customer Reports**: Monitor support channels
  - Check: Slack #support, email
  - Action: Respond immediately to issues

- [ ] **Database Health**: Verify data integrity
  - Command: `psql yawl -c "SELECT COUNT(*) FROM workflow_case;"`
  - Compare: Should match pre-deployment count

- [ ] **API Performance**: Monitor response times
  - Tool: curl from multiple endpoints
  - Expected: Response times < 1 second

### Phase 3: Documentation and Handoff (5 minutes)

- [ ] **Deployment Log Created**: Document deployment details
  - File: `/var/log/yawl-deployment-${TIMESTAMP}.log`
  - Include: Start time, end time, any issues, resolution

- [ ] **Handoff to Night Team**: If applicable
  - Brief: Summarize deployment, no known issues
  - Provide: Contact info for incident escalation

- [ ] **Team Retrospective**: Schedule follow-up
  - Plan: Debrief within 24 hours
  - Document: Lessons learned, process improvements

---

## If Deployment Fails - Rollback Immediately

### Automatic Rollback (< 10 minutes)

1. **Acknowledge Failure**
   - Assess severity: Is production down?
   - Decision: Rollback vs. investigation

2. **Execute Rollback**
   - Command: `sudo bash scripts/rollback.sh`
   - Monitor: `tail -f $CATALINA_HOME/logs/catalina.out`
   - Timeout: Should complete within 5-10 minutes

3. **Verify Rollback**
   - Command: `bash scripts/health-check.sh`
   - Expected: All checks passing

4. **Notify Stakeholders**
   - Action: Post to #deployments channel
   - Message: "Rollback completed, v5.1 active, investigating issue"

5. **Root Cause Analysis**
   - Collect diagnostics from `/var/log/yawl-rollback-diagnostics-*/`
   - File incident report with error details
   - Schedule engineering review

### Manual Rollback (if automatic fails)

Refer to `/home/user/yawl/docs/ROLLBACK-PROCEDURES.md` sections:
- Quick Rollback (§3)
- Extended Rollback (§4)

---

## Post-Rollback Actions

- [ ] **Incident Report**: Document what went wrong
  - File: Jira ticket with detailed RCA
  - Include: Timeline, root cause, preventive measures

- [ ] **Improve Testing**: Enhance test coverage
  - Add: Tests that would have caught the issue
  - Review: Test coverage metrics

- [ ] **Process Improvement**: Update deployment procedures
  - Document: What went wrong and how to prevent
  - Share: With team in retrospective

- [ ] **Re-deployment Plan**: Schedule fixed version
  - Coordinate: With engineering and stakeholders
  - Timeline: Typically 1-3 days after fix

---

## Quick Reference Commands

```bash
# Pre-deployment
ant compile && ant unitTest                        # Verify build
bash scripts/health-check.sh                       # Current state
sudo bash scripts/backup-before-deploy.sh --full   # Create backup

# Deployment
sudo systemctl stop yawl-app                       # Stop services
cp build/lib/*.war $CATALINA_HOME/webapps/         # Deploy WAR
sudo systemctl start yawl-app                      # Start services
bash scripts/health-check.sh --wait-for-startup    # Verify startup

# If rollback needed
sudo bash scripts/rollback.sh                      # Execute rollback
bash scripts/health-check.sh                       # Verify success

# Post-deployment monitoring
tail -f $CATALINA_HOME/logs/catalina.out           # Monitor logs
curl http://localhost:8080/yawl/api/cases          # Test API
```

---

## Deployment Window Definitions

**Standard Deployment**
- Duration: 30-45 minutes
- Downtime: 10-15 minutes (brief maintenance window)
- Typical: Tuesday-Thursday, 22:00-23:00 UTC (off-peak)

**Emergency Hotfix**
- Duration: 15-20 minutes
- Downtime: 5-10 minutes
- Approved by: CTO or VP Engineering only

**Rollback Deployment**
- Duration: 10-15 minutes
- Downtime: None (rolling update)
- Triggered by: On-call engineer or incident commander

---

## Deployment Success Metrics

| Metric | Target | Acceptable | Unacceptable |
|--------|--------|-----------|--------------|
| Deployment Time | < 30 min | < 45 min | > 60 min |
| Downtime | < 10 min | < 15 min | > 20 min |
| Health Checks | 100% pass | 95% pass | < 90% pass |
| Error Rate | < 0.1% | < 0.5% | > 1% |
| Response Time p95 | < 1s | < 2s | > 5s |
| CPU Usage | < 30% | < 50% | > 70% |
| Memory Usage | < 40% | < 60% | > 80% |

---

## Escalation Path

**Severity Levels**

| Level | Issue | Action |
|-------|-------|--------|
| **CRITICAL** | Production down, data loss | 1. Execute rollback immediately, 2. Page on-call director, 3. Incident commander takes charge |
| **HIGH** | Partial outage, performance issue | 1. Investigate for 5 minutes, 2. If unresolved, execute rollback, 3. Escalate to engineering lead |
| **MEDIUM** | Degraded performance, minor errors | 1. Monitor for 15 minutes, 2. If worsening, rollback, 3. File incident ticket |
| **LOW** | Expected warnings, monitoring only | 1. Continue monitoring, 2. Document in deployment log, 3. Address in next deployment |

---

## Document Information

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Last Updated | 2026-02-16 |
| Owner | DevOps Team |
| Related Documents | ROLLBACK-PROCEDURES.md, DEPLOYMENT-GUIDE.md |
| Contacts | devops@yawl-team.org, oncall@yawl-team.org |

