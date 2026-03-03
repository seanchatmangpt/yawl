package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.CompletableFuture;
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
            // Two-phase cancellation: write a.thread, then read a.stopped.
            // stop() writes a.stopped, then reads a.thread.
            // Volatile memory ordering guarantees at least one of them sees
            // the other's write, eliminating the "null thread → missed interrupt" race:
            //   Case A: stop() completes first → a.stopped=true; task checks → exits.
            //   Case B: task sets a.thread first → stop() reads it → interrupts.
            a.thread = Thread.currentThread();
            if (a.stopped) {
                // stop() already ran but saw a.thread==null and couldn't interrupt;
                // honour the stop now by exiting before any blocking behavior starts.
                return;
            }
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
     * Spawn a new actor asynchronously, returning a CompletableFuture.
     * The behavior runs on a virtual thread; when behavior returns, actor is removed.
     * The future completes when the actor is spawned (not when the behavior completes).
     * This is a non-blocking operation that returns immediately after scheduling the spawn.
     *
     * @param behavior the actor behavior
     * @return CompletableFuture that completes with the ActorRef when spawned
     */
    public CompletableFuture<ActorRef> spawnAsync(ActorBehavior behavior) {
        return CompletableFuture.supplyAsync(() -> spawn(behavior), executor);
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
     *
     * Two-phase cancellation protocol (pairs with spawn task):
     *   1. Remove from registry (prevents new messages; recv() will throw ISE).
     *   2. Set a.stopped=true (volatile write) — task checks this after setting a.thread.
     *   3. Read a.thread (volatile read) — may be null if task hasn't started yet.
     *   4. Interrupt if non-null.
     * The volatile ordering ensures: if the task starts after step 2 and sees
     * stopped=true, it exits. If the task set a.thread before step 3, we interrupt it.
     */
    @Override
    public void stop(int actorId) {
        Agent a = registry.remove(actorId);
        if (a != null) {
            a.stopped = true;          // volatile write: happens-before task's volatile read
            Thread t = a.thread;       // volatile read: sees task's volatile write if it ran first
            if (t != null) t.interrupt();
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
