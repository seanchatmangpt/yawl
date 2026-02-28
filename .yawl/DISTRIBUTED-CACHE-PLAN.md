# YAWL Distributed Build Cache — Architecture & Strategy

**Status**: PLANNING | Quantum 5 | Phase 4 (Build Optimization)
**Owner**: Engineer O | **Target Completion**: Week of 2026-03-10
**Success Metric**: New machine cache hit in <5s (vs 90s cold start)

---

## Executive Summary

The YAWL project currently has a **local per-machine build cache** (`.yawl/cache/`) that improves warm builds by ~30-70%. However, new developer machines and CI runners experience **90-120s cold starts** because they rebuild everything from scratch.

**Goal**: Implement a **distributed build cache backend** that lets new machines download cached artifacts in <5s, reducing first-build time from ~90s to ~35s (61% improvement).

**Recommendation**: **Option B (S3-Compatible Backend)** for balance of simplicity, cost, and scalability.

---

## Option Evaluation Matrix

| Aspect | Option A: Maven Remote | Option B: S3-Compatible | Option C: Git LFS | Option D: Hybrid |
|--------|------------------------|-------------------------|-------------------|------------------|
| **Setup Complexity** | Medium (paid service) | Low (MinIO or AWS S3) | Low (git native) | High (dual system) |
| **Cost** | $10-50/mo | $2-8/mo | Free (LFS bandwidth) | $5-20/mo |
| **Cold Start Time** | 15-20s | 3-5s | 8-12s | 2-4s |
| **Network Dependency** | High | High | Medium | High |
| **Scalability** | Good | Excellent | Poor | Good |
| **Maintenance** | Low | Medium | Low | High |
| **Team Adoption** | Easy | Easy | Very easy | Medium |
| **Offline Mode** | Requires fallback | Fallback via local | Limited | Excellent |
| **TTL/Expiration** | Auto-handled | Manual | N/A | Manual |
| **Authentication** | OAuth/token | IAM/bucket policy | Git ssh | Multiple |
| **Industry Standard** | Yes (legacy) | Yes (ubiquitous) | Yes (popular) | Experimental |

---

## Option Deep-Dive: S3-Compatible (Recommended)

### Why S3-Compatible?

1. **Simplicity**: Single backend, well-understood protocol
2. **Cost**: Negligible ($2-8/mo for typical team)
3. **Speed**: Low latency with regional endpoints
4. **Flexibility**: Works with AWS S3, MinIO, DigitalOcean Spaces, Backblaze B2, GCS
5. **Ecosystem**: Excellent CLI tools (aws, mc, gsutil)
6. **Security**: IAM-based access control, encryption at rest/transit
7. **Scalability**: Unlimited storage, automatic tiering available
8. **DevOps-Friendly**: No custom code, pure HTTP

### Target Architecture

```
Developer / CI Runner
    ↓
Local cache (.yawl/cache/)
    ├─ [Cache hit] → Use local file (0.1s)
    ├─ [Cache miss] → Download from S3
    │  └─ s3://yawl-build-cache/{module}/{hash}.tar.gz (3-5s)
    └─ [S3 unavailable] → Cold build (full compile + test)

After successful build:
    ↓
Upload cache artifacts to S3
    ├─ Parallel uploads (8 concurrent streams)
    ├─ Compression (gzip, 40-50% reduction)
    ├─ Tagging (module, date, owner)
    └─ Retention policy (90 days, then archive to Glacier)
```

### Implementation Timeline

**Phase 1 (Week 1)**: Infrastructure setup
- [ ] Create AWS S3 bucket (or MinIO cluster)
- [ ] Configure bucket policy (VPC endpoint, encryption)
- [ ] Set up credentials (IAM roles, access keys)
- [ ] Test connectivity (aws s3 ls, upload/download)

**Phase 2 (Week 2)**: Integration
- [ ] Add Maven remote cache configuration
- [ ] Implement cache sync script (`scripts/remote-cache-sync.sh`)
- [ ] Update `dx.sh` with `--remote-cache` flag
- [ ] Add fallback logic (fail gracefully if S3 down)

**Phase 3 (Week 3)**: Rollout
- [ ] Test on staging environment
- [ ] Onboard developers (documentation, troubleshooting)
- [ ] Monitor metrics (hit rate, latency, cost)
- [ ] Gather feedback and iterate

