#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0 - Diagram Generation Script
#
# Generates Mermaid diagrams, a YAWL XML specification, and facts snapshots
# into docs/v6/diagrams/ for architecture documentation.
#
# Usage:
#   ./tools/gen_v6_diagrams.sh               Generate all diagrams
#   ./tools/gen_v6_diagrams.sh --no-maven    Skip Maven dependency resolution
#   ./tools/gen_v6_diagrams.sh --output=DIR  Override output directory
#   ./tools/gen_v6_diagrams.sh --help        Show usage
#
# Exit codes:
#   0  All diagrams generated successfully
#   1  Fatal error (missing pom.xml or invalid output path)
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Terminal colours
# ---------------------------------------------------------------------------
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${PROJECT_DIR}/docs/v6/diagrams"
USE_MAVEN=true

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
fatal()   { error "$*"; exit 1; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}  $*${NC}"
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo ""
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
for arg in "$@"; do
    case "$arg" in
        --no-maven)     USE_MAVEN=false ;;
        --output=*)     OUTPUT_DIR="${arg#--output=}" ;;
        --help|-h)
            echo "Usage: $0 [--no-maven] [--output=DIR] [--help]"
            echo ""
            echo "Options:"
            echo "  --no-maven    Skip Maven dependency resolution (use static fallback)"
            echo "  --output=DIR  Override output directory (default: docs/v6/diagrams)"
            echo "  --help, -h    Show this usage message"
            exit 0
            ;;
        *)
            fatal "Unknown argument: $arg. Use --help for usage."
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------
POM="${PROJECT_DIR}/pom.xml"
if [[ ! -f "${POM}" ]]; then
    fatal "pom.xml not found at repo root: ${POM}"
fi

# ---------------------------------------------------------------------------
# Directory setup
# ---------------------------------------------------------------------------
setup_dirs() {
    mkdir -p "${OUTPUT_DIR}/yawl"
    mkdir -p "${OUTPUT_DIR}/facts"
}

# ===========================================================================
# Section 4: Reactor map — Maven module layout grouped by layer
# ===========================================================================
generate_reactor_map() {
    local out="${OUTPUT_DIR}/reactor-map.mmd"
    cat > "${out}" << 'MERMAID'
graph LR
    subgraph Foundation
        yawl-utilities
        yawl-elements
        yawl-authentication
    end

    subgraph Core
        yawl-engine
        yawl-stateless
        yawl-security
    end

    subgraph Services
        yawl-resourcing
        yawl-worklet
        yawl-scheduling
        yawl-integration
        yawl-monitoring
    end

    subgraph Presentation
        yawl-webapps
        yawl-control-panel
    end

    Foundation --> Core
    Core --> Services
    Services --> Presentation
MERMAID
    success "Generated: reactor-map.mmd"
}

# ===========================================================================
# Section 5: Build lifecycle — Maven phase sequence
# ===========================================================================
generate_build_lifecycle() {
    local out="${OUTPUT_DIR}/build-lifecycle.mmd"
    cat > "${out}" << 'MERMAID'
flowchart TD
    A[validate] --> B[compile]
    B --> C[test]
    C --> D[package]
    D --> E[verify]
    E --> F[install]
    F --> G[deploy]

    subgraph Quality Gates
        C --> U[Surefire unit tests]
        E --> I[Failsafe integration tests]
        E --> S[SpotBugs]
        E --> P[PMD]
        E --> K[Checkstyle]
    end

    U --> OK{all green?}
    I --> OK
    S --> OK
    P --> OK
    K --> OK

    OK -->|yes| PASS[proceed]
    OK -->|no| STOP[stop-the-line]

    style PASS fill:#166534,stroke:#14532d,color:#dcfce7
    style STOP fill:#991b1b,stroke:#7f1d1d,color:#fecaca
MERMAID
    success "Generated: build-lifecycle.mmd"
}

