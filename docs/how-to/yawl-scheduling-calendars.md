# How-To: Configure Business Calendars

Set up calendars to make scheduled cases calendar-aware, skipping weekends and holidays.

## Prerequisites

- YAWL v6.0+ running
- Understanding of scheduling concepts
- Administrative access to calendar API

---

## Task 1: Create a Basic Business Calendar

### Define a 9-to-5 Weekday Calendar

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "calendarId": "standard-business-hours",
    "name": "Standard Business Hours (9-5)",
    "timezone": "UTC",
    "workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "workingHours": {
      "start": "09:00:00",
      "end": "17:00:00"
    },
    "description": "Standard 9-to-5 business hours, Monday-Friday"
  }'
```

### Response

```json
{
  "calendarId": "standard-business-hours",
  "status": "ACTIVE",
  "created": "2026-02-28T14:00:00Z"
}
```

---

## Task 2: Add Holidays to Calendar

Holidays are dates when the calendar is not working.

### Add US Federal Holidays

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/holidays \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "holidays": [
      {
        "date": "2026-01-01",
        "name": "New Years Day"
      },
      {
        "date": "2026-01-19",
        "name": "MLK Jr. Day"
      },
      {
        "date": "2026-02-16",
        "name": "Presidents Day"
      },
      {
        "date": "2026-05-25",
        "name": "Memorial Day"
      },
      {
        "date": "2026-07-04",
        "name": "Independence Day"
      },
      {
        "date": "2026-09-07",
        "name": "Labor Day"
      },
      {
        "date": "2026-11-26",
        "name": "Thanksgiving Day"
      },
      {
        "date": "2026-12-25",
        "name": "Christmas Day"
      }
    ]
  }'
```

### Add Company-Specific Holidays

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/holidays \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "holidays": [
      {
        "date": "2026-06-15",
        "name": "Company Anniversary"
      },
      {
        "date": "2026-08-05",
        "name": "Summer Shutdown Day 1"
      },
      {
        "date": "2026-08-06",
        "name": "Summer Shutdown Day 2"
      }
    ]
  }'
```

---

## Task 3: Create Shift-Based Calendars

For 24/7 operations with multiple shifts:

### Create 24/7 Shift Calendar

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "calendarId": "shift-24-7",
    "name": "24/7 Shift Operations",
    "timezone": "America/New_York",
    "workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
    "shifts": [
      {
        "shiftId": "night",
        "name": "Night Shift",
        "start": "00:00:00",
        "end": "08:00:00",
        "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
        "staffingLevel": 3
      },
      {
        "shiftId": "morning",
        "name": "Morning Shift",
        "start": "08:00:00",
        "end": "16:00:00",
        "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
        "staffingLevel": 5
      },
      {
        "shiftId": "evening",
        "name": "Evening Shift",
        "start": "16:00:00",
        "end": "00:00:00",
        "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
        "staffingLevel": 4
      }
    ]
  }'
```

---

## Task 4: Create Multi-Timezone Calendars

For global operations:

### Create Global Calendar with Timezone Support

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "calendarId": "global-operations",
    "name": "Global Operations",
    "timezone": "UTC",
    "workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "workingHours": {
      "start": "06:00:00",  // UTC
      "end": "18:00:00"
    },
    "regionalOverrides": [
      {
        "region": "EMEA",
        "timezone": "Europe/London",
        "workingHours": {
          "start": "08:00:00",
          "end": "17:00:00"
        }
      },
      {
        "region": "APAC",
        "timezone": "Asia/Tokyo",
        "workingHours": {
          "start": "09:00:00",
          "end": "18:00:00"
        }
      },
      {
        "region": "AMER",
        "timezone": "America/New_York",
        "workingHours": {
          "start": "09:00:00",
          "end": "17:00:00"
        }
      }
    ]
  }'
```

---

## Task 5: Query Calendar Working Hours

### Check if a Specific Date is a Working Day

```bash
curl -X GET 'http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/is-working-day?date=2026-03-01' \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Response:
# {
#   "date": "2026-03-01",
#   "isWorkingDay": true,
#   "dayOfWeek": "SUNDAY",
#   "reason": "Not a working day (Sunday)"
# }
```

### Calculate Next Working Day

```bash
curl -X GET 'http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/next-working-day?fromDate=2026-03-01' \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Response:
# {
#   "fromDate": "2026-03-01",
#   "nextWorkingDay": "2026-03-02"
# }
```

### Calculate Working Days Between Dates

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/working-days \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "startDate": "2026-03-01",
    "endDate": "2026-03-31"
  }'

# Response:
# {
#   "startDate": "2026-03-01",
#   "endDate": "2026-03-31",
#   "workingDaysCount": 21,
#   "weekendsCount": 8,
#   "holidaysCount": 1
# }
```

---

## Task 6: Use Calendar in Scheduled Cases

Reference the calendar when scheduling:

### Schedule with Calendar

```bash
curl -X POST http://localhost:8080/yawl/api/v1/cases/schedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "ApprovalProcess",
    "scheduledTime": "2026-03-01T09:00:00Z",
    "calendarId": "standard-business-hours",
    "caseData": {}
  }'
```

If 2026-03-01 is not a working day, YAWL automatically schedules for the next working day (2026-03-02).

### Schedule Recurring with Calendar

