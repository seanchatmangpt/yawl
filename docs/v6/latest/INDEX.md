# v6 Observatory (Latest)

Run: 20260217T230032Z  Status: GREEN

## Receipt
- [receipts/observatory.json](receipts/observatory.json)

## Facts
- [facts/modules.json](facts/modules.json) — Module inventory (names, paths, source strategy)
- [facts/reactor.json](facts/reactor.json) — Maven reactor order and inter-module dependencies
- [facts/shared-src.json](facts/shared-src.json) — Shared source roots and ownership ambiguities
- [facts/dual-family.json](facts/dual-family.json) — Stateful/stateless mirror class families
- [facts/duplicates.json](facts/duplicates.json) — Duplicate FQCNs within and across artifacts
- [facts/deps-conflicts.json](facts/deps-conflicts.json) — Dependency version convergence analysis
- [facts/tests.json](facts/tests.json) — Test topology (surefire/failsafe, counts per module)
- [facts/gates.json](facts/gates.json) — Quality gates (SpotBugs, PMD, Checkstyle, JaCoCo)
- [facts/maven-hazards.json](facts/maven-hazards.json) — Maven cache hazards and build traps

## Diagrams
- [diagrams/10-maven-reactor.mmd](diagrams/10-maven-reactor.mmd) — Maven reactor dependency graph
- [diagrams/15-shared-src-map.mmd](diagrams/15-shared-src-map.mmd) — Shared source ownership map
- [diagrams/16-dual-family-map.mmd](diagrams/16-dual-family-map.mmd) — Stateful/stateless mirror families
- [diagrams/17-deps-conflicts.mmd](diagrams/17-deps-conflicts.mmd) — Dependency conflict hotspot map
- [diagrams/30-test-topology.mmd](diagrams/30-test-topology.mmd) — Test distribution across modules
- [diagrams/40-ci-gates.mmd](diagrams/40-ci-gates.mmd) — CI quality gate lifecycle
- [diagrams/50-risk-surfaces.mmd](diagrams/50-risk-surfaces.mmd) — FMEA risk surface analysis

## YAWL Workflow
- [diagrams/yawl/build-and-test.yawl.xml](diagrams/yawl/build-and-test.yawl.xml) — Build lifecycle as YAWL net

## FMEA Risk Priority Numbers

| ID | Failure Mode | S | O | D | RPN | Mitigation |
|----|-------------|---|---|---|-----|------------|
| FM1 | Shared Source Path Confusion | 9 | 8 | 3 | 216 | shared-src.json + 15-shared-src-map.mmd |
| FM2 | Dual-Family Class Confusion | 8 | 7 | 4 | 224 | dual-family.json + 16-dual-family-map.mmd |
| FM3 | Dependency Version Skew | 7 | 6 | 5 | 210 | deps-conflicts.json + 17-deps-conflicts.mmd |
| FM4 | Maven Cached Missing Artifacts | 6 | 5 | 2 | 60 | maven-hazards.json |
| FM5 | Test Selection Ambiguity | 7 | 4 | 3 | 84 | tests.json + 30-test-topology.mmd |
| FM6 | Gate Bypass via Skip Flags | 8 | 3 | 6 | 144 | gates.json + 40-ci-gates.mmd |
| FM7 | Reactor Order Violation | 5 | 3 | 7 | 105 | reactor.json + 10-maven-reactor.mmd |

**S**=Severity **O**=Occurrence **D**=Detection (1=best, 10=worst) **RPN**=S*O*D

## How Mermaid Diagrams Solve Path Confusion

The 7 Mermaid diagrams provide **visual topology truth** that eliminates guessing:

1. **10-maven-reactor.mmd** — Shows which modules depend on which, preventing wrong build order
2. **15-shared-src-map.mmd** — Maps every module to its actual source root and include filters,
   so agents know exactly which files belong to which module
3. **16-dual-family-map.mmd** — Explicitly maps every stateful class to its stateless mirror,
   preventing edits to the wrong variant
4. **17-deps-conflicts.mmd** — Shows dependency management categories and conflict hotspots,
   so version issues are visible before they cause runtime errors
5. **30-test-topology.mmd** — Shows which modules have tests and where they live,
   preventing test selection errors
6. **40-ci-gates.mmd** — Shows the full gate lifecycle and what skip flags disable,
   preventing accidental gate bypass
7. **50-risk-surfaces.mmd** — FMEA risk surface map with RPN scores and mitigations

## How YAWL XML Solves Build Workflow Ambiguity

The YAWL XML specification (build-and-test.yawl.xml) models the build lifecycle as a
Petri-net-based workflow with:

- **Input/Output conditions** bounding the lifecycle
- **Sequential tasks** (Validate -> Compile -> UnitTests) enforcing order
- **Parallel AND-split** for quality gates (SpotBugs, PMD, Checkstyle run concurrently)
- **AND-join** synchronization before integration tests
- **Documentation** on each task linking to specific refusal codes

This makes the build process **executable** and **verifiable** — not just documented.
