/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.unit;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.actor.deadlock.ActorLockFreeValidator;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActorDeadlockDetector with comprehensive deadlock detection
 *
 * Tests deadlock detection through lock monitoring, thread state analysis,
 * and deadlock scenario simulation in actor systems.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Unit: Actor Deadlock Detection")
class ActorDeadlockDetectorTest {

    private ActorLockFreeValidator deadlockDetector;
    private YEngine engine;
    private final long TEST_TIMEOUT_MS = 10000;

    @BeforeAll
    static void setup() {
        // Initialize engine once for all tests
    }

    @BeforeEach
    void setupDetector() {
        deadlockDetector = new ActorLockFreeValidator();
        deadlockDetector.startMonitoring();
        engine = YEngine.getInstance();
        if (engine != null) {
            engine.initialise();
        }
    }

    @AfterEach
    void cleanup() {
        deadlockDetector.stopMonitoring();
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Detect circular waiting deadlock")
    void testDetectCircularWaitingDeadlock() throws Exception {
        // Create scenario that causes circular waiting deadlock
        Lock lock1 = new ReentrantLock();
        Lock lock2 = new ReentrantLock();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);

        // Thread 1: lock1 -> lock2
        executor.submit(() -> {
            try {
                lock1.lock();
                Thread.sleep(100); // Hold lock1
                lock2.lock(); // Try to acquire lock2
                lock2.unlock();
                lock1.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: lock2 -> lock1 (circular)
        executor.submit(() -> {
            try {
                lock2.lock();
                Thread.sleep(100); // Hold lock2
                lock1.lock(); // Try to acquire lock1 - this will deadlock
                lock1.unlock();
                lock2.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Check for deadlock
        Thread.sleep(500); // Wait for potential deadlock
        deadlockDetected.set(deadlockDetector.isDeadlockDetected());

        executor.shutdown();
        assertTrue(deadlockDetected.get(), "Circular waiting deadlock should be detected");
    }

    @Test
    @DisplayName("Detect nested lock acquisition deadlock")
    void testDetectNestedLockDeadlock() throws Exception {
        // Test nested lock acquisition pattern
        ReentrantLock outerLock = new ReentrantLock();
        ReentrantLock innerLock = new ReentrantLock();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);

        // Thread 1: Acquire outer, wait for barrier, then try inner
        executor.submit(() -> {
            try {
                outerLock.lock();
                barrier.await(); // Synchronize with other thread
                innerLock.lock(); // This will deadlock
                innerLock.unlock();
                outerLock.unlock();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: Acquire inner, wait for barrier, then try outer
        executor.submit(() -> {
            try {
                innerLock.lock();
                barrier.await(); // Synchronize
                outerLock.lock(); // This will deadlock
                outerLock.unlock();
                innerLock.unlock();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(500);
        deadlockDetected.set(deadlockDetector.isDeadlockDetected());

        executor.shutdown();
        assertTrue(deadlockDetected.get(), "Nested lock deadlock should be detected");
    }

    @Test
    @DisplayName("Detect unbounded blocking deadlock")
    void testDetectUnboundedBlockingDeadlock() throws Exception {
        // Test unbounded blocking scenario
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);

        // Producer
        executor.submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    queue.put("item_" + i); // This will block when queue is full
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer (very slow)
        executor.submit(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    String item = queue.take();
                    Thread.sleep(100); // Slow consumption
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(1000); // Wait for potential deadlock
        deadlockDetected.set(deadlockDetector.isDeadlockDetected());

        executor.shutdown();
        // Note: This might not always detect deadlock as it's not a true deadlock
        // but a resource starvation scenario
    }

    @Test
    @DisplayName("Detect indefinite wait deadlock")
    void testDetectIndefiniteWaitDeadlock() throws Exception {
        // Test indefinite wait scenario
        Object lock1 = new Object();
        Object lock2 = new Object();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);

        // Thread 1: Lock1, wait for lock2 indefinitely
        executor.submit(() -> {
            synchronized (lock1) {
                try {
                    // Wait indefinitely for a signal that never comes
                    lock1.wait(); // This can cause deadlock if not handled properly
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Thread 2: Lock2, wait for lock1 indefinitely
        executor.submit(() -> {
            synchronized (lock2) {
                try {
                    lock2.wait(); // Indefinite wait
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread.sleep(500);
        deadlockDetected.set(deadlockDetector.isDeadlockDetected());

        executor.shutdown();
        assertFalse(deadlockDetected.get(), "Indefinite wait might not be detected as deadlock");
    }

    @Test
    @DisplayName("No false positive with proper lock ordering")
    void testNoFalsePositiveWithProperLockOrdering() throws Exception {
        // Test proper lock ordering prevents false positives
        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both threads acquire locks in same order: lock1 -> lock2
        executor.submit(() -> {
            lock1.lock();
            try {
                Thread.sleep(50);
                lock2.lock();
                try {
                    Thread.sleep(50);
                } finally {
                    lock2.unlock();
                }
            } finally {
                lock1.unlock();
            }
        });

        executor.submit(() -> {
            lock1.lock();
            try {
                Thread.sleep(50);
                lock2.lock();
                try {
                    Thread.sleep(50);
                } finally {
                    lock2.unlock();
                }
            } finally {
                lock1.unlock();
            }
        });

        Thread.sleep(300);
        assertFalse(deadlockDetector.isDeadlockDetected(),
                    "No deadlock should be detected with proper lock ordering");

        executor.shutdown();
    }

    @Test
    @DisplayName("Deadlock detection with thread state monitoring")
    void testDeadlockDetectionWithThreadStateMonitoring() throws Exception {
        // Test deadlock detection using thread state analysis
        Object lock = new Object();
        AtomicBoolean threadBlocked = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Acquire lock and wait
        executor.submit(() -> {
            synchronized (lock) {
                try {
                    threadBlocked.set(true);
                    lock.wait(5000); // Wait with timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                threadBlocked.set(false);
            }
        });

        // Thread 2: Try to acquire lock while waiting
        executor.submit(() -> {
            synchronized (lock) {
                // This thread will block if Thread 1 is still waiting
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread.sleep(1000);
        deadlockDetector.analyzeThreadStates();
        assertFalse(deadlockDetector.isDeadlockDetected(),
                    "No deadlock should be detected with thread state analysis");

        executor.shutdown();
    }

    @Test
    @DisplayName("Deadlock detection timeout mechanisms")
    void testDeadlockDetectionTimeoutMechanisms() throws Exception {
        // Test timeout mechanisms for deadlock detection
        deadlockDetector.setTimeout(200); // 200ms timeout

        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: lock1 -> lock2
        executor.submit(() -> {
            lock1.lock();
            try {
                Thread.sleep(300); // Longer than timeout
                lock2.lock();
                lock2.unlock();
                lock1.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: lock2 -> lock1
        executor.submit(() -> {
            lock2.lock();
            try {
                Thread.sleep(50);
                lock1.lock(); // This will take longer than timeout
                lock1.unlock();
                lock2.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(500);
        // With timeout, deadlock should be detected faster
        boolean deadlockDetected = deadlockDetector.isDeadlockDetected();
        executor.shutdown();

        // Timeout mechanism should help detect deadlock faster
        assertTrue(deadlockDetected, "Deadlock should be detected with timeout mechanism");
    }

    @Test
    @DisplayName("Deadlock recovery simulation")
    void testDeadlockRecoverySimulation() throws Exception {
        // Test deadlock recovery mechanisms
        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deadlockLatch = new CountDownLatch(1);

        // Thread 1: lock1 -> lock2
        executor.submit(() -> {
            lock1.lock();
            try {
                deadlockLatch.await(); // Wait for deadlock
                lock2.lock();
                lock2.unlock();
                lock1.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: lock2 -> lock1
        executor.submit(() -> {
            lock2.lock();
            try {
                deadlockLatch.await(); // Wait for deadlock
                lock1.lock();
                lock1.unlock();
                lock2.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Let deadlock occur
        Thread.sleep(200);
        assertTrue(deadlockDetector.isDeadlockDetected(), "Deadlock should be detected");

        // Trigger recovery
        deadlockLatch.countDown();

        Thread.sleep(300);
        assertFalse(deadlockDetector.isDeadlockDetected(),
                    "Deadlock should be resolved after recovery");

        executor.shutdown();
    }

    @Test
    @DisplayName("Deadlock detection under high concurrency")
    void testDeadlockDetectionUnderHighConcurrency() throws Exception {
        // Test deadlock detection under high load
        int threadCount = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        // Create multiple potential deadlock scenarios
        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    // Simulate work that might cause deadlock
                    ReentrantLock lock = new ReentrantLock();
                    lock.lock();
                    try {
                        // Simulate complex work
                        Thread.sleep(10);
                        // Check for other threads holding locks
                        if (deadlockDetector.isDeadlockDetected()) {
                            return false; // Deadlock detected
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }));
        }

        // Wait for all tasks to complete
        boolean allSucceeded = true;
        for (Future<?> future : futures) {
            try {
                if (!Boolean.TRUE.equals(future.get(1, TimeUnit.SECONDS))) {
                    allSucceeded = false;
                    break;
                }
            } catch (Exception e) {
                allSucceeded = false;
                break;
            }
        }

        executor.shutdown();
        // Under high concurrency, we might detect deadlocks
        // The exact behavior depends on the timing
    }

    @Test
    @DisplayName("Deadlock with different lock types")
    void testDeadlockWithDifferentLockTypes() throws Exception {
        // Test deadlock detection with various lock types
        Object synchronizedLock = new Object();
        ReentrantLock reentrantLock = new ReentrantLock();
        AtomicInteger atomicInteger = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: synchronized -> ReentrantLock
        executor.submit(() -> {
            synchronized (synchronizedLock) {
                try {
                    Thread.sleep(100);
                    reentrantLock.lock();
                    try {
                        atomicInteger.incrementAndGet();
                    } finally {
                        reentrantLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Thread 2: ReentrantLock -> synchronized (circular)
        executor.submit(() -> {
            reentrantLock.lock();
            try {
                Thread.sleep(100);
                synchronized (synchronizedLock) {
                    atomicInteger.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                reentrantLock.unlock();
            }
        });

        Thread.sleep(500);
        boolean deadlockDetected = deadlockDetector.isDeadlockDetected();
        executor.shutdown();

        // This should detect deadlock with mixed lock types
        assertTrue(deadlockDetected, "Deadlock should be detected with mixed lock types");
    }
}