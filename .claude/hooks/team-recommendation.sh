#!/bin/bash
# Team Recommendation Hook - Intelligent Team Formation
# Detects multi-quantum tasks and recommends team (τ) mode
# Runs at session start or when task analysis is triggered
#
# Exit codes:
# 0 = recommendation made, proceed
# 2 = recommendation with guidance (informational only)
#
# This hook makes team recommendations the DEFAULT for suitable tasks,
# requiring explicit user override only if they prefer single session.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FACTS_DIR="${PROJECT_ROOT}/.claude/facts"

# Input: task description from Claude Code or user prompt
TASK_DESCRIPTION="${1:-}"
SESSION_CONTEXT="${2:-}"

# Colors
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
NC='\033[0m'

if [[ -z "${TASK_DESCRIPTION}" ]]; then
    exit 0
fi

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}[team-recommendation] Analyzing task for team mode...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Initialize counters
QUANTUM_COUNT=0
TEAM_INDICATORS=0
declare -a DETECTED_QUANTUMS=()

# Check for multi-quantum indicators in task description
detect_quantums() {
    local desc="$1"
    local desc_lower="${desc,,}"

    # Engine semantic patterns
    if [[ $desc_lower =~ (engine|ynetrunner|workflow|state|transition|guard|deadlock|task.completion) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Engine Semantic (yawl/engine/**)")
        ((TEAM_INDICATORS++))
    fi

    # Schema/XSD patterns
    if [[ $desc_lower =~ (schema|xsd|specification|type.definition|workflow.type|element) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Schema Definition (schema/**, exampleSpecs/**)")
        ((TEAM_INDICATORS++))
    fi

    # Integration/MCP/A2A patterns
    if [[ $desc_lower =~ (integration|mcp|a2a|endpoint|event|publisher|listener|z\.ai) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Integration (yawl/integration/**)")
        ((TEAM_INDICATORS++))
    fi

    # Resourcing/allocation patterns
    if [[ $desc_lower =~ (resource|allocation|pool|workqueue|queue|drain) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Resourcing (yawl/resourcing/**)")
        ((TEAM_INDICATORS++))
    fi

    # Testing patterns
    if [[ $desc_lower =~ (test|junit|coverage|integration.test|e2e) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Testing (yawl/**/src/test/**)")
        ((TEAM_INDICATORS++))
    fi

    # Security patterns
    if [[ $desc_lower =~ (security|auth|crypto|jwt|tls|cert|validation|encryption) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Security (yawl/authentication/**)")
        ((TEAM_INDICATORS++))
    fi

    # Stateless/monitoring patterns
    if [[ $desc_lower =~ (stateless|monitor|export|snapshot|case.data|case.export) ]]; then
        ((QUANTUM_COUNT++))
        DETECTED_QUANTUMS+=("Stateless (yawl/stateless/**)")
        ((TEAM_INDICATORS++))
    fi

    # Collaboration indicators (review, investigation, competing hypotheses)
    if [[ $desc_lower =~ (review|investigate|debug|hypothesis|root.cause|profile|optimiz) ]]; then
        ((TEAM_INDICATORS += 2))
    fi
}

# Check for file conflicts in shared-src.json
check_file_conflicts() {
    if [[ ! -f "${FACTS_DIR}/shared-src.json" ]]; then
        return 1
    fi

    # Parse shared-src.json to see if quantums can run independently
    local conflicts=$(jq -r '.conflicts | length' "${FACTS_DIR}/shared-src.json" 2>/dev/null || echo "0")

    if [[ "$conflicts" -gt 0 ]]; then
        echo -e "${YELLOW}⚠️  File conflicts detected (${conflicts}). Sequential work recommended.${NC}"
        return 1
    fi

    return 0
}

# Main recommendation logic
detect_quantums "${TASK_DESCRIPTION}"

# Display detected quantums
if [[ ${#DETECTED_QUANTUMS[@]} -gt 0 ]]; then
    echo ""
    echo -e "${GREEN}✓ Detected ${QUANTUM_COUNT} quantums:${NC}"
    for quantum in "${DETECTED_QUANTUMS[@]}"; do
        echo -e "  ${MAGENTA}◆${NC} ${quantum}"
    done
    echo ""
fi

# Decide on recommendation
if [[ $QUANTUM_COUNT -ge 2 && $QUANTUM_COUNT -le 5 ]]; then
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}✓ TEAM MODE RECOMMENDED (τ)${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "${CYAN}Team Architecture:${NC}"
    echo -e "  • Lead session (orchestration + synthesis)"
    echo -e "  • ${QUANTUM_COUNT} teammates (2-5 is optimal)"
    echo -e "  • Shared task list with dependencies"
    echo -e "  • Direct teammate messaging for findings"
    echo ""

    # Check if file conflicts exist
    if check_file_conflicts; then
        echo -e "${GREEN}✓ No file conflicts detected. Safe for parallel work.${NC}"
    else
        echo -e "${YELLOW}⚠️  Verify file conflicts before spawning team.${NC}"
    fi

    echo ""
    echo -e "${CYAN}Quick Start:${NC}"
    echo -e "  ${YELLOW}Create a team with ${QUANTUM_COUNT} teammates:${NC}"
    echo ""
    echo -e "  ${BLUE}Example: \"Create an agent team to work on these ${QUANTUM_COUNT}"
    echo -e "  components in parallel. Spawn teammates for: $(printf '%s, ' "${DETECTED_QUANTUMS[@]%% (*}" | sed 's/, $//').${NC}"
    echo ""

    echo -e "${CYAN}Prerequisites:${NC}"
    echo -e "  • Enable experimental teams: ${YELLOW}CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
    echo -e "  • Facts current: ${YELLOW}bash scripts/observatory/observatory.sh${NC}"
    echo -e "  • No blocked tasks: Check pre-team checklist in CLAUDE.md"
    echo ""

    echo -e "${CYAN}Cost Estimate:${NC}"
    echo -e "  • Single session: ~\$C"
    echo -e "  • Team (${QUANTUM_COUNT} teammates): ~\$$(echo "$QUANTUM_COUNT" | awk '{print $1 * 3.5}')C"
    echo -e "  • ROI positive if: investigation + review + collaboration"
    echo ""

    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}[team-recommendation] Ready to spawn team${NC}"
    exit 0

elif [[ $QUANTUM_COUNT -eq 1 ]]; then
    echo ""
    echo -e "${YELLOW}⚠️  Single quantum detected. Use single session (faster, cheaper).${NC}"
    echo -e "   Task scope: $(printf '%s, ' "${DETECTED_QUANTUMS[@]%% (*}" | sed 's/, $//')"
    echo ""
    exit 2

elif [[ $QUANTUM_COUNT -gt 5 ]]; then
    echo ""
    echo -e "${YELLOW}⚠️  ${QUANTUM_COUNT} quantums detected. Too many for single team.${NC}"
    echo -e "   Recommendation: Split into 2-3 sequential phases (max 5 teammates per team)"
    echo ""
    exit 2

else
    echo ""
    echo -e "${YELLOW}ℹ️  Could not detect clear quantums. Analyze task manually.${NC}"
    echo -e "   See CLAUDE.md 'GODSPEED Quantum Selection' section for guidance."
    echo ""
    exit 0
fi
