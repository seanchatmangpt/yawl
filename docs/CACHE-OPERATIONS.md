# Cache System Operations Guide

## Quick Reference

### Check Cache Status
```bash
# View cache statistics
bash scripts/cache-cleanup.sh --stats

# View cache hit/miss rate
bash scripts/cache-cleanup.sh --hitrate
```

### Enable/Disable Cache
```bash
# Enable cache (default)
bash scripts/dx.sh

# Disable cache for this build
DX_CACHE=0 bash scripts/dx.sh

# Disable cache for all remaining builds
export DX_CACHE=0
bash scripts/dx.sh
```

### Clear Cache
```bash
# Remove all cached entries
bash scripts/cache-cleanup.sh --clear

# Remove entries older than N hours
bash scripts/cache-cleanup.sh --prune 24
```

### Configure Cache
```bash
# Custom TTL (48 hours instead of 24)
YAWL_CACHE_TTL_HOURS=48 bash scripts/dx.sh

# Custom cache size (10GB instead of 5GB)
YAWL_CACHE_MAX_SIZE_BYTES=10737418240 bash scripts/dx.sh

# Disable automatic cleanup
YAWL_CACHE_MAX_SIZE_BYTES=999999999999 bash scripts/dx.sh
```

## Daily Operations

### Monday: Full Cache Validation
```bash
echo "=== Weekly Cache Validation ==="
bash scripts/cache-cleanup.sh --stats
echo ""
echo "Hit rate:"
bash scripts/cache-cleanup.sh --hitrate
```

### Wednesday: Cache Cleanup
```bash
echo "=== Midweek Cleanup ==="
bash scripts/cache-cleanup.sh
echo ""
bash scripts/cache-cleanup.sh --stats
```

### Friday: Prune Old Entries
```bash
echo "=== Friday Prune ==="
# Remove entries older than 7 days
bash scripts/cache-cleanup.sh --prune 168
bash scripts/cache-cleanup.sh --stats
```

## Build Operations

### Standard Build (with cache)
```bash
# Build uses cache automatically
bash scripts/dx.sh -pl yawl-engine
```

### Force Fresh Build (bypass cache)
```bash
# Skip cache for this build
DX_CACHE=0 bash scripts/dx.sh -pl yawl-engine
```

### Full System Build
```bash
# Build all modules (uses cache where applicable)
bash scripts/dx.sh all
```

## Monitoring

### Watch Cache Growth
```bash
# Monitor cache size over time
watch -n 60 'du -sh .yawl/cache/test-results'

# Or capture to file
while true; do
    echo "$(date): $(du -sh .yawl/cache/test-results | awk '{print $1}')" \
        >> .yawl/cache/.size-log.txt
    sleep 3600  # Check hourly
done
```

### Track Hit Rate
```bash
# Capture hit rate periodically
while true; do
    bash scripts/cache-cleanup.sh --hitrate >> .yawl/cache/.hitrate-log.txt
    sleep 3600  # Check hourly
done
```

## Troubleshooting

### Cache Always Misses
```bash
# 1. Verify cache is enabled
echo "DX_CACHE=${DX_CACHE:-1}"

# 2. Check cache directory exists
ls -la .yawl/cache/test-results/

# 3. Verify hash functions work
source .mvn/cache-config.sh
compute_source_hash "yawl-engine"

# 4. Clear cache and rebuild
bash scripts/cache-cleanup.sh --clear
bash scripts/dx.sh -pl yawl-engine
```

### Cache is Stale
```bash
# 1. Check TTL settings
echo $YAWL_CACHE_TTL_HOURS

# 2. Remove expired entries
bash scripts/cache-cleanup.sh --prune $((YAWL_CACHE_TTL_HOURS + 1))

# 3. View remaining entries
bash scripts/cache-cleanup.sh --stats
```

### Cache Too Large
```bash
# 1. Check current size
du -sh .yawl/cache/test-results/

# 2. Run cleanup
bash scripts/cache-cleanup.sh

# 3. View statistics
bash scripts/cache-cleanup.sh --stats

# 4. If still too large, clear and rebuild
bash scripts/cache-cleanup.sh --clear
bash scripts/dx.sh all
```

## Performance Analysis

### Measure Cache Impact
```bash
# Build 1: With cache
time bash scripts/dx.sh -pl yawl-engine

# Build 2: Without cache
DX_CACHE=0 time bash scripts/dx.sh -pl yawl-engine

# Compare times
```

