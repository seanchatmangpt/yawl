#!/bin/bash

# YAWL Terraform Output Helper Script
# Usage: ./scripts/output.sh [output-name]
# Example: ./scripts/output.sh app_service_default_hostname

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

cd "$PROJECT_ROOT"

if [ $# -eq 0 ]; then
    # Show all outputs
    print_header "Terraform Outputs"
    terraform output
else
    # Show specific output
    OUTPUT_NAME="$1"
    print_header "Output: $OUTPUT_NAME"

    if terraform output "$OUTPUT_NAME" &> /dev/null; then
        terraform output "$OUTPUT_NAME"
    else
        echo -e "${YELLOW}Output '$OUTPUT_NAME' not found${NC}"
        echo ""
        echo "Available outputs:"
        terraform output -json | jq 'keys[]' -r | sed 's/^/  - /'
    fi
fi
