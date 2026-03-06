# YAWL Interfaces Decision Tree — Choose Your Integration Path

**Purpose**: Rapidly identify which YAWL interface (A, B, E, X) fits your use case.
**Last Updated**: 2026-03-06
**Audience**: Integrators, developers, operators

---

## 🎯 Quick Decision Tree

```
What do you need to do?
│
├─ Deploy/manage/design workflows?
│  └─→ Interface A (Design/Management)
│
├─ Execute cases, assign work, complete tasks?
│  └─→ Interface B (Case/Work Item Management)
│
├─ Monitor & integrate with external systems (MCP, agents, observability)?
│  └─→ Interface E (Events & External Integration)
│
├─ Custom extensions, advanced APIs?
│  └─→ Interface X (Extended Operations)
│
└─ Not sure? → See "Use Cases" table below
```

---

## 📋 Interface Comparison Matrix

| Interface | Purpose | Primary Use | Protocol | Auth | Complexity | Best For |
|-----------|---------|-------------|----------|------|------------|----------|
| **A** | Specification design & management | Upload/list/delete workflows | REST API + Java API | Basic/API Key | Low-Medium | DevOps, spec developers, CI/CD pipelines |
| **B** | Case & work item execution | Launch cases, assign work, complete tasks | REST API + Java API | Session-based | Low | Operators, task managers, case users |
| **E** | Event streams & external integration | Subscribe to case/work/exception events | Webhook/listener API | Basic/Session | Medium | Integrators, monitoring systems, autonomous agents |
| **X** | Advanced/custom operations | Deadlock detection, force termination, history queries | REST API | Admin-only | High | Power users, administrators, custom workflows |

---

## 🔍 Detailed Interface Profiles

### **Interface A: Specification Design & Management**

**What it does**: Manage workflow specifications (upload, version, deploy, retire)

**Key Operations**:
```java
// Upload a new specification
String specXML = "<specification>...</specification>";
String specID = interfaceA.uploadSpecification(specXML);

// List available specifications
Map<String, YSpecification> specs = interfaceA.getSpecifications();

// Get specification details
YSpecification spec = interfaceA.getSpecification(specID);

// Delete specification
boolean success = interfaceA.removeSpecification(specID);
```

**When to use**:
- ✅ Deploying new workflows to production
- ✅ Managing workflow versions
- ✅ CI/CD pipeline integration
- ✅ Workflow governance/approval

**When NOT to use**:
- ❌ Executing cases (use Interface B)
- ❌ Monitoring events (use Interface E)
- ❌ Custom extensions (use Interface X)

**REST Endpoints**: `/api/spec/*`
**Java Entry Point**: `InterfaceADesign`, `InterfaceAManagement`
**Authentication**: Basic auth or API key

**Example Integration**:
```bash
# Deploy workflow via REST
curl -X POST https://yawl.example.com/api/spec/upload \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d @workflow.xml

# Expected response: spec_id=abc123
```

---

### **Interface B: Case & Work Item Execution**

**What it does**: Execute cases, manage work items, assign tasks

**Key Operations**:
```java
// Launch a case
String caseID = interfaceB.launchCase(specID, initialData);

// Get enabled work items
List<YWorkItem> items = interfaceB.getEnabledWorkItems(caseID);

// Start work item (allocate to user)
boolean success = interfaceB.startWorkItem(workItem, userID);

// Complete work item
boolean success = interfaceB.completeWorkItem(workItem, outputData);

// Get case status
YNetRunner runner = interfaceB.getCaseRunner(caseID);
```

**When to use**:
- ✅ Launching new cases
- ✅ Querying work items
- ✅ Assigning tasks to users/resources
- ✅ Completing tasks
- ✅ Building user interfaces

**When NOT to use**:
- ❌ Uploading/managing specifications (use Interface A)
- ❌ Real-time event monitoring (use Interface E for events)
- ❌ Administrative operations (use Interface X)

**REST Endpoints**: `/api/case/*`, `/api/workitem/*`
**Java Entry Point**: `InterfaceB_EngineBasedServer`, `InterfaceB_EnvironmentBasedClient`
**Authentication**: Session-based (login required)

**Example Integration**:
```bash
# Launch case
curl -X POST https://yawl.example.com/api/case \
  -H "Authorization: Bearer SESSION_TOKEN" \
  -d '{"spec_id":"abc123", "initial_data":{"vendor_id":"V001"}}'

# Get enabled work items
curl https://yawl.example.com/api/case/abc-case-001/workitems \
  -H "Authorization: Bearer SESSION_TOKEN"
```

