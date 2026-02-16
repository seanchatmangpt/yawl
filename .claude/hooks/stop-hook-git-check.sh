#!/bin/bash
# Stop Hook: Git Status Verification
# Ensures no uncommitted changes remain after Claude finishes work
# Exit 0 = Success (clean state), Exit 1 = Warning (uncommitted changes)

set -euo pipefail

# Colors
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
  echo -e "${GREEN}✓${NC} Not a git repository" >&2
  exit 0
fi

# Check for uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "" >&2
  echo -e "${YELLOW}⚠️  There are uncommitted changes in the repository.${NC}" >&2
  echo -e "${YELLOW}Please commit and push these changes to the remote branch.${NC}" >&2
  echo "" >&2

  # Show what's changed
  echo -e "${YELLOW}Changed files:${NC}" >&2
  git status --short >&2
  echo "" >&2

  exit 1  # Non-zero exit shows message but doesn't block
fi

# Check for untracked files (less critical, just inform)
UNTRACKED=$(git ls-files --others --exclude-standard)
if [ -n "$UNTRACKED" ]; then
  echo "" >&2
  echo -e "${YELLOW}ℹ️  Note: There are untracked files (not critical):${NC}" >&2
  echo "$UNTRACKED" | head -5 >&2
  echo "" >&2
fi

# All good
echo -e "${GREEN}✓${NC} Git state clean" >&2
exit 0
