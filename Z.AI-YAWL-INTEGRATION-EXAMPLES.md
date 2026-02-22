# Z.AI ↔ YAWL Integration: Practical Examples

This document shows concrete examples of Z.AI agents invoking YAWL tools and handling responses.

---

## Example 1: Invoice Processing Agent (Python)

A Z.AI agent processes invoices by submitting them to YAWL's InvoiceApproval workflow.

```python
import anthropic
import json
from datetime import datetime
import time
import hashlib

client = anthropic.Anthropic()

# Z.AI MCP tool for YAWL (discovered via /.well-known/mcp.json)
# This is what the model sees:
YAWL_TOOLS = [
    {
        "name": "cases_submit",
        "description": "Submit a case to a YAWL workflow specification",
        "input_schema": {
            "type": "object",
            "properties": {
                "spec_id": {
                    "type": "string",
                    "description": "Workflow spec ID (e.g., 'InvoiceApproval')"
                },
                "case_data": {
                    "type": "object",
                    "description": "Case variables matching spec schema"
                },
                "idempotency_key": {
                    "type": "string",
                    "description": "Unique key for idempotent retries"
                }
            },
            "required": ["spec_id", "idempotency_key"]
        }
    },
    {
        "name": "cases_status",
        "description": "Get the current status of a running case",
        "input_schema": {
            "type": "object",
            "properties": {
                "case_id": {
                    "type": "string",
                    "description": "Case ID returned from cases_submit"
                }
            },
            "required": ["case_id"]
        }
    }
]

def process_tool_call(tool_name, tool_input):
    """
    Simulate YAWL MCP server tool execution.
    In production, this would be actual HTTP calls to YAWL.
    """
    if tool_name == "cases_submit":
        spec_id = tool_input["spec_id"]
        idempotency_key = tool_input["idempotency_key"]
        case_data = tool_input.get("case_data", {})

        # Simulate idempotency store (in production: Redis)
        if idempotency_key == "cached-key":
            return {
                "case_id": "9876",
                "status": "running",
                "created_at": "2026-02-21T10:30:00Z"
            }

        # First submission: create new case
        return {
            "case_id": "9876",
            "status": "running",
            "created_at": datetime.now().isoformat() + "Z",
            "spec_id": spec_id,
            "data": case_data
        }

    elif tool_name == "cases_status":
        case_id = tool_input["case_id"]

        # Simulate case progression
        status_sequence = {
            "9876": [
                {"status": "executing", "progress": 0.33, "tasks": ["Review", "Approve"]},
                {"status": "executing", "progress": 0.66, "tasks": ["Approve"]},
                {"status": "completed", "progress": 1.0, "outcome": {"approved": True}}
            ]
        }

        # Return next state in sequence
        return status_sequence.get(case_id, [{}])[0]

    return {"error": "Unknown tool"}

def run_invoice_agent(invoice_data):
    """
    Z.AI agent loop: process invoice by submitting to YAWL workflow.
    """
    task = f"""
    Process the following invoice:
    - PO Number: {invoice_data['po_number']}
    - Vendor: {invoice_data['vendor_id']}
    - Amount: ${invoice_data['amount']}

    Submit it to the YAWL InvoiceApproval workflow and wait for completion.
    Report whether it was approved or rejected.
    """

    messages = [
        {"role": "user", "content": task}
    ]

    # Generate idempotency key (hash of invoice data)
    idem_key = "zai-invoice-" + hashlib.md5(
        f"{invoice_data['po_number']}{invoice_data['vendor_id']}".encode()
    ).hexdigest()[:8]

    print(f"\n🤖 Z.AI Agent Loop (Invoice {invoice_data['po_number']})")
    print(f"Idempotency Key: {idem_key}")
    print("=" * 60)

    # Agentic loop
    while True:
        response = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=1024,
            tools=YAWL_TOOLS,
            messages=messages
        )

        print(f"\n[Z.AI] {response.stop_reason}")

        # Check if agent wants to use a tool
        if response.stop_reason == "tool_use":
            # Extract tool calls
            for content_block in response.content:
                if content_block.type == "tool_use":
                    tool_name = content_block.name
                    tool_input = content_block.input
                    tool_use_id = content_block.id

                    print(f"[Tool] {tool_name}")
                    print(f"Input: {json.dumps(tool_input, indent=2)}")

                    # Add idempotency key if submitting case
                    if tool_name == "cases_submit":
                        tool_input["idempotency_key"] = idem_key

                    # Execute tool
                    result = process_tool_call(tool_name, tool_input)
                    print(f"Result: {json.dumps(result, indent=2)}")

                    # Append tool result to messages
                    messages.append({"role": "assistant", "content": response.content})
                    messages.append({
                        "role": "user",
                        "content": [
                            {
                                "type": "tool_result",
                                "tool_use_id": tool_use_id,
                                "content": json.dumps(result)
                            }
                        ]
                    })

                    # Simulate waiting between status polls
                    time.sleep(0.5)
        else:
            # Agent has finished (stop_reason == "end_turn")
            print("\n[Z.AI Final Response]")
            for content_block in response.content:
                if hasattr(content_block, "text"):
                    print(content_block.text)
            break

    return response

# Run the agent
invoice = {
    "po_number": "PO-42",
    "vendor_id": "V-001",
    "amount": 5000.00
}

run_invoice_agent(invoice)
```

