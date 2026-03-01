package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.LinkedTransferQueue;

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
 */
final class Agent {

    final int id;                        // 4 bytes — identity, 4B agents addressable
    final LinkedTransferQueue<Object> q; // 8 bytes ref — lock-free MPSC/MPMC mailbox
    volatile Thread thread;              // set by VirtualThreadRuntime; used for injectException
    volatile boolean stopped;            // true after stop() — task checks this on start

    Agent(int id) {
        this.id = id;
        this.q = new LinkedTransferQueue<>();
    }

    void send(Object msg) {
        q.offer(msg);
    }

    Object recv() {
        return q.poll();
    }
}
