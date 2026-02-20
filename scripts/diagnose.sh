#!/usr/bin/env bash
# ==========================================================================
# diagnose.sh — 80/20 Dev Mistake Detection for YAWL
#
# Catches common development mistakes BEFORE wasting build time:
#  1. Maven version compatibility (pom.xml vs installed)
#  2. Java/JDK availability and version
#  3. Stale/conflicting dependencies
#  4. HYPER_STANDARDS violations (TODO/FIXME, mocks, stubs)
#  5. Failed test results (suggests root cause)
#  6. Docker/container issues (services running)
#  7. Build cache corruption (suggests mvn clean)
#
# Usage:
#   bash scripts/diagnose.sh              # Full diagnostic scan
#   bash scripts/diagnose.sh quick        # Skip slow checks (deps, docker)
#   bash scripts/diagnose.sh --verbose    # Show all details
#
# Exit codes:
#   0 = All checks passed
#   1 = Warnings detected (non-fatal)
#   2 = Errors detected (likely build will fail)
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Flags
QUICK_MODE=0
VERBOSE=0
PROFILE_MODE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        quick)     QUICK_MODE=1; shift ;;
        -v|--verbose) VERBOSE=1; shift ;;
        -h|--help) cat << 'EOF'
diagnose.sh — 80/20 Dev Mistake Detection

Usage:
  bash scripts/diagnose.sh              # Full diagnostic scan
  bash scripts/diagnose.sh quick        # Skip slow checks
  bash scripts/diagnose.sh -v           # Verbose output

Checks:
  ✓ Java/JDK availability and version
  ✓ Maven version and compatibility
  ✓ Dependency tree warnings (slow)
  ✓ HYPER_STANDARDS violations in changed files
  ✓ Failed test output analysis
  ✓ Docker/container availability
  ✓ Build cache issues

Exit codes:
  0 = All checks passed
  1 = Warnings (non-fatal)
  2 = Errors (likely build failure)
EOF
            exit 0 ;;
        *)        echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
ERRORS=0
WARNINGS=0
CHECKS_PASSED=0

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 1: Java/JDK Availability and Version
# ═══════════════════════════════════════════════════════════════════════════
check_java() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 1] Java/JDK${NC}"

    if ! command -v java &>/dev/null; then
        echo -e "${RED}ERROR: java not found${NC}" >&2
        echo "  Set JAVA_HOME or ensure java is in PATH" >&2
        ((ERRORS++))
        return
    fi

    JAVA_VERSION=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]*' || true)
    if [[ -z "$JAVA_VERSION" ]]; then
        JAVA_VERSION=$(java -version 2>&1 | head -1)
    fi

    if ! echo "$JAVA_VERSION" | grep -qE '21|25'; then
        echo -e "${YELLOW}WARNING: Java version not 21+ (found: ${JAVA_VERSION})${NC}" >&2
        echo "  YAWL requires Java 21+. Expected version 21.0.0 or later" >&2
        ((WARNINGS++))
    else
        [[ $VERBOSE -eq 1 ]] && echo "  Java version: $JAVA_VERSION"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 2: Maven Version and pom.xml Compatibility
