# Tutorial: Resource Management Getting Started

Welcome to YAWL Resource Management. By the end of this tutorial, you will have:
- Set up an organizational model (participants, roles, capabilities)
- Configured work item allocation strategies
- Routed tasks to human users and automated agents
- Built a simple worklist application

This is a **learning-by-doing** guide. You'll build a complete order approval workflow with resource routing.

---

## What is Resource Management?

In YAWL v6, resource management handles **who does what**:

1. **Organizational Model** — Participants, roles, positions, capabilities
2. **Work Item Allocation** — Round-robin, shortest queue, random, role-based
3. **Task Constraints** — Four-eyes, Chinese wall, delegation rules
4. **Worklist Management** — Human users check out, complete, and check in work items

Unlike v4, there's no separate Resource Service WAR. Everything integrates directly into your application via:
- **REST API** (HTTP Interface B)
- **MCP tools** (for AI agents)
- **A2A skills** (for agent orchestration)

---

## Prerequisites

- YAWL 6.0.0 built from source (Tutorial 01 completed)
- Java 21+ (Java 25 recommended)
- Maven 3.9+
- A running YAWL engine (Tutorial 03)
- Basic familiarity with YAWL specifications (Tutorial 04)

### Quick Check

```bash
# Verify yawl-resourcing module exists
ls -la yawl-resourcing/pom.xml

# Build the module
mvn -pl yawl-resourcing clean package
```

---

## Part 1: Build an Organizational Model

### Scenario

Your company has three departments:
- **Finance** — Reviews orders > $1,000
- **Operations** — Coordinates shipping
- **Compliance** — Final approval for high-risk orders

You have 8 people across these departments with different roles and capabilities.

### Step 1: Define Your Organizational Data

Create a JSON file representing your org structure:

```json
{
  "organisation": {
    "name": "ACME Corp",
    "departments": [
      {
        "id": "fin",
        "name": "Finance",
        "roles": ["reviewer", "approver"]
      },
      {
        "id": "ops",
        "name": "Operations",
        "roles": ["shipper", "coordinator"]
      },
      {
        "id": "comp",
        "name": "Compliance",
        "roles": ["auditor", "final_approver"]
      }
    ],
    "participants": [
      {
        "id": "p_alice",
        "name": "Alice Chen",
        "department": "fin",
        "roles": ["approver"],
        "capabilities": ["order_review", "risk_assessment"],
        "capacity": 10
      },
      {
        "id": "p_bob",
        "name": "Bob Martinez",
        "department": "fin",
        "roles": ["reviewer"],
        "capabilities": ["order_review"],
        "capacity": 15
      },
      {
        "id": "p_carol",
        "name": "Carol Singh",
        "department": "ops",
        "roles": ["shipper"],
        "capabilities": ["shipping_coordination"],
        "capacity": 20
      },
      {
        "id": "p_dave",
        "name": "Dave Johnson",
        "department": "comp",
        "roles": ["final_approver"],
        "capabilities": ["compliance_review", "risk_assessment"],
        "capacity": 8
      }
    ],
    "roles": [
      {
        "id": "approver",
        "name": "Approver",
        "description": "Can approve orders"
      },
      {
        "id": "reviewer",
        "name": "Reviewer",
        "description": "Can review orders"
      },
      {
        "id": "shipper",
        "name": "Shipper",
        "description": "Can process shipments"
      },
      {
        "id": "final_approver",
        "name": "Final Approver",
        "description": "Final compliance approval"
      }
    ]
  }
}
```

Save this as `org-model.json`.

### Step 2: Load the Organizational Model

Create a Java class to load and parse the org model:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class OrganizationModelLoader {

    static class Participant {
        public String id;
        public String name;
        public String department;
        public List<String> roles;
        public List<String> capabilities;
        public int capacity;

        @Override
        public String toString() {
            return String.format(
                "%s (%s) - Roles: %s, Capacity: %d",
                name, id, roles, capacity
            );
        }
    }

    static class Organization {
        public String name;
        public List<Participant> participants;
    }

    public static Organization loadOrgModel(String filePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path path = Paths.get(filePath);
        String json = Files.readString(path);

        JsonNode root = mapper.readTree(json);
        Organization org = new Organization();
        org.name = root.get("organisation").get("name").asText();
        org.participants = new ArrayList<>();

        for (JsonNode participantNode : root.get("organisation").get("participants")) {
            Participant p = mapper.treeToValue(participantNode, Participant.class);
            org.participants.add(p);
        }

        return org;
    }

    public static void main(String[] args) throws Exception {
        Organization org = loadOrgModel("org-model.json");

        System.out.println("=== Organization: " + org.name + " ===\n");

        for (Participant p : org.participants) {
            System.out.println(p);
        }
    }
}
```

Run it:

```bash
javac -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) \
    OrganizationModelLoader.java
