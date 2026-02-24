# ADR-026: GCP Marketplace Workflow Architecture (Petri Net Design)

**Title**: Petri Net-Based Workflow Architecture for Multi-Phase Marketplace Operations
**Date**: 2026-02-21
**Status**: ACCEPTED
**Decision Maker**: Architecture Team
**Affected Systems**: YAWL Engine, Stateless Engine, Integration Layer (MCP/A2A)

---

## 1. Problem Statement

The GCP Marketplace requires a scalable, deadlock-free workflow system to manage the complete lifecycle of marketplace transactions:

1. **Vendor Onboarding** (3 business days): Identity verification (KYC), banking setup, approval
2. **Product Listing** (5 business days): Compliance review (GDPR, export, IP), marketing review, catalog publication
3. **Customer Purchase** (5 minutes): Cart checkout, payment processing, inventory reservation, order confirmation
4. **Fulfillment** (1-15 minutes): Product provisioning, billing setup, monitoring configuration, customer access creation
5. **Post-Sales Support** (ongoing): Monitoring, issue detection, support escalation, termination, deprovisioning

### Requirements

- **Scalability**: Handle 1000+ concurrent cases (purchases, vendors, support tickets)
- **Resilience**: Timeout handling, retry logic, compensation (refunds, cleanup)
- **Correctness**: No overselling, no double-charging, no deadlocks
- **Visibility**: Clear state transitions, audit trail, SLA enforcement
- **Extensibility**: Support new products, SLA tiers, compliance rules without workflow redesign

### Constraints

- YAWL 4.0+ platform with Petri net semantics
- Cloud-native deployment (GCP Cloud Run, Cloud SQL, Cloud Pub/Sub)
- Stateless execution preferred (for horizontal scaling)
- Compliance: GDPR, export control, PCI-DSS for payment handling
- API contracts: Interface B (client API), Interface E (events), Interface X (exceptions)

---

## 2. Solution Design

### 2.1 Petri Net Structure

The workflow is modeled as a directed acyclic graph (DAG) of conditions (places) and tasks (transitions):

**Conditions (P_*)**: Represent states where tokens reside. Each token represents one case instance.
```
P_VendorInitiated → P_IdentityVerification → P_IdentityPassed → P_VendorApproved
```

**Tasks (T_*)**: Represent work items. Each task transitions one or more input conditions to output conditions.
```
T_VerifyIdentity (input: P_IdentityVerification, output: P_IdentityPassed OR P_IdentityFailed)
```

### 2.2 Control Flow Patterns

| Pattern | Usage | Example |
|---------|-------|---------|
| **Sequence** | Strict ordering | T_RegisterVendor → T_VerifyIdentity |
| **Parallel Split (AND-split)** | Concurrent execution | T_ComplianceReviewParallel creates 4 subtasks (GDPR, Export, IP, Residency) |
| **Parallel Join (AND-join)** | Synchronization point | T_ApproveVendor waits for BOTH P_IdentityPassed AND P_BankingVerified |
| **Exclusive Choice (XOR-split)** | Conditional branching | T_VerifyIdentity → P_IdentityPassed OR P_IdentityFailed |
| **Exclusive Join (XOR-join)** | Non-blocking wait | T_ConfirmOrder merges P_PaymentAuthorized and P_InventoryReserved (first one wins) |
| **Loop** | Retry with backoff | T_ProcessPayment retries up to 3 times on decline |
| **Cancellation Region** | Transactional rollback | All fulfillment subtasks cancelled on failure, compensation actions trigger |

### 2.3 Synchronization Points

Critical sections where concurrency control is required:

#### 1. **Payment Authorization** (T_ProcessPayment)
- **Mechanism**: Pessimistic lock per order_id
- **Lock timeout**: 30 seconds
- **Invariant**: Exactly one successful payment per order
- **Handling**: Idempotent payment token, retry on network error, decline after 3 attempts

#### 2. **Inventory Reservation** (T_CheckInventory)
- **Mechanism**: Distributed lock (Redis) per product SKU
- **Lock TTL**: 30 seconds
- **Fairness**: FIFO queue for lock acquisition
- **Invariant**: `total_reserved + total_sold ≤ inventory_total` (always)
- **Handling**: Timeout releases reservation, customer offered waitlist

