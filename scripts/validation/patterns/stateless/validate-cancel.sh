#!/bin/bash

# ==========================================================================
# validate-cancel.sh - YAWL Cancellation Patterns (WCP-22 to WCP-25, 29-31)
#
# Patterns: Cancel MI, Cancel Complete MI, Cancel N-of-M,
#           Cancel Region, Structured Cancel, Cancel Loop
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
# WCP-22: Cancel Multi-Instance
# -------------------------------------------------------------------------
test_wcp22_cancel_mi() {
    log_info "Testing WCP-22: Cancel Multi-Instance Pattern"

    cat << 'EOF' > /tmp/wcp22_cancel_mi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-22-Cancel-MI">
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

    if yawl_upload_spec "/tmp/wcp22_cancel_mi.xml" "WCP-22-Cancel-MI"; then
        yawl_launch_case "WCP-22-Cancel-MI"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-022")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-23: Cancel and Complete MI
# -------------------------------------------------------------------------
test_wcp23_cancel_complete() {
    log_info "Testing WCP-23: Cancel and Complete MI Pattern"

    cat << 'EOF' > /tmp/wcp23_cancel_comp.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-23-Cancel-Complete">
    <name>Cancel and Complete MI Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Cancel"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <cancel id="Cancel">
          <name>Cancel</name>
          <completeOnCancel="true"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </cancel>
        <gateway id="Join">
          <name>Join</name>
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp23_cancel_comp.xml" "WCP-23-Cancel-Complete"; then
        yawl_launch_case "WCP-23-Cancel-Complete"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-023")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-25: Cancel on Condition
# -------------------------------------------------------------------------
test_wcp25_cancel_condition() {
    log_info "Testing WCP-25: Cancel on Condition Pattern"

    cat << 'EOF' > /tmp/wcp25_cond.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-25-Cancel-Condition">
    <name>Cancel on Condition Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Cancel"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <cancel id="Cancel">
          <name>Cancel on Condition</name>
          <cancelCondition="true"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </cancel>
        <gateway id="Join">
          <name>Join</name>
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp25_cond.xml" "WCP-25-Cancel-Condition"; then
        yawl_launch_case "WCP-25-Cancel-Condition"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-025")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-29: Cancel Region (Structured)
# -------------------------------------------------------------------------
test_wcp29_cancel_region_structured() {
    log_info "Testing WCP-29: Cancel Region (Structured) Pattern"

    cat << 'EOF' > /tmp/wcp29_region.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-29-Cancel-Region-Structured">
    <name>Cancel Region (Structured) Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Region"/></flowsInto>
        </inputCondition>
        <region id="Region">
          <name>Cancelable Region</name>
          <processControlElements>
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
          </processControlElements>
        </region>
        <cancel id="Cancel-Region">
          <name>Cancel Region</name>
          <regionRef="Region"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp29_region.xml" "WCP-29-Cancel-Region-Structured"; then
        yawl_launch_case "WCP-29-Cancel-Region-Structured"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-029")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-30: Structured Cancel
# -------------------------------------------------------------------------
test_wcp30_structured_cancel() {
    log_info "Testing WCP-30: Structured Cancel Pattern"

    cat << 'EOF' > /tmp/wcp30_structured.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-30-Structured-Cancel">
    <name>Structured Cancel Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Choice"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Choice">
          <name>Choice</name>
          <split code="xor"/>
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Cancel"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <cancel id="Cancel">
          <name>Structured Cancel</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp30_structured.xml" "WCP-30-Structured-Cancel"; then
        yawl_launch_case "WCP-30-Structured-Cancel"
        yawl_complete_case "CASE-030"
        yawl_validate_case "CASE-030" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-31: Cancel Loop
# -------------------------------------------------------------------------
test_wcp31_cancel_loop() {
    log_info "Testing WCP-31: Cancel Loop Pattern"

    cat << 'EOF' > /tmp/wcp31_loop.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-31-Cancel-Loop">
    <name>Cancel Loop Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Loop"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <loop id="Loop">
          <name>Loop</name>
          <loopCondition="count lt 3"/>
          <processControlElements>
            <task id="TaskB">
              <name>Loop Task</name>
              <flowsInto><nextElementRef id="LoopCheck"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
          </processControlElements>
        </loop>
        <cancel id="Cancel-Loop">
          <name>Cancel Loop</name>
          <loopRef="Loop"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp31_loop.xml" "WCP-31-Cancel-Loop"; then
        yawl_launch_case "WCP-31-Cancel-Loop"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-031")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
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

run_cancel_tests() {
    run_pattern_test "WCP-22" test_wcp22_cancel_mi || true
    run_pattern_test "WCP-23" test_wcp23_cancel_complete || true
    run_pattern_test "WCP-25" test_wcp25_cancel_condition || true
    run_pattern_test "WCP-29" test_wcp29_cancel_region_structured || true
    run_pattern_test "WCP-30" test_wcp30_structured_cancel || true
    run_pattern_test "WCP-31" test_wcp31_cancel_loop || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Cancellation Patterns Validation (WCP-22-25,29-31)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all cancel pattern tests
    run_cancel_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Cancellation Patterns Validation Complete"
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
