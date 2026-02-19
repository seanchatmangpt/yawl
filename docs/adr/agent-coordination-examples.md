# Agent Coordination Examples

**Practical examples showing all features of the ADR-025 agent coordination implementation**

## Quick Start

```bash
# Start with these examples to understand agent coordination
./examples/agent-coordination/quick-start.sh
```

## Example 1: Basic Multi-Agent Setup

### Configuration

Create multiple agents with different capabilities:

```yaml
# agent-1-config.yaml
agent:
  id: "reviewer-general-001"
  name: "General Document Reviewer"
  port: 8081
  capability:
    domain: "document-processing"
    skills:
      - "document-review"
      - "content-analysis"
    constraints:
      documentTypes: ["PDF", "DOCX", "TXT"]
      maxPages: 100

# agent-2-config.yaml
agent:
  id: "reviewer-specialized-002"
  name: "Specialized Legal Reviewer"
  port: 8082
  capability:
    domain: "document-processing"
    skills:
      - "legal-document-review"
      - "contract-analysis"
    constraints:
      documentTypes: ["PDF"]
      maxPages: 50
      priority: "high"

# agent-3-config.yaml
agent:
  id: "reviewer-quality-003"
  name: "Quality Assurance Reviewer"
  port: 8083
  capability:
    domain: "document-processing"
    skills:
      - "quality-review"
      - "error-detection"
    constraints:
      documentTypes: ["DOCX", "TXT"]
      maxPages: 200
```

### Java Implementation

```java
// MultiAgentOrchestrator.java
public class MultiAgentOrchestrator {

    public static void main(String[] args) {
        // Start multiple agents
        List<Agent> agents = startAgents();

        // Create workflow specification
        YSpecification spec = createMultiAgentReviewWorkflow();

        // Launch case
        String caseId = engine.launchCase("MultiAgentReview", caseData);

        // Monitor progress
        monitorWorkflow(caseId, agents);
    }

    private static List<Agent> startAgents() {
        Agent generalReviewer = new Agent.Builder()
            .config(loadConfig("agent-1-config.yaml"))
            .build();

        Agent specializedReviewer = new Agent.Builder()
            .config(loadConfig("agent-2-config.yaml"))
            .build();

        Agent qaReviewer = new Agent.Builder()
            .config(loadConfig("agent-3-config.yaml"))
            .build();

        // Start all agents
        generalReviewer.start();
        specializedReviewer.start();
        qaReviewer.start();

        return List.of(generalReviewer, specializedReviewer, qaReviewer);
    }
}
```

### Workflow Specification

```xml
<!-- multi-agent-review-spec.xml -->
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
    <specification id="MultiAgentReview">
        <name>Multi-Agent Document Review</name>
        <version>1.0</version>

        <!-- Initial document upload -->
        <task id="UploadDocument">
            <name>Upload Document</name>
            <inputParameters>
                <parameter id="documentFile" type="string"/>
            </inputParameters>
            <outputParameters>
                <parameter id="documentId" type="string"/>
                <parameter id="documentType" type="string"/>
                <parameter id="documentSize" type="integer"/>
            </outputParameters>
        </task>

        <!-- Multiple reviewers with partitioning -->
        <task id="ReviewDocument" multiInstance="true" qorum="3">
            <name>Review Document</name>
            <inputParameters>
                <parameter id="documentId" type="string"/>
            </inputParameters>
            <outputParameters>
                <parameter id="reviewResults" type="string"/>
            </outputParameters>

            <!-- Agent binding with conflict resolution -->
            <agentBinding>
                <agentType>autonomous</agentType>
                <capabilityRequired>document-processing</capabilityRequired>
                <reviewQuorum>3</reviewQuorum>
                <conflictResolution>MAJORITY_VOTE</conflictResolution>
                <conflictArbiter>review-supervisor</conflictArbiter>
            </agentBinding>
        </task>

        <!-- Quality check -->
        <task id="QualityCheck">
            <name>Quality Assurance Check</name>
            <inputParameters>
                <parameter id="reviewResults" type="string"/>
            </inputParameters>
            <outputParameters>
                <parameter id="finalDecision" type="string"/>
            </outputParameters>

            <agentBinding>
                <agentType>autonomous</agentType>
                <capabilityRequired>document-processing</capabilityRequired>
                <reviewQuorum>1</reviewQuorum>
                <conflictResolution>ESCALATE</conflictResolution>
                <agreementThreshold>0.8</agreementThreshold>
                <conflictArbiter>quality-manager</conflictArbiter>
            </agentBinding>
        </task>

        <!-- Final decision -->
        <task id="FinalDecision">
            <name>Final Decision</name>
            <inputParameters>
                <parameter id="finalDecision" type="string"/>
            </inputParameters>
            <outputParameters>
                <parameter id="approvalStatus" type="string"/>
                <parameter id="comments" type="string"/>
            </outputParameters>
        </task>
    </specification>
</YAWL>
```