**Phase 4 (Week 4+)**: Optimization & Expansion
- [ ] Implement TTL-based cleanup
- [ ] Add cost tracking and budgeting
- [ ] Extend to CI/CD pipelines
- [ ] Consider Glacier archival for cold storage

---

## Cost Analysis (S3-Compatible)

### AWS S3 Estimate (Per Month)

**Assumptions**:
- Cache size per module: ~50MB average (compressed)
- Modules: 20 active modules
- Cache refresh: 2× per week per developer
- Team size: 10 developers + 5 CI runners

**Monthly Volumes**:
- **Storage**: 20 modules × 50MB = 1GB baseline
  - Growth: 10 developers × 2 uploads/week × ~100MB = 2GB/month
  - Total stored: ~3GB (1-month rolling)
  - Cost: 3GB × $0.023/GB = **$0.07/month**

- **Upload (PUT/POST)**:
  - 10 devs × 8 uploads/week + 5 CI runners × 20 builds/week
  - ~360 uploads/month
  - Cost: 360 × $0.0000050 = **$0.002/month**

- **Download (GET)**:
  - 10 devs × 8 cold starts/week + 5 CI runners × 20 jobs/week
  - ~720 downloads/month × 100MB avg = 72GB/month
  - Cost: 72GB × $0.0090/GB (inbound free, outbound charged) = **$0.65/month**

- **Data Transfer Out** (cross-region or internet egress):
  - Typical: 500-1000MB/month
  - Cost: 500MB × $0.0900/GB = **$0.05/month**

**Total AWS S3**: ~**$0.73/month** (essentially free tier)

### MinIO Self-Hosted (Per Year)

**One-time costs**:
- Storage hardware: $200-500 (old server repurposed)
- Network: Included in office infrastructure
- Monitoring: Free (Prometheus + Grafana)

**Ongoing**:
- Electricity: ~$10/month (modest)
- Maintenance: ~2h/month
- Total: ~**$130/year** (minimal)

### Cost Recommendation

- **Startups/Teams < 20**: AWS S3 (~$1-5/month)
- **Established teams > 50**: MinIO self-hosted (~$130/year)
- **Hybrid**: S3 as primary, MinIO as fallback

---

## Security Model

### Access Control Tiers

**Tier 1: Developers (Read-Only)**
- Can download cache artifacts
- Cannot upload (prevent accidental overwrites)
- Requires valid AWS credentials (IAM user or temporary token)
- Recommended: Use short-lived tokens (AWS SigV4 with 1h expiry)

**Tier 2: CI/CD (Read-Write)**
- Can download and upload cache artifacts
- Restricted to specific modules/paths
- Uses service account credentials
- Recommended: IAM role + STS assume role

**Tier 3: Admin (Full Access)**
- Can manage bucket, policies, credentials
- Manage retention and archival policies
- Monitor costs and quotas
- Typically: DevOps/SRE team

### Implementation

**AWS S3 Bucket Policy**:
```json
{
  "Statement": [
    {
      "Sid": "AllowDeveloperRead",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::ACCOUNT:group/yawl-developers"
      },
      "Action": ["s3:GetObject", "s3:GetObjectVersion"],
      "Resource": "arn:aws:s3:::yawl-build-cache/*"
    },
    {
      "Sid": "AllowCIUpload",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::ACCOUNT:role/github-actions-runner"
      },
      "Action": ["s3:PutObject", "s3:PutObjectAcl"],
      "Resource": "arn:aws:s3:::yawl-build-cache/*",
      "Condition": {
        "StringEquals": {
          "s3:x-amz-server-side-encryption": "AES256"
      }
    }
  ]
}
```

### Credential Management

**Never commit credentials to git**:
```bash
# ✓ Good: Use environment variables
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."

# ✗ Bad: Don't commit to .env files
cat .env  # NEVER

# ✓ Better: Use IAM roles (AWS Lambda, EC2, GitHub Actions)
aws sts assume-role --role-arn arn:aws:iam::ACCOUNT:role/yawl-build-cache
```

**Credential Rotation**:
- Rotate every 90 days
- Use AWS Secrets Manager or GitHub Secrets
- Implement automated rotation (AWS Lambda)

---

## Integration Points

