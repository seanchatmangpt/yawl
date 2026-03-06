#!/bin/bash
# Ralph Loop Skill - Self-Referential Iterative Task Completion
#
# This skill initiates an iterative task loop that feeds prompts back to Claude
# until a completion promise is satisfied. Works both generically and with YAWL's
# validation pipeline (smart validation mode).
#
# Usage:
#   /ralph-loop "Task description here" --completion-promise "COMPLETE"
#   /ralph-loop "Task description" --no-team --completion-promise "DONE"  # single agent
#
# DEFAULT BEHAVIOR (5-Agent Team Mode):
#   - Routes task to appropriate 5-agent team (explore/plan/implement)
#   - Spawns 5 parallel agents using Task tool
#   - Agents coordinate via messaging
#   - Consolidation phase at end of each iteration
#
# Single Agent Mode (--no-team flag):
#   - Runs with a single agent instead of 5-agent team
#   - Use for simple tasks that don't need parallelization
#
# Smart Validation (YAWL context):
#   - Auto-detects YAWL environment (pom.xml + scripts/dx.sh + scripts/observatory/)
#   - Skips validation on iteration 1 if dx.sh already GREEN
#   - Runs validation on subsequent iterations if files edited
#   - Re-injects prompt with validation errors if violations found
#
# Exit codes:
#   0 = Loop exited normally (promise satisfied or max iterations reached)
#   1 = Transient error (retry-able)
#   2 = Fatal error (user must fix)

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RALPH_STATE_DIR="${REPO_ROOT}/.claude/.ralph-state"
CLAUDE_DIR="${REPO_ROOT}/.claude"
CONFIG_DIR="${CLAUDE_DIR}/config"

# Create state directory
mkdir -p "${RALPH_STATE_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# TEAM ROUTING FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

# Route task to appropriate team based on keywords
route_to_team() {
    local task="$1"
    local task_lower="${task,,}"

    # EXPLORE team keywords
    local explore_keywords="research investigate discover analyze explore understand study examine survey probe find look identify detect"
    # PLAN team keywords
    local plan_keywords="plan design architect strategy blueprint organize structure layout framework approach refactor reorganize conceptualize decide"
    # IMPLEMENT team keywords (default)
    local implement_keywords="implement build code develop write create fix debug test integrate commit deploy execute run solve patch improve"

    local explore_score=0
    local plan_score=0
    local implement_score=0

    # Count keyword matches for each team
    for kw in $explore_keywords; do
        [[ "$task_lower" == *"$kw"* ]] && ((explore_score++))
    done
    for kw in $plan_keywords; do
        [[ "$task_lower" == *"$kw"* ]] && ((plan_score++))
    done
    for kw in $implement_keywords; do
        [[ "$task_lower" == *"$kw"* ]] && ((implement_score++))
    done

    # Return team with highest score (default: implement)
    if [[ $explore_score -gt $plan_score && $explore_score -gt $implement_score ]]; then
        echo "explore"
    elif [[ $plan_score -gt $implement_score ]]; then
        echo "plan"
    else
        echo "implement"
    fi
}

# Get agent names for a team
get_team_agents() {
    local team="$1"
    case "$team" in
        explore)
            echo "explorer_1 explorer_2 explorer_3 explorer_4 explorer_5"
            ;;
        plan)
            echo "planner_1 planner_2 planner_3 planner_4 planner_5"
            ;;
        implement)
            echo "implementer_1 implementer_2 implementer_3 implementer_4 implementer_5"
            ;;
        *)
            echo "implementer_1 implementer_2 implementer_3 implementer_4 implementer_5"
            ;;
    esac
}

# Get agent capabilities for a team
get_team_capabilities() {
    local team="$1"
    case "$team" in
        explore)
            echo "research investigation discovery analysis reporting"
            ;;
        plan)
            echo "architecture design strategy planning decision-making"
            ;;
        implement)
            echo "coding building integration execution testing"
            ;;
        *)
            echo "coding building integration execution testing"
            ;;
    esac
}

# ──────────────────────────────────────────────────────────────────────────────
# ARGUMENT PARSING
# ──────────────────────────────────────────────────────────────────────────────

TASK_DESCRIPTION=""
MAX_ITERATIONS=50
COMPLETION_PROMISE="DONE"  # DEFAULT: "DONE"
TEAM_MODE=true  # DEFAULT: Team mode enabled
TEAM_NAME=""

