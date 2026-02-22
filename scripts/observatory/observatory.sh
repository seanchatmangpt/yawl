#!/usr/bin/env bash
# observatory.sh — Thin wrapper for YAWL Observatory Rust binary
#
# Replaces the original 374-line bash implementation.
# Build: ~30s first time, then cached. Run: <4s (vs 51s bash).
#
# Usage:
#   ./scripts/observatory/observatory.sh          # Full run
#   ./scripts/observatory/observatory.sh --facts   # Facts only
#   ./scripts/observatory/observatory.sh --force   # Force regeneration
#   OBSERVATORY_FORCE=1 ./scripts/observatory/observatory.sh  # Force regeneration
#
# Set permissions: chmod +x scripts/observatory/observatory.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BINARY="$SCRIPT_DIR/target/release/observatory"
HOOKS_BINARY="$SCRIPT_DIR/target/release/yawl-hooks"

# Detect if either binary needs rebuilding
needs_build() {
    [[ ! -f "$BINARY" ]] || [[ ! -f "$HOOKS_BINARY" ]] || [[ "$SCRIPT_DIR/Cargo.toml" -nt "$BINARY" ]]
}

if needs_build; then
    echo "[observatory] Building Rust binaries (first run — ~30s)..."
    echo "[observatory] Builds: observatory (facts) + yawl-hooks (Claude Code hooks)"
    echo "[observatory] Subsequent runs: <4s cold, <0.5s cached"
    cd "$SCRIPT_DIR"
    if ! cargo build --release --quiet 2>&1 | grep -v "^$" | sed 's/^/  /'; then
        echo "[observatory] ❌ Build failed" >&2
        exit 1
    fi
    echo "[observatory] ✅ Binaries built: $BINARY + $HOOKS_BINARY"
fi

exec "$BINARY" --repo-root "$REPO_ROOT" "$@"