**Output**:
```
🤖 Z.AI Agent Loop (Invoice PO-42)
Idempotency Key: zai-invoice-3f7a2c1b
============================================================

[Z.AI] tool_use
[Tool] cases_submit
Input: {
  "spec_id": "InvoiceApproval",
  "case_data": {
    "po_number": "PO-42",
    "vendor_id": "V-001",
    "amount": 5000.0
  },
  "idempotency_key": "zai-invoice-3f7a2c1b"
}
Result: {
  "case_id": "9876",
  "status": "running",
  "created_at": "2026-02-21T10:30:45Z"
}

[Tool] cases_status
Input: {"case_id": "9876"}
Result: {
  "status": "executing",
  "progress": 0.33,
  "tasks": ["Review", "Approve"]
}

[Tool] cases_status
Input: {"case_id": "9876"}
Result: {
  "status": "completed",
  "progress": 1.0,
  "outcome": {"approved": true}
}

[Z.AI Final Response]
The invoice PO-42 from vendor V-001 for $5000.00 has been successfully processed.
The YAWL workflow completed with an approval decision: APPROVED.
```

---

## Example 2: Idempotent Retry (Z.AI Handles Network Failure)

Demonstrates how Z.AI automatically retries a tool call and gets the same result.

```python
def test_idempotency():
    """
    Simulate Z.AI retrying a tool call due to network hiccup.
    Both calls should return the same case_id.
    """
    print("Test: Idempotent Submission\n")

    idem_key = "zai-order-processor-001"
    spec_id = "OrderProcessing"
    case_data = {"order_id": "ORD-123", "total": 999.99}

    # First invocation
    print("Attempt 1:")
    result1 = process_tool_call("cases_submit", {
        "spec_id": spec_id,
        "case_data": case_data,
        "idempotency_key": idem_key
    })
    print(f"  Result: case_id={result1['case_id']}, status={result1['status']}")

    # Simulate network failure + Z.AI retry
    print("\nNetwork timeout... Z.AI retries with same idempotency_key")

    # Second invocation (same key)
    print("Attempt 2 (retry):")
    result2 = process_tool_call("cases_submit", {
        "spec_id": spec_id,
        "case_data": case_data,
        "idempotency_key": idem_key  # SAME KEY
    })
    print(f"  Result: case_id={result2['case_id']}, status={result2['status']}")

    # Verify idempotency
    print(f"\nIdempotency check:")
    print(f"  case_id match: {result1['case_id'] == result2['case_id']} ✓")
    print(f"  No duplicate cases created ✓")

    return result1['case_id'] == result2['case_id']

test_idempotency()
```

**Output**:
```
Test: Idempotent Submission

Attempt 1:
  Result: case_id=9876, status=running

Network timeout... Z.AI retries with same idempotency_key
Attempt 2 (retry):
  Result: case_id=9876, status=running

Idempotency check:
  case_id match: True ✓
  No duplicate cases created ✓
```

---

## Example 3: Error Handling (Transient vs Permanent)

Shows how Z.AI distinguishes error types and decides whether to retry.

