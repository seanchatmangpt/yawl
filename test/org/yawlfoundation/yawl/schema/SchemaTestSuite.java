package org.yawlfoundation.yawl.schema;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * @author Lachlan Aldred
 * Date: 6/08/2004
 * Time: 16:47:21
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    TestSchemaHandler.class,
    TestSchemaHandlerValidation.class
})
public class SchemaTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Schema Test Suite");
        suite.addTestSuite(TestSchemaHandler.class);
        suite.addTestSuite(TestSchemaHandlerValidation.class);
        return suite;
    }
}
