# YAWL Jakarta EE Migration - Documentation Index

**YAWL v5.2**
**Migration:** Java EE 8 (javax.*) → Jakarta EE 10 (jakarta.*)
**Date:** 2026-02-15

---

## Quick Navigation

### For Immediate Execution

1. **Start Here:** [`/home/user/yawl/MIGRATION_SUMMARY.md`](/home/user/yawl/MIGRATION_SUMMARY.md)
   - Overview of what will change
   - Quick execution steps
   - Expected results

2. **Quick Start Guide:** [`/home/user/yawl/JAKARTA_MIGRATION_README.md`](/home/user/yawl/JAKARTA_MIGRATION_README.md)
   - Fast-track migration guide
   - Common issues and solutions
   - Rollback instructions

3. **Execution Plan:** [`/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md`](/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md)
   - Detailed step-by-step execution
   - Timeline and checkpoints
   - Validation procedures

### For Technical Details

4. **Comprehensive Migration Guide:** [`/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md`](/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md)
   - Complete technical documentation
   - API-by-API migration details
   - Testing strategies
   - Troubleshooting guide

5. **Architecture Decision Record:** [`/home/user/yawl/docs/architecture/adr/ADR-011-jakarta-ee-migration.md`](/home/user/yawl/docs/architecture/adr/ADR-011-jakarta-ee-migration.md)
   - Strategic rationale
   - Business drivers
   - Technical constraints
   - Risk assessment

---

## Migration Scripts

### Primary Execution Scripts

| Script | Location | Purpose | Usage |
|--------|----------|---------|-------|
| **Verification** | `/home/user/yawl/verify-migration-status.sh` | Analyze current state | `./verify-migration-status.sh` |
| **Dry Run** | `/home/user/yawl/migrate-jakarta.sh` | Preview changes | `./migrate-jakarta.sh --dry-run` |
| **Execution** | `/home/user/yawl/execute-jakarta-migration.sh` | Execute migration | `./execute-jakarta-migration.sh` |
| **Python Alternative** | `/home/user/yawl/migrate_javax_to_jakarta.py` | Python migration tool | `python3 migrate_javax_to_jakarta.py` |

### Script Features

- ✅ Automatic javax.* → jakarta.* replacement
- ✅ Preserves Java SE javax.* packages
- ✅ Detailed logging and reporting
- ✅ Dry-run mode available
- ✅ Rollback support
- ✅ Verification checks

---

## Documentation Structure

```
/home/user/yawl/
│
├── MIGRATION_SUMMARY.md                    ← START HERE (Quick overview)
├── JAKARTA_MIGRATION_README.md             ← Quick start guide
│
├── docs/
│   ├── migration/
│   │   ├── INDEX.md                        ← This file
│   │   ├── JAVAX_TO_JAKARTA_MIGRATION.md   ← Complete technical guide
│   │   └── MIGRATION_EXECUTION_PLAN.md     ← Step-by-step execution
│   │
│   └── architecture/
│       └── adr/
│           └── ADR-011-jakarta-ee-migration.md  ← Architecture decision
│
├── verify-migration-status.sh              ← Pre-migration verification
├── migrate-jakarta.sh                      ← Comprehensive migration (dry-run)
├── execute-jakarta-migration.sh            ← Execution with verification
└── migrate_javax_to_jakarta.py            ← Python migration tool
```

---

## Reading Guide by Role

### For Project Managers

**Read these first:**
1. [`MIGRATION_SUMMARY.md`](/home/user/yawl/MIGRATION_SUMMARY.md) - Overview and timeline
2. [`ADR-011-jakarta-ee-migration.md`](/home/user/yawl/docs/architecture/adr/ADR-011-jakarta-ee-migration.md) - Business rationale
3. [`MIGRATION_EXECUTION_PLAN.md`](/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md) - Detailed timeline

**Key sections:**
- Migration scope and impact
- Timeline and resource requirements
- Risk assessment and mitigation
- Success criteria

### For Developers

**Read these first:**
1. [`JAKARTA_MIGRATION_README.md`](/home/user/yawl/JAKARTA_MIGRATION_README.md) - Quick start
2. [`JAVAX_TO_JAKARTA_MIGRATION.md`](/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md) - Technical details

**Key sections:**
- API migration mappings
- Code examples (before/after)
- Testing procedures
- Troubleshooting guide

### For DevOps/SRE

**Read these first:**
1. [`MIGRATION_EXECUTION_PLAN.md`](/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md) - Deployment guide
2. [`JAVAX_TO_JAKARTA_MIGRATION.md`](/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md) - Configuration updates

**Key sections:**
- Application server requirements
- Configuration file updates
- Deployment procedures
- Rollback strategies
- Monitoring and validation

### For QA/Testers

**Read these first:**
1. [`JAVAX_TO_JAKARTA_MIGRATION.md`](/home/user/yawl/docs/migration/JAVAX_TO_JAKARTA_MIGRATION.md) - Testing strategy
2. [`MIGRATION_EXECUTION_PLAN.md`](/home/user/yawl/docs/migration/MIGRATION_EXECUTION_PLAN.md) - Test procedures

**Key sections:**
- Unit testing guidelines
- Integration test scenarios
- Functional test checklist
- Performance validation
- Regression testing

---

## Migration Workflow

