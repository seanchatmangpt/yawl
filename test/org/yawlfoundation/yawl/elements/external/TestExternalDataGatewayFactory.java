package org.yawlfoundation.yawl.elements.external;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.data.external.ExternalDataGateway;
import org.yawlfoundation.yawl.elements.data.external.ExternalDataGatewayFactory;

/**
 * Chicago TDD tests for ExternalDataGatewayFactory.
 * Tests expression parsing and instance creation.
 */
@DisplayName("ExternalDataGatewayFactory Tests")
@Tag("unit")
class TestExternalDataGatewayFactory {

    @Nested
    @DisplayName("Expression Detection Tests")
    class ExpressionDetectionTests {

        @Test
        @DisplayName("IsExternalDataMappingExpression returns true for valid expression")
        void isExternalDataMappingExpressionReturnsTrueForValid() {
            assertTrue(ExternalDataGatewayFactory.isExternalDataMappingExpression(
                "#external:HibernateEngine"));
        }

        @Test
        @DisplayName("IsExternalDataMappingExpression returns false for non-external expression")
        void isExternalDataMappingExpressionReturnsFalseForNonExternal() {
            assertFalse(ExternalDataGatewayFactory.isExternalDataMappingExpression(
                "some regular expression"));
        }

        @Test
        @DisplayName("IsExternalDataMappingExpression returns false for null")
        void isExternalDataMappingExpressionReturnsFalseForNull() {
            assertFalse(ExternalDataGatewayFactory.isExternalDataMappingExpression(null));
        }

        @Test
        @DisplayName("IsExternalDataMappingExpression returns false for empty string")
        void isExternalDataMappingExpressionReturnsFalseForEmpty() {
            assertFalse(ExternalDataGatewayFactory.isExternalDataMappingExpression(""));
        }
    }

    @Nested
    @DisplayName("Class Extraction Tests")
    class ClassExtractionTests {

        @Test
        @DisplayName("GetMappingClassFromExpression extracts class name")
        void getMappingClassFromExpressionExtractsClassName() {
            String className = ExternalDataGatewayFactory.getMappingClassFromExpression(
                "#external:HibernateEngine");
            assertEquals("HibernateEngine", className);
        }

        @Test
        @DisplayName("GetMappingClassFromExpression returns null for null input")
        void getMappingClassFromExpressionReturnsNullForNull() {
            assertNull(ExternalDataGatewayFactory.getMappingClassFromExpression(null));
        }

        @Test
        @DisplayName("GetMappingClassFromExpression handles complex expressions")
        void getMappingClassFromExpressionHandlesComplexExpressions() {
            String className = ExternalDataGatewayFactory.getMappingClassFromExpression(
                "#external:SomeComplexClassName");
            assertEquals("SomeComplexClassName", className);
        }
    }

    @Nested
    @DisplayName("Base Package Tests")
    class BasePackageTests {

        @Test
        @DisplayName("GetBasePackage returns correct package")
        void getBasePackageReturnsCorrectPackage() {
            assertEquals("org.yawlfoundation.yawl.elements.data.external.",
                ExternalDataGatewayFactory.getBasePackage());
        }
    }

    @Nested
    @DisplayName("External Paths Tests")
    class ExternalPathsTests {

        @Test
        @DisplayName("SetExternalPaths accepts null")
        void setExternalPathsAcceptsNull() {
            assertDoesNotThrow(() -> ExternalDataGatewayFactory.setExternalPaths(null));
        }

        @Test
        @DisplayName("SetExternalPaths accepts empty string")
        void setExternalPathsAcceptsEmptyString() {
            assertDoesNotThrow(() -> ExternalDataGatewayFactory.setExternalPaths(""));
        }

        @Test
        @DisplayName("SetExternalPaths accepts valid path")
        void setExternalPathsAcceptsValidPath() {
            assertDoesNotThrow(() -> ExternalDataGatewayFactory.setExternalPaths("/custom/path"));
        }

        @Test
        @DisplayName("SetExternalPaths accepts multiple paths")
        void setExternalPathsAcceptsMultiplePaths() {
            assertDoesNotThrow(() -> ExternalDataGatewayFactory.setExternalPaths("/path1:/path2"));
        }
    }

    @Nested
    @DisplayName("GetInstance Tests")
    class GetInstanceTests {

        @Test
        @DisplayName("GetInstance with expression extracts class")
        void getInstanceWithExpressionExtractsClass() {
            // Should return null for non-existent class, but not throw
            ExternalDataGateway gateway = ExternalDataGatewayFactory.getInstance(
                "#external:NonExistentClass");
            assertNull(gateway);
        }

        @Test
        @DisplayName("GetInstance with null returns null")
        void getInstanceWithNullReturnsNull() {
            ExternalDataGateway gateway = ExternalDataGatewayFactory.getInstance(null);
            assertNull(gateway);
        }

        @Test
        @DisplayName("GetInstance with simple class name returns null for non-existent")
        void getInstanceWithSimpleClassNameReturnsNullForNonExistent() {
            ExternalDataGateway gateway = ExternalDataGatewayFactory.getInstance("NonExistentClass");
            assertNull(gateway);
        }

        @Test
        @DisplayName("GetInstance with qualified class name returns null for non-existent")
        void getInstanceWithQualifiedClassNameReturnsNullForNonExistent() {
            ExternalDataGateway gateway = ExternalDataGatewayFactory.getInstance(
                "com.example.NonExistentClass");
            assertNull(gateway);
        }
    }

    @Nested
    @DisplayName("GetInstances Tests")
    class GetInstancesTests {

        @Test
        @DisplayName("GetInstances returns non-null set")
        void getInstancesReturnsNonNullSet() {
            java.util.Set<ExternalDataGateway> instances = ExternalDataGatewayFactory.getInstances();
            assertNotNull(instances);
        }

        @Test
        @DisplayName("GetInstances returns set of ExternalDataGateway")
        void getInstancesReturnsSetOfExternalDataGateway() {
            java.util.Set<ExternalDataGateway> instances = ExternalDataGatewayFactory.getInstances();
            for (ExternalDataGateway gateway : instances) {
                assertTrue(gateway instanceof ExternalDataGateway);
            }
        }
    }

    @Nested
    @DisplayName("Factory Properties Tests")
    class FactoryPropertiesTests {

        @Test
        @DisplayName("Factory should have private constructor")
        void factoryHasPrivateConstructor() throws NoSuchMethodException {
            java.lang.reflect.Constructor<ExternalDataGatewayFactory> constructor =
                ExternalDataGatewayFactory.class.getDeclaredConstructor();
            assertFalse(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
        }

        @Test
        @DisplayName("Mapping prefix constant should have expected value")
        void mappingPrefixConstantHasExpectedValue() {
            assertEquals("#external:", ExternalDataGatewayFactory.MAPPING_PREFIX);
        }
    }
}
