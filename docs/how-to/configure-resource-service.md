# How to Configure Resource Allocation in YAWL v6

> YAWL v6 does not have a standalone resource service. The `yawl-resourcing` Maven module
> is a stub with no Java implementation. There is no resource service WAR, no worklist
> gateway, and no `ResourceManager` class.

## What "Resource Service" Meant in YAWL v4

In YAWL v4, the Resource Service was a separate web application that maintained an
organisational model (participants, roles, capabilities, positions) and distributed work
items to human participants via a worklist. It implemented offer/allocate/start lifecycle
stages for each work item.

None of this is implemented in YAWL v6.

## How Work Item Assignment Works in YAWL v6

Work item routing in YAWL v6 uses two mechanisms:

1. **MCP tools** — AI agents check out and check in work items programmatically via
   `yawl_checkout_work_item` and `yawl_checkin_work_item`.

2. **A2A skills** — The A2A protocol (`YawlA2AServer`) lets named agent skills be
   invoked when a work item fires. The skill performs the work and checks in the item.

Human tasks — tasks that require a person to make a decision — are handled by your
application. Your application polls the engine for available work items, presents them
to users through your own UI, and calls the engine's Interface B to check out and check
in items on the user's behalf.

## Access Control: Who Can Touch Which Work Items

YAWL v6 enforces access control through OAuth2 scopes and JWT claims, implemented in
`RbacAuthorizationEnforcer` and `YawlOAuth2Scopes`.

The relevant scopes for work item access are:

| Scope | Permitted operations |
|---|---|
| `yawl:operator` | Launch cases, cancel cases, check out/in work items |
| `yawl:agent` | Same as operator, plus MCP and A2A integration endpoints |
| `yawl:monitor` | Read-only: get case status, list work items, get case data |
| `yawl:admin` | All operations |

A human participant's JWT must carry `yawl:operator` (or `yawl:admin`) to check out
and complete work items. An automated agent's service account JWT must carry `yawl:agent`.

Configure scopes in your OIDC provider (Keycloak, Auth0, Azure AD). The `YawlOAuth2Scopes`
class in `src/org/yawlfoundation/yawl/integration/oauth2/YawlOAuth2Scopes.java` has the
canonical scope string constants.

## Practical Pattern: Routing Work Items to Human Participants

Since there is no built-in worklist, you build the routing logic in your application:

### 1. Poll for available work items

```bash
# Get all enabled work items across all running cases
curl -s -H "Authorization: Bearer $JWT" \
  "http://localhost:8080/yawl/ib?action=getCompleteListOfLiveWorkItems&sessionHandle=$SESSION"
```

The response is an XML list of `WorkItemRecord` elements. Each carries the task ID,
case ID, specification URI, and current status.

### 2. Filter by task type to determine which human should act

Your application determines which participant should handle each work item. Because
there is no org model in v6, this mapping lives in your application. Common patterns:

- Store task-to-role mappings in your own database and look them up by `taskID`.
- Read a claim from the user's JWT (e.g. `department`, `role`) and match it to the
  task's `specURI` using rules you define.
- For automated tasks, match by task name prefix and route to the appropriate
  A2A skill or MCP tool.

### 3. Check out a work item for a specific participant

```bash
# Check out work item on behalf of participant (using their session handle)
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkOutWorkItem" \
  -d "workItemID=42:ApproveOrder" \
  -d "sessionHandle=$PARTICIPANT_SESSION"
```

The participant must hold a token with `yawl:operator` scope. The `sessionHandle` here
is the participant's own YAWL session (obtained by authenticating to the engine with
their credentials), not an admin handle.

### 4. Check in the work item with output data

```bash
# Check in the work item after the participant has taken action
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkInWorkItem" \
  -d "workItemID=42:ApproveOrder" \
  -d "data=<ApproveOrder><decision>approved</decision></ApproveOrder>" \
  -d "sessionHandle=$PARTICIPANT_SESSION"
```

## Routing Automated Work Items to AI Agents

For tasks that should be handled by AI agents rather than humans, use the MCP tools
directly. The agent:

1. Calls `yawl_get_work_items` to discover available work items.
2. Calls `yawl_checkout_work_item` with the work item ID.
3. Performs its task (external API call, data transformation, decision, etc.).
4. Calls `yawl_checkin_work_item` with the output data.

For more structured routing, register a custom `YawlMcpTool` (see
`docs/tutorials/06-write-a-custom-work-item-handler.md`) that encapsulates the agent's
decision logic and is invoked by name.

## Interface B Direct Access

For Java-based applications, the `InterfaceB_EnvironmentBasedClient` class
(`src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EnvironmentBasedClient.java`)
wraps all Interface B HTTP calls. Include `yawl-engine` as a dependency and use it
directly:

```java
InterfaceB_EnvironmentBasedClient ibClient =
    new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");

String sessionHandle = ibClient.connect("operator-user", "password");

// Get work items for a specific case
List<WorkItemRecord> items = ibClient.getWorkItemsForCase("42", sessionHandle);

// Check out the first enabled item
WorkItemRecord item = items.get(0);
String result = ibClient.checkOutWorkItem(item.getID(), sessionHandle);

// Check in with output data
ibClient.checkInWorkItem(
    item.getID(),
    "<data><decision>approved</decision></data>",
    null,
    sessionHandle);
```

## Verify

Confirm the engine is accepting work item operations:

```bash
# Get a session handle
SESSION=$(curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect&userid=admin&password=YAWL" | grep -oP '(?<=<response>)[^<]+')

# List all live work items
curl -s "http://localhost:8080/yawl/ib?action=getCompleteListOfLiveWorkItems&sessionHandle=$SESSION"
```

A successful response returns an XML document containing zero or more `workItem` elements.
An `<failure>` element indicates an authentication or engine connectivity problem.

## Troubleshooting

**"Not authorised" on checkOutWorkItem**
The caller's token does not include `yawl:operator` or `yawl:admin` scope. Verify the
JWT claims using `jwt.io` or your OIDC provider's token introspection endpoint. Update
the client scope assignment in your OIDC provider.

**Work item is in "Enabled" status but checkOut fails**
A work item can only be checked out if it is in `Enabled` or `Fired` status. If the
engine returned it as `Enabled` but the check-out fails with "item not found", another
caller checked it out between your list and check-out calls. Re-query the work item list
and retry.

**No work items returned despite a running case**
Verify the case is actively executing (not suspended). Call
`getCaseState(caseId, sessionHandle)` and confirm the net has active tokens. If the case
is suspended, resume it with `yawl_resume_case` via the MCP server or call
`unsuspendWorkItem` directly via Interface B.
