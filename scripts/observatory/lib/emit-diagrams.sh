#!/usr/bin/env bash
# ==========================================================================
# Observatory Diagram Emitters — DYNAMIC (reads from facts)
#
# Each emit_*_diagram() function generates Mermaid visualizations
# by reading actual data from $facts_dir JSON files.
#
# Usage: source this file, then call run_diagrams "$facts_dir" "$diagrams_dir"
# ==========================================================================

set -euo pipefail

# ── Helper: Safe JSON read ────────────────────────────────────────────────────
json_get() {
    local file="$1"
    local query="$2"
    local default="${3:-}"

    if [[ -f "$file" ]]; then
        jq -r "$query // \"$default\"" "$file" 2>/dev/null || echo "$default"
    else
        echo "$default"
    fi
}

# ── Dynamic Reactor Diagram ───────────────────────────────────────────────────
emit_reactor_diagram() {
    local facts_dir="$1"
    local out="$2/10-maven-reactor.mmd"

    log_info "Emitting diagrams/10-maven-reactor.mmd (dynamic)..."

    local modules_json="$facts_dir/modules.json"
    local reactor_json="$facts_dir/reactor.json"

    # Build module nodes dynamically
    local module_nodes=""
    local deps_edges=""

    if [[ -f "$modules_json" ]]; then
        local count=0
        while IFS= read -r module; do
            local name=$(echo "$module" | jq -r '.name')
            local src_files=$(echo "$module" | jq -r '.src_files // 0')
            local strategy=$(echo "$module" | jq -r '.strategy // "standard"')

            # Emoji based on role
            local emoji="📦"
            case "$name" in
                yawl-parent) emoji="🏠" ;;
                yawl-engine) emoji="⚙️" ;;
                yawl-elements) emoji="📋" ;;
                yawl-utilities) emoji="🔧" ;;
                yawl-integration) emoji="🌐" ;;
                yawl-authentication) emoji="🔐" ;;
                yawl-resourcing) emoji="👥" ;;
                yawl-stateless) emoji="🔄" ;;
                yawl-ggen) emoji="🎯" ;;
                yawl-mcp-a2a*) emoji="📡" ;;
                yawl-pi) emoji="🧠" ;;
            esac

            # Only show file count for modules with files
            local file_info=""
            if [[ "$src_files" -gt 0 ]]; then
                file_info="<br/>$src_files files"
            fi

            module_nodes+="        ${name}[\"$emoji $name$file_info\"]\n"
            ((count++)) || true
        done < <(jq -c '.modules[]' "$modules_json" 2>/dev/null)
    fi

    # Build dependency edges from reactor.json
    if [[ -f "$reactor_json" ]]; then
        while IFS= read -r dep; do
            local from=$(echo "$dep" | jq -r '.from')
            local to=$(echo "$dep" | jq -r '.to')
            deps_edges+="    ${from} --> ${to}\n"
        done < <(jq -c '.module_deps[]' "$reactor_json" 2>/dev/null)
    fi

    # Get module count
    local module_count=0
    [[ -f "$modules_json" ]] && module_count=$(jq '.modules | length' "$modules_json" 2>/dev/null || echo "0")

    cat > "$out" << EOF
graph TD
    subgraph "YAWL v6 Maven Reactor [$module_count modules]"
$(echo -e "$module_nodes" | head -30)
    end

$(echo -e "$deps_edges")

    classDef primary fill:#4A90E2,stroke:#2E5C8A,color:#fff
    classDef support fill:#7B68EE,stroke:#5A4BA3,color:#fff
    classDef data fill:#50C878,stroke:#2D7A4A,color:#fff

    class yawl-parent,yawl-engine primary
    class yawl-utilities,yawl-elements support
    class yawl-integration,yawl-authentication,yawl-resourcing data
EOF
}

