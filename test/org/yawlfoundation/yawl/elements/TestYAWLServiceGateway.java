package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Chicago TDD tests for YAWLServiceGateway.
 * Tests service reference management and verification.
 */
@DisplayName("YAWLServiceGateway Tests")
@Tag("unit")
class TestYAWLServiceGateway {

    private YSpecification spec;
    private YAWLServiceGateway gateway;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://test.com/test-spec");
        gateway = new YAWLServiceGateway("testGateway", spec);
    }

    @Nested
    @DisplayName("YAWLServiceGateway Creation Tests")
    class YAWLServiceGatewayCreationTests {

        @Test
        @DisplayName("Gateway should store ID correctly")
        void gatewayStoresIdCorrectly() {
            assertEquals("testGateway", gateway.getID());
        }

        @Test
        @DisplayName("Gateway should reference parent specification")
        void gatewayReferencesParentSpecification() {
            assertEquals(spec, gateway.getSpecification());
        }

        @Test
        @DisplayName("Gateway should have no service initially")
        void gatewayHasNoServiceInitially() {
            assertNull(gateway.getYawlService());
        }

        @Test
        @DisplayName("Gateway should have empty service map initially")
        void gatewayHasEmptyServiceMapInitially() {
            assertNull(gateway.getYawlService());
        }
    }

    @Nested
    @DisplayName("Service Management Tests")
    class ServiceManagementTests {

        @Test
        @DisplayName("SetYawlService should store service reference")
        void setYawlServiceStoresServiceReference() {
            YAWLServiceReference service = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService");

            gateway.setYawlService(service);

            assertEquals(service, gateway.getYawlService());
        }

        @Test
        @DisplayName("SetYawlService with same URI updates service")
        void setYawlServiceWithSameUriUpdatesService() {
            YAWLServiceReference service1 = new YAWLServiceReference(
                "http://service.example.com", gateway, "Service1");
            YAWLServiceReference service2 = new YAWLServiceReference(
                "http://service.example.com", gateway, "Service2");

            gateway.setYawlService(service1);
            gateway.setYawlService(service2);

            // Service with same URI replaces the first
            assertEquals(service2.getServiceName(), gateway.getYawlService().getServiceName());
        }

        @Test
        @DisplayName("SetYawlService with null does not throw")
        void setYawlServiceWithNullDoesNotThrow() {
            assertDoesNotThrow(() -> gateway.setYawlService(null));
        }

        @Test
        @DisplayName("GetYawlService with ID returns specific service")
        void getYawlServiceWithIdReturnsSpecificService() {
            YAWLServiceReference service = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService");
            gateway.setYawlService(service);

            YAWLServiceReference retrieved = gateway.getYawlService("http://service.example.com");

            assertEquals(service, retrieved);
        }

        @Test
        @DisplayName("GetYawlService with unknown ID returns null")
        void getYawlServiceWithUnknownIdReturnsNull() {
            assertNull(gateway.getYawlService("http://unknown.example.com"));
        }

        @Test
        @DisplayName("ClearYawlService removes all services")
        void clearYawlServiceRemovesAllServices() {
            YAWLServiceReference service = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService");
            gateway.setYawlService(service);

            gateway.clearYawlService();

            assertNull(gateway.getYawlService());
        }
    }

    @Nested
    @DisplayName("Enablement Parameter Tests")
    class EnablementParameterTests {

        @Test
        @DisplayName("GetEnablementParameters returns empty map initially")
        @SuppressWarnings("deprecation")
        void getEnablementParametersReturnsEmptyMapInitially() {
            Map<String, YParameter> params = gateway.getEnablementParameters();
            assertNotNull(params);
            assertTrue(params.isEmpty());
        }

        @Test
        @DisplayName("SetEnablementParameter stores parameter")
        @SuppressWarnings("deprecation")
        void setEnablementParameterStoresParameter() {
            YParameter param = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);
            param.setName("enableParam");

            gateway.setEnablementParameter(param);

            assertTrue(gateway.getEnablementParameterNames().contains("enableParam"));
        }

        @Test
        @DisplayName("SetEnablementParameter with non-enablement type throws")
        @SuppressWarnings("deprecation")
        void setEnablementParameterWithWrongTypeThrows() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("inputParam");

            assertThrows(RuntimeException.class, () -> gateway.setEnablementParameter(param));
        }

        @Test
        @DisplayName("SetEnablementParameter with element name uses element name as key")
        @SuppressWarnings("deprecation")
        void setEnablementParameterWithElementNameUsesElementNameAsKey() {
            YParameter param = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);
            param.setElementName("elemParam");

            gateway.setEnablementParameter(param);

            assertTrue(gateway.getEnablementParameterNames().contains("elemParam"));
        }
    }

    @Nested
    @DisplayName("ToXML Tests")
    class ToXMLTests {

        @Test
        @DisplayName("ToXML should contain gateway content")
        void toXmlContainsGatewayContent() {
            String xml = gateway.toXML();
            assertNotNull(xml);
        }

        @Test
        @DisplayName("ToXML should contain service when set")
        void toXmlContainsServiceWhenSet() {
            YAWLServiceReference service = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService");
            gateway.setYawlService(service);

            String xml = gateway.toXML();

            assertTrue(xml.contains("<yawlService"));
            assertTrue(xml.contains("http://service.example.com"));
        }

        @Test
        @DisplayName("ToXML should contain enablement parameters when set")
        @SuppressWarnings("deprecation")
        void toXmlContainsEnablementParametersWhenSet() {
            YParameter param = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);
            param.setName("enableParam");
            param.setDataTypeAndName("xs:string", "enableParam", "http://www.w3.org/2001/XMLSchema");
            gateway.setEnablementParameter(param);

            String xml = gateway.toXML();

            assertTrue(xml.contains("<enablementParam"));
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Verify should pass for simple gateway")
        void verifyPassesForSimpleGateway() {
            YVerificationHandler handler = new YVerificationHandler();
            gateway.verify(handler);

            // Gateway itself should verify successfully
            assertFalse(handler.hasErrors());
        }

        @Test
        @DisplayName("Verify should verify service reference")
        void verifyVerifiesServiceReference() {
            YAWLServiceReference service = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService");
            gateway.setYawlService(service);

            YVerificationHandler handler = new YVerificationHandler();
            gateway.verify(handler);

            // Verification completes without exceptions
            assertFalse(handler.hasErrors());
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("YAWLServiceGateway should extend YDecomposition")
        void yawlServiceGatewayExtendsYDecomposition() {
            assertTrue(gateway instanceof YDecomposition);
        }

        @Test
        @DisplayName("YAWLServiceGateway should implement YVerifiable")
        void yawlServiceGatewayImplementsYVerifiable() {
            assertTrue(gateway instanceof YVerifiable);
        }
    }
}
