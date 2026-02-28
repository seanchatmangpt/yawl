# Intelligence Layer (ι): The Observational Substrate of Artifact Quality

**Quadrant**: Explanation | **Concept**: Typed deltas, audit trails, and intelligent context injection
**Date**: 2026-02-28 | **Version**: 1.0
**Status**: Production Ready | **Audience**: Senior engineers, YAWL maintainers, framework designers

---

## Overview: Why Intelligence Matters

The Intelligence Layer (ι) is the observational backbone of YAWL's agent quality model. It answers a fundamental question: **How does an agent know what has changed, and why?**

In traditional software development, change is opaque. A git diff shows *what* changed (lines of code), but not *why* (intent, design decision, API contract). This opacity creates cascading problems:

1. **Agents make redundant decisions** — without understanding prior intent, they re-investigate solved problems
2. **Integration breaks silently** — teammates don't share assumptions about API contracts or behavior changes
3. **Audit trails are useless** — uniform line-diffs hide semantic meaning, making root-cause analysis impossible
4. **Context thrashing** — fetching external specs repeatedly wastes compute and network resources

The Intelligence Layer solves these by:

- **Capturing semantic deltas** (not line-diffs) — delta = Vec<Delta> where each Delta is a typed semantic unit (declaration, rule, criterion, dependency, behavior)
- **Creating immutable audit trails** — blake3-hashed JSON receipts enable cryptographic verification of what changed and when
- **Implementing watermark-based fetching** — content hash + TTL prevents redundant re-fetching of unchanged specs
- **Injecting context at decision points** — UserPromptSubmit injection feeds agents only relevant changes, reducing context noise

This document explains *why* each mechanism exists, *how* they work together, and *when* to use them.

---

## Part 1: Core Concepts

### 1.1 Typed Deltas: Beyond Line-Diffs

In traditional VCS, a change is a unified diff:

```diff
- public void processOrder(Order order) {
-   validate(order);
-   return null;
+ public void processOrder(Order order) throws OrderException {
+   validate(order);
+   return service.execute(order);
}
```

This tells you *what changed* but not *why*. The semantic questions are invisible:

- Is the new exception contract a breaking change?
- Is the new dependency on `service` a new architectural layer?
- Is the behavior change intentional?

A **typed delta** captures semantic intent:

```json
{
  "delta_id": "abc123def456",
  "timestamp": "2026-02-28T14:32:15Z",
  "file": "src/main/java/org/yawl/OrderProcessor.java",
  "changes": [
    {
      "kind": "DECLARATION",
      "type": "METHOD_SIGNATURE",
      "target": "processOrder(Order order)",
      "old": "void processOrder(Order order)",
      "new": "void processOrder(Order order) throws OrderException",
      "semantic_change": "throws_contract",
      "breaking": true,
      "rationale": "Exceptions now propagate to caller instead of silently failing"
    },
    {
      "kind": "BEHAVIOR",
      "type": "RETURN_VALUE",
      "target": "processOrder(Order order)",
      "old": "null",
      "new": "service.execute(order)",
      "semantic_change": "implementation_change",
      "breaking": false,
      "rationale": "Execute actual business logic instead of stub"
    },
    {
      "kind": "DEPENDENCY",
      "type": "FIELD_INJECTION",
      "target": "processOrder(Order order)",
      "new": "service",
      "semantic_change": "new_dependency",
      "rationale": "Introduced external service dependency for order execution"
    }
  ],
  "summary": "Implement OrderProcessor.processOrder with exception contract"
}
```

Typed deltas enable:

- **Precise impact analysis** — identify breaking changes before merge
- **Context routing** — feed only relevant deltas to concerned agents
- **Semantic validation** — verify that implementation matches declared intent
- **Audit fidelity** — understand not just what changed, but why

### 1.2 Delta Kinds and Semantic Units

The Intelligence Layer recognizes six delta kinds:

| Kind | Example | Use Case |
|------|---------|----------|
| **DECLARATION** | Method signature, type definition, constant | API contract changes |
| **RULE** | Workflow rule, guard condition, validation logic | Business rule updates |
| **CRITERION** | Acceptance criterion, test assertion, SLA bound | Quality metric changes |
| **DEPENDENCY** | Import, service injection, external API call | Architecture changes |
| **BEHAVIOR** | Return value, side effect, control flow | Implementation changes |
| **QUAD** | Integration point, MCP endpoint, A2A contract | Cross-system contracts |

Each delta unit includes:

- `kind` — semantic category
- `type` — specific subtype (e.g., METHOD_SIGNATURE, FIELD_INJECTION)
- `target` — fully qualified reference (e.g., `org.yawl.OrderProcessor#processOrder`)
- `old` / `new` — before/after content
- `semantic_change` — high-level classification (breaking, enhancement, refactoring)
- `breaking` — true/false for API consumers
- `rationale` — human-readable explanation of why