## Example 2: Handoff Between Specialized Agents

### Scenario: Initial agent detects need for specialized handling

```java
// InitialReviewer.java
public class InitialReviewer extends GenericPartyAgent {

    @Override
    protected void onWorkItemCheckedOut(WorkItemRecord workItem) {
        // Check if document requires specialized review
        DocumentComplexity complexity = assessDocumentComplexity(workItem);

        if (complexity == DocumentComplexity.HIGH) {
            // Find specialized reviewer
            AgentDescriptor specialized = findSpecializedReviewer();

            if (specialized != null) {
                // Initiate handoff
                initiateHandoff(workItem, specialized);
                return;
            }
        }

        // Continue with normal processing
        super.onWorkItemCheckedOut(workItem);
    }

    private DocumentComplexity assessDocumentComplexity(WorkItemRecord workItem) {
        // Use Z.AI to assess document complexity
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(
            zaiApiKey,
            "Assess document complexity. Is this a simple document or requires specialized review?"
        );

        EligibilityResult result = reasoner.evaluate(workItem);
        return result.isEligible() ? DocumentComplexity.LOW : DocumentComplexity.HIGH;
    }

    private AgentDescriptor findSpecializedReviewer() {
        return agentRegistry.findByCapability(
            "document-processing",
            "legal-document-review"
        ).findFirst().orElse(null);
    }

    private void initiateHandoff(WorkItemRecord workItem, AgentDescriptor specialized) {
        // Create handoff session
        HandoffSession session = handoffProtocol.createHandoffSession(
            workItem.getID(),
            getId(),
            specialized.getId()
        );

        // Send A2A handoff message
        HandoffMessage message = new HandoffMessage(
            session.getToken(),
            workItem,
            getEndpoint(),
            Map.of(
                "handoffReason", "Document requires specialized legal review",
                "complexityAssessment", "HIGH",
                "priority", "URGENT"
            )
        );

        a2aClient.sendHandoffMessage(specialized.getEndpoint(), message);

        // Roll back work item
        interfaceBClient.rollbackWorkItem(getSessionHandle(), workItem.getID());

        // Log handoff event
        eventStore.append(new WorkItemHandoffEvent(
            workItem.getID(),
            getId(),
            specialized.getId(),
            "Document complexity exceeds capability"
        ));
    }
}

// SpecializedReviewer.java
public class SpecializedReviewer extends GenericPartyAgent {

    @Override
    public void onHandoffReceived(String handoffToken) {
        // Validate and process handoff
        HandoffToken token = handoffProtocol.validateToken(handoffToken);

        // Get work item using original session
        WorkItemRecord item = interfaceBClient.getWorkItem(
            token.getEngineSession(),
            token.getWorkItemId()
        );

        // Check out work item using original session
        interfaceBClient.checkoutWorkItem(token.getEngineSession(), item.getID());

        // Process with specialized reasoning
        Decision decision = reasoner.decide(item);

        // Complete work item
        interfaceBClient.completeWorkItem(
            token.getEngineSession(),
            item.getID(),
            decision.getOutput()
        );

        // Log handoff completion
        eventStore.append(new WorkItemHandoffCompletedEvent(
            item.getID(),
            token.getFromAgent(),
            getId(),
            "Specialized review completed"
        ));
    }
}
```

