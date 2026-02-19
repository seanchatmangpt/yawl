# ADR-011: Jakarta EE Migration Strategy

**Status:** Approved
**Date:** 2026-02-15
**Decision Makers:** YAWL Architecture Team
**Technical Story:** Migrate from Java EE 8 (javax.*) to Jakarta EE 9+ (jakarta.*)

## Context

YAWL v6.0.0 currently uses Java EE 8 APIs with the `javax.*` namespace. Jakarta EE 9 introduced a backward-incompatible namespace change from `javax.*` to `jakarta.*`. This migration is necessary for:

### Business Drivers

1. **Modern Application Server Support**
   - Tomcat 10+ requires Jakarta EE
   - WildFly 27+ requires Jakarta EE
   - GlassFish 7+ requires Jakarta EE

2. **Spring Boot 3.x Compatibility**
   - Spring Boot 3.0+ requires Jakarta EE
   - YAWL uses Spring Boot 3.2.2 for actuator and cloud-native features

3. **Long-term Support**
   - Java EE 8 is end-of-life
   - Jakarta EE is the future of enterprise Java
   - Oracle has transferred Java EE stewardship to Eclipse Foundation

4. **Cloud-Native Readiness**
   - Modern cloud platforms expect Jakarta EE
   - Container orchestration requires current APIs
   - Observability tools integrate with Jakarta EE

### Technical Constraints

1. **Backward Compatibility Breaking**
   - Binary incompatibility between javax.* and jakarta.*
   - Cannot mix javax.* and jakarta.* in same application
   - All-or-nothing migration required

2. **Third-Party Dependencies**
   - All dependencies must support Jakarta EE
   - Older libraries may not be compatible
   - May require library upgrades or replacements

3. **Testing Complexity**
   - Extensive testing required
   - All servlets, filters, JSF components must be tested
   - JAXB serialization must be verified

## Decision

**We will migrate YAWL to Jakarta EE 10** with the following strategy:

### Migration Scope

#### APIs to Migrate

| API | From | To | Impact |
|-----|------|-----|--------|
| Servlet | javax.servlet.* | jakarta.servlet.* | HIGH - ~50 servlets |
| Mail | javax.mail.* | jakarta.mail.* | MEDIUM - 3 services |
| Activation | javax.activation.* | jakarta.activation.* | LOW - mail dependency |
| Annotation | javax.annotation.* | jakarta.annotation.* | LOW - 3 metrics classes |
| JSF | javax.faces.* | jakarta.faces.* | HIGH - ~25 backing beans |
| JAXB | javax.xml.bind.* | jakarta.xml.bind.* | MEDIUM - XML serialization |
| JPA | javax.persistence.* | jakarta.persistence.* | MEDIUM - Hibernate 6.5 |

#### APIs to Keep (Java SE)

These remain as `javax.*` because they are part of Java SE, not Java EE:

- `javax.swing.*` - Desktop GUI (not migrated)
- `javax.xml.parsers.*` - JAXP (part of Java SE)
- `javax.xml.transform.*` - XSLT (part of Java SE)
- `javax.xml.validation.*` - XML Schema (part of Java SE)
- `javax.xml.datatype.*` - XML data types (part of Java SE)
- `javax.xml.xpath.*` - XPath (part of Java SE)
- `javax.xml.soap.*` - SOAP (legacy, not migrated)
- `javax.net.*` - Networking including SSL/TLS (Java SE)
- `javax.crypto.*` - Cryptography (Java SE)
- `javax.sql.*` - JDBC (Java SE)

### Dependency Strategy

#### Maven Dependencies (pom.xml)

