# Maven-First Implementation Guide

**Quick Reference for YAWL v5.2 Maven Transition**

---

## Quick Start (5 Minutes)

### Step 1: Install Maven Wrapper

```bash
cd /home/user/yawl
chmod +x docs/architecture/maven-module-templates/mvnw-setup.sh
./docs/architecture/maven-module-templates/mvnw-setup.sh
```

### Step 2: Build with Maven

```bash
# Full build
./mvnw clean install

# Parallel build (faster)
./mvnw clean install -T 1C

# Production build with security scan
./mvnw clean install -Pprod
```

### Step 3: Run Tests

```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw verify

# With coverage report
./mvnw verify jacoco:report
open target/site/jacoco/index.html
```

### Step 4: Build Docker Images

```bash
# Build all service images
./mvnw jib:dockerBuild

# Build specific service
./mvnw jib:dockerBuild -pl resource-service

# Push to registry
./mvnw jib:build -Pprod
```

---

## Module Creation Checklist

### Creating a New Core Module

**Example: `yawl-scheduling`**

1. **Create directory:**
```bash
mkdir -p yawl-scheduling/src/main/java/org/yawlfoundation/yawl/scheduling
mkdir -p yawl-scheduling/src/test/java/org/yawlfoundation/yawl/scheduling
```

2. **Copy POM template:**
```bash
cp docs/architecture/maven-module-templates/yawl-engine-pom-example.xml \
   yawl-scheduling/pom.xml
```

3. **Edit POM:**
```xml
<artifactId>yawl-scheduling</artifactId>
<name>YAWL Scheduling</name>
<description>Task scheduling and timing</description>
```

4. **Add to parent POM:**
```xml
<!-- /home/user/yawl/pom.xml -->
<modules>
    ...
    <module>yawl-scheduling</module>
</modules>
```

5. **Add to dependency management:**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-scheduling</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

6. **Build:**
```bash
./mvnw install -pl yawl-scheduling
```

### Creating a New Service Module

**Example: `notification-service`**

1. **Create directory:**
```bash
mkdir -p notification-service/src/main/java/org/yawlfoundation/yawl/notification
mkdir -p notification-service/src/main/resources
```

2. **Copy Spring Boot POM template:**
```bash
cp docs/architecture/maven-module-templates/resource-service-pom-example.xml \
   notification-service/pom.xml
```

3. **Edit POM:**
```xml
<artifactId>notification-service</artifactId>
<name>YAWL Notification Service</name>
<description>Email and webhook notifications</description>

<properties>
    <main.class>org.yawlfoundation.yawl.notification.NotificationServiceApplication</main.class>
</properties>
```

4. **Create Spring Boot application:**
```java
// notification-service/src/main/java/org/yawlfoundation/yawl/notification/NotificationServiceApplication.java
package org.yawlfoundation.yawl.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

5. **Create application.yml:**
```yaml
# notification-service/src/main/resources/application.yml
spring:
  application:
    name: notification-service
  threads:
    virtual:
      enabled: true

server:
  port: 8083

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

6. **Add to parent POM and build:**
```bash
./mvnw install -pl notification-service
./mvnw jib:dockerBuild -pl notification-service
```

---

## Common Commands

### Build Commands

```bash
# Clean build (all modules)
./mvnw clean install

# Build specific module
./mvnw install -pl yawl-engine

# Build module and dependencies
./mvnw install -pl yawl-engine -am

# Build without tests
./mvnw install -DskipTests

# Parallel build (1 thread per CPU core)
./mvnw clean install -T 1C

# Production build (optimized, security scan)
./mvnw clean install -Pprod
```

### Test Commands

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=YEngineTest

# Run specific test method
./mvnw test -Dtest=YEngineTest#testLaunchCase

# Integration tests
./mvnw verify

# Skip tests
./mvnw install -DskipTests

# Skip integration tests
./mvnw install -DskipITs

# Debug tests (remote debugger on port 5005)
./mvnw test -Dmaven.surefire.debug
```

### Coverage Commands

```bash
# Generate coverage report
./mvnw verify jacoco:report

# View report
open target/site/jacoco/index.html

# Check coverage threshold (70% minimum)
./mvnw verify jacoco:check
```

### Dependency Commands

```bash
# Dependency tree
./mvnw dependency:tree

# Dependency tree for specific module
./mvnw dependency:tree -pl yawl-engine

# Analyze unused dependencies
./mvnw dependency:analyze

# Check for updates
./mvnw versions:display-dependency-updates

# Check for plugin updates
./mvnw versions:display-plugin-updates
```

### Docker Commands

```bash
# Build Docker image (local)
./mvnw jib:dockerBuild

# Build specific service
./mvnw jib:dockerBuild -pl resource-service

# Build and push to registry
./mvnw jib:build -Pprod

# Export as TAR
./mvnw jib:buildTar

# Multi-arch build
./mvnw jib:build -Djib.from.platforms=linux/amd64,linux/arm64
```

### Security Commands

```bash
# OWASP Dependency Check
./mvnw verify -Psecurity-scan