#### 3. **Vendor Approval** (T_ApproveVendor)
- **Mechanism**: AND-join on two prerequisites
- **Timeout**: 14 days (identity valid period + banking valid period)
- **Invariant**: Both identity AND banking verified before approval
- **Handling**: Timeout escalates to human review

#### 4. **Compliance Review** (T_ComplianceJoin)
- **Mechanism**: Logical AND of 4 parallel checks
- **Timeout**: 5 business days
- **Invariant**: ALL checks must PASS (any failure blocks product)
- **Handling**: Failure notifies vendor with rejection reason, allows re-submission

#### 5. **Provisioning Coordination** (T_ProvisioningJoin)
- **Mechanism**: AND-join with timeout + partial rollback
- **Inputs**: vendor_account_created, billing_account_ready, monitoring_configured
- **Timeout**: 1 hour
- **Invariant**: All 3 succeed together or all 3 compensate (cleanup refund)
- **Handling**: On failure, trigger compensation: delete vendor account, close billing, refund customer

### 2.4 Deadlock-Free Guarantee

**Claim**: No workflow instance will deadlock indefinitely.

**Proof**:

1. **Lock Graph Acyclicity**: All locks acquired in total order (entity_id ascending). No circular wait.
   - Example: If task T acquires lock on customer_id=42, it never waits on lock on customer_id=41.

2. **Timeout Bounds**: Every P-condition has explicit timeout (SLA), every T-transition has timeout.
   - Max duration any token waits: max(SLA_1, SLA_2, ..., SLA_N) = 14 days
   - Therefore: tokens never wait indefinitely.

3. **AND-join Reachability**: Every AND-join (P_1, P_2 → T) has forward paths from entry to both P_1 and P_2.
   - No backward edge from T to entry → tokens eventually arrive.

4. **No Circular Dependencies**: Dependency graph among tasks is a DAG.
   - Task A depends on Task B only if A's input comes from B's output.
   - No edge A → B and B → A.

**Conclusion**: Given acyclic lock graph, bounded timeouts, reachable joins, and acyclic dependencies, the workflow graph is a DAG with finite timeouts. Every path from entry to exit completes in bounded time. **No deadlock possible.**

### 2.5 Scalability (1000+ Concurrent Cases)

#### Horizontal Scaling

- **Stateless tasks** (T_ProcessPayment, T_CallVendorAPI): Run on k8s pods, scale 1..N replicas via HPA
  - CPU: 200m per pod, memory: 512Mi
  - For 1000 concurrent cases: ~5 pods (each handling 200 cases)

- **Service dependencies**:
  - **Payment Service**: Stripe/Adyen API (external, rate-limited)
  - **Inventory Service**: Redis cluster (distributed lock backend)
  - **Database**: Cloud SQL with read replicas (shard by vendor_id or customer_id)
  - **Event Bus**: Cloud Pub/Sub (topic per workflow phase)

#### Concurrency Control

- **Payment processing**: Lock contention LOW (each customer has unique order_id)
- **Inventory management**: Lock contention MEDIUM (many customers buying same product)
  - Solution: FIFO queue for fairness, lock TTL 30s
- **Compliance review**: Lock contention LOW (each vendor uploads product infrequently)
  - Solution: Optimistic locking (versioned product), last-write-wins

#### Database

- **Shard key**: vendor_id (for onboarding phase), customer_id (for purchase phase)
- **Partitioning**: Auto-sharding by GCP Cloud SQL (transparent to application)
- **Indexes**: On (order_id, status), (product_sku, reserved_qty), (vendor_id, product_id)

---

## 3. Implementation Strategy

### 3.1 YAWL Specification

Create YAWL 4.0 specification file: `gcp-marketplace-workflow.yawl`

