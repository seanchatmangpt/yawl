# Jakarta EE Migration - Execution Plan

**YAWL v5.2**
**Date:** 2026-02-15
**Status:** READY FOR EXECUTION

## Executive Summary

This document provides a step-by-step execution plan for migrating YAWL from Java EE 8 (javax.*) to Jakarta EE 10 (jakarta.*). The migration affects ~100+ Java source files across ~200+ import statements.

## Pre-Execution Checklist

- [ ] Git repository is clean (no uncommitted changes)
- [ ] Backup created or working in feature branch
- [ ] Java 21 is installed and active
- [ ] Maven 3.8+ is installed
- [ ] Write permissions verified for YAWL directory
- [ ] All team members notified
- [ ] Test environment prepared (Tomcat 10+)

## Migration Steps

### Step 1: Verify Current State

```bash
cd /home/user/yawl

# Make verification script executable
chmod +x verify-migration-status.sh

# Run verification
./verify-migration-status.sh
```

**Expected output:**
- Total Java files: ~600+
- Files to be modified: ~100+
- Imports to migrate: ~200+
- Imports to keep (Java SE): ~100+

**Decision Point:** Review the output. If numbers look reasonable, proceed.

---

### Step 2: Create Backup (Optional but Recommended)

```bash
# Option A: Git branch
git checkout -b jakarta-ee-migration
git commit -am "Pre-migration checkpoint"

# Option B: Filesystem backup
tar -czf /tmp/yawl-pre-jakarta-backup-$(date +%Y%m%d).tar.gz .
```

---

### Step 3: Execute Automated Migration

```bash
# Make execution script executable
chmod +x execute-jakarta-migration.sh

# Execute migration
./execute-jakarta-migration.sh
```

**What happens:**
1. Replaces all `javax.servlet.*` → `jakarta.servlet.*`
2. Replaces all `javax.mail.*` → `jakarta.mail.*`
3. Replaces all `javax.activation.*` → `jakarta.activation.*`
4. Replaces all `javax.annotation.*` → `jakarta.annotation.*`
5. Replaces all `javax.faces.*` → `jakarta.faces.*`
6. Replaces all `javax.xml.bind.*` → `jakarta.xml.bind.*`
7. Replaces all `javax.persistence.*` → `jakarta.persistence.*`
8. Verifies results and reports status

**Expected duration:** 2-5 minutes

**Expected output:**
- Modified ~100+ files
- Added ~200+ Jakarta imports
- 0 remaining Java EE imports (excluding Java SE)

---

### Step 4: Review Changes

```bash
# See summary of changes
git diff --stat

# Review specific changes
git diff src/org/yawlfoundation/yawl/engine/interfce/ | less
git diff src/org/yawlfoundation/yawl/resourcing/jsf/ | less
git diff src/org/yawlfoundation/yawl/mailSender/ | less

# Check specific files
git diff src/org/yawlfoundation/yawl/engine/interfce/EngineGatewayImpl.java
git diff src/org/yawlfoundation/yawl/mailSender/MailSender.java
git diff src/org/yawlfoundation/yawl/resourcing/jsf/SessionBean.java
```

**What to look for:**
- All `import javax.servlet.*` changed to `import jakarta.servlet.*`
- All `import javax.mail.*` changed to `import jakarta.mail.*`
- All `import javax.faces.*` changed to `import jakarta.faces.*`
- No changes to `import javax.swing.*` (should remain)
- No changes to `import javax.xml.parsers.*` (should remain)
- No changes to `import javax.xml.datatype.*` (should remain)

**Decision Point:** If changes look correct, proceed. Otherwise, rollback.

---

### Step 5: Verify Dependencies

```bash
# Check pom.xml has Jakarta dependencies
grep -A2 "jakarta.servlet" pom.xml
grep -A2 "jakarta.mail" pom.xml
grep -A2 "jakarta.activation" pom.xml
grep -A2 "jakarta.annotation" pom.xml
grep -A2 "jakarta.faces" pom.xml
grep -A2 "jakarta.xml.bind" pom.xml
```

**Expected:** All Jakarta EE dependencies should be present in pom.xml

**Actual Status:** pom.xml already updated with all Jakarta EE dependencies

---

### Step 6: Compile & Test

```bash
# Clean and compile
mvn clean compile

# Expected result: BUILD SUCCESS

# Run unit tests
mvn test

# Expected result: Most tests should pass
# Some integration tests may fail if they require application server

# Check for compilation errors
mvn clean compile 2>&1 | grep -i error
```

**If compilation fails:**
- Check error messages for ClassNotFoundException
- Verify all Jakarta dependencies are in pom.xml
- Check for mixed javax/jakarta imports in same file

---

### Step 7: Manual Configuration Updates

Some configuration files require manual updates:

#### web.xml (if exists)

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

#### faces-config.xml (if exists)

