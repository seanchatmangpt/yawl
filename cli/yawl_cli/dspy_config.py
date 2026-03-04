"""
DSPy configuration for YAWL CLI.

Configures DSPy to use Groq's LLM endpoints for all inference.
All LLM calls in YAWL should go through DSPy for consistent
observability and optimization.

Usage:
    from yawl_cli.dspy_config import configure_dspy, get_dspy_lm

    # Configure at startup
    configure_dspy()

    # Get the configured LM for custom use
    lm = get_dspy_lm()
"""

from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import dspy

__all__ = [
    "configure_dspy",
    "get_dspy_lm",
    "DSPY_MODEL",
    "DSPY_API_BASE",
    "DSPY_API_KEY_ENV",
]

# Default Groq model for YAWL DSPy inference
DSPY_MODEL = os.getenv("DSPY_LM_MODEL", "groq/gpt-oss-20b")

# Groq API base URL (OpenAI-compatible)
DSPY_API_BASE = os.getenv("DSPY_API_BASE", "https://api.groq.com/openai/v1")

# Environment variable for Groq API key
DSPY_API_KEY_ENV = "GROQ_API_KEY"

# Module-level cached LM instance
_dspy_lm: dspy.LM | None = None


def configure_dspy(
    model: str | None = None,
    api_key: str | None = None,
    api_base: str | None = None,
    temperature: float = 0.0,
    max_tokens: int = 4096,
) -> dspy.LM:
    """
    Configure DSPy to use Groq LLM endpoints.

    This should be called once at application startup. It configures
    the global DSPy language model to use Groq's OpenAI-compatible API.

    Args:
        model: Model identifier (default: from DSPY_LM_MODEL env or groq/gpt-oss-20b)
        api_key: Groq API key (default: from GROQ_API_KEY env)
        api_base: API base URL (default: https://api.groq.com/openai/v1)
        temperature: Sampling temperature (default: 0.0 for deterministic output)
        max_tokens: Maximum tokens in response (default: 4096)

    Returns:
        The configured DSPy LM instance

    Raises:
        ImportError: If dspy is not installed
        ValueError: If API key is not provided and not in environment

    Example:
        >>> from yawl_cli.dspy_config import configure_dspy
        >>> lm = configure_dspy()
        >>> dspy.settings.configure(lm=lm)
    """
    global _dspy_lm

    try:
        import dspy
    except ImportError as e:
        raise ImportError(
            "DSPy is not installed. Install with: uv add dspy==3.1.3"
        ) from e

    # Resolve model
    resolved_model = model or DSPY_MODEL

    # Resolve API key
    resolved_api_key = api_key or os.getenv(DSPY_API_KEY_ENV)
    if not resolved_api_key:
        raise ValueError(
            f"Groq API key required. Set {DSPY_API_KEY_ENV} environment variable "
            "or pass api_key parameter."
        )

    # Resolve API base
    resolved_api_base = api_base or DSPY_API_BASE

    # Create DSPy LM instance
    # DSPy 3.x uses dspy.LM for all LLM interactions
    _dspy_lm = dspy.LM(
        model=resolved_model,
        api_key=resolved_api_key,
        api_base=resolved_api_base,
        temperature=temperature,
        max_tokens=max_tokens,
    )

    # Configure DSPy globally
    dspy.settings.configure(lm=_dspy_lm)

    return _dspy_lm


def get_dspy_lm() -> dspy.LM:
    """
    Get the configured DSPy LM instance.

    If not yet configured, this will auto-configure using environment
    variables.

    Returns:
        The configured DSPy LM instance

    Raises:
        ImportError: If dspy is not installed
        ValueError: If not configured and API key not available
    """
    global _dspy_lm

    if _dspy_lm is None:
        return configure_dspy()

    return _dspy_lm


def create_custom_lm(
    model: str,
    api_key: str | None = None,
    api_base: str | None = None,
    temperature: float = 0.0,
    max_tokens: int = 4096,
) -> dspy.LM:
    """
    Create a custom DSPy LM without affecting global configuration.

    Use this when you need a different model for specific tasks
    without changing the global DSPy settings.

    Args:
        model: Model identifier (e.g., "groq/llama-3.3-70b-versatile")
        api_key: API key (default: from GROQ_API_KEY env)
        api_base: API base URL (default: Groq API)
        temperature: Sampling temperature
        max_tokens: Maximum tokens in response

    Returns:
        A new DSPy LM instance (not globally configured)

    Example:
        >>> from yawl_cli.dspy_config import create_custom_lm
        >>> custom_lm = create_custom_lm("groq/llama-3.3-70b-versatile")
        >>> # Use with specific DSPy modules
    """
    try:
        import dspy
    except ImportError as e:
        raise ImportError(
            "DSPy is not installed. Install with: uv add dspy==3.1.3"
        ) from e

    resolved_api_key = api_key or os.getenv(DSPY_API_KEY_ENV)
    if not resolved_api_key:
        raise ValueError(
            f"API key required. Set {DSPY_API_KEY_ENV} environment variable "
            "or pass api_key parameter."
        )

    resolved_api_base = api_base or DSPY_API_BASE

    return dspy.LM(
        model=model,
        api_key=resolved_api_key,
        api_base=resolved_api_base,
        temperature=temperature,
        max_tokens=max_tokens,
    )
