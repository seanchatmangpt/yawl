"""
YAWL Self-Play Loop v3.0 - Layer 6: ML Optimization Module

This module implements ML-driven optimization using TPOT2, GEPA, and DSPy.
"""

__version__ = "3.0.0"
__author__ = "YAWL Team"

from .tpot2_bridge import Tpot2Bridge
from .gepa_evaluator import GepaEvaluator
from .construct_query_author import ConstructQueryAuthor

__all__ = [
    'Tpot2Bridge',
    'GepaEvaluator',
    'ConstructQueryAuthor'
]