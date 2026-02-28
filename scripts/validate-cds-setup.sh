#!/usr/bin/env bash
# ==========================================================================
# validate-cds-setup.sh — Validate CDS Setup and Configuration
#
# Checks that all CDS components are properly configured and ready.
#
# Usage:
#   bash scripts/validate-cds-setup.sh        # Full validation
#   bash scripts/validate-cds-setup.sh --fix  # Auto-fix issues
#
# Exit codes:
#   0 = All checks passed
#   1 = Some checks failed (warnings)
#   2 = Critical checks failed (must fix)
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Configuration
FIX_MODE=0
if [[ "${1:-}" == "--fix" ]]; then
    FIX_MODE=1
fi

# Colors
readonly RED='\033[91m'
readonly GREEN='\033[92m'
readonly YELLOW='\033[93m'
readonly BLUE='\033[94m'
readonly CYAN='\033[96m'
readonly RESET='\033[0m'

# Tracking
CHECKS_PASSED=0
CHECKS_FAILED=0
CHECKS_WARNINGS=0

# Helper functions
pass() {
    echo -e "${GREEN}✓${RESET} $1"
    ((CHECKS_PASSED++))
}

fail() {
    echo -e "${RED}✗${RESET} $1"
    ((CHECKS_FAILED++))
}

warn() {
    echo -e "${YELLOW}◇${RESET} $1"
    ((CHECKS_WARNINGS++))
}

info() {
    echo -e "${CYAN}ℹ${RESET} $1"
}

# ── Java Version Check ────────────────────────────────────────────────────
echo -e "${CYAN}Checking Java compatibility...${RESET}"

if java -version 2>&1 | grep -q "version \"25"; then
    java_version=$(java -version 2>&1 | grep 'version "' | cut -d'"' -f2)
    pass "Java version: $java_version (Java 25+ required)"
else
    fail "Java 25+ not found"
fi

# ── CDS Directory Structure ───────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking CDS directory structure...${RESET}"

CDS_DIR="${REPO_ROOT}/.yawl/cds"

if [[ -d "$CDS_DIR" ]]; then
    pass "CDS directory exists: $CDS_DIR"
else
    fail "CDS directory missing: $CDS_DIR"
    if [[ $FIX_MODE -eq 1 ]]; then
        mkdir -p "$CDS_DIR"
        pass "  (created)"
    fi
fi

# Check .gitkeep
if [[ -f "${CDS_DIR}/.gitkeep" ]]; then
    pass ".gitkeep present"
else
    warn ".gitkeep missing"
    if [[ $FIX_MODE -eq 1 ]]; then
        touch "${CDS_DIR}/.gitkeep"
        pass "  (created)"
    fi
fi

# Check .gitignore
if [[ -f "${CDS_DIR}/.gitignore" ]]; then
    if grep -q "*.jsa" "${CDS_DIR}/.gitignore"; then
        pass ".gitignore configured for *.jsa files"
    else
        fail ".gitignore missing *.jsa pattern"
    fi
else
    fail ".gitignore missing"
fi

# Check README.md
if [[ -f "${CDS_DIR}/README.md" ]]; then
    pass "README.md present"
else
    warn "README.md missing (optional)"
fi

# ── Scripts Check ─────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking CDS scripts...${RESET}"

required_scripts=(
    "scripts/generate-cds-archives.sh"
    "scripts/cds-helper.sh"
    "scripts/test-cds-performance.sh"
)

for script in "${required_scripts[@]}"; do
    script_path="${REPO_ROOT}/${script}"
    if [[ -f "$script_path" ]]; then
        if [[ -x "$script_path" ]]; then
            pass "$script (executable)"
        else
            warn "$script (not executable)"
            if [[ $FIX_MODE -eq 1 ]]; then
                chmod +x "$script_path"
                pass "  (made executable)"
            fi
        fi
    else
        fail "$script missing"
    fi
done

# ── Script Syntax Check ───────────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking script syntax...${RESET}"

