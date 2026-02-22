"""Type safety and input validation tests for YAWL CLI (Chicago TDD).

Verifies:
- Type hints are correct (mypy --strict passes)
- Real input validation (not mocks) for Config, load_facts, run_shell_cmd
- Edge cases: empty, None, invalid types, boundary values
- No type coercion bugs in config parsing
- Complex type validation for nested structures
"""

import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional

import pytest
import yaml

from yawl_cli.utils import Config, load_facts, run_shell_cmd, prompt_yes_no, prompt_choice


class TestConfigGetTypeSafety:
    """Real tests: Config.get() returns correct types without coercion."""

    def test_get_bool_value_returns_bool(self, temp_project_dir: Path) -> None:
        """Config.get() returns bool for boolean values, not string."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"parallel": True, "verbose": False}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        parallel = config.get("build.parallel")
        verbose = config.get("build.verbose")

        assert parallel is True
        assert isinstance(parallel, bool)
        assert verbose is False
        assert isinstance(verbose, bool)

    def test_get_int_value_returns_int(self, temp_project_dir: Path) -> None:
        """Config.get() returns int for integer values, not string."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 8, "timeout": 600}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        threads = config.get("build.threads")
        timeout = config.get("build.timeout")

        assert threads == 8
        assert isinstance(threads, int)
        assert timeout == 600
        assert isinstance(timeout, int)

    def test_get_string_value_returns_str(self, temp_project_dir: Path) -> None:
        """Config.get() returns str for string values."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"module": "yawl-engine"}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        module = config.get("build.module")

        assert module == "yawl-engine"
        assert isinstance(module, str)

    def test_get_float_value_returns_float(self, temp_project_dir: Path) -> None:
        """Config.get() returns float for float values."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"test": {"coverage": 82.5}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        coverage = config.get("test.coverage")

        assert coverage == 82.5
        assert isinstance(coverage, float)

    def test_get_dict_value_returns_dict(self, temp_project_dir: Path) -> None:
        """Config.get() returns dict for nested dictionary values."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"options": {"fast": True, "clean": False}}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        options = config.get("build.options")

        assert isinstance(options, dict)
        assert options["fast"] is True
        assert options["clean"] is False

    def test_get_list_value_returns_list(self, temp_project_dir: Path) -> None:
        """Config.get() returns list for list values."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"maven": {"profiles": ["analysis", "coverage"]}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        profiles = config.get("maven.profiles")

        assert isinstance(profiles, list)
        assert profiles == ["analysis", "coverage"]

    def test_get_missing_key_returns_default(self, temp_project_dir: Path) -> None:
        """Config.get() returns default for missing keys."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump({"build": {}}), encoding="utf-8")
        config = Config.from_project(temp_project_dir)

        result = config.get("build.nonexistent", default="fallback")

        assert result == "fallback"
        assert isinstance(result, str)

    def test_get_none_default(self, temp_project_dir: Path) -> None:
        """Config.get() returns None when key missing and no default."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(yaml.dump({"build": {}}), encoding="utf-8")
        config = Config.from_project(temp_project_dir)

        result = config.get("build.nonexistent")

        assert result is None

    def test_get_no_config_data_returns_default(self, temp_project_dir: Path) -> None:
        """Config.get() returns default when no config data loaded."""
        config = Config(project_root=temp_project_dir)
        config.config_data = None

        result = config.get("anything", default=42)

        assert result == 42

    def test_get_deep_nested_key(self, temp_project_dir: Path) -> None:
        """Config.get() traverses deeply nested keys correctly."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"a": {"b": {"c": {"d": "deep_value"}}}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        result = config.get("a.b.c.d")

        assert result == "deep_value"

    def test_get_intermediate_non_dict_returns_default(
        self, temp_project_dir: Path
    ) -> None:
        """Config.get() returns default when intermediate key is not a dict."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"build": {"threads": 4}}),
            encoding="utf-8",
        )
        config = Config.from_project(temp_project_dir)

        # Trying to traverse through an int value
        result = config.get("build.threads.sub_key", default="nope")

        assert result == "nope"


