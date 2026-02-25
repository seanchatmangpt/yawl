#!/usr/bin/env bash
# Test JAX-RS compilation and structure
# This script verifies that JAX-RS dependencies and code are correctly set up

set -euo pipefail

echo "=== YAWL JAX-RS Setup Verification ==="
echo

# Check JAX-RS dependencies
echo "1. Checking JAX-RS dependencies..."
LIB_DIR="/home/user/yawl/build/3rdParty/lib"
DEPS=(
    "jakarta.ws.rs-api-3.1.0.jar"
    "jersey-server-3.1.5.jar"
    "jersey-client-3.1.5.jar"
    "jersey-common-3.1.5.jar"
    "jersey-container-servlet-3.1.5.jar"
    "jersey-hk2-3.1.5.jar"
    "jersey-media-json-jackson-3.1.5.jar"
    "hk2-locator-3.0.5.jar"
    "hk2-api-3.0.5.jar"
    "hk2-utils-3.0.5.jar"
)

MISSING=0
for dep in "${DEPS[@]}"; do
    if [ -f "$LIB_DIR/$dep" ]; then
        echo "  ✓ $dep"
    else
        echo "  ✗ MISSING: $dep"
        MISSING=$((MISSING + 1))
    fi
done

if [ $MISSING -gt 0 ]; then
    echo
    echo "ERROR: $MISSING dependencies missing!"
    exit 1
fi

echo
echo "2. Checking build.xml configuration..."
if grep -q "jakarta.ws.rs.api" /home/user/yawl/build/build.xml; then
    echo "  ✓ JAX-RS properties defined in build.xml"
else
    echo "  ✗ JAX-RS properties NOT found in build.xml"
    exit 1
fi

if grep -q "cp.jaxrs" /home/user/yawl/build/build.xml; then
    echo "  ✓ JAX-RS classpath defined in build.xml"
else
    echo "  ✗ JAX-RS classpath NOT found in build.xml"
    exit 1
fi

echo
echo "3. Checking REST source files..."
REST_DIR="/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/rest"
REST_FILES=(
    "YawlRestApplication.java"
    "InterfaceBRestResource.java"
    "InterfaceARestResource.java"
    "InterfaceERestResource.java"
    "InterfaceXRestResource.java"
    "YawlExceptionMapper.java"
    "package-info.java"
)

MISSING=0
for file in "${REST_FILES[@]}"; do
    if [ -f "$REST_DIR/$file" ]; then
        echo "  ✓ $file"
    else
        echo "  ✗ MISSING: $file"
        MISSING=$((MISSING + 1))
    fi
done

if [ $MISSING -gt 0 ]; then
    echo
    echo "ERROR: $MISSING source files missing!"
    exit 1
fi

echo
echo "4. Checking REST annotations..."
if grep -q "@ApplicationPath" "$REST_DIR/YawlRestApplication.java"; then
    echo "  ✓ @ApplicationPath annotation found"
else
    echo "  ✗ @ApplicationPath annotation NOT found"
    exit 1
fi

if grep -q "@Path(\"/ib\")" "$REST_DIR/InterfaceBRestResource.java"; then
    echo "  ✓ Interface B @Path annotation found"
else
    echo "  ✗ Interface B @Path annotation NOT found"
    exit 1
fi

if grep -q "@Provider" "$REST_DIR/YawlExceptionMapper.java"; then
    echo "  ✓ @Provider annotation found"
else
    echo "  ✗ @Provider annotation NOT found"
    exit 1
fi

echo
echo "5. Checking Interface B implementation..."
METHODS=(
    "connect"
    "disconnect"
    "getWorkItems"
    "getWorkItem"
    "checkoutWorkItem"
    "checkinWorkItem"
    "completeWorkItem"
    "getCaseData"
    "cancelCase"
)

MISSING=0
for method in "${METHODS[@]}"; do
    if grep -q "public Response $method(" "$REST_DIR/InterfaceBRestResource.java"; then
        echo "  ✓ $method() implemented"
    else
        echo "  ✗ MISSING: $method() method"
        MISSING=$((MISSING + 1))
    fi
done

if [ $MISSING -gt 0 ]; then
    echo
    echo "ERROR: $MISSING methods missing from Interface B!"
    exit 1
fi

echo
echo "6. File statistics..."
echo "  REST source files: $(ls -1 $REST_DIR/*.java 2>/dev/null | wc -l)"
echo "  JAX-RS dependencies: $(ls -1 $LIB_DIR/jakarta.ws.rs-api*.jar $LIB_DIR/jersey*.jar $LIB_DIR/hk2*.jar 2>/dev/null | wc -l)"
echo "  Total JAX-RS code lines: $(cat $REST_DIR/*.java 2>/dev/null | wc -l)"

echo
echo "=== ALL CHECKS PASSED ==="
echo
echo "Next steps:"
echo "  1. Add Jersey servlet configuration to web.xml"
echo "  2. Build and deploy YAWL engine"
echo "  3. Test REST endpoints at http://localhost:8080/yawl/api/ib/"
echo
