#!/bin/bash
set -e

echo "=== YAWL v5.2 Staging Environment Validation ==="
date

FAILED=0
PASSED=0

# Test 1: Kubernetes cluster connectivity
echo -e "\n[TEST 1] Kubernetes Cluster Connectivity"
if kubectl cluster-info &>/dev/null; then
    echo "✅ PASS: Can reach Kubernetes API"
    ((PASSED++))
else
    echo "❌ FAIL: Cannot reach Kubernetes API"
    ((FAILED++))
fi

# Test 2: Database connectivity
echo -e "\n[TEST 2] Database Connectivity"
if pg_isready -h ${DB_HOST} -p ${DB_PORT} &>/dev/null; then
    echo "✅ PASS: PostgreSQL database reachable"
    ((PASSED++))
else
    echo "❌ FAIL: PostgreSQL database unreachable"
    ((FAILED++))
fi

# Test 3: Database schema exists
echo -e "\n[TEST 3] Database Schema Validation"
TABLES=$(psql -h ${DB_HOST} -U ${DB_USERNAME} -d ${DB_NAME} -c "\dt" 2>/dev/null | wc -l)
if [ $TABLES -gt 0 ]; then
    echo "✅ PASS: Database has schema ($TABLES tables)"
    ((PASSED++))
else
    echo "❌ FAIL: Database schema missing"
    ((FAILED++))
fi

# Test 4: Z.AI API connectivity
echo -e "\n[TEST 4] Z.AI API Connectivity"
curl -s -H "Authorization: Bearer ${ZAI_API_KEY}" \
    https://staging.z.ai.anthropic.com/health \
    -o /dev/null && echo "✅ PASS: Z.AI API reachable" && ((PASSED++)) || \
    { echo "❌ FAIL: Z.AI API unreachable"; ((FAILED++)); }

# Test 5: Secrets configured
echo -e "\n[TEST 5] Secrets in Kubernetes"
if kubectl get secret yawl-secrets -n staging &>/dev/null; then
    echo "✅ PASS: Secrets configured"
    ((PASSED++))
else
    echo "❌ FAIL: Secrets missing"
    ((FAILED++))
fi

# Test 6: Network policies applied
echo -e "\n[TEST 6] Network Policies"
POLICIES=$(kubectl get networkpolicy -n staging --no-headers 2>/dev/null | wc -l)
if [ $POLICIES -gt 0 ]; then
    echo "✅ PASS: Network policies applied ($POLICIES policies)"
    ((PASSED++))
else
    echo "❌ WARN: No network policies configured"
fi

# Test 7: SPIFFE/SPIRE running
echo -e "\n[TEST 7] SPIFFE/SPIRE Health"
if kubectl get pod -n spire-system | grep "spire-server\|spire-agent" | grep Running &>/dev/null; then
    echo "✅ PASS: SPIRE system healthy"
    ((PASSED++))
else
    echo "⚠️  WARN: SPIRE system not healthy (can be optional)"
fi

# Test 8: Monitoring stack
echo -e "\n[TEST 8] Monitoring Stack"
if kubectl get deployment -n monitoring | grep prometheus &>/dev/null; then
    echo "✅ PASS: Prometheus deployed"
    ((PASSED++))
else
    echo "⚠️  WARN: Prometheus not deployed"
fi

# Summary
echo -e "\n=== VALIDATION SUMMARY ==="
echo "Passed: $PASSED"
echo "Failed: $FAILED"

if [ $FAILED -eq 0 ]; then
    echo "✅ All critical tests passed!"
    exit 0
else
    echo "❌ Some tests failed. Fix and retry."
    exit 1
fi
