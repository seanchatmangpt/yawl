#!/usr/bin/env bash
# Build the data-modelling-ffi cdylib and copy to target/release/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROFILE="${1:-release}"

echo "==> Building data-modelling-ffi (profile: $PROFILE)"
cd "$YAWL_ROOT"

if [ "$PROFILE" = "release" ]; then
    cargo build -p data-modelling-ffi --release --manifest-path data-modelling-ffi/Cargo.toml
    LIB_SRC="$YAWL_ROOT/data-modelling-ffi/target/release/libdata_modelling_ffi.so"
else
    cargo build -p data-modelling-ffi --manifest-path data-modelling-ffi/Cargo.toml
    LIB_SRC="$YAWL_ROOT/data-modelling-ffi/target/debug/libdata_modelling_ffi.so"
fi

OUT_DIR="$YAWL_ROOT/target/release"
mkdir -p "$OUT_DIR"
cp "$LIB_SRC" "$OUT_DIR/libdata_modelling_ffi.so"
echo "==> Copied to $OUT_DIR/libdata_modelling_ffi.so"
echo "To use: -Ddata_modelling_ffi.library.path=$OUT_DIR/libdata_modelling_ffi.so"
