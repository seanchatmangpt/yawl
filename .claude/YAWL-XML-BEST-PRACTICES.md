# YAWL XML Best Practices for Production Deployment

**Prepared for Wil Van Der Aalst** | YAWL v6.0.0-Alpha | February 2026

This document establishes best practices for YAWL XML specifications based on rigorous Petri net semantics, formal verification, and production deployment experience.

---

## 1. SEMANTIC COMPLETENESS

### 1.1 Liveness (No Deadlocks)

Every transition must be reachable from the input condition and able to reach an output condition.

**Rule**: For every transition T in the net:
- ∃ path: InputCondition → ... → T
- ∃ path: T → ... → OutputCondition

**Example - Compliant**:
```xml
<inputCondition id="Start">
  <flowsInto><nextElementRef id="Task1"/></flowsInto>
</inputCondition>

<task id="Task1">
  <flowsInto><nextElementRef id="Task2"/></flowsInto>
  <join code="xor"/><split code="xor"/>
</task>

<task id="Task2">
  <flowsInto><nextElementRef id="End"/></flowsInto>
  <join code="xor"/><split code="xor"/>
</task>

<outputCondition id="End"/>
```

**Example - Non-Compliant (Dead Task)**:
```xml
<!-- FORBIDDEN: OrphanTask never reached -->
<task id="OrphanTask">
  <!-- No incoming arc! -->
  <flowsInto><nextElementRef id="End"/></flowsInto>
</task>
```

### 1.2 Boundedness (Memory Safety)

No place should accumulate unlimited tokens.

**Rule**: For every place P:
- max_tokens(P) must be bounded and documented
- AND-splits must always have matching AND-joins
- Loops must have termination conditions

**Example - Compliant**:
```xml
<!-- AND-split: 1 token → 2 tokens -->
<condition id="Fork">
  <flowsInto><nextElementRef id="Task1"/></flowsInto>
  <flowsInto><nextElementRef id="Task2"/></flowsInto>
</condition>

<task id="Task1">
  <flowsInto><nextElementRef id="Join"/></flowsInto>
</task>

<task id="Task2">
  <flowsInto><nextElementRef id="Join"/></flowsInto>
</task>

<!-- AND-join: 2 tokens → 1 token (balanced!) -->
<condition id="Join">
  <flowsInto><nextElementRef id="NextTask"/></flowsInto>
</condition>
```

**Example - Non-Compliant (Unbounded)**:
```xml
<!-- FORBIDDEN: Fork without matching join -->
<condition id="Fork">
  <flowsInto><nextElementRef id="Task1"/></flowsInto>
  <flowsInto><nextElementRef id="Task2"/></flowsInto>
</condition>

<task id="Task1">
  <flowsInto><nextElementRef id="End"/></flowsInto>
</task>

<task id="Task2">
  <flowsInto><nextElementRef id="End"/></flowsInto>
  <!-- No AND-join! Unbounded token accumulation -->
</task>
```

### 1.3 Proper Termination

All execution paths must reach an output condition (no hanging cases).

**Rule**:
- All output conditions must be terminal (no outgoing arcs)
- All refusal codes must have defined exception handlers
- No circular paths (except loops with bounds)

**Example - Compliant**:
```xml
<condition id="DecisionPoint">
  <!-- Exactly one output per execution path -->
  <flowsInto>
    <nextElementRef id="Success"/>
    <predicate>status == "OK"</predicate>
  </flowsInto>
  <flowsInto>
    <nextElementRef id="Failure"/>
    <predicate>status == "FAILED"</predicate>
  </flowsInto>
</condition>

<outputCondition id="Success"/> <!-- Terminal -->
<outputCondition id="Failure"/> <!-- Terminal -->
```

---

## 2. SYNTACTIC COMPLIANCE

### 2.1 XSD Validation

Every YAWL specification must validate against YAWL_Schema4.0.xsd.