### Configuration for Handoff

```yaml
# handoff-config.yaml
handoff:
  enabled: true
  timeout: 30000
  ttl: 60000
  maxRetries: 3
  retry:
    baseDelayMs: 1000
    maxDelayMs: 30000
    jitter: true
  validation:
    checkCompatibility: true
    verifyTargetAvailability: true
    handoffReasons:
      - "document complexity"
      - "specialized skill required"
      - "resource constraints"

agents:
  - id: "reviewer-general"
    handoffFrom:
      - "reviewer-legal"
      - "reviewer-financial"
      - "reviewer-technical"

  - id: "reviewer-legal"
    specializedIn: ["legal-document-review"]
    canHandoffTo: ["reviewer-general"]
    priority: "high"
```

## Example 3: Conflict Resolution with Multiple Strategies

### Scenario: Multiple agents review same work item and disagree

```java
// DocumentReviewAgent.java
public class DocumentReviewAgent extends GenericPartyAgent {

    @Override
    protected void onCompleteWorkItem(WorkItemRecord workItem, Decision decision) {
        // Check for conflicts with other agents
        List<AgentDecision> peerDecisions = getPeerDecisions(workItem.getID());

        // Apply different conflict resolution strategies based on work item type
        ConflictResolutionStrategy strategy = determineResolutionStrategy(workItem);

        if (strategy != null && strategy.requiresResolution(peerDecisions)) {
            // Handle conflict
            handleConflict(workItem, peerDecisions, strategy);
        } else {
            // No conflict, complete normally
            super.onCompleteWorkItem(workItem, decision);
        }
    }

    private ConflictResolutionStrategy determineResolutionStrategy(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();

        switch (taskName) {
            case "FinancialApproval":
                return new EscalationConflictResolver(
                    agentRegistry,
                    findArbiter("finance-director"),
                    0.8  // 80% agreement threshold
                );

            case "SecurityReview":
                return new MajorityVoteConflictResolver();

            case "ContractApproval":
                return new EscalatingConflictResolver(
                    agentRegistry,
                    findArbiter("legal-director"),
                    0.6  // 60% agreement threshold
                ) {
                    @Override
                    protected boolean shouldFallbackToHuman(List<AgentDecision> decisions) {
                        // Additional logic for contract approval
                        return decisions.stream()
                            .allMatch(d -> d.getOutcome() == DecisionOutcome.REJECT);
                    }
                };

            default:
                return new MajorityVoteConflictResolver();
        }
    }

    private void handleConflict(WorkItemRecord workItem,
                             List<AgentDecision> decisions,
                             ConflictResolutionStrategy strategy) {

        if (strategy instanceof MajorityVoteConflictResolver) {
            // Simple majority vote
            AgentResolution resolution = strategy.resolve(decisions, workItem);

            // Log conflict resolution
            eventStore.append(new AgentConflictResolvedEvent(
                workItem.getID(),
                resolution,
                "Majority vote: " + resolution.getOutcome()
            ));

            // Apply resolution
            interfaceBClient.completeWorkItem(
                sessionHandle,
                workItem.getID(),
                resolution.getOutput()
            );

        } else if (strategy instanceof EscalationConflictResolver) {
            // Escalate to arbiter
            EscalationConflictResolver resolver = (EscalationConflictResolver) strategy;

            if (resolver.requiresEscalation(decisions)) {
                AgentResolution resolution = resolver.escalate(
                    decisions,
                    workItem,
                    resolver.getArbiterEndpoint()
                );

                // Log escalation
                eventStore.append(new AgentConflictEscalatedEvent(
                    workItem.getID(),
                    resolution,
                    "Escalated to arbiter"
                ));

                // Apply arbiter decision
                interfaceBClient.completeWorkItem(
                    sessionHandle,
                    workItem.getID(),
                    resolution.getOutput()
                );

            } else {
                // Agreement reached, proceed normally
                super.onCompleteWorkItem(workItem, getLastDecision(decisions));
            }
        }
    }
}
```

