#!/bin/bash
# Generate stateful vs stateless engine classes facts

source "$(dirname "$0")/common.sh"

# Find YAWL engine files
ENGINE_FILES=()
STATEFUL_ENGINES=()
STATELESS_ENGINES=()

while IFS= read -r file; do
    if [[ "$file" == *YEngine* ]]; then
        ENGINE_FILES+=("$file")
        if [[ "$file" == *YStatelessEngine* ]]; then
            STATELESS_ENGINES+=("$file")
        else
            STATEFUL_ENGINES+=("$file")
        fi
    fi
done < <(find . -name "*.java" -not -path "./node_modules/*")

# Analyze engine classes
ENGINE_CLASSES=()

for file in "${ENGINE_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract class name
        CLASS_NAME=$(grep -o "class.*[Ee]ngine" "$file" | head -1 | sed 's/class *//' | cut -d' ' -f1)

        # Check if stateful
        IS_STATEFUL=0
        if grep -q "instance variables\|private.*;" "$file" || \
           grep -q "@Stateful\|@SessionScoped\|@ManagedBean" "$file"; then
            IS_STATEFUL=1
        fi

        # Check for stateless patterns
        IS_STATELESS=0
        if grep -q "@Stateless\|@ApplicationScoped\|@Singleton" "$file" || \
           grep -q "stateless.*engine\|StatelessEngine" "$file"; then
            IS_STATELESS=1
        fi

        # Get methods count
        METHODS_COUNT=$(grep -c "public.*(" "$file" 2>/dev/null || echo 0)

        # Get dependencies
        DEPENDENCIES=$(grep -o "import.*Engine\|import.*[Ee]ngine" "$file" | \
            sed 's/import \(.*\)\..*/\1/' | sort | uniq | jq -R 'split("\n") | map(select(. != "")) | .[]')

        CLASS_DATA=$(jq -n --arg file "$file" \
            --arg class "$CLASS_NAME" \
            --argjson stateful "$IS_STATEFUL" \
            --argjson stateless "$IS_STATELESS" \
            --argjson methods "$METHODS_COUNT" \
            --argjson deps "$DEPENDENCIES" \
            '{
                file_path: $file,
                class_name: $class,
                is_stateful: $stateful,
                is_stateless: $stateless,
                method_count: $methods,
                depends_on_engines: $deps
            }')

        ENGINE_CLASSES+=("$CLASS_DATA")
    fi
done

# Build dual family data
DUAL_DATA=$(jq -n --argjson stateful "${#STATEFUL_ENGINES[@]}" \
    --argjson stateless "${#STATELESS_ENGINES[@]}" \
    --argjson total "${#ENGINE_FILES[@]}" \
    --argjson classes "$(printf '%s\n' "${ENGINE_CLASSES[@]}" | jq -s '.[]')" \
    '{
        stateful_engines: $stateful,
        stateless_engines: $stateless,
        total_engine_files: $total,
        engine_classes: $classes,
        family_ratio: ($stateful / $total | if . == null then 0 else . end)
    }')

FINAL_JSON=$(generate_base "generate-dual-family-facts.sh" "$DUAL_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/dual-family.json"
clean_fact "$FACTS_DIR/dual-family.json"

echo "Generated dual-family.json with ${#STATEFUL_ENGINES[@]} stateful and ${#STATELESS_ENGINES[@]} stateless engines"
exit 0