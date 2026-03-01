#!/usr/bin/env python3

import json
import csv
import os
from datetime import datetime
from pathlib import Path
import argparse

class StressTestReportGenerator:
    def __init__(self, report_dir):
        self.report_dir = Path(report_dir)
        self.reports = []
        self.summary = {}

    def load_all_reports(self):
        """Load all JSON reports from the reports directory"""
        reports_dir = self.report_dir / "reports"
        if not reports_dir.exists():
            print(f"Reports directory not found: {reports_dir}")
            return

        json_files = list(reports_dir.glob("*.json"))
        if not json_files:
            print("No JSON reports found")
            return

        for json_file in json_files:
            try:
                with open(json_file, 'r') as f:
                    data = json.load(f)
                    data['filename'] = json_file.name
                    self.reports.append(data)
            except Exception as e:
                print(f"Error loading {json_file}: {e}")

    def generate_csv_summary(self, output_file="stress-test-summary.csv"):
        """Generate CSV summary of all test results"""
        if not self.reports:
            print("No reports to summarize")
            return

        # Define CSV columns
        fieldnames = [
            'Test Name',
            'Timestamp',
            'Threads',
            'Duration (s)',
            'Total Queries',
            'Success Count',
            'Failure Count',
            'Error Rate (%)',
            'Throughput (qps)',
            'Avg Latency (ms)',
            'P95 Latency (ms)',
            'P99 Latency (ms)',
            'Memory Growth (MB)',
            'GC Cycles',
            'Status'
        ]

        output_path = self.report_dir / output_file

        with open(output_path, 'w', newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()

            for report in self.reports:
                try:
                    row = {
                        'Test Name': report.get('test_name', 'Unknown'),
                        'Timestamp': report.get('timestamp', ''),
                        'Threads': report.get('thread_count', 0),
                        'Duration (s)': report.get('duration_seconds', 0),
                        'Total Queries': report.get('total_queries', 0),
                        'Success Count': report.get('success_count', 0),
                        'Failure Count': report.get('failure_count', 0),
                        'Error Rate (%)': report.get('error_rate', 0.0),
                        'Throughput (qps)': report.get('throughput', 0.0),
                        'Avg Latency (ms)': report.get('avg_latency', 0.0),
                        'P95 Latency (ms)': report.get('p95_latency', 0.0),
                        'P99 Latency (ms)': report.get('p99_latency', 0.0),
                        'Memory Growth (MB)': report.get('memory_growth_mb', 0.0),
                        'GC Cycles': report.get('gc_cycles', 0),
                        'Status': 'PASSED' if report.get('error_rate', 0) < 0.05 else 'FAILED'
                    }
                    writer.writerow(row)
                except Exception as e:
                    print(f"Error processing report {report.get('filename', 'unknown')}: {e}")

        print(f"CSV summary generated: {output_path}")

    def generate_performance_chart_data(self, output_file="performance-data.csv"):
        """Generate CSV for performance charting"""
        if not self.reports:
            print("No reports for performance data")
            return

        # CSV suitable for plotting tools
        fieldnames = [
            'Test Name',
            'Thread Count',
            'Throughput (qps)',
            'Avg Latency (ms)',
            'P95 Latency (ms)',
            'Error Rate (%)'
        ]

        output_path = self.report_dir / output_file

        with open(output_path, 'w', newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()

            for report in self.reports:
                # Only include concurrent test results
                if 'concurrent' in report.get('filename', ''):
                    row = {
                        'Test Name': report.get('test_name', 'Unknown'),
                        'Thread Count': report.get('thread_count', 0),
                        'Throughput (qps)': report.get('throughput', 0.0),
                        'Avg Latency (ms)': report.get('avg_latency', 0.0),
                        'P95 Latency (ms)': report.get('p95_latency', 0.0),
                        'Error Rate (%)': report.get('error_rate', 0.0)
                    }
                    writer.writerow(row)

        print(f"Performance data generated: {output_path}")

    def generate_bottleneck_report(self, output_file="bottleneck-analysis.csv"):
        """Identify and report system bottlenecks"""
        if not self.reports:
            print("No reports for bottleneck analysis")
            return

        fieldnames = [
            'Bottleneck Type',
            'Test Name',
            'Metric Value',
            'Threshold',
            'Severity',
            'Recommendation'
        ]

        output_path = self.report_dir / output_file

        with open(output_path, 'w', newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()

            for report in self.reports:
                # Check for various bottlenecks
                bottlenecks = self._identify_bottlenecks(report)

                for bottleneck in bottlenecks:
                    writer.writerow({
                        'Bottleneck Type': bottleneck['type'],
                        'Test Name': report.get('test_name', 'Unknown'),
                        'Metric Value': bottleneck['value'],
                        'Threshold': bottleneck['threshold'],
                        'Severity': bottleneck['severity'],
                        'Recommendation': bottleneck['recommendation']
                    })

        print(f"Bottleneck analysis generated: {output_path}")

    def _identify_bottlenecks(self, report):
        """Identify potential system bottlenecks"""
        bottlenecks = []

        # High latency
        p95_latency = report.get('p95_latency', 0)
        if p95_latency > 500:
            bottlenecks.append({
                'type': 'High Latency',
                'value': p95_latency,
                'threshold': 500,
                'severity': 'CRITICAL',
                'recommendation': 'Check database indexes, optimize queries, increase resources'
            })
        elif p95_latency > 200:
            bottlenecks.append({
                'type': 'High Latency',
                'value': p95_latency,
                'threshold': 200,
                'severity': 'WARNING',
                'recommendation': 'Monitor query performance, consider optimization'
            })

        # High error rate
        error_rate = report.get('error_rate', 0)
        if error_rate > 0.20:
            bottlenecks.append({
                'type': 'High Error Rate',
                'value': error_rate * 100,
                'threshold': 20,
                'severity': 'CRITICAL',
                'recommendation': 'Check system resources, investigate error causes'
            })
        elif error_rate > 0.05:
            bottlenecks.append({
                'type': 'High Error Rate',
                'value': error_rate * 100,
                'threshold': 5,
                'severity': 'WARNING',
                'recommendation': 'Monitor for increasing errors'
            })

        # High memory growth
        memory_growth = report.get('memory_growth_mb', 0)
        if memory_growth > 500:
            bottlenecks.append({
                'type': 'Memory Leak',
                'value': memory_growth,
                'threshold': 500,
                'severity': 'CRITICAL',
                'recommendation': 'Investigate memory leaks, check resource cleanup'
            })
        elif memory_growth > 100:
            bottlenecks.append({
                'type': 'High Memory Usage',
                'value': memory_growth,
                'threshold': 100,
                'severity': 'WARNING',
                'recommendation': 'Monitor memory usage patterns'
            })

        # Low throughput
        throughput = report.get('throughput', 0)
        if throughput < 10:
            bottlenecks.append({
                'type': 'Low Throughput',
                'value': throughput,
                'threshold': 10,
                'severity': 'WARNING',
                'recommendation': 'Check system performance, optimize queries'
            })

        return bottlenecks

    def generate_json_summary(self, output_file="stress-test-summary.json"):
        """Generate JSON summary for programmatic access"""
        summary = {
            'generated_at': datetime.now().isoformat(),
            'total_tests': len(self.reports),
            'passed_tests': sum(1 for r in self.reports if r.get('error_rate', 0) < 0.05),
            'failed_tests': sum(1 for r in self.reports if r.get('error_rate', 0) >= 0.05),
            'overall_throughput': sum(r.get('throughput', 0) for r in self.reports) / len(self.reports),
            'avg_latency': sum(r.get('avg_latency', 0) for r in self.reports) / len(self.reports),
            'reports': self.reports
        }

        output_path = self.report_dir / output_file

        with open(output_path, 'w') as f:
            json.dump(summary, f, indent=2)

        print(f"JSON summary generated: {output_path}")

    def generate_all_reports(self):
        """Generate all types of reports"""
        print(f"Generating reports for {len(self.reports)} test results...")

        # Generate all reports
        self.generate_csv_summary()
        self.generate_performance_chart_data()
        self.generate_bottleneck_report()
        self.generate_json_summary()

        print("All reports generated successfully!")

def main():
    parser = argparse.ArgumentParser(description='Generate stress test reports')
    parser.add_argument('--report-dir', default='stress-test-reports',
                       help='Directory containing test reports')
    parser.add_argument('--output', help='Output file name (optional)')

    args = parser.parse_args()

    # Initialize report generator
    generator = StressTestReportGenerator(args.report_dir)

    # Load reports
    generator.load_all_reports()

    if not generator.reports:
        print("No reports found to process")
        return

    # Generate reports
    generator.generate_all_reports()

if __name__ == '__main__':
    main()