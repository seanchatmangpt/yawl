# YAWL v6.0.0 Troubleshooting Guide

## Build Issues

### "Cannot resolve dependencies"

**Error Message**:
```
Could not transfer artifact ... from/to central
... repo.maven.apache.org: Temporary failure in name resolution
```

**Cause**: Network unavailable or first build without cached dependencies

**Solutions**:

1. **Check network connectivity**
   ```bash
   ping repo.maven.apache.org
   ping 8.8.8.8
   ```

2. **First build - ensure network available**
   ```bash
   # This will download ~200MB of dependencies
   mvn clean install
   ```

3. **Force dependency refresh**
   ```bash
   mvn clean install -U  # Update snapshots
   ```

4. **Check Maven settings**
   ```bash
   cat ~/.m2/settings.xml  # Verify proxy settings
   mvn help:describe      # Test Maven functionality
   ```

5. **Use offline mode after first build**
   ```bash
   mvn -o clean compile  # Uses cached dependencies only
   ```

### "Out of memory" during build

**Error Message**:
```
OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: GC overhead limit exceeded
```

**Cause**: Insufficient heap memory allocated

**Solutions**:

1. **Increase heap size**
   ```bash
   export MAVEN_OPTS="-Xmx4g"
   mvn clean install
   ```

2. **Update .mvn/jvm.config**
   ```bash
   # Change -Xmx2g to -Xmx4g
   -Xms512m
   -Xmx4g          # Increase to 4GB or 8GB
   -XX:+UseZGC
   --enable-preview
   ```

3. **Skip tests to reduce memory usage**
   ```bash
   mvn clean install -DskipTests
   ```

4. **Build single module at a time**
   ```bash
   mvn -pl yawl-utilities clean install
   mvn -pl yawl-elements clean install
   ```

### "Java version mismatch"

**Error Message**:
```
Error: A JNI error has occurred, please check your installation
invalid class file format or major.minor version mismatch
```

**Cause**: Wrong Java version or mixed compiler versions

**Solutions**:

1. **Verify Java version**
   ```bash
   java -version      # Should show 21.0.10
   javac -version     # Should show 21.0.10
   ```

2. **Set JAVA_HOME explicitly**
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   java -version
   javac -version
   mvn clean compile
   ```

3. **Check Maven's Java**
   ```bash
   mvn -v  # Will show which Java Maven is using
   ```

4. **Remove old compiled classes**
   ```bash
   mvn clean  # Removes target/ directories
   mvn compile
   ```

### "Maven wrapper broken"

**Error Message**:
```
./mvnw: cannot execute: required file not found
```

**Cause**: mvnw script missing .mvn/wrapper/maven-wrapper.jar

**Solutions**:

1. **Use system Maven instead**
   ```bash
   mvn clean install  # Use /opt/maven instead
   ```

2. **Recreate wrapper (requires network)**
   ```bash
   mvn -N io.takari:maven:wrapper -Dmaven=3.9.11
   chmod +x mvnw
   ./mvnw clean install
   ```

3. **Remove wrapper if not needed**
   ```bash
   rm mvnw mvnw.cmd
   # Continue using system Maven
   ```

## Test Issues

### "Tests fail with timeout"

**Error Message**:
```
ERROR Tests run: 5, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 30.123 s
java.util.concurrent.TimeoutException: Future did not complete
```

**Cause**: Tests take too long to execute

**Solutions**:

1. **Increase test timeout**
   ```bash
   mvn test -DargLine="-Dtimeout=300000"
   ```

2. **Run specific test in isolation**
   ```bash
   mvn test -Dtest=SlowTestClass
   ```

3. **Skip slow tests**
   ```bash
   mvn test -DexcludedGroups=slow
   ```

4. **Debug with single thread**
   ```bash
   mvn test -T 1  # Use single thread
   mvn test -Dmaven.surefire.debug  # Enable debug
   ```

5. **Check for deadlocks**
   - Look for threads waiting on locks
   - Check database connections pool
   - Verify no infinite loops

### "Test database connection errors"

**Error Message**:
```
org.hibernate.exc.JDBCConnectionException: Unable to acquire JDBC Connection
```

**Cause**: H2 database not initialized or connection pool exhausted

**Solutions**:

1. **Verify H2 driver**
   ```bash
   mvn dependency:tree | grep h2
   # Should show: h2:2.4.240
   ```

2. **Check database properties**
   ```bash
   # In test/resources/application.properties
   spring.datasource.url=jdbc:h2:mem:testdb
   spring.datasource.driver-class-name=org.h2.Driver
   ```

3. **Increase connection pool size**
   ```properties
   spring.datasource.hikari.maximum-pool-size=10
   spring.datasource.hikari.minimum-idle=2
   ```

4. **Reset database between tests**
   ```java
   @BeforeEach
   void resetDatabase() {
       // Drop and recreate schema
       executeSql("DROP ALL OBJECTS");
       executeSql("CREATE SCHEMA ...");
   }
   ```

### "Tests are flaky" (intermittent failures)

**Solutions**:

1. **Identify the pattern**
   ```bash
   # Run tests multiple times
   for i in {1..5}; do
       mvn test -Dtest=FlakyTest
   done
   ```

2. **Look for race conditions**
   - Concurrent test execution
   - Shared state between tests
   - Database transaction conflicts

3. **Add test isolation**
   ```java
   @BeforeEach
   void setUp() {
       // Create fresh instances
       // Clear caches
       // Reset database
   }
   ```

4. **Disable parallel execution for troubleshooting**
   ```bash
   mvn test -T 1  # Single thread
   ```

## Compilation Errors

### "Cannot find symbol"

**Error Message**:
```
error: cannot find symbol
symbol: class YWorkItem
location: package org.yawlfoundation.yawl.engine
```

**Cause**: Missing dependency or wrong import

**Solutions**:

1. **Check dependency is in pom.xml**
   ```bash
   mvn dependency:tree | grep yawl
   ```

2. **Verify import statement**
   ```java
   import org.yawlfoundation.yawl.engine.YWorkItem;  // Correct
   ```

3. **Rebuild project**
   ```bash
   mvn clean compile
   ```

4. **Refresh IDE**
   - IntelliJ: File > Invalidate Caches > Restart
   - Eclipse: Project > Clean
   - VS Code: Ctrl+Shift+P > Java: Clean Workspace

### "Compilation failure: source is version 21 but maximum is 20"

**Cause**: Java version mismatch

**Solutions**:

1. **Verify compilation config**
   ```bash
   grep -A5 "maven-compiler-plugin" pom.xml
   # Should have: <release>21</release>
   ```

2. **Force Java 21**
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   mvn clean compile
   ```