java -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):. \
    OrganizationModelLoader
```

Expected output:
```
=== Organization: ACME Corp ===

Alice Chen (p_alice) - Roles: [approver], Capacity: 10
Bob Martinez (p_bob) - Roles: [reviewer], Capacity: 15
Carol Singh (p_ops) - Roles: [shipper], Capacity: 20
Dave Johnson (p_dave) - Roles: [final_approver], Capacity: 8
```

---

## Part 2: Implement Work Item Allocation

### Scenario

You want to allocate tasks to people based on:
1. **Task type** matches participant's **capability**
2. **Participant's current workload** is considered
3. **Role membership** is enforced

### Step 1: Define Allocation Rules

Create a simple allocation engine:

```java
import java.util.List;
import java.util.Comparator;

public class WorkItemAllocator {

    static class AllocationRule {
        String taskType;
        String requiredCapability;
        String preferredRole;

        AllocationRule(String taskType, String capability, String role) {
            this.taskType = taskType;
            this.requiredCapability = capability;
            this.preferredRole = role;
        }
    }

    private final List<AllocationRule> rules;
    private final List<Participant> participants;

    public WorkItemAllocator(List<AllocationRule> rules,
            List<Participant> participants) {
        this.rules = rules;
        this.participants = participants;
    }

    /**
     * Allocate a work item to the best participant.
     * Strategy: Find participants with required capability,
     * filter to those with required role, select with lowest current workload.
     */
    public Participant allocate(String taskType, int currentWorkItems) {
        // Find the matching rule
        AllocationRule rule = rules.stream()
            .filter(r -> r.taskType.equals(taskType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No allocation rule for task: " + taskType));

        // Find participants with required capability
        List<Participant> qualified = participants.stream()
            .filter(p -> p.capabilities.contains(rule.requiredCapability))
            .filter(p -> p.roles.contains(rule.preferredRole))
            .filter(p -> p.capacity > currentWorkItems)  // Still has capacity
            .sorted(Comparator.comparingInt(p -> p.capacity - currentWorkItems))
            .toList();

        if (qualified.isEmpty()) {
            throw new IllegalArgumentException(
                "No available participant for task: " + taskType);
        }

        return qualified.get(0);
    }

    public static void main(String[] args) throws Exception {
        // Load org model
        Organization org = OrganizationModelLoader.loadOrgModel("org-model.json");

        // Define allocation rules
        List<AllocationRule> rules = List.of(
            new AllocationRule("order_review", "order_review", "reviewer"),
            new AllocationRule("order_approval", "risk_assessment", "approver"),
            new AllocationRule("shipping", "shipping_coordination", "shipper"),
            new AllocationRule("final_approval", "compliance_review", "final_approver")
        );

        WorkItemAllocator allocator = new WorkItemAllocator(rules, org.participants);

        // Test allocations
        System.out.println("=== Work Item Allocation ===\n");

        Participant p1 = allocator.allocate("order_review", 5);
        System.out.println("Task 'order_review' allocated to: " + p1.name);

        Participant p2 = allocator.allocate("order_approval", 3);
        System.out.println("Task 'order_approval' allocated to: " + p2.name);

        Participant p3 = allocator.allocate("shipping", 10);
        System.out.println("Task 'shipping' allocated to: " + p3.name);
    }
}
```

Run the test:

```bash
javac -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):. \
    -d . *.java
java -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):. \
    WorkItemAllocator
```

Expected output:
```
=== Work Item Allocation ===

Task 'order_review' allocated to: Bob Martinez
Task 'order_approval' allocated to: Alice Chen
Task 'shipping' allocated to: Carol Singh
```

### Step 2: Advanced Allocation Strategies

Implement different allocation strategies:

```java
public interface AllocationStrategy {
    Participant allocate(String taskType, List<Participant> candidates);
}

