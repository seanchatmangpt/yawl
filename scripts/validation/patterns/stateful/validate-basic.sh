#!/bin/bash

# Validate YAWL Basic Patterns with Database Verification (WCP-01 to WCP-05)
# Sequence, Parallel Split, Synchronization, Exclusive Choice, Simple Merge

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL Basic Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions
test_wcp01_sequence() {
    log_info "Testing WCP-01: Sequence Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp01_sequence.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-01-Sequence-Stateful">
    <name>Sequence Pattern (Stateful)</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp01_sequence.xml" "WCP-01-Sequence-Stateful"; then
        yawl_launch_case "WCP-01-Sequence-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-001")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp02_parallel_split() {
    log_info "Testing WCP-02: Parallel Split Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp02_parallel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-02-Parallel-Split-Stateful">
    <name>Parallel Split Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp02_parallel.xml" "WCP-02-Parallel-Split-Stateful"; then
        yawl_launch_case "WCP-02-Parallel-Split-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-002")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp03_synchronization() {
    log_info "Testing WCP-03: Synchronization Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp03_sync.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-03-Synchronization-Stateful">
    <name>Synchronization Pattern (Stateful)</name>
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
          <split code="or"/>
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

    if yawl_upload_spec "/tmp/wcp03_sync.xml" "WCP-03-Synchronization-Stateful"; then
        yawl_launch_case "WCP-03-Synchronization-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-003")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp04_exclusive_choice() {
    log_info "Testing WCP-04: Exclusive Choice Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp04_choice.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-04-Exclusive-Choice-Stateful">
    <name>Exclusive Choice Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp04_choice.xml" "WCP-04-Exclusive-Choice-Stateful"; then
        yawl_launch_case "WCP-04-Exclusive-Choice-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-004")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp05_simple_merge() {
    log_info "Testing WCP-05: Simple Merge Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp05_merge.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-05-Simple-Merge-Stateful">
    <name>Simple Merge Pattern (Stateful)</name>
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
          <join code="and"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp05_merge.xml" "WCP-05-Simple-Merge-Stateful"; then
        yawl_launch_case "WCP-05-Simple-Merge-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-005")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

# Run tests
run_basic_tests() {
    test_wcp01_sequence && log_test "PASS" "Sequence pattern" "WCP-01"
    test_wcp02_parallel_split && log_test "PASS" "Parallel split pattern" "WCP-02"
    test_wcp03_synchronization && log_test "PASS" "Synchronization pattern" "WCP-03"
    test_wcp04_exclusive_choice && log_test "PASS" "Exclusive choice pattern" "WCP-04"
    test_wcp05_simple_merge && log_test "PASS" "Simple merge pattern" "WCP-05"
}

# Main execution
main() {
    start_time=$(date +%s)

    run_basic_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "Basic Patterns Validation (Stateful) Complete"
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