# How to Target a Specific Module with dx.sh

**Quadrant**: How-To | **Goal**: Build or test only one module (and its dependencies) instead of everything

---

## Use `dx.sh -pl`

```bash
bash scripts/dx.sh -pl yawl-engine
```

This compiles `yawl-engine` and its transitive YAWL dependencies automatically (Maven `-am` flag).

```bash
# Compile only
bash scripts/dx.sh compile -pl yawl-engine

# Test only (assumes already compiled)
bash scripts/dx.sh test -pl yawl-engine

# Compile + test
bash scripts/dx.sh -pl yawl-engine
```

## Minimal Module Lists by Target

If you need fine-grained control with `mvn -pl` directly (no `-am`), use the minimal list from the build sequences reference:

| Target | Minimal `-pl` list |
|--------|-------------------|
| yawl-utilities | `yawl-utilities` |
| yawl-elements | `yawl-utilities,yawl-elements` |
| yawl-engine | `yawl-utilities,yawl-elements,yawl-engine` |
| yawl-stateless | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless` |
| yawl-ggen | `yawl-graalpy,yawl-ggen` |
| yawl-graalwasm | `yawl-graaljs,yawl-graalwasm` |
| yawl-integration | `yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration` |
| yawl-pi | add `yawl-integration,yawl-pi` to the integration list |
| yawl-resourcing | add `yawl-integration,yawl-resourcing` to the integration list |
| yawl-mcp-a2a-app | all 19 modules |

## Let Maven Resolve Dependencies for You

The simplest approach — let Maven figure out the upstream chain:

```bash
mvn -pl yawl-integration -am -T 1.5C clean compile
```

`-am` means "also make" — Maven walks the dependency graph and builds everything `yawl-integration` needs.

## Build Multiple Modules

Comma-separate them:

```bash
bash scripts/dx.sh -pl yawl-engine,yawl-stateless
```

## Exclude Heavy Modules (Remote/CI)

`yawl-pi` and `yawl-mcp-a2a-app` require `onnxruntime` (89 MB). Exclude them in environments without the artifact:

```bash
CLAUDE_CODE_REMOTE=true bash scripts/dx.sh all
```

Or manually:

```bash
mvn -pl '!yawl-pi,!yawl-mcp-a2a-app' -T 1.5C clean compile
```

## Verify Only One Module's Tests

```bash
mvn -pl yawl-engine -am -T 1.5C test -P quick-test
```

`quick-test` profile runs only `@Tag("unit")` tests (~10s per module).

---

## See Also

- [Build Sequences Reference](../../reference/build-sequences.md) — full minimal path table
- [dx.sh CLI Reference](../../reference/dx-sh.md) — all flags and environment variables
