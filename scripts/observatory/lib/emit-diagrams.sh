#!/usr/bin/env bash
# ==========================================================================
# emit-diagrams.sh — Generates all 7 Mermaid diagram files
#
# Each diagram is regenerated from scratch using data from $FACTS_DIR.
# Sources util.sh for constants and helpers.
# ==========================================================================

# ── 10-maven-reactor.mmd ─────────────────────────────────────────────────
emit_reactor_diagram() {
    local out="$DIAGRAMS_DIR/10-maven-reactor.mmd"
    log_info "Emitting diagrams/10-maven-reactor.mmd ..."

    local reactor_json="$FACTS_DIR/reactor.json"

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#2d3748' } } }%%"
        echo "graph TD"
        echo "    classDef parent fill:#4a5568,stroke:#2d3748,color:#fff"
        echo "    classDef fullshared fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef pkgscoped fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef infra fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""
        echo "    PARENT[yawl-parent<br/>6.0.0-Alpha]:::parent"
        echo ""

        # Read reactor order
        local modules
        modules=$(discover_modules)

        # Classify each module
        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            local pom="$REPO_ROOT/$mod/pom.xml"
            local src_dir
            src_dir=$(grep -oP '(?<=<sourceDirectory>)[^<]+' "$pom" 2>/dev/null || echo "")
            local class="pkgscoped"
            if [[ "$src_dir" == "../src" ]]; then
                class="fullshared"
            fi
            local display="${mod}"
            echo "    ${mod//[-]/_}[${display}]:::${class}"
        done <<< "$modules"

        echo ""
        echo "    %% Reactor ordering (parent -> all modules)"
        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            echo "    PARENT --> ${mod//[-]/_}"
        done <<< "$modules"

        echo ""
        echo "    %% Inter-module dependencies"
        if [[ -f "$reactor_json" ]]; then
            grep '"from"' "$reactor_json" 2>/dev/null | while IFS= read -r line; do
                local from to
                from=$(echo "$line" | grep -oP '(?<="from":")[^"]+')
                to=$(echo "$line" | grep -oP '(?<="to":")[^"]+')
                [[ -z "$from" || -z "$to" ]] && continue
                echo "    ${from//[-]/_} --> ${to//[-]/_}"
            done
        fi

        echo ""
        echo "    %% Legend"
        echo "    subgraph Legend"
        echo "        L1[Full Shared ../src]:::fullshared"
        echo "        L2[Package Scoped]:::pkgscoped"
        echo "    end"
    } > "$out"
}