```bash
# Validate at commit time
xmllint --schema schema/YAWL_Schema4.0.xsd my-spec.yawl.xml --noout

# Expected output: my-spec.yawl.xml validates
```

**Required Elements** (per YAWL 4.0):
```xml
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification>
    <metaData>
      <title/>        <!-- REQUIRED: Non-empty -->
      <identifier/>   <!-- REQUIRED: Unique within document -->
      <version/>      <!-- REQUIRED: SemVer recommended -->
      <documentation/> <!-- RECOMMENDED: Complex nets -->
    </metaData>

    <decomposition id="net-id" isRootNet="true">
      <processControlElements>
        <inputCondition id="InputCondition"/>   <!-- REQUIRED -->
        <!-- Tasks, conditions, etc. -->
        <outputCondition id="OutputCondition"/> <!-- REQUIRED -->
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
```

### 2.2 Naming Conventions

- **Elements**: CamelCase, descriptive (e.g., `DeployToAWS`, `HealthCheck`)
- **IDs**: Alphanumeric, no spaces (e.g., `task-deploy-aws-01`)
- **Variables**: camelCase, type-prefixed where useful (e.g., `cloudStatus`, `deploymentAttempt`)

---

## 3. PETRI NET SOUNDNESS VERIFICATION

### 3.1 Soundness = Liveness + Boundedness + Proper Completion

A sound YAWL net guarantees:
1. No deadlocks (liveness)
2. No unbounded token accumulation (safety)
3. All cases reach a terminal state (progress)

### 3.2 Automated Soundness Check

```java
public class PetriNetSoundnessVerifier {
    public SoundnessReport verifySoundness(YSpecification spec) {
        LivenessReport liveness = analyzer.checkLiveness();
        if (!liveness.isLive()) {
            return SoundnessReport.unsound("Dead transitions: " + liveness.getDeadTransitions());
        }

        BoundednessReport boundedness = analyzer.checkBoundedness();
        if (boundedness.isUnbounded()) {
            return SoundnessReport.unsound("Unbounded places: " + boundedness.getUnboundedPlaces());
        }

        TerminationReport termination = analyzer.checkProperCompletion();
        if (!termination.isProperlyTerminating()) {
            return SoundnessReport.unsound("Non-terminating paths detected");
        }

        return SoundnessReport.sound("Specification is sound");
    }
}
```

### 3.3 Manual Verification Steps

1. **List all transitions**: Extract T0, T1, ..., Tn
2. **For each transition**:
   - Trace backward to InputCondition
   - Trace forward to OutputCondition
   - If either path missing → dead transition
3. **Check all AND-joins**: Verify matching AND-split exists
4. **Test with sample data**: Execute on engine to detect hidden deadlocks

---

## 4. AGENT COORDINATION (μ-monoid)

### 4.1 Agent Role Mapping

| Agent Role | Task Type | Responsibility |
|-----------|-----------|-----------------|
| `engineer` | Automated | Code implementation, infrastructure deployment |
| `validator` | Automated | Build verification, XSD validation |
| `architect` | Human | Design reviews, architectural decisions |
| `integrator` | Automated | Coordinate subsystems, manage dependencies |
| `reviewer` | Human | Code quality, security review |
| `tester` | Automated | Test execution, coverage analysis |
| `prod-val` | Automated | Production health checks, incident response |
| `perf-bench` | Automated | Performance measurement, load testing |

### 4.2 Task Allocation Rules

```xml
<task id="DeployToAWS">
  <name>Deploy to AWS Multi-Region</name>

  <!-- Allocate by role and capability -->
  <allocation>
    <allocateToRole>
      <role id="engineer"/>
      <capability id="aws-deployment"/>
      <skillLevel>expert</skillLevel>
    </allocateToRole>
  </allocation>

  <!-- Refusal codes for exception handling -->
  <refusalCodeList>
    <refusal>AWS_INSUFFICIENT_CAPACITY</refusal>
    <refusal>AWS_INVALID_CREDENTIALS</refusal>
    <refusal>AWS_QUOTA_EXCEEDED</refusal>
  </refusalCodeList>
</task>
```