### 1. Maven Integration

**File**: `.mvn/maven-build-cache-remote-config.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0">
  <!-- Existing local cache -->
  <local>
    <maxBuildsCached>50</maxBuildsCached>
    <retentionPeriod>P30D</retentionPeriod>
    <maxSize>10GB</maxSize>
  </local>

  <!-- NEW: Remote S3 backend -->
  <remote enabled="true" id="yawl-s3-cache">
    <url>s3://yawl-build-cache/cache/{module}</url>
    <transport>http</transport>
    <timeout>30000</timeout>
    <credentials>
      <username>${aws.access.key.id}</username>
      <password>${aws.secret.access.key}</password>
    </credentials>
    <retry>
      <maxAttempts>3</maxAttempts>
      <backoffBase>1000</backoffBase>
    </retry>
  </remote>

  <!-- Fallback policy: local miss → S3 miss → full build -->
  <fallbackPolicy>
    <localMiss>REMOTE</localMiss>
    <remoteMiss>BUILD</remoteMiss>
  </fallbackPolicy>
</cache>
```

### 2. Build Script Integration (dx.sh)

**New flag**: `--remote-cache`

```bash
# Enable remote cache explicitly
bash scripts/dx.sh --remote-cache compile

# With reporting
bash scripts/dx.sh --remote-cache --stats

# Disable for debugging
bash scripts/dx.sh --no-remote-cache
```

**Environment variables**:
```bash
# S3 endpoint
YAWL_S3_ENDPOINT="s3.amazonaws.com"

# Bucket name
YAWL_S3_BUCKET="yawl-build-cache"

# Region
YAWL_S3_REGION="us-east-1"

# Disable remote cache
YAWL_REMOTE_CACHE=0

# Timeout for uploads/downloads (seconds)
YAWL_REMOTE_CACHE_TIMEOUT=30
```

### 3. CI/CD Integration (GitHub Actions)

**File**: `.github/workflows/remote-cache-sync.yml`

```yaml
name: Build + Remote Cache Sync

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: us-east-1

      - name: Build with Remote Cache
        run: |
          bash scripts/dx.sh --remote-cache all

      - name: Upload Cache to S3
        if: success()
        run: |
          bash scripts/remote-cache-management.sh sync --upload

      - name: Report Metrics
        run: |
          bash scripts/remote-cache-management.sh metrics --upload
```

---

## Offline & Fallback Modes

### Network Unavailable

When S3 is unreachable (DNS failure, network partition, timeout):

```
Check S3 connectivity
    ↓
[FAIL] → Log warning
    ↓
Use local cache only
    ↓
[Local hit] → Return result
[Local miss] → Full build
```

**Implementation**:
```bash
# Fast connectivity check (1s timeout)
if ! timeout 1 aws s3 ls s3://yawl-build-cache/ >/dev/null 2>&1; then
    echo "⚠ Remote cache unavailable. Using local cache only."
    export YAWL_REMOTE_CACHE=0
fi
```

### Degraded Performance

If S3 is slow (>5s for downloads):

```
Download starts
    ↓
[Timeout after 5s] → Abandon download, start local build in parallel
    ↓
[Local completes first] → Use local result, cancel S3 download
[S3 completes first] → Use S3 result, cancel local build
```

**Implementation**: Fork process for parallelism + kill slower one.

---

## Monitoring & Observability

### Metrics to Track

**Performance**:
- Cache hit rate (local %)
- Cache hit rate (remote %)
- Average download time (seconds)
- Average upload time (seconds)
- Cold start time (new machine)

**Cost**:
- S3 storage (GB)
- Data transfer out (GB/month)
- Estimated cost ($/month)

**Health**:
- Upload failures (count, %)
- Download failures (count, %)
- S3 API errors (rate)

### Monitoring Implementation

**Metrics file**: `.yawl/metrics/remote-cache-stats.json`

```json
{
  "timestamp": "2026-02-28T14:30:00Z",
  "period_days": 7,
  "cache_hits_local": 150,
  "cache_hits_remote": 45,
  "cache_misses": 30,
  "hit_rate_total_pct": 86.5,
  "hit_rate_remote_pct": 23.1,
  "download_time_avg_sec": 4.2,
  "upload_time_avg_sec": 6.8,
  "cold_start_time_avg_sec": 35,
  "s3_storage_gb": 2.3,
  "s3_transfer_out_gb": 45,
  "s3_cost_estimated_usd": 0.42,
  "errors": {
    "upload_failures": 2,
    "download_failures": 1,
    "api_errors": 0
  }
}
```

