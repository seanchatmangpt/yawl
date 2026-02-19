# YAWL v6.0.0 Documentation Portal

**Last Updated:** 2026-02-16
**Build Status:** ACTIVE
**Java Version:** 25
**Framework:** Jakarta EE 10 + JUnit 5 + Hibernate 6
**Portal Maintainer:** Documentation Specialist

---

## Navigation by Role

### For New Developers
Start here:
1. [README.md](README.md) - Project overview & Java 25 setup
2. [CLAUDE.md](CLAUDE.md) - Development workflow & agent coordination
3. [.claude/BEST-PRACTICES-2026.md](.claude/BEST-PRACTICES-2026.md) - Modern Java standards

Then explore:
- [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - Current build status
- [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md) - Testing framework reference

### For Build/DevOps Engineers
Priority order:
1. [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - What's broken/working
2. [BUILD_HEALTH_REPORT.md](BUILD_HEALTH_REPORT.md) - Detailed error analysis
3. [DEPENDENCY_MATRIX.md](DEPENDENCY_MATRIX.md) - Dependency audit & cleanup
4. [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md) - Emergency procedures

### For Integration Specialists
Essential reading:
1. [INTEGRATION_README.md](INTEGRATION_README.md) - A2A & MCP integration overview
2. [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - Detailed integration setup
3. [docs/REST-API-JAX-RS.md](docs/REST-API-JAX-RS.md) - JAX-RS endpoint reference
4. [docs/JAX-RS-IMPLEMENTATION-SUMMARY.md](docs/JAX-RS-IMPLEMENTATION-SUMMARY.md) - Implementation details

### For QA/Testing Teams
Required documentation:
1. [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md) - JUnit 5 testing guide
2. [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md) - Verification checklist
3. [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - Success criteria

### For Release/Operations
Deployment checklist:
1. [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - Readiness criteria
2. [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md) - Rollback procedures
3. [RELEASE_NOTES_v5.2.md](RELEASE_NOTES_v5.2.md) - What's new & breaking changes
4. [SECURITY_FIX_PHASE_1_2.md](SECURITY_FIX_PHASE_1_2.md) - Security considerations

---

## Complete Documentation Inventory

### Core Project Documentation
| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| [README.md](README.md) | Project overview, features, quick start | Everyone | Current |
| [CLAUDE.md](CLAUDE.md) | Development instructions, agent system | Developers | Current |
| [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) | This master portal | Everyone | Current |

### Build & Validation
| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) | Build status, blockers, fix timeline | DevOps/Devs | Current |
| [BUILD_HEALTH_REPORT.md](BUILD_HEALTH_REPORT.md) | Detailed build analysis & errors | Build Engineers | Current |
| [DEPENDENCY_MATRIX.md](DEPENDENCY_MATRIX.md) | All 210+ dependencies, license audit | DevOps/Security | Current |
| [JAKARTA_FACES_CLASSPATH.md](JAKARTA_FACES_CLASSPATH.md) | JSF/Faces classpath resolution | Build Engineers | Current |

### Migration Guides (Completed)
| Document | From → To | Scope | Status |
|----------|-----------|-------|--------|
| [JAKARTA_SERVLET_MIGRATION.md](JAKARTA_SERVLET_MIGRATION.md) | javax.servlet → jakarta.servlet | Web tier | Complete |
| [JUNIT5_MIGRATION_SUMMARY.md](JUNIT5_MIGRATION_SUMMARY.md) | JUnit 4 → JUnit 5 | Testing | Complete |
| [JSP_XHTML_MIGRATION.md](JSP_XHTML_MIGRATION.md) | JSP → XHTML/Facelets | Templates | Complete |
| [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md) | Verification checklist | QA | Complete |

### Phase Deliverables
| Document | Phase | Focus | Status |
|----------|-------|-------|--------|
| [PHASE4_INTEGRATION_MODERNIZATION.md](PHASE4_INTEGRATION_MODERNIZATION.md) | Phase 4 | A2A/MCP integration | Complete |
| [PHASE5_SUMMARY.md](PHASE5_SUMMARY.md) | Phase 5 | Completion & next steps | Complete |
| [AGENT_REGISTRY_IMPLEMENTATION.md](AGENT_REGISTRY_IMPLEMENTATION.md) | Agent System | Registry implementation | Complete |

### Integration & API
| Document | Type | Audience | Status |
|----------|------|----------|--------|
| [INTEGRATION_README.md](INTEGRATION_README.md) | Quick start | Integrators | Current |
| [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) | Detailed guide | Integrators | Current |
| [docs/REST-API-JAX-RS.md](docs/REST-API-JAX-RS.md) | API Reference | Developers | Current |
| [docs/JAX-RS-IMPLEMENTATION-SUMMARY.md](docs/JAX-RS-IMPLEMENTATION-SUMMARY.md) | Implementation | Developers | Current |

### Testing & Quality
| Document | Topic | Audience | Status |
|----------|-------|----------|--------|
| [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md) | JUnit 5 cheatsheet | QA/Devs | Current |
| [docs/PROMPT_CLOSE_TEST_GAPS.md](docs/PROMPT_CLOSE_TEST_GAPS.md) | Test gap analysis | QA Lead | Current |
| [.claude/BEST-PRACTICES-2026.md](.claude/BEST-PRACTICES-2026.md) | Quality standards | Everyone | Current |

### Deployment & Operations
| Document | Topic | Audience | Status |
|----------|-------|----------|--------|
| [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md) | Emergency rollback | DevOps | Current |
| [SECURITY_FIX_PHASE_1_2.md](SECURITY_FIX_PHASE_1_2.md) | Security hardening | Security/DevOps | Current |

### Reference & Standards
| Document | Type | Audience | Status |
|----------|------|----------|--------|
| [.claude/HYPER_STANDARDS.md](.claude/HYPER_STANDARDS.md) | Code standards | Developers | Reference |
| [.claude/README-QUICK.md](.claude/README-QUICK.md) | Quick reference | Everyone | Reference |
| [.claude/INDEX.md](.claude/INDEX.md) | Internal portal | Developers | Reference |
| [.claude/CAPABILITIES.md](.claude/CAPABILITIES.md) | System capabilities | Architects | Reference |
| [.claude/80-20-ANALYSIS.md](.claude/80-20-ANALYSIS.md) | Priority analysis | Managers | Reference |

### Research & Vision
| Document | Type | Purpose | Status |
|----------|------|---------|--------|
| [docs/THESIS_Autonomous_Workflow_Agents.md](docs/THESIS_Autonomous_Workflow_Agents.md) | Research | Agent theory | Reference |
| [docs/pasadena-tdd-manifesto.md](docs/pasadena-tdd-manifesto.md) | Manifesto | Testing philosophy | Reference |

---

## Quick Command Reference

### Documentation Access
```bash
# View this index
cat /home/user/yawl/DOCUMENTATION_INDEX.md

# Find documents by keyword
grep -r "keyword" /home/user/yawl/*.md

# List all markdown docs (human-readable)
ls -lh /home/user/yawl/*.md
ls -lh /home/user/yawl/docs/*.md
ls -lh /home/user/yawl/.claude/*.md
```

### Build & Validation
```bash
# Check build status
ant compile

# Run full validation
ant buildAll

# Run unit tests
ant unitTest

# Quick status check
cat /home/user/yawl/BUILD_VALIDATION_SUMMARY.md
```

### Project Navigation
```bash
# View README
less /home/user/yawl/README.md

# View project instructions
less /home/user/yawl/CLAUDE.md

# View build health
cat /home/user/yawl/BUILD_HEALTH_REPORT.md

# View dependencies
cat /home/user/yawl/DEPENDENCY_MATRIX.md
```

---

## Document Search Guide

### Finding Documentation

**By Topic:**
- **Build Issues** → BUILD_VALIDATION_SUMMARY.md, BUILD_HEALTH_REPORT.md
- **Integration** → INTEGRATION_README.md, INTEGRATION_GUIDE.md
- **Testing** → JUNIT5_MIGRATION_SUMMARY.md, docs/JUNIT5_QUICK_REFERENCE.md
- **Deployment** → docs/ROLLBACK-PROCEDURES.md, DEPLOYMENT guides
- **Security** → SECURITY_FIX_PHASE_1_2.md, DEPENDENCY_MATRIX.md
- **Standards** → .claude/BEST-PRACTICES-2026.md, .claude/HYPER_STANDARDS.md

**By Audience:**
- **New Developers** → README.md, CLAUDE.md, .claude/BEST-PRACTICES-2026.md
- **DevOps** → BUILD_VALIDATION_SUMMARY.md, BUILD_HEALTH_REPORT.md, docs/ROLLBACK-PROCEDURES.md
- **Architects** → CLAUDE.md, .claude/CAPABILITIES.md, docs/THESIS*.md
- **QA Teams** → MIGRATION_VERIFICATION.md, docs/JUNIT5_QUICK_REFERENCE.md
- **Managers** → BUILD_VALIDATION_SUMMARY.md, .claude/80-20-ANALYSIS.md

**By Phase:**
- **Phase 1-2** → SECURITY_FIX_PHASE_1_2.md, JAKARTA_SERVLET_MIGRATION.md
- **Phase 3-4** → JUNIT5_MIGRATION_SUMMARY.md, JSP_XHTML_MIGRATION.md, PHASE4_INTEGRATION_MODERNIZATION.md
- **Phase 5+** → PHASE5_SUMMARY.md, INTEGRATION_README.md

---

## File Locations

### Documentation Root
```
/home/user/yawl/*.md                    # Master documentation files
```

### Subdirectories
```
/home/user/yawl/docs/*.md               # API, deployment, research
/home/user/yawl/.claude/*.md            # Internal standards & guidelines
/home/user/yawl/k8s/README.md           # Kubernetes deployment
```

### Build System
```
/home/user/yawl/build/build.xml         # Ant build configuration
/home/user/yawl/build/build.properties  # Build properties
/home/user/yawl/build/3rdParty/lib/     # JAR dependencies
```

---

## Documentation Standards

All documentation in this portal adheres to:

**Format Standards:**
- Markdown (.md) format for readability
- Clear hierarchical headings (H1, H2, H3)
- Absolute file paths (never relative)
- Code blocks with language specifications
- Tables for structured data

**Content Standards:**
- Purpose clearly stated at document top
- Target audience identified
- Status indicator (Complete, Current, In Progress, Reference)
- Table of contents for documents >2000 words
- Links to related documentation

**Maintenance Standards:**
- Last updated timestamp on all documents
- Status badges for currency tracking
- Revision history for major changes
- Deprecation notices for obsolete docs

---

## Document Status Legend

| Status | Meaning | Action |
|--------|---------|--------|
| **Current** | Updated within 2 weeks | Use as-is |
| **Complete** | Migration/phase finished | Reference use |
| **Reference** | Guidelines & standards | Ongoing reference |
| **In Progress** | Active work | Check for updates |
| **Deprecated** | No longer used | Archive or remove |

---

## Master Documentation Workflow

### Adding New Documentation
1. Create markdown file in appropriate directory
2. Include header with purpose, audience, status
3. Add entry to DOCUMENTATION_INDEX.md table
4. Link from relevant role/topic sections
5. Commit with `docs:` prefix

### Updating Documentation
1. Change content in source file
2. Update "Last Updated" date
3. Update status if needed
4. Run spelling/link checks
5. Commit with `docs: update <topic>`

### Archiving Documentation
1. Add "DEPRECATED: [reason]" at top
2. Add "See instead: [link]" with redirect
3. Keep file accessible for historical reference
4. Note in DOCUMENTATION_INDEX.md

---

## Quick Links Summary

**Essential (Read First):**
- [README.md](README.md) - Start here for overview
- [CLAUDE.md](CLAUDE.md) - Then read project instructions
- [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - Then check build status

**Troubleshooting (When Issues Arise):**
- [BUILD_HEALTH_REPORT.md](BUILD_HEALTH_REPORT.md) - Build compilation errors
- [DEPENDENCY_MATRIX.md](DEPENDENCY_MATRIX.md) - Missing/duplicate JARs
- [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md) - Migration failures

**Deployment (Before Release):**
- [docs/ROLLBACK-PROCEDURES.md](docs/ROLLBACK-PROCEDURES.md) - Rollback procedures
- [SECURITY_FIX_PHASE_1_2.md](SECURITY_FIX_PHASE_1_2.md) - Security checklist
- [BUILD_VALIDATION_SUMMARY.md](BUILD_VALIDATION_SUMMARY.md) - Readiness criteria

**Development (Daily Work):**
- [.claude/BEST-PRACTICES-2026.md](.claude/BEST-PRACTICES-2026.md) - Code standards
- [docs/JUNIT5_QUICK_REFERENCE.md](docs/JUNIT5_QUICK_REFERENCE.md) - Testing guide
- [docs/REST-API-JAX-RS.md](docs/REST-API-JAX-RS.md) - API reference

---

## Documentation Portal Statistics

**Total Documents:** 35+ markdown files
**Total Documentation:** ~350 KB
**Categories:** 12 functional areas
**Average Document Length:** 8-12 KB
**Update Frequency:** Weekly
**Last Comprehensive Update:** 2026-02-16

---

## Version History

| Date | Changes | Session |
|------|---------|---------|
| 2026-02-16 | Master portal created, navigation by role added, complete inventory | This session |
| 2026-02-15 | BUILD_HEALTH_REPORT.md completed | Prior |
| 2026-02-15 | DEPENDENCY_MATRIX.md completed | Prior |
| 2026-02-15 | BUILD_VALIDATION_SUMMARY.md created | Prior |

---

## Support & Feedback

For documentation issues:
1. Check this index first
2. Search by topic or audience
3. Review related documents in linked section
4. Contact project maintainers for updates

**Documentation Maintainer:** Architecture Team
**Contact:** Via project issue tracker
**Feedback:** Submit via PR with improvements

---

**Portal Status:** OPERATIONAL
**Last Verified:** 2026-02-16
**Java Version:** 25
**Framework Stack:** Jakarta EE 10 + JUnit 5 + Hibernate 6
