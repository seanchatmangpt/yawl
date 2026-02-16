# YAWL (Yet Another Workflow Language)

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

