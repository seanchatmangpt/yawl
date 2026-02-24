# YAWL Production Deployment Readiness Checklist

## Pre-Deployment (48 Hours Before)

### Code Preparation

- [ ] Feature branch prepared with all changes
- [ ] All commits follow conventional commit format
- [ ] Code reviewed by minimum 2 team members
- [ ] All comments addressed and resolved
- [ ] No unresolved conversations in PRs

### Testing & Quality

- [ ] 100% unit tests passing locally
- [ ] 100% unit tests passing in CI
- [ ] Integration tests passing
- [ ] Code coverage >= 65% (line), >= 55% (branch)
- [ ] No HYPER_STANDARDS violations detected
- [ ] SpotBugs analysis passed (no HIGH/MEDIUM issues)
- [ ] CheckStyle validation passed
- [ ] PMD code quality rules passed
- [ ] Security scan passed (no vulnerabilities)

### Configuration & Database

- [ ] `application-production.yml` updated if needed
- [ ] All database migrations created with proper naming
- [ ] Migration syntax validated (no SQL errors)
- [ ] Database migration tested locally
- [ ] Rollback script prepared for migrations
- [ ] Database backup strategy confirmed
- [ ] Connection pool settings reviewed

### Documentation

- [ ] README.md updated with version info
- [ ] CHANGELOG.md updated with all changes
- [ ] Architecture documentation current (if changed)
- [ ] API documentation updated (if changed)
- [ ] Migration guide prepared (if schema changed)
- [ ] Known issues documented (if any)

### Artifacts & Build

- [ ] Build passes locally: `mvn clean package -Pprod`
- [ ] Docker image builds successfully
- [ ] Docker image runs successfully locally
- [ ] Container image scanned for vulnerabilities
- [ ] All artifacts uploaded to registry
- [ ] SBOM generated (CycloneDX)
- [ ] Checksums calculated and verified
- [ ] GPG signatures generated (if applicable)

---

## 24 Hours Before Deployment

### Infrastructure Verification

- [ ] Kubernetes cluster status: ALL NODES READY
- [ ] Cluster resource availability checked
  - [ ] CPU: minimum 4 cores available
  - [ ] Memory: minimum 4Gi available
  - [ ] Disk: minimum 20Gi available
- [ ] Database cluster status: ALL NODES HEALTHY
- [ ] Database backup completed successfully
- [ ] Database replication lag: < 1 second
- [ ] Load balancer operational
- [ ] DNS records verified and cached

### Secrets & Access

- [ ] All GitHub Secrets configured
  - [ ] DATABASE_URL
  - [ ] DATABASE_USER
  - [ ] DATABASE_PASSWORD
  - [ ] ENCRYPTION_KEY
  - [ ] API_SIGNING_KEY
  - [ ] DOCKER_REGISTRY credentials
  - [ ] KUBECONFIG for production cluster
- [ ] Temporary credentials created (if needed)
- [ ] Credential rotation documented
- [ ] Access logs configured for deployment
- [ ] Audit logging enabled

### Monitoring & Alerting

- [ ] Prometheus metrics collection verified
- [ ] Grafana dashboards prepared
- [ ] Alert rules configured and tested
  - [ ] Error rate > 1%
  - [ ] Latency > 500ms
  - [ ] Pod restart spike
  - [ ] Database connection pool exhaustion
  - [ ] Disk space critical
- [ ] PagerDuty/On-call escalation tested
- [ ] Slack notification webhooks verified
- [ ] Log aggregation streaming to ELK/Splunk

### Communication

- [ ] Deployment window announced to team
- [ ] Stakeholder notifications sent (if needed)
- [ ] On-call team aware and available
- [ ] War room Slack channel created
- [ ] Communication plan prepared
  - [ ] Update frequency: every 5 minutes
  - [ ] Escalation contacts listed
  - [ ] Stakeholder distribution list

### Runbooks & Procedures

- [ ] Deployment runbook reviewed
- [ ] Rollback procedure tested (in staging)
- [ ] Incident response playbook updated
- [ ] Emergency contacts verified and current
- [ ] Escalation procedure confirmed
- [ ] War room procedures documented

---

## Day of Deployment (2 Hours Before)

### Final Validation

- [ ] Production readiness validation script passes
  ```bash
  bash ci-cd/scripts/validate-production-readiness.sh production
  ```
  **Expected Output:**
  ```
  RESULT: PRODUCTION READY
  ```

- [ ] Staging environment verified with same version
- [ ] Smoke tests pass in staging
- [ ] Performance baseline established
- [ ] Database migration tested in staging
- [ ] Backup/restore procedure tested
- [ ] Rollback tested in staging environment

