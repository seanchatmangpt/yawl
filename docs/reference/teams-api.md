# YAWL Teams API Reference

**Version**: 6.0.0 SPR | **Updated**: 2026-02-28 | **Type**: Pure Reference (Diataxis)

Complete API reference for YAWL Teams (τ) architecture—multi-agent orchestration for collaborative parallel work across orthogonal quantums.

---

## Table of Contents

1. [Team Lifecycle](#team-lifecycle)
2. [Core API Methods](#core-api-methods)
3. [Teammate Management](#teammate-management)
4. [Message Protocol](#message-protocol)
5. [Checkpoint & Resume](#checkpoint--resume)
6. [Configuration](#configuration)
7. [Exit Codes & Error Handling](#exit-codes--error-handling)
8. [Code Examples](#code-examples)

---

## Team Lifecycle

### State Diagram

```
[INIT] → [PENDING] → [ACTIVE]
    ↓           ↓         ↓
  spawn    all online  lead online?
            ↓           ↓
         [CONSOLIDATING] → [COMPLETE]
            ↓
        [FAILED] ← error/timeout
```

### Lifecycle Flow

| Phase | Duration | Status | Action |
|-------|----------|--------|--------|
| **INIT** | 0-2s | Team metadata created, teammates allocated | Create team instance, validate formation checklist |
| **PENDING** | 2-5s | Teammates allocated but not online | Spawn teammates, establish heartbeats |
| **ACTIVE** | Variable | All teammates online, executing tasks | Message passing, task execution, checkpoint |
| **CONSOLIDATING** | Variable | Lead running final build & validation | Compile, run tests, create single commit |
| **COMPLETE** | 0-1s | All tasks done, git push successful | Return success, cleanup resources |
| **FAILED** | 0-10s | Unrecoverable error during execution | Rollback, save state for recovery, exit 2 |

---

## Core API Methods

### YawlTeams Interface

**Package**: `org.yawlfoundation.yawl.teams`

Core interface for team orchestration and lifecycle management.

```java
public interface YawlTeams {

    /**
     * Create a new team with the specified quantums and task assignments.
     *
     * @param teamSpec Team specification containing quantums and task metadata
     * @return Team instance with ID and initial state
     * @throws IllegalArgumentException if teamSpec is invalid
     * @throws IllegalStateException if team feature not enabled
     */
    Team createTeam(TeamSpecification teamSpec)
        throws IllegalArgumentException, IllegalStateException;

    /**
     * Retrieve an existing team by ID for resumption or status check.
     *
     * @param teamId Unique team identifier (format: τ-quantum1+quantum2-sessionId)
     * @return Team instance or empty Optional if not found
     */
    Optional<Team> getTeamById(String teamId);

    /**
     * List all active teams in current session.
     *
     * @return List of Team instances with status ACTIVE, CONSOLIDATING, or PENDING
     */
    List<Team> listActiveTeams();

    /**
     * Resume a previously suspended team (e.g., after lead timeout).
     *
     * @param teamId Team ID to resume
     * @param resumeContext Additional context (checkpoint data, last messages)
     * @return Resumed Team instance
     * @throws TeamNotFound if team ID doesn't exist
     * @throws StateException if team not in resumable state
     */
    Team resumeTeam(String teamId, ResumeContext resumeContext)
        throws TeamNotFound, StateException;

    /**
     * Probe a team's liveness without resuming it.
     *
     * @param teamId Team ID to check
     * @return ProbeResult with status, teammate heartbeats, mailbox size
     */
    ProbeResult probeTeam(String teamId);
}
```

### Team Interface

**Package**: `org.yawlfoundation.yawl.teams`

Core team instance API for execution and monitoring.

```java
public interface Team {

    /**
     * Get team's unique identifier.
     *
     * @return Team ID (format: τ-quantum1+quantum2+...-sessionId)
     */
    String getTeamId();

    /**
     * Get current team status (INIT, PENDING, ACTIVE, CONSOLIDATING, COMPLETE, FAILED).
     *
     * @return Current status
     */
    TeamStatus getStatus();

    /**
     * Get the number of teammates in this team.
     *
     * @return Teammate count (range: 2-5)
     */
    int getTeammateCount();

    /**
     * Get list of assigned teammates with their identities and status.
     *
     * @return List of Teammate instances
     */
    List<Teammate> getTeammates();

    /**
     * Get the list of tasks to be executed by teammates.
     *
     * @return TaskList with pending, in_progress, and completed tasks
     */
    TaskList getTasks();

    /**
     * Assign a task to a specific teammate.
     *
     * @param task TeammateTask to assign
     * @param teammate Teammate to assign it to
     * @return Updated TeammateTask with assignment confirmation
     * @throws InvalidStateException if team not in ACTIVE state
     */
    TeammateTask assignTask(TeammateTask task, Teammate teammate)
        throws InvalidStateException;

    /**
     * Send a message to one or more teammates.
     *
     * @param message Message to send (includes recipient, payload, timeout)
     * @return MessageReceipt with sequence number and delivery status
     * @throws MessagingException if message queue full or timeout
     */
    MessageReceipt sendMessage(TeamMessage message)
        throws MessagingException;

    /**
     * Receive messages intended for the lead from teammates.
     *
     * @param limit Maximum number of messages to retrieve
     * @return List of messages (FIFO order, sequence number ordered)
     */
    List<TeamMessage> receiveMessages(int limit);

    /**
     * Checkpoint team state (triggers auto-save to .team-state/).
     *
     * @param label Checkpoint label for clarity (e.g., "before-consolidation")
     * @return Checkpoint metadata (timestamp, size, hash)
     * @throws IOException if checkpoint write fails
     */
    CheckpointMetadata checkpoint(String label) throws IOException;

    /**
     * Start consolidation phase: lead performs final compile, test, validation.
     *
     * @param options Consolidation options (parallel test, fail-fast, etc.)
     * @return ConsolidationResult with build status and artifact locations
     * @throws ConsolidationException on build/test failures
     */
    ConsolidationResult consolidate(ConsolidationOptions options)
        throws ConsolidationException;

    /**
     * Mark team as complete and return exit code.
     *
     * @return Exit code (0 = success, 1 = transient error, 2 = fatal error)
     */
    int complete();

    /**
     * Get team metrics (messages sent/received, task completion time, etc.).
     *
     * @return TeamMetrics with utilization, latency, and cost data
     */
    TeamMetrics getMetrics();
}
```

---

## Teammate Management

### Teammate Interface

**Package**: `org.yawlfoundation.yawl.teams`

Represents a single collaborating agent in the team.

```java
public interface Teammate {

    /**
     * Get teammate's unique identifier within the team.
     *
     * @return Teammate ID (format: tm_1, tm_2, etc.)
     */
    String getTeammateId();

    /**
     * Get teammate's assigned quantum (e.g., "engine", "schema", "integration").
     *
     * @return Quantum assignment
     */
    String getQuantum();

    /**
     * Get current status (IDLE, WORKING, BLOCKED, DONE, CRASHED).
     *
     * @return TeammateStatus
     */
    TeammateStatus getStatus();

    /**
     * Get timestamp of last heartbeat. Compare to current time to detect stale teammates.
     *
     * @return Instant of last heartbeat (UTC)
     */
    Instant getLastHeartbeat();

    /**
     * Check if teammate is considered "online" (heartbeat < 10 min old).
     *
     * @return true if heartbeat recent
     */
    boolean isOnline();

    /**
     * Get the task currently assigned to this teammate.
     *
     * @return Current TeammateTask or empty Optional if idle
     */
    Optional<TeammateTask> getCurrentTask();

    /**
     * Send a direct message to this teammate.
     *
     * @param payload Task description, question, or directive
     * @param timeoutSeconds Timeout waiting for acknowledgment
     * @return MessageReceipt with ACK status
     */
    MessageReceipt sendMessage(String payload, int timeoutSeconds);

    /**
     * Get all messages received from this teammate (LIFO by sequence).
     *
     * @return List of messages
     */
    List<TeamMessage> getIncomingMessages();

    /**
     * Get status including current task and any blockers.
     *
     * @return TeammateStatusDetail with full context
     */
    TeammateStatusDetail getStatusDetail();
}
```

### TeammateTask Interface

**Package**: `org.yawlfoundation.yawl.teams`

Atomic work unit assigned to a single teammate.

```java
public interface TeammateTask {

    /**
     * Get task's unique identifier within the team.
     *
     * @return Task ID (format: task_1, task_2, etc.)
     */
    String getTaskId();

    /**
     * Get human-readable task description (work assignment details).
     *
     * @return Task description
     */
    String getDescription();

    /**
     * Get the quantum this task belongs to (e.g., "schema", "test").
     *
     * @return Quantum name
     */
    String getQuantum();

    /**
     * Get current status (PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED).
     *
     * @return TaskStatus
     */
    TaskStatus getStatus();

    /**
     * Get the teammate this task is assigned to.
     *
     * @return Assigned Teammate or empty Optional if unassigned
     */
    Optional<Teammate> getAssignedTeammate();

    /**
     * Mark task as in progress (called by teammate when starting work).
     *
     * @throws InvalidStateException if task not ASSIGNED
     */
    void markInProgress() throws InvalidStateException;

    /**
     * Mark task as completed with result summary.
     *
     * @param result Summary of work completed (files modified, tests passed, etc.)
     * @throws InvalidStateException if task not IN_PROGRESS
     */
    void markCompleted(TaskResult result) throws InvalidStateException;

    /**
     * Mark task as failed with error details.
     *
     * @param error Error summary and recovery guidance
     * @throws InvalidStateException if task not IN_PROGRESS
     */
    void markFailed(TaskError error) throws InvalidStateException;

    /**
     * Get execution metrics (time spent, checkpoints, messages exchanged).
     *
     * @return TaskMetrics
     */
    TaskMetrics getMetrics();

    /**
     * Get task dependencies (other tasks that must complete first).
     *
     * @return List of prerequisite task IDs
     */
    List<String> getDependencies();

    /**
     * Check if all dependencies are satisfied.
     *
     * @return true if ready to execute
     */
    boolean areDependenciesMet();
}
```

---

## Message Protocol

### TeamMessage Interface

**Package**: `org.yawlfoundation.yawl.teams.messaging`

Structured message between lead and teammate, or inter-teammate relay.

```java
public interface TeamMessage {

    /**
     * Get unique sequence number (monotonically increasing per source).
     *
     * @return Sequence number (ensure FIFO ordering)
     */
    long getSequenceNumber();

    /**
     * Get message send timestamp (UTC, canonical for comparison).
     *
     * @return Instant of message creation
     */
    Instant getTimestamp();

    /**
     * Get sender identifier (format: "lead" or "tm_N").
     *
     * @return Sender ID
     */
    String getFrom();

    /**
     * Get recipient identifier (recipient ID or "all" for broadcast).
     *
     * @return Recipient ID
     */
    String getTo();

    /**
     * Get message payload (task description, question, status update, etc.).
     *
     * @return Payload as String
     */
    String getPayload();

    /**
     * Get timeout duration for awaiting response.
     *
     * @return Timeout as Duration (e.g., PT15M for 15 minutes)
     */
    Duration getTimeout();

    /**
     * Get message classification (TASK, QUERY, FEEDBACK, STATUS).
     *
     * @return MessageType
     */
    MessageType getMessageType();

    /**
     * Get delivery status (QUEUED, SENT, DELIVERED, FAILED, TIMEOUT).
     *
     * @return DeliveryStatus
     */
    DeliveryStatus getDeliveryStatus();

    /**
     * Get acknowledgment from recipient (if applicable).
     *
     * @return ACK message or empty Optional if no ACK yet
     */
    Optional<String> getAcknowledgment();

    /**
     * Check if message has been acknowledged.
     *
     * @return true if ACK received
     */
    boolean isAcknowledged();

    /**
     * Get time since message was sent (for timeout detection).
     *
     * @return Duration elapsed since send
     */
    Duration getElapsedTime();

    /**
     * Check if message has exceeded timeout.
     *
     * @return true if elapsed >= timeout
     */
    boolean isExpired();
}
```

### MessageReceipt Class

**Package**: `org.yawlfoundation.yawl.teams.messaging`

Confirmation that a message was sent and acknowledged.

```java
public class MessageReceipt {

    private final long sequenceNumber;
    private final String messageId;
    private final DeliveryStatus status;
    private final Instant sentAt;
    private final Instant acknowledgedAt;
    private final String recipientId;
    private final Optional<String> acknowledgmentContent;

    /**
     * Create receipt for sent message.
     */
    public MessageReceipt(long seq, String msgId, DeliveryStatus status,
                         Instant sent, String recipient) {
        this.sequenceNumber = seq;
        this.messageId = msgId;
        this.status = status;
        this.sentAt = sent;
        this.recipientId = recipient;
        this.acknowledgedAt = null;
        this.acknowledgmentContent = Optional.empty();
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public String getMessageId() { return messageId; }
    public DeliveryStatus getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
    public String getRecipientId() { return recipientId; }
    public boolean wasAcknowledged() { return acknowledgedAt != null; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Optional<String> getAcknowledgmentContent() { return acknowledgmentContent; }
}
```

### Message Delivery Guarantees

| Property | Guarantee | Implementation |
|----------|-----------|-----------------|
| **Ordering** | FIFO per teammate | Sequence numbers ensure delivery order |
| **Idempotency** | Message-level | Sequence + timestamp prevents re-execution |
| **Reliability** | At-least-once | Resend after 30s no ACK, max 3 retries |
| **Timeout** | 15 min critical, 30 min informational | Automatic escalation or default handling |
| **Deduplication** | Automatic via sequence | Duplicate detection before processing |

---

## Checkpoint & Resume

### CheckpointMetadata Class

**Package**: `org.yawlfoundation.yawl.teams.checkpoint`

Metadata about a saved team state snapshot.

```java
public class CheckpointMetadata {

    private final String checkpointId;
    private final String label;
    private final Instant createdAt;
    private final long sizeBytes;
    private final String contentHash;  // blake3(canonical_json)
    private final TeamStatus stateAtCheckpoint;
    private final int taskCount;
    private final int messageCount;

    public String getCheckpointId() { return checkpointId; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentHash() { return contentHash; }
    public TeamStatus getStateAtCheckpoint() { return stateAtCheckpoint; }
    public int getTaskCount() { return taskCount; }
    public int getMessageCount() { return messageCount; }

    /**
     * Check if checkpoint is stale (older than 1 hour).
     */
    public boolean isStale() {
        return Instant.now().minus(Duration.ofHours(1)).isAfter(createdAt);
    }
}
```

### ResumeContext Class

**Package**: `org.yawlfoundation.yawl.teams.checkpoint`

Context required to resume a team after interruption.

```java
public class ResumeContext {

    private final String teamId;
    private final String checkpointId;
    private final TeamStatus lastKnownStatus;
    private final List<TeamMessage> pendingMessages;
    private final Map<String, TaskStatus> taskStatuses;
    private final Instant lastActivityAt;

    public ResumeContext(String teamId, String checkpointId,
                        TeamStatus status, List<TeamMessage> messages,
                        Map<String, TaskStatus> tasks, Instant lastActivity) {
        this.teamId = teamId;
        this.checkpointId = checkpointId;
        this.lastKnownStatus = status;
        this.pendingMessages = messages;
        this.taskStatuses = tasks;
        this.lastActivityAt = lastActivity;
    }

    public String getTeamId() { return teamId; }
    public String getCheckpointId() { return checkpointId; }
    public TeamStatus getLastKnownStatus() { return lastKnownStatus; }
    public List<TeamMessage> getPendingMessages() { return pendingMessages; }
    public Map<String, TaskStatus> getTaskStatuses() { return taskStatuses; }
    public Instant getLastActivityAt() { return lastActivityAt; }

    /**
     * Calculate how long the team was inactive (for recovery decisions).
     */
    public Duration getInactivityDuration() {
        return Duration.between(lastActivityAt, Instant.now());
    }

    /**
     * Check if inactivity suggests team should be considered dead (>1 hour).
     */
    public boolean isTeamLikelyDead() {
        return getInactivityDuration().compareTo(Duration.ofHours(1)) > 0;
    }
}
```

### ProbeResult Class

**Package**: `org.yawlfoundation.yawl.teams.checkpoint`

Quick health check result for a team (without full resume).

```java
public class ProbeResult {

    private final String teamId;
    private final TeamStatus status;
    private final Map<String, TeammateHeartbeatStatus> heartbeats;
    private final int mailboxSize;
    private final Instant lastCheckpointAt;
    private final boolean isResumable;

    public String getTeamId() { return teamId; }
    public TeamStatus getStatus() { return status; }
    public Map<String, TeammateHeartbeatStatus> getHeartbeats() { return heartbeats; }
    public int getMailboxSize() { return mailboxSize; }
    public Instant getLastCheckpointAt() { return lastCheckpointAt; }
    public boolean isResumable() { return isResumable; }

    /**
     * Get count of online teammates (heartbeat < 10 min).
     */
    public int getOnlineTeammateCount() {
        return (int) heartbeats.values().stream()
            .filter(h -> h.isOnline())
            .count();
    }

    /**
     * Get count of stale teammates (heartbeat 10-30 min).
     */
    public int getStaleTeammateCount() {
        return (int) heartbeats.values().stream()
            .filter(h -> h.isStale())
            .count();
    }

    /**
     * Get count of offline teammates (heartbeat > 30 min).
     */
    public int getOfflineTeammateCount() {
        return (int) heartbeats.values().stream()
            .filter(h -> h.isOffline())
            .count();
    }

    public enum TeammateHeartbeatStatus {
        ONLINE,      // < 10 min
        STALE,       // 10-30 min (can wake up)
        OFFLINE,     // > 30 min (reassign)
        CRASHED      // Unrecoverable
    }
}
```

### Checkpoint Storage (.team-state/ Structure)

```
.team-state/
├── τ-engine+schema+test-iDs6b/
│   ├── metadata.json              (team name, ID, status, creation time)
│   ├── teammates.json             (teammate roster with heartbeats)
│   ├── task-queue.jsonl           (append-only task log)
│   ├── mailbox.jsonl              (append-only message log)
│   ├── checkpoints/
│   │   ├── checkpoint-001.json    (team state snapshot)
│   │   ├── checkpoint-002.json
│   │   └── checkpoint-003.json
│   └── git-state.json             (staged files, commit plan)
└── τ-perf-opt-validation-kD9pR/
    └── [same structure]
```

---

## Configuration

### TeamSpecification Class

**Package**: `org.yawlfoundation.yawl.teams`

Configuration for creating a new team.

```java
public class TeamSpecification {

    private final String leadSessionId;
    private final List<String> quantums;              // e.g., ["engine", "schema"]
    private final List<TeammateTaskSpec> tasks;       // Task assignments
    private final int maxTeammateCount;               // 2-5
    private final Duration messageTimeout;            // Default: PT15M
    private final Duration checkpointInterval;        // Default: PT5M
    private final boolean autoConsolidate;            // Default: true
    private final Map<String, Object> options;        // Custom options

    public TeamSpecification(String sessionId, List<String> quantums,
                           List<TeammateTaskSpec> tasks) {
        this.leadSessionId = sessionId;
        this.quantums = quantums;
        this.tasks = tasks;
        this.maxTeammateCount = Math.min(quantums.size(), 5);
        this.messageTimeout = Duration.ofMinutes(15);
        this.checkpointInterval = Duration.ofMinutes(5);
        this.autoConsolidate = true;
        this.options = new HashMap<>();
    }

    public String getLeadSessionId() { return leadSessionId; }
    public List<String> getQuantums() { return quantums; }
    public List<TeammateTaskSpec> getTasks() { return tasks; }
    public int getMaxTeammateCount() { return maxTeammateCount; }
    public Duration getMessageTimeout() { return messageTimeout; }
    public Duration getCheckpointInterval() { return checkpointInterval; }
    public boolean isAutoConsolidateEnabled() { return autoConsolidate; }
    public Map<String, Object> getOptions() { return options; }
}
```

### ConsolidationOptions Class

**Package**: `org.yawlfoundation.yawl.teams`

Options controlling how the lead consolidates and finalizes team work.

```java
public class ConsolidationOptions {

    private final boolean parallelTest;               // Run tests in parallel
    private final boolean failFast;                   // Stop on first test failure
    private final int maxBuildTimeSeconds;            // Timeout for build
    private final boolean requireAllTeammatesGreen;   // All must pass before consolidation
    private final boolean autoCommit;                 // Auto-commit if build succeeds
    private final String commitMessage;               // Custom commit message

    public ConsolidationOptions() {
        this.parallelTest = true;
        this.failFast = false;
        this.maxBuildTimeSeconds = 600;  // 10 minutes
        this.requireAllTeammatesGreen = true;
        this.autoCommit = false;
        this.commitMessage = null;
    }

    public boolean isParallelTestEnabled() { return parallelTest; }
    public boolean isFailFastEnabled() { return failFast; }
    public int getMaxBuildTimeSeconds() { return maxBuildTimeSeconds; }
    public boolean requireAllTeammatesGreen() { return requireAllTeammatesGreen; }
    public boolean isAutoCommitEnabled() { return autoCommit; }
    public Optional<String> getCommitMessage() { return Optional.ofNullable(commitMessage); }
}
```

### ConsolidationResult Class

**Package**: `org.yawlfoundation.yawl.teams`

Result of the consolidation phase (final build + test + validation).

```java
public class ConsolidationResult {

    private final BuildStatus buildStatus;            // SUCCESS, FAILURE, TIMEOUT
    private final int testsRun;
    private final int testsPassed;
    private final int testsFailed;
    private final List<CompileError> compileErrors;
    private final List<TestFailure> testFailures;
    private final Duration buildDuration;
    private final String commitHash;                  // If auto-committed
    private final List<String> pushedBranches;

    public BuildStatus getBuildStatus() { return buildStatus; }
    public int getTestsRun() { return testsRun; }
    public int getTestsPassed() { return testsPassed; }
    public int getTestsFailed() { return testsFailed; }
    public boolean allTestsPassed() { return testsFailed == 0; }
    public List<CompileError> getCompileErrors() { return compileErrors; }
    public List<TestFailure> getTestFailures() { return testFailures; }
    public Duration getBuildDuration() { return buildDuration; }
    public Optional<String> getCommitHash() { return Optional.ofNullable(commitHash); }
    public List<String> getPushedBranches() { return pushedBranches; }

    public enum BuildStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }
}
```

---

## Exit Codes & Error Handling

### Exit Code Reference

| Code | Meaning | Recovery |
|------|---------|----------|
| **0** | Success | Team completed, all tests passed, git push successful |
| **1** | Transient error | IO failure, network timeout, teammate crash—safe to retry |
| **2** | Fatal error | Unrecoverable invariant violation, conflicting changes, persistent failures |

### Exception Hierarchy

```java
// Base exception
public abstract class TeamException extends Exception {
    public abstract int getExitCode();
}

// Specific exceptions
public class TeamNotFound extends TeamException {
    @Override public int getExitCode() { return 1; }  // Transient
}

public class InvalidStateException extends TeamException {
    @Override public int getExitCode() { return 2; }  // Fatal
}

public class MessagingException extends TeamException {
    @Override public int getExitCode() { return 1; }  // Transient: retry
}

public class ConsolidationException extends TeamException {
    @Override public int getExitCode() { return 2; }  // Fatal: fix and retry
}

public class CheckpointException extends TeamException {
    @Override public int getExitCode() { return 1; }  // Transient: IO issue
}

public class TeammateTimeoutException extends TeamException {
    @Override public int getExitCode() { return 1; }  // Transient: reassign
}
```

### Error Recovery Protocol

| Error | Trigger | Action | Exit Code |
|-------|---------|--------|-----------|
| Teammate idle >30 min | No heartbeat | Message + wait 5 min → reassign | 1 |
| Task timeout >2 hours | Task duration | Extend 1h or split → reassign | 1 |
| Message timeout >15 min (critical) | No ACK | Resend [URGENT] + wait 5 min → crash | 1→2 |
| Lead offline >5 min | Session disconnected | Enter ZOMBIE mode, auto-checkpoint | 0 (continue) |
| Build failure in consolidation | compile/test RED | Fix incompatibility + retry | 2 |
| Hook violation (H-guard) | Guard check failed | Teammate fixes locally + re-run | 2 |
| Circular dependency | Task DAG analysis | Lead breaks tie via task ordering | 2 |

---

## Code Examples

### Example 1: Creating and Running a Team

```java
import org.yawlfoundation.yawl.teams.*;

public class TeamCreationExample {

    public static void main(String[] args) throws Exception {
        // Step 1: Initialize teams API
        YawlTeams teamsApi = YawlTeamsFactory.getDefault();

        // Step 2: Define quantums and tasks
        List<String> quantums = List.of("schema", "engine", "test");
        List<TeammateTaskSpec> tasks = List.of(
            new TeammateTaskSpec("task_schema_1",
                "Define SLA tracking fields in YAWL schema (xsd)",
                "schema",
                Duration.ofHours(2)),
            new TeammateTaskSpec("task_engine_1",
                "Implement SLA tracking in YNetRunner",
                "engine",
                Duration.ofHours(3)),
            new TeammateTaskSpec("task_test_1",
                "Write unit tests for SLA tracking",
                "test",
                Duration.ofHours(2))
        );

        // Step 3: Create team specification
        TeamSpecification teamSpec = new TeamSpecification(
            "session-abc123",
            quantums,
            tasks
        );

        // Step 4: Create the team
        Team team = teamsApi.createTeam(teamSpec);
        System.out.printf("✓ Team created: %s%n", team.getTeamId());
        System.out.printf("  Status: %s%n", team.getStatus());
        System.out.printf("  Teammates: %d%n", team.getTeammateCount());

        // Step 5: Monitor team execution
        while (team.getStatus() == TeamStatus.ACTIVE) {
            // Receive updates from teammates
            List<TeamMessage> messages = team.receiveMessages(10);
            for (TeamMessage msg : messages) {
                System.out.printf("[%s → lead] %s%n",
                    msg.getFrom(),
                    msg.getPayload());
            }

            // Checkpoint periodically
            if (shouldCheckpoint()) {
                CheckpointMetadata checkpoint = team.checkpoint("periodic");
                System.out.printf("✓ Checkpoint: %s (%d bytes)%n",
                    checkpoint.getCheckpointId(),
                    checkpoint.getSizeBytes());
            }

            Thread.sleep(30000);  // Poll every 30 seconds
        }

        // Step 6: Consolidate (lead's final phase)
        ConsolidationOptions options = new ConsolidationOptions();
        ConsolidationResult result = team.consolidate(options);

        if (result.getBuildStatus() == ConsolidationResult.BuildStatus.SUCCESS) {
            System.out.printf("✓ Build successful: %d tests passed%n",
                result.getTestsPassed());
            System.exit(team.complete());  // Exit 0
        } else {
            System.err.printf("✗ Build failed: %d compile errors%n",
                result.getCompileErrors().size());
            System.exit(2);  // Fatal error
        }
    }

    private static boolean shouldCheckpoint() {
        // Checkpoint every 5 minutes or after milestone
        return Math.random() < 0.1;
    }
}
```

### Example 2: Teammate Task Execution and Messaging

```java
import org.yawlfoundation.yawl.teams.*;

public class TeammateTaskExample {

    public static void main(String[] args) throws Exception {
        // Teammate receives task from lead
        Team team = YawlTeamsFactory.getDefault()
            .getTeamById("τ-schema+engine+test-iDs6b")
            .orElseThrow(() -> new TeamNotFound("Team not found"));

        Teammate currentTeammate = team.getTeammates().get(0);
        System.out.printf("Teammate: %s (quantum: %s)%n",
            currentTeammate.getTeammateId(),
            currentTeammate.getQuantum());

        // Receive task assignment
        TeammateTask task = currentTeammate.getCurrentTask()
            .orElseThrow(() -> new IllegalStateException("No task assigned"));

        System.out.printf("Task: %s%n", task.getDescription());

        // Step 1: Mark task as in progress
        task.markInProgress();
        System.out.printf("✓ Task in progress: %s%n", task.getTaskId());

        try {
            // Step 2: Execute work (e.g., implement schema)
            executeSchemaImplementation();

            // Step 3: Communicate progress to lead
            MessageReceipt receipt = team.sendMessage(
                TeamMessage.builder()
                    .from(currentTeammate.getTeammateId())
                    .to("lead")
                    .payload("Schema implementation complete. Validated against XSD.")
                    .timeout(Duration.ofMinutes(15))
                    .type(MessageType.STATUS)
                    .build()
            );

            System.out.printf("✓ Message sent (seq: %d)%n",
                receipt.getSequenceNumber());

            // Step 4: Query lead for feedback (optional)
            if (needsFeedback()) {
                MessageReceipt feedbackRequest = team.sendMessage(
                    TeamMessage.builder()
                        .from(currentTeammate.getTeammateId())
                        .to("lead")
                        .payload("Schema review: does SLA field conflict with existing attributes?")
                        .timeout(Duration.ofMinutes(15))
                        .type(MessageType.QUERY)
                        .build()
                );
                System.out.printf("✓ Feedback requested (seq: %d)%n",
                    feedbackRequest.getSequenceNumber());

                // Wait for response
                Thread.sleep(10000);
                List<TeamMessage> responses = team.receiveMessages(5);
                for (TeamMessage resp : responses) {
                    if (resp.getFrom().equals("lead")) {
                        System.out.printf("Lead response: %s%n", resp.getPayload());
                    }
                }
            }

            // Step 5: Mark task completed
            TaskResult result = new TaskResult(
                "SLA tracking schema defined: 3 new fields added",
                List.of("schema/workflow-attributes.xsd"),
                Duration.ofHours(1),
                true  // All dependencies satisfied
            );
            task.markCompleted(result);
            System.out.printf("✓ Task completed: %s%n", task.getTaskId());

        } catch (Exception e) {
            // Mark task failed
            TaskError error = new TaskError(
                "Schema validation failed: " + e.getMessage(),
                "Review XSD syntax. Run: mvn validate -pl yawl-schema"
            );
            task.markFailed(error);
            System.err.printf("✗ Task failed: %s%n", error.getRecoveryGuidance());
            System.exit(1);  // Transient error, can retry
        }
    }

    private static void executeSchemaImplementation() throws Exception {
        // Simulate work
        System.out.println("  → Implementing SLA tracking in XSD...");
        Thread.sleep(2000);
        System.out.println("  → Validating schema...");
        Thread.sleep(1000);
    }

    private static boolean needsFeedback() { return true; }
}
```

### Example 3: Checkpoint and Resume

```java
import org.yawlfoundation.yawl.teams.*;

public class CheckpointResumeExample {

    public static void main(String[] args) throws Exception {
        YawlTeams teamsApi = YawlTeamsFactory.getDefault();

        // Scenario: Lead's session interrupted (network timeout)
        String teamId = "τ-schema+engine+test-iDs6b";

        // Step 1: Probe team liveness (no full resume)
        ProbeResult probe = teamsApi.probeTeam(teamId);
        System.out.printf("Team status: %s%n", probe.getStatus());
        System.out.printf("Online teammates: %d / %d%n",
            probe.getOnlineTeammateCount(),
            probe.getHeartbeats().size());
        System.out.printf("Pending messages: %d%n", probe.getMailboxSize());

        // Step 2: Decide if resumable
        if (!probe.isResumable()) {
            System.err.println("✗ Team not resumable. Too many stale teammates.");
            System.exit(2);  // Fatal: start new team
        }

        // Step 3: Attempt resume
        ResumeContext context = new ResumeContext(
            teamId,
            probe.getLastCheckpointAt().toString(),
            probe.getStatus(),
            probe.getMailboxSize() > 0 ? new ArrayList<>() : List.of(),
            Map.of(),  // Task statuses from checkpoint
            probe.getLastCheckpointAt()
        );

        try {
            Team resumedTeam = teamsApi.resumeTeam(teamId, context);
            System.out.printf("✓ Team resumed: %s%n", resumedTeam.getTeamId());
            System.out.printf("  Status: %s%n", resumedTeam.getStatus());

            // Step 4: Continue execution
            processResumedTeam(resumedTeam);

        } catch (TeamNotFound e) {
            System.err.printf("✗ Team %s not found in checkpoint storage%n", teamId);
            System.exit(1);  // Transient: could be delayed persistence
        } catch (StateException e) {
            System.err.printf("✗ Cannot resume from %s state%n",
                context.getLastKnownStatus());
            System.exit(2);  // Fatal: manual intervention required
        }
    }

    private static void processResumedTeam(Team team) throws Exception {
        // Continue from where we left off
        List<TeamMessage> pendingMessages = team.receiveMessages(20);
        System.out.printf("Resuming with %d pending messages%n",
            pendingMessages.size());

        // ... continue execution as normal ...
    }
}
```

### Example 4: Message Protocol with Timeout Handling

```java
import org.yawlfoundation.yawl.teams.*;

public class MessageProtocolExample {

    public static void main(String[] args) throws Exception {
        Team team = YawlTeamsFactory.getDefault()
            .getTeamById("τ-engine+test-iDs6b")
            .orElseThrow();

        Teammate engineer1 = team.getTeammates().get(0);

        // Scenario 1: Send task to teammate
        System.out.println("=== Sending Task (15 min timeout) ===");
        MessageReceipt taskReceipt = engineer1.sendMessage(
            "Implement deadlock detection in YNetRunner:\n" +
            "  1. Add timeout monitoring to task completion\n" +
            "  2. Detect circular wait conditions\n" +
            "  3. Return to queue or escalate\n" +
            "See IMPLEMENTATION.md for details.",
            900  // 15 minutes
        );

        System.out.printf("✓ Task sent (seq: %d, status: %s)%n",
            taskReceipt.getSequenceNumber(),
            taskReceipt.getStatus());

        // Scenario 2: Poll for acknowledgment
        System.out.println("=== Waiting for ACK ===");
        int pollCount = 0;
        while (!taskReceipt.wasAcknowledged() && pollCount < 12) {
            Thread.sleep(5000);  // Poll every 5 seconds
            pollCount++;

            // Check if message expired
            if (taskReceipt.getStatus() == DeliveryStatus.TIMEOUT) {
                System.err.println("✗ Message timeout after 15 minutes");
                System.err.println("  Action: Reassign task to different teammate");
                break;
            }

            System.out.printf("  Poll %d: status=%s%n",
                pollCount,
                taskReceipt.getStatus());
        }

        if (taskReceipt.wasAcknowledged()) {
            System.out.printf("✓ ACK received at %s%n",
                taskReceipt.getAcknowledgedAt());
        }

        // Scenario 3: Receive progress updates
        System.out.println("=== Receiving Updates ===");
        List<TeamMessage> updates = engineer1.getIncomingMessages();
        for (TeamMessage msg : updates) {
            System.out.printf("[%s] %s%n",
                msg.getTimestamp(),
                msg.getPayload());
        }

        // Scenario 4: Send critical message with urgent flag
        System.out.println("=== Sending Critical Message [URGENT] ===");
        try {
            MessageReceipt criticalMsg = engineer1.sendMessage(
                "[URGENT] Build failed. Can you check compilation errors? " +
                "Blocking consolidation phase.",
                300  // 5 minutes for critical
            );

            System.out.printf("✓ Critical message sent (seq: %d)%n",
                criticalMsg.getSequenceNumber());

            // For critical messages, crash if no response in 5 minutes
            if (!waitForAcknowledgment(criticalMsg, 300)) {
                System.err.println("✗ No ACK for critical message in 5 minutes");
                System.err.println("  Action: Teammate crash detected. Reassign.");
                System.exit(1);  // Transient: can recover
            }

        } catch (MessagingException e) {
            System.err.printf("✗ Failed to send message: %s%n", e.getMessage());
            System.exit(1);  // Transient: retry
        }
    }

    private static boolean waitForAcknowledgment(MessageReceipt receipt,
                                                  int timeoutSeconds)
            throws InterruptedException {
        int waitTime = 0;
        while (waitTime < timeoutSeconds) {
            if (receipt.wasAcknowledged()) {
                return true;
            }
            Thread.sleep(1000);
            waitTime += 1;
        }
        return false;
    }
}
```

### Example 5: Consolidation with Error Handling

```java
import org.yawlfoundation.yawl.teams.*;

public class ConsolidationExample {

    public static void main(String[] args) throws Exception {
        Team team = YawlTeamsFactory.getDefault()
            .getTeamById("τ-schema+engine+test-iDs6b")
            .orElseThrow();

        // All teammates report GREEN on local dx.sh
        System.out.println("=== All Teammates GREEN ===");
        for (Teammate tm : team.getTeammates()) {
            System.out.printf("✓ %s: local dx.sh passed%n", tm.getTeammateId());
        }

        // Step 1: Configure consolidation
        ConsolidationOptions options = new ConsolidationOptions();
        System.out.println("\n=== Consolidation Options ===");
        System.out.printf("Parallel test: %s%n", options.isParallelTestEnabled());
        System.out.printf("Fail fast: %s%n", options.isFailFastEnabled());
        System.out.printf("Max build time: %d seconds%n",
            options.getMaxBuildTimeSeconds());

        // Step 2: Create checkpoint before consolidation
        CheckpointMetadata checkpoint = team.checkpoint("pre-consolidation");
        System.out.printf("\n✓ Pre-consolidation checkpoint: %s%n",
            checkpoint.getCheckpointId());

        // Step 3: Run consolidation
        System.out.println("\n=== Running Consolidation ===");
        ConsolidationResult result;
        try {
            result = team.consolidate(options);

        } catch (ConsolidationException e) {
            System.err.printf("✗ Consolidation failed: %s%n", e.getMessage());
            System.err.println("  Remediation:");
            System.err.println("    1. Identify failing module");
            System.err.println("    2. Message responsible teammate");
            System.err.println("    3. Teammate fixes locally + re-runs dx.sh");
            System.err.println("    4. Lead re-runs consolidation");
            System.exit(2);  // Fatal: requires fix
        }

        // Step 4: Analyze results
        System.out.printf("\nBuild Status: %s%n", result.getBuildStatus());
        System.out.printf("Tests: %d passed, %d failed (of %d run)%n",
            result.getTestsPassed(),
            result.getTestsFailed(),
            result.getTestsRun());
        System.out.printf("Build time: %s%n", result.getBuildDuration());

        // Step 5: Handle failures
        if (!result.allTestsPassed()) {
            System.err.println("\n=== Test Failures ===");
            for (ConsolidationResult.TestFailure failure : result.getTestFailures()) {
                System.err.printf("✗ %s: %s%n",
                    failure.getTestName(),
                    failure.getErrorMessage());
            }
            System.err.println("\nAction: Fix tests and re-consolidate");
            System.exit(2);  // Fatal: manual fix required
        }

        // Step 6: Commit and push
        if (result.getCommitHash().isPresent()) {
            System.out.printf("\n✓ Committed: %s%n", result.getCommitHash().get());
            for (String branch : result.getPushedBranches()) {
                System.out.printf("✓ Pushed: %s%n", branch);
            }
        }

        // Step 7: Complete team
        System.out.println("\n=== Team Complete ===");
        int exitCode = team.complete();
        System.out.printf("✓ Team completed with exit code: %d%n", exitCode);
        System.exit(exitCode);
    }
}
```

---

## API Status Matrix

| Component | Method | Status | Notes |
|-----------|--------|--------|-------|
| **YawlTeams** | createTeam | STABLE | Production-ready |
| | getTeamById | STABLE | Production-ready |
| | listActiveTeams | STABLE | Production-ready |
| | resumeTeam | BETA | Monitor ZOMBIE timeout edge cases |
| | probeTeam | STABLE | Fast health check (<1s) |
| **Team** | sendMessage | STABLE | 30s retry, 3× max attempts |
| | receiveMessages | STABLE | FIFO ordering guaranteed |
| | checkpoint | STABLE | ~50-100 KB per checkpoint |
| | consolidate | STABLE | Exit code 0/2 only |
| **Teammate** | sendMessage | STABLE | Sequence-number ordered |
| | getStatusDetail | BETA | New in v6.0.0 |
| **TeammateTask** | markInProgress | STABLE | Atomic state transition |
| | markCompleted | STABLE | Records checkpoint |
| | markFailed | STABLE | Triggers recovery protocol |

---

## See Also

- **User Guide**: `.claude/rules/TEAMS-GUIDE.md` (how-to, decision trees, patterns)
- **Quick Reference**: `.claude/rules/TEAMS-QUICK-REF.md` (timeout values, error codes)
- **Decision Framework**: `.claude/rules/team-decision-framework.md` (when to use teams)
- **Session Resumption**: `.claude/rules/TEAMS-GUIDE.md#session-resumption` (checkpoint protocol)

---

**Document Version**: 6.0.0 SPR | **Last Updated**: 2026-02-28 | **Status**: Production Ready | **H-Guards**: Zero Violations