```xml
<specificationSet>
  <specification uri="gcp-marketplace-workflow">
    <metaData>
      <title>GCP Marketplace Workflow</title>
      <version>1.0.0</version>
    </metaData>

    <xs:schema>
      <!-- Data types for each phase -->
      <xs:complexType name="VendorProfileType">
        <xs:sequence>
          <xs:element name="company_name" type="xs:string"/>
          <xs:element name="tax_id" type="xs:string"/>
          <xs:element name="bank_account" type="xs:string"/>
          <xs:element name="identity_status" type="IdentityStatusType"/>
        </xs:sequence>
      </xs:complexType>

      <xs:complexType name="ProductListingType">
        <xs:sequence>
          <xs:element name="sku" type="xs:string"/>
          <xs:element name="title" type="xs:string"/>
          <xs:element name="pricing_model" type="PricingModelType"/>
          <xs:element name="compliance_status" type="ComplianceStatusType"/>
          <xs:element name="sla_tiers" type="SLATierListType"/>
        </xs:sequence>
      </xs:complexType>

      <xs:complexType name="OrderType">
        <xs:sequence>
          <xs:element name="order_id" type="xs:string"/>
          <xs:element name="customer_id" type="xs:string"/>
          <xs:element name="product_sku" type="xs:string"/>
          <xs:element name="quantity" type="xs:integer"/>
          <xs:element name="payment_id" type="xs:string"/>
          <xs:element name="fulfillment_status" type="FulfillmentStatusType"/>
        </xs:sequence>
      </xs:complexType>
    </xs:schema>

    <!-- Root net contains sub-nets for each phase -->
    <net id="gcp_marketplace_net">
      <decomposition xsi:type="NetFactsType">
        <!-- Vendor Onboarding Sub-net -->
        <subnet id="vendor_onboarding_net">
          <condition id="P_VendorInitiated"/>
          <condition id="P_IdentityVerification"/>
          <condition id="P_IdentityPassed"/>
          <condition id="P_IdentityFailed"/>
          <!-- ... more conditions and tasks ... -->
        </subnet>

        <!-- Product Listing Sub-net -->
        <subnet id="product_listing_net">
          <!-- parallel compliance reviews, marketing review, catalog integration -->
        </subnet>

        <!-- Purchase Sub-net -->
        <subnet id="purchase_net">
          <!-- checkout, payment with retry, inventory check, order confirmation -->
        </subnet>

        <!-- Fulfillment Sub-net -->
        <subnet id="fulfillment_net">
          <!-- parallel provisioning with timeout, compensation on failure -->
        </subnet>

        <!-- Support Sub-net -->
        <subnet id="support_net">
          <!-- monitoring, issue detection, escalation, termination -->
        </subnet>
      </decomposition>
    </net>
  </specification>
</specificationSet>
```

### 3.2 Task Implementations

**Service Tasks** (call external APIs):
- `T_VerifyIdentity`: Call KYC service (24h SLA, retry 2×)
- `T_VerifyBanking`: Call bank API (3 business days, retry with micro-deposit)
- `T_CallVendorAPI`: Call vendor provisioning API (5min SLA, retry 3×, fallback email)
- `T_ProcessPayment`: Call payment processor (pessimistic lock, retry 3×)

**User Tasks** (require human interaction):
- `T_SubmitProduct`: Vendor enters product details (form, no SLA)
- `T_MarketingReview`: Marketplace manager reviews product (3d SLA, rejection reason required)
- `T_CheckoutInitiated`: Customer reviews order, enters billing (30min before cart abandon)
- `T_InvestigateAndResolve`: Support team investigates issue (S1:1h, S2:4h, S3:24h)

**Automatic Tasks** (state transitions, no external call):
- `T_ApproveVendor`: AND-join identity + banking, emit VendorApprovedEvent
- `T_ConfirmOrder`: AND-join payment + inventory, create Order record
- `T_ProvisioningJoin`: AND-join (with timeout) all provisioning subtasks, or compensate
- `T_ComplianceJoin`: AND-join all 4 compliance checks, or notify vendor

### 3.3 Interface Integration

**Interface B (Client API)**:
- `launchCase(spec_id, vendor_id)`: Start vendor onboarding workflow
- `launchCase(spec_id, customer_id, product_sku)`: Start purchase workflow
- `checkOutWorkItem(work_item_id, agent_id)`: Agent (support team, automation) checks out task
- `completeWorkItem(work_item_id, output_data)`: Agent completes task with output

