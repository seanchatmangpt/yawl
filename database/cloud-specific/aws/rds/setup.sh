#!/bin/bash
# AWS RDS Proxy Setup for YAWL
# This script configures RDS Proxy for secure database connectivity

set -euo pipefail

# Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
DB_INSTANCE_IDENTIFIER="${DB_INSTANCE_IDENTIFIER:-yawl-db}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_ENGINE="${DB_ENGINE:-postgres}"
DB_ENGINE_VERSION="${DB_ENGINE_VERSION:-14}"
DB_INSTANCE_CLASS="${DB_INSTANCE_CLASS:-db.t3.medium}"
DB_PORT="${DB_PORT:-5432}"
ALLOCATED_STORAGE="${ALLOCATED_STORAGE:-100}"
STORAGE_TYPE="${STORAGE_TYPE:-gp3}"
VPC_ID="${VPC_ID:-}"
SUBNET_IDS="${SUBNET_IDS:-}"
PROXY_NAME="${PROXY_NAME:-yawl-db-proxy}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI not found. Please install AWS CLI v2."
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        log_error "jq not found. Please install jq."
        exit 1
    fi

    # Check AWS credentials
    if ! aws sts get-caller-identity > /dev/null 2>&1; then
        log_error "AWS credentials not configured. Run 'aws configure'."
        exit 1
    fi

    log_info "Prerequisites satisfied."
}

# Create or get VPC
setup_vpc() {
    log_info "Setting up VPC..."

    if [ -z "$VPC_ID" ]; then
        # Create VPC
        VPC_ID=$(aws ec2 create-vpc \
            --cidr-block 10.0.0.0/16 \
            --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=yawl-vpc}]" \
            --query 'Vpc.VpcId' \
            --output text \
            --region "$AWS_REGION")

        # Enable DNS hostnames
        aws ec2 modify-vpc-attribute \
            --vpc-id "$VPC_ID" \
            --enable-dns-hostnames \
            --region "$AWS_REGION"

        # Create subnets
        SUBNET_IDS=""

        for i in 1 2 3; do
            local az_letter=$((i + 96))
            local az="${AWS_REGION}${i}"
            local cidr="10.0.${i}.0/24"

            local subnet_id=$(aws ec2 create-subnet \
                --vpc-id "$VPC_ID" \
                --cidr-block "$cidr" \
                --availability-zone "$az" \
                --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=yawl-subnet-${i}}]" \
                --query 'Subnet.SubnetId' \
                --output text \
                --region "$AWS_REGION")

            if [ -n "$SUBNET_IDS" ]; then
                SUBNET_IDS="${SUBNET_IDS} ${subnet_id}"
            else
                SUBNET_IDS="$subnet_id"
            fi
        done

        log_info "Created VPC: $VPC_ID with subnets: $SUBNET_IDS"
    else
        log_info "Using existing VPC: $VPC_ID"
    fi
}

# Create DB subnet group
create_db_subnet_group() {
    log_info "Creating DB subnet group..."

    aws rds create-db-subnet-group \
        --db-subnet-group-name yawl-db-subnet-group \
        --db-subnet-group-description "YAWL Database Subnet Group" \
        --subnet-ids $SUBNET_IDS \
        --region "$AWS_REGION" || true

    log_info "DB subnet group created."
}

# Create security groups
create_security_groups() {
    log_info "Creating security groups..."

    # DB security group
    DB_SG_ID=$(aws ec2 create-security-group \
        --group-name yawl-db-sg \
        --description "YAWL Database Security Group" \
        --vpc-id "$VPC_ID" \
        --query 'GroupId' \
        --output text \
        --region "$AWS_REGION" || aws ec2 describe-security-groups \
        --group-names yawl-db-sg \
        --query 'SecurityGroups[0].GroupId' \
        --output text \
        --region "$AWS_REGION")

    # Allow PostgreSQL from VPC
    aws ec2 authorize-security-group-ingress \
        --group-id "$DB_SG_ID" \
        --protocol tcp \
        --port 5432 \
        --cidr 10.0.0.0/16 \
        --region "$AWS_REGION" || true

    # Proxy security group
    PROXY_SG_ID=$(aws ec2 create-security-group \
        --group-name yawl-db-proxy-sg \
        --description "YAWL RDS Proxy Security Group" \
        --vpc-id "$VPC_ID" \
        --query 'GroupId' \
        --output text \
        --region "$AWS_REGION" || aws ec2 describe-security-groups \
        --group-names yawl-db-proxy-sg \
        --query 'SecurityGroups[0].GroupId' \
        --output text \
        --region "$AWS_REGION")

    log_info "Security groups created: DB=$DB_SG_ID, Proxy=$PROXY_SG_ID"
}

