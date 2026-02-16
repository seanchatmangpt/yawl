# YAWL v5.2 Production Go-Live Checklist

**Target Go-Live Date:** March 2, 2026
**Version:** 5.2.0
**Last Updated:** 2026-02-16

---

## Pre-Flight Checklist (7 Days Before Go-Live)

### Infrastructure Readiness

- [ ] **Kubernetes Cluster**
  - [ ] GKE/EKS/AKS cluster created and configured
  - [ ] Node pools sized appropriately (min 3 nodes)
  - [ ] Auto-scaling enabled (min 3, max 10 nodes)
  - [ ] Network policies configured
  - [ ] RBAC roles and service accounts created

- [ ] **Database**
  - [ ] PostgreSQL 15+ instance provisioned
  - [ ] Database sized for production workload (db.r5.large or equivalent)
  - [ ] Automated backups configured (daily, 30-day retention)
  - [ ] Point-in-time recovery enabled
  - [ ] WAL archiving configured
  - [ ] Read replicas configured (if applicable)
  - [ ] Connection pooling configured (HikariCP: max 50, min 10)

- [ ] **SPIFFE/SPIRE**
  - [ ] SPIRE server deployed and healthy
  - [ ] SPIRE agent DaemonSet running on all nodes
  - [ ] Workload entries registered for all YAWL components
  - [ ] mTLS certificates verified
  - [ ] Certificate rotation tested (1-hour TTL)

- [ ] **Secrets Management**
  - [ ] External Secrets Operator deployed
  - [ ] SecretStore configured (GCP Secret Manager / AWS Secrets Manager / Azure Key Vault)
  - [ ] Database credentials stored in secret manager
  - [ ] API keys stored securely
  - [ ] Secret rotation procedures documented

