# YAWL (Yet Another Workflow Language)

![Version](https://img.shields.io/badge/version-6.0.0--Alpha-orange)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/license-LGPL--3.0-green)
![Status](https://img.shields.io/badge/status-Alpha-yellow)

**Latest Release:** v6.0.0-Alpha (February 16, 2026)
**Contributors:** Sean Chatman & Claude Code Agent Team
**Status:** Alpha - Community Preview & Testing

> **⚠️ Alpha Release Notice:** This is an alpha release with modernized library dependencies and enhanced quality frameworks. Intended for community review and testing. See [CHANGELOG.md](CHANGELOG.md) for details.

[YAWL](https://yawlfoundation.github.io) is a BPM/Workflow system, based on a concise and powerful modelling language, that handles complex data transformations, and full integration with organizational resources and external Web Services. 

### Major Features
YAWL offers these distinctive features:

* the most powerful process specification language for capturing control-flow dependencies and resourcing requirements.
* native data handling using XML Schema, XPath and XQuery.
* a formal foundation that makes its specifications unambiguous and allows automated verification.
* a service-oriented architecture that provides an environment that can easily be tuned to specific needs.
* YAWL has been developed independent from any commercial interests. It simply aims to be the most powerful language for process specification.
* For its expressiveness, YAWL offers relatively few constructs (compare this e.g. to BPMN!).
* YAWL offers unique support for exception handling, both those that were and those that were not anticipated at design time.
* YAWL offers unique support for dynamic workflow through the Worklets approach. Workflows can thus evolve over time to meet new and changing requirements.
* YAWL aims to be straightforward to deploy. It offers a number of automatic installers and an intuitive graphical design environment.
* YAWL's architecture is Service-oriented and hence one can replace existing components with one's own or extend the environment with newly developed components.
* The YAWL environments supports the automated generation of forms. This is particularly useful for rapid prototyping purposes.
* Tasks in YAWL can be mapped to human participants, Web Services, external applications or to Java classes.
* Through the C-YAWL approach a theory has been developed for the configuration of YAWL models. For more information on process configuration visit [www.processconfiguration.com]
* Simulation support is offered through a link with the [ProM](https://www.processmining.org) environment. Through this environment it is also possible to conduct post-execution analysis of YAWL processes (e.g. in order to identify bottlenecks).

### Other Features
* new: completely rewritten Process Editor
* new: Auto Update + Install/Uninstall of selected components
* delayed case starting
* support for passing files as data
* support for non-human resources
* support for interprocess communication
* calendar service and scheduling capabilities
* task documentation facility
* revised logging format and exporting to OpenXES
* integration with external applications
* custom forms
* sophisticated verification support
* Web service communication
* Highly configurable and extensible

## Performance Testing (NEW - v5.2)

**Complete performance testing framework with baselines, load testing, and capacity planning.**

### Quick Start
```bash
# Run full performance suite
./scripts/run-performance-tests.sh --full

# Run baseline measurements
./scripts/run-performance-tests.sh --baseline-only

# Quick smoke test
./scripts/run-performance-tests.sh --quick
```

### Documentation
- **[Performance Testing Summary](PERFORMANCE_TESTING_SUMMARY.md)** - Executive overview
- **[Performance Baselines](docs/performance/PERFORMANCE_BASELINES.md)** - Targets and results
- **[Testing Guide](docs/performance/PERFORMANCE_TESTING_GUIDE.md)** - How-to procedures
- **[Capacity Planning](docs/performance/CAPACITY_PLANNING.md)** - Sizing and scaling
- **[Full Delivery Report](PERFORMANCE_BASELINE_DELIVERY.md)** - Complete details

### Performance Targets
| Metric | Target | Status |
|--------|--------|--------|
| Case Launch (p95) | < 500ms | ✓ PASS |
| Work Item Completion (p95) | < 200ms | ✓ PASS |
| Concurrent Throughput | > 100/sec | ✓ PASS |
| Memory (1000 cases) | < 512MB | ✓ PASS |

### Test Coverage
- **5 Baseline Measurements**: Latency, throughput, memory, startup
- **3 Load Test Scenarios**: Sustained, burst, ramp-up
- **3 Scalability Tests**: Case scaling, memory efficiency, load recovery

---

## Maven Build — Root POM (`pom.xml`)

**Artifact:** `org.yawlfoundation:yawl-parent:6.0.0-Alpha` | `packaging: pom`

The root POM is the aggregator and Bill of Materials (BOM) for the entire multi-module build.
All dependency versions and plugin versions are centralised here; child modules never
redeclare version numbers.

### Module Build Order

| # | Module | Artifact |
|---|--------|----------|
| 1 | `yawl-utilities` | common utils, schema, unmarshal |
| 2 | `yawl-elements` | Petri net model (YNet, YTask, YSpec) |
| 3 | `yawl-authentication` | session, JWT, CSRF |
| 4 | `yawl-engine` | stateful engine + persistence |
| 5 | `yawl-stateless` | event-driven engine, no DB |
| 6 | `yawl-resourcing` | resource allocators, work queues |
| 7 | `yawl-worklet` | dynamic worklet selection, RDR exceptions |
| 8 | `yawl-scheduling` | timers, calendar |
| 9 | `yawl-security` | PKI / digital signatures |
| 10 | `yawl-integration` | MCP + A2A connectors |
| 11 | `yawl-monitoring` | OpenTelemetry, Prometheus |
| 12 | `yawl-webapps` | WAR aggregator |
| 13 | `yawl-control-panel` | Swing desktop UI |

### Build Profiles

| Profile | Trigger | Extra Tooling |
|---------|---------|---------------|
| `java21` | **default** | compiler target 21, JaCoCo skipped |
| `java24` | `-P java24` | forward-compat testing on JDK 24 |
| `ci` | `-P ci` | JaCoCo + SpotBugs |
| `prod` | `-P prod` | JaCoCo + SpotBugs + OWASP CVE (fails ≥ CVSS 7) |
| `security-audit` | `-P security-audit` | OWASP CVE report only, never fails |
| `analysis` | `-P analysis` | JaCoCo + SpotBugs + Checkstyle + PMD |
| `sonar` | `-P sonar` | SonarQube push (`SONAR_TOKEN`, `SONAR_HOST_URL`) |
| `online` | `-P online` | upstream BOMs: Spring Boot, OTel, Resilience4j, TestContainers, Jackson |

### Enforcer Rules

Enforced at `validate` phase — build fails if:
- Maven < 3.9 or Java < 21
- Duplicate dependency declarations exist in any POM
- Any plugin lacks an explicit version

Each module has its own README.md with detailed documentation. See the module directories.

### Test Coverage Summary (All Modules)

| Module | Test Classes | Active Tests | Coverage Status |
|--------|-------------|-------------|-----------------|
| `yawl-utilities` | 4 | ~8 | Partial — util/schema packages untested at module scope |
| `yawl-elements` | 20 | ~115 | Good — all element types and schema validation covered |
| `yawl-authentication` | 2 | 23 | Partial — CSRF filter and session persistence not tested |
| `yawl-engine` | 25 | ~157 | Good — core execution paths, OR-join, persistence, virtual threads |
| `yawl-stateless` | 2 | 20 | Minimal — JSON round-trip and suspend/resume not tested |
| `yawl-resourcing` | 1 | 15 | Minimal — allocator strategies and Hibernate CRUD not tested |
| `yawl-worklet` | 0 | 0 | **No coverage** |
| `yawl-scheduling` | 0 | 0 | **No coverage** |
| `yawl-security` | 0 | 0 | **No coverage** |
| `yawl-integration` | 4 active | 28 | Partial — MCP tools and A2A excluded; dedup untested |
| `yawl-monitoring` | 1 | 21 | Partial — exporter config and log format not asserted |
| `yawl-webapps` | N/A | 0 | Aggregator — no source |
| `yawl-engine-webapp` | N/A | 0 | Packaging-only — covered by integration module |
| `yawl-control-panel` | 0 | 0 | **No coverage** |
| **Total** | **~59** | **~387** | |

Run the full test suite: `mvn -T 1.5C clean test`

### Roadmap

- **Fill zero-coverage modules** — `yawl-worklet`, `yawl-scheduling`, `yawl-security`, `yawl-control-panel` all have no tests; each module README contains a specific testing roadmap
- **JaCoCo gate in CI** — enable the `ci` profile on every pull request; enforce 65% line / 55% branch coverage targets defined in the `analysis` profile
- **Testcontainers integration suite** — add a `yawl-it` integration test module that starts the full engine WAR in a Testcontainers-managed Tomcat and runs end-to-end HTTP tests
- **Java 25 preview adoption** — track Java 25 LTS (expected September 2025); adopt value types, module system enhancements, and any new virtual thread APIs
- **A2A SDK enablement** — re-enable the commented-out A2A dependencies and tests once `io.anthropic:a2a-java-sdk-*` is published to Maven Central
- **SonarQube quality gate** — configure the `sonar` profile in CI to fail PRs that reduce the overall code quality rating below A
- **Dependency update automation** — enable Renovate Bot or Dependabot to open PRs for version bumps guided by the parent BOM; require green CI before merge

