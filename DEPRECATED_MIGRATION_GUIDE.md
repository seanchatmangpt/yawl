# YAWL Deprecated API Migration Guide

**Audit Date:** 2026-02-17  
**Auditor:** YAWL Code Reviewer  
**Total @Deprecated annotations found:** 70 (across 38 distinct deprecated items)  
**Files containing deprecated usage:** 25+ production files

---

## Executive Summary

The YAWL codebase contains four distinct categories of deprecated code:

| Category | Items | Urgency |
|---|---|---|
| Record conversion legacy getters (TaskInformation, YLogDataItem) | 12 methods | Remove in next release |
| procletService Entity accessor legacy getters | 8 methods in 12 files | CRITICAL: one call site triggers UnsupportedOperationException at runtime |
| MCP stub package (pending real SDK) | 7 classes / 37 imports | Longer transition - SDK-dependent |
| Abandoned technology (WSIF) | 2 classes | Remove immediately (no production callers) |
| Orderfulfillment-specific deprecated classes | 4 classes | Remove after confirming workload migration |
| Miscellaneous (HibernateEngine.getByCriteria, MailSettings.copyOf) | 2 methods | Low risk; straightforward removal |

---

## CRITICAL: Runtime Bug - EntitySID.setEsid() Call

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/blockType/BlockCP.java:439`

```java
arc.getEntityID().getEsid().setEsid("5");
```

`EntitySID.setEsid()` is annotated `@Deprecated` and its body throws `UnsupportedOperationException`:

```java
@Deprecated
public void setEsid(String newEsid) {
    throw new UnsupportedOperationException(
        "EntitySID is immutable. Create a new instance: new EntitySID(\"" + newEsid + "\")"
    );
}
```

**This call will crash at runtime.** Fix: replace with a new entity construction or with a mutable field on the containing arc object.

---

## Category 1: Java Record Conversion Legacy Getters

These classes were converted from mutable POJOs to Java records in YAWL 5.2. Legacy JavaBean getters (`getXxx()`) were kept only for backward compatibility and delegate directly to the record component accessor (`xxx()`).

### TaskInformation (7 deprecated methods)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/TaskInformation.java`

| Deprecated Method | Replacement | Active Callers |
|---|---|---|
| `getParamSchema()` | `paramSchema()` | 3 files |
| `getTaskID()` | `taskID()` | 0 (other classes named similarly) |
| `getSpecificationID()` | `specificationID()` | 0 (other classes named similarly) |
| `getTaskDocumentation()` | `taskDocumentation()` | 0 |
| `getTaskName()` | `taskName()` | 0 |
| `getDecompositionID()` | `decompositionID()` | 0 |
| `getAttributes()` | `attributes()` | 0 |

**Active callers of `getParamSchema()`:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceBWebsideController.java:430`
- `/home/user/yawl/src/org/yawlfoundation/yawl/swingWorklist/YWorklistModel.java:405`
- `/home/user/yawl/src/org/yawlfoundation/yawl/wsif/WSIFController.java:86` (deprecated class itself)

**Migration:** Replace `taskInfo.getParamSchema()` with `taskInfo.paramSchema()` at each call site.

### YLogDataItem (5 deprecated methods)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/logging/YLogDataItem.java`

| Deprecated Method | Replacement | Active Callers |
|---|---|---|
| `getName()` | `name()` | 1 (YLogDataItemInstance.java:81) |
| `getValue()` | `value()` | 1 (YLogDataItemInstance.java:89) |
| `getDataTypeName()` | `dataTypeName()` | 1 (YLogDataItemInstance.java:97) |
| `getDataTypeDefinition()` | `dataTypeDefinition()` | 0 |
| `getDescriptor()` | `descriptor()` | 1 (YLogDataItemInstance.java:105) |

**Active callers all in:**
`/home/user/yawl/src/org/yawlfoundation/yawl/logging/table/YLogDataItemInstance.java`

**Migration:** In `YLogDataItemInstance`, replace each `dataItem.getXxx()` call with `dataItem.xxx()`.

---

## Category 2: procletService Entity Accessor Legacy Getters

These classes were converted to be immutable but retain both deprecated getters and deprecated mutating methods.

### EntityID (2 deprecated methods)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/util/EntityID.java`

| Deprecated Method | Replacement | Active Callers |
|---|---|---|
| `getEmid()` | `emid()` field access | 7+ sites across 4 files |
| `getEsid()` | `esid()` field access | 5+ sites across 4 files |

### EntityMID (2 deprecated methods)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/util/EntityMID.java`

| Deprecated Method | Notes | Active Callers |
|---|---|---|
| `getValue()` | Returns the emid string; use `emid()` or `toString()` | 15+ sites |
| `setEmid(String)` | THROWS UnsupportedOperationException - immutable | 0 direct calls found |

