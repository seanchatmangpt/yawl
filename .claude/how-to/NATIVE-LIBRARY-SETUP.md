# QLever Embedded Native Library Setup

**Status**: READY FOR IMPLEMENTATION
**Scope**: Complete setup guide for QLever Embedded with Panama FFI

---

## 1. Overview

QLever Embedded uses Java 25's Panama Foreign Function & Memory API for native integration. This document covers platform-specific setup, building from source, and runtime configuration.

### Key Components

- **QLever FFI Bindings**: Panama interface to native QLever engine
- **Embedded Engine**: Thread-safe wrapper with lifecycle management
- **Native Library**: Compiled QLever core with JNI bindings
- **Memory Management**: Automatic resource cleanup with scoped values

### Architecture

```
Java Application
    ↓
QLeverEmbeddedSparqlEngine (Thread-safe wrapper)
    ↓
QLeverFfiBindings (Panama FFI interface)
    ↓
Native Library (qleverjni.so/.dll/.dylib)
    ↓
QLever Core C++ Engine
```

---

## 2. Platform-Specific Setup

### macOS (Apple Silicon & Intel)

#### Prerequisites
```bash
# Install Xcode Command Line Tools
xcode-select --install

# Install dependencies via Homebrew
brew install cmake boost openssl git

# Verify Java 25
java -version
# Expected: Java 25.x
```

#### Library Path Configuration
```bash
# Add to ~/.zshrc or ~/.bash_profile
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH="$JAVA_HOME/bin:$PATH"

# For Apple Silicon (M1/M2/M3)
export DYLD_LIBRARY_PATH="$JAVA_HOME/lib/server:$DYLD_LIBRARY_PATH"

# For Intel Mac
export DYLD_LIBRARY_PATH="$JAVA_HOME/lib/server:$DYLD_LIBRARY_PATH"

# Add QLever native library location
export DYLD_LIBRARY_PATH="$PWD/yawl-qlever/src/main/resources/native:$DYLD_LIBRARY_PATH"
```

### Linux (Ubuntu/Debian/CentOS)

#### Prerequisites
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y build-essential cmake git libboost-all-dev libssl-dev

# CentOS/RHEL
sudo yum groupinstall "Development Tools"
sudo yum install cmake boost-devel openssl-devel git

# Verify Java 25 (JDK 25 required)
# Use Java Development Kit (JDK) 25 for Panama FFI
```

#### Library Path Configuration
```bash
# Add to ~/.bashrc or ~/.profile
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64  # Adjust for your installation
export PATH="$JAVA_HOME/bin:$PATH"

# Set library path
export LD_LIBRARY_PATH="$JAVA_HOME/lib/server:$LD_LIBRARY_PATH"

# Add QLever native library location
export LD_LIBRARY_PATH="$PWD/yawl-qlever/src/main/resources/native:$LD_LIBRARY_PATH"
```

### Windows

#### Prerequisites
```powershell
# Install Visual Studio 2022 (Community Edition)
# Select "Desktop development with C++" workload

# Install vcpkg for package management
git clone https://github.com/microsoft/vcpkg.git
cd vcpkg
./bootstrap-vcpkg.bat

# Install dependencies
./vcpkg install boost openssl

# Download and install JDK 25 from Oracle or Adoptium
```

#### Environment Variables
```powershell
# Set in System Properties → Advanced → Environment Variables
JAVA_HOME=C:\Program Files\Java\jdk-25
PATH=%JAVA_HOME%\bin;%PATH%

# Set library path
set QLIVER_NATIVE_LIBRARY=C:\path\to\yawl-qlever\src\main\resources\native
set PATH=%QLIVER_NATIVE_LIBRARY%;%PATH%
```

---

## 3. Building from Source

### Build QLever Native Library

#### Step 1: Clone QLever Repository
```bash
git clone https://github.com/ad-freiburg/qlever.git
cd qlever
git checkout v5.0.0  # Use appropriate version
```

#### Step 2: Configure CMake
```bash
# Create build directory
mkdir build && cd build

