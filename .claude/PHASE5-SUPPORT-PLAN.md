# PHASE 5: Support & Escalation Plan — YAWL v6.0.0 Parallelization

**Date**: 2026-02-28  
**Status**: PRODUCTION SUPPORT READINESS  
**Branch**: `claude/launch-agents-build-review-qkDBE`

---

## Executive Summary

This document defines the support structure, escalation procedures, and troubleshooting guides for YAWL v6.0.0 parallelization rollout. It ensures developers have clear pathways to get help and that issues are resolved quickly.

---

## Support Structure

### Support Team

| Role | Person | Hours | Contact | Escalation |
|------|--------|-------|---------|-----------|
| **Primary Support** | Build Engineer | 9-5 M-F | Slack #yawl-builds | Tech Lead after hours |
| **Secondary Support** | Tech Lead | 9-5 M-F | Slack #yawl-platform | Release Manager |
| **On-Call (24/7)** | DevOps/SRE | Always | PagerDuty | System Admin |
| **Release Manager** | TBD | 9-5 M-F | Email + Slack | CTO on escalation |

### Support Channels

| Channel | Response Time | Use For | Example |
|---------|---|---|---|
| **Slack #yawl-builds** | <30 min | Quick questions, build failures | "Why is my build taking 2 min?" |
| **Slack DM (Build Eng)** | <15 min | Urgent blockers | "Build is hanging, need help now" |
| **GitHub Issues** | <4 hours | Bugs, feature requests | Create issue with logs + reproduction |
| **Email** | <24 hours | Non-urgent, documentation | Feedback on documentation |
| **Emergency (PagerDuty)** | <5 min | Prod down, builds failing for >1 hour | "Parallel profile broken in CI/CD" |

---

## FAQ: Common Issues & Solutions

### Q1: My builds are taking longer than expected. Help?

**Symptoms**: 
- Sequential: 150s → Parallel: 110-120s (only ~20% faster, expected ~40%)
- Frustration: "Parallel isn't working"

**Root Causes**:
1. System is already under high load (other processes running)
2. IDE/network I/O is bottlenecked
3. Parallel profile not active (default is sequential)

**Solution**:
```bash
# 1. Verify parallel profile is active
mvn help:active-profiles -P integration-parallel | grep integration-parallel
# Expected output: integration-parallel

# 2. Check system resources
top  # Look for CPU and memory usage
# Expected: <60% CPU available for Maven

# 3. Close unnecessary IDE windows/tabs
# IDE indexing can interfere with Maven builds

# 4. Try again with verbose output
mvn clean verify -P integration-parallel -X 2>&1 | tail -100
# Look for: "Forkmode: "perClass", forkCount: 2"

# 5. Expected time: 85-95 seconds
# If still slow, contact build engineer
```

**When to Escalate**: If parallel is active but builds still take >120s, contact build engineer.

---

### Q2: Some tests are failing in parallel mode but pass in sequential. Why?

**Symptoms**:
- Test X passes with `mvn verify`
- Test X fails with `mvn verify -P integration-parallel`
- Intermittent failures (not 100% reproducible)

**Root Causes**:
1. Test has hidden dependency on execution order
2. Shared state between tests (static variables, singletons)
3. Port/resource contention in parallel forks
4. Timing-sensitive test (race condition)

**Solution**:
```bash
# 1. Run test in isolation (sequential)
mvn clean verify -Dit.test=YourTestName
# If passes: indicates ordering/state dependency

# 2. Run test multiple times in parallel
for i in {1..5}; do 
  mvn clean verify -P integration-parallel -Dit.test=YourTestName
done
# If sometimes passes: indicates flakiness

# 3. Check test for common issues
# Search test file for:
#   - Static variables
#   - Hardcoded ports
#   - Shared resources
#   - Thread sleeps / timing assumptions

# 4. If you find the issue, fix it:
#   - Use @BeforeEach / @AfterEach instead of static state
#   - Use dynamic port allocation
#   - Use proper synchronization

# 5. Run full integration test suite
mvn clean verify -P integration-parallel
# All 332 tests should pass
```

**When to Escalate**: If test is part of core suite and you can't fix it, escalate with reproduction steps.

---

### Q3: "Cannot allocate memory" error during parallel build

**Symptoms**:
```
[ERROR] Java heap space
[ERROR] OutOfMemoryError: Java heap space
[INFO] BUILD FAILURE
```

**Root Causes**:
1. JVM heap too small for parallel execution
2. Too many parallel forks running simultaneously
3. Memory leak in test code
4. System doesn't have enough available memory

