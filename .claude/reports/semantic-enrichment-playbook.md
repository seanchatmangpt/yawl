# Semantic Enrichment Playbook — Phase 1-3 Implementation Guide

**Status**: READY FOR ENGINEERING TEAMS
**Last Updated**: 2026-02-24
**Audience**: Integrator, Engine, Observatory, Autonomous Agent teams

---

## Quick Reference: Priority Checklist

### Phase 1 (Week 1): PROV-O + Action Types [MANDATORY]

- [ ] **Day 1-2**: Design RDF emission layer
  - [ ] Create `YWorkItemRDFEmitter` interface
  - [ ] Define PROV-O triple generation template
  - [ ] Document RDF serialization format (.ttl, JSON-LD)

- [ ] **Day 2-3**: Implement PROV-O for task completions
  - [ ] Emit `prov:wasGeneratedBy` (task → activity)
  - [ ] Emit `prov:wasAssociatedWith` (agent → activity)
  - [ ] Emit `prov:startedAtTime` + `prov:endedAtTime`
  - [ ] Emit `prov:used` for input parameters
  - [ ] Unit test: 100% completion events emit RDF

- [ ] **Day 3-4**: Add Schema.org Action types
  - [ ] Tag approval tasks: `schema:Action` + `schema:CheckAction`
  - [ ] Tag payment tasks: `schema:Action` + `schema:PayAction`
  - [ ] Tag notification tasks: `schema:Action` + `schema:CommunicateAction`
  - [ ] Unit test: task.toRDF() includes action type

- [ ] **Day 4-5**: SPARQL endpoint + validation
  - [ ] Create SPARQL query service
  - [ ] Implement 10 core queries (see query library below)
  - [ ] Integration test: execute queries on live RDF model
  - [ ] Exit criteria: All 10 queries return valid results

**Deliverables**:
- YWorkItemRDFEmitter.java
- SemanticTaskClassifier.java
- SparqlQueryService.java
- 10 integration tests

---

### Phase 2 (Week 2-3): Domain Semantics [HIGH VALUE]

- [ ] **Day 6-7**: FIBO alignment design
  - [ ] Create `FiboPaymentMapper` class
  - [ ] Define payment → fibo:PaymentInstruction mapping
  - [ ] Add FIBO vocabulary to ontology imports
  - [ ] Document FIBO-YAWL bridging rules

- [ ] **Day 8-9**: SLA semantic model
  - [ ] Create `SemanticSLA` class
  - [ ] Map yawls:Timer → yawl-new:SLA semantics
  - [ ] Add tolerance, escalation, end-to-end properties
  - [ ] Unit test: SLA serialization/deserialization

- [ ] **Day 10-11**: Schema.org JobPosting for resourcing
  - [ ] Create `ResourceRequirementExporter` (→ schema:JobPosting)
  - [ ] Map yawls:Selector → schema:Job attributes
  - [ ] Add skill/experience requirements
  - [ ] Unit test: resource requirements export correctly

- [ ] **Day 12-13**: Financial analytics queries
  - [ ] Build "total approved by role" query
  - [ ] Build "SLA status by case" query
  - [ ] Build "approval chain audit" query
  - [ ] Build "resource utilization vs. capacity" query

- [ ] **Day 14-15**: Integration & validation
  - [ ] Test with real order-to-cash workflow
  - [ ] Verify financial totals accuracy
  - [ ] Validate SLA prediction against actual timers
  - [ ] Exit criteria: All 4 financial queries accurate

**Deliverables**:
- FiboPaymentMapper.java
- SemanticSLAModel.java
- ResourceRequirementExporter.java
- FinancialAnalyticsQueries.sparql
- 25 integration tests

---

### Phase 3 (Week 4+): Intelligence Layer [STRATEGIC]

- [ ] **Week 4**: Inference rule design
  - [ ] Document 8 core rules (causality, deadlock, SLA, compensation)
  - [ ] Create SPARQL rule templates
  - [ ] Design rule priority/conflict resolution