for script in "${required_scripts[@]}"; do
    script_path="${REPO_ROOT}/${script}"
    if [[ -f "$script_path" ]]; then
        if bash -n "$script_path" 2>/dev/null; then
            pass "$script (syntax valid)"
        else
            fail "$script (syntax error)"
        fi
    fi
done

# ── dx.sh Integration ─────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking dx.sh integration...${RESET}"

dx_file="${REPO_ROOT}/scripts/dx.sh"

if [[ ! -f "$dx_file" ]]; then
    fail "scripts/dx.sh not found"
else
    if grep -q "cds-helper.sh" "$dx_file"; then
        pass "dx.sh calls cds-helper.sh"
    else
        fail "dx.sh missing cds-helper.sh integration"
    fi

    if grep -q "DX_CDS_GENERATE" "$dx_file"; then
        pass "dx.sh supports DX_CDS_GENERATE flag"
    else
        warn "dx.sh missing DX_CDS_GENERATE support"
    fi

    if grep -q "Post-compile CDS" "$dx_file"; then
        pass "dx.sh includes post-compile CDS regeneration"
    else
        warn "dx.sh missing post-compile CDS regeneration"
    fi
fi

# ── Maven Configuration ───────────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking Maven configuration...${RESET}"

mvn_config="${REPO_ROOT}/.mvn/jvm.config"

if [[ ! -f "$mvn_config" ]]; then
    fail ".mvn/jvm.config not found"
else
    if grep -q "Class Data Sharing" "$mvn_config"; then
        pass ".mvn/jvm.config mentions CDS"
    else
        warn ".mvn/jvm.config doesn't mention CDS"
    fi

    # Check for duplicate UseCompactObjectHeaders
    count=$(grep -c "UseCompactObjectHeaders" "$mvn_config" || echo 0)
    if [[ $count -le 1 ]]; then
        pass ".mvn/jvm.config has no duplicate flags"
    else
        warn ".mvn/jvm.config has duplicate UseCompactObjectHeaders ($count occurrences)"
    fi
fi

# ── Performance Directory ─────────────────────────────────────────────────
echo ""
echo -e "${CYAN}Checking performance tracking...${RESET}"

perf_dir="${REPO_ROOT}/.yawl/performance"

if [[ -d "$perf_dir" ]]; then
    pass "Performance directory exists: $perf_dir"
else
    warn "Performance directory missing: $perf_dir"
    if [[ $FIX_MODE -eq 1 ]]; then
        mkdir -p "$perf_dir"
        pass "  (created)"
    fi
fi

# ── Summary ───────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${RESET}"
echo -e "Validation Summary"
echo -e "${CYAN}═══════════════════════════════════════════════════════${RESET}"

total=$((CHECKS_PASSED + CHECKS_FAILED + CHECKS_WARNINGS))
echo "Checks passed: ${GREEN}${CHECKS_PASSED}${RESET}"
echo "Checks warned: ${YELLOW}${CHECKS_WARNINGS}${RESET}"
echo "Checks failed: ${RED}${CHECKS_FAILED}${RESET}"
echo "Total: ${total}"

echo ""

if [[ $CHECKS_FAILED -eq 0 ]]; then
    if [[ $CHECKS_WARNINGS -eq 0 ]]; then
        echo -e "${GREEN}✓ All CDS checks passed!${RESET}"
        echo ""
        echo "Next steps:"
        echo "  1. Build hot modules: bash scripts/dx.sh compile -pl yawl-engine,yawl-elements"
        echo "  2. Verify CDS generation: bash scripts/cds-helper.sh status"
        echo "  3. Test performance: bash scripts/test-cds-performance.sh"
        exit 0
    else
        echo -e "${YELLOW}◇ CDS setup complete with warnings${RESET}"
        echo ""
        echo "Warnings can be ignored, but consider running with --fix:"
        echo "  bash scripts/validate-cds-setup.sh --fix"
        exit 1
    fi
else
    echo -e "${RED}✗ CDS setup has critical issues${RESET}"
    echo ""
    if [[ $FIX_MODE -eq 0 ]]; then
        echo "Run with --fix to auto-correct issues:"
        echo "  bash scripts/validate-cds-setup.sh --fix"
    fi
    exit 2
fi
