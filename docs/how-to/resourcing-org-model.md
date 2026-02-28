# How-To: Design and Implement Organizational Models

This guide covers designing, building, storing, and maintaining organizational models for YAWL work item allocation.

---

## Quick Setup (10 minutes)

For a simple 3-role organization with 5 participants:

```json
{
  "organisation": {
    "name": "MyOrg",
    "roles": [
      {"id": "reviewer", "name": "Reviewer"},
      {"id": "approver", "name": "Approver"},
      {"id": "shipper", "name": "Shipper"}
    ],
    "participants": [
      {
        "id": "p1",
        "name": "Alice",
        "roles": ["approver"],
        "capabilities": ["order_approval"],
        "capacity": 10
      },
      {
        "id": "p2",
        "name": "Bob",
        "roles": ["reviewer"],
        "capabilities": ["order_review"],
        "capacity": 15
      }
    ]
  }
}
```

Load it:

```java
Organization org = OrganizationModelLoader.loadFromJson("org-model.json");
WorkItemAllocator allocator = new WorkItemAllocator(rules, org.participants);
```

---

## Design Patterns

### Pattern 1: Flat Organization (Startup)

Minimal structure for early-stage workflows:

```json
{
  "roles": [
    {"id": "admin", "name": "Administrator"},
    {"id": "operator", "name": "Operator"}
  ],
  "participants": [
    {"id": "alice", "name": "Alice Chen", "roles": ["admin"]},
    {"id": "bob", "name": "Bob Martinez", "roles": ["operator"]},
    {"id": "carol", "name": "Carol Singh", "roles": ["operator"]}
  ]
}
```

**Use when:** < 10 people, <5 roles, all workflows are similar

### Pattern 2: Departmental Organization (Mid-Size Company)

Organize by business function:

```json
{
  "departments": [
    {
      "id": "finance",
      "name": "Finance",
      "manager": "alice",
      "roles": ["reviewer", "approver"]
    },
    {
      "id": "operations",
      "name": "Operations",
      "manager": "dave",
      "roles": ["shipper", "coordinator"]
    }
  ],
  "participants": [
    {
      "id": "alice",
      "name": "Alice Chen",
      "department": "finance",
      "roles": ["approver"],
      "manager": null
    },
    {
      "id": "bob",
      "name": "Bob Martinez",
      "department": "finance",
      "roles": ["reviewer"],
      "manager": "alice"
    }
  ]
}
```

**Use when:** 10-100 people, <10 departments, clear reporting structure

### Pattern 3: Matrix Organization (Enterprise)

Support multiple hierarchies (functional + project-based):

```json
{
  "organisations": {
    "functional": {
      "name": "Functional Org",
      "departments": [
        {
          "id": "finance",
          "name": "Finance",
          "members": ["alice", "bob"]
        }
      ]
    },
    "project": {
      "name": "Project Org",
      "projects": [
        {
          "id": "proj_x",
          "name": "Project X",
          "members": ["alice", "carol", "dave"]
        }
      ]
    }
  },
  "participants": [
    {
      "id": "alice",
      "name": "Alice Chen",
      "functional_role": "finance_approver",
      "project_assignments": ["proj_x"],
      "project_roles": {"proj_x": "project_lead"}
    }
  ]
}
```

**Use when:** >100 people, dotted-line reporting, complex resource allocation

---

## Storage Options

### Option 1: JSON File (Simple)

```bash
# Single file
org-model.json

# Directory structure (easier to version control)
org-model/
├── roles.json
├── departments.json
└── participants.json
```

Load with `OrganizationModelLoader.loadFromJson(path)`.

### Option 2: SQL Database (Scalable)

```sql
CREATE TABLE oc_roles (
    role_id VARCHAR(50) PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE oc_participants (
    participant_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    department_id VARCHAR(50),
    capacity INT DEFAULT 10,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oc_participant_roles (
    participant_id VARCHAR(50),
    role_id VARCHAR(50),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (participant_id, role_id),
    FOREIGN KEY (participant_id) REFERENCES oc_participants(participant_id),
    FOREIGN KEY (role_id) REFERENCES oc_roles(role_id)
);

CREATE TABLE oc_participant_capabilities (
    participant_id VARCHAR(50),
    capability VARCHAR(255),
    proficiency_level INT DEFAULT 1,  -- 1-5 scale
    PRIMARY KEY (participant_id, capability),
    FOREIGN KEY (participant_id) REFERENCES oc_participants(participant_id)
);
```

Load with:

```java
public class DatabaseOrgModelLoader {
    private final DataSource dataSource;

    public Organization load() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            Organization org = new Organization();

            // Load participants
            String sql = "SELECT participant_id, name, department_id, capacity FROM oc_participants WHERE active = true";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Participant p = new Participant();
                    p.id = rs.getString("participant_id");
                    p.name = rs.getString("name");
                    p.capacity = rs.getInt("capacity");

                    // Load roles
                    String rolesSql = "SELECT role_id FROM oc_participant_roles WHERE participant_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(rolesSql)) {
                        ps.setString(1, p.id);
                        try (ResultSet roleRs = ps.executeQuery()) {
                            while (roleRs.next()) {
                                p.roles.add(roleRs.getString("role_id"));
                            }
                        }
                    }

                    org.participants.add(p);
                }
            }

            return org;
        }
    }
}
```