**Solution**:
```bash
# 1. Check available system memory
free -h
# Expected: at least 8GB available for build

# 2. Increase JVM heap
export MAVEN_OPTS="-Xmx4G -XX:+UseG1GC"
mvn clean verify -P integration-parallel
# Increase -Xmx value if still fails

# 3. Reduce parallel forks (temporary)
mvn clean verify -P integration-parallel -Dfailsafe.forkCount=1
# Note: This reverts to sequential integration tests

# 4. Run sequential mode (safe fallback)
mvn clean verify
# No memory increase needed, slower but reliable

# 5. Check for memory leaks (if persistent)
mvn clean verify -P integration-parallel -verbose -X
# Look for: GC activity, heap growth
```

**When to Escalate**: If you have 16GB+ available and still getting OOM, contact build engineer.

---

### Q4: Specific tests timeout in parallel but not sequential

**Symptoms**:
```
[ERROR] Test execution timeout: YourTestName exceeded 120s
[ERROR] BUILD FAILURE
```

**Root Causes**:
1. Test is slow and gets stuck behind other tests
2. Parallel execution adds overhead (fork startup, JVM warm-up)
3. Test has thread waits that don't work well in parallel
4. System is resource-constrained

**Solution**:
```bash
# 1. Check test execution time (sequential)
mvn clean verify -Dit.test=YourTestName
# Note actual time taken

# 2. If test takes >100s: may timeout in parallel
#    Solution: Optimize test or increase timeout

# 3. Check timeout settings
grep -r "timeout" pom.xml | grep -i failsafe
# Expected: 180s method timeout, 120s integration timeout

# 4. If timeout is already generous, check for:
#    - Thread.sleep() calls
#    - Locking/deadlocks
#    - Resource contention

# 5. Temporary fix: increase timeout (not recommended)
mvn clean verify -P integration-parallel -Dintegration.test.timeout.method=300s

# 6. Permanent fix: optimize test code
#    - Remove unnecessary sleep
#    - Use CountDownLatch instead of polling
#    - Use proper synchronization
```

**When to Escalate**: If timeout is genuine (test needs >180s), contact tech lead for decision.

---

### Q5: IDE (IntelliJ/VS Code) not recognizing parallel profile

**Symptoms**:
- Can run `mvn -P integration-parallel` from terminal
- IntelliJ shows green checkmark for default profile only
- VS Code Maven extension doesn't recognize profile

**Root Causes**:
1. IDE maven settings don't include profile
2. IDE cached build configuration
3. Profile not activated in IDE settings

**Solution**:

**IntelliJ IDEA**:
```
1. Go to: Preferences > Build, Execution, Deployment > Build Tools > Maven
2. In "Profiles", check: "integration-parallel"
3. Click "Apply" + "OK"
4. Right-click project > Maven > Reimport
5. Try: Right-click test > Run with Maven > integration-parallel profile
```

**VS Code**:
```
1. Install Maven extension (if not present)
2. Open command palette: Ctrl+Shift+P
3. Type: "Maven: Execute command"
4. Enter: mvn clean verify -P integration-parallel
5. Or: Create .vscode/settings.json:
   {
     "maven.profiles": ["integration-parallel"]
   }
```

**Both IDEs**:
```bash
# From terminal: Manually run Maven
mvn clean verify -P integration-parallel
# IDE will eventually catch up after 1-2 builds
```

**When to Escalate**: If IDE still doesn't recognize after steps above, contact build engineer with IDE version.

---

### Q6: Parallel profile works locally but not in CI/CD

**Symptoms**:
- Local: `mvn -P integration-parallel` → All tests pass (85s)
- CI/CD: Same command → Some tests fail (timeout)
- Inconsistent: Sometimes passes, sometimes fails

**Root Causes**:
1. CI/CD has fewer resources (less CPU/memory)
2. CI/CD system is overloaded
3. Different Java version in CI vs local
4. Network/I/O bottleneck in CI environment

**Solution**:
```bash
# 1. Check CI/CD resource constraints
# In GitHub Actions: Check runner specs
# In Jenkins: Check agent/executor resource limits

# 2. Replicate CI/CD environment locally
# Run inside Docker with same specs:
docker run -cpus=2 -m=4g ubuntu:22.04
# Then run mvn -P integration-parallel

# 3. If tests fail in constrained environment:
#    Check parallel fork count
mvn help:active-profiles -P integration-parallel
# Look for: failsafe.forkCount=2C

# 4. Reduce forkCount for CI/CD (safer)
# In GitHub Actions workflow:
#   mvn clean verify -P integration-parallel -Dfailsafe.forkCount=1C

# 5. Or: Disable parallel profile in CI/CD (for now)
#   Use sequential until resource issue resolved
```

