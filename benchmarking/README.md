# YAWL Performance Testing Framework

A comprehensive performance testing framework for the YAWL application, featuring load testing, stress testing, resource scaling analysis, and database performance benchmarking.

## Overview

This framework provides integrated tools and scripts for evaluating system performance across multiple dimensions:

- **Load Testing**: Gradual load increase to validate production readiness
- **Stress Testing**: Push systems to limits and identify breaking points
- **Spike Testing**: Handle sudden traffic increases
- **Endurance Testing**: Detect memory leaks and stability issues
- **Database Performance**: Query optimization and index effectiveness analysis
- **Resource Scaling**: Measure scalability across various load profiles

## Directory Structure

```
benchmarking/
├── README.md                              # This file
├── load-testing.js                        # K6 load testing script
├── performance-test.sh                    # Main test orchestration script
├── benchmark-report-template.md           # Report template
├── resource-scaling-tests.yaml            # Load scenario configurations
├── database-performance-queries.sql       # Database benchmark queries
└── results/                               # Auto-generated test results
    ├── performance_report_*.md
    ├── performance_metrics_*.json
    ├── k6_results_*.json
    ├── k6_output_*.log
    ├── curl_load_test_*.txt
    ├── resource_scaling_*.txt
    └── database_performance_*.txt
```

## Prerequisites

### Required Tools

- **curl**: For basic HTTP load testing (usually pre-installed)
- **bash**: Shell scripting (usually pre-installed)
- **jq**: JSON processing (optional, for advanced JSON parsing)

### Optional Tools

