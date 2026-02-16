# YAWL Jakarta EE Migration - Quick Start Guide

**Version:** 5.2
**Date:** 2026-02-15
**Migration Type:** Java EE 8 (javax.*) → Jakarta EE 10 (jakarta.*)

## Quick Start

### 1. Pre-Migration Checklist

- [ ] Backup your work or commit to git
- [ ] Verify you have Java 21 installed
- [ ] Ensure you have write permissions to YAWL directory
- [ ] Review `/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md`

### 2. Execute Migration

```bash
cd /home/user/yawl

# Make scripts executable
chmod +x migrate-jakarta.sh execute-jakarta-migration.sh

# Option A: Dry run to see what will change
./migrate-jakarta.sh --dry-run

# Option B: Execute the migration
./execute-jakarta-migration.sh

# Option C: Use Python script (alternative)
python3 migrate_javax_to_jakarta.py --dry-run
python3 migrate_javax_to_jakarta.py
```

### 3. Verify Migration

```bash
# Check for remaining javax.* imports (excluding Java SE)
grep -r "import javax\." src/ | \
    grep -v javax.swing | \
    grep -v javax.xml.parsers | \
    grep -v javax.xml.transform | \
    grep -v javax.xml.validation | \
    grep -v javax.xml.datatype | \
    grep -v javax.xml.xpath | \
    grep -v javax.xml.soap | \
    grep -v javax.net | \
    wc -l

# Should return 0 (zero)

# Count jakarta.* imports
grep -r "import jakarta\." src/ | wc -l

# Should return ~200+ (actual count depends on migrated classes)

# Review changes in git
git diff --stat
git diff src/ | head -100
```

### 4. Test Build

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Generate coverage report
mvn jacoco:report
```

### 5. Deploy & Test

```bash
# Deploy to Tomcat 10+
cp target/yawl-5.2.jar $CATALINA_HOME/webapps/

# Start server
$CATALINA_HOME/bin/catalina.sh run

