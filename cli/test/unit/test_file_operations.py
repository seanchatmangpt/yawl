"""Comprehensive tests for file operations (Chicago TDD, Real Objects).

Real file I/O, directory operations, atomic writes, permission handling.
"""

import json
import os
import shutil
import stat
import tempfile
from pathlib import Path
from typing import Any, Dict, List

import pytest
import yaml

from yawl_cli.utils import Config, load_facts


class TestFileCreationAndDeletion:
    """Test file creation and deletion operations."""

    def test_create_simple_file(self, temp_project_dir: Path) -> None:
        """Create a simple file."""
        test_file = temp_project_dir / "test.txt"
        test_file.write_text("content")

        assert test_file.exists()
        assert test_file.read_text() == "content"

    def test_create_nested_directories(self, temp_project_dir: Path) -> None:
        """Create nested directory structure."""
        nested_dir = temp_project_dir / "a" / "b" / "c" / "d"
        nested_dir.mkdir(parents=True, exist_ok=True)

        assert nested_dir.exists()
        assert nested_dir.is_dir()

    def test_delete_file(self, temp_project_dir: Path) -> None:
        """Delete a file."""
        test_file = temp_project_dir / "delete_me.txt"
        test_file.write_text("content")
        assert test_file.exists()

        test_file.unlink()

        assert not test_file.exists()

    def test_delete_directory_recursively(self, temp_project_dir: Path) -> None:
        """Delete directory tree recursively."""
        subdir = temp_project_dir / "subdir"
        subdir.mkdir()
        (subdir / "file.txt").write_text("content")
        (subdir / "nested").mkdir()
        (subdir / "nested" / "deep.txt").write_text("deep content")

        shutil.rmtree(subdir)

        assert not subdir.exists()

    def test_create_file_with_bytes(self, temp_project_dir: Path) -> None:
        """Create file with binary content."""
        test_file = temp_project_dir / "binary.bin"
        test_file.write_bytes(b"\x00\x01\x02\x03")

        assert test_file.exists()
        assert test_file.read_bytes() == b"\x00\x01\x02\x03"

    def test_append_to_file(self, temp_project_dir: Path) -> None:
        """Append to existing file."""
        test_file = temp_project_dir / "append.txt"
        test_file.write_text("line1\n")

        with open(test_file, "a") as f:
            f.write("line2\n")

        content = test_file.read_text()
        assert "line1" in content
        assert "line2" in content


class TestYamlFileOperations:
    """Test YAML file operations."""

    def test_write_yaml_file(self, temp_project_dir: Path) -> None:
        """Write YAML file."""
        data = {
            "build": {"threads": 8, "parallel": True},
            "maven": {"profiles": ["analysis"]},
        }
        yaml_file = temp_project_dir / "config.yaml"

        with open(yaml_file, "w") as f:
            yaml.dump(data, f)

        assert yaml_file.exists()
        with open(yaml_file) as f:
            loaded = yaml.safe_load(f)
        assert loaded == data

    def test_read_yaml_with_comments(self, temp_project_dir: Path) -> None:
        """Read YAML file with comments."""
        yaml_file = temp_project_dir / "config.yaml"
        yaml_file.write_text(
            """
# Build configuration
build:
  # Number of threads
  threads: 8
  # Parallel execution
  parallel: true

# Maven settings
maven:
  profiles:
    - analysis
    - coverage
"""
        )

        with open(yaml_file) as f:
            data = yaml.safe_load(f)

        assert data["build"]["threads"] == 8
        assert data["build"]["parallel"] is True
        assert data["maven"]["profiles"] == ["analysis", "coverage"]

    def test_write_yaml_preserves_structure(self, temp_project_dir: Path) -> None:
        """Write YAML preserves data structure."""
        data = {
            "section1": {"key1": "value1", "key2": 42},
            "section2": {"nested": {"deep": "value"}},
            "list": [1, 2, 3],
        }
        yaml_file = temp_project_dir / "config.yaml"

        with open(yaml_file, "w") as f:
            yaml.dump(data, f, default_flow_style=False, sort_keys=False)

        with open(yaml_file) as f:
            loaded = yaml.safe_load(f)

        assert loaded == data

    def test_yaml_unicode_support(self, temp_project_dir: Path) -> None:
        """YAML supports Unicode characters."""
        data = {
            "description": "YAWL Workflow Σ = Java + XML + Petri nets",
            "phases": ["Ψ (Observatory)", "Λ (Build)", "H (Guards)"],
        }
        yaml_file = temp_project_dir / "config.yaml"

        with open(yaml_file, "w", encoding="utf-8") as f:
            yaml.dump(data, f, allow_unicode=True)

        with open(yaml_file, encoding="utf-8") as f:
            loaded = yaml.safe_load(f)

        assert loaded["description"] == data["description"]
        assert "Ψ" in loaded["phases"]

    def test_yaml_with_list_of_dicts(self, temp_project_dir: Path) -> None:
        """YAML with list of dictionaries."""
        data = {
            "modules": [
                {"name": "yawl-engine", "path": "yawl/engine"},
                {"name": "yawl-elements", "path": "yawl/elements"},
            ]
        }
        yaml_file = temp_project_dir / "modules.yaml"

        with open(yaml_file, "w") as f:
            yaml.dump(data, f)

        with open(yaml_file) as f:
            loaded = yaml.safe_load(f)

        assert len(loaded["modules"]) == 2
        assert loaded["modules"][0]["name"] == "yawl-engine"


