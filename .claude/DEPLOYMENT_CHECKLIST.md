# YAWL CLI v6.0.0 — Deployment Checklist

**Release Date**: February 22, 2026  
**Target Deployment**: GA Release (after Phase 1 fixes)  
**Release Manager**: [To be assigned]

---

## Pre-Deployment (Phase 1) — 30 Minutes

### Code Fixes Required

- [ ] **Fix entry point in pyproject.toml**
  - Change: `yawl = "godspeed_cli:app"`
  - To: `yawl = "yawl_cli.godspeed_cli:app"`
  - File: `/home/user/yawl/cli/pyproject.toml` line 33
  - Verify: `pip install -e . && yawl --version`

- [ ] **Export DEBUG from utils.py**
  - Add after imports in `yawl_cli/utils.py`:
  ```python
  __all__ = [
      "Config",
      "DEBUG",
      "ensure_project_root",
      "load_facts",
      "run_shell_cmd",
      "prompt_yes_no",
      "prompt_choice",
  ]
  ```
  - Verify: `python -c "from yawl_cli.utils import DEBUG; print(DEBUG)"`

- [ ] **Fix test fixture bug**
  - File: `test/unit/test_config.py` line 77
  - Change: `(temp_project_dir / ".yawl" / "config.yaml").unlink()`
  - To: `(temp_project_dir / ".yawl" / "config.yaml").unlink(missing_ok=True)`
  - Verify: `pytest test/unit/test_config.py::TestConfigLoading::test_load_invalid_yaml_raises_error`

### Test Validation

- [ ] **Run full test suite**
  ```bash
  cd /home/user/yawl/cli
  pytest --tb=short -v
  # Expected: 87 passed
  ```

- [ ] **Verify no import errors**
  ```bash
  python -c "from yawl_cli.build import build_app; print('OK')"
  python -c "from yawl_cli.godspeed import godspeed_app; print('OK')"
  python -c "from yawl_cli.observatory import observatory_app; print('OK')"
  ```

- [ ] **Test entry point**
  ```bash
  python -m pip install -e .
  yawl --version
  yawl --help
  yawl build --help
  ```

### Git Commit

- [ ] **Stage fixes**
  ```bash
  cd /home/user/yawl/cli
  git add pyproject.toml yawl_cli/utils.py test/unit/test_config.py
  ```

- [ ] **Commit with message**
  ```bash
  git commit -m "fix: Production readiness issues

  - Fix pyproject.toml entry point to yawl_cli.godspeed_cli:app
  - Export DEBUG from utils.py via __all__
  - Fix test fixture: add missing_ok=True to unlink()

  All tests now passing: 87/87 ✓"
  ```

- [ ] **Tag release**
  ```bash
  git tag -a v6.0.0-rc1 -m "Release candidate 1: Production readiness fixes"
  git push origin v6.0.0-rc1
  ```

---

## Phase 2 Testing (Week of Feb 24) — 3-4 Hours

### Environment 1: Local Development

- [ ] **Install in clean environment**
  ```bash
  cd /tmp
  mkdir yawl-test-local
  cd yawl-test-local
  git clone /home/user/yawl .
  cd cli
  python -m venv venv
  source venv/bin/activate
  pip install -e .
  ```

- [ ] **Verify all commands**
  - [ ] `yawl --version` returns 6.0.0
  - [ ] `yawl --help` shows all 7 subcommands
  - [ ] `yawl build --help` shows options
  - [ ] `yawl build compile` (test with --dry-run)
  - [ ] `yawl config --help` works
  - [ ] `yawl observatory --help` works
  - [ ] `yawl godspeed --help` works
  - [ ] `yawl ggen --help` works
  - [ ] `yawl gregverse --help` works
  - [ ] `yawl team --help` works

- [ ] **Test configuration**
  - [ ] Create `.yawl/config.yaml` manually
  - [ ] `yawl config show` displays correctly
  - [ ] `yawl config get build.parallel` works
  - [ ] `yawl config set build.threads 8` works

- [ ] **Verify debug flag**
  - [ ] `YAWL_CLI_DEBUG=1 yawl build compile` shows debug output

### Environment 2: Docker Container

- [ ] **Create Dockerfile**
  ```dockerfile
  FROM ubuntu:latest
  RUN apt-get update && apt-get install -y python3 python3-pip git maven
  WORKDIR /opt/yawl
  COPY cli/ /opt/yawl/cli/
  WORKDIR /opt/yawl/cli
  RUN pip install -e .
  ENTRYPOINT ["yawl"]
  ```

- [ ] **Build and test**
  ```bash
  docker build -t yawl-cli:6.0.0 .
  docker run yawl-cli:6.0.0 --version
  docker run yawl-cli:6.0.0 --help
  ```

- [ ] **Verify Maven access**
  - [ ] `docker run yawl-cli:6.0.0 build compile --dry-run`
  - [ ] Check Maven version detection

### Environment 3: CI/CD (GitHub Actions)

- [ ] **Create .github/workflows/cli-tests.yml**
  ```yaml
  name: YAWL CLI Tests
  on: [push, pull_request]
  jobs:
    test:
      runs-on: ubuntu-latest
      strategy:
        matrix:
          python-version: ["3.10", "3.11", "3.12"]
      steps:
        - uses: actions/checkout@v3
        - uses: actions/setup-python@v4
          with:
            python-version: ${{ matrix.python-version }}
        - run: pip install -e ./cli
        - run: cd cli && pytest
        - run: yawl --version
        - run: yawl --help
  ```

