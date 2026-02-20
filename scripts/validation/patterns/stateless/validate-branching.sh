#!/bin/bash

# ==========================================================================
# validate-branching.sh - YAWL Branching Patterns (WCP-06 to WCP-11)
#
# Patterns: Multi-Choice, Sync Merge, Discriminator, Multi-Merge,
#           Structured Discriminator, Non-Exclusive Split
# ==========================================================================

set -euo pipefail

# Resolve script location and source helpers
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

source "${VALIDATION_DIR}/lib/common.sh"
source "${VALIDATION_DIR}/docker/api-helpers.sh"

# Track results
declare -a PATTERNS_PASSED=()
declare -a PATTERNS_FAILED=()

# -------------------------------------------------------------------------
# WCP-06: Multi-Choice Pattern
# -------------------------------------------------------------------------
test_wcp06_multi_choice() {
    log_info "Testing WCP-06: Multi-Choice Pattern"

    cat << 'EOF' > /tmp/wcp06_multi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-06-Multi-Choice">
    <name>Multi-Choice Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Choice"/></flowsInto>
        </inputCondition>
        <gateway id="Choice">
          <name>Choice</name>
          <split code="or"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/><nextElementRef id="TaskC"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Merge">
          <name>Merge</name>
          <join code="and"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp06_multi.xml" "WCP-06-Multi-Choice"; then
        yawl_launch_case "WCP-06-Multi-Choice"
        yawl_complete_case "CASE-006"
        yawl_validate_case "CASE-006" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-07: Synchronous Merge Pattern
# -------------------------------------------------------------------------
test_wcp07_sync_merge() {
    log_info "Testing WCP-07: Synchronous Merge Pattern"

    cat << 'EOF' > /tmp/wcp07_sync.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-07-Sync-Merge">
    <name>Synchronous Merge Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Split"/></flowsInto>
        </inputCondition>
        <gateway id="Split">
          <name>Split</name>
          <split code="and"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Merge">
          <name>Merge</name>
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp07_sync.xml" "WCP-07-Sync-Merge"; then
        yawl_launch_case "WCP-07-Sync-Merge"
        yawl_complete_case "CASE-007"
        yawl_validate_case "CASE-007" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-08: Discriminator Pattern
# -------------------------------------------------------------------------
test_wcp08_discriminator() {
    log_info "Testing WCP-08: Discriminator Pattern"

    cat << 'EOF' > /tmp/wcp08_disc.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-08-Discriminator">
    <name>Discriminator Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Choice"/></flowsInto>
        </inputCondition>
        <gateway id="Choice">
          <name>Choice</name>
          <split code="xor"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/><nextElementRef id="TaskC"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Disc"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Disc"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <flowsInto><nextElementRef id="Disc"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Disc">
          <name>Discriminator</name>
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp08_disc.xml" "WCP-08-Discriminator"; then
        yawl_launch_case "WCP-08-Discriminator"
        yawl_complete_case "CASE-008"
        yawl_validate_case "CASE-008" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-09: Multi-Merge Pattern
# -------------------------------------------------------------------------
test_wcp09_multi_merge() {
    log_info "Testing WCP-09: Multi-Merge Pattern"

    cat << 'EOF' > /tmp/wcp09_multi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-09-Multi-Merge">
    <name>Multi-Merge Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Split"/></flowsInto>
        </inputCondition>
        <gateway id="Split">
          <name>Split</name>
          <split code="or"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Merge">
          <name>Merge</name>
          <join code="or"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp09_multi.xml" "WCP-09-Multi-Merge"; then
        yawl_launch_case "WCP-09-Multi-Merge"
        yawl_complete_case "CASE-009"
        yawl_validate_case "CASE-009" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-10: Structured Discriminator Pattern
# -------------------------------------------------------------------------
test_wcp10_structured_discriminator() {
    log_info "Testing WCP-10: Structured Discriminator Pattern"

    cat << 'EOF' > /tmp/wcp10_struct.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-10-Structured-Discriminator">
    <name>Structured Discriminator Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Split"/></flowsInto>
        </inputCondition>
        <gateway id="Split">
          <name>Split</name>
          <split code="and"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Disc"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Disc"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Disc">
          <name>Discriminator</name>
          <join code="discriminator" threshold="1"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp10_struct.xml" "WCP-10-Structured-Discriminator"; then
        yawl_launch_case "WCP-10-Structured-Discriminator"
        yawl_complete_case "CASE-010"
        yawl_validate_case "CASE-010" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-11: Non-Exclusive Split Pattern (Parallel Split)
# -------------------------------------------------------------------------
test_wcp11_non_exclusive_split() {
    log_info "Testing WCP-11: Non-Exclusive Split Pattern"

    cat << 'EOF' > /tmp/wcp11_non.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-11-Non-Exclusive-Split">
    <name>Non-Exclusive Split Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <flowsInto><nextElementRef id="Split"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Split">
          <name>Split</name>
          <split code="and"/>
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="TaskC"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Join">
          <name>Join</name>
          <join code="and"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp11_non.xml" "WCP-11-Non-Exclusive-Split"; then
        yawl_launch_case "WCP-11-Non-Exclusive-Split"
        yawl_complete_case "CASE-011"
        yawl_validate_case "CASE-011" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# Test Runner
# -------------------------------------------------------------------------
run_pattern_test() {
    local pattern_name="$1"
    local test_func="$2"

    if $test_func; then
        log_test "PASS" "${pattern_name} pattern completed" "$pattern_name"
        PATTERNS_PASSED+=("$pattern_name")
        return 0
    else
        log_test "FAIL" "${pattern_name} pattern failed" "$pattern_name"
        PATTERNS_FAILED+=("$pattern_name")
        return 1
    fi
}

run_branching_tests() {
    run_pattern_test "WCP-06" test_wcp06_multi_choice || true
    run_pattern_test "WCP-07" test_wcp07_sync_merge || true
    run_pattern_test "WCP-08" test_wcp08_discriminator || true
    run_pattern_test "WCP-09" test_wcp09_multi_merge || true
    run_pattern_test "WCP-10" test_wcp10_structured_discriminator || true
    run_pattern_test "WCP-11" test_wcp11_non_exclusive_split || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Branching Patterns Validation (WCP-06 to WCP-11)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all branching pattern tests
    run_branching_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Branching Patterns Validation Complete"
    echo "Duration: ${duration}s"
    echo "Passed: ${#PATTERNS_PASSED[@]}"
    echo "Failed: ${#PATTERNS_FAILED[@]}"
    echo

    # Cleanup
    yawl_disconnect
    rm -f /tmp/wcp*.xml 2>/dev/null || true

    # Return exit code based on test results
    if [[ ${#PATTERNS_FAILED[@]} -gt 0 ]]; then
        return 1
    fi
    return 0
}

main "$@"
