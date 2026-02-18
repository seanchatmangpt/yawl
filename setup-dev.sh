#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0 - One-Command Local Development Setup
#
# Usage:
#   ./setup-dev.sh           # Full setup (recommended for first-time use)
#   ./setup-dev.sh --verify  # Verify existing setup without making changes
#   ./setup-dev.sh --hooks   # Install git hooks only
#   ./setup-dev.sh --env     # Generate .env file only
#
# What this script does:
#   1. Verifies Java 21+ and Maven 3.9+ are present
#   2. Verifies JAVA_HOME is set correctly
#   3. Installs git pre-commit hook for HYPER_STANDARDS enforcement
#   4. Copies .env.example to .env if no .env exists
#   5. Warms the local Maven repository cache (mvn dependency:resolve)
#   6. Runs a fast compile to confirm the environment is working
#
# Requirements: Java 21+, Maven 3.9+, bash 4+, git
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Terminal colours (disabled when not connected to a tty)
# ---------------------------------------------------------------------------
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REQUIRED_JAVA_MAJOR=21
REQUIRED_MAVEN_MAJOR=3
REQUIRED_MAVEN_MINOR=9
HOOKS_SRC="${PROJECT_DIR}/.claude/pre-commit-hook"
HOOKS_DST="${PROJECT_DIR}/.git/hooks/pre-commit"
ENV_EXAMPLE="${PROJECT_DIR}/.env.example"
ENV_FILE="${PROJECT_DIR}/.env"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
fatal()   { error "$*"; exit 1; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}  $*${NC}"
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo ""
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
VERIFY_ONLY=false
HOOKS_ONLY=false
ENV_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --verify) VERIFY_ONLY=true ;;
        --hooks)  HOOKS_ONLY=true ;;
        --env)    ENV_ONLY=true ;;
        --help|-h)
            echo "Usage: $0 [--verify|--hooks|--env|--help]"
            echo ""
            echo "  (no flags)  Full development setup"
            echo "  --verify    Verify tools are present; exit non-zero if not"
            echo "  --hooks     Install git hooks only"
            echo "  --env       Create .env from .env.example only"
            exit 0
            ;;
        *)
            fatal "Unknown argument: $arg. Use --help for usage."
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Step 1: Verify Java
# ---------------------------------------------------------------------------
check_java() {
    header "Checking Java"

    if ! command -v java &>/dev/null; then
        fatal "Java not found on PATH. Install Java ${REQUIRED_JAVA_MAJOR}+ from https://adoptium.net or use SDKMAN: sdk install java"
    fi

    JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -1)"
    # Extract major version: handles both "21.0.x" and "25" style strings
    JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"

    if [ -z "${JAVA_MAJOR}" ]; then
        fatal "Could not determine Java major version from: ${JAVA_VERSION_OUTPUT}"
    fi

    if [ "${JAVA_MAJOR}" -lt "${REQUIRED_JAVA_MAJOR}" ]; then
        fatal "Java ${REQUIRED_JAVA_MAJOR}+ required, found Java ${JAVA_MAJOR}. Output: ${JAVA_VERSION_OUTPUT}"
    fi

    success "Java ${JAVA_MAJOR} found: $(java -version 2>&1 | head -1)"

    if [ -z "${JAVA_HOME:-}" ]; then
        warn "JAVA_HOME is not set. Maven may not find the correct JDK."
        warn "Set JAVA_HOME to the JDK root directory, e.g.:"
        warn "  export JAVA_HOME=\$(java -XshowSettings:property -version 2>&1 | grep java.home | awk '{print \$3}')"
    else
        success "JAVA_HOME=${JAVA_HOME}"
    fi
}

# ---------------------------------------------------------------------------
# Step 2: Verify Maven
# ---------------------------------------------------------------------------
check_maven() {
    header "Checking Maven"

    if ! command -v mvn &>/dev/null; then
        fatal "Maven not found on PATH. Install Maven ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}+ from https://maven.apache.org/download.cgi"
    fi

    # grep for the "Apache Maven" line explicitly to skip JVM noise on stderr that
    # leaks into stdout on some JVM builds (e.g. ZGC "Failed to uncommit memory").
    MVN_VERSION_LINE="$(mvn --version 2>&1 | grep -m1 'Apache Maven')"
    if [ -z "${MVN_VERSION_LINE}" ]; then
        fatal "Could not parse Maven version from 'mvn --version' output."
    fi
    MVN_VERSION="$(echo "${MVN_VERSION_LINE}" | sed -E 's/Apache Maven ([0-9]+\.[0-9]+\.[0-9]+).*/\1/')"
    MVN_MAJOR="$(echo "${MVN_VERSION}" | cut -d. -f1)"
    MVN_MINOR="$(echo "${MVN_VERSION}" | cut -d. -f2)"

    if [ "${MVN_MAJOR}" -lt "${REQUIRED_MAVEN_MAJOR}" ] || \
       { [ "${MVN_MAJOR}" -eq "${REQUIRED_MAVEN_MAJOR}" ] && [ "${MVN_MINOR}" -lt "${REQUIRED_MAVEN_MINOR}" ]; }; then
        fatal "Maven ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}+ required, found ${MVN_VERSION}."
    fi

    success "Maven ${MVN_VERSION} found: ${MVN_VERSION_LINE}"
}

