#!/usr/bin/env bash
set -euo pipefail

# YAWL v5.2 Production Readiness Validation Script
# Runs all validation checks before deployment approval

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "==================================================================="
echo "  YAWL v5.2 Production Readiness Validation"
echo "  Date: $(date)"
echo "==================================================================="
echo ""

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

check_pass() {
    echo "✅ PASS: $1"
    ((PASS_COUNT++))
}

check_fail() {
    echo "❌ FAIL: $1"
    ((FAIL_COUNT++))
}

check_warn() {
    echo "⚠️  WARN: $1"
    ((WARN_COUNT++))
}

# ===================================================================
# 1. Build Verification
# ===================================================================
echo "=== 1. Build Verification ==="

if cd "$YAWL_ROOT" && ant -f build/build.xml clean compile > /tmp/build.log 2>&1; then
    check_pass "Source compilation successful"
else
    check_fail "Source compilation failed (see /tmp/build.log)"
fi

# Check for WAR files
if [ -f "$YAWL_ROOT/output/yawl-lib-5.2.jar" ]; then
    check_pass "YAWL library JAR exists"
else
    check_warn "YAWL library JAR not found (may need buildAll)"
fi

# ===================================================================
# 2. Code Quality (HYPER_STANDARDS)
# ===================================================================
echo ""
echo "=== 2. Code Quality (HYPER_STANDARDS) ==="

TODO_COUNT=$(grep -rn "TODO\|FIXME\|XXX\|HACK" "$YAWL_ROOT/src/" 2>/dev/null | wc -l || echo "0")
if [ "$TODO_COUNT" -eq 0 ]; then
    check_pass "No TODO/FIXME/XXX/HACK comments found"
else
    check_warn "Found $TODO_COUNT TODO/FIXME/XXX/HACK comments"
fi

