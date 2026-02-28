#!/bin/bash
#
# Observatory diagram emitters
# Each emit_*_diagram() function generates a Mermaid visualization
#

# Emit Maven reactor DAG
emit_reactor_diagram() {
    local facts_dir="$1"
    local out="$2/10-maven-reactor.mmd"

    log_info "Emitting diagrams/10-maven-reactor.mmd ..."

    cat > "$out" << 'EOF'
graph TD
    subgraph "YAWL v6 Maven Reactor"
        yawl-parent["📦 yawl-parent<br/>Root POM"]
        yawl-utilities["🔧 yawl-utilities<br/>Shared utilities"]
        yawl-elements["📋 yawl-elements<br/>YAWL domain model"]
        yawl-engine["⚙️ yawl-engine<br/>Workflow engine<br/>736 Java files"]
        yawl-resourcing["👥 yawl-resourcing<br/>Resource allocation"]
        yawl-authentication["🔐 yawl-authentication<br/>Auth & crypto"]
        yawl-integration["🌐 yawl-integration<br/>MCP/A2A endpoints"]
        yawl-stateless["🔄 yawl-stateless<br/>Stateless mirrors"]
        yawl-ggen["🎯 yawl-ggen<br/>Code generation"]
        yawl-mcp-a2a["📡 yawl-mcp-a2a<br/>MCP/A2A server"]
        yawl-pi["🧠 yawl-pi<br/>Process Intelligence"]
    end

    yawl-parent --> yawl-utilities
    yawl-parent --> yawl-elements
    yawl-utilities --> yawl-engine
    yawl-elements --> yawl-engine
    yawl-engine --> yawl-resourcing
    yawl-engine --> yawl-authentication
    yawl-engine --> yawl-integration
    yawl-engine --> yawl-stateless
    yawl-integration --> yawl-ggen
    yawl-integration --> yawl-mcp-a2a
    yawl-engine --> yawl-pi

    classDef primary fill:#4A90E2,stroke:#2E5C8A,color:#fff
    classDef support fill:#7B68EE,stroke:#5A4BA3,color:#fff
    classDef data fill:#50C878,stroke:#2D7A4A,color:#fff
    classDef meta fill:#FFB84D,stroke:#CC8C00,color:#fff

    class yawl-parent primary
    class yawl-engine primary
    class yawl-utilities,yawl-elements support
    class yawl-resourcing,yawl-authentication,yawl-integration data
    class yawl-ggen,yawl-mcp-a2a,yawl-stateless,yawl-pi meta
EOF
}

# Emit shared source ownership diagram
emit_shared_src_diagram() {
    local facts_dir="$1"
    local out="$2/15-shared-src-map.mmd"

    log_info "Emitting diagrams/15-shared-src-map.mmd ..."

    cat > "$out" << 'EOF'
graph TB
    subgraph "Shared Source Tree (/src)"
        org["📁 org/yawlfoundation/yawl/"]
        utils["🔧 utils/"]
        engine["⚙️ engine/"]
        elements["📋 elements/"]
        auth["🔐 authentication/"]
        time["⏱️ engine/time/"]
        stateless["🔄 stateless/"]
    end

    subgraph "Module Ownership & Filters"
        yawl-utilities["yawl-utilities<br/>includes: **/utils/**"]
        yawl-engine["yawl-engine<br/>includes: **/engine/**"]
        yawl-elements["yawl-elements<br/>includes: **/elements/**"]
        yawl-auth["yawl-authentication<br/>includes: **/auth/**"]
    end

    org --> utils
    org --> engine
    org --> elements
    org --> auth
    engine --> time
    org --> stateless

    yawl-utilities -.-> utils
    yawl-engine -.-> engine
    yawl-elements -.-> elements
    yawl-auth -.-> auth

    classDef source fill:#E8F4F8,stroke:#2E7D8C,color:#000
    classDef owner fill:#C8E6C9,stroke:#2E7A2E,color:#000
    class org,utils,engine,elements,auth,time,stateless source
    class yawl-utilities,yawl-engine,yawl-elements,yawl-auth owner
EOF
}

# Emit stateful/stateless mirror diagram
emit_dual_family_diagram() {
    local facts_dir="$1"
    local out="$2/16-dual-family-map.mmd"

    log_info "Emitting diagrams/16-dual-family-map.mmd ..."

    cat > "$out" << 'EOF'
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

    style stateful fill:#E3F2FD,stroke:#1976D2
    style stateless fill:#F3E5F5,stroke:#7B1FA2
EOF
}

# Emit dependency conflicts diagram
emit_deps_conflicts_diagram() {
    local facts_dir="$1"
    local out="$2/17-deps-conflicts.mmd"

    log_info "Emitting diagrams/17-deps-conflicts.mmd ..."

    cat > "$out" << 'EOF'
graph TD
    Parent["📋 Root POM<br/>dependencyManagement<br/>110+ managed versions"]

    Child1["yawl-engine<br/>depends-on:<br/>yawl-elements<br/>yawl-utilities"]
    Child2["yawl-integration<br/>depends-on:<br/>yawl-engine<br/>external libs"]
    Child3["yawl-ggen<br/>depends-on:<br/>yawl-engine"]

    Parent -->|centralizes| Child1
    Parent -->|centralizes| Child2
    Parent -->|centralizes| Child3

    Strategy["✅ Strategy: Parent POM<br/>centralizes all versions<br/>Child POMs use property<br/>references only"]

    Parent -.->|enforces| Strategy

    classDef parent fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef child fill:#2196F3,stroke:#1565C0,color:#fff
    classDef strategy fill:#FF9800,stroke:#E65100,color:#fff

    class Parent parent
    class Child1,Child2,Child3 child
    class Strategy strategy
EOF
}

