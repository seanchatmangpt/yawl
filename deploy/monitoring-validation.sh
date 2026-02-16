#!/bin/bash

echo "=== Monitoring Stack Validation ==="

# Check 1: Prometheus scraping targets
echo -e "\n[CHECK 1] Prometheus Targets"
curl -s http://staging-prometheus:9090/api/v1/targets | jq '.data.activeTargets | length' || echo "0"

# Check 2: Alert rules loaded
echo -e "\n[CHECK 2] Alert Rules"
curl -s http://staging-prometheus:9090/api/v1/rules | jq '.data | length' || echo "0"

# Check 3: Grafana dashboards
echo -e "\n[CHECK 3] Grafana Dashboards"
curl -s -H "Authorization: Bearer ${GRAFANA_TOKEN}" \
    http://staging-grafana:3000/api/search | jq '.[] | .title' | head -5

# Check 4: Jaeger tracing
echo -e "\n[CHECK 4] Jaeger Tracing"
curl -s http://staging-jaeger:16686/api/services | jq '.data | length'

# Check 5: Log aggregation (ELK)
echo -e "\n[CHECK 5] Log Aggregation"
curl -s http://staging-elasticsearch:9200/_cluster/health | jq '.status'

echo -e "\n✅ Monitoring validation complete!"
