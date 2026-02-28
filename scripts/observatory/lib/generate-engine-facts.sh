#!/bin/bash
# Generate YEngine, YStatelessEngine entry points facts

source "$(dirname "$0")/common.sh"

# Find engine files
ENGINE_FILES=()
Y_ENGINE_FILES=()
Y_STATELESS_ENGINE_FILES=()

while IFS= read -r -f file; do
    ENGINE_FILES+=("$file")
    if [[ "$file" == *YEngine* ]]; then
        Y_ENGINE_FILES+=("$file")
    fi
    if [[ "$file" == *YStatelessEngine* ]]; then
        Y_STATELESS_ENGINE_FILES+=("$file")
    fi
done < <(find . -name "*.java" -not -path "./node_modules/*")

# Analyze engine entry points
ENGINE_CLASSES=()

for file in "${ENGINE_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract class name
        CLASS_NAME=$(grep -o "class.*Engine" "$file" | head -1 | sed 's/class *//' | cut -d' ' -f1)

        # Get entry point methods
        METHODS=$(grep -n "public.*main\|public static.*main\|public.*Engine.*(" "$file" | \
            while read -r line; do
                METHOD=$(echo "$line" | sed 's/.*public *\([^ ]*\) *\([^(]*\) *(.*/\1 \2/')
                LINE_NUM=$(echo "$line" | sed 's/^\([0-9]*\):.*/\1/')
                echo "{\"method\": \"$METHOD\", \"line\": $LINE_NUM}"
            done)

        # Get dependencies
        DEPS=$(grep -o "import.*Engine\|import.*Workflow\|import.*YAWL" "$file" | \
            sed 's/import \(.*\)\..*/\1/' | sort | uniq | jq -R 'split("\n") | map(select(. != "")) | .[]')

        # Get workflow patterns
        PATTERNS=$(grep -o "workflow\|net\|case\|workitem" "$file" | tr '[:lower:]' '[:upper]' | sort | uniq | jq -R 'split("\n") | map(select(. != "")) | .[]')

        ENGINE_DATA=$(jq -n --arg file "$file" \
            --arg class "$CLASS_NAME" \
            --argjson methods "$METHODS" \
            --argjson deps "$DEPS" \
            --argjson patterns "$PATTERNS" \
            '{
                file_path: $file,
                class_name: $class,
                entry_point_methods: $methods,
                dependencies: $deps,
                workflow_patterns: $patterns,
                is_engine: true
            }')

        ENGINE_CLASSES+=("$ENGINE_DATA")
    fi
done

# Analyze YEngine specifically
Y_ENGINE_DETAILS=()
for file in "${Y_ENGINE_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract YEngine specific features
        HAS_STATE=$(grep -q "stateful\|instance.*variables" "$file" && echo "true" || echo "false")
        HAS_WORKFLOW_MANAGER=$(grep -q "workflow.*manager\|case.*manager" "$file" && echo "true" || echo "false")
        HAS_MUTEX=$(grep -q "synchronized\|lock\|mutex" "$file" && echo "true" || echo "false")

        ENGINE_DETAIL=$(jq -n --arg file "$file" \
            --argjson stateful "$HAS_STATE" \
            --argjson has_manager "$HAS_WORKFLOW_MANAGER" \
            --argjson has_mutex "$HAS_MUTEX" \
            '{
                file_path: $file,
                is_stateful: $stateful,
                has_workflow_manager: $has_manager,
                has_mutex_protection: $has_mutex
            }')

        Y_ENGINE_DETAILS+=("$ENGINE_DETAIL")
    fi
done

# Analyze YStatelessEngine
STATELESS_ENGINE_DETAILS=()
for file in "${Y_STATELESS_ENGINE_FILES[@]}"; file
    if [ -f "$file" ]; then
        # Extract YStatelessEngine specific features
        HAS_NO_STATE=$(grep -q "@Stateless\|stateless.*engine" "$file" && echo "true" || echo "false")
        HAS_WORKFLOW_POOL=$(grep -q "workflow.*pool\|executor\|thread.*pool" "$file" && echo "true" || echo "false")

        STATELESS_DETAIL=$(jq -n --arg file "$file" \
            --argjson stateless "$HAS_NO_STATE" \
            --argjson has_pool "$HAS_WORKFLOW_POOL" \
            '{
                file_path: $file,
                is_stateless: $stateless,
                has_workflow_pool: $has_pool
            }')

        STATELESS_ENGINE_DETAILS+=("$STATELESS_DETAIL")
    fi
done

# Build engine data
ENGINE_DATA=$(jq -n --argjson y_engines "$(printf '%s\n' "${Y_ENGINE_DETAILS[@]}" | jq -s '.[]')" \
    --argjson y_stateless_engines "$(printf '%s\n' "${STATELESS_ENGINE_DETAILS[@]}" | jq -s '.[]')" \
    --argjson all_engines "$(printf '%s\n' "${ENGINE_CLASSES[@]}" | jq -s '.[]')" \
    --argjson total_engines "${#ENGINE_FILES[@]}" \
    '{
        y_engine_files: $y_engines,
        y_stateless_engine_files: $y_stateless_engines,
        all_engine_classes: $all_engines,
        total_engine_files: $total_engines,
        engine_count: ($y_engines | length),
        stateless_engine_count: ($y_stateless_engines | length)
    }')

FINAL_JSON=$(generate_base "generate-engine-facts.sh" "$ENGINE_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/engine.json"
clean_fact "$FACTS_DIR/engine.json"

echo "Generated engine.json with ${#Y_ENGINE_FILES[@]} YEngine and ${#Y_STATELESS_ENGINE_FILES[@]} YStatelessEngine files"
exit 0