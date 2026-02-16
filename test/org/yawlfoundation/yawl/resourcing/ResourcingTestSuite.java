package org.yawlfoundation.yawl.resourcing;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * JUnit 5 Test Suite for Resourcing (with JUnit 3 backward compatibility)
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

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Resourcing Test Suite");
        suite.addTestSuite(TestResourceSpecXML.class);
        suite.addTestSuite(TestGetSelectors.class);
        suite.addTestSuite(TestHibernate.class);
        suite.addTestSuite(TestDB.class);
        suite.addTestSuite(TestJDBC.class);
        suite.addTestSuite(TestParseXML.class);
        return suite;
    }
}
