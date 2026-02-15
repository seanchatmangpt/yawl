# AWS RDS Terraform Configuration
# Production-ready PostgreSQL deployment for YAWL

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "s3" {
    bucket         = "yawl-terraform-state"
    key            = "rds/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}

# Variables
variable "aws_region" {
  description = "AWS Region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "yawl"
}

variable "database_username" {
  description = "Database master username"
  type        = string
  default     = "yawl"
}

variable "engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "14"
}

variable "instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "allocated_storage" {
  description = "Allocated storage in GB"
  type        = number
  default     = 100
}

variable "max_allocated_storage" {
  description = "Maximum allocated storage for autoscaling"
  type        = number
  default     = 500
}

variable "storage_type" {
  description = "Storage type (gp2, gp3, io1)"
  type        = string
  default     = "gp3"
}

variable "multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot on deletion"
  type        = bool
  default     = false
}

variable "backup_retention_period" {
  description = "Backup retention in days"
  type        = number
  default     = 30
}

variable "performance_insights_enabled" {
  description = "Enable Performance Insights"
  type        = bool
  default     = true
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "enable_rds_proxy" {
  description = "Enable RDS Proxy"
  type        = bool
  default     = true
}

variable "proxy_debug_logging" {
  description = "Enable RDS Proxy debug logging"
  type        = bool
  default     = false
}

# Provider configuration
provider "aws" {
  region = var.aws_region
}

# Random password
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# VPC
resource "aws_vpc" "yawl_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "yawl-vpc-${var.environment}"
    Environment = var.environment
  }
}

# Internet Gateway
resource "aws_internet_gateway" "yawl_igw" {
  vpc_id = aws_vpc.yawl_vpc.id

  tags = {
    Name        = "yawl-igw-${var.environment}"
    Environment = var.environment
  }
}

# Subnets
resource "aws_subnet" "private" {
  count             = 3
  vpc_id            = aws_vpc.yawl_vpc.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name        = "yawl-private-${count.index + 1}-${var.environment}"
    Environment = var.environment
    Type        = "private"
  }
}

resource "aws_subnet" "public" {
  count                   = 3
  vpc_id                  = aws_vpc.yawl_vpc.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 20)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name        = "yawl-public-${count.index + 1}-${var.environment}"
    Environment = var.environment
    Type        = "public"
  }
}

# DB Subnet Group
resource "aws_db_subnet_group" "yawl" {
  name       = "yawl-db-subnet-group-${var.environment}"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name        = "yawl-db-subnet-group-${var.environment}"
    Environment = var.environment
  }
}

