#!/usr/bin/env python3
"""
Debug script for TPOT2 Bridge
"""

import logging
from yawl_dspy import Tpot2Bridge

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create mock compositions
mock_compositions = [
    {
        'pipeline': 'http://yawl.io/sim/pipeline_1',
        'type': 'Filter',
        'params': {'threshold': 0.7, 'strategy': 'hash'}
    },
    {
        'pipeline': 'http://yawl.io/sim/pipeline_2',
        'type': 'Join',
        'params': {'strategy': 'bloom', 'join_type': 'inner'}
    },
    {
        'pipeline': 'http://yawl.io/sim/pipeline_3',
        'type': 'Transform',
        'params': {'complexity': 1.2}
    }
]

if __name__ == "__main__":
    logger.info("Creating TPOT2 bridge...")
    tpot2 = Tpot2Bridge(qlever_endpoint="http://localhost:8080")

    logger.info("Running optimization with mock data...")
    result = tpot2.run_optimization(
        compositions=mock_compositions,
        generations=2,
        population=5
    )

    logger.info(f"Optimization complete: {result}")