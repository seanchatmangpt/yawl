#!/bin/bash

# YAML to YAWL XML Converter
# Converts pattern YAML files to YAWL XML specifications

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "${RED}ERROR:${NC} $1" >&2
    exit 1
}

success() {
    echo "${GREEN}SUCCESS:${NC} $1"
}

warn() {
    echo "${YELLOW}WARNING:${NC} $1"
}

# Validate input
validate_input() {
    local yaml_file="$1"
    local output_dir="${2:-/tmp}"

    if [[ -z "$yaml_file" ]]; then
        error "Usage: $0 <yaml_file> [output_dir]"
    fi

    if [[ ! -f "$yaml_file" ]]; then
        error "YAML file not found: $yaml_file"
    fi

    if [[ ! -d "$output_dir" ]]; then
        mkdir -p "$output_dir" || error "Cannot create output directory: $output_dir"
    fi
}

# Extract pattern information from YAML
extract_pattern_info() {
    local yaml_file="$1"

    # Extract basic info from filename
    local filename=$(basename "$yaml_file")
    local pattern_id=$(echo "$filename" | sed 's/\.yaml$//' | tr '-' '_')

    # Try to extract name and description from YAML
    local name=$(grep -E "^\s*name:" "$yaml_file" | sed 's/name:\s*//' | tr -d '"' | xargs || echo "$pattern_id")
    local description=$(grep -E "^\s*description:" "$yaml_file" | sed 's/description:\s*//' | tr -d '"' | xargs || echo "YAWL workflow pattern")

    echo "$pattern_id|$name|$description"
}

# Generate YAWL XML from pattern definition
generate_yawl_xml() {
    local yaml_file="$1"
    local output_dir="$2"
    local pattern_info=($(extract_pattern_info "$yaml_file"))
    local pattern_id="${pattern_info[0]}"
    local name="${pattern_info[1]}"
    local description="${pattern_info[2]}"
    local xml_file="${output_dir}/${pattern_id}.xml"

    log "Generating YAWL XML for: $name ($pattern_id)"

    # Read YAML content
    local yaml_content
    yaml_content=$(cat "$yaml_file")

    # Extract pattern components
    local inputs=$(echo "$yaml_content" | grep -E "^\s*inputs:" -A 10 | sed '/^.*:$/d' | grep -E "^\s+-" | sed 's/^\s*-\s*//' | sed 's/,.*$//' | tr '\n' ' ' | xargs)
    local outputs=$(echo "$yaml_content" | grep -E "^\s*outputs:" -A 10 | sed '/^.*:$/d' | grep -E "^\s+-" | sed 's/^\s*-\s*//' | sed 's/,.*$//' | tr '\n' ' ' | xargs)

    # Generate XML
    cat > "$xml_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="${pattern_id}" name="${name}" version="1.0"/>
    <description>${description}</description>
    <schemaVersion>2.0</schemaVersion>

    <!-- Input conditions -->
    <inputCondition>
EOF

    if [[ -n "$inputs" ]]; then
        for input in $inputs; do
            echo "        <parameter name=\"${input}\"/>" >> "$xml_file"
        done
    else
        echo "        <parameter name=\"data\"/>" >> "$xml_file"
    fi

    cat >> "$xml_file" << EOF
    </inputCondition>

    <!-- Process definition -->
    <process id="${pattern_id}-process">
        <name>${name} Process</name>
        <nodes>
            <!-- Start node -->
            <node id="start" type="Start" name="Start">
EOF

    if [[ -n "$inputs" ]]; then
        echo "                <inputMapping>" >> "$xml_file"
        for input in $inputs; do
            echo "                    <map from=\"${input}\"/>" >> "$xml_file"
        done
        echo "                </inputMapping>" >> "$xml_file"
    fi

    cat >> "$xml_file" << EOF
            </node>
EOF

    # Add task nodes based on pattern type
    local pattern_type=$(echo "$pattern_id" | tr '_' '-')
    case "$pattern_type" in
        *sequence*)
            cat >> "$xml_file" << EOF
            <!-- Task nodes for sequence -->
            <node id="task1" type="Task" name="Task1">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
            <node id="task2" type="Task" name="Task2">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
EOF
            ;;
        *parallel*)
            cat >> "$xml_file" << EOF
            <!-- Task nodes for parallel split -->
            <node id="task1" type="Task" name="Task1">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
            <node id="task2" type="Task" name="Task2">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
            <node id="sync" type="Join" name="Synchronization">
                <joinType>AND</joinType>
            </node>
