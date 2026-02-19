#!/usr/bin/env bash
# ==========================================================================
# docker-security-validate.sh — Docker Security Validation for YAWL
# ==========================================================================
# Validates all Docker configurations against security baseline.
# Checks: non-root users, TLS 1.3, health checks, security headers,
# minimal base images, no secrets, proper ownership.
#
# Usage:
#   bash scripts/docker-security-validate.sh              # Full validation
#   bash scripts/docker-security-validate.sh --quick      # Quick check (no scans)
#   bash scripts/docker-security-validate.sh --scan       # Run Trivy/Hadolint scans
#   bash scripts/docker-security-validate.sh --report     # Generate JSON report
#
# Output:
#   reports/docker-security-report.json - Detailed findings
#   reports/docker-security-summary.txt  - Human-readable summary
#
# Exit codes:
#   0 - All checks passed
#   1 - Critical security issues found
#   2 - Script error
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORTS_DIR="${REPO_ROOT}/reports"
DOCKER_DIR="${REPO_ROOT}/docker"

# Colors for output
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    RESET='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    BOLD=''
    RESET=''
fi

# Configuration
QUICK_MODE=false
RUN_SCANS=false
GENERATE_REPORT=false
FAIL_ON_CRITICAL=true
CRITICAL_COUNT=0
HIGH_COUNT=0
MEDIUM_COUNT=0
PASS_COUNT=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick|-q)    QUICK_MODE=true; shift ;;
        --scan|-s)     RUN_SCANS=true; shift ;;
        --report|-r)   GENERATE_REPORT=true; shift ;;
        --help|-h)     show_help; exit 0 ;;
        *)             shift ;;
    esac
done

show_help() {
    cat << 'EOF'
Docker Security Validation for YAWL v6.0.0

Usage:
  bash scripts/docker-security-validate.sh [options]

Options:
  --quick, -q    Quick validation (skip Trivy/Hadolint scans)
  --scan, -s     Run container security scans (Trivy, Hadolint)
  --report, -r   Generate JSON and text reports
  --help, -h     Show this help message

Security Checks:
  1. Non-root user configuration
  2. TLS 1.3 enforcement (Java containers)
  3. Health check configuration
  4. Minimal base images
  5. No hardcoded secrets
  6. Proper file ownership
  7. OCI labels present
  8. No unnecessary packages

Output:
  reports/docker-security-report.json
  reports/docker-security-summary.txt
EOF
}

# Logging functions
log_info()    { echo -e "${CYAN}[INFO]${RESET} $*"; }
log_pass()    { echo -e "${GREEN}[PASS]${RESET} $*"; ((PASS_COUNT++)); }
log_warn()    { echo -e "${YELLOW}[WARN]${RESET} $*"; ((MEDIUM_COUNT++)); }
log_fail()    { echo -e "${RED}[FAIL]${RESET} $*"; ((CRITICAL_COUNT++)); }
log_high()    { echo -e "${RED}[HIGH]${RESET} $*"; ((HIGH_COUNT++)); }
log_section() { echo -e "\n${BOLD}${BLUE}=== $* ===${RESET}"; }

# Find all Dockerfiles
find_dockerfiles() {
    find "${REPO_ROOT}" -name "Dockerfile*" -type f | grep -v node_modules | grep -v .git | sort
}

# Get list of all Dockerfiles
DOCKERFILES=$(find_dockerfiles)
DOCKERFILE_COUNT=$(echo "${DOCKERFILES}" | wc -l | tr -d ' ')

# ==========================================================================
# Security Check Functions
# ==========================================================================

# Check if Dockerfile has non-root USER directive
check_non_root_user() {
    local dockerfile="$1"
    local has_user=false
    local user_line=""

    if grep -qE "^\s*USER\s+\S+" "$dockerfile"; then
        has_user=true
        user_line=$(grep -E "^\s*USER\s+\S+" "$dockerfile" | tail -1)
        local username=$(echo "$user_line" | awk '{print $2}')

        # Check it's not root
        if [[ "$username" == "root" ]]; then
            log_fail "Non-root user check: $dockerfile uses USER root"
            return 1
        else
            log_pass "Non-root user check: $dockerfile uses USER $username"
            return 0
        fi
    else
        log_fail "Non-root user check: $dockerfile missing USER directive"
        return 1
    fi
}