---

### **Interface E: Events & External Integration**

**What it does**: Stream events to external systems (MCP, autonomous agents, webhooks, monitoring)

**Event Types**:
- `CaseCreated` / `CaseCompleted` / `CaseSuspended` / `CaseTerminated`
- `WorkItemEnabled` / `WorkItemStarted` / `WorkItemCompleted` / `WorkItemFailed`
- `ExceptionThrown` / `ExceptionResolved`
- `LogEvent` (custom application events)
- `TimerFired` (scheduled task triggered)

**When to use**:
- ✅ Real-time monitoring & observability
- ✅ Integrating with autonomous agents (MCP, A2A)
- ✅ External system notifications (Slack, email, webhooks)
- ✅ Custom business logic (trigger workflows, update external systems)
- ✅ Audit logging and compliance

**When NOT to use**:
- ❌ Direct case/work item execution (use Interface B)
- ❌ Specification management (use Interface A)
- ❌ Pull-based querying of case status (use Interface B for polling)

**Implementation**:
```java
// Register event listeners
YStatelessEngine engine = new YStatelessEngine();
engine.addCaseEventListener(caseEvent -> {
    System.out.println("Case " + caseEvent.getCaseID() + " completed");
});

engine.addWorkItemEventListener(workItemEvent -> {
    System.out.println("Work item enabled: " + workItemEvent.getWorkItem().getID());
    // Trigger external agent, send Slack notification, etc.
});
```

**Example Integration**:
```java
// MCP Server (Claude AI integration)
YawlMcpSpringApplication.main(new String[]{});
// Exposes YAWL as tools/resources to Claude

// A2A Server (Autonomous Agent integration)
YawlA2AServer server = new YawlA2AServer();
server.registerAgent("fraud-detector", fraudDetectionService);
// Agents can invoke registered services
```

**REST Endpoints**: `/api/events/subscribe` (webhook registration)
**Java Entry Point**: `YWorkItemEventListener`, `YCaseEventListener`, `YExceptionEventListener`, `YLogEventListener`, `YTimerEventListener`

---

### **Interface X: Extended Operations**

**What it does**: Advanced administrative and diagnostic operations

**Key Operations**:
```java
// Force-terminate a case (destructive)
boolean success = interfaceX.forceCaseTermination(caseID);

// Detect deadlocks
List<DeadlockSituation> deadlocks = interfaceX.findDeadlocks();

// Get case history (detailed audit log)
List<YLogEvent> history = interfaceX.getCaseHistory(caseID);

// Custom queries (database-level access)
List<YCase> cases = interfaceX.queryCases("status='suspended'");
```

**When to use**:
- ✅ Administrative troubleshooting
- ✅ Emergency case termination
- ✅ Advanced querying and reporting
- ✅ Debugging deadlocks
- ✅ Audit/compliance queries

**When NOT to use**:
- ❌ Normal case execution (use Interface B)
- ❌ Regular monitoring (use Interface E)
- ❌ Workflow deployment (use Interface A)

**Authentication**: Admin-only (elevated privileges required)
**Risk Level**: HIGH — These operations are destructive or expose sensitive data

