"""
DSPy program serialization for Java/GraalPy integration.

This module provides utilities for saving and loading optimized DSPy programs
to/from JSON files. This enables GEPA-optimized programs to persist across
JVM restarts without recompilation.

Author: YAWL Foundation
Version: 6.0.0
"""

import json
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional
from datetime import datetime

try:
    import dspy
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False


class DspyProgramSerializer:
    """Serializes DSPy programs to JSON for Java consumption.

    The serialized format captures:
    - Optimized prompts from each predictor's signature
    - Few-shot examples from BootstrapFewShot compilation
    - Program metadata (version, optimization metrics)
    - Source hash for cache invalidation

    This enables DSPy programs to be:
    1. Saved after GEPA optimization
    2. Loaded from Java without Python recompilation
    3. Exposed via MCP tools and A2A skills
    """

    @staticmethod
    def serialize(program: 'dspy.Module', name: str, metadata: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Serialize a DSPy program to a JSON-serializable dict.

        Extracts optimized prompts, signature definitions, and metadata
        from a compiled DSPy program.

        Args:
            program: A DSPy module (optionally compiled with GEPA/BootstrapFewShot)
            name: Program name for identification
            metadata: Optional metadata (optimizer, metrics, etc.)

        Returns:
            JSON-serializable dictionary with program state
        """
        if not DSPY_AVAILABLE:
            raise ImportError("DSPy is not available. Install dspy-ai to use serialization.")

        predictors = {}
        for pred_name, predictor in program.named_predictors():
            pred_data = {
                "signature": DspyProgramSerializer._serialize_signature(predictor.signature),
                "demos": DspyProgramSerializer._serialize_demos(predictor),
            }

            # Extract learned instructions if available
            if hasattr(predictor, 'signature') and hasattr(predictor.signature, 'instructions'):
                pred_data["learned_instructions"] = predictor.signature.instructions

            predictors[pred_name] = pred_data

        return {
            "name": name,
            "version": "1.0.0",
            "dspy_version": dspy.__version__ if hasattr(dspy, '__version__') else "unknown",
            "predictors": predictors,
            "metadata": metadata or {},
            "source_hash": DspyProgramSerializer._hash_program(program),
            "serialized_at": datetime.utcnow().isoformat() + "Z",
        }

    @staticmethod
    def _serialize_signature(signature) -> Dict[str, Any]:
        """Serialize a DSPy signature to dict format."""
        sig_data = {}

        # Get instructions
        if hasattr(signature, 'instructions'):
            sig_data["instructions"] = signature.instructions
        elif hasattr(signature, '__doc__'):
            sig_data["instructions"] = signature.__doc__ or ""

        # Get input fields
        input_fields = []
        if hasattr(signature, 'input_fields'):
            for name, field in signature.input_fields.items():
                input_fields.append({
                    "name": name,
                    "desc": getattr(field, 'desc', None) or "",
                })
        sig_data["input_fields"] = input_fields

        # Get output fields
        output_fields = []
        if hasattr(signature, 'output_fields'):
            for name, field in signature.output_fields.items():
                output_fields.append({
                    "name": name,
                    "desc": getattr(field, 'desc', None) or "",
                })
        sig_data["output_fields"] = output_fields

        return sig_data

    @staticmethod
    def _serialize_demos(predictor) -> List[Dict[str, Any]]:
        """Serialize few-shot demos from a predictor."""
        demos = []
        if hasattr(predictor, 'demos') and predictor.demos:
            for demo in predictor.demos:
                if hasattr(demo, '_asdict'):
                    demos.append(demo._asdict())
                elif isinstance(demo, dict):
                    demos.append(demo)
                else:
                    demos.append({"demo": str(demo)})
        return demos

    @staticmethod
    def _hash_program(program: 'dspy.Module') -> str:
        """Generate SHA-256 hash of program state."""
        data = DspyProgramSerializer.serialize(program, "temp")
        # Remove fields that change on each serialization
        data.pop("serialized_at", None)
        data.pop("source_hash", None)
        canonical = json.dumps(data, sort_keys=True)
        return hashlib.sha256(canonical.encode()).hexdigest()

    @staticmethod
    def save(program: 'dspy.Module', name: str, path: Path, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Save a DSPy program to a JSON file.

        Args:
            program: The DSPy module to save
            name: Program name
            path: Output file path
            metadata: Optional metadata dict

        Returns:
            The path to the saved file as a string
        """
        path = Path(path)
        data = DspyProgramSerializer.serialize(program, name, metadata)

        # Ensure parent directory exists
        path.parent.mkdir(parents=True, exist_ok=True)

        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)

        return str(path)

    @staticmethod
    def load(path: Path) -> Dict[str, Any]:
        """Load a saved DSPy program from JSON.

        Note: This returns the raw JSON data. To reconstruct an executable
        DSPy module, use reconstruct_module().

        Args:
            path: Path to the JSON file

        Returns:
            Dictionary containing the saved program data
        """
        path = Path(path)
        if not path.exists():
            raise FileNotFoundError(f"Program file not found: {path}")

        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    @staticmethod
    def reconstruct_module(data: Dict[str, Any]) -> Optional['dspy.Module']:
        """Reconstruct an executable DSPy module from saved data.

        This creates a new DSPy module with the saved instructions and demos.
        The module can then be used for inference without recompilation.

        Args:
            data: The saved program data from load()

        Returns:
            A DSPy module ready for inference, or None if reconstruction fails
        """
        if not DSPY_AVAILABLE:
            return None

        predictors_data = data.get("predictors", {})

        class ReconstructedModule(dspy.Module):
            def __init__(self):
                super().__init__()
                self._predictor_names = list(predictors_data.keys())
                for pred_name, pred_data in predictors_data.items():
                    # Create signature from saved data
                    sig_data = pred_data.get("signature", {})
                    instructions = sig_data.get("instructions", "")

                    # Build input/output field strings
                    input_names = [f["name"] for f in sig_data.get("input_fields", [])]
                    output_names = [f["name"] for f in sig_data.get("output_fields", [])]

                    # Create dynamic signature
                    sig_str = " -> ".join([", ".join(input_names), ", ".join(output_names)])
                    sig = dspy.Signature(sig_str, instructions)

                    # Create predictor with learned instructions
                    predictor = dspy.ChainOfThought(sig)

                    # Restore demos if available
                    demos = pred_data.get("demos", [])
                    if demos:
                        predictor.demos = [dspy.Example(**d) for d in demos]

                    setattr(self, pred_name, predictor)

            def forward(self, **kwargs):
                # Default: use first predictor
                first_pred_name = self._predictor_names[0] if self._predictor_names else None
                if first_pred_name:
                    return getattr(self, first_pred_name)(**kwargs)
                raise ValueError("No predictors available in reconstructed module")

        return ReconstructedModule()


