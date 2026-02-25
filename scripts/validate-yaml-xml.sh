#!/usr/bin/env bash
# Validate YAML-generated XML against YAWL schema

set -euo pipefail

# Configuration
SCHEMA="/Users/sac/cre/vendors/yawl/schema/YAWL_Schema4.0.xsd"
MODULE="yawl-mcp-a2a-app"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS") echo -e "${GREEN}✓ ${NC}${message}" ;;
        "ERROR") echo -e "${RED}✗ ${NC}${message}" ;;
        "WARNING") echo -e "${YELLOW}! ${NC}${message}" ;;
        *) echo -e "${NC}ℹ  ${message}" ;;
    esac
}

show_usage() {
    echo "Usage: $0 <yaml-file>"
    echo ""
    echo "Validates a YAML workflow specification by converting it to XML and"
    echo "checking compliance with the YAWL Schema 4.0."
    echo ""
    echo "Arguments:"
    echo "  yaml-file    Path to the YAML workflow specification file"
}

if [ -z "${1:-}" ]; then
    show_usage
    exit 1
fi

YAML_FILE="$1"
YAML_ABS=$(realpath "$YAML_FILE")
SCHEMA_ABS=$(realpath "$SCHEMA")

print_status "INFO" "Starting YAML validation..."
print_status "INFO" "Input file: $YAML_ABS"
print_status "INFO" "Schema file: $SCHEMA_ABS"

# Ensure project is built
print_status "INFO" "Ensuring project is built..."
mvn -pl "$MODULE" clean compile -q

# Copy dependencies
print_status "INFO" "Copying dependencies..."
mvn -pl "$MODULE" dependency:copy-dependencies -DoutputDirectory=target/dependency -q

# Set up classpath
MODULE_CP="$MODULE/target/classes"
MODULE_DEPS="$MODULE/target/dependency/*"

# Add all YAWL module dependencies
for module in yawl-engine yawl-elements yawl-stateless yawl-integration yawl-utilities; do
    if [ -d "$module/target/classes" ]; then
        MODULE_CP="$MODULE_CP:$module/target/classes"
    fi
    if [ -d "$module/target/dependency" ]; then
        MODULE_DEPS="$MODULE_DEPS:$module/target/dependency/*"
    fi
done

FULL_CP="$MODULE_CP:$MODULE_DEPS"

# Temp file for XML
TEMP_XML=$(mktemp)

print_status "INFO" "Converting YAML to XML..."
java -cp "$FULL_CP" org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter "$YAML_ABS" 2>&1 | \
    awk '/===.*XML Output ===/,/===.*Features ===/' | \
    grep -v "^\[.*\]" | \
    grep -v "^---" | \
    grep -v "^$" | \
    sed '1d;$d' > "$TEMP_XML"

# Check if conversion succeeded
if [ -s "$TEMP_XML" ] && grep -q "<specificationSet" "$TEMP_XML" 2>/dev/null; then
    print_status "SUCCESS" "YAML to XML conversion completed"
    
    # Show preview
    print_status "INFO" "Generated XML preview:"
    echo "--------------------------------------------------"
    head -20 "$TEMP_XML"
    echo "--------------------------------------------------"
    
    # Validate
    print_status "INFO" "Validating against schema..."
    if xmllint --schema "$SCHEMA_ABS" --noout "$TEMP_XML" 2>/dev/null; then
        print_status "SUCCESS" "XML validation passed - specification is compliant with YAWL Schema 4.0"
        echo ""
        print_status "INFO" "Validation details:"
        xmllint --schema "$SCHEMA_ABS" "$TEMP_XML"
        
        print_status "SUCCESS" "YAML specification validation completed successfully"
        rm -f "$TEMP_XML"
        exit 0
    else
        print_status "ERROR" "XML validation failed - specification is not compliant with YAWL Schema 4.0"
        echo ""
        print_status "INFO" "Validation errors:"
        xmllint --schema "$SCHEMA_ABS" "$TEMP_XML"
        
        rm -f "$TEMP_XML"
        exit 1
    fi
else
    print_status "ERROR" "Conversion failed - no valid XML generated"
    cat "$TEMP_XML" >&2
    rm -f "$TEMP_XML"
    exit 1
fi
