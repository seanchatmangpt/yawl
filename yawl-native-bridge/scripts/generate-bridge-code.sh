#!/bin/bash
# Generate bridge code from ontology specification
# Uses ggen to generate Java and Erlang interfaces

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ONTOLOGY_DIR="$PROJECT_ROOT/../schema/bridge"

# Check for ggen
if ! command -v ggen &> /dev/null; then
    echo "Error: ggen is required but not installed" >&2
    exit 1
fi

echo "Generating bridge code from ontology..."

# Generate Java interfaces
ggen generate \
    --input "$ONTOLOGY_DIR/bridge.ttl" \
    --template "$ONTOLOGY_DIR/java-bridge.tera" \
    --output "$PROJECT_ROOT/src/main/java"

# Generate Erlang gen_server modules
ggen generate \
    --input "$ONTOLOGY_DIR/bridge.ttl" \
    --template "$ONTOLOGY_DIR/erlang-gen-server.tera" \
    --output "$PROJECT_ROOT/src/main/erlang"

# Generate Rust NIF bindings
ggen generate \
    --input "$ONTOLOGY_DIR/bridge.ttl" \
    --template "$ONTOLOGY_DIR/rust-nif.tera" \
    --output "$PROJECT_ROOT/src/main/rust/src/nif_bindings.rs"

echo "Bridge code generation completed"