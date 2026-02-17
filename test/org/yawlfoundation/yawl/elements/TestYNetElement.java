package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:14:05
 *
 */
class TestYNetElement{

    @Test

    void testNothingMuch(){
        YNetElement netEl = new YCondition("netElement1", null, null);
        assertTrue(netEl.getID().equals("netElement1"));
    }
}