# ── 15-shared-src-map.mmd ────────────────────────────────────────────────
emit_shared_src_diagram() {
    local out="$DIAGRAMS_DIR/15-shared-src-map.mmd"
    log_info "Emitting diagrams/15-shared-src-map.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph LR"
        echo "    classDef srcroot fill:#fed7d7,stroke:#c53030"
        echo "    classDef fullmod fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef pkgmod fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef pkg fill:#edf2f7,stroke:#4a5568"
        echo ""
        echo "    SRC[\"src/<br/>Shared Source Root\"]:::srcroot"
        echo "    TEST[\"test/<br/>Shared Test Root\"]:::srcroot"
        echo ""

        # Full shared modules (point to ../src)
        echo "    subgraph FullShared[\"Full Shared Access Modules\"]"
        for mod in yawl-elements yawl-engine yawl-stateless yawl-utilities yawl-security; do
            echo "        ${mod//[-]/_}[${mod}]:::fullmod"
        done
        echo "    end"
        echo ""

        # Package-scoped modules
        echo "    subgraph PackageScoped[\"Package-Scoped Modules\"]"
        for mod in yawl-authentication yawl-control-panel yawl-integration yawl-monitoring yawl-resourcing yawl-scheduling yawl-worklet; do
            echo "        ${mod//[-]/_}[${mod}]:::pkgmod"
        done
        echo "    end"
        echo ""

        # Source packages
        echo "    subgraph Packages[\"Source Packages\"]"
        echo "        elements[elements/]:::pkg"
        echo "        engine[engine/]:::pkg"
        echo "        stateless[stateless/]:::pkg"
        echo "        util[util/]:::pkg"
        echo "        schema_pkg[schema/]:::pkg"
        echo "        unmarshal[unmarshal/]:::pkg"
        echo "        authentication[authentication/]:::pkg"
        echo "        resourcing[resourcing/]:::pkg"
        echo "        scheduling[scheduling/]:::pkg"
        echo "        worklet[worklet/]:::pkg"
        echo "        integration_pkg[integration/]:::pkg"
        echo "        observability[observability/]:::pkg"
        echo "        controlpanel[controlpanel/]:::pkg"
        echo "        security_pkg[security/]:::pkg"
        echo "    end"
        echo ""

        # Full shared -> source root
        for mod in yawl-elements yawl-engine yawl-stateless yawl-utilities yawl-security; do
            echo "    SRC --> ${mod//[-]/_}"
        done
        echo ""

        # Package-scoped -> specific packages
        echo "    yawl_authentication --> authentication"
        echo "    yawl_control_panel --> controlpanel"
        echo "    yawl_integration --> integration_pkg"
        echo "    yawl_monitoring --> observability"
        echo "    yawl_resourcing --> resourcing"
        echo "    yawl_scheduling --> scheduling"
        echo "    yawl_worklet --> worklet"
        echo ""

        # Full shared modules use include filters
        echo "    yawl_elements -.->|includes filter| elements"
        echo "    yawl_elements -.->|includes filter| schema_pkg"
        echo "    yawl_engine -.->|includes filter| engine"
        echo "    yawl_stateless -.->|includes filter| stateless"
        echo "    yawl_utilities -.->|includes filter| util"
        echo "    yawl_utilities -.->|includes filter| unmarshal"
        echo "    yawl_security -.->|includes filter| security_pkg"
    } > "$out"
}

