# YAWL Test Result Caching System

## Overview

The test result caching system provides a 30%+ reduction in build times for warm builds by storing and reusing test results when source code, dependencies, and test configurations remain unchanged.

**Cache Location**: `.yawl/cache/test-results/`
**TTL**: 24 hours (configurable)
**Max Cache Size**: 5GB (configurable)
**Per-Module Limit**: 50 entries (configurable)

## Architecture

### Cache Entry Structure

Each cached test result is stored as a JSON file with the following structure:

```json
{
  "code_hash": "blake3-hash-of-source-code",
  "dependency_hashes": ["hash1", "hash2", ...],
  "test_config_hash": "hash-of-junit-config",
  "timestamp": "2026-02-28T07:20:41Z",
  "test_results": {
    "passed": 42,
    "failed": 0,
    "skipped": 2,
    "duration_ms": 5320
  },
  "ttl_expires": "2026-03-01T07:20:41Z"
}
```

### Cache Key

The cache key is computed as:
```
{source_hash:16chars}-{dep_hash:16chars}-{config_hash:16chars}.json
```

This ensures that any change to source code, dependencies, or test configuration invalidates the cache.

### Cache Directory Structure

```
.yawl/cache/
├── test-results/
│   ├── yawl-engine/
│   │   ├── abc1def2ghi3-jkl4mno5pqr6-stu7vwx8yz9a.json
│   │   └── ... (up to 50 entries per module, LRU cleaned)
│   ├── yawl-utilities/
│   │   └── ...
│   └── [other modules]/
├── .gitignore           # Cache not committed to git
├── .gitkeep
└── cache-manifest.json  # (optional) Hit/miss statistics
```

## Usage

### Automatic Cache Usage (via dx.sh)

The cache is automatically used by `dx.sh` when:
1. Running tests with `bash scripts/dx.sh test`
2. Running compile-test with `bash scripts/dx.sh compile-test`
3. Using explicit module list: `bash scripts/dx.sh -pl yawl-engine`

Cache can be disabled with:
```bash
DX_CACHE=0 bash scripts/dx.sh
```

### Manual Cache Operations

Source the cache configuration:
```bash
source .mvn/cache-config.sh
```

#### Check cache status
```bash
cache_is_valid "yawl-engine"  # Returns 0 if valid, 1 if invalid
```

#### Get cached results
```bash
result=$(cache_get_result "yawl-engine")
echo "$result" | jq '.test_results'
```

#### Store test results
```bash
test_results='{"passed": 42, "failed": 0, "skipped": 2, "duration_ms": 5320}'
cache_store_result "yawl-engine" "$test_results"
```

#### View cache statistics
```bash
bash scripts/cache-cleanup.sh --stats
```

#### Clear entire cache
```bash
bash scripts/cache-cleanup.sh --clear
```

#### Show hit/miss ratio
```bash
bash scripts/cache-cleanup.sh --hitrate
```

#### Cleanup old entries
```bash
bash scripts/cache-cleanup.sh --prune 24  # Remove entries older than 24 hours
```

## Configuration

Cache behavior can be controlled via environment variables:

```bash
# Cache root directory (default: .yawl/cache)
YAWL_CACHE_ROOT=.yawl/cache bash scripts/dx.sh

# TTL in hours (default: 24)
YAWL_CACHE_TTL_HOURS=48 bash scripts/dx.sh

# Max cache size in bytes (default: 5GB = 5368709120)
YAWL_CACHE_MAX_SIZE_BYTES=10737418240 bash scripts/dx.sh  # 10GB

# Max entries per module (default: 50)
YAWL_CACHE_MAX_ENTRIES_PER_MODULE=100 bash scripts/dx.sh

# Disable cache entirely (default: enabled)
DX_CACHE=0 bash scripts/dx.sh
```

Example: Increase TTL to 48 hours and max cache to 10GB:
```bash
YAWL_CACHE_TTL_HOURS=48 YAWL_CACHE_MAX_SIZE_BYTES=10737418240 bash scripts/dx.sh
```

## Hash Computation

The cache uses the following hash strategy:

### Source Code Hash
- All `.java` files in `src/` directory
- Sorted and concatenated for deterministic hashing
- Detects changes in any source file

### Dependency Hash
- All `<dependency>` entries in `pom.xml` (module and parent)
- Parent pom dependencies
- Detects version changes in dependencies

### Test Configuration Hash
- Surefire plugin configuration from `pom.xml`
- `junit-platform.properties` settings
- Test-related settings from `.mvn/maven.config`
- Detects changes in test configuration

## Cache Lifecycle

### 1. Store Phase (after successful test run)
```
Test execution completes
  ↓
Parse Surefire reports for test counts/duration
  ↓
Compute source/dependency/config hashes
  ↓
Create cache entry with TTL (current_time + 24h)
  ↓
Store in .yawl/cache/test-results/{module}/{key}.json
  ↓
Check cache size and run LRU cleanup if needed
```

### 2. Lookup Phase (before test execution)
```
Check if explicit modules specified
  ↓
For each module:
  ├─ Compute cache key (source/dep/config hashes)
  ├─ Check if cache file exists
  ├─ Verify TTL (not expired)
  ├─ If valid: mark module as cache hit (skip tests)
  └─ If invalid: mark module as cache miss (run tests)
  ↓
Report hit rate and time savings
```

