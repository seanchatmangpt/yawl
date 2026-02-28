# Incremental Build Quick Reference Card

**Print this out or keep it in your editor's sidebar!**

---

## Daily Builds

```bash
# Build changed modules
bash scripts/dx.sh compile

# Build + test changed modules
bash scripts/dx.sh

# Build specific module
bash scripts/dx.sh -pl yawl-engine

# Test only
bash scripts/dx.sh test
```

---

## Performance

| Build | Time | How It Works |
|-------|------|------------|
| **Comment change** | <2s | No bytecode change → cache hit |
| **Single file change** | <5s | Recompile changed file + deps |
| **Single module change** | <10s | Recompile module + affected |
| **Clean build** | 45-50s | Establish cache baseline |

---

## If Build is Slow

1. **Check cache is enabled**
   ```bash
   ls -la ~/.m2/build-cache/yawl/ | wc -l
   # If 0, cache is empty or not working
   ```

2. **Verify cache is working**
   ```bash
   bash scripts/test-incremental-build.sh
   # If incremental >2s, diagnose with --verbose
   ```

3. **Clear cache and rebuild**
   ```bash
   rm -rf ~/.m2/build-cache/
   mvn clean compile -q -DskipTests
   # Next build will be cached
   ```

---

## IDE Setup (Recommended)

**IntelliJ**: Preferences → Build → Compiler → ☑ Delegate IDE build to Maven
**Eclipse**: Project → Properties → Java Build Path → Ensure Maven integration
**VS Code**: Install "Maven for Java" extension, run Maven goals from command palette

---

## Before Commit

```bash
# Run full test (all modules)
bash scripts/dx.sh all

# Or use Maven directly
mvn clean verify -DskipIntegrationTests
```

---

## CI/CD (For DevOps)

```yaml
# Cache directory across builds
~/.m2/build-cache/

# Build cache config
.mvn/maven-build-cache-config.xml

# Test incremental works
bash scripts/test-incremental-build.sh
```

---

## Common Issues

| Problem | Fix |
|---------|-----|
| Still rebuilding unchanged files | Delete `~/.m2/build-cache/` |
| IDE shows changes but CLI doesn't | Use Maven instead of IDE compiler |
| Build cache not enabled | Check `.mvn/extensions.xml` has maven-build-cache-extension |
| Out of memory during compile | Increase `<maxmem>` in `pom.xml` (line 1429) |

---

## Important Files

| Path | Purpose |
|------|---------|
| `scripts/dx.sh` | Main developer build script |
| `scripts/dx-incremental.sh` | Enhanced incremental (git-based) |
| `scripts/test-incremental-build.sh` | Cache verification suite |
| `.mvn/extensions.xml` | Enable/disable build cache |
| `.mvn/maven-build-cache-config.xml` | Cache tuning |
| `.claude/guides/INCREMENTAL-BUILD-GUIDE.md` | Full guide |

---

## Performance Targets

- ✓ Incremental (no change): **<2s**
- ✓ Comment-only: **<2s**
- ✓ Single file: **<5s**
- ✓ Single module: **<10s**
- ✓ Clean build: **<50s**

**Not meeting targets?** → Run `test-incremental-build.sh` and file an issue

---

## Help & Documentation

- **Full Guide**: `.claude/guides/INCREMENTAL-BUILD-GUIDE.md`
- **Analysis**: `.claude/profiles/incremental-build-analysis.md`
- **Team Status**: `.claude/TEAM-STATUS-INCREMENTAL-BUILD.md`

**Questions?** Check guides or contact build team.