- **k6**: Modern load testing framework (https://k6.io)
  ```bash
  # macOS (Homebrew)
  brew install k6

  # Linux (apt)
  sudo apt-get install k6

  # or from source
  go install go.k6.io/k6@latest
  ```

- **PostgreSQL/MySQL Client**: For database performance testing
  ```bash
  # PostgreSQL
  sudo apt-get install postgresql-client

  # MySQL
  sudo apt-get install mysql-client
  ```

## Quick Start

### 1. Basic Load Test

```bash
cd /home/user/yawl/benchmarking

# Run with default settings (localhost:8080)
./performance-test.sh

# Run against specific environment
./performance-test.sh -u https://api.example.com -e production

# Run with custom load parameters
./performance-test.sh -u http://localhost:8080 -v 100 -d 5m
```

### 2. K6 Load Testing

```bash
# Run K6 script directly
k6 run load-testing.js

# Run with environment variable
k6 run -e BASE_URL=http://localhost:8080 load-testing.js

# Run with custom output
k6 run --out json=results/k6_results.json load-testing.js
```

### 3. Database Performance Testing

```bash
# PostgreSQL
psql -U username -d database_name -f database-performance-queries.sql

# MySQL
mysql -u username -p database_name < database-performance-queries.sql
```

## Command-Line Options

```bash
./performance-test.sh [OPTIONS]

Options:
  -u, --url URL              Base URL for testing (default: http://localhost:8080)
  -e, --environment ENV      Environment name (default: development)
  -v, --vus COUNT           Virtual users for K6 (default: 50)
  -d, --duration DURATION   Duration for K6 tests (default: 10m)
  --no-k6                   Skip K6 tests
  --verbose                 Enable verbose output
  -h, --help               Show help message

Examples:
  ./performance-test.sh
  ./performance-test.sh -u http://api.example.com -e production
  ./performance-test.sh --no-k6 -v 200 -d 15m
```

## Configuration Files

### load-testing.js (K6 Script)

The K6 load testing script includes:

- **Custom Metrics**: Response time, error rate, success count, active users
- **Load Stages**: Ramp-up, sustained load, ramp-down phases
- **Thresholds**: Pass/fail criteria for performance
- **Test Coverage**: Health checks, GET/POST endpoints, pagination, auth, error handling

Modify the `options.stages` array to change the load profile:

```javascript
stages: [
  { duration: '30s', target: 10 },    // Ramp-up to 10 users
  { duration: '1m30s', target: 50 },  // Ramp-up to 50 users
  { duration: '5m', target: 50 },     // Sustained 50 users
  { duration: '2m', target: 100 },    // Ramp-up to 100 users
  { duration: '5m', target: 100 },    // Sustained 100 users
  { duration: '30s', target: 0 },     // Ramp-down
]
```

### resource-scaling-tests.yaml

Defines multiple test scenarios:

- **smoke_test**: Basic functionality verification
- **load_test**: Ramp-up to production-like load
- **stress_test**: Push to breaking point
- **spike_test**: Handle sudden traffic
- **endurance_test**: Long-running stability
- **data_volume_scaling**: Varying dataset sizes
- **concurrent_users_scaling**: Incremental user increase

Each scenario includes:
- Load stages with durations and target users
- Performance thresholds (response time, error rate, throughput)
- Monitored metrics and resource utilization targets
- Alert rules for anomalies

### database-performance-queries.sql

Organized into sections:

1. **Basic Queries**: Simple SELECT, WHERE, ORDER BY
2. **Join Performance**: INNER JOIN, LEFT JOIN, multiple joins
3. **Aggregation**: GROUP BY, HAVING, aggregate functions
4. **Subqueries**: IN clauses, correlated subqueries, CTEs
5. **Index Analysis**: Primary key, single-column, composite indexes
6. **Data Types**: Type conversion, JSON operations
7. **Concurrency**: Simulated concurrent queries
8. **Write Operations**: INSERT, UPDATE, DELETE
9. **Monitoring**: Locks, long-running queries
10. **Health Checks**: Table sizes, index usage, cache ratios

### benchmark-report-template.md

Comprehensive report template including:

- Executive summary with key metrics
- Test configuration and environment details
- Load profile specification
- Detailed performance results
- Endpoint performance breakdown
- Load scaling analysis
- Resource utilization metrics
- Database performance analysis
- Bottleneck identification
- Baseline comparisons
- Optimization recommendations
- Test artifacts and logs
- Appendices with code snippets

## Performance Testing Scenarios

### Scenario 1: Smoke Test

Quick sanity check before full testing:

```bash
./performance-test.sh -u http://localhost:8080 -v 1 -d 60s
```

Expected: All endpoints respond successfully.

### Scenario 2: Load Testing

Validate performance under production-like load:

```bash
./performance-test.sh -u http://api.example.com -e production -v 50 -d 10m
```

Expected: All metrics within acceptable thresholds.

### Scenario 3: Stress Testing

Identify system breaking point:

```bash
k6 run --stage 5m:100 --stage 5m:500 --stage 5m:1000 load-testing.js
```

Expected: Document degradation curve and maximum sustainable load.

### Scenario 4: Endurance Testing

Long-running stability verification:

```bash
k6 run --stage 30m:50 load-testing.js
```

Expected: No memory leaks or connection exhaustion.

### Scenario 5: Spike Testing

Handle sudden traffic increases:

```bash
k6 run --stage 1m:10 --stage 1m:500 --stage 1m:10 load-testing.js
```

Expected: System recovers gracefully after spike.

## Interpreting Results

### Key Metrics

| Metric | Target | Excellent | Good | Acceptable | Poor |
|--------|--------|-----------|------|------------|------|
| P95 Response Time | < 500ms | < 200ms | < 300ms | < 500ms | > 500ms |
| P99 Response Time | < 1000ms | < 500ms | < 750ms | < 1000ms | > 1000ms |
| Error Rate | < 1% | 0% | < 0.5% | < 1% | > 1% |
| Throughput | > 100 req/s | > 500 req/s | > 250 req/s | > 100 req/s | < 100 req/s |

### Understanding Output

```
K6 Summary Output:
  checks....................: 98.5% ✓ 492
  data_received..............: 1.2 MB
  data_sent..................: 50.2 kB
  http_req_blocked...........: avg=100µs min=0 max=5ms p(90)=1ms p(95)=2ms
  http_req_connecting........: avg=0µs min=0 max=0 max=0µs p(90)=0µs p(95)=0µs
  http_req_duration..........: avg=186ms min=100ms max=542ms p(90)=300ms p(95)=450ms ✓
  http_req_failed............: 1.5% ✗
  http_req_receiving.........: avg=10ms min=0 max=50ms p(90)=20ms p(95)=30ms
  http_req_sending...........: avg=1ms min=0 max=10ms p(90)=2ms p(95)=3ms
  http_req_tls_handshaking...: avg=0µs min=0 max=0 max=0µs p(90)=0µs p(95)=0µs
  http_req_waiting...........: avg=175ms min=90ms max=520ms p(90)=290ms p(95)=440ms
  http_reqs..................: 500 16.66/s
  iteration_duration.........: avg=187ms min=102ms max=552ms p(90)=301ms p(95)=451ms
  iterations.................: 500
```

### Common Issues and Solutions

**High Response Time**
- Check server resource utilization (CPU, memory)
- Review database slow query logs
- Identify network latency
- Profile hot code paths

**High Error Rate**
- Review application logs for exceptions
- Check server capacity and rate limits
- Verify database connectivity
- Validate input validation logic

**Memory Leaks (Endurance Tests)**
- Monitor memory usage trends over time
- Look for unbounded collections or caches
- Check connection pool for leaks
- Profile garbage collection

**Throughput Degradation**
- Analyze database query performance
- Check for connection pool exhaustion
- Review application thread pool settings
- Optimize critical paths

## Advanced Usage

### Custom Load Profiles

Edit `load-testing.js` to define custom stages:

```javascript
export const options = {
  stages: [
    { duration: '5m', target: 100 },      // Custom ramp-up
    { duration: '30m', target: 100 },     // Extended sustained load
    { duration: '5m', target: 200 },      // Spike test
    { duration: '5m', target: 100 },      // Recovery
    { duration: '5m', target: 0 },        // Cool-down
  ],
};
```

### Database Connection Testing

Test database-specific performance:

```bash
# PostgreSQL with password
PGPASSWORD=yourpassword psql -h localhost -U username -d database_name \
  -f database-performance-queries.sql

# MySQL with options
mysql -h localhost -u username -p -e "source database-performance-queries.sql;"
```

### Integration with CI/CD

Add to your CI/CD pipeline:

```yaml
# GitHub Actions example
- name: Run Performance Tests
  run: |
    cd benchmarking
    ./performance-test.sh -u ${{ env.TEST_URL }} -e staging

- name: Upload Results
  if: always()
  uses: actions/upload-artifact@v2
  with:
    name: performance-results
    path: benchmarking/results/
```

### Continuous Monitoring

Set up baseline comparisons:

```bash
# Run tests regularly and track trends
0 2 * * * cd /home/user/yawl/benchmarking && ./performance-test.sh -u production > /var/log/perf_test.log 2>&1
```

## Best Practices

1. **Establish Baselines**: Run tests before making changes to establish baseline metrics
2. **Regular Testing**: Schedule periodic performance tests (daily/weekly)
3. **Document Changes**: Track performance impact of code changes
4. **Isolate Variables**: Test one change at a time
5. **Production-like Environment**: Test in environment matching production
6. **Monitor Resources**: Collect system metrics during tests
7. **Analyze Trends**: Look for performance regression over time
8. **Stress Test**: Push beyond expected load to find limits
9. **Reproduce Issues**: Use consistent test data and scenarios
10. **Review Results**: Always analyze and document findings

## Troubleshooting

### K6 Not Found

```bash
# Install k6
brew install k6  # macOS
sudo apt-get install k6  # Linux
go install go.k6.io/k6@latest  # From source
```

### Connection Refused

Verify service is running:

```bash
curl -v http://localhost:8080/health
```

### Database Connection Failed

Check credentials and permissions:

```bash
# PostgreSQL
psql -h localhost -U username -d database_name -c "SELECT 1;"

# MySQL
mysql -h localhost -u username -p -e "SELECT 1;"
```

### Out of File Descriptors

Increase system limits:

```bash
ulimit -n 10000
```

## Performance Testing Checklist

- [ ] Environment configured and accessible
- [ ] Baseline metrics established
- [ ] Smoke test passing
- [ ] Load test passing
- [ ] Stress test completed
- [ ] Resource utilization analyzed
- [ ] Database queries optimized
- [ ] Results documented
- [ ] Recommendations identified
- [ ] Action items assigned

## Output and Reporting

Test results are saved to the `results/` directory:

- **performance_report_*.md**: Formatted markdown report
- **performance_metrics_*.json**: Raw metrics in JSON format
- **k6_results_*.json**: K6 detailed results
- **k6_output_*.log**: K6 execution log
- **curl_load_test_*.txt**: HTTP benchmark results
- **resource_scaling_*.txt**: Resource scaling analysis
- **database_performance_*.txt**: Database test results

## Performance Targets

### API Response Times

```
Endpoint              P95      P99      Error Rate
/health              100ms    200ms    0%
/api/v1/resources    500ms    1000ms   < 1%
/api/v1/auth/login   750ms    1500ms   < 0.5%
```

### Database Queries

```
Query Type           Target P95   Target P99
SELECT (indexed)     50ms         100ms
SELECT (filtered)    100ms        200ms
JOIN (2 tables)      200ms        500ms
GROUP BY             300ms        750ms
```

### System Resources

```
Metric               Warning   Critical
CPU Usage            70%       85%
Memory Usage         75%       90%
Disk I/O             80%       95%
Network I/O          75%       90%
```

## References

- [K6 Documentation](https://k6.io/docs/)
- [Performance Testing Guide](https://en.wikipedia.org/wiki/Software_performance_testing)
- [Database Performance Tuning](https://use-the-index-luke.com/)
- [REST API Performance Best Practices](https://restfulapi.net/best-practices/)

## Support and Contribution

For issues, questions, or contributions:

1. Check existing documentation
2. Review test output and logs
3. Enable verbose mode: `--verbose` flag
4. Contact the performance engineering team

## License

This performance testing framework is part of the YAWL project.

---

**Last Updated**: February 2024
**Version**: 1.0
**Maintained By**: Performance Engineering Team
