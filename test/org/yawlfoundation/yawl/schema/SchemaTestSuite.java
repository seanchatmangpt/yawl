package org.yawlfoundation.yawl.schema;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * @author Lachlan Aldred
 * Date: 6/08/2004
 * Time: 16:47:21
 *
 * JUnit 5 Test Suite
 */
@Suite
@SuiteDisplayName("Schema Test Suite")
@SelectClasses({
    TestSchemaHandler.class,
    TestSchemaHandlerValidation.class
})
public class SchemaTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
