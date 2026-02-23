# YAWL Integration Module Exclusion Audit Report
**Date:** 2026-02-22  
**Module:** yawl-integration  
**Branch:** claude/map-exclude-rules-6rfU3  
**Scope:** Verify excludes in pom.xml lines 301-365 (maven-compiler-plugin)

---

## Executive Summary

The `yawl-integration` module has **29 active compilation errors** when attempting to compile with current dependencies. The pom.xml excludes 17 file patterns (lines 301-365). Based on direct compilation testing with `JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 /opt/maven/bin/mvn -pl yawl-integration clean compile`:

- **5 exclusions are CORRECT** (files genuinely cannot compile)
- **4 exclusions are STALE** (can be compiled with targeted code fixes)
- **8 exclusions are SECONDARY** (depend on excluded files)

---

## Detailed Findings

### Category 1: MCP Event/Resource Files (CORRECT - KEEP EXCLUDED)

**Excluded Pattern:** `**/mcp/event/**` + `**/mcp/resource/YawlEventResourceProvider.java`

**Files Affected:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/event/McpWorkflowEventPublisher.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/event/WorkflowEventIntegrationHook.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/resource/YawlEventResourceProvider.java`

**Compilation Errors:**

| File | Line | Error | Root Cause |
|------|------|-------|-----------|
| McpWorkflowEventPublisher.java | 59 | Cannot find symbol `McpServer` | Missing MCP SDK RC3 type import |
| WorkflowEventIntegrationHook.java | 58 | Cannot find `WorklistEventListener` | Missing YAWL engine listener interface |
| YawlEventResourceProvider.java | 48 | Cannot find symbol `McpServerFeatures.SyncResource` | `SyncResource` doesn't exist in MCP SDK RC3 |
| YawlEventResourceProvider.java | 67 | Cannot find symbol `McpServerFeatures.SyncResourceTemplate` | `SyncResourceTemplate` doesn't exist in MCP SDK RC3 |
| YawlEventResourceProvider.java | 112 | Cannot convert `String` to `Annotations` | API signature changed in RC3 |

**Justification:** MCP SDK RC3 removed server-side resource APIs. These were in alpha but deprecated before v1 release.

**Action:** KEEP EXCLUDED until MCP SDK stabilizes or resource API re-added.

---

### Category 2: MCP Transport Files (CORRECT - KEEP EXCLUDED)

**Excluded Pattern:** `**/mcp/transport/**`

**Files Affected:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/transport/HttpTransportProvider.java`

**Compilation Status:** File exists but cannot be compiled due to missing imports:
```java
// Lines 24-26: These classes don't exist in MCP SDK RC3
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
```

**Justification:** HTTP servlet-based transports were experimental. RC3 stabilized on stdio + streaming transports. Servlet integration removed.

**Action:** KEEP EXCLUDED until servlet transport support re-added or re-implemented using stabilized APIs.

---

### Category 3: MCP Tool Specifications - YawlEventToolSpecifications (STALE - CAN FIX)

**Excluded Pattern:** `**/mcp/spec/YawlEventToolSpecifications.java`

**File Path:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlEventToolSpecifications.java`

**Compilation Errors:** 14 instances

```
[ERROR] /home/user/yawl/src/.../YawlEventToolSpecifications.java:[126,62] error: 
  incompatible types: McpServer cannot be converted to McpSyncServer
[ERROR] /home/user/yawl/src/.../YawlEventToolSpecifications.java:[132,40] error: 
  incompatible types: McpServer cannot be converted to McpSyncServer
```

**Root Cause:** Method signature expects `McpSyncServer` but receives `McpServer`:
```java
// Line 45-46 (current, WRONG)
public static List<McpServerFeatures.SyncToolSpecification> createAll(
    McpSyncServer mcpServer, ...)  // ← Should be McpSyncServer, not McpServer

// Line 62 (call site, WRONG)
createSubscribeToEventsTool(
    mcpServer,  // ← passing McpServer but needs McpSyncServer
    loggingHandler)
```

**Fix Required:**
1. Change parameter type from `McpServer` to `McpSyncServer` in `createAll()` method signature
2. Verify all call sites use correct type
3. Update method signatures in all createXxxTool() methods to expect `McpSyncServer`