# ── Dynamic Shared Source Diagram ─────────────────────────────────────────────
emit_shared_src_diagram() {
    local facts_dir="$1"
    local out="$2/15-shared-src-map.mmd"

    log_info "Emitting diagrams/15-shared-src-map.mmd (dynamic)..."

    local shared_src_json="$facts_dir/shared-src.json"
    local modules_json="$facts_dir/modules.json"

    # Count shared vs standard modules
    local shared_count=0
    local standard_count=0

    if [[ -f "$modules_json" ]]; then
        shared_count=$(jq '[.modules[] | select(.strategy == "full_shared")] | length' "$modules_json" 2>/dev/null || echo "0")
        standard_count=$(jq '[.modules[] | select(.strategy == "standard")] | length' "$modules_json" 2>/dev/null || echo "0")
    fi

    # Count ownership ambiguities
    local ambiguities=0
    if [[ -f "$shared_src_json" ]]; then
        ambiguities=$(jq '.ownership_ambiguities | length' "$shared_src_json" 2>/dev/null || echo "0")
    fi

    cat > "$out" << EOF
graph TB
    subgraph "Shared Source Tree (/src)"
        subgraph shared["$shared_count modules share ../src"]
            org["📁 org/yawlfoundation/yawl/"]
            utils["🔧 utils/"]
            engine["⚙️ engine/"]
            elements["📋 elements/"]
            auth["🔐 authentication/"]
            stateless["🔄 stateless/"]
        end

        subgraph standard["$standard_count modules use src/main/java"]
            erlang["yawl-erlang<br/>73 files"]
            ggen["yawl-ggen<br/>25 files"]
            pi["yawl-pi<br/>58 files"]
            mcp["yawl-mcp-a2a-app<br/>216 files"]
        end
    end

    org --> utils
    org --> engine
    org --> elements
    org --> auth
    org --> stateless

    classDef shared fill:#E8F4F8,stroke:#2E7D8C,color:#000
    classDef standard fill:#C8E6C9,stroke:#2E7A2E,color:#000
    classDef warning fill:#FFCDD2,stroke:#C62828,color:#000

    class org,utils,engine,elements,auth,stateless shared
    class erlang,ggen,pi,mcp standard
EOF

    # Add ambiguity warning if present
    if [[ "$ambiguities" -gt 0 ]]; then
        cat >> "$out" << EOF

    ambiguities["⚠️ $ambiguities ownership ambiguities<br/>See shared-src.json"]
    org -.->|check| ambiguities
    class ambiguities warning
EOF
    fi
}

# ── Dynamic Dual Family Diagram ───────────────────────────────────────────────
emit_dual_family_diagram() {
    local facts_dir="$1"
    local out="$2/16-dual-family-map.mmd"

    log_info "Emitting diagrams/16-dual-family-map.mmd (dynamic)..."

    local dual_family_json="$facts_dir/dual-family.json"

    # Count mirror pairs
    local mirror_count=0
    if [[ -f "$dual_family_json" ]]; then
        mirror_count=$(jq '.mirror_pairs | length' "$dual_family_json" 2>/dev/null || echo "0")
    fi

    # Get sample pairs (first 3)
    local sample_pairs=""
    if [[ -f "$dual_family_json" ]]; then
        sample_pairs=$(jq -r '.mirror_pairs[:3][] | "\(.stateful) ↔ \(.stateless)"' "$dual_family_json" 2>/dev/null)
    fi

    cat > "$out" << EOF
graph LR
    subgraph stateful["Stateful (org.yawlfoundation.yawl.*)"]
        YEngine["YEngine<br/>Workflow Engine"]
        YWorkItem["YWorkItem<br/>Work Item"]
        YTask["YTask<br/>Task Definition"]
    end

    subgraph stateless["Stateless (org.yawlfoundation.yawl.stateless.*)"]
        YEngineS["YEngine<br/>Stateless"]
        YWorkItemS["YWorkItem<br/>Stateless"]
        YTaskS["YTask<br/>Stateless"]
    end

    YEngine <==> YEngineS
    YWorkItem <==> YWorkItemS
    YTask <==> YTaskS

    stats["📊 $mirror_count mirror pairs<br/>Divergence = bugs"]

    style stateful fill:#E3F2FD,stroke:#1976D2
    style stateless fill:#F3E5F5,stroke:#7B1FA2
    classDef stats fill:#FFF3E0,stroke:#E65100,color:#000
    class stats stats
EOF
}

