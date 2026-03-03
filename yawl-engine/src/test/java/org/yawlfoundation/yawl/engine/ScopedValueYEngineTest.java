package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for ScopedValueYEngine class.
 *
 * Tests cover:
 * - Engine lifecycle management
 * - State transitions
 * - Context execution methods
 * - Error handling
 * - AutoCloseable functionality
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScopedValueYEngine")
class ScopedValueYEngineTest {

    private ScopedValueYEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ScopedValueYEngine();
    }

    @Nested
    @DisplayName("Engine Lifecycle")
    class EngineLifecycle {

        @Test
        @DisplayName("should start in CREATED state")
        void shouldStartInCreatedState() {
            assertEquals(ScopedValueYEngine.State.CREATED, engine.getState());
        }

        @Test
        @DisplayName("should initialize successfully")
        void shouldInitializeSuccessfully() {
            // When
            engine.initialize();

            // Then
            assertEquals(ScopedValueYEngine.State.RUNNING, engine.getState());
            assertTrue(engine.isRunning());
            assertFalse(engine.isShutdown());
        }

        @Test
        @DisplayName("should throw exception when initializing twice")
        void shouldThrowExceptionWhenInitializingTwice() {
            // Given
            engine.initialize();

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                engine::initialize
            );

            assertEquals("Cannot initialize engine in state: RUNNING", exception.getMessage());
        }

        @Test
        @DisplayName("should shutdown successfully")
        void shouldShutdownSuccessfully() {
            // Given
            engine.initialize();

            // When
            engine.shutdown();

            // Then
            assertEquals(ScopedValueYEngine.State.SHUTDOWN, engine.getState());
            assertFalse(engine.isRunning());
            assertTrue(engine.isShutdown());
        }

        @Test
        @DisplayName("should shutdown idempotently")
        void shouldShutdownIdempotently() {
            // Given
            engine.initialize();
            engine.shutdown();

            // When
            engine.shutdown(); // Second shutdown

            // Then
            assertEquals(ScopedValueYEngine.State.SHUTDOWN, engine.getState());
            // Should not throw exception
        }
    }

    @Nested
    @DisplayName("Context Execution")
    class ContextExecution {

        @Test
        @DisplayName("should execute action with engine context")
        void shouldExecuteActionWithEngineContext() {
            // Given
            engine.initialize();

            // When
            String result = engine.executeWithEngine(() -> {
                assertSame(engine, ScopedEngineContext.current());
                return "context-test";
            });

            // Then
            assertEquals("context-test", result);
        }

        @Test
        @DisplayName("should execute callable with engine context")
        void shouldExecuteCallableWithEngineContext() throws Exception {
            // Given
            engine.initialize();

            Callable<String> callable = () -> {
                assertSame(engine, ScopedEngineContext.current());
                return "callable-test";
            };

            // When
            String result = engine.executeWithEngine(callable);

            // Then
            assertEquals("callable-test", result);
        }

        @Test
        @DisplayName("should throw exception when executing on uninitialized engine")
        void shouldThrowExceptionWhenExecutingOnUninitializedEngine() {
            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> engine.executeWithEngine(() -> "test")
            );

            assertEquals("Engine is not running. Current state: CREATED", exception.getMessage());
        }

        @Test
        @DisplayName("should throw exception when executing on shutdown engine")
        void shouldThrowExceptionWhenExecutingOnShutdownEngine() {
            // Given
            engine.initialize();
            engine.shutdown();

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> engine.executeWithEngine(() -> "test")
            );

            assertEquals("Engine is not running. Current state: SHUTDOWN", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Safe Execution")
    class SafeExecution {

        @Test
        @DisplayName("should execute safely with error handling")
        void shouldExecuteSafelyWithErrorHandling() {
            // Given
            engine.initialize();

            // When
            assertThrows(
                RuntimeException.class,
                () -> engine.executeSafely("failing-task", () -> {
                    throw new RuntimeException("Simulated error");
                })
            );

            // Engine should still be in running state
            assertTrue(engine.isRunning());
        }

        @Test
        @DisplayName("should execute successfully with safe execution")
        void shouldExecuteSuccessfullyWithSafeExecution() {
            // Given
            engine.initialize();

            // When
            String result = engine.executeSafely("successful-task", () -> {
                assertSame(engine, ScopedEngineContext.current());
                return "safe-execution-result";
            });

            // Then
            assertEquals("safe-execution-result", result);
        }
    }

    @Nested
    @DisplayName("Contextual Operations")
    class ContextualOperations {

        @Test
        @DisplayName("should execute with context and input")
        void shouldExecuteWithContextAndInput() {
            // Given
            engine.initialize();

            String input = "test-input";
            String result = engine.executeWithContext(input, (inputValue) -> {
                assertSame(input, inputValue);
                assertSame(engine, ScopedEngineContext.current());
                return "processed-" + inputValue;
            });

            assertEquals("processed-test-input", result);
        }

        @Test
        @DisplayName("should execute consumer with context")
        void shouldExecuteConsumerWithContext() {
            // Given
            engine.initialize();
            AtomicInteger counter = new AtomicInteger(0);

            // When
            engine.executeWithContext("test-data", (data) -> {
                assertEquals("test-data", data);
                assertSame(engine, ScopedEngineContext.current());
                counter.incrementAndGet();
            });

            // Then
            assertEquals(1, counter.get());
        }
    }

    @Nested
    @DisplayName("AutoCloseable")
    class AutoCloseable {

        @Test
        @DisplayName("should close with try-with-resources")
        void shouldCloseWithTryWithResources() {
            // Given
            try (ScopedValueYEngine testEngine = new ScopedValueYEngine()) {
                testEngine.initialize();

                // Verify it's running
                assertTrue(testEngine.isRunning());

                // Execute with context
                String result = testEngine.executeWithEngine(() -> {
                    assertSame(testEngine, ScopedEngineContext.current());
                    return "auto-close-test";
                });

                assertEquals("auto-close-test", result);
            }

            // Engine should be shutdown
            assertTrue(testEngine.isShutdown());
        }

        @Test
        @DisplayName("should not close multiple times")
        void shouldNotCloseMultipleTimes() {
            // Given
            engine.initialize();
            engine.shutdown();

            // When - second close
            assertDoesNotThrow(engine::close);

            // Then - should still be shutdown
            assertTrue(engine.isShutdown());
        }
    }

    @Nested
    @DisplayName("Engine Properties")
    class EngineProperties {

        @Test
        @DisplayName("should generate unique engine IDs")
        void shouldGenerateUniqueEngineIDs() {
            // Given
            ScopedValueYEngine engine2 = new ScopedValueYEngine();

            // When & Then
            assertNotEquals(engine.getEngineId(), engine2.getEngineId());
            assertFalse(engine.getEngineId().equals(engine2.getEngineId()));
        }

        @Test
        @DisplayName("should have creation timestamp")
        void shouldHaveCreationTimestamp() {
            // Given
            long beforeCreation = System.currentTimeMillis();
            ScopedValueYEngine newEngine = new ScopedValueYEngine();
            long afterCreation = System.currentTimeMillis();

            // When & Then
            long creationTime = newEngine.getCreationTimestamp();
            assertTrue(creationTime >= beforeCreation);
            assertTrue(creationTime <= afterCreation);
        }

        @Test
        @DisplayName("should provide string representation")
        void shouldProvideStringRepresentation() {
            // When
            String representation = engine.toString();

            // Then
            assertTrue(representation.contains(engine.getEngineId()));
            assertTrue(representation.contains("CREATED"));
        }

        @Test
        @DisplayName("should implement equals and hashCode consistently")
        void shouldImplementEqualsAndHashCodeConsistently() {
            // Given
            ScopedValueYEngine engine2 = new ScopedValueYEngine();

            // When & Then
            assertNotEquals(engine, engine2);
            assertNotEquals(engine.hashCode(), engine2.hashCode());

            // Same engine should equal itself
            assertEquals(engine, engine);
            assertEquals(engine.hashCode(), engine.hashCode());
        }
    }

    @Nested
    @DisplayName("Error Scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("should handle null action in executeWithContext")
        void shouldHandleNullActionInExecuteWithContext() {
            // Given
            engine.initialize();

            // When & Then
            assertThrows(
                NullPointerException.class,
                () -> engine.executeWithContext("test", null)
            );
        }

        @Test
        @DisplayName("should handle null function in executeWithContext")
        void shouldHandleNullFunctionInExecuteWithContext() {
            // Given
            engine.initialize();

            // When & Then
            assertThrows(
                NullPointerException.class,
                () -> engine.executeWithContext("test", (Function<String, String>) null)
            );
        }

        @Test
        @DisplayName("should handle null consumer in executeWithContext")
        void shouldHandleNullConsumerInExecuteWithContext() {
            // Given
            engine.initialize();

            // When & Then
            assertThrows(
                NullPointerException.class,
                () -> engine.executeWithContext("test", (Consumer<String>) null)
            );
        }

        @Test
        @DisplayName("should handle null action name in executeSafely")
        void shouldHandleNullActionNameInExecuteSafely() {
            // Given
            engine.initialize();

            // When & Then
            assertThrows(
                NullPointerException.class,
                () -> engine.executeSafely(null, () -> "test")
            );
        }
    }
}