# Reference: Resource Management API

Complete API reference for YAWL resource allocation and work item routing.

---

## Work Item Allocator

### AllocationStrategy Interface

```java
public interface AllocationStrategy {
    /**
     * Allocate a work item to the best candidate.
     */
    Participant allocate(String taskType, List<Participant> candidates)
        throws AllocationException;

    /**
     * Get strategy name (for logging/metrics).
     */
    String strategyName();
}
```

### Built-in Strategies

#### RandomAllocator

```java
public class RandomAllocator implements AllocationStrategy {
    private final Random random = new Random();

    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }
}
```

#### RoundRobinAllocator

```java
public class RoundRobinAllocator implements AllocationStrategy {
    private int lastIndex = 0;

    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        Participant p = candidates.get(lastIndex % candidates.size());
        lastIndex++;
        return p;
    }
}
```

#### ShortestQueueAllocator

```java
public class ShortestQueueAllocator implements AllocationStrategy {
    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(Participant::getCurrentWorkload))
            .orElseThrow(() -> new AllocationException("No candidates"));
    }
}
```

#### CapabilityBasedAllocator

```java
public class CapabilityBasedAllocator implements AllocationStrategy {
    @Override
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.stream()
            .filter(p -> p.hasCapability(taskType))
            .min(Comparator.comparingInt(Participant::getCurrentWorkload))
            .orElseThrow(() -> new AllocationException(
                "No participant with capability: " + taskType));
    }
}
```

---

## Participant Model

```java
public class Participant {
    public String id;
    public String name;
    public String department;
    public List<String> roles;
    public List<Capability> capabilities;
    public int capacity;
    public int currentWorkload;
    public ParticipantStatus status;  // ACTIVE, INACTIVE, ON_LEAVE
    public String manager;
    public Instant lastActive;

    public boolean hasRole(String role);
    public boolean hasCapability(String capability);
    public int availableCapacity();
    public void addWorkload(int amount);
    public void removeWorkload(int amount);
}

public enum ParticipantStatus {
    ACTIVE,
    INACTIVE,
    ON_LEAVE,
    SUSPENDED
}
```

---

## Organization Model

```java
public class Organization {
    public String name;
    public List<Department> departments;
    public List<Participant> participants;
    public List<Role> roles;
    public Map<String, Participant> participantIndex;

    public Participant getParticipant(String id);
    public List<Participant> getByRole(String roleName);
    public List<Participant> getByCapability(String capability);
    public List<Participant> getDepartmentMembers(String departmentId);
}

public record Department(
    String id,
    String name,
    String managerId,
    List<String> roles
) {}

public record Role(
    String id,
    String name,
    String description
) {}

public record Capability(
    String name,
    int proficiencyLevel,  // 1-5
    Instant acquiredDate
) {}
```

---

## Organization Model Loaders

### JSON Loader

```java
public class JsonOrgModelLoader {
    public static Organization load(String filePath) throws Exception;
    public static Organization load(Path filePath) throws Exception;
    public static Organization load(InputStream inputStream) throws Exception;
}
```

### Database Loader

```java
public class DatabaseOrgModelLoader {
    private final DataSource dataSource;

    public Organization load() throws Exception;
    public Organization load(String orgId) throws Exception;

    public void save(Organization org) throws Exception;
    public void delete(String orgId) throws Exception;
}
```

### LDAP Loader

```java
public class LdapOrgModelLoader {
    private final LdapTemplate ldapTemplate;

    public Organization loadFromLdap(String baseDn) throws Exception;

    public void syncLdapToDatabase(String ldapBaseDn, DataSource target)
        throws Exception;
}
```

---

## Work Item Allocation

### WorkItemAllocator

```java
public class WorkItemAllocator {
    public Participant allocate(String taskType, int currentWorkItems)
        throws AllocationException;

    public Participant allocate(String taskType, List<Participant> candidates)
        throws AllocationException;

    public List<Participant> getCandidates(String taskType)
        throws AllocationException;

    public void registerRule(AllocationRule rule);
    public void registerStrategy(String taskType, AllocationStrategy strategy);
}

public record AllocationRule(
    String taskType,
    String requiredCapability,
    String preferredRole
) {}
```

---

## Constraints

