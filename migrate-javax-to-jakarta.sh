#!/bin/bash
################################################################################
# migrate-javax-to-jakarta.sh
# YAWL v5.2 - Migrate javax.* imports to jakarta.*
#
# Purpose: Automatically convert legacy javax.* imports to Jakarta EE 10
# Date: 2026-02-16
#
# IMPORTANT: This script does NOT migrate:
#   - javax.swing.* (Java SE, not Jakarta)
#   - javax.imageio.* (Java SE, not Jakarta)
#   - javax.net.ssl.* (Java SE, not Jakarta)
#   - javax.naming.* (Complex JNDI migration - needs manual review)
#   - javax.xml.XMLConstants (Java SE, not Jakarta)
#
################################################################################

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SRC_DIR="${1:-src}"
BACKUP_DIR="/tmp/yawl-javax-backup-$(date +%Y%m%d-%H%M%S)"
DRY_RUN="${DRY_RUN:-false}"

echo -e "${GREEN}=== YAWL javax → jakarta Migration ===${NC}"
echo "Source directory: $SRC_DIR"
echo "Backup directory: $BACKUP_DIR"
echo "Dry run: $DRY_RUN"
echo ""

# Validate source directory exists
if [ ! -d "$SRC_DIR" ]; then
    echo -e "${RED}ERROR: Source directory '$SRC_DIR' not found${NC}"
    exit 1
fi

# Create backup
echo -e "${YELLOW}Creating backup...${NC}"
mkdir -p "$BACKUP_DIR"
cp -r "$SRC_DIR" "$BACKUP_DIR/"
echo "Backup created at: $BACKUP_DIR"
echo ""

# Count files before migration
TOTAL_JAVA_FILES=$(find "$SRC_DIR" -name "*.java" | wc -l)
echo "Total Java files: $TOTAL_JAVA_FILES"

# Analyze javax.* imports
echo -e "${YELLOW}Analyzing javax.* imports...${NC}"
JAVAX_IMPORTS=$(grep -r "import javax\." "$SRC_DIR" --include="*.java" | wc -l)
echo "Total javax.* imports found: $JAVAX_IMPORTS"
echo ""