- [ ] **Verify all matrix combinations pass**
  - [ ] Python 3.10 ✓
  - [ ] Python 3.11 ✓
  - [ ] Python 3.12 ✓

### Functional Testing

- [ ] **Build workflow**
  ```bash
  yawl build compile --dry-run
  yawl build test --dry-run
  yawl build validate --dry-run
  yawl build all --dry-run
  ```

- [ ] **Observatory workflow**
  ```bash
  # If facts directory exists
  yawl observatory list
  yawl observatory show modules
  yawl observatory search "YNetRunner"
  ```

- [ ] **GODSPEED workflow**
  ```bash
  yawl godspeed discover --dry-run
  yawl godspeed compile --dry-run
  yawl godspeed full --dry-run
  ```

- [ ] **Configuration workflow**
  ```bash
  yawl config show
  yawl config get build.parallel
  yawl config set build.threads 4
  yawl config show
  ```

### Performance Baselines

- [ ] **Measure startup time**
  ```bash
  time yawl --version
  # Target: <500ms
  ```

- [ ] **Measure config load**
  ```bash
  time yawl config show
  # Target: <100ms per command
  ```

- [ ] **Measure help generation**
  ```bash
  time yawl --help > /dev/null
  # Target: <200ms
  ```

---

## Phase 3 Documentation (Week of Mar 3) — 2-3 Hours

### Documentation Files

- [ ] **docs/CLI_INSTALLATION.md**
  - [ ] Linux installation instructions
  - [ ] macOS installation instructions
  - [ ] Windows installation instructions
  - [ ] Virtual environment setup
  - [ ] Verify each with test install

- [ ] **docs/CLI_CONFIGURATION.md**
  - [ ] Configuration hierarchy diagram
  - [ ] File locations for each platform
  - [ ] All available settings
  - [ ] Examples: minimal, development, production
  - [ ] Troubleshooting section

- [ ] **docs/CLI_TROUBLESHOOTING.md**
  - [ ] 10+ common error scenarios
  - [ ] Recovery steps for each
  - [ ] Debug tips and tricks
  - [ ] FAQ section

- [ ] **Man page: yawl.1**
  - [ ] Standard man page format
  - [ ] All commands documented
  - [ ] All options documented
  - [ ] See also section

### Documentation Review

- [ ] **Documentation quality check**
  - [ ] All links work
  - [ ] All examples tested
  - [ ] Consistent formatting
  - [ ] Grammar and spelling

- [ ] **Completeness check**
  - [ ] All 7 subcommand groups covered
  - [ ] All options documented
  - [ ] All config settings documented
  - [ ] Error messages explained

---

## Phase 4 Release (Week of Mar 10)

### Pre-Release Checklist

- [ ] **Update CHANGELOG.md**
  ```markdown
  ## v6.0.0 — 2026-03-10
  
  ### New Features
  - Complete CLI rewrite with Typer
  - Multi-level configuration hierarchy
  - Comprehensive error handling
  
  ### Fixes
  - Fix entry point reference
  - Export DEBUG in __all__
  - Fix test fixtures
  
  ### Documentation
  - Installation guide
  - Configuration reference
  - Troubleshooting guide
  ```

- [ ] **Update version**
  - [ ] Change `version = "6.0.0-rc1"` to `version = "6.0.0"`
  - [ ] Update README.md version references
  - [ ] Update any other version strings

- [ ] **Final testing**
  - [ ] Run all tests: `pytest`
  - [ ] Run mypy: `mypy yawl_cli/`
  - [ ] Run ruff: `ruff check yawl_cli/`
  - [ ] Manual smoke test of all commands

### Release Steps

- [ ] **Create final commit**
  ```bash
  git commit -m "release: v6.0.0

  Full production release with:
  - Complete CLI functionality
  - Comprehensive documentation
  - All tests passing (87/87)
  - Production readiness verified"
  ```

- [ ] **Create git tag**
  ```bash
  git tag -a v6.0.0 -m "YAWL CLI v6.0.0 - Production Release"
  ```

- [ ] **Push to repository**
  ```bash
  git push origin main
  git push origin v6.0.0
  ```

- [ ] **Publish to PyPI** (if applicable)
  ```bash
  cd /home/user/yawl/cli
  python -m build
  python -m twine upload dist/*
  ```

---

## Sign-Off

### Release Manager

- [ ] Name: ___________________________
- [ ] Date: ___________________________
- [ ] Signature: _______________________

### QA Lead

- [ ] Testing complete: ___________________________
- [ ] All issues resolved: ___________________________
- [ ] Approved for release: ___________________________

### Deployment Lead

- [ ] Deployment plan finalized: ___________________________
- [ ] Rollback procedure documented: ___________________________
- [ ] Go/no-go decision: ___________________________

---

## Rollback Procedure

If critical issues found post-release:

1. **Identify issue**
   ```bash
   # Check deployed version
   yawl --version
   
   # Gather error logs
   YAWL_CLI_DEBUG=1 yawl <failing-command>
   ```

2. **Rollback to previous version**
   ```bash
   # If using pip
   pip install yawl-cli==6.0.0-rc1
   
   # If using git
   git checkout v6.0.0-rc1
   ```

3. **Verify rollback**
   ```bash
   yawl --version  # Should show 6.0.0-rc1
   ```

4. **Report issue**
   - Create GitHub issue with reproduction steps
   - Tag as `critical` if blocking workflow
   - Assign to release manager

---

**Document Version**: 1.0  
**Last Updated**: February 22, 2026  
**Status**: READY FOR PHASE 1 EXECUTION

