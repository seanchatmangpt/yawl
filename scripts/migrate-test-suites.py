#!/usr/bin/env python3
"""
Migrate JUnit 3 TestSuite classes to JUnit 5 @Suite annotations.
"""

import re
import sys
from pathlib import Path


def extract_test_classes(content):
    """Extract test class names from suite() method."""
    classes = []

    # Find all addTestSuite calls
    pattern = r'suite\.addTestSuite\((\w+)\.class\)'
    matches = re.findall(pattern, content)
    classes.extend(matches)

    return classes


def migrate_suite_file(file_path):
    """Migrate a single TestSuite file to JUnit 5."""

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Skip if already migrated
    if '@Suite' in content:
        print(f"SKIP (already migrated): {file_path}")
        return False

    # Skip if no TestSuite
    if 'TestSuite' not in file_path.name:
        print(f"SKIP (not a suite): {file_path}")
        return False

    # Extract package name
    package_match = re.search(r'package\s+([\w.]+);', content)
    if not package_match:
        print(f"ERROR: No package found in {file_path}")
        return False

    package_name = package_match.group(1)

    # Extract class name
    class_match = re.search(r'public class (\w+)', content)
    if not class_match:
        print(f"ERROR: No class found in {file_path}")
        return False

    class_name = class_match.group(1)

    # Extract test classes from suite() method
    test_classes = extract_test_classes(content)

    if not test_classes:
        print(f"WARNING: No test classes found in {file_path}")
        # Create empty suite anyway
        test_classes = []

    # Extract javadoc comment if exists
    javadoc = ""
    javadoc_match = re.search(r'(/\*\*.*?\*/)', content, re.DOTALL)
    if javadoc_match:
        javadoc = javadoc_match.group(1)
        # Clean up javadoc
        javadoc = javadoc.strip()

    # Build new content
    new_content = f"""package {package_name};

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

{javadoc}
"""

    if test_classes:
        classes_str = ',\n    '.join([f"{cls}.class" for cls in test_classes])
        new_content += f"""@Suite
@SelectClasses({{
    {classes_str}
}})
public class {class_name} {{
}}
"""
    else:
        new_content += f"""@Suite
public class {class_name} {{
}}
"""

    # Write new content
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)

    print(f"MIGRATED: {file_path} ({len(test_classes)} test classes)")
    return True


def main():
    """Main migration function."""

    test_dir = Path('/home/user/yawl/test')

    if not test_dir.exists():
        print(f"ERROR: Test directory not found: {test_dir}")
        sys.exit(1)

    # Find all TestSuite files
    suite_files = list(test_dir.rglob('*TestSuite.java'))

    print(f"Found {len(suite_files)} test suite files\n")

    migrated_count = 0
    for suite_file in sorted(suite_files):
        if migrate_suite_file(suite_file):
            migrated_count += 1

    print(f"\n{'='*60}")
    print(f"Migration complete: {migrated_count}/{len(suite_files)} suites migrated")
    print(f"{'='*60}")


if __name__ == '__main__':
    main()
