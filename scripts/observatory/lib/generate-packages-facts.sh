#!/bin/bash
# Generate package-info.java coverage facts

source "$(dirname "$0")/common.sh"

# Find Java package directories
PACKAGE_DIRS=()
while IFS= read -r -d '' dir; do
    if [[ "$dir" != "." ]] && [[ -f "$dir/package-info.java" ]]; then
        PACKAGE_DIRS+=("$dir")
    fi
done < <(find . -name "*.java" -type d -print0)

# Find all Java packages
ALL_PACKAGES=()
while IFS= read -r -d '' dir; do
    if [[ "$dir" != "." ]]; then
        PACKAGE_PATH=$(realpath --relative-to=. "$dir")
        ALL_PACKAGES+=("$PACKAGE_PATH")
    fi
done < <(find . -name "*.java" -type d -print0)

# Find package-info.java files
PACKAGE_INFO_FILES=()
while IFS= read -r -d '' file; do
    PACKAGE_INFO_FILES+=("$file")
done < <(find . -name "package-info.java" -type f -print0)

# Analyze package-info.java coverage
PACKAGE_INFO_ANALYSIS=()
for file in "${PACKAGE_INFO_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Extract package name from file path
        PACKAGE_NAME=$(echo "$file" | sed 's|^\./||' | sed 's|/package-info.java||' | tr '/' '.')

        # Get package description
        DESCRIPTION=$(grep -o "/\*\*.*\*/" "$file" | sed 's|/\*\*||' | sed 's|\*/||' | tr -d '\n' | head -1)

        # Check for annotations
        HAS_ANNOTATION=$(grep -q "@Deprecated\|@NonNull\|@Nullable" "$file" && echo "true" || echo "false")

        # Get version info if present
        VERSION=$(grep -o 'version.*[0-9.]*' "$file" | head -1)

        PACKAGE_INFO_DATA=$(jq -n --arg file "$file" \
            --arg package "$PACKAGE_NAME" \
            --arg description "$DESCRIPTION" \
            --argjson has_annotation "$HAS_ANNOTATION" \
            --argjson version "$VERSION" \
            '{
                file_path: $file,
                package_name: $package,
                description: $description,
                has_annotations: $has_annotation,
                version_info: $version,
                has_package_info: true
            }')

        PACKAGE_INFO_ANALYSIS+=("$PACKAGE_INFO_DATA")
    fi
done

# Calculate coverage
TOTAL_PACKAGES=${#ALL_PACKAGES[@]}
PACKAGE_INFO_COUNT=${#PACKAGE_INFO_FILES[@]}
COVERAGE_PERCENTAGE=0
if [ "$TOTAL_PACKAGES" -gt 0 ]; then
    COVERAGE_PERCENTAGE=$(( PACKAGE_INFO_COUNT * 100 / TOTAL_PACKAGES ))
fi

# Build packages data
PACKAGES_DATA=$(jq -n --argjson total_packages "$TOTAL_PACKAGES" \
    --argjson package_info_files "$PACKAGE_INFO_COUNT" \
    --argjson coverage_percentage "$COVERAGE_PERCENTAGE" \
    --argjson analysis "$(printf '%s\n' "${PACKAGE_INFO_ANALYSIS[@]}" | jq -s '.[]')" \
    --argjson all_packages "$(printf '%s\n' "${ALL_PACKAGES[@]}" | jq -R 'split("\n") | map(select(. != "")) | .[]')" \
    '{
        total_packages: $total_packages,
        package_info_files: $package_info_files,
        coverage_percentage: $coverage_percentage,
        package_analysis: $analysis,
        all_packages: $all_packages,
        packages_with_info: ($package_info_files * 100.0 / $total_packages | if . == null then 0 else . end),
        package_info_coverage_rate: ($package_info_files / $total_packages | if . == null then 0 else . end)
    }')

FINAL_JSON=$(generate_base "generate-packages-facts.sh" "$PACKAGES_DATA")
echo "$FINAL_JSON" > "$FACTS_DIR/packages.json"
clean_fact "$FACTS_DIR/packages.json"

echo "Generated packages.json with $TOTAL_PACKAGES total packages and $COVERAGE_PERCENTAGE% coverage"
exit 0