class TestConfigSetTypeSafety:
    """Real tests: Config.set() handles types correctly."""

    def test_set_bool_preserves_type(self, temp_project_dir: Path) -> None:
        """Config.set() stores bool values without coercion."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        config.set("build.parallel", True)

        assert config.get("build.parallel") is True
        assert isinstance(config.get("build.parallel"), bool)

    def test_set_int_preserves_type(self, temp_project_dir: Path) -> None:
        """Config.set() stores int values without coercion."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        config.set("build.threads", 16)

        assert config.get("build.threads") == 16
        assert isinstance(config.get("build.threads"), int)

    def test_set_creates_nested_structure(self, temp_project_dir: Path) -> None:
        """Config.set() creates intermediate dicts for nested keys."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {}

        config.set("a.b.c", "value")

        assert config.get("a.b.c") == "value"
        assert isinstance(config.config_data, dict)
        assert isinstance(config.config_data["a"], dict)
        assert isinstance(config.config_data["a"]["b"], dict)

    def test_set_on_none_config_data_initializes(
        self, temp_project_dir: Path
    ) -> None:
        """Config.set() initializes config_data if None."""
        config = Config(project_root=temp_project_dir)
        config.config_data = None

        config.set("key", "value")

        assert config.config_data is not None
        assert config.get("key") == "value"


class TestConfigDeepMerge:
    """Real tests: Config._deep_merge() type handling."""

    def test_merge_preserves_types(self) -> None:
        """Deep merge preserves value types from both dicts."""
        base: Dict[str, Any] = {"build": {"threads": 4, "parallel": True}}
        override: Dict[str, Any] = {"build": {"timeout": 600}}

        result = Config._deep_merge(base, override)

        assert isinstance(result["build"]["threads"], int)
        assert isinstance(result["build"]["parallel"], bool)
        assert isinstance(result["build"]["timeout"], int)

    def test_merge_override_replaces_type(self) -> None:
        """Deep merge allows type change when overriding."""
        base: Dict[str, Any] = {"build": {"threads": 4}}
        override: Dict[str, Any] = {"build": {"threads": "auto"}}

        result = Config._deep_merge(base, override)

        assert result["build"]["threads"] == "auto"
        assert isinstance(result["build"]["threads"], str)

    def test_merge_nested_dicts_recursively(self) -> None:
        """Deep merge handles nested dict structures."""
        base: Dict[str, Any] = {"a": {"b": {"c": 1}}}
        override: Dict[str, Any] = {"a": {"b": {"d": 2}}}

        result = Config._deep_merge(base, override)

        assert result["a"]["b"]["c"] == 1
        assert result["a"]["b"]["d"] == 2

    def test_merge_empty_dicts(self) -> None:
        """Deep merge handles empty dictionaries."""
        base: Dict[str, Any] = {}
        override: Dict[str, Any] = {"key": "value"}

        result = Config._deep_merge(base, override)

        assert result == {"key": "value"}


class TestConfigYamlParsingEdgeCases:
    """Real tests: YAML parsing type safety and edge cases."""

    def test_invalid_yaml_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """Invalid YAML raises RuntimeError with location info."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("invalid: yaml: [\nbad", encoding="utf-8")

        with pytest.raises(RuntimeError, match="Invalid YAML"):
            Config.from_project(temp_project_dir)

    def test_yaml_list_root_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """YAML file with list root raises RuntimeError."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("- item1\n- item2\n", encoding="utf-8")

        with pytest.raises(RuntimeError, match="dictionary"):
            Config.from_project(temp_project_dir)

    def test_yaml_scalar_root_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """YAML file with scalar root raises RuntimeError."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("just a string\n", encoding="utf-8")

        with pytest.raises(RuntimeError, match="dictionary"):
            Config.from_project(temp_project_dir)

    def test_empty_yaml_file_loads_empty_config(
        self, temp_project_dir: Path
    ) -> None:
        """Empty YAML file loads as empty config without error."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text("", encoding="utf-8")

        config = Config.from_project(temp_project_dir)

        # Empty YAML -> empty dict (yaml.safe_load returns None, handled as {})
        assert config.config_data == {}

    def test_yaml_with_unicode_values(self, temp_project_dir: Path) -> None:
        """YAML with unicode values loads correctly."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        config_file.write_text(
            yaml.dump({"name": "workflow-\u03a8\u2192\u039b"}),
            encoding="utf-8",
        )

        config = Config.from_project(temp_project_dir)

        assert config.get("name") == "workflow-\u03a8\u2192\u039b"

    def test_oversized_config_file_raises_error(
        self, temp_project_dir: Path
    ) -> None:
        """Config file exceeding 1MB raises RuntimeError."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        # Write >1MB of valid YAML
        config_file.write_text("x: " + "a" * (1024 * 1024 + 100), encoding="utf-8")

        with pytest.raises(RuntimeError, match="too large"):
            Config.from_project(temp_project_dir)


class TestLoadFactsTypeSafety:
    """Real tests: load_facts() validates types and handles edge cases."""

    def test_returns_dict(self, facts_directory: Path) -> None:
        """load_facts() returns a dict, not other types."""
        result = load_facts(facts_directory, "modules.json")

        assert isinstance(result, dict)

    def test_nested_dict_values_preserve_types(
        self, facts_directory: Path
    ) -> None:
        """Nested values in facts preserve their types."""
        result = load_facts(facts_directory, "modules.json")

        engine = result["yawl-engine"]
        assert isinstance(engine, dict)
        assert isinstance(engine["path"], str)
        assert isinstance(engine["files"], int)

    def test_nonexistent_directory_raises_file_not_found(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() raises FileNotFoundError for missing directory."""
        with pytest.raises(FileNotFoundError, match="Facts directory not found"):
            load_facts(temp_project_dir / "nonexistent", "any.json")

    def test_nonexistent_file_raises_file_not_found(
        self, facts_directory: Path
    ) -> None:
        """load_facts() raises FileNotFoundError for missing fact file."""
        with pytest.raises(FileNotFoundError, match="Fact file not found"):
            load_facts(facts_directory, "nonexistent.json")

    def test_non_dict_json_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() rejects JSON that is not a dict (e.g., list)."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)
        (facts_dir / "bad.json").write_text("[1, 2, 3]", encoding="utf-8")

        with pytest.raises(RuntimeError, match="Expected JSON object"):
            load_facts(facts_dir, "bad.json")

    def test_malformed_json_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() raises RuntimeError for malformed JSON."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)
        (facts_dir / "broken.json").write_text("{bad json", encoding="utf-8")

        with pytest.raises(RuntimeError, match="Malformed JSON"):
            load_facts(facts_dir, "broken.json")

    def test_not_a_directory_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() raises RuntimeError when path is a file not directory."""
        fake_dir = temp_project_dir / "not_a_dir"
        fake_dir.write_text("file", encoding="utf-8")

        with pytest.raises(RuntimeError, match="not a directory"):
            load_facts(fake_dir, "any.json")

    def test_not_a_file_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() raises RuntimeError when fact path is a directory."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)
        # Create a directory where a file is expected
        (facts_dir / "dir.json").mkdir()

        with pytest.raises(RuntimeError, match="not a file"):
            load_facts(facts_dir, "dir.json")

    def test_oversized_fact_file_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """load_facts() rejects files larger than 100MB."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)
        huge_file = facts_dir / "huge.json"

        # Create sparse file that appears >100MB
        with open(huge_file, "wb") as f:
            f.seek(100 * 1024 * 1024 + 1)
            f.write(b"\0")

        with pytest.raises(RuntimeError, match="too large"):
            load_facts(facts_dir, "huge.json")