### 3. Cleanup Phase (automatic + scheduled)
```
Automatic (during cache_store_result):
  ├─ Check total cache size
  ├─ If > 5GB: run LRU cleanup
  └─ Enforce 50 entries per module

Scheduled (via cache-cleanup.sh):
  ├─ Remove expired entries (by TTL)
  ├─ Remove oldest entries if size exceeded
  └─ Keep cache ≤ 50% of max size after cleanup
```

## Performance Impact

### Expected Improvements
- **Warm builds** (no changes): 30-70% faster test execution
- **Incremental builds** (single module): 40-80% faster
- **Cold builds** (full cache miss): No impact (same as normal build)

### Example Timings
```
Cold build (no cache):
  Compile + test (yawl-engine): 25s

Warm build with 3 cache hits:
  Lookup time: 0.2s
  Cache hit savings: 12s (40% reduction)
  Total time: 13.2s
```

## Cache Invalidation Scenarios

The cache is automatically invalidated when:

1. **Source Code Changes**
   - Any `.java` file modified in the module
   - New test cases added/removed
   - Cache entry automatically recomputed

2. **Dependency Changes**
   - Version bump in any dependency
   - New dependency added to pom.xml
   - Parent pom dependency change
   - Cache key changes automatically

3. **Test Configuration Changes**
   - Surefire plugin configuration modified
   - junit-platform.properties updated
   - Test timeout or fork settings changed
   - Cache key changes automatically

4. **TTL Expiration**
   - Default: 24 hours after cache creation
   - Expired entries removed during cleanup
   - Configurable via `YAWL_CACHE_TTL_HOURS`

5. **Cache Size Limit**
   - Per-module: Keep only 50 most recent entries
   - Global: Keep cache ≤ 5GB total
   - LRU (Least Recently Used) cleanup removes oldest entries

## Troubleshooting

### Cache always misses
```bash
# Check if cache is enabled
echo $DX_CACHE  # Should be 1

# Check cache contents
bash scripts/cache-cleanup.sh --stats

# Check if hashing is working
source .mvn/cache-config.sh
compute_source_hash "yawl-engine"  # Should output hash
```

### Cache hit rate is low
```bash
# View detailed cache statistics
bash scripts/cache-cleanup.sh --stats

# Check TTL expiration
bash scripts/cache-cleanup.sh --prune 0  # Remove all expired

# Check cache key computation
source .mvn/cache-config.sh
cache_generate_key "yawl-engine"  # Should output consistent key
```

### Cache takes up too much space
```bash
# View cache size
du -sh .yawl/cache/

# Run cleanup
bash scripts/cache-cleanup.sh

# Reduce TTL to expire entries faster
YAWL_CACHE_TTL_HOURS=12 bash scripts/cache-cleanup.sh --prune 12

# Clear entire cache if needed
bash scripts/cache-cleanup.sh --clear
```

### Tests are cached but shouldn't be
```bash
# Force cache invalidation for a module
rm -rf .yawl/cache/test-results/yawl-engine/

# Or disable cache for this build
DX_CACHE=0 bash scripts/dx.sh -pl yawl-engine
```

## Integration Points

### dx.sh Integration
- Checks cache before running tests (phase 1 of build)
- Reports cache hit rate and time savings
- Stores results after successful test run (phase 3)
- Environment variables control cache behavior

### Maven/Surefire Integration
- Reads Surefire XML reports after test execution
- Parses test count and duration automatically
- No additional Maven plugin required
- Compatible with Maven 3.8+ and Maven 4.0+

### CI/CD Integration
- Cache survives between builds (persistent)
- TTL ensures stale results are discarded
- Size limits prevent unbounded growth
- Automatic cleanup runs on every cache store

## Implementation Details

### Hash Function Selection
```bash
if command -v b3sum >/dev/null 2>&1; then
    # Blake3 (64-char hex)
elif command -v sha256sum >/dev/null 2>&1; then
    # SHA256 (64-char hex)
else
    # MD5 fallback (32-char hex)
fi
```

### LRU Cleanup Algorithm
1. Find oldest entry by modification time
2. Delete entry
3. Check if size still exceeds limit
4. Repeat until cache size ≤ 50% of max

### Per-Module Limits
- Keep 50 most recent cache entries per module
- Enforced during cleanup phase
- Allows testing multiple build scenarios

## Best Practices

### For Developers
1. **Don't disable cache during active development** (unless debugging cache issues)
2. **Run `cache-cleanup.sh --stats` periodically** to monitor cache health
3. **Use `DX_TIMINGS=1 bash scripts/dx.sh`** to measure actual time savings
4. **Clear cache when switching major branches** to avoid stale entries

### For CI/CD
1. **Enable cache in CI builds** for faster test feedback
2. **Run cleanup job weekly** to maintain cache health
3. **Monitor cache hit rate** via `cache-cleanup.sh --hitrate`
4. **Set appropriate TTL** based on build frequency (24h for daily builds)

### For Repository Maintenance
1. **Cache directory is NOT committed** (see `.yawl/cache/.gitignore`)
2. **Cache is local to each environment** (separate caches on dev/CI/prod)
3. **No cache synchronization between machines** (each computes its own)
4. **Cache is ephemeral** (can be cleared without side effects)

## References

- **Configuration**: `.mvn/cache-config.sh`
- **Cleanup Script**: `scripts/cache-cleanup.sh`
- **Integration**: `scripts/dx.sh` (lines 230-297)
- **Architecture**: `.yawl/cache/`
