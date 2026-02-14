#!/bin/bash
#
# YAWL AWS CDK Deployment Helper Script
# This script provides convenient commands for deploying and managing YAWL infrastructure
#
# Usage: ./deploy.sh [command] [environment]
# Commands:
#   setup              - Initial setup and bootstrap
#   deploy             - Deploy all stacks
#   deploy-layer N     - Deploy specific layer (network, database, cache, ecs, storage, distribution, monitoring)
#   diff               - Show differences between current and desired state
#   outputs            - Show stack outputs
#   logs               - Tail ECS logs
#   status             - Show infrastructure status
#   cleanup            - Destroy all stacks
#   help               - Show this help message

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${2:-production}
REGION=${AWS_REGION:-us-east-1}
CONTAINER_IMAGE=${CONTAINER_IMAGE:-"public.ecr.aws/docker/library/nginx:latest"}

# Functions
print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI not found. Please install AWS CLI v2"
        exit 1
    fi
    print_success "AWS CLI found: $(aws --version)"

    # Check Python
    if ! command -v python3 &> /dev/null; then
        print_error "Python 3 not found. Please install Python 3.9 or higher"
        exit 1
    fi
    print_success "Python found: $(python3 --version)"

    # Check CDK
    if ! command -v cdk &> /dev/null; then
        print_error "AWS CDK not found. Please install with: npm install -g aws-cdk"
        exit 1
    fi
    print_success "CDK found: $(cdk --version)"

    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured"
        exit 1
    fi
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    print_success "AWS Account: $ACCOUNT_ID"
}

activate_venv() {
    if [ ! -d "venv" ]; then
        print_header "Creating Python Virtual Environment"
        python3 -m venv venv
    fi

    source venv/bin/activate
    print_success "Virtual environment activated"
}

setup() {
    print_header "Setting Up YAWL Infrastructure"

    check_prerequisites
    activate_venv

    # Upgrade pip and install dependencies
    print_header "Installing Dependencies"
    pip install --upgrade pip > /dev/null 2>&1
    pip install -r requirements.txt > /dev/null 2>&1
    print_success "Dependencies installed"

    # Bootstrap CDK
    print_header "Bootstrapping CDK"
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    if ! aws s3 ls "s3://cdk-hnb659fds-${ACCOUNT_ID}-${REGION}" 2> /dev/null; then
        cdk bootstrap aws://${ACCOUNT_ID}/${REGION}
        print_success "CDK bootstrapped"
    else
        print_warning "CDK already bootstrapped"
    fi

    print_success "Setup complete!"
    echo ""
    echo "Next steps:"
    echo "  1. Set environment: export ENVIRONMENT=production"
    echo "  2. Deploy: ./deploy.sh deploy production"
    echo "  3. View outputs: ./deploy.sh outputs production"
}

deploy() {
    print_header "Deploying YAWL Infrastructure for $ENVIRONMENT"
    check_prerequisites
    activate_venv

    export ENVIRONMENT=${ENVIRONMENT}
    export AWS_REGION=${REGION}
    export CONTAINER_IMAGE=${CONTAINER_IMAGE}

    echo "Configuration:"
    echo "  Environment: ${ENVIRONMENT}"
    echo "  Region: ${REGION}"
    echo "  Container: ${CONTAINER_IMAGE}"
    echo ""

    # List stacks to be deployed
    print_header "Stacks to Deploy"
    cdk list

    # Deploy
    print_header "Starting Deployment"
    cdk deploy \
        --require-approval=never \
        --method=direct

    print_success "Deployment complete!"
}

deploy_layer() {
    LAYER=$1

    if [ -z "$LAYER" ]; then
        print_error "Layer not specified"
        echo "Available layers: network, database, cache, ecs, storage, distribution, monitoring"
        exit 1
    fi

    print_header "Deploying Layer: $LAYER"
    check_prerequisites
    activate_venv

    LAYER_MAP=(
        "network:yawl-${ENVIRONMENT}-network"
        "database:yawl-${ENVIRONMENT}-database"
        "cache:yawl-${ENVIRONMENT}-cache"
        "ecs:yawl-${ENVIRONMENT}-ecs"
        "storage:yawl-${ENVIRONMENT}-storage"
        "distribution:yawl-${ENVIRONMENT}-distribution"
        "monitoring:yawl-${ENVIRONMENT}-monitoring"
    )

    STACK_NAME=""
    for pair in "${LAYER_MAP[@]}"; do
        key="${pair%:*}"
        value="${pair#*:}"
        if [ "$key" = "$LAYER" ]; then
            STACK_NAME="$value"
            break
        fi
    done

    if [ -z "$STACK_NAME" ]; then
        print_error "Unknown layer: $LAYER"
        echo "Available layers: network, database, cache, ecs, storage, distribution, monitoring"
        exit 1
    fi

    export ENVIRONMENT=${ENVIRONMENT}
    export AWS_REGION=${REGION}

    cdk deploy ${STACK_NAME} --require-approval=never

    print_success "Layer deployment complete!"
}

show_diff() {
    print_header "Showing Differences"
    check_prerequisites
    activate_venv

    export ENVIRONMENT=${ENVIRONMENT}
    export AWS_REGION=${REGION}

    cdk diff
}

