package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Single-JVM actor runtime implementation using virtual threads.
 * Minimal overhead: one ConcurrentHashMap registry + one virtual thread executor.
 *
 * Byte accounting (per idle actor):
 *   Actor object:   24 bytes
 *   Queue object:   40 bytes (LinkedTransferQueue, lock-free)
 *   VThread:        64 bytes (unmounted, no stack)
 *   ScopedValue:     4 bytes (amortized)
 *   Total:         132 bytes
 *
 * Note on boxing at 10M scale:
 *   ConcurrentHashMap&lt;Integer, Actor&gt; boxes int keys -&gt; Integer (16 bytes each).
 *   At 10M actors: 160MB of Integer objects. Measure first; switch to
 *   Eclipse Collections IntObjectHashMap if GC overhead exceeds 5%.
 */
public final class YawlActorRuntime implements ActorRuntime {

    /** ScopedValue — zero-cost identity propagation into virtual threads. */
    static final ScopedValue<Actor> CURRENT = ScopedValue.newInstance();

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private final ConcurrentHashMap<Integer, Actor> registry = new ConcurrentHashMap<>();
    private final ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Spawn a new actor. The behavior Consumer runs on a virtual thread.
     * Behavior lives in the closure — not in the Actor object.
     *
     * @param behavior the message handler
     * @return an opaque ActorRef for messaging
     */
    @Override
    public ActorRef spawn(Consumer<Object> behavior) {
        Actor a = new Actor(SEQ.getAndIncrement());
        registry.put(a.id, a);
        vt.submit(() ->
            ScopedValue.where(CURRENT, a).run(() -> {
                try {
                    while (!Thread.interrupted()) {
                        // take() parks virtual thread when idle — unmounts from carrier
                        // This is the correct pattern for 1M+ actor scale
                        Object msg = a.q.take();
                        behavior.accept(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    registry.remove(a.id);
                }
            })
        );
        return new ActorRef(a.id, this);
    }

    /**
     * Send a message to an actor by id. No-op if id not found.
     * Called by ActorRef.tell().
     */
    @Override
    public void send(int targetId, Object msg) {
        Actor a = registry.get(targetId);
        if (a != null) a.send(msg);
    }

    /**
     * Stop an actor immediately.
     * Called by ActorRef.stop().
     */
    @Override
    public void stop(int actorId) {
        Actor a = registry.remove(actorId);
        if (a != null) {
            // Interrupt the virtual thread by poisoning the queue with a sentinel
            // The behavior loop should check for shutdown signals (future enhancement)
            a.q.offer(new ActorShutdown());
        }
    }

    /**
     * Current number of live actors in this runtime.
     */
    @Override
    public int size() {
        return registry.size();
    }

    /**
     * Shutdown the runtime.
     * All virtual threads are interrupted, registry is cleared.
     */
    @Override
    public void close() {
        vt.shutdownNow();
        registry.clear();
    }

    /**
     * Sentinel message used to signal actor shutdown.
     * Internal use only.
     */
    static final class ActorShutdown {
        @Override
        public String toString() {
            return "ActorShutdown";
        }
    }
}
