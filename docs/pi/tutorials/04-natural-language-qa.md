# Tutorial 4 — Natural Language Q&A over Process Knowledge

In this tutorial you will:
1. Ingest a process mining report into `ProcessKnowledgeBase`
2. Ask a natural language question with `NaturalLanguageQueryEngine`
3. Inspect the response — with and without Z.AI LLM

Time: ~10 minutes.

---

## Prerequisites

You need a `ProcessIntelligenceFacade` from [Tutorial 1](01-first-case-prediction.md)
or an independently constructed `NaturalLanguageQueryEngine`.

---

## Step 1: Ingest knowledge into the knowledge base

`ProcessKnowledgeBase.ingest()` stores process mining facts from a `ProcessMiningReport`.
You can also add raw facts manually using `ingestFact()`.

```java
import org.yawlfoundation.yawl.pi.rag.*;
import org.yawlfoundation.yawl.engine.YSpecificationID;

ProcessKnowledgeBase kb = new ProcessKnowledgeBase();

YSpecificationID specId = new YSpecificationID(
    "loan-application", "1.0", "http://yawl.example.com/loan");

// Ingest raw facts (what you'd normally get from ProcessMiningFacade.analyze())
kb.ingestFact(specId.getIdentifier(),
    "The credit-check task has an average wait time of 8.3 minutes.");
kb.ingestFact(specId.getIdentifier(),
    "37% of cases that had cancelled work items eventually failed.");
kb.ingestFact(specId.getIdentifier(),
    "The loan-approval task completes in under 2 minutes for 90% of cases.");
kb.ingestFact(specId.getIdentifier(),
    "The busiest hour is 10:00-11:00 UTC, with 34% of all case starts.");
kb.ingestFact(specId.getIdentifier(),
    "Resource res-alice handles 42% of credit-check work items.");

System.out.println("Facts ingested: " + kb.factCount(specId.getIdentifier()));
```

---

## Step 2: Build the query engine (without LLM)

```java
NaturalLanguageQueryEngine nlEngine = new NaturalLanguageQueryEngine(kb, null);
// null ZaiService → RAG returns raw retrieved facts, no LLM synthesis
```

---

## Step 3: Ask a question

```java
import org.yawlfoundation.yawl.pi.rag.NlQueryRequest;
import org.yawlfoundation.yawl.pi.rag.NlQueryResponse;

NlQueryRequest request = NlQueryRequest.of(
    "Which task has the highest wait time?",
    specId.getIdentifier()   // scope to this specification
);

NlQueryResponse response = nlEngine.query(request);

System.out.println("Answer:        " + response.answer());
System.out.println("Grounded:      " + response.groundedInKb());
System.out.println("LLM available: " + response.llmAvailable());
System.out.println("Sources:       " + response.sourceFacts());
System.out.println("Response time: " + response.responseTimeMs() + "ms");
```

Without LLM, the `answer` is the top-k retrieved facts concatenated:

```
Answer:        The credit-check task has an average wait time of 8.3 minutes.
               37% of cases that had cancelled work items eventually failed.
Grounded:      true
LLM available: false
Sources:       [credit-check task wait time 8.3 minutes, ...]
Response time: 12ms
```

The answer is correct even without LLM — the relevant fact was retrieved by keyword
similarity.

---

## Step 4: Ask a broader question

```java
NlQueryRequest request2 = NlQueryRequest.of(
    "What are the performance characteristics of this loan process?"
);
// No specificationId — searches across all ingested specs

NlQueryResponse response2 = nlEngine.query(request2);
System.out.println("Answer: " + response2.answer());
```

---

## Step 5: Use via the facade

If you have a `ProcessIntelligenceFacade`, use `ask()`:

```java
import org.yawlfoundation.yawl.pi.PIException;

try {
    NlQueryResponse r = facade.ask(NlQueryRequest.of(
        "What is the main bottleneck in the loan process?",
        specId.getIdentifier()
    ));
    System.out.println(r.answer());
} catch (PIException e) {
    System.err.println("RAG query failed: " + e.getMessage());
}
```

---

## Step 6: With Z.AI LLM (optional)

If your deployment includes Z.AI:

```java
import org.yawlfoundation.yawl.integration.zai.ZaiService;

ZaiService zai = new ZaiService(
    "https://api.z.ai/v1",
    System.getenv("ZAI_API_KEY")
);

NaturalLanguageQueryEngine nlEngineWithLlm =
    new NaturalLanguageQueryEngine(kb, zai);

NlQueryResponse richResponse = nlEngineWithLlm.query(
    NlQueryRequest.of(
        "Given the high wait time at credit-check, what should we do?",
        specId.getIdentifier()
    )
);

System.out.println("LLM available: " + richResponse.llmAvailable());  // true
System.out.println("Answer: " + richResponse.answer());
// Answer: "Based on the process data, the credit-check task has an average wait
//  time of 8.3 minutes — significantly higher than the 2-minute loan-approval task.
//  Consider increasing credit-check resource allocation during the 10:00-11:00 UTC
//  peak hour, when 34% of cases start. Resource res-alice currently handles 42% of
//  credit-check work, suggesting load imbalance."
```

---

## NlQueryRequest options

```java
// Minimum: just the question
NlQueryRequest.of("What is the bottleneck?")

// Scoped to a specification
NlQueryRequest.of("What is the bottleneck?", "loan-application/1.0")

// Full control
new NlQueryRequest(
    "What is the bottleneck?",         // question
    "loan-application/1.0",            // specificationId (nullable)
    5,                                 // topK — number of facts to retrieve
    UUID.randomUUID().toString()       // requestId for tracing
)
```

---

## Understanding the retrieval

`ProcessKnowledgeBase.retrieve(question, topK)` uses keyword matching:
1. Tokenizes the question into keywords
2. Scores each stored fact by keyword overlap
3. Returns the top-k facts by score

This is intentionally simple — no embedding models or vector databases required.
When Z.AI is available, the LLM turns the top-k facts into a coherent answer.

---

## What you built

- A `ProcessKnowledgeBase` with 5 process mining facts
- Two queries: one without LLM (raw fact retrieval), one with LLM (synthesized answer)
- Understanding of how graceful degradation works when LLM is unavailable

## Next steps

- [How to ingest an event log](../how-to/ingest-event-log.md) — Convert raw data to OCEL2 for process mining
- [The six PI connections](../explanation/6-pi-connections.md) — Why RAG is Connection 4
- [Facade API reference](../reference/facade-api.md) — Complete `ask()` signature
