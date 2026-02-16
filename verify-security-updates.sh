#!/bin/bash
#
# YAWL Security Update Verification Script
# Phase 1 - Priority Updates (2026-02-15)
#
# This script verifies that all security updates were applied correctly.
#

set -e

YAWL_ROOT="/home/user/yawl"
LIB_DIR="${YAWL_ROOT}/build/3rdParty/lib"
SRC_DIR="${YAWL_ROOT}/src"

echo "=========================================="
echo "YAWL Security Update Verification"
echo "Phase 1 - Priority Updates"
echo "=========================================="
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# Function to check if file exists
check_jar() {
    local jar=$1
    if [ -f "${LIB_DIR}/${jar}" ]; then
        echo -e "${GREEN}✓${NC} Found: ${jar}"
        return 0
    else
        echo -e "${RED}✗${NC} Missing: ${jar}"
        ((ERRORS++))
        return 1
    fi
}

# Function to check if old file is removed
check_old_jar() {
    local jar=$1
    if [ ! -f "${LIB_DIR}/${jar}" ]; then
        echo -e "${GREEN}✓${NC} Removed: ${jar}"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} Old version still present: ${jar}"
        ((WARNINGS++))
        return 1
    fi
}

# Function to check for deprecated imports
check_imports() {
    local pattern=$1
    local description=$2
    local count=$(find "${SRC_DIR}" -name "*.java" -type f -exec grep -l "${pattern}" {} + 2>/dev/null | wc -l)

    if [ "$count" -eq 0 ]; then
        echo -e "${GREEN}✓${NC} No deprecated imports: ${description}"
        return 0
    else
        echo -e "${RED}✗${NC} Found ${count} files with deprecated imports: ${description}"
        ((ERRORS++))
        return 1
    fi
}

echo "1. Checking Log4j2 Updates..."
echo "------------------------------"
check_jar "log4j-api-2.24.1.jar"
check_jar "log4j-core-2.24.1.jar"
check_jar "log4j-slf4j2-impl-2.24.1.jar"
check_old_jar "log4j-api-2.18.0.jar"
check_old_jar "log4j-core-2.18.0.jar"
check_old_jar "log4j-1.2-api-2.17.1.jar"
check_old_jar "log4j-slf4j-impl-2.17.1.jar"
echo ""

echo "2. Checking SLF4J Updates..."
echo "-----------------------------"
check_jar "slf4j-api-2.0.13.jar"
check_old_jar "slf4j-api-1.7.12.jar"
echo ""

echo "3. Checking Database Driver Updates..."
echo "---------------------------------------"
check_jar "mysql-connector-j-8.0.33.jar"
check_jar "postgresql-42.7.2.jar"
check_jar "h2-2.2.224.jar"
check_old_jar "mysql-connector-java-5.1.22-bin.jar"
check_old_jar "postgresql-42.2.8.jar"
check_old_jar "h2-1.3.176.jar"
echo ""

echo "4. Checking Apache Commons Updates..."
echo "--------------------------------------"
check_jar "commons-lang3-3.14.0.jar"
check_jar "commons-text-1.11.0.jar"
check_jar "commons-codec-1.16.0.jar"
check_jar "commons-collections4-4.4.jar"
check_jar "commons-dbcp2-2.10.0.jar"
check_jar "commons-pool2-2.12.0.jar"
check_jar "commons-io-2.15.1.jar"
check_jar "commons-fileupload-1.5.jar"
check_jar "commons-vfs2-2.9.0.jar"
check_old_jar "commons-lang-2.3.jar"
check_old_jar "commons-lang3-3.6.jar"
check_old_jar "commons-codec-1.9.jar"
check_old_jar "commons-collections-3.2.1.jar"
check_old_jar "commons-dbcp-1.3.jar"
check_old_jar "commons-pool-1.5.4.jar"
check_old_jar "commons-io-2.0.1.jar"
check_old_jar "commons-fileupload-1.2.2.jar"
check_old_jar "commons-vfs2-2.1.jar"
echo ""

echo "5. Checking Java Source Code Updates..."
echo "----------------------------------------"
check_imports "import org\.apache\.commons\.lang\." "commons-lang (should be lang3)"
echo ""

echo "6. Checking MySQL Driver Configuration..."
echo "------------------------------------------"
if grep "^hibernate.connection.driver_class" "${YAWL_ROOT}/build/properties/hibernate.properties.mysql" | grep -q "com.mysql.cj.jdbc.Driver"; then
    echo -e "${GREEN}✓${NC} MySQL driver class updated (com.mysql.cj.jdbc.Driver)"
elif grep "^hibernate.connection.driver_class" "${YAWL_ROOT}/build/properties/hibernate.properties.mysql" | grep -q "com.mysql.jdbc.Driver"; then
    echo -e "${RED}✗${NC} Old MySQL driver class found (com.mysql.jdbc.Driver)"
    ((ERRORS++))
else
    echo -e "${YELLOW}⚠${NC} MySQL driver class configuration not found"
    ((WARNINGS++))
fi
echo ""

echo "7. Checking Build Configuration..."
echo "-----------------------------------"
if grep -q "log4j-api-2.24.1" "${YAWL_ROOT}/build/build.xml"; then
    echo -e "${GREEN}✓${NC} build.xml updated with Log4j 2.24.1"
else
    echo -e "${RED}✗${NC} build.xml not updated for Log4j"
    ((ERRORS++))
fi

if grep -q "mysql-connector-j-8.0.33" "${YAWL_ROOT}/build/build.xml"; then
    echo -e "${GREEN}✓${NC} build.xml updated with MySQL 8.0.33"
else
    echo -e "${RED}✗${NC} build.xml not updated for MySQL"
    ((ERRORS++))
fi

if grep -q "commons-lang3-3.14.0" "${YAWL_ROOT}/build/build.xml"; then
    echo -e "${GREEN}✓${NC} build.xml updated with commons-lang3 3.14.0"
else
    echo -e "${RED}✗${NC} build.xml not updated for commons-lang3"
    ((ERRORS++))
fi
echo ""

echo "8. Summary..."
echo "-------------"
TOTAL_JARS=$(ls -1 "${LIB_DIR}" | wc -l)
echo "Total JAR files: ${TOTAL_JARS}"
echo ""

if [ $ERRORS -eq 0 ]; then
    if [ $WARNINGS -eq 0 ]; then
        echo -e "${GREEN}=========================================="
        echo "✓ ALL CHECKS PASSED"
        echo "==========================================${NC}"
        echo ""
        echo "Security updates successfully applied!"
        echo ""
        echo "Next steps:"
        echo "1. Run: ant clean"
        echo "2. Run: ant buildAll"
        echo "3. Run: ant unitTest"
        echo "4. Review: SECURITY_MIGRATION_GUIDE.md"
        exit 0
    else
        echo -e "${YELLOW}=========================================="
        echo "⚠ WARNINGS DETECTED: ${WARNINGS}"
        echo "==========================================${NC}"
        echo ""
        echo "Some old JAR files are still present."
        echo "Review the warnings above and remove old JARs if safe."
        exit 0
    fi
else
    echo -e "${RED}=========================================="
    echo "✗ ERRORS DETECTED: ${ERRORS}"
    if [ $WARNINGS -gt 0 ]; then
        echo "⚠ WARNINGS: ${WARNINGS}"
    fi
    echo "==========================================${NC}"
    echo ""
    echo "Please fix the errors above before proceeding."
    echo "See SECURITY_MIGRATION_GUIDE.md for details."
    exit 1
fi
