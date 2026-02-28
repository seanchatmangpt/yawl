# Profile Selection Guide

**Quick decisions for Maven profiles.** Choose your profile in 30 seconds.

---

## The Quick Decision Matrix

| Your Situation | Recommended Profile | Command | Time |
|---|---|---|---|
| Writing code, need instant feedback | `quick-test` | `mvn test -P quick-test` | 10s |
| Agent development, minimal overhead | `agent-dx` | `mvn compile test -P agent-dx` | 10s |
| Debugging slow tests | `fast-verify` | `mvn test -P fast-verify` | 12s |
| Ready to commit, need comprehensive test | `integration-parallel` | `mvn verify -P integration-parallel` | 85s |
| Running in CI, need coverage | `ci` | `mvn verify -P ci` | 120s |
| Production release, need security scan | `prod` | `mvn verify -P prod` | 150s |
| Don't know, just be safe | (default) | `mvn verify` | 150s |

Pick one row, copy the command. Done.

---

## Detailed Profiles

### quick-test: Dev Loop (10 seconds)

**Best for**: Active development, red-green-refactor cycle

**What**: Unit tests only, no JaCoCo, single JVM

**Command**:
```bash
mvn test -P quick-test
```

**With module targeting**:
```bash
mvn test -P quick-test -pl yawl-engine
```

**Trade-offs**:
- ✓ Fastest feedback (10s)
- ✓ Ideal for rapid iteration
- ✗ Doesn't test integration
- ✗ No coverage metrics

**Use when**:
- You're in active development
- You want <15 second feedback
- You're working on a single module
- You're not touching engine/persistence code

**Don't use when**:
- You modified engine code
- You modified database logic
- You're preparing a commit
- You need coverage reports

**Example workflow**:
```bash
# Edit code
vim YNetRunner.java

# Get instant feedback
mvn test -P quick-test
# Takes 10 seconds

# Fix issues
vim YNetRunner.java

# Test again
mvn test -P quick-test
# Takes 10 seconds

# When done, run full tests
mvn verify -P integration-parallel
# Takes 85 seconds
```

---

### agent-dx: Agent Development (10 seconds)

**Best for**: AI/agent assisted development

**What**: Unit tests only, minimal overhead, all analysis tools disabled

**Command**:
```bash
mvn compile test -P agent-dx
```

**With module targeting**:
```bash
mvn compile test -P agent-dx -pl yawl-engine -amd
```

**Or via dx.sh**:
```bash
bash scripts/dx.sh
# Auto-detects changed modules, uses agent-dx profile
```

**Trade-offs**:
- ✓ Fastest feedback (10s)
- ✓ No analysis overhead
- ✓ Great for agent development
- ✓ Auto-detects changed modules (dx.sh)
- ✗ Only unit tests
- ✗ No static analysis

**Use when**:
- You're working with AI code generation
- You want maximum speed
- You're testing locally before commit
- You're using `dx.sh` for development

**Don't use when**:
- You need static analysis (SpotBugs, PMD)
- You need code coverage
- You're testing integration logic

**Example workflow**:
```bash
# Agent generates code
# You run quick validation
bash scripts/dx.sh

# Takes 10 seconds, only changed modules
# Detects compilation errors immediately
# Shows which tests fail

# Fix issues
vim generated.java

# Validate again
bash scripts/dx.sh

# When ready to commit
mvn verify -P integration-parallel
```

---

### fast-verify: Timing Analysis (12 seconds)

**Best for**: Performance debugging, identifying slow tests

**What**: Unit tests with detailed timing reports and HTML output

**Command**:
```bash
mvn test -P fast-verify
```

**View report**:
```bash
open target/site/surefire-report.html
```

**Trade-offs**:
- ✓ Timing metrics per test
- ✓ HTML report for trend tracking
- ✓ Still fast (12s)
- ✗ Only unit tests
- ✗ Slightly more overhead than quick-test

**Use when**:
- You're optimizing test performance
- You want to find slow tests
- You need timing trends
- You want visual performance reports

**Output**:
```
Slowest tests:
  • testComplexWorkflow (2.34s)
  • testDeadlockDetection (1.89s)
  • testStateRecovery (1.56s)

Report generated: target/site/surefire-report.html
```

**Example**:
```bash
# Run with timing
mvn test -P fast-verify

# Identify bottlenecks
cat target/surefire-reports/*.txt | grep "Time elapsed"

# View visual report
open target/site/surefire-report.html
```

---

### integration-parallel: Production Testing (85 seconds)

**Best for**: Comprehensive verification with parallelization

**What**: Unit + integration tests, 2C parallel forks, 1.77x speedup

**Command**:
```bash
mvn clean verify -P integration-parallel
```

**With module targeting**:
```bash
mvn verify -P integration-parallel -pl yawl-engine -amd
```