# Depth/thoroughness controls
# DEFAULTS: Maximum thoroughness - user shouldn't have to ask for quality
AGENT_MODEL="opus"        # DEFAULT: opus (smartest, most thorough)
AGENT_MAX_TURNS=200       # DEFAULT: 200 turns per agent (maximum exploration)
DEPTH_MODE="maximum"      # DEFAULT: maximum (expert-level thoroughness)

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --max-iterations)
            MAX_ITERATIONS="$2"
            shift 2
            ;;
        --completion-promise)
            COMPLETION_PROMISE="$2"
            shift 2
            ;;
        --team)
            TEAM_MODE=true
            shift
            ;;
        --no-team)
            TEAM_MODE=false
            shift
            ;;
        --team-name)
            TEAM_NAME="$2"
            shift 2
            ;;
        # Depth control options (DEFAULT is maximum - use flags only to REDUCE depth)
        --quick|--fast)
            DEPTH_MODE="quick"
            AGENT_MODEL="haiku"
            AGENT_MAX_TURNS=20
            shift
            ;;
        --normal)
            DEPTH_MODE="normal"
            AGENT_MODEL="sonnet"
            AGENT_MAX_TURNS=100
            shift
            ;;
        --deep)
            DEPTH_MODE="deep"
            AGENT_MODEL="sonnet"
            AGENT_MAX_TURNS=150
            shift
            ;;
        --maximum|--opus)
            DEPTH_MODE="maximum"
            AGENT_MODEL="opus"
            AGENT_MAX_TURNS=200
            shift
            ;;
        --model)
            AGENT_MODEL="$2"
            shift 2
            ;;
        --turns)
            AGENT_MAX_TURNS="$2"
            shift 2
            ;;
        *)
            # First non-flag argument is the task description
            if [[ -z "${TASK_DESCRIPTION}" ]]; then
                TASK_DESCRIPTION="$1"
            fi
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z "${TASK_DESCRIPTION}" ]]; then
    echo "Error: No task description provided" >&2
    echo "Usage: /ralph-loop \"Task description\" [--completion-promise \"PROMISE\"] [--max-iterations N]" >&2
    echo "   or: /ralph-loop \"Task description\" --no-team  # single agent mode" >&2
    exit 2
fi

if ! [[ "${MAX_ITERATIONS}" =~ ^[0-9]+$ ]] || (( MAX_ITERATIONS < 1 )); then
    echo "Error: max-iterations must be a positive integer" >&2
    exit 2
fi

# Route to team if team mode enabled (DEFAULT)
if [[ "${TEAM_MODE}" == "true" ]]; then
    if [[ -z "${TEAM_NAME}" ]]; then
        TEAM_NAME=$(route_to_team "${TASK_DESCRIPTION}")
    fi
    echo "🎯 Team mode: ENABLED (default) — routed to '${TEAM_NAME}' team (5 agents)"
else
    echo "👤 Team mode: DISABLED — single agent mode"
fi

# ──────────────────────────────────────────────────────────────────────────────
# YAWL CONTEXT DETECTION
# ──────────────────────────────────────────────────────────────────────────────

is_yawl_context() {
    # Check for YAWL markers: pom.xml, scripts/dx.sh, scripts/observatory/
    [[ -f "${REPO_ROOT}/pom.xml" ]] && \
    [[ -f "${REPO_ROOT}/scripts/dx.sh" ]] && \
    [[ -d "${REPO_ROOT}/scripts/observatory" ]]
}

ENABLE_SMART_VALIDATION=false
if is_yawl_context; then
    ENABLE_SMART_VALIDATION=true
    echo "🎯 YAWL context detected - smart validation enabled"
fi

# ──────────────────────────────────────────────────────────────────────────────
# STATE INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

STATE_FILE="${RALPH_STATE_DIR}/loop-state.json"
EDIT_TRACKER="${RALPH_STATE_DIR}/loop-edits.txt"
TEAM_STATE_FILE="${RALPH_STATE_DIR}/team-state.json"

# Get team agents and capabilities if in team mode
TEAM_AGENTS=""
TEAM_CAPABILITIES=""
if [[ "${TEAM_MODE}" == "true" ]]; then
    TEAM_AGENTS=$(get_team_agents "${TEAM_NAME}")
    TEAM_CAPABILITIES=$(get_team_capabilities "${TEAM_NAME}")
fi

