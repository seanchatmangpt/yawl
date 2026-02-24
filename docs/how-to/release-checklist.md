# YAWL v6.0.0 Release Checklist

**Version**: 6.0.0 | **Release Type**: Major | **Status**: Preparation

---

## Pre-Release (T-7 days)

### Documentation Completeness

- [ ] All 89 packages have `package-info.java` files
- [ ] Master INDEX.md created and validated (`docs/INDEX.md`)
- [ ] All internal links verified (0 broken)
- [ ] Observatory facts up-to-date
- [ ] Performance baselines measured
- [ ] CLAUDE.md updated with v6.0.0 references

### Validation Scripts

- [ ] `bash scripts/validation/validate-documentation.sh` passes
- [ ] `bash scripts/validation/validate-observatory.sh` passes
- [ ] `bash scripts/validation/validate-performance-baselines.sh` passes
- [ ] GitHub Actions CI passes on master branch

### Code Quality

- [ ] `mvn -T 1.5C clean compile` succeeds (0 errors)
- [ ] `mvn -T 1.5C clean test` succeeds (0 failures)
- [ ] `mvn clean verify -P analysis` passes
- [ ] No SpotBugs high-priority findings
- [ ] No PMD critical violations
- [ ] Checkstyle warnings < 10

### Security

- [ ] `mvn cyclonedx:makeBom` generates SBOM
- [ ] No known HIGH/CRITICAL vulnerabilities in dependencies
- [ ] TLS 1.3 configuration verified
- [ ] No hardcoded secrets in codebase
- [ ] Security scan (Bandit/Trivy) passes

### Compatibility

- [ ] Java 25 compatibility verified
- [ ] Database migration scripts tested (if any)
- [ ] API backward compatibility verified
- [ ] Breaking changes documented

---

## Release Day (T-0)

### Final Validation

- [ ] `bash scripts/validation/validate-release.sh` passes
- [ ] All quality gates green
- [ ] No uncommitted changes in working directory

### Version Update

- [ ] Update version in all pom.xml files to `6.0.0`
- [ ] Update version in CLAUDE.md header
- [ ] Update version in docs/INDEX.md
- [ ] Update CHANGELOG.md with release notes

### Build & Tag

```bash
# Final build
mvn -T 1.5C clean package -DskipTests

# Create tag
git tag -a v6.0.0 -m "YAWL v6.0.0 release

Features:
- Complete documentation overhaul (89 package-info.java)
- Observatory system for codebase instrumentation
- Validation scripts for CI/CD
- Performance baselines and SLAs
- Java 25 optimization patterns

Migration:
- See docs/MIGRATION-v5.2-to-v6.0.md for upgrade guide
"

# Push tag
git push origin v6.0.0
```

### Artifacts

- [ ] JARs published to Maven Central (or repository)
- [ ] Docker images built and pushed
- [ ] SBOM attached to GitHub release
- [ ] Source archive generated

### GitHub Release

```markdown
## YAWL v6.0.0

### Highlights

- **Documentation**: Complete overhaul with 89 package-info.java files
- **Observatory**: Codebase instrumentation system (9 facts, 8 diagrams)
- **Validation**: Automated CI/CD for documentation quality
- **Performance**: Baselines and SLAs established
- **Java 25**: Full optimization patterns documented

### Breaking Changes

None. API backward compatible with v5.2.

### Migration

See [Migration Guide](docs/MIGRATION-v5.2-to-v6.0.md) for details.

### Artifacts

- [yawl-engine-6.0.0.jar](link)
- [SBOM (CycloneDX)](link)
- [Docker Image](link)

### Contributors

@contributor1, @contributor2, ...
```

---

## Post-Release (T+1 day)

### Verification

- [ ] Maven Central artifacts available (can take 2-4 hours)
- [ ] Docker images pullable
- [ ] GitHub release visible
- [ ] Documentation site updated

### Communication

- [ ] Announcement sent to mailing list
- [ ] Twitter/X post published
- [ ] Blog post published (if major)
- [ ] Internal team notified

### Cleanup

- [ ] Create `release/6.0.x` branch for patches
- [ ] Update `master` to next development version (6.1.0-SNAPSHOT)
- [ ] Close all issues tagged with `v6.0.0`
- [ ] Update project board

---

## Rollback Plan

If critical issues found post-release:

### Immediate (within 24 hours)

```bash
# Revert release commit
git revert <commit-sha>

# Delete tag
git tag -d v6.0.0
git push origin :v6.0.0

# Re-publish as v6.0.1
# (follow release process with fix)
```

### Documented (after 24 hours)

1. Create hotfix branch from `v6.0.0` tag
2. Apply fix
3. Release as `v6.0.1`
4. Update CHANGELOG with fix details
5. Notify users of patch release

### Communication

- GitHub issue for rollback tracking
- Update release notes with rollback notice
- Notify all known users via mailing list

---

## Checklist Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Release Manager | | | |
| Tech Lead | | | |
| QA Lead | | | |
| Documentation Lead | | | |

---

## Related Documents

- [Final Implementation Plan](FINAL-IMPLEMENTATION-PLAN.md)
- [Migration Guide v5.2 to v6.0](MIGRATION-v5.2-to-v6.0.md)
- [Performance SLA](v6/latest/performance/SLA.md)
- [Observatory Index](v6/latest/INDEX.md)

---

**Template Version**: 1.0
**Last Updated**: 2026-02-18