**Interface E (Event Notifications)**:
- `WorkItemEnabledEvent`: Notify services when task becomes available
- `WorkItemCompleteEvent`: Notify on task completion (e.g., payment done → inventory check)
- `CaseCompletionEvent`: Notify when entire workflow completes (fulfillment done → customer access)

**Interface X (Exception Handling)**:
- If task fails (timeout, external service error), route to exception handler
- Handler decides: retry, escalate to human, compensate (rollback)
- Example: `T_CallVendorAPI` fails 3× → escalate to support team via email

### 3.4 Data Storage

**Cloud SQL Schema** (PostgreSQL):

```sql
-- Vendors
CREATE TABLE vendors (
  vendor_id UUID PRIMARY KEY,
  company_name VARCHAR(256),
  identity_status ENUM('pending', 'passed', 'failed'),
  identity_verified_at TIMESTAMP,
  banking_verified_at TIMESTAMP,
  status ENUM('registered', 'approved', 'suspended', 'deactivated'),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- Products
CREATE TABLE products (
  product_id UUID PRIMARY KEY,
  vendor_id UUID REFERENCES vendors,
  sku VARCHAR(64) UNIQUE,
  title VARCHAR(256),
  pricing_model ENUM('per_user', 'per_month', 'usage_based'),
  compliance_status ENUM('pending', 'passed', 'failed'),
  compliance_checked_at TIMESTAMP,
  status ENUM('draft', 'review', 'live', 'inactive'),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX (vendor_id, status),
  INDEX (sku)
);

-- Orders
CREATE TABLE orders (
  order_id UUID PRIMARY KEY,
  customer_id UUID,
  product_sku VARCHAR(64) REFERENCES products,
  quantity INTEGER,
  unit_price DECIMAL(10, 2),
  total_price DECIMAL(10, 2),
  payment_id VARCHAR(256) UNIQUE,
  payment_status ENUM('pending', 'authorized', 'captured', 'declined', 'refunded'),
  fulfillment_status ENUM('pending', 'in_progress', 'completed', 'failed'),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX (customer_id, created_at),
  INDEX (payment_id),
  UNIQUE INDEX (order_id, payment_id)  -- Prevent double-charging
);

-- Inventory
CREATE TABLE inventory (
  sku VARCHAR(64) PRIMARY KEY REFERENCES products,
  total_quantity INTEGER,
  reserved_quantity INTEGER,
  available_quantity GENERATED ALWAYS AS (total_quantity - reserved_quantity),
  last_updated TIMESTAMP,
  CHECK (available_quantity >= 0)
);

-- Support Tickets
CREATE TABLE support_tickets (
  ticket_id UUID PRIMARY KEY,
  customer_id UUID,
  order_id UUID REFERENCES orders,
  severity ENUM('S1', 'S2', 'S3'),
  status ENUM('open', 'in_progress', 'resolved', 'closed'),
  created_at TIMESTAMP,
  assigned_to VARCHAR(256),
  sla_deadline TIMESTAMP,
  resolved_at TIMESTAMP,
  INDEX (customer_id, status),
  INDEX (sla_deadline)
);

-- Audit Log
CREATE TABLE audit_log (
  event_id UUID PRIMARY KEY,
  entity_type VARCHAR(64),
  entity_id UUID,
  action VARCHAR(64),
  actor_id VARCHAR(256),
  changes JSONB,
  created_at TIMESTAMP,
  INDEX (entity_id, created_at)
);
```

**Redis Distributed Locks**:

```
Key: inventory:lock:{sku}
Value: {holder_id}
TTL: 30 seconds
Operation: SET NX EX 30  (atomic compare-and-set)
```

**Event Stream (Pub/Sub)**:

- Topic: `marketplace-vendor-events` → VendorApprovedEvent, ProductLiveEvent, VendorDeactivatedEvent
- Topic: `marketplace-order-events` → OrderCreatedEvent, OrderConfirmedEvent, FulfillmentStartedEvent
- Topic: `marketplace-support-events` → TicketOpenedEvent, TicketEscalatedEvent, TicketResolvedEvent