```xml
<!-- Jakarta EE BOM -->
<dependency>
    <groupId>jakarta.platform</groupId>
    <artifactId>jakarta.jakartaee-bom</artifactId>
    <version>10.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Individual Jakarta APIs -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>jakarta.mail</groupId>
    <artifactId>jakarta.mail-api</artifactId>
    <version>2.1.0</version>
</dependency>

<dependency>
    <groupId>jakarta.activation</groupId>
    <artifactId>jakarta.activation-api</artifactId>
    <version>2.1.0</version>
</dependency>

<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
    <version>3.0.0</version>
</dependency>

<dependency>
    <groupId>jakarta.faces</groupId>
    <artifactId>jakarta.faces-api</artifactId>
    <version>3.0.0</version>
</dependency>

<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
    <version>3.0.1</version>
</dependency>

<dependency>
    <groupId>jakarta.persistence</groupId>
    <artifactId>jakarta.persistence-api</artifactId>
    <version>3.0.0</version>
</dependency>
```

#### Build Configuration (build.xml)

- Replace `javax.activation-api-1.2.0.jar` with `jakarta.activation-1.2.2.jar`
- Replace `jaxb-api-2.3.1.jar` with `jakarta.xml.bind-api-3.0.1.jar`
- Update all JAR references in classpath configurations

### Migration Automation

#### Automated Source Code Migration

Use shell script to automate import replacements:

```bash
#!/bin/bash
# Migrate javax.servlet → jakarta.servlet
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.servlet\.|import jakarta.servlet.|g' {} +

# Migrate javax.mail → jakarta.mail
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.mail\.|import jakarta.mail.|g' {} +

# Migrate javax.activation → jakarta.activation
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.activation\.|import jakarta.activation.|g' {} +

# Migrate javax.annotation → jakarta.annotation
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.annotation\.|import jakarta.annotation.|g' {} +

# Migrate javax.faces → jakarta.faces
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.faces\.|import jakarta.faces.|g' {} +

# Migrate javax.xml.bind → jakarta.xml.bind
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.xml\.bind\.|import jakarta.xml.bind.|g' {} +

# Migrate javax.persistence → jakarta.persistence
find src test -name "*.java" -type f -exec sed -i \
    's|import javax\.persistence\.|import jakarta.persistence.|g' {} +
```

#### Manual Verification Required

1. **web.xml Servlet Version**
   - Update schema from Servlet 4.0 to Servlet 5.0
   - Change namespace to `https://jakarta.ee/xml/ns/jakartaee`

2. **JSF Configuration (faces-config.xml)**
   - Update JSF version to 3.0
   - Update schema namespace

3. **JAXB Context Initialization**
   - Verify JAXBContext creation
   - Test XML marshalling/unmarshalling

### Testing Strategy

#### Unit Testing

1. **JAXB Serialization**
   ```java
   @Test
   public void testYSpecificationSerialization() {
       YSpecification spec = createTestSpec();
       String xml = YSpecification.marshal(spec);
       YSpecification restored = YSpecification.unmarshal(xml);
       assertEquals(spec, restored);
   }
   ```

2. **Servlet Handling**
   ```java
   @Test
   public void testEngineGatewayPost() {
       MockHttpServletRequest request = new MockHttpServletRequest();
       MockHttpServletResponse response = new MockHttpServletResponse();
       servlet.doPost(request, response);
       assertEquals(200, response.getStatus());
   }
   ```

3. **Mail Sending**
   ```java
   @Test
   public void testMailSender() {
       MailSender sender = new MailSender();
       sender.sendEmail(to, subject, body);
       // Verify mail was sent
   }
   ```

4. **Annotation Processing**
   ```java
   @Test
   public void testPostConstructInitialization() {
       YAgentPerformanceMetrics metrics = context.getBean(...);
       assertTrue(metrics.isInitialized());
   }
   ```

#### Integration Testing

1. **Full Workflow Execution**
   - Upload specification via Interface A
   - Create case via Interface B
   - Execute work items
   - Verify completion

2. **JSF Application Testing**
   - Test resource service UI
   - Verify form submission
   - Test session management

