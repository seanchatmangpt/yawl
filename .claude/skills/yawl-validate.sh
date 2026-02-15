#!/bin/bash
# YAWL Validate Skill - Claude Code 2026 Best Practices
# Usage: /yawl-validate [file.xml] [--all-specs] [--schema=VERSION]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SCHEMA_DIR="${PROJECT_ROOT}/schema"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Validate Skill - Validate YAWL specifications

Usage: /yawl-validate [options] [file.xml]

Options:
  --all-specs      Validate all specs in exampleSpecs/
  --schema=VERSION Use specific schema version (default: 4.0)
  --strict         Enable strict validation
  -h, --help       Show this help message

Schema Versions:
EOF
    ls -1 "${SCHEMA_DIR}"/YAWL_Schema*.xsd 2>/dev/null | xargs -n1 basename | sed 's/YAWL_Schema/  /;s/.xsd//' || echo "  No schemas found"
}

# Parse arguments
TARGET_FILE=""
SCHEMA_VERSION="4.0"
VALIDATE_ALL=false
STRICT=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --all-specs)
            VALIDATE_ALL=true
            shift
            ;;
        --schema=*)
            SCHEMA_VERSION="${1#*=}"
            shift
            ;;
        --strict)
            STRICT="--strict"
            shift
            ;;
        *.xml|*.ywl)
            TARGET_FILE="$1"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Find schema file
SCHEMA_FILE="${SCHEMA_DIR}/YAWL_Schema${SCHEMA_VERSION}.xsd"

if [[ ! -f "${SCHEMA_FILE}" ]]; then
    echo -e "${RED}[yawl-validate] Schema file not found: ${SCHEMA_FILE}${NC}"
    echo "Available schemas:"
    ls -1 "${SCHEMA_DIR}"/YAWL_Schema*.xsd 2>/dev/null | xargs -n1 basename
    exit 1
fi

echo -e "${BLUE}[yawl-validate] Using schema: ${SCHEMA_FILE}${NC}"

# Validate function
validate_file() {
    local FILE="$1"
    echo -e "${BLUE}[yawl-validate] Validating: ${FILE}${NC}"

    if [[ ! -f "${FILE}" ]]; then
        echo -e "  ${RED}File not found${NC}"
        return 1
    fi

    # Use xmllint if available
    if command -v xmllint &> /dev/null; then
        if xmllint --noout --schema "${SCHEMA_FILE}" ${STRICT} "${FILE}" 2>&1; then
            echo -e "  ${GREEN}Valid${NC}"
            return 0
        else
            echo -e "  ${RED}Invalid${NC}"
            return 1
        fi
    else
        echo -e "  ${YELLOW}xmllint not available, skipping XML validation${NC}"
        echo -e "  ${YELLOW}Install with: brew install libxml2 (macOS)${NC}"
        return 0
    fi
}

# Execute validation
FAILED=0

if [[ "${VALIDATE_ALL}" == "true" ]]; then
    echo -e "${BLUE}[yawl-validate] Validating all specifications...${NC}"
    echo ""

    for FILE in "${PROJECT_ROOT}"/exampleSpecs/*.xml "${PROJECT_ROOT}"/exampleSpecs/*.ywl; do
        if [[ -f "${FILE}" ]]; then
            validate_file "${FILE}" || ((FAILED++))
        fi
    done

    for FILE in "${PROJECT_ROOT}"/test/*.ywl; do
        if [[ -f "${FILE}" ]]; then
            validate_file "${FILE}" || ((FAILED++))
        fi
    done

    echo ""
    if [[ ${FAILED} -eq 0 ]]; then
        echo -e "${GREEN}[yawl-validate] All specifications are valid${NC}"
    else
        echo -e "${RED}[yawl-validate] ${FAILED} specification(s) failed validation${NC}"
    fi
elif [[ -n "${TARGET_FILE}" ]]; then
    if [[ "${TARGET_FILE:0:1}" != "/" ]]; then
        TARGET_FILE="${PROJECT_ROOT}/${TARGET_FILE}"
    fi
    validate_file "${TARGET_FILE}" || FAILED=1
else
    echo -e "${RED}[yawl-validate] No file specified. Use --all-specs or provide a file path.${NC}"
    print_usage
    exit 1
fi

exit ${FAILED}
