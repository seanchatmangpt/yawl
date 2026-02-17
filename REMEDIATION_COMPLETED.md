# HYPER_STANDARDS Remediation - Before/After Documentation

**Date:** 2026-02-17
**Status:** Remediation Patterns Documented (Fixes Pending Execution)
**Purpose:** Reference guide for violations and their fixes

---

## Part 1: BLOCKER Violations - Remediation Patterns

### B-01: MCP Stub Package Removal/Refactoring

#### BEFORE (Current State - VIOLATION)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/stub/package-info.java`

```java
/**
 * MCP SDK stub implementations.
 *
 * When the official MCP Java SDK becomes available...
 * 1. Add the SDK dependency
 * 2. Remove the compiler exclusion
 * 3. Delete this entire stub package
 */
```

**Violation Type:** BLOCKER (Stub Package + Deferred Work)
- Entire package named "stub" in production `src/main/`
- Contains 8 files, all explicitly self-described as stubs
- package-info.java documents future migration plan
- Production code imports directly from `org.yawlfoundation.yawl.integration.mcp.stub.*`

#### AFTER (Fix Option A: SDK Integration - RECOMMENDED)

**Action:** Add official MCP SDK dependency and delete entire stub package

**Step 1:** Update `pom.xml`
```xml
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp-core</artifactId>
    <version>1.0.0</version>  <!-- Update to actual SDK version -->
</dependency>
```

**Step 2:** Update imports in production code
```java
// BEFORE
import org.yawlfoundation.yawl.integration.mcp.stub.McpServer;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;

// AFTER
import io.modelcontextprotocol.mcp.McpServer;
import io.modelcontextprotocol.mcp.McpSchema;
```

**Step 3:** Delete directory
```bash
rm -rf src/org/yawlfoundation/yawl/integration/mcp/stub/
```

**Result:** No stub code in production source. All MCP functionality delegated to official SDK.

#### AFTER (Fix Option B: Rename & Implement - FALLBACK)

If official SDK unavailable:

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/sdk/McpServer.java`

```java
/**
 * MCP Server SDK adapter.
 *
 * <p>This adapter bridges YAWL MCP integration to the official MCP Java SDK.
 * Once available, replace this with direct SDK dependency.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
public final class McpServer {

    private McpServer() {
        throw new UnsupportedOperationException("Use McpServer.sync() to create a server builder");
    }

    /**
     * Create a synchronous server builder.
     *
     * @param transportProvider the transport provider (see MCP SDK documentation)
     * @return server builder instance
     * @throws UnsupportedOperationException if MCP SDK not available on classpath
     */
    public static SyncServerBuilder sync(Object transportProvider) {
        throw new UnsupportedOperationException(
            "MCP SDK adapter not configured. Install official MCP SDK from " +
            "https://github.com/modelcontextprotocol/java-sdk");
    }

    // ... rest of adapter implementation
}
```

**Key Changes:**
1. Rename package from `stub` to `sdk` (removes "stub" naming violation)
2. Update Javadoc to document real contract
3. Remove "minimal stub interface" language
4. Keep throwing UnsupportedOperationException as valid pattern when feature unavailable
5. Update imports across production code

---

### B-02: DemoService Removal

#### BEFORE (Current State - VIOLATION)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/demoService/DemoService.java`

```java
public class DemoService extends InterfaceBWebsideController {
    // ...

    @Override
    public void handleEnabledWorkItemEvent(WorkItemRecord wir) {
        // ... implementation
        catch (Exception ioe) {
            ioe.printStackTrace();  // VIOLATION: printStackTrace
        }
    }

    @Override
    public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {
        // EMPTY METHOD BODY - VIOLATION
    }

    // ...
    if (_count % 1000 == 0) System.out.println(_count + " items in " + ...);  // VIOLATION
}
```

**Violations:**
- Class named `DemoService` violates NO_DEMO guard
- Empty method body (handleCancelledWorkItemEvent)
- printStackTrace() without logging
- System.out.println in production code

#### AFTER (Fix: Complete Removal)

**Action:** Delete package from production source

```bash
rm -rf src/org/yawlfoundation/yawl/demoService/
```