# ── Dynamic Dependency Conflicts Diagram ──────────────────────────────────────
emit_deps_conflicts_diagram() {
    local facts_dir="$1"
    local out="$2/17-deps-conflicts.mmd"

    log_info "Emitting diagrams/17-deps-conflicts.mmd (dynamic)..."

    local deps_json="$facts_dir/deps-conflicts.json"

    # Count conflicts
    local conflicts=0
    if [[ -f "$deps_json" ]]; then
        conflicts=$(jq '.conflicts | length // 0' "$deps_json" 2>/dev/null || echo "0")
    fi

    cat > "$out" << EOF
graph TD
    Parent["📋 Root POM<br/>dependencyManagement<br/>Centralized versions"]

    Engine["yawl-engine<br/>depends: elements, utilities"]
    Integration["yawl-integration<br/>depends: engine, external"]
    Ggen["yawl-ggen<br/>depends: engine, graalpy"]
    Pi["yawl-pi<br/>depends: engine, tpot2"]

    Parent -->|centralizes| Engine
    Parent -->|centralizes| Integration
    Parent -->|centralizes| Ggen
    Parent -->|centralizes| Pi

    conflicts_box["⚠️ $conflicts version overrides<br/>See deps-conflicts.json"]

    Parent -.->|enforces| conflicts_box

    classDef parent fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef child fill:#2196F3,stroke:#1565C0,color:#fff
    classDef warning fill:#FF9800,stroke:#E65100,color:#fff

    class Parent parent
    class Engine,Integration,Ggen,Pi child
    class conflicts_box warning
EOF
}

# ── Dynamic Test Topology Diagram ─────────────────────────────────────────────
emit_test_topology_diagram() {
    local facts_dir="$1"
    local out="$2/30-test-topology.mmd"

    log_info "Emitting diagrams/30-test-topology.mmd (dynamic)..."

    local tests_json="$facts_dir/tests.json"

    # Get test counts
    local total_tests=0
    local test_modules=""

    if [[ -f "$tests_json" ]]; then
        total_tests=$(jq '.total_test_files // 0' "$tests_json" 2>/dev/null || echo "0")
        test_modules=$(jq -r '.modules[:3][] | "\(.name): \(.test_files) tests"' "$tests_json" 2>/dev/null)
    fi

    cat > "$out" << EOF
graph TD
    subgraph surefire["Surefire (Unit Tests)"]
        SureDir["src/test/java"]
        Pattern["**/*Test.java<br/>**/*Tests.java"]
        SureExec["\$ mvn test"]
    end

    subgraph failsafe["Failsafe (Integration Tests)"]
        FailDir["src/test/java"]
        FailPattern["**/*IT.java"]
        FailExec["\$ mvn integration-test"]
    end

    subgraph modules["Test Modules ($total_tests total)"]
        Tests["📊 $total_tests test files<br/>See tests.json"]
    end

    SureDir --> Pattern
    Pattern --> SureExec
    FailDir --> FailPattern
    FailPattern --> FailExec

    SureExec --> modules
    FailExec --> modules

    classDef suretest fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef failtest fill:#F44336,stroke:#C62828,color:#fff
    classDef modtest fill:#2196F3,stroke:#1565C0,color:#fff

    class surefire,SureDir,Pattern,SureExec suretest
    class failsafe,FailDir,FailPattern,FailExec failtest
    class modules,Tests modtest
EOF
}

# ── Dynamic Quality Gates Diagram ─────────────────────────────────────────────
emit_gates_diagram() {
    local facts_dir="$1"
    local out="$2/40-ci-gates.mmd"

    log_info "Emitting diagrams/40-ci-gates.mmd (dynamic)..."

    local gates_json="$facts_dir/gates.json"

    # Get gate status
    local h_status="?"
    local q_status="?"

    if [[ -f "$gates_json" ]]; then
        h_status=$(jq -r '.h_gates.status // "unknown"' "$gates_json" 2>/dev/null)
        q_status=$(jq -r '.q_invariants.status // "unknown"' "$gates_json" 2>/dev/null)
    fi

    # Emoji for status
    local h_emoji="❓"
    local q_emoji="❓"
    [[ "$h_status" == "GREEN" ]] && h_emoji="✅"
    [[ "$h_status" == "RED" ]] && h_emoji="🔴"
    [[ "$q_status" == "GREEN" ]] && q_emoji="✅"
    [[ "$q_status" == "RED" ]] && q_emoji="🔴"

    cat > "$out" << EOF
graph LR
    Build["mvn clean verify"]

    Build -->|Phase: validate| Checkstyle["✓ Checkstyle"]
    Build -->|Phase: validate| Enforcer["✓ Enforcer"]
    Build -->|Phase: test| Surefire["✓ Surefire"]
    Build -->|Phase: verify| SpotBugs["✓ SpotBugs"]

    subgraph yawl_gates["YAWL DX Gates"]
        H["H-Guards $h_emoji<br/>$h_status"]
        Q["Q-Invariants $q_emoji<br/>$q_status"]
    end

    Checkstyle --> H
    Surefire --> Q
    SpotBugs --> Success["✅ BUILD SUCCESS"]

    H --> Success
    Q --> Success

    classDef gate fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef yawl fill:#9C27B0,stroke:#6A1B9A,color:#fff
    classDef result fill:#2196F3,stroke:#1565C0,color:#fff

    class Checkstyle,Enforcer,Surefire,SpotBugs gate
    class H,Q yawl
    class Success result
EOF
}

