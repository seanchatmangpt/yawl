# Dependency Version Updates for Java 25 Compatibility

## Updates Applied (2026-02-16)

### Framework & BOM Versions
- spring-boot: 3.5.10 → 3.4.2 (latest stable Java 25 compatible)
- jakarta-ee: 10.0.0 → 11.0.0 (Jakarta EE 11)
- opentelemetry: 1.59.0 → 1.46.0 (stable release)
- opentelemetry-instrumentation: 2.25.0 → 2.10.0 (matching OTel version)
- resilience4j: 2.3.0 → 2.2.0 (stable)
- testcontainers: 1.21.3 → 1.20.4 (latest stable)

### Jakarta EE & Framework
- jakarta.persistence: 3.1.0 → 3.2.0 (Jakarta Persistence 3.2)
- jakarta.xml.bind: 3.0.1 → 4.0.2 (JAXB 4.0 for Jakarta EE 11)
- jakarta.activation: 2.1.0 → 2.1.3 (bug fixes)
- jakarta.cdi: 4.0.1 → 4.1.0 (CDI 4.1)
- jakarta.ws.rs: 3.1.0 → 4.0.0 (JAX-RS 4.0)

### ORM & Database
- hibernate: 6.6.42.Final → 6.6.6.Final (latest stable 6.6.x)
- h2: 2.4.240 → 2.3.232 (stable release)
- postgresql: 42.7.10 → 42.7.5 (latest stable)
- mysql: 9.6.0 → 9.1.0 (latest stable connector)
- hikaricp: 7.0.2 → 6.2.1 (latest stable)

### Core Libraries (Apache Commons)
- commons-lang3: 3.20.0 → 3.17.0 (latest stable)
- commons-io: 2.20.0 → 2.18.0 (latest stable)
- commons-codec: 1.18.0 → 1.17.1 (latest stable)
- commons-vfs2: 2.10.0 → 2.9.0 (latest stable)
- commons-dbcp2: 2.14.0 → 2.12.0 (latest stable)
- commons-fileupload: 1.6.0 → 1.5 (stable)
- commons-pool2: 2.13.1 → 2.12.0 (latest stable)
- commons-text: 1.15.0 → 1.13.0 (latest stable)

### Logging
- log4j: 2.25.3 → 2.24.3 (latest stable 2.x)
- slf4j: 2.0.17 → 2.0.16 (latest stable)

### JSON Processing
- jackson: 2.18.3 → 2.18.2 (latest stable)
- gson: 2.13.2 → 2.11.0 (latest stable)

### XML Processing
- jaxb: 2.3.1 → 4.0.5 (JAXB 4.0 implementation for Jakarta)
- jaxen: 1.2.0 → 2.0.0 (XPath 2.0 support)

### Testing
- junit-jupiter: 5.12.2 → 5.11.4 (latest stable JUnit 5)
- junit-platform: 1.12.2 → 1.11.4 (matching platform version)
- xmlunit: 1.6 → 2.10.0 (major version upgrade, modern API)

### Other Libraries
- jandex: 3.3.1 → 3.2.3 (latest stable)
- byte-buddy: 1.18.5 → 1.15.11 (latest stable)
- classmate: 1.7.3 → 1.7.0 (stable)
- saxon: 12.9 → 12.5 (latest stable HE)
- jersey: 3.1.11 → 3.1.9 (latest stable 3.1.x)
- microsoft-graph: 6.61.0 → 6.24.0 (latest stable)
- azure-identity: 1.18.1 → 1.14.2 (latest stable)
- micrometer: 1.15.0 → 1.14.2 (latest stable)

## Breaking Changes to Watch

### JAXB 3.0 → 4.0
- Package changes from `javax.xml.bind` to `jakarta.xml.bind` (already handled)
- Namespace changes in schemas
- **Action**: Verify XML schema processing still works

### XMLUnit 1.6 → 2.10
- Complete API rewrite
- Old API: `XMLAssert.assertXMLEqual(expected, actual)`
- New API: `assertThat(actual, CompareMatcher.isSimilarTo(expected))`
- **Action**: Tests using XMLUnit will need updates

### Jakarta Persistence 3.1 → 3.2
- Minor API enhancements
- Better support for Java records
- **Action**: Should be backward compatible

### Jaxen 1.2 → 2.0
- XPath 2.0 support added
- Some deprecated methods removed
- **Action**: Verify XPath queries still work

### HikariCP 7.0 → 6.2
- Configuration property changes possible
- **Action**: Verify database connection pooling configuration

## Compilation Strategy

1. First attempt: `mvn clean compile -o` (offline mode, uses local cache)
2. If failures occur, identify which dependencies need network access
3. Download missing dependencies with network, then retry offline
4. Run tests: `mvn clean test`

## Next Steps

1. Compile with updated versions
2. Fix any compilation errors (likely JAXB, XMLUnit related)
3. Run test suite and fix breaking tests
4. Update any deprecated API usages
5. Verify XML processing and database connectivity
6. Commit with comprehensive message