### Dashboard

Metrics exposed via simple HTTP endpoint (optional):

```bash
GET /.yawl/metrics/remote-cache.json
→ Returns latest metrics JSON

GET /.yawl/metrics/remote-cache/history?days=30
→ Returns metrics for last 30 days (aggregated)
```

---

## Migration & Rollout Strategy

### Phase 1: Pilot (Week 1-2)

**Target**: 2-3 developers + 1 CI runner

```bash
# Manual opt-in
export YAWL_REMOTE_CACHE=1
bash scripts/dx.sh all
```

**Metrics to watch**:
- Any S3 connection errors?
- Cache hit rate reasonable?
- Upload/download speeds acceptable?

### Phase 2: Gradual Rollout (Week 3-4)

**Enable by default for new machines**:

```bash
# .mvn/maven.config (new machines use remote cache by default)
-Dyawl.remote.cache.enabled=true
```

**Keep opt-out**:
```bash
# Developers can disable if issues arise
export YAWL_REMOTE_CACHE=0
bash scripts/dx.sh all
```

### Phase 3: Full Adoption (Week 5+)

**All machines use remote cache** (with graceful fallback to local-only if S3 fails).

**Documentation & Training**:
- Troubleshooting guide (see `.yawl/DISTRIBUTED-CACHE-SPEC.md`)
- Setup instructions for new developers
- Monitoring dashboard access

---

## Success Metrics

| Metric | Baseline | Target | Timeline |
|--------|----------|--------|----------|
| Cold start time (new machine) | 90s | <35s | Week 4 |
| Cache hit rate (remote) | N/A | >50% | Week 3 |
| Download latency (avg) | N/A | <5s | Week 2 |
| Upload latency (avg) | N/A | <10s | Week 2 |
| S3 monthly cost | N/A | <$10 | Ongoing |
| Adoption rate | N/A | >80% of team | Week 5 |
| Team satisfaction | N/A | 4/5 stars | Week 6 |

---

## Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|-----------|
| S3 service outage | Build failures | Low (AWS SLA 99.99%) | Local fallback, offline mode |
| Leaked credentials | Security breach | Medium | Rotate keys, use IAM roles, scan git history |
| Unbounded storage growth | Cost increase | Medium | TTL policy, Glacier archival, pruning |
| Slow S3 response | Build delays | Medium | Parallel upload/download, timeout + fallback |
| Wrong cache artifact | Test failures | Low | Strict hashing, version tags in cache key |
| Developer confusion | Support burden | Medium | Clear documentation, training session |

---

## Next Steps

1. **Finalize backend choice** → Section `.yawl/DISTRIBUTED-CACHE-SPEC.md`
2. **Design cache protocol** → Cache key format, compression, tagging
3. **Prototype implementation** → Manual S3 upload/download
4. **Cost analysis** → Detailed AWS vs MinIO breakdown
5. **Security review** → Credentials, policies, audit logging
6. **Documentation** → Setup, troubleshooting, operations
7. **Rollout plan** → Phased adoption with metrics

---

## References

- **Current Local Cache**: `.yawl/cache/`, `docs/cache-system.md`
- **Maven Build Cache**: `https://maven.apache.org/shared/maven-build-cache/`
- **AWS S3 Pricing**: `https://aws.amazon.com/s3/pricing/`
- **MinIO**: `https://min.io/`
- **Implementation Spec**: `.yawl/DISTRIBUTED-CACHE-SPEC.md` (coming)
- **Cost Analysis**: `.yawl/DISTRIBUTED-CACHE-BUDGET.md` (coming)

---

## Approval Checklist

- [ ] Architecture approved by Tech Lead
- [ ] Cost analysis reviewed by Finance
- [ ] Security model approved by InfoSec
- [ ] Implementation plan assigned to team
- [ ] Rollout strategy communicated to team
- [ ] Success metrics defined and tracked

**Status**: READY FOR IMPLEMENTATION | Est. Effort: 80-100 hours (4 weeks, 2-3 engineers)