# Configure with JNI support
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DWITH_JNI=ON \
  -DCMAKE_INSTALL_PREFIX=/usr/local/qlever

# For macOS
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DWITH_JNI=ON \
  -DCMAKE_INSTALL_PREFIX=/usr/local/qlever \
  -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"  # Universal binary
```

#### Step 3: Build and Install
```bash
# Build with multiple jobs
cmake --build . -j$(nproc)

# Install
cmake --install .

# Verify installation
ls /usr/local/qlever/lib/libqlever*
```

#### Step 4: Create JNI Wrapper
```bash
# Create JNI bindings directory
mkdir -p yawl-qlever/src/main/resources/native

# Copy compiled library to Java resources
cp /usr/local/qlever/lib/libqleverjni.so \
   yawl-qlever/src/main/resources/native/libqleverjni.so

# Create JNI header from Java class
javac -h yawl-qlever/src/main/resources/native \
  -cp yawl-qlever/src/main/java \
  yawl-qlever/src/main/java/org/yawlfoundation/yawl/qlever/QLeverFfiBindings.java
```

### Build with Maven

#### Step 1: Update pom.xml
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>build-native</id>
            <phase>generate-resources</phase>
            <goals>
                <goal>exec</exec>
            </goals>
            <configuration>
                <executable>bash</executable>
                <arguments>
                    <argument>scripts/build-native.sh</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Step 2: Build Script (scripts/build-native.sh)
```bash
#!/bin/bash
set -e

echo "Building QLever native library..."

# Detect platform
OS=$(uname -s)
ARCH=$(uname -m)

case $OS in
    Linux)
        LIBEXT="so"
        CMAKE_OPTS="-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DWITH_JNI=ON"
        ;;
    Darwin)
        LIBEXT="dylib"
        CMAKE_OPTS="-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DWITH_JNI=ON -DCMAKE_OSX_ARCHITECTURES=x86_64;arm64"
        ;;
    MINGW*|CYGWIN*|Windows_NT)
        LIBEXT="dll"
        CMAKE_OPTS="-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DWITH_JNI=ON -A x64"
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

echo "Platform: $OS $ARCH"

# Build QLever
cd /tmp
git clone https://github.com/ad-freiburg/qlever.git
cd qlever
git checkout v5.0.0

mkdir build && cd build
cmake .. $CMAKE_OPTS
cmake --build . -j$(nproc)
cmake --install .

# Copy to project
cp /usr/local/qlever/lib/libqleverjni.$LIBEXT \
   ${PROJECT_ROOT}/yawl-qlever/src/main/resources/native/libqleverjni.$LIBEXT

echo "Native library built successfully"
```

---

## 4. Runtime Configuration

### Java System Properties

```bash
# Minimal configuration
java -Djava.library.path="./yawl-qlever/src/main/resources/native" \
     -jar your-app.jar

# Comprehensive configuration
java -Djava.library.path="./yawl-qlever/src/main/resources/native" \
     -Dqlever.native.library.path="/usr/local/qlever/lib" \
     -Dqlever.engine.memory.limit=1073741824 \  # 1GB
     -Dqlever.query.timeout=30000 \              # 30 seconds
     -Dqlever.log.level=INFO \
     -jar your-app.jar
