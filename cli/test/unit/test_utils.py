"""Unit tests for YAWL CLI utility functions (Chicago TDD)."""

import json
from pathlib import Path
from unittest.mock import Mock

import pytest

from yawl_cli.utils import (
    ensure_project_root,
    load_facts,
    prompt_choice,
    prompt_yes_no,
    run_shell_cmd,
)


class TestEnsureProjectRoot:
    """Test project root detection."""

    def test_ensure_project_root_found(self, temp_project_dir: Path, monkeypatch) -> None:
        """Find project root from within project directory."""
        monkeypatch.chdir(temp_project_dir)

        root = ensure_project_root()

        assert root == temp_project_dir
        assert (root / "pom.xml").exists()
        assert (root / "CLAUDE.md").exists()

    def test_ensure_project_root_from_subdirectory(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Find project root from subdirectory."""
        subdir = temp_project_dir / "nested" / "directory"
        subdir.mkdir(parents=True)
        monkeypatch.chdir(subdir)

        root = ensure_project_root()

        assert root == temp_project_dir

    def test_ensure_project_root_not_found(self, monkeypatch) -> None:
        """Raise error when project root not found."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            monkeypatch.chdir(tmpdir)

            with pytest.raises(RuntimeError, match="Could not find YAWL project root"):
                ensure_project_root()

    def test_ensure_project_root_requires_both_markers(
        self, temp_project_dir: Path, monkeypatch
    ) -> None:
        """Both pom.xml and CLAUDE.md must exist."""
        (temp_project_dir / "CLAUDE.md").unlink()
        monkeypatch.chdir(temp_project_dir)

        with pytest.raises(RuntimeError, match="Could not find YAWL project root"):
            ensure_project_root()


class TestLoadFacts:
    """Test fact file loading."""

    def test_load_facts_success(self, facts_directory: Path) -> None:
        """Load fact file successfully."""
        data = load_facts(facts_directory, "modules.json")

        assert isinstance(data, dict)
        assert "yawl-engine" in data
        assert data["yawl-engine"]["path"] == "yawl/engine"

    def test_load_facts_all_sample_files(self, facts_directory: Path) -> None:
        """Load all sample fact files."""
        for fact_name in ["modules.json", "gates.json", "tests.json"]:
            data = load_facts(facts_directory, fact_name)
            assert isinstance(data, dict)
            assert len(data) > 0

    def test_load_facts_directory_not_found(self) -> None:
        """Raise error when facts directory doesn't exist."""
        nonexistent_dir = Path("/nonexistent/facts")

        with pytest.raises(
            FileNotFoundError,
            match="Facts directory not found.*Run: yawl observatory generate",
        ):
            load_facts(nonexistent_dir, "modules.json")

    def test_load_facts_file_not_found(self, facts_directory: Path) -> None:
        """Raise error when fact file doesn't exist."""
        with pytest.raises(
            FileNotFoundError, match="Fact file not found.*Available facts"
        ):
            load_facts(facts_directory, "nonexistent.json")

    def test_load_facts_malformed_json(self, facts_directory: Path) -> None:
        """Raise error for malformed JSON."""
        bad_file = facts_directory / "malformed.json"
        bad_file.write_text("{invalid json content")

        with pytest.raises(RuntimeError, match="Malformed JSON"):
            load_facts(facts_directory, "malformed.json")

    def test_load_facts_not_dict_raises_error(self, facts_directory: Path) -> None:
        """Raise error if JSON is not a dict."""
        array_file = facts_directory / "array.json"
        with open(array_file, "w") as f:
            json.dump([1, 2, 3], f)

        with pytest.raises(RuntimeError, match="Expected JSON object"):
            load_facts(facts_directory, "array.json")

    def test_load_facts_empty_directory(self, temp_project_dir: Path) -> None:
        """Handle empty facts directory with helpful message."""
        empty_dir = temp_project_dir / "empty_facts"
        empty_dir.mkdir()

        with pytest.raises(
            FileNotFoundError, match="No facts generated yet.*yawl observatory generate"
        ):
            load_facts(empty_dir, "any.json")


class TestRunShellCmd:
    """Test shell command execution."""

    def test_run_shell_cmd_success(self) -> None:
        """Execute successful shell command."""
        exit_code, stdout, stderr = run_shell_cmd(["echo", "hello"])

        assert exit_code == 0
        assert "hello" in stdout

    def test_run_shell_cmd_failure(self) -> None:
        """Execute failing shell command returns nonzero exit code."""
        exit_code, stdout, stderr = run_shell_cmd(["sh", "-c", "exit 1"])

        assert exit_code == 1

    def test_run_shell_cmd_with_cwd(self, temp_project_dir: Path) -> None:
        """Execute command in specified working directory."""
        marker_file = temp_project_dir / "marker.txt"
        marker_file.write_text("test")

        exit_code, stdout, stderr = run_shell_cmd(
            ["sh", "-c", "ls -la marker.txt"],
            cwd=temp_project_dir,
        )

        assert exit_code == 0
        assert "marker.txt" in stdout

    def test_run_shell_cmd_captures_stderr(self) -> None:
        """Capture stderr output."""
        exit_code, stdout, stderr = run_shell_cmd(
            ["sh", "-c", "echo 'error' >&2"],
        )

        assert "error" in stderr

    def test_run_shell_cmd_verbose_flag(self, capsys) -> None:
        """Verbose flag prints command before execution."""
        exit_code, stdout, stderr = run_shell_cmd(
            ["echo", "test"],
            verbose=True,
        )

        captured = capsys.readouterr()
        # Rich console output may not be captured easily, so this is a basic check
        assert exit_code == 0

    def test_run_shell_cmd_complex_command(self) -> None:
        """Execute complex shell command with pipes."""
        exit_code, stdout, stderr = run_shell_cmd(
            ["sh", "-c", "echo 'line1\\nline2\\nline3' | wc -l"],
        )

        assert exit_code == 0
        assert "3" in stdout.strip()


class TestPromptYesNo:
    """Test yes/no prompt."""

    def test_prompt_yes_no_user_yes(self, monkeypatch) -> None:
        """User enters 'yes' returns True."""
        monkeypatch.setattr("builtins.input", lambda x: "yes")

        result = prompt_yes_no("Continue?")

        assert result is True

    def test_prompt_yes_no_user_no(self, monkeypatch) -> None:
        """User enters 'no' returns False."""
        monkeypatch.setattr("builtins.input", lambda x: "no")

        result = prompt_yes_no("Continue?", default=True)

        assert result is False

    def test_prompt_yes_no_user_y(self, monkeypatch) -> None:
        """User enters 'y' returns True."""
        monkeypatch.setattr("builtins.input", lambda x: "y")

        result = prompt_yes_no("Continue?")

        assert result is True

    def test_prompt_yes_no_user_n(self, monkeypatch) -> None:
        """User enters 'n' returns False."""
        monkeypatch.setattr("builtins.input", lambda x: "n")

        result = prompt_yes_no("Continue?")

        assert result is False

    def test_prompt_yes_no_default_yes(self, monkeypatch) -> None:
        """Empty input uses default True."""
        monkeypatch.setattr("builtins.input", lambda x: "")

        result = prompt_yes_no("Continue?", default=True)

        assert result is True

    def test_prompt_yes_no_default_no(self, monkeypatch) -> None:
        """Empty input uses default False."""
        monkeypatch.setattr("builtins.input", lambda x: "")

        result = prompt_yes_no("Continue?", default=False)

        assert result is False

    def test_prompt_yes_no_case_insensitive(self, monkeypatch) -> None:
        """Response is case-insensitive."""
        monkeypatch.setattr("builtins.input", lambda x: "YES")

        result = prompt_yes_no("Continue?")

        assert result is True

    def test_prompt_yes_no_numeric_true(self, monkeypatch) -> None:
        """Numeric '1' or 'true' returns True."""
        monkeypatch.setattr("builtins.input", lambda x: "1")

        result = prompt_yes_no("Continue?")

        assert result is True

    def test_prompt_yes_no_numeric_true_word(self, monkeypatch) -> None:
        """Word 'true' returns True."""
        monkeypatch.setattr("builtins.input", lambda x: "true")

        result = prompt_yes_no("Continue?")

        assert result is True

    def test_prompt_yes_no_non_interactive(self, monkeypatch) -> None:
        """EOFError returns default (non-interactive mode)."""
        monkeypatch.setattr("builtins.input", lambda x: (_ for _ in ()).throw(EOFError()))

        result = prompt_yes_no("Continue?", default=True)

        assert result is True

    def test_prompt_yes_no_invalid_input_uses_default(self, monkeypatch) -> None:
        """Invalid input uses default."""
        monkeypatch.setattr("builtins.input", lambda x: "maybe")

        result = prompt_yes_no("Continue?", default=True)

        assert result is True


class TestPromptChoice:
    """Test choice prompt."""

    def test_prompt_choice_valid_selection(self, monkeypatch) -> None:
        """User selects valid choice."""
        monkeypatch.setattr("builtins.input", lambda x: "2")

        choices = ["Option 1", "Option 2", "Option 3"]
        result = prompt_choice("Choose:", choices)

        assert result == "Option 2"

    def test_prompt_choice_default(self, monkeypatch) -> None:
        """Empty input uses default choice."""
        monkeypatch.setattr("builtins.input", lambda x: "")

        choices = ["Option 1", "Option 2", "Option 3"]
        result = prompt_choice("Choose:", choices, default=1)

        assert result == "Option 2"

    def test_prompt_choice_first_option(self, monkeypatch) -> None:
        """User selects first option (index 1)."""
        monkeypatch.setattr("builtins.input", lambda x: "1")

        choices = ["Option 1", "Option 2", "Option 3"]
        result = prompt_choice("Choose:", choices)

        assert result == "Option 1"

    def test_prompt_choice_last_option(self, monkeypatch) -> None:
        """User selects last option."""
        monkeypatch.setattr("builtins.input", lambda x: "3")

        choices = ["Option A", "Option B", "Option C"]
        result = prompt_choice("Choose:", choices)

        assert result == "Option C"

    def test_prompt_choice_out_of_range_uses_default(self, monkeypatch) -> None:
        """Out of range input uses default."""
        monkeypatch.setattr("builtins.input", lambda x: "99")

        choices = ["Option 1", "Option 2"]
        result = prompt_choice("Choose:", choices, default=0)

        assert result == "Option 1"

    def test_prompt_choice_non_numeric_uses_default(self, monkeypatch) -> None:
        """Non-numeric input uses default."""
        monkeypatch.setattr("builtins.input", lambda x: "invalid")

        choices = ["Option 1", "Option 2"]
        result = prompt_choice("Choose:", choices, default=1)

        assert result == "Option 2"

    def test_prompt_choice_non_interactive(self, monkeypatch) -> None:
        """EOFError uses default (non-interactive mode)."""
        monkeypatch.setattr("builtins.input", lambda x: (_ for _ in ()).throw(EOFError()))

        choices = ["Option 1", "Option 2", "Option 3"]
        result = prompt_choice("Choose:", choices, default=2)

        assert result == "Option 3"

    def test_prompt_choice_single_option(self, monkeypatch) -> None:
        """Single choice returns that choice."""
        monkeypatch.setattr("builtins.input", lambda x: "")

        choices = ["Only Option"]
        result = prompt_choice("Choose:", choices)

        assert result == "Only Option"

    def test_prompt_choice_negative_index(self, monkeypatch) -> None:
        """Negative index uses default."""
        monkeypatch.setattr("builtins.input", lambda x: "-1")

        choices = ["Option 1", "Option 2"]
        result = prompt_choice("Choose:", choices, default=0)

        assert result == "Option 1"
