# YAWL CLI v6.0.0 — Operations Runbook

**Version**: 6.0.0  
**Updated**: February 22, 2026  
**Audience**: Operations, DevOps, System Administrators  
**Owner**: YAWL Operations Team

---

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Monitoring](#monitoring)
4. [Troubleshooting](#troubleshooting)
5. [Incident Response](#incident-response)
6. [Maintenance](#maintenance)
7. [Rollback Procedures](#rollback-procedures)

---

## Installation

### Prerequisites

- Python 3.10 or higher
- pip (Python package manager)
- Optional: Maven 3.6+ (for build operations)
- Optional: Git (for version control integration)

### Install from PyPI

```bash
# Install latest version
pip install yawl-cli

# Install specific version
pip install yawl-cli==6.0.0

# Verify installation
yawl --version
# Output: yawl, version 6.0.0
```

### Install from Source

```bash
# Clone repository
git clone https://github.com/yawl/yawl.git
cd yawl/cli

# Install in development mode
pip install -e .

# Verify
yawl --version
```

### Verify Installation

```bash
# Check all major components
yawl --help                    # Should show 7 subcommand groups
yawl build --help             # Should show build commands
yawl godspeed --help          # Should show GODSPEED workflow
yawl config show              # Should show current configuration
```

---

## Configuration

### Configuration Hierarchy

YAWL CLI uses a 3-level configuration hierarchy (highest to lowest priority):

```
1. Project level:  ./.yawl/config.yaml       (local project)
2. User level:     ~/.yawl/config.yaml        (user home)
3. System level:   /etc/yawl/config.yaml      (system-wide)
```

### Default Locations

| Platform | Location |
|----------|----------|
| Linux | ~/.yawl/config.yaml or /etc/yawl/config.yaml |
| macOS | ~/.yawl/config.yaml or /etc/yawl/config.yaml |
| Windows | %APPDATA%\.yawl\config.yaml (if applicable) |

### Creating Configuration

```bash
# Initialize default configuration
mkdir -p ~/.yawl
cat > ~/.yawl/config.yaml << 'EOF'
build:
  parallel: true
  threads: 4
  timeout: 600

facts:
  auto_refresh: true
  cache_ttl: 3600

maven:
  profiles: ["analysis"]
  skip_tests: false
EOF
```

### Configuration Options

```yaml
# Build settings
build:
  parallel: true              # Use parallel compilation
  threads: 4                  # Number of threads
  timeout: 600                # Timeout in seconds

# Facts settings
facts:
  auto_refresh: true          # Auto-refresh facts
  cache_ttl: 3600             # Cache TTL in seconds

# Maven settings
maven:
  profiles: ["analysis"]      # Active profiles
  skip_tests: false           # Skip tests during build

# Debug settings (development)
debug:
  enabled: false              # Enable debug output
  verbose: true               # Verbose logging
```

### Reading Configuration

```bash
# Show all configuration
yawl config show

# Get specific value
yawl config get build.parallel
yawl config get build.threads
yawl config get facts.auto_refresh

# Get with default if missing
yawl config get nonexistent.key --default "default_value"
```

### Modifying Configuration

```bash
# Set configuration value
yawl config set build.threads 8

# Verify change
yawl config get build.threads
# Output: 8
```

### Debug Mode

Enable debug output for troubleshooting:

```bash
# Set environment variable
export YAWL_CLI_DEBUG=1

# Run command with debug
yawl build compile

# Verbose mode
yawl build compile --verbose

# Disable debug
unset YAWL_CLI_DEBUG
```

---

## Monitoring

### Health Checks

```bash
# Check CLI version
yawl --version

# Check configuration
yawl config show

# Check Maven availability
mvn --version  # If build operations used

# Check Git availability
git --version  # If Git integration used
```

### Common Checks

```bash
# Verify project detection
yawl build compile --dry-run

# Verify Observatory facts
yawl observatory list

# Verify GODSPEED workflow
yawl godspeed discover --dry-run
```

### Status Indicators

**Success indicators**:
- Command returns exit code 0
- No error messages
- Expected output produced

**Failure indicators**:
- Command returns non-zero exit code
- Error messages in stderr
- Timeout or hanging process

### Performance Metrics

**Expected performance**:
- CLI startup: <500ms
- Config load: <100ms
- Build compile: <60s (depends on project size)
- GODSPEED full circuit: <3 minutes

**Monitoring commands**:

```bash
# Measure CLI startup
time yawl --version

# Measure command execution
time yawl build compile --dry-run

# Measure full workflow
time yawl godspeed full
```

---

## Troubleshooting

### Issue: "Could not find YAWL project root"

**Cause**: CLI run outside YAWL project directory

**Solution**:
```bash
# Navigate to YAWL project root
cd /path/to/yawl

# Verify project markers
ls pom.xml CLAUDE.md

# Retry command
yawl build compile
```

**Prevention**: Always run from project root or subdirectory

### Issue: "No such option: --version"

**Cause**: Entry point not configured correctly

**Solution**:
```bash
# Check installation
pip show yawl-cli

# Reinstall
pip uninstall yawl-cli
pip install yawl-cli==6.0.0

# Verify
yawl --version
```

### Issue: "ModuleNotFoundError: No module named 'godspeed_cli'"

**Cause**: Package not properly installed or path issue

**Solution**:
```bash
# Check Python path
python -c "import sys; print(sys.path)"

# Check installation
pip show yawl-cli

# Reinstall from source
cd /home/user/yawl/cli
pip install -e .
```

### Issue: Maven not found when running build

**Cause**: Maven not installed or not in PATH

**Solution**:
```bash
# Check Maven installation
mvn --version

# If not found, install Maven
sudo apt-get install maven          # Ubuntu/Debian
brew install maven                  # macOS
# Windows: Download from https://maven.apache.org

# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/default
```

### Issue: Configuration file invalid YAML

**Cause**: YAML syntax error in config file

**Solution**:
```bash
# Check syntax with yq (if available)
yq eval '.build' ~/.yawl/config.yaml

# Or validate with Python
python -c "import yaml; yaml.safe_load(open('~/.yawl/config.yaml'))"

# Fix YAML syntax (common issues):
# - Inconsistent indentation
# - Missing colons after keys
# - Unquoted strings with special characters
```

### Issue: Timeout during build

**Cause**: Build taking longer than timeout value

**Solution**:
```bash
# Check current timeout
yawl config get build.timeout

# Increase timeout
yawl config set build.timeout 1200    # 20 minutes

# Run with explicit timeout flag
yawl build compile --timeout 1200

# Investigate why build is slow
yawl build compile --verbose
```

### Debug Checklist

When troubleshooting issues:

1. **Enable debug mode**
   ```bash
   export YAWL_CLI_DEBUG=1
   yawl <command>
   ```

2. **Check logs**
   ```bash
   # If logging directory exists
   tail -f ~/.yawl/logs/yawl.log
   ```

3. **Verify environment**
   ```bash
   yawl --version
   yawl config show
   mvn --version
   git --version
   ```

4. **Test with dry-run**
   ```bash
   yawl build compile --dry-run
   yawl godspeed discover --dry-run
   ```

5. **Check file permissions**
   ```bash
   ls -la ~/.yawl/
   ls -la .yawl/
   ```

6. **Review error message**
   - Read error message carefully
   - Check suggested fixes
   - Look for file paths or line numbers

---

## Incident Response

### Critical Issues (P1)

**Symptoms**: CLI completely non-functional, cannot run any commands

**Response**:
1. Check installation: `pip show yawl-cli`
2. Reinstall: `pip install --force-reinstall yawl-cli==6.0.0`
3. Verify: `yawl --version`
4. If still broken: Rollback to previous version (see below)

**Escalation**: Report to YAWL operations team

### High Priority Issues (P2)

**Symptoms**: Specific commands fail consistently, but CLI is functional

**Response**:
1. Enable debug mode: `export YAWL_CLI_DEBUG=1`
2. Run failing command: `yawl <command>`
3. Capture output and error messages
4. Check troubleshooting section above
5. If unresolved: Report with debug output

**Escalation**: Report to YAWL operations team with debug logs

### Medium Priority Issues (P3)

**Symptoms**: Commands work but slow or occasional errors

**Response**:
1. Collect performance metrics
2. Check system resources (disk, memory, CPU)
3. Review configuration
4. Try again (might be transient)
5. Report if recurring

### Security Issues

**If suspected security issue**:

1. **Do not** share logs in public channels
2. Do **not** commit credentials to git
3. Report to security@yawl-org.com
4. Include reproduction steps
5. Include affected version

---

## Maintenance

### Regular Tasks

#### Daily
- Monitor CLI execution (no errors in logs)
- Spot-check commands: `yawl --version`, `yawl config show`

#### Weekly
- Review configuration: `yawl config show`
- Check for available updates: `pip list --outdated | grep yawl`
- Verify fact generation (if used): `yawl observatory list`

#### Monthly
- Run security update check: `pip install --upgrade pip`
- Test backup/restore procedures
- Review any collected logs for issues

#### Quarterly
- Update to latest stable version
- Run comprehensive test suite
- Review configuration best practices

### Updating YAWL CLI

```bash
# Check current version
yawl --version

# Check available versions
pip index versions yawl-cli

# Update to latest
pip install --upgrade yawl-cli

# Update to specific version
pip install yawl-cli==6.1.0

# Verify update
yawl --version
```

### Clearing Cache

If experiencing stale data issues:

```bash
# Clear Python cache
find ~/.yawl -name "*.pyc" -delete
find ~/.yawl -name "__pycache__" -type d -delete

# Clear pip cache (if needed)
pip cache purge

# Refresh Observatory facts (if used)
yawl observatory refresh
```

### Log Rotation

If collecting logs to .yawl/logs/:

```bash
# Manual rotation
gzip ~/.yawl/logs/yawl.log.*
rm ~/.yawl/logs/yawl.log.*.gz older than 30 days

# Or use logrotate (Linux)
# Create /etc/logrotate.d/yawl-cli:
#   /home/*/.yawl/logs/*.log {
#     daily
#     rotate 10
#     missingok
#     notifempty
#   }
```

---

## Rollback Procedures

### Rollback to Previous Version

If critical issues found post-upgrade:

```bash
# Get list of installed versions
pip index versions yawl-cli

# Install previous version
pip install yawl-cli==6.0.0-rc1

# Verify version
yawl --version
```

### Emergency Rollback

If CLI is completely broken:

```bash
# Remove current installation
pip uninstall yawl-cli

# Install stable version from scratch
pip install yawl-cli==6.0.0

# Verify all commands
yawl --help
yawl build --help
yawl config show
```

### Restore from Backup

If configuration was corrupted:

```bash
# Backup current config
cp ~/.yawl/config.yaml ~/.yawl/config.yaml.broken

# Restore from backup
cp ~/.yawl/config.yaml.backup ~/.yawl/config.yaml

# Verify
yawl config show
```

---

## Support & Contact

### Getting Help

1. **Documentation**: https://github.com/yawl/yawl/docs/
2. **Issues**: https://github.com/yawl/yawl/issues/
3. **Email**: support@yawl-org.com

### Reporting Issues

Include:
- YAWL CLI version: `yawl --version`
- Python version: `python --version`
- Operating system: `uname -a`
- Complete command that failed
- Error message (preferably with --verbose or YAWL_CLI_DEBUG=1)
- Steps to reproduce

### Escalation

| Issue Type | Primary | Escalation | Timeline |
|-----------|---------|------------|----------|
| P1 (Critical) | ops-team@yawl-org.com | engineering@yawl-org.com | <1 hour |
| P2 (High) | ops-team@yawl-org.com | engineering@yawl-org.com | <4 hours |
| P3 (Medium) | support@yawl-org.com | ops-team@yawl-org.com | <1 day |
| Feature request | support@yawl-org.com | product@yawl-org.com | <1 week |

---

## Appendix A: Environment Variables

### Available Variables

| Variable | Values | Default | Purpose |
|----------|--------|---------|---------|
| YAWL_CLI_DEBUG | 1, true, yes | false | Enable debug output |
| JAVA_HOME | /path/to/java | auto-detect | Java installation |
| MAVEN_HOME | /path/to/maven | auto-detect | Maven installation |
| HOME | /home/user | auto-detect | User home directory |
| PWD | /current/dir | auto-detect | Current directory |

### Setting Environment Variables

```bash
# Bash/Zsh
export YAWL_CLI_DEBUG=1
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Fish
set -x YAWL_CLI_DEBUG 1
set -x JAVA_HOME /usr/lib/jvm/java-17-openjdk

# Windows (PowerShell)
$env:YAWL_CLI_DEBUG = "1"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

---

**Document Version**: 1.0  
**Last Updated**: February 22, 2026  
**Status**: PRODUCTION READY