### Option 3: LDAP/Active Directory (Enterprise)

```java
public class LdapOrgModelLoader {
    private final LdapTemplate ldapTemplate;

    public Organization loadFromLdap(String baseDn) throws Exception {
        Organization org = new Organization();

        // Search for all users
        List<LdapUser> users = ldapTemplate.search(
            "", "(objectClass=person)",
            new PersonContextMapper()
        );

        for (LdapUser user : users) {
            Participant p = new Participant();
            p.id = user.getUid();
            p.name = user.getCn();
            p.roles = extractRoles(user);
            p.capabilities = extractCapabilities(user);

            org.participants.add(p);
        }

        return org;
    }

    private List<String> extractRoles(LdapUser user) {
        // Extract from LDAP group membership
        return user.getGroups().stream()
            .map(g -> g.replace("cn=", "").split(",")[0])
            .toList();
    }

    private List<String> extractCapabilities(LdapUser user) {
        // Extract from LDAP attributes
        return user.getAttribute("capabilities");
    }
}
```

---

## Implementation Examples

### Example 1: Role-Based Allocation

Allocate tasks based on role membership:

```java
public class RoleBasedAllocator implements AllocationStrategy {
    private final Organization org;

    public Participant allocate(String taskId, String requiredRole) {
        // Find all participants with the required role
        List<Participant> qualified = org.participants.stream()
            .filter(p -> p.roles.contains(requiredRole))
            .filter(p -> p.currentWorkload < p.capacity)
            .sorted(Comparator.comparingInt(p -> p.currentWorkload))
            .toList();

        if (qualified.isEmpty()) {
            throw new NoAvailableResourceException(
                "No participant available with role: " + requiredRole);
        }

        return qualified.get(0);
    }
}
```

### Example 2: Capability-Based Allocation

Allocate tasks based on specific skills:

```java
public class CapabilityBasedAllocator implements AllocationStrategy {
    private final Organization org;
    private final int requiredProficiency;  // 1-5

    public Participant allocate(String taskId, String capability) {
        List<Participant> qualified = org.participants.stream()
            .filter(p -> hasProficiency(p, capability))
            .filter(p -> p.currentWorkload < p.capacity)
            .sorted(Comparator.comparingInt(p -> p.currentWorkload))
            .toList();

        if (qualified.isEmpty()) {
            // Fall back to any role that can do the task
            return allocateFallback(taskId, capability);
        }

        return qualified.get(0);
    }

    private boolean hasProficiency(Participant p, String capability) {
        return p.capabilities.stream()
            .filter(c -> c.name.equals(capability))
            .anyMatch(c -> c.proficiency >= requiredProficiency);
    }
}
```

### Example 3: Cost-Optimized Allocation

Minimize total cost of task allocation:

```java
public class CostOptimizedAllocator implements AllocationStrategy {
    private final Organization org;
    private final CostModel costModel;

    public Participant allocate(String taskId, List<Participant> candidates) {
        return candidates.stream()
            .min(Comparator.comparing(p -> {
                int baseCost = costModel.getHourlyRate(p);
                int workloadPenalty = p.currentWorkload * 10;
                int oversTimeCost = (p.currentWorkload > p.capacity) ? 1000 : 0;
                return baseCost + workloadPenalty + oversTimeCost;
            }))
            .orElseThrow(() -> new NoAvailableResourceException("No candidates"));
    }
}
```

---

## Sync Strategies

### Strategy 1: Batch Import (Daily)

```bash
#!/bin/bash
# Import org model from LDAP every night

LDAP_SEARCH="ldapsearch -H ldap://ldap.company.com -b 'dc=company,dc=com'"

$LDAP_SEARCH "(objectClass=person)" \
  | java -cp yawl.jar \
    org.yawlfoundation.yawl.resourcing.tools.LdapToJsonConverter \
    > /tmp/org-model.json.new

# Validate
java -cp yawl.jar \
  org.yawlfoundation.yawl.resourcing.tools.ValidateOrgModel \
  /tmp/org-model.json.new || exit 1

# Swap
mv /tmp/org-model.json.new /opt/yawl/org-model.json
```

Schedule with cron:

```cron
0 2 * * * /opt/yawl/scripts/sync-org-model.sh
```

### Strategy 2: Event-Driven (Real-Time)

Listen to LDAP directory change notifications:

```java
@Component
public class LdapDirectoryListener {
    private final OrganizationService orgService;

    @PostConstruct
    public void subscribeToChanges() {
        // Listen for LDAP user add/modify/delete events
        ldapTemplate.subscribe(
            "(objectClass=person)",
            this::handleLdapChange
        );
    }

    private void handleLdapChange(LdapEvent event) {
        switch (event.type()) {
            case ADD:
                orgService.addParticipant(event.getUser());
                break;
            case MODIFY:
                orgService.updateParticipant(event.getUser());
                break;
            case DELETE:
                orgService.deactivateParticipant(event.getUserId());
                break;
        }
    }
}
```

