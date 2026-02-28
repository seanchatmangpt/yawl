# Fortune 5 Enterprise SAFe Simulation via YAWL Orchestration

**Date**: 2026-02-28
**Version**: 1.0.0
**Status**: Production Design
**Scope**: MCP-based integrations for portfolio, delivery, and real-time observability

---

## TABLE OF CONTENTS

1. [Executive Summary](#executive-summary)
2. [System Architecture](#system-architecture)
3. [External System Integrations](#external-system-integrations)
4. [Data Flows & ETL Pipelines](#data-flows--etl-pipelines)
5. [Real-Time Dashboards](#real-time-dashboards)
6. [API Contracts](#api-contracts)
7. [MCP Tool Specifications](#mcp-tool-specifications)
8. [Z.AI Integration for Autonomous Agents](#zai-integration-for-autonomous-agents)
9. [Resilience & Observability](#resilience--observability)
10. [Deployment & Operations](#deployment--operations)
11. [Cost Model & ROI](#cost-model--roi)

---

## EXECUTIVE SUMMARY

This document designs Fortune 5-grade enterprise integrations for SAFe (Scaled Agile Framework) simulation orchestrated by the YAWL workflow engine. The solution leverages:

- **YAWL 6.0** as the orchestration backbone for cross-system coordination
- **MCP (Model Context Protocol) 2025-11-25** for tool invocation and real-time data access
- **Z.AI (Zhipu.ai)** via `ZHIPU_API_KEY` for autonomous decision-making and insights
- **A2A (Agent-to-Agent)** protocol for inter-process communication
- **SPARQL CONSTRUCT** for zero-inference-cost workflow pattern matching and token routing
- **Virtual threads** (Java 25+) for million-concurrent-case scalability

The system enables:

1. **Portfolio Management**: Jira Align → YAWL case state → dashboard updates in <2s
2. **Delivery Tracking**: Azure DevOps commits → YAWL work item completion → burndown sync
3. **AI-Assisted Decisions**: GitHub Copilot + Z.AI → task recommendations → automation proposals
4. **Financial Visibility**: SAP/Oracle GL → portfolio value streams → executive dashboards
5. **Risk/Compliance**: ServiceNow incidents ↔ YAWL case state + audit trail (GDPR-ready)
6. **Customer Insights**: Salesforce NPS/demand signals → workflow priority adjustment

All integrations use **environment-based credentials** (never hardcoded), **exponential backoff** for transient failures, and **zero-fallback** semantics (fail-fast via `UnsupportedOperationException`).

---

## SYSTEM ARCHITECTURE

### High-Level Integration Topology

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FORTUNE 5 SAFe ORCHESTRATION                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
│  │ Jira Align │  │  Azure   │  │ GitHub   │  │Salesforce│  │SAP/Oracle │ │
│  │ Portfolio  │  │ DevOps   │  │ Copilot  │  │ CRM      │  │ Financial │ │
│  └──────┬─────┘  └──────┬───┘  └──────┬───┘  └──────┬───┘  └─────┬──────┘ │
│         │               │             │             │             │         │
│         └───────────────┴─────────────┴─────────────┴─────────────┘         │
│                         │                                                    │
│              ┌──────────▼───────────────┐                                   │
│              │  MCP Connector Layer     │                                   │
│              │  (tool invocation)       │                                   │
│              └──────────┬───────────────┘                                   │
│                         │                                                    │
│         ┌───────────────▼──────────────────────┐                            │
│         │     YAWL 6.0 Engine                  │                            │
│         │  (case orchestration + A2A server)   │                            │
│         │  - YNetRunner (per-case virtual     │                            │
│         │    threads, <50ms latency)           │                            │
│         │  - YWorkItem (work distribution)     │                            │
│         │  - Agent Marketplace (strategy sel)  │                            │
│         └───────────┬──────────────────────────┘                            │
│                     │                                                        │
│  ┌──────────────────┼──────────────────────┐                               │
│  │                  │                      │                               │
│  ▼                  ▼                      ▼                               │
│ Z.AI             SPARQL          Event Sourcing                           │
│ (ZHIPU_API_KEY)  CONSTRUCT        (case state)                           │
│ autonomous       token routing                                            │
│ decisions        (0 inference)                                            │
│                                                                             │
│  ┌──────────────────────────────────────────────────────┐                 │
│  │         Real-Time Dashboards                         │                 │
│  │  (Executive | ART | Metrics | Risk | Geo)           │                 │
│  │  WebSocket SSE for <1s latency updates              │                 │
│  └──────────────────────────────────────────────────────┘                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Role | Technology |
|-----------|------|-----------|
| **Jira Align MCP** | Portfolio query + sync | REST, GraphQL, JQL |
| **Azure DevOps MCP** | Work item sync, burndown | REST API, webhooks |
| **GitHub Copilot MCP** | Code insights, recommendations | GitHub API, LLM bridge |
| **Salesforce MCP** | Customer signals, NPS | REST API, streaming |
| **SAP/Oracle MCP** | GL posting, budget tracking | OData, RFC, SQL |
| **ServiceNow MCP** | Incident correlation | REST API, webhook CMDB |
| **YAWL Engine** | Workflow orchestration | Java 25, virtual threads |
| **Z.AI Agent** | Autonomous decisions | LLM inference via API |
| **SPARQL Engine** | Token routing logic | Oxigraph (embedded) |
| **Event Store** | Audit trail + replay | Event Sourcing (append-only) |
| **Real-Time Dashboard** | Portfolio visibility | WebSocket, D3.js, React |

---

## EXTERNAL SYSTEM INTEGRATIONS

### 1. Jira Align Integration (Portfolio Management)

#### MCP Server: `JiraAlignPortfolioMCP`

**Entry Point**: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/JiraAlignPortfolioTools.java`

**Data Model**:

```java
/**
 * Jira Align Portfolio MCP Tools — query PI/ART/team planning data
 * in real-time and sync updates to YAWL case state.
 */
public final class JiraAlignPortfolioTools {

    /**
     * Query ART health and current capacity.
     * @param artId Agile Release Train identifier
     * @return ArtHealthSnapshot with velocity, capacity, WIP
     */
    public record ArtHealthSnapshot(
        String artId,
        String artName,
        int plannedVelocity,
        int actualVelocity,
        int wipCount,
        double healthScore,  // 0.0-1.0
        List<RiskSignal> risks,
        Instant lastSync
    ) {}

    /**
     * Query PI planning state and forecast.
     */
    public record PiPlanningState(
        String piId,
        String piName,
        LocalDate startDate,
        LocalDate endDate,
        List<FeatureAllocation> features,
        List<DependencyLink> dependencies,
        int forecastedVelocity,
        double confidenceScore
    ) {}

    /**
     * Streaming team capacity model.
     */
    public record TeamCapacity(
        String teamId,
        String teamName,
        int totalCapacity,
        int allocatedCapacity,
        int availableCapacity,
        List<TeamMember> members
    ) {}

    /**
     * Risk signal for escalation to YAWL.
     */
    public record RiskSignal(
        String riskId,
        String title,
        String severity,  // critical|high|medium|low
        String description,
        String recommendedAction
    ) {}
}
```

**MCP Tools**:

1. **`jira_align_query_art_health`** — Get ART metrics in real-time
   - **Input**: `artId: string`
   - **Output**: `ArtHealthSnapshot` JSON
   - **Timeout**: 2s (circuit break if exceeded)
   - **Auth**: `JIRA_ALIGN_API_KEY` env var

2. **`jira_align_query_pi_planning`** — Get PI features and dependencies
   - **Input**: `piId: string`
   - **Output**: `PiPlanningState` JSON
   - **Timeout**: 3s
   - **Cache**: 30s (watermark-based)

3. **`jira_align_list_teams`** — Get team capacity across portfolio
   - **Input**: None
   - **Output**: `List<TeamCapacity>` JSON
   - **Timeout**: 5s

4. **`jira_align_sync_case_to_story`** — Update Jira epic/story from YAWL case
   - **Input**: `caseId: string, storyKey: string, status: string`
   - **Output**: `{ success: boolean, jiraStatus: string }`
   - **Timeout**: 3s
   - **Idempotent**: Yes (updates existing epic)

**Environment Variables**:

```bash
JIRA_ALIGN_BASE_URL=https://jira-align.company.com
JIRA_ALIGN_API_KEY=<api-key-from-vault>
JIRA_ALIGN_WORKSPACE_ID=<workspace-uuid>
```

**Failure Handling**:

- Network timeout (2s) → Retry with exponential backoff (100ms, 200ms, 400ms, 800ms)
- API rate limit (429) → Wait for `Retry-After` header or 60s
- Invalid API key → Fail-fast with `UnsupportedOperationException` (credentials unavailable)
- ART not found (404) → Return empty snapshot with status "not_found"

**Watermark Protocol** (prevents thrashing):

```java
// Skip fetch if cache is fresh
String cacheKey = "jira_align_art_" + artId;
Optional<CacheEntry<ArtHealthSnapshot>> cached = watermarks.get(cacheKey);
if (cached.isPresent()) {
    CacheEntry<ArtHealthSnapshot> entry = cached.get();
    if (Instant.now().isBefore(entry.expiresAt()) &&
        entry.contentHash() == computeHash(entry.value())) {
        return entry.value();  // Return cached, skip API call
    }
}

// Fetch from API
ArtHealthSnapshot fresh = fetchFromJiraAlign(artId);
watermarks.put(cacheKey, new CacheEntry<>(
    fresh,
    Instant.now().plus(Duration.ofSeconds(30)),
    blake3Hash(fresh)
));
return fresh;
```

---

### 2. Azure DevOps Integration (Delivery Tracking)

#### MCP Server: `AzureDevOpsMCP`

**Data Model**:

```java
public final class AzureDevOpsTools {

    /**
     * Work item with sprint assignment and burndown.
     */
    public record WorkItemBurndown(
        int workItemId,
        String title,
        String state,  // "New", "Active", "Resolved", "Closed"
        int storyPoints,
        String sprintId,
        Instant createdDate,
        Instant completedDate,
        List<ActivityLog> history
    ) {}

    /**
     * Sprint velocity and forecast.
     */
    public record SprintMetrics(
        String sprintId,
        String sprintName,
        LocalDate startDate,
        LocalDate endDate,
        int committedPoints,
        int completedPoints,
        int inProgressPoints,
        int remainingPoints,
        double velocityTrend  // points/day average over last 3 sprints
    ) {}

    /**
     * Commit → code review → PR merge pipeline.
     */
    public record PullRequestMetrics(
        int prId,
        String title,
        String status,
        List<Commit> commits,
        Instant createdAt,
        Instant mergedAt,
        Duration reviewDuration
    ) {}

    public record Commit(
        String commitId,
        String authorName,
        String message,
        LocalDateTime commitDate,
        List<String> associatedWorkItems
    ) {}
}
```

**MCP Tools**:

1. **`azure_devops_list_sprints`** — Get all sprints for a project
   - **Input**: `projectId: string`
   - **Output**: `List<SprintMetrics>` JSON
   - **Cache**: 60s

2. **`azure_devops_sync_work_item`** — Update work item state
   - **Input**: `workItemId: int, state: string, remainingWork: number`
   - **Output**: `WorkItemBurndown` JSON
   - **Idempotent**: Yes

3. **`azure_devops_query_pr_metrics`** — Get PR metrics for burndown impact
   - **Input**: `prId: int`
   - **Output**: `PullRequestMetrics` JSON
   - **Timeout**: 2s

**Environment Variables**:

```bash
AZURE_DEVOPS_ORG_URL=https://dev.azure.com/<org>
AZURE_DEVOPS_PAT=<personal-access-token>
AZURE_DEVOPS_PROJECT_ID=<project-uuid>
```

**Webhook Integration**:

- Register `/webhook/azure-devops/work-item-updated` for state changes
- YAWL receives webhook → updates case state → triggers dependent work
- **Signature Verification**: HMAC-SHA256 using `AZURE_DEVOPS_WEBHOOK_SECRET`

---

### 3. GitHub Copilot Integration (AI-Assisted Coding)

#### MCP Server: `GitHubCopilotMCP`

**Purpose**: Query Copilot suggestions and integrate with YAWL task recommendations.

**Data Model**:

```java
public final class GitHubCopilotTools {

    /**
     * Copilot suggestion for a code context.
     */
    public record CopilotSuggestion(
        String suggestionId,
        String filePath,
        int lineNumber,
        String suggestedCode,
        String rationale,  // why this suggestion
        double confidence,
        String category  // "bug_fix", "optimization", "test", "docs"
    ) {}

    /**
     * Repository code quality metrics.
     */
    public record RepositoryMetrics(
        String repoName,
        int totalFiles,
        int filesWithTests,
        double testCoverage,
        List<CodeSmell> detectedSmells
    ) {}

    public record CodeSmell(
        String filePath,
        String type,  // "duplication", "complexity", "smell"
        int lineNumber,
        String description
    ) {}
}
```

**MCP Tools**:

1. **`github_copilot_suggest_refactoring`** — Get Copilot refactoring suggestions
   - **Input**: `filePath: string, codeContext: string`
   - **Output**: `List<CopilotSuggestion>` JSON
   - **Auth**: `GITHUB_TOKEN` (with Copilot access)
   - **Timeout**: 5s (LLM inference)

2. **`github_copilot_repository_metrics`** — Code quality summary
   - **Input**: `repoName: string`
   - **Output**: `RepositoryMetrics` JSON
   - **Cache**: 24h

3. **`github_copilot_generate_task_description`** — Auto-generate PR template
   - **Input**: `branchName: string, commits: List<Commit>`
   - **Output**: `{ title: string, description: string, checklistItems: string[] }`
   - **Timeout**: 3s

**Workflow Integration**:

```
YAWL CaseType: "CodeReview"
  │
  ├─ Task: "Review PR" (assigned to engineer)
  │   │
  │   └─ MCP call: github_copilot_suggest_refactoring
  │       └─ Display suggestions in work item UI
  │
  ├─ Task: "Address feedback"
  │   │
  │   └─ (engineer commits)
  │
  └─ Task: "Approve" (assigned to lead)
```

---

### 4. Salesforce Integration (Customer Insights)

#### MCP Server: `SalesforceInsightsMCP`

**Data Model**:

```java
public final class SalesforceToolsTools {

    /**
     * Customer NPS and satisfaction trends.
     */
    public record CustomerSentiment(
        String customerId,
        String accountName,
        double npsScore,
        String sentiment,  // "very_satisfied", "satisfied", "neutral", "dissatisfied"
        String feedbackTopic,  // "feature_request", "performance", "support_quality"
        Instant lastFeedbackDate
    ) {}

    /**
     * Opportunity pipeline with demand signals.
     */
    public record OpportunityPipeline(
        String opportunityId,
        String accountName,
        String stageName,
        BigDecimal amount,
        LocalDate expectedCloseDate,
        List<DemandSignal> signals
    ) {}

    public record DemandSignal(
        String featureRequested,
        int mentionCount,
        String urgency  // "immediate", "planned", "future"
    ) {}

    /**
     * Account health and risk.
     */
    public record AccountHealth(
        String accountId,
        String accountName,
        String healthStatus,  // "at_risk", "healthy", "growing"
        double healthScore,
        List<String> riskIndicators
    ) {}
}
```

**MCP Tools**:

1. **`salesforce_get_customer_sentiment`** — Query NPS and feedback
   - **Input**: `customerId: string OR accountName: string`
   - **Output**: `CustomerSentiment` JSON
   - **Cache**: 24h

2. **`salesforce_query_demand_pipeline`** — Feature requests from opportunities
   - **Input**: None (query all open opportunities)
   - **Output**: `List<OpportunityPipeline>` JSON
   - **Timeout**: 3s

3. **`salesforce_get_account_health`** — Risk assessment per account
   - **Input**: `accountId: string`
   - **Output**: `AccountHealth` JSON

**Workflow Integration** (Priority Adjustment):

```
YAWL receives Salesforce webhook:
  "Account ABC (npsScore=2) reported issue with Feature X"

  → Lookup YAWL cases tagged "Feature X"
  → Increase priority from "medium" to "high"
  → Create escalation task for product manager
  → Send alert to ART lead
```

---

### 5. SAP/Oracle Integration (Financial Systems)

#### MCP Server: `FinancialSystemsMCP`

**Data Model**:

```java
public final class FinancialSystemsTools {

    /**
     * Cost center budget and actuals.
     */
    public record CostCenterBudget(
        String costCenterId,
        String costCenterName,
        BigDecimal budgetAmount,
        BigDecimal actualSpent,
        BigDecimal forecastedEnd,
        LocalDate fiscalPeriodEnd
    ) {}

    /**
     * Project financial KPIs.
     */
    public record ProjectFinancialSnapshot(
        String projectId,
        String projectName,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost,
        double costPerformanceIndex,  // EV / AC
        double schedulePerformanceIndex,  // EV / PV
        BigDecimal estimateAtCompletion
    ) {}

    /**
     * GL posting audit trail.
     */
    public record GlPosting(
        String postingId,
        String documentNumber,
        LocalDate postingDate,
        String account,
        String costCenter,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String narration,
        String approvalStatus
    ) {}
}
```

**MCP Tools**:

1. **`oracle_query_cost_center_budget`** — Get budget vs. actuals
   - **Input**: `costCenterId: string`
   - **Output**: `CostCenterBudget` JSON
   - **Auth**: `ORACLE_JDBC_USER`, `ORACLE_JDBC_PASSWORD` (env vars)
   - **Cache**: 6h (financial data slower-changing)

2. **`sap_query_project_financial_snapshot`** — EV/AC/PV metrics
   - **Input**: `projectId: string`
   - **Output**: `ProjectFinancialSnapshot` JSON
   - **Timeout**: 5s (large financial queries)

3. **`oracle_post_gl_entry`** — Post GL entry from YAWL case completion
   - **Input**: `caseId: string, projectId: string, amount: number, account: string`
   - **Output**: `{ glNumber: string, status: string, timestamp: ISO8601 }`
   - **Idempotent**: Yes (use YAWL caseId as document reference)
   - **Audit Trail**: Captured in YAWL event store + GL posting table

**Connection Pooling** (Java 25):

```java
// Use virtual threads for concurrent GL queries
try (var scope = new StructuredTaskScope.ShutdownOnFailure<ProjectFinancialSnapshot>()) {
    List<String> projectIds = /* ... */;
    List<Future<ProjectFinancialSnapshot>> futures = projectIds.stream()
        .map(id -> scope.fork(() -> oracleClient.querySnapshot(id)))
        .toList();

    scope.join();  // Wait for all or first failure
    return futures.stream()
        .map(Future::resultNow)
        .toList();
}
```

**Environment Variables**:

```bash
ORACLE_JDBC_URL=jdbc:oracle:thin:@//host:1521/db
ORACLE_JDBC_USER=apps
ORACLE_JDBC_PASSWORD=<password>
ORACLE_RETRY_TIMEOUT_MILLIS=5000

SAP_RFC_GATEWAY_HOST=sap-gateway.company.com
SAP_RFC_GATEWAY_PORT=3300
SAP_RFC_GATEWAY_USER=rfcuser
SAP_RFC_GATEWAY_PASSWORD=<password>
```

---

### 6. ServiceNow Integration (IT Operations & Compliance)

#### MCP Server: `ServiceNowComplianceMCP`

**Data Model**:

```java
public final class ServiceNowTools {

    /**
     * Incident with correlation to YAWL cases.
     */
    public record IncidentRecord(
        String incidentId,
        String shortDescription,
        String state,  // "new", "assigned", "in_progress", "resolved", "closed"
        String priority,
        String assignedTo,
        Instant createdDate,
        Instant resolvedDate,
        List<String> relatedCaseIds,  // YAWL case IDs
        String complianceArea  // "data_privacy", "security", "audit"
    ) {}

    /**
     * Change Management workflow state.
     */
    public record ChangeTicket(
        String changeId,
        String title,
        String type,  // "normal", "standard", "emergency"
        String status,
        List<String> affectedCases,
        Instant scheduledStart,
        Instant scheduledEnd
    ) {}

    /**
     * CMDB configuration for workflow dependencies.
     */
    public record ConfigurationItem(
        String ciId,
        String ciName,
        String ciType,  // "application", "database", "service"
        List<String> relatedIncidents,
        String operationalStatus
    ) {}
}
```

**MCP Tools**:

1. **`servicenow_create_incident_from_case`** — Convert YAWL case failure to incident
   - **Input**: `caseId: string, reason: string, severity: string`
   - **Output**: `IncidentRecord` JSON
   - **Idempotent**: Yes (query by caseId, return existing or create)

2. **`servicenow_query_related_incidents`** — Find incidents blocking YAWL case
   - **Input**: `caseId: string`
   - **Output**: `List<IncidentRecord>` JSON
   - **Timeout**: 2s

3. **`servicenow_update_change_status`** — Synchronize change approval with YAWL
   - **Input**: `changeId: string, status: string`
   - **Output**: `{ success: boolean }`

4. **`servicenow_verify_cmdb_compliance`** — Check if workflow deployment complies with CMDB
   - **Input**: `deploymentInfo: { systems: string[], components: string[] }`
   - **Output**: `{ compliant: boolean, missingApprovals: string[] }`

**GDPR Erasure Integration**:

```java
public class GdprErasureService {

    /**
     * When customer requests deletion (GDPR Article 17):
     * 1. Find all YAWL cases mentioning customer ID
     * 2. Query ServiceNow for related incidents
     * 3. Mark data for erasure with 30-day retention
     * 4. Create audit log entry (cannot be deleted)
     */
    public void eraseCustomerData(String customerId) {
        // 1. YAWL cases
        List<String> caseIds = caseStore.findByCustomerId(customerId);
        for (String caseId : caseIds) {
            caseStore.markForErasure(caseId);  // Soft delete
        }

        // 2. ServiceNow incidents
        for (String caseId : caseIds) {
            List<IncidentRecord> incidents =
                servicenowClient.queryRelatedIncidents(caseId);
            for (IncidentRecord incident : incidents) {
                servicenowClient.scrubCustomerData(incident.incidentId());
            }
        }

        // 3. Create immutable audit entry
        auditLog.recordErasure(customerId, Instant.now(), "GDPR Article 17");
    }
}
```

**Webhook Monitoring**:

```
ServiceNow → YAWL Webhook Receiver
  ├─ Incident created
  │   └─ Create YAWL case type "Incident Response"
  │
  ├─ Incident priority elevated
  │   └─ Escalate YAWL case priority
  │
  ├─ Change deployed
  │   └─ Mark YAWL deployment case as completed
  │
  └─ Compliance violation detected
      └─ Create case type "Compliance Remediation"
         → Assign to compliance officer → workflow triggers audit
```

---

## DATA FLOWS & ETL PIPELINES

### Flow 1: Portfolio Synergy (Jira Align → YAWL → Azure DevOps → Dashboards)

**Latency SLA**: P99 <2 seconds

```
[Jira Align]
  │ webhook: epic.status.changed
  │
  ▼
[YAWL MCP Connector]
  │ 1. Receive: ART ABC assigned feature X
  │
  ├─ 2. Query jira_align_query_art_health(artId="ABC")
  │     Response: { velocityTrend: 45pt/sprint, health: 0.85 }
  │
  ├─ 3. Create YAWL case
  │     type: "feature_delivery"
  │     data: {
  │       jiraEpicId: "PROJ-1234",
  │       artId: "ABC",
  │       estPoints: 34,
  │       priority: "high"
  │     }
  │
  ├─ 4. Trigger task: "Estimate in Azure DevOps"
  │
  └─ 5. Call azure_devops_sync_work_item(
         workItemId=5678,
         state="In Progress",
         storyPoints=34
       )

  ▼
[Azure DevOps]
  │ work item updated with YAWL case reference
  │
  └─ webhook: work item state changed

  ▼
[YAWL Real-Time Event Stream]
  │ event: {
  │   type: "work_item_activated",
  │   caseId: "case-12345",
  │   artId: "ABC",
  │   priority: "high",
  │   timestamp: "2026-02-28T14:32:00Z"
  │ }
  │
  └─> Dashboard subscribers (WebSocket)
      ├─ Executive dashboard: "ART ABC has 1 new high-priority feature"
      ├─ ART burndown chart: updates series for in-progress items
      └─ Risk dashboard: updates health score
```

### Flow 2: Customer Demand → Priority Adjustment (Salesforce → YAWL)

**Scenario**: Salesforce detects NPS degradation; auto-escalate related YAWL cases.

```
[Salesforce CRM]
  │ customer ABC submits feedback:
  │   "Feature X is broken in production"
  │   npsScore: 2 (very dissatisfied)
  │
  └─ webhook: feedback.created

  ▼
[YAWL MCP Connector]
  │ 1. Receive webhook
  │ 2. Call salesforce_get_customer_sentiment(customerId="ABC")
  │    Response: { npsScore: 2, sentiment: "dissatisfied" }
  │
  ├─ 3. Query YAWL case store:
  │     Find all cases tagged "Feature X"
  │     Current: 2 cases (1 "in_development", 1 "in_testing")
  │
  ├─ 4. Update both cases:
  │     priority: "high" → "critical"
  │     status: add "ESCALATED_BY_CUSTOMER_FEEDBACK"
  │
  ├─ 5. Create escalation case:
  │     type: "customer_escalation"
  │     data: { customer: "ABC", nps: 2, reason: "Feature X broken" }
  │     assign: product_manager_lead
  │
  └─ 6. Publish event:
      event: {
        type: "case_priority_escalated",
        oldPriority: "high",
        newPriority: "critical",
        reason: "customer_sentiment_degradation",
        npsScore: 2
      }

  ▼
[Dashboard Subscribers]
  └─ Product manager sees: "2 cases escalated due to NPS drop"
     → Auto-opens Feature X case detail
     → Shows: "Customer ABC (20% of annual revenue) dissatisfied"
     → Offers: "View suggested actions" (Z.AI powered)
```

### Flow 3: Financial Closure (Case completion → GL posting → SAP)

**Scenario**: YAWL case for "Project Mgmt" work completes → auto-post GL entry.

```
[YAWL Engine]
  │ case: project_mgmt_work_case_001
  │ state: "in_progress"
  │ duration: 40 hours (tracked via work items)
  │
  └─ task: "Review & Approve" completes

  ▼
[YAWL Event Stream]
  │ event: {
  │   type: "case_completed",
  │   caseId: "project_mgmt_001",
  │   projectId: "PRJ-12345",
  │   costCenter: "CC-7890",
  │   duration: 40 hours,
  │   actualCost: $2000 (40h @ $50/h)
  │ }
  │
  └─ trigger: "PostGlEntry" task

  ▼
[GL Posting Microservice]
  │ 1. Call oracle_post_gl_entry({
  │      caseId: "project_mgmt_001",
  │      projectId: "PRJ-12345",
  │      account: "6110" (project labor),
  │      costCenter: "CC-7890",
  │      amount: 2000,
  │      narration: "Project management work - case project_mgmt_001"
  │    })
  │
  │ 2. Oracle response:
  │    {
  │      glNumber: "GL-2026-002847",
  │      status: "posted",
  │      timestamp: "2026-02-28T14:35:12Z"
  │    }
  │
  └─ 3. YAWL case updated:
       data.glNumber = "GL-2026-002847"
       data.glStatus = "posted"

  ▼
[SAP Query]
  │ Next day, finance team runs:
  │   sap_query_project_financial_snapshot(projectId="PRJ-12345")
  │
  │ Response includes:
  │   - PV (Planned Value): $150,000
  │   - EV (Earned Value): $145,000
  │   - AC (Actual Cost): $146,500 ← includes today's GL posting
  │   - CPI: 0.99 (slightly over budget)
  │   - EAC (Est. at Completion): $149,200
  │
  └─ Dashboard updated: Project 99% of budget consumed
```

---

## REAL-TIME DASHBOARDS

### Dashboard 1: Executive Portfolio Dashboard

**Update Frequency**: WebSocket SSE, <1s latency

**Metrics**:

| Metric | Source | Refresh |
|--------|--------|---------|
| Portfolio Value (EVA) | SAP GL + YAWL case tracking | Per case completion |
| ART Health Trend (6-sprint) | Jira Align + velocity calculation | Per sprint end |
| Risk Heat Map | ServiceNow incidents + YAWL escalations | Real-time |
| Budget vs. Actuals | Oracle cost centers + GL postings | Hourly |
| Delivery Predictability | Azure DevOps burndown + forecast | Per day |
| Customer Satisfaction (NPS) | Salesforce sentiment aggregation | Per feedback |

**Layout**:

```
┌────────────────────────────────────────────────────────────────┐
│ Executive Portfolio Dashboard — Q1 FY2026                      │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Portfolio Value    Budget Used    Health Score    Risk Level  │
│ $487.2M (EV)       72% / $680M     8.4/10         YELLOW      │
│ +$12M YTD          ↑ $5M (overrun) ↑ 0.3          ↑ 2 issues  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ ART Health Trend (6-sprint view)                               │
│                                                                 │
│  10.0 ┌─────────────────────────────────────────────────────┐  │
│       │                                ART-B ···ART-C····    │  │
│   9.0 │  ART-A ──────────────────────────────              │  │
│       │                      ╱          ╱                     │  │
│   8.0 │                  ╱          ╱                         │  │
│       │              ╱          ╱                             │  │
│   7.0 │         ╱         ╱                                   │  │
│       └─────────────────────────────────────────────────────┘  │
│         S1    S2    S3    S4    S5    S6                       │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ Dependency Status      Risk Escalations    Team Capacity       │
│                                                                 │
│ ✓ 34 on track         🔴 CRIT: 1          ┌───────────────┐  │
│ ⚠ 8 at risk           🟡 HIGH: 3          │ ART-A: 78%    │  │
│ ✗ 2 blocked           🟡 MED:  5          │ ART-B: 92%    │  │
│ (2 from ServiceNow)   (+ 2 escalated)     │ ART-C: 65%    │  │
│                                           └───────────────┘  │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

**WebSocket Events**:

```json
[
  {
    "type": "case_completed",
    "caseId": "proj_123",
    "earnedValue": 45000,
    "timestamp": "2026-02-28T14:35:12Z",
    "chart_update": {
      "series": "ev_curve",
      "new_point": [14, 145000],
      "animation": "fade_in"
    }
  },
  {
    "type": "incident_created",
    "incidentId": "INC-98765",
    "severity": "critical",
    "relatedCaseIds": ["proj_123"],
    "timestamp": "2026-02-28T14:36:00Z",
    "ui_action": "highlight_case",
    "notification": "Critical incident detected in active case"
  }
]
```

### Dashboard 2: ART Health Dashboard

**Focus**: Agile Release Train metrics, team capacity, sprint burndown.

**Key Metrics**:

```
ART-A: Predictability Engine
├─ Velocity (last 6 sprints): [38, 41, 39, 42, 40, 43 pts]
│   Trend: +1.8% MoM
│
├─ Sprint 27 Status:
│   Planned: 40 pts
│   Completed: 31 pts (77.5%)
│   In Progress: 6 pts
│   Blocked: 3 pts
│
├─ Team Capacity:
│   Allocated: 148/160 hours (92.5%)
│   Available: 12 hours
│   Absences: 1 engineer (20h, Feb 28 - Mar 7)
│
├─ WIP Trends:
│   Avg cycle time: 4.2 days
│   ↓ 0.3 days (improvement)
│
└─ Risk Signals:
    🔴 2 blockers (1 external dependency, 1 infra)
    🟡 5 stories at risk of spill
```

### Dashboard 3: Value Stream Flow Metrics

**Metrics** (Lean/SAFe inspired):

```
Feature: "Real-time Fraud Detection"
├─ Demand Discovery: 2 weeks (Jira planning → YAWL case creation)
├─ Development Lead Time: 8 weeks
│   ├─ Design: 1 week
│   ├─ Development: 4 weeks (Azure DevOps tracking)
│   ├─ Testing: 2 weeks
│   └─ Deployment: 1 week
│
├─ Processing Time (active work): 4 weeks
├─ Waiting Time (blocked/queue): 4 weeks
│
├─ Cash Flow Impact:
│   Development Cost: $180K
│   Expected Revenue Impact: $2.4M annually
│   ROI: 13.3×
│
└─ Deployment Time: 2 days (production roll)
    Service outage risk: <0.1% (canary deployment)
```

### Dashboard 4: Risk & Compliance Dashboard

**Data Sources**: ServiceNow incidents, YAWL escalations, audit trail.

```
┌─────────────────────────────────────────────────────────────────┐
│ Risk & Compliance Dashboard                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ Active Incidents    Compliance Status    Audit Trail          │
│ Critical:   1       Data Privacy: ✓       Last 30 days:      │
│ High:       3       Security:    ✓       - 0 unauthorized    │
│ Medium:     5       Financial:   ⚠ 1    - 2 escalations     │
│ Low:        8       (budget overrun)     - 1 GDPR erasure    │
│                                                                │
│ Correlation to YAWL Cases:                                    │
│ ├─ INC-98765 ──> proj_123 (critical)                         │
│ ├─ INC-98764 ──> feature_abc (high)                          │
│ └─ INC-98763 ──> test_xyz (medium)                           │
│                                                                │
│ GDPR Compliance:                                              │
│ Data retention: 45/90 days (within SLA)                     │
│ Erasure requests processed: 3 (avg 2.1 days)               │
│ Last audit: 2026-02-15 (13 days ago)                       │
│                                                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## API CONTRACTS

### Contract 1: Portfolio Query API (YAWL Internal)

**Endpoint**: `GET /api/v1/portfolio/cases?artId={artId}&status={status}`

**Request**:

```http
GET /api/v1/portfolio/cases?artId=ART-ABC&status=in_progress
Host: yawl.company.com
Authorization: Bearer {sessionHandle}
Accept: application/json
```

**Response**:

```json
{
  "artId": "ART-ABC",
  "artName": "Predictability Engine",
  "cases": [
    {
      "caseId": "case-12345",
      "title": "Implement real-time fraud detection",
      "status": "in_progress",
      "priority": "critical",
      "jiraEpicId": "PROJ-1234",
      "storyPoints": 34,
      "assignedTo": "engineering.team@company.com",
      "startDate": "2026-02-18",
      "estimatedCompletionDate": "2026-03-18",
      "actualCompletionDate": null,
      "workItems": [
        {
          "workItemId": "wi-456",
          "title": "Design data flow",
          "status": "completed",
          "completedDate": "2026-02-25",
          "assignee": "john.doe@company.com"
        }
      ],
      "linkedIncidents": ["INC-98765"],
      "riskFlags": ["external_dependency_blocked"],
      "earnedValue": 12000,
      "actualCost": 10500,
      "cpIndex": 1.14
    }
  ],
  "summary": {
    "totalCases": 8,
    "inProgressCount": 3,
    "completedCount": 5,
    "earnedValueSum": 125000,
    "actualCostSum": 118750,
    "portfolioCpiSum": 1.05
  }
}
```

**Error Handling**:

| Status | Error | Meaning |
|--------|-------|---------|
| 400 | `INVALID_ART_ID` | ART not found or malformed |
| 401 | `UNAUTHORIZED` | Session expired or invalid |
| 503 | `SERVICE_UNAVAILABLE` | Jira Align or Oracle unreachable |

---

### Contract 2: ART Status API (Real-Time)

**Endpoint**: `GET /api/v1/art/{artId}/health`

**Request**:

```http
GET /api/v1/art/ART-ABC/health?includeRisks=true
Host: yawl.company.com
Authorization: Bearer {sessionHandle}
X-Request-ID: req-12345
```

**Response** (with WebSocket upgrade option):

```json
{
  "artId": "ART-ABC",
  "artName": "Predictability Engine",
  "healthScore": 8.4,
  "healthStatus": "healthy",
  "metrics": {
    "velocity": {
      "current": 43,
      "trend": "stable",
      "lastSixSprints": [38, 41, 39, 42, 40, 43]
    },
    "wipCount": 6,
    "wipTrend": "stable",
    "cycleTime": {
      "mean": 4.2,
      "trend": "improving"
    },
    "teamCapacity": {
      "allocatedPercentage": 92.5,
      "availableHours": 12
    }
  },
  "risks": [
    {
      "riskId": "risk-001",
      "title": "External API dependency delay",
      "severity": "critical",
      "affectedCases": ["case-12345"],
      "recommendedAction": "Escalate to platform team"
    }
  ],
  "lastSync": "2026-02-28T14:35:00Z",
  "nextSync": "2026-02-28T14:36:00Z"
}
```

**WebSocket Upgrade**:

```
Client sends:
  GET /api/v1/art/ART-ABC/health/stream
  Upgrade: websocket
  Connection: Upgrade
  Sec-WebSocket-Key: ...

Server responds:
  101 Switching Protocols

Stream events (every 10s or on state change):
  {
    "type": "health_update",
    "healthScore": 8.4,
    "wipCount": 6,
    "affectedCases": [],
    "timestamp": "2026-02-28T14:35:10Z"
  }
```

---

### Contract 3: Work Item Completion API

**Endpoint**: `POST /api/v1/work-items/{workItemId}/complete`

**Request**:

```http
POST /api/v1/work-items/wi-456/complete
Host: yawl.company.com
Authorization: Bearer {sessionHandle}
Content-Type: application/json

{
  "caseId": "case-12345",
  "workItemId": "wi-456",
  "status": "completed",
  "outputData": {
    "reviewApprovedBy": "alice.smith@company.com",
    "commitsIncluded": ["abc123def456", "xyz789uvw012"],
    "coverageImproved": true
  },
  "completionTimestamp": "2026-02-28T14:35:00Z"
}
```

**Response**:

```json
{
  "workItemId": "wi-456",
  "caseId": "case-12345",
  "status": "completed",
  "nextWorkItems": [
    {
      "workItemId": "wi-457",
      "title": "Deploy to staging",
      "assignee": "devops.team@company.com"
    }
  ],
  "caseProgress": {
    "completedTasks": 5,
    "totalTasks": 7,
    "progressPercentage": 71.4
  },
  "timestamp": "2026-02-28T14:35:01Z"
}
```

**Workflow Consequence**:

- If all tasks in case complete → case state transitions to "closed"
- YAWL triggers post-completion: GL posting, dashboard update, notifications
- Azure DevOps work item also marked completed (via webhook)

---

### Contract 4: Dependency Management API

**Endpoint**: `GET /api/v1/cases/{caseId}/dependencies`

**Purpose**: Identify blocked work and enable cross-ART dependency visualization.

**Response**:

```json
{
  "caseId": "case-12345",
  "dependencies": {
    "blockers": [
      {
        "blockingCaseId": "case-99999",
        "blockingArtId": "ART-XYZ",
        "reason": "Platform API not ready",
        "estimatedUnblockDate": "2026-03-10",
        "severity": "critical"
      }
    ],
    "dependents": [
      {
        "dependentCaseId": "case-54321",
        "dependentArtId": "ART-ABC",
        "reason": "Awaits real-time fraud module",
        "waitingSince": "2026-02-20"
      }
    ],
    "externalDependencies": [
      {
        "systemName": "ServiceNow CMDB",
        "resourceName": "Production K8s cluster approval",
        "approvalStatus": "pending",
        "approverEmail": "infra-lead@company.com"
      }
    ]
  }
}
```

---

### Contract 5: Metrics Streaming API

**Endpoint**: `GET /api/v1/metrics/stream`

**Purpose**: Real-time metrics for dashboard and BI tools.

**WebSocket Message Format**:

```json
{
  "type": "metric_update",
  "namespace": "portfolio",
  "name": "earned_value_total",
  "value": 145000,
  "unit": "USD",
  "timestamp": "2026-02-28T14:35:10Z",
  "tags": {
    "artId": "ART-ABC",
    "quarter": "Q1-2026"
  }
}
```

**Supported Metrics**:

| Namespace | Metric | Unit | Update Freq |
|-----------|--------|------|-------------|
| portfolio | earned_value_total | USD | Per case completion |
| portfolio | planned_value_total | USD | Per sprint start |
| art | velocity_current | points | Per sprint end |
| art | health_score | 0-10 | Real-time |
| case | cycle_time_mean | days | Hourly |
| customer | nps_score | -100 to 100 | Per feedback |
| financial | budget_variance | USD | Daily |
| incident | open_count | count | Real-time |

---

## MCP TOOL SPECIFICATIONS

### Tool Inventory (All Fortune 5 Integrations)

| Tool Name | System | Input Args | Output Type | Timeout | Idempotent |
|-----------|--------|-----------|------------|---------|-----------|
| `jira_align_query_art_health` | Jira Align | artId: str | JSON | 2s | Yes |
| `jira_align_query_pi_planning` | Jira Align | piId: str | JSON | 3s | Yes |
| `jira_align_list_teams` | Jira Align | None | JSON | 5s | Yes |
| `jira_align_sync_case_to_story` | Jira Align | caseId, storyKey, status | JSON | 3s | Yes |
| `azure_devops_list_sprints` | Azure DevOps | projectId: str | JSON | 3s | Yes |
| `azure_devops_sync_work_item` | Azure DevOps | workItemId, state, points | JSON | 3s | Yes |
| `azure_devops_query_pr_metrics` | Azure DevOps | prId: int | JSON | 2s | Yes |
| `github_copilot_suggest_refactoring` | GitHub | filePath, context | JSON | 5s | No |
| `github_copilot_repository_metrics` | GitHub | repoName: str | JSON | 2s | Yes |
| `salesforce_get_customer_sentiment` | Salesforce | customerId: str | JSON | 3s | Yes |
| `salesforce_query_demand_pipeline` | Salesforce | None | JSON | 3s | Yes |
| `salesforce_get_account_health` | Salesforce | accountId: str | JSON | 2s | Yes |
| `oracle_query_cost_center_budget` | Oracle | costCenterId: str | JSON | 3s | Yes |
| `sap_query_project_financial_snapshot` | SAP | projectId: str | JSON | 5s | Yes |
| `oracle_post_gl_entry` | Oracle | caseId, projectId, account, amount | JSON | 4s | Yes |
| `servicenow_create_incident_from_case` | ServiceNow | caseId, reason, severity | JSON | 3s | Yes |
| `servicenow_query_related_incidents` | ServiceNow | caseId: str | JSON | 2s | Yes |
| `servicenow_update_change_status` | ServiceNow | changeId, status | JSON | 2s | Yes |

---

## Z.AI INTEGRATION FOR AUTONOMOUS AGENTS

### Autonomous Decision Framework

The YAWL engine integrates with Z.AI (Zhipu.ai LLM) via `ZHIPU_API_KEY` to enable autonomous case routing, priority adjustment, and risk recommendations.

**Credential Requirement**:

```bash
export ZHIPU_API_KEY=<api-key-from-vault>
```

### Tool 1: Autonomous Priority Adjustment

**MCP Tool**: `z_ai_recommend_priority_adjustment`

**Input**:

```json
{
  "caseId": "case-12345",
  "currentPriority": "high",
  "context": {
    "customerNps": 2,
    "revenue": 500000,
    "incidentCount": 1,
    "blockingOtherCases": 2,
    "resourcesAvailable": "low"
  }
}
```

**Z.AI Prompt**:

```
You are a SAFe portfolio optimization agent. A feature is currently marked "high" priority.

Case ID: case-12345
Context:
- Customer satisfaction (NPS): 2 (very dissatisfied)
- Customer annual revenue: $500K (significant)
- Related incidents: 1 critical
- Blocking other cases: 2
- Team capacity: Low (92% allocated)

Recommend: Keep priority "high" or escalate to "critical"?
Reasoning: [provide 2-3 sentences]
```

**Z.AI Response**:

```json
{
  "recommendedPriority": "critical",
  "reasoning": "Customer satisfaction has degraded (NPS=2) and this is a significant revenue account ($500K). The case is blocking other delivery work. Escalating to critical prioritizes resolution.",
  "confidence": 0.87,
  "alternativeActions": [
    "Assign senior engineer to unblock",
    "Escalate to product VP for customer communication"
  ]
}
```

**YAWL Action**:

```java
if (recommendation.confidence > 0.80) {
    caseState.setPriority(recommendation.recommendedPriority);
    auditLog.record("Priority escalated by Z.AI recommendation");
    notificationService.send(productVp, "High-risk case escalation");
}
```

### Tool 2: Root Cause Analysis (Incident → Recommendation)

**MCP Tool**: `z_ai_analyze_incident_root_cause`

**Trigger**: ServiceNow incident created with YAWL case correlation.

**Input**:

```json
{
  "incidentId": "INC-98765",
  "incidentDescription": "Fraud detection API returning 503 errors intermittently",
  "affectedCaseId": "case-12345",
  "relatedIncidents": [
    "INC-98764 (related system failure 2 weeks ago)"
  ],
  "eventLog": [
    "2026-02-28 14:30 - API latency spike to 5s",
    "2026-02-28 14:31 - 50% of requests timeout",
    "2026-02-28 14:32 - Circuit breaker opens"
  ]
}
```

**Z.AI Analysis**:

```
Given the fraud detection API is returning 503 intermittently, with a latency spike preceding the failure:

1. Most likely cause: Resource exhaustion (CPU/memory spike on backend pods)
2. Secondary cause: External dependency (database connection pool exhausted)
3. Related incident 2 weeks ago suggests potential recurring issue

Recommended actions:
- Check Kubernetes pod metrics for CPU/memory usage
- Query database connection pool status
- Review deployment logs for recent changes
- Enable detailed tracing on next occurrence
```

**YAWL Workflow Trigger**:

```
incident created
  ├─ → Create case type "Incident Investigation"
  ├─ → Assign to on-call SRE
  ├─ → Call z_ai_analyze_incident_root_cause()
  ├─ → Update case.analysisRecommendations with Z.AI output
  ├─ → Create follow-up work items from recommendations
  └─ → Escalate if Z.AI confidence < 0.60 (manual review needed)
```

### Tool 3: AI-Assisted Specification Synthesis

**MCP Tool**: `z_ai_synthesize_workflow_spec`

**Scenario**: Product manager describes a new workflow; Z.AI generates YAWL specification skeleton.

**Input**:

```json
{
  "workflowDescription": "Sales order processing: receive order, validate inventory, create shipment, send invoice.",
  "constraints": {
    "maxTaskDuration": "2 hours",
    "parallelization": "allowed",
    "externalSystems": ["Salesforce", "Warehouse Management System", "Billing System"]
  }
}
```

**Z.AI Output** (YAWL YAML):

```yaml
specification:
  name: sales_order_processing
  nets:
    - id: order_processing_net
      tasks:
        - id: receive_order
          description: "Receive order from Salesforce"
          postset: ["validate_inventory"]

        - id: validate_inventory
          description: "Check warehouse availability"
          postset: ["inventory_ok", "inventory_fail"]

        - id: inventory_ok
          description: "Inventory sufficient; proceed to shipment"
          postset: ["create_shipment"]

        - id: inventory_fail
          description: "Insufficient inventory; notify customer"
          postset: ["notify_customer"]

        - id: create_shipment
          description: "Create shipment in WMS"
          postset: ["send_invoice"]

        - id: send_invoice
          description: "Send invoice via Billing System"
          postset: []  # Completion

      xor_splits:
        - id: validate_inventory
          branches: ["inventory_ok", "inventory_fail"]
```

**YAWL Workflow** (post-generation):

```
Designer reviews synthesized spec
  ├─ Approves structure (80% reduces manual effort)
  ├─ Customizes task assignments, timeouts, data mappings
  └─ Uploads to YAWL engine for testing
```

---

## RESILIENCE & OBSERVABILITY

### Resilience Patterns

#### Pattern 1: Circuit Breaker (Jira Align)

```java
public class JiraAlignCircuitBreaker {
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;
    private int failureCount = 0;
    private Instant lastFailureTime;
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESET_TIMEOUT = Duration.ofMinutes(1);

    public ArtHealthSnapshot call(String artId) throws CircuitBreakerOpenException {
        if (state == State.OPEN) {
            if (Instant.now().isAfter(lastFailureTime.plus(RESET_TIMEOUT))) {
                state = State.HALF_OPEN;
            } else {
                throw new CircuitBreakerOpenException(
                    "Jira Align circuit breaker open; retry after " +
                    RESET_TIMEOUT.toMinutes() + " minutes"
                );
            }
        }

        try {
            var future = asyncClient.queryArtHealth(artId)
                .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            var result = future.get();

            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                failureCount = 0;
            }
            return result;
        } catch (TimeoutException | ExecutionException e) {
            failureCount++;
            lastFailureTime = Instant.now();

            if (failureCount >= FAILURE_THRESHOLD) {
                state = State.OPEN;
                log.warn("Jira Align circuit breaker opened after {} failures", failureCount);
            }
            throw new CircuitBreakerFailureException("Jira Align call failed", e);
        }
    }
}
```

#### Pattern 2: Exponential Backoff (Oracle GL Posting)

```java
public class ExponentialBackoffRetry {
    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_DELAY_MS = 100;

    public GlPostingResponse postGlEntry(GlPostingRequest req)
            throws PermanentFailureException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return oracleClient.postEntry(req);
            } catch (TransientException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new PermanentFailureException("Max retries exceeded", e);
                }
                long delayMs = INITIAL_DELAY_MS * (long) Math.pow(2, attempt - 1);
                log.info("GL posting retry {} after {} ms", attempt, delayMs);
                Thread.sleep(delayMs);
            }
        }
        throw new PermanentFailureException("Unreachable");
    }
}
```

#### Pattern 3: Bulkhead (Per-ART Isolation)

```java
public class PerArtBulkhead {
    private final Map<String, Semaphore> artSemaphores = new ConcurrentHashMap<>();

    public <T> T callWithBulkhead(String artId, Callable<T> task)
            throws InterruptedException, Exception {
        Semaphore semaphore = artSemaphores.computeIfAbsent(
            artId,
            id -> new Semaphore(5)  // Max 5 concurrent calls per ART
        );

        semaphore.acquire();
        try {
            return task.call();
        } finally {
            semaphore.release();
        }
    }
}
```

### Observability & Tracing

#### Structured Logging (Example: Jira Align)

```java
public class JiraAlignMetrics {
    private static final Meter meter = GLOBAL_METRICS.newMeter();
    private static final Timer queryTimer = meter.newTimer("jira_align.query.duration");

    public ArtHealthSnapshot queryWithMetrics(String artId) {
        var context = queryTimer.time();
        try {
            var snapshot = jiraAlignClient.queryArtHealth(artId);

            log.structured()
                .field("system", "jira_align")
                .field("operation", "query_art_health")
                .field("artId", artId)
                .field("healthScore", snapshot.healthScore())
                .field("durationMs", context.elapsed(TimeUnit.MILLISECONDS))
                .field("status", "success")
                .log("Jira Align query completed");

            return snapshot;
        } catch (Exception e) {
            log.structured()
                .field("system", "jira_align")
                .field("operation", "query_art_health")
                .field("artId", artId)
                .field("durationMs", context.elapsed(TimeUnit.MILLISECONDS))
                .field("status", "failure")
                .field("errorType", e.getClass().getSimpleName())
                .field("errorMessage", e.getMessage())
                .log("Jira Align query failed");
            throw e;
        } finally {
            context.stop();
        }
    }
}
```

#### Correlation IDs (Request Tracing)

```java
public class CorrelationIdFilter {
    private static final ScopedValue<String> CORRELATION_ID =
        ScopedValue.newInstance();

    public void onMcpToolCall(String toolName, Map<String, Object> input) {
        String correlationId = UUID.randomUUID().toString();

        ScopedValue.callWhere(CORRELATION_ID, correlationId, () -> {
            log.structured()
                .field("correlationId", correlationId)
                .field("toolName", toolName)
                .field("input", input)
                .log("MCP tool invoked");

            // Virtual thread inherits correlation ID automatically
            Thread.ofVirtual()
                .name("mcp-" + toolName)
                .start(() -> mcpServer.invokeTool(toolName, input));
        });
    }
}
```

---

## DEPLOYMENT & OPERATIONS

### Kubernetes Deployment (Fortune 5 SRE)

**Helm Chart Structure**:

```
yawl-fortune5-integration/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── yawl-engine-deployment.yaml
│   ├── mcp-server-configmap.yaml
│   ├── external-system-secrets.yaml
│   ├── pvc-event-sourcing.yaml
│   ├── service-mesh-virtualservice.yaml
│   └── monitoring/
│       ├── prometheus-servicemonitor.yaml
│       └── grafana-dashboard-configmap.yaml
└── tests/
    ├── integration-test.yaml
    └── smoke-test.yaml
```

**Key Deployment Considerations**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-orchestration-engine
  namespace: fortune5-platform
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
    spec:
      serviceAccountName: yawl-engine

      # Java 25 settings for virtual threads + compact headers
      containers:
      - name: yawl-engine
        image: company.azurecr.io/yawl:6.0.0
        env:
        - name: JAVA_OPTS
          value: >
            -XX:+UseCompactObjectHeaders
            -Djdk.virtualThreadScheduler.parallelism=4
            -Djdk.virtualThreadScheduler.maxPoolSize=256

        # Credentials from vault
        envFrom:
        - secretRef:
            name: yawl-external-credentials

        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: metrics

        # Liveness (case engine must process cases)
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

        # Readiness (wait for external systems connected)
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 5

        # Resource limits (virtual threads don't consume OS threads)
        resources:
          requests:
            memory: 4Gi
            cpu: 2
          limits:
            memory: 8Gi
            cpu: 4
```

### Health Checks

```java
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        // Returns 200 if JVM is alive
        return ResponseEntity.ok(Map.of("status", "alive"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("yawl_engine", yawlEngine.isRunning() ? "up" : "down");
        health.put("jira_align", checkJiraAlign() ? "up" : "down");
        health.put("azure_devops", checkAzureDevOps() ? "up" : "down");
        health.put("oracle_database", checkOracleDb() ? "up" : "down");
        health.put("event_sourcing", eventStore.isHealthy() ? "up" : "down");

        boolean allHealthy = health.values().stream()
            .allMatch(v -> "up".equals(v));

        return allHealthy
            ? ResponseEntity.ok(health)
            : ResponseEntity.status(503).body(health);
    }
}
```

---

## COST MODEL & ROI

### Operational Costs (Annual)

| Component | Type | Unit Cost | Annual |
|-----------|------|-----------|--------|
| Jira Align | SaaS | $50K/org | $50K |
| Azure DevOps | SaaS | $40K/org | $40K |
| Salesforce | SaaS | $80K | $80K |
| SAP/Oracle | License | Existing | $0 (amortized) |
| ServiceNow | SaaS | $120K | $120K |
| Z.AI API calls | Consumption | $0.002/call | $12K (6M calls/year) |
| Kubernetes infrastructure | Cloud | $8K/month | $96K |
| **TOTAL OPEX** | | | **$398K** |

### Value Creation (Annual)

| Benefit | Calculation | Annual Value |
|---------|-----------|--------------|
| **Time saved** (ART planning) | 50 ARTs × 20h/sprint saved × $200/h | $1.04M |
| **Improved velocity** | 15% velocity increase × $2M annual dev capacity | $300K |
| **Risk mitigation** | 2 production incidents prevented × $500K impact | $1M |
| **Customer retention** | 5 high-value accounts @ risk, saved via NPS monitoring | $2.5M |
| **Financial accuracy** | 100% GL posting accuracy; audit exceptions → 0 | $200K |
| **Decision speed** | Z.AI recommendations eliminate 2-day leadership cycles | $400K |
| **Total realized** | | **$5.4M** |

### ROI & Payback Period

```
Net Annual Benefit = Value Created - Opex
                   = $5.4M - $398K
                   = $5.0M

ROI = (Net Benefit / Opex) × 100%
    = ($5.0M / $398K) × 100%
    = 1255%

Payback Period = Opex / Monthly Benefit
               = $398K / ($5.0M / 12)
               = $398K / $416.7K
               ≈ 1.1 months
```

---

## CONCLUSION

This document defines a production-grade SAFe portfolio orchestration platform built on YAWL 6.0 with MCP-based integrations to Fortune 5 enterprise systems. The architecture enables:

- **Real-time portfolio visibility** (<2s latency)
- **Autonomous decision-making** via Z.AI
- **Zero-fallback resilience** (fail-fast semantics)
- **Complete audit trails** (event sourcing + GDPR compliance)
- **Massive scalability** (millions of concurrent cases via virtual threads)
- **Positive ROI** in <2 months (1255% annual return)

The system prioritizes **production safety** over convenience: all credentials flow from environment variables, all external calls timeout and backoff, all generated code must pass H-guards validation, and all promises are kept (no silent fallbacks).

**Reference Implementation**: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spec/` and `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/marketplace/`

---

**Document Version**: 1.0.0
**Last Updated**: 2026-02-28
**Author**: YAWL Architecture Team
**Status**: Ready for Implementation Sprint
