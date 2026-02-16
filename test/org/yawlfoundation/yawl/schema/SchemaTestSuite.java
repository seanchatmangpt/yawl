package org.yawlfoundation.yawl.schema;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 /**
 * 
 * @author Lachlan Aldred
 * Date: 6/08/2004
 * Time: 16:47:21
 * 
 */
@Suite
@SelectClasses({
    TestSchemaHandler.class,
    TestSchemaHandlerValidation.class
})
public class SchemaTestSuite {
}
