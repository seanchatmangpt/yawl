# How to Add a New Maven Module

**Quadrant**: How-To | **Goal**: Register a new module in the correct reactor position

Adding a module in the wrong position violates FM7 (Reactor Order Violation, RPN=105). Follow this checklist to place it correctly.

---

## Prerequisites

- Understand the existing 7-layer dependency graph (`docs/build-sequences.md §4`)
- Know which existing modules your new module depends on

---

## 1 — Determine your layer

Find the highest-numbered layer of any module you depend on, then add 1.

| Your dependencies include... | Your layer |
|------------------------------|-----------|
| Nothing (standalone) | 0 |
| Only yawl-utilities / yawl-security / yawl-graalpy / yawl-graaljs | 1 |
| yawl-elements or yawl-ggen or yawl-graalwasm | 2 or higher |
| yawl-engine | 3 or higher |
| yawl-stateless | 4 or higher |
| yawl-integration | 5 or higher |
| yawl-pi or yawl-resourcing | 6 or higher |

## 2 — Create the module directory and pom.xml

```bash
mkdir -p yawl-mymodule/src/main/java
mkdir -p yawl-mymodule/src/test/java
```

Minimal `yawl-mymodule/pom.xml`:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-GA</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>yawl-mymodule</artifactId>
  <packaging>jar</packaging>
  <name>YAWL My Module</name>

  <dependencies>
    <!-- Only declare YAWL deps you directly need; never declare versions -->
    <dependency>
      <groupId>org.yawlfoundation</groupId>
      <artifactId>yawl-engine</artifactId>
    </dependency>
    <!-- Test scope -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

## 3 — Add to root pom.xml `<modules>` in layer order

Open `/home/user/yawl/pom.xml` and locate the `<modules>` block. Insert your module **after** all modules it depends on and **before** any module that will depend on it.

If your module depends on `yawl-engine` (layer 2), insert it in the layer 4 group alongside `yawl-authentication`, `yawl-scheduling`, etc.:

```xml
<!-- Layer 4 — Services (parallel) -->
<module>yawl-authentication</module>
<module>yawl-scheduling</module>
<module>yawl-mymodule</module>   <!-- add here -->
<module>yawl-monitoring</module>
```

## 4 — Add to dx.sh `ALL_MODULES` in the same layer

Open `scripts/dx.sh` and find the `ALL_MODULES` array. Insert your module in the correct layer group:

```bash
# Layer 4 — Services (parallel)
yawl-authentication yawl-scheduling yawl-mymodule \
yawl-monitoring yawl-worklet yawl-control-panel yawl-integration yawl-webapps
```

## 5 — Update reactor.json

Add an entry to `docs/v6/diagrams/facts/reactor.json`:

```json
{
  "position": 11,
  "module": "yawl-mymodule",
  "layer": 4,
  "depends_on": ["yawl-engine"],
  "description": "My module — what it does"
}
```

Renumber all subsequent positions.

## 6 — Verify

```bash
bash scripts/dx.sh compile all
```

Must exit 0. If Maven reports a dependency resolution error, your layer assignment is wrong — check `mvn dependency:tree -pl yawl-mymodule`.

---

## Checklist

- [ ] Layer determined from dependency graph
- [ ] `pom.xml` created with no version declarations on YAWL deps
- [ ] Entry added to root `pom.xml` `<modules>` in correct position
- [ ] Entry added to `scripts/dx.sh` `ALL_MODULES` in correct position
- [ ] `docs/v6/diagrams/facts/reactor.json` updated
- [ ] `bash scripts/dx.sh compile all` exits 0

## See Also

- [Build Sequences Reference](../../reference/build-sequences.md) — full 19-module topology
- [Reactor Ordering Explanation](../../explanation/reactor-ordering.md) — why order matters
