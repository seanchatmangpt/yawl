# YAWL v6.0.0 Documentation Index

**Version**: 6.0.0 | **Updated**: 2026-02-18 | **Status**: Production-Ready

---

## Quick Navigation

| Audience | Start Here |
|----------|------------|
| **New Developer** | [Getting Started Tutorial](#tutorials) |
| **Contributor** | [Architecture Overview](#architecture) |
| **Operator** | [Deployment Guide](#operations) |
| **Claude Code User** | [CLAUDE.md](../CLAUDE.md) |

---

## Documentation Quadrants

### Tutorials (Learning-Oriented)

| Tutorial | Description | Time |
|----------|-------------|------|
| [Getting Started](tutorials/getting-started.md) | 5-minute setup guide | 5 min |
| [First Workflow](tutorials/first-workflow.md) | Build your first YAWL net | 15 min |
| [Agent Integration](tutorials/agent-integration.md) | Connect autonomous agents | 20 min |
| [MCP/A2A Setup](tutorials/mcp-a2a-setup.md) | Model Context Protocol setup | 10 min |

### Guides (Problem-Oriented)

| Guide | Solves |
|-------|--------|
| [Build Optimization](guides/build-optimization.md) | Slow build times |
| [Migration v5 to v6](guides/migration-v5-v6.md) | Upgrading from v5.x |
| [Performance Tuning](guides/performance-tuning.md) | Workflow bottlenecks |
| [Security Hardening](guides/security-hardening.md) | Production security |

### Reference (Information-Oriented)

| Reference | Coverage |
|-----------|----------|
| [API Documentation](reference/api/) | Interface A, B, E, X |
| [Schema Reference](reference/schemas/) | YAWL XML schemas |
| [Configuration](reference/configuration/) | Engine, services, agents |
| [Error Codes](reference/error-codes.md) | Diagnostic reference |

### Explanation (Understanding-Oriented)

| Concept | Description |
|---------|-------------|
| [Petri Net Semantics](explanation/petri-nets.md) | Formal foundation |
| [Workflow Patterns](explanation/patterns.md) | 43+ control-flow patterns |
| [Agent Architecture](explanation/agents.md) | Autonomous agent design |
| [Observatory System](explanation/observatory.md) | Codebase instrumentation |

---

## Codebase Facts (Observatory)

**Generated**: 2026-02-18T19:29:52Z | **Status**: RED | **Health Score**: 100

### Quick Facts

| Fact | Source | What It Answers |
|------|--------|-----------------|
| [Module Inventory](v6/latest/facts/modules.json) | `modules.json` | What modules exist? |
| [Build Order](v6/latest/facts/reactor.json) | `reactor.json` | Maven reactor sequence? |
| [Integration Status](v6/latest/facts/integration.json) | `integration.json` | MCP/A2A ready? |
| [Static Analysis](v6/latest/facts/static-analysis.json) | `static-analysis.json` | Code health summary? |

### All Facts (9 files)

- `facts/modules.json` - Module inventory with source strategies
- `facts/reactor.json` - Maven reactor build order
- `facts/integration.json` - MCP/A2A integration status
- `facts/static-analysis.json` - Aggregated code health
- `facts/spotbugs-findings.json` - SpotBugs bug findings
- `facts/pmd-violations.json` - PMD rule violations
- `facts/checkstyle-warnings.json` - Checkstyle warnings
- `facts/coverage.json` - Test coverage metrics
- `facts/integration-facts.json` - Detailed integration facts

### Diagrams (8 files)

| Diagram | Visualizes |
|---------|------------|
| [Maven Reactor](v6/latest/diagrams/10-maven-reactor.mmd) | Module dependency graph |
| [Risk Surfaces](v6/latest/diagrams/50-risk-surfaces.mmd) | FMEA risk map |
| [Code Health](v6/latest/diagrams/60-code-health-dashboard.mmd) | Health dashboard |
| [MCP Architecture](v6/latest/diagrams/60-mcp-architecture.mmd) | MCP integration flow |
| [Static Analysis Trends](v6/latest/diagrams/61-static-analysis-trends.mmd) | Trend visualization |
| [A2A Topology](v6/latest/diagrams/65-a2a-topology.mmd) | A2A integration flow |
| [Agent Capabilities](v6/latest/diagrams/70-agent-capabilities.mmd) | Agent capability matrix |
| [Protocol Sequences](v6/latest/diagrams/75-protocol-sequences.mmd) | Protocol flow diagrams |

### Verification

- [Observatory Receipt](v6/latest/receipts/observatory.json) - SHA256 provenance
- [Performance Summary](v6/latest/performance/) - Benchmark results

---

## Architecture

### Core Components

| Component | Package | Description |
|-----------|---------|-------------|
| **Engine** | `org.yawlfoundation.yawl.engine` | Stateful workflow engine |
| **Stateless Engine** | `org.yawlfoundation.yawl.stateless` | Event-driven engine |
| **Elements** | `org.yawlfoundation.yawl.elements` | YSpecification, YNet, YTask |
| **Resourcing** | `org.yawlfoundation.yawl.resourcing` | Resource service |
| **Integration** | `org.yawlfoundation.yawl.integration` | MCP/A2A servers |

### Interfaces

| Interface | Purpose | Clients |
|-----------|---------|---------|
| **A** | Design-time | YAWL Editor, spec upload |
| **B** | Client/Runtime | Worklist, case management |
| **E** | Events | External event listeners |
| **X** | Extended | Custom services |

### Patterns

YAWL implements 43+ workflow control-flow patterns:

| Category | Patterns |
|----------|----------|
| Basic | Sequence, Parallel Split, Synchronization, Exclusive Choice |
| Advanced | Multi-Choice, Discriminator, N-out-of-M, Deferred Choice |
| Iteration | Structured Loop, Recursion, Arbitrary Cycles |
| Cancellation | Cancel Activity, Cancel Case, Cancel Region |
| State | Interleaved Routing, Milestone, Critical Section |

See [Workflow Patterns](explanation/patterns.md) for complete list.

---

## Claude Code Integration

### Configuration Files

| File | Purpose |
|------|---------|
| [CLAUDE.md](../CLAUDE.md) | Main agent instructions (A = mu(O)) |
| [BEST-PRACTICES-2026.md](../.claude/BEST-PRACTICES-2026.md) | 12 best practices sections |
| [JAVA-25-FEATURES.md](../.claude/JAVA-25-FEATURES.md) | Java 25 adoption roadmap |
| [ARCHITECTURE-PATTERNS-JAVA25.md](../.claude/ARCHITECTURE-PATTERNS-JAVA25.md) | 8 architectural patterns |
| [BUILD-PERFORMANCE.md](../.claude/BUILD-PERFORMANCE.md) | Maven optimization guide |
| [SECURITY-CHECKLIST-JAVA25.md](../.claude/SECURITY-CHECKLIST-JAVA25.md) | Production security |
| [OBSERVATORY.md](../.claude/OBSERVATORY.md) | Observatory instrument protocol |

### Agents

| Agent | Role | Invoke With |
|-------|------|-------------|
| `engineer` | Implement features | Task(..., "engineer") |
| `validator` | Run builds, verify | Task(..., "validator") |
| `architect` | Design patterns | Task(..., "architect") |
| `integrator` | Coordinate subsystems | Task(..., "integrator") |
| `reviewer` | Code quality | Task(..., "reviewer") |
| `tester` | Write/run tests | Task(..., "tester") |
| `prod-val` | Production validation | Task(..., "prod-val") |
| `perf-bench` | Benchmark performance | Task(..., "perf-bench") |

### Guards (H)

Post-tool-use validation enforces these anti-patterns:

```
H = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
```

See [HYPER_STANDARDS.md](../.claude/HYPER_STANDARDS.md) for details.

### Hooks

| Hook | Trigger | Action |
|------|---------|--------|
| `session-start.sh` | Session begin | Bootstrap Maven + H2 |
| `validate-no-mocks.sh` | Post-response | Verify no guard violations |
| `hyper-validate.sh` | Post-Write/Edit | Block forbidden patterns |

---

## Operations

### Build Commands

```bash
# Fast agent DX loop (PREFERRED)
bash scripts/dx.sh                       # Compile + test changed modules only
bash scripts/dx.sh compile               # Compile changed modules
bash scripts/dx.sh test                  # Test changed modules
bash scripts/dx.sh all                   # Compile + test ALL modules

# Standard build
mvn -T 1.5C clean compile               # Parallel compile (~45s)
mvn -T 1.5C clean test                  # Parallel tests (~90s)
mvn -T 1.5C clean package               # Full build

# Validation
bash scripts/validation/validate-release.sh  # Pre-release validation
bash scripts/observatory/observatory.sh      # Refresh facts
```

### Performance Baselines

| Metric | Target | Location |
|--------|--------|----------|
| Clean compile | <60s | `v6/latest/performance/build-baseline.json` |
| Observatory run | <5s | `v6/latest/performance/observatory-baseline.json` |
| Test coverage | >75% | `v6/latest/performance/test-coverage-baseline.json` |

### Deployment

```bash
# Build Docker image
docker buildx bake --load

# Run in container
docker run -it --rm -v $(pwd):/work -w /work yawl:6.0.0 bash

# Production deployment
kubectl apply -f k8s/deployment.yaml
```

---

## Validation & Quality

### Validation Scripts

| Script | Validates |
|--------|-----------|
| `scripts/validation/validate-documentation.sh` | Links, schemas, coverage |
| `scripts/validation/validate-observatory.sh` | Fact freshness, SHA256 |
| `scripts/validation/validate-performance-baselines.sh` | Regression detection |
| `scripts/validation/validate-release.sh` | Complete pre-release check |

### Quality Gates

| Gate | Threshold | Enforced By |
|------|-----------|-------------|
| Package-info coverage | 100% | CI/CD |
| Test pass rate | 100% | Maven |
| Build time regression | <10% | Validation script |
| Link integrity | 0 broken | markdown-link-check |

### CI/CD Pipeline

- **Trigger**: Push to docs/, .claude/, schema/
- **Jobs**: Link validation, observatory validation, XSD validation, coverage check
- **Artifact**: Validation report uploaded on completion

---

## Release Information

- [Release Checklist](RELEASE-CHECKLIST.md) - Pre-release validation steps
- [Migration Guide v5.2 to v6.0](MIGRATION-v5.2-to-v6.0.md) - Upgrade instructions
- [Final Implementation Plan](FINAL-IMPLEMENTATION-PLAN.md) - Days 7-14 roadmap
- [CHANGELOG](../CHANGELOG.md) - Version history

---

## Related Resources

| Resource | Link |
|----------|------|
| Official Website | https://yawl.foundation |
| GitHub Repository | https://github.com/yawlfoundation/yawl |
| Issue Tracker | https://github.com/yawlfoundation/yawl/issues |
| Maven Central | https://search.maven.org/search?q=yawl |

---

## Documentation Maintenance

### Refresh Commands

```bash
# Refresh observatory facts (after code changes)
bash scripts/observatory/observatory.sh

# Validate all documentation
bash scripts/validation/validate-documentation.sh

# Update performance baselines
bash scripts/performance/measure-baseline.sh
```

### Staleness Detection

Check `docs/v6/latest/receipts/observatory.json`:

```bash
# Verify observatory receipt
RECEIPT_SHA=$(jq -r '.outputs.index_sha256' docs/v6/latest/receipts/observatory.json)
ACTUAL_SHA="sha256:$(sha256sum docs/v6/latest/INDEX.md | cut -d' ' -f1)"

if [[ "$RECEIPT_SHA" != "$ACTUAL_SHA" ]]; then
  echo "Observatory facts stale. Run: bash scripts/observatory/observatory.sh"
fi
```

---

**Generated by**: YAWL v6.0.0 Documentation System
**Last Updated**: 2026-02-18
**Maintained by**: Documentation Architect
