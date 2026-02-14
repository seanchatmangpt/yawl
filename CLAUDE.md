# YAWL (Yet Another Workflow Language) - Claude Development Guide

This document provides instructions for building, running, and testing the YAWL project in Claude Code environments.

## Project Overview

YAWL is a BPM/Workflow system with a powerful modelling language that handles complex data transformations and integration with organizational resources and external Web Services.

**Project Location:** `/home/user/yawl`
**Build System:** Apache Ant
**Java Version:** OpenJDK 21.0.10
**Default Database:** PostgreSQL (configurable)

## Quick Start

### Build the Project
```bash
# Compile all source files
ant compile

# Run unit tests
ant unitTest

# Build the Control Panel executable JAR
ant build_controlPanel.jar

# Full cycle: clean, compile, test, and build
ant clean compile unitTest build_controlPanel.jar
```

### Run the Project
```bash
# Run YAWL Control Panel (GUI application)
java -jar output/YawlControlPanel-5.2.jar

# List all runnable classes with main methods
find src -name "*.java" -type f -exec grep -l "public static void main" {} \;
```

## Build Targets

### Core Build Targets
- **`compile`** - Compile all 866 source files
- **`clean`** - Remove build artifacts
- **`unitTest`** - Run unit tests (102 tests total)
- **`compile-test`** - Compile test files

### Build Individual Components
- **`build_controlPanel.jar`** - YAWL Control Panel (423 KB)
- **`build_procletEditor.jar`** - Proclet Editor
- **`build_YResourceServiceClient.jar`** - Resource Service Client
- **`build_simulator.jar`** - Workflow Simulator
- **`build_engine.war`** - YAWL Engine Web Application
- **`buildWebApps`** - Build all WAR files
- **`buildAll`** - Build all release-required material

## Test Results

Last run (Feb 14, 2026):
- **Tests Run:** 102
- **Failures:** 12
- **Errors:** 41
- **Skipped:** 0
- **Execution Time:** ~2 seconds

*Note: Some test failures are expected due to missing database and Tomcat server dependencies.*

## Build Output

All build artifacts are located in `/home/user/yawl/output/`:
- `yawl-lib-5.2.jar` - Core YAWL library (3.0 MB)
- `YawlControlPanel-5.2.jar` - Control Panel executable (423 KB)

Compiled classes: `/home/user/yawl/classes/`

## Configuration

### Database Configuration
The project is configured for PostgreSQL by default. To change the database:

1. Edit `build/build.properties`
2. Set one of these properties to `true`:
   - `use_postgres8`
   - `use_mysql`
   - `use_derby`
   - `use_h2`
   - `use_hypersonic`
   - `use_oracle`

### Build Properties
Located in `build/build.properties`:
- Tomcat installation directory and configuration
- Database selection
- Component build numbers and timestamps
- Hibernate properties

## Common Commands

```bash
# View all available build targets
ant -p

# Build and display help
ant help

# Run specific tests
ant resourcingtest

# Generate Javadoc
ant javadoc

# Deploy web applications (requires Tomcat)
ant deployWebapps

# Deploy engine only
ant deployEngine
```

## Project Structure

```
yawl/
├── src/                    # Source code (866 Java files)
├── test/                   # Test code (77 Java files)
├── build/                  # Ant build configuration
│   ├── build.xml          # Main build file
│   ├── build.properties   # Build configuration
│   └── 3rdParty/          # Third-party libraries
├── classes/               # Compiled classes (generated)
├── output/                # Build artifacts (generated)
├── exampleSpecs/          # Example YAWL specifications
└── schema/                # YAWL schema definitions
```

## Troubleshooting

### Build Warnings
The project shows 107 Java warnings, primarily from:
- Java 21 deprecation warnings (URL constructors, Integer boxing, etc.)
- Hibernate API deprecations
- Unchecked generic operations

These are non-fatal and do not prevent builds or execution.

### Missing Ant
If Ant is not installed, download and use Apache Ant 1.10.14:
```bash
# Download
wget https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.14-bin.tar.gz

# Extract
tar -xzf apache-ant-1.10.14-bin.tar.gz

# Add to PATH
export PATH="/path/to/apache-ant-1.10.14/bin:$PATH"
```

### Missing Database
For full testing with database integration:
```bash
# Install PostgreSQL
apt-get install postgresql-client postgresql

# Or use H2 (embedded database - change build.properties)
```

## Development Workflow

### In Claude Code CLI
```bash
# Build and test in one command
ant clean compile unitTest build_controlPanel.jar

# Run in background
java -jar output/YawlControlPanel-5.2.jar &
```

### In Claude Code Web
- Use SessionStart hook to automatically run build and tests
- Push changes to designated feature branch
- Create pull requests from web session

## Next Steps

1. **Setup Web Development** - See `.claude/session-start-hook.sh`
2. **Configure IDE** - Import as Ant project in your IDE
3. **Run Web Services** - Deploy to Tomcat for full YAWL environment
4. **Explore Components** - Check individual service WARs in `build/` directory

## Resources

- **YAWL Foundation:** https://yawlfoundation.github.io
- **Workflow Language Docs:** See `schema/` directory for XSD specifications
- **Example Specs:** See `exampleSpecs/` for sample workflows

## Integration with Claude Code

This project is configured to work seamlessly with Claude Code:
- ✅ Builds with Ant (no special IDE required)
- ✅ Tests runnable with `ant unitTest`
- ✅ Clean build artifacts tracked in `.gitignore`
- ✅ SessionStart hook ready for automated builds
- ✅ Commit hooks for quality checks

---

**Last Updated:** February 14, 2026
**Project Version:** 5.2
