package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:14:55
 *
 */
@Tag("unit")
class TestYOutputCondition{

    private YOutputCondition _invalidOutputCondition;

    @BeforeEach

    void setUp(){
        YSpecification spec = new YSpecification("");
        spec.setVersion(YSchemaVersion.Beta2);
        YNet deadNet = new YNet("aNetName", spec);
        _invalidOutputCondition = new YOutputCondition("ic1", "input", deadNet);
        _invalidOutputCondition.addPostset(new YFlow(_invalidOutputCondition, new YCondition("c2", deadNet)));
        _invalidOutputCondition.addPostset(new YFlow(_invalidOutputCondition, new YAtomicTask("at1", YTask._AND, YTask._AND, deadNet)));
    }

    @Test

    void testInvalidInputCondition(){
        YVerificationHandler handler = new YVerificationHandler();
        _invalidOutputCondition.verify(handler);
        /*
            OutputCondition:ic1 postset must be empty: [YCondition:c2, YAtomicTask:at1]
            OutputCondition:ic1 The preset size must be > 0
        */
        if(handler.getMessageCount() != 2){
            fail("Should receive 2 error messages, but didn't ( messages size == " + handler.getMessageCount());
        }
    }
}
