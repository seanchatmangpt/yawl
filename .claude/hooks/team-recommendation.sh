#!/bin/bash
# Team Recommendation Hook - Intelligent Team Formation
# Detects multi-quantum tasks and recommends team (τ) mode
#
# Exit codes: 0 = proceed, 1 = error

TASK_DESCRIPTION="${1:-}"
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
NC='\033[0m'

if [[ -z "$TASK_DESCRIPTION" ]]; then
    exit 0
fi

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}[team-recommendation] Analyzing task for team mode...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Detect quantums (simple pattern matching)
QUANTUM_COUNT=0
DETECTED=""

desc_lower="${TASK_DESCRIPTION,,}"

if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Engine Semantic (yawl/engine/**)\n"
fi

if [[ $desc_lower =~ (schema|xsd|specification|type.definition) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Schema Definition (schema/**, exampleSpecs/**)\n"
fi

if [[ $desc_lower =~ (integration|mcp|a2a|endpoint|event|publisher) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Integration (yawl/integration/**)\n"
fi

if [[ $desc_lower =~ (resource|resourc|allocation|pool|workqueue|queue) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Resourcing (yawl/resourcing/**)\n"
fi

if [[ $desc_lower =~ (test|junit|coverage|integration.test|e2e) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Testing (yawl/**/src/test/**)\n"
fi

if [[ $desc_lower =~ (security|auth|crypto|jwt|tls|cert|encryption) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Security (yawl/authentication/**)\n"
fi

if [[ $desc_lower =~ (stateless|monitor|export|snapshot|case.data) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Stateless (yawl/stateless/**)\n"
fi

# Display results
echo ""
if [[ $QUANTUM_COUNT -gt 0 ]]; then
    echo -e "${GREEN}✓ Detected ${QUANTUM_COUNT} quantums:${NC}"
    echo -e "$DETECTED"
fi

# Recommendation logic
if [[ $QUANTUM_COUNT -ge 2 && $QUANTUM_COUNT -le 5 ]]; then
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}✓ TEAM MODE RECOMMENDED (τ)${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "${CYAN}Architecture:${NC}"
    echo -e "  • Lead session (orchestration + synthesis)"
    echo -e "  • ${QUANTUM_COUNT} teammates (2-5 is optimal)"
    echo -e "  • Shared task list with dependencies"
    echo -e "  • Direct teammate messaging"
    echo ""
    echo -e "${CYAN}Prerequisites:${NC}"
    echo -e "  • ${YELLOW}CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
    echo -e "  • ${YELLOW}bash scripts/observatory/observatory.sh${NC}"
    echo ""
    echo -e "${GREEN}[team-recommendation] Ready${NC}"
    exit 0

elif [[ $QUANTUM_COUNT -eq 1 ]]; then
    echo -e "${YELLOW}⚠️  Single quantum. Use single session (faster, cheaper).${NC}"
    echo ""
    exit 2

elif [[ $QUANTUM_COUNT -gt 5 ]]; then
    echo -e "${YELLOW}⚠️  Too many quantums (${QUANTUM_COUNT}). Split into phases (max 5 per team).${NC}"
    echo ""
    exit 2

else
    echo -e "${YELLOW}⚠️  Could not detect clear quantums. Provide task with specific quantum keywords.${NC}"
    echo ""
    exit 2
fi