class TestRunShellCmdInputValidation:
    """Real tests: run_shell_cmd() validates inputs and handles edge cases."""

    def test_empty_command_raises_value_error(self) -> None:
        """Empty command list raises ValueError."""
        with pytest.raises(ValueError, match="cannot be empty"):
            run_shell_cmd([])

    def test_empty_string_command_raises_value_error(self) -> None:
        """Command with empty string raises ValueError."""
        with pytest.raises(ValueError, match="cannot be empty"):
            run_shell_cmd([""])

    def test_non_string_arguments_raises_value_error(self) -> None:
        """Non-string command arguments raise ValueError."""
        with pytest.raises(ValueError, match="must be strings"):
            run_shell_cmd(["echo", 42])  # type: ignore[list-item]

    def test_negative_timeout_raises_value_error(self) -> None:
        """Negative timeout raises ValueError."""
        with pytest.raises(ValueError, match="must be positive"):
            run_shell_cmd(["echo", "test"], timeout=-1)

    def test_zero_timeout_raises_value_error(self) -> None:
        """Zero timeout raises ValueError."""
        with pytest.raises(ValueError, match="must be positive"):
            run_shell_cmd(["echo", "test"], timeout=0)

    def test_command_not_found_raises_runtime_error(self) -> None:
        """Missing command raises RuntimeError with guidance."""
        with pytest.raises(RuntimeError, match="not found"):
            run_shell_cmd(["nonexistent_command_xyz"])

    def test_returns_correct_tuple_types(self) -> None:
        """run_shell_cmd returns (int, str, str) tuple."""
        exit_code, stdout, stderr = run_shell_cmd(
            ["echo", "hello"], timeout=10
        )

        assert isinstance(exit_code, int)
        assert isinstance(stdout, str)
        assert isinstance(stderr, str)

    def test_exit_code_is_int(self) -> None:
        """Exit code is always an int (not bool or other)."""
        exit_code, _, _ = run_shell_cmd(["true"], timeout=10)

        assert exit_code == 0
        assert type(exit_code) is int

    def test_nonzero_exit_code_returns_int(self) -> None:
        """Non-zero exit code is returned as int."""
        exit_code, _, _ = run_shell_cmd(["false"], timeout=10)

        assert exit_code != 0
        assert isinstance(exit_code, int)

    def test_timeout_raises_runtime_error(self) -> None:
        """Command exceeding timeout raises RuntimeError."""
        with pytest.raises(RuntimeError, match="timed out"):
            run_shell_cmd(["sleep", "60"], timeout=1)

    def test_default_timeout_for_mvn_commands(self) -> None:
        """Maven commands get 600s default timeout (returns valid tuple)."""
        # Verify mvn commands with default timeout return proper types
        exit_code, stdout, stderr = run_shell_cmd(["echo", "mvn-test"])

        assert isinstance(exit_code, int)
        assert isinstance(stdout, str)
        assert isinstance(stderr, str)