# ===========================================================================
# Section 6: Test topology — CI test job dependency graph
# ===========================================================================
generate_test_topology() {
    local out="${OUTPUT_DIR}/test-topology.mmd"
    cat > "${out}" << 'MERMAID'
flowchart TD
    build[Build Java 25] --> unit[Unit Tests<br/>JUnit 5 Parallel]
    build --> integration[Integration Tests<br/>PostgreSQL + H2]
    unit --> coverage[JaCoCo Coverage<br/>65% line / 55% branch]
    unit --> docker[Docker Build<br/>Multi-Arch amd64+arm64]
    docker --> cscan[Trivy Container Scan]
    docker --> ctest[Container Health Check]
    coverage --> report[Pipeline Summary]
    cscan --> report
    ctest --> report

    style build fill:#1e3a5f,stroke:#0f2942,color:#bfdbfe
    style report fill:#166534,stroke:#14532d,color:#dcfce7
MERMAID
    success "Generated: test-topology.mmd"
}

# ===========================================================================
# Section 7: CI gates — PR quality gate requirements
# ===========================================================================
generate_ci_gates() {
    local out="${OUTPUT_DIR}/ci-gates.mmd"
    # Count actual workflow files
    local wf_count=0
    if [[ -d "${PROJECT_DIR}/.github/workflows" ]]; then
        wf_count="$(find "${PROJECT_DIR}/.github/workflows" -maxdepth 1 -type f \( -name "*.yml" -o -name "*.yaml" \) 2>/dev/null | wc -l | tr -d ' ')"
    fi

    cat > "${out}" << MERMAID
flowchart LR
    PR[Pull Request] --> hyper[Hyper Standards<br/>14 anti-pattern checks]
    PR --> compile[Compile Gate<br/>Java 25]
    PR --> style[Checkstyle<br/>Google Java Style]
    PR --> pmd[PMD Quality Rules]
    PR --> spotbugs[SpotBugs<br/>HIGH+MEDIUM threshold]

    hyper --> gate{Quality Gate}
    compile --> gate
    style --> gate
    pmd --> gate
    spotbugs --> gate

    gate -->|pass| merge[Merge Allowed]
    gate -->|fail| block[PR Blocked]

    note["${wf_count} CI workflow files active"]

    style merge fill:#166534,stroke:#14532d,color:#dcfce7
    style block fill:#991b1b,stroke:#7f1d1d,color:#fecaca
MERMAID
    success "Generated: ci-gates.mmd"
}

# ===========================================================================
# Section 8: Module dependencies — inter-module edges (DOT-to-Mermaid)
# ===========================================================================

# 8a: Parse Maven DOT output into Mermaid edges using awk
_emit_mmd_from_dot() {
    local dot_input="$1"
    local out_file="$2"

    # Extract lines with dependency arrows where both sides are yawlfoundation
    local raw_edges
    raw_edges="$(echo "${dot_input}" | grep ' -> ' | grep 'yawlfoundation.*->.*yawlfoundation' || true)"

    if [[ -z "${raw_edges}" ]]; then
        warn "No inter-module edges found in DOT output; using static map."
        _emit_mmd_static "${out_file}"
        return
    fi

    # Parse DOT edges into Mermaid format:
    #   Input:  "org.yawlfoundation:yawl-elements:jar:6.0.0-Alpha:compile" -> "org.yawlfoundation:yawl-utilities:jar:6.0.0-Alpha:compile" ;
    #   Output: yawl-elements --> yawl-utilities
    local mmd_edges
    mmd_edges="$(echo "${raw_edges}" | awk '
        / -> / {
            split($0, parts, / -> /)
            src = parts[1]
            dst = parts[2]
            gsub(/"/, "", src)
            gsub(/"/, "", dst)
            gsub(/;/, "", dst)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", src)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", dst)
            n = split(src, sa, /:/)
            m = split(dst, da, /:/)
            src_id = sa[2]
            dst_id = da[2]
            if (src_id ~ /^yawl-/ && dst_id ~ /^yawl-/) {
                print "    " src_id " --> " dst_id
            }
        }
    ' | sort -u)"

    if [[ -z "${mmd_edges}" ]]; then
        warn "Edge extraction produced no yawl-to-yawl edges; using static map."
        _emit_mmd_static "${out_file}"
        return
    fi

    # Write Mermaid file with extracted edges
    {
        echo "graph TD"
        echo "    %% Inter-module dependencies from Maven dependency:tree"
        echo "${mmd_edges}"
    } > "${out_file}"
}

# 8b: Static fallback — ground-truth edges derived from module pom.xml files
_emit_mmd_static() {
    local out_file="$1"
    cat > "${out_file}" << 'MERMAID'
graph TD
    %% Inter-module dependencies derived from module pom.xml files
    yawl-elements --> yawl-utilities
    yawl-authentication --> yawl-elements
    yawl-engine --> yawl-elements
    yawl-stateless --> yawl-utilities
    yawl-stateless --> yawl-elements
    yawl-stateless --> yawl-engine
    yawl-resourcing --> yawl-engine
    yawl-worklet --> yawl-engine
    yawl-worklet --> yawl-resourcing
    yawl-scheduling --> yawl-engine
    yawl-integration --> yawl-engine
    yawl-integration --> yawl-stateless
    yawl-monitoring --> yawl-engine
    yawl-control-panel --> yawl-engine
MERMAID
}

generate_module_dependencies() {
    local out="${OUTPUT_DIR}/module-dependencies.mmd"
    local dot_output

    if "${USE_MAVEN}" && command -v mvn > /dev/null 2>&1; then
        info "Running mvn dependency:tree -DoutputType=dot ..."
        set +e
        dot_output="$(
            cd "${PROJECT_DIR}"
            mvn -B --no-transfer-progress dependency:tree \
                -DoutputType=dot \
                -DincludeGroupIds=org.yawlfoundation \
                2>/dev/null
        )"
        local mvn_rc=$?
        set -e

        if [[ "${mvn_rc}" -eq 0 && -n "${dot_output}" ]]; then
            _emit_mmd_from_dot "${dot_output}" "${out}"
        else
            warn "Maven dependency:tree failed (rc=${mvn_rc}); using static dependency map."
            _emit_mmd_static "${out}"
        fi
    else
        if ! "${USE_MAVEN}"; then
            info "Maven skipped (--no-maven); using static dependency map."
        else
            warn "Maven not found on PATH; using static dependency map."
        fi
        _emit_mmd_static "${out}"
    fi

    success "Generated: module-dependencies.mmd"
}

