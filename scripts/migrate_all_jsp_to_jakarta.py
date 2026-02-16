#!/usr/bin/env python3
"""
Migrate ALL JSP/XHTML files in the entire YAWL codebase from Java EE to Jakarta EE 10.
Updates JSF and JSTL namespace URIs to Jakarta standards.
"""

import os
import sys
import re
from pathlib import Path

# Namespace mappings: old -> new
NAMESPACE_MAPPINGS = {
    # JSF namespaces
    'http://java.sun.com/jsf/core': 'jakarta.faces.core',
    'http://java.sun.com/jsf/html': 'jakarta.faces.html',
    'http://java.sun.com/jsf/facelets': 'jakarta.faces.facelets',
    'http://xmlns.jcp.org/jsf/core': 'jakarta.faces.core',
    'http://xmlns.jcp.org/jsf/html': 'jakarta.faces.html',
    'http://xmlns.jcp.org/jsf/facelets': 'jakarta.faces.facelets',

    # JSTL namespaces
    'http://java.sun.com/jsp/jstl/core': 'jakarta.tags.core',
    'http://java.sun.com/jsp/jstl/fmt': 'jakarta.tags.fmt',
    'http://java.sun.com/jsp/jstl/functions': 'jakarta.tags.functions',
    'http://java.sun.com/jsp/jstl/xml': 'jakarta.tags.xml',
    'http://java.sun.com/jsp/jstl/sql': 'jakarta.tags.sql',
}

def migrate_file(file_path):
    """Migrate a single JSP/XHTML/JSPF file to Jakarta namespaces."""
    try:
        with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
            content = f.read()

        original_content = content
        changes_made = []

        # Replace each namespace
        for old_ns, new_ns in NAMESPACE_MAPPINGS.items():
            if old_ns in content:
                content = content.replace(old_ns, new_ns)
                changes_made.append(f"  {old_ns} -> {new_ns}")

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

    # Find all JSP, XHTML, and JSPF files
    extensions = ['jsp', 'xhtml', 'jspf']
    files = find_files(root_dir, extensions)

    print(f"Found {len(files)} files to process\n")

    updated_count = 0
    for file_path in files:
        if migrate_file(file_path):
            updated_count += 1

    print(f"\n{'='*60}")
    print(f"Migration complete: {updated_count}/{len(files)} files updated")
    print(f"{'='*60}")

    return 0

if __name__ == '__main__':
    sys.exit(main())
