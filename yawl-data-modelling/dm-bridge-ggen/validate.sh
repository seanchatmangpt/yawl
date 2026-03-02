#!/usr/bin/env bash
# validate.sh — ggen golden-file validator for dm-bridge-ggen
#
# Diffs the committed golden reference files (golden/) against the actual
# generated source files (../src/main/java/…/datamodelling/bridge/).
#
# Usage:
#   bash validate.sh
#
# Exit codes:
#   0 — golden files match generated source (sync is current)
#   1 — divergence detected (run: ggen sync, then re-commit)

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GOLDEN_DIR="$DIR/golden"
SRC_DIR="$DIR/../src/main/java/org/yawlfoundation/yawl/datamodelling/bridge"

GENERATED_FILES=(
  DataModellingCapability.java
  DataModellingCapabilityRegistry.java
  DataModellingL2.java
  DataModellingL3.java
  DataModellingMapsToCapability.java
  DataModellingCapabilityTest.java
  DataModellingCapabilityRegistryException.java
  DataModellingJTBDJob.java
)

diverged=0

for file in "${GENERATED_FILES[@]}"; do
  golden="$GOLDEN_DIR/$file"
  src="$SRC_DIR/$file"

  if [[ ! -f "$golden" ]]; then
    echo "[MISSING GOLDEN] $file"
    echo "  Expected: $golden"
    echo "  Run: ggen sync (in dm-bridge-ggen/) then copy output to golden/"
    diverged=1
    continue
  fi

  if [[ ! -f "$src" ]]; then
    echo "[MISSING SOURCE] $file"
    echo "  Expected: $src"
    echo "  Run: ggen sync (in dm-bridge-ggen/) to regenerate"
    diverged=1
    continue
  fi

  if diff -q "$golden" "$src" > /dev/null 2>&1; then
    echo "[OK] $file"
  else
    echo "[DIVERGED] $file"
    diff "$golden" "$src" || true
    echo "  golden/ and src/main/java/…/bridge/ differ."
    echo "  Either run 'ggen sync' to regenerate, or update golden/ if intentional."
    diverged=1
  fi
done

if [[ "$diverged" -eq 1 ]]; then
  echo ""
  echo "Validation FAILED — golden files diverge from generated source."
  echo "Run: cd dm-bridge-ggen && ggen sync"
  exit 1
else
  echo ""
  echo "Validation PASSED — all golden files match generated source."
  exit 0
fi
