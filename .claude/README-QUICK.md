# YAWL Quick Start - 80/20 Guide

**Get 80% done in 20% of the time.**

---

## ⚡ **30-Second Start**

```bash
# 1. Quick test (verify environment)
java -cp classes org.yawlfoundation.yawl.util.QuickTest

# 2. Run tests
./.claude/quick-start.sh test

# 3. Build
./.claude/quick-start.sh build
```

**Done.** You're now productive.

---

## 🎯 **The 20% You Need to Know**

### **1. Environment Detection (Most Important)**

```java
import org.yawlfoundation.yawl.util.EnvironmentDetector;

// This ONE line determines everything:
if (EnvironmentDetector.isClaudeCodeRemote()) {
    // Remote: H2 database, skip integration tests
} else {
    // Local: PostgreSQL, full testing
}
```

### **2. One Command for Everything**

```bash
# Instead of remembering Ant commands:
./.claude/quick-start.sh {test|build|run|clean|env}
```

### **3. Smart Build (Auto-Detects)**

```bash
# Figures out what to compile and runs tests:
./.claude/smart-build.sh
```

### **4. Environment Check**

```bash
# Know where you are:
java -cp classes org.yawlfoundation.yawl.util.EnvironmentDetector
```

### **5. Configuration (Auto-Managed)**

**Don't touch these files:**
- `build/build.properties` → Auto-symlinked
- `build/build.properties.remote` → Auto-generated
- `build/build.properties.local` → Auto-backed-up

**Just let the SessionStart hook handle it.**

---

## 📊 **Command Cheat Sheet**

| Task | Command | Time |
|------|---------|------|
| Verify setup | `java -cp classes org.yawlfoundation.yawl.util.QuickTest` | 1s |
| Run tests | `./.claude/quick-start.sh test` | 5s |
| Compile | `./.claude/quick-start.sh build` | 15s |
| Smart build | `./.claude/smart-build.sh` | 5-20s |
| Check env | `./.claude/quick-start.sh env` | 1s |

---

## 🚫 **What NOT to Do**

1. ❌ Don't manually install Ant (SessionStart hook does it)
2. ❌ Don't edit `build/build.properties` directly (use symlinks)
3. ❌ Don't run `ant clean` every time (wastes time)
4. ❌ Don't memorize Ant targets (use quick-start.sh)
5. ❌ Don't commit built JARs (already in .gitignore)

---

## 💡 **Key Insights**

### **Local vs Remote (Auto-Detected)**

| Feature | Local (Docker) | Remote (Claude Code Web) |
|---------|----------------|--------------------------|
| **Database** | PostgreSQL | H2 (in-memory) |
| **Setup** | Manual | Auto (SessionStart hook) |
| **Tests** | All 102 | Unit tests only |
| **Build Tool** | Pre-installed | Auto-installed |
| **Persistence** | ✅ Saved | ❌ Ephemeral |

**You don't configure this. `EnvironmentDetector` does.**

---

## 🎓 **Advanced (20% More Knowledge)**

### **Force Environment Switch**

```bash
# Force local config:
ln -sf build/build.properties.local build/build.properties

# Force remote config:
ln -sf build/build.properties.remote build/build.properties
```

### **Direct Ant Usage**

```bash
# If you really need Ant directly:
ant -f build/build.xml compile  # Compile only
ant -f build/build.xml unitTest # Tests only
ant -f build/build.xml clean    # Clean build
```

### **Manual Test Execution**

```bash
# Run specific test class:
java -cp "classes:build/3rdParty/lib/*" \
  junit.textui.TestRunner org.yawlfoundation.yawl.elements.TestYNet
```

---

## 📚 **Full Documentation**

- **Detailed capabilities:** `.claude/CAPABILITIES.md`
- **YAWL guide:** `CLAUDE.md`
- **Build details:** `build/build.xml`

---

## 🐛 **Troubleshooting**

### **Build fails with "Ant not found"**

```bash
# In remote sessions, wait for SessionStart hook to finish (~30 seconds)
# Or manually install:
apt-get update && apt-get install -y ant
```

### **Tests fail with database errors**

```bash
# Check database configuration:
grep database.type build/build.properties

# Should be:
#   h2 (remote) or postgres (local)
```

### **"Classes not found" error**

```bash
# Recompile:
ant -f build/build.xml clean compile
```

---

## ✅ **Success Criteria**

You're ready when:

```bash
$ java -cp classes org.yawlfoundation.yawl.util.QuickTest
🧪 YAWL Quick Test
✅ Test 1: Environment detected as REMOTE
✅ Test 2: Database type = h2
✅ Test 3: Java version = 21.0.10
✅ Test 4: Classpath configured correctly
✅ Test 5: Session ID = session_01PuZ...
📊 Results: 5 passed, 0 failed
🎉 All tests passed! Environment is ready.
```

---

**That's it. 80% of what you need in 20% of the reading time.**

For the remaining 20%, read `.claude/CAPABILITIES.md`.
