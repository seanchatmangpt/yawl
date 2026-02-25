# YAWL Disaster Recovery Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This document outlines the disaster recovery (DR) strategy and procedures for YAWL Workflow Engine deployments.

### 1.1 Recovery Objectives

| Metric | Target | Description |
|--------|--------|-------------|
| **RTO** (Recovery Time Objective) | 1 hour | Maximum downtime |
| **RPO** (Recovery Point Objective) | 5 minutes | Maximum data loss |
| **MTTR** (Mean Time To Recovery) | 30 minutes | Average recovery time |

### 1.2 DR Strategy Options

| Strategy | RTO | RPO | Cost | Complexity |
|----------|-----|-----|------|------------|
| **Active-Active** | 0 | 0 | High | High |
| **Active-Passive (Hot)** | <15 min | <5 min | Medium-High | Medium |
| **Active-Passive (Warm)** | <1 hour | <15 min | Medium | Medium |
| **Backup-Restore** | <4 hours | <24 hours | Low | Low |

---

## 2. Architecture Patterns

### 2.1 Multi-Region Active-Passive

```
+-------------------+                    +-------------------+
|  Primary Region   |                    |  DR Region        |
|                   |                    |                   |
|  +-------------+  |    Replication     |  +-------------+  |
|  | Engine (3)  |  |<------------------>|  | Engine (0)  |  |
|  +-------------+  |                    |  +-------------+  |
|         |         |                    |         |         |
|  +------v------+  |                    |  +------v------+  |
|  | PostgreSQL  |  |    WAL Shipping    |  | PostgreSQL  |  |
|  | Primary     |---------------------->| | Standby     |  |
|  +-------------+  |                    |  +-------------+  |
|         |         |                    |         |         |
|  +------v------+  |                    |  +------v------+  |
|  | Redis       |  |    Cluster         |  | Redis       |  |
|  | Primary     |  |    Replication     |  | Standby     |  |
|  +-------------+  |                    |  +-------------+  |
+-------------------+                    +-------------------+
         |                                       |
         +-------------------+-------------------+
                             |
                    +--------v--------+
                    |  Global DNS     |
                    |  (Health Check) |
                    +-----------------+
```

### 2.2 Database Replication

**PostgreSQL Streaming Replication:**

```sql
-- Primary server postgresql.conf
wal_level = replica
max_wal_senders = 10
wal_keep_size = 1GB
synchronous_commit = on
synchronous_standby_names = 'standby1'

-- Standby server recovery.conf
standby_mode = 'on'
primary_conninfo = 'host=primary-host port=5432 user=replicator password=secret'
trigger_file = '/tmp/postgresql.trigger.5432'
```

**Cloud-Specific Replication:**

| Cloud | Service | Replication Method |
|-------|---------|-------------------|
| AWS | RDS | Cross-Region Read Replica |
| Azure | Azure Database | Geo-Replica |
| GCP | Cloud SQL | Cross-Region Replica |

---

## 3. Backup Strategy

### 3.1 Backup Types

| Backup Type | Frequency | Retention | Size |
|-------------|-----------|-----------|------|
| Full Database | Daily | 35 days | ~100% |
| Incremental | Hourly | 7 days | ~5% |
| WAL Archive | Continuous | 7 days | Variable |
| Configuration | On change | 90 days | <1 MB |

### 3.2 Backup Configuration

**AWS RDS:**

```bash
aws rds modify-db-instance \
  --db-instance-identifier yawl-postgres \
  --backup-retention-period 35 \
  --preferred-backup-window "02:00-03:00" \
  --copy-tags-to-snapshots
```

**Google Cloud SQL:**

```bash
gcloud sql instances patch yawl-postgres \
  --backup-start-time=02:00 \
  --retain-backups-count=35 \
  --enable-point-in-time-recovery
```

### 3.3 Backup Verification

