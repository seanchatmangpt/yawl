#!/usr/bin/env bash
set -euo pipefail
#
# Add @Execution annotations to test files missing them
#

# Get list of files missing annotations
files_missing=$(bash scripts/validation/lib/check-singleton-ann.sh --json | jq -r '.failed_files[]')

echo "Adding @Execution annotations to ${#files_missing[@]} files..."

for file in $files_missing; do
    echo "Processing: $file"

    # Check if file already has @Execution annotation
    if grep -q "@Execution" "$file"; then
        echo "  Already has @Execution annotation, skipping"
        continue
    fi

    # Add import and annotation
    temp_file=$(mktemp)

    # Find the line with the class declaration
    class_line=$(grep -n "^class\|^public class\|^protected class\|^private class" "$file" | head -1 | cut -d: -f1)

    if [[ -n "$class_line" ]]; then
        # Add imports before package statement if they exist, otherwise at the top
        if grep -q "^import.*junit" "$file"; then
            # Find last import line
            last_import=$(grep -n "^import" "$file" | tail -1 | cut -d: -f1)
            # Insert imports after last import
            head -n $((last_import + 1)) "$file" > "$temp_file"
            cat >> "$temp_file" << EOF
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
EOF
            tail -n +$((last_import + 2)) "$file" >> "$temp_file"
        else
            # Find package line and add imports after it
            package_line=$(grep -n "^package" "$file" | head -1 | cut -d: -f1)
            head -n $((package_line + 1)) "$file" > "$temp_file"
            cat >> "$temp_file" << EOF

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
EOF
            tail -n +$((package_line + 2)) "$file" >> "$temp_file"
        fi

        # Add annotation before class declaration
        class_line_after_imports=$((class_line + $(grep -c "^import" "$temp_file") + 1))

        # Split the file at the class declaration
        head -n $((class_line_after_imports - 1)) "$temp_file" > "${temp_file}.head"
        echo "    @Execution(ExecutionMode.SAME_THREAD)" >> "${temp_file}.head"
        echo "" >> "${temp_file}.head"
        tail -n +$class_line_after_imports "$temp_file" >> "${temp_file}.head"

        # Replace original file
        mv "${temp_file}.head" "$file"

        echo "  Added @Execution annotation"
    else
        echo "  ERROR: Could not find class declaration in $file"
    fi
done

echo "Done!"