**Before (JSF 2.3):**
```xml
<faces-config xmlns="http://xmlns.jcp.org/xml/ns/javaee"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
              http://xmlns.jcp.org/xml/ns/javaee/web-facesconfig_2_3.xsd"
              version="2.3">
```

**After (JSF 3.0):**
```xml
<faces-config xmlns="https://jakarta.ee/xml/ns/jakartaee"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
              https://jakarta.ee/xml/ns/jakartaee/web-facesconfig_3_0.xsd"
              version="3.0">
```

```bash
# Find all web.xml files
find . -name "web.xml" -type f

# Find all faces-config.xml files
find . -name "faces-config.xml" -type f

# Update manually using your editor
```

---

### Step 8: Deploy to Test Environment

```bash
# Package application
mvn clean package

# Deploy to Tomcat 10+
cp target/yawl-5.2.jar $CATALINA_HOME/webapps/

# Or for WAR deployment
cp target/yawl-5.2.war $CATALINA_HOME/webapps/

# Start Tomcat
$CATALINA_HOME/bin/catalina.sh run
```

**Monitor startup logs:**
```bash
tail -f $CATALINA_HOME/logs/catalina.out
```

**Look for:**
- No ClassNotFoundException
- No NoClassDefFoundError
- Successful deployment messages
- No jakarta/javax mixing errors

---

### Step 9: Functional Testing

#### Test 1: Engine Health Check

```bash
curl http://localhost:8080/yawl/ib
```

**Expected:** Interface B landing page or WSDL

#### Test 2: Upload Specification (Interface A)

```bash
# Using YAWL client or curl
curl -X POST http://localhost:8080/yawl/ia \
  -F "action=upload" \
  -F "userid=admin" \
  -F "password=YAWL" \
  -F "specfile=@/path/to/test-spec.yawl"
```

**Expected:** Specification uploaded successfully

#### Test 3: Create Case (Interface B)

```bash
curl -X POST http://localhost:8080/yawl/ib \
  -d "action=launchCase" \
  -d "specID=test-spec" \
  -d "userid=admin" \
  -d "password=YAWL"
```

**Expected:** Case created successfully

#### Test 4: JSF Resource Service

```bash
# Open in browser
firefox http://localhost:8080/resourceService/
```

**Expected:** Login page displays, forms work correctly

#### Test 5: Mail Sending

Test the MailSender service:
- Trigger workflow that sends email
- Verify email is sent
- Check logs for errors

#### Test 6: JAXB Serialization

```bash
# Run JAXB-specific tests
mvn test -Dtest=*Specification*Test
mvn test -Dtest=*Marshal*Test
```

**Expected:** All JAXB tests pass

---

### Step 10: Commit Changes

```bash
# Stage all changes
git add .

# Commit with descriptive message
git commit -m "Migrate to Jakarta EE 10

- Migrated all javax.servlet.* to jakarta.servlet.*
- Migrated all javax.mail.* to jakarta.mail.*
- Migrated all javax.activation.* to jakarta.activation.*
- Migrated all javax.annotation.* to jakarta.annotation.*
- Migrated all javax.faces.* to jakarta.faces.*
- Migrated all javax.xml.bind.* to jakarta.xml.bind.*
- Migrated all javax.persistence.* to jakarta.persistence.*
- Updated pom.xml with Jakarta EE 10 dependencies
- Kept Java SE javax.* packages unchanged
- Updated web.xml to Servlet 5.0
- Updated faces-config.xml to JSF 3.0

This migration enables compatibility with:
- Apache Tomcat 10+
- Spring Boot 3.x
- Modern cloud platforms

Tested on:
- Java 21
- Tomcat 10.1
- All unit tests passing
- Integration tests verified"

# Push to repository
git push origin jakarta-ee-migration
```

---

## Rollback Procedures

### Immediate Rollback (Git)

```bash
# Discard all changes
git checkout -- src/ test/ pom.xml build/build.xml

# Or reset to previous commit
git reset --hard HEAD~1

# Clean untracked files
git clean -fd
```

### Filesystem Rollback

```bash
# Restore from backup
cd /home/user
rm -rf yawl
tar -xzf /tmp/yawl-pre-jakarta-backup-*.tar.gz
cd yawl
```

### Application Server Rollback

```bash
# Stop Tomcat 10
$CATALINA_HOME/bin/shutdown.sh

# Install Tomcat 9
# Download from https://tomcat.apache.org/download-90.cgi
# Extract and configure

# Deploy old version
cp /path/to/old-yawl.war $TOMCAT9_HOME/webapps/

# Start Tomcat 9
$TOMCAT9_HOME/bin/startup.sh
```

---

## Troubleshooting Guide

### Issue: ClassNotFoundException: javax.servlet.http.HttpServlet

**Cause:** Application deployed to Tomcat 9 or earlier

**Solution:**
```bash
# Upgrade to Tomcat 10+
# Or rollback migration
```