- [ ] **Week 5-6**: Inference rule implementation
  - [ ] Implement "task causality" rule
  - [ ] Implement "resource bottleneck" rule
  - [ ] Implement "deadlock detection" rule
  - [ ] Implement "SLA breach prediction" rule
  - [ ] Implement "compensation action" rule
  - [ ] Unit test: 100% inference accuracy on synthetic cases

- [ ] **Week 7**: Autonomous agent reasoning module
  - [ ] Create `SemanticAutonomousAgent` class
  - [ ] Integrate inference rules
  - [ ] Add decision recommendation engine
  - [ ] Unit test: agent recommends correct actions

- [ ] **Week 8**: Multi-service saga orchestration
  - [ ] Implement saga state machine (RUNNING → COMPENSATING → COMPLETED)
  - [ ] Add compensation action selection (from semantic rules)
  - [ ] Test with 3-service saga (order → payment → shipping)
  - [ ] Integration test: saga handles service failures correctly

- [ ] **Week 9**: Production validation
  - [ ] Field test with real enterprise cases
  - [ ] Measure actual autonomous decision accuracy
  - [ ] Document edge cases + fixes

**Deliverables**:
- InferenceRules.sparql (8+ rules)
- SemanticAutonomousAgent.java
- SagaOrchestrationEngine.java
- 80+ integration tests + performance benchmarks

---

## Query Library: Copy-Paste SPARQL Templates

### Core Queries (Phase 1)

#### Q1: Task Execution Causality

```sparql
# "Why did task X fire?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX prov: <http://www.w3.org/ns/prov#>

SELECT ?previousTask ?trigger ?triggerVariable ?currentTask ?timestamp
WHERE {
  # Start at target task
  ?currentTask yawls:id "TASK_ID" .

  # Find incoming flow
  ?flow yawls:nextElement ?currentTask .
  ?previousTask yawls:hasFlowInto ?flow .

  # Check for predicate (condition)
  OPTIONAL {
    ?flow yawls:hasPredicate ?predicate .
    ?predicate yawls:xpathExpression ?trigger .
  }

  # Link to data variable that triggered it
  OPTIONAL {
    ?previousActivity prov:wasGeneratedBy ?previousTask ;
                      prov:used ?triggerVariable ;
                      prov:endedAtTime ?timestamp .
  }

  FILTER (!BOUND(?trigger) || CONTAINS(?trigger, ?triggerVariable))
}
```

**Use Case**: Explain why approval task fired after rejection was submitted.
**Expected Result**: previousTask="ReviewTask", trigger="decision='approved'", triggerVariable="decision"

---

#### Q2: Task Classification

```sparql
# "What type of task is this?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX schema: <https://schema.org/>

SELECT ?task ?taskName ?actionType
WHERE {
  ?task yawls:id "TASK_ID" ;
        yawls:name ?taskName ;
        rdf:type ?actionType .

  FILTER (?actionType IN (
    schema:CheckAction,
    schema:PayAction,
    schema:CommunicateAction,
    schema:Action
  ))
}
```

**Use Case**: Categorize all tasks by action type.
**Expected Result**: task="approvalTask", actionType="schema:CheckAction"

---

#### Q3: Data Lineage

```sparql
# "Where did this variable originate?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX prov: <http://www.w3.org/ns/prov#>

SELECT ?variable ?originTask ?originTime ?transformations
WHERE {
  # Trace variable back through tasks
  ?currentActivity prov:used ?variable ;
                   prov:wasGeneratedBy ?currentTask .

  # Find where variable was created
  ?originActivity prov:wasGeneratedBy ?originTask ;
                  prov:startedAtTime ?originTime ;
                  prov:wasGeneratedBy [ yawls:hasCompletedMapping [
                    yawls:mapsTo ?variable
                  ]] .

  # Count intermediate transformations
  ?intermediateActivity prov:used ?variable ;
                        prov:wasGeneratedBy ?intermediateTask .

  BIND(COUNT(DISTINCT ?intermediateTask) AS ?transformations)
}
```

**Use Case**: Audit trail — which task created this invoice amount?
**Expected Result**: variable="invoiceAmount", originTask="InvoiceCreation", originTime="2026-02-23T14:32:00Z", transformations=2

