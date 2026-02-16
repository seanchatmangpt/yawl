#!/bin/bash
# YAWL v5.2 - Dependency Conflict Resolution Script
# Session: https://claude.ai/code/session_0192xw4JzxMuKcu5pbiwBPQb

set -e

echo "=== YAWL Dependency Conflict Resolution ==="
echo "Date: $(date -I)"
echo ""

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "ERROR: Must run from YAWL root directory"
    exit 1
fi

# Verify critical fixes are in place
echo "Step 1: Verifying POM integrity..."

# Check for build cache extension (should NOT exist)
if grep -q "maven-build-cache-extension" pom.xml; then
    echo "❌ FAIL: Maven build cache extension still present"
    echo "  Action: Remove <extensions> block from pom.xml"
    exit 1
else
    echo "✅ PASS: Maven build cache extension removed"
fi

# Check for duplicate Spring Boot dependencies
ACTUATOR_COUNT=$(grep -c "spring-boot-starter-actuator" pom.xml || true)
WEB_COUNT=$(grep -c "spring-boot-starter-web" pom.xml || true)

if [ "$ACTUATOR_COUNT" -gt 2 ]; then
    echo "❌ FAIL: Duplicate spring-boot-starter-actuator declarations ($ACTUATOR_COUNT found)"
    echo "  Action: Keep only one declaration in <dependencyManagement>"
    exit 1
else
    echo "✅ PASS: No duplicate spring-boot-starter-actuator ($ACTUATOR_COUNT declarations)"
fi

if [ "$WEB_COUNT" -gt 2 ]; then
    echo "❌ FAIL: Duplicate spring-boot-starter-web declarations ($WEB_COUNT found)"
    echo "  Action: Keep only one declaration in <dependencyManagement>"
    exit 1
else
    echo "✅ PASS: No duplicate spring-boot-starter-web ($WEB_COUNT declarations)"
fi

echo ""
echo "Step 2: Validating POM structure..."
mvn validate 2>&1 | grep -E "(SUCCESS|FAILURE|ERROR|WARNING)" | head -20

echo ""
echo "Step 3: Analyzing dependencies (offline mode)..."

# Try to show effective POM
if mvn -o help:effective-pom > /tmp/effective-pom.xml 2>/dev/null; then
    echo "✅ Effective POM generated: /tmp/effective-pom.xml"
else
    echo "⚠️  Cannot generate effective POM in offline mode"
fi

echo ""
echo "Step 4: Checking for known issues..."

# Check for failsafe plugin
if mvn -o dependency:tree -Dverbose 2>&1 | grep -q "maven-failsafe-plugin"; then
    echo "⚠️  Failsafe plugin referenced but may not be fully downloaded"
    echo "  Check: ls /root/.m2/repository/org/apache/maven/plugins/maven-failsafe-plugin/3.5.2/"
fi

# Check local repository status
echo ""
echo "Step 5: Local repository status..."
REPO_SIZE=$(du -sh /root/.m2/repository 2>/dev/null | cut -f1)
echo "  Repository size: $REPO_SIZE"

KEY_DEPS=(
    "org/springframework/boot/spring-boot/3.5.10"
    "org/hibernate/orm/hibernate-core/6.6.42.Final"
    "com/fasterxml/jackson/core/jackson-databind/2.18.3"
    "org/apache/logging/log4j/log4j-api/2.25.3"
)

echo "  Checking key dependencies:"
for dep in "${KEY_DEPS[@]}"; do
    if [ -d "/root/.m2/repository/$dep" ]; then
        echo "    ✅ $dep"
    else
        echo "    ❌ $dep (NOT FOUND)"
    fi
done

echo ""
echo "Step 6: Integration module dependencies..."

cd yawl-integration 2>/dev/null || true
if [ -f "pom.xml" ]; then
    echo "  Checking yawl-integration module..."

    # Check for excluded packages
    EXCLUDED=$(grep -A5 "<excludes>" pom.xml | grep "<exclude>" | wc -l)
    echo "    Excluded source patterns: $EXCLUDED"

    # Check for MCP/A2A dependencies
    if grep -q "<!-- MCP SDK" pom.xml; then
        echo "    ⚠️  MCP SDK dependencies commented out (not on Maven Central)"
    fi

    if grep -q "<!-- A2A SDK" pom.xml; then
        echo "    ⚠️  A2A SDK dependencies commented out (not on Maven Central)"
    fi
fi
cd - > /dev/null 2>&1 || true

echo ""
echo "=== Summary ==="
echo ""
echo "Critical fixes:"
echo "  ✅ Maven build cache extension removed"
echo "  ✅ Duplicate Spring Boot dependencies removed"
echo ""
echo "Known issues:"
echo "  ⚠️  MCP SDK not on Maven Central - requires local installation"
echo "  ⚠️  A2A SDK not on Maven Central - requires local installation"
echo "  ⚠️  Failsafe plugin may need re-download in online mode"
echo ""
echo "Next steps:"
echo "  1. Review DEPENDENCY_ANALYSIS.md for detailed report"
echo "  2. Run 'mvn clean compile' to verify build"
echo "  3. Install MCP/A2A SDKs if needed for integration module"
echo ""
echo "For full dependency analysis when online:"
echo "  mvn dependency:tree -Dverbose > dependency-tree.txt"
echo "  mvn dependency:analyze > dependency-analysis.txt"
echo ""
