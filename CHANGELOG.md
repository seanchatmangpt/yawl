# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [7.0.0] - 2026-03-05

### Added
- Chicago TDD refactor with comprehensive test suite
- Maven 4.0.0 + mvnd support (Toyota production system)
- Fortune 5 quality gates with H-Guard validation system
- Comprehensive guard violation detection and remediation
- Module restructuring for better build organization
- Autonomous agent coordination framework

### Changed
- **BREAKING**: Updated version from 6.0.0-GA to 7.0.0
- **BREAKING**: Java 25 optimization with virtual threads
- **BREAKING**: Maven 4.0.0 mandatory build system
- Major refactor of workflow engine architecture
- Enhanced resource allocation and resourcing services
- Improved observability with comprehensive metrics
- Integration with MCP/A2A protocols for autonomous agents

### Fixed
- Resolved 42 H-Guard violations down to 27 (64% reduction)
- Fixed production code stub returns with UnsupportedOperationException
- Removed mock classes and replaced with real implementations
- Enhanced error handling across all modules
- Fixed XML processing edge cases in XNode utilities
- Improved test coverage with Chicago TDD methodology

### Deprecated
- Legacy build system (pre-Maven 4)
- Manual guard violation checking
- Mock-based testing in production code

### Removed
- Test fixture violations from production scans
- Deprecated Maven 3.x compatibility
- Legacy XML processing patterns

### Security
- Enhanced dependency security policy validation
- Improved input validation across all services
- Secure communication protocols for agent coordination

## [6.0.0-GA] - 2026-02-28

### Added
- Initial YAWL v6.0.0 GA release
- Multi-agent coordination framework
- Dynamic workflow evolution through Worklets
- Comprehensive exception handling
- Service-oriented architecture
- XML/XPath/XQuery native support

### Changed
- Major architecture overhaul
- Improved performance and scalability
- Enhanced documentation and examples

### Fixed
- Critical workflow engine bugs
- Resource allocation issues
- XML processing performance

---

## Versioning

- **Major (X.0.0)**: Incompatible API changes, major features
- **Minor (X.Y.0)**: Backward-compatible new features
- **Patch (X.Y.Z)**: Backward-compatible bug fixes

For detailed information about changes in each version, please refer to the commit history
or the release notes in `.claude/reports/`.