package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.CadsNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;

/**
 * Service-level capability tests for Group L — Filter Operations (5 capabilities).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilterCapabilityTest {

    DataModellingService service;
    List<CadsNode> mixedNodes;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping");
        service = DataModellingModule.create();
        // CadsNode(id, name, type, owner) — no tags field
        mixedNodes = List.of(
            new CadsNode("n1", "OrderService",     "microservice", "order-team"),
            new CadsNode("n2", "InventoryService", "microservice", "inventory-team"),
            new CadsNode("n3", "OrderDB",          "relational",   "order-team"),
            new CadsNode("n4", "EventBus",         "messaging",    "platform-team")
        );
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) service.close();
    }

    // ── Group L: FILTER_NODES_BY_OWNER ──────────────────────────────────────

    @Test @CapabilityTest(FILTER_NODES_BY_OWNER)
    void filterNodesByOwner_matchingOwner_returnsOnlyMatchingNodes() {
        var result = service.filterNodesByOwner(mixedNodes, "order-team");
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test @CapabilityTest(FILTER_NODES_BY_OWNER)
    void filterNodesByOwner_nonexistentOwner_returnsEmptyList() {
        var result = service.filterNodesByOwner(mixedNodes, "nonexistent-team");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test @CapabilityTest(FILTER_NODES_BY_OWNER)
    void filterNodesByOwner_emptyNodeList_returnsEmpty() {
        var result = service.filterNodesByOwner(List.of(), "order-team");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Group L: FILTER_RELATIONSHIPS_BY_OWNER ──────────────────────────────

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_OWNER)
    void filterRelationshipsByOwner_emptyJson_returnsEmptyList() {
        var result = service.filterRelationshipsByOwner("[]", "order-team");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_OWNER)
    void filterRelationshipsByOwner_nonexistentOwner_returnsEmpty() {
        var result = service.filterRelationshipsByOwner("[]", "no-such-team");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Group L: FILTER_NODES_BY_INFRASTRUCTURE_TYPE ────────────────────────

    @Test @CapabilityTest(FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    void filterNodesByInfrastructureType_relational_returnsOnlyRelationalNodes() {
        var result = service.filterNodesByInfrastructureType(mixedNodes, "relational");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("OrderDB", result.get(0).name());
    }

    @Test @CapabilityTest(FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    void filterNodesByInfrastructureType_microservice_returnsBothMicroservices() {
        var result = service.filterNodesByInfrastructureType(mixedNodes, "microservice");
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test @CapabilityTest(FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    void filterNodesByInfrastructureType_unknownType_returnsEmpty() {
        var result = service.filterNodesByInfrastructureType(mixedNodes, "quantum-computer");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Group L: FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE ────────────────

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE)
    void filterRelationshipsByInfrastructureType_emptyJson_returnsEmpty() {
        var result = service.filterRelationshipsByInfrastructureType("[]", "relational");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE)
    void filterRelationshipsByInfrastructureType_unknownType_returnsEmpty() {
        var result = service.filterRelationshipsByInfrastructureType("[]", "unknown-infra");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Group L: FILTER_BY_TAGS ──────────────────────────────────────────────

    @Test @CapabilityTest(FILTER_BY_TAGS)
    void filterByTags_knownTag_returnsNonNull() {
        var result = service.filterByTags(mixedNodes, List.of("orders"));
        assertNotNull(result);
    }

    @Test @CapabilityTest(FILTER_BY_TAGS)
    void filterByTags_unknownTag_returnsEmptyList() {
        var result = service.filterByTags(mixedNodes, List.of("zzz-no-such-tag-abc123"));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test @CapabilityTest(FILTER_BY_TAGS)
    void filterByTags_emptyNodeList_returnsEmpty() {
        var result = service.filterByTags(List.of(), List.of("orders"));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
