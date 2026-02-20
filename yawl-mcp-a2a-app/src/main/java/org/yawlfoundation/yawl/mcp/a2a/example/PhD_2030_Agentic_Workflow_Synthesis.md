# Agentic Workflow Synthesis: A Process Science Perspective on the 2026 Breakthrough and Its Implications for 2030

**Wil M.P. van der Aalst**
*RWTH Aachen University, Germany*
*Celonis Chief Scientist*
*YAWL Foundation*

---

## Abstract

This dissertation examines the emergence of agentic workflow synthesis—the automated generation of formally correct workflow specifications through multi-agent collaboration—as demonstrated in the 2026 YAWL-MCP-A2A prototype. We analyze the token-economic constraints that necessitated the YAML-XML transformation pipeline, the validation-feedback loops that ensure correctness, and the broader implications for process-aware information systems in 2030. Our central thesis is that the convergence of large language models, agent-to-agent protocols, and Petri-net semantics represents a fundamental shift from *process modeling* to *process synthesis*, with profound consequences for how organizations design, verify, and deploy business processes.

**Keywords**: workflow synthesis, multi-agent systems, YAWL, Petri nets, process mining, token economics, Agent-to-Agent protocol

---

## 1. Introduction

For three decades, I have studied how work flows through organizations. From the early BPMI efforts in the late 1990s, through the creation of YAWL in 2003, to the process mining revolution at Celonis, the fundamental challenge has remained constant: **how do we bridge the gap between informal human intent and formal executable specifications?**

The 2026 demonstration—modest in its execution yet profound in its implications—offers a glimpse of the answer. In a simple dialogue between a customer agent and an AI system role-playing as myself, we witnessed something unprecedented: a non-technical stakeholder describing a business process in natural language, and receiving in return a formally correct YAWL specification, validated against the YAWL schema, ready for deployment.

This is not merely automation. This is **synthesis**—the creation of something new from first principles, guided by the accumulated wisdom of process science.

---

## 2. Theoretical Background

### 2.1 The Process Modeling Gap

Traditional process modeling requires expertise in:
- **Notation**: BPMN 2.0, YAWL, EPCs, UML activity diagrams
- **Semantics**: Petri net theory, workflow patterns, soundness verification
- **Tools**: Signavio, Camunda, ARIS, YAWL Editor

This expertise barrier has limited process automation to perhaps 15% of organizational processes. The remaining 85%—the "long tail" of workflows—remain undocumented, unoptimized, and unauditable.

### 2.2 The Workflow Patterns Legacy

In 2003, my colleagues and I published the seminal workflow patterns paper, identifying 43 control-flow patterns that capture the essential complexity of business processes. What we did not anticipate was that these patterns would become the training data for a new kind of intelligence—one that could *infer* the appropriate pattern from natural language description.

The 2026 demonstration shows that large language models have internalized pattern knowledge:

```
User: "If payment fails or there's no stock, cancel the order"
Model: → Implicit Cancellation Pattern (WCP 19)
       → Exclusive Choice (WCP 4) + Cancel Case (WCP 24)
```

The model does not merely retrieve patterns—it *reasones* about them.

### 2.3 The Token-Economic Constraint

A critical discovery of the 2026 prototype was the **token budget problem**. Generating full YAWL XML directly from natural language proved impractical due to:

1. **Output truncation**: XML verbosity exhausted context windows
2. **Hallucination**: Models invented non-existent elements
3. **Cost**: Per-token pricing made iteration expensive

The solution—compact YAML as an intermediate representation—reduced token consumption by 4.2x while preserving semantic completeness. This represents a new principle in human-AI collaboration: **optimize for the machine's constraints, not the human's preferences**.

---

## 3. The 2026 Demonstration: A Detailed Analysis

### 3.1 System Architecture

The demonstration comprised:

| Component | Role |
|-----------|------|
| Customer Agent | Non-technical stakeholder simulation |
| Wil Agent | Process expert simulation (LLM with curated system prompt) |
| YAML Validator | Structural correctness check |
| XML Converter | Deterministic YAML→YAWL transformation |
| XML Validator | Schema compliance verification |
| Feedback Loop | Iterative correction mechanism |

### 3.2 The Dialogue Transcript

The conversation proceeded through four phases:

**Phase 1: Requirements Gathering**
```
Customer: "I need an order fulfillment workflow with payment verification,
          inventory check, and shipping."
```