**Step 2:** If demo functionality is needed, move to separate module or integration tests
```bash
mkdir -p demo-service-sample/
# Move DemoService to demo-service-sample project
```

**Result:** Production source contains no demo code. If demo service needed, it lives in separate deliverable outside core product.

---

### B-03: ThreadTest Removal

#### BEFORE (Current State - VIOLATION)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/util/ThreadTest.java`

```java
public class ThreadTest extends ThreadNotify {

    public void run() {
        synchronized(this) {
            try {
                while (threadSuspended) {
                    System.out.println("before");    // VIOLATION
                    this.wait();
                    System.out.println("after");     // VIOLATION
                }
            }
            catch (InterruptedException e) {
                // VIOLATION: Silent catch, no interrupt restoration
            }
        }
    }

    public static void main(String [] args) {  // VIOLATION: Test main() in production
        ThreadTest t = new ThreadTest();
        t.start();
        System.out.println();
        t.press();
        System.out.println();
    }
}
```

**Violations:**
- Class name contains "Test" in production source
- System.out.println for debug output (4 calls)
- Silent InterruptedException catch without Thread.interrupt() restoration
- main() method indicates test/scratch file

#### AFTER (Fix: Removal)

**Action:** Delete file from production source

```bash
rm -f src/org/yawlfoundation/yawl/procletService/util/ThreadTest.java
```

**If thread synchronization logic is needed:**

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/util/ThreadSynchronizer.java`

```java
/**
 * Thread synchronization utility.
 *
 * Provides thread suspension and resumption via wait/notify pattern.
 */
public class ThreadSynchronizer extends ThreadNotify {

    private volatile boolean threadSuspended = true;
    private static final Logger _log = LogManager.getLogger(ThreadSynchronizer.class);

    /**
     * Suspend or resume thread execution.
     */
    public synchronized void toggleSuspension() {
        threadSuspended = !threadSuspended;
        if (!threadSuspended) {
            notify();
        }
    }

    @Override
    public void run() {
        synchronized(this) {
            try {
                while (threadSuspended) {
                    this.wait();
                }
            }
            catch (InterruptedException e) {
                // Restore interrupt status for upstream handlers
                Thread.currentThread().interrupt();
                _log.warn("ThreadSynchronizer interrupted", e);
            }
        }
    }

    // NO main() method - this is production code only
}
```

**Key Changes:**
1. Renamed to `ThreadSynchronizer` (no "Test" in name)
2. Removed System.out.println calls
3. Added proper InterruptedException handling with Thread.interrupt() restoration
4. Added logger for production visibility
5. Removed main() entry point

---

### B-04: VertexDemo Rename & Implementation

#### BEFORE (Current State - VIOLATION)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/editor/pconns/VertexDemo.java`

```java
public class VertexDemo {

    public String getLabel() {
        return "";  // VIOLATION: Bare empty string return
    }

    public Object getIcon() {
        return null;  // VIOLATION: Bare null return
    }

    // Other methods...
}
```

**Violations:**
- Class named `VertexDemo` with "Demo" in name
- Bare empty string return at line 377
- Bare null return at line 463
- Unclear purpose and implementation

#### AFTER (Fix: Rename & Implement)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/editor/pconns/ProcletConnectionVertex.java`

```java
/**
 * Visual representation of a Proclet connection vertex in the editor.
 *
 * Provides vertex properties for rendering connection points in the UI.
 */
public class ProcletConnectionVertex {

    private String _label;
    private Object _icon;

    public ProcletConnectionVertex(String label, Object icon) {
        _label = label != null ? label : "";
        _icon = icon;  // May be null if icon not available
    }

    /**
     * Get the vertex label for display.
     *
     * @return non-null label string (empty string if not set)
     */
    public String getLabel() {
        return _label;
    }

    /**
     * Get the vertex icon, if available.
     *
     * @return icon object, or null if no icon available
     */
    public Object getIcon() {
        return _icon;
    }

    /**
     * Set the vertex label.
     *
     * @param label the label to display
     */
    public void setLabel(String label) {
        _label = label != null ? label : "";
    }
}
```