3. **Check for old compilation files**
   ```bash
   rm -rf target/classes
   mvn clean compile
   ```

### "Error: --enable-preview flag required"

**Cause**: Compilation trying to use Java 21 preview features without flag

**Solutions**:

1. **Verify compiler args**
   ```bash
   grep -B5 -A5 "enable-preview" pom.xml
   ```

2. **Set in pom.xml**
   ```xml
   <compilerArgs>
       <arg>--enable-preview</arg>
       <arg>-Xlint:all</arg>
   </compilerArgs>
   ```

3. **Or set via Maven**
   ```bash
   mvn clean compile -Dcompiler.args="--enable-preview"
   ```

## IDE Issues

### IntelliJ "cannot resolve symbol" in editor

**Solutions**:

1. **Reload Maven projects**
   - Maven > Reload Projects (or Ctrl+Shift+O)

2. **Invalidate caches**
   - File > Invalidate Caches > Restart

3. **Rebuild project**
   - Build > Rebuild Project

4. **Reset compiler**
   - File > Settings > Build, Execution, Deployment > Compiler
   - Click "Reset to Defaults"

### Eclipse "Build path error"

**Solutions**:

1. **Update Maven project**
   - Right-click project > Maven > Update Project (Alt+F5)

2. **Clean build**
   - Project > Clean

3. **Recreate classpath container**
   - Right-click project > Build Path > Configure Build Path
   - Remove and re-add Maven Libraries

4. **Full reset**
   ```bash
   rm -rf .project .classpath .settings/
   mvn eclipse:eclipse
   ```

### VS Code "Cannot find module"

**Solutions**:

1. **Install Extension Pack for Java**
   - Extensions > Search "Extension Pack for Java"

2. **Reload workspace**
   - Ctrl+Shift+P > Developer: Reload Window

3. **Clear cache**
   ```bash
   rm -rf .vscode/
   rm -rf ~/.java/
   ```

4. **Update Maven index**
   - Ctrl+Shift+P > Maven: Update Maven Index

## Git Issues

### "Cannot push to repository"

**Error Message**:
```
fatal: could not read Username for 'https://github.com': terminal prompts disabled
```

**Solutions**:

1. **Set up SSH key**
   ```bash
   ssh-keygen -t ed25519
   cat ~/.ssh/id_ed25519.pub  # Add to GitHub
   ```

2. **Or use HTTPS with token**
   ```bash
   git config --global credential.helper store
   git push  # Will prompt for token
   ```