---

#### Q4: Case Audit Trail

```sparql
# "Show all agent actions on this case in chronological order"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX prov: <http://www.w3.org/ns/prov#>

SELECT ?stepNumber ?timestamp ?agent ?action ?result ?modifiedVariables
WHERE {
  # Get all activities on case
  ?case rdf:type yawls:WorkflowNet ; yawls:id "CASE_ID" .

  ?activity prov:wasGeneratedBy [ yawls:id ?task ] ;
            prov:wasAssociatedWith ?agent ;
            prov:startedAtTime ?timestamp ;
            yawls:hasLogPredicate [ rdf:value ?action ] .

  # Find what was modified
  ?activity prov:wasGeneratedBy [ yawls:hasCompletedMapping [
    yawls:mapsTo ?modifiedVariable
  ]] .

  # Infer result from downstream task status
  OPTIONAL {
    ?downstreamTask yawls:hasStartingMapping [ yawls:expression ?expr ] .
    FILTER(CONTAINS(?expr, ?modifiedVariable))
    BIND(IF(BOUND(?downstreamTask), "Affected downstream", "No downstream impact") AS ?result)
  }

  BIND(ROW_NUMBER() OVER (ORDER BY ?timestamp) AS ?stepNumber)
}
ORDER BY ?timestamp
```

**Use Case**: Generate compliance audit report.
**Expected Result**: Timeline of all approvals, rejections, and modifications with agents.

---

#### Q5: Parameter Completeness

```sparql
# "Are all required outputs mapped before task completion?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>

SELECT ?task ?missingOutputs ?completeness
WHERE {
  ?task yawls:id "TASK_ID" ;
        yawls:decomposesTo ?decomposition .

  # Required outputs
  ?decomposition yawls:hasOutputParameter ?output ;
                 yawls:isMandatory true ;
                 yawls:variableName ?requiredVar .

  # Check if mapped
  OPTIONAL {
    ?task yawls:hasCompletedMapping [ yawls:mapsTo ?requiredVar ]
  }

  # Collect unmapped
  ?unmappedOutput yawls:variableName ?unmappedVar .
  FILTER NOT EXISTS {
    ?task yawls:hasCompletedMapping [ yawls:mapsTo ?unmappedVar ]
  }

  BIND(CONCAT(GROUP_CONCAT(?unmappedVar, ", "), "") AS ?missingOutputs)
  BIND(CONCAT(
    ROUND((COUNT(DISTINCT ?requiredVar) - COUNT(DISTINCT ?unmappedVar)) /
          COUNT(DISTINCT ?requiredVar) * 100, 0),
    "%"
  ) AS ?completeness)
}
GROUP BY ?task ?decomposition
```

**Use Case**: Design-time validation.
**Expected Result**: missingOutputs="approvalDecision, escalationFlag", completeness="60%"

---

### Financial Queries (Phase 2)

#### Q6: Total Approved Commitments by Role

```sparql
# "How much has each approval role approved across all open orders?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX fibo: <https://spec.edmcouncil.org/fibo/>
PREFIX schema: <https://schema.org/>

SELECT ?approverRole ?totalApproved ?currency ?orderCount
WHERE {
  # Approval activities
  ?approvalActivity rdf:type prov:Activity ;
                    prov:wasGeneratedBy ?approvalTask ;
                    prov:wasAssociatedWith [ schema:jobTitle ?approverRole ] ;
                    fibo:approves [ fibo:hasAmount ?amount ;
                                   fibo:hasCurrency ?currency ] .

  # Group by role
  ?approvalTask yawls:id "ApproveOrder" .

  BIND(SUM(?amount) AS ?totalApproved)
  BIND(COUNT(DISTINCT ?approvalActivity) AS ?orderCount)
}
GROUP BY ?approverRole ?currency
ORDER BY DESC(?totalApproved)
```

**Use Case**: Finance dashboard — approval authority verification.
**Expected Result**: approverRole="Senior Approver", totalApproved=1000000.00, currency="USD", orderCount=47