### Conflict Resolution Configuration

```yaml
# conflict-resolution-config.yaml
conflict:
  strategies:
    majorityVote:
      name: "MAJORITY_VOTE"
      description: "Simple majority vote among agents"
      quorum: 3
      timeout: 30000
      applyImmediately: true

    escalation:
      name: "ESCALATE"
      description: "Escalate to arbiter agent when agreement threshold not met"
      agreementThreshold: 0.8
      arbiterEndpoint: "http://arbiter-agent:8081"
      escalationTimeout: 60000
      fallbackToHuman: false

    humanFallback:
      name: "HUMAN_FALLBACK"
      description: "Escalate to human participants when all agents disagree"
      enable: true
      resourceService: "human-resource-service"
      escalationMessage: "All agents disagree, human review required"

  arbitration:
    enabled: true
    arbiterAgents:
      - id: "finance-director"
        domain: "financial"
        priority: "HIGH"
      - id: "legal-director"
        domain: "legal"
        priority: "HIGH"
      - id: "security-director"
        domain: "security"
        priority: "HIGH"

  escalationRules:
    - condition: "task == 'FinancialApproval'"
      strategy: "escalation"
      threshold: 0.8
      arbiter: "finance-director"

    - condition: "task == 'ContractApproval'"
      strategy: "escalation"
      threshold: 0.6
      arbiter: "legal-director"
      fallback: "human"

    - condition: "task == 'SecurityReview'"
      strategy: "majorityVote"
      quorum: 2
```

## Example 4: MCP Integration with Claude Desktop

### Configuration for Claude Desktop

```json
{
  "mcpServers": {
    "yawl-coordination": {
      "command": "java",
      "args": ["-jar", "/path/to/yawl-mcp-server.jar"],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "YAWL",
        "A2A_API_KEY": "${env:A2A_API_KEY}",
        "ZAI_API_KEY": "${env:ZAI_API_KEY}"
      }
    }
  }
}
```

### Claude Desktop Usage Examples

```markdown
# Work Item Management

## Check out work items
```
/checkout_work_item {"id": "WI-42"}
```

## Complete work item with decision
```
/complete_work_item {"id": "WI-42", "data": {"decision": "APPROVE", "comments": "Document looks good"}}
```

## List available agents
```
/list_agents
```

## Escalate conflict
```
/escalate_conflict {"workItemId": "WI-42", "reason": "Agents disagree on approval"}
```

## Launch workflow via A2A
```
/yawl_launch {"specId": "DocumentReview", "caseData": {"document": "contract.pdf"}}
```

## Monitor workflow status
```
/yawl_status {"caseId": "CASE-123"}
```
```

### Custom Claude Commands