# ===========================================================================
# Section 9: YAWL XML specification — build pipeline as Petri net
# ===========================================================================
generate_yawl_spec() {
    local out="${OUTPUT_DIR}/yawl/build-and-test.yawl.xml"
    local version
    version="$(awk '/<version>/{gsub(/.*<version>/,""); gsub(/<\/version>.*/,""); gsub(/^[[:space:]]+|[[:space:]]+$/,""); print; exit}' "${POM}")"

    cat > "${out}" << YAWLXML
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0"
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">

  <specification uri="BuildAndTestPipeline">
    <name>YAWL ${version} Build and Test Pipeline</name>
    <documentation>Models the Maven build lifecycle and CI test execution for YAWL ${version}.</documentation>
    <metaData/>

    <decomposition id="BuildAndTestNet" xsi:type="NetFactsType" isRootNet="true">
      <name>Build and Test Process</name>
      <processControlElements>

        <inputCondition id="start">
          <name>Start</name>
          <flowsInto>
            <nextElementRef id="Compile"/>
          </flowsInto>
        </inputCondition>

        <task id="Compile">
          <name>mvn clean compile</name>
          <flowsInto>
            <nextElementRef id="UnitTest"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>

        <task id="UnitTest">
          <name>mvn test (JUnit 5 parallel)</name>
          <flowsInto>
            <nextElementRef id="Package"/>
          </flowsInto>
          <flowsInto>
            <nextElementRef id="TestFailureHandler"/>
            <predicate>false()</predicate>
          </flowsInto>
          <join code="xor"/>
          <split code="xor"/>
        </task>

        <task id="Package">
          <name>mvn package</name>
          <flowsInto>
            <nextElementRef id="Validate"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>

        <task id="Validate">
          <name>xmllint schema validation</name>
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>

        <task id="TestFailureHandler">
          <name>Report Test Failures</name>
          <flowsInto>
            <nextElementRef id="end"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
        </task>

        <outputCondition id="end">
          <name>End</name>
        </outputCondition>

      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
YAWLXML
    success "Generated: yawl/build-and-test.yawl.xml"
}

