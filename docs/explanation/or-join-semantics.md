# OR-Join Semantics

> YAWL's OR-join fires when all paths that could still deliver a token to it have already done so — a property that requires exhaustive reachability analysis and distinguishes YAWL from most other workflow formalisms.

## The Four Join Types in YAWL

`YTask` (`src/org/yawlfoundation/yawl/elements/YTask.java`) defines three join type constants:

```java
public static final int _AND = 95;
public static final int _OR  = 103;
public static final int _XOR = 126;
```

These numeric codes are stored in `YTask._joinType` and referenced throughout the engine. In the XSD schema, join types are expressed as the `ControlTypeCodeType` simple type with values `"and"`, `"or"`, and `"xor"`.

**AND-join** (`_joinType = _AND`): Fires when all preset places hold a token. It requires every upstream branch to deliver a token. This is unconditional synchronisation: the task waits for every path, regardless of how many branches the preceding split activated.

**XOR-join** (`_joinType = _XOR`): Fires when any one preset place holds a token. Each incoming condition is an independent trigger; tokens from other branches are simply not present. XOR-join works naturally when the preceding split was an XOR-split (exactly one branch activates), because exactly one token will arrive.

**OR-join** (`_joinType = _OR`): Fires when all incoming paths that are still capable of producing a token have already done so. This requires the engine to determine which branches are "live" — still reachable from the current marking — and which are "dead" — cancelled, already consumed, or unreachable. Only when no live path could still deliver a token to a remaining empty preset place does the OR-join become enabled.

**Duplicate-AND** is not a distinct join type in the YAWL codebase. The term refers to an AND-join used in a context where multiple XOR-splits converge, causing the AND-join to deadlock because some branches never produce tokens. This is a spec design error, not a distinct mode.

## Why OR-Join Is Hard

XOR-join and AND-join are both decidable in constant or linear time: check for exactly one token, or check all places for tokens. OR-join is qualitatively harder because it requires answering the question: "Could any token currently moving through the net eventually reach this join's empty input places?"

This is a reachability problem over the net's state space. For arbitrary nets, reachability in Petri nets is decidable but requires exponential time and space in the worst case. YAWL's semantics demand that OR-joins be correctly resolved without approximation, which means the engine must run a formal algorithm — not a heuristic — every time it evaluates an OR-join's enabledness.

The computational cost is real. In a net with deep branching or many active tokens, the OR-join check may be significantly more expensive than any other control-flow operation. This cost is paid on every evaluation: every time the engine calls `continueIfPossible` or re-evaluates task enabledness, every OR-join in the net with at least one token in its preset is re-evaluated.

## YAWL's Algorithm: E2WFOJ Reset Net Analysis

YAWL uses the E2WFOJ (Enabling for WFOJ — Workflow OR-Join) algorithm, based on Reset net theory. The implementation is in:

- `src/org/yawlfoundation/yawl/engine/core/elements/E2WFOJCore.java` — canonical algorithm implementation
- `src/org/yawlfoundation/yawl/elements/e2wfoj/E2WFOJNet.java` — stateful engine wrapper
- `src/org/yawlfoundation/yawl/stateless/elements/e2wfoj/E2WFOJNet.java` — stateless engine wrapper
- Supporting types in `src/org/yawlfoundation/yawl/elements/e2wfoj/`: `RPlace`, `RTransition`, `RFlow`, `RMarking`, `RSetOfMarkings`, `CombinationGenerator`

### Step 1: Convert to Reset Net

`E2WFOJCore.convertToResetNet()` transforms the YAWL net into a Reset net representation:

- Each YAWL condition becomes an `RPlace`.
- Each YAWL task becomes an internal place `p_<taskId>` (representing the task being in progress).
- Each task's join type produces start transitions: AND-join → one `<taskId>_start` transition requiring all preset places; XOR-join → one `<taskId>_start^<presetId>` transition per preset place (any single one enables it).
- Each task's split type produces end transitions: AND-split → one end transition posting to all postset places; XOR-split → one end transition per postset place; OR-split → all non-empty subsets of the postset each get their own end transition (exponential in postset size, but bounded by spec design).
- Cancellation regions (the `removesTokens` set) become reset arcs on end transitions.

### Step 2: Remove the OR-Join Being Evaluated

`E2WFOJCore.removeOrJoin(orJoin)` removes the start transition of the OR-join under analysis from the Reset net. This is the key step: by removing the OR-join's own transition, the algorithm asks "if this join cannot fire, can the current marking still evolve to deliver a token to any of its empty input places?" Other OR-joins in the net are converted to XOR semantics (one start transition per preset) to avoid recursive dependency.

### Step 3: Structural Restriction

`E2WFOJCore.restrictNet(orJoin)` (called via `E2WFOJNet.restrictNet(YTask)`) performs backward reachability from the OR-join's preset places through the Reset net to retain only the subnet that can influence those places. Transitions and places not reachable backward from the OR-join's preset are pruned. This dramatically reduces the state space for the coverability check.

### Step 4: Active Projection

`E2WFOJCore.restrictNet(marking)` (called via `E2WFOJNet.restrictNet(YMarking)`) performs forward reachability from the current marking, retaining only the subnet reachable from currently marked places. Combined with backward restriction, this leaves only the fragment of the net relevant to both "where tokens are now" and "how they could reach the OR-join".

### Step 5: Coverability Check

