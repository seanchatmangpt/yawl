# YAWL Scheduling Module

**Artifact:** `org.yawlfoundation:yawl-scheduling:6.0.0-Beta`

## Overview

The YAWL Scheduling Module provides comprehensive time-based workflow orchestration capabilities for YAWL v6.0.0. Built on Java 21+ virtual threads, this module enables sophisticated scheduling operations including delayed case launches, recurring workflow executions, timer-based task triggers, and calendar-aware scheduling.

## Core Components

### Constants
- **Purpose**: Configuration constants for scheduling intervals, timeouts, and defaults
- **Key Constants**:
  - Resource status management (unchecked, unknown, unavailable, available, requested, reserved)
  - Utilization types (plan, begin, end)
  - Address types (IP, email, SMS)
  - XML schema constants for scheduling operations
  - CSS class names for form styling

### Mapping
- **Purpose**: Data mapping utilities for converting between scheduling representations
- **Key Fields**:
  - `workItemId`: Identifier for scheduled work items
  - `requestKey`: Request identifier for tracking
  - `workItemStatus`: Current state (parent, checkout, cached, processing)
  - `isLocked`: Concurrency control flag

### SchedulingException
- **Purpose**: Domain-specific exception for scheduling failures
- **Features**: Contextual error information with support for root cause chaining

## Key Scheduling Capabilities

### 1. Time-Based Triggers
- **Delayed Case Launch**: Schedule cases to start at specific future timestamps
- **Recurring Execution**: Cron-like scheduling for periodic workflow runs
- **Timer Events**: Time-based triggers within workflow definitions
- **Timeout Handling**: Automatic escalation when tasks exceed time limits

### 2. Calendar Management
- **Business Hours**: Schedule based on working hours and shifts
- **Holiday Exclusions**: Automatically exclude public holidays from scheduling
- **Time Zone Support**: Full time zone awareness with proper date arithmetic
- **Duration Computation**: Calculate task durations considering working hours

### 3. Resource Utilization Scheduling
- **Resource Allocation**: Schedule resource utilization with start/end times
- **Conflict Detection**: Prevent overbooking of resources
- **Reservation Management**: Handle resource reservations with status tracking

### 4. Virtual Thread Architecture
- **Non-blocking timers**: Virtual threads for efficient timer management
- **Concurrent execution**: Thousands of scheduled tasks running concurrently
- **Efficient waiting**: Lightweight waiting for time-based triggers
- **Scalability**: No thread pool sizing needed

## Integration with YAWL Engine

### Basic Usage
```java
// Schedule a case for future execution
ScheduledCase future = scheduler.scheduleCase(
    specId,
    caseData,
    Instant.parse("2026-03-01T09:00:00Z")
);

// Schedule recurring execution (daily at 6 AM UTC)
RecurringSchedule daily = scheduler.scheduleRecurring(
    specId,
    caseData,
    CronExpression.dailyAt(6, 0)
);

// Query upcoming scheduled cases
List<ScheduledCase> upcoming = scheduler.getUpcoming(
    Instant.now(),
    Instant.now().plus(Duration.ofDays(7))
);
```

### Timer Integration
```java
// Set a timer for a work item
Timer timer = new Timer(
    "work-item-" + workItemId,
    Instant.now().plusSeconds(300), // 5 minutes
    () -> handleTimerExpired(workItemId)
);

// Cancel a timer before expiration
timer.cancel();
```

### Calendar Operations
```java
// Create a business calendar
BusinessCalendar calendar = new BusinessCalendar.Builder()
    .workingHours(9, 17) // 9 AM to 5 PM
    .timezone(ZoneId.of("America/New_York"))
    .addHoliday(2026, 1, 1) // New Year's Day
    .addHoliday(2026, 12, 25) // Christmas
    .build();

// Calculate due date considering working hours
Instant dueDate = calendar.calculateDueDate(
    Instant.now(),
    Duration.ofHours(8)
);
```

## Dependencies

### Internal Dependencies
- **yawl-engine**: Core engine APIs and timer hook points
- **yawl-elements**: YAWL specification elements

### External Dependencies
- **Java 21+**: Virtual thread support
- **log4j-api**: Logging framework
- **commons-lang3**: Utility libraries

## Configuration

### Virtual Thread Configuration
```properties
# Enable virtual threads for scheduling
java.util.concurrent.ForkJoinPool.common.parallelism=1000

# Virtual thread settings for timers
java.lang.VirtualThread.LAZY=true
```

### Calendar Configuration
```properties
# Default time zone for scheduling
yawlscheduling.timezone=UTC

# Default working hours (24-hour format)
yawlscheduling.workingHours=9,17

# Calendar file for holidays (optional)
yawlscheduling.holidayCalendar=/path/to/holidays.ics
```

## Performance Considerations

### Virtual Thread Benefits
- **Memory Efficiency**: Thousands of threads with minimal memory overhead
- **CPU Efficiency**: No kernel context switching
- **Scalability**: Handles massive numbers of concurrent timers

### Best Practices
- **Timer Cleanup**: Always cancel timers when cases are completed or cancelled
- **Calendar Caching**: Cache calendar data to avoid repeated holiday lookups
- **Batch Operations**: Use batch methods for multiple scheduling operations
- **Monitor Memory**: Watch for memory leaks in timer cleanup

## Security Considerations

### Timer Security
- Prevent timer attacks by limiting the number of pending timers
- Validate timer expiration times to prevent far-future scheduling
- Use secure random for timer ID generation

### Calendar Security
- Validate holiday calendar files to prevent malicious input
- Sanitize calendar data to prevent injection attacks

## Testing

The module includes test fixtures in `test/org/yawlfoundation/yawl/scheduling/`:

- **TestSchedulingService**: Unit tests for core scheduling operations
- **TestCalendarManager**: Tests for calendar management and working hour calculations

## Roadmap

### Immediate Enhancements
- iCal (RFC 5545) import for calendar definitions
- REST API for calendar management
- Enhanced time zone support

### Future Enhancements
- Quartz Scheduler integration for clustered environments
- Advanced cron expression support
- Performance monitoring and metrics
- Load balancing for scheduled tasks

## Troubleshooting

### Common Issues

**Timer Not Firing**
- Check if the timer was properly cancelled
- Verify the system clock is synchronized
- Ensure no virtual thread pool exhaustion

**Calendar Calculations Incorrect**
- Verify time zone settings
- Check holiday calendar configuration
- Validate working hour definitions

**Memory Issues**
- Monitor pending timer count
- Ensure proper timer cleanup in exception handlers
- Check for circular references in scheduling data