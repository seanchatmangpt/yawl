#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0 - Git Hook Installer
#
# Installs all YAWL git hooks into .git/hooks/ as symlinks so that updates
# to the hook sources in .claude/ propagate automatically without reinstalling.
#
# Usage:
#   ./install-hooks.sh           Install all hooks
#   ./install-hooks.sh --check   Verify hooks are installed (no changes)
#   ./install-hooks.sh --remove  Remove all YAWL-managed hooks
#
# Hooks installed:
#   pre-commit   Runs HYPER_STANDARDS validator (blocks mocks/stubs/TODOs)
#
# The hooks block commits that violate Fortune 5 production standards.
# See CLAUDE.md and .claude/HYPER_STANDARDS.md for the full list of rules.
# =============================================================================

set -euo pipefail

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

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIT_HOOKS_DIR="${PROJECT_DIR}/.git/hooks"
CLAUDE_HOOKS_DIR="${PROJECT_DIR}/.claude"

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
fatal()   { error "$*"; exit 1; }

# Map of git hook name -> source file (relative to .claude/)
declare -A HOOK_MAP=(
    ["pre-commit"]="pre-commit-hook"
)

# ---------------------------------------------------------------------------
# Validate repository
# ---------------------------------------------------------------------------
if [ ! -d "${PROJECT_DIR}/.git" ]; then
    fatal "Not a git repository: ${PROJECT_DIR}. Run from the project root."
fi

if [ ! -d "${GIT_HOOKS_DIR}" ]; then
    mkdir -p "${GIT_HOOKS_DIR}"
fi

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
MODE="install"
for arg in "$@"; do
    case "$arg" in
        --check)  MODE="check" ;;
        --remove) MODE="remove" ;;
        --help|-h)
            echo "Usage: $0 [--check|--remove|--help]"
            echo ""
            echo "  (no flags)  Install all YAWL git hooks as symlinks"
            echo "  --check     Verify hooks are installed; exit 1 if not"
            echo "  --remove    Remove all YAWL-managed hooks from .git/hooks/"
            exit 0
            ;;
        *)
            fatal "Unknown argument: $arg. Use --help for usage."
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Install mode
# ---------------------------------------------------------------------------
install_hooks() {
    echo ""
    echo -e "${BOLD}${CYAN}Installing YAWL Git Hooks${NC}"
    echo ""

    local installed=0
    local skipped=0

    for hook_name in "${!HOOK_MAP[@]}"; do
        local src_file="${HOOK_MAP[$hook_name]}"
        local src_path="${CLAUDE_HOOKS_DIR}/${src_file}"
        local dst_path="${GIT_HOOKS_DIR}/${hook_name}"
        # Relative path from .git/hooks/ to .claude/ (two levels up from .git/hooks/)
        local rel_src="../../.claude/${src_file}"

        if [ ! -f "${src_path}" ]; then
            warn "Hook source not found: ${src_path} — skipping ${hook_name}"
            skipped=$((skipped + 1))
            continue
        fi

        # Backup any existing non-symlink hook
        if [ -f "${dst_path}" ] && [ ! -L "${dst_path}" ]; then
            local backup="${dst_path}.bak.$(date +%Y%m%d%H%M%S)"
            warn "Existing hook at ${dst_path} — backing up to ${backup##"${PROJECT_DIR}/"}"
            cp "${dst_path}" "${backup}"
        fi

        ln -sf "${rel_src}" "${dst_path}"
        chmod +x "${dst_path}"
        success "Installed ${hook_name} -> ${rel_src}"
        installed=$((installed + 1))
    done

    echo ""
    info "${installed} hook(s) installed, ${skipped} skipped."

    if [ "${installed}" -gt 0 ]; then
        echo ""
        echo -e "${BOLD}What the pre-commit hook enforces:${NC}"
        echo "  - No TODO / FIXME / XXX / HACK comments"
        echo "  - No mock/stub method names (mockFetch, stubValidation...)"
        echo "  - No empty method bodies"
        echo "  - No silent fallbacks in catch blocks"
        echo "  - No mock framework imports in src/"
        echo ""
        echo -e "  See: ${CYAN}CLAUDE.md${NC} and ${CYAN}.claude/HYPER_STANDARDS.md${NC}"
    fi
}

# ---------------------------------------------------------------------------
# Check mode
# ---------------------------------------------------------------------------
check_hooks() {
    echo ""
    echo -e "${BOLD}${CYAN}Verifying YAWL Git Hooks${NC}"
    echo ""

    local all_ok=true

    for hook_name in "${!HOOK_MAP[@]}"; do
        local src_file="${HOOK_MAP[$hook_name]}"
        local src_path="${CLAUDE_HOOKS_DIR}/${src_file}"
        local dst_path="${GIT_HOOKS_DIR}/${hook_name}"

        if [ ! -L "${dst_path}" ]; then
            error "${hook_name}: not installed as symlink at ${dst_path}"
            all_ok=false
        elif [ ! -f "${src_path}" ]; then
            error "${hook_name}: symlink target missing — ${src_path}"
            all_ok=false
        else
            success "${hook_name}: installed and source present."
        fi
    done

    echo ""
    if $all_ok; then
        success "All hooks are correctly installed."
    else
        error "Some hooks are missing. Run ./install-hooks.sh to fix."
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Remove mode
# ---------------------------------------------------------------------------
remove_hooks() {
    echo ""
    echo -e "${BOLD}${YELLOW}Removing YAWL Git Hooks${NC}"
    echo ""

    local removed=0

    for hook_name in "${!HOOK_MAP[@]}"; do
        local dst_path="${GIT_HOOKS_DIR}/${hook_name}"

        if [ -L "${dst_path}" ]; then
            rm "${dst_path}"
            warn "Removed: ${hook_name}"
            removed=$((removed + 1))
        elif [ -f "${dst_path}" ]; then
            warn "${hook_name} exists but is not a YAWL symlink — not removed."
        else
            info "${hook_name}: not present, nothing to remove."
        fi
    done

    echo ""
    info "${removed} hook(s) removed."
    warn "HYPER_STANDARDS enforcement is no longer active."
    warn "Run ./install-hooks.sh to re-enable."
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
case "${MODE}" in
    install) install_hooks ;;
    check)   check_hooks ;;
    remove)  remove_hooks ;;
    *)       fatal "Internal error: unhandled mode '${MODE}'" ;;
esac
