"""
YAWL Self-Play Loop v3.0 - Layer 6: ML Optimization Module

This module implements ML-driven optimization using TPOT2, GEPA, and DSPy.

Groq Configuration:
- HTTP API: Uses openai/gpt-oss-20b via GroqService (Java)
- DSPy: Uses groq/gpt-oss-20b via groq_config.py (Python)

To configure DSPy for Groq:
    from yawl_dspy.groq_config import configure_dspy_for_groq
    configure_dspy_for_groq()  # Uses GROQ_API_KEY env var
"""

__version__ = "3.0.0"
__author__ = "YAWL Team"

from .tpot2_bridge import Tpot2Bridge
from .gepa_evaluator import GepaEvaluator
from .construct_query_author import ConstructQueryAuthor

# Groq DSPy configuration (optional - requires dspy-ai)
try:
    from .groq_config import (
        configure_dspy_for_groq,
        get_groq_lm,
        is_groq_configured,
        GroqDspyLM,
        V7GapProposalModule,
        V7ChallengeModule,
        create_self_play_modules,
        GROQ_MODEL_DSPY,
        GROQ_MODEL_HTTP,
    )
    _groq_available = True
except ImportError:
    _groq_available = False

__all__ = [
    'Tpot2Bridge',
    'GepaEvaluator',
    'ConstructQueryAuthor',
]

# Add Groq exports if available
if _groq_available:
    __all__.extend([
        'configure_dspy_for_groq',
        'get_groq_lm',
        'is_groq_configured',
        'GroqDspyLM',
        'V7GapProposalModule',
        'V7ChallengeModule',
        'create_self_play_modules',
        'GROQ_MODEL_DSPY',
        'GROQ_MODEL_HTTP',
    ])