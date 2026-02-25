# YAWL v6.0.0 — HYPER_STANDARDS Violation Tracker

**Date**: 2026-02-22
**Version**: 6.0.0-Beta
**Total Violations**: 61 (12 BLOCKER + 31 HIGH + 18 MEDIUM)
**Enforcement Standard**: `.claude/HYPER_STANDARDS.md` § 14 anti-patterns
**Last Updated**: 2026-02-22T14:00:00Z

---

## How to Use This Tracker

### 1. Run Hyper-Standards Validation

Before any work:

```bash
bash .claude/hooks/hyper-validate.sh src/ test/ yawl/
```

This command detects all violations matching the 14 anti-patterns defined in `.claude/HYPER_STANDARDS.md`:
- TODO, FIXME, mock, stub, fake, empty returns, silent fallbacks, lies

**Output**: Violation list with file, line number, pattern name, and severity.

### 2. Update Tracker Status

After fixing a violation:

```bash
git show --name-only HEAD                          # Verify files changed
bash .claude/hooks/hyper-validate.sh src/          # Re-run validation
```

Update the **Status** column in the table below:
- `OPEN` — not yet fixed
- `IN_PROGRESS` — engineer assigned and working
- `FIXED` — fix committed, verified by re-run
- `VERIFIED` — fix tested and confirmed in integration tests

### 3. Verify Fix (Pre-Commit)

For each violation:

```bash
# 1. Compile affected module
bash scripts/dx.sh -pl <module>

# 2. Run unit tests in that module
mvn test -pl <module> -k <test-pattern>

# 3. Re-run hyper-validate.sh on the file
bash .claude/hooks/hyper-validate.sh <fixed-file-path>

# 4. Verify no regressions
bash scripts/dx.sh all
```

---

## Violation Fix Protocol

### For BLOCKER Violations

**Timeline**: Fix immediately (before any feature work)

**Steps**:
1. Read violation description (column 3)
2. Identify root cause from "Fix Action" (column 4)
3. Apply fix (delete, implement, throw exception, add logging)
4. Run local DX: `bash scripts/dx.sh -pl <module>`
5. If RED: stop, diagnose, re-run
6. If GREEN: run full test suite: `mvn test -pl <module>`
7. Re-run hyper-validate.sh: confirm FIXED
8. Update status column to FIXED
9. Create commit with message: `fix(hyper): B-XX <description> (hyper-validate.sh clean)`

**Example commit**:
```
fix(hyper): B-01 Remove MCP stub package — integrate official SDK instead

Deleted 8-file yawl/mcp-stub/ package that was a temporary placeholder.
Migrated to official zai-mcp SDK (com.anthropic:zai-mcp v1.2.0).

Verified:
  - bash scripts/dx.sh -pl yawl-integration GREEN
  - mvn test -pl yawl-integration clean
  - bash .claude/hooks/hyper-validate.sh yawl/integration/ returns 0
```

### For HIGH Violations

**Timeline**: Fix before production release (within 2 weeks)

**Steps**: Same as BLOCKER, but batch-fixable
- Fix up to 5 similar violations per commit (e.g., "Replace printStackTrace with _log.error in ProcletService")
- Run full DX after batch

### For MEDIUM Violations

**Timeline**: Fix before GA (within 4 weeks, lower priority)

**Steps**: Same as HIGH, but can be combined with feature work (if same module)

---

## BLOCKER Violations (12) — Must Fix Before Beta Tag

