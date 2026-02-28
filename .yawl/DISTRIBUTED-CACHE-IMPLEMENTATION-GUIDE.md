# YAWL Distributed Build Cache — Implementation Quick Start Guide

**Status**: Ready for Development
**Version**: 1.0
**Date**: 2026-02-28
**Owner**: Engineer O

---

## Quick Navigation

**Start here based on your role**:

- **Tech Lead / Architecture Reviewer**: Read [DISTRIBUTED-CACHE-PLAN.md](.yawl/DISTRIBUTED-CACHE-PLAN.md) (Executive Summary section)
- **Finance / Budget Approver**: Read [DISTRIBUTED-CACHE-BUDGET.md](.yawl/DISTRIBUTED-CACHE-BUDGET.md)
- **Implementation Engineer**: Follow steps below → then read [DISTRIBUTED-CACHE-SPEC.md](.yawl/DISTRIBUTED-CACHE-SPEC.md)
- **DevOps / Operations**: Read [DISTRIBUTED-CACHE-OPS.md](.yawl/DISTRIBUTED-CACHE-OPS.md)
- **Security Review**: Check "Security Model" section in [DISTRIBUTED-CACHE-PLAN.md](.yawl/DISTRIBUTED-CACHE-PLAN.md)

---

## What We're Building

A **distributed build cache** that:
- ✅ Reduces new machine cold start from 90s → 35s (61% faster)
- ✅ Shares cache artifacts across all developers via S3
- ✅ Works automatically (transparent to developers)
- ✅ Costs ~$7/year (negligible)
- ✅ Pays for itself in 2.7 months

**Core idea**: When you build, upload compiled artifacts to S3. When someone else builds the same module, they download it from S3 instead of recompiling.

---

## Implementation Checklist (4 Weeks)

### Week 1: Infrastructure Setup (8 hours)

```bash
# 1. Create AWS S3 bucket
ACCOUNT_ID="YOUR_AWS_ACCOUNT_ID"
BUCKET_NAME="yawl-build-cache-${ACCOUNT_ID}"

aws s3api create-bucket \
  --bucket "$BUCKET_NAME" \
  --region us-east-1

# 2. Enable encryption
aws s3api put-bucket-encryption \
  --bucket "$BUCKET_NAME" \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}
    }]
  }'

# 3. Block public access
aws s3api put-public-access-block \
  --bucket "$BUCKET_NAME" \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# 4. Set lifecycle policy (90-day retention)
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$BUCKET_NAME" \
  --lifecycle-configuration file://s3-lifecycle.json

# 5. Create IAM role for GitHub Actions
# See: .yawl/DISTRIBUTED-CACHE-OPS.md (AWS S3 Setup section)

# 6. Verify connectivity
aws s3 ls s3://$BUCKET_NAME/
```

**Deliverables**: S3 bucket created, IAM roles configured, GitHub Secrets set

---

### Week 2-3: Core Implementation (32 hours)

#### Task 1: Maven Configuration (4h)

**File**: `.mvn/maven-build-cache-remote-config.xml` (ALREADY CREATED ✓)

- ✅ Remote cache section with S3 endpoint
- ✅ Upload configuration (8 parallel streams)
- ✅ Download configuration (4 parallel streams)
- ✅ Retry logic (3 attempts, exponential backoff)
- ✅ Compression (GZIP)

**Activation**: Maven automatically uses this config if `YAWL_REMOTE_CACHE=1`

---

#### Task 2: Build Script Integration (8h)

**File**: `scripts/dx.sh`

