# YAWL Scheduling Configuration Reference

Complete configuration options for case scheduling, recurring schedules, and calendar integration.

---

## Scheduler Configuration

### Core Settings

```yaml
yawl:
  scheduling:
    # Enable scheduling service
    # Default: true
    enabled: true

    # Thread pool configuration
    executor:
      # Core threads for scheduling
      # Default: 1 (single scheduler thread)
      core-threads: 1

      # Virtual thread per task executor
      # Default: true (use virtual threads for scheduled cases)
      use-virtual-threads: true

      # Queue capacity for pending schedules
      # Default: 10000
      queue-capacity: 10000

    # Timer configuration
    timer:
      # Timer thread name prefix
      # Default: "yawl-scheduler"
      thread-name: yawl-scheduler

      # Timer daemon thread
      # Default: true (doesn't prevent JVM shutdown)
      daemon: true

      # Time unit for scheduling
      # Options: SECONDS, MINUTES, HOURS, DAYS
      # Default: SECONDS
      time-unit: SECONDS

    # Schedule persistence
    persistence:
      # Store schedules in database
      # Default: true
      enabled: true

      # Retention period for completed schedules
      # Default: 30 (days)
      retention-days: 30

      # Archive completed schedules
      # Default: true
      archive-completed: true
```

---

## Case Scheduling

### One-Shot Schedules

```yaml
yawl:
  scheduling:
    schedules:
      # Minimum advance scheduling time
      # Default: 60 (seconds)
      min-advance-seconds: 60

      # Maximum advance scheduling time
      # Default: 2592000 (30 days)
      max-advance-seconds: 2592000

      # Default timezone
      # Default: UTC
      default-timezone: UTC

      # Reschedule failed cases
      reschedule-on-failure:
        # Enable automatic rescheduling
        # Default: true
        enabled: true

        # Retry attempts
        # Default: 3
        max-retries: 3

        # Backoff strategy
        # Options: linear, exponential
        # Default: exponential
        backoff-strategy: exponential

        # Backoff multiplier
        # Default: 2
        backoff-multiplier: 2

        # Initial backoff (seconds)
        # Default: 60
        initial-backoff-seconds: 60
```

### Recurring Schedules

```yaml
yawl:
  scheduling:
    recurring:
      # Maximum concurrent instances of same schedule
      # Default: 1 (no overlapping)
      max-concurrent-instances: 1

      # Overlap handling
      # Options: skip, queue, error
      # Default: skip
      overlap-strategy: skip

      # Schedule patterns
      patterns:
        # Days pattern (DAILY)
        daily:
          # Default time
          default-time: "09:00:00"

        # Weekly pattern (WEEKLY)
        weekly:
          # Default day
          default-day: "MONDAY"
          # Default time
          default-time: "09:00:00"

        # Monthly pattern (MONTHLY)
        monthly:
          # Default day of month
          default-day: 1
          # Default time
          default-time: "09:00:00"

        # Cron pattern (CRON)
        cron:
          # Enable cron patterns
          enabled: true
          # Timezone support
          timezone-aware: true
```

---

## Calendar Configuration

### Working Hours

```yaml
yawl:
  scheduling:
    calendars:
      # Default working hours
      default:
        # Default timezone
        timezone: UTC

        # Working days
        working-days:
          - MONDAY
          - TUESDAY
          - WEDNESDAY
          - THURSDAY
          - FRIDAY

        # Working hours (24-hour format)
        working-hours:
          start: "09:00:00"
          end: "17:00:00"

      # Adjust scheduled time to next working day
      # Default: true
      align-to-working-hours: true

      # Adjust scheduled time to working hours
      # Default: true
      adjust-to-business-hours: true

    # Holiday management
    holidays:
      # Enable holiday exclusion
      # Default: true
      enabled: true

      # Holidays source
      # Options: builtin, custom, both
      # Default: both
      source: both

      # Built-in holiday calendars
      builtin:
        # US Federal holidays
        us-federal: true

        # UK Bank holidays
        uk-holidays: false

        # EU holidays
        eu-holidays: false

      # Custom holidays source
      custom:
        # Type: database, file, api
        type: database

        # Query or path for holidays
        query: "SELECT holiday_date FROM holidays WHERE country = ?"
```

---

## Deadline Configuration

### Task Deadlines

```yaml
yawl:
  scheduling:
    deadlines:
      # Enable deadline tracking
      # Default: true
      enabled: true

      # Calendar-aware deadline calculation
      # Default: true
      calendar-aware: true

      # Alert configuration
      alerts:
        # Send alert before deadline
        # Default: true
        enabled: true

        # Alert channels
        channels:
          - email
          - webhook
          - database

        # Alert recipients
        recipients:
          - task-owner
          - case-creator
          - supervisor

      # Escalation on missed deadline
      escalation:
        # Enable escalation
        # Default: true
        enabled: true

        # Escalation recipients
        recipients:
          - manager
          - director

        # Escalation event
        # Default: DEADLINE_MISSED
        event: DEADLINE_MISSED

    # Case-level deadlines
    case-deadlines:
      # Overall case deadline
      overall:
        # Default case timeout
        # Default: null (no timeout)
        default-timeout: null

        # Cancel case on timeout
        # Default: false
        auto-cancel: false

      # Warning at percentage of deadline
      # Default: 80
      warning-percentage: 80
```

