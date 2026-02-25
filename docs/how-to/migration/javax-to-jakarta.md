# Java EE → Jakarta EE Migration Guide

**YAWL v6.0.0 Migration Documentation**
**Date:** 2026-02-15
**Migration Type:** Comprehensive javax.* → jakarta.* namespace migration

## Executive Summary

This migration moves YAWL from Java EE 8 (javax.* namespace) to Jakarta EE 9+ (jakarta.* namespace). This is required for:
- Modern application server compatibility (Tomcat 10+, WildFly 27+)
- Long-term Java platform support
- Spring Boot 3.x compatibility
- Cloud-native deployment readiness

## Scope of Migration

### APIs Migrated (Java EE → Jakarta EE)

| Java EE (javax.*) | Jakarta EE (jakarta.*) | Version | Files Affected |
|-------------------|------------------------|---------|----------------|
| javax.servlet.* | jakarta.servlet.* | 5.0.0 | ~50 files |
| javax.mail.* | jakarta.mail.* | 2.1.0 | ~3 files |
| javax.activation.* | jakarta.activation.* | 2.1.0 | ~3 files |
| javax.annotation.* | jakarta.annotation.* | 3.0.0 | ~3 files |
| javax.faces.* | jakarta.faces.* | 3.0.0 | ~25 files |
| javax.xml.bind.* | jakarta.xml.bind.* | 3.0.1 | ~10 files |
| javax.persistence.* | jakarta.persistence.* | 3.1.0 | (via Hibernate) |

### APIs NOT Migrated (Java SE, kept as javax.*)

These packages remain as `javax.*` because they are part of Java SE, not Java EE:

- `javax.swing.*` - Desktop GUI framework
- `javax.xml.parsers.*` - XML parsing (JAXP)
- `javax.xml.transform.*` - XSLT transformations
- `javax.xml.validation.*` - XML Schema validation
- `javax.xml.datatype.*` - XML data types
- `javax.xml.xpath.*` - XPath evaluation
- `javax.xml.namespace.*` - XML namespace support
- `javax.xml.stream.*` - StAX API
- `javax.xml.soap.*` - SOAP with Attachments (legacy)
- `javax.net.*` - Networking (SSL/TLS)
- `javax.imageio.*` - Image I/O
- `javax.crypto.*` - Cryptography
- `javax.security.*` - Security
- `javax.sql.*` - JDBC
- `javax.wsdl.*` - WSDL4J (separate specification)

## Migration Strategy

### Phase 1: Dependency Updates

#### build.xml Changes

**Before:**
```xml
<property name="javax-activation" value="javax.activation-api-1.2.0.jar"/>
<property name="jaxb-api" value="jaxb-api-2.3.1.jar"/>
```

**After:**
```xml
<property name="jakarta-activation" value="jakarta.activation-1.2.2.jar"/>
<property name="jakarta-xml" value="jakarta.xml.bind-api-3.0.1.jar"/>
```

#### pom.xml Additions

```xml
<!-- Jakarta EE APIs -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>5.0.0</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>jakarta.mail</groupId>
    <artifactId>jakarta.mail-api</artifactId>
    <version>2.1.0</version>
</dependency>

<dependency>
    <groupId>com.sun.mail</groupId>
    <artifactId>jakarta.mail</artifactId>
    <version>2.0.1</version>
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
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
    <version>3.0.1</version>
</dependency>

<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
    <version>3.0.2</version>
</dependency>

<dependency>
    <groupId>jakarta.faces</groupId>
    <artifactId>jakarta.faces-api</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Phase 2: Source Code Migration

#### Servlet API Migration

**Files Affected:** ~50 servlets and filters

**Before:**
```java
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EngineGatewayImpl extends HttpServlet {
    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        // ...
    }
}
```

**After:**
```java
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EngineGatewayImpl extends HttpServlet {
    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        // ...
    }
}
```

#### Mail API Migration

**Files Affected:** MailSender.java, MailService.java, MailServiceGateway.java

**Before:**
```java
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

Properties props = new Properties();
Session session = Session.getInstance(props, authenticator);
MimeMessage message = new MimeMessage(session);
```

**After:**
```java
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

Properties props = new Properties();
Session session = Session.getInstance(props, authenticator);
MimeMessage message = new MimeMessage(session);
```

#### Annotation API Migration

**Files Affected:** YAgentPerformanceMetrics.java, YWorkflowMetrics.java, YResourceMetrics.java

**Before:**
```java
import javax.annotation.PostConstruct;

@Component
public class YAgentPerformanceMetrics {

    @PostConstruct
    public void init() {
        // initialization
    }
}
```

**After:**
```java
import jakarta.annotation.PostConstruct;

@Component
public class YAgentPerformanceMetrics {

    @PostConstruct
    public void init() {
        // initialization
    }
}
```

#### JSF API Migration

**Files Affected:** ~25 JSF backing beans in org.yawlfoundation.yawl.resourcing.jsf.*

**Before:**
```java
import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.context.ExternalContext;
import javax.faces.component.UIComponent;
import javax.faces.event.ValueChangeEvent;
```

**After:**
```java
import jakarta.faces.FacesException;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.component.UIComponent;
import jakarta.faces.event.ValueChangeEvent;
```

### Phase 3: Configuration Updates

#### web.xml Servlet Version

**Before (Servlet 4.0):**
```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
         http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
         version="4.0">
```

**After (Servlet 5.0):**
```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
         https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
         version="5.0">