public class RoundRobinAllocator implements AllocationStrategy {
    private int lastIndex = 0;

    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidates available");
        }
        Participant p = candidates.get(lastIndex % candidates.size());
        lastIndex++;
        return p;
    }
}

public class ShortestQueueAllocator implements AllocationStrategy {
    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(p -> p.capacity))
            .orElseThrow(() -> new IllegalArgumentException("No candidates"));
    }
}

public class RandomAllocator implements AllocationStrategy {
    private final java.util.Random random = new java.util.Random();

    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }
}
```

---

## Part 3: Integrate with YAWL Engine

### Step 1: Create a Work Item Handler

Create a handler that receives work items from the engine and routes them:

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.WorkItemRecord;

import java.util.List;

public class ResourceAwareWorkItemHandler {

    private final WorkItemAllocator allocator;
    private final InterfaceB_EnvironmentBasedClient ibClient;

    public ResourceAwareWorkItemHandler(
            WorkItemAllocator allocator,
            InterfaceB_EnvironmentBasedClient ibClient) {
        this.allocator = allocator;
        this.ibClient = ibClient;
    }

    /**
     * Handle an enabled work item by allocating it to an appropriate participant.
     */
    public void handleWorkItem(WorkItemRecord item, String sessionHandle)
            throws Exception {

        // Determine the task type from the work item
        String taskType = deriveTaskType(item.getTaskID());

        // Get current workload (in a real system, query your database)
        int currentWorkItems = getCurrentWorkload(taskType);

        // Allocate to a participant
        Participant allocated = allocator.allocate(taskType, currentWorkItems);

        System.out.println("Allocated " + item.getTaskID() +
            " to " + allocated.name);

        // In a real system, you would:
        // 1. Store this allocation in your database
        // 2. Notify the participant (email, mobile push, etc.)
        // 3. Update the work item status
    }

    private String deriveTaskType(String taskID) {
        // Parse task ID: e.g., "42:review" → "order_review"
        if (taskID.contains("review")) return "order_review";
        if (taskID.contains("approve")) return "order_approval";
        if (taskID.contains("ship")) return "shipping";
        return "default";
    }

    private int getCurrentWorkload(String taskType) {
        // In a real system, query your workload database
        return 0;
    }

    public static void main(String[] args) throws Exception {
        // Load org model and create allocator
        Organization org = OrganizationModelLoader.loadOrgModel("org-model.json");
        List<AllocationRule> rules = List.of(
            new AllocationRule("order_review", "order_review", "reviewer"),
            new AllocationRule("order_approval", "risk_assessment", "approver")
        );
        WorkItemAllocator allocator = new WorkItemAllocator(rules, org.participants);

        // Create engine client
        InterfaceB_EnvironmentBasedClient ibClient =
            new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");

        ResourceAwareWorkItemHandler handler =
            new ResourceAwareWorkItemHandler(allocator, ibClient);

        // Connect to engine
        String sessionHandle = ibClient.connect("admin", "YAWL");

        // Get live work items
        List<WorkItemRecord> items = ibClient.getCompleteListOfLiveWorkItems(sessionHandle);

        // Allocate each one
        for (WorkItemRecord item : items) {
            if (item.getStatus().equals("Enabled")) {
                handler.handleWorkItem(item, sessionHandle);
            }
        }

        ibClient.disconnect(sessionHandle);
    }
}
```

### Step 2: Build a Worklist Application

Create a simple web interface for your worklist:

