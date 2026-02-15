#!/bin/bash
#
# Compliance Validation Script for Multi-Cloud Marketplace Readiness
# Product: YAWL Workflow Engine v5.2
#
# Usage: ./validate-compliance.sh [--standard <standard>] [--verbose]
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
QUIET=false
STANDARD="all"
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
        --standard|-s)
            STANDARD="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v           Enable verbose output"
            echo "  --quiet, -q             Suppress non-essential output"
            echo "  --standard, -s <name>   Check specific standard (soc2|iso27001|gdpr|hipaa|pci|fedramp|all)"
            echo "  --help, -h              Show this help message"
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

log_section() {
    echo ""
    echo -e "${BLUE}=== $1 ===${NC}"
}

# Check for compliance documentation
check_documentation() {
    local doc_name="$1"
    local patterns="$2"

    log_info "Checking for $doc_name..."

    local found=false
    for pattern in $patterns; do
        if find . -type f -iname "*$pattern*" 2>/dev/null | head -1 | grep -q .; then
            found=true
            break
        fi
    done

    if [ "$found" = true ]; then
        log_success "$doc_name found"
        return 0
    else
        log_warning "$doc_name not found"
        return 1
    fi
}

# SOC 2 Type II Compliance
check_soc2() {
    log_section "SOC 2 Type II Compliance"

    # Security (Common Criteria)
    log_info "Checking SOC 2 Security criteria..."

    # CC6.1 - Logical and Physical Access
    if grep -r "rbac\|role.*based\|access.*control" --include="*.yaml" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CC6.1: Access control mechanisms documented"
    else
        log_warning "CC6.1: Access control documentation needed"
    fi

    # CC6.6 - Security of Transmission
    if grep -r "tls\|ssl\|https\|encryption.*transit" --include="*.yaml" --include="*.tf" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CC6.6: Transmission security documented"
    else
        log_warning "CC6.6: Transmission security documentation needed"
    fi

    # CC6.7 - Protection of Data
    if grep -r "encryption.*rest\|aes-256\|kms\|key.*management" --include="*.yaml" --include="*.tf" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CC6.7: Data protection mechanisms documented"
    else
        log_warning "CC6.7: Data protection documentation needed"
    fi

    # CC7.2 - System Monitoring
    if grep -r "monitoring\|alerting\|cloudwatch\|stackdriver\|prometheus" --include="*.yaml" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CC7.2: System monitoring documented"
    else
        log_warning "CC7.2: System monitoring documentation needed"
    fi

    # CC7.4 - Incident Management
    check_documentation "Incident Response Plan" "incident*response incident*management security*incident"
    check_documentation "Business Continuity Plan" "business*continuity bcp disaster*recovery"

    # Availability
    log_info "Checking SOC 2 Availability criteria..."

    # A1.2 - Backup and Recovery
    if grep -r "backup\|recovery\|restore" --include="*.yaml" --include="*.tf" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "A1.2: Backup procedures documented"
    else
        log_warning "A1.2: Backup documentation needed"
    fi

    # Check for SLA documentation
    check_documentation "SLA Document" "sla service*level*agreement"

    # Confidentiality
    log_info "Checking SOC 2 Confidentiality criteria..."

    # C1.1 - Data Classification
    check_documentation "Data Classification Policy" "data*classification information*classification"

    # Processing Integrity
    log_info "Checking SOC 2 Processing Integrity criteria..."

    # PI1.1 - Data Processing Accuracy
    if grep -r "validation\|checksum\|integrity" --include="*.py" --include="*.java" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "PI1.1: Data validation mechanisms found"
    else
        log_warning "PI1.1: Data validation documentation needed"
    fi
}

