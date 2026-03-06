#!/usr/bin/env bash
set -euo pipefail

# Script to fix import statements in legacy src directory
# This script updates package imports to reference the correct module locations

LEGACY_SRC_DIR="src"
TEMP_DIR="temp-fix-$(date +%s)"
mkdir -p "$TEMP_DIR"

# Read the package mapping
while IFS='=' read -r old_package new_package; do
    # Skip comments and empty lines
    [[ "$old_package" =~ ^\s*# ]] && continue
    [[ -z "$old_package" ]] && continue

    # Find all Java files with the old import
    find "$LEGACY_SRC_DIR" -name "*.java" -type f -exec grep -l "import $old_package" {} \; | while read -r file; do
        echo "Fixing imports in: $file"

        # Create backup
        cp "$file" "$TEMP_DIR/$(basename "$file").backup"

        # Replace the import
        sed -i.tmp "s|import $old_package|import $new_package|g" "$file"
        rm "$file".tmp

        # Also replace any references in the code (if package name changed)
        if [[ "$old_package" != "$new_package" ]]; then
            # This is more complex and may need manual review
            echo "  Warning: Package name changed from $old_package to $new_package"
            echo "  Manual review needed for class references"
        fi
    done
done < src-fix-map.txt

echo "Import fixes completed. Review changes in $TEMP_DIR for any issues."
echo "Test with: mvn compile -q"

# Cleanup
rm -rf "$TEMP_DIR"