### Infrastructure Health Check

- [ ] Kubernetes nodes: ALL READY
  ```bash
  kubectl get nodes
  ```

- [ ] Persistent volumes: ALL BOUND
  ```bash
  kubectl get pv
  ```

- [ ] Database connectivity verified
  ```bash
  kubectl exec -it <pod> -- \
    java -cp target/yawl.jar \
    -Dspring.datasource.url=$DATABASE_URL \
    org.springframework.boot.loader.PropertiesLauncher \
    --test-connection
  ```

- [ ] DNS resolution working
  ```bash
  nslookup yawl-production.svc.cluster.local
  ```

- [ ] TLS certificates valid
  ```bash
  kubectl get certificate -n yawl-production
  ```

### Team Readiness

- [ ] On-call engineer logged in and ready
- [ ] Senior engineer available for consultation
- [ ] Database team ready for migration support
- [ ] Infrastructure team standing by
- [ ] All team members in war room
- [ ] Communication channels tested

### System Status Dashboard

- [ ] Grafana dashboards displayed
- [ ] Current metrics baseline visible
  - [ ] Error rate: < 0.1%
  - [ ] P99 latency: < 200ms
  - [ ] Request throughput: steady
  - [ ] Pod resource usage: normal
- [ ] Alert rules verified firing correctly
- [ ] Log aggregation streaming

### Final Approval

- [ ] Code owner approval obtained
- [ ] Infrastructure owner sign-off
- [ ] Database team approval
- [ ] Release manager approval
- [ ] All approvals documented

---

## Deployment Window (Actual Deployment)

### Pre-Deployment Actions (T-15 minutes)

- [ ] Final health check of all systems
- [ ] Announce deployment start in all channels
- [ ] Start screen recording/session logging
- [ ] Begin deployment metrics baseline collection
- [ ] Verify team is in war room

### Deployment (T+0 to T+30 minutes)

**Blue-Green Strategy Timeline:**

| Time | Action | Verification |
|------|--------|--------------|
| T+0 | Initiate workflow | Check GitHub Actions status |
| T+2 | Readiness checks | All checks passing |
| T+7 | Manual approval (if required) | Approval obtained |
| T+12 | Deploy GREEN environment | Pods starting |
| T+20 | Smoke tests on GREEN | All tests passing |
| T+22 | Traffic switch | Monitor error rates |
| T+25 | Post-deployment validation | Full suite passing |
| T+30 | Deployment complete | Confirm in war room |

**Canary Strategy Timeline:**

| Time | Action | Validation |
|------|--------|------------|
| T+0 | Initiate workflow | Check GitHub Actions |
| T+2 | Readiness checks | All checks passing |
| T+7 | Manual approval (if required) | Approved |
| T+12 | Deploy 5% canary | Monitor metrics |
| T+22 | Increase to 25% | Error rate < 0.1% |
| T+32 | Increase to 50% | Latency < 200ms |
| T+42 | Complete to 100% | Database consistent |
| T+45 | Post-deployment validation | Full suite passing |

**Rolling Strategy Timeline:**

| Time | Action | Verification |
|------|--------|--------------|
| T+0 | Initiate workflow | Check GitHub Actions |
| T+2 | Readiness checks | All checks passing |
| T+7 | Manual approval (if required) | Approved |
| T+12 | Start rolling update | Monitor pod turnover |
| T+20 | 25% pods updated | Endpoints responding |
| T+25 | 50% pods updated | Error rate normal |
| T+30 | 75% pods updated | Performance stable |
| T+35 | 100% pods updated | All replicas ready |
| T+40 | Post-deployment validation | Smoke tests pass |

### Real-Time Monitoring Checklist

Every 2 minutes during deployment:

- [ ] Error rate remains < 0.1%
- [ ] P99 latency < 300ms (canary: < 500ms)
- [ ] No pod crash loops
- [ ] No memory pressure warnings
- [ ] Database latency normal
- [ ] No stuck database connections
- [ ] Cache hit rate stable
- [ ] Message queue processing normal
- [ ] Scheduled jobs completing normally
- [ ] No critical alerts firing

### Log Review (Continuous)

Monitor logs for:
- [ ] No exceptions in application logs
- [ ] No database connection errors
- [ ] No authentication failures
- [ ] No resource limit hits
- [ ] No cascading failures
- [ ] Migration logs (if applicable)
  - [ ] All migrations completed
  - [ ] No migration rollback

---

## Post-Deployment (Immediate)

### Deployment Completion Verification

**At T+30 minutes (or strategy completion time):**

