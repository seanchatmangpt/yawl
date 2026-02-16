#!/bin/bash
#
# YAWL Dependency Cleanup Script
#
# This script removes obsolete dependencies from build/3rdParty/lib/
# as part of the build system modernization.
#
# Date: 2026-02-15
# YAWL Version: 5.2
#
# Safety: Creates backup before deletion
# See: docs/BUILD_SYSTEM_MIGRATION_GUIDE.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="$PROJECT_ROOT/build/3rdParty/lib"
BACKUP_DIR="$PROJECT_ROOT/build/3rdParty/lib-backup-$(date +%Y%m%d-%H%M%S)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "YAWL Dependency Cleanup Script"
echo "=========================================="
echo ""

# Check if lib directory exists
if [ ! -d "$LIB_DIR" ]; then
    echo -e "${RED}Error: $LIB_DIR not found${NC}"
    exit 1
fi

# Create backup
echo -e "${YELLOW}Creating backup at: $BACKUP_DIR${NC}"
mkdir -p "$BACKUP_DIR"
cp -r "$LIB_DIR"/* "$BACKUP_DIR/"
echo -e "${GREEN}Backup created successfully${NC}"
echo ""

# Function to remove JAR if it exists
remove_jar() {
    local jar_name="$1"
    local reason="$2"

    if [ -f "$LIB_DIR/$jar_name" ]; then
        echo -e "${YELLOW}Removing: $jar_name${NC}"
        echo "  Reason: $reason"
        rm -f "$LIB_DIR/$jar_name"
        echo -e "${GREEN}  ✓ Removed${NC}"
        return 0
    else
        echo -e "Skipping: $jar_name (not found)"
        return 1
    fi
}

echo "=========================================="
echo "Removing Obsolete Dependencies"
echo "=========================================="
echo ""

# Counter for statistics
removed_count=0
total_checked=0

echo "--- SOAP/Web Services Libraries (Obsolete) ---"
echo ""

remove_jar "axis-1.1RC2.jar" "Legacy SOAP - not used in codebase" && ((removed_count++))
((total_checked++))

remove_jar "wsdl4j-20030807.jar" "SOAP dependency - replaced by modern HTTP" && ((removed_count++))
((total_checked++))

remove_jar "saaj.jar" "SOAP attachment API - not required" && ((removed_count++))
((total_checked++))

remove_jar "wsif.jar" "Web Services Invocation Framework - deprecated" && ((removed_count++))
((total_checked++))

remove_jar "jaxrpc.jar" "Legacy JAX-RPC - replaced by JAX-WS/REST" && ((removed_count++))
((total_checked++))

remove_jar "apache_soap-2_3_1.jar" "Ancient Apache SOAP library - not used" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Pre-Java 5 Concurrency (Obsolete) ---"
echo ""

remove_jar "concurrent-1.3.4.jar" "Pre-Java 5 - use java.util.concurrent" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Duplicate/Legacy Commons Libraries ---"
echo ""

remove_jar "commons-lang-2.3.jar" "Legacy - use commons-lang3-3.14.0" && ((removed_count++))
((total_checked++))

remove_jar "commons-lang-2.6.jar" "Legacy - use commons-lang3-3.14.0" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Duplicate OkHttp (Keep 4.x for Spring compatibility) ---"
echo ""

remove_jar "okhttp-5.2.1.jar" "Incompatible with Spring - use okhttp-4.12.0" && ((removed_count++))
((total_checked++))

remove_jar "okio-3.9.1.jar" "OkHttp 5.x dependency - not needed" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Duplicate JDOM (Keep v2 only) ---"
echo ""

remove_jar "jdom.jar" "Legacy JDOM v1 - use jdom2-2.0.6.1" && ((removed_count++))
((total_checked++))

remove_jar "jdom1-impl.jar" "Legacy JDOM v1 - use jdom2-2.0.6.1" && ((removed_count++))
((total_checked++))

remove_jar "saxon9-jdom.jar" "JDOM v1 adapter - use Saxon with jdom2" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Duplicate Jakarta CDI (Consolidate to 3.0.0) ---"
echo ""

remove_jar "jakarta.enterprise.cdi-api-2.0.2.jar" "Legacy - use jakarta.enterprise.cdi-api-3.0.0" && ((removed_count++))
((total_checked++))

echo ""
echo "--- Legacy Jakarta Mail (Consolidate to 2.1.0) ---"
echo ""

remove_jar "jakarta.mail-1.6.7.jar" "Legacy - use jakarta.mail-2.1.0 or eclipse-angus" && ((removed_count++))
((total_checked++))

echo ""
echo "=========================================="
echo "Cleanup Summary"
echo "=========================================="
echo ""
echo "Dependencies checked: $total_checked"
echo "Dependencies removed: $removed_count"
echo "Dependencies kept: $((total_checked - removed_count))"
echo ""
echo -e "${GREEN}Backup location: $BACKUP_DIR${NC}"
echo ""

if [ $removed_count -gt 0 ]; then
    echo -e "${YELLOW}Warning: Build may need to be updated to reference new library versions${NC}"
    echo "See: docs/BUILD_SYSTEM_MIGRATION_GUIDE.md"
    echo ""
    echo "To restore from backup:"
    echo "  cp -r $BACKUP_DIR/* $LIB_DIR/"
    echo ""
fi

echo "=========================================="
echo "Dependencies That Should Be Kept"
echo "=========================================="
echo ""
echo "✓ antlr-2.7.7.jar           - Required by Hibernate (transitive)"
echo "✓ twitter4j-core-2.1.8.jar  - Used by twitterService"
echo "✓ jung-*.jar (11 files)     - Used by Proclet Service for graphs"
echo "✓ commons-lang3-3.6.jar     - Modern commons-lang (upgrade to 3.14.0 via Maven)"
echo "✓ okhttp-4.12.0.jar         - Compatible with Spring Boot"
echo "✓ jdom2-2.0.5.jar           - Modern JDOM (upgrade to 2.0.6.1 via Maven)"
echo "✓ jakarta.enterprise.cdi-api-3.0.0.jar - Jakarta EE 10"
echo ""

echo "Next Steps:"
echo "1. Verify build still works: ant -f build/build.xml compile"
echo "2. Switch to Maven: mvn clean install"
echo "3. Update imports if using removed libraries"
echo "4. Run tests: mvn test"
echo ""
echo -e "${GREEN}Cleanup complete!${NC}"
