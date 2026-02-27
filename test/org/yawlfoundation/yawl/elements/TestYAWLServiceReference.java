package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * Chicago TDD tests for YAWLServiceReference.
 * Tests service identity and XML serialization.
 */
@DisplayName("YAWLServiceReference Tests")
@Tag("unit")
class TestYAWLServiceReference {

    private YAWLServiceReference service;
    private YAWLServiceGateway gateway;

    @BeforeEach
    void setUp() {
        YSpecification spec = new YSpecification("http://test.com/test-spec");
        gateway = new YAWLServiceGateway("testGateway", spec);
        service = new YAWLServiceReference("http://service.example.com", gateway, "TestService");
    }

    @Nested
    @DisplayName("YAWLServiceReference Creation Tests")
    class YAWLServiceReferenceCreationTests {

        @Test
        @DisplayName("Service should store ID correctly")
        void serviceStoresIdCorrectly() {
            assertEquals("http://service.example.com", service.getServiceID());
        }

        @Test
        @DisplayName("Service should store name correctly")
        void serviceStoresNameCorrectly() {
            assertEquals("TestService", service.getServiceName());
        }

        @Test
        @DisplayName("GetURI should return service ID")
        void getUriReturnsServiceId() {
            assertEquals("http://service.example.com", service.getURI());
        }

        @Test
        @DisplayName("Default constructor should work")
        void defaultConstructorWorks() {
            YAWLServiceReference emptyService = new YAWLServiceReference();
            assertNull(emptyService.getServiceID());
        }

        @Test
        @DisplayName("Constructor with password should store password")
        void constructorWithPasswordStoresPassword() {
            YAWLServiceReference pwdService = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService", "secret");
            assertEquals("secret", pwdService.getServicePassword());
        }

        @Test
        @DisplayName("Constructor with documentation should store documentation")
        void constructorWithDocumentationStoresDocumentation() {
            YAWLServiceReference docService = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService", "pwd", "This is a test service");
            assertEquals("This is a test service", docService.get_documentation());
        }
    }

    @Nested
    @DisplayName("Assignable Tests")
    class AssignableTests {

        @Test
        @DisplayName("Default assignable is true")
        void defaultAssignableIsTrue() {
            assertTrue(service.isAssignable());
        }

        @Test
        @DisplayName("SetAssignable updates value")
        void setAssignableUpdatesValue() {
            service.setAssignable(false);
            assertFalse(service.isAssignable());
        }

        @Test
        @DisplayName("CanBeAssignedToTask returns assignable value")
        void canBeAssignedToTaskReturnsAssignableValue() {
            assertTrue(service.canBeAssignedToTask());
            service.setAssignable(false);
            assertFalse(service.canBeAssignedToTask());
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Services with same ID are equal")
        void servicesWithSameIdAreEqual() {
            YAWLServiceReference service2 = new YAWLServiceReference(
                "http://service.example.com", gateway, "OtherService");

            assertEquals(service, service2);
        }

        @Test
        @DisplayName("Services with different ID are not equal")
        void servicesWithDifferentIdAreNotEqual() {
            YAWLServiceReference service2 = new YAWLServiceReference(
                "http://other.example.com", gateway, "TestService");

            assertNotEquals(service, service2);
        }

        @Test
        @DisplayName("Service should not equal null")
        void serviceShouldNotEqualNull() {
            assertNotEquals(null, service);
        }

        @Test
        @DisplayName("HashCode should be consistent with equals")
        void hashCodeConsistentWithEquals() {
            YAWLServiceReference service2 = new YAWLServiceReference(
                "http://service.example.com", gateway, "OtherService");

            assertEquals(service.hashCode(), service2.hashCode());
        }
    }

    @Nested
    @DisplayName("ToXML Tests")
    class ToXMLTests {

        @Test
        @DisplayName("ToXML should contain service ID")
        void toXmlContainsServiceId() {
            String xml = service.toXML();
            assertTrue(xml.contains("<yawlService"));
            assertTrue(xml.contains("id=\"http://service.example.com\""));
        }

        @Test
        @DisplayName("ToXML should contain documentation when set")
        void toXmlContainsDocumentationWhenSet() {
            YAWLServiceReference docService = new YAWLServiceReference(
                "http://service.example.com", gateway, "TestService", "pwd", "Test docs");

            String xml = docService.toXML();

            assertTrue(xml.contains("<documentation>Test docs</documentation>"));
        }

        @Test
        @DisplayName("ToXMLComplete should contain all fields")
        void toXmlCompleteContainsAllFields() {
            service.setServicePassword("secret");
            service.setAssignable(false);

            String xml = service.toXMLComplete();

            assertTrue(xml.contains("<servicename>TestService</servicename>"));
            assertTrue(xml.contains("<servicepassword>secret</servicepassword>"));
            assertTrue(xml.contains("<assignable>false</assignable>"));
        }
    }

