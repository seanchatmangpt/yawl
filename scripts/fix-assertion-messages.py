#!/usr/bin/env python3
"""
Fix JUnit 5 assertion message parameter order.
In JUnit 3/4: assertEquals(message, expected, actual)
In JUnit 5: assertEquals(expected, actual, message)
"""

import re
import sys
from pathlib import Path


def fix_assertion_messages(file_path):
    """Fix assertion message order in a single file."""

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Pattern for assertNotNull("message", value)
    content = re.sub(
        r'assertNotNull\(\s*"([^"]+)"\s*,\s*([^)]+)\)',
        r'assertNotNull(\2, "\1")',
        content
    )

    # Pattern for assertNull("message", value)
    content = re.sub(
        r'assertNull\(\s*"([^"]+)"\s*,\s*([^)]+)\)',
        r'assertNull(\2, "\1")',
        content
    )

    # Pattern for assertTrue("message", condition)
    content = re.sub(
        r'assertTrue\(\s*"([^"]+)"\s*,\s*([^)]+)\)',
        r'assertTrue(\2, "\1")',
        content
    )

    # Pattern for assertFalse("message", condition)
    content = re.sub(
        r'assertFalse\(\s*"([^"]+)"\s*,\s*([^)]+)\)',
        r'assertFalse(\2, "\1")',
        content
    )

    # Pattern for assertEquals("message", expected, actual) with 3 params
    # This is tricky - need to swap message to end
    content = re.sub(
        r'assertEquals\(\s*"([^"]+)"\s*,\s*([^,]+)\s*,\s*([^)]+)\)',
        r'assertEquals(\2, \3, "\1")',
        content
    )

    # Pattern for assertSame("message", expected, actual)
    content = re.sub(
        r'assertSame\(\s*"([^"]+)"\s*,\s*([^,]+)\s*,\s*([^)]+)\)',
        r'assertSame(\2, \3, "\1")',
        content
    )

    # Pattern for assertNotSame("message", expected, actual)
    content = re.sub(
        r'assertNotSame\(\s*"([^"]+)"\s*,\s*([^,]+)\s*,\s*([^)]+)\)',
        r'assertNotSame(\2, \3, "\1")',
        content
    )

    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True

    return False


def main():
    """Main function."""

    test_dir = Path('/home/user/yawl/test')

    if not test_dir.exists():
        print(f"ERROR: Test directory not found: {test_dir}")
        sys.exit(1)

    test_files = list(test_dir.rglob('*.java'))

    print(f"Processing {len(test_files)} test files\n")

    fixed_count = 0
    for test_file in sorted(test_files):
        if fix_assertion_messages(test_file):
            print(f"FIXED: {test_file}")
            fixed_count += 1

    print(f"\n{'='*60}")
    print(f"Fixed {fixed_count}/{len(test_files)} files")
    print(f"{'='*60}")


if __name__ == '__main__':
    main()
