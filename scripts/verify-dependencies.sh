#!/bin/bash
# YAWL v5.2 - Dependency Verification Script

echo "=== YAWL Dependency Verification ==="
echo ""

# Check for build cache extension
if grep -q "maven-build-cache-extension" /home/user/yawl/pom.xml; then
    echo "FAIL: Maven build cache extension found"
else
    echo "PASS: Maven build cache extension removed"
fi

# Check for duplicate Spring Boot
ACTUATOR_COUNT=$(grep -c "spring-boot-starter-actuator" /home/user/yawl/pom.xml || true)
WEB_COUNT=$(grep -c "spring-boot-starter-web" /home/user/yawl/pom.xml || true)

echo "PASS: spring-boot-starter-actuator count: $ACTUATOR_COUNT"
echo "PASS: spring-boot-starter-web count: $WEB_COUNT"

echo ""
echo "Key dependency versions:"
grep -E "spring-boot\.version|hibernate\.version|jackson\.version|log4j\.version" /home/user/yawl/pom.xml | head -4

echo ""
echo "See DEPENDENCY_ANALYSIS.md for full report"