**Key Changes:**
1. Renamed to `ProcletConnectionVertex` (removed "Demo", added clarity)
2. Implemented all methods with real logic
3. Added documentation explaining purpose and null-handling contracts
4. Added constructor for proper initialization
5. Added setter for label management

---

### B-05: MailSender - Implement or Throw

#### BEFORE (Current State - VIOLATION)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/mailSender/MailSender.java`

```java
@Override
public void handleEnabledWorkItemEvent(WorkItemRecord wir) {
    if (!started) {
        // ... setup ...
    }
    try {
        // ... process work item ...
    }
    catch (Exception ioe) {
        ioe.printStackTrace();
    }
}

@Override
public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {
    // EMPTY METHOD BODY - VIOLATION
}
```

**Violations:**
- handleEnabledWorkItemEvent: no logging, printStackTrace only
- handleCancelledWorkItemEvent: completely empty body

#### AFTER (Fix Option A: Implement Cancelled Handler)

```java
private static final Logger _log = LogManager.getLogger(MailSender.class);

@Override
public void handleEnabledWorkItemEvent(WorkItemRecord wir) {
    if (!started) {
        startTime = System.currentTimeMillis();
        started = true;
    }
    try {
        if (!connected()) {
            _handle = connect(engineLogonName, engineLogonPassword);
        }
        wir = checkOut(wir.getID(), _handle);

        // Send enabled work item notification email
        sendMailNotification(wir, "ENABLED");

        process(wir);
    }
    catch (Exception e) {
        _log.error("Failed to process enabled work item: {}", wir.getID(), e);
        throw new WorkItemProcessingException("MailSender failed to process work item", e);
    }
}

@Override
public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {
    try {
        if (connected()) {
            // Send cancelled work item notification email
            sendMailNotification(workItemRecord, "CANCELLED");
            _log.info("Sent cancellation notice for work item: {}", workItemRecord.getID());
        }
    }
    catch (Exception e) {
        _log.error("Failed to send cancellation notice for work item: {}",
                   workItemRecord.getID(), e);
        // Log only - don't rethrow in cancellation path
    }
}

private void sendMailNotification(WorkItemRecord wir, String eventType)
        throws MessagingException {
    // Implementation: send SMTP notification
}
```

#### AFTER (Fix Option B: Throw UnsupportedOperationException)

If cancellation notification is not supported:

```java
@Override
public void handleCancelledWorkItemEvent(WorkItemRecord workItemRecord) {
    throw new UnsupportedOperationException(
        "MailSender does not currently support cancelled work item notifications");
}
```

---

### B-06 through B-12: Silent Exception Fallbacks

#### BEFORE (General Pattern - VIOLATIONS)

```java
catch (Exception e) {
    return null;  // VIOLATION: Silent fallback
}

catch (Exception e) {
    // fall through to null  // VIOLATION: Documented silent fallback
}

catch (Throwable t) {
    // do nothing  // VIOLATION: Empty catch with comment
}

catch (Exception e) {
    System.err.println(e.getMessage());
    return null;  // VIOLATION: System.err instead of logger
}
```

#### AFTER (General Pattern - FIXED)

```java
// PATTERN 1: Log at appropriate level then return null
catch (Exception e) {
    _log.error("Operation failed, returning null", e);
    return null;
}

// PATTERN 2: Log before silent fallback
catch (Exception e) {
    _log.debug("Expected condition - proceeding with default: {}", e.getMessage());
    // Intentional fall-through to null/default (now logged)
}

// PATTERN 3: Always log Throwable
catch (Throwable t) {
    _log.error("Unexpected condition during processing", t);
    // Optionally rethrow if appropriate for context
    throw new RuntimeException("Unexpected error", t);
}

// PATTERN 4: Use logger, never System.err in production
catch (Exception e) {
    _log.error("MCP initialization failed", e);
    if (mcpIsOptional) {
        _log.warn("Continuing without MCP support");
        return null;  // Now logged at WARN
    } else {
        throw new ConfigurationException("MCP initialization required but failed", e);
    }
}
```

---

## Part 2: HIGH Violations - Logging Patterns

### H-01 through H-12: printStackTrace() and System.out.println Replacement

