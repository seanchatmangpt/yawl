#!/bin/bash
# YAWL Review Skill - Claude Code 2026 Best Practices
# Usage: /yawl-review [path] [--hyper-standards] [--security]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Review Skill - Code review with HYPER_STANDARDS enforcement

Usage: /yawl-review [options] [path]

Options:
  --hyper-standards    Run full HYPER_STANDARDS check (default: true)
  --security           Focus on security issues
  --performance        Focus on performance issues
  --format=TYPE        Output format (text, json)
  -h, --help           Show this help message

HYPER_STANDARDS Checks:
  1. NO DEFERRED WORK - No TODO/FIXME/XXX/HACK markers
  2. NO MOCKS - No mock/stub/fake/test/demo/sample behavior
  3. NO STUBS - No empty returns, no-op methods, placeholder data
  4. NO FALLBACKS - No silent degradation to fake behavior
  5. NO LIES - Code behavior must match documentation

Examples:
  /yawl-review                              # Review src/ with HYPER_STANDARDS
  /yawl-review src/org/yawlfoundation/yawl/engine/  # Review specific path
  /yawl-review --security src/              # Security-focused review
EOF
}

# Parse arguments
TARGET_PATH="${PROJECT_ROOT}/src"
HYPER_STANDARDS=true
SECURITY=false
PERFORMANCE=false
FORMAT="text"

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --hyper-standards)
            HYPER_STANDARDS=true
            shift
            ;;
        --security)
            SECURITY=true
            shift
            ;;
        --performance)
            PERFORMANCE=true
            shift
            ;;
        --format=*)
            FORMAT="${1#*=}"
            shift
            ;;
        src/*|test/*)
            TARGET_PATH="${PROJECT_ROOT}/$1"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

echo -e "${BLUE}[yawl-review] YAWL Code Review${NC}"
echo -e "${BLUE}[yawl-review] Target: ${TARGET_PATH}${NC}"
echo ""

ISSUES=0

# HYPER_STANDARDS check
if [[ "${HYPER_STANDARDS}" == "true" ]]; then
    echo -e "${YELLOW}=== HYPER_STANDARDS Check ===${NC}"

    # Check for TODO-like patterns (14 patterns from hyper-validate.sh)
    echo -e "${BLUE}Checking for deferred work markers...${NC}"
    TODO_FILES=$(grep -rl --include="*.java" \
        -E "(TODO|FIXME|XXX|HACK|LATER|@stub|@mock|@fake|Not implemented|Coming soon|Placeholder|For demo purposes)" \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${TODO_FILES}" ]]; then
        echo -e "${RED}Found deferred work markers:${NC}"
        echo "${TODO_FILES}"
        ((ISSUES++))
    else
        echo -e "${GREEN}No deferred work markers found${NC}"
    fi

    # Check for mock patterns
    echo -e "${BLUE}Checking for mock method patterns...${NC}"
    MOCK_FILES=$(grep -rl --include="*.java" \
        -E "(mock|stub|fake|demo|sample|test)[A-Za-z]*\s*\(" \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${MOCK_FILES}" ]]; then
        echo -e "${RED}Found mock method patterns:${NC}"
        echo "${MOCK_FILES}"
        ((ISSUES++))
    else
        echo -e "${GREEN}No mock method patterns found${NC}"
    fi

    # Check for empty returns (stub implementations)
    echo -e "${BLUE}Checking for stub implementations...${NC}"
    EMPTY_RETURNS=$(grep -rn --include="*.java" \
        -E 'return\s+"";\s*$|return\s+null;\s*//|public\s+void\s+\w+\([^)]*\)\s*\{\s*\}' \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${EMPTY_RETURNS}" ]]; then
        echo -e "${RED}Found suspicious empty returns:${NC}"
        echo "${EMPTY_RETURNS}" | head -20
        ((ISSUES++))
    else
        echo -e "${GREEN}No suspicious empty returns found${NC}"
    fi

    # Check for mock mode flags
    echo -e "${BLUE}Checking for mock mode flags...${NC}"
    MOCK_FLAGS=$(grep -rn --include="*.java" \
        -E '(MOCK_MODE|useMock|isTestMode|testing\s*=\s*true)' \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${MOCK_FLAGS}" ]]; then
        echo -e "${RED}Found mock mode flags:${NC}"
        echo "${MOCK_FLAGS}"
        ((ISSUES++))
    else
        echo -e "${GREEN}No mock mode flags found${NC}"
    fi

    echo ""
fi

# Security check
if [[ "${SECURITY}" == "true" ]]; then
    echo -e "${YELLOW}=== Security Check ===${NC}"

    # Check for hardcoded secrets
    echo -e "${BLUE}Checking for hardcoded secrets...${NC}"
    SECRETS=$(grep -rn --include="*.java" \
        -E "(password|secret|api[_-]?key|token)\s*=\s*\"[^\"]+\"" \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${SECRETS}" ]]; then
        echo -e "${RED}Found potential hardcoded secrets:${NC}"
        echo "${SECRETS}"
        ((ISSUES++))
    else
        echo -e "${GREEN}No hardcoded secrets found${NC}"
    fi

    # Check for SQL injection vulnerabilities
    echo -e "${BLUE}Checking for SQL injection patterns...${NC}"
    SQL_INJECTION=$(grep -rn --include="*.java" \
        -E "executeQuery\s*\(\s*\"[^\"]*\"\s*\+" \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${SQL_INJECTION}" ]]; then
        echo -e "${RED}Found potential SQL injection:${NC}"
        echo "${SQL_INJECTION}"
        ((ISSUES++))
    else
        echo -e "${GREEN}No SQL injection patterns found${NC}"
    fi

    echo ""
fi

# Performance check
if [[ "${PERFORMANCE}" == "true" ]]; then
    echo -e "${YELLOW}=== Performance Check ===${NC}"

    # Check for string concatenation in loops
    echo -e "${BLUE}Checking for inefficient string concatenation...${NC}"
    STRING_CONCAT=$(grep -rn --include="*.java" \
        -E "for\s*\([^)]*\)\s*\{[^}]*\+\s*\"" \
        "${TARGET_PATH}" 2>/dev/null || true)

    if [[ -n "${STRING_CONCAT}" ]]; then
        echo -e "${YELLOW}Found potential string concatenation in loops:${NC}"
        echo "${STRING_CONCAT}"
    else
        echo -e "${GREEN}No string concatenation issues found${NC}"
    fi

    echo ""
fi

# Summary
echo -e "${YELLOW}=== Review Summary ===${NC}"

if [[ ${ISSUES} -eq 0 ]]; then
    echo -e "${GREEN}No issues found - code passes HYPER_STANDARDS${NC}"
else
    echo -e "${RED}Found ${ISSUES} issue(s) requiring attention${NC}"
fi

exit ${ISSUES}