# ISO 27001 Compliance
check_iso27001() {
    log_section "ISO 27001 Compliance"

    # Annex A.5 - Information Security Policies
    check_documentation "Information Security Policy" "information*security*policy security*policy"
    check_documentation "Acceptable Use Policy" "acceptable*use"

    # Annex A.6 - Organization of Information Security
    log_info "Checking organizational controls..."
    check_documentation "Security Roles and Responsibilities" "security*role responsibility*matrix"

    # Annex A.8 - Asset Management
    log_info "Checking asset management..."
    check_documentation "Asset Inventory" "asset*inventory hardware*inventory"
    check_documentation "Data Classification Scheme" "data*classification"

    # Annex A.9 - Access Control
    log_info "Checking access controls..."
    check_documentation "Access Control Policy" "access*control*policy"
    check_documentation "User Access Management Procedure" "user*access access*management"

    # Annex A.10 - Cryptography
    log_info "Checking cryptographic controls..."
    if grep -r "encryption\|cryptographic\|cipher" --include="*.yaml" --include="*.md" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "A.10: Cryptographic controls documented"
    else
        log_warning "A.10: Cryptographic policy needed"
    fi

    # Annex A.12 - Operations Security
    log_info "Checking operational security..."
    check_documentation "Change Management Procedure" "change*management"
    check_documentation "Vulnerability Management Procedure" "vulnerability*management patch*management"

    # Annex A.13 - Communications Security
    log_info "Checking communications security..."
    if grep -r "network*security\|firewall\|segmentation" --include="*.yaml" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "A.13: Network security controls documented"
    else
        log_warning "A.13: Network security documentation needed"
    fi

    # Annex A.14 - System Acquisition and Development
    log_info "Checking secure development..."
    check_documentation "Secure Development Policy" "secure*development sdlc security*development"

    # Annex A.16 - Information Security Incident Management
    check_documentation "Incident Management Procedure" "incident*management incident*response"

    # Annex A.17 - Business Continuity
    check_documentation "Business Continuity Plan" "business*continuity"
    check_documentation "Disaster Recovery Plan" "disaster*recovery drp"

    # Annex A.18 - Compliance
    check_documentation "Regulatory Compliance Matrix" "compliance*matrix regulatory*compliance"
}

# GDPR Compliance
check_gdpr() {
    log_section "GDPR Compliance"

    # Article 5 - Principles
    log_info "Checking GDPR principles..."
    check_documentation "Data Processing Principles" "data*processing principle"

    # Article 6 - Lawful Basis
    check_documentation "Lawful Basis Documentation" "lawful*basis consent*management"

    # Article 7 - Consent
    log_info "Checking consent management..."
    if grep -r "consent\|opt.*in\|cookie.*policy" --include="*.html" --include="*.js" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Art. 7: Consent mechanisms found"
    else
        log_warning "Art. 7: Consent management needed"
    fi

    # Article 13/14 - Transparency
    check_documentation "Privacy Notice" "privacy*notice privacy*policy"
    check_documentation "Cookie Policy" "cookie*policy"

    # Article 15-22 - Data Subject Rights
    check_documentation "Data Subject Rights Procedures" "data*subject*rights subject*access*request sar"

    # Article 25 - Privacy by Design
    log_info "Checking privacy by design..."
    if grep -r "privacy.*design\|data.*minimization\|pseudonymi" --include="*.md" --include="*.py" --include="*.java" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Art. 25: Privacy by design considerations found"
    else
        log_warning "Art. 25: Privacy by design documentation needed"
    fi

    # Article 28 - Data Processing Agreements
    check_documentation "Data Processing Agreement Template" "data*processing*agreement dpa subprocessor"

    # Article 30 - Records of Processing
    check_documentation "Records of Processing Activities" "ropa processing*activities record*processing"

    # Article 32 - Security of Processing
    log_info "Checking security measures..."
    if grep -r "encryption\|access.*control\|pseudonymization" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Art. 32: Security measures documented"
    else
        log_warning "Art. 32: Security measures documentation needed"
    fi

    # Article 33/34 - Breach Notification
    check_documentation "Breach Notification Procedure" "breach*notification data*breach"

    # Data Retention
    check_documentation "Data Retention Policy" "retention*policy data*retention"
}

