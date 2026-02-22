# YAWL Observatory — docs/v6/latest/

Generated: 2026-02-22T08:16:09Z | Branch: claude/run-validation-tests-osh3r | Commit: 593a8fc | Status: ✅ GREEN

## Facts (11 files)

| File | Question | Size |
|------|----------|------|
| [facts/modules.json](facts/modules.json) | What modules exist? | 0.1KB |
| [facts/reactor.json](facts/reactor.json) | Build order? | 0.1KB |
| [facts/shared-src.json](facts/shared-src.json) | Who owns which source files? | 0.4KB |
| [facts/dual-family.json](facts/dual-family.json) | Stateful ↔ stateless mapping? | 0.8KB |
| [facts/duplicates.json](facts/duplicates.json) | Duplicate classes? | 0.2KB |
| [facts/tests.json](facts/tests.json) | Tests per module? | 0.5KB |
| [facts/integration.json](facts/integration.json) | MCP/A2A tools and skills? | 0.4KB |
| [facts/docker-testing.json](facts/docker-testing.json) | Docker testing enabled? | 0.9KB |
| [facts/coverage.json](facts/coverage.json) | Code coverage data? | 0.4KB |
| [facts/static-analysis.json](facts/static-analysis.json) | Overall code health? | 0.5KB |
| [facts/deps-conflicts.json](facts/deps-conflicts.json) | Dependency conflicts? | 0.3KB |

## Quick Reference

Run `bash scripts/observatory/observatory.sh` to refresh all facts.

### About the Observatory

The YAWL Observatory generates **facts** — machine-readable knowledge about the codebase.

- **Fast**: Parallel Rust implementation (~2-4s vs 51s bash).
- **Incremental**: Facts are cached and only regenerated when dependencies change.
- **Observable**: All outputs are git-friendly JSON + Markdown.

### Key Facts

| Fact | Used For |
|------|----------|
| **modules.json** | Module structure, ownership, dependencies |
| **reactor.json** | Build order, parallel safety, compilation phases |
| **shared-src.json** | File ownership, cross-module duplication |
| **dual-family.json** | Stateful ↔ stateless engine mapping |
| **gates.json** | Quality gates, test coverage, build checks |
| **integration.json** | MCP/A2A tools, autonomous agent endpoints |
| **deps-conflicts.json** | Dependency version conflicts, managed versions |
| **static-analysis.json** | SpotBugs, PMD, Checkstyle findings |

