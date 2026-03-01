package org.yawlfoundation.yawl.engine.agent.core;

import java.util.concurrent.LinkedTransferQueue;

/**
 * Minimal agent — 24-byte object, ~132 bytes total per idle agent.
 * <p>
 * Byte accounting (with -XX:+UseCompactObjectHeaders):
 *   Object header: 12 bytes
 *   int id:         4 bytes  (not UUID — saves 12 bytes)
 *   queue ref:      8 bytes  (reference only)
 *   ─────────────────────
 *   Agent object:  24 bytes
 *   Queue object:  40 bytes  (LinkedTransferQueue, lock-free)
 *   VThread:       64 bytes  (unmounted, no stack)
 *   ScopedValue:   ~4 bytes  (amortized across scope)
 *   ─────────────────────
 *   Per idle agent: ~132 bytes  (vs Erlang: ~1,856 bytes)
 *
 * What is NOT here:
 *   - behavior: lives in virtual thread closure (saves 8 bytes)
 *   - name: use id (saves 40+ bytes)
 *   - state: live in closure (saves 48+ bytes)
 */
final class Agent {

    final int id;                        // 4 bytes — identity, 4B agents addressable
    final LinkedTransferQueue<Object> q; // 8 bytes ref — lock-free MPSC/MPMC mailbox
    volatile Thread thread;              // set by VirtualThreadRuntime; used for injectException

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