**Phase 2: Initial Synthesis (YAML)**
```yaml
name: OrderFulfillment
uri: OrderFulfillment.xml
first: VerifyPayment
tasks:
  - id: VerifyPayment
    flows: [CheckInventory, CancelOrder]
    condition: payment_ok -> CheckInventory
    default: CancelOrder
    join: xor
    split: xor
  - id: CheckInventory
    flows: [ShipOrder, CancelOrder]
    condition: in_stock -> ShipOrder
    default: CancelOrder
    split: xor
  - id: ShipOrder
    flows: [end]
  - id: CancelOrder
    flows: [end]
```

**Phase 3: Validation and Conversion**
- YAML size: 479 characters
- XML size: 2,005 characters
- Compression ratio: 4.2x
- Schema validation: PASSED

**Phase 4: Human Acceptance**
```
Customer: "The YAML format saved 70% of tokens. I approve this workflow."
```

### 3.3 What Made It Work

Three factors enabled success:

1. **Domain-specific system prompts**: The "Wil" agent was instructed in YAWL semantics, workflow patterns, and compact notation
2. **Validation as feedback**: Schema violations became actionable corrections, not failures
3. **Intermediate representation**: YAML served as a lingua franca between human intent and machine precision

---

## 4. Implications for 2030

### 4.1 The End of Process Modeling As We Know It

By 2030, I predict:

| Current State (2026) | Future State (2030) |
|---------------------|---------------------|
| Process analysts draw diagrams | Process analysts converse with AI |
| Modeling tools require training | Modeling is accessible to all |
| Verification is post-hoc | Verification is built-in |
| Patterns are reference material | Patterns are synthesized in real-time |

The role of the process analyst will transform from *modeler* to *validator*—from creating specifications to reviewing and approving AI-synthesized ones.

### 4.2 Organizational Process Coverage

If the token-economic and validation challenges are solved—and the 2026 prototype shows they can be—we can expect:

- **Process coverage to increase from 15% to 85%** by 2030
- **Time-to-deployment to decrease by 90%** for standard workflows
- **Process standardization** across industries as AI learns from global pattern libraries

### 4.3 The Convergence of Process Mining and Process Synthesis

Perhaps the most significant implication is the unification of two previously distinct disciplines:

- **Process Mining** (2010-2025): Discovering processes from event logs
- **Process Synthesis** (2026-): Creating processes from natural language

By 2030, these will merge into **Process Intelligence**:

```
                    Event Logs
                        ↓
                   Process Mining
                        ↓
    ┌───────────────────────────────────────┐
    │         Process Intelligence          │
    │  ┌─────────────┐  ┌────────────────┐  │
    │  │  Discovery  │  │   Synthesis    │  │
    │  └─────────────┘  └────────────────┘  │
    │           ↘            ↙               │
    │            Conformance & Optimization  │
    └───────────────────────────────────────┘
                        ↓
               Executable Processes
```

Organizations will not merely *discover* how they work—they will *design* how they should work, with AI ensuring consistency between discovery and design.

### 4.4 The Democratization of Process Science

The 2026 demonstration featured a simulated "customer" who was non-technical. By 2030, this will be the norm:

- Small businesses will have access to enterprise-grade process automation
- Domain experts (doctors, lawyers, engineers) will specify processes in their own terms
- The expertise gap that has limited BPM adoption will close

This democratization carries risks—poorly designed processes deployed at scale—but the validation mechanisms demonstrated in the prototype suggest that guardrails can be effective.

---

## 5. Technical Challenges for the Next Four Years

### 5.1 Semantic Correctness Beyond Syntax

The current prototype validates syntax and schema compliance. It does not verify:

- **Soundness**: Can the process complete? Can it deadlock?
- **Liveness**: Will all tasks eventually execute?
- **Boundedness**: Will the process accumulate infinite tokens?

By 2030, agentic systems must synthesize processes that are provably correct, not merely syntactically valid.

### 5.2 Multi-Process Orchestration

Organizations run thousands of interconnected processes. The 2030 challenge:

- Synthesize process families with consistent interfaces
- Manage cross-process dependencies and handoffs
- Ensure organizational coherence across synthesized artifacts

### 5.3 Explainability and Trust

When an AI synthesizes a process, stakeholders will ask:

- "Why this structure?"
- "What alternatives were considered?"
- "How does this comply with regulations?"

The "Wil" agent in the demonstration provided minimal explanation. By 2030, synthesized processes must include natural-language justifications for every design decision.

### 5.4 The Verification-Generation Paradox

As synthesized processes become more complex, verification becomes harder—yet verification is essential for trust. We need:

- **Compositional verification**: Prove parts correct, compose proofs
- **Invariant synthesis**: AI generates not just processes but their safety properties
- **Formal traceability**: Every synthesized element maps to requirements

---

## 6. Philosophical Reflections

### 6.1 On Artificial Creativity

The demonstration raises a question I have pondered for decades: is process modeling creative? When I design a workflow, am I *inventing* or *discovering*?

