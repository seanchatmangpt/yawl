#!/bin/bash
# Security Scanning Script for YAWL Workflow Engine
# Comprehensive security analysis including SAST, dependency, and container scanning

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="${REPORT_DIR:-$PROJECT_ROOT/reports/security}"
SEVERITY_LEVEL="${SEVERITY_LEVEL:-HIGH,CRITICAL}"
FAIL_ON_VULNERABILITY="${FAIL_ON_VULNERABILITY:-true}"
IMAGE_NAME="${IMAGE_NAME:-yawl:latest}"

# Create report directory
mkdir -p "$REPORT_DIR"

echo "=============================================="
echo "YAWL Security Scanner"
echo "=============================================="
echo ""
echo "Project Root: $PROJECT_ROOT"
echo "Report Directory: $REPORT_DIR"
echo "Severity Level: $SEVERITY_LEVEL"
echo "Fail on Vulnerability: $FAIL_ON_VULNERABILITY"
echo ""

# Track results
TOTAL_VULNERABILITIES=0
CRITICAL_COUNT=0
HIGH_COUNT=0
MEDIUM_COUNT=0
LOW_COUNT=0

# Check for required tools
check_tool() {
    local tool="$1"
    local package="$2"

    if ! command -v "$tool" &> /dev/null; then
        echo -e "${YELLOW}$tool not found. Installing...${NC}"

        case "$(uname -s)" in
            Linux*)
                if command -v apt-get &> /dev/null; then
                    sudo apt-get update && sudo apt-get install -y "$package"
                elif command -v yum &> /dev/null; then
                    sudo yum install -y "$package"
                elif command -v brew &> /dev/null; then
                    brew install "$package"
                fi
                ;;
            Darwin*)
                if command -v brew &> /dev/null; then
                    brew install "$package"
                fi
                ;;
        esac
    fi
}

# ============================================
# 1. Dependency Vulnerability Scan
# ============================================
scan_dependencies() {
    echo ""
    echo -e "${BLUE}=== Dependency Vulnerability Scan ===${NC}"
    echo ""

    local report_file="$REPORT_DIR/dependency-check-report.json"

    # Check for OWASP Dependency Check
    if command -v dependency-check &> /dev/null; then
        echo "Running OWASP Dependency Check..."

        dependency-check \
            --project "YAWL Workflow Engine" \
            --scan "$PROJECT_ROOT" \
            --out "$REPORT_DIR" \
            --format JSON \
            --format HTML \
            --enableExperimental \
            --failOnCVSS 7 \
            || true

        if [ -f "$report_file" ]; then
            VULN_COUNT=$(jq '[.dependencies[]?.vulnerabilities // []] | add | length' "$report_file" 2>/dev/null || echo "0")
            echo -e "Found ${YELLOW}$VULN_COUNT${NC} dependency vulnerabilities"
            ((TOTAL_VULNERABILITIES += VULN_COUNT))
        fi
    else
        echo -e "${YELLOW}OWASP Dependency Check not installed. Skipping...${NC}"
        echo "Install with: brew install dependency-check (macOS)"
    fi

    # Check Java dependencies with Maven/Gradle if available
    if [ -f "$PROJECT_ROOT/pom.xml" ]; then
        echo "Checking Maven dependencies..."

        if command -v mvn &> /dev/null; then
            mvn dependency:analyze -f "$PROJECT_ROOT/pom.xml" || true
        fi
    fi
}

# ============================================
# 2. Static Application Security Testing (SAST)
# ============================================
scan_sast() {
    echo ""
    echo -e "${BLUE}=== Static Application Security Testing (SAST) ===${NC}"
    echo ""

    # Check for SonarQube Scanner
    if command -v sonar-scanner &> /dev/null; then
        echo "Running SonarQube analysis..."

        sonar-scanner \
            -Dsonar.projectKey=yawl \
            -Dsonar.sources="$PROJECT_ROOT/src" \
            -Dsonar.java.binaries="$PROJECT_ROOT/classes" \
            -Dsonar.host.url="${SONAR_HOST:-http://localhost:9000}" \
            -Dsonar.login="${SONAR_TOKEN:-}" \
            || true
    else
        echo -e "${YELLOW}SonarQube scanner not found. Skipping...${NC}"
    fi

    # Run SpotBugs for Java
    if command -v spotbugs &> /dev/null; then
        echo "Running SpotBugs analysis..."

        spotbugs \
            -textui \
            -high \
            -output "$REPORT_DIR/spotbugs-report.xml" \
            -auxclasspath "$PROJECT_ROOT/build/3rdParty/lib/*" \
            "$PROJECT_ROOT/classes" \
            || true
    else
        echo -e "${YELLOW}SpotBugs not installed. Skipping...${NC}"
    fi

    # Run PMD
    if command -v pmd &> /dev/null; then
        echo "Running PMD analysis..."

        pmd pmd \
            -d "$PROJECT_ROOT/src" \
            -f xml \
            -r "$REPORT_DIR/pmd-report.xml" \
            -R rulesets/java/quickstart.xml \
            || true
    else
        echo -e "${YELLOW}PMD not installed. Skipping...${NC}"
    fi
}

