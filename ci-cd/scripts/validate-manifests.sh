#!/bin/bash
# Validate Kubernetes Manifests for YAWL Workflow Engine
# Usage: ./validate-manifests.sh [directory]

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
MANIFEST_DIR="${1:-k8s}"
ERRORS=0
WARNINGS=0

echo "=============================================="
echo "YAWL Kubernetes Manifest Validator"
echo "=============================================="
echo ""
echo "Validating manifests in: $MANIFEST_DIR"
echo ""

# Check if directory exists
if [ ! -d "$MANIFEST_DIR" ]; then
    echo -e "${RED}ERROR: Directory $MANIFEST_DIR does not exist${NC}"
    exit 1
fi

# Find all YAML files
YAML_FILES=$(find "$MANIFEST_DIR" -name "*.yaml" -o -name "*.yml" 2>/dev/null | sort)

if [ -z "$YAML_FILES" ]; then
    echo -e "${YELLOW}WARNING: No YAML files found in $MANIFEST_DIR${NC}"
    exit 0
fi

echo "Found manifest files:"
echo "$YAML_FILES"
echo ""

# Check for required tools
check_tools() {
    echo "Checking required tools..."

    local missing_tools=()

    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        missing_tools+=("kubectl")
    fi

    # Check kubeval (optional but recommended)
    if ! command -v kubeval &> /dev/null; then
        echo -e "${YELLOW}kubeval not found - schema validation will be skipped${NC}"
    else
        HAS_KUBEVAL=true
    fi

    # Check kubeconform (alternative to kubeval)
    if ! command -v kubeconform &> /dev/null; then
        echo -e "${YELLOW}kubeconform not found - additional validation will be skipped${NC}"
    else
        HAS_KUBECONFORM=true
    fi

    # Check yamllint
    if ! command -v yamllint &> /dev/null; then
        echo -e "${YELLOW}yamllint not found - linting will be skipped${NC}"
    else
        HAS_YAMLLINT=true
    fi

    if [ ${#missing_tools[@]} -ne 0 ]; then
        echo -e "${RED}Missing required tools: ${missing_tools[*]}${NC}"
        exit 1
    fi

    echo ""
}

# Validate YAML syntax
validate_yaml_syntax() {
    local file="$1"

    echo "Validating YAML syntax: $file"

    # Check for tabs (should use spaces)
    if grep -P '\t' "$file" > /dev/null 2>&1; then
        echo -e "  ${YELLOW}WARNING: File contains tabs (should use spaces)${NC}"
        ((WARNINGS++))
    fi

    # Check for trailing whitespace
    if grep -E ' +$' "$file" > /dev/null 2>&1; then
        echo -e "  ${YELLOW}WARNING: File contains trailing whitespace${NC}"
        ((WARNINGS++))
    fi

    # Validate YAML structure
    if command -v python3 &> /dev/null; then
        if ! python3 -c "import yaml; yaml.safe_load(open('$file'))" 2>/dev/null; then
            echo -e "  ${RED}ERROR: Invalid YAML syntax${NC}"
            ((ERRORS++))
            return 1
        fi
    fi

    echo -e "  ${GREEN}OK${NC}"
    return 0
}

# Validate Kubernetes schema
validate_k8s_schema() {
    local file="$1"

    echo "Validating Kubernetes schema: $file"

    if [ "${HAS_KUBEVAL:-false}" = true ]; then
        if ! kubeval "$file" --strict 2>&1; then
            echo -e "  ${RED}ERROR: Schema validation failed${NC}"
            ((ERRORS++))
            return 1
        fi
    elif [ "${HAS_KUBECONFORM:-false}" = true ]; then
        if ! kubeconform "$file" 2>&1; then
            echo -e "  ${RED}ERROR: Schema validation failed${NC}"
            ((ERRORS++))
            return 1
        fi
    else
        # Use kubectl with --dry-run
        if ! kubectl apply --dry-run=client -f "$file" 2>&1; then
            echo -e "  ${RED}ERROR: Schema validation failed${NC}"
            ((ERRORS++))
            return 1
        fi
    fi

    echo -e "  ${GREEN}OK${NC}"
    return 0
}

# Validate best practices
validate_best_practices() {
    local file="$1"

    echo "Checking best practices: $file"

    # Check for resource limits
    if ! grep -q "resources:" "$file" 2>/dev/null; then
        echo -e "  ${YELLOW}WARNING: No resource limits defined${NC}"
        ((WARNINGS++))
    fi

    # Check for liveness/readiness probes
    if grep -q "kind: Deployment" "$file" 2>/dev/null; then
        if ! grep -q "livenessProbe:" "$file" 2>/dev/null; then
            echo -e "  ${YELLOW}WARNING: No liveness probe defined${NC}"
            ((WARNINGS++))
        fi
        if ! grep -q "readinessProbe:" "$file" 2>/dev/null; then
            echo -e "  ${YELLOW}WARNING: No readiness probe defined${NC}"
            ((WARNINGS++))
        fi
    fi

    # Check for security context
    if ! grep -q "securityContext:" "$file" 2>/dev/null; then
        if grep -q "kind: Deployment\|kind: Pod\|kind: StatefulSet\|kind: DaemonSet" "$file" 2>/dev/null; then
            echo -e "  ${YELLOW}WARNING: No security context defined${NC}"
            ((WARNINGS++))
        fi
    fi

    # Check for image tag (should not use :latest)
    if grep -q "image:.*:latest" "$file" 2>/dev/null; then
        echo -e "  ${YELLOW}WARNING: Using :latest image tag (not recommended)${NC}"
        ((WARNINGS++))
    fi

    # Check for hostNetwork
    if grep -q "hostNetwork: true" "$file" 2>/dev/null; then
        echo -e "  ${YELLOW}WARNING: hostNetwork is enabled${NC}"
        ((WARNINGS++))
    fi

    # Check for privileged containers
    if grep -q "privileged: true" "$file" 2>/dev/null; then
        echo -e "  ${YELLOW}WARNING: Privileged container detected${NC}"
        ((WARNINGS++))
    fi

    # Check for hostPath volumes
    if grep -q "hostPath:" "$file" 2>/dev/null; then
        echo -e "  ${YELLOW}WARNING: hostPath volume detected${NC}"
        ((WARNINGS++))
    fi

    echo -e "  ${GREEN}OK${NC}"
    return 0
}

# Run yamllint
run_yamllint() {
    local file="$1"

    if [ "${HAS_YAMLLINT:-false}" = true ]; then
        echo "Running yamllint: $file"
        if ! yamllint -d "{extends: relaxed, rules: {line-length: {max: 200}}}" "$file" 2>&1; then
            echo -e "  ${YELLOW}WARNING: yamllint found issues${NC}"
            ((WARNINGS++))
        fi
    fi
}

# Main validation loop
validate_manifest() {
    local file="$1"

    echo "----------------------------------------"
    echo "Validating: $file"
    echo "----------------------------------------"

    validate_yaml_syntax "$file" || true
    validate_k8s_schema "$file" || true
    validate_best_practices "$file" || true
    run_yamllint "$file" || true

    echo ""
}

# Run checks
check_tools

# Validate each file
for file in $YAML_FILES; do
    validate_manifest "$file"
done

# Summary
echo "=============================================="
echo "Validation Summary"
echo "=============================================="
echo ""
echo -e "Files validated: $(echo "$YAML_FILES" | wc -l | tr -d ' ')"

if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}Errors: $ERRORS${NC}"
else
    echo -e "${GREEN}Errors: 0${NC}"
fi

if [ $WARNINGS -gt 0 ]; then
    echo -e "${YELLOW}Warnings: $WARNINGS${NC}"
else
    echo -e "${GREEN}Warnings: 0${NC}"
fi

echo ""

if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}VALIDATION FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}VALIDATION PASSED${NC}"
    exit 0
fi