# Initialize state file with loop metadata
cat > "${STATE_FILE}" << EOF
{
  "task_description": $(printf '%s\n' "${TASK_DESCRIPTION}" | jq -R -s .),
  "completion_promise": $(printf '%s\n' "${COMPLETION_PROMISE}" | jq -R -s .),
  "max_iterations": ${MAX_ITERATIONS},
  "current_iteration": 1,
  "enable_smart_validation": ${ENABLE_SMART_VALIDATION},
  "started_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "active",
  "team_mode": ${TEAM_MODE},
  "team_name": $(printf '%s\n' "${TEAM_NAME}" | jq -R .),
  "team_agents": $(printf '%s\n' "${TEAM_AGENTS}" | jq -R -s 'split(" ")'),
  "team_capabilities": $(printf '%s\n' "${TEAM_CAPABILITIES}" | jq -R -s 'split(" ")'),
  "depth_mode": "${DEPTH_MODE}",
  "agent_model": "${AGENT_MODEL}",
  "agent_max_turns": ${AGENT_MAX_TURNS},
  "coordinator_mode": "spawn_only",
  "banned_tools": ["Bash", "Read", "Edit", "Write", "MultiEdit", "Grep", "Glob"]
}
EOF

# Mark loop as active for the ban hook
echo "active" > "${RALPH_STATE_DIR}/status"
echo "${AGENT_MODEL}" > "${RALPH_STATE_DIR}/agent-model"
echo "${AGENT_MAX_TURNS}" > "${RALPH_STATE_DIR}/agent-max-turns"