# View security report
open target/dependency-check-report.html

# SonarQube analysis
./mvnw sonar:sonar \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=$SONAR_TOKEN
```

### Profile Commands

```bash
# Java 21 (default)
./mvnw clean install

# Java 24 (preview features)
./mvnw clean install -Pjava-24

# Java 25 (virtual threads)
./mvnw clean install -Pjava-25

# Development profile (debug symbols)
./mvnw clean install -Pdev

# Production profile (optimized, security)
./mvnw clean install -Pprod

# Performance benchmarks
./mvnw verify -Pperformance
```

---

## Troubleshooting

### Problem: "Cannot find parent POM"

**Solution:**
```bash
# Build parent first
./mvnw install -N

# Then build modules
./mvnw install
```

### Problem: "Dependency conflicts"

**Solution:**
```bash
# Check dependency tree
./mvnw dependency:tree -Dverbose

# Force specific version
mvn dependency:tree -Dincludes=com.squareup.okhttp3:okhttp

# Exclude transitive dependency
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Problem: "Out of memory during build"

**Solution:**
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx4g"

# Or set in ~/.mavenrc
echo "export MAVEN_OPTS=\"-Xmx4g -XX:+UseG1GC\"" >> ~/.mavenrc
```

### Problem: "Tests fail on Java 21"

**Solution:**
```bash
# Add module opens for Hibernate
export MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED"

# Or add to surefire plugin
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            --add-opens java.base/java.lang=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

### Problem: "Docker build fails with permission denied"

**Solution:**
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Or use sudo
sudo ./mvnw jib:dockerBuild

# Or use Jib without Docker
./mvnw jib:build  # Pushes directly to registry
```

### Problem: "Slow builds"

**Solution:**
```bash
# Use parallel execution
./mvnw clean install -T 1C

# Skip tests during development
./mvnw install -DskipTests

# Use incremental compilation
./mvnw compile  # Only changed files

# Clean specific module
./mvnw clean -pl yawl-engine
```

---

## CI/CD Integration

### GitHub Actions

**Setup:**
```bash
# Copy workflow template
cp docs/architecture/maven-module-templates/github-actions-maven.yml \
   .github/workflows/maven-build.yml

# Commit and push
git add .github/workflows/maven-build.yml
git commit -m "Add Maven build workflow"
git push
```

**Required Secrets:**
- `SONAR_TOKEN` (SonarQube)
- `SLACK_WEBHOOK_URL` (Notifications)
- `GITHUB_TOKEN` (Auto-generated)

### GitLab CI/CD

**Setup:**
```bash
# Copy pipeline template
cp docs/architecture/maven-module-templates/gitlab-ci-maven.yml \
   .gitlab-ci.yml

# Commit and push
git add .gitlab-ci.yml
git commit -m "Add Maven CI/CD pipeline"
git push
```

**Required Variables:**
- `SONAR_URL` (SonarQube URL)
- `SONAR_TOKEN` (SonarQube token)
- `CI_REGISTRY_USER` (Docker registry user)
- `CI_REGISTRY_PASSWORD` (Docker registry password)

---

## Performance Optimization

### Build Time Targets

| Task | Target | Command |
|------|--------|---------|
| Clean build (cold) | <20 min | `./mvnw clean install` |
| Clean build (warm cache) | <10 min | `./mvnw clean install -T 1C` |
| Incremental build | <2 min | `./mvnw install` |
| Test execution | <5 min | `./mvnw test -T 1C` |
| Docker build | <3 min | `./mvnw jib:dockerBuild` |

### Optimization Tips

1. **Use parallel builds:**
```bash
./mvnw clean install -T 1C  # 1 thread per core
```

2. **Enable Maven caching:**
```bash
# GitHub Actions (automatic with setup-java@v4 cache: 'maven')
# GitLab (add to .gitlab-ci.yml cache section)
```

3. **Skip unnecessary plugins:**
```bash
./mvnw install -Dmaven.javadoc.skip=true -Dmaven.source.skip=true
```

4. **Use offline mode (when dependencies cached):**
```bash
./mvnw install -o
```

5. **Profile-specific builds:**
```bash
# Development (skip security scans)
./mvnw install -Pdev

# Production (full validation)
./mvnw install -Pprod
```

---

## Migration from Ant

### Command Mapping

| Ant Command | Maven Command | Notes |
|-------------|---------------|-------|
| `ant compile` | `./mvnw compile` | Compile source |
| `ant unitTest` | `./mvnw test` | Run unit tests |
| `ant buildWebApps` | `./mvnw package` | Build WAR files |
| `ant buildAll` | `./mvnw clean install` | Full build |
| `ant clean` | `./mvnw clean` | Clean artifacts |
| `ant jar` | `./mvnw package` | Create JAR |

### Property Migration

**Ant properties (build/build.properties):**
```properties
catalina.home=/opt/tomcat
hibernate.logging.level=WARN
```

**Maven properties (pom.xml):**
```xml
<properties>
    <catalina.home>/opt/tomcat</catalina.home>
    <hibernate.logging.level>WARN</hibernate.logging.level>
