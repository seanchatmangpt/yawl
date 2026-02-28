# Class Data Sharing (CDS) Archives

This directory contains Class Data Sharing (CDS) archives for YAWL's hot modules.

## What Are These Files?

- **`*.jsa`** — CDS archive files (binary, Java 25+ format)
  - `yawl-engine.jsa` — Pre-loaded classes for yawl-engine module
  - `yawl-elements.jsa` — Pre-loaded classes for yawl-elements module

- **`*.lst`** — Class list files (text, auto-generated)
  - Lists all classes included in corresponding .jsa archive

- **`metadata.json`** — Generation metadata
  - Tracks Java version, timestamp, archive sizes
  - Used to detect when regeneration is needed

## Auto-Generation

These files are **automatically generated** by the build system:

1. **First build**: `scripts/generate-cds-archives.sh` generates archives after compiling hot modules
2. **Incremental builds**: Archives are regenerated after any hot module compilation
3. **Java upgrades**: Archives are auto-regenerated if Java version changes

You generally **do not need to manually manage** these files.

## Usage

### Automatic (Default)

The build system (`scripts/dx.sh`) automatically:
- Generates archives if missing
- Regenerates after hot module compilation
- Injects CDS flags into JVM configuration

```bash
# Archives are auto-generated and used
bash scripts/dx.sh compile -pl yawl-engine
```

### Manual Generation

To manually generate/manage CDS archives:

```bash
# Generate archives for all hot modules
bash scripts/generate-cds-archives.sh generate

# Generate specific module
bash scripts/generate-cds-archives.sh generate yawl-engine

# Validate existing archives
bash scripts/generate-cds-archives.sh validate

# Check status
bash scripts/generate-cds-archives.sh status

# Remove all archives (they'll be regenerated on next build)
bash scripts/generate-cds-archives.sh clean
```

### Check CDS Helper

For integration with build system:

```bash
# Get CDS JVM flags (if archives exist)
bash scripts/cds-helper.sh flags

# Check if CDS is available
bash scripts/cds-helper.sh should-use
echo $?  # 0 = CDS available, 1 = not available

# Report status
bash scripts/cds-helper.sh status
```

## Performance Impact

CDS reduces **startup time** (first JVM invocation) by ~30-40%:
- **Cold build** (no cache): Baseline
- **Warm build** (CDS available): 30-40% faster startup
- **Overall impact**: 5-10% on full compile-test cycle

See `docs/v6/CDS-OPTIMIZATION.md` for detailed benchmarks.

## Troubleshooting

### Archives Not Generated

Check Java version and module compilation:

```bash
# Verify Java 25+
java -version

# Check if modules are compiled
ls yawl-engine/target/classes

# Force regeneration
bash scripts/generate-cds-archives.sh clean
bash scripts/generate-cds-archives.sh generate
```

### Validate Archives After Java Upgrade

Archives are Java-version specific. After upgrading Java:

```bash
# Automatically handled by build system, or manually:
bash scripts/generate-cds-archives.sh validate

# If validation fails, regenerate:
bash scripts/generate-cds-archives.sh clean
bash scripts/generate-cds-archives.sh generate
```

## Git Configuration

These files are **not committed** to git (see `.gitignore`):

```
*.jsa          # Binary archives
*.lst          # Class lists
metadata.json  # Generation metadata
```

This is correct because:
- Archives are platform-specific (Linux/macOS/Windows differ)
- Archives are Java-version-specific (25.0.1 ≠ 25.0.2)
- Archives are auto-generated and reproducible

## Environment Variables

Control CDS behavior via environment variables:

```bash
# Enable CDS during build (default: on)
DX_CDS_GENERATE=1 bash scripts/dx.sh compile

# Disable CDS
DX_CDS_GENERATE=0 bash scripts/dx.sh compile

# Verbose output during generation
CDS_VERBOSE=1 bash scripts/generate-cds-archives.sh generate

# Skip validation after generation
CDS_SKIP_VALIDATE=1 bash scripts/generate-cds-archives.sh generate
```

## Disk Space

Total CDS archives: ~20-25 MB
- `yawl-engine.jsa`: ~15 MB
- `yawl-elements.jsa`: ~8 MB
- Class lists and metadata: < 1 MB

**Note**: These files are not committed to git, so repository size is unaffected.

## More Information

- **Full documentation**: See `docs/v6/CDS-OPTIMIZATION.md`
- **Build system integration**: See `scripts/dx.sh` and `scripts/cds-helper.sh`
- **Performance testing**: Run `bash scripts/test-cds-performance.sh`
- **Java CDS docs**: https://docs.oracle.com/en/java/javase/21/vm/class-data-sharing.html

---

**Status**: Production Ready | **Compatibility**: Java 25+ | **Last Updated**: 2026-02-28
