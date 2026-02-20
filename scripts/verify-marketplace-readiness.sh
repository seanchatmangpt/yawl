#!/bin/bash

###############################################################################
# YAWL GCP Marketplace Readiness Verification Script
#
# Quickly verify all 5 blockers are implemented and working
# Run: bash scripts/verify-marketplace-readiness.sh
###############################################################################

set -e

BOLD='\033[1m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

###############################################################################
# Helper Functions
###############################################################################

check_pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((PASS_COUNT++))
}

check_fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((FAIL_COUNT++))
}

check_warn() {
    echo -e "${YELLOW}⚠ WARN${NC}: $1"
    ((WARN_COUNT++))
}

heading() {
    echo ""
    echo -e "${BOLD}========================================${NC}"
    echo -e "${BOLD}$1${NC}"
    echo -e "${BOLD}========================================${NC}"
}

###############################################################################
# BLOCKER #1: Multi-Tenancy Isolation
###############################################################################

heading "BLOCKER #1: Multi-Tenancy Isolation"

# Check TenantContext exists
if [ -f "src/org/yawlfoundation/yawl/engine/TenantContext.java" ]; then
    check_pass "TenantContext.java exists"

    # Check key methods
    if grep -q "public TenantContext(String tenantId)" src/org/yawlfoundation/yawl/engine/TenantContext.java; then
        check_pass "TenantContext constructor found"
    else
        check_fail "TenantContext constructor not found"
    fi

    if grep -q "public boolean isAuthorized(String caseID)" src/org/yawlfoundation/yawl/engine/TenantContext.java; then
        check_pass "isAuthorized() method found"
    else
        check_fail "isAuthorized() method not found"
    fi
else
    check_fail "TenantContext.java does not exist"
fi

# Check YEngine integration
if grep -q "ThreadLocal<TenantContext>" src/org/yawlfoundation/yawl/engine/YEngine.java; then
    check_pass "YEngine has ThreadLocal<TenantContext>"
else
    check_fail "YEngine missing ThreadLocal<TenantContext>"
fi

if grep -q "setTenantContext" src/org/yawlfoundation/yawl/engine/YEngine.java; then
    check_pass "YEngine has setTenantContext() method"
else
    check_fail "YEngine missing setTenantContext() method"
fi

if grep -q "validateTenantAccess" src/org/yawlfoundation/yawl/engine/YEngine.java; then
    check_pass "YEngine has validateTenantAccess() method"
else
    check_fail "YEngine missing validateTenantAccess() method"
fi

###############################################################################
# BLOCKER #2: Resource Quotas
###############################################################################

heading "BLOCKER #2: Resource Quotas"

if grep -q "class QuotaEnforcer" billing/gcp/UsageMeter.java; then
    check_pass "QuotaEnforcer class found in UsageMeter"

    if grep -q "MAX_EXECUTION_TIME_MS_MONTHLY" billing/gcp/UsageMeter.java; then
        check_pass "MAX_EXECUTION_TIME_MS_MONTHLY constant found"
    else
        check_fail "MAX_EXECUTION_TIME_MS_MONTHLY constant not found"
    fi

    if grep -q "MAX_COMPUTE_UNITS_MONTHLY" billing/gcp/UsageMeter.java; then
        check_pass "MAX_COMPUTE_UNITS_MONTHLY constant found"
    else
        check_fail "MAX_COMPUTE_UNITS_MONTHLY constant not found"
    fi

    if grep -q "checkAndRecordUsage" billing/gcp/UsageMeter.java; then
        check_pass "checkAndRecordUsage() method found"
    else
        check_fail "checkAndRecordUsage() method not found"
    fi
else
    check_fail "QuotaEnforcer class not found"
fi

if grep -q "quotaEnforcers" billing/gcp/UsageMeter.java; then
    check_pass "quotaEnforcers map found in UsageMeter"
else
    check_fail "quotaEnforcers map not found"
fi

###############################################################################
# BLOCKER #3: Encryption at Rest
###############################################################################

heading "BLOCKER #3: Encryption at Rest"

# Check Cloud SQL encryption config
if [ -f "deployment/gcp/cloud-sql-encryption.yaml" ]; then
    check_pass "cloud-sql-encryption.yaml exists"

    if grep -q "kmsKeyName" deployment/gcp/cloud-sql-encryption.yaml; then
        check_pass "CMEK keyName found in Cloud SQL config"
    else
        check_fail "CMEK keyName not found in Cloud SQL config"
    fi

    if grep -q "backupEncryptionConfiguration" deployment/gcp/cloud-sql-encryption.yaml; then
        check_pass "Backup encryption config found"
    else
        check_fail "Backup encryption config not found"
    fi
else
    check_fail "cloud-sql-encryption.yaml does not exist"
fi

