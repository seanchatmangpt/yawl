# ADR-021: Automatic Engine Selection for v6.0.0

## Status
**ACCEPTED**

## Context

ADR-001 (Dual Engine Architecture) established that YAWL maintains both a stateful
`YEngine` and a stateless `YStatelessEngine`. The decision flowchart in ADR-001
requires the operator to manually choose the appropriate engine at deployment time.

For v6.0.0, Phase 3 of the ADR-001 migration plan is due:
> **Phase 3 (v6.0):** Automatic engine selection based on specification hints

Currently, specifying which engine to use requires:
1. Deploying either the stateful WAR or the stateless JAR (separate artefacts)
2. Configuring environment variables explicitly
3. Updating client code to use the correct API endpoint

This creates friction for:
- **Developers** who want to test locally with the stateless engine but deploy with stateful
- **SaaS operators** who want to use the cheapest execution path per workflow type
- **CI/CD pipelines** that use the same specification in both ephemeral test environments
  and persistent production environments

## Decision

**YAWL v6.0.0 introduces specification-level engine hints and a runtime engine
selector that automatically routes case launches to the appropriate engine.**

### Specification-Level Engine Hint

A `<executionProfile>` element in the specification root signals the preferred execution model:

```xml
<specificationSet version="2.1" schemaVersion="6.0">
  <specification id="DataValidationPipeline">

    <!-- Engine selection hint (optional — defaults to stateful) -->
    <executionProfile>
      <preferred>stateless</preferred>
      <maxDuration>PT5M</maxDuration>
      <allowHumanTasks>false</allowHumanTasks>
      <fallbackToStateful>true</fallbackToStateful>
    </executionProfile>

    <!-- ... rest of specification ... -->
  </specification>
</specificationSet>
```

### Engine Selector Algorithm

At case launch time (`POST /ib/cases`), the `EngineSelector` evaluates:

```
1. Does the specification declare <executionProfile>?
   NO  → use stateful engine (safe default)
   YES → evaluate profile:

2. Does profile declare preferred=stateless?
   NO  → use stateful engine

3. Does the specification contain human tasks?
   YES AND allowHumanTasks=false → reject launch with clear error
   YES AND allowHumanTasks=true  → use stateful engine (human tasks require persistence)

4. Does the specification contain timer escalations?
   YES → use stateful engine (long-running timers require persistence)

5. Is the stateless engine available?
   NO AND fallbackToStateful=true  → use stateful engine (log warning)
   NO AND fallbackToStateful=false → reject launch with 503

6. → use stateless engine
```

The selector's decision is recorded in the launch audit log and returned in the
`CaseLaunchResponse`:

```json
{
  "caseId": "CASE-2026-042",
  "engineUsed": "stateless",
  "engineSelectionReason": "specification executionProfile preferred=stateless"
}
```

### Override at Launch Time

The caller can override the engine selection in the launch request:

```json
{
  "specIdentifier": "DataValidationPipeline",
  "caseData": "<data>...</data>",
  "engineOverride": "stateful"
}
```

Overrides are restricted to the `engine-admin` role (ADR-017: Authentication).
Service accounts and `workflow-user` roles cannot override the spec-declared preference.

### Configuration

```properties
# application.properties (v6)
yawl.engine-selector.stateless.enabled=true
yawl.engine-selector.stateless.max-duration-hint=PT5M
yawl.engine-selector.stateless.allow-override=true
yawl.engine-selector.default=stateful
```

The `max-duration-hint` configures what duration in `<maxDuration>` is considered
"suitable for stateless". Specifications declaring a duration longer than this hint
are redirected to the stateful engine.

### Mixed-Engine Case Monitoring

A unified `GET /ib/cases/{caseId}` endpoint works regardless of which engine is running
the case. The response includes an `engineMode` field:

```json
{
  "caseId": "CASE-2026-042",
  "status": "Executing",
  "engineMode": "stateless",
  "engineInstanceId": "node-2"
}
```

## Consequences

### Positive

1. Developers write specifications once and deploy to any engine mode
2. SaaS operators can reduce cost by routing high-volume, short-duration workflows
   to the stateless engine without code changes
3. CI/CD test environments can use stateless engine for faster, cheaper test runs
4. `fallbackToStateful=true` prevents operational failures when stateless engine
   is degraded

### Negative

1. Additional complexity in the case launch path (`EngineSelector` logic must be
   maintained as both engines evolve)
2. Specification `<executionProfile>` is a new hint that many existing specifications
   will not declare — they all default to stateful, which is correct but may surprise
   users who expected automatic optimisation
3. Mixed-engine deployments require both engines to be running simultaneously

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Selector routes long-running case to stateless engine | LOW | HIGH | `maxDuration` guard + human task detection prevent this |
| Specification's `maxDuration` hint is wrong | MEDIUM | MEDIUM | Engine logs warning if stateless case exceeds `maxDuration`; `fallbackToStateful` available |
| `engineOverride` misused to bypass governance | LOW | MEDIUM | Override restricted to `engine-admin` role; all overrides are audit-logged |

## Alternatives Considered

### Two Separate Deployments (Status Quo)
Keep stateful and stateless as separate deployments with separate URLs. Rejected:
this is the v5.x model and does not address the Phase 3 migration goal.

### Use Stateless for Everything, Add Persistence as a Plugin
A unified engine with a pluggable persistence backend that can be set to "none" for
stateless execution. Rejected for v6.0: the architectural refactoring required
(shared `YNetRunner` with abstracted persistence) is the v7.0 target.

### Performance-Based Routing
Route based on real-time engine load: use stateless when stateful engine queue exceeds
a threshold. Rejected: this violates the separation of concerns principle. Engine
selection should be based on functional requirements (human tasks, persistence needs)
not operational load. Load-based routing is a separate concern handled by the load
balancer (ADR-014: Clustering).

## Related ADRs

- ADR-001: Dual Engine Architecture (this ADR implements Phase 3 of the migration plan)
- ADR-013: Schema Versioning (`<executionProfile>` is a v6.0 schema element)
- ADR-014: Clustering (both engine types can run in clustered mode)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