```java
// CustomMcpCommands.java
public class CustomMcpCommands {

    @McpTool("yawl_batch_checkout")
    public ToolResult batchCheckout(Map<String, Object> args) {
        List<String> workItemIds = (List<String>) args.get("workItemIds");

        List<WorkItemRecord> items = workItemIds.stream()
            .map(id -> interfaceBClient.getWorkItem(sessionHandle, id))
            .collect(Collectors.toList());

        // Filter by capability
        List<WorkItemRecord> filtered = items.stream()
            .filter(item -> eligibilityReasoner.canHandle(item, currentCapability))
            .collect(Collectors.toList());

        // Checkout items
        filtered.forEach(item -> interfaceBClient.checkoutWorkItem(sessionHandle, item.getID()));

        return new ToolResult(Map.of(
            "checkedOut", filtered.size(),
            "total", items.size(),
            "items", filtered.stream().map(WorkItemRecord::getID).collect(Collectors.toList())
        ), "");
    }

    @McpTool("yawl_handoff_to_agent")
    public ToolResult handoffToAgent(Map<String, Object> args) {
        String workItemId = (String) args.get("workItemId");
        String agentId = (String) args.get("agentId");
        String reason = (String) args.get("reason");

        WorkItemRecord item = interfaceBClient.getWorkItem(sessionHandle, workItemId);
        AgentDescriptor targetAgent = agentRegistry.findById(agentId);

        if (targetAgent == null) {
            throw new AgentNotFoundException("Agent not found: " + agentId);
        }

        // Create handoff session
        HandoffSession session = handoffProtocol.createHandoffSession(
            workItemId,
            getCurrentAgent().getId(),
            agentId
        );

        // Send handoff message
        HandoffMessage message = new HandoffMessage(
            session.getToken(),
            item,
            getEndpoint(),
            Map.of("handoffReason", reason)
        );

        a2aClient.sendHandoffMessage(targetAgent.getEndpoint(), message);

        // Roll back work item
        interfaceBClient.rollbackWorkItem(sessionHandle, workItemId);

        return new ToolResult(Map.of(
            "success", true,
            "handoffToken", session.getToken(),
            "targetAgent", agentId
        ), "");
    }
}
```

## Example 5: A2A Integration for Workflow Orchestration

### Claude to YAWL Workflow

```java
// ClaudeWorkflowOrchestrator.java
@RestController
@RequestMapping("/workflow")
public class ClaudeWorkflowOrchestrator {

    @PostMapping("/launch")
    public ResponseEntity<A2AResponse> launchWorkflow(@RequestBody A2ARequest request) {
        // Extract workflow parameters
        String specId = (String) request.getParts().stream()
            .filter(p -> "data".equals(p.getType()))
            .findFirst()
            .map(p -> (Map<String, Object>) p.getContent())
            .map(m -> (String) m.get("yawl_spec_id"))
            .orElseThrow();

        Map<String, Object> caseData = (Map<String, Object>) request.getParts().stream()
            .filter(p -> "data".equals(p.getType()))
            .findFirst()
            .map(p -> (Map<String, Object>) p.getContent())
            .map(m -> (Map<String, Object>) m.get("yawl_case_data"))
            .orElse(Map.of());

        // Launch YAWL case
        String caseId = engine.launchCase(specId, caseData);

        // Get initial work items
        List<WorkItemDescriptor> workItems = engine.getWorkItems(caseId)
            .stream()
            .filter(w -> w.getStatus() == WorkItemStatus.ENABLED)
            .collect(Collectors.toList());

        // Create response
        A2AResponse response = new A2AResponse.Builder()
            .data(Map.of(
                "yawl_case_id", caseId,
                "work_items", workItems,
                "callback_url", getCallbackUrl(caseId)
            ))
            .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{caseId}/callback")
    public ResponseEntity<?> handleWorkflowCallback(
            @PathVariable String caseId,
            @RequestBody CallbackRequest request) {

        if ("completed".equals(request.getStatus())) {
            // Handle successful completion
            Map<String, Object> result = request.getWorkflowData();

            // Trigger downstream actions
            downstreamService.processWorkflowResult(result);

            // Send notification back to Claude
            notifyClaudeCompletion(caseId, result);

        } else if ("failed".equals(request.getStatus())) {
            // Handle failure
            workflowFailureHandler.handle(caseId, request.getError());

            // Notify Claude of failure
            notifyClaudeFailure(caseId, request.getError());
        }

        return ResponseEntity.ok().build();
    }
}
```

### Workflow Definition with A2A Integration

