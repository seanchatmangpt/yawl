/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.stateless;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ScopedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.autonomous.AgentContext;
import org.yawlfoundation.yawl.stateless.engine.WorkflowContext;
import org.yawlfoundation.yawl.stateless.engine.YEngine;

/**
 * Test suite validating Java 25 ScopedValue propagation across virtual threads.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("ScopedValue Propagation Tests (Java 25)")
@Tag("unit")
@Tag("java25")
class TestScopedValuePropagation {

    @Test
    @DisplayName("WorkflowContext binding in current thread")
    void testWorkflowContextBindingInCurrentThread() {
        String caseID = "case-001";
        String specID = "spec:1.0";
        int engineNbr = 1;

        WorkflowContext ctx = WorkflowContext.of(caseID, specID, engineNbr);

        try {
            ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
                WorkflowContext bound = YEngine.WORKFLOW_CONTEXT.get();
                assertEquals(caseID, bound.caseID());
                assertEquals(specID, bound.specID());
                assertEquals(engineNbr, bound.engineNbr());
                return null;
            });
        } catch (Exception e) {
            fail("WorkflowContext binding failed", e);
        }
    }

    @Test
    @DisplayName("WorkflowContext propagates to spawned virtual threads")
    void testWorkflowContextPropagationToVirtualThreads() throws Exception {
        String caseID = "case-virtual-001";
        String specID = "spec:2.0";
        int engineNbr = 2;

        WorkflowContext ctx = WorkflowContext.of(caseID, specID, engineNbr);
        AtomicReference<WorkflowContext> contextInThread = new AtomicReference<>();
        CountDownLatch threadLatch = new CountDownLatch(1);

        try {
            ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
                Thread vt = Thread.ofVirtual()
                        .name("test-case-" + caseID)
                        .start(() -> {
                            try {
                                WorkflowContext inherited = YEngine.WORKFLOW_CONTEXT.get();
                                contextInThread.set(inherited);
                            } finally {
                                threadLatch.countDown();
                            }
                        });

                assertTrue(threadLatch.await(5, TimeUnit.SECONDS));
                vt.join();
                return null;
            });
        } catch (Exception e) {
            fail("ScopedValue propagation failed", e);
        }

        assertNotNull(contextInThread.get());
        assertEquals(caseID, contextInThread.get().caseID());
    }

    @Test
    @DisplayName("WorkflowContext propagates through StructuredTaskScope")
    void testWorkflowContextPropagationThroughStructuredTaskScope() throws Exception {
        String caseID = "case-scope-001";

        WorkflowContext ctx = WorkflowContext.of(caseID, "spec:3.0", 3);
        List<WorkflowContext> collectedContexts = Collections.synchronizedList(new ArrayList<>());

        try {
            ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
                try (StructuredTaskScope.ShutdownOnFailure scope =
                        new StructuredTaskScope.ShutdownOnFailure("test-scope", Thread.ofVirtual().factory())) {

                    for (int i = 0; i < 3; i++) {
                        scope.fork(() -> {
                            WorkflowContext inherited = YEngine.WORKFLOW_CONTEXT.get();
                            collectedContexts.add(inherited);
                            return null;
                        });
                    }

                    scope.join().throwIfFailed();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        } catch (Exception e) {
            fail("StructuredTaskScope propagation failed", e);
        }

        assertEquals(3, collectedContexts.size());
    }

    @Test
    @DisplayName("AgentContext binding in current thread")
    void testAgentContextBindingInCurrentThread() {
        AgentContext ctx = new AgentContext("agent-1", "Test Agent", "http://localhost:8080/yawl", "session-1");

        try {
            ScopedValue.callWhere(AgentContext.CURRENT, ctx, () -> {
                AgentContext bound = AgentContext.CURRENT.get();
                assertEquals("agent-1", bound.agentId());
                return null;
            });
        } catch (Exception e) {
            fail("AgentContext binding failed", e);
        }
    }

    @Test
    @DisplayName("AgentContext propagates to spawned virtual threads")
    void testAgentContextPropagationToVirtualThreads() throws Exception {
        AgentContext ctx = new AgentContext("agent-2", "Test Agent", "http://localhost:8080/yawl", "session-2");
        AtomicReference<AgentContext> contextInThread = new AtomicReference<>();
        CountDownLatch threadLatch = new CountDownLatch(1);

        try {
            ScopedValue.callWhere(AgentContext.CURRENT, ctx, () -> {
                Thread vt = Thread.ofVirtual()
                        .start(() -> {
                            try {
                                AgentContext inherited = AgentContext.CURRENT.get();
                                contextInThread.set(inherited);
                            } finally {
                                threadLatch.countDown();
                            }
                        });

                assertTrue(threadLatch.await(5, TimeUnit.SECONDS));
                vt.join();
                return null;
            });
        } catch (Exception e) {
            fail("AgentContext propagation failed", e);
        }

        assertNotNull(contextInThread.get());
        assertEquals("agent-2", contextInThread.get().agentId());
    }

    @Test
    @DisplayName("Multiple ScopedValue bindings are isolated")
    void testMultipleScopedValueBindingsAreIsolated() throws Exception {
        WorkflowContext ctx1 = WorkflowContext.of("case-001", "spec:1.0", 1);
        WorkflowContext ctx2 = WorkflowContext.of("case-002", "spec:2.0", 2);

        AtomicReference<String> case1 = new AtomicReference<>();
        AtomicReference<String> case2 = new AtomicReference<>();

        try {
            ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx1, () -> {
                case1.set(YEngine.WORKFLOW_CONTEXT.get().caseID());
                return null;
            });

            ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx2, () -> {
                case2.set(YEngine.WORKFLOW_CONTEXT.get().caseID());
                return null;
            });
        } catch (Exception e) {
            fail("ScopedValue isolation test failed", e);
        }

        assertEquals("case-001", case1.get());
        assertEquals("case-002", case2.get());
    }

    @Test
    @DisplayName("Unbound ScopedValue throws exception outside scope")
    void testUnboundScopedValueThrowsException() {
        assertThrows(NoSuchElementException.class, () -> {
            YEngine.WORKFLOW_CONTEXT.get();
        });
    }
}