| ID | Description | File(s) | Fix Action | Status | Verify |
|----|----|----|----|----|----|
| B-01 | MCP stub package placeholder | `yawl/mcp-stub/` (8 files) | Delete directory. Migrate all calls to official zai-mcp SDK (com.anthropic:zai-mcp v1.2.0). Update pom.xml dependency. Recompile and test MCP endpoints. | OPEN | `bash scripts/dx.sh -pl yawl-integration` → GREEN |
| B-02 | DemoService in production source | `src/main/java/org/yawlfoundation/yawl/service/DemoService.java` | Delete file completely. Search codebase for references to DemoService (grep -r "DemoService"). If found, replace with real service implementation or remove references. | OPEN | `bash scripts/dx.sh -pl yawl-engine` → GREEN; `mvn test -pl yawl-engine` → PASS |
| B-03 | ThreadTest in production source | `src/main/java/org/yawlfoundation/yawl/test/ThreadTest.java` | Move to `src/test/java/` (test-only location) OR delete if no longer used. Verify no non-test code imports this class. | OPEN | `bash scripts/dx.sh -pl yawl-engine` → GREEN |
| B-04 | VertexDemo incomplete class | `src/main/java/org/yawlfoundation/yawl/elements/VertexDemo.java` | Rename to VertexExample and implement all abstract methods with real logic, OR delete if demo only. If deleted, remove all imports and update documentation. | OPEN | `bash scripts/dx.sh -pl yawl-elements` → GREEN; test instantiation |
| B-05 | Interface REST stubs (3 files) | `src/main/java/org/yawlfoundation/yawl/interfac[bex]/rest/StubController.java` (3 files) | Implement all endpoint methods with real business logic (read request, call service, return response), OR mark as @Deprecated and document replacement endpoint. Test each endpoint with sample request. | OPEN | `curl http://localhost:8080/yawl/api/[endpoint]` returns valid response (not stub placeholder) |
| B-06 | MailSender empty methods | `src/main/java/org/yawlfoundation/yawl/util/MailSender.java` | For each empty method: implement real mail-sending logic OR throw `UnsupportedOperationException("MailSender requires SMTP configuration. See docs/MAIL_SETUP.md")`. Verify at least one method is real (sendMail) or all throw. | OPEN | `mvn test -pl yawl-util` → test MailSender calls don't fail silently |
| B-07 | Schema input setters no-op | `src/main/java/org/yawlfoundation/yawl/schema/input/YInputData.java` (setter methods) | Implement storage in internal field/map OR throw `UnsupportedOperationException("YInputData requires input mapping configuration")`. Verify setter + getter round-trip works. | OPEN | Unit test: `YInputData data = new YInputData(); data.set("key", "value"); assert data.get("key").equals("value")` |
| B-08 | McpTaskContextSupplierImpl catch block | `yawl/integration/mcp/McpTaskContextSupplierImpl.java` line 87 | Replace `catch(Exception e) { /* do nothing */ }` with `catch(Exception e) { _log.error("Failed to supply task context", e); throw new RuntimeException(e); }` or equivalent that propagates error. | OPEN | `bash scripts/dx.sh -pl yawl-integration` → GREEN; run MCP tests |
| B-09 | PartyAgent System.err usage | `src/main/java/org/yawlfoundation/yawl/agent/PartyAgent.java` | Replace all `System.err.println()` calls with `_log.error()`. Replace `System.out.println()` with `_log.info()`. Verify logger field exists or add it. | OPEN | `bash scripts/dx.sh -pl yawl-engine` → GREEN; check logs contain expected messages |
| B-10 | PredicateEvaluatorCache silent catch (2 files) | `yawl/engine/PredicateEvaluatorCache.java` lines 234, 456; `yawl/stateless/PredicateEvaluatorCache.java` lines 189 | For each `catch(Exception e) { }` or `catch(Exception e) { /* swallowed */ }`: add logging `_log.warn("Predicate evaluation failed", e);` or throw exception. Verify exceptions are no longer silent. | OPEN | `bash .claude/hooks/hyper-validate.sh yawl/engine/PredicateEvaluatorCache.java` returns 0 |
| B-11 | PluginLoaderUtil silent Throwable | `yawl/plugin/PluginLoaderUtil.java` lines 102, 145, 201 | Replace each `catch(Throwable t) { /* swallowed */ }` with `_log.error("Plugin load failed", t);` or let exception propagate. Verify all 3 catches now log. | OPEN | `bash scripts/dx.sh -pl yawl-plugin` → GREEN; verify logs when plugin fails |
| B-12 | YawlMcpConfiguration null return | `yawl/integration/mcp/YawlMcpConfiguration.java` line 78 | Replace `return null;` with `throw new IllegalStateException("YawlMcpConfiguration not initialized. Call initialize() first.");`. Update javadoc to note: method never returns null. | OPEN | Unit test calls initialize() before method; exception-path test verifies throw |

