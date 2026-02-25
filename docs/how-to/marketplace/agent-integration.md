# GCP Marketplace Workflow: Agent Integration Guide

**Version**: 1.0
**Date**: 2026-02-21
**Audience**: Agent implementers, workflow engineers, integration specialists

This guide enables autonomous agents (Claude, specialized workflows, or microservices) to integrate with the GCP Marketplace workflow system via YAWL interfaces.

---

## 1. Overview

### 1.1 What is YAWL?

YAWL (Yet Another Workflow Language) is a formal workflow engine based on Petri net semantics. It provides:

- **Interface A (Admin)**: Load/unload workflow specifications
- **Interface B (Client)**: Launch cases, checkout/complete work items, query state
- **Interface E (Events)**: Listen to case state changes
- **Interface X (Exception)**: Handle failures, timeouts, escalations

### 1.2 GCP Marketplace Workflow Phases

```
Vendor Onboarding (3d)
    ↓
Product Listing (5d)
    ↓
Customer Purchase (5min)
    ↓
Fulfillment (1h)
    ↓
Post-Sales Support (ongoing)
    ↓
Deprovisioning (cleanup)
```

Each phase contains multiple work items (tasks). Agents execute tasks by:
1. **Discovering** available work items
2. **Checking out** a work item (lock + claim)
3. **Executing** (call external service, collect data, make decision)
4. **Completing** (return output, unlock, emit event)

---

## 2. Setting Up Agent Environment

### 2.1 Install YAWL Engine

**Option 1: Docker (Recommended)**
```bash
docker run -d \
  --name yawl-engine \
  -p 8080:8080 \
  -e DB_HOST=localhost \
  -e DB_PORT=5432 \
  -e DB_NAME=yawl \
  -e DB_USER=postgres \
  -e DB_PASSWORD=secret \
  ghcr.io/yawlfoundation/yawl-engine:v6.0.0
```

**Option 2: Local Build**
```bash
cd /home/user/yawl
mvn clean install -DskipTests
java -jar target/yawl-engine-6.0.0.jar
```

### 2.2 Load Marketplace Workflow Specification

Via Interface A (Administration):

```bash
curl -X POST http://localhost:8080/yawl/ia \
  -H "Content-Type: application/xml" \
  -d @gcp-marketplace-workflow.yawl \
  -u admin:admin
```

Response:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<session>
  <sessionHandle>ABC123</sessionHandle>
  <specificationID>
    <name>gcp-marketplace-workflow</name>
    <version>1.0.0</version>
    <uri>gcp-marketplace-workflow</uri>
  </specificationID>
</session>
```

### 2.3 Verify Engine Status

```bash
curl http://localhost:8080/yawl/ib/admin/health | jq .
```

Expected:
```json
{
  "status": "healthy",
  "engine": "YawlEngine v6.0.0",
  "database": "connected",
  "cases_running": 0,
  "work_items_pending": 0
}
```

---

## 3. Agent Implementation via Interface B

### 3.1 Java Agent Using YAWL SDK

```java
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClientObserver;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import java.util.Set;

public class MarketplaceAgent implements InterfaceBClientObserver {

    private InterfaceBClient engine;
    private String sessionHandle;
    private String agentId = "vendor-verification-agent-001";

    public MarketplaceAgent(String engineUrl) {
        this.engine = new InterfaceBClient(engineUrl);
    }

    /**
     * Connect to YAWL engine and establish session
     */
    public void connect() throws Exception {
        sessionHandle = engine.connect("agent_user", "agent_password");
        System.out.println("Connected to YAWL engine, session: " + sessionHandle);
    }

    /**
     * Discover available work items for this agent
     * (called every 30 seconds by discovery loop)
     */
    public void discoverWorkItems() throws Exception {
        // Query Interface B: get all enabled work items
        Set<YWorkItem> enabledItems = engine.getAvailableWorkItems(sessionHandle);

        for (YWorkItem item : enabledItems) {
            // Filter by task ID (agent specialization)
            if (item.getTaskID().equals("T_VerifyIdentity")) {
                processIdentityVerification(item);
            }
            else if (item.getTaskID().equals("T_ProcessPayment")) {
                processPayment(item);
            }
            // ... more task handlers
        }
    }

