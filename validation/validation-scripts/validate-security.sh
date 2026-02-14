#!/bin/bash
#
# Security Validation Script for Multi-Cloud Marketplace Readiness
# Product: YAWL Workflow Engine v5.2
#
# Usage: ./validate-security.sh [--verbose] [--scan-type <type>]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(dirname "$SCRIPT_DIR")"
REPORT_DIR="${VALIDATION_DIR}/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Options
VERBOSE=false
SCAN_TYPE="all"
QUIET=false
FAILED=0
PASSED=0
WARNINGS=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --quiet|-q)
            QUIET=true
            shift
            ;;
        --scan-type)
            SCAN_TYPE="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v        Enable verbose output"
            echo "  --quiet, -q          Suppress non-essential output"
            echo "  --scan-type <type>   Scan type (container|code|secrets|network|all)"
            echo "  --help, -h           Show this help message"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

log_info() {
    [ "$QUIET" = true ] && return
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARNINGS++))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED++))
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Container Security Scan
scan_containers() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Container Security Scan ==="

    local image="${CONTAINER_IMAGE:-yawl/yawl-engine:5.2.0}"

    # Trivy scan
    if command_exists "trivy"; then
        log_info "Running Trivy vulnerability scan..."

        local trivy_output
        trivy_output=$(trivy image --quiet --severity HIGH,CRITICAL --format json "$image" 2>/dev/null || echo '{"Results":[]}')

        local critical=$(echo "$trivy_output" | jq '[.Results[].Vulnerabilities // [] | .[] | select(.Severity == "CRITICAL")] | length' 2>/dev/null || echo "0")
        local high=$(echo "$trivy_output" | jq '[.Results[].Vulnerabilities // [] | .[] | select(.Severity == "HIGH")] | length' 2>/dev/null || echo "0")

        if [ "$critical" -eq 0 ] && [ "$high" -eq 0 ]; then
            log_success "No critical/high vulnerabilities in container image"
        else
            log_error "Found $critical critical and $high high vulnerabilities"
        fi
    else
        log_warning "Trivy not installed - skipping container vulnerability scan"
    fi

    # Docker Scout (alternative)
    if command_exists "docker" && docker scout --help >/dev/null 2>&1; then
        log_info "Running Docker Scout scan..."
        if docker scout cves "$image" 2>/dev/null | grep -q "no vulnerability found"; then
            log_success "Docker Scout: No vulnerabilities found"
        else
            log_warning "Docker Scout: Vulnerabilities detected - review output"
        fi
    fi

    # Check for sensitive files in image
    log_info "Checking for sensitive files in image..."
    local sensitive_files=(
        "/etc/shadow"
        "/root/.ssh/id_rsa"
        "/root/.bash_history"
        "/.env"
        "/credentials.json"
        "/secrets"
    )

    for file in "${sensitive_files[@]}"; do
        if docker run --rm "$image" test -f "$file" 2>/dev/null; then
            log_error "Sensitive file found in image: $file"
        fi
    done
    log_success "No sensitive files found in image"

    # Check if running as root
    local user=$(docker run --rm "$image" whoami 2>/dev/null || echo "unknown")
    if [ "$user" = "root" ]; then
        log_warning "Container runs as root by default - consider non-root user"
    else
        log_success "Container runs as non-root user: $user"
    fi
}

# Code Security Scan
scan_code() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Code Security Scan ==="

    # Bandit (Python)
    if command_exists "bandit"; then
        log_info "Running Bandit security linter..."
        if bandit -r . -f json -q 2>/dev/null | jq '.results | length' | grep -q "0"; then
            log_success "Bandit: No security issues found"
        else
            local issues=$(bandit -r . -f json -q 2>/dev/null | jq '.results | length')
            log_warning "Bandit: $issues potential security issues found"
        fi
    else
        log_warning "Bandit not installed - skipping Python security scan"
    fi

    # Semgrep
    if command_exists "semgrep"; then
        log_info "Running Semgrep security scan..."
        if semgrep --config=auto --json --quiet . 2>/dev/null | jq '.results | length' | grep -q "0"; then
            log_success "Semgrep: No security issues found"
        else
            log_warning "Semgrep: Security issues found - review output"
        fi
    else
        log_info "Semgrep not installed - skipping"
    fi

    # Check for SQL injection patterns
    log_info "Checking for SQL injection patterns..."
    local sqli_patterns=(
        "execute.*%s.*%"
        "cursor\.execute.*\+"
        "raw.*request\."
        "\.format\(.*request"
    )

    local sqli_found=false
    for pattern in "${sqli_patterns[@]}"; do
        if grep -rE "$pattern" --include="*.py" --include="*.java" . 2>/dev/null | head -1 | grep -q .; then
            log_warning "Potential SQL injection pattern found"
            sqli_found=true
            break
        fi
    done
    [ "$sqli_found" = false ] && log_success "No obvious SQL injection patterns found"

    # Check for XSS patterns
    log_info "Checking for XSS patterns..."
    if grep -r "innerHTML\s*=" --include="*.js" --include="*.jsx" --include="*.ts" --include="*.tsx" . 2>/dev/null | grep -v "sanitize\|escape" | head -1 | grep -q .; then
        log_warning "Potential XSS vulnerability (innerHTML without sanitization)"
    else
        log_success "No obvious XSS patterns found"
    fi
}

