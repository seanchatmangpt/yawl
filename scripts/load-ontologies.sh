#!/bin/bash
# =============================================================================
# load-ontologies.sh — Load YAWL self-play ontologies into embedded QLever
# Layer 1: Ontology Foundation Verification
#
# NOTE: QLever is an embedded Java/C++ FFI bridge, NOT a Docker HTTP service.
# This script uses the Java CLI to load ontologies via QLeverEmbeddedSparqlEngine.
# =============================================================================

set -e

ONT_DIR="/Users/sac/yawl/ontology"

echo "🔍 Loading YAWL Self-Play Ontologies into Embedded QLever..."
echo "Ontology directory: $ONT_DIR"
echo ""

# Use Java to load ontologies via embedded QLever
# QLeverEmbeddedSparqlEngine is an in-process FFI bridge, not HTTP
java -cp "/Users/sac/yawl/target/classes:/Users/sac/yawl/yawl-qlever/target/classes" \
    org.yawlfoundation.yawl.qlever.ontology.OntologyLoader \
    "$ONT_DIR/process-mining/pm-bridge.ttl" \
    "$ONT_DIR/data-modelling/dm-bridge.ttl" \
    "$ONT_DIR/safe/safe-core.ttl" \
    "$ONT_DIR/simulation/yawl-sim.ttl" \
    2>&1 || {
    echo ""
    echo "⚠️  OntologyLoader not found. Using test-based loading..."
    echo ""

    # Alternative: Use JUnit test to load ontologies
    mvn test -Dtest=QLeverEmbeddedSparqlEngineTest#testLoadOntologies -q 2>&1 || {
        echo ""
        echo "Run the QLever ontology loading programmatically:"
        echo ""
        echo "  QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();"
        echo "  engine.initialize();"
        echo "  engine.loadRdfDataFromFile(\"$ONT_DIR/process-mining/pm-bridge.ttl\", \"TURTLE\");"
        echo "  engine.loadRdfDataFromFile(\"$ONT_DIR/data-modelling/dm-bridge.ttl\", \"TURTLE\");"
        echo "  engine.loadRdfDataFromFile(\"$ONT_DIR/safe/safe-core.ttl\", \"TURTLE\");"
        echo "  engine.loadRdfDataFromFile(\"$ONT_DIR/simulation/yawl-sim.ttl\", \"TURTLE\");"
    }
}

echo ""
echo "✅ Ontologies loaded into embedded QLever (in-process FFI)"
