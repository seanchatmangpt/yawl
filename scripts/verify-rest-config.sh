#!/bin/bash
#
# Verify REST API Configuration
# Tests that all necessary files and configurations are in place
#

set -e

YAWL_HOME="/home/user/yawl"
PASS=0
FAIL=0

echo "========================================"
echo "YAWL REST API Configuration Verification"
echo "========================================"
echo ""

# Function to check file exists
check_file() {
    local file="$1"
    local desc="$2"

    if [ -f "$file" ]; then
        echo "[PASS] $desc"
        echo "       $file"
        ((PASS++))
    else
        echo "[FAIL] $desc"
        echo "       $file (NOT FOUND)"
        ((FAIL++))
    fi
}

# Function to check directory exists
check_dir() {
    local dir="$1"
    local desc="$2"

    if [ -d "$dir" ]; then
        echo "[PASS] $desc"
        echo "       $dir"
        ((PASS++))
    else
        echo "[FAIL] $desc"
        echo "       $dir (NOT FOUND)"
        ((FAIL++))
    fi
}

# Function to check file contains string
check_contains() {
    local file="$1"
    local pattern="$2"
    local desc="$3"

    if [ -f "$file" ] && grep -q "$pattern" "$file"; then
        echo "[PASS] $desc"
        ((PASS++))
    else
        echo "[FAIL] $desc"
        echo "       Pattern not found: $pattern"
        ((FAIL++))
    fi
}

echo "1. Checking REST Resource Classes"
echo "----------------------------------"
check_dir "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest" "REST package directory"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/YawlRestApplication.java" "YawlRestApplication class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceBRestResource.java" "InterfaceBRestResource class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceARestResource.java" "InterfaceARestResource class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceERestResource.java" "InterfaceERestResource class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceXRestResource.java" "InterfaceXRestResource class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/YawlExceptionMapper.java" "YawlExceptionMapper class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/CorsFilter.java" "CorsFilter class"
check_file "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/package-info.java" "Package documentation"
echo ""

echo "2. Checking web.xml Configuration"
echo "----------------------------------"
check_file "$YAWL_HOME/build/engine/web.xml" "Engine web.xml file"
check_contains "$YAWL_HOME/build/engine/web.xml" "JerseyServlet" "Jersey servlet definition"
check_contains "$YAWL_HOME/build/engine/web.xml" "org.glassfish.jersey.servlet.ServletContainer" "Jersey servlet class"
check_contains "$YAWL_HOME/build/engine/web.xml" "YawlRestApplication" "REST application reference"
check_contains "$YAWL_HOME/build/engine/web.xml" "/api/\*" "REST API URL mapping"
check_contains "$YAWL_HOME/build/engine/web.xml" "CorsFilter" "CORS filter definition"
check_contains "$YAWL_HOME/build/engine/web.xml" "org.yawlfoundation.yawl.engine.interfce.rest.CorsFilter" "CORS filter class"
echo ""

echo "3. Checking Documentation"
echo "-------------------------"
check_file "$YAWL_HOME/docs/REST-API-JAX-RS.md" "REST API documentation"
check_file "$YAWL_HOME/docs/REST-API-Configuration.md" "Configuration guide"
check_file "$YAWL_HOME/build/engine/api-docs.html" "API documentation HTML"
echo ""

echo "4. Checking Dependencies"
echo "------------------------"
check_file "$YAWL_HOME/build/3rdParty/lib/jakarta.servlet-api-6.0.0.jar" "Jakarta Servlet API"
check_file "$YAWL_HOME/build/3rdParty/lib/jakarta.ws.rs-api-3.1.0.jar" "JAX-RS API"
check_file "$YAWL_HOME/build/3rdParty/lib/jersey-container-servlet-3.1.5.jar" "Jersey Container"
echo ""

echo "5. XML Validation"
echo "-----------------"
if command -v xmllint &> /dev/null; then
    if xmllint --noout "$YAWL_HOME/build/engine/web.xml" 2>&1; then
        echo "[PASS] web.xml is well-formed XML"
        ((PASS++))
    else
        echo "[FAIL] web.xml has XML syntax errors"
        ((FAIL++))
    fi
else
    echo "[SKIP] xmllint not available (cannot validate XML)"
fi
echo ""

echo "6. Java Compilation Check"
echo "--------------------------"
if command -v javac &> /dev/null; then
    SERVLET_JAR="$YAWL_HOME/build/3rdParty/lib/jakarta.servlet-api-6.0.0.jar"
    LOG4J_JAR="$YAWL_HOME/build/3rdParty/lib/log4j-api-2.23.1.jar"

    if [ -f "$SERVLET_JAR" ] && [ -f "$LOG4J_JAR" ]; then
        TMP_DIR=$(mktemp -d)
        if javac -classpath "$SERVLET_JAR:$LOG4J_JAR" \
                -d "$TMP_DIR" \
                "$YAWL_HOME/src/org/yawlfoundation/yawl/engine/interfce/rest/CorsFilter.java" 2>&1; then
            echo "[PASS] CorsFilter compiles successfully"
            ((PASS++))
            rm -rf "$TMP_DIR"
        else
            echo "[FAIL] CorsFilter compilation failed"
            ((FAIL++))
        fi
    else
        echo "[SKIP] Required JARs not found for compilation test"
    fi
else
    echo "[SKIP] javac not available (cannot test compilation)"
fi
echo ""

echo "========================================"
echo "Summary"
echo "========================================"
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [ $FAIL -eq 0 ]; then
    echo "✓ All checks passed! REST API configuration is complete."
    echo ""
    echo "Next steps:"
    echo "  1. Build: ant -f build/build.xml buildAll"
    echo "  2. Deploy: cp output/yawl.war \$CATALINA_HOME/webapps/"
    echo "  3. Test: curl http://localhost:8080/yawl/api-docs.html"
    exit 0
else
    echo "✗ Some checks failed. Please review the errors above."
    exit 1
fi
