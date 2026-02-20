#!/bin/bash

# ==========================================================================
# validate-basic.sh - YAWL Basic Control Flow Patterns (WCP-01 to WCP-05)
#
# Patterns: Sequence, Parallel Split, Synchronization, Exclusive Choice,
#           Simple Merge
# ==========================================================================

set -euo pipefail

# Resolve script location and source helpers
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

source "${VALIDATION_DIR}/validation/lib/common.sh"
source "${VALIDATION_DIR}/validation/docker/api-helpers.sh"

# Track results
declare -a PATTERNS_PASSED=()
declare -a PATTERNS_FAILED=()

# -------------------------------------------------------------------------
# WCP-01: Sequence Pattern
# -------------------------------------------------------------------------
test_wcp01_sequence() {
    log_info "Testing WCP-01: Sequence Pattern"

    cat << 'EOF' > /tmp/wcp01_sequence.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-01-Sequence">
    <name>Sequence Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
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

    if yawl_upload_spec "/tmp/wcp01_sequence.xml" "WCP-01-Sequence"; then
        yawl_launch_case "WCP-01-Sequence"
        yawl_complete_case "CASE-001"
        yawl_validate_case "CASE-001" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-02: Parallel Split Pattern
# -------------------------------------------------------------------------
test_wcp02_parallel_split() {
    log_info "Testing WCP-02: Parallel Split Pattern"

    cat << 'EOF' > /tmp/wcp02_parallel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-02-Parallel-Split">
    <name>Parallel Split Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Split"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Split">
          <name>Split</name>
          <split code="and"/>
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="TaskC"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Task C</name>
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

    if yawl_upload_spec "/tmp/wcp02_parallel.xml" "WCP-02-Parallel-Split"; then
        yawl_launch_case "WCP-02-Parallel-Split"
        yawl_complete_case "CASE-002"
        yawl_validate_case "CASE-002" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-03: Synchronization Pattern
# -------------------------------------------------------------------------
test_wcp03_synchronization() {
    log_info "Testing WCP-03: Synchronization Pattern"

    cat << 'EOF' > /tmp/wcp03_sync.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-03-Synchronization">
    <name>Synchronization Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Split"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Split">
          <name>Split</name>
          <split code="or"/>
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="TaskC"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Task C</name>
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

    if yawl_upload_spec "/tmp/wcp03_sync.xml" "WCP-03-Synchronization"; then
        yawl_launch_case "WCP-03-Synchronization"
        yawl_complete_case "CASE-003"
        yawl_validate_case "CASE-003" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-04: Exclusive Choice Pattern
# -------------------------------------------------------------------------
test_wcp04_exclusive_choice() {
    log_info "Testing WCP-04: Exclusive Choice Pattern"

    cat << 'EOF' > /tmp/wcp04_choice.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-04-Exclusive-Choice">
    <name>Exclusive Choice Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Choice"/></flowsInto>
        </inputCondition>
        <gateway id="Choice">
          <name>Choice</name>
          <split code="xor"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
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

    if yawl_upload_spec "/tmp/wcp04_choice.xml" "WCP-04-Exclusive-Choice"; then
        yawl_launch_case "WCP-04-Exclusive-Choice"
        yawl_complete_case "CASE-004"
        yawl_validate_case "CASE-004" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-05: Simple Merge Pattern
# -------------------------------------------------------------------------
test_wcp05_simple_merge() {
    log_info "Testing WCP-05: Simple Merge Pattern"

    cat << 'EOF' > /tmp/wcp05_merge.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-05-Simple-Merge">
    <name>Simple Merge Pattern</name>
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
          <name>Task A</name>
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
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

    if yawl_upload_spec "/tmp/wcp05_merge.xml" "WCP-05-Simple-Merge"; then
        yawl_launch_case "WCP-05-Simple-Merge"
        yawl_complete_case "CASE-005"
        yawl_validate_case "CASE-005" "complete"
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

run_basic_tests() {
    run_pattern_test "WCP-01" test_wcp01_sequence || true
    run_pattern_test "WCP-02" test_wcp02_parallel_split || true
    run_pattern_test "WCP-03" test_wcp03_synchronization || true
    run_pattern_test "WCP-04" test_wcp04_exclusive_choice || true
    run_pattern_test "WCP-05" test_wcp05_simple_merge || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Basic Patterns Validation (WCP-01 to WCP-05)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all basic pattern tests
    run_basic_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Basic Patterns Validation Complete"
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
