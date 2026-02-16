package org.yawlfoundation.yawl.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 */
class TestYConnectivityException {

	private String messageIn = "This is an unexceptional exception";

	@Test

	void testConstructor()
	{
		YConnectivityException yce = new YConnectivityException(messageIn);
		String messageOut = yce.getMessage();
		this.assertEquals(messageOut, messageIn);
	}
}
