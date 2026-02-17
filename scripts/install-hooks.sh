#!/bin/bash
# YAWL v5.2 Git Hooks Installer
#
# Installs the YAWL quality gate hooks into .git/hooks/.
# Run this once after cloning or when hooks are updated.
#
# Usage:
#   bash scripts/install-hooks.sh              # verify existing hooks
#   bash scripts/install-hooks.sh --full       # install full Maven quality gate hook
#   bash scripts/install-hooks.sh --minimal    # install HYPER_STANDARDS-only hook (fast)
#
# Hook variants:
#   --full     Runs: HYPER_STANDARDS + SpotBugs + Checkstyle + unit tests
#              Slower (~2-3 min) but catches everything. Use for main/develop branches.
#   --minimal  Runs: HYPER_STANDARDS scan only
#              Fast (<5 sec). Use for feature branches with fast iteration cycles.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GIT_HOOKS_DIR="$REPO_ROOT/.git/hooks"
FULL_HOOK="$SCRIPT_DIR/pre-commit-hook.sh"
MINIMAL_HOOK="$REPO_ROOT/.claude/pre-commit-hook"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

MODE="${1:-}"

echo ""
echo "YAWL v5.2 Git Hooks Installer"
echo "=============================="
echo "Repository root: $REPO_ROOT"
echo ""

# Validate git repository
if [ ! -d "$REPO_ROOT/.git" ]; then
    echo -e "${RED}ERROR: Not inside a git repository: $REPO_ROOT${NC}"
    exit 1
fi

# Check Java
if ! command -v java &>/dev/null; then
    echo -e "${YELLOW}WARNING: java not found on PATH. Install JDK 21+ before using the hook.${NC}"
fi

# Check Maven
if ! command -v mvn &>/dev/null; then
    echo -e "${YELLOW}WARNING: mvn not found on PATH. Install Maven 3.9+ before using the hook.${NC}"
fi

dest="$GIT_HOOKS_DIR/pre-commit"

backup_existing() {
    if [ -f "$dest" ] && ! [ -L "$dest" ]; then
        local bk="$dest.backup.$(date +%Y%m%d_%H%M%S)"
        cp "$dest" "$bk"
        echo -e "${YELLOW}  Backed up existing hook to: $(basename $bk)${NC}"
    fi
}

if [ "$MODE" = "--full" ]; then
    # -------------------------------------------------------------------------
    # Install full Maven quality gate hook
    # -------------------------------------------------------------------------
    echo "Installing FULL Maven quality gate hook..."
    echo "(HYPER_STANDARDS + SpotBugs + Checkstyle + unit tests)"
    echo ""

    if [ ! -f "$FULL_HOOK" ]; then
        echo -e "${RED}ERROR: Full hook source not found: $FULL_HOOK${NC}"
        exit 1
    fi

    backup_existing
    # Remove the .claude symlink (if that's what's there) and install the full hook
    rm -f "$dest"
    cp "$FULL_HOOK" "$dest"
    chmod +x "$dest"
    echo -e "${GREEN}  Installed: .git/hooks/pre-commit (full Maven quality gate)${NC}"

elif [ "$MODE" = "--minimal" ]; then
    # -------------------------------------------------------------------------
    # Install minimal HYPER_STANDARDS-only hook (symlink to .claude version)
    # -------------------------------------------------------------------------
    echo "Installing MINIMAL hook (HYPER_STANDARDS scan only)..."
    echo ""

    if [ ! -f "$MINIMAL_HOOK" ]; then
        echo -e "${RED}ERROR: Minimal hook source not found: $MINIMAL_HOOK${NC}"
        exit 1
    fi

    backup_existing
    rm -f "$dest"
    ln -s "../../.claude/pre-commit-hook" "$dest"
    chmod +x "$dest"
    echo -e "${GREEN}  Installed: .git/hooks/pre-commit (HYPER_STANDARDS minimal)${NC}"

else
    # -------------------------------------------------------------------------
    # Verify mode: report what is currently installed
    # -------------------------------------------------------------------------
    echo "Current hook status:"
    echo ""

    if [ -L "$dest" ]; then
        target="$(readlink "$dest")"
        if [[ "$target" == *"pre-commit-hook"* ]]; then
            echo -e "${GREEN}  pre-commit: MINIMAL hook (HYPER_STANDARDS only)${NC}"
            echo "    Symlink -> $target"
        else
            echo -e "${YELLOW}  pre-commit: UNKNOWN symlink -> $target${NC}"
        fi
    elif [ -f "$dest" ]; then
        if grep -q "YAWL Pre-Commit Quality Gate" "$dest" 2>/dev/null; then
            echo -e "${GREEN}  pre-commit: FULL Maven quality gate hook${NC}"
        elif grep -q "HYPER_STANDARDS" "$dest" 2>/dev/null; then
            echo -e "${GREEN}  pre-commit: HYPER_STANDARDS hook${NC}"
        else
            echo -e "${YELLOW}  pre-commit: present (unknown version)${NC}"
        fi
    else
        echo -e "${YELLOW}  pre-commit: NOT INSTALLED${NC}"
        echo "    Run: bash scripts/install-hooks.sh --full"
    fi
fi

echo ""
echo "Verifying quality gate configuration files..."
CONFIG_OK=true
required_files=(
    "checkstyle.xml"
    "checkstyle-suppressions.xml"
    "pmd-ruleset.xml"
    "pmd-exclusions.properties"
    "spotbugs-exclude.xml"
    "owasp-suppressions.xml"
)
for f in "${required_files[@]}"; do
    if [ -f "$REPO_ROOT/$f" ]; then
        echo -e "${GREEN}  OK: $f${NC}"
    else
        echo -e "${RED}  MISSING: $f${NC}"
        CONFIG_OK=false
    fi
done

echo ""
if $CONFIG_OK; then
    echo -e "${GREEN}All quality gate configuration files present.${NC}"
else
    echo -e "${RED}One or more configuration files are missing. Run: git status${NC}"
    exit 1
fi

echo ""
echo "Quick reference:"
echo "  mvn clean package -P analysis    # full analysis (SpotBugs+Checkstyle+PMD+JaCoCo)"
echo "  mvn spotbugs:check               # SpotBugs only"
echo "  mvn spotbugs:gui                 # SpotBugs interactive GUI"
echo "  mvn checkstyle:check             # Checkstyle only"
echo "  mvn pmd:check                    # PMD only"
echo "  mvn test                         # unit tests"
echo ""
echo "To switch hook variant:"
echo "  bash scripts/install-hooks.sh --full     # full Maven quality gate"
echo "  bash scripts/install-hooks.sh --minimal  # HYPER_STANDARDS scan only"
echo ""