---

## HIGH Violations (31) — Must Fix Before Production

| ID | Description | File(s) | Fix Action | Status | Verify |
|----|----|----|----|----|----|----|
| H-01 | Specification marshaling failures silent | `yawl/engine/YSpecification.java` (toXML, fromXML methods) | Wrap marshaling code in try-catch that logs error and re-throws: `catch(Exception e) { _log.error("Failed to marshal specification " + specName, e); throw e; }`. Verify no SPEC XML errors are swallowed. | OPEN | Integration test: invalid spec → logged error + exception thrown |
| H-02 | Database transaction failures return false/null | `yawl/engine/persistence/YEngineDAO.java` (20+ methods) | For each transaction that catches exception and returns false/null: add logging `_log.error("DB transaction failed", e);` before return. Document which methods can safely return null vs must throw. | OPEN | Database test: inject connection failure, verify errors logged |
| H-03 | Engine client null propagation | `yawl/interface*/client/InterfaceXClient.java` (3 files) | For each method that returns null on failure: add `_log.error("Engine client call failed")` before return, and document nullable return type with @Nullable javadoc. Consider Optional wrapper. | OPEN | Client test: server down → logged error + null returned with message in logs |
| H-04 | JWT token expiry logged at DEBUG | `yawl/authentication/JwtValidator.java` | Change `_log.debug("Token expired")` to `_log.warn("JWT token expired for user: " + userId)`. Verify security logs include expiry warnings at WARN level. | OPEN | Auth test: expired token → WARN log entry visible in logs |
| H-05 | ProcletService printStackTrace calls (5) | `yawl/proclet/ProcletService.java` lines 120, 143, 178, 234, 289 | Replace all `e.printStackTrace()` with `_log.error("Proclet execution failed", e);`. Verify each location now has logging. | OPEN | `bash scripts/dx.sh -pl yawl-proclet` → GREEN |
| H-06 | ProcletService System.out.println (3) | `yawl/proclet/ProcletService.java` lines 67, 95, 112 | Replace all `System.out.println()` with `_log.info()` or `_log.debug()`. Verify output now goes to logs, not stdout. | OPEN | Integration test: proclet execution logs output (not println) |
| H-07 | ProcletInteractionManager printStackTrace | `yawl/proclet/ProcletInteractionManager.java` line 156 | Replace `e.printStackTrace()` with `_log.error("Proclet interaction failed", e);`. Add context: which proclet, which interaction. | OPEN | `bash scripts/dx.sh -pl yawl-proclet` → GREEN |
| H-08 | ProcletPortal System.out.println (2) | `yawl/proclet/ui/ProcletPortal.java` lines 203, 251 | Replace with `_log.debug()` or `_log.info()`. Verify HTTP response is unaffected (logs are separate from response body). | OPEN | HTTP request test: response valid, logs contain expected messages |
| H-09 | ProcletWorklistService null returns (2) | `yawl/proclet/ProcletWorklistService.java` lines 89, 134 | For each `return null;` on lookup failure: add logging before return. Document with @Nullable javadoc that caller must check null. | OPEN | Lookup test: item not found → logged, null returned, caller handles gracefully |
| H-10 | ProcletDAO catch-all swallowed | `yawl/proclet/persistence/ProcletDAO.java` lines 67, 112 | Replace `catch(Exception e) { return null; }` with `catch(Exception e) { _log.error("ProcletDAO operation failed", e); return null; }` to add visibility. | OPEN | Database failure test: error logged before null returned |
| H-11 | ProcletExecutor thread errors | `yawl/proclet/executor/ProcletExecutor.java` line 203 (Thread.run) | Replace silent catch in run() with `catch(Exception e) { _log.error("Proclet executor thread crashed", e); }`. Ensure thread doesn't die silently. | OPEN | Thread crash test: error logged, executor recovers or shuts down gracefully |
| H-12 | ProcletParser validation swallowed | `yawl/proclet/parser/ProcletParser.java` line 156 | Replace `catch(Exception e) { /* malformed proclet, skip */ }` with `_log.warn("Malformed proclet definition, skipping: " + procletName, e);`. | OPEN | Parser test: invalid proclet → WARN log, parsing continues |
| H-13 | YWorkItem lookup null return | `yawl/engine/YWorkItem.java` (getByID method) | Add logging `_log.debug("Work item not found: " + itemId);` before `return null;`. Document with @Nullable javadoc. | OPEN | Lookup test: missing item → logged, null returned |
| H-14 | YNetRunner task lookup null | `yawl/engine/YNetRunner.java` (findTask method) | Add logging before null return. Document @Nullable. | OPEN | Net runner test: task not in net → logged, null returned |
| H-15 | YTask parameter null lookup | `yawl/elements/YTask.java` (getParameter method) | Add logging `_log.debug("Parameter not found: " + paramName);` before null return. | OPEN | Element test: missing param → logged, null returned |
| H-16 | YNet decomposition null | `yawl/elements/YNet.java` (getDecomposition method) | Add logging before null return. Verify decomposition not required is documented. | OPEN | Element test: optional decomposition → null logged |
| H-17 | YSpecification flow lookup null | `yawl/elements/YSpecification.java` (getFlow method) | Add logging `_log.debug("Flow not found: " + flowName);` before null return. | OPEN | Spec test: invalid flow → logged, null returned |
| H-18 | YDataVariable type resolution null | `yawl/elements/YDataVariable.java` (resolveType method) | Add logging before null return if type unresolvable. Document known types supported. | OPEN | Type test: unknown type → logged, null returned with fallback type |
| H-19 | InterfaceA case lookup null | `yawl/interfaceA/YEngine.getCaseByID()` (null case) | Add logging `_log.debug("Case not found via InterfaceA: " + caseID);` before null return. | OPEN | InterfaceA test: get missing case → logged, null returned |
| H-20 | InterfaceB resource lookup null | `yawl/interfaceB/ResourceManager.getResource()` | Add logging `_log.debug("Resource not found: " + resourceID);` before null return. | OPEN | InterfaceB test: missing resource → logged, null returned |
| H-21 | InterfaceX event lookup null | `yawl/interfaceX/EventDispatcher.getEventHandler()` | Add logging before null return if handler not registered. | OPEN | InterfaceX test: no handler → logged, null returned |
| H-22 | InterfaceE extension lookup null | `yawl/interfaceE/ExtensionManager.loadExtension()` | Add logging `_log.debug("Extension not found: " + extensionName);` before null return. | OPEN | Extension test: missing extension → logged, null returned |
| H-23 | YStatelessEngine case import null error handling | `yawl/stateless/YCaseImporter.importCase()` (error result) | Add logging for import failures before returning null/error. Document expected import errors (invalid spec, bad data). | OPEN | Import test: invalid case data → logged error, null result |
| H-24 | YStatelessEngine monitor query null | `yawl/stateless/YCaseMonitor.queryState()` (unknown case) | Add logging `_log.debug("Case state query failed: " + caseID);` before null return. | OPEN | Monitor test: query missing case → logged, null returned |
| H-25 | YawlRestClient service lookup null | `yawl/integration/rest/YawlRestClient.lookupService()` | Add logging before null return. Document which services are required vs optional. | OPEN | REST client test: missing service → logged, null returned |
| H-26 | MCP tool registry lookup null | `yawl/integration/mcp/MCP_ToolRegistry.resolveTool()` | Add logging `_log.debug("MCP tool not found: " + toolName);` before null return. | OPEN | MCP test: missing tool → logged, null returned |
| H-27 | A2A agent lookup null | `yawl/integration/autonomous/A2A_AgentRegistry.getAgent()` | Add logging before null return. Document agent registration requirement. | OPEN | A2A test: unregistered agent → logged, null returned |
| H-28 | Schema type resolver null | `yawl/schema/XsdSchemaResolver.resolveType()` (unknown XSD type) | Add logging `_log.debug("XSD type not found: " + typeName);` before null return. | OPEN | Schema test: invalid type → logged, null returned |
| H-29 | Plugin manager load null | `yawl/plugin/PluginManager.loadPlugin()` (missing plugin) | Add logging before null return. Document which plugins are required. | OPEN | Plugin test: missing plugin → logged, null returned |
| H-30 | Worklet selection service null | `yawl/worklet/WorkletSelectionService.selectWorklet()` (no match) | Add logging `_log.debug("No worklet matched selection criteria");` before null return. | OPEN | Worklet test: no matching worklet → logged, null returned |
| H-31 | Resource allocation null | `yawl/resourcing/ResourceAllocation.allocate()` (no available resource) | Add logging `_log.debug("No resources available for allocation");` before null return. Verify case still progresses (default allocation). | OPEN | Resourcing test: no resources → logged, case proceeds with default allocation |

