# Getting Started with YAWL Scheduling

Learn how to schedule cases to run at specific times, manage recurring workflows, and integrate calendar awareness.

## What You'll Learn

In this tutorial, you'll:
1. Schedule a case to start at a specific time
2. Create recurring schedules (daily, weekly, monthly)
3. Query and manage scheduled cases
4. Apply calendar-aware scheduling (exclude weekends/holidays)
5. Monitor scheduled case execution

**Time to complete:** 25 minutes
**Prerequisites:** YAWL v6.0+ running, understanding of case specifications

---

## Step 1: Understanding the Scheduler

YAWL's scheduling service manages:
- **One-shot schedules:** Case starts once at a specified time
- **Recurring schedules:** Case repeats on a schedule (e.g., daily, weekly)
- **Calendar awareness:** Skip non-working days (weekends, holidays)
- **Deadlines:** Track task and case completion times

The scheduler runs on **virtual threads**, meaning thousands of scheduled cases can be tracked without consuming platform threads.

---

## Step 2: Schedule Your First Case

Use the REST API to schedule a case for a specific time:

### Using curl

```bash
# Schedule a case to start tomorrow at 9:00 AM
curl -X POST http://localhost:8080/yawl/api/v1/cases/schedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "Invoice2024",
    "scheduledTime": "2026-03-01T09:00:00Z",
    "caseData": {
      "invoiceAmount": 1500.00,
      "vendor": "Acme Corp"
    }
  }'
```

### Response

```json
{
  "scheduleId": "sched_12345",
  "specificationId": "Invoice2024",
  "status": "PENDING",
  "scheduledTime": "2026-03-01T09:00:00Z",
  "createdAt": "2026-02-28T14:00:00Z"
}
```

---

## Step 3: Create a Recurring Schedule

Set up a case to repeat daily, weekly, or monthly:

```bash
# Schedule a case to run every weekday at 8:00 AM
curl -X POST http://localhost:8080/yawl/api/v1/cases/schedule/recurring \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "DailyReportGen",
    "schedule": {
      "pattern": "DAILY",
      "time": "08:00:00",
      "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
    },
    "caseData": {
      "reportType": "daily_summary"
    },
    "startDate": "2026-03-01"
  }'
```

### Supported Patterns

| Pattern | Example | Description |
|---------|---------|-------------|
| ONCE | 2026-03-01T09:00:00Z | Single execution |
| DAILY | 08:00:00 | Every day at specified time |
| WEEKLY | MON,WED,FRI 10:00:00 | Specific days of week |
| MONTHLY | 1st 14:00:00 | Specific day of month |
| CRON | 0 0 * * MON | Full cron expression |

---

## Step 4: Apply Calendar-Aware Scheduling

Skip non-working days (weekends, holidays):

```bash
# Define a working calendar
curl -X POST http://localhost:8080/yawl/api/v1/calendars \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "calendarId": "us-business-hours",
    "name": "US Business Hours",
    "timezone": "America/New_York",
    "workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "workingHours": {
      "start": "09:00:00",
      "end": "17:00:00"
    },
    "holidays": [
      "2026-01-01",  // New Years Day
      "2026-07-04"   // Independence Day
    ]
  }'
```

### Use the Calendar in a Schedule

```bash
curl -X POST http://localhost:8080/yawl/api/v1/cases/schedule/recurring \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "ApprovalProcess",
    "calendarId": "us-business-hours",
    "schedule": {
      "pattern": "DAILY",
      "time": "10:00:00"
    },
    "caseData": {}
  }'
```

**Result:** If 2026-03-01 is a holiday, the case starts on the next working day.

---

## Step 5: Query Scheduled Cases

### List All Pending Schedules

```bash
curl -X GET http://localhost:8080/yawl/api/v1/cases/schedule \
  -H "Authorization: Bearer $TOKEN"
```

### Response

```json
{
  "schedules": [
    {
      "scheduleId": "sched_12345",
      "specificationId": "Invoice2024",
      "status": "PENDING",
      "scheduledTime": "2026-03-01T09:00:00Z",
      "createdAt": "2026-02-28T14:00:00Z"
    }
  ],
  "total": 1
}
```

### Filter by Status

```bash
# Get completed schedules
curl -X GET 'http://localhost:8080/yawl/api/v1/cases/schedule?status=COMPLETED' \
  -H "Authorization: Bearer $TOKEN"
```

