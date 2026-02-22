"""YAWL CLI package."""

__version__ = "6.0.0"
__author__ = "YAWL Team"

from yawl_cli.utils import Config, ensure_project_root

__all__ = ["Config", "ensure_project_root", "__version__"]
