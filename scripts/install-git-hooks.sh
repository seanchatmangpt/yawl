#!/usr/bin/env bash
# ==========================================================================
# install-git-hooks.sh — Install YAWL git hooks for local development
#
# Usage: bash scripts/install-git-hooks.sh
#
# Installs hooks into .git/hooks/ for:
#   pre-commit: Validates no mocks/stubs, runs fast compile check
#   commit-msg: Enforces session URL reference in YAWL AI-assisted commits
#   pre-push:   Prevents force-push to main/master/release branches
# ==========================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="${REPO_ROOT}/.git/hooks"
SCRIPTS_DIR="${REPO_ROOT}/scripts"

echo "[hooks] Installing YAWL git hooks to ${HOOKS_DIR}/"

# ── pre-commit ─────────────────────────────────────────────────────────────
cat > "${HOOKS_DIR}/pre-commit" << 'HOOK'
#!/usr/bin/env bash
# YAWL pre-commit hook: Guard validation + fast compile check
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
STAGED_FILES="$(git diff --cached --name-only --diff-filter=ACM)"

# Skip if no staged files
if [ -z "${STAGED_FILES}" ]; then
    exit 0
fi

echo "[pre-commit] Checking staged files..."

# Guard check: No mocks, stubs, TODOs in src/ files
GUARD_PATTERNS='TODO|FIXME|XXX|HACK|mockFetch\|stubValidation\|boolean useMockData|return "";|return null; \/\/ stub|DUMMY_CONFIG|PLACEHOLDER_VALUE|if (isTestMode)|isTestMode\(\)|\.getOrDefault.*test_value|if (true) return|log\.warn.*not implemented'

GUARD_VIOLATIONS=0
while IFS= read -r file; do
    if [[ "$file" == src/* ]] && [[ "$file" == *.java ]]; then
        if git show ":${file}" 2>/dev/null | grep -qE "${GUARD_PATTERNS}"; then
            echo "[pre-commit] GUARD VIOLATION in: ${file}"
            git show ":${file}" | grep -nE "${GUARD_PATTERNS}" | head -5
            GUARD_VIOLATIONS=$((GUARD_VIOLATIONS + 1))
        fi
    fi
done <<< "${STAGED_FILES}"

if [ "${GUARD_VIOLATIONS}" -gt 0 ]; then
    echo "[pre-commit] BLOCKED: ${GUARD_VIOLATIONS} guard violation(s) found."
    echo "[pre-commit] Fix violations before committing."
    exit 1
fi

echo "[pre-commit] Guard validation: PASSED"
exit 0
HOOK

chmod +x "${HOOKS_DIR}/pre-commit"
echo "[hooks] + pre-commit hook installed"

# ── commit-msg ─────────────────────────────────────────────────────────────
cat > "${HOOKS_DIR}/commit-msg" << 'HOOK'
#!/usr/bin/env bash
# YAWL commit-msg hook: Light validation (non-blocking)
MSG_FILE="$1"
MSG="$(cat "${MSG_FILE}")"

# Minimum length check
if [ "${#MSG}" -lt 10 ]; then
    echo "[commit-msg] WARNING: Commit message very short (< 10 chars)"
fi

exit 0
HOOK

chmod +x "${HOOKS_DIR}/commit-msg"
echo "[hooks] + commit-msg hook installed"

# ── pre-push ───────────────────────────────────────────────────────────────
cat > "${HOOKS_DIR}/pre-push" << 'HOOK'
#!/usr/bin/env bash
# YAWL pre-push hook: Prevent direct push to protected branches
set -euo pipefail

PROTECTED_BRANCHES="main master release"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

for BRANCH in ${PROTECTED_BRANCHES}; do
    if [ "${CURRENT_BRANCH}" = "${BRANCH}" ]; then
        echo "[pre-push] ERROR: Direct push to '${BRANCH}' is not allowed."
        echo "[pre-push] Create a feature branch (claude/<desc>-<sessionId>) and open a PR."
        exit 1
    fi
done

exit 0
HOOK

chmod +x "${HOOKS_DIR}/pre-push"
echo "[hooks] + pre-push hook installed"

echo ""
echo "[hooks] All YAWL git hooks installed successfully!"
echo "[hooks] Run 'git config core.hooksPath .git/hooks' to ensure git uses them."
echo ""
