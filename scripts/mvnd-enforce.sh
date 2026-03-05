#!/usr/bin/env bash
# YAWL Maven Daemon (mvnd) Enforcement
# =====================================
# CRITICAL: mvnd (Maven Daemon) is REQUIRED for all builds.
# This follows Toyota production system principles: strict standards, no exceptions.
#
# This script:
# 1. Verifies mvnd is installed
# 2. Verifies mvnd daemon is running
# 3. Fails HARD if mvnd is not available
# 4. Provides clear error messages and installation instructions
#
# Usage: source scripts/mvnd-enforce.sh  (before running mvnd/mvn)
# Exit codes: 0 = mvnd ready, 2 = FATAL (mvnd not available or not running)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}YAWL Maven Daemon (mvnd) - Strict Requirement Check${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Step 1: Check if mvnd is installed
echo -e "${BOLD}[1/3]${NC} Checking mvnd installation..."
if ! command -v mvnd &> /dev/null; then
    echo -e "${RED}❌ FATAL ERROR: mvnd (Maven Daemon) not found in PATH${NC}"
    echo ""
    echo "YAWL v6.0.0 requires mvnd for Toyota production system compliance."
    echo "mvnd is MANDATORY - no fallback to mvn allowed."
    echo ""
    echo -e "${BOLD}Installation Instructions:${NC}"
    echo ""
    echo "Option 1: SDKMAN (Recommended)"
    echo "  $ sdk install maven-mvnd"
    echo "  $ mvnd --version"
    echo ""
    echo "Option 2: Manual Download"
    echo "  $ curl -s https://github.com/apache/maven-mvnd/releases/download/0.9.1/maven-mvnd-0.9.1-linux-x86_64.tar.gz | tar xz -C ~/.local/bin"
    echo "  $ ~/.local/bin/maven-mvnd-0.9.1/bin/mvnd --version"
    echo ""
    echo "Option 3: Homebrew (macOS)"
    echo "  $ brew install maven-mvnd"
    echo ""
    echo -e "${BOLD}After installation, run:${NC}"
    echo "  $ mvnd --version  (to verify)"
    echo "  $ mvnd clean compile  (to start daemon)"
    echo ""
    exit 2
fi

MVND_VERSION=$(mvnd --version 2>/dev/null | head -n1 || echo "unknown")
echo -e "${GREEN}✅ mvnd installed: ${MVND_VERSION}${NC}"
echo ""

# Step 2: Check if mvnd daemon is running
echo -e "${BOLD}[2/3]${NC} Checking mvnd daemon status..."
MVND_STATUS=$(mvnd --status 2>/dev/null || echo "NOT_RUNNING")

if echo "${MVND_STATUS}" | grep -q "listening on"; then
    DAEMON_COUNT=$(echo "${MVND_STATUS}" | grep -c "listening on" || echo "1")
    echo -e "${GREEN}✅ mvnd daemon running (${DAEMON_COUNT} instance(s))${NC}"
    echo "   ${MVND_STATUS}" | head -1
    echo ""
else
    echo -e "${YELLOW}⚠️  mvnd daemon not running - starting daemon...${NC}"

    # Start the daemon
    if mvnd clean compile -q --no-transfer-progress -B -pl yawl-utilities 2>&1 | head -5; then
        echo -e "${GREEN}✅ mvnd daemon started successfully${NC}"
        echo ""
    else
        echo -e "${RED}❌ FATAL ERROR: Could not start mvnd daemon${NC}"
        echo "   Run 'mvnd --help' for troubleshooting"
        exit 2
    fi
fi

# Step 3: Verify Maven 4 is configured
echo -e "${BOLD}[3/3]${NC} Verifying Maven 4 configuration..."
if [ -f "${REPO_ROOT}/.mvn/maven.config" ]; then
    if grep -q "^-b concurrent" "${REPO_ROOT}/.mvn/maven.config"; then
        echo -e "${GREEN}✅ Maven 4 concurrent builder enabled${NC}"
    else
        echo -e "${YELLOW}⚠️  Maven 4 concurrent builder NOT enabled${NC}"
        echo "   Edit .mvn/maven.config and uncomment: -b concurrent"
    fi
else
    echo -e "${RED}❌ Maven configuration missing: .mvn/maven.config${NC}"
    exit 2
fi

echo ""
echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}${BOLD}✅ mvnd is ready. All YAWL builds are Toyota production system compliant.${NC}"
echo -e "${GREEN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

exit 0