# Check for TLS 1.3 configuration in Java containers
check_tls_config() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    # Only check Java-based Dockerfiles
    if grep -q "temurin\|eclipse-temurin\|openjdk" "$dockerfile"; then
        if grep -q "jdk.tls.disabledAlgorithms" "$dockerfile"; then
            # Check that TLS 1.0/1.1 are disabled
            if grep -qE "TLSv1[,}]|TLSv1\.1" "$dockerfile"; then
                log_pass "TLS 1.3 check: $basename has TLS 1.0/1.1 disabled"
                return 0
            else
                log_warn "TLS 1.3 check: $basename has tls config but may not disable TLS 1.0/1.1"
                return 2
            fi
        else
            log_fail "TLS 1.3 check: $basename missing TLS configuration"
            return 1
        fi
    else
        log_info "TLS 1.3 check: $basename (non-Java, skipped)"
        return 0
    fi
}

# Check for health check configuration
check_health_check() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    if grep -qE "^\s*HEALTHCHECK\s+" "$dockerfile"; then
        log_pass "Health check: $basename has HEALTHCHECK directive"
        return 0
    else
        log_warn "Health check: $basename missing HEALTHCHECK directive"
        return 2
    fi
}

# Check for hardcoded secrets
check_no_secrets() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")
    local issues=0

    # Check for common secret patterns
    local patterns=(
        "password\s*=\s*['\"][^'\"]+['\"]"
        "secret\s*=\s*['\"][^'\"]+['\"]"
        "api_key\s*=\s*['\"][^'\"]+['\"]"
        "token\s*=\s*['\"][^'\"]+['\"]"
        "AKIA[0-9A-Z]{16}"
    )

    for pattern in "${patterns[@]}"; do
        if grep -qiE -- "$pattern" "$dockerfile"; then
            log_high "Secret detection: $basename may contain hardcoded secret (pattern: $pattern)"
            ((issues++))
        fi
    done

    # Check for private key pattern separately (has leading dashes that break grep)
    if grep -q "BEGIN.*PRIVATE KEY" "$dockerfile"; then
        log_high "Secret detection: $basename may contain private key"
        ((issues++))
    fi

    if [[ $issues -eq 0 ]]; then
        log_pass "Secret detection: $basename no hardcoded secrets found"
        return 0
    else
        return 1
    fi
}

# Check for OCI labels
check_oci_labels() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")
    local required_labels=(
        "org.opencontainers.image.version"
        "org.opencontainers.image.vendor"
    )
    local missing=0

    for label in "${required_labels[@]}"; do
        if ! grep -q "$label" "$dockerfile"; then
            ((missing++))
        fi
    done

    if [[ $missing -eq 0 ]]; then
        log_pass "OCI labels: $basename has required labels"
        return 0
    else
        log_warn "OCI labels: $basename missing $missing required labels"
        return 2
    fi
}

# Check for COPY --chown or RUN chown
check_file_ownership() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    # Check if there are COPY commands with proper ownership
    local copy_count=$(grep -cE "^\s*COPY\s+" "$dockerfile" 2>/dev/null || true)
    copy_count=$(echo "$copy_count" | tr -d '[:space:]')
    copy_count=${copy_count:-0}
    local chown_count=$(grep -cE "(COPY\s+--chown|chown\s+-R|chown\s+:)" "$dockerfile" 2>/dev/null || true)
    chown_count=$(echo "$chown_count" | tr -d '[:space:]')
    chown_count=${chown_count:-0}

    if [[ "$copy_count" -gt 0 ]] && [[ "$chown_count" -gt 0 ]]; then
        log_pass "File ownership: $basename has ownership controls"
        return 0
    elif [[ "$copy_count" -eq 0 ]]; then
        log_info "File ownership: $basename has no COPY commands (skipped)"
        return 0
    else
        log_warn "File ownership: $basename may need ownership controls for COPY commands"
        return 2
    fi
}

# Check minimal base image (Alpine preferred)
check_minimal_base() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    # Check FROM directive
    local from_line=$(grep -m1 -E "^\s*FROM\s+" "$dockerfile")

    if echo "$from_line" | grep -qi "alpine"; then
        log_pass "Minimal base: $basename uses Alpine (minimal)"
        return 0
    elif echo "$from_line" | grep -qi "slim"; then
        log_pass "Minimal base: $basename uses slim image"
        return 0
    elif echo "$from_line" | grep -qiE "temurin.*alpine|eclipse-temurin.*alpine"; then
        log_pass "Minimal base: $basename uses Temurin Alpine"
        return 0
    else
        log_warn "Minimal base: $basename may use larger base image: $from_line"
        return 2
    fi
}

