#!/usr/bin/env bash
# ==========================================================================
# emit-yawl-xml.sh — Generates YAWL workflow XML specifications
#
# Generates YAWL XML representing the build lifecycle as a Petri-net-based
# workflow specification.
# ==========================================================================

# ── XML Escaping for Safe Interpolation ───────────────────────────────────
xml_escape() {
    local s="$1"
    s="${s//&/&amp;}"
    s="${s//</&lt;}"
    s="${s//>/&gt;}"
    s="${s//\"/&quot;}"
    s="${s//\'/&apos;}"
    printf '%s' "$s"
}

# ── Build and Test Workflow YAWL XML ─────────────────────────────────────
emit_build_test_yawl() {
    local out="$YAWL_DIR/build-and-test.yawl.xml"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/yawl/build-and-test.yawl.xml ..."

    mkdir -p "$YAWL_DIR"

    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local git_commit_escaped
    local git_branch_escaped
    git_commit_escaped=$(xml_escape "$(git_commit)")
    git_branch_escaped=$(xml_escape "$(git_branch)")

    cat > "$out" << YAWLXML
<?xml version="1.0" encoding="UTF-8"?>
<!--
  YAWL Specification: Build and Test Workflow
  Generated: ${timestamp}
  Commit: ${git_commit_escaped}
  Branch: ${git_branch_escaped}

  This YAWL net models the Maven build lifecycle as a workflow with:
  - Sequential tasks (Validate -> Compile -> UnitTests)
  - Parallel AND-split for quality gates
  - AND-join synchronization before integration tests
-->
<specification version="4.0"
               xmlns="http://www.yawlfoundation.org/yawlschema"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                                   http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd"
               uri="http://yawlfoundation.org/yawl/v6/build-and-test">

  <metaData>
    <title>YAWL V6 Build and Test Workflow</title>
    <description>Maven lifecycle modeled as YAWL workflow net</description>
    <creator>YAWL V6 Observatory</creator>
    <created>${timestamp}</created>
  </metaData>

  <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="buildConfig" type="xs:string"/>
    <xs:element name="testResults" type="xs:string"/>
  </xs:schema>

  <decomposition id="BuildAndTest" isRootNet="true">
    <processControlElements>
      <inputCondition id="start" name="Start">
        <flowsInto>
          <nextElementRef id="validate"/>
        </flowsInto>
      </inputCondition>

      <task id="validate" name="Validate">
        <description>Validate project structure and enforcer rules</description>
        <flowsInto>
          <nextElementRef id="compile"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="compile" name="Compile">
        <description>Compile all source code with Java 21</description>
        <flowsInto>
          <nextElementRef id="unitTests"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="unitTests" name="Unit Tests">
        <description>Run surefire unit tests</description>
        <flowsInto>
          <nextElementRef id="qualityGateSplit"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <condition id="qualityGateSplit" name="Quality Gate Split">
        <flowsInto>
          <nextElementRef id="spotbugs"/>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="pmd"/>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="checkstyle"/>
        </flowsInto>
      </condition>

      <task id="spotbugs" name="SpotBugs Analysis">
        <description>Static analysis for bug detection</description>
        <flowsInto>
          <nextElementRef id="qualityGateJoin"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <task id="pmd" name="PMD Analysis">
        <description>Code quality rule violations</description>
        <flowsInto>
          <nextElementRef id="qualityGateJoin"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <task id="checkstyle" name="Checkstyle Analysis">
        <description>Code style compliance check</description>
        <flowsInto>
          <nextElementRef id="qualityGateJoin"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <condition id="qualityGateJoin" name="Quality Gate Join">
        <flowsInto>
          <nextElementRef id="integrationTests"/>
        </flowsInto>
      </condition>

      <task id="integrationTests" name="Integration Tests">
        <description>Run failsafe integration tests</description>
        <flowsInto>
          <nextElementRef id="package"/>
        </flowsInto>
        <join code="and"/>
        <split code="and"/>
      </task>

      <task id="package" name="Package">
        <description>Package artifacts (JAR/WAR)</description>
        <flowsInto>
          <nextElementRef id="verify"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="verify" name="Verify">
        <description>Run all verification checks</description>
        <flowsInto>
          <nextElementRef id="end"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <outputCondition id="end" name="Build Complete"/>

    </processControlElements>
  </decomposition>

</specification>
YAWLXML

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_build_test_yawl" "$op_elapsed"
    log_ok "YAWL XML written to $out"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_yawl_xml_all() {
    timer_start
    record_memory "yawl_xml_start"
    mkdir -p "$YAWL_DIR"
    emit_build_test_yawl
    YAWL_XML_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "yawl_xml" "$YAWL_XML_ELAPSED"
    log_ok "All YAWL XML emitted in ${YAWL_XML_ELAPSED}ms"
}
