package org.yawlfoundation.yawl.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 */
class TestYSyntaxException {

	private String messageIn = "This is an unexceptional exception";

    @Test

    void testConstructor()
    {
        YSyntaxException syntaxException = new YSyntaxException(messageIn);
        assertEquals(syntaxException.getMessage(), messageIn);
    }

}
