# Branch Validation Prompt — `claude/map-exclude-rules-6rfU3`

**Purpose**: Comprehensive validation of every change on this branch before merge.
**Session ID**: `6rfU3`
**Merge base**: `da379067c68a6bf4144c6eb0f1f147a74626b219`

## Branch Summary

19 commits across the following themes:

1. **Z.AI SDK migration** — replaced ad hoc `ZaiHttpClient` / `PinnedTrustManager` with `zai-sdk 0.3.0`
2. **MCP SDK RC3 migration** — migrated 10+ classes to `McpSyncServer` / `CallToolResult` builder API
3. **pom.xml exclusion cleanup** — removed ~60 stale `<exclude>` entries as files were fixed
4. **Process mining** — deleted `Pm4PyClient.java` (Python bridge), added `Ocel2Exporter.java`,
   fixed `PerformanceAnalyzer.java` (imports `BottleneckDetector` from shared `src/`)
5. **Claude tool** — migrated `ExecuteClaudeTool` to RC3, removed stale `claude/**` exclusion
6. **InterfaceB API fixes** — `CompleteWorkItemTool`, `GetWorkItemsTool` use correct engine API

## Changed Files

```
yawl-integration/pom.xml                                    # exclusion cleanup + zai-sdk dep
src/.../integration/claude/ExecuteClaudeTool.java           # MCP RC3 migration
src/.../integration/mcp/YawlMcpServer.java                  # MCP RC3 migration
src/.../integration/mcp/event/McpWorkflowEventPublisher.java
src/.../integration/mcp/spec/YawlEventToolSpecifications.java
src/.../integration/mcp/spec/YawlProcessMiningToolSpecifications.java  # removed pm4py deps
src/.../integration/mcp/spec/YawlSpecToolSpecifications.java            # removed claude/** deps
src/.../integration/mcp/spec/YawlToolSpecifications.java
src/.../integration/mcp/spring/YawlMcpConfiguration.java
src/.../integration/mcp/spring/YawlMcpSpringApplication.java
src/.../integration/mcp/spring/YawlMcpToolRegistry.java
src/.../integration/mcp/spring/tools/CompleteWorkItemTool.java
src/.../integration/mcp/spring/tools/GetWorkItemsTool.java
src/.../integration/mcp/zai/HttpZaiMcpBridge.java           # implements ZaiMcpBridge interface
src/.../integration/mcp/zai/ZaiBridgeToolRegistry.java      # RC3 builder API
src/.../integration/mcp/zai/ZaiMcpBridge.java
src/.../integration/orderfulfillment/Pm4PyClient.java       # DELETED
src/.../integration/processmining/Ocel2Exporter.java        # NEW
src/.../integration/processmining/ProcessDiscoveryResult.java
src/.../integration/processmining/discovery/HeuristicMiner.java
src/.../integration/processmining/discovery/ProcessDiscoveryAlgorithm.java
src/.../integration/processmining/performance/PerformanceAnalyzer.java
src/.../integration/processmining/streaming/YAWLEventStream.java
src/.../integration/processmining/synthesis/ConformanceScore.java
src/.../integration/zai/PinnedTrustManager.java             # DELETED
src/.../integration/zai/SpecificationGenerator.java         # NEW (uses zai-sdk)
src/.../integration/zai/SpecificationOptimizer.java
src/.../integration/zai/ZaiClientFactory.java               # NEW (zai-sdk factory)
src/.../integration/zai/ZaiFunctionService.java             # migrated to zai-sdk
src/.../integration/zai/ZaiHttpClient.java                  # DELETED
src/.../integration/zai/ZaiService.java
```

---

## Validation Team (6 Specialists)

Each programmer validates their domain independently, then messages findings to lead.

---

### Programmer 1 — Java Compiler (`yawl-validator` agent)

**Domain**: JVM compilation, Maven build, classpath correctness

**Tasks**:
- Run `bash scripts/dx.sh all` → must be `BUILD SUCCESS` for all modules
- Run `bash scripts/dx.sh -pl yawl-integration` → targeted integration module build
- Verify `PerformanceAnalyzer.java` resolves `BottleneckDetector` from
  `org.yawlfoundation.yawl.observability` (shared `src/`) — not from a `yawl-monitoring` module
- Confirm `ZaiHttpClient.java` and `PinnedTrustManager.java` are deleted (no dangling imports
  anywhere referencing these classes)
- Check `yawl-integration/pom.xml` does NOT contain `yawl-monitoring` as a `<dependency>`
- Report: module compile status, any unresolved symbols, test count pass/fail

---

### Programmer 2 — Rust-style Code Reviewer (`yawl-reviewer` agent)

**Domain**: Ownership semantics, resource safety, HYPER_STANDARDS (H-guard enforcement)

**Tasks**:
- Run `bash .claude/hooks/hyper-validate.sh` on the full `src/org/yawlfoundation/yawl/integration/`
  tree — report any H-violations (TODO/mock/stub/fake/empty/silent-fallback)
- Read `ZaiClientFactory.java` — verify it builds a real client (not a stub), uses `zai-sdk` API
  correctly, no silent null returns
- Read `ZaiFunctionService.java` (first 80 lines) — confirm it no longer references the deleted
  `ZaiHttpClient` or `PinnedTrustManager`
