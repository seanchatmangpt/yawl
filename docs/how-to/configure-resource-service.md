# How to Configure the Resource Service

## Prerequisites

- YAWL engine deployed and reachable at `http://localhost:8080/yawl`
- Resource service WAR deployed at `http://localhost:8080/resourceService`
- A YAWL specification loaded into the engine with resourcing directives on at least one task
- PostgreSQL (or another supported database) accessible to the resource service

## Steps

### 1. Configure the Resource Service Back-end URI

The resource service needs to know where the YAWL engine's Interface B endpoint is. Edit
`build/resourceService/web.xml` (or the equivalent deployed configuration) and set the
engine connection properties:

```xml
<!-- web.xml context params for resource service -->
<context-param>
    <param-name>InterfaceBBackEnd</param-name>
    <param-value>http://localhost:8080/yawl/ib</param-value>
</context-param>
<context-param>
    <param-name>EngineLogonName</param-name>
    <param-value>admin</param-value>
</context-param>
<context-param>
    <param-name>EngineLogonPassword</param-name>
    <param-value>YAWL</param-value>
</context-param>
```

If you are connecting from the scheduling service, also update
`build/schedulingService/properties/yawl.properties`:

```properties
InterfaceBClient.backEndURI=http://localhost:8080/yawl/ib
ResourceGatewayClient.backEndURI=http://localhost:8080/resourceService/gateway
WorkQueueGatewayClient.backEndURI=http://localhost:8080/resourceService/workqueuegateway
```

### 2. Configure the Persistence Database

The resource service stores participants, roles, capabilities, and positions in a relational
database. Configure `build/properties/hibernate.properties` for your database:

```properties
# PostgreSQL (recommended for production)
hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
hibernate.connection.driver_class=org.postgresql.Driver
hibernate.connection.url=jdbc:postgresql://localhost:5432/yawl
hibernate.connection.username=postgres
hibernate.connection.password=yawl

# HikariCP connection pool
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.minimumIdle=5
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.leakDetectionThreshold=60000
hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider
```

For development, H2 in-memory works without setup:

```properties
hibernate.dialect=org.hibernate.dialect.H2Dialect
hibernate.connection.driver_class=org.h2.Driver
hibernate.connection.url=jdbc:h2:mem:yawl;DB_CLOSE_DELAY=-1
hibernate.connection.username=sa
hibernate.connection.password=
```

### 3. Define Participants

Participants represent people who can be assigned work items. Add participants via the
resource service gateway HTTP API (once deployed):

```bash
# Register a participant
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addParticipant" \
  -d "userid=jsmith" \
  -d "firstname=John" \
  -d "lastname=Smith" \
  -d "password=secret" \
  -d "isAdmin=false" \
  -d "sessionHandle=$(cat /tmp/session)"
```

Participants hold work items from their worklist. A participant without any role
assignment receives no items from role-based routing; assign roles in step 4.

### 4. Define Roles and Assign Participants

Roles group participants for routing. A task's resourcing spec references a role by name,
and all participants with that role can receive the work item:

```bash
# Create the Approver role
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addRole" \
  -d "roleName=Approver" \
  -d "sessionHandle=$(cat /tmp/session)"

# Assign jsmith to the Approver role (use the role ID returned above)
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=updateParticipant" \
  -d "participantID=<pid>" \
  -d "roles=<roleID>" \
  -d "sessionHandle=$(cat /tmp/session)"
```

### 5. Configure the Allocation Strategy in the Specification

In the YAWL specification XML, each task that uses the resource service carries a
`resourcing` element. The `allocate` child specifies the strategy:

```xml
<resourcing>
  <offer initiator="system">
    <distributionSet>
      <initialSet>
        <role>Approver</role>
      </initialSet>
    </distributionSet>
    <filters/>
    <constraints/>
  </offer>
  <allocate initiator="system">
    <allocator>
      <!-- RandomChoice: pick a random participant from those who were offered the item -->
      <name>RandomChoice</name>
      <params/>
    </allocator>
  </allocate>
  <start initiator="user"/>
</resourcing>
```

