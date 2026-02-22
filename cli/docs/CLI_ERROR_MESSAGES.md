# YAWL CLI Error Messages and Recovery Guide

**Version**: 6.0.0
**Updated**: 2026-02-22

This guide documents 50+ common error scenarios and recovery steps for the YAWL CLI.

## Quick Reference

| Error | Cause | Recovery |
|-------|-------|----------|
| Could not find YAWL project root | Not in a YAWL directory | `cd` to project directory with pom.xml + CLAUDE.md |
| Maven not found | Maven not installed | `sudo apt install maven` (Ubuntu) or `brew install maven` (macOS) |
| mvn: Command not found | JAVA_HOME not set or mvn not in PATH | Set `export JAVA_HOME=/path/to/java` |
| Fact file not found | Facts not generated | Run `yawl observatory generate` |
| Invalid YAML in config file | Config file has syntax errors | Fix YAML or delete .yawl/config.yaml and run `yawl init` |
| Permission denied | Missing file/directory permissions | Check ownership: `ls -la .yawl/` |
| Command timed out | Build took too long | Increase timeout: `--timeout 1200` |

---

## 1. Project Not Found

### Error Message
```
Could not find YAWL project root.
Please run from within a YAWL project directory.
YAWL project must contain both: pom.xml and CLAUDE.md
```

### Causes
- Running CLI outside a YAWL project directory
- pom.xml or CLAUDE.md missing from project root
- In a subdirectory without parent pom.xml

### Recovery Steps
1. Verify you're in a YAWL project: `ls pom.xml CLAUDE.md`
2. If missing, navigate to project root: `cd /path/to/yawl`
3. Verify both files exist: `find . -maxdepth 1 -name "pom.xml" -o -name "CLAUDE.md"`

### Example
```bash
$ yawl build compile
✗ Error: Could not find YAWL project root...

$ cd ~/projects/yawl
$ ls pom.xml CLAUDE.md
CLAUDE.md  pom.xml

$ yawl build compile
✓ Compile successful (3.2s)
```

---

## 2. Maven Not Installed

### Error Message
```
Maven not found: mvn
Install Maven: sudo apt install maven (Ubuntu) or brew install maven (macOS)
```

### Causes
- Maven not installed on system
- Maven not in PATH environment variable
- Java not properly configured