# ---------------------------------------------------------------------------
# Step 3: Install git pre-commit hook
# ---------------------------------------------------------------------------
install_hooks() {
    header "Installing Git Hooks"

    if [ ! -d "${PROJECT_DIR}/.git" ]; then
        warn "No .git directory found at ${PROJECT_DIR}. Skipping hook installation."
        return
    fi

    if [ ! -f "${HOOKS_SRC}" ]; then
        fatal "Hook source not found at ${HOOKS_SRC}. Repository may be incomplete."
    fi

    mkdir -p "${PROJECT_DIR}/.git/hooks"

    if [ -f "${HOOKS_DST}" ] && [ ! -L "${HOOKS_DST}" ]; then
        warn "Existing pre-commit hook at ${HOOKS_DST} — backing up to ${HOOKS_DST}.bak"
        cp "${HOOKS_DST}" "${HOOKS_DST}.bak"
    fi

    # Install as a symlink so updates to .claude/pre-commit-hook propagate automatically
    ln -sf "../../.claude/pre-commit-hook" "${HOOKS_DST}"
    chmod +x "${HOOKS_DST}"

    success "Pre-commit hook installed: ${HOOKS_DST} -> ${HOOKS_SRC}"
    info "The hook enforces HYPER_STANDARDS: no mocks, stubs, TODOs, or silent fallbacks."
}

# ---------------------------------------------------------------------------
# Step 4: Create .env from .env.example
# ---------------------------------------------------------------------------
setup_env() {
    header "Environment Configuration"

    if [ ! -f "${ENV_EXAMPLE}" ]; then
        warn ".env.example not found at ${ENV_EXAMPLE}. Skipping .env setup."
        return
    fi

    if [ -f "${ENV_FILE}" ]; then
        success ".env already exists at ${ENV_FILE}. Not overwriting."
        info "To regenerate, delete ${ENV_FILE} and re-run this script."
        return
    fi

    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    success ".env created from .env.example at ${ENV_FILE}"
    warn "Review ${ENV_FILE} and set real values before starting services."
}

# ---------------------------------------------------------------------------
# Step 5: Warm Maven dependency cache
# ---------------------------------------------------------------------------
warm_maven_cache() {
    header "Warming Maven Dependency Cache"

    info "Resolving all dependencies (first run may take several minutes)..."
    cd "${PROJECT_DIR}"

    if mvn dependency:resolve \
        --batch-mode \
        --no-transfer-progress \
        -q \
        2>&1; then
        success "Maven dependency cache warmed."
    else
        warn "Some dependencies could not be resolved. Check network access and ~/.m2/settings.xml."
        warn "Build may still succeed if dependencies are already cached."
    fi
}

# ---------------------------------------------------------------------------
# Step 6: Fast compile verification
# ---------------------------------------------------------------------------
verify_compile() {
    header "Verifying Compilation"

    info "Running fast compile check (mvn clean compile)..."
    cd "${PROJECT_DIR}"

    if mvn clean compile \
        --batch-mode \
        --no-transfer-progress \
        -q \
        2>&1; then
        success "Compilation succeeded."
    else
        error "Compilation failed. Review the output above for errors."
        error "Common issues:"
        error "  - JAVA_HOME not pointing to a JDK (not JRE)"
        error "  - Java version mismatch (need ${REQUIRED_JAVA_MAJOR}+)"
        error "  - Missing dependencies (run: mvn dependency:resolve)"
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
header "YAWL v6.0 Development Environment Setup"
info "Project root: ${PROJECT_DIR}"

if $VERIFY_ONLY; then
    check_java
    check_maven
    success "Environment verification complete."
    exit 0
fi

if $HOOKS_ONLY; then
    install_hooks
    exit 0
fi

if $ENV_ONLY; then
    setup_env
    exit 0
fi

# Full setup
check_java
check_maven
install_hooks
setup_env
warm_maven_cache
verify_compile

echo ""
echo -e "${BOLD}${GREEN}==============================================================================${NC}"
echo -e "${BOLD}${GREEN}  Setup complete!${NC}"
echo -e "${BOLD}${GREEN}==============================================================================${NC}"
echo ""
echo -e "  ${BOLD}Quick commands:${NC}"
echo -e "    ${CYAN}./build.sh${NC}             Build (compile only)"
echo -e "    ${CYAN}./test.sh${NC}              Run unit tests"
echo -e "    ${CYAN}./test.sh --coverage${NC}   Run tests with JaCoCo coverage"
echo -e "    ${CYAN}make test-quick${NC}        Shell-based quick tests"
echo -e "    ${CYAN}make help${NC}              Full list of make targets"
echo ""
echo -e "  ${BOLD}Documentation:${NC}"
echo -e "    DEVELOPMENT.md         Full contributor guide"
echo -e "    DEVELOPER_GUIDE.md     Architecture deep-dive"
echo -e "    CLAUDE.md              Coding standards (HYPER_STANDARDS)"
echo ""
