package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Service-level capability tests for Group K — Domain Operations (4 capabilities).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DomainCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) service.close();
    }

    // ── Group K: CREATE_DOMAIN ──────────────────────────────────────────────

    @Test @CapabilityTest(CREATE_DOMAIN)
    void createDomain_withNameAndDescription_returnsPopulatedDomain() {
        var domain = service.createDomain("orders-domain", "Order management system");
        assertNotNull(domain);
        assertEquals("orders-domain", domain.name());
    }

    @Test @CapabilityTest(CREATE_DOMAIN)
    void createDomain_withNullDescription_returnsValidDomain() {
        var domain = service.createDomain("test-domain", null);
        assertNotNull(domain);
        assertEquals("test-domain", domain.name());
    }

    @Test @CapabilityTest(CREATE_DOMAIN)
    void createDomain_newDomain_hasEmptySystems() {
        var domain = service.createDomain("empty-domain", "No systems yet");
        assertNotNull(domain);
        assertNotNull(domain.systems());
        assertTrue(domain.systems().isEmpty());
    }

    // ── Group K: ADD_SYSTEM_TO_DOMAIN ───────────────────────────────────────

    @Test @CapabilityTest(ADD_SYSTEM_TO_DOMAIN)
    void addSystemToDomain_validSystem_domainContainsSystem() {
        var domain = service.createDomain("orders-domain", "Order management");
        var sys = new SystemDefinition("OrderService", "Order processing service", "order-team");
        var updated = service.addSystemToDomain(domain, sys);
        assertNotNull(updated);
        assertFalse(updated.systems().isEmpty());
    }

    @Test @CapabilityTest(ADD_SYSTEM_TO_DOMAIN)
    void addSystemToDomain_twoSystems_domainHasBoth() {
        var domain = service.createDomain("orders-domain", "Order management");
        var sys1 = new SystemDefinition("OrderService", "Order processing", "order-team");
        var sys2 = new SystemDefinition("PaymentService", "Payment processing", "payment-team");
        var after1 = service.addSystemToDomain(domain, sys1);
        var after2 = service.addSystemToDomain(after1, sys2);
        assertNotNull(after2);
        assertEquals(2, after2.systems().size());
    }

    // ── Group K: ADD_CADS_NODE_TO_DOMAIN ────────────────────────────────────

    @Test @CapabilityTest(ADD_CADS_NODE_TO_DOMAIN)
    void addCadsNodeToDomain_validNode_domainIsNonNull() {
        var domain = service.createDomain("orders-domain", "Order management");
        var node = new CadsNode("n1", "OrderDB", "relational", "order-team");
        var updated = service.addCadsNodeToDomain(domain, node);
        assertNotNull(updated);
    }

    @Test @CapabilityTest(ADD_CADS_NODE_TO_DOMAIN)
    void addCadsNodeToDomain_preservesDomainName() {
        var domain = service.createDomain("named-domain", "desc");
        var node = new CadsNode("n2", "EventBus", "messaging", "platform-team");
        var updated = service.addCadsNodeToDomain(domain, node);
        assertEquals("named-domain", updated.name());
    }

    // ── Group K: ADD_ODCS_NODE_TO_DOMAIN ────────────────────────────────────

    @Test @CapabilityTest(ADD_ODCS_NODE_TO_DOMAIN)
    void addOdcsNodeToDomain_validNode_domainIsNonNull() {
        var domain = service.createDomain("orders-domain", "Order management");
        var odcsNode = new OdcsNode("o1", "OrdersContract", "order-team", ODCS_YAML);
        var updated = service.addOdcsNodeToDomain(domain, odcsNode);
        assertNotNull(updated);
    }

    @Test @CapabilityTest(ADD_ODCS_NODE_TO_DOMAIN)
    void addOdcsNodeToDomain_preservesDomainName() {
        var domain = service.createDomain("contract-domain", "Contract management");
        var odcsNode = new OdcsNode("o2", "ItemsContract", "items-team", ODCS_YAML);
        var updated = service.addOdcsNodeToDomain(domain, odcsNode);
        assertEquals("contract-domain", updated.name());
    }
}