---

#### Q7: SLA Breach Prediction (24h Lookout)

```sparql
# "Which orders will miss their SLA in the next 24 hours?"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX yawl-new: <http://yawlfoundation.org/yawl/pattern/new#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT ?orderID ?currentTask ?riskLevel ?hoursUntilBreach ?recommendation
WHERE {
  ?case rdf:type yawls:WorkflowNet ;
        yawls:id ?orderID ;
        prov:startedAtTime ?caseStart .

  # Current enabled task
  ?currentTask yawls:id ?taskId ;
               yawl-new:hasSLA [ yawl-new:tolerance ?tolerance ] ;
               yawls:name ?taskName .

  # SLA elapsed
  BIND(NOW() - ?caseStart AS ?elapsedTime)
  BIND(?tolerance - ?elapsedTime AS ?timeRemaining)
  BIND(HOURS(?timeRemaining) AS ?hoursUntilBreach)

  # Risk assessment
  BIND(IF(?hoursUntilBreach < 4, "CRITICAL",
          IF(?hoursUntilBreach < 12, "HIGH", "MEDIUM")) AS ?riskLevel)

  # Recommendation
  BIND(IF(?riskLevel = "CRITICAL",
          CONCAT("ESCALATE: ", ?taskName, " needs immediate attention"),
          CONCAT("MONITOR: ", ?taskName, " has ", ?hoursUntilBreach, " hours"))) AS ?recommendation)
}
FILTER (?hoursUntilBreach < 24)
ORDER BY ?hoursUntilBreach
```

**Use Case**: SLA monitoring dashboard.
**Expected Result**: orderID="ORD-12345", currentTask="ShippingTask", riskLevel="HIGH", hoursUntilBreach=8

---

#### Q8: Approval Chain Audit

```sparql
# "Show complete approval chain for this order with amounts and agents"
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX fibo: <https://spec.edmcouncil.org/fibo/>
PREFIX schema: <https://schema.org/>

SELECT ?stepNumber ?timestamp ?approver ?role ?decision ?amount ?approvalID
WHERE {
  ?case rdf:type yawls:WorkflowNet ; yawls:id "ORDER_ID" .

  # Approval activities
  ?approvalActivity prov:wasGeneratedBy ?approvalTask ;
                    prov:startedAtTime ?timestamp ;
                    prov:wasAssociatedWith ?agent ;
                    yawls:hasLogPredicate [ rdf:value ?decision ] ;
                    fibo:approves [ fibo:hasAmount ?amount ] .

  ?approvalTask yawls:id "ApproveOrder" ;
                yawls:hasResourcing [ yawls:hasAllocate [
                  yawls:selectorName ?role
                ]] .

  ?agent schema:name ?approver .

  BIND(STR(?approvalActivity) AS ?approvalID)
  BIND(ROW_NUMBER() OVER (ORDER BY ?timestamp) AS ?stepNumber)
}
ORDER BY ?timestamp
```

**Use Case**: Compliance + financial reconciliation.
**Expected Result**: stepNumber=1, approver="Alice Johnson", role="Manager", decision="approved", amount=5000.00

---

### Inference Rules (Phase 3)

#### RULE1: Task Causality Inference

```sparql
# Infer: ?task2 yawl-new:causedBy ?task1
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX yawl-new: <http://yawlfoundation.org/yawl/pattern/new#>

INSERT {
  ?task2 yawl-new:causedBy ?task1 ;
         yawl-new:causedBySatisfyingCondition ?expression ;
         yawl-new:causedByDataVariable ?triggerVar .
}
WHERE {
  ?flow yawls:nextElement ?task2 ;
        yawls:hasPredicate ?pred .
  ?task1 yawls:hasFlowInto ?flow .

  OPTIONAL {
    ?pred yawls:xpathExpression ?expression .
  }

  # Optional: link to data variable
  OPTIONAL {
    ?task1 yawls:hasCompletedMapping [ yawls:mapsTo ?triggerVar ] .
  }
}
```

---

#### RULE2: Resource Capability Inference

