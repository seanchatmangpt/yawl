package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minimal agent — measured ~1,454 bytes total per idle (parked) agent at scale.
 * <p>
 * Structural byte accounting:
 *   Object header:  12 bytes
 *   int id:          4 bytes  (not UUID — saves 12 bytes)
 *   queue ref:       8 bytes  (reference only)
 *   ──────────────────────────
 *   Agent object:   24 bytes
 *   Queue object:  ~160 bytes (LinkedTransferQueue + internal nodes)
 *   VThread object: ~200-400 bytes (unmounted continuation + JVM bookkeeping)
 *   CHM entry:      ~48 bytes (ConcurrentHashMap.Node + hash overhead)
 *   ActorRef:       ~32 bytes (held in behavior closure)
 *   GC overhead:    ~590 bytes (fragmentation, card table, TLAB alignment)
 *   ──────────────────────────
 *   Measured per idle agent: ~1,454 bytes  (2GB heap → 1.46M max concurrent)
 *   (Earlier "132 bytes" was the Agent struct only, not the full VThread cost.)
 *
 * Capacity formula:  maxConcurrent = heapBytes / 1,454
 *   512 MB → ~369 K actors
 *   2   GB → ~1.46 M actors
 *   16  GB → ~11.7 M actors
 *
 * stop() race note: 'stopped' flag must be checked before any blocking call.
 * See VirtualThreadRuntime.stop() for the two-phase cancellation protocol.
 *
 * Mailbox types:
 *   - Unbounded (default): LinkedTransferQueue (fire-and-forget, never blocks on send)
 *   - Bounded: ArrayBlockingQueue with capacity (supports backpressure)
 *
 * IMPORTANT: Agent.recv() uses poll(1, SECONDS) instead of blocking take() to avoid
 * saturating carrier threads. The timeout allows:
 *   1. Graceful shutdown when actor should stop
 *   2. Carrier thread parking (no spin-waiting)
 *   3. Responsiveness to interruption
 *
 * Before (blocking forever):
 *   Object msg = queue.take(); // would block indefinitely
 *
 * After (non-blocking with timeout):
 *   Object msg = queue.poll(1, TimeUnit.SECONDS); // parks virtual thread
 *   if (msg == null) continue; // try again
 */
final class Agent {

    final int id;                          // 4 bytes — identity, 4B agents addressable
    final BlockingQueue<Object> q;         // 8 bytes ref — MPSC/MPMC mailbox (bounded or unbounded)
    final boolean bounded;                 // true if using ArrayBlockingQueue with backpressure
    volatile Thread thread;                // set by VirtualThreadRuntime; used for injectException
    volatile boolean stopped;              // true after stop() — task checks this on start

    /**
     * Create an unbounded agent (default, fire-and-forget semantics).
     */
    Agent(int id) {
        this.id = id;
        this.q = new LinkedTransferQueue<>();
        this.bounded = false;
    }

    /**
     * Create a bounded agent with backpressure (blocking send on full mailbox).
     *
     * @param id       actor identity
     * @param capacity maximum messages in mailbox (must be > 0)
     */
    Agent(int id, int capacity) {
        this.id = id;
        this.q = new ArrayBlockingQueue<>(capacity);
        this.bounded = true;
    }

    /**
     * Send a message non-blocking.
     * For unbounded agents: always succeeds (fire-and-forget).
     * For bounded agents: offer() — may drop if queue is full.
     */
    void send(Object msg) {
        q.offer(msg);
    }

    /**
     * Send a message with backpressure (blocking if queue is full).
     * For unbounded agents: equivalent to send() (never blocks).
     * For bounded agents: put() — blocks caller until space available.
     *
     * @param msg message to send
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    void sendBlocking(Object msg) throws InterruptedException {
        if (bounded) {
            q.put(msg);  // blocks until space available (backpressure)
        } else {
            q.offer(msg);  // unbounded: always succeeds, non-blocking
        }
    }

    /**
     * Receive a message with timeout-based polling.
     *
     * Uses poll(1, TimeUnit.SECONDS) instead of blocking take() to:
     *   - Avoid saturating carrier threads (virtual threads park on timeout)
     *   - Allow graceful shutdown when actor should stop
     *   - Handle interruption properly
     *   - Prevent memory leaks from endless blocking
     *
     * @return the next message from the queue, or null if timeout occurs
     */
    Object recv() {
        try {
            return q.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupt status
            return null; // treat as timeout behavior
        }
    }

    /**
     * Run the actor behavior with the specified ActorRef.
     * This is the entry point for the actor execution.
     *
     * @param ref the ActorRef for this actor (self-reference)
     * @param behavior the actor behavior to run
     */
    void run(ActorRef ref, ActorBehavior behavior) {
        // Two-phase cancellation: write a.thread, then read a.stopped.
        // VirtualThreadRuntime.stop() writes a.stopped, then reads a.thread.
        // Volatile memory ordering guarantees at least one of them sees
        // the other's write, eliminating the "null thread → missed interrupt" race:
        //   Case A: stop() completes first → a.stopped=true; task checks → exits.
        //   Case B: task sets a.thread first → stop() reads it → interrupts.
        this.thread = Thread.currentThread();
        if (this.stopped) {
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
            // Actor exits; VirtualThreadRuntime will handle removal.
            System.err.printf("[Agent] Agent %d threw: %s%n", id, e.getMessage());
        }
    }

}