### 1.3 Receipt Chains: Immutable Audit Trails

Every delta sequence creates a **receipt** — a cryptographically signed record that proves:

1. **What changed** — the complete delta set
2. **When it changed** — timestamp
3. **By whom** — agent identity
4. **That it hasn't been tampered with** — blake3 hash

```json
{
  "receipt_id": "receipt_2026-02-28T14:32:15Z_abc123",
  "session": "claude/session_01SfdxrP7PZC8eiQQws7Rbz2",
  "timestamp": "2026-02-28T14:32:15Z",
  "agent": "yawl-engineer",
  "deltas": ["abc123def456", "def456ghi789"],
  "delta_hash": "blake3:a1b2c3d4e5f6...",
  "previous_receipt": "receipt_2026-02-28T14:30:00Z_zzz999",
  "chain_hash": "blake3:x1y2z3...",
  "status": "COMMITTED",
  "verified": true
}
```

Receipts are **append-only**. Once written to `receipts/intelligence.jsonl`, they are never deleted or modified. This creates a chain:

```
Receipt_0 (session start)
  ↓
Receipt_1 (first set of changes)
  ├─ delta_hash: h(Δ₁)
  ├─ previous_receipt: Receipt_0
  └─ chain_hash: h(Receipt_0 + Δ₁)
  ↓
Receipt_2 (second set of changes)
  ├─ delta_hash: h(Δ₂)
  ├─ previous_receipt: Receipt_1
  └─ chain_hash: h(Receipt_1 + Δ₂)
  ↓
...
```

This chain is **cryptographically verifiable**:

```bash
# Verify the receipt chain has not been tampered with
verification_script verify-receipt-chain receipts/intelligence.jsonl
# Output: VALID (all hashes check out)
```

**Why this matters**: When an agent is uncertain about prior work ("Did we already implement X?"), the receipt chain provides authoritative proof. No speculation, no re-investigation — just cryptographic certainty.

### 1.4 Watermark Protocol: TTL-Based Refetching

External specs change constantly: API contracts, schema versions, dependency updates, config. Agents need to fetch these, but fetching every time wastes compute and network.

The **watermark protocol** solves this with three mechanisms:

#### Watermark Structure

```json
{
  "fetch_url": "https://spec-server/yawl-mcp-spec.json",
  "fetched_at": "2026-02-28T10:00:00Z",
  "content_hash": "blake3:abc123def456...",
  "ttl_seconds": 3600,
  "expires_at": "2026-02-28T11:00:00Z"
}
```

#### Fetch Decision Logic

When the agent needs a spec:

```
FETCH_SPEC(url) {
  watermark = LOAD_WATERMARK(url)

  if watermark does not exist:
    FETCH and STORE
    CREATE watermark with ttl=3600
    return content

  if NOW > watermark.expires_at:
    FETCH latest
    NEW_HASH = HASH(latest)
    if NEW_HASH == watermark.content_hash:
      UPDATE watermark.expires_at = NOW + ttl
      return cached content
    else:
      STORE latest
      UPDATE watermark with new hash and expiry
      return latest

  if NOW < watermark.expires_at:
    return cached content (no network)
}
```

#### Example: Preventing MCP Spec Thrashing

Scout is fetching the MCP spec to understand available tools. Without watermarking:

```
Session Start
  ↓
Scout: Fetch MCP spec → 50KB, 200ms
  ↓
Engineer: Ask "do we have this tool?" → Scout fetches again → 50KB, 200ms
  ↓
Validator: Check tool compatibility → Scout fetches again → 50KB, 200ms
  ↓
Total: 150KB transferred, 600ms latency
```

With watermarking:

```
Session Start
  ↓
Scout: Fetch MCP spec → 50KB, 200ms
  Create watermark: hash=abc123, ttl=1h, expires=11:00
  ↓
Engineer: Ask "do we have this tool?" → Cache HIT (expires_at not passed)
  Return cached spec (0KB, 0ms)
  ↓
Validator: Check tool compatibility → Cache HIT
  Return cached spec (0KB, 0ms)
  ↓
Total: 50KB transferred, 200ms latency (3× improvement)
```

---

## Part 2: Architecture

### 2.1 The Fetch → Parse → Receipt Flow

The Intelligence Layer operates in a three-phase pipeline:

