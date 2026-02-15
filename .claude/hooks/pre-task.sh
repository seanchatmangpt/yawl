#!/bin/bash
# Pre-Task Hook - Claude Code Native Coordination
# Auto-assigns YAWL agent based on task description

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Arguments
TASK_DESCRIPTION="${1:-}"
TASK_ID="${2:-}"
AGENT_TYPE="${3:-}"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}[pre-task] Analyzing task for agent assignment...${NC}"

# Auto-assign agent based on task description
if [[ -z "${AGENT_TYPE}" ]] && [[ -n "${TASK_DESCRIPTION}" ]]; then
    case "${TASK_DESCRIPTION,,}" in
        *engine*|*ynet*|*workflow*|*pattern*|*ynetrunner*)
            AGENT_TYPE="yawl-engineer"
            ;;
        *valid*|*schema*|*xsd*|*specification*)
            AGENT_TYPE="yawl-validator"
            ;;
        *architect*|*design*|*interface*|*system*)
            AGENT_TYPE="yawl-architect"
            ;;
        *mcp*|*a2a*|*integration*|*z.ai*)
            AGENT_TYPE="yawl-integrator"
            ;;
        *test*|*junit*|*coverage*|*testcase*)
            AGENT_TYPE="yawl-tester"
            ;;
        *review*|*standard*|*hyper*|*quality*)
            AGENT_TYPE="yawl-reviewer"
            ;;
        *deploy*|*production*|*config*|*tomcat*)
            AGENT_TYPE="yawl-production-validator"
            ;;
        *performance*|*benchmark*|*optim*|*profile*)
            AGENT_TYPE="yawl-performance-benchmarker"
            ;;
        *)
            AGENT_TYPE="yawl-engineer"
            ;;
    esac
fi

if [[ -n "${AGENT_TYPE}" ]]; then
    echo -e "${GREEN}[pre-task] Recommended agent: ${AGENT_TYPE}${NC}"
    echo -e "${YELLOW}[pre-task] Use: Task(\"Description\", \"Task details\", \"${AGENT_TYPE}\")${NC}"
fi

# Log session
HISTORY_DIR="${PROJECT_ROOT}/.claude/memory"
mkdir -p "${HISTORY_DIR}"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "[${TIMESTAMP}] task: ${TASK_DESCRIPTION} -> ${AGENT_TYPE}" >> "${HISTORY_DIR}/history.log" 2>/dev/null || true

echo -e "${GREEN}[pre-task] Ready${NC}"
exit 0
