#!/usr/bin/env python3
"""
YAWL MCP A2A Message Throughput Benchmark Results Analyzer

This script analyzes benchmark results and generates performance reports.
"""

import json
import re
import sys
from typing import Dict, List, Tuple
from datetime import datetime
import matplotlib.pyplot as plt
import numpy as np

class BenchmarkAnalyzer:
    def __init__(self):
        self.results = []
        self.patterns = ['1:1', '1:N', 'N:1', 'N:M']
        self.message_sizes = ['small', 'medium', 'large']
        self.threading_models = ['virtual', 'platform']

    def parse_benchmark_output(self, output: str) -> Dict:
        """Parse benchmark output into structured data"""
        parsed = {
            'timestamp': datetime.now().isoformat(),
            'results': []
        }

        # Parse each test result
        current_test = None

        for line in output.split('\n'):
            line = line.strip()

            # Start of new test
            if '===' in line and 'Pattern' in line:
                current_test = {
                    'name': line.split('===')[1].strip(),
                    'message_size': None,
                    'throughput': None,
                    'duration': None,
                    'latency': {}
                }
                parsed['results'].append(current_test)

            # Message size header
            elif line.startswith('Messages ('):
                current_test['message_size'] = line.split('(')[1].split(')')[0].split()[0].lower()

            # Throughput
            elif 'Throughput:' in line:
                current_test['throughput'] = float(line.split(':')[1].strip().split()[0])

            # Duration
            elif 'Duration:' in line:
                current_test['duration'] = float(line.split(':')[1].strip().split()[0])

            # Latency percentiles
            elif 'Latency P' in line:
                parts = line.split(':')
                percentile = parts[0].split()[1]
                value = float(parts[1].strip().split()[0])
                current_test['latency'][percentile] = value

        return parsed

    def compare_threading_models(self, results: List[Dict]) -> Dict:
        """Compare virtual vs platform threading performance"""
        comparison = {}

        for pattern in self.patterns:
            pattern_results = [r for r in results if pattern in r['name']]

            if pattern_results:
                comparison[pattern] = {
                    'virtual': None,
                    'platform': None,
                    'improvement': None
                }

                # Find virtual and platform results
                for size in self.message_sizes:
                    virtual = next((r for r in pattern_results
                                   if r['message_size'] == size and 'Virtual Threads' in r['name']), None)
                    platform = next((r for r in pattern_results
                                    if r['message_size'] == size and 'Platform Threads' in r['name']), None)

                    if virtual and platform:
                        comparison[pattern][f'{size}_virtual'] = virtual['throughput']
                        comparison[pattern][f'{size}_platform'] = platform['throughput']

                        # Calculate improvement percentage
                        improvement = ((virtual['throughput'] - platform['throughput']) / platform['throughput']) * 100
                        comparison[pattern][f'{size}_improvement'] = improvement

        return comparison

    def generate_performance_report(self, results: Dict) -> str:
        """Generate a comprehensive performance report"""
        report = []
        report.append("=== YAWL MCP A2A Message Throughput Benchmark Report ===")
        report.append(f"Generated: {results['timestamp']}")
        report.append("")

        # Summary statistics
        report.append("## Summary Statistics")
        report.append("")

        all_throughputs = [r['throughput'] for r in results['results'] if r['throughput']]
        if all_throughputs:
            report.append(f"**Average Throughput**: {np.mean(all_throughputs):.2f} messages/sec")
            report.append(f"**Peak Throughput**: {max(all_throughputs):.2f} messages/sec")
            report.append(f"**Min Throughput**: {min(all_throughputs):.2f} messages/sec")
            report.append("")

        # Pattern performance
        report.append("## Pattern Performance Analysis")
        report.append("")

        for pattern in self.patterns:
            pattern_results = [r for r in results['results'] if pattern in r['name']]
            if pattern_results:
                report.append(f"### {pattern} Pattern")
                report.append("")

                for size in self.message_sizes:
                    size_results = [r for r in pattern_results if r['message_size'] == size]
                    if size_results:
                        result = size_results[0]
                        report.append(f"**{size.upper()} Messages**: {result['throughput']:.2f} msg/sec")
                        report.append(f"  - Duration: {result['duration']:.3f} seconds")
                        report.append(f"  - Latency P99: {result['latency'].get('P99', 'N/A')} μs")
                        report.append("")

        # Threading comparison
        threading_comparison = self.compare_threading_models(results['results'])
        report.append("## Virtual vs Platform Thread Comparison")
        report.append("")

        for pattern, data in threading_comparison.items():
            report.append(f"### {pattern} Pattern")
            report.append("")

            for size in self.message_sizes:
                virtual_key = f'{size}_virtual'
                platform_key = f'{size}_platform'
                improvement_key = f'{size}_improvement'

                if virtual_key in data and data[virtual_key]:
                    virtual_perf = data[virtual_key]
                    platform_perf = data[platform_key]
                    improvement = data[improvement_key]

                    report.append(f"**{size.upper()} Messages**:")
                    report.append(f"  - Virtual Threads: {virtual_perf:.2f} msg/sec")
                    report.append(f"  - Platform Threads: {platform_perf:.2f} msg/sec")
                    report.append(f"  - Improvement: {improvement:+.2f}%")
                    report.append("")

        # Recommendations
        report.append("## Performance Recommendations")
        report.append("")
        report.append("### Virtual Thread Benefits")
        report.append("- Better throughput for I/O-bound workloads")
        report.append("- Lower memory footprint per thread")
        report.append("- Scales better with high concurrency")
        report.append("")

        report.append("### Platform Thread Benefits")
        report.append("- Better for CPU-bound workloads")
        report.append("- More predictable performance")
        report.append("- Better for legacy code compatibility")
        report.append("")

        report.append("### General Recommendations")
        report.append("- Use virtual threads for message processing workloads")
        report.append("- Monitor GC pressure with large messages")
        report("- Consider batching for high-throughput scenarios")
        report("- Use connection pooling for network-based messaging")
        report.append("")

        return "\n".join(report)

    def create_performance_chart(self, results: Dict, output_file: str = 'performance_comparison.png'):
        """Create a performance comparison chart"""
        plt.figure(figsize=(15, 10))

        # Throughput comparison by pattern
        plt.subplot(2, 2, 1)

        for i, pattern in enumerate(self.patterns):
            x = []
            y = []
            labels = []

            for size in self.message_sizes:
                result = next((r for r in results['results']
                              if pattern in r['name'] and r['message_size'] == size), None)
                if result:
                    x.append(i * 3 + ['small', 'medium', 'large'].index(size))
                    y.append(result['throughput'])
                    labels.append(size.upper())

            plt.bar(x, y, label=f'{pattern} Pattern')

        plt.xlabel('Message Size')
        plt.ylabel('Throughput (msg/sec)')
        plt.title('Throughput by Pattern and Message Size')
        plt.xticks([0, 3, 6], ['Small', 'Medium', 'Large'])
        plt.legend()

        # Latency comparison
        plt.subplot(2, 2, 2)

        for pattern in self.patterns:
            x = []
            y = []

            for size in self.message_sizes:
                result = next((r for r in results['results']
                              if pattern in r['name'] and r['message_size'] == size), None)
                if result and 'P95' in result['latency']:
                    x.append(['small', 'medium', 'large'].index(size))
                    y.append(result['latency']['P95'])

            if x and y:
                plt.plot(x, y, marker='o', label=f'{pattern} Pattern')

        plt.xlabel('Message Size')
        plt.ylabel('Latency P95 (μs)')
        plt.title('Latency P95 by Message Size')
        plt.xticks([0, 1, 2], ['Small', 'Medium', 'Large'])
        plt.legend()

        # Virtual vs Platform comparison
        plt.subplot(2, 2, 3)

        threading_comparison = self.compare_threading_models(results['results'])

        for pattern in self.patterns:
            virtual_perf = []
            platform_perf = []

            for size in self.message_sizes:
                virtual_key = f'{size}_virtual'
                platform_key = f'{size}_platform'

                if virtual_key in threading_comparison[pattern] and threading_comparison[pattern][virtual_key]:
                    virtual_perf.append(threading_comparison[pattern][virtual_key])
                    platform_perf.append(threading_comparison[pattern][platform_key])

            if virtual_perf and platform_perf:
                x = range(len(virtual_perf))
                width = 0.35

                plt.bar([i - width/2 for i in x], virtual_perf, width, label=f'{pattern} Virtual')
                plt.bar([i + width/2 for i in x], platform_perf, width, label=f'{pattern} Platform')

        plt.xlabel('Message Size')
        plt.ylabel('Throughput (msg/sec)')
        plt.title('Virtual vs Platform Threads')
        plt.xticks([0, 1, 2], ['Small', 'Medium', 'Large'])
        plt.legend()

        # Performance improvement heatmap
        plt.subplot(2, 2, 4)

        improvement_matrix = []
        pattern_labels = []

        for pattern in self.patterns:
            pattern_improvements = []
            for size in self.message_sizes:
                improvement_key = f'{size}_improvement'
                if improvement_key in threading_comparison[pattern]:
                    improvement = threading_comparison[pattern][improvement_key]
                    pattern_improvements.append(improvement)

            if pattern_improvements:
                improvement_matrix.append(pattern_improvements)
                pattern_labels.append(pattern)

        if improvement_matrix:
            im = plt.imshow(improvement_matrix, cmap='RdYlGn', aspect='auto')
            plt.colorbar(im, label='Improvement (%)')

            plt.xticks(range(3), ['Small', 'Medium', 'Large'])
            plt.yticks(range(len(pattern_labels)), pattern_labels)
            plt.xlabel('Message Size')
            plt.ylabel('Pattern')
            plt.title('Virtual Thread Performance Improvement')

        plt.tight_layout()
        plt.savefig(output_file, dpi=300, bbox_inches='tight')
        plt.close()

        return output_file

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze-benchmark-results.py <benchmark_output_file>")
        sys.exit(1)

    input_file = sys.argv[1]

    # Read benchmark output
    try:
        with open(input_file, 'r') as f:
            benchmark_output = f.read()
    except FileNotFoundError:
        print(f"Error: Input file '{input_file}' not found")
        sys.exit(1)

    # Analyze results
    analyzer = BenchmarkAnalyzer()

    # Parse the benchmark output
    results = analyzer.parse_benchmark_output(benchmark_output)

    # Generate report
    report = analyzer.generate_performance_report(results)

    # Write report to file
    report_file = 'benchmark_report.md'
    with open(report_file, 'w') as f:
        f.write(report)

    print(f"Generated performance report: {report_file}")

    # Create visualization
    chart_file = analyzer.create_performance_chart(results)
    print(f"Generated performance chart: {chart_file}")

    print("\nAnalysis complete!")

if __name__ == "__main__":
    main()