```java
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.WorkItemRecord;

import java.util.List;
import java.util.Map;

@Service
public class WorklistService {

    private final InterfaceB_EnvironmentBasedClient ibClient;

    public WorklistService() {
        this.ibClient = new InterfaceB_EnvironmentBasedClient(
            "http://localhost:8080/yawl/ib");
    }

    /**
     * Get all work items available to a participant.
     */
    public List<WorkItemRecord> getMyWorkItems(String participantId,
            String password) throws Exception {

        // Connect as the participant
        String sessionHandle = ibClient.connect(participantId, password);

        // Get all items they can work on
        List<WorkItemRecord> items = ibClient.getCompleteListOfLiveWorkItems(sessionHandle);

        // Filter to those allocated to this participant
        // (In a real system, check your allocation database)
        List<WorkItemRecord> myItems = items.stream()
            .filter(item -> isAllocatedTo(item, participantId))
            .toList();

        ibClient.disconnect(sessionHandle);

        return myItems;
    }

    /**
     * Check out a work item for a participant.
     */
    public void checkOut(String participantId, String password,
            String workItemId) throws Exception {

        String sessionHandle = ibClient.connect(participantId, password);

        ibClient.checkOutWorkItem(workItemId, sessionHandle);

        System.out.println(participantId + " checked out " + workItemId);

        ibClient.disconnect(sessionHandle);
    }

    /**
     * Check in a work item with output data.
     */
    public void checkIn(String participantId, String password,
            String workItemId, String outputData) throws Exception {

        String sessionHandle = ibClient.connect(participantId, password);

        ibClient.checkInWorkItem(
            workItemId,
            outputData,
            null,  // no logData
            sessionHandle
        );

        System.out.println(participantId + " checked in " + workItemId);

        ibClient.disconnect(sessionHandle);
    }

    private boolean isAllocatedTo(WorkItemRecord item, String participantId) {
        // Check your allocation database
        // For now, return true to show all items
        return true;
    }
}

@RestController
@RequestMapping("/api/worklist")
public class WorklistController {

    private final WorklistService worklistService;

    public WorklistController(WorklistService worklistService) {
        this.worklistService = worklistService;
    }

    @GetMapping("/items")
    public List<WorkItemRecord> getMyItems(
            @RequestParam String participantId,
            @RequestParam String password) throws Exception {
        return worklistService.getMyWorkItems(participantId, password);
    }

    @PostMapping("/checkout")
    public void checkout(
            @RequestParam String participantId,
            @RequestParam String password,
            @RequestParam String workItemId) throws Exception {
        worklistService.checkOut(participantId, password, workItemId);
    }

    @PostMapping("/checkin")
    public void checkin(
            @RequestParam String participantId,
            @RequestParam String password,
            @RequestParam String workItemId,
            @RequestBody Map<String, Object> outputData) throws Exception {
        worklistService.checkIn(
            participantId,
            password,
            workItemId,
            convertToXml(outputData)
        );
    }

    private String convertToXml(Map<String, Object> data) {
        // Convert map to XML (simplified)
        StringBuilder xml = new StringBuilder("<data>");
        for (var entry : data.entrySet()) {
            xml.append("<").append(entry.getKey()).append(">")
               .append(entry.getValue())
               .append("</").append(entry.getKey()).append(">");
        }
        xml.append("</data>");
        return xml.toString();
    }
}
```

---

## Part 4: Advanced Scenarios

### Scenario 1: Task Delegation

Allow a participant to delegate their work to another:

```java
public void delegateTask(String fromParticipant, String toParticipant,
        String workItemId) throws Exception {
    // Update your allocation database
    // Remove from → update to

    // Notify the new assignee
    notifyParticipant(toParticipant,
        "You have been delegated task: " + workItemId);
}
```

### Scenario 2: Four-Eyes Principle

Ensure sensitive tasks require two different participants:

```java
public boolean canApprove(String participantId, String workItemId) {
    // Check if this participant already reviewed the item
    WorkItemHistory history = getHistory(workItemId);

    // If the same person already reviewed, they can't approve
    return !history.wasReviewedBy(participantId);
}
```

### Scenario 3: Escalation

Escalate tasks that exceed a time threshold:

```java
public void checkAndEscalate(String workItemId) throws Exception {
    WorkItemRecord item = getWorkItem(workItemId);

    // If item has been with current owner for > 1 hour
    if (item.getTimeoutMinutes() > 60) {
        // Escalate to manager
        Participant manager = getManager(item.getCurrentOwner());
        reallocate(workItemId, manager.id);
    }
}
```

---

## Part 5: Test Your Resource System End-to-End

### Step 1: Create a Test Workflow

