package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.jdom2.JDOMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 *
 * Author: Lachlan Aldred
 * Date: 29/05/2003
 * Time: 16:19:02
 *
 */
class TestSimpleExecutionUseCases{
    private YIdentifier _caseId;
    private YWorkItemRepository _workItemRepository;
    private YEngine _engine;

    @BeforeEach

    void setUp() throws YSchemaBuildingException, YSyntaxException, YEngineStateException, YQueryException, JDOMException, IOException, YStateException, YPersistenceException, YDataStateException {
        URL fileURL = getClass().getResource("ImproperCompletion.xml");
		File yawlXMLFile = new File(fileURL.getFile());
        YSpecification specification = YMarshal.
                        unmarshalSpecifications(StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        _engine = YEngine.getInstance();
        _workItemRepository = _engine.getWorkItemRepository();
        EngineClearer.clear(_engine);
        _engine.loadSpecification(specification);
        _caseId =  _engine.startCase(specification.getSpecificationID(), null, null,
                null, new YLogDataItemList(), null, false);

    }

    @Test

    void testUseCase1(){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        YWorkItem item = _engine.getWorkItem(
                _caseId.toString() +
                ":" +
                "b-top");
        Exception f = null;
        try {
            _engine.startWorkItem(item ,_engine.getExternalClient("admin"));
        } catch (YAWLException e) {
            f =e;
        }
        assertNotNull(f);

        item = _engine.getWorkItem(
                _caseId.toString() +
                ":" +
                "a-top");
        f = null;
        try {
            _engine.startWorkItem(item ,_engine.getExternalClient("admin"));
        } catch (YAWLException e) {
            fail(e.getMessage());
        }

        Set firedWorkItems = _workItemRepository.getFiredWorkItems();
        item = (YWorkItem) firedWorkItems.iterator().next();
        try {
            _engine.startWorkItem(item, _engine.getExternalClient("admin"));
        } catch (YAWLException e) {
            fail(e.getMessage());
        }
    }
}
