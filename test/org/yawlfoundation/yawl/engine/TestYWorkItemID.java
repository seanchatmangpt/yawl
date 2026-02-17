package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Lachlan Aldred
 * Date: 25/07/2005
 * Time: 08:25:30
 *
 */
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
