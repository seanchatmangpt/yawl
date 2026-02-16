package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.yawlfoundation.yawl.authentication.AuthenticationTestSuite;
import org.yawlfoundation.yawl.elements.ElementsTestSuite;
import org.yawlfoundation.yawl.elements.state.StateTestSuite;
import org.yawlfoundation.yawl.engine.EngineTestSuite;
import org.yawlfoundation.yawl.exceptions.ExceptionTestSuite;
import org.yawlfoundation.yawl.logging.LoggingTestSuite;
import org.yawlfoundation.yawl.schema.SchemaTestSuite;
import org.yawlfoundation.yawl.stateless.StatelessTestSuite;
import org.yawlfoundation.yawl.unmarshal.UnmarshallerTestSuite;
import org.yawlfoundation.yawl.util.UtilTestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:33:26
 *
 * Master JUnit 5 Test Suite for all YAWL tests
 */
@Suite
@SelectClasses({
    ElementsTestSuite.class,
    StateTestSuite.class,
    StatelessTestSuite.class,
    EngineTestSuite.class,
    ExceptionTestSuite.class,
    LoggingTestSuite.class,
    SchemaTestSuite.class,
    UnmarshallerTestSuite.class,
    UtilTestSuite.class,
    AuthenticationTestSuite.class
})
public class TestAllYAWLSuites {
}
