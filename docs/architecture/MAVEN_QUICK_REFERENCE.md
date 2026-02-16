# Maven Quick Reference Card

**YAWL v5.2 Maven Build System**

---

## Essential Commands

### Build
```bash
./mvnw clean install              # Full build
./mvnw clean install -T 1C        # Parallel build (FAST)
./mvnw install -DskipTests        # Skip tests
./mvnw install -pl yawl-engine    # Build specific module
./mvnw install -pl yawl-engine -am # Module + dependencies
```

### Test
```bash
./mvnw test                       # Unit tests
./mvnw verify                     # Integration tests
./mvnw test -Dtest=YEngineTest    # Specific test
./mvnw verify jacoco:report       # Coverage report
```

### Docker
```bash
./mvnw jib:dockerBuild            # Build to local Docker
./mvnw jib:build -Pprod           # Push to registry
./mvnw jib:dockerBuild -pl resource-service # Specific service
```

### Security
```bash
./mvnw verify -Psecurity-scan     # OWASP + SonarQube
./mvnw dependency:tree            # Dependency tree
./mvnw dependency:analyze         # Unused dependencies
```

### Profiles
```bash
./mvnw install -Pjava-21          # Java 21 (default)
./mvnw install -Pjava-24          # Java 24 (preview)
./mvnw install -Pjava-25          # Java 25 (virtual threads)
./mvnw install -Pdev              # Development
./mvnw install -Pprod             # Production
./mvnw verify -Psecurity-scan     # Security scanning
./mvnw verify -Pperformance       # Benchmarks
```

---

## Module Structure

```
yawl-parent/
├── yawl-engine          (Core BPM engine)
├── yawl-elements        (Workflow elements)
├── yawl-stateless       (Stateless components)
├── yawl-integration     (A2A, MCP)
├── yawl-util            (Utilities)
├── resource-service     (Spring Boot)
├── worklet-service      (Spring Boot)
├── scheduling-service   (Spring Boot)
├── cost-service         (Spring Boot)
├── monitor-service      (Spring Boot)
├── proclet-service      (Spring Boot)
├── document-store-service
├── mail-service
├── yawl-schema
├── yawl-test-support
└── yawl-distribution
```

---

## BOM Hierarchy

```
Spring Boot BOM 3.4.0
  → Spring Framework 6.2.0
  → Hibernate 6.5.1
  → Jackson 2.15.x

Jakarta EE BOM 10.0.0
  → Servlet 6.0
  → Mail 2.1
  → XML Bind 4.0

OpenTelemetry BOM 1.40.0
  → API, SDK, Exporters

TestContainers BOM 1.19.7
  → PostgreSQL, MySQL modules
```

---

## Plugin Goals

```bash
mvn compiler:compile              # Compile Java sources
mvn surefire:test                 # Run unit tests
mvn failsafe:integration-test     # Run integration tests
mvn jacoco:report                 # Generate coverage report
mvn shade:shade                   # Create fat JAR
mvn jib:build                     # Build and push Docker image
mvn jib:dockerBuild               # Build to local Docker
mvn jib:buildTar                  # Export as TAR
mvn dependency:tree               # Dependency tree
mvn dependency:analyze            # Analyze dependencies
mvn versions:display-dependency-updates # Check for updates
mvn sonar:sonar                   # SonarQube analysis
```

---

## Troubleshooting

### Out of Memory
```bash
export MAVEN_OPTS="-Xmx4g -XX:+UseG1GC"
```

### Dependency Conflicts
```bash
mvn dependency:tree -Dverbose
mvn dependency:analyze
```

### Slow Builds
```bash
./mvnw clean install -T 1C        # Parallel
./mvnw install -DskipTests        # Skip tests
./mvnw install -o                 # Offline mode
```

### Test Failures on Java 21
```bash
export MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED"
```

---

## File Locations

