/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.util;

import java.util.Random;
import java.util.UUID;

/**
 * Helper class to generate corrupt and malformed XML for error testing.
 *
 * <p>Provides methods to create various types of invalid XML that can be used
 * to test error handling in YCaseImporter and related classes.</p>
 *
 * <p>Chicago TDD: Generates real corrupt data for testing error paths.
 * No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class ErrorInjectionHelper {

    private static final Random RANDOM = new Random();

    /**
     * Creates XML with missing required elements.
     *
     * @return XML string missing required elements
     */
    public static String createMissingElementXML() {
        String caseId = UUID.randomUUID().toString();
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="%s">
              <!-- Missing specificationSet element -->
              <runners>
                <runner>
                  <identifier id="%s.1">
                    <locations/>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """.formatted(caseId, caseId);
    }

    /**
     * Creates malformed XML with unclosed tags.
     *
     * @return Malformed XML string
     */
    public static String createMalformedXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="malformed-test">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification uri="Test">
                  <decomposition id="TestNet">
                    <!-- Unclosed tag
                </specification>
              </specificationSet>
            </case>
            """;
    }

    /**
     * Creates oversized XML that exceeds normal size limits.
     *
     * @param sizeBytes approximate size in bytes
     * @return Large XML string
     */
    public static String createOversizedXML(int sizeBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<case id=\"oversized-test\">\n");
        sb.append("  <specificationSet xmlns=\"http://www.yawlfoundation.org/yawlschema\"/>\n");
        sb.append("  <runners>\n");
        sb.append("    <runner>\n");
        sb.append("      <identifier id=\"oversized.1\">\n");
        sb.append("        <locations>\n");

        // Generate lots of location entries to increase size
        int locationsNeeded = sizeBytes / 100;
        for (int i = 0; i < locationsNeeded; i++) {
            sb.append("          <location>location-")
              .append(i)
              .append("-")
              .append(UUID.randomUUID())
              .append("</location>\n");
        }

        sb.append("        </locations>\n");
        sb.append("        <children/>\n");
        sb.append("      </identifier>\n");
        sb.append("    </runner>\n");
        sb.append("  </runners>\n");
        sb.append("</case>");

        return sb.toString();
    }

    /**
     * Creates XML with invalid characters.
     *
     * @return XML with invalid characters
     */
    public static String createInvalidCharactersXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="invalid\x00chars\x01test">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners/>
            </case>
            """;
    }

    /**
     * Creates XML with invalid namespace declarations.
     *
     * @return XML with invalid namespaces
     */
    public static String createInvalidNamespaceXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="namespace-test" xmlns:invalid=":::not-a-valid-namespace:::">
              <invalid:element>content</invalid:element>
            </case>
            """;
    }

    /**
     * Creates XML with deeply nested elements.
     *
     * @param depth nesting depth
     * @return Deeply nested XML
     */
    public static String createDeeplyNestedXML(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<case id=\"nested-test\">\n");

        for (int i = 0; i < depth; i++) {
            sb.append("  ".repeat(i + 1)).append("<level").append(i).append(">\n");
        }

        sb.append("  ".repeat(depth + 1)).append("<content>deep</content>\n");

        for (int i = depth - 1; i >= 0; i--) {
            sb.append("  ".repeat(i + 1)).append("</level").append(i).append(">\n");
        }

        sb.append("</case>");
        return sb.toString();
    }

    /**
     * Creates XML with duplicate element IDs.
     *
     * @return XML with duplicate IDs
     */
    public static String createDuplicateIdXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="duplicate-test">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="same-id">
                    <locations/>
                    <children/>
                  </identifier>
                </runner>
                <runner>
                  <identifier id="same-id">
                    <locations/>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates XML with empty required fields.
     *
     * @return XML with empty required fields
     */
    public static String createEmptyRequiredFieldsXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="">
                    <locations/>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates XML with binary data injection.
     *
     * @return XML with binary data
     */
    public static String createBinaryInjectionXML() {
        byte[] randomBytes = new byte[100];
        RANDOM.nextBytes(randomBytes);

        StringBuilder hexString = new StringBuilder();
        for (byte b : randomBytes) {
            hexString.append(String.format("%02x", b));
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="binary-test">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <netdata>%s</netdata>
                </runner>
              </runners>
            </case>
            """.formatted(hexString);
    }

    /**
     * Creates XML with SQL injection patterns.
     *
     * @return XML with SQL injection patterns
     */
    public static String createSqlInjectionXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="sql'; DROP TABLE cases; --">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="1 OR 1=1">
                    <locations>
                      <location>'; DELETE FROM workitems; '</location>
                    </locations>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates XML with script injection patterns.
     *
     * @return XML with script injection
     */
    public static String createScriptInjectionXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="<script>alert('xss')</script>">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="javascript:alert(1)">
                    <locations>
                      <location>&lt;img src=x onerror=alert(1)&gt;</location>
                    </locations>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates XML with truncated content.
     *
     * @param originalXml the original XML to truncate
     * @param truncateAt position to truncate at
     * @return Truncated XML
     * @throws IllegalArgumentException if originalXml is null or truncateAt is negative
     */
    public static String createTruncatedXML(String originalXml, int truncateAt) {
        if (originalXml == null) {
            throw new IllegalArgumentException("originalXml cannot be null");
        }
        if (truncateAt < 0) {
            throw new IllegalArgumentException("truncateAt cannot be negative: " + truncateAt);
        }
        return originalXml.substring(0, Math.min(truncateAt, originalXml.length()));
    }

    /**
     * Creates XML with circular references (simulated).
     *
     * @return XML with circular-like structure
     */
    public static String createCircularReferenceXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="circular-root">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="circular.1">
                    <locations/>
                    <children>
                      <identifier id="circular.1.1">
                        <parent>circular.1</parent>
                        <locations/>
                        <children>
                          <identifier id="circular.1">
                            <parent>circular.1.1</parent>
                            <locations/>
                            <children/>
                          </identifier>
                        </children>
                      </identifier>
                    </children>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates XML with encoded entities.
     *
     * @return XML with various entity encodings
     */
    public static String createEncodedEntitiesXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="entity&amp;test">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
              <runners>
                <runner>
                  <identifier id="test&lt;&gt;&quot;">
                    <locations>
                      <location>&lt;encoded&gt;&amp;&quot;test&quot;</location>
                    </locations>
                    <children/>
                  </identifier>
                </runner>
              </runners>
            </case>
            """;
    }

    /**
     * Creates minimal valid case XML for comparison.
     *
     * @param caseId the case ID to use
     * @return Valid minimal XML
     */
    public static String createMinimalValidXML(String caseId) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <case id="%s">
              <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                <specification uri="TestSpec" version="0.1">
                  <metaData>
                    <title>Test Specification</title>
                  </metaData>
                  <decomposition id="TestNet" isRootNet="true">
                    <processControlElements>
                      <inputCondition id="start"/>
                      <outputCondition id="end"/>
                    </processControlElements>
                  </decomposition>
                </specification>
              </specificationSet>
              <runners>
                <runner>
                  <parent/>
                  <identifier id="%s">
                    <locations>
                      <location>start</location>
                    </locations>
                    <children/>
                  </identifier>
                  <netdata></netdata>
                  <containingtask/>
                  <starttime>0</starttime>
                  <executionstatus>Normal</executionstatus>
                  <enabledtasks/>
                  <busytasks/>
                  <timerstates/>
                  <workitems/>
                </runner>
              </runners>
            </case>
            """.formatted(caseId, caseId);
    }
}