- [ ] **Networking**
  - [ ] Ingress controller deployed (nginx-ingress)
  - [ ] Load balancer configured
  - [ ] DNS records created and verified
  - [ ] TLS certificates obtained (Let's Encrypt via cert-manager)
  - [ ] Certificate auto-renewal configured
  - [ ] Firewall rules configured

### Monitoring & Observability

- [ ] **Prometheus**
  - [ ] Prometheus deployed (kube-prometheus-stack)
  - [ ] ServiceMonitor configured for YAWL engine
  - [ ] Recording rules configured for SLIs
  - [ ] Data retention configured (30 days)

- [ ] **Grafana**
  - [ ] Grafana deployed and accessible
  - [ ] YAWL operational dashboard imported
  - [ ] SLO dashboard imported
  - [ ] Security dashboard imported
  - [ ] Alerts configured in Grafana

- [ ] **Alerting**
  - [ ] Alertmanager configured
  - [ ] PagerDuty integration configured
  - [ ] Slack integration configured
  - [ ] Alert routing rules configured
  - [ ] Critical alerts: Availability, Latency, Error Rate
  - [ ] Warning alerts: High resource usage, slow queries

- [ ] **OpenTelemetry**
  - [ ] OpenTelemetry Collector deployed
  - [ ] Traces exported to Jaeger/Tempo/Cloud Trace
  - [ ] Logs exported to Cloud Logging/CloudWatch
  - [ ] Trace sampling configured (1.0 for initial launch)

- [ ] **Logging**
  - [ ] Centralized logging configured
  - [ ] Log retention configured (90 days)
  - [ ] Log aggregation working (Loki/CloudWatch/Stackdriver)
  - [ ] Structured logging enabled (JSON format)

### Application Deployment

- [ ] **Build & Artifacts**
  - [ ] Maven build completes successfully
  - [ ] All tests passing (unit + integration)
  - [ ] Docker images built and pushed to registry
  - [ ] Image scanning completed (Trivy/Grype)
  - [ ] No critical vulnerabilities
  - [ ] Artifact signatures verified

- [ ] **Kubernetes Manifests**
  - [ ] Namespace created (`yawl`)
  - [ ] ConfigMaps created and validated
  - [ ] Secrets created via External Secrets Operator
  - [ ] Deployments created (yawl-engine, resource-service, etc.)
  - [ ] Services created
  - [ ] Ingress created and verified
  - [ ] HorizontalPodAutoscaler configured (min 3, max 10 replicas)
  - [ ] PodDisruptionBudget configured (minAvailable: 2)

- [ ] **Application Configuration**
  - [ ] Database connection string configured
  - [ ] SPIFFE socket path configured
  - [ ] Virtual threads enabled (Java 25)
  - [ ] JVM tuning parameters configured (-Xmx4g -Xms2g)
  - [ ] Actuator endpoints enabled
  - [ ] Metrics export enabled (Prometheus)
  - [ ] Health checks configured (readiness, liveness)

### Security

- [ ] **SPIFFE/SPIRE Verification**
  - [ ] SPIRE health check passing
  - [ ] SVIDs issued to all YAWL pods
  - [ ] mTLS connections verified between services
  - [ ] SPIFFE ID authorization rules configured

- [ ] **Network Security**
  - [ ] Network policies applied (default deny all)
  - [ ] Ingress traffic allowed only from load balancer
  - [ ] Egress traffic restricted to database, external APIs
  - [ ] DNS egress allowed

- [ ] **Pod Security**
  - [ ] Pod Security Standards enforced (Restricted)
  - [ ] Containers run as non-root
  - [ ] Read-only root filesystem (where applicable)
  - [ ] Capabilities dropped (ALL)
  - [ ] seccompProfile configured (RuntimeDefault)

- [ ] **RBAC**
  - [ ] Service accounts created with least privilege
  - [ ] Roles and RoleBindings configured
  - [ ] No cluster-admin access for workloads

- [ ] **Secrets**
  - [ ] No hardcoded secrets in code or manifests
  - [ ] Secrets injected from external provider
  - [ ] Secret rotation tested

### Testing

- [ ] **Functional Testing**
  - [ ] End-to-end workflow tested in staging
  - [ ] Case creation tested
  - [ ] Work item checkout/completion tested
  - [ ] Specification upload tested
  - [ ] All API endpoints verified

- [ ] **Performance Testing**
  - [ ] Load test executed (1,000 req/sec sustained for 1 hour)
  - [ ] p95 latency < 500ms verified
  - [ ] Error rate < 0.1% verified
  - [ ] Database query performance verified
  - [ ] Connection pool sizing validated

- [ ] **Failover Testing**
  - [ ] Database failover tested
  - [ ] Pod failure recovery tested (kill pod, verify auto-restart)
  - [ ] Node failure recovery tested (drain node, verify pod migration)
  - [ ] Load balancer failover tested

- [ ] **Disaster Recovery Testing**
  - [ ] Database backup restored successfully
  - [ ] Point-in-time recovery tested
  - [ ] RTO verified (< 15 minutes)
  - [ ] RPO verified (< 1 minute)

### Documentation

- [ ] **Runbooks**
  - [ ] GKE deployment runbook reviewed
  - [ ] EKS deployment runbook reviewed
  - [ ] AKS deployment runbook reviewed
  - [ ] Security runbook reviewed
  - [ ] Incident response runbook reviewed
  - [ ] Disaster recovery runbook reviewed

- [ ] **ADRs**
  - [ ] All ADRs reviewed and approved
  - [ ] ADR-001: Dual Engine Architecture
  - [ ] ADR-004: Spring Boot 3.4 + Java 25
  - [ ] ADR-005: SPIFFE/SPIRE
  - [ ] ADR-009: Multi-Cloud Strategy

- [ ] **SLO Documentation**
  - [ ] SLO targets documented and approved
  - [ ] SLI measurement methods defined
  - [ ] Error budget calculations verified
  - [ ] Monthly reporting process defined

---

## 24 Hours Before Go-Live

### Final Verification

- [ ] **Database**
  - [ ] Latest backup verified (< 24 hours old)
  - [ ] Backup restoration tested successfully
  - [ ] Database connection from YAWL pods verified
  - [ ] Database performance metrics within normal range

- [ ] **Monitoring**
  - [ ] All Grafana dashboards displaying data correctly
  - [ ] All Prometheus targets UP
  - [ ] Alert routing tested (send test alert to PagerDuty/Slack)
  - [ ] On-call rotation configured in PagerDuty

- [ ] **Security**
  - [ ] SPIFFE SVID expiry checked (> 2 hours remaining)
  - [ ] TLS certificates expiry checked (> 30 days remaining)
  - [ ] No open security vulnerabilities (critical/high)

- [ ] **Team Readiness**
  - [ ] On-call engineer assigned and briefed
  - [ ] Backup on-call engineer assigned
  - [ ] All team members aware of go-live time
  - [ ] Rollback plan reviewed with team
  - [ ] War room / Slack channel created (#yawl-golive)

- [ ] **Communication**
  - [ ] Customers notified of go-live window
  - [ ] Maintenance window scheduled (if applicable)
  - [ ] Status page updated
  - [ ] Internal stakeholders notified

- [ ] **Rollback Plan**
  - [ ] Previous version Docker images available
  - [ ] Rollback commands documented and tested
  - [ ] Database rollback scripts prepared (if schema changes)
  - [ ] Rollback decision criteria defined (e.g., error rate > 5%)

---

## Go-Live Procedure (Day Of)

### T-60 minutes: Pre-Deployment

- [ ] **Team Assembly**
  - [ ] War room established (Slack: #yawl-golive or Zoom call)
  - [ ] Roles assigned:
    - [ ] Deployment Lead
    - [ ] Database Admin
    - [ ] Monitoring Lead
    - [ ] Communication Lead
  - [ ] Rollback trigger defined (e.g., error rate > 5%, p95 latency > 2s)

- [ ] **Pre-Deployment Checks**
  - [ ] All pods healthy in current production
  - [ ] Database responsive
  - [ ] Monitoring dashboards green
  - [ ] No ongoing incidents
  - [ ] Traffic patterns normal

### T-30 minutes: Staging Validation

- [ ] **Staging Environment**
  - [ ] Deploy v5.2 to staging
  - [ ] Smoke tests passed
  - [ ] Performance tests passed
  - [ ] No errors in logs

### T-15 minutes: Blue-Green Setup

- [ ] **Blue Environment (Current v5.1)**
  - [ ] Verify stable and healthy
  - [ ] Traffic: 100%

- [ ] **Green Environment (New v5.2)**
  - [ ] Deploy v5.2 to production cluster (separate namespace or deployment)
  - [ ] Wait for all pods ready
  - [ ] Health checks passing
  - [ ] Traffic: 0% (not exposed to users yet)

### T-0: Canary Rollout

#### Phase 1: 10% Traffic (T+0 to T+15)

- [ ] **Route 10% traffic to v5.2**
  ```bash
  kubectl patch ingress yawl-ingress -n yawl -p '
  {
    "metadata": {
      "annotations": {
        "nginx.ingress.kubernetes.io/canary": "true",
        "nginx.ingress.kubernetes.io/canary-weight": "10"
      }
    }
  }'
  ```

- [ ] **Monitor for 15 minutes**
  - [ ] Error rate < 0.5%
  - [ ] p95 latency < 1s
  - [ ] No critical errors in logs
  - [ ] Health checks passing
  - [ ] Database queries performing normally

- [ ] **Decision Point: Continue or Rollback?**
  - [ ] **GO**: Proceed to Phase 2
  - [ ] **NO-GO**: Rollback (set canary-weight to 0)

#### Phase 2: 50% Traffic (T+15 to T+30)

- [ ] **Route 50% traffic to v5.2**
  ```bash
  kubectl patch ingress yawl-ingress -n yawl -p '
  {
    "metadata": {
      "annotations": {
        "nginx.ingress.kubernetes.io/canary-weight": "50"
      }
    }
  }'
  ```

- [ ] **Monitor for 15 minutes**
  - [ ] Error rate < 0.5%
  - [ ] p95 latency < 1s
  - [ ] Database connection pool healthy
  - [ ] Memory usage normal
  - [ ] CPU usage normal

- [ ] **Decision Point: Continue or Rollback?**
  - [ ] **GO**: Proceed to Phase 3
  - [ ] **NO-GO**: Rollback (set canary-weight to 0)

#### Phase 3: 100% Traffic (T+30 onwards)

- [ ] **Route 100% traffic to v5.2**
  ```bash
  kubectl patch ingress yawl-ingress -n yawl -p '
  {
    "metadata": {
      "annotations": {
        "nginx.ingress.kubernetes.io/canary": "false"
      }
    }
  }'
  ```

- [ ] **Decommission Blue Environment (v5.1)**
  ```bash
  kubectl scale deployment yawl-engine-v51 -n yawl --replicas=0
  ```

- [ ] **Monitor for 2 hours**
  - [ ] Error rate < 0.1%
  - [ ] p95 latency < 500ms
  - [ ] SLOs met
  - [ ] No user-reported issues

---

## Post-Deployment (T+2 hours)

### Verification

- [ ] **Metrics Check**
  - [ ] Availability: 100% (last 2 hours)
  - [ ] p95 latency: < 500ms
  - [ ] Error rate: < 0.1%
  - [ ] Active cases count matches pre-deployment
  - [ ] Case creation rate normal

- [ ] **Smoke Tests**
  - [ ] Create test case
  - [ ] Checkout work item
  - [ ] Complete work item
  - [ ] Query case details
  - [ ] All API endpoints responding

- [ ] **Database Verification**
  - [ ] Connection pool healthy
  - [ ] Query performance normal
  - [ ] No slow queries
  - [ ] Backup successful (post-deployment backup)

- [ ] **Security Verification**
  - [ ] SPIFFE SVIDs valid
  - [ ] mTLS connections working
  - [ ] No authentication failures
  - [ ] No authorization denials (unexpected)

### Communication

- [ ] **Internal Communication**
  - [ ] Post success message to #yawl-golive
  - [ ] Email team: "Go-live successful"
  - [ ] Update status page: "All systems operational"

- [ ] **Customer Communication**
  - [ ] Email customers: "Upgrade completed successfully"
  - [ ] Highlight new features (if applicable)
  - [ ] Provide support contact information

### Documentation

- [ ] **Update Documentation**
  - [ ] Mark go-live as complete
  - [ ] Document any issues encountered
  - [ ] Update runbooks with lessons learned
  - [ ] Archive deployment logs

---

## Post-Deployment Monitoring (First 7 Days)

### Daily Checks (Days 1-7)

- [ ] **Day 1**
  - [ ] Review metrics: Availability, Latency, Error Rate
  - [ ] Check for any anomalies
  - [ ] Review user feedback
  - [ ] Verify backups successful

- [ ] **Day 2**
  - [ ] Review metrics
  - [ ] Check resource usage trends
  - [ ] Review slow query log
  - [ ] Verify no memory leaks

- [ ] **Day 3**
  - [ ] Review metrics
  - [ ] Performance analysis
  - [ ] Identify optimization opportunities

- [ ] **Day 4**
  - [ ] Review metrics
  - [ ] Security audit (review authentication logs)
  - [ ] Certificate expiry check

- [ ] **Day 5**
  - [ ] Review metrics
  - [ ] Database performance review
  - [ ] Connection pool tuning (if needed)

- [ ] **Day 6**
  - [ ] Review metrics
  - [ ] Cost analysis (cloud spending)
  - [ ] Resource optimization

- [ ] **Day 7**
  - [ ] Weekly performance report
  - [ ] SLO compliance check
  - [ ] Lessons learned meeting

### Weekly Checks (Weeks 2-4)

- [ ] **Week 2**
  - [ ] Weekly performance report
  - [ ] SLO compliance review
  - [ ] Customer feedback review
  - [ ] Optimize based on production data

- [ ] **Week 3**
  - [ ] Performance optimization review
  - [ ] Security audit
  - [ ] Cost optimization review

- [ ] **Week 4**
  - [ ] Monthly SLO report
  - [ ] Incident review (if any)
  - [ ] Continuous improvement recommendations
  - [ ] **Close go-live project**

---

## Rollback Procedures

### Trigger Conditions

Initiate rollback if any of the following occur:

- [ ] Error rate > 5% for > 5 minutes
- [ ] p95 latency > 2 seconds for > 10 minutes
- [ ] Data corruption detected
- [ ] Security breach detected
- [ ] Database connectivity loss
- [ ] Critical bug discovered

### Rollback Execution

```bash
# 1. Stop new traffic to v5.2
kubectl patch ingress yawl-ingress -n yawl -p '
{
  "metadata": {
    "annotations": {
      "nginx.ingress.kubernetes.io/canary": "false"
    }
  }
}'

# 2. Scale down v5.2
kubectl scale deployment yawl-engine-v52 -n yawl --replicas=0

# 3. Scale up v5.1 (if not already running)
kubectl scale deployment yawl-engine-v51 -n yawl --replicas=5

# 4. Verify v5.1 healthy
kubectl get pods -n yawl
kubectl rollout status deployment/yawl-engine-v51 -n yawl

# 5. Rollback database (if schema changed)
kubectl exec -it postgres-0 -n yawl -- psql -U postgres yawl < rollback-v5.2-to-v5.1.sql

# 6. Verify health
curl https://yawl.example.com/engine/health

# 7. Monitor for 30 minutes
# Verify error rate < 0.1%, p95 latency < 500ms

# 8. Communicate rollback to team and customers
```

---

## Success Criteria

Go-live is considered successful if all of the following are met:

- [ ] **Deployment Completed**
  - [ ] v5.2 deployed to production
  - [ ] 100% traffic routed to v5.2
  - [ ] No rollback required

- [ ] **SLOs Met (First 24 Hours)**
  - [ ] Availability: > 99.95%
  - [ ] p95 Latency: < 500ms
  - [ ] Error Rate: < 0.1%
  - [ ] Data Durability: 100% (no data loss)

- [ ] **No Critical Issues**
  - [ ] No P0 or P1 incidents
  - [ ] No data corruption
  - [ ] No security breaches

- [ ] **Positive Feedback**
  - [ ] No customer-reported critical bugs
  - [ ] User acceptance testing passed
  - [ ] Team confident in stability

---

## Sign-Off

### Pre-Go-Live Approval

**Engineering Lead:**
- [ ] All technical requirements met
- [ ] Tests passing
- [ ] Ready for deployment

**Signed:** _________________ **Date:** _________

**Operations Lead:**
- [ ] Infrastructure ready
- [ ] Monitoring configured
- [ ] On-call team briefed

**Signed:** _________________ **Date:** _________

**Security Lead:**
- [ ] Security requirements met
- [ ] SPIFFE/SPIRE configured
- [ ] No critical vulnerabilities

**Signed:** _________________ **Date:** _________

**Product Manager:**
- [ ] Release notes approved
- [ ] Customer communication ready
- [ ] Go-live authorized

**Signed:** _________________ **Date:** _________

### Post-Go-Live Sign-Off

**Deployment Lead:**
- [ ] Deployment successful
- [ ] SLOs met
- [ ] No critical issues

**Signed:** _________________ **Date:** _________

---

## Appendix: Contact Information

| Role | Name | Email | Phone | Slack |
|------|------|-------|-------|-------|
| Deployment Lead | TBD | deploy-lead@yawl.org | +1-555-0100 | @deploy-lead |
| Database Admin | TBD | db-admin@yawl.org | +1-555-0123 | @db-admin |
| Security Lead | TBD | security@yawl.org | +1-555-0456 | @security-lead |
| On-Call Engineer | PagerDuty | - | - | #yawl-oncall |

---

**Document Version:** 1.0
**Last Updated:** 2026-02-16
**Next Review:** 2026-03-01 (day before go-live)
