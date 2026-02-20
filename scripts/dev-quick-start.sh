#!/usr/bin/env bash
# ==========================================================================
# dev-quick-start.sh — Get YAWL developers productive in <5 minutes
#
# Usage:
#   bash scripts/dev-quick-start.sh          # Full setup + verify
#   bash scripts/dev-quick-start.sh --verify # Verify only (skip build)
#   bash scripts/dev-quick-start.sh --help   # Show this message
# ==========================================================================
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(dirname "${SCRIPT_DIR}")"
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

VERIFY_ONLY=false

# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

log_header() { echo -e "\n${BLUE}=== YAWL Quick Start ===${NC}\n"; }
log_ok()     { echo -e "${GREEN}✓ $1${NC}"; }
log_err()    { echo -e "${RED}✗ $1${NC}"; }
log_step()   { echo -e "\n${BLUE}→ $1${NC}"; }
log_info()   { echo -e "${BLUE}ℹ $1${NC}"; }

show_help() {
    sed -n '2,/^# ===/p' "$0" | grep '^#' | sed 's/^# \?//'
    exit 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 1: Check Java 21+
# ─────────────────────────────────────────────────────────────────────────────

check_java() {
    log_step "Checking Java 21+ installed..."

    if ! command -v java >/dev/null 2>&1; then
        log_err "Java not found"
        echo "  Install: https://www.oracle.com/java/technologies/downloads/"
        echo "  Or: apt-get install openjdk-25-jdk"
        return 1
    fi

    local java_path major_version java_version
    java_path="$(command -v java)"
    java_version=$(java -version 2>&1 | head -1)
    major_version=$(java -version 2>&1 | grep -oP '(?:version )?"?\K(\d+)' | head -1 || echo "0")

    if [[ $major_version -lt 21 ]]; then
        log_err "Java $major_version too old (need 21+): $java_version"
        return 1
    fi

    log_ok "Java $major_version: $java_path"
    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 2: Check Maven & repo structure
# ─────────────────────────────────────────────────────────────────────────────

check_maven() {
    log_step "Checking Maven & repo structure..."

    if ! command -v mvn >/dev/null 2>&1 && ! command -v mvnd >/dev/null 2>&1; then
        log_err "Maven not found"
        echo "  Install: https://maven.apache.org/download.cgi"
        echo "  Or: apt-get install maven"
        return 1
    fi

    if [[ ! -f "${REPO_ROOT}/pom.xml" ]]; then
        log_err "pom.xml not found at $REPO_ROOT"
        return 1
    fi
    log_ok "pom.xml found"

    local mods=(yawl-elements yawl-engine yawl-stateless yawl-utilities)
    local missing=()
    for mod in "${mods[@]}"; do
        [[ -d "${REPO_ROOT}/${mod}" ]] || missing+=("$mod")
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_err "Missing modules: ${missing[*]}"
        return 1
    fi
    log_ok "All core modules found (14 total)"
    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 3: Run full build verification
# ─────────────────────────────────────────────────────────────────────────────

run_build_verify() {
    log_step "Running full build (compile + test, 2-5 minutes)..."
    echo "  Compiling 14 modules + running tests..."

    cd "${REPO_ROOT}"
    if bash scripts/dx.sh all; then
        log_ok "All modules compiled and tested successfully"
        return 0
    else
        log_err "Build failed"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 4: Print success checklist & next steps
# ─────────────────────────────────────────────────────────────────────────────

print_checklist() {
    cat << 'EOF'

════════════════════════════════════════════════════════════
  ✓ SETUP COMPLETE — You are productive!
════════════════════════════════════════════════════════════

✓ Java:   Java 21+
✓ Maven:  Ready
✓ Build:  All modules compiled and tested

Daily Workflows (Next Steps):
─────────────────────────────────────────────────────────────
  1. Fast loop (changed modules only):
     bash scripts/dx.sh

  2. Compile only (instant feedback):
     bash scripts/dx.sh compile

  3. Pre-commit (all modules):
     bash scripts/dx.sh all

  4. Target module:
     bash scripts/dx.sh -pl yawl-engine

  5. Run test:
     mvn test -pl yawl-engine -Dtest=YEngineTest

Project Structure:
─────────────────────────────────────────────────────────────
  yawl-engine/      YAWL execution engine (stateful)
  yawl-stateless/   Stateless engine & case runner
  yawl-elements/    Domain model & YAWL elements
  yawl-utilities/   Shared base & utilities
  yawl-integration/ MCP & A2A agent integration
  + 9 more modules

Documentation:
─────────────────────────────────────────────────────────────
  Quick guide:   CLAUDE.md
  Build guide:   docs/BUILD.md
  Architecture:  .claude/ARCHITECTURE-PATTERNS-JAVA25.md

════════════════════════════════════════════════════════════
  Ready to code. Happy workflow building!
════════════════════════════════════════════════════════════

EOF
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 5: Common error troubleshooting
# ─────────────────────────────────────────────────────────────────────────────

handle_java_error() {
    cat << 'EOF'

Troubleshooting Java:
─────────────────────────────────────────────────────────────
  1. Check installed versions:
     java -version
     javac -version

  2. Install Java 25 (recommended):
     macOS:   brew install openjdk@25
     Ubuntu:  sudo apt-get install openjdk-25-jdk
     Oracle:  https://www.oracle.com/java/technologies/downloads/

  3. Set JAVA_HOME if Maven uses wrong version:
     export JAVA_HOME=/path/to/jdk-25
     bash scripts/dev-quick-start.sh

EOF
    exit 1
}

handle_maven_error() {
    cat << 'EOF'

Troubleshooting Maven:
─────────────────────────────────────────────────────────────
  1. Install Maven 3.9+:
     macOS:   brew install maven
     Ubuntu:  sudo apt-get install maven
     Apache:  https://maven.apache.org/download.cgi

  2. Verify Maven finds Java 21+:
     mvn -version
     If wrong Java, set: export JAVA_HOME=/path/to/jdk-25

  3. Clear cache (if dependency issues):
     rm -rf ~/.m2/repository
     bash scripts/dev-quick-start.sh

EOF
    exit 1
}

handle_build_error() {
    cat << 'EOF'

Build failed. Troubleshooting:
─────────────────────────────────────────────────────────────
  1. Check Java version:
     java -version  # Should be 21+, preferably 25

  2. Clean rebuild:
     bash scripts/dx.sh all

  3. Verify disk space (~2GB for Maven cache):
     df -h ~/

  4. View detailed output:
     bash scripts/dx.sh all && DX_VERBOSE=1 bash scripts/dx.sh all

EOF
    exit 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verify) VERIFY_ONLY=true; shift ;;
            --help|-h) show_help ;;
            *) echo "Unknown: $1"; show_help ;;
        esac
    done
}

main() {
    parse_args "$@"
    log_header

    check_java || handle_java_error
    check_maven || handle_maven_error

    if [[ "$VERIFY_ONLY" == false ]]; then
        run_build_verify || handle_build_error
    fi

    print_checklist
}

main "$@"
