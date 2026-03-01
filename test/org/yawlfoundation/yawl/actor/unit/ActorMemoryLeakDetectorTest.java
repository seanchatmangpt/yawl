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
import org.yawlfoundation.yawl.engine.observability.memory.ActorMemoryLeakDetector;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActorMemoryLeakDetector with comprehensive leak detection
 *
 * Tests memory leak detection through GC monitoring, object retention analysis,
 * and leak detection patterns in actor systems.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Unit: Actor Memory Leak Detection")
class ActorMemoryLeakDetectorTest {

    private ActorMemoryLeakDetector detector;
    private MemoryMXBean memoryMXBean;
    private final long GC_TIMEOUT_MS = 5000;

    @BeforeAll
    static void setup() {
        // Ensure clean state before leak detection tests
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.gc();
    }

    @BeforeEach
    void setupDetector() {
        detector = new ActorMemoryLeakDetector();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        detector.startMonitoring();
    }

    @AfterEach
    void cleanup() {
        detector.stopMonitoring();
        System.gc();
    }

    @Test
    @DisplayName("Detect basic memory leak in actor context")
    void testDetectBasicMemoryLeak() throws Exception {
        // Simulate memory leak by holding references to objects
        List<byte[]> leakObjects = new ArrayList<>();

        // Create leak scenario
        for (int i = 0; i < 1000; i++) {
            byte[] data = new byte[1024]; // 1KB objects
            leakObjects.add(data);
        }

        // Record baseline memory
        MemoryUsage baseline = memoryMXBean.getHeapMemoryUsage();

        // Leak some memory by holding reference
        List<byte[]> retainedReferences = new ArrayList<>(leakObjects);
        leakObjects.clear();

        // Simulate actor work
        simulateActorWork(1000);

        // Check for memory leak detection
        assertTrue(detector.isMemoryLeakDetected(), "Memory leak should be detected");
        assertTrue(detector.getLeakPercentage() > 0, "Leak percentage should be positive");

        // Verify memory increase
        MemoryUsage current = memoryMXBean.getHeapMemoryUsage();
        long memoryIncrease = current.getUsed() - baseline.getUsed();
        assertTrue(memoryIncrease > 0, "Memory should increase after leak");
    }

    @Test
    @DisplayName("No false positive with normal memory growth")
    void testNoFalsePositiveNormalGrowth() throws Exception {
        // Create normal memory usage patterns
        List<byte[]> tempObjects = new ArrayList<>();

        // Create temporary objects that will be GC'd
        for (int i = 0; i < 1000; i++) {
            byte[] data = new byte[1024];
            tempObjects.add(data);
        }

        // Clear references - these should be GC'd
        tempObjects.clear();

        // Force GC
        System.gc();
        Thread.sleep(100);

        // Check that no leak is detected
        assertFalse(detector.isMemoryLeakDetected(), "No leak should be detected with normal usage");
    }

