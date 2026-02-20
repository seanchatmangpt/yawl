# Quick Reference: HYPER_STANDARDS Validation

## 5-Point Validation Checklist (Pre-Commit Hook)

### 1. NO DEFERRED WORK (40% of violations)
```
FORBIDDEN:  // TODO: ...
            // FIXME: ...
            // XXX, HACK, LATER, FUTURE
            // @stub, @mock, @incomplete
            
REQUIRED:   throw new UnsupportedOperationException(
                "Requires: [dependency list]\n" +
                "Steps: [implementation steps]\n" +
                "See: [reference class]"
            );
```

**Test Command:**
```bash
git diff --cached | grep -E "//\s*(TODO|FIXME|XXX|HACK)"
```

---

### 2. NO MOCKS (25% of violations)
```
FORBIDDEN:  mockData()
            stubService
            fakeUser
            demoResponse()
            
            private boolean useMockMode = true;
            if (isTestMode) return fake();
            
REQUIRED:   Real implementation with injected dependencies
            OR throw UnsupportedOperationException
```

**Test Command:**
```bash
git diff --cached | grep -E "(mock|stub|fake|demo)[A-Z]"
```

---

### 3. PRODUCTION PURITY (15% of violations)
```
FORBIDDEN:  import org.mockito.*;
            import org.junit.Test;
            @Test, @Mock, @RunWith in src/main/
            
REQUIRED:   All test code in src/test/
            Production code uses real dependency injection
```

**Test Command:**
```bash
git diff --cached src/main | grep "import.*mockito\|@Test"
```

---

### 4. NO STUBS (15% of violations)
```
FORBIDDEN:  return "";
            return null;  // without semantic meaning
            public void save() { }
            
REQUIRED:   - return data or throw exception
            - if empty, add comment: "// Empty = no results found"
            - no-op methods throw exception
```

**Test Command:**
```bash
git diff --cached | grep -E 'return\s+""\s*;|public\s+void\s+\w+\s*\{\s*\}'
```

---

### 5. NO SILENT FALLBACKS (5% of violations)
```
FORBIDDEN:  catch (Exception e) {
                return fake();
            }
            
            String key = getKey() ?? "default";
            
REQUIRED:   - Propagate exceptions upward
            - Throw explicit exception if required config missing
            - Use Optional<T> instead of null defaults
```

**Test Command:**
```bash
git diff --cached | grep -B1 "catch.*{" | grep "return"
```

---

## Decision Tree

```
Is this code a TODO/FIXME/XXX/HACK comment?
├─ YES → REJECT (use UnsupportedOperationException instead)
└─ NO → Continue

Does code have mock/stub/fake in name?
├─ YES → REJECT (implement real or throw)
└─ NO → Continue

Is test code in src/main/ (not src/test/)?
├─ YES → REJECT (move to src/test/)
└─ NO → Continue

Is this method body empty?
├─ YES → REJECT (throw exception or add real logic)
└─ NO → Continue

Does catch block return fake data?
├─ YES → REJECT (propagate exception instead)
└─ NO → PASS ✅
```

---

## Common Fixes

### Fix 1: Replace TODO with Exception
```java
// BEFORE
public void process() {
    // TODO: implement processing logic
}

// AFTER
public void process() {
    throw new UnsupportedOperationException(
        "process() requires OrderRepository to be injected.\n" +
        "Steps:\n" +
        "  1. Add 'private final OrderRepository repo;' field\n" +
        "  2. Inject via constructor\n" +
        "  3. Implement: 'return repo.findOrders();'\n" +
        "Example: OrderService.java:42"
    );
}
```

### Fix 2: Remove Mock Method
```java
// BEFORE
public String getMockData() {
    return "fake data";
}

// AFTER
// Option A: Implement real version
public String getData() {
    return repository.fetch();
}

// Option B: Mark as unimplemented
public String getData() {
    throw new UnsupportedOperationException(
        "getData() requires DatabaseConnection to be configured in application.properties"
    );
}
```

### Fix 3: Move Test Code to test/
```bash
# BEFORE
src/main/java/com/yawl/MockService.java
src/main/java/com/yawl/FakeRepository.java

# AFTER (commands)
git mv src/main/java/com/yawl/MockService.java src/test/java/com/yawl/MockService.java
git mv src/main/java/com/yawl/FakeRepository.java src/test/java/com/yawl/FakeRepository.java

# Then update imports in src/main if needed
```

### Fix 4: Replace Empty Return
```java
// BEFORE
public List<Item> getItems() {
    return new ArrayList<>();
}

// AFTER: If legitimately empty is semantic
public List<Item> getItems() {
    // Empty list means "no items found" - this is valid
    return repository.findAll();  // May be empty, may have results
}

// OR: If this is a stub, throw
public List<Item> getItems() {
    throw new UnsupportedOperationException(
        "getItems() requires ItemRepository to be configured"
    );
}
```

### Fix 5: Remove Silent Fallback
```java
// BEFORE
public String fetchData() {
    try {
        return api.call();
    } catch (Exception e) {
        log.error("API failed, using default");
        return "default value";
    }
}

// AFTER
public String fetchData() {
    try {
        return api.call();
    } catch (ApiException e) {
        throw new RuntimeException(
            "API call failed. Check network and credentials.",
            e
        );
    }
}
```

---

## Bypass Only If

- You're using `git commit --no-verify` (ONLY for emergency hotfixes)
- You understand you're committing code that violates Fortune 5 standards
- You WILL fix it in the next commit immediately after

**Better approach:** Use feature branches for WIP code

```bash
# Create WIP branch
git checkout -b wip/my-feature

# Commit without validation
SKIP_VALIDATION=1 git commit -m "WIP: [description]"

# Later: clean up and merge to main
git checkout main
git cherry-pick wip/my-feature  # Only after validation passes
```

---

## Hook Performance

**Expected timing:**
- 1-5 files: ~200ms
- 10-20 files: ~300ms
- 50+ files: ~600-800ms
- Max: ~1-2s for 100+ files

**If hook is slow:**
1. Check disk I/O (git status might be slow)
2. Reduce staged files to smaller commits
3. Run `git gc` to optimize repo

---

## Documentation References

| What | Where |
|------|-------|
| All HYPER_STANDARDS rules | `.claude/HYPER_STANDARDS.md` |
| Validation guide (detailed) | `.claude/PRE_COMMIT_VALIDATION_GUIDE.md` |
| Project standards | `CLAUDE.md` |
| Build & test commands | `scripts/dx.sh` |
| Hook source code | `.git/hooks/pre-commit` |

---

**Remember:** 
- The hook catches common violations fast
- You still need code review for semantic errors
- The best code is honest: does what it claims, no false returns