```bash
#!/bin/bash
# verify-backups.sh

# Check last backup time
LAST_BACKUP=$(aws rds describe-db-instances \
  --db-instance-identifier yawl-postgres \
  --query 'DBInstances[0].LatestRestorableTime' \
  --output text)

# Ensure backup is less than 24 hours old
BACKUP_AGE=$(( $(date +%s) - $(date -d "$LAST_BACKUP" +%s) ))
if [ $BACKUP_AGE -gt 86400 ]; then
  echo "WARNING: Backup is older than 24 hours"
  exit 1
fi
```

---

## 4. Failover Procedures

### 4.1 Automatic Failover

**Database:**
- RDS Multi-AZ: Automatic failover in 60-120 seconds
- Cloud SQL HA: Automatic failover in ~60 seconds
- Azure Database: Automatic failover in ~60 seconds

**Kubernetes:**
- Pod restart: Automatic (liveness/readiness probes)
- Node failure: Automatic (pod rescheduling)

### 4.2 Manual Regional Failover

```bash
#!/bin/bash
# failover-to-dr.sh

set -e

PRIMARY_REGION="us-east-1"
DR_REGION="us-west-2"

echo "Starting failover to DR region..."

# Step 1: Verify DR database is ready
echo "Verifying DR database..."
aws rds describe-db-instances \
  --db-instance-identifier yawl-postgres-dr \
  --region $DR_REGION

# Step 2: Promote DR database to primary
echo "Promoting DR database..."
aws rds promote-read-replica \
  --db-instance-identifier yawl-postgres-dr \
  --region $DR_REGION

# Step 3: Scale up DR EKS cluster
echo "Scaling DR cluster..."
kubectl config use-context yawl-dr-cluster
kubectl scale deployment yawl-engine \
  --replicas=3 -n yawl
kubectl scale deployment yawl-resource-service \
  --replicas=2 -n yawl

# Step 4: Update DNS
echo "Updating DNS..."
aws route53 change-resource-record-sets \
  --hosted-zone-id $HOSTED_ZONE_ID \
  --change-batch file://dns-failover.json

# Step 5: Verify services
echo "Verifying services..."
curl -f https://yawl.yourdomain.com/ib/api/health

echo "Failover complete!"
```

### 4.3 Failback Procedures

```bash
#!/bin/bash
# failback-to-primary.sh

echo "Starting failback to primary region..."

# Step 1: Establish replication from current primary to original
echo "Establishing reverse replication..."
aws rds create-db-instance-read-replica \
  --db-instance-identifier yawl-postgres \
  --source-db-instance-identifier yawl-postgres-dr \
  --source-region us-west-2 \
  --region us-east-1

# Wait for replication to catch up
sleep 3600

# Step 2: Stop writes to DR
kubectl scale deployment yawl-engine \
  --replicas=0 -n yawl --context yawl-dr-cluster

# Step 3: Promote original primary
aws rds promote-read-replica \
  --db-instance-identifier yawl-postgres \
  --region us-east-1

# Step 4: Scale up original cluster
kubectl config use-context yawl-primary-cluster
kubectl scale deployment yawl-engine \
  --replicas=3 -n yawl

# Step 5: Update DNS back
aws route53 change-resource-record-sets \
  --hosted-zone-id $HOSTED_ZONE_ID \
  --change-batch file://dns-failback.json

echo "Failback complete!"
```

---

## 5. Recovery Procedures

### 5.1 Database Recovery from Backup

**Point-in-Time Recovery (PITR):**

```bash
# AWS RDS PITR
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier yawl-postgres \
  --target-db-instance-identifier yawl-postgres-restored \
  --restore-time 2024-01-15T10:00:00Z

# Google Cloud SQL PITR
gcloud sql instances clone yawl-postgres yawl-postgres-restored \
  --point-in-time="2024-01-15T10:00:00Z"
```

### 5.2 Full System Recovery