```xml
<!-- a2a-integration-spec.xml -->
<YAWL xmlns="http://www.yawlfoundation.org/yawl">
    <specification id="A2AIntegration">
        <name>A2A Integration Example</name>

        <!-- Task launched via A2A -->
        <task id="ProcessInvoice">
            <name>Process Invoice</name>
            <inputParameters>
                <parameter id="invoiceData" type="string"/>
                <parameter id="callbackUrl" type="string"/>
            </inputParameters>
            <outputParameters>
                <parameter id="processingResult" type="string"/>
            </outputParameters>

            <agentBinding>
                <agentType>autonomous</agentType>
                <capabilityRequired>invoice-processing</capabilityRequired>
                <reviewQuorum>1</reviewQuorum>
            </agentBinding>
        </task>

        <!-- Notify Claude of completion -->
        <task id="NotifyClaude">
            <name>Notify Claude</name>
            <inputParameters>
                <parameter id="caseId" type="string"/>
                <parameter id="result" type="string"/>
            </inputParameters>

            <automaticTasks>true</automaticTasks>
        </task>

        <!-- Flow -->
        <flows>
            <flow id="1" from="ProcessInvoice" to="NotifyClaude"/>
        </flows>
    </specification>
</YAWL>
```

## Example 6: Performance Optimization and Monitoring

### Performance Configuration

```yaml
# performance-optimization.yaml
performance:
  partitioning:
    batchSize: 1000          # Process items in batches
    timeout: 1000           # Partition timeout (ms)
    cacheSize: 10000        # Cache partition results
    evictionPolicy: "LRU"
    cacheRefreshInterval: 60000  # Refresh cache every minute

  handoff:
    async: true            # Enable async handoff
    queueSize: 1000        # Handoff message queue
    consumerThreads: 8     # Handoff consumer threads
    batchProcessing: true  # Process handoffs in batches

  conflict:
    asyncResolution: true  # Enable async conflict resolution
    resolutionTimeout: 30000
    maxConcurrent: 50      # Max concurrent resolutions
    queuePriority: "HIGH"  # High priority for conflicts

  discovery:
    cacheTTL: 300000       # 5 minutes cache TTL
    refreshInterval: 60000 # Refresh every minute
    maxAgents: 1000        # Max registered agents

  monitoring:
    metricsEnabled: true
    metricsInterval: 10000 # Collect metrics every 10 seconds
    healthCheckInterval: 30000
    alerts:
      partitionImbalance: 0.3  # Alert if imbalance > 30%
      handoffFailureRate: 0.1  # Alert if failure rate > 10%
      conflictResolutionTime: 5000  # Alert if resolution > 5s
```

### Monitoring Dashboard

```java
// AgentCoordinationMonitor.java
@Component
public class AgentCoordinationMonitor {

    private final MeterRegistry meterRegistry;
    private final AgentRegistry agentRegistry;

    @Scheduled(fixedRate = 10000)  // Every 10 seconds
    public void collectMetrics() {
        // Partition balance metrics
        Map<Integer, Integer> distribution = agentRegistry.getWorkItemDistribution();
        double avg = distribution.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);

        distribution.forEach((agentIndex, count) -> {
            double deviation = Math.abs(count - avg) / avg;
            meterRegistry.gauge("agent.partition.deviation", agentIndex, deviation);

            if (deviation > 0.3) {
                alertService.sendPartitionImbalanceAlert(agentIndex, deviation);
            }
        });

        // Handoff metrics
        HandoffMetrics handoffMetrics = handoffProtocol.getMetrics();
        meterRegistry.gauge("handoff.queue.size", handoffMetrics.getQueueSize());
        meterRegistry.gauge("handoff.processing.rate",
            handoffMetrics.getProcessingRatePerSecond());
        meterRegistry.gauge("handoff.failure.rate",
            handoffMetrics.getFailureRate());

        // Conflict resolution metrics
        ConflictMetrics conflictMetrics = conflictResolver.getMetrics();
        meterRegistry.gauge("conflict.resolution.time",
            conflictMetrics.getAverageResolutionTimeMs());
        meterRegistry.gauge("conflict.escalation.rate",
            conflictMetrics.getEscalationRate());
        meterRegistry.gauge("conflict.human.fallback.rate",
            conflictMetrics.getHumanFallbackRate());

        // Agent health metrics
        agentRegistry.getAllAgents().forEach(agent -> {
            HealthStatus health = agentRegistry.getHealthStatus(agent.getId());
            meterRegistry.gauge("agent.health.score",
                agent.getId(), health.getScore());

            if (!health.isHealthy()) {
                alertService.sendAgentHealthAlert(agent.getId(), health);
            }
        });
    }

    @GetMapping("/metrics/dashboard")
    public DashboardMetrics getDashboardMetrics() {
        return DashboardMetrics.builder()
            .partitionBalance(calculatePartitionBalance())
            .handoffPerformance(handoffProtocol.getPerformanceMetrics())
            .conflictStats(conflictResolver.getStatistics())
            .agentHealth(getAgentHealthStatus())
            .systemHealth(getSystemHealth())
            .build();
    }
}
```