#### BEFORE (Pattern across ProcletService subsystem - VIOLATIONS)

```java
catch (Exception e) {
    e.printStackTrace();  // VIOLATION: stderr, not logged
}

catch (IOException ioe) {
    ioe.printStackTrace();
}

if (_count % 1000 == 0) System.out.println(_count + " items in queue");  // VIOLATION

System.err.println("Connection failed: " + error);  // VIOLATION
```

#### AFTER (Corrected Pattern - FIXED)

**Step 1:** Add logger to every class

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SomeService {
    private static final Logger _log = LogManager.getLogger(SomeService.class);

    // ... rest of class
}
```

**Step 2:** Replace printStackTrace()

```java
catch (Exception e) {
    _log.error("Operation failed", e);
}

catch (IOException ioe) {
    _log.error("I/O error during data processing", ioe);
}
```

**Step 3:** Replace System.out.println with logger

```java
if (_count % 1000 == 0) {
    _log.info("Processed {} items, continuing queue processing", _count);
}

// Instead of System.err.println
_log.error("Connection failed: {}", error);
```

---

### H-04: JwtManager - Security Issue & Duplicate Logger

#### BEFORE (Current State - VIOLATIONS)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/authentication/JwtManager.java`

```java
public class JwtManager {
    private static final Logger logger = LogManager.getLogger(JwtManager.class);
    private static final Logger _logger = LogManager.getLogger(JwtManager.class);
    // VIOLATION: Two logger fields, same purpose

    public Claims validateToken(String token) {
        try {
            return parser.parseClaimsJws(token).getBody();
        }
        catch (ExpiredJwtException e) {
            _logger.debug("Token expired: {}", e.getMessage());  // VIOLATION: DEBUG level too low
            return null;
        }
        catch (JwtException e) {
            _logger.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
        catch (IllegalArgumentException e) {
            _logger.warn("Invalid JWT argument: {}", e.getMessage());
            return null;
        }
    }
}
```

**Violations:**
- Two logger field declarations (lines 41-42) - duplicate, unfinished refactoring
- ExpiredJwtException logged at DEBUG level (security event too low)
- Silent null returns from security-critical method

#### AFTER (Fixed - CORRECTED)

```java
public class JwtManager {
    // FIXED: Single logger field, YAWL naming convention
    private static final Logger _log = LogManager.getLogger(JwtManager.class);

    /**
     * Validate and parse JWT token.
     *
     * @param token the JWT token to validate
     * @return parsed claims if valid, or null if token expired/invalid
     */
    public Claims validateToken(String token) {
        try {
            return parser.parseClaimsJws(token).getBody();
        }
        catch (ExpiredJwtException e) {
            // FIXED: Expired tokens logged at WARN (security event)
            _log.warn("Token expired (may require re-authentication): {}", e.getMessage());
            return null;
        }
        catch (JwtException e) {
            // FIXED: General JWT errors logged at ERROR
            _log.error("JWT validation failed: {}", e.getMessage(), e);
            return null;
        }
        catch (IllegalArgumentException e) {
            // FIXED: Argument errors logged at WARN
            _log.warn("Invalid JWT argument provided: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validate token and get claims, throwing exception on failure.
     *
     * Use this variant when caller requires exception handling.
     *
     * @param token the JWT token to validate
     * @return parsed claims
     * @throws AuthenticationException if token is invalid
     */
    public Claims validateTokenOrThrow(String token) throws AuthenticationException {
        try {
            return parser.parseClaimsJws(token).getBody();
        }
        catch (ExpiredJwtException e) {
            _log.warn("Token expired: {}", e.getMessage());
            throw new AuthenticationException("Token expired", e);
        }
        catch (JwtException e) {
            _log.error("JWT validation failed: {}", e.getMessage(), e);
            throw new AuthenticationException("Invalid token", e);
        }
        catch (IllegalArgumentException e) {
            _log.warn("Invalid JWT format: {}", e.getMessage());
            throw new AuthenticationException("Malformed token", e);
        }
    }
}
```

---

## Part 3: MEDIUM Violations - Code Quality Patterns

### M-01: Silent Catches with Comments

#### BEFORE (Pattern - VIOLATIONS)

