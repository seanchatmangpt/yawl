#!/bin/bash
#
# Master Validation Script for Multi-Cloud Marketplace Readiness
# Product: YAWL Workflow Engine v5.2
#
# Usage: ./validate-all.sh [--cloud <provider>] [--verbose] [--report <format>]
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(dirname "$SCRIPT_DIR")"
REPORT_DIR="${VALIDATION_DIR}/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default options
VERBOSE=false
REPORT_FORMAT="json"
CLOUD_PROVIDER="all"
FAILED_CHECKS=0
PASSED_CHECKS=0
WARNINGS=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --cloud)
            CLOUD_PROVIDER="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --report)
            REPORT_FORMAT="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --cloud <provider>   Validate specific cloud (gcp|aws|azure|oracle|ibm|teradata|all)"
            echo "  --verbose, -v        Enable verbose output"
            echo "  --report <format>    Report format (json|html|markdown)"
            echo "  --help, -h           Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED_CHECKS++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARNINGS++))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED_CHECKS++))
}

log_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Validate prerequisites
validate_prerequisites() {
    log_section "Validating Prerequisites"

    # Check required tools
    local tools=("docker" "jq" "curl" "git")
    for tool in "${tools[@]}"; do
        if command_exists "$tool"; then
            log_success "Tool available: $tool"
        else
            log_error "Missing required tool: $tool"
        fi
    done

    # Check cloud CLIs based on provider
    case $CLOUD_PROVIDER in
        gcp|all)
            if command_exists "gcloud"; then
                log_success "GCP CLI available"
            else
                log_warning "GCP CLI (gcloud) not installed - GCP checks will be skipped"
            fi
            ;;
    esac

    case $CLOUD_PROVIDER in
        aws|all)
            if command_exists "aws"; then
                log_success "AWS CLI available"
            else
                log_warning "AWS CLI not installed - AWS checks will be skipped"
            fi
            ;;
    esac

    case $CLOUD_PROVIDER in
        azure|all)
            if command_exists "az"; then
                log_success "Azure CLI available"
            else
                log_warning "Azure CLI not installed - Azure checks will be skipped"
            fi
            ;;
    esac

    case $CLOUD_PROVIDER in
        oracle|all)
            if command_exists "oci"; then
                log_success "OCI CLI available"
            else
                log_warning "OCI CLI not installed - OCI checks will be skipped"
            fi
            ;;
    esac

    case $CLOUD_PROVIDER in
        ibm|all)
            if command_exists "ibmcloud"; then
                log_success "IBM Cloud CLI available"
            else
                log_warning "IBM Cloud CLI not installed - IBM checks will be skipped"
            fi
            ;;
    esac
}

# Validate container images
validate_containers() {
    log_section "Validating Container Images"

    local image_name="${CONTAINER_IMAGE:-yawl/yawl-engine:5.2.0}"

    # Check if image exists locally
    if docker image inspect "$image_name" >/dev/null 2>&1; then
        log_success "Container image exists: $image_name"

        # Get image size
        local size=$(docker image inspect "$image_name" --format='{{.Size}}')
        local size_mb=$((size / 1024 / 1024))

        if [ $size_mb -lt 500 ]; then
            log_success "Image size acceptable: ${size_mb}MB (< 500MB)"
        else
            log_warning "Image size large: ${size_mb}MB (consider optimizing)"
        fi

        # Check for vulnerabilities using docker scout or trivy
        if command_exists "trivy"; then
            log_info "Running vulnerability scan..."
            if trivy image --quiet --severity HIGH,CRITICAL "$image_name" | grep -q "Total: 0"; then
                log_success "No critical/high vulnerabilities found"
            else
                log_warning "Vulnerabilities found - review trivy output"
            fi
        else
            log_warning "Trivy not installed - skipping vulnerability scan"
        fi
    else
        log_warning "Container image not found locally: $image_name"
    fi
}