```

### Configuration File (qlever.properties)

```properties
# qlever.properties
native.library.path=./yawl-qlever/src/main/resources/native
engine.memory.limit=1073741824
query.timeout=30000
log.level=INFO
max.concurrent.queries=100
cache.size=512000000
```

Load in Java:
```java
Properties props = new Properties();
try (InputStream input = getClass().getResourceAsStream("/qlever.properties")) {
    props.load(input);
    String libPath = props.getProperty("native.library.path");
    System.setProperty("java.library.path", libPath);
} catch (IOException e) {
    // Handle error
}
```

### Maven Plugin for Testing

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <systemPropertyVariables>
            <java.library.path>${basedir}/src/main/resources/native</java.library.path>
            <qlever.native.library.path>${basedir}/src/main/resources/native</qlever.native.library.path>
            <qlever.query.timeout>10000</qlever.query.timeout>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

---

## 5. Verification Test Commands

### Basic Connectivity Test

```java
@Test
public void testNativeLibraryLoading() {
    QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();

    try {
        engine.initialize();
        assertTrue(engine.isInitialized());

        // Test basic query
        QLeverResult result = engine.executeQuery("SELECT * WHERE { ?s ?p ?o } LIMIT 1");
        assertNotNull(result);

    } catch (QLeverFfiException e) {
        fail("Failed to initialize engine: " + e.getMessage());
    } finally {
        engine.shutdown();
    }
}
```

### Memory Management Test

```java
@Test
public void testMemoryManagement() {
    QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();

    try {
        engine.setMemoryLimit(100 * 1024 * 1024); // 100MB
        engine.initialize();

        // Load test data
        String testData = "<urn:test> <urn:test> <urn:test> .";
        QLeverResult loadResult = engine.loadRdfData(testData, "TURTLE");
        assertTrue(loadResult.isSuccess());

        // Check memory usage
        long memoryUsage = engine.getCurrentMemoryUsage();
        assertTrue(memoryUsage > 0);

    } catch (QLeverFfiException e) {
        fail("Memory management test failed: " + e.getMessage());
    } finally {
        engine.shutdown();
    }
}
```

### Performance Benchmark Test

```java
@Test
public void testPerformance() {
    QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();

    try {
        engine.initialize();
        engine.setQueryTimeout(5000); // 5 seconds

        String query = "SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }";

        long startTime = System.nanoTime();
        QLeverResult result = engine.executeQuery(query);
        long duration = System.nanoTime() - startTime;

        assertTrue(result.isSuccess());
        assertTrue(duration < 5_000_000_000L); // < 5 seconds

    } catch (QLeverFfiException e) {
        fail("Performance test failed: " + e.getMessage());
    } finally {
        engine.shutdown();
    }
}
```

### CLI Test Script

```bash
#!/bin/bash
# test-native-library.sh

echo "=== Native Library Verification ==="

# Set Java library path
export JAVA_LIBRARY_PATH="./yawl-qlever/src/main/resources/native"
export LD_LIBRARY_PATH="./yawl-qlever/src/main/resources/native:$LD_LIBRARY_PATH"
export DYLD_LIBRARY_PATH="./yawl-qlever/src/main/resources/native:$DYLD_LIBRARY_PATH"

# Test native library loading
echo "1. Testing native library loading..."
java -cp "yawl-qlever/target/classes:yawl-qlever/target/dependency/*" \
     org.yawlfoundation.yawl.qlever.QLeverFfiBindingsTest

# Test embedded engine
echo ""
echo "2. Testing embedded engine..."
java -cp "yawl-qlever/target/classes:yawl-qlever/target/dependency/*" \
     org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngineTest

echo "=== Verification Complete ==="
```

---

## 6. Troubleshooting Common Errors

### UnsatisfiedLinkError

#### Error Message
```
java.lang.UnsatisfiedLinkError: no qleverjni in java.library.path
```

#### Causes and Solutions

1. **Library Path Not Set**
   ```bash
   # Verify library path is set
   echo $LD_LIBRARY_PATH    # Linux
   echo $DYLD_LIBRARY_PATH  # macOS
   echo $PATH               # Windows

   # Set correct path
   export LD_LIBRARY_PATH="/path/to/native/libraries:$LD_LIBRARY_PATH"
   ```

2. **Wrong Library Name**
   ```bash
   # Check available libraries
   ls -la /path/to/native/libraries/

   # Expected names:
   # - libqleverjni.so (Linux)
   # - libqleverjni.dylib (macOS)
   # - qleverjni.dll (Windows)
   ```

3. **Architecture Mismatch**
   ```bash
   # Check system architecture
   uname -m

   # Ensure library matches architecture
   # x86_64, arm64, aarch64
   ```

#### Diagnostic Commands

```bash
# Check if library can be loaded
strace -e open java -jar app.jar  # Linux
dtrace -n 'syscall::open: { printf("%s", copyinstr(arg0)); }' java -jar app.jar  # macOS
```

### Symbol Not Found

#### Error Message
```
java.lang.UnsatisfiedLinkError: /path/to/libqleverjni.so: undefined symbol: QLever_initialize
```

#### Causes and Solutions

1. **Missing Dependencies**
   ```bash
   # Check dependencies on Linux
   ldd /path/to/libqleverjni.so

   # Install missing dependencies
   sudo apt-get install libboost-all-dev libssl-dev
   ```

2. **Wrong Build Configuration**
   ```bash
   # Rebuild with proper configuration
   cmake .. -DWITH_JNI=ON -DBUILD_SHARED_LIBS=ON
   ```

3. **Version Mismatch**
   ```bash
   # Check QLever version
   strings /path/to/libqleverjni.so | grep QLever

   # Ensure JNI functions are present
   nm -D /path/to/libqleverjni.so | grep QLever_
   ```

#### Diagnostic Commands

```bash
# Check symbols
nm -D /path/to/libqleverjni.so | grep QLever

# Check for undefined symbols
ldd /path/to/libqleverjni.so

# On macOS
otool -L /path/to/libqleverjni.dylib
```

### Memory Allocation Errors

#### Error Message
```
java.lang.OutOfMemoryError: Cannot create native memory segment
```

#### Solutions

1. **Increase Memory Limits**
   ```java
   engine.setMemoryLimit(2 * 1024 * 1024 * 1024); // 2GB
   ```

2. **Check Resource Cleanup**
   ```java
   try {
       engine.initialize();
       // Perform operations
   } finally {
       engine.shutdown(); // Important!
   }
   ```

3. **Monitor Memory Usage**
   ```java
   long usage = engine.getCurrentMemoryUsage();
   if (usage > engine.getMemoryLimit() * 0.9) {
       // Handle memory pressure
   }
   ```

### Panama FFI Specific Errors

#### Error Message
```
java.lang.NoClassDefFoundError: jdk/internal/foreign/abi/UpcallStubs
```

#### Causes and Solutions

1. **Java Version Not 25+**
   ```bash
   # Verify Java 25+
   java -version
   # Must show Java 25.x

   # Install Java 25
   # Ubuntu/Debian
   sudo apt install openjdk-25-jdk

   # macOS
   brew install openjdk@25

   # Windows
   # Download from Adoptium or Oracle
   ```

2. **Module Path Issues**
   ```bash
   # Use correct module path
   java --module-path /path/to/jdk-25/modules --add-modules=jdk.incubator.vector \
        -jar app.jar
   ```

#### Panama Configuration

```bash
# Check Panama options
java -XX:+ShowHiddenFrames -version

# Enable Panama debugging
java -Dforeign.restricted=allow -Djdk.internal.foreign.abi.linux=x64 \
     -jar app.jar
```

### Configuration Issues

#### Error Message
```
java.io.IOException: Cannot find native library in path
```

#### Solutions

1. **Verify Configuration**
   ```java
   // Check current library path
   String path = System.getProperty("java.library.path");
   System.out.println("Library path: " + path);
   ```

2. **Use Fallback Path**
   ```java
   public class QLeverLibraryLoader {
       public static void loadLibrary() throws QLeverFfiException {
           String[] possiblePaths = {
               "./native",
               "./src/main/resources/native",
               "/usr/local/qlever/lib",
               System.getProperty("java.library.path")
           };

           for (String path : possiblePaths) {
               try {
                   System.loadLibrary("qleverjni");
                   return;
               } catch (UnsatisfiedLinkError e) {
                   // Try next path
               }
           }

           throw new QLeverFfiException("Cannot load QLever native library");
       }
   }
   ```

---

## 7. CI/CD Integration Patterns

### GitHub Actions Example

```yaml
# .github/workflows/qlever-native.yml
name: QLever Native Build

