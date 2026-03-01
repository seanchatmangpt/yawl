#!/usr/bin/env python3
"""
Export benchmark data for external analysis tools.
Supports JSON, CSV, and formats for ML analysis.
"""

import pandas as pd
import json
import numpy as np
from pathlib import Path
import datetime

def export_benchmark_data():
    # Create output directory
    output_dir = Path("actor-benchmark-reports/exports")
    output_dir.mkdir(exist_ok=True)
    
    # Load benchmark data
    data_path = Path("actor-benchmark-reports/raw_benchmark_data.csv")
    df = pd.read_csv(data_path)
    
    # Add timestamp
    df['timestamp'] = datetime.datetime.now().isoformat()
    
    # Export to JSON format
    json_data = df.to_dict(orient='records')
    with open(output_dir / 'benchmark_data.json', 'w') as f:
        json.dump(json_data, f, indent=2)
    
    # Export to CSV format (already done, but copy)
    df.to_csv(output_dir / 'benchmark_data.csv', index=False)
    
    # Export for ML analysis (normalized features)
    ml_data = df.copy()
    
    # Normalize numeric features
    numeric_features = ['Throughput', 'Memory', 'Latency', 'Scalability', 'Recovery']
    for feature in numeric_features:
        if feature in ml_data.columns:
            ml_data[f'{feature}_normalized'] = (
                ml_data[feature] - ml_data[feature].min()
            ) / (ml_data[feature].max() - ml_data[feature].min())
    
    # Add performance score
    ml_data['performance_score'] = (
        ml_data['Throughput_normalized'] * 0.3 +
        ml_data['Scalability_normalized'] * 0.25 +
        (1 - ml_data['Latency_normalized']) * 0.25 +
        (1 - ml_data['Recovery_normalized']) * 0.2
    ) * 100
    
    # Export ML data
    ml_data.to_csv(output_dir / 'ml_benchmark_data.csv', index=False)
    
    # Generate summary statistics
    summary_stats = {
        'generated_at': datetime.datetime.now().isoformat(),
        'total_benchmarks': len(df),
        'metrics': {
            metric: {
                'mean': df[metric].mean(),
                'median': df[metric].median(),
                'std': df[metric].std(),
                'min': df[metric].min(),
                'max': df[metric].max(),
                'p95': df[metric].quantile(0.95),
                'p99': df[metric].quantile(0.99)
            }
            for metric in numeric_features
        },
        'performance_assessment': {
            'excellent': len(df[df['Status'] == '✓']),
            'good': len(df[df['Status'] == '△']),
            'needs_improvement': len(df[df['Status'] == '✗'])
        }
    }
    
    with open(output_dir / 'summary_statistics.json', 'w') as f:
        json.dump(summary_stats, f, indent=2)
    
    # Create visualization data for external tools
    viz_data = {
        'charts': [
            {
                'type': 'bar',
                'title': 'Message Throughput by Benchmark',
                'data': {
                    'x': df['Benchmark'].tolist(),
                    'y': df['Throughput'].tolist(),
                    'labels': [f"{b}: {t/1e6:.1f}M" for b, t in zip(df['Benchmark'], df['Throughput'])]
                }
            },
            {
                'type': 'scatter',
                'title': 'Scalability vs Throughput',
                'data': {
                    'x': df['Scalability'].tolist(),
                    'y': df['Throughput'].tolist(),
                    'color': [s == '✓' for s in df['Status']],
                    'size': df['Memory'].tolist(),
                    'labels': df['Benchmark'].tolist()
                }
            },
            {
                'type': 'radar',
                'title': 'Overall Performance Profile',
                'data': {
                    'categories': ['Throughput', 'Memory', 'Latency', 'Scalability', 'Recovery'],
                    'values': [
                        df['Throughput'].mean() / 10_000_000 * 100,
                        (10 - df['Memory'].mean()) / 10 * 100,
                        (5 - df['Latency'].mean()) / 5 * 100,
                        df['Scalability'].mean(),
                        (200 - df['Recovery'].mean()) / 200 * 100
                    ]
                }
            }
        ]
    }
    
    with open(output_dir / 'visualization_data.json', 'w') as f:
        json.dump(viz_data, f, indent=2)
    
    print(f"Data exported to: {output_dir}")
    print("\nExported files:")
    for file in output_dir.glob("*"):
        print(f"  - {file.name}")

if __name__ == "__main__":
    export_benchmark_data()
