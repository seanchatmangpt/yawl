#!/bin/bash
#
# YAWL Jakarta EE Migration - Status Verification
# Shows current state and what will be migrated
#

set -e

YAWL_ROOT="/home/user/yawl"
cd "$YAWL_ROOT"

echo "================================================================================"
echo "YAWL Jakarta EE Migration - Status Verification"
echo "================================================================================"
echo ""

# Count Java files
TOTAL_JAVA=$(find src test -name "*.java" 2>/dev/null | wc -l)
echo "Total Java files: $TOTAL_JAVA"
echo ""

echo "--------------------------------------------------------------------------------"
echo "Current Java EE (javax.*) Import Analysis"
echo "--------------------------------------------------------------------------------"
echo ""

# Function to count imports
count_imports() {
    local pattern=$1
    local description=$2
    local count=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -h "^import ${pattern}" 2>/dev/null | wc -l)
    printf "%-40s %5d imports\n" "$description:" "$count"
}

# Java EE APIs that will be migrated
echo "APIs to be migrated to jakarta.*:"
count_imports "javax\.servlet\." "  javax.servlet.*"
count_imports "javax\.mail\." "  javax.mail.*"
count_imports "javax\.activation\." "  javax.activation.*"
count_imports "javax\.annotation\." "  javax.annotation.*"
count_imports "javax\.faces\." "  javax.faces.*"
count_imports "javax\.xml\.bind\." "  javax.xml.bind.*"
count_imports "javax\.persistence\." "  javax.persistence.*"

echo ""
TOTAL_TO_MIGRATE=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -h "^import javax\." 2>/dev/null | \
    grep -E "(servlet|mail|activation|annotation|faces|xml\.bind|persistence)" | wc -l)
echo "Total imports to migrate: $TOTAL_TO_MIGRATE"

echo ""
echo "--------------------------------------------------------------------------------"
echo "Java SE APIs (javax.* - will NOT be migrated)"
echo "--------------------------------------------------------------------------------"
echo ""

# Java SE APIs that will stay as javax.*
count_imports "javax\.swing\." "  javax.swing.* (Java SE)"
count_imports "javax\.xml\.parsers\." "  javax.xml.parsers.* (Java SE)"
count_imports "javax\.xml\.transform\." "  javax.xml.transform.* (Java SE)"
count_imports "javax\.xml\.validation\." "  javax.xml.validation.* (Java SE)"
count_imports "javax\.xml\.datatype\." "  javax.xml.datatype.* (Java SE)"
count_imports "javax\.xml\.xpath\." "  javax.xml.xpath.* (Java SE)"
count_imports "javax\.xml\.namespace\." "  javax.xml.namespace.* (Java SE)"
count_imports "javax\.xml\.stream\." "  javax.xml.stream.* (Java SE)"
count_imports "javax\.xml\.soap\." "  javax.xml.soap.* (legacy SOAP)"
count_imports "javax\.xml\.XMLConstants" "  javax.xml.XMLConstants (Java SE)"
count_imports "javax\.net\." "  javax.net.* (Java SE)"
count_imports "javax\.imageio\." "  javax.imageio.* (Java SE)"
count_imports "javax\.crypto\." "  javax.crypto.* (Java SE)"
count_imports "javax\.security\." "  javax.security.* (Java SE)"
count_imports "javax\.sql\." "  javax.sql.* (Java SE)"
count_imports "javax\.naming\." "  javax.naming.* (Java SE)"
count_imports "javax\.management\." "  javax.management.* (Java SE)"
count_imports "javax\.wsdl\." "  javax.wsdl.* (WSDL4J)"

echo ""
TOTAL_KEEP=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -h "^import javax\." 2>/dev/null | \
    grep -v -E "(servlet|mail|activation|annotation|faces|xml\.bind|persistence)" | wc -l)
echo "Total javax.* imports to keep (Java SE): $TOTAL_KEEP"

echo ""
echo "--------------------------------------------------------------------------------"
echo "Current Jakarta EE Imports (if any)"
echo "--------------------------------------------------------------------------------"
echo ""

CURRENT_JAKARTA=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -h "^import jakarta\." 2>/dev/null | wc -l)
echo "Current jakarta.* imports: $CURRENT_JAKARTA"

if [ $CURRENT_JAKARTA -gt 0 ]; then
    echo ""
    echo "Existing jakarta.* imports found:"
    find src test -name "*.java" -type f 2>/dev/null | xargs grep -h "^import jakarta\." 2>/dev/null | \
        sed 's/import /  /' | sort -u | head -20
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "Files to be Modified"
echo "--------------------------------------------------------------------------------"
echo ""