- [ ] Workflow job status: SUCCESS
- [ ] All pods: Running and Ready
- [ ] Service endpoints: All responding
- [ ] Smoke test results: 100% passing
- [ ] Error rate: < 0.05% (should be normal)
- [ ] Response latency: Normal baseline
- [ ] Pod restart count: 0 since deployment
- [ ] Resource usage: Within limits

### Smoke Test Execution

```bash
bash ci-cd/scripts/smoke-tests.sh full production
```

**Expected Results:**
```
SMOKE TEST SUMMARY
==================
Passed:  35/35
Failed:   0
Skipped:  0

RESULT: ALL SMOKE TESTS PASSED
```

### Service Verification

- [ ] `GET /api/health` → 200 OK (< 100ms)
- [ ] `GET /api/version` → 200 OK with new version
- [ ] `GET /api/status` → 200 OK, all healthy
- [ ] `GET /api/v1/specifications` → 200 OK
- [ ] `GET /api/v1/cases` → 200 OK
- [ ] `GET /api/v1/workitems` → 200 OK
- [ ] Database queries responsive
- [ ] Authentication working
- [ ] Authorization working

### Agent Status Check

- [ ] All workflow agents registered
- [ ] No disconnected agents
- [ ] Agent communication latency normal
- [ ] Agent discovery successful
- [ ] Custom agents loaded (if applicable)

### Business Functionality Validation

- [ ] Can create new workflow case
- [ ] Can complete work items
- [ ] Can view case history
- [ ] Can run workflow queries
- [ ] Reports generating correctly
- [ ] Data consistency verified

### Announcement

```
Post-Deployment Announcement (War Room & Channels)
==================================================

Status: DEPLOYMENT SUCCESSFUL

Version: 5.2.0
Environment: Production
Deployment Time: 30 minutes
Strategy: Blue-green

Key Metrics:
- Error Rate: 0.02% (within SLA)
- P99 Latency: 145ms (below threshold)
- All Services: Operational
- Agent Status: 100% operational
- Database: Healthy and responsive

Next Steps:
- Continue monitoring for 2 hours
- Daily health checks for 1 week
- Full regression testing complete

Contact: @yawl-oncall for any issues
```

---

## 24-48 Hours Post-Deployment

### Stability Monitoring

- [ ] Error rate stable and low
- [ ] No unexpected latency spikes
- [ ] No memory leaks detected
- [ ] No connection pool issues
- [ ] No cascading failure events
- [ ] Metrics show normal patterns

### Log Analysis

- [ ] No critical errors in logs
- [ ] No database warnings
- [ ] No security violations
- [ ] No performance degradation
- [ ] Audit logs complete and consistent

### Performance Validation

- [ ] Throughput: ± 5% of baseline
- [ ] Latency: ± 10% of baseline
- [ ] Resource utilization: Normal
- [ ] Capacity headroom: Maintained

### Regression Testing

- [ ] Automated regression tests pass
- [ ] Manual smoke tests pass
- [ ] Business workflows operational
- [ ] Integration tests pass

### Final Sign-Off

- [ ] Infrastructure team: OK
- [ ] Database team: OK
- [ ] QA team: OK
- [ ] Product owner: OK
- [ ] Release manager: OK

---

## If Deployment Fails

### Immediate Actions (First 5 minutes)

1. [ ] **STOP** further changes
2. [ ] **ALERT** incident response team
3. [ ] **ASSESS** severity level
4. [ ] **CREATE** incident issue
5. [ ] **ESTABLISH** war room

### Decision Point (5-10 minutes)

**Is the issue critical (P1)?**

