#!/bin/bash
# Generate dependency conflicts and versions facts

source "$(dirname "$0")/common.sh"

# Find all dependency files
DEPENDENCY_FILES=()

# Maven pom.xml files
while IFS= read -r -d '' file; do
    DEPENDENCY_FILES+=("$file")
done < <(find . -name "pom.xml" -type f -print0)

# Gradle build.gradle files
while IFS= read -r -d '' file; do
    DEPENDENCY_FILES+=("$file")
done < <(find . -name "build.gradle" -type f -print0)

# package.json files
while IFS= read -r -d '' file; do
    DEPENDENCY_FILES+=("$file")
done < <(find . -name "package.json" -type f -print0)

# requirements.txt files
while IFS= read -r -d '' file; do
    DEPENDENCY_FILES+=("$file")
done < <(find . -name "requirements.txt" -type f -print0)

# Parse dependencies
ALL_DEPS=()
CONFLICTS=()

for file in "${DEPENDENCY_FILES[@]}"; do
    FILE_TYPE=$(basename "$file")
    FILE_DIR=$(dirname "$file")

    case "$FILE_TYPE" in
        "pom.xml")
            # Extract Maven dependencies
            while IFS= read -r dep; do
                if [ -n "$dep" ]; then
                    ALL_DEPS+=("maven:$dep")
                fi
            done < <(grep -E '<groupId>.*</groupId>\s*<artifactId>.*</artifactId>\s*<version>' "$file" | \
                sed 's/.*<groupId>\([^<]*\)<\/groupId>\s*<artifactId>\([^<]*\)<\/artifactId>\s*<version>\([^<]*\)<.*/\1:\2:\3/' | \
                sort | uniq)
            ;;
        "package.json")
            # Extract npm dependencies
            if [ -f "$file" ]; then
                DEPS_JSON=$(jq '.dependencies, .devDependencies' "$file" 2>/dev/null || echo '{}')
                if [ -n "$DEPS_JSON" ]; then
                    while IFS= read -r dep; do
                        if [ -n "$dep" ]; then
                            ALL_DEPS+=("npm:$dep")
                        fi
                    done < <(echo "$DEPS_JSON" | jq -r 'to_entries | .[] | "\(.key):\(.version)"' | sort | uniq)
                fi
            fi
            ;;
        "requirements.txt")
            # Extract Python dependencies
            while IFS= read -r dep; do
                if [ -n "$dep" ] && [[ ! "$dep" == \#* ]]; then
                    ALL_DEPS+=("python:$dep")
                fi
            done < <(grep -v '^#' "$file" | grep -v '^$' | sort | uniq)
            ;;
    esac
done

# Find dependency conflicts
declare DEP_MAP
for dep in "${ALL_DEPS[@]}"; do
    dep_key=$(echo "$dep" | cut -d: -f2)
    dep_version=$(echo "$dep" | cut -d: -f3)

    if [ -n "${DEP_MAP[$dep_key]}" ]; then
        if [ "${DEP_MAP[$dep_key]}" != "$dep_version" ]; then
            CONFLICTS+=("$dep_key:${DEP_MAP[$dep_KEY]} vs $dep_version")
        fi
    else
        DEP_MAP[$dep_key]=$dep_version
    fi
done

# Count unique dependencies
UNIQUE_DEPS=($(printf '%s\n' "${ALL_DEPS[@]}" | sort | uniq | tr '\n' ' '))
DEP_TYPES=($(printf '%s\n' "${UNIQUE_DEPS[@]}" | cut -d: -f1 | sort | uniq | tr '\n' ' '))

# Build JSON
DEPS_DATA=$(jq -n --argjson total_deps "${#UNIQUE_DEPS[@]}" \
    --argjson conflicts "$(printf '%s\n' "${CONFLICTS[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson type_count "$(printf '%s\n' "${DEP_TYPES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    --argjson dependency_files "${#DEPENDENCY_FILES[@]}" \
    '{
        total_dependencies: $total_deps,
        dependency_files_count: $dependency_files,
        conflicts: $conflicts,
        dependency_types: $type_count,
        unique_dependency_count: ($total_deps - ($conflicts | length))
    }')

FINAL_JSON=$(generate_base "generate-deps-facts.sh" "$DEPS_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/deps-conflicts.json"
clean_fact "$FACTS_DIR/deps-conflicts.json"

echo "Generated deps-conflicts.json with ${#UNIQUE_DEPS[@]} unique dependencies and ${#CONFLICTS[@]} conflicts"
exit 0