### 4.3 Work Item Completion Semantics

**Success Path**:
```xml
<workItemCompletion>
  <completionType>normal_completion</completionType>
  <status>COMPLETED</status>
  <data>
    <item key="deploymentStatus">SUCCESS</item>
    <item key="timestamp">2026-02-18T14:32:51Z</item>
  </data>
</workItemCompletion>
```

**Failure Path (Refusal)**:
```xml
<workItemRefusal>
  <refusalCode>AWS_INSUFFICIENT_CAPACITY</refusalCode>
  <reason>No capacity in us-east-1 for i3en.3xlarge instance</reason>
  <context>
    <item key="failedCloud">aws</item>
    <item key="failureTime">2026-02-18T14:15:22Z</item>
  </context>
</workItemRefusal>
```

---

## 5. EXCEPTION HANDLING & COMPENSATION

### 5.1 Refusal Code Semantics

Every task must explicitly document refusal codes:

```xml
<task id="ValidateCredentials">
  <documentation>
    Refusal Codes (Exception Handling):
    - INVALID_CREDENTIALS: Auth failed for cloud
    - REGION_UNAVAILABLE: Requested region capacity exceeded
    - QUOTA_EXCEEDED: Account quota limits reached

    Recovery Path:
    - Refusal → Manual remediation → Workflow restart
    - No automatic retry (human judgment required)
  </documentation>

  <refusalCodeList>
    <refusal>INVALID_CREDENTIALS</refusal>
    <refusal>REGION_UNAVAILABLE</refusal>
    <refusal>QUOTA_EXCEEDED</refusal>
  </refusalCodeList>
</task>
```

### 5.2 Compensating Transactions (Sagas Pattern)

For critical atomic sections, define compensating actions:

```xml
<task id="TriggerRollback">
  <documentation>
    Compensation Strategy (Sagas Pattern):

    Compensating Actions (Execute in Reverse Order):
    Order 5: NotifyAgents (inform agents of rollback)
    Order 4: RevertDNSRecords (restore previous endpoints)
    Order 3: InvalidateCDN (clear cache)
    Order 2: RestoreDatabaseSnapshots (rollback databases)
    Order 1: DestroyNewComputeResources (terminate instances)

    Idempotency Guarantee:
    - Each action safe to run multiple times
    - Destroy operations check existence first
    - Database restore uses snapshot IDs (non-destructive)

    Atomicity Guarantee:
    - Either all 6 clouds rollback or none
    - No partial rollback (prevents inconsistent state)
  </documentation>

  <compensatingActions>
    <action id="C5_NotifyAgents">
      <order>5</order>
      <description>Notify all agents of rollback event</description>
    </action>
    <action id="C4_RevertDNS">
      <order>4</order>
      <description>Revert DNS records to previous endpoints</description>
    </action>
    <action id="C3_InvalidateCDN">
      <order>3</order>
      <description>Invalidate CloudFront distribution</description>
    </action>
    <action id="C2_RestoreDB">
      <order>2</order>
      <description>Restore RDS from previous snapshot</description>
    </action>
    <action id="C1_DestroyEC2">
      <order>1</order>
      <description>Terminate EC2 instances in all regions</description>
    </action>
  </compensatingActions>
</task>
```

---

## 6. DOCUMENTATION STANDARDS

### 6.1 Specification-Level Documentation

