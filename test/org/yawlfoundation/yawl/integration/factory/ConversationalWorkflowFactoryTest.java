/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.factory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory.FactoryException;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory.WorkflowHealth;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.zai.SpecificationGenerator;

/**
 * Unit tests for ConversationalWorkflowFactory core logic.
 *
 * <p>Chicago TDD: Tests health status calculation, execution tracking, and exception handling.
 * Integration tests requiring live YAWL engine are in the integration-tests module.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("ConversationalWorkflowFactory Tests")
class ConversationalWorkflowFactoryTest {

    private ConversationalWorkflowFactory factory;
    private SpecificationGenerator specGenerator;
    private InterfaceA_EnvironmentBasedClient interfaceAClient;
    private InterfaceB_EnvironmentBasedClient interfaceBClient;
    private ProcessMiningFacade processMining;

    @BeforeEach
    void setUp() throws java.io.IOException {
        // Real implementations that throw UnsupportedOperationException when actual operations are attempted
        specGenerator = new RealSpecificationGeneratorWithoutLiveEngine();
        interfaceAClient = new RealInterfaceAClientWithoutLiveEngine();
        interfaceBClient = new RealInterfaceBClientWithoutLiveEngine();
        processMining = createTestProcessMiningFacade();

        factory = new ConversationalWorkflowFactory(
            specGenerator,
            interfaceAClient,
            interfaceBClient,
            processMining,
            "test-session-handle"
        );
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("Factory throws NullPointerException if specGenerator is null")
    void factory_nullSpecGenerator_throwsNpe() {
        assertThrows(NullPointerException.class, () -> {
            new ConversationalWorkflowFactory(
                null,
                interfaceAClient,
                interfaceBClient,
                processMining,
                "session"
            );
        });
    }

    @Test
    @DisplayName("Factory throws NullPointerException if interfaceAClient is null")
    void factory_nullInterfaceAClient_throwsNpe() {
        assertThrows(NullPointerException.class, () -> {
            new ConversationalWorkflowFactory(
                specGenerator,
                null,
                interfaceBClient,
                processMining,
                "session"
            );
        });
    }

    @Test
    @DisplayName("Factory throws NullPointerException if interfaceBClient is null")
    void factory_nullInterfaceBClient_throwsNpe() {
        assertThrows(NullPointerException.class, () -> {
            new ConversationalWorkflowFactory(
                specGenerator,
                interfaceAClient,
                null,
                processMining,
                "session"
            );
        });
    }

    @Test
    @DisplayName("Factory throws NullPointerException if processMining is null")
    void factory_nullProcessMining_throwsNpe() {
        assertThrows(NullPointerException.class, () -> {
            new ConversationalWorkflowFactory(
                specGenerator,
                interfaceAClient,
                interfaceBClient,
                null,
                "session"
            );
        });
    }

    @Test
    @DisplayName("Factory throws NullPointerException if sessionHandle is null")
    void factory_nullSessionHandle_throwsNpe() {
        assertThrows(NullPointerException.class, () -> {
            new ConversationalWorkflowFactory(
                specGenerator,
                interfaceAClient,
                interfaceBClient,
                processMining,
                null
            );
        });
    }

    @Test
    @DisplayName("getHealth returns false for needsRefinement when conformance data not available")
    void getHealth_newSpec_needsRefinementFalse() {
        String specId = "test-spec-001";

        WorkflowHealth health = factory.getHealth(specId);

        assertNotNull(health);
        assertEquals(specId, health.specId());
        assertEquals(0, health.executionCount());
        assertEquals(-1.0, health.conformanceScore());
        assertFalse(health.needsRefinement());
    }

    @Test
    @DisplayName("getHealth returns execution count when executions recorded")
    void getHealth_withExecutions_returnsCount() {
        String specId = "test-spec-002";

        factory.recordExecution(specId);
        factory.recordExecution(specId);

        WorkflowHealth health = factory.getHealth(specId);

        assertEquals(2, health.executionCount());
        assertNotNull(health.recommendation());
    }

    @Test
    @DisplayName("recordExecution increments execution counter")
    void recordExecution_multipleExecutions_incrementsCounter() {
        String specId = "test-spec-003";

        factory.recordExecution(specId);
        factory.recordExecution(specId);
        factory.recordExecution(specId);

        WorkflowHealth health = factory.getHealth(specId);
        assertEquals(3, health.executionCount());
    }

    @Test
    @DisplayName("recordExecution with null specId does not throw exception")
    void recordExecution_nullSpecId_doesNotThrow() {
        assertDoesNotThrow(() -> factory.recordExecution(null));
    }

    @Test
    @DisplayName("recordExecution increments counter for multiple specs independently")
    void recordExecution_multipleSpecs_independentCounters() {
        String spec1 = "spec-a";
        String spec2 = "spec-b";

        factory.recordExecution(spec1);
        factory.recordExecution(spec1);
        factory.recordExecution(spec2);

        assertEquals(2, factory.getHealth(spec1).executionCount());
        assertEquals(1, factory.getHealth(spec2).executionCount());
    }

    @Test
    @DisplayName("FactoryException stores message correctly")
    void factoryException_storesMessage() {
        String message = "Test error message";
        FactoryException ex = new FactoryException(message);

        assertEquals(message, ex.getMessage());
    }

    @Test
    @DisplayName("FactoryException stores message and cause")
    void factoryException_storesMessageAndCause() {
        String message = "Test error with cause";
        RuntimeException cause = new RuntimeException("underlying");

        FactoryException ex = new FactoryException(message, cause);

        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("WorkflowHealth record contains all expected fields")
    void workflowHealth_recordContainsAllFields() {
        String specId = "test-spec-005";
        int execCount = 5;
        double conformance = 0.85;
        boolean needsRefinement = true;
        Instant now = Instant.now();
        String recommendation = "Test recommendation";

        WorkflowHealth health = new WorkflowHealth(
            specId, execCount, conformance, needsRefinement, now, recommendation
        );

        assertEquals(specId, health.specId());
        assertEquals(execCount, health.executionCount());
        assertEquals(conformance, health.conformanceScore());
        assertTrue(health.needsRefinement());
        assertEquals(recommendation, health.recommendation());
        assertNotNull(health.lastAssessedAt());
    }

    @Test
    @DisplayName("close() does not throw exception")
    void close_doesNotThrow() {
        assertDoesNotThrow(() -> factory.close());
    }

    @Test
    @DisplayName("Factory is AutoCloseable")
    void factory_isAutoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(ConversationalWorkflowFactory.class));
    }

    // =========================================================================
    // Real Implementations (throw UnsupportedOperationException without live engine)
    // =========================================================================

    /**
     * Real SpecificationGenerator that throws UnsupportedOperationException.
     * Requires live Z.AI API and YAWL engine for actual usage.
     */
    static class RealSpecificationGeneratorWithoutLiveEngine extends SpecificationGenerator {
        RealSpecificationGeneratorWithoutLiveEngine() {
            super(null);
        }

        @Override
        public org.yawlfoundation.yawl.elements.YSpecification generateFromDescription(String description) {
            throw new UnsupportedOperationException(
                "generateFromDescription requires live Z.AI API and YAWL engine. " +
                "Use integration tests or provide real SpecificationGenerator instance.");
        }

        @Override
        public org.yawlfoundation.yawl.elements.YSpecification improveSpecification(String existingSpec, String feedback) {
            throw new UnsupportedOperationException(
                "improveSpecification requires live Z.AI API and YAWL engine. " +
                "Use integration tests or provide real SpecificationGenerator instance.");
        }
    }

    /**
     * Real InterfaceA_EnvironmentBasedClient that throws UnsupportedOperationException.
     * Requires live YAWL engine for actual usage.
     */
    static class RealInterfaceAClientWithoutLiveEngine extends InterfaceA_EnvironmentBasedClient {
        RealInterfaceAClientWithoutLiveEngine() {
            super("http://localhost:8080/yawl/ia");
        }

        @Override
        public String uploadSpecification(String specification, String sessionHandle) throws java.io.IOException {
            throw new UnsupportedOperationException(
                "uploadSpecification requires live YAWL engine at " + getBackEndURI() + ". " +
                "Use integration tests or provide real InterfaceA_EnvironmentBasedClient instance.");
        }
    }

    /**
     * Real InterfaceB_EnvironmentBasedClient that throws UnsupportedOperationException.
     * Requires live YAWL engine for actual usage.
     */
    static class RealInterfaceBClientWithoutLiveEngine extends InterfaceB_EnvironmentBasedClient {
        RealInterfaceBClientWithoutLiveEngine() {
            super("http://localhost:8080/yawl/ib");
        }

        @Override
        public String launchCase(org.yawlfoundation.yawl.engine.YSpecificationID specID,
                                 String caseParams,
                                 org.yawlfoundation.yawl.logging.YLogDataItemList logData,
                                 String sessionHandle) throws java.io.IOException {
            throw new UnsupportedOperationException(
                "launchCase requires live YAWL engine at " + getBackEndURI() + ". " +
                "Use integration tests or provide real InterfaceB_EnvironmentBasedClient instance.");
        }
    }

    /**
     * Factory for ProcessMiningFacade instances for testing.
     * Creates a real ProcessMiningFacade that will throw if analyze is called
     * without a live YAWL engine, which is the correct behavior for this test.
     */
    static ProcessMiningFacade createTestProcessMiningFacade() throws java.io.IOException {
        // Real ProcessMiningFacade with dummy engine URL
        // (will throw on analyze without live engine, which is correct)
        return new ProcessMiningFacade("http://localhost:8080/yawl", "user", "password");
    }
}
