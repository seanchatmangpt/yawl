# Jakarta Mail Migration - TODO

**Status**: ⏸️ BLOCKED - Missing Jakarta Mail 2.1+ Libraries
**Date**: 2026-02-16
**Priority**: Medium

## Problem Statement

YAWL codebase needs to migrate from `javax.mail.*` to `jakarta.mail.*` as part of the broader Jakarta EE 9+ migration. However, the required Jakarta Mail libraries are not currently available in the build system.

## Current Situation

### JAR File Mismatch

The file `/home/user/yawl/build/3rdParty/lib/jakarta.mail-1.6.7.jar` has a **misleading name**:

```bash
# Despite the filename, this JAR contains OLD javax.mail.* packages:
$ unzip -l jakarta.mail-1.6.7.jar | grep "javax/mail" | head -3
        0  2021-04-08 08:33   javax/mail/
        0  2021-04-08 08:33   javax/mail/event/
        0  2021-04-08 08:33   javax/mail/search/
```

**Why**: Jakarta Mail 1.6.x was a transitional release that kept `javax.mail` packages for backward compatibility. True Jakarta Mail 2.0+ uses `jakarta.mail.*` packages.

### Version History

| Version | Package Namespace | Notes |
|---------|------------------|-------|
| JavaMail 1.6.x | `javax.mail.*` | Legacy JavaEE |
| Jakarta Mail 1.6.x | `javax.mail.*` | Transitional (Eclipse Foundation) |
| **Jakarta Mail 2.0+** | `jakarta.mail.*` | ✅ True Jakarta EE 9+ |

## Required Actions

### 1. Add Jakarta Mail 2.1+ Libraries

Download and add to `build/3rdParty/lib/`:

```bash
# Jakarta Mail API 2.1.0 (or newer)
wget https://repo1.maven.org/maven2/jakarta/mail/jakarta.mail-api/2.1.0/jakarta.mail-api-2.1.0.jar

# Jakarta Activation 2.0.1 (required dependency)
wget https://repo1.maven.org/maven2/com/sun/activation/jakarta.activation/2.0.1/jakarta.activation-2.0.1.jar

# Jakarta Mail Implementation (Angus Mail - reference implementation)
wget https://repo1.maven.org/maven2/org/eclipse/angus/angus-mail/2.0.1/angus-mail-2.0.1.jar
```

### 2. Update build.xml Properties

```xml
<!-- Replace old properties -->
<property name="jakarta-mail" value="jakarta.mail-api-2.1.0.jar"/>
<property name="jakarta-activation" value="jakarta.activation-2.0.1.jar"/>
<property name="angus-mail" value="angus-mail-2.0.1.jar"/>
```

### 3. Update Classpath References

Update these sections in `build/build.xml`:

```xml
<!-- Line ~736-737: Main compile classpath -->
<pathelement location="${lib.dir}/${jakarta-mail}"/>
<pathelement location="${lib.dir}/${jakarta-activation}"/>
<pathelement location="${lib.dir}/${angus-mail}"/>

<!-- Line ~866-868: cp.mail classpath -->
<path id="cp.mail">
    <pathelement location="${lib.dir}/${jakarta-mail}"/>
    <pathelement location="${lib.dir}/${jakarta-activation}"/>
    <pathelement location="${lib.dir}/${angus-mail}"/>
    <!-- ... rest of mail dependencies ... -->
</path>

<!-- Line ~655: webapp_mailSender.libs -->
<property name="webapp_mailSender.libs"
          value="${jdom} ${log4j.libs} ${jakarta-mail} ${jakarta-activation} ${angus-mail}
                 ${commonsIO} ${commonsFileupload} ${commonsCodec} ${xerces}"/>
```

### 4. Migrate Java Source Code

Once libraries are in place, update these files:

#### File: `src/org/yawlfoundation/yawl/mailSender/MailSender.java`

```java
// OLD (lines 28-34)
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

// NEW
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
```

#### File: `src/org/yawlfoundation/yawl/mailService/MailService.java`

```java
// OLD (line 36)
import javax.mail.Message;

// NEW
import jakarta.mail.Message;
```

### 5. Remove Old JavaMail Libraries (Optional Cleanup)

After verifying the migration works:

```bash
# These can be removed or archived:
rm build/3rdParty/lib/jakarta.mail-1.6.7.jar  # Contains javax.mail, not jakarta.mail
rm build/3rdParty/lib/mail.jar                # Legacy JavaMail
rm build/3rdParty/lib/mailapi.jar             # Legacy JavaMail API
rm build/3rdParty/lib/activation.jar          # Legacy JAF
rm build/3rdParty/lib/javax.activation-api-1.2.0.jar  # Old API
```

**Keep**: `jakarta.activation-1.2.2.jar` might be needed for backward compatibility during transition.

## Testing Checklist

After migration:

- [ ] `ant clean compile` succeeds
- [ ] `ant unitTest` passes (especially mail-related tests)
- [ ] MailSender webapp deploys correctly
- [ ] Email sending functionality works
- [ ] No runtime ClassNotFoundException for mail classes
- [ ] No NoSuchMethodError exceptions (API compatibility verified)

## API Compatibility Notes

Jakarta Mail 2.0+ is **mostly backward compatible** with JavaMail 1.6, but watch for:

1. **Package renames** (handled by import changes)
2. **Deprecated methods removed** (check MailSender.java for deprecated API usage)
3. **SSL/TLS configuration changes** (verify mail.smtp.socketFactory.class still works)

## Maven Dependencies (for reference)

If using Maven build (pom.xml):

```xml
<dependency>
    <groupId>jakarta.mail</groupId>
    <artifactId>jakarta.mail-api</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
    <version>2.0.1</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>jakarta.activation</groupId>
    <artifactId>jakarta.activation-api</artifactId>
    <version>2.1.0</version>
</dependency>
```

## Related Issues

- ✅ javax.xml.bind → jakarta.xml.bind (COMPLETED in commit 9d588d1)
- ✅ org.apache.commons.lang → lang3 (COMPLETED in commit 9d588d1)
- ⏸️ javax.mail → jakarta.mail (THIS ISSUE - blocked on library availability)
- 🔲 javax.servlet → jakarta.servlet (PARTIALLY DONE - some files already migrated)

## References

- [Jakarta Mail Specification 2.1](https://jakarta.ee/specifications/mail/2.1/)
- [Angus Mail (Reference Implementation)](https://eclipse-ee4j.github.io/angus-mail/)
- [Migration Guide: JavaMail → Jakarta Mail](https://eclipse-ee4j.github.io/angus-mail/docs/JavaMail-1.6-MigrationGuide.pdf)
- [YAWL Import Migration Checklist](./IMPORT_MIGRATION_CHECKLIST.md)