# ── 16-dual-family-map.mmd ───────────────────────────────────────────────
emit_dual_family_diagram() {
    local out="$DIAGRAMS_DIR/16-dual-family-map.mmd"
    log_info "Emitting diagrams/16-dual-family-map.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph TB"
        echo "    classDef stateful fill:#4a5568,stroke:#2d3748,color:#fff"
        echo "    classDef stateless fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef mirror fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo ""
        echo "    subgraph Stateful[\"org.yawlfoundation.yawl.engine\"]"
        echo "        sYEngine[YEngine]:::stateful"
        echo "        sYNetRunner[YNetRunner]:::stateful"
        echo "        sYWorkItem[YWorkItem]:::stateful"
        echo "        sYWorkItemID[YWorkItemID]:::stateful"
        echo "        sYAnnouncer[YAnnouncer]:::stateful"
        echo "        sYNetRunnerRepo[YNetRunnerRepository]:::stateful"
        echo "        sYWorkItemRepo[YWorkItemRepository]:::stateful"
        echo "        sYTimerVar[YTimerVariable]:::stateful"
        echo "        sYWorkItemTimer[YWorkItemTimer]:::stateful"
        echo "    end"
        echo ""
        echo "    subgraph Stateless[\"org.yawlfoundation.yawl.stateless.engine\"]"
        echo "        slYEngine[YEngine]:::stateless"
        echo "        slYNetRunner[YNetRunner]:::stateless"
        echo "        slYWorkItem[YWorkItem]:::stateless"
        echo "        slYWorkItemID[YWorkItemID]:::stateless"
        echo "        slYAnnouncer[YAnnouncer]:::stateless"
        echo "        slYNetRunnerRepo[YNetRunnerRepository]:::stateless"
        echo "        slYWorkItemRepo[YWorkItemRepository]:::stateless"
        echo "        slYTimerVar[YTimerVariable]:::stateless"
        echo "        slYWorkItemTimer[YWorkItemTimer]:::stateless"
        echo "    end"
        echo ""
        echo "    %% Mirror relationships (MIRROR_REQUIRED policy)"
        echo "    sYEngine <-.->|mirror| slYEngine"
        echo "    sYNetRunner <-.->|mirror| slYNetRunner"
        echo "    sYWorkItem <-.->|mirror| slYWorkItem"
        echo "    sYWorkItemID <-.->|mirror| slYWorkItemID"
        echo "    sYAnnouncer <-.->|mirror| slYAnnouncer"
        echo "    sYNetRunnerRepo <-.->|mirror| slYNetRunnerRepo"
        echo "    sYWorkItemRepo <-.->|mirror| slYWorkItemRepo"
        echo "    sYTimerVar <-.->|mirror| slYTimerVar"
        echo "    sYWorkItemTimer <-.->|mirror| slYWorkItemTimer"
        echo ""

        # Also show the elements mirror layer
        echo "    subgraph StatefulElements[\"org.yawlfoundation.yawl.elements\"]"
        echo "        eYNet[YNet]:::stateful"
        echo "        eYTask[YTask]:::stateful"
        echo "        eYAtomicTask[YAtomicTask]:::stateful"
        echo "        eYCompositeTask[YCompositeTask]:::stateful"
        echo "        eYCondition[YCondition]:::stateful"
        echo "        eYFlow[YFlow]:::stateful"
        echo "        eYDecomp[YDecomposition]:::stateful"
        echo "    end"
        echo ""
        echo "    subgraph StatelessElements[\"org.yawlfoundation.yawl.stateless.elements\"]"
        echo "        slYNet[YNet]:::stateless"
        echo "        slYTask[YTask]:::stateless"
        echo "        slYAtomicTask[YAtomicTask]:::stateless"
        echo "        slYCompositeTask[YCompositeTask]:::stateless"
        echo "        slYCondition[YCondition]:::stateless"
        echo "        slYFlow[YFlow]:::stateless"
        echo "        slYDecomp[YDecomposition]:::stateless"
        echo "    end"
        echo ""
        echo "    eYNet <-.->|mirror| slYNet"
        echo "    eYTask <-.->|mirror| slYTask"
        echo "    eYAtomicTask <-.->|mirror| slYAtomicTask"
        echo "    eYCompositeTask <-.->|mirror| slYCompositeTask"
        echo "    eYCondition <-.->|mirror| slYCondition"
        echo "    eYFlow <-.->|mirror| slYFlow"
        echo "    eYDecomp <-.->|mirror| slYDecomp"
    } > "$out"
}