**Changes needed**:
```bash
# 1. Add environment variable support
REMOTE_CACHE_ENABLED="${YAWL_REMOTE_CACHE:-1}"

# 2. Add command-line flag
case "$1" in
  --remote-cache)     REMOTE_CACHE_ENABLED=1; shift ;;
  --no-remote-cache)  REMOTE_CACHE_ENABLED=0; shift ;;
esac

# 3. Before Maven invocation
if [[ "$REMOTE_CACHE_ENABLED" == "1" ]]; then
  export YAWL_S3_BUCKET="${YAWL_S3_BUCKET:?Error: S3 bucket not set}"
  export YAWL_S3_REGION="${YAWL_S3_REGION:-us-east-1}"
  # Verify connectivity with timeout
  if ! timeout 1 aws s3 ls "s3://${YAWL_S3_BUCKET}/" >/dev/null 2>&1; then
    echo "⚠ Remote cache unreachable, using local only"
    export YAWL_REMOTE_CACHE=0
  fi
fi

# 4. Pass to Maven
mvn ... -Dyawl.remote.cache.enabled="${REMOTE_CACHE_ENABLED}"
```

**Testing**:
```bash
# Enable remote cache
bash scripts/dx.sh --remote-cache compile

# Disable remote cache (offline mode)
bash scripts/dx.sh --no-remote-cache compile
```

---

#### Task 3: Cache Management Script (12h)

**File**: `scripts/remote-cache-management.sh` (ALREADY CREATED ✓)

**Implementation needed** (replace TODOs):
- `cmd_sync_upload()`: Upload artifacts to S3
  - Iterate `.yawl/cache/` directory
  - For each module, compress and upload via `aws s3 cp`
  - Generate metadata JSON with timestamp, hash, size
  - Retry logic with backoff
- `cmd_sync_download()`: Download specific artifact
  - Check S3 for cache hit
  - Download with timeout (10s)
  - Verify SHA256 checksum
  - Extract to target directory
- `cmd_prune()`: Delete old artifacts
  - List S3 objects with LastModified date
  - Delete if older than N days
  - Report what was deleted
- Other commands: Mostly shell wrappers around existing checks

**Total lines**: ~500 (mostly implemented, needs ~200 lines completion)

---

#### Task 4: Metrics Collection (8h)

**Location**: `.yawl/metrics/remote-cache-stats.json`

**What to track**:
```json
{
  "timestamp": "2026-02-28T14:30:00Z",
  "builds_total": 225,
  "cache_hits_local": 150,
  "cache_hits_remote": 45,
  "cache_misses": 30,
  "hit_rate_total_pct": 86.7,
  "download_time_avg_sec": 4.2,
  "upload_time_avg_sec": 6.8,
  "cold_start_time_avg_sec": 35.2,
  "s3_storage_gb": 2.3,
  "s3_cost_estimated_usd": 0.42,
  "errors": {
    "upload_failures": 2,
    "download_failures": 1
  }
}
```

**Implementation**:
- Add metrics collection to `dx.sh` (before/after timings)
- Append to JSON log file (JSONL format for streaming)
- Expose via `remote-cache-management.sh stats` command
- Archive monthly for historical analysis

---

### Week 4: CI/CD Integration (12 hours)

#### Task 1: GitHub Actions Workflow (6h)

**File**: `.github/workflows/remote-cache-sync.yml` (ALREADY CREATED ✓)

**Status**: Complete with 13 steps
- Checkout, Java setup, AWS auth (OIDC)
- Build with remote cache
- Sync metrics to S3
- Report cache statistics
- Test result uploads
- PR comments with cache stats

**Activation**:
1. Set GitHub Secrets:
   ```bash
   gh secret set AWS_ROLE_ARN --body "arn:aws:iam::ACCOUNT:role/yawl-github-actions-cache"
   gh secret set S3_BUCKET_NAME --body "yawl-build-cache-ACCOUNT"
   ```

2. Push to trigger workflow

---

#### Task 2: Testing (6h)

