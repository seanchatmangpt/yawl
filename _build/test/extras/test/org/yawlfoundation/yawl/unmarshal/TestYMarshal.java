package org.yawlfoundation.yawl.unmarshal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSchemaBuildingException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 *
 * Author: Lachlan Aldred
 * Date: 3/10/2003
 * Time: 15:21:49
 *
 */
@Tag("unit")
class TestYMarshal {
    private YSpecification _originalSpec;
    private YSpecification _copy;
    private String _originalXMLString;
    private String _copyXMLString;
    private String tempfileName = "tempTestFile.xml";

    @BeforeEach

    void setUp() throws YSyntaxException, IOException, YSchemaBuildingException, JDOMException {
        File specificationFile = new File(YMarshal.class.getResource("MakeRecordings.xml").getFile());
        // MakeRecordings.xml is a legacy Beta3 spec that predates the current schema;
        // schema validation is disabled to allow the structural marshal/unmarshal test to run
        List specifications = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(specificationFile.getAbsolutePath()), false);
        _originalSpec = (YSpecification) specifications.iterator().next();
        String marshalledSpecs = YMarshal.marshal(specifications, _originalSpec.getSchemaVersion());
        File derivedSpecFile = new File(specificationFile.getParent() + File.separator + tempfileName);
        try {
            FileWriter fw = new FileWriter(derivedSpecFile);
            fw.write(marshalledSpecs);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        _copy = (YSpecification) YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(derivedSpecFile.getAbsolutePath()), false).iterator().next();
        derivedSpecFile.delete();
        _originalXMLString = YMarshal.marshal(_originalSpec);
        _copyXMLString = YMarshal.marshal(_copy);
    }

    public void setUp2() throws YSchemaBuildingException, YSyntaxException, JDOMException, IOException {
//        File specificationFile = new File(YMarshal.class.getResource("MakeRecordings.xml").getFile());
        File specificationFile = new File(YMarshal.class.getResource(tempfileName).getFile());
        List specifications = null;
        specifications = YMarshal.unmarshalSpecifications(StringUtil.fileToString(
                specificationFile.getAbsolutePath()));
        _originalSpec = (YSpecification) specifications.iterator().next();
        String marshalledSpecsString = YMarshal.marshal(specifications, _originalSpec.getSchemaVersion());
        SAXBuilder builder = new SAXBuilder();
        StringReader marshalledSpecsStringReader = new StringReader(marshalledSpecsString);
        Document marshalledSpecsStringDoc = builder.build(marshalledSpecsStringReader);
        XMLOutputter marshalledSpecsStringOutputter = new XMLOutputter(Format.getPrettyFormat());
        marshalledSpecsString = marshalledSpecsStringOutputter.outputString(marshalledSpecsStringDoc);
System.out.println("marshalledSpecsString = " + marshalledSpecsString);
        File derivedSpecFile = new File(
                specificationFile.getParent() + File.separator + tempfileName);
        try {
            FileWriter fw = new FileWriter(derivedSpecFile);
            fw.write(marshalledSpecsString);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        _copy = (YSpecification) YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(derivedSpecFile.getAbsolutePath())).iterator().next();

        derivedSpecFile.delete();
        _originalXMLString = YMarshal.marshal(_originalSpec);
        _copyXMLString = YMarshal.marshal(_copy);
    }

/*
    @Test

    void testBothValid() {
        List errorMessages = _originalSpec.verify();
        if (errorMessages.size() != 0) {
            fail(YMessagePrinter.getMessageString(errorMessages));
        }
        errorMessages = _copy.verify();
        if (errorMessages.size() != 0) {
            fail(YMessagePrinter.getMessageString(errorMessages));
        }
    }

*/
    @Test

    void testBothEqual() {
        assertEquals(_originalSpec.getURI(), _copy.getURI());
        YNet origNet = _originalSpec.getRootNet();
        YNet copyNet = _copy.getRootNet();
        assertEquals(origNet.getInputCondition().toXML(), copyNet.getInputCondition().toXML());
        assertEquals(origNet.getOutputCondition().toXML(), copyNet.getOutputCondition().toXML());
        assertEquals(origNet.getLocalVariables().toString(), copyNet.getLocalVariables().toString());
//System.out.println("\n\norigXML\n" + _originalXMLString);
//System.out.println("\n\n_copyXMLString\n" + _copyXMLString);
        assertEquals(_originalXMLString, _copyXMLString);
    }

    @Test

    void testLineByLine() {
        File testFile = new File(YMarshal.class.getResource("MakeRecordings.xml").getFile());
//System.out.println("origXML\n" + _originalXMLString);
//YNet r = _originalSpec.getRootNet();
//System.out.println("r.hashCode() = " + r.hashCode());
//System.out.println("r.getInputCondition() = " + r.getInputCondition().toXML());
//Map ne = r.getNetElements();
//System.out.println("r.getNetElements() = " + ne);
//YTask t = ((YTask) r.getNetElement("record"));
//System.out.println("t.toXML() = " + t.toXML(false));
//YMultiInstanceAttributes mi = t.getMultiInstanceAttributes();
//System.out.println("this should be 1: " + mi.getMinInstances());
//System.out.println("this should be 1: " + mi.getMaxInstances());
//System.out.println("this should be 1: " + mi.getThreshold());
        SAXBuilder builder = new SAXBuilder();
        Document doc = null;
        try {
            doc = builder.build(new StringReader(_originalXMLString));
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());
        String groomedOriginalXMLString = outputter.outputString(doc);
        groomedOriginalXMLString = groomedOriginalXMLString.replaceAll(" />", "/>");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(testFile));
            String line = null;
            for (int i = 1; (line = reader.readLine()) != null; i++) {
                line = line.trim();
                // Skip empty lines, the specificationSet header (version changes during marshalling),
                // and bare <metaData/> (marshalling always produces full metaData element)
                if (line.isEmpty() || line.startsWith("<specificationSet") || line.startsWith("</specificationSet")
                        || line.equals("<metaData/>")) {
                    continue;
                }
                if (groomedOriginalXMLString.indexOf(line) == -1) {
                    fail("\n[Error]:line[" + i + "]" + testFile.getName() + " \"" +
                            line + "\" not found in XML-isation");
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