# HIPAA Compliance (if applicable)
check_hipaa() {
    log_section "HIPAA Compliance (Healthcare)"

    # Privacy Rule
    log_info "Checking HIPAA Privacy Rule..."
    check_documentation "Notice of Privacy Practices" "notice*privacy*practices npp"
    check_documentation "PHI Handling Procedures" "phi*handling protected*health"

    # Security Rule - Administrative Safeguards
    log_info "Checking Administrative Safeguards..."
    check_documentation "Security Management Process" "security*management risk*analysis"
    check_documentation "Workforce Security Procedures" "workforce*security"
    check_documentation "Information Access Management" "information*access"

    # Security Rule - Physical Safeguards
    log_info "Checking Physical Safeguards..."
    check_documentation "Facility Access Controls" "facility*access physical*security"

    # Security Rule - Technical Safeguards
    log_info "Checking Technical Safeguards..."

    # Access Control (164.312(a))
    if grep -r "access.*control\|authentication\|authorization" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "164.312(a): Access control documented"
    else
        log_warning "164.312(a): Access control documentation needed"
    fi

    # Audit Controls (164.312(b))
    if grep -r "audit\|logging\|monitoring" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "164.312(b): Audit controls documented"
    else
        log_warning "164.312(b): Audit controls documentation needed"
    fi

    # Integrity (164.312(c))
    if grep -r "integrity\|checksum\|hash" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "164.312(c): Integrity controls documented"
    else
        log_warning "164.312(c): Integrity documentation needed"
    fi

    # Transmission Security (164.312(e))
    if grep -r "tls\|ssl\|encryption.*transit" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "164.312(e): Transmission security documented"
    else
        log_warning "164.312(e): Transmission security documentation needed"
    fi

    # Breach Notification Rule
    check_documentation "Breach Notification Procedure" "breach*notification hipaa*breach"

    # BAA Template
    check_documentation "Business Associate Agreement Template" "business*associate baa"
}

# PCI-DSS Compliance (if applicable)
check_pci() {
    log_section "PCI-DSS Compliance (Payment Card)"

    # Requirement 1 - Firewall
    log_info "Checking network security..."
    if grep -r "firewall\|network*segmentation\|dmz" --include="*.yaml" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 1: Network security controls documented"
    else
        log_warning "Req 1: Network security documentation needed"
    fi

    # Requirement 2 - Default Passwords
    log_info "Checking default password handling..."
    if grep -r "default.*password\|change.*password\|initial.*credential" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 2: Default password procedures documented"
    else
        log_warning "Req 2: Default password documentation needed"
    fi

    # Requirement 3 - Stored Cardholder Data
    log_info "Checking cardholder data protection..."
    if grep -r "cardholder.*data\|pan.*mask\|tokenization" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 3: Cardholder data protection documented"
    else
        log_warning "Req 3: Cardholder data protection documentation needed"
    fi

    # Requirement 4 - Encryption
    if grep -r "tls.*1.2\|tls.*1.3\|encryption.*transit" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 4: Encryption in transit documented"
    else
        log_warning "Req 4: Encryption documentation needed"
    fi

    # Requirement 6 - Secure Systems
    log_info "Checking secure development..."
    check_documentation "Secure Coding Standards" "secure*coding coding*standard"
    check_documentation "Vulnerability Management Process" "vulnerability*management"

    # Requirement 7 - Need to Know
    check_documentation "Access Control Policy" "access*control need*to*know"

    # Requirement 8 - Authentication
    log_info "Checking authentication requirements..."
    if grep -r "mfa\|multi.*factor\|two.*factor\|2fa" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 8: MFA documented"
    else
        log_warning "Req 8: MFA documentation needed"
    fi

    # Requirement 10 - Logging
    if grep -r "logging\|audit.*log\|log.*retention" --include="*.yaml" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Req 10: Logging documented"
    else
        log_warning "Req 10: Logging documentation needed"
    fi

    # Requirement 11 - Testing
    check_documentation "Penetration Testing Procedures" "penetration*test pentest"
    check_documentation "Vulnerability Scan Procedures" "vulnerability*scan"

    # Requirement 12 - Policy
    check_documentation "Information Security Policy" "security*policy"
    check_documentation "Incident Response Plan" "incident*response"
}