# ── 17-deps-conflicts.mmd ────────────────────────────────────────────────
emit_deps_conflicts_diagram() {
    local out="$DIAGRAMS_DIR/17-deps-conflicts.mmd"
    log_info "Emitting diagrams/17-deps-conflicts.mmd ..."

    local deps_json="$FACTS_DIR/deps-conflicts.json"

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph LR"
        echo "    classDef managed fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef conflict fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef module fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo ""
        echo "    PARENT_POM[\"Parent POM<br/>dependencyManagement\"]:::managed"
        echo ""

        # Show the dependency management categories
        echo "    subgraph ManagedCategories[\"Managed Categories\"]"
        echo "        CAT_JAKARTA[Jakarta EE]:::managed"
        echo "        CAT_SPRING[Spring Boot]:::managed"
        echo "        CAT_COMMONS[Apache Commons]:::managed"
        echo "        CAT_JACKSON[Jackson]:::managed"
        echo "        CAT_LOGGING[Logging]:::managed"
        echo "        CAT_DB[Database]:::managed"
        echo "        CAT_XML[XML Processing]:::managed"
        echo "        CAT_SECURITY[Security/Auth]:::managed"
        echo "        CAT_MCP[MCP/A2A]:::managed"
        echo "        CAT_REST[REST/Jersey]:::managed"
        echo "        CAT_OBSERVE[Observability]:::managed"
        echo "        CAT_TEST[Testing]:::managed"
        echo "    end"
        echo ""
        echo "    PARENT_POM --> CAT_JAKARTA"
        echo "    PARENT_POM --> CAT_SPRING"
        echo "    PARENT_POM --> CAT_COMMONS"
        echo "    PARENT_POM --> CAT_JACKSON"
        echo "    PARENT_POM --> CAT_LOGGING"
        echo "    PARENT_POM --> CAT_DB"
        echo "    PARENT_POM --> CAT_XML"
        echo "    PARENT_POM --> CAT_SECURITY"
        echo "    PARENT_POM --> CAT_MCP"
        echo "    PARENT_POM --> CAT_REST"
        echo "    PARENT_POM --> CAT_OBSERVE"
        echo "    PARENT_POM --> CAT_TEST"
        echo ""

        # Known transitive conflict hotspots
        echo "    subgraph TransitiveHotspots[\"Known Transitive Conflict Hotspots\"]"
        echo "        H_LOG[\"log4j-to-slf4j vs<br/>log4j-slf4j2-impl\"]:::conflict"
        echo "        H_COMMONS_IO[\"commons-io<br/>dual groupId\"]:::conflict"
        echo "        H_COMMONS_CODEC[\"commons-codec<br/>dual groupId\"]:::conflict"
        echo "        H_JACKSON[\"Jackson version<br/>Spring vs explicit\"]:::conflict"
        echo "        H_JAKARTA[\"Jakarta API<br/>version alignment\"]:::conflict"
        echo "    end"
        echo ""
        echo "    CAT_LOGGING -.->|conflict zone| H_LOG"
        echo "    CAT_COMMONS -.->|conflict zone| H_COMMONS_IO"
        echo "    CAT_COMMONS -.->|conflict zone| H_COMMONS_CODEC"
        echo "    CAT_JACKSON -.->|conflict zone| H_JACKSON"
        echo "    CAT_JAKARTA -.->|conflict zone| H_JAKARTA"

        # Show any explicit conflicts from facts
        if [[ -f "$deps_json" ]]; then
            local conflict_count
            conflict_count=$(grep -c '"ga"' "$deps_json" 2>/dev/null || echo "0")
            conflict_count=$(echo "$conflict_count" | tr -d '[:space:]')
            if [[ "$conflict_count" -gt 0 ]] 2>/dev/null; then
                echo ""
                echo "    subgraph ExplicitConflicts[\"Explicit Version Conflicts: ${conflict_count}\"]"
                local idx=0
                grep '"ga"' "$deps_json" 2>/dev/null | head -10 | while IFS= read -r line; do
                    local ga
                    ga=$(echo "$line" | grep -oP '(?<="ga":")[^"]+')
                    [[ -z "$ga" ]] && continue
                    idx=$((idx + 1))
                    echo "        EC${idx}[\"${ga}\"]:::conflict"
                done
                echo "    end"
            fi
        fi
    } > "$out"
}

