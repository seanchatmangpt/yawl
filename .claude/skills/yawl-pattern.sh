#!/bin/bash
# YAWL Pattern Skill - Claude Code 2026 Best Practices
# Usage: /yawl-pattern --list | --implement=WCP1 | --verify

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PATTERNS_DIR="${PROJECT_ROOT}/src/org/yawlfoundation/yawl/patterns"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Pattern Skill - Implement and verify control-flow patterns

Usage: /yawl-pattern [options]

Options:
  --list             List all control-flow patterns
  --implement=WCPn   Show implementation guide for pattern
  --verify           Verify pattern implementations
  -h, --help         Show this help message

Pattern Categories:
  WCP1-WCP5    Basic Control Flow
  WCP6-WCP10   Advanced Branching/Sync
  WCP11-WCP15  Multiple Instances
  WCP16-WCP20  State-based
  WCP21-WCP25  Cancellation/Completion
  WCP26-WCP30  Iteration
  WCP31-WCP43  Advanced Patterns

Examples:
  /yawl-pattern --list
  /yawl-pattern --implement=WCP1
  /yawl-pattern --verify
EOF
}

# Parse arguments
ACTION="list"
PATTERN=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --list)
            ACTION="list"
            shift
            ;;
        --implement=*)
            ACTION="implement"
            PATTERN="${1#*=}"
            shift
            ;;
        --verify)
            ACTION="verify"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Pattern definitions
declare -A PATTERNS=(
    ["WCP1"]="Sequence - Tasks execute in sequential order"
    ["WCP2"]="Parallel Split - A thread splits into multiple parallel threads"
    ["WCP3"]="Synchronization - Multiple threads converge into one"
    ["WCP4"]="Exclusive Choice - A thread chooses one of several branches"
    ["WCP5"]="Simple Merge - Two or more branches reunite without synchronization"
    ["WCP6"]="Multi-Choice - A thread can choose multiple branches"
    ["WCP7"]="Structured Synchronizing Merge - Multiple branches merge with synchronization"
    ["WCP8"]="Multi-Merge - Multiple branches merge without synchronization"
    ["WCP9"]="Structured Discriminator - First to complete continues, rest discarded"
    ["WCP10"]="Local Discriminator - Discriminator without reset"
    ["WCP11"]="Arbitrary Cycles - Jump back to any point"
    ["WCP12"]="Implicit Termination - Process ends when no work remains"
    ["WCP13"]="Explicit Termination - Process can be explicitly terminated"
    ["WCP14"]="Multi-Choice with Synchronization - WCP6 + WCP7 combined"
    ["WCP15"]="Multi-Instance with Synchronization - Multiple instances sync at join"
    ["WCP16"]="Deferred Choice - Choice made by environment"
    ["WCP17"]="Interleaved Parallel Routing - Tasks execute in any order but not simultaneously"
    ["WCP18"]="Milestone - Task enabled only when milestone is active"
    ["WCP19"]="Cancel Activity - Activity can be cancelled"
    ["WCP20"]="Cancel Case - Entire case can be cancelled"
    ["WCP21"]="Structured Loop - Loop with explicit structure"
    ["WCP22"]="Structured Loop with Multiple Exit Conditions"
    ["WCP23"]="Structured Loop with Multiple Entry Points"
    ["WCP24"]="Structured Loop with Parallel Exit"
    ["WCP25"]="Structured Loop with Parallel Entry"
    ["WCP26"]="Cancel Region - Cancel a group of activities"
    ["WCP27"]="Cancel Multiple Instances - Cancel specific instances"
    ["WCP28"]="Complete Multiple Instances - Force completion"
    ["WCP29"]="Cancel Case Partial - Cancel part of a case"
    ["WCP30"]="Structured Partial Join - Partial synchronization"
    ["WCP31"]="Blocking Discriminator - Discriminator that blocks"
    ["WCP32"]="Cancelling Discriminator - Discriminator that cancels"
    ["WCP33"]="Trigger - External event triggers activity"
    ["WCP34"]="Scheduled Activation - Time-based activation"
    ["WCP35"]="Local Parallel Entry - Local version of parallel entry"
    ["WCP36"]="Local Parallel Exit - Local version of parallel exit"
    ["WCP37"]="Structured Synchronizing Merge (Alternative)"
    ["WCP38"]="Structured Discriminator (Alternative)"
    ["WCP39"]="Generalized AND-Join - AND-join with multiple inputs"
    ["WCP40"]="Generalized OR-Join - OR-join with multiple inputs"
    ["WCP41"]="Generalized XOR-Join - XOR-join with multiple inputs"
    ["WCP42"]="Thread Split - Split into numbered threads"
    ["WCP43"]="Thread Merge - Merge numbered threads"
)

