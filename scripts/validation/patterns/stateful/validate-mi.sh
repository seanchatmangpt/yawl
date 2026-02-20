#!/bin/bash

# Validate YAWL Multi-Instance Patterns with Database Verification (WCP-12 to WCP-17)
# Multi-Instance, Sequential, Parallel, Iterative, etc.

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL Multi-Instance Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions
test_wcp12_sequential_mi() {
    log_info "Testing WCP-12: Sequential Multi-Instance Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp12_sequential.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-12-Sequential-MI-Stateful">
    <name>Sequential Multi-Instance Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp12_sequential.xml" "WCP-12-Sequential-MI-Stateful"; then
        yawl_launch_case "WCP-12-Sequential-MI-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-012")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp13_parallel_mi() {
    log_info "Testing WCP-13: Parallel Multi-Instance Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp13_parallel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-13-Parallel-MI-Stateful">
    <name>Parallel Multi-Instance Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp13_parallel.xml" "WCP-13-Parallel-MI-Stateful"; then
        yawl_launch_case "WCP-13-Parallel-MI-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-013")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp14_iterative() {
    log_info "Testing WCP-14: Iterative Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp14_iterative.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-14-Iterative-Stateful">
    <name>Iterative Pattern (Stateful)</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Condition"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Condition">
          <name>Condition</name>
          <split code="xor"/>
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="end"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Join">
          <name>Join</name>
          <join code="xor"/>
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp14_iterative.xml" "WCP-14-Iterative-Stateful"; then
        yawl_launch_case "WCP-14-Iterative-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-014")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp15_milestone() {
    log_info "Testing WCP-15: Milestone Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp15_milestone.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-15-Milestone-Stateful">
    <name>Milestone Pattern (Stateful)</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Milestone"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <milestone id="Milestone">
          <name>Milestone</name>
          <flowConstraint><nextElementRef id="TaskC"/></flowConstraint>
        </milestone>
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

    if yawl_upload_spec "/tmp/wcp15_milestone.xml" "WCP-15-Milestone-Stateful"; then
        yawl_launch_case "WCP-15-Milestone-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-015")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp16_critical_section() {
    log_info "Testing WCP-16: Critical Section Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp16_critical.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-16-Critical-Section-Stateful">
    <name>Critical Section Pattern (Stateful)</name>
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
          <flowsInto><nextElementRef id="Critical"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="Critical">
          <name>Critical Section</name>
          <exclusiveAccess="true"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <gateway id="Join">
          <name>Join</name>
          <join code="or"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp16_critical.xml" "WCP-16-Critical-Section-Stateful"; then
        yawl_launch_case "WCP-16-Critical-Section-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-016")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp17_compensation() {
    log_info "Testing WCP-17: Compensation Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp17_comp.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-17-Compensation-Stateful">
    <name>Compensation Pattern (Stateful)</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Compensation"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="Compensation">
          <name>Compensation Task</name>
          <compensationTask="true"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp17_comp.xml" "WCP-17-Compensation-Stateful"; then
        yawl_launch_case "WCP-17-Compensation-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-017")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

# Run tests
run_mi_tests() {
    test_wcp12_sequential_mi && log_test "PASS" "Sequential MI pattern" "WCP-12"
    test_wcp13_parallel_mi && log_test "PASS" "Parallel MI pattern" "WCP-13"
    test_wcp14_iterative && log_test "PASS" "Iterative pattern" "WCP-14"
    test_wcp15_milestone && log_test "PASS" "Milestone pattern" "WCP-15"
    test_wcp16_critical_section && log_test "PASS" "Critical section pattern" "WCP-16"
    test_wcp17_compensation && log_test "PASS" "Compensation pattern" "WCP-17"
}

# Main execution
main() {
    start_time=$(date +%s)

    run_mi_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "Multi-Instance Patterns Validation (Stateful) Complete"
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
