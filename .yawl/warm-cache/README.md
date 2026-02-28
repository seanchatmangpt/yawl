# YAWL Warm Module Bytecode Cache

## Overview

The warm module cache is a build optimization that reduces compilation time for frequently-modified modules (**yawl-engine** and **yawl-elements**) by reusing compiled bytecode from previous builds when source code hasn't changed.

### Key Features

- **Automatic Caching**: Compiled classes are saved after successful builds
- **Intelligent Validation**: Cache is only used if source files, dependencies, and Java version match
- **TTL-Based Expiration**: Cache entries expire after 8 hours (configurable)
- **Zero Configuration**: Enable with `--warm-cache` flag or `DX_WARM_CACHE=1` environment variable
- **Transparent Fallback**: Invalid cache gracefully falls back to normal compilation

## Quick Start

### Enable Warm Cache for a Build

```bash
# Option 1: Using --warm-cache flag
bash scripts/dx.sh --warm-cache compile

# Option 2: Using environment variable
DX_WARM_CACHE=1 bash scripts/dx.sh compile

# Option 3: For compile + test
bash scripts/dx.sh --warm-cache all
```

### Check Cache Statistics

```bash
bash scripts/manage-warm-cache.sh stats
```

### View Cache Info for Specific Module

```bash
bash scripts/manage-warm-cache.sh info yawl-engine
```

### Validate Cache

```bash
bash scripts/manage-warm-cache.sh validate yawl-engine
```

### Clean Cache

```bash
# Clean expired entries (TTL > 8 hours)
bash scripts/manage-warm-cache.sh clean

# Manually clear all cache
rm -rf .yawl/warm-cache/*
bash scripts/manage-warm-cache.sh stats
```

## How It Works

### Build Workflow with Warm Cache

```
Start build with --warm-cache
    ↓
Load yawl-engine from cache (if valid)
    ↓
Load yawl-elements from cache (if valid)
    ↓
Run Maven for remaining modules only
    ↓
Save newly compiled modules to cache
    ↓
End build
```

### Cache Validation Checklist

Before using cached bytecode, the system verifies:

1. **Source Files Match** - SHA256 hash of all Java source files matches cached hash
2. **Dependencies Match** - pom.xml and parent pom.xml haven't changed (dependency hash match)
3. **Java Version Matches** - JDK version (e.g., Java 25) hasn't changed
4. **TTL Not Expired** - Cache entry was created less than 8 hours ago

If any check fails, the cache is invalidated and the module is recompiled from scratch.

## Cache Structure

```
.yawl/warm-cache/
├── metadata.json                    # Global cache metadata
├── yawl-engine/
│   └── classes-{hash}/             # Compiled classes for this hash
│       ├── org/
│       ├── META-INF/
│       └── ...
└── yawl-elements/
    └── classes-{hash}/
```

### metadata.json Format

```json
{
  "version": "1.0",
  "created": "2026-02-28T10:00:00Z",
  "updated": "2026-02-28T14:00:00Z",
  "cache_hits": 5,
  "cache_misses": 2,
  "modules": {
    "yawl-engine": {
      "status": "cached",
      "hash": "abc123def456...",
      "size": "52428800",
      "timestamp": "2026-02-28T14:00:00Z",
      "ttl_expires": "2026-03-01T14:00:00Z",
      "java_version": "25"
    }
  }
}
```

## Performance Expectations

### Typical Build Times

| Scenario | Time | Notes |
|----------|------|-------|
| **First build** (no cache) | 45-60s | Full compilation |
| **Cached build** (unchanged code) | 2-5s | Load bytecode + build remaining modules |
| **Cache miss** (code changed) | 45-60s | Recompiles, then updates cache |
| **Build after 8h** (TTL expired) | 45-60s | Cache invalidated, full rebuild |

### Target Performance

- **Baseline**: Full compile of yawl-engine + yawl-elements = ~30-40s
- **With warm cache**: 2-5s (using cached bytecode)
- **Target speedup**: 6-15x faster for cached builds
- **Success threshold**: ≥40% reduction in compilation time

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `DX_WARM_CACHE` | `0` (disabled) | Enable warm cache (set to `1`) |
| `WARM_CACHE_TTL_HOURS` | `8` | Cache expiration time in hours |
| `WARM_CACHE_MAX_SIZE_BYTES` | `536870912` (500MB) | Max total cache size |
| `WARM_CACHE_DEBUG` | `0` | Show detailed cache debug output |

### Example with Custom TTL

```bash
# Use 24-hour cache expiration instead of default 8 hours
WARM_CACHE_TTL_HOURS=24 bash scripts/dx.sh --warm-cache compile
```

## Cache Invalidation

The cache is automatically invalidated in these scenarios:

### Source Code Changes
- Any Java file in `src/main/java/` changes
- Hash mismatch detected on cache load