</properties>
```

### Gradual Migration Strategy

**Week 1-4:**
- Use both Ant and Maven
- Compare outputs
- Train team

**Week 5-8:**
- Maven primary in CI/CD
- Ant fallback for local dev
- Document differences

**Week 9-12:**
- Maven only
- Deprecate Ant
- Remove Ant from CI/CD

---

## IDE Configuration

### IntelliJ IDEA

**Import project:**
1. `File` → `Open` → Select `/home/user/yawl/pom.xml`
2. IntelliJ auto-detects Maven project
3. Wait for indexing

**Maven tool window:**
- `View` → `Tool Windows` → `Maven`
- Run goals directly from UI

**Run configurations:**
- Right-click module → `Run 'Maven install'`

### VS Code

**Extensions:**
```bash
code --install-extension vscjava.vscode-maven
code --install-extension vscjava.vscode-java-pack
```

**settings.json:**
```json
{
  "java.configuration.maven.userSettings": "~/.m2/settings.xml",
  "maven.terminal.useJavaHome": true
}
```

### Eclipse

**Import:**
1. `File` → `Import` → `Maven` → `Existing Maven Projects`
2. Select `/home/user/yawl`
3. Eclipse auto-imports all modules

---

## Best Practices

### 1. Always Use Maven Wrapper

```bash
# Good
./mvnw clean install

# Avoid (requires local Maven installation)
mvn clean install
```

### 2. Use Profiles for Environments

```bash
# Development
./mvnw install -Pdev

# Production
./mvnw install -Pprod
```

### 3. Leverage Parallel Builds

```bash
# Faster builds
./mvnw clean install -T 1C
```

### 4. Cache Dependencies

```bash
# CI/CD: Use Maven cache
# Local: Dependencies auto-cached in ~/.m2/repository
```

### 5. Run Security Scans Regularly

```bash
# Daily in CI/CD
./mvnw verify -Psecurity-scan
```

### 6. Keep Dependencies Up-to-Date

```bash
# Check for updates
./mvnw versions:display-dependency-updates

# Update versions
./mvnw versions:use-latest-releases
```

### 7. Test on Multiple Java Versions

```bash
# Java 21 (LTS)
./mvnw test -Pjava-21

# Java 24 (preview)
./mvnw test -Pjava-24
```

### 8. Use BOM for Dependency Management

```xml
<!-- Good: Version managed by BOM -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <!-- No version needed -->
</dependency>

<!-- Avoid: Hard-coded version -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.4.0</version>
</dependency>
```

---

## Production Deployment

### Build for Production

```bash
# Full production build
./mvnw clean install -Pprod

# Build Docker images
./mvnw jib:build -Pprod

# Create distribution ZIP
cd yawl-distribution
../mvnw assembly:single
```

### Kubernetes Deployment

```bash
# Build and push images
./mvnw jib:build -Pprod

# Deploy to Kubernetes
kubectl apply -f k8s/yawl-engine.yaml

# Check status
kubectl rollout status deployment/yawl-engine

# View logs
kubectl logs -f deployment/yawl-engine
```

### Docker Compose (Local/Staging)

```bash
# Build images
./mvnw jib:dockerBuild

# Start stack
docker-compose up -d

# View logs
docker-compose logs -f yawl-engine

# Stop stack
docker-compose down
```

---

## Quick Reference Card

**Essential Commands:**

```bash
# Build
./mvnw clean install           # Full build
./mvnw clean install -T 1C     # Parallel build
./mvnw install -DskipTests     # Skip tests

# Test
./mvnw test                    # Unit tests
./mvnw verify                  # Integration tests
./mvnw verify jacoco:report    # Coverage

# Docker
./mvnw jib:dockerBuild         # Build images
./mvnw jib:build -Pprod        # Push to registry

# Security
./mvnw verify -Psecurity-scan  # OWASP scan

# Dependencies
./mvnw dependency:tree         # Dependency tree
./mvnw dependency:analyze      # Unused deps

# Profiles
-Pjava-21  -Pjava-24  -Pjava-25  (Java version)
-Pdev  -Pprod                      (Environment)
-Psecurity-scan                    (Security)
-Pperformance                      (Benchmarks)
```

---

## Resources

- **Main Architecture:** `/home/user/yawl/docs/architecture/MAVEN_FIRST_TRANSITION_ARCHITECTURE.md`
- **Module Templates:** `/home/user/yawl/docs/architecture/maven-module-templates/`
- **CI/CD Templates:** `github-actions-maven.yml`, `gitlab-ci-maven.yml`
- **Maven Wrapper Setup:** `mvnw-setup.sh`

---

**Last Updated:** 2026-02-16
**Author:** YAWL Architecture Team
**Version:** 1.0