### Analyze Bottlenecks
```bash
# View slowest tests
DX_VERBOSE=1 bash scripts/dx.sh -pl yawl-engine 2>&1 | grep "Time elapsed" | sort
```

## Migration & Upgrades

### Upgrade Cache Config
```bash
# When cache config changes, clear and rebuild
bash scripts/cache-cleanup.sh --clear
bash scripts/dx.sh all
```

### Move Cache Location
```bash
# 1. Save old cache
cp -r .yawl/cache/test-results /backup/cache-backup-$(date +%s)

# 2. Clear and use new location
YAWL_CACHE_ROOT=/new/location bash scripts/dx.sh all

# 3. Remove old cache if desired
rm -rf .yawl/cache/test-results
```

## CI/CD Integration

### GitHub Actions
```yaml
- name: Cache Test Results
  env:
    DX_CACHE: 1
    YAWL_CACHE_TTL_HOURS: 48
  run: bash scripts/dx.sh all

- name: Cache Statistics
  if: always()
  run: bash scripts/cache-cleanup.sh --stats
```

### Jenkins Pipeline
```groovy
stage('Test with Cache') {
    environment {
        DX_CACHE = '1'
        YAWL_CACHE_TTL_HOURS = '48'
    }
    steps {
        sh 'bash scripts/dx.sh all'
        sh 'bash scripts/cache-cleanup.sh --stats'
    }
}
```

### GitLab CI
```yaml
test_with_cache:
  script:
    - DX_CACHE=1 bash scripts/dx.sh all
    - bash scripts/cache-cleanup.sh --stats
  cache:
    paths:
      - .yawl/cache/test-results/
```

## Environment Variables Reference

| Variable | Default | Notes |
|----------|---------|-------|
| `DX_CACHE` | 1 | Set to 0 to disable cache |
| `YAWL_CACHE_ROOT` | `.yawl/cache` | Cache root directory |
| `YAWL_CACHE_TTL_HOURS` | 24 | How long to keep entries |
| `YAWL_CACHE_MAX_SIZE_BYTES` | 5368709120 | 5GB cache limit |
| `YAWL_CACHE_MAX_ENTRIES_PER_MODULE` | 50 | Max entries per module |

### Set Environment Variables

```bash
# Temporary (single command)
DX_CACHE=0 bash scripts/dx.sh

# Session-wide (current shell)
export DX_CACHE=0
bash scripts/dx.sh
bash scripts/dx.sh -pl yawl-engine

# File-based (.env.local)
echo "DX_CACHE=0" > .env.local
source .env.local
bash scripts/dx.sh
```

## Logs & Debugging

### Enable Debug Output
```bash
# Show Maven output (normally quiet)
DX_VERBOSE=1 bash scripts/dx.sh

# Show timing metrics
DX_TIMINGS=1 bash scripts/dx.sh
```

### View Build Log
```bash
# After build, examine log
tail -50 /tmp/dx-build-log.txt

# Search for cache hits
grep "cache" /tmp/dx-build-log.txt
```

### Inspect Cache Files
```bash
# List cache entries
find .yawl/cache/test-results -name "*.json" | head -10

# View cache entry
cat .yawl/cache/test-results/yawl-engine/*.json | jq '.'

# Extract test counts
jq '.test_results' .yawl/cache/test-results/yawl-engine/*.json
```

## Best Practices

### DO ✓
- ✓ Run `cache-cleanup.sh --stats` weekly
- ✓ Use cache in local development (30%+ faster)
- ✓ Clear cache when switching major branches
- ✓ Monitor cache hit rate for optimization
- ✓ Use cache in CI/CD (cost savings)

### DON'T ✗
- ✗ Don't disable cache without good reason
- ✗ Don't store cache in git (see `.gitignore`)
- ✗ Don't manually edit cache entries (use API)
- ✗ Don't forget to cleanup old entries (use prune)
- ✗ Don't assume cache is synchronized between machines

## Support

### Report Issues
```bash
# Collect diagnostic information
echo "=== Cache Diagnostics ==="
echo "Cache size:"
du -sh .yawl/cache/test-results/
echo ""
echo "Cache stats:"
bash scripts/cache-cleanup.sh --stats
echo ""
echo "Build log (last 50 lines):"
tail -50 /tmp/dx-build-log.txt
```

### Contact
For cache system issues, refer to:
- Documentation: `docs/cache-system.md`
- Testing guide: `docs/CACHE-TESTING.md`
- Configuration: `.mvn/cache-config.sh`