**When to Escalate**: If environment is correctly specified but tests still fail, escalate to DevOps.

---

### Q7: How do I know if I'm using parallel profile correctly?

**Symptoms**:
- Uncertainty: "Am I actually running parallel?"
- Verification needed: Want to confirm profile is active

**Solution**:
```bash
# 1. Check profile is active
mvn help:active-profiles -P integration-parallel
# Should list: integration-parallel, default (or similar)

# 2. Check forkCount setting
mvn help:describe-mojo -Dplugin=org.apache.maven.plugins:maven-failsafe-plugin \
  | grep forkCount
# Should show: 2C (or whatever configured)

# 3. Look at build output for sign
mvn clean verify -P integration-parallel 2>&1 | grep -i fork
# Expected: "[INFO] ... forking ..." messages

# 4. Run with verbose output
mvn clean verify -P integration-parallel -X 2>&1 | tail -50
# Look for: "forkCount: 2" and "parallel execution"

# 5. Time-based check
# Sequential: ~150s
# Parallel: ~85s
# If you see ~85s: you're using parallel!
```

**When to Escalate**: If you confirm profile is active but behavior is still wrong, contact build engineer.

---

## Troubleshooting Decision Tree

```
Build/Test Issue Detected
  ↓
Is parallel profile active?
  ├─ NO
  │  └─ Activate: mvn -P integration-parallel
  │     Then re-run
  ├─ YES
  │  └─ Identify issue type:
  │     ├─ SLOW (takes >120s)
  │     │  └─ See Q1: Performance issue → Optimize environment
  │     ├─ FAILING (tests fail in parallel only)
  │     │  └─ See Q2: Test isolation → Fix test code or report
  │     ├─ TIMEOUT (specific test hangs)
  │     │  └─ See Q4: Timeout → Optimize test or increase timeout
  │     ├─ MEMORY (OutOfMemory error)
  │     │  └─ See Q3: Memory → Increase JVM heap
  │     ├─ IDE (IDE doesn't recognize)
  │     │  └─ See Q5: IDE setup → Reconfigure IDE
  │     └─ CI/CD (works locally, fails in CI)
  │        └─ See Q6: CI/CD environment → Replicate + reduce parallelism

Still Stuck?
  └─ Escalate to build engineer via Slack
     Provide: command run, output, system info
     Attach: mvn -X output (if <1MB), test failure log
```

---

## Known Limitations

### Limitation 1: Parallel Profile is Opt-In (Not Default)

**Why**: Conservative approach, preserves backward compatibility

**Impact**: 
- Developers must explicitly use `-P integration-parallel`
- Default behavior is sequential (slower but safe)

**Workaround**:
```bash
# Add to shell profile (.bashrc, .zshrc):
alias mvn-parallel='mvn -P integration-parallel'

# Then use:
mvn-parallel clean verify
# Much shorter!
```

---

### Limitation 2: Some Slow Tests May Timeout

**Why**: Parallel execution adds fork overhead; slow tests may not adapt well

**Impact**:
- Tests taking >100s in sequential may timeout in parallel
- Requires optimization or timeout increase

**Workaround**:
```bash
# Increase timeout temporarily
mvn -P integration-parallel \
  -Dintegration.test.timeout.default=180s \
  -Dintegration.test.timeout.method=300s
```

---

### Limitation 3: Limited to 2 Parallel Forks (2C)

**Why**: Safety first; more forks risk resource contention

**Impact**: 
- Not using full CPU power on 4+ core systems
- Max speedup ~1.8x vs 2x ideal

**Workaround**:
```bash
# Increase forks (if system has >8GB RAM + 4+ cores)
mvn -P integration-parallel -Dfailsafe.forkCount=3C
# But monitor for OOM errors!
```

---

## Escalation Procedures

### Level 1: Self-Service (Try First)
1. Check FAQ for your symptom
2. Follow troubleshooting steps
3. Run diagnostic commands
4. If resolved: mark as solved and move on

---

### Level 2: Build Engineer (Most Issues)
**When**: Issue not resolved after FAQ + troubleshooting

**How to Report**:
```
Slack message to #yawl-builds:

Title: "Issue: [Short description]"

Details:
- What are you trying to do?
- What did you expect?
- What actually happened?
- Error message (paste full log if possible)
- Command you ran:
  mvn clean verify -P integration-parallel -X
- System info:
  - OS: (macOS, Linux, Windows)
  - Java: java -version
  - Maven: mvn -version
  - RAM: (e.g., 16GB)
  - Cores: (e.g., 8 cores)

Logs (if helpful):
- Paste last 50 lines of error output
- Or attach full log file
```