# ── 30-test-topology.mmd ─────────────────────────────────────────────────
emit_test_topology_diagram() {
    local out="$DIAGRAMS_DIR/30-test-topology.mmd"
    log_info "Emitting diagrams/30-test-topology.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph TD"
        echo "    classDef unit fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef integration fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef notested fill:#a0aec0,stroke:#718096,color:#fff"
        echo "    classDef plugin fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo ""
        echo "    SUREFIRE[\"maven-surefire-plugin<br/>Unit Tests\"]:::plugin"
        echo "    FAILSAFE[\"maven-failsafe-plugin<br/>Integration Tests\"]:::plugin"
        echo "    SHARED_TEST[\"test/<br/>Shared Test Root\"]"
        echo ""

        local modules
        modules=$(discover_modules)

        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            local pom="$REPO_ROOT/$mod/pom.xml"
            [[ -f "$pom" ]] || continue

            local test_dir
            test_dir=$(grep -oP '(?<=<testSourceDirectory>)[^<]+' "$pom" 2>/dev/null || echo "")
            local resolved_test=""
            [[ -n "$test_dir" ]] && resolved_test="${REPO_ROOT}/${mod}/${test_dir}"

            local unit_count=0 it_count=0
            if [[ -d "$resolved_test" ]]; then
                unit_count=$(find "$resolved_test" -name "*Test.java" -type f 2>/dev/null | wc -l)
                it_count=$(find "$resolved_test" -name "*IT.java" -type f 2>/dev/null | wc -l)
            fi

            local class="notested"
            [[ $unit_count -gt 0 ]] && class="unit"
            [[ $it_count -gt 0 ]] && class="integration"

            local safe_mod="${mod//[-]/_}"
            echo "    ${safe_mod}[\"${mod}<br/>unit:${unit_count} it:${it_count}\"]:::${class}"

            if [[ $unit_count -gt 0 ]]; then
                echo "    SUREFIRE --> ${safe_mod}"
            fi
            if [[ $it_count -gt 0 ]]; then
                echo "    FAILSAFE --> ${safe_mod}"
            fi

            # Show test source relationship
            if [[ "$test_dir" == "../test" ]]; then
                echo "    SHARED_TEST --> ${safe_mod}"
            fi
        done <<< "$modules"
    } > "$out"
}

# ── 40-ci-gates.mmd ──────────────────────────────────────────────────────
emit_ci_gates_diagram() {
    local out="$DIAGRAMS_DIR/40-ci-gates.mmd"
    log_info "Emitting diagrams/40-ci-gates.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph LR"
        echo "    classDef phase fill:#4a5568,stroke:#2d3748,color:#fff"
        echo "    classDef gate fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef skip fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef warn fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo ""
        echo "    %% Maven Lifecycle Phases"
        echo "    VALIDATE[validate]:::phase"
        echo "    COMPILE[compile]:::phase"
        echo "    TEST[test]:::phase"
        echo "    PACKAGE[package]:::phase"
        echo "    VERIFY[verify]:::phase"
        echo "    INSTALL[install]:::phase"
        echo ""
        echo "    VALIDATE --> COMPILE --> TEST --> PACKAGE --> VERIFY --> INSTALL"
        echo ""
        echo "    %% Gates bound to phases"
        echo "    ENFORCER[\"enforcer<br/>dependency rules\"]:::gate"
        echo "    SUREFIRE[\"surefire<br/>unit tests\"]:::gate"
        echo "    JACOCO[\"jacoco<br/>coverage\"]:::gate"
        echo "    SPOTBUGS[\"spotbugs<br/>bug detection\"]:::gate"
        echo "    PMD_GATE[\"pmd<br/>code quality\"]:::gate"
        echo "    CHECKSTYLE[\"checkstyle<br/>style rules\"]:::gate"
        echo "    FAILSAFE[\"failsafe<br/>integration tests\"]:::gate"
        echo "    OWASP[\"owasp<br/>dependency check\"]:::gate"
        echo ""
        echo "    VALIDATE --> ENFORCER"
        echo "    TEST --> SUREFIRE"
        echo "    VERIFY --> JACOCO"
        echo "    VERIFY --> SPOTBUGS"
        echo "    VERIFY --> PMD_GATE"
        echo "    VERIFY --> CHECKSTYLE"
        echo "    VERIFY --> FAILSAFE"
        echo "    VERIFY --> OWASP"
        echo ""
        echo "    %% Skip flags (risk indicators)"
        echo "    subgraph SkipFlags[\"Skip Flags = Risk\"]"
        echo "        SKIP_TESTS[\"-DskipTests=true\"]:::skip"
        echo "        SKIP_ITS[\"-DskipITs=true\"]:::skip"
        echo "        SKIP_SB[\"-Dspotbugs.skip\"]:::warn"
        echo "        SKIP_PMD[\"-Dpmd.skip\"]:::warn"
        echo "        SKIP_CS[\"-Dcheckstyle.skip\"]:::warn"
        echo "        SKIP_ENF[\"-Denforcer.skip\"]:::skip"
        echo "    end"
        echo ""
        echo "    SKIP_TESTS -.->|disables| SUREFIRE"
        echo "    SKIP_TESTS -.->|disables| FAILSAFE"
        echo "    SKIP_ITS -.->|disables| FAILSAFE"
        echo "    SKIP_SB -.->|disables| SPOTBUGS"
        echo "    SKIP_PMD -.->|disables| PMD_GATE"
        echo "    SKIP_CS -.->|disables| CHECKSTYLE"
        echo "    SKIP_ENF -.->|disables| ENFORCER"
    } > "$out"
}

