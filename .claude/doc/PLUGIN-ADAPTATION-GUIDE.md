# Plugin Adaptation Guide — Pattern for Autonomous Integration

**Version**: 1.0
**Date**: 2026-03-04
**Author**: Autonomous Plugin Framework
**Status**: PRODUCTION

---

## Table of Contents

1. [Overview](#overview)
2. [Plugin Adaptation Pattern](#plugin-adaptation-pattern)
3. [HyperStandardsValidator Case Study](#hyperstandards-validator-case-study)
4. [Replication Checklist](#replication-checklist)
5. [Common Pitfalls](#common-pitfalls)
6. [Troubleshooting](#troubleshooting)
7. [Next Plugins to Adapt](#next-plugins-to-adapt)

---

## Overview

### What This Guide Covers

This guide teaches the **reproducible pattern** for adapting YAWL plugins to work autonomously with the error detection and remediation framework built in the previous session.

**Core insight**: Transform any plugin into an autonomous agent by:
1. Creating a shell wrapper (adapter)
2. Accepting error JSON input
3. Producing standardized receipt JSON output
4. Integrating with existing infrastructure (analyze-errors.sh → decision-engine.sh → remediate-violations.sh)

### Who Should Use This

- **Engineers adapting Tier 2-3 plugins** for autonomous use
- **Architects designing new plugin integrations**
- **Developers extending the autonomous framework**

### Time Investment

- **Simple plugins** (validators, hooks): 1-2 hours
- **Medium plugins** (adapters, servers): 2-4 hours
- **Complex plugins** (MCP tools, agents): 4-8 hours

---

## Plugin Adaptation Pattern

### Step 1: Understand the Plugin Contract

Before adapting, answer these questions:

**Input Contract**:
- [ ] What triggers the plugin? (error type, event, manual invocation)
- [ ] What format is the input? (JSON, XML, Java objects, files)
- [ ] What parameters does it need? (paths, configurations, context)
- [ ] Where are examples of inputs?

**Output Contract**:
- [ ] What does it produce? (errors, receipts, modified files)
- [ ] What format is the output? (JSON, XML, logs, files)
- [ ] What error codes does it use? (0, 1, 2, etc.)
- [ ] How are errors signaled? (exceptions, return codes, logging)

**Integration Points**:
- [ ] Where is it currently used?
- [ ] What calls it?
- [ ] What does it call?
- [ ] Can it be invoked standalone?

### Step 2: Design the Adapter

Create a shell script (`plugin-<name>-adapter.sh`) with this structure:

```bash
#!/usr/bin/env bash
# Plugin: <Name>
# Purpose: Wrap <OriginalPlugin> for autonomous integration
# Input: Error JSON from analyze-errors.sh
# Output: Receipt JSON to .claude/receipts/
# Exit: 0 (GREEN), 1 (TRANSIENT), 2 (RED)

set -euo pipefail

# 1. Configuration & paths
PROJECT_ROOT="$(cd ... && pwd)"
RECEIPTS_DIR="${PROJECT_ROOT}/.claude/receipts"
PLUGIN_RECEIPT="${RECEIPTS_DIR}/plugin-<name>-receipt.json"

# 2. Input validation
validate_input() {
    # Check required files, directories, configurations
    # Return 1 if missing
}

# 3. Plugin invocation
run_plugin() {
    # Call the original plugin
    # Capture output
    # Handle errors
}

# 4. Output transformation
parse_output() {
    # Convert plugin output to standard receipt JSON
    # Extract violations, errors, recommendations
}

# 5. Receipt creation
create_receipt() {
    # Build JSON with: phase, timestamp, status, violations, exit_code
}

# 6. Main flow
main() {
    validate_input || return 1
    local output=$(run_plugin) || return 1
    local violations=$(parse_output "$output")
    local receipt=$(create_receipt "$violations")
    echo "$receipt" > "$PLUGIN_RECEIPT"
    exit $?
}

main "$@"
```

### Step 3: Define Receipt Format

All adapters must produce a standardized receipt JSON:

```json
{
  "phase": "H_GUARDS or Q_INVARIANTS or CUSTOM",
  "timestamp": "2026-03-04T12:34:56Z",
  "plugin": "PluginName",
  "status": "GREEN or RED",
  "violations_count": 0,
  "violations": [
    {
      "pattern": "VIOLATION_TYPE",
      "severity": "FAIL or WARN",
      "file": "/path/to/file.java",
      "line": 42,
      "content": "actual code content",
      "fix_guidance": "how to fix this"
    }
  ],
  "error_message": "human-readable error if RED",
  "exit_code": 0,
  "recommendations": [
    "What to do next..."
  ],
  "next_step": "remediation or none"
}
```

**Key fields**:
- **phase**: Links to validation phases (H, Q, or custom)
- **status**: Determines routing (GREEN = done, RED = needs action)
- **violations**: Array of detected issues with remediation guidance
- **exit_code**: 0 (GREEN), 2 (RED), 1 (TRANSIENT error)

### Step 4: Write Tests

Create a test file (`plugin-<name>-adapter.test`):

```bash
#!/usr/bin/env bash

test_adapter_exists() {
    # Verify adapter script exists and is executable
}

test_missing_input() {
    # Test graceful handling when input is missing
}

test_valid_output_format() {
    # Verify receipt JSON matches schema
}

test_success_path() {
    # Test with clean input (should return GREEN)
}

test_error_path() {
    # Test with problematic input (should return RED)
}

# Run all tests
run_all_tests() { ... }
run_all_tests
```

### Step 5: Integration Testing

Test the full chain:

```bash
# 1. Generate error via analyze-errors.sh
bash .claude/scripts/analyze-errors.sh

# 2. Invoke adapter via decision-engine.sh
bash .claude/scripts/decision-engine.sh <error-type>

# 3. Verify receipt was created
ls -lh .claude/receipts/plugin-<name>-receipt.json

# 4. Check if remediate-violations.sh can consume it
bash .claude/scripts/remediate-violations.sh

# 5. Verify remediation was applied
git diff HEAD~1
```

### Step 6: Document & Commit

- [ ] Add adapter location to `.claude/audit/plugin-adaptation-guide.md`
- [ ] Document any special configuration in `.claude/config/<plugin>.toml`
- [ ] Add test results to `.claude/audit/plugin-<name>-adapter.test.log`
- [ ] Commit with clear message: `Adapt plugin-<name> for autonomous integration`

---

## HyperStandardsValidator Case Study

### The Challenge

**HyperStandardsValidator** is a Java validator that detects H-phase violations (TODO, MOCK, STUB, etc.). It:
- Exists as a Java class in the codebase
- Takes a path to generated code as input
- Returns violations via exceptions or return codes
- Produces GuardReceipt objects (not JSON)

**Problem**: Doesn't directly integrate with the autonomous framework because:
1. Returns Java objects, not JSON
2. Needs Maven to invoke (not a simple shell call)
3. No standard error routing

### The Solution

Created **plugin-hyperstandards-validator-adapter.sh** that:

**1. Wraps the Java invocation**:
```bash
mvn -q exec:java \
    -Dexec.mainClass="org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator" \
    -Dexec.args="${EMIT_DIR}"
```

**2. Parses the output** to extract violations:
```bash
grep -c "H_TODO\|H_MOCK\|H_STUB..." < violation_output
```

**3. Creates standardized receipt JSON**:
```json
{
  "phase": "H_GUARDS",
  "timestamp": "2026-03-04T12:34:56Z",
  "plugin": "HyperStandardsValidator",
  "violations_count": 3,
  "violations": [...],
  "status": "RED",
  "exit_code": 2
}
```

**4. Integrates with decision-engine.sh**:
```
Error detected → adapter invoked → receipt created →
decision-engine routes to yawl-engineer or yawl-reviewer
```

### Code Walkthrough

**Initialization & Validation**:
```bash
# Paths
RECEIPTS_DIR="${PROJECT_ROOT}/.claude/receipts"
EMIT_DIR="${EMIT_DIR:-${PROJECT_ROOT}/yawl-engine/emit}"

# Check environment
validate_emit_dir() {
    [[ -d "${EMIT_DIR}" ]] && find ... *.java || return 1
}
check_maven() {
    command -v mvn &>/dev/null || return 1
}
```

**Plugin Invocation**:
```bash
run_hyperstandards_validator() {
    mvn -q exec:java \
        -Dexec.mainClass="${VALIDATE_CLASS}" \
        -Dexec.args="${EMIT_DIR}"
    # Returns: output lines with violations
}
```

**Output Parsing**:
```bash
parse_validator_output() {
    # Extract violations using grep + jq
    echo "${validator_output}" | grep "H_" | jq -R '{
        pattern: (match("[A-Z_]+") | .string),
        line: (match("[0-9]+") | .string),
        content: .
    }'
}
```

**Receipt Creation**:
```bash
create_receipt() {
    local violations_json="$1"
    local violation_count=$(echo "$violations_json" | jq 'length')
    local status=$([[ ${violation_count} -gt 0 ]] && echo "RED" || echo "GREEN")

    cat <<EOF
{
      "phase": "H_GUARDS",
      "status": "${status}",
      "violations_count": ${violation_count},
      ...
    }
EOF
}
```

### Integration Results

**Input**: Error receipt from analyze-errors.sh with `error_type: "H_TODO"`
**Process**: Adapter runs HyperStandardsValidator
**Output**: Receipt JSON with violations
**Routing**: decision-engine.sh routes to yawl-engineer (Tier 1 agent)
**Remediation**: remediate-violations.sh removes TODO comments or throws exceptions

**Test Results**:
- ✅ Adapter script exists and is executable
- ✅ Handles missing emit directory gracefully
- ✅ Receipt JSON is valid and well-formed
- ✅ Status matches exit code (GREEN=0, RED=2)
- ✅ Violations array contains structured data
- ✅ Recommendations guide remediation

---

## Replication Checklist

### Before Adaptation

- [ ] **Research phase** (30 min)
  - [ ] Read plugin documentation
  - [ ] Identify input/output contracts
  - [ ] Find existing tests
  - [ ] Locate in audit/plugin-analysis-summary.md

- [ ] **Design phase** (30 min)
  - [ ] Sketch adapter architecture
  - [ ] Define receipt schema
  - [ ] Identify error cases
  - [ ] Plan test scenarios

### During Adaptation

- [ ] **Implementation** (1-2 hours)
  - [ ] Create `plugin-<name>-adapter.sh`
  - [ ] Add configuration handling
  - [ ] Implement plugin invocation
  - [ ] Add output parsing
  - [ ] Create receipt generation

- [ ] **Testing** (1-2 hours)
  - [ ] Create test file
  - [ ] Write 5-7 test cases
  - [ ] Run against real plugin
  - [ ] Verify receipt JSON
  - [ ] Test error paths

- [ ] **Integration** (30 min)
  - [ ] Add to audit inventory
  - [ ] Register in decision-engine.sh
  - [ ] Test full chain (analyze → route → remediate)
  - [ ] Document special config

### Before Commit

- [ ] **Code Review**
  - [ ] Check for H-Guard violations (no TODOs, mocks)
  - [ ] Run dx.sh all if possible
  - [ ] Verify error handling
  - [ ] Check JSON schema compliance

- [ ] **Documentation**
  - [ ] Add entry to PLUGIN-ADAPTATION-GUIDE.md
  - [ ] Document non-obvious decisions
  - [ ] Include example input/output
  - [ ] List known limitations

- [ ] **Commit**
  - [ ] git add .claude/adapters/plugin-<name>-*
  - [ ] git commit -m "Adapt plugin-<name> for autonomous integration"
  - [ ] git push origin <branch>

---

## Common Pitfalls

### Pitfall 1: Plugin Doesn't Expose Errors as Data

**Problem**: Plugin logs errors but doesn't return structured data

**Solution**:
1. Parse stderr/stdout using grep + sed
2. Infer violation type from error message pattern
3. Use jq to structure into receipt

**Example**:
```bash
# Plugin outputs: "ERROR: TODO marker at line 42"
# Solution:
plugin_output | grep "ERROR:" | sed 's/.*line //' | jq -R '{
    pattern: "H_TODO",
    line: .,
    severity: "FAIL"
}'
```

### Pitfall 2: Plugin Needs Custom Configuration

**Problem**: Plugin requires config file or environment variable

**Solution**:
1. Create `.claude/config/plugin-<name>.toml`
2. Load in adapter before invocation
3. Pass to plugin as needed

**Example**:
```bash
# Load config
if [[ -f "${CONFIG_FILE}" ]]; then
    export PLUGIN_CONFIG=$(cat "${CONFIG_FILE}")
fi

# Pass to plugin
mvn exec:java -Dconfig="${PLUGIN_CONFIG}" ...
```

### Pitfall 3: Plugin Blocks on I/O

**Problem**: Plugin waits for input or network, causing hangs

**Solution**:
1. Add timeout to adapter
2. Use `timeout` command
3. Provide fallback/default behavior

**Example**:
```bash
timeout 30s mvn exec:java ... || {
    [[ $? -eq 124 ]] && echo "Plugin timed out"
    exit 1
}
```

### Pitfall 4: Plugin Output Format is Inconsistent

**Problem**: Output varies based on plugin version or environment

**Solution**:
1. Test with multiple plugin versions
2. Create flexible parsing (regex alternatives)
3. Document assumptions in comments

**Example**:
```bash
# Handles both old and new output formats
parse_output() {
    # Try new format first
    grep -E "pattern:[A-Z_]+" || \
    # Fall back to old format
    grep -E "[A-Z_]+ at line [0-9]+"
}
```

### Pitfall 5: Receipt JSON Doesn't Match Schema

**Problem**: Omitted required fields or wrong data types

**Solution**:
1. Validate JSON with jq before writing
2. Include all required fields (even if null)
3. Use consistent data types (numbers as numbers, not strings)

**Example**:
```bash
create_receipt() {
    local receipt=$( ... )

    # Validate schema
    if ! jq -e '.status and .exit_code and .violations' <<< "$receipt"; then
        echo "ERROR: Receipt missing required fields" >&2
        exit 1
    fi

    echo "$receipt"
}
```

---

## Troubleshooting

### Adapter Exits with Exit Code 1 (Transient Error)

**Likely causes**:
- Maven is not installed
- Plugin jar not found
- Network/file system issue

**Debug steps**:
1. Run adapter manually with debug: `bash -x plugin-adapter.sh`
2. Check Maven installation: `mvn -v`
3. Verify plugin classpath
4. Check disk space: `df -h`

### Receipt JSON is Invalid

**Debug**:
```bash
# Check JSON syntax
cat .claude/receipts/plugin-receipt.json | jq empty

# Pretty-print for inspection
cat .claude/receipts/plugin-receipt.json | jq '.'
```

**Common issues**:
- Unescaped quotes in violation content
- Missing commas in array
- Non-ASCII characters

**Fix**: Pipe suspicious values through jq's `@json`:
```bash
cat <<EOF | jq -s '.[0]'
{
  "content": $(echo "$violation_content" | jq -R '@json')
}
EOF
```

### Plugin Violations Not Being Remediated

**Debug steps**:
1. Check receipt status: `jq '.status' receipt.json` → should be "RED"
2. Check violations count: `jq '.violations | length'` → should be > 0
3. Check decision-engine routing: `grep plugin-name decision.log`
4. Verify remediate-violations.sh understands violation type

### Receipt is Stuck in GREEN but Violations Exist

**Cause**: Parser didn't recognize violation patterns

**Solution**:
1. Update grep patterns to match actual plugin output
2. Run plugin manually and examine output
3. Adjust parser regex

**Example**:
```bash
# If plugin outputs "H_STUB: Empty return"
parse_output() {
    grep -oE "H_[A-Z_]+:" # Changed from grep "H_" alone
}
```

---

## Next Plugins to Adapt

Based on Tier classification from Phase 3, recommend this order:

### Phase 5 (Next 6-8 hours):
1. **YSpecificationValidator** (8/10 score)
   - Spec validation
   - Input: YAWL spec files
   - Effort: 2-3 hours
   - Blocker: Low

2. **YCoreDataValidator** (8/10 score)
   - Core data validation
   - Input: Data objects
   - Effort: 1-2 hours
   - Blocker: Low

3. **YawlA2AServer** (9/10 score)
   - A2A protocol integration
   - Input: A2A messages
   - Effort: 3-4 hours
   - Blocker: Medium (protocol format)

### Phase 6 (Following 10-15 hours):
4. **JsonSchemaValidator** (8/10)
5. **SchemaValidator** (8/10)
6. **ApiKeyValidator** (7/10)
7. **JwtValidator** (7/10)

### Phase 7+ (Long-term roadmap):
- MCP tool wrapping (50+ tools, 2h each = 100h total)
- SAFe agents (6 agents, 3h each = 18h)
- Legacy interfaces (8 interfaces, 2.5h each = 20h)

---

## Resources

### Reference Files
- **Adapter inventory**: `.claude/audit/plugin-inventory.md`
- **Analysis summary**: `.claude/audit/plugin-analysis-summary.md`
- **Tier classification**: `.claude/audit/tier-classification.md`
- **Existing adapters**: `.claude/adapters/plugin-*-adapter.sh`

### Autonomous Framework
- **Error detection**: `.claude/scripts/analyze-errors.sh`
- **Remediation**: `.claude/scripts/remediate-violations.sh`
- **Routing**: `.claude/scripts/decision-engine.sh`
- **Team coordination**: `.claude/scripts/activate-permanent-team.sh`

### Guidelines
- **YAWL standards**: `CLAUDE.md` (ROOT AXIOM, σ AUTONOMY sections)
- **H-Guards rules**: `.claude/rules/validation-phases/H-GUARDS-*.md`
- **Q-Invariants rules**: `.claude/rules/validation-phases/Q-INVARIANTS-*.md`
- **Shell conventions**: `.claude/rules/shell-conventions.md`

---

## Summary

The plugin adaptation pattern is:

1. **Understand** the plugin contract (input, output, errors)
2. **Design** an adapter shell script wrapper
3. **Implement** with standardized receipt JSON output
4. **Test** with mock and real data
5. **Integrate** with decide-engine.sh routing
6. **Document** for replication

**Time per plugin**: 1-4 hours depending on complexity

**Impact**: Transforms individual plugins into autonomous agents that:
- ✅ Detect issues automatically
- ✅ Produce structured error receipts
- ✅ Route to appropriate agents
- ✅ Enable automated remediation

**Next plugin to adapt**: YSpecificationValidator (2-3 hours, medium complexity)

---

**End of Plugin Adaptation Guide**
