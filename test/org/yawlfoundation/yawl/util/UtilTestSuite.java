package org.yawlfoundation.yawl.util;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 16:00:43
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
public class UtilTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        return new TestSuite("Util Test Suite");
    }
}
