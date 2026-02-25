# YAWL v6.0.0 GODSPEED CLI Guide

Complete reference for the unified YAWL CLI — one mega command to control all build, test, validation, and team operations.

**Version**: 6.0.0
**Status**: Production Ready
**Updated**: 2026-02-22

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Installation & Setup](#installation--setup)
3. [Project Initialization](#project-initialization)
4. [Core Commands](#core-commands)
5. [GODSPEED Phases](#godspeed-phases)
6. [Build Operations](#build-operations)
7. [Observatory (Facts)](#observatory-facts)
8. [Code Generation (ggen)](#code-generation-ggen)
9. [Team Operations](#team-operations)
10. [Configuration](#configuration)
11. [Interactive Mode](#interactive-mode)
12. [Troubleshooting](#troubleshooting)
13. [Command Cheat Sheet](#command-cheat-sheet)
14. [Advanced Usage](#advanced-usage)

---

## Quick Start

### 30 Second Setup

```bash
# 1. Initialize your YAWL project
yawl init

# 2. Run full GODSPEED circuit (Ψ → Λ → H → Q → Ω)
yawl godspeed full --verbose

# 3. Check status
yawl status

# 4. Commit changes
git add .
git commit -m "GODSPEED validation passed"
git push -u origin claude/my-feature
```

### Interactive Mode (Recommended for Beginners)

```bash
# Launch interactive wizard
yawl init --interactive

# Guided GODSPEED execution
yawl godspeed full --interactive

# Build with preview
yawl build all --plan
```

---

## Installation & Setup

### Prerequisites

- **Java**: 25+ (check with `java -version`)
- **Maven**: 3.9.0+ (check with `mvn --version`)
- **Python**: 3.10+ (CLI implementation)
- **Git**: 2.40+ (for version control)
- **Bash**: 4.0+ (for scripts)

### Install CLI

```bash
# Clone YAWL repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Install Python CLI
pip install -e cli/

# Verify installation
yawl version
```

### Environment Setup

```bash
# Set JAVA_HOME
export JAVA_HOME=/path/to/java25
export PATH=$JAVA_HOME/bin:$PATH

# Verify Maven proxy (if behind corporate firewall)
yawl build validate-network

# Check all prerequisites
yawl version --verbose
```

---

## Project Initialization

### Basic Initialization

```bash
yawl init
```

Creates:
- `.yawl/` - CLI state directory
- `.yawl/cli/` - Command history and caches
- `.yawl/config.yaml` - Project configuration (if not exists)

### Interactive Initialization

```bash
yawl init --interactive
```

Prompts for:
- Default build module (e.g., `yawl-engine`)
- Test patterns (e.g., `**/*Test.java`)
- Team size preferences (2-5)
- Deploy target (docker, kubernetes, etc.)

Example workflow:

```
Welcome to YAWL CLI Setup!

? Default build module: yawl-engine
? Test pattern: **/*Test.java, **/*IT.java
? Team size (2-5): 3
? Deploy target: docker
? Enable observability hooks? Yes

✓ Configuration saved to .yawl/config.yaml
✓ CLI initialized
```

---

## Core Commands

### `yawl version`

Show CLI version and environment info.

```bash
yawl version
# Output:
# ╔══════════════════════════════════════════╗
# ║  YAWL v6.0.0 - CLI GODSPEED             ║
# ║  One Mega CLI for Everything             ║
# ║  Maven + Observatory + ggen + gregverse  ║
# ╚══════════════════════════════════════════╝
#
# Version: 6.0.0
# Python: 3.11.2
# Project root: /home/user/yawl
# JAVA_HOME: /usr/lib/jvm/java-25-openjdk
# Maven: Apache Maven 3.9.6

yawl version --verbose
# Additional details: facts location, git branch, hooks status
```

### `yawl init`

Initialize YAWL project for CLI use.

```bash
yawl init
# Creates: .yawl directory, .yawl/config.yaml

yawl init --interactive
# Interactive setup wizard (recommended for first time)
```

### `yawl status`

Show project status and latest facts.

```bash
yawl status
# Output:
# ╔════════════════════════════════════════╗
# ║  YAWL Project Status                   ║
# ╚════════════════════════════════════════╝
#
# Observatory facts: 12 files
# Latest fact: modules.json (5 min ago)
# Git branch: claude/fix-deadlock
# Build status: GREEN (compile ≺ test ≺ validate)
```

---

## GODSPEED Phases

The GODSPEED circuit validates code through 5 gates: **Ψ → Λ → H → Q → Ω**

### Full Circuit: `yawl godspeed full`

Run all phases in sequence with automatic gating.

```bash
yawl godspeed full --verbose

# Phases executed:
# Ψ (Discover)   - Generate facts via Observatory (60s)
# Λ (Compile)    - Build artifacts (90s)
# H (Guards)     - Check anti-patterns (20s)
# Q (Verify)     - Validate invariants (30s)
# (Ready for Ω - consolidation/commit)
```

**Options**:
- `--verbose, -v` - Show detailed output
- `--skip-psi` - Skip Observatory (use existing facts)
- `--skip-h` - Skip guard checks
- `--skip-q` - Skip invariant verification
- `--dry-run` - Show what would happen without running

### Phase 1: Ψ (Discover)

Generate facts about the codebase via Observatory.

```bash
yawl godspeed discover --verbose

# Generates facts:
# - modules.json
# - gates.json
# - deps-conflicts.json
# - shared-src.json
# Location: docs/v6/latest/facts/
```

**Use when**:
- Codebase structure changed
- Facts are older than 30 minutes
- Adding new modules

### Phase 2: Λ (Compile)

Fastest feedback loop - compile only changed modules.

```bash
# Compile all
yawl godspeed compile --verbose

# Compile specific module
yawl godspeed compile --module yawl-engine --verbose

# Show compile plan before running
yawl godspeed compile --plan
```

**Output**:
- `GREEN` - Ready to proceed to H phase
- `RED` - Fix compilation errors and re-run

**Common errors**:
- `Cannot find symbol` - Run `yawl observatory discover` to refresh facts
- `Plugin error` - Run `yawl build clean` then retry

### Phase 3: H (Guards)

Check for anti-patterns: TODO, mock, stub, fake, empty, silent fallback, lies.

```bash
yawl godspeed guard --verbose

# Specific file
yawl godspeed guard --file yawl/engine/YNetRunner.java

# Show violations before fixing
yawl godspeed guard --verbose --list-violations
```

**If violations found**:
1. Read detailed violation report
2. Fix code (implement real logic or throw `UnsupportedOperationException`)
3. Rerun: `yawl godspeed guard --verbose`
4. Proceed to Q phase

**Violations detected** (H = Guards):
- `H_TODO`: Deferred work markers (TODO, FIXME, XXX, HACK, LATER, FUTURE)
- `H_MOCK`: Mock implementations (mock, stub, fake, test, demo, sample)
- `H_STUB`: Empty returns and no-op methods
- `H_EMPTY`: Completely empty method bodies
- `H_FALLBACK`: Silent exception handling (catch and return fake)
- `H_LIE`: Code behavior doesn't match documentation
- `H_SILENT`: Logging instead of throwing exceptions

### Phase 4: Q (Verify)

Verify code invariants: real_impl ∨ throw, ¬mock, ¬lie, ¬silent_fallback.

```bash
yawl godspeed verify --verbose

# Run specific invariant check
yawl godspeed verify --invariant Q_REAL_IMPL_OR_THROW

# Detailed report
yawl godspeed verify --report json > invariant-report.json
```

**Invariants verified** (Q = Invariants):
- `Q_REAL_IMPL_OR_THROW`: Methods implement real logic or throw UnsupportedOperationException
- `Q_NO_MOCK`: No mock objects, stubs, or fake implementations in production code
- `Q_NO_LIE`: Method behavior matches documentation and signature
- `Q_NO_SILENT_FALLBACK`: Exceptions propagated, never silently caught and faked

**If invariants violated**:
1. Review invariant-report.json for details
2. Fix code violations (implement real logic or throw UnsupportedOperationException)
3. Rerun: `yawl godspeed verify`
4. Proceed to consolidation

### Full GODSPEED with All Options

```bash
yawl godspeed full \
  --verbose \
  --skip-psi \
  --fail-fast \
  --report json \
  --output results/

# Output:
# • Ψ (skipped - using cached facts)
# • Λ (compile) → GREEN
# • H (guards) → GREEN (3 patterns checked)
# • Q (verify) → GREEN (4 invariants verified)
# → Ready for consolidation & commit
# Results saved to: results/godspeed-report.json
```

---

## Build Operations

### `yawl build compile`

Compile YAWL project (fastest feedback).

```bash
# Compile all
yawl build compile --verbose

# Compile specific module
yawl build compile --module yawl-engine

# Show what would compile
yawl build compile --plan

# Parallel compilation (faster)
yawl build compile --parallel
```

### `yawl build test`

Run unit and integration tests.

```bash
# Run all tests
yawl build test --verbose

# Run specific module tests
yawl build test --module yawl-engine

# Run matching pattern
yawl build test --pattern '**/YNetRunner*'

# Run with coverage report
yawl build test --coverage
```

### `yawl build validate`

Run static analysis (checkstyle, spotbugs, pmd).

```bash
yawl build validate --verbose

# Show violations only
yawl build validate --violations

# Export report
yawl build validate --report html > validation.html
```

### `yawl build all`

Full build: compile → test → validate (pre-commit gate).

```bash
yawl build all --verbose
# Expected: ~3-5 minutes total
# Must be GREEN before commit

yawl build all --parallel --cache
# Faster with caching
```

### `yawl build clean`

Clean build artifacts.

```bash
yawl build clean
# Removes: target/, .m2/repository/ (local)
```

---

## Observatory (Facts)

Generate codebase facts for analysis.

### Basic Usage

```bash
yawl observatory discover --verbose

# Outputs to: docs/v6/latest/facts/
# Files generated:
# - modules.json (89 modules)
# - gates.json (test gates)
# - deps-conflicts.json
# - shared-src.json
# - reactor.json
# + 10 more fact files
```

### Check Fact Staleness

```bash
yawl observatory check-staleness

# Output:
# ✓ modules.json - fresh (2 min ago)
# ✓ gates.json - fresh (2 min ago)
# ! deps-conflicts.json - stale (45 min ago)
#
# Recommendation: Run 'yawl observatory discover'
```

### List Available Facts

```bash
yawl observatory list --verbose

# Output:
# modules.json (50 KB) - Module registry
# gates.json (8 KB) - Test gates
# deps-conflicts.json (15 KB) - Dependency analysis
# ... (10+ more)
```

---

## Code Generation (ggen)

Generate YAWL XML workflows from specifications.

### Generate Workflows

```bash
yawl ggen generate --spec myworkflow.yawl --output generated/

# Options:
# --spec FILE           Specification file
# --output DIR          Output directory
# --overwrite           Overwrite existing
# --validate            Validate after generation
```

### Validate Generated Code

```bash
yawl ggen validate --directory generated/ --verbose

# Validates:
# Ψ (Facts) - Facts are current
# Λ (Compile) - Generated code compiles
# H (Guards) - No anti-patterns
# Q (Verify) - Invariants hold
```

### Export Workflows (gregverse)

```bash
yawl gregverse export --format bpmn --output my-workflow.bpmn
yawl gregverse export --format wsdl --output my-workflow.wsdl
```

---

## Team Operations

Coordinate multi-agent parallel work.

### Create Team

```bash
yawl team create my-team \
  --quantums "engine,schema,test" \
  --agents 3 \
  --verbose

# Creates team with 3 agents working on independent quantums:
# - Agent 1: Engine (YNetRunner deadlock fix)
# - Agent 2: Schema (workflow type definition)
# - Agent 3: Tests (deadlock test cases)
```

### List Teams

```bash
yawl team list

# Output:
# ┌─────────────────┬────────┬───────┬───────┐
# │ Team ID         │ Status │ Agents│ Tasks │
# ├─────────────────┼────────┼───────┼───────┤
# │ τ-abc123-eng    │ active │ 3     │ 5/5   │
# │ τ-xyz789-schema │ done   │ 2     │ 3/3   │
# └─────────────────┴────────┴───────┴───────┘
```

### Resume Team

```bash
yawl team resume τ-abc123

# Reconnects lead to existing team
# Shows: team status, pending tasks, mailbox
```

### Message Team Member

```bash
yawl team message τ-abc123 engineer-a "Status check: still on track?"

# Sends message to teammate
# Appears in: team mailbox, teammate session
```

### Consolidate Team

```bash
yawl team consolidate τ-abc123 \
  --message "Fix deadlock in YNetRunner" \
  --verbose

# Lead phase:
# 1. Compile all (bash scripts/dx.sh all)
# 2. Hook check (guard violations?)
# 3. Git add <team files>
# 4. Git commit
# 5. Git push
```

---

## Configuration

YAWL CLI reads configuration from multiple sources (in order of precedence):

1. `.yawl/config.yaml` (project-specific)
2. `~/.yawl/config.yaml` (user home)
3. `/etc/yawl/config.yaml` (system-wide)
4. Built-in defaults

### Project Configuration (.yawl/config.yaml)

```yaml
# YAWL CLI Configuration
project:
  name: "YAWL v6"
  version: "6.0.0"

build:
  default_module: "yawl-engine"
  parallel: true
  threads: 8
  timeout_seconds: 600

test:
  pattern: "**/*Test.java"
  coverage_minimum: 80
  fail_fast: false

observatory:
  facts_dir: "docs/v6/latest/facts"
  refresh_interval_minutes: 30
  auto_refresh: true

godspeed:
  phases:
    - "discover"
    - "compile"
    - "guard"
    - "verify"
  fail_fast: true
  verbose: false

team:
  max_agents: 5
  heartbeat_interval_seconds: 60
  timeout_minutes: 120

output:
  format: "table"  # table, json, yaml
  verbose: false
  color: true
```

### User Configuration (~/.yawl/config.yaml)

```yaml
# Global preferences (all YAWL projects)
cli:
  default_verbose: false
  default_format: "table"
  color: true

build:
  # Override per-project settings
  # parallel: true
  # threads: 16

team:
  default_agents: 3
```

### Load Configuration

```bash
# Show loaded configuration
yawl config show

# Show value for key
yawl config get build.parallel

# Set value
yawl config set build.parallel true

# Reset to defaults
yawl config reset
```

---

## Interactive Mode

### Interactive GODSPEED

```bash
yawl godspeed full --interactive

# Prompts:
# ? Run Ψ (Discover) phase? [Y/n]
# ? Show facts summary? [y/N]
# ? Run Λ (Compile) phase? [Y/n]
# ? Show compilation warnings? [y/N]
# ? Run H (Guards) phase? [Y/n]
# ? Auto-fix guard violations? [y/N]
# ? Run Q (Verify) phase? [Y/n]
# ? Save detailed report? [y/N]
# ? Ready to consolidate? [Y/n]
```

### Interactive Build

```bash
yawl build all --interactive

# Show plan first
? Compile all modules (parallel, 8 threads)? [Y/n]
? Run tests with coverage? [Y/n]
? Generate validation reports? [y/N]
? Ready to proceed? [Y/n]
```

### Interactive Team Creation

```bash
yawl team create --interactive

# Prompts:
# ? Team name: fix-deadlock
# ? How many agents? (2-5): 3
# ? Quantum 1 name: engine
# ? Quantum 2 name: schema
# ? Quantum 3 name: test
# ? Confirm team creation? [Y/n]
```

---

## Troubleshooting

### Build Fails: Cannot Find Symbol

**Problem**: `[ERROR] cannot find symbol: class YWorkItem`

**Solution**:
```bash
# 1. Refresh facts
yawl observatory discover

# 2. Check module dependency order
yawl observatory list | grep modules

# 3. Rebuild from clean
yawl build clean
yawl build compile --verbose

# 4. Check if module is in pom.xml
grep -r "yawl-elements" pom.xml
```

### Guard Violations Not Found

**Problem**: `yawl godspeed guard` says GREEN but you know there are violations.

**Solution**:
```bash
# 1. Run with verbose
yawl godspeed guard --verbose --list-violations

# 2. Check specific file
yawl godspeed guard --file src/main/java/MyClass.java

# 3. Refresh hook patterns
yawl config reset

# 4. Run direct hook
bash .claude/hooks/hyper-validate.sh src/
```

### Invariant Verification Fails

**Problem**: `yawl godspeed verify` finds violations but code looks correct.

**Solution**:
```bash
# 1. Review detailed report
yawl godspeed verify --report json > verify-report.json
cat verify-report.json

# 2. Check specific invariant
yawl godspeed verify --invariant Q_REAL_IMPL_OR_THROW --verbose

# 3. Inspect generated RDF (if using ggen)
yawl ggen validate --directory generated/ --verbose

# 4. Clear verification cache
yawl build clean
yawl godspeed verify
```

### Team Timeout

**Problem**: Team consolidation times out after 2 hours.

**Solution**:
```bash
# 1. Check team status
yawl team status τ-abc123

# 2. Message slow agent
yawl team message τ-abc123 engineer-a "Status?"

# 3. Increase timeout
yawl config set team.timeout_minutes 180
yawl team consolidate τ-abc123

# 4. If still failing, check local DX
# Have agent run: bash scripts/dx.sh all locally
```

### Network/Proxy Issues

**Problem**: Maven can't reach central repo behind corporate firewall.

**Solution**:
```bash
# 1. Check network status
yawl build validate-network

# 2. Configure proxy
export https_proxy="http://user:pass@proxy.corp.com:3128"

# 3. Test Maven proxy
yawl build compile --verbose

# 4. Permanent fix in ~/.yawl/config.yaml
network:
  proxy_url: "http://user:pass@proxy.corp.com:3128"
```

### CLI Not Found

**Problem**: `yawl: command not found`

**Solution**:
```bash
# 1. Check Python installation
python3 -m pip list | grep yawl

# 2. Install/reinstall CLI
cd /home/user/yawl
pip install -e cli/

# 3. Check PATH
echo $PATH | grep -i python

# 4. Install system-wide
sudo pip install -e cli/
```

---

## Command Cheat Sheet

### Most Used Commands

```bash
# Initialize (first time only)
yawl init

# Quick GODSPEED (primary workflow)
yawl godspeed full --verbose

# Build & test (pre-commit)
yawl build all

# Check status
yawl status

# Run specific phase
yawl godspeed compile --module yawl-engine
yawl godspeed guard --verbose
yawl godspeed verify --report json

# Observatory (refresh facts)
yawl observatory discover

# Team operations
yawl team create my-team --quantums "eng,test" --agents 2
yawl team list
yawl team consolidate <team-id> --message "Fix deadlock"

# Configuration
yawl config show
yawl config set build.parallel true
```

### Advanced Commands

```bash
# Dry-run before executing
yawl godspeed full --dry-run
yawl build all --plan

# Interactive mode (recommended for new users)
yawl init --interactive
yawl godspeed full --interactive

# Detailed reports
yawl godspeed guard --verbose --list-violations
yawl godspeed verify --report json > results.json
yawl build test --coverage

# Parallel operations (faster)
yawl build compile --parallel
yawl build test --parallel --threads 16

# Skip phases
yawl godspeed full --skip-psi --skip-q

# Fail fast (stop at first error)
yawl godspeed full --fail-fast
```

### Observatory Commands

```bash
yawl observatory discover --verbose          # Generate facts
yawl observatory list                        # Show available facts
yawl observatory check-staleness             # Check fact age
yawl observatory load modules.json           # Load specific fact
```

### ggen Commands

```bash
yawl ggen generate --spec workflow.yawl --output generated/
yawl ggen validate --directory generated/
yawl gregverse export --format bpmn --output workflow.bpmn
```

---

## Advanced Usage

### Custom Build Module Selection

```bash
# Override default module
yawl build compile --module yawl-elements

# Compile multiple specific modules
yawl build compile --modules yawl-engine,yawl-elements,yawl-stateless
```

### Test Filtering and Coverage

```bash
# Run specific test class
yawl build test --class YNetRunnerTest

# Run tests matching pattern
yawl build test --pattern '**/YNetRunner*'

# Generate coverage report
yawl build test --coverage --format html

# Enforce coverage minimum
yawl build test --coverage --minimum 85
```

### Fact Analysis

```bash
# Load and inspect facts
yawl observatory load modules.json
yawl observatory load gates.json

# Search facts
yawl observatory search "YNetRunner"

# Export facts for analysis
yawl observatory export --format json --output facts/
```

### Team Workflow Examples

**Example 1: Deadlock Investigation (3 engineers)**
```bash
yawl team create deadlock-fix \
  --quantums "engine semantic,guard logic,concurrency" \
  --agents 3

# Agents work in parallel:
# - Engineer A: State machine analysis
# - Engineer B: Guard evaluation logic
# - Engineer C: Concurrency and synchronization

yawl team status deadlock-fix
yawl team message deadlock-fix engineer-a "Found root cause in line 427"
yawl team consolidate deadlock-fix --message "Fixed YNetRunner deadlock"
```

**Example 2: Schema + Implementation (2 engineers)**
```bash
yawl team create workflow-type-sla \
  --quantums "schema definition,engine implementation" \
  --agents 2

# Schema engineer defines SLA element
# Engine engineer implements SLA enforcement
yawl team consolidate workflow-type-sla --message "Added SLA tracking"
```

### Performance Tuning

```bash
# Enable parallel compilation
yawl config set build.parallel true
yawl config set build.threads 16

# Cache compiled modules
yawl build compile --cache

# Skip tests for faster feedback
yawl build compile --skip-test

# Fail fast on first error
yawl godspeed full --fail-fast
```

### Integration with CI/CD

```bash
# Non-interactive mode for CI/CD
yawl godspeed full \
  --verbose \
  --report json \
  --output ci-results/ \
  --fail-fast

# Check exit code
if [ $? -eq 0 ]; then
  echo "GODSPEED validation passed"
  git push -u origin claude/feature
else
  echo "GODSPEED validation failed"
  exit 1
fi
```

---

## Environment Variables

```bash
# Override Java home
export JAVA_HOME=/path/to/java25

# Maven settings
export MAVEN_HOME=/path/to/maven
export MAVEN_OPTS="-Xmx2g"

# Network proxy
export https_proxy="http://proxy.corp.com:3128"
export http_proxy="http://proxy.corp.com:3128"

# CLI behavior
export YAWL_VERBOSE=true
export YAWL_COLOR=true
export YAWL_TIMEOUT_MINUTES=180

# Observatory
export YAWL_FACTS_DIR="/path/to/facts"
export YAWL_REFRESH_INTERVAL=30
```

---

## Files and Directories

```
.yawl/
├── config.yaml                   # Project configuration
├── cli/
│   ├── history.json             # Command history
│   └── cache/                   # Temporary caches
│
docs/v6/latest/facts/            # Observatory facts
├── modules.json
├── gates.json
├── deps-conflicts.json
└── ...

.claude/
├── hooks/
│   ├── hyper-validate.sh        # Guard checker
│   ├── q-phase-invariants.sh    # Invariant verifier
│   └── ...
├── sparql/
│   └── invariants-q-phase.sparql # SPARQL queries
└── receipts/
    ├── guard-receipt.json
    └── invariant-receipt.json
```

---

## Summary

The YAWL CLI (yawl) provides:

✓ **One command for everything** - Build, test, validate, analyze, generate
✓ **GODSPEED circuit automation** - Ψ → Λ → H → Q → Ω gates
✓ **Fast feedback loops** - Compile in seconds, not minutes
✓ **Team coordination** - Multi-agent parallel work with messaging
✓ **Smart caching** - Only rebuild what changed
✓ **Interactive mode** - Guided workflows for beginners
✓ **Production-grade validation** - Guards + Invariants + Tests
✓ **Flexible configuration** - Project, user, and system-wide settings

---

**Status**: Production Ready
**Last Updated**: 2026-02-22
**Support**: See CONTRIBUTING.md for issue reporting
**License**: Apache 2.0
