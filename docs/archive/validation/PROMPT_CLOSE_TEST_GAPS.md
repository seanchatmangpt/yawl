# Prompt for Next Agent: Run Tests Correctly, Then Fix

**Status:** In progress. When **Tests run: 106, Failures: 0, Errors: 0** is achieved, archive this file to `docs/archive/` or add a "Work complete" note.

## Mandatory: Run Tests Inside the Container

This project is **Docker/devcontainer-only** (see [.cursorrules](.cursorrules)). Do **not** run `ant` or `java` on the host. All build and test must happen **inside** the devcontainer or `yawl-dev` container.

**How to run unit tests:**
- **Option A (from host):** `docker compose run --rm yawl-dev ./scripts/run-unit-tests.sh`
- **Option B (already in container):** `./scripts/run-unit-tests.sh`

`run-unit-tests.sh` runs `ensure-build-properties.sh` (or, if that script is not executable, `rm -f build/build.properties && cp build/build.properties.remote build/build.properties`) then `ant -f build/build.xml clean unitTest`. Use `ant clean unitTest` only after ensuring `build/build.properties` exists (e.g. copy from `build/build.properties.remote`).

---

## Your Task

1. **Run the tests correctly** (inside the container, per above).
2. **Fix all gaps** so that `ant unitTest` reports **0 failures, 0 errors**.

Current state:
- **Engine init fixed:** 45 errors resolved via hibernate.cfg.xml in classpath, DriverManagerConnectionProvider, AUTO_SERVER removal for H2 mem.
- **Remaining:** 7 failures, 0 errors. All errors fixed. See docs/plans/close_all_test_gaps.md.

Full implementation plan: [docs/plans/close_all_test_gaps.md](plans/close_all_test_gaps.md).

---

## Short Checklist

1. Ensure `build/build.properties` is valid and uses H2 (relative symlink to `build.properties.remote` or copy from it).
2. Add `scripts/ensure-build-properties.sh` and `scripts/run-unit-tests.sh` if missing.
3. (Optional) Propagate cause in `YEngine.getInstance()` — `throw new RuntimeException("...", e)`.
4. Fix testToXML expected string (flowsInto order).
5. Fix testInvalidMIAttributeVerify (contains or expected message).
6. Fix testInvalidInputCondition (message count or verification).
7. Fix stubList validation (fixtures or validator) for testGoodNetVerify, testBadSpecVerify, testSpecWithLoops.
8. Re-run unit tests **inside the container** and confirm: `Tests run: 106, Failures: 0, Errors: 0`. When that is achieved, archive this prompt (e.g. move to `docs/archive/`) or add a note that the work is complete.