# Initialize team state file if in team mode
if [[ "${TEAM_MODE}" == "true" ]]; then
    cat > "${TEAM_STATE_FILE}" << EOF
{
  "team_id": "tau-${TEAM_NAME}-$(date +%s)",
  "team_name": "${TEAM_NAME}",
  "agents": $(printf '%s\n' "${TEAM_AGENTS}" | jq -R -s 'split(" ")'),
  "agent_count": 5,
  "status": "active",
  "mailbox": [],
  "checkpoints": [],
  "started_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
fi

# Initialize edit tracker (empty - no files edited yet)
> "${EDIT_TRACKER}"

# ──────────────────────────────────────────────────────────────────────────────
# PROMPT GENERATION FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

# Generate single-agent prompt (original behavior)
generate_single_prompt() {
    cat << PROMPT

## Ralph Loop Iteration 1

**Task**: ${TASK_DESCRIPTION}

**Completion Promise**: When you are confident the task is complete, output this exact text:
\`\`\`
${COMPLETION_PROMISE}
\`\`\`

**What happens next**: When you output the promise, the loop will check if your work meets validation standards. If issues are found, the prompt will be re-injected with details. Otherwise the loop exits successfully.

**Current status**: Ready for iteration 1. Begin work on the task above.

PROMPT
}

# Generate team-based prompt that spawns 5 agents
generate_team_prompt() {
    local team_lower="${TEAM_NAME,,}"
    local agent_prefix=""
    local subagent_type=""
    local thoroughness=""
    local agent_instructions=""

    # Map team to agent types and set thoroughness
    case "${TEAM_NAME}" in
        explore)
            agent_prefix="explorer"
            subagent_type="Explore"
            thoroughness="very thorough"
            agent_instructions="Explore deeply. Use Grep extensively with multiple patterns. Read ALL relevant files. Cross-reference findings. Build comprehensive understanding. Take your time - thoroughness over speed."
            ;;
        plan)
            agent_prefix="planner"
            subagent_type="Plan"
            thoroughness="very thorough"
            agent_instructions="Analyze comprehensively. Consider multiple approaches. Document trade-offs. Create detailed implementation plans. Think through edge cases and failure modes."
            ;;
        implement)
            agent_prefix="implementer"
            subagent_type="yawl-engineer"
            thoroughness="thorough"
            agent_instructions="Implement completely. Handle all edge cases. Add comprehensive error handling. Write tests. Ensure production-ready code."
            ;;
        *)
            agent_prefix="implementer"
            subagent_type="yawl-engineer"
            thoroughness="thorough"
            agent_instructions="Complete the task thoroughly."
            ;;
    esac

    cat << PROMPT

# 🚨 CRITICAL: COORDINATOR MODE - SPAWN AGENTS ONLY 🚨

## 🚫 TOOL BAN IN EFFECT

You are BANNED from using these tools:
- ❌ Bash
- ❌ Read
- ❌ Edit
- ❌ Write
- ❌ Grep
- ❌ Glob
- ❌ AskUserQuestion

Your ONLY allowed actions:
- ✅ Agent(...) - Spawn subagents to do work
- ✅ TaskOutput - Get results from agents

**NO QUESTIONS.** You are the expert. Make decisions. Execute.

**If you try to use a banned tool, the hook will BLOCK it.**

You are the COORDINATOR. Your job is to SPAWN agents, not do work yourself.

## ⏱️ ANTI-PREMATURE-EXIT PROTOCOL

**DO NOT exit early. The user wants THOROUGHNESS, not speed.**

Before you can output "${COMPLETION_PROMISE}", you MUST:
1. ✅ Have spawned 5 agents with max_turns=100 each
2. ✅ Each agent has explored AT LEAST 20+ files
3. ✅ All findings have been cross-referenced and validated
4. ✅ A comprehensive report has been generated

## Spawn 5 DEEP-EXPLORATION Agents

Use the Agent tool with these CRITICAL parameters:
- **model="${AGENT_MODEL}"** (smart model for deep thinking)
- **max_turns=${AGENT_MAX_TURNS}** (allows ${AGENT_MAX_TURNS} API round-trips per agent)
- **isolation="worktree"** (isolated workspace for safety)

Agent(description="${agent_prefix} phase 1", prompt="You are subagent 1 of 5 on a DEEP EXPLORATION mission.

TASK: ${TASK_DESCRIPTION}

YOUR SCOPE: Files/modules starting with A-F

INSTRUCTIONS:
${agent_instructions}

METHOD:
1. Use Grep with MULTIPLE related patterns (not just one)
2. Read EVERY file that matches - do not skip
3. Follow import chains to understand dependencies
4. Note ALL occurrences with line numbers
5. Cross-reference with other files you find
6. Build a COMPREHENSIVE picture

DO NOT RUSH. Take 50+ tool calls if needed. Thoroughness > speed.

Report format:
- Total files examined: N
- Patterns searched: list
- Findings: detailed with file:line references
- Confidence level: high/medium/low

Begin deep exploration now.", subagent_type="${subagent_type}", model="${AGENT_MODEL}", max_turns=${AGENT_MAX_TURNS})

Agent(description="${agent_prefix} phase 2", prompt="You are subagent 2 of 5 on a DEEP EXPLORATION mission.

TASK: ${TASK_DESCRIPTION}

YOUR SCOPE: Files/modules starting with G-L

INSTRUCTIONS:
${agent_instructions}

METHOD:
1. Use Grep with MULTIPLE related patterns (not just one)
2. Read EVERY file that matches - do not skip
3. Follow import chains to understand dependencies
4. Note ALL occurrences with line numbers
5. Cross-reference with other files you find
6. Build a COMPREHENSIVE picture

DO NOT RUSH. Take 50+ tool calls if needed. Thoroughness > speed.

Report format:
- Total files examined: N
- Patterns searched: list
- Findings: detailed with file:line references
- Confidence level: high/medium/low

Begin deep exploration now.", subagent_type="${subagent_type}", model="${AGENT_MODEL}", max_turns=${AGENT_MAX_TURNS})

Agent(description="${agent_prefix} phase 3", prompt="You are subagent 3 of 5 on a DEEP EXPLORATION mission.

TASK: ${TASK_DESCRIPTION}

YOUR SCOPE: Files/modules starting with M-R

INSTRUCTIONS:
${agent_instructions}

METHOD:
1. Use Grep with MULTIPLE related patterns (not just one)
2. Read EVERY file that matches - do not skip
3. Follow import chains to understand dependencies
4. Note ALL occurrences with line numbers
5. Cross-reference with other files you find
6. Build a COMPREHENSIVE picture

DO NOT RUSH. Take 50+ tool calls if needed. Thoroughness > speed.

Report format:
- Total files examined: N
- Patterns searched: list
- Findings: detailed with file:line references
- Confidence level: high/medium/low

Begin deep exploration now.", subagent_type="${subagent_type}", model="${AGENT_MODEL}", max_turns=${AGENT_MAX_TURNS})

Agent(description="${agent_prefix} phase 4", prompt="You are subagent 4 of 5 on a DEEP EXPLORATION mission.

TASK: ${TASK_DESCRIPTION}

YOUR SCOPE: Files/modules starting with S-Z

INSTRUCTIONS:
${agent_instructions}

METHOD:
1. Use Grep with MULTIPLE related patterns (not just one)
2. Read EVERY file that matches - do not skip
3. Follow import chains to understand dependencies
4. Note ALL occurrences with line numbers
5. Cross-reference with other files you find
6. Build a COMPREHENSIVE picture

DO NOT RUSH. Take 50+ tool calls if needed. Thoroughness > speed.

Report format:
- Total files examined: N
- Patterns searched: list
- Findings: detailed with file:line references
- Confidence level: high/medium/low

Begin deep exploration now.", subagent_type="${subagent_type}", model="${AGENT_MODEL}", max_turns=${AGENT_MAX_TURNS})

Agent(description="${agent_prefix} phase 5", prompt="You are subagent 5 of 5 on a DEEP EXPLORATION mission.

TASK: ${TASK_DESCRIPTION}

YOUR SCOPE: Tests, validation, configuration, and integration aspects

INSTRUCTIONS:
${agent_instructions}

METHOD:
1. Search test directories for related test patterns
2. Read ALL relevant test files
3. Check configuration files (pom.xml, .toml, .json configs)
4. Look for integration points and dependencies
5. Identify validation requirements
6. Note any gaps or missing coverage

DO NOT RUSH. Take 50+ tool calls if needed. Thoroughness > speed.

Report format:
- Total files examined: N
- Test patterns found: list
- Configuration analysis: details
- Integration points: list
- Coverage gaps: list
- Confidence level: high/medium/low

Begin deep exploration now.", subagent_type="${subagent_type}", model="${AGENT_MODEL}", max_turns=${AGENT_MAX_TURNS})

---

## After ALL 5 subagents complete:

1. ⏳ Wait for ALL 5 reports (do not proceed early)
2. 📊 Consolidate findings into comprehensive report
3. 🔍 Verify no gaps in coverage
4. 📝 If implementation needed, spawn MORE agents (don't do it yourself)
5. ✅ Run validation: \`bash scripts/dx.sh all\`
6. 🎯 When TRULY complete (not before), output: **${COMPLETION_PROMISE}**

## ⚠️ ABSOLUTELY DO NOT:
- DO NOT exit after 15-60 minutes (take HOURS if needed)
- DO NOT use haiku model (always sonnet for deep work)
- DO NOT skip max_turns=100 parameter
- DO NOT run Bash/Read/Grep yourself (agents do this)
- DO NOT declare done until ALL agents report comprehensive findings
- DO NOT rush - the user explicitly wants THOROUGHNESS

**Quality > Speed. Completeness > Efficiency. Deep > Shallow.**

PROMPT
}

# ──────────────────────────────────────────────────────────────────────────────
# PRE-ITERATION VALIDATION (YAWL ONLY)
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${ENABLE_SMART_VALIDATION}" == "true" ]]; then
    echo "🔍 Checking initial validation state..."

    # Run dx.sh to establish baseline
    if bash "${REPO_ROOT}/scripts/dx.sh" all > /tmp/ralph-dx-baseline.log 2>&1; then
        DX_BASELINE_GREEN=true
        echo "   ✅ Baseline validation GREEN - smart mode enabled (skip validation on iteration 1)"
    else
        DX_BASELINE_GREEN=false
        echo "   ⚠️  Baseline validation RED - validation will run on all iterations"
    fi

    # Store baseline status in state
    jq --arg baseline "$DX_BASELINE_GREEN" '.dx_baseline_green = ($baseline == "true")' "${STATE_FILE}" > "${STATE_FILE}.tmp"
    mv "${STATE_FILE}.tmp" "${STATE_FILE}"
fi

# ──────────────────────────────────────────────────────────────────────────────
# ACTIVATE STOP HOOK MECHANISM
# ──────────────────────────────────────────────────────────────────────────────

# Mark loop as active so stop-hook.sh will intercept exits
export RALPH_LOOP_ACTIVE=true
export RALPH_STATE_FILE="${STATE_FILE}"
export RALPH_COMPLETION_PROMISE="${COMPLETION_PROMISE}"
export RALPH_TEAM_MODE="${TEAM_MODE}"
export RALPH_TEAM_NAME="${TEAM_NAME}"

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "🚀 Ralph Loop Starting"
echo "───────────────────────────────────────────────────────────────────────────"
echo "📋 Task: ${TASK_DESCRIPTION}"
echo "🎯 Promise: ${COMPLETION_PROMISE}"
echo "🔄 Max iterations: ${MAX_ITERATIONS}"
echo "🧠 Smart validation: $([ "${ENABLE_SMART_VALIDATION}" == "true" ] && echo "ENABLED" || echo "DISABLED")"
echo "📊 Depth mode: ${DEPTH_MODE} (model=${AGENT_MODEL}, turns=${AGENT_MAX_TURNS})"
if [[ "${TEAM_MODE}" == "true" ]]; then
    echo "👥 Team mode: ENABLED (${TEAM_NAME} - 5 agents)"
fi
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# GENERATE PROMPT (SINGLE OR TEAM MODE)
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${TEAM_MODE}" == "true" ]]; then
    # TEAM MODE: Generate prompt that spawns 5 agents in parallel
    generate_team_prompt
else
    # SINGLE MODE: Original prompt
    generate_single_prompt
fi

# Return success - the stop-hook takes over from here
exit 0
