#!/usr/bin/env python3
"""
YAWL Java EE → Jakarta EE Migration Script
Migrates all javax.* APIs to jakarta.* equivalents across the entire codebase.

This script:
1. Finds all Java files with javax imports
2. Replaces javax.servlet → jakarta.servlet
3. Replaces javax.mail → jakarta.mail
4. Replaces javax.activation → jakarta.activation
5. Replaces javax.annotation → jakarta.annotation
6. Replaces javax.faces → jakarta.faces
7. Replaces javax.xml.bind → jakarta.xml.bind
8. Keeps javax.swing, javax.xml.*, javax.net.ssl, javax.imageio (Java SE)
9. Updates build configuration files
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Tuple, Set

# Migration mappings (Java EE → Jakarta EE)
JAVAX_TO_JAKARTA_MAPPINGS = {
    'javax.servlet': 'jakarta.servlet',
    'javax.mail': 'jakarta.mail',
    'javax.activation': 'jakarta.activation',
    'javax.annotation': 'jakarta.annotation',
    'javax.faces': 'jakarta.faces',
    'javax.xml.bind': 'jakarta.xml.bind',
    'javax.persistence': 'jakarta.persistence',
}

# Packages to KEEP as javax (Java SE, not Java EE)
JAVAX_KEEP_PATTERNS = [
    'javax.swing',
    'javax.xml.parsers',
    'javax.xml.transform',
    'javax.xml.validation',
    'javax.xml.datatype',
    'javax.xml.xpath',
    'javax.xml.soap',  # Legacy SOAP, keep for compatibility
    'javax.xml.namespace',
    'javax.xml.stream',
    'javax.xml.XMLConstants',
    'javax.net',
    'javax.imageio',
    'javax.crypto',
    'javax.security',
    'javax.naming',
    'javax.sql',
    'javax.management',
    'javax.rmi',
    'javax.sound',
    'javax.accessibility',
    'javax.print',
    'javax.tools',
    'javax.lang',
    'javax.script',
    'javax.wsdl',  # WSDL is not part of Jakarta EE migration
]


class JavaxToJakartaMigrator:
    """Migrates javax.* to jakarta.* across YAWL codebase."""

    def __init__(self, root_dir: str):
        self.root_dir = Path(root_dir)
        self.files_modified = 0
        self.total_replacements = 0
        self.dry_run = False

    def should_keep_javax(self, import_line: str) -> bool:
        """Check if this javax import should be kept (Java SE, not Java EE)."""
        for keep_pattern in JAVAX_KEEP_PATTERNS:
            if keep_pattern in import_line:
                return True
        return False

    def migrate_import_line(self, line: str) -> Tuple[str, bool]:
        """
        Migrate a single import line from javax to jakarta.
        Returns (modified_line, was_modified).
        """
        if 'import javax.' not in line:
            return (line, False)

        # Check if this is a Java SE javax that should be kept
        if self.should_keep_javax(line):
            return (line, False)

        original_line = line
        modified = False

        # Apply each mapping
        for javax_pkg, jakarta_pkg in JAVAX_TO_JAKARTA_MAPPINGS.items():
            if f'import {javax_pkg}' in line or f'import static {javax_pkg}' in line:
                line = line.replace(javax_pkg, jakarta_pkg)
                modified = True
                break

        return (line, modified)

    def migrate_file(self, file_path: Path) -> int:
        """
        Migrate a single Java file.
        Returns number of replacements made.
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except Exception as e:
            print(f"Error reading {file_path}: {e}")
            return 0

        modified_lines = []
        replacements = 0

        for line in lines:
            new_line, was_modified = self.migrate_import_line(line)
            modified_lines.append(new_line)
            if was_modified:
                replacements += 1
                print(f"  {file_path.name}: {line.strip()} → {new_line.strip()}")

        if replacements > 0 and not self.dry_run:
            try:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.writelines(modified_lines)
                self.files_modified += 1
            except Exception as e:
                print(f"Error writing {file_path}: {e}")
                return 0

        return replacements

    def find_java_files(self) -> List[Path]:
        """Find all Java source files."""
        java_files = []
        for pattern in ['src/**/*.java', 'test/**/*.java']:
            java_files.extend(self.root_dir.glob(pattern))
        return sorted(java_files)

    def migrate_build_xml(self) -> int:
        """Update build.xml to reference Jakarta JARs."""
        build_xml = self.root_dir / 'build' / 'build.xml'
        if not build_xml.exists():
            print(f"Warning: {build_xml} not found")
            return 0

        try:
            with open(build_xml, 'r', encoding='utf-8') as f:
                content = f.read()
        except Exception as e:
            print(f"Error reading {build_xml}: {e}")
            return 0

        original_content = content
        replacements = 0

        # Update JAR references
        jar_replacements = {
            'javax.activation-api-1.2.0.jar': 'jakarta.activation-1.2.2.jar',
            'jaxb-api-2.3.1.jar': 'jakarta.xml.bind-api-3.0.1.jar',
            # Note: servlet JARs typically provided by container
        }

        for old_jar, new_jar in jar_replacements.items():
            if old_jar in content:
                content = content.replace(old_jar, new_jar)
                replacements += 1
                print(f"  build.xml: {old_jar} → {new_jar}")

        if replacements > 0 and not self.dry_run:
            try:
                with open(build_xml, 'w', encoding='utf-8') as f:
                    f.write(content)
            except Exception as e:
                print(f"Error writing {build_xml}: {e}")
                return 0

        return replacements

    def migrate_pom_xml(self) -> int:
        """Update pom.xml to use Jakarta dependencies."""
        pom_xml = self.root_dir / 'pom.xml'
        if not pom_xml.exists():
            print(f"Warning: {pom_xml} not found")
            return 0

        try:
            with open(pom_xml, 'r', encoding='utf-8') as f:
                content = f.read()
        except Exception as e:
            print(f"Error reading {pom_xml}: {e}")
            return 0

        original_content = content
        replacements = 0

        # Add Jakarta dependencies if not already present
        jakarta_deps = [
            ('jakarta.servlet-api', '5.0.0', 'provided'),
            ('jakarta.mail', '2.1.0', 'compile'),
            ('jakarta.activation', '2.1.0', 'compile'),
            ('jakarta.annotation-api', '3.0.0', 'compile'),
            ('jakarta.xml.bind-api', '3.0.1', 'compile'),
        ]

        dependencies_section = content.find('<dependencies>')
        if dependencies_section == -1:
            print("Warning: Could not find <dependencies> section in pom.xml")
            return 0

        # Check if Jakarta dependencies are already present
        for artifact_id, version, scope in jakarta_deps:
            if artifact_id not in content:
                print(f"  pom.xml: Would add {artifact_id}:{version} (manually review)")
                replacements += 1

        # Note: Actual POM modification requires careful XML manipulation
        # This is a detection phase; actual changes should be made carefully

        return replacements

    def run(self, dry_run: bool = False) -> None:
        """Execute the migration."""
        self.dry_run = dry_run

        print("=" * 80)
        print("YAWL Java EE → Jakarta EE Migration")
        print("=" * 80)
        if dry_run:
            print("DRY RUN MODE: No files will be modified")
        print()

        # Find all Java files
        java_files = self.find_java_files()
        print(f"Found {len(java_files)} Java files to analyze")
        print()

        # Migrate Java source files
        print("Migrating Java source files...")
        print("-" * 80)
        for java_file in java_files:
            replacements = self.migrate_file(java_file)
            self.total_replacements += replacements

        print()
        print("Migrating build configuration files...")
        print("-" * 80)

        # Migrate build.xml
        build_replacements = self.migrate_build_xml()
        self.total_replacements += build_replacements

        # Check pom.xml
        pom_replacements = self.migrate_pom_xml()

        # Summary
        print()
        print("=" * 80)
        print("Migration Summary")
        print("=" * 80)
        print(f"Files analyzed:     {len(java_files)}")
        print(f"Files modified:     {self.files_modified}")
        print(f"Total replacements: {self.total_replacements}")

        if dry_run:
            print()
            print("This was a DRY RUN. Re-run without --dry-run to apply changes.")
        else:
            print()
            print("Migration complete!")
            print()
            print("Next steps:")
            print("1. Review changes with: git diff")
            print("2. Update web.xml servlet version (if exists)")
            print("3. Test JAXB serialization/deserialization")
            print("4. Test servlet request/response handling")
            print("5. Test mail functionality")
            print("6. Run full test suite")


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description='Migrate YAWL from Java EE (javax.*) to Jakarta EE (jakarta.*)'
    )
    parser.add_argument(
        '--root',
        default='/home/user/yawl',
        help='Root directory of YAWL codebase (default: /home/user/yawl)'
    )
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='Show what would be changed without modifying files'
    )

    args = parser.parse_args()

    if not os.path.exists(args.root):
        print(f"Error: Root directory not found: {args.root}")
        sys.exit(1)

    migrator = JavaxToJakartaMigrator(args.root)
    migrator.run(dry_run=args.dry_run)


if __name__ == '__main__':
    main()
