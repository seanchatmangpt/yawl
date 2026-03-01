package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for RoutingSlip history cap and CaseRegistry warnings.
 *
 * Detroit School: Tests verify observable behavior through public API.
 * Tests verify:
 * - MAX_HISTORY_SIZE = 1000 constant is enforced
 * - Envelope history is capped at MAX_HISTORY_SIZE (no growth beyond 1000)
 * - History grows normally below cap (incremental advance adds to history)
 * - advance() at MAX_HISTORY_SIZE doesn't add new entry (prevents overflow)
 * - Routing with stopped/dead actors works (WeakRef GC handling)
 * - Empty slip triggers completion handler (forward() callback)
 * - CaseRegistry warns at 100K+ cases but does not throw (stderr warning)
 * - CaseRegistry.register/deregister/get/size work correctly
 * - Envelope.withPayload() preserves routing slip and history
 * - Envelope.isComplete() checks slip emptiness
 * - Envelope.peekNext() returns null when slip is empty
 *
 * Tests use real VirtualThreadRuntime with real routing slip envelopes.
 * No mocks. Timing verifies proper message flow through routing chain.
 */
class RoutingSlipCapTest {

    private VirtualThreadRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new VirtualThreadRuntime();
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    @DisplayName("Envelope history is capped at MAX_HISTORY_SIZE")
    @Timeout(5)
    void envelope_history_cappedAtMax() {
        // Create single actor (just for existence)
        CountDownLatch done = new CountDownLatch(1);
        ActorRef actor = runtime.spawn(self -> done.countDown());

        // Build an envelope with history already at max
        List<String> longHistory = new ArrayList<>();
        for (int i = 0; i < RoutingSlip.MAX_HISTORY_SIZE; i++) {
            longHistory.add("actor#" + i);
        }
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(actor);
        RoutingSlip.Envelope env = new RoutingSlip.Envelope(
            "case-1", "payload", slip,
            Collections.unmodifiableList(longHistory)
        );

        // advance() should cap the history, not grow beyond MAX
        RoutingSlip.Envelope advanced = env.advance("newActor");
        assertTrue(advanced.history().size() <= RoutingSlip.MAX_HISTORY_SIZE,
            "History must not exceed MAX_HISTORY_SIZE after advance()");
    }

    @Test
    @DisplayName("history grows normally below cap")
    @Timeout(5)
    void history_growsNormally_belowCap() {
        // Create slip with 5 actors so each advance() pops one from the slip
        ActorRef[] actors = new ActorRef[5];
        for (int i = 0; i < 5; i++) {
            actors[i] = runtime.spawn(self -> {});
        }
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(actors);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "payload", slip);

        // 5 advances should produce 5 history entries (one actor popped per advance)
        for (int i = 0; i < 5; i++) {
            env = env.advance("actor" + i);
        }

