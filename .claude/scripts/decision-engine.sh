#!/bin/bash
# Decision Engine - Autonomous Decision Making System
# Evaluates rules from TOML config to make decisions without user interaction
#
# Usage:
#   decision-engine.sh --rule-set agent_selection --input '{"task":"fix ynet deadlock"}'
#   decision-engine.sh --rule-set violation_remediation --input '{"violation":"H_TODO","file":"Test.java","line":42}'
#   decision-engine.sh --rule-set loop_continuation --input '{"promise_found":false,"iterations":5,"max":50}'
#
# Output: JSON with decision, confidence, reasoning

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_FILE="${REPO_ROOT}/.claude/config/autonomous.toml"
RULES_FILE="${REPO_ROOT}/.claude/rules/decisions.toml"
DECISION_LOG="${REPO_ROOT}/.claude/memory/decisions.log"
DECISION_STATE_DIR="${REPO_ROOT}/.claude/.decision-state"

mkdir -p "${DECISION_STATE_DIR}" "$(dirname "${DECISION_LOG}")"

# ──────────────────────────────────────────────────────────────────────────────
# TOML PARSER (minimal, focused on our use cases)
# ──────────────────────────────────────────────────────────────────────────────

parse_toml_section() {
    local section="$1"
    local file="$2"

    # Extract section [section_name] ... [next_section]
    sed -n "/^\[$section\]/,/^\[/p" "$file" | head -n -1 | grep -v '^\[' || true
}

get_toml_value() {
    local key="$1"
    local section_content="$2"

    echo "$section_content" | grep "^$key\s*=" | head -1 | cut -d'=' -f2 | tr -d ' "' || echo ""
}

get_toml_array() {
    local key="$1"
    local section_content="$2"

    # Extract array between [ ]
    section_content=$(echo "$section_content" | grep "^$key\s*=")
    echo "$section_content" | sed 's/.*\[\(.*\)\].*/\1/' | tr ',' '\n' | tr -d ' "[]' || true
}

# ──────────────────────────────────────────────────────────────────────────────
# DECISION LOGIC - AGENT SELECTION
# ──────────────────────────────────────────────────────────────────────────────

decide_agent() {
    local task_description="$1"
    local task_lower=$(echo "${task_description,,}")

    # Load rules from TOML
    local rules_section=$(parse_toml_section "agent_selection" "$RULES_FILE")

    local max_confidence=0.0
    local selected_agent=""
    local matched_keywords=""

    # Test each agent pattern
    for pattern in yawl-engineer yawl-validator yawl-architect yawl-integrator yawl-tester yawl-reviewer yawl-prod-validator yawl-performance-benchmarker; do
        local keywords=$(get_toml_array "$pattern" "$rules_section" || echo "")
        local confidence=$(get_toml_value "${pattern}_confidence" "$rules_section" || echo "0.5")

        # Check if any keyword matches
        for keyword in $keywords; do
            if echo "$task_lower" | grep -q "$keyword"; then
                if (( $(echo "$confidence > $max_confidence" | bc -l) )); then
                    max_confidence=$confidence
                    selected_agent="$pattern"
                    matched_keywords="$keyword"
                fi
                break
            fi
        done
    done

    # Fallback
    if [ -z "$selected_agent" ]; then
        selected_agent="yawl-engineer"
        max_confidence=0.70
        matched_keywords="(default)"
    fi

    # Output decision
    local decision_id=$(uuidgen 2>/dev/null || echo "decision-$(date +%s)")

    cat <<EOF
{
  "decision_id": "$decision_id",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "rule_set": "agent_selection",
  "decision": "$selected_agent",
  "confidence": $max_confidence,
  "reasoning": "Matched keyword: $matched_keywords",
  "task_description": "$task_description"
}
EOF

    # Log decision
    log_decision "$decision_id" "agent_selection" "$selected_agent" "$max_confidence" "Matched keyword: $matched_keywords"
}

# ──────────────────────────────────────────────────────────────────────────────
# DECISION LOGIC - VIOLATION REMEDIATION
# ──────────────────────────────────────────────────────────────────────────────