# ═══════════════════════════════════════════════════════════════════════════
check_maven() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 2] Maven${NC}"

    if ! command -v mvn &>/dev/null; then
        echo -e "${RED}ERROR: mvn not found${NC}" >&2
        echo "  Ensure Maven is installed: install Maven 3.9+ or use mvnd" >&2
        ((ERRORS++))
        return
    fi

    MVN_VERSION=$(mvn --version 2>&1 | head -1)
    if ! echo "$MVN_VERSION" | grep -qE '3\.9|4\.'; then
        echo -e "${YELLOW}WARNING: Maven version not 3.9+ (found: ${MVN_VERSION})${NC}" >&2
        echo "  Upgrade Maven: apt-get install maven or download from https://maven.apache.org" >&2
        ((WARNINGS++))
    else
        [[ $VERBOSE -eq 1 ]] && echo "  Maven version: $MVN_VERSION"
        ((CHECKS_PASSED++))
    fi

    # Check pom.xml modelVersion
    if [[ -f "${REPO_ROOT}/pom.xml" ]]; then
        if grep -q '<modelVersion>4.0.0</modelVersion>' "${REPO_ROOT}/pom.xml"; then
            [[ $VERBOSE -eq 1 ]] && echo "  pom.xml modelVersion: 4.0.0 (compatible)"
            ((CHECKS_PASSED++))
        else
            echo -e "${YELLOW}WARNING: pom.xml uses old modelVersion${NC}" >&2
            ((WARNINGS++))
        fi
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 3: Stale Dependencies (skip if QUICK_MODE=1)
# ═══════════════════════════════════════════════════════════════════════════
check_dependencies() {
    if [[ $QUICK_MODE -eq 1 ]]; then
        [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 3] Dependencies (SKIPPED in quick mode)${NC}"
        return
    fi

    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 3] Dependencies${NC}"

    # Try to detect obvious dependency issues
    if [[ -f "${REPO_ROOT}/pom.xml" ]]; then
        # Check for version ranges (dangerous)
        if grep -qE '\[|,\]' "${REPO_ROOT}/pom.xml" 2>/dev/null; then
            echo -e "${YELLOW}WARNING: pom.xml contains version ranges${NC}" >&2
            echo "  Version ranges (e.g., [1.0,2.0)) cause non-reproducible builds" >&2
            echo "  Fix: Use fixed versions (e.g., 1.5.3) in pom.xml" >&2
            ((WARNINGS++))
        else
            [[ $VERBOSE -eq 1 ]] && echo "  No dangerous version ranges detected"
            ((CHECKS_PASSED++))
        fi

        # Check for SNAPSHOT dependencies in production
        if grep -q 'SNAPSHOT' "${REPO_ROOT}/pom.xml" 2>/dev/null; then
            SNAPSHOT_COUNT=$(grep -c 'SNAPSHOT' "${REPO_ROOT}/pom.xml" || true)
            [[ $VERBOSE -eq 1 ]] && echo "  Found $SNAPSHOT_COUNT SNAPSHOT dependencies (acceptable in development)"
        fi
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 4: HYPER_STANDARDS Violations (TODO/FIXME, mocks, stubs)
# ═══════════════════════════════════════════════════════════════════════════
check_standards() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 4] HYPER_STANDARDS Violations${NC}"

    local violations_found=0
    local changed_files=""

    # Get list of changed files
    if [[ -d "${REPO_ROOT}/.git" ]]; then
        changed_files=$(git -C "${REPO_ROOT}" diff --name-only HEAD 2>/dev/null || true)
        changed_files+=$'\n'$(git -C "${REPO_ROOT}" diff --name-only --cached 2>/dev/null || true)
        changed_files+=$'\n'$(git -C "${REPO_ROOT}" ls-files --others --exclude-standard 2>/dev/null || true)
    fi

    # Filter to Java files only
    local java_files=$(echo "$changed_files" | grep -E '\.java$' | grep -E '/(src|test)/' | sort -u || true)

    if [[ -z "$java_files" ]]; then
        [[ $VERBOSE -eq 1 ]] && echo "  No changed Java files to check"
        ((CHECKS_PASSED++))
        return
    fi

    # Pattern 1: TODO/FIXME-like markers
    if echo "$java_files" | xargs grep -l -E '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub)' 2>/dev/null | head -3 | grep -q .; then
        echo -e "${YELLOW}WARNING: TODO/FIXME markers in changed files${NC}" >&2
        echo "$java_files" | xargs grep -n -E '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub)' 2>/dev/null | head -3 | sed 's/^/  /' >&2
        ((WARNINGS++))
        violations_found=1
    fi

    # Pattern 2: Mock/stub method names in src/
    if echo "$java_files" | grep -v '/test/' | xargs grep -l -E '(mock|stub|fake)[A-Z]' 2>/dev/null | head -3 | grep -q .; then
        echo -e "${RED}ERROR: mock/stub patterns in src/ (not allowed outside tests)${NC}" >&2
        echo "$java_files" | grep -v '/test/' | xargs grep -n -E '(mock|stub|fake)[A-Z]' 2>/dev/null | head -3 | sed 's/^/  /' >&2
        ((ERRORS++))
        violations_found=1
    fi

    # Pattern 3: Empty return stubs
    if echo "$java_files" | grep -v '/test/' | xargs grep -l 'return\s*"";\s*$' 2>/dev/null | head -3 | grep -q .; then
        echo -e "${YELLOW}WARNING: empty string returns (likely stubs)${NC}" >&2
        ((WARNINGS++))
        violations_found=1
    fi

    if [[ $violations_found -eq 0 ]]; then
        [[ $VERBOSE -eq 1 ]] && echo "  No HYPER_STANDARDS violations detected"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 5: Failed Test Results Analysis
# ═══════════════════════════════════════════════════════════════════════════
check_test_failures() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 5] Recent Test Failures${NC}"

    local test_report="${REPO_ROOT}/target/surefire-reports/TEST-*.xml"

    # Look for most recent failed test
    if ls $test_report 2>/dev/null | xargs grep -l failure 2>/dev/null | head -1 | grep -q .; then
        echo -e "${RED}ERROR: Failed tests detected${NC}" >&2
        echo "  Suggested fixes:" >&2

        # Try to extract test class and reason
        if grep -h '<failure' $test_report 2>/dev/null | head -3 | grep -q .; then
            grep -h '<failure' $test_report 2>/dev/null | head -1 | sed 's/.*<failure message="//' | sed 's/" type.*//' | sed 's/^/    /' >&2
        fi

        echo "  Run individual test: mvn test -Dtest=ClassName" >&2
        echo "  See: target/surefire-reports/ for details" >&2
        ((ERRORS++))
    elif ls $test_report 2>/dev/null | xargs grep -l error 2>/dev/null | head -1 | grep -q .; then
        echo -e "${YELLOW}WARNING: Test errors detected${NC}" >&2
        ((WARNINGS++))
    else
        [[ $VERBOSE -eq 1 ]] && echo "  No recent test failures"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 6: Build Cache Issues
