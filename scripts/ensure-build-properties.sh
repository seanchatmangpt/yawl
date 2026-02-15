#!/usr/bin/env bash
# Ensures build/build.properties exists for unit tests (H2 config).
# If missing or broken symlink, copies build/build.properties.remote.
# Run from repo root. Exit 0.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$REPO_ROOT/build"
PROPS="$BUILD_DIR/build.properties"
REMOTE="$BUILD_DIR/build.properties.remote"

cd "$REPO_ROOT"
# Remove broken symlink if present
if [ -L "$PROPS" ] && [ ! -e "$PROPS" ]; then
    rm -f "$PROPS"
fi
# Copy if missing or unreadable
if [ ! -f "$PROPS" ] || [ ! -r "$PROPS" ]; then
    if [ -f "$REMOTE" ]; then
        cp "$REMOTE" "$PROPS"
        echo "[ensure-build-properties] Created $PROPS from $REMOTE (H2 config for unit tests)"
    fi
fi
exit 0