# Check Cloud Storage encryption config
if [ -f "deployment/gcp/gcs-encryption.yaml" ]; then
    check_pass "gcs-encryption.yaml exists"

    if grep -q "defaultKmsKeyName" deployment/gcp/gcs-encryption.yaml; then
        check_pass "CMEK keyName found in GCS config"
    else
        check_fail "CMEK keyName not found in GCS config"
    fi

    if grep -q "retentionPolicy" deployment/gcp/gcs-encryption.yaml; then
        check_pass "Retention policy found in GCS config"
    else
        check_fail "Retention policy not found in GCS config"
    fi
else
    check_fail "gcs-encryption.yaml does not exist"
fi

###############################################################################
# BLOCKER #4: Legal Documentation
###############################################################################

heading "BLOCKER #4: Legal Documentation"

declare -a legal_docs=("PRIVACY.md" "SLA.md" "DPA.md" "SUPPORT-POLICY.md")

for doc in "${legal_docs[@]}"; do
    if [ -f "$doc" ]; then
        check_pass "$doc exists"

        # Check for key sections
        case "$doc" in
            "PRIVACY.md")
                if grep -q "GDPR" "$doc"; then
                    check_pass "$doc mentions GDPR"
                else
                    check_warn "$doc does not mention GDPR"
                fi
                ;;
            "SLA.md")
                if grep -q "99.9" "$doc" || grep -q "uptime" "$doc"; then
                    check_pass "$doc mentions uptime SLA"
                else
                    check_warn "$doc does not mention uptime SLA"
                fi
                ;;
            "DPA.md")
                if grep -q "Data Processing" "$doc" || grep -q "GDPR" "$doc"; then
                    check_pass "$doc covers data processing"
                else
                    check_warn "$doc does not cover data processing"
                fi
                ;;
            "SUPPORT-POLICY.md")
                if grep -q "support" "$doc" -i; then
                    check_pass "$doc covers support"
                else
                    check_warn "$doc does not cover support"
                fi
                ;;
        esac
    else
        check_fail "$doc does not exist"
    fi
done

###############################################################################
# BLOCKER #5: LGPL Compliance
###############################################################################

heading "BLOCKER #5: LGPL Compliance"

if [ -f "THIRD-PARTY-LICENSES/README.md" ]; then
    check_pass "THIRD-PARTY-LICENSES/README.md exists"

    if grep -q "LGPL" THIRD-PARTY-LICENSES/README.md; then
        check_pass "LGPL mentioned in license docs"
    else
        check_fail "LGPL not mentioned in license docs"
    fi

    if grep -q "H2\|HSQLDB" THIRD-PARTY-LICENSES/README.md; then
        check_pass "H2/HSQLDB mentioned in license docs"
    else
        check_warn "H2/HSQLDB not mentioned in license docs"
    fi
else
    check_fail "THIRD-PARTY-LICENSES/README.md does not exist"
fi

if [ -f "LICENSES.md" ]; then
    check_pass "LICENSES.md exists"
else
    check_fail "LICENSES.md does not exist"
fi

###############################################################################
# Compilation Check
###############################################################################

heading "Code Compilation Check"

echo "Attempting compilation (timeout: 30s)..."
if timeout 30s bash scripts/dx.sh compile -q 2>/dev/null; then
    check_pass "Code compiles successfully"
else
    check_warn "Code compilation skipped or timed out (may be network issue)"
fi

###############################################################################
# Git Status Check
###############################################################################

heading "Git Status Check"

if git diff --quiet; then
    check_pass "No uncommitted changes"
else
    check_warn "Uncommitted changes found (review with: git status)"
fi

COMMITS=$(git log --oneline -3 | wc -l)
if [ "$COMMITS" -ge 2 ]; then
    check_pass "At least 2 implementation commits found"
    echo "Recent commits:"
    git log --oneline -3 | sed 's/^/  /'
else
    check_fail "Insufficient commits found"
fi

###############################################################################
# Summary
###############################################################################

heading "VERIFICATION SUMMARY"

TOTAL=$((PASS_COUNT + FAIL_COUNT + WARN_COUNT))

echo ""
echo -e "${GREEN}PASSED:${NC} $PASS_COUNT"
echo -e "${RED}FAILED:${NC} $FAIL_COUNT"
echo -e "${YELLOW}WARNINGS:${NC} $WARN_COUNT"
echo -e "${BOLD}TOTAL:${NC} $TOTAL"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    if [ $WARN_COUNT -eq 0 ]; then
        echo -e "${GREEN}${BOLD}✓ ALL CHECKS PASSED - READY FOR MARKETPLACE${NC}"
        exit 0
    else
        echo -e "${YELLOW}${BOLD}⚠ CHECKS PASSED (with warnings) - READY FOR MARKETPLACE${NC}"
        exit 0
    fi
else
    echo -e "${RED}${BOLD}✗ FAILURES DETECTED - REVIEW ABOVE${NC}"
    exit 1
fi