# Check for no sudo usage
check_no_sudo() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    if grep -qE "^\s*RUN\s+.*\bsudo\b" "$dockerfile"; then
        log_fail "Sudo check: $basename uses sudo (should not be needed with non-root user)"
        return 1
    else
        log_pass "Sudo check: $basename does not use sudo"
        return 0
    fi
}

# Check for multi-stage builds (production images)
check_multi_stage() {
    local dockerfile="$1"
    local basename=$(basename "$dockerfile")

    # Production images should use multi-stage builds
    if [[ "$basename" == *"production"* ]] || [[ "$dockerfile" == *"/production/"* ]]; then
        local from_count=$(grep -cE "^\s*FROM\s+" "$dockerfile" 2>/dev/null || echo "0")
        if [[ $from_count -ge 2 ]]; then
            log_pass "Multi-stage build: $basename uses multi-stage build"
            return 0
        else
            log_warn "Multi-stage build: $basename (production) should use multi-stage build"
            return 2
        fi
    else
        log_info "Multi-stage build: $basename (not production, skipped)"
        return 0
    fi
}

# ==========================================================================
# Scanner Functions
# ==========================================================================

run_hadolint_scan() {
    log_section "Running Hadolint (Dockerfile Linter)"

    if ! command -v hadolint &>/dev/null; then
        log_warn "Hadolint not installed. Install with: brew install hadolint"
        return 0
    fi

    local hadolint_config="${REPO_ROOT}/security/container-security/hadolint.yaml"
    local hadolint_opts=""

    if [[ -f "$hadolint_config" ]]; then
        hadolint_opts="--config ${hadolint_config}"
    fi

    for dockerfile in ${DOCKERFILES}; do
        local basename=$(basename "$dockerfile")
        log_info "Scanning: $basename"

        set +e
        local output
        output=$(hadolint $hadolint_opts "$dockerfile" 2>&1)
        local exit_code=$?
        set -e

        if [[ $exit_code -eq 0 ]] && [[ -z "$output" ]]; then
            log_pass "Hadolint: $basename - no issues"
        else
            echo "$output" | while read -r line; do
                if echo "$line" | grep -qi "error"; then
                    log_fail "Hadolint: $basename - $line"
                else
                    log_warn "Hadolint: $basename - $line"
                fi
            done
        fi
    done
}

run_trivy_scan() {
    log_section "Running Trivy (Container Scanner)"

    if ! command -v trivy &>/dev/null; then
        log_warn "Trivy not installed. Install with: brew install trivy"
        return 0
    fi

    local trivy_config="${REPO_ROOT}/security/container-security/trivy-scan.yaml"
    local trivy_opts=""

    if [[ -f "$trivy_config" ]]; then
        trivy_opts="--config ${trivy_config}"
    fi

    # Scan Dockerfiles as config files (misconfig detection)
    for dockerfile in ${DOCKERFILES}; do
        local basename=$(basename "$dockerfile")
        log_info "Scanning config: $basename"

        set +e
        local output
        output=$(trivy config $trivy_opts "$dockerfile" 2>&1)
        local exit_code=$?
        set -e

        if [[ $exit_code -eq 0 ]]; then
            log_pass "Trivy config: $basename - no issues"
        else
            echo "$output" | grep -E "(CRITICAL|HIGH|MEDIUM)" | while read -r line; do
                if echo "$line" | grep -qi "CRITICAL\|HIGH"; then
                    log_fail "Trivy: $basename - $line"
                else
                    log_warn "Trivy: $basename - $line"
                fi
            done
        fi
    done
}

# ==========================================================================
# Report Generation
# ==========================================================================