### EntitySID (2 deprecated methods)

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/util/EntitySID.java`

| Deprecated Method | Notes | Active Callers |
|---|---|---|
| `getValue()` | Returns the esid string; use `esid()` or `toString()` | 10+ sites |
| `setEsid(String)` | THROWS UnsupportedOperationException - immutable | **1 call: BlockCP.java:439** |

**Affected files (12 total):**
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/blockType/BlockCP.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/blockType/BlockPI.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/blockType/BlockPICreate.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/blockType/CompleteCaseDeleteCase.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/editor/block/FrmBlock.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/editor/model/BlockEditFrame.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/interactionGraph/InteractionGraph.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/interactionGraph/InteractionGraphs.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/persistence/StoredInteractionArc.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/persistence/StoredItem.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/state/Performative.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/procletService/state/Performatives.java`

**Migration steps:**
1. Fix `BlockCP.java:439` immediately - replace `arc.getEntityID().getEsid().setEsid("5")` with a proper EntityID/EntitySID reconstruction.
2. Replace `.getEmid()` with `.emid()` throughout all 12 files.
3. Replace `.getEsid()` with `.esid()` throughout all 12 files.
4. Replace `emid.getValue()` / `esid.getValue()` with `emid.emid()` / `esid.esid()` or `emid.toString()`.

---

## Category 3: MCP Stub Package (Pending Official SDK)

**Package:** `org.yawlfoundation.yawl.integration.mcp.stub`  
**Files:** 7 deprecated stub classes / 1 package-info  
**Reason:** The official `io.modelcontextprotocol.sdk:mcp-core` Java SDK was not yet on Maven Central when this integration was written. The stubs allow compilation but all constructor/factory calls throw `UnsupportedOperationException`.

**Deprecated stub classes:**
- `JacksonMcpJsonMapper` - constructor throws `UnsupportedOperationException`
- `McpSchema` - data model classes (fully implemented, safe to use at compile time)
- `McpServer` - `sync()` factory throws `UnsupportedOperationException`
- `McpServerFeatures` - feature specification holders (compile-safe)
- `McpSyncServer` - interface only (compile-safe)
- `McpSyncServerExchange` - interface only (compile-safe)
- `StdioServerTransportProvider` - constructor throws `UnsupportedOperationException`
- `ZaiFunctionService` - constructor and `processWithFunctions` throw `UnsupportedOperationException`

**Callers (15 non-stub files that import from this package):**
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/logging/McpLoggingHandler.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/resource/YawlResourceProvider.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/server/YawlServerCapabilities.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlCompletionSpecifications.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlPromptSpecifications.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpConfiguration.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpResource.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpResourceRegistry.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpSpringApplication.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpTool.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpToolRegistry.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/resources/SpecificationsResource.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/tools/LaunchCaseTool.java`

**Migration steps (when official SDK is available):**
1. Add `io.modelcontextprotocol.sdk:mcp-core` and `io.modelcontextprotocol.sdk:mcp-json-jackson2` to `yawl-integration/pom.xml`.
2. Remove the compiler exclusion for the mcp package in `yawl-integration/pom.xml`.
3. Delete the entire `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/stub/` directory.
4. Fix any API incompatibilities between the stub API and the official SDK API in the 15 caller files.
5. Migrate `ZaiFunctionService` usage in `YawlToolSpecifications.java` and `YawlMcpConfiguration.java` to the real Z.AI integration package.

**SDK reference:** https://github.com/modelcontextprotocol/java-sdk

---

## Category 4: Abandoned Technology - Apache WSIF

**Files:**
- `/home/user/yawl/src/org/yawlfoundation/yawl/wsif/WSIFController.java` - `@Deprecated(since="5.2", forRemoval=true)`
- `/home/user/yawl/src/org/yawlfoundation/yawl/wsif/WSIFInvoker.java` - `@Deprecated(since="5.2", forRemoval=true)`

**Reason:** Apache WSIF was abandoned in 2007 and has known security vulnerabilities. Both classes are explicitly marked `forRemoval=true` since YAWL 5.2.

**Production callers:** NONE. `WSIFController.handleEnabledWorkItemEvent()` already throws `UnsupportedOperationException`.

**Test callers:** `/home/user/yawl/test/org/yawlfoundation/yawl/wsif/TestWSIFInvoker.java` - attempts network calls to `xmethods.net` (defunct service). This test should be deleted.

**Migration:** Use Jakarta JAX-WS (`jakarta.xml.ws.*`) or modern REST APIs. See https://eclipse-ee4j.github.io/metro-jax-ws/

---

## Category 5: Orderfulfillment-Specific Deprecated Classes

All four classes in the `orderfulfillment` package are deprecated with explicit replacements documented in their Javadoc.

| Deprecated Class | Replacement | External Callers |
|---|---|---|
| `OrderfulfillmentLauncher` | `GenericWorkflowLauncher` in `autonomous.launcher` | 0 (main() entry only) |
| `DecisionWorkflow` | `ZaiDecisionReasoner` in `autonomous.reasoners` | 1 (PartyAgent, same package) |
| `EligibilityWorkflow` | `ZaiEligibilityReasoner` in `autonomous.reasoners` | 1 (PartyAgent, same package) |
| `PartyAgent` | `GenericPartyAgent` in `autonomous` package | 0 (main() entry only) |

**Note:** The `autonomous` package replacements already exist and are production-ready. The `orderfulfillment` package is self-contained. The entire package can be removed once deployment scripts referencing `OrderfulfillmentLauncher.main()` or `PartyAgent.main()` are updated to use the generic alternatives.

---

## Category 6: Miscellaneous

### HibernateEngine.getByCriteria()

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java:438`  
**Reason:** Hibernate 6 migration removed the Criteria API; replaced with JPA Criteria API.  
**Replacement:** `getByCriteriaJPA(Class, boolean, Predicate...)`  
**Callers:** 0 (delegating wrapper only - no external callers found).  
**Migration:** Delete the wrapper method. It is a one-liner that delegates to `getByCriteriaJPA`.

