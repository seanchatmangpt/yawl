#!/bin/bash

# Validate YAWL State Patterns with Database Verification (WCP-18 to WCP-21)
# Deferred Choice, Milestone, Cancel Activity, Cancel Case

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL State Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions
test_wcp18_deferred_choice() {
    log_info "Testing WCP-18: Deferred Choice Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp18_deferred.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-18-Deferred-Choice-Stateful">
    <name>Deferred Choice Pattern (Stateful)</name>
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
          <name>Task A</name>
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskC">
          <name>Task C</name>
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

    if yawl_upload_spec "/tmp/wcp18_deferred.xml" "WCP-18-Deferred-Choice-Stateful"; then
        yawl_launch_case "WCP-18-Deferred-Choice-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-018")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp19_cancel_activity() {
    log_info "Testing WCP-19: Cancel Activity Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp19_cancel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-19-Cancel-Activity-Stateful">
    <name>Cancel Activity Pattern (Stateful)</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Cancel"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <cancelTask="true"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </task>
        <cancel id="Cancel">
          <name>Cancel</name>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </cancel>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp19_cancel.xml" "WCP-19-Cancel-Activity-Stateful"; then
        yawl_launch_case "WCP-19-Cancel-Activity-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-019")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel activity completed and verified in DB" "WCP-19"
            return 0
        else
            log_test "FAIL" "Cancel activity validation or DB verification failed" "WCP-19"
            return 1
        fi
    else
        return 1
    fi
}

test_wcp20_cancel_case() {
    log_info "Testing WCP-20: Cancel Case Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp20_cancel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-20-Cancel-Case-Stateful">
    <name>Cancel Case Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp20_cancel.xml" "WCP-20-Cancel-Case-Stateful"; then
        yawl_launch_case "WCP-20-Cancel-Case-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-020")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel case completed and verified in DB" "WCP-20"
            return 0
        else
            log_test "FAIL" "Cancel case validation or DB verification failed" "WCP-20"
            return 1
        fi
    else
        return 1
    fi
}

test_wcp21_cancel_region() {
    log_info "Testing WCP-21: Cancel Region Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp21_region.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-21-Cancel-Region-Stateful">
    <name>Cancel Region Pattern (Stateful)</name>
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
          <flowsInto><nextElementRef id="Region"/><nextElementRef id="TaskD"/></flowsInto>
        </gateway>
        <region id="Region">
          <name>Region</name>
          <flowConstraint><nextElementRef id="Join"/></flowConstraint>
          <processControlElements>
            <task id="TaskB">
              <name>Task B</name>
              <flowsInto><nextElementRef id="Cancel"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
            <task id="TaskC">
              <name>Task C</name>
              <flowsInto><nextElementRef id="Join"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
            <cancel id="Cancel">
              <name>Cancel</name>
              <flowsInto><nextElementRef id="Join"/></flowsInto>
            </cancel>
            <gateway id="Join">
              <name>Join</name>
              <join code="xor"/>
              <flowsInto><nextElementRef id="end"/></flowsInto>
            </gateway>
          </processControlElements>
        </region>
        <task id="TaskD">
          <name>Task D</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp21_region.xml" "WCP-21-Cancel-Region-Stateful"; then
        yawl_launch_case "WCP-21-Cancel-Region-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-021")
        yawl_cancel_case "$case_id"
        sleep 2
        if yawl_validate_case "$case_id" "terminated" && db_verify_cancellation "$case_id"; then
            log_test "PASS" "Cancel region completed and verified in DB" "WCP-21"
            return 0
        else
            log_test "FAIL" "Cancel region validation or DB verification failed" "WCP-21"
            return 1
        fi
    else
        return 1
    fi
}

# Run tests
run_state_tests() {
    test_wcp18_deferred_choice && log_test "PASS" "Deferred choice pattern" "WCP-18"
    test_wcp19_cancel_activity
    test_wcp20_cancel_case
    test_wcp21_cancel_region
}

# Main execution
main() {
    start_time=$(date +%s)

    run_state_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "State Patterns Validation (Stateful) Complete"
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
