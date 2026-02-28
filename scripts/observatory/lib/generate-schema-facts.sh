#!/bin/bash
# Generate XSD files and schema versions facts

source "$(dirname "$0")/common.sh"

# Find XSD files
XSD_FILES=()
SCHEMA_VERSIONS=()
SCHEMA_NAMES=()

while IFS= read -r -f file; do
    XSD_FILES+=("$file")
    # Extract schema name from filename
    SCHEMA_NAME=$(basename "$file" | sed 's/\.xsd$//')
    SCHEMA_NAMES+=("$SCHEMA_NAME")

    # Extract version if present
    VERSION=$(grep -o 'targetNamespace.*version=[0-9.]*' "$file" | sed 's/.*version=\([^"]*\).*/\1/' | head -1)
    if [ -z "$VERSION" ]; then
        VERSION=$(grep -o '<version>[0-9.]*</version>' "$file" | sed 's/<version>\([0-9.]*\)<\/version>/\1/' | head -1)
    fi
    if [ -z "$VERSION" ]; then
        VERSION="1.0"
    fi
    SCHEMA_VERSIONS+=("$VERSION")
done < <(find . -name "*.xsd" -not -path "./node_modules/*")

# Find schema validation files
VALIDATION_FILES=()
while IFS= read -r -d '' file; do
    if [[ "$file" == *validate* ]] || [[ "$file" == *schema* ]]; then
        VALIDATION_FILES+=("$file")
    fi
done < <(find . -name "*.java" -o -name "*.xml" | grep -i validate | sort -u)

# Analyze schema complexity
SCHEMA_STATS=()
for file in "${XSD_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Get element count
        ELEMENT_COUNT=$(grep -c '<element' "$file" 2>/dev/null || echo 0)
        # Get complex type count
        COMPLEX_TYPE_COUNT=$(grep -c '<complexType' "$file" 2>/dev/null || echo 0)
        # Get simple type count
        SIMPLE_TYPE_COUNT=$(grep -c '<simpleType' "$file" 2>/dev/null || echo 0)

        SCHEMA_DATA=$(jq -n --arg file "$file" \
            --argjson elements "$ELEMENT_COUNT" \
            --argjson complex "$COMPLEX_TYPE_COUNT" \
            --argjson simple "$SIMPLE_TYPE_COUNT" \
            '{
                file_path: $file,
                element_count: $elements,
                complex_type_count: $complex,
                simple_type_count: $simple,
                total_definitions: ($elements + $complex + $simple)
            }')

        SCHEMA_STATS+=("$SCHEMA_DATA")
    fi
done

# Find imports and includes
SCHEMA_DEPENDENCIES=()
for file in "${XSD_FILES[@]}"; do
    if [ -f "$file" ]; then
        IMPORTS=$(grep -o 'schemaLocation="[^"]*"' "$file" | sed 's/schemaLocation="\([^"]*\)"/\1/g')
        if [ -n "$IMPORTS" ]; then
            DEP_DATA=$(jq -n --arg file "$file" --argjson imports "$IMPORTS" '{
                source_file: $file,
                imports: $imports,
                dependency_count: ($imports | length)
            }')
            SCHEMA_DEPENDENCIES+=("$DEP_DATA")
        fi
    fi
done

# Build schema data
SCHEMA_DATA=$(jq -n --argjson files "$(printf '%s\n' "${XSD_FILES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson versions "$(printf '%s\n' "${SCHEMA_VERSIONS[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson names "$(printf '%s\n' "${SCHEMA_NAMES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson validation_files "$(printf '%s\n' "${VALIDATION_FILES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson stats "$(printf '%s\n' "${SCHEMA_STATS[@]}" | jq -s '.[]')" \
    --argjson dependencies "$(printf '%s\n' "${SCHEMA_DEPENDENCIES[@]}" | jq -s '.[]')" \
    '{
        xsd_files: $files,
        schema_versions: $versions,
        schema_names: $names,
        validation_files: $validation_files,
        schema_statistics: $stats,
        schema_dependencies: $dependencies,
        total_xsd_files: ($files | length),
        unique_schema_versions: ($versions | unique | .[]),
        has_dependencies: ($dependencies | length) > 0
    }')

FINAL_JSON=$(generate_base "generate-schema-facts.sh" "$SCHEMA_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/schema.json"
clean_fact "$FACTS_DIR/schema.json"

echo "Generated schema.json with ${#XSD_FILES[@]} XSD files"
exit 0