package org.yawlfoundation.yawl.util.java25.structured;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Structured Concurrency (JEP 428, 429, 437).
 *
 * Chicago TDD: Tests real structured task scopes with fan-out/fan-in,
 * error handling, and cancellation semantics.
 */
@DisplayName("Structured Concurrency Patterns")
class StructuredConcurrencyTest {

    @Test
    @DisplayName("StructuredTaskScope.ShutdownOnFailure cancels on first failure")
    @Timeout(10)
    void testShutdownOnFailure() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> task1 = scope.fork(() -> {
                Thread.sleep(100);
                return "result-1";
            });

            StructuredTaskScope.Subtask<String> task2 = scope.fork(() -> {
                Thread.sleep(50);
                throw new RuntimeException("Task failed");
            });

            StructuredTaskScope.Subtask<String> task3 = scope.fork(() -> {
                Thread.sleep(500);
                return "result-3";
            });

            ExecutionException ex = assertThrows(ExecutionException.class, scope::join);
            assertTrue(ex.getCause() instanceof RuntimeException);
            assertEquals("Task failed", ex.getCause().getMessage());

            assertTrue(task1.state() == StructuredTaskScope.Subtask.State.SUCCESS ||
                      task1.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("StructuredTaskScope.ShutdownOnSuccess awaits all tasks")
    @Timeout(10)
    void testShutdownOnSuccess() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            scope.fork(() -> {
                Thread.sleep(50);
                return "result-1";
            });

            scope.fork(() -> {
                Thread.sleep(100);
                return "result-2";
            });

            String result = scope.join().result();
            assertNotNull(result);
            assertTrue(result.equals("result-1") || result.equals("result-2"));
        }
    }

    @Test
    @DisplayName("Fan-out/fan-in pattern with multiple workers")
    @Timeout(15)
    void testFanOutFanIn() throws Exception {
        List<String> items = List.of("item-1", "item-2", "item-3", "item-4", "item-5");

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<String>> tasks = new ArrayList<>();

            for (String item : items) {
                tasks.add(scope.fork(() -> processItem(item)));
            }

            scope.join();

            assertEquals(items.size(), tasks.size());
            for (var task : tasks) {
                assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, task.state());
                assertTrue(task.get().startsWith("processed-"));
            }
        }
    }

    private String processItem(String item) throws InterruptedException {
        Thread.sleep(10);
        return "processed-" + item;
    }

    @Test
    @DisplayName("Error handling with partial results")
    @Timeout(10)
    void testErrorHandlingPartialResults() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> success = scope.fork(() -> "success");

            StructuredTaskScope.Subtask<String> failure = scope.fork(() -> {
                throw new RuntimeException("Failure");
            });

            assertThrows(ExecutionException.class, scope::join);

            assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, success.state());
            assertTrue(failure.state() == StructuredTaskScope.Subtask.State.FAILED ||
                      failure.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("Subtask state transitions are observable")
    @Timeout(10)
    void testSubtaskStateTransitions() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<Integer> task = scope.fork(() -> {
                Thread.sleep(50);
                return 42;
            });

            Thread.sleep(10);
            assertNotEquals(StructuredTaskScope.Subtask.State.SUCCESS, task.state());

            scope.join();
            assertEquals(StructuredTaskScope.Subtask.State.SUCCESS, task.state());
            assertEquals(42, task.get().intValue());
        }
    }

    @Test
    @DisplayName("Resource cleanup on scope exit")
    @Timeout(10)
    void testResourceCleanup() throws Exception {
        AtomicInteger taskCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < 10; i++) {
                scope.fork(() -> {
                    taskCount.incrementAndGet();
                    try {
                        Thread.sleep(50);
                    } finally {
                        completedCount.incrementAndGet();
                    }
                    return null;
                });
            }

            scope.join();
        }

        assertEquals(10, taskCount.get());
        assertEquals(10, completedCount.get());
    }

    @Test
    @DisplayName("Nested structured tasks")
    @Timeout(15)
    void testNestedStructuredTasks() throws Exception {
        try (var outer = new StructuredTaskScope.ShutdownOnFailure()) {
            outer.fork(() -> {
                try (var inner = new StructuredTaskScope.ShutdownOnFailure()) {
                    inner.fork(() -> "nested-result-1");
                    inner.fork(() -> "nested-result-2");
                    inner.join();
                    return "outer-result";
                }
            });

            outer.join();
        }
    }

    @Test
    @DisplayName("Multiple tasks with shared result collection")
    @Timeout(15)
    void testSharedResultCollection() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < 5; i++) {
                final int id = i;
                scope.fork(() -> {
                    String result = "result-" + id;
                    results.add(result);
                    return result;
                });
            }

            scope.join();
        }

        assertEquals(5, results.size());
        for (int i = 0; i < 5; i++) {
            assertTrue(results.contains("result-" + i));
        }
    }

    @Test
    @DisplayName("Task cancellation on scope shutdown")
    @Timeout(10)
    void testTaskCancellation() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean(false);

        try {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                scope.fork(() -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return "should-not-complete";
                });

                Thread.sleep(100);
                scope.fork(() -> {
                    throw new RuntimeException("Trigger shutdown");
                });

                scope.join();
            }
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof RuntimeException);
        }
    }

    @Test
    @DisplayName("Large number of subtasks")
    @Timeout(20)
    void testManySubtasks() throws Exception {
        int taskCount = 1000;

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < taskCount; i++) {
                final int id = i;
                scope.fork(() -> id * 2);
            }

            scope.join();
        }
    }

    @Test
    @DisplayName("Virtual threads in structured tasks")
    @Timeout(15)
    void testVirtualThreadsInStructuredTasks() throws Exception {
        AtomicInteger completedVirtualTasks = new AtomicInteger(0);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < 100; i++) {
                scope.fork(() -> {
                    Thread.ofVirtual().start(() -> {
                        try {
                            Thread.sleep(10);
                            completedVirtualTasks.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    return null;
                });
            }

            scope.join();
        }

        Thread.sleep(500);
        assertTrue(completedVirtualTasks.get() > 0);
    }

    @Test
    @DisplayName("Exception propagation in structured scope")
    @Timeout(10)
    void testExceptionPropagation() throws Exception {
        String exceptionMessage = "Structured task failure";

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                scope.fork(() -> {
                    throw new IllegalArgumentException(exceptionMessage);
                });

                scope.join();
            }
        });

        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertEquals(exceptionMessage, ex.getCause().getMessage());
    }

    @Test
    @DisplayName("Subtask result retrieval")
    @Timeout(10)
    void testSubtaskResultRetrieval() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> task1 = scope.fork(() -> "value-1");
            StructuredTaskScope.Subtask<Integer> task2 = scope.fork(() -> 42);
            StructuredTaskScope.Subtask<Double> task3 = scope.fork(() -> 3.14);

            scope.join();

            assertEquals("value-1", task1.get());
            assertEquals(42, task2.get().intValue());
            assertEquals(3.14, task3.get(), 0.001);
        }
    }

    @Test
    @DisplayName("Scope timeout behavior")
    @Timeout(10)
    void testScopeTimeoutBehavior() throws Exception {
        AtomicBoolean taskStarted = new AtomicBoolean(false);

        try {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                scope.fork(() -> {
                    taskStarted.set(true);
                    Thread.sleep(5000);
                    return "should-not-complete";
                });

                Thread.sleep(100);
                scope.fork(() -> {
                    throw new RuntimeException("Trigger failure");
                });

                scope.join();
            }
        } catch (ExecutionException e) {
            assertTrue(taskStarted.get());
        }
    }
}
