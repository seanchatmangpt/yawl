# YAWL CLI Code Review - Quick Fix Guide

**Date**: 2026-02-22  
**Effort**: ~2-4 hours to fix all issues  
**Priority**: Medium (can deploy now, but improvements recommended)  

---

## Quick Summary

Review found:
- **0 blocking issues** (can deploy)
- **3 major issues** (2 intentional, 1 should fix)
- **5 minor issues** (best practices)

All issues are easily fixable.

---

## Fix Checklist

### PRIORITY 1: Do First (1-2 hours)

- [ ] **Remove unused `import sys`** (8 files)
  ```bash
  # Check which files have unused sys
  grep -n "^import sys$" yawl_cli/*.py
  
  # Remove from each file (replace line with nothing)
  ```
  Files: build.py, config_cli.py, ggen.py, godspeed.py, gregverse.py, observatory.py, team.py, utils.py

- [ ] **Document silent fallbacks in utils.py**
  ```python
  # Line 59-60 (Maven detection)
  except Exception:
      # Maven detection is optional - may not be installed yet
      # Failing silently here is acceptable behavior
      pass
  
  # Line 73-74 (Git branch detection)
  except Exception:
      # Git detection is optional - may fail in non-git directories
      # Failing silently here is acceptable behavior
      pass
  ```

- [ ] **Fix team.status() stub** (team.py lines 218-231)
  ```python
  def status(
      team_id: Optional[str] = typer.Argument(None, help="Team ID (optional)"),
  ) -> None:
      """Show team status and progress."""
      raise NotImplementedError(
          "Team status reporting is under development.\n"
          "To check team status, run: yawl team list"
      )
  ```

### PRIORITY 2: Do This Sprint

- [ ] **Fix generic exception at config_cli.py:194**
  ```python
  # Before
  except Exception:
      pass
  
  # After (gracefully handle project root not found)
  except (RuntimeError, FileNotFoundError):
      # Project root detection is optional for 'locations' command
      pass
  ```

- [ ] **Fix generic exception at team.py:147**
  ```python
  # Before
  except Exception as e:
      stderr_console.print(f"[yellow]Warning:[/yellow] Error reading team metadata: {e}")
  
  # After
  except (json.JSONDecodeError, OSError, IOError) as e:
      stderr_console.print(f"[yellow]Warning:[/yellow] Error reading team metadata: {e}")
  ```

- [ ] **Add validation to team.message() (team.py:239)**
  ```python
  def message(
      team_id: str = typer.Argument(..., help="Team ID"),
      agent: str = typer.Argument(..., help="Agent name"),
      text: str = typer.Argument(..., help="Message text"),
  ) -> None:
      """Send a message to a team agent."""
      # Validate inputs
      _validate_team_identifier(team_id, "Team ID")
      _validate_team_identifier(agent, "Agent name")
      
      # ADD THIS:
      if not text or len(text) > 10000:
          raise ValueError("Message must be 1-10000 characters")
      
      # ... rest of function
  ```

### PRIORITY 3: Long-term

- [ ] Create error formatting utility
  ```python
  # In utils.py, add:
  def print_error(message: str) -> None:
      """Print error message with consistent formatting."""
      stderr_console.print(f"[bold red]✗ Error:[/bold red] {message}")
  ```
  Then replace all `stderr_console.print(f"[bold red]✗ Error:[/bold red] {e}")` calls with `print_error(str(e))`

- [ ] Set up linting in CI/CD
  ```bash
  # Add to pre-commit hooks or CI/CD pipeline:
  flake8 yawl_cli/ --select F401,E501,W291
  pylint yawl_cli/
  ```

---

## File-by-File Changes

### utils.py
```python
Line 6:  Remove "import sys"
Line 59: Add comment: "# Maven detection is optional - may not be installed yet"
Line 73: Add comment: "# Git detection is optional - may fail in non-git directories"
```

### build.py
```python
Line 3: Remove "import sys"
```

### config_cli.py
```python
Line 3:  Remove "import sys"
Line 194: Change "except Exception:" to "except (RuntimeError, FileNotFoundError):"
```

### observatory.py
```python
Line 4: Remove "import sys"
```

### godspeed.py
```python
Line 3: Remove "import sys"
```

### ggen.py
```python
Line 3: Remove "import sys"
```

### gregverse.py
```python
Line 3: Remove "import sys"
```

### team.py
```python
Line 3:   Remove "import sys"
Line 147: Change "except Exception" to "except (json.JSONDecodeError, OSError, IOError)"
Line 218-231: Replace stub with "raise NotImplementedError(...)"
Line 239: Add message text validation check
```

---

## Testing After Changes

```bash
# Run existing tests to verify no breakage
pytest test/ -v

# Check for unused imports
python3 -m flake8 --select F401 yawl_cli/

# Check type safety
python3 -m mypy yawl_cli/ --ignore-missing-imports

# Manual smoke test
yawl build compile
yawl godspeed discover
yawl team list  # Should work
yawl team status  # Should now raise NotImplementedError with helpful message
```

---

## Validation Checklist

After fixes, verify:
- [ ] All tests pass: `pytest test/ -v`
- [ ] No unused imports: `flake8 --select F401 yawl_cli/`
- [ ] No new type errors: `mypy yawl_cli/ --ignore-missing-imports`
- [ ] Manual CLI tests work
- [ ] Error messages are consistent
- [ ] Silent fallbacks are documented

---

## Expected Outcome

After these fixes:
- **H Gate**: GREEN (no violations)
- **Q Gate**: GREEN (all violations resolved)
- **Code Quality**: GREEN (all best practices applied)
- **Security**: GREEN (no changes needed)

**Status**: PRODUCTION READY

---

## Questions?

Refer to the detailed report: `/home/user/yawl/cli/CODE_REVIEW_REPORT.md`

Quick reference: `/home/user/yawl/cli/REVIEW_FINDINGS.txt`

---

**Report Generated**: 2026-02-22  
**Standards**: HYPER_STANDARDS + Fortune 5 Production Quality