on:
  push:
    paths:
      - 'yawl-qlever/**'
      - 'scripts/build-native.sh'
  pull_request:
    paths:
      - 'yawl-qlever/**'

jobs:
  build-native:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        java-version: ['25']

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${{ matrix.java-version }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java-version }}
          distribution: 'temurin'

      - name: Build Native Library
        run: |
          chmod +x scripts/build-native.sh
          ./scripts/build-native.sh

      - name: Test Native Integration
        run: |
          cd yawl-qlever
          mvn test -P native-tests

      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: native-libraries-${{ matrix.os }}
          path: yawl-qlever/src/main/resources/native/
```

### Jenkins Pipeline Example

```groovy
// Jenkinsfile
pipeline {
    agent any

    environment {
        JAVA_HOME = tool 'jdk25'
        MAVEN_HOME = tool 'maven'
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"
    }

    stages {
        stage('Build Native') {
            steps {
                sh 'chmod +x scripts/build-native.sh'
                sh './scripts/build-native.sh'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test -P native-tests -P coverage'
            }
            post {
                always {
                    publishTestResults testResultsPattern: '**/surefire-reports/*.xml'
                    jacoco()
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh 'mvn deploy -DskipTests'
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
```

### Docker Integration

#### Dockerfile Example

```dockerfile
# Dockerfile
FROM openjdk:25-jdk-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    cmake \
    build-essential \
    libboost-all-dev \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY . /app
WORKDIR /app

# Build native library
RUN chmod +x scripts/build-native.sh \
    && ./scripts/build-native.sh

# Set environment variables
ENV JAVA_LIBRARY_PATH=/app/yawl-qlever/src/main/resources/native
ENV LD_LIBRARY_PATH=/app/yawl-qlever/src/main/resources/native

# Run application
CMD ["java", "-jar", "app.jar"]
```

#### Docker Compose Example

```yaml
# docker-compose.yml
version: '3.8'

services:
  qlever-engine:
    build: .
    environment:
      - JAVA_LIBRARY_PATH=/app/native
      - QLEVER_NATIVE_LIBRARY_PATH=/app/native
      - QLEVER_ENGINE_MEMORY_LIMIT=1073741824
    volumes:
      - ./data:/app/data
      - ./logs:/app/logs
    ports:
      - "8080:8080"

  test-runner:
    build: .
    command: mvn test -P native-tests
    volumes:
      - ./reports:/app/reports
```

### Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "=== Running Native Library Pre-commit Checks ==="

# Check native library exists
if [ ! -f "yawl-qlever/src/main/resources/native/libqleverjni.so" ] && \
   [ ! -f "yawl-qlever/src/main/resources/native/libqleverjni.dylib" ] && \
   [ ! -f "yawl-qlever/src/main/resources/native/qleverjni.dll" ]; then
    echo "❌ Native library not found"
    echo "Run: ./scripts/build-native.sh"
    exit 1
fi

# Verify library can be loaded
cd yawl-qlever
mvn test -Dtest=QLeverFfiBindingsTest -q
if [ $? -ne 0 ]; then
    echo "❌ Native library tests failed"
    exit 1
fi

echo "✅ Native library checks passed"
```

---

## 8. Performance Tuning

### Memory Configuration

```java
// Optimize memory usage
QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();

// Set appropriate memory limits
engine.setMemoryLimit(2 * 1024 * 1024 * 1024); // 2GB
engine.setQueryTimeout(30000); // 30 seconds

// Enable memory monitoring
engine.setMemoryLimit(500 * 1024 * 1024); // 500MB
```

### Concurrency Settings

```java
// Use virtual threads for better scalability
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

for (int i = 0; i < 1000; i++) {
    String query = "SELECT * WHERE { ?s <urn:id> \"" + i + "\" }";
    CompletableFuture<QLeverResult> future = engine.executeQueryAsync(query);

    future.thenAccept(result -> {
        // Handle result
    });
}
```

### Cache Configuration

```properties
# qlever.properties
cache.size=1073741824  # 1GB cache
cache.ttl=3600          # 1 hour TTL
cache.max.entries=10000 # Max cache entries
```

```java
// Enable caching in application
engine.setCacheSize(1000000000); // 1GB
```

---

## 9. Security Considerations

### Library Integrity

```bash
# Verify library checksum
sha256sum yawl-qlever/src/main/resources/native/libqleverjni.so

# Sign the library
gpg --detach-sign --armor libqleverjni.so

# Verify signature
gpg --verify libqleverjni.so.asc
```

### Runtime Protection

```java
// Enable security manager
System.setSecurityManager(new SecurityManager());

// Check permissions before native calls
if (System.getSecurityManager() != null) {
    System.getSecurityManager().checkPermission(
        new RuntimePermission("accessDeclaredMembers")
    );
}
```

### Environment Variables Security

```bash
# Secure environment variables
export QLEVER_NATIVE_LIBRARY_PATH="/secure/path/to/libs"
export QLEVER_CONFIG_PATH="/secure/config"
unset QLEVER_DEBUG  # Disable debugging in production
```

---

## 10. Debugging and Profiling

### Debug Mode

```java
// Enable debug output
System.setProperty("qlever.debug", "true");
System.setProperty("qlever.trace.native", "true");

// Enable verbose GC for memory issues
java -Xlog:gc*=info:file=qlever-gc.log \
     -Djava.library.path=./native \
     -jar app.jar
```

### Profiling Tools

```bash
# Linux perf
perf record -g java -Djava.library.path=./native -jar app.jar
perf report

# Java Flight Recorder
java -XX:+UnlockCommercialFeatures \
     -XX:+FlightRecorder \
     -XX:FlightRecorderOptions=defaultrecording=true,settings=profile,filename=jfr.jfr \
     -Djava.library.path=./native \
     -jar app.jar

# VisualVM
visualvm --jdkhome /path/to/jdk-25 \
         -Djava.library.path=./native \
         --pid <java-pid>
```

### Native Debugging

```bash
# GDB debugging
gdb --args java -Djava.library.path=./native -jar app.jar

# (gdb) break QLever_initialize
# (gdb) run
# (gdb) bt
```

---

## 11. References

### Official Documentation

- [QLever GitHub Repository](https://github.com/ad-freiburg/qlever)
- [Panama Foreign Function & Memory API](https://openjdk.org/jeps/454)
- [JNI Documentation](https://docs.oracle.com/javase/25/docs/technotes/guides/jni/)

### Related Projects

- [YAWL Workflow Engine](https://github.com/yawlfoundation/yawl)
- [Apache Jena SPARQL Engine](https://jena.apache.org/documentation/query/)
- [Virtuoso RDF Store](https://virtuoso.openlinksw.com/)

### Build Tools

- [CMake Documentation](https://cmake.org/documentation/)
- [Maven Native Plugin](https://www.mojohaus.org/native-maven-plugin/)
- [Gradle Native Plugin](https://github.com/gradle-natives/gradle-native)

---

**Next Steps**:
1. Follow platform-specific setup instructions
2. Build native library using cmake
3. Configure runtime paths
4. Run verification tests
5. Integrate with CI/CD pipeline

**Support**: For issues, check GitHub issues or create new ones with detailed logs.