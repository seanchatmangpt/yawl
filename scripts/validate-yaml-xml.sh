#!/bin/bash
# Validate YAML-generated XML against YAWL schema

# Exit immediately if a command exits with a non-zero status
set -e
set -u

# Configuration
SCHEMA="/Users/sac/cre/vendors/yawl/schema/YAWL_Schema4.0.xsd"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}✓ ${NC}${message}"
            ;;
        "ERROR")
            echo -e "${RED}✗ ${NC}${message}"
            ;;
        "WARNING")
            echo -e "${YELLOW}! ${NC}${message}"
            ;;
        "INFO")
            echo -e "${NC}ℹ  ${message}"
            ;;
    esac
}

# Function to show usage
show_usage() {
    echo "Usage: $0 <yaml-file>"
    echo ""
    echo "Validates a YAML workflow specification by converting it to XML and"
    echo "checking compliance with the YAWL Schema 4.0."
    echo ""
    echo "Arguments:"
    echo "  yaml-file    Path to the YAML workflow specification file"
    echo ""
    echo "Examples:"
    echo "  $0 workflow.yaml"
    echo "  $0 /path/to/specification.yaml"
    echo ""
    echo "Environment variables:"
    echo "  JAVA_HOME    Path to Java installation (optional)"
    exit 1
}

# Check if xmllint is available
if ! command -v xmllint &> /dev/null; then
    print_status "ERROR" "xmllint is not installed. Please install libxml2-utils."
    exit 1
fi

# Check input file
if [ -z "${1:-}" ]; then
    show_usage
fi

YAML_FILE="$1"

# Check if YAML file exists
if [ ! -f "$YAML_FILE" ]; then
    print_status "ERROR" "YAML file not found: $YAML_FILE"
    exit 1
fi

# Check if file is readable
if [ ! -r "$YAML_FILE" ]; then
    print_status "ERROR" "Cannot read YAML file: $YAML_FILE (permission denied)"
    exit 1
fi

# Check if schema exists
if [ ! -f "$SCHEMA" ]; then
    print_status "ERROR" "YAWL schema not found: $SCHEMA"
    exit 1
fi

# Check if schema is readable
if [ ! -r "$SCHEMA" ]; then
    print_status "ERROR" "Cannot read YAWL schema: $SCHEMA (permission denied)"
    exit 1
fi

# Get absolute paths
YAML_FILE_ABS=$(realpath "$YAML_FILE")
SCHEMA_ABS=$(realpath "$SCHEMA")

print_status "INFO" "Starting YAML validation..."
print_status "INFO" "Input file: $YAML_FILE_ABS"
print_status "INFO" "Schema file: $SCHEMA_ABS"

# Temporary file for generated XML
TEMP_XML=$(mktemp)

# Check if Java is available
if ! command -v java &> /dev/null; then
    print_status "ERROR" "Java is not installed or not in PATH"
    exit 1
fi

# Check if Maven is available for building
if ! command -v mvn &> /dev/null; then
    print_status "ERROR" "Maven is not installed or not in PATH"
    exit 1
fi

# Build the project if needed
print_status "INFO" "Checking if project is built..."
if [ ! -f "/Users/sac/cre/vendors/yawl/yawl-mcp-a2a-app/target/classes/org/yawlfoundation/yawl/mcp/a2a/example/ExtendedYamlConverter.class" ]; then
    print_status "WARNING" "Project not built. Building now..."
    if ! mvn -pl yawl-mcp-a2a-app clean compile -q; then
        print_status "ERROR" "Failed to build project"
        rm -f "$TEMP_XML"
        exit 1
    fi
    print_status "SUCCESS" "Project built successfully"
fi

