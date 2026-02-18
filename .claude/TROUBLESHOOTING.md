# YAWL Troubleshooting Guide

Common issues and solutions for YAWL development.

---

## Build Issues

### "Module not found: yawl-worklet"

**Problem:** Build fails with module not found error for `yawl-worklet`.

**Cause:** The `yawl-worklet` module was removed from the reactor in v6.0.0.

**Solution:**
1. Update any custom scripts to remove `yawl-worklet` from module lists
2. Verify the correct module list:
```bash
# Correct module list (no yawl-worklet)
grep "<module>" pom.xml
```

### Maven Out of Memory

**Problem:** `java.lang.OutOfMemoryError: Java heap space`

**Solution:** Increase Maven memory:
```bash
# Temporary
export MAVEN_OPTS="-Xmx4g"

# Permanent (add to ~/.bashrc or ~/.zshrc)
echo 'export MAVEN_OPTS="-Xmx4g -XX:+UseContainerSupport"' >> ~/.bashrc
```

### Compilation Fails After Pull

**Problem:** Compilation errors after git pull.

**Solution:** Clean and rebuild:
```bash
mvn clean
bash scripts/dx.sh compile
```

### Maven Dependency Resolution Fails

**Problem:** Cannot resolve dependencies.

**Solution:**
1. Check network connectivity
2. Clear local cache and retry:
```bash
rm -rf ~/.m2/repository/org/yawlfoundation
mvn dependency:resolve
```

---

## Test Issues

### Tests Fail with Parallel Execution

**Problem:** Tests pass individually but fail when run in parallel.

**Cause:** Tests share state via singletons (YEngine, CredentialManagerFactory, etc.)

**Solution:** Add `@Execution(ExecutionMode.SAME_THREAD)` annotation:
```java
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class MyTest {
    // Tests run sequentially, avoiding race conditions
}
```

Tests that need this annotation:
- `MultiModuleIntegrationTest` - YEngine.getInstance()
- `SecurityFixesTest` - CredentialManagerFactory singleton
- Tests using `@Container` static fields

### Test Fails: "No Spring Boot Application"

**Problem:** Spring tests fail with "Unable to start ServletWebServerFactory"

**Solution:** Ensure test uses correct annotations:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyIntegrationTest {
    // ...
}
```

### Testcontainers Not Starting

**Problem:** Docker containers fail to start in tests.

**Solution:**
1. Verify Docker is running: `docker ps`
2. Check Testcontainers configuration:
```properties
# test/resources/testcontainers.properties
testcontainers.reuse.enable=true
```

### Flaky Test Detection

**Problem:** Some tests intermittently fail.

**Solution:** Run flaky test analyzer:
```bash
# Run tests 5 times to detect flakiness
bash scripts/test-analyze-flaky.sh 5

# View report
cat reports/flaky-tests-report.md
```

---

## Script Issues

### "No file watcher found"

**Problem:** `test-watch.sh` fails with "No file watcher found"

**Solution:** Install a file watcher:

**macOS:**
```bash
brew install fswatch
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt install inotify-tools
```

**Linux (RHEL/CentOS):**
```bash
sudo yum install inotify-tools
```

### Script Permission Denied

**Problem:** `bash scripts/dx.sh: Permission denied`

**Solution:** Make scripts executable:
```bash
chmod +x scripts/*.sh
chmod +x scripts/observatory/*.sh
chmod +x docker/scripts/*.sh
```

### dx-cache.sh Fails on macOS

**Problem:** Cache operations fail with stat errors.

**Solution:** The script uses macOS-compatible stat commands. If issues persist:
```bash
# Verify GNU coreutils are not overriding system stat
which stat
# Should be: /usr/bin/stat (macOS native)
```

---

## Docker Issues

### Docker Compose Fails to Start

**Problem:** `docker compose up` fails.

**Solution:**
1. Verify Docker is running: `docker info`
2. Check for port conflicts:
```bash
# Check if ports are in use
lsof -i :8080
lsof -i :9090
```
3. Remove orphaned containers:
```bash
docker compose down --remove-orphans
```

### Container Health Check Fails

**Problem:** Container shows "unhealthy" status.

**Solution:**
1. Check container logs:
```bash
docker logs yawl-engine
```
2. Check health check script:
```bash
docker exec yawl-engine /app/healthcheck.sh
```
3. Verify actuator endpoint:
```bash
curl http://localhost:8080/actuator/health/liveness
```

### Docker Build Out of Memory

**Problem:** Docker build fails with OOM error.

**Solution:** Increase Docker memory limit:
1. Docker Desktop → Settings → Resources
2. Increase Memory to 8GB+
3. Restart Docker

---

## Observatory Issues

### Observatory Takes Too Long

**Problem:** Observatory runs take >10 seconds.

**Solution:** The parallelized version should run in ~4-5 seconds:
```bash
# Ensure you're using the parallel version
head -20 scripts/observatory/observatory.sh
# Should show "(Parallel Version)"
```

### Observatory Fails to Generate Facts

**Problem:** Observatory exits with error during fact generation.

**Solution:**
1. Check Java version (must be 17+):
```bash
java -version
```
2. Run with verbose output:
```bash
bash scripts/observatory/observatory.sh --facts
```
3. Check for syntax errors in source files

---

## Java 25 Issues

### "Unsupported class file version"

**Problem:** Build fails with unsupported class file version.

**Solution:** Verify Java 25 is active:
```bash
java -version
# Should show version 25.x.x

# If wrong version, update JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

### Compact Object Headers Not Working

**Problem:** Expected performance improvement from compact headers not seen.

**Solution:** Verify the flag is active:
```bash
java -XX:+UseCompactObjectHeaders -version
# Should not show warning about experimental flag
```

---

## Git Issues

### Commit Blocked by Hooks

**Problem:** Git commit fails with hook errors.

**Solution:**
1. Check hook output for specific error
2. Fix the issue (usually TODO/FIXME markers or missing tests)
3. Re-run commit

### Uncommitted Changes in Target Directories

**Problem:** `git status` shows untracked files in target/

**Solution:** These should be in .gitignore. If not:
```bash
# Add to .gitignore
echo "target/" >> .gitignore
git add .gitignore
```

---

## Performance Issues

### Slow Build Times

**Problem:** Builds taking >3 minutes.

**Solutions:**

1. **Use DX scripts:**
```bash
bash scripts/dx.sh compile  # Faster than mvn compile
```

2. **Enable caching:**
```bash
bash scripts/dx-cache.sh save
```

3. **Use RAM disk (Linux):**
```bash
sudo bash scripts/dx-setup-tmpfs.sh
```

4. **Check Maven daemon:**
```bash
# Install mvnd for faster builds
brew install mvnd  # macOS
```

### Slow Test Execution

**Problem:** Tests taking >5 minutes.

**Solutions:**

1. **Run only changed module tests:**
```bash
bash scripts/dx.sh test
```

2. **Run specific tests:**
```bash
bash scripts/dx-test-single.sh MyTest
```

3. **Skip integration tests:**
```bash
mvn test -DskipITs
```

---

## Getting Help

1. Check this troubleshooting guide
2. Review [DEVELOPER-BUILD-GUIDE.md](./DEVELOPER-BUILD-GUIDE.md)
3. Check [DX-CHEATSHEET.md](./DX-CHEATSHEET.md)
4. Search existing issues on GitHub
5. Ask in the YAWL community channels
