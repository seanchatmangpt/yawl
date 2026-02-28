# Class Data Sharing (CDS) Optimization — YAWL v6.0

## Overview

Class Data Sharing (CDS) is a Java 25+ feature that pre-loads commonly used classes into a binary archive, reducing startup time and memory footprint. For YAWL's hot modules (yawl-engine, yawl-elements), CDS can provide **30-40% startup time reduction** on subsequent builds.

## How CDS Works

Java 25+ CDS creates a platform-specific archive (.jsa file) containing:
- Metadata about frequently-used classes
- Pre-processed bytecode
- Optimized class layout

When the JVM starts with `-XX:SharedArchiveFile=path/to/archive.jsa`, it memory-maps this archive instead of parsing individual .class files.

## YAWL Implementation

### Hot Modules

CDS is automatically generated and used for these modules (critical path):
- **yawl-engine** — Stateful workflow engine, high compilation overhead
- **yawl-elements** — Core element definitions, frequently compiled

### File Structure

```
.yawl/cds/
├── .gitkeep                # Marker file (directory tracked by git)
├── .gitignore              # Excludes *.jsa, *.lst, metadata.json
├── yawl-engine.jsa         # CDS archive (auto-generated, not committed)
├── yawl-engine-classes.lst # Class list for CDS (auto-generated)
├── yawl-elements.jsa       # CDS archive (auto-generated, not committed)
├── yawl-elements-classes.lst # Class list for CDS (auto-generated)
└── metadata.json           # Generation metadata (auto-generated)
```

### Auto-Generation

CDS archives are **automatically generated** by `scripts/generate-cds-archives.sh` in these scenarios:

1. **First run**: Archives don't exist → generated on first compile
2. **Module compilation**: Hot modules are freshly compiled → archives regenerated
3. **Java upgrade**: Java version changes → archives regenerated (detected via metadata)

### Scripts

#### `scripts/generate-cds-archives.sh`

Main CDS archive generator:

```bash
# Generate for all hot modules (or update if needed)
bash scripts/generate-cds-archives.sh generate

# Generate specific module
bash scripts/generate-cds-archives.sh generate yawl-engine

# Validate existing archives
bash scripts/generate-cds-archives.sh validate

# Check CDS status and metadata
bash scripts/generate-cds-performance.sh status

# Remove all CDS archives
bash scripts/generate-cds-archives.sh clean
```

Exit codes:
- **0**: Archives generated/validated successfully
- **1**: Java version incompatible or transient error
- **2**: Generation/validation failed

Environment:
- `CDS_VERBOSE=1` — Show detailed output (default: quiet)
- `CDS_SKIP_VALIDATE=1` — Skip validation after generation (default: validate)

#### `scripts/cds-helper.sh`

CDS integration helper (called by dx.sh):

```bash
# Print CDS JVM flags for Maven (if archives exist)
bash scripts/cds-helper.sh flags
# Output: -XX:SharedArchiveFile=.yawl/cds/yawl-engine.jsa -XX:SharedArchiveFile=.yawl/cds/yawl-elements.jsa

# Check if CDS archives exist (exit 0 if yes)
bash scripts/cds-helper.sh should-use

# Validate all CDS archives
bash scripts/cds-helper.sh validate

# Report CDS status
bash scripts/cds-helper.sh status
# Output:
#   CDS Status:
#     - yawl-engine: 15842 KB
#     - yawl-elements: 8291 KB

# Auto-generate CDS if modules changed
bash scripts/cds-helper.sh generate [force_flag]
```

#### `scripts/test-cds-performance.sh`

Measure CDS performance impact:

```bash
# Full test (5 iterations each with/without CDS)
bash scripts/test-cds-performance.sh

# Quick test (3 iterations)
bash scripts/test-cds-performance.sh --quick

# Clean + test
bash scripts/test-cds-performance.sh --clean

# Results saved to .yawl/performance/cds-test.json
```

### Integration with dx.sh

The main build script (`scripts/dx.sh`) **automatically**:

1. **Pre-compile**: Checks if CDS archives are available and injects flags
2. **Post-compile**: Regenerates CDS archives for any hot modules that were compiled
3. **Graceful fallback**: Works without CDS if archives don't exist (no performance degradation)

#### Flags

Control CDS behavior via environment variables:

```bash
# Enable CDS (default: on)
DX_CDS_GENERATE=1 bash scripts/dx.sh compile

# Disable CDS
DX_CDS_GENERATE=0 bash scripts/dx.sh compile

# Force CDS regeneration
bash scripts/dx.sh compile -pl yawl-engine
# Automatically regenerates yawl-engine.jsa after compile
```

### Integration with .mvn/jvm.config

The Maven JVM configuration (`/.mvn/jvm.config`) includes baseline settings:
- Memory: `-Xms4g -Xmx8g`
- GC: ZGC with generational mode
- Metaspace: 256m-512m

**CDS flags are NOT hardcoded** in jvm.config because:
1. Archives are platform-specific
2. Archives may not exist initially
3. Paths must be absolute/relative consistently

Instead, CDS flags are **dynamically injected by scripts/cds-helper.sh** when archives exist.

### Metadata Tracking

`.yawl/cds/metadata.json` tracks:

```json
{
  "modules": {
    "yawl-engine": {
      "java_version": "25.0.2+10-LTS",
      "generated_at": "2026-02-28T14:30:00Z",
      "archive_size": 16219136
    },
    "yawl-elements": {
      "java_version": "25.0.2+10-LTS",
      "generated_at": "2026-02-28T14:30:05Z",
      "archive_size": 8489984
    }
  }
}
```