The core question: for each empty preset place of the OR-join, is there a marking reachable from the current marking (in the restricted Reset net) that places a token in that empty preset place?

`E2WFOJCore.orJoinEnabled(marking, orJoin)` constructs a set of "bigger-enabling markings" — one per empty preset place, each being the base marking (tokens already in preset) extended with one additional token in the empty place. For each such target, `isCoverable` checks whether the restricted Reset net can reach a marking that covers (is >= component-wise) the target.

`isCoverable` calls `computeFiniteBasisPredecessors`, which iteratively computes the minimal basis of predecessor markings using backwards firing (`getPreviousMarking`). If any predecessor is component-wise <= the current marking, the target is coverable — meaning a live path to the empty preset place exists.

**If ANY empty preset place is coverable** (i.e., a live path exists), the OR-join is NOT enabled. `orJoinEnabled` returns `false`.

**If NO empty preset place is coverable** (all remaining paths are dead), the OR-join IS enabled. `orJoinEnabled` returns `true`.

### Where It Is Called

`YNet.orJoinEnabled` (`src/org/yawlfoundation/yawl/elements/YNet.java`, line 386) orchestrates the check:

```java
public boolean orJoinEnabled(YTask orJoinTask, YIdentifier caseID) {
    YMarking actualMarking = new YMarking(caseID);
    Set preSet = orJoinTask.getPresetElements();
    if (locations.containsAll(preSet)) {
        return true;   // all preset places have tokens: trivially enabled
    }
    for (YNetElement element : locations) {
        if (preSet.contains(element)) {
            E2WFOJNet e2Net = new E2WFOJNet(this, orJoinTask);
            e2Net.restrictNet(actualMarking);
            e2Net.restrictNet(orJoinTask);
            return e2Net.orJoinEnabled(actualMarking, orJoinTask);
        }
    }
    return false;  // no tokens in any preset place
}
```

The fast path — all preset places hold tokens — avoids the full algorithm. Only when a partial set of preset tokens is present (some present, some absent) does the engine invoke `E2WFOJNet`. This optimisation is critical because AND-joins and fully-ready OR-joins share the same O(1) check.

`YTask` calls `orJoinEnabled` via `_net.orJoinEnabled(this, id)` at line 1068 of `YTask.java`.

## OR-Join vs XOR-Join vs AND-Join: Decision Guide

| Situation | Correct join |
|---|---|
| Preceding split was XOR (exactly one branch taken) | XOR-join |
| All parallel branches must synchronise | AND-join |
| One OR-split may activate any subset; join when all active branches finish | OR-join |
| Some branches may be cancelled mid-execution | OR-join (OR-join handles partial completion; AND-join would deadlock) |

**The key intuition:** Use OR-join when the number of tokens that will arrive is not fixed at design time because the upstream path selection was an OR-split. OR-split chooses any non-empty subset of outputs; OR-join waits for exactly those paths that were activated.

## Common Mistakes That Create OR-Join Deadlocks

**Mistake 1: OR-join paired with AND-split.** An AND-split always activates all branches. An OR-join downstream of an AND-split will fire once all branches deliver tokens — which is equivalent to AND-join semantics and wastes the OR-join's complexity. More dangerously, if any branch has a task that can fail or is unreachable under certain data conditions, the OR-join waits indefinitely because the E2WFOJ algorithm correctly detects that a live path still exists.

**Mistake 2: OR-join downstream of another OR-join with overlapping paths.** If path A goes through OR-join 1 and then continues to OR-join 2, and path B also reaches OR-join 2 directly, the liveness analysis at OR-join 2 must account for whether OR-join 1 has already fired (removing its token). Incorrect spec design here can make OR-join 2 appear perpetually not-enabled because the algorithm detects OR-join 1's path as live even when OR-join 1 already fired and its output token was consumed.

**Mistake 3: Placing tokens in cancellation regions that feed the OR-join.** If a task in the OR-join's backward reachable subnet has a cancellation region that removes tokens from conditions the OR-join waits on, the E2WFOJ algorithm accounts for this via reset arcs. However, if the designer places the OR-join task itself in a cancellation region triggered by a sibling task, the OR-join can be cancelled before it fires — leaving the case in a state where the OR-join is gone but the net is not complete.

**Mistake 4: Using AND-join where OR-join is needed after OR-split.** The OR-split may send tokens down only some branches. An AND-join expects all branches to deliver tokens. Since the inactive branches never produce tokens, the AND-join waits forever. The case deadlocks. The YAWL verifier does not always catch this statically because branch activation depends on runtime data values.

## Performance Considerations for Coding Agents

Every instance of `E2WFOJNet` is constructed fresh per OR-join evaluation (no caching). Each construction invokes `convertToResetNet()`, which iterates all net elements and all flow arcs. For nets with many tasks and OR-splits, this per-evaluation cost accumulates.

The stateful engine holds the `E2WFOJNet` as a lazy field `_resetNet` in `YTask` (set via `setResetNet`), but the structural restriction and active projection must be recomputed per evaluation because the current marking changes. Marking-dependent steps cannot be cached without invalidation logic tied to every token movement.

When designing nets for performance-critical use cases, minimize OR-joins. Prefer XOR-joins when only one branch can be active, or AND-joins when all branches are guaranteed to deliver tokens. OR-joins are semantically necessary when OR-splits and partial-branch execution are genuine requirements of the process, but they carry a measurable runtime cost proportional to the net's complexity.
