package org.yawlfoundation.yawl.elements.predicate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for PredicateEvaluatorCache.
 * Tests static API and caching behavior.
 * Uses real PredicateEvaluator instances.
 */
@DisplayName("PredicateEvaluatorCache Tests")
@Tag("unit")
class TestPredicateEvaluatorCache {

    @Nested
    @DisplayName("Substitute Method Tests")
    class SubstituteMethodTests {

        @Test
        @DisplayName("Substitute should handle null predicate")
        void substituteHandlesNull() {
            String result = PredicateEvaluatorCache.substitute(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Substitute should return unchanged predicate when no substitutions")
        void substituteReturnsUnchangedWhenNoSubstitutions() {
            String predicate = "simple predicate without substitutions";
            String result = PredicateEvaluatorCache.substitute(predicate);
            assertEquals(predicate, result);
        }

        @Test
        @DisplayName("Substitute should handle empty string")
        void substituteHandlesEmptyString() {
            String result = PredicateEvaluatorCache.substitute("");
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Accept Method Tests")
    class AcceptMethodTests {

        @Test
        @DisplayName("Accept should handle null predicate")
        void acceptHandlesNull() {
            boolean result = PredicateEvaluatorCache.accept(null);
            assertFalse(result);
        }

        @Test
        @DisplayName("Accept should return false for unrecognized predicate")
        void acceptReturnsFalseForUnrecognized() {
            boolean result = PredicateEvaluatorCache.accept("unknown_predicate_format");
            assertFalse(result);
        }

        @Test
        @DisplayName("Accept should handle empty string")
        void acceptHandlesEmptyString() {
            boolean result = PredicateEvaluatorCache.accept("");
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Process Method Tests")
    class ProcessMethodTests {

        @Test
        @DisplayName("Process should handle null predicate")
        void processHandlesNull() {
            String result = PredicateEvaluatorCache.process(null, null, null);
            assertNull(result);
        }

        @Test
        @DisplayName("Process should return unchanged predicate when no evaluators accept")
        void processReturnsUnchangedWhenNoEvaluatorsAccept() {
            String predicate = "plain text without special format";
            String result = PredicateEvaluatorCache.process(null, predicate, null);
            assertEquals(predicate, result);
        }

        @Test
        @DisplayName("Process should handle empty string")
        void processHandlesEmptyString() {
            String result = PredicateEvaluatorCache.process(null, "", null);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Cache Instance Tests")
    class CacheInstanceTests {

        @Test
        @DisplayName("Cache should extend YCorePredicateEvaluatorCache")
        void cacheExtendsCoreCache() {
            // PredicateEvaluatorCache has private constructor, use static methods only
            // Verify it extends the core class via class hierarchy
            assertTrue(org.yawlfoundation.yawl.engine.core.predicate.YCorePredicateEvaluatorCache.class
                .isAssignableFrom(PredicateEvaluatorCache.class));
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Multiple concurrent substitute calls should not throw")
        void concurrentSubstituteCallsDoNotThrow() throws InterruptedException {
            Thread[] threads = new Thread[10];
            final boolean[] success = {true};

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        PredicateEvaluatorCache.substitute("test predicate " + Thread.currentThread().getId());
                    } catch (Exception e) {
                        success[0] = false;
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertTrue(success[0]);
        }

        @Test
        @DisplayName("Multiple concurrent accept calls should not throw")
        void concurrentAcceptCallsDoNotThrow() throws InterruptedException {
            Thread[] threads = new Thread[10];
            final boolean[] success = {true};

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        PredicateEvaluatorCache.accept("test predicate " + Thread.currentThread().getId());
                    } catch (Exception e) {
                        success[0] = false;
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertTrue(success[0]);
        }
    }
}