decide_remediation() {
    local violation_pattern="$1"  # H_TODO, H_MOCK, Q_EMPTY, etc.
    local file="$2"
    local line="$3"

    local rules_section=$(parse_toml_section "violation_remediation" "$RULES_FILE")

    # Map violation to auto-fix action
    local action=""
    local confidence="1.0"

    case "$violation_pattern" in
        H_TODO)
            action="remove_comment"
            confidence="1.0"
            ;;
        H_MOCK)
            action="delete_mock_class"
            confidence="0.95"
            ;;
        H_STUB)
            action="replace_with_throw"
            confidence="0.98"
            ;;
        H_EMPTY)
            action="add_throw_exception"
            confidence="1.0"
            ;;
        H_FALLBACK)
            action="rethrow_exception"
            confidence="0.90"
            ;;
        H_SILENT)
            action="convert_to_throw"
            confidence="0.95"
            ;;
        Q_EMPTY)
            action="add_throw_exception"
            confidence="1.0"
            ;;
        Q_FAKE_RETURN)
            action="replace_with_throw"
            confidence="0.95"
            ;;
        *)
            action="escalate_to_user"
            confidence="0.50"
            ;;
    esac

    local decision_id=$(uuidgen 2>/dev/null || echo "decision-$(date +%s)")

    cat <<EOF
{
  "decision_id": "$decision_id",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "rule_set": "violation_remediation",
  "violation_pattern": "$violation_pattern",
  "file": "$file",
  "line": $line,
  "decision": "$action",
  "confidence": $confidence,
  "auto_fix": true
}
EOF

    log_decision "$decision_id" "violation_remediation" "$action" "$confidence" "Pattern: $violation_pattern in $file:$line"
}

# ──────────────────────────────────────────────────────────────────────────────
# DECISION LOGIC - LOOP CONTINUATION
# ──────────────────────────────────────────────────────────────────────────────

decide_loop_continuation() {
    local promise_found="$1"
    local iterations="$2"
    local max_iterations="$3"
    local validation_green="$4"

    local decision=""
    local confidence="0.0"
    local reasoning=""

    # Priority logic (from CLAUDE.md)
    if [ "$promise_found" = "true" ] && [ "$validation_green" = "true" ]; then
        decision="exit"
        confidence="1.0"
        reasoning="Completion promise found + validation GREEN"
    elif [ "$iterations" -ge "$max_iterations" ]; then
        decision="exit"
        confidence="0.95"
        reasoning="Max iterations ($max_iterations) reached"
    elif [ "$promise_found" = "true" ] && [ "$validation_green" = "false" ]; then
        decision="continue"
        confidence="0.90"
        reasoning="Promise found but validation failed, attempting remediation"
    else
        decision="continue"
        confidence="0.85"
        reasoning="Iteration $iterations/$max_iterations, no completion promise yet"
    fi

    local decision_id=$(uuidgen 2>/dev/null || echo "decision-$(date +%s)")

    cat <<EOF
{
  "decision_id": "$decision_id",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "rule_set": "loop_continuation",
  "decision": "$decision",
  "confidence": $confidence,
  "iterations": $iterations,
  "max_iterations": $max_iterations,
  "promise_found": $promise_found,
  "validation_green": $validation_green,
  "reasoning": "$reasoning"
}
EOF

    log_decision "$decision_id" "loop_continuation" "$decision" "$confidence" "$reasoning"
}

# ──────────────────────────────────────────────────────────────────────────────
# LOGGING
# ──────────────────────────────────────────────────────────────────────────────

log_decision() {
    local decision_id="$1"
    local rule_set="$2"
    local decision="$3"
    local confidence="$4"
    local reasoning="$5"

    if [ "${AUTONOMOUS_LOG_DECISIONS:-true}" != "false" ]; then
        cat <<EOF >> "${DECISION_LOG}"
{"decision_id":"$decision_id","timestamp":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","rule_set":"$rule_set","decision":"$decision","confidence":$confidence,"reasoning":"$reasoning"}
EOF
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# MAIN ENTRY POINT
# ──────────────────────────────────────────────────────────────────────────────

if [ $# -lt 2 ]; then
    echo "Usage: decision-engine.sh --rule-set <set_name> --input <json>" >&2
    echo "Examples:" >&2
    echo "  decision-engine.sh --rule-set agent_selection --input '{\"task\":\"fix ynet\"}'" >&2
    echo "  decision-engine.sh --rule-set violation_remediation --input '{\"violation\":\"H_TODO\",\"file\":\"Test.java\",\"line\":42}'" >&2
    exit 2
fi

RULE_SET=""
INPUT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --rule-set)
            RULE_SET="$2"
            shift 2
            ;;
        --input)
            INPUT="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

case "$RULE_SET" in
    agent_selection)
        task=$(echo "$INPUT" | jq -r '.task // ""')
        decide_agent "$task"
        ;;
    violation_remediation)
        violation=$(echo "$INPUT" | jq -r '.violation // ""')
        file=$(echo "$INPUT" | jq -r '.file // ""')
        line=$(echo "$INPUT" | jq -r '.line // 0')
        decide_remediation "$violation" "$file" "$line"
        ;;
    loop_continuation)
        promise_found=$(echo "$INPUT" | jq -r '.promise_found // false')
        iterations=$(echo "$INPUT" | jq -r '.iterations // 0')
        max_iterations=$(echo "$INPUT" | jq -r '.max_iterations // 50')
        validation_green=$(echo "$INPUT" | jq -r '.validation_green // false')
        decide_loop_continuation "$promise_found" "$iterations" "$max_iterations" "$validation_green"
        ;;
    *)
        echo "Unknown rule set: $RULE_SET" >&2
        exit 2
        ;;
esac

exit 0
