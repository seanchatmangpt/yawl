package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.jdom2.JDOMException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YMultiInstanceAttributes;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 *
 * Author: Lachlan Aldred
 * Date: 6/06/2003
 * Time: 12:28:34
 *
 */
@Tag("integration")
class TestEngineSystem1 {
    private YNetRunner _netRunner;
    private YIdentifier _idForTopNet;
    private YWorkItemRepository _workItemRepository;
    private int _sleepTime = 100;
    private YEngine _engine;
    private YSpecification _specification;

    @BeforeEach

    void setUp() throws YSchemaBuildingException, YSyntaxException, JDOMException, IOException {
        URL fileURL = getClass().getResource("YAWL_Specification3.xml");
        assertNotNull(fileURL, "Test specification file YAWL_Specification3.xml must exist in classpath");
        File yawlXMLFile = new File(fileURL.getFile());
        _specification = YMarshal.
                            unmarshalSpecifications(StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        _engine = YEngine.getInstance();
        _workItemRepository = _engine.getWorkItemRepository();
    }

    @Test

    void testDecomposingNets() throws YDataStateException, YStateException, YEngineStateException, YQueryException, YSchemaBuildingException, YPersistenceException {
        try {
            EngineClearer.clear(_engine);
            _engine.loadSpecification(_specification);
            _idForTopNet = _engine.startCase(_specification.getSpecificationID(), null,
                    null, null, null, null, false);
            //enabled btop
            Set currWorkItems = _workItemRepository.getEnabledWorkItems();
            YWorkItem anItem = (YWorkItem) currWorkItems.iterator().next();
            assertTrue(currWorkItems.size() == 1 && anItem.getTaskID().equals("b-top"));
            Thread.sleep(_sleepTime);
            //fire btop
//            _localWorklist.startOneWorkItemAndSetOthersToFired(anItem.getCaseID().toString(), anItem.getTaskID());
            anItem = _engine.startWorkItem(anItem, _engine.getExternalClient("admin"));
            currWorkItems = _workItemRepository.getEnabledWorkItems();
            assertTrue(currWorkItems.isEmpty());
            currWorkItems = _workItemRepository.getExecutingWorkItems();
            assertTrue(currWorkItems.size() == 1, "" + currWorkItems.size());
            anItem = (YWorkItem) currWorkItems.iterator().next();
            Thread.sleep(_sleepTime);
            //complete btop
//            _localWorklist.setWorkItemToComplete(anItem.getCaseID().toString(), anItem.getTaskID(),"<data/>");
            _engine.completeWorkItem(anItem, "<data/>", null, WorkItemCompletion.Normal);
            //should atumatically fire multi inst comp task ctop
            //get mi attributes of f-leaf-c
            List leafNetRunners = new Vector();
            Set enabled = _workItemRepository.getEnabledWorkItems();
            YMultiInstanceAttributes fLeafCMIAttributes = null;
            Iterator iter = enabled.iterator();
            while (iter.hasNext()) {
                YWorkItem item = (YWorkItem) iter.next();
                YNetRunner leafCRunner = _engine.getNetRunner(item.getCaseID());
                if (leafNetRunners.size() == 0) {
                    fLeafCMIAttributes =
                            ((YAtomicTask) leafCRunner.getNetElement("f-leaf-c"))
                            .getMultiInstanceAttributes();
                }
                leafNetRunners.add(leafCRunner);
//System.out.println("item "+item);
                assertTrue(leafCRunner != null);
            }
            //fire all the enabled e-leaf-c nodes
            while (_workItemRepository.getEnabledWorkItems().size() > 0) {
                Vector v = new Vector(_workItemRepository.getEnabledWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
                assertEquals(anItem.getCaseID().getParent(), _idForTopNet);
                assertTrue(anItem.getTaskID().equals("e-leaf-c"));
                assertTrue(anItem.getStatus() == YWorkItemStatus.statusEnabled);
                //fire e-leaf-c
//                _localWorklist.startOneWorkItemAndSetOthersToFired(anItem.getCaseID().toString(), anItem.getTaskID());
                _engine.startWorkItem(anItem, _engine.getExternalClient("admin"));
                assertTrue(anItem.getStatus() == YWorkItemStatus.statusIsParent,
                        "Item status ("+anItem.getStatus()+") should be is parent.");
                Set executingChildren = _workItemRepository.getExecutingWorkItems();
                assertTrue(executingChildren.containsAll(anItem.getChildren()));
                Thread.sleep(_sleepTime);
            }
            //complete e-leaf-c
            while (_workItemRepository.getExecutingWorkItems().size() > 0) {
                Vector v = new Vector(_workItemRepository.getExecutingWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
//                _localWorklist.setWorkItemToComplete(
//                        anItem.getCaseID().toString(), anItem.getTaskID(),"<data/>");
                _engine.completeWorkItem(anItem, "<data/>", null, WorkItemCompletion.Normal);
                assertTrue(_workItemRepository.get(
                        anItem.getCaseID().toString(), anItem.getTaskID())
                        == null);
                Thread.sleep(_sleepTime);
            }
            currWorkItems = _workItemRepository.getEnabledWorkItems();
            assertTrue(currWorkItems.size() == _workItemRepository.getWorkItems().size());
            // fire all the enabled f-leaf-c and g-leaf-c children
            while (_workItemRepository.getEnabledWorkItems().size() > 0) {
                Vector v = new Vector(_workItemRepository.getEnabledWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
//System.out.println("v.size() : " + v.size() + " InstanceValidator: " + InstanceValidator);
                anItem = (YWorkItem) v.get(temp);
                assertTrue(anItem.getTaskID().equals("f-leaf-c")
                        || anItem.getTaskID().equals("g-leaf-c"));
//                _localWorklist.startOneWorkItemAndSetOthersToFired(
//                        anItem.getCaseID().toString(), anItem.getTaskID());
                _engine.startWorkItem(anItem, _engine.getExternalClient("admin"));
                if (anItem.getTaskID().equals("g-leaf-c")) {
                    assertTrue(anItem.getChildren().size() == 1);
                    assertTrue(((YWorkItem) anItem.getChildren().iterator().next())
                            .getStatus() == YWorkItemStatus.statusExecuting);
                } else {
                    int numChildren = anItem.getChildren().size();
                    assertTrue(numChildren >= fLeafCMIAttributes.getMinInstances()
                            && numChildren <= fLeafCMIAttributes.getMaxInstances());
                }
                Thread.sleep(_sleepTime);
            }
            //start all the fired f-leaf-c nodes
            while (_workItemRepository.getFiredWorkItems().size() > 0) {

                Vector v = new Vector(_workItemRepository.getFiredWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
                assertTrue(anItem.getTaskID().equals("f-leaf-c"));
//                _localWorklist.startOneWorkItemAndSetOthersToFired(
//                        anItem.getCaseID().toString(), anItem.getTaskID());
                _engine.startWorkItem(anItem, _engine.getExternalClient("admin"));
                assertTrue(anItem.getStatus() == YWorkItemStatus.statusExecuting);
                assertTrue(_workItemRepository.getWorkItems().contains(anItem));
                Thread.sleep(_sleepTime);
            }
            //complete all of the f-leaf-c and g-leaf-c children
            while (_workItemRepository.getExecutingWorkItems().size() > 0) {
                Vector v = new Vector(_workItemRepository.getExecutingWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
                assertTrue(anItem.getTaskID().equals("f-leaf-c")
                        || anItem.getTaskID().equals("g-leaf-c"));
//                _localWorklist.setWorkItemToComplete(anItem.getCaseID().toString(), anItem.getTaskID(),"<data/>");
                _engine.completeWorkItem(anItem, "<data/>", null, WorkItemCompletion.Normal);
                if (anItem.getTaskID().equals("g-leaf-c")) {
                    assertFalse(_workItemRepository.getWorkItems().contains(anItem));
                }
                Thread.sleep(_sleepTime);
            }
            //fire all but one of the h-leaf-c children
            while (_workItemRepository.getEnabledWorkItems().size() > 1) {
                Vector v = new Vector(_workItemRepository.getEnabledWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
                assertTrue(anItem.getTaskID().equals("h-leaf-c"));
//                _localWorklist.startOneWorkItemAndSetOthersToFired(anItem.getCaseID().toString(), anItem.getTaskID());
                _engine.startWorkItem(anItem, _engine.getExternalClient("admin"));
                assertTrue(anItem.getChildren().size() == 1);
                assertTrue(((YWorkItem) anItem.getChildren().iterator().next()).getStatus()
                        == YWorkItemStatus.statusExecuting);
                Thread.sleep(_sleepTime);
            }
            /*
             * Complete two of the h-leaf-c children to ensure that:
             * All child nets are dead, finished.
             * All work items to-do with those nets are removed.
             * And that d-top is enabled. - done
             */
            YNetRunner topNetRunner = _engine.getNetRunner(_idForTopNet);
            while (_workItemRepository.getExecutingWorkItems().size() > 0) {
                Vector v = new Vector(_workItemRepository.getExecutingWorkItems());
                int temp = (int) Math.abs(Math.floor(Math.random() * v.size()));
                anItem = (YWorkItem) v.get(temp);
                assertTrue(anItem.getTaskID().equals("h-leaf-c"));
//                _localWorklist.setWorkItemToComplete(anItem.getCaseID().toString(), anItem.getTaskID(),"<data/>");
                _engine.completeWorkItem(anItem, "<data/>", null, WorkItemCompletion.Normal);
                assertFalse(_workItemRepository.getWorkItems().contains(anItem));
                Thread.sleep(_sleepTime);
            }
            assertTrue(_workItemRepository.getWorkItems().size() == 0,
                    "" + _workItemRepository.getWorkItems());
            Iterator iterator = leafNetRunners.iterator();
            while (iterator.hasNext()) {
                YNetRunner leafCRunner = (YNetRunner) iterator.next();
                assertFalse(leafCRunner.isAlive(), "" + leafCRunner.getCaseID());
            }
            assertTrue(_workItemRepository.getWorkItems().size() == 0);
            assertFalse(topNetRunner.isAlive());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
