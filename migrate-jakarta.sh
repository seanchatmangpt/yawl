#!/bin/bash
#
# YAWL Java EE → Jakarta EE Migration Script
# Migrates all javax.* APIs to jakarta.* equivalents
#
# Usage:
#   ./migrate-jakarta.sh          # Execute migration
#   ./migrate-jakarta.sh --dry-run # Preview changes only
#

set -e

YAWL_ROOT="/home/user/yawl"
DRY_RUN=0

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=1
    echo -e "${YELLOW}DRY RUN MODE: No files will be modified${NC}"
fi

echo "================================================================================"
echo "YAWL Java EE → Jakarta EE Migration"
echo "================================================================================"
echo ""

cd "$YAWL_ROOT"

# Count files before migration
TOTAL_JAVA_FILES=$(find src test -name "*.java" 2>/dev/null | wc -l)
echo -e "${BLUE}Found ${TOTAL_JAVA_FILES} Java files${NC}"
echo ""

# Function to perform replacement
replace_in_files() {
    local pattern=$1
    local replacement=$2
    local description=$3

    echo -e "${GREEN}Migrating: ${description}${NC}"
    echo "  Pattern: $pattern → $replacement"

    if [[ $DRY_RUN -eq 1 ]]; then
        local count=$(grep -r "import ${pattern}" src/ test/ 2>/dev/null | wc -l)
        echo "  Would replace ${count} occurrences"
    else
        # Use find + sed for cross-platform compatibility
        find src test -name "*.java" -type f -exec sed -i \
            "s|import ${pattern}|import ${replacement}|g" {} + 2>/dev/null || true
        find src test -name "*.java" -type f -exec sed -i \
            "s|import static ${pattern}|import static ${replacement}|g" {} + 2>/dev/null || true

        local count=$(grep -r "import ${replacement}" src/ test/ 2>/dev/null | wc -l)
        echo "  ✓ Migrated ${count} imports"
    fi
    echo ""
}

echo "--------------------------------------------------------------------------------"
echo "Phase 1: Servlet API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.servlet\." "jakarta.servlet." "Servlet API (jakarta.servlet.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 2: Mail API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.mail\." "jakarta.mail." "Mail API (jakarta.mail.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 3: Activation API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.activation\." "jakarta.activation." "Activation API (jakarta.activation.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 4: Annotation API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.annotation\." "jakarta.annotation." "Annotation API (jakarta.annotation.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 5: JSF API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.faces\." "jakarta.faces." "JavaServer Faces API (jakarta.faces.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 6: JAXB API Migration"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.xml\.bind\." "jakarta.xml.bind." "JAXB API (jakarta.xml.bind.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 7: Persistence API Migration (Hibernate)"
echo "--------------------------------------------------------------------------------"
echo ""

replace_in_files "javax\.persistence\." "jakarta.persistence." "JPA API (jakarta.persistence.*)"

echo "--------------------------------------------------------------------------------"
echo "Phase 8: Build Configuration Updates"
echo "--------------------------------------------------------------------------------"
echo ""

# Update build.xml
BUILD_XML="build/build.xml"
if [[ -f "$BUILD_XML" ]]; then
    echo -e "${GREEN}Updating build.xml${NC}"
    if [[ $DRY_RUN -eq 0 ]]; then
        # Update JAR references
        sed -i 's/javax\.activation-api-1\.2\.0\.jar/jakarta.activation-1.2.2.jar/g' "$BUILD_XML"
        sed -i 's/jaxb-api-2\.3\.1\.jar/jakarta.xml.bind-api-3.0.1.jar/g' "$BUILD_XML"
        echo "  ✓ Updated JAR references in build.xml"
    else
        echo "  Would update JAR references in build.xml"
    fi
else
    echo "  ⚠ build.xml not found"
fi
echo ""

# Update pom.xml (add Jakarta dependencies)
POM_XML="pom.xml"
if [[ -f "$POM_XML" ]]; then
    echo -e "${GREEN}Checking pom.xml for Jakarta dependencies${NC}"

    # Check if Jakarta servlet is present
    if grep -q "jakarta.servlet-api" "$POM_XML"; then
        echo "  ✓ jakarta.servlet-api already present"
    else
        echo "  ⚠ jakarta.servlet-api not found - manual addition required"
    fi

    # Check if Jakarta mail is present
    if grep -q "jakarta.mail" "$POM_XML"; then
        echo "  ✓ jakarta.mail already present"
    else
        echo "  ⚠ jakarta.mail not found - manual addition required"
    fi

    # Check if Jakarta activation is present
    if grep -q "jakarta.activation" "$POM_XML"; then
        echo "  ✓ jakarta.activation already present"
    else
        echo "  ⚠ jakarta.activation not found - manual addition required"
    fi
else
    echo "  ⚠ pom.xml not found"
fi
echo ""

echo "--------------------------------------------------------------------------------"
echo "Phase 9: Verification"
echo "--------------------------------------------------------------------------------"
echo ""

# Check for remaining Java EE imports (excluding Java SE)
echo "Checking for remaining Java EE imports..."
REMAINING=$(grep -r "import javax\." src/ test/ 2>/dev/null | \
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
    echo -e "${YELLOW}⚠ Found ${REMAINING} remaining Java EE imports${NC}"
    echo "  Review these manually:"
    grep -r "import javax\." src/ test/ 2>/dev/null | \
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
    echo -e "${GREEN}✓ No remaining Java EE imports found${NC}"
fi
echo ""

# Count Jakarta imports
JAKARTA_COUNT=$(grep -r "import jakarta\." src/ test/ 2>/dev/null | wc -l)
echo -e "${GREEN}✓ Found ${JAKARTA_COUNT} Jakarta imports${NC}"
echo ""

echo "================================================================================"
echo "Migration Summary"
echo "================================================================================"
echo ""
echo "Files analyzed:     ${TOTAL_JAVA_FILES}"
echo "Jakarta imports:    ${JAKARTA_COUNT}"
echo "Remaining javax EE: ${REMAINING}"
echo ""

if [[ $DRY_RUN -eq 1 ]]; then
    echo -e "${YELLOW}This was a DRY RUN. No files were modified.${NC}"
    echo "Run without --dry-run to apply changes."
else
    echo -e "${GREEN}✓ Migration complete!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Review changes:     git diff"
    echo "  2. Compile:            cd /home/user/yawl && ant clean compile"
    echo "  3. Run tests:          ant unitTest"
    echo "  4. Update web.xml servlet version to 5.0"
    echo "  5. Deploy to Tomcat 10+"
fi
echo ""
echo "================================================================================"
