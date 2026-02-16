package org.yawlfoundation.yawl.elements.state;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:52:04
 * 
 */
@Suite
@SelectClasses({
    TestYIdentifier.class,
    TestYMarking.class,
    TestYSetOfMarkings.class
})
public class StateTestSuite {
}
