package org.yawlfoundation.yawl.elements.predicate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.core.predicate.ICorePredicateEvaluator;

/**
 * Chicago TDD tests for PredicateEvaluator interface contract.
 * Tests the interface methods and inheritance from ICorePredicateEvaluator.
 */
@DisplayName("PredicateEvaluator Interface Tests")
@Tag("unit")
class TestPredicateEvaluator {

    /**
     * Minimal implementation of PredicateEvaluator for testing the interface contract.
     */
    private static class TestPredicateEvaluatorImpl implements PredicateEvaluator {

        @Override
        public boolean accept(String predicate) {
            return predicate != null && predicate.startsWith("test:");
        }

        @Override
        public String substituteDefaults(String predicate) {
            if (predicate == null) return null;
            return predicate.replace("${default}", "defaultValue");
        }

        @Override
        public String replace(org.yawlfoundation.yawl.elements.YDecomposition decomposition,
                              String predicate,
                              org.yawlfoundation.yawl.elements.state.YIdentifier token) {
            if (predicate == null) return null;
            return predicate.replace("${token}", token != null ? token.toString() : "null");
        }
    }

    private final PredicateEvaluator evaluator = new TestPredicateEvaluatorImpl();

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("PredicateEvaluator should extend ICorePredicateEvaluator")
        void extendsICorePredicateEvaluator() {
            assertTrue(evaluator instanceof ICorePredicateEvaluator);
        }

        @Test
        @DisplayName("Accept method should return boolean")
        void acceptReturnsBoolean() {
            assertTrue(evaluator.accept("test:something"));
            assertFalse(evaluator.accept("other:something"));
            assertFalse(evaluator.accept(null));
        }

        @Test
        @DisplayName("Substitute defaults should transform predicate")
        void substituteDefaultsTransformsPredicate() {
            String result = evaluator.substituteDefaults("value=${default}");
            assertEquals("value=defaultValue", result);
        }

        @Test
        @DisplayName("Substitute defaults should handle null")
        void substituteDefaultsHandlesNull() {
            assertNull(evaluator.substituteDefaults(null));
        }

        @Test
        @DisplayName("Substitute defaults should return unchanged if no defaults")
        void substituteDefaultsUnchangedIfNoDefaults() {
            String result = evaluator.substituteDefaults("no defaults here");
            assertEquals("no defaults here", result);
        }
    }

    @Nested
    @DisplayName("Accept Method Tests")
    class AcceptMethodTests {

        @Test
        @DisplayName("Accept should work with empty string")
        void acceptWorksWithEmptyString() {
            assertFalse(evaluator.accept(""));
        }

        @Test
        @DisplayName("Accept should be consistent for same input")
        void acceptIsConsistent() {
            String predicate = "test:valid";
            assertEquals(evaluator.accept(predicate), evaluator.accept(predicate));
        }
    }

    @Nested
    @DisplayName("Substitute Defaults Tests")
    class SubstituteDefaultsTests {

        @Test
        @DisplayName("Substitute defaults should replace multiple occurrences")
        void substituteDefaultsReplacesMultiple() {
            String result = evaluator.substituteDefaults("${default} and ${default}");
            assertEquals("defaultValue and defaultValue", result);
        }

        @Test
        @DisplayName("Substitute defaults should preserve other content")
        void substituteDefaultsPreservesOtherContent() {
            String result = evaluator.substituteDefaults("prefix/${default}/suffix");
            assertEquals("prefix/defaultValue/suffix", result);
        }
    }

    @Nested
    @DisplayName("Replace Method Tests")
    class ReplaceMethodTests {

        @Test
        @DisplayName("Replace should handle null decomposition")
        void replaceHandlesNullDecomposition() throws Exception {
            org.yawlfoundation.yawl.elements.state.YIdentifier id =
                new org.yawlfoundation.yawl.elements.state.YIdentifier("case1");
            String result = evaluator.replace(null, "${token}", id);
            assertEquals("case1", result);
        }

        @Test
        @DisplayName("Replace should handle null token")
        void replaceHandlesNullToken() {
            String result = evaluator.replace(null, "${token}", null);
            assertEquals("null", result);
        }

        @Test
        @DisplayName("Replace should handle null predicate")
        void replaceHandlesNullPredicate() throws Exception {
            org.yawlfoundation.yawl.elements.state.YIdentifier id =
                new org.yawlfoundation.yawl.elements.state.YIdentifier("case1");
            assertNull(evaluator.replace(null, null, id));
        }
    }

    @Nested
    @DisplayName("Type Hierarchy Tests")
    class TypeHierarchyTests {

        @Test
        @DisplayName("PredicateEvaluator should be assignable to ICorePredicateEvaluator")
        void assignableToICorePredicateEvaluator() {
            ICorePredicateEvaluator coreEvaluator = evaluator;
            assertNotNull(coreEvaluator);
        }

        @Test
        @DisplayName("Interface methods accept and substituteDefaults should be callable via ICorePredicateEvaluator reference")
        void callableViaCoreReference() {
            ICorePredicateEvaluator coreEvaluator = evaluator;
            assertTrue(coreEvaluator.accept("test:value"));
            assertEquals("defaultValue", coreEvaluator.substituteDefaults("${default}"));
        }
    }
}