# Validate security configurations
validate_security() {
    log_section "Validating Security Configuration"

    # Run detailed security validation
    if [ -f "${SCRIPT_DIR}/validate-security.sh" ]; then
        log_info "Running detailed security validation..."
        "${SCRIPT_DIR}/validate-security.sh" --quiet || log_warning "Security validation had warnings"
    else
        log_warning "Security validation script not found"
    fi

    # Check for hardcoded secrets
    log_info "Checking for hardcoded secrets..."
    local secret_patterns=(
        "password\s*=\s*['\"][^'\"]+['\"]"
        "api_key\s*=\s*['\"][^'\"]+['\"]"
        "secret\s*=\s*['\"][^'\"]+['\"]"
        "aws_access_key_id\s*=\s*['\"][^'\"]+['\"]"
    )

    local found_secrets=false
    for pattern in "${secret_patterns[@]}"; do
        if grep -rE "$pattern" --include="*.py" --include="*.js" --include="*.yaml" --include="*.yml" . 2>/dev/null | grep -v ".env.example" | head -1 | grep -q .; then
            log_warning "Potential hardcoded secret found matching: $pattern"
            found_secrets=true
        fi
    done

    if [ "$found_secrets" = false ]; then
        log_success "No obvious hardcoded secrets found"
    fi

    # Check TLS configuration
    log_info "Checking TLS configuration..."
    if grep -r "TLSv1.3\|tls1.3\|minVersion.*1.3" . --include="*.py" --include="*.js" --include="*.yaml" 2>/dev/null | head -1 | grep -q .; then
        log_success "TLS 1.3 configuration found"
    else
        log_warning "TLS 1.3 not explicitly configured"
    fi
}

# Validate performance benchmarks
validate_performance() {
    log_section "Validating Performance Configuration"

    if [ -f "${SCRIPT_DIR}/validate-performance.sh" ]; then
        log_info "Running performance validation..."
        "${SCRIPT_DIR}/validate-performance.sh" --quiet || log_warning "Performance validation had warnings"
    else
        log_warning "Performance validation script not found"
    fi

    # Check for performance test files
    if [ -d "tests/performance" ] || [ -d "test/performance" ]; then
        log_success "Performance test directory found"
    else
        log_warning "No performance test directory found"
    fi
}

# Validate compliance documentation
validate_compliance() {
    log_section "Validating Compliance Documentation"

    if [ -f "${SCRIPT_DIR}/validate-compliance.sh" ]; then
        log_info "Running compliance validation..."
        "${SCRIPT_DIR}/validate-compliance.sh" --quiet || log_warning "Compliance validation had warnings"
    else
        log_warning "Compliance validation script not found"
    fi

    # Check for required compliance documents
    local compliance_docs=(
        "privacy-policy"
        "terms-of-service"
        "sla"
        "security-whitepaper"
    )

    for doc in "${compliance_docs[@]}"; do
        if find . -type f -name "*${doc}*" 2>/dev/null | head -1 | grep -q .; then
            log_success "Compliance document found: $doc"
        else
            log_warning "Missing compliance document: $doc"
        fi
    done
}

# Cloud-specific validation
validate_gcp() {
    log_section "Validating GCP Configuration"

    if ! command_exists "gcloud"; then
        log_warning "GCP CLI not available - skipping"
        return
    fi

    # Check authentication
    if gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -1 | grep -q .; then
        log_success "GCP authenticated"
    else
        log_warning "GCP not authenticated"
    fi

    # Check Artifact Registry
    log_info "Checking Artifact Registry..."
    # Add specific GCP checks here

    # Check project configuration
    local project=$(gcloud config get-value project 2>/dev/null || echo "")
    if [ -n "$project" ]; then
        log_success "GCP project configured: $project"
    else
        log_warning "No GCP project configured"
    fi
}

validate_aws() {
    log_section "Validating AWS Configuration"

    if ! command_exists "aws"; then
        log_warning "AWS CLI not available - skipping"
        return
    fi

    # Check authentication
    if aws sts get-caller-identity >/dev/null 2>&1; then
        log_success "AWS authenticated"
        local account=$(aws sts get-caller-identity --query "Account" --output text 2>/dev/null)
        log_info "AWS Account: $account"
    else
        log_warning "AWS not authenticated"
    fi

    # Check ECR
    log_info "Checking ECR repositories..."
    # Add specific AWS checks here
}

