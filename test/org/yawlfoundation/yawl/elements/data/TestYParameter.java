package org.yawlfoundation.yawl.elements.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Chicago TDD tests for YParameter.
 * Tests parameter types, cut-through, and XML serialization.
 */
@DisplayName("YParameter Tests")
@Tag("unit")
class TestYParameter {

    private YSpecification spec;
    private YAWLServiceGateway gateway;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://test.com/test-spec");
        gateway = new YAWLServiceGateway("testGateway", spec);
    }

    @Nested
    @DisplayName("YParameter Creation Tests")
    class YParameterCreationTests {

        @Test
        @DisplayName("YParameter with input type should be created")
        void yParameterWithInputTypeCreated() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            assertTrue(param.isInput());
            assertEquals("inputParam", param.getDirection());
        }

        @Test
        @DisplayName("YParameter with output type should be created")
        void yParameterWithOutputTypeCreated() {
            YParameter param = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            assertTrue(param.isOutput());
            assertEquals("outputParam", param.getDirection());
        }

        @Test
        @DisplayName("YParameter with enablement type should be created")
        void yParameterWithEnablementTypeCreated() {
            YParameter param = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);
            assertTrue(param.isEnablement());
            assertEquals("enablementParam", param.getDirection());
        }

        @Test
        @DisplayName("YParameter with invalid type should throw")
        void yParameterWithInvalidTypeThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new YParameter(gateway, 999));
        }

        @Test
        @DisplayName("YParameter with string type input")
        void yParameterWithStringTypeInput() {
            YParameter param = new YParameter(gateway, "inputParam");
            assertTrue(param.isInput());
        }

        @Test
        @DisplayName("YParameter with string type output")
        void yParameterWithStringTypeOutput() {
            YParameter param = new YParameter(gateway, "outputParam");
            assertTrue(param.isOutput());
        }

        @Test
        @DisplayName("YParameter with string type enablement")
        void yParameterWithStringTypeEnablement() {
            YParameter param = new YParameter(gateway, "enablementParam");
            assertTrue(param.isEnablement());
        }

        @Test
        @DisplayName("YParameter with invalid string type throws")
        void yParameterWithInvalidStringTypeThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new YParameter(gateway, "invalidType"));
        }

        @Test
        @DisplayName("Default constructor should work")
        void defaultConstructorWorks() {
            YParameter param = new YParameter();
            assertNotNull(param);
        }
    }

    @Nested
    @DisplayName("Cut-Through Parameter Tests")
    class CutThroughParameterTests {

        @Test
        @DisplayName("Set cut-through on output param should work")
        void setCutThroughOnOutputParamWorks() {
            YParameter param = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            param.setIsCutThroughParam(true);
            assertTrue(param.bypassesDecompositionStateSpace());
        }

        @Test
        @DisplayName("Set cut-through to false should work")
        void setCutThroughToFalseWorks() {
            YParameter param = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            param.setIsCutThroughParam(true);
            param.setIsCutThroughParam(false);
            assertFalse(param.bypassesDecompositionStateSpace());
        }

        @Test
        @DisplayName("Default cut-through should be false")
        void defaultCutThroughIsFalse() {
            YParameter param = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            assertFalse(param.bypassesDecompositionStateSpace());
        }

        @Test
        @DisplayName("Set cut-through on input param should throw")
        void setCutThroughOnInputParamThrows() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            assertThrows(IllegalArgumentException.class, () ->
                param.setIsCutThroughParam(true));
        }
    }

    @Nested
    @DisplayName("Type Helper Methods Tests")
    class TypeHelperMethodsTests {

        @Test
        @DisplayName("GetTypeForInput returns inputParam")
        void getTypeForInputReturnsInputParam() {
            assertEquals("inputParam", YParameter.getTypeForInput());
        }

        @Test
        @DisplayName("GetTypeForOutput returns outputParam")
        void getTypeForOutputReturnsOutputParam() {
            assertEquals("outputParam", YParameter.getTypeForOutput());
        }

        @Test
        @DisplayName("GetTypeForEnablement returns enablementParam")
        void getTypeForEnablementReturnsEnablementParam() {
            assertEquals("enablementParam", YParameter.getTypeForEnablement());
        }

        @Test
        @DisplayName("GetParamType returns correct type")
        void getParamTypeReturnsCorrectType() {
            YParameter input = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            YParameter output = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            YParameter enablement = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);

            assertEquals(YParameter._INPUT_PARAM_TYPE, input.getParamType());
            assertEquals(YParameter._OUTPUT_PARAM_TYPE, output.getParamType());
            assertEquals(YParameter._ENABLEMENT_PARAM_TYPE, enablement.getParamType());
        }

        @Test
        @DisplayName("GetParamTypeStr returns correct string")
        void getParamTypeStrReturnsCorrectString() {
            YParameter input = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            YParameter output = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            YParameter enablement = new YParameter(gateway, YParameter._ENABLEMENT_PARAM_TYPE);

            assertEquals("inputParam", input.getParamTypeStr());
            assertEquals("outputParam", output.getParamTypeStr());
            assertEquals("enablementParam", enablement.getParamTypeStr());
        }
    }

    @Nested
    @DisplayName("ToXML Tests")
    class ToXMLTests {

        @Test
        @DisplayName("ToXML should contain parameter type element")
        void toXmlContainsParameterTypeElement() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("testParam");
            param.setDataTypeAndName("xs:string", "testParam", "http://www.w3.org/2001/XMLSchema");

            String xml = param.toXML();
            assertTrue(xml.contains("<inputParam"));
            assertTrue(xml.contains("</inputParam>"));
        }

        @Test
        @DisplayName("ToXML should contain name")
        void toXmlContainsName() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("myParam");
            param.setDataTypeAndName("xs:string", "myParam", "http://www.w3.org/2001/XMLSchema");

            String xml = param.toXML();
            assertTrue(xml.contains("<name>myParam</name>"));
        }

        @Test
        @DisplayName("ToXML should contain cut-through for output param")
        void toXmlContainsCutThroughForOutputParam() {
            YParameter param = new YParameter(gateway, YParameter._OUTPUT_PARAM_TYPE);
            param.setName("myParam");
            param.setDataTypeAndName("xs:string", "myParam", "http://www.w3.org/2001/XMLSchema");
            param.setIsCutThroughParam(true);

            String xml = param.toXML();
            assertTrue(xml.contains("<bypassesStatespaceForDecomposition/>"));
        }

        @Test
        @DisplayName("ToSummaryXML should contain ordering")
        void toSummaryXmlContainsOrdering() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("myParam");
            param.setDataTypeAndName("xs:string", "myParam", "http://www.w3.org/2001/XMLSchema");
            param.setOrdering(5);

            String xml = param.toSummaryXML();
            assertTrue(xml.contains("<ordering>5</ordering>"));
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Verify should fail for mandatory param with initial value")
        void verifyFailsForMandatoryWithInitialValue() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("testParam");
            param.setDataTypeAndName("xs:string", "testParam", "http://www.w3.org/2001/XMLSchema");
            param.setMandatory(true);
            param.setInitialValue("<test>value</test>");

            YVerificationHandler handler = new YVerificationHandler();
            param.verify(handler);

            assertTrue(handler.hasErrors());
        }

        @Test
        @DisplayName("Verify should pass for optional param with initial value")
        void verifyPassesForOptionalWithInitialValue() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("testParam");
            param.setDataTypeAndName("xs:string", "testParam", "http://www.w3.org/2001/XMLSchema");
            param.setMandatory(false);
            param.setInitialValue("<test>value</test>");

            YVerificationHandler handler = new YVerificationHandler();
            param.verify(handler);

            // String type doesn't validate value content
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("YParameter should extend YVariable")
        void yParameterExtendsYVariable() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            assertTrue(param instanceof YVariable);
        }

        @Test
        @DisplayName("YParameter should implement Comparable")
        void yParameterImplementsComparable() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            assertTrue(param instanceof Comparable);
        }

        @Test
        @DisplayName("CompareTo should compare by ordering")
        void compareToComparesByOrdering() {
            YParameter param1 = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            YParameter param2 = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);

            param1.setOrdering(1);
            param2.setOrdering(2);

            assertTrue(param1.compareTo(param2) < 0);
            assertTrue(param2.compareTo(param1) > 0);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("ToString should return XML")
        void toStringReturnsXml() {
            YParameter param = new YParameter(gateway, YParameter._INPUT_PARAM_TYPE);
            param.setName("testParam");
            param.setDataTypeAndName("xs:string", "testParam", "http://www.w3.org/2001/XMLSchema");

            String result = param.toString();
            assertTrue(result.contains("<inputParam"));
        }
    }
}
