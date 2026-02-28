# YAWL Distributed Build Cache — Implementation Specification

**Status**: READY FOR IMPLEMENTATION | Phase 4, Quantum 5
**Version**: 1.0 | Date: 2026-02-28
**Owner**: Engineer O | Target Delivery: Week of 2026-03-10
**Success Metric**: New machine cache hit in <5 seconds (vs 90s cold start)

---

## Table of Contents

1. [Overview](#overview)
2. [Cache Architecture](#cache-architecture)
3. [Backend Selection & Setup](#backend-selection--setup)
4. [Cache Protocol](#cache-protocol)
5. [Integration Points](#integration-points)
6. [Fallback & Error Handling](#fallback--error-handling)
7. [Operations & Maintenance](#operations--maintenance)
8. [Testing & Validation](#testing--validation)

---

## Overview

### Problem Statement

- **Current state**: Local per-machine cache improves warm builds by 30-70%
- **Cold start penalty**: New machines rebuild everything in 90-120 seconds
- **Team impact**: 10 developers × 2 new-machine setups/year = 33+ hours wasted
- **CI/CD impact**: Every fresh runner cold starts (99% of GitHub Actions runs)

### Solution Approach

Implement a distributed cache backend enabling:
- **New machines**: Download cache in <5 seconds
- **CI runners**: Reuse cached artifacts across jobs
- **Developers**: Transparent caching with optional manual control
- **Infrastructure**: S3-compatible backend (AWS S3 or MinIO)

### Target Improvement

| Metric | Baseline | Target | Improvement |
|--------|----------|--------|-------------|
| New machine cold start | 90s | 35s | 61% faster |
| Cache hit rate (remote) | N/A | >50% | Fast iterations |
| Download latency | N/A | <5s | Imperceptible |
| S3 monthly cost | N/A | <$10 | Negligible |

---

## Cache Architecture

### Logical Architecture

```
Developer / CI Runner
    │
    ├─→ Check Local Cache (.yawl/cache/)
    │   │
    │   ├─ [HIT] → Use cached artifact (0.1s)
    │   │   └─ Increment cache.hits.local
    │   │
    │   └─ [MISS] → Check Remote Cache (S3)
    │       │
    │       ├─ [HIT] → Download artifact (3-5s)
    │       │   ├─ Increment cache.hits.remote
    │       │   ├─ Store in local cache for next time
    │       │   └─ Extract and use
    │       │
    │       └─ [MISS] → Full Build
    │           ├─ Compile + test + package
    │           ├─ Create cache artifact
    │           └─ Upload to S3 (async)
    │
    └─ Report metrics to .yawl/metrics/remote-cache-stats.json
```

### Physical Architecture

```
┌─────────────────────────────────────────────────┐
│  Developer Workstation / CI Runner              │
├─────────────────────────────────────────────────┤
│  .yawl/cache/                                   │
│  ├─ local/                                      │
│  │  ├─ yawl-elements.{hash}.tar.gz              │
│  │  ├─ yawl-engine.{hash}.tar.gz                │
│  │  └─ ...                                      │
│  └─ manifest-local.json                         │
└──────────────┬──────────────────────────────────┘
               │ [Cache Miss]
               │ Download from S3
               ↓
┌─────────────────────────────────────────────────┐
│  AWS S3 / MinIO Bucket (yawl-build-cache)       │
├─────────────────────────────────────────────────┤
│  /                                              │
│  ├─ yawl-elements/                              │
│  │  ├─ {hash-1}.tar.gz      [uploaded 2/28]     │
│  │  ├─ {hash-2}.tar.gz      [uploaded 2/27]     │
│  │  └─ manifest.json        [tracking file]     │
│  ├─ yawl-engine/                                │
│  │  └─ ...                                      │
│  └─ .metadata/                                  │
│     └─ remote-manifest.json                     │
└─────────────────────────────────────────────────┘
```

### Cache Entry Structure

```
Cache Artifact = {
  module-name}/
  ├─ {content-hash}.tar.gz           [compressed cache]
  ├─ {content-hash}.tar.gz.sha256     [checksum for integrity]
  └─ metadata/
     └─ {content-hash}.json           [artifact metadata]
}

Metadata JSON = {
  "module": "yawl-elements",
  "hash": "a1b2c3d4e5f6...",
  "timestamp": "2026-02-28T14:32:15Z",
  "java_version": "25.0.2",
  "maven_version": "3.9.4",
  "os": "linux-x86_64",
  "size_bytes": 524288,
  "compression": "gzip",
  "compression_ratio": 0.45,
  "source": "github-actions-runner-1",
  "ttl_expires": "2026-05-29T14:32:15Z"
}
```

---

## Backend Selection & Setup

### Option: AWS S3

**Recommended for**: Teams with AWS infrastructure, medium-to-large scale

**Setup steps**:

```bash
# 1. Create S3 bucket
aws s3api create-bucket \
  --bucket yawl-build-cache-ACCOUNT-ID \
  --region us-east-1

# 2. Enable versioning (optional, for rollback)
aws s3api put-bucket-versioning \
  --bucket yawl-build-cache-ACCOUNT-ID \
  --versioning-configuration Status=Enabled

# 3. Enable encryption at rest (AES256)
aws s3api put-bucket-encryption \
  --bucket yawl-build-cache-ACCOUNT-ID \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# 4. Configure lifecycle policy (90 day TTL + Glacier archive)
aws s3api put-bucket-lifecycle-configuration \
  --bucket yawl-build-cache-ACCOUNT-ID \
  --lifecycle-configuration file://s3-lifecycle.json

# 5. Block public access (security)
aws s3api put-public-access-block \
  --bucket yawl-build-cache-ACCOUNT-ID \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# 6. Verify connectivity
aws s3 ls s3://yawl-build-cache-ACCOUNT-ID/
```

**Lifecycle configuration** (`s3-lifecycle.json`):

```json
{
  "Rules": [
    {
      "ID": "ExpireOldCacheEntries",
      "Filter": { "Prefix": "" },
      "Expiration": { "Days": 90 },
      "Status": "Enabled"
    },
    {
      "ID": "ArchiveToGlacier",
      "Filter": { "Prefix": "" },
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "GLACIER"
        }
      ],
      "Status": "Enabled"
    }
  ]
}
```

### Option: MinIO Self-Hosted

**Recommended for**: Offline-first environments, cost-conscious teams, on-premises only

**Setup steps**:

```bash
# 1. Install MinIO (Docker)
docker run -d \
  -p 9000:9000 \
  -p 9001:9001 \
  -v /data/minio:/data \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=$(openssl rand -base64 32) \
  minio/minio:latest \
  server /data --console-address ":9001"

# 2. Access MinIO console
# http://localhost:9001
# Username: minioadmin
# Password: (from above)

# 3. Create bucket via CLI
mc alias set minio http://localhost:9000 minioadmin MINIOADMIN_PASSWORD
mc mb minio/yawl-build-cache

# 4. Enable versioning
mc version enable minio/yawl-build-cache

# 5. Test upload/download
echo "test" | mc pipe minio/yawl-build-cache/test.txt
mc cat minio/yawl-build-cache/test.txt
```

### Cost Comparison

| Provider | Setup | Monthly | Annual | Notes |
|----------|-------|---------|--------|-------|
| **AWS S3** | Free | $1-5 | $12-60 | Easiest, pay-as-you-go |
| **MinIO (self-hosted)** | $200-500 hardware | $10-20 | $130-240 | No recurring costs, upfront investment |
| **DigitalOcean Spaces** | Free | $5-15 | $60-180 | Simpler than AWS, lower cost |
| **Backblaze B2** | Free | $2-8 | $24-96 | Very cheap storage, higher egress |

**Recommendation**: Start with AWS S3 (lowest complexity), switch to MinIO if volume grows.

---

## Cache Protocol

### Cache Key Format

```
cache_key = {module_name}/{content_hash}.tar.gz

Examples:
- s3://yawl-build-cache/yawl-elements/a1b2c3d4e5f6g7h8.tar.gz
- s3://yawl-build-cache/yawl-engine/i9j8k7l6m5n4o3p2.tar.gz
- file:///minio/yawl-build-cache/yawl-engine/i9j8k7l6m5n4o3p2.tar.gz
```

### Content Hash Calculation

```bash
# Hash = SHA256({pom.xml + all source files + dependencies.xml})
# Algorithm:
# 1. List all input files (pom.xml, src/main/**, src/test/**)
# 2. Sort by path
# 3. Calculate SHA256 of concatenated file hashes
# 4. Truncate to 16 chars for cache key

example_hash() {
  local module_dir="$1"
  find "$module_dir" \
    -type f \
    \( -name "pom.xml" -o -path "*/src/*" -o -name "dependencies.txt" \) \
    -exec sha256sum {} \; | \
    sort -k2 | \
    sha256sum | \
    cut -c1-16
}
```

### Cache Artifact Format

**Tarball contents**:

```
{module}-cache.tar.gz
├─ pom.xml                       [module POM]
├─ target/classes/               [compiled bytecode]
├─ target/test-classes/          [test bytecode]
├─ target/dependency/            [resolved dependencies]
└─ target/classes.cds            [CDS archive, if available]
```

**Compression**: gzip (40-50% reduction typical)

**Size per module**: 50-500 MB (varies)

```
yawl-utilities:  ~50 MB
yawl-elements:   ~150 MB
yawl-engine:     ~300 MB
yawl-stateless:  ~200 MB
```

### Upload Protocol

**Trigger**: After successful build completion

**Flow**:

```
Build Completion
    ↓
Create cache artifact (tar + gzip)
    ↓
Calculate SHA256 checksum
    ↓
Write metadata.json
    ↓
Upload to S3 in parallel (8 concurrent streams)
    ├─ artifact.tar.gz         [PUT request]
    ├─ artifact.tar.gz.sha256   [PUT request]
    └─ metadata.json            [PUT request]
    ↓
Retry logic: 3 attempts with exponential backoff (1s, 2s, 4s)
    ↓
[SUCCESS] → Log upload time, increment metrics.uploads.success
[FAILURE] → Log error, don't fail build, increment metrics.uploads.failed
```

**Implementation (bash)**:

```bash
upload_cache_artifact() {
  local module="$1"
  local hash="$2"
  local artifact="$3"  # path to .tar.gz
  local s3_bucket="${YAWL_S3_BUCKET:-yawl-build-cache}"
  local s3_endpoint="${YAWL_S3_ENDPOINT:-s3.amazonaws.com}"

  local s3_path="s3://${s3_bucket}/${module}/${hash}.tar.gz"

  echo "[CACHE] Uploading ${module} to S3..."

  # Calculate checksum
  local checksum=$(sha256sum "$artifact" | cut -d' ' -f1)

  # Retry logic
  local max_retries=3
  local backoff=1
  for attempt in $(seq 1 $max_retries); do
    if aws s3 cp "$artifact" "$s3_path" \
        --region "${YAWL_S3_REGION:-us-east-1}" \
        --sse AES256 \
        --quiet; then

      # Upload checksum file
      echo "$checksum" | aws s3 cp - "$s3_path.sha256" --quiet

      # Upload metadata
      aws s3 cp /dev/stdin "$s3_path.metadata.json" \
        --content-type application/json <<EOF
{
  "module": "$module",
  "hash": "$hash",
  "timestamp": "$(date -Iseconds)",
  "java_version": "$(java -version 2>&1 | grep version | cut -d'"' -f2)",
  "size_bytes": $(stat -c%s "$artifact"),
  "checksum": "$checksum"
}
EOF

      echo "[CACHE] ✓ Upload successful (${artifact##*/})"
      return 0
    fi

    if [[ $attempt -lt $max_retries ]]; then
      echo "[CACHE] ⚠ Retry ${attempt}/${max_retries} after ${backoff}s..."
      sleep $backoff
      backoff=$((backoff * 2))
    fi
  done

  echo "[CACHE] ✗ Upload failed after ${max_retries} attempts (continuing build)"
  return 1
}
```

### Download Protocol

**Trigger**: Local cache miss, before full build

**Flow**:

```
Check local cache
    ↓ [MISS]
Check remote S3
    ↓
[Connection test: 1s timeout]
    ├─ [SUCCESS] → Proceed to download
    ├─ [TIMEOUT/FAIL] → Fallback to offline, skip S3
    ↓
Download artifact with retry (3 attempts, 5s timeout)
    ├─ S3 → Local file
    ├─ Verify SHA256 checksum
    │   ├─ [MATCH] → Extract .tar.gz to target/
    │   └─ [MISMATCH] → Discard, retry download
    ├─ Increment metrics.downloads.success
    └─ [DOWNLOAD TIMEOUT] → Start full build in parallel
         (cache may arrive while building)
```

**Implementation (bash)**:

```bash
download_cache_artifact() {
  local module="$1"
  local hash="$2"
  local output_file="$3"  # where to extract
  local s3_bucket="${YAWL_S3_BUCKET:-yawl-build-cache}"

  local s3_path="s3://${s3_bucket}/${module}/${hash}.tar.gz"
  local temp_file="/tmp/cache-${module}-${hash}.tar.gz"

  echo "[CACHE] Downloading ${module} from S3..."

  # Quick connectivity check (1s timeout)
  if ! timeout 1 aws s3 ls "$s3_path" \
      --region "${YAWL_S3_REGION:-us-east-1}" \
      >/dev/null 2>&1; then
    echo "[CACHE] ⚠ S3 unreachable, using local cache only"
    return 1
  fi

  # Download with timeout (10s)
  if ! timeout 10 aws s3 cp "$s3_path" "$temp_file" \
      --region "${YAWL_S3_REGION:-us-east-1}" \
      --quiet; then
    echo "[CACHE] ✗ Download timeout (>10s), starting local build"
    return 1
  fi

  # Verify checksum
  local expected_checksum=$(aws s3 cp "$s3_path.sha256" - 2>/dev/null)
  local actual_checksum=$(sha256sum "$temp_file" | cut -d' ' -f1)

  if [[ "$expected_checksum" != "$actual_checksum" ]]; then
    echo "[CACHE] ✗ Checksum mismatch, discarding artifact"
    rm -f "$temp_file"
    return 1
  fi

  # Extract to target directory
  mkdir -p "$output_file"
  if tar -xzf "$temp_file" -C "$output_file"; then
    echo "[CACHE] ✓ Download + extract successful"
    rm -f "$temp_file"
    return 0
  else
    echo "[CACHE] ✗ Extract failed"
    rm -f "$temp_file"
    return 1
  fi
}
```

---

## Integration Points

### 1. Maven Build Cache Configuration

**File**: `.mvn/maven-build-cache-remote-config.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<cache xmlns="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://maven.apache.org/BUILD-CACHE-CONFIG/1.0.0
                           https://maven.apache.org/xsd/build-cache-config-1.0.0.xsd">

  <!-- Local cache (per-machine) -->
  <local>
    <maxBuildsCached>50</maxBuildsCached>
    <retentionPeriod>P30D</retentionPeriod>
    <maxSize>10GB</maxSize>
  </local>

  <!-- Remote cache (S3 or MinIO) -->
  <remote enabled="true" id="yawl-s3-cache">
    <!-- S3 endpoint (or MinIO server) -->
    <url>${yawl.s3.endpoint}</url>

    <!-- S3 bucket -->
    <bucket>${yawl.s3.bucket}</bucket>

    <!-- AWS region (ignored for MinIO) -->
    <region>${yawl.s3.region:-us-east-1}</region>

    <!-- HTTP transport config -->
    <transport>http</transport>
    <timeout>30000</timeout>

    <!-- AWS S3 credentials (or MinIO user/password) -->
    <credentials>
      <username>${aws.access.key.id}</username>
      <password>${aws.secret.access.key}</password>
    </credentials>

    <!-- Retry policy -->
    <retry>
      <maxAttempts>3</maxAttempts>
      <backoffBase>1000</backoffBase>
    </retry>

    <!-- Upload config -->
    <upload>
      <enabled>true</enabled>
      <compression>GZIP</compression>
      <threads>8</threads>
      <timeout>30000</timeout>
    </upload>
  </remote>

  <!-- Fallback policy: local miss → S3 miss → full build -->
  <fallbackPolicy>
    <localMiss>REMOTE</localMiss>
    <remoteMiss>BUILD</remoteMiss>
  </fallbackPolicy>

  <!-- Input tracking -->
  <input>
    <global>
      <glob>{*.java,*.xml,*.properties,*.yaml,*.yml}</glob>
      <glob exclude="true">{target/**,.git/**,*.log,*.tmp,*.swp,*~}</glob>
    </global>
  </input>

  <!-- Execution control -->
  <executionControl>
    <runAlways>
      <goalLists>
        <goalsList artifactId="maven-clean-plugin">
          <goals>clean</goals>
        </goalsList>
        <goalsList artifactId="maven-deploy-plugin">
          <goals>deploy</goals>
        </goalsList>
      </goalLists>
    </runAlways>
  </executionControl>

</cache>
```

### 2. Build Script Integration (dx.sh)

**New environment variables**:

```bash
# Enable/disable remote cache
export YAWL_REMOTE_CACHE=${YAWL_REMOTE_CACHE:-1}

# S3 configuration
export YAWL_S3_ENDPOINT="${YAWL_S3_ENDPOINT:-s3.amazonaws.com}"
export YAWL_S3_BUCKET="${YAWL_S3_BUCKET:-yawl-build-cache}"
export YAWL_S3_REGION="${YAWL_S3_REGION:-us-east-1}"

# Timeouts
export YAWL_REMOTE_CACHE_TIMEOUT="${YAWL_REMOTE_CACHE_TIMEOUT:-30}"
export YAWL_REMOTE_CACHE_DOWNLOAD_TIMEOUT="${YAWL_REMOTE_CACHE_DOWNLOAD_TIMEOUT:-10}"
```

**Usage**:

```bash
# Enable remote cache
bash scripts/dx.sh --remote-cache compile

# Disable remote cache (troubleshooting)
bash scripts/dx.sh --no-remote-cache compile

# With statistics
bash scripts/dx.sh --remote-cache --stats all

# Override S3 endpoint (for MinIO)
YAWL_S3_ENDPOINT="http://minio:9000" bash scripts/dx.sh --remote-cache all
```

### 3. CI/CD Integration (GitHub Actions)

**File**: `.github/workflows/remote-cache-sync.yml`

```yaml
name: Build + Remote Cache Sync

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build-with-cache:
    runs-on: ubuntu-latest

    permissions:
      id-token: write  # OIDC for AWS STS assume-role
      contents: read

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS Credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          role-session-name: yawl-ci-cache
          aws-region: us-east-1

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build with remote cache
        env:
          YAWL_REMOTE_CACHE: '1'
          YAWL_S3_BUCKET: ${{ secrets.S3_BUCKET_NAME }}
        run: |
          bash scripts/dx.sh --remote-cache all

      - name: Sync metrics to S3
        if: always()
        run: |
          bash scripts/remote-cache-management.sh metrics --upload

      - name: Report cache statistics
        if: always()
        run: |
          bash scripts/remote-cache-management.sh stats
```

**GitHub Secrets to set**:

```
AWS_ROLE_ARN: arn:aws:iam::ACCOUNT:role/yawl-ci-cache
S3_BUCKET_NAME: yawl-build-cache-ACCOUNT-ID
```

---

## Fallback & Error Handling

### Offline Mode

When remote cache is unavailable:

```
[Network unavailable]
    ↓
[Local cache check] → Use local if available
    ↓
[Local miss] → Full build (warn user)
```

**Implementation**:

```bash
# Detect offline quickly
if ! timeout 1 aws s3 ls s3://${YAWL_S3_BUCKET}/ >/dev/null 2>&1; then
  echo "[CACHE] ⚠ Remote cache offline, using local only"
  export YAWL_REMOTE_CACHE=0
fi
```

### Degraded Performance

When S3 is slow (>5s for download):

```
[Download started]
    ↓ [5s elapsed with no response]
[Start local build in background]
    ↓
[Whichever completes first wins]
    ├─ S3 finishes first → Use S3 result
    └─ Local build first → Use local result
```

### Corrupted Artifacts

If checksum fails:

```
[SHA256 mismatch]
    ↓
[Delete corrupted artifact]
    ↓
[Retry download (up to 2 more times)]
    ↓
[Still fails → Start full build]
```

### S3 Errors

| Error | Handling |
|-------|----------|
| 403 Forbidden | Check credentials, rotate keys if needed |
| 404 Not Found | Expected (cache miss), proceed to build |
| 500 Server Error | Retry with backoff (1s, 2s, 4s) |
| Timeout | Fall back to local build, don't block |
| Network partition | Use local cache only |

---

## Operations & Maintenance

### Manual Cache Management

**File**: `scripts/remote-cache-management.sh`

```bash
# List all cached modules
bash scripts/remote-cache-management.sh list

# Clear remote cache (dangerous!)
bash scripts/remote-cache-management.sh clean --remote

# Clear local cache
bash scripts/remote-cache-management.sh clean --local

# View cache statistics
bash scripts/remote-cache-management.sh stats

# Upload metrics to S3
bash scripts/remote-cache-management.sh metrics --upload

# Check S3 connectivity
bash scripts/remote-cache-management.sh health

# Prune cache (delete entries >90 days old)
bash scripts/remote-cache-management.sh prune --remote --days=90
```

### Monitoring Metrics

**File**: `.yawl/metrics/remote-cache-stats.json`

Updated after each build:

```json
{
  "timestamp": "2026-02-28T14:30:00Z",
  "period_days": 7,
  "builds_total": 225,
  "cache_hits_local": 150,
  "cache_hits_remote": 45,
  "cache_misses": 30,
  "hit_rate_total_pct": 86.7,
  "hit_rate_local_pct": 66.7,
  "hit_rate_remote_pct": 20.0,
  "download_time_avg_sec": 4.2,
  "download_time_min_sec": 0.8,
  "download_time_max_sec": 9.5,
  "upload_time_avg_sec": 6.8,
  "upload_time_min_sec": 2.1,
  "upload_time_max_sec": 15.3,
  "cold_start_time_avg_sec": 35.2,
  "s3_storage_gb": 2.3,
  "s3_transfer_out_gb": 45.0,
  "s3_transfer_in_gb": 12.0,
  "s3_cost_estimated_usd": 0.42,
  "errors": {
    "upload_failures": 2,
    "download_failures": 1,
    "api_errors": 0,
    "checksum_mismatches": 0
  },
  "modules": {
    "yawl-elements": {
      "hits_local": 42,
      "hits_remote": 15,
      "misses": 8,
      "size_mb": 150
    },
    "yawl-engine": {
      "hits_local": 38,
      "hits_remote": 18,
      "misses": 12,
      "size_mb": 280
    }
  }
}
```

### Credential Rotation

```bash
# Rotate AWS access keys every 90 days
aws iam create-access-key --user-name yawl-ci-cache

# Delete old key
aws iam delete-access-key --user-name yawl-ci-cache --access-key-id AKIA...

# Update GitHub Secrets
gh secret set AWS_ACCESS_KEY_ID --body "AKIA..."
gh secret set AWS_SECRET_ACCESS_KEY --body "..."

# For OIDC (preferred, no rotation needed)
# Just use AWS STS assume-role with time-limited credentials
```

### Troubleshooting

**High download latency (>10s)**:
- Check S3 region (use closest region)
- Check network connectivity
- Try MinIO local instance as fallback

**Low cache hit rate (<30%)**:
- Verify cache key algorithm is stable
- Check if dependencies changed frequently
- Consider broader cache invalidation strategy

**Growing cost (>$20/month)**:
- Reduce retention period (currently 90d)
- Archive to Glacier more aggressively
- Consider switching to MinIO self-hosted

**Corrupted artifacts**:
- Check S3 encryption settings
- Verify SHA256 checksums
- Re-upload clean artifacts

---

## Testing & Validation

### Unit Test Checklist

- [ ] `test_cache_key_calculation()` - Verify hash algorithm
- [ ] `test_cache_hit_local()` - Verify local cache lookup
- [ ] `test_cache_miss_remote()` - Verify S3 download fallback
- [ ] `test_cache_upload_retry()` - Verify retry logic
- [ ] `test_checksum_validation()` - Verify integrity checks
- [ ] `test_offline_mode()` - Verify graceful degradation
- [ ] `test_concurrent_uploads()` - Verify parallel efficiency

### Integration Test Checklist

- [ ] S3 connectivity (AWS and MinIO)
- [ ] End-to-end cache hit (new machine → S3 → use cache)
- [ ] End-to-end cache upload (build → compress → S3)
- [ ] Fallback on timeout (>5s → local build)
- [ ] Credential rotation (keys updated, builds succeed)
- [ ] GitHub Actions integration (OIDC + assume-role)

### Performance Test Checklist

- [ ] Cold start time <35s (vs 90s baseline)
- [ ] Download latency <5s (typical)
- [ ] Upload latency <10s (typical)
- [ ] Cache hit rate >50% (after warm-up)
- [ ] S3 cost <$10/month

---

## Deployment Checklist

- [ ] Create S3 bucket (or MinIO instance)
- [ ] Set up IAM roles/policies
- [ ] Configure Maven build-cache-remote-config.xml
- [ ] Update .mvn/maven.config with S3 endpoint
- [ ] Implement remote-cache-sync.sh script
- [ ] Update dx.sh with --remote-cache flag
- [ ] Create GitHub workflow (remote-cache-sync.yml)
- [ ] Set GitHub secrets (AWS_ROLE_ARN, S3_BUCKET_NAME)
- [ ] Document setup in DISTRIBUTED-CACHE-OPS.md
- [ ] Test on staging environment
- [ ] Rollout to production (phased approach)
- [ ] Monitor metrics (.yawl/metrics/remote-cache-stats.json)
- [ ] Gather feedback from team

---

## References

- **Architecture**: `.yawl/DISTRIBUTED-CACHE-PLAN.md`
- **Cost Analysis**: `.yawl/DISTRIBUTED-CACHE-BUDGET.md`
- **Operations**: `.yawl/DISTRIBUTED-CACHE-OPS.md`
- **Maven Documentation**: https://maven.apache.org/shared/maven-build-cache/
- **AWS S3 API**: https://docs.aws.amazon.com/s3/
- **MinIO Documentation**: https://docs.min.io/

---

**Status**: READY FOR DEVELOPMENT
**Next Phase**: Implement scripts and CI/CD integration
**Owner**: Engineer O
**Deadline**: 2026-03-10