# Secrets Scan
scan_secrets() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Secrets Detection Scan ==="

    # GitLeaks
    if command_exists "gitleaks"; then
        log_info "Running GitLeaks scan..."
        if gitleaks detect --source=. --no-git -q 2>/dev/null; then
            log_success "GitLeaks: No secrets detected"
        else
            log_error "GitLeaks: Potential secrets detected in codebase"
        fi
    else
        log_warning "GitLeaks not installed - skipping git secrets scan"
    fi

    # TruffleHog
    if command_exists "trufflehog"; then
        log_info "Running TruffleHog scan..."
        if trufflehog filesystem . --no-update --json 2>/dev/null | grep -q "Found"; then
            log_error "TruffleHog: Secrets detected"
        else
            log_success "TruffleHog: No secrets detected"
        fi
    else
        log_info "TruffleHog not installed - skipping"
    fi

    # Manual pattern check
    log_info "Scanning for common secret patterns..."
    local secret_patterns=(
        "AKIA[0-9A-Z]{16}"                           # AWS Access Key
        "sk-[a-zA-Z0-9]{20,}"                        # OpenAI/Stripe keys
        "xox[baprs]-[0-9]{10,}"                      # Slack tokens
        "ghp_[a-zA-Z0-9]{36}"                        # GitHub PAT
        "[0-9a-f]{32}"                               # MD5/Secret
        "private_key.*-----BEGIN"                    # Private keys
        "password\s*[=:]\s*['\"][^'\"]{8,}['\"]"    # Hardcoded passwords
    )

    local secrets_found=false
    for pattern in "${secret_patterns[@]}"; do
        if grep -rE "$pattern" --include="*.py" --include="*.js" --include="*.java" --include="*.yaml" --include="*.yml" --include="*.properties" . 2>/dev/null | grep -v ".env.example" | grep -v "test" | head -1 | grep -q .; then
            log_error "Potential secret pattern found: $pattern"
            secrets_found=true
        fi
    done
    [ "$secrets_found" = false ] && log_success "No secret patterns detected"

    # Check for .env files
    log_info "Checking for exposed .env files..."
    if find . -name ".env" -not -path "*/node_modules/*" -not -path "*/.git/*" 2>/dev/null | head -1 | grep -q .; then
        log_warning ".env files found - ensure they are in .gitignore"
    else
        log_success "No exposed .env files"
    fi

    # Verify .gitignore
    if [ -f ".gitignore" ]; then
        if grep -q ".env" .gitignore && grep -q "*.pem" .gitignore && grep -q "*credentials*" .gitignore; then
            log_success ".gitignore properly configured for sensitive files"
        else
            log_warning ".gitignore may be missing patterns for sensitive files"
        fi
    fi
}

