# Build Sequences Reference

**Quadrant**: Reference | **Source of truth**: `docs/build-sequences.md` + `docs/v6/diagrams/facts/reactor.json`

Quick lookup for module ordering, dependency layers, and `-pl` targeting. For full diagrams and sequence diagrams per module, see the primary document at `docs/build-sequences.md`.

---

## 19-Module Canonical Order

| # | Module | Layer | Direct YAWL Dependencies |
|---|--------|-------|--------------------------|
| 1 | yawl-utilities | 0 | — |
| 2 | yawl-security | 0 | — |
| 3 | yawl-graalpy | 0 | — |
| 4 | yawl-graaljs | 0 | — |
| 5 | yawl-elements | 1 | utilities |
| 6 | yawl-ggen | 1 | graalpy |
| 7 | yawl-graalwasm | 1 | graaljs |
| 8 | yawl-engine | 2 | elements |
| 9 | yawl-stateless | 3 | utilities, elements, engine |
| 10 | yawl-authentication | 4 | engine |
| 11 | yawl-scheduling | 4 | engine |
| 12 | yawl-monitoring | 4 | engine, stateless |
| 13 | yawl-worklet | 4 | stateless |
| 14 | yawl-control-panel | 4 | engine |
| 15 | yawl-integration | 4 | engine, stateless, ggen |
| 16 | yawl-webapps | 4* | — (aggregator) |
| 17 | yawl-pi | 5 | engine, integration, elements |
| 18 | yawl-resourcing | 5 | engine, stateless, integration |
| 19 | yawl-mcp-a2a-app | 6 | integration, pi, engine, stateless, elements, utilities, ggen |

---

## Parallel Build Groups

Modules in the same layer can compile concurrently.

| Layer | Modules | Gate |
|-------|---------|------|
| 0 | utilities, security, graalpy, graaljs | None — build first |
| 1 | elements, ggen, graalwasm | Wait for layer 0 |
| 2 | engine | Wait for elements |
| 3 | stateless | Wait for engine, utilities, elements |
| 4 | authentication, scheduling, monitoring, worklet, control-panel, integration, webapps | Wait for stateless (and ggen for integration) |
| 5 | pi, resourcing | Wait for integration |
| 6 | mcp-a2a-app | Wait for pi and resourcing |

---

## Critical Path

The longest dependency chain (7 hops):

```
utilities → elements → engine → stateless → integration → pi → mcp-a2a-app
```

Any change to a module on this path affects all downstream modules.

---

## Minimal `-pl` Lists for Targeted Builds

| Target | `mvn -pl` list | Module count |
|--------|---------------|-------------|
| yawl-utilities | `yawl-utilities` | 1 |
| yawl-elements | `yawl-utilities,yawl-elements` | 2 |
| yawl-graalwasm | `yawl-graaljs,yawl-graalwasm` | 2 |
| yawl-ggen | `yawl-graalpy,yawl-ggen` | 2 |
| yawl-engine | `yawl-utilities,yawl-elements,yawl-engine` | 3 |
| yawl-stateless | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless` | 4 |
| yawl-authentication | `yawl-utilities,yawl-elements,yawl-engine,yawl-authentication` | 4 |
| yawl-scheduling | `yawl-utilities,yawl-elements,yawl-engine,yawl-scheduling` | 4 |
| yawl-control-panel | `yawl-utilities,yawl-elements,yawl-engine,yawl-control-panel` | 4 |
| yawl-worklet | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-worklet` | 5 |
| yawl-monitoring | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-monitoring` | 5 |
| yawl-integration | `yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration` | 7 |
| yawl-pi | ...integration list + `yawl-pi` | 8 |
| yawl-resourcing | ...integration list + `yawl-resourcing` | 8 |
| yawl-mcp-a2a-app | all 19 | 19 |

Alternatively, use `mvn -pl <target> -am` and let Maven resolve the upstream chain automatically.

---

## dx.sh ALL_MODULES Array (corrected)

```bash
ALL_MODULES=(
    yawl-utilities yawl-security yawl-graalpy yawl-graaljs
    yawl-elements yawl-ggen yawl-graalwasm
    yawl-engine
    yawl-stateless
    yawl-authentication yawl-scheduling yawl-monitoring
    yawl-worklet yawl-control-panel yawl-integration yawl-webapps
    yawl-pi yawl-resourcing
    yawl-mcp-a2a-app
)
```

---

## Source Strategies

| Strategy | Modules |
|----------|---------|
| `package_scoped` (shared `../src`) | graaljs, graalwasm, graalpy, worklet, authentication, scheduling, security, monitoring, control-panel |
| `standard` (`src/main/java`) | utilities, elements, engine, stateless, resourcing, integration, ggen, pi, mcp-a2a-app |
| `aggregator` (no sources) | webapps |

---

## FM7 Poka-Yoke Artifacts

- `docs/v6/diagrams/facts/reactor.json` — machine-readable reactor order
- `docs/v6/diagrams/reactor-map.mmd` — Mermaid layer DAG
- `docs/v6/diagrams/module-dependencies.mmd` — Mermaid dependency arrows

---

## See Also

- [Full Build Sequences Document](../build-sequences.md) — with per-module sequence diagrams
- [dx.sh CLI Reference](dx-sh.md) — all flags
- [FMEA Risk Table](fmea-risk-table.md) — FM7 reactor order violation
