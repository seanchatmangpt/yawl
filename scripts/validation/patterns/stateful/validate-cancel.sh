#!/bin/bash

# Validate YAWL Cancellation Patterns with Database Verification (WCP-22 to WCP-25)
# Additional cancellation patterns beyond basic WCP-19-21

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL Cancellation Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions for additional cancellation patterns
test_wcp22_cancel_mi() {
    log_info "Testing WCP-22: Cancel Multi-Instance Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp22_cancel_mi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-22-Cancel-MI-Stateful">
    <name>Cancel Multi-Instance Pattern (Stateful)</name>
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
          <flowConstraint><nextElementRef id="End"/></flowConstraint>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp22_cancel_mi.xml" "WCP-22-Cancel-MI-Stateful"; then
        yawl_launch_case "WCP-22-Cancel-MI-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-022")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel MI pattern completed and verified in DB" "WCP-22"
            return 0
        else
            log_test "FAIL" "Cancel MI pattern validation or DB verification failed" "WCP-22"
            return 1
        fi
    else
        return 1
    fi
}

test_wcp23_cancel_and_complete() {
    log_info "Testing WCP-23: Cancel and Complete Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp23_cancel_comp.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-23-Cancel-Complete-Stateful">
    <name>Cancel and Complete Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp23_cancel_comp.xml" "WCP-23-Cancel-Complete-Stateful"; then
        yawl_launch_case "WCP-23-Cancel-Complete-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-023")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"; then
            log_test "PASS" "Cancel and complete pattern verified in DB" "WCP-23"
            return 0
        else
            log_test "FAIL" "Cancel and complete validation or DB verification failed" "WCP-23"
            return 1
        fi
    else
        return 1
    fi
}

test_wcp24_cancel_n_of_m() {
    log_info "Testing WCP-24: Cancel N-out-of-M Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp24_cancel_n.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-24-Cancel-N-of-M-Stateful">
    <name>Cancel N-out-of-M Pattern (Stateful)</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="MI-Tasks"/></flowsInto>
        </inputCondition>
        <multiTask id="MI-Tasks" multiInstanceCardinality="10" sequential="false">
          <name>MI Tasks</name>
          <join code="xor"/><split code="xor"/>
        </multiTask>
        <cancel id="Cancel-N-of-M">
          <name>Cancel N of M</name>
          <cancelCondition="n_out_of_m"/>
          <flowConstraint><nextElementRef id="End"/></flowConstraint>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp24_cancel_n.xml" "WCP-24-Cancel-N-of-M-Stateful"; then
        yawl_launch_case "WCP-24-Cancel-N-of-M-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-024")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel N-out-of-M pattern verified in DB" "WCP-24"
            return 0
        else
            log_test "FAIL" "Cancel N-out-of-M validation or DB verification failed" "WCP-24"
            return 1
        fi
    else
        return 1
    fi
}

test_wcp25_cancel_condition() {
    log_info "Testing WCP-25: Cancel on Condition Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp25_cond.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-25-Cancel-Condition-Stateful">
    <name>Cancel on Condition Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp25_cond.xml" "WCP-25-Cancel-Condition-Stateful"; then
        yawl_launch_case "WCP-25-Cancel-Condition-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-025")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel condition pattern verified in DB" "WCP-25"
            return 0
        else
            log_test "FAIL" "Cancel condition validation or DB verification failed" "WCP-25"
            return 1
        fi
    else
        return 1
    fi
}

# Run tests
run_cancel_tests() {
    test_wcp22_cancel_mi
    test_wcp23_cancel_and_complete
    test_wcp24_cancel_n_of_m
    test_wcp25_cancel_condition
}

# Main execution
main() {
    start_time=$(date +%s)

    run_cancel_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "Cancellation Patterns Validation (Stateful) Complete"
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
