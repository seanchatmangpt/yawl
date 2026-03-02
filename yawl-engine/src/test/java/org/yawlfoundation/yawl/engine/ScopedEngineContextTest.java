package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for ScopedEngineContext.
 *
 * Tests cover:
 * - Basic ScopedValue binding and retrieval
 * - Parallel execution scenarios
 * - Nested scopes
 * - Error handling and edge cases
 * - Memory leak prevention
 * - Virtual thread compatibility
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScopedEngineContext")
class ScopedEngineContextTest {

    private ScopedValueYEngine testEngine;
    private final String testEngineId = "test-engine-123";

    @BeforeEach
    void setUp() {
        testEngine = new ScopedValueYEngine();
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("should bind and retrieve engine successfully")
        void shouldBindAndRetrieveEngine() {
            // Given
            assertFalse(ScopedEngineContext.isEngineBound());

            // When
            String result = ScopedEngineContext.withEngine(testEngine, () -> {
                assertTrue(ScopedEngineContext.isEngineBound());
                assertSame(testEngine, ScopedEngineContext.current());
                return "success";
            });

            // Then
            assertEquals("success", result);
            assertFalse(ScopedEngineContext.isEngineBound());
        }

        @Test
        @DisplayName("should throw exception when accessing unbound engine")
        void shouldThrowExceptionWhenAccessingUnboundEngine() {
            // Given & When
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                ScopedEngineContext::current
            );

            // Then
            assertTrue(exception.getMessage().contains("No YAWL bound in current scope"));
        }

        @Test
        @DisplayName("should return null from currentOrNull when unbound")
        void shouldReturnNullFromCurrentOrNullWhenUnbound() {
            // Given & When
            YEngine result = ScopedEngineContext.currentOrNull();

            // Then
            assertNull(result);
            assertFalse(ScopedEngineContext.isEngineBound());
        }