show_outputs() {
    print_header "Stack Outputs for $ENVIRONMENT"

    STACKS=$(aws cloudformation list-stacks \
        --region ${REGION} \
        --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
        --query "StackSummaries[?contains(StackName, 'yawl-${ENVIRONMENT}')].StackName" \
        --output text)

    if [ -z "$STACKS" ]; then
        print_warning "No stacks found for environment: $ENVIRONMENT"
        exit 1
    fi

    for STACK in $STACKS; do
        echo ""
        print_header "Stack: $STACK"
        aws cloudformation describe-stacks \
            --region ${REGION} \
            --stack-name ${STACK} \
            --query 'Stacks[0].Outputs' \
            --output table
    done
}

tail_logs() {
    print_header "Tailing ECS Logs for $ENVIRONMENT"

    aws logs tail "/ecs/yawl/${ENVIRONMENT}" --follow --region ${REGION}
}

show_status() {
    print_header "Infrastructure Status for $ENVIRONMENT"

    echo ""
    echo "VPC:"
    aws ec2 describe-vpcs \
        --region ${REGION} \
        --filters "Name=tag:Name,Values=yawl-vpc-${ENVIRONMENT}" \
        --query 'Vpcs[].{VpcId:VpcId,CidrBlock:CidrBlock,IsDefault:IsDefault}' \
        --output table

    echo ""
    echo "RDS Database:"
    aws rds describe-db-instances \
        --region ${REGION} \
        --db-instance-identifier "yawl-db-${ENVIRONMENT}" \
        --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus,DBInstanceClass,Engine,EngineVersion]' \
        --output table 2>/dev/null || print_warning "No RDS instance found"

    echo ""
    echo "ECS Cluster:"
    aws ecs describe-clusters \
        --region ${REGION} \
        --clusters "yawl-cluster-${ENVIRONMENT}" \
        --query 'clusters[].[clusterName,status,clusterArn]' \
        --output table 2>/dev/null || print_warning "No ECS cluster found"

    echo ""
    echo "ECS Service:"
    aws ecs describe-services \
        --region ${REGION} \
        --cluster "yawl-cluster-${ENVIRONMENT}" \
        --services "yawl-service-${ENVIRONMENT}" \
        --query 'services[].[serviceName,status,desiredCount,runningCount]' \
        --output table 2>/dev/null || print_warning "No ECS service found"

    echo ""
    echo "Load Balancer:"
    aws elbv2 describe-load-balancers \
        --region ${REGION} \
        --query "LoadBalancers[?LoadBalancerName=='yawl-alb-${ENVIRONMENT}'].[LoadBalancerName,State.Code,DNSName,Scheme]" \
        --output table 2>/dev/null || print_warning "No ALB found"

    echo ""
    echo "S3 Buckets:"
    aws s3 ls --region ${REGION} | grep "yawl.*${ENVIRONMENT}"

    echo ""
    echo "CloudFront Distribution:"
    aws cloudfront list-distributions \
        --query "DistributionList.Items[?Comment=='YAWL Distribution - ${ENVIRONMENT}'].[Id,DomainName,Status,Enabled]" \
        --output table 2>/dev/null || print_warning "No CloudFront distribution found"
}

cleanup() {
    print_header "Destroying Infrastructure for $ENVIRONMENT"
    print_warning "This will delete all resources!"
    echo ""
    read -p "Type 'yes' to confirm: " confirm

    if [ "$confirm" != "yes" ]; then
        print_warning "Aborted"
        exit 0
    fi

    check_prerequisites
    activate_venv

    export ENVIRONMENT=${ENVIRONMENT}
    export AWS_REGION=${REGION}

    cdk destroy --require-approval=never

    print_success "Cleanup complete!"
}

show_help() {
    cat << EOF
YAWL AWS CDK Deployment Helper

Usage: ./deploy.sh [command] [environment]

Commands:
  setup               - Initial setup and bootstrap
  deploy              - Deploy all stacks (requires environment)
  deploy-layer LAYER  - Deploy specific layer (requires environment)
  diff                - Show differences between current and desired state (requires environment)
  outputs             - Show stack outputs (requires environment)
  logs                - Tail ECS logs (requires environment)
  status              - Show infrastructure status (requires environment)
  cleanup             - Destroy all stacks (requires environment)
  help                - Show this help message

Layers:
  network            - VPC and networking
  database           - RDS PostgreSQL
  cache              - Redis ElastiCache
  ecs                - ECS Fargate and ALB
  storage            - S3 buckets
  distribution       - CloudFront distribution
  monitoring         - CloudWatch monitoring

Environment:
  Defaults to 'production' if not specified

Environment Variables:
  ENVIRONMENT        - deployment environment (development, staging, production)
  AWS_REGION         - AWS region (default: us-east-1)
  CONTAINER_IMAGE    - Docker image URI (default: public.ecr.aws/docker/library/nginx:latest)
  AWS_PROFILE        - AWS CLI profile to use

Examples:
  ./deploy.sh setup
  ./deploy.sh deploy production
  ./deploy.sh deploy development
  ./deploy.sh deploy-layer network production
  ./deploy.sh outputs production
  ENVIRONMENT=development ./deploy.sh deploy
  AWS_REGION=eu-west-1 ./deploy.sh deploy production

EOF
}

# Main
case "${1:-help}" in
    setup)
        setup
        ;;
    deploy)
        deploy
        ;;
    deploy-layer)
        deploy_layer "$2"
        ;;
    diff)
        show_diff
        ;;
    outputs)
        show_outputs
        ;;
    logs)
        tail_logs
        ;;
    status)
        show_status
        ;;
    cleanup)
        cleanup
        ;;
    help)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