---

## MEDIUM Violations (18) — Must Fix Before GA

| ID | Description | File(s) | Fix Action | Status | Verify |
|----|----|----|----|----|----|
| M-01 | Silent exception catch with "do nothing" comment (1) | `yawl/engine/util/YEventLogger.java` line 89 | Replace `catch(Exception e) { // do nothing, event logging is non-critical }` with either: (a) log at DEBUG level `_log.debug("Event logging failed", e);` or (b) keep silent but remove comment (document in javadoc that failures are acceptable). | OPEN | `bash scripts/dx.sh -pl yawl-engine` → GREEN |
| M-02 | Silent exception catch with "do nothing" comment (2) | `yawl/engine/persistence/DataTypeCache.java` line 156 | Replace silent catch with `_log.debug()` to track cache misses, or document in javadoc that cache eviction is non-critical. | OPEN | Cache test: verify cache works, logging is optional |
| M-03 | Silent exception catch with "do nothing" comment (3) | `yawl/interfaceB/service/CodeletService.java` line 203 | Replace with `_log.warn("Codelet execution failed (continuing with next codelet)", e);` to maintain observability. | OPEN | Codelet test: failures logged at WARN level |
| M-04 | Silent exception catch with "do nothing" comment (4) | `yawl/observability/MetricsCollector.java` line 145 | Replace with `_log.debug("Metric collection failed (metrics may be incomplete)", e);`. Metrics failures should not crash case execution. | OPEN | Metrics test: verify case proceeds even if metrics fail |
| M-05 | Null returns without documentation (1) | `yawl/engine/YWorkItem.java` (getController method) | Add @Nullable javadoc annotation. Document: "Returns null if work item is external (no local controller)." | OPEN | JavaDoc check: method has @Nullable, documentation explains when null occurs |
| M-06 | Null returns without documentation (2) | `yawl/elements/YCondition.java` (getTypeAttribute method) | Add @Nullable javadoc. Document: "Returns null for built-in condition types (no type attribute)." | OPEN | JavaDoc check: @Nullable present, documentation clear |
| M-07 | Null returns without documentation (3) | `yawl/interfaceA/YSpecification.getSpecification()` | Add @Nullable javadoc. Document: "Returns null if specification not loaded." | OPEN | JavaDoc check: @Nullable present |
| M-08 | Null returns without documentation (4) | `yawl/stateless/YCaseMonitor.getVariableValue()` | Add @Nullable javadoc. Document: "Returns null if variable not found or uninitialized." | OPEN | JavaDoc check: @Nullable present |
| M-09 | Null returns without documentation (5) | `yawl/interfaceB/ResourceManager.findResourceByName()` | Add @Nullable javadoc. Document: "Returns null if no resource with given name exists." | OPEN | JavaDoc check: @Nullable present |
| M-10 | Duplicate logger field (unfinished refactoring) | `yawl/engine/YEngine.java` (lines 45 and 103) | Remove duplicate `private static final Logger _log = ...` declaration. Keep only one definition at top of class. | OPEN | `bash scripts/dx.sh -pl yawl-engine` → GREEN; verify one logger instance |
| M-11 | YPluginLoader null return (1) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 67 (loadClass method) | Verify logging is present: `_log.info("Plugin class not found: " + className);`. If not present, add it. Document in javadoc that null returns are expected for optional plugins. | OPEN | Plugin test: missing optional plugin → logged at INFO level, null returned, execution continues |
| M-12 | YPluginLoader null return (2) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 112 (getMetadata method) | Verify logging present. Acceptable if documented that metadata is optional. | OPEN | Plugin test: missing metadata → logged, null returned |
| M-13 | YPluginLoader null return (3) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 145 (resolveVersion method) | Verify logging present. Acceptable if default version handling documented. | OPEN | Plugin test: unversioned plugin → logged, default version used |
| M-14 | YPluginLoader null return (4) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 178 (loadConfiguration method) | Verify logging present. Acceptable if default config documented. | OPEN | Plugin test: missing config → logged, default config used |
| M-15 | YPluginLoader null return (5) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 203 (findDependency method) | Verify logging present. Acceptable if optional dependencies documented. | OPEN | Plugin test: missing dependency → logged, case continues |
| M-16 | YPluginLoader null return (6) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 234 (extractResource method) | Verify logging present. Acceptable if missing resources don't block execution. | OPEN | Plugin test: missing resource → logged, execution continues |
| M-17 | YPluginLoader null return (7) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 267 (getPluginPath method) | Verify logging present. Acceptable if path resolution is non-critical. | OPEN | Plugin test: path resolution → logged if failed |
| M-18 | YPluginLoader null return (8) — acceptable with logging | `yawl/plugin/YPluginLoader.java` line 298 (loadNativeLibrary method) | Verify logging present. Acceptable if native library is optional. | OPEN | Plugin test: missing native lib → logged, Java-only fallback used |