### Get Details of a Single Schedule

```bash
curl -X GET http://localhost:8080/yawl/api/v1/cases/schedule/sched_12345 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Step 6: Cancel or Modify a Schedule

### Cancel a Pending Schedule

```bash
curl -X DELETE http://localhost:8080/yawl/api/v1/cases/schedule/sched_12345 \
  -H "Authorization: Bearer $TOKEN"
```

### Reschedule to a Different Time

```bash
curl -X PATCH http://localhost:8080/yawl/api/v1/cases/schedule/sched_12345 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "scheduledTime": "2026-03-02T14:00:00Z"
  }'
```

---

## Step 7: Monitor Case Deadlines

Set deadlines for task completion:

```bash
# Schedule a case with task deadlines
curl -X POST http://localhost:8080/yawl/api/v1/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "ProjectManagement",
    "data": {},
    "deadlines": {
      "ReviewProposal": {
        "duration": "2 hours",
        "alertBefore": "30 minutes"
      },
      "ApproveProject": {
        "duration": "1 day",
        "alertBefore": "2 hours"
      }
    }
  }'
```

---

## Complete Example: Daily Report Generation

Here's a real-world example of scheduling daily reports:

```java
import org.yawlfoundation.yawl.scheduling.*;
import java.time.*;
import java.util.*;

public class DailyReportScheduler {
    private WorkflowScheduler scheduler;

    public DailyReportScheduler(BiConsumer<String, String> onCaseLaunch) {
        this.scheduler = new WorkflowScheduler(onCaseLaunch);
    }

    public void scheduleDailyReport() {
        // Schedule report to run at 8:00 AM every weekday
        RecurringSchedule schedule = new RecurringSchedule()
            .pattern("DAILY")
            .time(LocalTime.of(8, 0, 0))
            .daysOfWeek(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
            .startDate(LocalDate.of(2026, 3, 1));

        ScheduledCase case1 = new ScheduledCase(
            "DailyReportSpec",
            schedule,
            "{\"reportType\": \"daily\"}"
        );

        scheduler.schedule(case1);
        System.out.println("Daily report scheduled!");
    }

    public List<ScheduledCase> listPending() {
        return scheduler.getPendingCases();
    }
}
```

---

## Troubleshooting

### Schedule not firing

**Possible causes:**
1. Scheduler not running — check logs for `WorkflowScheduler` startup
2. Scheduled time is in the past — reschedule to a future time
3. Calendar excludes the scheduled day — adjust calendar or schedule

**Solution:**
```bash
# Check scheduler status
curl http://localhost:8080/yawl/health | grep scheduler
```

### Calendar calculation incorrect

**Issue:** Case scheduled for non-working day despite calendar configuration

**Solution:** Verify calendar is assigned to the schedule:
```bash
curl http://localhost:8080/yawl/api/v1/cases/schedule/sched_12345 \
  -H "Authorization: Bearer $TOKEN" | grep calendarId
```

### Virtual thread resource exhaustion

**Error:** `Virtual thread limit exceeded`

**Solution:** YAWL's virtual thread executor creates threads on-demand. This error is rare. If it occurs:
1. Reduce number of concurrent schedules
2. Increase Java heap (`-Xmx2g`)
3. Check for leaked schedules (call `cancel()` when done)

---

## What's Next?

- **[Scheduling Configuration Reference](../reference/yawl-scheduling-config.md)** — All scheduler options
- **[How-To: Configure Business Calendars](../how-to/yawl-scheduling-calendars.md)** — Advanced calendar setup
- **[How-To: Set Task Deadlines](../how-to/yawl-scheduling-deadlines.md)** — Deadline management
- **[Architecture: Scheduling Design](../explanation/yawl-scheduling-architecture.md)** — Virtual thread model

---

## Quick Reference

| Task | Endpoint |
|------|----------|
| Schedule once | `POST /api/v1/cases/schedule` |
| Schedule recurring | `POST /api/v1/cases/schedule/recurring` |
| List schedules | `GET /api/v1/cases/schedule` |
| Cancel schedule | `DELETE /api/v1/cases/schedule/{scheduleId}` |
| Reschedule | `PATCH /api/v1/cases/schedule/{scheduleId}` |
| Create calendar | `POST /api/v1/calendars` |
| List calendars | `GET /api/v1/calendars` |

---

**Next:** [How-To: Configure Business Calendars](../how-to/yawl-scheduling-calendars.md)