# ── 50-risk-surfaces.mmd (FMEA) ──────────────────────────────────────────
emit_risk_surfaces_diagram() {
    local out="$DIAGRAMS_DIR/50-risk-surfaces.mmd"
    log_info "Emitting diagrams/50-risk-surfaces.mmd (FMEA) ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "graph TD"
        echo "    classDef critical fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef high fill:#dd6b20,stroke:#c05621,color:#fff"
        echo "    classDef medium fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef low fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef mitigated fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo ""
        echo "    FMEA[\"FMEA Risk Surface Map<br/>YAWL V6 Observatory\"]"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 1: Shared Source Path Confusion"
        echo "    %% Severity=9 Occurrence=8 Detection=3 RPN=216"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM1[\"FM1: Shared Source Path Confusion<br/>RPN=216 CRITICAL\"]"
        echo "        FM1_CAUSE[\"Cause: 5 modules share ../src<br/>with include/exclude filters\"]"
        echo "        FM1_EFFECT[\"Effect: Agent edits wrong file<br/>or misattributes class ownership\"]"
        echo "        FM1_DETECT[\"Detection: shared-src.json +<br/>15-shared-src-map.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM1:::critical"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 2: Dual-Family Class Confusion"
        echo "    %% Severity=8 Occurrence=7 Detection=4 RPN=224"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM2[\"FM2: Dual-Family Class Confusion<br/>RPN=224 CRITICAL\"]"
        echo "        FM2_CAUSE[\"Cause: 48+ mirrored classes in<br/>stateful + stateless packages\"]"
        echo "        FM2_EFFECT[\"Effect: Change applied to wrong<br/>variant; silent behavioral divergence\"]"
        echo "        FM2_DETECT[\"Detection: dual-family.json +<br/>16-dual-family-map.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM2:::critical"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 3: Dependency Version Skew"
        echo "    %% Severity=7 Occurrence=6 Detection=5 RPN=210"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM3[\"FM3: Dependency Version Skew<br/>RPN=210 HIGH\"]"
        echo "        FM3_CAUSE[\"Cause: Transitive deps pull<br/>different versions across modules\"]"
        echo "        FM3_EFFECT[\"Effect: Runtime ClassNotFound or<br/>NoSuchMethod at deploy time\"]"
        echo "        FM3_DETECT[\"Detection: deps-conflicts.json +<br/>17-deps-conflicts.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM3:::high"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 4: Maven Cached Missing Artifacts"
        echo "    %% Severity=6 Occurrence=5 Detection=2 RPN=60"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM4[\"FM4: Maven Cached Missing Artifacts<br/>RPN=60 MEDIUM\"]"
        echo "        FM4_CAUSE[\"Cause: Failed download cached in<br/>~/.m2 with .lastUpdated marker\"]"
        echo "        FM4_EFFECT[\"Effect: Build fails even after<br/>network restored; misleading errors\"]"
        echo "        FM4_DETECT[\"Detection: maven-hazards.json<br/>+ actionable remediation\"]:::mitigated"
        echo "    end"
        echo "    FM4:::medium"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 5: Test Selection Ambiguity"
        echo "    %% Severity=7 Occurrence=4 Detection=3 RPN=84"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM5[\"FM5: Test Selection Ambiguity<br/>RPN=84 MEDIUM\"]"
        echo "        FM5_CAUSE[\"Cause: Shared ../test root with<br/>module-scoped test filtering\"]"
        echo "        FM5_EFFECT[\"Effect: Test runs wrong test class<br/>or misses coverage target\"]"
        echo "        FM5_DETECT[\"Detection: tests.json +<br/>30-test-topology.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM5:::medium"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 6: Gate Bypass via Skip Flags"
        echo "    %% Severity=8 Occurrence=3 Detection=6 RPN=144"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM6[\"FM6: Gate Bypass via Skip Flags<br/>RPN=144 HIGH\"]"
        echo "        FM6_CAUSE[\"Cause: -DskipTests, -Denforcer.skip<br/>disable safety gates\"]"
        echo "        FM6_EFFECT[\"Effect: Broken code merged;<br/>bugs reach production\"]"
        echo "        FM6_DETECT[\"Detection: gates.json +<br/>40-ci-gates.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM6:::high"
        echo ""
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    %% FAILURE MODE 7: Reactor Order Violation"
        echo "    %% Severity=5 Occurrence=3 Detection=7 RPN=105"
        echo "    %% ═══════════════════════════════════════════════════"
        echo "    subgraph FM7[\"FM7: Reactor Order Violation<br/>RPN=105 MEDIUM\"]"
        echo "        FM7_CAUSE[\"Cause: Module added to POM in<br/>wrong position vs its deps\"]"
        echo "        FM7_EFFECT[\"Effect: Compile failure on first<br/>build; confusing error messages\"]"
        echo "        FM7_DETECT[\"Detection: reactor.json +<br/>10-maven-reactor.mmd\"]:::mitigated"
        echo "    end"
        echo "    FM7:::medium"
        echo ""
        echo "    %% Connections: FMEA -> Failure Modes"
        echo "    FMEA --> FM1"
        echo "    FMEA --> FM2"
        echo "    FMEA --> FM3"
        echo "    FMEA --> FM4"
        echo "    FMEA --> FM5"
        echo "    FMEA --> FM6"
        echo "    FMEA --> FM7"
        echo ""
        echo "    %% Mitigation chain: Diagrams + Facts reduce Detection score"
        echo "    subgraph Mitigation[\"Observatory Mitigations\"]"
        echo "        M_FACTS[\"Facts: 9 JSON files<br/>Machine-readable truth\"]:::mitigated"
        echo "        M_DIAGRAMS[\"Diagrams: 7 Mermaid files<br/>Visual topology maps\"]:::mitigated"
        echo "        M_YAWL[\"YAWL XML: Build workflow<br/>as executable net\"]:::mitigated"
        echo "        M_RECEIPT[\"Receipt: observatory.json<br/>Deterministic verification\"]:::mitigated"
        echo "    end"
        echo ""
        echo "    FM1_DETECT --> M_FACTS"
        echo "    FM2_DETECT --> M_DIAGRAMS"
        echo "    FM3_DETECT --> M_FACTS"
        echo "    FM4_DETECT --> M_FACTS"
        echo "    FM5_DETECT --> M_DIAGRAMS"
        echo "    FM6_DETECT --> M_DIAGRAMS"
        echo "    FM7_DETECT --> M_FACTS"
    } > "$out"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_all_diagrams() {
    timer_start
    emit_reactor_diagram
    emit_shared_src_diagram
    emit_dual_family_diagram
    emit_deps_conflicts_diagram
    emit_test_topology_diagram
    emit_ci_gates_diagram
    emit_risk_surfaces_diagram
    DIAGRAMS_ELAPSED=$(timer_elapsed_ms)
    log_ok "All diagrams emitted in ${DIAGRAMS_ELAPSED}ms"
}