Create a YAWL specification with multiple resource-sensitive tasks:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="OrderApprovalWithResources">
    <net id="MainNet" isRootNet="true">
      <localVariable>
        <index>1</index>
        <name>order_amount</name>
        <type>double</type>
      </localVariable>
      <localVariable>
        <index>2</index>
        <name>approved</name>
        <type>boolean</type>
      </localVariable>
      <inputOutputCondition id="InputCondition"/>
      <task id="review" name="Review Order">
        <documentation>Resource: reviewer role</documentation>
      </task>
      <task id="approve" name="Approve Order">
        <documentation>Resource: approver role</documentation>
      </task>
      <task id="ship" name="Ship Order">
        <documentation>Resource: shipper role</documentation>
      </task>
      <outputCondition id="OutputCondition"/>
      <flow source="InputCondition" target="review"/>
      <flow source="review" target="approve"/>
      <flow source="approve" target="ship"/>
      <flow source="ship" target="OutputCondition"/>
    </net>
  </specification>
</specificationSet>
```

### Step 2: Deploy and Execute

```bash
# Deploy spec
curl -X POST "http://localhost:8080/yawl/ia" \
  -d "action=uploadSpecification" \
  -d "specFile=@OrderApprovalWithResources.yawl" \
  -d "sessionHandle=$ADMIN_SESSION"

# Launch a case
SESSION=$(curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect&userid=admin&password=YAWL" | \
  grep -oP '(?<=<response>)[^<]+')

CASE=$(curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=launchCase" \
  -d "specURI=OrderApprovalWithResources" \
  -d "caseParams=<order_amount>5000</order_amount>" \
  -d "sessionHandle=$SESSION" | \
  grep -oP '(?<=<caseID>)[^<]+')

echo "Case launched: $CASE"

# Test resource allocation
java -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):. \
    ResourceAwareWorkItemHandler
```

---

## Verify Your Setup

Run this verification test:

```java
public class ResourceManagementTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Resource Management Verification ===\n");

        // 1. Load org model
        try {
            Organization org = OrganizationModelLoader.loadOrgModel("org-model.json");
            System.out.println("✓ Loaded org model: " + org.name +
                " (" + org.participants.size() + " participants)");
        } catch (Exception e) {
            System.out.println("✗ Failed to load org model: " + e.getMessage());
            return;
        }

        // 2. Test allocation rules
        try {
            List<AllocationRule> rules = List.of(
                new AllocationRule("order_review", "order_review", "reviewer")
            );
            System.out.println("✓ Created " + rules.size() + " allocation rules");
        } catch (Exception e) {
            System.out.println("✗ Failed to create rules: " + e.getMessage());
        }

        // 3. Test YAWL connection
        try {
            InterfaceB_EnvironmentBasedClient client =
                new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");
            String handle = client.connect("admin", "YAWL");
            System.out.println("✓ Connected to YAWL engine: " + handle.substring(0, 10) + "...");
            client.disconnect(handle);
        } catch (Exception e) {
            System.out.println("✗ Failed to connect to engine: " + e.getMessage());
            System.out.println("  (Ensure engine is running on localhost:8080)");
        }

        System.out.println("\n=== Setup Complete! ===");
    }
}
```

---

## Next Steps

You've learned the basics! Explore:

1. **Advanced Allocation** — Read `docs/how-to/configure-resource-service.md` for LDAP integration
2. **Org Model Design** — Read `docs/explanation/resource-allocation.md` for architectural patterns
3. **REST API** — Read `docs/reference/api-reference.md` for complete Interface B reference
4. **Multi-Tenancy** — Read `docs/how-to/configure-multi-tenancy.md` to support multiple organizations
5. **Performance** — Read `docs/how-to/setup-org-model.md` for large-scale org model optimization

---

## Troubleshooting

### "Engine not reachable"
**Cause:** YAWL engine not running
**Fix:** Start the engine with `java -Xmx2g -jar yawl-engine.jar &`

### "No candidates available for task"
**Cause:** No participants have the required capability or role
**Fix:** Check your org model and allocation rules match

### "Work item check-out failed"
**Cause:** Participant lacks `yawl:operator` scope or incorrect sessionHandle
**Fix:** Verify OAuth2 scopes and that sessionHandle is fresh

---

## Success Criteria

By the end of this tutorial, you should:
- [ ] Load and parse an organizational model
- [ ] Create allocation rules for different task types
- [ ] Allocate work items to participants
- [ ] Integrate with the running YAWL engine
- [ ] Build a basic worklist application
- [ ] Handle check-out and check-in operations

Congratulations! You've completed the resource management tutorial!