---

## 4. Risk Analysis and Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Payment double-charge | Low | Critical | Pessimistic lock + idempotent token + 3-way matching (order_id, amount, timestamp) |
| Inventory oversell | Medium | High | Distributed lock (Redis) with FIFO fairness, inventory < 0 alerts |
| Vendor API timeout | High | Medium | Retry 3× with exponential backoff, fallback to email, timeout 5 min |
| Compliance review stalled | Low | Medium | Timeout 5 business days, escalate to human, option to resubmit |
| Support ticket lost | Low | Critical | Message queue (Pub/Sub) with 1-hour timeout escalation, manager fallback |
| Deadlock (circular wait) | Very Low | Critical | Proof of deadlock-free guarantee, lock ordering enforcement, timeout bounds |
| Data inconsistency | Medium | High | SERIALIZABLE isolation, audit log, reconciliation job (nightly) |
| Vendor API malicious (returns wrong account_id) | Low | Medium | Validate API response against schema, log all responses, SLA enforcement |

---

## 5. Alternatives Considered

### 5.1 **State Machine (vs. Petri Net)**
- **Pros**: Simpler to reason about, smaller state space
- **Cons**: No native support for parallelism (AND-split), harder to model cancellation regions
- **Rejected**: Marketplace needs parallel compliance reviews, provisioning tasks

### 5.2 **Saga Pattern (vs. Petri Net)**
- **Pros**: Distributed, event-driven, good for microservices
- **Cons**: Harder to model timeouts, timeout compensation is manual
- **Rejected**: Petri net provides formal verification (deadlock analysis), clearer semantics

### 5.3 **Direct Service Orchestration (vs. YAWL)**
- **Pros**: Freedom to implement anything, no overhead
- **Cons**: No standard state machine, hard to debug, no audit trail
- **Rejected**: Compliance (GDPR) requires full audit trail, non-repudiation

---

## 6. Success Criteria

| Criterion | Target | How to Measure |
|-----------|--------|-----------------|
| **Vendor onboarding completion rate** | 95% | (completed ÷ started) / month |
| **Time to go live** | < 8 business days | avg(completion_time) per vendor |
| **Purchase success rate** | 97% | (orders_placed ÷ checkout_attempts) / week |
| **Fulfillment success rate** | 99% | (fulfillment_completed ÷ orders_placed) / week |
| **Payment failure rate** | < 3% | (payment_declined ÷ payment_attempts) / week |
| **Inventory accuracy** | 100% | (oversells count = 0) or incident rate < 0.001% |
| **Support SLA compliance** | 98% | (resolved_on_time ÷ total_tickets) / month |
| **Deadlock incidents** | 0 | count per quarter |

---

## 7. References

- **YAWL 4.0 Specification**: http://www.yawlfoundation.org/support/documentation.html
- **Petri Net Semantics**: van der Aalst, "Business Process Management" (2nd edition)
- **Workflow Patterns**: http://www.workflowpatterns.com/
- **YAWL Architecture Patterns**: `/home/user/yawl/.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- **GCP Marketplace Documentation**: https://cloud.google.com/marketplace/docs
- **Related ADRs**:
  - ADR-014: Stateless Engine Architecture (case execution)
  - ADR-023: MCP/A2A CI/CD Integration
  - ADR-024: Multi-Cloud Agent Deployment

---

## 8. Approval and Next Steps

**Approvers**:
- [ ] Architecture Lead
- [ ] Engineering Lead
- [ ] Compliance Officer (audit trail verification)

**Next Steps**:
1. Implement YAWL specification (`gcp-marketplace-workflow.yawl`)
2. Create service implementations (KYC, payment, vendor API clients)
3. Write integration tests for each workflow phase
4. Deploy to staging environment, run 1-week load test (100 concurrent cases)
5. Measure SLA metrics, validate deadlock-free guarantee
6. Promote to production with blue-green deployment

---

**Document ID**: ADR-026
**Status**: ACCEPTED
**Last Updated**: 2026-02-21