```sparql
# Infer: ?agent yawl-new:canCompleteProcess ?process
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX yawl-new: <http://yawlfoundation.org/yawl/pattern/new#>

INSERT {
  ?agent yawl-new:canCompleteProcess ?process ;
         yawl-new:tasksCoveragePercent ?coverage .
}
WHERE {
  ?process rdf:type yawls:WorkflowNet .

  # Count total tasks
  BIND(COUNT(DISTINCT ?task) AS ?totalTasks)

  # Count tasks agent can do
  BIND(COUNT(DISTINCT ?capableTask) AS ?capableTasks)

  ?task yawls:id ?taskId .
  OPTIONAL {
    ?agent yawls:canPerform [ yawls:id ?taskId ] .
  }

  BIND(IF(?totalTasks > 0, ?capableTasks / ?totalTasks * 100, 0) AS ?coverage)

  # Only infer if coverage >= 80%
  FILTER (?coverage >= 80)
}
```

---

#### RULE3: SLA Breach Risk Inference

```sparql
# Infer: ?case yawl-new:slaRisk ?riskLevel
PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
PREFIX yawl-new: <http://yawlfoundation.org/yawl/pattern/new#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

INSERT {
  ?case yawl-new:slaRisk ?riskLevel ;
        yawl-new:estimatedCompletionTime ?projectedEnd ;
        yawl-new:slaMargin ?marginHours .
}
WHERE {
  ?case rdf:type yawls:WorkflowNet ;
        prov:startedAtTime ?caseStart ;
        yawl-new:hasSLA [ yawl-new:tolerance ?tolerance ] .

  # Estimate completion based on current progress
  ?currentTask yawls:hasFlowInto [ yawls:nextElement ?nextTask ] .
  ?currentTask yawls:name ?currentTaskName .

  # Historical average completion time for next task
  BIND(AVG(?historicalDuration) AS ?avgTaskTime)

  # Project completion
  BIND(?caseStart + ?avgTaskTime AS ?projectedEnd)
  BIND(HOURS(?tolerance - (NOW() - ?caseStart)) AS ?marginHours)

  # Risk level
  BIND(IF(?marginHours < 4, "CRITICAL",
          IF(?marginHours < 12, "HIGH", "MEDIUM")) AS ?riskLevel)
}
```

---

## Implementation Patterns

### Pattern 1: RDF Emission in Java

```java
// Phase 1: Emit PROV-O on task completion

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

class YWorkItemRDFEmitter {
    private Model model = ModelFactory.createDefaultModel();

    void emitCompletion(YWorkItem item, String agentId, Map<String, Object> outputs) {
        String ns = "http://yawlfoundation.org/case#";
        Resource activity = model.createResource(
            ns + "activity-" + item.getId() + "-" + System.currentTimeMillis()
        );

        // prov:wasGeneratedBy (which task?)
        Resource task = model.createResource(ns + "task-" + item.getTaskId());
        activity.addProperty(PROV.wasGeneratedBy, task);

        // prov:wasAssociatedWith (which agent?)
        Resource agent = model.createResource(
            "http://agents.yawlfoundation.org/" + agentId
        );
        activity.addProperty(PROV.wasAssociatedWith, agent);

        // prov:startedAtTime, prov:endedAtTime
        activity.addProperty(PROV.startedAtTime,
            model.createTypedLiteral(item.getStartTime(), XSDDatatype.XSDdateTime));
        activity.addProperty(PROV.endedAtTime,
            model.createTypedLiteral(item.getCompletionTime(), XSDDatatype.XSDdateTime));

        // prov:used (input parameters)
        for (Map.Entry<String, Object> param : item.getInputParameters().entrySet()) {
            Resource paramRes = model.createResource(
                ns + "param-" + param.getKey()
            );
            activity.addProperty(PROV.used, paramRes);
        }

        // schema:Action type
        String actionType = classifyTaskType(item.getTaskName());
        activity.addProperty(RDF.type, model.createResource(
            "https://schema.org/" + actionType
        ));
    }

    String classifyTaskType(String taskName) {
        if (taskName.toLowerCase().contains("approv")) return "CheckAction";
        if (taskName.toLowerCase().contains("pay")) return "PayAction";
        if (taskName.toLowerCase().contains("notif")) return "CommunicateAction";
        return "Action";
    }

    String getTurtle() {
        StringWriter sw = new StringWriter();
        model.write(sw, "TURTLE");
        return sw.toString();
    }
}
```