**Expected Response**: <30 min during business hours

---

### Level 3: Tech Lead (Complex Issues)
**When**: Build engineer needs guidance or architectural decision needed

**When to Escalate from Build Engineer**:
- Issue affects core build system
- Fix requires code changes
- Performance characteristics changed
- Need to adjust parallelism strategy

**How**: Build engineer escalates via Slack + scheduled sync

**Expected Response**: <4 hours

---

### Level 4: Release Manager / CTO (Prod Issues)
**When**: Production build is failing, production release blocked

**How**:
1. Build engineer or tech lead declares incident
2. PagerDuty alert triggered
3. Emergency team assembled
4. Incident resolved or rollback performed

**Expected Response**: <15 min

---

## Post-Resolution Procedure

### For Each Issue Resolved

1. **Document the Fix**
   - Update FAQ with new issue + solution
   - If code fix: add comment explaining why needed

2. **Root Cause Analysis** (if serious)
   - What caused the issue?
   - Could it have been prevented?
   - Any systemic improvements needed?

3. **Update Troubleshooting Guide**
   - Add new decision tree node if pattern repeats
   - Update FAQ if similar issues likely

4. **Communicate**
   - Post summary in Slack #yawl-builds
   - Share updated FAQ with team
   - Note for next Phase 5 review

---

## Monitoring & Proactive Support

### Daily Checks
- [ ] Monitor Slack #yawl-builds for new issues
- [ ] Check GitHub Actions for any build failures
- [ ] Review CI/CD metrics dashboard
- [ ] Respond to questions within 30 min

### Weekly Review
- [ ] Aggregate issues from past week
- [ ] Identify trends or patterns
- [ ] Update FAQ with common questions
- [ ] Prepare summary for team standup

### Monthly Review
- [ ] Analyze 30-day support metrics
- [ ] Identify systemic issues
- [ ] Recommend pom.xml or process improvements
- [ ] Plan next phase optimizations

---

## Support Metrics & SLA

### Service Level Agreements (SLA)

| Severity | Response Time | Resolution Target | Example |
|----------|---|---|---|
| **Critical (P1)** | <15 min | <1 hour | Builds broken for everyone |
| **High (P2)** | <30 min | <4 hours | Parallel profile broken, team blocked |
| **Medium (P3)** | <2 hours | <24 hours | Single developer can't use parallel |
| **Low (P4)** | <24 hours | <1 week | Question about profile, FAQ clarification |

### Measurement

```json
{
  "week": "2026-03-03",
  "metrics": {
    "total_issues": 5,
    "critical": 0,
    "high": 1,
    "medium": 2,
    "low": 2,
    "avg_response_time_min": 22,
    "avg_resolution_time_hours": 2.5,
    "sla_compliance_pct": 100
  }
}
```

---

## Support Resources

### Documentation
- `.claude/PHASE5-PRODUCTION-READINESS.md` — Deployment validation
- `.claude/PHASE5-DEPLOYMENT-PLAN.md` — Rollout strategy
- `.claude/PERFORMANCE-BASELINE.md` — Performance metrics
- `README.md` — Quick-start guide (to be created)

### Scripts
- `mvn-parallel` (alias) — Run parallel: `mvn -P integration-parallel`
- `scripts/collect-build-metrics.sh` — Performance monitoring
- `scripts/monitor-build-performance.sh` — Regression detection

### Communication Channels
- **Slack**: #yawl-builds (real-time questions)
- **GitHub Issues**: For bugs and feature requests
- **Email**: For non-urgent feedback
- **Office Hours**: TBD (weekly support session)

---

## Sign-Off

### Prepared By
- **Engineer**: Claude Code (YAWL Build Optimization Team)
- **Date**: 2026-02-28
- **Session**: 01BBypTYFZ5sySVQizgZmRYh

### Review & Approval

| Role | Status | Date | Notes |
|------|--------|------|-------|
| **Build Engineer** | Ready | TBD | Will own daily support |
| **Tech Lead** | Ready | TBD | Escalation point |
| **Release Manager** | Ready | TBD | Prod emergency escalation |

---

**Document**: `/home/user/yawl/.claude/PHASE5-SUPPORT-PLAN.md`  
**Status**: COMPLETE & READY FOR DEPLOYMENT  
**Next**: Activate support channels and train team on escalation procedures
