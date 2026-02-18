# YAWL v6 Validation Environment Setup Guide

**Purpose:** Configure your local machine to generate validation data for YAWL v6.0.0
**Last Updated:** 2026-02-18
**Java Target:** 25 LTS
**Maven Target:** 3.9.11

---

## Table of Contents

1. [Prerequisites Check](#1-prerequisites-check)
2. [Java 25 Installation](#2-java-25-installation)
3. [Maven Installation](#3-maven-installation)
4. [Environment Verification](#4-environment-verification)
5. [Running Tests with Coverage](#5-running-tests-with-coverage)
6. [Running Static Analysis](#6-running-static-analysis)
7. [Generating Observatory Facts](#7-generating-observatory-facts)
8. [Generating SBOM](#8-generating-sbom)
9. [Quick Reference Commands](#9-quick-reference-commands)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites Check

Run this command to check your current environment:

```bash
# One-liner environment check
echo "=== YAWL Environment Check ===" && \
echo "Java: $(java -version 2>&1 | head -1 || echo 'NOT INSTALLED')" && \
echo "Maven: $(mvn -version 2>/dev/null | head -1 || echo 'NOT INSTALLED')" && \
echo "Git: $(git --version 2>/dev/null || echo 'NOT INSTALLED')" && \
echo "Python3: $(python3 --version 2>/dev/null || echo 'NOT INSTALLED')"
```

### Required Versions

| Tool | Minimum | Recommended | How to Check |
|------|---------|-------------|--------------|
| Java | 25 | 25.0.1+ | `java -version` |
| Maven | 3.9.0 | 3.9.11 | `mvn -version` |
| Git | 2.30 | latest | `git --version` |
| Python3 | 3.9 | 3.12+ | `python3 --version` |

---

## 2. Java 25 Installation

### macOS (Recommended: SDKMAN)

```bash
# Install SDKMAN (if not already installed)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 25 (Eclipse Temurin - recommended)
sdk install java 25-tem

# Verify installation
java -version
# Expected: openjdk version "25" or similar
```

### macOS (Alternative: Homebrew)

```bash
# Install via Homebrew
brew install --cask temurin@25

# Or use OpenJDK
brew install openjdk@25

# Link if needed
sudo ln -sfn /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk \
     /Library/Java/JavaVirtualMachines/openjdk-25.jdk

# Verify
java -version
```

### Linux (Ubuntu/Debian)

```bash
# Install Eclipse Temurin 25
sudo apt update
sudo apt install -y wget apt-transport-https
wget -O - https://packages.adoptium.net/keys/gpg | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-25-jdk

# Verify
java -version
```

### Linux (Fedora/RHEL)

```bash
# Install via dnf
sudo dnf install -y java-25-openjdk java-25-openjdk-devel

# Verify
java -version
```

### Windows

```powershell
# Using winget
winget install EclipseAdoptium.Temurin.25.JDK

# Or using Chocolatey
choco install temurin25

# Verify in new terminal
java -version
```

### Setting JAVA_HOME

After installation, set `JAVA_HOME` environment variable:

```bash
# macOS (add to ~/.zshrc or ~/.bashrc)
export JAVA_HOME=$(/usr/libexec/java_home -v 25)

# Linux (add to ~/.bashrc)
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk
# Or for OpenJDK:
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk

# Windows (System Environment Variables)
# JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25
```

Reload your shell:

```bash
source ~/.zshrc  # or ~/.bashrc
echo $JAVA_HOME
```

---

## 3. Maven Installation

### macOS (Homebrew)

```bash
brew install maven
mvn -version
```

### Linux (SDKMAN - Recommended)

```bash
# If you have SDKMAN
sdk install maven 3.9.11

# Verify
mvn -version
```

### Linux (Package Manager)

```bash
# Ubuntu/Debian
sudo apt install -y maven

# Fedora/RHEL
sudo dnf install -y maven

# Verify
mvn -version
```

### Manual Installation (Any Platform)

```bash
# Download Maven 3.9.11
cd /tmp
wget https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz

# Extract
sudo tar -xzf apache-maven-3.9.11-bin.tar.gz -C /opt

# Create symlink
sudo ln -s /opt/apache-maven-3.9.11 /opt/maven

# Add to PATH (add to ~/.bashrc or ~/.zshrc)
export M2_HOME=/opt/maven
export PATH=$M2_HOME/bin:$PATH

# Reload and verify
source ~/.bashrc
mvn -version
```

---

## 4. Environment Verification

Once Java 25 and Maven are installed, verify the full setup:

```bash
# Navigate to YAWL project
cd /path/to/yawl

# Run verification script
bash scripts/migrate-to-java25.sh --verify-only
```

### Manual Verification Steps

```bash
# 1. Check Java version (must be 25+)
java -version
# Expected: openjdk version "25.x.x"

# 2. Check Maven version (must be 3.9.0+)
mvn -version
# Expected: Apache Maven 3.9.x

# 3. Check JAVA_HOME
echo $JAVA_HOME
# Should point to Java 25 installation

# 4. Verify project compiles
mvn -T 1.5C clean compile -q
# Expected: BUILD SUCCESS

# 5. Quick test run
bash scripts/dx.sh compile
# Expected: dx: OK (X.Xs)
```

---

## 5. Running Tests with Coverage

### Using JaCoCo Coverage Plugin

The project uses JaCoCo for code coverage. Coverage reports are generated in the `target/site/jacoco/` directory.

```bash
# Run tests with coverage (all modules)
mvn -T 1.5C clean test -Djacoco.skip=false

# Run tests with coverage on specific module
mvn -T 1.5C clean test -Djacoco.skip=false -pl yawl-engine

# Generate coverage report only (after tests)
mvn jacoco:report
```

### Coverage Report Locations

After running tests with coverage:

```
yawl-engine/target/site/jacoco/index.html           # Engine coverage
yawl-stateless/target/site/jacoco/index.html        # Stateless coverage
yawl-elements/target/site/jacoco/index.html         # Elements coverage
...
```

### Using the Analysis Profile

The `analysis` profile includes JaCoCo coverage with a 75% threshold:

```bash
# Full analysis with coverage (2-3 minutes)
mvn -T 1.5C clean verify -P analysis

# This runs:
# - Compilation
# - Tests with JaCoCo coverage
# - Coverage threshold check (75%)
# - SpotBugs static analysis
# - PMD code smell detection
```

### Coverage Dashboard

View coverage summary:

```bash
# Generate coverage facts
bash scripts/observatory/observatory.sh --facts

# Display ASCII coverage dashboard
bash scripts/coverage-report.sh
```

Example output:
```
+--------------------------------------------------------------+
|          YAWL Coverage Dashboard - Q2 2026 Targets          |
+--------------------------------------------------------------+
|  AGGREGATE  Line:  45.2% [OK] (target 50%)  Branch:  38.1% [OK] (target 40%)
+-------------------------------+----------+------------------+
| Module                        |  Line %  |  Status          |
+-------------------------------+----------+------------------+
| yawl-engine                   |   52.3%  | #####.....       |
| yawl-stateless                |   41.7%  | ####......       |
| yawl-elements                 |   48.9%  | #####.....       |
...
```

### Aggregated Coverage Report

To get combined coverage across all modules:

```bash
# Generate aggregate report
mvn jacoco:report-aggregate

# Location: target/site/jacoco-aggregate/index.html
```

---

## 6. Running Static Analysis

### Available Analysis Tools

| Tool | Purpose | Command |
|------|---------|---------|
| **SpotBugs** | Bug pattern detection | `mvn spotbugs:check` |
| **PMD** | Code smell detection | `mvn pmd:check` |
| **Checkstyle** | Code style enforcement | `mvn checkstyle:check` |
| **Error Prone** | Compile-time checks | Enabled in `analysis` profile |

### Using the Analysis Profile

```bash
# Full static analysis (SpotBugs + PMD + JaCoCo)
mvn -T 1.5C clean verify -P analysis

# Analysis-only (skip tests)
mvn -T 1.5C verify -P analysis -DskipTests
```

### Individual Tool Commands

```bash
# SpotBugs only
mvn spotbugs:spotbugs spotbugs:check

# PMD only
mvn pmd:pmd pmd:check

# Checkstyle only
mvn checkstyle:check

# All analysis tools
mvn spotbugs:check pmd:check checkstyle:check
```

### Report Locations

```
target/spotbugs/spotbugs.xml          # SpotBugs XML report
target/site/spotbugs/index.html       # SpotBugs HTML report
target/pmd.xml                        # PMD XML report
target/site/pmd/index.html            # PMD HTML report
target/checkstyle-result.xml          # Checkstyle XML report
target/site/checkstyle/index.html     # Checkstyle HTML report
```

### Observatory Static Analysis Facts

Generate structured analysis data:

```bash
# Generate static analysis facts
bash scripts/observatory/observatory.sh --static-analysis

# Output: docs/v6/latest/facts/static-analysis.json
# Output: docs/v6/latest/diagrams/quality-gates.mmd
```

---

## 7. Generating Observatory Facts

The Observatory generates pre-computed facts about the codebase, reducing context consumption by 100x.

### Full Observatory Run

```bash
# Generate all facts, diagrams, and receipts
bash scripts/observatory/observatory.sh

# Output: docs/v6/latest/
#   - facts/          (JSON fact files)
#   - diagrams/       (Mermaid diagrams)
#   - receipts/       (SHA256 hashes)
#   - INDEX.md        (Navigation index)
```

### Facts-Only Run (Faster)

```bash
# Generate only JSON facts (~13 seconds)
bash scripts/observatory/observatory.sh --facts
```

### Available Fact Files

| Fact File | Content | Use Case |
|-----------|---------|----------|
| `modules.json` | Module list, versions | Understanding project structure |
| `reactor.json` | Build order, dependencies | Build optimization |
| `shared-src.json` | Source file ownership | Code navigation |
| `tests.json` | Test counts per module | Test coverage planning |
| `dual-family.json` | Stateful/stateless mapping | Architecture understanding |
| `duplicates.json` | Duplicate class names | Refactoring targets |
| `gates.json` | Quality gate status | CI/CD configuration |
| `coverage.json` | Coverage metrics | Quality metrics |
| `deps-conflicts.json` | Dependency version conflicts | Dependency management |

### Observatory Output Structure

```
docs/v6/latest/
+-- INDEX.md                    # Navigation index
+-- facts/
|   +-- modules.json            # Module definitions
|   +-- reactor.json            # Maven reactor info
|   +-- shared-src.json         # Source file mapping
|   +-- tests.json              # Test inventory
|   +-- dual-family.json        # Engine variants
|   +-- duplicates.json         # Duplicate FQCNs
|   +-- gates.json              # Quality gates
|   +-- coverage.json           # Coverage data
|   +-- static-analysis.json    # SpotBugs/PMD results
|   +-- deps-conflicts.json     # Dependency issues
+-- diagrams/
|   +-- build-order.mmd         # Reactor build order
|   +-- module-dependencies.mmd # Dependency graph
|   +-- quality-gates.mmd       # Quality gate status
|   +-- 50-risk-surfaces.mmd    # FMEA risk analysis
+-- receipts/
|   +-- observatory.json        # SHA256 hashes + metadata
+-- performance/
    +-- summary.json            # Performance metrics
```

### Reading Facts

```bash
# Read specific fact
cat docs/v6/latest/facts/modules.json | python3 -m json.tool

# Quick module list
jq '.modules[].name' docs/v6/latest/facts/modules.json

# Check coverage
jq '.aggregate' docs/v6/latest/facts/coverage.json
```

---

## 8. Generating SBOM

Software Bill of Materials (SBOM) is generated using CycloneDX.

### Generate SBOM

```bash
# Generate SBOM (CycloneDX format)
mvn cyclonedx:makeBom

# Output: target/bom.json and target/bom.xml
```

### Security Profile (SBOM + OWASP Check)

```bash
# Full security scan with SBOM
mvn clean verify -P security

# This includes:
# - OWASP Dependency Check
# - CycloneDX SBOM generation
# - Vulnerability scanning
```

### SBOM Locations

```
target/bom.json                    # CycloneDX JSON format
target/bom.xml                     # CycloneDX XML format
target/site/dependency-check/      # OWASP report
```

### Verify SBOM

```bash
# Validate SBOM format
cyclonedx validate --input-file target/bom.json

# View dependency count
jq '.components | length' target/bom.json
```

---

## 9. Quick Reference Commands

### Development Workflow

```bash
# Fast build-test loop (changed modules only)
bash scripts/dx.sh                 # Compile + test
bash scripts/dx.sh compile         # Compile only
bash scripts/dx.sh all             # All modules

# Standard Maven
mvn -T 1.5C clean compile          # Parallel compile
mvn -T 1.5C clean test             # Parallel tests
mvn -T 1.5C clean package          # Full build
```

### Validation Commands

```bash
# Coverage
mvn -T 1.5C clean test -Djacoco.skip=false
bash scripts/observatory/observatory.sh --facts
bash scripts/coverage-report.sh

# Static analysis
mvn -T 1.5C clean verify -P analysis

# Security
mvn clean verify -P security

# Observatory
bash scripts/observatory/observatory.sh

# SBOM
mvn cyclonedx:makeBom
```

### Verification Commands

```bash
# Environment check
bash scripts/migrate-to-java25.sh --verify-only

# Quick validation
bash scripts/dx.sh all

# Full validation pipeline
mvn -T 1.5C clean verify -P analysis && \
bash scripts/observatory/observatory.sh && \
mvn cyclonedx:makeBom
```

---

## 10. Troubleshooting

### "Java version mismatch" Error

**Symptom:** Build fails with "invalid source release: 25"

**Solution:**
```bash
# Check Java version
java -version
# Must show version 25+

# If wrong version, check JAVA_HOME
echo $JAVA_HOME

# Set correct JAVA_HOME (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 25)

# Set correct JAVA_HOME (Linux)
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk

# Verify
java -version
```

### "Unable to locate a Java Runtime" Error

**Symptom:** `java -version` fails with "Unable to locate a Java Runtime"

**Solution:**
```bash
# Install Java 25 (see Section 2)

# After installation, reload shell
source ~/.zshrc  # or ~/.bashrc

# Verify path
which java
java -version
```

### "Maven out of memory" Error

**Symptom:** Build fails with `OutOfMemoryError: Java heap space`

**Solution:**
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx4g -XX:+UseZGC"

# Or edit .mvn/jvm.config
echo "-Xmx4g" >> .mvn/jvm.config
```

### Tests Fail with Database Errors

**Symptom:** Tests fail with H2/PostgreSQL connection errors

**Solution:**
```bash
# Verify H2 is configured (default for testing)
grep database.type build/build.properties
# Should show: h2

# Or set via system property
mvn test -Ddatabase.type=h2
```

### JaCoCo Reports Not Generated

**Symptom:** `target/site/jacoco/` directory is empty

**Solution:**
```bash
# Ensure JaCoCo is enabled
mvn test -Djacoco.skip=false

# Generate report explicitly
mvn jacoco:report

# Check report location
ls -la yawl-engine/target/site/jacoco/
```

### Observatory Fails

**Symptom:** `observatory.sh` returns errors

**Solution:**
```bash
# Ensure project is compiled first
mvn compile

# Check prerequisites
which python3
which jq

# Run with verbose output
bash -x scripts/observatory/observatory.sh --facts
```

### SpotBugs/PMD Check Failures

**Symptom:** `mvn verify -P analysis` fails on static analysis

**Solution:**
```bash
# View specific violations
cat target/spotbugs/spotbugs.xml
cat target/pmd.xml

# Generate HTML reports for easier viewing
mvn spotbugs:spotbugs pmd:pmd site

# Reports at:
# target/site/spotbugs/index.html
# target/site/pmd/index.html
```

### Slow Build Times

**Symptom:** Builds take > 5 minutes

**Solution:**
```bash
# Use parallel builds
mvn -T 1.5C clean compile

# Use dx.sh for incremental builds
bash scripts/dx.sh

# Enable Maven daemon (optional)
mvnd --version  # If mvnd is installed
```

---

## Summary Checklist

After completing setup, verify you can run:

```bash
# 1. Environment check
java -version                      # Shows 25.x
mvn -version                       # Shows 3.9.x

# 2. Compile succeeds
mvn -T 1.5C clean compile          # BUILD SUCCESS

# 3. Tests pass
mvn -T 1.5C clean test             # Tests run, BUILD SUCCESS

# 4. Coverage works
mvn test -Djacoco.skip=false       # Coverage reports generated
ls yawl-engine/target/site/jacoco/  # index.html exists

# 5. Static analysis works
mvn verify -P analysis             # SpotBugs/PMD run

# 6. Observatory works
bash scripts/observatory/observatory.sh --facts   # Facts generated
ls docs/v6/latest/facts/           # JSON files exist

# 7. SBOM generation works
mvn cyclonedx:makeBom              # target/bom.json created
ls target/bom.json                 # File exists
```

All checks pass? You're ready to generate validation data for YAWL v6.

---

## Next Steps

1. **Run full validation pipeline:**
   ```bash
   mvn -T 1.5C clean verify -P analysis
   bash scripts/observatory/observatory.sh
   mvn cyclonedx:makeBom
   ```

2. **Review outputs:**
   - Coverage: `yawl-engine/target/site/jacoco/index.html`
   - Analysis: `target/site/spotbugs/index.html`, `target/site/pmd/index.html`
   - Facts: `docs/v6/latest/facts/*.json`
   - SBOM: `target/bom.json`

3. **For CI/CD integration**, see:
   - `docs/BUILD.md` for build commands
   - `.github/workflows/` for workflow examples
   - `docs/deployment/JAVA25-GUIDE.md` for deployment

---

**Questions?** See:
- Build documentation: `docs/BUILD.md`
- Java 25 guide: `docs/deployment/JAVA25-GUIDE.md`
- Observatory docs: `.claude/OBSERVATORY.md`
- Quick start: `.claude/README-QUICK.md`
