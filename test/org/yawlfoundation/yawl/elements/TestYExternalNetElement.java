package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 */
class TestYExternalNetElement{
    private YExternalNetElement _aCondition;
    private YExternalNetElement _aTask;

    @BeforeEach

    void setUp(){
        YSpecification spec = new YSpecification("");
        spec.setVersion(YSchemaVersion.Beta2);
        YNet deadNet = new YNet("aNetName", spec);
        _aCondition = new YCondition("c1", deadNet);
        _aTask = new YAtomicTask("et1", YTask._AND, YTask._AND, deadNet);
        YFlow f= new YFlow(_aCondition, _aTask);
        _aCondition.addPostset(f);
        f = new YFlow(_aTask, _aCondition);
        _aTask.addPostset(f);
    }

    @Test

    void testFlowStuff(){
        assertTrue(_aTask.getPostsetElement("c1").equals(_aCondition));
        assertTrue(_aCondition.getPostsetElement("et1").equals(_aTask));
        assertTrue(_aTask.getPresetElement("c1").equals(_aCondition));
        assertTrue(_aCondition.getPresetElement("et1").equals(_aTask));
        YVerificationHandler handler = new YVerificationHandler();
        _aTask.verify(handler);
        if (handler.hasMessages()) {
            for (YVerificationMessage msg : handler.getMessages()) {
                System.out.println(msg.getMessage());
            }
            fail(handler.getMessages().get(0).getMessage());
        }
    }
}