```bash
curl -X POST http://localhost:8080/yawl/api/v1/cases/schedule/recurring \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "DailyReportGen",
    "calendarId": "standard-business-hours",
    "schedule": {
      "pattern": "DAILY",
      "time": "09:00:00"
    },
    "caseData": {}
  }'
```

Result: Case runs every working day at 9:00 AM, skipping weekends and holidays.

---

## Task 7: Calculate Task Deadlines with Calendar

Set deadline durations that respect working hours:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "ApprovalWorkflow",
    "data": {},
    "deadlines": {
      "ReviewDocument": {
        "duration": "2 working days",  // 16 working hours
        "calendarId": "standard-business-hours",
        "alertBefore": "1 hour"
      },
      "FinalApproval": {
        "duration": "4 working hours",  // Half day
        "calendarId": "standard-business-hours",
        "alertBefore": "30 minutes"
      }
    }
  }'
```

---

## Task 8: View Calendar Details

### List All Calendars

```bash
curl -X GET http://localhost:8080/yawl/api/v1/calendars \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.calendars[] | {id: .calendarId, name: .name}'

# Output:
# {
#   "id": "standard-business-hours",
#   "name": "Standard Business Hours (9-5)"
# }
# {
#   "id": "shift-24-7",
#   "name": "24/7 Shift Operations"
# }
```

### Get Calendar Details

```bash
curl -X GET http://localhost:8080/yawl/api/v1/calendars/standard-business-hours \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

### Export Calendar as iCal

```bash
curl -X GET http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/export.ics \
  -H "Authorization: Bearer $ADMIN_TOKEN" > calendar.ics

# Use with Outlook, Google Calendar, Apple Calendar, etc.
```

---

## Task 9: Update Calendar at Runtime

### Modify Working Hours

```bash
curl -X PATCH http://localhost:8080/yawl/api/v1/calendars/standard-business-hours \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "workingHours": {
      "start": "08:30:00",
      "end": "17:30:00"
    }
  }'
```

### Add a Holiday

```bash
curl -X POST http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/holidays \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "holidays": [
      {
        "date": "2026-03-15",
        "name": "Special Company Event"
      }
    ]
  }'
```

### Remove a Holiday

```bash
curl -X DELETE http://localhost:8080/yawl/api/v1/calendars/standard-business-hours/holidays/2026-03-15 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Task 10: Production Calendar Setup

Complete example for a production environment:

```yaml
# calendars-config.yaml
calendars:
  - id: us-business
    name: "US Business Hours"
    timezone: America/New_York
    workingDays: [MON, TUE, WED, THU, FRI]
    workingHours:
      start: "09:00:00"
      end: "17:00:00"
    holidays:
      - { date: 2026-01-01, name: "New Years Day" }
      - { date: 2026-07-04, name: "Independence Day" }
      - { date: 2026-12-25, name: "Christmas" }

  - id: global-operations
    name: "Global 24/7"
    timezone: UTC
    workingDays: [MON, TUE, WED, THU, FRI, SAT, SUN]
    workingHours:
      start: "00:00:00"
      end: "23:59:59"

  - id: european-business
    name: "European Business Hours"
    timezone: Europe/London
    workingDays: [MON, TUE, WED, THU, FRI]
    workingHours:
      start: "08:30:00"
      end: "17:30:00"
    holidays:
      - { date: 2026-01-01, name: "New Years Day" }
      - { date: 2026-12-25, name: "Christmas Day" }
      - { date: 2026-12-26, name: "Boxing Day" }
```

Import calendars on startup:

```bash
#!/bin/bash

ADMIN_TOKEN="your-admin-token"
YAWL_URL="http://localhost:8080"

# Load calendars from YAML
yq eval '.calendars[]' calendars-config.yaml | \
  while read -r calendar_json; do
    curl -X POST $YAWL_URL/yawl/api/v1/calendars \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d "$calendar_json"
  done
```

---

## Troubleshooting

### Schedule Running on Wrong Day

**Issue:** Case scheduled on excluded day

**Solution:**
1. Verify calendar holidays: `GET /api/v1/calendars/{calendarId}/holidays`
2. Check date in question: `GET /api/v1/calendars/{calendarId}/is-working-day?date=...`
3. Confirm calendar assigned to schedule: check response from `/api/v1/cases/schedule/{scheduleId}`

### Deadlines Calculated Incorrectly

**Issue:** Deadline doesn't account for working hours

**Solution:**
1. Verify calendar working hours: `GET /api/v1/calendars/{calendarId}`
2. Check timezone matches your region
3. Ensure deadline duration specifies "working hours/days" not "hours/days"

### Calendar Changes Not Taking Effect

**Issue:** New holidays don't apply to pending schedules

**Solution:**
- Calendar changes only affect future schedules
- Pending schedules calculated at creation time
- Reschedule affected cases: `PATCH /api/v1/cases/schedule/{scheduleId}`

---

## What's Next?

- **[Scheduling Configuration Reference](../reference/yawl-scheduling-config.md)** — All options
- **[How-To: Set Task Deadlines](../how-to/yawl-scheduling-deadlines.md)** — Advanced deadline management
- **[Architecture: Scheduling Design](../explanation/yawl-scheduling-architecture.md)** — Calendar algorithm details

---

**Return to:** [Tutorial: Getting Started](../tutorials/yawl-scheduling-getting-started.md)