validate_azure() {
    log_section "Validating Azure Configuration"

    if ! command_exists "az"; then
        log_warning "Azure CLI not available - skipping"
        return
    fi

    # Check authentication
    if az account show >/dev/null 2>&1; then
        log_success "Azure authenticated"
        local subscription=$(az account show --query "name" -o tsv 2>/dev/null)
        log_info "Azure Subscription: $subscription"
    else
        log_warning "Azure not authenticated"
    fi
}

validate_oracle() {
    log_section "Validating Oracle Cloud Configuration"

    if ! command_exists "oci"; then
        log_warning "OCI CLI not available - skipping"
        return
    fi

    # Check authentication
    if oci os ns get >/dev/null 2>&1; then
        log_success "OCI authenticated"
    else
        log_warning "OCI not authenticated"
    fi
}

validate_ibm() {
    log_section "Validating IBM Cloud Configuration"

    if ! command_exists "ibmcloud"; then
        log_warning "IBM Cloud CLI not available - skipping"
        return
    fi

    # Check authentication
    if ibmcloud target >/dev/null 2>&1; then
        log_success "IBM Cloud authenticated"
    else
        log_warning "IBM Cloud not authenticated"
    fi
}

validate_teradata() {
    log_section "Validating Teradata Configuration"

    # Teradata typically uses JDBC/ODBC, check for connectivity tools
    if command_exists "python3"; then
        if python3 -c "import teradatasql" 2>/dev/null; then
            log_success "Teradata Python driver available"
        else
            log_warning "Teradata Python driver not installed"
        fi
    fi
}

# Generate report
generate_report() {
    log_section "Validation Summary"

    local total=$((PASSED_CHECKS + FAILED_CHECKS + WARNINGS))
    local pass_rate=0
    if [ $total -gt 0 ]; then
        pass_rate=$((PASSED_CHECKS * 100 / total))
    fi

    echo ""
    echo "Total Checks: $total"
    echo -e "Passed: ${GREEN}$PASSED_CHECKS${NC}"
    echo -e "Failed: ${RED}$FAILED_CHECKS${NC}"
    echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"
    echo "Pass Rate: ${pass_rate}%"
    echo ""

    # Create report directory
    mkdir -p "$REPORT_DIR"

    # Generate JSON report
    if [ "$REPORT_FORMAT" = "json" ] || [ "$REPORT_FORMAT" = "all" ]; then
        local report_file="${REPORT_DIR}/validation-report-${TIMESTAMP}.json"
        cat > "$report_file" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "product": "YAWL Workflow Engine",
  "version": "5.2.0",
  "cloud_provider": "$CLOUD_PROVIDER",
  "summary": {
    "total": $total,
    "passed": $PASSED_CHECKS,
    "failed": $FAILED_CHECKS,
    "warnings": $WARNINGS,
    "pass_rate": $pass_rate
  },
  "status": "$([ $FAILED_CHECKS -eq 0 ] && echo "PASS" || echo "FAIL")"
}
EOF
        log_info "Report saved to: $report_file"
    fi

    # Determine exit status
    if [ $FAILED_CHECKS -gt 0 ]; then
        echo -e "${RED}VALIDATION FAILED${NC}"
        exit 1
    else
        echo -e "${GREEN}VALIDATION PASSED${NC}"
        exit 0
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "YAWL Multi-Cloud Marketplace Validation"
    echo "=========================================="
    echo "Provider: $CLOUD_PROVIDER"
    echo "Timestamp: $(date)"
    echo "=========================================="

    # Run all validations
    validate_prerequisites
    validate_containers
    validate_security
    validate_performance
    validate_compliance

    # Cloud-specific validations
    case $CLOUD_PROVIDER in
        gcp|all) validate_gcp ;;
        aws|all) validate_aws ;;
        azure|all) validate_azure ;;
        oracle|all) validate_oracle ;;
        ibm|all) validate_ibm ;;
        teradata|all) validate_teradata ;;
    esac

    # Generate report
    generate_report
}

# Run main
main "$@"
