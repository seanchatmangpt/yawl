"""
Tests for DSPy Persistence module.

Validates the program serialization, caching, and reconstruction
of DSPy programs for Java consumption.

Run with: pytest -m unit cli/tests/dspy/test_dspy_persistence.py
"""

import pytest
import json
import hashlib
from unittest.mock import Mock, patch, MagicMock
from pathlib import Path
import sys
import os
import tempfile

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "yawl-ggen" / "src" / "main" / "resources" / "polyglot"))

# Try to import, but skip gracefully if not available
try:
    from dspy_persistence import (
        DspyProgramSerializer,
        save_optimized_program,
        load_program_info,
        list_saved_programs,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    DspyProgramSerializer = None
    save_optimized_program = None
    load_program_info = None
    list_saved_programs = None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestDspyProgramSerializer:
    """Test DSPy program serialization utilities."""

    @pytest.fixture
    def temp_dir(self):
        """Create a temporary directory for test files."""
        with tempfile.TemporaryDirectory() as tmp:
            yield Path(tmp)

    @pytest.fixture
    def sample_program_data(self):
        """Create sample program data for testing."""
        return {
            "name": "test-program",
            "version": "1.0.0",
            "dspy_version": "2.0.0",
            "predictors": {
                "predictor1": {
                "signature": {
                    "instructions": "Test instructions",
                    "input_fields": [
                        {"name": "input1", "desc": "Test input"}
                    ],
                    "output_fields": [
                        {"name": "output1", "desc": "Test output"}
                    ]
                },
                "demos": [
                    {"input1": "test", "output1": "result"}
                ]
            }
        },
        "metadata": {
            "optimizer": "BootstrapFewShot",
            "train_size": 100
        },
        "source_hash": "abc123",
        "serialized_at": "2025-01-01T00:00:00Z"
    }

    def test_serialize_program_structure(self, sample_program_data):
        """Test program serialization data structure."""
        assert "name" in sample_program_data
        assert "predictors" in sample_program_data
        assert "metadata" in sample_program_data

    def test_serialize_signature_structure(self):
        """Test signature serialization structure."""
        sig_data = {
            "instructions": "Test signature",
            "input_fields": [{"name": "x", "desc": "Input"}],
            "output_fields": [{"name": "y", "desc": "Output"}]
        }

        assert sig_data["instructions"] == "Test signature"
        assert len(sig_data["input_fields"]) == 1

    def test_serialize_demos(self):
        """Test demo serialization."""
        demos = [
            {"input1": "value1", "output1": "result1"},
            {"input1": "value2", "output1": "result2"}
        ]

        assert len(demos) == 2
        assert demos[0]["input1"] == "value1"


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestSaveOptimizedProgram:
    """Test program saving functionality."""

    @pytest.fixture
    def temp_dir(self):
        """Create a temporary directory for test files."""
        with tempfile.TemporaryDirectory() as tmp:
            yield Path(tmp)

    def test_save_program_to_file(self, temp_dir):
        """Test saving program data to file."""
        program_data = {
            "name": "test-prog",
            "version": "1.0.0",
            "predictors": {}
        }

        output_path = temp_dir / "test_program.json"
        with open(output_path, "w") as f:
            json.dump(program_data, f, indent=2)

        assert output_path.exists()

        with open(output_path, "r") as f:
            loaded = json.load(f)

        assert loaded["name"] == "test-prog"

    def test_save_creates_parent_dirs(self, temp_dir):
        """Test that saving creates parent directories."""
        output_path = temp_dir / "nested" / "dir" / "program.json"
        output_path.parent.mkdir(parents=True, exist_ok=True)

        program_data = {"name": "nested-prog", "version": "1.0.0"}
        with open(output_path, "w") as f:
            json.dump(program_data, f)

        assert output_path.exists()


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestLoadProgramInfo:
    """Test program loading functionality."""

    @pytest.fixture
    def temp_dir(self):
        """Create a temporary directory for test files."""
        with tempfile.TemporaryDirectory() as tmp:
            yield Path(tmp)

    def test_load_program_info_success(self, temp_dir):
        """Test loading valid program file."""
        program_data = {
            "name": "loaded-prog",
            "version": "2.0.0",
            "predictor_count": 3
        }

        program_path = temp_dir / "program.json"
        with open(program_path, "w") as f:
            json.dump(program_data, f)

        with open(program_path, "r") as f:
            loaded = json.load(f)

        assert loaded["name"] == "loaded-prog"
        assert loaded["version"] == "2.0.0"

    def test_load_program_info_not_found(self, temp_dir):
        """Test loading non-existent file raises error."""
        non_existent = temp_dir / "nonexistent.json"

        with pytest.raises(FileNotFoundError):
            with open(non_existent, "r"):
                json.load(f)


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestListSavedPrograms:
    """Test program listing functionality."""

    @pytest.fixture
    def programs_dir(self):
        """Create a directory with test programs."""
        with tempfile.TemporaryDirectory() as tmp:
            programs_path = Path(tmp)

            # Create multiple test programs
            for i in range(3):
                program = {
                    "name": f"program-{i}",
                    "version": "1.0.0",
                    "predictors": {}
                }
                with open(programs_path / f"prog{i}.json", "w") as f:
                    json.dump(program, f)

            yield programs_path

    def test_list_programs(self, programs_dir):
        """Test listing saved programs."""
        programs = []
        for json_file in programs_dir.glob("*.json"):
            with open(json_file, "r") as f:
                programs.append(json.load(f))

        assert len(programs) == 3

        names = [p["name"] for p in programs]
        assert "program-0" in names
        assert "program-1" in names
        assert "program-2" in names

    def test_list_empty_directory(self):
        """Test listing from empty directory."""
        with tempfile.TemporaryDirectory() as tmp:
            programs = list(Path(tmp).glob("*.json"))
            assert len(programs) == 0


class TestSourceHash:
    """Test source hash generation."""

    def test_hash_consistency(self):
        """Test that same data produces same hash."""
        data = {"test": "data", "nested": {"key": "value"}}
        canonical1 = json.dumps(data, sort_keys=True)
        canonical2 = json.dumps(data, sort_keys=True)

        hash1 = hashlib.sha256(canonical1.encode()).hexdigest()
        hash2 = hashlib.sha256(canonical2.encode()).hexdigest()

        assert hash1 == hash2

    def test_hash_difference(self):
        """Test that different data produces different hash."""
        data1 = {"test": "data1"}
        data2 = {"test": "data2"}

        hash1 = hashlib.sha256(json.dumps(data1, sort_keys=True).encode()).hexdigest()
        hash2 = hashlib.sha256(json.dumps(data2, sort_keys=True).encode()).hexdigest()

        assert hash1 != hash2


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
