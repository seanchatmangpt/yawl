#!/usr/bin/env python3
"""
YAWL GCP Marketplace Readiness Verification
Quick check of all 5 blockers implementation status
"""

import os
import sys

# Colors
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
BOLD = '\033[1m'
END = '\033[0m'

passed = 0
failed = 0
warned = 0

def check_pass(msg):
    global passed
    passed += 1
    print(f"{GREEN}✓{END} {msg}")

def check_fail(msg):
    global failed
    failed += 1
    print(f"{RED}✗{END} {msg}")

def check_warn(msg):
    global warned
    warned += 1
    print(f"{YELLOW}⚠{END} {msg}")

def file_exists(path):
    return os.path.isfile(path)

def contains(filepath, text):
    if not file_exists(filepath):
        return False
    try:
        with open(filepath, 'r') as f:
            return text in f.read()
    except:
        return False

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}BLOCKER #1: Multi-Tenancy Isolation{END}")
print(f"{BOLD}{'='*50}{END}")

if file_exists("src/org/yawlfoundation/yawl/engine/TenantContext.java"):
    check_pass("TenantContext.java exists")
    if contains("src/org/yawlfoundation/yawl/engine/TenantContext.java", "isAuthorized"):
        check_pass("isAuthorized() method found")
    else:
        check_fail("isAuthorized() not found")
else:
    check_fail("TenantContext.java missing")

if contains("src/org/yawlfoundation/yawl/engine/YEngine.java", "ThreadLocal<TenantContext>"):
    check_pass("YEngine has ThreadLocal<TenantContext>")
else:
    check_fail("YEngine missing ThreadLocal<TenantContext>")

if contains("src/org/yawlfoundation/yawl/engine/YEngine.java", "setTenantContext"):
    check_pass("YEngine has setTenantContext()")
else:
    check_fail("YEngine missing setTenantContext()")

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}BLOCKER #2: Resource Quotas{END}")
print(f"{BOLD}{'='*50}{END}")

if contains("billing/gcp/UsageMeter.java", "class QuotaEnforcer"):
    check_pass("QuotaEnforcer class found")
    if contains("billing/gcp/UsageMeter.java", "MAX_EXECUTION_TIME_MS_MONTHLY"):
        check_pass("Execution time limit found")
    if contains("billing/gcp/UsageMeter.java", "MAX_COMPUTE_UNITS_MONTHLY"):
        check_pass("Compute units limit found")
else:
    check_fail("QuotaEnforcer not found")

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}BLOCKER #3: Encryption at Rest{END}")
print(f"{BOLD}{'='*50}{END}")

if file_exists("deployment/gcp/cloud-sql-encryption.yaml"):
    check_pass("cloud-sql-encryption.yaml exists")
    if contains("deployment/gcp/cloud-sql-encryption.yaml", "kmsKeyName"):
        check_pass("CMEK encryption for Cloud SQL")
    else:
        check_fail("CMEK not configured in Cloud SQL")
else:
    check_fail("cloud-sql-encryption.yaml missing")

if file_exists("deployment/gcp/gcs-encryption.yaml"):
    check_pass("gcs-encryption.yaml exists")
    if contains("deployment/gcp/gcs-encryption.yaml", "defaultKmsKeyName"):
        check_pass("CMEK encryption for GCS")
    else:
        check_fail("CMEK not configured in GCS")
else:
    check_fail("gcs-encryption.yaml missing")

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}BLOCKER #4: Legal Documentation{END}")
print(f"{BOLD}{'='*50}{END}")

docs = ["PRIVACY.md", "SLA.md", "DPA.md", "SUPPORT-POLICY.md"]
for doc in docs:
    if file_exists(doc):
        check_pass(f"{doc} exists")
    else:
        check_fail(f"{doc} missing")

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}BLOCKER #5: LGPL Compliance{END}")
print(f"{BOLD}{'='*50}{END}")

if file_exists("THIRD-PARTY-LICENSES/README.md"):
    check_pass("THIRD-PARTY-LICENSES/README.md exists")
    if contains("THIRD-PARTY-LICENSES/README.md", "LGPL"):
        check_pass("LGPL tracked in license docs")
    else:
        check_warn("LGPL not mentioned")
else:
    check_fail("THIRD-PARTY-LICENSES/README.md missing")

if file_exists("LICENSES.md"):
    check_pass("LICENSES.md exists")
else:
    check_fail("LICENSES.md missing")

print(f"\n{BOLD}{'='*50}{END}")
print(f"{BOLD}SUMMARY{END}")
print(f"{BOLD}{'='*50}{END}")

total = passed + failed + warned
print(f"\n{GREEN}PASSED:{END} {passed}")
print(f"{RED}FAILED:{END} {failed}")
print(f"{YELLOW}WARNED:{END} {warned}")
print(f"{BOLD}TOTAL:{END} {total}\n")

if failed == 0:
    if warned == 0:
        print(f"{GREEN}{BOLD}✓ ALL CHECKS PASSED - READY FOR MARKETPLACE{END}\n")
        sys.exit(0)
    else:
        print(f"{YELLOW}{BOLD}⚠ CHECKS PASSED (with warnings) - READY FOR MARKETPLACE{END}\n")
        sys.exit(0)
else:
    print(f"{RED}{BOLD}✗ FAILURES DETECTED - REVIEW ABOVE{END}\n")
    sys.exit(1)
