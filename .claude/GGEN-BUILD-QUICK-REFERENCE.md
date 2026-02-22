# ggen Build Orchestration — Quick Reference

**Purpose**: Execute Λ (Build) phase with receipt generation and guards validation
**Location**: `/home/user/yawl/scripts/ggen-build.sh`

---

## Quick Start

```bash
# Execute full pipeline: generate → compile → test → validate
bash scripts/ggen-build.sh --phase lambda

# Expected output:
# [BUILD] Phase 0: Generate (ggen)
# [✓] ggen generation completed in 500ms
# [BUILD] Phase 1: Compile
# [✓] Compilation succeeded in 12000ms
# [BUILD] Phase 2: Test
# [✓] Tests passed in 45000ms
# [BUILD] Phase 3: Validate (Static Analysis)
# [!] Analysis found issues: SpotBugs=0 PMD=1 Checkstyle=0
# [✓] All phases GREEN
# [BUILD] Receipt: /home/user/yawl/.ggen/build-receipt.json
```

---

## Invocation Options

### Full Pipeline (Recommended)

```bash
bash scripts/ggen-build.sh --phase lambda
# Runs: generate → compile → test → validate
# Exit code: 0 (success) or 1-4 (failure)
```

### Individual Phases

```bash
# Compile only (fast feedback)
bash scripts/ggen-build.sh --phase compile
exit 1-2 on failure

# Test only (assumes compiled)
bash scripts/ggen-build.sh --phase test
exit 2 on failure

# Validate only (static analysis)
bash scripts/ggen-build.sh --phase validate
exit 3 on failure

# Generate only (ggen artifacts)
bash scripts/ggen-build.sh --phase generate
exit 4 on failure
```

### Options

```bash
# Force rebuild (skip cache)
bash scripts/ggen-build.sh --force

# Skip cache checks
bash scripts/ggen-build.sh --no-cache

# Show Maven output (verbose)
bash scripts/ggen-build.sh -v --phase compile

# Timeout per phase (seconds)
GGEN_BUILD_TIMEOUT=600 bash scripts/ggen-build.sh
```

---

## Receipt Format

**Location**: `.ggen/build-receipt.json` (JSONL)

**Each line** (one JSON object):

```json
{
  "phase": "compile",
  "status": "GREEN",
  "timestamp": "2026-02-21T14:32:10Z",
  "elapsed_ms": 12450,
  "details": {
    "modules": 5,
    "tests": 0
  }
}
```

**Status values**:
- `GREEN` = phase passed
- `WARN` = phase passed with warnings (e.g., code quality issues)
- `FAIL` = phase failed (build halts)

---

## Check Build Status (After Execution)

### Quick: Did build pass?

```bash
# Exit 0 if all GREEN, exit 1 if any FAIL
if tail -1 .ggen/build-receipt.json | grep -q "GREEN"; then
  echo "BUILD PASSED"
else
  echo "BUILD FAILED"
fi
```

### Detailed: Full receipt chain

```bash
# Pretty-print all receipts
jq '.' .ggen/build-receipt.json | less

# Count phases
jq 'length' .ggen/build-receipt.json

# Check if all GREEN
jq 'all(.status == "GREEN")' .ggen/build-receipt.json

# Get total time
jq '[.[].elapsed_ms] | add' .ggen/build-receipt.json
```

### Via Java API

```bash
# Compile BuildPhase.java and run
javac src/org/yawlfoundation/yawl/engine/ggen/BuildPhase.java
java -cp src org.yawlfoundation.yawl.engine.ggen.BuildPhase

# Or in your code:
java org.yawlfoundation.yawl.engine.ggen.BuildPhase /home/user/yawl
```

---

## Failure Diagnostics

### Compile failed?

```bash
# Check Maven log
cat /tmp/ggen-compile.log | tail -50
DX_VERBOSE=1 bash scripts/dx.sh compile
```

### Test failed?

```bash
# Check test output
cat /tmp/ggen-test.log | tail -100
# Find specific failure
grep -A5 "FAILURE" /tmp/ggen-test.log
```

### Validation failed?

```bash
# Check analysis results
cat target/spotbugsXml.xml
cat target/pmd.xml
cat target/checkstyle-result.xml
```

### Full error log?

