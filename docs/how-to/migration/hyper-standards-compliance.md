# How to Achieve HYPER_STANDARDS Compliance at Scale

**Quadrant**: How-to | **Audience**: Engineering teams, compliance managers, DevOps | **Updated**: 2026-02-28

A complete, practical guide for organizations migrating to HYPER_STANDARDS compliance across hundreds of modules and thousands of files. This guide covers the 5-phase compliance workflow, batch remediation strategies, and operational integration.

---

## Table of Contents

1. [What Are HYPER_STANDARDS?](#what-are-hyper_standards)
2. [Why Compliance Matters](#why-compliance-matters)
3. [Compliance Scope at Scale](#compliance-scope-at-scale)
4. [The 5-Phase Compliance Workflow](#the-5-phase-compliance-workflow)
5. [Phase 1: Audit Current State](#phase-1-audit-current-state)
6. [Phase 2: Plan Compliance](#phase-2-plan-compliance)
7. [Phase 3: Fix Violations](#phase-3-fix-violations)
8. [Phase 4: Automate Enforcement](#phase-4-automate-enforcement)
9. [Phase 5: Monitor Compliance](#phase-5-monitor-compliance)
10. [Batch Remediation Strategies](#batch-remediation-strategies)
11. [Integration with GODSPEED](#integration-with-godspeed)
12. [Real-World Scenarios](#real-world-scenarios)
13. [Tools and Automation](#tools-and-automation)
14. [Troubleshooting Guide](#troubleshooting-guide)
15. [Configuration Reference](#configuration-reference)

---

## What Are HYPER_STANDARDS?

HYPER_STANDARDS are Fortune 5 production code quality standards that enforce honest, transparent code with zero tolerance for:

1. **H_TODO** — Deferred work markers (TODO, FIXME, HACK, etc.)
2. **H_MOCK** — Mock/stub/fake implementations in production code
3. **H_STUB** — Empty returns, no-op methods, placeholder data
4. **H_EMPTY** — Method bodies with no implementation
5. **H_FALLBACK** — Silent degradation to fake behavior on errors
6. **H_LIE** — Code behavior that doesn't match its documentation
7. **H_SILENT** — Logging errors instead of throwing exceptions

**Core principle**: Every line of code must do EXACTLY what it claims to do. No exceptions, no workarounds, no deferred promises.

### The Five Commandments

```
1. NO DEFERRED WORK — No TODO/FIXME/XXX/HACK variants
2. NO MOCKS — No mock/stub/fake/test/demo behavior
3. NO STUBS — No empty returns, no-op methods, placeholder data
4. NO FALLBACKS — No silent degradation on errors
5. NO LIES — Code behavior must match docs and promises
```

**Enforcement**: Violation of ANY commandment = code rejected (exit 2 from validation hook)

---

## Why Compliance Matters

### Production Safety
- Code runs critical business workflows worth millions per hour
- Silent failures cause data corruption and compliance violations
- Stakeholders trust system status reports — mocks undermine that trust

### Code Quality
- Future AI assistants read this code; mocks confuse them about what's real
- Honest exceptions help developers understand requirements
- Clear contracts enable better testing and integration

### Maintainability
- Developers quickly distinguish real vs incomplete code
- No hidden technical debt
- Easier debugging and auditing

### Toyota Production System (Jidoka)
- **Stop the line** when defects occur
- **Fail fast** instead of passing defects downstream
- **Andon cord** = throw exceptions, not log.warn()

### Chicago TDD Principles
- Test real integrations, not mocks
- Collaboration tests, not isolation
- End-to-end confidence, not unit test theater

---

## Compliance Scope at Scale

### Organizational Levels

| Level | Scope | Effort | Timeline |
|-------|-------|--------|----------|
| **Small** | 1-2 modules, 50-100 files | 1-2 days | 1 person |
| **Medium** | 5-10 modules, 200-500 files | 1-2 weeks | 2-3 people |
| **Large** | 30+ modules, 5000+ files | 2-3 months | Team of 5-8 |
| **Enterprise** | 100+ modules, 50000+ files | 6 months | Phased across quarters |

### Discovery Questions

Before starting compliance, answer these questions:

- How many Java files exist in the codebase?
- Which modules have existing mock/stub code?
- How many TODO/FIXME comments are in the codebase?
- What's the age of the codebase (new vs legacy)?
- Do you have automated testing in place?
- How many developers will participate?
- What's the risk tolerance (aggressive vs conservative)?

---

## The 5-Phase Compliance Workflow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   HYPER_STANDARDS COMPLIANCE WORKFLOW                    │
└─────────────────────────────────────────────────────────────────────────┘

Phase 1: AUDIT             Phase 2: PLAN             Phase 3: FIX
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│ Scan for     │          │ Prioritize   │          │ Batch fix    │
│ violations   │ ─────→   │ violations   │ ─────→   │ violations   │
│ (dry-run)    │          │ by severity  │          │ by category  │
└──────────────┘          └──────────────┘          └──────────────┘
     ↓                          ↓                          ↓
  Receipt:                  Priority:                 Commits:
  14 H_TODO               HIGH (20%)                 Atomic fixes
  32 H_MOCK                MED (50%)                per module
  5 H_STUB                 LOW (30%)
  etc.

Phase 4: AUTOMATE          Phase 5: MONITOR
┌──────────────┐          ┌──────────────┐
│ Enforce via  │          │ Track        │
│ CI/CD gates  │ ─────→   │ compliance   │
│ and hooks    │          │ metrics      │
└──────────────┘          └──────────────┘
     ↓                          ↓
  Gate: Reject             Dashboard:
  PRs with                  100% target
  violations               Trend tracking
```

### Workflow Checklist

- [ ] Phase 1: Run audit scanner, capture baseline metrics
- [ ] Phase 2: Prioritize violations (severity, impact, effort)
- [ ] Phase 3: Assign fixes by module and team
- [ ] Phase 4: Deploy validation gates in CI/CD
- [ ] Phase 5: Set up compliance dashboard and reporting

---

## Phase 1: Audit Current State

### Objective

Discover all violations in the codebase without making changes. Establish baseline metrics and understand the scope of work.

### Tools

- **hyper-validate.sh** — Scanner script for forbidden patterns
- **ggen validate** — Semantic validation (for complex patterns)
- **Custom scanner** — Report violations to JSON receipt

### Procedure 1.1: Quick Audit (30 minutes)

**For small codebases (<100 files)**

```bash
# Navigate to project root
cd /home/user/yawl

# Run quick audit on specific module
bash .claude/hooks/hyper-validate.sh src/main/java/org/yawl/engine

# Or scan entire codebase
find src/main/java -name "*.java" \
  | xargs grep -l -E "(TODO|FIXME|XXX|HACK|mock|stub|fake)" \
  | head -20

# Count violations by type
echo "=== VIOLATION COUNTS ==="
echo "TODO comments:"
find src/main/java -name "*.java" \
  | xargs grep -c "TODO\|FIXME\|XXX\|HACK" 2>/dev/null \
  | awk -F: '{sum+=$2} END {print sum}'

echo "Mock methods:"
find src/main/java -name "*.java" \
  | xargs grep -c "(mock|stub|fake)[A-Z]" 2>/dev/null \
  | awk -F: '{sum+=$2} END {print sum}'

echo "Empty returns:"
find src/main/java -name "*.java" \
  | xargs grep -c 'return\s+"";' 2>/dev/null \
  | awk -F: '{sum+=$2} END {print sum}'
```

### Procedure 1.2: Comprehensive Audit (2-4 hours)

**For large codebases (1000+ files)**

Create a comprehensive audit script:

```bash
#!/bin/bash
# scripts/compliance/audit-violations.sh

set -e

CODEBASE_ROOT="${1:-.}"
REPORT_DIR="${CODEBASE_ROOT}/.compliance/audits"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/audit_${TIMESTAMP}.json"

mkdir -p "${REPORT_DIR}"

echo "Starting comprehensive HYPER_STANDARDS audit..."

# Define violation patterns
declare -A PATTERNS=(
  ["H_TODO"]='//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub)'
  ["H_MOCK"]='(mock|stub|fake|test|demo|sample)[A-Z][a-zA-Z]*\s*[=(]'
  ["H_STUB"]='return\s+"";|return\s+0;|return\s+null;.*//.*stub'
  ["H_EMPTY"]='public\s+void\s+\w+\([^)]*\)\s*\{\s*\}'
  ["H_FALLBACK"]='catch\s*\([^)]+\)\s*\{[^}]*(return|fake)'
  ["H_SILENT"]='log\.(warn|error)\(".*not\s+implemented'
)

# Create JSON receipt structure
cat > "${REPORT_FILE}" <<'EOF'
{
  "audit_timestamp": "$(date -Iseconds)",
  "codebase_root": "${CODEBASE_ROOT}",
  "violations": {},
  "summary": {}
}
EOF

# Scan each pattern
for pattern_name in "${!PATTERNS[@]}"; do
  echo "Scanning for $pattern_name..."

  count=$(find "${CODEBASE_ROOT}/src" -name "*.java" \
    | xargs grep -c "${PATTERNS[$pattern_name]}" 2>/dev/null \
    | awk -F: '{sum+=$2} END {print sum}' || echo "0")

  # Find files with violations
  files=$(find "${CODEBASE_ROOT}/src" -name "*.java" \
    | xargs grep -l "${PATTERNS[$pattern_name]}" 2>/dev/null \
    | sort)

  # Append to receipt
  echo "$pattern_name: $count violations" >> "${REPORT_FILE}"
done

echo "Audit complete. Report: ${REPORT_FILE}"
cat "${REPORT_FILE}"
```

Run the audit:

```bash
bash scripts/compliance/audit-violations.sh /home/user/yawl
```

### Procedure 1.3: Generate Audit Report

Create a detailed report with module breakdown:

```bash
#!/bin/bash
# scripts/compliance/generate-audit-report.sh

CODEBASE_ROOT="${1:-.}"

echo "=========================================="
echo "HYPER_STANDARDS COMPLIANCE AUDIT REPORT"
echo "=========================================="
echo "Date: $(date)"
echo "Codebase: ${CODEBASE_ROOT}"
echo ""

echo "VIOLATION SUMMARY BY PATTERN:"
echo "---"

patterns=("H_TODO" "H_MOCK" "H_STUB" "H_EMPTY" "H_FALLBACK" "H_SILENT")
declare -A pattern_regex=(
  ["H_TODO"]='//\s*(TODO|FIXME|XXX|HACK)'
  ["H_MOCK"]='(mock|stub|fake)[A-Z]'
  ["H_STUB"]='return\s+"";'
  ["H_EMPTY"]='void\s+\w+\([^)]*\)\s*\{\s*\}'
  ["H_FALLBACK"]='catch.*return'
  ["H_SILENT"]='log\.(warn|error)'
)

for pattern in "${patterns[@]}"; do
  count=$(find "${CODEBASE_ROOT}/src/main/java" -name "*.java" 2>/dev/null \
    | xargs grep -c "${pattern_regex[$pattern]}" 2>/dev/null \
    | awk -F: '{sum+=$2} END {print sum+0}')

  echo "${pattern}: ${count} violations"
done

echo ""
echo "VIOLATION BREAKDOWN BY MODULE:"
echo "---"

for module_dir in "${CODEBASE_ROOT}/yawl"/*; do
  if [ -d "$module_dir/src/main/java" ]; then
    module_name=$(basename "$module_dir")
    todo_count=$(find "$module_dir" -name "*.java" \
      | xargs grep -c "TODO\|FIXME" 2>/dev/null \
      | awk -F: '{sum+=$2} END {print sum+0}')

    if [ "$todo_count" -gt 0 ]; then
      echo "${module_name}: ${todo_count} TODOs"
    fi
  fi
done

echo ""
echo "FILES WITH MOST VIOLATIONS:"
echo "---"

find "${CODEBASE_ROOT}/src/main/java" -name "*.java" \
  | while read file; do
    count=$(grep -c "TODO\|FIXME\|mock\|stub" "$file" 2>/dev/null || echo 0)
    echo "$count $file"
  done \
  | sort -rn \
  | head -20
```

### Output: Audit Receipt

The audit produces a JSON receipt:

```json
{
  "audit_timestamp": "2026-02-28T14:30:15Z",
  "codebase_root": "/home/user/yawl",
  "total_files_scanned": 2847,
  "violations_by_pattern": {
    "H_TODO": 147,
    "H_MOCK": 34,
    "H_STUB": 89,
    "H_EMPTY": 12,
    "H_FALLBACK": 23,
    "H_SILENT": 18,
    "H_LIE": 5
  },
  "total_violations": 328,
  "severity_breakdown": {
    "CRITICAL": 12,
    "HIGH": 156,
    "MEDIUM": 127,
    "LOW": 33
  },
  "modules_affected": [
    {
      "module": "yawl-engine",
      "violations": 89,
      "files_affected": 23
    },
    {
      "module": "yawl-elements",
      "violations": 64,
      "files_affected": 18
    }
  ]
}
```

---

## Phase 2: Plan Compliance

### Objective

Create a prioritized remediation plan based on audit results. Determine which violations to fix first, in what order, and with what strategy.

### Prioritization Matrix

Create a prioritization score for each violation:

```
Priority Score = (Impact × 0.5) + (Effort × 0.3) + (Risk × 0.2)

Where:
  Impact = How critical is this code? (1-10)
           10 = core engine, 1 = utility
  Effort = How hard to fix? (1-10)
           10 = major rewrite, 1 = simple regex fix
  Risk = How risky is the change? (1-10)
         10 = changes core behavior, 1 = safe refactor
```

### Procedure 2.1: Classify Violations by Severity

```bash
#!/bin/bash
# scripts/compliance/classify-violations.sh

AUDIT_RECEIPT="${1:-./compliance-audit.json}"

echo "VIOLATION CLASSIFICATION"
echo "========================"
echo ""

# H_TODO — Usually medium priority (easy to fix, deferred work)
echo "H_TODO — Deferred Work Markers"
echo "  Priority: MEDIUM"
echo "  Effort: LOW (1-2 hours per file)"
echo "  Action: Remove comment or implement/throw exception"
echo "  Example count: $(grep -c 'TODO' "${AUDIT_RECEIPT}" || echo '0')"
echo ""

# H_MOCK — HIGH priority (violates core principle)
echo "H_MOCK — Mock Implementations"
echo "  Priority: HIGH"
echo "  Effort: MEDIUM-HIGH (4-8 hours per file)"
echo "  Action: Replace with real impl or throw UnsupportedOperationException"
echo ""

# H_STUB — HIGH priority (impacts functionality)
echo "H_STUB — Empty/Placeholder Returns"
echo "  Priority: HIGH"
echo "  Effort: MEDIUM (2-4 hours per file)"
echo "  Action: Implement real logic or throw exception"
echo ""

# H_EMPTY — CRITICAL (core violation)
echo "H_EMPTY — Empty Method Bodies"
echo "  Priority: CRITICAL"
echo "  Effort: MEDIUM-HIGH (3-6 hours per file)"
echo "  Action: Implement immediately or throw exception"
echo ""

# H_FALLBACK — CRITICAL (silent failures)
echo "H_FALLBACK — Silent Fallbacks"
echo "  Priority: CRITICAL"
echo "  Effort: HIGH (6-10 hours per file)"
echo "  Action: Propagate exceptions, remove fake behavior"
echo ""

# H_SILENT — HIGH (prevents debugging)
echo "H_SILENT — Log Instead of Throw"
echo "  Priority: HIGH"
echo "  Effort: MEDIUM (2-3 hours per file)"
echo "  Action: Replace log.error() with throw statements"
echo ""

# H_LIE — CRITICAL (violates core principle)
echo "H_LIE — Dishonest Behavior"
echo "  Priority: CRITICAL"
echo "  Effort: HIGH (8+ hours per file)"
echo "  Action: Fix behavior or update documentation"
```

### Procedure 2.2: Create Remediation Plan

```bash
#!/bin/bash
# scripts/compliance/create-remediation-plan.sh

AUDIT_RECEIPT="${1:-./compliance-audit.json}"
PLAN_FILE="${2:-./remediation-plan.md}"

cat > "${PLAN_FILE}" <<'EOF'
# HYPER_STANDARDS Remediation Plan

## Timeline Overview

- **Phase 1 (Week 1-2)**: Fix CRITICAL violations (H_EMPTY, H_FALLBACK, H_LIE)
- **Phase 2 (Week 3-4)**: Fix HIGH violations (H_MOCK, H_STUB, H_SILENT)
- **Phase 3 (Week 5-6)**: Fix MEDIUM violations (H_TODO)
- **Phase 4 (Week 7-8)**: Validation and compliance gates

## Priority 1: CRITICAL (Complete by Day 14)

### H_EMPTY — Empty Method Bodies
- Effort: 20 hours
- Risk: Medium (may break tests)
- Approach: Batch by module, implement real logic or throw exception
- Modules: yawl-engine (8h), yawl-elements (6h), yawl-integration (6h)

### H_FALLBACK — Silent Fallbacks
- Effort: 24 hours
- Risk: High (error handling changes)
- Approach: Review catch blocks, propagate or throw
- Modules: yawl-integration (12h), yawl-resourcing (8h), yawl-persistence (4h)

### H_LIE — Dishonest Behavior
- Effort: 16 hours
- Risk: High (behavior changes)
- Approach: Fix behavior to match docs or update docs
- Modules: yawl-engine (10h), yawl-elements (6h)

**Total CRITICAL effort: 60 hours (2 engineers × 3 weeks)**

## Priority 2: HIGH (Complete by Day 28)

### H_MOCK — Mock Implementations
- Effort: 32 hours
- Risk: Medium (removes test utilities)
- Approach: Delete mocks or implement real services
- Modules: yawl-engine (12h), yawl-elements (10h), yawl-test-utils (10h)

### H_STUB — Empty/Placeholder Returns
- Effort: 28 hours
- Risk: Medium (behavior changes)
- Approach: Implement real logic or throw exception
- Modules: yawl-integration (12h), yawl-resourcing (10h), yawl-elements (6h)

### H_SILENT — Log Instead of Throw
- Effort: 16 hours
- Risk: Low (improves error handling)
- Approach: Replace log.error() with throw statements
- Modules: yawl-persistence (8h), yawl-integration (8h)

**Total HIGH effort: 76 hours (2 engineers × 4 weeks)**

## Priority 3: MEDIUM (Complete by Day 42)

### H_TODO — Deferred Work Markers
- Effort: 20 hours
- Risk: Low (safe cleanup)
- Approach: Remove or implement/throw
- Modules: yawl-engine (6h), yawl-elements (6h), yawl-integration (8h)

**Total MEDIUM effort: 20 hours (1 engineer × 2 weeks)**

## Resource Allocation

### Team A (Weeks 1-2): CRITICAL Violations
- Engineer 1: H_EMPTY violations in yawl-engine
- Engineer 2: H_FALLBACK violations in yawl-integration
- Lead: Review, approve, test

### Team B (Weeks 3-4): HIGH Violations
- Engineer 3: H_MOCK violations in yawl-test-utils
- Engineer 4: H_STUB violations in yawl-elements
- Lead: Review, approve, merge

### Team C (Weeks 5-6): MEDIUM Violations
- Engineer 5: H_TODO cleanup across modules
- Lead: Final review, gate deployment

## Success Criteria

- [x] All CRITICAL violations resolved (0 remaining)
- [x] All HIGH violations resolved (0 remaining)
- [x] All MEDIUM violations resolved (0 remaining)
- [x] CI/CD validation gates enabled
- [x] Code review process updated
- [x] Team training completed
- [x] Compliance dashboard deployed

EOF

echo "Remediation plan created: ${PLAN_FILE}"
cat "${PLAN_FILE}"
```

### Output: Remediation Plan

```markdown
# HYPER_STANDARDS Remediation Plan

## Violation Summary

| Severity | Pattern | Count | Effort | Timeline |
|----------|---------|-------|--------|----------|
| CRITICAL | H_EMPTY | 12 | 20h | Week 1 |
| CRITICAL | H_FALLBACK | 23 | 24h | Week 2 |
| CRITICAL | H_LIE | 5 | 16h | Week 1-2 |
| HIGH | H_MOCK | 34 | 32h | Week 3 |
| HIGH | H_STUB | 89 | 28h | Week 3-4 |
| HIGH | H_SILENT | 18 | 16h | Week 4 |
| MEDIUM | H_TODO | 147 | 20h | Week 5-6 |

**Total effort: 176 hours (6 engineers × 6 weeks)**

## Phased Rollout

**Phase 1 (CRITICAL)**: Resolve all data corruption and silent failure risks
**Phase 2 (HIGH)**: Fix core principle violations
**Phase 3 (MEDIUM)**: Clean up deferred work markers
```

---

## Phase 3: Fix Violations

### Objective

Apply fixes to violations in a coordinated, testable manner. Focus on atomic, reviewable changes per module or category.

### Batch Remediation Strategies

#### Strategy 1: Fix by Module

Fix all violations in one module at a time:

```bash
#!/bin/bash
# scripts/compliance/fix-by-module.sh

MODULE="${1:-yawl-engine}"
FIXES_DIR=".compliance/fixes/${MODULE}"

mkdir -p "${FIXES_DIR}"

echo "Starting compliance fixes for module: ${MODULE}"

# Step 1: Identify all violations in this module
echo "Step 1: Scanning module for violations..."
find "yawl/${MODULE}/src/main/java" -name "*.java" \
  | while read file; do
    grep -n "TODO\|FIXME\|mock\|stub\|fake" "$file" \
      | head -10 >> "${FIXES_DIR}/violations.txt"
  done

# Step 2: Create a fix branch
git checkout -b "compliance/fix-${MODULE}-$(date +%s)"

# Step 3: Review fixes by pattern
echo "Step 2: Categorizing violations..."
grep "TODO\|FIXME" "${FIXES_DIR}/violations.txt" > "${FIXES_DIR}/todos.txt"
grep "mock\|stub\|fake" "${FIXES_DIR}/violations.txt" > "${FIXES_DIR}/mocks.txt"

# Step 4: Fix TODOs (safest)
echo "Step 3: Fixing deferred work markers..."
# [manual review and fixes here]

# Step 5: Fix mocks (requires implementation)
echo "Step 4: Fixing mock implementations..."
# [manual implementation or throw exceptions]

# Step 6: Test
echo "Step 5: Running tests..."
mvn test -pl "yawl/${MODULE}"

# Step 7: Validate
echo "Step 6: Running compliance validation..."
bash .claude/hooks/hyper-validate.sh "yawl/${MODULE}/src/main/java"

# Step 8: Commit if green
echo "Step 7: Committing changes..."
git add "yawl/${MODULE}/src"
git commit -m "fix(${MODULE}): resolve HYPER_STANDARDS violations"
```

#### Strategy 2: Fix by Pattern

Fix all violations of one type across the codebase:

```bash
#!/bin/bash
# scripts/compliance/fix-by-pattern.sh

PATTERN="${1:-H_TODO}"

case "${PATTERN}" in
  "H_TODO")
    echo "Fixing H_TODO: Deferred work markers"
    # Find and remove TODO comments, or implement/throw
    find src/main/java -name "*.java" -exec sed -i '
      /\/\/ TODO:/c\    throw new UnsupportedOperationException("TODO: see implementation guide");
      /\/\/ FIXME:/c\    throw new UnsupportedOperationException("FIXME: see implementation guide");
    ' {} \;
    ;;

  "H_MOCK")
    echo "Fixing H_MOCK: Remove mock implementations"
    # Find mockXxx methods and replace with throw
    find src/main/java -name "*.java" -exec grep -l "mock[A-Z]" {} \; \
      | while read file; do
        echo "Review and fix mocks in: $file"
      done
    ;;

  "H_STUB")
    echo "Fixing H_STUB: Remove placeholder returns"
    find src/main/java -name "*.java" -exec sed -i '
      s/return "";/throw new UnsupportedOperationException("Implementation required");/g
      s/return 0;/throw new UnsupportedOperationException("Implementation required");/g
      s/return null;/throw new UnsupportedOperationException("Implementation required");/g
    ' {} \;
    ;;
esac

# Validate fixes
echo "Validating fixes..."
mvn clean verify -P analysis
```

#### Strategy 3: Fix by Team

Assign fixes to specific teams by ownership:

```bash
#!/bin/bash
# scripts/compliance/assign-fixes-by-team.sh

cat > .compliance/team-assignments.txt <<'EOF'
## TEAM A (Engine & Core)
- Modules: yawl-engine, yawl-elements, yawl-stateless
- Lead: Alice
- Violations: 120
- Effort: 40 hours
- Deadline: 2 weeks

## TEAM B (Integration & Resourcing)
- Modules: yawl-integration, yawl-resourcing, yawl-mcp
- Lead: Bob
- Violations: 85
- Effort: 30 hours
- Deadline: 2 weeks

## TEAM C (Infrastructure)
- Modules: yawl-persistence, yawl-monitoring, yawl-test-utils
- Lead: Carol
- Violations: 45
- Effort: 20 hours
- Deadline: 1 week

EOF

echo "Team assignments created. Next steps:"
echo "1. Review .compliance/team-assignments.txt"
echo "2. Create per-team branches: compliance/team-a, compliance/team-b, etc."
echo "3. Assign PR reviews by team expertise"
echo "4. Merge in order: Team C → Team B → Team A"
```

### Procedure 3.1: Fix a Single H_TODO Violation

**Time estimate**: 5-10 minutes

```bash
# Step 1: Locate the TODO
grep -n "TODO" src/main/java/org/yawl/engine/YWorkItem.java
# Output: 427: // TODO: Add deadlock detection

# Step 2: Examine the context
sed -n '425,430p' src/main/java/org/yawl/engine/YWorkItem.java

# Step 3: Choose your fix:
# Option A: Remove TODO and implement the real logic
# Option B: Replace with UnsupportedOperationException + implementation guide

# Option A (implement):
# [Add real deadlock detection code]

# Option B (throw with guide):
cat > /tmp/fix.txt <<'EOF'
private void detectDeadlock() {
    throw new UnsupportedOperationException(
        "Deadlock detection requires:\n" +
        "  1. Graph cycle detection algorithm\n" +
        "  2. Work item dependency analysis\n" +
        "  3. Notification to case handler\n" +
        "See DeadlockDetector.java for similar implementation"
    );
}
EOF

# Step 4: Apply the fix
# [Edit the file using Edit tool]

# Step 5: Validate
bash .claude/hooks/hyper-validate.sh src/main/java/org/yawl/engine/YWorkItem.java

# Step 6: Commit
git add src/main/java/org/yawl/engine/YWorkItem.java
git commit -m "fix(engine): remove TODO comment, implement deadlock detection"
```

### Procedure 3.2: Fix a Single H_MOCK Violation

**Time estimate**: 1-3 hours

```bash
# Step 1: Locate the mock
grep -n "mockData\|MockService" src/main/java/org/yawl/test/MockDataService.java

# Step 2: Examine the mock implementation
cat src/main/java/org/yawl/test/MockDataService.java

# Step 3: Decide: Delete or implement?
# Question 1: Is this test-only code (in src/test)?
#   YES → OK to keep with clear test markers
#   NO → Must delete or replace

# Question 2: Are other modules depending on this?
#   YES → Implement a real version or create an interface
#   NO → Delete if test-only, implement if production

# Option A (Delete mock):
rm src/main/java/org/yawl/test/MockDataService.java

# Option B (Implement real version):
# [Create real DataService implementation using actual database/API]

# Step 4: Update imports and references
find src -name "*.java" \
  | xargs grep -l "MockDataService\|new MockData" \
  | while read file; do
    # Update to use real service instead
    sed -i 's/MockDataService/RealDataService/g' "$file"
    sed -i 's/new MockData/RealDataService.create/g' "$file"
  done

# Step 5: Run tests to ensure nothing broke
mvn test

# Step 6: Validate
bash .claude/hooks/hyper-validate.sh src/

# Step 7: Commit
git add -A
git commit -m "fix(test): replace MockDataService with real implementation"
```

### Procedure 3.3: Fix a Single H_FALLBACK Violation

**Time estimate**: 2-4 hours

```bash
# Step 1: Locate the fallback
grep -n "catch.*return.*fake\|getOrDefault" src/main/java/org/yawl/integration/ApiClient.java

# Step 2: Examine the fallback logic
sed -n '120,140p' src/main/java/org/yawl/integration/ApiClient.java

# Step 3: Replace with honest error handling

# Before (FORBIDDEN):
cat > /tmp/before.txt <<'EOF'
public String fetchConfig(String key) {
    try {
        return api.getConfig(key);
    } catch (ApiException e) {
        log.warn("API failed, using default");
        return "default_value";  // LIES!
    }
}
EOF

# After (CORRECT):
cat > /tmp/after.txt <<'EOF'
public String fetchConfig(String key) {
    if (key == null || key.isEmpty()) {
        throw new IllegalArgumentException("Config key required");
    }
    try {
        return api.getConfig(key);
    } catch (ApiException e) {
        throw new RuntimeException(
            "Failed to fetch config key: " + key + ". " +
            "Check API credentials and network connectivity.",
            e
        );
    }
}
EOF

# Step 4: Apply the fix
# [Use Edit tool to replace the method]

# Step 5: Update callers to handle the exception
find src -name "*.java" \
  | xargs grep -l "fetchConfig\|getOrDefault" \
  | while read file; do
    echo "Review: $file - ensure exception handling"
  done

# Step 6: Add unit tests for error cases
# [Create test that verifies exception is thrown]

# Step 7: Validate
bash .claude/hooks/hyper-validate.sh src/

# Step 8: Commit
git add -A
git commit -m "fix(integration): remove silent fallback, propagate API errors"
```

### Testing After Fixes

After fixing violations, always run:

```bash
# 1. Validate no new violations
bash .claude/hooks/hyper-validate.sh src/main/java

# 2. Run unit tests
mvn test

# 3. Run integration tests
mvn verify

# 4. Run static analysis
mvn clean verify -P analysis

# 5. Check compilation
mvn clean compile

# 6. Create summary report
echo "Fixes applied to module: $MODULE"
git diff --stat HEAD~1
```

---

## Phase 4: Automate Enforcement

### Objective

Deploy validation gates to prevent new violations from entering the codebase. Integrate compliance checking into CI/CD pipeline.

### Procedure 4.1: Enable Local Validation Hook

The hook blocks violations before code is even committed.

```bash
# Step 1: Ensure hook exists
cat /home/user/yawl/.claude/hooks/hyper-validate.sh

# Step 2: Make it executable
chmod +x /home/user/yawl/.claude/hooks/hyper-validate.sh

# Step 3: Test the hook
echo 'public void test() { // TODO: implement' | \
  bash /home/user/yawl/.claude/hooks/hyper-validate.sh -

# Expected output:
# ❌ STANDARDS VIOLATION: TODO-like markers found
# Exit code: 2
```

### Procedure 4.2: Configure Git Pre-Commit Hook

Prevent commits with violations:

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Run hyper-validate.sh on staged Java files
STAGED_FILES=$(git diff --cached --name-only | grep '\.java$')

if [ -z "$STAGED_FILES" ]; then
    exit 0
fi

for file in $STAGED_FILES; do
    bash .claude/hooks/hyper-validate.sh "$file"
    if [ $? -ne 0 ]; then
        echo "❌ Commit blocked: HYPER_STANDARDS violations in $file"
        exit 2
    fi
done

exit 0
```

Install the hook:

```bash
# Copy to .git
cp scripts/compliance/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Test
git add src/main/java/Badcode.java  # File with violations
git commit -m "test"  # Will be blocked!
```

### Procedure 4.3: Configure CI/CD Validation Gate

Add compliance check to GitHub Actions (or equivalent):

```yaml
# .github/workflows/compliance-gate.yml
name: HYPER_STANDARDS Compliance

on:
  pull_request:
    paths:
      - 'src/main/java/**/*.java'
      - '.github/workflows/compliance-gate.yml'

jobs:
  compliance:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0

      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run HYPER_STANDARDS validation
        run: |
          # Get changed Java files
          CHANGED_FILES=$(git diff --name-only origin/main HEAD | grep '\.java$')

          if [ -z "$CHANGED_FILES" ]; then
            echo "✅ No Java files changed"
            exit 0
          fi

          echo "Validating ${CHANGED_FILES} for HYPER_STANDARDS violations..."

          VIOLATIONS=0
          for file in $CHANGED_FILES; do
            bash .claude/hooks/hyper-validate.sh "$file" || VIOLATIONS=$((VIOLATIONS+1))
          done

          if [ $VIOLATIONS -gt 0 ]; then
            echo "❌ $VIOLATIONS file(s) have HYPER_STANDARDS violations"
            exit 2
          fi

          echo "✅ All files passed HYPER_STANDARDS validation"
          exit 0

      - name: Comment on PR if violations found
        if: failure()
        uses: actions/github-script@v6
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '❌ PR contains HYPER_STANDARDS violations. Fix violations and push changes.\n\nSee: https://yawl.example.com/docs/how-to/migration/hyper-standards-compliance.md'
            })
```

### Procedure 4.4: Create Compliance Report Dashboard

Track compliance metrics over time:

```bash
#!/bin/bash
# scripts/compliance/generate-dashboard.sh

REPORT_DIR=".compliance/reports"
mkdir -p "${REPORT_DIR}"

cat > "${REPORT_DIR}/index.html" <<'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>HYPER_STANDARDS Compliance Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .metric { display: inline-block; margin: 20px; padding: 15px; border: 1px solid #ccc; }
        .green { color: #27ae60; font-weight: bold; }
        .red { color: #e74c3c; font-weight: bold; }
        .chart { margin: 20px 0; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
        th { background-color: #f9f9f9; }
    </style>
</head>
<body>
    <h1>HYPER_STANDARDS Compliance Dashboard</h1>
    <p>Last updated: <span id="timestamp"></span></p>

    <div class="metric">
        <h3>Overall Compliance</h3>
        <div class="green" style="font-size: 32px;" id="compliance-score">0%</div>
    </div>

    <div class="metric">
        <h3>Violations</h3>
        <div class="red" style="font-size: 32px;" id="violation-count">0</div>
    </div>

    <div class="metric">
        <h3>Modules at 100%</h3>
        <div class="green" style="font-size: 32px;" id="clean-modules">0</div>
    </div>

    <h2>Violations by Pattern</h2>
    <table id="violations-table">
        <tr>
            <th>Pattern</th>
            <th>Count</th>
            <th>Severity</th>
            <th>Trend</th>
        </tr>
    </table>

    <h2>Modules by Compliance</h2>
    <table id="modules-table">
        <tr>
            <th>Module</th>
            <th>Violations</th>
            <th>Files</th>
            <th>Compliance %</th>
            <th>Status</th>
        </tr>
    </table>

    <script>
        // Load compliance data and render dashboard
        // [JavaScript to fetch and display metrics]
    </script>
</body>
</html>
EOF

echo "Dashboard created: ${REPORT_DIR}/index.html"
```

---

## Phase 5: Monitor Compliance

### Objective

Continuously track compliance metrics and identify new violations early. Maintain 100% compliance as the codebase evolves.

### Procedure 5.1: Generate Compliance Metrics

Run regularly (e.g., daily) to track trends:

```bash
#!/bin/bash
# scripts/compliance/metrics.sh

METRIC_DIR=".compliance/metrics"
mkdir -p "${METRIC_DIR}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
METRIC_FILE="${METRIC_DIR}/metrics_${TIMESTAMP}.json"

# Initialize JSON
cat > "${METRIC_FILE}" <<'EOF'
{
  "timestamp": "$(date -Iseconds)",
  "codebase_stats": {},
  "violations": {},
  "modules": {}
}
EOF

# Count total Java files
TOTAL_FILES=$(find src/main/java -name "*.java" | wc -l)

# Count violations by pattern
declare -A violations=(
  ["H_TODO"]=$(find src/main/java -name "*.java" | xargs grep -c "TODO\|FIXME" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  ["H_MOCK"]=$(find src/main/java -name "*.java" | xargs grep -c "(mock|stub|fake)[A-Z]" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  ["H_STUB"]=$(find src/main/java -name "*.java" | xargs grep -c 'return\s+"";' 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  ["H_EMPTY"]=$(find src/main/java -name "*.java" | xargs grep -c 'void\s+\w+\([^)]*\)\s*\{\s*\}' 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
)

TOTAL_VIOLATIONS=0
for pattern in "${!violations[@]}"; do
  count=${violations[$pattern]}
  TOTAL_VIOLATIONS=$((TOTAL_VIOLATIONS + count))
done

COMPLIANCE=$((100 - (TOTAL_VIOLATIONS * 100 / TOTAL_FILES)))

# Generate report
cat > "${METRIC_FILE}.txt" <<EOF
HYPER_STANDARDS Compliance Metrics
Generated: $(date)

SUMMARY:
  Files scanned: ${TOTAL_FILES}
  Violations found: ${TOTAL_VIOLATIONS}
  Compliance rate: ${COMPLIANCE}%

VIOLATIONS BY PATTERN:
EOF

for pattern in "${!violations[@]}"; do
  echo "  ${pattern}: ${violations[$pattern]}" >> "${METRIC_FILE}.txt"
done

echo "Metrics saved: ${METRIC_FILE}"
cat "${METRIC_FILE}.txt"

# Keep last 30 days of metrics
find "${METRIC_DIR}" -name "metrics_*.json" -mtime +30 -delete
```

### Procedure 5.2: Set Up Automated Compliance Alerts

Alert team when compliance drops below threshold:

```bash
#!/bin/bash
# scripts/compliance/alert.sh

COMPLIANCE_THRESHOLD=95  # Alert if < 95%
METRIC_FILE="${1:-.compliance/metrics/latest.json}"

if [ ! -f "$METRIC_FILE" ]; then
  echo "Error: Metric file not found: $METRIC_FILE"
  exit 1
fi

CURRENT=$(jq -r '.compliance_rate' "$METRIC_FILE")
VIOLATIONS=$(jq -r '.total_violations' "$METRIC_FILE")

if [ "$CURRENT" -lt "$COMPLIANCE_THRESHOLD" ]; then
  echo "⚠️  ALERT: Compliance dropped to ${CURRENT}%"
  echo "Violations found: ${VIOLATIONS}"
  echo ""
  echo "Take action:"
  echo "1. Review recent commits for violations"
  echo "2. Run: bash scripts/compliance/audit-violations.sh"
  echo "3. Fix violations in feature branch"
  echo "4. Re-run validation gate"

  # Send alert (email, Slack, etc.)
  # curl -X POST https://hooks.slack.com/... \
  #   -d "text=⚠️ Compliance Alert: $CURRENT% (${VIOLATIONS} violations)"
fi
```

### Procedure 5.3: Track Compliance Trend

Create a trend chart showing compliance over time:

```bash
#!/bin/bash
# scripts/compliance/trend-report.sh

METRIC_DIR=".compliance/metrics"

echo "COMPLIANCE TREND (Last 30 Days)"
echo "==============================="
echo ""
echo "Date            Violations  Compliance"
echo "---             ----------  -----------"

ls -t "${METRIC_DIR}"/metrics_*.txt | head -30 | tac | while read file; do
  timestamp=$(basename "$file" | sed 's/metrics_//;s/\.txt//')
  # Format: 20260228_143015 → 2026-02-28
  date_fmt=$(echo "$timestamp" | sed 's/\([0-9]\{4\}\)\([0-9]\{2\}\)\([0-9]\{2\}\).*/\1-\2-\3/')
  violations=$(grep "total_violations" "$file" | awk '{print $3}')
  compliance=$(grep "Compliance rate" "$file" | awk '{print $4}')

  printf "%s  %10s  %s\n" "$date_fmt" "$violations" "$compliance"
done
```

### Output: Compliance Dashboard

Example metrics file:

```json
{
  "timestamp": "2026-02-28T15:30:00Z",
  "codebase_stats": {
    "total_files": 2847,
    "total_java_files": 2156,
    "total_lines_of_code": 847000
  },
  "violations": {
    "H_TODO": 0,
    "H_MOCK": 0,
    "H_STUB": 3,
    "H_EMPTY": 0,
    "H_FALLBACK": 1,
    "H_SILENT": 0,
    "H_LIE": 0,
    "total": 4
  },
  "compliance_rate": 99.8,
  "modules": {
    "yawl-engine": {
      "violations": 0,
      "compliance": 100
    },
    "yawl-elements": {
      "violations": 2,
      "compliance": 99.5
    },
    "yawl-integration": {
      "violations": 2,
      "compliance": 98.9
    }
  },
  "trend": {
    "violations_week_ago": 45,
    "violations_month_ago": 328,
    "improvement": "98.8% reduction"
  }
}
```

---

## Batch Remediation Strategies

### Strategy 1: Module-Based Fix (Recommended for Large Teams)

Fix one module at a time, complete and test before moving to the next.

**Advantages**:
- Clear ownership and accountability
- Easy to roll back if issues occur
- Natural testing boundaries
- Parallel teams can work on different modules

**Timeline**: Medium project (5 modules) = 1-2 weeks

**Procedure**:
1. Pick highest-risk module (e.g., yawl-engine)
2. Create branch: `compliance/fix-yawl-engine`
3. Fix all violations in that module
4. Run full test suite
5. Get code review
6. Merge when green
7. Repeat for next module

### Strategy 2: Pattern-Based Fix (Best for Distributed Teams)

Fix all violations of one type across the entire codebase.

**Advantages**:
- Easier to automate (batch sed scripts)
- Lower risk of context-switching
- Good for simple patterns (H_TODO)
- Faster for well-defined fixes

**Timeline**: Medium project (5 modules) = 3-5 days

**Procedure**:
1. Pick simplest pattern (e.g., H_TODO)
2. Create branch: `compliance/fix-h-todo`
3. Use sed/regex to fix all occurrences
4. Run validation gate
5. If <5% failures, fix exceptions manually
6. Merge
7. Repeat for next pattern

### Strategy 3: Risk-Based Fix (Best for Production Systems)

Fix highest-risk violations first (H_FALLBACK, H_LIE) before lower-risk ones.

**Advantages**:
- Reduces data corruption risk early
- Allows incremental improvements
- Business stakeholders see impact quickly

**Timeline**: Large project (30 modules) = 6-8 weeks

**Procedure**:
1. Rank by risk: H_LIE > H_FALLBACK > H_EMPTY > H_MOCK > H_STUB > H_SILENT > H_TODO
2. Fix all H_LIE violations (1 week)
3. Fix all H_FALLBACK violations (1 week)
4. Fix all H_EMPTY violations (2 weeks)
5. Etc.

### Strategy 4: Hybrid Approach (Recommended)

Combine strategies for maximum efficiency:

- **Week 1-2**: Pattern-based fix for H_TODO (safest)
- **Week 3-4**: Pattern-based fix for H_SILENT (easy)
- **Week 5-8**: Module-based fix for H_MOCK, H_STUB (requires implementation)
- **Week 9-12**: Risk-based fix for H_FALLBACK, H_EMPTY (critical)

---

## Integration with GODSPEED

GODSPEED is YAWL's enforcement circuit. HYPER_STANDARDS compliance integrates at multiple gates:

### Gate H (GUARDS)

HYPER_STANDARDS enforcement happens at the H gate:

```
Code generation flow:
  Phase 1: Generate code
  Phase 2: Validate syntax (Λ gate)
  Phase 3: Run H-GUARDS (THIS IS HYPER_STANDARDS CHECK)
    ├─ Run regex patterns (H_TODO, H_MOCK, H_SILENT, etc.)
    ├─ Run semantic checks (H_LIE detection)
    ├─ Emit guard-receipt.json
    └─ Exit 0 (GREEN) or exit 2 (RED)
  Phase 4: Check invariants (Q gate)
  Phase 5: Deploy
```

When H gate detects violations:
- Generation stops
- Receipt shows exact violations
- Developer must fix or revise spec
- Code cannot proceed to Q gate

### Integration Example

```bash
# In your CI/CD pipeline:
if ! mvn clean compile; then
  echo "Compilation failed"
  exit 1
fi

if ! bash .claude/hooks/hyper-validate.sh src/main/java; then
  echo "HYPER_STANDARDS violations detected"
  exit 2
fi

# Only if H gate passes:
mvn verify  # Run integration tests
mvn deploy  # Deploy to production
```

### Receiving Violations from GODSPEED

When code violates HYPER_STANDARDS, you receive a structured receipt:

```json
{
  "phase": "guards",
  "status": "RED",
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "src/main/java/org/yawl/engine/YWorkItem.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Implement real deadlock detection or throw UnsupportedOperationException"
    }
  ],
  "error_message": "3 guard violations found. Fix violations or throw UnsupportedOperationException."
}
```

**Response**:
1. Read receipt carefully
2. Understand why code violates
3. Choose: implement or throw exception
4. Create fix branch
5. Apply fixes
6. Re-run validation
7. Submit for review

---

## Real-World Scenarios

### Scenario 1: Small Project (1 module, 50 files) — 1-2 days

**Project**: Internal utility library with 50 Java files, 1 module.

**Current state audit**:
- 12 TODO comments
- 3 mock methods
- 2 empty returns
- Total: 17 violations

**Timeline**:
- **Day 1 Morning (2 hours)**: Audit, identify violations
- **Day 1 Afternoon (4 hours)**: Fix all 17 violations
  - Fix 12 TODOs: 2 hours
  - Replace 3 mocks: 1.5 hours
  - Fix 2 empty returns: 0.5 hours
- **Day 1 Evening (1 hour)**: Test, code review
- **Day 2 Morning (1 hour)**: Final review, merge

**Team**: 1 engineer + 1 reviewer

**Effort**: 8 hours total

**Success criteria**:
- Validation hook returns 0
- All tests pass
- Zero violations in final code review

### Scenario 2: Medium Project (5 modules, 200 files) — 1-2 weeks

**Project**: Business process engine with 5 modules, 200 Java files.

**Current state audit**:
- Module A (engine): 30 violations
- Module B (elements): 25 violations
- Module C (integration): 20 violations
- Module D (persistence): 15 violations
- Module E (util): 10 violations
- Total: 100 violations

**Timeline**:
- **Week 1**:
  - Day 1: Audit and planning (4 hours)
  - Day 2-3: Team A fixes Module E (20 hours)
  - Day 4-5: Team B fixes Module D (15 hours)

- **Week 2**:
  - Day 1-2: Team A fixes Module C (20 hours)
  - Day 3-4: Team B fixes Module B (25 hours)
  - Day 5: Team A fixes Module A + final review (30 hours)

**Team**: 2 engineers + 1 lead

**Effort**: 114 hours (2 engineers × 2 weeks)

**Success criteria**:
- All modules pass validation hook
- All tests pass in each module
- CI/CD gate enables
- Zero violations on main branch

### Scenario 3: Large Project (30+ modules, 5000+ files) — 2-3 months

**Project**: Enterprise workflow engine with 30+ modules, 5000+ Java files.

**Current state audit**:
- 400+ violations across 80 files
- Breakdown:
  - H_TODO: 150 (38%)
  - H_MOCK: 80 (20%)
  - H_STUB: 100 (25%)
  - H_FALLBACK: 40 (10%)
  - H_LIE: 20 (5%)
  - Other: 10 (2%)

**Phased approach** (risk-based):

**Phase 1 (Week 1-2): Critical Violations (H_LIE, H_FALLBACK)**
- Team A: Fix H_LIE violations (20 violations, 40 hours)
- Team B: Fix H_FALLBACK violations (40 violations, 60 hours)
- Effort: 100 hours

**Phase 2 (Week 3-4): High-Impact Violations (H_MOCK, H_STUB)**
- Team A: Fix H_MOCK violations (80 violations, 80 hours)
- Team B: Fix H_STUB violations (100 violations, 100 hours)
- Effort: 180 hours

**Phase 3 (Week 5-6): Medium-Impact Violations (H_TODO)**
- Team A: Fix H_TODO violations (150 violations, 50 hours)
- Team B: Enable CI/CD gates and monitoring
- Effort: 50 hours

**Phase 4 (Week 7-8): Validation and Hardening**
- All teams: Final review, edge cases
- Deploy compliance dashboard
- Train team on standards

**Team**: 3-4 engineers + 1 lead

**Effort**: 330 hours (3-4 engineers × 8 weeks)

**Success criteria**:
- All modules at 100% compliance
- CI/CD gates block violations
- Compliance dashboard deployed
- Team trained on standards

---

## Tools and Automation

### Built-in Tools

#### 1. hyper-validate.sh

Core validation script that checks all 7 patterns:

```bash
bash .claude/hooks/hyper-validate.sh <file_or_directory>

# Exit codes:
# 0 = No violations
# 2 = Violations found
```

#### 2. ggen validate

Semantic validation for complex patterns (H_LIE detection):

```bash
ggen validate --phase guards --emit src/
```

#### 3. Custom Scanner Scripts

Create module-specific scanners:

```bash
scripts/compliance/audit-violations.sh        # Full codebase audit
scripts/compliance/fix-by-module.sh            # Fix one module
scripts/compliance/fix-by-pattern.sh           # Fix one pattern
scripts/compliance/metrics.sh                  # Generate metrics
scripts/compliance/trend-report.sh             # Show compliance trend
```

### Automation Scripts

#### Batch Fix Script (H_TODO)

```bash
#!/bin/bash
# scripts/compliance/batch-fix-todo.sh

find src/main/java -name "*.java" -exec grep -l "TODO\|FIXME" {} \; | while read file; do
  echo "Fixing TODOs in: $file"

  # Count TODOs
  count=$(grep -c "TODO\|FIXME" "$file")

  # Option 1: Replace with exception (safe)
  sed -i 's|// TODO:.*$|throw new UnsupportedOperationException("See implementation guide");|g' "$file"

  # Option 2: Remove if comment only (less safe)
  # sed -i '/^\s*\/\/ TODO:/d' "$file"
done

echo "All TODOs processed. Run validation:"
bash .claude/hooks/hyper-validate.sh src/main/java
```

#### Compliance Report Generator

```bash
#!/bin/bash
# scripts/compliance/report.sh

cat > .compliance/report.md <<'EOF'
# HYPER_STANDARDS Compliance Report

Generated: $(date)

## Summary

| Metric | Value |
|--------|-------|
| Total Files | $(find src/main/java -name "*.java" | wc -l) |
| Total Violations | $(find src/main/java -name "*.java" | xargs grep -c "TODO\|mock\|stub" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}') |
| Compliance Rate | $(echo "scale=1; 100 - (violations * 100 / files)" | bc)% |

## Violations by Pattern

EOF

for pattern in H_TODO H_MOCK H_STUB H_EMPTY H_FALLBACK H_SILENT; do
  count=$(find src/main/java -name "*.java" | xargs grep -c "$pattern" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  echo "| ${pattern} | ${count} |" >> .compliance/report.md
done

echo "Report: .compliance/report.md"
cat .compliance/report.md
```

### CI/CD Integration

#### GitHub Actions Workflow

```yaml
# .github/workflows/compliance-gate.yml
name: HYPER_STANDARDS Gate

on: [pull_request]

jobs:
  compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Run HYPER_STANDARDS validation
        run: |
          for file in $(git diff --name-only origin/main HEAD -- '*.java'); do
            bash .claude/hooks/hyper-validate.sh "$file"
          done

      - name: Generate compliance report
        run: bash scripts/compliance/report.sh

      - name: Upload report
        uses: actions/upload-artifact@v3
        with:
          name: compliance-report
          path: .compliance/report.md
```

#### GitLab CI Integration

```yaml
# .gitlab-ci.yml
compliance_gate:
  image: openjdk:21
  script:
    - for file in $(git diff --name-only origin/main HEAD -- '*.java'); do
        bash .claude/hooks/hyper-validate.sh "$file"
      done
  only:
    - merge_requests
```

---

## Troubleshooting Guide

### Issue 1: False Positive in H_TODO Detection

**Symptom**: Real TODO in comments (e.g., "TODO list data structure") flagged as violation.

**Root cause**: Regex pattern matches legitimate TODO words.

**Solution**:
Option 1 (Quick): Rename the word
```java
// Before: "TODO list implementation"
// After: "Pending list implementation" or "TOFIX list"
```

Option 2 (Correct): Add context to clarify
```java
// Prepare TODO list
// [Implement using custom data structure]
```

### Issue 2: Mock Test Utilities Can't Be Removed

**Symptom**: H_MOCK violation for test utilities (e.g., MockDataService in src/test).

**Root cause**: Mocks are technically forbidden, but test utilities are legitimate.

**Solution**:
Option 1 (Best): Move to src/test/
```
src/main/java/ProductionCode.java (no mocks)
src/test/java/MockDataService.java (mock allowed for testing)
```

Option 2 (If needed): Use interface-based design
```java
// Interface in production code
public interface DataProvider { Data getData(); }

// Real impl in production
public class ProductionDataProvider implements DataProvider { }

// Mock impl in test
public class MockDataProvider implements DataProvider { }
```

### Issue 3: H_EMPTY Violation Can't Be Fixed (Complex Implementation)

**Symptom**: Need to implement complex method but unsure how.

**Root cause**: Implementation requires investigation or new architecture.

**Solution**:
```java
public void complexFeature() {
    throw new UnsupportedOperationException(
        "This feature requires:\n" +
        "  1. Review design spec: docs/design/feature-x.md\n" +
        "  2. Study existing implementation: XClass.java:123\n" +
        "  3. Implement with proper error handling\n" +
        "  4. Add unit tests in ComplexFeatureTest.java\n" +
        "\n" +
        "Estimated effort: 4-6 hours\n" +
        "Dependencies: library-x, service-y"
    );
}
```

Then create a follow-up issue:
```
Title: Implement complexFeature in Module X
Description: See UnsupportedOperationException message for requirements
Effort: 4-6 hours
Priority: High (blocks compliance)
```

### Issue 4: H_FALLBACK Violations Complicate Error Handling

**Symptom**: Need to handle API errors gracefully but H_FALLBACK forbids fakes.

**Root cause**: Confusion between error handling and fake fallbacks.

**Solution** (Correct approach):
```java
// FORBIDDEN (silent fallback):
try {
    return api.fetch();
} catch (ApiException e) {
    return Collections.emptyList();  // LIES!
}

// CORRECT (fail fast):
try {
    return api.fetch();
} catch (ApiException e) {
    throw new RuntimeException("API fetch failed", e);
}

// ALSO CORRECT (optional with retry):
try {
    return api.fetch();
} catch (ApiException e) {
    if (retryCount < 3) {
        Thread.sleep(1000);
        return fetch(url, retryCount + 1);
    }
    throw new RuntimeException("API fetch failed after retries", e);
}
```

### Issue 5: Legacy Code with Many H_TODO Comments

**Symptom**: Module has 50+ TODO comments from years ago.

**Root cause**: Accumulated technical debt.

**Solution** (Triage approach):
1. For each TODO, ask:
   - Is this still relevant? (Yes/No)
   - Can it be implemented quickly? (< 1 hour)
   - Should it be deferred? (Create issue instead)

2. Actions:
   - **Still relevant + quick**: Implement immediately
   - **Still relevant + complex**: Replace with UnsupportedOperationException, create Issue
   - **No longer relevant**: Delete comment

### Issue 6: H_LIE Violations Are Hard to Detect

**Symptom**: Code semantically violates H_LIE but pattern matching doesn't catch it.

**Root cause**: H_LIE requires semantic/AI analysis.

**Solution**:
Run manual semantic check:
```bash
# Review Javadoc vs implementation
grep -A 10 "/**" src/main/java/YWorkItem.java \
  | grep -E "@return|@throws|Method name"

# Compare to actual method body
sed -n '100,150p' src/main/java/YWorkItem.java

# Ask: Does implementation match documentation?
# If NO: fix implementation or update docs
```

Use ggen for semantic validation:
```bash
ggen validate --phase invariants src/main/java
```

### Issue 7: CI/CD Gate Too Strict (Blocks Valid Code)

**Symptom**: Gate rejects code that should be allowed.

**Root cause**: Overly broad regex patterns.

**Solution**:
1. Review the specific violation
2. Adjust regex pattern (in hyper-validate.sh or config)
3. Re-test

Example: If "mock" keyword is legitimate in comments:
```bash
# Before: Catches all "mock" occurrences
grep -E "(mock|stub|fake)[A-Z]"

# After: Only catches mock method/class names
grep -E "(mock|stub|fake)[A-Z][a-zA-Z]*\s*(class|interface|=|\()"
```

---

## Configuration Reference

### HYPER_STANDARDS Configuration File

Create `.compliance/config.toml`:

```toml
[hyper_standards]
enabled = true
enforcement_level = "STRICT"  # STRICT (zero tolerance) or WARN (log only)

[patterns]
# Enable/disable individual patterns
h_todo = true
h_mock = true
h_stub = true
h_empty = true
h_fallback = true
h_silent = true
h_lie = true

[regex_patterns]
h_todo = '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE)'
h_mock = '(mock|stub|fake|test|demo)[A-Z][a-zA-Z]*\s*[=(]'
h_stub = 'return\s+"";|return\s+0;|return\s+null;'
h_empty = 'void\s+\w+\([^)]*\)\s*\{\s*\}'
h_fallback = 'catch.*return\s+(new|fake|")'
h_silent = 'log\.(warn|error)\(".*not\s+implemented'

[exceptions]
# Files/patterns to exclude from validation
exclude_files = [
  "**/Test*.java",
  "**/*Test.java",
  "src/test/**",
  ".claude/**"
]

exclude_patterns = [
  "// TODO: code review"  # Comments with context
]

[enforcement]
# Where to enforce
gate_phase = "H"  # GODSPEED phase
block_on_violation = true
exit_code_on_violation = 2

# CI/CD integration
ci_enabled = true
ci_block_pr = true
ci_comment_template = """
❌ PR contains HYPER_STANDARDS violations:
{violations}

See: https://docs.example.com/compliance
"""

[metrics]
enabled = true
report_dir = ".compliance/metrics"
retention_days = 90

[dashboard]
enabled = true
public_url = "https://example.com/compliance"
update_frequency = "1h"
```

### Load Configuration

```bash
#!/bin/bash
# scripts/compliance/load-config.sh

CONFIG_FILE="${1:-.compliance/config.toml}"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Config file not found: $CONFIG_FILE"
    exit 1
fi

# Parse TOML and set environment variables
export HYPER_STRICT=$(grep -A 1 "enforcement_level" "$CONFIG_FILE" | tail -1 | cut -d'"' -f2)
export HYPER_GATE=$(grep -A 1 "gate_phase" "$CONFIG_FILE" | tail -1 | cut -d'"' -f2)

echo "Loaded HYPER_STANDARDS configuration from: $CONFIG_FILE"
echo "Enforcement level: $HYPER_STRICT"
echo "Gate phase: $HYPER_GATE"
```

### Per-Module Configuration

Override defaults for specific modules:

```toml
# yawl-engine/.compliance/config.toml
[overrides]
# Engine module is critical - extra strict
enforcement_level = "MAXIMUM"
block_on_violation = true

# But allow test mocks in src/test
[patterns.h_mock]
exclude_patterns = ["src/test/**"]
```

---

## Measuring Compliance Progress

### Key Metrics

Track these metrics regularly:

```
1. Violation Count Trend
   - Total violations (should ↓)
   - Violations per module (should ↓)
   - Violations per engineer (should ↓)

2. Compliance Rate (%)
   - Target: 100%
   - Formula: (Files - FilesWithViolations) / Files × 100

3. Closure Rate
   - Violations closed per week (should ↑)
   - Average time to fix (should ↓)

4. Distribution by Pattern
   - Identify which patterns are recurring
   - Focus resources on top patterns

5. Module Compliance Ranking
   - Which modules are 100% compliant?
   - Which modules need most work?
```

### Dashboard Metrics Query

```bash
#!/bin/bash
# scripts/compliance/dashboard-metrics.sh

echo "COMPLIANCE METRICS DASHBOARD"
echo "============================"
echo ""

echo "1. OVERALL COMPLIANCE"
TOTAL=$(find src/main/java -name "*.java" | wc -l)
VIOLATIONS=$(find src/main/java -name "*.java" | xargs grep -c "TODO\|mock\|stub" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
COMPLIANCE=$((100 - (VIOLATIONS * 100 / TOTAL)))
echo "   Files: ${TOTAL}"
echo "   Violations: ${VIOLATIONS}"
echo "   Compliance: ${COMPLIANCE}%"
echo ""

echo "2. VIOLATIONS BY PATTERN"
for pattern in H_TODO H_MOCK H_STUB H_EMPTY H_FALLBACK H_SILENT H_LIE; do
  count=$(find src/main/java -name "*.java" | xargs grep -c "$pattern" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  pct=$((count * 100 / VIOLATIONS))
  echo "   ${pattern}: ${count} (${pct}%)"
done
echo ""

echo "3. MODULE COMPLIANCE"
for module_dir in yawl/*/src/main/java; do
  module=$(echo "$module_dir" | cut -d/ -f2)
  count=$(find "$module_dir" -name "*.java" | xargs grep -c "TODO\|mock" 2>/dev/null | awk -F: '{sum+=$2} END {print sum+0}')
  if [ $count -gt 0 ]; then
    echo "   ${module}: ${count} violations"
  else
    echo "   ${module}: CLEAN ✓"
  fi
done
echo ""

echo "4. TREND (Week over Week)"
# Compare to 7 days ago
PREV=$(ls -t .compliance/metrics/metrics_*.json | tail -n +8 | head -1)
CURRENT=$(ls -t .compliance/metrics/metrics_*.json | head -1)

if [ -n "$PREV" ] && [ -n "$CURRENT" ]; then
  PREV_VIOLATIONS=$(jq -r '.violations.total // 0' "$PREV")
  CURR_VIOLATIONS=$(jq -r '.violations.total // 0' "$CURRENT")
  CHANGE=$((PREV_VIOLATIONS - CURR_VIOLATIONS))
  PCTCHANGE=$((CHANGE * 100 / PREV_VIOLATIONS))

  if [ $CHANGE -gt 0 ]; then
    echo "   Violations reduced: ${CHANGE} (-${PCTCHANGE}%) ✓"
  else
    echo "   Violations increased: ${CHANGE} (+${PCTCHANGE}%) ✗"
  fi
fi
```

---

## Summary: Compliance Checklist

Use this checklist to track progress:

### Phase 1: Audit
- [ ] Run comprehensive audit
- [ ] Generate audit receipt
- [ ] Analyze by pattern, module, severity
- [ ] Share results with team

### Phase 2: Plan
- [ ] Create remediation plan
- [ ] Prioritize violations
- [ ] Assign ownership
- [ ] Estimate effort and timeline

### Phase 3: Fix
- [ ] Create fix branches
- [ ] Fix violations by module/pattern/team
- [ ] Run tests after each fix
- [ ] Get code review
- [ ] Merge when green

### Phase 4: Automate
- [ ] Enable local validation hook
- [ ] Add pre-commit hook
- [ ] Configure CI/CD gate
- [ ] Set up alerting

### Phase 5: Monitor
- [ ] Generate daily metrics
- [ ] Set up compliance dashboard
- [ ] Configure trend alerts
- [ ] Train team on standards

### Ongoing
- [ ] Zero violations on main branch
- [ ] All PRs pass compliance gate
- [ ] Metrics trending toward 100%
- [ ] Team practices HYPER_STANDARDS by default

---

## Next Steps

1. **Start with Phase 1**: Run `bash scripts/compliance/audit-violations.sh` to understand current state
2. **Create plan**: Use templates in Phase 2 to create your remediation plan
3. **Assign team**: Distribute work across engineers
4. **Enable gates**: Deploy validation in CI/CD before fixing
5. **Fix systematically**: Use batch strategies from Phase 3
6. **Monitor continuously**: Set up dashboard from Phase 5

For more information, see:
- [HYPER_STANDARDS Pattern Reference](../reference/hyper-standards.md)
- [CLAUDE.md](../../../CLAUDE.md) — Root standards document
- [H-GUARDS Implementation Guide](./../../../.claude/rules/validation-phases/H-GUARDS-IMPLEMENTATION.md)

---

**Document version**: 1.0
**Last updated**: 2026-02-28
**Status**: READY FOR USE
**Maintenance**: Update quarterly with lessons learned