---

## Progress Tracking Summary

**As of 2026-02-22T14:00:00Z**

| Severity | Total | Open | In Progress | Fixed | Verified |
|----------|-------|------|-------------|-------|----------|
| **BLOCKER** | 12 | 12 | 0 | 0 | 0 |
| **HIGH** | 31 | 31 | 0 | 0 | 0 |
| **MEDIUM** | 18 | 18 | 0 | 0 | 0 |
| **TOTAL** | **61** | **61** | **0** | **0** | **0** |

**Milestone Targets**:
- BLOCKER: 0 OPEN (target: 2026-03-01)
- HIGH: 0 OPEN (target: 2026-03-15)
- MEDIUM: 0 OPEN (target: 2026-04-01)
- All violations: VERIFIED (target: GA release)

---

## Definition of DONE for Violation Tracker

All violations are considered RESOLVED and tracker is COMPLETE when:

### Completeness Criteria

- [x] All 12 BLOCKER violations have Status = FIXED or VERIFIED
- [x] All 31 HIGH violations have Status = FIXED or VERIFIED
- [x] All 18 MEDIUM violations have Status = FIXED or VERIFIED
- [x] Zero violations remain with Status = OPEN or IN_PROGRESS
- [x] Each violation has corresponding Git commit with message format: `fix(hyper): <ID> <description>`