generate_json_report() {
    local report_file="${REPORTS_DIR}/docker-security-report.json"
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    mkdir -p "${REPORTS_DIR}"

    cat > "$report_file" << EOF
{
  "reportType": "docker-security-validation",
  "version": "1.0.0",
  "timestamp": "${timestamp}",
  "summary": {
    "dockerfilesScanned": ${DOCKERFILE_COUNT},
    "criticalIssues": ${CRITICAL_COUNT},
    "highIssues": ${HIGH_COUNT},
    "mediumIssues": ${MEDIUM_COUNT},
    "checksPassed": ${PASS_COUNT}
  },
  "dockerfiles": [
$(echo "${DOCKERFILES}" | while read -r df; do
    cat << DFEOF
    {
      "path": "${df#${REPO_ROOT}/}",
      "basename": "$(basename "$df")"
    },
DFEOF
done | sed '$ s/,$//')
  ],
  "baseline": {
    "nonRootUser": true,
    "tls13Enforcement": true,
    "healthChecks": true,
    "noSecrets": true,
    "ociLabels": true,
    "minimalBase": true
  }
}
EOF

    log_info "JSON report: $report_file"
}

generate_text_report() {
    local report_file="${REPORTS_DIR}/docker-security-summary.txt"
    local timestamp=$(date)

    mkdir -p "${REPORTS_DIR}"

    cat > "$report_file" << EOF
================================================================================
YAWL Docker Security Validation Report
================================================================================
Generated: ${timestamp}
Dockerfiles Scanned: ${DOCKERFILE_COUNT}

SUMMARY
--------------------------------------------------------------------------------
Checks Passed:      ${PASS_COUNT}
Critical Issues:    ${CRITICAL_COUNT}
High Issues:        ${HIGH_COUNT}
Medium Issues:      ${MEDIUM_COUNT}

SECURITY BASELINE
--------------------------------------------------------------------------------
[X] Non-root user execution
[X] TLS 1.3 enforcement (Java containers)
[X] Health check configuration
[X] No hardcoded secrets
[X] OCI image labels
[X] Minimal base images (Alpine/slim)
[X] Proper file ownership
[X] No sudo usage

DOCKERFILES VALIDATED
--------------------------------------------------------------------------------
$(echo "${DOCKERFILES}" | while read -r df; do
    echo "  - ${df#${REPO_ROOT}/}"
done)

RECOMMENDATIONS
--------------------------------------------------------------------------------
1. Ensure all production images use multi-stage builds
2. Keep base images updated with security patches
3. Run Trivy scans regularly in CI/CD
4. Review and update TLS configuration quarterly
5. Monitor container runtime security with Falco or similar

================================================================================
EOF

    log_info "Text report: $report_file"
}

# ==========================================================================
# Main Execution
# ==========================================================================

main() {
    log_section "YAWL Docker Security Validation"
    log_info "Dockerfiles to scan: ${DOCKERFILE_COUNT}"

    # Run security checks on each Dockerfile
    for dockerfile in ${DOCKERFILES}; do
        log_section "Validating: $(basename "$dockerfile")"

        check_non_root_user "$dockerfile" || true
        check_tls_config "$dockerfile" || true
        check_health_check "$dockerfile" || true
        check_no_secrets "$dockerfile" || true
        check_oci_labels "$dockerfile" || true
        check_file_ownership "$dockerfile" || true
        check_minimal_base "$dockerfile" || true
        check_no_sudo "$dockerfile" || true
        check_multi_stage "$dockerfile" || true
    done

    # Run external scanners if requested
    if [[ "$RUN_SCANS" == "true" ]] && [[ "$QUICK_MODE" == "false" ]]; then
        run_hadolint_scan
        run_trivy_scan
    fi

    # Generate reports
    if [[ "$GENERATE_REPORT" == "true" ]]; then
        log_section "Generating Reports"
        generate_json_report
        generate_text_report
    fi

    # Summary
    log_section "Validation Summary"
    echo -e "  Checks Passed:    ${GREEN}${PASS_COUNT}${RESET}"
    echo -e "  Critical Issues:  ${RED}${CRITICAL_COUNT}${RESET}"
    echo -e "  High Issues:      ${RED}${HIGH_COUNT}${RESET}"
    echo -e "  Medium Issues:    ${YELLOW}${MEDIUM_COUNT}${RESET}"
    echo ""

    # Exit with appropriate code
    if [[ $CRITICAL_COUNT -gt 0 ]] && [[ "$FAIL_ON_CRITICAL" == "true" ]]; then
        log_fail "Critical security issues found. Please remediate before deployment."
        exit 1
    elif [[ $HIGH_COUNT -gt 0 ]]; then
        log_warn "High severity issues found. Review and remediate when possible."
        exit 0
    else
        log_pass "Docker security validation passed."
        exit 0
    fi
}

main "$@"
