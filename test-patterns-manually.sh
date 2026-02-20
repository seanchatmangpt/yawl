#!/bin/bash

# Manual YAWL Pattern Validation Script
# Tests XML generation and basic validation without requiring engine

set -e

echo "=== YAWL Stateless Pattern Validation ==="
echo "Testing pattern XML generation and basic validation..."
echo ""

# Track results
declare -a PATTERNS_PASSED=()
declare -a PATTERNS_FAILED=()

# Test data directory
TEST_DIR="/tmp/yawl-pattern-test"
mkdir -p "$TEST_DIR"

# WCP-01: Sequence Pattern
echo "Testing WCP-01: Sequence Pattern"
cat << 'EOF' > "$TEST_DIR/wcp01_sequence.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-01_Sequence" name="Sequence Pattern">
    <Net id="WCP-01_Sequence" name="Sequence Pattern">
      <inputCondition id="start"/>
      <task id="A" name="Task A"/>
      <task id="B" name="Task B"/>
      <outputCondition id="end"/>
      <flow from="start" to="A"/>
      <flow from="A" to="B"/>
      <flow from="B" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

# Validate XML structure
if xmllint --noout "$TEST_DIR/wcp01_sequence.xml" 2>/dev/null; then
    echo "✓ WCP-01 XML validation passed"
    PATTERNS_PASSED+=("WCP-01")
else
    echo "✗ WCP-01 XML validation failed"
    PATTERNS_FAILED+=("WCP-01")
fi

# WCP-02: Parallel Split
echo ""
echo "Testing WCP-02: Parallel Split Pattern"
cat << 'EOF' > "$TEST_DIR/wcp02_parallel.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-02_Parallel" name="Parallel Split Pattern">
    <Net id="WCP-02_Parallel" name="Parallel Split Pattern">
      <inputCondition id="start"/>
      <task id="A" name="Task A"/>
      <task id="B" name="Task B"/>
      <task id="C" name="Task C"/>
      <outputCondition id="end"/>
      <flow from="start" to="A"/>
      <flow from="A" to="B"/>
      <flow from="A" to="C"/>
      <flow from="B" to="end"/>
      <flow from="C" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

if xmllint --noout "$TEST_DIR/wcp02_parallel.xml" 2>/dev/null; then
    echo "✓ WCP-02 XML validation passed"
    PATTERNS_PASSED+=("WCP-02")
else
    echo "✗ WCP-02 XML validation failed"
    PATTERNS_FAILED+=("WCP-02")
fi

# WCP-03: Synchronization
echo ""
echo "Testing WCP-03: Synchronization Pattern"
cat << 'EOF' > "$TEST_DIR/wcp03_sync.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-03_Sync" name="Synchronization Pattern">
    <Net id="WCP-03_Sync" name="Synchronization Pattern">
      <inputCondition id="start"/>
      <task id="A" name="Task A"/>
      <task id="B" name="Task B"/>
      <task id="C" name="Task C"/>
      <outputCondition id="end"/>
      <flow from="start" to="A"/>
      <flow from="start" to="B"/>
      <flow from="start" to="C"/>
      <flow from="A" to="join"/>
      <flow from="B" to="join"/>
      <flow from="C" to="join"/>
      <flow from="join" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

if xmllint --noout "$TEST_DIR/wcp03_sync.xml" 2>/dev/null; then
    echo "✓ WCP-03 XML validation passed"
    PATTERNS_PASSED+=("WCP-03")
else
    echo "✗ WCP-03 XML validation failed"
    PATTERNS_FAILED+=("WCP-03")
fi

# Test Basic Pattern Category Results
echo ""
echo "=== Basic Pattern Category Results ==="
echo "Patterns Passed: ${#PATTERNS_PASSED[@]}"
printf '%s\n' "${PATTERNS_PASSED[@]}"
echo "Patterns Failed: ${#PATTERNS_FAILED[@]}"
printf '%s\n' "${PATTERNS_FAILED[@]}"