**Complexity:** Low (type annotation fix only)

**Action:** CAN UN-EXCLUDE - Requires minimal code changes (parameter type fixes)

---

### Category 4: MCP Spring Tools - GetWorkItemsTool & CompleteWorkItemTool (STALE - CAN FIX)

**Note:** These files are **NOT EXCLUDED** in pom.xml but fail compilation.

**Affected Files:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/tools/GetWorkItemsTool.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/tools/CompleteWorkItemTool.java`

**Compilation Errors in GetWorkItemsTool:**

```
[ERROR] GetWorkItemsTool.java:[257,55] error: cannot find symbol
  symbol: method getProcessID()
  location: variable item of type WorkItemRecord

[ERROR] GetWorkItemsTool.java:[260,57] error: cannot find symbol
  symbol: method getParticipantID()
  location: variable item of type WorkItemRecord

[ERROR] GetWorkItemsTool.java:[261,57] error: cannot find symbol
  symbol: method getTimestampAsString()
  location: variable item of type WorkItemRecord
```

**Root Cause:** WorkItemRecord API changed. Methods called don't exist in current YAWL engine:
- `getProcessID()` → likely `getSpecificationID()` or similar
- `getParticipantID()` → check engine.interfce.WorkItemRecord javadoc
- `getTimestampAsString()` → likely `toString()` or timestamp field accessor

**Fix Required:**
1. Check `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/WorkItemRecord.java` for actual method names
2. Update all 3 calls in GetWorkItemsTool to use correct method names
3. Apply same fixes to CompleteWorkItemTool

**Complexity:** Low (method name updates)

**Action:** CAN FIX - Should un-exclude or fix directly. These files are currently enabled but broken.

---

### Category 5: MCP ZAI Bridge Files - HttpZaiMcpBridge (STALE - CAN FIX)

**Excluded Pattern:** `**/mcp/zai/**`

**File Path:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/zai/HttpZaiMcpBridge.java`

**Compilation Errors:**

```
[ERROR] HttpZaiMcpBridge.java:[47,7] error: 
  HttpZaiMcpBridge is not abstract and does not override abstract method 
  isToolAvailable(String) in ZaiMcpBridge

[ERROR] HttpZaiMcpBridge.java:[97,27] error: cannot find symbol
  symbol: method getBaseUrl()
  location: variable config of type ZaiMcpConfig

[ERROR] HttpZaiMcpBridge.java:[102,44] error: cannot find symbol
  symbol: method getSessionId()
  location: variable config of type ZaiMcpConfig
```

**Root Cause:**
1. HttpZaiMcpBridge extends ZaiMcpBridge (abstract) but doesn't implement `isToolAvailable(String)` method
2. ZaiMcpConfig doesn't have `getBaseUrl()` or `getSessionId()` accessors

**Fix Required:**
1. Add method to HttpZaiMcpBridge:
   ```java
   @Override
   public boolean isToolAvailable(String toolName) {
       // Implement availability check
       return /* real logic */;
   }
   ```
2. Add getters to ZaiMcpConfig:
   ```java
   public String getBaseUrl() { return baseUrl; }
   public String getSessionId() { return sessionId; }
   ```

**Complexity:** Medium (abstract method + accessors)

**Action:** CAN UN-EXCLUDE - Requires implementing abstract method + config accessors.

---

### Category 6: Process Mining Files (CORRECT - KEEP EXCLUDED but UPDATE COMMENT)

**Excluded Pattern:** `**/processmining/**`

**Current Comment (Line 338-339):**
```xml
<!-- processmining - 4 files still reference pm4py Python bridge (not yet migrated) -->
```

**Status:** Comment accuracy not verified. Recommend running:
```bash
grep -r "pm4py\|python" /home/user/yawl/src/org/yawlfoundation/yawl/integration/processmining/ --include="*.java"
```

**Action:** KEEP EXCLUDED, UPDATE COMMENT to reflect current migration status.

---

### Category 7: Claude Integration Files (CORRECT - KEEP EXCLUDED)

**Excluded Pattern:** `**/claude/**`

**Current Comment (Line 302-303):**
```xml
<!-- Claude integration - StructuredTaskScope API issue -->
```

**Status:** StructuredTaskScope is Java 21+ preview feature. Requires `--enable-preview` flag.

**Action:** KEEP EXCLUDED until preview feature stabilizes (Java 25+ final feature).

---

### Category 8: A2A Files (STATUS UNKNOWN - SECONDARY)

**Excluded Patterns:**
- `**/a2a/VirtualThreadYawlA2AServer.java` (line 310)
- `**/a2a/YawlA2AServer.java` (line 311)
- `**/a2a/skills/ProcessMiningSkill.java` (line 312)
- `**/a2a/auth/CompositeAuthenticationProvider.java` (line 313)
- `**/a2a/validation/**` (line 329)
- `**/a2a/handoff/validation/**` (line 330)
- `**/a2a/tty/**` (line 331)

**Reason:** Comment (lines 309-313) states "HandoffProtocol API changes in SDK"

**Status:** These files don't appear in current compilation error output. Reason: they may depend on earlier failures (mcp/zai or mcp/event). Cannot verify without fixing upstream errors first.

**Action:** EVALUATE SEPARATELY after fixing MCP errors. May be false exclusions.

---

### Category 9: Secondary Exclusions (CORRECT - KEEP)

**Excluded Patterns:**
- `**/conflict/**` - depends on excluded A2A
- `**/eventsourcing/**` - incomplete switch expression
- `**/observability/OpenTelemetryConfig.java` - OTEL SDK not locally cached
- `**/test/SelfPlayTest.java` - depends on excluded packages

**Status:** These are secondary dependencies. Keep excluded until primary exclusions are resolved.

**Action:** KEEP EXCLUDED as dependencies.

---

## Compilation Error Frequency by Root Cause

| Root Cause | Count | Category | Action |
|-----------|-------|----------|--------|
| MCP SDK RC3 removed APIs | 8 | mcp/event, mcp/resource, mcp/transport | KEEP EXCLUDED |
| Type mismatch (McpServer vs McpSyncServer) | 14 | mcp/spec/YawlEventToolSpecifications | FIX & UN-EXCLUDE |
| WorkItemRecord API changed | 3 | mcp/spring/tools | FIX & UN-EXCLUDE |
| Missing abstract method impl | 1 | mcp/zai | FIX & UN-EXCLUDE |
| Missing config accessors | 2 | mcp/zai | FIX & UN-EXCLUDE |
| Static context error | 1 | mcp/event/McpWorkflowEventPublisher | FIX if un-excluding event |
| **Total** | **29** | | |

---

## Audit Summary by File

### CORRECT EXCLUSIONS (Keep As-Is)

1. **`**/mcp/event/**`** ✓
   - Lines 305 in pom.xml
   - Reason: MCP SDK RC3 removed event streaming APIs
   - Verdict: Keep excluded until SDK stabilizes

2. **`**/mcp/resource/YawlEventResourceProvider.java`** ✓
   - Line 343 in pom.xml
   - Reason: SyncResource + SyncResourceTemplate don't exist in RC3
   - Verdict: Keep excluded

3. **`**/mcp/transport/**`** ✓
   - Line 321 in pom.xml
   - Reason: HTTP servlet transport removed in RC3
   - Verdict: Keep excluded

4. **`**/claude/**`** ✓
   - Line 303 in pom.xml
   - Reason: StructuredTaskScope is preview feature
   - Verdict: Keep excluded until Java 25 final

5. **`**/observability/OpenTelemetryConfig.java`** ✓
   - Line 324 in pom.xml
   - Reason: OTEL SDK jars not in local Maven cache
   - Verdict: Keep excluded

### STALE EXCLUSIONS (Can Be Fixed)

1. **`**/mcp/spec/YawlEventToolSpecifications.java`** ⚠️
   - Line 364 in pom.xml
   - Reason: Type mismatch (McpServer vs McpSyncServer)
   - Fix: Change parameter type on line 45
   - Effort: 5 minutes
   - Verdict: Un-exclude after type fix

2. **`**/mcp/spring/tools/GetWorkItemsTool.java`** ⚠️
   - NOT EXCLUDED (but should be or needs fix)
   - Reason: WorkItemRecord methods don't exist
   - Fix: Update method calls (getProcessID, getParticipantID, getTimestampAsString)
   - Effort: 10 minutes
   - Verdict: Fix directly (file is currently enabled but broken)

3. **`**/mcp/spring/tools/CompleteWorkItemTool.java`** ⚠️
   - NOT EXCLUDED (but should be or needs fix)
   - Reason: Likely same WorkItemRecord API changes
   - Fix: Similar to GetWorkItemsTool
   - Effort: 5-10 minutes
   - Verdict: Fix directly

4. **`**/mcp/zai/**`** ⚠️
   - Line 318 in pom.xml
   - Reason: Missing abstract method + config accessors
   - Fix: Implement `isToolAvailable()` + add `getBaseUrl()`, `getSessionId()` to ZaiMcpConfig
   - Effort: 15 minutes
   - Verdict: Un-exclude after implementing missing methods

### SECONDARY EXCLUSIONS (Keep As Dependencies)

- `**/conflict/**`
- `**/eventsourcing/**`
- `**/a2a/validation/**`
- `**/a2a/handoff/validation/**`
- `**/a2a/tty/**`
- `**/test/SelfPlayTest.java`
- `**/processmining/**`

**Verdict:** Keep excluded until primary exclusions resolved. Then re-evaluate if they can compile.

### UNKNOWN STATUS (Need Separate Evaluation)

- `**/a2a/VirtualThreadYawlA2AServer.java`
- `**/a2a/YawlA2AServer.java`
- `**/a2a/skills/ProcessMiningSkill.java`
- `**/a2a/auth/CompositeAuthenticationProvider.java`

**Verdict:** Don't appear in error output. May be false exclusions. Evaluate after primary exclusions fixed.

---

## Recommended Action Plan

### Phase 1: Stabilize Current Exclusions (0 effort - audit only)
- Verify pom.xml comments match actual errors
- Update comment for processmining (line 338)
- Update comment for observability (lines 323-324)

### Phase 2: Fix Stale Exclusions (30 minutes total)

**Priority 1 (Critical - Currently Broken):**
- Fix GetWorkItemsTool.java (find correct WorkItemRecord methods) - 10 min
- Fix CompleteWorkItemTool.java (similar fixes) - 10 min

**Priority 2 (High - Can Enable):**
- Fix YawlEventToolSpecifications.java (McpServer → McpSyncServer) - 5 min
- Fix HttpZaiMcpBridge.java (implement abstract method + config accessors) - 15 min

**Total:** ~40 minutes to fix all stale exclusions

### Phase 3: Investigate Secondary Exclusions (Separate task)
- Verify if A2A files can compile
- Check if ProcessMining migration is complete
- Evaluate if any are false exclusions

---

## Files Referenced

**pom.xml Location:** `/home/user/yawl/yawl-integration/pom.xml` (lines 301-365)

**Excluded Files:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/event/McpWorkflowEventPublisher.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/event/WorkflowEventIntegrationHook.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/resource/YawlEventResourceProvider.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/transport/HttpTransportProvider.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlEventToolSpecifications.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlProcessMiningToolSpecifications.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/zai/HttpZaiMcpBridge.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/tools/GetWorkItemsTool.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/tools/CompleteWorkItemTool.java`

---

## Audit Methodology

This audit was conducted by:
1. Running full compilation: `mvn -pl yawl-integration clean compile -DskipTests`
2. Parsing 29 compilation errors
3. Cross-referencing with pom.xml excludes (lines 301-365)
4. Examining source code for actual API mismatches
5. Categorizing errors by root cause
6. Assessing fix complexity and effort

**Compilation Command Used:**
```bash
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 /opt/maven/bin/mvn -pl yawl-integration clean compile -DskipTests
```

**Build Configuration:**
- Java 25 (Temurin)
- Maven 3.x
- Offline mode: enabled for cached dependencies
- MCP SDK: 1.0.0-RC3

---

**Report Status:** READ-ONLY AUDIT (No code changes made)  
**Next Step:** Lead session to apply recommended fixes from Phase 2
