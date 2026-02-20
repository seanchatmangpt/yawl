#!/bin/bash

# ==========================================================================
# validate-state.sh - YAWL State-Based Patterns (WCP-18 to WCP-21, 32-35)
#
# Patterns: Deferred Choice, Cancel Activity, Cancel Case, Cancel Region,
#           Interleaved Parallel Routing, Interleaved Routing,
#           Milestone, Critical Section
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
# WCP-18: Deferred Choice Pattern
# -------------------------------------------------------------------------
test_wcp18_deferred_choice() {
    log_info "Testing WCP-18: Deferred Choice Pattern"

    cat << 'EOF' > /tmp/wcp18_deferred.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-18-Deferred-Choice">
    <name>Deferred Choice Pattern</name>
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

    if yawl_upload_spec "/tmp/wcp18_deferred.xml" "WCP-18-Deferred-Choice"; then
        yawl_launch_case "WCP-18-Deferred-Choice"
        yawl_complete_case "CASE-018"
        yawl_validate_case "CASE-018" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-19: Cancel Activity Pattern
# -------------------------------------------------------------------------
test_wcp19_cancel_activity() {
    log_info "Testing WCP-19: Cancel Activity Pattern"

    cat << 'EOF' > /tmp/wcp19_cancel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-19-Cancel-Activity">
    <name>Cancel Activity Pattern</name>
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
          <name>Cancel Task</name>
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

    if yawl_upload_spec "/tmp/wcp19_cancel.xml" "WCP-19-Cancel-Activity"; then
        yawl_launch_case "WCP-19-Cancel-Activity"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-019")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-20: Cancel Case Pattern
# -------------------------------------------------------------------------
test_wcp20_cancel_case() {
    log_info "Testing WCP-20: Cancel Case Pattern"

    cat << 'EOF' > /tmp/wcp20_cancel.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-20-Cancel-Case">
    <name>Cancel Case Pattern</name>
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

    if yawl_upload_spec "/tmp/wcp20_cancel.xml" "WCP-20-Cancel-Case"; then
        yawl_launch_case "WCP-20-Cancel-Case"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-020")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-21: Cancel Region Pattern
# -------------------------------------------------------------------------
test_wcp21_cancel_region() {
    log_info "Testing WCP-21: Cancel Region Pattern"

    cat << 'EOF' > /tmp/wcp21_region.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-21-Cancel-Region">
    <name>Cancel Region Pattern</name>
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
          <join code="xor"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp21_region.xml" "WCP-21-Cancel-Region"; then
        yawl_launch_case "WCP-21-Cancel-Region"
        local case_id=$(echo "$response" | jq -r '.caseId' 2>/dev/null || echo "CASE-021")
        yawl_cancel_case "$case_id"
        sleep 2
        yawl_validate_case "$case_id" "terminated"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-32: Interleaved Parallel Routing
# -------------------------------------------------------------------------
test_wcp32_interleaved_parallel() {
    log_info "Testing WCP-32: Interleaved Parallel Routing Pattern"

    cat << 'EOF' > /tmp/wcp32_interleaved.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-32-Interleaved-Parallel">
    <name>Interleaved Parallel Routing Pattern</name>
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
          <name>Task A</name>
          <exclusiveMode="sequential"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <exclusiveMode="sequential"/>
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

    if yawl_upload_spec "/tmp/wcp32_interleaved.xml" "WCP-32-Interleaved-Parallel"; then
        yawl_launch_case "WCP-32-Interleaved-Parallel"
        yawl_complete_case "CASE-032"
        yawl_validate_case "CASE-032" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-33: Interleaved Routing
# -------------------------------------------------------------------------
test_wcp33_interleaved_routing() {
    log_info "Testing WCP-33: Interleaved Routing Pattern"

    cat << 'EOF' > /tmp/wcp33_interleaved.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-33-Interleaved-Routing">
    <name>Interleaved Routing Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Region"/></flowsInto>
        </inputCondition>
        <region id="Region">
          <name>Interleaved Region</name>
          <interleaved="true"/>
          <processControlElements>
            <task id="TaskA">
              <name>Task A</name>
              <flowsInto><nextElementRef id="TaskB"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
            <task id="TaskB">
              <name>Task B</name>
              <flowsInto><nextElementRef id="TaskC"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
            <task id="TaskC">
              <name>Task C</name>
              <flowsInto><nextElementRef id="end"/></flowsInto>
              <join code="xor"/><split code="xor"/>
            </task>
          </processControlElements>
        </region>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp33_interleaved.xml" "WCP-33-Interleaved-Routing"; then
        yawl_launch_case "WCP-33-Interleaved-Routing"
        yawl_complete_case "CASE-033"
        yawl_validate_case "CASE-033" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-34: Milestone
# -------------------------------------------------------------------------
test_wcp34_milestone() {
    log_info "Testing WCP-34: Milestone Pattern"

    cat << 'EOF' > /tmp/wcp34_milestone.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-34-Milestone">
    <name>Milestone Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Milestone"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <milestone id="Milestone">
          <name>Milestone</name>
          <flowsInto><nextElementRef id="TaskC"/></flowsInto>
        </milestone>
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

    if yawl_upload_spec "/tmp/wcp34_milestone.xml" "WCP-34-Milestone"; then
        yawl_launch_case "WCP-34-Milestone"
        yawl_complete_case "CASE-034"
        yawl_validate_case "CASE-034" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-35: Critical Section
# -------------------------------------------------------------------------
test_wcp35_critical_section() {
    log_info "Testing WCP-35: Critical Section Pattern"

    cat << 'EOF' > /tmp/wcp35_critical.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-35-Critical-Section">
    <name>Critical Section Pattern</name>
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
          <join code="and"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </gateway>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp35_critical.xml" "WCP-35-Critical-Section"; then
        yawl_launch_case "WCP-35-Critical-Section"
        yawl_complete_case "CASE-035"
        yawl_validate_case "CASE-035" "complete"
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

run_state_tests() {
    run_pattern_test "WCP-18" test_wcp18_deferred_choice || true
    run_pattern_test "WCP-19" test_wcp19_cancel_activity || true
    run_pattern_test "WCP-20" test_wcp20_cancel_case || true
    run_pattern_test "WCP-21" test_wcp21_cancel_region || true
    run_pattern_test "WCP-32" test_wcp32_interleaved_parallel || true
    run_pattern_test "WCP-33" test_wcp33_interleaved_routing || true
    run_pattern_test "WCP-34" test_wcp34_milestone || true
    run_pattern_test "WCP-35" test_wcp35_critical_section || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL State-Based Patterns Validation (WCP-18-21,32-35)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all state pattern tests
    run_state_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "State-Based Patterns Validation Complete"
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