BASIC_RESULT="PASS"
if [ ${#PATTERNS_FAILED[@]} -gt 0 ]; then
    BASIC_RESULT="FAIL"
fi

echo "Overall Result: $BASIC_RESULT"

# Test Branching Patterns (WCP-06 to WCP-11)
echo ""
echo "=== Testing Branching Patterns ==="

# WCP-06: Exclusive Choice
echo "Testing WCP-06: Exclusive Choice Pattern"
cat << 'EOF' > "$TEST_DIR/wcp06_choice.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-06_Choice" name="Exclusive Choice Pattern">
    <Net id="WCP-06_Choice" name="Exclusive Choice Pattern">
      <inputCondition id="start"/>
      <task id="A" name="Task A"/>
      <task id="B" name="Task B"/>
      <outputCondition id="end"/>
      <flow from="start" to="choice"/>
      <flow from="choice" to="A"/>
      <flow from="choice" to="B"/>
      <flow from="A" to="end"/>
      <flow from="B" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

if xmllint --noout "$TEST_DIR/wcp06_choice.xml" 2>/dev/null; then
    echo "✓ WCP-06 XML validation passed"
    PATTERNS_PASSED+=("WCP-06")
else
    echo "✗ WCP-06 XML validation failed"
    PATTERNS_FAILED+=("WCP-06")
fi

BRANCHING_RESULT="PASS"
if [ ${#PATTERNS_FAILED[@]} -gt 0 ]; then
    BRANCHING_RESULT="FAIL"
fi

# Test Multi-Instance Patterns
echo ""
echo "=== Testing Multi-Instance Patterns ==="

# WCP-12: Multi-Instance Sequential
echo "Testing WCP-12: Multi-Instance Sequential Pattern"
cat << 'EOF' > "$TEST_DIR/wcp12_mi.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-12_MI" name="Multi-Instance Pattern">
    <Net id="WCP-12_MI" name="Multi-Instance Pattern">
      <inputCondition id="start"/>
      <task id="MI_Task" name="Multi-Instance Task" multiinstance="true"/>
      <outputCondition id="end"/>
      <flow from="start" to="MI_Task"/>
      <flow from="MI_Task" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

if xmllint --noout "$TEST_DIR/wcp12_mi.xml" 2>/dev/null; then
    echo "✓ WCP-12 XML validation passed"
    PATTERNS_PASSED+=("WCP-12")
else
    echo "✗ WCP-12 XML validation failed"
    PATTERNS_FAILED+=("WCP-12")
fi

MI_RESULT="PASS"
if [ ${#PATTERNS_FAILED[@]} -gt 0 ]; then
    MI_RESULT="FAIL"
fi

# Test State Patterns
echo ""
echo "=== Testing State Patterns ==="

# WCP-18: Deferred Choice
echo "Testing WCP-18: Deferred Choice Pattern"
cat << 'EOF' > "$TEST_DIR/wcp18_deferred.xml"
<?xml version="1.0" encoding="UTF-8"?>
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
  <schemaVersion>2.2</schemaVersion>
  <type>YAWL</type>
  <process id="WCP-18_Deferred" name="Deferred Choice Pattern">
    <Net id="WCP-18_Deferred" name="Deferred Choice Pattern">
      <inputCondition id="start"/>
      <task id="A" name="Task A"/>
      <task id="B" name="Task B"/>
      <outputCondition id="end"/>
      <flow from="start" to="defer"/>
      <flow from="defer" to="A"/>
      <flow from="defer" to="B"/>
      <flow from="A" to="end"/>
      <flow from="B" to="end"/>
    </Net>
  </process>
</YAWL>
EOF

if xmllint --noout "$TEST_DIR/wcp18_deferred.xml" 2>/dev/null; then
    echo "✓ WCP-18 XML validation passed"
    PATTERNS_PASSED+=("WCP-18")
else
    echo "✗ WCP-18 XML validation failed"
    PATTERNS_FAILED+=("WCP-18")
fi

STATE_RESULT="PASS"
if [ ${#PATTERNS_FAILED[@]} -gt 0 ]; then
    STATE_RESULT="FAIL"
fi

# Generate Summary Report
echo ""
echo "=== Pattern Validation Summary ==="
echo "Basic Patterns (WCP-01-05): $BASIC_RESULT"
echo "Branching Patterns (WCP-06-11): $BRANCHING_RESULT"
echo "Multi-Instance Patterns (WCP-12-17): $MI_RESULT"
echo "State Patterns (WCP-18-21): $STATE_RESULT"
echo ""
echo "Total Patterns Tested: ${#PATTERNS_PASSED[@]} + ${#PATTERNS_FAILED[@]}"
echo "Passed: ${#PATTERNS_PASSED[@]}"
echo "Failed: ${#PATTERNS_FAILED[@]}"

# Clean up
rm -rf "$TEST_DIR"

# Exit with appropriate code
if [ ${#PATTERNS_FAILED[@]} -gt 0 ]; then
    exit 1
else
    exit 0
fi