#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# dependency-registry.sh — Input-to-Output Dependency Mapping
#
# Maps each output file to its input dependencies for incremental builds.
# Used by emit_if_stale() to determine if regeneration is needed.
#
# Usage:
#   source lib/dependency-registry.sh
#   inputs=$(get_inputs "facts/modules.json")
#
# Format:
#   DEPENDENCY_REGISTRY["output_file"]="input1 input2 glob_pattern/**"
# ==========================================================================

# Registry mapping output files to their input dependencies
declare -A DEPENDENCY_REGISTRY=(
    # Facts - derived from source code and configuration
    ["facts/modules.json"]="pom.xml src/**/package-info.java"
    ["facts/reactor.json"]="pom.xml"
    ["facts/shared-src.json"]="src/**/*.java"
    ["facts/tests.json"]="test/**/*.java"
    ["facts/dual-family.json"]="pom.xml src/**/*.java"
    ["facts/code-quality.json"]="src/**/*.java test/**/*.java"
    ["facts/complexity.json"]="src/**/*.java"
    ["facts/dependencies.json"]="pom.xml yawl-*/pom.xml"
    ["facts/deps-conflicts.json"]="pom.xml yawl-*/pom.xml"
    ["facts/test-patterns.json"]="test/**/*.java"
    ["facts/test-suites.json"]="test/**/*Test.java test/**/*Suite.java"
    ["facts/mock-usage.json"]="src/**/*.java test/**/*.java"
    ["facts/architectural-elements.json"]="src/**/*.java"
    ["facts/package-structure.json"]="src/**/*.java"
    ["facts/class-relations.json"]="src/**/*.java"
    ["facts/integration-points.json"]="src/**/*.java test/**/*.java"
    ["facts/api-structure.json"]="src/**/*API*.java src/**/*Service*.java"
    ["facts/config-sources.json"]="src/**/*.java"
    ["facts/doc-links.json"]="**/*.md docs/**/*.md"
    ["facts/schema-references.json"]="schema/**/*.xsd spec*.xml"

    # Static Analysis Facts - derived from Maven analysis reports
    ["facts/spotbugs-findings.json"]="target/spotbugsXml.xml yawl-*/target/spotbugsXml.xml"
    ["facts/pmd-violations.json"]="target/pmd.xml yawl-*/target/pmd.xml"
    ["facts/checkstyle-warnings.json"]="target/checkstyle-result.xml yawl-*/target/checkstyle-result.xml"
    ["facts/static-analysis.json"]="facts/spotbugs-findings.json facts/pmd-violations.json facts/checkstyle-warnings.json"

    # Diagrams - derived from facts (diagrams depend on their source facts)
    ["diagrams/10-maven-reactor.mmd"]="facts/reactor.json facts/modules.json pom.xml"
    ["diagrams/50-risk-surfaces.mmd"]="facts/static-analysis.json facts/code-quality.json facts/complexity.json"
    ["diagrams/60-mcp-architecture.mmd"]="src/**/YawlMcpServer.java src/**/mcp/**/*.java"
    ["diagrams/60-code-health-dashboard.mmd"]="facts/static-analysis.json facts/spotbugs-findings.json facts/pmd-violations.json facts/checkstyle-warnings.json"
    ["diagrams/61-static-analysis-trends.mmd"]="docs/v6/static-analysis-history/history.jsonl"
    ["diagrams/65-a2a-topology.mmd"]="src/**/YawlA2AServer.java src/**/a2a/**/*.java"
    ["diagrams/70-agent-capabilities.mmd"]="src/**/YawlMcpServer.java src/**/YawlA2AServer.java"
    ["diagrams/75-protocol-sequences.mmd"]="src/**/mcp/**/*.java src/**/a2a/**/*.java"

    # Integration Diagrams
    ["diagrams/integration/mcp-flow.mmd"]="src/**/mcp/**/*.java"
    ["diagrams/integration/a2a-flow.mmd"]="src/**/a2a/**/*.java"

    # Integration Facts - derived from integration source code
    ["facts/integration-facts.json"]="src/**/integration/**/*.java src/**/mcp/**/*.java src/**/a2a/**/*.java src/**/zai/**/*.java"
    ["facts/docker-testing.json"]="docker-compose.a2a-mcp-test.yml scripts/run-docker-a2a-mcp-test.sh scripts/test-a2a-mcp-zai.sh yawl-mcp-a2a-app/pom.xml yawl-mcp-a2a-app/target/*.jar"

    # Additional diagrams that depend on analysis outputs
    ["diagrams/62-dependency-health.mmd"]="facts/deps-conflicts.json facts/dependencies.json"
)

# Get the input patterns for a given output file
get_inputs() {
    local output_file="$1"
    if [[ -v DEPENDENCY_REGISTRY["$output_file"] ]]; then
        echo "${DEPENDENCY_REGISTRY[$output_file]}"
    else
        echo "src/"  # Default fallback
        return 1
    fi
}

# Get all output files that depend on a given input pattern
get_dependents() {
    local input_pattern="$1"
    local dependents=()

    for output in "${!DEPENDENCY_REGISTRY[@]}"; do
        local inputs="${DEPENDENCY_REGISTRY[$output]}"
        if [[ "$inputs" == *"$input_pattern"* ]]; then
            dependents+=("$output")
        fi
    done

    printf '%s\n' "${dependents[@]}"
}

# Validate registry on load
validate_registry() {
    return 0  # Basic validation - add more checks as needed
}
