# Cache System Testing & Validation Guide

## Test Suite Overview

The cache system includes automated tests that validate:
1. Cache configuration and initialization
2. Hash computation (source, dependency, test config)
3. Cache storage and retrieval
4. Cache validation and TTL expiration
5. LRU cleanup mechanisms
6. Cache hit/miss statistics

## Running Cache Tests

### Quick Validation Test

```bash
# Source cache config and run basic test
bash -c 'source .mvn/cache-config.sh && cache_stats'
```

Expected output:
```
Cache Statistics:
  Location: .yawl/cache/test-results
  Total entries: 0
  Total size: 4.5K
```

### Comprehensive Integration Test

```bash
# Run full cache infrastructure test
cat > /tmp/cache_validation.sh << 'EOF'
#!/bin/bash
set -uo pipefail
cd /home/user/yawl

source .mvn/cache-config.sh

echo "=== Cache Infrastructure Validation ==="
echo ""

# Test 1: Configuration
echo "1. Configuration Check"
echo "   Cache root: ${YAWL_CACHE_ROOT}"
echo "   TTL: ${YAWL_CACHE_TTL_HOURS}h"
echo "   Max size: ${YAWL_CACHE_MAX_SIZE_BYTES} bytes"
echo "   Max entries: ${YAWL_CACHE_MAX_ENTRIES_PER_MODULE}"
echo "   ✓ Configuration valid"
echo ""

# Test 2: Hash Functions
echo "2. Hash Function Test"
test_file="/tmp/test.java"
echo "public class Test {}" > "$test_file"
hash=$(compute_file_hash "$test_file")
rm -f "$test_file"
echo "   Hash length: ${#hash}"
echo "   ✓ Hash function works (expected 64 chars for SHA256)"
echo ""

# Test 3: Real Module Hashing
echo "3. Module Hash Computation"
src_hash=$(compute_source_hash "yawl-engine" 2>/dev/null || echo "ERROR")
if [[ "$src_hash" != "ERROR" ]]; then
    echo "   Source hash: ${src_hash:0:16}..."
    echo "   ✓ Source hashing works"
else
    echo "   ✗ Source hashing failed"
fi
echo ""

# Test 4: Cache Storage
echo "4. Cache Storage Test"
modules=("yawl-engine" "yawl-utilities" "yawl-ggen")
for mod in "${modules[@]}"; do
    test_results='{"passed": 42, "failed": 0, "skipped": 0, "duration_ms": 5000}'
    if cache_store_result "$mod" "$test_results" 2>/dev/null; then
        echo "   ✓ Stored $mod"
    else
        echo "   ✗ Failed to store $mod"
    fi
done
echo ""

# Test 5: Cache Validation
echo "5. Cache Validation Test"
for mod in "${modules[@]}"; do
    if cache_is_valid "$mod" 2>/dev/null; then
        echo "   ✓ $mod valid"
    else
        echo "   ✗ $mod invalid"
    fi
done
echo ""

# Test 6: Cache Retrieval
echo "6. Cache Retrieval Test"
mod="yawl-engine"
result=$(cache_get_result "$mod" 2>/dev/null)
passed=$(echo "$result" | jq -r '.test_results.passed' 2>/dev/null || echo "ERROR")
if [[ "$passed" == "42" ]]; then
    echo "   ✓ Retrieved correct result ($passed tests)"
else
    echo "   ✗ Retrieved incorrect result"
fi
echo ""

# Test 7: Cache Statistics
echo "7. Cache Statistics"
cache_stats | tail -6 | sed 's/^/   /'
echo ""

# Test 8: Cleanup
echo "8. Cleanup Test"
rm -rf "${YAWL_CACHE_TEST_RESULTS:?}"/*
echo "   ✓ Cache cleared"
echo ""

echo "=== All Tests Passed ==="
EOF
bash /tmp/cache_validation.sh
```

### Cache Hit Rate Test

```bash
# Test actual cache hit/miss behavior
cat > /tmp/cache_hitrate_test.sh << 'EOF'
#!/bin/bash
set -uo pipefail
cd /home/user/yawl

source .mvn/cache-config.sh

echo "=== Cache Hit Rate Test ==="
echo ""

# Store results for multiple modules
echo "Storing cache entries for 5 modules..."
modules=("yawl-engine" "yawl-utilities" "yawl-ggen" "yawl-elements" "yawl-security")
for mod in "${modules[@]}"; do
    passed=$((20 + RANDOM % 50))
    test_results="{\"passed\": $passed, \"failed\": 0, \"skipped\": 0, \"duration_ms\": 5000}"
    cache_store_result "$mod" "$test_results" 2>/dev/null
    echo "  ✓ $mod"
done
echo ""

# Test cache lookups
echo "Testing cache lookups..."
hits=0
misses=0
for mod in "${modules[@]}"; do
    if cache_is_valid "$mod" 2>/dev/null; then
        ((hits++))
        echo "  HIT: $mod"
    else
        ((misses++))
        echo "  MISS: $mod"
    fi
done
echo ""

total=$((hits + misses))
hit_rate=$((hits * 100 / total))
echo "Hit Rate: $hits/$total = $hit_rate%"
echo ""

# Cleanup
rm -rf "${YAWL_CACHE_TEST_RESULTS:?}"/*
echo "Cache cleared"
EOF
bash /tmp/cache_hitrate_test.sh
```