def save_optimized_program(
    program: 'dspy.Module',
    output_path: str,
    name: str,
    metadata: Optional[Dict[str, Any]] = None
) -> str:
    """Convenience function to save an optimized DSPy program.

    This is the main entry point for saving GEPA-optimized programs
    after compilation.

    Args:
        program: The compiled DSPy program
        output_path: Path to save the JSON file
        name: Program name for identification
        metadata: Optional metadata (optimizer, train_size, val_score, etc.)

    Returns:
        The path to the saved file

    Example:
        >>> optimizer = GEPA(metric=metric, auto="heavy")
        >>> optimized = optimizer.compile(program, trainset=train_set)
        >>> save_optimized_program(
        ...     optimized,
        ...     "/var/lib/yawl/dspy/programs/worklet_selector.json",
        ...     "worklet_selector",
        ...     {"optimizer": "GEPA", "val_score": 0.95}
        ... )
    """
    return DspyProgramSerializer.save(program, name, Path(output_path), metadata)


def load_program_info(path: str) -> Dict[str, Any]:
    """Load program information from a saved JSON file.

    Args:
        path: Path to the JSON file

    Returns:
        Dictionary with program metadata and predictor info
    """
    return DspyProgramSerializer.load(Path(path))


def list_saved_programs(programs_dir: str) -> List[Dict[str, Any]]:
    """List all saved DSPy programs in a directory.

    Args:
        programs_dir: Directory containing .json program files

    Returns:
        List of program info dictionaries
    """
    programs_path = Path(programs_dir)
    if not programs_path.exists():
        return []

    programs = []
    for json_file in programs_path.glob("*.json"):
        try:
            data = DspyProgramSerializer.load(json_file)
            programs.append({
                "name": data.get("name", json_file.stem),
                "path": str(json_file),
                "version": data.get("version", "unknown"),
                "predictor_count": len(data.get("predictors", {})),
                "serialized_at": data.get("serialized_at"),
            })
        except (json.JSONDecodeError, KeyError):
            continue

    return programs
