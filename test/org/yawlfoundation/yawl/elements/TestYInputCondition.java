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
 * Time: 11:13:27
 *
 */
@Tag("unit")
class TestYInputCondition{
    private YInputCondition _invalidInputCondition;

    @BeforeEach

    void setUp(){
        YSpecification spec = new YSpecification("");
        spec.setVersion(YSchemaVersion.Beta2);

        YNet deadNet = new YNet("aNetName", spec);
        _invalidInputCondition = new YInputCondition("ic1", "input", deadNet);
        _invalidInputCondition.addPostset(new YFlow(_invalidInputCondition, new YCondition("c2", deadNet)));
        _invalidInputCondition.addPreset(new YFlow(new YAtomicTask("at1", YTask._AND, YTask._AND, deadNet), _invalidInputCondition));
    }

    @Test

    void testInvalidInputCondition(){
        YVerificationHandler handler = new YVerificationHandler();
        _invalidInputCondition.verify(handler);
        assertTrue(handler.getMessageCount() >= 1,
                "Should receive at least 1 error (input condition preset must be empty): " + handler.getMessages());
    }
}