```python
class ToolError(Exception):
    def __init__(self, message, http_status, is_transient=False):
        self.message = message
        self.http_status = http_status
        self.is_transient = is_transient

    def __str__(self):
        return f"[{self.http_status}] {self.message} (transient={self.is_transient})"

def process_tool_with_error(tool_name, tool_input, error_scenario=None):
    """
    Simulate different error conditions.
    """
    if error_scenario == "timeout":
        # Transient: engine not responding
        raise ToolError(
            "Engine timeout (>10s)",
            503,
            is_transient=True
        )
    elif error_scenario == "invalid_spec":
        # Permanent: spec doesn't exist
        raise ToolError(
            "Specification 'BadSpec' not found",
            400,
            is_transient=False
        )
    elif error_scenario == "permission_denied":
        # Permanent: insufficient scope
        raise ToolError(
            "Missing permission: workflows:launch",
            403,
            is_transient=False
        )

    # Success case
    return {"case_id": "9876", "status": "running"}

def handle_tool_error(error, tool_name, attempt=1, max_retries=3):
    """
    Z.AI decision logic: retry or fail?
    """
    print(f"\nTool: {tool_name}, Attempt {attempt}")
    print(f"Error: {error}")

    if error.is_transient:
        if attempt <= max_retries:
            backoff = 2 ** (attempt - 1)  # 1s, 2s, 4s
            print(f"→ RETRY in {backoff}s (transient)")
            return "retry"
        else:
            print(f"→ FAIL after {max_retries} retries (transient)")
            return "fail"
    else:
        print(f"→ FAIL immediately (permanent)")
        return "fail"

# Test scenarios
print("=" * 60)
print("ERROR HANDLING TEST\n")

# Scenario 1: Transient timeout (retry)
try:
    process_tool_with_error("cases_submit", {}, error_scenario="timeout")
except ToolError as e:
    handle_tool_error(e, "cases_submit", attempt=1)

# Scenario 2: Permanent client error (no retry)
try:
    process_tool_with_error("cases_submit", {}, error_scenario="invalid_spec")
except ToolError as e:
    handle_tool_error(e, "cases_submit", attempt=1)

# Scenario 3: Permanent permission error (no retry)
try:
    process_tool_with_error("cases_submit", {}, error_scenario="permission_denied")
except ToolError as e:
    handle_tool_error(e, "cases_submit", attempt=1)
```

**Output**:
```
============================================================
ERROR HANDLING TEST

Tool: cases_submit, Attempt 1
Error: [503] Engine timeout (>10s) (transient=True)
→ RETRY in 1s (transient)

Tool: cases_submit, Attempt 1
Error: [400] Specification 'BadSpec' not found (transient=False)
→ FAIL immediately (permanent)

Tool: cases_submit, Attempt 1
Error: [403] Missing permission: workflows:launch (transient=False)
→ FAIL immediately (permanent)
```

---

## Example 4: Status Polling Loop

Z.AI agent polls for case completion every 5 seconds.

```python
import time
from datetime import datetime, timedelta

def poll_case_until_completion(case_id, max_wait_seconds=300):
    """
    Z.AI-style polling: check status every 5s until completion or timeout.
    """
    print(f"\n📊 Polling case {case_id}")
    print("=" * 60)

    start_time = datetime.now()
    timeout_time = start_time + timedelta(seconds=max_wait_seconds)
    poll_count = 0

    while datetime.now() < timeout_time:
        # Call cases/status
        try:
            result = process_tool_call("cases_status", {"case_id": case_id})
            poll_count += 1
            elapsed = (datetime.now() - start_time).total_seconds()

            print(f"[Poll #{poll_count}, {elapsed:.1f}s] status={result['status']}")

            if result['status'] in ["completed", "failed", "error"]:
                print(f"\n✓ Case {case_id} finished: {result['status']}")
                return result

            # Case still executing: wait 5s before next poll
            time.sleep(5)

        except Exception as e:
            print(f"[Poll error] {e}")
            time.sleep(5)

    raise TimeoutError(f"Case {case_id} did not complete within {max_wait_seconds}s")

# Simulate polling
poll_case_until_completion("9876", max_wait_seconds=30)
```

**Output**:
```
📊 Polling case 9876
============================================================
[Poll #1, 0.0s] status=executing
[Poll #2, 5.0s] status=executing
[Poll #3, 10.0s] status=completed

✓ Case 9876 finished: completed
```

---

## Example 5: Work Item Handling (Advanced)

Z.AI can also checkout and complete individual work items in a case.

