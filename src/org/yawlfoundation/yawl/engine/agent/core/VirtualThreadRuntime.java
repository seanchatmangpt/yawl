package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level actor runtime implementing ActorRuntime.
 *
 * Behavior receives ActorRef (self-reference for tell/recv/ask).
 * Uses virtual threads (one per actor behavior invocation).
 *
 * Named VirtualThreadRuntime so chaos tests can load it by reflection:
 *   Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime")
 *
 * Thread-safe. All operations are lock-free where possible.
 *
 * Usage:
 *   ActorRuntime runtime = new VirtualThreadRuntime();
 *   ActorRef actor = runtime.spawn(self -> {
 *       Object msg = self.recv();
 *       // handle msg...
 *   });
 */
public final class VirtualThreadRuntime implements ActorRuntime {

    private final AtomicInteger nextId = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Agent> registry = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Spawn a new actor. Behavior receives its own ActorRef for self-reference.
     * The behavior runs on a virtual thread; when behavior returns, actor is removed.
     */
    @Override
    public ActorRef spawn(ActorBehavior behavior) {
        int id = nextId.getAndIncrement();
        Agent a = new Agent(id);
        registry.put(id, a);
        ActorRef ref = new ActorRef(id, this);

        executor.submit(() -> {
            a.thread = Thread.currentThread();
            try {
                behavior.run(ref);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupt status
            } catch (RuntimeException e) {
                // Behavior threw uncaught exception (natural or via injectException);
                // recv() translated ExceptionTrigger sentinel into the injected cause.
                // Actor exits; supervisor will handle restart if registered.
                System.err.printf("[VirtualThreadRuntime] Actor %d threw: %s%n",
                    id, e.getMessage());
            } finally {
                registry.remove(id);
            }
        });

        return ref;
    }

    /**
     * Send a message to an actor by id. No-op if id not found.
     */
    @Override
    public void send(int targetId, Object msg) {
        Agent a = registry.get(targetId);
        if (a != null) a.send(msg);
    }

    /**
     * Stop an actor: remove from registry and interrupt its virtual thread.
     */
    @Override
    public void stop(int actorId) {
        Agent a = registry.remove(actorId);
        if (a != null && a.thread != null) {
            a.thread.interrupt();
        }
    }

    /**
     * Check if an actor is alive (in the registry).
     */
    @Override
    public boolean isAlive(int actorId) {
        return registry.containsKey(actorId);
    }

    /**
     * Block until a message arrives in the specified actor's mailbox.
     * Called via ActorRef.recv() from within the actor's own virtual thread.
     *
     * If the message is an ExceptionTrigger, throws its cause RuntimeException.
     * If the thread is interrupted, throws InterruptedException.
     */
    @Override
    public Object recv(int actorId) throws InterruptedException {
        Agent a = registry.get(actorId);
        if (a == null) {
            throw new IllegalStateException("Actor " + actorId + " not found in registry");
        }
        Object msg = a.q.take(); // parks virtual thread until message arrives
        if (msg instanceof ExceptionTrigger t) {
            throw t.cause();
        }
        return msg;
    }

    /**
     * Inject an exception into an actor's behavior.
     * Puts ExceptionTrigger in mailbox AND interrupts the virtual thread.
     */
    @Override
    public void injectException(int actorId, RuntimeException cause) {
        Agent a = registry.get(actorId);
        if (a != null) {
            a.q.offer(new ExceptionTrigger(cause));
            if (a.thread != null) {
                a.thread.interrupt();
            }
        }
    }

    /**
     * Current number of live actors.
     */
    @Override
    public int size() {
        return registry.size();
    }

    /**
     * Shutdown: interrupt all virtual threads, clear registry.
     */
    @Override
    public void close() {
        executor.shutdownNow();
        registry.clear();
    }
}