# Security Groups
resource "aws_security_group" "rds" {
  name        = "yawl-rds-sg-${var.environment}"
  description = "Security group for YAWL RDS database"
  vpc_id      = aws_vpc.yawl_vpc.id

  ingress {
    description     = "PostgreSQL from VPC"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    cidr_blocks     = [var.vpc_cidr]
    security_groups = [aws_security_group.proxy.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "yawl-rds-sg-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_security_group" "proxy" {
  name        = "yawl-rds-proxy-sg-${var.environment}"
  description = "Security group for YAWL RDS Proxy"
  vpc_id      = aws_vpc.yawl_vpc.id

  ingress {
    description = "PostgreSQL from VPC"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "yawl-rds-proxy-sg-${var.environment}"
    Environment = var.environment
  }
}

# RDS Parameter Group
resource "aws_db_parameter_group" "yawl" {
  family = "postgres14"
  name   = "yawl-postgres14-${var.environment}"

  parameter {
    name  = "max_connections"
    value = "200"
  }

  parameter {
    name  = "shared_buffers"
    value = "262144"  # 256MB in 8KB pages
  }

  parameter {
    name  = "work_mem"
    value = "16384"  # 16MB in KB
  }

  parameter {
    name  = "maintenance_work_mem"
    value = "131072"  # 128MB in KB
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  parameter {
    name  = "log_lock_waits"
    value = "1"
  }

  tags = {
    Name        = "yawl-postgres14-${var.environment}"
    Environment = var.environment
  }
}

# RDS Instance
resource "aws_db_instance" "yawl" {
  identifier     = "yawl-db-${var.environment}"
  engine         = "postgres"
  engine_version = var.engine_version

  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage
  storage_type      = var.storage_type
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds.arn

  # Storage autoscaling
  max_allocated_storage = var.max_allocated_storage

  # Database configuration
  db_name  = var.database_name
  username = var.database_username
  password = random_password.db_password.result
  port     = 5432

  # Network configuration
  db_subnet_group_name   = aws_db_subnet_group.yawl.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # High availability
  multi_az               = var.multi_az
  availability_zone      = data.aws_availability_zones.available.names[0]

  # Backup configuration
  backup_retention_period = var.backup_retention_period
  preferred_backup_window = "02:00-03:00"

  # Maintenance
  preferred_maintenance_window = "sun:03:00-sun:04:00"
  auto_minor_version_upgrade   = true

  # Performance Insights
  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_retention_period = 7

  # Monitoring
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  monitoring_interval             = 60
  monitoring_role_arn            = aws_iam_role.rds_monitoring.arn

  # Parameter group
  parameter_group_name = aws_db_parameter_group.yawl.name

  # Deletion protection
  deletion_protection      = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "yawl-db-final-snapshot-${var.environment}-${timestamp()}"

  tags = {
    Name        = "yawl-db-${var.environment}"
    Environment = var.environment
  }

  lifecycle {
    prevent_destroy = false
    ignore_changes  = [snapshot_identifier]
  }
}

# KMS Key for encryption
resource "aws_kms_key" "rds" {
  description             = "KMS key for YAWL RDS encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Name        = "yawl-rds-kms-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_kms_alias" "rds" {
  name          = "alias/yawl-rds-${var.environment}"
  target_key_id = aws_kms_key.rds.key_id
}

# Secrets Manager
resource "aws_secretsmanager_secret" "db_credentials" {
  name                    = "yawl/db-credentials-${var.environment}"
  description             = "YAWL Database Credentials"
  recovery_window_in_days = 30

  tags = {
    Name        = "yawl-db-credentials-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.database_username
    password = random_password.db_password.result
    host     = aws_db_instance.yawl.address
    port     = aws_db_instance.yawl.port
    database = var.database_name
  })
}

# IAM Role for RDS Monitoring
resource "aws_iam_role" "rds_monitoring" {
  name = "yawl-rds-monitoring-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "yawl-rds-monitoring-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# RDS Proxy
resource "aws_db_proxy" "yawl" {
  count                  = var.enable_rds_proxy ? 1 : 0
  name                   = "yawl-db-proxy-${var.environment}"
  engine_family          = "POSTGRESQL"
  require_tls            = true
  idle_client_timeout    = 1800
  debug_logging          = var.proxy_debug_logging

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = aws_secretsmanager_secret.db_credentials.arn
  }

  role_arn                 = aws_iam_role.proxy[0].arn
  vpc_subnet_ids           = aws_subnet.private[*].id
  vpc_security_group_ids   = [aws_security_group.proxy.id]

  tags = {
    Name        = "yawl-db-proxy-${var.environment}"
    Environment = var.environment
  }

  depends_on = [aws_iam_role_policy_attachment.proxy]
}

resource "aws_db_proxy_default_target_group" "yawl" {
  count         = var.enable_rds_proxy ? 1 : 0
  db_proxy_name = aws_db_proxy.yawl[0].name

  connection_pool_config {
    connection_borrow_timeout = 120
    max_connections_percent   = 100
    max_idle_connections_percent = 50
    session_pinning_filters   = []
  }
}

resource "aws_db_proxy_target" "yawl" {
  count            = var.enable_rds_proxy ? 1 : 0
  db_proxy_name    = aws_db_proxy.yawl[0].name
  target_group_name = aws_db_proxy_default_target_group.yawl[0].name
  db_instance_identifier = aws_db_instance.yawl.identifier
}

# IAM Role for RDS Proxy
resource "aws_iam_role" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0
  name  = "yawl-rds-proxy-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "yawl-rds-proxy-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "proxy" {
  count = var.enable_rds_proxy ? 1 : 0
  name  = "yawl-rds-proxy-policy-${var.environment}"
  role  = aws_iam_role.proxy[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.db_credentials.arn
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt"
        ]
        Resource = aws_kms_key.rds.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "proxy" {
  count      = var.enable_rds_proxy ? 1 : 0
  role       = aws_iam_role.proxy[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonRDSFullAccess"
}

# Availability Zones data source
data "aws_availability_zones" "available" {
  state = "available"
}

# Outputs
output "rds_endpoint" {
  value       = aws_db_instance.yawl.endpoint
  description = "RDS instance endpoint"
}

output "rds_port" {
  value       = aws_db_instance.yawl.port
  description = "RDS instance port"
}

output "database_name" {
  value       = aws_db_instance.yawl.db_name
  description = "Database name"
}

output "database_username" {
  value       = aws_db_instance.yawl.username
  description = "Database username"
}

output "database_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "proxy_endpoint" {
  value       = var.enable_rds_proxy ? aws_db_proxy.yawl[0].endpoint : null
  description = "RDS Proxy endpoint"
}

output "secret_arn" {
  value       = aws_secretsmanager_secret.db_credentials.arn
  description = "Secrets Manager ARN"
}

output "jdbc_url" {
  value       = var.enable_rds_proxy ? "jdbc:postgresql://${aws_db_proxy.yawl[0].endpoint}:5432/${var.database_name}" : "jdbc:postgresql://${aws_db_instance.yawl.endpoint}/${var.database_name}"
  description = "JDBC URL"
}

output "vpc_id" {
  value       = aws_vpc.yawl_vpc.id
  description = "VPC ID"
}

output "private_subnet_ids" {
  value       = aws_subnet.private[*].id
  description = "Private subnet IDs"
}
