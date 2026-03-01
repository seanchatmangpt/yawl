#!/usr/bin/env bash
# Regenerate Layer 1 Java bindings from rust4pm.h using jextract.
# Download jextract from: https://github.com/openjdk/jextract/releases
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HEADER="$YAWL_ROOT/rust/rust4pm/rust4pm.h"
OUT_DIR="$YAWL_ROOT/yawl-rust4pm/src/main/java"
PACKAGE="org.yawlfoundation.yawl.rust4pm.generated"

if ! command -v jextract &>/dev/null; then
    echo "ERROR: jextract not in PATH."
    echo "Download from: https://github.com/openjdk/jextract/releases"
    echo "Hand-written bindings are in: $OUT_DIR"
    exit 1
fi

jextract \
    --output "$OUT_DIR" \
    --target-package "$PACKAGE" \
    --library rust4pm \
    "$HEADER"

echo "Generated to $OUT_DIR"
