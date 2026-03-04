#!/bin/bash
# Pre-Task Hook - Consolidate Agent Assignment, Intelligence Check, & Environment Verification
#
# This hook runs before task execution and:
# 1. Auto-assigns YAWL agent based on task description
# 2. Validates intelligence (JIRA ticket) criteria if available
# 3. Checks Observatory freshness and dx.sh baseline
# 4. Logs task to session history
#
# Merged from:
#   - pre-task.sh (agent auto-assignment)
#   - intelligence-pre-task.sh (ticket validation)

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

echo -e "${BLUE}[pre-task] Preparing task environment...${NC}"

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
    echo -e "${GREEN}[pre-task] Agent: ${AGENT_TYPE}${NC}"
fi

# Validate intelligence (JIRA) criteria if available
INTELLIGENCE_BIN="${PROJECT_ROOT}/rust/yawl-hooks/target/release/yawl-jira"
if [[ -x "${INTELLIGENCE_BIN}" ]]; then
    if output=$("${INTELLIGENCE_BIN}" pre-write 2>/dev/null); then
        if echo "$output" | grep -q '"decision": "approve"'; then
            echo -e "${GREEN}[pre-task] Intelligence validation: PASSED${NC}"
        fi
    fi
fi

# Check Observatory freshness
if [[ -f "${PROJECT_ROOT}/pom.xml" ]]; then
    POM_HASH_FILE="${PROJECT_ROOT}/.yawl/.dx-state/observatory-pom-hash.txt"
    if [[ -f "${POM_HASH_FILE}" ]]; then
        STORED_HASH=$(cat "${POM_HASH_FILE}" 2>/dev/null || echo "")
        CURRENT_HASH=$(sha256sum "${PROJECT_ROOT}/pom.xml" 2>/dev/null | awk '{print $1}')
        if [[ "${STORED_HASH}" == "${CURRENT_HASH}" ]]; then
            echo -e "${GREEN}[pre-task] Observatory: cached (fresh)${NC}"
        else
            echo -e "${YELLOW}[pre-task] Observatory: cache outdated${NC}"
        fi
    fi
fi

# Log task to session history
HISTORY_DIR="${PROJECT_ROOT}/.claude/memory"
mkdir -p "${HISTORY_DIR}"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "[${TIMESTAMP}] task: ${TASK_DESCRIPTION} -> ${AGENT_TYPE}" >> "${HISTORY_DIR}/history.log" 2>/dev/null || true

echo -e "${GREEN}[pre-task] Ready${NC}"
exit 0
