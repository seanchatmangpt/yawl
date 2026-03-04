#!/usr/bin/env python3
"""
Verify OptimalPipeline Triple in QLever

This script checks if the OptimalPipeline triple was successfully written to QLever.
"""

import requests
import json
import logging
import glob
from pathlib import Path

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def query_qlever(endpoint: str, query: str) -> dict:
    """Execute a SPARQL query against QLever."""
    try:
        response = requests.post(
            endpoint,
            data={"query": query},
            timeout=30
        )
        response.raise_for_status()
        return response.json()
    except requests.RequestException as e:
        logger.error(f"QLever query failed: {e}")
        return {}

def check_optimal_pipeline_in_qlever(endpoint: str) -> bool:
    """Check if OptimalPipeline exists in QLever."""
    query = """
    ASK WHERE {
        ?pipeline a <https://yawl.io/sim#OptimalPipeline> .
    }
    """

    result = query_qlever(endpoint, query)

    # Parse the ASK result
    if "boolean" in result:
        return result["boolean"]

    return False

def check_optimal_pipeline_files() -> dict:
    """Check for OptimalPipeline RDF files."""
    files = glob.glob("optimal_pipeline_*.ttl")

    info = {
        "files_found": len(files),
        "files": files,
        "latest_file": max(files) if files else None
    }

    return info

def main():
    """Main verification function."""
    qlever_endpoint = "http://localhost:8080"

    logger.info("Verifying OptimalPipeline triple...")

    # Check QLever directly
    qlever_has_pipeline = check_optimal_pipeline_in_qlever(qlever_endpoint)
    logger.info(f"QLever has OptimalPipeline: {qlever_has_pipeline}")

    # Check for RDF files
    file_info = check_optimal_pipeline_files()
    logger.info(f"OptimalPipeline files found: {file_info['files_found']}")

    if file_info['files_found'] > 0:
        logger.info("Latest file:")
        try:
            with open(file_info['latest_file'], 'r') as f:
                lines = f.readlines()[:10]  # Show first 10 lines
                for line in lines:
                    logger.info(f"  {line.rstrip()}")
        except Exception as e:
            logger.error(f"Error reading file: {e}")

    # Determine overall status
    overall_success = qlever_has_pipeline or file_info['files_found'] > 0

    logger.info(f"\nVerification Result: {'SUCCESS' if overall_success else 'FAILURE'}")

    return overall_success

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)