EOF
            ;;
        *choice*)
            cat >> "$xml_file" << EOF
            <!-- Task nodes for exclusive choice -->
            <node id="task1" type="Task" name="Choice1">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
            <node id="task2" type="Task" name="Choice2">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
EOF
            ;;
        *)
            # Default task nodes
            cat >> "$xml_file" << EOF
            <!-- Default task node -->
            <node id="task1" type="Task" name="Task1">
                <inputMapping>
                    <map from="data"/>
                </inputMapping>
            </node>
EOF
            ;;
    esac

    cat >> "$xml_file" << EOF
            <!-- End node -->
            <node id="end" type="End" name="End">
EOF

    if [[ -n "$outputs" ]]; then
        echo "                <outputMapping>" >> "$xml_file"
        for output in $outputs; do
            echo "                    <map to=\"${output}\"/>" >> "$xml_file"
        done
        echo "                </outputMapping>" >> "$xml_file"
    fi

    cat >> "$xml_file" << EOF
            </node>
        </nodes>

        <!-- Arcs -->
        <arcs>
EOF

    # Generate arcs based on pattern type
    case "$pattern_type" in
        *sequence*)
            echo "            <arc id=\"arc1\" from=\"start\" to=\"task1\"/>" >> "$xml_file"
            echo "            <arc id=\"arc2\" from=\"task1\" to=\"task2\"/>" >> "$xml_file"
            echo "            <arc id=\"arc3\" from=\"task2\" to=\"end\"/>" >> "$xml_file"
            ;;
        *parallel*)
            echo "            <arc id=\"arc1\" from=\"start\" to=\"task1\"/>" >> "$xml_file"
            echo "            <arc id=\"arc2\" from=\"start\" to=\"task2\"/>" >> "$xml_file"
            echo "            <arc id=\"arc3\" from=\"task1\" to=\"sync\"/>" >> "$xml_file"
            echo "            <arc id=\"arc4\" from=\"task2\" to=\"sync\"/>" >> "$xml_file"
            echo "            <arc id=\"arc5\" from=\"sync\" to=\"end\"/>" >> "$xml_file"
            ;;
        *choice*)
            echo "            <arc id=\"arc1\" from=\"start\" to=\"task1\"/>" >> "$xml_file"
            echo "            <arc id=\"arc2\" from=\"start\" to=\"task2\"/>" >> "$xml_file"
            echo "            <arc id=\"arc3\" from=\"task1\" to=\"end\"/>" >> "$xml_file"
            echo "            <arc id=\"arc4\" from=\"task2\" to=\"end\"/>" >> "$xml_file"
            ;;
        *)
            echo "            <arc id=\"arc1\" from=\"start\" to=\"task1\"/>" >> "$xml_file"
            echo "            <arc id=\"arc2\" from=\"task1\" to=\"end\"/>" >> "$xml_file"
            ;;
    esac

    cat >> "$xml_file" << EOF
        </arcs>
    </process>

    <!-- Output conditions -->
    <outputCondition>
EOF

    if [[ -n "$outputs" ]]; then
        for output in $outputs; do
            echo "        <parameter name=\"${output}\"/>" >> "$xml_file"
        done
    else
        echo "        <parameter name=\"result\"/>" >> "$xml_file"
    fi

    cat >> "$xml_file" << EOF
    </outputCondition>
</specification>
EOF

    echo "$xml_file"
}

# Convert batch of YAML files
convert_batch() {
    local pattern_dir="$1"
    local output_dir="$2"

    log "Converting all YAML files in: $pattern_dir"

    if [[ ! -d "$pattern_dir" ]]; then
        error "Pattern directory not found: $pattern_dir"
    fi

    local yaml_files=($(find "$pattern_dir" -name "*.yaml" | sort))
    local converted=0
    local failed=0

    for yaml_file in "${yaml_files[@]}"; do
        log "Converting: $(basename "$yaml_file")"
        if generate_yawl_xml "$yaml_file" "$output_dir"; then
            ((converted++))
        else
            ((failed++))
        fi
    done

    success "Conversion complete: $converted files converted, $failed files failed"
}

# Main execution
main() {
    case "${1:-}" in
        batch)
            if [[ -z "${2:-}" || -z "${3:-}" ]]; then
                error "Usage: $0 batch <pattern_dir> <output_dir>"
            fi
            convert_batch "$2" "$3"
            ;;
        *)
            if [[ -z "${1:-}" ]]; then
                error "Usage: $0 <yaml_file> [output_dir]"
            fi
            validate_input "$1" "${2:-/tmp}"
            generate_yawl_xml "$1" "${2:-/tmp}"
            ;;
    esac
}

# Run main
main "$@"