# Show breakdown by package
echo "Import breakdown:"
echo "  javax.persistence: $(grep -r "import javax\.persistence\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.xml.bind: $(grep -r "import javax\.xml\.bind\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.mail: $(grep -r "import javax\.mail\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.activation: $(grep -r "import javax\.activation\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.servlet: $(grep -r "import javax\.servlet\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.annotation: $(grep -r "import javax\.annotation\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.enterprise: $(grep -r "import javax\.enterprise\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.faces: $(grep -r "import javax\.faces\." "$SRC_DIR" --include="*.java" | wc -l)"
echo "  javax.ws.rs: $(grep -r "import javax\.ws\.rs\." "$SRC_DIR" --include="*.java" | wc -l)"
echo ""

# Packages that should NOT be migrated
echo -e "${YELLOW}Packages excluded from migration:${NC}"
echo "  javax.swing.* (Java SE - GUI)"
echo "  javax.imageio.* (Java SE - Image I/O)"
echo "  javax.net.ssl.* (Java SE - SSL/TLS)"
echo "  javax.naming.* (Java SE - JNDI, needs manual review)"
echo "  javax.xml.XMLConstants (Java SE - XML constants)"
echo "  javax.xml.soap.* (Needs manual review)"
echo ""

if [ "$DRY_RUN" = "true" ]; then
    echo -e "${YELLOW}DRY RUN MODE - No files will be modified${NC}"
    echo "To actually migrate, run: DRY_RUN=false ./migrate-javax-to-jakarta.sh"
    exit 0
fi

# Confirm migration
read -p "Proceed with migration? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Migration cancelled"
    exit 0
fi

echo -e "${GREEN}Starting migration...${NC}"
echo ""

# Migration function
migrate_package() {
    local old_package="$1"
    local new_package="$2"
    local description="$3"

    echo -n "  Migrating $description... "
    local count=$(find "$SRC_DIR" -name "*.java" -exec sed -i \
        "s/import ${old_package}\./import ${new_package}./g" {} \; \
        -exec grep -l "import ${new_package}\." {} \; 2>/dev/null | wc -l)
    echo -e "${GREEN}$count files${NC}"
}

# Perform migrations
echo "Package migrations:"

migrate_package "javax\.persistence" "jakarta.persistence" "JPA (Persistence)"
migrate_package "javax\.xml\.bind" "jakarta.xml.bind" "JAXB (XML Binding)"
migrate_package "javax\.mail" "jakarta.mail" "JavaMail"
migrate_package "javax\.activation" "jakarta.activation" "Activation Framework"
migrate_package "javax\.servlet" "jakarta.servlet" "Servlet API"
migrate_package "javax\.annotation" "jakarta.annotation" "Annotations"
migrate_package "javax\.enterprise" "jakarta.enterprise" "CDI (Enterprise)"
migrate_package "javax\.faces" "jakarta.faces" "JSF (Faces)"
migrate_package "javax\.ws\.rs" "jakarta.ws.rs" "JAX-RS (REST)"
migrate_package "javax\.transaction" "jakarta.transaction" "JTA (Transactions)"
migrate_package "javax\.validation" "jakarta.validation" "Bean Validation"
migrate_package "javax\.inject" "jakarta.inject" "Dependency Injection"
migrate_package "javax\.jms" "jakarta.jms" "JMS (Messaging)"
migrate_package "javax\.ejb" "jakarta.ejb" "EJB"
migrate_package "javax\.interceptor" "jakarta.interceptor" "Interceptors"

echo ""

# Verify migration
echo -e "${YELLOW}Verifying migration...${NC}"
REMAINING_JAVAX=$(grep -r "import javax\." "$SRC_DIR" --include="*.java" | \
    grep -v "javax.swing" | \
    grep -v "javax.imageio" | \
    grep -v "javax.net.ssl" | \
    grep -v "javax.naming" | \
    grep -v "javax.xml.XMLConstants" | \
    grep -v "javax.xml.soap" | \
    wc -l)

echo "Remaining javax.* imports (excluding Java SE): $REMAINING_JAVAX"

if [ "$REMAINING_JAVAX" -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}Files with remaining javax.* imports:${NC}"
    grep -r "import javax\." "$SRC_DIR" --include="*.java" | \
        grep -v "javax.swing" | \
        grep -v "javax.imageio" | \
        grep -v "javax.net.ssl" | \
        grep -v "javax.naming" | \
        grep -v "javax.xml.XMLConstants" | \
        grep -v "javax.xml.soap" | \
        cut -d: -f1 | sort -u | head -20

    if [ "$REMAINING_JAVAX" -gt 20 ]; then
        echo "  ... and $(($REMAINING_JAVAX - 20)) more"
    fi
    echo ""
    echo -e "${YELLOW}These may require manual review${NC}"
fi

# Count jakarta.* imports
JAKARTA_IMPORTS=$(grep -r "import jakarta\." "$SRC_DIR" --include="*.java" | wc -l)
echo "New jakarta.* imports: $JAKARTA_IMPORTS"
echo ""

# Summary
echo -e "${GREEN}=== Migration Summary ===${NC}"
echo "  Before: $JAVAX_IMPORTS javax.* imports"
echo "  After: $JAKARTA_IMPORTS jakarta.* imports"
echo "  Migrated: $(($JAVAX_IMPORTS - $REMAINING_JAVAX)) imports"
echo "  Remaining: $REMAINING_JAVAX javax.* imports (expected for Java SE packages)"
echo ""
echo "Backup location: $BACKUP_DIR"
echo ""

# Test compilation (optional)
read -p "Test compilation with Ant? (yes/no): " TEST_COMPILE
if [ "$TEST_COMPILE" = "yes" ]; then
    echo -e "${YELLOW}Testing compilation...${NC}"
    if ant -f build/build.xml compile; then
        echo -e "${GREEN}Compilation successful!${NC}"
    else
        echo -e "${RED}Compilation failed - check errors above${NC}"
        echo "To rollback, run: cp -r $BACKUP_DIR/src/* src/"
        exit 1
    fi
fi

echo -e "${GREEN}Migration complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Review remaining javax.* imports (if any)"
echo "  2. Update Hibernate JARs to 6.5.1"
echo "  3. Run: ant compile"
echo "  4. Run: ant unitTest"
echo "  5. Commit changes if successful"
