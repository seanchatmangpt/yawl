# YAWL v6.0.0 Developer Documentation

Welcome to the YAWL (Yet Another Workflow Language) v6.0.0 developer documentation. This comprehensive guide will help you set up your development environment, understand the build system, and contribute to the project.

---

## Documentation Structure

```
docs/
├── README.md                        # This file - Documentation hub
├── QUICK-START.md                   # 5-minute setup for new developers
├── BUILD.md                         # Maven build system guide
├── TESTING.md                       # Test framework and practices
├── CONTRIBUTING.md                  # Contribution workflow
├── TROUBLESHOOTING.md               # Problem solutions
├── CONFIGURATION_VERIFICATION.md    # System status report
│
├── v6/                              # YAWL v6 specific documentation
│   ├── latest/                      # Observatory outputs (facts, diagrams)
│   │   ├── INDEX.md                 # Observatory manifest
│   │   ├── facts/                   # JSON facts (machine-readable)
│   │   ├── diagrams/                # Mermaid diagrams (visual)
│   │   ├── receipts/                # Cryptographic verification
│   │   └── performance/             # Performance metrics
│   ├── OBSERVATORY-GUIDE.md         # Comprehensive observatory usage
│   ├── THESIS-YAWL-V6-*.md          # Architecture analysis
│   └── DEFINITION-OF-DONE.md        # Quality checklist
│
├── diataxis/                        # Diataxis framework navigation
│   └── INDEX.md                     # Four-quadrant doc organization
│
├── explanation/                     # Conceptual explanations
│   ├── interface-architecture.md
│   ├── petri-net-foundations.md
│   ├── dual-engine-architecture.md
│   └── ...
│
├── patterns/                        # Workflow patterns
│   └── README.md                    # 36+ YAWL patterns
│
└── archive/                         # Historical validation reports
    └── 2026-02-16/
```

---

## Quick Navigation

### By Task

**I want to...**

| Task | Start Here |
|------|------------|
| Set up development environment | [QUICK-START.md](QUICK-START.md) |
| Understand the build system | [BUILD.md](BUILD.md) |
| Write and run tests | [TESTING.md](TESTING.md) |
| Contribute code | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Debug a problem | [TROUBLESHOOTING.md](TROUBLESHOOTING.md) |
| Understand codebase topology | [v6/latest/INDEX.md](v6/latest/INDEX.md) |
| Browse docs by type | [diataxis/INDEX.md](diataxis/INDEX.md) |
| Learn about observatory | [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) |

### By Role

| Role | Key Documents |
|------|---------------|
| **New Developer** | [QUICK-START.md](QUICK-START.md), [CONTRIBUTING.md](CONTRIBUTING.md), [TESTING.md](TESTING.md) |
| **Project Lead** | [BUILD.md](BUILD.md), [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md), [CONFIGURATION_VERIFICATION.md](CONFIGURATION_VERIFICATION.md) |
| **QA / Test Engineer** | [TESTING.md](TESTING.md), [TROUBLESHOOTING.md](TROUBLESHOOTING.md) |
| **DevOps / CI Engineer** | [BUILD.md](BUILD.md), [v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md) |
| **Architect** | [explanation/](explanation/), [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) |

---

## Documentation Map

### Getting Started

- **[QUICK-START.md](QUICK-START.md)** - 5-minute setup for new developers
  - Prerequisites and initial setup
  - IDE configuration (IntelliJ, Eclipse, VS Code)
  - First commit workflow
  - Common commands

### Development Guides

- **[BUILD.md](BUILD.md)** - Maven build system explained
  - Architecture and module structure
  - Configuration files and properties
  - Dependency management
  - Build profiles and plugins
  - Performance optimization
  - CI/CD integration

- **[TESTING.md](TESTING.md)** - How to run and write tests
  - JUnit 5 framework and best practices
  - Test organization and structure
  - Running tests in various configurations
  - Code coverage measurement
  - Benchmark testing with JMH

- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Developer workflow and standards
  - Code of conduct and etiquette
  - HYPER_STANDARDS compliance (critical!)
  - Code style and conventions
  - JavaDoc standards
  - Commit message format
  - Pull request process

### Problem Solving

- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
  - Build issues and solutions
  - Test failures and debugging
  - Compilation errors
  - IDE configuration problems
  - Performance optimization
  - Git workflow issues

### Configuration Verification

- **[CONFIGURATION_VERIFICATION.md](CONFIGURATION_VERIFICATION.md)** - System status report
  - Environment verification
  - Maven configuration details
  - Dependency health check
  - Known issues and blockers
  - Build readiness checklist

### v6 Observatory

- **[v6/latest/INDEX.md](v6/latest/INDEX.md)** - Observatory manifest
  - Facts: JSON files with codebase topology
  - Diagrams: Mermaid visual maps
  - Receipts: Cryptographic verification
  - Performance: Timing metrics

- **[v6/OBSERVATORY-GUIDE.md](v6/OBSERVATORY-GUIDE.md)** - Comprehensive usage guide
  - How to run the observatory
  - Output format documentation
  - CI/CD integration
  - Troubleshooting

### Diataxis Framework

- **[diataxis/INDEX.md](diataxis/INDEX.md)** - Four-quadrant doc organization
  - **Tutorials**: Learning-oriented (step-by-step lessons)
  - **How-to Guides**: Problem-oriented (task completion)
  - **Reference**: Information-oriented (API, config lookup)
  - **Explanation**: Understanding-oriented (concepts, rationale)

---

## Key Concepts

### HYPER_STANDARDS Compliance (CRITICAL!)

All code must follow HYPER_STANDARDS - see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

