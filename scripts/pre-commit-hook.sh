#!/bin/bash
# YAWL v5.2 Pre-Commit Quality Gate Hook
#
# This is the canonical, version-controlled pre-commit hook.
# Install it with: bash scripts/install-hooks.sh --install-full
#
# Gates (in order, fast-failing):
#   1. HYPER_STANDARDS scan (TODO/FIXME/mock/stub patterns)
#   2. Maven compile
#   3. SpotBugs
#   4. Checkstyle
#   5. Unit tests
#
# Exit codes:
#   0 = all checks passed, commit proceeds
#   1 = one or more checks failed, commit blocked

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
FAILED=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

banner() {
    echo ""
    echo -e "${BLUE}╔═══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  YAWL Pre-Commit Quality Gate                     ║${NC}"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════╝${NC}"
    echo ""
}

pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; FAILED=1; }
step() { echo ""; echo -e "${YELLOW}--- $1 ---${NC}"; }

# Only run on staged Java files
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep '\.java$' || true)

if [ -z "$STAGED_JAVA" ]; then
    echo "No Java source files staged. Skipping Java quality checks."
    exit 0
fi

JAVA_FILE_COUNT=$(echo "$STAGED_JAVA" | wc -l | tr -d ' ')
banner
echo "Staged Java files: $JAVA_FILE_COUNT"

# =========================================================================
# Gate 1: HYPER_STANDARDS Scan
# =========================================================================
step "HYPER_STANDARDS Scan"
HYPER_VIOLATIONS=0

while IFS= read -r file; do
    abs="$REPO_ROOT/$file"
    [ -f "$abs" ] || continue
    # Skip legacy orderfulfillment package
    [[ "$file" =~ /orderfulfillment/ ]] && continue

    if grep -nE '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|placeholder|not\s+implemented)' "$abs" 2>/dev/null; then
        fail "DEFERRED WORK MARKER in $file"
        HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
    fi
    if grep -nE '(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]' "$abs" 2>/dev/null | grep -v '/test/'; then
        fail "MOCK/STUB PATTERN in $file"
        HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
    fi
    if grep -nE '(class|interface)\s+(Mock|Stub|Fake|Demo)[A-Za-z]*\s+(implements|extends|\{)' "$abs" 2>/dev/null; then
        fail "MOCK CLASS NAME in $file"
        HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
    fi
    if grep -nE 'public\s+void\s+\w+\([^)]*\)\s*\{\s*\}' "$abs" 2>/dev/null; then
        fail "EMPTY NO-OP METHOD in $file"
        HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
    fi
done <<< "$STAGED_JAVA"

if [ "$HYPER_VIOLATIONS" -eq 0 ]; then
    pass "No HYPER_STANDARDS violations"
else
    fail "$HYPER_VIOLATIONS violation(s) - see CLAUDE.md for fixes"
fi

# =========================================================================
# Gate 2: Compile
# =========================================================================
step "Maven Compile"
cd "$REPO_ROOT"
if mvn clean compile --batch-mode --no-transfer-progress --quiet -T 1C 2>&1 | tail -15; then
    pass "Compilation succeeded"
else
    fail "Compilation FAILED"
fi

# =========================================================================
# Gate 3: SpotBugs
# =========================================================================
step "SpotBugs"
if mvn spotbugs:check --batch-mode --no-transfer-progress --quiet 2>&1 | tail -20; then
    pass "SpotBugs: no HIGH/MEDIUM bugs"
else
    fail "SpotBugs: bugs detected (run 'mvn spotbugs:gui' for details)"
fi

# =========================================================================
# Gate 4: Checkstyle
# =========================================================================
step "Checkstyle"
if mvn checkstyle:check --batch-mode --no-transfer-progress --quiet 2>&1 | tail -20; then
    pass "Checkstyle: no violations"
else
    fail "Checkstyle: violations detected"
fi

# =========================================================================
# Gate 5: Unit Tests
# =========================================================================
step "Unit Tests"
if mvn test --batch-mode --no-transfer-progress --quiet \
    -Dmaven.test.failure.ignore=false 2>&1 | tail -20; then
    pass "All unit tests passed"
else
    fail "Unit test(s) FAILED"
fi

# =========================================================================
# Result
# =========================================================================
echo ""
echo "============================================================"
if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}All pre-commit checks PASSED. Commit proceeds.${NC}"
    exit 0
else
    echo -e "${RED}Pre-commit quality gate FAILED. Commit blocked.${NC}"
    echo ""
    echo "Diagnose:"
    echo "  mvn clean compile              # compilation"
    echo "  mvn spotbugs:check             # SpotBugs"
    echo "  mvn spotbugs:gui               # SpotBugs interactive GUI"
    echo "  mvn checkstyle:check           # Checkstyle"
    echo "  mvn test                       # unit tests"
    echo "  mvn clean package -P analysis  # full suite"
    echo ""
    echo "EMERGENCY bypass (never on main/master):"
    echo "  git commit --no-verify -m \"EMERGENCY: ...\""
    exit 1
fi
