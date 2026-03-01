package org.yawlfoundation.yawl.engine.agent.core;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Minimal agent runtime — AtomicInteger IDs + ConcurrentHashMap registry
 * + one virtual thread executor per task.
 *
 * Note on boxing at 10M scale:
 *   ConcurrentHashMap<Integer, Agent> boxes int keys -> Integer (16 bytes each).
 *   At 10M agents: 160MB of Integer objects. Measure first; switch to
 *   Eclipse Collections IntObjectHashMap if GC overhead exceeds 5%.
 */
final class Runtime implements Closeable {

    /** ScopedValue — zero-cost identity propagation into virtual threads. */
    static final ScopedValue<Actor> CURRENT = ScopedValue.newInstance();

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private final ConcurrentHashMap<Integer, Actor> registry = new ConcurrentHashMap<>();
    private final ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Spawn an agent. The behavior Consumer runs on a virtual thread.
     * Behavior lives in the closure — not in the Actor object.
     */
    Actor spawn(Consumer<Object> behavior) {
        Actor a = new Actor(SEQ.getAndIncrement());
        registry.put(a.id, a);
        vt.submit(() ->
            ScopedValue.where(CURRENT, a).run(() -> {
                try {
                    while (!Thread.interrupted()) {
                        // take() parks virtual thread when idle — unmounts from carrier
                        // This is the correct pattern for 1M+ agent scale
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
        return a;
    }

    /** Send a message to agent by id. No-op if id not found. */
    void send(int targetId, Object msg) {
        Actor a = registry.get(targetId);
        if (a != null) a.send(msg);
    }

    /** Current number of live agents. */
    int size() {
        return registry.size();
    }

    @Override
    public void close() {
        vt.shutdownNow();
        registry.clear();
    }
}
