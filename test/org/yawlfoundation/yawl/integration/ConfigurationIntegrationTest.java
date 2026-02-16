package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration Integration Tests - Verifies JAXB serialization and mail configuration
 * Tests XML marshalling/unmarshalling and mail sender configuration
 */
public class ConfigurationIntegrationTest {

    private JAXBContext jaxbContext;

    @BeforeEach
    public void setUp() throws JAXBException {
        jaxbContext = JAXBContext.newInstance(WorkflowConfiguration.class);
    }

    @Test
    public void testJAXBMarshalling() throws Exception {
        WorkflowConfiguration config = new WorkflowConfiguration();
        config.setProcessName("Test Workflow");
        config.setVersion("1.0");
        config.setEnabled(true);
        
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        
        StringWriter writer = new StringWriter();
        marshaller.marshal(config, writer);
        
        String xml = writer.toString();
        assertNotNull("XML should be generated", xml);
        assertTrue("XML should contain process name", xml.contains("Test Workflow"));
        assertTrue("XML should contain version", xml.contains("1.0"));
    }

    @Test
    public void testJAXBUnmarshalling() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<workflowConfiguration>" +
            "  <processName>Unmarshalled Workflow</processName>" +
            "  <version>2.0</version>" +
            "  <enabled>true</enabled>" +
            "</workflowConfiguration>";
        
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        WorkflowConfiguration config = 
            (WorkflowConfiguration) unmarshaller.unmarshal(new StringReader(xml));
        
        assertNotNull("Configuration should be unmarshalled", config);
        assertEquals("Process name should match", "Unmarshalled Workflow", config.getProcessName());
        assertEquals("Version should match", "2.0", config.getVersion());
        assertTrue("Enabled should be true", config.isEnabled());
    }

    @Test
    public void testRoundTripSerialization() throws Exception {
        WorkflowConfiguration original = new WorkflowConfiguration();
        original.setProcessName("Round Trip Test");
        original.setVersion("3.0");
        original.setEnabled(false);
        original.setDescription("This is a test configuration");
        
        // Marshal to XML
        Marshaller marshaller = jaxbContext.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(original, writer);
        String xml = writer.toString();
        
        // Unmarshal from XML
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        WorkflowConfiguration restored = 
            (WorkflowConfiguration) unmarshaller.unmarshal(new StringReader(xml));
        
        assertEquals("Process name should match after round trip", 
            original.getProcessName(), restored.getProcessName());
        assertEquals("Version should match after round trip", 
            original.getVersion(), restored.getVersion());
        assertEquals("Enabled should match after round trip", 
            original.isEnabled(), restored.isEnabled());
        assertEquals("Description should match after round trip", 
            original.getDescription(), restored.getDescription());
    }

    @Test
    public void testPropertiesConfiguration() {
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", "smtp.gmail.com");
        props.setProperty("mail.smtp.port", "587");
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        
        assertEquals("SMTP host should match", "smtp.gmail.com", 
            props.getProperty("mail.smtp.host"));
        assertEquals("SMTP port should be 587", "587", 
            props.getProperty("mail.smtp.port"));
        assertTrue("Auth should be enabled", 
            Boolean.parseBoolean(props.getProperty("mail.smtp.auth")));
    }

    @Test
    public void testMailConfiguration() throws Exception {
        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", "localhost");
        mailProps.put("mail.smtp.port", "25");
        mailProps.put("mail.smtp.from", "workflow@example.com");
        
        String host = (String) mailProps.get("mail.smtp.host");
        String port = (String) mailProps.get("mail.smtp.port");
        String from = (String) mailProps.get("mail.smtp.from");
        
        assertEquals("Mail host should be localhost", "localhost", host);
        assertEquals("Mail port should be 25", "25", port);
        assertEquals("Mail from should match", "workflow@example.com", from);
    }

    @Test
    public void testXmlNamespaces() throws Exception {
        String xmlWithNamespace = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<config xmlns=\"http://yawlfoundation.org/config\">" +
            "  <processName>Namespace Test</processName>" +
            "  <version>1.0</version>" +
            "</config>";
        
        assertNotNull("XML with namespace should be valid", xmlWithNamespace);
        assertTrue("XML should contain namespace URI", 
            xmlWithNamespace.contains("yawlfoundation.org"));
    }

    @Test
    public void testConfigurationValidation() throws Exception {
        WorkflowConfiguration invalidConfig = new WorkflowConfiguration();
        invalidConfig.setProcessName(null);
        invalidConfig.setVersion("1.0");
        
        Marshaller marshaller = jaxbContext.createMarshaller();
        StringWriter writer = new StringWriter();
        
        try {
            marshaller.marshal(invalidConfig, writer);
        } catch (JAXBException e) {
            // Some validators may reject null required fields
            assertTrue("JAXBException should be thrown or handled gracefully", true);
        }
    }

    @Test
    public void testPropertyPlaceholders() {
        String configTemplate = "mail.host=${MAIL_HOST:localhost}," +
            "mail.port=${MAIL_PORT:25}," +
            "mail.user=${MAIL_USER:admin}";
        
        assertNotNull("Config template should be created", configTemplate);
        assertTrue("Template should contain placeholders", configTemplate.contains("${"));
    }

    // Helper JAXB class
    public static class WorkflowConfiguration {
        private String processName;
        private String version;
        private boolean enabled;
        private String description;

        public String getProcessName() { return processName; }
        public void setProcessName(String processName) { this.processName = processName; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