```
┌─────────────────────────────────────────────────────────────┐
│                     Phase 1: FETCH                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  yawl-scout (async, non-blocking binary)                    │
│  ├─ Load watermarks from .claude/context/watermarks.json    │
│  ├─ For each spec URL in fetch-list:                        │
│  │   ├─ Check if watermark exists and valid                 │
│  │   ├─ If valid: use cached content                        │
│  │   ├─ If stale: fetch latest, verify hash                 │
│  │   └─ Update watermark file                               │
│  └─ Write specs to .claude/context/live-intelligence.md     │
│                                                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                     Phase 2: PARSE                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Intelligence Parser (stateless function)                   │
│  ├─ Load live-intelligence.md                               │
│  ├─ Parse each spec (JSON, YAML, Markdown, etc.)           │
│  ├─ Extract semantic units:                                 │
│  │   ├─ Type definitions (classes, interfaces, records)    │
│  │   ├─ API contracts (method signatures, returns)          │
│  │   ├─ Rules (validation, guard conditions)                │
│  │   ├─ Dependencies (imports, service contracts)           │
│  │   └─ Behavior (side effects, state changes)              │
│  └─ Emit typed fact set to .claude/context/facts/           │
│                                                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Phase 3: RECEIPT                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Receipt Chain Manager                                      │
│  ├─ Hash parsed facts: h_facts = blake3(canonical_json)    │
│  ├─ Verify chain integrity: h_chain = blake3(prev + δ)     │
│  ├─ Create receipt object:                                  │
│  │   {receipt_id, timestamp, agent, facts_hash,             │
│  │    previous_receipt, chain_hash, status}                 │
│  └─ Append receipt to receipts/intelligence.jsonl           │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Data Structures at Each Phase

#### Phase 1: Watermarks

```json
{
  ".claude/context/watermarks.json": {
    "https://spec.yawl-engine.org/api-spec.json": {
      "fetched_at": "2026-02-28T10:00:00Z",
      "content_hash": "blake3:xyz789abc...",
      "ttl_seconds": 3600,
      "expires_at": "2026-02-28T11:00:00Z"
    },
    "https://docs.github.com/mcp-spec": {
      "fetched_at": "2026-02-28T09:30:00Z",
      "content_hash": "blake3:abc123xyz...",
      "ttl_seconds": 7200,
      "expires_at": "2026-02-28T11:30:00Z"
    }
  }
}
```

#### Phase 2: Parsed Facts

```
.claude/context/facts/
├── api-contracts.json
│   └─ [{signature, return_type, throws, javadoc, deprecation}]
├── type-definitions.json
│   └─ [{name, kind, fields, implements, extends}]
├── rules.json
│   └─ [{type, condition, action, priority}]
├── dependencies.json
│   └─ [{source, target, kind, version_constraint}]
└── behaviors.json
    └─ [{method, side_effect, precondition, postcondition}]
```

#### Phase 3: Receipts

```
receipts/intelligence.jsonl  (append-only)

{"receipt_id": "...", "timestamp": "...", "status": "COMMITTED", ...}
{"receipt_id": "...", "timestamp": "...", "status": "COMMITTED", ...}
{"receipt_id": "...", "timestamp": "...", "status": "COMMITTED", ...}
```

---

## Part 3: Use Cases and Injection Points

### 3.1 Scout Patterns: When and Why

**Scout** is the async fetcher binary that runs at SessionStart. It pre-fetches relevant specs so agents never block on network I/O.

#### Use Case 1: MCP Endpoint Discovery

**Scenario**: An agent is building a new MCP endpoint for YAWL case monitoring.

**Without Scout**:
```
Engineer: "I need to understand the MCP protocol"
Scout runs in background, network latency (200ms)
Engineer waits...
Engineer waits...
(200ms later) Scout finishes, facts available
Engineer can now read MCP spec
```

**With Scout**:
```
SessionStart hook fires
Scout runs async in background: fetch MCP, save watermark
SessionStart completes (0ms for engineer)
Engineer asks "what's in MCP spec?"
Intelligence layer returns cached facts (0ms)
Engineer reads spec, starts implementation
```

**Fetch list for MCP use case**:
```toml
# .claude/context/scout-config.toml
[[fetch_list]]
url = "https://spec.modelcontextprotocol.io/spec/latest"
cache_ttl = 3600  # 1 hour
destination = "live-intelligence/mcp-protocol.md"

[[fetch_list]]
url = "https://github.com/modelcontextprotocol/spec/blob/main/README.md"
cache_ttl = 86400  # 1 day
destination = "live-intelligence/mcp-best-practices.md"
```

#### Use Case 2: API Contract Validation

**Scenario**: A teammate modified an API signature. Other agents need to know the new contract.

**Flow**:
1. Teammate commits API change
2. PostToolUse hook fires
3. yawl-jira binary extracts semantic deltas from diff
4. Receipt created and added to intelligence.jsonl
5. UserPromptSubmit injection feeds the delta to next query
6. Next agent reads "API changed: now throws OrderException instead of returning null"
7. Agent adapts their implementation accordingly

#### Use Case 3: Spec Version Updates

**Scenario**: A new version of the YAWL schema was released.

**With watermarking**:
1. First fetch: download new schema (500KB, 500ms)
2. Create watermark with hash, ttl=24h
3. For next 24 hours: serve from cache (0ms)
4. After 24 hours: fetch again, compare hash
5. If hash unchanged: update expiry, continue serving cached version
6. If hash changed: download new version, update watermark

**Impact**: Save 500KB × 100 agents per day = 50MB traffic saved

### 3.2 Injection Points: Four Opportunities

The Intelligence Layer feeds information to agents at **four injection points**:

#### Injection 1: SessionStart

**When**: Session initializes
**What**: Baseline context (ticket description, issue context, prior work summary)
**Format**: Markdown narrative
**Example**:

```markdown
## Session Context (SessionStart Injection)

