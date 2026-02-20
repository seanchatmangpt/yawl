#!/usr/bin/env bash
# ==========================================================================
# dev-quick-start.sh — Get YAWL developers productive in <5 minutes
#
# One script. Five key checks. Full build verification. Zero external deps.
#
# Usage:
#   bash scripts/dev-quick-start.sh          # Full setup + verify
#   bash scripts/dev-quick-start.sh --verify # Verify existing setup only
#   bash scripts/dev-quick-start.sh --help   # Show this message
#
# What it does:
#   1. Check Java 21+ installed (required for YAWL v6)
#   2. Validate Maven/JDK environment
#   3. Run dx.sh all (compile + test all modules)
#   4. Print success checklist with next steps
#   5. Handle common errors gracefully
#
# Expected: 5 minutes (cold Maven cache) to 2 minutes (warm cache)
# ==========================================================================
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(dirname "${SCRIPT_DIR}")"
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Global state
JAVA_VERSION=""
JAVA_PATH=""
MAVEN_CMD=""
VERIFY_ONLY=false
SETUP_SUCCESS=false

# ─────────────────────────────────────────────────────────────────────────────
# Helper functions
# ─────────────────────────────────────────────────────────────────────────────

print_header() {
    echo -e "\n${BLUE}=== YAWL Dev Quick Start ===${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_step() {
    echo -e "\n${BLUE}→ $1${NC}"
}

