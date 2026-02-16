#!/usr/bin/env python3
"""
JUnit 3 to JUnit 5 Migration Script
Migrates test files from JUnit 3 (extends TestCase) to JUnit 5 (Jupiter) annotations.
"""

import re
import sys
from pathlib import Path


def migrate_test_file(file_path):
    """Migrate a single test file from JUnit 3 to JUnit 5."""

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Skip if already migrated (has @Test annotation)
    if '@Test' in content or 'org.junit.jupiter' in content:
        print(f"SKIP (already migrated): {file_path}")
        return False

    # Skip if doesn't extend TestCase
    if 'extends TestCase' not in content and 'extends TestSuite' not in content:
        print(f"SKIP (not JUnit 3): {file_path}")
        return False

    # 1. Update imports
    content = content.replace('import junit.framework.TestCase;', '')
    content = content.replace('import junit.framework.Test;', '')
    content = content.replace('import junit.framework.TestSuite;', '')
    content = content.replace('import junit.textui.TestRunner;', '')

    # Add JUnit 5 imports (insert after package declaration)
    junit5_imports = []

    # Check if we need specific imports
    if 'public void setUp()' in content or 'protected void setUp()' in content or 'void setUp()' in content:
        junit5_imports.append('import org.junit.jupiter.api.BeforeEach;')

    if 'public void tearDown()' in content or 'protected void tearDown()' in content or 'void tearDown()' in content:
        junit5_imports.append('import org.junit.jupiter.api.AfterEach;')

    if 'public void testSetUpBeforeClass()' in content or 'setUpBeforeClass' in content:
        junit5_imports.append('import org.junit.jupiter.api.BeforeAll;')

    if 'public void tearDownAfterClass()' in content or 'tearDownAfterClass' in content:
        junit5_imports.append('import org.junit.jupiter.api.AfterAll;')

    # Always add Test import if there are test methods
    if 'public void test' in content:
        junit5_imports.append('import org.junit.jupiter.api.Test;')

    # Add static imports for assertions
    assertions_needed = []
    if 'assertEquals(' in content:
        assertions_needed.append('assertEquals')
    if 'assertNotNull(' in content:
        assertions_needed.append('assertNotNull')
    if 'assertNull(' in content:
        assertions_needed.append('assertNull')
    if 'assertTrue(' in content:
        assertions_needed.append('assertTrue')
    if 'assertFalse(' in content:
        assertions_needed.append('assertFalse')
    if 'assertSame(' in content:
        assertions_needed.append('assertSame')
    if 'assertNotSame(' in content:
        assertions_needed.append('assertNotSame')
    if 'fail(' in content:
        assertions_needed.append('fail')

    if assertions_needed:
        junit5_imports.append(f"import static org.junit.jupiter.api.Assertions.*;")

    # Insert imports after package and before first import
    package_match = re.search(r'package\s+[\w.]+;\s*\n', content)
    if package_match and junit5_imports:
        insert_pos = package_match.end()
        # Find first import line
        first_import = re.search(r'\nimport\s+', content[insert_pos:])
        if first_import:
            insert_pos += first_import.start() + 1
            imports_text = '\n'.join(junit5_imports) + '\n'
            content = content[:insert_pos] + imports_text + content[insert_pos:]

    # 2. Remove TestCase/TestSuite extension and constructors
    content = re.sub(r'\s+extends\s+TestCase', '', content)
    content = re.sub(r'\s+extends\s+TestSuite', '', content)

    # Remove constructors (e.g., public TestYAtomicTask(String name) { super(name); })
    content = re.sub(
        r'\s*\/\*\*[^*]*\*+(?:[^/*][^*]*\*+)*\/\s*\n\s*public\s+Test\w+\(String\s+\w+\)\s*\{\s*super\(\w+\);\s*\}',
        '',
        content
    )
    content = re.sub(
        r'\n\s*public\s+Test\w+\(String\s+\w+\)\s*\{\s*super\(\w+\);\s*\}',
        '',
        content
    )

    # 3. Convert class declaration from 'public class' to just 'class'
    content = re.sub(r'\npublic class (Test\w+)', r'\nclass \1', content)

    # 4. Update setUp method
    content = re.sub(
        r'(\s+)public void setUp\(\)',
        r'\1@BeforeEach\n\1void setUp()',
        content
    )
    content = re.sub(
        r'(\s+)protected void setUp\(\)',
        r'\1@BeforeEach\n\1void setUp()',
        content
    )

    # 5. Update tearDown method
    content = re.sub(
        r'(\s+)public void tearDown\(\)',
        r'\1@AfterEach\n\1void tearDown()',
        content
    )
    content = re.sub(
        r'(\s+)protected void tearDown\(\)',
        r'\1@AfterEach\n\1void tearDown()',
        content
    )

    # 6. Update test methods (public void test* -> @Test void test*)
    content = re.sub(
        r'(\s+)public void (test\w+\([^)]*\))',
        r'\1@Test\n\1void \2',
        content
    )

    # 7. Update static setup/teardown
    content = re.sub(
        r'(\s+)public static void setUpBeforeClass\(\)',
        r'\1@BeforeAll\n\1static void setUpBeforeClass()',
        content
    )
    content = re.sub(
        r'(\s+)public static void tearDownAfterClass\(\)',
        r'\1@AfterAll\n\1static void tearDownAfterClass()',
        content
    )

    # 8. Remove suite() methods and main() methods (test suites handled differently in JUnit 5)
    # Remove multi-line suite method
    content = re.sub(
        r'\n\s*public static Test suite\(\)\s*\{[^}]*\n(?:\s*[^}]*\n)*\s*\}',
        '',
        content,
        flags=re.MULTILINE
    )

    # Remove main method
    content = re.sub(
        r'\n\s*public static void main\(String\s+\w+\[\]\)\s*\{[^}]*\n(?:\s*[^}]*\n)*\s*\}',
        '',
        content,
        flags=re.MULTILINE
    )

    # 9. Clean up extra blank lines
    content = re.sub(r'\n\n\n+', '\n\n', content)

    # Remove trailing whitespace
    content = re.sub(r' +\n', '\n', content)

    # Only write if content changed
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"MIGRATED: {file_path}")
        return True
    else:
        print(f"NO CHANGE: {file_path}")
        return False


def main():
    """Main migration function."""

    # Get test directory
    test_dir = Path('/home/user/yawl/test')

    if not test_dir.exists():
        print(f"ERROR: Test directory not found: {test_dir}")
        sys.exit(1)

    # Find all Test*.java files
    test_files = list(test_dir.rglob('Test*.java'))

    print(f"Found {len(test_files)} test files\n")

    migrated_count = 0
    for test_file in sorted(test_files):
        if migrate_test_file(test_file):
            migrated_count += 1

    print(f"\n{'='*60}")
    print(f"Migration complete: {migrated_count}/{len(test_files)} files migrated")
    print(f"{'='*60}")


if __name__ == '__main__':
    main()