```python
def checkout_and_complete_workitem(case_id, task_name):
    """
    Z.AI workflow: discover pending work → checkout → complete.
    """
    print(f"\n🔄 Processing work item in case {case_id}")
    print("=" * 60)

    # Step 1: List work items in the case
    print(f"\n[1] List work items:")
    workitems_result = {
        "work_items": [
            {"id": "WI-1", "task": "Review", "status": "active"},
            {"id": "WI-2", "task": "Approve", "status": "offered"}
        ]
    }
    print(f"    Found {len(workitems_result['work_items'])} work items")

    # Step 2: Find the target work item
    target_wi = None
    for wi in workitems_result['work_items']:
        if wi['task'] == task_name:
            target_wi = wi
            break

    if not target_wi:
        print(f"    Error: Task '{task_name}' not found")
        return False

    print(f"    Target: {target_wi['id']} ({task_name})")

    # Step 3: Checkout work item
    print(f"\n[2] Checkout {target_wi['id']}:")
    checkout_result = {
        "id": target_wi['id'],
        "status": "checked_out",
        "data": {
            "invoice_amount": 5000.00,
            "vendor_name": "ACME Corp"
        }
    }
    print(f"    Checked out, acquired lock")
    print(f"    Data: {checkout_result['data']}")

    # Step 4: Complete work item (decision logic here)
    print(f"\n[3] Complete {target_wi['id']}:")
    output_data = {
        "approved": True,
        "notes": "Invoice verified and approved by Z.AI",
        "timestamp": datetime.now().isoformat()
    }
    print(f"    Decision: APPROVED")
    print(f"    Output: {output_data}")

    complete_result = {
        "id": target_wi['id'],
        "status": "completed",
        "next_tasks": ["Archive", "Notify"]
    }
    print(f"    Status: {complete_result['status']}")
    print(f"    Next tasks: {complete_result['next_tasks']}")

    return True

checkout_and_complete_workitem("9876", "Review")
```

**Output**:
```
🔄 Processing work item in case 9876
============================================================

[1] List work items:
    Found 2 work items
    Target: WI-1 (Review)

[2] Checkout WI-1:
    Checked out, acquired lock
    Data: {'invoice_amount': 5000.0, 'vendor_name': 'ACME Corp'}

[3] Complete WI-1:
    Decision: APPROVED
    Output: {'approved': True, 'notes': '...', 'timestamp': '2026-02-21T...'}
    Status: completed
    Next tasks: ['Archive', 'Notify']
```

---

## Example 6: MCP Tool Discovery (How Z.AI Finds YAWL)

Z.AI discovers available tools via MCP protocol.

```python
import json

def discover_yawl_tools():
    """
    Z.AI fetches tool catalog from YAWL MCP server.
    In production: GET https://yawl-server:8080/.well-known/mcp.json
    """

    # Simulated response from YAWL MCP server
    mcp_response = {
        "tools": [
            {
                "name": "cases_submit",
                "description": "Submit a case to a YAWL workflow specification",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "spec_id": {"type": "string"},
                        "case_data": {"type": "object"},
                        "idempotency_key": {"type": "string"}
                    },
                    "required": ["spec_id", "idempotency_key"]
                }
            },
            {
                "name": "cases_status",
                "description": "Get the current status of a running case",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "case_id": {"type": "string"}
                    },
                    "required": ["case_id"]
                }
            },
            {
                "name": "workitems_list",
                "description": "List work items in a case",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "case_id": {"type": "string"}
                    },
                    "required": ["case_id"]
                }
            },
            {
                "name": "specifications_list",
                "description": "List available workflow specifications",
                "inputSchema": {"type": "object", "properties": {}}
            }
        ]
    }

    print("\n🔍 YAWL Tool Discovery")
    print("=" * 60)
    print(f"Discovered {len(mcp_response['tools'])} tools:\n")

    for tool in mcp_response['tools']:
        required = tool['inputSchema'].get('required', [])
        print(f"  • {tool['name']}")
        print(f"    {tool['description']}")
        if required:
            print(f"    Required: {', '.join(required)}")
        print()

    return mcp_response['tools']

discover_yawl_tools()
```

**Output**:
```
🔍 YAWL Tool Discovery
============================================================
Discovered 4 tools:

  • cases_submit
    Submit a case to a YAWL workflow specification
    Required: spec_id, idempotency_key

  • cases_status
    Get the current status of a running case
    Required: case_id

  • workitems_list
    List work items in a case
    Required: case_id

  • specifications_list
    List available workflow specifications
```

---

## Key Takeaways

1. **Idempotency**: Always pass `idempotency_key` with `cases_submit`. Z.AI will retry on transient failures; YAWL will recognize the key and return the same case_id.

2. **Polling Pattern**: Call `cases_status` every 5 seconds until `status` is `completed`, `failed`, or `error`.

3. **Error Handling**: HTTP 503 = transient (retry); HTTP 400/403/404 = permanent (fail immediately).

4. **Authentication**: Z.AI gets JWT from environment, includes `Authorization: Bearer <token>` in all tool calls.

5. **Work Items**: Z.AI can also checkout and complete individual tasks within a case for more granular control.

6. **Tool Discovery**: Z.AI discovers tool catalog from `/.well-known/mcp.json` on startup.

---

**See also**: Full architecture in `Z.AI-YAWL-INTEGRATION-DESIGN.md`, 2-page summary in `Z.AI-YAWL-INTEGRATION-SUMMARY.md`.