    /**
     * Example: Process vendor identity verification task
     *
     * Task: T_VerifyIdentity
     * Input: Vendor profile (company name, tax ID, documents)
     * Output: identity_status (PASS or FAIL) + rejection_reason (if FAIL)
     * SLA: 24 hours
     * Retries: 2 on timeout
     */
    private void processIdentityVerification(YWorkItem workItem) {
        try {
            String caseId = workItem.getCaseID().toString();
            String itemId = workItem.getID();

            // Step 1: Check out work item (lock)
            YWorkItem checkedOut = engine.checkOutWorkItem(
                itemId,
                agentId,
                sessionHandle
            );

            if (checkedOut == null) {
                System.out.println("Item already checked out by another agent");
                return;
            }

            // Step 2: Extract input data
            String vendorXml = checkedOut.getDataListString();
            VendorProfile vendor = parseVendorData(vendorXml);

            System.out.println("Processing identity verification for vendor: " + vendor.companyName);

            // Step 3: Call external service (KYC)
            KYCServiceClient kycService = new KYCServiceClient("https://kyc-api.example.com");
            KYCResult kycResult = kycService.verify(
                vendor.companyName,
                vendor.taxId,
                vendor.documents
            );

            // Step 4: Prepare output
            String outputData;
            if (kycResult.isPassed()) {
                outputData = """
                    <VendorVerification>
                        <identity_status>PASS</identity_status>
                        <verified_at>${now}</verified_at>
                    </VendorVerification>
                    """;
            } else {
                outputData = """
                    <VendorVerification>
                        <identity_status>FAIL</identity_status>
                        <rejection_reason>${kycResult.getReason()}</rejection_reason>
                        <verified_at>${now}</verified_at>
                    </VendorVerification>
                    """;
            }

            // Step 5: Complete work item
            String resultXml = engine.completeWorkItem(
                itemId,
                outputData,
                null,  // logData
                sessionHandle
            );

            System.out.println("Completed identity verification for case: " + caseId);
            System.out.println("Output: " + resultXml);

        } catch (Exception e) {
            System.err.println("Error processing identity verification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example: Process payment task
     *
     * Task: T_ProcessPayment
     * Input: Order details (customer ID, amount, payment method)
     * Output: payment_status (AUTHORIZED or DECLINED) + payment_id
     * SLA: 2 minutes
     * Retries: 3 with exponential backoff (1s, 5s, 25s)
     */
    private void processPayment(YWorkItem workItem) {
        String itemId = workItem.getID();
        String orderId = null;
        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount < maxRetries) {
            try {
                // Check out
                YWorkItem checkedOut = engine.checkOutWorkItem(
                    itemId,
                    agentId,
                    sessionHandle
                );

                // Extract input
                String orderXml = checkedOut.getDataListString();
                Order order = parseOrderData(orderXml);
                orderId = order.orderId;

                System.out.println("Processing payment for order: " + orderId);

                // Call payment processor
                PaymentProcessor paymentProcessor = new PaymentProcessor("https://stripe.com/api");
                PaymentResult paymentResult = paymentProcessor.authorize(
                    order.customerId,
                    order.amount,
                    order.paymentMethodId
                );

                // Prepare output
                String outputData;
                if (paymentResult.isApproved()) {
                    outputData = """
                        <PaymentResult>
                            <payment_status>AUTHORIZED</payment_status>
                            <payment_id>${paymentResult.getTransactionId()}</payment_id>
                            <authorization_code>${paymentResult.getAuthCode()}</authorization_code>
                            <processed_at>${now}</processed_at>
                        </PaymentResult>
                        """;
                } else {
                    outputData = """
                        <PaymentResult>
                            <payment_status>DECLINED</payment_status>
                            <decline_reason>${paymentResult.getReason()}</decline_reason>
                            <processed_at>${now}</processed_at>
                        </PaymentResult>
                        """;
                }

                // Complete work item
                engine.completeWorkItem(itemId, outputData, null, sessionHandle);
                System.out.println("Payment processed: " + paymentResult.getStatus());
                return;  // Success, exit retry loop

            } catch (TemporaryNetworkException e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    long backoffMs = (long) Math.pow(5, retryCount - 1) * 1000;  // 1s, 5s, 25s
                    System.out.println("Network error, retrying in " + backoffMs + "ms");
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ex) {}
                } else {
                    System.err.println("Payment processing failed after " + maxRetries + " retries");
                    throw e;
                }
            } catch (Exception e) {
                System.err.println("Error processing payment: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Run discovery loop (called by agent framework)
     */
    public void start() {
        new Thread(() -> {
            while (true) {
                try {
                    discoverWorkItems();
                    Thread.sleep(30000);  // 30 second discovery interval
                } catch (Exception e) {
                    System.err.println("Error in discovery loop: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Helper methods
    private VendorProfile parseVendorData(String xml) {
        // Parse XML to extract vendor profile
        // ... implementation ...
        return new VendorProfile();
    }

    private Order parseOrderData(String xml) {
        // Parse XML to extract order details
        // ... implementation ...
        return new Order();
    }
}
```

### 3.2 Python Agent Using httpx

```python
import httpx
import asyncio
import json
from datetime import datetime
from typing import Optional

class MarketplaceAgent:
    def __init__(self, engine_url: str, agent_id: str):
        self.engine_url = engine_url
        self.agent_id = agent_id
        self.session_handle = None
        self.client = httpx.AsyncClient()

    async def connect(self):
        """Establish session with YAWL engine via Interface B"""
        response = await self.client.post(
            f"{self.engine_url}/yawl/ib",
            json={
                "action": "connect",
                "user": "agent_user",
                "password": "agent_password"
            }
        )
        data = response.json()
        self.session_handle = data["sessionHandle"]
        print(f"Connected to YAWL engine: {self.session_handle}")

    async def discover_work_items(self):
        """Query available work items (Interface B)"""
        response = await self.client.get(
            f"{self.engine_url}/yawl/ib/workitems",
            params={"sessionHandle": self.session_handle}
        )
        items = response.json().get("workItems", [])
        return items

    async def checkout_work_item(self, item_id: str) -> Optional[dict]:
        """Lock and claim a work item"""
        response = await self.client.post(
            f"{self.engine_url}/yawl/ib/workitems/{item_id}/checkout",
            json={
                "agentId": self.agent_id,
                "sessionHandle": self.session_handle
            }
        )
        if response.status_code == 200:
            return response.json()
        elif response.status_code == 409:
            print(f"Work item already checked out by another agent: {item_id}")
            return None
        else:
            raise Exception(f"Checkout failed: {response.text}")

    async def complete_work_item(self, item_id: str, output_data: dict):
        """Unlock and complete a work item"""
        response = await self.client.post(
            f"{self.engine_url}/yawl/ib/workitems/{item_id}/complete",
            json={
                "outputData": output_data,
                "sessionHandle": self.session_handle
            }
        )
        if response.status_code != 200:
            raise Exception(f"Completion failed: {response.text}")
        return response.json()

    async def process_payment(self, work_item: dict):
        """Example: Process payment task (with retries)"""
        item_id = work_item["id"]
        max_retries = 3
        retry_count = 0

        while retry_count < max_retries:
            try:
                # Checkout
                checked_out = await self.checkout_work_item(item_id)
                if not checked_out:
                    return  # Already checked out by another agent

                # Extract order data
                order_data = checked_out["inputData"]
                order_id = order_data["orderId"]
                amount = float(order_data["amount"])

                print(f"Processing payment for order: {order_id}")

                # Call payment processor
                payment_result = await self.call_payment_api(
                    order_id=order_id,
                    amount=amount,
                    payment_method=order_data["paymentMethod"]
                )

                # Prepare output
                output = {
                    "payment_status": "AUTHORIZED" if payment_result["approved"] else "DECLINED",
                    "payment_id": payment_result.get("transaction_id"),
                    "processed_at": datetime.utcnow().isoformat()
                }

                # Complete work item
                await self.complete_work_item(item_id, output)
                print(f"Payment completed: {output['payment_status']}")
                return

            except asyncio.TimeoutError:
                retry_count += 1
                if retry_count < max_retries:
                    backoff_ms = (5 ** retry_count) * 1000
                    print(f"Timeout, retrying in {backoff_ms}ms")
                    await asyncio.sleep(backoff_ms / 1000)
                else:
                    print(f"Payment processing failed after {max_retries} retries")
                    raise

            except Exception as e:
                print(f"Error processing payment: {e}")
                raise

    async def call_payment_api(self, order_id: str, amount: float, payment_method: str):
        """Call external payment processor API"""
        response = await self.client.post(
            "https://stripe.com/api/charges",
            headers={"Authorization": "Bearer sk_test_..."},
            json={
                "amount": int(amount * 100),  # Convert to cents
                "currency": "usd",
                "source": payment_method,
                "description": f"Order {order_id}"
            }
        )
        data = response.json()
        return {
            "approved": data["status"] == "succeeded",
            "transaction_id": data.get("id"),
            "reason": data.get("failure_message")
        }

    async def start(self):
        """Start agent discovery loop"""
        await self.connect()

        while True:
            try:
                items = await self.discover_work_items()
                for item in items:
                    task_id = item["taskId"]

                    if task_id == "T_ProcessPayment":
                        await self.process_payment(item)
                    elif task_id == "T_VerifyIdentity":
                        await self.process_identity_verification(item)
                    # ... more task handlers

            except Exception as e:
                print(f"Error in discovery loop: {e}")

            await asyncio.sleep(30)  # 30 second discovery interval

# Usage
if __name__ == "__main__":
    agent = MarketplaceAgent(
        engine_url="http://localhost:8080",
        agent_id="payment-processor-agent-001"
    )
    asyncio.run(agent.start())
```

---

## 4. Event Listening via Interface E

Agents can subscribe to workflow events for monitoring or secondary processing:

```python
import asyncio
from typing import Callable

class EventListener:
    def __init__(self, engine_url: str):
        self.engine_url = engine_url
        self.event_callbacks = {}

    def register_callback(self, event_type: str, callback: Callable):
        """Register handler for event type"""
        self.event_callbacks[event_type] = callback

    async def start_listening(self):
        """Listen to Interface E events (WebSocket or SSE)"""
        async with httpx.AsyncClient() as client:
            # Start SSE listener
            async with client.stream(
                "GET",
                f"{self.engine_url}/yawl/ie/events",
                params={"stream": "true"}
            ) as response:
                async for line in response.aiter_lines():
                    if line:
                        event = json.loads(line)
                        await self.handle_event(event)

    async def handle_event(self, event: dict):
        """Process received event"""
        event_type = event["type"]  # e.g., "WorkItemEnabled", "CaseCompleted"

        if event_type == "WorkItemEnabled":
            # Triggered when a task becomes available
            print(f"Task available: {event['taskId']}")

        elif event_type == "WorkItemCompleted":
            # Triggered when a task is completed
            case_id = event["caseId"]
            print(f"Task completed in case: {case_id}")

        elif event_type == "CaseCompleted":
            # Triggered when entire workflow instance completes
            case_id = event["caseId"]
            print(f"Workflow completed: {case_id}")
            # Trigger post-processing: send completion email, log metrics, etc.

        # Call registered callback
        callback = self.event_callbacks.get(event_type)
        if callback:
            await callback(event)

# Usage
listener = EventListener("http://localhost:8080")
listener.register_callback("CaseCompleted", on_case_completed)
asyncio.run(listener.start_listening())

async def on_case_completed(event):
    print(f"Workflow {event['caseId']} completed!")
    # Trigger post-workflow actions
```

---

## 5. Exception Handling via Interface X

When a task fails (timeout, external service error), route to exception handler:

```java
import org.yawlfoundation.yawl.engine.interfce.interfaceX.*;

public class ExceptionHandler {

    /**
     * Handle failed work item (via Interface X)
     *
     * Decision options:
     * 1. RETRY: task is retried with same inputs
     * 2. REROUTE: task rerouted to different agent/worklet
     * 3. ESCALATE: escalate to human or manager
     * 4. WITHDRAW: task withdrawn, case suspended
     * 5. RESTART: task restarted from beginning
     */
    public void handleTaskFailure(WorkletInvocationException e) {
        String taskId = e.getTaskId();
        String caseId = e.getCaseId();
        String failureReason = e.getMessage();

        System.out.println("Task failed: " + taskId + " in case: " + caseId);
        System.out.println("Reason: " + failureReason);

        if (failureReason.contains("timeout")) {
            // Retry with exponential backoff
            handleTimeout(taskId, caseId);
        } else if (failureReason.contains("unauthorized")) {
            // Escalate to human
            escalateToHuman(taskId, caseId, "Authorization required");
        } else if (failureReason.contains("invalid_data")) {
            // Reroute to data validation specialist
            rerouteToWorklet(taskId, caseId, "DataValidationWorklet");
        } else {
            // Unknown failure, escalate
            escalateToHuman(taskId, caseId, failureReason);
        }
    }

    private void handleTimeout(String taskId, String caseId) {
        // Send retry decision to engine via Interface X
        // Engine will re-enable the task (move back to Enabled state)
        // Agent will discover and re-attempt
    }

    private void escalateToHuman(String taskId, String caseId, String reason) {
        // Create support ticket, notify human team
        // Human reviews context and decides: retry, skip, or cancel
    }

    private void rerouteToWorklet(String taskId, String caseId, String workletId) {
        // Route to specialized worklet (sub-workflow)
        // Worklet may implement alternative algorithm or human review
    }
}
```

---

## 6. Concurrency Control and Synchronization

### 6.1 Preventing Double-Charging

```python
async def process_payment_idempotent(engine_url: str, order_id: str, amount: float):
    """
    Ensure payment captured exactly once, even with retries/network hiccups.

    Strategy: Use order_id as idempotency key in payment processor.
    """
    # Call payment processor with idempotency key
    response = await httpx.post(
        "https://stripe.com/api/charges",
        json={
            "amount": int(amount * 100),
            "currency": "usd",
            "idempotency_key": order_id,  # ← Key: ensures idempotence
            "description": f"Order {order_id}"
        },
        headers={"Authorization": "Bearer sk_test_..."}
    )

    # Even if network error occurs and we retry,
    # Stripe will return same transaction ID (not create duplicate)
    return response.json()
```

### 6.2 Inventory Lock Pattern

```python
async def reserve_inventory(engine_url: str, product_sku: str, quantity: int):
    """
    Reserve inventory using distributed lock (Redis).

    Lock key: inventory:lock:{sku}
    Lock TTL: 30 seconds
    Fairness: FIFO queue
    """
    import redis_async

    redis_client = redis_async.from_url("redis://localhost:6379")
    lock_key = f"inventory:lock:{product_sku}"

    try:
        # Acquire lock atomically (SET NX EX 30)
        acquired = await redis_client.set(
            lock_key,
            json.dumps({"holder": "agent_001", "ts": time.time()}),
            nx=True,
            ex=30  # 30 second TTL
        )

        if not acquired:
            raise Exception(f"Inventory locked by another agent: {product_sku}")

        # Critical section: check and update inventory
        inventory = await redis_client.get(f"inventory:qty:{product_sku}")
        available = int(inventory or 0)

        if available < quantity:
            raise Exception(f"Insufficient inventory: need {quantity}, have {available}")

        # Reserve (decrement)
        await redis_client.decrby(f"inventory:qty:{product_sku}", quantity)

        return {"reserved": quantity, "remaining": available - quantity}

    finally:
        # Always release lock
        await redis_client.delete(lock_key)
```

---

## 7. Testing and Debugging

### 7.1 Unit Test: Payment Processing

```python
import pytest
from unittest.mock import AsyncMock, patch

@pytest.mark.asyncio
async def test_payment_success():
    """Test payment processing task completes successfully"""
    agent = MarketplaceAgent("http://localhost:8080", "payment-agent-001")

    with patch.object(agent, "call_payment_api", new_callable=AsyncMock) as mock_api:
        mock_api.return_value = {
            "approved": True,
            "transaction_id": "txn_12345"
        }

        work_item = {
            "id": "item_001",
            "taskId": "T_ProcessPayment",
            "inputData": {
                "orderId": "order_001",
                "amount": "99.99",
                "paymentMethod": "pm_card_visa"
            }
        }

        with patch.object(agent, "checkout_work_item", return_value=work_item):
            with patch.object(agent, "complete_work_item") as mock_complete:
                await agent.process_payment(work_item)

                # Assert work item was completed with correct output
                mock_complete.assert_called_once()
                call_args = mock_complete.call_args
                output = call_args[0][2]
                assert output["payment_status"] == "AUTHORIZED"
                assert output["payment_id"] == "txn_12345"

@pytest.mark.asyncio
async def test_payment_retry_on_timeout():
    """Test payment retry with exponential backoff"""
    agent = MarketplaceAgent("http://localhost:8080", "payment-agent-001")

    call_count = 0

    async def mock_api_with_timeout(*args, **kwargs):
        nonlocal call_count
        call_count += 1
        if call_count < 3:
            raise asyncio.TimeoutError("Stripe API timeout")
        return {"approved": True, "transaction_id": "txn_12345"}

    with patch.object(agent, "call_payment_api", side_effect=mock_api_with_timeout):
        work_item = {
            "id": "item_001",
            "taskId": "T_ProcessPayment",
            "inputData": {
                "orderId": "order_001",
                "amount": "99.99",
                "paymentMethod": "pm_card_visa"
            }
        }

        with patch.object(agent, "checkout_work_item", return_value=work_item):
            with patch.object(agent, "complete_work_item"):
                await agent.process_payment(work_item)

                # Assert retry happened (2 failures, then success on 3rd attempt)
                assert call_count == 3
```

### 7.2 Integration Test: End-to-End Purchase

```python
@pytest.mark.integration
async def test_customer_purchase_flow():
    """Test complete purchase workflow from cart to fulfillment"""
    engine = YawlEngineClient("http://localhost:8080")

    # Load marketplace specification
    spec_id = await engine.load_specification("gcp-marketplace-workflow.yawl")

    # Launch case: customer purchase
    case_id = await engine.launch_case(
        spec_id=spec_id,
        case_data={
            "customerId": "cust_001",
            "productSku": "prod-standard-001",
            "quantity": 1,
            "paymentMethod": "pm_card_visa"
        }
    )

    # Agent 1: Process payment
    payment_agent = MarketplaceAgent("http://localhost:8080", "payment-agent-001")
    items = await payment_agent.discover_work_items()
    assert len(items) > 0
    payment_item = [i for i in items if i["taskId"] == "T_ProcessPayment"][0]
    await payment_agent.process_payment(payment_item)

    # Agent 2: Check inventory
    inventory_agent = MarketplaceAgent("http://localhost:8080", "inventory-agent-001")
    items = await inventory_agent.discover_work_items()
    inventory_item = [i for i in items if i["taskId"] == "T_CheckInventory"][0]
    # ... process inventory ...

    # Agent 3: Confirm order
    order_agent = MarketplaceAgent("http://localhost:8080", "order-agent-001")
    items = await order_agent.discover_work_items()
    order_item = [i for i in items if i["taskId"] == "T_ConfirmOrder"][0]
    # ... confirm order ...

    # Agent 4: Provision product
    fulfillment_agent = MarketplaceAgent("http://localhost:8080", "fulfillment-agent-001")
    # ... process fulfillment ...

    # Verify workflow completed
    case_status = await engine.get_case_status(case_id)
    assert case_status["status"] == "COMPLETED"
```

---

## 8. Performance and Monitoring

### 8.1 Key Metrics to Track

```python
class MetricsCollector:
    def __init__(self):
        self.metrics = {}

    def record_task_completion(self, task_id: str, duration_ms: float, success: bool):
        """Record task execution metrics"""
        key = f"{task_id}:completion_time"
        self.metrics[key] = {
            "duration_ms": duration_ms,
            "success": success,
            "timestamp": datetime.utcnow().isoformat()
        }

    def record_sla_metric(self, task_id: str, sla_seconds: float, actual_seconds: float):
        """Record SLA compliance"""
        compliant = actual_seconds <= sla_seconds
        print(f"Task {task_id}: SLA {sla_seconds}s, actual {actual_seconds}s - {'PASS' if compliant else 'MISS'}")

    def record_inventory_accuracy(self, oversells: int):
        """Alert on inventory violations"""
        if oversells > 0:
            print(f"ALERT: Inventory oversold by {oversells} units")

    def record_deadlock_detection(self, case_id: str):
        """Alert on deadlock"""
        print(f"ALERT: Deadlock detected in case {case_id}")
```

### 8.2 Health Check Endpoint

```python
@app.get("/health")
async def health_check():
    """Agent health check endpoint"""
    return {
        "status": "healthy",
        "agent_id": "payment-agent-001",
        "engine_url": "http://localhost:8080",
        "connected": True,
        "tasks_processed": 1234,
        "avg_task_duration_ms": 450,
        "last_error": None,
        "timestamp": datetime.utcnow().isoformat()
    }
```

---

## 9. Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Work item not discovered | Task not enabled yet | Check case state, ensure all prerequisites completed |
| Checkout returns 409 | Item already checked out | Wait and retry, check for agent failures |
| Payment double-charged | Idempotency key not used | Ensure idempotency_key set to order_id in payment API call |
| Inventory becomes negative | Race condition in lock | Verify lock acquisition uses SET NX EX atomically |
| Task times out | External service slow | Increase timeout SLA, implement fallback, escalate |
| Case stuck in Enabled | No agent discovering task | Check agent logs, verify agent connected, restart agent |
| Deadlock suspected | Circular dependencies | Run `deadlock_analyzer.sh` on workflow, check lock ordering |

---

## 10. References

- **YAWL Documentation**: http://www.yawlfoundation.org/
- **Interface B API**: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceBClient.java`
- **Marketplace Workflow Spec**: `/home/user/yawl/docs/architecture/gcp-marketplace-workflow.yaml`
- **Architecture Decision Record**: `/home/user/yawl/docs/architecture/decisions/ADR-026-gcp-marketplace-petri-net.md`

---

**Last Updated**: 2026-02-21
**Version**: 1.0
**Status**: Production Ready