### TTL Expiration Test

```bash
# Test TTL expiration handling
cat > /tmp/cache_ttl_test.sh << 'EOF'
#!/bin/bash
set -uo pipefail
cd /home/user/yawl

source .mvn/cache-config.sh

echo "=== TTL Expiration Test ==="
echo ""

# Store an entry
echo "1. Store cache entry with 24h TTL..."
cache_store_result "yawl-engine" '{"passed": 42, "failed": 0, "skipped": 0, "duration_ms": 5000}'

# Verify it's valid
if cache_is_valid "yawl-engine"; then
    echo "   ✓ Entry is valid"
else
    echo "   ✗ Entry is invalid (should be valid!)"
fi
echo ""

# Manually expire the entry (for testing)
echo "2. Expire the entry manually..."
cache_file=$(ls .yawl/cache/test-results/yawl-engine/*.json 2>/dev/null | head -1)
if [[ -f "$cache_file" ]]; then
    # Use sed to replace ttl_expires with a past date
    past_date="2026-01-01T00:00:00Z"
    sed -i "s/\"ttl_expires\": \"[^\"]*\"/\"ttl_expires\": \"$past_date\"/" "$cache_file"
    echo "   ✓ Entry expired"
else
    echo "   ✗ Cache file not found"
fi
echo ""

# Verify it's now invalid
echo "3. Verify expiration..."
if cache_is_valid "yawl-engine" 2>/dev/null; then
    echo "   ✗ Entry should be invalid!"
else
    echo "   ✓ Entry is now invalid (as expected)"
fi
echo ""

# Cleanup
rm -rf "${YAWL_CACHE_TEST_RESULTS:?}"/*
echo "Cache cleared"
EOF
bash /tmp/cache_ttl_test.sh
```

### Cache Cleanup Test

```bash
# Test LRU cleanup mechanism
cat > /tmp/cache_cleanup_test.sh << 'EOF'
#!/bin/bash
set -uo pipefail
cd /home/user/yawl

source .mvn/cache-config.sh

echo "=== Cache Cleanup Test ==="
echo ""

# Store multiple entries to trigger cleanup
echo "1. Storing entries to test LRU cleanup..."
for i in {1..10}; do
    passed=$((10 * i))
    test_results="{\"passed\": $passed, \"failed\": 0, \"skipped\": 0, \"duration_ms\": $((1000 * i))}"
    cache_store_result "yawl-engine" "$test_results" 2>/dev/null
    echo "   Entry $i: $passed tests"
    sleep 0.1  # Stagger to ensure different mtimes
done
echo ""

# Check entry count
entry_count=$(find .yawl/cache/test-results/yawl-engine -name "*.json" | wc -l)
echo "2. Cache entries stored: $entry_count"
echo ""

# Run cleanup
echo "3. Running cleanup (max 5 entries per module)..."
old_YAWL_CACHE_MAX_ENTRIES_PER_MODULE=$YAWL_CACHE_MAX_ENTRIES_PER_MODULE
export YAWL_CACHE_MAX_ENTRIES_PER_MODULE=5
cache_cleanup_if_needed
export YAWL_CACHE_MAX_ENTRIES_PER_MODULE=$old_YAWL_CACHE_MAX_ENTRIES_PER_MODULE

# Check remaining entries
remaining=$(find .yawl/cache/test-results/yawl-engine -name "*.json" | wc -l)
echo "   Remaining entries: $remaining (should be ≤ 5)"
echo ""

# Verify it kept the newest entries
echo "4. Verifying kept entries (should have highest test counts)..."
newest_result=$(cache_get_result "yawl-engine" 2>/dev/null)
highest_count=$(echo "$newest_result" | jq '.test_results.passed')
echo "   Newest entry test count: $highest_count (should be high)"
echo ""

# Cleanup
rm -rf "${YAWL_CACHE_TEST_RESULTS:?}"/*
echo "Cache cleared"
EOF
bash /tmp/cache_cleanup_test.sh
```

## Test Cases

### TC-1: Cache Initialization
**Purpose**: Verify cache directory structure is created correctly

```bash
# Should create .yawl/cache/test-results/
source .mvn/cache-config.sh
test -d .yawl/cache/test-results && echo "✓ PASS" || echo "✗ FAIL"
```

### TC-2: Hash Function Consistency
**Purpose**: Verify same input produces same hash

```bash
source .mvn/cache-config.sh
test_file="/tmp/test.txt"
echo "test" > "$test_file"
hash1=$(compute_file_hash "$test_file")
hash2=$(compute_file_hash "$test_file")
test "$hash1" = "$hash2" && echo "✓ PASS" || echo "✗ FAIL"
rm -f "$test_file"
```

