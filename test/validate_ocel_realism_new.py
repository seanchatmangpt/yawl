#!/usr/bin/env python3
"""
OCEL Test Data Realism Validation Script - Updated Version

This script validates OCEL test data for realism by checking for:
1. Failure events (should be ~10-15%)
2. Timing variations (not perfectly sequential)
3. Multiple event types (not just success)
4. Edge cases (null, empty, extreme values)
5. Realistic data distributions
6. Concurrent processing scenarios

Usage: python validate_ocel_realism_new.py <ocel-file.json>
"""

import json
import sys
import re
from datetime import datetime, timedelta
from typing import Dict, List, Any, Set
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
            'human_events': 0,
            'event_types': set(),
            'has_timing_jitter': False,
            'has_edge_cases': False,
            'has_concurrent_events': False,
            'has_failed_cases': False,
            'hardcoded_patterns': [],
            'realism_score': 0,
            'issues': [],
            'recommendations': [],
            'timing_analysis': [],
            'failure_analysis': {}
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
        events = self.data.get('events', [])
        self.results['total_events'] = len(events)

        if not events:
            self.results['issues'].append("No events found in file")
            return

        previous_timestamp = None
        timing_intervals = []

        for event in events:
            event_id = event.get('id', '')
            event_type = event.get('type', '')
            lifecycle = event.get('lifecycle', '')
            timestamp = event.get('timestamp', '')

            # Track event types
            self.results['event_types'].add(event_type)

            # Analyze timing
            if timestamp and previous_timestamp:
                try:
                    ts1 = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
                    ts2 = datetime.fromisoformat(previous_timestamp.replace('Z', '+00:00'))
                    interval = (ts1 - ts2).total_seconds()
                    timing_intervals.append(interval)

                    # Check for perfect sequential timing (exactly 2 minutes)
                    if interval == 120:  # Exactly 2 minutes
                        self.results['hardcoded_patterns'].append(
                            f"Perfect sequential timing: {previous_timestamp} -> {timestamp} ({interval}s)"
                        )
                except:
                    pass

            previous_timestamp = timestamp

            # Categorize events
            if event_type in ['Error', 'PaymentError', 'ValidationError', 'SystemError', 'Timeout', 'Fail']:
                self.results['error_events'] += 1
                self.results['failure_events'] += 1
            elif event_type in ['Warning', 'Retry']:
                self.results['warning_events'] += 1
            elif lifecycle in ['terminate', 'fail']:
                self.results['failure_events'] += 1
            elif lifecycle in ['complete', 'success']:
                self.results['success_events'] += 1
            elif event_type in ['ManualReview', 'Escalation', 'Approval', 'Rejection']:
                self.results['human_events'] += 1
            else:
                self.results['system_events'] += 1

            # Check for edge cases
            if self.check_edge_cases(event):
                self.results['has_edge_cases'] = True

            # Check for concurrent events
            if self.check_concurrent_events(event, events):
                self.results['has_concurrent_events'] = True

            # Check for failed cases
            if self.check_failed_cases(event):
                self.results['has_failed_cases'] = True

        # Analyze timing patterns
        self.analyze_timing_patterns(timing_intervals)

        # Calculate failure rate
        if self.results['total_events'] > 0:
            failure_rate = self.results['failure_events'] / self.results['total_events']
            self.results['failure_analysis'] = {
                'rate': failure_rate,
                'count': self.results['failure_events'],
                'acceptable_range': (0.08, 0.15),
                'is_adequate': 0.08 <= failure_rate <= 0.15
            }

    def check_edge_cases(self, event: Dict) -> bool:
        """Check for edge cases like null values, empty strings, extreme values"""
        has_edge_cases = False

        # Check for null values
        if any(value is None for value in event.get('attributes', {}).values()):
            has_edge_cases = True

        # Check for empty strings
        if any(value == "" for value in event.get('attributes', {}).values()):
            has_edge_cases = True

        # Check for extreme values in numeric fields
        attributes = event.get('attributes', {})
        for key, value in attributes.items():
            if isinstance(value, (int, float)):
                if value == 0 or value >= 1000000 or value <= -1000000:
                    has_edge_cases = True

        return has_edge_cases

    def check_concurrent_events(self, event: Dict, all_events: List) -> bool:
        """Check for concurrent events (same timestamp)"""
        event_timestamp = event.get('timestamp', '')
        if not event_timestamp:
            return False

        concurrent_count = sum(1 for e in all_events
                             if e.get('timestamp') == event_timestamp and e.get('id') != event.get('id'))
        return concurrent_count > 0

    def check_failed_cases(self, event: Dict) -> bool:
        """Check for failed/cancelled orders"""
        event_type = event.get('type', '')
        lifecycle = event.get('lifecycle', '')

        # Check specific failure patterns
        failure_patterns = [
            'Cancel', 'Error', 'Fail', 'Timeout', 'Rejected', 'Failed'
        ]

        return any(pattern in event_type or pattern in lifecycle for pattern in failure_patterns)

    def analyze_timing_patterns(self, intervals: List[float]):
        """Analyze timing intervals for realism"""
        if len(intervals) < 2:
            return

        # Calculate timing statistics
        avg_interval = sum(intervals) / len(intervals)
        min_interval = min(intervals)
        max_interval = max(intervals)

        # Check for timing jitter (variation)
        if max_interval - min_interval > 10:  # More than 10 seconds variation
            self.results['has_timing_jitter'] = True

        # Check for too regular timing
        if all(abs(interval - 120) < 1 for interval in intervals):  # All exactly 2 minutes
            self.results['issues'].append("All events have exactly 2-minute intervals - unrealistic")

        self.results['timing_analysis'] = {
            'average_interval': avg_interval,
            'min_interval': min_interval,
            'max_interval': max_interval,
            'variation': max_interval - min_interval
        }

    def calculate_realism_score(self):
        """Calculate overall realism score (0-100)"""
        score = 100

        # Deduct points for issues
        if self.results['failure_analysis'].get('rate', 0) < 0.05:
            score -= 20
            self.results['recommendations'].append("Add more failure events (target 8-15% failure rate)")
        elif self.results['failure_analysis'].get('rate', 0) > 0.20:
            score -= 10
            self.results['recommendations'].append("Reduce failure rate (too many failures)")

        if not self.results['has_timing_jitter']:
            score -= 15
            self.results['recommendations'].append("Add timing variation (avoid perfect sequential timing)")

        if len(self.results['event_types']) < 4:
            score -= 10
            self.results['recommendations'].append("Add more event type diversity")

        if not self.results['has_edge_cases']:
            score -= 10
            self.results['recommendations'].append("Add edge cases (null values, extreme values)")

        if not self.results['has_concurrent_events']:
            score -= 10
            self.results['recommendations'].append("Add concurrent events")

        if self.results['hardcoded_patterns']:
            score -= 15
            self.results['recommendations'].append("Remove hardcoded sequential patterns")

        # Bonus points for good characteristics
        if self.results['has_failed_cases']:
            score += 5

        if len(self.results['event_types']) >= 6:
            score += 5

        if self.results['human_events'] > 0:
            score += 5

        self.results['realism_score'] = max(0, score)

    def validate(self):
        """Run the complete validation"""
        self.validate_events()
        self.calculate_realism_score()
        return self.results

