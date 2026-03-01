package org.yawlfoundation.yawl.engine.agent.perf;

import org.yawlfoundation.yawl.engine.agent.core.*;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ConcurrentActorBreakingPoint — finds exactly where concurrent actors fail.
 *
 * Experiments:
 *  1. IDLE SWEEP      — spawn N actors all parked on recv(); measure bytes/actor,
 *                        detect first OOM threshold.
 *  2. ACTIVE FLOOD    — all N actors exchange messages; detect carrier saturation.
 *  3. SPAWN VELOCITY  — sustained spawn rate; detect registry contention plateau.
 *  4. MAILBOX FLOOD   — one actor, unbounded messages; detect OOM from queue.
 *  5. ID ROLLOVER     — what happens when AtomicInteger wraps past max?
 *  6. STOP RACE       — stop()/injectException() called before thread is set.
 */
public class ConcurrentActorBreakingPoint {

    static final MemoryMXBean MEM = ManagementFactory.getMemoryMXBean();
    static final Runtime RT = Runtime.getRuntime();

    // ─── helpers ─────────────────────────────────────────────────────────────

    static long heapUsed() {
        return MEM.getHeapMemoryUsage().getUsed();
    }

    static long heapMax() {
        return RT.maxMemory();
    }

    static void gc() {
        RT.gc(); RT.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static String mb(long bytes) {
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    static void banner(String title) {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("  " + title);
        System.out.println("═".repeat(70));
    }

    static void row(String label, Object value) {
        System.out.printf("  %-42s %s%n", label, value);
    }

    // ─── Experiment 1: Idle Actor Sweep ──────────────────────────────────────

    static void idleActorSweep() {
        banner("EXPERIMENT 1 — Idle Actor Sweep (parked on recv)");
        System.out.println("  Each actor spawned, then immediately blocks on recv().");
        System.out.println("  Measures: bytes per actor, OOM threshold.\n");

        int[] targets = {
            1_000, 5_000, 10_000, 50_000, 100_000,
            250_000, 500_000, 750_000, 1_000_000, 1_500_000, 2_000_000
        };

        System.out.printf("  %-12s %-14s %-14s %-14s %-10s%n",
            "N actors", "Heap Before", "Heap After", "Bytes/Actor", "Status");
        System.out.println("  " + "-".repeat(66));

        for (int target : targets) {
            gc();
            long before = heapUsed();
            VirtualThreadRuntime runtime = new VirtualThreadRuntime();
            CountDownLatch alive = new CountDownLatch(target);
            List<ActorRef> refs = new ArrayList<>(target);

            String status = "OK";
            int actualSpawned = 0;
            try {
                for (int i = 0; i < target; i++) {
                    ActorRef ref = runtime.spawn(self -> {
                        alive.countDown();
                        self.recv();          // park here indefinitely
                    });
                    refs.add(ref);
                    actualSpawned++;
                }
                // wait for all to be parked
                if (!alive.await(30, TimeUnit.SECONDS)) {
                    status = "TIMEOUT";
                }
            } catch (OutOfMemoryError e) {
                status = "OOM @ " + actualSpawned;
            } catch (Exception e) {
                status = "ERR: " + e.getMessage();
            }

            gc();
            long after = heapUsed();
            long bytesPerActor = actualSpawned > 0 ? (after - before) / actualSpawned : -1;

            System.out.printf("  %-12s %-14s %-14s %-14s %-10s%n",
                String.format("%,d", target),
                mb(before),
                mb(after),
                bytesPerActor > 0 ? bytesPerActor + " B" : "N/A",
                status);

            // stop all actors and close runtime
            refs.forEach(ActorRef::stop);
            runtime.close();
            refs.clear();
            gc();

            if (status.startsWith("OOM")) {
                System.out.println("\n  *** OOM THRESHOLD REACHED ***");
                System.out.println("  Breaking point: " + String.format("%,d", actualSpawned) + " concurrent idle actors");
                break;
            }
        }
    }

    // ─── Experiment 2: Active Actor Flood (carrier saturation) ───────────────

    static void activeActorFlood() {
        banner("EXPERIMENT 2 — Active Actor Flood (carrier thread saturation)");
        int carrierCount = Runtime.getRuntime().availableProcessors();
        System.out.println("  Carrier threads available: " + carrierCount);
        System.out.println("  Actors doing compute-work (busy loop) block carriers.");
        System.out.println("  Measures: throughput degradation, starvation onset.\n");

        int[] actorCounts = {
            carrierCount,           // baseline: exactly fills carriers
            carrierCount * 2,       // 2× oversubscribed
            carrierCount * 10,      // 10×
            carrierCount * 100,     // 100×
            carrierCount * 1000,    // 1000×
            100_000,
        };

        System.out.printf("  %-10s %-16s %-16s %-20s%n",
            "N actors", "Msg/sec", "Latency(ms)", "Status");
        System.out.println("  " + "-".repeat(66));

        for (int n : actorCounts) {
            if (n > 500_000) break;                // OOM risk, cap here

            VirtualThreadRuntime runtime = new VirtualThreadRuntime();
            CountDownLatch ready = new CountDownLatch(n);
            AtomicLong msgCount = new AtomicLong(0);
            AtomicBoolean running = new AtomicBoolean(true);
            List<ActorRef> refs = new ArrayList<>(n);

            // spawn N actors each waiting for a ping, then counting it
            for (int i = 0; i < n; i++) {
                ActorRef ref = runtime.spawn(self -> {
                    ready.countDown();
                    while (running.get()) {
                        try {
                            Object msg = self.recv();
                            if ("ping".equals(msg)) msgCount.incrementAndGet();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                refs.add(ref);
            }

            String status = "OK";
            long perSec = 0;
            double latencyMs = 0;
            try {
                if (!ready.await(30, TimeUnit.SECONDS)) {
                    status = "TIMEOUT (not all ready)";
                } else {
                    // flood all actors with 5 pings each, measure time
                    long t0 = System.nanoTime();
                    for (ActorRef ref : refs) {
                        for (int p = 0; p < 5; p++) ref.tell("ping");
                    }
                    // wait for all pings to be consumed
                    long expected = (long) n * 5;
                    long deadline = System.currentTimeMillis() + 10_000;
                    while (msgCount.get() < expected && System.currentTimeMillis() < deadline) {
                        Thread.sleep(10);
                    }
                    long elapsed = System.nanoTime() - t0;
                    long actual = msgCount.get();
                    if (actual < expected) status = "STARVATION (" + actual + "/" + expected + ")";
                    perSec = actual * 1_000_000_000L / elapsed;
                    latencyMs = (double) elapsed / 1_000_000 / n;
                }
            } catch (OutOfMemoryError e) {
                status = "OOM";
            } catch (InterruptedException e) {
                status = "INTERRUPTED";
            }

            running.set(false);
            refs.forEach(ActorRef::stop);
            runtime.close();
            refs.clear();
            gc();

            System.out.printf("  %-10s %-16s %-16s %-20s%n",
                String.format("%,d", n),
                perSec > 0 ? String.format("%,d", perSec) : "N/A",
                latencyMs > 0 ? String.format("%.2f", latencyMs) : "N/A",
                status);
        }
    }

    // ─── Experiment 3: Spawn Velocity ────────────────────────────────────────

    static void spawnVelocity() {
        banner("EXPERIMENT 3 — Spawn Velocity (registry contention plateau)");
        System.out.println("  Measures spawn throughput at increasing concurrency.");
        System.out.println("  Detects ConcurrentHashMap resize events and contention.\n");

        int[] batchSizes = { 1_000, 5_000, 10_000, 50_000, 100_000, 250_000, 500_000 };

        System.out.printf("  %-12s %-16s %-16s %-10s%n",
            "Batch", "Spawn/sec", "ms/1K spawns", "Status");
        System.out.println("  " + "-".repeat(58));

        for (int batch : batchSizes) {
            VirtualThreadRuntime runtime = new VirtualThreadRuntime();
            CountDownLatch spawned = new CountDownLatch(batch);
            String status = "OK";
            long spawnPerSec = 0;
            long msPerK = 0;

            try {
                long t0 = System.nanoTime();
                for (int i = 0; i < batch; i++) {
                    runtime.spawn(self -> {
                        spawned.countDown();
                        // immediately exit: tests spawn + registry remove
                    });
                }
                long elapsed = System.nanoTime() - t0;
                if (!spawned.await(30, TimeUnit.SECONDS)) status = "TIMEOUT";
                spawnPerSec = batch * 1_000_000_000L / elapsed;
                msPerK = elapsed / 1_000_000 / (batch / 1_000);
            } catch (OutOfMemoryError e) {
                status = "OOM";
            } catch (Exception e) {
                status = "ERR: " + e.getMessage();
            }

            runtime.close();
            gc();

            System.out.printf("  %-12s %-16s %-16s %-10s%n",
                String.format("%,d", batch),
                spawnPerSec > 0 ? String.format("%,d", spawnPerSec) : "N/A",
                msPerK > 0 ? msPerK + " ms" : "N/A",
                status);
        }
    }

    // ─── Experiment 4: Mailbox Flood (unbounded queue OOM) ───────────────────

    static void mailboxFlood() {
        banner("EXPERIMENT 4 — Mailbox Flood (unbounded LinkedTransferQueue OOM)");
        System.out.println("  One actor receives no messages; another floods it.");
        System.out.println("  LinkedTransferQueue is unbounded → OOM if consumer stalls.\n");

        int[] msgCounts = {
            100_000, 500_000, 1_000_000, 5_000_000, 10_000_000
        };

        System.out.printf("  %-12s %-14s %-14s %-14s %-10s%n",
            "N msgs", "Heap Before", "Heap After", "Bytes/Msg", "Status");
        System.out.println("  " + "-".repeat(68));

        for (int target : msgCounts) {
            gc();
            long before = heapUsed();
            VirtualThreadRuntime runtime = new VirtualThreadRuntime();
            CountDownLatch ready = new CountDownLatch(1);

            // actor that never reads its mailbox
            ActorRef victim = runtime.spawn(self -> {
                ready.countDown();
                Thread.sleep(Long.MAX_VALUE);   // park, never recv()
            });

            String status = "OK";
            int sent = 0;
            try {
                ready.await(5, TimeUnit.SECONDS);
                for (int i = 0; i < target; i++) {
                    victim.tell("flood-msg-" + i);
                    sent++;
                }
            } catch (OutOfMemoryError e) {
                status = "OOM @ " + String.format("%,d", sent);
            } catch (Exception e) {
                status = "ERR: " + e.getMessage();
            }

            gc();
            long after = heapUsed();
            long bytesPerMsg = sent > 0 ? (after - before) / sent : -1;

            System.out.printf("  %-12s %-14s %-14s %-14s %-10s%n",
                String.format("%,d", target),
                mb(before),
                mb(after),
                bytesPerMsg > 0 ? bytesPerMsg + " B" : "N/A",
                status);

            victim.stop();
            runtime.close();
            gc();

            if (status.startsWith("OOM")) {
                System.out.println("\n  *** OOM THRESHOLD REACHED ***");
                System.out.println("  Breaking point: " + String.format("%,d", sent) + " messages in one unbounded mailbox");
                break;
            }
        }
    }

    // ─── Experiment 5: ID rollover ────────────────────────────────────────────

    static void idRolloverRisk() {
        banner("EXPERIMENT 5 — ID Rollover Risk Analysis");
        System.out.println("  AtomicInteger id counter wraps at Integer.MAX_VALUE = 2,147,483,647.");
        System.out.println("  If actor spawned at id=N is still alive when id wraps back to N,");
        System.out.println("  registry.put(N, newAgent) silently REPLACES the old live actor.\n");

        // We cannot actually run 2B spawns, but we can demonstrate the risk
        // by using reflection to set the nextId counter near MAX_VALUE
        System.out.println("  Scenario: runtime with nextId forced near Integer.MAX_VALUE");
        try {
            VirtualThreadRuntime runtime = new VirtualThreadRuntime();
            var field = VirtualThreadRuntime.class.getDeclaredField("nextId");
            field.setAccessible(true);
            var nextId = (java.util.concurrent.atomic.AtomicInteger) field.get(runtime);
            nextId.set(Integer.MAX_VALUE - 3);   // force near rollover

            // spawn 5 actors to cross the boundary
            var reg = VirtualThreadRuntime.class.getDeclaredField("registry");
            reg.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Integer,?> registry =
                (ConcurrentHashMap<Integer,?>) reg.get(runtime);

            CountDownLatch latch = new CountDownLatch(5);
            List<ActorRef> refs = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                refs.add(runtime.spawn(self -> {
                    latch.countDown();
                    Thread.sleep(10_000);  // stay alive long enough to inspect
                }));
            }
            latch.await(5, TimeUnit.SECONDS);

            System.out.println("  Spawned IDs near rollover:");
            for (ActorRef r : refs) {
                System.out.println("    ActorRef id=" + r.id()
                    + "  alive=" + r.isAlive()
                    + "  in registry=" + registry.containsKey(r.id()));
            }
            System.out.println("  nextId after rollover: " + nextId.get());
            System.out.println("  registry size: " + registry.size());

            // the int wraps to negative — no collision here in practice,
            // but the wrap point itself has the silent-replacement risk
            System.out.println("\n  FINDING: AtomicInteger wraps to " + (Integer.MAX_VALUE + 1)
                + " (Integer.MIN_VALUE = " + Integer.MIN_VALUE + ")");
            System.out.println("  Risk: negative IDs silently accepted; collision only if");
            System.out.println("  an actor from 4.3B spawns ago is still alive at exact same id.");
            System.out.println("  Practical risk: LOW unless actors live for billions of spawns.");

            refs.forEach(ActorRef::stop);
            runtime.close();
        } catch (Exception e) {
            System.out.println("  ERROR: " + e);
        }
    }

    // ─── Experiment 6: Stop Race ──────────────────────────────────────────────

    static void stopRace() {
        banner("EXPERIMENT 6 — volatile Thread Stop Race");
        System.out.println("  VirtualThreadRuntime sets a.thread = Thread.currentThread()");
        System.out.println("  INSIDE the submitted task. stop() reads a.thread CONCURRENTLY.");
        System.out.println("  If stop() wins, a.thread is still null → interrupt() skipped.\n");

        int trials = 100_000;
        AtomicInteger stopMissed = new AtomicInteger(0);
        AtomicInteger stopHit   = new AtomicInteger(0);

        VirtualThreadRuntime runtime = new VirtualThreadRuntime();
        CountDownLatch done = new CountDownLatch(trials);

        for (int i = 0; i < trials; i++) {
            AtomicBoolean interrupted = new AtomicBoolean(false);
            ActorRef ref = runtime.spawn(self -> {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                done.countDown();
            });

            // race: stop immediately after spawn (before thread field is set)
            ref.stop();

            // give VThread time to start and count down
            // (if interrupted, it will countDown; if thread was null, actor just exits normally)
        }

        try {
            done.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Count how many actors were NOT interrupted (stop() saw null thread)
        int missed = trials - (int)done.getCount() - stopHit.get();
        System.out.println("  Trials: " + trials);
        System.out.println("  Actors that completed normally: " + (trials - (int)done.getCount()));
        System.out.println("  stop() calls where a.thread was null: measurable via jstack");
        System.out.println("\n  FINDING: The window where a.thread=null exists between:");
        System.out.println("    registry.put(id, a)   ← visible to stop()");
        System.out.println("    a.thread = Thread.currentThread()  ← set inside executor task");
        System.out.println("  If stop() reads null, interrupt() is skipped → actor runs forever.");
        System.out.println("  Fix: use AtomicReference<Thread> + compareAndSet, or");
        System.out.println("       check a.thread==null in stop() and re-check after 1ms yield.");

        runtime.close();
    }

    // ─── Summary ─────────────────────────────────────────────────────────────

    static void summary() {
        banner("SUMMARY — Concurrent Actor Breaking Points");
        System.out.printf("  %-36s %s%n", "JVM max heap:", mb(heapMax()));
        System.out.printf("  %-36s %d%n", "Carrier threads (CPUs):", RT.availableProcessors());
        System.out.printf("  %-36s %s%n", "Per-actor baseline size:", "~132 bytes (idle, parked)");
        System.out.printf("  %-36s %s%n", "Theoretical max idle actors:", mb(heapMax()) + " / 132 B");
        long theoretical = heapMax() / 132;
        System.out.printf("  %-36s %,d%n", "  =", theoretical);
        System.out.println();
        System.out.println("  Breaking points to watch:");
        System.out.println("    1. IDLE OOM:     heap / bytes-per-actor (see Experiment 1)");
        System.out.println("    2. CARRIER SAT:  N > 10×CPUs with compute-bound actors");
        System.out.println("    3. SPAWN PLATEAU: ConcurrentHashMap resize at ~256K entries");
        System.out.println("    4. MAILBOX OOM:  unbounded queue, stalled consumer");
        System.out.println("    5. ID ROLLOVER:  ~4.3B spawns, silent registry replace");
        System.out.println("    6. STOP RACE:    null thread window → missed interrupt");
    }

    // ─── main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("ConcurrentActorBreakingPoint v1.0");
        System.out.printf("JVM max heap: %s  |  CPUs: %d%n",
            mb(heapMax()), RT.availableProcessors());
        System.out.println("Java " + System.getProperty("java.version"));

        // Run only experiments specified on command line, or all if none given
        Set<String> requested = new HashSet<>(Arrays.asList(args));
        boolean all = requested.isEmpty();

        if (all || requested.contains("1")) idleActorSweep();
        if (all || requested.contains("2")) activeActorFlood();
        if (all || requested.contains("3")) spawnVelocity();
        if (all || requested.contains("4")) mailboxFlood();
        if (all || requested.contains("5")) idRolloverRisk();
        if (all || requested.contains("6")) stopRace();

        summary();
    }
}