```bash
# Build failure log (all errors)
cat /tmp/ggen-build-failure.log
```

---

## Integration with Guards Phase (H)

### Before H phase starts, verify:

```bash
# Receipt file exists and is readable?
test -f .ggen/build-receipt.json && echo "✓ Receipt found"

# All 4 phases present?
jq '.[] | .phase' .ggen/build-receipt.json

# Any FAIL status?
if jq '.[] | select(.status == "FAIL")' .ggen/build-receipt.json | grep -q .; then
  echo "✗ BUILD NOT READY (phase failed)"
  exit 1
fi

# All phases GREEN or WARN?
if jq 'all(.status == "GREEN" or .status == "WARN")' .ggen/build-receipt.json; then
  echo "✓ BUILD READY FOR H PHASE"
else
  echo "✗ BUILD HAS FAILURES"
  exit 1
fi
```

---

## Environment Variables

```bash
# Enable/disable build cache (default: 1)
export GGEN_BUILD_CACHE=1
bash scripts/ggen-build.sh

# Enable verbose Maven output (default: 0)
export GGEN_BUILD_VERBOSE=1
bash scripts/ggen-build.sh

# Timeout per phase in seconds (default: 300)
export GGEN_BUILD_TIMEOUT=600
bash scripts/ggen-build.sh
```

---

## Troubleshooting

### Build hangs?

```bash
# Check if Maven is running
ps aux | grep mvn

# Kill and retry with timeout
killall mvn
bash scripts/ggen-build.sh --phase compile
```

### Maven network issues?

```bash
# Check if Maven proxy is working
curl -x 127.0.0.1:3128 https://repo.maven.apache.org/maven2
# If proxy fails, run: python3 maven-proxy-v2.py &

# Force offline mode (requires local cache)
mvn -o compile
```

### Receipt file corrupted?

```bash
# Backup and reset
cp .ggen/build-receipt.json .ggen/build-receipt.json.bak
rm .ggen/build-receipt.json
bash scripts/ggen-build.sh --force
```

---

## Performance Tips

### Speed up compile (single module)

```bash
# Compile only one module + its dependencies
bash scripts/ggen-build.sh --phase compile -pl yawl-engine

# Or via dx.sh (faster change detection)
bash scripts/dx.sh compile
```

### Cache hit (skip rebuild)

```bash
# If source code unchanged, build is cached
# To force rebuild:
bash scripts/ggen-build.sh --force

# To skip cache checks:
bash scripts/ggen-build.sh --no-cache
```

### Parallel build

```bash
# Maven already uses parallel (1.5C cores)
# To change parallelism:
mvn -T 2 compile
```

---

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Build (Λ phase)
  run: bash scripts/ggen-build.sh --phase lambda
  timeout-minutes: 5

- name: Check receipts
  if: always()
  run: |
    jq '.' .ggen/build-receipt.json
    if jq 'any(.status == "FAIL")' .ggen/build-receipt.json; then
      exit 1
    fi

- name: Guards validation (H phase)
  run: bash scripts/validate-guards.sh
```

---

## File Locations (Summary)

| File | Purpose |
|------|---------|
| `scripts/ggen-build.sh` | Build orchestrator (Bash) |
| `.ggen/build-receipt.json` | Build audit trail (JSONL) |
| `/tmp/ggen-compile.log` | Compile output |
| `/tmp/ggen-test.log` | Test output |
| `/tmp/ggen-validate.log` | Analysis output |
| `/tmp/ggen-build-failure.log` | Failure log (if RED) |

---

## Reference: Exit Codes

| Code | Meaning | Recovery |
|------|---------|----------|
| 0 | All phases GREEN | Proceed to H phase |
| 1 | Compile FAILED | Fix Java syntax errors |
| 2 | Test FAILED | Fix failing unit tests |
| 3 | Validate FAILED | Fix code quality issues |
| 4 | Generate FAILED | Check ggen artifacts |

---

## See Also

- `.claude/BUILD-PHASE-ORCHESTRATION.md` — Full design doc
- `scripts/dx.sh` — Fast change-detection build (for local development)
- `scripts/ggen-sync.sh` — ggen code generation (called by phase 0)
- `.ggen/` — Build artifacts directory

---

**Last Updated**: 2026-02-21
**Version**: 1.0
**Status**: Production Ready