echo "Files with javax.servlet imports:"
FILES_SERVLET=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.servlet\." 2>/dev/null | wc -l)
echo "  $FILES_SERVLET files"

echo "Files with javax.mail imports:"
FILES_MAIL=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.mail\." 2>/dev/null | wc -l)
echo "  $FILES_MAIL files"

echo "Files with javax.activation imports:"
FILES_ACTIVATION=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.activation\." 2>/dev/null | wc -l)
echo "  $FILES_ACTIVATION files"

echo "Files with javax.annotation imports:"
FILES_ANNOTATION=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.annotation\." 2>/dev/null | wc -l)
echo "  $FILES_ANNOTATION files"

echo "Files with javax.faces imports:"
FILES_FACES=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.faces\." 2>/dev/null | wc -l)
echo "  $FILES_FACES files"

echo "Files with javax.xml.bind imports:"
FILES_JAXB=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.xml\.bind\." 2>/dev/null | wc -l)
echo "  $FILES_JAXB files"

echo "Files with javax.persistence imports:"
FILES_PERSIST=$(find src test -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.persistence\." 2>/dev/null | wc -l)
echo "  $FILES_PERSIST files"

TOTAL_FILES=$((FILES_SERVLET + FILES_MAIL + FILES_ACTIVATION + FILES_ANNOTATION + FILES_FACES + FILES_JAXB + FILES_PERSIST))
echo ""
echo "Total files to be modified: $TOTAL_FILES"

echo ""
echo "--------------------------------------------------------------------------------"
echo "Dependency Status"
echo "--------------------------------------------------------------------------------"
echo ""

# Check pom.xml for Jakarta dependencies
if [ -f "pom.xml" ]; then
    echo "Checking pom.xml for Jakarta EE dependencies..."
    echo ""

    check_dependency() {
        local groupId=$1
        local artifactId=$2
        if grep -q "<groupId>${groupId}</groupId>" pom.xml && \
           grep -A1 "<groupId>${groupId}</groupId>" pom.xml | grep -q "<artifactId>${artifactId}</artifactId>"; then
            echo "  ✓ ${groupId}:${artifactId}"
        else
            echo "  ✗ ${groupId}:${artifactId} (missing)"
        fi
    }

    check_dependency "jakarta.servlet" "jakarta.servlet-api"
    check_dependency "jakarta.mail" "jakarta.mail-api"
    check_dependency "jakarta.activation" "jakarta.activation-api"
    check_dependency "jakarta.annotation" "jakarta.annotation-api"
    check_dependency "jakarta.faces" "jakarta.faces-api"
    check_dependency "jakarta.xml.bind" "jakarta.xml.bind-api"
    check_dependency "jakarta.persistence" "jakarta.persistence-api"
else
    echo "  ⚠ pom.xml not found"
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "Sample Files to be Modified"
echo "--------------------------------------------------------------------------------"
echo ""

echo "Servlet examples (first 5):"
find src -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.servlet\." 2>/dev/null | head -5 | \
    while read file; do
        echo "  - ${file#$YAWL_ROOT/}"
    done

echo ""
echo "Mail examples (first 3):"
find src -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.mail\." 2>/dev/null | head -3 | \
    while read file; do
        echo "  - ${file#$YAWL_ROOT/}"
    done

echo ""
echo "JSF examples (first 5):"
find src -name "*.java" -type f 2>/dev/null | xargs grep -l "^import javax\.faces\." 2>/dev/null | head -5 | \
    while read file; do
        echo "  - ${file#$YAWL_ROOT/}"
    done

echo ""
echo "================================================================================"
echo "Summary"
echo "================================================================================"
echo ""
echo "Total Java files:              $TOTAL_JAVA"
echo "Files to be modified:          $TOTAL_FILES"
echo "Imports to migrate:            $TOTAL_TO_MIGRATE"
echo "Imports to keep (Java SE):     $TOTAL_KEEP"
echo "Current jakarta.* imports:     $CURRENT_JAKARTA"
echo ""
echo "Ready for migration: YES"
echo ""
echo "To proceed with migration:"
echo "  chmod +x execute-jakarta-migration.sh"
echo "  ./execute-jakarta-migration.sh"
echo ""
echo "Or for dry-run preview:"
echo "  chmod +x migrate-jakarta.sh"
echo "  ./migrate-jakarta.sh --dry-run"
echo ""
echo "================================================================================"
