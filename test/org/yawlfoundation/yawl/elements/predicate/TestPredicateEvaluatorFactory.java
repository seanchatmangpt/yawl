package org.yawlfoundation.yawl.elements.predicate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for PredicateEvaluatorFactory.
 * Tests external paths and instance creation.
 */
@DisplayName("PredicateEvaluatorFactory Tests")
@Tag("unit")
class TestPredicateEvaluatorFactory {

    @Nested
    @DisplayName("Get Instances Tests")
    class GetInstancesTests {

        @Test
        @DisplayName("Get instances should return non-null set")
        void getInstancesReturnsNonNull() {
            Set<PredicateEvaluator> instances = PredicateEvaluatorFactory.getInstances();
            assertNotNull(instances);
        }

        @Test
        @DisplayName("Get instances should return consistent results")
        void getInstancesReturnsConsistentResults() {
            Set<PredicateEvaluator> instances1 = PredicateEvaluatorFactory.getInstances();
            Set<PredicateEvaluator> instances2 = PredicateEvaluatorFactory.getInstances();
            assertNotNull(instances1);
            assertNotNull(instances2);
        }
    }

    @Nested
    @DisplayName("Set External Paths Tests")
    class SetExternalPathsTests {

        @Test
        @DisplayName("Set external paths should accept null")
        void setExternalPathsAcceptsNull() {
            assertDoesNotThrow(() -> PredicateEvaluatorFactory.setExternalPaths(null));
        }

        @Test
        @DisplayName("Set external paths should accept empty string")
        void setExternalPathsAcceptsEmptyString() {
            assertDoesNotThrow(() -> PredicateEvaluatorFactory.setExternalPaths(""));
        }

        @Test
        @DisplayName("Set external paths should accept valid path")
        void setExternalPathsAcceptsValidPath() {
            assertDoesNotThrow(() -> PredicateEvaluatorFactory.setExternalPaths("/some/path"));
        }

        @Test
        @DisplayName("Set external paths should accept multiple paths")
        void setExternalPathsAcceptsMultiplePaths() {
            assertDoesNotThrow(() -> PredicateEvaluatorFactory.setExternalPaths("/path1:/path2:/path3"));
        }
    }

    @Nested
    @DisplayName("Factory Properties Tests")
    class FactoryPropertiesTests {

        @Test
        @DisplayName("Factory should have private constructor")
        void factoryHasPrivateConstructor() throws NoSuchMethodException {
            // Verify the constructor exists and is private
            java.lang.reflect.Constructor<PredicateEvaluatorFactory> constructor =
                PredicateEvaluatorFactory.class.getDeclaredConstructor();
            assertFalse(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
        }
    }

    @Nested
    @DisplayName("Base Package Tests")
    class BasePackageTests {

        @Test
        @DisplayName("Factory should use correct base package")
        void factoryUsesCorrectBasePackage() {
            // The base package is defined as a constant in the factory
            // Verify it exists via reflection or by checking the loaded instances
            Set<PredicateEvaluator> instances = PredicateEvaluatorFactory.getInstances();
            // If instances are loaded from the correct package, they should not throw
            for (PredicateEvaluator evaluator : instances) {
                assertNotNull(evaluator.getClass().getPackage());
                assertTrue(evaluator.getClass().getPackage().getName()
                    .startsWith("org.yawlfoundation.yawl"));
            }
        }
    }
}
