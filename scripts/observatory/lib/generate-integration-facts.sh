#!/bin/bash
# Generate MCP/A2A integration status facts

source "$(dirname "$0")/common.sh"

# Find integration-related files
INTEGRATION_FILES=()
INTEGRATION_TYPES=()

# Check for MCP files
while IFS= read -r -d '' file; do
    INTEGRATION_FILES+=("$file")
    INTEGRATION_TYPES+=("mcp")
done < <(find . -name "*mcp*" -type f)

while IFS= read -r -d '' file; do
    INTEGRATION_FILES+=("$file")
    INTEGRATION_TYPES+=("a2a")
done < <(find . -name "*a2a*" -type f)

# Check for integration classes
INTEGRATION_CLASSES=()
while IFS= read -r -d '' file; do
    if grep -q "mcp\|integration\|autonomous" "$file"; then
        INTEGRATION_FILES+=("$file")
        INTEGRATION_TYPES+=("autonomous")
    fi
done < <(find . -name "*.java" -not -path "./node_modules/*")

# Analyze integration endpoints
ENDPOINTS=()
for file in "${INTEGRATION_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract endpoint information
        ENDPOINT_NAME=$(grep -o "class.*[Ee]ndpoint\|class.*[Hh]andler\|class.*[Ss]erver" "$file" | head -1 | sed 's/class *//' | cut -d' ' -f1)

        # Get HTTP methods if present
        METHODS=$(grep -o "@[Pp]ost\|@[Gg]et\|@[Pp]ut\|@[Dd]elete" "$file" | sed 's/@//' | tr '[:lower:]' '[:upper:]' | sort | uniq)

        # Get protocol type
        PROTOCOL="UNKNOWN"
        if grep -q "MCP" "$file"; then
            PROTOCOL="MCP"
        elif grep -q "A2A" "$file"; then
            PROTOCOL="A2A"
        elif grep -q "REST\|HTTP" "$file"; then
            PROTOCOL="HTTP"
        fi

        ENDPOINT_DATA=$(jq -n --arg file "$file" \
            --arg endpoint "$ENDPOINT_NAME" \
            --arg protocol "$PROTOCOL" \
            --argjson methods "$METHODS" \
            '{
                file_path: $file,
                endpoint_class: $endpoint,
                protocol_type: $protocol,
                http_methods: $methods,
                is_integration: true
            }')

        ENDPOINTS+=("$ENDPOINT_DATA")
    fi
done

# Find integration patterns
PATTERNS=()
PATTERN_TYPES=("async-callback" "event-driven" "workflow-integration" "database-trigger" "api-gateway")

for pattern in "${PATTERN_TYPES[@]}"; do
    MATCHES=$(find . -name "*.java" -exec grep -l "$pattern" {} \; 2>/dev/null | wc -l)
    if [ "$MATCHES" -gt 0 ]; then
        PATTERNS+=("$pattern:$MATCHES")
    fi
done

# Build integration data
INTEGRATION_DATA=$(jq -n --argjson endpoints "$(printf '%s\n' "${ENDPOINTS[@]}" | jq -s '.[]')" \
    --argjson patterns "$(printf '%s\n' "${PATTERNS[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson integration_files "${#INTEGRATION_FILES[@]}" \
    '{
        total_integration_files: $integration_files,
        endpoints: $endpoints,
        integration_patterns: $patterns,
        supported_protocols: ($endpoints | map(.protocol_type) | unique | .[]),
        endpoint_classes_count: ($endpoints | length)
    }')

FINAL_JSON=$(generate_base "generate-integration-facts.sh" "$INTEGRATION_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/integration.json"
clean_fact "$FACTS_DIR/integration.json"

echo "Generated integration.json with ${#INTEGRATION_FILES[@]} integration files"
exit 0