**Trade-offs**:
- ✓ 1.77x faster than sequential
- ✓ Comprehensive testing (unit + integration)
- ✓ Perfect for CI with time constraints
- ✓ Safe (process isolation)
- ✗ Slower than quick-test (85s)
- ✗ Requires >4GB RAM for best performance

**Use when**:
- You're ready to commit/push
- You modified engine/core code
- You need comprehensive testing
- You want faster CI/CD (85s vs 150s)
- You're in a time-constrained environment

**Don't use when**:
- You need maximum safety (use default instead)
- You have <2GB RAM (use quick-test or -Dfailsafe.forkCount=1)
- You need full static analysis (use ci instead)

**Example**:
```bash
# Comprehensive testing in 85s
mvn clean verify -P integration-parallel

# Only integration tests
mvn verify -P integration-parallel -DskipUnitTests=true

# Specific module
mvn verify -P integration-parallel -pl yawl-engine -amd

# Custom parallelism for low-end machine
mvn verify -P integration-parallel -Dfailsafe.forkCount=1.5C
```

**Performance on different hardware**:
- 2-core: ~110s
- 4-core: ~85s (1.77x)
- 8-core: ~70s (2.15x)
- 16-core: ~60s (2.51x)

---

### ci: Continuous Integration (120 seconds)

**Best for**: CI/CD pipelines with coverage and analysis requirements

**What**: All tests + JaCoCo coverage + SpotBugs + strict rules

**Command**:
```bash
mvn clean verify -P ci

# Or auto-activate with environment variable
CI=true mvn clean verify
```

**Trade-offs**:
- ✓ Complete validation
- ✓ Code coverage reports (JaCoCo)
- ✓ Static analysis (SpotBugs, PMD)
- ✓ Auto-activates in CI environments
- ✗ Slower (120s)
- ✗ Too much overhead for local dev

**Use when**:
- Running in GitHub Actions, Jenkins, GitLab CI
- You need code coverage metrics
- You need static analysis reports
- You're doing quality gates
- You need strict dependency convergence checks

**Don't use when**:
- Local development (too slow)
- You don't need coverage (use integration-parallel)
- You need <30s feedback

**CI/CD examples**:
```yaml
# GitHub Actions
- name: Test
  run: mvn clean verify -P ci

# Jenkins
sh 'mvn clean verify -P ci'

# GitLab CI
script:
  - mvn clean verify -P ci

# Auto-activate
CI=true mvn clean verify
```

**Reports generated**:
- JaCoCo coverage: `target/site/jacoco/index.html`
- SpotBugs: `target/spotbugsXml.xml`
- Test report: `target/site/surefire-report.html`

---

### prod: Production Release (150 seconds)

**Best for**: Pre-release validation and production deployments

**What**: All tests + JaCoCo + OWASP CVE scanning (fail on CVSS >= 7)

**Command**:
```bash
NVD_API_KEY=your_key mvn clean verify -P prod
```

**Trade-offs**:
- ✓ Maximum safety
- ✓ Security scanning (CVEs)
- ✓ Comprehensive coverage
- ✓ Strict validation rules
- ✗ Slowest (150s)
- ✗ Requires NVD API key for CVE data
- ✗ Not suitable for local dev

**Use when**:
- You're preparing a production release
- You need security scanning
- You need strict coverage thresholds
- You're doing final pre-deployment validation
- Security is paramount

**Don't use when**:
- Local development
- Quick feedback loops
- CVE scanning is not required

**Setup**:
```bash
# Get NVD API key from https://nvd.nist.gov/developers/request-an-api-key

# Set environment
export NVD_API_KEY=your_key

# Run validation
mvn clean verify -P prod
```

**Reports generated**:
- CVE scan: `target/dependency-check-report.html`
- Coverage: `target/site/jacoco/index.html`
- Test report: `target/surefire-report.html`

---

### Default (No Profile): Maximum Safety (150 seconds)

**Best for**: When you're not sure or need maximum conservatism

**What**: Sequential execution, all tests, no optimizations

**Command**:
```bash
mvn clean verify
```

**Trade-offs**:
- ✓ Maximum safety
- ✓ Predictable behavior
- ✓ No surprises
- ✗ Slowest (150s)
- ✗ Wastes parallelism on multi-core machines

**Use when**:
- You want baseline sequential behavior
- You're troubleshooting flaky tests
- You're unsure about other profiles
- You need maximum predictability

**Example**:
```bash
# When in doubt
mvn clean verify

# This is safe and reliable
# Just slower than it needs to be
```

---

## Profile Comparison Table