    @Nested
    @DisplayName("FromXML Tests")
    class FromXMLTests {

        @Test
        @DisplayName("FromXML should parse service ID")
        void fromXmlParsesServiceId() {
            String xml = "<yawlService id=\"http://parsed.example.com\"/>";

            YAWLServiceReference parsed = new YAWLServiceReference();
            parsed.fromXML(xml);

            assertEquals("http://parsed.example.com", parsed.getServiceID());
        }

        @Test
        @DisplayName("FromXML should parse service name")
        void fromXmlParsesServiceName() {
            String xml = "<yawlService id=\"http://test.com\"><servicename>ParsedService</servicename></yawlService>";

            YAWLServiceReference parsed = new YAWLServiceReference();
            parsed.fromXML(xml);

            assertEquals("ParsedService", parsed.getServiceName());
        }

        @Test
        @DisplayName("FromXML should parse assignable")
        void fromXmlParsesAssignable() {
            String xml = "<yawlService id=\"http://test.com\"><assignable>true</assignable></yawlService>";

            YAWLServiceReference parsed = new YAWLServiceReference();
            parsed.fromXML(xml);

            assertTrue(parsed.isAssignable());
        }
    }

    @Nested
    @DisplayName("Unmarshal Tests")
    class UnmarshalTests {

        @Test
        @DisplayName("Unmarshal should create service from XML")
        void unmarshalCreatesServiceFromXml() {
            String xml = "<yawlService id=\"http://unmarshal.example.com\">" +
                "<servicename>UnmarshalService</servicename>" +
                "<servicepassword>pwd</servicepassword>" +
                "</yawlService>";

            YAWLServiceReference unmarshaled = YAWLServiceReference.unmarshal(xml);

            assertNotNull(unmarshaled);
            assertEquals("http://unmarshal.example.com", unmarshaled.getServiceID());
            assertEquals("UnmarshalService", unmarshaled.getServiceName());
            assertEquals("pwd", unmarshaled.getServicePassword());
        }

        @Test
        @DisplayName("Unmarshal returns null for invalid XML")
        void unmarshalReturnsNullForInvalidXml() {
            YAWLServiceReference unmarshaled = YAWLServiceReference.unmarshal("not valid xml");
            assertNull(unmarshaled);
        }
    }

    @Nested
    @DisplayName("GetScheme Tests")
    class GetSchemeTests {

        @Test
        @DisplayName("GetScheme extracts http scheme")
        void getSchemeExtractsHttpScheme() {
            assertEquals("http", service.getScheme());
        }

        @Test
        @DisplayName("GetScheme extracts https scheme")
        void getSchemeExtractsHttpsScheme() {
            YAWLServiceReference httpsService = new YAWLServiceReference(
                "https://secure.example.com", gateway, "SecureService");
            assertEquals("https", httpsService.getScheme());
        }

        @Test
        @DisplayName("GetScheme returns null for no scheme")
        void getSchemeReturnsNullForNoScheme() {
            YAWLServiceReference noSchemeService = new YAWLServiceReference(
                "localhost", gateway, "LocalService");
            assertNull(noSchemeService.getScheme());
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("SetServiceName updates name")
        void setServiceNameUpdatesName() {
            service.setServiceName("NewName");
            assertEquals("NewName", service.getServiceName());
        }

        @Test
        @DisplayName("SetServicePassword updates password")
        void setServicePasswordUpdatesPassword() {
            service.setServicePassword("newPassword");
            assertEquals("newPassword", service.getServicePassword());
        }
    }

    @Nested
    @DisplayName("Hibernate Accessor Tests")
    class HibernateAccessorTests {

        @Test
        @DisplayName("Hibernate accessors work correctly")
        void hibernateAccessorsWorkCorrectly() {
            service.set_yawlServiceID("http://hibernate.example.com");
            assertEquals("http://hibernate.example.com", service.get_yawlServiceID());

            service.set_serviceName("HibernateService");
            assertEquals("HibernateService", service.get_serviceName());

            service.set_servicePassword("hibPassword");
            assertEquals("hibPassword", service.get_servicePassword());

            service.set_assignable(false);
            assertFalse(service.get_assignable());
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Verify should complete without errors")
        void verifyCompletesWithoutErrors() {
            org.yawlfoundation.yawl.util.YVerificationHandler handler =
                new org.yawlfoundation.yawl.util.YVerificationHandler();

            service.verify(handler);

            // Verification completes, may have warnings about unregistered service
        }
    }
}