**Ticket**: YAWL-1234: Implement deadlock detection in YNetRunner

**Prior Work**:
- Engineer A investigated state machine implementation (02-27)
  - Found: State transitions in YNetRunner.execute()
  - Issue: Race condition between task completion and case cancellation

- Engineer B analyzed guard condition logic (02-27)
  - Found: Guard condition not respecting state transitions
  - Proposal: Add atomic state check in Guard.evaluate()

**Acceptance Criteria**:
1. YNetRunner handles concurrent cancellation + completion
2. Guard conditions are evaluated atomically
3. No task orphaning on race condition

**Your Task**: Implement deadlock fix in YNetRunner, leveraging prior findings.
```

Scout runs in parallel, fetching MCP/API specs without blocking.

#### Injection 2: UserPromptSubmit

**When**: Agent submits a question or requests code generation
**What**: Relevant typed deltas from prior work in same session
**Format**: JSON delta array
**Example**:

```json
{
  "user_query": "How should the new exception contract affect OrderProcessor callers?",
  "relevant_deltas": [
    {
      "kind": "DECLARATION",
      "type": "METHOD_SIGNATURE",
      "target": "processOrder(Order)",
      "old": "void processOrder(Order)",
      "new": "void processOrder(Order) throws OrderException",
      "breaking": true
    },
    {
      "kind": "DEPENDENCY",
      "type": "FIELD_INJECTION",
      "target": "processOrder(Order)",
      "new": "service",
      "rationale": "Introduced external service dependency"
    }
  ]
}
```

Agent reads: "OK, new exception contract means callers must be prepared to catch OrderException. The implementation now uses an external service."

#### Injection 3: PreToolUse

**When**: Before an agent runs a tool (e.g., before calling dx.sh, read file)
**What**: Context about the files/modules about to be affected
**Format**: Fact summary
**Example**:

```markdown
## Module Context (PreToolUse Injection)

**Modules affected by next compile**:
- yawl-engine (contains YNetRunner)
- yawl-elements (contains Guard)

**Recent changes in these modules**:
- YNetRunner.execute() signature unchanged
- Guard.evaluate() now throws GuardEvaluationException

**Imports to be aware of**:
- org.yawl.engine.YNetRunner (stateful, not thread-safe without lock)
- org.yawl.elements.Guard (now exception-throwing)

**Build warnings to expect**:
- Unused import: org.yawl.engine.legacy.OldGuardEvaluator (deprecated)
```

#### Injection 4: PostToolUse

**When**: After an agent completes a tool operation
**What**: Summarize what changed, record for receipt chain
**Format**: Correction record
**Example**:

```json
{
  "tool": "Edit (OrderProcessor.java)",
  "status": "SUCCESS",
  "changes_made": [
    {
      "kind": "DECLARATION",
      "type": "METHOD_THROWS",
      "target": "processOrder(Order)",
      "new": "throws OrderException"
    },
    {
      "kind": "BEHAVIOR",
      "type": "RETURN_VALUE",
      "old": "null",
      "new": "service.execute(order)"
    }
  ],
  "receipt_id": "rec_20260228_143215_abc123",
  "verified": true
}
```

This injection point ensures every tool use is recorded for the receipt chain.

---

## Part 4: Watermark Protocol (Deep Dive)

### 4.1 Why Watermarking Is Critical

Without watermarking, agents thrash the network:

```
Scout initializes
  ↓
Fetch MCP spec (200ms, 50KB)
Fetch YAWL API (150ms, 100KB)
Fetch DMN spec (180ms, 75KB)
Total: 530ms, 225KB
  ↓
Engineer A asks: "What parameters does Task accept?"
Scout fetches API again (150ms, 100KB) — spec unchanged!
  ↓
Engineer B asks: "How do I define rules?"
Scout fetches DMN again (180ms, 75KB) — spec unchanged!
  ↓
Validator asks: "Is this MCP call valid?"
Scout fetches MCP again (200ms, 50KB) — spec unchanged!
  ↓
Total session cost: 1060ms, 550KB for unchanged specs
```

With watermarking:

```
Scout initializes
  ↓