def format_results(results: Dict) -> str:
    """Format validation results for display"""
    output = []
    output.append(f"\n{'='*60}")
    output.append(f"OCEL Realism Validation Results")
    output.append(f"{'='*60}")
    output.append(f"File: {results['filename']}")
    output.append(f"Realism Score: {results['realism_score']}/100")
    output.append("")

    # Event statistics
    output.append("Event Statistics:")
    output.append(f"  Total Events: {results['total_events']}")
    output.append(f"  Success Events: {results['success_events']}")
    output.append(f"  Failure Events: {results['failure_events']} ({results['failure_analysis'].get('rate', 0):.1%})")
    output.append(f"  Error Events: {results['error_events']}")
    output.append(f"  Warning Events: {results['warning_events']}")
    output.append(f"  Event Types: {len(results['event_types'])}")
    output.append(f"  Human Events: {results['human_events']}")
    output.append("")

    # Feature analysis
    output.append("Feature Analysis:")
    output.append(f"  ✓ Timing Jitter: {'Yes' if results['has_timing_jitter'] else 'No'}")
    output.append(f"  ✓ Edge Cases: {'Yes' if results['has_edge_cases'] else 'No'}")
    output.append(f"  ✓ Concurrent Events: {'Yes' if results['has_concurrent_events'] else 'No'}")
    output.append(f"  ✓ Failed Cases: {'Yes' if results['has_failed_cases'] else 'No'}")
    output.append("")

    # Timing analysis
    if results['timing_analysis']:
        ta = results['timing_analysis']
        output.append("Timing Analysis:")
        output.append(f"  Average Interval: {ta['average_interval']:.1f}s")
        output.append(f"  Min Interval: {ta['min_interval']:.1f}s")
        output.append(f"  Max Interval: {ta['max_interval']:.1f}s")
        output.append(f"  Variation: {ta['variation']:.1f}s")
        output.append("")

    # Event types
    output.append("Event Types Found:")
    for etype in sorted(results['event_types']):
        output.append(f"  - {etype}")
    output.append("")

    # Issues
    if results['issues']:
        output.append("Issues Found:")
        for issue in results['issues']:
            output.append(f"  ❌ {issue}")
        output.append("")

    # Hardcoded patterns
    if results['hardcoded_patterns']:
        output.append("Hardcoded Patterns Found:")
        for pattern in results['hardcoded_patterns'][:5]:  # Show first 5
            output.append(f"  ⚠️  {pattern}")
        output.append("")

    # Recommendations
    if results['recommendations']:
        output.append("Recommendations:")
        for rec in results['recommendations']:
            output.append(f"  💡 {rec}")
        output.append("")

    # Final verdict
    if results['realism_score'] >= 75:
        verdict = "GOOD"
        status = "✅"
    elif results['realism_score'] >= 50:
        verdict = "NEEDS IMPROVEMENT"
        status = "⚠️"
    else:
        verdict = "POOR"
        status = "❌"

    output.append(f"Overall Verdict: {status} {verdict}")

    return "\n".join(output)

def main():
    parser = argparse.ArgumentParser(description='Validate OCEL test data for realism')
    parser.add_argument('file_path', help='Path to OCEL JSON file')
    args = parser.parse_args()

    validator = OCELValidator(args.file_path)
    results = validator.validate()

    print(format_results(results))

    # Exit with appropriate code
    if results['realism_score'] >= 75:
        sys.exit(0)
    elif results['realism_score'] >= 50:
        sys.exit(1)
    else:
        sys.exit(2)

if __name__ == "__main__":
    main()