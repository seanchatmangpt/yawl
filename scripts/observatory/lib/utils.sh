#!/bin/bash
#
# Observatory utilities library
#

# Compute SHA256 of a file
compute_sha256() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        echo ""
        return 1
    fi
    sha256sum "$file" | cut -d' ' -f1
}

# Convert bytes to MB
bytes_to_mb() {
    local bytes="$1"
    echo "scale=2; $bytes / 1048576" | bc
}

# Parse Maven POM using Python/XML parsing
parse_pom() {
    local pom_file="$1"
    local xpath="$2"

    # Use Python for robust XML parsing
    python3 << EOF
import xml.etree.ElementTree as ET
import sys

try:
    tree = ET.parse('$pom_file')
    root = tree.getroot()

    # Define namespaces
    namespaces = {
        'pom': 'http://maven.apache.org/POM/4.0.0',
        'maven': 'http://maven.apache.org/POM/4.0.0'
    }

    # Find elements
    elements = root.findall('$xpath', namespaces)

    for elem in elements:
        if elem.text:
            print(elem.text)
except Exception as e:
    sys.stderr.write(f'Error parsing {pom_file}: {e}\\n')
    sys.exit(1)
EOF
}

# Get all module names from reactor
get_modules_from_pom() {
    local pom_file="$1"

    python3 << 'EOF'
import xml.etree.ElementTree as ET
import sys

try:
    tree = ET.parse(sys.argv[1])
    root = tree.getroot()

    # Define namespace
    ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

    # Get modules
    for module in root.findall('.//pom:module', ns):
        if module.text:
            print(module.text)
except Exception as e:
    sys.stderr.write(f'Error: {e}\n')
    sys.exit(1)
EOF
    return 0
}

# Read artifact ID from POM
get_artifact_id() {
    local pom_file="$1"

    python3 << 'EOF'
import xml.etree.ElementTree as ET
import sys

try:
    tree = ET.parse(sys.argv[1])
    root = tree.getroot()

    ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

    # Try artifactId first
    artifactId = root.find('.//pom:artifactId', ns)
    if artifactId is not None and artifactId.text:
        print(artifactId.text)
    else:
        sys.exit(1)
except Exception:
    sys.exit(1)
EOF
}

# Check if directory exists
dir_exists() {
    [[ -d "$1" ]]
}

# Check if file exists
file_exists() {
    [[ -f "$1" ]]
}

# Count files matching pattern
count_files() {
    local pattern="$1"
    find . -path "$pattern" -type f 2>/dev/null | wc -l
}

# Format JSON output with proper escaping
json_escape() {
    python3 -c "import sys, json; print(json.dumps(sys.stdin.read()))"
}

# Create JSON array from list
make_json_array() {
    python3 << 'EOF'
import sys
import json

items = []
for line in sys.stdin:
    line = line.strip()
    if line:
        items.append(line)

print(json.dumps(items))
EOF
}

# Format ISO 8601 timestamp
iso8601_now() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

# Format run ID (timestamp without separators)
run_id_now() {
    date -u +"%Y%m%dT%H%M%SZ"
}
