#!/bin/bash

# ==========================================================================
# validate-mi.sh - YAWL Multi-Instance Patterns (WCP-12 to WCP-17, 24, 26-27)
#
# Patterns: Sequential MI, Parallel MI, Static MI, Dynamic MI,
#           MI without Sync, MI with prior design, Cancel MI (WCP-24),
#           Structured Synchronizing MI (WCP-26), Structured Partial Join (WCP-27)
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
# WCP-12: Sequential Multi-Instance Pattern
# -------------------------------------------------------------------------
test_wcp12_sequential_mi() {
    log_info "Testing WCP-12: Sequential Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp12_sequential.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-12-Sequential-MI">
    <name>Sequential Multi-Instance Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
        </inputCondition>
        <multiTask id="MI-Tasks" multiInstanceCardinality="3" sequential="true">
          <name>MI Tasks</name>
          <join code="xor"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp12_sequential.xml" "WCP-12-Sequential-MI"; then
        yawl_launch_case "WCP-12-Sequential-MI"
        yawl_complete_case "CASE-012"
        yawl_validate_case "CASE-012" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-13: Parallel Multi-Instance Pattern
# -------------------------------------------------------------------------
test_wcp13_parallel_mi() {
    log_info "Testing WCP-13: Parallel Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp13_parallel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-13-Parallel-MI">
    <name>Parallel Multi-Instance Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
        </inputCondition>
        <multiTask id="MI-Tasks" multiInstanceCardinality="3" sequential="false">
          <name>MI Tasks</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp13_parallel.xml" "WCP-13-Parallel-MI"; then
        yawl_launch_case "WCP-13-Parallel-MI"
        yawl_complete_case "CASE-013"
        yawl_validate_case "CASE-013" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-14: Design Time MI (Static)
# -------------------------------------------------------------------------
test_wcp14_static_mi() {
    log_info "Testing WCP-14: Design Time Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp14_static.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-14-Static-MI">
    <name>Design Time Multi-Instance Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="2" sequential="false">
          <name>Static MI Tasks</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp14_static.xml" "WCP-14-Static-MI"; then
        yawl_launch_case "WCP-14-Static-MI"
        yawl_complete_case "CASE-014"
        yawl_validate_case "CASE-014" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-15: Runtime MI (Dynamic)
# -------------------------------------------------------------------------
test_wcp15_dynamic_mi() {
    log_info "Testing WCP-15: Runtime Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp15_dynamic.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-15-Dynamic-MI">
    <name>Runtime Multi-Instance Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="runtime" sequential="false">
          <name>Dynamic MI Tasks</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp15_dynamic.xml" "WCP-15-Dynamic-MI"; then
        yawl_launch_case "WCP-15-Dynamic-MI"
        yawl_complete_case "CASE-015"
        yawl_validate_case "CASE-015" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-16: Multiple Instances without Synchronization
# -------------------------------------------------------------------------
test_wcp16_mi_no_sync() {
    log_info "Testing WCP-16: MI Without Synchronization Pattern"

    cat << 'EOF' > /tmp/wcp16_nosync.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-16-MI-No-Sync">
    <name>MI Without Synchronization Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="3" sequential="false">
          <name>MI Tasks (No Sync)</name>
          <join code="or"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp16_nosync.xml" "WCP-16-MI-No-Sync"; then
        yawl_launch_case "WCP-16-MI-No-Sync"
        yawl_complete_case "CASE-016"
        yawl_validate_case "CASE-016" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-17: Multiple Instances with Prior Design
# -------------------------------------------------------------------------
test_wcp17_mi_prior_design() {
    log_info "Testing WCP-17: MI with Prior Design Pattern"

    cat << 'EOF' > /tmp/wcp17_prior.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-17-MI-Prior-Design">
    <name>MI with Prior Design Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Design"/></flowsInto>
        </inputCondition>
        <task id="Design">
          <name>Design Task</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="3" sequential="false">
          <name>MI Tasks (Prior Design)</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp17_prior.xml" "WCP-17-MI-Prior-Design"; then
        yawl_launch_case "WCP-17-MI-Prior-Design"
        yawl_complete_case "CASE-017"
        yawl_validate_case "CASE-017" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-24: Cancel Multi-Instance
# -------------------------------------------------------------------------
test_wcp24_cancel_mi() {
    log_info "Testing WCP-24: Cancel Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp24_cancel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-24-Cancel-MI">
    <name>Cancel Multi-Instance Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
        </inputCondition>
        <multiTask id="MI-Tasks" multiInstanceCardinality="5" sequential="false">
          <name>MI Tasks</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <cancel id="Cancel-MI">
          <name>Cancel MI</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp24_cancel.xml" "WCP-24-Cancel-MI"; then
        yawl_launch_case "WCP-24-Cancel-MI"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-024")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-26: Structured Synchronizing MI
# -------------------------------------------------------------------------
test_wcp26_struct_sync_mi() {
    log_info "Testing WCP-26: Structured Synchronizing MI Pattern"

    cat << 'EOF' > /tmp/wcp26_struct.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-26-Struct-Sync-MI">
    <name>Structured Synchronizing MI Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="3" sequential="false">
          <name>Struct Sync MI Tasks</name>
          <join code="and"/><split code="xor"/>
        </multiTask>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp26_struct.xml" "WCP-26-Struct-Sync-MI"; then
        yawl_launch_case "WCP-26-Struct-Sync-MI"
        yawl_complete_case "CASE-026"
        yawl_validate_case "CASE-026" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-27: Structured Partial Join for MI
# -------------------------------------------------------------------------
test_wcp27_partial_join() {
    log_info "Testing WCP-27: Structured Partial Join MI Pattern"

    cat << 'EOF' > /tmp/wcp27_partial.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-27-Partial-Join-MI">
    <name>Structured Partial Join MI Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <multiTask id="MI-Tasks" multiInstanceCardinality="5" sequential="false">
          <name>Partial Join MI Tasks</name>
          <join code="discriminator" threshold="3"/><split code="xor"/>
        </multiTask>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp27_partial.xml" "WCP-27-Partial-Join-MI"; then
        yawl_launch_case "WCP-27-Partial-Join-MI"
        yawl_complete_case "CASE-027"
        yawl_validate_case "CASE-027" "complete"
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

run_mi_tests() {
    run_pattern_test "WCP-12" test_wcp12_sequential_mi || true
    run_pattern_test "WCP-13" test_wcp13_parallel_mi || true
    run_pattern_test "WCP-14" test_wcp14_static_mi || true
    run_pattern_test "WCP-15" test_wcp15_dynamic_mi || true
    run_pattern_test "WCP-16" test_wcp16_mi_no_sync || true
    run_pattern_test "WCP-17" test_wcp17_mi_prior_design || true
    run_pattern_test "WCP-24" test_wcp24_cancel_mi || true
    run_pattern_test "WCP-26" test_wcp26_struct_sync_mi || true
    run_pattern_test "WCP-27" test_wcp27_partial_join || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Multi-Instance Patterns Validation (WCP-12-17,24,26-27)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all MI pattern tests
    run_mi_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Multi-Instance Patterns Validation Complete"
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