# Check if dependencies are copied
if [ ! -f "/Users/sac/cre/vendors/yawl/yawl-mcp-a2a-app/target/dependency/jackson-dataformat-yaml-2.19.4.jar" ]; then
    print_status "INFO" "Copying dependencies..."
    if ! mvn -pl yawl-mcp-a2a-app dependency:copy-dependencies -DoutputDirectory=target/dependency -q; then
        print_status "ERROR" "Failed to copy dependencies"
        rm -f "$TEMP_XML"
        exit 1
    fi
    print_status "SUCCESS" "Dependencies copied successfully"
fi

# Read the YAML content
YAML_CONTENT=$(cat "$YAML_FILE_ABS")

# Create a temporary Java file that uses the ExtendedYamlConverter directly
TEMP_JAVA=$(mktemp).java
cat > "$TEMP_JAVA" << 'JAVA_EOF'
import java.io.*;
import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;

class YamlValidator {
    public static void main(String[] args) {
        try {
            // Read YAML from stdin
            StringBuilder yamlBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {
                yamlBuilder.append(line).append("\n");
            }
            
            // Convert to XML
            ExtendedYamlConverter converter = new ExtendedYamlConverter();
            String xml = converter.convertToXml(yamlBuilder.toString());
            
            // Output XML
            System.out.println(xml);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
JAVA_EOF

# Compile the validator
CLASSPATH="/Users/sac/cre/vendors/yawl/yawl-mcp-a2a-app/target/classes:/Users/sac/cre/vendors/yawl/yawl-mcp-a2a-app/target/dependency/*:/Users/sac/cre/vendors/yawl/yawl-engine/target/classes:/Users/sac/cre/vendors/yawl/yawl-elements/target/classes:/Users/sac/cre/vendors/yawl/yawl-stateless/target/classes:/Users/sac/cre/vendors/yawl/yawl-integration/target/classes:/Users/sac/cre/vendors/yawl/yawl-utilities/target/classes"

print_status "INFO" "Compiling validator..."
if ! javac -cp "$CLASSPATH" "$TEMP_JAVA"; then
    print_status "ERROR" "Failed to compile validator"
    rm -f "$TEMP_XML" "$TEMP_JAVA" "${TEMP_JAVA%.java}.class"
    exit 1
fi

# Run the validator
print_status "INFO" "Converting YAML to XML..."
echo "$YAML_CONTENT" | java -cp "$CLASSPATH:." YamlValidator > "$TEMP_XML" 2>&1

# Clean up temporary files
rm -f "$TEMP_JAVA" "${TEMP_JAVA%.java}.class"

# Check if conversion produced valid XML
if ! grep -q "<specificationSet" "$TEMP_XML"; then
    print_status "ERROR" "Converter did not produce valid XML"
    cat "$TEMP_XML" >&2
    rm -f "$TEMP_XML"
    exit 1
fi

print_status "SUCCESS" "YAML to XML conversion completed"

# Display the generated XML (first 20 lines)
print_status "INFO" "Generated XML preview (first 20 lines):"
echo "--------------------------------------------------"
head -20 "$TEMP_XML"
echo "--------------------------------------------------"

# Validate XML against schema
print_status "INFO" "Validating XML against YAWL Schema 4.0..."
if xmllint --schema "$SCHEMA_ABS" --noout "$TEMP_XML" 2>&1; then
    print_status "SUCCESS" "XML validation passed - the specification is compliant with YAWL Schema 4.0"
    VALIDATION_RESULT=0
else
    print_status "ERROR" "XML validation failed - specification is not compliant with YAWL Schema 4.0"
    VALIDATION_RESULT=1
fi

# Show validation details
echo ""
print_status "INFO" "Validation details:"
xmllint --schema "$SCHEMA_ABS" "$TEMP_XML"

# Clean up temporary file
rm -f "$TEMP_XML"

# Exit with appropriate status
if [ $VALIDATION_RESULT -eq 0 ]; then
    print_status "SUCCESS" "YAML specification validation completed successfully"
    exit 0
else
    print_status "ERROR" "YAML specification validation failed"
    exit 1
fi