---

## Virtual Thread Configuration

### Execution Model

```yaml
yawl:
  scheduling:
    virtual-threads:
      # Use virtual threads for schedule execution
      # Default: true (Java 21+)
      enabled: true

      # Thread naming pattern
      # Variables: {specId}, {caseId}, {scheduleId}
      # Default: "case-{caseId}"
      thread-name-pattern: "case-{caseId}"

      # Virtual thread monitoring
      monitoring:
        # Track virtual thread creation
        # Default: false
        enabled: false

        # Log virtual thread lifecycle
        # Default: false
        log-lifecycle: false

        # Track thread pool size
        # Default: false
        track-pool-size: false
```

---

## API Endpoints

### Schedule Management

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/cases/schedule` | POST | Schedule a case (one-shot) |
| `/api/v1/cases/schedule/recurring` | POST | Create recurring schedule |
| `/api/v1/cases/schedule` | GET | List schedules |
| `/api/v1/cases/schedule/{id}` | GET | Get schedule details |
| `/api/v1/cases/schedule/{id}` | PATCH | Reschedule |
| `/api/v1/cases/schedule/{id}` | DELETE | Cancel schedule |

### Calendar Management

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/calendars` | POST | Create calendar |
| `/api/v1/calendars` | GET | List calendars |
| `/api/v1/calendars/{id}` | GET | Get calendar |
| `/api/v1/calendars/{id}` | PATCH | Update calendar |
| `/api/v1/calendars/{id}` | DELETE | Delete calendar |
| `/api/v1/calendars/{id}/holidays` | GET | Get holidays |
| `/api/v1/calendars/{id}/holidays` | POST | Add holidays |
| `/api/v1/calendars/{id}/holidays/{date}` | DELETE | Remove holiday |
| `/api/v1/calendars/{id}/is-working-day` | GET | Check working day |
| `/api/v1/calendars/{id}/next-working-day` | GET | Get next working day |
| `/api/v1/calendars/{id}/working-days` | POST | Count working days |

---

## Complete Example

```yaml
# application-scheduling.yaml

yawl:
  scheduling:
    enabled: true

    executor:
      core-threads: 1
      use-virtual-threads: true
      queue-capacity: 10000

    schedules:
      min-advance-seconds: 60
      max-advance-seconds: 2592000
      default-timezone: UTC
      reschedule-on-failure:
        enabled: true
        max-retries: 3
        backoff-strategy: exponential
        backoff-multiplier: 2
        initial-backoff-seconds: 60

    recurring:
      max-concurrent-instances: 1
      overlap-strategy: skip

    calendars:
      default:
        timezone: America/New_York
        working-days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
        working-hours:
          start: "09:00:00"
          end: "17:00:00"
      align-to-working-hours: true

    holidays:
      enabled: true
      source: both
      builtin:
        us-federal: true
        uk-holidays: false

    deadlines:
      enabled: true
      calendar-aware: true
      alerts:
        enabled: true
        channels: [email, webhook, database]
      escalation:
        enabled: true

    virtual-threads:
      enabled: true
      thread-name-pattern: "case-{caseId}"
```

---

## Common Patterns

### US Business Hours (9-5, Monday-Friday)

```yaml
yawl:
  scheduling:
    calendars:
      default:
        timezone: America/New_York
        working-days: [MON, TUE, WED, THU, FRI]
        working-hours:
          start: "09:00:00"
          end: "17:00:00"

    holidays:
      enabled: true
      builtin:
        us-federal: true
```

### 24/7 Operations (All Days, All Hours)

```yaml
yawl:
  scheduling:
    calendars:
      default:
        timezone: UTC
        working-days: [MON, TUE, WED, THU, FRI, SAT, SUN]
        working-hours:
          start: "00:00:00"
          end: "23:59:59"

    holidays:
      enabled: false
```

### European Business Hours (8:30-17:30, Monday-Friday)

```yaml
yawl:
  scheduling:
    calendars:
      default:
        timezone: Europe/London
        working-days: [MON, TUE, WED, THU, FRI]
        working-hours:
          start: "08:30:00"
          end: "17:30:00"

    holidays:
      enabled: true
      builtin:
        uk-holidays: true
```

---

## Troubleshooting

### Schedule Not Firing

1. Verify scheduler is enabled: `yawl.scheduling.enabled: true`
2. Check scheduled time is in the future
3. Verify calendar (if used) includes the date
4. Check logs for errors: `grep -i scheduler yawl.log`

### Incorrect Deadline Calculation

1. Verify calendar working hours are correct
2. Check timezone matches your region
3. Ensure deadline duration specifies "working" hours/days

### Virtual Thread Exhaustion

1. Reduce number of concurrent schedules
2. Increase Java heap size
3. Ensure schedules are being completed

---

## See Also

- [How-To: Configure Business Calendars](../how-to/yawl-scheduling-calendars.md)
- [Tutorial: Getting Started](../tutorials/yawl-scheduling-getting-started.md)
- [Architecture: Scheduling Design](../explanation/yawl-scheduling-architecture.md)