Fetch MCP spec (200ms, 50KB) → watermark: hash=abc, ttl=1h
Fetch YAWL API (150ms, 100KB) → watermark: hash=def, ttl=1h
Fetch DMN spec (180ms, 75KB) → watermark: hash=ghi, ttl=1h
Total: 530ms, 225KB
  ↓
Engineer A asks: "What parameters does Task accept?"
Check watermark: NOT expired, hash matches
Return cached API (0ms, 0KB)
  ↓
Engineer B asks: "How do I define rules?"
Check watermark: NOT expired, hash matches
Return cached DMN (0ms, 0KB)
  ↓
Validator asks: "Is this MCP call valid?"
Check watermark: NOT expired, hash matches
Return cached MCP (0ms, 0KB)
  ↓
Total session cost: 530ms, 225KB (network cost paid once)
```

### 4.2 Content Hash Verification

When TTL expires, Scout re-fetches but first checks if content changed:

```python
def fetch_with_watermark(url):
    watermark = load_watermark(url)

    # TTL still valid: return cached
    if time.now() < watermark.expires_at:
        return load_cached_content(url)

    # TTL expired: fetch and verify
    latest_content = http.get(url)
    latest_hash = blake3(latest_content)

    if latest_hash == watermark.content_hash:
        # Content unchanged: just extend TTL
        watermark.expires_at = time.now() + watermark.ttl_seconds
        save_watermark(watermark)
        return load_cached_content(url)
    else:
        # Content changed: use new version
        save_cached_content(url, latest_content)
        watermark.content_hash = latest_hash
        watermark.expires_at = time.now() + watermark.ttl_seconds
        save_watermark(watermark)
        return latest_content
```

This ensures:
- **Network efficiency** — don't re-download unchanged specs
- **Data freshness** — detect updates immediately
- **Verifiability** — content hash proves authenticity

### 4.3 Watermark Lifecycle

```
Watermark States:
  ↓