```xml
<specification uri="MySpecification">
  <metaData>
    <title>Workflow Title</title>
    <version>1.0.0</version>
    <documentation>
      PURPOSE:
      Brief description of workflow goals and business value.

      PETRI NET PROPERTIES:
      - Liveness: Verified (no dead transitions)
      - Boundedness: Max 6 concurrent tokens
      - Proper Termination: All paths lead to OutputCondition
      - Soundness: Verified via automated analysis

      ARCHITECTURE PATTERNS:
      1. Virtual Thread Per Case
      2. Structured Concurrency with ShutdownOnFailure
      3. Sealed Task Hierarchy (exhaustive pattern matching)
      4. CQRS Split (commands/queries)
      5. Scoped Values (context propagation)
      6. Compensating Transactions (atomic rollback)

      AGENT COORDINATION:
      μ = {engineer, validator, prod-val, reviewer}
      - engineer: Infrastructure deployment
      - validator: Build artifact verification
      - prod-val: Health checks, incident response
      - reviewer: Deployment approval
    </documentation>
  </metaData>
</specification>
```

### 6.2 Complex Gate Documentation

All AND-splits, XOR gates, and composite tasks must be documented:

```xml
<task id="DeploymentFork">
  <documentation>
    TASK DESCRIPTION (AND-split - Parallel Deployment)

    Purpose: Deploy YAWL v6 to all 6 clouds in parallel

    Petri Net Semantics:
    - Transition: Place "CloudReady" → 6 output places
    - Fire Rule: When token in CloudReady, distribute to all 6
    - Guarantee: All 6 must complete before AND-join

    Concurrency Model:
    - StructuredTaskScope.ShutdownOnFailure
    - Virtual thread per cloud
    - If any cloud fails, cancel remaining deployments
    - Atomicity: All-or-nothing (no partial deployments)

    Agent Assignment:
    - Role: engineer
    - Capability: cloud-deployment
    - Skill Level: expert

    Timeout: PT30M

    Refusal Codes:
    - CLOUD_DEPLOYMENT_TIMEOUT: Exceeded 30 minutes
    - CLOUD_INSUFFICIENT_CAPACITY: No capacity in region
  </documentation>
</task>
```

---

## 7. JAVA 25 INTEGRATION

### 7.1 Virtual Thread Per Case Pattern

```java
public class YNetRunnerVirtualThread {
    /**
     * Execute workflow case on dedicated virtual thread.
     *
     * Benefits:
     * - 1M concurrent cases with minimal memory (1KB each)
     * - Automatic OS scheduling, no thread pool limits
     * - Deadlock-free: each case has isolated context
     */
    public void executeCase(YIdentifier caseId) {
        Thread.ofVirtual()
            .name("case-" + caseId)
            .start(() -> {
                try {
                    YNetRunner runner = engine.getNetRunner(caseId);
                    while (!runner.isComplete()) {
                        runner.continueIfPossible();
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    announcer.announceCaseException(caseId, e);
                }
            });
    }
}
```

### 7.2 Structured Concurrency (ShutdownOnFailure)

```java
public class MultiCloudDeployer {
    /**
     * Deploy to all clouds with automatic cancellation on failure.
     *
     * Guarantees:
     * 1. All subtasks complete or all are cancelled
     * 2. No resource leaks (automatic cleanup)
     * 3. Exceptions propagate correctly
     */
    public DeploymentResults deployAllClouds(List<CloudTask> tasks) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Map<String, Subtask<DeploymentResult>> cloudTasks =
                tasks.stream()
                    .collect(Collectors.toMap(
                        CloudTask::cloudName,
                        task -> scope.fork(() -> deployToCloud(task))
                    ));

            scope.join();          // Wait for all
            scope.throwIfFailed(); // Propagate first failure

            return new DeploymentResults(
                cloudTasks.entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().resultNow()
                    )),
                DeploymentStatus.SUCCESS
            );
        } catch (ExecutionException e) {
            announcer.announceDeploymentFailure(e.getCause().getMessage());
            throw new DeploymentException(e.getCause());
        }
    }
}
```

### 7.3 Sealed Task Executor Hierarchy