**Summary**:
- No TODO/FIXME/XXX/HACK comments
- No mock/stub/fake implementations
- Implement real functionality OR throw UnsupportedOperationException
- Write meaningful tests
- Update documentation

### Module Architecture

```
YAWL v6.0.0 (15 modules)
├── yawl-utilities (foundation)
├── yawl-elements (core definitions)
├── yawl-engine (stateful execution)
├── yawl-stateless (cloud deployment)
├── yawl-integration (MCP/A2A servers)
└── 10 other specialized modules
```

### Build System

- **Build Tool**: Maven 3.9.11
- **Java Version**: 25 (with preview features)
- **Default Database**: H2 (testing)
- **Test Framework**: JUnit 5
- **Build Time Target**: <45s compile, <5m full build

### Observatory Principle

**Read facts, do not explore.**

| Operation | Token Cost | When to Use |
|-----------|------------|-------------|
| Read 1 fact file | ~50 tokens | Always (first resort) |
| Read v6/latest/INDEX.md | ~100 tokens | Start of session |
| Run observatory | ~17s wall clock | Facts are stale |
| Grep codebase | ~500-5000 tokens | Fact file does not cover your question |

**Compression ratio: 100x.** 1 fact file (50 tokens) replaces grepping (5000 tokens).

---

## File Structure

```
yawl/
├── docs/                           # Documentation (this directory)
│   ├── README.md                   # Documentation hub
│   ├── QUICK-START.md              # 5-minute setup
│   ├── BUILD.md                    # Maven build system
│   ├── TESTING.md                  # Test framework
│   ├── CONTRIBUTING.md             # Contribution guide
│   ├── TROUBLESHOOTING.md          # Problem solutions
│   ├── v6/                         # YAWL v6 specific docs
│   │   └── latest/                 # Observatory outputs
│   ├── diataxis/                   # Diataxis framework
│   ├── explanation/                # Conceptual explanations
│   └── patterns/                   # Workflow patterns
├── pom.xml                         # Root Maven config
├── .mvn/                           # Maven settings
│   ├── maven.config                # Build properties
│   ├── jvm.config                  # JVM settings
│   └── extensions.xml              # Maven extensions
├── yawl-*/                         # 15 module directories
│   ├── src/                        # Production code
│   ├── test/                       # Test code
│   └── pom.xml                     # Module config
├── schema/                         # XML schema definitions
├── test/                           # Integration tests
└── .github/workflows/              # CI/CD pipelines
```

---

## System Requirements

### Minimum

- Java 21+
- Maven 3.9+
- 4GB RAM
- 2GB disk space
- Git

### Recommended

- Java 25 (OpenJDK)
- Maven 3.9.11
- 8GB+ RAM
- 10GB disk space
- IntelliJ IDEA or VS Code

---

## First Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/yawlfoundation/yawl.git
   cd yawl
   ```

2. **Follow QUICK-START.md**
   - Set up development environment
   - Verify installation
   - Configure IDE

3. **Read CONTRIBUTING.md**
   - Understand HYPER_STANDARDS
   - Learn commit workflow
   - Review code style

4. **Make your first change**
   - Create feature branch
   - Write test first (TDD)
   - Implement functionality
   - Run full test suite
   - Submit PR

---

## Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| Compilation | <45s | 45-60s |
| Unit Tests | <2m | 2-3m |
| Full Build | <5m | 5-10m |
| Code Coverage | 80%+ | Varies |

*Actual times depend on system specs and network availability*

---

## Getting Help

### Documentation

- Check the relevant guide (see above)
- Browse [diataxis/INDEX.md](diataxis/INDEX.md) for doc organization
- Check [v6/latest/INDEX.md](v6/latest/INDEX.md) for codebase facts
- Read examples in `test/` directory
- Review existing code patterns

### Troubleshooting

1. Search [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. Check build output carefully
3. Try `mvn clean` and rebuild
4. Increase logging: `mvn -X clean compile`

### Community

- GitHub Issues: Report bugs, request features
- Stack Overflow: Use `yawl` tag
- YAWL Foundation: Visit yawlfoundation.org

---

## Important Notes

### Network Requirements

First build requires network access to download ~200MB of dependencies. Subsequent builds use cached dependencies and can run offline.

### Build Cache

Build cache extension is disabled for offline compatibility. Enable in `.mvn/extensions.xml` if needed in online environments.

### Testing

All commits must pass:
```bash
mvn clean compile test
```

Test coverage target: 80%+ for new code.

---

## Document Versions

| Document | Version | Updated |
|----------|---------|---------|
| README.md | 2.0 | 2026-02-18 |
| QUICK-START.md | 1.0 | 2026-02-17 |
| BUILD.md | 1.0 | 2026-02-17 |
| TESTING.md | 1.0 | 2026-02-17 |
| CONTRIBUTING.md | 1.0 | 2026-02-17 |
| TROUBLESHOOTING.md | 1.0 | 2026-02-17 |
| CONFIGURATION_VERIFICATION.md | 1.0 | 2026-02-17 |
| v6/latest/INDEX.md | 1.0 | 2026-02-18 |
| diataxis/INDEX.md | 1.0 | 2026-02-18 |

---

## License

YAWL is an open-source project. See LICENSE file for details.

## Contributing

To contribute to YAWL:

1. Read [QUICK-START.md](QUICK-START.md) for setup
2. Read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
3. Follow HYPER_STANDARDS in [CONTRIBUTING.md](CONTRIBUTING.md)
4. Submit a pull request

Thank you for contributing to YAWL!

---

**Last Updated**: 2026-02-18
**Documentation Version**: 6.0.0-Alpha
**Build Version**: 6.0.0-Alpha
