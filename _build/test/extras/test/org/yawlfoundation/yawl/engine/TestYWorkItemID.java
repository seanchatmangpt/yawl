package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Lachlan Aldred
 * Date: 25/07/2005
 * Time: 08:25:30
 *
 */
@Tag("unit")
class TestYWorkItemID{

    @Test

    void testUniqueIDGenerator() {
        char[] alphas = UniqueIDGenerator.newAlphas();
        for(int i = 0; i < 14776336; i++){
            alphas = UniqueIDGenerator.nextInOrder(alphas);
        }
        assertEquals("0000000000000000000010000", new String(alphas));
    }

}