# Test basic functionality
curl http://localhost:8080/yawl/ib
```

## Migration Scope

### What Gets Migrated

| API | Before | After | Files Affected |
|-----|--------|-------|----------------|
| Servlet | javax.servlet.* | jakarta.servlet.* | ~50 |
| Mail | javax.mail.* | jakarta.mail.* | ~3 |
| Activation | javax.activation.* | jakarta.activation.* | ~3 |
| Annotation | javax.annotation.* | jakarta.annotation.* | ~3 |
| JSF | javax.faces.* | jakarta.faces.* | ~25 |
| JAXB | javax.xml.bind.* | jakarta.xml.bind.* | ~10 |
| JPA | javax.persistence.* | jakarta.persistence.* | Via Hibernate |

### What Stays as javax.*

These are part of Java SE, NOT Java EE, so they remain as `javax.*`:

- `javax.swing.*` - Desktop GUI
- `javax.xml.parsers.*` - XML parsing (JAXP)
- `javax.xml.transform.*` - XSLT
- `javax.xml.validation.*` - XML Schema
- `javax.xml.datatype.*` - XML datatypes (Duration, XMLGregorianCalendar)
- `javax.xml.xpath.*` - XPath
- `javax.xml.soap.*` - SOAP (legacy, kept for compatibility)
- `javax.net.*` - Networking (SSL/TLS)
- `javax.crypto.*` - Cryptography
- `javax.sql.*` - JDBC
- `javax.imageio.*` - Image I/O
- `javax.wsdl.*` - WSDL4J (separate spec)

## Files Modified

### Source Code (~100+ files)

```
src/org/yawlfoundation/yawl/engine/interfce/*.java
src/org/yawlfoundation/yawl/resourcing/jsf/*.java
src/org/yawlfoundation/yawl/mailSender/*.java
src/org/yawlfoundation/yawl/mailService/*.java
src/org/yawlfoundation/yawl/worklet/*.java
src/org/yawlfoundation/yawl/cost/*.java
src/org/yawlfoundation/yawl/documentStore/*.java
src/org/yawlfoundation/yawl/engine/actuator/metrics/*.java
... and more
```

### Configuration Files

- `pom.xml` - Jakarta EE dependencies added
- `build/build.xml` - JAR references updated
- `web.xml` - Servlet version update required (manual)
- `faces-config.xml` - JSF version update required (manual)

## Example Changes

### Before (Java EE 8)

```java
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.mail.Message;
import javax.mail.Session;
import javax.activation.DataHandler;
import javax.annotation.PostConstruct;

public class EngineGatewayImpl extends HttpServlet {
    @PostConstruct
    public void init() {
        // initialization
    }

    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        // handle request
    }
}
```

### After (Jakarta EE 10)

```java
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.activation.DataHandler;
import jakarta.annotation.PostConstruct;

public class EngineGatewayImpl extends HttpServlet {
    @PostConstruct
    public void init() {
        // initialization
    }

    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        // handle request
    }
}
```

## Rollback Instructions

If migration fails or causes issues:

### Git Rollback

```bash
# Discard all changes
git checkout -- src/ test/ pom.xml build/build.xml

# Or reset to previous commit
git reset --hard HEAD~1

# Clean untracked files
git clean -fd
```

### Manual Rollback

1. Restore `pom.xml` from backup
2. Restore `build/build.xml` from backup
3. Revert all Java source files
4. Downgrade to Tomcat 9 if upgraded

## Troubleshooting

### ClassNotFoundException: javax.servlet.*

**Problem:** Application server doesn't support Jakarta EE

**Solution:** Upgrade to Tomcat 10+, WildFly 27+, or GlassFish 7+

### NoClassDefFoundError: jakarta.xml.bind.*

**Problem:** JAXB runtime not on classpath

**Solution:** Ensure `jaxb-runtime-3.0.2.jar` is in classpath

### Mail authentication failures

**Problem:** Jakarta Mail API changes

**Solution:** Review `MailSender.java` authenticator implementation

### JSF ViewExpiredException

**Problem:** Session serialization issues

**Solution:** Verify all session beans implement `Serializable`

### JAXB marshalling errors

**Problem:** JAXB context creation failures

**Solution:** Check `JAXBContext.newInstance()` calls, verify package names

## Required Application Server Versions

| Application Server | Minimum Version | Jakarta EE Version |
|-------------------|-----------------|-------------------|
| Apache Tomcat | 10.0+ | Jakarta EE 9 |
| WildFly | 27+ | Jakarta EE 10 |
| GlassFish | 7.0+ | Jakarta EE 10 |
| Jetty | 11.0+ | Jakarta EE 9 |
| WebLogic | 14c+ | Jakarta EE 9 |

## Maven Dependencies Added

The migration updates `pom.xml` to include:

```xml
<!-- Jakarta EE BOM -->
<dependency>
    <groupId>jakarta.platform</groupId>
    <artifactId>jakarta.jakartaee-bom</artifactId>
    <version>10.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Jakarta Servlet -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- Jakarta Mail -->
<dependency>
    <groupId>jakarta.mail</groupId>
    <artifactId>jakarta.mail-api</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>com.sun.mail</groupId>
    <artifactId>jakarta.mail</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Jakarta Activation -->
<dependency>
    <groupId>jakarta.activation</groupId>
    <artifactId>jakarta.activation-api</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Jakarta Annotation -->
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Jakarta Faces (JSF) -->
<dependency>
    <groupId>jakarta.faces</groupId>
    <artifactId>jakarta.faces-api</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Jakarta XML Binding (JAXB) -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
    <version>3.0.1</version>
</dependency>

<!-- Jakarta Persistence (JPA) -->
<dependency>
    <groupId>jakarta.persistence</groupId>
    <artifactId>jakarta.persistence-api</artifactId>
    <version>3.0.0</version>
</dependency>
```

## Testing Checklist

After migration, verify:

- [ ] YAWL compiles without errors
- [ ] Unit tests pass
- [ ] YAWL engine starts successfully
- [ ] Upload specification via Interface A
- [ ] Create case via Interface B
- [ ] Execute work items
- [ ] JSF resource service UI loads
- [ ] Mail sending works
- [ ] Session persistence works
- [ ] Custom services integrate correctly
- [ ] No ClassNotFoundException in logs
- [ ] No NoClassDefFoundError in logs

## Documentation

For more details, see:

- **Migration Guide:** `/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md`
- **Architecture Decision:** `/home/user/yawl/docs/architecture/adr/ADR-011-jakarta-ee-migration.md`
- **Jakarta EE Specification:** https://jakarta.ee/specifications/platform/10/
- **Apache Tomcat 10 Migration:** https://tomcat.apache.org/migration-10.html

## Support

For issues or questions:

- YAWL Forum: https://yawlfoundation.org/forum
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues
- Email: support@yawlfoundation.org

---

**Migration Scripts:**
- `/home/user/yawl/migrate-jakarta.sh` - Comprehensive bash migration
- `/home/user/yawl/execute-jakarta-migration.sh` - Detailed execution with verification
- `/home/user/yawl/migrate_javax_to_jakarta.py` - Python alternative

**Status:** Ready for execution
**Last Updated:** 2026-02-15
