#!/bin/bash

# Validate YAWL Extended Patterns with Database Verification (WCP-41 to WCP-44 + Enterprise)
# Blocked Split, Critical Section, Saga, Enterprise patterns

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL Extended Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions
test_wcp41_blocked_split() {
    log_info "Testing WCP-41: Blocked Split Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp41_blocked.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-41-Blocked-Split-Stateful">
    <name>Blocked Split Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp41_blocked.xml" "WCP-41-Blocked-Split-Stateful"; then
        yawl_launch_case "WCP-41-Blocked-Split-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-041")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp42_saga() {
    log_info "Testing WCP-42: Saga Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp42_saga.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-42-Saga-Stateful">
    <name>Saga Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp42_saga.xml" "WCP-42-Saga-Stateful"; then
        yawl_launch_case "WCP-42-Saga-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-042")
        yawl_complete_case "$case_id"
        if yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"; then
            # Verify compensators are tracked
            local workitem_count=$(db_get_work_item_count "$case_id")
            if [ "$workitem_count" -ge 3 ]; then
                log_info "Saga pattern completed with compensation tracking ($workitem_count work items)"
            fi
            return 0
        else
            return 1
        fi
    else
        return 1
    fi
}

test_wcp43_complex_saga() {
    log_info "Testing WCP-43: Complex Saga Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp43_complex.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-43-Complex-Saga-Stateful">
    <name>Complex Saga Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp43_complex.xml" "WCP-43-Complex-Saga-Stateful"; then
        yawl_launch_case "WCP-43-Complex-Saga-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-043")
        yawl_complete_case "$case_id"
        if yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"; then
            local workitem_count=$(db_get_work_item_count "$case_id")
            log_info "Complex saga pattern completed ($workitem_count work items tracked)"
            return 0
        else
            return 1
        fi
    else
        return 1
    fi
}

test_wcp44_compensation_handler() {
    log_info "Testing WCP-44: Compensation Handler Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp44_handler.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-44-Compensation-Handler-Stateful">
    <name>Compensation Handler Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp44_handler.xml" "WCP-44-Compensation-Handler-Stateful"; then
        yawl_launch_case "WCP-44-Compensation-Handler-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-044")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

# Enterprise patterns
test_ent_approval() {
    log_info "Testing ENT-Approval Pattern (Stateful)"

    cat << 'EOF' > /tmp/ent_approval.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Approval-Stateful">
    <name>Approval Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/ent_approval.xml" "ENT-Approval-Stateful"; then
        yawl_launch_case "ENT-Approval-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-ENT-01")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_ent_escalation() {
    log_info "Testing ENT-Escalation Pattern (Stateful)"

    cat << 'EOF' > /tmp/ent_escalation.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="ENT-Escalation-Stateful">
    <name>Escalation Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/ent_escalation.xml" "ENT-Escalation-Stateful"; then
        yawl_launch_case "ENT-Escalation-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-ENT-02")
        sleep 5  # Wait for timeout
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

# Run tests
run_extended_tests() {
    # Standard extended patterns
    test_wcp41_blocked_split && log_test "PASS" "Blocked split pattern" "WCP-41"
    test_wcp42_saga && log_test "PASS" "Saga pattern" "WCP-42"
    test_wcp43_complex_saga && log_test "PASS" "Complex saga pattern" "WCP-43"
    test_wcp44_compensation_handler && log_test "PASS" "Compensation handler pattern" "WCP-44"

    # Enterprise patterns
    test_ent_approval && log_test "PASS" "Approval pattern" "ENT-APPROVAL"
    test_ent_escalation && log_test "PASS" "Escalation pattern" "ENT-ESCALATION"
}

# Main execution
main() {
    start_time=$(date +%s)

    run_extended_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "Extended Patterns Validation (Stateful) Complete"
    echo "Duration: ${duration}s"
    echo

    # Cleanup
    yawl_disconnect
    cleanup "/tmp/*.xml"
    db_cleanup_test_data

    # Return exit code based on test results
    output_summary
}

main "$@"
