# YAWL Upgrade Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This guide covers the upgrade process for YAWL Workflow Engine, including version upgrades, rolling updates, and rollback procedures.

---

## 2. Upgrade Types

| Type | Description | Downtime |
|------|-------------|----------|
| **Patch** | Bug fixes (5.2.0 -> 5.2.1) | None |
| **Minor** | Feature additions (5.1 -> 5.2) | Minimal |
| **Major** | Breaking changes (4.x -> 5.x) | Planned |

---

## 3. Pre-Upgrade Checklist

- [ ] Review release notes for breaking changes
- [ ] Backup database
- [ ] Verify backup integrity
- [ ] Test upgrade in staging environment
- [ ] Notify stakeholders
- [ ] Schedule maintenance window (if needed)
- [ ] Prepare rollback plan

---

## 4. Upgrade Procedures

### 4.1 Helm Upgrade

```bash
# Update Helm repository
helm repo update

# View available versions
helm search repo yawl --versions

# Dry-run upgrade
helm upgrade yawl yawl/yawl-stack \
  --namespace yawl \
  --version 5.3.0 \
  --dry-run

# Perform upgrade
helm upgrade yawl yawl/yawl-stack \
  --namespace yawl \
  --version 5.3.0 \
  --values values-production.yaml \
  --timeout 15m
```

### 4.2 Rolling Update

```bash
# Update image tag
kubectl set image deployment/yawl-engine \
  yawl-engine=yawl/engine:5.3.0 \
  -n yawl

# Monitor rollout
kubectl rollout status deployment/yawl-engine -n yawl

# Check pods
kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine
```

### 4.3 Database Migration

```bash
# Run migrations
kubectl exec -it deployment/yawl-engine -n yawl -- \
  java -jar /app/yawl-engine.jar migrate

# Verify migration
kubectl exec -it deployment/yawl-engine -n yawl -- \
  java -jar /app/yawl-engine.jar migrate --verify
```

---

## 5. Rollback Procedures

### 5.1 Helm Rollback

```bash
# View history
helm history yawl -n yawl

# Rollback to previous
helm rollback yawl -n yawl

# Rollback to specific revision
helm rollback yawl 5 -n yawl
```

### 5.2 Kubernetes Rollback

```bash
# View rollout history
kubectl rollout history deployment/yawl-engine -n yawl

# Undo last rollout
kubectl rollout undo deployment/yawl-engine -n yawl

# Undo to specific revision
kubectl rollout undo deployment/yawl-engine -n yawl --to-revision=3
```

---

## 6. Post-Upgrade Verification

```bash
# Check pod health
kubectl get pods -n yawl

# Check application logs
kubectl logs -f -l app.kubernetes.io/name=yawl-engine -n yawl

# Test API
curl https://yawl.yourdomain.com/ib/api/health

# Verify version
curl https://yawl.yourdomain.com/ib/api/version
```

---

## 7. Best Practices

1. **Always backup before upgrading**
2. **Test in non-production first**
3. **Use rolling updates for zero downtime**
4. **Monitor after upgrade**
5. **Have rollback plan ready**
6. **Document upgrade procedures**

---

## 8. Troubleshooting

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| Image pull error | CrashLoopBackOff | Check image registry, credentials |
| Migration failure | App fails to start | Restore backup, check migration logs |
| Compatibility issue | Runtime errors | Review release notes, update config |

---

## 9. Next Steps

- [Scaling Guide](scaling-guide.md)
- [Disaster Recovery](disaster-recovery.md)
