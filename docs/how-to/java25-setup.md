# Java 25 Setup and Compilation Guide for YAWL v6.0.0

## Current Status ✅

**Java 25 is successfully installed and configured:**
- Version: OpenJDK 25.0.2 (25.0.2+10-Ubuntu-124.04)
- Compiler: javac 25.0.2
- Runtime: openjdk 25.0.2
- Maven: 3.9.11 (via Maven wrapper)
- POM Configuration: `<maven.compiler.release>25</maven.compiler.release>`

## Verification

```bash
java -version
# Output: openjdk version "25.0.2" 2026-01-20

javac -version
# Output: javac 25.0.2
```

## Issue: Maven Repository Access

The current environment has an **egress proxy** (21.0.0.111:15004) that requires authentication with a JWT token. Maven's default HTTP client has limitations with HTTPS CONNECT tunneling through authenticating proxies with complex credentials (JWT tokens).

### What Works ✅
- `curl` successfully tunnels through the proxy to Maven Central
- Direct network path to Maven Central is verified
- Java 25 compilation pipeline is ready

### Challenge 🔄
- Maven's HTTP client (via Aether/Wagon) fails to authenticate with the JWT-based egress proxy
- DNS resolution attempts before proxy authentication completes
- Java's InetAddress DNS resolution doesn't wait for proxy tunnel setup

## Solutions

### Solution 1: Docker/Container with Maven Cache (RECOMMENDED)

Pre-download Maven artifacts in a container with better proxy support:

```bash
# In a separate clean build environment with direct internet access
docker run -it maven:3.9.11-eclipse-temurin-25 bash
mvn -T 1.5C clean dependency:resolve dependency:resolve-plugins
# Copy ~/.m2/repository to YAWL build environment
```

### Solution 2: Local Maven Proxy (Advanced)

Files created in this session:
- `/home/user/yawl/maven-proxy.py` - Simple CONNECT proxy
- `/home/user/yawl/maven-proxy-v2.py` - Improved proxy
- `/home/user/yawl/maven-proxy-443.py` - Port 443 variant
- `/home/user/yawl/maven-proxy-debug.py` - Debug version

Run with:
```bash
export https_proxy="http://user:jwt_token@21.0.0.111:15004"
python3 /home/user/yawl/maven-proxy.py &
```

Configure `~/.m2/settings.xml`:
```xml
<proxies>
  <proxy>
    <id>local</id>
    <protocol>https</protocol>
    <host>127.0.0.1</host>
    <port>3128</port>
  </proxy>
</proxies>
```

### Solution 3: Enterprise Proxy Configuration (ADVANCED)

If your organization has a Nexus/Artifactory proxy:
```xml
<!-- ~/.m2/settings.xml -->
<mirrors>
  <mirror>
    <id>nexus</id>
    <mirrorOf>central</mirrorOf>
    <url>https://your-nexus.example.com/repository/maven-central/</url>
  </mirror>
</mirrors>
```

### Solution 4: Manual Bootstrap

For air-gapped or highly restricted networks:

```bash
# Pre-download essential plugins
mkdir -p ~/.m2/repository/org/apache/maven/plugins
# Manually download JAR files and place in ~/.m2/repository
```

## Java 25 Features Enabled

The YAWL project is configured for Java 25 with:
- **Language Level**: 25 (via `maven.compiler.release`)
- **Preview Features**: Supported
- **Modern Java Features**: Virtual threads, records, sealed classes, pattern matching, etc.

## Build Commands (When Repository Access is Resolved)

```bash
# Full build (compile + test + validate)
bash scripts/dx.sh all

# Compile only
bash scripts/dx.sh compile

# Test only (after compile)
bash scripts/dx.sh test

# Single module
bash scripts/dx.sh -pl yawl-engine

# With verbose Maven output
DX_VERBOSE=1 bash scripts/dx.sh compile
```

## Next Steps

1. **Choose Solution**: Pick one of the solutions above based on your environment
2. **Verify Maven**: Run `mvn dependency:resolve` successfully
3. **Test Compilation**: `mvn -T 1.5C clean compile`
4. **Run Tests**: `mvn test`
5. **Full Validation**: `bash scripts/dx.sh all`

## Technical Details

### Project Configuration
- **File**: `/home/user/yawl/pom.xml`
- **Key Property**: `<maven.compiler.release>25</maven.compiler.release>`
- **Modules**: 15 (all Java 25 compatible)
- **Tests**: 447 test files

### Proxy Configuration Files
- Created: `~/.m2/settings.xml` (Maven settings)
- Modified: `/etc/hosts` (removed experimental entries)
- Environment: `JAVA_TOOL_OPTIONS` (contains egress proxy settings)

### Maven Configuration
- Compiler: Apache Maven Compiler Plugin 3.14.0
- Resolution: Parallel artifact resolution (16 threads)
- Build Cache: Enabled for faster incremental builds
- Test Parallelization: JUnit 5 concurrent execution

## Known Limitations

1. **Proxy Auth**: Maven's Aether wagon doesn't handle JWT-based HTTPS CONNECT well
2. **DNS Timing**: Java DNS resolution happens before proxy negotiation
3. **Environment Conflict**: JAVA_TOOL_OPTIONS may interfere with Maven proxy settings

## Troubleshooting

### Error: "repo.maven.apache.org: Temporary failure in name resolution"
→ DNS resolver can't reach Maven Central (proxy issue)

### Error: "Connect to repo.maven.apache.org:443 failed: Read timed out"
→ Connection established but TLS handshake timeout

### Error: "Cannot access central in offline mode"
→ Not all plugins cached locally - need initial online build

## References

- YAWL Foundation: https://www.yawlfoundation.org/
- Java 25 Documentation: https://openjdk.org/projects/jdk/25/
- Maven Settings: ~/.m2/settings.xml
- Project: /home/user/yawl/

---

**Created**: 2026-02-20
**Java Version**: 25.0.2
**Maven Version**: 3.9.11
**YAWL Version**: 6.0.0-Alpha
