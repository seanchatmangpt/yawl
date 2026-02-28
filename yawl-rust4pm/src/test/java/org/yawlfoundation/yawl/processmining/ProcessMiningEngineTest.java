package org.yawlfoundation.yawl.processmining;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessMiningEngineTest {

    private static final String MINIMAL_OCEL2 = """
        {
          "objectTypes": [{"name": "Order", "attributes": []}],
          "eventTypes":  [{"name": "place", "attributes": []}],
          "objects":     [{"id": "o1", "type": "Order", "attributes": []}],
          "events": [{
            "id": "e1", "type": "place",
            "time": "2024-01-01T10:00:00Z",
            "attributes": [],
            "relationships": [{"objectId": "o1", "qualifier": ""}]
          }]
        }
        """;

    @Test
    void engine_instantiation_succeeds() {
        try (Rust4pmBridge bridge = new Rust4pmBridge()) {
            ProcessMiningEngine engine = new ProcessMiningEngine(bridge);
            assertNotNull(engine);
        }
    }

    @Test
    void parseOcel2Json_throws_UnsupportedOperationException_when_library_absent() {
        if (rust4pm_h.LIBRARY.isPresent()) return;
        try (Rust4pmBridge bridge = new Rust4pmBridge();
             ProcessMiningEngine engine = new ProcessMiningEngine(bridge)) {
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> engine.parseOcel2Json(MINIMAL_OCEL2));
            assertTrue(e.getMessage().contains("rust4pm native library not found"));
        }
    }

    @Test
    void parseAll_empty_list_returns_empty_without_native_call() throws Exception {
        try (Rust4pmBridge bridge = new Rust4pmBridge();
             ProcessMiningEngine engine = new ProcessMiningEngine(bridge)) {
            List<org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle> result =
                engine.parseAll(List.of());
            assertTrue(result.isEmpty());
        }
    }
}