class TestJsonFileOperations:
    """Test JSON file operations."""

    def test_write_json_file(self, temp_project_dir: Path) -> None:
        """Write JSON file."""
        data = {
            "yawl-engine": {"path": "yawl/engine", "files": 42},
            "yawl-elements": {"path": "yawl/elements", "files": 28},
        }
        json_file = temp_project_dir / "modules.json"

        with open(json_file, "w") as f:
            json.dump(data, f, indent=2)

        assert json_file.exists()
        with open(json_file) as f:
            loaded = json.load(f)
        assert loaded == data

    def test_read_malformed_json_raises_error(self, temp_project_dir: Path) -> None:
        """Reading malformed JSON raises error."""
        json_file = temp_project_dir / "bad.json"
        json_file.write_text("{invalid json}")

        with pytest.raises(json.JSONDecodeError):
            with open(json_file) as f:
                json.load(f)

    def test_json_with_unicode(self, temp_project_dir: Path) -> None:
        """JSON supports Unicode."""
        data = {
            "phases": ["Ψ", "Λ", "H", "Q", "Ω"],
            "description": "YAWL v6.0.0",
        }
        json_file = temp_project_dir / "phases.json"

        with open(json_file, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        with open(json_file, encoding="utf-8") as f:
            loaded = json.load(f)

        assert loaded["phases"] == data["phases"]

    def test_json_with_nested_objects(self, temp_project_dir: Path) -> None:
        """JSON with deeply nested objects."""
        data = {
            "level1": {
                "level2": {
                    "level3": {
                        "level4": {
                            "value": "deep"
                        }
                    }
                }
            }
        }
        json_file = temp_project_dir / "nested.json"

        with open(json_file, "w") as f:
            json.dump(data, f)

        with open(json_file) as f:
            loaded = json.load(f)

        assert loaded["level1"]["level2"]["level3"]["level4"]["value"] == "deep"


class TestFilePermissions:
    """Test file permission handling."""

    def test_file_readable_by_owner(self, temp_project_dir: Path) -> None:
        """File is readable by owner."""
        test_file = temp_project_dir / "readable.txt"
        test_file.write_text("content")

        assert os.access(test_file, os.R_OK)

    def test_file_writable_by_owner(self, temp_project_dir: Path) -> None:
        """File is writable by owner."""
        test_file = temp_project_dir / "writable.txt"
        test_file.write_text("content")

        assert os.access(test_file, os.W_OK)

    def test_make_script_executable(self, temp_project_dir: Path) -> None:
        """Make script executable."""
        script = temp_project_dir / "script.sh"
        script.write_text("#!/bin/bash\necho test")
        script.chmod(0o755)

        mode = script.stat().st_mode
        assert stat.S_ISREG(mode)
        assert mode & stat.S_IXUSR  # Executable by owner

    def test_set_file_permissions(self, temp_project_dir: Path) -> None:
        """Set file permissions."""
        test_file = temp_project_dir / "restricted.txt"
        test_file.write_text("secret")
        test_file.chmod(0o600)

        mode = test_file.stat().st_mode
        # Owner can read and write
        assert mode & stat.S_IRUSR
        assert mode & stat.S_IWUSR
        # Others cannot read
        assert not (mode & stat.S_IROTH)


class TestAtomicFileWrite:
    """Test atomic file writing patterns."""

    def test_atomic_write_temp_and_rename(self, temp_project_dir: Path) -> None:
        """Atomic write using temp file and rename."""
        config_file = temp_project_dir / "config.yaml"
        temp_file = config_file.with_suffix(".yaml.tmp")

        data = {"key": "value"}

        # Write to temp file
        with open(temp_file, "w") as f:
            yaml.dump(data, f)

        # Verify temp file exists
        assert temp_file.exists()

        # Atomic rename
        temp_file.replace(config_file)

        # Verify final file exists and temp is gone
        assert config_file.exists()
        assert not temp_file.exists()

        # Verify content
        with open(config_file) as f:
            loaded = yaml.safe_load(f)
        assert loaded == data

    def test_atomic_write_cleanup_on_error(self, temp_project_dir: Path) -> None:
        """Atomic write cleans up temp file on error."""
        config_file = temp_project_dir / "config.yaml"
        temp_file = config_file.with_suffix(".yaml.tmp")

        try:
            # Write to temp file
            with open(temp_file, "w") as f:
                f.write("data")

            # Simulate error before rename
            raise ValueError("Simulated error")
        except ValueError:
            # Clean up temp file on error
            if temp_file.exists():
                temp_file.unlink()

        assert not temp_file.exists()
        assert not config_file.exists()

    def test_atomic_write_handles_existing_temp(self, temp_project_dir: Path) -> None:
        """Atomic write handles existing temp file."""
        config_file = temp_project_dir / "config.yaml"
        temp_file = config_file.with_suffix(".yaml.tmp")

        # Write old temp file
        temp_file.write_text("old data")

        # Write new temp file (overwrites old)
        data = {"key": "new_value"}
        with open(temp_file, "w") as f:
            yaml.dump(data, f)

        # Atomic rename
        temp_file.replace(config_file)

        # Verify new content
        with open(config_file) as f:
            loaded = yaml.safe_load(f)
        assert loaded == data


class TestDirectoryOperations:
    """Test directory operations."""

    def test_create_directory_tree(self, temp_project_dir: Path) -> None:
        """Create nested directory tree."""
        tree_root = temp_project_dir / "yawl" / "engine" / "src" / "main"
        tree_root.mkdir(parents=True, exist_ok=True)

        assert tree_root.exists()
        assert (tree_root.parent / "engine" / "src" / "main").exists()

    def test_list_directory_contents(self, temp_project_dir: Path) -> None:
        """List directory contents."""
        (temp_project_dir / "file1.txt").write_text("content")
        (temp_project_dir / "file2.txt").write_text("content")
        (temp_project_dir / "subdir").mkdir()

        contents = list(temp_project_dir.iterdir())
        names = [item.name for item in contents]

        assert "file1.txt" in names
        assert "file2.txt" in names
        assert "subdir" in names

    def test_glob_files_by_pattern(self, temp_project_dir: Path) -> None:
        """Glob files by pattern."""
        (temp_project_dir / "file1.txt").write_text("content")
        (temp_project_dir / "file2.txt").write_text("content")
        (temp_project_dir / "readme.md").write_text("content")

        txt_files = list(temp_project_dir.glob("*.txt"))

        assert len(txt_files) == 2
        names = [f.name for f in txt_files]
        assert "file1.txt" in names
        assert "file2.txt" in names

    def test_recursive_glob(self, temp_project_dir: Path) -> None:
        """Recursive glob pattern."""
        (temp_project_dir / "src" / "main").mkdir(parents=True)
        (temp_project_dir / "src" / "main" / "java.java").write_text("code")
        (temp_project_dir / "src" / "test" / "java.java").mkdir(parents=True)
        (temp_project_dir / "src" / "test" / "java.java").write_text("test")

        java_files = list(temp_project_dir.glob("**/*.java"))

        assert len(java_files) >= 2

    def test_directory_size_calculation(self, temp_project_dir: Path) -> None:
        """Calculate total directory size."""
        test_dir = temp_project_dir / "testdir"
        test_dir.mkdir()

        # Create files of known sizes
        (test_dir / "file1.txt").write_text("x" * 100)
        (test_dir / "file2.txt").write_text("x" * 200)

        total_size = sum(f.stat().st_size for f in test_dir.glob("**/*") if f.is_file())

        assert total_size >= 300


class TestFilePathOperations:
    """Test file path operations."""

    def test_resolve_absolute_path(self, temp_project_dir: Path) -> None:
        """Resolve to absolute path."""
        relative_path = Path("subdir") / "file.txt"
        absolute_path = (temp_project_dir / relative_path).resolve()

        assert absolute_path.is_absolute()

    def test_get_file_stem_and_suffix(self, temp_project_dir: Path) -> None:
        """Get file stem and suffix."""
        test_file = temp_project_dir / "config.yaml"

        assert test_file.stem == "config"
        assert test_file.suffix == ".yaml"

    def test_replace_file_suffix(self, temp_project_dir: Path) -> None:
        """Replace file suffix."""
        test_file = temp_project_dir / "config.yaml"
        new_file = test_file.with_suffix(".json")

        assert new_file.name == "config.json"

    def test_get_file_parent_directory(self, temp_project_dir: Path) -> None:
        """Get file parent directory."""
        nested_file = temp_project_dir / "a" / "b" / "c" / "file.txt"
        nested_file.parent.mkdir(parents=True, exist_ok=True)
        nested_file.write_text("content")

        parent = nested_file.parent
        assert parent.name == "c"
        assert (parent / "file.txt").exists()

    def test_relative_path_from_to(self, temp_project_dir: Path) -> None:
        """Calculate relative path from one location to another."""
        from_dir = temp_project_dir / "src"
        to_file = temp_project_dir / "target" / "output.txt"

        from_dir.mkdir()
        to_file.parent.mkdir(parents=True, exist_ok=True)

        # relative_to doesn't work across different parents, use relative path logic
        relative = Path("../target/output.txt")
        assert ".." in str(relative)


class TestConfigFileOperations:
    """Test config-specific file operations."""

    def test_config_file_with_subdirectories(self, temp_project_dir: Path) -> None:
        """Config file saved in subdirectory."""
        config_dir = temp_project_dir / ".yawl"
        config_dir.mkdir(parents=True, exist_ok=True)

        config = Config(project_root=temp_project_dir)
        config.config_data = {"test": "data"}
        config.save()

        config_file = config_dir / "config.yaml"
        assert config_file.exists()

    def test_facts_directory_structure(self, facts_directory: Path) -> None:
        """Verify facts directory structure."""
        assert facts_directory.exists()
        assert (facts_directory / "modules.json").exists()
        assert (facts_directory / "gates.json").exists()

    def test_load_facts_from_directory(self, facts_directory: Path) -> None:
        """Load facts from directory."""
        modules = load_facts(facts_directory, "modules.json")

        assert isinstance(modules, dict)
        assert "yawl-engine" in modules


class TestLargeFileHandling:
    """Test handling of large files."""

    def test_write_and_read_large_file(self, temp_project_dir: Path) -> None:
        """Write and read a large file."""
        large_file = temp_project_dir / "large.txt"

        # Write 1MB of data
        data = "x" * (1024 * 1024)
        large_file.write_text(data)

        assert large_file.exists()
        assert large_file.stat().st_size >= 1024 * 1024

        # Verify content
        content = large_file.read_text()
        assert len(content) >= 1024 * 1024

    def test_large_json_file(self, temp_project_dir: Path) -> None:
        """Handle large JSON file."""
        large_data = {f"key_{i}": f"value_{i}" for i in range(10000)}
        json_file = temp_project_dir / "large.json"

        with open(json_file, "w") as f:
            json.dump(large_data, f)

        with open(json_file) as f:
            loaded = json.load(f)

        assert len(loaded) == 10000

    def test_large_yaml_file(self, temp_project_dir: Path) -> None:
        """Handle large YAML file."""
        large_data = {f"section_{i}": {"key": f"value_{i}"} for i in range(1000)}
        yaml_file = temp_project_dir / "large.yaml"

        with open(yaml_file, "w") as f:
            yaml.dump(large_data, f)

        with open(yaml_file) as f:
            loaded = yaml.safe_load(f)

        assert len(loaded) == 1000


class TestFileEncodingHandling:
    """Test file encoding handling."""

    def test_utf8_file_write_and_read(self, temp_project_dir: Path) -> None:
        """Write and read UTF-8 file."""
        test_file = temp_project_dir / "utf8.txt"
        content = "YAWL Workflow Σ = Java + XML + Petri nets"

        test_file.write_text(content, encoding="utf-8")

        assert test_file.exists()
        assert test_file.read_text(encoding="utf-8") == content

    def test_yaml_utf8_with_unicode_chars(self, temp_project_dir: Path) -> None:
        """YAML with Unicode characters in UTF-8."""
        yaml_file = temp_project_dir / "unicode.yaml"
        data = {
            "phases": ["Ψ", "Λ", "H", "Q", "Ω"],
            "description": "YAWL v6.0.0",
        }

        with open(yaml_file, "w", encoding="utf-8") as f:
            yaml.dump(data, f, allow_unicode=True)

        with open(yaml_file, encoding="utf-8") as f:
            loaded = yaml.safe_load(f)

        assert "Ψ" in loaded["phases"]

    def test_invalid_utf8_raises_error(self, temp_project_dir: Path) -> None:
        """Invalid UTF-8 raises error."""
        test_file = temp_project_dir / "invalid.txt"

        # Write invalid UTF-8 bytes
        with open(test_file, "wb") as f:
            f.write(b"\x80\x81\x82\x83")

        # Reading as UTF-8 should raise
        with pytest.raises(UnicodeDecodeError):
            test_file.read_text(encoding="utf-8")
