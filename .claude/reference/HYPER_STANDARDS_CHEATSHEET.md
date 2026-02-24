# 🚨 HYPER_STANDARDS - 1-PAGE CHEATSHEET

## Five Commandments (Zero Tolerance)

| Pattern | Regex Detection | Quick Fix | Severity |
|---------|----------------|-----------|----------|
| **TODO-Like Comments** | `//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)` | Remove or implement | ❌ REJECT |
| **Mock Methods** | `(mock|stub|fake|test|demo|sample)[A-Z][a-zA-Z]*\s*[=(]` | Rename to real impl | ❌ REJECT |
| **Mock Classes** | `(Mock|Stub|Fake|Test|Demo|Sample)[A-Za-z]*\s+(class|interface)` | Delete or implement | ❌ REJECT |
| **Mock Mode Flags** | `(is|use|enable)(Mock|Test|Fake)(Mode|Data|ing)` | Remove flags | ❌ REJECT |
| **Empty Returns** | `return\s+"";|return\s+0;|return\s+null;.*//.*stub` | Return real data or throw | ❌ REJECT |
| **No-op Methods** | `public\s+void\s+\w+\([^)]*\)\s*\{\s*\}` | Implement logic or throw | ❌ REJECT |
| **Silent Fallbacks** | `catch\s*\([^)]+\)\s*\{[^}]*return\s+(new|\")` | Propagate exception | ❌ REJECT |
| **Dishonest Behavior** | Semantic analysis required | Match behavior to name | ❌ REJECT |

## Forbidden Examples vs Required

### ❌ FORBIDDEN:
```java
// TODO: implement later
public String mockFetch() { return "fake"; }
public class MockService { }
public String getData() { return ""; }
try { return api.fetch(); } catch (e) { return "default"; }
/** Starts workflow */ public void start() { log.info("started"); }
```

### ✅ REQUIRED:
```java
// Either implement now or throw
throw new UnsupportedOperationException(
    "Requires: schema.xsd + validator impl");
public class ApiClient { /* real logic */ }
public String getData() { /* real data */ }
throw new RuntimeException("API failed");
/** Starts workflow */ public void start() { /* real work */ }
```

## Edge Cases

### Valid Empty Returns:
```java
// "no results" is valid business state
List<User> findActive() { return repo.findByStatus(ACTIVE); }

// "not found" with Optional
Optional<Config> findConfig(String key) {
    return Optional.ofNullable(repo.findByKey(key));
}
```

### When to Throw:
```java
// Missing dependency = crash
String getKey() {
    String key = System.getenv("API_KEY");
    if (key == null) throw new IllegalStateException("API_KEY required");
    return key;
}
```

## Protocol

### Pre-Flight Scan:
- Scan planned code for 8 forbidden patterns
- If any match: STOP and choose real implementation or throw

### Response Templates:
```
"Cannot create mock code. Violates:
- ❌ NO MOCKS: fake behavior
- ❌ NO STUBS: empty returns
- ❌ NO LIES: claims to work

Options:
1. Implement real version
2. Throw with implementation guide
3. Create interface contract"
```

## Validation Hook

```bash
#!/bin/bash
FILE=$(cat | jq -r '.tool_input.file_path')
VIOLATIONS=0

if grep -E '//\s*(TODO|FIXME|XXX|HACK|@stub|placeholder)' "$FILE"; then ((VIOLATIONS++)); fi
if grep -E '(mock|stub|fake|test|demo)[A-Z][a-zA-Z]*\s*[=(]' "$FILE"; then ((VIOLATIONS++)); fi
if grep -E 'return\s+"";|return\s+null;.*//.*stub' "$FILE"; then ((VIOLATIONS++)); fi

if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ $VIOLATIONS HYPER_STANDARDS VIOLATIONS in $FILE" >&2
    exit 2
fi
```

**100% compliance required - NO EXCEPTIONS**

---
