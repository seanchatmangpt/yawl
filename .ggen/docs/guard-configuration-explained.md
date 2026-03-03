# Guard Configuration Explained

## Overview

The guard configuration at `.ggen/config/guard-config.toml` defines exclusion patterns and rules to ensure that guard validation only scans PRODUCTION code, not test fixtures, test code, or documentation examples.

## Key Sections

### 1. Exclusions

Comprehensive list of directories and patterns to exclude from guard validation:

- **Test fixtures**: `test/fixtures/**`, `tests/fixtures/**` - contain intentional violations
- **Test code**: `**/src/test/**`, `**/test/**`, `**/*Test*.java`, `**/*Spec*.java` - may use mocks
- **Documentation**: `docs/templates/**`, `docs/examples/**`, `docs/**/*.java` - contain TODOs and examples
- **Demo code**: `**/demo/**`, `**/examples/**` - unfinished or placeholder code
- **Generated/third-party**: `**/target/**`, `**/build/**`, `**/node_modules/**` - auto-generated
- **IDE directories**: `**/.idea/**`, `**/.vscode/**` - IDE-generated files
- **Temporary**: `**/tmp/**`, `**/temp/**` - temporary files

### 2. Inclusion Patterns

Specific patterns that should be scanned (complements exclusions):

- Core YAWL packages:
  - `**/org/yawlfoundation/yawl/elements/**`
  - `**/org/yawlfoundation/yawl/engine/**`
  - `**/org/yawlfoundation/yawl/stateless/**`
  - `**/org/yawlfoundation/yawl/resourcing/**`
  - `**/org/yawlfoundation/yawl/schema/**`

### 3. Skip Patterns by Directory

Custom rules for different excluded directories:

- **test/fixtures**: Skip all guard patterns (H_TODO, H_MOCK, etc.)
- **docs/templates**: Skip H_TODO (intentional examples)
- **src/test**: Skip all patterns (test code can use mocks)

### 4. Custom Patterns

Enhanced regex patterns for better detection:

- `h_todo_impl_needed`: "real implementation needed" comments
- `h_placeholder_code`: "@placeholder" markers
- `h_intentional_stub`: "intentional stub" comments
- `h_demo_unfinished`: "demo unfinished" markers

### 5. Test and Documentation Patterns

Special patterns that are acceptable in non-production contexts:

- **Test code patterns**: Mockito, PowerMockito, @Mock, etc.
- **Doc code patterns**: "implement", "placeholder", "demo" markers
- **Acceptable logging**: Test and demo related logging

### 6. Performance Optimizations

- **File caching**: Caches parsed AST trees to avoid re-parsing
- **Batch processing**: Processes files in batches of 50
- **Memory limits**: Clears cache when memory exceeds 512MB
- **Large directory skipping**: Skips directories >100MB

## Guard Pattern Coverage

### H_TODO (Deferred work markers)
- Excludes: Documentation, test fixtures, template comments
- Detects: Real TODO/FIXME in production code

### H_MOCK (Mock implementations)
- Excludes: Test code, examples with mock prefixes
- Detects: Mock classes/methods in production code

### H_STUB (Empty returns)
- Excludes: Test fixtures, documentation examples
- Detects: Stub returns in production methods

### H_EMPTY (No-op methods)
- Excludes: Test utilities, empty interface implementations
- Detects: Empty methods that should throw

### H_FALLBACK (Silent catch-and-fake)
- Excludes: Test error handling, example recovery code
- Detects: Silent fallback in production error handling

### H_LIE (Code ≠ documentation)
- Applied to: Production code with proper documentation
- Detects: Mismatch between Javadoc and implementation

### H_SILENT (Log instead of throw)
- Excludes: Documentation examples with "not implemented" logs
- Detects: Silent logging instead of throwing exceptions

## Usage

### Command Line
```bash
# Run guards with current configuration
ggen validate --phase guards --emit /path/to/generated/code

# Run with verbose output
ggen validate --phase guards --emit /path/to/code --verbose

# Custom configuration file
ggen validate --phase guards --config /path/to/custom-config.toml
```

### Configuration Validation
```bash
# Validate configuration syntax
ggen validate-config --config .ggen/config/guard-config.toml

# Test configuration on sample files
ggen validate --phase guards --test-fixtures
```

## Best Practices

1. **Test fixtures should contain intentional violations** for testing guard detection
2. **Documentation templates should use TODO comments** as examples
3. **Production code must either implement real logic or throw UnsupportedOperationException**
4. **Test code can use mocks and stubs** - they're excluded from validation
5. **Example code in docs can have placeholders** - they're excluded from validation

## Troubleshooting

### Common Issues

1. **Guards flagging test code**: Check exclusions match your test file patterns
2. **False positives in docs**: Verify documentation templates are properly excluded
3. **Performance issues**: Enable file caching and adjust batch sizes
4. **Missing violations**: Check custom patterns match your code style

### Debug Commands
```bash
# Check which files would be scanned
ggen validate --phase guards --dry-run --list-files

# View pattern matching
ggen validate --phase guards --debug-patterns

# Test specific pattern
ggen validate --phase guards --test-pattern H_TODO
```

## Configuration Reference

Full configuration options and defaults are documented in the `guard-config.toml` file.