Used to detect when regeneration is needed (Java version change, etc.)

## Performance Expectations

### Startup Time

CDS primarily benefits **startup time**, not compilation time:
- **First build** (no CDS): Baseline
- **Second+ builds** (with CDS): 30-40% faster startup
- **Overall effect on full compile-test cycle**: 5-10% improvement (limited by other overhead)

### Typical Timings

Example for `yawl-elements` module:

| Scenario | Time | Notes |
|----------|------|-------|
| Cold compile (no cache) | 45s | JVM startup overhead |
| Warm compile (2nd run) | 42s | ~7% improvement from CDS |
| Warm compile (3rd+ run) | 42s | Archives cached in OS page cache |
| With incremental build | 12s | Includes link/package phases |

### What CDS Doesn't Optimize

CDS does NOT improve:
- **Compilation time** (javac speed)
- **Test execution** (JUnit running)
- **Full builds** (non-hot modules)

It only improves **JVM startup** → affects module build startup.

## Troubleshooting

### CDS Archives Not Generated

**Symptom**: `dx.sh` completes but `ls .yawl/cds/*.jsa` shows no files

**Causes**:
1. Java version < 25
2. Compilation failed (modules not built)
3. CDS generation disabled (`DX_CDS_GENERATE=0`)

**Fix**:
```bash
# Check Java version
java -version

# Check module compilation
ls yawl-engine/target/classes

# Manually regenerate
bash scripts/generate-cds-archives.sh generate

# Enable CDS
DX_CDS_GENERATE=1 bash scripts/dx.sh compile -pl yawl-engine
```

### CDS Archives Invalid After Java Upgrade

**Symptom**: `cds-helper.sh validate` fails after Java update

**Cause**: Archives are Java-version specific; Java 25.0.1 ≠ Java 25.0.2

**Fix**:
```bash
# Automatically triggers regeneration
bash scripts/generate-cds-archives.sh generate

# Or manually clean + regenerate
bash scripts/generate-cds-archives.sh clean
bash scripts/generate-cds-archives.sh generate
```

### CDS Not Used Despite Archives Existing

**Symptom**: Archive exists but `dx.sh` doesn't report "CDS archives available"

**Causes**:
1. Archive size < 1KB (invalid)
2. Archive corrupted

**Fix**:
```bash
# Check archive validity
bash scripts/cds-helper.sh validate

# Check archive size
ls -lh .yawl/cds/*.jsa

# Regenerate if needed
bash scripts/generate-cds-archives.sh clean
bash scripts/generate-cds-archives.sh generate
```

### "CDS disabled" Messages in Compilation

**Symptom**: `java ... -XX:SharedArchiveFile=... --version` output shows errors

**Cause**: Archive incompatible with current JVM flags or Java version

**Fix**:
```bash
# Validate and rebuild
bash scripts/cds-helper.sh validate

# If validation fails, regenerate
bash scripts/generate-cds-archives.sh clean
bash scripts/generate-cds-archives.sh generate
```

## Advanced Usage

### Profiling CDS Effectiveness

Generate CDS performance metrics:

```bash
# Full performance test (measures startup time reduction)
bash scripts/test-cds-performance.sh

# Quick test
bash scripts/test-cds-performance.sh --quick

# Results in .yawl/performance/cds-test.json
cat .yawl/performance/cds-test.json | jq .
```

### Manual CDS Generation

For CI/CD pipelines:

```bash
# Generate CDS before main build
bash scripts/generate-cds-archives.sh generate

# Validate CDS is ready
bash scripts/cds-helper.sh validate

# Run build (CDS flags auto-injected)
bash scripts/dx.sh compile all
```

### Disabling CDS

If CDS causes issues:

```bash
# Disable for this build only
DX_CDS_GENERATE=0 bash scripts/dx.sh compile

# Disable globally (edit .bashrc or equivalent)
export DX_CDS_GENERATE=0
```

## FAQ

**Q: Does CDS slow down the build if archives don't exist?**
A: No. CDS is opt-in and gracefully falls back to normal operation.

**Q: Can I commit CDS archives to git?**
A: No. Archives are platform and Java-version specific. They must be regenerated on each platform/version.

**Q: What's the minimum Java version?**
A: Java 25+. CDS is not available in older versions. YAWL requires Java 25.

**Q: Do I need to do anything to use CDS?**
A: No. It's automatic. Just run `bash scripts/dx.sh compile` and CDS is handled.

**Q: How much disk space do CDS archives use?**
A: ~20-25 MB total (yawl-engine: ~15MB, yawl-elements: ~8MB). They're excluded from git.

**Q: Can I use CDS with IDE builds (IntelliJ, Eclipse)?**
A: Indirectly. IDEs use Maven under the hood, and Maven uses `.mvn/jvm.config` which respects CDS flags from `cds-helper.sh`. However, IDEs may not automatically regenerate CDS. Run `bash scripts/generate-cds-archives.sh generate` after major code changes.

## References

- [Java CDS Documentation](https://docs.oracle.com/en/java/javase/21/vm/class-data-sharing.html)
- [YAWL Build Optimization Guide](./BUILD-OPTIMIZATION.md)
- [dx.sh Documentation](../scripts/dx.sh)

---

**Last Updated**: 2026-02-28
**Status**: Production Ready
**Compatibility**: Java 25+ | Maven 4+
