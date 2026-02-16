# YAWL Security Update - Quick Reference Card

## Phase 1 - Priority Updates (2026-02-15)

### Import Changes Required in Java Code

```java
// OLD (commons-lang 2.x) → NEW (commons-lang3 3.14.0)
import org.apache.commons.lang.StringUtils;
→ import org.apache.commons.lang3.StringUtils;

import org.apache.commons.lang.RandomStringUtils;
→ import org.apache.commons.lang3.RandomStringUtils;

import org.apache.commons.lang.time.DateUtils;
→ import org.apache.commons.lang3.time.DateUtils;

// StringEscapeUtils moved to commons-text
import org.apache.commons.lang.StringEscapeUtils;
→ import org.apache.commons.text.StringEscapeUtils;

// Collections API (if direct usage)
import org.apache.commons.collections.*;
→ import org.apache.commons.collections4.*;

// DBCP (if direct usage - rare)
import org.apache.commons.dbcp.*;
→ import org.apache.commons.dbcp2.*;

// Pool (if direct usage - rare)
import org.apache.commons.pool.*;
→ import org.apache.commons.pool2.*;
```

### Configuration Changes

#### MySQL Hibernate Configuration
```properties
# build/properties/hibernate.properties.mysql

# OLD
hibernate.connection.driver_class com.mysql.jdbc.Driver

# NEW
hibernate.connection.driver_class com.mysql.cj.jdbc.Driver
```

#### Log4j2 (no changes required)
- `log4j2.xml` remains unchanged
- All logging calls remain unchanged

#### Database Drivers (automatic via Hibernate)
- MySQL: Driver class updated (see above)
- PostgreSQL: No changes required
- H2: No changes required

### Build Commands

```bash
# Clean and rebuild with new dependencies
ant clean
ant buildAll

# Run unit tests
ant unitTest

# Build for specific database
# Edit build/build.properties: database.type=mysql|postgres8|h2
ant buildWebApps
```

### New JAR Files Added

```
log4j-api-2.24.1.jar
log4j-core-2.24.1.jar
log4j-slf4j2-impl-2.24.1.jar
slf4j-api-2.0.13.jar
mysql-connector-j-8.0.33.jar
postgresql-42.7.2.jar
h2-2.2.224.jar
commons-lang3-3.14.0.jar
commons-text-1.11.0.jar
commons-codec-1.16.0.jar
commons-collections4-4.4.jar
commons-dbcp2-2.10.0.jar
commons-pool2-2.12.0.jar
commons-io-2.15.1.jar
commons-fileupload-1.5.jar
commons-vfs2-2.9.0.jar
```

### Old JAR Files Removed

```
log4j-api-2.18.0.jar
log4j-core-2.18.0.jar
log4j-1.2-api-2.17.1.jar (legacy bridge)
log4j-slf4j-impl-2.17.1.jar
slf4j-api-1.7.12.jar
mysql-connector-java-5.1.22-bin.jar
postgresql-42.2.8.jar
h2-1.3.176.jar
commons-lang-2.3.jar (consolidated into lang3)
commons-lang3-3.6.jar
commons-codec-1.9.jar
commons-collections-3.2.1.jar
commons-dbcp-1.3.jar
commons-pool-1.5.4.jar
commons-io-2.0.1.jar
commons-fileupload-1.2.2.jar
commons-vfs2-2.1.jar
```

### Critical CVEs Fixed

- **Log4Shell** (CVE-2021-44228, CVE-2021-45046) - CRITICAL
- **MySQL 5.1.x** - Multiple vulnerabilities (EOL 2013)
- **Commons Collections** (CVE-2015-6420) - RCE vulnerability
- **Commons FileUpload** (CVE-2016-1000031) - RCE vulnerability

### Verification Commands

```bash
# Check JAR versions
ls -lh build/3rdParty/lib/ | grep -E "(log4j|mysql|postgresql|h2|commons)"

# Verify no deprecated imports
grep -r "import org.apache.commons.lang\." src/ --include="*.java"
# Should return nothing (all updated to lang3)

# Check MySQL driver class
grep "com.mysql.jdbc.Driver" build/properties/ -r
# Should return nothing (all updated to com.mysql.cj.jdbc.Driver)

# Test compilation
ant compile

# Run tests
ant unitTest
```

### Common Issues

**Issue:** MySQL connection fails with "No suitable driver"
**Fix:** Verify `com.mysql.cj.jdbc.Driver` in hibernate.properties.mysql

**Issue:** ClassNotFoundException for commons-lang
**Fix:** Update imports from `org.apache.commons.lang` to `org.apache.commons.lang3`

**Issue:** StringEscapeUtils not found
**Fix:** Update import to `org.apache.commons.text.StringEscapeUtils`

**Issue:** H2 2.x compatibility mode errors
**Fix:** Use standard SQL or update to H2 2.x syntax (minimal impact)

### Next Steps (Week 1 - Not Implemented Yet)

- Additional commons libraries updates
- Spring Boot dependency updates (if applicable)
- Hibernate version review
- Additional security scanning

---

**For detailed information, see:** `SECURITY_MIGRATION_GUIDE.md`