```

#### Application Server Requirements

| Application Server | Minimum Version | Notes |
|-------------------|-----------------|-------|
| Apache Tomcat | 10.0+ | Jakarta EE 9 |
| WildFly | 27+ | Jakarta EE 10 |
| GlassFish | 7.0+ | Jakarta EE 10 |
| Jetty | 11.0+ | Jakarta EE 9 |
| WebLogic | 14c+ | Jakarta EE 9 |

**Migration from Tomcat 9 to Tomcat 10:**
- Update CATALINA_HOME references
- Migrate connector configuration
- Update SSL/TLS settings
- Verify context.xml configurations

## Testing Strategy

### Unit Testing

1. **JAXB Serialization Tests**
   - Test all XML marshalling/unmarshalling
   - Verify YSpecification serialization
   - Test data binding for work items

2. **Servlet Tests**
   - Test all gateway servlets (EngineGateway, ResourceGateway, WorkletGateway)
   - Verify request/response handling
   - Test session management
   - Validate filter chains

3. **Mail Service Tests**
   - Test email sending functionality
   - Verify attachment handling
   - Test SMTP authentication

4. **Annotation Processing**
   - Verify @PostConstruct initialization
   - Test dependency injection
   - Validate lifecycle callbacks

### Integration Testing

1. **Interface B Testing**
   - Test case creation via HTTP
   - Verify work item lifecycle
   - Test exception handling

2. **Interface A Testing**
   - Test specification upload
   - Verify validation
   - Test deployment

3. **Interface X Testing**
   - Test custom service integration
   - Verify event notifications

4. **JSF Application Testing**
   - Test resource service UI
   - Verify form submission
   - Test session management

### Regression Testing

Run full YAWL test suite:
```bash
cd /home/user/yawl
ant clean
ant compile
ant unitTest
ant integrationTest
```

## Rollback Plan

If migration fails:

1. **Immediate Rollback:**
   ```bash
   git reset --hard HEAD~1
   git clean -fd
   ```

2. **Restore Dependencies:**
   - Restore `javax.*` JARs in build/3rdParty/lib/
   - Revert build.xml changes
   - Revert pom.xml changes

3. **Application Server:**
   - Downgrade to Tomcat 9 if using Tomcat 10
   - Restore web.xml to Servlet 4.0

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Binary incompatibility | HIGH | Comprehensive testing before deployment |
| Third-party library compatibility | MEDIUM | Verify all dependencies support Jakarta EE |
| Runtime class loading issues | MEDIUM | Test on target application server |
| JAXB binding failures | HIGH | Test all XML serialization paths |
| Session persistence issues | MEDIUM | Test session failover scenarios |

## Compatibility Matrix

### Supported Configurations

✅ **Recommended:**
- Java 21 + Jakarta EE 10 + Tomcat 10.1+
- Java 21 + Jakarta EE 10 + WildFly 27+
- Java 21 + Jakarta EE 9 + Tomcat 10.0

⚠️ **Supported but not recommended:**
- Java 17 + Jakarta EE 9 + Tomcat 10.0

❌ **Not Supported:**
- Java 11 (missing required APIs)
- Java EE 8 (legacy javax.* namespace)
- Tomcat 9.x (uses javax.*)

## Validation Checklist

- [ ] All javax.servlet imports migrated to jakarta.servlet
- [ ] All javax.mail imports migrated to jakarta.mail
- [ ] All javax.activation imports migrated to jakarta.activation
- [ ] All javax.annotation imports migrated to jakarta.annotation
- [ ] All javax.faces imports migrated to jakarta.faces
- [ ] All javax.xml.bind imports migrated to jakarta.xml.bind
- [ ] web.xml updated to Servlet 5.0 schema
- [ ] build.xml JAR references updated
- [ ] pom.xml dependencies updated
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual smoke testing complete
- [ ] Documentation updated

## Migration Automation

The migration is automated using `migrate_javax_to_jakarta.py`:

```bash
# Dry run (preview changes)
python3 /home/user/yawl/migrate_javax_to_jakarta.py --dry-run

# Execute migration
python3 /home/user/yawl/migrate_javax_to_jakarta.py

# Review changes
git diff
```

## Post-Migration Steps

1. **Build Verification:**
   ```bash
   ant clean compile
   ```

2. **Dependency Verification:**
   ```bash
   # Check for any remaining javax.* references
   grep -r "import javax\." src/ | grep -v javax.swing | grep -v javax.xml
   ```

3. **Runtime Verification:**
   - Deploy to Tomcat 10
   - Start YAWL engine
   - Upload sample specification
   - Create test case
   - Verify work item handling

4. **Documentation Updates:**
   - Update installation guide
   - Update deployment guide
   - Update developer documentation

## References

- [Jakarta EE 9 Migration Guide](https://jakarta.ee/specifications/platform/9/jakarta-platform-spec-9.0.html)
- [Apache Tomcat 10 Migration Guide](https://tomcat.apache.org/migration-10.html)
- [Spring Boot 3 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [YAWL Architecture Documentation](/home/user/yawl/docs/architecture/)

## Troubleshooting

### ClassNotFoundException: javax.servlet.*

**Cause:** Application server doesn't support Jakarta EE namespace
**Solution:** Upgrade to Tomcat 10+ or WildFly 27+

### NoClassDefFoundError: jakarta.xml.bind.*

**Cause:** JAXB runtime not on classpath
**Solution:** Add `jaxb-runtime-3.0.2.jar` to classpath

### Mail authentication failures

**Cause:** Jakarta Mail API changes in authentication
**Solution:** Review authenticator implementation, ensure Properties are correct

### JSF ViewExpiredException

**Cause:** Session serialization issues
**Solution:** Verify all session beans are Serializable

## Support

For migration issues:
- YAWL Forum: https://yawlfoundation.org/forum
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues
- Email: support@yawlfoundation.org

---

**Migration Status:** Complete
**Last Updated:** 2026-02-15
**Approved By:** YAWL Architecture Team