```bash
#!/bin/bash
# full-recovery.sh

TARGET_TIME=$1

echo "Starting full system recovery to $TARGET_TIME..."

# Step 1: Restore database
echo "Restoring database..."
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier yawl-postgres \
  --target-db-instance-identifier yawl-postgres-recovered \
  --restore-time $TARGET_TIME

# Wait for restore
aws rds wait db-instance-available \
  --db-instance-identifier yawl-postgres-recovered

# Step 2: Restore Redis from snapshot
echo "Restoring Redis..."
aws elasticache create-replication-group \
  --replication-group-id yawl-redis-recovered \
  --replication-group-description "Recovered Redis" \
  --snapshot-name yawl-redis-snapshot-$TARGET_TIME

# Step 3: Update application configuration
echo "Updating application config..."
kubectl create secret generic yawl-db-credentials \
  --from-literal=host=yawl-postgres-recovered.xxxxx.rds.amazonaws.com \
  --from-literal=password=$DB_PASSWORD \
  --namespace yawl \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 4: Restart applications
echo "Restarting applications..."
kubectl rollout restart deployment/yawl-engine -n yawl
kubectl rollout restart deployment/yawl-resource-service -n yawl

# Step 5: Verify recovery
echo "Verifying recovery..."
kubectl rollout status deployment/yawl-engine -n yawl
curl -f https://yawl.yourdomain.com/ib/api/health

echo "Recovery complete!"
```

---

## 6. DR Testing

### 6.1 Testing Schedule

| Test Type | Frequency | Scope |
|-----------|-----------|-------|
| Backup Restore | Monthly | Database snapshot restore |
| Failover Test | Quarterly | Database failover only |
| DR Drill | Bi-annually | Full regional failover |
| Tabletop Exercise | Annually | Process review |

### 6.2 DR Test Procedure

```markdown
## DR Test Checklist

### Pre-Test
- [ ] Schedule test window
- [ ] Notify stakeholders
- [ ] Verify backups are current
- [ ] Document current state

### During Test
- [ ] Verify DR database replication lag
- [ ] Test read access on DR database
- [ ] Verify DR cluster is ready
- [ ] Execute partial failover (read-only)
- [ ] Measure failover time
- [ ] Execute full failover
- [ ] Verify application functionality
- [ ] Execute failback

### Post-Test
- [ ] Document results
- [ ] Measure RTO/RPO achieved
- [ ] Identify improvements
- [ ] Update runbooks
```

---

## 7. Monitoring and Alerting

### 7.1 DR Health Metrics

| Metric | Threshold | Alert |
|--------|-----------|-------|
| Replication Lag | > 60 seconds | Warning |
| Replication Lag | > 300 seconds | Critical |
| Last Backup Age | > 26 hours | Warning |
| Last Backup Age | > 48 hours | Critical |
| DR Region Health | Unavailable | Critical |

### 7.2 Alert Configuration

```yaml
groups:
  - name: dr-alerts
    rules:
      - alert: DatabaseReplicationLag
        expr: pg_replication_lag_seconds > 60
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Database replication lag is high"
          description: "Replication lag is {{ $value }} seconds"

      - alert: BackupTooOld
        expr: time() - pg_last_backup_timestamp > 93600  # 26 hours
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "Database backup is older than 26 hours"
```

---

## 8. Contact and Escalation

### 8.1 DR Team Roles

| Role | Responsibility | Contact |
|------|----------------|---------|
| DR Coordinator | Overall coordination | [Primary] |
| DBA | Database recovery | [Primary] |
| Infrastructure | Infrastructure recovery | [Primary] |
| Application | Application recovery | [Primary] |
| Communications | Stakeholder updates | [Primary] |

### 8.2 Escalation Path

```
Level 1: On-call Engineer (15 min response)
    |
Level 2: DR Team Lead (30 min response)
    |
Level 3: DR Coordinator (1 hour response)
    |
Level 4: Executive Sponsor (as needed)
```

---

## 9. Best Practices

1. **Document everything**: Keep runbooks updated
2. **Test regularly**: Schedule periodic DR tests
3. **Automate failover**: Reduce human error
4. **Monitor continuously**: Catch issues early
5. **Train the team**: Ensure everyone knows the procedures
6. **Review after incidents**: Update procedures based on learnings
7. **Secure backups**: Encrypt and store offsite
8. **Practice failback**: Ensure you can return to normal

---

## 10. Next Steps

- [Upgrade Guide](upgrade-guide.md)
- [Security Overview](../security/security-overview.md)
- [Operations Guide](scaling-guide.md)