# Emit test topology diagram
emit_test_topology_diagram() {
    local facts_dir="$1"
    local out="$2/30-test-topology.mmd"

    log_info "Emitting diagrams/30-test-topology.mmd ..."

    cat > "$out" << 'EOF'
graph TD
    subgraph surefire["Surefire (Unit Tests)"]
        SureDir["src/test/java"]
        Pattern["**/*Test.java<br/>**/*Tests.java"]
        SureExec["$ mvn test"]
    end

    subgraph failsafe["Failsafe (Integration Tests)"]
        FailDir["src/test/java"]
        FailPattern["**/*IT.java"]
        FailExec["$ mvn integration-test"]
    end

    subgraph modules["Test Modules"]
        YEngine["yawl-engine<br/>184 test files"]
        YElements["yawl-elements<br/>42 test files"]
        YUtils["yawl-utilities<br/>28 test files"]
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
    class modules,YEngine,YElements,YUtils modtest
EOF
}

# Emit quality gates diagram
emit_gates_diagram() {
    local facts_dir="$1"
    local out="$2/40-ci-gates.mmd"

    log_info "Emitting diagrams/40-ci-gates.mmd ..."

    cat > "$out" << 'EOF'
graph LR
    Build["mvn clean verify"]

    Build -->|Phase: validate| Checkstyle["✓ Checkstyle<br/>Code style"]
    Build -->|Phase: validate| Enforcer["✓ Enforcer<br/>Dependency rules"]

    Build -->|Phase: test| Surefire["✓ Surefire<br/>Unit tests"]

    Build -->|Phase: verify| PMD["✓ PMD<br/>Code analysis"]
    Build -->|Phase: verify| SpotBugs["✓ SpotBugs<br/>Bug detection"]
    Build -->|Phase: verify| JaCoCo["✓ JaCoCo<br/>Code coverage"]
    Build -->|Phase: verify| DependencyCheck["⚠️ DependencyCheck<br/>Vuln scan"]

    Checkstyle --> Success["✅ BUILD SUCCESS"]
    Enforcer --> Success
    Surefire --> Success
    PMD --> Success
    SpotBugs --> Success
    JaCoCo --> Success
    DependencyCheck --> Success

    classDef gate fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef warning fill:#FF9800,stroke:#E65100,color:#fff
    classDef result fill:#2196F3,stroke:#1565C0,color:#fff

    class Checkstyle,Enforcer,Surefire,PMD,SpotBugs,JaCoCo gate
    class DependencyCheck warning
    class Success result
EOF
}

# Emit risk surface (FMEA)
emit_risk_surfaces_diagram() {
    local facts_dir="$1"
    local out="$2/50-risk-surfaces.mmd"

    log_info "Emitting diagrams/50-risk-surfaces.mmd ..."

    cat > "$out" << 'EOF'
graph TD
    subgraph risks["Critical Risk Surfaces"]
        R1["🔴 Shared Source Tree<br/>Multiple modules claim ../src<br/>Unintended overlaps"]
        R2["🔴 Stateful/Stateless Mirrors<br/>51 mirror pairs must stay in sync<br/>Divergence = bugs"]
        R3["🟡 Maven Cache Hazards<br/>Stale artifacts in ~/.m2<br/>Rare but deadly"]
        R4["🟡 Dependency Conflicts<br/>21 explicit versions override parent<br/>Maintenance burden"]
        R5["🟡 Missing Source Dirs<br/>Module points to non-existent path<br/>Breaks compilation"]
    end

    subgraph mitigations["Mitigation Strategies"]
        M1["Observatory fact: shared-src.json<br/>Validate ownership filters"]
        M2["Observatory fact: dual-family.json<br/>CI gate: verify mirror consistency"]
        M3["Observatory fact: maven-hazards.json<br/>CI gate: detect .lastUpdated files"]
        M4["Observatory fact: deps-conflicts.json<br/>Enforce parent POM strategy"]
        M5["Observatory fact: modules.json<br/>CI gate: verify source dirs exist"]
    end

    R1 --> M1
    R2 --> M2
    R3 --> M3
    R4 --> M4
    R5 --> M5

    classDef critical fill:#F44336,stroke:#C62828,color:#fff
    classDef warning fill:#FF9800,stroke:#E65100,color:#fff
    classDef mitigation fill:#4CAF50,stroke:#2E7D32,color:#fff

    class R1,R2 critical
    class R3,R4,R5 warning
    class M1,M2,M3,M4,M5 mitigation
EOF
}

# Main function to run all diagram emitters
run_diagrams() {
    local root_dir="$1"
    local facts_dir="$2"
    local diagrams_dir="$3"

    emit_reactor_diagram "$facts_dir" "$diagrams_dir"
    emit_shared_src_diagram "$facts_dir" "$diagrams_dir"
    emit_dual_family_diagram "$facts_dir" "$diagrams_dir"
    emit_deps_conflicts_diagram "$facts_dir" "$diagrams_dir"
    emit_test_topology_diagram "$facts_dir" "$diagrams_dir"
    emit_gates_diagram "$facts_dir" "$diagrams_dir"
    emit_risk_surfaces_diagram "$facts_dir" "$diagrams_dir"
}
