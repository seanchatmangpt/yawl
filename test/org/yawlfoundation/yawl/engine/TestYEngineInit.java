package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Smoke test for YEngine initialization with H2 config.
 * Ensures YEngine.getInstance() succeeds when build.properties has database.type=h2.
 */
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class TestYEngineInit {

    @Test
    void testEngineInstanceWithH2() {
        YEngine engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine.getInstance() must return a non-null instance when H2 is configured");
    }
}
