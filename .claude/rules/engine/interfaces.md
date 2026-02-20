---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/engine/interfac*/**"
  - "*/src/test/java/org/yawlfoundation/yawl/engine/interfac*/**"
---

# YAWL Interface Rules

## Interface A (Administration/Design)
- Design-time operations: load/unload specifications, manage cases
- Entry: `InterfaceA_EnvironmentBasedClient`
- URL pattern: `/yawl/ia`
- Used by: YAWL Editor, admin tools

## Interface B (Client/Runtime)
- Runtime operations: launch cases, check out/in work items, get work queues
- Entry: `InterfaceB_EnvironmentBasedClient`
- URL pattern: `/yawl/ib`
- Used by: Custom services, resource service, autonomous agents
- CQRS candidate: split into commands (launchCase, completeWorkItem) and queries (getAvailableWorkItems)

## Interface E (Events/Logging)
- Event notifications: case state changes, work item lifecycle events
- Entry: `InterfaceE_EnvironmentBasedClient`
- Used by: Monitoring, audit logging, external event consumers

## Interface X (Extended/Exception)
- Exception handling: worklet selection, compensation, escalation
- Entry: `InterfaceX_EnvBasedClient`
- Used by: Worklet service, exception handlers

## Contract Rules
- Never break existing interface method signatures (backward compatibility)
- New operations extend interfaces, never modify existing signatures
- All interface methods must handle session authentication
- Return XML-formatted responses (YAWL's native wire format)