# Create RDS instance
create_rds_instance() {
    log_info "Creating RDS instance..."

    # Generate password
    DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)

    aws rds create-db-instance \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
        --db-instance-class "$DB_INSTANCE_CLASS" \
        --engine "$DB_ENGINE" \
        --engine-version "$DB_ENGINE_VERSION" \
        --master-username "$DB_USER" \
        --master-user-password "$DB_PASSWORD" \
        --allocated-storage "$ALLOCATED_STORAGE" \
        --storage-type "$STORAGE_TYPE" \
        --storage-encrypted \
        --db-name "$DB_NAME" \
        --db-subnet-group-name yawl-db-subnet-group \
        --vpc-security-group-ids "$DB_SG_ID" \
        --multi-az \
        --backup-retention-period 30 \
        --preferred-backup-window "02:00-03:00" \
        --preferred-maintenance-window "sun:03:00-sun:04:00" \
        --enable-performance-insights \
        --performance-insights-retention-period 7 \
        --enable-cloudwatch-logs-exports '["postgresql", "upgrade"]' \
        --deletion-protection \
        --region "$AWS_REGION" || true

    log_info "RDS instance creation initiated. Waiting for availability..."

    aws rds wait db-instance-available \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
        --region "$AWS_REGION"

    log_info "RDS instance is available."
}

# Create Secrets Manager secret
create_secret() {
    log_info "Creating Secrets Manager secret..."

    # Get RDS endpoint
    RDS_ENDPOINT=$(aws rds describe-db-instances \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
        --query 'DBInstances[0].Endpoint.Address' \
        --output text \
        --region "$AWS_REGION")

    # Create secret
    SECRET_ARN=$(aws secretsmanager create-secret \
        --name "yawl/db-credentials" \
        --description "YAWL Database Credentials" \
        --secret-string "{\"username\":\"${DB_USER}\",\"password\":\"${DB_PASSWORD}\",\"host\":\"${RDS_ENDPOINT}\",\"port\":${DB_PORT},\"database\":\"${DB_NAME}\"}" \
        --query 'ARN' \
        --output text \
        --region "$AWS_REGION" || aws secretsmanager describe-secret \
        --secret-id "yawl/db-credentials" \
        --query 'ARN' \
        --output text \
        --region "$AWS_REGION")

    log_info "Secret created: $SECRET_ARN"
}

# Create IAM role for RDS Proxy
create_iam_role() {
    log_info "Creating IAM role for RDS Proxy..."

    # Create trust policy document
    cat > /tmp/trust-policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "rds.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF

    PROXY_ROLE_ARN=$(aws iam create-role \
        --role-name "yawl-rds-proxy-role" \
        --assume-role-policy-document file:///tmp/trust-policy.json \
        --query 'Role.Arn' \
        --output text || aws iam get-role \
        --role-name "yawl-rds-proxy-role" \
        --query 'Role.Arn' \
        --output text)

    # Create policy for secrets access
    cat > /tmp/proxy-policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue"
            ],
            "Resource": "${SECRET_ARN}"
        },
        {
            "Effect": "Allow",
            "Action": [
                "kms:Decrypt"
            ],
            "Resource": "*"
        }
    ]
}
EOF

    aws iam put-role-policy \
        --role-name "yawl-rds-proxy-role" \
        --policy-name "yawl-rds-proxy-policy" \
        --policy-document file:///tmp/proxy-policy.json || true

    log_info "IAM role created: $PROXY_ROLE_ARN"
}