# ============================================
# 3. Secret Scanning
# ============================================
scan_secrets() {
    echo ""
    echo -e "${BLUE}=== Secret Scanning ===${NC}"
    echo ""

    local report_file="$REPORT_DIR/secrets-report.json"

    # TruffleHog
    if command -v trufflehog &> /dev/null; then
        echo "Running TruffleHog scan..."

        trufflehog git file://"$PROJECT_ROOT" \
            --json \
            --only-verified \
            > "$report_file" \
            || true

        SECRET_COUNT=$(wc -l < "$report_file" 2>/dev/null || echo "0")
        if [ "$SECRET_COUNT" -gt 0 ]; then
            echo -e "${RED}Found $SECRET_COUNT potential secrets!${NC}"
            cat "$report_file"
            ((TOTAL_VULNERABILITIES += SECRET_COUNT))
        else
            echo -e "${GREEN}No secrets detected${NC}"
        fi
    else
        echo -e "${YELLOW}TruffleHog not installed. Skipping...${NC}"
        echo "Install with: pip install trufflehog3"
    fi

    # Gitleaks
    if command -v gitleaks &> /dev/null; then
        echo "Running Gitleaks scan..."

        gitleaks detect \
            --source="$PROJECT_ROOT" \
            --report-path="$REPORT_DIR/gitleaks-report.json" \
            --report-format=json \
            --exit-code=0 \
            || true

        if [ -f "$REPORT_DIR/gitleaks-report.json" ]; then
            LEAK_COUNT=$(jq 'length' "$REPORT_DIR/gitleaks-report.json" 2>/dev/null || echo "0")
            if [ "$LEAK_COUNT" -gt 0 ]; then
                echo -e "${RED}Gitleaks found $LEAK_COUNT potential leaks${NC}"
                ((TOTAL_VULNERABILITIES += LEAK_COUNT))
            fi
        fi
    else
        echo -e "${YELLOW}Gitleaks not installed. Skipping...${NC}"
        echo "Install with: brew install gitleaks (macOS)"
    fi
}

# ============================================
# 4. Container Security Scan
# ============================================
scan_container() {
    echo ""
    echo -e "${BLUE}=== Container Security Scan ===${NC}"
    echo ""

    if [ -z "${DOCKER_BUILD:-}" ] && [ ! -f "$PROJECT_ROOT/Dockerfile" ]; then
        echo -e "${YELLOW}No Dockerfile found. Skipping container scan...${NC}"
        return
    fi

    # Trivy
    if command -v trivy &> /dev/null; then
        echo "Running Trivy container scan..."

        # Build image if needed
        if ! docker image inspect "$IMAGE_NAME" &> /dev/null; then
            echo "Building Docker image..."
            docker build -t "$IMAGE_NAME" "$PROJECT_ROOT" || {
                echo -e "${YELLOW}Could not build image. Skipping container scan...${NC}"
                return
            }
        fi

        trivy image \
            --severity "$SEVERITY_LEVEL" \
            --format json \
            --output "$REPORT_DIR/trivy-image-report.json" \
            "$IMAGE_NAME" \
            || true

        trivy image \
            --severity "$SEVERITY_LEVEL" \
            --format table \
            "$IMAGE_NAME" \
            || true

        # Count vulnerabilities
        if [ -f "$REPORT_DIR/trivy-image-report.json" ]; then
            CRITICAL_COUNT=$(jq '[.Results[]?.Vulnerabilities // [] | .[] | select(.Severity == "CRITICAL")] | length' "$REPORT_DIR/trivy-image-report.json" 2>/dev/null || echo "0")
            HIGH_COUNT=$(jq '[.Results[]?.Vulnerabilities // [] | .[] | select(.Severity == "HIGH")] | length' "$REPORT_DIR/trivy-image-report.json" 2>/dev/null || echo "0")
            MEDIUM_COUNT=$(jq '[.Results[]?.Vulnerabilities // [] | .[] | select(.Severity == "MEDIUM")] | length' "$REPORT_DIR/trivy-image-report.json" 2>/dev/null || echo "0")
            LOW_COUNT=$(jq '[.Results[]?.Vulnerabilities // [] | .[] | select(.Severity == "LOW")] | length' "$REPORT_DIR/trivy-image-report.json" 2>/dev/null || echo "0")

            echo ""
            echo "Vulnerability Summary:"
            echo -e "  Critical: ${RED}$CRITICAL_COUNT${NC}"
            echo -e "  High:     ${RED}$HIGH_COUNT${NC}"
            echo -e "  Medium:   ${YELLOW}$MEDIUM_COUNT${NC}"
            echo -e "  Low:      ${GREEN}$LOW_COUNT${NC}"

            ((TOTAL_VULNERABILITIES += CRITICAL_COUNT))
            ((TOTAL_VULNERABILITIES += HIGH_COUNT))
        fi
    else
        echo -e "${YELLOW}Trivy not installed. Skipping container scan...${NC}"
        echo "Install with: brew install trivy (macOS)"
    fi

    # Grype (alternative)
    if command -v grype &> /dev/null; then
        echo "Running Grype container scan..."

        grype "$IMAGE_NAME" \
            --output json \
            --file "$REPORT_DIR/grype-report.json" \
            || true
    fi
}

