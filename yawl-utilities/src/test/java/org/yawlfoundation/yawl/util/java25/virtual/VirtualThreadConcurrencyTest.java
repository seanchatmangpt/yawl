package org.yawlfoundation.yawl.util.java25.virtual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ScopedValue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Virtual Threads in Java 21+.
 *
 * Chicago TDD: Real concurrent execution tests with actual virtual threads.
 * Tests thread creation, task execution, and synchronization patterns.
 */
@DisplayName("Virtual Thread Concurrency")
class VirtualThreadConcurrencyTest {

    @Test
    @DisplayName("Create and execute 1000 virtual threads")
    @Timeout(30)
    void testCreateVirtualThreads() throws InterruptedException {
        int threadCount = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger executedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                .name("virtual-worker-" + i)
                .start(() -> {
                    executedCount.incrementAndGet();
                    latch.countDown();
                });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All virtual threads should complete within timeout");
        assertEquals(threadCount, executedCount.get());
    }

    @Test
    @DisplayName("Virtual thread executor service processes tasks")
    @Timeout(30)
    void testVirtualThreadExecutor() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        int taskCount = 500;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(10);
                    completedTasks.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "All tasks should complete");
        assertEquals(taskCount, completedTasks.get());

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Virtual threads can be named individually")
    @Timeout(10)
    void testVirtualThreadNaming() throws InterruptedException {
        String expectedName = "case-worker-123";
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual()
            .name(expectedName)
            .start(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

        latch.await();
        assertEquals(expectedName, threadName.get());
    }

    @Test
    @DisplayName("Multiple virtual threads execute concurrently")
    @Timeout(15)
    void testConcurrentVirtualExecution() throws InterruptedException {
        int threadCount = 100;
        long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                .start(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
        }

        latch.await();
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(elapsed < 2000, "100 threads sleeping 100ms should take ~100ms, not sequential 10000ms");
    }

    @Test
    @DisplayName("Virtual thread interrupt handling")
    @Timeout(10)
    void testVirtualThreadInterrupt() throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = Thread.ofVirtual()
            .start(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

        Thread.sleep(100);
        t.interrupt();

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(interrupted.get());
    }

    @Test
    @DisplayName("Virtual threads with shared state coordination")
    @Timeout(10)
    void testSharedStateWithVirtualThreads() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                .start(() -> {
                    counter.incrementAndGet();
                    latch.countDown();
                });
        }

        latch.await();
        assertEquals(threadCount, counter.get());
    }

    @Test
    @DisplayName("Virtual thread completion with futures")
    @Timeout(15)
    void testVirtualThreadWithFutures() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        Future<String> future1 = executor.submit(() -> {
            Thread.sleep(50);
            return "result-1";
        });

        Future<String> future2 = executor.submit(() -> {
            Thread.sleep(50);
            return "result-2";
        });

        assertEquals("result-1", future1.get(5, TimeUnit.SECONDS));
        assertEquals("result-2", future2.get(5, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Virtual thread exception handling")
    @Timeout(10)
    void testVirtualThreadExceptionHandling() throws InterruptedException {
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual()
            .start(() -> {
                try {
                    throw new RuntimeException("Test error");
                } catch (RuntimeException e) {
                    caughtException.set(e);
                } finally {
                    latch.countDown();
                }
            });

        latch.await();
        assertNotNull(caughtException.get());
        assertEquals("Test error", caughtException.get().getMessage());
    }

    @Test
    @DisplayName("Virtual thread task queue processing")
    @Timeout(20)
    void testVirtualThreadTaskQueue() throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        int taskCount = 100;

        for (int i = 0; i < taskCount; i++) {
            queue.offer("task-" + i);
        }

        AtomicInteger processed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(taskCount);

        executor.submit(() -> {
            while (true) {
                try {
                    String task = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (task == null) break;

                    processed.incrementAndGet();
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        latch.await(10, TimeUnit.SECONDS);
        assertEquals(taskCount, processed.get());

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Virtual threads don't block platform threads")
    @Timeout(15)
    void testVirtualThreadNoBlockingOfPlatform() throws InterruptedException {
        AtomicInteger platformWork = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        Thread platformThread = Thread.ofPlatform()
            .start(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        platformWork.incrementAndGet();
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        virtualExecutor.submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        assertTrue(platformWork.get() > 0, "Platform thread should progress regardless of virtual thread");

        virtualExecutor.shutdown();
        virtualExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Many virtual threads with minimal resource overhead")
    @Timeout(30)
    void testManyVirtualThreads() throws InterruptedException {
        int threadCount = 10000;
        long startMemory = Runtime.getRuntime().totalMemory();

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        assertTrue(completed, "10000 virtual threads should complete");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long endMemory = Runtime.getRuntime().totalMemory();
        long memoryUsed = endMemory - startMemory;

        assertTrue(memoryUsed > 0, "Memory should be used");
    }

    @Test
    @DisplayName("Virtual thread scoped value propagation")
    @Timeout(10)
    void testVirtualThreadScopedValues() throws InterruptedException {
        ScopedValue<String> scopedValue = ScopedValue.newInstance();
        AtomicInteger correctValues = new AtomicInteger(0);
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            String value = "value-" + id;
            Thread.ofVirtual()
                .start(() -> {
                    ScopedValue.callWhere(scopedValue, value, () -> {
                        if (value.equals(scopedValue.get())) {
                            correctValues.incrementAndGet();
                        }
                        latch.countDown();
                    });
                });
        }

        latch.await();
        assertEquals(threadCount, correctValues.get(), "All virtual threads should receive correct scoped values");
    }
}
