# Explanation: Resource Allocation Design & Strategy

Understanding the principles and patterns behind YAWL resource management.

---

## The Resource Problem

In workflow systems, the fundamental question is: **Who does what?**

Three competing goals:
1. **Fairness** — Distribute work evenly across people
2. **Efficiency** — Minimize idle time and task waiting
3. **Skill Match** — Route tasks to people who can do them

These goals often conflict:
- Fairness wants round-robin (everyone gets tasks equally)
- Efficiency wants shortest queue (load balance)
- Skill match wants capability-based (only experts)

YAWL resource allocation lets you choose your trade-offs.

---

## Allocation Strategies

### Strategy 1: Round-Robin (Simple)

```
Task 1 → Person A
Task 2 → Person B
Task 3 → Person C
Task 4 → Person A (cycle)
```

**Pros:** Fair, simple, predictable
**Cons:** Ignores skill, ignores workload

**Use when:** All people equally skilled, work evenly distributed

**Code:**

```java
public class RoundRobinAllocator implements AllocationStrategy {
    private int pointer = 0;

    public Participant allocate(String taskType, List<Participant> candidates) {
        Participant p = candidates.get(pointer % candidates.size());
        pointer++;
        return p;
    }
}
```

### Strategy 2: Shortest Queue (Efficiency)

```
Person A: 5 tasks
Person B: 2 tasks  ← allocate next task here
Person C: 8 tasks
```

**Pros:** Balances load, minimizes wait time
**Cons:** May overload fast workers, unfair to slow workers

**Use when:** Different people work at different speeds

**Code:**

```java
public class ShortestQueueAllocator implements AllocationStrategy {
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(Participant::getCurrentWorkload))
            .orElseThrow();
    }
}
```

### Strategy 3: Capability-Based (Skill Match)

```
Task: "Approve high-value orders" (requires "financial_analyst" skill)
    ↓
Filter to people with skill
    ↓
Among skilled people, use shortest queue
```

**Pros:** Routes to right person, ensures quality
**Cons:** Specialized people become bottleneck

**Use when:** Work requires specific expertise

**Code:**

```java
public class CapabilityBasedAllocator implements AllocationStrategy {
    public Participant allocate(String taskType, List<Participant> candidates) {
        return candidates.stream()
            .filter(p -> p.hasCapability(taskType))
            .min(Comparator.comparingInt(Participant::getCurrentWorkload))
            .orElseThrow(() -> new Exception("No skilled person available"));
    }
}
```

### Strategy 4: Weighted (Balanced)

```
Score = (0.3 × fairness) + (0.5 × efficiency) + (0.2 × skill_match)

For each candidate:
  fairness_score = 100 - (current_workload / capacity * 100)
  efficiency_score = 100 - ((current_workload - avg_workload) / avg_workload * 100)
  skill_score = has_capability ? 100 : 0
  total = weighted_sum

Allocate to max score
```

**Pros:** Balances all three goals
**Cons:** Requires tuning weights

**Use when:** You want optimal across multiple dimensions

---

## Organizational Models

### Pattern 1: Flat (Startup)

```
Manager (Alice)
├─ Operator (Bob)
├─ Operator (Carol)
└─ Operator (Dave)
```

**Characteristics:**
- Few people (<20)
- Few roles (<5)
- Everyone does everything

**Allocation:** Simple round-robin or skill-based

### Pattern 2: Departmental (Growing Company)

```
CEO
├─ Finance Director
│  ├─ Controller (Alice)
│  └─ Accountant (Bob)
├─ Operations Director
│  ├─ Logistics Manager (Carol)
│  └─ Shipper (Dave)
└─ Sales Director
   ├─ Sales Manager (Eve)
   └─ Sales Rep (Frank)
```

**Characteristics:**
- 10-100 people
- Clear reporting structure
- Role = department-bound

**Allocation:** Role-based + skill-based

**Rules:**
- "Order approval" → Finance roles only
- "Shipping" → Operations roles only

### Pattern 3: Matrix (Enterprise)

```
Functional Org:          Project Org:
CEO                      CEO
├─ Finance               ├─ Project X (Alice leads)
│  └─ Alice              │  ├─ Alice (finance lead)
├─ Engineering           │  ├─ Bob (engineer)
│  └─ Bob                │  └─ Carol (ops)
└─ Operations            └─ Project Y (Bob leads)
   └─ Carol                 └─ ...
```

**Characteristics:**
- 100+ people
- Dual reporting (manager + project lead)
- Person has multiple roles

**Allocation:** Complex scoring (functional role + project role + availability)

---

## Constraint Enforcement

### The Four-Eyes Principle

**Rule:** Different people review and approve (prevent fraud)

**Implementation:**

```
Task: "Approve Order"
    ↓
Get all people who worked on this case
    ↓
Filter out anyone who already worked it
    ↓
Allocate to remaining person
```

**SQL:**

```sql
SELECT DISTINCT p.participant_id, p.name
FROM oc_participants p
WHERE p.participant_id NOT IN (
    SELECT DISTINCT participant_id
    FROM workitem_history
    WHERE case_id = ?
)
ORDER BY current_workload ASC
LIMIT 1;
```

### Chinese Wall (Conflict of Interest)

**Rule:** Don't work on competing clients

**Implementation:**

```
Task: "Review Order" for Client "Company X"
    ↓
Find all people who worked for "Company Y" (competitor of X)
    ↓
Exclude them
    ↓
Allocate to remaining person
```

**Code:**