case "${ACTION}" in
    list)
        echo -e "${BLUE}[yawl-pattern] Control-Flow Patterns (43 patterns)${NC}"
        echo ""
        echo "Basic Control Flow (WCP1-WCP5):"
        for p in WCP1 WCP2 WCP3 WCP4 WCP5; do
            echo "  ${p}: ${PATTERNS[$p]}"
        done
        echo ""
        echo "Advanced Branching/Sync (WCP6-WCP10):"
        for p in WCP6 WCP7 WCP8 WCP9 WCP10; do
            echo "  ${p}: ${PATTERNS[$p]}"
        done
        echo ""
        echo "Run with --implement=WCPn for implementation guide"
        ;;
    implement)
        if [[ -z "${PATTERN}" ]] || [[ -z "${PATTERNS[$PATTERN]:-}" ]]; then
            echo -e "${RED}[yawl-pattern] Unknown pattern: ${PATTERN}${NC}"
            echo "Run --list to see available patterns"
            exit 1
        fi

        echo -e "${BLUE}[yawl-pattern] Implementation Guide: ${PATTERN}${NC}"
        echo -e "${BLUE}${PATTERNS[$PATTERN]}${NC}"
        echo ""

        case "${PATTERN}" in
            WCP1)
                cat << 'GUIDE'
Implementation: Sequence Pattern (WCP1)

YAWL Net Structure:
  InputCondition -> Task A -> Task B -> ... -> OutputCondition

Code Reference: YNetRunner.java (sequential execution)

Implementation Steps:
1. Define tasks in specification with flowsInto elements
2. Each task has exactly one outgoing flow
3. Tasks execute in declaration order

Example Specification:
  <task id="TaskA">
    <flowsInto><nextElementRef id="TaskB"/></flowsInto>
  </task>
  <task id="TaskB">
    <flowsInto><nextElementRef id="end"/></flowsInto>
  </task>

Test: See TestSequencePattern.java
GUIDE
                ;;
            WCP2)
                cat << 'GUIDE'
Implementation: Parallel Split (WCP2)

YAWL Net Structure:
  Task A -> [Task B, Task C, Task D] (all start simultaneously)

Code Reference: YNetRunner.java (parallel task enabling)

Implementation Steps:
1. Task has multiple flowsInto elements
2. All target tasks are enabled simultaneously
3. Use AND-split semantics

Example Specification:
  <task id="TaskA">
    <flowsInto><nextElementRef id="TaskB"/></flowsInto>
    <flowsInto><nextElementRef id="TaskC"/></flowsInto>
    <flowsInto><nextElementRef id="TaskD"/></flowsInto>
  </task>

Test: See TestParallelSplitPattern.java
GUIDE
                ;;
            *)
                echo "See ${PATTERNS_DIR} for implementation details"
                echo "Test file: test/org/yawlfoundation/yawl/patterns/Test${PATTERN}.java"
                ;;
        esac
        ;;
    verify)
        echo -e "${BLUE}[yawl-pattern] Verifying pattern implementations...${NC}"
        echo ""

        FOUND=0
        MISSING=0

        for pattern in "${!PATTERNS[@]}"; do
            if [[ -f "${PATTERNS_DIR}/${pattern}.java" ]]; then
                echo -e "  ${GREEN}✓${NC} ${pattern}: Found"
                ((FOUND++))
            else
                echo -e "  ${YELLOW}?${NC} ${pattern}: Not found as separate class"
                ((MISSING++))
            fi
        done

        echo ""
        echo -e "${GREEN}Found: ${FOUND}${NC}"
        echo -e "${YELLOW}Missing/Integrated: ${MISSING}${NC}"
        echo ""
        echo "Note: Many patterns are implemented within YNetRunner.java and related classes."
        ;;
esac
