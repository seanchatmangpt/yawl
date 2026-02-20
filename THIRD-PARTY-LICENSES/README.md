# YAWL v6.0.0 - Third-Party License Texts

This directory contains complete license texts for third-party dependencies used in YAWL v6.0.0.

## Directory Structure

```
THIRD-PARTY-LICENSES/
├── README.md (this file)
├── LICENSE-LGPL-v2.1.txt (LGPL v2.1 for H2, HSQLDB)
├── LICENSE-LGPL-v3.txt (LGPL v3 reference)
├── LICENSE-APACHE-2.0.txt (Apache License 2.0)
├── LICENSE-EPL-2.0.txt (Eclipse Public License 2.0)
└── ATTRIBUTION.md (Required attribution notices)
```

## License Categories

### LGPL v2.1+ (Weak Copyleft)

**Libraries**: H2 Database, HSQLDB

- **File**: `LICENSE-LGPL-v2.1.txt`
- **Requirement**: Source code modifications must be disclosed
- **YAWL Compliance**: These are runtime dependencies; any modifications to their source must be documented
- **Distribution**: License text included in this directory

### Apache License 2.0 (Permissive)

**Libraries**: ~45 dependencies (Apache Commons, Jersey, Hibernate, Jackson, etc.)

- **File**: `LICENSE-APACHE-2.0.txt`
- **Requirement**: Include license and NOTICE file in distribution
- **YAWL Compliance**: NOTICE files included in distribution package

### Eclipse Public License 2.0 (Permissive)

**Libraries**: JUnit, Jakarta, JAXB

- **File**: `LICENSE-EPL-2.0.txt`
- **Requirement**: Include license in distribution
- **YAWL Compliance**: Included in distribution package

### MIT License (Permissive)

**Libraries**: Guava, JDOM, and others

- Included in Apache 2.0 group
- Permissive with attribution required

### BSD License (Permissive)

**Libraries**: ANTLR, Jaxen, Saxon, JUNG

- Included in Apache 2.0 group
- Permissive with attribution required

---

## Source Code Modifications

### H2 Database

If H2 source code is modified, disclosure is required:

1. Create a file: `H2_MODIFICATIONS.md`
2. Document what was changed and why
3. Include in source distribution
4. Reference this document in release notes

**Example**:
```markdown
# H2 Database Source Modifications

## Version: 2.2.224

### Modifications
- File: `org/h2/Server.java`
- Change: Added connection pooling support
- Reason: Performance optimization for high-concurrency scenarios
- Date: 2026-02-20
```

### HSQLDB

If HSQLDB source code is modified, disclosure is required:

1. Create a file: `HSQLDB_MODIFICATIONS.md`
2. Document what was changed and why
3. Include in source distribution

---

## Compliance Verification

### Source Distribution Checklist

- [ ] All license texts present in `THIRD-PARTY-LICENSES/`
- [ ] `LICENSES.md` references this directory
- [ ] LGPL modifications documented (if any)
- [ ] NOTICE files for Apache 2.0 libraries included
- [ ] ATTRIBUTION.md is complete and accurate
- [ ] No GPL-licensed libraries included
- [ ] Dependency tree validated (no hidden GPL)

### Binary Distribution Checklist

- [ ] License texts bundled in JAR/package
- [ ] `THIRD-PARTY-LICENSES/` directory included
- [ ] NOTICE file in META-INF/
- [ ] No source code of LGPL libraries included (unless modified)
- [ ] README (this file) included
- [ ] Verification: `mvn license:aggregate-add-third-party`

---

## License Compliance Tool

Use Maven to verify licenses:

```bash
# Generate license report
mvn license:aggregate-add-third-party

# Check for GPL/problematic licenses
mvn -P analysis verify
```

---

## Distribution Instructions

### When Packaging YAWL for Distribution

1. **Copy This Directory**
   ```bash
   cp -r THIRD-PARTY-LICENSES/ dist/
   ```

2. **Include LICENSES.md**
   ```bash
   cp LICENSES.md dist/
   ```

3. **Add NOTICE File** (Apache 2.0 requirement)
   ```bash
   echo "YAWL includes software developed by Apache Software Foundation" > dist/NOTICE
   ```

4. **Update Installation Guide**
   ```
   License information is in: THIRD-PARTY-LICENSES/
   ```

### For GCP Marketplace

1. Upload `THIRD-PARTY-LICENSES/` to repository
2. Include in Docker image
3. Reference in marketplace listing
4. Provide license statement in GCP console

---

## License Scanning

### Automated Tools

```bash
# Using FOSSA
fossa analyze --update-license-report

# Using Black Duck
black-duck-scan.sh

# Using WhiteSource
whitesource-scan.sh
```

### Manual Review

Every 6 months, review:
1. New dependencies added
2. License changes in updates
3. GPL or AGPL creeping in
4. Proprietary licenses conflicts

---

## Known Licensing Issues

### None Currently

All dependencies are compatible with commercial use. No GPL libraries detected.

### Deprecated Libraries (Plan Migration)

- **JAX-RPC** (deprecated, migrate to REST)
- **AXIS** (deprecated, migrate to modern SOAP/REST)
- **Legacy bundled JARs** (source licensing unclear)

---

## Contact & Questions

**License Compliance Officer**
- Email: licensing@yawlfoundation.org
- Response: Within 48 hours

**Legal Review**
- Email: legal@yawlfoundation.org
- For GPL/AGPL concerns

---

## Revision History

| Date | Version | Changes |
|------|---------|---------|
| 2026-02-20 | 1.0 | Initial creation for GCP Marketplace Launch |

---

## Related Documents

- `LICENSES.md` - Full license inventory
- `.claude/HYPER_STANDARDS.md` - Code quality standards
- `pom.xml` - Maven dependency declarations
- `scripts/check-licenses.sh` - License verification script

---

**Last Updated**: February 20, 2026
**Classification**: Public