# FedRAMP Compliance (if applicable)
check_fedramp() {
    log_section "FedRAMP Compliance (US Government)"

    # AC - Access Control
    log_info "Checking Access Control (AC)..."
    check_documentation "Access Control Policy" "access*control"
    if grep -r "ac-2\|account.*management\|role.*based" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "AC-2: Account management documented"
    fi

    # AU - Audit and Accountability
    log_info "Checking Audit Controls (AU)..."
    if grep -r "audit.*log\|au-2\|au-3\|au-6" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "AU: Audit controls documented"
    else
        log_warning "AU: Audit controls documentation needed"
    fi

    # CA - Assessment and Authorization
    check_documentation "Security Assessment Plan" "security*assessment sap"
    check_documentation "Plan of Action and Milestones" "poam poa*m"

    # CM - Configuration Management
    log_info "Checking Configuration Management (CM)..."
    if grep -r "cm-2\|baseline\|configuration.*management" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CM: Configuration management documented"
    else
        log_warning "CM: Configuration management documentation needed"
    fi

    # CP - Contingency Planning
    check_documentation "Contingency Plan" "contingency*plan cp"
    check_documentation "Disaster Recovery Plan" "disaster*recovery"

    # IA - Identification and Authentication
    log_info "Checking Identification and Authentication (IA)..."
    if grep -r "ia-2\|ia-5\|authentication\|multi.*factor" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "IA: I&A controls documented"
    else
        log_warning "IA: I&A documentation needed"
    fi

    # IR - Incident Response
    check_documentation "Incident Response Plan" "incident*response ir"
    check_documentation "Incident Response Test Results" "ir*test incident*test"

    # MA - Maintenance
    check_documentation "System Maintenance Procedures" "maintenance*procedure"

    # MP - Media Protection
    check_documentation "Media Protection Policy" "media*protection"

    # PE - Physical and Environmental
    check_documentation "Physical Security Policy" "physical*security"

    # PL - Planning
    check_documentation "System Security Plan" "system*security*plan ssp"

    # PS - Personnel Security
    check_documentation "Personnel Security Policy" "personnel*security"

    # RA - Risk Assessment
    check_documentation "Risk Assessment" "risk*assessment"
    check_documentation "Security Categorization" "security*categorization fips*199"

    # SA - System and Services Acquisition
    check_documentation "Supply Chain Risk Management" "supply*chain"

    # SC - System and Communications Protection
    log_info "Checking System Communications Protection (SC)..."
    if grep -r "sc-7\|sc-8\|boundary\|encryption" --include="*.md" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "SC: Communications protection documented"
    else
        log_warning "SC: Communications protection documentation needed"
    fi

    # SI - System and Information Integrity
    log_info "Checking System Integrity (SI)..."
    if grep -r "si-2\|si-3\|flaw.*remediation\|malicious.*code" --include="*.md" . 2>/dev/null | head -1 | grep -q .; then
        log_success "SI: System integrity documented"
    else
        log_warning "SI: System integrity documentation needed"
    fi
}

# Generate compliance report
generate_report() {
    log_section "Compliance Validation Summary"

    local total=$((PASSED + FAILED + WARNINGS))
    local compliance_score=0

    if [ $total -gt 0 ]; then
        compliance_score=$((PASSED * 100 / total))
    fi

    echo ""
    echo "Total Compliance Checks: $total"
    echo -e "Passed: ${GREEN}$PASSED${NC}"
    echo -e "Failed: ${RED}$FAILED${NC}"
    echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"
    echo "Compliance Score: ${compliance_score}%"

    mkdir -p "$REPORT_DIR"

    local report_file="${REPORT_DIR}/compliance-report-${TIMESTAMP}.json"
    cat > "$report_file" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "product": "YAWL Workflow Engine",
  "version": "5.2.0",
  "standard": "$STANDARD",
  "summary": {
    "total": $total,
    "passed": $PASSED,
    "failed": $FAILED,
    "warnings": $WARNINGS,
    "compliance_score": $compliance_score
  },
  "status": "$([ $FAILED -eq 0 ] && echo "COMPLIANT" || echo "NON-COMPLIANT")",
  "recommendation": "$([ $compliance_score -ge 80 ] && echo "Ready for certification" || ([ $compliance_score -ge 60 ] && echo "Address gaps before certification" || echo "Significant compliance gaps"))"
}
EOF

    [ "$QUIET" = false ] && log_info "Compliance report saved to: $report_file"

    if [ $FAILED -gt 0 ]; then
        echo -e "${RED}COMPLIANCE VALIDATION FAILED${NC}"
        exit 1
    else
        echo -e "${GREEN}COMPLIANCE VALIDATION PASSED${NC}"
        exit 0
    fi
}

# Main
main() {
    [ "$QUIET" = false ] && echo "=========================================="
    [ "$QUIET" = false ] && echo "YAWL Compliance Validation"
    [ "$QUIET" = false ] && echo "Standard: $STANDARD"
    [ "$QUIET" = false ] && echo "=========================================="

    case $STANDARD in
        soc2|all) check_soc2 ;;
        iso27001|all) check_iso27001 ;;
        gdpr|all) check_gdpr ;;
        hipaa|all) check_hipaa ;;
        pci|all) check_pci ;;
        fedramp|all) check_fedramp ;;
        *)
            log_error "Unknown standard: $STANDARD"
            exit 1
            ;;
    esac

    generate_report
}

main "$@"
