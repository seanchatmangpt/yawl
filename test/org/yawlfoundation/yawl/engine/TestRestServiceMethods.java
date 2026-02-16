package org.yawlfoundation.yawl.engine;

import org.jdom2.JDOMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YSchemaBuildingException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 *
 * @author Lachlan Aldred
 * Date: 27/02/2004
 * Time: 12:43:53
 *
 */
class TestRestServiceMethods{
    private YEngine _engine;
    private YSpecification _specification;
    private YSpecification _specification2;

    @BeforeEach

    void setUp() throws YSchemaBuildingException, YEngineStateException, YSyntaxException, JDOMException, IOException, YPersistenceException {
        URL makeMusic = getClass().getResource("MakeMusic.xml");
        URL makeMusic2 = getClass().getResource("MakeMusic2.xml");
        File mmFile = new File(makeMusic.getFile());
        File mm2File = new File(makeMusic2.getFile());
        _specification = (YSpecification) YMarshal.
                unmarshalSpecifications(StringUtil.fileToString(mmFile.getAbsolutePath())).get(0);
        _specification2 = (YSpecification) YMarshal.
                unmarshalSpecifications(StringUtil.fileToString(mm2File.getAbsolutePath())).get(0);
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
        _engine.loadSpecification(_specification);
        _engine.loadSpecification(_specification2);

    }

    @Test

    void testGetTask(){
        YTask task = _engine.getTaskDefinition(_specification.getSpecificationID(), "learn");
        assertTrue(task != null);
    }

    @Test

    void testGetTaskWithoutSpecification(){
        YTask task = _engine.getTaskDefinition(new YSpecificationID("badSpecName"), "irrelevant");
        assertTrue(task == null);
    }
}
