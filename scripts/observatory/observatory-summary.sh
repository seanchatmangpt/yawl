#!/bin/bash
# YAWL Observatory Summary - Generated facts summary

set -euo pipefail

FACTS_DIR=".claude/facts"

echo "YAWL Observatory v6.0.0 - Generated Facts Summary"
echo "================================================"
echo

# Check if facts directory exists
if [[ ! -d "$FACTS_DIR" ]]; then
    echo "Facts directory not found: $FACTS_DIR"
    exit 1
fi

# Count total fact files
TOTAL_FACTS=$(ls -1 "$FACTS_DIR"/*.json 2>/dev/null | wc -l)
echo "Total fact files generated: $TOTAL_FACTS"
echo

# Generate summary for each fact
for fact_file in "$FACTS_DIR"/*.json; do
    if [[ -f "$fact_file" ]]; then
        fact_name=$(basename "$fact_file" .json)

        # Extract key statistics
        echo "=== $fact_name ==="

        case "$fact_name" in
            modules)
                echo "  • Modules: $(jq -r '.data.modules_count' "$fact_file")"
                echo "  • Java modules: $(jq -r '.data.java_modules' "$fact_file")"
                echo "  • Test modules: $(jq -r '.data.test_modules' "$fact_file")"
                ;;
            tests)
                echo "  • Test files: $(jq -r '.data.test_files_count' "$fact_file")"
                echo "  • Test methods: $(jq -r '.data.test_methods_count' "$fact_file")"
                echo "  • Coverage: $(jq -r '.data.coverage_percentage' "$fact_file")%"
                ;;
            deps-conflicts)
                echo "  • Total dependencies: $(jq -r '.data.total_dependencies' "$fact_file")"
                echo "  • Conflicts found: $(jq -r '.data.conflicts | length' "$fact_file")"
                ;;
            interfaces)
                echo "  • Total interfaces: $(jq -r '.data.total_interfaces' "$fact_file")"
                echo "  • Type A: $(jq -r '.data.interface_type_a' "$fact_file")"
                echo "  • Type B: $(jq -r '.data.interface_type_b' "$fact_file")"
                echo "  • Type E: $(jq -r '.data.interface_type_e' "$fact_file")"
                echo "  • Type X: $(jq -r '.data.interface_type_x' "$fact_file")"
                ;;
            packages)
                echo "  • Total packages: $(jq -r '.data.total_packages' "$fact_file")"
                echo "  • Package info files: $(jq -r '.data.package_info_files' "$fact_file")"
                echo "  • Coverage: $(jq -r '.data.coverage_percentage' "$fact_file")%"
                ;;
            gates)
                echo "  • Gate files: $(jq -r '.data.gate_files_count' "$fact_file")"
                echo "  • Gate classes: $(jq -r '.data.gate_classes_count' "$fact_file")"
                echo "  • Gate methods: $(jq -r '.data.gate_methods_count' "$fact_file")"
                ;;
            *)
                echo "  • Generated: $(jq -r '.generated_at' "$fact_file")"
                echo "  • Data fields: $(jq '.data | keys | length' "$fact_file")"
                ;;
        esac
        echo
    fi
done

# Calculate overall metrics
TOTAL_LATEST_MOD=0
for file in "$FACTS_DIR"/*.json; do
    if [[ -f "$file" ]]; then
        MOD_TIME=$(stat -f %m "$file" 2>/dev/null || stat -c %Y "$file" 2>/dev/null)
        TOTAL_LATEST_MOD=$((TOTAL_LATEST_MOD + MOD_TIME))
    fi
done

AVG_MOD_TIME=$((TOTAL_LATEST_MOD / TOTAL_FACTS))

echo "=== Summary ==="
echo "• All $TOTAL_FACTS fact files generated successfully"
echo "• Average file modification time: $(date -r "$AVG_MOD_TIME" +"%Y-%m-%d %H:%M:%S")"
echo "• Output directory: $FACTS_DIR"
echo
echo "Facts can be used for:"
echo "• Repository observatory analysis"
echo "• Code quality metrics"
echo "• Dependency management"
echo "• Test coverage tracking"
echo "• Architecture insights"