### MailSettings.copyOf()

**File:** `/home/user/yawl/src/org/yawlfoundation/yawl/util/MailSettings.java:90`  
**Reason:** `MailSettings` is now a Java record; records are inherently immutable so copyOf() returns `this`.  
**Replacement:** Use the `MailSettings` instance directly.  
**Callers:** 1 - `/home/user/yawl/src/org/yawlfoundation/yawl/mailService/MailService.java:149`  
**Migration:** In `MailService.java:149`, change `MailSettings settings = _defaults.copyOf();` to `MailSettings settings = _defaults;` and remove the `copyOf()` method.

---

## Proposed First-Stage Removal List

The following deprecated items have zero external production callers and can be removed immediately in a single PR without touching any caller code:

### Stage 1 PR: "Remove zero-usage deprecated code"

**Files to delete entirely:**
1. `/home/user/yawl/src/org/yawlfoundation/yawl/wsif/WSIFController.java`
2. `/home/user/yawl/src/org/yawlfoundation/yawl/wsif/WSIFInvoker.java`
3. `/home/user/yawl/test/org/yawlfoundation/yawl/wsif/TestWSIFInvoker.java`
4. `/home/user/yawl/src/org/yawlfoundation/yawl/integration/orderfulfillment/OrderfulfillmentLauncher.java`

**Methods to delete:**
5. `TaskInformation.getTaskDocumentation()` (0 callers)
6. `TaskInformation.getTaskName()` (0 callers)
7. `TaskInformation.getDecompositionID()` (0 callers)
8. `TaskInformation.getAttributes()` (0 callers)
9. `WorkItemRecord.getFormattedDate(String)` (private - 0 external callers)
10. `InterfaceBWebsideController.getTaskInformation(String, String, String)` (0 callers)
11. `InterfaceB_EnvironmentBasedClient.getTaskInformationStr(String, String, String)` (0 callers)
12. `YLogDataItem.getDataTypeDefinition()` (0 callers)
13. `HibernateEngine.getByCriteria(Class, Predicate...)` (0 callers)

**One-line caller fixes required for immediate removal:**
14. `MailSettings.copyOf()` - change `MailService.java:149` from `_defaults.copyOf()` to `_defaults`, then delete `copyOf()`.

### Stage 2 PR: "Migrate record accessor callers"

Update all call sites in `YLogDataItemInstance.java`, `InterfaceBWebsideController.java`, `YWorklistModel.java` to use record accessors, then remove the remaining deprecated getters on `TaskInformation` and `YLogDataItem`.

### Stage 3 PR: "Fix procletService Entity accessor runtime bug and migrate getters"

Fix the `BlockCP.java:439` runtime crash first, then migrate all 12 proclet files to use record accessors, then remove deprecated getters from `EntityID`, `EntityMID`, `EntitySID`.

### Stage 4 PR: "Remove orderfulfillment package"

After confirming deployment scripts use `GenericWorkflowLauncher` and `GenericPartyAgent`, delete the entire `orderfulfillment` package.

### Stage 5 PR: "Migrate to official MCP SDK"

When `io.modelcontextprotocol.sdk:mcp-core` is available on Maven Central, replace all 7 stub classes and update all 15 caller files.

---

## Severity Summary

| Priority | Items | Action |
|---|---|---|
| IMMEDIATE (runtime crash) | `EntitySID.setEsid()` call in `BlockCP.java:439` | Fix before next deployment |
| CAN_REMOVE_NOW (Stage 1) | WSIFController, WSIFInvoker, 0-caller deprecated methods, MailSettings.copyOf() | Stage 1 PR |
| NEXT_RELEASE (Stage 2-4) | Record accessor getters, proclet Entity getters, orderfulfillment package | Planned PRs |
| LONGER_TRANSITION (Stage 5) | MCP stub package | Blocked on official SDK availability |
