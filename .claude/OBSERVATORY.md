# Ψ (Observatory) — Codebase Instrument Protocol

## Axiom: Observe ≺ Act ≺ Assert

**Your context window is finite. The codebase is not.**
Do NOT read 736 source files to answer "which module owns YEngine?"
Read 1 fact file (50 tokens) instead of grepping (5000 tokens).
**Compression ratio: 100x.**

## Ψ.run — Refresh Facts

```bash
bash scripts/observatory/observatory.sh          # Full run (~17s)
bash scripts/observatory/observatory.sh --facts   # Facts only (~13s)
bash scripts/observatory/observatory.sh --diagrams # Diagrams only (~2s)
```

**Output**: `docs/v6/latest/{INDEX.md, receipts/, facts/, diagrams/}`

## Ψ.facts — Question → File Map

| Question | Read This | NOT This |
|----------|-----------|----------|
| What modules exist? | `facts/modules.json` | `grep '<module>' pom.xml` |
| Build order? Dependencies? | `facts/reactor.json` | `mvn dependency:tree` |
| Who owns which source files? | `facts/shared-src.json` | `find src/ -name '*.java'` |
| Stateful ↔ stateless mapping? | `facts/dual-family.json` | `grep -r 'class Y' src/` |
| Duplicate classes? | `facts/duplicates.json` | `find . -name '*.java' \| sort` |
| Dependency conflicts? | `facts/deps-conflicts.json` | `mvn dependency:analyze` |
| Tests per module? | `facts/tests.json` | `find test/ -name '*Test.java'` |
| Quality gates active? | `facts/gates.json` | reading 1700-line pom.xml |
| M2 cache hazards? | `facts/maven-hazards.json` | `ls ~/.m2/repository/` |

### Reading Protocol

```
1. Read(docs/v6/latest/INDEX.md)        # 74 lines — full manifest
2. Read(docs/v6/latest/facts/<X>.json)   # Answer your specific question
3. If facts stale → bash scripts/observatory/observatory.sh
```

**Staleness check**: Compare `receipts/observatory.json → outputs.index_sha256`
against `sha256sum docs/v6/latest/INDEX.md`. If different, re-run.

## Ψ.diagrams — Visual Topology

7 Mermaid diagrams + 1 YAWL XML — read these when JSON isn't sufficient:

| Diagram | Shows | When To Read |
|---------|-------|-------------|
| `10-maven-reactor.mmd` | Module DAG | Before adding dependencies |
| `15-shared-src-map.mmd` | Source ownership | Before editing shared code |
| `16-dual-family-map.mmd` | Stateful↔stateless | Before touching Y* classes |
| `17-deps-conflicts.mmd` | Dep hotspots | Before upgrading versions |
| `30-test-topology.mmd` | Test distribution | Before writing tests |
| `40-ci-gates.mmd` | Gate lifecycle | Before modifying CI |
| `50-risk-surfaces.mmd` | FMEA risk map | Before high-risk changes |

## Ψ.receipt — Provenance

`receipts/observatory.json` contains:
- `run_id`, `status` (GREEN/YELLOW/RED)
- `inputs.root_pom_sha256` — POM fingerprint at generation time
- `outputs.{facts,diagrams,index}_sha256` — integrity hashes
- `refusals[]` — any data the observatory could NOT produce
- `warnings[]` — any data that may be inaccurate
- `timing_ms` — performance budget

## Φ — Building New Instruments

**Meta-principle**: When you encounter a question about the codebase that requires
reading >3 files to answer, **build an instrument** instead of exploring ad-hoc.

### Instrument Design Pattern

```
Instrument(question) → {
  1. Define: What question does this answer?
  2. Source: Where does ground truth live? (POM, source, config)
  3. Extract: Shell/Python one-liner to extract the fact
  4. Format: JSON (machine-readable, diffable, hashable)
  5. Visualize: Mermaid diagram (human-readable topology)
  6. Receipt: SHA256 hash + timestamp for provenance
}
```

### Example: Adding a New Fact

To add `facts/new-fact.json` to the observatory:

1. Add `emit_new_fact()` function to `scripts/observatory/lib/emit-facts.sh`
2. Follow the existing pattern:
   ```bash
   emit_new_fact() {
       local out="$FACTS_DIR/new-fact.json"
       log_info "Emitting facts/new-fact.json ..."
       # Extract ground truth from real sources (POM, source tree, configs)
       # Format as JSON
       # Write to $out
   }
   ```
3. Add the call to the `run_facts()` function
4. Add a corresponding Mermaid diagram to `emit-diagrams.sh` if topology is involved
5. Re-run observatory to verify

### Anti-Patterns for Instruments

| Anti-Pattern | Why It Fails | Correct Approach |
|-------------|-------------|------------------|
| Hardcoded counts | Drift immediately | Extract from source dynamically |
| Grepping without context | False positives | Parse XML/JSON structurally (python3) |
| Counting visible, not owned | Inflated numbers | Scope via include/exclude filters |
| Binary "enabled" flags | Misses profile-gating | Check activation context (executions, profiles, skip) |
| No deduplication | Duplicate entries | Use associative arrays or `sort -u` |

### Information Budget

| Operation | Token Cost | Use When |
|-----------|-----------|----------|
| Read 1 fact file | ~50 | Always (first resort) |
| Read INDEX.md | ~74 | Start of session |
| Run observatory | ~17s wall clock, 0 tokens | Facts are stale |
| Grep codebase | ~500-5000 | Fact file doesn't cover your question |
| Read source files | ~200/file | You need implementation details |
| Read all 736 sources | ~150K | NEVER. Build an instrument instead. |

## Ψ_∞ — Recursive Self-Improvement

The observatory already practices FMEA on itself (see `50-risk-surfaces.mmd`).
When you find an observatory output that's wrong:

```
1. Identify the failure mode (what was wrong?)
2. Score: S×O×D = RPN
3. Fix the emit_*() function (root cause, not symptom)
4. Re-run → verify → commit
```

This is the fundamental loop: **Observe → Diagnose → Instrument → Verify → Commit**.
It applies to the codebase AND to the observatory itself.
