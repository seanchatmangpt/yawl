package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the CapabilityRegistry is complete (no missing @MapsToCapability).
 * This test does NOT require the native library to be loaded.
 */
class CapabilityRegistryTest {

    @Test
    void assertComplete_noViolations_doesNotThrow() {
        // Validates annotation coverage on DataModellingBridge + DataModellingServiceImpl
        // Works even when native library is absent
        assertDoesNotThrow(CapabilityRegistry::assertComplete);
    }

    @Test
    void capability_totalMatchesEnumCount() {
        assertEquals(Capability.TOTAL, Capability.values().length,
            "Capability.TOTAL must equal the number of enum constants");
    }

    @Test
    void capability_hasExpectedGroups() {
        // Spot-check that all groups A-L are present
        assertNotNull(Capability.PARSE_ODCS_YAML,        "Group A");
        assertNotNull(Capability.IMPORT_FROM_SQL,         "Group B");
        assertNotNull(Capability.IMPORT_FROM_AVRO,        "Group C");
        assertNotNull(Capability.EXPORT_TO_AVRO,          "Group D");
        assertNotNull(Capability.VALIDATE_ODPS,           "Group E");
        assertNotNull(Capability.IMPORT_BPMN_MODEL,       "Group F");
        assertNotNull(Capability.IMPORT_DMN_MODEL,        "Group G");
        assertNotNull(Capability.IMPORT_OPENAPI_SPEC,     "Group H");
        assertNotNull(Capability.MIGRATE_DATAFLOW_TO_DOMAIN, "Group I");
        assertNotNull(Capability.PARSE_SKETCH_YAML,       "Group J");
        assertNotNull(Capability.CREATE_DOMAIN,           "Group K");
        assertNotNull(Capability.FILTER_NODES_BY_OWNER,   "Group L");
    }
}
