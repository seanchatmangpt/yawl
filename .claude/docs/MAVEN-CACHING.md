# Maven Dependency Auto-Caching

## Overview

Maven dependency caching is configured for both GitHub Actions CI/CD and local development environments to significantly reduce build times.

## GitHub Actions Configuration

Located in `.github/workflows/build-maven.yaml`, the following jobs have explicit Maven caching:

1. **test-matrix** - Multi-version Java testing (21, 24)
2. **coverage** - Code coverage with JaCoCo
3. **security-scan** - OWASP Dependency Check
4. **dependency-analysis** - Dependency analysis and updates

### Cache Configuration

```yaml
- name: Cache Maven dependencies
  uses: actions/cache@v3
  with:
    path: ~/.m2/repository
    key: maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      maven-
```

### How It Works

- **Cache Key**: Generated from hash of all `pom.xml` files
- **Cache Path**: `~/.m2/repository` (standard Maven local repository)
- **Cache Hit**: When `pom.xml` files unchanged, dependencies restored instantly
- **Cache Miss**: When `pom.xml` changed, dependencies re-downloaded
- **Fallback**: `restore-keys` allows partial cache hits (e.g., if only one module changed)

### Performance Expectations

- **First Build (cache miss)**: ~30 seconds for dependency downloads
- **Subsequent Builds (cache hit)**: <5 seconds for dependency resolution
- **Partial Cache Hit**: 5-15 seconds (only changed dependencies downloaded)

## Local Development Setup

Located in `.claude/hooks/session-start.sh`, the setup hook:

1. Creates `~/.m2` directory if it doesn't exist
2. Creates `~/.m2/repository` cache directory if needed
3. Verifies cache directory is writable
4. Reports cache size and status

### Local Cache Verification

```bash
# Check cache status
ls -lh ~/.m2/repository

# Check cache size
du -sh ~/.m2/repository

# Verify cache is working
mvn dependency:resolve
```

## Testing

Cache functionality is tested in `test/org/yawlfoundation/yawl/build/BuildSystemTest.java`:

### Test Coverage

1. **testMavenCacheDirectoryExists**: Verifies `~/.m2/repository` exists
2. **testMavenCacheIsWritable**: Ensures cache is writable
3. **testMavenCachePersistsDependencies**: Confirms JAR files cached after resolution
4. **testMavenCacheHasExpectedStructure**: Validates proper Maven cache structure
5. **testMavenCachePerformanceImprovement**: Measures caching performance improvement

### Running Cache Tests

```bash
mvn test -Dtest=BuildSystemTest
```

## Cache Maintenance

### Clearing Cache

```bash
# Remove entire cache (GitHub Actions - automatic on cache miss)
rm -rf ~/.m2/repository

# Remove specific artifact
rm -rf ~/.m2/repository/org/yawlfoundation/yawl

# Maven clean plugin (removes specific artifacts)
mvn dependency:purge-local-repository
```

### Cache Size Management

The Maven cache can grow over time. Recommended practices:

1. **GitHub Actions**: Automatic - caches expire after 7 days of non-use
2. **Local Development**: Manual cleanup recommended every 3-6 months
3. **Typical Size**: 100MB-500MB depending on project dependencies

### Invalidating Cache

Cache is automatically invalidated when:
- Any `pom.xml` file is modified
- Dependency versions are updated
- New dependencies are added

## Troubleshooting

### Cache Not Working

1. **Check Directory Permissions**
   ```bash
   ls -ld ~/.m2/repository
   # Should be drwxr-xr-x or similar
   ```

2. **Verify Maven Settings**
   ```bash
   mvn help:effective-settings | grep localRepository
   # Should show ~/.m2/repository
   ```

3. **Check Network Connectivity**
   ```bash
   mvn dependency:resolve -X
   # Look for "Downloading from central" messages
   ```

### Slow Builds Despite Caching

1. **Verify Cache Hit**
   ```bash
   # GitHub Actions - check workflow logs for:
   # "Cache restored from key: maven-..."
   ```

2. **Check for Snapshot Dependencies**
   - SNAPSHOT versions are always re-downloaded
   - Solution: Use release versions in production

3. **Network Issues**
   - Timeout downloading artifacts
   - Solution: Retry build or check Maven Central status

## Implementation Details

### Files Modified

1. `.github/workflows/build-maven.yaml`
   - Added `actions/cache@v3` step to 4 jobs
   - Removed redundant `cache: 'maven'` from `setup-java` steps
   - Cache key based on POM hash for accuracy

2. `.claude/hooks/session-start.sh`
   - Added cache directory creation logic
   - Added writability verification
   - Added cache size reporting

3. `test/org/yawlfoundation/yawl/build/BuildSystemTest.java`
   - Added 5 new test methods for cache verification
   - Added `measureDependencyResolutionTime()` helper

### Design Decisions

1. **Explicit Cache Action**: Used `actions/cache@v3` instead of relying only on `setup-java` built-in cache for better visibility and control

2. **Cache Key Strategy**: Hash-based keys ensure cache invalidation when dependencies change

3. **Restore Keys**: Fallback pattern allows partial cache hits, reducing download times

4. **Local Setup**: Automatic directory creation in session hook ensures cache works out-of-the-box

## References

- [GitHub Actions Cache Documentation](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
- [Maven Local Repository](https://maven.apache.org/settings.html#localrepository)
- [actions/cache Action](https://github.com/actions/cache)
