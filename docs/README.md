# YAWL v6.0.0 Developer Documentation

Welcome to the YAWL (Yet Another Workflow Language) v6.0.0 developer documentation. This comprehensive guide will help you set up your development environment, understand the build system, and contribute to the project.

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

## Quick Navigation

### By Task

**I want to...**

- **Set up development environment**
  - Start with [QUICK-START.md](QUICK-START.md)
  - Then read [BUILD.md](BUILD.md) for details

- **Write and run tests**
  - Read [TESTING.md](TESTING.md)
  - Examples in `test/` directory

- **Make code changes**
  - Follow [CONTRIBUTING.md](CONTRIBUTING.md)
  - Ensure HYPER_STANDARDS compliance
  - Write tests for your changes

- **Debug a problem**
  - Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
  - Review error messages carefully
  - Check [BUILD.md](BUILD.md) for configuration

- **Understand the build**
  - Read [BUILD.md](BUILD.md) sections:
    - Architecture Overview
    - Build Lifecycle
    - Dependency Management

- **Contribute a fix**
  - [CONTRIBUTING.md](CONTRIBUTING.md) - Full workflow
  - [TESTING.md](TESTING.md) - Test requirements
  - [BUILD.md](BUILD.md) - Build verification

### By Role

**Project Lead / Maintainer**
- [BUILD.md](BUILD.md) - Full architecture understanding
- [CONTRIBUTING.md](CONTRIBUTING.md) - Review guidelines
- [CONFIGURATION_VERIFICATION.md](CONFIGURATION_VERIFICATION.md) - System health

**New Developer**
- [QUICK-START.md](QUICK-START.md) - Start here!
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution workflow
- [TESTING.md](TESTING.md) - Test-driven development

**QA / Test Engineer**
- [TESTING.md](TESTING.md) - Test framework and execution
- [BUILD.md](BUILD.md) - Build system
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Issue diagnosis

**DevOps / CI Engineer**
- [BUILD.md](BUILD.md) - CI/CD integration section
- [CONFIGURATION_VERIFICATION.md](CONFIGURATION_VERIFICATION.md) - System verification
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Performance tuning

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
- **Java Version**: 21.0.10 (with preview features)
- **Default Database**: H2 (testing)
- **Test Framework**: JUnit 5
- **Build Time Target**: <45s compile, <5m full build

## File Structure

```
yawl/
├── docs/
│   ├── QUICK-START.md              # 5-minute setup
│   ├── BUILD.md                    # Maven build system
│   ├── TESTING.md                  # Test framework
│   ├── CONTRIBUTING.md             # Contribution guide
│   ├── TROUBLESHOOTING.md          # Problem solutions
│   └── README.md                   # This file
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

## System Requirements

### Minimum
- Java 21+
- Maven 3.9+
- 4GB RAM
- 2GB disk space
- Git

### Recommended
- Java 21.0.10 (OpenJDK)
- Maven 3.9.11
- 8GB+ RAM
- 10GB disk space
- IntelliJ IDEA or VS Code

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

## Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| Compilation | <45s | 45-60s |
| Unit Tests | <2m | 2-3m |
| Full Build | <5m | 5-10m |
| Code Coverage | 80%+ | Varies |

*Actual times depend on system specs and network availability*

## Getting Help

### Documentation
- Check the relevant guide (see above)
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

## Document Versions

| Document | Version | Updated |
|----------|---------|---------|
| QUICK-START.md | 1.0 | 2026-02-17 |
| BUILD.md | 1.0 | 2026-02-17 |
| TESTING.md | 1.0 | 2026-02-17 |
| CONTRIBUTING.md | 1.0 | 2026-02-17 |
| TROUBLESHOOTING.md | 1.0 | 2026-02-17 |
| CONFIGURATION_VERIFICATION.md | 1.0 | 2026-02-17 |

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

**Last Updated**: 2026-02-17  
**Documentation Version**: 6.0.0-Alpha  
**Build Version**: 6.0.0-Alpha  
