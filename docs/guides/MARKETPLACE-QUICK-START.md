# GCP Marketplace Workflow: Quick Start (5-Minute Reference)

**Version**: 1.0 | **Date**: 2026-02-21

---

## What You're Building

A scalable, deadlock-free workflow for managing GCP Marketplace transactions with 5 phases:

```
Vendor Onboarding (3d) → Product Listing (5d) → Purchase (5min) → Fulfillment (1h) → Support (∞)
```

---

## Key Files

| File | Purpose | Audience |
|------|---------|----------|
| `gcp-marketplace-workflow.yaml` | Complete Petri net specification (2000+ lines) | Architects, workflow engineers |
| `ADR-026-gcp-marketplace-petri-net.md` | Design decisions, deadlock proof, alternatives | Decision makers, reviewers |
| `MARKETPLACE-AGENT-INTEGRATION.md` | How to implement agents (Java, Python examples) | Agent developers |
| `MARKETPLACE-QUICK-START.md` | This file - 5-min cheat sheet | Everyone |

---

## Core Concepts

### Petri Net

- **Conditions (P_*)**: States where tokens live (e.g., `P_PaymentAuthorized`)
- **Tasks (T_*)**: Work items that transition tokens (e.g., `T_ProcessPayment`)
- **Tokens**: Case instances (one token = one vendor/purchase/support ticket)

### Control Patterns

| Pattern | What | Example |
|---------|------|---------|
| **Sequence** | Do A, then B | Register → Verify identity → Setup banking |
| **AND-split** | Do A, B, C in parallel | Run 4 compliance checks at once |
| **AND-join** | Wait for A AND B both done | Wait for payment + inventory before confirming order |
| **XOR-split** | Choose A or B | If identity passes → continue; if fails → reject |
| **Timeout** | If task > SLA, escalate | If payment > 2 min, retry 3× then offer manual |

---

## Implementation Checklist

```
[ ] 1. Load YAWL engine (Docker or local)
      docker run -d -p 8080:8080 ghcr.io/yawlfoundation/yawl-engine:v6.0.0

[ ] 2. Load marketplace spec via Interface A
      curl -X POST http://localhost:8080/yawl/ia \
        -d @gcp-marketplace-workflow.yawl

[ ] 3. Implement agents (Java, Python, or microservices)
      - Discover available work items (Interface B)
      - Check out task (lock + claim)
      - Execute (call external service)
      - Complete task (unlock + return output)

[ ] 4. Set up synchronization
      - Payment processing: pessimistic lock (Redis)
      - Inventory reservation: distributed lock (Redis, FIFO)
      - Approval gates: AND-join with timeout

[ ] 5. Test & monitor
      - Unit tests: payment idempotence, inventory accuracy
      - Integration tests: end-to-end purchase flow
      - Metrics: SLA compliance, deadlock alerts, oversell detection

[ ] 6. Deploy to production
      - GCP Cloud Run (stateless agents)
      - Cloud SQL (PostgreSQL) for persistence
      - Cloud Pub/Sub for event notifications
      - Cloud Monitoring for SLA tracking
```

---

## Deadlock-Free Guarantee (TL;DR)

**Why no deadlock?**

1. **Lock graph is acyclic**: All locks acquired in total order (customer_id < product_id < payment_id)
2. **Timeouts everywhere**: No infinite waits (max 14 days)
3. **AND-join reachability**: Both inputs of a join are reachable
4. **Dependencies form DAG**: No circular task dependencies

**Proof**: Acyclic + bounded timeouts + reachable joins = no deadlock. ✓

---

## Concurrency Control Summary

| What | Problem | Solution |
|------|---------|----------|
| **Payment** | Double-charging | Pessimistic lock (order_id), idempotent key |
| **Inventory** | Overselling | Distributed lock (Redis), FIFO fairness |
| **Compliance** | Race condition | Optimistic locking (versioned product) |
| **Vendor approval** | Missing prerequisite | AND-join (identity + banking), timeout 14d |

---

## Workflow State Diagram (ASCII)

