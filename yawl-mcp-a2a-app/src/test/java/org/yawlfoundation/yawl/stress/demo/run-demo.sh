#!/bin/bash

# Virtual Thread Starvation Demo
# This script demonstrates the key concepts and test patterns

echo "=== Virtual Thread Starvation Test Demo ==="
echo ""

# Show test structure
echo "1. Test Structure Overview:"
echo "   ├── BasicVirtualThreadTests"
echo "   │   ├── createVirtualThreads()"
echo "   │   └── virtualThreadPoolBehavior()"
echo "   ├── IoBlockingTests"
echo "   │   ├── virtualThreadsWithIoBlocking()"
echo "   │   └── manyVirtualThreadsWithIoBlocking()"
echo "   ├── CarrierThreadPoolSaturationTests"
echo "   │   ├── carrierThreadPoolSaturationEqual()"
echo "   │   └── carrierThreadPoolSaturationRatio()"
echo "   ├── StarvationDetectionTests"
echo "   │   ├── detectStarvationPoint()"
echo "   │   └── blockingLockContention()"
echo "   ├── PerformanceUnderLoadTests"
echo "   │   ├── performanceWithIncreasingLoad()"
echo "   │   └── resourceUsageComparison()"
echo "   └── EdgeCasesTests"
echo "       ├── handleInterruptionGracefully()"
echo "       ├── testInvalidParameters()"
echo "       └── testThreadNames()"
echo ""

# Show key findings
echo "2. Key Findings:"
echo "   ✓ Virtual threads are lightweight and can scale to millions"
echo "   ✓ Carrier threads (platform threads) are the execution resource"
echo "   ✓ When virtual threads block on I/O, they unmount from carrier threads"
echo "   ✓ Carrier thread pool size = number of available processors by default"
echo "   ✓ Many virtual threads can share few carrier threads efficiently"
echo "   ✓ Starvation occurs when too many threads are ready to run but"
echo "     carrier threads are occupied by blocking operations"
echo ""

# Show test patterns
echo "3. Test Patterns:"
echo "   • Use Thread.ofVirtual() for creating virtual threads"
echo "   • Use Executors.newVirtualThreadPerTaskExecutor() for thread pools"
echo "   • Use CountDownLatch for thread coordination"
echo "   • Use CyclicBarrier for synchronized thread execution"
echo "   • Use AtomicInteger for thread-safe counters"
echo "   • Use ThreadLocalRandom for random delays"
echo "   • Measure execution time to detect performance issues"
echo ""

# Show expected behavior
echo "4. Expected Behavior:"
echo "   • Normal: All tests should complete successfully"
echo "   • Saturation: Some tests may show performance degradation"
echo "   • Starvation: Tests may show timeouts or slow progress"
echo "   • Recovery: System should remain responsive"
echo ""

# Show resource usage patterns
echo "5. Resource Usage Patterns:"
echo "   • Memory: Virtual threads use ~2KB each vs ~1MB for platform threads"
echo "   • CPU: Carrier threads are the CPU resource bottleneck"
echo "   • I/O: Virtual threads excel at I/O-bound workloads"
echo "   • Synchronization: Use ReentrantLock instead of synchronized"
echo ""

echo "6. Running the Tests:"
echo "   • Use the run-test.sh script (requires Java)"
echo "   • Or run directly with Maven: mvn test -Dtest=SimpleVirtualThreadTest"
echo "   • Or run as standalone: java SimpleVirtualThreadTest"
echo ""

echo "Demo completed!"