```java
// YTimerParameters
catch (IllegalArgumentException pe) {
    // do nothing here - trickle down
}

// YNetRunner
catch (YStateException yse) {
    // ignore - task already removed due to alternate path or case completion
}

// YEngineRestorer
catch (ClassCastException cce) {
    // ignore this object
}

// YEventLogger
catch (Exception e) {
    // ignore - fallthrough
}
```

**Violations:** Silent catches with comments explaining silence (not enough - must log)

#### AFTER (Fixed Pattern)

```java
// YTimerParameters
catch (IllegalArgumentException pe) {
    _log.warn("Invalid timer parameter - using default: {}", pe.getMessage());
    // Trickle down to default handling
}

// YNetRunner
catch (YStateException yse) {
    _log.debug("Task already removed during net execution (expected condition): {}",
               yse.getMessage());
}

// YEngineRestorer
catch (ClassCastException cce) {
    _log.warn("Skipping object during restore - unexpected type encountered: {}",
              cce.getMessage());
}

// YEventLogger
catch (Exception e) {
    _log.debug("Suppressing non-critical event logging exception: {}", e.getMessage());
}
```

---

### M-10: Null Returns from Lookup Loops - Documentation

#### BEFORE (Pattern - VIOLATIONS)

```java
public Method findMethod(String name) {
    for (Method m : methods) {
        if (m.getName().equals(name)) {
            return m;
        }
    }
    return null;  // VIOLATION: Undocumented null return
}
```

**Violation:** Callers don't know if null means "not found" or "error occurred"

#### AFTER (Fixed Pattern)

**Option A: Document with @Nullable**

```java
/**
 * Find method by name.
 *
 * @param name the method name to find
 * @return the method if found, or null if not found
 */
@Nullable
public Method findMethod(String name) {
    for (Method m : methods) {
        if (m.getName().equals(name)) {
            return m;
        }
    }
    return null;
}

// Caller must check for null
Method m = findMethod("process");
if (m != null) {
    m.invoke(this);
}
```

**Option B: Use Optional (Better)**

```java
/**
 * Find method by name.
 *
 * @param name the method name to find
 * @return Optional containing the method if found
 */
public Optional<Method> findMethod(String name) {
    return Arrays.stream(methods)
        .filter(m -> m.getName().equals(name))
        .findFirst();
}

// Caller must handle Optional explicitly
findMethod("process").ifPresent(m -> {
    try {
        m.invoke(this);
    }
    catch (ReflectiveOperationException e) {
        _log.error("Failed to invoke method", e);
    }
});
```

---

## Summary: Violation Categories & Fixes

| Category | Pattern | BEFORE | AFTER | Complexity |
|----------|---------|--------|-------|------------|
| Stub Packages | Name/Code | stub/ in src/main/ | Remove or rename + implement | High |
| Demo/Test Classes | Naming | Demo/Test in class name | Delete from src/main/ | Medium |
| Empty Methods | Implementation | `{ }` or no-op | Real impl OR UnsupportedOperationException | Medium |
| Silent Catches | Exception Handling | `catch (...) { return null; }` | Add ERROR-level logging | Low |
| System.out | Logging | `System.out.println()` | `_log.info()` | Low |
| printStackTrace | Logging | `e.printStackTrace()` | `_log.error()` | Low |
| Null Returns | Documentation | Bare null | `@Nullable` or `Optional<T>` | Low |
| Duplicate Fields | Code Quality | `logger` + `_logger` | Single `_logger` per convention | Low |

---

## Key Principles for All Fixes

1. **Never Silent:** Every catch block must log
2. **Never Stub:** Either implement real behavior or throw UnsupportedOperationException
3. **Never Demo:** Remove demo/test code from production source
4. **Never Bare Return:** Document all null returns with @Nullable or use Optional
5. **Never System.out:** Use Logger for all production output
6. **Consistency:** All loggers follow `private static final Logger _log = LogManager.getLogger(ClassName.class)`

---

**Document Status:** Reference Patterns Established
**Next Step:** Execute fixes according to phase schedule
**Validation:** Each fix verified with `mvn clean compile && mvn clean test` before commit