```
Start
  │
  ├─→ Vendor Onboarding
  │     ├─ Verify Identity (24h)
  │     ├─ Setup Banking (7d)
  │     └─ Approve Vendor (AND-join)
  │
  ├─→ Product Listing
  │     ├─ PARALLEL compliance:
  │     │   ├─ GDPR review
  │     │   ├─ Export control
  │     │   ├─ IP review
  │     │   └─ Data residency
  │     ├─ Marketing review (3d)
  │     └─ Publish to catalog
  │
  ├─→ Customer Purchase
  │     ├─ Checkout (30min)
  │     ├─ Process payment (retry 3×, 2min SLA)
  │     ├─ Check inventory (distributed lock)
  │     ├─ Confirm order (AND-join)
  │
  ├─→ Fulfillment
  │     ├─ PARALLEL provisioning:
  │     │   ├─ Call vendor API (5min, retry 3×)
  │     │   ├─ Setup billing
  │     │   └─ Setup monitoring
  │     ├─ Create credentials
  │     └─ Grant customer access
  │
  ├─→ Support (loop)
  │     ├─ Monitor (automated alerts)
  │     ├─ Issue detected → open ticket
  │     ├─ Support investigates (S1:1h, S2:4h, S3:24h)
  │     └─ Resolve
  │
  └─→ Deprovisioning
       ├─ Revoke access
       ├─ Delete resources
       └─ Close billing
            │
            └─→ End
```

---

## Agent Skeleton (Java)

```java
public class MyAgent implements InterfaceBClientObserver {
    InterfaceBClient engine = new InterfaceBClient("http://localhost:8080");
    String sessionHandle = engine.connect("user", "pass");

    void discover() {
        Set<YWorkItem> items = engine.getAvailableWorkItems(sessionHandle);
        for (YWorkItem item : items) {
            if (item.getTaskID().equals("T_ProcessPayment")) {
                YWorkItem checked = engine.checkOutWorkItem(item.getID(), agentId, sessionHandle);
                String input = checked.getDataListString();

                // Call external service
                PaymentResult result = paymentProcessor.authorize(input);

                // Return output
                String output = "<PaymentResult><status>" + result.status + "</status></PaymentResult>";
                engine.completeWorkItem(item.getID(), output, null, sessionHandle);
            }
        }
    }
}
```

---

## Testing Checklist

```
Unit Tests:
  [ ] Payment idempotence (retry should not double-charge)
  [ ] Inventory accuracy (no overselling)
  [ ] Lock fairness (FIFO queue)

Integration Tests:
  [ ] End-to-end vendor onboarding (8d max)
  [ ] End-to-end purchase (5min + fulfillment 1h)
  [ ] Concurrent purchases with inventory limit
  [ ] Support ticket routing

Chaos Tests:
  [ ] Payment processor timeout → retry + fallback
  [ ] Vendor API unavailable → email fallback
  [ ] Database connection pool exhausted → queue, no loss
  [ ] Deadlock simulation → none found ✓
```

---

## Monitoring Alerts

```
Critical:
  [ ] Deadlock detected → page on-call
  [ ] Inventory becomes negative → block sales immediately
  [ ] Payment processor down → switch backup + notify customers
  [ ] Support SLA missed → escalate to manager

Warning:
  [ ] Compliance review > 5 business days → nag team
  [ ] Fulfillment > 2 hours → check vendor API status
  [ ] Support queue > 100 → activate overflow team
  [ ] Lock contention > 30% → add Redis nodes

Info:
  [ ] Payment success rate < 95% → investigate
  [ ] Task duration > 2× median → log slow tasks
  [ ] Agent unhealthy (no heartbeat) → restart
```

---

## SLA Summary

| Phase | SLA | Enforced By |
|-------|-----|-------------|
| Identity verification | 24 hours | T_VerifyIdentity timeout |
| Banking setup | 7 days | T_SetupBanking timeout |
| Vendor approval | 14 days | T_ApproveVendor (AND-join) timeout |
| Compliance review | 5 business days | T_ComplianceJoin timeout |
| Marketing review | 3 business days | T_MarketingReview timeout |
| Customer checkout | 30 minutes | T_Checkout timeout (cart abandon) |
| Payment authorization | 2 minutes | T_ProcessPayment (retry 3×) |
| Inventory check | 30 seconds | Distributed lock TTL |
| Fulfillment | 1 hour (std), 15 min (premium) | T_ProvisioningJoin timeout |
| Support S1 | 1 hour | T_InvestigateAndResolve SLA |
| Support S2 | 4 hours | T_InvestigateAndResolve SLA |
| Support S3 | 24 hours | T_InvestigateAndResolve SLA |