```java
/**
 * Sealed class hierarchy for task execution (Java 25).
 *
 * Ensures compiler-verified exhaustiveness for all task types.
 */
public sealed interface TaskExecutor
    permits HumanTaskExecutor, AutomatedTaskExecutor,
            CompositeTaskExecutor, MultiInstanceTaskExecutor {}

public final class AutomatedTaskExecutor implements TaskExecutor {
    private final YTask task;

    public void execute(YWorkItem workItem) throws InterruptedException {
        Thread.ofVirtual()
            .name("task-" + task.getID())
            .start(() -> {
                try {
                    String result = service.execute(workItem.getDataList());
                    engine.completeWorkItem(workItem, result);
                } catch (Exception e) {
                    engine.refuseWorkItem(workItem, e.getMessage());
                }
            });
    }
}
```

---

## 8. DEFINITION OF DONE FOR YAWL SPECIFICATIONS

### Checklist for Production Deployment

- [ ] **Semantic Completeness**
  - [ ] Liveness verified: All transitions reachable
  - [ ] Boundedness verified: Max tokens per place calculated
  - [ ] Proper termination: All paths reach output condition
  - [ ] No deadlocks detected (simulation tested)

- [ ] **Syntactic Compliance**
  - [ ] XSD validates: `xmllint --schema YAWL_Schema4.0.xsd`
  - [ ] Unique IDs within specification
  - [ ] All required attributes present (id, name, decomposesTo)
  - [ ] Valid XML (well-formed, correct namespaces)

- [ ] **Petri Net Soundness**
  - [ ] Formal verification run (automated tool)
  - [ ] Liveness: No dead transitions
  - [ ] Safety: No unbounded places
  - [ ] Proper completion: All paths to output conditions

- [ ] **Agent Coverage**
  - [ ] All tasks assigned to roles (100% coverage)
  - [ ] Skill requirements defined (expert, intermediate, novice)
  - [ ] Role-based routing documented
  - [ ] Fallback agents for unavailable roles

- [ ] **Exception Handling**
  - [ ] All refusal codes documented
  - [ ] Compensating transactions defined for atomic sections
  - [ ] Retry logic specified (backoff, max retries)
  - [ ] Exception paths lead to terminal states
  - [ ] No silent failures (all errors reported)

- [ ] **Documentation**
  - [ ] Purpose statement in specification metadata
  - [ ] All complex gates documented (AND-split, XOR, etc.)
  - [ ] Petri net properties documented
  - [ ] Task descriptions reference refusal codes
  - [ ] Compensating transactions documented
  - [ ] Agent routing rules documented

- [ ] **Testing**
  - [ ] Happy path execution tested
  - [ ] ≥3 failure scenarios tested (timeout, partial failure, cascading)
  - [ ] Recovery path (rollback) tested
  - [ ] Performance validated (throughput, latency SLA)
  - [ ] Load test with concurrent cases

- [ ] **Deployment Readiness**
  - [ ] Version matches release version (SemVer)
  - [ ] Unique URI (no conflicts)
  - [ ] External service endpoints specified
  - [ ] Timeout values realistic for production
  - [ ] Capacity limits documented
  - [ ] Monitoring/alerting hooks defined

---

## 9. REFERENCES

- **YAWL Schema**: `schema/YAWL_Schema4.0.xsd`
- **Architecture Patterns**: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- **Java 25 Features**: `.claude/JAVA-25-FEATURES.md`
- **Best Practices**: `.claude/BEST-PRACTICES-2026.md`
- **Multi-Cloud Spec**: `docs/v6/latest/specs/v6-release-deployment-multicloud.yawl.xml`

---

## Conclusion

This document establishes production-ready standards for YAWL specifications. By following these practices:
1. Soundness is verified (no deadlocks, unbounded growth, or incomplete termination)
2. Agent coordination is explicit and role-based
3. Exception handling is comprehensive and documented
4. Java 25 modern concurrency patterns are leveraged
5. Specifications are ready for review by formal methods experts

Ready to present to Wil Van Der Aalst.