**Example Integration**:
```bash
# Find deadlocked cases (ADMIN ONLY)
curl -X GET https://yawl.example.com/api/admin/deadlocks \
  -H "Authorization: Bearer ADMIN_TOKEN"

# Force terminate case (ADMIN ONLY)
curl -X DELETE https://yawl.example.com/api/admin/case/abc-case-001 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

## 📊 Use Case → Interface Mapping

| Use Case | Interface(s) | Rationale |
|----------|---|---|
| **Deploy workflow to production** | A | Use A to upload spec before any cases run |
| **Build a task manager UI** | B | B provides case/work item queries and completion |
| **Real-time Slack notifications** | B + E | B to query status, E to listen for events |
| **Integrate with ChatGPT/Claude** | E (MCP) | MCP server exposes YAWL as tools to LLMs |
| **Autonomous agent (fraud checker)** | B + E | E to get triggered, B to complete tasks |
| **SLA monitoring dashboard** | B + E + X | B for case queries, E for events, X for advanced metrics |
| **Workflow audit trail** | B + X | B for status, X for detailed history |
| **CI/CD pipeline (auto-deploy workflows)** | A | A to upload specs as part of build |
| **Emergency case termination** | X | X provides admin-only force termination |
| **Deadlock debugging** | X | X provides deadlock detection |

---

## 🚀 Getting Started by Role

### **DevOps / Deployment Engineer**
1. Start with **Interface A** — deploy specs via REST API
2. Add **Interface X** — monitor/troubleshoot via admin endpoints
3. Example: `yawl deploy spec.xml --api-key=YOUR_KEY`

### **Application Developer**
1. Start with **Interface B** — execute cases, manage work items
2. Add **Interface E** — listen for events in your app
3. Example:
   ```java
   YStatelessEngine engine = new YStatelessEngine();
   engine.launchCase(specID, caseID, data);
   engine.addWorkItemEventListener(event -> handleWorkItem(event));
   ```

### **Integration Engineer (External Systems)**
1. Start with **Interface E** — subscribe to events
2. Add **Interface B** — query case/work item status as needed
3. Example: Register webhook to receive case events, trigger external service

### **AI/Agent Developer**
1. Use **Interface E (MCP)** — Register YAWL as Claude AI tools
2. Or **Interface E (A2A)** — Call registered agents from YAWL
3. Example: Claude agent reads case status, suggests actions

### **System Administrator**
1. Start with **Interface B** — normal operations
2. Add **Interface X** — troubleshooting and emergency operations
3. Know when to escalate to specialist teams

---

## 🔗 Protocol & Authentication Summary

| Interface | REST Path | Java API | Auth Method | Timeout |
|-----------|-----------|----------|-------------|---------|
| A | `/api/spec/*` | `InterfaceADesign`, `InterfaceAManagement` | Basic/API Key | 30s |
| B | `/api/case/*`, `/api/workitem/*` | `InterfaceB_*` | Session + JWT | 5min (per request) |
| E | `/api/events/*` | Listeners (Java objects) | Session-based | Real-time |
| X | `/api/admin/*` | `InterfaceXAdministration` | Admin token | 60s (destructive ops) |

---

## ⚠️ Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---|---|
| Use Interface B to deploy workflows | B only executes; A manages specs | Use A to upload spec first, then B to launch cases |
| Use Interface A to query work items | A doesn't know about running cases | Use B to query work items |
| Poll Interface B instead of using E | Polling misses events & wastes bandwidth | Register an Interface E listener |
| Expose Interface X to non-admins | X allows case termination & sensitive queries | Restrict X to admin endpoints only |
| Mix SOAP & REST calls | Different protocols, session mismatches | Pick one: prefer REST for new code |

---

## 🔄 Typical Integration Flow

```
┌─────────────────────────────────────────────────────┐
│ 1. Deploy Specification (Interface A)               │
│    DevOps uploads workflow spec via REST API        │
└─────────────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────────┐
│ 2. Launch & Execute Case (Interface B)              │
│    App calls launchCase(), assigns work items       │
└─────────────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────────┐
│ 3. Listen for Events (Interface E)                  │
│    MCP/Agent/Monitoring system subscribes           │
└─────────────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────────┐
│ 4. Troubleshoot if Needed (Interface X)             │
│    Admin uses deadlock detection, history queries   │
└─────────────────────────────────────────────────────┘
```

---

## 📚 Reference Links

- **Interface A Documentation**: See `yawl/engine/interfce/InterfaceA*.java`
- **Interface B Documentation**: See `yawl/engine/interfce/InterfaceB*.java`
- **Interface E Documentation**: See `yawl/engine/interfce/InterfaceE*.java`
- **Interface X Documentation**: See `yawl/engine/interfce/InterfaceX*.java`
- **REST API Docs**: See `yawl/engine/interfce/rest/` directory
- **Event Listener Examples**: See `yawl/observability/listeners/` directory

---

## ❓ FAQ

**Q: Can I use multiple interfaces in one integration?**
A: Yes, absolutely. Most real-world integrations use 2-3 interfaces. Example: B to manage cases + E to listen for events + X to troubleshoot.

**Q: Which interface is "best"?**
A: It depends on your use case. A for deployment, B for execution, E for events, X for admin. See "Use Case → Interface Mapping" above.

**Q: Are Interface A/B/E/X the same in stateful vs stateless deployments?**
A: Yes, the interface contracts are the same. YEngine (stateful) and YStatelessEngine (stateless) both implement the same interfaces.

**Q: Which interface is most performant?**
A: E (events) is push-based and lowest latency. B is request-response and sufficient for most workloads. A is design-time (not performance-critical). X is admin-only (latency acceptable).

---

**Document Version**: 1.0
**Compatible with**: YAWL v6.0.0+
**Feedback**: Open an issue on GitHub or contact the YAWL team