3. **Update remote URL**
   ```bash
   git remote set-url origin git@github.com:USERNAME/yawl.git
   git push
   ```

### "Unresolved merge conflicts"

**Solutions**:

1. **Abort merge and try again**
   ```bash
   git merge --abort
   git fetch upstream
   git rebase upstream/main
   ```

2. **Resolve conflicts manually**
   ```bash
   # Edit conflicted files
   git status  # See files with conflicts
   # Fix conflicts, then:
   git add .
   git rebase --continue
   ```

3. **Take theirs or ours**
   ```bash
   git checkout --theirs pom.xml  # Take upstream version
   git checkout --ours src/MyFile.java  # Keep local version
   git add .
   git rebase --continue
   ```

## Performance Issues

### Build is slow

**Diagnosis**:
```bash
mvn clean compile -T 1C  # Single thread to find bottleneck
mvn -X clean compile 2>&1 | grep -E "Building|Built"
```

**Solutions**:

1. **Skip tests**
   ```bash
   mvn clean install -DskipTests  # ~3-5 min instead of 5-10 min
   ```

2. **Use offline mode**
   ```bash
   mvn -o clean compile  # Skip network checks
   ```

3. **Increase parallelism**
   ```bash
   mvn -T 1C clean compile  # 1 thread per core
   ```

4. **Increase memory**
   ```bash
   export MAVEN_OPTS="-Xmx4g"
   mvn clean install
   ```

5. **Clean up repository**
   ```bash
   rm -rf ~/.m2/repository
   mvn clean install  # Fresh download
   ```

### IDE is slow

**Solutions**:

1. **Disable indexing**
   - Settings > Editor > General > Optimize Imports > Disable

2. **Exclude unnecessary folders**
   - Right-click folder > Mark Directory as > Excluded

3. **Increase heap**
   - Edit `idea.vmoptions` or `vmoptions`
   - Increase `-Xmx` to 4g or 8g

4. **Disable unnecessary plugins**
   - Settings > Plugins > Disable unused plugins

## Documentation Issues

### Documentation links are broken

**Solutions**:

1. **Check relative paths**
   ```markdown
   [Link](./BUILD.md)  # Relative: correct
   [Link](/BUILD.md)   # Absolute: wrong in docs/
   ```

2. **Use full relative path**
   ```markdown
   [Link](../docs/BUILD.md)  # From root
   [Link](./BUILD.md)        # From docs/
   ```

3. **Verify file exists**
   ```bash
   ls docs/BUILD.md
   ```

## Still Having Issues?

1. **Check logs**
   ```bash
   mvn clean install -X  # Enable debug logging
   cat target/test.log
   ```

2. **Search issues**
   - GitHub Issues
   - Stack Overflow with `yawl` tag

3. **Ask for help**
   - Create GitHub Issue with:
     - Error message (full stack trace)
     - Reproduction steps
     - Environment (Java version, OS, Maven version)
     - What you already tried

4. **Reference these docs**
   - Point to QUICK-START.md for setup help
   - Link to BUILD.md for build questions
   - Reference TESTING.md for test issues

## Performance Baseline Report

### Build Times (Typical Performance)

| Operation | Time | Notes |
|-----------|------|-------|
| `mvn clean compile` | 45-60s | Fast iteration |
| `mvn clean test` | 2-3m | Parallel tests (4 threads) |
| `mvn clean install` | 5-10m | Full build |
| `mvn clean install -DskipTests` | 3-5m | Packaging only |
| First build (network) | 10-15m | ~200MB dependency download |

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| RAM | 4GB | 8GB+ |
| Disk | 2GB | 10GB+ |
| Network | 1MB/s | 5MB/s+ |
| CPU Cores | 2 | 4+ |
| Java | 21+ | Latest LTS |

### Optimization Checklist

- [ ] Java 21+ installed
- [ ] Maven 3.9.11+ installed
- [ ] Network available (first build)
- [ ] 4GB+ RAM available
- [ ] MAVEN_OPTS set to -Xmx2g minimum
- [ ] .mvn/extensions.xml disabled for offline mode
- [ ] No stray Java/Maven processes running

## Success Indicators

Your setup is working correctly if:

```bash
mvn --version                    # Shows Maven 3.9.11+
java -version                    # Shows Java 21.0.10+
mvn clean compile                # Succeeds in <60 seconds
mvn clean test                   # All tests pass
mvn clean install                # JAR/WAR artifacts created
ls yawl-control-panel/target/*.jar  # JAR file exists
```

If all above commands succeed, your development environment is ready!

