package org.yawlfoundation.yawl.engine;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.YEngine;

/**
 * Smoke test for YEngine initialization with H2 config.
 * Ensures YEngine.getInstance() succeeds when build.properties has database.type=h2.
 */
public class TestYEngineInit extends TestCase {

    public TestYEngineInit(String name) {
        super(name);
    }

    public void testEngineInstanceWithH2() {
        YEngine engine = YEngine.getInstance();
        assertNotNull("YEngine.getInstance() must return a non-null instance when H2 is configured", engine);
    }
}