**Manual Testing Checklist**:
- [ ] S3 connectivity (timeout: 1s, backoff works)
- [ ] Cache upload (3 modules, 8 parallel streams)
- [ ] Cache download (verify SHA256)
- [ ] Offline mode (S3 down, local build continues)
- [ ] Metrics collection (stats file populated)
- [ ] GitHub Actions workflow (runs on PR)
- [ ] Cache hit rate >50% (after warm-up)
- [ ] Download latency <5s
- [ ] Cold start <40s

**Test Commands**:
```bash
# Test connectivity
bash scripts/remote-cache-management.sh health

# Test upload
bash scripts/remote-cache-management.sh sync --upload

# Test stats
bash scripts/remote-cache-management.sh stats

# Test with build
YAWL_REMOTE_CACHE=1 bash scripts/dx.sh all

# Disable and test offline mode
YAWL_REMOTE_CACHE=0 bash scripts/dx.sh all
```

---

### Week 5+: Rollout & Monitoring (Ongoing)

#### Phase 1: Pilot (1 week)
- 2-3 developers + 1 CI runner
- Manual opt-in: `export YAWL_REMOTE_CACHE=1`
- Collect feedback on any issues

#### Phase 2: Gradual Rollout (1-2 weeks)
- Enable by default in `.mvn/maven.config`
- Developers can still disable via env var
- Monitor metrics dashboard

#### Phase 3: Full Adoption (ongoing)
- All machines use remote cache
- Weekly health checks
- Monthly performance reports
- Alert on: high failure rate, cost overrun, storage growth

---

## Key Files Reference

| File | Purpose | Status |
|------|---------|--------|
| `.yawl/DISTRIBUTED-CACHE-PLAN.md` | Architecture & strategy | ✅ Complete |
| `.yawl/DISTRIBUTED-CACHE-SPEC.md` | Technical specification | ✅ Complete |
| `.yawl/DISTRIBUTED-CACHE-BUDGET.md` | Cost analysis | ✅ Complete |
| `.yawl/DISTRIBUTED-CACHE-OPS.md` | Operations guide | ✅ Complete |
| `.mvn/maven-build-cache-remote-config.xml` | Maven config | ✅ Complete |
| `scripts/remote-cache-management.sh` | Cache admin tool | 90% Complete |
| `.github/workflows/remote-cache-sync.yml` | CI/CD workflow | ✅ Complete |

---

## Common Questions

### Q1: How do I enable remote cache?
**A**: Set environment variable before building:
```bash
export YAWL_REMOTE_CACHE=1
bash scripts/dx.sh all
```

Or use flag:
```bash
bash scripts/dx.sh --remote-cache all
```

### Q2: Will it fail if S3 is down?
**A**: No. Falls back to local cache gracefully:
1. Check S3 connectivity (1s timeout)
2. If S3 down → skip remote cache
3. Use local cache if available
4. Full build if local miss
5. Builds always succeed (remote cache is optional)

### Q3: What if I have slow internet?
**A**: Download has 10s timeout. If S3 is slow:
1. Download starts in background
2. Local build starts in parallel
3. Whichever finishes first wins
4. Fallback ensures build never hangs

### Q4: How much disk space do I need?
**A**: ~10 GB per developer (configurable, default in `.mvn/maven-build-cache-config.xml`)
- Local cache: 5-10 GB
- S3 cache: Unlimited (but costs money)
- Free up space: `bash scripts/remote-cache-management.sh clean --local`

### Q5: Can I use MinIO instead of AWS S3?
**A**: Yes! MinIO is S3-compatible:
```bash
export YAWL_S3_ENDPOINT="http://minio-server:9000"
export YAWL_S3_BUCKET="yawl-build-cache"
bash scripts/dx.sh --remote-cache all
```

See `DISTRIBUTED-CACHE-OPS.md` for MinIO setup.

### Q6: How do I monitor cache effectiveness?
**A**: Check stats:
```bash
bash scripts/remote-cache-management.sh stats
bash scripts/remote-cache-management.sh health
bash scripts/remote-cache-management.sh dashboard
```