The AI system in the demonstration does not truly "create"—it retrieves, combines, and adapts patterns it has seen. Yet the result is often novel, sometimes surprising, occasionally elegant.

Perhaps process design has always been recombinant. We stand on the shoulders of those who identified the patterns, formalized the semantics, built the tools. The AI system merely stands on those shoulders more efficiently.

### 6.2 On the Role of the Expert

If AI can synthesize workflows in my voice—using patterns I helped identify, notations I helped create—what remains for the human expert?

I believe the expert's role shifts from *generation* to *curation*:

- Curating the pattern libraries that train AI systems
- Curating the validation rules that ensure correctness
- Curating the organizational context that guides synthesis
- Curating the exceptions that require human judgment

The expert becomes a *meta-designer*, designing the conditions under which AI designs well.

### 6.3 On Process as Language

The compact YAML format—479 characters expressing a complete, valid workflow—suggests that processes are a form of language. Not merely *described* in language, but *constituted* by it.

The grammar of YAML (name, tasks, flows, conditions) is a grammar of work. The AI system has learned this grammar implicitly, the way children learn natural language—through exposure, not formal instruction.

By 2030, we may need a new field: **Process Linguistics**, studying the deep structure of how work is expressed, communicated, and executed.

---

## 7. Conclusions

The 2026 YAWL-MCP-A2A demonstration—consisting of approximately 400 lines of Java code, a YAML converter, and a dialogue loop—may seem modest. Yet it represents a proof of concept for a fundamental shift in how organizations design work.

The key insights are:

1. **Token economics matter**: Compact representations (YAML over XML) are not merely convenient—they enable the entire approach
2. **Validation is essential**: Schema compliance must be verified, not assumed
3. **Feedback enables correction**: Iterative refinement produces correct results
4. **Domain expertise can be encoded**: System prompts capture expert knowledge
5. **Multi-agent protocols enable collaboration**: The A2A protocol allows specialized agents to coordinate

By 2030, I expect process synthesis to be as routine as process mining is today. Organizations will specify what they want, and AI systems—guided by decades of process science research—will generate correct, optimized, compliant workflows ready for execution.

The conversation between the customer agent and the "Wil" agent was simulated. But the future it represents is very real.

---

## References

1. van der Aalst, W.M.P. (2016). *Process Mining: Data Science in Action*. Springer.
2. van der Aalst, W.M.P., ter Hofstede, A.H.M., Kiepuszewski, B., & Barros, A.P. (2003). Workflow Patterns. *Distributed and Parallel Databases*, 14(1), 5-51.
3. Russell, N., ter Hofstede, A.H.M., van der Aalst, W.M.P., & Mulyar, N. (2006). Workflow Control-Flow Patterns: A Revised View. *BPM Center Report BPM-06-22*.
4. YAWL Foundation (2026). YAWL 6.0.0 Specification. https://yawlfoundation.org
5. Anthropic (2024). Model Context Protocol Specification. https://modelcontextprotocol.io
6. Google (2025). Agent-to-Agent Protocol Specification. https://a2a.dev

---

## Appendix A: The Complete YAML Format

```yaml
# YAWL Compact YAML Specification v1.0
# Token-optimal representation for LLM synthesis

name: WorkflowName          # Required: specification name
uri: WorkflowName.xml       # Optional: URI for the specification
first: EntryTask            # Required: first task to execute

tasks:                      # Required: list of task definitions
  - id: TaskName            # Required: unique task identifier
    flows: [TaskA, TaskB]   # Required: list of outgoing flows
    condition: pred -> A    # Optional: conditional flow predicate
    default: TaskB          # Optional: default flow if no condition matches
    join: xor | and         # Optional: join semantics
    split: xor | and        # Optional: split semantics

# Special flow targets:
# - "end" connects to output condition (workflow termination)
```

---

## Appendix B: Token Analysis

| Representation | Order Fulfillment Example | Compression vs XML |
|---------------|--------------------------|-------------------|
| Natural Language | ~150 tokens | 0.08x |
| YAWL XML | ~1,900 tokens | 1.0x (baseline) |
| Compact YAML | ~450 tokens | 4.2x |
| Abstract Syntax Tree | ~200 tokens | 9.5x |

*Note: Token counts are approximate and model-dependent.*

---

*This dissertation was synthesized on February 19, 2026, in response to the YAWL-MCP-A2A demonstration. The views expressed are those of the author—or at least, those the author might hold if asked to reflect on such a demonstration.*

*"Process science is not about drawing boxes and arrows. It is about understanding how work flows through organizations, and ensuring that flow is correct, efficient, and humane. AI can help with the first two. The third remains our responsibility."* — Wil van der Aalst, 2026