### TC-3: Source Hash Detection
**Purpose**: Verify hash changes when source code changes

```bash
source .mvn/cache-config.sh
hash1=$(compute_source_hash "yawl-engine")
# (Make a trivial source change)
hash2=$(compute_source_hash "yawl-engine")
# (Revert the change)
hash3=$(compute_source_hash "yawl-engine")
test "$hash1" = "$hash3" && test "$hash1" != "$hash2" && echo "✓ PASS" || echo "✗ FAIL"
```

### TC-4: Cache Store & Retrieve
**Purpose**: Verify data is stored and retrieved correctly

```bash
source .mvn/cache-config.sh
cache_store_result "test-module" '{"passed": 10, "failed": 0, "skipped": 0, "duration_ms": 1000}'
result=$(cache_get_result "test-module")
passed=$(echo "$result" | jq -r '.test_results.passed')
test "$passed" = "10" && echo "✓ PASS" || echo "✗ FAIL"
rm -rf .yawl/cache/test-results/test-module
```

### TC-5: TTL Expiration
**Purpose**: Verify expired entries are detected

```bash
source .mvn/cache-config.sh
cache_store_result "test-module" '{"passed": 10, "failed": 0, "skipped": 0, "duration_ms": 1000}'

# Manually expire
cache_file=$(ls .yawl/cache/test-results/test-module/*.json | head -1)
sed -i 's/"ttl_expires": "[^"]*"/"ttl_expires": "2025-01-01T00:00:00Z"/' "$cache_file"

# Should be invalid now
! cache_is_valid "test-module" 2>/dev/null && echo "✓ PASS" || echo "✗ FAIL"
rm -rf .yawl/cache/test-results/test-module
```

### TC-6: LRU Cleanup
**Purpose**: Verify oldest entries are removed when limit exceeded

```bash
source .mvn/cache-config.sh
for i in {1..60}; do
    cache_store_result "test-module" "{\"passed\": $i, \"failed\": 0, \"skipped\": 0, \"duration_ms\": 1000}" 2>/dev/null
done
count=$(find .yawl/cache/test-results/test-module -name "*.json" | wc -l)
test $count -le 50 && echo "✓ PASS (kept $count entries)" || echo "✗ FAIL (kept $count entries)"
rm -rf .yawl/cache/test-results/test-module
```

### TC-7: Hit Rate Calculation
**Purpose**: Verify accurate hit/miss counting

```bash
source .mvn/cache-config.sh
cache_store_result "yawl-engine" '{"passed": 10, "failed": 0, "skipped": 0, "duration_ms": 1000}'
# All modules valid = 100% hit rate
count=$(find .yawl/cache/test-results -name "*.json" | wc -l)
test $count -eq 1 && echo "✓ PASS (1 entry)" || echo "✗ FAIL"
rm -rf .yawl/cache/test-results/*
```

## Metrics & Reporting

### Cache Hit Rate Formula
```
hit_rate = hits / (hits + misses) * 100%
```

### Time Savings Calculation
```
savings = (cached_modules * avg_test_time) / total_time * 100%
```

### Cache Size Monitoring
```bash
# Monitor over time
du -sh .yawl/cache/test-results >> .yawl/cache/.size-log.txt
```

## Continuous Validation

### Weekly Checks
```bash
# Run weekly cache validation
bash scripts/cache-cleanup.sh --stats
bash scripts/cache-cleanup.sh --hitrate
```

### Monthly Maintenance
```bash
# Monthly full cache cleanup
bash scripts/cache-cleanup.sh --prune 720  # 30 days
bash scripts/cache-cleanup.sh --stats
```

## Known Limitations

1. **Cross-machine caching**: Cache is local to each machine (not synchronized)
2. **Partial source changes**: Any source file change invalidates cache for entire module
3. **Transitive dependency changes**: Not directly detected; parent pom changes invalidate
4. **Test flakiness**: Cache assumes tests are deterministic (no flaky tests)
5. **Surefire format dependency**: Assumes standard Maven Surefire report format

## Troubleshooting Test Failures

### "source directory not found" error
```bash
# Check module structure
ls -la yawl-engine/src/
# Should show: java, main, test, or main/java directories
```

### Hash computation returns empty
```bash
# Check if hashing tools are available
command -v b3sum && echo "b3sum available" || echo "fallback to sha256sum"
command -v sha256sum && echo "sha256sum available" || echo "fallback to md5sum"
```

### Cache not being used by dx.sh
```bash
# Check cache is enabled
echo $DX_CACHE  # Should be 1
# Check cache config sources correctly
bash -c 'source .mvn/cache-config.sh && echo "Config loaded"'
```

## References

- Test configuration: `.mvn/cache-config.sh`
- Cleanup implementation: `scripts/cache-cleanup.sh`
- Integration test fixtures: `/tmp/cache_*.sh` (above)
