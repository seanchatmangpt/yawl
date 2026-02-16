#!/bin/bash
#
# YAWL Java EE → Jakarta EE Migration - EXECUTION SCRIPT
# This script performs the actual migration of source code
#
# IMPORTANT: This script MODIFIES FILES. Ensure you have a backup or are in git.
#

set -e

YAWL_ROOT="/home/user/yawl"
cd "$YAWL_ROOT"

echo "================================================================================
YAWL Java EE → Jakarta EE Migration - EXECUTION
================================================================================"
echo ""
echo "This script will MODIFY source files to migrate from javax.* to jakarta.*"
echo ""
echo "Press CTRL+C to abort, or ENTER to continue..."
read

echo ""
echo "Starting migration..."
echo ""

# Function to replace imports in all Java files
migrate_package() {
    local from_pkg=$1
    local to_pkg=$2
    local description=$3

    echo "Migrating: $description"
    echo "  From: $from_pkg"
    echo "  To:   $to_pkg"

    # Find and replace in all .java files
    find src test -name "*.java" -type f 2>/dev/null | while read file; do
        # Check if file contains the old import
        if grep -q "import ${from_pkg}" "$file" 2>/dev/null; then
            # Perform replacement
            sed -i "s|import ${from_pkg}|import ${to_pkg}|g" "$file"
            sed -i "s|import static ${from_pkg}|import static ${to_pkg}|g" "$file"
            echo "    ✓ $file"
        fi
    done

    echo ""
}

# Phase 1: javax.servlet → jakarta.servlet
echo "================================================================================"
echo "Phase 1: Servlet API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.servlet\." "jakarta.servlet." "Servlet API"

# Phase 2: javax.mail → jakarta.mail
echo "================================================================================"
echo "Phase 2: Mail API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.mail\." "jakarta.mail." "Mail API"

# Phase 3: javax.activation → jakarta.activation
echo "================================================================================"
echo "Phase 3: Activation API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.activation\." "jakarta.activation." "Activation API"

# Phase 4: javax.annotation → jakarta.annotation
echo "================================================================================"
echo "Phase 4: Annotation API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.annotation\." "jakarta.annotation." "Annotation API"

# Phase 5: javax.faces → jakarta.faces
echo "================================================================================"
echo "Phase 5: JSF API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.faces\." "jakarta.faces." "JavaServer Faces API"

# Phase 6: javax.xml.bind → jakarta.xml.bind (JAXB only, not all javax.xml)
echo "================================================================================"
echo "Phase 6: JAXB API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.xml\.bind\." "jakarta.xml.bind." "JAXB API"

# Phase 7: javax.persistence → jakarta.persistence (for Hibernate)
echo "================================================================================"
echo "Phase 7: JPA/Persistence API Migration"
echo "================================================================================"
echo ""
migrate_package "javax\.persistence\." "jakarta.persistence." "JPA/Persistence API"

# Phase 8: Verification
echo "================================================================================"
echo "Phase 8: Verification"
echo "================================================================================"
echo ""

echo "Checking for remaining Java EE imports..."
REMAINING=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep "^import javax\." 2>/dev/null | \
    grep -v "javax.swing" | \
    grep -v "javax.xml.parsers" | \
    grep -v "javax.xml.transform" | \
    grep -v "javax.xml.validation" | \
    grep -v "javax.xml.datatype" | \
    grep -v "javax.xml.xpath" | \
    grep -v "javax.xml.namespace" | \
    grep -v "javax.xml.stream" | \
    grep -v "javax.xml.soap" | \
    grep -v "javax.xml.XMLConstants" | \
    grep -v "javax.net" | \
    grep -v "javax.imageio" | \
    grep -v "javax.crypto" | \
    grep -v "javax.security" | \
    grep -v "javax.sql" | \
    grep -v "javax.naming" | \
    grep -v "javax.management" | \
    grep -v "javax.wsdl" | \
    wc -l)

if [[ $REMAINING -gt 0 ]]; then
    echo "⚠ WARNING: Found $REMAINING remaining Java EE imports"
    echo ""
    echo "These should be reviewed manually:"
    find src test -name "*.java" -type f 2>/dev/null | xargs grep "^import javax\." 2>/dev/null | \
        grep -v "javax.swing" | \
        grep -v "javax.xml.parsers" | \
        grep -v "javax.xml.transform" | \
        grep -v "javax.xml.validation" | \
        grep -v "javax.xml.datatype" | \
        grep -v "javax.xml.xpath" | \
        grep -v "javax.xml.namespace" | \
        grep -v "javax.xml.stream" | \
        grep -v "javax.xml.soap" | \
        grep -v "javax.xml.XMLConstants" | \
        grep -v "javax.net" | \
        grep -v "javax.imageio" | \
        grep -v "javax.crypto" | \
        grep -v "javax.security" | \
        grep -v "javax.sql" | \
        grep -v "javax.naming" | \
        grep -v "javax.management" | \
        grep -v "javax.wsdl" | \
        head -20
else
    echo "✓ No remaining Java EE imports found"
fi
echo ""

# Count jakarta imports
JAKARTA_COUNT=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep "^import jakarta\." 2>/dev/null | wc -l)
echo "✓ Total Jakarta imports: $JAKARTA_COUNT"
echo ""

# Count modified files
MODIFIED_FILES=$(git diff --name-only | grep "\.java$" | wc -l)
echo "✓ Modified files: $MODIFIED_FILES"
echo ""

echo "================================================================================"
echo "Migration Complete!"
echo "================================================================================"
echo ""
echo "Summary:"
echo "  - Modified $MODIFIED_FILES Java source files"
echo "  - Added $JAKARTA_COUNT Jakarta EE imports"
echo "  - Remaining javax EE imports: $REMAINING"
echo ""
echo "Next steps:"
echo "  1. Review changes:  git diff src/"
echo "  2. Review test changes:  git diff test/"
echo "  3. Check specific file:  git diff <filename>"
echo "  4. Test compilation:  mvn clean compile"
echo "  5. Run tests:  mvn test"
echo "  6. If satisfied, commit:  git commit -am 'Migrate to Jakarta EE'"
echo ""
echo "If you need to rollback:"
echo "  git checkout -- src/ test/"
echo ""
echo "================================================================================"
