package org.yawlfoundation.yawl.resourcing;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 *
 */
@Suite
@SelectClasses({
    TestResourceSpecXML.class,
    TestGetSelectors.class,
    TestHibernate.class,
    TestDB.class,
    TestJDBC.class,
    TestParseXML.class
})
public class ResourcingTestSuite {
}