# ═══════════════════════════════════════════════════════════════════════════
check_build_cache() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 6] Build Cache${NC}"

    local target_dir="${REPO_ROOT}/target"
    local corrupted=0

    if [[ -d "$target_dir" ]]; then
        # Check for incomplete builds (pom.xml newer than target)
        if [[ "${REPO_ROOT}/pom.xml" -nt "$target_dir" ]]; then
            echo -e "${YELLOW}WARNING: pom.xml changed since last build${NC}" >&2
            echo "  Suggested fix: mvn clean compile" >&2
            ((WARNINGS++))
            corrupted=1
        fi

        # Check for stale .class files
        STALE_CLASSES=$(find "$target_dir" -name "*.class" -mtime +7 2>/dev/null | wc -l)
        if [[ $STALE_CLASSES -gt 100 ]]; then
            echo -e "${YELLOW}WARNING: Stale build cache (${STALE_CLASSES} classes >7 days old)${NC}" >&2
            echo "  Suggested fix: mvn clean" >&2
            ((WARNINGS++))
            corrupted=1
        fi

        if [[ $corrupted -eq 0 ]]; then
            [[ $VERBOSE -eq 1 ]] && echo "  Build cache appears healthy"
            ((CHECKS_PASSED++))
        fi
    else
        [[ $VERBOSE -eq 1 ]] && echo "  No target/ directory (fresh clone)"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 7: Docker/Container Availability (skip if QUICK_MODE=1)
# ═══════════════════════════════════════════════════════════════════════════
check_docker() {
    if [[ $QUICK_MODE -eq 1 ]]; then
        [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 7] Docker (SKIPPED in quick mode)${NC}"
        return
    fi

    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 7] Docker/Containers${NC}"

    if ! command -v docker &>/dev/null; then
        [[ $VERBOSE -eq 1 ]] && echo "  Docker not found (optional for unit tests, required for integration tests)"
        return
    fi

    # Check if daemon is running
    if ! docker ps &>/dev/null; then
        echo -e "${YELLOW}WARNING: Docker daemon not responding${NC}" >&2
        echo "  Docker required for: integration tests, A2A/MCP tests" >&2
        echo "  Start Docker or use: bash scripts/dx.sh (unit tests only)" >&2
        ((WARNINGS++))
    else
        [[ $VERBOSE -eq 1 ]] && echo "  Docker daemon running"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# CHECK 8: Module Detection and pom.xml Sanity
# ═══════════════════════════════════════════════════════════════════════════
check_modules() {
    [[ $VERBOSE -eq 1 ]] && echo -e "${BLUE}[CHECK 8] Module Configuration${NC}"

    # Check that modules listed in parent pom exist
    local expected_modules=(
        yawl-utilities yawl-elements yawl-authentication yawl-engine
        yawl-stateless yawl-resourcing yawl-scheduling
        yawl-security yawl-integration yawl-monitoring yawl-webapps
        yawl-control-panel
    )

    local missing=0
    for mod in "${expected_modules[@]}"; do
        if [[ ! -d "${REPO_ROOT}/${mod}" ]]; then
            [[ $VERBOSE -eq 1 ]] && echo "  WARNING: Module $mod not found (may be optional)"
            ((missing++))
        fi
    done

    if [[ $missing -gt 3 ]]; then
        echo -e "${YELLOW}WARNING: Multiple modules missing (${missing} not found)${NC}" >&2
        ((WARNINGS++))
    else
        [[ $VERBOSE -eq 1 ]] && echo "  Module structure appears correct"
        ((CHECKS_PASSED++))
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# MAIN DIAGNOSTIC RUN
# ═══════════════════════════════════════════════════════════════════════════
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  YAWL Development Diagnostic v1.0                    ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

check_java
check_maven
check_dependencies
check_standards
check_test_failures
check_build_cache
check_docker
check_modules

# ═══════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════════════════
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  DIAGNOSTIC SUMMARY                                   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"

printf "Checks passed: %d | Warnings: %d | Errors: %d\n" "$CHECKS_PASSED" "$WARNINGS" "$ERRORS"
echo ""

if [[ $ERRORS -eq 0 && $WARNINGS -eq 0 ]]; then
    echo -e "${GREEN}✓ All checks passed. Ready to build.${NC}"
    echo ""
    echo "Next steps:"
    echo "  bash scripts/dx.sh           # Compile + test changed modules"
    echo "  bash scripts/dx.sh all       # Full build (pre-commit gate)"
    exit 0
elif [[ $ERRORS -eq 0 ]]; then
    echo -e "${YELLOW}⚠ Warnings detected. Builds may fail.${NC}"
    echo ""
    echo "To proceed anyway:"
    echo "  bash scripts/dx.sh -pl <module>"
    echo ""
    exit 1
else
    echo -e "${RED}✗ Critical errors detected. Build will fail.${NC}"
    echo ""
    echo "Fix the errors above before running:"
    echo "  bash scripts/dx.sh"
    echo ""
    exit 2
fi
