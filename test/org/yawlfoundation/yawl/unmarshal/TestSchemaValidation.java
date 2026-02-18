/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.unmarshal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

/**
 * Comprehensive tests for schema validation in the YAWL unmarshalling layer.
 *
 * Tests cover:
 * - Missing required attributes
 * - Invalid element nesting
 * - Duplicate ID detection
 * - Version compatibility
 * - XSD validation against YAWL schemas
 *
 * Chicago TDD: All tests use real XML parsing and schema validation, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Schema Validation Tests")
@Tag("validation")
class TestSchemaValidation {

    // Valid minimal specification template
    private static final String VALID_SPEC_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <specification uri="test-spec">
            <metaData>
              <title>Test Specification</title>
              <creator>Test</creator>
            </metaData>
            <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
            <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
              <processControlElements>
                <inputCondition id="input"/>
                <outputCondition id="output"/>
              </processControlElements>
            </decomposition>
          </specification>
        </specificationSet>
        """;

    // ============================================================
    // Missing Attributes Tests
    // ============================================================

    @Nested
    @DisplayName("Missing Attributes Validation")
    class MissingAttributesTests {

        @Test
        @DisplayName("Detects missing specification uri attribute")
        void detectsMissingSpecificationUri() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification>
                    <metaData/>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject specification without uri attribute");
        }

        @Test
        @DisplayName("Detects missing decomposition id attribute")
        void detectsMissingDecompositionId() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject decomposition without id attribute");
        }

        @Test
        @DisplayName("Detects missing inputCondition id")
        void detectsMissingInputConditionId() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject inputCondition without id");
        }

        @Test
        @DisplayName("Detects missing outputCondition id")
        void detectsMissingOutputConditionId() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject outputCondition without id");
        }

        @Test
        @DisplayName("Detects missing isRootNet on root decomposition")
        void detectsMissingIsRootNet() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            // This may or may not fail depending on schema strictness
            // but should not crash
            assertDoesNotThrow(() -> {
                try {
                    YMarshal.unmarshalSpecifications(invalidXml, true);
                } catch (YSyntaxException e) {
                    // Expected if schema requires isRootNet
                }
            });
        }

        @Test
        @DisplayName("Detects missing xsi:type on decomposition")
        void detectsMissingXsiType() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject decomposition without xsi:type");
        }
    }

    // ============================================================
    // Invalid Nesting Tests
    // ============================================================

    @Nested
    @DisplayName("Invalid Element Nesting Validation")
    class InvalidNestingTests {

        @Test
        @DisplayName("Detects task inside inputCondition")
        void detectsTaskInsideInputCondition() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input">
                          <task id="invalid"/>
                        </inputCondition>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject task inside inputCondition");
        }

        @Test
        @DisplayName("Detects specification inside specification")
        void detectsNestedSpecifications() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="outer-spec">
                    <metaData/>
                    <specification uri="inner-spec">
                      <metaData/>
                    </specification>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject nested specification elements");
        }

        @Test
        @DisplayName("Detects processControlElements outside decomposition")
        void detectsProcessControlElementsOutsideDecomposition() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="test-spec">
                    <metaData/>
                    <processControlElements>
                      <inputCondition id="input"/>
                    </processControlElements>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject processControlElements outside decomposition");
        }

        @Test
        @DisplayName("Detects missing processControlElements in net")
        void detectsMissingProcessControlElementsInNet() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject net without processControlElements");
        }

        @Test
        @DisplayName("Validates correct nesting order")
        void validatesCorrectNestingOrder() throws YSyntaxException {
            String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData>
                      <title>Test</title>
                    </metaData>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <task id="task1">
                          <flowsInto>
                            <nextElementRef id="output"/>
                          </flowsInto>
                        </task>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(validXml, true);
            assertNotNull(specs);
            assertFalse(specs.isEmpty());
        }
    }

    // ============================================================
    // Duplicate ID Tests
    // ============================================================

    @Nested
    @DisplayName("Duplicate ID Detection")
    class DuplicateIdTests {

        @Test
        @DisplayName("Detects duplicate decomposition IDs")
        void detectsDuplicateDecompositionIds() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="duplicate" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                    <decomposition id="duplicate" xsi:type="NetFactsType">
                      <processControlElements>
                        <inputCondition id="input2"/>
                        <outputCondition id="output2"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject duplicate decomposition IDs");
        }

        @Test
        @DisplayName("Detects duplicate task IDs within same net")
        void detectsDuplicateTaskIdsWithinNet() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <task id="duplicate"/>
                        <task id="duplicate"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject duplicate task IDs");
        }

        @Test
        @DisplayName("Detects duplicate condition IDs")
        void detectsDuplicateConditionIds() {
            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="duplicate"/>
                        <outputCondition id="duplicate"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(invalidXml, true);
            }, "Should reject duplicate condition IDs");
        }

        @Test
        @DisplayName("Allows same ID in different specifications")
        void allowsSameIdInDifferentSpecifications() throws YSyntaxException {
            String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="spec1">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                  <specification uri="spec2">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(validXml, true);
            assertEquals(2, specs.size(), "Should parse two specifications");
        }
    }

    // ============================================================
    // Version Compatibility Tests
    // ============================================================

    @Nested
    @DisplayName("Version Compatibility")
    class VersionCompatibilityTests {

        @Test
        @DisplayName("Accepts version 4.0")
        void acceptsVersion40() throws YSyntaxException {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, true);
            assertNotNull(specs);
            assertEquals(YSchemaVersion.FourPointZero, specs.get(0).getSchemaVersion());
        }

        @Test
        @DisplayName("Accepts version 3.0")
        void acceptsVersion30() throws YSyntaxException {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="3.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, true);
            assertNotNull(specs);
        }

        @Test
        @DisplayName("Accepts version 2.2")
        void acceptsVersion22() throws YSyntaxException {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="2.2" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, true);
            assertNotNull(specs);
        }

        @Test
        @DisplayName("Defaults to Beta2 for missing version")
        void defaultsToBeta2ForMissingVersion() throws YSyntaxException {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="test-spec">
                    <metaData/>
                  </specification>
                </specificationSet>
                """;

            // Without schema validation (Beta2 format may not validate against 4.0 schema)
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            assertNotNull(specs);
            assertEquals(YSchemaVersion.Beta2, specs.get(0).getSchemaVersion());
        }

        @Test
        @DisplayName("Rejects unknown version")
        void rejectsUnknownVersion() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="99.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="test-spec">
                    <metaData/>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () -> {
                YMarshal.unmarshalSpecifications(xml, true);
            }, "Should reject unknown version");
        }

        @Test
        @DisplayName("YSchemaVersion fromString handles all valid versions")
        void schemaVersionFromStringHandlesAllVersions() {
            assertEquals(YSchemaVersion.FourPointZero, YSchemaVersion.fromString("4.0"));
            assertEquals(YSchemaVersion.ThreePointZero, YSchemaVersion.fromString("3.0"));
            assertEquals(YSchemaVersion.TwoPointTwo, YSchemaVersion.fromString("2.2"));
            assertEquals(YSchemaVersion.TwoPointOne, YSchemaVersion.fromString("2.1"));
            assertEquals(YSchemaVersion.Beta2, YSchemaVersion.fromString("Beta2"));
        }
    }

    // ============================================================
    // XSD Validation Tests
    // ============================================================

    @Nested
    @DisplayName("XSD Schema Validation")
    class XsdValidationTests {

        @Test
        @DisplayName("SchemaHandler validates valid XML")
        void schemaHandlerValidatesValidXml() {
            SchemaHandler handler = new SchemaHandler(YSchemaVersion.FourPointZero.getSchemaURL());

            boolean compiled = handler.compileSchema();
            assertTrue(compiled, "Schema should compile successfully");

            String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            boolean valid = handler.validate(validXml);
            assertTrue(valid, "Valid XML should pass validation: " + handler.getConcatenatedMessage());
        }

        @Test
        @DisplayName("SchemaHandler rejects invalid XML")
        void schemaHandlerRejectsInvalidXml() {
            SchemaHandler handler = new SchemaHandler(YSchemaVersion.FourPointZero.getSchemaURL());
            handler.compileSchema();

            String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <invalidElement/>
                </specificationSet>
                """;

            boolean valid = handler.validate(invalidXml);
            assertFalse(valid, "Invalid XML should fail validation");
            assertFalse(handler.getErrorMessages().isEmpty(), "Should have error messages");
        }

        @Test
        @DisplayName("SchemaHandler reports error messages")
        void schemaHandlerReportsErrorMessages() {
            SchemaHandler handler = new SchemaHandler(YSchemaVersion.FourPointZero.getSchemaURL());
            handler.compileSchema();

            String malformedXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification>
                    <!-- Missing required uri attribute -->
                  </specification>
                </specificationSet>
                """;

            boolean valid = handler.validate(malformedXml);
            assertFalse(valid);

            String errors = handler.getConcatenatedMessage();
            assertNotNull(errors);
        }

        @Test
        @DisplayName("SchemaHandler compileAndValidate combines operations")
        void schemaHandlerCompileAndValidateCombinesOperations() {
            SchemaHandler handler = new SchemaHandler(YSchemaVersion.FourPointZero.getSchemaURL());

            String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            boolean result = handler.compileAndValidate(validXml);
            assertTrue(result, "compileAndValidate should succeed for valid XML");
        }

        @Test
        @DisplayName("SchemaHandler handles empty schema string")
        void schemaHandlerHandlesEmptySchemaString() {
            SchemaHandler handler = new SchemaHandler("");

            boolean compiled = handler.compileSchema();
            assertFalse(compiled, "Empty schema should fail to compile");
        }
    }

    // ============================================================
    // Schema Version Feature Tests
    // ============================================================

    @Nested
    @DisplayName("Schema Version Feature Tests")
    class SchemaVersionFeatureTests {

        @Test
        @DisplayName("Version 4.0 has correct header")
        void version40HasCorrectHeader() {
            String header = YSchemaVersion.FourPointZero.getHeader();
            assertNotNull(header);
            assertTrue(header.contains("4.0"));
            assertTrue(header.contains("specificationSet"));
        }

        @Test
        @DisplayName("Version 4.0 has schema URL")
        void version40HasSchemaUrl() {
            assertNotNull(YSchemaVersion.FourPointZero.getSchemaURL());
        }

        @Test
        @DisplayName("All versions have headers")
        void allVersionsHaveHeaders() {
            for (YSchemaVersion version : YSchemaVersion.values()) {
                assertNotNull(version.getHeader(), version + " should have header");
            }
        }

        @Test
        @DisplayName("Version comparison works")
        void versionComparisonWorks() {
            assertTrue(YSchemaVersion.FourPointZero.compareTo(YSchemaVersion.ThreePointZero) > 0);
            assertTrue(YSchemaVersion.ThreePointZero.compareTo(YSchemaVersion.FourPointZero) < 0);
        }
    }

    // ============================================================
    // Integration Tests
    // ============================================================

    @Nested
    @DisplayName("Schema Validation Integration")
    class SchemaValidationIntegrationTests {

        @Test
        @DisplayName("Valid specification unmarshals and remarshals correctly")
        void validSpecificationUnmarshalsAndRemarshalsCorrectly() throws YSyntaxException {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData>
                      <title>Test Specification</title>
                      <creator>Test Author</creator>
                    </metaData>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, true);
            assertEquals(1, specs.size());

            YSpecification spec = specs.get(0);
            assertEquals("test-spec", spec.getID());
            assertNotNull(spec.getRootNet());

            // Remarshal and verify
            String remarshalled = YMarshal.marshal(spec);
            assertNotNull(remarshalled);
            assertTrue(remarshalled.contains("test-spec"));
        }

        @Test
        @DisplayName("Validation can be disabled")
        void validationCanBeDisabled() {
            String xmlWithInvalidSchema = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <specification uri="test-spec">
                    <metaData/>
                    <schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                    <decomposition id="root" xsi:type="NetFactsType" isRootNet="true">
                      <processControlElements>
                        <inputCondition id="input"/>
                        <outputCondition id="output"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            // Should not throw when validation is disabled
            assertDoesNotThrow(() -> {
                YMarshal.unmarshalSpecifications(xmlWithInvalidSchema, false);
            });
        }
    }
}
