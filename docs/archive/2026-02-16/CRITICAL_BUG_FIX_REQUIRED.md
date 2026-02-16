# CRITICAL BUG FIX REQUIRED: yawl-build.sh Maven Flag Error

**Priority**: CRITICAL  
**Component**: `/home/user/yawl/.claude/skills/yawl-build.sh`  
**Issue**: Line 137 passes invalid Maven command-line flag  
**Impact**: ALL Maven builds fail immediately  
**Time to Fix**: ~5 minutes  

---

## Problem Summary

The `yawl-build.sh` skill passes the `--enable-preview` flag as a Maven command-line argument, but Maven does not recognize this flag. This causes ALL Maven-based builds to fail with:

```
Unable to parse command line options: Unrecognized option: --enable-preview
```

---

## Current Broken Implementation

**File**: `/home/user/yawl/.claude/skills/yawl-build.sh`  
**Lines**: 135-157

```bash
135  if [[ "${BUILD_SYSTEM}" == "maven" ]]; then
136      # Maven build with Java 25 preview features
137      MVN_ARGS=("--batch-mode" "--enable-preview" "-T" "1C")
138
139      # Add verbose/quiet flags
140      if [[ -n "${VERBOSE}" ]]; then
141          MVN_ARGS+=("-X")
142      fi
143      if [[ -n "${QUIET}" ]]; then
144          MVN_ARGS+=("-q")
145      fi
146
147      # Skip tests if requested
148      if [[ "${SKIP_TESTS}" == "true" ]]; then
149          MVN_ARGS+=("-DskipTests")
150      fi
151
152      echo -e "${BLUE}[yawl-build] Maven command: ${MVN_CMD} ${TARGET} ${MVN_ARGS[*]}${NC}"
153      echo ""
154
155      if ${MVN_CMD} "${TARGET}" "${MVN_ARGS[@]}"; then
156          BUILD_SUCCESS=true
157      fi
158  fi
```

---

## Root Cause

**The `--enable-preview` flag is a Java compiler/JVM option**, not a Maven command-line option.

When Maven is invoked like this:
```bash
mvn compile --batch-mode --enable-preview -T 1C
```

Maven tries to parse `--enable-preview` as a Maven option and fails.

The correct approach is to pass Java options via the `MAVEN_OPTS` environment variable:
```bash
export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent"
mvn compile --batch-mode -T 1C
```

---

## Error Evidence

When running `bash ./claude/skills/yawl-build.sh compile`:

```
[yawl-build] Using Maven build system
[yawl-build] Building YAWL with target: compile
[yawl-build] Build system: maven

[yawl-build] Maven command: /opt/maven/bin/mvn compile --batch-mode --enable-preview -T 1C

Unable to parse command line options: Unrecognized option: --enable-preview

usage: mvn [options] [<goal(s)>] [<phase(s)>]

Options:
 -am,--also-make                         If project list is specified,
                                         also build projects required by
                                         the list
 [... rest of Maven help ...]

[yawl-build] Build failed: compile
```

---

## Required Fix

### Step 1: Remove `--enable-preview` from MVN_ARGS

**Line 137** - CHANGE FROM:
```bash
MVN_ARGS=("--batch-mode" "--enable-preview" "-T" "1C")
```

**TO**:
```bash
MVN_ARGS=("--batch-mode" "-T" "1C")
```

### Step 2: Add MAVEN_OPTS Export Before Maven Invocation

**Between lines 150 and 155** - ADD:
```bash
# Configure Java 25 preview features via MAVEN_OPTS
export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent"
```

---

## Complete Fixed Code Section

Here is the corrected implementation:

```bash
135  if [[ "${BUILD_SYSTEM}" == "maven" ]]; then
136      # Maven build with Java 25 preview features
137      MVN_ARGS=("--batch-mode" "-T" "1C")
138
139      # Add verbose/quiet flags
140      if [[ -n "${VERBOSE}" ]]; then
141          MVN_ARGS+=("-X")
142      fi
143      if [[ -n "${QUIET}" ]]; then
144          MVN_ARGS+=("-q")
145      fi
146
147      # Skip tests if requested
148      if [[ "${SKIP_TESTS}" == "true" ]]; then
149          MVN_ARGS+=("-DskipTests")
150      fi
151
152      # Configure Java 25 preview features via MAVEN_OPTS
153      export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent"
154
155      echo -e "${BLUE}[yawl-build] Maven command: ${MVN_CMD} ${TARGET} ${MVN_ARGS[*]}${NC}"
156      echo ""
157
158      if ${MVN_CMD} "${TARGET}" "${MVN_ARGS[@]}"; then
159          BUILD_SUCCESS=true
160      fi
161  fi
```

---

## Verification Steps

After applying the fix:

1. Test Maven detection:
   ```bash
   bash ./.claude/skills/yawl-build.sh --help
   ```
   Should show help with Maven targets listed.

2. Test Maven compile (simulated, will fail without proper Java 25):
   ```bash
   bash ./.claude/skills/yawl-build.sh compile
   ```
   Should invoke Maven correctly (may fail due to Java version, not Maven flag).

3. Verify MAVEN_OPTS is set:
   ```bash
   bash -x ./.claude/skills/yawl-build.sh compile 2>&1 | grep MAVEN_OPTS
   ```
   Should show: `export MAVEN_OPTS="..."`

---

## Context & Rationale

The `MAVEN_OPTS` approach is the correct Maven practice for passing JVM arguments:

- **Maven itself** handles Java execution through wrapper scripts
- **JVM options** like `--enable-preview` must be set as environment variables
- **Maven documentation** recommends MAVEN_OPTS for Java 9+ flags
- **session-start.sh** already sets MAVEN_OPTS correctly (line 56):
  ```bash
  export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent -Xmx2g"
  ```

The fix aligns with this established pattern in the codebase.

---

## Testing After Fix

Once fixed, Maven builds should:

1. Accept `--batch-mode` flag from MVN_ARGS
2. Inherit `--enable-preview` from MAVEN_OPTS environment variable
3. Successfully invoke `mvn ${TARGET}` without flag parsing errors
4. Continue to detect and respect Maven as primary build system

---

## Impact Assessment

**Before Fix**:
- Maven builds: 100% FAIL
- Ant builds: ✅ Work (if user falls back)
- Impact: Blocks all Maven-based development

**After Fix**:
- Maven builds: ✅ Work (with proper Java 25 available)
- Ant builds: ✅ Work (with deprecation warning)
- Impact: Full Maven support restored

---

## Files to Update

1. `/home/user/yawl/.claude/skills/yawl-build.sh`
   - Line 137: Remove `--enable-preview` from MVN_ARGS
   - After line 150: Add MAVEN_OPTS export

---

## Review Checklist

After fix is applied:

- [ ] Line 137 no longer contains `--enable-preview`
- [ ] MAVEN_OPTS export added before Maven invocation (line 153 area)
- [ ] `bash -n` syntax check passes
- [ ] `--help` works correctly
- [ ] Maven detection still works
- [ ] No HYPER_STANDARDS violations introduced

---

**Severity**: CRITICAL - BLOCKING  
**Reported**: 2026-02-16  
**Status**: PENDING FIX  
**Estimated Fix Time**: 5 minutes  
**Validation Report**: `/home/user/yawl/HOOK_SKILL_VALIDATION_2026-02-16.md`

