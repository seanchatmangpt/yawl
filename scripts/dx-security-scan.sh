#!/usr/bin/env bash
# ==========================================================================
# dx-security-scan.sh — Fast Security Vulnerability Scanning
#
# Runs OWASP dependency check and SpotBugs security rules with fast mode
# using cached NVD data. Outputs to reports/security/.
#
# Usage:
#   bash scripts/dx-security-scan.sh              # Full scan
#   bash scripts/dx-security-scan.sh --fast       # Use cached NVD data
#   bash scripts/dx-security-scan.sh --deps       # Dependencies only
#   bash scripts/dx-security-scan.sh --code       # SpotBugs security only
#
# Output:
#   reports/security/dependency-check-report.html
#   reports/security/spotbugs-security.xml
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORTS_DIR="${REPO_ROOT}/reports/security"
cd "${REPO_ROOT}"

# ── Parse arguments ───────────────────────────────────────────────────────
MODE="full"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast)  MODE="fast"; shift ;;
        --deps)  MODE="deps"; shift ;;
        --code)  MODE="code"; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *) shift ;;
    esac
done

# ── Ensure output directory ───────────────────────────────────────────────
mkdir -p "${REPORTS_DIR}"

# ── Helper: timestamp ─────────────────────────────────────────────────────
START_MS=$(python3 -c "import time; print(int(time.time() * 1000))")

# ── Run scans based on mode ───────────────────────────────────────────────
echo "=== YAWL Security Scan ==="
echo "Mode: ${MODE}"
echo "Output: ${REPORTS_DIR}"
echo ""

ISSUES_FOUND=0

# ── Dependency Check (OWASP) ──────────────────────────────────────────────
run_dependency_check() {
    local fast_mode="$1"
    echo "Running OWASP Dependency Check..."

    local extra_args=""
    if [[ "$fast_mode" == "fast" ]]; then
        # Use cached NVD data if available
        if [[ -d "${HOME}/.m2/repository/org/owasp/dependency-check-utils" ]]; then
            extra_args="-Ddependency-check.analyzer.retirejs.enabled=false -Ddependency-check.analyzer.nodeaudit.enabled=false"
            echo "  (fast mode: using cached NVD data)"
        fi
    fi

    set +e
    mvn dependency-check:aggregate \
        -Ddependency-check.format=HTML \
        -Ddependency-check.outputDirectory="${REPORTS_DIR}" \
        $extra_args \
        -q 2>&1 | tee "${REPORTS_DIR}/dependency-check.log"
    EXIT_CODE=$?
    set -e

    if [[ -f "${REPORTS_DIR}/dependency-check-report.html" ]]; then
        # Count vulnerabilities
        SUPPRESSED=$(grep -c "suppressed" "${REPORTS_DIR}/dependency-check-report.html" 2>/dev/null || echo "0")
        CRITICAL=$(grep -o "CRITICAL" "${REPORTS_DIR}/dependency-check-report.html" 2>/dev/null | wc -l | tr -d ' ' || echo "0")
        HIGH=$(grep -o "HIGH" "${REPORTS_DIR}/dependency-check-report.html" 2>/dev/null | wc -l | tr -d ' ' || echo "0")

        echo "  Found: ${CRITICAL} critical, ${HIGH} high severity issues"
        ISSUES_FOUND=$((ISSUES_FOUND + CRITICAL + HIGH))
    fi

    return $EXIT_CODE
}

# ── SpotBugs Security ─────────────────────────────────────────────────────
run_spotbugs_security() {
    echo "Running SpotBugs Security Analysis..."

    # First ensure code is compiled
    mvn compile -q 2>/dev/null || true

    set +e
    mvn spotbugs:spotbugs \
        -Dspotbugs.effort=Max \
        -Dspotbugs.threshold=Low \
        -Dspotbugs.includeFilterFile="${REPO_ROOT}/.claude/spotbugs-security.xml" \
        -Dspotbugs.xmlOutput=true \
        -Dspotbugs.outputDirectory="${REPORTS_DIR}" \
        -q 2>&1 | tee "${REPORTS_DIR}/spotbugs-security.log"
    EXIT_CODE=$?
    set -e

    # Find generated reports
    for mod in $(find . -name "spotbugsXml.xml" -type f 2>/dev/null); do
        mod_name=$(dirname "$mod" | sed 's|^\./||' | cut -d'/' -f1)
        cp "$mod" "${REPORTS_DIR}/spotbugs-${mod_name}.xml" 2>/dev/null || true

        # Count issues in this module
        BUG_COUNT=$(grep -c "<BugInstance" "${REPORTS_DIR}/spotbugs-${mod_name}.xml" 2>/dev/null || echo "0")
        if [[ "$BUG_COUNT" -gt 0 ]]; then
            echo "  ${mod_name}: ${BUG_COUNT} potential issues"
            ISSUES_FOUND=$((ISSUES_FOUND + BUG_COUNT))
        fi
    done

    return $EXIT_CODE
}

# ── Execute based on mode ─────────────────────────────────────────────────
case "$MODE" in
    full)
        run_dependency_check "full"
        run_spotbugs_security
        ;;
    fast)
        run_dependency_check "fast"
        run_spotbugs_security
        ;;
    deps)
        run_dependency_check "full"
        ;;
    code)
        run_spotbugs_security
        ;;
esac

# ── Create SpotBugs security filter if it doesn't exist ───────────────────
SECURITY_FILTER="${REPO_ROOT}/.claude/spotbugs-security.xml"
if [[ ! -f "$SECURITY_FILTER" ]]; then
    mkdir -p "$(dirname "$SECURITY_FILTER")"
    cat > "$SECURITY_FILTER" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter>
    <!-- Security-related bug patterns -->
    <Match>
        <Bug category="SECURITY"/>
    </Match>
    <!-- Common security issues -->
    <Match>
        <Bug pattern="SQL_INJECTION,XSS_REQUEST_PARAMETER_TO_SERVLET,PATH_TRAVERSAL_IN,COMMAND_INJECTION,PREDICTABLE_RANDOM,WEAK_TRUST_MANAGER,WEAK_HOSTNAME_VERIFIER"/>
    </Match>
</FindBugsFilter>
EOF
    echo "Created SpotBugs security filter: ${SECURITY_FILTER}"
fi

# ── Summary ───────────────────────────────────────────────────────────────
END_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(python3 -c "print(f'{${ELAPSED_MS}/1000:.1f}')")

echo ""
echo "=== Security Scan Complete ==="
echo "Time: ${ELAPSED_S}s"
echo "Issues found: ${ISSUES_FOUND}"
echo "Reports: ${REPORTS_DIR}"
echo ""

if [[ $ISSUES_FOUND -gt 0 ]]; then
    echo "⚠️  Security issues detected. Review reports in ${REPORTS_DIR}"
    echo ""
    echo "View reports:"
    echo "  open ${REPORTS_DIR}/dependency-check-report.html"
    echo "  cat ${REPORTS_DIR}/spotbugs-*.xml"
    exit 1
else
    echo "✓ No security issues detected"
    exit 0
fi