### Performance Test Example

```java
// PerformanceTest.java
@Test
public void testMultiAgentPerformance() {
    // Setup
    startYawlEngine();
    startAgents(10);  // Start 10 agents
    createLargeWorkflow(10000);  // 10,000 work items

    // Monitor performance
    PerformanceMonitor monitor = new PerformanceMonitor();
    monitor.start();

    // Run test
    String caseId = engine.launchCase("PerformanceTest", largeCaseData);

    // Wait for completion
    await().atMost(5, MINUTES)
        .until(() -> engine.getCaseStatus(caseId) == CaseStatus.COMPLETED);

    // Collect metrics
    PerformanceMetrics metrics = monitor.stop();

    // Verify performance targets
    assertThat(metrics.getAverageProcessingTime()).isLessThan(500);  // < 500ms per item
    assertThat(metrics.getThroughput()).isGreaterThan(100);  // > 100 items/sec
    assertThat(metrics.getSuccessRate()).isGreaterThan(0.99);  // > 99% success rate
    assertThat(metrics.getMemoryUsage()).isLessThan(1024);  // < 1GB memory

    // Verify partition balance
    Map<Integer, Integer> distribution = agentRegistry.getWorkItemDistribution();
    double maxDeviation = distribution.values().stream()
        .mapToDouble(count -> Math.abs(count - 1000) / 1000.0)  // 1000 items per agent
        .max()
        .orElse(0);

    assertThat(maxDeviation).isLessThan(0.1);  // < 10% deviation from average
}
```

## Example 7: Real-World Document Processing Workflow

### Complete Workflow Example

```yaml
# document-processing-workflow.yaml
workflow:
  name: "Enterprise Document Processing"
  version: "2.0"

  stages:
    - name: "Document Ingestion"
      type: "input"
      tasks:
        - id: "UploadDocument"
          name: "Upload Document"
          assignee: "system"
          timeout: "5m"

        - id: "ClassifyDocument"
          name: "Classify Document Type"
          assignee: "classifier-agent"
          timeout: "30s"

    - name: "Review Process"
      type: "review"
      tasks:
        - id: "InitialReview"
          name: "Initial Document Review"
          assignee: "general-reviewer"
          requires: ["document-classification"]
          timeout: "10m"

        - id: "SpecializedReview"
          name: "Specialized Review"
          assignee: "specialized-reviewer"
          condition: "document-type == 'contract' || document-type == 'legal'"
          timeout: "15m"
          handoff:
            from: "general-reviewer"
            reason: "specialized-requirement"

        - id: "QualityReview"
          name: "Quality Assurance Review"
          assignee: "qa-reviewer"
          requires: ["initial-review", "specialized-review"]
          timeout: "5m"

    - name: "Decision"
      type: "decision"
      tasks:
        - id: "FinalApproval"
          name: "Final Approval"
          assignee: "approval-manager"
          requires: ["quality-review"]
          timeout: "2m"
          conflictResolution:
            strategy: "escalation"
            threshold: 0.7
            arbiter: "director"

    - name: "Output"
      type: "output"
      tasks:
        - id: "ArchiveDocument"
          name: "Archive Document"
          assignee: "system"
          requires: ["final-approval"]
          timeout: "1m"

        - id: "GenerateReport"
          name: "Generate Processing Report"
          assignee: "system"
          requires: ["final-approval"]
          timeout: "2m"
```

