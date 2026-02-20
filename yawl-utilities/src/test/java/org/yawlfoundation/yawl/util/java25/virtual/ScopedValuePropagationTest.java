package org.yawlfoundation.yawl.util.java25.virtual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScopedValue context propagation in virtual threads.
 *
 * Chicago TDD: Tests real context binding and propagation across
 * virtual thread boundaries.
 */
@DisplayName("ScopedValue Context Propagation")
class ScopedValuePropagationTest {

    static class WorkflowContext {
        private final String caseId;
        private final String userId;
        private final long timestamp;

        public WorkflowContext(String caseId, String userId, long timestamp) {
            this.caseId = caseId;
            this.userId = userId;
            this.timestamp = timestamp;
        }

        public String getCaseId() {
            return caseId;
        }

        public String getUserId() {
            return userId;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private static final ScopedValue<WorkflowContext> WORKFLOW_CONTEXT = ScopedValue.newInstance();
    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
    private static final ScopedValue<Long> TIMEOUT = ScopedValue.newInstance();

    @Test
    @DisplayName("ScopedValue stores and retrieves values")
    @Timeout(10)
    void testScopedValueBasicUsage() {
        WorkflowContext context = new WorkflowContext("case-001", "user1", System.currentTimeMillis());

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
            WorkflowContext retrieved = WORKFLOW_CONTEXT.get();
            assertEquals("case-001", retrieved.getCaseId());
            assertEquals("user1", retrieved.getUserId());
        });
    }

    @Test
    @DisplayName("ScopedValue is inaccessible outside scope")
    void testScopedValueOutOfScope() {
        assertThrows(NoSuchElementException.class, () -> {
            WORKFLOW_CONTEXT.get();
        });
    }