# ===========================================================================
# Section 10: Facts snapshots
# ===========================================================================
generate_facts() {
    # modules.txt — Maven module list from pom.xml
    awk '
        BEGIN { inmods=0 }
        /<modules>/  { inmods=1; next }
        /<\/modules>/ { inmods=0 }
        inmods && /<module>/ {
            gsub(/.*<module>/, "")
            gsub(/<\/module>.*/, "")
            gsub(/^[[:space:]]+|[[:space:]]+$/, "")
            if (length($0) > 0) print $0
        }
    ' "${POM}" | sort > "${OUTPUT_DIR}/facts/modules.txt"

    # ci-jobs.txt — job names from GitHub Actions workflow files
    local wf_dir="${PROJECT_DIR}/.github/workflows"
    if [[ -d "${wf_dir}" ]]; then
        grep -h -E '^  [a-z][a-z0-9_-]*:$' "${wf_dir}"/*.yml "${wf_dir}"/*.yaml 2>/dev/null \
            | sed 's/://; s/^[[:space:]]*//' \
            | grep -v '^jobs$' \
            | sort -u \
            > "${OUTPUT_DIR}/facts/ci-jobs.txt" || true
    else
        : > "${OUTPUT_DIR}/facts/ci-jobs.txt"
    fi

    # schema-version.txt — YAWL schema version
    local schema="${PROJECT_DIR}/schema/YAWL_Schema4.0.xsd"
    if [[ -f "${schema}" ]]; then
        grep -m1 'version=' "${schema}" \
            | sed 's/.*version="//; s/".*//' \
            > "${OUTPUT_DIR}/facts/schema-version.txt"
    else
        echo "4.0" > "${OUTPUT_DIR}/facts/schema-version.txt"
    fi

    success "Generated: facts/modules.txt, facts/ci-jobs.txt, facts/schema-version.txt"
}

# ===========================================================================
# Section 11: INDEX.md — index of all generated artifacts
# ===========================================================================
generate_index() {
    local out="${OUTPUT_DIR}/INDEX.md"
    local gen_date
    gen_date="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    local module_count
    module_count="$(wc -l < "${OUTPUT_DIR}/facts/modules.txt" | tr -d ' ')"

    cat > "${out}" << INDEX
# YAWL v6 Diagram Index

Generated: ${gen_date}

## Diagrams

| File | Description |
|------|-------------|
| [reactor-map.mmd](reactor-map.mmd) | Maven build reactor module layout |
| [build-lifecycle.mmd](build-lifecycle.mmd) | Maven lifecycle phase sequence with quality gates |
| [test-topology.mmd](test-topology.mmd) | CI test job dependency graph |
| [ci-gates.mmd](ci-gates.mmd) | PR quality gate requirements |
| [module-dependencies.mmd](module-dependencies.mmd) | Inter-module dependency graph (${module_count} modules) |

## YAWL Specifications

| File | Description |
|------|-------------|
| [yawl/build-and-test.yawl.xml](yawl/build-and-test.yawl.xml) | Build and test pipeline as YAWL Petri net |

## Facts Snapshots

| File | Description |
|------|-------------|
| [facts/modules.txt](facts/modules.txt) | Maven module list |
| [facts/ci-jobs.txt](facts/ci-jobs.txt) | CI workflow job names |
| [facts/schema-version.txt](facts/schema-version.txt) | YAWL schema version |
INDEX
    success "Generated: INDEX.md"
}

# ===========================================================================
# Section 12: Main entrypoint
# ===========================================================================
main() {
    header "YAWL v6 Diagram Generation"
    info "Project root: ${PROJECT_DIR}"
    info "Output directory: ${OUTPUT_DIR}"
    info "Maven dependency resolution: ${USE_MAVEN}"
    echo ""

    setup_dirs

    generate_reactor_map
    generate_build_lifecycle
    generate_test_topology
    generate_ci_gates
    generate_module_dependencies
    generate_yawl_spec
    generate_facts
    generate_index

    echo ""
    success "All diagrams generated in: ${OUTPUT_DIR}"
    echo ""
    info "Render Mermaid diagrams with:"
    info "  npx @mermaid-js/mermaid-cli -i ${OUTPUT_DIR}/module-dependencies.mmd -o module-deps.svg"
    info "Validate the YAWL spec with:"
    info "  xmllint --schema schema/YAWL_Schema4.0.xsd ${OUTPUT_DIR}/yawl/build-and-test.yawl.xml"
}

main "$@"
