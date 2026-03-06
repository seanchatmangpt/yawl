#!/bin/bash
# Pre-Tool-Use Ban Hook - Enforce Coordinator-Only Mode for Ralph Loop
#
# When ralph-loop is active, the coordinator is BANNED from doing work directly.
# It must spawn agents instead. This hook enforces that constraint.
#
# Banned tools: Bash, Read, Edit, Write, Grep, Glob, AskUserQuestion
# Allowed tools: Agent, TaskOutput, Skill
#
# Exit codes:
#   0 = Allow tool
#   2 = Block tool (with error message)

set -euo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd)}"
RALPH_STATE="${PROJECT_ROOT}/.claude/.ralph-state"

# Check if ralph-loop is active
if [[ ! -f "${RALPH_STATE}/status" ]]; then
    exit 0  # No ralph-loop active, allow all tools
fi

STATUS=$(cat "${RALPH_STATE}/status" 2>/dev/null || echo "inactive")
if [[ "${STATUS}" != "active" ]]; then
    exit 0  # Not active, allow all tools
fi

# Parse tool name from stdin (JSON from Claude Code)
INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")

if [[ -z "${TOOL}" ]]; then
    exit 0  # Can't determine tool, allow
fi

# Banned tools for coordinator (must spawn agents instead)
BANNED_TOOLS=(
    "Bash"
    "Read"
    "Edit"
    "Write"
    "MultiEdit"
    "Grep"
    "Glob"
    "NotebookEdit"
    "AskUserQuestion"
)

# Allowed tools (coordinator functions)
ALLOWED_TOOLS=(
    "Agent"
    "TaskOutput"
    "TaskStop"
    "Skill"
    "ExitPlanMode"
    "EnterPlanMode"
    "EnterWorktree"
    "TaskCreate"
    "TaskGet"
    "TaskUpdate"
    "TaskList"
)

# Check if tool is banned
for banned in "${BANNED_TOOLS[@]}"; do
    if [[ "${TOOL}" == "${banned}" ]]; then
        cat >&2 << EOF

╔══════════════════════════════════════════════════════════════════════════════╗
║                     🚫 TOOL BANNED IN RALPH LOOP MODE                        ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  Tool: ${TOOL}
║                                                                              ║
║  In ralph-loop mode, the COORDINATOR cannot do work directly.               ║
║  You must SPAWN AN AGENT instead.                                           ║
║                                                                              ║
║  ═══════════════════════════════════════════════════════════════════════    ║
║                                                                              ║
║  ❌ BANNED:    Bash, Read, Edit, Write, Grep, Glob, AskUserQuestion         ║
║  ✅ ALLOWED:   Agent, TaskOutput, Skill                                      ║
║                                                                              ║
║  ═══════════════════════════════════════════════════════════════════════    ║
║                                                                              ║
║  Use this pattern instead:                                                   ║
║                                                                              ║
║    Agent(                                                                    ║
║      description="task description",                                         ║
║      prompt="Your task here...",                                            ║
║      subagent_type="yawl-engineer",                                         ║
║      model="opus",                                                          ║
║      max_turns=200                                                          ║
║    )                                                                         ║
║                                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝

EOF
        exit 2  # Block the tool call
    fi
done

exit 0  # Tool is allowed