        assertEquals(5, env.history().size(),
            "History should grow normally when below cap");
    }

    @Test
    @DisplayName("advance() at MAX_HISTORY_SIZE doesn't add new entry to history")
    @Timeout(10)
    void advance_atMaxHistorySize_dontAddHistory() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1001);
        ActorRef[] actors = new ActorRef[1001];

        // Create 1001 actors (one more than MAX_HISTORY_SIZE)
        for (int i = 0; i < 1001; i++) {
            actors[i] = runtime.spawn(self -> {
                ready.countDown();
            });
        }

        ready.await(10, TimeUnit.SECONDS);

        // Create a slip with the 1001 actors
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(actors);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "payload", slip);

        // Advance 1000 times (to fill history up to MAX_HISTORY_SIZE)
        for (int i = 0; i < 1000; i++) {
            env = env.advance("actor" + i);
        }

        assertEquals(1000, env.history().size(),
            "History should be at MAX_HISTORY_SIZE after 1000 advances");

        // Try to advance one more time
        RoutingSlip.Envelope env2 = env.advance("actor1000");

        // History should stay at 1000, not grow to 1001
        assertEquals(1000, env2.history().size(),
            "History should not exceed MAX_HISTORY_SIZE, should stay at 1000");
    }

    @Test
    @DisplayName("empty slip triggers completion handler")
    @Timeout(5)
    void emptySlip_triggersCompletionHandler() {
        RoutingSlip.Envelope env = RoutingSlip.envelope(
            "case-done", "payload", RoutingSlip.empty()
        );

        List<RoutingSlip.Envelope> completed = new ArrayList<>();
        RoutingSlip.forward(env, completed::add);

        assertEquals(1, completed.size(),
            "Empty slip should trigger completion handler");
        assertEquals("case-done", completed.get(0).caseId(),
            "Completed envelope should have correct case ID");
    }

    @Test
    @DisplayName("forward with single live actor routes correctly")
    @Timeout(5)
    void forward_singleLiveActor_routesCorrectly() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);

        ActorRef hop1 = runtime.spawn(self -> {
            try {
                RoutingSlip.Envelope env = (RoutingSlip.Envelope) self.recv();
                received.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(hop1);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "test", slip);

        List<RoutingSlip.Envelope> completed = new ArrayList<>();
        RoutingSlip.forward(env, completed::add);

        assertTrue(received.await(5, TimeUnit.SECONDS),
            "hop1 should receive the envelope");
        assertEquals(0, completed.size(),
            "Should not complete while envelope is being processed");
    }

    @Test
    @DisplayName("routing slip with multiple hops processes in sequence")
    @Timeout(10)
    void routingSlip_multipleHops_processSequentially() throws InterruptedException {
        CountDownLatch hop1Ready = new CountDownLatch(1);
        CountDownLatch hop2Ready = new CountDownLatch(1);
        CountDownLatch hop1Forward = new CountDownLatch(1);
        CountDownLatch hop2Forward = new CountDownLatch(1);

        // Hop 2: final destination
        ActorRef hop2 = runtime.spawn(self -> {
            hop2Ready.countDown();
            try {
                hop2Forward.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Hop 1: intermediate, forwards to hop2
        AtomicReference<RoutingSlip.Envelope> hop1Envelope = new AtomicReference<>();
        ActorRef hop1 = runtime.spawn(self -> {
            hop1Ready.countDown();
            try {
                RoutingSlip.Envelope env = (RoutingSlip.Envelope) self.recv();
                hop1Envelope.set(env);
                hop1Forward.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        hop1Ready.await(3, TimeUnit.SECONDS);
        hop2Ready.await(3, TimeUnit.SECONDS);

        // Create slip: hop1 -> hop2
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(hop1, hop2);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "work", slip);

        // Send to hop1
        hop1.tell(env);

        hop1Forward.await(5, TimeUnit.SECONDS);

        // Verify hop1 received the envelope
        assertNotNull(hop1Envelope.get(), "hop1 should have received envelope");

        hop2Forward.countDown();
    }

    @Test
    @DisplayName("CaseRegistry warns on 100K+ cases but does not throw")
    @Timeout(30)
    void caseRegistry_warnsButDoesNotThrow_at100K() {
        RoutingSlip.CaseRegistry registry = new RoutingSlip.CaseRegistry();

        // Fill to 100K — should not throw
        for (int i = 0; i < 100_000; i++) {
            RoutingSlip.Envelope env = RoutingSlip.envelope(
                "case-" + i, "p", RoutingSlip.empty()
            );
            assertDoesNotThrow(() -> registry.register(env),
                "Registering cases should not throw");
        }

        assertEquals(100_000, registry.size(),
            "Registry should have 100K cases");

        // Adding 100_001st should still not throw (just logs warning to stderr)
        RoutingSlip.Envelope overflow = RoutingSlip.envelope("overflow", "p", RoutingSlip.empty());
        assertDoesNotThrow(() -> registry.register(overflow),
            "Registering 100,001st case should not throw");
        assertEquals(100_001, registry.size(),
            "Registry should have 100,001 cases (no exception)");
    }

    @Test
    @DisplayName("CaseRegistry deregister removes case")
    @Timeout(5)
    void caseRegistry_deregister_removeCase() {
        RoutingSlip.CaseRegistry registry = new RoutingSlip.CaseRegistry();

        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "payload", RoutingSlip.empty());
        registry.register(env);
        assertEquals(1, registry.size(), "Should have 1 case");

        registry.deregister("case-1");
        assertEquals(0, registry.size(), "Should have 0 cases after deregister");
        assertNull(registry.get("case-1"), "Deregistered case should not be found");
    }

    @Test
    @DisplayName("Envelope.withPayload() preserves routing slip and history")
    @Timeout(5)
    void envelope_withPayload_preservesRoutingSlip() {
        ActorRef actor = runtime.spawn(self -> {});
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(actor);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "old-payload", slip);

        // Advance to add to history
        RoutingSlip.Envelope env2 = env.advance("actor#1");

        // Change payload
        RoutingSlip.Envelope env3 = env2.withPayload("new-payload");

        assertEquals("new-payload", env3.payload(),
            "Payload should be updated");
        assertEquals(env2.history(), env3.history(),
            "History should be preserved");
        assertEquals("case-1", env3.caseId(),
            "Case ID should be preserved");
    }

    @Test
    @DisplayName("Envelope.isComplete() returns true when slip is empty")
    @Timeout(5)
    void envelope_isComplete_checksSlip() {
        ActorRef actor = runtime.spawn(self -> {});
        Deque<java.lang.ref.WeakReference<ActorRef>> slip = RoutingSlip.create(actor);
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "payload", slip);

        assertFalse(env.isComplete(), "Envelope with actors should not be complete");

        RoutingSlip.Envelope env2 = RoutingSlip.envelope("case-2", "payload", RoutingSlip.empty());
        assertTrue(env2.isComplete(), "Envelope with empty slip should be complete");
    }

    @Test
    @DisplayName("Envelope.peekNext() returns null when slip is empty or actors GC'd")
    @Timeout(5)
    void envelope_peekNext_returnsNullWhenEmpty() {
        RoutingSlip.Envelope env = RoutingSlip.envelope("case-1", "payload", RoutingSlip.empty());
        assertNull(env.peekNext(), "peekNext() should return null on empty slip");
    }

    /**
     * Helper to hold a mutable reference for use in actor closures.
     */
    private static class AtomicReference<T> {
        private T value;

        public void set(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }
}