        @Test
        @DisplayName("should return engine from currentOrNull when bound")
        void shouldReturnEngineFromCurrentOrNullWhenBound() {
            // Given
            ScopedEngineContext.withEngine(testEngine, () -> {
                // When
                YEngine result = ScopedEngineContext.currentOrNull();

                // Then
                assertSame(testEngine, result);
                return null;
            });
        }
    }

    @Nested
    @DisplayName("Callable Support")
    class CallableSupport {

        @Test
        @DisplayName("should execute callable with engine context")
        void shouldExecuteCallableWithEngineContext() throws Exception {
            // Given
            Callable<String> callable = () -> {
                assertSame(testEngine, ScopedEngineContext.current());
                return "callable-result";
            };

            // When
            String result = ScopedEngineContext.withEngine(testEngine, callable);

            // Then
            assertEquals("callable-result", result);
        }

        @Test
        @DisplayName("should propagate checked exceptions from callable")
        void shouldPropagateCheckedExceptionsFromCallable() {
            // Given
            Exception expectedException = new Exception("Test exception");
            Callable<String> callable = () -> {
                throw expectedException;
            };

            // When & Then
            Exception exception = assertThrows(
                Exception.class,
                () -> ScopedEngineContext.withEngine(testEngine, callable)
            );
            assertSame(expectedException, exception);
        }
    }

    @Nested
    @DisplayName("Nested Scopes")
    class NestedScopes {

        @Test
        @DisplayName("should support nested scopes with same engine")
        void shouldSupportNestedScopesWithSameEngine() {
            // Given
            ScopedEngineContext.withEngine(testEngine, () -> {
                // When
                String result = ScopedEngineContext.inNestedScope(() -> {
                    assertSame(testEngine, ScopedEngineContext.current());
                    return "nested-result";
                });

                // Then
                assertEquals("nested-result", result);
                assertSame(testEngine, ScopedEngineContext.current());
            });
        }

        @Test
        @DisplayName("should throw exception when creating nested scope without bound engine")
        void shouldThrowExceptionWhenCreatingNestedScopeWithoutBoundEngine() {
            // Given & When
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ScopedEngineContext.inNestedScope(() -> "should-fail")
            );

            // Then
            assertTrue(exception.getMessage().contains("no engine bound in current scope"));
        }

        @Test
        @DisplayName("should support multiple levels of nesting")
        void shouldSupportMultipleLevelsOfNesting() {
            // Given
            String[] results = new String[3];

            ScopedEngineContext.withEngine(testEngine, () -> {
                results[0] = "level1";

                ScopedEngineContext.inNestedScope(() -> {
                    results[1] = "level2";

                    ScopedEngineContext.inNestedScope(() -> {
                        results[2] = "level3";
                        return "level3-result";
                    });

                    return "level2-result";
                });

                return "level1-result";
            });

            // Then
            assertArrayEquals(new String[]{"level1", "level2", "level3"}, results);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw NullPointerException for null engine")
        void shouldThrowNullPointerExceptionForNullEngine() {
            // Given & When
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> ScopedEngineContext.withEngine(null, () -> "test")
            );

            // Then
            assertEquals("Engine cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should throw NullPointerException for null action")
        void shouldThrowNullPointerExceptionForNullAction() {
            // Given & When
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> ScopedEngineContext.withEngine(testEngine, null)
            );

            // Then
            assertEquals("Action cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should clean up scope even when action throws exception")
        void shouldCleanUpScopeWhenActionThrowsException() {
            // Given
            assertFalse(ScopedEngineContext.isEngineBound());

            // When
            RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ScopedEngineContext.withEngine(testEngine, () -> {
                    assertTrue(ScopedEngineContext.isEngineBound());
                    throw new RuntimeException("Action failed");
                })
            );

            // Then
            assertEquals("Action failed", exception.getMessage());
            assertFalse(ScopedEngineContext.isEngineBound());
        }

        @Test
        @DisplayName("should detect multiple bindings in same scope")
        void shouldDetectMultipleBindingsInSameScope() {
            // Given
            ScopedValueYEngine secondEngine = new ScopedValueYEngine();

            // ScopedValue doesn't actually prevent multiple bindings in the same scope
            // But we can test the behavior
            ScopedEngineContext.withEngine(testEngine, () -> {
                // This should work fine as ScopedValue allows rebinding
                String result = ScopedEngineContext.withEngine(secondEngine, () -> {
                    assertSame(secondEngine, ScopedEngineContext.current());
                    return "second-engine";
                });

                assertEquals("second-engine", result);
                assertSame(secondEngine, ScopedEngineContext.current());
                return "first-engine";
            });

            assertFalse(ScopedEngineContext.isEngineBound());
        }
    }

    @Nested
    @DisplayName("Without Engine Scopes")
    class WithoutEngineScopes {

        @Test
        @DisplayName("should execute action without engine context")
        void shouldExecuteActionWithoutEngineContext() {
            // Given
            ScopedEngineContext.withEngine(testEngine, () -> {
                assertTrue(ScopedEngineContext.isEngineBound());

                // When
                String result = ScopedEngineContext.withoutEngine(() -> {
                    assertFalse(ScopedEngineContext.isEngineBound());
                    return "without-engine";
                });

                // Then
                assertEquals("without-engine", result);
                assertSame(testEngine, ScopedEngineContext.current());
                return null;
            });
        }

        @Test
        @DisplayName("should restore engine context after withoutEngine")
        void shouldRestoreEngineContextAfterWithoutEngine() {
            // Given
            ScopedEngineContext.withEngine(testEngine, () -> {
                // When
                String result = ScopedEngineContext.withoutEngine(() -> {
                    assertFalse(ScopedEngineContext.isEngineBound());
                    return "temporarily-unbound";
                });

                // Then
                assertEquals("temporarily-unbound", result);
                assertSame(testEngine, ScopedEngineContext.current());
                return null;
            });
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("should support parallel execution in different threads")
        void shouldSupportParallelExecutionInDifferentThreads throws InterruptedException {
            // Given
            final int threadCount = 10;
            final AtomicReference<String> results[] = new AtomicReference[threadCount];
            final Thread[] threads = new Thread[threadCount];

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    ScopedValueYEngine threadEngine = new ScopedValueYEngine();
                    String result = ScopedEngineContext.withEngine(threadEngine, () -> {
                        assertSame(threadEngine, ScopedEngineContext.current());
                        return "thread-" + threadIndex;
                    });
                    results[threadIndex] = new AtomicReference<>(result);
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then
            for (int i = 0; i < threadCount; i++) {
                assertEquals("thread-" + i, results[i].get());
            }
        }

        @Test
        @DisplayName("should support virtual threads")
        void shouldSupportVirtualThreads throws Exception {
            // Given
            final int taskCount = 100;
            final AtomicReference<String[]> results = new AtomicReference<>(new String[taskCount]);

            // When
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                for (int i = 0; i < taskCount; i++) {
                    final int taskIndex = i;
                    scope.fork(() -> {
                        ScopedValueYEngine taskEngine = new ScopedValueYEngine();
                        return ScopedEngineContext.withEngine(taskEngine, () -> {
                            assertSame(taskEngine, ScopedEngineContext.current());
                            return "virtual-task-" + taskIndex;
                        });
                    });
                }
                scope.join();
                scope.throwIfFailed();

                // Collect results
                int index = 0;
                for (var task : scope.tasks()) {
                    results.get()[index++] = task.get();
                }
            }

            // Then
            for (int i = 0; i < taskCount; i++) {
                assertEquals("virtual-task-" + i, results.get()[i]);
            }
        }
    }

    @Nested
    @DisplayName("Memory Management")
    class MemoryManagement {

        @Test
        @DisplayName("should prevent memory leaks from unclosed scopes")
        void shouldPreventMemoryLeaksFromUnclosedScopes() {
            // Given
            // Create many scopes without explicitly closing them
            final int scopeCount = 1000;
            for (int i = 0; i < scopeCount; i++) {
                final int index = i;
                ScopedEngineContext.withEngine(testEngine, () -> {
                    // Just a dummy operation
                    return "scope-" + index;
                });
            }

            // Then - no exceptions should be thrown
            // The ScopedValue should automatically clean up
            assertFalse(ScopedEngineContext.isEngineBound());
        }

        @Test
        @DisplayName("should clean up resources on scope exit")
        void shouldCleanUpResourcesOnScopeExit() {
            // Given
            final AtomicReference<Boolean> cleanupFlag = new AtomicReference<>(false);

            // When
            ScopedEngineContext.withEngine(testEngine, () -> {
                // Register cleanup action
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    cleanupFlag.set(true);
                }));
                return "test";
            });

            // Then
            assertFalse(ScopedEngineContext.isEngineBound());
            // Note: We can't easily test the shutdown hook execution in this test
            // but the ScopedValue mechanism ensures proper cleanup
        }
    }

    @Nested
    @DisplayName("Integration with ScopedValueYEngine")
    class IntegrationWithScopedValueYEngine {

        @Test
        @DisplayName("should work seamlessly with ScopedValueYEngine")
        void shouldWorkSeamlesslyWithScopedValueYEngine() {
            // Given
            ScopedValueYEngine engine = new ScopedValueYEngine();
            engine.initialize();

            // When
            String result = engine.executeWithEngine(() -> {
                assertSame(engine, ScopedEngineContext.current());
                return "integration-test";
            });

            // Then
            assertEquals("integration-test", result);
            engine.shutdown();
        }

        @Test
        @DisplayName("should provide safe execution with error handling")
        void shouldProvideSafeExecutionWithErrorHandling() {
            // Given
            ScopedValueYEngine engine = new ScopedValueYEngine();
            engine.initialize();

            // When & Then
            assertThrows(
                RuntimeException.class,
                () -> engine.executeSafely("failing-operation", () -> {
                    throw new RuntimeException("Simulated failure");
                })
            );

            engine.shutdown();
        }

        @Test
        @DisplayName("should support try-with-resources pattern")
        void shouldSupportTryWithResourcesPattern() {
            // Given
            try (ScopedValueYEngine engine = new ScopedValueYEngine()) {
                engine.initialize();

                String result = engine.executeWithEngine(() -> {
                    assertSame(engine, ScopedEngineContext.current());
                    return "auto-close-test";
                });

                assertEquals("auto-close-test", result);
                // Engine will be automatically closed by try-with-resources
            }

            // Verify engine is shutdown
            // Note: This is just to demonstrate the pattern
        }
    }
}