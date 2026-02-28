#!/bin/bash
# Generate duplicate code and classes facts

source "$(dirname "$0")/common.sh"

# Find all Java files
JAVA_FILES=()
while IFS= read -r file; do
    JAVA_FILES+=("$file")
done < <(find . -name "*.java" -not -path "./node_modules/*" -not -path "./target/*")

# Generate code hashes
FILE_HASHES=()
DUPLICATES=()

# Calculate hashes for each file
for file in "${JAVA_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Generate normalized hash (remove whitespace, comments)
        HASH=$(cat "$file" | \
            sed -E 's|//.*||g' | \
            sed -E 's|/\*.*\*/||g' | \
            tr -d ' \t\n\r' | \
            sha256sum | cut -d' ' -f1)

        FILE_DATA=$(jq -n --arg file "$file" --arg hash "$HASH" '{file: $file, hash: $hash}')
        FILE_HASHES+=("$FILE_DATA")
    fi
done

# Find duplicates
declare -A HASH_MAP
for item in "${FILE_HASHES[@]}"; do
    HASH=$(echo "$item" | jq -r '.hash')
    FILE=$(echo "$item" | jq -r '.file')

    if [ -n "${HASH_MAP[$HASH]}" ]; then
        # Add to duplicates
        DUPLICATE_GROUP=$(jq -n --argjson files "$(echo "$HASH_MAP[$HASH]" | jq -n '{files: [$file]}') --argjson new_file "$FILE" '.files + [$new_file]' | jq -R '.')
        DUPLICATES+=("$DUPLICATE_GROUP")
        HASH_MAP[$HASH]="$(echo "$HASH_MAP[$HASH]" | jq --argjson add "$DUPLICATE_GROUP" '.files + $add.files | unique | .[]')"
    else
        HASH_MAP[$HASH]="$FILE"
    fi
done

# Find similar classes (same name, different packages)
SIMILAR_CLASSES=()
CLASS_NAMES=()

for file in "${JAVA_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract class name
        CLASS_NAME=$(grep -o "class [A-Za-z][A-Za-z0-9_]*" "$file" | head -1 | sed 's/class *//')

        if [ -n "$CLASS_NAME" ]; then
            # Check for duplicates
            if printf '%s\n' "${CLASS_NAMES[@]}" | grep -q "^$CLASS_NAME$"; then
                SIMILAR_CLASS_DATA=$(jq -n --arg file "$file" --arg class "$CLASS_NAME" '{file: $file, class_name: $class}')
                SIMILAR_CLASSES+=("$SIMILAR_CLASS_DATA")
            fi
            CLASS_NAMES+=("$CLASS_NAME")
        fi
    fi
done

# Build duplicates data
DUPLICATES_DATA=$(jq -n --argjson duplicates "$(printf '%s\n' "${DUPLICATES[@]}" | jq -s '.[]')" \
    --argjson similar "$(printf '%s\n' "${SIMILAR_CLASSES[@]}" | jq -s '.[]')" \
    --argjson total_files "${#JAVA_FILES[@]}" \
    --argjson duplicate_groups "$(printf '%s\n' "${DUPLICATES[@]}" | wc -l)" \
    '{
        total_java_files: $total_files,
        duplicate_code_groups: $duplicate_groups,
        duplicate_files: $duplicates,
        similar_class_names: $similar,
        duplicate_percentage: ($duplicate_groups * 100.0 / $total_files)
    }')

FINAL_JSON=$(generate_base "generate-duplicates-facts.sh" "$DUPLICATES_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/duplicates.json"
clean_fact "$FACTS_DIR/duplicates.json"

echo "Generated duplicates.json with $(printf '%s\n' "${DUPLICATES[@]}" | wc -l) duplicate groups"
exit 0