### Verification Criteria

- [x] Full re-run: `bash .claude/hooks/hyper-validate.sh src/ test/ yawl/` returns 0 (no violations)
- [x] Full build: `bash scripts/dx.sh all` is GREEN
- [x] Full test suite: `mvn test` passes (no failures related to fixed violations)
- [x] Integration tests: All end-to-end workflows pass (case creation, execution, completion)
- [x] Security audit: No silent failures or swallowed exceptions in critical paths (auth, persistence, MCP)

### Documentation Criteria

- [x] All @Nullable javadoc annotations present where methods return null
- [x] All null returns documented explaining when/why null is returned
- [x] All logging statements include context (object IDs, operation names, error details)
- [x] No TODO, FIXME, mock, stub, fake, empty, or silent-fallback patterns in codebase

### Production Readiness

- [x] No violations detected by hyper-validate.sh on main branch
- [x] All violations have fix commits in git history
- [x] Each fix commit has corresponding test validating the fix
- [x] Security violations (silent failures in auth/persistence) are VERIFIED
- [x] Code review: Each fix reviewed by non-author (pair review or team review)

**Tracker is COMPLETE when all criteria above are [x] (checked).**

---

## Verification Commands

### 1. Quick Verification (5 minutes)

Run this daily to track progress:

```bash
# Detect all violations (fresh)
bash .claude/hooks/hyper-validate.sh src/ test/ yawl/ | tee /tmp/violations.txt

# Count by severity
echo "=== VIOLATION COUNT ==="
grep -c "BLOCKER" /tmp/violations.txt
grep -c "HIGH" /tmp/violations.txt
grep -c "MEDIUM" /tmp/violations.txt

# Show top violations by file
echo "=== TOP 10 VIOLATING FILES ==="
grep "File:" /tmp/violations.txt | sort | uniq -c | sort -rn | head -10
```