# ── Dynamic Risk Surfaces Diagram ─────────────────────────────────────────────
emit_risk_surfaces_diagram() {
    local facts_dir="$1"
    local out="$2/50-risk-surfaces.mmd"

    log_info "Emitting diagrams/50-risk-surfaces.mmd (dynamic)..."

    local modules_json="$facts_dir/modules.json"
    local shared_src_json="$facts_dir/shared-src.json"
    local dual_family_json="$facts_dir/dual-family.json"
    local deps_json="$facts_dir/deps-conflicts.json"
    local maven_hazards_json="$facts_dir/maven-hazards.json"

    # Gather risk metrics
    local shared_modules=0
    local ambiguities=0
    local mirror_pairs=0
    local dep_conflicts=0
    local maven_hazards=0

    [[ -f "$modules_json" ]] && shared_modules=$(jq '[.modules[] | select(.strategy == "full_shared")] | length' "$modules_json" 2>/dev/null || echo "0")
    [[ -f "$shared_src_json" ]] && ambiguities=$(jq '.ownership_ambiguities | length // 0' "$shared_src_json" 2>/dev/null || echo "0")
    [[ -f "$dual_family_json" ]] && mirror_pairs=$(jq '.mirror_pairs | length // 0' "$dual_family_json" 2>/dev/null || echo "0")
    [[ -f "$deps_json" ]] && dep_conflicts=$(jq '.conflicts | length // 0' "$deps_json" 2>/dev/null || echo "0")
    [[ -f "$maven_hazards_json" ]] && maven_hazards=$(jq '.hazards | length // 0' "$maven_hazards_json" 2>/dev/null || echo "0")

    cat > "$out" << EOF
graph TD
    subgraph risks["Critical Risk Surfaces"]
        R1["🔴 Shared Source Tree<br/>$shared_modules modules share ../src<br/>$ambiguities ambiguities"]
        R2["🔴 Stateful/Stateless Mirrors<br/>$mirror_pairs pairs must sync<br/>Divergence = bugs"]
        R3["🟡 Maven Cache Hazards<br/>$maven_hazards hazards detected<br/>Rare but deadly"]
        R4["🟡 Dependency Conflicts<br/>$dep_conflicts version overrides<br/>Maintenance burden"]
    end

    subgraph mitigations["Mitigation Strategies"]
        M1["✅ shared-src.json<br/>Validate ownership"]
        M2["✅ dual-family.json<br/>CI sync check"]
        M3["✅ maven-hazards.json<br/>Detect stale cache"]
        M4["✅ deps-conflicts.json<br/>Enforce parent POM"]
    end

    R1 --> M1
    R2 --> M2
    R3 --> M3
    R4 --> M4

    classDef critical fill:#F44336,stroke:#C62828,color:#fff
    classDef warning fill:#FF9800,stroke:#E65100,color:#fff
    classDef mitigation fill:#4CAF50,stroke:#2E7D32,color:#fff

    class R1,R2 critical
    class R3,R4 warning
    class M1,M2,M3,M4 mitigation
EOF
}

# ── Main Entry Point ──────────────────────────────────────────────────────────
run_diagrams() {
    local root_dir="$1"
    local facts_dir="$2"
    local diagrams_dir="$3"

    mkdir -p "$diagrams_dir"

    emit_reactor_diagram "$facts_dir" "$diagrams_dir"
    emit_shared_src_diagram "$facts_dir" "$diagrams_dir"
    emit_dual_family_diagram "$facts_dir" "$diagrams_dir"
    emit_deps_conflicts_diagram "$facts_dir" "$diagrams_dir"
    emit_test_topology_diagram "$facts_dir" "$diagrams_dir"
    emit_gates_diagram "$facts_dir" "$diagrams_dir"
    emit_risk_surfaces_diagram "$facts_dir" "$diagrams_dir"
}
