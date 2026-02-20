#!/bin/bash

# Validate YAWL Branching Patterns with Database Verification (WCP-06 to WCP-11)
# Multi-Choice, Synchronous Merge, Discriminator, Multi-Merge, Non-Exclusive Split

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/../../docker/api-helpers.sh"
source "$(dirname "$0")/lib/db-helpers.sh"

log_section "YAWL Branching Patterns Validation - Stateful"
echo

# Initialize validation
yawl_init_validation
yawl_connect
db_init_test

# Test functions
test_wcp06_multi_choice() {
    log_info "Testing WCP-06: Multi-Choice Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp06_multi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-06-Multi-Choice-Stateful">
    <name>Multi-Choice Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp06_multi.xml" "WCP-06-Multi-Choice-Stateful"; then
        yawl_launch_case "WCP-06-Multi-Choice-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-006")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp07_sync_merge() {
    log_info "Testing WCP-07: Synchronous Merge Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp07_sync.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-07-Sync-Merge-Stateful">
    <name>Synchronous Merge Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp07_sync.xml" "WCP-07-Sync-Merge-Stateful"; then
        yawl_launch_case "WCP-07-Sync-Merge-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-007")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp08_discriminator() {
    log_info "Testing WCP-08: Discriminator Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp08_disc.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-08-Discriminator-Stateful">
    <name>Discriminator Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp08_disc.xml" "WCP-08-Discriminator-Stateful"; then
        yawl_launch_case "WCP-08-Discriminator-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-008")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp09_multi_merge() {
    log_info "Testing WCP-09: Multi-Merge Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp09_multi.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-09-Multi-Merge-Stateful">
    <name>Multi-Merge Pattern (Stateful)</name>
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
        <task id="TaskC">
          <flowsInto><nextElementRef id="Merge"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskD">
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

    if yawl_upload_spec "/tmp/wcp09_multi.xml" "WCP-09-Multi-Merge-Stateful"; then
        yawl_launch_case "WCP-09-Multi-Merge-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-009")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

test_wcp11_non_or_split() {
    log_info "Testing WCP-11: Non-Exclusive Split Pattern (Stateful)"

    cat << 'EOF' > /tmp/wcp11_non.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-11-Non-Exclusive-Split-Stateful">
    <name>Non-Exclusive Split Pattern (Stateful)</name>
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

    if yawl_upload_spec "/tmp/wcp11_non.xml" "WCP-11-Non-Exclusive-Split-Stateful"; then
        yawl_launch_case "WCP-11-Non-Exclusive-Split-Stateful"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-011")
        yawl_complete_case "$case_id"
        yawl_validate_case "$case_id" "complete" && db_verify_persistence "$case_id"
    else
        return 1
    fi
}

# Run tests
run_branching_tests() {
    test_wcp06_multi_choice && log_test "PASS" "Multi-choice pattern" "WCP-06"
    test_wcp07_sync_merge && log_test "PASS" "Sync merge pattern" "WCP-07"
    test_wcp08_discriminator && log_test "PASS" "Discriminator pattern" "WCP-08"
    test_wcp09_multi_merge && log_test "PASS" "Multi-merge pattern" "WCP-09"
    test_wcp11_non_or_split && log_test "PASS" "Non-or split pattern" "WCP-11"
}

# Main execution
main() {
    start_time=$(date +%s)

    run_branching_tests

    # Generate report
    duration=$(get_duration "$start_time")
    log_header "Branching Patterns Validation (Stateful) Complete"
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
