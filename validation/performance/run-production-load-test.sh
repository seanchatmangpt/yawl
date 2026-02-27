#!/bin/bash

# YAWL Production Load Test Runner Script
# Validates production-scale performance and scalability

set -e

echo "==============================================="
echo "YAWL Production Load Test"
echo "==============================================="
echo ""

# Configuration
SERVICE_URL=${SERVICE_URL:-"http://localhost:8080"}
K6_OPTIONS=${K6_OPTIONS:-"--vus 1 --duration 1s --out json=k6-output.json"}
TEST_SCRIPT="production-load-test.js"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 is not installed${NC}"
    echo "Please install k6 from: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "$TEST_SCRIPT" ]; then
    echo -e "${RED}Error: production-load-test.js not found${NC}"
    echo "Please run this script from the validation/performance directory"
    exit 1
fi

# Function to display usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --url URL               YAWL service URL (default: http://localhost:8080)"
    echo "  --vus COUNT             Number of virtual users for dry run"
    echo "  --duration DURATION     Duration for dry run (default: 30s)"
    echo "  --help                  Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  SERVICE_URL             YAWL service URL"
    echo "  K6_OPTIONS              Additional k6 options"
    echo ""
    echo "Examples:"
    echo "  $0                     # Run with default settings"
    echo "  $0 --url http://prod.yawl:8080  # Run against production"
    echo "  $0 --vus 10 --duration 1m    # Dry run with 10 users"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --url)
            SERVICE_URL="$2"
            shift 2
            ;;
        --vus)
            VUS="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            exit 1
            ;;
    esac
done

# Build k6 options
K6_RUN_OPTIONS="--out json=k6-output.json"

if [ ! -z "$VUS" ]; then
    K6_RUN_OPTIONS="$K6_RUN_OPTIONS --vus $VUS"
fi

if [ ! -z "$DURATION" ]; then
    K6_RUN_OPTIONS="$K6_RUN_OPTIONS --duration $DURATION"
else
    K6_RUN_OPTIONS="$K6_RUN_OPTIONS --duration 30s"
fi

# Function to check if service is healthy
check_service_health() {
    local endpoint="$1"
    local max_attempts=5
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        echo -n "Checking $endpoint... "
        if curl -s -f "$endpoint" > /dev/null 2>&1; then
            echo -e "${GREEN}OK${NC}"
            return 0
        else
            echo -e "${YELLOW}attempt $attempt/$max_attempts${NC}"
            sleep 2
            ((attempt++))
        fi
    done
    
    echo -e "${RED}Failed to connect to $endpoint${NC}"
    return 1
}

# Pre-run checks
echo "🔍 Running pre-flight checks..."
echo ""

# Check service health
if ! check_service_health "$SERVICE_URL/actuator/health"; then
    echo -e "${RED}Health check failed${NC}"
    exit 1
fi

if ! check_service_health "$SERVICE_URL/yawl/ib"; then
    echo -e "${YELLOW}YAWL interface slow to respond${NC}"
fi

# Run dry run test
echo -e "\n${YELLOW}🧪 Running dry run test...${NC}"
echo "Service URL: $SERVICE_URL"
echo "VUs: ${VUS:-1}"
echo "Duration: ${DURATION:-30s}"
echo ""

export SERVICE_URL="$SERVICE_URL"
timeout 60 k6 run $K6_RUN_OPTIONS $TEST_SCRIPT || {
    echo -e "\n${RED}Dry run failed or timed out${NC}"
    exit 1
}

# Check if dry run output was generated
if [ -f "k6-output.json" ]; then
    echo -e "\n${GREEN}✅ Dry run completed successfully${NC}"
    
    # Extract key metrics from dry run
    TOTAL_REQS=$(jq '.metrics.http_reqs.values.count' k6-output.json 2>/dev/null || echo "0")
    SUCCESS_RATE=$(jq '.metrics.http_req_failed.values.rate' k6-output.json 2>/dev/null || echo "0")
    AVG_RESP_TIME=$(jq '.metrics.http_req_duration.values.avg' k6-output.json 2>/dev/null || echo "0")
    P95_RESP_TIME=$(jq '.metrics.http_req_duration.values["p(95)"]' k6-output.json 2>/dev/null || echo "0")
    
    echo ""
    echo "Dry Run Results:"
    echo "  Total Requests: $TOTAL_REQS"
    echo "  Success Rate: $(echo "scale=2; $SUCCESS_RATE * 100" | bc)%"
    echo "  Avg Response Time: ${AVG_RESP_TIME}ms"
    echo "  P95 Response Time: ${P95_RESP_TIME}ms"
    echo ""
fi

# Ask user to confirm production run
echo -e "${YELLOW}📋 Production Load Test Configuration:${NC}"
echo "Target: 10,000 concurrent users for 60 minutes"
echo "Thresholds: P95 < 500ms, Error Rate < 1%"
echo "Service URL: $SERVICE_URL"
echo ""

read -p "Do you want to run the full production load test? (yes/no): " confirm

if [[ $confirm =~ ^[Yy] ]]; then
    echo -e "\n${GREEN}🚀 Starting full production load test...${NC}"
    echo "This will take approximately 60 minutes"
    echo ""
    
    # Run full production test
    timeout 4500 k6 run $TEST_SCRIPT || {
        echo -e "\n${YELLOW}Production test completed or timed out${NC}"
    }
    
    # Generate final report
    if [ -f "k6-prod-load-summary.json" ]; then
        echo -e "\n${GREEN}📊 Generating final report...${NC}"
        
        # Extract key metrics
        TOTAL_REQ=$(jq '.metrics.total_requests' k6-prod-load-summary.json)
        SUCCESS_RATE=$(jq '.metrics.success_rate' k6-prod-load-summary.json)
        P95_TIME=$(jq '.metrics.p95_response_time' k6-prod-load-summary.json)
        OVERALL_RESULT=$(jq -r '.threshold_results | [to_entries[] | select(.value.passed == true) | .key] | length == (length | (. // 0))' k6-prod-load-summary.json)
        
        echo ""
        echo "==============================================="
        echo "PRODUCTION LOAD TEST RESULTS"
        echo "==============================================="
        echo ""
        echo "📊 Performance Metrics:"
        echo "  Total Requests: $TOTAL_REQ"
        echo "  Success Rate: $(echo "scale=2; $SUCCESS_RATE * 100" | bc)%"
        echo "  P95 Response Time: ${P95_TIME}ms"
        echo ""
        echo "✅ Test Status: $([ "$OVERALL_RESULT" == "true" ] && echo "PASSED" || echo "FAILED")"
        echo ""
        
        if [ "$OVERALL_RESULT" == "true" ]; then
            echo "🎉 Congratulations! YAWL has passed production load testing"
            echo "The system is ready for production deployment"
        else
            echo "⚠️  Some performance thresholds were not met"
            echo "Please review the detailed report for improvement areas"
        fi
        
        echo ""
        echo "Detailed report available in: k6-prod-load-summary.json"
    else
        echo -e "${YELLOW}⚠️  Final report not found${NC}"
    fi
else
    echo -e "\n${YELLOW}Production load test cancelled${NC}"
    exit 0
fi

echo ""
echo "==============================================="
echo "Test complete"
echo "==============================================="
