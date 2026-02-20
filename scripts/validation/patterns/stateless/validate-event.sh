#!/bin/bash

# ==========================================================================
# validate-event.sh - YAWL Event-Based Patterns (WCP-37 to WCP-40, 51-59)
#
# Patterns: Local Trigger, Global Trigger, Reset Trigger, Cancel Trigger,
#           Transient Trigger, Persistent Trigger, Event-based Choice,
#           Event-based Split, Event-based Join, Timer, Message, Exception
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
# WCP-37: Local Trigger
# -------------------------------------------------------------------------
test_wcp37_local_trigger() {
    log_info "Testing WCP-37: Local Trigger Pattern"

    cat << 'EOF' > /tmp/wcp37_local.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-37-Local-Trigger">
    <name>Local Trigger Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Trigger"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <trigger id="Trigger">
          <name>Local Trigger</name>
          <triggerType="local"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </trigger>
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

    if yawl_upload_spec "/tmp/wcp37_local.xml" "WCP-37-Local-Trigger"; then
        yawl_launch_case "WCP-37-Local-Trigger"
        yawl_complete_case "CASE-037"
        yawl_validate_case "CASE-037" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-38: Global Trigger
# -------------------------------------------------------------------------
test_wcp38_global_trigger() {
    log_info "Testing WCP-38: Global Trigger Pattern"

    cat << 'EOF' > /tmp/wcp38_global.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-38-Global-Trigger">
    <name>Global Trigger Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Trigger"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <trigger id="Trigger">
          <name>Global Trigger</name>
          <triggerType="global"/>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </trigger>
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

    if yawl_upload_spec "/tmp/wcp38_global.xml" "WCP-38-Global-Trigger"; then
        yawl_launch_case "WCP-38-Global-Trigger"
        yawl_complete_case "CASE-038"
        yawl_validate_case "CASE-038" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-39: Reset Trigger
# -------------------------------------------------------------------------
test_wcp39_reset_trigger() {
    log_info "Testing WCP-39: Reset Trigger Pattern"

    cat << 'EOF' > /tmp/wcp39_reset.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-39-Reset-Trigger">
    <name>Reset Trigger Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Reset"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <resetTrigger id="Reset">
          <name>Reset Trigger</name>
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </resetTrigger>
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

    if yawl_upload_spec "/tmp/wcp39_reset.xml" "WCP-39-Reset-Trigger"; then
        yawl_launch_case "WCP-39-Reset-Trigger"
        yawl_complete_case "CASE-039"
        yawl_validate_case "CASE-039" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-40: Cancel Trigger
# -------------------------------------------------------------------------
test_wcp40_cancel_trigger() {
    log_info "Testing WCP-40: Cancel Trigger Pattern"

    cat << 'EOF' > /tmp/wcp40_cancel_trig.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-40-Cancel-Trigger">
    <name>Cancel Trigger Pattern</name>
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
          <flowsInto><nextElementRef id="TaskB"/><nextElementRef id="Cancel-Trigger"/></flowsInto>
        </gateway>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <cancelTrigger id="Cancel-Trigger">
          <name>Cancel Trigger</name>
          <flowsInto><nextElementRef id="Join"/></flowsInto>
        </cancelTrigger>
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

    if yawl_upload_spec "/tmp/wcp40_cancel_trig.xml" "WCP-40-Cancel-Trigger"; then
        yawl_launch_case "WCP-40-Cancel-Trigger"
        yawl_complete_case "CASE-040"
        yawl_validate_case "CASE-040" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-51: Transient Trigger
# -------------------------------------------------------------------------
test_wcp51_transient_trigger() {
    log_info "Testing WCP-51: Transient Trigger Pattern"

    cat << 'EOF' > /tmp/wcp51_transient.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-51-Transient-Trigger">
    <name>Transient Trigger Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Trigger"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <trigger id="Trigger">
          <name>Transient Trigger</name>
          <triggerType="local" persistence="transient"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </trigger>
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

    if yawl_upload_spec "/tmp/wcp51_transient.xml" "WCP-51-Transient-Trigger"; then
        yawl_launch_case "WCP-51-Transient-Trigger"
        yawl_complete_case "CASE-051"
        yawl_validate_case "CASE-051" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-52: Persistent Trigger
# -------------------------------------------------------------------------
test_wcp52_persistent_trigger() {
    log_info "Testing WCP-52: Persistent Trigger Pattern"

    cat << 'EOF' > /tmp/wcp52_persistent.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-52-Persistent-Trigger">
    <name>Persistent Trigger Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Trigger"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <trigger id="Trigger">
          <name>Persistent Trigger</name>
          <triggerType="local" persistence="persistent"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </trigger>
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

    if yawl_upload_spec "/tmp/wcp52_persistent.xml" "WCP-52-Persistent-Trigger"; then
        yawl_launch_case "WCP-52-Persistent-Trigger"
        yawl_complete_case "CASE-052"
        yawl_validate_case "CASE-052" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-53: Event-based Exclusive Choice
# -------------------------------------------------------------------------
test_wcp53_event_exclusive_choice() {
    log_info "Testing WCP-53: Event-based Exclusive Choice Pattern"

    cat << 'EOF' > /tmp/wcp53_event_choice.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-53-Event-Exclusive-Choice">
    <name>Event-based Exclusive Choice Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Choice"/></flowsInto>
        </inputCondition>
        <gateway id="Choice">
          <name>Event Choice</name>
          <split code="event_xor"/>
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

    if yawl_upload_spec "/tmp/wcp53_event_choice.xml" "WCP-53-Event-Exclusive-Choice"; then
        yawl_launch_case "WCP-53-Event-Exclusive-Choice"
        yawl_complete_case "CASE-053"
        yawl_validate_case "CASE-053" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-54: Event-based Parallel Split
# -------------------------------------------------------------------------
test_wcp54_event_parallel_split() {
    log_info "Testing WCP-54: Event-based Parallel Split Pattern"

    cat << 'EOF' > /tmp/wcp54_event_split.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-54-Event-Parallel-Split">
    <name>Event-based Parallel Split Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="Trigger"/></flowsInto>
        </inputCondition>
        <trigger id="Trigger">
          <name>Event Trigger</name>
          <triggerType="local"/>
          <flowsInto><nextElementRef id="Split"/></flowsInto>
        </trigger>
        <gateway id="Split">
          <name>Split</name>
          <split code="and"/>
          <flowsInto><nextElementRef id="TaskA"/><nextElementRef id="TaskB"/></flowsInto>
        </gateway>
        <task id="TaskA">
          <name>Task A</name>
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

    if yawl_upload_spec "/tmp/wcp54_event_split.xml" "WCP-54-Event-Parallel-Split"; then
        yawl_launch_case "WCP-54-Event-Parallel-Split"
        yawl_complete_case "CASE-054"
        yawl_validate_case "CASE-054" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-55: Timer Pattern
# -------------------------------------------------------------------------
test_wcp55_timer() {
    log_info "Testing WCP-55: Timer Pattern"

    cat << 'EOF' > /tmp/wcp55_timer.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-55-Timer">
    <name>Timer Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Timer"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <timer id="Timer">
          <name>Timer</name>
          <timerExpression="PT5S"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </timer>
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

    if yawl_upload_spec "/tmp/wcp55_timer.xml" "WCP-55-Timer"; then
        yawl_launch_case "WCP-55-Timer"
        sleep 6  # Wait for timer
        yawl_complete_case "CASE-055"
        yawl_validate_case "CASE-055" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-56: Message Pattern
# -------------------------------------------------------------------------
test_wcp56_message() {
    log_info "Testing WCP-56: Message Pattern"

    cat << 'EOF' > /tmp/wcp56_message.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-56-Message">
    <name>Message Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Message"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <message id="Message">
          <name>Message Wait</name>
          <messageType="external"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </message>
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

    if yawl_upload_spec "/tmp/wcp56_message.xml" "WCP-56-Message"; then
        yawl_launch_case "WCP-56-Message"
        yawl_complete_case "CASE-056"
        yawl_validate_case "CASE-056" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-57: Exception Pattern
# -------------------------------------------------------------------------
test_wcp57_exception() {
    log_info "Testing WCP-57: Exception Pattern"

    cat << 'EOF' > /tmp/wcp57_exception.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-57-Exception">
    <name>Exception Pattern</name>
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
        <exceptionHandler id="Exception">
          <name>Exception Handler</name>
          <exceptionType="error"/>
          <flowsInto><nextElementRef id="Handler"/></flowsInto>
        </exceptionHandler>
        <task id="Handler">
          <name>Error Handler</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp57_exception.xml" "WCP-57-Exception"; then
        yawl_launch_case "WCP-57-Exception"
        yawl_complete_case "CASE-057"
        yawl_validate_case "CASE-057" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-58: Compensation Event
# -------------------------------------------------------------------------
test_wcp58_compensation_event() {
    log_info "Testing WCP-58: Compensation Event Pattern"

    cat << 'EOF' > /tmp/wcp58_comp_event.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-58-Compensation-Event">
    <name>Compensation Event Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <compensator="Comp-A"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <compensationHandler id="Comp-A">
          <name>Compensation A</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </compensationHandler>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF

    if yawl_upload_spec "/tmp/wcp58_comp_event.xml" "WCP-58-Compensation-Event"; then
        yawl_launch_case "WCP-58-Compensation-Event"
        yawl_complete_case "CASE-058"
        yawl_validate_case "CASE-058" "complete"
    else
        return 1
    fi
}

# -------------------------------------------------------------------------
# WCP-59: Signal Event
# -------------------------------------------------------------------------
test_wcp59_signal_event() {
    log_info "Testing WCP-59: Signal Event Pattern"

    cat << 'EOF' > /tmp/wcp59_signal.xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-59-Signal-Event">
    <name>Signal Event Pattern</name>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="Signal"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <signal id="Signal">
          <name>Signal Wait</name>
          <signalRef="external-signal"/>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
        </signal>
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

    if yawl_upload_spec "/tmp/wcp59_signal.xml" "WCP-59-Signal-Event"; then
        yawl_launch_case "WCP-59-Signal-Event"
        yawl_complete_case "CASE-059"
        yawl_validate_case "CASE-059" "complete"
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

run_event_tests() {
    run_pattern_test "WCP-37" test_wcp37_local_trigger || true
    run_pattern_test "WCP-38" test_wcp38_global_trigger || true
    run_pattern_test "WCP-39" test_wcp39_reset_trigger || true
    run_pattern_test "WCP-40" test_wcp40_cancel_trigger || true
    run_pattern_test "WCP-51" test_wcp51_transient_trigger || true
    run_pattern_test "WCP-52" test_wcp52_persistent_trigger || true
    run_pattern_test "WCP-53" test_wcp53_event_exclusive_choice || true
    run_pattern_test "WCP-54" test_wcp54_event_parallel_split || true
    run_pattern_test "WCP-55" test_wcp55_timer || true
    run_pattern_test "WCP-56" test_wcp56_message || true
    run_pattern_test "WCP-57" test_wcp57_exception || true
    run_pattern_test "WCP-58" test_wcp58_compensation_event || true
    run_pattern_test "WCP-59" test_wcp59_signal_event || true
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------
main() {
    log_section "YAWL Event-Based Patterns Validation (WCP-37-40,51-59)"
    echo

    # Initialize validation
    yawl_init_validation
    yawl_connect

    local start_time=$(date +%s)

    # Run all event pattern tests
    run_event_tests

    # Generate report
    local duration=$(( $(date +%s) - start_time ))
    log_header "Event-Based Patterns Validation Complete"
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