[INITIAL] (watermark doesn't exist)
  ├─ Action: Fetch content, create watermark
  └─ State transition: → [VALID]
  ↓
[VALID] (watermark exists, TTL not expired)
  ├─ Action: Return cached content
  ├─ Network cost: 0
  └─ State transition: → [VALID] (if TTL renewed) or → [STALE]
  ↓
[STALE] (watermark exists, TTL expired)
  ├─ Action: Fetch content, verify hash
  ├─ If hash unchanged: extend TTL, return cached
  ├─ If hash changed: use new content, update hash and TTL
  └─ State transition: → [VALID]
  ↓
[ORPHANED] (watermark exists but resource deleted)
  ├─ Action: Log warning, remove watermark
  └─ State transition: → [INITIAL]
```

---

## Part 5: Receipt Chain and Audit Trails

### 5.1 Immutability and Cryptographic Verification

A receipt chain is a **linked list of hashes**, where each receipt depends on the previous one:

```
Receipt[0]
  hash = blake3("session_start")
  ↓
Receipt[1] (deltas: API change)
  previous = Receipt[0].hash
  delta_hash = blake3(canonical_json(deltas))
  chain_hash = blake3(Receipt[0].hash + delta_hash)
  ↓
Receipt[2] (deltas: implementation change)
  previous = Receipt[1].chain_hash
  delta_hash = blake3(canonical_json(deltas))
  chain_hash = blake3(Receipt[1].chain_hash + delta_hash)
  ↓
Receipt[3] (deltas: test addition)
  previous = Receipt[2].chain_hash
  delta_hash = blake3(canonical_json(deltas))
  chain_hash = blake3(Receipt[2].chain_hash + delta_hash)
```

**Verification process**:

```bash
# Verify entire chain integrity
verify_receipt_chain() {
    prev_hash = INITIAL_HASH
    for receipt in receipts:
        assert receipt.previous == prev_hash
        computed_hash = blake3(prev_hash + receipt.delta_hash)
        assert computed_hash == receipt.chain_hash
        prev_hash = receipt.chain_hash
    echo "VALID: All receipts form an unbroken chain"
}
```

If any receipt is modified:

```
Receipt[1] (modified)
  delta_hash = blake3(canonical_json(modified_deltas))  # Different!
  chain_hash = blake3(Receipt[0].hash + modified_delta_hash)
  ↓
Receipt[2] (now invalid)
  expected: blake3(Receipt[1].chain_hash + Receipt[2].delta_hash)
  actual: blake3(old_Receipt[1].chain_hash + Receipt[2].delta_hash)
  MISMATCH! Chain is broken.
```

**Result**: Tampering is immediately detected.

### 5.2 Example: Complete Receipt Chain

```json
[
  {
    "receipt_id": "receipt_20260228_100000_session_init",
    "timestamp": "2026-02-28T10:00:00Z",
    "agent": "yawl-orchestrator",
    "event_type": "SESSION_START",
    "session": "claude/intelligence-layer-01SfdxrP7PZC8eiQQws7Rbz2",
    "previous_receipt": null,
    "chain_hash": "blake3:a1b2c3d4e5f6g7h8",
    "verified": true,
    "status": "COMMITTED"
  },
  {
    "receipt_id": "receipt_20260228_143200_delta_api",
    "timestamp": "2026-02-28T14:32:00Z",
    "agent": "yawl-engineer",
    "event_type": "DELTAS_RECORDED",
    "session": "claude/intelligence-layer-01SfdxrP7PZC8eiQQws7Rbz2",
    "deltas": [
      {
        "kind": "DECLARATION",
        "type": "METHOD_SIGNATURE",
        "target": "OrderProcessor#processOrder",
        "breaking": true
      }
    ],
    "delta_hash": "blake3:b2c3d4e5f6g7h8i9",
    "previous_receipt": "blake3:a1b2c3d4e5f6g7h8",
    "chain_hash": "blake3:b2c3d4e5f6g7h8i9_chained",
    "verified": true,
    "status": "COMMITTED"
  },
  {
    "receipt_id": "receipt_20260228_143215_delta_implementation",
    "timestamp": "2026-02-28T14:32:15Z",
    "agent": "yawl-engineer",
    "event_type": "DELTAS_RECORDED",
    "session": "claude/intelligence-layer-01SfdxrP7PZC8eiQQws7Rbz2",
    "deltas": [
      {
        "kind": "BEHAVIOR",
        "type": "RETURN_VALUE",
        "target": "OrderProcessor#processOrder",
        "old": "null",
        "new": "service.execute(order)"
      },
      {
        "kind": "DEPENDENCY",
        "type": "FIELD_INJECTION",
        "target": "OrderProcessor#processOrder",
        "new": "service"
      }
    ],
    "delta_hash": "blake3:c3d4e5f6g7h8i9j0",
    "previous_receipt": "blake3:b2c3d4e5f6g7h8i9_chained",
    "chain_hash": "blake3:c3d4e5f6g7h8i9j0_chained",
    "verified": true,
    "status": "COMMITTED"
  }
]
```

---

## Part 6: Integration with Agent Workflows

### 6.1 How Intelligence Feeds UserPromptSubmit

The Intelligence Layer integrates into agent decision-making at critical moments:

```
Engineer submits prompt:
"I need to implement OrderProcessor. What's the API contract?"
  ↓
UserPromptSubmit injection gate activates
  ├─ Parse query: extract keywords ["OrderProcessor", "API contract"]
  ├─ Search live-intelligence facts for matches
  ├─ Retrieve typed deltas relevant to OrderProcessor
  └─ Inject deltas into prompt context
  ↓
Prompt delivered to agent with enriched context:
{
  "user_query": "I need to implement OrderProcessor...",
  "context": {
    "relevant_types": [
      {
        "name": "Order",
        "fields": [...],
        "methods": [...]
      },
      {
        "name": "OrderService",
        "interface": true,
        "methods": [...]
      }
    ],
    "recent_changes": [
      {
        "kind": "DECLARATION",
        "target": "OrderProcessor#processOrder",
        "change": "throws_contract_added"
      }
    ]
  }
}
  ↓
Agent reads context and makes informed decision:
"processOrder now throws OrderException. I need to catch or propagate it."
```

### 6.2 Preventing Context Thrashing

Without Intelligence Layer, agents thrash:

```
Engineer A: "What's the MCP protocol?"
Scout: Fetch MCP spec (200ms)
Intelligence: Grep entire codebase for "mcp" (1000ms)
Total: 1200ms
  ↓
Engineer B: "How do I define an MCP endpoint?"
Scout: Fetch MCP spec again (200ms, spec unchanged!)
Intelligence: Grep entire codebase again (1000ms, results unchanged!)
Total: 1200ms
  ↓
Validator: "Is this MCP call correct?"
Scout: Fetch MCP spec again (200ms)
Intelligence: Grep again (1000ms)
Total: 1200ms
  ↓
Session cost: 3600ms for unchanged specs
```

With Intelligence Layer:

```
Engineer A: "What's the MCP protocol?"
Scout: Fetch and watermark (200ms) → cache for 1h
Intelligence: Parse once → cache parsed facts
Total: 200ms
  ↓
Engineer B: "How do I define an MCP endpoint?"
Scout: Cache hit (0ms)
Intelligence: Return cached facts (0ms)
Total: 0ms
  ↓
Validator: "Is this MCP call correct?"
Scout: Cache hit (0ms)
Intelligence: Return cached facts (0ms)
Total: 0ms
  ↓
Session cost: 200ms (network paid once)
```

**Improvement**: 18× speedup

---

## Part 7: Decision Tree: When to Use Intelligence Layer

Not every task needs the Intelligence Layer. Use this tree to decide:

```
Does your task involve understanding changes to APIs, specs, or contracts?
├─ NO → You don't need Intelligence Layer
│   └─ Use simple local facts (imports, signatures)
│
├─ YES: Are you working in a multi-agent session?
│   ├─ NO → Lightweight approach: read files directly
│   │   └─ No watermarking needed
│   │
│   └─ YES: Are teammates making changes to shared APIs?
│       ├─ NO → Use basic UserPromptSubmit injection
│       │   └─ You don't need full receipt chain
│       │
│       └─ YES → Use full Intelligence Layer
│           ├─ Enable scout (async fetching)
│           ├─ Enable typed deltas (UserPromptSubmit)
│           ├─ Enable receipt chain (audit trail)
│           └─ Enable watermarking (prevent thrashing)
```

### Decision Examples

**Example 1: Solo Task, No API Changes**
Task: "Add logging to YNetRunner"
- Affects: Single module (yawl-engine)
- External specs needed: None
- Teammates affected: None
- **Decision**: Don't use Intelligence Layer
- **Why**: No external specs to fetch, no teammates to coordinate with

**Example 2: Team Task, Shared API**
Task: "Implement order processing with exception handling" (with teammate modifying OrderService)
- Affects: OrderProcessor and OrderService (different modules)
- External specs needed: Order schema, Service API
- Teammates affected: Teammate modifying OrderService
- **Decision**: Use full Intelligence Layer
- **Why**: Need to track teammate's changes, understand updated API contract

**Example 3: Integration Task, External Service**
Task: "Add MCP endpoint for case monitoring"
- Affects: yawl-integration module
- External specs needed: MCP protocol spec (updates quarterly)
- Teammates affected: Possibly (depends on team structure)
- **Decision**: Use Intelligence Layer with watermarking
- **Why**: Need to fetch external spec (prevent thrashing), need audit trail for A2A contract

**Example 4: Validation Task, Multiple Specs**
Task: "Validate schema compliance across all modules"
- Affects: All modules
- External specs needed: YAWL schema, XSD definitions, validation rules
- Teammates affected: All
- **Decision**: Use full Intelligence Layer
- **Why**: Need to understand changes in every module, need immutable audit trail for compliance

---

## Part 8: Common Patterns and Troubleshooting

### 8.1 Pattern: Updating a Shared API

**Scenario**: You modify an API that three teammates depend on.

**Flow**:

1. **Make the change**:
   ```java
   // Old
   public void processOrder(Order order) { }

   // New
   public void processOrder(Order order) throws OrderException {
       service.execute(order);
   }
   ```

2. **Change is recorded in receipt chain**:
   ```json
   {
     "kind": "DECLARATION",
     "type": "METHOD_SIGNATURE",
     "target": "OrderProcessor#processOrder",
     "old": "void processOrder(Order)",
     "new": "void processOrder(Order) throws OrderException",
     "breaking": true
   }
   ```

3. **Teammate queries "What's the OrderProcessor contract?"**
   ```
   UserPromptSubmit injection fires
   ↓
   Finds typed delta: "throws_contract_added"
   ↓
   Teammate reads: "OK, method now throws OrderException"
   ↓
   Teammate wraps call: try { processor.processOrder(order); } catch (OrderException e) { ... }
   ```

4. **No guessing, no thrashing, no "wait, did they change the API?"**

### 8.2 Pattern: Coordinating Across Specs

**Scenario**: You're implementing a feature that depends on three external specs (MCP, YAWL schema, OData). All three specs change frequently.

**Without watermarking**:
- Scout fetches 3 specs × 5 times per session = 15 fetches
- Network cost: 15 × 300ms = 4500ms
- Total bytes: 15 × 225KB = 3375KB

**With watermarking**:
- Scout fetches 3 specs once = 3 fetches
- For 4 subsequent questions: cache hits = 0ms
- Total session cost: 3 × 300ms = 900ms (4.5× faster)

**To set this up**:

```toml
# .claude/context/scout-config.toml
[[fetch_list]]
url = "https://spec.modelcontextprotocol.io/spec"
cache_ttl = 3600
priority = "high"

[[fetch_list]]
url = "https://yawl-engine.org/schema/latest.xsd"
cache_ttl = 86400
priority = "high"

[[fetch_list]]
url = "https://oasis-open.org/odata/v4"
cache_ttl = 604800
priority = "medium"
```

### 8.3 Troubleshooting: Stale Cache

**Problem**: You suspect the cached MCP spec is outdated.

**Diagnosis**:
```bash
# Check watermark
jq '.["https://spec.modelcontextprotocol.io/spec"]' \
  .claude/context/watermarks.json

# Output:
# {
#   "fetched_at": "2026-02-27T10:00:00Z",
#   "content_hash": "blake3:abc123...",
#   "expires_at": "2026-02-27T11:00:00Z"
# }

# Current time: 2026-02-28T14:32:00Z
# Conclusion: Cache EXPIRED (expires_at is in the past)
```

**Recovery**: Force re-fetch:
```bash
# Delete watermark entry to force fresh fetch
jq 'del(.["https://spec.modelcontextprotocol.io/spec"])' \
  .claude/context/watermarks.json > watermarks.tmp && \
  mv watermarks.tmp .claude/context/watermarks.json

# Scout will re-fetch on next request
```

### 8.4 Troubleshooting: Missing Typed Deltas

**Problem**: UserPromptSubmit injection didn't include relevant changes.

**Diagnosis**:
```bash
# Check receipt chain for deltas
jq '.deltas[] | select(.target == "OrderProcessor#processOrder")' \
  receipts/intelligence.jsonl

# If empty: no deltas recorded for OrderProcessor
```

**Recovery**: Manually record deltas:
```bash
# Append delta to receipt chain
echo '{
  "receipt_id": "receipt_manual_20260228_143230",
  "timestamp": "2026-02-28T14:32:30Z",
  "agent": "manual",
  "event_type": "DELTAS_RECORDED",
  "deltas": [
    {
      "kind": "DECLARATION",
      "type": "METHOD_SIGNATURE",
      "target": "OrderProcessor#processOrder",
      "breaking": true
    }
  ]
}' >> receipts/intelligence.jsonl
```

### 8.5 Troubleshooting: Broken Receipt Chain

**Problem**: Receipt verification fails.

**Diagnosis**:
```bash
# Verify chain integrity
bash scripts/verify-receipt-chain.sh receipts/intelligence.jsonl