Or view metrics file:
```bash
jq . .yawl/metrics/remote-cache-stats.json
```

### Q7: What if checksum fails?
**A**: Cache artifact is corrupted:
1. Artifact deleted from S3
2. Build continues (triggers full compilation)
3. Fresh artifact uploaded
4. Next build uses new artifact

### Q8: How much does this cost?
**A**: For typical team of 10:
- AWS S3: ~$7/year (negligible)
- MinIO self-hosted: ~$130/year (one-time hardware)

See `DISTRIBUTED-CACHE-BUDGET.md` for full breakdown.

---

## Troubleshooting Quick Links

**Problem**: "Remote cache unavailable"
→ See `DISTRIBUTED-CACHE-OPS.md`, section "Issue: Remote cache unavailable"

**Problem**: "Checksum mismatch"
→ See `DISTRIBUTED-CACHE-OPS.md`, section "Issue: Checksum mismatch"

**Problem**: "Slow downloads (>10s)"
→ See `DISTRIBUTED-CACHE-OPS.md`, section "Issue: Slow downloads"

**Problem**: "Credentials expired"
→ See `DISTRIBUTED-CACHE-OPS.md`, section "Issue: Credentials expired"

**Problem**: "Out of disk space"
→ See `DISTRIBUTED-CACHE-OPS.md`, section "Issue: Out of disk space"

---

## Success Metrics (Post-Implementation)

Track these weekly:
```bash
bash scripts/remote-cache-management.sh stats | jq . > /tmp/metrics-$(date +%Y%m%d).json
```

| Metric | Target | How to Check |
|--------|--------|--------------|
| Cold start time | <35s | Run on fresh VM, time from `mvn clean verify` to completion |
| Cache hit rate | >50% | `cache_hit_rate_total_pct` in stats |
| Download latency | <5s | `download_time_avg_sec` in stats |
| Upload latency | <10s | `upload_time_avg_sec` in stats |
| Availability | 99.9% | Monitor S3 uptime (AWS SLA) |
| Cost | <$20/month | `s3_cost_estimated_usd` × 30 in stats |

---

## Implementation Timeline

```
Week 1 (Feb 28 - Mar 7):      Infrastructure setup
  Day 1: S3 bucket creation
  Day 2: IAM roles, GitHub Secrets
  Day 3: Testing, documentation

Week 2-3 (Mar 8 - 21):        Core implementation
  Week 2: Maven config, build script integration
  Week 3: Cache script, metrics collection

Week 4 (Mar 22 - 28):          CI/CD integration
  Week 4: GitHub workflow, testing, documentation

Week 5+ (Mar 29+):             Rollout & monitoring
  Pilot: 2-3 developers
  Gradual: Enable for team
  Full: Monitor & optimize
```

**Go-Live Target**: 2026-03-17 (end of Phase 4)

---

## Next Steps

1. **Review** this guide and linked documents
2. **Approve** architecture + budget
3. **Assign** engineers to Phase 1 (infrastructure)
4. **Create** S3 bucket + IAM roles
5. **Set** GitHub Secrets
6. **Begin** Phase 2 implementation
7. **Test** thoroughly before rollout
8. **Monitor** metrics for 1 week
9. **Gather** team feedback
10. **Optimize** based on real usage

---

## Support & Questions

- **Architecture questions**: See `DISTRIBUTED-CACHE-PLAN.md`
- **Implementation details**: See `DISTRIBUTED-CACHE-SPEC.md`
- **Cost questions**: See `DISTRIBUTED-CACHE-BUDGET.md`
- **Operations/troubleshooting**: See `DISTRIBUTED-CACHE-OPS.md`
- **Script usage**: `bash scripts/remote-cache-management.sh help`

---

**Version**: 1.0
**Status**: READY FOR IMPLEMENTATION
**Last Updated**: 2026-02-28
**Next Review**: After Phase 1 (2026-03-07)