### Dependency Changes
- `pom.xml` modified
- Parent `pom.xml` modified
- Dependency versions updated

### Environment Changes
- Java version changed (e.g., Java 21 → Java 25)
- Major OS/architecture change

### TTL Expiration
- Cache entry is older than 8 hours

When invalidation occurs, the module is automatically recompiled and the cache is refreshed.

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Build with warm cache
  run: |
    DX_WARM_CACHE=1 bash scripts/dx.sh compile
```

### Jenkins Pipeline Example

```groovy
stage('Compile') {
  environment {
    DX_WARM_CACHE = '1'
  }
  steps {
    sh 'bash scripts/dx.sh --warm-cache compile'
  }
}
```

### Docker Build Example

```dockerfile
# Build stage with warm cache
FROM openjdk:25-jdk-slim as builder
WORKDIR /app
COPY . .
RUN DX_WARM_CACHE=1 bash scripts/dx.sh compile
```

## Troubleshooting

### Cache not being used (always recompiling)

Check if cache is valid:
```bash
bash scripts/manage-warm-cache.sh validate yawl-engine
```

If validation fails, check the reason:
```bash
WARM_CACHE_DEBUG=1 bash scripts/manage-warm-cache.sh validate yawl-engine
```

### Stale cache causing build failures

Clean expired cache entries:
```bash
bash scripts/manage-warm-cache.sh clean
```

Or completely reset cache:
```bash
rm -rf .yawl/warm-cache/*
```

### Cache growing too large (>500MB)

The cache automatically cleans expired entries when size limit is exceeded. To manually resize:

```bash
# Set smaller max size (e.g., 250MB)
WARM_CACHE_MAX_SIZE_BYTES=268435456 bash scripts/manage-warm-cache.sh clean
```

### Cache hits not being counted

Enable debug output to see cache operations:
```bash
WARM_CACHE_DEBUG=1 bash scripts/dx.sh --warm-cache compile
```

## Advanced Usage

### Combining with CDS Archives

Warm cache works with Class Data Sharing (CDS) for maximum performance:

```bash
# First build: compile and generate CDS
bash scripts/dx.sh compile all

# Subsequent builds: use warm cache + CDS
bash scripts/dx.sh --warm-cache compile
```

### Performance Profiling

Track build times over time:

```bash
# Enable timing metrics
DX_TIMINGS=1 bash scripts/dx.sh --warm-cache compile

# View results
cat .yawl/timings/build-timings.json | tail -5
```

### Testing Cache Behavior

Run the warm cache test suite:

```bash
bash scripts/test-warm-cache.sh yawl-engine
```

This validates:
- Initial build without cache
- Cache save functionality
- Cached load functionality
- Cache statistics
- Cache validation

## Implementation Details

### Cache Management Script

The `scripts/manage-warm-cache.sh` script provides low-level cache management:

```bash
# Save compiled classes
bash scripts/manage-warm-cache.sh save yawl-engine

# Load from cache
bash scripts/manage-warm-cache.sh load yawl-engine

# Validate cache
bash scripts/manage-warm-cache.sh validate yawl-engine

# Show info
bash scripts/manage-warm-cache.sh info yawl-engine

# Clean old entries
bash scripts/manage-warm-cache.sh clean

# Show statistics
bash scripts/manage-warm-cache.sh stats
```

### Integration Points in dx.sh

The `scripts/dx.sh` script integrates warm cache at two points:

1. **Pre-compile**: Load cached modules before Maven runs
2. **Post-compile**: Save newly compiled modules to cache

When cached modules are loaded, they are excluded from Maven's module list using the `-pl` flag, so Maven only compiles the modules that changed.

## Limitations and Assumptions

1. **Single-user builds only**: Cache is not thread-safe for concurrent builds
2. **Local development only**: Not intended for distributed CI without shared cache
3. **Hot modules only**: Currently supports yawl-engine and yawl-elements
4. **No incremental compilation**: Cache is all-or-nothing per module
5. **Single Java version**: Cache is tied to the current Java version

## Future Enhancements

Planned improvements for Phase 4:

- [ ] Multi-module cache warmup (parallelize cache loads)
- [ ] Network-shared cache (NFS/S3 backend)
- [ ] Cache compression for modules >100MB
- [ ] Per-package cache granularity
- [ ] Machine learning-based cache invalidation prediction
- [ ] Integration with Maven incremental builds
- [ ] Distributed cache (Redis/Memcached backend)

## See Also

- `scripts/dx.sh` — Fast build-test loop that uses warm cache
- `scripts/manage-warm-cache.sh` — Low-level cache management
- `scripts/test-warm-cache.sh` — Test suite for cache functionality
- `.yawl/warm-cache/.gitignore` — Cache directory exclusions
- `CLAUDE.md` (Build section Λ) — Build orchestration documentation

---

**Last Updated**: 2026-02-28
**Version**: 1.0
**Status**: Production Ready
