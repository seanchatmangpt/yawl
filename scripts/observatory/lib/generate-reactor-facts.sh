#!/bin/bash
# Generate Maven reactor build order facts

source "$(dirname "$0")/common.sh"

# Find all pom.xml files to understand reactor structure
POM_FILES=()
while IFS= read -r -d '' pom; do
    POM_FILES+=("$pom")
done < <(find . -name "pom.xml" -type f -print0)

# Build reactor information
REACTOR_JSON='[]'

# Find root pom (usually parent)
ROOT_POM=""
for pom in "${POM_FILES[@]}"; do
    if grep -q '<packaging>pom</packaging>' "$pom"; then
        ROOT_POM="$pom"
        break
    fi
done

# Parse reactor modules
MODULES_ORDER=()
MODULES_COUNT=0

for pom in "${POM_FILES[@]}"; do
    MODULE_DIR=$(dirname "$pom")
    MODULE_NAME=$(basename "$MODULE_DIR")

    # Check if this module has sub-modules
    SUBMODULES=()
    for subpom in "${POM_FILES[@]}"; do
        SUBPOM_DIR=$(dirname "$subpom")
        if [[ "$SUBPOM_DIR" == "$MODULE_DIR"* ]] && [[ "$SUBPOM_DIR" != "$MODULE_DIR" ]]; then
            SUBMODULES+=("$subpom")
        fi
    done

    # Convert submodules array to JSON
    SUBMODULES_JSON=$(printf '%s\n' "${SUBMODULES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[] | split("/") | .[-1]')

    # Get module details
    MODULE_DATA=$(jq -n --arg module "$MODULE_NAME" \
        --argjson depth "$(echo "$MODULE_DIR" | tr -d '/' | wc -c)" \
        --argjson submodules "$SUBMODULES_JSON" \
        '{
            name: $module,
            depth: $depth,
            submodule_count: ($submodules | length),
            has_submodules: ($submodules | length) > 0,
            full_path: "'"$MODULE_DIR"'"
        }')

    MODULES_ORDER=$(echo "$MODULES_ORDER" | jq ". + [$MODULE_DATA]")
    ((MODULES_COUNT++))
done

# Build reactor data
REACTOR_DATA=$(jq -n --argjson modules "$MODULES_ORDER" \
    --argjson total "$MODULES_COUNT" \
    --argjson root "$ROOT_POM" \
    '{
        root_pom: $root,
        total_modules: $total,
        modules: $modules,
        module_dependency_count: ($modules | map(.submodule_count) | add),
        depth: ($modules | map(.depth) | max)
    }')

FINAL_JSON=$(generate_base "generate-reactor-facts.sh" "$REACTOR_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/reactor.json"
clean_fact "$FACTS_DIR/reactor.json"

echo "Generated reactor.json with $MODULES_COUNT modules"
exit 0