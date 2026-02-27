# Why Reactor Ordering Matters

**Quadrant**: Explanation | **Concept**: Maven reactor order + FM7 failure mode

This document explains the *why* behind the 19-module topological build order. Understanding this concept helps you avoid FM7 (Reactor Order Violation, RPN=105) when adding modules or modifying dependencies.

---

## What the Maven Reactor Does

When you run `mvn compile` at the root of a multi-module project, Maven reads the `<modules>` list in the root `pom.xml` and builds them in the order they appear. This is called the **reactor order**.

Maven does not automatically reorder modules based on declared dependencies. It builds them in the order you listed them, left to right, top to bottom. If module B depends on module A, and you listed B before A, Maven will try to compile B before A's classes exist — and fail with a dependency resolution error.

This makes the `<modules>` list in `pom.xml` a topological specification, not just an inventory.

---

## Directed Acyclic Graphs and Topological Order

The 19 YAWL modules form a **directed acyclic graph (DAG)**. Each module is a node; each Maven dependency is an edge pointing from consumer to provider. "Topological order" means: for every edge A → B, module A appears before module B in the build sequence.

There can be many valid topological orderings of the same DAG. YAWL uses a layered grouping that also maximizes parallelism within each layer:

```
Layer 0: utilities, security, graalpy, graaljs          (parallel — no YAWL deps)
Layer 1: elements, ggen, graalwasm                       (parallel — each depends on one L0)
Layer 2: engine                                          (serial — depends on elements)
Layer 3: stateless                                       (serial — depends on engine)
Layer 4: authentication, scheduling, monitoring,         (parallel — all depend on engine/stateless)
         worklet, control-panel, integration, webapps
Layer 5: pi, resourcing                                  (parallel — both depend on integration)
Layer 6: mcp-a2a-app                                     (serial — depends on pi + resourcing)
```

Every module in Layer N depends only on modules in layers 0 through N−1. Modules in the same layer have no dependencies on each other and can compile concurrently.

---

## Why This Failed in Practice

Before the 2026-02-27 fix, the `scripts/dx.sh` `ALL_MODULES` array contained this ordering:

```bash
# WRONG — authentication before engine:
yawl-utilities yawl-elements yawl-authentication yawl-engine ...
```

`yawl-authentication` depends on `yawl-engine`. When `dx.sh` tried to compile `yawl-authentication` before `yawl-engine` was built, the compile would fail with a `package org.yawlfoundation.yawl.engine does not exist` error — but only if `yawl-engine` wasn't already compiled from a previous run. In a clean environment (CI, fresh checkout), this consistently failed. In an incremental local build where the previous compile artifacts were still present, it *seemed* to work — which is exactly what makes this class of bug dangerous. The bug was invisible locally and catastrophic in CI.

The same ordering error existed in `docs/v6/DEFINITION-OF-DONE.md §3.1`, which listed the reactor order as documentation guidance used by agents setting up environments.

---

## The Danger of Invisible Bugs

Reactor order violations have high Detection difficulty (D=7 in the FMEA) because:

1. **Incremental builds hide the problem**: If you compile in the right order once and then change only one file, Maven uses cached `.class` files from previous compiles. The violation is only visible during a clean build.
2. **The error message is misleading**: `package X does not exist` looks like a missing dependency, not an ordering problem.
3. **The DoD doesn't automatically enforce it**: Nothing prevents you from writing the wrong order in `pom.xml` or `dx.sh`.

The FM7 poka-yoke solution is `reactor.json` — a machine-readable source of truth that agents read before modifying `pom.xml` or `dx.sh`. When the authoritative order is documented and the documentation is checked, the probability of introducing a new ordering bug drops significantly.

---

## Parallelism as an Optimization

The layer structure isn't just about correctness — it's also about speed. Maven's `-T 1.5C` flag (used in the DoD compile commands) tells Maven to use 1.5 threads per CPU core. Modules in the same layer can be compiled on separate threads simultaneously.

With a typical 4-core machine at 6 threads:
- Layer 0 (4 modules): all compile in parallel — longest single compile time, not sum
- Layer 1 (3 modules): all compile in parallel
- Layers 2–3 (serial): no benefit from threading, but unavoidable given dependencies
- Layer 4 (7 modules): all 7 compile in parallel — the biggest speed win

Without the layer grouping, a sequential build of all 19 modules takes 3–5 minutes. With `-T 1.5C` and the correct layer grouping, it typically completes in under 90 seconds.

---

## Why `yawl-webapps` Is Special

`yawl-webapps` is listed in Layer 4, but it has no YAWL module dependencies — it's an aggregator POM that collects WAR files. It could technically be built at any layer. It's placed at Layer 4 for pragmatic reasons:

1. The WAR files it aggregates are produced by Layer 2–4 modules
2. Building it too early produces an incomplete WAR bundle
3. Layer 4 is the earliest point where all constituent WARs exist

This is a case where the logical dependency (on the built artifacts) differs from the Maven POM dependency declaration. `reactor.json` documents this with a `note` field.

---

## The Critical Path

The longest chain in the dependency graph determines the minimum build time regardless of parallelism:

```
utilities → elements → engine → stateless → integration → pi → mcp-a2a-app
```

This is 7 hops. Even with infinite CPUs, you cannot build `mcp-a2a-app` faster than the sum of compile times along this chain. Any change to any module on this path ripples to all downstream modules.

This has implications for how you prioritize changes during development:
- Changes to `yawl-utilities` invalidate 17 downstream modules
- Changes to `yawl-elements` invalidate 15 downstream modules
- Changes to `yawl-integration` invalidate only `yawl-pi`, `yawl-resourcing`, and `yawl-mcp-a2a-app`

When doing exploratory work, preferring changes to high-layer modules (integration, resourcing, mcp-a2a-app) minimizes rebuild time.

---

## Adding a New Module Correctly

When adding a new module, the layer assignment follows a simple rule: **your layer = max(dependency layers) + 1**.

If your new module depends on `yawl-engine` (Layer 2) and `yawl-stateless` (Layer 3):
- max(2, 3) = 3
- Your layer = 3 + 1 = **4**

You then add your module to `pom.xml` after all Layer 3 modules and before any Layer 5 modules. You also update `reactor.json` so other agents and the poka-yoke mechanism have the correct information.

The checklist in [How to Add a Maven Module](../how-to/build/add-maven-module.md) walks through this process step by step.

---

## See Also

- [Build Sequences Reference](../reference/build-sequences.md) — 19-module canonical order table
- [FMEA Risk Table](../reference/fmea-risk-table.md) — FM7 risk analysis
- [How-To: Add a Maven Module](../how-to/build/add-maven-module.md) — correct reactor placement
- [dx.sh CLI Reference](../reference/dx-sh.md) — how the ordering is used in builds