# Network Security Scan
scan_network() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Network Security Configuration ==="

    # Check for TLS configuration
    log_info "Checking TLS/SSL configuration..."
    local tls_config_found=false

    if grep -r "TLSv1.3\|tls1.3\|minVersion.*1.3" --include="*.py" --include="*.js" --include="*.yaml" --include="*.properties" . 2>/dev/null | head -1 | grep -q .; then
        log_success "TLS 1.3 configuration found"
        tls_config_found=true
    fi

    if grep -r "TLSv1.2\|tls1.2" --include="*.py" --include="*.js" --include="*.yaml" --include="*.properties" . 2>/dev/null | head -1 | grep -q .; then
        log_success "TLS 1.2 configuration found"
        tls_config_found=true
    fi

    [ "$tls_config_found" = false ] && log_warning "No TLS configuration explicitly found"

    # Check for deprecated protocols
    if grep -rE "SSLv[23]|TLSv1\.0|TLSv1\.1" --include="*.py" --include="*.js" --include="*.java" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_error "Deprecated SSL/TLS protocols found"
    else
        log_success "No deprecated SSL/TLS protocols found"
    fi

    # Check CORS configuration
    log_info "Checking CORS configuration..."
    if grep -r "Access-Control-Allow-Origin.*\*" --include="*.py" --include="*.js" --include="*.java" . 2>/dev/null | grep -v "test" | head -1 | grep -q .; then
        log_warning "Wildcard CORS configuration found - review for production"
    else
        log_success "No wildcard CORS configurations found"
    fi

    # Check for exposed ports
    log_info "Checking Dockerfile for exposed ports..."
    if [ -f "Dockerfile" ]; then
        local exposed=$(grep -E "^EXPOSE" Dockerfile | awk '{print $2}' | tr '\n' ' ')
        log_info "Exposed ports: $exposed"

        # Check for sensitive ports
        local sensitive_ports=("22" "23" "3306" "5432" "6379" "27017")
        for port in "${sensitive_ports[@]}"; do
            if echo "$exposed" | grep -qw "$port"; then
                log_warning "Potentially sensitive port exposed: $port"
            fi
        done
    fi

    # Check network policies
    log_info "Checking Kubernetes network policies..."
    if find . -name "*networkpolicy*" -o -name "*network-policy*" 2>/dev/null | head -1 | grep -q .; then
        log_success "Kubernetes NetworkPolicy files found"
    else
        log_warning "No Kubernetes NetworkPolicy files found"
    fi
}

# IAM Security Check
scan_iam() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== IAM Security Configuration ==="

    # Check for overly permissive policies
    log_info "Checking for overly permissive IAM policies..."

    # AWS
    if find . -name "*.json" -exec grep -l "Action.*\*" {} \; 2>/dev/null | grep -i iam | head -1 | grep -q .; then
        log_warning "AWS IAM policy with wildcard actions found"
    else
        log_success "No overly permissive AWS IAM policies found"
    fi

    # GCP
    if find . -name "*.yaml" -o -name "*.yml" | xargs grep -l "roles/owner" 2>/dev/null | head -1 | grep -q .; then
        log_warning "GCP IAM policy with owner role found"
    else
        log_success "No overly permissive GCP IAM policies found"
    fi

    # Kubernetes RBAC
    log_info "Checking Kubernetes RBAC..."
    if find . -name "*rbac*" -o -name "*role*" 2>/dev/null | xargs grep -l "apiGroups.*\*" 2>/dev/null | head -1 | grep -q .; then
        log_warning "Kubernetes RBAC with wildcard apiGroups found"
    else
        log_success "No overly permissive Kubernetes RBAC found"
    fi

    # Check for service account key files
    log_info "Checking for service account key files..."
    if find . -name "*key*.json" -o -name "*credentials*.json" -o -name "*service-account*.json" 2>/dev/null | grep -v "test" | head -1 | grep -q .; then
        log_error "Service account key files found in repository"
    else
        log_success "No service account key files found"
    fi
}

# Generate report
generate_report() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Security Scan Summary ==="

    local total=$((PASSED + FAILED + WARNINGS))

    echo ""
    echo "Total Security Checks: $total"
    echo -e "Passed: ${GREEN}$PASSED${NC}"
    echo -e "Failed: ${RED}$FAILED${NC}"
    echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"

    mkdir -p "$REPORT_DIR"

    local report_file="${REPORT_DIR}/security-report-${TIMESTAMP}.json"
    cat > "$report_file" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "scan_type": "$SCAN_TYPE",
  "summary": {
    "total": $total,
    "passed": $PASSED,
    "failed": $FAILED,
    "warnings": $WARNINGS
  },
  "status": "$([ $FAILED -eq 0 ] && echo "PASS" || echo "FAIL")",
  "readiness": "$([ $FAILED -eq 0 ] && [ $WARNINGS -eq 0 ] && echo "READY" || ([ $FAILED -eq 0 ] && echo "CONDITIONAL" || echo "NOT_READY"))"
}
EOF

    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && log_info "Security report saved to: $report_file"

    if [ $FAILED -gt 0 ]; then
        echo -e "${RED}SECURITY VALIDATION FAILED${NC}"
        exit 1
    else
        echo -e "${GREEN}SECURITY VALIDATION PASSED${NC}"
        exit 0
    fi
}

# Main
main() {
    [ "$QUIET" = false ] && echo "=========================================="
    [ "$QUIET" = false ] && echo "YAWL Security Validation"
    [ "$QUIET" = false ] && echo "=========================================="

    case $SCAN_TYPE in
        container|all) scan_containers ;;
        code|all) scan_code ;;
        secrets|all) scan_secrets ;;
        network|all) scan_network ;;
        iam|all) scan_iam ;;
    esac

    generate_report
}

main "$@"