---

## Failure Scenarios and Recovery

| Scenario | Detection | Action |
|----------|-----------|--------|
| **Payment declined** | T_ProcessPayment fails | Retry 3× (1s, 5s, 25s backoff) → offer alt payment |
| **Inventory oversold** | inventory < 0 alert fires | Block sales, refund last buyers, post-mortem |
| **Vendor API timeout** | T_CallVendorAPI > 5min | Retry 3×, email vendor, refund if 3× fail |
| **Fulfillment partial fail** | Some of {vendor, billing, monitor} succeed | Compensate all, refund, notify customer |
| **Support ticket lost** | Unhandled > 1 hour | Escalate to fallback queue, page manager |
| **Deadlock suspected** | Task stuck > 2× SLA | Run deadlock analyzer, break tie, restart |

---

## Database Schema (Essential Tables)

```sql
vendors(vendor_id, status, identity_status, banking_status, created_at)
products(product_id, vendor_id, sku, status, compliance_status)
orders(order_id, customer_id, product_sku, payment_id, fulfillment_status)
inventory(sku, total_qty, reserved_qty, available_qty)
support_tickets(ticket_id, order_id, severity, status, sla_deadline)
audit_log(event_id, entity_id, action, actor_id, created_at)

Indexes:
  orders(customer_id, created_at)
  orders(payment_id)
  products(vendor_id, status)
  support_tickets(sla_deadline)
```

---

## Common Mistakes to Avoid

```
❌ Not using idempotent keys in payment API
✓ Always set idempotency_key = order_id

❌ Acquiring multiple locks (nested)
✓ Acquire 1, use, release before next lock

❌ No timeout on distributed lock
✓ Set TTL = 30 seconds (auto-release if holder crashes)

❌ Waiting forever in AND-join
✓ Always set timeout (e.g., 1 hour for fulfillment)

❌ No retry logic on network errors
✓ Retry 3× with exponential backoff (1s, 5s, 25s)

❌ Silent failures
✓ Log every decision, escalate on error

❌ Manual verification of deadlock
✓ Prove deadlock-free: acyclic locks + timeouts + reachable joins
```

---

## Deploy to GCP (High Level)

```bash
# 1. Build Docker image
docker build -t gcr.io/my-project/marketplace-agents:latest .

# 2. Push to Artifact Registry
docker push gcr.io/my-project/marketplace-agents:latest

# 3. Deploy YAWL engine to Cloud Run
gcloud run deploy yawl-engine \
  --image gcr.io/my-project/yawl-engine:latest \
  --platform managed \
  --region us-central1 \
  --memory 2Gi \
  --set-env-vars DB_HOST=10.0.0.5,DB_NAME=yawl

# 4. Deploy agents to Cloud Run (one pod per agent type)
gcloud run deploy payment-agent \
  --image gcr.io/my-project/marketplace-agents:latest \
  --platform managed \
  --region us-central1 \
  --args payment-agent-001

# 5. Monitor
gcloud logging read "resource.type=cloud_run_revision" --limit 50
gcloud monitoring dashboards create --config-from-file=dashboard.json
```

---

## Need Help?

- **Architecture questions**: See `ADR-026-gcp-marketplace-petri-net.md` (full decisions + proof)
- **Agent implementation**: See `MARKETPLACE-AGENT-INTEGRATION.md` (Java + Python examples)
- **Workflow spec**: See `gcp-marketplace-workflow.yaml` (2000+ lines of detail)
- **YAWL docs**: http://www.yawlfoundation.org/

---

**Last Updated**: 2026-02-21
**Status**: Production Ready
**Maintained By**: YAWL Architecture Team