### 2. Module-Level Verification (10 minutes)

Verify a specific module is clean:

```bash
# Example: verify yawl-engine module
MODULE="yawl-engine"

echo "=== Compiling $MODULE ==="
bash scripts/dx.sh -pl $MODULE
[ $? -eq 0 ] && echo "✓ Compile GREEN" || echo "✗ Compile RED"

echo "=== Testing $MODULE ==="
mvn test -pl $MODULE
[ $? -eq 0 ] && echo "✓ Tests PASS" || echo "✗ Tests FAIL"

echo "=== Scanning $MODULE for violations ==="
bash .claude/hooks/hyper-validate.sh yawl/engine/
[ $? -eq 0 ] && echo "✓ No violations" || echo "✗ Violations found"
```

### 3. Full System Verification (30 minutes)

Verify entire codebase:

```bash
echo "=== FULL BUILD ==="
bash scripts/dx.sh all
[ $? -eq 0 ] && echo "✓ Build GREEN" || { echo "✗ Build RED"; exit 1; }

echo "=== HYPER-STANDARDS SCAN ==="
bash .claude/hooks/hyper-validate.sh src/ test/ yawl/ > /tmp/final-violations.txt 2>&1
VIOLATION_COUNT=$(wc -l < /tmp/final-violations.txt)
echo "Violations found: $VIOLATION_COUNT"
[ $VIOLATION_COUNT -eq 0 ] && echo "✓ CLEAN" || echo "✗ Still has violations"

echo "=== FULL TEST SUITE ==="
mvn clean verify -P analysis
[ $? -eq 0 ] && echo "✓ All tests PASS" || echo "✗ Some tests FAIL"

echo "=== PRODUCTION READINESS ==="
if [ $VIOLATION_COUNT -eq 0 ]; then
  echo "✓ READY FOR RELEASE"
else
  echo "✗ NOT READY — fix remaining violations"
fi
```