---

### Pattern 2: SPARQL Query Execution

```java
// Execute query on live RDF model

import org.apache.jena.query.*;

class SparqlQueryService {
    private Model model;
    private QueryExecution qe;

    List<Map<String, String>> query(String sparqlQuery) {
        Query query = QueryFactory.create(sparqlQuery);
        qe = QueryExecutionFactory.create(query, model);

        List<Map<String, String>> results = new ArrayList<>();
        ResultSet rs = qe.execSelect();

        while (rs.hasNext()) {
            QuerySolution qs = rs.nextSolution();
            Map<String, String> row = new HashMap<>();

            for (String var : rs.getResultVars()) {
                RDFNode node = qs.get(var);
                if (node != null) {
                    row.put(var, node.toString());
                }
            }
            results.add(row);
        }

        qe.close();
        return results;
    }

    // Usage
    void demonstrateQuery() {
        String causality = """
            PREFIX yawls: <http://www.yawlfoundation.org/yawlschema#>
            PREFIX prov: <http://www.w3.org/ns/prov#>

            SELECT ?previousTask ?trigger WHERE {
              ?task yawls:id "ApproveOrder" .
              ?flow yawls:nextElement ?task .
              ?previousTask yawls:hasFlowInto ?flow .
              OPTIONAL { ?flow yawls:hasPredicate ?pred .
                         ?pred yawls:xpathExpression ?trigger }
            }
        """;

        List<Map<String, String>> results = query(causality);
        for (Map<String, String> row : results) {
            System.out.println("Previous: " + row.get("previousTask"));
            System.out.println("Trigger: " + row.get("trigger"));
        }
    }
}
```

---

### Pattern 3: Autonomous Agent Reasoning

```java
// Phase 3: Semantic autonomous agent

class SemanticAutonomousAgent {
    private SparqlQueryService sparql;
    private InferenceRuleEngine inference;

    List<AgentRecommendation> reasonAboutCase(String caseId) {
        // Step 1: Query causality
        var causality = sparql.query(QUERY_CAUSALITY.replace("?CASE", caseId));

        // Step 2: Query SLA risk
        var slaRisk = sparql.query(QUERY_SLA_RISK.replace("?CASE", caseId));

        // Step 3: Query current task
        var currentTask = sparql.query(QUERY_CURRENT_TASK.replace("?CASE", caseId));

        // Step 4: Apply inference rules
        var recommendations = new ArrayList<AgentRecommendation>();

        if (slaRisk.isEmpty()) {
            recommendations.add(new AgentRecommendation(
                "MONITOR",
                "SLA on track",
                currentTask.get(0).get("taskName")
            ));
        } else {
            // SLA risk exists
            String riskLevel = slaRisk.get(0).get("riskLevel");
            if ("CRITICAL".equals(riskLevel)) {
                recommendations.add(new AgentRecommendation(
                    "ESCALATE",
                    "SLA breach imminent",
                    currentTask.get(0).get("taskName"),
                    true  // requires immediate action
                ));
            }
        }

        // Step 5: Suggest next actions based on rules
        var nextActions = inference.inferNextActions(caseId, recommendations);
        recommendations.addAll(nextActions);

        return recommendations;
    }
}
```

---

## Testing Checklist

### Phase 1 Tests