### Strategy 3: Hybrid (Eventual Consistency)

Combine batch + event-driven:

```yaml
synchronization:
  batch:
    enabled: true
    schedule: "0 2 * * *"  # 2 AM daily
    source: ldap
  event-driven:
    enabled: true
    source: ldap_directory_changes
  cache:
    ttl-minutes: 60
    backend: redis
  conflict-resolution: BATCH_WINS
```

---

## Constraint Enforcement

### Four-Eyes Principle

Prevent the same person from reviewing and approving:

```java
public class FourEyesPrinciple implements AllocationConstraint {

    @Override
    public boolean isSatisfied(String taskId, Participant candidate,
            AllocationHistory history) {

        // Get all participants who already worked on this case
        Set<String> previousWorkers = history.getParticipants();

        if (previousWorkers.contains(candidate.id)) {
            // Same person trying to work again
            return false;
        }

        return true;
    }
}
```

### Chinese Wall Principle

Prevent conflicts of interest:

```java
public class ChineseWallPrinciple implements AllocationConstraint {

    private final ConflictRegistry conflictRegistry;

    @Override
    public boolean isSatisfied(String taskId, Participant candidate,
            CaseContext context) {

        String caseClient = context.getAttribute("client");

        // Check if candidate has worked for competing client
        Set<String> candidateClients = candidateWorkHistory.getClientsWorkedFor(candidate.id);

        return !isCompetingClient(caseClient, candidateClients);
    }

    private boolean isCompetingClient(String client, Set<String> previousClients) {
        for (String prev : previousClients) {
            if (conflictRegistry.areCompetitors(client, prev)) {
                return true;
            }
        }
        return false;
    }
}
```

---

## Testing Your Org Model

```java
@SpringBootTest
public class OrganizationModelTest {

    @Test
    public void testModelLoads() throws Exception {
        Organization org = loadOrgModel("org-model.json");

        assertNotNull(org);
        assertEquals("ACME Corp", org.name);
        assertTrue(org.participants.size() > 0);
    }

    @Test
    public void testAllocationWorks() throws Exception {
        Organization org = loadOrgModel("org-model.json");
        WorkItemAllocator allocator = new WorkItemAllocator(rules, org.participants);

        Participant p = allocator.allocate("order_review", 0);

        assertNotNull(p);
        assertTrue(p.capabilities.contains("order_review"));
        assertTrue(p.capacity > 0);
    }

    @Test
    public void testNoGapsInHierarchy() throws Exception {
        Organization org = loadOrgModel("org-model.json");

        // Every manager should be a participant
        Set<String> allParticipants = org.participants.stream()
            .map(p -> p.id)
            .collect(Collectors.toSet());

        for (Participant p : org.participants) {
            if (p.manager != null) {
                assertTrue(allParticipants.contains(p.manager),
                    "Manager " + p.manager + " not found for " + p.id);
            }
        }
    }

    @Test
    public void testCapacityIsNonnegative() throws Exception {
        Organization org = loadOrgModel("org-model.json");

        for (Participant p : org.participants) {
            assertTrue(p.capacity >= 0,
                "Participant " + p.id + " has negative capacity");
        }
    }
}
```

---

## Performance Optimization

### Caching the Org Model

```java
@Component
public class CachedOrganizationRepository {
    private final DatabaseOrgModelLoader loader;
    private final Cache<String, Organization> cache;

    public Organization getOrgModel(String orgId) {
        return cache.get(orgId, key -> {
            try {
                return loader.load(orgId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void invalidateCache() {
        cache.invalidateAll();
    }
}

// Configure Caffeine cache
@Configuration
public class CacheConfiguration {
    @Bean
    public Cache<String, Organization> orgModelCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .maximumSize(100)
            .recordStats()
            .build();
    }
}
```

### Bulk Operations

```java
public class BulkAllocationService {

    public Map<String, Participant> allocateMany(List<WorkItem> items,
            OrganizationModel org) {

        // Pre-sort candidates once
        Map<String, List<Participant>> roleIndex = org.participants.stream()
            .collect(Collectors.groupingBy(p -> p.roles.get(0)));

        return items.parallelStream()
            .collect(Collectors.toMap(
                WorkItem::getId,
                item -> allocateToLightestLoaded(item, roleIndex)
            ));
    }
}
```

---

## Troubleshooting

### "No participant found for role X"
- Verify role exists in org model
- Check participant has the role assigned
- Ensure participant is active (not deactivated)

### "Capacity check failing"
- Check current workload tracking is accurate
- Verify capacity values are set (not null or negative)
- Consider increasing capacity or adding more participants

### "LDAP import has stale data"
- Increase sync frequency
- Verify LDAP connection string
- Check LDAP credentials

---

## See Also

- **Tutorial:** `docs/tutorials/resourcing-getting-started.md`
- **Reference:** `docs/reference/resourcing-api.md`
- **Explanation:** `docs/explanation/resource-allocation.md`