```
┌─────────────────────────────────────┐
│ 1. Read MIGRATION_SUMMARY.md        │
│    (Understand scope and impact)    │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 2. Run verify-migration-status.sh   │
│    (Check current state)            │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 3. Read MIGRATION_EXECUTION_PLAN.md │
│    (Detailed execution steps)       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 4. Execute migration script         │
│    (execute-jakarta-migration.sh)   │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 5. Review changes (git diff)        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 6. Compile and test (mvn)           │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 7. Manual config updates            │
│    (web.xml, faces-config.xml)      │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 8. Deploy and validate              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 9. Commit changes                   │
└─────────────────────────────────────┘
```

---

## Key Concepts

### What Is Being Migrated?

**Java EE APIs (javax.* → jakarta.*):**
- Servlet API
- Mail API
- Activation API
- Annotation API
- JSF API
- JAXB API
- JPA API

**Java SE APIs (staying as javax.*):**
- Swing (Desktop GUI)
- XML Parsing (JAXP)
- JAXP (Transform, Validation, XPath)
- XML Datatypes
- Networking (SSL/TLS)
- Cryptography
- JDBC

### Why This Migration?

1. **Platform Evolution:** Java EE → Jakarta EE (Oracle → Eclipse Foundation)
2. **Modern Servers:** Tomcat 10+, WildFly 27+ require Jakarta EE
3. **Spring Boot 3:** Requires Jakarta EE namespace
4. **Long-term Support:** Jakarta EE is actively developed
5. **Cloud Native:** Better integration with modern cloud platforms

### What Are the Risks?

1. **Binary Incompatibility:** Cannot mix javax.* and jakarta.*
2. **Dependency Updates:** All dependencies must support Jakarta EE
3. **Configuration Changes:** web.xml, faces-config.xml need updates
4. **Testing Required:** Extensive testing needed
5. **Deployment:** Requires Tomcat 10+ or equivalent

---

## FAQ

### Q: Can I run YAWL on Tomcat 9 after migration?
**A:** No. After migration, YAWL requires Tomcat 10+ or another Jakarta EE compatible server.

### Q: Will this break existing YAWL specifications?
**A:** No. YAWL specifications are XML-based and not affected by Java API changes.

### Q: How long does the migration take?
**A:** Script execution: 5 minutes. Full migration with testing: 3-5 hours.

### Q: Can I rollback if something goes wrong?
**A:** Yes. Use `git checkout --` or `git reset --hard HEAD~1` to rollback.

### Q: What about third-party custom services?
**A:** Custom services must also be migrated to Jakarta EE if they use Java EE APIs.

### Q: Do I need to change my code?
**A:** Only import statements change. Business logic remains unchanged.

---

## Migration Impact Summary

| Aspect | Impact | Details |
|--------|--------|---------|
| **Source Code** | HIGH | ~100+ files, ~200+ imports changed |
| **Dependencies** | MEDIUM | All Jakarta EE dependencies added |
| **Configuration** | LOW | web.xml, faces-config.xml updates |
| **Database** | NONE | No database changes required |
| **Specifications** | NONE | XML specifications unchanged |
| **Custom Services** | MEDIUM | May need Jakarta EE migration |
| **Deployment** | HIGH | Requires Tomcat 10+ |
| **Testing** | HIGH | Extensive testing required |

---

## Success Metrics

- ✅ All source files compile
- ✅ >90% unit tests pass
- ✅ Application deploys successfully
- ✅ All core operations work
- ✅ No performance degradation
- ✅ No logs errors
- ✅ Documentation updated

---

## Support Resources

### Internal Resources

- **Migration Scripts:** `/home/user/yawl/*-migration*.sh`
- **Documentation:** `/home/user/yawl/docs/migration/`
- **Architecture Docs:** `/home/user/yawl/docs/architecture/adr/`

### External Resources

- **Jakarta EE Specification:** https://jakarta.ee/specifications/platform/10/
- **Jakarta EE Tutorial:** https://jakarta.ee/learn/docs/jakartaee-tutorial/current/
- **Tomcat 10 Migration:** https://tomcat.apache.org/migration-10.html
- **Spring Boot 3 Migration:** https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide
- **Hibernate 6 Migration:** https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc

### Community Support

- **YAWL Forum:** https://yawlfoundation.org/forum
- **GitHub Issues:** https://github.com/yawlfoundation/yawl/issues
- **Stack Overflow:** Tag: `yawl` + `jakarta-ee`

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-15 | Initial migration documentation |

---

## Document Ownership

- **Created By:** YAWL Architecture Team
- **Maintained By:** Development Team
- **Review Cycle:** Quarterly
- **Last Updated:** 2026-02-15

---

## Related Documentation

- [YAWL Architecture Overview](/home/user/yawl/docs/architecture/README.md)
- [Spring Boot 3.2 Integration ADR](/home/user/yawl/docs/architecture/adr/ADR-001-spring-boot.md)
- [Hibernate 6.5 Upgrade ADR](/home/user/yawl/docs/architecture/adr/ADR-007-hibernate-upgrade.md)
- [Cloud-Native Architecture ADR](/home/user/yawl/docs/architecture/adr/ADR-009-cloud-native.md)

---

**Status:** DOCUMENTATION COMPLETE
**Migration Status:** READY FOR EXECUTION
**Next Step:** Read [`MIGRATION_SUMMARY.md`](/home/user/yawl/MIGRATION_SUMMARY.md)