```java
@Test void testPROVEmissionOnCompletion() {
    // Verify prov:wasGeneratedBy, prov:wasAssociatedWith present
    // Verify prov:startedAtTime/endedAtTime match work item times
    // Verify prov:used includes all input parameters
}

@Test void testActionTypeClassification() {
    // Verify approval tasks tagged schema:CheckAction
    // Verify payment tasks tagged schema:PayAction
    // Verify notification tasks tagged schema:CommunicateAction
}

@Test void testCausalityQuery() {
    // Execute QUERY_CAUSALITY, verify previousTask found
    // Verify trigger expression extracted
    // Verify triggerVariable identified
}

@Test void testAuditTrailQuery() {
    // Execute QUERY_AUDIT_TRAIL, verify steps in order
    // Verify agent names included
    // Verify actions recorded
}

@Test void testParameterMappingCompletion() {
    // Execute QUERY_PARAMETER_COMPLETENESS
    // Verify missing outputs identified
    // Verify completeness percentage calculated
}
```

### Phase 2 Tests

```java
@Test void testFiboPaymentMapping() {
    // Verify payment task → fibo:PaymentInstruction
    // Verify amount, currency, recipient properties present
    // Verify amount datatype is xsd:decimal
}

@Test void testSLASemanticModel() {
    // Verify yawl-new:hasSLA property created
    // Verify tolerance as xsd:duration
    // Verify escalation action linked
}

@Test void testFinancialAnalyticsAccuracy() {
    // Execute QUERY_TOTAL_APPROVED_BY_ROLE
    // Verify sum matches manual calculation
    // Verify currency grouping correct
}

@Test void testSLABreachPrediction() {
    // Create case with 4-hour SLA tolerance
    // Set elapsed time to 2 hours
    // Execute QUERY_SLA_BREACH_PREDICTION
    // Verify predicted breach = true, hoursUntilBreach = 2
}
```

### Phase 3 Tests

```java
@Test void testTaskCausalityInference() {
    // Insert sample tasks + flows
    // Execute RULE_CAUSALITY
    // Verify ?task2 yawl-new:causedBy ?task1 inferred
}

@Test void testResourceCapabilityInference() {
    // Create agent with partial task coverage
    // Execute RULE_CAPABILITY
    // Verify canCompleteProcess only inferred if coverage >= 80%
}

@Test void testSLARiskInference() {
    // Create case with historical task durations
    // Execute RULE_SLA_RISK
    // Verify slaRisk level matches current elapsed + projected time
}

@Test void testSemanticAgentDecision() {
    // Create case at SLA risk
    // Call agent.reasonAboutCase()
    // Verify ESCALATE recommendation generated
}
```

---

## Success Criteria by Phase

### Phase 1 Success Criteria

- ✓ 100% of task completions emit PROV-O triples
- ✓ All 10 Phase 1 SPARQL queries execute successfully
- ✓ Causality queries return correct previous task + trigger
- ✓ Audit trail query shows chronological agent actions
- ✓ All Phase 1 unit tests pass (15+ tests)
- ✓ No performance regression (query latency <1s per case)

### Phase 2 Success Criteria

- ✓ All payment tasks map to fibo:PaymentInstruction
- ✓ Financial analytics queries agree with manual totals (±$0)
- ✓ SLA predictions validated against actual timers (>95% accuracy)
- ✓ Resource requirement export enables agent planning
- ✓ All Phase 2 unit tests pass (25+ tests)
- ✓ No failures on order-to-cash workflow test

### Phase 3 Success Criteria

- ✓ 8 inference rules fire correctly on synthetic test cases
- ✓ Autonomous agent recommends correct next actions (>90% accuracy)
- ✓ Multi-service saga orchestration handles failures + compensations
- ✓ Deadlock detection prevents circular task waits
- ✓ All Phase 3 unit tests pass (80+ tests)
- ✓ 24-hour field testing with real enterprise cases

---

## References

- **Main Report**: `.claude/reports/ontology-utilization.md` (13 sections, comprehensive)
- **Executive Summary**: `.claude/reports/ontology-utilization-summary.txt` (quick brief)
- **Ontology Files**: `.specify/yawl-ontology.ttl`, `.specify/extended-patterns.ttl`
- **SHACL Shapes**: `.specify/yawl-shapes.ttl`
- **Invariants**: `.specify/invariants.ttl`

---

**Document Version**: 1.0
**Status**: READY FOR ENGINEERING KICKOFF
**Last Updated**: 2026-02-24T14:32:15Z
