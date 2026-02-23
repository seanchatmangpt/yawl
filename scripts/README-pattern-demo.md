# YAWL Pattern Demo Runner Guide

This guide explains how to run Van Der Aalst workflow pattern demonstrations using the YAWL Pattern Demo Runner.

## Overview

The Pattern Demo Runner is a powerful tool for executing and analyzing YAWL workflow patterns. It can run individual patterns, pattern categories, or all 43+ YAWL workflow patterns with comprehensive reporting and analysis.

## Prerequisites

1. **Java 21+** - Required for running the demo
2. **Maven** - For building the project
3. **YAWL Project** - Compiled with JAR files available

## Quick Start

### Basic Usage

```bash
# Run the 5 basic patterns (WCP-1 through WCP-5)
./scripts/run-vdaalst-demo.sh

# Run a single pattern
./scripts/run-vdaalst-demo.sh --pattern WCP-1

# Run multiple patterns
./scripts/run-vdaalst-demo.sh --pattern WCP-1,WCP-2,WCP-3

# Run all patterns in a category
./scripts/run-vdaalst-demo.sh --category BASIC

# Run all patterns
./scripts/run-vdaalst-demo.sh --all
```

## Command Line Options

| Option | Description | Example |
|--------|-------------|---------|
| `--pattern PATTERNS` | Run specific pattern IDs | `--pattern WCP-1,WCP-2,WCP-10` |
| `--category CATEGORY` | Run patterns in category | `--category BASIC` |
| `--all` | Run all available patterns | `--all` |
| `--format FORMAT` | Output format (console, json, markdown, html) | `--format json` |
| `--timeout SECONDS` | Execution timeout per pattern | `--timeout 60` |
| `--output PATH` | Output file path | `--output report.md` |
| `--sequential` | Disable parallel execution | `--sequential` |
| `--token-report` | Include token savings analysis | `--token-report` |
| `--with-commentary` | Include Wil van der Aalst commentary | `--with-commentary` |

## Pattern Categories

The demo organizes patterns into logical categories:

### Basic Patterns (WCP-1 through WCP-6)
- **WCP-1**: Sequence
- **WCP-2**: Parallel Split
- **WCP-3**: Synchronization
- **WCP-4**: Exclusive Choice
- **WCP-5**: Simple Merge
- **WCP-6**: Multi-Choice

### Branching Patterns
- **WCP-7**: Multi-Merge
- **WCP-8**: Discriminator
- **WCP-9**: N-out-of-M
- **WCP-10**: Deferred Choice
- **WCP-11**: Immediate Choice
- **WCP-12**: Interleaved Routing
- **WCP-13**: Milestone
- **WCP-14**: Cancel Case
- **WCP-15**: Cancel Task
- **WCP-16**: Cancel Region

### State-Based Patterns
- **WCP-17**: Or Join
- **WCP-18**: Or Split
- **WCP-19**: Synchronizing Merge
- **WCP-20**: Synchronizing Split
- **WCP-21**: Structured Synchronizing Split
- **WCP-22**: Structured Synchronizing Merge
- **WCP-23**: Structured Synchronizing Split/Join
- **WCP-24**: Multi-Instance Sequential
- **WCP-25**: Multi-Instance Parallel
- **WCP-26**: Multi-Instance Iterative
- **WCP-27**: Multi-Instance with a Priori Design-Time Knowledge
- **WCP-28**: Multi-Instance with a Priori Runtime Knowledge
- **WCP-29**: Sequential/Parallel Iteration
- **WCP-30**: Deferred Time Choice
- **WCP-31**: Instant Time Choice
- **WCP-32**: Multiple Instances without Synchronization
- **WCP-33**: Multiple Instances with Synchronization
- **WCP-34**: Discriminator without Reset
- **WCP-35**: Discriminator with Reset
- **WCP-36**: Milestone without Cancel
- **WCP-37**: Milestone with Cancel
- **WCP-38**: Cancel Part Case
- **WCP-39**: Cancel Part Task
- **WCP-40**: Cancel Part Process
- **WCP-41**: Cancel Subprocess
- **WCP-42**: Cancel Multiple Instances
- **WCP-43**: Cancel Multiple Instances with Synchronization
- **WCP-44**: Cancel Multiple Instances without Synchronization

## Output Formats

### Console (Default)
Human-readable output with colored status indicators:

```bash
[1/5] WCP-1... OK (150ms)
[2/5] WCP-2... OK (120ms)
[3/5] WCP-3... OK (180ms)
[4/5] WCP-4... FAIL (30000ms)
[5/5] WCP-5... OK (90ms)

======================================================================
Complete. 4/5 patterns successful.
1 patterns failed:
  - WCP-4: Execution timed out after 30000ms

Total duration: 1m 15s
======================================================================
```