### Multi-Agent Implementation

```java
// DocumentProcessingWorkflow.java
@Component
public class DocumentProcessingWorkflow {

    @Autowired
    private AgentRegistry agentRegistry;

    @Autowired
    private HandoffProtocol handoffProtocol;

    @Autowired
    private ConflictResolver conflictResolver;

    public void processDocument(Document document) {
        // Create workflow case
        Map<String, Object> caseData = createCaseData(document);
        String caseId = engine.launchCase("DocumentProcessing", caseData);

        // Monitor workflow
        monitorWorkflow(caseId);
    }

    private void monitorWorkflow(String caseId) {
        while (true) {
            CaseStatus status = engine.getCaseStatus(caseId);

            if (status == CaseStatus.COMPLETED) {
                handleCompletion(caseId);
                break;
            } else if (status == CaseStatus.FAILED) {
                handleFailure(caseId);
                break;
            }

            // Check for conflicts
            checkForConflicts(caseId);

            // Check for handoffs
            checkForHandoffs(caseId);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void checkForConflicts(String caseId) {
        List<WorkItemRecord> enabledItems = engine.getWorkItems(caseId)
            .stream()
            .filter(w -> w.getStatus() == WorkItemStatus.ENABLED)
            .collect(Collectors.toList());

        for (WorkItemRecord item : enabledItems) {
            // Check if this task requires conflict resolution
            if (requiresConflictResolution(item)) {
                List<AgentDecision> decisions = getAgentDecisions(caseId, item.getID());

                if (needsConflictResolution(decisions)) {
                    resolveConflict(caseId, item, decisions);
                }
            }
        }
    }

    private void handleHandoffs(String caseId) {
        List<WorkItemRecord> items = engine.getWorkItems(caseId);

        for (WorkItemRecord item : items) {
            // Check if any agent wants to hand off
            List<AgentDecision> handoffRequests = getHandoffRequests(caseId, item.getID());

            for (AgentDecision request : handoffRequests) {
                if (request.getDecision() == DecisionOutcome.HANDOFF) {
                    initiateHandoff(caseId, item, request);
                }
            }
        }
    }

    private void resolveConflict(String caseId, WorkItemRecord item,
                               List<AgentDecision> decisions) {

        // Get conflict resolution strategy from task definition
        ConflictResolutionStrategy strategy = getConflictResolutionStrategy(item);

        if (strategy != null) {
            AgentResolution resolution = strategy.resolve(decisions, item);

            // Apply resolution
            interfaceBClient.completeWorkItem(
                getSessionHandle(caseId),
                item.getID(),
                resolution.getOutput()
            );

            // Log resolution
            eventStore.append(new AgentConflictResolvedEvent(
                item.getID(),
                resolution,
                "Conflict resolved: " + resolution.getReason()
            ));
        }
    }
}
```

## Best Practices Summary

1. **Start with single agent**: Build simple single-agent workflows first
2. **Add partitioning gradually**: Enable partitioning when scaling to multiple agents
3. **Implement handoffs**: Use handoffs for specialized processing
4. **Define conflict resolution**: Choose appropriate strategies for different scenarios
5. **Monitor performance**: Track partition balance and handoff performance
6. **Test failure scenarios**: Verify behavior when agents fail or become unavailable
7. **Use circuit breakers**: Prevent cascading failures
8. **Log everything**: Track all handoffs, conflicts, and resolutions
9. **Scale horizontally**: Add more agents instead of scaling single agents
10. **Regular health checks**: Monitor agent availability and performance

## Additional Resources

- [ADR-025 Implementation Guide](ADR-025-IMPLEMENTATION.md)
- [Configuration Examples](configuration-examples.md)
- [Troubleshooting Guide](troubleshooting.md)
- [Performance Testing Guide](../performance/PERFORMANCE_TESTING_GUIDE.md)
- [MCP Server Guide](../integration/MCP-SERVER-GUIDE.md)
- [A2A Server Guide](../integration/A2A-SERVER-GUIDE.md)