package org.yawlfoundation.yawl.integration.a2a.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for YStatelessEngine.
 *
 * Uses an inline minimal YAWL spec (identical structure to
 * test/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml) to avoid
 * cross-package resource path dependencies. All three tests exercise real engine
 * behaviour: no mocks, no stubs.
 */
public class SimpleVerificationTest {

    /**
     * Minimal YAWL spec XML: one atomic task (manual gateway) wired
     * InputCondition -> task1 -> OutputCondition.
     */
    private static final String MINIMAL_SPEC_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0"
                xmlns="http://www.yawlfoundation.org/yawlschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema \
            http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
              <specification uri="SmokeTestSpec">
                <metaData>
                  <title>Smoke Test Spec</title>
                  <creator>SimpleVerificationTest</creator>
                  <description>Minimal spec for smoke testing YStatelessEngine.</description>
                  <version>0.1</version>
                </metaData>
                <decomposition id="SmokeNet" xsi:type="NetFactsType" isRootNet="true">
                  <name>SmokeNet</name>
                  <processControlElements>
                    <inputCondition id="start">
                      <flowsInto>
                        <nextElementRef id="task1"/>
                      </flowsInto>
                    </inputCondition>
                    <task id="task1">
                      <name>task1</name>
                      <flowsInto>
                        <nextElementRef id="end"/>
                      </flowsInto>
                      <join code="xor"/>
                      <split code="and"/>
                      <decomposesTo id="task1Gateway"/>
                    </task>
                    <outputCondition id="end"/>
                  </processControlElements>
                </decomposition>
                <decomposition id="task1Gateway" xsi:type="WebServiceGatewayFactsType">
                  <name>task1Gateway</name>
                  <externalInteraction>manual</externalInteraction>
                </decomposition>
              </specification>
            </specificationSet>
            """;

    @Test
    @DisplayName("engineStartsCleanly: YStatelessEngine constructor succeeds and returns non-null instance")
    void engineStartsCleanly() {
        YStatelessEngine engine = new YStatelessEngine();
        assertNotNull(engine, "YStatelessEngine constructor must return a non-null instance");
    }

    @Test
    @DisplayName("engineCanUnmarshalAndLaunchCase: spec unmarshals and case runner is alive after launch")
    void engineCanUnmarshalAndLaunchCase() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();

        YSpecification spec = engine.unmarshalSpecification(MINIMAL_SPEC_XML);
        assertNotNull(spec, "unmarshalSpecification must return a non-null YSpecification");

        YNetRunner runner = engine.launchCase(spec, "smoke-test");
        assertNotNull(runner, "launchCase must return a non-null YNetRunner");
        assertTrue(runner.isAlive(),
                "Runner must be alive immediately after launchCase (task1 is a manual work item awaiting execution)");
    }

    @Test
    @DisplayName("engineHandlesMultipleCasesIndependently: three cases launched from same spec have distinct case IDs")
    void engineHandlesMultipleCasesIndependently() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();

        YSpecification spec = engine.unmarshalSpecification(MINIMAL_SPEC_XML);
        assertNotNull(spec, "unmarshalSpecification must return a non-null YSpecification");

        YNetRunner runner1 = engine.launchCase(spec);
        YNetRunner runner2 = engine.launchCase(spec);
        YNetRunner runner3 = engine.launchCase(spec);

        assertNotNull(runner1, "First runner must not be null");
        assertNotNull(runner2, "Second runner must not be null");
        assertNotNull(runner3, "Third runner must not be null");

        String id1 = runner1.getCaseID().toString();
        String id2 = runner2.getCaseID().toString();
        String id3 = runner3.getCaseID().toString();

        Set<String> distinctIds = new HashSet<>(Set.of(id1, id2, id3));
        assertEquals(3, distinctIds.size(),
                "Three independently launched cases must have three distinct case IDs; got: "
                        + id1 + ", " + id2 + ", " + id3);
    }
}
