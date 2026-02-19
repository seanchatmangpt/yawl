#!/usr/bin/env bash

# Registry mapping output files to their input dependencies
declare -A DEPENDENCY_REGISTRY=(
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