### Issue: NoClassDefFoundError: jakarta/servlet/ServletException

**Cause:** Missing Jakarta Servlet API dependency

**Solution:**
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Issue: JAXB unmarshalling fails

**Cause:** JAXB runtime not on classpath or context issue

**Solution:**
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>com.sun.xml.bind</groupId>
    <artifactId>jaxb-impl</artifactId>
    <version>3.0.1</version>
</dependency>
```

### Issue: Mail sending fails

**Cause:** Jakarta Mail API version mismatch

**Solution:**
```bash
# Verify dependencies
mvn dependency:tree | grep mail

# Ensure both API and implementation are present
```

### Issue: JSF pages don't render

**Cause:** Missing Jakarta Faces implementation or wrong version

**Solution:**
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>jakarta.faces</artifactId>
    <version>3.0.0</version>
</dependency>
```

---

## Validation Checklist

- [ ] All source files compiled successfully
- [ ] Unit tests pass (>90% pass rate)
- [ ] YAWL engine starts without errors
- [ ] Interface A: Specification upload works
- [ ] Interface B: Case creation works
- [ ] Interface B: Work item handling works
- [ ] Interface X: Custom service callbacks work
- [ ] JSF UI: Resource service loads
- [ ] JSF UI: Forms submit correctly
- [ ] Mail service: Emails send successfully
- [ ] JAXB: XML serialization/deserialization works
- [ ] No ClassNotFoundException in logs
- [ ] No NoClassDefFoundError in logs
- [ ] No mixing of javax/jakarta namespaces
- [ ] Documentation updated
- [ ] Changes committed to git

---

## Success Criteria

Migration is considered successful when:

1. **Compilation:** `mvn clean compile` succeeds with 0 errors
2. **Testing:** >90% of unit tests pass
3. **Deployment:** Application deploys to Tomcat 10+ without errors
4. **Functionality:** All core YAWL operations work correctly
5. **No Regressions:** All existing features still function
6. **Performance:** No significant performance degradation
7. **Logs:** No jakarta/javax mixing errors in logs

---

## Timeline

| Phase | Duration | Activity |
|-------|----------|----------|
| Preparation | 30 min | Backup, verify environment |
| Execution | 5 min | Run migration scripts |
| Review | 30 min | Review changes in git |
| Compilation | 15 min | Maven compile and test |
| Manual Updates | 30 min | Update web.xml, faces-config.xml |
| Deployment | 15 min | Deploy to test server |
| Testing | 2 hours | Functional testing |
| Documentation | 1 hour | Update docs |
| **Total** | **~5 hours** | Complete migration |

---

## Post-Migration Tasks

1. **Documentation Updates:**
   - [ ] Update installation guide
   - [ ] Update deployment guide
   - [ ] Update developer quick start
   - [ ] Update architecture documentation

2. **Stakeholder Communication:**
   - [ ] Notify development team
   - [ ] Update deployment playbooks
   - [ ] Document breaking changes
   - [ ] Update support documentation

3. **Continuous Integration:**
   - [ ] Update CI/CD pipelines
   - [ ] Update Docker images
   - [ ] Update Kubernetes deployments
   - [ ] Update Terraform configurations

4. **Knowledge Transfer:**
   - [ ] Team training on Jakarta EE
   - [ ] Document common issues
   - [ ] Create troubleshooting guide

---

## Contacts

**Migration Lead:** YAWL Architecture Team
**Technical Support:** support@yawlfoundation.org
**Emergency Rollback:** Follow procedures in "Rollback Procedures" section

---

## Appendix: Quick Reference

### Files Modified by Migration

- **Servlet Classes:** ~50 files in `src/org/yawlfoundation/yawl/engine/interfce/`
- **JSF Beans:** ~25 files in `src/org/yawlfoundation/yawl/resourcing/jsf/`
- **Mail Services:** 3 files in `src/org/yawlfoundation/yawl/mailSender/` and `mailService/`
- **Metrics:** 3 files in `src/org/yawlfoundation/yawl/engine/actuator/metrics/`
- **Configuration:** `pom.xml`, `build/build.xml`

### Scripts Provided

- `/home/user/yawl/verify-migration-status.sh` - Pre-migration verification
- `/home/user/yawl/migrate-jakarta.sh` - Comprehensive migration with dry-run option
- `/home/user/yawl/execute-jakarta-migration.sh` - Detailed execution with verification
- `/home/user/yawl/migrate_javax_to_jakarta.py` - Python alternative

### Documentation

- `/home/user/yawl/JAKARTA_MIGRATION_README.md` - Quick start guide
- `/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md` - Detailed migration guide
- `/home/user/yawl/docs/architecture/adr/ADR-011-jakarta-ee-migration.md` - Architecture decision record
- `/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md` - This document

---

**Status:** READY FOR EXECUTION
**Last Updated:** 2026-02-15
**Version:** 1.0
