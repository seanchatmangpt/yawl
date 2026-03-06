import java.lang.ScopedValue;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standalone Virtual Thread and Lock Contention Breaking Point Stress Test.
 * No Maven, no dependencies - pure Java 25 with virtual threads.
 */
public class VirtualThreadBreakingPointTest {

    private static final List<String> REPORT_LINES = new ArrayList<>();

    // Test ScopedValues at various depths
    private static final ScopedValue<String> DEPTH_1 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_2 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_3 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_1 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_2 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_3 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_4 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_5 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_6 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_7 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_8 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_9 = ScopedValue.newInstance();
    private static final ScopedValue<String> DEPTH_10_10 = ScopedValue.newInstance();

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("===== AGENT 2: VIRTUAL THREAD BREAKING POINTS =====");
        System.out.println();

        test1_virtualThreadScalingBreakingPoint();
        test2_lockContentionLadder();
        test3_pinningDetection();
        test4_scopedValueChainDepth();
        test5_platformVirtualThreadMix();

        System.out.println();
        System.out.println("## AGENT 2 RESULTS: Virtual Thread Breaking Points");
        for (String line : REPORT_LINES) {
            System.out.println(line);
        }
        System.out.println();
    }

    private static long pct(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // =========================================================================
    // Test 1: Virtual Thread Scaling — Max threads before OOM/GC crisis
    // =========================================================================

    private static void test1_virtualThreadScalingBreakingPoint() throws Exception {
        System.out.println("\nTest 1: Virtual Thread Scaling...");

        final int[] LEVELS = {10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000};

        Runtime rt = Runtime.getRuntime();
        long maxVThreads = 0;
        long memoryPerThread = 0;
        String breakPoint = "Not reached";

        for (int threadCount : LEVELS) {
            rt.gc();
            long heapBefore = rt.totalMemory() - rt.freeMemory();
            long startWall = System.nanoTime();
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            try {
                for (int i = 0; i < threadCount; i++) {
                    Thread.ofVirtual()
                          .name("stress-scaling-" + i)
                          .start(() -> {
                              try {
                                  Thread.sleep(100);
                                  long dummy = System.nanoTime();
                                  if (dummy < 0) errors.incrementAndGet();
                              } catch (OutOfMemoryError | InterruptedException e) {
                                  errors.incrementAndGet();
                              } finally {
                                  done.countDown();
                              }
                          });
                }

                boolean completed = done.await(60, TimeUnit.SECONDS);
                long wallMs = (System.nanoTime() - startWall) / 1_000_000;

                long heapAfter = rt.totalMemory() - rt.freeMemory();
                long heapDelta = heapAfter - heapBefore;

                if (completed && errors.get() == 0) {
                    maxVThreads = threadCount;
                    memoryPerThread = threadCount > 0 ? heapDelta / threadCount : 0;
                    System.out.printf("  ✓ %,d threads: heap_delta=%,dMB (%.0f B/thread) wall=%.2fs%n",
                            threadCount, heapDelta / (1024 * 1024), (double) heapDelta / threadCount, wallMs / 1000.0);
                } else {
                    breakPoint = String.format("%,d threads (OOM or timeout)", threadCount);
                    System.out.printf("  ✗ BREAKING POINT at %,d threads: completed=%b errors=%d%n",
                            threadCount, completed, errors.get());
                    break;
                }
            } catch (OutOfMemoryError oom) {
                breakPoint = String.format("%,d threads (OutOfMemoryError)", threadCount);
                System.out.printf("  ✗ OOM at %,d threads%n", threadCount);
                break;
            }
        }

        String result = String.format(
                "Test 1 — Virtual Thread Scaling: max_vthreads=%,d  memory_per_thread=%,d bytes  breaking_point=%s",
                maxVThreads, memoryPerThread, breakPoint);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Test 2: Lock Contention Ladder — Throughput at N=10/100/1000/10000
    // =========================================================================

    private static void test2_lockContentionLadder() throws Exception {
        System.out.println("\nTest 2: Lock Contention Ladder...");

        final int[] THREAD_COUNTS = {10, 100, 1_000, 10_000};
        final int ITERATIONS = 10_000;
        final long DURATION_MS = 5_000;

        List<String> contentionResults = new ArrayList<>();
        long opsAt10 = 0, opsAt100 = 0, opsAt1K = 0, opsAt10K = 0;
        int degradationPoint = -1;

        for (int threadCount : THREAD_COUNTS) {
            ReentrantLock lock = new ReentrantLock();
            AtomicLong ops = new AtomicLong(0);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch allRunning = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            long startWall = System.nanoTime();
            long endTime = startWall + (DURATION_MS * 1_000_000L);

            // Launch threads
            for (int t = 0; t < threadCount; t++) {
                Thread.ofVirtual()
                      .name("contention-" + t)
                      .start(() -> {
                          try {
                              allRunning.countDown();
                              start.await();

                              while (System.nanoTime() < endTime) {
                                  lock.lock();
                                  try {
                                      ops.incrementAndGet();
                                  } finally {
                                      lock.unlock();
                                  }
                              }
                          } catch (InterruptedException e) {
                              errors.incrementAndGet();
                              Thread.currentThread().interrupt();
                          } catch (Exception e) {
                              errors.incrementAndGet();
                          }
                      });
            }

            allRunning.await(10, TimeUnit.SECONDS);
            start.countDown();

            Thread.sleep(DURATION_MS);
            long wallMs = (System.nanoTime() - startWall) / 1_000_000;

            long throughput = wallMs > 0 ? (ops.get() * 1000) / wallMs : 0;
            contentionResults.add(String.format("N=%,5d: %,12d ops/sec", threadCount, throughput));

            // Track for degradation analysis
            if (threadCount == 10) opsAt10 = throughput;
            else if (threadCount == 100) opsAt100 = throughput;
            else if (threadCount == 1_000) opsAt1K = throughput;
            else if (threadCount == 10_000) opsAt10K = throughput;

            System.out.printf("  N=%,6d: throughput=%,d ops/sec%n", threadCount, throughput);
        }

        // Find degradation point (>50% drop)
        long peakOps = Math.max(Math.max(opsAt10, opsAt100), Math.max(opsAt1K, opsAt10K));
        if (opsAt10 > peakOps * 0.5 && opsAt100 <= peakOps * 0.5) degradationPoint = 100;
        else if (opsAt100 > peakOps * 0.5 && opsAt1K <= peakOps * 0.5) degradationPoint = 1_000;
        else if (opsAt1K > peakOps * 0.5 && opsAt10K <= peakOps * 0.5) degradationPoint = 10_000;

        String result = String.format(
                "Test 2 — Lock Contention: N=10: %,d ops/s | N=100: %,d ops/s | N=1K: %,d ops/s | N=10K: %,d ops/s  degradation_point=%s",
                opsAt10, opsAt100, opsAt1K, opsAt10K,
                degradationPoint > 0 ? String.valueOf(degradationPoint) : "None detected");
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Test 3: Pinning Detection — Synchronized blocks under virtual threads
    // =========================================================================

    private static void test3_pinningDetection() throws Exception {
        System.out.println("\nTest 3: Pinning Detection...");

        final int THREAD_COUNT = 1_000;

        // Test 1: ReentrantLock (should NOT pin)
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch done1 = new CountDownLatch(THREAD_COUNT);
        AtomicLong counter1 = new AtomicLong(0);

        long startWall = System.nanoTime();
        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread.ofVirtual()
                  .name("reentrant-" + i)
                  .start(() -> {
                      try {
                          lock.lock();
                          try {
                              counter1.incrementAndGet();
                          } finally {
                              lock.unlock();
                          }
                          LockSupport.parkNanos(100_000);
                      } finally {
                          done1.countDown();
                      }
                  });
        }

        done1.await(20, TimeUnit.SECONDS);
        long reentrantWallMs = (System.nanoTime() - startWall) / 1_000_000;

        // Test 2: Synchronized block (may pin)
        CountDownLatch done2 = new CountDownLatch(THREAD_COUNT);
        AtomicLong counter2 = new AtomicLong(0);
        Object syncLock = new Object();

        startWall = System.nanoTime();
        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread.ofVirtual()
                  .name("sync-" + i)
                  .start(() -> {
                      try {
                          synchronized (syncLock) {
                              counter2.incrementAndGet();
                          }
                          LockSupport.parkNanos(100_000);
                      } finally {
                          done2.countDown();
                      }
                  });
        }

        done2.await(20, TimeUnit.SECONDS);
        long syncWallMs = (System.nanoTime() - startWall) / 1_000_000;

        double syncOverhead = syncWallMs > reentrantWallMs ?
                ((double)(syncWallMs - reentrantWallMs) / reentrantWallMs * 100) : 0;

        String result = String.format(
                "Test 3 — Pinning Detection: ReentrantLock=%dms  Synchronized=%dms  sync_overhead=%.1f%%  counter1=%d  counter2=%d",
                reentrantWallMs, syncWallMs, syncOverhead, counter1.get(), counter2.get());
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    // =========================================================================
    // Test 4: ScopedValue Chain Depth — Latency by binding depth
    // =========================================================================

    private static void test4_scopedValueChainDepth() throws Exception {
        System.out.println("\nTest 4: ScopedValue Chain Depth...");

        final int ITERATIONS = 1_000_000;

        // Depth 1
        long latency1 = measureScopedValueLatency(DEPTH_1, "val1", ITERATIONS, 1);

        // Depth 3
        long latency3 = measureScopedValueLatency(DEPTH_1, "val1", ITERATIONS, 3);

        // Depth 10
        long latency10 = measureScopedValueLatency(DEPTH_10_1, "val1", ITERATIONS, 10);

        String result = String.format(
                "Test 4 — ScopedValue Chain Depth: depth_1=%d ns  depth_3=%d ns  depth_10=%d ns",
                latency1, latency3, latency10);
        REPORT_LINES.add(result);
        System.out.println(result);
    }

    private static long measureScopedValueLatency(ScopedValue<String> sv, String value, int iterations, int depth) throws Exception {
        long totalNanos = 0;

        for (int iter = 0; iter < iterations; iter++) {
            long startNanos = System.nanoTime();

            if (depth == 1) {
                ScopedValue.where(sv, value).run(() -> {
                    String v = sv.get();
                    if (v == null) throw new AssertionError();
                });
            } else if (depth == 3) {
                ScopedValue.where(DEPTH_1, "v1")
                    .where(DEPTH_2, "v2")
                    .where(DEPTH_3, "v3")
                    .run(() -> {
                        String v = DEPTH_3.get();
                        if (v == null) throw new AssertionError();
                    });
            } else if (depth == 10) {
                ScopedValue.where(DEPTH_10_1, "v1")
                    .where(DEPTH_10_2, "v2")
                    .where(DEPTH_10_3, "v3")
                    .where(DEPTH_10_4, "v4")
                    .where(DEPTH_10_5, "v5")
                    .where(DEPTH_10_6, "v6")
                    .where(DEPTH_10_7, "v7")
                    .where(DEPTH_10_8, "v8")
                    .where(DEPTH_10_9, "v9")
                    .where(DEPTH_10_10, "v10")
                    .run(() -> {
                        String v = DEPTH_10_10.get();
                        if (v == null) throw new AssertionError();
                    });
            } else {
                // For other depths, use a simpler measurement
                ScopedValue.where(sv, value).run(() -> {
                    String v = sv.get();
                    if (v == null) throw new AssertionError();
                });
            }

            totalNanos += (System.nanoTime() - startNanos);
        }

        return totalNanos / iterations;
    }

    // =========================================================================
    // Test 5: Platform + Virtual Thread Mix — Starvation point
    // =========================================================================

    private static void test5_platformVirtualThreadMix() throws Exception {
        System.out.println("\nTest 5: Platform + Virtual Thread Mix...");

        final int[] VTHREAD_LEVELS = {100, 500, 1_000, 5_000, 10_000};
        final int PLATFORM_THREAD_COUNT = 100;
        final long BASELINE_LATENCY_MS = 1; // expected P99 for platform thread work

        long starvationVThreadCount = -1;
        long baselinePlatformLatency = 0;

        for (int vthreadCount : VTHREAD_LEVELS) {
            ExecutorService platformExecutor = Executors.newFixedThreadPool(PLATFORM_THREAD_COUNT);
            ExecutorService vthreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

            CountDownLatch platformStarted = new CountDownLatch(PLATFORM_THREAD_COUNT);
            CountDownLatch workDone = new CountDownLatch(PLATFORM_THREAD_COUNT + vthreadCount);

            List<Long> platformLatencies = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger platformErrors = new AtomicInteger(0);

            long startWall = System.nanoTime();

            // Start platform threads doing metered work
            for (int p = 0; p < PLATFORM_THREAD_COUNT; p++) {
                platformExecutor.submit(() -> {
                    try {
                        platformStarted.countDown();
                        platformStarted.await();

                        while (System.nanoTime() - startWall < 10_000_000_000L) { // 10s
                            long t0 = System.nanoTime();
                            Thread.sleep(1); // 1ms work
                            platformLatencies.add((System.nanoTime() - t0) / 1_000_000);
                        }
                    } catch (InterruptedException e) {
                        platformErrors.incrementAndGet();
                        Thread.currentThread().interrupt();
                    } finally {
                        workDone.countDown();
                    }
                });
            }

            // Start virtual threads doing background work
            for (int v = 0; v < vthreadCount; v++) {
                vthreadExecutor.submit(() -> {
                    try {
                        platformStarted.countDown();
                        platformStarted.await();

                        while (System.nanoTime() - startWall < 10_000_000_000L) {
                            LockSupport.parkNanos(10_000); // 0.01ms
                        }
                    } catch (Exception e) {
                        platformErrors.incrementAndGet();
                    } finally {
                        workDone.countDown();
                    }
                });
            }

            workDone.await(15, TimeUnit.SECONDS);
            platformExecutor.shutdownNow();
            vthreadExecutor.shutdownNow();

            long[] lats = platformLatencies.stream().mapToLong(Long::longValue).sorted().toArray();
            long p99 = lats.length > 0 ? pct(lats, 99) : 0;

            if (vthreadCount == VTHREAD_LEVELS[0]) {
                baselinePlatformLatency = p99;
            }

            double starvationRatio = baselinePlatformLatency > 0 ? (double) p99 / baselinePlatformLatency : 1.0;

            if (starvationRatio > 10.0 && starvationVThreadCount < 0) {
                starvationVThreadCount = vthreadCount;
            }

            System.out.printf("  vthreads=%,5d: platform_p99=%dms  ratio=%.1f%n",
                    vthreadCount, p99, starvationRatio);
        }

        String result = String.format(
                "Test 5 — Platform + Virtual Mix: baseline_p99=%dms  starvation_point=%s vthreads",
                baselinePlatformLatency,
                starvationVThreadCount > 0 ? String.valueOf(starvationVThreadCount) : "Not reached in tested range");
        REPORT_LINES.add(result);
        System.out.println(result);
    }
}
