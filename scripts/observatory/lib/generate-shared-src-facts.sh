#!/bin/bash
# Generate files edited by multiple engineers facts

source "$(dirname "$0")/common.sh"

# Find all Java source files
JAVA_FILES=()
while IFS= read -r file; do
    JAVA_FILES+=("$file")
done < <(find . -name "*.java" -not -path "./node_modules/*" -not -path "./target/*")

# Analyze git history for multiple editors
SHARED_FILES=()

for file in "${JAVA_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Get number of unique authors who edited this file
        AUTHOR_COUNT=$(git log --format="%an" "$file" | sort -u | wc -l)

        # Get recent editors (last 30 days)
        RECENT_EDITORS=$(git log --since="30 days ago" --format="%an" "$file" | sort -u | wc -l)

        if [ "$AUTHOR_COUNT" -gt 1 ] || [ "$RECENT_EDITORS" -gt 1 ]; then
            # Get file details
            FILE_SIZE=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo 0)
            MOD_TIME=$(stat -f%m "$file" 2>/dev/null || stat -c%Y "$file" 2>/dev/null || echo 0)

            # Get top contributors
            CONTRIBUTORS=$(git log --format="%an" "$file" | sort | uniq -c | sort -nr | head -5 | \
                jq -R 'split("\n") | map(select(. != "")) | .[]' | \
                jq -R 'split(" ") | .[-1] as $count | .[0:-1] | join(" ") | {author: ., commits: ($count | tonumber)}')

            SHARED_FILE_DATA=$(jq -n --arg file "$file" \
                --argjson author_count "$AUTHOR_COUNT" \
                --argjson recent_editors "$RECENT_EDITORS" \
                --argjson file_size "$FILE_SIZE" \
                --argjson mod_time "$MOD_TIME" \
                --argjson contributors "$CONTRIBUTORS" \
                '{
                    file: $file,
                    total_editors: $author_count,
                    recent_editors: $recent_editors,
                    file_size_bytes: $file_size,
                    last_modified_epoch: $mod_time,
                    top_contributors: $contributors
                }')

            SHARED_FILES+=("$SHARED_FILE_DATA")
        fi
    fi
done

# Build shared source data
SHARED_DATA=$(jq -n --argjson files "$(printf '%s\n' "${SHARED_FILES[@]}" | jq -s '.[]')" \
    --argjson total "${#SHARED_FILES[@]}" \
    '{
        shared_files_count: $total,
        shared_files: $files,
        average_editors_per_file: ($files | map(.total_editors) | add / ($files | length))
    }')

FINAL_JSON=$(generate_base "generate-shared-src-facts.sh" "$SHARED_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/shared-src.json"
clean_fact "$FACTS_DIR/shared-src.json"

echo "Generated shared-src.json with ${#SHARED_FILES[@]} files edited by multiple engineers"
exit 0