3. **Custom Service Integration**
   - Test Interface X callbacks
   - Verify event notifications

### Rollback Strategy

If migration fails:

1. **Git Rollback**
   ```bash
   git reset --hard HEAD~1
   git clean -fd
   ```

2. **Restore Dependencies**
   - Restore `javax.*` JARs
   - Revert pom.xml
   - Revert build.xml

3. **Application Server**
   - Downgrade to Tomcat 9
   - Restore web.xml to Servlet 4.0

## Consequences

### Positive

1. **Modern Platform Support**
   - Compatible with Tomcat 10+, WildFly 27+, GlassFish 7+
   - Compatible with Spring Boot 3.x
   - Future-proof for 5+ years

2. **Improved Performance**
   - Jakarta EE implementations are more performant
   - Better container integration
   - Reduced startup time

3. **Better Tooling**
   - Modern IDEs support Jakarta EE
   - Better code completion
   - Improved debugging

4. **Cloud-Native Ready**
   - Compatible with Kubernetes, Docker
   - Integrates with Prometheus, Grafana
   - OpenTelemetry support

### Negative

1. **Breaking Change**
   - Binary incompatibility with Java EE 8
   - Requires application server upgrade
   - Cannot run on Tomcat 9 or earlier

2. **Testing Overhead**
   - Extensive testing required
   - Potential runtime issues
   - Need to verify all integration points

3. **Documentation Updates**
   - All docs must reference Jakarta EE
   - Installation guides need updating
   - Developer docs need revision

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| JAXB binding failures | MEDIUM | HIGH | Comprehensive XML serialization tests |
| Third-party incompatibility | LOW | MEDIUM | Verify all dependencies before migration |
| Runtime class loading | LOW | HIGH | Test on target servers |
| Session persistence issues | LOW | MEDIUM | Test failover scenarios |
| JSF rendering problems | MEDIUM | MEDIUM | Visual testing of all forms |

### Migration Checklist

- [x] Create migration automation scripts
- [x] Update pom.xml with Jakarta dependencies
- [x] Update build.xml with Jakarta JARs
- [x] Document migration strategy (this ADR)
- [x] Create detailed migration guide
- [ ] Execute automated migration
- [ ] Update web.xml to Servlet 5.0
- [ ] Update faces-config.xml to JSF 3.0
- [ ] Test JAXB serialization
- [ ] Test all servlets
- [ ] Test JSF application
- [ ] Test mail functionality
- [ ] Run full test suite
- [ ] Deploy to staging environment
- [ ] Performance testing
- [ ] Update installation documentation
- [ ] Update developer documentation
- [ ] Deploy to production

## Implementation Timeline

1. **Week 1: Preparation**
   - Review all dependencies
   - Create backup/branch
   - Set up test environment

2. **Week 2: Migration Execution**
   - Run automated migration
   - Manual verification
   - Update configuration files

3. **Week 3: Testing**
   - Unit testing
   - Integration testing
   - Performance testing

4. **Week 4: Documentation & Deployment**
   - Update documentation
   - Deploy to staging
   - Production deployment

## Related Decisions

- ADR-001: Spring Boot 3.2 Adoption (requires Jakarta EE)
- ADR-007: Hibernate 6.5 Upgrade (uses Jakarta Persistence API)
- ADR-009: Cloud-Native Architecture (requires modern APIs)

## References

- [Jakarta EE 9 Migration Guide](https://jakarta.ee/specifications/platform/9/jakarta-platform-spec-9.0.html)
- [Jakarta EE 10 Specification](https://jakarta.ee/specifications/platform/10/)
- [Apache Tomcat 10 Migration](https://tomcat.apache.org/migration-10.html)
- [Spring Boot 3.0 Migration](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Hibernate 6.0 Migration Guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-15

**Implementation Status:** IN PROGRESS
**Completion Target:** 2026-03-15

---

**Revision History:**
- 2026-02-15: Initial version approved
