#!/bin/bash

# ==========================================================================
# validate-extended.sh - YAWL Extended/Enterprise Patterns (WCP-41-44, ENT)
#
# Patterns: Blocked Split, Saga, Complex Saga, Compensation Handler,
#           Enterprise: Approval, Escalation, Notification, Delegation
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
# WCP-41: Blocked Split Pattern
# -------------------------------------------------------------------------
test_wcp41_blocked_split() {
    log_info "Testing WCP-41: Blocked Split Pattern"

    cat << 'EOF' > /tmp/wcp41_blocked.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-41-Blocked-Split">
    <name>Blocked Split Pattern</name>
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
          <split code="xor"/>
          <blockedSplit="true"/>
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
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp41_blocked.xml" "WCP-41-Blocked-Split"; then
        yawl_launch_case "WCP-41-Blocked-Split"
        yawl_complete_case "CASE-041"
        yawl_validate_case "CASE-041" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-42: Saga Pattern
# -------------------------------------------------------------------------
test_wcp42_saga() {
    log_info "Testing WCP-42: Saga Pattern"

    cat << 'EOF' > /tmp/wcp42_saga.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-42-Saga">
    <name>Saga Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <compensator="TaskA-Comp"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <compensator="TaskB-Comp"/>
          <flowsInto><nextElementRef id="TaskC"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Task C</name>
          <compensator="TaskC-Comp"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskA-Comp">
          <name>Comp A</name>
          <compensationTask="true"/>
        </task>
        <task id="TaskB-Comp">
          <name>Comp B</name>
          <compensationTask="true"/>
        </task>
        <task id="TaskC-Comp">
          <name>Comp C</name>
          <compensationTask="true"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp42_saga.xml" "WCP-42-Saga"; then
        yawl_launch_case "WCP-42-Saga"
        yawl_complete_case "CASE-042"
        yawl_validate_case "CASE-042" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-43: Complex Saga Pattern
# -------------------------------------------------------------------------
test_wcp43_complex_saga() {
    log_info "Testing WCP-43: Complex Saga Pattern"

    cat << 'EOF' > /tmp/wcp43_complex.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-43-Complex-Saga">
    <name>Complex Saga Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <compensator="TaskA-Comp"/>
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
          <compensator="TaskB-Comp"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Task C</name>
          <compensator="TaskC-Comp"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Join">
          <name>Join</name>
          <join code="or"/>
          <flowsInto><nextElementRef id="TaskD"/></flowsInto>
        </gateway>
        <task id="TaskD">
          <name>Task D</name>
          <compensator="TaskD-Comp"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskA-Comp">
          <name>Comp A</name>
          <compensationTask="true"/>
        </task>
        <task id="TaskB-Comp">
          <name>Comp B</name>
          <compensationTask="true"/>
        </task>
        <task id="TaskC-Comp">
          <name>Comp C</name>
          <compensationTask="true"/>
        </task>
        <task id="TaskD-Comp">
          <name>Comp D</name>
          <compensationTask="true"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp43_complex.xml" "WCP-43-Complex-Saga"; then
        yawl_launch_case "WCP-43-Complex-Saga"
        yawl_complete_case "CASE-043"
        yawl_validate_case "CASE-043" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-44: Compensation Handler Pattern
# -------------------------------------------------------------------------
test_wcp44_compensation_handler() {
    log_info "Testing WCP-44: Compensation Handler Pattern"

    cat << 'EOF' > /tmp/wcp44_handler.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-44-Compensation-Handler">
    <name>Compensation Handler Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <compensator="Handler"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Error"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <error id="Error">
          <name>Error</name>
          <flowsInto><nextElementRef id="Handler"/></flowsInto>
        </error>
        <compensationHandler id="Handler">
          <name>Handler</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </compensationHandler>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp44_handler.xml" "WCP-44-Compensation-Handler"; then
        yawl_launch_case "WCP-44-Compensation-Handler"
        yawl_complete_case "CASE-044"
        yawl_validate_case "CASE-044" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# ENT-APPROVAL: Enterprise Approval Pattern
# -------------------------------------------------------------------------
test_ent_approval() {
    log_info "Testing ENT-Approval: Enterprise Approval Pattern"

    cat << 'EOF' > /tmp/ent_approval.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Approval">
    <name>Enterprise Approval Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Submit"/></flowsInto>
        </inputCondition>
        <task id="Submit">
          <name>Submit Request</name>
          <flowsInto><nextElementRef id="Approval"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="Approval">
          <name>Approval Task</name>
          <flowsInto><nextElementRef id="Decision"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Decision">
          <name>Decision</name>
          <split code="xor"/>
          <flowsInto><nextElementRef id="Approve"/><nextElementRef id="Reject"/></flowsInto>
        </gateway>
        <task id="Approve">
          <name>Approve</name>
          <flowsInto><nextElementRef id="Complete"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="Reject">
          <name>Reject</name>
          <flowsInto><nextElementRef id="Complete"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="Complete">
          <name>Complete</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/ent_approval.xml" "ENT-Approval"; then
        yawl_launch_case "ENT-Approval"
        yawl_complete_case "CASE-ENT-01"
        yawl_validate_case "CASE-ENT-01" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# ENT-ESCALATION: Enterprise Escalation Pattern
# -------------------------------------------------------------------------
test_ent_escalation() {
    log_info "Testing ENT-Escalation: Enterprise Escalation Pattern"

    cat << 'EOF' > /tmp/ent_escalation.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Escalation">
    <name>Enterprise Escalation Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <timeout="PT1M"/>
          <flowsInto><nextElementRef id="Escalation"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <escalation id="Escalation">
          <name>Escalation</name>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </escalation>
        <task id="TaskB">
          <name>Task B (Escalated)</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/ent_escalation.xml" "ENT-Escalation"; then
        yawl_launch_case "ENT-Escalation"
        sleep 5  # Wait for timeout
        yawl_complete_case "CASE-ENT-02"
        yawl_validate_case "CASE-ENT-02" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# ENT-NOTIFICATION: Enterprise Notification Pattern
# -------------------------------------------------------------------------
test_ent_notification() {
    log_info "Testing ENT-Notification: Enterprise Notification Pattern"

    cat << 'EOF' > /tmp/ent_notification.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Notification">
    <name>Enterprise Notification Pattern</name>
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
          <flowsInto><nextElementRef id="Notify"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <notification id="Notify">
          <name>Notification</name>
          <notificationType="alert"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </notification>
        <task id="TaskB">
          <name>Task B</name>
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

    if yawl_upload_spec "/tmp/ent_notification.xml" "ENT-Notification"; then
        yawl_launch_case "ENT-Notification"
        yawl_complete_case "CASE-ENT-03"
        yawl_validate_case "CASE-ENT-03" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# ENT-DELEGATION: Enterprise Delegation Pattern
# -------------------------------------------------------------------------
test_ent_delegation() {
    log_info "Testing ENT-Delegation: Enterprise Delegation Pattern"

    cat << 'EOF' > /tmp/ent_delegation.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Delegation">
    <name>Enterprise Delegation Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Manager Task</name>
          <flowsInto><nextElementRef id="Delegate"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <delegation id="Delegate">
          <name>Delegation</name>
          <delegateTo="team"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </delegation>
        <task id="TaskB">
          <name>Delegated Task</name>
          <flowsInto><nextElementRef id="TaskC"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Review</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/ent_delegation.xml" "ENT-Delegation"; then
        yawl_launch_case "ENT-Delegation"
        yawl_complete_case "CASE-ENT-04"
        yawl_validate_case "CASE-ENT-04" "complete"
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

run_extended_tests() {
    # Standard extended patterns
    run_pattern_test "WCP-41" test_wcp41_blocked_split || true
    run_pattern_test "WCP-42" test_wcp42_saga || true
    run_pattern_test "WCP-43" test_wcp43_complex_saga || true
    run_pattern_test "WCP-44" test_wcp44_compensation_handler || true

    # Enterprise patterns
    run_pattern_test "ENT-APPROVAL" test_ent_approval || true
    run_pattern_test "ENT-ESCALATION" test_ent_escalation || true
    run_pattern_test "ENT-NOTIFICATION" test_ent_notification || true
    run_pattern_test "ENT-DELEGATION" test_ent_delegation || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Extended/Enterprise Patterns Validation (WCP-41-44,ENT)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all extended pattern tests
    run_extended_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Extended/Enterprise Patterns Validation Complete"
    echo "Duration: ${duration}s"
    echo "Passed: ${#PATTERNS_PASSED[@]}"
    echo "Failed: ${#PATTERNS_FAILED[@]}"
    echo

    # Cleanup
    yawl_disconnect
    rm -f /tmp/wcp*.xml /tmp/ent*.xml 2>/dev/null || true

    # Return exit code based on test results
    if [[ ${#PATTERNS_FAILED[@]} -gt 0 ]]; then
        return 1
    fi
    return 0
}

main "$@"