- Read `HttpZaiMcpBridge.java` (first 60 lines) — confirm it properly implements `ZaiMcpBridge`
  interface (all methods real, no empty bodies)
- Read `Ocel2Exporter.java` — confirm it contains a real OCEL2 serialization implementation,
  not a stub
- Report: H-gate status (GREEN/RED), list any violations with file:line

---

### Programmer 3 — Python Specialist (`yawl-tester` agent)

**Domain**: Python integration, process mining, pm4py removal

**Tasks**:
- Confirm `Pm4PyClient.java` is gone: `ls src/.../orderfulfillment/Pm4PyClient.java` → should 404
- Confirm no remaining Java files reference `pm4py` or `Pm4Py`:
  `grep -r "pm4py\|Pm4Py" src/org/yawlfoundation/yawl/integration/ --include="*.java"`
  → must be 0 results
- Read `YawlProcessMiningToolSpecifications.java` (first 50 lines) — confirm the pm4py Python
  bridge calls have been replaced with pure-Java OCEL2/process-mining calls
- Verify the observatory script runs cleanly:
  `bash scripts/observatory/observatory.sh` → `STATUS=GREEN`
- Report: pm4py reference count, observatory status, any residual Python bridge code

---

### Programmer 4 — Shell/DevOps Specialist (`yawl-validator` agent, second instance)

**Domain**: pom.xml, Maven exclusions, build configuration

**Tasks**:
- Diff `yawl-integration/pom.xml` compiler exclusions: count how many `<exclude>` entries were
  removed vs how many remain
- Verify the 5 `<exclude>` entries that remain are legitimate (A2A classes still broken,
  `mcp/transport/**`, `conflict/**`, `mcp/event/**`) — not stale leftovers
- Confirm `zai-sdk 0.3.0` is added with correct Jackson exclusions to avoid version conflicts
- Confirm no test exclusions were removed that should have stayed:
  verify `SelfPlayTest`, `IntegrationTestSuite`, `V6EndToEndIntegrationTest` still excluded
- Check root `pom.xml` — confirm no new `<module>` entry for `yawl-monitoring` or
  `yawl-observability` (those must remain packages in shared `src/`, not standalone modules)
- Report: exclusion delta (+added / -removed), zai-sdk dep present (yes/no), root pom unchanged

---

### Programmer 5 — Docker/Infrastructure Specialist (`yawl-production-validator` agent)

**Domain**: Production readiness, security, TLS, secret handling

**Tasks**:
- Confirm `PinnedTrustManager.java` is deleted (it hard-coded TLS pins — security debt removed)
- Read `ZaiService.java` (first 60 lines) — confirm Z.AI API keys/tokens are read from
  environment variables or config, NOT hardcoded strings
- Read `ZaiClientFactory.java` — confirm the factory does not embed credentials or bypass TLS
- Scan for secrets: `grep -rn "api_key\|apiKey\|Bearer\|token.*=.*\"" src/.../integration/zai/`
  → should return 0 hardcoded secrets
- Check `HttpZaiMcpBridge.java` for any HTTP calls that bypass TLS (plain `http://` URLs for
  non-localhost targets)
- Report: secrets scan result, TLS posture (improved/regressed/neutral), production-ready flag

---

### Programmer 6 — ggen/Code Generation Specialist (`yawl-engineer` agent)

**Domain**: MCP SDK RC3 API correctness, spec tool fidelity, code generation patterns

**Tasks**:
- Read `ZaiBridgeToolRegistry.java` (first 80 lines) — verify it uses `CallToolResult` builder
  pattern from RC3 (not the old constructor), methods return real results
- Read `YawlToolSpecifications.java` (first 60 lines) — confirm tool schema definitions are
  complete (name, description, input schema) — not empty
- Read `CompleteWorkItemTool.java` — confirm it calls the correct InterfaceB engine API
  (`YInterface.completeWorkItem()` or equivalent), not a stub
- Read `GetWorkItemsTool.java` (first 50 lines) — confirm it retrieves real work items from
  the engine, handles empty result correctly (returns empty list, not null)
- Read `ExecuteClaudeTool.java` (first 50 lines) — confirm RC3 migration is complete:
  uses `McpSyncServer` API, no references to deleted RC2 types
- Report: RC3 migration completeness (all files / partial), any remaining RC2 API calls

---

## Consolidation Criteria

Lead consolidates only when ALL 6 programmers report:

| # | Domain | Gate |
|---|--------|------|
| 1 | Java | `BUILD SUCCESS`, 0 unresolved symbols |
| 2 | Rust-style review | H-gate GREEN, 0 violations |
| 3 | Python | 0 pm4py references, observatory GREEN |
| 4 | Shell/DevOps | Exclusion delta correct, no stale entries |
| 5 | Docker/Infra | 0 hardcoded secrets, TLS posture neutral/improved |
| 6 | ggen/MCP | RC3 migration complete, all tool specs real |

Any RED gate → programmer identifies fix, implements locally, re-runs `dx.sh -pl yawl-integration`.

---

## Deliverable

Lead commits a single `validate(6rfU3): all gates GREEN` entry to this branch after consolidation,
or files remediation commits if any gate is RED.

Branch: `claude/map-exclude-rules-6rfU3`

---

**Created**: 2026-02-23
**Author**: Lead session (session ID 6rfU3)
**Status**: Ready for validation team
