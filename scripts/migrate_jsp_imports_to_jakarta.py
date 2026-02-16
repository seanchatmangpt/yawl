#!/usr/bin/env python3
"""
Migrate JSP import statements from javax to jakarta.
Updates JAXB and XML stream imports in JSP files.
"""

import os
import sys
import re
from pathlib import Path

# Import mappings: old -> new
IMPORT_MAPPINGS = {
    'javax.xml.bind': 'jakarta.xml.bind',
    'javax.xml.stream': 'jakarta.xml.stream',
}

def migrate_file(file_path):
    """Migrate a single JSP file's imports to Jakarta."""
    try:
        with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
            content = f.read()

        original_content = content
        changes_made = []

        # Replace each import
        for old_import, new_import in IMPORT_MAPPINGS.items():
            if old_import in content:
                content = content.replace(old_import, new_import)
                changes_made.append(f"  {old_import} -> {new_import}")

        # Only write if changes were made
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)

            print(f"✓ Updated: {file_path}")
            for change in changes_made:
                print(change)
            return True
        else:
            return False

    except Exception as e:
        print(f"✗ Error processing {file_path}: {e}", file=sys.stderr)
        return False

def find_files(root_dir, extensions):
    """Find all files with given extensions under root_dir."""
    files = []
    for ext in extensions:
        for file_path in Path(root_dir).rglob(f"*.{ext}"):
            files.append(str(file_path))
    return sorted(files)

def main():
    root_dir = '/home/user/yawl'

    # Find all JSP files
    extensions = ['jsp']
    files = find_files(root_dir, extensions)

    print(f"Found {len(files)} JSP files to process\n")

    updated_count = 0
    for file_path in files:
        if migrate_file(file_path):
            updated_count += 1

    print(f"\n{'='*60}")
    print(f"Import migration complete: {updated_count}/{len(files)} files updated")
    print(f"{'='*60}")

    return 0

if __name__ == '__main__':
    sys.exit(main())
