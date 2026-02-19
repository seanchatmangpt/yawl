package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.jdom2.JDOMException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
/**
 *
 * Author: Lachlan Aldred
 * Date: 23/07/2003
 * Time: 12:24:13
 *
 */
@Tag("integration")
class TestEngineAgainstImproperCompletionOfASubnet {

    private YIdentifier _idForTopNet;
    private YWorkItemRepository _workItemRepository;
    private long _sleepTime = 500;
    private YEngine engine;
    private YSpecification _specification;

    @BeforeEach

    void setUp() throws YSyntaxException, JDOMException, YSchemaBuildingException, IOException {
//        new YLocalWorklist("Barbara");
        URL fileURL = getClass().getResource("ImproperCompletion.xml");
    @Execution(ExecutionMode.SAME_THREAD)

        File yawlXMLFile = new File(fileURL.getFile());
        _specification = null;
        _specification = (YSpecification) YMarshal.
                unmarshalSpecifications(StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        engine = YEngine.getInstance();
        _workItemRepository = engine.getWorkItemRepository();
    }

    public synchronized void testImproperCompletionSubnet() throws YDataStateException, YEngineStateException, YStateException, YQueryException, YSchemaBuildingException, YPersistenceException {
        EngineClearer.clear(engine);
        engine.loadSpecification(_specification);
        _idForTopNet = engine.startCase(_specification.getSpecificationID(),null, null, null, null, null, false);
        assertTrue(_workItemRepository.getCompletedWorkItems().size() == 0);
        assertTrue(_workItemRepository.getEnabledWorkItems().size() == 1);
        assertTrue(_workItemRepository.getExecutingWorkItems().size() == 0);
        assertTrue(_workItemRepository.getFiredWorkItems().size() == 0);
        while (_workItemRepository.getEnabledWorkItems().size() > 0 ||
                _workItemRepository.getFiredWorkItems().size() > 0 ||
                _workItemRepository.getExecutingWorkItems().size() > 0) {
            YWorkItem item;
            while (_workItemRepository.getEnabledWorkItems().size() > 0) {
                item = (YWorkItem) _workItemRepository.getEnabledWorkItems().iterator().next();
//System.out.println("TestEngine::() item = " + item);
                engine.startWorkItem(item, engine.getExternalClient("admin"));
                try{ Thread.sleep(_sleepTime);}
                catch(InterruptedException ie){ie.printStackTrace();}
            }
            while (_workItemRepository.getFiredWorkItems().size() > 0) {
                item = (YWorkItem) _workItemRepository.getFiredWorkItems().iterator().next();
                engine.startWorkItem(item, engine.getExternalClient("admin"));
                try{ Thread.sleep(_sleepTime);}
                catch(InterruptedException ie){ie.printStackTrace();}
            }
            while (_workItemRepository.getExecutingWorkItems().size() > 0) {
                item = _workItemRepository.getExecutingWorkItems().iterator().next();
                engine.completeWorkItem(item, "<data/>", null, WorkItemCompletion.Normal);
                try{ Thread.sleep(_sleepTime);}
                catch(InterruptedException ie){ie.printStackTrace();}
            }
        }
    }
}