# ============================================
# 5. Infrastructure as Code Scan
# ============================================
scan_iac() {
    echo ""
    echo -e "${BLUE}=== Infrastructure as Code Scan ===${NC}"
    echo ""

    # Checkov
    if command -v checkov &> /dev/null; then
        echo "Running Checkov scan..."

        checkov \
            --directory "$PROJECT_ROOT" \
            --framework terraform,kubernetes,dockerfile \
            --output json \
            --output-file "$REPORT_DIR/checkov-report.json" \
            --soft-fail \
            || true
    else
        echo -e "${YELLOW}Checkov not installed. Skipping...${NC}"
        echo "Install with: pip install checkov"
    fi

    # KICS
    if command -v kics &> /dev/null; then
        echo "Running KICS scan..."

        kics scan \
            --path "$PROJECT_ROOT" \
            --output-path "$REPORT_DIR" \
            --output-formats json \
            || true
    else
        echo -e "${YELLOW}KICS not installed. Skipping...${NC}"
    fi
}

# ============================================
# 6. License Compliance Scan
# ============================================
scan_licenses() {
    echo ""
    echo -e "${BLUE}=== License Compliance Scan ===${NC}"
    echo ""

    if command -v license-checker &> /dev/null; then
        echo "Checking license compliance..."

        license-checker \
            --start "$PROJECT_ROOT" \
            --json "$REPORT_DIR/licenses.json" \
            || true
    else
        echo -e "${YELLOW}License checker not installed. Skipping...${NC}"
    fi

    # Check for license headers in source files
    echo "Checking for license headers in source files..."

    FILES_WITHOUT_LICENSE=$(find "$PROJECT_ROOT/src" -name "*.java" \
        -exec grep -L "Copyright\|Licensed\|SPDX-License-Identifier" {} \; 2>/dev/null | wc -l | tr -d ' ')

    if [ "$FILES_WITHOUT_LICENSE" -gt 0 ]; then
        echo -e "${YELLOW}$FILES_WITHOUT_LICENSE files without license headers${NC}"
    else
        echo -e "${GREEN}All source files have license headers${NC}"
    fi
}

# ============================================
# 7. Generate Summary Report
# ============================================
generate_summary() {
    echo ""
    echo "=============================================="
    echo "Security Scan Summary"
    echo "=============================================="
    echo ""

    local summary_file="$REPORT_DIR/summary.txt"

    cat > "$summary_file" << EOF
YAWL Security Scan Summary
Generated: $(date)
========================================

Total Vulnerabilities Found: $TOTAL_VULNERABILITIES
  - Critical: $CRITICAL_COUNT
  - High: $HIGH_COUNT
  - Medium: $MEDIUM_COUNT
  - Low: $LOW_COUNT

Reports generated in: $REPORT_DIR
  - dependency-check-report.json
  - trivy-image-report.json
  - secrets-report.json
  - gitleaks-report.json
  - checkov-report.json
  - pmd-report.xml
  - spotbugs-report.xml

========================================
EOF

    echo -e "Total Vulnerabilities: ${RED}$TOTAL_VULNERABILITIES${NC}"
    echo ""
    echo "Reports saved to: $REPORT_DIR"
    echo ""

    if [ "$TOTAL_VULNERABILITIES" -gt 0 ]; then
        echo -e "${RED}SECURITY ISSUES DETECTED!${NC}"

        if [ "$FAIL_ON_VULNERABILITY" = "true" ]; then
            echo "Failing build due to security vulnerabilities..."
            exit 1
        fi
    else
        echo -e "${GREEN}No security issues detected!${NC}"
    fi
}

# ============================================
# Main Execution
# ============================================
main() {
    echo "Starting security scan at $(date)"
    echo ""

    scan_dependencies
    scan_sast
    scan_secrets
    scan_container
    scan_iac
    scan_licenses

    generate_summary

    echo ""
    echo "Security scan completed at $(date)"
}

# Run main function
main "$@"