    @Test
    @DisplayName("ScopedValue propagates to virtual threads")
    @Timeout(10)
    void testScopedValueInVirtualThreads() throws InterruptedException {
        WorkflowContext context = new WorkflowContext("case-002", "user2", System.currentTimeMillis());
        AtomicReference<String> childCaseId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
            Thread.ofVirtual()
                .start(() -> {
                    try {
                        WorkflowContext childContext = WORKFLOW_CONTEXT.get();
                        childCaseId.set(childContext.getCaseId());
                    } finally {
                        latch.countDown();
                    }
                });
        });

        latch.await();
        assertEquals("case-002", childCaseId.get());
    }

    @Test
    @DisplayName("Multiple ScopedValues can be bound simultaneously")
    @Timeout(10)
    void testMultipleScopedValues() {
        WorkflowContext context = new WorkflowContext("case-003", "user3", System.currentTimeMillis());
        String requestId = "req-12345";
        Long timeout = 30000L;

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
            ScopedValue.callWhere(REQUEST_ID, requestId, () -> {
                ScopedValue.callWhere(TIMEOUT, timeout, () -> {
                    assertEquals("case-003", WORKFLOW_CONTEXT.get().getCaseId());
                    assertEquals("req-12345", REQUEST_ID.get());
                    assertEquals(30000L, TIMEOUT.get().longValue());
                });
            });
        });
    }

    @Test
    @DisplayName("ScopedValue changes are isolated between threads")
    @Timeout(10)
    void testScopedValueIsolation() throws InterruptedException {
        WorkflowContext context1 = new WorkflowContext("case-1", "user1", 1000L);
        WorkflowContext context2 = new WorkflowContext("case-2", "user2", 2000L);

        AtomicReference<String> thread1CaseId = new AtomicReference<>();
        AtomicReference<String> thread2CaseId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = Thread.ofVirtual()
            .start(() -> {
                ScopedValue.callWhere(WORKFLOW_CONTEXT, context1, () -> {
                    try {
                        Thread.sleep(100);
                        thread1CaseId.set(WORKFLOW_CONTEXT.get().getCaseId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            });

        Thread t2 = Thread.ofVirtual()
            .start(() -> {
                ScopedValue.callWhere(WORKFLOW_CONTEXT, context2, () -> {
                    try {
                        Thread.sleep(100);
                        thread2CaseId.set(WORKFLOW_CONTEXT.get().getCaseId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            });

        latch.await();
        assertEquals("case-1", thread1CaseId.get());
        assertEquals("case-2", thread2CaseId.get());
    }

    @Test
    @DisplayName("Nested ScopedValue scopes")
    @Timeout(10)
    void testNestedScopedValues() throws InterruptedException {
        WorkflowContext outer = new WorkflowContext("outer-case", "outer-user", 1000L);
        WorkflowContext inner = new WorkflowContext("inner-case", "inner-user", 2000L);

        AtomicReference<String> outerResult = new AtomicReference<>();
        AtomicReference<String> innerResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ScopedValue.callWhere(WORKFLOW_CONTEXT, outer, () -> {
            outerResult.set(WORKFLOW_CONTEXT.get().getCaseId());

            ScopedValue.callWhere(WORKFLOW_CONTEXT, inner, () -> {
                innerResult.set(WORKFLOW_CONTEXT.get().getCaseId());

                assertEquals("inner-case", WORKFLOW_CONTEXT.get().getCaseId());
            });

            assertEquals("outer-case", WORKFLOW_CONTEXT.get().getCaseId());
            latch.countDown();
        });

        latch.await();
        assertEquals("outer-case", outerResult.get());
        assertEquals("inner-case", innerResult.get());
    }

    @Test
    @DisplayName("ScopedValue with executor service")
    @Timeout(15)
    void testScopedValueWithExecutor() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        WorkflowContext context = new WorkflowContext("case-executor", "user-executor", System.currentTimeMillis());

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(5);

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        WorkflowContext threadContext = WORKFLOW_CONTEXT.get();
                        results.add(threadContext.getCaseId());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();
        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(cid -> "case-executor".equals(cid)));

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("ScopedValue immutability")
    @Timeout(10)
    void testScopedValueImmutability() {
        WorkflowContext original = new WorkflowContext("case-immut", "user-immut", 1000L);

        ScopedValue.callWhere(WORKFLOW_CONTEXT, original, () -> {
            WorkflowContext retrieved = WORKFLOW_CONTEXT.get();
            assertEquals(original.getCaseId(), retrieved.getCaseId());
            assertEquals(original.getUserId(), retrieved.getUserId());
            assertEquals(original.getTimestamp(), retrieved.getTimestamp());
        });
    }

    @Test
    @DisplayName("ScopedValue with different value types")
    @Timeout(10)
    void testScopedValueDifferentTypes() {
        String requestId = "req-999";
        Long timeout = 60000L;

        ScopedValue.callWhere(REQUEST_ID, requestId, () -> {
            ScopedValue.callWhere(TIMEOUT, timeout, () -> {
                assertEquals("req-999", REQUEST_ID.get());
                assertEquals(60000L, TIMEOUT.get().longValue());
                assertEquals("req-999".length(), REQUEST_ID.get().length());
                assertTrue(TIMEOUT.get() > 0);
            });
        });
    }

    @Test
    @DisplayName("ScopedValue across multiple virtual thread layers")
    @Timeout(15)
    void testScopedValueMultipleLayers() throws InterruptedException {
        WorkflowContext context = new WorkflowContext("case-layers", "user-layers", System.currentTimeMillis());
        AtomicInteger layerCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
            layerCount.incrementAndGet();

            Thread.ofVirtual()
                .start(() -> {
                    assertEquals("case-layers", WORKFLOW_CONTEXT.get().getCaseId());
                    layerCount.incrementAndGet();

                    try {
                        var future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                            assertEquals("case-layers", WORKFLOW_CONTEXT.get().getCaseId());
                            layerCount.incrementAndGet();
                        });
                        future.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        fail("Should propagate context through executor: " + e);
                    } finally {
                        latch.countDown();
                    }
                });
        });

        latch.await();
        assertEquals(3, layerCount.get());
    }

    @Test
    @DisplayName("ScopedValue exception handling")
    @Timeout(10)
    void testScopedValueExceptionHandling() {
        WorkflowContext context = new WorkflowContext("case-exc", "user-exc", System.currentTimeMillis());

        assertThrows(RuntimeException.class, () -> {
            ScopedValue.callWhere(WORKFLOW_CONTEXT, context, () -> {
                assertEquals("case-exc", WORKFLOW_CONTEXT.get().getCaseId());
                throw new RuntimeException("Test exception");
            });
        });
    }

    @Test
    @DisplayName("ScopedValue cleanup after exception")
    @Timeout(10)
    void testScopedValueCleanupAfterException() {
        WorkflowContext context1 = new WorkflowContext("case-1", "user-1", 1000L);
        WorkflowContext context2 = new WorkflowContext("case-2", "user-2", 2000L);

        try {
            ScopedValue.callWhere(WORKFLOW_CONTEXT, context1, () -> {
                assertEquals("case-1", WORKFLOW_CONTEXT.get().getCaseId());
                throw new RuntimeException("Test");
            });
        } catch (RuntimeException e) {
            // Expected
        }

        ScopedValue.callWhere(WORKFLOW_CONTEXT, context2, () -> {
            assertEquals("case-2", WORKFLOW_CONTEXT.get().getCaseId());
        });
    }
}
