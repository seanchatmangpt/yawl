#!/usr/bin/env python3
"""
OCEL Test Data Realism Validation Script

This script validates OCEL test data for realism by checking for:
1. Failure events (should be ~10%)
2. Timing variations (not perfectly sequential)
3. Multiple event types (not just success)
4. Edge cases (null, empty, extreme values)
5. Realistic data distributions

Usage: python validate_ocel_realism.py <ocel-file.json>
"""

import json
import sys
import re
from datetime import datetime, timedelta
from typing import Dict, List, Any
import argparse

class OCELValidator:
    def __init__(self, file_path: str):
        self.file_path = file_path
        self.data = self.load_ocel_file()
        self.results = {
            'filename': file_path,
            'total_events': 0,
            'failure_events': 0,
            'success_events': 0,
            'error_events': 0,
            'warning_events': 0,
            'system_events': 0,
            'event_types': set(),
            'has_timing_jitter': False,
            'has_edge_cases': False,
            'hardcoded_patterns': [],
            'realism_score': 0,
            'issues': [],
            'recommendations': []
        }

    def load_ocel_file(self) -> Dict[str, Any]:
        try:
            with open(self.file_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            print(f"❌ Error: File {self.file_path} not found")
            sys.exit(1)
        except json.JSONDecodeError as e:
            print(f"❌ Error: Invalid JSON in {self.file_path}: {e}")
            sys.exit(1)

    def validate_events(self):
        """Validate event data for realism"""
        events = self.data.get('events', [])
        self.results['total_events'] = len(events)

        if not events:
            self.results['issues'].append("No events found")
            return

        # Analyze event types
        event_types_found = set()
        failure_types = ['TaskFail', 'Error', 'Fail', 'Cancelled', 'Timeout', 'Exception']
        success_types = ['TaskComplete', 'Complete', 'Success', 'Accept']
        warning_types = ['Warning', 'Alert', 'Block', 'Unblock']

        for event in events:
            event_type = event.get('type', event.get('activity', 'Unknown'))
            event_types_found.add(event_type)

            # Count different event types
            if any(fail_type.lower() in event_type.lower() for fail_type in failure_types):
                self.results['failure_events'] += 1
            elif any(success_type.lower() in event_type.lower() for success_type in success_types):
                self.results['success_events'] += 1
            elif any(warning_type.lower() in event_type.lower() for warning_type in warning_types):
                self.results['warning_events'] += 1

            if event_type.lower() in ['error', 'system', 'event']:
                self.results['system_events'] += 1

            # Check for hardcoded sequential IDs
            if event.get('id', '').startswith('e') and event['id'][1:].isdigit():
                expected_num = int(event['id'][1:]) if len(event['id']) > 1 else 1
                if expected_num != len(event_types_found):
                    self.results['hardcoded_patterns'].append(f"Sequential ID {event['id']}")

        self.results['event_types'] = event_types_found

        # Calculate failure rate
        if self.results['total_events'] > 0:
            failure_rate = (self.results['failure_events'] / self.results['total_events']) * 100
            if failure_rate < 5:
                self.results['issues'].append(f"❌ Failure rate too low: {failure_rate:.1f}% (should be ~10%)")
                self.results['recommendations'].append("Add failure events (TaskFail, Error, etc.)")
            elif failure_rate > 30:
                self.results['issues'].append(f"⚠️ Failure rate high: {failure_rate:.1f}%")

        # Check for perfect timing
        self._check_timing_patterns(events)

        # Check for edge cases
        self._check_edge_cases(events)

    def _check_timing_patterns(self, events: List[Dict]):
        """Check for realistic timing patterns"""
        timestamps = []
        for event in events:
            ts = event.get('timestamp')
            if ts:
                try:
                    dt = datetime.fromisoformat(ts.replace('Z', '+00:00'))
                    timestamps.append(dt)
                except:
                    continue

        if len(timestamps) < 2:
            return

        # Check for sequential timing (every X minutes exactly)
        intervals = []
        for i in range(1, len(timestamps)):
            interval = (timestamps[i] - timestamps[i-1]).total_seconds()
            intervals.append(interval)

        # Check if intervals are too perfect
        perfect_intervals = all(abs(interval - 120) < 1 for interval in intervals[:5])  # Perfect 2-minute intervals
        if perfect_intervals and len(intervals) >= 5:
            self.results['issues'].append("❌ Perfect sequential timing detected")
            self.results['recommendations'].append("Add timing jitter (±100ms to 5s)")
        else:
            self.results['has_timing_jitter'] = True

    def _check_edge_cases(self, events: List[Dict]):
        """Check for edge cases in event attributes"""
        edge_cases_found = []

        for event in events:
            attrs = event.get('attributes', {})
            for key, value in attrs.items():
                # Check for null values
                if value is None:
                    edge_cases_found.append(f"Null value in {key}")

                # Check for empty strings
                elif isinstance(value, str) and value.strip() == "":
                    edge_cases_found.append(f"Empty string in {key}")

                # Check for extreme numbers
                elif isinstance(value, (int, float)):
                    if abs(value) > 1000000:  # Very large numbers
                        edge_cases_found.append(f"Extreme number in {key}: {value}")
                    elif value == 0:
                        edge_cases_found.append(f"Zero value in {key}")

        if edge_cases_found:
            self.results['has_edge_cases'] = True
            self.results['issues'].append(f"Found {len(edge_cases_found)} edge cases")
        else:
            self.results['issues'].append("⚠️ No edge cases found (add null, empty, extreme values)")
            self.results['recommendations'].append("Add edge cases: null values, empty strings, extreme numbers")

    def calculate_realism_score(self) -> int:
        """Calculate realism score 0-100"""
        score = 0

        # Event type diversity (20 points)
        if len(self.results['event_types']) >= 5:
            score += 20
        elif len(self.results['event_types']) >= 3:
            score += 10

        # Failure rate (20 points)
        if self.results['total_events'] > 0:
            failure_rate = (self.results['failure_events'] / self.results['total_events']) * 100
            if 8 <= failure_rate <= 15:  # ~10%
                score += 20
            elif 5 <= failure_rate <= 20:
                score += 10

        # Timing variation (15 points)
        if self.results['has_timing_jitter']:
            score += 15

        # Edge cases (15 points)
        if self.results['has_edge_cases']:
            score += 15

        # No hardcoded patterns (20 points)
        if not self.results['hardcoded_patterns']:
            score += 20
        elif len(self.results['hardcoded_patterns']) <= 2:
            score += 10

        # System events (10 points)
        if self.results['system_events'] > 0:
            score += 10

        self.results['realism_score'] = score
        return score

    def validate(self) -> Dict[str, Any]:
        """Run all validations"""
        self.validate_events()
        self.calculate_realism_score()

        # Generate recommendations based on findings
        if self.results['realism_score'] < 50:
            self.results['recommendations'].append("File needs major realism improvements")
        elif self.results['realism_score'] < 75:
            self.results['recommendations'].append("File needs some realism improvements")

        return self.results

    def print_report(self):
        """Print validation report"""
        print(f"\n{'='*60}")
        print(f"OCEL REALITY VALIDATION REPORT")
        print(f"{'='*60}")
        print(f"File: {self.file_path}")
        print(f"Realism Score: {self.results['realism_score']}/100")

        print(f"\n📊 Event Statistics:")
        print(f"  Total Events: {self.results['total_events']}")
        print(f"  Success Events: {self.results['success_events']}")
        print(f"  Failure Events: {self.results['failure_events']}")
        print(f"  Warning Events: {self.results['warning_events']}")
        print(f"  System Events: {self.results['system_events']}")

        print(f"\n🎯 Event Types Found:")
        for event_type in sorted(self.results['event_types']):
            print(f"  - {event_type}")

        if self.results['failure_events'] > 0:
            failure_rate = (self.results['failure_events'] / self.results['total_events']) * 100
            print(f"\n📈 Failure Rate: {failure_rate:.1f}%")

        print(f"\n✅ Realism Checks:")
        print(f"  Timing Jitter: {'✅' if self.results['has_timing_jitter'] else '❌'}")
        print(f"  Edge Cases: {'✅' if self.results['has_edge_cases'] else '❌'}")

        if self.results['hardcoded_patterns']:
            print(f"\n⚠️ Hardcoded Patterns Found:")
            for pattern in self.results['hardcoded_patterns']:
                print(f"  - {pattern}")

        if self.results['issues']:
            print(f"\n🚨 Issues Found:")
            for issue in self.results['issues']:
                print(f"  - {issue}")

        if self.results['recommendations']:
            print(f"\n💡 Recommendations:")
            for rec in self.results['recommendations']:
                print(f"  - {rec}")

        print(f"\n{'='*60}")

def main():
    parser = argparse.ArgumentParser(description='Validate OCEL test data realism')
    parser.add_argument('file', help='Path to OCEL JSON file')
    parser.add_argument('--score-only', action='store_true', help='Output only the realism score')

    args = parser.parse_args()

    validator = OCELValidator(args.file)
    results = validator.validate()

    if args.score_only:
        print(results['realism_score'])
    else:
        validator.print_report()

if __name__ == "__main__":
    main()