```java
public boolean isSuitable(Participant p, String client) {
    Set<String> clientsWorkedFor = history.getClientsWorkedFor(p.id);
    for (String c : clientsWorkedFor) {
        if (conflictRegistry.areCompetitors(client, c)) {
            return false;  // Conflict of interest
        }
    }
    return true;
}
```

### Separation of Duties (Generalized Constraint)

**Rule:** Person A does X, then Person B does Y (never the same person)

**Implementation:**

```
Define constraint: X → Y (different people)
    ↓
Before allocating Y, check who did X
    ↓
Exclude that person
    ↓
Allocate to someone else
```

---

## Workload Balancing

### Problem: Bursty Workflows

```
Monday morning: 100 cases arrive
    ↓
If allocated evenly: everyone gets 20 tasks
    ↓
But some finish in 2 hours, some in 8 hours
    ↓
Slow workers get stressed, fast workers bored
```

**Solution 1: Weighted Capacity**

```
Fast person: capacity = 20
Slow person: capacity = 10

Allocation: allocate to whoever has more free capacity (%)
```

**Solution 2: Skill-Based Routing**

```
Simple tasks → Fast people
Complex tasks → Skilled people (even if slower)
```

**Solution 3: Queuing Theory**

```
Monitor queue depth
    ↓
If queue > threshold: hire temp worker or delegate to vendor
```

---

## Escalation & Delegation

### Escalation Pattern

```
Task waits > 1 hour
    ↓
Route to manager
    ↓
Manager approves or adds resources
```

**Code:**

```java
@Scheduled(fixedRate = 60000)  // Every minute
public void checkAndEscalate() {
    List<WorkItem> stuck = db.findWorkItemsWaitingOver(1, TimeUnit.HOURS);

    for (WorkItem item : stuck) {
        Participant current = getAllocatedTo(item);
        Participant manager = getManager(current);

        reallocate(item, manager);
        notify(manager, "Task escalated: " + item.taskId());
    }
}
```

### Delegation Pattern

```
Alice: "This task should go to Bob"
    ↓
Update allocation in database
    ↓
Notify Bob
    ↓
Continue case
```

---

## Synchronization with Directory Services

### LDAP Sync

**Daily import (2 AM):**

```bash
ldapsearch -H ldap://ldap.company.com -b 'dc=company,dc=com' \
  '(objectClass=person)' \
  > /tmp/ldap-export.ldif

# Convert LDIF to JSON
convertLdifToJson.sh /tmp/ldap-export.ldif > org-model.json.new

# Validate
validateOrgModel.sh org-model.json.new || exit 1

# Swap
mv org-model.json.new org-model.json
```

**Advantages:**
- Single source of truth (LDAP)
- Automatic role changes
- Automatic terminations

**Disadvantages:**
- 24-hour delay (not real-time)
- LDAP failures block updates

### Event-Driven Sync

**Listen to LDAP change notifications:**

```java
ldapTemplate.subscribe("(objectClass=person)", event -> {
    if (event.type() == LdapEventType.USER_ADDED) {
        addParticipant(event.user());
    } else if (event.type() == LdapEventType.USER_DELETED) {
        deactivateParticipant(event.user());
    } else if (event.type() == LdapEventType.GROUP_CHANGED) {
        updateRoles(event.user());
    }
});
```

**Advantages:**
- Real-time updates
- No batch windows

**Disadvantages:**
- More complex infrastructure
- Requires LDAP server to support change notifications

---

## Performance Optimization

### Caching the Org Model

```
First call: load from database (100ms)
    ↓
Cache in Redis with TTL 60 min
    ↓
Subsequent calls: hit cache (5ms)
    ↓
After 60 min: reload from database
```

**Hit rate:** 90%+ typical

### Indexing for Fast Queries

```sql
-- Fast lookup by role
CREATE INDEX idx_participant_roles ON oc_participant_roles(role_id);

-- Fast lookup by capability
CREATE INDEX idx_participant_capabilities ON oc_participant_capabilities(capability);

-- Fast lookup by department
CREATE INDEX idx_participant_department ON oc_participants(department_id);
```

### Batch Operations

```java
// Bad: allocate 1000 tasks sequentially
for (WorkItem item : items) {
    Participant p = allocator.allocate(item.taskType(), candidates);
    allocate(item, p);  // Database write
}
// Total: 1000 × 10ms = 10 seconds

// Good: allocate all tasks in parallel
Map<String, Participant> allocations = items.parallelStream()
    .collect(Collectors.toMap(
        WorkItem::id,
        item -> allocator.allocate(item.taskType(), candidates)
    ));
// Total: 100ms (parallel)
```

---

## Trade-Offs

| Strategy | Fairness | Efficiency | Skill Match | Complexity |
|----------|----------|-----------|-------------|-----------|
| Round-Robin | ★★★★★ | ★☆☆ | ★☆☆ | ★☆☆ |
| Shortest Queue | ★★☆ | ★★★★★ | ★☆☆ | ★★☆ |
| Capability | ★★☆ | ★★☆ | ★★★★★ | ★★☆ |
| Weighted | ★★★ | ★★★ | ★★★ | ★★★★ |
| Hungarian | ★★★★★ | ★★★★★ | ★★★★ | ★★★★★ |

**Recommendation:**
1. Start with **Shortest Queue** (simple, good efficiency)
2. Add constraints (Four Eyes, Chinese Wall) as needed
3. Migrate to **Hungarian Algorithm** when cases > 1000/day (need optimal)

---

## See Also

- **Tutorial:** `docs/tutorials/resourcing-getting-started.md`
- **Reference:** `docs/reference/resourcing-api.md`
- **How-To:** `docs/how-to/resourcing-org-model.md`
