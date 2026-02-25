# Pattern Demo Runner - Standalone Shell Script

This script provides a standalone way to run the PatternDemoRunner class using Maven's exec:java plugin, bypassing Spring Boot JAR classloading issues.

## Features

- ✅ Bypasses Spring Boot classloading issues
- ✅ Uses Maven exec:java for clean classpath isolation
- ✅ Supports all PatternDemoRunner options
- ✅ Comprehensive error handling and validation
- ✅ Colorized output for better readability
- ✅ Verbose mode for debugging
- ✅ Fail-fast option for CI/CD pipelines

## Usage

### Basic Commands

```bash
# Run all patterns
./run-pattern-demo.sh --all

# Run specific pattern
./run-pattern-demo.sh --pattern WCP-1

# Run all patterns in a category
./run-pattern-demo.sh --category BASIC

# Run with JSON output format
./run-pattern-demo.sh --all --format json

# Run with Markdown output format
./run-pattern-demo.sh --category ADVANCED --format markdown

# Run with verbose output
./run-pattern-demo.sh --pattern WCP-1 --verbose

# Run with fail-fast (stop on first error)
./run-pattern-demo.sh --all --fail-fast
```

### Options

| Option | Short | Description | Example |
|--------|-------|-------------|---------|
| `--pattern` | `-p` | Run specific pattern by ID | `--pattern WCP-1` |
| `--category` | `-c` | Run all patterns in category | `--category BASIC` |
| `--all` | `-a` | Run all available patterns | `--all` |
| `--format` | `-f` | Output format: console|json|markdown | `--format json` |
| `--verbose` | `-v` | Enable verbose output | `--verbose` |
| `--fail-fast` | `-x` | Stop on first failure | `--fail-fast` |
| `--help` | `-h` | Show help message | `--help` |

## Output Formats

### Console (Default)
Human-readable text output with colorization:
```text
Pattern Demo Runner
===================
Running all patterns...

Pattern: WCP-1 - Sequence
✓ Description: A sequence of tasks in order
✓ Expected: Task1 → Task2 → Task3 → Complete
✓ Status: PASSED

Pattern: WCP-2 - Parallel Split
✓ Description: Splitting into parallel tasks
✓ Expected: Split → TaskA & TaskB → Sync → Complete
✓ Status: PASSED

✓ All 12 patterns completed successfully in 2.345s
```

### JSON
Machine-readable JSON output:
```json
{
  "runner": "PatternDemoRunner",
  "version": "6.0.0-Beta",
  "timestamp": "2026-02-22T14:30:45Z",
  "patterns": [
    {
      "id": "WCP-1",
      "name": "Sequence",
      "description": "A sequence of tasks in order",
      "expected": "Task1 → Task2 → Task3 → Complete",
      "status": "PASSED",
      "durationMs": 123
    },
    {
      "id": "WCP-2",
      "name": "Parallel Split",
      "description": "Splitting into parallel tasks",
      "expected": "Split → TaskA & TaskB → Sync → Complete",
      "status": "PASSED",
      "durationMs": 456
    }
  ],
  "summary": {
    "total": 12,
    "passed": 12,
    "failed": 0,
    "durationMs": 2345
  }
}
```

### Markdown
GitHub-flavored Markdown format:
```markdown
# Pattern Demo Runner Results

Generated: 2026-02-22T14:30:45Z

## Summary
- Total Patterns: 12
- Passed: 12 ✅
- Failed: 0 ❌
- Duration: 2.345s

## Results

### WCP-1 - Sequence
- **Description**: A sequence of tasks in order
- **Expected**: Task1 → Task2 → Task3 → Complete
- **Status**: ✅ PASSED
- **Duration**: 123ms

### WCP-2 - Parallel Split
- **Description**: Splitting into parallel tasks
- **Expected**: Split → TaskA & TaskB → Sync → Complete
- **Status**: ✅ PASSED
- **Duration**: 456ms
```

## Pattern Categories

The demo runner supports the following pattern categories:

| Category | Description | Patterns Included |
|----------|-------------|------------------|
| `BASIC` | Fundamental workflow patterns | WCP-1 to WCP-10 |
| `ADVANCED` | Complex workflow patterns | WCP-11 to WCP-20 |
| `COMPLEX` | Expert-level patterns | WCP-21 to WCP-30 |
| `ALL` | All available patterns | WCP-1 to WCP-N |

## Pattern IDs

Individual patterns can be specified by their YAWL Control-Flow Pattern (WCP) ID:

- **WCP-1**: Sequence
- **WCP-2**: Parallel Split
- **WCP-3**: Synchronization
- **WCP-4**: Exclusive Choice
- **WCP-5**: Simple Merge
- **WCP-6**: Multi-Choice
- **WCP-7**: Multi-Merge
- **WCP-8**: Discriminator
- **WCP-9**: N-out-of-M
- **WCP-10**: Deferred Choice
- **WCP-11**: Implicit Termination
- **WCP-12**: Multiple Instances
- ... and more

## Prerequisites

1. **Maven** must be installed and in your PATH
2. **Java** (JDK 8+) must be installed
3. The project must be properly built (dependencies available)

## Error Handling

The script includes comprehensive error handling:

- **Missing Maven**: Shows error and exits
- **Invalid Options**: Validates arguments before execution
- **Missing Dependencies**: Maven will build if needed
- **Execution Failures**: Captures and reports Maven exit codes
- **Pattern Failures**: Reports individual pattern status

## Examples

### CI/CD Pipeline Integration

```bash
# Run all patterns in CI
./run-pattern-demo.sh --all --format json

# Run with fail-fast for quick feedback
./run-pattern-demo.sh --all --fail-fast

# Run specific regression test
./run-pattern-demo.sh --pattern WCP-1,WCP-2,WCP-3
```

### Development Workflow

```bash
# Quick check with verbose output
./run-pattern-demo.sh --all --verbose

# Test new pattern implementation
./run-pattern-demo.sh --pattern WCP-15

# Generate documentation
./run-pattern-demo.sh --all --format markdown > patterns.md
```

### Testing

Run the test script to validate the runner script:

```bash
./test-pattern-demo.sh
```

## Troubleshooting

### Maven Classpath Issues

If you encounter classpath errors:
1. Ensure Maven is properly installed
2. Run `mvn clean install` to refresh dependencies
3. Check that the main class exists in the source

### Pattern Not Found

If a pattern ID doesn't work:
1. Try `--all` to see available patterns
2. Check the pattern ID format (must be WCP-N)
3. Verify the pattern exists in the current version

### Slow Execution

For faster execution:
1. Use `--fail-fast` to stop early on failures
2. Run specific patterns instead of `--all`
3. Build the project first with `mvn clean compile`

## Integration with Build Tools

The script can be integrated into various build systems:

### Maven Plugin
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <mainClass>org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner</mainClass>
        <args>
            <arg>--all</arg>
            <arg>--format</arg>
            <arg>json</arg>
        </args>
    </configuration>
</plugin>
```

### Shell Alias
```bash
alias demo-pattern="./scripts/run-pattern-demo.sh --all --format console"
```

### Jenkins Pipeline
```groovy
pipeline {
    stages {
        stage('Run Pattern Demo') {
            steps {
                sh './scripts/run-pattern-demo.sh --all --format json'
            }
        }
    }
}
```

## Contributing

When adding new patterns or features:
1. Update the script with new pattern IDs
2. Add new categories if needed
3. Update documentation
4. Run the test script to verify changes

## License

This script is part of the YAWL project and follows the same license terms.