### JSON
Machine-readable format suitable for automation:

```json
{
  "version": "6.0.0",
  "timestamp": "2026-02-22T00:30:00Z",
  "totalPatterns": 5,
  "successfulPatterns": 4,
  "failedPatterns": 1,
  "patterns": [
    {
      "id": "WCP-1",
      "name": "Sequence",
      "status": "SUCCESS",
      "durationMs": 150,
      "executionMetrics": {
        "workItemCount": 1,
        "eventCount": 2
      }
    }
  ],
  "totalDurationMs": 75000
}
```

### Markdown
Formatted documentation ready for use:

```markdown
# YAWL Pattern Demo Report

## Summary
- Total patterns: 5
- Successful: 4 (80%)
- Failed: 1 (20%)
- Duration: 1m 15s

## Pattern Results

### WCP-1: Sequence
- **Status**: ✅ SUCCESS
- **Duration**: 150ms
- **Work Items**: 1
- **Events**: 2

### WCP-2: Parallel Split
- **Status**: ✅ SUCCESS
- **Duration**: 120ms
- **Work Items**: 2
- **Events**: 3
```

### HTML
Interactive report with CSS styling and JavaScript features.

## Examples

### 1. Run Basic Patterns
```bash
./scripts/run-vdaalst-demo.sh
```

### 2. Run Single Pattern with JSON Output
```bash
./scripts/run-vdaalst-demo.sh --pattern WCP-1 --format json
```

### 3. Run All Basic Patterns with Markdown Report
```bash
./scripts/run-vdaalst-demo.sh --category BASIC --format markdown --output basic-patterns.md
```

### 4. Run All Patterns with HTML Report
```bash
./scripts/run-vdaalst-demo.sh --all --format html --output pattern-report.html
```

### 5. Run Patterns with Custom Timeout
```bash
./scripts/run-vdaalst-demo.sh --pattern WCP-1,WCP-2 --timeout 60
```

### 6. Run Sequential Execution
```bash
./scripts/run-vdaalst-demo.sh --pattern WCP-1,WCP-2,WCP-3 --sequential
```

## Troubleshooting

### Common Issues

1. **Java Version Issues**
   ```
   Error: Unsupported class file version
   ```
   **Solution**: Use Java 21+ with `--enable-preview` if needed

2. **Missing Dependencies**
   ```
   Error: Could not find class
   ```
   **Solution**: Build the project: `mvn -DskipTests package -pl yawl-mcp-a2a-app`

3. **Timeout Errors**
   ```
   Error: Pattern execution timed out
   ```
   **Solution**: Increase timeout: `--timeout 600`

4. **Empty Output**
   ```
   No patterns found matching criteria
   ```
   **Solution**: Check pattern IDs and categories with `--help`

### Testing the Setup

Run the test script to verify everything works:

```bash
./scripts/test-pattern-demo.sh
```

## Advanced Features

### Token Analysis
The demo includes token savings analysis showing the difference between YAML and XML representations:

```bash
Token Analysis:
  YAML tokens: 1,234
  XML tokens: 2,456
  Savings:     49.7%
```

### Parallel Execution
By default, patterns run in parallel using Java virtual threads. This significantly speeds up execution:

```bash
# Disable parallel execution if needed
./scripts/run-vdaalst-demo.sh --sequential
```

### Pattern Tracing
Enable detailed execution tracing:

```bash
# Not directly supported in current version, but can be added to the demo config
```

## Integration with YAWL Engine

The Pattern Demo Runner uses:
- **YStatelessEngine** - Core workflow engine
- **YAML Converter** - Converts patterns to XML
- **Execution Harness** - Runs pattern instances
- **Report Generator** - Creates output reports

## Performance Considerations

- **Parallel Execution**: Default and recommended for multiple patterns
- **Timeout Settings**: Adjust based on pattern complexity
- **Memory Usage**: Large patterns may require JVM tuning
- **Disk I/O**: Output to file for large reports

## Contributing

To add new patterns or categories:

1. Add pattern definitions in `yawl-mcp-a2a-app/src/main/resources/patterns/`
2. Update pattern registry in `PatternRegistry.java`
3. Add categories in `PatternCategory.java`
4. Test with `./scripts/test-pattern-demo.sh`

## References

- Van der Aalst, W. M. P. (2003). Workflow Patterns: Identification, Representation and Implementation.
- YAWL Workflow Patterns - http://www.workflowpatterns.com/
- YAWL Documentation - https://github.com/yawlfoundation/yawl