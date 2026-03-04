#!/usr/bin/env python3
"""
Validate all OCEL test files for realism
"""

import json
import os
import glob
from validate_ocel_realism import OCELValidator

def main():
    # Find all OCEL files
    ocel_patterns = [
        "test/ocel*.json",
        "test/jtbd/pi-sprint*.json",
        "test/fixtures/ocel/**/*.json"
    ]

    all_files = []
    for pattern in ocel_patterns:
        all_files.extend(glob.glob(pattern, recursive=True))

    # Filter out metadata files
    ocel_files = [f for f in all_files if not f.endswith('metadata.json') and not f.endswith('README.md')]

    print(f"Found {len(ocel_files)} OCEL files to validate")
    print("=" * 60)

    total_score = 0
    file_count = 0

    for file_path in sorted(ocel_files):
        if not os.path.exists(file_path):
            continue

        print(f"\n🔍 Validating: {file_path}")
        validator = OCELValidator(file_path)
        results = validator.validate()

        # Print summary
        score = results['realism_score']
        total_score += score
        file_count += 1

        print(f"   Score: {score}/100")

        # Show failure rate
        if results['total_events'] > 0:
            failure_rate = (results['failure_events'] / results['total_events']) * 100
            print(f"   Failure Rate: {failure_rate:.1f}%")

        # Show key issues
        if results['issues']:
            print(f"   Issues: {len(results['issues'])}")

    # Overall statistics
    print("\n" + "=" * 60)
    print("OVERALL RESULTS")
    print("=" * 60)
    print(f"Files Validated: {file_count}")
    print(f"Average Realism Score: {total_score/file_count:.1f}/100")

    if total_score/file_count >= 75:
        print("✅ Overall: Good realism")
    elif total_score/file_count >= 50:
        print("⚠️ Overall: Needs improvement")
    else:
        print("❌ Overall: Poor realism")

    # Summary of needs
    print("\nSUMMARY OF IMPROVEMENTS NEEDED:")
    print("- Add more failure events (target 10% failure rate)")
    print("- Add timing jitter (avoid perfect sequential timing)")
    print("- Include edge cases (null values, empty strings, extreme values)")
    print("- Add more event type diversity")
    print("- Avoid hardcoded sequential IDs")

if __name__ == "__main__":
    main()