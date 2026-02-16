# YAWL Build Skill (Maven)

**Command**: `/yawl-build`

**Description**: Build YAWL modules using Maven. Provides convenient shortcuts for common build operations.

## Aliases
- `build` - Compile and package
- `test` - Run test suite
- `package` - Create distribution packages

## Examples

```bash
# Compile source code
/yawl-build compile

# Run complete build with tests
/yawl-build package

# Build specific module
/yawl-build --module=yawl-engine compile

# Clean build artifacts
/yawl-build clean

# Build all WARs without tests
/yawl-build buildWebApps

# Generate Javadoc
/yawl-build javadoc
```

## Supported Targets

| Target | Maven Goal | Description |
|--------|-----------|-------------|
| `compile` | `mvn clean compile` | Compile source code only |
| `test` / `unitTest` | `mvn clean test` | Run JUnit test suite |
| `package` / `build` / `buildAll` | `mvn clean package` | Full build with tests and package |
| `buildWebApps` | `mvn clean package -DskipTests` | Build WAR files (skip tests) |
| `clean` | `mvn clean` | Remove build artifacts |
| `install` | `mvn clean install` | Build and install to local repo |
| `javadoc` | `mvn javadoc:javadoc` | Generate API documentation |
| `verify` | `mvn clean verify` | Build and verify |

## Parameters

- `--module=MODULE` - Build specific Maven module (e.g., `yawl-engine`)
- `--verbose` - Enable verbose output with `-X` flag
- `--quiet` - Suppress Maven output (default when not verbose)

## Equivalent Commands

Instead of using the skill, you can run Maven directly:

```bash
# Instead of /yawl-build compile
mvn clean compile

# Instead of /yawl-build test
mvn clean test

# Instead of /yawl-build --module=yawl-engine test
mvn -pl yawl-engine clean test
```
