#!/usr/bin/env bash
# Build the rust4pm cdylib and copy to target/release/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROFILE="${1:-release}"

echo "==> Building rust4pm (profile: $PROFILE)"
cd "$YAWL_ROOT/rust"

if [ "$PROFILE" = "release" ]; then
    cargo build -p rust4pm --release
    LIB_SRC="$YAWL_ROOT/rust/target/release/librust4pm.so"
else
    cargo build -p rust4pm
    LIB_SRC="$YAWL_ROOT/rust/target/debug/librust4pm.so"
fi

OUT_DIR="$YAWL_ROOT/target/release"
mkdir -p "$OUT_DIR"
cp "$LIB_SRC" "$OUT_DIR/librust4pm.so"
echo "==> Copied to $OUT_DIR/librust4pm.so"
echo "To use: -Drust4pm.library.path=$OUT_DIR/librust4pm.so"
