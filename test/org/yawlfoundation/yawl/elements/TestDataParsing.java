package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

import org.jdom2.JDOMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YSchemaBuildingException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 *
 * @author Lachlan Aldred
 * Date: 23/02/2004
 * Time: 17:23:58
 *
 */
class TestDataParsing {
    private YSpecification _badSpecification;

    @BeforeEach

    void setUp() {
    }

    @Test

    void testSchemaCatching() throws JDOMException, IOException, YSchemaBuildingException {
        Exception d = null;
        try {
            File file1 = new File(getClass().getResource("duplicateDataSpecification.xml").getFile());
            _badSpecification = (YSpecification) YMarshal.unmarshalSpecifications(StringUtil.fileToString(
                    file1.getAbsolutePath())).get(0);
        } catch (YSyntaxException e) {
            d = e;
        }
        assertTrue(d != null);
    }
}