Built-in allocators available in YAWL's resource service:

| Allocator name | Behaviour |
|---|---|
| `RandomChoice` | Selects a random participant from the offered set |
| `RoundRobin` | Cycles through participants in round-robin order |
| `ShortestQueue` | Picks the participant with the fewest active work items |
| `MostCapable` | Ranks by capability match score, picks the highest |

To use a custom allocator, implement the allocator interface and reference it by fully
qualified class name in the `<name>` element.

### 6. Configure Mail Notifications (optional)

When work items are offered to a participant, the resource service can send an email
notification. Edit `build/resourceService/mail.properties`:

```properties
# Use Courier for SMTP relay
provider=Courier
uid=<your-courier-uid>

# Or configure a custom SMTP server
provider=Custom
host=smtp.example.com
port=587
strategy=TLS
user=alerts@example.com
password=<smtp-password>
fromName=YAWL Workflow

# Message body template (placeholders: {firstname}, {id}, {doco}, {ui_url})
content=<p>Hello {firstname}</p><p>You have a new work item: ID={id}</p>
ui_url=http://localhost:8080/yawlui/
```

### 7. Annotate Work Items with Resource Status

The engine signals the resource service when work items are enabled. The resource service
tracks them through a lifecycle that maps to `WorkItemRecord` resource status constants:

| Constant | Meaning |
|---|---|
| `statusResourceUnresourced` | Item exists but no resource assigned yet |
| `statusResourceOffered` | Offered to one or more participants |
| `statusResourceAllocated` | Assigned to one specific participant |
| `statusResourceStarted` | Participant has started working on the item |

Monitor allocation progress programmatically via the Interface B client:

```java
InterfaceBClient ibClient = new InterfaceBClient("http://localhost:8080/yawl/ib");
String handle = ibClient.connect("admin", "YAWL");

List<WorkItemRecord> items = ibClient.getWorkItemsWithIdentifier("Approve_Order", handle);
for (WorkItemRecord item : items) {
    System.out.printf("Item %s: engine status=%s, resource status=%s%n",
        item.getID(), item.getStatus(), item.getResourceStatus());
}
```

## Verify

1. Start both the engine and the resource service. In the engine log you should see:
   ```
   Resource Service connected on Interface B.
   ```

2. Navigate to `http://localhost:8080/resourceService` and log in as `admin / YAWL`.
   The admin view shows participants, roles, and active work items.

3. Launch a case that has a task with `<offer initiator="system">` using a role you
   configured. The task's work item should appear in the worklist of every participant
   who holds that role.

4. Log in as a participant and check the worklist endpoint:
   ```bash
   curl "http://localhost:8080/resourceService/workqueuegateway?action=getQueuedWorkItems&participantID=<pid>&sessionHandle=<sh>"
   ```
   The response XML contains all items currently on that participant's queue.

## Troubleshooting

**Resource service shows "Not connected to engine"**
Check that the `InterfaceBBackEnd` context param in `web.xml` matches the running engine
URL. The resource service polls the engine on startup; restart the resource service after
fixing the URL.

**Work items not appearing in any worklist**
Verify the specification's `<resourcing>` block has `initiator="system"` on `<offer>`.
If `initiator="user"` is set, the participant manually allocates from an unresourced
queue, and no automatic distribution occurs.

**AllocationStrategy not found**
Custom allocator class names must be on the resource service classpath. Place the JAR in
`WEB-INF/lib` and restart the service.

**Participant has no capacity left (all items at capacity)**
The scheduling service imposes capacity constraints. If `msgHasNoRole` or capacity
rejection messages appear in the log, confirm the participant's role assignment and
reduce the `maximumPoolSize` on HikariCP to prevent over-allocation of database
connections competing with workflow threads.