```
/home/user/yawl/
├── pom.xml                       (Root POM)
├── mvnw, mvnw.cmd                (Maven wrapper)
├── .mvn/wrapper/                 (Wrapper config)
├── docs/architecture/
│   ├── MAVEN_FIRST_TRANSITION_ARCHITECTURE.md (Full spec)
│   ├── MAVEN_IMPLEMENTATION_GUIDE.md          (How-to guide)
│   ├── MAVEN_QUICK_REFERENCE.md               (This file)
│   └── maven-module-templates/
│       ├── yawl-engine-pom-example.xml
│       ├── resource-service-pom-example.xml
│       ├── yawl-integration-pom-example.xml
│       ├── mvnw-setup.sh
│       ├── github-actions-maven.yml
│       └── gitlab-ci-maven.yml
└── MAVEN_TRANSITION_SUMMARY.md   (Executive summary)
```

---

## CI/CD

### GitHub Actions
```yaml
# .github/workflows/maven-build.yml
- uses: actions/setup-java@v4
  with:
    distribution: 'temurin'
    java-version: '21'
    cache: 'maven'
- run: ./mvnw clean install -T 1C
```

### GitLab CI
```yaml
# .gitlab-ci.yml
cache:
  paths:
    - .m2/repository
script:
  - ./mvnw clean install -T 1C
```

---

## Docker Images

```bash
# Build
./mvnw jib:dockerBuild

# Images created:
ghcr.io/yawlfoundation/yawl-engine:5.2
ghcr.io/yawlfoundation/resource-service:5.2
ghcr.io/yawlfoundation/worklet-service:5.2
# ... (all services)

# Run
docker run -p 8080:8080 yawl-engine:5.2

# Compose
docker-compose up -d
```

---

## Ant → Maven Mapping

| Ant | Maven | Notes |
|-----|-------|-------|
| `ant compile` | `./mvnw compile` | Compile |
| `ant unitTest` | `./mvnw test` | Tests |
| `ant buildWebApps` | `./mvnw package` | WARs |
| `ant buildAll` | `./mvnw clean install` | Full build |
| `ant clean` | `./mvnw clean` | Clean |

---

## Performance Targets

| Task | Target | Command |
|------|--------|---------|
| Clean build (warm) | <10 min | `./mvnw clean install -T 1C` |
| Incremental build | <2 min | `./mvnw install` |
| Docker build | <3 min | `./mvnw jib:dockerBuild` |
| Tests | <5 min | `./mvnw test -T 1C` |

---

## IDE Integration

### IntelliJ IDEA
1. `File` → `Open` → `pom.xml`
2. Auto-detects Maven project
3. `View` → `Tool Windows` → `Maven`

### VS Code
```bash
code --install-extension vscjava.vscode-maven
```

### Eclipse
`File` → `Import` → `Maven` → `Existing Maven Projects`

---

## Best Practices

✅ **DO:**
- Use Maven wrapper (`./mvnw`)
- Parallel builds (`-T 1C`)
- Profile-specific builds (`-Pprod`)
- Cache dependencies
- Run security scans

❌ **DON'T:**
- Hard-code dependency versions (use BOM)
- Skip tests in production builds
- Ignore dependency conflicts
- Forget to update parent POM when adding modules

---

## Getting Help

**Documentation:**
- Full architecture: `docs/architecture/MAVEN_FIRST_TRANSITION_ARCHITECTURE.md`
- Implementation guide: `docs/architecture/MAVEN_IMPLEMENTATION_GUIDE.md`
- This quick reference: `docs/architecture/MAVEN_QUICK_REFERENCE.md`

**External:**
- Maven docs: https://maven.apache.org/guides/
- Jib plugin: https://github.com/GoogleContainerTools/jib
- Spring Boot: https://spring.io/guides

**Support:**
- Slack: #maven-migration
- Email: maven-migration@yawl.org

---

**Version:** 1.0
**Last Updated:** 2026-02-16
