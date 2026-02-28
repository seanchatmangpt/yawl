#!/bin/bash
#
# Observatory receipt emitter
# Generates a JSON receipt with provenance and integrity hashes
#

# Generate the observatory receipt
generate_receipt() {
    local root_dir="$1"
    local facts_dir="$2"
    local diagrams_dir="$3"
    local receipts_dir="$4"

    log_info "Generating observatory receipt..."

    export ROOT_DIR="$root_dir"
    export FACTS_DIR="$facts_dir"
    export DIAGRAMS_DIR="$diagrams_dir"
    export RECEIPTS_DIR="$receipts_dir"

    python3 << 'PYEOF'
import json
import os
import hashlib
from pathlib import Path
from datetime import datetime

root_dir = os.environ.get('ROOT_DIR', '.')
facts_dir = os.environ.get('FACTS_DIR', '')
diagrams_dir = os.environ.get('DIAGRAMS_DIR', '')
receipts_dir = os.environ.get('RECEIPTS_DIR', '')

def sha256_file(filepath):
    """Compute SHA256 hash of a file"""
    if not os.path.exists(filepath):
        return ""
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

# Input provenance
root_pom = os.path.join(root_dir, 'pom.xml')
root_pom_sha256 = sha256_file(root_pom)

# Output integrity
facts_hashes = {}
if os.path.isdir(facts_dir):
    for fact_file in sorted(os.listdir(facts_dir)):
        if fact_file.endswith('.json'):
            filepath = os.path.join(facts_dir, fact_file)
            facts_hashes[fact_file] = sha256_file(filepath)

diagrams_hashes = {}
if os.path.isdir(diagrams_dir):
    for diagram_file in sorted(os.listdir(diagrams_dir)):
        if diagram_file.endswith('.mmd') or diagram_file.endswith('.xml'):
            filepath = os.path.join(diagrams_dir, diagram_file)
            diagrams_hashes[diagram_file] = sha256_file(filepath)

# Index file (if exists)
index_sha256 = ""
index_file = os.path.join(os.path.dirname(facts_dir), 'INDEX.md')
if os.path.exists(index_file):
    index_sha256 = sha256_file(index_file)

# Build receipt
receipt = {
    "run_id": datetime.utcnow().strftime("%Y%m%dT%H%M%SZ"),
    "generated_at": datetime.utcnow().isoformat() + "Z",
    "status": "GREEN",
    "inputs": {
        "root_pom": "pom.xml",
        "root_pom_sha256": root_pom_sha256
    },
    "outputs": {
        "facts_dir": "facts",
        "facts_sha256": facts_hashes,
        "diagrams_dir": "diagrams",
        "diagrams_sha256": diagrams_hashes,
        "index_sha256": index_sha256
    },
    "facts_emitted": sorted(list(facts_hashes.keys())),
    "diagrams_emitted": sorted(list(diagrams_hashes.keys())),
    "timing_ms": {
        "facts": 0,
        "diagrams": 0,
        "receipt": 0,
        "total": 0
    },
    "refusals": [],
    "warnings": []
}

receipt_file = os.path.join(receipts_dir, 'observatory.json')
with open(receipt_file, 'w') as f:
    json.dump(receipt, f, indent=2)
    f.write('\n')

print(f"Receipt written to {receipt_file}")
PYEOF
}
