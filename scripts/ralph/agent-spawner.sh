#!/usr/bin/env bash
# Agent Spawner — Invoke Claude Code's Agent tool to spawn teams
set -euo pipefail

RALPH_HOME="${RALPH_HOME:-.}"

source "${RALPH_HOME}/scripts/ralph/utils.sh"
source "${RALPH_HOME}/scripts/ralph/loop-state.sh"

# Spawn agent team based on validation failure
spawn_agent_team() {
    local task_description="$1"
    local loop_id="$2"

    log_info "Spawning agent team for: ${task_description}"

    # Get current commit to track agent changes
    local pre_agent_commit
    pre_agent_commit=$(git_current_commit)

    # Record pre-spawn state
    local spawn_timestamp
    spawn_timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    # Output Task() syntax that Claude Code will parse and execute
    # This is the key integration point - Claude Code's native Task() parser
    # will detect these lines and spawn agents automatically
    cat <<EOF

---
**VALIDATION FAILED** — Auto-spawning agent team to fix issues

Ralph is invoking parallel agents to resolve validation failures:

EOF

    # Spawn yawl-engineer to fix code
    local engineer_id
    engineer_id=$(generate_uuid)
    record_agent_spawn "${engineer_id}" "yawl-engineer"

    cat <<EOF
Task("Engineer", "Fix validation failures in YAWL code: ${task_description}", "yawl-engineer")
EOF

    # Spawn yawl-validator to check fixes
    local validator_id
    validator_id=$(generate_uuid)
    record_agent_spawn "${validator_id}" "yawl-validator"

    cat <<EOF
Task("Validator", "Validate agent changes: Run dx.sh all and hyper-validate.sh", "yawl-validator")
EOF

    # Spawn yawl-tester to run full test suite
    local tester_id
    tester_id=$(generate_uuid)
    record_agent_spawn "${tester_id}" "yawl-tester"

    cat <<EOF
Task("Tester", "Run full test suite and verify all tests pass", "yawl-tester")

**Agents working in parallel.** Results will be integrated into the next loop iteration.
---

EOF

    # Wait for agents to commit changes
    # Agents will naturally commit their changes to the branch
    # We detect this via git log
    log_info "Waiting for agents to complete and commit changes..."

    # Get pre-agent commit for comparison
    local pre_agent_commit
    pre_agent_commit=$(git_current_commit)

    # Check for new commits from agents (with timeout)
    local commit_timeout=1800  # 30 minutes
    local check_cmd="verify_agent_commits '${pre_agent_commit}'"

    if wait_with_timeout "${commit_timeout}" "${check_cmd}"; then
        # Verify commits actually changed code
        local post_agent_commit
        post_agent_commit=$(git_current_commit)

        local new_commits
        new_commits=$(git log --oneline "${pre_agent_commit}..${post_agent_commit}" 2>/dev/null | wc -l)

        # Verify there are actual code changes (not just metadata)
        if git diff "${pre_agent_commit}..${post_agent_commit}" --stat 2>/dev/null | grep -qE '\.(java|xml|py|sh)'; then
            log_info "Agents completed: ${new_commits} commits with code changes"

            record_agent_complete "${engineer_id}" "success" "${new_commits}"
            record_agent_complete "${validator_id}" "success" "0"
            record_agent_complete "${tester_id}" "success" "0"

            set_validation_status "PENDING"  # Reset to pending for next iteration
            return 0
        else
            log_error "Agent commits don't contain code changes"
            return 1
        fi
    else
        log_error "Agent team timeout - agents did not complete within 30 minutes"
        record_agent_complete "${engineer_id}" "timeout" "0"
        record_agent_complete "${validator_id}" "timeout" "0"
        record_agent_complete "${tester_id}" "timeout" "0"

        return 1
    fi
}

# Verify agent commits exist
verify_agent_commits() {
    local pre_commit="$1"
    local current
    current=$(git_current_commit)

    if [[ "$pre_commit" == "$current" ]]; then
        log_debug "No new commits yet (pre: ${pre_commit}, current: ${current})"
        return 1
    fi

    log_debug "New commits detected: ${pre_commit} → ${current}"
    return 0
}

# Main entry
if [[ $# -lt 1 ]]; then
    echo "Usage: agent-spawner.sh <task-description> [loop-id]"
    exit 1
fi

task_description="$1"
loop_id="${2:-$(jq -r '.loop_id' "${RALPH_STATE_DIR}/current-loop.json" 2>/dev/null || echo 'unknown')}"

spawn_agent_team "${task_description}" "${loop_id}"
