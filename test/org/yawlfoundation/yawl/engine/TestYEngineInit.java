package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test for YEngine initialization with H2 config.
 * Ensures YEngine.getInstance() succeeds when build.properties has database.type=h2.
 */
class TestYEngineInit {

    @Test
    void testEngineInstanceWithH2() {
        YEngine engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine.getInstance() must return a non-null instance when H2 is configured");
    }
}