# Create RDS Proxy
create_rds_proxy() {
    log_info "Creating RDS Proxy..."

    # Wait for IAM role to be ready
    sleep 10

    aws rds create-db-proxy \
        --db-proxy-name "$PROXY_NAME" \
        --engine-family "$DB_ENGINE" \
        --auth "AuthScheme=SECRETS,SecretArn=${SECRET_ARN},IAMAuth=DISABLED" \
        --role-arn "$PROXY_ROLE_ARN" \
        --vpc-subnet-ids $SUBNET_IDS \
        --vpc-security-group-ids "$PROXY_SG_ID" \
        --require-tls \
        --idle-client-timeout 1800 \
        --region "$AWS_REGION" || true

    log_info "RDS Proxy creation initiated. Waiting for availability..."

    aws rds wait db-proxy-available \
        --db-proxy-name "$PROXY_NAME" \
        --region "$AWS_REGION"

    log_info "RDS Proxy is available."
}

# Register DB instance with proxy
register_db_with_proxy() {
    log_info "Registering DB instance with proxy..."

    aws rds register-db-proxy-targets \
        --db-proxy-name "$PROXY_NAME" \
        --db-instance-identifiers "$DB_INSTANCE_IDENTIFIER" \
        --region "$AWS_REGION" || true

    log_info "DB instance registered with proxy."
}

# Verify connectivity
verify_connection() {
    log_info "Verifying proxy connectivity..."

    PROXY_ENDPOINT=$(aws rds describe-db-proxies \
        --db-proxy-name "$PROXY_NAME" \
        --query 'DBProxies[0].Endpoint' \
        --output text \
        --region "$AWS_REGION")

    log_info "Proxy endpoint: $PROXY_ENDPOINT"

    # Test connection (requires psql)
    if command -v psql &> /dev/null; then
        log_info "Testing connection..."
        PGPASSWORD="$DB_PASSWORD" psql \
            --host="$PROXY_ENDPOINT" \
            --port="$DB_PORT" \
            --username="$DB_USER" \
            --dbname="$DB_NAME" \
            --command="SELECT version();" || log_warn "Could not verify connection."
    fi
}

# Print connection info
print_info() {
    RDS_ENDPOINT=$(aws rds describe-db-instances \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
        --query 'DBInstances[0].Endpoint.Address' \
        --output text \
        --region "$AWS_REGION")

    PROXY_ENDPOINT=$(aws rds describe-db-proxies \
        --db-proxy-name "$PROXY_NAME" \
        --query 'DBProxies[0].Endpoint' \
        --output text \
        --region "$AWS_REGION")

    echo ""
    echo "========================================"
    echo "AWS RDS Proxy Setup Complete"
    echo "========================================"
    echo ""
    echo "Direct Connection (use for admin only):"
    echo "  Host:     ${RDS_ENDPOINT}"
    echo "  Port:     ${DB_PORT}"
    echo "  Database: ${DB_NAME}"
    echo "  User:     ${DB_USER}"
    echo ""
    echo "Proxy Connection (use for applications):"
    echo "  Host:     ${PROXY_ENDPOINT}"
    echo "  Port:     ${DB_PORT}"
    echo "  Database: ${DB_NAME}"
    echo "  User:     ${DB_USER}"
    echo ""
    echo "JDBC URL (Proxy):"
    echo "  jdbc:postgresql://${PROXY_ENDPOINT}:${DB_PORT}/${DB_NAME}"
    echo ""
    echo "Spring Boot Configuration:"
    echo "  spring.datasource.url=jdbc:postgresql://${PROXY_ENDPOINT}:${DB_PORT}/${DB_NAME}"
    echo "  spring.datasource.username=${DB_USER}"
    echo ""
    echo "Secrets Manager ARN:"
    echo "  ${SECRET_ARN}"
    echo ""
    echo "CloudWatch Logs:"
    echo "  /aws/rds/instance/${DB_INSTANCE_IDENTIFIER}/postgresql"
    echo ""
}

# Main execution
main() {
    log_info "Starting AWS RDS Proxy setup..."

    check_prerequisites
    setup_vpc
    create_db_subnet_group
    create_security_groups
    create_rds_instance
    create_secret
    create_iam_role
    create_rds_proxy
    register_db_with_proxy
    verify_connection
    print_info

    log_info "Setup complete!"
}

# Run main function
main "$@"