- **YES** → Go to [Rollback Execution](#rollback-execution)
- **NO** → Proceed to [Investigation](#investigation)

### Rollback Execution

**Automated Rollback:**
```bash
bash ci-cd/scripts/deploy.sh \
  --environment production \
  --target kubernetes \
  --rollback
```

**Manual Rollback (Blue-Green):**
```bash
# Get current service selector
kubectl get svc yawl -n yawl-production -o jsonpath='{.spec.selector}'

# Switch back to blue
kubectl patch service yawl -n yawl-production \
  -p '{"spec":{"selector":{"version":"blue"}}}'

# Verify
kubectl get svc yawl -n yawl-production -o jsonpath='{.spec.selector}'
```

**Verification After Rollback:**
```bash
# Check pods
kubectl get pods -n yawl-production

# Run smoke tests
bash ci-cd/scripts/smoke-tests.sh full production

# Verify service
curl http://yawl.yawl-production.svc.cluster.local:8080/api/health
```

### Investigation

1. [ ] Collect deployment logs
   ```bash
   kubectl logs -n yawl-production -l app=yawl --tail=200 > logs.txt
   ```

2. [ ] Collect metrics snapshot
   ```bash
   kubectl top pods -n yawl-production > metrics.txt
   kubectl top nodes >> metrics.txt
   ```

3. [ ] Get events
   ```bash
   kubectl get events -n yawl-production --sort-by='.lastTimestamp' > events.txt
   ```

4. [ ] Review application logs for root cause
5. [ ] Check database consistency
6. [ ] Verify infrastructure status

### Root Cause Categories

- **Code Issue** → Fix code, add tests, revert version
- **Configuration** → Update config, redeploy same version
- **Database** → Run recovery procedure, check migrations
- **Infrastructure** → Scale cluster, fix networking, retry deployment
- **External Dependency** → Wait for service restoration, retry

---

## Rollback/Restart Completion

### After Rollback Decision

- [ ] Incident timeline recorded
- [ ] Root cause documented (preliminary)
- [ ] Stakeholders notified
- [ ] Services confirmed operational
- [ ] Monitoring confirmed stable
- [ ] War room remains open for 1 hour

### Schedule Post-Incident Review

- [ ] RCA meeting scheduled (within 24 hours)
- [ ] All stakeholders invited
- [ ] Logs and metrics archived
- [ ] Remediation items identified
- [ ] Prevention measures planned

### Prepare for Re-Deployment

- [ ] Identify failing component
- [ ] Develop fix (if code issue)
- [ ] Test fix thoroughly
- [ ] Get additional code reviews
- [ ] Update runbooks with lessons learned
- [ ] Schedule re-deployment for next business day

---

## Rollback Procedure (P1 Emergency)

**Time: Must complete within 5 minutes**

### Step 1: Confirm Issue (30 seconds)

```
Question: Is the service degraded/down?
Answer: Check metrics dashboard

If YES → Proceed to Step 2
If NO → Cancel rollback, investigate
```

### Step 2: Execute Rollback (2 minutes)

```bash
# Option A: Automatic (Blue-Green)
kubectl patch service yawl -n yawl-production \
  -p '{"spec":{"selector":{"version":"blue"}}}'

# Option B: Kubernetes (Rolling)
kubectl rollout undo deployment/yawl -n yawl-production

# Verify immediately
sleep 10
curl http://yawl.yawl-production.svc.cluster.local:8080/api/health
```

### Step 3: Verify Recovery (2 minutes)

```bash
# Check error rate dropped
# Check latency normal
# Check pods healthy
# Announce in war room: ROLLBACK COMPLETE
```

### Step 4: Post-Rollback (30 seconds)

- [ ] Announce rollback completion
- [ ] Create incident issue
- [ ] Schedule RCA
- [ ] Keep war room open

---

## Escalation Contacts

### Primary On-Call
- **Name:** TBD
- **Phone:** +1-555-YAWL-911
- **Slack:** @yawl-oncall
- **Email:** oncall@yawlfoundation.org

### Escalation L2
- **Name:** TBD
- **Phone:** +1-555-YAWL-222
- **Slack:** @yawl-senior
- **Email:** senior@yawlfoundation.org

### Escalation L3
- **Name:** TBD
- **Phone:** +1-555-YAWL-333
- **Slack:** @yawl-lead
- **Email:** lead@yawlfoundation.org

---

## Appendix: Command Quick Reference

### Pre-Deployment
```bash
# Validate readiness
bash ci-cd/scripts/validate-production-readiness.sh production

# Test locally
mvn clean package -Pprod
docker build -t yawl:5.2.0 .
docker run -p 8080:8080 yawl:5.2.0

# Run smoke tests in staging
bash ci-cd/scripts/smoke-tests.sh full staging
```

### Deployment Trigger
```bash
# Via GitHub CLI
gh workflow run production-deployment.yml \
  -f deployment_env=production \
  -f deployment_strategy=blue-green \
  -f version=5.2.0

# Via GitHub Web UI
# Actions > Production Deployment > Run workflow
```

### Monitoring
```bash
# Watch pods
kubectl get pods -n yawl-production -w

# Check logs
kubectl logs -n yawl-production -l app=yawl -f

# Check metrics
kubectl top pods -n yawl-production

# Get events
kubectl get events -n yawl-production --sort-by='.lastTimestamp'
```

### Rollback
```bash
# Blue-Green immediate
kubectl patch service yawl -n yawl-production \
  -p '{"spec":{"selector":{"version":"blue"}}}'

# Kubernetes automatic
kubectl rollout undo deployment/yawl -n yawl-production

# Script-based
bash ci-cd/scripts/deploy.sh --environment production --rollback
```

---

**Document Version:** 1.0
**Last Updated:** February 18, 2026
**Next Review:** March 18, 2026

This checklist is a living document. Update after each deployment with lessons learned.