# Output:
# Receipt[42]: INVALID
#   Expected chain_hash: blake3:xyz...
#   Actual chain_hash: blake3:abc...
#   Previous: blake3:def...
#
# Chain is broken at receipt[42]. Tampering detected or corruption.
```

**Recovery**: Recover to last good receipt:
```bash
# Find last valid receipt
jq '.[] | select(.verified == true)' receipts/intelligence.jsonl | tail -1

# Truncate file to last valid receipt
head -n 41 receipts/intelligence.jsonl > receipts/intelligence.jsonl.bak && \
  mv receipts/intelligence.jsonl.bak receipts/intelligence.jsonl

# Re-run current task to rebuild chain
```

---

## Part 9: Integration Checklist

### Pre-Session Checklist

- [ ] `.claude/context/` directory exists with `watermarks.json`
- [ ] `.claude/context/scout-config.toml` defines fetch list
- [ ] `receipts/intelligence.jsonl` exists (append-only)
- [ ] `yawl-scout` binary available and executable
- [ ] Team aware that UserPromptSubmit injection is active

### During Session

- [ ] Scout runs async at SessionStart (background, non-blocking)
- [ ] UserPromptSubmit injection enriches prompts with relevant deltas
- [ ] Agent reads injected context before making decisions
- [ ] PostToolUse injection records changes in receipt chain
- [ ] Check watermark expiry: no gratuitous re-fetches

### Post-Session

- [ ] Verify receipt chain integrity: `scripts/verify-receipt-chain.sh`
- [ ] Review cache hit ratio in scout logs
- [ ] Archive receipts for audit: `receipts/intelligence.jsonl.YYYYMMDD`
- [ ] Document any watermark misses for future optimization

---

## Part 10: Conclusion

The Intelligence Layer (ι) transforms agents from decision-makers operating in information darkness into informed collaborators with access to:

1. **Complete change history** — receipt chain proves what changed and when
2. **Efficient context loading** — watermarks prevent re-fetching unchanged specs
3. **Typed understanding** — deltas encode semantic intent, not just line changes
4. **Audit fidelity** — cryptographic hashes enable verification and root-cause analysis

By implementing the Fetch → Parse → Receipt flow, you enable:

- **Team coordination** — teammates understand each other's API contracts
- **Session efficiency** — no thrashing, no redundant fetches
- **Compliance** — immutable audit trail for regulatory requirements
- **Debugging** — receipt chains answer "what changed?" with certainty

For a working implementation, see the related how-to guides:
- `docs/how-to/enable-intelligence-layer.md` (once created)
- `docs/how-to/configure-watermarks.md` (once created)
- `docs/how-to/verify-receipt-chains.md` (once created)

**Latest Reference**: See `.claude/rules/CLAUDE.md` section **ι INTELLIGENCE** for the authoritative specification.

---

**Document Date**: 2026-02-28
**Version**: 1.0
**Status**: Production Ready
**Next Review**: 2026-03-31
