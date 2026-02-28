# YAWL Parallelization Documentation Suite

**Complete documentation for parallel test execution in YAWL v6.0.0**

**Status**: Production Ready
**Last Updated**: February 28, 2026
**Location**: `/home/user/yawl/.claude/guides/`

---

## Quick Navigation

### I want to...

| Goal | Start Here | Time |
|------|-----------|------|
| Run tests fast right now | [`QUICK-START-PARALLEL-TESTS.md`](#1-quick-startparalleltestsmd) | 1 min |
| Choose the right profile | [`PROFILE-SELECTION-GUIDE.md`](#4-profile-selectionguidemd) | 2 min |
| Understand how it works | [`DEVELOPER-GUIDE-PARALLELIZATION.md`](#2-developer-guideparallelizationmd) | 15 min |
| Fix a failing test | [`TROUBLESHOOTING-GUIDE.md`](#5-troubleshootingguidemd) | 5-10 min |
| Configure my build | [`BUILD-TUNING-REFERENCE.md`](#3-build-tuningrefencemd) | 10 min |
| Find answers to my questions | [`FAQ.md`](#6-faqmd) | 5 min |
| See the technical proof | `PHASE3-CONSOLIDATION.md` (in parent dir) | 20 min |

---

## Document Overview

### 1. QUICK-START-PARALLEL-TESTS.md

**What**: Copy-paste commands to get started immediately

**Size**: 357 lines (2-3 pages)

**Best for**:
- Getting started in <1 minute
- Seeing expected output examples
- Quick copy-paste commands
- Understanding when to use it

**Contents**:
- TL;DR command
- 30-second overview
- Copy-paste commands (6 variants)
- Expected output examples
- When to use (decision matrix)
- Quick troubleshooting
- CI/CD examples
- FAQ (6 questions)

**Read if**: You want to run `mvn verify -P integration-parallel` and understand what's happening

**Reading time**: 1-2 minutes

---

### 2. DEVELOPER-GUIDE-PARALLELIZATION.md

**What**: Comprehensive technical reference (production-grade documentation)

**Size**: 1,038 lines (15 pages)

**Best for**:
- Understanding parallelization in depth
- Learning how it works technically
- Complete configuration reference
- In-depth troubleshooting

**Contents**:
- Executive summary with metrics
- What is parallelization (3 sections)
- How to enable it (step-by-step)
- Configuration options (detailed)
- Troubleshooting (7 common issues)
- FAQ (10 questions)
- Performance tuning (techniques + benchmarks)
- Backward compatibility guarantees

**Sections**:
1. Executive Summary
2. What is Parallelization
   - The problem
   - The solution
   - Technical details
   - Architecture diagram
   - Safety guarantees
3. How to Enable It
   - Quick start
   - Step-by-step setup
   - Integration with dev workflow
   - Using with CI/CD
   - Using with IDE
4. Configuration Options
   - Profile selection
   - Maven properties
   - JUnit platform config
   - Test timeouts
5. Troubleshooting
   - Issue 1: Tests fail in parallel (deep dive)
   - Issue 2: OOM errors (solutions)
   - Issue 3: Timeouts (root causes + fixes)
   - Issue 4: Hangs/deadlocks
   - Issue 5: Flaky tests
   - Issue 6: Slower than expected
   - Issue 7: Thread interrupt errors
6. FAQ (10 questions)
7. Performance Tuning
   - Measuring performance
   - Optimization techniques
   - Benchmarking results
   - Hardware-specific tuning

**Read if**: You want to understand the complete picture and troubleshoot complex issues

**Reading time**: 15-20 minutes

---

### 3. BUILD-TUNING-REFERENCE.md

**What**: Complete reference for all Maven profiles and configuration

**Size**: 707 lines (8 pages)

**Best for**:
- Understanding all available profiles
- When to use each profile
- Performance expectations
- Configuration deep-dive

**Contents**:
- Profile overview (8 profiles)
- When to use each (detailed per-profile sections)
- Profile details (properties matrix)
- Performance expectations (by profile, by hardware)
- Common issues and solutions
- Decision tree (text-based flowchart)

**Profiles covered**:
1. `quick-test` — Dev loop (10s)
2. `agent-dx` — Agent development (10s)
3. `fast-verify` — Timing analysis (12s)
4. `integration-parallel` — Fast comprehensive (85s)
5. `ci` — CI/CD with coverage (120s)
6. `prod` — Production release (150s+)
7. `java25` — Java version config
8. *(default)* — Sequential safe mode (150s)

**Read if**: You need detailed information about profiles, when to use them, and how to configure them

**Reading time**: 10-15 minutes

---

### 4. PROFILE-SELECTION-GUIDE.md

**What**: Quick decision guide for choosing the right profile

**Size**: 610 lines (4 pages)

**Best for**:
- Making quick decisions
- Understanding common workflows
- Setting up IDE integration
- Creating shell aliases

**Contents**:
- Quick decision matrix (1-row per profile)
- Detailed profiles (50-100 lines each)
  - quick-test
  - agent-dx
  - fast-verify
  - integration-parallel
  - ci
  - prod
  - default
- Profile comparison table
- Common workflows (4 detailed examples)
- Decision flowchart
- Environment setup (bash aliases, IDE config)
- Troubleshooting profile selection

**Read if**: You need to decide which profile to use for your current task

**Reading time**: 2-5 minutes (to find your scenario)

---

### 5. TROUBLESHOOTING-GUIDE.md

**What**: Diagnostic and fix guide for common issues

**Size**: 1,006 lines (5 pages)

**Best for**:
- Fixing failing tests
- Debugging performance issues
- Solving memory problems
- Resolving timeouts

**Issues covered**:
1. Tests fail in parallel but pass sequentially
2. Out of Memory (OOM) errors
3. Builds are slower than expected
4. Tests timeout during parallel execution
5. Flaky tests (intermittent failures)
6. State corruption or deadlocks
7. Thread interrupt errors
8. Database lock errors
9. Profile not activating
10. Coverage reports missing

**Structure for each issue**:
1. Symptom (what you see)
2. Root cause analysis
3. Diagnosis steps (commands to run)
4. Solutions (multiple approaches)
5. Verification (how to confirm fix)

**Read if**: Your build is broken or misbehaving

**Reading time**: 5-30 minutes (depends on issue complexity)

---

### 6. FAQ.md

**What**: Frequently asked questions with concise answers

**Size**: 722 lines (4 pages)

**Best for**:
- Quick answers to common questions
- Understanding the "why"
- Learning technical details
- Understanding ROI and migration path

**Question categories**:
- General (10 questions)
  - Is it safe?
  - Will tests break?
  - What's the speedup?
  - Do I have to enable it?
  - What about CI/CD?
  - Can I run all tests in parallel?
  - What's expected speedup on my machine?
  - What Java version?
  - Can I customize settings?
  - What if it doesn't work?

- Technical (5 questions)
  - How does thread-local isolation work?
  - Fork vs threadCount?
  - Why process isolation?
  - reuseForks explanation
  - Parallelization + JaCoCo?

- Operational (5 questions)
  - How do I know if it's active?
  - What if a test fails?
  - How to measure performance?
  - Does it affect IDE?
  - How to revert?

- Migration (2 questions)
  - Should I enable immediately?
  - How to gradually adopt?

- Performance (3 questions)
  - Cost of process isolation?
  - Can I optimize further?
  - What's the ROI?

- Support (2 questions)
  - Where's detailed documentation?
  - What if I have more questions?

**Read if**: You have a quick question and want a concise answer

**Reading time**: 1-5 minutes (depends on question)

---

## Reading Paths

### Path 1: "Just Get Started" (5 minutes)

1. [`QUICK-START-PARALLEL-TESTS.md`](#1-quick-startparalleltestsmd) (1 min)
   - Run: `mvn verify -P integration-parallel`
   - Done

### Path 2: "Understand and Use" (15 minutes)

1. [`QUICK-START-PARALLEL-TESTS.md`](#1-quick-startparalleltestsmd) (1 min)
2. [`PROFILE-SELECTION-GUIDE.md`](#4-profile-selectionguidemd) (2 min)
3. [`DEVELOPER-GUIDE-PARALLELIZATION.md`](#2-developer-guideparallelizationmd) sections 1-3 (10 min)

### Path 3: "Complete Mastery" (40 minutes)

1. [`QUICK-START-PARALLEL-TESTS.md`](#1-quick-startparalleltestsmd) (1 min)
2. [`PROFILE-SELECTION-GUIDE.md`](#4-profile-selectionguidemd) (5 min)
3. [`DEVELOPER-GUIDE-PARALLELIZATION.md`](#2-developer-guideparallelizationmd) (15 min)
4. [`BUILD-TUNING-REFERENCE.md`](#3-build-tuningrefencemd) (10 min)
5. [`FAQ.md`](#6-faqmd) (5 min)

### Path 4: "Troubleshooting" (Variable)

1. [`TROUBLESHOOTING-GUIDE.md`](#5-troubleshootingguidemd) (find your issue)
2. Follow diagnosis steps
3. Apply solution
4. Verify fix

---

## Document Statistics

| Document | Lines | Pages | Topics |
|----------|-------|-------|--------|
| QUICK-START | 357 | 2-3 | 6 commands, 2 examples, 6 FAQ |
| DEVELOPER-GUIDE | 1,038 | 15 | Complete reference |
| BUILD-TUNING | 707 | 8 | 8 profiles, config details |
| PROFILE-SELECTION | 610 | 4 | Profile comparison, workflows |
| TROUBLESHOOTING | 1,006 | 5 | 10 issues, diagnosis, solutions |
| FAQ | 722 | 4 | 25 questions, 4 categories |
| **Total** | **4,440** | **38-40** | **Comprehensive suite** |

---

## Key Facts

**Performance**:
- Sequential baseline: 150.5 seconds
- Parallel optimized: 84.86 seconds
- Speedup: **1.77x (43.6% improvement)**

**Safety**:
- Test pass rate: 100%
- State corruption risk: <0.1% (VERY LOW)
- Code changes required: 0

**Adoption**:
- Activation: One flag `-P integration-parallel`
- Rollback: Remove the flag
- Complexity: Low (opt-in, no breaking changes)

**Hardware**:
- Min requirement: Java 25, 4GB RAM, 2 cores
- Recommended: 4+ cores, 8GB RAM
- Optimal: 8+ cores, 16GB RAM

---

## Quick Reference: One-Liners

```bash
# Get instant feedback (unit tests only)
mvn test -P quick-test

# Full comprehensive test (unit + integration)
mvn verify -P integration-parallel

# With timing metrics
DX_TIMINGS=1 bash scripts/dx.sh all

# Agent development (auto-detects changes)
bash scripts/dx.sh

# CI/CD pipeline
mvn clean verify -P ci

# Production release
NVD_API_KEY=xxx mvn clean verify -P prod
```

---

## Finding Answers

**Question Type** → **Document**

| Question | Document |
|----------|----------|
| How do I run tests fast? | QUICK-START |
| Which profile should I use? | PROFILE-SELECTION |
| Is it safe? | FAQ |
| How does it work? | DEVELOPER-GUIDE |
| My tests are failing | TROUBLESHOOTING |
| I want all the details | BUILD-TUNING + DEVELOPER-GUIDE |
| What's the ROI? | FAQ |
| How to troubleshoot OOM? | TROUBLESHOOTING |
| How to configure for my machine? | BUILD-TUNING |
| Quick answers to common questions? | FAQ |

---

## Related Documents

**Phase 3 Technical Report** (in parent directory):
- `/home/user/yawl/.claude/PHASE3-CONSOLIDATION.md`
- Complete technical validation, benchmarks, design documents
- 5-agent team deliverables
- 897 lines of safety test code

**Build Configuration**:
- `pom.xml` — Maven profiles (search for "integration-parallel")
- `scripts/dx.sh` — Developer workflow script
- `.mvn/maven.config` — JVM options
- `test/resources/junit-platform.properties` — JUnit 5 config

---

## Version History

**v1.0** (February 28, 2026):
- Initial comprehensive documentation suite
- 6 detailed guides (4,440 lines total)
- Production ready
- All profiles documented
- Complete troubleshooting coverage

---

## Document Maintenance

Last updated: **February 28, 2026**

All documents are:
- ✓ Current with YAWL v6.0.0
- ✓ Tested with real examples
- ✓ Production-grade quality
- ✓ Comprehensive and cross-referenced

---

## Support

### If you can't find the answer:

1. **Check Quick Start** (2 min) → `QUICK-START-PARALLEL-TESTS.md`
2. **Check Profile Selection** (2 min) → `PROFILE-SELECTION-GUIDE.md`
3. **Check FAQ** (5 min) → `FAQ.md`
4. **Check Troubleshooting** (10 min) → `TROUBLESHOOTING-GUIDE.md`
5. **Check Developer Guide** (15 min) → `DEVELOPER-GUIDE-PARALLELIZATION.md`
6. **Check Build Tuning** (10 min) → `BUILD-TUNING-REFERENCE.md`

### If you still need help:

```bash
# Get verbose output
mvn verify -P integration-parallel -X 2>&1 | tee debug.log

# Check for system issues
free -h           # Memory
nproc             # CPU cores
java -version     # Java version

# Look at build configuration
grep -A 50 "integration-parallel" pom.xml
```

---

## Acknowledgments

Comprehensive documentation created as part of **Phase 4: Final Validation & Documentation** for YAWL v6.0.0 parallelization feature.

Based on:
- Phase 3 technical implementation and validation
- Real-world testing with 256+ tests
- Production deployment patterns
- Developer feedback from YAWL team

---

**Start here**: [`QUICK-START-PARALLEL-TESTS.md`](#1-quick-startparalleltestsmd)

**Command to run**: `mvn clean verify -P integration-parallel`

**Expected time**: 85 seconds (1.77x faster)

**Status**: Production ready