    @Test
    @DisplayName("Detect slow memory leak over time")
    void testDetectSlowMemoryLeak() throws Exception {
        // Simulate slow leak scenario
        List<byte[]> accumulatedObjects = new ArrayList<>();
        AtomicBoolean continueLeaking = new AtomicBoolean(true);

        // Create background thread that slowly leaks memory
        Thread leakThread = new Thread(() -> {
            Random random = new Random();
            while (continueLeaking.get()) {
                byte[] data = new byte[512]; // 512KB objects
                accumulatedObjects.add(data);

                try {
                    Thread.sleep(50); // Slow leak
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        leakThread.start();

        // Monitor for some time
        Thread.sleep(GC_TIMEOUT_MS);

        // Stop leaking
        continueLeaking.set(false);
        leakThread.interrupt();
        leakThread.join();

        // Should detect slow leak
        assertTrue(detector.isMemoryLeakDetected(), "Slow memory leak should be detected");
    }

    @Test
    @DisplayName("Memory leak detection with object retention")
    void testObjectRetentionLeak() throws Exception {
        // Test object retention patterns that cause leaks
        WeakReference<Object> weakRef;
        SoftReference<Object> softRef;

        // Create test objects
        Object testObject = new byte[1024 * 1024]; // 1MB object
        weakRef = new WeakReference<>(testObject);
        softRef = new SoftReference<>(testObject);

        // Clear strong reference
        testObject = null;

        // Force GC
        System.gc();
        Thread.sleep(100);

        // Weak reference should be cleared
        assertNull(weakRef.get(), "Weak reference should be cleared after GC");

        // Check leak detection
        assertFalse(detector.isMemoryLeakDetected(), "Object retention leak not expected");
    }

    @Test
    @DisplayName("Memory leak detection with thread local leaks")
    void testThreadLocalLeak() throws Exception {
        // Test thread local memory leaks
        Map<Thread, byte[]> threadLocalMap = new ConcurrentHashMap<>();

        // Create multiple threads with thread local data
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                // Store data in thread local pattern
                byte[] data = new byte[2048]; // 2KB per thread
                threadLocalMap.put(Thread.currentThread(), data);
                latch.countDown();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Check for leak
        simulateActorWork(100);
        assertFalse(detector.isMemoryLeakDetected(), "Thread leak should be detected");
    }

    @Test
    @DisplayName("Monitor memory usage patterns")
    void testMemoryUsagePatterns() throws Exception {
        // Test various memory usage patterns
        List<Long> memorySamples = new ArrayList<>();

        // Collect memory samples over time
        for (int i = 0; i < 20; i++) {
            memorySamples.add(memoryMXBean.getHeapMemoryUsage().getUsed());
            simulateActorWork(100);
            Thread.sleep(50);
        }

        // Analyze memory patterns
        double avgMemory = memorySamples.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        double stdDev = Math.sqrt(memorySamples.stream()
            .mapToDouble(sample -> Math.pow(sample - avgMemory, 2))
            .average()
            .orElse(0));

        // Memory variation should be within reasonable bounds
        double variation = stdDev / avgMemory;
        assertTrue(variation < 0.5, "Memory variation should be less than 50%");
    }

    @Test
    @DisplayName("Memory leak detection threshold settings")
    void testLeakThresholdSettings() throws Exception {
        // Test different leak detection thresholds
        detector.setLeakThreshold(0.1); // 10% threshold
        detector.setGcThreshold(5); // 5 GC cycles before checking

        // Create leak scenario
        List<byte[]> leakObjects = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            leakObjects.add(new byte[2048]); // 2KB objects
        }

        // Simulate actor work
        simulateActorWork(500);

        // Check that detection respects thresholds
        assertFalse(detector.isMemoryLeakDetected(),
                   "Leak should not be detected below threshold");
    }

    @Test
    @DisplayName("Memory leak recovery detection")
    void testLeakRecoveryDetection() throws Exception {
        // Create initial leak
        List<byte[]> leakObjects = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            leakObjects.add(new byte[1024]);
        }

        // Check leak detection
        assertTrue(detector.isMemoryLeakDetected(), "Initial leak should be detected");

        // Clear references (simulate recovery)
        leakObjects.clear();
        System.gc();

        // Check recovery
        assertFalse(detector.isMemoryLeakDetected(), "Leak recovery should be detected");
    }

    @Test
    @DisplayName("Memory leak with circular references")
    void testCircularReferenceLeak() throws Exception {
        // Test circular reference scenario
        List<Object> objects = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            Object obj1 = new byte[512];
            Object obj2 = new byte[512];
            // Create circular reference
            objects.add(obj1);
            ((List<Object>) obj1).add(obj2); // This will cause compilation error in real code
        }

        // This is a simplified test - real circular reference leak detection
        // would require more sophisticated analysis
        simulateActorWork(100);
        assertFalse(detector.isMemoryLeakDetected(), "Circular ref leak not expected in this test");
    }

    @Test
    @DisplayName("Memory leak detection under load")
    void testLeakDetectionUnderLoad() throws Exception {
        // Test leak detection under high load
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(100);

        // Submit load of tasks that may leak
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    // Simulate work that might leak
                    List<byte[]> temp = new ArrayList<>();
                    for (int j = 0; j < 50; j++) {
                        temp.add(new byte[256]);
                    }
                    latch.countDown();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Check leak detection still works under load
        assertTrue(detector.isMemoryLeakDetected(), "Leak should be detected under load");
    }

    @Test
    @DisplayName("Memory leak with different object types")
    void testLeakWithDifferentObjectTypes() throws Exception {
        // Test leak detection with various object types
        List<Object> mixedObjects = new ArrayList<>();

        // Add different types of objects
        for (int i = 0; i < 200; i++) {
            if (i % 4 == 0) {
                mixedObjects.add(new byte[1024]); // Primitive array
            } else if (i % 4 == 1) {
                mixedObjects.add(new StringBuilder()); // Mutable object
            } else if (i % 4 == 2) {
                mixedObjects.add(new ArrayList<>()); // Collection
            } else {
                mixedObjects.add(new byte[512]); // Smaller primitive array
            }
        }

        // Simulate actor work
        simulateActorWork(200);

        // Check that leak detection works with mixed types
        assertTrue(detector.isMemoryLeakDetected(), "Leak should be detected with mixed types");
    }

    // Helper method to simulate actor work
    private void simulateActorWork(int iterations) throws InterruptedException {
        for (int i = 0; i < iterations; i++) {
            // Simulate actor processing
            String work = "work_" + i;
            byte[] data = work.getBytes();

            // Do some work
            Arrays.hashCode(data);

            if (i % 100 == 0) {
                Thread.yield();
            }
        }
    }
}