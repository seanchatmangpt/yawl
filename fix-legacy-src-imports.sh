#!/usr/bin/env bash
set -euo pipefail

# Script to fix imports in legacy src directory
# This adds the legacy src as an additional source directory to the appropriate modules

echo "=== Fixing Legacy Source Directory Imports ==="

# First, let's create a backup of the original files
BACKUP_DIR="backup-$(date +%s)"
mkdir -p "$BACKUP_DIR"
echo "Creating backup in: $BACKUP_DIR"

# Copy all legacy src files to backup
cp -r src/ "$BACKUP_DIR/"

# For each module that contains legacy code, add the legacy src as additional source
MODULES=(
    "yawl-elements"
    "yawl-engine"
    "yawl-stateless"
)

for module in "${MODULES[@]}"; do
    if [[ -f "$module/pom.xml" ]]; then
        echo "Processing module: $module"

        # Check if the module already has additional source directories
        if grep -q "additionalSourceDirectories" "$module/pom.xml"; then
            echo "  Module already has additional source directories"
        else
            # Add the legacy src as additional source directory
            echo "  Adding legacy src as additional source directory"

            # Insert the additional source directories configuration
            # Find the </build> tag and insert before it
            temp_pom="$module/pom.xml.tmp"

            # Create the build configuration with additional source directories
            awk '
            /<\/build>/ {
                print "        <additionalSourceDirectories>"
                print "            <additionalSourceDirectory>../../src</additionalSourceDirectory>"
                print "        </additionalSourceDirectories>"
                print ""
            }
            { print }
            ' "$module/pom.xml" > "$temp_pom"

            mv "$temp_pom" "$module/pom.xml"
            echo "  Updated $module/pom.xml"
        fi
    fi
done

echo ""
echo "=== Import Fix Complete ==="
echo "Backup created in: $BACKUP_DIR"
echo ""
echo "Next steps:"
echo "1. Run 'mvn clean compile' to test the changes"
echo "2. If there are still import errors, we may need to manually fix some imports"
echo "3. Run the provided script to fix any remaining import issues"