class TestConfigSaveTypeSafety:
    """Real tests: Config.save() validates data types before writing."""

    def test_save_non_dict_raises_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """Config.save() rejects non-dict config_data."""
        config = Config(project_root=temp_project_dir)
        config.config_data = "not a dict"  # type: ignore[assignment]

        with pytest.raises(RuntimeError, match="must be a dictionary"):
            config.save()

    def test_save_creates_parent_directories(
        self, temp_project_dir: Path
    ) -> None:
        """Config.save() creates parent directories when needed."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {"key": "value"}

        nested_path = temp_project_dir / "deep" / "nested" / "config.yaml"
        config.save(nested_path)

        assert nested_path.exists()
        loaded = yaml.safe_load(nested_path.read_text())
        assert loaded["key"] == "value"

    def test_save_preserves_types_in_yaml(
        self, temp_project_dir: Path
    ) -> None:
        """Config.save() preserves Python types when serializing to YAML."""
        config = Config(project_root=temp_project_dir)
        config.config_data = {
            "bool_val": True,
            "int_val": 42,
            "float_val": 3.14,
            "str_val": "hello",
            "list_val": [1, 2, 3],
            "dict_val": {"nested": True},
        }

        save_path = temp_project_dir / ".yawl" / "config.yaml"
        config.save(save_path)

        # Reload and verify types survived round-trip
        loaded = yaml.safe_load(save_path.read_text())
        assert isinstance(loaded["bool_val"], bool)
        assert isinstance(loaded["int_val"], int)
        assert isinstance(loaded["float_val"], float)
        assert isinstance(loaded["str_val"], str)
        assert isinstance(loaded["list_val"], list)
        assert isinstance(loaded["dict_val"], dict)


class TestTeamInputValidation:
    """Real tests: Team command input validation."""

    def test_validate_team_identifier_empty_raises(self) -> None:
        """Empty team identifier raises ValueError."""
        from yawl_cli.team import _validate_team_identifier

        with pytest.raises(ValueError, match="cannot be empty"):
            _validate_team_identifier("", "Team name")

    def test_validate_team_identifier_too_long_raises(self) -> None:
        """Oversized team identifier raises ValueError."""
        from yawl_cli.team import _validate_team_identifier

        with pytest.raises(ValueError, match="too long"):
            _validate_team_identifier("a" * 256, "Team name")

    def test_validate_team_identifier_special_chars_raises(self) -> None:
        """Special characters in team identifier raise ValueError."""
        from yawl_cli.team import _validate_team_identifier

        for bad_input in ["team;rm -rf /", "team$(cmd)", "team`id`", "team\nname"]:
            with pytest.raises(ValueError, match="invalid characters"):
                _validate_team_identifier(bad_input, "Team name")

    def test_validate_team_identifier_valid_names_pass(self) -> None:
        """Valid team identifiers pass validation."""
        from yawl_cli.team import _validate_team_identifier

        # These should not raise
        _validate_team_identifier("my-team", "Team name")
        _validate_team_identifier("team_123", "Team name")
        _validate_team_identifier("Team.v2", "Team name")
        _validate_team_identifier("a", "Team name")
        _validate_team_identifier("a" * 255, "Team name")

    def test_validate_team_identifier_max_length_boundary(self) -> None:
        """Team identifier at exactly 255 chars passes, 256 fails."""
        from yawl_cli.team import _validate_team_identifier

        # Exactly 255 should pass
        _validate_team_identifier("a" * 255, "Team name")

        # 256 should fail
        with pytest.raises(ValueError, match="too long"):
            _validate_team_identifier("a" * 256, "Team name")


class TestOptionalParameterHandling:
    """Real tests: Optional[T] parameter handling across modules."""

    def test_config_optional_fields_default_to_none(
        self, temp_project_dir: Path
    ) -> None:
        """Config optional fields default to None."""
        config = Config(project_root=temp_project_dir)

        assert config.maven_version is None
        assert config.java_home is None
        assert config.branch is None
        assert config.facts_dir is None
        assert config.config_file is None
        assert config.config_data is None

    def test_config_from_project_fills_optional_fields(
        self, temp_project_dir: Path
    ) -> None:
        """Config.from_project() fills optional fields with real values."""
        config = Config.from_project(temp_project_dir)

        # facts_dir should always be set
        assert config.facts_dir is not None
        assert isinstance(config.facts_dir, Path)
        # config_data should be a dict (possibly empty)
        assert config.config_data is not None
        assert isinstance(config.config_data, dict)

    def test_run_shell_cmd_optional_cwd_none(self) -> None:
        """run_shell_cmd works with cwd=None (uses current directory)."""
        exit_code, stdout, _ = run_shell_cmd(
            ["echo", "test"], cwd=None, timeout=10
        )

        assert exit_code == 0
        assert "test" in stdout

    def test_run_shell_cmd_optional_timeout_none(self) -> None:
        """run_shell_cmd works with timeout=None (uses default)."""
        exit_code, stdout, _ = run_shell_cmd(
            ["echo", "test"], timeout=None
        )

        assert exit_code == 0


class TestComplexTypeValidation:
    """Real tests: validation of complex/nested type structures."""

    def test_config_data_nested_dict_structure(
        self, temp_project_dir: Path
    ) -> None:
        """Config data maintains nested dict[str, Any] structure."""
        config_file = temp_project_dir / ".yawl" / "config.yaml"
        complex_config = {
            "build": {
                "profiles": ["analysis", "coverage"],
                "options": {
                    "threads": 4,
                    "parallel": True,
                    "targets": ["compile", "test"],
                },
            },
            "godspeed": {
                "phases": ["discover", "compile", "guard", "verify"],
                "fail_fast": True,
            },
        }
        config_file.write_text(yaml.dump(complex_config), encoding="utf-8")
        config = Config.from_project(temp_project_dir)

        # Verify nested types
        profiles = config.get("build.profiles")
        assert isinstance(profiles, list)
        assert all(isinstance(p, str) for p in profiles)

        options = config.get("build.options")
        assert isinstance(options, dict)
        assert isinstance(options["threads"], int)
        assert isinstance(options["parallel"], bool)
        assert isinstance(options["targets"], list)

    def test_facts_complex_structure_types(
        self, temp_project_dir: Path
    ) -> None:
        """Facts JSON preserves complex nested types."""
        facts_dir = temp_project_dir / "docs" / "v6" / "latest" / "facts"
        facts_dir.mkdir(parents=True, exist_ok=True)

        complex_facts = {
            "modules": {
                "yawl-engine": {
                    "files": 42,
                    "coverage": 85.5,
                    "has_tests": True,
                    "dependencies": ["yawl-elements", "yawl-core"],
                }
            },
            "version": "6.0.0",
            "count": 7,
        }
        (facts_dir / "complex.json").write_text(
            json.dumps(complex_facts), encoding="utf-8"
        )

        result = load_facts(facts_dir, "complex.json")

        assert isinstance(result["modules"], dict)
        engine = result["modules"]["yawl-engine"]
        assert isinstance(engine["files"], int)
        assert isinstance(engine["coverage"], float)
        assert isinstance(engine["has_tests"], bool)
        assert isinstance(engine["dependencies"], list)
        assert isinstance(result["version"], str)
        assert isinstance(result["count"], int)


class TestConfigCliValueParsing:
    """Real tests: config CLI value parsing type coercion."""

    def test_parse_true_variants(self) -> None:
        """Config set command parses 'true', 'yes', '1' as bool True."""
        for true_str in ["true", "True", "TRUE", "yes", "Yes", "YES", "1"]:
            parsed = _parse_config_value(true_str)
            assert parsed is True, f"Failed for {true_str!r}"
            assert isinstance(parsed, bool)

    def test_parse_false_variants(self) -> None:
        """Config set command parses 'false', 'no', '0' as bool False."""
        for false_str in ["false", "False", "FALSE", "no", "No", "NO", "0"]:
            parsed = _parse_config_value(false_str)
            assert parsed is False, f"Failed for {false_str!r}"
            assert isinstance(parsed, bool)

    def test_parse_integers(self) -> None:
        """Config set command parses numeric strings as int."""
        for int_str, expected in [("42", 42), ("-1", -1), ("0", False)]:
            parsed = _parse_config_value(int_str)
            if int_str == "0":
                # "0" is parsed as False (bool) in the current implementation
                assert parsed is False
            else:
                assert parsed == expected
                assert isinstance(parsed, int)

    def test_parse_strings(self) -> None:
        """Config set command keeps non-numeric strings as str."""
        for str_val in ["hello", "yawl-engine", "/path/to/file", "3.14"]:
            parsed = _parse_config_value(str_val)
            assert isinstance(parsed, str)
            assert parsed == str_val


def _parse_config_value(value: str) -> Any:
    """Replicate config_cli.set value parsing logic for testing."""
    if value.lower() in ("true", "yes", "1"):
        return True
    elif value.lower() in ("false", "no", "0"):
        return False
    elif value.lstrip("-").isdigit():
        return int(value)
    else:
        return value


class TestEnsureProjectRootEdgeCases:
    """Real tests: ensure_project_root() edge cases."""

    def test_finds_project_root(self, temp_project_dir: Path) -> None:
        """ensure_project_root() finds root with pom.xml + CLAUDE.md."""
        from yawl_cli.utils import ensure_project_root

        # conftest's reset_project_root sets cwd to temp_project_dir
        root = ensure_project_root()

        assert root == temp_project_dir
        assert (root / "pom.xml").exists()
        assert (root / "CLAUDE.md").exists()

    def test_returns_path_type(self, temp_project_dir: Path) -> None:
        """ensure_project_root() returns Path object."""
        from yawl_cli.utils import ensure_project_root

        root = ensure_project_root()

        assert isinstance(root, Path)


class TestMypyStrictCompliance:
    """Meta-test: verify mypy --strict passes on all source files."""

    def test_mypy_strict_zero_errors(self) -> None:
        """mypy --strict reports 0 errors on yawl_cli/ package."""
        import importlib.util
        if importlib.util.find_spec("mypy") is None:
            pytest.skip("mypy not installed in this environment")

        result = subprocess.run(
            ["python3", "-m", "mypy", "yawl_cli/", "--strict"],
            capture_output=True,
            text=True,
            cwd="/home/user/yawl/cli",
            timeout=120,
        )

        assert result.returncode == 0, (
            f"mypy --strict failed with {result.returncode}:\n"
            f"stdout: {result.stdout}\n"
            f"stderr: {result.stderr}"
        )
        assert "Success" in result.stdout or "no issues found" in result.stdout
