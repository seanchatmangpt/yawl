# YAWL Scheduling Architecture

Understanding virtual threads, calendar-aware scheduling, and deadline management.

---

## Core Design: Virtual Threads

YAWL's scheduler uses **virtual threads** (Project Loom, Java 21+) to manage thousands of pending schedules:

```
Traditional Threads:
  1 case scheduled = 1 platform thread consumed
  1000 cases scheduled = 1000 threads = memory exhaustion
  Total: 10 cases per server

Virtual Threads:
  1 case scheduled = negligible memory
  1,000,000 cases scheduled = 1 virtual thread pool
  Total: Millions of cases per server
```

### Scheduler Executor

```
WorkflowScheduler
├─ Timer: ScheduledExecutorService (1 platform thread)
│  └─ Fires schedule events on time
├─ Virtual Thread Pool: newVirtualThreadPerTaskExecutor()
│  └─ Launches case on virtual thread (zero overhead)
└─ Schedule Store
   ├─ Pending: ConcurrentHashMap
   ├─ Recurring: ConcurrentHashMap
   └─ History: Database (durable)
```

---

## Calendar-Aware Scheduling

### Workflow

```
Schedule Request:
  "Schedule case for 2026-03-01 at 09:00"
         ↓
Check Calendar:
  "Is 2026-03-01 a working day in this calendar?"
         ↓
Calendar Lookup:
  Working days: [MON, TUE, WED, THU, FRI]
  2026-03-01 is SUNDAY → Not working day
  Holiday check: Not in holidays
  Next working day: 2026-03-02
         ↓
Adjust Schedule:
  "Schedule case for 2026-03-02 at 09:00"
         ↓
Fire on Scheduled Time:
  Virtual thread launched at 2026-03-02 09:00 UTC
```

### Working Hours Calculation

For deadlines like "2 working days from now":

```
Start: 2026-02-28 14:00 (Friday, outside working hours)
Duration: 2 working days (16 hours)
Calendar: US business (9-5, Mon-Fri)

Calculation:
  Friday 14:00 → Skip to Monday 09:00 (after weekend)
  Monday 09:00-17:00 = 8 hours (day 1)
  Tuesday 09:00-17:00 = 8 hours (day 2)
  Total: Tuesday 17:00
```

---

## Recurring Schedule Algorithm

### Cron-Style Patterns

```yaml
pattern: CRON
expression: "0 9 * * MON-FRI"  # 9 AM, Monday-Friday
calendar: us-business
result: Every working day at 9 AM
```

### Overlap Handling

When a recurring schedule fires while previous instance is running:

```
Strategy: SKIP
  Case A fires at 09:00
    ↓ execution time = 2 hours
  Case A still running at 10:00
  Case B scheduled for 10:00 → SKIPPED
    ↓
  Case A completes at 11:00
  Case B fires at 11:00 (next scheduled time)

Strategy: QUEUE
  Case A fires at 09:00
    ↓ execution time = 2 hours
  Case B scheduled for 10:00 → QUEUED
    ↓
  Case A completes at 11:00
  Case B queued → LAUNCHES IMMEDIATELY
    ↓
  Case B fires at 11:00

Strategy: ERROR
  Case A fires at 09:00
    ↓ execution time = 2 hours
  Case B scheduled for 10:00 → ERROR
    Overlap detected, schedule marked FAILED
```

---

## Deadline Escalation

### Multi-Level Deadline

```
Case: Invoice Approval
├─ Task 1 (ReviewAndApprove): 2 working days deadline
│  ├─ Alert: At 1 working day remaining
│  ├─ Escalation: At deadline + 2 hours
│  └─ Auto-fail: At deadline + 1 day
│
└─ Task 2 (PayInvoice): 1 working day deadline
   └─ ...
```

### Alert Pipeline

```
T+0:00    Deadline set (2 working days = 2026-03-02 17:00)
          ↓
T+1:30    Alert triggered (80% of deadline)
          Email to approver
          ↓
T+1:59    Escalation alert (at deadline)
          Email to manager
          ↓
T+2:00    Escalation workflow triggered
          Auto-escalate to director
          ↓
T+2:30    Final deadline exceeded
          Case marked OVERDUE
          Workflow can cancel or continue
```

---

## Persistence & Recovery

### Durable Storage

All schedules persisted to database:

```sql
schedules (
  schedule_id,
  spec_id,
  status,              -- PENDING, EXECUTING, COMPLETED, FAILED
  scheduled_time,      -- When to fire
  created_at,
  completed_at,
  last_error
)

recurring_schedules (
  schedule_id,
  spec_id,
  pattern,             -- DAILY, WEEKLY, CRON, etc.
  next_fire_time,
  calendar_id,
  active,
  retention_days       -- How long to keep completed
)
```

### Startup Recovery

```
1. Load all PENDING and FAILED schedules from DB
2. For each, calculate time until scheduled execution
3. Register with virtual thread scheduler
4. Resume any interrupted recurring schedules
5. Log recovery statistics
```

---

## Integration with Engine

### Launch Callback

```java
WorkflowScheduler scheduler = new WorkflowScheduler((specId, caseData) -> {
    // This runs on a virtual thread
    YEngine engine = getYawlEngine();
    engine.launchCase(specId, parseData(caseData));
    // Virtual thread automatically cleaned up when method ends
});
```

Benefits:
- No platform thread consumed during case execution
- Platform thread reused for next scheduled event
- Scales to millions of pending cases

---

## Design Decisions

### Why Virtual Threads?

**Alternative 1: Fixed Thread Pool**
```
100-thread pool → schedules 100 concurrent cases → queue builds up
Cons: Blocked threads, memory waste
```

**Alternative 2: No Threads (Single-threaded)**
```
One thread fires all schedules
Cons: Only one case at a time (serialized)
```

**Choice: Virtual Threads**
```
On-demand threads, automatic cleanup → scales to millions
Pros: No pool sizing, automatic concurrency
```

### Why Calendar-Aware?

**Problem**: Deadlines calculated without business context
- "2 day deadline" at Friday 5 PM = Due Monday 5 PM
- But 1.75 days = outside business hours

**Solution**: Calendar marks working days/hours
- "2 working days" = 16 business hours
- Friday 5 PM → Monday 9 AM + 16 hours = Tuesday 5 PM

---

## Performance Characteristics

| Operation | Time | Notes |
|-----------|------|-------|
| Schedule single case | <1 ms | Async, returns immediately |
| Schedule 1000 cases | <1 second | Batch insert |
| Virtual thread creation | <100 µs | Negligible |
| Calendar check | <10 ms | Cached after first load |
| Fire scheduled case | <100 ms | Depends on engine speed |

---

## Related Architecture

- **[Authentication](yawl-authentication-architecture.md)** — User context for schedules
- **[Scheduling](yawl-scheduling-architecture.md)** ← (you are here)
- **[Monitoring](yawl-monitoring-architecture.md)** — Trace scheduled executions
- **[Worklets](yawl-worklet-architecture.md)** — Worklet for scheduled cases

---

## See Also

- [How-To: Configure Business Calendars](../how-to/yawl-scheduling-calendars.md)
- [Reference: Configuration Options](../reference/yawl-scheduling-config.md)
- [Tutorial: Getting Started](../tutorials/yawl-scheduling-getting-started.md)
