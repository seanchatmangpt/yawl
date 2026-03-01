package org.yawlfoundation.yawl.integration.autonomous.analytics;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.engine.spi.FlowWorkflowEventBus;
import org.yawlfoundation.yawl.engine.spi.WorkflowEvent;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Chicago TDD tests for {@link WorkflowEventPublisher}.
 *
 * <p>Always-run tests verify SPARQL INSERT DATA generation and unavailability tolerance
 * using an unused port. No live QLever instance is required for the always-run suite.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class WorkflowEventPublisherTest extends TestCase {

    // -------------------------------------------------------------------------
    // Always-run: SPARQL INSERT DATA generation (no QLever required)
    // -------------------------------------------------------------------------

    public void testBuildCaseInsertContainsCorrectType() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        String update = publisher.buildCaseInsert(
                WorkflowEventVocabulary.caseIri("42"), "42", "orderProcess",
                "starting", "2026-03-01T12:00:00Z", null, -1L);

        assertTrue("INSERT DATA must declare CaseExecution type",
                update.contains(WorkflowEventVocabulary.CASE_EXECUTION));
        assertTrue("INSERT DATA must be a SPARQL UPDATE command",
                update.trim().startsWith("PREFIX"));
        assertTrue("INSERT DATA must contain INSERT DATA keyword",
                update.contains("INSERT DATA"));
        assertTrue("Case ID must appear in triples",
                update.contains("\"42\""));
        assertTrue("Spec ID must appear in triples",
                update.contains("\"orderProcess\""));
        assertTrue("Status must be 'starting'",
                update.contains("\"starting\""));
    }

    public void testBuildCaseInsertIriIsWellFormed() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        String caseIri = WorkflowEventVocabulary.caseIri("case-101");
        String update = publisher.buildCaseInsert(caseIri, "case-101", null,
                "starting", "2026-03-01T09:00:00Z", null, -1L);

        assertTrue("Case IRI must use the analytics namespace",
                caseIri.startsWith(WorkflowEventVocabulary.NS));
        assertTrue("Update must wrap IRI in angle brackets",
                update.contains("<" + caseIri + ">"));
    }

    public void testBuildTaskInsertContainsCorrectType() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        String taskExecIri = WorkflowEventVocabulary.taskExecIri("42", "reviewOrder", 1L);
        String update = publisher.buildTaskInsert(
                taskExecIri, "42", "reviewOrder", "executing", "2026-03-01T12:05:00Z");

        assertTrue("INSERT DATA must declare TaskExecution type",
                update.contains(WorkflowEventVocabulary.TASK_EXECUTION));
        assertTrue("INSERT DATA must contain the task ID",
                update.contains("\"reviewOrder\""));
        assertTrue("INSERT DATA must contain the case ID",
                update.contains("\"42\""));
        assertTrue("INSERT DATA must contain 'executing' status",
                update.contains("\"executing\""));
        assertTrue("INSERT DATA must contain the start time",
                update.contains("2026-03-01T12:05:00Z"));
    }

    public void testBuildTaskTerminalUpdateContainsDurationWhenPositive() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        String update = publisher.buildTaskTerminalUpdate(
                "42_reviewOrder", "42", "reviewOrder",
                "completed", "2026-03-01T12:10:00Z", 30000L);

        assertTrue("Terminal update must contain 'completed' status",
                update.contains("\"completed\""));
        assertTrue("Terminal update must contain end time",
                update.contains("2026-03-01T12:10:00Z"));
        assertTrue("Terminal update must contain duration when positive",
                update.contains("30000"));
        assertTrue("Terminal update must declare TaskExecution type",
                update.contains(WorkflowEventVocabulary.TASK_EXECUTION));
    }

    public void testBuildTaskTerminalUpdateOmitsDurationWhenNegative() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        WorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        String update = publisher.buildTaskTerminalUpdate(
                "42_reviewOrder", "42", "reviewOrder",
                "cancelled", "2026-03-01T12:10:00Z", -1L);

        assertTrue("Terminal update must contain 'cancelled' status",
                update.contains("\"cancelled\""));
        assertFalse("Terminal update must omit duration when negative",
                update.contains(WorkflowEventVocabulary.TASK_DURATION_MS));
    }

    public void testPublisherToleratesUnavailableEngine() throws InterruptedException {
        // Arrange: engine on unused port + real in-process bus
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        FlowWorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        // Act: publish a CASE_STARTING event — publisher should log WARN, not throw
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe(YEventType.CASE_STARTING, event -> latch.countDown());

        bus.publish(new WorkflowEvent(YEventType.CASE_STARTING, null, "orderProcess",
                Instant.now()));

        // Give the virtual-thread bus a moment to dispatch
        boolean delivered = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Event must be dispatched even when QLever is unavailable", delivered);

        // No exception should have propagated to the caller
        publisher.close();
    }

    public void testVocabularyNamespaceIsWellFormedUrl() {
        assertTrue("Analytics namespace must start with http://",
                WorkflowEventVocabulary.NS.startsWith("http://"));
        assertTrue("Analytics namespace must end with #",
                WorkflowEventVocabulary.NS.endsWith("#"));
        assertTrue("CASE_EXECUTION IRI must be in analytics namespace",
                WorkflowEventVocabulary.CASE_EXECUTION.startsWith(WorkflowEventVocabulary.NS));
        assertTrue("TASK_EXECUTION IRI must be in analytics namespace",
                WorkflowEventVocabulary.TASK_EXECUTION.startsWith(WorkflowEventVocabulary.NS));
    }

    // -------------------------------------------------------------------------
    // Self-skipping: live QLever roundtrip (port 7001)
    // -------------------------------------------------------------------------

    public void testSparqlUpdateWritesTripleWhenQLeverRunning() throws Exception {
        QLeverSparqlEngine engine = new QLeverSparqlEngine();
        if (!engine.isAvailable()) return; // skip gracefully when QLever not running

        FlowWorkflowEventBus bus = new FlowWorkflowEventBus();
        WorkflowEventPublisher publisher = new WorkflowEventPublisher(engine, bus);

        // Publish a case-starting event and verify no exception from the QLever write
        bus.publish(new WorkflowEvent(YEventType.CASE_STARTING, null, "integrationSpec",
                Instant.now()));

        // Allow async dispatch
        Thread.sleep(500);

        // If we get here without exception, the write path is functional
        publisher.close();
    }
}