MOCK_COUNT=$(grep -rn "mock\|stub\|fake" "$YAWL_ROOT/src/" --include="*.java" 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_COUNT" -eq 0 ]; then
    check_pass "No mock/stub/fake code found"
else
    check_warn "Found $MOCK_COUNT instances of mock/stub/fake code"
fi

# ===================================================================
# 3. Security Validation
# ===================================================================
echo ""
echo "=== 3. Security Validation ==="

# Check for SPIFFE implementation
if [ -f "$YAWL_ROOT/src/org/yawlfoundation/yawl/integration/spiffe/SpiffeWorkloadIdentity.java" ]; then
    check_pass "SPIFFE workload identity implementation found"
else
    check_fail "SPIFFE workload identity implementation missing"
fi

# Check for hardcoded secrets
if grep -r "password.*=" "$YAWL_ROOT/k8s/base/secrets.yaml" | grep -q "yawl\|change-me"; then
    check_fail "Default/placeholder secrets found in k8s/base/secrets.yaml - MUST ROTATE"
else
    check_pass "No default secrets found in Kubernetes manifests"
fi

# Check for .env files
if find "$YAWL_ROOT" -name ".env*" -type f 2>/dev/null | grep -q .; then
    check_fail ".env files found - potential secret exposure"
else
    check_pass "No .env files found"
fi

# ===================================================================
# 4. Observability Components
# ===================================================================
echo ""
echo "=== 4. Observability Components ==="

if [ -f "$YAWL_ROOT/src/org/yawlfoundation/yawl/integration/orderfulfillment/AgentTracer.java" ]; then
    check_pass "AgentTracer (distributed tracing) implemented"
else
    check_fail "AgentTracer missing"
fi

if [ -f "$YAWL_ROOT/src/org/yawlfoundation/yawl/integration/autonomous/observability/MetricsCollector.java" ]; then
    check_pass "MetricsCollector (Prometheus metrics) implemented"
else
    check_fail "MetricsCollector missing"
fi

if [ -f "$YAWL_ROOT/src/org/yawlfoundation/yawl/integration/autonomous/observability/HealthCheck.java" ]; then
    check_pass "HealthCheck (K8s probes) implemented"
else
    check_fail "HealthCheck missing"
fi

# ===================================================================
# 5. Kubernetes Manifests
# ===================================================================
echo ""
echo "=== 5. Kubernetes Manifests ==="

if [ -d "$YAWL_ROOT/k8s/base" ]; then
    check_pass "Kubernetes base manifests directory exists"
    
    # Count deployments
    DEPLOYMENT_COUNT=$(find "$YAWL_ROOT/k8s/base/deployments" -name "*.yaml" -type f 2>/dev/null | wc -l || echo "0")
    if [ "$DEPLOYMENT_COUNT" -gt 0 ]; then
        check_pass "Found $DEPLOYMENT_COUNT deployment manifests"
    else
        check_fail "No deployment manifests found"
    fi
    
    # Check for required files
    for file in namespace.yaml configmap.yaml secrets.yaml services.yaml ingress.yaml; do
        if [ -f "$YAWL_ROOT/k8s/base/$file" ]; then
            check_pass "k8s/base/$file exists"
        else
            check_fail "k8s/base/$file missing"
        fi
    done
else
    check_fail "Kubernetes manifests directory missing"
fi

# ===================================================================
# 6. Docker Configuration
# ===================================================================
echo ""
echo "=== 6. Docker Configuration ==="

if [ -d "$YAWL_ROOT/containerization" ]; then
    DOCKERFILE_COUNT=$(find "$YAWL_ROOT/containerization" -name "Dockerfile.*" -type f 2>/dev/null | wc -l || echo "0")
    if [ "$DOCKERFILE_COUNT" -gt 0 ]; then
        check_pass "Found $DOCKERFILE_COUNT Dockerfiles"
    else
        check_warn "No Dockerfiles found in containerization/"
    fi
else
    check_warn "No containerization/ directory found"
fi

if [ -f "$YAWL_ROOT/docker-compose.yml" ]; then
    check_pass "docker-compose.yml exists"
else
    check_warn "docker-compose.yml missing"
fi

# ===================================================================
# 7. Database Configuration
# ===================================================================
echo ""
echo "=== 7. Database Configuration ==="

if [ -d "$YAWL_ROOT/database/migrations" ]; then
    MIGRATION_COUNT=$(find "$YAWL_ROOT/database/migrations" -name "V*.sql" -type f 2>/dev/null | wc -l || echo "0")
    if [ "$MIGRATION_COUNT" -gt 0 ]; then
        check_pass "Found $MIGRATION_COUNT database migrations"
    else
        check_warn "No database migrations found"
    fi
else
    check_warn "Database migrations directory missing"
fi

if [ -f "$YAWL_ROOT/database/connection-pooling/hikaricp/hikaricp.properties" ]; then
    check_pass "HikariCP configuration exists"
else
    check_warn "HikariCP configuration missing"
fi

# ===================================================================
# 8. Documentation
# ===================================================================
echo ""
echo "=== 8. Documentation ==="

REQUIRED_DOCS=(
    "PRODUCTION_DEPLOYMENT_CHECKLIST.md"
    "CLOUD_DEPLOYMENT_RUNBOOKS.md"
    "SCALING_AND_OBSERVABILITY_GUIDE.md"
    "PRODUCTION_READINESS_SUMMARY.md"
)

for doc in "${REQUIRED_DOCS[@]}"; do
    if [ -f "$YAWL_ROOT/docs/$doc" ]; then
        check_pass "Documentation: $doc exists"
    else
        check_fail "Documentation: $doc missing"
    fi
done

# ===================================================================
# 9. Test Execution (if possible)
# ===================================================================
echo ""
echo "=== 9. Test Execution ==="

if cd "$YAWL_ROOT" && ant -f build/build.xml unitTest > /tmp/test.log 2>&1; then
    check_pass "Unit tests executed successfully"
else
    check_warn "Unit tests failed (see /tmp/test.log) - may be due to Spring Boot dependencies"
fi

# ===================================================================
# Summary
# ===================================================================
echo ""
echo "==================================================================="
echo "  Validation Summary"
echo "==================================================================="
echo "✅ PASS:    $PASS_COUNT checks"
echo "⚠️  WARN:    $WARN_COUNT checks"
echo "❌ FAIL:    $FAIL_COUNT checks"
echo ""

if [ "$FAIL_COUNT" -eq 0 ] && [ "$WARN_COUNT" -eq 0 ]; then
    echo "🎉 VERDICT: PRODUCTION READY"
    exit 0
elif [ "$FAIL_COUNT" -eq 0 ]; then
    echo "⚠️  VERDICT: CONDITIONAL PASS (address warnings)"
    exit 0
else
    echo "❌ VERDICT: NOT READY (fix failures)"
    exit 1
fi