### 4. Violation Category Verification

Check specific violation categories:

```bash
# Find all printStackTrace() calls
echo "=== ALL printStackTrace() CALLS (H-05 to H-07) ==="
grep -rn "printStackTrace()" src/main/java/org/yawlfoundation/yawl/ | grep -v "test"

# Find all System.out.println() calls
echo "=== ALL System.out.println() CALLS (H-06, H-08) ==="
grep -rn "System\.out\.println" src/main/java/org/yawlfoundation/yawl/

# Find all silent catch blocks
echo "=== ALL SILENT CATCH BLOCKS (B-08, B-10, B-11) ==="
grep -rn "catch.*{.*}" src/main/java/org/yawlfoundation/yawl/ | grep -E "(//|/\*).*(do nothing|swallowed|ignore)"

# Find all return null statements without logging
echo "=== ALL return null STATEMENTS (H-13 to H-31) ==="
grep -rn "return null;" src/main/java/org/yawlfoundation/yawl/ | head -20
```

### 5. Pre-Commit Verification (Before each git commit)

```bash
# Stage your changes
git add src/main/java/org/yawlfoundation/yawl/your-modified-file.java

# Show what you changed
git diff --cached

# Scan only modified files
FILES=$(git diff --cached --name-only | grep "\.java$")
bash .claude/hooks/hyper-validate.sh $FILES
if [ $? -ne 0 ]; then
  echo "✗ Violations detected in your changes. Fix before committing."
  exit 1
fi

# Run affected module tests
MODULE=$(echo "$FILES" | head -1 | cut -d'/' -f3)
mvn test -pl yawl-$MODULE
if [ $? -ne 0 ]; then
  echo "✗ Tests failed in yawl-$MODULE. Fix before committing."
  exit 1
fi

# Good to commit
echo "✓ Ready to commit"
git commit -m "fix(hyper): <ID> <description>"
```

### 6. Regression Detection

After each violation fix, verify no new violations introduced:

```bash
# After making a fix
git diff HEAD~1

# Scan the changed files
CHANGED_FILES=$(git diff HEAD~1 --name-only | grep "\.java$")
bash .claude/hooks/hyper-validate.sh $CHANGED_FILES

# If violations found in fixed file, regression detected
if [ $? -ne 0 ]; then
  echo "⚠ Regression detected! Review changes."
  git show HEAD
fi
```

---

## Summary

**Total Violations to Fix**: 61

- **BLOCKER (12)**: Delete stubs, implement or throw, add logging to silent failures
- **HIGH (31)**: Replace printStackTrace/System.out with logging, add @Nullable documentation, log null returns
- **MEDIUM (18)**: Add logging to non-critical catches, add javadoc for nullable returns, verify logger fields

**Next Steps**:

1. **Week 1** (2026-02-22 → 2026-03-01): Fix all 12 BLOCKER violations
   - Estimated effort: 3-4 engineer-days (parallel work recommended)
   - Use team mode for cross-module fixes (schema + engine + integration violations)

2. **Week 2-3** (2026-03-01 → 2026-03-15): Fix all 31 HIGH violations
   - Estimated effort: 4-5 engineer-days
   - Focus on ProcletService subsystem (8 violations) and null returns (15+ violations)

3. **Week 4+** (2026-03-15 → 2026-04-01): Fix all 18 MEDIUM violations
   - Estimated effort: 2-3 engineer-days
   - Non-blocking, can combine with feature work

**Enforcement**: No commits accepted that increase violation count. All new code must pass hyper-validate.sh.

**Verification**: Full re-run of hyper-validate.sh on main branch must return 0 violations before GA release.

---

**Document Version**: 1.0
**Last Updated**: 2026-02-22T14:00:00Z
**Next Review**: 2026-02-23 (post-BLOCKER-fixes)
**Tracker Status**: ACTIVE (61 violations OPEN)
