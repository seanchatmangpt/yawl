# YAWL Distributed Build Cache — Operations & Troubleshooting Guide

**Status**: READY FOR OPERATIONS | Phase 4, Quantum 5
**Version**: 1.0 | Date: 2026-02-28
**Owner**: DevOps / SRE Team

---

## Table of Contents

1. [Setup Instructions](#setup-instructions)
2. [Daily Operations](#daily-operations)
3. [Monitoring & Alerting](#monitoring--alerting)
4. [Troubleshooting](#troubleshooting)
5. [Maintenance](#maintenance)
6. [Runbooks](#runbooks)

---

## Setup Instructions

### Prerequisites

```bash
# Verify tools installed
which aws          # AWS CLI v2 (for S3) or MC (for MinIO)
which gzip         # Compression utility
which tar          # Archive utility
which jq           # JSON processor
java -version      # Java 25+
mvn -version       # Maven 3.9+
```

### AWS S3 Setup (Recommended)

#### 1. Create S3 Bucket

```bash
# Variables
ACCOUNT_ID="123456789012"
BUCKET_NAME="yawl-build-cache-${ACCOUNT_ID}"
REGION="us-east-1"

# Create bucket
aws s3api create-bucket \
  --bucket "$BUCKET_NAME" \
  --region "$REGION"

# Verify creation
aws s3 ls s3://"$BUCKET_NAME"
```

#### 2. Configure Bucket Settings

```bash
# Enable versioning
aws s3api put-bucket-versioning \
  --bucket "$BUCKET_NAME" \
  --versioning-configuration Status=Enabled

# Enable encryption (AES256)
aws s3api put-bucket-encryption \
  --bucket "$BUCKET_NAME" \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# Block all public access (IMPORTANT)
aws s3api put-public-access-block \
  --bucket "$BUCKET_NAME" \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# Enable Intelligent-Tiering (cost optimization)
aws s3api put-bucket-intelligent-tiering-configuration \
  --bucket "$BUCKET_NAME" \
  --id "auto-archive" \
  --intelligent-tiering-configuration '{
    "Id": "auto-archive",
    "Filter": {"Prefix": ""},
    "Status": "Enabled",
    "Tierings": [
      {
        "Days": 30,
        "AccessTier": "ARCHIVE_ACCESS"
      },
      {
        "Days": 90,
        "AccessTier": "DEEP_ARCHIVE_ACCESS"
      }
    ]
  }'
```

#### 3. Create Lifecycle Policy

```bash
cat > /tmp/s3-lifecycle.json <<'EOF'
{
  "Rules": [
    {
      "ID": "DeleteOldCacheEntries",
      "Filter": {"Prefix": ""},
      "Expiration": {"Days": 90},
      "Status": "Enabled"
    }
  ]
}
EOF

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$BUCKET_NAME" \
  --lifecycle-configuration file:///tmp/s3-lifecycle.json
```

#### 4. Create IAM Roles

**Developer Read-Only Role**:

```bash
# Create policy
cat > /tmp/yawl-dev-cache-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListBucket",
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::yawl-build-cache-*"
    },
    {
      "Sid": "GetCacheArtifacts",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:GetObjectVersion"],
      "Resource": "arn:aws:s3:::yawl-build-cache-*/*"
    }
  ]
}
EOF

aws iam create-policy \
  --policy-name yawl-developers-cache-read \
  --policy-document file:///tmp/yawl-dev-cache-policy.json

# Attach to group
aws iam add-group-policy \
  --group-name yawl-developers \
  --policy-name yawl-developers-cache-read
```

**CI/CD Read-Write Role**:

```bash
# Create policy
cat > /tmp/yawl-ci-cache-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListBucket",
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::yawl-build-cache-*"
    },
    {
      "Sid": "ReadWriteCacheArtifacts",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::yawl-build-cache-*/*"
    }
  ]
}
EOF

# Create service role for GitHub Actions (OIDC)
cat > /tmp/github-oidc-trust-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:yawlfoundation/yawl:*"
        }
      }
    }
  ]
}
EOF

aws iam create-role \
  --role-name yawl-github-actions-cache \
  --assume-role-policy-document file:///tmp/github-oidc-trust-policy.json

aws iam put-role-policy \
  --role-name yawl-github-actions-cache \
  --policy-name yawl-cache-access \
  --policy-document file:///tmp/yawl-ci-cache-policy.json
```

#### 5. Set GitHub Secrets

```bash
# Get role ARN
ROLE_ARN=$(aws iam get-role --role-name yawl-github-actions-cache --query 'Role.Arn' --output text)

# Set secrets
gh secret set AWS_ROLE_ARN --body "$ROLE_ARN"
gh secret set S3_BUCKET_NAME --body "yawl-build-cache-${ACCOUNT_ID}"
gh secret set AWS_REGION --body "us-east-1"
```

### MinIO Setup (Alternative)

```bash
# 1. Start MinIO in Docker
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

# 3. Create bucket via CLI
mc alias set minio http://localhost:9000 minioadmin MINIOADMIN_PASSWORD
mc mb minio/yawl-build-cache

# 4. Create service account for CI/CD
mc admin user add minio yawl-ci-service $(openssl rand -base64 32)

# 5. Set policy
mc admin policy attach minio consoleAdmin --user=yawl-ci-service
```

---

## Daily Operations

### Developers

#### Check Cache Status

```bash
# View local cache stats
bash scripts/remote-cache-management.sh stats

# Expected output:
# ✓ Local cache size: 2.3 GB / 10 GB max
# ✓ Remote cache hits: 45 (20.0% of all hits)
# ✓ Download latency: 4.2s average
```

#### Force Cache Clear (if corrupted)

```bash
# Clear local cache only (safe)
bash scripts/remote-cache-management.sh clean --local

# Clear specific module cache
bash scripts/remote-cache-management.sh clean --module yawl-engine

# DO NOT clear remote cache (affects all developers)
```

#### Disable Remote Cache (if having issues)

```bash
# Temporary disable
export YAWL_REMOTE_CACHE=0
bash scripts/dx.sh all

# Or via flag
bash scripts/dx.sh --no-remote-cache all
```

### CI/CD Runners

#### Verify Cache Sync

```bash
# Check if metrics uploaded to S3
aws s3 ls s3://yawl-build-cache/metrics/ --recursive

# View latest metrics
aws s3 cp s3://yawl-build-cache/.metadata/latest-metrics.json - | jq .
```

#### Manual Cache Upload (if needed)

```bash
# Upload build artifacts to S3
bash scripts/remote-cache-management.sh sync --upload

# Upload with specific module
bash scripts/remote-cache-management.sh sync --upload --module yawl-elements
```

---

## Monitoring & Alerting

### Real-Time Metrics

**Location**: `.yawl/metrics/remote-cache-stats.json`

**Update frequency**: After each build (appended to JSONL log)

**Key metrics to watch**:

```json
{
  "timestamp": "2026-02-28T14:30:00Z",
  "cache_hit_rate_pct": 86.7,
  "hit_rate_local_pct": 66.7,
  "hit_rate_remote_pct": 20.0,
  "download_time_avg_sec": 4.2,
  "cold_start_time_avg_sec": 35.2,
  "s3_storage_gb": 2.3,
  "s3_cost_estimated_usd": 0.42,
  "errors": {
    "upload_failures": 2,
    "download_failures": 1
  }
}
```

### Dashboard Setup (Optional)

**GitHub Pages Dashboard**:

```bash
# Generate dashboard HTML
bash scripts/remote-cache-management.sh dashboard > /tmp/dashboard.html

# View in browser
open /tmp/dashboard.html
```

### Alerting Rules

#### Alert: Low Cache Hit Rate

**Threshold**: <50%
**Condition**: Rolling 7-day average < 50%
**Action**: Investigate cache invalidation

```bash
# Check if dependencies changed
grep -r "SNAPSHOT" pom.xml | wc -l

# Verify cache key algorithm hasn't drifted
bash scripts/remote-cache-management.sh validate-keys
```

#### Alert: High Upload Failures

**Threshold**: >5% of builds
**Condition**: Upload failures / total uploads > 5%
**Action**: Check S3 credentials, bucket policy

```bash
# Verify S3 access
aws s3 ls s3://yawl-build-cache/

# Check credentials expiry
aws sts get-caller-identity

# Rotate credentials if needed
bash scripts/remote-cache-management.sh rotate-credentials
```

#### Alert: Storage Growth

**Threshold**: >5 GB
**Condition**: Storage exceeding 5 GB
**Action**: Trigger cleanup, review retention policy

```bash
# Check what's taking space
aws s3api list-objects-v2 \
  --bucket yawl-build-cache \
  --query 'Contents[].{Key:Key,Size:Size}' | \
  jq 'sort_by(.Size) | reverse | .[0:10]'

# Prune old artifacts (>90 days)
bash scripts/remote-cache-management.sh prune --days=90
```

#### Alert: Cost Overrun

**Threshold**: >$20/month
**Condition**: Estimated monthly cost > $20
**Action**: Review usage, adjust strategy

```bash
# Check current month cost
aws ce get-cost-and-usage \
  --time-period Start=$(date -d 'first day of this month' +%Y-%m-%d),End=$(date +%Y-%m-%d) \
  --granularity MONTHLY \
  --filter file://s3-service-filter.json \
  --metrics "UnblendedCost" --group-by Type=DIMENSION,Key=SERVICE
```

---

## Troubleshooting

### Issue: "Remote cache unavailable"

**Symptoms**:
- Build logs show `⚠ Remote cache offline`
- Downloads failing with timeout errors

**Diagnosis**:

```bash
# Check S3 connectivity
aws s3 ls s3://yawl-build-cache/ --region us-east-1

# Check DNS resolution
nslookup s3.amazonaws.com

# Check network connectivity
curl -I https://s3.amazonaws.com/
```

**Resolution**:

```bash
# Fallback to local cache
export YAWL_REMOTE_CACHE=0

# Or wait for network to recover
# S3 is designed to fail gracefully (local build proceeds)
```

### Issue: "Checksum mismatch"

**Symptoms**:
- Build logs show `✗ Checksum mismatch, discarding artifact`
- Cache hit rate drops suddenly

**Diagnosis**:

```bash
# Check S3 object integrity
aws s3api head-object --bucket yawl-build-cache --key yawl-elements/a1b2c3d4.tar.gz

# Verify local checksum
sha256sum ~/.m2/build-cache/yawl/yawl-elements/a1b2c3d4.tar.gz
```

**Resolution**:

```bash
# Delete corrupted artifact from S3
aws s3 rm s3://yawl-build-cache/yawl-elements/a1b2c3d4.tar.gz

# Clear local cache
bash scripts/remote-cache-management.sh clean --local

# Re-run build (will generate fresh cache)
bash scripts/dx.sh all
```

### Issue: "Slow downloads (>10s)"

**Symptoms**:
- Build logs show download latency >10s
- Developers report slow builds

**Diagnosis**:

```bash
# Check S3 latency
time aws s3 cp s3://yawl-build-cache/yawl-elements/a1b2c3d4.tar.gz /tmp/test.tar.gz

# Check network latency to S3
ping s3.amazonaws.com

# Check regional endpoint
aws ec2 describe-regions --query 'Regions[].RegionName' --output text
```

**Resolution**:

```bash
# Use closer regional endpoint
export YAWL_S3_ENDPOINT="s3.us-west-2.amazonaws.com"
bash scripts/dx.sh all

# Or use S3 transfer acceleration (if high-frequency)
aws s3api put-bucket-accelerate-configuration \
  --bucket yawl-build-cache \
  --accelerate-configuration Status=Enabled

# Then use s3-accelerate endpoint
export YAWL_S3_ENDPOINT="yawl-build-cache.s3-accelerate.amazonaws.com"
```

### Issue: "Credentials expired"

**Symptoms**:
- CI/CD failures with `InvalidAccessKeyId` error
- Developers can access local cache but not remote

**Diagnosis**:

```bash
# Check credential expiry
aws sts get-caller-identity --query 'UserId' --output text

# Check if key is still valid
aws iam get-access-key-last-used --access-key-id AKIA...
```

**Resolution**:

```bash
# For developers (obtain new temporary credentials)
aws sts assume-role \
  --role-arn arn:aws:iam::ACCOUNT:role/yawl-developers \
  --role-session-name yawl-dev

# For CI/CD (rotate service account key)
aws iam create-access-key --user-name yawl-ci-service
aws iam delete-access-key --user-name yawl-ci-service --access-key-id AKIA_OLD

# Update GitHub Secrets
gh secret set AWS_ACCESS_KEY_ID --body "AKIA_NEW"
gh secret set AWS_SECRET_ACCESS_KEY --body "..."
```

### Issue: "Out of disk space"

**Symptoms**:
- Build fails with "no space left on device"
- Local cache directory >10 GB

**Diagnosis**:

```bash
# Check disk usage
du -sh .yawl/cache/

# List largest artifacts
find .yawl/cache/ -type f -exec du -h {} \; | sort -rh | head -10
```

**Resolution**:

```bash
# Clear old cache entries
bash scripts/remote-cache-management.sh clean --local

# Or reduce retention period
export YAWL_CACHE_RETENTION_DAYS=7
bash scripts/remote-cache-management.sh clean --local --retention-days=$YAWL_CACHE_RETENTION_DAYS

# Or increase available disk space
# (coordinate with DevOps for workstation storage upgrade)
```

---

## Maintenance

### Weekly Maintenance

```bash
# Monday morning: Check health
bash scripts/remote-cache-management.sh health

# Review metrics
aws s3 cp s3://yawl-build-cache/.metadata/latest-metrics.json - | jq .

# Check for errors
grep "ERROR\|WARN" ~/.yawl/logs/cache-*.log
```

### Monthly Maintenance

```bash
# 1st of month: Prune old artifacts
bash scripts/remote-cache-management.sh prune --days=90

# Review cost
aws ce get-cost-and-usage \
  --time-period Start=$(date -d 'last month' +%Y-%m-01),End=$(date +%Y-%m-01) \
  --granularity MONTHLY \
  --metrics "UnblendedCost"

# Rotate credentials
bash scripts/remote-cache-management.sh rotate-credentials
```

### Quarterly Maintenance

```bash
# Review retention policy
aws s3api get-bucket-lifecycle-configuration --bucket yawl-build-cache

# Update documentation
# (if changes to S3 service or pricing)

# Audit bucket access
aws s3api get-bucket-logging --bucket yawl-build-cache

# Review performance
bash scripts/remote-cache-management.sh report --period=3months
```

### Annual Maintenance

```bash
# Review cost vs budget
echo "Year-to-date cost: $(...)"

# Plan for next year
# - Update cost estimates
# - Plan for team growth
# - Consider provider changes

# Archive metrics for audit trail
tar czf /archive/yawl-cache-metrics-2026.tar.gz .yawl/metrics/
```

---

## Runbooks

### Emergency: S3 Bucket Corrupted

**Symptoms**: Multiple checksum failures, widespread cache misses

**Resolution** (5-10 minutes):

1. **Disable remote cache**:
   ```bash
   export YAWL_REMOTE_CACHE=0
   ```

2. **Clear local caches**:
   ```bash
   bash scripts/remote-cache-management.sh clean --local
   ```

3. **Contact AWS Support** if bucket integrity suspected

4. **Rebuild all artifacts**:
   ```bash
   bash scripts/dx.sh clean
   bash scripts/dx.sh all
   ```

5. **Re-enable when ready**:
   ```bash
   export YAWL_REMOTE_CACHE=1
   ```

### Emergency: Runaway Storage Growth

**Symptoms**: S3 cost >$50/month, storage >10 GB

**Resolution** (15-30 minutes):

1. **Identify cause**:
   ```bash
   aws s3api list-objects-v2 --bucket yawl-build-cache \
     --query 'Contents[?Size > `100000000`]'
   ```

2. **Delete suspect objects**:
   ```bash
   aws s3 rm s3://yawl-build-cache/suspicious-module/ --recursive
   ```

3. **Restore from archive** (if using Glacier):
   ```bash
   aws s3api restore-object --bucket yawl-build-cache \
     --key archived-module/artifact.tar.gz
   ```

4. **Tighten lifecycle policy**:
   ```bash
   # Update to 30-day retention instead of 90
   aws s3api put-bucket-lifecycle-configuration ...
   ```

### Emergency: Credential Leak

**Symptoms**: AWS keys accidentally committed to git

**Resolution** (immediately):

1. **Rotate credentials** (invalidate old key):
   ```bash
   aws iam delete-access-key --access-key-id AKIA_LEAKED
   ```

2. **Create new key**:
   ```bash
   aws iam create-access-key --user-name yawl-ci-service
   ```

3. **Update secrets everywhere**:
   ```bash
   gh secret set AWS_ACCESS_KEY_ID --body "AKIA_NEW"
   ```

4. **Scan git history**:
   ```bash
   git log --all --oneline | grep -i aws
   ```

5. **Check CloudTrail** for unauthorized access

---

## Support & Escalation

**Contact**: DevOps Team (devops@yawlfoundation.org)

**Response Times**:
- P1 (service down): 15 minutes
- P2 (degraded): 1 hour
- P3 (inconvenience): 4 hours

---

**Version**: 1.0
**Last Updated**: 2026-02-28
**Owner**: DevOps / SRE