| Feature | quick-test | agent-dx | fast-verify | integration-parallel | ci | prod | default |
|---------|-----------|----------|-------------|---------------------|----|----|---------|
| Time | 10s | 10s | 12s | 85s | 120s | 150s | 150s |
| Unit tests | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Integration tests | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Parallelization | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ |
| JaCoCo coverage | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ |
| SpotBugs/PMD | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ |
| CVE scanning | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ |
| Timing reports | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ |
| Best for dev | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ |
| Best for CI | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ | ✗ |
| Best for release | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ |

---

## Common Workflows

### Workflow 1: Active Development

```bash
# 1. Write code
vim YNetRunner.java

# 2. Quick test (10s)
mvn test -P quick-test
# FAIL -> fix

# 3. Repeat until green

# 4. When ready, full test
mvn verify -P integration-parallel

# 5. If green, commit
git commit -m "..."
```

**Total time**: 10s + 10s + 10s + 85s = 115s (vs 150s default)

### Workflow 2: Agent Development

```bash
# 1. Agent generates code

# 2. Quick validation (10s)
bash scripts/dx.sh

# 3. If failures, let agent fix code

# 4. When happy, full test
mvn verify -P integration-parallel

# 5. Commit
git commit -m "..."
```

**Total time**: 10s + 85s = 95s

### Workflow 3: CI/CD Pipeline

```bash
# Automatically on push
CI=true mvn clean verify -P ci

# Generate reports
# - Coverage: target/site/jacoco/index.html
# - Analysis: target/spotbugsXml.xml
# - Tests: target/surefire-report.html

# Deploy if green
```

**Total time**: 120s

### Workflow 4: Production Release

```bash
# Final validation before release
NVD_API_KEY=$KEY mvn clean verify -P prod

# If green, tag and deploy
git tag v1.2.3
git push origin v1.2.3
```

**Total time**: 150s

---

## Decision Flowchart (Text)

```
What are you doing?
│
├─ Active coding (red-green-refactor)
│  └─ Use: -P quick-test (10s)
│
├─ Agent assisted development
│  └─ Use: -P agent-dx (10s)
│
├─ Debugging test performance
│  └─ Use: -P fast-verify (12s)
│
├─ Preparing a commit (ready to test fully)
│  ├─ Need it fast?
│  │  ├─ YES → Use: -P integration-parallel (85s)
│  │  └─ NO  → Use: (default) (150s)
│  └─
│
├─ Running in CI/CD
│  ├─ Need coverage/analysis?
│  │  ├─ YES → Use: -P ci (120s)
│  │  └─ NO  → Use: -P integration-parallel (85s)
│  └─
│
└─ Production release
   └─ Use: -P prod (150s + CVE scan)
```

---

## Environment Setup

### Bash Aliases

Add to `~/.bashrc` or `~/.zshrc`:

```bash
# Quick development
alias test-quick="mvn test -P quick-test"
alias test-agent="mvn compile test -P agent-dx"

# Full verification
alias test-full="mvn clean verify -P integration-parallel"
alias test-all="mvn clean verify"

# CI/CD
alias test-ci="mvn clean verify -P ci"

# Developer workflow
alias dx="bash scripts/dx.sh"

# Production
alias test-prod="NVD_API_KEY=$NVD_API_KEY mvn clean verify -P prod"
```

### IDE Integration

**IntelliJ IDEA**:
1. Run → Edit Configurations
2. Add new "Maven" configuration
3. Goals: `verify -P integration-parallel`
4. Save and use

**Eclipse**:
1. Right-click project → Run As → Maven build
2. Goals: `verify -P integration-parallel`

**VS Code**:
1. Install "Maven for Java" extension
2. Command Palette → "Maven: Run..."
3. Select "integration-parallel" profile

---

## Troubleshooting Profile Selection

**Problem**: "I ran the wrong profile"
**Solution**: Just run the correct one. Profiles don't affect each other.

```bash
# Ran this by mistake
mvn verify -P quick-test

# Just run this instead
mvn verify -P integration-parallel
```

**Problem**: "Profile not found"
**Solution**: Make sure you're in the YAWL root directory

```bash
cd /home/user/yawl
mvn help:active-profiles
```

**Problem**: "Wrong tests are running"
**Solution**: Check excludedGroups in the profile

```bash
# See what each profile does
grep -A5 "<id>quick-test</id>" pom.xml
grep -A5 "<id>integration-parallel</id>" pom.xml
```

---

## Reference

- **Full Developer Guide**: See `DEVELOPER-GUIDE-PARALLELIZATION.md`
- **Build Tuning Details**: See `BUILD-TUNING-REFERENCE.md`
- **Quick Start**: See `QUICK-START-PARALLEL-TESTS.md`
- **Troubleshooting**: See `TROUBLESHOOTING-GUIDE.md`

---

**Version**: 1.0
**Status**: Production Ready
**Last Updated**: February 28, 2026