```java
public interface AllocationConstraint {
    boolean isSatisfied(String taskId, Participant candidate,
        AllocationContext context) throws AllocationException;

    String constraintName();
}
```

### Built-in Constraints

#### FourEyesPrinciple

```java
public class FourEyesPrinciple implements AllocationConstraint {
    /**
     * Ensure different people review and approve.
     */
    @Override
    public boolean isSatisfied(String taskId, Participant candidate,
            AllocationContext context) {
        Set<String> previousWorkers = context.getHistoricalParticipants();
        return !previousWorkers.contains(candidate.id);
    }
}
```

#### ChineseWallPrinciple

```java
public class ChineseWallPrinciple implements AllocationConstraint {
    /**
     * Prevent conflicts of interest (e.g., competing clients).
     */
    @Override
    public boolean isSatisfied(String taskId, Participant candidate,
            AllocationContext context) {
        String caseClient = context.getAttribute("client");
        Set<String> candidateClients = history.getClientsWorkedFor(candidate.id);
        return !isCompetingClient(caseClient, candidateClients);
    }
}
```

#### AvailabilityConstraint

```java
public class AvailabilityConstraint implements AllocationConstraint {
    @Override
    public boolean isSatisfied(String taskId, Participant candidate,
            AllocationContext context) {
        return candidate.currentWorkload < candidate.capacity &&
               candidate.status == ParticipantStatus.ACTIVE;
    }
}
```

---

## REST Interface B Operations

### List Work Items

```bash
GET /ib?action=getCompleteListOfLiveWorkItems&sessionHandle=<handle>

Response:
<workItems>
  <workItem>
    <caseID>42</caseID>
    <taskID>ApproveOrder</taskID>
    <status>Enabled</status>
    <enabledTime>2026-02-28T10:00:00Z</enabledTime>
    <data>...</data>
  </workItem>
</workItems>
```

### Check Out Work Item

```bash
POST /ib
  -d "action=checkOutWorkItem"
  -d "workItemID=42:ApproveOrder"
  -d "sessionHandle=<handle>"

Response:
<workItem>
  <caseID>42</caseID>
  <taskID>ApproveOrder</taskID>
  <data>...</data>
</workItem>
```

### Check In Work Item

```bash
POST /ib
  -d "action=checkInWorkItem"
  -d "workItemID=42:ApproveOrder"
  -d "data=<ApproveOrder><decision>approved</decision></ApproveOrder>"
  -d "sessionHandle=<handle>"

Response:
<response>success</response>
```

---

## Worklist Service

```java
public interface WorklistService {
    /**
     * Get all work items available to a participant.
     */
    List<WorkItemRecord> getMyWorkItems(String participantId)
        throws Exception;

    /**
     * Check out a work item.
     */
    void checkOut(String participantId, String workItemId)
        throws Exception;

    /**
     * Check in a work item with output data.
     */
    void checkIn(String participantId, String workItemId, String outputData)
        throws Exception;

    /**
     * Delegate a work item to another participant.
     */
    void delegate(String fromParticipant, String toParticipant,
        String workItemId) throws Exception;

    /**
     * Get work item details.
     */
    WorkItemRecord getWorkItem(String workItemId)
        throws Exception;
}
```

---

## Metrics

| Metric | Description |
|--------|-------------|
| `resourcing.allocation.duration` | Time to allocate (timer) |
| `resourcing.allocation.success` | Successful allocations (counter) |
| `resourcing.allocation.failures` | Failed allocations (counter) |
| `resourcing.participant.utilization` | % capacity used (gauge) |
| `resourcing.workitem.wait_time` | Time until check-out (timer) |

---

## Exception Handling

```java
public class AllocationException extends Exception {
    public AllocationException(String message);
    public AllocationException(String message, Throwable cause);

    public AllocationExceptionType getType();
}

public enum AllocationExceptionType {
    NO_CANDIDATES_AVAILABLE,
    CONSTRAINT_VIOLATION,
    CAPACITY_EXCEEDED,
    PARTICIPANT_NOT_FOUND,
    INVALID_TASK_TYPE,
    UNKNOWN
}
```

---

## See Also

- **Tutorial:** `docs/tutorials/resourcing-getting-started.md`
- **How-To:** `docs/how-to/resourcing-org-model.md`
- **Explanation:** `docs/explanation/resource-allocation.md`