show_help() {
    sed -n '2,/^# ===/p' "$0" | grep '^#' | sed 's/^# \?//'
    exit 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 1: Check Java version
# ─────────────────────────────────────────────────────────────────────────────

check_java() {
    print_step "Checking Java version..."

    if ! command -v java >/dev/null 2>&1; then
        print_error "Java not found. Install JDK 21+ from:"
        echo "  https://www.oracle.com/java/technologies/downloads/"
        echo "  or use: apt-get install openjdk-25-jdk (Linux)"
        exit 1
    fi

    JAVA_PATH="$(command -v java)"
    JAVA_VERSION=$(java -version 2>&1 | head -1)

    # Extract major version (works for all Java versions)
    local major_version
    major_version=$(java -version 2>&1 | grep -oP '(?:version )?"?\K(\d+)' | head -1 || echo "0")

    if [[ $major_version -lt 21 ]]; then
        print_error "Java version too old: $JAVA_VERSION"
        print_error "YAWL v6.0 requires Java 21 or newer (preferably 25)"
        echo "  Current: $JAVA_PATH"
        echo "  Version: $JAVA_VERSION"
        exit 1
    fi

    print_success "Java $major_version found: $JAVA_PATH"
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 2: Validate Maven environment
# ─────────────────────────────────────────────────────────────────────────────

check_maven() {
    print_step "Checking Maven environment..."

    # Prefer mvnd (Maven daemon) for speed, fallback to mvn
    if command -v mvnd >/dev/null 2>&1; then
        MAVEN_CMD="mvnd"
        print_success "Maven Daemon (mvnd) found — builds will be faster"
    elif command -v mvn >/dev/null 2>&1; then
        MAVEN_CMD="mvn"
        print_info "Maven (mvn) found — first build may take longer"
    else
        print_error "Maven not found. Install from:"
        echo "  https://maven.apache.org/download.cgi"
        echo "  or use: apt-get install maven (Linux)"
        exit 1
    fi

    # Validate pom.xml
    if [[ ! -f "${REPO_ROOT}/pom.xml" ]]; then
        print_error "pom.xml not found at ${REPO_ROOT}"
        exit 1
    fi
    print_success "Parent pom.xml found"

    # Validate key modules exist
    local modules=(yawl-elements yawl-engine yawl-stateless yawl-utilities)
    local missing=()
    for mod in "${modules[@]}"; do
        if [[ ! -d "${REPO_ROOT}/${mod}" ]]; then
            missing+=("$mod")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        print_error "Missing modules: ${missing[*]}"
        exit 1
    fi
    print_success "All required modules found"
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 3: Run build verification (dx.sh all)
# ─────────────────────────────────────────────────────────────────────────────

run_build_verify() {
    print_step "Running full build verification (this takes 2-5 minutes on first run)..."
    echo "  Compiling 14 modules + running tests..."
    echo ""

    # Change to repo root (dx.sh expects this)
    cd "${REPO_ROOT}"

    # Run dx.sh with all modules
    if bash scripts/dx.sh all; then
        SETUP_SUCCESS=true
        print_success "Build verification completed successfully"
    else
        print_error "Build failed. See errors above."
        exit 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 4: Print success checklist
# ─────────────────────────────────────────────────────────────────────────────

print_checklist() {
    echo ""
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  SETUP COMPLETE - You are now productive!${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${GREEN}✓ Java:${NC}   $JAVA_VERSION"
    echo -e "${GREEN}✓ Maven:${NC}  $MAVEN_CMD"
    echo -e "${GREEN}✓ Build:${NC}  All modules compiled and tested"
    echo ""

    echo "Next Steps:"
    echo "─────────────────────────────────────────────────────────────────"
    echo ""
    echo "1. Daily development loop (test only changed modules):"
    echo "   bash scripts/dx.sh"
    echo ""
    echo "2. Compile only (fastest feedback):"
    echo "   bash scripts/dx.sh compile"
    echo ""
    echo "3. Pre-commit verification (all modules):"
    echo "   bash scripts/dx.sh all"
    echo ""
    echo "4. Target specific module:"
    echo "   bash scripts/dx.sh -pl yawl-engine"
    echo ""
    echo "5. Run specific test:"
    echo "   mvn test -pl yawl-engine -Dtest=YEngineTest"
    echo ""

    echo "Project Structure:"
    echo "─────────────────────────────────────────────────────────────────"
    echo "  $REPO_ROOT/"
    echo "  ├─ yawl-engine/       YAWL execution engine (stateful)"
    echo "  ├─ yawl-stateless/    Stateless engine & case runner"
    echo "  ├─ yawl-elements/     Domain model & YAWL elements"
    echo "  ├─ yawl-utilities/    Shared utilities & base classes"
    echo "  ├─ yawl-integration/  MCP & A2A agent integration"
    echo "  ├─ yawl-resourcing/   Resource & queue management"
    echo "  ├─ yawl-scheduling/   Task scheduling & calendar"
    echo "  └─ ... (8 more modules)"
    echo ""

    echo "Documentation:"
    echo "─────────────────────────────────────────────────────────────────"
    echo "  Quick reference:  .claude/README-QUICK.md"
    echo "  Build guide:      docs/BUILD.md"
    echo "  Architecture:     .claude/ARCHITECTURE-PATTERNS-JAVA25.md"
    echo "  YAWL guide:       CLAUDE.md"
    echo ""

    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}Ready to code. Happy workflow building!${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo ""
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 5: Handle common errors
# ─────────────────────────────────────────────────────────────────────────────

handle_java_error() {
    local msg="$1"
    print_error "$msg"
    echo ""
    echo "Troubleshooting Java issues:"
    echo "─────────────────────────────────────────────────────────────────"
    echo ""
    echo "1. Check installed versions:"
    echo "   java -version"
    echo "   javac -version"
    echo ""
    echo "2. Install Java 25 (recommended):"
    echo "   # macOS"
    echo "   brew install openjdk@25"
    echo ""
    echo "   # Ubuntu/Debian"
    echo "   sudo apt-get install openjdk-25-jdk"
    echo ""
    echo "   # Or from Oracle"
    echo "   https://www.oracle.com/java/technologies/downloads/"
    echo ""
    echo "3. Set JAVA_HOME if Maven uses wrong version:"
    echo "   export JAVA_HOME=/path/to/jdk-25"
    echo "   bash scripts/dev-quick-start.sh"
    echo ""
    exit 1
}

handle_maven_error() {
    local msg="$1"
    print_error "$msg"
    echo ""
    echo "Troubleshooting Maven issues:"
    echo "─────────────────────────────────────────────────────────────────"
    echo ""
    echo "1. Install Maven 3.9+"
    echo "   # macOS"
    echo "   brew install maven"
    echo ""
    echo "   # Ubuntu/Debian"
    echo "   sudo apt-get install maven"
    echo ""
    echo "   # Or from Apache"
    echo "   https://maven.apache.org/download.cgi"
    echo ""
    echo "2. Verify Maven picks up correct Java:"
    echo "   mvn -version"
    echo ""
    echo "   Should show Java >= 21. If not, set JAVA_HOME:"
    echo "   export JAVA_HOME=/path/to/jdk-25"
    echo ""
    echo "3. Clear Maven cache and retry (if dependency issues):"
    echo "   rm -rf ~/.m2/repository"
    echo "   bash scripts/dev-quick-start.sh"
    echo ""
    exit 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Parse arguments
# ─────────────────────────────────────────────────────────────────────────────

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verify)
                VERIFY_ONLY=true
                shift
                ;;
            --help|-h)
                show_help
                ;;
            *)
                echo "Unknown argument: $1"
                show_help
                ;;
        esac
    done
}

# ─────────────────────────────────────────────────────────────────────────────
# Main entry point
# ─────────────────────────────────────────────────────────────────────────────

main() {
    parse_args "$@"

    print_header

    # Always check Java and Maven
    if ! check_java 2>/dev/null; then
        handle_java_error "Java setup failed"
    fi

    if ! check_maven 2>/dev/null; then
        handle_maven_error "Maven setup failed"
    fi

    # Run build verification unless --verify-only requested
    if [[ "$VERIFY_ONLY" == false ]]; then
        if ! run_build_verify; then
            print_error "Build verification failed"
            echo ""
            echo "Troubleshooting:"
            echo "─────────────────────────────────────────────────────────────────"
            echo ""
            echo "1. Check Java version compatibility:"
            echo "   java -version  # Should be 21+, preferably 25"
            echo ""
            echo "2. Clean rebuild (if incremental build fails):"
            echo "   bash scripts/dx.sh all"
            echo ""
            echo "3. Check disk space (Maven cache needs ~2GB):"
            echo "   df -h ~/"
            echo ""
            echo "4. View detailed error output:"
            echo "   bash scripts/dx.sh all --verbose"
            echo ""
            exit 1
        fi
    fi

    # Print success info if build succeeded
    if [[ "$SETUP_SUCCESS" == true ]] || [[ "$VERIFY_ONLY" == true ]]; then
        print_checklist
    fi
}

# Run main
main "$@"
