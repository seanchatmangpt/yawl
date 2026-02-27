package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

/**
 *
 * Author: Lachlan Aldred
 * Date: 26/09/2003
 * Time: 10:16:44
 *
 */
@Tag("unit")
class TestYFlowsInto{
    private YFlow _flowsInto, _flowsInto2,  _flowsInto4, _flowsInto5;
    private YExternalNetElement _XORSplit;
    private YExternalNetElement _ANDSplit;
    private YCondition _condition1;
    private YVerificationHandler handler = new YVerificationHandler();

    @BeforeEach

    void setUp(){
        YSpecification spec = new YSpecification("");
        spec.setVersion(YSchemaVersion.Beta2);

        YNet net1 = new YNet("net1", spec);
        _XORSplit = new YAtomicTask("XORSplit_1", YTask._AND, YTask._XOR, net1);
        _ANDSplit = new YAtomicTask("ANDSplit_1", YTask._AND, YTask._AND, new YNet("net2", spec));
        _condition1 = new YCondition("condition1", null);
        _flowsInto = new YFlow(_XORSplit, _condition1);
        _flowsInto2 = new YFlow(_ANDSplit, _condition1);
//        _flowsInto3 = new YFlow(null, null);
        _flowsInto4 = new YFlow(_condition1, _condition1);
        _flowsInto5 = new YFlow(new YOutputCondition("output1",null), new YInputCondition("input1",null));

    }

    @Test

    void testToString(){
        assertTrue(_flowsInto.toString().startsWith("Flow"), _flowsInto.toString());
    }

    @Test

    void testXOR_ORSplitNeedsDefaultFlowNotBoth(){
        handler.reset();
        _flowsInto.verify(null, handler);
        assertTrue(handler.getMessageCount() == 2, "Unexpected messages");
        _flowsInto.setIsDefaultFlow(true);
        handler.reset();
        _flowsInto.verify(null, handler);
        assertTrue(handler.getMessageCount() == 1, "Unexpected messages");
        _flowsInto.setXpathPredicate("hi mum");
        /*
        null [error] any flow from any Element (YAtomicTask:XORSplit_1) to any Element (YCondition:condition1) must occur with the bounds of the same net.
        null [error] any flow from any XOR-split (YAtomicTask:XORSplit_1) must have either a predicate or be a default flow (cannot be both).
        null [error] any flow from any XOR-split (YAtomicTask:XORSplit_1) that has a predicate, must have an eval ordering.
        */
        handler.reset();
        _flowsInto.verify(null, handler);
        assertTrue(handler.getMessageCount() == 3, "Unexpected messages");
    }

    @Test

    void testANDCantBeDefaultFlow(){
        _flowsInto2.setIsDefaultFlow(true);
        _flowsInto2.setXpathPredicate("hi mum");
        _flowsInto2.setEvalOrdering(Integer.valueOf(5));
        handler.reset();
        _flowsInto2.verify(null, handler);
        if (handler.getMessageCount() != 4) {
            for (YVerificationMessage msg : handler.getMessages()) {
                System.out.println(msg.getMessage());
            }
        }
        assertTrue(handler.getMessageCount() == 4);
    }

    @Test

    void testConditionToCondition(){
        handler.reset();
        _flowsInto4.verify(null, handler);
        assertTrue(handler.getMessageCount() == 1);

        _flowsInto4.setXpathPredicate("hi mum");
        handler.reset();
        _flowsInto4.verify(null, handler);
        assertTrue(handler.getMessageCount() == 2);

        _flowsInto4.setIsDefaultFlow(true);
        handler.reset();
        _flowsInto4.verify(null, handler);
        assertTrue(handler.getMessageCount() == 3);

        _flowsInto4.setEvalOrdering(Integer.valueOf(100));
        handler.reset();
        _flowsInto4.verify(null, handler);
        assertTrue(handler.getMessageCount() == 4);
    }

    @Test

    void testInputOutputFlow(){
        handler.reset();
         _flowsInto5.verify(null, handler);
         assertTrue(handler.getMessageCount() == 3);
    }
}