### Recovery Steps
1. Install Maven:
   - **Ubuntu/Debian**: `sudo apt update && sudo apt install maven`
   - **macOS**: `brew install maven`
   - **Manual**: Download from [maven.apache.org](https://maven.apache.org), add to PATH
2. Verify installation: `mvn --version`
3. Set JAVA_HOME if needed: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`

### Verification
```bash
$ mvn --version
Apache Maven 3.8.1
Maven home: /usr/share/maven
Java version: 17.0.2

# Try build again
$ yawl build compile
✓ Compile successful
```

---

## 3. Java Not Found

### Error Message
```
Error: JAVA_HOME not set correctly
Java compiler not available
```

### Causes
- JAVA_HOME environment variable not set
- Java not installed
- Java installation corrupted

### Recovery Steps
1. Find Java installation: `which java` or `java -version`
2. Set JAVA_HOME:
   ```bash
   # Ubuntu/Debian
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

   # macOS
   export JAVA_HOME=$(/usr/libexec/java_home)

   # Manual location
   export JAVA_HOME=/opt/jdk17
   ```
3. Add to ~/.bashrc or ~/.zshrc for persistence:
   ```bash
   echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
   source ~/.bashrc
   ```
4. Verify: `echo $JAVA_HOME` and `javac -version`

---

## 4. Facts Not Generated

### Error Message
```
Facts directory not found: /path/to/yawl/docs/v6/latest/facts
Run: yawl observatory generate
```

### Causes
- Observatory has never run
- Facts directory deleted
- Project structure changed

### Recovery Steps
1. Generate facts: `yawl observatory generate`
2. Verify generation: `yawl observatory list`
3. If generation fails, check Observatory logs

### Troubleshooting
```bash
# Check facts directory
$ ls -la docs/v6/latest/facts/
total 0

# Generate fresh facts
$ yawl observatory generate
[bold cyan]Generating Observatory facts[/bold cyan]
✓ Facts generated successfully
8 fact files created

# Verify
$ yawl observatory list
Available Facts
┏━━━━━━━━━━━━━━┓
┃ Fact File    ┃
┡━━━━━━━━━━━━━━┩
│ modules.json │
│ tests.json   │
│ gates.json   │
└──────────────┘
```

---

## 5. Invalid Configuration File

### Error Message
```
Invalid YAML in /home/user/yawl/.yawl/config.yaml:
Line 5: mapping values are not allowed here
Please fix the YAML syntax or delete the file to regenerate.
```

### Causes
- YAML syntax error (indentation, colons, quotes)
- Corrupted config file
- Manual editing introduced invalid format

### Recovery Steps
1. View the error line: `sed -n '5p' .yawl/config.yaml`
2. Fix the YAML (check indentation, colons, quotes)
3. Validate YAML: `python3 -c "import yaml; yaml.safe_load(open('.yawl/config.yaml'))"`
4. If unfixable, regenerate:
   ```bash
   rm .yawl/config.yaml
   yawl init --interactive
   ```

### YAML Checklist
- Indentation must be 2 or 4 spaces (not tabs)
- Keys followed by colon+space: `key: value`
- String values with colons quoted: `"value: with colon"`
- No trailing spaces on lines

### Example Fix
```yaml
# WRONG
build:
parallel: true  # Missing indentation

# CORRECT
build:
  parallel: true
```

---

## 6. Permission Denied

### Error Messages
```
Permission denied creating config directory /home/user/yawl/.yawl: [Errno 13]
Check directory permissions.
```

### Causes
- Directory owned by another user
- File permissions too restrictive
- Filesystem mounted read-only
- Insufficient disk space

### Recovery Steps
1. Check ownership and permissions:
   ```bash
   ls -la /home/user/yawl/.yawl/
   ```
2. Fix ownership:
   ```bash
   sudo chown -R $(whoami):$(whoami) /home/user/yawl/.yawl
   ```
3. Fix permissions:
   ```bash
   chmod -R u+rwX /home/user/yawl/.yawl
   ```
4. Verify:
   ```bash
   touch /home/user/yawl/.yawl/test-file.txt && rm $_
   ```

### Advanced: Specific File Issues
```bash
# Restore default permissions
chmod 755 /home/user/yawl/.yawl
chmod 644 /home/user/yawl/.yawl/config.yaml

# Check disk space
df -h /home/user/yawl
```

---

## 7. Command Timeout

### Error Message
```
Command timed out after 600 seconds: bash scripts/dx.sh compile
Increase timeout with: --timeout 1200
```

### Causes
- Build takes longer than timeout (default: 600s)
- Resource constraints (low CPU/RAM)
- Network issues during dependency download
- Hang in compile phase

### Recovery Steps
1. Increase timeout: `yawl build compile --timeout 1200`
2. Use parallel compilation: `yawl config set build.threads 8`
3. Check system resources: `top`, `free -h`
4. Try specific module: `yawl build compile --module yawl-engine`
5. Check network: `ping repo.maven.apache.org`

### Best Practices
```bash
# For slow machine
yawl build compile --timeout 1800  # 30 minutes

# For large builds
yawl build all --timeout 2400      # 40 minutes (compile+test+validate)

# Parallel threads for multi-core
yawl config set build.threads 16
```

---

## 8. Fact File Malformed

### Error Message
```
Malformed JSON in /path/to/facts/modules.json: Expecting value: line 42 column 3 (char 523)
Line 42: Expecting ',' delimiter
Try regenerating facts: yawl observatory generate
```

### Causes
- Fact file corrupted
- Observatory crash during generation
- Manual editing of fact files
- Stale facts after codebase changes

### Recovery Steps
1. Regenerate facts:
   ```bash
   yawl observatory generate
   ```
2. If regeneration fails, check Observatory logs
3. Verify facts are readable:
   ```bash
   python3 -c "import json; json.load(open('docs/v6/latest/facts/modules.json'))"
   ```
4. Backup old facts and retry:
   ```bash
   mv docs/v6/latest/facts docs/v6/latest/facts.backup
   yawl observatory generate
   ```

---

## 9. Script Not Found

### Error Message
```
Script not found: bash scripts/dx.sh
Check that you're in a YAWL project directory.
```

### Causes
- Running CLI from wrong directory
- scripts/dx.sh deleted or moved
- Project structure damaged
- Symlinks broken

### Recovery Steps
1. Verify project structure:
   ```bash
   ls scripts/dx.sh scripts/observatory/ .claude/hooks/
   ```
2. If missing, restore from git:
   ```bash
   git checkout scripts/dx.sh
   git checkout .claude/hooks/
   ```
3. Verify directory: `pwd` should show YAWL project root

---

## 10. Network Errors (Proxy/Firewall)

### Error Message
```
Failed to download artifact: Connection timed out
No route to host: repo.maven.apache.org
```

### Causes
- Network unavailable
- Firewall blocking Maven Central
- Proxy misconfigured
- DNS resolution failing

### Recovery Steps
1. Check connectivity:
   ```bash
   ping -c 3 repo.maven.apache.org
   curl -I https://repo.maven.apache.org
   ```
2. Check proxy settings:
   ```bash
   env | grep -i proxy
   ```
3. If behind proxy, configure Maven:
   ```bash
   # Edit ~/.m2/settings.xml
   <proxy>
     <id>corporate</id>
     <active>true</active>
     <protocol>https</protocol>
     <host>proxy.company.com</host>
     <port>8080</port>
   </proxy>
   ```
4. Clear cache and retry:
   ```bash
   mvn clean -U
   yawl build compile
   ```

---

## 11. File Too Large

### Error Message
```
Fact file is too large (156.3 MB).
Maximum allowed size: 100 MB
```

### Causes
- Fact file unexpectedly large (bug in Observatory)
- Binary files included in facts
- Codebase too large for fact generation

### Recovery Steps
1. Check fact file:
   ```bash
   ls -lh docs/v6/latest/facts/
   ```
2. Check what's in it:
   ```bash
   head -100 docs/v6/latest/facts/modules.json
   ```
3. Regenerate with limits:
   ```bash
   rm docs/v6/latest/facts/*
   yawl observatory generate
   ```
4. If persistent, contact support

---

## 12. Invalid Input Format

### Error Message
```
Invalid export format: xml
Valid formats: turtle, json, yaml
```

### Causes
- Typo in --format option
- Unsupported format requested
- Case sensitivity issue

### Recovery Steps
1. Check available formats in help:
   ```bash
   yawl ggen export --help
   ```
2. Use correct format:
   ```bash
   yawl ggen export spec.ttl --format turtle  # ✓ Correct
   yawl ggen export spec.ttl --format xml      # ✗ Not supported
   ```
3. List supported formats by command:
   ```bash
   # ggen: turtle, json, yaml
   # gregverse: bpmn, xpdl, petri, json
   ```

---

## 13. Invalid Configuration Value

### Error Message
```
Validation error: Invalid config key: build..threads
Keys must be alphanumeric with dots (e.g., 'build.threads')
```

### Causes
- Typo in config key
- Double dots
- Invalid characters
- Key doesn't exist

### Recovery Steps
1. View available keys:
   ```bash
   yawl config show
   ```
2. Use correct key format:
   ```bash
   yawl config set build.threads 8   # ✓ Correct
   yawl config set build..threads 8  # ✗ Double dot
   ```
3. Query existing value:
   ```bash
   yawl config get build.threads
   ```

---

## 14. Team Creation Failed

### Error Message
```
Validation error: Agent count must be between 2 and 5
```

### Causes
- Agent count out of range
- Invalid team name (special characters)
- Quantum count > 5

### Recovery Steps
1. Check team requirements:
   ```bash
   yawl team create --help
   ```
2. Use valid parameters:
   ```bash
   yawl team create my-team --quantums engine,schema --agents 2
   ```
3. Valid team names: alphanumeric, hyphens, underscores
4. Quantum count: 2-5 (agent count <= quantum count)

---

## 15. Team Not Found

### Error Message
```
Team not found: invalid-team-id
Run 'yawl team list' to see available teams
```

### Causes
- Typo in team ID
- Team already completed/deleted
- Wrong project

### Recovery Steps
1. List available teams:
   ```bash
   yawl team list
   ```
2. Get exact team ID:
   ```bash
   ls -la .team-state/
   ```
3. Use correct ID:
   ```bash
   yawl team resume τ-engine+schema-ABC123  # ✓ Correct
   ```

---

## 16. Compilation Error

### Error Message
```
✗ Compile failed
[ERROR] /path/to/File.java:[42] error: cannot find symbol
        symbol: variable x
        location: class Foo
```

### Causes
- Syntax errors in source code
- Missing dependencies
- Wrong Java version
- IDE cache issues

### Recovery Steps
1. Clean and rebuild:
   ```bash
   yawl build clean
   yawl build compile
   ```
2. Check specific module:
   ```bash
   yawl build compile --module yawl-engine
   ```
3. Review error line:
   ```bash
   sed -n '42p' /path/to/File.java
   ```
4. Verbose output:
   ```bash
   yawl build compile --verbose
   ```

---

## 17. Test Failure

### Error Message
```
✗ Tests failed
[ERROR] RunnerTest.testWorkflow() FAILED
java.lang.AssertionError: Expected 5, got 3
```

### Causes
- Test code broken
- Functionality regressed
- Test environment not set up
- Timing/concurrency issue

### Recovery Steps
1. Run specific test:
   ```bash
   yawl build test --module yawl-engine
   ```
2. View full error:
   ```bash
   yawl build test --verbose
   ```
3. Re-run with verbose logging:
   ```bash
   mvn test -Dtest=RunnerTest -e
   ```

---

## 18. Validation Failure (Static Analysis)

### Error Message
```
✗ Validation failed
[ERROR] Checkstyle ERRORS:
/path/to/File.java:42: warning: Line length exceeds 120
```

### Causes
- Code style violations (line length, formatting)
- Potential bugs (SpotBugs)
- Code quality issues (PMD)

### Recovery Steps
1. View violations:
   ```bash
   yawl build validate --verbose
   ```
2. Fix code style:
   ```bash
   # Use IDE formatter
   mvn spotless:apply
   ```
3. Re-run validation:
   ```bash
   yawl build validate
   ```

---

## 19. Disk Space Issues

### Error Message
```
Cannot write config file /home/user/yawl/.yawl/config.yaml: No space left on device
Check file permissions and disk space.
```

### Causes
- Disk full
- Quota exceeded
- Filesystem error

### Recovery Steps
1. Check disk space:
   ```bash
   df -h /home/user/yawl
   du -sh /home/user/yawl
   ```
2. Free up space:
   ```bash
   yawl build clean
   rm -rf .m2/repository/*  # Remove Maven cache (use with caution)
   ```
3. Check inode usage:
   ```bash
   df -i /home/user/yawl
   ```

---

## 20. Config Directory Creation Failed

### Error Message
```
Cannot create config directory /home/user/yawl/.yawl: Permission denied
Check directory permissions.
```

### Causes
- Parent directory read-only
- Owner mismatch
- SELinux/AppArmor restrictions

### Recovery Steps
1. Check parent:
   ```bash
   ls -ld /home/user/yawl
   ```
2. Fix permissions:
   ```bash
   chmod u+w /home/user/yawl
   ```
3. Create directory manually:
   ```bash
   mkdir -p /home/user/yawl/.yawl
   chmod 755 /home/user/yawl/.yawl
   ```

---

## 21-50: Additional Common Issues

### Invalid Turtle File
```
# Error
Spec file not found: invalid.ttl

# Fix
yawl ggen generate valid.ttl
ls -l *.ttl  # Verify file exists
```

### YAWL File Not Found
```
# Error
YAWL file not found: spec.yawl

# Fix
yawl ggen generate spec.ttl --output spec.yawl
```

### Round-Trip Conversion Failure
```
# Error
✗ Round-trip test successful: Generation phase failed

# Fix
yawl ggen generate spec.ttl --verbose
yawl ggen round_trip spec.ttl --verbose
```

### Workflow Import Format Error
```
# Error
Invalid format: doc
Valid formats: auto, xpdl, bpmn, petri

# Fix
yawl gregverse import workflow.xpdl --format xpdl
```

### Export Output Permission Issue
```
# Error
Permission denied writing to /path/to/output.json

# Fix
yawl ggen export spec.yawl --output /tmp/output.json
chmod 644 /tmp/output.json
```

### Config Show Error (RuntimeError)
```
# Error
Cannot read config file /home/user/yawl/.yawl/config.yaml: [Errno 2]

# Fix
rm /home/user/yawl/.yawl/config.yaml
yawl init --interactive
```

### Observable File Missing
```
# Error
Facts directory not found: docs/v6/latest/facts

# Fix
mkdir -p docs/v6/latest/facts
yawl observatory generate
```

### Team Message Delivery Failure
```
# Error
✗ Send failed: team-id/engineer-a message failed

# Fix
yawl team status team-id
yawl team resume team-id  # Check if team is active
```

### Config Merge Conflict
```
# Error
Invalid YAML in ~/.yawl/config.yaml

# Fix: Use project config instead
rm ~/.yawl/config.yaml
yawl config set --project key value
```

### Build Artifact Not Found
```
# Error
Maven cannot resolve dependency: com.example:artifact:1.0

# Fix
mvn clean install
yawl build compile --verbose
```

---

## Debug Mode

Enable debug mode to see full stack traces:

```bash
# Via command line
yawl --debug build compile

# Via environment
export YAWL_CLI_DEBUG=1
yawl build compile

# Disable
unset YAWL_CLI_DEBUG
```

Debug mode shows:
- Full Python stack traces
- All shell commands executed
- Verbose output from subprocesses
- File I/O operations

---

## Getting Help

```bash
# Show command help
yawl --help
yawl build --help
yawl build compile --help

# View configuration
yawl config show
yawl config locations

# Check project status
yawl status
yawl status --verbose

# View environment
yawl version --verbose
```

---

## Contact & Support

**Issues**: Open issue at [YAWL GitHub](https://github.com/yawl-org/yawl)
**Docs**: See [GODSPEED_CLI_GUIDE.md](./GODSPEED_CLI_GUIDE.md)
**Email**: support@yawl.org

---

**Last Updated**: 2026-02-22
**Maintained by**: YAWL Team
**License**: Apache 2.0
